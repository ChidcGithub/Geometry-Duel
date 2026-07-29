package com.geometryduel.game.gfx

import androidx.compose.ui.graphics.Color
import com.geometryduel.game.Body
import com.geometryduel.render.GameRenderer
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 粒子，还原 pama1234.gdx.game.duel.util.graphics.Particle：
 * - 速度阻尼 0.98/帧；超龄移除；方块型自转 0.157 rad/帧
 * - type 0 dot：灰度 128+progress*127 的单像素点
 * - type 1 square：strokeWeight 2 的旋转方框，alpha=fade
 * - type 2 line：800 长射线，alpha=fade/2，线宽=weight*fade^4
 * - type 3 ring：扩散圆环，半径系数 ((p-1)^5+1)*2，线宽=weight*fade
 */
class Particle : Body() {

    companion object {
        const val DOT = 0
        const val SQUARE = 1
        const val LINE = 2
        const val RING = 3
        private const val FRICTION = 0.98f
    }

    var displayColor = Color.Black
    var displaySize = 10f
    var lifespanFrameCount = 60
    var particleTypeNumber = 0
    var properFrameCount = 0
    var rotationAngle = 0f
    var strokeWeightValue = 1f
    var dead = false

    override fun update() {
        super.update()
        vel.scl(FRICTION)
        properFrameCount++
        if (properFrameCount > lifespanFrameCount) dead = true
        if (particleTypeNumber == SQUARE) rotationAngle += 0.15707964f
    }

    fun getProgressRatio(): Float =
        minOf(1f, properFrameCount / lifespanFrameCount.toFloat())

    fun getFadeRatio(): Float = 1f - getProgressRatio()

    override fun display(s: GameRenderer) {
        when (particleTypeNumber) {
            DOT -> {
                val g = (getProgressRatio() * 127f + 128f).toInt()
                s.dot(floor(pos.x), floor(pos.y), Color(g / 255f, g / 255f, g / 255f, 1f))
            }
            SQUARE -> {
                val a = getFadeRatio()
                if (a <= 0.01f) return
                s.noFill()
                s.stroke(displayColor, (a * 256f).toInt())
                s.strokeWeight(2f)
                s.push()
                s.translate(pos.x, pos.y)
                s.rotate(rotationAngle)
                val h = displaySize
                s.rect(-h / 2f, -h / 2f, h, h)
                s.pop()
            }
            LINE -> {
                val a = getFadeRatio() / 2f
                if (a <= 0.01f) return
                s.stroke(displayColor, (a * 256f).toInt())
                val fade = getFadeRatio()
                s.strokeWeight(strokeWeightValue * fade * fade * fade * fade)
                s.line(pos.x, pos.y,
                    pos.x + cos(rotationAngle) * 800f,
                    pos.y + sin(rotationAngle) * 800f)
            }
            RING -> {
                val pr = getProgressRatio() - 1f
                val f = (pr * pr * pr * pr * pr + 1f) * 2f
                val a = getFadeRatio()
                if (a <= 0.01f) return
                s.noFill()
                s.stroke(displayColor, (a * 256f).toInt())
                s.strokeWeight(strokeWeightValue * getFadeRatio())
                s.circle(pos.x, pos.y, displaySize * (f + 1f) / 2f)
            }
        }
    }

    fun copyFrom(o: Particle) {
        particleTypeNumber = o.particleTypeNumber
        pos.set(o.pos)
        vel.set(o.vel)
        directionAngle = o.directionAngle
        speed = o.speed
        rotationAngle = o.rotationAngle
        displayColor = o.displayColor
        strokeWeightValue = o.strokeWeightValue
        displaySize = o.displaySize
        lifespanFrameCount = o.lifespanFrameCount
        properFrameCount = 0
    }
}
