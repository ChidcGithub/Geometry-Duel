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
    public static final int TELEPORT_MARK_DURATION = 900; // 传送标记有效期 15 秒
    public static final int TELEPORT_HIT_PENALTY = 240;   // 标记期间受击一次 -4 秒

    public final GameSystem sys;
    public final PlayerEngine engine;
    public final Color fillColor;
    public PlayerState state;

    public float aimAngle;
    public int chargedFrameCount;
    public int damageRemainingFrameCount;

    /** 传送标记：按 C 记录锚点，15 秒内再按 C 瞬移回锚点；受击每次 -4 秒。 */
    public boolean teleportMarked;
    public float teleportAnchorX, teleportAnchorY;
    public int teleportMarkRemaining;
    public int teleportRecallCount;
    private boolean prevTeleportPressed;

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
        handleTeleport();
        state.act(this);
    }

    /** 传送（边沿触发）：未标记→记录锚点；已标记→瞬移回锚点。 */
    private void handleTeleport() {
        boolean pressed = engine.teleportButtonPressed;
        if (pressed && !prevTeleportPressed) {
            if (!teleportMarked) {
                teleportMarked = true;
                teleportAnchorX = pos.x;
                teleportAnchorY = pos.y;
                teleportMarkRemaining = TELEPORT_MARK_DURATION;
                sys.particles.builder()
                        .type(3).position(pos.x, pos.y)
                        .polarVelocity(0, 0)
                        .particleSize(60f)
                        .particleColor(sys.theme().teleportStroke)
                        .weight(8f).lifespanSecond(0.4f)
                        .buildInto();
            } else {
                pos.set(teleportAnchorX, teleportAnchorY);
                teleportMarked = false;
                teleportRecallCount++;
                sys.screenShakeValue += 10f;
                sys.particles.builder()
                        .type(3).position(pos.x, pos.y)
                        .polarVelocity(0, 0)
                        .particleSize(80f)
                        .particleColor(sys.theme().teleportEffect)
                        .weight(8f).lifespanSecond(0.5f)
                        .buildInto();
            }
        }
        prevTeleportPressed = pressed;
    }

    /** 受击时：传送标记倒计时 -4 秒（可被打断）。 */
    public void onDamaged() {
        if (teleportMarked) {
            teleportMarkRemaining -= TELEPORT_HIT_PENALTY;
            if (teleportMarkRemaining <= 0) teleportMarked = false;
        }
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
        // 传送标记倒计时：15 秒到期自动失效
        if (teleportMarked && --teleportMarkRemaining <= 0) {
            teleportMarked = false;
        }
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
        if (teleportMarked) {
            s.noFill();
            // 锚点：旋转方框标记
            s.stroke(sys.theme().teleportEffect);
            s.strokeWeight(3f);
            s.push();
            s.translate(teleportAnchorX, teleportAnchorY);
            s.rotate((sys.frameCount / 60f) % 3.1415927f);
            s.rect(-12f, -12f, 24f, 24f);
            s.pop();
            // 自身周围：倒计时环（剩余 <3 秒变红）
            float progress = teleportMarkRemaining / (float) TELEPORT_MARK_DURATION;
            s.stroke(teleportMarkRemaining < 180 ? sys.theme().longbowEffect : sys.theme().teleportStroke);
            s.strokeWeight(4f);
            s.arc(pos.x, pos.y, 24f, 90f, progress * 360f);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
