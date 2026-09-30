package com.davidgcd.backlog.data.tmdb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** The signed-in TMDB user: [sessionId] is the credential every account call needs, so it never leaves the device. */
data class TmdbSession(val sessionId: String, val accountId: Long, val username: String?)

interface TmdbSessionStore {
    suspend fun get(): TmdbSession?
    suspend fun save(session: TmdbSession)
    suspend fun clear()
}

/**
 * Keeps the session in `noBackupFilesDir`: app-private and excluded from Android Auto Backup / device
 * transfer, unlike the DataStore holding the (non-secret) linked-account summary. A restored phone
 * simply asks the user to sign in again.
 */
class FileTmdbSessionStore(context: Context) : TmdbSessionStore {
    private val file = File(context.noBackupFilesDir, "tmdb_session.json")

    override suspend fun get(): TmdbSession? = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(file.readText())
            TmdbSession(o.getString("session_id"), o.getLong("account_id"), o.optString("username").ifEmpty { null })
        }.getOrNull()
    }

    override suspend fun save(session: TmdbSession) = withContext(Dispatchers.IO) {
        file.writeText(
            JSONObject()
                .put("session_id", session.sessionId)
                .put("account_id", session.accountId)
                .put("username", session.username ?: "")
                .toString(),
        )
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }
}
