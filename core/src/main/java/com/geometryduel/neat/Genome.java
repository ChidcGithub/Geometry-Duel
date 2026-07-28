package com.geometryduel.neat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * NEAT 基因组：节点基因 + 连接基因（含创新号）。
 * 支持交叉、权重变异、加节点、加连接与兼容距离计算。
 * 字段 public 以便 libGDX Json 直接序列化。
 */
public class Genome {
    public static final int INPUT = 0, HIDDEN = 1, OUTPUT = 2;
    public static final int TANH = 0, RELU = 1, SIGMOID = 2, LEAKY_RELU = 3;

    public ArrayList<NodeGene> nodes = new ArrayList<NodeGene>();
    public ArrayList<ConnectionGene> conns = new ArrayList<ConnectionGene>();

    public static class NodeGene {
        public int id;
        public int type;
        public int activation = TANH;

        public NodeGene() {
        }

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

        public ConnectionGene() {
        }

        public ConnectionGene(int in, int out, float weight, int innovation) {
            this.in = in;
            this.out = out;
            this.weight = weight;
            this.innovation = innovation;
        }
    }

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

    /** 权重变异：90% 扰动（高斯 * 0.3），10% 重赋随机值。 */
    public void mutateWeights(Random rng) {
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            if (rng.nextFloat() < 0.9f) c.weight += (float) rng.nextGaussian() * 0.3f;
            else c.weight = rng.nextFloat() * 4f - 2f;
            if (c.weight > 8f) c.weight = 8f;
            else if (c.weight < -8f) c.weight = -8f;
        }
    }

    /** 加连接：随机选一对可连节点（不成环、不重复），失败返回 false。 */
    public boolean mutateAddConnection(Random rng, InnovationCounter counter) {
        for (int tries = 0; tries < 20; tries++) {
            NodeGene a = nodes.get(rng.nextInt(nodes.size()));
            NodeGene b = nodes.get(rng.nextInt(nodes.size()));
            if (a.id == b.id) continue;
            // 确定方向：输出侧不能是输入节点，输入侧不能是输出节点
            NodeGene in = a, out = b;
            if (a.type == OUTPUT || b.type == INPUT) {
                in = b;
                out = a;
            }
            if (in.type == OUTPUT || out.type == INPUT || in.id == out.id) continue;
            if (hasConnection(in.id, out.id)) continue;
            if (createsCycle(in.id, out.id)) continue;
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

    /** 开关连接：翻转 enable 状态。 */
    public boolean mutateToggleConnection(Random rng) {
        if (conns.isEmpty()) return false;
        ConnectionGene c = conns.get(rng.nextInt(conns.size()));
        c.enabled = !c.enabled;
        return true;
    }

    public boolean mutateResetWeights(Random rng) {
        if (conns.isEmpty()) return false;
        for (int i = 0; i < conns.size(); i++) conns.get(i).weight = rng.nextFloat() * 4f - 2f;
        return true;
    }

    private boolean createsCycle(int in, int out) {
        HashMap<Integer, ArrayList<Integer>> adj = new HashMap<Integer, ArrayList<Integer>>();
        for (int i = 0; i < conns.size(); i++) {
            ConnectionGene c = conns.get(i);
            if (!c.enabled) continue;
            ArrayList<Integer> l = adj.get(c.in);
            if (l == null) {
                l = new ArrayList<Integer>();
                adj.put(c.in, l);
            }
            l.add(c.out);
        }
        ArrayList<Integer> stack = new ArrayList<Integer>();
        stack.add(out);
        ArrayList<Integer> visited = new ArrayList<Integer>();
        while (!stack.isEmpty()) {
            int cur = stack.remove(stack.size() - 1);
            if (cur == in) return true;
            if (visited.contains(cur)) continue;
            visited.add(cur);
            ArrayList<Integer> l = adj.get(cur);
            if (l != null) stack.addAll(l);
        }
        return false;
    }

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

    public boolean mutateRemoveConnection(Random rng) {
        if (conns.isEmpty()) return false;
        conns.remove(rng.nextInt(conns.size()));
        return true;
    }

    // ------------------------------------------------------------ 交叉

    /** 标准 NEAT 交叉：a 为适应度更高方，不匹配基因只从 a 继承。 */
    public static Genome crossover(Genome a, Genome b, Random rng) {
        HashMap<Integer, ConnectionGene> bMap = new HashMap<Integer, ConnectionGene>();
        for (int i = 0; i < b.conns.size(); i++) {
            ConnectionGene c = b.conns.get(i);
            bMap.put(c.innovation, c);
        }
        Genome child = new Genome();
        for (int i = 0; i < a.nodes.size(); i++) {
            NodeGene n = a.nodes.get(i);
            NodeGene nc = new NodeGene(n.id, n.type);
            nc.activation = n.activation;
            child.nodes.add(nc);
        }
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
            // 任一亲本中禁用 → 75% 概率保持禁用，否则继承 chosen 状态
            nc.enabled = disabledSomewhere ? rng.nextFloat() >= 0.75f && chosen.enabled : chosen.enabled;
            child.conns.add(nc);
            // 匹配基因可能引用 b 独有的节点
            ensureNode(child, b, nc.in);
            ensureNode(child, b, nc.out);
        }
        return child;
    }

    private static void ensureNode(Genome child, Genome src, int id) {
        if (child.node(id) != null) return;
        NodeGene n = src.node(id);
        if (n != null) { NodeGene nc = new NodeGene(n.id, n.type); nc.activation = n.activation; child.nodes.add(nc); }
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
        for (int i = 0; i < conns.size(); i++) {
            if (conns.get(i).innovation > myMax) myMax = conns.get(i).innovation;
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
            if (hasInnovation(c.innovation)) continue;
            if (c.innovation > myMax) excess++;
            else disjoint++;
        }
        int n = Math.max(conns.size(), o.conns.size());
        if (n < 20) n = 1;
        float wd = matching == 0 ? 0f : weightDiff / matching;
        return excess / (float) n + disjoint / (float) n + 0.4f * wd;
    }

    private boolean hasInnovation(int innov) {
        for (int i = 0; i < conns.size(); i++) {
            if (conns.get(i).innovation == innov) return true;
        }
        return false;
    }

    /** 创新号/节点号计数器（持久化随种群保存；每代内去重）。 */
    public static class InnovationCounter {
        public int nextInnovation;
        public int nextNode;
        private final HashMap<String, Integer> genMap = new HashMap<String, Integer>();

        public InnovationCounter() {
        }

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

        public int nextNodeId() {
            return nextNode++;
        }

        /** 每代结束后调用，清空代内去重表。 */
        public void endGeneration() {
            genMap.clear();
        }
    }
}
