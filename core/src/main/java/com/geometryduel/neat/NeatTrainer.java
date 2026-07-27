package com.geometryduel.neat;

import com.badlogic.gdx.Gdx;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.game.GameSystem;
import com.geometryduel.game.engine.ComputerEngine;
import com.geometryduel.game.engine.PlayerEngine;
import com.geometryduel.game.state.ResultGameState;

import java.util.ArrayList;
import java.util.Random;

/**
 * 后台训练线程：无头 GameSystem 快速模拟（候选 vs 规则 AI / 名人堂冠军），
 * 按适应度驱动 NeatEvolver 逐代进化；冠军基因组供玩家对局使用。
 *
 * 线程模型：单后台线程持续跑代，玩家对局进行中由 GameScreen 暂停；
 * 每 10 代写盘一次，shutdown 时最终保存。
 */
public class NeatTrainer {
    public static final int POPULATION = 50;
    private static final int MAX_MATCH_FRAMES = 180 + 7200; // 倒计时 + 2 分钟

    private final GeometryDuelGame app;
    private final Random rng = new Random();

    private volatile int rayCount;
    private volatile NeatEvolver evolver;
    private volatile Genome champion;      // 历史最佳（深拷贝，绝不变异）
    private Genome championSource;         // 冠军在种群中的源引用（用于实战奖励加成）
    private Genome hallOfFame;             // 上一代冠军（陪练对手）

    private volatile int generation;
    private volatile float bestFitness;
    private volatile long simMatches;
    private volatile boolean paused;
    private volatile boolean stopped;
    private float realMatchBonus;

    private Thread thread;

    public NeatTrainer(GeometryDuelGame app, int rayCount) {
        this.app = app;
        this.rayCount = rayCount;
    }

    // ------------------------------------------------------------ 生命周期

    public void start() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "neat-trainer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        thread.start();
    }

    public void shutdown() {
        stopped = true;
        paused = false;
        if (thread != null) {
            try {
                thread.join(3000);
            } catch (InterruptedException ignored) {
            }
        }
        saveNow();
    }

    /** 清空存档并从零进化（射线数变更时也必须调用，因输入维度变化）。 */
    public void reset(int newRayCount) {
        rayCount = newRayCount;
        NeatStorage.clear();
        champion = null;
        championSource = null;
        hallOfFame = null;
        generation = 0;
        bestFitness = 0f;
        simMatches = 0;
        realMatchBonus = 0f;
        evolver = newEvolver(newRayCount, null, null);
    }

    public void saveNow() {
        NeatEvolver ev = evolver;
        if (ev != null) NeatStorage.save(rayCount, generation, bestFitness, simMatches, ev, champion);
    }

    // ------------------------------------------------------------ 外部接口

    public void setPaused(boolean p) {
        paused = p;
    }

    /** 玩家实战结果上报：转化为冠军在下代评估中的奖励/惩罚（含技能使用小奖）。 */
    public void reportRealMatch(MatchStats m) {
        realMatchBonus += (m.aiWon ? 30f : -30f)
                + Math.min(m.longShotsFired, 10) * 1.5f
                + Math.min(m.teleportsUsed, 8) * 1f;
    }

    public Genome currentChampion() {
        return champion;
    }

    public int generation() {
        return generation;
    }

    public float bestFitness() {
        return bestFitness;
    }

    public long simMatches() {
        return simMatches;
    }

    // ------------------------------------------------------------ 训练循环

    private void loop() {
        if (evolver == null) loadOrCreate();
        while (!stopped) {
            if (paused) {
                sleep(200);
                continue;
            }
            try {
                runGeneration();
            } catch (Throwable t) {
                Gdx.app.error("NeatTrainer", "generation failed", t);
                sleep(1000);
            }
            if (generation % 10 == 0) saveNow();
        }
    }

    private void loadOrCreate() {
        NeatStorage.SaveData d = NeatStorage.load(rayCount);
        if (d != null) {
            generation = d.generation;
            bestFitness = d.bestFitness;
            simMatches = d.simMatches;
            champion = d.champion;
            hallOfFame = null;
            evolver = newEvolver(rayCount, d.population,
                    new Genome.InnovationCounter(d.nextInnovation, d.nextNode));
            Gdx.app.log("NeatTrainer", "loaded save: gen " + generation + " best " + bestFitness);
        } else {
            evolver = newEvolver(rayCount, null, null);
        }
    }

    private NeatEvolver newEvolver(int rays, ArrayList<Genome> population,
                                   Genome.InnovationCounter counter) {
        int inputCount = new VisionSensor(rays).inputSize() + 1; // +1 偏置
        return counter == null
                ? new NeatEvolver(inputCount, 5, POPULATION, rng)
                : new NeatEvolver(inputCount, 5, POPULATION, population, counter, rng);
    }

    private void runGeneration() {
        NeatEvolver ev = evolver;
        ArrayList<Genome> pop = ev.population;
        float[] fitness = new float[pop.size()];
        for (int i = 0; i < pop.size(); i++) {
            if (stopped || paused || ev != evolver) return; // 代中止：不进化，下轮重跑
            Genome g = pop.get(i);
            MatchStats m1 = simulate(g, null);
            float f = m1.fitness();
            simMatches++;
            Genome hof = hallOfFame;
            if (hof != null) {
                MatchStats m2 = simulate(g, hof);
                f = (f + m2.fitness()) * 0.5f;
                simMatches++;
            }
            if (g == championSource) f += realMatchBonus;
            fitness[i] = f;
        }

        int best = 0;
        for (int i = 1; i < fitness.length; i++) {
            if (fitness[i] > fitness[best]) best = i;
        }
        if (fitness[best] > bestFitness || champion == null) {
            bestFitness = Math.max(bestFitness, fitness[best]);
            hallOfFame = champion;
            champion = pop.get(best).copy();
            championSource = pop.get(best);
        }
        ev.nextGeneration(fitness);
        generation++;
        realMatchBonus *= 0.7f;
    }

    /**
     * 无头快速模拟一局：候选为 myGroup，对手为规则 AI（vsRule==null 时）
     * 或指定基因组（名人堂）。逻辑/渲染已分离，全程纯 Java 运算。
     */
    private MatchStats simulate(final Genome candidate, final Genome vsGenome) {
        final int rays = rayCount;
        GameSystem.EngineFactory engineA = new GameSystem.EngineFactory() {
            @Override
            public PlayerEngine create(GameSystem sys) {
                return new NeatEngine(candidate, rays);
            }
        };
        GameSystem.EngineFactory engineB = new GameSystem.EngineFactory() {
            @Override
            public PlayerEngine create(GameSystem sys) {
                return vsGenome != null ? (PlayerEngine) new NeatEngine(vsGenome, rays)
                        : new ComputerEngine(sys, 1.0f);
            }
        };
        GameSystem sys = new GameSystem(app, false, false, 1.0f, null,
                engineA, engineB, true, new Random(rng.nextLong()));
        MatchTracker tracker = new MatchTracker(sys.myGroup);
        while (!(sys.currentState instanceof ResultGameState) && sys.frameCount < MAX_MATCH_FRAMES) {
            sys.update();
            tracker.update();
        }
        MatchStats m = new MatchStats();
        m.frames = sys.frameCount;
        if (sys.currentState instanceof ResultGameState) {
            m.aiWon = ((ResultGameState) sys.currentState).winGroup == sys.myGroup.id;
        }
        m.hitsDealt = sys.otherGroup.damageCount;
        m.hitsTaken = sys.myGroup.damageCount;
        tracker.fill(m);
        return m;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
