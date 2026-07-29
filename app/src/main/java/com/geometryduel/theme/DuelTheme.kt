package com.geometryduel.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.geometryduel.DuelController

/**
 * 应用主题：Material 3 Expressive + Material You 动态取色。
 * - API 31+ 且开关开：dynamic*ColorScheme（壁纸取色）
 * - 否则：expressive*ColorScheme（默认 M3 紫）
 * - 系统栏图标色随亮暗主题切换
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DuelTheme(controller: DuelController, content: @Composable () -> Unit) {
    val dark = controller.themeType == GamePalette.Type.Dark
    val context = LocalView.current.context
    val colorScheme = when {
        controller.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowInsetsControllerCompat(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
}
