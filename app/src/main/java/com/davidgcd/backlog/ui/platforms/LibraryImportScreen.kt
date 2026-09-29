package com.davidgcd.backlog.ui.platforms

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.library.ImportResult
import com.davidgcd.backlog.data.library.ItemStatus
import com.davidgcd.backlog.data.library.LibraryImportItem
import com.davidgcd.backlog.data.library.LibraryProgress
import com.davidgcd.backlog.data.library.SyncPreview
import com.davidgcd.backlog.ui.components.GlassBadge
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.GradientProgressBar
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryImportScreen(viewModel: LibraryImportViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.library_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.phase) {
                ImportPhase.WORKING -> ProgressPanel(state.progress)
                ImportPhase.PREVIEW -> PreviewPanel(
                    preview = state.preview!!,
                    selected = state.selected,
                    selectedCount = state.selectedCount,
                    onToggle = viewModel::toggle,
                    onToggleAllNew = viewModel::toggleAllNew,
                    onImport = viewModel::importSelected,
                )
                ImportPhase.DONE -> DonePanel(state.result!!, state.preview, onDone = onBack)
                ImportPhase.ERROR -> MessagePanel(
                    message = stringResource(state.error?.messageRes() ?: R.string.library_error_no_games),
                    onRetry = viewModel::start,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun ProgressPanel(progress: LibraryProgress?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val determinate = progress != null && progress.total > 0
        Text(
            text = when {
                progress == null || progress.stage == LibraryProgress.Stage.FETCHING -> stringResource(R.string.library_stage_fetching)
                progress.stage == LibraryProgress.Stage.MATCHING -> stringResource(R.string.library_stage_matching, progress.done, progress.total)
                else -> stringResource(R.string.library_stage_importing, progress.done, progress.total)
            },
            color = Glass.Text,
            style = MaterialTheme.typography.titleMedium,
        )
        Box(modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
            // Unknown length (fetching) shows a full, static bar rather than a fake percentage.
            GradientProgressBar(if (determinate) progress!!.done.toFloat() / progress.total else 1f)
        }
    }
}

@Composable
private fun PreviewPanel(
    preview: SyncPreview,
    selected: Set<String>,
    selectedCount: Int,
    onToggle: (String) -> Unit,
    onToggleAllNew: () -> Unit,
    onImport: () -> Unit,
) {
    val newItems = preview.items(ItemStatus.NEW)
    val uncertain = preview.items(ItemStatus.UNCERTAIN)
    val present = preview.items.filter { it.status == ItemStatus.LINKED || it.status == ItemStatus.EXISTING }
    val unmatched = preview.items(ItemStatus.UNMATCHED)

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") { SummaryRow(preview) }

            if (newItems.isNotEmpty()) {
                item(key = "h_new") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(stringResource(R.string.library_section_new) + " (${newItems.size})", Modifier.weight(1f))
                        val allSelected = newItems.all { it.key in selected }
                        TextButton(onClick = onToggleAllNew) {
                            Text(stringResource(if (allSelected) R.string.library_select_none else R.string.library_select_all))
                        }
                    }
                }
                items(newItems, key = { "n_${it.key}" }) { ItemRow(it, it.key in selected, onToggle) }
            }

            if (uncertain.isNotEmpty()) {
                item(key = "h_unc") {
                    Column {
                        SectionHeader(stringResource(R.string.library_section_uncertain) + " (${uncertain.size})")
                        Text(stringResource(R.string.library_section_uncertain_hint), style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted)
                    }
                }
                items(uncertain, key = { "u_${it.key}" }) { ItemRow(it, it.key in selected, onToggle) }
            }

            if (present.isNotEmpty()) {
                item(key = "h_present") { SectionHeader(stringResource(R.string.library_section_present) + " (${present.size})") }
                items(present, key = { "p_${it.key}" }) { ItemRow(it, checked = false, onToggle = null) }
            }

            if (unmatched.isNotEmpty()) {
                item(key = "unmatched") {
                    Column {
                        SectionHeader(stringResource(R.string.library_section_unmatched) + " (${unmatched.size})")
                        Text(
                            stringResource(R.string.library_unmatched_hint, unmatched.take(5).joinToString(", ") { it.game.name } + if (unmatched.size > 5) "…" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = Glass.TextMuted,
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(16.dp)) {
            if (selectedCount > 0) {
                GradientButton(
                    text = pluralText(R.plurals.library_import_action, selectedCount, selectedCount),
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(stringResource(R.string.library_up_to_date), color = Glass.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SummaryRow(preview: SyncPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralText(R.plurals.library_games_found, preview.ownedCount, preview.ownedCount),
            style = MaterialTheme.typography.titleMedium,
            color = Glass.Text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(preview.alreadyCount.toString(), stringResource(R.string.library_section_present), Modifier.weight(1f), Glass.Green)
            StatCard(preview.newCount.toString(), stringResource(R.string.library_section_new), Modifier.weight(1f), Glass.Cyan)
        }
        Text(
            stringResource(
                R.string.library_last_sync_at,
                DateUtils.formatDateTime(
                    androidx.compose.ui.platform.LocalContext.current,
                    preview.syncedAt,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
                ),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Glass.TextMuted,
        )
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp))
}

@Composable
private fun ItemRow(item: LibraryImportItem, checked: Boolean, onToggle: ((String) -> Unit)?) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle?.let { toggle -> { toggle(item.key) } },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onToggle != null) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle(item.key) },
                    colors = CheckboxDefaults.colors(checkedColor = Glass.Cyan, uncheckedColor = Glass.TextMuted),
                )
            }
            item.game.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(84.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(8.dp)),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.game.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Glass.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val playtime = formatPlaytime(item.game.playtimeMinutes) ?: stringResource(R.string.library_never_played)
                val status = when {
                    item.status == ItemStatus.UNCERTAIN -> stringResource(R.string.library_status_looks_like, item.matchedName.orEmpty())
                    item.status == ItemStatus.NEW -> stringResource(R.string.library_status_new)
                    else -> stringResource(R.string.library_status_present)
                }
                Text("$playtime · $status", style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.previouslyRemoved) GlassBadge(stringResource(R.string.library_status_removed_earlier), tint = Glass.Amber)
            }
        }
    }
}

@Composable
private fun DonePanel(result: ImportResult, preview: SyncPreview?, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            pluralText(R.plurals.library_done_added, result.added, result.added),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Glass.Green,
        )
        Text(pluralText(R.plurals.library_done_present, result.alreadyPresent, result.alreadyPresent), color = Glass.Text)
        Text(stringResource(R.string.library_done_no_duplicates), color = Glass.Text)
        if (result.failedNames.isNotEmpty()) {
            Text(
                stringResource(R.string.library_done_failed, result.failedNames.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Amber,
            )
        }
        Box(modifier = Modifier.padding(top = 12.dp)) {
            GradientButton(stringResource(R.string.library_done_close), onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MessagePanel(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Glass.Text, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(stringResource(R.string.action_back), onBack, Modifier.weight(1f))
            GradientButton(stringResource(R.string.action_retry), onRetry, Modifier.weight(1f))
        }
    }
}
