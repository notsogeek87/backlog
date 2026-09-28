package com.davidgcd.backlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm cream / terracotta palette, echoing the iOS app's two-icon theme
// (cream canvas + terracotta accent in light, near-black + amber in dark).
private val BrandPrimaryLight = Color(0xFFAC4C10)
private val BgCanvasLight = Color(0xFFFBF1E4)
private val BrandPrimaryDark = Color(0xFFE0A458)
private val BgCanvasDark = Color(0xFF121212)

private val LightColors = lightColorScheme(
    primary = BrandPrimaryLight,
    background = BgCanvasLight,
    surface = BgCanvasLight,
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    background = BgCanvasDark,
    surface = BgCanvasDark,
)

@Composable
fun BacklogTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
