package com.geometryduel.game.actor;

import com.badlogic.gdx.graphics.Color;
import com.geometryduel.game.GameSystem;
import com.geometryduel.game.engine.PlayerEngine;
import com.geometryduel.game.state.PlayerState;
import com.geometryduel.render.Shapes;

/**
 * 玩家（纯方块）。还原 ServerPlayerActor + ClientPlayerActor：
 * - 判定半径 16，方块 32×32
 * - 速度上限 vx±10 / vy±7，摩擦 0.92，边界反弹 -0.5（活动范围 16..624）
 * - 旋转角每帧 += ((vx²+vy²)*0.04 + 0.1) * 2π/60
 */
public class PlayerActor extends Actor {
    public static final float BODY_SIZE = 32f, HALF_BODY = 16f;
    public static final float MAX_VX = 10f, MAX_VY = 7f, FRICTION = 0.92f;

    public final GameSystem sys;
    public final PlayerEngine engine;
    public final Color fillColor;
    public PlayerState state;

    public float aimAngle;
    public int chargedFrameCount;
    public int teleportChargedFrameCount;
    public int damageRemainingFrameCount;

    public PlayerActor(GameSystem sys, PlayerEngine engine, Color fillColor) {
        super(16f);
        this.sys = sys;
        this.engine = engine;
        this.fillColor = fillColor;
    }

    public void addVelocity(float dx, float dy) {
        vel.x = clamp(vel.x + dx, -MAX_VX, MAX_VX);
        vel.y = clamp(vel.y + dy, -MAX_VY, MAX_VY);
    }

    @Override
    public void act() {
        engine.run(this);
        state.act(this);
    }

    @Override
    public void update() {
        super.update();
        if (pos.x < 16f) { pos.x = 16f; vel.x *= -0.5f; }
        if (pos.x > 624f) { pos.x = 624f; vel.x *= -0.5f; }
        if (pos.y < 16f) { pos.y = 16f; vel.y *= -0.5f; }
        if (pos.y > 624f) { pos.y = 624f; vel.y *= -0.5f; }
        vel.x *= FRICTION;
        vel.y *= FRICTION;
        rotationAngle += ((vel.x * vel.x + vel.y * vel.y) * 0.04f + 0.1f) * 6.2831855f / 60f;
        state.update(this);
    }

    @Override
    public void display(Shapes s) {
        s.stroke(sys.theme().stroke);
        s.strokeWeight(3f);
        s.doFill();
        s.fill(fillColor);
        s.push();
        s.translate(pos.x, pos.y);
        s.push();
        s.rotate(rotationAngle);
        s.rect(-HALF_BODY, -HALF_BODY, BODY_SIZE, BODY_SIZE);
        s.pop();
        state.displayEffect(s, this);
        s.pop();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
