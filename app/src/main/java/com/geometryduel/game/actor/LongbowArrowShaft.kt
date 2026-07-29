package com.geometryduel.game.actor

import com.geometryduel.game.GameSystem
import com.geometryduel.render.GameRenderer

/**
 * 长弓箭杆（致命大招组成部分，共 5 节）。
 * 判定半径 16，半长 16，速度 64 恒定。拖尾：每帧 5 个方块粒子。
 */
open class LongbowArrowShaft(protected val sys: GameSystem) : ArrowActor(16f, 16f) {

    override fun isLethal() = true

    override fun act() {
        val a = directionAngle + Math.PI.toFloat() + sys.random(-1.5707964f, 1.5707964f)
        for (i in 0 until 5) {
            sys.particles.builder()
                .type(1).position(pos.x, pos.y)
                .polarVelocity(a, sys.random(2f, 4f))
                .particleSize(4f)
                .particleColor(sys.theme().longbowArrow)
                .lifespanSecond(1f)
                .buildInto()
        }
    }

    override fun display(s: GameRenderer) {
        s.strokeWeight(5f)
        s.stroke(0f, 0f, 0f, 1f) // 原作硬编码 stroke(0)/fill(0) 黑色
        s.doFill()
        s.fill(0f, 0f, 0f, 1f)
        s.push()
        s.translate(pos.x, pos.y)
        s.rotate(rotationAngle)
        s.line(-halfLength, 0f, halfLength, 0f)
        s.pop()
    }
}
