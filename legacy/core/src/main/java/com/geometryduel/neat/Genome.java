package com.geometryduel.neat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class Genome {
    public static final int INPUT = 0, HIDDEN = 1, OUTPUT = 2;
    public static final int TANH = 0, RELU = 1, SIGMOID = 2, LEAKY_RELU = 3;

    public ArrayList<NodeGene> nodes = new ArrayList<NodeGene>();
    public ArrayList<ConnectionGene> conns = new ArrayList<ConnectionGene>();

    // ---- 可进化的突变率参数 (B) ----
    public float mutationPower = 0.3f;
    public float weightMutProb = 0.9f;
    public float addNodeProb = 0.25f;
    public float addConnProb = 0.14f;
    public float toggleProb = 0.06f;
    public float resetProb = 0.05f;
    public float activationProb = 0.05f;
    public float removeProb = 0.04f;

    public static class NodeGene {
        public int id;
        public int type;
        public int activation = TANH;

        public NodeGene() {}

        public NodeGene(int id, int type) {
            this.id = id;
            this.type = type;
            this.activation = type == HIDDEN ? RELU : TANH;
        }
    }

    public static class ConnectionGene {
        public int in, out;
        public float weight;
        public boolean enabled = true;
        public int innovation;

        public ConnectionGene() {}

        public ConnectionGene(int in, int out, float weight, int innovation) {
            this.in = in; this.out = out; this.weight = weight; this.innovation = innovation;
        }
    }

    // ---- 节点/连接查找 ----

    public NodeGene node(int id) {
        for (int i = 0; i < nodes.size(); i++) {
            NodeGene n = nodes.get(i);
            if (n.id == id) return n;
        }
        return null;
    }

    public boolean hasConnection(int in, int out) {
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            if (c.in == in && c.out == out) return true;
        }
        return false;
    }

    public Genome copy() {
        Genome g = new Genome();
        g.mutationPower = mutationPower;
        g.weightMutProb = weightMutProb;
        g.addNodeProb = addNodeProb;
        g.addConnProb = addConnProb;
        g.toggleProb = toggleProb;
        g.resetProb = resetProb;
        g.activationProb = activationProb;
        g.removeProb = removeProb;
        for (int i = 0; i < nodes.size(); i++) {
            NodeGene n = nodes.get(i);
            NodeGene cp = new NodeGene(n.id, n.type);
            cp.activation = n.activation;
            g.nodes.add(cp);
        }
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            ConnectionGene nc = new ConnectionGene(c.in, c.out, c.weight, c.innovation);
            nc.enabled = c.enabled;
            g.conns.add(nc);
        }
        return g;
    }

    // ------------------------------------------------------------ 变异

    /** 权重变异：按基因组自身的 mutationPower 和 weightMutProb。 */
    public void mutateWeights(Random rng) {
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            if (rng.nextFloat() < weightMutProb)
                c.weight += (float) rng.nextGaussian() * mutationPower;
            else
                c.weight = rng.nextFloat() * 4f - 2f;
            if (c.weight > 8f) c.weight = 8f;
            else if (c.weight < -8f) c.weight = -8f;
        }
    }

    /** 加连接：允许循环（为循环神经网络开路）。 */
    public boolean mutateAddConnection(Random rng, InnovationCounter counter) {
        for (int tries = 0; tries < 20; tries++) {
            NodeGene a = nodes.get(rng.nextInt(nodes.size()));
            NodeGene b = nodes.get(rng.nextInt(nodes.size()));
            if (a.id == b.id) continue;
            NodeGene in = a, out = b;
            if (a.type == OUTPUT || b.type == INPUT) { in = b; out = a; }
            if (in.type == OUTPUT || out.type == INPUT || in.id == out.id) continue;
            if (hasConnection(in.id, out.id)) continue;
            int innov = counter.innovation(in.id, out.id);
            conns.add(new ConnectionGene(in.id, out.id, rng.nextFloat() * 2f - 1f, innov));
            return true;
        }
        return false;
    }

    /** 加节点：拆分一条随机使能连接。 */
    public boolean mutateAddNode(Random rng, InnovationCounter counter) {
        ArrayList<ConnectionGene> enabled = new ArrayList<ConnectionGene>();
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            if (c.enabled) enabled.add(c);
        }
        if (enabled.isEmpty()) return false;
        ConnectionGene c = enabled.get(rng.nextInt(enabled.size()));
        c.enabled = false;
        int newId = counter.nextNodeId();
        nodes.add(new NodeGene(newId, HIDDEN));
        conns.add(new ConnectionGene(c.in, newId, 1f, counter.innovation(c.in, newId)));
        conns.add(new ConnectionGene(newId, c.out, c.weight, counter.innovation(newId, c.out)));
        return true;
    }

    /** 开关连接 */
    public boolean mutateToggleConnection(Random rng) {
        if (conns.isEmpty()) return false;
        ConnectionGene c = conns.get(rng.nextInt(conns.size()));
        c.enabled = !c.enabled;
        return true;
    }

    /** 重置所有权重 */
    public boolean mutateResetWeights(Random rng) {
        if (conns.isEmpty()) return false;
        for (int i = 0; i < conns.size(); i++) conns.get(i).weight = rng.nextFloat() * 4f - 2f;
        return true;
    }

    /** 激活函数变异 */
    public boolean mutateActivation(Random rng) {
        if (nodes.isEmpty()) return false;
        NodeGene n = nodes.get(rng.nextInt(nodes.size()));
        if (n.type == INPUT || n.type == OUTPUT) return false;
        int[] acts = {TANH, RELU, SIGMOID, LEAKY_RELU};
        int cur = n.activation;
        int next;
        do { next = acts[rng.nextInt(acts.length)]; } while (next == cur && acts.length > 1);
        n.activation = next;
        return true;
    }

    /** 删除连接 */
    public boolean mutateRemoveConnection(Random rng) {
        if (conns.isEmpty()) return false;
        conns.remove(rng.nextInt(conns.size()));
        return true;
    }

    /** 变异突变率自身 (B)：每个率有 50% 概率被高斯扰动 ±20% */
    public void mutateMutationRates(Random rng) {
        if (rng.nextFloat() < 0.5f) mutationPower *= (float) Math.exp(rng.nextGaussian() * 0.2);
        if (rng.nextFloat() < 0.5f) weightMutProb = clamp(weightMutProb + (float) rng.nextGaussian() * 0.05f, 0.5f, 0.99f);
        if (rng.nextFloat() < 0.5f) addNodeProb   = clamp(addNodeProb   + (float) rng.nextGaussian() * 0.03f, 0.05f, 0.5f);
        if (rng.nextFloat() < 0.5f) addConnProb   = clamp(addConnProb   + (float) rng.nextGaussian() * 0.03f, 0.02f, 0.3f);
        if (rng.nextFloat() < 0.5f) toggleProb    = clamp(toggleProb    + (float) rng.nextGaussian() * 0.02f, 0.01f, 0.15f);
        if (rng.nextFloat() < 0.5f) resetProb     = clamp(resetProb     + (float) rng.nextGaussian() * 0.02f, 0.005f, 0.1f);
        if (rng.nextFloat() < 0.5f) activationProb= clamp(activationProb+ (float) rng.nextGaussian() * 0.02f, 0.005f, 0.1f);
        if (rng.nextFloat() < 0.5f) removeProb    = clamp(removeProb    + (float) rng.nextGaussian() * 0.02f, 0.005f, 0.1f);
        // 重新归一化 addNode + addConn 比例占主要
        mutationPower = Math.max(0.05f, Math.min(1.5f, mutationPower));
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ------------------------------------------------------------ 交叉

    /** 标准 NEAT 交叉 + 突变率交叉 */
    public static Genome crossover(Genome a, Genome b, Random rng) {
        HashMap<Integer, ConnectionGene> bMap = new HashMap<Integer, ConnectionGene>();
        for (int i = 0; i < b.conns.size(); i++) {
            ConnectionGene c = b.conns.get(i);
            bMap.put(c.innovation, c);
        }
        Genome child = new Genome();
        // 突变率交叉：随机选亲本
        child.mutationPower = rng.nextBoolean() ? a.mutationPower : b.mutationPower;
        child.weightMutProb = rng.nextBoolean() ? a.weightMutProb : b.weightMutProb;
        child.addNodeProb   = rng.nextBoolean() ? a.addNodeProb : b.addNodeProb;
        child.addConnProb   = rng.nextBoolean() ? a.addConnProb : b.addConnProb;
        child.toggleProb    = rng.nextBoolean() ? a.toggleProb : b.toggleProb;
        child.resetProb     = rng.nextBoolean() ? a.resetProb : b.resetProb;
        child.activationProb= rng.nextBoolean() ? a.activationProb : b.activationProb;
        child.removeProb    = rng.nextBoolean() ? a.removeProb : b.removeProb;
        // 节点（同时记录 id 集，供 ensureNode O(1) 查重）
        HashSet<Integer> childNodeIds = new HashSet<Integer>();
        for (int i = 0; i < a.nodes.size(); i++) {
            NodeGene n = a.nodes.get(i);
            NodeGene nc = new NodeGene(n.id, n.type);
            nc.activation = n.activation;
            child.nodes.add(nc);
            childNodeIds.add(n.id);
        }
        // b 节点表（ensureNode O(1) 查找，避免每次线性扫描）
        HashMap<Integer, NodeGene> bNodes = new HashMap<Integer, NodeGene>();
        for (int i = 0; i < b.nodes.size(); i++) {
            NodeGene n = b.nodes.get(i);
            bNodes.put(n.id, n);
        }
        // 连接
        for (int i = 0; i < a.conns.size(); i++) {
            ConnectionGene ca = a.conns.get(i);
            ConnectionGene cb = bMap.get(ca.innovation);
            ConnectionGene chosen = ca;
            boolean disabledSomewhere = !ca.enabled;
            if (cb != null) {
                if (rng.nextBoolean()) chosen = cb;
                if (!cb.enabled) disabledSomewhere = true;
            }
            ConnectionGene nc = new ConnectionGene(chosen.in, chosen.out, chosen.weight, chosen.innovation);
            nc.enabled = disabledSomewhere ? rng.nextFloat() >= 0.75f && chosen.enabled : chosen.enabled;
            child.conns.add(nc);
            ensureNode(child, bNodes, childNodeIds, nc.in);
            ensureNode(child, bNodes, childNodeIds, nc.out);
        }
        return child;
    }

    private static void ensureNode(Genome child, HashMap<Integer, NodeGene> srcNodes,
                                   HashSet<Integer> childNodeIds, int id) {
        if (childNodeIds.contains(id)) return;
        NodeGene n = srcNodes.get(id);
        if (n != null) {
            NodeGene nc = new NodeGene(n.id, n.type);
            nc.activation = n.activation;
            child.nodes.add(nc);
            childNodeIds.add(id);
        }
    }

    // ------------------------------------------------------------ 兼容距离

    public float distance(Genome o) {
        HashMap<Integer, ConnectionGene> oMap = new HashMap<Integer, ConnectionGene>();
        int oMax = 0;
        for (int i = 0; i < o.conns.size(); i++) {
            ConnectionGene c = o.conns.get(i);
            oMap.put(c.innovation, c);
            if (c.innovation > oMax) oMax = c.innovation;
        }
        int myMax = 0;
        HashSet<Integer> myInnovations = new HashSet<Integer>();
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            myInnovations.add(c.innovation);
            if (c.innovation > myMax) myMax = c.innovation;
        }
        int excess = 0, disjoint = 0, matching = 0;
        float weightDiff = 0f;
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            ConnectionGene oc = oMap.get(c.innovation);
            if (oc == null) {
                if (c.innovation > oMax) excess++;
                else disjoint++;
            } else {
                matching++;
                weightDiff += Math.abs(c.weight - oc.weight);
            }
        }
        for (int i = 0; i < o.conns.size(); i++) {
            ConnectionGene c = o.conns.get(i);
            if (myInnovations.contains(c.innovation)) continue;
            if (c.innovation > myMax) excess++;
            else disjoint++;
        }
        int n = Math.max(conns.size(), o.conns.size());
        if (n < 20) n = 1;
        float wd = matching == 0 ? 0f : weightDiff / matching;
        return excess / (float) n + disjoint / (float) n + 0.4f * wd;
    }

    // ------------------------------------------------------------ 行为签名 (C)

    /** 提取当前基因组的粗略行为特征（结构层面） */
    public float[] structureSignature() {
        int totalNodes = nodes.size();
        int hiddenNodes = 0, tanhCount = 0, reluCount = 0, sigmoidCount = 0, leakyCount = 0;
        for (int i = 0; i < nodes.size(); i++) {
            NodeGene n = nodes.get(i);
            if (n.type == HIDDEN) {
                hiddenNodes++;
                if (n.activation == TANH) tanhCount++;
                else if (n.activation == RELU) reluCount++;
                else if (n.activation == SIGMOID) sigmoidCount++;
                else if (n.activation == LEAKY_RELU) leakyCount++;
            }
        }
        int totalConns = conns.size();
        int enabledConns = 0;
        float absWeightSum = 0f;
        int positiveWeights = 0;
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            if (c.enabled) {
                enabledConns++;
                absWeightSum += Math.abs(c.weight);
                if (c.weight > 0f) positiveWeights++;
            }
        }
        float avgWeight = enabledConns > 0 ? absWeightSum / enabledConns : 0f;
        float posRatio = enabledConns > 0 ? positiveWeights / (float) enabledConns : 0.5f;
        return new float[] {
            hiddenNodes / 50f,
            totalConns / 500f,
            enabledConns / (float) Math.max(1, totalConns),
            tanhCount / (float) Math.max(1, hiddenNodes),
            reluCount / (float) Math.max(1, hiddenNodes),
            sigmoidCount / (float) Math.max(1, hiddenNodes),
            avgWeight / 4f,
            posRatio
        };
    }

    /** 创新号/节点号计数器 */
    public static class InnovationCounter {
        public int nextInnovation;
        public int nextNode;
        private final HashMap<String, Integer> genMap = new HashMap<String, Integer>();

        public InnovationCounter() {}

        public InnovationCounter(int nextInnovation, int nextNode) {
            this.nextInnovation = nextInnovation;
            this.nextNode = nextNode;
        }

        public int innovation(int in, int out) {
            String key = in + ":" + out;
            Integer v = genMap.get(key);
            if (v != null) return v;
            int r = nextInnovation++;
            genMap.put(key, r);
            return r;
        }

        public int nextNodeId() { return nextNode++; }

        public void endGeneration() { genMap.clear(); }
    }
}
