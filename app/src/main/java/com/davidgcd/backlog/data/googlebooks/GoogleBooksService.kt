package com.davidgcd.backlog.data.googlebooks

import com.davidgcd.backlog.data.openlibrary.BookSourceError
import com.davidgcd.backlog.data.openlibrary.BookSourceException
import com.davidgcd.backlog.model.BookPage
import com.davidgcd.backlog.util.Isbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Google Books, the fallback catalogue: only asked when Open Library is down or has nothing for a
 * query. It uses the public endpoint without an API key (Google's anonymous quota is plenty for one
 * person's searches), so the app never depends on a Google credential.
 */
class GoogleBooksService(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://www.googleapis.com/books/v1",
) {
    suspend fun searchBooks(query: String, page: Int = 1, limit: Int = GoogleBooksParsers.PAGE_SIZE, langRestrict: String? = null): BookPage {
        val q = query.trim()
        if (q.isEmpty()) return BookPage(emptyList(), hasMore = false)
        val isbn = Isbn.fromQuery(q)
        val url = "$baseUrl/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", if (isbn != null) "isbn:$isbn" else q)
            .addQueryParameter("startIndex", ((page - 1) * limit).toString())
            .addQueryParameter("maxResults", limit.toString())
            .addQueryParameter("printType", "books")
            .apply { langRestrict?.let { addQueryParameter("langRestrict", it) } }
            .build()
        return GoogleBooksParsers.parseSearch(get(url))
    }

    private suspend fun get(url: okhttp3.HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw BookSourceException(BookSourceError.UNAVAILABLE, "HTTP ${response.code} Google Books")
                response.body?.string().orEmpty()
            }
        } catch (e: BookSourceException) {
            throw e
        } catch (e: IOException) {
            throw BookSourceException(BookSourceError.UNAVAILABLE, "I/O: ${e.message}", e)
        }
    }
}
