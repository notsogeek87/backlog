package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.davidgcd.backlog.ui.theme.Glass

/** A book cover is 2:3: every cover takes the same box, whatever the image, so a grid never jumps. */
private const val COVER_RATIO = 2f / 3f

/**
 * The book-side [GameCover]: same rounded glass frame and shadow, but with a placeholder (the title on
 * a night-blue tile) when the catalogue has no cover or the image can't be loaded — never a broken
 * image or an empty gap. The caller sets the width (`Modifier.width(…)` or `fillMaxWidth()`); the
 * height follows from the 2:3 ratio.
 */
@Composable
fun BookCover(url: String?, title: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val frame = modifier
        .aspectRatio(COVER_RATIO)
        .shadow(8.dp, shape, ambientColor = Glass.Cyan.copy(alpha = 0.25f), spotColor = Color.Black)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, Glass.Border, shape)
    if (url == null) {
        BookPlaceholder(title, frame)
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = frame,
            loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
            error = { BookPlaceholder(title, Modifier.fillMaxSize()) },
        )
    }
}

@Composable
private fun BookPlaceholder(title: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF2A2350)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Book, contentDescription = null, tint = Glass.Cyan.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Glass.Text.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
