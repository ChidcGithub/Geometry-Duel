package com.geometryduel.neat;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 由 Genome 构建的前馈网络：迭代激活（tanh）。
 * 输入节点 id 0..inputCount-1（最后一个为恒 1 偏置），输出节点紧随其后。
 */
public class NeatNetwork {
    public final int inputCount, outputCount;

    private final int[] order;
    private final float[][] inWeights;
    private final int[][] inSources;
    private final int[] outputIds;
    private final int[] activations;

    public NeatNetwork(Genome g, int inputCount, int outputCount) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;

        // 邻接表（仅使能连接）
        HashMap<Integer, ArrayList<int[]>> incoming = new HashMap<Integer, ArrayList<int[]>>();
        HashMap<Integer, ArrayList<float[]>> incomingW = new HashMap<Integer, ArrayList<float[]>>();
        HashMap<Integer, Integer> indegree = new HashMap<Integer, Integer>();
        HashMap<Integer, ArrayList<Integer>> outgoing = new HashMap<Integer, ArrayList<Integer>>();
        for (int i = 0; i < g.conns.size(); i++) {
            Genome.ConnectionGene c = g.conns.get(i);
            if (!c.enabled) continue;
            ArrayList<int[]> l = incoming.get(c.out);
            ArrayList<float[]> wl = incomingW.get(c.out);
            if (l == null) {
                l = new ArrayList<int[]>();
                wl = new ArrayList<float[]>();
                incoming.put(c.out, l);
                incomingW.put(c.out, wl);
            }
            l.add(new int[] {c.in});
            wl.add(new float[] {c.weight});
            Integer d = indegree.get(c.out);
            indegree.put(c.out, d == null ? 1 : d + 1);
            ArrayList<Integer> ol = outgoing.get(c.in);
            if (ol == null) {
                ol = new ArrayList<Integer>();
                outgoing.put(c.in, ol);
            }
            ol.add(c.out);
        }

        // Kahn 拓扑排序；成环节点追加在末尾（按陈旧值求值）
        ArrayList<Integer> ord = new ArrayList<Integer>();
        HashMap<Integer, Integer> deg = new HashMap<Integer, Integer>(indegree);
        ArrayList<Integer> queue = new ArrayList<Integer>();
        for (int i = 0; i < g.nodes.size(); i++) {
            int id = g.nodes.get(i).id;
            if (!deg.containsKey(id)) queue.add(id);
        }
        while (!queue.isEmpty()) {
            int id = queue.remove(queue.size() - 1);
            ord.add(id);
            ArrayList<Integer> ol = outgoing.get(id);
            if (ol == null) continue;
            for (int i = 0; i < ol.size(); i++) {
                int o = ol.get(i);
                int d = deg.get(o) - 1;
                deg.put(o, d);
                if (d == 0) queue.add(o);
            }
        }
        for (int i = 0; i < g.nodes.size(); i++) {
            int id = g.nodes.get(i).id;
            if (!ord.contains(id)) ord.add(id);
        }

        order = new int[ord.size()];
        inWeights = new float[ord.size()][];
        inSources = new int[ord.size()][];
        for (int i = 0; i < ord.size(); i++) {
            int id = ord.get(i);
            order[i] = id;
            ArrayList<int[]> l = incoming.get(id);
            if (l == null) {
                inSources[i] = new int[0];
                inWeights[i] = new float[0];
            } else {
                int n = l.size();
                inSources[i] = new int[n];
                inWeights[i] = new float[n];
                ArrayList<float[]> wl = incomingW.get(id);
                for (int j = 0; j < n; j++) {
                    inSources[i][j] = l.get(j)[0];
                    inWeights[i][j] = wl.get(j)[0];
                }
            }
        }

        outputIds = new int[outputCount];
        int found = 0;
        for (int i = 0; i < g.nodes.size() && found < outputCount; i++) {
            Genome.NodeGene n = g.nodes.get(i);
            if (n.type == Genome.OUTPUT) outputIds[found++] = n.id;
        }

        HashMap<Integer, Integer> actMap = new HashMap<Integer, Integer>();
        for (int i = 0; i < g.nodes.size(); i++) {
            Genome.NodeGene n = g.nodes.get(i);
            actMap.put(n.id, n.activation);
        }
        activations = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            Integer a = actMap.get(order[i]);
            activations[i] = a != null ? a : Genome.TANH;
        }
    }

    public float[] eval(float[] inputs, float[] out) {
        HashMap<Integer, Float> values = new HashMap<Integer, Float>();
        for (int i = 0; i < inputCount; i++) values.put(i, inputs[i]);
        for (int i = 0; i < order.length; i++) {
            int id = order[i];
            if (id < inputCount) continue;
            float sum = 0f;
            int[] src = inSources[i];
            float[] w = inWeights[i];
            for (int j = 0; j < src.length; j++) {
                Float v = values.get(src[j]);
                if (v != null) sum += v * w[j];
            }
            values.put(id, activate(sum, activations[i]));
        }
        for (int i = 0; i < outputCount; i++) {
            Float v = values.get(outputIds[i]);
            out[i] = v == null ? 0f : v;
        }
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
}
