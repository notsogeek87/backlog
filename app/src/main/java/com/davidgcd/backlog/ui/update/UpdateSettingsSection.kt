package com.davidgcd.backlog.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.BuildConfig
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.components.GlassButton
import com.lielu.githubupdater.UpdateState

/** Bloc « Mises à jour » des Réglages : version installée, résultat de la dernière vérification et bouton manuel. */
@Composable
fun UpdateSettingsSection(viewModel: AppUpdateViewModel) {
    if (!viewModel.updatesEnabled) return
    val state by viewModel.state.collectAsState()
    val userStarted by viewModel.userStarted.collectAsState()

    val status = when (val s = state) {
        UpdateState.Checking -> stringResource(R.string.settings_update_checking)
        UpdateState.UpToDate -> stringResource(R.string.settings_update_up_to_date, BuildConfig.VERSION_NAME)
        is UpdateState.UpdateAvailable -> stringResource(R.string.update_available_intro, s.update.versionName)
        is UpdateState.Error -> if (userStarted) updateErrorMessage(s.error) else null
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_section_updates),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        GlassButton(
            text = stringResource(R.string.settings_check_update),
            onClick = viewModel::checkManually,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = status ?: stringResource(R.string.settings_update_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
