package com.geometryduel.game;

import com.badlogic.gdx.math.Vector2;

/** 还原 pama1234.app.game.server.duel.util.Body。 */
public abstract class Body {
    public final Vector2 pos = new Vector2();
    public final Vector2 vel = new Vector2();
    public float directionAngle;
    public float speed;

    public void update() {
        pos.add(vel);
    }

    public void vel(float angle, float speed) {
        this.directionAngle = angle;
        this.speed = speed;
        this.vel.set(speed * cos(angle), speed * sin(angle));
    }

    public float dist(Body o) {
        return Vector2.dst(pos.x, pos.y, o.pos.x, o.pos.y);
    }

    public float distPow2(Body o) {
        float dx = o.pos.x - pos.x, dy = o.pos.y - pos.y;
        return dx * dx + dy * dy;
    }

    public float angle(Body o) {
        return (float) Math.atan2(o.pos.y - pos.y, o.pos.x - pos.x);
    }

    public abstract void display(com.geometryduel.render.Shapes s);

    public static float cos(float a) {
        return (float) Math.cos(a);
    }

    public static float sin(float a) {
        return (float) Math.sin(a);
    }
}
