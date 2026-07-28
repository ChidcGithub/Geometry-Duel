package com.geometryduel.neat;

import com.badlogic.gdx.Gdx;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.game.GameSystem;
import com.geometryduel.game.engine.ComputerEngine;
import com.geometryduel.game.engine.PlayerEngine;
import com.geometryduel.game.state.ResultGameState;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 后台训练线程：无头 GameSystem 快速模拟（候选 vs 规则 AI / 冠军池 / 玩家幽灵），
 * 按适应度驱动 NeatEvolver 逐代进化；冠军基因组供玩家对局使用。
 *
 * 改进：
 * - 每对手 3 局取平均（降低方差）
 * - 规则 AI 难度渐进（gen 0: 0.3 → gen 140: 1.0）
 * - 冠军陪练池 FIFO 5 代（保持策略多样性）
 * - 实战奖励不随时间衰减
 */
public class NeatTrainer {
    public static final int POPULATION = 50;
    private static final int MAX_MATCH_FRAMES = 180 + 7200;
    private static final int MAX_GHOSTS = 5;
    private static final int SIMS_PER_MATCH = 3;
    private static final int CHAMPION_POOL_SIZE = 5;

    private final GeometryDuelGame app;
    private final Random rng = new Random();

    private volatile int rayCount;
    private volatile NeatEvolver evolver;
    private volatile Genome champion;
    private final ArrayList<Genome> championPool = new ArrayList<Genome>();

    private volatile int generation;
    private volatile float bestFitness;
    private volatile long simMatches;
    private volatile boolean paused;
    private volatile boolean stopped;
    private float realMatchBonus;
    private final CopyOnWriteArrayList<GhostData> ghosts = new CopyOnWriteArrayList<GhostData>();

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

    public void reset(int newRayCount) {
        rayCount = newRayCount;
        NeatStorage.clear();
        ghosts.clear();
        champion = null;
        championPool.clear();
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

    /** 玩家实战结果上报：只看输赢和传送连杀。 */
    public void reportRealMatch(MatchStats m) {
        realMatchBonus += (m.aiWon ? 1000f : -30f) + m.teleportKills * 20f;
    }

    public void addGhost(GhostData g) {
        if (g == null || g.frames < 300) return;
        ghosts.add(0, g);
        while (ghosts.size() > MAX_GHOSTS) ghosts.remove(ghosts.size() - 1);
        NeatStorage.saveGhosts(new ArrayList<GhostData>(ghosts));
    }

    public int ghostCount() {
        return ghosts.size();
    }

    private GhostData pickGhost() {
        if (ghosts.isEmpty()) return null;
        return ghosts.get(rng.nextInt(ghosts.size()));
    }

    private Genome pickChampion() {
        if (championPool.isEmpty()) return null;
        return championPool.get(rng.nextInt(championPool.size()));
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
            championPool.clear();
            if (champion != null) championPool.add(champion);
            evolver = newEvolver(rayCount, d.population,
                    new Genome.InnovationCounter(d.nextInnovation, d.nextNode));
            Gdx.app.log("NeatTrainer", "loaded save: gen " + generation + " best " + bestFitness);
        } else {
            evolver = newEvolver(rayCount, null, null);
        }
        ArrayList<GhostData> loaded = NeatStorage.loadGhosts();
        for (int i = 0; i < loaded.size() && ghosts.size() < MAX_GHOSTS; i++) {
            ghosts.add(loaded.get(i));
        }
        if (!ghosts.isEmpty()) Gdx.app.log("NeatTrainer", "loaded ghosts: " + ghosts.size());
    }

    private NeatEvolver newEvolver(int rays, ArrayList<Genome> population,
                                   Genome.InnovationCounter counter) {
        int inputCount = new VisionSensor(rays).inputSize() + 1;
        return counter == null
                ? new NeatEvolver(inputCount, 5, POPULATION, rng)
                : new NeatEvolver(inputCount, 5, POPULATION, population, counter, rng);
    }

    /**
     * 课程式奖励系数：gen 0 ≈ 3.0 → 指数衰减 → 0.2 保底。
     * 行为奖励已内置软上限 30，shaping 只影响技能引导项的力度。
     */
    public static float shapingScale(int generation) {
        return 0.2f + 2.8f * (float) Math.exp(-generation / 80.0);
    }

    /** 渐进式规则 AI 难度：gen 0 → 0.3，每 20 代 +0.1，约 gen 140 达到 1.0。 */
    private static float progressiveLevel(int generation) {
        return Math.min(1.0f, 0.3f + generation * 0.005f);
    }

    private void runGeneration() {
        NeatEvolver ev = evolver;
        ArrayList<Genome> pop = ev.population;
        float[] fitness = new float[pop.size()];
        float shaping = shapingScale(generation);
        float level = progressiveLevel(generation);

        // 整代固定陪练（公平比较同代内所有个体）
        final GhostData ghost = pickGhost();
        final Genome champOpponent = pickChampion();

        // 秒拍实战奖励，本轮结束时加到最优个体上
        float bonusToApply = realMatchBonus;
        realMatchBonus = 0f;

        for (int i = 0; i < pop.size(); i++) {
            if (stopped || paused || ev != evolver) return;
            Genome g = pop.get(i);
            float f = 0f;
            int n = 0;

            f += evaluate(g, null, null, shaping, level);
            n++;
            simMatches += SIMS_PER_MATCH;

            if (champOpponent != null) {
                f += evaluate(g, champOpponent, null, shaping, 1.0f);
                n++;
                simMatches += SIMS_PER_MATCH;
            }

            if (ghost != null) {
                f += evaluate(g, null, ghost, shaping, 1.0f);
                n++;
                simMatches += SIMS_PER_MATCH;
            }

            f /= n;
            fitness[i] = f;
        }

        int best = 0;
        for (int i = 1; i < fitness.length; i++) {
            if (fitness[i] > fitness[best]) best = i;
        }

        fitness[best] += bonusToApply;

        Genome newChamp = pop.get(best).copy();
        championPool.add(0, newChamp);
        while (championPool.size() > CHAMPION_POOL_SIZE) {
            championPool.remove(championPool.size() - 1);
        }
        champion = newChamp;
        bestFitness = Math.max(bestFitness, fitness[best]);
        ev.nextGeneration(fitness);
        generation++;
    }

    /** 同一对手打 SIMS_PER_MATCH 局，返回平均适应度。 */
    private float evaluate(Genome candidate, Genome vsGenome, GhostData ghost,
                           float shaping, float level) {
        float sum = 0f;
        for (int i = 0; i < SIMS_PER_MATCH; i++) {
            sum += simulate(candidate, vsGenome, ghost, level).fitness(shaping);
        }
        return sum / SIMS_PER_MATCH;
    }

    private MatchStats simulate(final Genome candidate, final Genome vsGenome,
                                final GhostData ghost, final float level) {
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
                return ghost != null ? (PlayerEngine) new ReplayEngine(ghost)
                        : vsGenome != null ? (PlayerEngine) new NeatEngine(vsGenome, rays)
                        : new ComputerEngine(sys, level);
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
