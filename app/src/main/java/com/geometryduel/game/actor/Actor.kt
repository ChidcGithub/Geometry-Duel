package com.geometryduel.game.actor

import com.geometryduel.game.Body

/** 还原 pama1234...util.actor.Actor：圆形碰撞（dist < r1+r2）。 */
abstract class Actor(val collisionRadius: Float) : Body() {
    var group: ActorGroup? = null
    var rotationAngle = 0f

    fun isCollided(o: Actor): Boolean {
        return dist(o) < this.collisionRadius + o.collisionRadius
    }

    abstract fun act()
}
