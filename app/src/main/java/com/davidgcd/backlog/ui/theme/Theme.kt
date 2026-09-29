package com.davidgcd.backlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm cream / terracotta palette, echoing the iOS app's two-icon theme
// (cream canvas + terracotta accent in light, near-black + amber in dark).
// Every role is set explicitly: leaving the container/outline roles at their
// Material defaults tints chips, cards and selected states lavender, which
// clashes with the warm canvas.
private val BrandPrimaryLight = Color(0xFFAC4C10)
private val BgCanvasLight = Color(0xFFFBF1E4)
private val BrandPrimaryDark = Color(0xFFE0A458)
private val BgCanvasDark = Color(0xFF121212)

private val LightColors = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF391300),
    secondary = Color(0xFF77574A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6DDD1),
    onSecondaryContainer = Color(0xFF2C160C),
    background = BgCanvasLight,
    onBackground = Color(0xFF221A14),
    surface = BgCanvasLight,
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF2E0D2),
    onSurfaceVariant = Color(0xFF52443B),
    surfaceContainerLow = Color(0xFFF7EBDD),
    surfaceContainer = Color(0xFFF3E6D8),
    surfaceContainerHigh = Color(0xFFEEE0D2),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD7C3B8),
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF3F2600),
    primaryContainer = Color(0xFF5C3A00),
    onPrimaryContainer = Color(0xFFFFDDB2),
    secondary = Color(0xFFE5BFA8),
    onSecondary = Color(0xFF432B1C),
    secondaryContainer = Color(0xFF5C4131),
    onSecondaryContainer = Color(0xFFFFDBC8),
    background = BgCanvasDark,
    onBackground = Color(0xFFEDE0D8),
    surface = BgCanvasDark,
    onSurface = Color(0xFFEDE0D8),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD7C3B8),
    surfaceContainerLow = Color(0xFF1A1714),
    surfaceContainer = Color(0xFF211D19),
    surfaceContainerHigh = Color(0xFF2B2622),
    outline = Color(0xFF9F8D83),
    outlineVariant = Color(0xFF52443B),
)

@Composable
fun BacklogTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
