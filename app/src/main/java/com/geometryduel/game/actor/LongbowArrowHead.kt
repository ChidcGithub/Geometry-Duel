package com.geometryduel.game.actor

import com.geometryduel.game.GameSystem
import com.geometryduel.render.GameRenderer

/**
 * 长弓箭头（致命大招的尖端，位于第 6 节位置）。
 * 三角箭头：半长/半宽 24，填充 longbowArrow 色。
 */
class LongbowArrowHead(sys: GameSystem) : LongbowArrowShaft(sys) {

    companion object {
        const val HEAD_HALF_LEN = 24f
        const val HEAD_HALF_W = 24f
    }

    override fun display(s: GameRenderer) {
        s.strokeWeight(5f)
        s.stroke(sys.theme().stroke)
        s.doFill()
        s.fill(sys.theme().longbowArrow)
        s.push()
        s.translate(pos.x, pos.y)
        s.rotate(rotationAngle)
        s.line(-halfLength, 0f, 0f, 0f)
        s.quad(0f, 0f, -HEAD_HALF_LEN, -HEAD_HALF_W, HEAD_HALF_LEN, 0f, -HEAD_HALF_LEN, HEAD_HALF_W)
        s.pop()
    }
}
