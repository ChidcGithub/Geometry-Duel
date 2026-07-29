package com.geometryduel.game.state

import com.geometryduel.game.GameSystem
import com.geometryduel.game.actor.ActorGroup
import com.geometryduel.game.actor.ArrowActor
import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.render.GameRenderer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 对战状态（还原 ClientPlayGameState / ServerPlayGameState）：
 * - 每帧：两组 update+act → 碰撞检测 → 粒子更新
 * - 箭-箭相撞：双方碎裂（10 个方块粒子：size 7、速度 1..5、寿命 1s）
 * - 箭-人相撞：致命箭 → 击杀（50 个方块粒子：size 16、速度 2..10、寿命 4s，震动=50）；
 *   非致命箭 → 击退（沿箭→人方向 ±π/4 随机，冲量 20，进入 45 帧受击，震动+10）
 * - 前 60 帧显示"冲啊！！"（渐隐）
 */
class PlayGameState(system: GameSystem) : GameSystemState(system) {

    companion object {
        const val MESSAGE_DURATION = 60
    }

    init {
        system.stateIndex = 2
    }

    override fun updateSystem() {
        system.myGroup.update()
        system.myGroup.act()
        system.otherGroup.update()
        system.otherGroup.act()
        checkCollision()
        system.particles.update()
    }

    override fun checkStateTransition() {
        if (system.myGroup.players.isEmpty()) {
            system.currentState(ResultGameState(system, system.otherGroup.id, false))
        } else if (system.otherGroup.players.isEmpty()) {
            system.currentState(ResultGameState(system, system.myGroup.id, true))
        }
    }

    private fun checkCollision() {
        val my = system.myGroup
        val other = system.otherGroup

        // 箭 vs 箭（跨组）
        for (a in my.arrows) {
            for (b in other.arrows) {
                if (a.isCollided(b)) {
                    breakArrow(a, my)
                    breakArrow(b, other)
                }
            }
        }

        // 我方箭 vs 敌方玩家
        val enemy = other.firstPlayer()
        if (enemy != null) {
            for (a in my.arrows) {
                if (a.isCollided(enemy)) {
                    if (a.isLethal()) killPlayer(enemy)
                    else thrustPlayerActor(a, enemy)
                    breakArrow(a, my)
                }
            }
        }

        // 敌方箭 vs 我方玩家
        val me = my.firstPlayer()
        if (me != null) {
            for (a in other.arrows) {
                if (a.isCollided(me)) {
                    if (a.isLethal()) killPlayer(me)
                    else thrustPlayerActor(a, me)
                    breakArrow(a, other)
                }
            }
        }
    }

    fun killPlayer(p: PlayerActor) {
        // 无头训练模拟不需要特效：跳过 50 个粒子分配与震屏
        if (!system.muted) {
            system.particles.addSquareParticles(p.pos.x, p.pos.y, 50, 16f, 2f, 10f, 4f,
                system.theme().squareParticles)
            system.screenShakeValue = 50f
        }
        p.group?.removePlayer(p)
        system.playHurt()
    }

    fun breakArrow(a: ArrowActor, group: ActorGroup) {
        if (!system.muted) {
            system.particles.addSquareParticles(a.pos.x, a.pos.y, 10, 7f, 1f, 5f, 1f,
                system.theme().squareParticles)
        }
        group.breakArrow(a)
    }

    fun thrustPlayerActor(a: ArrowActor, p: PlayerActor) {
        val ang = atan2(p.pos.y - a.pos.y, p.pos.x - a.pos.x) + system.random(-0.7853982f, 0.7853982f)
        p.vel.x += cos(ang) * 20f
        p.vel.y += sin(ang) * 20f
        p.state = system.damagedState.entryState(p)
        p.group?.damageCount = (p.group?.damageCount ?: 0) + 1
        p.onDamaged() // 传送标记倒计时 -4 秒
        system.screenShakeValue += 10f
        system.playHurt()
    }

    override fun display(s: GameRenderer) {
        system.myGroup.displayPlayers(s)
        system.otherGroup.displayPlayers(s)
        system.myGroup.displayArrows(s)
        system.otherGroup.displayArrows(s)
        system.particles.display(s)
    }

    override fun getScore(groupId: Int): Float =
        -(if (groupId == 0) system.myGroup.damageCount else system.otherGroup.damageCount).toFloat()
}
