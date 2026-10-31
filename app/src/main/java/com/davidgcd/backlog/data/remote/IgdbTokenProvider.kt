package com.davidgcd.backlog.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** What every IGDB call needs: the public Twitch client id and an app access token. */
data class IgdbCredentials(val clientId: String, val token: String)

/**
 * Fetches the IGDB credentials from the app's server ([IgdbTokenApi]) and caches them in memory, refreshing a
 * little before expiry. A token IGDB refuses (401, see [IgdbAuthenticator]) is dropped with [invalidate], and the
 * next fetch reports it to the server so a revoked token is renewed instead of being handed out again.
 */
class IgdbTokenProvider(
    private val api: IgdbTokenApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var cached: IgdbCredentials? = null
    private var expiresAtMillis: Long = 0
    private var staleFingerprint: String? = null

    suspend fun credentials(): IgdbCredentials = mutex.withLock {
        val now = clock()
        cached?.takeIf { now < expiresAtMillis }?.let { return@withLock it }

        val response = api.token(stale = staleFingerprint)
        val fresh = IgdbCredentials(response.client_id, response.access_token)
        cached = fresh
        staleFingerprint = null
        expiresAtMillis = now + (response.expires_in * 1000) - REFRESH_MARGIN_MILLIS
        fresh
    }

    /** Forgets [token] if it is the cached one (a concurrent call may already have replaced it). */
    suspend fun invalidate(token: String) = mutex.withLock {
        if (cached?.token == token) {
            cached = null
            expiresAtMillis = 0
            staleFingerprint = fingerprint(token)
        }
    }

    companion object {
        private const val REFRESH_MARGIN_MILLIS = 60_000L

        /** First 16 hex chars of sha256(token): identifies the token to the server without sending it back. */
        fun fingerprint(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}
