package com.davidgcd.backlog.data.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The linked account of one store. Only the public account id (SteamID64), a display name and sync
 * stats are kept — never a password or token.
 */
data class LibraryAccount(
    val providerId: String,
    val accountId: String,
    val displayName: String? = null,
    val connectedAt: Long,
    val lastSyncedAt: Long? = null,
    val ownedCount: Int? = null,
    val alreadyInBacklogCount: Int? = null,
    val newCount: Int? = null,
)

interface LibraryAccountStore {
    fun observe(providerId: String): Flow<LibraryAccount?>
    suspend fun get(providerId: String): LibraryAccount? = observe(providerId).first()
    suspend fun connect(providerId: String, accountId: String, displayName: String?, now: Long = System.currentTimeMillis())
    suspend fun disconnect(providerId: String)
    suspend fun recordSync(providerId: String, syncedAt: Long, ownedCount: Int, alreadyInBacklogCount: Int, newCount: Int)
}

private val Context.libraryDataStore by preferencesDataStore(name = "library_accounts")

class DataStoreLibraryAccountStore(private val context: Context) : LibraryAccountStore {
    private fun str(p: String, k: String) = stringPreferencesKey("${p}_$k")
    private fun lng(p: String, k: String) = longPreferencesKey("${p}_$k")
    private fun int(p: String, k: String) = intPreferencesKey("${p}_$k")

    override fun observe(providerId: String): Flow<LibraryAccount?> = context.libraryDataStore.data.map { prefs ->
        val accountId = prefs[str(providerId, "account_id")] ?: return@map null
        LibraryAccount(
            providerId = providerId,
            accountId = accountId,
            displayName = prefs[str(providerId, "display_name")],
            connectedAt = prefs[lng(providerId, "connected_at")] ?: 0L,
            lastSyncedAt = prefs[lng(providerId, "last_synced_at")],
            ownedCount = prefs[int(providerId, "owned")],
            alreadyInBacklogCount = prefs[int(providerId, "already")],
            newCount = prefs[int(providerId, "new")],
        )
    }

    override suspend fun connect(providerId: String, accountId: String, displayName: String?, now: Long) {
        context.libraryDataStore.edit { prefs ->
            // A different account starts from a clean slate; re-linking the same one keeps its stats.
            if (prefs[str(providerId, "account_id")] != accountId) clear(prefs, providerId)
            prefs[str(providerId, "account_id")] = accountId
            if (displayName != null) prefs[str(providerId, "display_name")] = displayName
            prefs[lng(providerId, "connected_at")] = now
        }
    }

    override suspend fun disconnect(providerId: String) {
        context.libraryDataStore.edit { clear(it, providerId) }
    }

    override suspend fun recordSync(providerId: String, syncedAt: Long, ownedCount: Int, alreadyInBacklogCount: Int, newCount: Int) {
        context.libraryDataStore.edit { prefs ->
            prefs[lng(providerId, "last_synced_at")] = syncedAt
            prefs[int(providerId, "owned")] = ownedCount
            prefs[int(providerId, "already")] = alreadyInBacklogCount
            prefs[int(providerId, "new")] = newCount
        }
    }

    private fun clear(prefs: androidx.datastore.preferences.core.MutablePreferences, providerId: String) {
        listOf(str(providerId, "account_id"), str(providerId, "display_name")).forEach { prefs.remove(it) }
        listOf(lng(providerId, "connected_at"), lng(providerId, "last_synced_at")).forEach { prefs.remove(it) }
        listOf(int(providerId, "owned"), int(providerId, "already"), int(providerId, "new")).forEach { prefs.remove(it) }
    }
}
