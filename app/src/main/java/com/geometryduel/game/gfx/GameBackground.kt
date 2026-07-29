package com.geometryduel.game.gfx

import androidx.compose.ui.graphics.Color
import com.geometryduel.render.GameRenderer
import java.util.Random

/**
 * 动态网格背景，还原 GameBackground/BackgroundLine：
 * 10 条横线 + 10 条竖线，初始位置随机 [0,640)，
 * 每帧 velocity += random(-0.1, 0.1)，position += velocity，越界反弹。
 */
class GameBackground(private val lineColor: Color, private val maxAccel: Float) {

    companion object {
        private const val MAX_POS = 640f
    }

    private val random = Random()
    private val lines = ArrayList<Line>()

    init {
        repeat(10) { lines.add(Line(true)) }
        repeat(10) { lines.add(Line(false)) }
    }

    fun update() {
        for (l in lines) l.update(random(-maxAccel, maxAccel))
    }

    fun display(s: GameRenderer) {
        s.stroke(lineColor)
        s.strokeWeight(1f)
        for (l in lines) l.display(s)
    }

    private fun random(lo: Float, hi: Float) = lo + random.nextFloat() * (hi - lo)

    private inner class Line(val horizontal: Boolean) {
        var position = random.nextFloat() * MAX_POS
        var velocity = 0f

        fun update(accel: Float) {
            position += velocity
            velocity += accel
            if (position < 0 || position > MAX_POS) velocity = -velocity
        }

        fun display(s: GameRenderer) {
            if (horizontal) s.line(0f, position, MAX_POS, position)
            else s.line(position, 0f, position, MAX_POS)
        }
    }
}
