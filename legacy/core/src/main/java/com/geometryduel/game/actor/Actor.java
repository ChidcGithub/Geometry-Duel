package com.geometryduel.game.actor;

import com.geometryduel.game.Body;

/** 还原 pama1234...util.actor.Actor：圆形碰撞（dist < r1+r2）。 */
public abstract class Actor extends Body {
    public final float collisionRadius;
    public ActorGroup group;
    public float rotationAngle;

    protected Actor(float collisionRadius) {
        this.collisionRadius = collisionRadius;
    }

    public boolean isCollided(Actor o) {
        return dist(o) < this.collisionRadius + o.collisionRadius;
    }

    public abstract void act();
}
