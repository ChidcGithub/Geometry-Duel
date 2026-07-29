package com.geometryduel.neat;

import com.badlogic.gdx.Gdx;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.game.GameSystem;
import com.geometryduel.game.engine.ComputerEngine;
import com.geometryduel.game.engine.PlayerEngine;
import com.geometryduel.game.state.ResultGameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class NeatTrainer {
    public static final int POPULATION = 150;
    private static final int MAX_MATCH_FRAMES = 180 + 7200;
    private static final int MAX_GHOSTS = 20;
    private static final int CHAMPION_POOL_SIZE = 8;
    private static final int HISTORICAL_CHAMPION_SIZE = 16;
    private static final int HISTORICAL_INTERVAL = 25;   // 名人堂入库间隔（代）
    private static final long SAVE_INTERVAL_MS = 30000L; // 存盘时间制：高速训练不再按代刷屏写盘

    // ---- 智能平台期收敛：以冠军 vs 规则AI(1.0) 胜率为可比进度指标 ----
    private static final int CHAMP_EVAL_INTERVAL = 10;   // 每 10 代测一次冠军胜率
    private static final int CHAMP_EVAL_SIMS = 6;
    private static final int MIN_GENS_FOR_STOP = 200;    // 最小进化代数门槛
    private static final int PLATEAU_LIMIT = 15;         // 15 次测量（≈150 代）无进展判平台期

    // 硬件感知的动态参数（质量优先，老旧设备自动降档）
    private int dynamicPopulation;
    private int dynamicSimsPerMatch;
    private int dynamicSelfPlayRounds;

    private final GeometryDuelGame app;
    private final Random rng = new Random();
    private final Object evolverLock = new Object();

    private ExecutorService threadPool;
    private final ThreadLocal<Random> rngPerThread = new ThreadLocal<Random>() {
        @Override
        protected Random initialValue() { return new Random(); }
    };
    private final ThreadLocal<NeatEngine> pooledEngineA = new ThreadLocal<>();
    private final ThreadLocal<NeatEngine> pooledEngineB = new ThreadLocal<>();
    private final ThreadLocal<MatchTracker> pooledTracker = new ThreadLocal<>();
    private final ThreadLocal<MatchStats> pooledStats = new ThreadLocal<>();
    private final ThreadLocal<BehaviorSignature> pooledSig = new ThreadLocal<>();

    private volatile int rayCount;
    private volatile NeatEvolver evolver;
    private volatile Genome champion;
    private final CopyOnWriteArrayList<Genome> championPool = new CopyOnWriteArrayList<Genome>();
    private final ArrayList<Genome> historicalChampions = new ArrayList<Genome>();
    private final HashMap<String, Integer> strategyCounts = new HashMap<String, Integer>();

    private volatile int generation;
    private volatile float bestFitness;
    private final AtomicLong simMatches = new AtomicLong();
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile boolean resetting;
    private volatile boolean converged;
    /** 自适应课程：对规则AI的当前难度（0.3~1.0），按 EMA 胜率动态升降。 */
    private float curriculumLevel = 0.3f;
    private float ruleWinRateEma = 0.5f;
    private int gensSinceCurriculumAdjust;

    // ---- 智能收敛状态 ----
    private volatile float champWinRateEma = -1f;  // 冠军胜率 EMA（-1=未测量）
    private float bestWinRateEma;
    private int plateauCount;
    private boolean diversityInjected;

    // ---- 训练速度指标与看门狗 ----
    private volatile float genRate;                // gen/s EMA
    private long lastSaveMs;
    /** 重置请求：由渲染线程发起、训练线程执行，避免在 UI 线程做重活。 */
    private volatile boolean resetRequested;
    private volatile int pendingRayCount;
    /** 幽灵入库请求：IO 由训练线程执行，避免渲染线程写大 JSON。 */
    private volatile boolean ghostSaveRequested;
    private volatile float realMatchBonus;
    private float championElo = 1200f;
    private int playerWins, playerLosses;
    private final CopyOnWriteArrayList<GhostData> ghosts = new CopyOnWriteArrayList<GhostData>();
    private final NoveltyArchive noveltyArchive = new NoveltyArchive(rng);

    private Thread thread;

    public NeatTrainer(GeometryDuelGame app, int rayCount) {
        this.app = app;
        this.rayCount = rayCount;
    }

    private void initializeDynamicParameters() {
        boolean hasNpu = app.hardware.npuInfo != null && !app.hardware.npuInfo.contains("None");
        int cores = Runtime.getRuntime().availableProcessors();

        // 质量优先的分级：充足算力换更准的适应度与更多样的对手；老旧设备自动降档
        if (hasNpu || cores >= 8) {
            dynamicPopulation = hasNpu ? POPULATION * 3 / 2 : POPULATION;
            dynamicSimsPerMatch = 8;
            dynamicSelfPlayRounds = 3;
        } else if (cores >= 5) {
            dynamicPopulation = POPULATION;
            dynamicSimsPerMatch = 6;
            dynamicSelfPlayRounds = 3;
        } else {
            // 老旧设备（≤4 核）：保住可训练性，由代耗时看门狗进一步兜底
            dynamicPopulation = POPULATION * 2 / 3;
            dynamicSimsPerMatch = 4;
            dynamicSelfPlayRounds = 2;
        }

        Gdx.app.log("NeatTrainer", "dynamic params: pop=" + dynamicPopulation +
                    ", sims=" + dynamicSimsPerMatch + ", selfPlayRounds=" + dynamicSelfPlayRounds);
    }

    // ------------------------------------------------------------ 生命周期

    public void start() {
        app.hardware.detect();
        initializeDynamicParameters();

        int cores = Runtime.getRuntime().availableProcessors();
        int workers;
        if (app.hardware.npuInfo != null && !app.hardware.npuInfo.contains("None")) {
            workers = Math.max(8, cores * 2);
        } else {
            workers = Math.max(4, cores);
        }
        workers = Math.min(workers, 32);

        threadPool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "neat-eval-" + Integer.toHexString(r.hashCode()));
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        });
        thread = new Thread(() -> loop(), "neat-trainer");
        thread.setDaemon(true);
        thread.start();
        Gdx.app.log("NeatTrainer", "started with " + workers + " workers, NPU: " + app.hardware.npuInfo);
    }

    public void shutdown() {
        stopped = true;
        if (threadPool != null) threadPool.shutdownNow();
        if (thread != null) { try { thread.join(3000); } catch (InterruptedException e) {} }
        saveNow();
    }

    /**
     * 请求重置训练（渲染线程安全，立即返回）。
     * 实际重置由训练线程执行，连点自动合并为一次。
     */
    public void requestReset(int newRayCount) {
        pendingRayCount = newRayCount;
        resetRequested = true;
    }

    /** 实际重置：仅在训练线程调用。 */
    private void doReset(int newRayCount) {
        synchronized (evolverLock) {
            resetting = true;
            try {
                rayCount = newRayCount;
                NeatStorage.clear();
                ghosts.clear();
                ghostSaveRequested = false;
                champion = null;
                championPool.clear();
                historicalChampions.clear();
                strategyCounts.clear();
                generation = 0;
                bestFitness = 0f;
                simMatches.set(0);
                realMatchBonus = 0f;
                championElo = 1200f;
                playerWins = playerLosses = 0;
                converged = false;
                evolver = newEvolver(newRayCount, null, null);
                noveltyArchive.clear();
                curriculumLevel = 0.3f;
                ruleWinRateEma = 0.5f;
                gensSinceCurriculumAdjust = 0;
                champWinRateEma = -1f;
                bestWinRateEma = 0f;
                plateauCount = 0;
                diversityInjected = false;
                genRate = 0f;
                lastSaveMs = 0L;
            } finally {
                resetting = false;
            }
        }
    }

    public void saveNow() {
        NeatEvolver ev = evolver;
        if (ev != null) NeatStorage.save(rayCount, expectedInputCount(), generation,
                bestFitness, simMatches.get(), ev, champion);
    }

    /** 当前射线数对应的网络输入维度（含 bias），存档兼容性校验用。 */
    private int expectedInputCount() {
        return new VisionSensor(rayCount).inputSize() + 1;
    }

    // ------------------------------------------------------------ 外部接口

    public void setPaused(boolean p) {
        paused = p;
        // 暂停（如退后台）时立即落盘，避免距上次存盘 30s 内的进化进度丢失
        if (p) { saveNow(); lastSaveMs = System.currentTimeMillis(); }
    }

    public void reportRealMatch(MatchStats m) {
        playerWins += m.aiWon ? 1 : 0;
        playerLosses += m.aiWon ? 0 : 1;
        int total = playerWins + playerLosses;
        float opponentRating = total > 0 ? 1200f + (playerWins / (float) total - 0.5f) * 200f : 1200f;
        championElo = updateElo(championElo, opponentRating, m.aiWon);
        float eloDiff = opponentRating - championElo;
        float mult = 1f + Math.max(-0.5f, Math.min(0.5f, eloDiff / 400f));
        realMatchBonus += (m.aiWon ? 1000f : -30f) * mult + m.teleportKills * 15f;
    }

    private static float updateElo(float rating, float oppRating, boolean won) {
        float expected = 1f / (1f + (float) Math.pow(10, (oppRating - rating) / 400f));
        return rating + 32f * ((won ? 1f : 0f) - expected);
    }

    public float getChampionElo() { return championElo; }

    public void addGhost(GhostData g) {
        if (g == null || g.frames < 300) return;
        if (g.calculateQuality() < 0.15f) {
            Gdx.app.log("NeatTrainer", "ghost rejected, quality too low");
            return;
        }
        ghosts.add(0, g);
        while (ghosts.size() > MAX_GHOSTS) {
            int worst = 0;
            float wq = ghosts.get(0).calculateQuality();
            for (int i = 1; i < ghosts.size(); i++) {
                float q = ghosts.get(i).calculateQuality();
                if (q < wq) { wq = q; worst = i; }
            }
            ghosts.remove(worst);
        }
        // JSON 写盘挪到训练线程执行，避免渲染线程 IO 卡顿
        ghostSaveRequested = true;
    }

    public int ghostCount() { return ghosts.size(); }

    private GhostData pickGhost(Random r) {
        if (ghosts.isEmpty()) return null;
        return ghosts.get(r.nextInt(ghosts.size()));
    }

    private Genome pickChampion(Random r) {
        if (championPool.isEmpty()) return null;
        // 50% 取最新冠军（通常最强），50% 从池中随机（保持对手多样性）
        if (championPool.size() == 1 || r.nextBoolean()) return championPool.get(0);
        return championPool.get(r.nextInt(championPool.size()));
    }

    private Genome pickHistoricalChampion(Random r) {
        if (historicalChampions.isEmpty()) return null;
        return historicalChampions.get(r.nextInt(historicalChampions.size()));
    }

    public Genome currentChampion() { return champion; }
    public NeatEvolver evolver() { return evolver; }
    public int generation() { return generation; }

    /** 各物种冠军及其策略标签（供玩家选择对手风格）。快照遍历，跨线程安全。 */
    public String[] speciesStyleLabels() {
        NeatEvolver ev = evolver;
        if (ev == null) return new String[0];
        ArrayList<NeatEvolver.SpeciesInfo> snapshot = ev.currentSpecies;
        String[] labels = new String[snapshot.size()];
        for (int i = 0; i < labels.length; i++)
            labels[i] = snapshot.get(i).strategyLabel;
        return labels;
    }

    /** 根据风格索引获取对应冠军基因组。-1=总冠军，0..N-1=物种冠军，null 表示未就绪 */
    public Genome styleChampion(int styleIndex) {
        NeatEvolver ev = evolver;
        if (ev == null) return null;
        if (styleIndex < 0) return champion;
        ArrayList<NeatEvolver.SpeciesInfo> snapshot = ev.currentSpecies;
        if (styleIndex < snapshot.size()) {
            Genome best = snapshot.get(styleIndex).best;
            if (best != null) return best;
        }
        return champion;
    }
    public float bestFitness() { return bestFitness; }
    public long simMatches() { return simMatches.get(); }
    public boolean isConverged() { return converged; }
    /** 冠军 vs 规则AI(1.0) 胜率 EMA（<0 表示尚未测量）。 */
    public float championWinRate() { return champWinRateEma; }
    /** 训练速度（gen/s EMA）。 */
    public float genRate() { return genRate; }

    // ------------------------------------------------------------ 训练循环

    private void loop() {
        if (evolver == null) loadOrCreate();
        while (!stopped) {
            // 优先处理重置请求（收敛后也能复活训练）
            if (resetRequested) {
                resetRequested = false;
                doReset(pendingRayCount);
                continue;
            }
            // 幽灵入库：暂停期间（玩家对战结束上报时）也要执行
            if (ghostSaveRequested) {
                ghostSaveRequested = false;
                NeatStorage.saveGhosts(new ArrayList<GhostData>(ghosts));
            }
            if (paused || converged) { sleep(200); continue; }
            try { runGeneration(); checkConvergence(); }
            catch (Throwable t) { Gdx.app.error("NeatTrainer", "gen failed", t); sleep(1000); }
            // 时间制存盘：高速训练下按代存盘会每秒写数次 JSON（磨损闪存）
            long now = System.currentTimeMillis();
            if (now - lastSaveMs >= SAVE_INTERVAL_MS) {
                lastSaveMs = now;
                saveNow();
                verifySave();
            }
        }
    }

    private void loadOrCreate() {
        NeatStorage.SaveData d = NeatStorage.load(rayCount, expectedInputCount());
        if (d != null) {
            generation = d.generation;
            bestFitness = d.bestFitness;
            simMatches.set(d.simMatches);
            champion = d.champion;
            championPool.clear();
            if (champion != null) championPool.add(champion);
            evolver = newEvolver(rayCount, d.population,
                    new Genome.InnovationCounter(d.nextInnovation, d.nextNode));
            // 按已训练的代数估计课程起点，避免老存档从 0.3 重新爬坡
            curriculumLevel = progressiveLevel(generation);
            Gdx.app.log("NeatTrainer", "loaded gen " + generation + " best " + bestFitness
                    + " curriculum " + String.format("%.2f", curriculumLevel));
        } else {
            evolver = newEvolver(rayCount, null, null);
        }
        ArrayList<GhostData> loaded = NeatStorage.loadGhosts();
        for (int i = 0; i < loaded.size() && ghosts.size() < MAX_GHOSTS; i++) ghosts.add(loaded.get(i));
        if (!ghosts.isEmpty()) Gdx.app.log("NeatTrainer", "loaded ghosts: " + ghosts.size());
    }

    private NeatEvolver newEvolver(int rays, ArrayList<Genome> pop, Genome.InnovationCounter counter) {
        int inputCount = new VisionSensor(rays).inputSize() + 1;
        return counter == null ? new NeatEvolver(inputCount, 5, dynamicPopulation, rng)
                : new NeatEvolver(inputCount, 5, dynamicPopulation, pop, counter, rng);
    }

    public static float shapingScale(int generation) {
        return 0.2f + 2.8f * (float) Math.exp(-generation / 80.0);
    }

    private static float progressiveLevel(int generation) {
        return Math.min(1.0f, 0.3f + generation * 0.005f);
    }

    // ------------------------------------------------------------ 代执行

    private void runGeneration() {
        if (resetting) return;
        final long genStart = System.currentTimeMillis();

        final NeatEvolver ev;
        synchronized (evolverLock) { ev = evolver; }
        if (ev == null) return;

        final ArrayList<Genome> pop = ev.population;
        final float[] fitness = new float[pop.size()];
        final float shaping = shapingScale(generation);
        final float level = curriculumLevel;

        final float bonusToApply = realMatchBonus;
        realMatchBonus = 0f;

        final int total = pop.size();
        final CountDownLatch latch = new CountDownLatch(total);
        final CopyOnWriteArrayList<Genome> popView = new CopyOnWriteArrayList<Genome>(pop);
        final BehaviorSignature[] sigs = new BehaviorSignature[total];
        final AtomicInteger ruleWins = new AtomicInteger();

        // 1. 主评估：每个体 vs 规则AI + 冠军 + 幽灵 + 历史冠军
        for (int i = 0; i < total; i++) {
            final int index = i;
            final Genome g = pop.get(i);
            threadPool.execute(() -> {
                if (stopped || paused || resetting) { latch.countDown(); return; }
                final Random r = rngPerThread.get();
                float f = 0f;
                int n = 0;

                // 第一局 vs 规则AI：捕获行为签名 (C)
                MatchStats firstStats = simulate(g, null, null, level, r);
                if (firstStats.aiWon) ruleWins.addAndGet(1);
                f += firstStats.fitness(shaping); n++;
                sigs[index] = BehaviorSignature.from(firstStats, g);
                simMatches.addAndGet(1);
                for (int s = 1; s < dynamicSimsPerMatch; s++) {
                    f += simulate(g, null, null, level, r).fitness(shaping); n++;
                    simMatches.addAndGet(1);
                }
                // 每个体独立随机抽取对手，降低全种群对单一对手风格的相关性过拟合
                final Genome champOpponent = pickChampion(r);
                final GhostData ghost = pickGhost(r);
                final Genome histChampion = pickHistoricalChampion(r);
                if (champOpponent != null) { f += evaluate(g, champOpponent, null, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch); }
                if (ghost != null) { f += evaluate(g, null, ghost, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch); }
                if (histChampion != null) { f += evaluate(g, histChampion, null, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch); }

                f /= n;

                // 策略多样性奖励（基于真实行为签名，非适应度数值）
                String profile = sigs[index] != null ? sigs[index].profile() : "unknown";
                synchronized (strategyCounts) {
                    int cnt = strategyCounts.getOrDefault(profile, 0) + 1;
                    strategyCounts.put(profile, cnt);
                    float rarity = 1f - cnt / (float) total;
                    f += rarity * 8f * shaping;
                }

                fitness[index] = f;
                latch.countDown();
            });
        }

        try { latch.await(); } catch (InterruptedException e) { return; }
        // 暂停/停止/重置时中止本代，避免残缺适应度（跳过个体=0分）污染进化
        if (stopped || paused || resetting) return;

        // 自适应课程：EMA 平滑胜率 + 最小间隔 5 代，抑制高速训练下的难度振荡
        float instantWinRate = ruleWins.get() / (float) total;
        ruleWinRateEma = ruleWinRateEma * 0.75f + instantWinRate * 0.25f;
        gensSinceCurriculumAdjust++;
        if (gensSinceCurriculumAdjust >= 5) {
            float oldLevel = curriculumLevel;
            if (ruleWinRateEma > 0.65f) curriculumLevel += 0.02f;
            else if (ruleWinRateEma > 0.5f) curriculumLevel += 0.008f;
            else if (ruleWinRateEma < 0.3f) curriculumLevel -= 0.01f;
            curriculumLevel = Math.max(0.3f, Math.min(1.0f, curriculumLevel));
            if (curriculumLevel != oldLevel) {
                gensSinceCurriculumAdjust = 0;
                Gdx.app.log("NeatTrainer", "curriculum " + String.format("%.2f", oldLevel)
                        + " -> " + String.format("%.2f", curriculumLevel)
                        + " (winRateEma " + String.format("%.2f", ruleWinRateEma) + ")");
            }
        }

        // 2. 同代对战（动态轮数）—— 等主评估全完成再提交，避免竞态覆盖
        int sameGenTasks = dynamicSelfPlayRounds * (total / 2);
        final CountDownLatch sameGenLatch = new CountDownLatch(sameGenTasks);
        for (int round = 0; round < dynamicSelfPlayRounds; round++) {
            ArrayList<Integer> idx = new ArrayList<Integer>();
            for (int i = 0; i < total; i++) idx.add(i);
            Collections.shuffle(idx, rng);
            for (int i = 0; i < idx.size() / 2; i++) {
                final int a = idx.get(2 * i), b = idx.get(2 * i + 1);
                final Genome ga = popView.get(a), gb = popView.get(b);
                threadPool.execute(() -> {
                    float fA = evaluate(ga, gb, null, shaping, 1.0f);
                    float fB = evaluate(gb, ga, null, shaping, 1.0f);
                    synchronized (fitness) { fitness[a] += fA * 0.3f; fitness[b] += fB * 0.3f; }
                    simMatches.addAndGet(dynamicSimsPerMatch * 2);
                    sameGenLatch.countDown();
                });
            }
        }

        try { sameGenLatch.await(); } catch (InterruptedException e) { return; }
        if (stopped || paused || resetting) return;

        // 3. 新颖性奖励 (C)：与存档+同代相比越独特越加分
        for (int i = 0; i < total; i++) {
            if (sigs[i] != null) {
                float nov = noveltyArchive.novelty(sigs[i]);
                float peerNov = NoveltyArchive.peerNovelty(sigs[i], sigs, total);
                fitness[i] += (nov * 0.6f + peerNov * 0.4f) * 12f * shaping; // 最多+12（非常独特时）
            }
        }

        // 实战奖励定向强化冠军血脉（与玩家对战的对手），而非随机高分个体
        if (bonusToApply != 0f && champion != null) {
            int ci = 0;
            float cd = Float.MAX_VALUE;
            for (int i = 0; i < total; i++) {
                float d = pop.get(i).distance(champion);
                if (d < cd) { cd = d; ci = i; }
            }
            fitness[ci] += bonusToApply;
        }

        int best = 0;
        for (int i = 1; i < fitness.length; i++) if (fitness[i] > fitness[best]) best = i;

        synchronized (evolverLock) {
            if (ev != evolver || resetting) return;

            Genome newChamp = pop.get(best).copy();
            championPool.add(0, newChamp);
            while (championPool.size() > CHAMPION_POOL_SIZE) championPool.remove(championPool.size() - 1);
            champion = newChamp;
            bestFitness = Math.max(bestFitness, fitness[best]);
            ev.nextGeneration(fitness);
            generation++;

            // 更新新颖性存档 (C)
            for (int i = 0; i < total; i++) {
                if (sigs[i] != null) noveltyArchive.tryAdd(sigs[i]);
            }

            // 名人堂入库（高速训练下 5 代一入会让 16 个槽位只覆盖几秒进化史）
            if (generation % HISTORICAL_INTERVAL == 0) {
                historicalChampions.add(newChamp.copy());
                while (historicalChampions.size() > HISTORICAL_CHAMPION_SIZE) historicalChampions.remove(0);
            }
        }

        // 每10代清策略统计
        if (generation % 10 == 0) { synchronized (strategyCounts) { strategyCounts.clear(); } }

        // ---- 代速率 EMA + 老旧设备看门狗：代均 >6s 自动降模拟局数 ----
        long genMs = System.currentTimeMillis() - genStart;
        if (genMs > 0) {
            float instant = 1000f / genMs;
            genRate = genRate <= 0f ? instant : genRate * 0.8f + instant * 0.2f;
        }
        if (generation >= 10 && generation % 10 == 0 && genMs > 6000L && dynamicSimsPerMatch > 3) {
            dynamicSimsPerMatch = Math.max(3, dynamicSimsPerMatch - 2);
            Gdx.app.log("NeatTrainer", "slow gen (" + genMs + "ms), sims -> " + dynamicSimsPerMatch);
        }
    }

    // ------------------------------------------------------------ 评估/模拟

    private float evaluate(Genome candidate, Genome vsGenome, GhostData ghost, float shaping, float level) {
        Random r = rngPerThread.get();
        float sum = 0f;
        for (int i = 0; i < dynamicSimsPerMatch; i++) {
            sum += simulate(candidate, vsGenome, ghost, level, r).fitness(shaping);
        }
        return sum / dynamicSimsPerMatch;
    }

    private MatchStats simulate(final Genome candidate, final Genome vsGenome,
                                final GhostData ghost, final float level, final Random rngLocal) {
        final int rays = rayCount;

        // ---- 池化引擎 A (被评估 AI)：同基因组多局只构建一次网络 ----
        NeatEngine engA = pooledEngineA.get();
        if (engA == null || engA.rayCount() != rays) {
            engA = new NeatEngine(candidate, rays);
            pooledEngineA.set(engA);
        } else if (engA.genome() != candidate) {
            engA.setGenome(candidate, rays);
        }
        engA.reset();
        // 训练与实战推理频率保持一致（AI Speed 设置）
        engA.setSkipFrames(app.aiSpeed + 1);
        final NeatEngine engineA = engA;

        // ---- 引擎 B (对手) ----
        final GameSystem.EngineFactory factoryB;
        if (ghost != null) {
            ReplayEngine rp = new ReplayEngine(ghost);
            rp.reset();
            factoryB = s -> rp;
        } else if (vsGenome != null) {
            NeatEngine engB = pooledEngineB.get();
            if (engB == null || engB.rayCount() != rays) {
                engB = new NeatEngine(vsGenome, rays);
                pooledEngineB.set(engB);
            } else if (engB.genome() != vsGenome) {
                engB.setGenome(vsGenome, rays);
            }
            engB.reset();
            engB.setSkipFrames(app.aiSpeed + 1);
            final NeatEngine eB = engB;
            factoryB = s -> eB;
        } else {
            factoryB = s -> new ComputerEngine(s, level);
        }

        GameSystem sys = new GameSystem(app, false, false, 1.0f, null,
                s -> engineA, factoryB, true, new Random(rngLocal.nextLong()));

        // ---- 池化 Stats/Tracker ----
        MatchTracker tracker = pooledTracker.get();
        if (tracker == null) { tracker = new MatchTracker(sys.myGroup); pooledTracker.set(tracker); }
        else tracker.reset(sys.myGroup);

        // 无头模拟已跳过开局倒计时：循环上限为纯对战帧数，统计帧数补偿回倒计时
        final int maxBattleFrames = MAX_MATCH_FRAMES - MatchStats.COUNTDOWN_FRAMES;
        while (!(sys.currentState instanceof ResultGameState) && sys.frameCount < maxBattleFrames) {
            sys.update();
            tracker.update();
        }

        MatchStats m = pooledStats.get();
        if (m == null) { m = new MatchStats(); pooledStats.set(m); }
        else m.reset();
        m.frames = sys.frameCount + MatchStats.COUNTDOWN_FRAMES;
        if (sys.currentState instanceof ResultGameState) {
            m.aiWon = ((ResultGameState) sys.currentState).winGroup == sys.myGroup.id;
        }
        m.hitsDealt = sys.otherGroup.damageCount;
        m.hitsTaken = sys.myGroup.damageCount;
        tracker.fill(m);
        return m;
    }

    // ------------------------------------------------------------ 早停 / 多样性

    /**
     * 智能平台期收敛：以冠军 vs 规则AI(1.0) 胜率 EMA 为跨代可比进度指标。
     * （旧机制用原始适应度取历史最大，而 shaping 随代数衰减 → 必然误判停滞，
     *   高速训练下几分钟就假收敛停摆。）
     * 平台期先注入多样性复活一次，仍无进展才判定收敛。
     */
    private void checkConvergence() {
        if (generation % 10 == 0) {
            float div = calculateDiversity();
            Gdx.app.log("NeatTrainer", "gen " + generation + " diversity=" + String.format("%.3f", div)
                    + " threshold=" + (evolver != null ? compatThreshold() : 3.0f)
                    + " winRate=" + (champWinRateEma < 0 ? "?" : String.format("%.2f", champWinRateEma)));
            if (div < 0.03f && generation >= MIN_GENS_FOR_STOP) tryReviveOrStop("population converged");
        }

        if (champion == null || generation % CHAMP_EVAL_INTERVAL != 0) return;
        float wr = measureChampionWinRate();
        champWinRateEma = champWinRateEma < 0 ? wr : champWinRateEma * 0.7f + wr * 0.3f;
        if (champWinRateEma > bestWinRateEma + 0.02f) {
            bestWinRateEma = champWinRateEma;
            plateauCount = 0;
            diversityInjected = false;
        } else if (++plateauCount >= PLATEAU_LIMIT && generation >= MIN_GENS_FOR_STOP) {
            tryReviveOrStop("winrate plateau " + String.format("%.2f", champWinRateEma));
        }
    }

    /** 冠军对规则AI(1.0) 胜率：训练进度的真实可比指标。 */
    private float measureChampionWinRate() {
        Genome c = champion;
        if (c == null) return 0f;
        int wins = 0;
        for (int i = 0; i < CHAMP_EVAL_SIMS; i++) {
            if (stopped || paused || resetting) break;
            if (simulate(c, null, null, 1.0f, rng).aiWon) wins++;
        }
        simMatches.addAndGet(CHAMP_EVAL_SIMS);
        return wins / (float) CHAMP_EVAL_SIMS;
    }

    /** 平台期处理：先注入多样性复活一次；复活后仍停滞才收敛停训。 */
    private void tryReviveOrStop(String reason) {
        if (!diversityInjected) {
            diversityInjected = true;
            plateauCount = 0;
            synchronized (evolverLock) {
                NeatEvolver ev = evolver;
                if (ev != null) ev.injectDiversity();
            }
            Gdx.app.log("NeatTrainer", "plateau (" + reason + "), injecting diversity");
        } else {
            converged = true;
            Gdx.app.log("NeatTrainer", "converged gen " + generation + " (" + reason + ")"
                    + " winRate " + String.format("%.2f", champWinRateEma));
        }
    }

    private float compatThreshold() {
        NeatEvolver ev = evolver;
        return ev != null ? ev.compatThreshold : 3.0f;
    }

    private float calculateDiversity() {
        NeatEvolver ev = evolver;
        if (ev == null || ev.population.isEmpty()) return 0f;
        ArrayList<Genome> pop = ev.population;
        int sample = Math.min(20, pop.size());
        float total = 0f;
        int count = 0;
        for (int i = 0; i < sample; i++)
            for (int j = i + 1; j < sample; j++) { total += pop.get(i).distance(pop.get(j)); count++; }
        return count > 0 ? total / count : 0f;
    }

    private void verifySave() {
        try {
            NeatStorage.SaveData loaded = NeatStorage.load(rayCount, expectedInputCount());
            if (loaded == null || loaded.generation != generation)
                Gdx.app.error("NeatTrainer", "save verify FAIL gen " + generation);
        } catch (Throwable t) {
            Gdx.app.error("NeatTrainer", "save verify error", t);
        }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) {} }
}
