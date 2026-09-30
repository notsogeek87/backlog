package com.davidgcd.backlog.ui.platforms

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.tmdb.TmdbSyncService
import com.davidgcd.backlog.data.library.LibraryAccount
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.WishlistSyncResult
import com.davidgcd.backlog.data.library.WishlistSyncService
import com.davidgcd.backlog.data.library.LibraryError
import com.davidgcd.backlog.data.library.android.AndroidLibraryProvider
import com.davidgcd.backlog.data.library.toLibraryException
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import com.davidgcd.backlog.data.local.GameSourceDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WishlistUiState {
    data object Idle : WishlistUiState
    data object Working : WishlistUiState
    data class Done(val result: WishlistSyncResult) : WishlistUiState
    data class Failed(val error: LibraryError) : WishlistUiState
}

/** "My platforms" cards in Settings: Steam (account) and the games installed on this phone. */
class PlatformsViewModel(
    private val accounts: LibraryAccountStore,
    private val sourceDao: GameSourceDao,
    private val tmdbSync: TmdbSyncService,
    private val wishlistSync: WishlistSyncService,
) : ViewModel() {
    val steam: StateFlow<LibraryAccount?> = accounts.observe(LibraryProviders.STEAM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** TMDB account (films & séries): signed in through the login WebView, imported on demand. */
    val tmdb: StateFlow<LibraryAccount?> = accounts.observe(LibraryProviders.TMDB)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val android: StateFlow<LibraryAccount?> = accounts.observe(LibraryProviders.ANDROID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _wishlist = MutableStateFlow<WishlistUiState>(WishlistUiState.Idle)
    val wishlist: StateFlow<WishlistUiState> = _wishlist
    private var wishlistJob: Job? = null

    /** Mirrors the Steam wishlist into the backlog (status « Souhaité »). No preview step: it never deletes. */
    fun syncWishlist() {
        if (wishlistJob?.isActive == true) return
        _wishlist.value = WishlistUiState.Working
        wishlistJob = viewModelScope.launch {
            _wishlist.value = try {
                WishlistUiState.Done(wishlistSync.sync())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val error = t.toLibraryException()
                AppLogger.network.error("Wishlist sync failed (${error.error}): ${error.message}", t)
                WishlistUiState.Failed(error.error)
            }
        }
    }

    /** Enables the device source. There is no login: the phone itself is the "account". */
    fun connectAndroid(onReady: () -> Unit) {
        viewModelScope.launch {
            accounts.connect(LibraryProviders.ANDROID, AndroidLibraryProvider.DEVICE_ACCOUNT_ID, Build.MODEL)
            onReady()
        }
    }

    /** Unlinks a store. Backlog games are left untouched; only their source rows for it go. */
    fun disconnect(providerId: String) {
        viewModelScope.launch {
            accounts.disconnect(providerId)
            sourceDao.deleteForProvider(providerId)
            // Signing out of TMDB also ends the session it granted (on TMDB and on this device).
            if (providerId == LibraryProviders.TMDB) tmdbSync.signOut()
            if (providerId == LibraryProviders.STEAM) {
                sourceDao.deleteForProvider(LibraryProviders.STEAM_WISHLIST)
                _wishlist.value = WishlistUiState.Idle
            }
        }
    }
}

class PlatformsViewModelFactory(
    private val accounts: LibraryAccountStore,
    private val sourceDao: GameSourceDao,
    private val tmdbSync: TmdbSyncService,
    private val wishlistSync: WishlistSyncService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PlatformsViewModel::class.java)
        return PlatformsViewModel(accounts, sourceDao, tmdbSync, wishlistSync) as T
    }
}
