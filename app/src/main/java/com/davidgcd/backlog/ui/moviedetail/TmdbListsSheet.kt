package com.davidgcd.backlog.ui.moviedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.ui.components.LocalAppSnackbar
import com.davidgcd.backlog.ui.theme.Glass
import kotlinx.coroutines.CoroutineScope

/**
 * Asks to offer « ajouter aussi à une liste TMDB » for a title that was just added from a list or a search.
 * Provided once by the nav host; a screen calls [show] after its add. Does nothing without a linked TMDB account
 * or when the user has no list of their own (the watchlist is already filled by the add itself).
 */
class TmdbListsPrompt(val show: (titleKey: String, name: String) -> Unit)

val LocalTmdbListsPrompt = staticCompositionLocalOf<TmdbListsPrompt?> { null }

/** The sheet itself: only appears once the lists are known and there is at least one to offer. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TmdbListsSheet(
    repository: MovieRepository,
    titleKey: String,
    name: String,
    /** Must outlive the sheet: a tick followed at once by « Terminé » must still reach TMDB. */
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val model = remember(titleKey) { TmdbListsModel(scope, repository, titleKey) }
    val ui by model.state.collectAsState()
    val appSnackbar = LocalAppSnackbar.current
    val error = stringResource(R.string.tmdb_lists_error)

    val loaded = ui as? TmdbListsUi.Loaded
    // Nothing to offer (no account, TMDB unreachable, no list of one's own): never show an empty sheet.
    LaunchedEffect(ui) {
        if (ui is TmdbListsUi.Hidden || (loaded != null && loaded.state.lists.isEmpty())) onDismiss()
    }
    if (loaded == null || loaded.state.lists.isEmpty()) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.tmdb_lists_sheet_title, name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.tmdb_lists_sheet_hint), style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                loaded.state.lists.forEach { entry ->
                    ListChip(
                        text = entry.list.name,
                        checked = entry.contains,
                        busy = TmdbListsUi.listRow(entry.list.id) in loaded.busy,
                        onClick = { model.toggleList(entry.list.id) { appSnackbar?.show(error) } },
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}
