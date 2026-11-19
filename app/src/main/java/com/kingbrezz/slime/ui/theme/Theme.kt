package com.kingbrezz.slime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SlimeGreen = Color(0xFFC6F25A)
val SlimeDim = Color(0xFF8FB83A)
val Bg = Color(0xFF0B100E)
val Raise = Color(0xFF121A16)
val Surface = Color(0xFF17211C)
val Surface2 = Color(0xFF223028)
val Line = Color(0xFF314239)
val Fg = Color(0xFFE7F3EB)
val Muted = Color(0xFF8EA497)
val Faint = Color(0xFF64786C)
val Ink = Color(0xFF12180D)
val Warn = Color(0xFFE4C56D)
val Bad = Color(0xFFE07A68)

private val SlimeScheme = darkColorScheme(
    primary = SlimeGreen,
    onPrimary = Ink,
    secondary = SlimeDim,
    background = Bg,
    onBackground = Fg,
    surface = Surface,
    onSurface = Fg,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Bad,
)

@Composable
fun SlimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SlimeScheme,
        content = content,
    )
}
