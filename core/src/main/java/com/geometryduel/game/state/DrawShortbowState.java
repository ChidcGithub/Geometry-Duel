package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.actor.ShortbowArrow;
import com.geometryduel.render.Shapes;

/**
 * 短弓（普通攻击）状态，还原 Server/ClientDrawShortbowPlayerActorState（对焦已削弱）：
 * - 按住 Z：瞄准线以 2°/帧 缓慢转向敌人（原为瞬时对焦），每 12 帧射一箭（frameCount % 12 == 0）
 * - 箭生成于玩家前方 24 处，初速 24
 * - 移动减速为 0.25 倍
 * - 特效：70 长瞄准线 + 半径 50、张角 45° 的弓弧
 * - 首次按下时立即锁定敌人方向，长按时缓慢转向
 */
public class DrawShortbowState extends PlayerState {
    public static final int FIRE_INTERVAL = 12;
    public static final float ARROW_OFFSET = 24f, ARROW_SPEED = 24f;
    public static final float MOVE_SCALE = 0.25f;
    public static final float AIM_TURN_SPEED = 0.035f; // 对焦速度 2°/帧（180° 转身需 1.5 秒）

    private final GameSystem sys;
    private boolean firstPress = true;  // 首次按下标志

    public DrawShortbowState(GameSystem sys) {
        this.sys = sys;
    }

    @Override
    public PlayerState entryState(PlayerActor p) {
        firstPress = true;  // 进入状态时重置首次按下标志
        return this;
    }

    @Override
    public void act(PlayerActor p) {
        // 首次按下时立即锁定敌人方向
        if (firstPress) {
            p.aimAngle = enemyAngle(p);
            firstPress = false;
        } else {
            // 长按时缓慢追踪：每帧向敌人方向最多转 AIM_TURN_SPEED
            float diff = enemyAngle(p) - p.aimAngle;
            while (diff > 3.1415927f) diff -= 6.2831855f;
            while (diff < -3.1415927f) diff += 6.2831855f;
            if (diff > AIM_TURN_SPEED) diff = AIM_TURN_SPEED;
            else if (diff < -AIM_TURN_SPEED) diff = -AIM_TURN_SPEED;
            p.aimAngle += diff;
        }
        p.addVelocity(p.engine.horizontalMove * MOVE_SCALE, p.engine.verticalMove * MOVE_SCALE);
        if (sys.frameCount % FIRE_INTERVAL == 0) fire(p);
        if (!p.engine.shotButtonPressed) {
            p.state = moveState.entryState(p);
        }
    }

    private void fire(PlayerActor p) {
        sys.playSFire();
        ShortbowArrow a = new ShortbowArrow(sys);
        float f = p.aimAngle;
        a.pos.x = p.pos.x + (float) Math.cos(f) * ARROW_OFFSET;
        a.pos.y = p.pos.y + (float) Math.sin(f) * ARROW_OFFSET;
        a.rotationAngle = f;
        a.vel(f, ARROW_SPEED);
        p.group.addArrow(a);
    }

    @Override
    public void update(PlayerActor p) {
    }

    @Override
    public void displayEffect(Shapes s, PlayerActor p) {
        s.strokeWeight(3f);
        s.line(0, 0, (float) Math.cos(p.aimAngle) * 70f, (float) Math.sin(p.aimAngle) * 70f);
        s.noFill();
        s.arc(0, 0, 50f, (float) Math.toDegrees(p.aimAngle) - 22.5f, 45f);
    }
}
