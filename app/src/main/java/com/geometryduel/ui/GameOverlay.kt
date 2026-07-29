package com.geometryduel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geometryduel.DuelController
import com.geometryduel.game.state.ResultGameState

/**
 * 对战界面的 Compose 覆盖层：
 * Back/Pause 按钮、结算卡片（上浮淡入）、说明窗、暂停遮罩。
 */
@Composable
fun BoxScope.GameOverlay(
    controller: DuelController,
    session: GameSession,
    tick: Long,
    onBack: () -> Unit,
) {
    // ---- 顶栏按钮 ----
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        OutlinedButton(onClick = { session.paused = !session.paused }) {
            Icon(DuelIcons.Pause, contentDescription = null)
            Text(" Pause", Modifier.padding(start = 4.dp))
        }
    }

    val system = session.system
    val st = system.currentState

    // ---- 结算卡片（上浮淡入）----
    if (st is ResultGameState && !system.demoPlay) {
        tick // 帧驱动重组
        val t = Anim.decelerate(Anim.clamp01(st.properFrameCount / 24f))
        if (t > 0f) {
            val rise = (1f - t) * 40f
            Card(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.72f)
                    .graphicsLayer {
                        alpha = t
                        translationY = -rise * density / 2f
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val msg = if (st.playerWon) "You Win!" else "You Lose!"
                    Text(
                        msg,
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (st.playerWon) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    if (st.properFrameCount > ResultGameState.DURATION) {
                        Text(
                            "Tap X to Restart",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    val wr = controller.trainer.championWinRate()
                    val sr = controller.trainer.simRate()
                    val simText = if (sr >= 1000f) "%.1fk".format(sr / 1000f)
                    else "%.0f".format(sr)
                    Text(
                        "Gen ${controller.trainer.generation()}" +
                                "  ·  WR ${if (wr < 0) "--" else "${Math.round(wr * 100)}%"}" +
                                "  ·  $simText sim/s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }

    // ---- 说明窗（演示模式，随 instrT 淡入）----
    if (session.instrT > 0f) {
        InstructionPanel(session.instrT)
    }

    // ---- 暂停遮罩 ----
    if (session.paused) {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)
        ) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "Paused",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/** 说明窗（M3 圆角卡片）。 */
@Composable
private fun BoxScope.InstructionPanel(alpha: Float) {
    Card(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.72f)
            .alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Text(
                "Geometry Duel!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            InstructionRow("Z Button:", "Normal Attack")
            InstructionRow("X Button:", "Deadly Attack")
            InstructionRow("Left Touch:", "Move or Aim")
            Text(
                "- Press Z to Start -",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "(Tap to Show/Hide)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Made by FAL! Android port by Pama1234!\nUnofficial Remake\nSelf-learning AI by Chidc",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )
        }
    }
}

@Composable
private fun InstructionRow(key: String, value: String) {
    Row(Modifier.padding(top = 14.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 110.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
