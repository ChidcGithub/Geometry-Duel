package com.geometryduel.game.state

import com.geometryduel.game.GameSystem
import com.geometryduel.game.actor.LongbowArrowHead
import com.geometryduel.game.actor.LongbowArrowShaft
import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.render.GameRenderer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 长弓（致命大招）状态，还原 Server/ClientDrawLongbowPlayerActorState：
 * - 按住 X 蓄力 30 帧；横向输入以 0.010471975 rad/帧（0.6°）转动瞄准角
 * - 蓄满后松开 X：射出 5 节箭杆（间距 24）+ 1 个箭头（前方 120），速度 64，命中即杀
 * - 未满松开：取消不发射
 * - 蓄满瞬间：半径 40 充能环粒子（0.5s）+ 音效；放箭瞬间：800 长激光线粒子（2s）+ 屏幕震动 +10
 * - 特效：半径 50 半圆弓弧、800 瞄准线（蓄满变红色 longbowEffect）、半径 40 充能进度环
 */
class DrawLongbowState(private val sys: GameSystem) : PlayerState() {

    companion object {
        const val UNIT_ANGLE_SPEED = 0.010471975f
        const val CHARGE_REQUIRED = 30
        const val ARROW_SPEED = 64f
    }

    override fun entryState(p: PlayerActor): PlayerState {
        p.chargedFrameCount = 0
        return this
    }

    override fun act(p: PlayerActor) {
        p.aimAngle += p.engine.horizontalMove * UNIT_ANGLE_SPEED
        p.addVelocity(p.engine.horizontalMove * 0.25f, p.engine.verticalMove * 0.25f)
        val charged = hasCompletedLongBowCharge(p)
        if (!p.engine.longShotButtonPressed && charged) {
            fire(p)
            return
        }
        // 蓄满当帧：环状粒子 + 音效（还原 ClientDrawLongbowPlayerActorState.act）
        if (p.chargedFrameCount == CHARGE_REQUIRED) {
            sys.particles.builder()
                .type(3).position(p.pos.x, p.pos.y)
                .polarVelocity(0f, 0f)
                .particleSize(80f)
                .particleColor(sys.theme().longbowEffect)
                .weight(8f).lifespanSecond(0.5f)
                .buildInto()
            sys.playLongShotCharged()
        }
        if (!p.engine.longShotButtonPressed) {
            p.state = moveState.entryState(p)
        }
    }

    private fun fire(p: PlayerActor) {
        sys.playLFire()
        val aim = p.aimAngle
        val cos = cos(aim)
        val sin = sin(aim)
        for (i in 0 until 5) {
            val shaft = LongbowArrowShaft(sys)
            val d = i * 24f
            shaft.pos.x = p.pos.x + cos * d
            shaft.pos.y = p.pos.y + d * sin
            shaft.rotationAngle = aim
            shaft.vel(aim, ARROW_SPEED)
            p.group?.addArrow(shaft)
        }
        val head = LongbowArrowHead(sys)
        head.pos.x = p.pos.x + cos * 120f
        head.pos.y = p.pos.y + sin * 120f
        head.rotationAngle = aim
        head.vel(aim, ARROW_SPEED)
        p.group?.addArrow(head)

        p.chargedFrameCount = 0
        p.state = moveState.entryState(p)
        // 放箭激光线粒子（type 2）+ 屏幕震动
        sys.particles.builder()
            .type(2).position(p.pos.x, p.pos.y)
            .polarVelocity(0f, 0f)
            .rotation(aim)
            .particleColor(sys.theme().longbowLine)
            .lifespanSecond(2f).weight(16f)
            .buildInto()
        sys.screenShakeValue += 10f
    }

    override fun update(p: PlayerActor) {
        p.chargedFrameCount++
    }

    override fun isDrawingLongBow() = true

    override fun hasCompletedLongBowCharge(p: PlayerActor): Boolean =
        p.chargedFrameCount >= CHARGE_REQUIRED

    override fun displayEffect(s: GameRenderer, p: PlayerActor) {
        s.noFill()
        s.stroke(sys.theme().stroke)
        s.strokeWeight(5f)
        s.arc(0f, 0f, 50f, Math.toDegrees(p.aimAngle.toDouble()).toFloat() - 90f, 180f)
        // 瞄准线：蓄满后变为 longbowEffect（红）
        if (hasCompletedLongBowCharge(p)) s.stroke(sys.theme().longbowEffect)
        else s.stroke(sys.theme().longbowStroke)
        s.line(0f, 0f, cos(p.aimAngle) * 800f, sin(p.aimAngle) * 800f)
        // 充能进度环（半径 40，线宽 8，自底部起顺时针）
        s.strokeWeight(8f)
        val progress = min(1f, p.chargedFrameCount / CHARGE_REQUIRED.toFloat())
        s.arc(0f, 0f, 40f, 90f, progress * 360f)
    }
}
