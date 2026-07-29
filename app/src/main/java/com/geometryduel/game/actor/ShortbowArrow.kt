package com.geometryduel.game.actor

import com.geometryduel.game.GameSystem
import com.geometryduel.render.GameRenderer

/**
 * 短弓箭（普通攻击弹）。
 * 判定半径 8，半长 20，初速 24 → 以 0.1/帧 收敛到终端速度 8（原作 ServerShortbowArrow）。
 * 拖尾：每帧 50% 概率喷 3 个方块粒子（ClientShortbowArrow.act）。
 */
class ShortbowArrow(private val sys: GameSystem) : ArrowActor(8f, 20f) {

    companion object {
        const val TERMINAL_SPEED = 8f
        const val HEAD_LEN = 8f
        const val HEAD_W = 4f
        const val FEATHER_LEN = 8f
        const val FEATHER_W = 4f
    }

    override fun isLethal() = false

    override fun update() {
        // 原作每帧按 directionAngle*speed 重建速度（不受外力）
        vel.set(speed * cos(directionAngle), speed * sin(directionAngle))
        super.update()
        speed += (TERMINAL_SPEED - speed) * 0.1f
    }

    override fun act() {
        if (sys.random(1f) >= 0.5f) return
        val a = directionAngle + Math.PI.toFloat() + sys.random(-0.7853982f, 0.7853982f)
        for (i in 0 until 3) {
            sys.particles.builder()
                .type(1).position(pos.x, pos.y)
                .polarVelocity(a, sys.random(0.5f, 2f))
                .particleSize(2f)
                .particleColor(sys.theme().shortbowArrow)
                .lifespanSecond(0.5f)
                .buildInto()
        }
    }

    override fun display(s: GameRenderer) {
        s.strokeWeight(3f)
        s.stroke(sys.theme().stroke)
        s.doFill()
        s.fill(0f, 0f, 0f, 1f) // 原作硬编码 fill(0) 黑色
        s.push()
        s.translate(pos.x, pos.y)
        s.rotate(rotationAngle)
        val h = halfLength
        s.line(-h, 0f, h, 0f) // 箭杆
        s.quad(h, 0f, h - HEAD_LEN, -HEAD_W, h + HEAD_LEN, 0f, h - HEAD_LEN, HEAD_W) // 箭头
        // 箭羽：3 对斜线
        s.line(-h, 0f, -h - FEATHER_LEN, -FEATHER_W)
        s.line(-h, 0f, -h - FEATHER_LEN, FEATHER_W)
        s.line(-h + 4f, 0f, -h - FEATHER_LEN + 4f, -FEATHER_W)
        s.line(-h + 4f, 0f, -h - FEATHER_LEN + 4f, FEATHER_W)
        s.line(-h + 8f, 0f, -h - FEATHER_LEN + 8f, -FEATHER_W)
        s.line(-h + 8f, 0f, -h - FEATHER_LEN + 8f, FEATHER_W)
        s.pop()
    }
}
