package com.davidgcd.backlog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * "Glass night" design tokens: night-blue canvas, translucent white surfaces,
 * cyan / blue / violet accents used sparingly (never as large fills).
 * The app is dark-only by design; these are the source of truth for every screen.
 */
object Glass {
    val Bg = Color(0xFF050A14)
    val Bg2 = Color(0xFF08111F)

    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF3B82F6)
    val Purple = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Green = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)

    val Text = Color(0xFFF5F7FF)
    val TextMuted = Color(0xFFF5F7FF).copy(alpha = 0.62f)

    val Border = Color.White.copy(alpha = 0.12f)
    val BorderLight = Color.White.copy(alpha = 0.18f)

    val GlassTop = Color.White.copy(alpha = 0.08f)
    val GlassBottom = Color.White.copy(alpha = 0.025f)
    val GlassStrong = Color.White.copy(alpha = 0.10f)

    val Accent: Brush get() = Brush.linearGradient(listOf(Cyan, Blue, Purple))
    val AccentHorizontal: Brush get() = Brush.horizontalGradient(listOf(Cyan, Blue, Purple))
    val GlassFill: Brush get() = Brush.linearGradient(listOf(GlassTop, GlassBottom))
    val BorderBrush: Brush get() = Brush.linearGradient(listOf(BorderLight, Border, Color.White.copy(alpha = 0.06f)))
}

private val NightColors = darkColorScheme(
    primary = Glass.Cyan,
    onPrimary = Color(0xFF032530),
    primaryContainer = Color(0xFF0B3A4A),
    onPrimaryContainer = Color(0xFFC6F3FF),
    secondary = Glass.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A2350),
    onSecondaryContainer = Color(0xFFE4DDFF),
    tertiary = Glass.Pink,
    background = Glass.Bg,
    onBackground = Glass.Text,
    surface = Glass.Bg,
    onSurface = Glass.Text,
    surfaceVariant = Color(0xFF16233A),
    onSurfaceVariant = Color(0xFFB4BCD0),
    surfaceContainerLow = Glass.Bg2,
    surfaceContainer = Color(0xFF0C1626),
    surfaceContainerHigh = Color(0xFF12203A),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3A0010),
    outline = Color(0xFF6B7690),
    outlineVariant = Color(0x33FFFFFF),
)

@Composable
fun BacklogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, content = content)
}
