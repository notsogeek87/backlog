package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.ui.theme.Glass

/**
 * Le fond d'une fiche : la jaquette floutée (flou réel dès Android 12), voilée de sa couleur dominante puis fondue
 * dans le fond de l'app. Chaque fiche prend ainsi l'ambiance de son image au lieu d'un cyan/violet fixe.
 */
@Composable
fun DetailBackdrop(imageUrl: String?, modifier: Modifier = Modifier) {
    if (imageUrl == null) return
    val tint = rememberDominantColor(imageUrl, fallback = Glass.Cyan).softened()
    Box(modifier = modifier.fillMaxWidth().height(460.dp).clipToBounds()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().scale(1.2f).blur(28.dp).alpha(if (Glass.IsDark) 0.5f else 0.35f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), Glass.Bg))),
        )
    }
}
