package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * translucent gradient + hairline gradient border + top highlight over the canvas; real blur
 * (API 31+) is only used on the artwork backdrops. Every neutral comes from the active palette
 * (sombre, clair ou AMOLED, voir ui/theme/Theme.kt) so the same components serve all three.
 */

val GlassShape = RoundedCornerShape(20.dp)
val GlassShapeSmall = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(999.dp)

/** Touch target recommended by Material / WCAG: pills look compact but answer taps over 48 dp. */
private val MinTouch = 48.dp

/** Canvas with faint cyan / violet radial washes so it never reads as flat. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bg = Glass.Bg
    val cyan = Glass.Cyan
    val wash = com.davidgcd.backlog.ui.theme.LocalGlassPalette.current.washAlpha
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(cyan.copy(alpha = wash), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Glass.Purple.copy(alpha = wash), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.2f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            },
    ) { content() }
}

fun Modifier.glass(shape: Shape = GlassShape, strong: Boolean = false): Modifier = composed {
    val fill = if (strong) Glass.GlassStrongFill else Glass.GlassFill
    val stroke = Glass.BorderBrush
    this
        .clip(shape)
        .background(fill)
        .border(1.dp, stroke, shape)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .glass(shape)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                    )
                } else {
                    Modifier
                },
            ),
    ) { content() }
}

/**
 * Cover art as a first-class element: rounded, hairline border, soft drop shadow.
 * Games pass an IGDB [imageId]; films & séries pass a full [imageUrl] (an TMDB poster) instead.
 * [contentDescription] names the cover when it is a button of its own (carousels); lists leave it null
 * because the row already carries the title.
 */
@Composable
fun GameCover(
    imageId: String?,
    width: Dp,
    modifier: Modifier = Modifier,
    size: IgdbImage.Size = IgdbImage.Size.CoverBig,
    imageUrl: String? = null,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val boxModifier = modifier
        .size(width, width * 4f / 3f)
        .shadow(10.dp, shape, ambientColor = Glass.Cyan.copy(alpha = 0.25f), spotColor = Color.Black)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, Glass.Border, shape)
    val model = imageUrl ?: imageId?.let { IgdbImage.url(it, size) }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = boxModifier,
        )
    } else {
        Box(boxModifier.then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier))
    }
}

/** Accent colours like violet / blue are too dark as text on a dark canvas (and too light on a light one): shift them toward the readable end. */
@Composable
fun readableOnDark(tint: Color): Color = if (Glass.IsDark) lerp(tint, Color.White, 0.45f) else lerp(tint, Color.Black, 0.45f)

