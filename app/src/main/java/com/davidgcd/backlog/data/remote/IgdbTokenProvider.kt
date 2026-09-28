package com.davidgcd.backlog.data.remote

import com.davidgcd.backlog.config.Secrets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * IGDB requires a Twitch app access token (client-credentials grant). This
 * fetches and caches it in memory, refreshing a little before expiry.
 * Equivalent role to AuthService + KeychainStorage on the iOS app, minus the
 * Keychain persistence (a client-credentials token is cheap to re-fetch, so
 * it isn't persisted across process launches here).
 */
class IgdbTokenProvider(private val twitchAuthApi: TwitchAuthApi) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAtMillis: Long = 0

    suspend fun token(): String = mutex.withLock {
        val now = System.currentTimeMillis()
        val current = cachedToken
        if (current != null && now < expiresAtMillis) return@withLock current

        val response = twitchAuthApi.requestToken(
            clientId = Secrets.TWITCH_CLIENT_ID,
            clientSecret = Secrets.TWITCH_CLIENT_SECRET,
        )
        cachedToken = response.access_token
        expiresAtMillis = now + (response.expires_in * 1000) - REFRESH_MARGIN_MILLIS
        response.access_token
    }

    companion object {
        private const val REFRESH_MARGIN_MILLIS = 60_000L
    }
}
