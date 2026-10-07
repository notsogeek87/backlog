package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.theme.Glass

/*
 * Pièces communes aux trois bibliothèques (jeux, films & séries, livres) : champ de recherche, choix
 * « Ma liste / Catalogue », état vide, chips de filtres actifs, en-tête de section, menu « ⋮ ». Les trois
 * écrans les assemblent au lieu de les recopier.
 */

/** Où cherche le champ : dans ce qu'on a déjà (filtre instantané) ou dans le catalogue en ligne pour ajouter. */
enum class SearchScope { LIBRARY, CATALOG }

@Composable
fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Glass.GlassStrong,
            unfocusedContainerColor = Glass.GlassTop,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Glass.Cyan,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
    )
}

/** « Ma liste » (filtre local, instantané) ou « Catalogue » (ajouter quelque chose de nouveau). */
@Composable
fun SearchScopePills(scope: SearchScope, onScope: (SearchScope) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassPill(stringResource(R.string.search_scope_library), selected = scope == SearchScope.LIBRARY, onClick = { onScope(SearchScope.LIBRARY) })
        GlassPill(stringResource(R.string.search_scope_catalog), selected = scope == SearchScope.CATALOG, onClick = { onScope(SearchScope.CATALOG) })
    }
}

/** Barre de progression réservée (les résultats ne sautent pas) + message d'erreur éventuel sous le champ. */
@Composable
fun SearchFeedback(isSearching: Boolean, errorText: String?) {
    if (isSearching) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Glass.Cyan, trackColor = Color.Transparent)
    } else {
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
    }
    errorText?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun NoResultsMessage(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.search_no_results),
        style = MaterialTheme.typography.bodyLarge,
        color = Glass.TextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(32.dp),
    )
}

/** État vide centré : logo, message, action principale et, si besoin, une action secondaire. */
@Composable
fun LibraryEmptyState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    fillScreen: Boolean = true,
) {
    Column(
        modifier = (if (fillScreen) modifier.fillMaxSize() else modifier.fillMaxWidth()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painter = painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(72.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
        GradientButton(text = actionLabel, onClick = onAction)
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 8.dp)) { Text(secondaryLabel) }
        }
    }
}

/** Un filtre actif, retirable d'un toucher. */
@Composable
fun RemovableChip(label: String, onClear: () -> Unit) {
    GlassPill(
        text = label,
        selected = true,
        onClick = onClear,
        trailing = {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_clear),
                modifier = Modifier.size(16.dp),
                tint = Glass.Cyan,
            )
        },
    )
}

/** Titre de section ; [trailing] (ex. le tri actif) s'aligne à droite et peut ouvrir la feuille de tri. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Glass.Text,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Libellé cliquable « Tri : … » à droite d'un titre de section. */
@Composable
fun SortLabel(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Glass.Cyan, maxLines = 1)
    }
}

/** Une entrée du menu « ⋮ ». */
data class OverflowAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

/** Menu « ⋮ » de la barre du haut : les actions secondaires (partager, classement…) y sont rangées. */
@Composable
fun OverflowMenuButton(actions: List<OverflowAction>) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                enabled = action.enabled,
                onClick = {
                    open = false
                    action.onClick()
                },
            )
        }
    }
}
