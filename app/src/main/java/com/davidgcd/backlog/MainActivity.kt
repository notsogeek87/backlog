package com.davidgcd.backlog

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.davidgcd.backlog.widget.BacklogWidget
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidgcd.backlog.notifications.NotificationIds
import com.davidgcd.backlog.notifications.NotificationPreferences
import com.davidgcd.backlog.ui.components.AppBackground
import com.davidgcd.backlog.ui.nav.BacklogNavHost
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.nav.AppAction
import com.davidgcd.backlog.ui.prefs.LocalUiPreferences
import com.davidgcd.backlog.ui.theme.BacklogTheme
import com.davidgcd.backlog.ui.theme.resolveDark
import com.lielu.githubupdater.UpdateState
import com.davidgcd.backlog.util.SharedTarget
import com.davidgcd.backlog.util.SharedText
import com.davidgcd.backlog.ui.update.AppUpdateViewModel
import com.davidgcd.backlog.ui.update.AppUpdateViewModelFactory
import com.davidgcd.backlog.ui.update.UpdatePrompt
import com.davidgcd.backlog.ui.whatsnew.WhatsNewPrompt
import com.davidgcd.backlog.ui.whatsnew.WhatsNewStore
import com.davidgcd.backlog.util.DetailLink
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** The app is French-only: force the locale so system-formatted text (dates, relative times) is French too. */
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply { setLocale(Locale.FRENCH) }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    /** Game a tapped notification asked to open; consumed by the NavHost once it has navigated. */
    private var pendingGameId by mutableStateOf<Long?>(null)

    /** Detail page a shared link asked to open; consumed by the NavHost once it has navigated. */
    private var pendingLink by mutableStateOf<DetailLink?>(null)

    /** Screen an app shortcut, the widget or shared text asked for; consumed by the NavHost once it has navigated. */
    private var pendingAction by mutableStateOf<AppAction?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Reads what an intent asks for: a notification's game, a shared detail link, a shortcut, or text shared to the app. */
    private fun handle(intent: Intent?) {
        pendingGameId = gameIdFrom(intent)
        pendingLink = DetailLink.parse(intent?.data)
            ?: intent?.getStringExtra(NotificationIds.EXTRA_TITLE_KEY)?.let { key -> DetailLink.parse("movie", key) }
        pendingAction = null
        val data = intent?.data
        when {
            data != null && data.scheme == DetailLink.SCHEME && data.host == "action" -> pendingAction = actionFrom(data.pathSegments)
            intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true -> {
                when (val target = SharedText.parse(intent.getStringExtra(Intent.EXTRA_TEXT))) {
                    is SharedTarget.Detail -> pendingLink = target.link
                    is SharedTarget.Search -> pendingAction = AppAction.Search(target.medium.toMediaType(), target.query)
                    null -> Unit
                }
            }
        }
    }

    private fun actionFrom(segments: List<String>): AppAction? = when (segments.firstOrNull()) {
        "add" -> AppAction.Search(
            when (segments.getOrNull(1)) {
                "movies" -> MediaType.MOVIES
                "books" -> MediaType.BOOKS
                else -> MediaType.GAMES
            },
            null,
        )
        "recap" -> AppAction.Recap
        "tonight" -> AppAction.Tonight
        else -> null
    }

    private fun Medium?.toMediaType(): MediaType = when (this) {
        Medium.MOVIE -> MediaType.MOVIES
        Medium.BOOK -> MediaType.BOOKS
        else -> MediaType.GAMES
    }

    private fun gameIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(NotificationIds.EXTRA_GAME_ID, NO_GAME_ID)?.takeIf { it != NO_GAME_ID }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after a config-driven recreate the tap was already handled.
        if (savedInstanceState == null) handle(intent)

        val app = application as BacklogApplication
        val repository = app.repository
        val notificationPreferences = NotificationPreferences(applicationContext)
        val whatsNewStore = WhatsNewStore(applicationContext)
        val uiPreferences = app.uiPreferences

        setContent {
            val themeMode by uiPreferences.themeMode.collectAsState()
            val dynamicColors by uiPreferences.dynamicColors.collectAsState()
            val dark = resolveDark(themeMode)
            // System bar icons follow the resolved theme (light icons on dark, dark icons on light).
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }

            val updateViewModel: AppUpdateViewModel = viewModel(
                factory = AppUpdateViewModelFactory(app.updateManager, app.updatesEnabled),
            )

            // Une seule fenêtre à la fois : les nouveautés attendent que la mise à jour proposée soit réglée.
            val updateState by updateViewModel.state.collectAsState()
            val updateDismissed by updateViewModel.dismissed.collectAsState()
            val updateModalShowing = updateViewModel.updatesEnabled && !updateDismissed && when (updateState) {
                is UpdateState.UpdateAvailable, is UpdateState.Downloading, is UpdateState.Installing -> true
                else -> false
            }

            BacklogTheme(mode = themeMode, dynamicColors = dynamicColors) {
              CompositionLocalProvider(LocalUiPreferences provides uiPreferences) {
                // Vérifie à chaque ouverture (ON_START) et propose la mise à jour avec les étapes expliquées.
                if (updateViewModel.updatesEnabled) UpdatePrompt(updateViewModel)
                WhatsNewPrompt(whatsNewStore, enabled = !updateModalShowing)
                AppBackground {
                    BacklogNavHost(
                        repository = repository,
                        notificationPreferences = notificationPreferences,
                        steamService = app.steamService,
                        metacriticService = app.metacriticService,
                        csvExportService = app.csvExportService,
                        csvImportService = app.csvImportService,
                        libraryAccountStore = app.libraryAccountStore,
                        librarySyncService = app.librarySyncService,
                        steamAuthService = app.steamAuthService,
                        gameSourceDao = app.gameSourceDao,
                        shareLinkService = app.shareLinkService,
                        movieRepository = app.movieRepository,
                        tmdbSyncService = app.tmdbSyncService,
                        bookRepository = app.bookRepository,
                        wishlistSyncService = app.wishlistSyncService,
                        updateViewModel = updateViewModel,
                        uiPreferences = uiPreferences,
                        appAction = pendingAction,
                        onAppActionHandled = { pendingAction = null },
                        openGameId = pendingGameId,
                        onOpenGameHandled = { pendingGameId = null },
                        openLink = pendingLink,
                        onOpenLinkHandled = { pendingLink = null },
                    )
                }
              }
            }
        }
    }

    /** Rafraîchit le widget quand l'app passe en arrière-plan : c'est là que la liste vient de changer. */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { runCatching { BacklogWidget().updateAll(applicationContext) } }
    }

    private companion object {
        const val NO_GAME_ID = -1L
    }
}
