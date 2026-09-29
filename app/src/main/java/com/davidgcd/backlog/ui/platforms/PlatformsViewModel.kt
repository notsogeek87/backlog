package com.davidgcd.backlog.ui.platforms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.library.LibraryAccount
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.local.GameSourceDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "My platforms" card in Settings. Steam is the only provider for now. */
class PlatformsViewModel(
    private val accounts: LibraryAccountStore,
    private val sourceDao: GameSourceDao,
) : ViewModel() {
    val steam: StateFlow<LibraryAccount?> = accounts.observe(LibraryProviders.STEAM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Unlinks the account. Backlog games are left untouched; only their Steam source rows go. */
    fun disconnectSteam() {
        viewModelScope.launch {
            accounts.disconnect(LibraryProviders.STEAM)
            sourceDao.deleteForProvider(LibraryProviders.STEAM)
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
