package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.render.Shapes;

/**
 * 传送状态，还原 Server/ClientDoTeleportPlayerActorState：
 * - 按住 C 进入：记录锚点 des = 当前位置，充电 30 帧（期间可全速移动）
 * - 蓄满后松开 C：瞬移回锚点，屏幕震动 +10
 * - 特效：半径 40 充能环（teleportStroke/Effect 蓝），蓄满后在锚点绘制旋转方框标记
 * 注：原作中传送为调试功能（debug 才显示 C 键），本项目完整复刻并默认开放。
 */
public class DoTeleportState extends PlayerState {
    public static final int CHARGE_REQUIRED = 30;

    private final GameSystem sys;
    private float desX, desY;

    public DoTeleportState(GameSystem sys) {
        this.sys = sys;
    }

    @Override
    public PlayerState entryState(PlayerActor p) {
        p.teleportChargedFrameCount = 0;
        this.desX = p.pos.x;
        this.desY = p.pos.y;
        return this;
    }

    @Override
    public void act(PlayerActor p) {
        p.addVelocity(p.engine.horizontalMove, p.engine.verticalMove); // 全速移动
        boolean charged = hasCompletedTeleportCharge(p);
        if (!p.engine.teleportButtonPressed && charged) {
            p.pos.set(desX, desY);
            sys.screenShakeValue += 10f;
            p.state = moveState.entryState(p);
            return;
        }
        // 蓄满当帧：环状粒子
        if (p.teleportChargedFrameCount == CHARGE_REQUIRED) {
            sys.particles.builder()
                    .type(3).position(p.pos.x, p.pos.y)
                    .polarVelocity(0, 0)
                    .particleSize(80f)
                    .particleColor(sys.theme().teleportEffect)
                    .weight(8f).lifespanSecond(0.5f)
                    .buildInto();
        }
        if (!p.engine.teleportButtonPressed) {
            p.state = moveState.entryState(p);
        }
    }

    @Override
    public void update(PlayerActor p) {
        p.teleportChargedFrameCount++;
    }

    @Override
    public boolean isDrawingLongBow() {
        return true;
    }

    @Override
    public boolean hasCompletedTeleportCharge(PlayerActor p) {
        return p.teleportChargedFrameCount >= CHARGE_REQUIRED;
    }

    @Override
    public void displayEffect(Shapes s, PlayerActor p) {
        s.noFill();
        boolean charged = hasCompletedTeleportCharge(p);
        if (charged) s.stroke(sys.theme().teleportEffect);
        else s.stroke(sys.theme().teleportStroke);
        // 充能进度环（半径 40，线宽 8）
        s.strokeWeight(8f);
        float progress = Math.min(1f, (float) p.teleportChargedFrameCount / CHARGE_REQUIRED);
        s.arc(0, 0, 40f, 90f, progress * 360f);
        if (charged) {
            // 锚点旋转方框标记（还原原作 translate(pos-des) 的镜像行为）
            s.stroke(sys.theme().teleportEffect);
            s.push();
            s.translate(p.pos.x - desX, p.pos.y - desY);
            s.rotate((sys.frameCount / 60f) % 3.1415927f);
            float i = CHARGE_REQUIRED - p.teleportChargedFrameCount;
            float f = -i / 2f;
            s.rect(f, f, i, i);
            s.pop();
        }
    }
}
