package com.davidgcd.backlog.ui.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R

/** Fenêtre « Nouveautés » affichée une seule fois après une mise à jour, listant ce qui a changé depuis la version précédente. */
@Composable
fun WhatsNewPrompt(store: WhatsNewStore, enabled: Boolean = true) {
    var entries by remember { mutableStateOf<List<ChangelogEntry>>(emptyList()) }
    // Waits while another dialog (a proposed update) is up: one window at a time.
    LaunchedEffect(enabled) { if (enabled) entries = store.takeUnseen() }
    if (entries.isEmpty()) return

    AlertDialog(
        onDismissRequest = { entries = emptyList() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { entries = emptyList() }) { Text(stringResource(R.string.whats_new_close)) }
        },
    )
}