/** Small glass pill for platforms / genres / status. [tint] left unspecified = neutral. */
@Composable
fun GlassBadge(text: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val neutral = tint == Color.Unspecified
    val base = if (neutral) Glass.Text else tint
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (neutral) Glass.Text else readableOnDark(tint),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(PillShape)
            .background(base.copy(alpha = if (neutral) 0.10f else 0.16f))
            .border(1.dp, base.copy(alpha = if (neutral) 0.20f else 0.28f), PillShape)
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
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(if (selected) tint.copy(alpha = 0.22f) else Glass.GlassTop)
            .border(1.dp, if (selected) tint.copy(alpha = 0.7f) else Glass.Border, PillShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Filter / switch pill. Announced as a radio (selected state read out by TalkBack); a pill with a
 * [trailing] icon is a removable chip and is announced as a button instead.
 */
@Composable
fun GlassPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(if (selected) Glass.Cyan.copy(alpha = 0.18f) else Glass.GlassTop)
            .border(1.dp, if (selected) Glass.Cyan.copy(alpha = 0.6f) else Glass.Border, PillShape)
            .selectable(selected = selected, role = if (trailing != null) Role.Button else Role.RadioButton, onClick = onClick)
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

/**
 * Primary CTA: accent gradient, 52 dp minimum. The label colour comes from the palette (dark navy on the
 * bright dark-theme gradient, white on the deeper light-theme one) so the contrast stays above 4.5:1 end to end.
 */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .shadow(14.dp, shape, ambientColor = Glass.Cyan.copy(alpha = 0.35f), spotColor = Glass.Cyan.copy(alpha = 0.35f))
            .clip(shape)
            .background(Glass.AccentHorizontal)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .semantics { role = Role.Button }
            .then(if (enabled) Modifier.combinedClickableCompat(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Glass.OnAccent.copy(alpha = if (enabled) 1f else 0.5f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

/** Secondary action: glass surface, optional accent colour for text (e.g. destructive). */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .glass(shape, strong = true)
            .semantics { role = Role.Button }
            .combinedClickableCompat(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (contentColor == Color.Unspecified) Glass.Text else contentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/** 0–100 score as a gradient bar; the glow is drawn inside the rounded track so nothing spills out. [description] is read by TalkBack ("Note 78 sur 100"). */
@Composable
fun GradientProgressBar(fraction: Float, modifier: Modifier = Modifier, description: String? = null) {
    val shape = RoundedCornerShape(999.dp)
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(shape)
            .background(Glass.Border)
            .semantics {
                if (description != null) {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                }
            },
    ) {
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

/**
 * Compact stat tile: big number, muted label. With [onClick] it behaves as a filter toggle (the tile of the
 * active filter is [selected]); without, it is a plain read-out.
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Glass.Cyan,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = GlassShape
    val interactive = if (onClick != null) {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Box(
        modifier = modifier
            .glass(shape)
            .then(if (selected) Modifier.border(1.5.dp, accent.copy(alpha = 0.8f), shape) else Modifier)
            .then(if (selected) Modifier.background(accent.copy(alpha = 0.10f)) else Modifier)
            .then(interactive),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = readableOnDark(accent))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One choice of the quick status menu opened from a row's status badge. */
data class StatusChoice(val label: String, val tint: Color, val selected: Boolean, val onSelect: () -> Unit)

/**
 * The one list row used by backlog, search and discover: big cover, title, platform badges,
 * release date and (when IGDB has one) the rating bar. Data is optional — nothing is invented.
 * [statusChoices] turns the status badge into a two-tap status changer; [onLongClick] opens the row's quick actions.
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
    statusTint: Color = Color.Unspecified,
    statusChoices: List<StatusChoice>? = null,
    coverUrl: String? = null,
    /** Replaces the number next to the rating bar (TMDB's 0–10 scale); [rating] still drives the bar (0–100). */
    ratingText: String? = null,
    /** The user's own note, shown as a badge beside the status. */
    userRatingLabel: String? = null,
    /** Replaces the game cover (books pass their own 2:3 cover with its placeholder); null keeps [GameCover]. */
    cover: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (cover != null) cover() else GameCover(coverImageId, width = 64.dp, size = IgdbImage.Size.CoverSmall, imageUrl = coverUrl)
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
                if (statusLabel != null || userRatingLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (statusLabel != null) {
                            if (statusChoices != null) StatusBadgeMenu(statusLabel, statusTint, statusChoices) else GlassBadge(statusLabel, tint = statusTint)
                        }
                        if (userRatingLabel != null) GlassBadge(userRatingLabel, tint = Glass.Amber)
                    }
                }
                if (!meta.isNullOrEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientProgressBar(
                            (rating / 100.0).toFloat(),
                            modifier = Modifier.weight(1f),
                            description = null,
                        )
                        Text(ratingText ?: "${rating.toInt()}", style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted)
                    }
                }
            }
            trailing()
        }
    }
}

/** Status badge that opens a menu of every status: changing it takes two taps, no detail screen. */
@Composable
fun StatusBadgeMenu(label: String, tint: Color, choices: List<StatusChoice>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        GlassBadge(
            text = label,
            tint = tint,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .selectable(selected = false, role = Role.DropdownList, onClick = { open = true })
                .semantics { stateDescription = label },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    leadingIcon = { RadioButton(selected = choice.selected, onClick = null) },
                    onClick = {
                        open = false
                        choice.onSelect()
                    },
                )
            }
        }
    }
}

/** Glass top bar: transparent so the canvas / artwork shows through. */
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

/** Hides a decorative box from TalkBack. */
fun Modifier.decorative(): Modifier = clearAndSetSemantics { }
