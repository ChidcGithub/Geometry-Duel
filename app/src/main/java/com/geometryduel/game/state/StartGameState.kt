package com.geometryduel.game.state

import com.geometryduel.game.GameSystem
import com.geometryduel.render.GameRenderer

/**
 * 开局倒计时状态（还原 ClientStartGameState）：
 * - 3→2→1，每个数字 60 帧，共 180 帧
 * - 中央半径 100 的进度圆环（线宽 3，ring 色）
 * - 结束时在 (320,320) 生成半径 200 的扩散环粒子（1s）并进入对战
 */
class StartGameState(system: GameSystem) : GameSystemState(system) {

    companion object {
        const val FRAMES_PER_NUMBER = 60
        const val RING_RADIUS = 100f
    }

    init {
        system.stateIndex = 1
    }

    override fun updateSystem() {
        system.myGroup.update()
        system.otherGroup.update()
    }

    override fun checkStateTransition() {
        if (properFrameCount >= FRAMES_PER_NUMBER * 3) {
            system.particles.builder()
                .type(3).position(320f, 320f)
                .polarVelocity(0f, 0f)
                .particleSize(200f)
                .particleColor(system.theme().ring)
                .weight(5f).lifespanSecond(1f)
                .buildInto()
            system.currentState(PlayGameState(system))
        }
    }

    /** 当前应显示的数字（3→2→1，结束后为 0）。 */
    fun displayNumber(): Int = 3 - properFrameCount / FRAMES_PER_NUMBER

    /** 当前秒内的环进度 [0,1]。 */
    fun ringProgress(): Float = (properFrameCount % FRAMES_PER_NUMBER) / FRAMES_PER_NUMBER.toFloat()

    override fun display(s: GameRenderer) {
        system.myGroup.displayPlayers(s)
        system.otherGroup.displayPlayers(s)
        s.push()
        s.translate(320f, 320f)
        s.noFill()
        s.stroke(system.theme().ring)
        s.strokeWeight(3f)
        s.arc(0f, 0f, RING_RADIUS, 90f, ringProgress() * 360f)
        s.pop()
    }
}
