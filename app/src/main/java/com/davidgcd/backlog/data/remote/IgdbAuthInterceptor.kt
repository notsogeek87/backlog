package com.davidgcd.backlog.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/** Runs [block] on OkHttp's thread; anything but an IOException would crash OkHttp's dispatcher (and the app). */
private fun <T> blockingIo(what: String, block: suspend () -> T): T = try {
    runBlocking { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    throw e
} catch (e: Throwable) {
    throw IOException(what, e)
}

private fun Request.withIgdbCredentials(credentials: IgdbCredentials): Request = newBuilder()
    .header("Client-ID", credentials.clientId)
    .header("Authorization", "Bearer ${credentials.token}")
    .build()

class IgdbAuthInterceptor(private val tokenProvider: IgdbTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = blockingIo("Failed to obtain IGDB access token") { tokenProvider.credentials() }
        return chain.proceed(chain.request().withIgdbCredentials(credentials))
    }
}

/**
 * On a 401 from IGDB (token revoked or expired early), drops the token, gets a new one and retries ONCE.
 * Without this, a dead token stayed cached until its theoretical expiry and IGDB answered nothing meanwhile.
 */
class IgdbAuthenticator(private val tokenProvider: IgdbTokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // already retried once: give up
        val refused = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        val fresh = blockingIo("Failed to renew IGDB access token") {
            tokenProvider.invalidate(refused)
            tokenProvider.credentials()
        }
        return response.request.withIgdbCredentials(fresh)
    }
}
