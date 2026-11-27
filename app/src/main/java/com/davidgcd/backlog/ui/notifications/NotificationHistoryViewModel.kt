package com.davidgcd.backlog.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.notifications.NotificationEntry
import com.davidgcd.backlog.notifications.NotificationHistory
import com.davidgcd.backlog.notifications.NotificationHistoryStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NotificationHistoryState(
    val entries: List<NotificationEntry> = emptyList(),
    val unreadCount: Int = 0,
)

/** Historique de la cloche : la liste des alertes passées et la pastille « non lues ». */
class NotificationHistoryViewModel(private val store: NotificationHistoryStore) : ViewModel() {
    /** Dernière ouverture avant celle-ci : ce qui est arrivé après est mis en avant pendant la visite. */
    val newAfterMillis: Long = store.lastSeenMillis()

    val state: StateFlow<NotificationHistoryState> = store.changes().map {
        val entries = store.entries()
        val lastSeen = store.lastSeenMillis()
        NotificationHistoryState(entries, NotificationHistory.unreadCount(entries, lastSeen))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationHistoryState())

    fun markAllSeen() = store.markAllSeen()

    fun clear() = store.clear()
}

class NotificationHistoryViewModelFactory(private val store: NotificationHistoryStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == NotificationHistoryViewModel::class.java)
        return NotificationHistoryViewModel(store) as T
    }
}
