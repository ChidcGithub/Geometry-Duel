package com.geometryduel.neat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import java.util.ArrayList;

/**
 * NEAT 存档：种群 + 冠军 + 世代号 + 创新计数 + 射线数，JSON 存本地文件。
 * 解析失败 / 版本不符 / 射线数不符 → 返回 null（调用方回退全新进化）。
 */
public class NeatStorage {
    private static final int VERSION = 1;
    private static final String FILE = "neat-ai.json";

    public static class SaveData {
        public int version;
        public int rayCount;
        public int generation;
        public float bestFitness;
        public long simMatches;
        public int nextInnovation;
        public int nextNode;
        public Genome champion;
        public ArrayList<Genome> population;
    }

    public static SaveData load(int rayCount) {
        try {
            FileHandle fh = Gdx.files.local(FILE);
            if (!fh.exists()) return null;
            SaveData d = new Json().fromJson(SaveData.class, fh);
            if (d == null || d.version != VERSION || d.rayCount != rayCount
                    || d.population == null || d.population.isEmpty()) {
                Gdx.app.log("NeatStorage", "save incompatible, starting fresh");
                return null;
            }
            return d;
        } catch (Throwable t) {
            Gdx.app.error("NeatStorage", "load failed, starting fresh", t);
            clear();
            return null;
        }
    }

    public static void save(int rayCount, int generation, float bestFitness, long simMatches,
                            NeatEvolver evolver, Genome champion) {
        try {
            SaveData d = new SaveData();
            d.version = VERSION;
            d.rayCount = rayCount;
            d.generation = generation;
            d.bestFitness = bestFitness;
            d.simMatches = simMatches;
            d.nextInnovation = evolver.counter.nextInnovation;
            d.nextNode = evolver.counter.nextNode;
            d.champion = champion;
            d.population = evolver.population;
            Gdx.files.local(FILE).writeString(new Json().toJson(d), false);
        } catch (Throwable t) {
            Gdx.app.error("NeatStorage", "save failed", t);
        }
    }

    public static void clear() {
        try {
            Gdx.files.local(FILE).delete();
        } catch (Throwable ignored) {
        }
    }
}
