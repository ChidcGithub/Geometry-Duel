package com.geometryduel.game;

import com.badlogic.gdx.math.MathUtils;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.ThemeData;
import com.geometryduel.game.actor.ActorGroup;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.engine.ComputerEngine;
import com.geometryduel.game.engine.HumanEngine;
import com.geometryduel.game.engine.PlayerEngine;
import com.geometryduel.game.gfx.GameBackground;
import com.geometryduel.game.gfx.ParticleSet;
import com.geometryduel.game.state.DamagedState;
import com.geometryduel.game.state.DrawLongbowState;
import com.geometryduel.game.state.DrawShortbowState;
import com.geometryduel.game.state.GameSystemState;
import com.geometryduel.game.state.MoveState;
import com.geometryduel.game.state.StartGameState;
import com.geometryduel.render.Shapes;

/**
 * 对局系统（还原 ClientGameSystem + ServerGameSystem）：
 * - 世界 640×640、逻辑 60 帧/秒
 * - 双方出生点：我方 (320,540)、敌方 (320,100)
 * - 震屏：display 时随机偏移，每帧衰减 0.8333（约 60 帧从 50 归零）
 */
public class GameSystem {
    public static final float WORLD = 640f;

    public final GeometryDuelGame app;
    public final boolean demoPlay;
    public boolean showInstruction;
    public final float level;

    public final ActorGroup myGroup = new ActorGroup(0);
    public final ActorGroup otherGroup = new ActorGroup(1);
    public GameSystemState currentState;
    public int stateIndex;
    public int frameCount;
    public float screenShakeValue;

    public final ParticleSet particles = new ParticleSet();
    public final GameBackground background;
    public final DamagedState damagedState;

    /** 对战模式下由界面层写入（X 键/按钮），ResultGameState 读取。 */
    public boolean restartPressed;
    private boolean restartRequested;

    /** 无头模拟：静音 + 独立随机源。 */
    public final boolean muted;
    private final java.util.Random rng;

    /** 引擎工厂：解决引擎依赖 GameSystem 引用、而 GameSystem 构造期就要建引擎的循环依赖。 */
    public interface EngineFactory {
        PlayerEngine create(GameSystem sys);
    }

    public GameSystem(GeometryDuelGame app, boolean demoPlay, boolean showInstruction,
                      float level, InputData humanInput) {
        this(app, demoPlay, showInstruction, level, humanInput, null, null, false, null);
    }

    public GameSystem(GeometryDuelGame app, boolean demoPlay, boolean showInstruction,
                      float level, InputData humanInput,
                      EngineFactory engineAFactory, EngineFactory engineBFactory,
                      boolean muted, java.util.Random rng) {
        this.app = app;
        this.demoPlay = demoPlay;
        this.showInstruction = showInstruction;
        this.level = level;
        this.muted = muted;
        this.rng = rng;

        myGroup.enemyGroup = otherGroup;
        otherGroup.enemyGroup = myGroup;

        // 状态机接线（还原 prepareServer / ClientGameSystem 构造；传送已移出状态机）
        MoveState move = new MoveState();
        DrawShortbowState shortbow = new DrawShortbowState(this);
        DrawLongbowState longbow = new DrawLongbowState(this);
        damagedState = new DamagedState();
        move.drawShortbowState = shortbow;
        move.drawLongbowState = longbow;
        shortbow.moveState = move;
        longbow.moveState = move;
        damagedState.moveState = move;

        PlayerEngine engineA = engineAFactory != null ? engineAFactory.create(this)
                : (demoPlay ? new ComputerEngine(this, level)
                : new HumanEngine(humanInput, app.isAndroid));
        PlayerActor playerA = new PlayerActor(this, engineA, theme().player_a);
        playerA.pos.set(320f, 540f);
        playerA.state = move;
        myGroup.addPlayer(playerA);

        PlayerEngine engineB = engineBFactory != null ? engineBFactory.create(this)
                : new ComputerEngine(this, level);
        PlayerActor playerB = new PlayerActor(this, engineB, theme().player_b);
        playerB.pos.set(320f, 100f);
        playerB.state = move;
        otherGroup.addPlayer(playerB);

        // 初始瞄准角指向敌方
        playerA.aimAngle = (float) Math.atan2(playerB.pos.y - playerA.pos.y, playerB.pos.x - playerA.pos.x);
        playerB.aimAngle = (float) Math.atan2(playerA.pos.y - playerB.pos.y, playerA.pos.x - playerB.pos.x);

        background = new GameBackground(theme().backgroundLine, 0.1f);
        currentState(new StartGameState(this));
    }

    public ThemeData theme() {
        return app.theme;
    }

    public void currentState(GameSystemState s) {
        this.currentState = s;
    }

    public void update() {
        background.update();
        currentState.update();
        frameCount++;
    }

    public void display(Shapes s) {
        background.display(s);
        currentState.display(s);
    }

    public void requestRestart() {
        restartRequested = true;
    }

    public boolean consumeRestart() {
        boolean r = restartRequested;
        restartRequested = false;
        return r;
    }

    public float random(float hi) {
        return rng != null ? rng.nextFloat() * hi : MathUtils.random(hi);
    }

    public float random(float lo, float hi) {
        return rng != null ? lo + rng.nextFloat() * (hi - lo) : MathUtils.random(lo, hi);
    }

    // ---- 音效（MusicAsset 还原；lFireHurt 原作仅加载未播放，这里接到受击/击杀）----
    public void playSFire() {
        if (!muted && app.sFire != null) app.sFire.play(app.volume * 0.3f);
    }

    public void playLFire() {
        if (!muted && app.lFire != null) app.lFire.play(app.volume);
    }

    public void playLongShotCharged() {
        if (!muted && app.longShotCharged != null) app.longShotCharged.play(app.volume);
    }

    public void playHurt() {
        if (!muted && app.lFireHurt != null) app.lFireHurt.play(app.volume);
    }
}
