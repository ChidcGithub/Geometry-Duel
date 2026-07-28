package com.geometryduel.neat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class NeatEvolver {
    public static final float COMPAT_THRESHOLD = 3.0f;
    private static final int MAX_SPECIES = 15;
    private static final int ELITES_PER_SPECIES = 2;

    public final int inputCount, outputCount, populationSize;
    public ArrayList<Genome> population = new ArrayList<Genome>();
    public Genome.InnovationCounter counter;
    private final Random rng;

    float compatThreshold = COMPAT_THRESHOLD;
    private int stagnantGenerations;
    private float lastBestFitness;

    /** 物种信息：代表基因组 + 策略标签 + 成员索引列表 */
    public static class SpeciesInfo {
        public final ArrayList<Integer> members = new ArrayList<Integer>();
        public Genome representative;
        public int bestIndex;
        public float bestFitness;
        public String strategyLabel; // 给玩家的可读标签
    }
    public ArrayList<SpeciesInfo> currentSpecies = new ArrayList<SpeciesInfo>();

    public NeatEvolver(int inputCount, int outputCount, int populationSize, Random rng) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        this.populationSize = populationSize;
        this.rng = rng;
        this.counter = new Genome.InnovationCounter(inputCount * outputCount, inputCount + outputCount);
        for (int i = 0; i < populationSize; i++) population.add(createMinimal());
    }

    public NeatEvolver(int inputCount, int outputCount, int populationSize,
                       ArrayList<Genome> population, Genome.InnovationCounter counter, Random rng) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        this.populationSize = populationSize;
        this.population = population;
        this.counter = counter;
        this.rng = rng;
    }

    private Genome createMinimal() {
        Genome g = new Genome();
        for (int i = 0; i < inputCount; i++) g.nodes.add(new Genome.NodeGene(i, Genome.INPUT));
        for (int i = 0; i < outputCount; i++) g.nodes.add(new Genome.NodeGene(inputCount + i, Genome.OUTPUT));
        for (int i = 0; i < inputCount; i++)
            for (int o = 0; o < outputCount; o++)
                g.conns.add(new Genome.ConnectionGene(i, inputCount + o,
                        rng.nextFloat() * 2f - 1f, i * outputCount + o));
        // bootstrap 12~18 hidden nodes
        int bootstrap = 12 + rng.nextInt(7);
        for (int k = 0; k < bootstrap; k++) g.mutateAddNode(rng, counter);
        return g;
    }

    // ------------------------------------------------------------ 动态阈值

    private void adjustThreshold(float bestFitness) {
        if (bestFitness <= lastBestFitness + 0.5f) {
            stagnantGenerations++;
            if (stagnantGenerations > 15)
                compatThreshold = Math.max(1.5f, compatThreshold * 0.95f);
        } else {
            stagnantGenerations = 0;
            if (compatThreshold < COMPAT_THRESHOLD)
                compatThreshold = Math.min(COMPAT_THRESHOLD, compatThreshold + 0.05f);
        }
        lastBestFitness = bestFitness;
    }

    // ------------------------------------------------------------ 进化

    public void nextGeneration(float[] fitness) {
        float currentBest = 0f;
        for (float f : fitness) if (f > currentBest) currentBest = f;
        adjustThreshold(currentBest);

        // ---- 物种划分 (D) ----
        currentSpecies.clear();
        int[] speciesOf = new int[population.size()];
        for (int i = 0; i < population.size(); i++) {
            Genome g = population.get(i);
            int placed = -1;
            for (int s = 0; s < currentSpecies.size(); s++) {
                if (g.distance(currentSpecies.get(s).representative) < compatThreshold) { placed = s; break; }
            }
            if (placed < 0 && currentSpecies.size() < MAX_SPECIES) {
                placed = currentSpecies.size();
                SpeciesInfo si = new SpeciesInfo();
                si.representative = g;
                currentSpecies.add(si);
            }
            if (placed < 0) {
                // 分配到最近的物种
                float minDist = Float.MAX_VALUE;
                for (int s = 0; s < currentSpecies.size(); s++) {
                    float d = g.distance(currentSpecies.get(s).representative);
                    if (d < minDist) { minDist = d; placed = s; }
                }
            }
            if (placed >= 0) {
                currentSpecies.get(placed).members.add(i);
                speciesOf[i] = placed;
            }
        }

        // 计算每个物种的最优个体
        for (int s = 0; s < currentSpecies.size(); s++) {
            SpeciesInfo si = currentSpecies.get(s);
            si.bestFitness = -Float.MAX_VALUE;
            for (int idx : si.members) {
                if (fitness[idx] > si.bestFitness) { si.bestFitness = fitness[idx]; si.bestIndex = idx; }
            }
            si.strategyLabel = labelSpecies(si, fitness);
        }

        // ---- 显式适应度分享 ----
        float[] adjusted = new float[population.size()];
        float totalAdjusted = 0f;
        for (int s = 0; s < currentSpecies.size(); s++) {
            SpeciesInfo si = currentSpecies.get(s);
            float sz = si.members.size();
            for (int idx : si.members) {
                float a = Math.max(0.01f, fitness[idx]) / sz;
                adjusted[idx] = a;
                totalAdjusted += a;
            }
        }

        // ---- 构建下一代 + 精英保留 (D) ----
        ArrayList<Genome> next = new ArrayList<Genome>();

        // 精英：每个物种保留 ELITES_PER_SPECIES 个最佳个体
        for (int s = 0; s < currentSpecies.size(); s++) {
            SpeciesInfo si = currentSpecies.get(s);
            if (si.members.size() < 2) continue;
            // 按适应度排序取前 ELITES_PER_SPECIES
            int[] sorted = new int[si.members.size()];
            for (int k = 0; k < sorted.length; k++) sorted[k] = si.members.get(k);
            for (int k = 0; k < sorted.length; k++)
                for (int m = k + 1; m < sorted.length; m++)
                    if (fitness[sorted[m]] > fitness[sorted[k]]) { int t = sorted[k]; sorted[k] = sorted[m]; sorted[m] = t; }
            for (int k = 0; k < Math.min(ELITES_PER_SPECIES, sorted.length); k++)
                next.add(population.get(sorted[k]).copy());
        }

        int remaining = populationSize - next.size();
        if (totalAdjusted <= 0f) totalAdjusted = 1f;

        // 按物种配额生成后代
        for (int s = 0; s < currentSpecies.size() && remaining > 0; s++) {
            SpeciesInfo si = currentSpecies.get(s);
            if (si.members.size() < 3) continue;
            float speciesSum = 0f;
            for (int idx : si.members) speciesSum += adjusted[idx];
            int quota = Math.round(speciesSum / totalAdjusted * remaining);
            for (int q = 0; q < quota && next.size() < populationSize; q++) {
                int p1 = tournament(si.members, adjusted);
                int p2 = tournament(si.members, adjusted);
                Genome a = population.get(p1), b = population.get(p2);
                Genome child = adjusted[p1] >= adjusted[p2]
                        ? Genome.crossover(a, b, rng) : Genome.crossover(b, a, rng);
                mutateWithOwnRates(child);
                next.add(child);
            }
        }

        // 补齐
        ArrayList<Integer> all = new ArrayList<Integer>();
        for (int i = 0; i < population.size(); i++) all.add(i);
        while (next.size() < populationSize) {
            int p1 = tournament(all, adjusted);
            int p2 = tournament(all, adjusted);
            Genome a = population.get(p1), b = population.get(p2);
            Genome child = adjusted[p1] >= adjusted[p2]
                    ? Genome.crossover(a, b, rng) : Genome.crossover(b, a, rng);
            mutateWithOwnRates(child);
            next.add(child);
        }

        population = next;
        counter.endGeneration();
    }

    // ---- 锦标赛选择 ----
    private int tournament(ArrayList<Integer> members, float[] adjusted) {
        int best = members.get(rng.nextInt(members.size()));
        for (int i = 0; i < 4; i++) {
            int c = members.get(rng.nextInt(members.size()));
            if (adjusted[c] > adjusted[best]) best = c;
        }
        return best;
    }

    /** 使用基因组自身的突变率 (B) */
    private void mutateWithOwnRates(Genome g) {
        g.mutateMutationRates(rng); // 先变异突变率本身
        float r = rng.nextFloat();
        float a = g.weightMutProb;
        float b = a + g.addNodeProb;
        float c = b + g.addConnProb;
        float d = c + g.toggleProb;
        float e = d + g.resetProb;
        float f = e + g.activationProb;
        if (r < a) g.mutateWeights(rng);
        else if (r < b) g.mutateAddNode(rng, counter);
        else if (r < c) g.mutateAddConnection(rng, counter);
        else if (r < d) g.mutateToggleConnection(rng);
        else if (r < e) g.mutateResetWeights(rng);
        else if (r < f) g.mutateActivation(rng);
        else g.mutateRemoveConnection(rng);
    }

    // ---- 物种标签 (D) ----
    private String labelSpecies(SpeciesInfo si, float[] fitness) {
        int totalNodes = 0, totalConns = 0;
        float avgMutPower = 0f;
        int count = Math.min(5, si.members.size());
        for (int k = 0; k < count; k++) {
            Genome g = population.get(si.members.get(k));
            totalNodes += g.nodes.size();
            totalConns += g.conns.size();
            avgMutPower += g.mutationPower;
        }
        avgMutPower /= count;
        float avgNodes = totalNodes / (float) count;
        float avgConns = totalConns / (float) count;

        if (avgMutPower > 0.5f) return "Explorer";
        if (avgNodes > 40) return "Tactician";
        if (avgConns > 300) return "Strategist";
        if (avgNodes < 20) return "Minimalist";
        return "Balanced";
    }

    /** 返回各物种冠军（供玩家选择对手） */
    public ArrayList<Genome> speciesChampions() {
        ArrayList<Genome> champs = new ArrayList<Genome>();
        for (SpeciesInfo si : currentSpecies) {
            if (!si.members.isEmpty())
                champs.add(population.get(si.bestIndex));
        }
        return champs;
    }
}
