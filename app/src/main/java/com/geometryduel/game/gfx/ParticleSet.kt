package com.geometryduel.game.gfx

import androidx.compose.ui.graphics.Color
import com.geometryduel.render.GameRenderer
import kotlin.math.cos
import kotlin.math.sin

/** 粒子集合 + 建造者（还原 ParticleSet/ParticleBuilder）。 */
class ParticleSet {
    val list = ArrayList<Particle>()
    private val builder = Builder()
    /** 无头训练模拟时置 false：所有粒子生成在 buildInto 处统一拦截。 */
    var enabled = true

    fun builder(): Builder = builder.initialize()

    /** 击杀/碎箭方块粒子（还原 addSquareParticles）。 */
    fun addSquareParticles(x: Float, y: Float, count: Int, size: Float,
                           speedLo: Float, speedHi: Float, lifeSec: Float, color: Color) {
        val b = builder().type(Particle.SQUARE).position(x, y)
            .particleSize(size).particleColor(color).lifespanSecond(lifeSec)
        val rng = java.util.Random()
        for (i in 0 until count) {
            val a = (rng.nextFloat() * 6.2831855f)
            val sp = speedLo + rng.nextFloat() * (speedHi - speedLo)
            b.polarVelocity(a, sp).buildInto()
        }
    }

    fun update() {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.update()
            if (p.dead) it.remove()
        }
    }

    fun display(s: GameRenderer) {
        for (p in list) p.display(s)
    }

    fun clear() = list.clear()

    inner class Builder {
        private val template = Particle()

        fun initialize(): Builder {
            template.particleTypeNumber = 0
            template.pos.set(0f, 0f)
            template.vel.set(0f, 0f)
            template.directionAngle = 0f
            template.speed = 0f
            template.rotationAngle = 0f
            template.displayColor = Color.Black
            template.strokeWeightValue = 1f
            template.displaySize = 10f
            template.lifespanFrameCount = 60
            return this
        }

        fun type(t: Int): Builder { template.particleTypeNumber = t; return this }

        fun position(x: Float, y: Float): Builder { template.pos.set(x, y); return this }

        fun polarVelocity(angle: Float, speed: Float): Builder {
            template.directionAngle = angle
            template.speed = speed
            template.vel.set(cos(angle) * speed, sin(angle) * speed)
            return this
        }

        fun rotation(r: Float): Builder { template.rotationAngle = r; return this }

        fun particleColor(c: Color): Builder { template.displayColor = c; return this }

        fun weight(w: Float): Builder { template.strokeWeightValue = w; return this }

        fun particleSize(sz: Float): Builder { template.displaySize = sz; return this }

        fun lifespan(frames: Int): Builder { template.lifespanFrameCount = frames; return this }

        fun lifespanSecond(sec: Float): Builder = lifespan((sec * 60f).toInt())

        fun buildInto(): Particle? {
            if (!enabled) return null
            val p = Particle()
            p.copyFrom(template)
            list.add(p)
            return p
        }
    }
}
