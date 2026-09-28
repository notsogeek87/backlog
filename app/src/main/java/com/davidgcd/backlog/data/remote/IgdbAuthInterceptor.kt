package com.davidgcd.backlog.data.remote

import com.davidgcd.backlog.config.Secrets
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class IgdbAuthInterceptor(private val tokenProvider: IgdbTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.token() }
        val request = chain.request().newBuilder()
            .addHeader("Client-ID", Secrets.TWITCH_CLIENT_ID)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
