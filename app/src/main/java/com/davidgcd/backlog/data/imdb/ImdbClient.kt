package com.davidgcd.backlog.data.imdb

import com.davidgcd.backlog.model.ImdbTitle
import com.davidgcd.backlog.model.MovieChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

enum class ImdbError {
    /** No (or an expired) IMDb session: the user has to sign in again. */
    NOT_SIGNED_IN,
    /** IMDb didn't answer, or answered something that isn't the expected page/file. */
    UNAVAILABLE,
    UNKNOWN,
}

/** Never carries text for the user — the UI maps [error] to a localized message. */
class ImdbException(val error: ImdbError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.name, cause)

/**
 * IMDb has no free public API, so this talks to the same endpoints its own website does: the
 * type-ahead JSON for search, the public title/chart pages, and — with the session of a signed-in
 * user (see [WebViewCookieJar]) — the CSV exports of their watchlist and ratings.
 * Parsing lives in [ImdbParsers]/[ImdbCsv]; this class only fetches.
 */
class ImdbClient(private val http: OkHttpClient) {

    suspend fun search(query: String): List<ImdbTitle> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val letter = q.first().takeIf { it.isLetterOrDigit() && it.code < 128 } ?: 'x'
        val body = get("$SUGGEST_HOST/suggestion/$letter/${URLEncoder.encode(q, "UTF-8").replace("+", "%20")}.json").body
        return ImdbParsers.parseSuggestions(body)
    }

    /** Full details of one title (plot, rating, runtime…), or null if the page has nothing we can read. */
    suspend fun title(id: String): ImdbTitle? = ImdbParsers.parseTitlePage(get("$WEB_HOST/title/$id/").body, fallbackId = id)

    suspend fun chart(chart: MovieChart): List<ImdbTitle> = ImdbParsers.parseChart(get("$WEB_HOST${chart.path}").body)

    /** The poster of a title via the type-ahead endpoint (searching an id returns that title). */
    suspend fun poster(id: String): String? =
        get("$SUGGEST_HOST/suggestion/t/$id.json").body.let(ImdbParsers::parseSuggestions).firstOrNull { it.id == id }?.posterUrl

    /** The `ur…` id of the signed-in user, or null when there is no valid session. */
    suspend fun resolveUserId(): String? {
        val response = get("$WEB_HOST/profile", allowSignedOut = true)
        return ImdbParsers.userIdFromUrl(response.finalUrl)
    }

    /** CSV export of the watchlist, or null when the watchlist can't be located. */
    suspend fun watchlistCsv(userId: String): String? {
        val page = get("$WEB_HOST/user/$userId/watchlist", allowSignedOut = true)
        if (page.finalUrl.contains("/registration/signin") || page.finalUrl.contains("/ap/signin")) {
            throw ImdbException(ImdbError.NOT_SIGNED_IN)
        }
        val listId = ImdbParsers.watchlistIdFrom(page.finalUrl, page.body) ?: return null
        return exportCsv("$WEB_HOST/list/$listId/export")
    }

    suspend fun ratingsCsv(userId: String): String = exportCsv("$WEB_HOST/user/$userId/ratings/export")

    private suspend fun exportCsv(url: String): String {
        val response = get(url, allowSignedOut = true)
        if (response.finalUrl.contains("/registration/signin") || response.finalUrl.contains("/ap/signin")) {
            throw ImdbException(ImdbError.NOT_SIGNED_IN)
        }
        if (!ImdbCsv.looksLikeExport(response.body)) {
            throw ImdbException(ImdbError.UNAVAILABLE, "not a CSV export: ${response.body.take(80)}")
        }
        return response.body
    }

    private class Page(val finalUrl: String, val body: String)

    private suspend fun get(url: String, allowSignedOut: Boolean = false): Page = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.6")
            .header("Accept", "text/html,application/json,text/csv,*/*;q=0.8")
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (!response.isSuccessful && !(allowSignedOut && response.code in 400..499 && response.code != 429)) {
                    throw ImdbException(ImdbError.UNAVAILABLE, "HTTP ${response.code} for $url")
                }
                Page(finalUrl, response.body?.string().orEmpty())
            }
        } catch (e: ImdbException) {
            throw e
        } catch (e: IOException) {
            throw ImdbException(ImdbError.UNAVAILABLE, "I/O: ${e.message}", e)
        }
    }

    companion object {
        const val WEB_HOST = "https://www.imdb.com"
        const val SUGGEST_HOST = "https://v3.sg.media-imdb.com"

        // IMDb turns away obviously non-browser agents; a plain mobile-Chrome UA gets the normal pages.
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
