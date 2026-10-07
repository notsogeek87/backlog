package com.davidgcd.backlog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.davidgcd.backlog.ui.prefs.ThemeMode

/**
 * Les valeurs d'une apparence. Le verre « Glass night » en est la déclinaison sombre ; les variantes claire et
 * AMOLED gardent les mêmes rôles (texte, bordures, verre, accent) pour que chaque écran suive sans changement.
 */
@Immutable
class GlassPalette(
    val isDark: Boolean,
    val bg: Color,
    val bg2: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val borderLight: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassStrong: Color,
    val cyan: Color,
    val accentStops: List<Color>,
    /** Couleur du texte posé sur le dégradé d'accent : assez sombre sur le sombre, blanche sur le clair (contraste ≥ 4,5:1). */
    val onAccent: Color,
    /** Halo diffus du fond (cyan / violet). */
    val washAlpha: Float,
    val navBar: Color,
)

private val DarkGlass = GlassPalette(
    isDark = true,
    bg = Color(0xFF050A14),
    bg2 = Color(0xFF08111F),
    text = Color(0xFFF5F7FF),
    textMuted = Color(0xFFF5F7FF).copy(alpha = 0.62f),
    border = Color.White.copy(alpha = 0.12f),
    borderLight = Color.White.copy(alpha = 0.18f),
    glassTop = Color.White.copy(alpha = 0.08f),
    glassBottom = Color.White.copy(alpha = 0.025f),
    glassStrong = Color.White.copy(alpha = 0.10f),
    cyan = Color(0xFF22D3EE),
    accentStops = listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF8B5CF6)),
    onAccent = Color(0xFF02101C),
    washAlpha = 0.09f,
    navBar = Color(0xB8050A14),
)

private val AmoledGlass = GlassPalette(
    isDark = true,
    bg = Color.Black,
    bg2 = Color(0xFF05070C),
    text = DarkGlass.text,
    textMuted = DarkGlass.textMuted,
    border = DarkGlass.border,
    borderLight = DarkGlass.borderLight,
    glassTop = DarkGlass.glassTop,
    glassBottom = DarkGlass.glassBottom,
    glassStrong = DarkGlass.glassStrong,
    cyan = DarkGlass.cyan,
    accentStops = DarkGlass.accentStops,
    onAccent = DarkGlass.onAccent,
    washAlpha = 0.04f,
    navBar = Color(0xE6000000),
)

private val LightGlass = GlassPalette(
    isDark = false,
    bg = Color(0xFFF3F6FC),
    bg2 = Color(0xFFE9EEF8),
    text = Color(0xFF0B1220),
    textMuted = Color(0xFF0B1220).copy(alpha = 0.66f),
    border = Color(0xFF0B1220).copy(alpha = 0.14f),
    borderLight = Color(0xFF0B1220).copy(alpha = 0.20f),
    glassTop = Color.White.copy(alpha = 0.92f),
    glassBottom = Color.White.copy(alpha = 0.60f),
    glassStrong = Color.White,
    cyan = Color(0xFF0E7490),
    accentStops = listOf(Color(0xFF0E7490), Color(0xFF1D4ED8), Color(0xFF6D28D9)),
    onAccent = Color.White,
    washAlpha = 0.14f,
    navBar = Color(0xE6F3F6FC),
)

val LocalGlassPalette = staticCompositionLocalOf { DarkGlass }

/**
 * Jetons de l'app : accents constants + neutres qui suivent l'apparence active ([GlassPalette]).
 * Les neutres se lisent dans une composition (`@Composable`) : c'est ce qui permet le thème clair.
 */
