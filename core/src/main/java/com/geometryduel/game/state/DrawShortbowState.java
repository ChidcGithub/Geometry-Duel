package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.actor.ShortbowArrow;
import com.geometryduel.render.Shapes;

/**
 * 短弓（普通攻击）状态，还原 Server/ClientDrawShortbowPlayerActorState：
 * - 按住 Z：自动瞄准敌人，每 12 帧射一箭（frameCount % 12 == 0）
 * - 箭生成于玩家前方 24 处，初速 24
 * - 移动减速为 0.25 倍
 * - 特效：70 长瞄准线 + 半径 50、张角 45° 的弓弧
 */
public class DrawShortbowState extends PlayerState {
    public static final int FIRE_INTERVAL = 12;
    public static final float ARROW_OFFSET = 24f, ARROW_SPEED = 24f;
    public static final float MOVE_SCALE = 0.25f;

    private final GameSystem sys;

    public DrawShortbowState(GameSystem sys) {
        this.sys = sys;
    }

    @Override
    public void act(PlayerActor p) {
        p.aimAngle = enemyAngle(p); // 自动瞄准
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
    public PlayerState entryState(PlayerActor p) {
        return this;
    }

    @Override
    public void displayEffect(Shapes s, PlayerActor p) {
        s.strokeWeight(3f);
        s.line(0, 0, (float) Math.cos(p.aimAngle) * 70f, (float) Math.sin(p.aimAngle) * 70f);
        s.noFill();
        s.arc(0, 0, 50f, (float) Math.toDegrees(p.aimAngle) - 22.5f, 45f);
    }
}
