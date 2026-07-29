package com.geometryduel.neat

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp

class Genome {

    companion object {
        const val INPUT = 0
        const val HIDDEN = 1
        const val OUTPUT = 2
        const val TANH = 0
        const val RELU = 1
        const val SIGMOID = 2
        const val LEAKY_RELU = 3

        private fun clamp(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v

        /** 标准 NEAT 交叉 + 突变率交叉 */
        fun crossover(a: Genome, b: Genome, rng: Random): Genome {
            val bMap = HashMap<Int, ConnectionGene>()
            for (c in b.conns) bMap[c.innovation] = c
            val child = Genome()
            // 突变率交叉：随机选亲本
            child.mutationPower = if (rng.nextBoolean()) a.mutationPower else b.mutationPower
            child.weightMutProb = if (rng.nextBoolean()) a.weightMutProb else b.weightMutProb
            child.addNodeProb = if (rng.nextBoolean()) a.addNodeProb else b.addNodeProb
            child.addConnProb = if (rng.nextBoolean()) a.addConnProb else b.addConnProb
            child.toggleProb = if (rng.nextBoolean()) a.toggleProb else b.toggleProb
            child.resetProb = if (rng.nextBoolean()) a.resetProb else b.resetProb
            child.activationProb = if (rng.nextBoolean()) a.activationProb else b.activationProb
            child.removeProb = if (rng.nextBoolean()) a.removeProb else b.removeProb
            // 节点（同时记录 id 集，供 ensureNode O(1) 查重）
            val childNodeIds = HashSet<Int>()
            for (n in a.nodes) {
                val nc = NodeGene(n.id, n.type)
                nc.activation = n.activation
                child.nodes.add(nc)
                childNodeIds.add(n.id)
            }
            // b 节点表（ensureNode O(1) 查找，避免每次线性扫描）
            val bNodes = HashMap<Int, NodeGene>()
            for (n in b.nodes) bNodes[n.id] = n
            // 连接
            for (ca in a.conns) {
                val cb = bMap[ca.innovation]
                var chosen = ca
                var disabledSomewhere = !ca.enabled
                if (cb != null) {
                    if (rng.nextBoolean()) chosen = cb
                    if (!cb.enabled) disabledSomewhere = true
                }
                val nc = ConnectionGene(chosen.`in`, chosen.out, chosen.weight, chosen.innovation)
                nc.enabled = if (disabledSomewhere) rng.nextFloat() >= 0.75f && chosen.enabled
                else chosen.enabled
                child.conns.add(nc)
                ensureNode(child, bNodes, childNodeIds, nc.`in`)
                ensureNode(child, bNodes, childNodeIds, nc.out)
            }
            return child
        }

        private fun ensureNode(child: Genome, srcNodes: HashMap<Int, NodeGene>,
                               childNodeIds: HashSet<Int>, id: Int) {
            if (childNodeIds.contains(id)) return
            val n = srcNodes[id] ?: return
            val nc = NodeGene(n.id, n.type)
            nc.activation = n.activation
            child.nodes.add(nc)
            childNodeIds.add(id)
        }
    }

    val nodes = ArrayList<NodeGene>()
    val conns = ArrayList<ConnectionGene>()

    // ---- 可进化的突变率参数 (B) ----
    var mutationPower = 0.3f
    var weightMutProb = 0.9f
    var addNodeProb = 0.25f
    var addConnProb = 0.14f
    var toggleProb = 0.06f
    var resetProb = 0.05f
    var activationProb = 0.05f
    var removeProb = 0.04f

    class NodeGene(
        var id: Int = 0,
        var type: Int = 0,
    ) {
        var activation: Int = if (type == HIDDEN) RELU else TANH
    }

    class ConnectionGene(
        var `in`: Int = 0,
        var out: Int = 0,
        var weight: Float = 0f,
        var innovation: Int = 0,
    ) {
        var enabled = true
    }

    // ---- 节点/连接查找 ----

    fun node(id: Int): NodeGene? = nodes.firstOrNull { it.id == id }

    fun hasConnection(`in`: Int, out: Int): Boolean =
        conns.any { it.`in` == `in` && it.out == out }

    fun copy(): Genome {
        val g = Genome()
        g.mutationPower = mutationPower
        g.weightMutProb = weightMutProb
        g.addNodeProb = addNodeProb
        g.addConnProb = addConnProb
        g.toggleProb = toggleProb
        g.resetProb = resetProb
        g.activationProb = activationProb
        g.removeProb = removeProb
        for (n in nodes) {
            val cp = NodeGene(n.id, n.type)
            cp.activation = n.activation
            g.nodes.add(cp)
        }
        for (c in conns) {
            val nc = ConnectionGene(c.`in`, c.out, c.weight, c.innovation)
            nc.enabled = c.enabled
            g.conns.add(nc)
        }
        return g
    }

    // ------------------------------------------------------------ 变异

    /** 权重变异：按基因组自身的 mutationPower 和 weightMutProb。 */
    fun mutateWeights(rng: Random) {
        for (c in conns) {
            if (rng.nextFloat() < weightMutProb)
                c.weight += (rng.nextGaussian().toFloat()) * mutationPower
            else
                c.weight = rng.nextFloat() * 4f - 2f
            if (c.weight > 8f) c.weight = 8f
            else if (c.weight < -8f) c.weight = -8f
        }
    }

    /** 加连接：允许循环（为循环神经网络开路）。 */
    fun mutateAddConnection(rng: Random, counter: InnovationCounter): Boolean {
        for (tries in 0 until 20) {
            val a = nodes[rng.nextInt(nodes.size)]
            val b = nodes[rng.nextInt(nodes.size)]
            if (a.id == b.id) continue
            var `in` = a
            var out = b
            if (a.type == OUTPUT || b.type == INPUT) { `in` = b; out = a }
            if (`in`.type == OUTPUT || out.type == INPUT || `in`.id == out.id) continue
            if (hasConnection(`in`.id, out.id)) continue
            val innov = counter.innovation(`in`.id, out.id)
            conns.add(ConnectionGene(`in`.id, out.id, rng.nextFloat() * 2f - 1f, innov))
            return true
        }
        return false
    }

    /** 加节点：拆分一条随机使能连接。 */
    fun mutateAddNode(rng: Random, counter: InnovationCounter): Boolean {
        val enabled = conns.filter { it.enabled }
        if (enabled.isEmpty()) return false
        val c = enabled[rng.nextInt(enabled.size)]
        c.enabled = false
        val newId = counter.nextNodeId()
        nodes.add(NodeGene(newId, HIDDEN))
        conns.add(ConnectionGene(c.`in`, newId, 1f, counter.innovation(c.`in`, newId)))
        conns.add(ConnectionGene(newId, c.out, c.weight, counter.innovation(newId, c.out)))
        return true
    }

    /** 开关连接 */
    fun mutateToggleConnection(rng: Random): Boolean {
        if (conns.isEmpty()) return false
        val c = conns[rng.nextInt(conns.size)]
        c.enabled = !c.enabled
        return true
    }

    /** 重置所有权重 */
    fun mutateResetWeights(rng: Random): Boolean {
        if (conns.isEmpty()) return false
        for (c in conns) c.weight = rng.nextFloat() * 4f - 2f
        return true
    }

    /** 激活函数变异 */
    fun mutateActivation(rng: Random): Boolean {
        if (nodes.isEmpty()) return false
        val n = nodes[rng.nextInt(nodes.size)]
        if (n.type == INPUT || n.type == OUTPUT) return false
        val acts = intArrayOf(TANH, RELU, SIGMOID, LEAKY_RELU)
        val cur = n.activation
        var next: Int
        do {
            next = acts[rng.nextInt(acts.size)]
        } while (next == cur && acts.size > 1)
        n.activation = next
        return true
    }

    /** 删除连接 */
    fun mutateRemoveConnection(rng: Random): Boolean {
        if (conns.isEmpty()) return false
        conns.removeAt(rng.nextInt(conns.size))
        return true
    }

    /** 变异突变率自身 (B)：每个率有 50% 概率被高斯扰动 ±20% */
    fun mutateMutationRates(rng: Random) {
        if (rng.nextFloat() < 0.5f) mutationPower *= exp(rng.nextGaussian() * 0.2).toFloat()
        if (rng.nextFloat() < 0.5f) weightMutProb = clamp(weightMutProb + rng.nextGaussian().toFloat() * 0.05f, 0.5f, 0.99f)
        if (rng.nextFloat() < 0.5f) addNodeProb = clamp(addNodeProb + rng.nextGaussian().toFloat() * 0.03f, 0.05f, 0.5f)
        if (rng.nextFloat() < 0.5f) addConnProb = clamp(addConnProb + rng.nextGaussian().toFloat() * 0.03f, 0.02f, 0.3f)
        if (rng.nextFloat() < 0.5f) toggleProb = clamp(toggleProb + rng.nextGaussian().toFloat() * 0.02f, 0.01f, 0.15f)
        if (rng.nextFloat() < 0.5f) resetProb = clamp(resetProb + rng.nextGaussian().toFloat() * 0.02f, 0.005f, 0.1f)
        if (rng.nextFloat() < 0.5f) activationProb = clamp(activationProb + rng.nextGaussian().toFloat() * 0.02f, 0.005f, 0.1f)
        if (rng.nextFloat() < 0.5f) removeProb = clamp(removeProb + rng.nextGaussian().toFloat() * 0.02f, 0.005f, 0.1f)
        // 重新归一化 addNode + addConn 比例占主要
        mutationPower = mutationPower.coerceIn(0.05f, 1.5f)
    }

    // ------------------------------------------------------------ 兼容距离

    fun distance(o: Genome): Float {
        val oMap = HashMap<Int, ConnectionGene>()
        var oMax = 0
        for (c in o.conns) {
            oMap[c.innovation] = c
            if (c.innovation > oMax) oMax = c.innovation
        }
        var myMax = 0
        val myInnovations = HashSet<Int>()
        for (c in conns) {
            myInnovations.add(c.innovation)
            if (c.innovation > myMax) myMax = c.innovation
        }
        var excess = 0
        var disjoint = 0
        var matching = 0
        var weightDiff = 0f
        for (c in conns) {
            val oc = oMap[c.innovation]
            if (oc == null) {
                if (c.innovation > oMax) excess++ else disjoint++
            } else {
                matching++
                weightDiff += abs(c.weight - oc.weight)
            }
        }
        for (c in o.conns) {
            if (myInnovations.contains(c.innovation)) continue
            if (c.innovation > myMax) excess++ else disjoint++
        }
        var n = maxOf(conns.size, o.conns.size)
        if (n < 20) n = 1
        val wd = if (matching == 0) 0f else weightDiff / matching
        return excess / n.toFloat() + disjoint / n.toFloat() + 0.4f * wd
    }

    // ------------------------------------------------------------ 行为签名 (C)

    /** 提取当前基因组的粗略行为特征（结构层面） */
    fun structureSignature(): FloatArray {
        var hiddenNodes = 0
        var tanhCount = 0
        var reluCount = 0
        var sigmoidCount = 0
        var leakyCount = 0
        for (n in nodes) {
            if (n.type == HIDDEN) {
                hiddenNodes++
                when (n.activation) {
                    TANH -> tanhCount++
                    RELU -> reluCount++
                    SIGMOID -> sigmoidCount++
                    LEAKY_RELU -> leakyCount++
                }
            }
        }
        val totalConns = conns.size
        var enabledConns = 0
        var absWeightSum = 0f
        var positiveWeights = 0
        for (c in conns) {
            if (c.enabled) {
                enabledConns++
                absWeightSum += abs(c.weight)
                if (c.weight > 0f) positiveWeights++
            }
        }
        val avgWeight = if (enabledConns > 0) absWeightSum / enabledConns else 0f
        val posRatio = if (enabledConns > 0) positiveWeights / enabledConns.toFloat() else 0.5f
        return floatArrayOf(
            hiddenNodes / 50f,
            totalConns / 500f,
            enabledConns / maxOf(1, totalConns).toFloat(),
            tanhCount / maxOf(1, hiddenNodes).toFloat(),
            reluCount / maxOf(1, hiddenNodes).toFloat(),
            sigmoidCount / maxOf(1, hiddenNodes).toFloat(),
            avgWeight / 4f,
            posRatio
        )
    }

    /** 创新号/节点号计数器 */
    class InnovationCounter {
        var nextInnovation: Int
        var nextNode: Int
        private val genMap = HashMap<String, Int>()

        constructor() {
            nextInnovation = 0
            nextNode = 0
        }

        constructor(nextInnovation: Int, nextNode: Int) {
            this.nextInnovation = nextInnovation
            this.nextNode = nextNode
        }

        fun innovation(`in`: Int, out: Int): Int {
            val key = "$`in`:$out"
            val v = genMap[key]
            if (v != null) return v
            val r = nextInnovation++
            genMap[key] = r
            return r
        }

        fun nextNodeId(): Int = nextNode++

        fun endGeneration() = genMap.clear()
    }

}
