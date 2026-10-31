package com.davidgcd.backlog

import android.app.Application
import com.davidgcd.backlog.data.library.SteamAutoSyncScheduler
import com.davidgcd.backlog.notifications.ReleaseReminderScheduler
import com.lielu.githubupdater.UpdateConfig
import com.lielu.githubupdater.UpdateManager

/**
 * Entry point of the process. The service graph lives in [AppContainer] (built lazily, on first use);
 * the properties below only forward to it, so screens and workers keep a single, stable access point.
 */
class BacklogApplication : Application() {

    lateinit var container: AppContainer
        private set

    val repository get() = container.repository
    val steamService get() = container.steamService
    val metacriticService get() = container.metacriticService
    val csvExportService get() = container.csvExportService
    val csvImportService get() = container.csvImportService
    val libraryAccountStore get() = container.libraryAccountStore
    val librarySyncService get() = container.librarySyncService
    val wishlistSyncService get() = container.wishlistSyncService
    val steamAuthService get() = container.steamAuthService
    val gameSourceDao get() = container.gameSourceDao
    val shareLinkService get() = container.shareLinkService
    val movieRepository get() = container.movieRepository
    val tmdbSyncService get() = container.tmdbSyncService
    val bookRepository get() = container.bookRepository

    /** Prix Steam des jeux souhaités, pour l'alerte « promo ». */
    val steamPriceService get() = container.steamPriceService

    /** Apparence (thème, couleurs dynamiques), aperçu des bandes-annonces, drapeaux « déjà demandé ». */
    val uiPreferences get() = container.uiPreferences

    /** Mises à jour via les GitHub Releases de CE dépôt ; partagé entre la fenêtre de lancement et les Réglages. */
    val updateManager: UpdateManager by lazy {
        UpdateManager(this, UpdateConfig(githubOwner = "notsogeek87", githubRepository = "backlog"))
    }

    /**
     * Désactivé sur le build Play Store (BuildConfig.SELF_UPDATE) et sur tout applicationId à suffixe
     * (ex. `.staging`) : un APK d'un autre applicationId ne peut pas mettre à jour la release de production.
     */
    val updatesEnabled: Boolean
        get() = BuildConfig.SELF_UPDATE && packageName == "com.davidgcd.backlog"

    override fun onCreate() {
        super.onCreate()
        java.util.Locale.setDefault(java.util.Locale.FRENCH)
        container = AppContainer(this)
        ReleaseReminderScheduler.schedule(this)
        SteamAutoSyncScheduler.schedule(this)
    }
}
