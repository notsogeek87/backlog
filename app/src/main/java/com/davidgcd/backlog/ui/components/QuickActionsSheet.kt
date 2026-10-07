package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.ui.theme.Glass

/** Une action de la feuille d'actions rapides d'une ligne (appui long). */
data class QuickAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val destructive: Boolean = false)

/**
 * Feuille ouverte par un appui long sur une ligne : le statut en un toucher, puis les actions courantes
 * (archiver, classer, partager…), sans passer par la fiche.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickActionsSheet(
    title: String,
    statusLabel: String,
    statusChoices: List<StatusChoice>,
    actions: List<QuickAction>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = Glass.Text,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (statusChoices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(statusLabel, style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        statusChoices.forEach { choice ->
                            GlassBadgeButton(choice.label, choice.tint, selected = choice.selected) {
                                choice.onSelect()
                                onDismiss()
                            }
                        }
                    }
                }
            }
            Column {
                actions.forEach { action ->
                    val color = if (action.destructive) MaterialTheme.colorScheme.error else Glass.Text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable {
                                onDismiss()
                                action.onClick()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                        Text(action.label, color = color, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
