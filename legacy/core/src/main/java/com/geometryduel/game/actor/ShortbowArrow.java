package com.geometryduel.game.actor;

import com.geometryduel.game.GameSystem;
import com.geometryduel.render.Shapes;

/**
 * 短弓箭（普通攻击弹）。
 * 判定半径 8，半长 20，初速 24 → 以 0.1/帧 收敛到终端速度 8（原作 ServerShortbowArrow）。
 * 拖尾：每帧 50% 概率喷 3 个方块粒子（ClientShortbowArrow.act）。
 */
public class ShortbowArrow extends ArrowActor {
    public static final float TERMINAL_SPEED = 8f;
    public static final float HEAD_LEN = 8f, HEAD_W = 4f, FEATHER_LEN = 8f, FEATHER_W = 4f;

    private final GameSystem sys;

    public ShortbowArrow(GameSystem sys) {
        super(8f, 20f);
        this.sys = sys;
    }

    @Override
    public boolean isLethal() {
        return false;
    }

    @Override
    public void update() {
        // 原作每帧按 directionAngle*speed 重建速度（不受外力）
        vel.set(speed * cos(directionAngle), speed * sin(directionAngle));
        super.update();
        speed += (TERMINAL_SPEED - speed) * 0.1f;
    }

    @Override
    public void act() {
        if (sys.random(1f) >= 0.5f) return;
        float a = directionAngle + (float) Math.PI + sys.random(-0.7853982f, 0.7853982f);
        for (int i = 0; i < 3; i++) {
            sys.particles.builder()
                    .type(1).position(pos.x, pos.y)
                    .polarVelocity(a, sys.random(0.5f, 2f))
                    .particleSize(2f)
                    .particleColor(sys.theme().shortbowArrow)
                    .lifespanSecond(0.5f)
                    .buildInto();
        }
    }

    @Override
    public void display(Shapes s) {
        s.strokeWeight(3f);
        s.stroke(sys.theme().stroke);
        s.doFill();
        s.fill(0f, 0f, 0f, 1f); // 原作硬编码 fill(0) 黑色
        s.push();
        s.translate(pos.x, pos.y);
        s.rotate(rotationAngle);
        float h = halfLength;
        s.line(-h, 0, h, 0); // 箭杆
        s.quad(h, 0, h - HEAD_LEN, -HEAD_W, h + HEAD_LEN, 0, h - HEAD_LEN, HEAD_W); // 箭头
        // 箭羽：3 对斜线
        s.line(-h, 0, -h - FEATHER_LEN, -FEATHER_W);
        s.line(-h, 0, -h - FEATHER_LEN, FEATHER_W);
        s.line(-h + 4f, 0, -h - FEATHER_LEN + 4f, -FEATHER_W);
        s.line(-h + 4f, 0, -h - FEATHER_LEN + 4f, FEATHER_W);
        s.line(-h + 8f, 0, -h - FEATHER_LEN + 8f, -FEATHER_W);
        s.line(-h + 8f, 0, -h - FEATHER_LEN + 8f, FEATHER_W);
        s.pop();
    }
}
