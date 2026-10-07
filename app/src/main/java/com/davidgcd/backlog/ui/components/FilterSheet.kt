package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.theme.Glass

/** Un choix (puce) d'une section de la feuille de filtres. */
data class SheetChoice(val label: String, val selected: Boolean, val onClick: () -> Unit)

/** Une section de la feuille : une rangée de puces à choix unique, ou un interrupteur. */
sealed interface SheetSection {
    data class Choices(val title: String, val choices: List<SheetChoice>) : SheetSection
    data class Toggle(val title: String, val checked: Boolean, val onChange: (Boolean) -> Unit) : SheetSection
}

/**
 * « Filtrer et trier » : une seule feuille pour le tri et tous les filtres, des puces plutôt qu'un menu à
 * radios qui défile. Les changements s'appliquent tout de suite derrière la feuille ; le bouton du bas annonce
 * combien d'éléments sont affichés et la ferme.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSortSheet(
    sections: List<SheetSection>,
    resultLabel: String,
    onReset: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = Glass.Text,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filter_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.filter_reset)) }
                }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                sections.forEach { section ->
                    when (section) {
                        is SheetSection.Choices -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(section.title, style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                section.choices.forEach { choice ->
                                    GlassPill(choice.label, selected = choice.selected, onClick = choice.onClick)
                                }
                            }
                        }
                        is SheetSection.Toggle -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = section.checked, role = Role.Switch, onValueChange = section.onChange)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(section.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = section.checked, onCheckedChange = null)
                        }
                    }
                }
            }
            GradientButton(
                text = resultLabel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}
