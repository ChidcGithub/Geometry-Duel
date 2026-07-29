package com.geometryduel.neat

/**
 * 由 Genome 构建的神经网络，支持循环连接 (A)。
 * 循环连接使用目标节点上一帧的激活值作为输入，提供短期记忆能力。
 * reset() 在每局开始时清理状态。
 */
class NeatNetwork(g: Genome, val inputCount: Int, val outputCount: Int) {

    private val orderLen: Int
    private val isInput: BooleanArray
    private val srcPositions: Array<IntArray>
    private val recPositions: Array<IntArray>
    private val weights: Array<FloatArray>
    private val recWeights: Array<FloatArray>
    private val srcLen: IntArray
    private val recLen: IntArray
    private val activations: IntArray
    /** 输入/输出节点位置按节点 id 固定映射（拓扑序随基因组结构而变，不能按序号假设）。 */
    private val inputPositions: IntArray
    private val outputPositions: IntArray
    /** 循环连接源节点位置（去重）：eval/reset 只需同步这些位置。 */
    private val recSrcPositions: IntArray
    private val values: FloatArray
    private val prevValues: FloatArray

    init {
        // 最大节点 ID
        var maxNodeId = 0
        for (n in g.nodes) if (n.id > maxNodeId) maxNodeId = n.id

        // 邻接表 + 可达性（只考虑使能连接）
        val incoming = HashMap<Int, ArrayList<Int>>()
        val incomingW = HashMap<Int, ArrayList<Float>>()
        val indegree = HashMap<Int, Int>()
        val outgoing = HashMap<Int, ArrayList<Int>>()

        for (c in g.conns) {
            if (!c.enabled) continue
            val l = incoming.getOrPut(c.out) { ArrayList() }
            val wl = incomingW.getOrPut(c.out) { ArrayList() }
            l.add(c.`in`)
            wl.add(c.weight)
            indegree[c.out] = (indegree[c.out] ?: 0) + 1
            outgoing.getOrPut(c.`in`) { ArrayList() }.add(c.out)
        }

        // ---- 循环检测：可达性矩阵按紧凑节点索引分配 ----
        // 矩阵规模为 nodeCount² 而非 maxNodeId²（id 随进化膨胀，避免内存爆炸）
        val nodeCount = g.nodes.size
        val idToIdx = IntArray(maxNodeId + 1) { -1 }
        for (i in 0 until nodeCount) idToIdx[g.nodes[i].id] = i

        // 紧凑邻接表（跳过端点缺失的损坏边）
        val outAdj = Array(nodeCount) { ArrayList<Int>() }
        for (c in g.conns) {
            if (!c.enabled) continue
            val fi = if (c.`in` <= maxNodeId) idToIdx[c.`in`] else -1
            val ti = if (c.out <= maxNodeId) idToIdx[c.out] else -1
            if (fi >= 0 && ti >= 0) outAdj[fi].add(ti)
        }

        // reachable[from][to] = 是否存在路径（DFS）
        val reachable = Array(nodeCount) { BooleanArray(nodeCount) }
        for (s in 0 until nodeCount) {
            val stack = ArrayList<Int>()
            val visited = BooleanArray(nodeCount)
            stack.add(s)
            while (stack.isNotEmpty()) {
                val cur = stack.removeAt(stack.size - 1)
                if (cur != s) reachable[s][cur] = true
                visited[cur] = true
                for (next in outAdj[cur]) {
                    if (!visited[next]) stack.add(next)
                }
            }
        }

        // 每条使能连接是否回边：out 可达 in = cycle
        val recurrent = BooleanArray(g.conns.size)
        for (i in g.conns.indices) {
            val c = g.conns[i]
            if (!c.enabled) continue
            val fi = if (c.`in` <= maxNodeId) idToIdx[c.`in`] else -1
            val ti = if (c.out <= maxNodeId) idToIdx[c.out] else -1
            recurrent[i] = fi >= 0 && ti >= 0 && reachable[ti][fi]
        }

        // ---- 标记循环连接：conn is recurrent if out can reach in ----
        // forwardIncoming/recIncoming: 拆分为前向和循环输入
        val fwdInc = HashMap<Int, ArrayList<Int>>()
        val fwdIncW = HashMap<Int, ArrayList<Float>>()
        val recInc = HashMap<Int, ArrayList<Int>>()
        val recIncW = HashMap<Int, ArrayList<Float>>()
        val fwdIndegree = HashMap<Int, Int>()

        for (i in g.conns.indices) {
            val c = g.conns[i]
            if (!c.enabled) continue
            val isRec = recurrent[i]
            val incMap = if (isRec) recInc else fwdInc
            val incWMap = if (isRec) recIncW else fwdIncW
            val l = incMap.getOrPut(c.out) { ArrayList() }
            val wl = incWMap.getOrPut(c.out) { ArrayList() }
            l.add(c.`in`)
            wl.add(c.weight)
            if (!isRec) fwdIndegree[c.out] = (fwdIndegree[c.out] ?: 0) + 1
        }

        // ---- Kahn 拓扑排序（仅前向子图） ----
        val ord = ArrayList<Int>()
        val deg = HashMap(fwdIndegree)
        val queue = ArrayList<Int>()
        for (n in g.nodes) {
            if (!deg.containsKey(n.id)) queue.add(n.id)
        }
        val fwdOutgoing = HashMap<Int, ArrayList<Int>>()
        for (i in g.conns.indices) {
            val c = g.conns[i]
            if (!c.enabled || recurrent[i]) continue
            fwdOutgoing.getOrPut(c.`in`) { ArrayList() }.add(c.out)
        }
        while (queue.isNotEmpty()) {
            val id = queue.removeAt(queue.size - 1)
            ord.add(id)
            val ol = fwdOutgoing[id] ?: continue
            for (o in ol) {
                val d = (deg[o] ?: 0) - 1
                deg[o] = d
                if (d == 0) queue.add(o)
            }
        }
        // 追加未排序节点（循环参与者）
        for (n in g.nodes) {
            if (!ord.contains(n.id)) ord.add(n.id)
        }

        // ---- 构建求值数组 ----
        val nodeIdToPos = IntArray(maxNodeId + 1) { -1 }
        for (i in ord.indices) nodeIdToPos[ord[i]] = i

        orderLen = ord.size
        isInput = BooleanArray(orderLen)
        srcLen = IntArray(orderLen)
        recLen = IntArray(orderLen)
        srcPositions = arrayOfNulls<IntArray>(orderLen).map { EMPTY_INTS }.toTypedArray()
        recPositions = arrayOfNulls<IntArray>(orderLen).map { EMPTY_INTS }.toTypedArray()
        weights = arrayOfNulls<FloatArray>(orderLen).map { EMPTY_FLOATS }.toTypedArray()
        recWeights = arrayOfNulls<FloatArray>(orderLen).map { EMPTY_FLOATS }.toTypedArray()
        activations = IntArray(orderLen)

        val actMap = HashMap<Int, Int>()
        for (n in g.nodes) {
            actMap[n.id] = n.activation
        }

        for (i in 0 until orderLen) {
            val id = ord[i]
            isInput[i] = id < inputCount
            activations[i] = actMap[id] ?: Genome.TANH

            // 前向连接
            val fL = fwdInc[id]
            if (fL == null) {
                srcLen[i] = 0
            } else {
                val n = fL.size
                srcLen[i] = n
                val pos = IntArray(n)
                val w = FloatArray(n)
                val fW = fwdIncW[id]!!
                for (j in 0 until n) {
                    pos[j] = nodeIdToPos[fL[j]]
                    w[j] = fW[j]
                }
                srcPositions[i] = pos
                weights[i] = w
            }

            // 循环连接
            val rL = recInc[id]
            if (rL == null) {
                recLen[i] = 0
            } else {
                val n = rL.size
                recLen[i] = n
                val pos = IntArray(n)
                val w = FloatArray(n)
                val rW = recIncW[id]!!
                for (j in 0 until n) {
                    pos[j] = nodeIdToPos[rL[j]]
                    w[j] = rW[j]
                }
                recPositions[i] = pos
                recWeights[i] = w
            }
        }

        // 输入/输出节点位置：按节点 id 精确映射（输入 id 0..inputCount-1，
        // 输出 id inputCount..inputCount+outputCount-1，由 Genome 构建约定保证）。
        // 拓扑序会随隐藏节点结构变化，按扫描顺序假设会导致 I/O 语义随结构漂移。
        inputPositions = IntArray(inputCount) { id ->
            if (id <= maxNodeId) nodeIdToPos[id] else -1
        }
        outputPositions = IntArray(outputCount) { k ->
            val id = inputCount + k
            if (id <= maxNodeId) nodeIdToPos[id] else -1
        }

        // 循环连接源节点去重列表：prevValues 只被这些位置读取
        val seen = BooleanArray(orderLen)
        var recSrcCount = 0
        for (k in 0 until orderLen) {
            val rp = recPositions[k]
            for (j in 0 until recLen[k]) {
                if (!seen[rp[j]]) { seen[rp[j]] = true; recSrcCount++ }
            }
        }
        recSrcPositions = IntArray(recSrcCount)
        var k = 0
        for (m in 0 until orderLen) {
            val rp = recPositions[m]
            for (j in 0 until recLen[m]) {
                if (seen[rp[j]]) { seen[rp[j]] = false; recSrcPositions[k++] = rp[j] }
            }
        }

        values = FloatArray(orderLen)
        prevValues = FloatArray(orderLen)
    }

