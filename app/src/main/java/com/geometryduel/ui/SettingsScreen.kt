package com.geometryduel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.geometryduel.DuelController
import com.geometryduel.HardwareInfo
import com.geometryduel.theme.GamePalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 设置（M3 Expressive）：
 * - 分区卡片：外观 / 对局 / AI 训练 / 关于
 * - 真控件：Switch（主题/动态取色/训练）、Slider（音量）、步进器（射线）、循环按钮（对手/速度）
 * - AI 训练卡片内含实时信息区（Gen/WR/G-S/物种/模拟局数/幽灵）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    controller: DuelController,
    onBack: () -> Unit,
) {
    fun back() {
        controller.saveConfig()
        onBack()
    }
    BackHandler { back() }

    // AI 信息定时刷新
    var infoTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(500); infoTick++ }
    }

    var clock by remember { mutableFloatStateOf(0f) }
    rememberFrameTick { dt -> clock += dt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { back() }) {
                        Icon(DuelIcons.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp, vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "appearance") {
                SectionCard("APPEARANCE", 0, clock) {
                    SwitchRow(
                        label = "Dark Theme",
                        checked = controller.themeType == GamePalette.Type.Dark,
                        onToggle = { controller.toggleTheme() }
                    )
                    SwitchRow(
                        label = "Dynamic Color" + if (controller.themeSeed == 0) " (N/A)" else "",
                        checked = controller.dynamicColor,
                        enabled = controller.themeSeed != 0,
                        onToggle = { controller.toggleDynamicColor() }
                    )
                }
            }

            item(key = "gameplay") {
                SectionCard("GAMEPLAY", 1, clock) {
                    // 音量
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume", style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${(controller.volume * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 14.dp)
                            )
                            Slider(
                                value = controller.volume,
                                onValueChange = { controller.volume = it },
                                modifier = Modifier.widthIn(max = 190.dp)
                            )
                        }
                    }
                    CycleRow("Opponent", controller.opponentStyleLabel()) {
                        controller.cycleOpponentStyle()
                    }
                    val speedLabels = arrayOf("30Hz", "20Hz", "15Hz", "12Hz")
                    CycleRow("AI Speed", speedLabels[controller.aiSpeed and 3]) {
                        controller.aiSpeed = (controller.aiSpeed + 1) % 4
                    }
                }
            }

            item(key = "ai") {
                SectionCard("AI TRAINING", 2, clock) {
                    SwitchRow(
                        label = "Background Training",
                        checked = controller.trainingEnabled,
                        onToggle = { controller.toggleTraining() }
                    )
                    // 战后训练轮数上限步进器
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Post-Match Sim Limit", style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { controller.updateTrainSimLimit(controller.trainSimLimit - 1000) }
                            ) { Text("-") }
                            Text(
                                "${controller.trainSimLimit}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            FilledTonalButton(
                                onClick = { controller.updateTrainSimLimit(controller.trainSimLimit + 1000) }
                            ) { Text("+") }
                        }
                    }
                    // 视野射线步进器
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vision Rays", style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { controller.updateVisionRays(controller.visionRays - 4) }
                            ) { Text("-") }
                            Text(
                                "${controller.visionRays}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            FilledTonalButton(
                                onClick = { controller.updateVisionRays(controller.visionRays + 4) }
                            ) { Text("+") }
                        }
                    }
                    // 危险区：重置 AI
                    var resetFlash by remember { mutableStateOf(false) }
                    LaunchedEffect(resetFlash) {
                        if (resetFlash) { delay(1200); resetFlash = false }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Danger Zone", style = MaterialTheme.typography.bodyLarge)
                        FilledTonalButton(
                            onClick = { controller.resetAi(); resetFlash = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) { Text(if (resetFlash) "Done!" else "Reset AI") }
                    }
                    // AI 实时信息区
                    infoTick
                    Column(Modifier.padding(top = 8.dp)) {
                        val wr = controller.trainer.championWinRate()
                        InfoLine(
                            "Gen ${controller.trainer.generation()}" +
                                    "  ·  WR ${if (wr < 0) "--" else "${Math.round(wr * 100)}%"}" +
                                    "  ·  ${"%.1f".format(controller.trainer.genRate())} g/s"
                        )
                        InfoLine(controller.speciesInfoText())
                        InfoLine(
                            "Sims ${controller.trainer.simMatches()}" +
                                    "  ·  Ghosts ${controller.trainer.ghostCount()}" +
                                    if (controller.trainer.isConverged()) "  ·  Converged" else ""
                        )
                        InfoLine(
                            "Post-match ${controller.trainer.simsSinceLastMatch()}/${controller.trainSimLimit} sims" +
                                    if (controller.trainer.simLimitReached()) "  ·  Auto-paused (play a match to resume)" else ""
                        )
                    }
                }
            }

            item(key = "about") {
                SectionCard("ABOUT", 3, clock) {
                    InfoLine("GPU: ${HardwareInfo.gpuRenderer}")
                    InfoLine("NPU: ${HardwareInfo.npuInfo}")
                    InfoLine("Version ${DuelController.VERSION}")
                    InfoLine("Made by FAL · Android port by Pama1234 · Unofficial remake")
                }
            }
        }
    }
}

/** 分区卡片：stagger 上浮入场。 */
@Composable
private fun SectionCard(
    title: String,
    index: Int,
    clock: Float,
    content: @Composable () -> Unit,
) {
    val entrance = Anim.decelerate(Anim.stagger(clock - 0.05f, index, 0.08f, 0.4f))
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 20.dp.toPx()
            }
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        )
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Composable
private fun CycleRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}
