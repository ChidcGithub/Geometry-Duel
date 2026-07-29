package com.geometryduel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geometryduel.DuelController
import kotlin.math.ceil
import kotlin.math.max

/**
 * 教学模式（M3 Expressive）：
 * - 初始难度 0.02，每从演示进入实战一次 +0.2，超过 1.0 即完成
 * - 顶部信息卡：标题 / 波浪进度条（Expressive）/ 剩余轮次；右上角 Skip 按钮
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TutorialScreen(
    controller: DuelController,
    onExit: () -> Unit,
) {
    val session = remember { GameSession(controller, tutorial = true) }

    Box(Modifier.fillMaxSize()) {
        GameScreen(controller = controller, session = session, onExit = onExit)

        // ---- 教学 HUD ----
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 72.dp)
        ) {
            Text(
                "Tutorial",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Level ${"%.1f".format(session.tutorialLevel)}" +
                        if (controller.tutorialDone) "  ·  Done" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            LinearWavyProgressIndicator(
                progress = { (session.tutorialLevel / 1.0f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 190.dp)
            )
            val remain = ceil((1.0f - session.tutorialLevel) / GameSession.LEVEL_STEP).toInt()
            Text(
                "${max(0, remain)} rounds to go",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        OutlinedButton(
            onClick = {
                controller.tutorialDone = true
                controller.saveConfig()
                session.exitToMenu()
                onExit()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 10.dp)
        ) { Text("Skip") }
    }
}