    /** 清理循环状态（每局开始调用） */
    fun reset() {
        for (i in recSrcPositions.indices) prevValues[recSrcPositions[i]] = 0f
    }

    /**
     * 求值：前向连接用当前值，循环连接用上一帧的值。
     */
    fun eval(inputs: FloatArray, out: FloatArray): FloatArray {
        for (k in 0 until inputCount) {
            val p = inputPositions[k]
            if (p >= 0) values[p] = inputs[k]
        }

        for (i in 0 until orderLen) {
            if (isInput[i]) continue
            var sum = 0f

            val pos = srcPositions[i]
            val w = weights[i]
            val len = srcLen[i]
            for (j in 0 until len) sum += values[pos[j]] * w[j]

            val rpos = recPositions[i]
            val rw = recWeights[i]
            val rlen = recLen[i]
            for (j in 0 until rlen) sum += prevValues[rpos[j]] * rw[j]

            values[i] = activate(sum, activations[i])
        }

        // 只复制循环源节点的当前值到 prevValues 供下一帧循环连接使用
        for (i in recSrcPositions.indices)
            prevValues[recSrcPositions[i]] = values[recSrcPositions[i]]

        for (i in 0 until outputCount) {
            val p = outputPositions[i]
            out[i] = if (p >= 0) values[p] else 0f
        }
        return out
    }

    companion object {
        private val EMPTY_INTS = IntArray(0)
        private val EMPTY_FLOATS = FloatArray(0)

        private fun activate(x: Float, type: Int): Float {
            return when (type) {
                Genome.RELU -> maxOf(0f, x)
                Genome.SIGMOID -> 1f / (1f + kotlin.math.exp(-x))
                Genome.LEAKY_RELU -> if (x > 0f) x else x * 0.01f
                else -> kotlin.math.tanh(x)
            }
        }
    }
}
