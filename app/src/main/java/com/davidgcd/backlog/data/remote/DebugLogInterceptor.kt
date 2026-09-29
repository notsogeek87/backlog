package com.davidgcd.backlog.data.remote

import com.davidgcd.backlog.util.DebugLog
import okhttp3.Interceptor
import okhttp3.Response

/** Logs every IGDB request/response (method, url, body, status) into [DebugLog]. */
class DebugLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBodyText = request.body?.let { body ->
            runCatching {
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }.getOrNull()
        }
        DebugLog.log("→ ${request.method} ${request.url}${requestBodyText?.let { "\nbody: $it" } ?: ""}")

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            DebugLog.log("✗ ${request.url} threw ${e::class.simpleName}: ${e.message}")
            throw e
        }

        val bodyPeek = response.peekBody(4_000).string()
        DebugLog.log("← ${response.code} ${request.url}\nbody: $bodyPeek")
        return response
    }
}
