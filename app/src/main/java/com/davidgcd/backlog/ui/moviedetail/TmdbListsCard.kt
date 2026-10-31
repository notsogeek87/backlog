package com.davidgcd.backlog.ui.moviedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.theme.Glass

/**
 * « Mes listes TMDB » — the user's TMDB watchlist first (the one most used), then each list they created on
 * themoviedb.org, as ticks to flip. Works for a title already saved here or only seen in a search. Hidden when
 * no TMDB account is linked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TmdbListsCard(
    ui: TmdbListsUi,
    onToggleWatchlist: () -> Unit,
    onToggleList: (Long) -> Unit,
) {
    if (ui is TmdbListsUi.Hidden) return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tmdb_lists_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (ui) {
                TmdbListsUi.Hidden -> Unit
                TmdbListsUi.Loading -> CircularProgressIndicator(color = Glass.Cyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                is TmdbListsUi.Loaded -> {
                    val busy = ui.busy
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListChip(
                            text = stringResource(R.string.tmdb_lists_watchlist),
                            checked = ui.state.watchlist,
                            busy = TmdbListsUi.WATCHLIST_ROW in busy,
                            onClick = onToggleWatchlist,
                        )
                        ui.state.lists.forEach { entry ->
                            ListChip(
                                text = entry.list.name,
                                checked = entry.contains,
                                busy = TmdbListsUi.listRow(entry.list.id) in busy,
                                onClick = { onToggleList(entry.list.id) },
                            )
                        }
                    }
                    if (ui.state.lists.isEmpty()) {
                        Text(stringResource(R.string.tmdb_lists_none), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
                    }
                }
            }
        }
    }
}

/** A pill that is a checkbox: ticked = the title is in that list. Ignores taps while its write is in flight. */
@Composable
private fun ListChip(text: String, checked: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (checked) Glass.Cyan.copy(alpha = 0.18f) else Glass.GlassTop)
            .border(1.dp, if (checked) Glass.Cyan.copy(alpha = 0.6f) else Glass.Border, shape)
            .toggleable(value = checked, enabled = !busy, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            busy -> CircularProgressIndicator(color = Glass.Cyan, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            checked -> Icon(Icons.Filled.Check, contentDescription = null, tint = Glass.Cyan, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (checked) Glass.Cyan else Glass.Text, maxLines = 1)
    }
}
