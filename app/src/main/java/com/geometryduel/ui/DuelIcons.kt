package com.geometryduel.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 内联 Material Symbols 图标（material3 1.4+ 不再捆绑 material-icons，按需手写矢量路径）。
 */
object DuelIcons {

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }

    val Pause: ImageVector by lazy {
        icon("Pause", "M6,19h4V5H6v14zm8,-14v14h4V5h-4z")
    }

    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow", "M8,5v14l11,-7z")
    }

    val SkipNext: ImageVector by lazy {
        icon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black)
        ).build()
}
