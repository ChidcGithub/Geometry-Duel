package com.geometryduel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.geometryduel.theme.DuelTheme
import com.geometryduel.ui.GameScreen
import com.geometryduel.ui.GameSession
import com.geometryduel.ui.MenuScreen
import com.geometryduel.ui.SettingsScreen
import com.geometryduel.ui.TutorialScreen

/** 应用根组合：主题 + 屏幕导航（菜单 / 对战 / 教学 / 设置）。 */
@Composable
fun DuelApp(controller: DuelController) {
    DuelTheme(controller) {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            var screen by remember { mutableStateOf(Screen.Menu) }
            when (screen) {
                Screen.Menu -> MenuScreen(
                    controller,
                    onStart = { screen = Screen.Game },
                    onTutorial = { screen = Screen.Tutorial },
                    onSettings = { screen = Screen.Settings }
                )
                Screen.Game -> {
                    val session = remember { GameSession(controller) }
                    GameScreen(controller, session, onExit = { screen = Screen.Menu })
                }
                Screen.Tutorial -> TutorialScreen(
                    controller,
                    onExit = { screen = Screen.Menu }
                )
                Screen.Settings -> SettingsScreen(
                    controller,
                    onBack = { screen = Screen.Menu }
                )
            }
        }
    }
}

private enum class Screen { Menu, Game, Tutorial, Settings }
