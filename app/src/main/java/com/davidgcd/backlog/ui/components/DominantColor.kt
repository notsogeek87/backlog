package com.davidgcd.backlog.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La couleur qui ressort de l'image [imageUrl] (jaquette, affiche), pour teinter l'ambiance de la fiche :
 * chaque fiche prend ainsi sa couleur au lieu du cyan/violet fixe. Tant que l'image n'est pas lue (ou si elle
 * n'a pas de couleur nette), c'est [fallback]. L'image vient du cache de Coil : pas de téléchargement en plus.
 */
@Composable
fun rememberDominantColor(imageUrl: String?, fallback: Color): Color {
    val context = LocalContext.current
    var color by remember(imageUrl) { mutableStateOf(fallback) }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).size(96).build()
        val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val swatch = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(12).generate()
            palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        }
        if (swatch != null) color = Color(swatch.rgb)
    }
    return color
}

/** La couleur dominante ramenée à une teinte douce : jamais plus lumineuse que [maxLuma] pour que le texte garde son contraste. */
fun Color.softened(maxLuma: Float = 0.55f): Color {
    val luma = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    if (luma <= maxLuma) return this
    val k = maxLuma / luma
    return Color(red * k, green * k, blue * k, alpha)
}
