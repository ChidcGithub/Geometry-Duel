package com.geometryduel.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.geometryduel.DuelController
import com.geometryduel.game.state.PlayGameState
import com.geometryduel.game.state.StartGameState
import com.geometryduel.render.GameRenderer
import kotlinx.coroutines.isActive

/** 每帧驱动的 tick 状态 + 帧回调（withFrameNanos vsync 对齐）。 */
@Composable
fun rememberFrameTick(onFrame: (Float) -> Unit): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    val callback = rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val delta = ((now - last) / 1_000_000_000f)
                last = now
                callback.value(delta)
                tick.longValue++
            }
        }
    }
    return tick
}

/**
 * 对战界面：Canvas 渲染世界 + 触控控件 + HUD；
 * Back/Pause/结算/说明窗/暂停遮罩用 Compose 覆盖层（M3 组件）。
 */
@Composable
fun GameScreen(
    controller: DuelController,
    session: GameSession,
    onExit: () -> Unit,
) {
    // 隐藏系统栏（沉浸对战），离开时恢复
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val c = window?.let { WindowInsetsControllerCompat(it, view) }
        c?.let {
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        controller.battleActiveChecker = { session.isBattleActive() }
        onDispose {
            c?.show(WindowInsetsCompat.Type.systemBars())
            controller.battleActiveChecker = null
        }
    }

    val tick = rememberFrameTick { dt -> session.onFrame(dt) }

    fun exit() {
        session.exitToMenu()
        onExit()
    }

    BackHandler { exit() }

    val focusRequester = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                val down = ev.type == KeyEventType.KeyDown
                val input = session.input
                when (ev.key) {
                    Key.DirectionUp, Key.W -> { input.isUpPressed = down; true }
                    Key.DirectionDown, Key.S -> { input.isDownPressed = down; true }
                    Key.DirectionLeft, Key.A -> { input.isLeftPressed = down; true }
                    Key.DirectionRight, Key.D -> { input.isRightPressed = down; true }
                    Key.Z, Key.J -> { input.isZPressed = down; true }
                    Key.X, Key.K -> { input.isXPressed = down; true }
                    Key.C, Key.L -> { input.isCPressed = down; true }
                    Key.P -> { if (down) session.paused = !session.paused; true }
                    Key.Escape -> { if (down) exit(); true }
                    else -> false
                }
            }
    ) {
        val renderer = remember { GameRenderer() }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(session) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                val wasPressed = change.previousPressed
                                val isPressed = change.pressed
                                when {
                                    !wasPressed && isPressed ->
                                        session.onTouchDown(
                                            change.id, change.position.x, change.position.y,
                                            size.width.toFloat()
                                        )
                                    wasPressed && !isPressed -> session.onTouchUp(change.id)
                                    isPressed && event.type == PointerEventType.Move ->
                                        session.onTouchMove(
                                            change.id, change.position.x, change.position.y
                                        )
                                }
                            }
                        }
                    }
                }
        ) {
            tick.value // 每帧触发重绘
            session.layoutTouchControls(size.width, size.height)
            renderer.bind(this)
            drawGameWorld(controller, session, renderer)
            drawHudAndControls(controller, session, renderer)
        }

        // ---- Compose 覆盖层 ----
        GameOverlay(controller, session, tick.value, onBack = { exit() })
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** 世界渲染：640×640 fit 居中 + 震屏偏移。 */
private fun DrawScope.drawGameWorld(
    controller: DuelController,
    session: GameSession,
    renderer: GameRenderer,
) {
    val system = session.system
    val pal = controller.palette
    // 背景清屏
    drawRect(pal.background)

    val scale = minOf(size.width, size.height) / 640f
    // 屏幕震动：随机偏移，随帧衰减 0.8333（还原 ClientGameSystem.display）
    var ox = 0f
    var oy = 0f
    val shake = system.screenShakeValue
    if (shake > 0f) {
        ox = system.random(-shake, shake)
        oy = system.random(-shake, shake)
        system.screenShakeValue -= 0.8333333f
    }
    withTransform({
        translate((size.width - 640f * scale) / 2f, (size.height - 640f * scale) / 2f)
        scale(scale, scale, Offset.Zero)
        translate(ox, oy)
    }) {
        system.display(renderer)
    }
}

/** HUD 文本（倒计时/Go!!）与触控控件（摇杆 + Z/X/C 按钮）。 */
private fun DrawScope.drawHudAndControls(
    controller: DuelController,
    session: GameSession,
    renderer: GameRenderer,
) {
    val system = session.system
    val pal = controller.palette
    val w = size.width
    val h = size.height
    val unit = h / 640f

    // ---- 倒计时 / Go!! ----
    when (val st = system.currentState) {
        is StartGameState -> {
            val n = st.displayNumber()
            if (n > 0) {
                val scale = session.countdownScale()
                renderer.text(
                    n.toString(), w / 2f, h / 2f,
                    88f * unit * scale, pal.ring
                )
            }
        }
        is PlayGameState -> {
            if (st.properFrameCount < PlayGameState.MESSAGE_DURATION) {
                val inT = Anim.spring(st.properFrameCount / 14f)
                val alpha = 1f - st.properFrameCount / PlayGameState.MESSAGE_DURATION.toFloat()
                renderer.text(
                    "Go!!", w / 2f, h / 2f,
                    88f * unit * 0.72f * inT, pal.text.copy(alpha = alpha)
                )
            }
        }
        else -> {}
    }

    // ---- 摇杆 ----
    renderer.noFill()
    renderer.stroke(pal.stroke, 100)
    renderer.strokeWeight(2f)
    renderer.circle(session.joyBaseX, session.joyBaseY, session.joyR * 2f)
    renderer.doFill()
    renderer.fill(pal.stroke, 140)
    renderer.noStroke()
    renderer.filledCircle(session.joyKnobX, session.joyKnobY, session.joyR)
    renderer.doStroke()

    // ---- Z/X/C 按钮 ----
    drawCircleButton(renderer, pal.stroke, session.zX, session.zY, session.zR, session.input.isZPressed)
    drawCircleButton(renderer, pal.stroke, session.xX, session.xY, session.xR, session.input.isXPressed)
    drawCircleButton(renderer, pal.stroke, session.cX, session.cY, session.cR, session.input.isCPressed)
    renderer.noFill()
    renderer.stroke(pal.text)
    renderer.text("Z", session.zX, session.zY, 30f * unit, pal.text)
    renderer.text("X", session.xX, session.xY, 30f * unit, pal.text)
    renderer.text("C", session.cX, session.cY, 23f * unit, pal.text)

    // C 键外圈：传送标记倒计时环（冷却中显示灰色冷却环）
    val ring = session.teleportRingState()
    if (ring != null) {
        val (progress, marked, urgent) = ring
        renderer.noFill()
        renderer.stroke(
            when {
                marked && urgent -> pal.longbowEffect
                marked -> pal.teleportEffect
                else -> pal.stroke.copy(alpha = 120 / 255f)
            }
        )
        renderer.strokeWeight(6f)
        renderer.arc(session.cX, session.cY, session.cR + 8f, 90f, progress * 360f)
    }
}

private fun DrawScope.drawCircleButton(
    renderer: GameRenderer, stroke: androidx.compose.ui.graphics.Color,
    cx: Float, cy: Float, r: Float, pressed: Boolean,
) {
    renderer.noFill()
    renderer.stroke(stroke, if (pressed) 255 else 140)
    renderer.strokeWeight(2f)
    renderer.circle(cx, cy, r * 2f)
}
