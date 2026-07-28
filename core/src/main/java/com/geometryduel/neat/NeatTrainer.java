package com.geometryduel.neat;

import com.badlogic.gdx.Gdx;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.game.GameSystem;
import com.geometryduel.game.engine.ComputerEngine;
import com.geometryduel.game.engine.PlayerEngine;
import com.geometryduel.game.state.ResultGameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class NeatTrainer {
    public static final int POPULATION = 150;
    private static final int MAX_MATCH_FRAMES = 180 + 7200;
    private static final int MAX_GHOSTS = 20;
    private static final int SIMS_PER_MATCH = 3;
    private static final int CHAMPION_POOL_SIZE = 5;
    private static final int HISTORICAL_CHAMPION_SIZE = 10;
    private static final int SAVE_INTERVAL = 5;
    private static final int PATIENCE = 40;

    // 硬件感知的动态参数
    private int dynamicPopulation;
    private int dynamicSimsPerMatch;
    private int dynamicSaveInterval;

    private final GeometryDuelGame app;
    private final Random rng = new Random();
    private final Object evolverLock = new Object();

    private ExecutorService threadPool;
    private final ThreadLocal<Random> rngPerThread = new ThreadLocal<Random>() {
        @Override
        protected Random initialValue() { return new Random(); }
    };

    private volatile int rayCount;
    private volatile NeatEvolver evolver;
    private volatile Genome champion;
    private final CopyOnWriteArrayList<Genome> championPool = new CopyOnWriteArrayList<Genome>();
    private final ArrayList<Genome> historicalChampions = new ArrayList<Genome>();
    private final HashMap<String, Integer> strategyCounts = new HashMap<String, Integer>();

    private volatile int generation;
    private volatile float bestFitness;
    private final AtomicLong simMatches = new AtomicLong();
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile boolean resetting;
    private volatile boolean converged;
    private volatile float realMatchBonus;
    private float championElo = 1200f;
    private int playerWins, playerLosses;
    private int patienceCounter;
    private float historicalBestFitness;
    private final ArrayList<Float> fitnessHistory = new ArrayList<Float>();
    private final CopyOnWriteArrayList<GhostData> ghosts = new CopyOnWriteArrayList<GhostData>();

    private Thread thread;

    public NeatTrainer(GeometryDuelGame app, int rayCount) {
        this.app = app;
        this.rayCount = rayCount;
    }

    private void initializeDynamicParameters() {
        boolean hasNpu = app.hardware.npuInfo != null && !app.hardware.npuInfo.contains("None");
        int cores = Runtime.getRuntime().availableProcessors();

        // 种群规模：有NPU时增加50%
        dynamicPopulation = hasNpu ? POPULATION * 3 / 2 : POPULATION;

        // 模拟次数：有NPU时增加50%
        dynamicSimsPerMatch = hasNpu ? SIMS_PER_MATCH * 3 / 2 : SIMS_PER_MATCH;

        // 保存间隔：有NPU时更频繁保存（每3代），否则每5代
        dynamicSaveInterval = hasNpu ? 3 : SAVE_INTERVAL;

        Gdx.app.log("NeatTrainer", "dynamic params: pop=" + dynamicPopulation +
                    ", sims=" + dynamicSimsPerMatch + ", saveInterval=" + dynamicSaveInterval);
    }

    // ------------------------------------------------------------ 生命周期

    public void start() {
        app.hardware.detect();
        initializeDynamicParameters();

        int cores = Runtime.getRuntime().availableProcessors();
        int workers;
        if (app.hardware.npuInfo != null && !app.hardware.npuInfo.contains("None")) {
            workers = Math.max(8, cores * 2);
        } else {
            workers = Math.max(4, cores);
        }
        workers = Math.min(workers, 32);

        threadPool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "neat-eval-" + Integer.toHexString(r.hashCode()));
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        });
        thread = new Thread(() -> loop(), "neat-trainer");
        thread.setDaemon(true);
        thread.start();
        Gdx.app.log("NeatTrainer", "started with " + workers + " workers, NPU: " + app.hardware.npuInfo);
    }

    public void shutdown() {
        stopped = true;
        if (threadPool != null) threadPool.shutdownNow();
        if (thread != null) { try { thread.join(3000); } catch (InterruptedException e) {} }
        saveNow();
    }

    public void reset(int newRayCount) {
        synchronized (evolverLock) {
            resetting = true;
            try {
                rayCount = newRayCount;
                NeatStorage.clear();
                ghosts.clear();
                champion = null;
                championPool.clear();
                historicalChampions.clear();
                strategyCounts.clear();
                generation = 0;
                bestFitness = 0f;
                simMatches.set(0);
                realMatchBonus = 0f;
                championElo = 1200f;
                playerWins = playerLosses = 0;
                patienceCounter = 0;
                historicalBestFitness = 0f;
                converged = false;
                fitnessHistory.clear();
                evolver = newEvolver(newRayCount, null, null);
            } finally {
                resetting = false;
            }
        }
    }

    public void saveNow() {
        NeatEvolver ev = evolver;
        if (ev != null) NeatStorage.save(rayCount, generation, bestFitness, simMatches.get(), ev, champion);
    }

    // ------------------------------------------------------------ 外部接口

    public void setPaused(boolean p) { paused = p; }

    public void reportRealMatch(MatchStats m) {
        playerWins += m.aiWon ? 1 : 0;
        playerLosses += m.aiWon ? 0 : 1;
        int total = playerWins + playerLosses;
        float opponentRating = total > 0 ? 1200f + (playerWins / (float) total - 0.5f) * 200f : 1200f;
        championElo = updateElo(championElo, opponentRating, m.aiWon);
        float eloDiff = opponentRating - championElo;
        float mult = 1f + Math.max(-0.5f, Math.min(0.5f, eloDiff / 400f));
        realMatchBonus += (m.aiWon ? 300f : -20f) * mult + m.teleportKills * 15f;
    }

    private static float updateElo(float rating, float oppRating, boolean won) {
        float expected = 1f / (1f + (float) Math.pow(10, (oppRating - rating) / 400f));
        return rating + 32f * ((won ? 1f : 0f) - expected);
    }

    public float getChampionElo() { return championElo; }

    public void addGhost(GhostData g) {
        if (g == null || g.frames < 300) return;
        if (g.calculateQuality() < 0.15f) {
            Gdx.app.log("NeatTrainer", "ghost rejected, quality too low");
            return;
        }
        ghosts.add(0, g);
        while (ghosts.size() > MAX_GHOSTS) {
            int worst = 0;
            float wq = ghosts.get(0).calculateQuality();
            for (int i = 1; i < ghosts.size(); i++) {
                float q = ghosts.get(i).calculateQuality();
                if (q < wq) { wq = q; worst = i; }
            }
            ghosts.remove(worst);
        }
        NeatStorage.saveGhosts(new ArrayList<GhostData>(ghosts));
    }

    public int ghostCount() { return ghosts.size(); }

    private GhostData pickGhost() {
        if (ghosts.isEmpty()) return null;
        return ghosts.get(rng.nextInt(ghosts.size()));
    }

    private Genome pickChampion() {
        if (championPool.isEmpty()) return null;
        return championPool.get(rng.nextInt(championPool.size()));
    }

    private Genome pickHistoricalChampion() {
        if (historicalChampions.isEmpty()) return null;
        return historicalChampions.get(rng.nextInt(historicalChampions.size()));
    }

    public Genome currentChampion() { return champion; }
    public int generation() { return generation; }
    public float bestFitness() { return bestFitness; }
    public long simMatches() { return simMatches.get(); }
    public boolean isConverged() { return converged; }

    // ------------------------------------------------------------ 训练循环

    private void loop() {
        if (evolver == null) loadOrCreate();
        while (!stopped && !converged) {
            if (paused) { sleep(200); continue; }
            try { runGeneration(); checkConvergence(); }
            catch (Throwable t) { Gdx.app.error("NeatTrainer", "gen failed", t); sleep(1000); }
            if (generation % dynamicSaveInterval == 0) { saveNow(); if (generation % 20 == 0) verifySave(); }
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
            Gdx.app.log("NeatTrainer", "loaded gen " + generation + " best " + bestFitness);
        } else {
            evolver = newEvolver(rayCount, null, null);
        }
        ArrayList<GhostData> loaded = NeatStorage.loadGhosts();
        for (int i = 0; i < loaded.size() && ghosts.size() < MAX_GHOSTS; i++) ghosts.add(loaded.get(i));
        if (!ghosts.isEmpty()) Gdx.app.log("NeatTrainer", "loaded ghosts: " + ghosts.size());
    }

    private NeatEvolver newEvolver(int rays, ArrayList<Genome> pop, Genome.InnovationCounter counter) {
        int inputCount = new VisionSensor(rays).inputSize() + 1;
        return counter == null ? new NeatEvolver(inputCount, 5, dynamicPopulation, rng)
                : new NeatEvolver(inputCount, 5, dynamicPopulation, pop, counter, rng);
    }

    public static float shapingScale(int generation) {
        return 0.2f + 2.8f * (float) Math.exp(-generation / 80.0);
    }

    private static float progressiveLevel(int generation) {
        return Math.min(1.0f, 0.3f + generation * 0.005f);
    }

    // ------------------------------------------------------------ 代执行

    private void runGeneration() {
        if (resetting) return;

        final NeatEvolver ev;
        synchronized (evolverLock) { ev = evolver; }
        if (ev == null) return;

        final ArrayList<Genome> pop = ev.population;
        final float[] fitness = new float[pop.size()];
        final float shaping = shapingScale(generation);
        final float level = progressiveLevel(generation);

        final GhostData ghost = pickGhost();
        final Genome champOpponent = pickChampion();
        final Genome histChampion = pickHistoricalChampion();

        final float bonusToApply = realMatchBonus;
        realMatchBonus = 0f;

        final int total = pop.size();
        final CountDownLatch latch = new CountDownLatch(total);
        final CopyOnWriteArrayList<Genome> popView = new CopyOnWriteArrayList<Genome>(pop);

        // 1. 主评估：每个体 vs 规则AI + 冠军 + 幽灵 + 历史冠军
        for (int i = 0; i < total; i++) {
            final int index = i;
            final Genome g = pop.get(i);
            threadPool.execute(() -> {
                if (stopped || paused || resetting) { latch.countDown(); return; }
                float f = 0f;
                int n = 0;

                f += evaluate(g, null, null, shaping, level); n++; simMatches.addAndGet(dynamicSimsPerMatch);
                if (champOpponent != null) { f += evaluate(g, champOpponent, null, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch); }
                if (ghost != null) { f += evaluate(g, null, ghost, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch); }
                if (histChampion != null) { f += evaluate(g, histChampion, null, shaping, 1.0f); n++; simMatches.addAndGet(dynamicSimsPerMatch); }

                f /= n;

                // 策略多样性奖励
                String profile = extractStrategyProfile(g, f);
                synchronized (strategyCounts) {
                    int cnt = strategyCounts.getOrDefault(profile, 0) + 1;
                    strategyCounts.put(profile, cnt);
                    float rarity = 1f - cnt / (float) total;
                    f += rarity * 8f * shaping;
                }

                fitness[index] = f;
                latch.countDown();
            });
        }

        // 2. 同代对战（2轮）
        int sameGenTasks = 2 * (total / 2);
        final CountDownLatch sameGenLatch = new CountDownLatch(sameGenTasks);
        for (int round = 0; round < 2; round++) {
            ArrayList<Integer> idx = new ArrayList<Integer>();
            for (int i = 0; i < total; i++) idx.add(i);
            Collections.shuffle(idx, rng);
            for (int i = 0; i < idx.size() / 2; i++) {
                final int a = idx.get(2 * i), b = idx.get(2 * i + 1);
                final Genome ga = popView.get(a), gb = popView.get(b);
                threadPool.execute(() -> {
                    float fA = evaluate(ga, gb, null, shaping, 1.0f);
                    float fB = evaluate(gb, ga, null, shaping, 1.0f);
                    synchronized (fitness) { fitness[a] += fA * 0.2f; fitness[b] += fB * 0.2f; }
                    simMatches.addAndGet(dynamicSimsPerMatch * 2);
                    sameGenLatch.countDown();
                });
            }
        }

        try { latch.await(); } catch (InterruptedException e) { return; }
        try { sameGenLatch.await(); } catch (InterruptedException e) { return; }
        if (stopped || resetting) return;

        int best = 0;
        for (int i = 1; i < fitness.length; i++) if (fitness[i] > fitness[best]) best = i;

        fitness[best] += bonusToApply;

        synchronized (evolverLock) {
            if (ev != evolver || resetting) return;

            Genome newChamp = pop.get(best).copy();
            championPool.add(0, newChamp);
            while (championPool.size() > CHAMPION_POOL_SIZE) championPool.remove(championPool.size() - 1);
            champion = newChamp;
            bestFitness = Math.max(bestFitness, fitness[best]);
            ev.nextGeneration(fitness);
            generation++;

            // 每5代入库历史冠军
            if (generation % 5 == 0) {
                historicalChampions.add(newChamp.copy());
                while (historicalChampions.size() > HISTORICAL_CHAMPION_SIZE) historicalChampions.remove(0);
            }
        }

        // 每10代清策略统计
        if (generation % 10 == 0) { synchronized (strategyCounts) { strategyCounts.clear(); } }
    }

    // ------------------------------------------------------------ 评估/模拟

    private float evaluate(Genome candidate, Genome vsGenome, GhostData ghost, float shaping, float level) {
        Random r = rngPerThread.get();
        float sum = 0f;
        for (int i = 0; i < dynamicSimsPerMatch; i++) {
            sum += simulate(candidate, vsGenome, ghost, level, r).fitness(shaping);
        }
        return sum / dynamicSimsPerMatch;
    }

    private MatchStats simulate(final Genome candidate, final Genome vsGenome,
                                final GhostData ghost, final float level, final Random rngLocal) {
        final int rays = rayCount;
        GameSystem.EngineFactory engineA = sys -> {
            NeatEngine engine = new NeatEngine(candidate, rays);
            engine.reset();
            return engine;
        };
        GameSystem.EngineFactory engineB = sys -> {
            if (ghost != null) {
                ReplayEngine engine = new ReplayEngine(ghost);
                engine.reset();
                return engine;
            } else if (vsGenome != null) {
                NeatEngine engine = new NeatEngine(vsGenome, rays);
                engine.reset();
                return engine;
            } else {
                return new ComputerEngine(sys, level);
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

    // ------------------------------------------------------------ 早停 / 多样性

    private String extractStrategyProfile(Genome g, float fitness) {
        // 基于适应度特征分类（在评估后传入MatchStats会更准确，这里用fitness粗略估计）
        StringBuilder sb = new StringBuilder();
        // 根据适应度值范围推断战术类型
        if (fitness > 80) sb.append("A");  // 攻击型（高命中/击杀）
        else if (fitness > 50) sb.append("B");  // 平衡型
        else if (fitness > 30) sb.append("D");  // 防御型
        else sb.append("P");  // 被动型

        // 结合基因组复杂度
        int conns = g.conns.size();
        if (conns > 400) sb.append("C"); else if (conns > 200) sb.append("M"); else sb.append("S");
        return sb.toString();
    }

    private void checkConvergence() {
        fitnessHistory.add(bestFitness);
        if (fitnessHistory.size() > 20) fitnessHistory.remove(0);

        if (bestFitness > historicalBestFitness + 1.0f) {
            historicalBestFitness = bestFitness;
            patienceCounter = 0;
        } else {
            patienceCounter++;
        }

        if (patienceCounter >= PATIENCE) {
            converged = true;
            Gdx.app.log("NeatTrainer", "early stop gen " + generation + " best " + bestFitness);
        }

        if (generation % 10 == 0) {
            float div = calculateDiversity();
            Gdx.app.log("NeatTrainer", "gen " + generation + " diversity=" + String.format("%.3f", div) + " threshold=" + (evolver != null ? compatThreshold() : 3.0f));
            if (div < 0.03f) { converged = true; Gdx.app.log("NeatTrainer", "population converged"); }
        }
    }

    private float compatThreshold() {
        NeatEvolver ev = evolver;
        return ev != null ? ev.compatThreshold : 3.0f;
    }

    private float calculateDiversity() {
        NeatEvolver ev = evolver;
        if (ev == null || ev.population.isEmpty()) return 0f;
        ArrayList<Genome> pop = ev.population;
        int sample = Math.min(20, pop.size());
        float total = 0f;
        int count = 0;
        for (int i = 0; i < sample; i++)
            for (int j = i + 1; j < sample; j++) { total += pop.get(i).distance(pop.get(j)); count++; }
        return count > 0 ? total / count : 0f;
    }

    private void verifySave() {
        try {
            NeatStorage.SaveData loaded = NeatStorage.load(rayCount);
            if (loaded == null || loaded.generation != generation)
                Gdx.app.error("NeatTrainer", "save verify FAIL gen " + generation);
        } catch (Throwable t) {
            Gdx.app.error("NeatTrainer", "save verify error", t);
        }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) {} }
}
