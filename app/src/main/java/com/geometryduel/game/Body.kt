package com.geometryduel.game

import com.geometryduel.render.GameRenderer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 二维向量（替代 libGDX Vector2）。 */
class Vec2 {
    var x = 0f
    var y = 0f

    fun set(x: Float, y: Float): Vec2 {
        this.x = x
        this.y = y
        return this
    }

    fun set(o: Vec2): Vec2 = set(o.x, o.y)

    fun add(o: Vec2): Vec2 {
        x += o.x
        y += o.y
        return this
    }

    fun scl(s: Float): Vec2 {
        x *= s
        y *= s
        return this
    }
}

/** 还原 pama1234.app.game.server.duel.util.Body。 */
abstract class Body {
    val pos = Vec2()
    val vel = Vec2()
    var directionAngle = 0f
    var speed = 0f

    open fun update() {
        pos.add(vel)
    }

    fun vel(angle: Float, speed: Float) {
        this.directionAngle = angle
        this.speed = speed
        this.vel.set(speed * cos(angle), speed * sin(angle))
    }

    fun dist(o: Body): Float {
        val dx = o.pos.x - pos.x
        val dy = o.pos.y - pos.y
        return sqrt(dx * dx + dy * dy)
    }

    fun distPow2(o: Body): Float {
        val dx = o.pos.x - pos.x
        val dy = o.pos.y - pos.y
        return dx * dx + dy * dy
    }

    fun angle(o: Body): Float {
        return kotlin.math.atan2(o.pos.y - pos.y, o.pos.x - pos.x)
    }

    abstract fun display(s: GameRenderer)

    companion object {
        fun cos(a: Float) = kotlin.math.cos(a)
        fun sin(a: Float) = kotlin.math.sin(a)
    }
}
