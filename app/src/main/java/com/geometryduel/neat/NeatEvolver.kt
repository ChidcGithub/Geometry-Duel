package com.geometryduel.neat

import java.util.Random

class NeatEvolver {

    companion object {
        const val COMPAT_THRESHOLD = 3.0f
        private const val MAX_SPECIES = 15
        private const val ELITES_PER_SPECIES = 2
    }

    val inputCount: Int
    val outputCount: Int
    val populationSize: Int
    var population = ArrayList<Genome>()
    var counter: Genome.InnovationCounter
    private val rng: Random

    var compatThreshold = COMPAT_THRESHOLD
    private var stagnantGenerations = 0
    private var lastBestFitness = 0f

    /** 物种信息：代表基因组 + 策略标签 + 成员索引列表 */
    class SpeciesInfo {
        val members = ArrayList<Int>()
        var representative: Genome? = null
        var bestIndex = 0
        var best: Genome? = null // 物种冠军基因组引用（不依赖 population 索引，跨线程读取安全）
        var bestFitness = 0f
        var strategyLabel: String = "" // 给玩家的可读标签
    }

    /** 快照发布：nextGeneration 构建局部列表后原子替换，渲染线程可安全遍历。 */
    @Volatile
    var currentSpecies = ArrayList<SpeciesInfo>()

    constructor(inputCount: Int, outputCount: Int, populationSize: Int, rng: Random) {
        this.inputCount = inputCount
        this.outputCount = outputCount
        this.populationSize = populationSize
        this.rng = rng
        this.counter = Genome.InnovationCounter(inputCount * outputCount, inputCount + outputCount)
        repeat(populationSize) { population.add(createMinimal()) }
    }

    constructor(inputCount: Int, outputCount: Int, populationSize: Int,
                population: ArrayList<Genome>, counter: Genome.InnovationCounter, rng: Random) {
        this.inputCount = inputCount
        this.outputCount = outputCount
        this.populationSize = populationSize
        this.population = population
        this.counter = counter
        this.rng = rng
    }

    private fun createMinimal(): Genome {
        val g = Genome()
        for (i in 0 until inputCount) g.nodes.add(Genome.NodeGene(i, Genome.INPUT))
        for (i in 0 until outputCount) g.nodes.add(Genome.NodeGene(inputCount + i, Genome.OUTPUT))
        for (i in 0 until inputCount)
            for (o in 0 until outputCount)
                g.conns.add(Genome.ConnectionGene(i, inputCount + o,
                    rng.nextFloat() * 2f - 1f, i * outputCount + o))
        // bootstrap 12~18 hidden nodes
        val bootstrap = 12 + rng.nextInt(7)
        repeat(bootstrap) { g.mutateAddNode(rng, counter) }
        return g
    }

    // ------------------------------------------------------------ 动态阈值

    private fun adjustThreshold(bestFitness: Float) {
        if (bestFitness <= lastBestFitness + 0.5f) {
            stagnantGenerations++
            if (stagnantGenerations > 15)
                compatThreshold = maxOf(1.5f, compatThreshold * 0.95f)
        } else {
            stagnantGenerations = 0
            if (compatThreshold < COMPAT_THRESHOLD)
                compatThreshold = minOf(COMPAT_THRESHOLD, compatThreshold + 0.05f)
        }
        lastBestFitness = bestFitness
    }

    // ------------------------------------------------------------ 进化

    fun nextGeneration(fitness: FloatArray) {
        var currentBest = 0f
        for (f in fitness) if (f > currentBest) currentBest = f
        adjustThreshold(currentBest)

        // ---- 物种划分 (D)：局部列表构建，完成后原子发布快照 ----
        val species = ArrayList<SpeciesInfo>()
        val speciesOf = IntArray(population.size)
        for (i in population.indices) {
            val g = population[i]
            var placed = -1
            for (s in species.indices) {
                if (g.distance(species[s].representative!!) < compatThreshold) { placed = s; break }
            }
            if (placed < 0 && species.size < MAX_SPECIES) {
                placed = species.size
                val si = SpeciesInfo()
                si.representative = g
                species.add(si)
            }
            if (placed < 0) {
                // 分配到最近的物种
                var minDist = Float.MAX_VALUE
                for (s in species.indices) {
                    val d = g.distance(species[s].representative!!)
                    if (d < minDist) { minDist = d; placed = s }
                }
            }
            if (placed >= 0) {
                species[placed].members.add(i)
                speciesOf[i] = placed
            }
        }

        // 计算每个物种的最优个体；代表基因组随机重选（经典 NEAT，避免首个划入者偏差）
        for (si in species) {
            si.bestFitness = -Float.MAX_VALUE
            for (idx in si.members) {
                if (fitness[idx] > si.bestFitness) { si.bestFitness = fitness[idx]; si.bestIndex = idx }
            }
            si.best = population[si.bestIndex]
            si.representative = population[si.members[rng.nextInt(si.members.size)]]
            si.strategyLabel = labelSpecies(si, fitness)
        }

        // ---- 显式适应度分享 ----
        val adjusted = FloatArray(population.size)
        var totalAdjusted = 0f
        for (si in species) {
            val sz = si.members.size.toFloat()
            for (idx in si.members) {
                val a = maxOf(0.01f, fitness[idx]) / sz
                adjusted[idx] = a
                totalAdjusted += a
            }
        }

        // ---- 构建下一代 + 精英保留 (D) ----
        val next = ArrayList<Genome>()

        // 精英：每个物种保留 ELITES_PER_SPECIES 个最佳个体（物种≥3人才保留）
        for (si in species) {
            if (si.members.size < 3) continue
            // 按适应度排序取前 ELITES_PER_SPECIES
            val sorted = si.members.toIntArray()
            for (k in sorted.indices)
                for (m in k + 1 until sorted.size)
                    if (fitness[sorted[m]] > fitness[sorted[k]]) {
                        val t = sorted[k]; sorted[k] = sorted[m]; sorted[m] = t
                    }
            for (k in 0 until minOf(ELITES_PER_SPECIES, sorted.size))
                next.add(population[sorted[k]].copy())
        }

        var remaining = populationSize - next.size
        if (totalAdjusted <= 0f) totalAdjusted = 1f

        // 按物种配额生成后代
        for (si in species) {
            if (remaining <= 0) break
            if (si.members.size < 3) continue
            var speciesSum = 0f
            for (idx in si.members) speciesSum += adjusted[idx]
            val quota = Math.round(speciesSum / totalAdjusted * remaining)
            var q = 0
            while (q < quota && next.size < populationSize) {
                val p1 = tournament(si.members, adjusted)
                val p2 = tournament(si.members, adjusted)
                val a = population[p1]
                val b = population[p2]
                val child = if (adjusted[p1] >= adjusted[p2])
                    Genome.crossover(a, b, rng) else Genome.crossover(b, a, rng)
                mutateWithOwnRates(child)
                next.add(child)
                q++
            }
        }

        // 补齐
        val all = ArrayList<Int>(population.size)
        for (i in population.indices) all.add(i)
        while (next.size < populationSize) {
            val p1 = tournament(all, adjusted)
            val p2 = tournament(all, adjusted)
            val a = population[p1]
            val b = population[p2]
            val child = if (adjusted[p1] >= adjusted[p2])
                Genome.crossover(a, b, rng) else Genome.crossover(b, a, rng)
            mutateWithOwnRates(child)
            next.add(child)
        }

        // 先发布物种快照（best 引用旧种群基因组，不依赖索引），再替换种群
        currentSpecies = species
        population = next
        counter.endGeneration()
    }

