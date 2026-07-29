package com.geometryduel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import com.geometryduel.DuelController
import com.geometryduel.game.GameSystem
import com.geometryduel.game.InputData
import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.game.state.PlayGameState
import com.geometryduel.game.state.ResultGameState
import com.geometryduel.game.state.StartGameState
import com.geometryduel.neat.Genome
import com.geometryduel.neat.GhostRecorder
import com.geometryduel.neat.MatchStats
import com.geometryduel.neat.MatchTracker
import com.geometryduel.neat.NeatEngine
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 对局会话（替代 libGDX GameScreen 的逻辑部分）：
 * - 固定 60fps 逻辑步进（累加器，单帧最多 3 步）
 * - 流程：演示(AI 对战+说明窗) → 按 Z 开战 → 结算 → 按 X 回演示
 * - 教学模式：初始难度 0.02，每进实战一次 +0.2，超过 1.0 即完成
 */
class GameSession(
    val controller: DuelController,
    val tutorial: Boolean = false,
) {
    companion object {
        const val STEP = 1f / 60f
        const val LEVEL_STEP = 0.2f
    }

    val input = InputData()
    var system: GameSystem
        private set

    var paused by mutableStateOf(false)
    private var accumulator = 0f
    private var matchReported = false
    /** 敌方为 NEAT 冠军时逐帧统计其技能使用（供实战上报）。 */
    private var aiTracker: MatchTracker? = null
    /** 玩家行为录制（非演示局），结算时作为幽灵陪练上报训练器。 */
    private var ghostRecorder: GhostRecorder? = null

    /** 教学模式难度（0.02 → 1.0 递增）。 */
    var tutorialLevel by mutableFloatStateOf(0.02f)
        private set

    // ---- HUD 动画状态 ----
    var instrT = 0f
        private set
    private var lastCountdown = -1
    private var countdownT = 0f

    // ---- 触控几何与指针跟踪（屏幕坐标，y 向下）----
    private var joyPointer: PointerId? = null
    private var zPointer: PointerId? = null
    private var xPointer: PointerId? = null
    private var cPointer: PointerId? = null
    var joyBaseX = 0f; var joyBaseY = 0f
    var joyKnobX = 0f; var joyKnobY = 0f
    var joyR = 0f
    var zX = 0f; var zY = 0f; var zR = 0f
    var xX = 0f; var xY = 0f; var xR = 0f
    var cX = 0f; var cY = 0f; var cR = 0f

    init {
        system = createSystem(true, true)
    }

    /** 玩家对战进行中（非演示局）：供应用层判断息屏恢复后是否保持训练暂停。 */
    fun isBattleActive(): Boolean = !system.demoPlay

    private fun currentLevel(): Float = if (tutorial) tutorialLevel else 1.0f

    /** 开新局钩子（教学模式用于难度递增）。 */
    private fun onNewGame() {
        if (tutorial) {
            tutorialLevel += LEVEL_STEP
            if (tutorialLevel > 1.0f) {
                controller.tutorialDone = true
                controller.saveConfig()
            }
        }
    }

    private fun neatFactory(genome: Genome): GameSystem.EngineFactory =
        GameSystem.EngineFactory {
            NeatEngine(genome, controller.visionRays).apply {
                setSkipFrames(controller.aiSpeed + 1)
            }
        }

    private fun createSystem(demo: Boolean, instruction: Boolean): GameSystem {
        var engineA: GameSystem.EngineFactory? = null
        var engineB: GameSystem.EngineFactory? = null
        if (demo) {
            // 演示：冠军（若有）vs 规则 AI
            val champ = controller.trainer.currentChampion()
            if (champ != null) engineA = neatFactory(champ)
        } else {
            // 玩家对战：按 style 选择对手风格；对局期间暂停后台训练
            controller.trainer.setPaused(true)
            if (controller.opponentStyle >= 0) {
                val styleGenome = controller.trainer.styleChampion(controller.opponentStyle)
                if (styleGenome != null) engineB = neatFactory(styleGenome)
            }
        }
        val sys = GameSystem(controller, demo, instruction, currentLevel(), input,
            engineA, engineB, false, null)
        aiTracker = if (!demo && engineB != null) MatchTracker(sys.otherGroup) else null
        ghostRecorder = if (!demo) GhostRecorder() else null
        return sys
    }

    fun newGame(demo: Boolean, instruction: Boolean) {
        if (!demo) onNewGame()
        matchReported = false
        system = createSystem(demo, instruction)
        input.isZPressed = false
        input.isXPressed = false
        input.isCPressed = false
        accumulator = 0f
    }

    /** 对局结束：向训练器上报实战表现并恢复后台训练。 */
    private fun reportMatchResult() {
        val rs = system.currentState as ResultGameState
        val ms = MatchStats()
        ms.aiWon = !rs.playerWon
        ms.frames = system.frameCount
        ms.hitsDealt = system.myGroup.damageCount    // 人类受击 = AI 命中
        ms.hitsTaken = system.otherGroup.damageCount // AI 受击
        aiTracker?.fill(ms)                          // AI 技能使用统计
        controller.trainer.reportRealMatch(ms)
        // 玩家行为录像入库：成为后续训练的幽灵陪练
        ghostRecorder?.let { controller.trainer.addGhost(it.build()) }
        controller.trainer.setPaused(false)
    }

    /** 离开对战界面：恢复后台训练。 */
    fun exitToMenu() {
        controller.trainer.setPaused(false)
    }

    // ------------------------------------------------------------ 帧推进

    /** 每个渲染帧调用：推进 HUD 动画与固定步长逻辑。 */
    fun onFrame(delta: Float) {
        // 说明窗淡入淡出（不管暂停与否都推进，与原作一致）
        val showInstr = system.demoPlay && system.showInstruction
        instrT = Anim.clamp01(instrT + if (showInstr) delta * 4f else -delta * 4f)
        // 倒计时数字动画时钟
        val st = system.currentState
        if (st is StartGameState) {
            val n = st.displayNumber()
            if (n != lastCountdown) { lastCountdown = n; countdownT = 0f }
            countdownT += delta
        }
        if (!paused) {
            accumulator += min(delta, 0.1f)
            var steps = 0
            while (accumulator >= STEP && steps < 3) {
                step()
                accumulator -= STEP
                steps++
            }
            if (steps == 3) accumulator = 0f
        }
    }

    private fun step() {
        if (system.demoPlay && input.isZPressed) {
            newGame(false, false)
            return
        }
        system.restartPressed = input.isXPressed
        system.update()
        aiTracker?.update()
        // 逐帧录制玩家操作（仅对战状态，与 act 调用时机对齐）
        val st = system.currentState
        val recorder = ghostRecorder
        if (recorder != null && st is PlayGameState) {
            val human = system.myGroup.firstPlayer()
            if (human != null) recorder.frame(human.engine)
        }
        if (!system.demoPlay && !matchReported && st is ResultGameState) {
            matchReported = true
            reportMatchResult()
        }
        if (system.consumeRestart()) {
            newGame(true, true)
        }
    }

    /** 倒计时数字的弹簧缩放（1=就位）。 */
    fun countdownScale(): Float = Anim.spring(countdownT / 0.35f)

    // ------------------------------------------------------------ 触控

    /** 按屏幕尺寸布局触控控件（y 向下）。 */
    fun layoutTouchControls(w: Float, h: Float) {
        val m = min(w, h)
        joyR = m * 0.14f
        joyBaseX = joyR * 1.5f
        joyBaseY = h - joyR * 1.5f
        if (joyPointer == null) {
            joyKnobX = joyBaseX
            joyKnobY = joyBaseY
        }
        zR = m * 0.085f
        zX = w - zR * 1.4f
        zY = h - zR * 1.6f
        xR = zR * 0.8f
        xX = w - zR * 3.6f
        xY = h - zR * 1.3f
        cR = zR * 0.6f
        cX = w - zR * 5.2f
        cY = h - zR * 1.1f
    }

    fun onTouchDown(id: PointerId, x: Float, y: Float, screenW: Float) {
        if (dist2(x, y, zX, zY) <= zR * zR) { zPointer = id; input.isZPressed = true; return }
        if (dist2(x, y, xX, xY) <= xR * xR) { xPointer = id; input.isXPressed = true; return }
        if (dist2(x, y, cX, cY) <= cR * cR) { cPointer = id; input.isCPressed = true; return }

        // 演示中轻触其他区域：显示/隐藏说明窗（还原 Game.mousePressed）
        if (system.demoPlay) {
            system.showInstruction = !system.showInstruction
            return
        }
        // 左半屏：摇杆
        if (x < screenW / 2f) {
            joyPointer = id
            joyKnobX = joyBaseX
            joyKnobY = joyBaseY
            updateJoystick(x, y)
        }
    }

    fun onTouchMove(id: PointerId, x: Float, y: Float) {
        if (id == joyPointer) updateJoystick(x, y)
    }

    fun onTouchUp(id: PointerId) {
        when (id) {
            joyPointer -> { joyPointer = null; input.clearTouch() }
            zPointer -> { zPointer = null; input.isZPressed = false }
            xPointer -> { xPointer = null; input.isXPressed = false }
            cPointer -> { cPointer = null; input.isCPressed = false }
        }
    }

    fun isPressed(id: PointerId): Boolean =
        id == joyPointer || id == zPointer || id == xPointer || id == cPointer

    private fun updateJoystick(x: Float, y: Float) {
        val dx = x - joyBaseX
        val dy = y - joyBaseY
        val mag = sqrt(dx * dx + dy * dy)
        val cl = min(mag, joyR)
        if (mag > 0.0001f) {
            joyKnobX = joyBaseX + dx / mag * cl
            joyKnobY = joyBaseY + dy / mag * cl
        }
        // 还原 targetTouchMoved：方向归一（屏幕与世界同为 y 向下，直接传递）
        input.targetTouchMoved(dx, dy, cl)
    }

    private fun dist2(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    /** C 键外圈进度：传送标记 / 冷却（0=不显示）。 */
    fun teleportRingState(): Triple<Float, Boolean, Boolean>? {
        val human: PlayerActor = system.myGroup.firstPlayer() ?: return null
        return if (human.teleportMarked) {
            Triple(human.teleportMarkRemaining / PlayerActor.TELEPORT_MARK_DURATION.toFloat(),
                true, human.teleportMarkRemaining < 180)
        } else if (human.teleportCooldown > 0) {
            Triple(human.teleportCooldown / PlayerActor.TELEPORT_COOLDOWN.toFloat(),
                false, false)
        } else null
    }
}
