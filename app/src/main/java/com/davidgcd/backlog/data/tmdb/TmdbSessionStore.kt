package com.davidgcd.backlog.data.tmdb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The signed-in TMDB user: [sessionId] is the credential every v3 account call needs, so it never leaves the device.
 * [accessToken] is the v4 one (writing a series to a list); null for a session opened before v4 sign-in.
 */
data class TmdbSession(val sessionId: String, val accountId: Long, val username: String?, val accessToken: String? = null)

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
            TmdbSession(
                o.getString("session_id"),
                o.getLong("account_id"),
                o.optString("username").ifEmpty { null },
                o.optString("access_token").ifEmpty { null },
            )
        }.getOrNull()
    }

    override suspend fun save(session: TmdbSession) = withContext(Dispatchers.IO) {
        file.writeText(
            JSONObject()
                .put("session_id", session.sessionId)
                .put("account_id", session.accountId)
                .put("username", session.username ?: "")
                .put("access_token", session.accessToken ?: "")
                .toString(),
        )
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }
}
