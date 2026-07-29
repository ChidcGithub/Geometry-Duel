package com.geometryduel.game.gfx;

import com.badlogic.gdx.graphics.Color;
import com.geometryduel.render.Shapes;

import java.util.ArrayList;
import java.util.Random;

/**
 * 动态网格背景，还原 GameBackground/BackgroundLine：
 * 10 条横线 + 10 条竖线，初始位置随机 [0,640)，
 * 每帧 velocity += random(-0.1, 0.1)，position += velocity，越界反弹。
 */
public class GameBackground {
    private static final float MAX_POS = 640f;

    private final Color lineColor;
    private final float maxAccel;
    private final Random random = new Random();
    private final ArrayList<Line> lines = new ArrayList<Line>();

    public GameBackground(Color lineColor, float maxAccel) {
        this.lineColor = lineColor;
        this.maxAccel = maxAccel;
        for (int i = 0; i < 10; i++) lines.add(new Line(true));
        for (int i = 0; i < 10; i++) lines.add(new Line(false));
    }

    public void update() {
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).update(random(-maxAccel, maxAccel));
        }
    }

    public void display(Shapes s) {
        s.stroke(lineColor);
        s.strokeWeight(1f);
        for (int i = 0; i < lines.size(); i++) lines.get(i).display(s);
    }

    private float random(float lo, float hi) {
        return lo + random.nextFloat() * (hi - lo);
    }

    private class Line {
        final boolean horizontal;
        float position;
        float velocity;

        Line(boolean horizontal) {
            this.horizontal = horizontal;
            this.position = random.nextFloat() * MAX_POS;
        }

        void update(float accel) {
            position += velocity;
            velocity += accel;
            if (position < 0 || position > MAX_POS) velocity = -velocity;
        }

        void display(Shapes s) {
            if (horizontal) s.line(0, position, MAX_POS, position);
            else s.line(position, 0, position, MAX_POS);
        }
    }
}
