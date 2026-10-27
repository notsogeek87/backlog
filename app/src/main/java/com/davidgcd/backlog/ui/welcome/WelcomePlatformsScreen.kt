package com.davidgcd.backlog.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass

/**
 * Premier lancement : invite à connecter Steam, les jeux Android et TMDB avant de commencer.
 * [platformsContent] est la même section « Mes plateformes » que dans les Réglages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomePlatformsScreen(
    onDone: () -> Unit,
    platformsContent: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.welcome_platforms_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.welcome_platforms_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = Glass.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            platformsContent()
            GradientButton(
                text = stringResource(R.string.welcome_platforms_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.welcome_platforms_later),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}
