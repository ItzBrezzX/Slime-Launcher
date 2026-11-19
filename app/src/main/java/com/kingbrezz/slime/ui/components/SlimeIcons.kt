package com.kingbrezz.slime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kingbrezz.slime.ui.theme.SlimeGreen

/** Original Slime vector icons — no emoji, no third-party icon packs. */
object SIcon {
    @Composable
    fun Droplet(color: Color = Color.Unspecified, size: Dp = 24.dp) {
        val c = if (color == Color.Unspecified) SlimeGreen else color
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.08f)
                cubicTo(w * 0.55f, h * 0.28f, w * 0.88f, h * 0.42f, w * 0.88f, h * 0.62f)
                cubicTo(w * 0.88f, h * 0.86f, w * 0.7f, h * 0.96f, w * 0.5f, h * 0.96f)
                cubicTo(w * 0.3f, h * 0.96f, w * 0.12f, h * 0.86f, w * 0.12f, h * 0.62f)
                cubicTo(w * 0.12f, h * 0.42f, w * 0.45f, h * 0.28f, w * 0.5f, h * 0.08f)
                close()
            }
            drawPath(path, c)
            drawCircle(Color(0xFF12180D).copy(alpha = 0.35f), radius = w * 0.07f, center = Offset(w * 0.42f, h * 0.55f))
        }
    }

    @Composable
    fun Home(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val path = Path().apply {
                moveTo(s * 0.5f, s * 0.12f)
                lineTo(s * 0.88f, s * 0.48f)
                lineTo(s * 0.88f, s * 0.88f)
                lineTo(s * 0.58f, s * 0.88f)
                lineTo(s * 0.58f, s * 0.62f)
                lineTo(s * 0.42f, s * 0.62f)
                lineTo(s * 0.42f, s * 0.88f)
                lineTo(s * 0.12f, s * 0.88f)
                lineTo(s * 0.12f, s * 0.48f)
                close()
            }
            drawPath(path, color)
        }
    }

    @Composable
    fun Layers(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val stroke = Stroke(width = s * 0.08f, cap = StrokeCap.Round)
            drawRoundRect(color, Offset(s * 0.15f, s * 0.18f), Size(s * 0.7f, s * 0.22f), CornerRadius(s * 0.06f), style = stroke)
            drawRoundRect(color, Offset(s * 0.15f, s * 0.42f), Size(s * 0.7f, s * 0.22f), CornerRadius(s * 0.06f), style = stroke)
            drawRoundRect(color, Offset(s * 0.15f, s * 0.66f), Size(s * 0.7f, s * 0.22f), CornerRadius(s * 0.06f), style = stroke)
        }
    }

    @Composable
    fun Modules(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val gap = s * 0.08f
            val cell = (s - gap * 3) / 2f
            drawRoundRect(color, Offset(gap, gap), Size(cell, cell), CornerRadius(s * 0.08f))
            drawRoundRect(color, Offset(gap * 2 + cell, gap), Size(cell, cell), CornerRadius(s * 0.08f))
            drawRoundRect(color, Offset(gap, gap * 2 + cell), Size(cell, cell), CornerRadius(s * 0.08f))
            drawRoundRect(color, Offset(gap * 2 + cell, gap * 2 + cell), Size(cell, cell), CornerRadius(s * 0.08f))
        }
    }

    @Composable
    fun Config(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
            drawCircle(color, radius = s * 0.28f, center = Offset(s * 0.5f, s * 0.5f), style = stroke)
            drawCircle(color, radius = s * 0.12f, center = Offset(s * 0.5f, s * 0.5f))
            for (i in 0 until 6) {
                val ang = Math.toRadians((i * 60).toDouble())
                val x1 = s * 0.5f + kotlin.math.cos(ang).toFloat() * s * 0.34f
                val y1 = s * 0.5f + kotlin.math.sin(ang).toFloat() * s * 0.34f
                val x2 = s * 0.5f + kotlin.math.cos(ang).toFloat() * s * 0.46f
                val y2 = s * 0.5f + kotlin.math.sin(ang).toFloat() * s * 0.46f
                drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            }
        }
    }

    @Composable
    fun Library(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            drawRoundRect(color, Offset(s * 0.18f, s * 0.2f), Size(s * 0.64f, s * 0.62f), CornerRadius(s * 0.06f))
            drawRect(Color(0xFF0B100E), Offset(s * 0.28f, s * 0.34f), Size(s * 0.44f, s * 0.08f))
            drawRect(Color(0xFF0B100E), Offset(s * 0.28f, s * 0.5f), Size(s * 0.32f, s * 0.08f))
        }
    }

    @Composable
    fun Settings(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val stroke = Stroke(width = s * 0.1f, cap = StrokeCap.Round)
            drawLine(color, Offset(s * 0.2f, s * 0.3f), Offset(s * 0.8f, s * 0.3f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
            drawLine(color, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.8f, s * 0.5f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
            drawLine(color, Offset(s * 0.2f, s * 0.7f), Offset(s * 0.8f, s * 0.7f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
            drawCircle(color, radius = s * 0.08f, center = Offset(s * 0.35f, s * 0.3f))
            drawCircle(color, radius = s * 0.08f, center = Offset(s * 0.65f, s * 0.5f))
            drawCircle(color, radius = s * 0.08f, center = Offset(s * 0.45f, s * 0.7f))
        }
    }

    @Composable
    fun Play(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val path = Path().apply {
                moveTo(s * 0.28f, s * 0.18f)
                lineTo(s * 0.82f, s * 0.5f)
                lineTo(s * 0.28f, s * 0.82f)
                close()
            }
            drawPath(path, color)
        }
    }

    @Composable
    fun Cross(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            drawLine(color, Offset(s * 0.25f, s * 0.25f), Offset(s * 0.75f, s * 0.75f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
            drawLine(color, Offset(s * 0.75f, s * 0.25f), Offset(s * 0.25f, s * 0.75f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Tools(color: Color, size: Dp = 22.dp) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            drawLine(color, Offset(s * 0.24f, s * 0.78f), Offset(s * 0.6f, s * 0.42f), strokeWidth = s * 0.15f, cap = StrokeCap.Round)
            drawCircle(color, radius = s * 0.2f, center = Offset(s * 0.67f, s * 0.33f), style = Stroke(width = s * 0.1f))
            drawCircle(color, radius = s * 0.07f, center = Offset(s * 0.24f, s * 0.78f))
        }
    }
}
