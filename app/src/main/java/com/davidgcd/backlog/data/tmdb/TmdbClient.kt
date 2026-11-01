package com.davidgcd.backlog.data.tmdb

import com.davidgcd.backlog.model.CastMember
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.MovieChart
import com.davidgcd.backlog.model.PersonFilmography
import com.davidgcd.backlog.model.TitleKey
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.Trailer
import com.davidgcd.backlog.model.WatchProviders
import kotlinx.coroutines.CoroutineDispatcher
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
 * The official TMDB API (https://developer.themoviedb.org): search, details, popular / top-rated
 * lists, and — with the access a user grants through TMDB's own approval page — their watchlist,
 * ratings and lists, read and written. Parsing lives in [TmdbParsers]; this class only fetches.
 *
 * Almost everything goes through v3. Writing to the user's own lists goes through v4: v3's
 * `/list/{id}/add_item` only knows films and would put the film with the same number in the list
 * instead of a series (`tv:74415` → `movie:74415`). With a [readAccessToken], the sign-in uses v4 (one
 * approval gives a v4 access token, converted into the v3 session for the rest).
 */
class TmdbClient(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val sessions: TmdbSessionStore,
    /** Where the blocking HTTP calls run; tests pass their own dispatcher so nothing outlives a test. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** TMDB's « API Read Access Token » (v4 auth); empty or the placeholder = v3 sign-in, no series in lists. */
    private val readAccessToken: String = "",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && !apiKey.startsWith("YOUR_")

    /** Whether the sign-in can use v4, the only way to put a series in one of the user's lists. */
    val usesV4: Boolean get() = readAccessToken.isNotBlank() && !readAccessToken.startsWith("YOUR_")

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

    /** Last aired and next episode of a series; null for a film, an unknown id or an unreadable answer. */
    suspend fun episodes(key: String): SeriesEpisodes? {
        if (TitleKey.kind(key) != TitleKind.SERIES) return null
        val id = TitleKey.tmdbId(key) ?: return null
        return TmdbParsers.parseEpisodes(call("GET", "/tv/$id"))
    }

    /** Director(s) and top-billed actors of [key], with their photos; empty when TMDB lists none. */
    suspend fun credits(key: String): List<CastMember> {
        val kind = TitleKey.kind(key) ?: return emptyList()
        val id = TitleKey.tmdbId(key) ?: return emptyList()
        val body = call("GET", "/${TitleKey.prefix(kind)}/$id", mapOf("append_to_response" to "credits"))
        return TmdbParsers.parseCredits(body, kind)
    }

    /** What [personId] acted in, or directed when [asDirector]; null if TMDB doesn't know the person. */
    suspend fun personFilmography(personId: Long, asDirector: Boolean): PersonFilmography? {
        val body = try {
            call("GET", "/person/$personId", mapOf("append_to_response" to "combined_credits"))
        } catch (e: TmdbException) {
            if (e.message?.startsWith("HTTP 404") == true) return null else throw e
        }
        return TmdbParsers.parsePersonFilmography(body, asDirector, genres())
    }

    /**
     * Where to watch [key] in France, fetched live (offers change), or null when TMDB lists none.
     * The data behind it is JustWatch's, which TMDB requires crediting.
     */
    suspend fun watchProviders(key: String): WatchProviders? {
        val kind = TitleKey.kind(key) ?: return null
        val id = TitleKey.tmdbId(key) ?: return null
        val body = call("GET", "/${TitleKey.prefix(kind)}/$id/watch/providers")
        return TmdbParsers.parseWatchProviders(body, WatchProviders.REGION)
    }

    /** The trailer of [key], in French when TMDB has one, else in another language; null when none. */
    suspend fun trailer(key: String): Trailer? {
        val kind = TitleKey.kind(key) ?: return null
        val id = TitleKey.tmdbId(key) ?: return null
        val body = call("GET", "/${TitleKey.prefix(kind)}/$id/videos", mapOf("include_video_language" to "fr,en,null"))
        return TmdbParsers.parseTrailer(body)
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

    /** Step 1: a token to send the user to [approvalUrl] with; v4 already needs [redirectTo] here. */
    suspend fun newRequestToken(redirectTo: String): String {
        val body = if (usesV4) {
            call("POST", "/auth/request_token", body = JSONObject().put("redirect_to", redirectTo), bearer = readAccessToken)
        } else {
            call("GET", "/authentication/token/new")
        }
        return TmdbParsers.stringField(body, "request_token") ?: throw TmdbException(TmdbError.UNAVAILABLE, "no request_token")
    }

    fun approvalUrl(requestToken: String, redirectTo: String): String =
        if (usesV4) {
            "https://www.themoviedb.org/auth/access?request_token=${java.net.URLEncoder.encode(requestToken, "UTF-8")}"
        } else {
            "https://www.themoviedb.org/authenticate/$requestToken?redirect_to=${java.net.URLEncoder.encode(redirectTo, "UTF-8")}"
        }

    /** Step 3, once the user approved: trades the token for a session (and, in v4, an access token) and remembers it. */
    suspend fun createSession(requestToken: String): TmdbSession {
        val accessToken = if (usesV4) {
            TmdbParsers.stringField(
                call("POST", "/auth/access_token", body = JSONObject().put("request_token", requestToken), bearer = readAccessToken),
                "access_token",
            ) ?: throw TmdbException(TmdbError.NOT_SIGNED_IN, "no access_token")
        } else {
            null
        }
        val sessionBody = if (accessToken != null) {
            call("POST", "/authentication/session/convert/4", body = JSONObject().put("access_token", accessToken))
        } else {
            call("POST", "/authentication/session/new", body = JSONObject().put("request_token", requestToken))
        }
        val sessionId = TmdbParsers.stringField(sessionBody, "session_id")
            ?: throw TmdbException(TmdbError.NOT_SIGNED_IN, "no session_id")
        val (accountId, username) = TmdbParsers.parseAccount(call("GET", "/account", session = sessionId))
            ?: throw TmdbException(TmdbError.UNAVAILABLE, "no account")
        return TmdbSession(sessionId, accountId, username, accessToken).also { sessions.save(it) }
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
            if (s.accessToken != null && usesV4) {
                try {
                    call("DELETE", "/auth/access_token", body = JSONObject().put("access_token", s.accessToken), bearer = readAccessToken)
                } catch (e: TmdbException) {
                    // Same.
                }
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

    // --- the user's own lists (created on themoviedb.org), next to the watchlist -----------------

    /** The lists the user created, all pages; empty when they have none. */
    suspend fun userLists(): List<TmdbUserList> {
        val s = requireSession()
        val out = mutableListOf<TmdbUserList>()
        var page = 1
        var pages = 1
        while (page <= pages && page <= MAX_PAGES) {
            val body = call("GET", "/account/${s.accountId}/lists", mapOf("page" to page.toString()), session = s.sessionId)
            pages = TmdbParsers.totalPages(body)
            out += TmdbParsers.parseUserLists(body)
            page++
        }
        return out
    }

    /** Whether [key] is in the user's watchlist. */
    suspend fun isInWatchlist(key: String): Boolean {
        val s = requireSession()
        val (kind, id) = split(key) ?: return false
        return TmdbParsers.parseInWatchlist(call("GET", "/${TitleKey.prefix(kind)}/$id/account_states", session = s.sessionId))
    }

    /**
     * Every title of the list [listId], as keys (`movie:603`, `tv:1396`). Read from the list itself, page by page:
     * TMDB's `item_status` only knows films, so it would never tick a series that is in the list.
     */
    suspend fun listItemKeys(listId: Long): Set<String> {
        val s = requireSession()
        val out = HashSet<String>()
        var page = 1
        var pages = 1
        while (page <= pages && page <= MAX_PAGES) {
            val body = call("GET", "/list/$listId", mapOf("page" to page.toString()), session = s.sessionId)
            pages = TmdbParsers.totalPages(body)
            out += TmdbParsers.parseListItemKeys(body)
            page++
        }
        return out
    }

    /** Every title of the list [listId] (films and series), all pages — what the library imports and filters on. */
    suspend fun listTitles(listId: Long): List<MediaTitle> {
        val s = requireSession()
        val names = genres()
        val out = mutableListOf<MediaTitle>()
        var page = 1
        var pages = 1
        while (page <= pages && page <= MAX_PAGES) {
            val body = call("GET", "/list/$listId", mapOf("page" to page.toString()), session = s.sessionId)
            pages = TmdbParsers.totalPages(body)
            out += TmdbParsers.parseListTitles(body, names)
            page++
        }
        return out.distinctBy { it.id }
    }

    /**
     * Adds / removes [key] from the list [listId]. Adding something already there (TMDB answers with an error
     * for a duplicate) counts as done, as does removing something already gone.
     *
     * Through v4 when the session has an access token: v3 only knows films and would write the film with the
     * same number. Without one (signed in before v4, or a build without the read token), a series is refused
     * with [TmdbError.NOT_SIGNED_IN] — signing in again fixes it — rather than writing the wrong title.
     */
    suspend fun setInList(listId: Long, key: String, on: Boolean) {
        val s = requireSession()
        val (kind, id) = split(key) ?: return
        val accessToken = s.accessToken
        if (accessToken == null && kind == TitleKind.SERIES) throw TmdbException(TmdbError.NOT_SIGNED_IN, "series in a list needs v4")
        try {
            if (accessToken != null) {
                val item = JSONObject().put("media_type", TitleKey.prefix(kind)).put("media_id", id)
                val body = JSONObject().put("items", org.json.JSONArray().put(item))
                val answer = call(if (on) "POST" else "DELETE", "/list/$listId/items", body = body, bearer = accessToken)
                // v4 answers 200 even when an item failed; its own `success` says whether this one was written.
                if (!TmdbParsers.parseV4ItemsSuccess(answer)) throw TmdbException(TmdbError.UNAVAILABLE, "HTTP 412 v4 item refused")
            } else {
                call("POST", "/list/$listId/${if (on) "add_item" else "remove_item"}", body = JSONObject().put("media_id", id), session = s.sessionId)
            }
        } catch (e: TmdbException) {
            val refused = e.message?.let { it.startsWith("HTTP 403") || it.startsWith("HTTP 412") || it.startsWith("HTTP 404") } == true
            if (!refused || (key in listItemKeys(listId)) != on) throw e
        }
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
        /** Set for a v4 call: the token sent as `Authorization: Bearer` (read token, or the user's access token). */
        bearer: String? = null,
    ): String = withContext(io) {
        if (!isConfigured) throw TmdbException(TmdbError.NOT_CONFIGURED)
        val url = "${if (bearer != null) HOST_V4 else HOST}$path".toHttpUrl().newBuilder().apply {
            if (bearer == null) {
                addQueryParameter("api_key", apiKey)
                addQueryParameter("language", "fr-FR")
            }
            session?.let { addQueryParameter("session_id", it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(url).method(
            method,
            if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody(JSON),
        ).apply { bearer?.let { header("Authorization", "Bearer $it") } }.build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw when {
                        // 401 = invalid key / read token, or (with a session or a user's v4 token) access the user revoked.
                        response.code == 401 -> {
                            val userCredential = session != null || (bearer != null && bearer != readAccessToken)
                            TmdbException(if (userCredential) TmdbError.NOT_SIGNED_IN else TmdbError.NOT_CONFIGURED, "HTTP 401")
                        }
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
        const val HOST_V4 = "https://api.themoviedb.org/4"
        const val MAX_PAGES = 25
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
