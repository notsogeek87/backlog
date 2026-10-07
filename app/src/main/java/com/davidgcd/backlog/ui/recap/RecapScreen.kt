package com.davidgcd.backlog.ui.recap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.model.YearRecap
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.FrenchLabels
import kotlinx.coroutines.launch
import java.io.File

/** « Mon année » : le bilan de l'année, partageable en image (la carte du haut) ou en texte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(viewModel: RecapViewModel, onBack: () -> Unit, onAddItems: () -> Unit) {
    val year by viewModel.year.collectAsState()
    val years by viewModel.years.collectAsState()
    val recap by viewModel.recap.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.recap_card_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (years.size > 1) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.forEach { y -> GlassPill(y.toString(), selected = y == year, onClick = { viewModel.selectYear(y) }) }
                }
            }
            val current = recap
            if (current == null || current.isEmpty) {
                Text(stringResource(R.string.recap_empty, year), style = MaterialTheme.typography.bodyLarge, color = Glass.TextMuted)
                GradientButton(stringResource(R.string.home_empty_cta), onClick = onAddItems, modifier = Modifier.fillMaxWidth())
            } else {
                // The card is drawn into a layer as well, so « Partager l'image » sends exactly what is on screen.
                Box(
                    modifier = Modifier.drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
                ) { RecapCard(current) }
                GradientButton(
                    text = stringResource(R.string.recap_share_image),
                    onClick = {
                        scope.launch {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            shareImage(context, bitmap, recapText(context, current))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassButton(
                    text = stringResource(R.string.recap_share_text),
                    onClick = { shareText(context, recapText(context, current)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** La carte du bilan : volontairement indépendante du thème (fond nuit, texte clair) pour que l'image partagée soit toujours la même. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecapCard(recap: YearRecap) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF07122A), Color(0xFF1B1448), Color(0xFF0B3A4A))))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.recap_card_year, recap.year), color = Color(0xFF7DE3F4), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Column {
            Text(recap.finished.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 64.sp)
            Text(stringResource(R.string.recap_finished_label), color = Color(0xFFB4BCD0), style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecapStat(recap.gamesFinished.toString(), stringResource(R.string.recap_games), Modifier.weight(1f))
            RecapStat((recap.moviesWatched + recap.seriesWatched).toString(), stringResource(R.string.recap_movies), Modifier.weight(1f))
            RecapStat(recap.booksRead.toString(), stringResource(R.string.recap_books), Modifier.weight(1f))
        }
        val details = buildList {
            if (recap.minutesWatched > 0) add(stringResource(R.string.recap_hours_watched, recap.minutesWatched / 60))
            if (recap.pagesRead > 0) add(stringResource(R.string.recap_pages_read, recap.pagesRead))
            add(stringResource(R.string.recap_added, recap.added))
        }
        Text(details.joinToString(" · "), color = Color(0xFFB4BCD0), style = MaterialTheme.typography.bodyMedium)
        if (recap.topGenres.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recap_top_genres), color = Color(0xFF7DE3F4), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recap.topGenres.forEach { (genre, count) ->
                        Text(
                            "${FrenchLabels.genre(genre)} · $count",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        if (recap.picks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.recap_picks), color = Color(0xFF7DE3F4), fontWeight = FontWeight.SemiBold)
                recap.picks.forEach { pick ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("★", color = Color(0xFFF59E0B))
                        Text(pick.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(stringResource(mediumRes(pick.medium)), color = Color(0xFFB4BCD0), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Text(stringResource(R.string.app_name), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RecapStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text(label, color = Color(0xFFB4BCD0), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private fun mediumRes(medium: Medium): Int = when (medium) {
    Medium.GAME -> R.string.media_game_one
    Medium.MOVIE -> R.string.media_movie_one
    Medium.BOOK -> R.string.media_book_one
}

private fun recapText(context: Context, recap: YearRecap): String = buildString {
    appendLine(context.getString(R.string.recap_share_header, recap.year))
    appendLine(context.getString(R.string.recap_share_line, recap.gamesFinished, recap.moviesWatched + recap.seriesWatched, recap.booksRead))
    if (recap.topGenres.isNotEmpty()) appendLine(context.getString(R.string.recap_top_genres) + " : " + recap.topGenres.joinToString(", ") { FrenchLabels.genre(it.first) })
    recap.picks.forEach { appendLine("★ ${it.title}") }
}.trim()

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.recap_share_chooser)))
}

/** Écrit la carte dans le cache de l'app et la partage par FileProvider ; en cas d'échec, le texte part quand même. */
private fun shareImage(context: Context, bitmap: Bitmap, text: String) {
    try {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "backlog-recap.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.recap_share_chooser)))
    } catch (t: Exception) {
        shareText(context, text)
    }
}
