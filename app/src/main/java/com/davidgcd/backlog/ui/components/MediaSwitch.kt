package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R

/** The worlds of the app: games (IGDB), films & séries (TMDB) and books (Open Library). */
enum class MediaType { GAMES, MOVIES, BOOKS }

/** Jeux / Films & séries / Livres switch, shown above the lists of screens that serve both (Discover). */
@Composable
fun MediaSwitch(selected: MediaType, onSelect: (MediaType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassPill(stringResource(R.string.media_games), selected = selected == MediaType.GAMES, onClick = { onSelect(MediaType.GAMES) })
        GlassPill(stringResource(R.string.media_movies), selected = selected == MediaType.MOVIES, onClick = { onSelect(MediaType.MOVIES) })
        GlassPill(stringResource(R.string.media_books), selected = selected == MediaType.BOOKS, onClick = { onSelect(MediaType.BOOKS) })
    }
}
