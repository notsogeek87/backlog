package com.davidgcd.backlog.ui.platforms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.tmdb.TmdbError
import com.davidgcd.backlog.data.tmdb.TmdbException
import com.davidgcd.backlog.data.tmdb.TmdbSyncResult
import com.davidgcd.backlog.data.tmdb.TmdbSyncService
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface TmdbImportState {
    data object Running : TmdbImportState
    data class Done(val result: TmdbSyncResult) : TmdbImportState
    data class Failed(val error: TmdbError) : TmdbImportState
}

/** Imports the TMDB watchlist and ratings as soon as the screen opens; the result stays on screen. */
class TmdbImportViewModel(private val sync: TmdbSyncService) : ViewModel() {
    private val _state = MutableStateFlow<TmdbImportState>(TmdbImportState.Running)
    val state: StateFlow<TmdbImportState> = _state

    init {
        run()
    }

    fun run() {
        _state.value = TmdbImportState.Running
        viewModelScope.launch {
            _state.value = try {
                TmdbImportState.Done(sync.sync())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val error = (t as? TmdbException)?.error ?: TmdbError.UNKNOWN
                AppLogger.network.warn("TMDB import failed: $error — ${t.message}")
                TmdbImportState.Failed(error)
            }
        }
    }
}

class TmdbImportViewModelFactory(private val sync: TmdbSyncService) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == TmdbImportViewModel::class.java)
        return TmdbImportViewModel(sync) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmdbImportScreen(
    viewModel: TmdbImportViewModel,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.tmdb_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val current = state) {
                is TmdbImportState.Running -> {
                    CircularProgressIndicator(color = Glass.Cyan)
                    Text(
                        stringResource(R.string.tmdb_import_running),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                is TmdbImportState.Failed -> {
                    Text(stringResource(current.error.messageRes()), textAlign = TextAlign.Center)
                    if (current.error == TmdbError.NOT_SIGNED_IN) {
                        GradientButton(
                            text = stringResource(R.string.tmdb_reconnect),
                            onClick = onReconnect,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    } else {
                        GradientButton(
                            text = stringResource(R.string.action_retry),
                            onClick = viewModel::run,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                is TmdbImportState.Done -> {
                    val r = current.result
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (r.added == 0 && r.updated == 0) stringResource(R.string.tmdb_import_up_to_date)
                                else pluralText(R.plurals.tmdb_import_added, r.added, r.added),
                                style = MaterialTheme.typography.titleMedium,
                                color = Glass.Text,
                            )
                            if (r.updated > 0) {
                                Text(pluralText(R.plurals.tmdb_import_updated, r.updated, r.updated), color = Glass.TextMuted)
                            }
                            Text(stringResource(R.string.tmdb_import_rated, r.ratedCount), color = Glass.TextMuted)
                            Text(stringResource(R.string.tmdb_import_watchlist, r.watchlistCount), color = Glass.TextMuted)
                        }
                    }
                    GlassButton(
                        text = stringResource(R.string.library_done_close),
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