    // ---- 锦标赛选择 ----
    private fun tournament(members: ArrayList<Int>, adjusted: FloatArray): Int {
        var best = members[rng.nextInt(members.size)]
        for (i in 0 until 4) {
            val c = members[rng.nextInt(members.size)]
            if (adjusted[c] > adjusted[best]) best = c
        }
        return best
    }

    /** 使用基因组自身的突变率 (B)。轮盘按各率之和归一化，保证所有变异类型按设定比例发生。 */
    private fun mutateWithOwnRates(g: Genome) {
        g.mutateMutationRates(rng) // 先变异突变率本身
        if (stagnantGenerations > 8) {
            // 反停滞：适应度长期无提升时放大结构变异，帮助跳出局部最优
            g.mutationPower = minOf(1.5f, g.mutationPower * 1.3f)
            g.addNodeProb = minOf(0.5f, g.addNodeProb * 1.5f)
            g.addConnProb = minOf(0.3f, g.addConnProb * 1.5f)
        }
        val total = g.weightMutProb + g.addNodeProb + g.addConnProb + g.toggleProb +
                g.resetProb + g.activationProb + g.removeProb
        val r = rng.nextFloat() * total
        val a = g.weightMutProb
        val b = a + g.addNodeProb
        val c = b + g.addConnProb
        val d = c + g.toggleProb
        val e = d + g.resetProb
        val f = e + g.activationProb
        when {
            r < a -> g.mutateWeights(rng)
            r < b -> g.mutateAddNode(rng, counter)
            r < c -> g.mutateAddConnection(rng, counter)
            r < d -> g.mutateToggleConnection(rng)
            r < e -> g.mutateResetWeights(rng)
            r < f -> g.mutateActivation(rng)
            else -> g.mutateRemoveConnection(rng)
        }
    }

    // ---- 物种标签 (D) ----
    private fun labelSpecies(si: SpeciesInfo, fitness: FloatArray): String {
        var totalNodes = 0
        var totalConns = 0
        var avgMutPower = 0f
        val count = minOf(5, si.members.size)
        for (k in 0 until count) {
            val g = population[si.members[k]]
            totalNodes += g.nodes.size
            totalConns += g.conns.size
            avgMutPower += g.mutationPower
        }
        avgMutPower /= count
        val avgNodes = totalNodes / count.toFloat()
        val avgConns = totalConns / count.toFloat()

        if (avgMutPower > 0.5f) return "Explorer"
        if (avgNodes > 40) return "Tactician"
        if (avgConns > 300) return "Strategist"
        if (avgNodes < 20) return "Minimalist"
        return "Balanced"
    }

    /**
     * 停滞复活（训练线程在世代间隙调用）：随机 40% 个体重度变异、10% 替换为全新基因组，
     * 并重置停滞计数，帮助种群跳出局部最优。
     */
    fun injectDiversity() {
        val heavy = population.size * 2 / 5
        for (i in 0 until heavy) {
            val idx = rng.nextInt(population.size)
            val g = population[idx].copy()
            g.mutateWeights(rng)
            g.mutateWeights(rng)
            g.mutateWeights(rng)
            g.mutateAddNode(rng, counter)
            population[idx] = g
        }
        val fresh = population.size / 10
        for (i in 0 until fresh) population[rng.nextInt(population.size)] = createMinimal()
        stagnantGenerations = 0
    }

    /** 返回各物种冠军（供玩家选择对手） */
    fun speciesChampions(): ArrayList<Genome> {
        val snapshot = currentSpecies
        val champs = ArrayList<Genome>()
        for (si in snapshot) {
            si.best?.let { champs.add(it) }
        }
        return champs
    }
}
