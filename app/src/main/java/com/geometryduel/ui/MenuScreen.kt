package com.geometryduel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geometryduel.DuelController
import com.geometryduel.HardwareInfo
import com.geometryduel.game.gfx.GameBackground
import com.geometryduel.render.GameRenderer
import com.geometryduel.theme.DuelTheme
import kotlinx.coroutines.delay

/**
 * 主菜单（M3 Expressive）：
 * - Hero 大标题：弹簧缩放入场
 * - AI 训练状态 chips（Gen / 胜率 / 代速）
 * - 三个大圆角按钮 stagger 上浮入场
 * - 底部：硬件信息 + 版本号
 * - 动态网格背景
 */
@Composable
fun MenuScreen(
    controller: DuelController,
    onStart: () -> Unit,
    onTutorial: () -> Unit,
    onSettings: () -> Unit,
) {
    val pal = controller.palette
    val background = remember(pal.backgroundLine) { GameBackground(pal.backgroundLine, 0.1f) }
    val renderer = remember { GameRenderer() }
    var clock by remember { mutableFloatStateOf(0f) }
    rememberFrameTick { dt -> background.update(); clock += dt }

    // AI chips 定时刷新（训练器数据非 Compose 状态）
    var chipsVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(500); chipsVersion++ }
    }

    Box(Modifier.fillMaxSize()) {
        // 动态网格背景（640 世界 fit 居中）
        Canvas(Modifier.fillMaxSize()) {
            val scale = minOf(size.width, size.height) / 640f
            renderer.bind(this)
            withTransform({
                translate((size.width - 640f * scale) / 2f, (size.height - 640f * scale) / 2f)
                scale(scale, scale, Offset.Zero)
            }) {
                background.display(renderer)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 横屏高度有限：内容超出时可滚动，保证 Settings 按钮可达
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ---- Hero 标题：弹簧缩放 + 淡入 ----
                val titleScale = Anim.lerp(0.86f, 1f, Anim.spring(Anim.clamp01(clock / 0.55f)))
                val titleAlpha = Anim.decelerate(Anim.clamp01(clock / 0.35f))
                Text(
                    "GEOMETRY DUEL",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                            alpha = titleAlpha
                        }
                )
                Text(
                    "N E A T   A I   E D I T I O N",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * titleAlpha),
                    modifier = Modifier.padding(top = 4.dp)
                )

                // ---- AI 状态 chips ----
                chipsVersion // 订阅刷新
                val chipAlpha = Anim.decelerate(Anim.stagger(clock, 2, 0.09f, 0.45f))
                if (chipAlpha > 0f) {
                    val chips = buildChips(controller)
                    Row(
                        Modifier
                            .padding(top = 10.dp)
                            .graphicsLayer { alpha = chipAlpha },
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (chip in chips) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    chip,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // ---- 主按钮列：stagger 上浮入场 ----
                Column(
                    Modifier
                        .padding(top = 20.dp, bottom = 12.dp)
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuButton(0, clock, filled = 0, text = "Start Game", onClick = onStart)
                    MenuButton(1, clock, filled = 1, text = "Tutorial", onClick = onTutorial)
                    MenuButton(2, clock, filled = 2, text = "Settings", onClick = onSettings)
                }
            }

            // ---- 底部硬件信息（固定在滚动区外）----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "GPU: ${HardwareInfo.gpuRenderer}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    Text(
                        "NPU: ${HardwareInfo.npuInfo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    "v${DuelController.VERSION}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    index: Int,
    clock: Float,
    filled: Int,
    text: String,
    onClick: () -> Unit,
) {
    val entrance = Anim.decelerate(Anim.stagger(clock - 0.1f, index, 0.09f, 0.45f))
    if (entrance <= 0f) {
        // 占位保持布局稳定
        Box(Modifier.height(58.dp))
        return
    }
    val modifier = Modifier
        .fillMaxWidth()
        .height(58.dp)
        .graphicsLayer {
            alpha = entrance
            translationY = (1f - entrance) * 24.dp.toPx()
        }
    when (filled) {
        0 -> Button(onClick = onClick, modifier = modifier) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
        1 -> FilledTonalButton(onClick = onClick, modifier = modifier) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
        else -> OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun buildChips(controller: DuelController): Array<String> {
    return if (controller.trainerStarted()) {
        val wr = controller.trainer.championWinRate()
        arrayOf(
            "GEN ${controller.trainer.generation()}",
            "WR ${if (wr < 0) "--" else "${Math.round(wr * 100)}%"}",
            "%.1f G/S".format(controller.trainer.genRate())
        )
    } else {
        arrayOf("AI", "WARMING", "UP")
    }
}
