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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 后台训练线程：无头 GameSystem 快速模拟（候选 vs 规则 AI / 冠军池 / 玩家幽灵），
 * 按适应度驱动 NeatEvolver 逐代进化；冠军基因组供玩家对局使用。
 *
 * 改进：
 * - 多线程并行评估个体（50 个分派到线程池，充分利用多核）
 * - 每对手 3 局取平均（降低方差）
 * - 规则 AI 难度渐进（gen 0: 0.3 → gen 140: 1.0）
 * - 冠军陪练池 FIFO 5 代（保持策略多样性）
 */
public class NeatTrainer {
    public static final int POPULATION = 50;
    private static final int MAX_MATCH_FRAMES = 180 + 7200;
    private static final int MAX_GHOSTS = 5;
    private static final int SIMS_PER_MATCH = 3;
    private static final int CHAMPION_POOL_SIZE = 5;

    private final GeometryDuelGame app;
    private final Random rng = new Random();

    /** 并行评估线程池（核数 - 1，至少为 2）。 */
    private ExecutorService threadPool;
    /** 每线程独立 Random，避免同步开销。 */
    private final ThreadLocal<Random> rngPerThread = new ThreadLocal<Random>() {
        @Override
        protected Random initialValue() {
            return new Random();
        }
    };

    private volatile int rayCount;
    private volatile NeatEvolver evolver;
    private volatile Genome champion;
    private final ArrayList<Genome> championPool = new ArrayList<Genome>();

    private volatile int generation;
    private volatile float bestFitness;
    private final AtomicLong simMatches = new AtomicLong();
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
        int cores = Runtime.getRuntime().availableProcessors();
        int workers = Math.max(2, cores - 1);
        threadPool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "neat-eval-" + r.hashCode());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        });
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "neat-trainer");
        thread.setDaemon(true);
        thread.start();
    }

    public void shutdown() {
        stopped = true;
        if (threadPool != null) threadPool.shutdownNow();
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
        simMatches.set(0);
        realMatchBonus = 0f;
        evolver = newEvolver(newRayCount, null, null);
    }

    public void saveNow() {
        NeatEvolver ev = evolver;
        if (ev != null) NeatStorage.save(rayCount, generation, bestFitness, simMatches.get(), ev, champion);
    }

    // ------------------------------------------------------------ 外部接口

    public void setPaused(boolean p) {
        paused = p;
    }

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
        return simMatches.get();
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
            simMatches.set(d.simMatches);
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

    public static float shapingScale(int generation) {
        return 0.2f + 2.8f * (float) Math.exp(-generation / 80.0);
    }

    private static float progressiveLevel(int generation) {
        return Math.min(1.0f, 0.3f + generation * 0.005f);
    }

    private void runGeneration() {
        final NeatEvolver ev = evolver;
        final ArrayList<Genome> pop = ev.population;
        final float[] fitness = new float[pop.size()];
        final float shaping = shapingScale(generation);
        final float level = progressiveLevel(generation);

        final GhostData ghost = pickGhost();
        final Genome champOpponent = pickChampion();

        final float bonusToApply = realMatchBonus;
        realMatchBonus = 0f;

        final int total = pop.size();
        final CountDownLatch latch = new CountDownLatch(total);

        for (int i = 0; i < total; i++) {
            final int index = i;
            final Genome g = pop.get(i);
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    if (stopped || paused || ev != evolver) {
                        latch.countDown();
                        return;
                    }
                    float f = 0f;
                    int n = 0;

                    f += evaluate(g, null, null, shaping, level);
                    n++;
                    simMatches.addAndGet(SIMS_PER_MATCH);

                    if (champOpponent != null) {
                        f += evaluate(g, champOpponent, null, shaping, 1.0f);
                        n++;
                        simMatches.addAndGet(SIMS_PER_MATCH);
                    }

                    if (ghost != null) {
                        f += evaluate(g, null, ghost, shaping, 1.0f);
                        n++;
                        simMatches.addAndGet(SIMS_PER_MATCH);
                    }

                    fitness[index] = f / n;
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }

        if (stopped || ev != evolver) return;

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

    private float evaluate(Genome candidate, Genome vsGenome, GhostData ghost,
                           float shaping, float level) {
        Random r = rngPerThread.get();
        float sum = 0f;
        for (int i = 0; i < SIMS_PER_MATCH; i++) {
            sum += simulate(candidate, vsGenome, ghost, level, r).fitness(shaping);
        }
        return sum / SIMS_PER_MATCH;
    }

    private MatchStats simulate(final Genome candidate, final Genome vsGenome,
                                final GhostData ghost, final float level, final Random rngLocal) {
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
                engineA, engineB, true, new Random(rngLocal.nextLong()));
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
