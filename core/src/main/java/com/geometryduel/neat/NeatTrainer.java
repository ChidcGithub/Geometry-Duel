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
 * 后台训练线程：无头 GameSystem 快速模拟（候选 vs 规则 AI / 名人堂冠军），
 * 按适应度驱动 NeatEvolver 逐代进化；冠军基因组供玩家对局使用。
 *
 * 线程模型：单后台线程持续跑代，玩家对局进行中由 GameScreen 暂停；
 * 每 10 代写盘一次，shutdown 时最终保存。
 */
public class NeatTrainer {
    public static final int POPULATION = 50;
    private static final int MAX_MATCH_FRAMES = 180 + 7200; // 倒计时 + 2 分钟
    private static final int MAX_GHOSTS = 5;                // 玩家幽灵池上限（FIFO）

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
    /** 玩家行为录像池（UI 线程写、训练线程读，CopyOnWrite 保证安全）。 */
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

    /** 清空存档并从零进化（射线数变更时也必须调用，因输入维度变化）。 */
    public void reset(int newRayCount) {
        rayCount = newRayCount;
        NeatStorage.clear();
        ghosts.clear();
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

    /**
     * 玩家实战结果上报：打赢玩家重奖 +1000（强烈信号：实战胜利远高于一切模拟指标），
     * 输掉 -30；传送→5秒内击杀玩家的连击 +20/次（不衰减）；含技能小奖。
     */
    public void reportRealMatch(MatchStats m) {
        realMatchBonus += (m.aiWon ? 1000f : -30f)
                + Math.min(m.longShotsFired, 10) * 2f
                + Math.min(m.teleportsUsed, 8) * 1f
                + m.teleportKills * 20f;
    }

    /** 录入一局玩家行为录像（幽灵陪练），FIFO 保留最近 MAX_GHOSTS 局并落盘。 */
    public void addGhost(GhostData g) {
        if (g == null || g.frames < 300) return; // 短于 5 秒的录像没有学习价值
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
        ArrayList<GhostData> loaded = NeatStorage.loadGhosts();
        for (int i = 0; i < loaded.size() && ghosts.size() < MAX_GHOSTS; i++) {
            ghosts.add(loaded.get(i));
        }
        if (!ghosts.isEmpty()) Gdx.app.log("NeatTrainer", "loaded ghosts: " + ghosts.size());
    }

    private NeatEvolver newEvolver(int rays, ArrayList<Genome> population,
                                   Genome.InnovationCounter counter) {
        int inputCount = new VisionSensor(rays).inputSize() + 1; // +1 偏置
        return counter == null
                ? new NeatEvolver(inputCount, 5, POPULATION, rng)
                : new NeatEvolver(inputCount, 5, POPULATION, population, counter, rng);
    }

    /**
     * 课程式奖励系数：gen 0 ≈ 3.0（强行 bootstrap 技能使用）→ 指数衰减 → 0.2 保底。
     * 约 80 代后回到 ~1.2，160 代后 ~0.6，技能已成为手段而非目标。
     */
    public static float shapingScale(int generation) {
        return 0.2f + 2.8f * (float) Math.exp(-generation / 80.0);
    }

    private void runGeneration() {
        NeatEvolver ev = evolver;
        ArrayList<Genome> pop = ev.population;
        float[] fitness = new float[pop.size()];
        float shaping = shapingScale(generation);
        for (int i = 0; i < pop.size(); i++) {
            if (stopped || paused || ev != evolver) return; // 代中止：不进化，下轮重跑
            Genome g = pop.get(i);
            float f = 0f;
            int n = 0;
            f += simulate(g, null, null).fitness(shaping); // vs 规则 AI
            n++;
            simMatches++;
            Genome hof = hallOfFame;
            if (hof != null) {
                f += simulate(g, hof, null).fitness(shaping); // vs 上代冠军
                n++;
                simMatches++;
            }
            GhostData ghost = pickGhost();
            if (ghost != null) {
                f += simulate(g, null, ghost).fitness(shaping); // vs 玩家影子
                n++;
                simMatches++;
            }
            f /= n;
            if (g == championSource) f += realMatchBonus;
            fitness[i] = f;
        }

        int best = 0;
        for (int i = 1; i < fitness.length; i++) {
            if (fitness[i] > fitness[best]) best = i;
        }
        // 课程式奖励下跨代适应度不可直接比较：每代滚动晋升当代最优为冠军，
        // 上代冠军转为名人堂陪练（新一代必须能打赢它才能拿高分，形成自我对弈锚点）
        hallOfFame = champion;
        champion = pop.get(best).copy();
        championSource = pop.get(best);
        bestFitness = Math.max(bestFitness, fitness[best]);
        ev.nextGeneration(fitness);
        generation++;
        realMatchBonus *= 0.7f;
    }

    /**
     * 无头快速模拟一局：候选为 myGroup；对手按优先级为 玩家幽灵 > 指定基因组（名人堂） > 规则 AI。
     * 逻辑/渲染已分离，全程纯 Java 运算。
     */
    private MatchStats simulate(final Genome candidate, final Genome vsGenome, final GhostData ghost) {
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
