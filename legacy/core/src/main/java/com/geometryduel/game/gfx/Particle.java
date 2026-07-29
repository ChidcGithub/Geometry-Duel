package com.geometryduel.game.gfx;

import com.badlogic.gdx.graphics.Color;
import com.geometryduel.game.Body;
import com.geometryduel.render.Shapes;

/**
 * 粒子，还原 pama1234.gdx.game.duel.util.graphics.Particle：
 * - 速度阻尼 0.98/帧；超龄移除；方块型自转 0.157 rad/帧
 * - type 0 dot：灰度 128+progress*127 的单像素点
 * - type 1 square：strokeWeight 2 的旋转方框，alpha=fade
 * - type 2 line：800 长射线，alpha=fade/2，线宽=weight*fade^4
 * - type 3 ring：扩散圆环，半径系数 ((p-1)^5+1)*2，线宽=weight*fade
 */
public class Particle extends Body {
    public static final int DOT = 0, SQUARE = 1, LINE = 2, RING = 3;
    private static final float FRICTION = 0.98f;

    public final Color displayColor = new Color(0, 0, 0, 1);
    public float displaySize = 10f;
    public int lifespanFrameCount;
    public int particleTypeNumber;
    public int properFrameCount;
    public float rotationAngle;
    public float strokeWeightValue = 1f;
    boolean dead;

    @Override
    public void update() {
        super.update();
        vel.scl(FRICTION);
        properFrameCount++;
        if (properFrameCount > lifespanFrameCount) dead = true;
        if (particleTypeNumber == SQUARE) rotationAngle += 0.15707964f;
    }

    public float getProgressRatio() {
        return Math.min(1f, (float) properFrameCount / lifespanFrameCount);
    }

    public float getFadeRatio() {
        return 1f - getProgressRatio();
    }

    public void display(Shapes s) {
        switch (particleTypeNumber) {
            case DOT: {
                int g = (int) (getProgressRatio() * 127f + 128f);
                s.dot((float) Math.floor(pos.x), (float) Math.floor(pos.y),
                        s.strokeColor.set(g / 255f, g / 255f, g / 255f, 1f));
                break;
            }
            case SQUARE: {
                float a = getFadeRatio();
                if (a <= 0.01f) return;
                s.noFill();
                s.stroke(displayColor, (int) (a * 256f));
                s.strokeWeight(2f);
                s.push();
                s.translate(pos.x, pos.y);
                s.rotate(rotationAngle);
                float h = displaySize;
                s.rect(-h / 2f, -h / 2f, h, h);
                s.pop();
                break;
            }
            case LINE: {
                float a = getFadeRatio() / 2f;
                if (a <= 0.01f) return;
                s.stroke(displayColor, (int) (a * 256f));
                float fade = getFadeRatio();
                s.strokeWeight(strokeWeightValue * fade * fade * fade * fade);
                s.line(pos.x, pos.y,
                        pos.x + (float) Math.cos(rotationAngle) * 800f,
                        pos.y + (float) Math.sin(rotationAngle) * 800f);
                break;
            }
            case RING: {
                float pr = getProgressRatio() - 1f;
                float f = (pr * pr * pr * pr * pr + 1f) * 2f;
                float a = getFadeRatio();
                if (a <= 0.01f) return;
                s.noFill();
                s.stroke(displayColor, (int) (a * 256f));
                s.strokeWeight(strokeWeightValue * getFadeRatio());
                s.circle(pos.x, pos.y, displaySize * (f + 1f) / 2f);
                break;
            }
            default:
                break;
        }
    }

    static int clampAlpha(float a) {
        return Math.max(0, Math.min(255, (int) a));
    }
}
