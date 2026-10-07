package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Une action déclenchée par un balayage (statut suivant, archivage). */
data class SwipeAction(val label: String, val icon: ImageVector, val tint: Color, val onTrigger: () -> Unit)

/**
 * Ligne balayable : vers la droite = [startAction] (statut suivant), vers la gauche = [endAction] (archiver).
 * La ligne revient toujours à sa place : l'action est annulable par le Snackbar de l'appelant. Les mêmes
 * actions sont exposées à TalkBack (actions personnalisées), car un geste ne se découvre pas à l'oreille.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionRow(
    startAction: SwipeAction?,
    endAction: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val start by rememberUpdatedState(startAction)
    val end by rememberUpdatedState(endAction)
    if (startAction == null && endAction == null) {
        Box(modifier) { content() }
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> start?.onTrigger()
                SwipeToDismissBoxValue.EndToStart -> end?.onTrigger()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOfNotNull(
                startAction?.let { a -> CustomAccessibilityAction(a.label) { a.onTrigger(); true } },
                endAction?.let { a -> CustomAccessibilityAction(a.label) { a.onTrigger(); true } },
            )
        },
        enableDismissFromStartToEnd = startAction != null,
        enableDismissFromEndToStart = endAction != null,
        backgroundContent = {
            val action = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> startAction
                SwipeToDismissBoxValue.EndToStart -> endAction
                SwipeToDismissBoxValue.Settled -> null
            }
            if (action != null) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(GlassShape)
                        .background(action.tint.copy(alpha = 0.28f))
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
                ) {
                    Icon(action.icon, contentDescription = null, tint = readableOnDark(action.tint))
                    Text(
                        action.label,
                        modifier = Modifier.padding(start = 8.dp),
                        color = readableOnDark(action.tint),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { content() }
}
