package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.theme.Glass

/** Un statut proposé par le long appui sur « + » : « Ajouter comme… ». */
data class AddChoice(val label: String, val onAdd: () -> Unit)

/**
 * Le « + » des résultats : un toucher ajoute avec le statut par défaut, un appui long propose de choisir
 * (Backlog, Souhaité…). Un menu « Ajouter comme » est aussi annoncé comme action personnalisée à TalkBack.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddButton(contentDescription: String, onAdd: () -> Unit, choices: List<AddChoice> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onLongClickLabel = if (choices.isNotEmpty()) stringResource(R.string.add_as_title) else null,
                    onLongClick = if (choices.isNotEmpty()) ({ open = true }) else null,
                    onClick = onAdd,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = contentDescription, tint = Glass.Cyan)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(stringResource(R.string.add_as_title), modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp), color = Glass.TextMuted)
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        open = false
                        choice.onAdd()
                    },
                )
            }
        }
    }
}

/** Une recherche demandée de l'extérieur (raccourci d'icône, lien ou texte partagé vers l'app) : média visé et texte à chercher. */
data class SearchRequest(val media: MediaType, val query: String?)
