package com.geometryduel.neat

import android.util.Log
import com.geometryduel.DuelController
import com.geometryduel.HardwareInfo
import com.geometryduel.game.GameSystem
import com.geometryduel.game.engine.ComputerEngine
import com.geometryduel.game.state.ResultGameState
import java.util.ArrayList
import java.util.Collections
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class NeatTrainer(private val app: DuelController, rayCount: Int) {

    companion object {
        private const val TAG = "NeatTrainer"
        const val POPULATION = 150
        private const val MAX_MATCH_FRAMES = 180 + 7200
        private const val MAX_GHOSTS = 20
        private const val CHAMPION_POOL_SIZE = 8
        private const val HISTORICAL_CHAMPION_SIZE = 16
        private const val HISTORICAL_INTERVAL = 25   // 名人堂入库间隔（代）
        private const val SAVE_INTERVAL_MS = 30000L // 存盘时间制：高速训练不再按代刷屏写盘

        // ---- 智能平台期收敛：以冠军 vs 规则AI(1.0) 胜率为可比进度指标 ----
        private const val CHAMP_EVAL_INTERVAL = 10   // 每 10 代测一次冠军胜率
        private const val CHAMP_EVAL_SIMS = 6
        private const val MIN_GENS_FOR_STOP = 200    // 最小进化代数门槛
        private const val PLATEAU_LIMIT = 15         // 15 次测量（≈150 代）无进展判平台期

        fun shapingScale(generation: Int): Float =
            0.2f + 2.8f * kotlin.math.exp(-generation / 80.0).toFloat()

        private fun progressiveLevel(generation: Int): Float =
            min(1.0f, 0.3f + generation * 0.005f)

        private fun updateElo(rating: Float, oppRating: Float, won: Boolean): Float {
            val expected = 1f / (1f + 10.0.pow(((oppRating - rating) / 400f).toDouble()).toFloat())
            return rating + 32f * ((if (won) 1f else 0f) - expected)
        }
    }

    // 硬件感知的动态参数（质量优先，老旧设备自动降档）
    private var dynamicPopulation = 0
    private var dynamicSimsPerMatch = 0
    private var dynamicSelfPlayRounds = 0

    private val rng = Random()
    private val evolverLock = Any()

    private var threadPool: ExecutorService? = null
    private val rngPerThread = object : ThreadLocal<Random>() {
        override fun initialValue(): Random = Random()
    }

    /** 当前线程的随机源（initialValue 保证非空）。 */
    private fun threadRng(): Random = rngPerThread.get()!!
    private val pooledEngineA = ThreadLocal<NeatEngine?>()
    private val pooledEngineB = ThreadLocal<NeatEngine?>()
    private val pooledTracker = ThreadLocal<MatchTracker?>()
    private val pooledStats = ThreadLocal<MatchStats?>()

    @Volatile private var rayCount: Int = rayCount
    @Volatile private var evolver: NeatEvolver? = null
    @Volatile private var champion: Genome? = null
    private val championPool = CopyOnWriteArrayList<Genome>()
    private val historicalChampions = ArrayList<Genome>()
    private val strategyCounts = HashMap<String, Int>()

    @Volatile private var generation = 0
    @Volatile private var bestFitness = 0f
    /** 距上次实战已训练的代数：达到 app.trainRoundLimit 后自动暂停，下一场实战重置。 */
    @Volatile private var gensSinceLastMatch = 0
    private val simMatches = AtomicLong()
    @Volatile private var paused = false
    @Volatile private var stopped = false
    @Volatile private var resetting = false
    @Volatile private var converged = false
    /** 自适应课程：对规则AI的当前难度（0.3~1.0），按 EMA 胜率动态升降。 */
    private var curriculumLevel = 0.3f
    private var ruleWinRateEma = 0.5f
    private var gensSinceCurriculumAdjust = 0

    // ---- 智能收敛状态 ----
    @Volatile private var champWinRateEma = -1f  // 冠军胜率 EMA（-1=未测量）
    private var bestWinRateEma = 0f
    private var plateauCount = 0
    private var diversityInjected = false

    // ---- 训练速度指标与看门狗 ----
    @Volatile private var genRate = 0f                // gen/s EMA
    private var lastSaveMs = 0L
    /** 重置请求：由渲染线程发起、训练线程执行，避免在 UI 线程做重活。 */
    @Volatile private var resetRequested = false
    @Volatile private var pendingRayCount = 0
    /** 幽灵入库请求：IO 由训练线程执行，避免渲染线程写大 JSON。 */
    @Volatile private var ghostSaveRequested = false
    @Volatile private var realMatchBonus = 0f
    private var championElo = 1200f
    private var playerWins = 0
    private var playerLosses = 0
    private val ghosts = CopyOnWriteArrayList<GhostData>()
    private val noveltyArchive = NoveltyArchive(rng)

    private var thread: Thread? = null

    private fun initializeDynamicParameters() {
        val hasNpu = HardwareInfo.npuInfo.let { it != "Unknown" && !it.contains("None") }
        val cores = Runtime.getRuntime().availableProcessors()

        // 质量优先的分级：充足算力换更准的适应度与更多样的对手；老旧设备自动降档
        if (hasNpu || cores >= 8) {
            dynamicPopulation = if (hasNpu) POPULATION * 3 / 2 else POPULATION
            dynamicSimsPerMatch = 8
            dynamicSelfPlayRounds = 3
        } else if (cores >= 5) {
            dynamicPopulation = POPULATION
            dynamicSimsPerMatch = 6
            dynamicSelfPlayRounds = 3
        } else {
            // 老旧设备（≤4 核）：保住可训练性，由代耗时看门狗进一步兜底
            dynamicPopulation = POPULATION * 2 / 3
            dynamicSimsPerMatch = 4
            dynamicSelfPlayRounds = 2
        }

        Log.i(TAG, "dynamic params: pop=$dynamicPopulation, sims=$dynamicSimsPerMatch" +
                ", selfPlayRounds=$dynamicSelfPlayRounds")
    }

    // ------------------------------------------------------------ 生命周期

    fun start() {
        HardwareInfo.detectAsync()
        initializeDynamicParameters()

        val cores = Runtime.getRuntime().availableProcessors()
        val hasNpu = HardwareInfo.npuInfo.let { it != "Unknown" && !it.contains("None") }
        var workers = if (hasNpu) max(8, cores * 2) else max(4, cores)
        workers = min(workers, 32)

        threadPool = Executors.newFixedThreadPool(workers) { r ->
            Thread(r, "neat-eval-" + Integer.toHexString(r.hashCode())).apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY + 1
            }
        }
        thread = Thread({ loop() }, "neat-trainer").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "started with $workers workers, NPU: ${HardwareInfo.npuInfo}")
    }

    fun shutdown() {
        stopped = true
        threadPool?.shutdownNow()
        thread?.let {
            try { it.join(3000) } catch (e: InterruptedException) { }
        }
        saveNow()
    }

    /**
     * 请求重置训练（渲染线程安全，立即返回）。
     * 实际重置由训练线程执行，连点自动合并为一次。
     */
    fun requestReset(newRayCount: Int) {
        pendingRayCount = newRayCount
        resetRequested = true
    }

    /** 实际重置：仅在训练线程调用。 */
    private fun doReset(newRayCount: Int) {
        synchronized(evolverLock) {
            resetting = true
            try {
                rayCount = newRayCount
                NeatStorage.clear()
                ghosts.clear()
                ghostSaveRequested = false
                champion = null
                championPool.clear()
                historicalChampions.clear()
                strategyCounts.clear()
                generation = 0
                bestFitness = 0f
                gensSinceLastMatch = 0
                simMatches.set(0)
                realMatchBonus = 0f
                championElo = 1200f
                playerWins = 0; playerLosses = 0
                converged = false
                evolver = newEvolver(newRayCount, null, null)
                noveltyArchive.clear()
                curriculumLevel = 0.3f
                ruleWinRateEma = 0.5f
                gensSinceCurriculumAdjust = 0
                champWinRateEma = -1f
                bestWinRateEma = 0f
                plateauCount = 0
                diversityInjected = false
                genRate = 0f
                lastSaveMs = 0L
            } finally {
                resetting = false
            }
        }
    }

    fun saveNow() {
        val ev = evolver ?: return
        NeatStorage.save(rayCount, expectedInputCount(), generation,
            bestFitness, simMatches.get(), ev, champion)
    }

    /** 当前射线数对应的网络输入维度（含 bias），存档兼容性校验用。 */
    private fun expectedInputCount(): Int = VisionSensor(rayCount).inputSize() + 1

    // ------------------------------------------------------------ 外部接口

    fun setPaused(p: Boolean) {
        paused = p
        // 暂停（如退后台）时立即落盘，避免距上次存盘 30s 内的进化进度丢失
        if (p) { saveNow(); lastSaveMs = System.currentTimeMillis() }
    }

    fun reportRealMatch(m: MatchStats) {
        // 每场实战重置战后训练预算，训练可再跑 trainRoundLimit 代
        gensSinceLastMatch = 0
        playerWins += if (m.aiWon) 1 else 0
        playerLosses += if (m.aiWon) 0 else 1
        val total = playerWins + playerLosses
        val opponentRating = if (total > 0) 1200f + (playerWins / total.toFloat() - 0.5f) * 200f else 1200f
        championElo = updateElo(championElo, opponentRating, m.aiWon)
        val eloDiff = opponentRating - championElo
        val mult = 1f + (eloDiff / 400f).coerceIn(-0.5f, 0.5f)
        // 缩放到常规适应度量级（胜局=100）：赢玩家≈多赢1.5场模拟，
        // 定向强化冠军血脉但不至于锁死选择、压制种群多样性
        realMatchBonus += (if (m.aiWon) 150f else -15f) * mult + m.teleportKills * 10f
    }

    fun getChampionElo() = championElo

    fun addGhost(g: GhostData?) {
        if (g == null || g.frames < 300) return
        if (g.calculateQuality() < 0.15f) {
            Log.i(TAG, "ghost rejected, quality too low")
            return
        }
        ghosts.add(0, g)
        while (ghosts.size > MAX_GHOSTS) {
            var worst = 0
            var wq = ghosts[0].calculateQuality()
            for (i in 1 until ghosts.size) {
                val q = ghosts[i].calculateQuality()
                if (q < wq) { wq = q; worst = i }
            }
            ghosts.removeAt(worst)
        }
        // JSON 写盘挪到训练线程执行，避免渲染线程 IO 卡顿
        ghostSaveRequested = true
    }

    fun ghostCount() = ghosts.size

    private fun pickGhost(r: Random): GhostData? =
        if (ghosts.isEmpty()) null else ghosts[r.nextInt(ghosts.size)]

    private fun pickChampion(r: Random): Genome? {
        if (championPool.isEmpty()) return null
        // 50% 取最新冠军（通常最强），50% 从池中随机（保持对手多样性）
        if (championPool.size == 1 || r.nextBoolean()) return championPool[0]
        return championPool[r.nextInt(championPool.size)]
    }

    private fun pickHistoricalChampion(r: Random): Genome? =
        if (historicalChampions.isEmpty()) null
        else historicalChampions[r.nextInt(historicalChampions.size)]

    fun currentChampion(): Genome? = champion
    fun evolver(): NeatEvolver? = evolver
    fun generation(): Int = generation

    /** 各物种冠军及其策略标签（供玩家选择对手风格）。快照遍历，跨线程安全。 */
    fun speciesStyleLabels(): Array<String> {
        val ev = evolver ?: return emptyArray()
        val snapshot = ev.currentSpecies
        return Array(snapshot.size) { snapshot[it].strategyLabel }
    }

    /** 根据风格索引获取对应冠军基因组。-1=总冠军，0..N-1=物种冠军，null 表示未就绪 */
    fun styleChampion(styleIndex: Int): Genome? {
        val ev = evolver ?: return null
        if (styleIndex < 0) return champion
        val snapshot = ev.currentSpecies
        if (styleIndex < snapshot.size) {
            val best = snapshot[styleIndex].best
            if (best != null) return best
        }
        return champion
    }

    fun bestFitness() = bestFitness
    fun simMatches() = simMatches.get()
    fun isConverged() = converged
    /** 冠军 vs 规则AI(1.0) 胜率 EMA（<0 表示尚未测量）。 */
    fun championWinRate() = champWinRateEma
    /** 训练速度（gen/s EMA）。 */
    fun genRate() = genRate
    /** 距上次实战已训练的代数（战后预算用量）。 */
    fun gensSinceLastMatch() = gensSinceLastMatch
    /** 战后训练轮数是否已达上限（自动暂停中）。 */
    fun roundLimitReached(): Boolean = gensSinceLastMatch >= app.trainRoundLimit

    // ------------------------------------------------------------ 训练循环

    private fun loop() {
        if (evolver == null) loadOrCreate()
        while (!stopped) {
            // 优先处理重置请求（收敛后也能复活训练）
            if (resetRequested) {
                resetRequested = false
                doReset(pendingRayCount)
                continue
            }
            // 幽灵入库：暂停期间（玩家对战结束上报时）也要执行
            if (ghostSaveRequested) {
                ghostSaveRequested = false
                NeatStorage.saveGhosts(ArrayList(ghosts))
            }
            // 达到战后训练轮数上限视同暂停（下一场实战或调大上限后自动恢复）
            if (paused || converged || roundLimitReached()) { sleep(200); continue }
            try {
                runGeneration()
                checkConvergence()
            } catch (t: Throwable) {
                Log.e(TAG, "gen failed", t)
                sleep(1000)
            }
            // 时间制存盘：高速训练下按代存盘会每秒写数次 JSON（磨损闪存）
            val now = System.currentTimeMillis()
            if (now - lastSaveMs >= SAVE_INTERVAL_MS) {
                lastSaveMs = now
                saveNow()
                verifySave()
            }
        }
    }

    private fun loadOrCreate() {
        val d = NeatStorage.load(rayCount, expectedInputCount())
        if (d != null) {
            generation = d.generation
            bestFitness = d.bestFitness
            simMatches.set(d.simMatches)
            champion = d.champion
            championPool.clear()
            champion?.let { championPool.add(it) }
            evolver = newEvolver(rayCount, d.population,
                Genome.InnovationCounter(d.nextInnovation, d.nextNode))
            // 按已训练的代数估计课程起点，避免老存档从 0.3 重新爬坡
            curriculumLevel = progressiveLevel(generation)
            Log.i(TAG, "loaded gen $generation best $bestFitness" +
                    " curriculum ${"%.2f".format(curriculumLevel)}")
        } else {
            evolver = newEvolver(rayCount, null, null)
        }
        val loaded = NeatStorage.loadGhosts()
        for (g in loaded) {
            if (ghosts.size >= MAX_GHOSTS) break
            ghosts.add(g)
        }
        if (ghosts.isNotEmpty()) Log.i(TAG, "loaded ghosts: ${ghosts.size}")
    }

    private fun newEvolver(rays: Int, pop: ArrayList<Genome>?,
                           counter: Genome.InnovationCounter?): NeatEvolver {
        val inputCount = VisionSensor(rays).inputSize() + 1
        return if (counter == null) NeatEvolver(inputCount, 5, dynamicPopulation, rng)
        else NeatEvolver(inputCount, 5, dynamicPopulation, pop!!, counter, rng)
    }

    // ------------------------------------------------------------ 代执行

    private fun runGeneration() {
        if (resetting) return
        val genStart = System.currentTimeMillis()

        val ev: NeatEvolver
        synchronized(evolverLock) { ev = evolver ?: return }

        val pop = ev.population
        val fitness = FloatArray(pop.size)
        val shaping = shapingScale(generation)
        val level = curriculumLevel

        val bonusToApply = realMatchBonus
        realMatchBonus = 0f

        val total = pop.size
        val latch = CountDownLatch(total)
        val popView = CopyOnWriteArrayList(pop)
        val sigs = arrayOfNulls<BehaviorSignature>(total)
        val ruleWins = AtomicInteger()
        val pool = threadPool ?: return

        // 1. 主评估：每个体 vs 规则AI + 冠军 + 幽灵 + 历史冠军
        for (i in 0 until total) {
            val index = i
            val g = pop[i]
            pool.execute {
                if (stopped || paused || resetting) { latch.countDown(); return@execute }
                val r = threadRng()
                var f = 0f
                var n = 0

                // 第一局 vs 规则AI：捕获行为签名 (C)
                val firstStats = simulate(g, null, null, level, r)
                if (firstStats.aiWon) ruleWins.addAndGet(1)
                f += firstStats.fitness(shaping); n++
                sigs[index] = BehaviorSignature.from(firstStats, g)
                simMatches.addAndGet(1)
                for (s in 1 until dynamicSimsPerMatch) {
                    f += simulate(g, null, null, level, r).fitness(shaping); n++
                    simMatches.addAndGet(1)
                }
                // 每个体独立随机抽取对手，降低全种群对单一对手风格的相关性过拟合
                val champOpponent = pickChampion(r)
                val ghost = pickGhost(r)
                val histChampion = pickHistoricalChampion(r)
                if (champOpponent != null) { f += evaluate(g, champOpponent, null, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch.toLong()) }
                if (ghost != null) { f += evaluate(g, null, ghost, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch.toLong()) }
                if (histChampion != null) { f += evaluate(g, histChampion, null, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch.toLong()) }

                f /= n

                // 策略多样性奖励（基于真实行为签名，非适应度数值）
                val profile = sigs[index]?.profile() ?: "unknown"
                synchronized(strategyCounts) {
                    val cnt = (strategyCounts[profile] ?: 0) + 1
                    strategyCounts[profile] = cnt
                    val rarity = 1f - cnt / total.toFloat()
                    f += rarity * 8f * shaping
                }

                fitness[index] = f
                latch.countDown()
            }
        }

        try { latch.await() } catch (e: InterruptedException) { return }
        // 暂停/停止/重置时中止本代，避免残缺适应度（跳过个体=0分）污染进化
        if (stopped || paused || resetting) return

        // 自适应课程：EMA 平滑胜率 + 最小间隔 5 代，抑制高速训练下的难度振荡
        val instantWinRate = ruleWins.get() / total.toFloat()
        ruleWinRateEma = ruleWinRateEma * 0.75f + instantWinRate * 0.25f
        gensSinceCurriculumAdjust++
        if (gensSinceCurriculumAdjust >= 5) {
            val oldLevel = curriculumLevel
            if (ruleWinRateEma > 0.65f) curriculumLevel += 0.02f
            else if (ruleWinRateEma > 0.5f) curriculumLevel += 0.008f
            else if (ruleWinRateEma < 0.3f) curriculumLevel -= 0.01f
            curriculumLevel = curriculumLevel.coerceIn(0.3f, 1.0f)
            if (curriculumLevel != oldLevel) {
                gensSinceCurriculumAdjust = 0
                Log.i(TAG, "curriculum ${"%.2f".format(oldLevel)}" +
                        " -> ${"%.2f".format(curriculumLevel)}" +
                        " (winRateEma ${"%.2f".format(ruleWinRateEma)})")
            }
        }

        // 2. 同代对战（动态轮数）—— 等主评估全完成再提交，避免竞态覆盖
        val sameGenTasks = dynamicSelfPlayRounds * (total / 2)
        val sameGenLatch = CountDownLatch(sameGenTasks)
        for (round in 0 until dynamicSelfPlayRounds) {
            val idx = ArrayList<Int>(total)
            for (i in 0 until total) idx.add(i)
            Collections.shuffle(idx, rng)
            for (i in 0 until idx.size / 2) {
                val a = idx[2 * i]
                val b = idx[2 * i + 1]
                val ga = popView[a]
                val gb = popView[b]
                pool.execute {
                    val fA = evaluate(ga, gb, null, shaping, 1.0f)
                    val fB = evaluate(gb, ga, null, shaping, 1.0f)
                    synchronized(fitness) { fitness[a] += fA * 0.3f; fitness[b] += fB * 0.3f }
                    simMatches.addAndGet((dynamicSimsPerMatch * 2).toLong())
                    sameGenLatch.countDown()
                }
            }
        }

        try { sameGenLatch.await() } catch (e: InterruptedException) { return }
        if (stopped || paused || resetting) return

        // 3. 新颖性奖励 (C)：与存档+同代相比越独特越加分
        for (i in 0 until total) {
            val sig = sigs[i]
            if (sig != null) {
                val nov = noveltyArchive.novelty(sig)
                val peerNov = NoveltyArchive.peerNovelty(sig, sigs, total)
                fitness[i] += (nov * 0.6f + peerNov * 0.4f) * 12f * shaping // 最多+12（非常独特时）
            }
        }

        // 实战奖励定向强化冠军血脉（与玩家对战的对手），而非随机高分个体
        if (bonusToApply != 0f && champion != null) {
            var ci = 0
            var cd = Float.MAX_VALUE
            for (i in 0 until total) {
                val d = pop[i].distance(champion!!)
                if (d < cd) { cd = d; ci = i }
            }
            fitness[ci] += bonusToApply
        }

        var best = 0
        for (i in 1 until fitness.size) if (fitness[i] > fitness[best]) best = i

        synchronized(evolverLock) {
            if (ev !== evolver || resetting) return

            val newChamp = pop[best].copy()
            championPool.add(0, newChamp)
            while (championPool.size > CHAMPION_POOL_SIZE) championPool.removeAt(championPool.size - 1)
            champion = newChamp
            bestFitness = max(bestFitness, fitness[best])
            ev.nextGeneration(fitness)
            generation++
            gensSinceLastMatch++

            // 更新新颖性存档 (C)
            for (i in 0 until total) {
                sigs[i]?.let { noveltyArchive.tryAdd(it) }
            }

            // 名人堂入库（高速训练下 5 代一入会让 16 个槽位只覆盖几秒进化史）
            if (generation % HISTORICAL_INTERVAL == 0) {
                historicalChampions.add(newChamp.copy())
                while (historicalChampions.size > HISTORICAL_CHAMPION_SIZE) historicalChampions.removeAt(0)
            }
        }

        // 每10代清策略统计
        if (generation % 10 == 0) { synchronized(strategyCounts) { strategyCounts.clear() } }

        // ---- 代速率 EMA + 老旧设备看门狗：代均 >6s 自动降模拟局数 ----
        val genMs = System.currentTimeMillis() - genStart
        if (genMs > 0) {
            val instant = 1000f / genMs
            genRate = if (genRate <= 0f) instant else genRate * 0.8f + instant * 0.2f
        }
        if (generation >= 10 && generation % 10 == 0 && genMs > 6000L && dynamicSimsPerMatch > 3) {
            dynamicSimsPerMatch = max(3, dynamicSimsPerMatch - 2)
            Log.i(TAG, "slow gen (${genMs}ms), sims -> $dynamicSimsPerMatch")
        }
    }

    // ------------------------------------------------------------ 评估/模拟

    private fun evaluate(candidate: Genome, vsGenome: Genome?, ghost: GhostData?,
                         shaping: Float, level: Float): Float {
        val r = threadRng()
        var sum = 0f
        for (i in 0 until dynamicSimsPerMatch) {
            sum += simulate(candidate, vsGenome, ghost, level, r).fitness(shaping)
        }
        return sum / dynamicSimsPerMatch
    }

    private fun simulate(candidate: Genome, vsGenome: Genome?,
                         ghost: GhostData?, level: Float, rngLocal: Random): MatchStats {
        val rays = rayCount

        // ---- 池化引擎 A (被评估 AI)：同基因组多局只构建一次网络 ----
        var engA = pooledEngineA.get()
        if (engA == null || engA.rayCount() != rays) {
            engA = NeatEngine(candidate, rays)
            pooledEngineA.set(engA)
        } else if (engA.genome() !== candidate) {
            engA.setGenome(candidate, rays)
        }
        engA.reset()
        // 训练与实战推理频率保持一致（AI Speed 设置）
        engA.setSkipFrames(app.aiSpeed + 1)
        val engineA = engA

        // ---- 引擎 B (对手) ----
        val factoryB: GameSystem.EngineFactory
        if (ghost != null) {
            val rp = ReplayEngine(ghost)
            rp.reset()
            factoryB = GameSystem.EngineFactory { rp }
        } else if (vsGenome != null) {
            var engB = pooledEngineB.get()
            if (engB == null || engB.rayCount() != rays) {
                engB = NeatEngine(vsGenome, rays)
                pooledEngineB.set(engB)
            } else if (engB.genome() !== vsGenome) {
                engB.setGenome(vsGenome, rays)
            }
            engB.reset()
            engB.setSkipFrames(app.aiSpeed + 1)
            val eB = engB
            factoryB = GameSystem.EngineFactory { eB }
        } else {
            factoryB = GameSystem.EngineFactory { s -> ComputerEngine(s, level) }
        }

        val sys = GameSystem(app, false, false, 1.0f, null,
            GameSystem.EngineFactory { engineA }, factoryB, true, Random(rngLocal.nextLong()))

        // ---- 池化 Stats/Tracker ----
        var tracker = pooledTracker.get()
        if (tracker == null) { tracker = MatchTracker(sys.myGroup); pooledTracker.set(tracker) }
        else tracker.reset(sys.myGroup)

        // 无头模拟已跳过开局倒计时：循环上限为纯对战帧数，统计帧数补偿回倒计时
        val maxBattleFrames = MAX_MATCH_FRAMES - MatchStats.COUNTDOWN_FRAMES
        while (sys.currentState !is ResultGameState && sys.frameCount < maxBattleFrames) {
            sys.update()
            tracker.update()
        }

        var m = pooledStats.get()
        if (m == null) { m = MatchStats(); pooledStats.set(m) }
        else m.reset()
        m.frames = sys.frameCount + MatchStats.COUNTDOWN_FRAMES
        val rs = sys.currentState
        if (rs is ResultGameState) {
            m.aiWon = rs.winGroup == sys.myGroup.id
        }
        m.hitsDealt = sys.otherGroup.damageCount
        m.hitsTaken = sys.myGroup.damageCount
        tracker.fill(m)
        return m
    }

    // ------------------------------------------------------------ 早停 / 多样性

    /**
     * 智能平台期收敛：以冠军 vs 规则AI(1.0) 胜率 EMA 为跨代可比进度指标。
     * （旧机制用原始适应度取历史最大，而 shaping 随代数衰减 → 必然误判停滞，
     *   高速训练下几分钟就假收敛停摆。）
     * 平台期先注入多样性复活一次，仍无进展才判定收敛。
     */
    private fun checkConvergence() {
        if (generation % 10 == 0) {
            val div = calculateDiversity()
            Log.i(TAG, "gen $generation diversity=${"%.3f".format(div)}" +
                    " threshold=${evolver?.compatThreshold ?: 3.0f}" +
                    " winRate=${if (champWinRateEma < 0) "?" else "%.2f".format(champWinRateEma)}")
            if (div < 0.03f && generation >= MIN_GENS_FOR_STOP) tryReviveOrStop("population converged")
        }

        if (champion == null || generation % CHAMP_EVAL_INTERVAL != 0) return
        val wr = measureChampionWinRate()
        champWinRateEma = if (champWinRateEma < 0) wr else champWinRateEma * 0.7f + wr * 0.3f
        if (champWinRateEma > bestWinRateEma + 0.02f) {
            bestWinRateEma = champWinRateEma
            plateauCount = 0
            diversityInjected = false
        } else if (++plateauCount >= PLATEAU_LIMIT && generation >= MIN_GENS_FOR_STOP) {
            tryReviveOrStop("winrate plateau ${"%.2f".format(champWinRateEma)}")
        }
    }

    /** 冠军对规则AI(1.0) 胜率：训练进度的真实可比指标。 */
    private fun measureChampionWinRate(): Float {
        val c = champion ?: return 0f
        var wins = 0
        for (i in 0 until CHAMP_EVAL_SIMS) {
            if (stopped || paused || resetting) break
            if (simulate(c, null, null, 1.0f, rng).aiWon) wins++
        }
        simMatches.addAndGet(CHAMP_EVAL_SIMS.toLong())
        return wins / CHAMP_EVAL_SIMS.toFloat()
    }

    /** 平台期处理：先注入多样性复活一次；复活后仍停滞才收敛停训。 */
    private fun tryReviveOrStop(reason: String) {
        if (!diversityInjected) {
            diversityInjected = true
            plateauCount = 0
            synchronized(evolverLock) {
                evolver?.injectDiversity()
            }
            Log.i(TAG, "plateau ($reason), injecting diversity")
        } else {
            converged = true
            Log.i(TAG, "converged gen $generation ($reason)" +
                    " winRate ${"%.2f".format(champWinRateEma)}")
        }
    }

    private fun calculateDiversity(): Float {
        val ev = evolver
        if (ev == null || ev.population.isEmpty()) return 0f
        val pop = ev.population
        val sample = min(20, pop.size)
        var total = 0f
        var count = 0
        for (i in 0 until sample)
            for (j in i + 1 until sample) { total += pop[i].distance(pop[j]); count++ }
        return if (count > 0) total / count else 0f
    }

    private fun verifySave() {
        try {
            val loaded = NeatStorage.load(rayCount, expectedInputCount())
            if (loaded == null || loaded.generation != generation)
                Log.e(TAG, "save verify FAIL gen $generation")
        } catch (t: Throwable) {
            Log.e(TAG, "save verify error", t)
        }
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (e: InterruptedException) { }
    }
}
