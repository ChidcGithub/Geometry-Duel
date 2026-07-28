package com.geometryduel.neat;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 由 Genome 构建的神经网络，支持循环连接 (A)。
 * 循环连接使用目标节点上一帧的激活值作为输入，提供短期记忆能力。
 * reset() 在每局开始时清理状态。
 */
public class NeatNetwork {
    public final int inputCount, outputCount;

    private final int orderLen;
    private final boolean[] isInput;
    private final int[][] srcPositions, recPositions;
    private final float[][] weights, recWeights;
    private final int[] srcLen, recLen;
    private final int[] activations;
    private final int[] outputPositions;
    private final float[] values, prevValues;

    public NeatNetwork(Genome g, int inputCount, int outputCount) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;

        // 最大节点 ID
        int maxNodeId = 0;
        for (int i = 0; i < g.nodes.size(); i++) {
            int id = g.nodes.get(i).id;
            if (id > maxNodeId) maxNodeId = id;
        }

        // 邻接表 + 可达性（只考虑使能连接）
        HashMap<Integer, ArrayList<Integer>> incoming = new HashMap<Integer, ArrayList<Integer>>();
        HashMap<Integer, ArrayList<Float>> incomingW = new HashMap<Integer, ArrayList<Float>>();
        HashMap<Integer, Integer> indegree = new HashMap<Integer, Integer>();
        HashMap<Integer, ArrayList<Integer>> outgoing = new HashMap<Integer, ArrayList<Integer>>();
        HashMap<Integer, ArrayList<Integer>> incomingSrc = new HashMap<Integer, ArrayList<Integer>>();

        for (int i = 0; i < g.conns.size(); i++) {
            Genome.ConnectionGene c = g.conns.get(i);
            if (!c.enabled) continue;

            ArrayList<Integer> l = incoming.get(c.out);
            ArrayList<Float> wl = incomingW.get(c.out);
            if (l == null) { l = new ArrayList<Integer>(); wl = new ArrayList<Float>();
                incoming.put(c.out, l); incomingW.put(c.out, wl); }
            l.add(c.in); wl.add(c.weight);

            Integer d = indegree.get(c.out);
            indegree.put(c.out, d == null ? 1 : d + 1);

            ArrayList<Integer> ol = outgoing.get(c.in);
            if (ol == null) { ol = new ArrayList<Integer>(); outgoing.put(c.in, ol); }
            ol.add(c.out);
        }

        // ---- 循环检测：构建可达性矩阵 ----
        // reachable[from][to] = 是否存在路径（BFS）
        boolean[][] reachable = new boolean[maxNodeId + 1][maxNodeId + 1];
        for (int src = 0; src <= maxNodeId; src++) {
            ArrayList<Integer> stack = new ArrayList<Integer>();
            boolean[] visited = new boolean[maxNodeId + 1];
            stack.add(src);
            while (!stack.isEmpty()) {
                int cur = stack.remove(stack.size() - 1);
                if (cur != src) reachable[src][cur] = true;
                visited[cur] = true;
                ArrayList<Integer> ol = outgoing.get(cur);
                if (ol == null) continue;
                for (int k = 0; k < ol.size(); k++) {
                    int next = ol.get(k);
                    if (!visited[next]) stack.add(next);
                }
            }
        }

        // ---- 标记循环连接：conn is recurrent if out can reach in ----
        // forwardIncoming/recIncoming: 拆分为前向和循环输入
        HashMap<Integer, ArrayList<Integer>> fwdInc = new HashMap<Integer, ArrayList<Integer>>();
        HashMap<Integer, ArrayList<Float>> fwdIncW = new HashMap<Integer, ArrayList<Float>>();
        HashMap<Integer, ArrayList<Integer>> recInc = new HashMap<Integer, ArrayList<Integer>>();
        HashMap<Integer, ArrayList<Float>> recIncW = new HashMap<Integer, ArrayList<Float>>();
        HashMap<Integer, Integer> fwdIndegree = new HashMap<Integer, Integer>();

        for (int i = 0; i < g.conns.size(); i++) {
            Genome.ConnectionGene c = g.conns.get(i);
            if (!c.enabled) continue;
            boolean isRec = reachable[c.out][c.in]; // out can reach back to in = cycle

            HashMap<Integer, ArrayList<Integer>> incMap = isRec ? recInc : fwdInc;
            HashMap<Integer, ArrayList<Float>> incWMap = isRec ? recIncW : fwdIncW;
            ArrayList<Integer> l = incMap.get(c.out);
            ArrayList<Float> wl = incWMap.get(c.out);
            if (l == null) { l = new ArrayList<Integer>(); wl = new ArrayList<Float>();
                incMap.put(c.out, l); incWMap.put(c.out, wl); }
            l.add(c.in); wl.add(c.weight);

            if (!isRec) {
                Integer d = fwdIndegree.get(c.out);
                fwdIndegree.put(c.out, d == null ? 1 : d + 1);
            }
        }

