package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.IgdbImage

/*
 * The app's glass design system. Compose has no cheap backdrop blur, so "glass" is a
 * translucent white gradient + hairline gradient border + top highlight over the night
 * canvas; real blur (API 31+) is only used on the game artwork backdrops.
 */

val GlassShape = RoundedCornerShape(20.dp)
val GlassShapeSmall = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(999.dp)

/** Night canvas with faint cyan / violet radial washes so it never reads as flat black. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Glass.Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Glass.Cyan.copy(alpha = 0.09f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Glass.Purple.copy(alpha = 0.09f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.2f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            },
    ) { content() }
}

fun Modifier.glass(shape: Shape = GlassShape, strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(if (strong) Brush.linearGradient(listOf(Glass.GlassStrong, Glass.GlassTop)) else Glass.GlassFill)
    .border(1.dp, Glass.BorderBrush, shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .glass(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** Cover art as a first-class element: rounded, hairline border, soft drop shadow. */
@Composable
fun GameCover(imageId: String?, width: Dp, modifier: Modifier = Modifier, size: IgdbImage.Size = IgdbImage.Size.CoverBig) {
    val shape = RoundedCornerShape(12.dp)
    val boxModifier = modifier
        .size(width, width * 4f / 3f)
        .shadow(10.dp, shape, ambientColor = Glass.Cyan.copy(alpha = 0.25f), spotColor = Color.Black)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, Glass.Border, shape)
    if (imageId != null) {
        AsyncImage(
            model = IgdbImage.url(imageId, size),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = boxModifier,
        )
    } else {
        Box(boxModifier)
    }
}

/** Accent colours like violet / blue are too dark as text on the night canvas: mix them toward white. */
fun readableOnDark(tint: Color): Color = lerp(tint, Color.White, 0.45f)

/** Small glass pill for platforms / genres / status. */
@Composable
fun GlassBadge(text: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (tint == Color.White) Glass.Text else readableOnDark(tint),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(PillShape)
            .background(tint.copy(alpha = if (tint == Color.White) 0.10f else 0.16f))
            .border(1.dp, tint.copy(alpha = 0.28f), PillShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Selectable chip tinted with [tint] (status picker): filled + bordered when selected, neutral otherwise. */
@Composable
fun GlassBadgeButton(text: String, tint: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) readableOnDark(tint) else Glass.TextMuted,
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(if (selected) tint.copy(alpha = 0.22f) else Glass.GlassTop)
            .border(1.dp, if (selected) tint.copy(alpha = 0.7f) else Glass.Border, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Filterable / removable pill (active filters). */
@Composable
fun GlassPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(if (selected) Glass.Cyan.copy(alpha = 0.18f) else Glass.GlassTop)
            .border(1.dp, if (selected) Glass.Cyan.copy(alpha = 0.6f) else Glass.Border, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Glass.Cyan else Glass.Text,
            maxLines = 1,
        )
        trailing?.invoke(this)
    }
}

/** Primary CTA: cyan → blue → violet gradient, 48dp minimum touch target. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .shadow(14.dp, shape, ambientColor = Glass.Cyan.copy(alpha = 0.35f), spotColor = Glass.Cyan.copy(alpha = 0.35f))
            .clip(shape)
            .background(Glass.AccentHorizontal)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}

/** Secondary action: glass surface, optional accent colour for text (e.g. destructive). */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, contentColor: Color = Glass.Text) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .glass(shape, strong = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}

/** 0–100 score as a gradient bar; the glow is drawn inside the rounded track so nothing spills out. */
@Composable
fun GradientProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(Glass.AccentHorizontal),
            )
        }
    }
}

/** Compact stat tile: big number, muted label. */
@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Glass.Cyan) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = readableOnDark(accent))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The one list row used by backlog, search and discover: big cover, title, platform badges,
 * release date and (when IGDB has one) the rating bar. Data is optional — nothing is invented.
 */
@Composable
fun GameListItem(
    name: String,
    coverImageId: String?,
    modifier: Modifier = Modifier,
    platforms: List<String> = emptyList(),
    meta: String? = null,
    rating: Double? = null,
    dimmed: Boolean = false,
    statusLabel: String? = null,
    statusTint: Color = Color.White,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GameCover(coverImageId, width = 64.dp, size = IgdbImage.Size.CoverSmall)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dimmed) Glass.TextMuted else Glass.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (platforms.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        platforms.take(2).forEach { GlassBadge(it, modifier = Modifier.weight(1f, fill = false)) }
                        if (platforms.size > 2) GlassBadge("+${platforms.size - 2}")
                    }
                }
                if (statusLabel != null) GlassBadge(statusLabel, tint = statusTint)
                if (!meta.isNullOrEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientProgressBar((rating / 100.0).toFloat(), modifier = Modifier.weight(1f))
                        Text("${rating.toInt()}", style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted)
                    }
                }
            }
            trailing()
        }
    }
}

/** Glass top bar: transparent so the night canvas / artwork shows through. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    titleContentColor = Glass.Text,
    actionIconContentColor = Glass.Text,
    navigationIconContentColor = Glass.Text,
)

@Composable
fun glassNavigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Glass.Cyan,
    selectedTextColor = Glass.Cyan,
    indicatorColor = Glass.Cyan.copy(alpha = 0.16f),
    unselectedIconColor = Glass.TextMuted,
    unselectedTextColor = Glass.TextMuted,
)

@Composable
fun glassRailItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = Glass.Cyan,
    selectedTextColor = Glass.Cyan,
    indicatorColor = Glass.Cyan.copy(alpha = 0.16f),
    unselectedIconColor = Glass.TextMuted,
    unselectedTextColor = Glass.TextMuted,
)

val GlassNavBarColor = Color(0xB8050A14)
