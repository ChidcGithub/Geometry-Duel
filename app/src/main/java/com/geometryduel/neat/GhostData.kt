package com.geometryduel.neat

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 玩家行为录像（幽灵）：逐帧记录人类引擎输出——移动量量化为 ±127 的 byte，
 * 三个动作按钮打包为位掩码（bit0 短弓 / bit1 长弓 / bit2 传送）。
 * 训练时由 ReplayEngine 回放，让候选个体与「玩家的影子」对战，从而学习玩家习惯。
 */
class GhostData {

    companion object {
        const val MAX_FRAMES = 14400 // 4 分钟上限（内存/存档体积保护）

        fun quantize(v0: Float): Byte {
            var v = v0
            if (v > 1f) v = 1f
            if (v < -1f) v = -1f
            return (v * 127f).roundToInt().toByte()
        }
    }

    var frames = 0
    var moveX = ByteArray(0)
    var moveY = ByteArray(0)
    var buttons = ByteArray(0)

    fun moveXAt(f: Int): Float = if (f < frames) moveX[f] / 127f else 0f

    fun moveYAt(f: Int): Float = if (f < frames) moveY[f] / 127f else 0f

    fun shotAt(f: Int): Boolean = f < frames && (buttons[f].toInt() and 1) != 0

    fun longShotAt(f: Int): Boolean = f < frames && (buttons[f].toInt() and 2) != 0

    fun teleportAt(f: Int): Boolean = f < frames && (buttons[f].toInt() and 4) != 0

    fun calculateQuality(): Float {
        if (frames < 300) return 0f
        var activeFrames = 0
        var shotFrames = 0
        var moveFrames = 0
        var teleportFrames = 0
        for (i in 0 until frames) {
            if (abs(moveX[i].toInt()) > 10 || abs(moveY[i].toInt()) > 10) moveFrames++
            if (shotAt(i) || longShotAt(i)) shotFrames++
            if (teleportAt(i)) teleportFrames++
            if (moveFrames > 0 || shotFrames > 0 || teleportFrames > 0) activeFrames++
        }
        val active = activeFrames / frames.toFloat()
        val shot = shotFrames / frames.toFloat()
        val move = moveFrames / frames.toFloat()
        val tele = teleportFrames / frames.toFloat()
        return active * 0.4f + shot * 0.25f + move * 0.2f + tele * 0.15f
    }
}
