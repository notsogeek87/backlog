package com.davidgcd.backlog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidgcd.backlog.notifications.NotificationIds
import com.davidgcd.backlog.notifications.NotificationPreferences
import com.davidgcd.backlog.ui.components.AppBackground
import com.davidgcd.backlog.ui.nav.BacklogNavHost
import com.davidgcd.backlog.ui.theme.BacklogTheme
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingGameId = gameIdFrom(intent)
        pendingLink = DetailLink.parse(intent.data)
    }

    private fun gameIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(NotificationIds.EXTRA_GAME_ID, NO_GAME_ID)?.takeIf { it != NO_GAME_ID }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after a config-driven recreate the tap was already handled.
        if (savedInstanceState == null) {
            pendingGameId = gameIdFrom(intent)
            pendingLink = DetailLink.parse(intent.data)
        }
        // Dark-only glass UI: light system bar icons regardless of the device theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val app = application as BacklogApplication
        val repository = app.repository
        val notificationPreferences = NotificationPreferences(applicationContext)
        val whatsNewStore = WhatsNewStore(applicationContext)

        setContent {
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* release reminders simply won't post until granted */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val updateViewModel: AppUpdateViewModel = viewModel(
                factory = AppUpdateViewModelFactory(app.updateManager, app.updatesEnabled),
            )

            BacklogTheme {
                // Vérifie à chaque ouverture (ON_START) et propose la mise à jour avec les étapes expliquées.
                if (updateViewModel.updatesEnabled) UpdatePrompt(updateViewModel)
                WhatsNewPrompt(whatsNewStore)
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
                        openGameId = pendingGameId,
                        onOpenGameHandled = { pendingGameId = null },
                        openLink = pendingLink,
                        onOpenLinkHandled = { pendingLink = null },
                    )
                }
            }
        }
    }

    private companion object {
        const val NO_GAME_ID = -1L
    }
}
