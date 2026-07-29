package com.geometryduel.game

import com.geometryduel.DuelController
import com.geometryduel.game.actor.ActorGroup
import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.game.engine.ComputerEngine
import com.geometryduel.game.engine.HumanEngine
import com.geometryduel.game.engine.PlayerEngine
import com.geometryduel.game.gfx.GameBackground
import com.geometryduel.game.gfx.ParticleSet
import com.geometryduel.game.state.DamagedState
import com.geometryduel.game.state.DrawLongbowState
import com.geometryduel.game.state.DrawShortbowState
import com.geometryduel.game.state.GameSystemState
import com.geometryduel.game.state.MoveState
import com.geometryduel.game.state.PlayGameState
import com.geometryduel.game.state.StartGameState
import com.geometryduel.render.GameRenderer
import com.geometryduel.theme.GamePalette
import kotlin.random.Random

/**
 * 对局系统（还原 ClientGameSystem + ServerGameSystem）：
 * - 世界 640×640、逻辑 60 帧/秒
 * - 双方出生点：我方 (320,540)、敌方 (320,100)
 * - 震屏：display 时随机偏移，每帧衰减 0.8333（约 60 帧从 50 归零）
 */
class GameSystem(
    val app: DuelController,
    val demoPlay: Boolean,
    var showInstruction: Boolean,
    val level: Float,
    humanInput: InputData?,
    engineAFactory: EngineFactory? = null,
    engineBFactory: EngineFactory? = null,
    val muted: Boolean = false,
    private val rng: java.util.Random? = null,
) {

    companion object {
        const val WORLD = 640f
    }

    /** 引擎工厂：解决引擎依赖 GameSystem 引用、而 GameSystem 构造期就要建引擎的循环依赖。 */
    fun interface EngineFactory {
        fun create(sys: GameSystem): PlayerEngine
    }

    val myGroup = ActorGroup(0)
    val otherGroup = ActorGroup(1)
    var currentState: GameSystemState
        private set
    var stateIndex = 0
    var frameCount = 0
    var screenShakeValue = 0f

    val particles = ParticleSet()
    val background: GameBackground?
    val damagedState: DamagedState

    /** 对战模式下由界面层写入（X 键/按钮），ResultGameState 读取。 */
    @Volatile
    var restartPressed = false
    private var restartRequested = false

    init {
        particles.enabled = !muted // 无头模拟禁用全部粒子特效

        myGroup.enemyGroup = otherGroup
        otherGroup.enemyGroup = myGroup

        // 状态机接线（还原 prepareServer / ClientGameSystem 构造；传送已移出状态机）
        val move = MoveState()
        val shortbow = DrawShortbowState(this)
        val longbow = DrawLongbowState(this)
        damagedState = DamagedState()
        move.drawShortbowState = shortbow
        move.drawLongbowState = longbow
        shortbow.moveState = move
        longbow.moveState = move
        damagedState.moveState = move

        val engineA = engineAFactory?.create(this)
            ?: (if (demoPlay) ComputerEngine(this, level)
            else HumanEngine(humanInput ?: InputData(), true))
        val playerA = PlayerActor(this, engineA, theme().playerA)
        playerA.pos.set(320f, 540f)
        playerA.state = move
        myGroup.addPlayer(playerA)

        val engineB = engineBFactory?.create(this) ?: ComputerEngine(this, level)
        val playerB = PlayerActor(this, engineB, theme().playerB)
        playerB.pos.set(320f, 100f)
        playerB.state = move
        otherGroup.addPlayer(playerB)

        // 初始瞄准角指向敌方
        playerA.aimAngle = Math.atan2(
            (playerB.pos.y - playerA.pos.y).toDouble(), (playerB.pos.x - playerA.pos.x).toDouble()
        ).toFloat()
        playerB.aimAngle = Math.atan2(
            (playerA.pos.y - playerB.pos.y).toDouble(), (playerA.pos.x - playerB.pos.x).toDouble()
        ).toFloat()

        background = if (muted) null else GameBackground(theme().backgroundLine, 0.1f)
        // addPlayer 是延迟入列：先 flush 保证 players 立即可判，
        // 否则无头模拟（直接进入 PlayGameState）首帧 checkStateTransition
        // 会因 players 为空误判一方全灭，每场对战 1 帧即结束
        myGroup.flushPending()
        otherGroup.flushPending()
        // 无头模拟跳过 180 帧开局倒计时（期间引擎不 act、玩家静止，物理等价）
        currentState = if (muted) PlayGameState(this) else StartGameState(this)
    }

    fun theme(): GamePalette = app.palette

    fun currentState(s: GameSystemState) {
        this.currentState = s
    }

    fun update() {
        if (!muted) background?.update() // 无头模拟跳过背景动画
        currentState.update()
        frameCount++
    }

    fun display(s: GameRenderer) {
        background?.display(s) // 无头模拟不分配背景
        currentState.display(s)
    }

    fun requestRestart() {
        restartRequested = true
    }

    fun consumeRestart(): Boolean {
        val r = restartRequested
        restartRequested = false
        return r
    }

    fun random(hi: Float): Float =
        rng?.nextFloat()?.times(hi) ?: Random.nextFloat() * hi

    fun random(lo: Float, hi: Float): Float =
        rng?.let { lo + it.nextFloat() * (hi - lo) } ?: (lo + Random.nextFloat() * (hi - lo))

    // ---- 音效（无头模拟静音）----
    fun playSFire() {
        if (!muted) app.playSFire()
    }

    fun playLFire() {
        if (!muted) app.playLFire()
    }

    fun playLongShotCharged() {
        if (!muted) app.playLongShotCharged()
    }

    fun playHurt() {
        if (!muted) app.playHurt()
    }
}
