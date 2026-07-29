package com.geometryduel.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Processing 风格的 2D 绘制封装（移植自 libGDX Shapes，语义一致）：
 * stroke/fill 状态机、矩阵栈、粗线/矩形/四边形/圆弧/圆角矩形。
 * 每帧通过 [bind] 绑定到当前 Compose [DrawScope] 后使用（单例复用，避免逐帧分配 Paint）。
 */
class GameRenderer {

    private lateinit var scope: DrawScope

    /** 每帧绘制前绑定当前 DrawScope。 */
    fun bind(scope: DrawScope) {
        this.scope = scope
    }

    var strokeColor: Color = Color.Black
        private set
    var fillColor: Color = Color.Black
        private set
    var doStroke = true
        private set
    var doFill = false
        private set
    var weight = 1f
        private set

    // ------------------------------------------------------------ 矩阵栈

    fun push() {
        scope.drawContext.canvas.save()
    }

    fun pop() {
        scope.drawContext.canvas.restore()
    }

    fun translate(x: Float, y: Float) {
        scope.drawContext.canvas.translate(x, y)
    }

    /** 旋转（弧度）。 */
    fun rotate(radians: Float) {
        scope.drawContext.canvas.rotate(Math.toDegrees(radians.toDouble()).toFloat())
    }

    fun scale(sx: Float, sy: Float) {
        scope.drawContext.canvas.scale(sx, sy)
    }

    // ------------------------------------------------------------ 样式

    fun stroke(r: Float, g: Float, b: Float, a: Float) {
        strokeColor = Color(r, g, b, a)
        doStroke = true
    }

    fun stroke(c: Color) = stroke(c, 255)

    fun stroke(c: Color, alpha: Int) {
        strokeColor = c.copy(alpha = clampAlpha(alpha))
        doStroke = true
    }

    fun noStroke() {
        doStroke = false
    }

    fun doStroke() {
        doStroke = true
    }

    fun doFill() {
        doFill = true
    }

    fun fill(r: Float, g: Float, b: Float, a: Float) {
        fillColor = Color(r, g, b, a)
        doFill = true
    }

    fun fill(c: Color) = fill(c, 255)

    fun fill(c: Color, alpha: Int) {
        fillColor = c.copy(alpha = clampAlpha(alpha))
        doFill = true
    }

    fun noFill() {
        doFill = false
    }

    fun strokeWeight(w: Float) {
        weight = w.coerceAtLeast(0.01f)
    }

    // ------------------------------------------------------------ 图元

    /** 直线（按 strokeWeight 加粗）。 */
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (!doStroke) return
        scope.drawLine(strokeColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = weight)
    }

    /** 矩形（左上角 + 宽高，y 向下）。 */
    fun rect(x: Float, y: Float, w: Float, h: Float) {
        if (doFill) {
            scope.drawRect(fillColor, Offset(x, y), Size(w, h))
        }
        if (doStroke) {
            val t = weight
            scope.drawRect(strokeColor, Offset(x, y), Size(w, t))
            scope.drawRect(strokeColor, Offset(x, y + h - t), Size(w, t))
            scope.drawRect(strokeColor, Offset(x, y + t), Size(t, h - 2 * t))
            scope.drawRect(strokeColor, Offset(x + w - t, y + t), Size(t, h - 2 * t))
        }
    }

    /** 四边形（两个三角形填充 + 四条边线）。 */
    fun quad(x1: Float, y1: Float, x2: Float, y2: Float,
             x3: Float, y3: Float, x4: Float, y4: Float) {
        if (doFill) {
            val p = Path()
            p.moveTo(x1, y1)
            p.lineTo(x2, y2)
            p.lineTo(x3, y3)
            p.lineTo(x4, y4)
            p.close()
            scope.drawPath(p, fillColor)
        }
        if (doStroke) {
            scope.drawLine(strokeColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = weight)
            scope.drawLine(strokeColor, Offset(x2, y2), Offset(x3, y3), strokeWidth = weight)
            scope.drawLine(strokeColor, Offset(x3, y3), Offset(x4, y4), strokeWidth = weight)
            scope.drawLine(strokeColor, Offset(x4, y4), Offset(x1, y1), strokeWidth = weight)
        }
    }

    /**
     * 圆弧（对应原作 arc(x,y,radius,startDeg,extentDeg)）。
     * 角度制与 y 向下坐标一致，extent 正值沿顺时针（视觉）。
     */
    fun arc(cx: Float, cy: Float, radius: Float, startDeg: Float, extentDeg: Float) {
        if (!doStroke || radius <= 0 || extentDeg == 0f) return
        scope.drawArc(
            color = strokeColor,
            startAngle = startDeg,
            sweepAngle = extentDeg,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = weight)
        )
    }

    /** 圆（stroke 轮廓；d 为直径）。 */
    fun circle(cx: Float, cy: Float, d: Float) {
        if (!doStroke) return
        scope.drawCircle(strokeColor, d / 2f, Offset(cx, cy), style = Stroke(width = weight))
    }

    /** 圆角矩形（r 自动钳制到短边一半）。 */
    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        val radius = r.coerceIn(0f, minOf(w, h) / 2f)
        if (doFill) {
            scope.drawRoundRect(
                fillColor, Offset(x, y), Size(w, h), CornerRadius(radius, radius)
            )
        }
        if (doStroke) {
            scope.drawRoundRect(
                strokeColor, Offset(x, y), Size(w, h), CornerRadius(radius, radius),
                style = Stroke(width = weight)
            )
        }
    }

    fun filledCircle(cx: Float, cy: Float, d: Float) {
        if (doFill) {
            scope.drawCircle(fillColor, d / 2f, Offset(cx, cy))
        }
        if (doStroke) circle(cx, cy, d)
    }

    /** 单像素点。 */
    fun dot(x: Float, y: Float, c: Color) {
        scope.drawRect(c, Offset(x - 1f, y - 1f), Size(2f, 2f))
    }

    // ------------------------------------------------------------ 文本（HUD，屏幕空间）

    private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
    }

    /**
     * 以中心点绘制文本（Roboto，屏幕像素字号）。
     * [cy] 为垂直中心。
     */
    fun text(s: String, cx: Float, cy: Float, sizePx: Float, color: Color) {
        if (sizePx <= 0.5f) return
        textPaint.textSize = sizePx
        textPaint.color = android.graphics.Color.argb(
            (color.alpha * 255f).toInt().coerceIn(0, 255),
            (color.red * 255f).toInt().coerceIn(0, 255),
            (color.green * 255f).toInt().coerceIn(0, 255),
            (color.blue * 255f).toInt().coerceIn(0, 255)
        )
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        scope.drawContext.canvas.nativeCanvas.drawText(s, cx, baseline, textPaint)
    }

    /** 文本测量宽度（字号 sizePx）。 */
    fun textWidth(s: String, sizePx: Float): Float {
        textPaint.textSize = sizePx
        return textPaint.measureText(s)
    }

    companion object {
        private fun clampAlpha(a: Int) = a.coerceIn(0, 255) / 255f
    }
}
