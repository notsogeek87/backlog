package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R

/** What the share button sends: the whole library (one link, a tab per typology) or only the current tab. */
@Composable
fun ShareScopeDialog(
    tabHintRes: Int,
    onLibrary: () -> Unit,
    onTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    @Composable
    fun Choice(titleRes: Int, hintRes: Int, onClick: () -> Unit) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(hintRes), style = MaterialTheme.typography.bodySmall)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_scope_title)) },
        text = {
            Column {
                Choice(R.string.share_scope_library, R.string.share_scope_library_hint, onLibrary)
                Choice(R.string.share_scope_tab, tabHintRes, onTab)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.share_scope_cancel)) } },
    )
}