        // ---- Kahn 拓扑排序（仅前向子图） ----
        ArrayList<Integer> ord = new ArrayList<Integer>();
        HashMap<Integer, Integer> deg = new HashMap<Integer, Integer>(fwdIndegree);
        ArrayList<Integer> queue = new ArrayList<Integer>();
        for (int i = 0; i < g.nodes.size(); i++) {
            int id = g.nodes.get(i).id;
            if (!deg.containsKey(id)) queue.add(id);
        }
        HashMap<Integer, ArrayList<Integer>> fwdOutgoing = new HashMap<Integer, ArrayList<Integer>>();
        for (int i = 0; i < g.conns.size(); i++) {
            Genome.ConnectionGene c = g.conns.get(i);
            if (!c.enabled || reachable[c.out][c.in]) continue;
            ArrayList<Integer> ol = fwdOutgoing.get(c.in);
            if (ol == null) { ol = new ArrayList<Integer>(); fwdOutgoing.put(c.in, ol); }
            ol.add(c.out);
        }
        while (!queue.isEmpty()) {
            int id = queue.remove(queue.size() - 1);
            ord.add(id);
            ArrayList<Integer> ol = fwdOutgoing.get(id);
            if (ol == null) continue;
            for (int i = 0; i < ol.size(); i++) {
                int o = ol.get(i);
                int d = deg.get(o) - 1;
                deg.put(o, d);
                if (d == 0) queue.add(o);
            }
        }
        // 追加未排序节点（循环参与者）
        for (int i = 0; i < g.nodes.size(); i++) {
            int id = g.nodes.get(i).id;
            if (!ord.contains(id)) ord.add(id);
        }

        // ---- 构建求值数组 ----
        int[] nodeIdToPos = new int[maxNodeId + 1];
        for (int i = 0; i < nodeIdToPos.length; i++) nodeIdToPos[i] = -1;
        for (int i = 0; i < ord.size(); i++) nodeIdToPos[ord.get(i)] = i;

        orderLen = ord.size();
        isInput = new boolean[orderLen];
        srcLen = new int[orderLen]; recLen = new int[orderLen];
        srcPositions = new int[orderLen][]; recPositions = new int[orderLen][];
        weights = new float[orderLen][]; recWeights = new float[orderLen][];
        activations = new int[orderLen];

        HashMap<Integer, Integer> actMap = new HashMap<Integer, Integer>();
        for (int i = 0; i < g.nodes.size(); i++) {
            Genome.NodeGene n = g.nodes.get(i);
            actMap.put(n.id, n.activation);
        }

        for (int i = 0; i < orderLen; i++) {
            int id = ord.get(i);
            isInput[i] = id < inputCount;
            Integer a = actMap.get(id);
            activations[i] = a != null ? a : Genome.TANH;

            // 前向连接
            ArrayList<Integer> fL = fwdInc.get(id);
            if (fL == null) { srcPositions[i] = emptyInts; weights[i] = emptyFloats; srcLen[i] = 0; }
            else {
                int n = fL.size(); srcLen[i] = n;
                srcPositions[i] = new int[n]; weights[i] = new float[n];
                ArrayList<Float> fW = fwdIncW.get(id);
                for (int j = 0; j < n; j++) { srcPositions[i][j] = nodeIdToPos[fL.get(j)]; weights[i][j] = fW.get(j); }
            }

            // 循环连接
            ArrayList<Integer> rL = recInc.get(id);
            if (rL == null) { recPositions[i] = emptyInts; recWeights[i] = emptyFloats; recLen[i] = 0; }
            else {
                int n = rL.size(); recLen[i] = n;
                recPositions[i] = new int[n]; recWeights[i] = new float[n];
                ArrayList<Float> rW = recIncW.get(id);
                for (int j = 0; j < n; j++) { recPositions[i][j] = nodeIdToPos[rL.get(j)]; recWeights[i][j] = rW.get(j); }
            }
        }

        // 输出节点位置
        outputPositions = new int[outputCount];
        int found = 0;
        for (int i = 0; i < orderLen && found < outputCount; i++) {
            int id = ord.get(i);
            for (int k = 0; k < g.nodes.size(); k++) {
                Genome.NodeGene n = g.nodes.get(k);
                if (n.id == id && n.type == Genome.OUTPUT) { outputPositions[found++] = i; break; }
            }
        }

        values = new float[orderLen];
        prevValues = new float[orderLen];
    }

    /** 清理循环状态（每局开始调用） */
    public void reset() {
        for (int i = 0; i < prevValues.length; i++) prevValues[i] = 0f;
    }

    /**
     * 求值：前向连接用当前值，循环连接用上一帧的值。
     */
    public float[] eval(float[] inputs, float[] out) {
        System.arraycopy(inputs, 0, values, 0, inputCount);

        for (int i = 0; i < orderLen; i++) {
            if (isInput[i]) continue;
            float sum = 0f;

            int[] pos = srcPositions[i];
            float[] w = weights[i];
            int len = srcLen[i];
            for (int j = 0; j < len; j++) sum += values[pos[j]] * w[j];

            int[] rpos = recPositions[i];
            float[] rw = recWeights[i];
            int rlen = recLen[i];
            for (int j = 0; j < rlen; j++) sum += prevValues[rpos[j]] * rw[j];

            values[i] = activate(sum, activations[i]);
        }

        // 复制当前值到 prevValues 供下一帧循环连接使用
        System.arraycopy(values, 0, prevValues, 0, orderLen);

        for (int i = 0; i < outputCount; i++)
            out[i] = values[outputPositions[i]];
        return out;
    }

    private static float activate(float x, int type) {
        switch (type) {
            case Genome.RELU: return Math.max(0f, x);
            case Genome.SIGMOID: return 1f / (1f + (float) Math.exp(-x));
            case Genome.LEAKY_RELU: return x > 0f ? x : x * 0.01f;
            default: return (float) Math.tanh(x);
        }
    }

    private static final int[] emptyInts = new int[0];
    private static final float[] emptyFloats = new float[0];
}