object Glass {
    val Blue = Color(0xFF3B82F6)
    val Purple = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Green = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)
    val Teal = Color(0xFF14B8A6)

    val Bg: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.bg
    val Bg2: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.bg2
    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.cyan
    val Text: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.text
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.textMuted
    val Border: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.border
    val BorderLight: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.borderLight
    val GlassTop: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.glassTop
    val GlassBottom: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.glassBottom
    val GlassStrong: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.glassStrong
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.onAccent
    val NavBar: Color @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.navBar
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.isDark

    val Accent: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(LocalGlassPalette.current.accentStops)
    val AccentHorizontal: Brush @Composable @ReadOnlyComposable get() = Brush.horizontalGradient(LocalGlassPalette.current.accentStops)
    val GlassFill: Brush
        @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.let { Brush.linearGradient(listOf(it.glassTop, it.glassBottom)) }
    val GlassStrongFill: Brush
        @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.let { Brush.linearGradient(listOf(it.glassStrong, it.glassTop)) }
    val BorderBrush: Brush
        @Composable @ReadOnlyComposable get() = LocalGlassPalette.current.let {
            Brush.linearGradient(listOf(it.borderLight, it.border, it.border.copy(alpha = it.border.alpha * 0.5f)))
        }
}

private val NightColors = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF032530),
    primaryContainer = Color(0xFF0B3A4A),
    onPrimaryContainer = Color(0xFFC6F3FF),
    secondary = Glass.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A2350),
    onSecondaryContainer = Color(0xFFE4DDFF),
    tertiary = Glass.Pink,
    background = Color(0xFF050A14),
    onBackground = Color(0xFFF5F7FF),
    surface = Color(0xFF050A14),
    onSurface = Color(0xFFF5F7FF),
    surfaceVariant = Color(0xFF16233A),
    onSurfaceVariant = Color(0xFFB4BCD0),
    surfaceContainerLow = Color(0xFF08111F),
    surfaceContainer = Color(0xFF0C1626),
    surfaceContainerHigh = Color(0xFF12203A),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3A0010),
    outline = Color(0xFF6B7690),
    outlineVariant = Color(0x33FFFFFF),
)

private val AmoledColors = NightColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLow = Color(0xFF05070C),
    surfaceContainer = Color(0xFF0A0F18),
    surfaceContainerHigh = Color(0xFF111827),
)

private val DayColors = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3FA),
    onPrimaryContainer = Color(0xFF023644),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E1FF),
    onSecondaryContainer = Color(0xFF241356),
    tertiary = Color(0xFFBE185D),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFF3F6FC),
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFDDE4F2),
    onSurfaceVariant = Color(0xFF3F4A60),
    surfaceContainerLow = Color(0xFFEAEFF9),
    surfaceContainer = Color(0xFFE3E9F5),
    surfaceContainerHigh = Color(0xFFDAE2F1),
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFF6B7690),
    outlineVariant = Color(0x330B1220),
)

/** `true` quand l'apparence résolue est sombre (le mode Système suit le téléphone). */
@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

@Composable
fun BacklogTheme(
    mode: ThemeMode = ThemeMode.DARK,
    dynamicColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = resolveDark(mode)
    val context = LocalContext.current
    val dynamic = dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseScheme = when {
        !dark -> DayColors
        mode == ThemeMode.AMOLED -> AmoledColors
        else -> NightColors
    }
    val scheme = if (dynamic) {
        val d = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        baseScheme.copy(primary = d.primary, onPrimary = d.onPrimary, primaryContainer = d.primaryContainer, onPrimaryContainer = d.onPrimaryContainer)
    } else {
        baseScheme
    }
    val basePalette = when {
        !dark -> LightGlass
        mode == ThemeMode.AMOLED -> AmoledGlass
        else -> DarkGlass
    }
    // Couleurs dynamiques : l'accent (et le début du dégradé) prend la couleur du fond d'écran.
    val palette = if (dynamic) {
        GlassPalette(
            isDark = basePalette.isDark, bg = basePalette.bg, bg2 = basePalette.bg2, text = basePalette.text,
            textMuted = basePalette.textMuted, border = basePalette.border, borderLight = basePalette.borderLight,
            glassTop = basePalette.glassTop, glassBottom = basePalette.glassBottom, glassStrong = basePalette.glassStrong,
            cyan = scheme.primary,
            accentStops = listOf(scheme.primary, scheme.primary),
            onAccent = scheme.onPrimary, washAlpha = basePalette.washAlpha, navBar = basePalette.navBar,
        )
    } else {
        basePalette
    }
    CompositionLocalProvider(LocalGlassPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
