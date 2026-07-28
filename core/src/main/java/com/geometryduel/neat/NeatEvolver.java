package com.geometryduel.neat;

import java.util.ArrayList;
import java.util.Random;

/**
 * NEAT 种群进化器：物种划分（兼容距离）、适应度共享、精英保留、
 * 锦标赛选择、交叉 + 变异产生下一代。
 */
public class NeatEvolver {
    public static final float COMPAT_THRESHOLD = 3.0f;

    public final int inputCount, outputCount, populationSize;
    public ArrayList<Genome> population = new ArrayList<Genome>();
    public Genome.InnovationCounter counter;
    private final Random rng;

    public NeatEvolver(int inputCount, int outputCount, int populationSize, Random rng) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        this.populationSize = populationSize;
        this.rng = rng;
        this.counter = new Genome.InnovationCounter(inputCount * outputCount, inputCount + outputCount);
        for (int i = 0; i < populationSize; i++) population.add(createMinimal());
    }

    /** 从存档恢复。 */
    public NeatEvolver(int inputCount, int outputCount, int populationSize,
                       ArrayList<Genome> population, Genome.InnovationCounter counter, Random rng) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        this.populationSize = populationSize;
        this.population = population;
        this.counter = counter;
        this.rng = rng;
    }

    /** 初始拓扑：无隐藏节点，全部 输入→输出 直连（创新号固定，便于交叉）。 */
    private Genome createMinimal() {
        Genome g = new Genome();
        for (int i = 0; i < inputCount; i++) g.nodes.add(new Genome.NodeGene(i, Genome.INPUT));
        for (int i = 0; i < outputCount; i++) g.nodes.add(new Genome.NodeGene(inputCount + i, Genome.OUTPUT));
        int innov = 0;
        for (int i = 0; i < inputCount; i++) {
            for (int o = 0; o < outputCount; o++) {
                g.conns.add(new Genome.ConnectionGene(i, inputCount + o,
                        rng.nextFloat() * 2f - 1f, innov++));
            }
        }
        return g;
    }

    // ------------------------------------------------------------ 世代更替

    /** fitness 与 population 一一对应；调用后种群被替换为下一代。 */
    public void nextGeneration(float[] fitness) {
        // 1. 物种划分
        ArrayList<ArrayList<Integer>> species = new ArrayList<ArrayList<Integer>>();
        ArrayList<Genome> reps = new ArrayList<Genome>();
        for (int i = 0; i < population.size(); i++) {
            Genome g = population.get(i);
            int placed = -1;
            for (int s = 0; s < reps.size(); s++) {
                if (g.distance(reps.get(s)) < COMPAT_THRESHOLD) {
                    placed = s;
                    break;
                }
            }
            if (placed < 0) {
                placed = species.size();
                species.add(new ArrayList<Integer>());
                reps.add(g);
            }
            species.get(placed).add(i);
        }

        // 2. 适应度共享 + 各物种总适应度
        float[] adjusted = new float[population.size()];
        float totalAdjusted = 0f;
        float[] speciesTotal = new float[species.size()];
        for (int s = 0; s < species.size(); s++) {
            ArrayList<Integer> members = species.get(s);
            for (int i = 0; i < members.size(); i++) {
                int idx = members.get(i);
                float a = Math.max(0.01f, fitness[idx]) / members.size();
                adjusted[idx] = a;
                speciesTotal[s] += a;
                totalAdjusted += a;
            }
        }

        // 3. 精英保留（物种规模 >= 3 保留头名）
        ArrayList<Genome> next = new ArrayList<Genome>();
        for (int s = 0; s < species.size(); s++) {
            ArrayList<Integer> members = species.get(s);
            if (members.size() < 3) continue;
            int best = members.get(0);
            for (int i = 1; i < members.size(); i++) {
                if (fitness[members.get(i)] > fitness[best]) best = members.get(i);
            }
            next.add(population.get(best).copy());
        }

        // 4. 按物种适应度份额分配后代名额
        int remaining = populationSize - next.size();
        if (totalAdjusted <= 0f) totalAdjusted = 1f;
        for (int s = 0; s < species.size() && remaining > 0; s++) {
            int quota = Math.round(speciesTotal[s] / totalAdjusted * remaining);
            ArrayList<Integer> members = species.get(s);
            for (int q = 0; q < quota && next.size() < populationSize; q++) {
                int p1 = tournament(members, adjusted);
                int p2 = tournament(members, adjusted);
                Genome a = population.get(p1), b = population.get(p2);
                Genome child = adjusted[p1] >= adjusted[p2]
                        ? Genome.crossover(a, b, rng) : Genome.crossover(b, a, rng);
                mutate(child);
                next.add(child);
            }
        }
        // 名额不足时从全局锦标赛补齐
        while (next.size() < populationSize) {
            ArrayList<Integer> all = new ArrayList<Integer>();
            for (int i = 0; i < population.size(); i++) all.add(i);
            int p1 = tournament(all, adjusted);
            int p2 = tournament(all, adjusted);
            Genome a = population.get(p1), b = population.get(p2);
            Genome child = adjusted[p1] >= adjusted[p2]
                    ? Genome.crossover(a, b, rng) : Genome.crossover(b, a, rng);
            mutate(child);
            next.add(child);
        }

        population = next;
        counter.endGeneration();
    }

    private int tournament(ArrayList<Integer> members, float[] adjusted) {
        int best = members.get(rng.nextInt(members.size()));
        for (int i = 0; i < 4; i++) {
            int c = members.get(rng.nextInt(members.size()));
            if (adjusted[c] > adjusted[best]) best = c;
        }
        return best;
    }

    private void mutate(Genome g) {
        float r = rng.nextFloat();
        if (r < 0.7f) g.mutateWeights(rng);
        else if (r < 0.78f) g.mutateAddNode(rng, counter);
        else if (r < 0.90f) g.mutateAddConnection(rng, counter);
    }
}
