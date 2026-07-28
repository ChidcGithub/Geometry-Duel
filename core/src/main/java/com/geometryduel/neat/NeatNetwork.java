package com.geometryduel.neat;

import java.util.ArrayList;
import java.util.HashMap;

public class NeatNetwork {
    public final int inputCount, outputCount;

    private final int[] order;
    private final int orderLen;
    private final boolean[] isInput;
    private final int[][] srcPositions;
    private final float[][] weights;
    private final int[] srcLen;
    private final int[] activations;
    private final int[] outputPositions;
    private final float[] values;

    public NeatNetwork(Genome g, int inputCount, int outputCount) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;

        int maxNodeId = 0;
        for (int i = 0; i < g.nodes.size(); i++) {
            int id = g.nodes.get(i).id;
            if (id > maxNodeId) maxNodeId = id;
        }

        // 邻接表（仅使能连接）
        HashMap<Integer, ArrayList<Integer>> incoming = new HashMap<Integer, ArrayList<Integer>>();
        HashMap<Integer, ArrayList<Float>> incomingW = new HashMap<Integer, ArrayList<Float>>();
        HashMap<Integer, Integer> indegree = new HashMap<Integer, Integer>();
        HashMap<Integer, ArrayList<Integer>> outgoing = new HashMap<Integer, ArrayList<Integer>>();
        for (int i = 0; i < g.conns.size(); i++) {
            Genome.ConnectionGene c = g.conns.get(i);
            if (!c.enabled) continue;
            ArrayList<Integer> l = incoming.get(c.out);
            ArrayList<Float> wl = incomingW.get(c.out);
            if (l == null) {
                l = new ArrayList<Integer>();
                wl = new ArrayList<Float>();
                incoming.put(c.out, l);
                incomingW.put(c.out, wl);
            }
            l.add(c.in);
            wl.add(c.weight);
            Integer d = indegree.get(c.out);
            indegree.put(c.out, d == null ? 1 : d + 1);
            ArrayList<Integer> ol = outgoing.get(c.in);
            if (ol == null) {
                ol = new ArrayList<Integer>();
                outgoing.put(c.in, ol);
            }
            ol.add(c.out);
        }

        // Kahn拓扑排序
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

        // node ID → position in order (for source lookups)
        int[] nodeIdToPos = new int[maxNodeId + 1];
        for (int i = 0; i < nodeIdToPos.length; i++) nodeIdToPos[i] = -1;
        for (int i = 0; i < ord.size(); i++) nodeIdToPos[ord.get(i)] = i;

        orderLen = ord.size();
        order = new int[orderLen];
        isInput = new boolean[orderLen];
        srcLen = new int[orderLen];
        srcPositions = new int[orderLen][];
        weights = new float[orderLen][];
        activations = new int[orderLen];

        HashMap<Integer, Integer> actMap = new HashMap<Integer, Integer>();
        for (int i = 0; i < g.nodes.size(); i++) {
            Genome.NodeGene n = g.nodes.get(i);
            actMap.put(n.id, n.activation);
        }

        for (int i = 0; i < orderLen; i++) {
            int id = ord.get(i);
            order[i] = id;
            isInput[i] = id < inputCount;
            Integer a = actMap.get(id);
            activations[i] = a != null ? a : Genome.TANH;

            ArrayList<Integer> l = incoming.get(id);
            if (l == null) {
                srcPositions[i] = emptyInts;
                weights[i] = emptyFloats;
                srcLen[i] = 0;
            } else {
                int n = l.size();
                srcLen[i] = n;
                srcPositions[i] = new int[n];
                weights[i] = new float[n];
                ArrayList<Float> wl = incomingW.get(id);
                for (int j = 0; j < n; j++) {
                    srcPositions[i][j] = nodeIdToPos[l.get(j)];
                    weights[i][j] = wl.get(j);
                }
            }
        }

        outputPositions = new int[outputCount];
        int found = 0;
        for (int i = 0; i < orderLen && found < outputCount; i++) {
            int id = order[i];
            for (int k = 0; k < g.nodes.size(); k++) {
                Genome.NodeGene n = g.nodes.get(k);
                if (n.id == id && n.type == Genome.OUTPUT) {
                    outputPositions[found++] = i;
                    break;
                }
            }
        }

        values = new float[orderLen];
    }

    /*
     * Performant evaluation: direct array access. No boxing, no HashMap, virtually no GC.
     * values[] is reused across calls — copy inputs in, compute, read outputs out.
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
            values[i] = activate(sum, activations[i]);
        }

        for (int i = 0; i < outputCount; i++) {
            out[i] = values[outputPositions[i]];
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

    private static final int[] emptyInts = new int[0];
    private static final float[] emptyFloats = new float[0];
}
