package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.game.actor.LongbowArrowHead;
import com.geometryduel.game.actor.LongbowArrowShaft;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.render.Shapes;

/**
 * 长弓（致命大招）状态，还原 Server/ClientDrawLongbowPlayerActorState：
 * - 按住 X 蓄力 30 帧；横向输入以 0.010471975 rad/帧（0.6°）转动瞄准角
 * - 蓄满后松开 X：射出 5 节箭杆（间距 24）+ 1 个箭头（前方 120），速度 64，命中即杀
 * - 未满松开：取消不发射
 * - 蓄满瞬间：半径 40 充能环粒子（0.5s）+ 音效；放箭瞬间：800 长激光线粒子（2s）+ 屏幕震动 +10
 * - 特效：半径 50 半圆弓弧、800 瞄准线（蓄满变红色 longbowEffect）、半径 40 充能进度环
 */
public class DrawLongbowState extends PlayerState {
    public static final float UNIT_ANGLE_SPEED = 0.010471975f;
    public static final int CHARGE_REQUIRED = 30;
    public static final float ARROW_SPEED = 64f;

    private final GameSystem sys;

    public DrawLongbowState(GameSystem sys) {
        this.sys = sys;
    }

    @Override
    public PlayerState entryState(PlayerActor p) {
        p.chargedFrameCount = 0;
        return this;
    }

    @Override
    public void act(PlayerActor p) {
        p.aimAngle += p.engine.horizontalMove * UNIT_ANGLE_SPEED;
        p.addVelocity(p.engine.horizontalMove * 0.25f, p.engine.verticalMove * 0.25f);
        boolean charged = hasCompletedLongBowCharge(p);
        if (!p.engine.longShotButtonPressed && charged) {
            fire(p);
            return;
        }
        // 蓄满当帧：环状粒子 + 音效（还原 ClientDrawLongbowPlayerActorState.act）
        if (p.chargedFrameCount == CHARGE_REQUIRED) {
            sys.particles.builder()
                    .type(3).position(p.pos.x, p.pos.y)
                    .polarVelocity(0, 0)
                    .particleSize(80f)
                    .particleColor(sys.theme().longbowEffect)
                    .weight(8f).lifespanSecond(0.5f)
                    .buildInto();
            sys.playLongShotCharged();
        }
        if (!p.engine.longShotButtonPressed) {
            p.state = moveState.entryState(p);
        }
    }

    private void fire(PlayerActor p) {
        sys.playLFire();
        float aim = p.aimAngle;
        float cos = (float) Math.cos(aim), sin = (float) Math.sin(aim);
        for (int i = 0; i < 5; i++) {
            LongbowArrowShaft shaft = new LongbowArrowShaft(sys);
            float d = i * 24f;
            shaft.pos.x = p.pos.x + cos * d;
            shaft.pos.y = p.pos.y + d * sin;
            shaft.rotationAngle = aim;
            shaft.vel(aim, ARROW_SPEED);
            p.group.addArrow(shaft);
        }
        LongbowArrowHead head = new LongbowArrowHead(sys);
        head.pos.x = p.pos.x + cos * 120f;
        head.pos.y = p.pos.y + sin * 120f;
        head.rotationAngle = aim;
        head.vel(aim, ARROW_SPEED);
        p.group.addArrow(head);

        p.chargedFrameCount = 0;
        p.state = moveState.entryState(p);
        // 放箭激光线粒子（type 2）+ 屏幕震动
        sys.particles.builder()
                .type(2).position(p.pos.x, p.pos.y)
                .polarVelocity(0, 0)
                .rotation(aim)
                .particleColor(sys.theme().longbowLine)
                .lifespanSecond(2f).weight(16f)
                .buildInto();
        sys.screenShakeValue += 10f;
    }

    @Override
    public void update(PlayerActor p) {
        p.chargedFrameCount++;
    }

    @Override
    public boolean isDrawingLongBow() {
        return true;
    }

    @Override
    public boolean hasCompletedLongBowCharge(PlayerActor p) {
        return p.chargedFrameCount >= CHARGE_REQUIRED;
    }

    @Override
    public void displayEffect(Shapes s, PlayerActor p) {
        s.noFill();
        s.stroke(sys.theme().stroke);
        s.strokeWeight(5f);
        s.arc(0, 0, 50f, (float) Math.toDegrees(p.aimAngle) - 90f, 180f);
        // 瞄准线：蓄满后变为 longbowEffect（红）
        if (hasCompletedLongBowCharge(p)) s.stroke(sys.theme().longbowEffect);
        else s.stroke(sys.theme().longbowStroke);
        s.line(0, 0, (float) Math.cos(p.aimAngle) * 800f, (float) Math.sin(p.aimAngle) * 800f);
        // 充能进度环（半径 40，线宽 8，自底部起顺时针）
        s.strokeWeight(8f);
        float progress = Math.min(1f, (float) p.chargedFrameCount / CHARGE_REQUIRED);
        s.arc(0, 0, 40f, 90f, progress * 360f);
    }
}
