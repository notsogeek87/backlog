package com.davidgcd.backlog.data.library.steam

import com.davidgcd.backlog.data.library.LibraryError
import com.davidgcd.backlog.data.library.LibraryException
import com.davidgcd.backlog.data.remote.SteamOpenIdApi
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Turns the OpenID callback URL into a verified [SteamProfile]. Stores nothing itself. */
class SteamAuthService(
    private val openIdApi: SteamOpenIdApi,
    private val provider: SteamLibraryProvider,
) {
    suspend fun completeLogin(callbackUrl: String): SteamProfile {
        val params = SteamOpenId.parseCallback(callbackUrl)
        if (SteamOpenId.isCancelled(params)) throw LibraryException(LibraryError.CANCELLED)
        val steamId = SteamOpenId.claimedSteamId(params)
            ?: throw LibraryException(LibraryError.UNKNOWN, "Malformed Steam OpenID assertion")

        val verified = try {
            SteamOpenId.isValidResponse(openIdApi.checkAuthentication(SteamOpenId.verificationParams(params)).string())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLogger.network.warn("Steam OpenID verification failed: ${e.message}")
            throw LibraryException(LibraryError.UNAVAILABLE, "OpenID verification I/O", e)
        } catch (t: Throwable) {
            AppLogger.network.warn("Steam OpenID verification failed: ${t.message}")
            throw LibraryException(LibraryError.UNAVAILABLE, "OpenID verification", t)
        }
        AppLogger.network.warn("Steam OpenID check_authentication: valid=$verified")
        if (!verified) throw LibraryException(LibraryError.UNKNOWN, "Steam rejected the OpenID assertion")

        // Persona name is cosmetic: a hiccup there must not undo a verified login.
        return try {
            provider.fetchProfile(steamId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LibraryException) {
            if (e.error == LibraryError.ACCOUNT_NOT_FOUND) throw e
            SteamProfile(steamId, displayName = null)
        }
    }
}
