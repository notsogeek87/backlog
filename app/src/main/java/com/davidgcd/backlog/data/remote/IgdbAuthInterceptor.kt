package com.davidgcd.backlog.data.remote

import com.davidgcd.backlog.config.Secrets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class IgdbAuthInterceptor(private val tokenProvider: IgdbTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp only treats IOException as a normal call failure; any other Throwable escaping
        // an interceptor crashes OkHttp's dispatcher thread (and the whole app) uncaught.
        val token = try {
            runBlocking { tokenProvider.token() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            throw IOException("Failed to obtain IGDB access token", e)
        }
        val request = chain.request().newBuilder()
            .addHeader("Client-ID", Secrets.TWITCH_CLIENT_ID)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
