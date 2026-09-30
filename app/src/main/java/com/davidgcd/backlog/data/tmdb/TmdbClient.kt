package com.davidgcd.backlog.data.tmdb

import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.MovieChart
import com.davidgcd.backlog.model.TitleKey
import com.davidgcd.backlog.model.TitleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

enum class TmdbError {
    /** No TMDB API key in this build (see Secrets.kt.example). */
    NOT_CONFIGURED,
    /** No (or an expired / revoked) TMDB session: the user has to sign in again. */
    NOT_SIGNED_IN,
    /** TMDB didn't answer, or answered something unexpected. */
    UNAVAILABLE,
    UNKNOWN,
}

/** Never carries text for the user — the UI maps [error] to a localized message. */
class TmdbException(val error: TmdbError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.name, cause)

/**
 * The official TMDB v3 API (https://developer.themoviedb.org): search, details, popular / top-rated
 * lists, and — with the session a user grants through TMDB's own approval page — their watchlist and
 * ratings, read and written. Parsing lives in [TmdbParsers]; this class only fetches.
 */
class TmdbClient(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val sessions: TmdbSessionStore,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && !apiKey.startsWith("YOUR_")

    @Volatile private var genreCache: Map<Int, String>? = null

    // --- catalogue ---------------------------------------------------------------------------

    suspend fun search(query: String): List<MediaTitle> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val body = call("GET", "/search/multi", mapOf("query" to q, "include_adult" to "false"))
        return TmdbParsers.parseList(body, forcedKind = null, genreNames = genres()).map { it.title }
    }

    suspend fun chart(chart: MovieChart): List<MediaTitle> =
        TmdbParsers.parseList(call("GET", chart.path), chart.kind, genres()).map { it.title }

    /** Full details (plot, runtime, director, cast) of one title, or null if TMDB doesn't know it. */
    suspend fun details(key: String): MediaTitle? {
        val kind = TitleKey.kind(key) ?: return null
        val id = TitleKey.tmdbId(key) ?: return null
        val body = try {
            call("GET", "/${TitleKey.prefix(kind)}/$id", mapOf("append_to_response" to "credits"))
        } catch (e: TmdbException) {
            if (e.message?.startsWith("HTTP 404") == true) return null else throw e
        }
        return TmdbParsers.parseDetails(body, kind)
    }

    /** Genre id → French name, both films and series; empty (no genre badges) if TMDB can't be asked. */
    private suspend fun genres(): Map<Int, String> {
        genreCache?.let { return it }
        return try {
            (TmdbParsers.parseGenres(call("GET", "/genre/movie/list")) + TmdbParsers.parseGenres(call("GET", "/genre/tv/list")))
                .also { genreCache = it }
        } catch (e: TmdbException) {
            emptyMap()
        }
    }

    // --- sign-in (TMDB's request-token flow: the user approves on themoviedb.org) --------------

    /** Step 1: a token to send the user to [approvalUrl] with. */
    suspend fun newRequestToken(): String =
        TmdbParsers.stringField(call("GET", "/authentication/token/new"), "request_token")
            ?: throw TmdbException(TmdbError.UNAVAILABLE, "no request_token")

    fun approvalUrl(requestToken: String, redirectTo: String): String =
        "https://www.themoviedb.org/authenticate/$requestToken?redirect_to=${java.net.URLEncoder.encode(redirectTo, "UTF-8")}"

    /** Step 3, once the user approved: trades the token for a session and remembers it. */
    suspend fun createSession(requestToken: String): TmdbSession {
        val sessionId = TmdbParsers.stringField(
            call("POST", "/authentication/session/new", body = JSONObject().put("request_token", requestToken)),
            "session_id",
        ) ?: throw TmdbException(TmdbError.NOT_SIGNED_IN, "no session_id")
        val (accountId, username) = TmdbParsers.parseAccount(call("GET", "/account", session = sessionId))
            ?: throw TmdbException(TmdbError.UNAVAILABLE, "no account")
        return TmdbSession(sessionId, accountId, username).also { sessions.save(it) }
    }

    suspend fun currentSession(): TmdbSession? = sessions.get()

    /** Ends the session on TMDB (best effort) and forgets it here. */
    suspend fun signOut() {
        sessions.get()?.let { s ->
            try {
                call("DELETE", "/authentication/session", body = JSONObject().put("session_id", s.sessionId))
            } catch (e: TmdbException) {
                // Forgetting it locally is what matters.
            }
        }
        sessions.clear()
    }

    // --- the user's account ------------------------------------------------------------------

    suspend fun watchlist(): List<TmdbListItem> = accountList("watchlist")

    suspend fun rated(): List<TmdbListItem> = accountList("rated")

    private suspend fun accountList(list: String): List<TmdbListItem> {
        val s = requireSession()
        val names = genres()
        return listOf(TitleKind.MOVIE to "movies", TitleKind.SERIES to "tv").flatMap { (kind, path) ->
            val out = mutableListOf<TmdbListItem>()
            var page = 1
            var pages = 1
            while (page <= pages && page <= MAX_PAGES) {
                val body = call("GET", "/account/${s.accountId}/$list/$path", mapOf("page" to page.toString(), "sort_by" to "created_at.desc"), session = s.sessionId)
                pages = TmdbParsers.totalPages(body)
                out += TmdbParsers.parseList(body, kind, names)
                page++
            }
            out
        }
    }

    /** Rates (1–10) or, with null, clears the user's rating of [key] on TMDB. */
    suspend fun rate(key: String, value: Int?) {
        val s = requireSession()
        val (kind, id) = split(key) ?: return
        val path = "/${TitleKey.prefix(kind)}/$id/rating"
        if (value == null) call("DELETE", path, session = s.sessionId)
        else call("POST", path, body = JSONObject().put("value", value.coerceIn(1, 10).toDouble()), session = s.sessionId)
    }

    /** Adds / removes [key] from the user's TMDB watchlist. */
    suspend fun setWatchlist(key: String, on: Boolean) {
        val s = requireSession()
        val (kind, id) = split(key) ?: return
        call(
            "POST", "/account/${s.accountId}/watchlist",
            body = JSONObject().put("media_type", TitleKey.prefix(kind)).put("media_id", id).put("watchlist", on),
            session = s.sessionId,
        )
    }

    private fun split(key: String): Pair<TitleKind, Long>? {
        val kind = TitleKey.kind(key) ?: return null
        return TitleKey.tmdbId(key)?.let { kind to it }
    }

    private suspend fun requireSession(): TmdbSession = sessions.get() ?: throw TmdbException(TmdbError.NOT_SIGNED_IN)

    // --- plumbing ----------------------------------------------------------------------------

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        session: String? = null,
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured) throw TmdbException(TmdbError.NOT_CONFIGURED)
        val url = "$HOST$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", apiKey)
            addQueryParameter("language", "fr-FR")
            session?.let { addQueryParameter("session_id", it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(url).method(
            method,
            if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody(JSON),
        ).build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw when {
                        // 401 = invalid key, or (with a session) a session the user revoked.
                        response.code == 401 -> TmdbException(if (session != null) TmdbError.NOT_SIGNED_IN else TmdbError.NOT_CONFIGURED, "HTTP 401")
                        else -> TmdbException(TmdbError.UNAVAILABLE, "HTTP ${response.code} $path")
                    }
                }
                text
            }
        } catch (e: TmdbException) {
            throw e
        } catch (e: IOException) {
            throw TmdbException(TmdbError.UNAVAILABLE, "I/O: ${e.message}", e)
        }
    }

    private companion object {
        const val HOST = "https://api.themoviedb.org/3"
        const val MAX_PAGES = 25
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
