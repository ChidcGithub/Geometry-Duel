package com.geometryduel.game.actor;

import com.geometryduel.game.GameSystem;
import com.geometryduel.render.Shapes;

/**
 * 长弓箭杆（致命大招组成部分，共 5 节）。
 * 判定半径 16，半长 16，速度 64 恒定。拖尾：每帧 5 个方块粒子。
 */
public class LongbowArrowShaft extends ArrowActor {
    protected final GameSystem sys;

    public LongbowArrowShaft(GameSystem sys) {
        super(16f, 16f);
        this.sys = sys;
    }

    @Override
    public boolean isLethal() {
        return true;
    }

    @Override
    public void act() {
        float a = directionAngle + (float) Math.PI + sys.random(-1.5707964f, 1.5707964f);
        for (int i = 0; i < 5; i++) {
            sys.particles.builder()
                    .type(1).position(pos.x, pos.y)
                    .polarVelocity(a, sys.random(2f, 4f))
                    .particleSize(4f)
                    .particleColor(sys.theme().longbowArrow)
                    .lifespanSecond(1f)
                    .buildInto();
        }
    }

    @Override
    public void display(Shapes s) {
        s.strokeWeight(5f);
        s.stroke(0f, 0f, 0f, 1f); // 原作硬编码 stroke(0)/fill(0) 黑色
        s.doFill();
        s.fill(0f, 0f, 0f, 1f);
        s.push();
        s.translate(pos.x, pos.y);
        s.rotate(rotationAngle);
        s.line(-halfLength, 0, halfLength, 0);
        s.pop();
    }
}
