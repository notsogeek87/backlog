package com.davidgcd.backlog.ui.platforms

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.library.LibraryAccount
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.android.AndroidLibraryProvider
import com.davidgcd.backlog.data.local.GameSourceDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "My platforms" cards in Settings: Steam (account) and the games installed on this phone. */
class PlatformsViewModel(
    private val accounts: LibraryAccountStore,
    private val sourceDao: GameSourceDao,
) : ViewModel() {
    val steam: StateFlow<LibraryAccount?> = accounts.observe(LibraryProviders.STEAM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val android: StateFlow<LibraryAccount?> = accounts.observe(LibraryProviders.ANDROID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        }
    }
}

class PlatformsViewModelFactory(
    private val accounts: LibraryAccountStore,
    private val sourceDao: GameSourceDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PlatformsViewModel::class.java)
        return PlatformsViewModel(accounts, sourceDao) as T
    }
}
