package com.davidgcd.backlog.data.tmdb

import com.davidgcd.backlog.model.CastMember
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.TitleKey
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchProvider
import com.davidgcd.backlog.model.WatchProviders
import com.davidgcd.backlog.util.TmdbImage
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

/** A line of a TMDB list: the title, and the user's own rating when the list is "rated" (1–10). */
data class TmdbListItem(val title: MediaTitle, val userRating: Int?)

/**
 * Turns TMDB's JSON into [MediaTitle]s. Pure functions, no network — the part covered by unit tests.
 * TMDB answers in French when asked (`language=fr-FR`), genre names included.
 */
object TmdbParsers {

    /** `{"genres":[{"id":28,"name":"Action"}]}` → id → name. */
    fun parseGenres(json: String): Map<Int, String> {
        val genres = runCatching { JSONObject(json).optJSONArray("genres") }.getOrNull() ?: return emptyMap()
        return genres.objects().mapNotNull { g ->
            val name = g.optString("name").ifEmpty { null } ?: return@mapNotNull null
            g.optInt("id", -1).takeIf { it >= 0 }?.let { it to name }
        }.toMap()
    }

    fun totalPages(json: String): Int = runCatching { JSONObject(json).optInt("total_pages", 1) }.getOrDefault(1).coerceAtLeast(1)

    /**
     * `results` of a search / chart / watchlist / rated page. [forcedKind] is set for endpoints that
     * only return one kind (their items carry no `media_type`); `/search/multi` passes null and
     * people (`media_type: person`) are dropped.
     */
    fun parseList(json: String, forcedKind: TitleKind?, genreNames: Map<Int, String>): List<TmdbListItem> {
        val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
        return results.objects().mapNotNull { item ->
            val kind = forcedKind ?: when (item.optString("media_type")) {
                "movie" -> TitleKind.MOVIE
                "tv" -> TitleKind.SERIES
                else -> return@mapNotNull null
            }
            val tmdbId = item.optLong("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val name = (if (kind == TitleKind.SERIES) item.optString("name") else item.optString("title")).trim()
            if (name.isEmpty()) return@mapNotNull null
            val date = item.optString(if (kind == TitleKind.SERIES) "first_air_date" else "release_date").ifEmpty { null }
            val release = parseDate(date)
            TmdbListItem(
                title = MediaTitle(
                    id = TitleKey.of(kind, tmdbId),
                    title = name,
                    kind = kind,
                    year = release?.let { LocalDate.ofEpochDay(it / SECONDS_PER_DAY).year } ?: date?.take(4)?.toIntOrNull(),
                    releaseDate = release,
                    posterUrl = TmdbImage.stored(item.optString("poster_path").ifEmpty { null }),
                    genres = item.optJSONArray("genre_ids")?.let { ids -> (0 until ids.length()).mapNotNull { genreNames[ids.optInt(it, -1)] } } ?: emptyList(),
                    plot = item.optString("overview").ifEmpty { null },
                    rating = item.optDoubleOrNull("vote_average")?.takeIf { item.optInt("vote_count", 1) > 0 },
                ),
                userRating = item.optDoubleOrNull("rating")?.let { Math.round(it).toInt() }?.coerceIn(1, 10),
            )
        }
    }

    /** `/movie/{id}` or `/tv/{id}` with `append_to_response=credits`. */
    fun parseDetails(json: String, kind: TitleKind): MediaTitle? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val tmdbId = o.optLong("id", -1).takeIf { it >= 0 } ?: return null
        val name = (if (kind == TitleKind.SERIES) o.optString("name") else o.optString("title")).trim()
        if (name.isEmpty()) return null
        val date = o.optString(if (kind == TitleKind.SERIES) "first_air_date" else "release_date").ifEmpty { null }
        val release = parseDate(date)
        val credits = o.optJSONObject("credits")
        val directors = if (kind == TitleKind.SERIES) {
            o.optJSONArray("created_by")?.objects()?.mapNotNull { it.optString("name").ifEmpty { null } }
        } else {
            credits?.optJSONArray("crew")?.objects()?.filter { it.optString("job") == "Director" }?.mapNotNull { it.optString("name").ifEmpty { null } }
        }
        val runtime = if (kind == TitleKind.SERIES) {
            o.optJSONArray("episode_run_time")?.let { if (it.length() > 0) it.optInt(0) else null }
        } else {
            o.optInt("runtime", 0)
        }?.takeIf { it > 0 }
        return MediaTitle(
            id = TitleKey.of(kind, tmdbId),
            title = name,
            kind = kind,
            year = release?.let { LocalDate.ofEpochDay(it / SECONDS_PER_DAY).year } ?: date?.take(4)?.toIntOrNull(),
            releaseDate = release,
            posterUrl = TmdbImage.stored(o.optString("poster_path").ifEmpty { null }),
            genres = o.optJSONArray("genres")?.objects()?.mapNotNull { it.optString("name").ifEmpty { null } } ?: emptyList(),
            plot = o.optString("overview").ifEmpty { null },
            rating = o.optDoubleOrNull("vote_average")?.takeIf { o.optInt("vote_count", 1) > 0 },
            runtimeMinutes = runtime,
            directors = directors?.takeIf { it.isNotEmpty() }?.joinToString(", "),
            cast = credits?.optJSONArray("cast")?.objects()?.mapNotNull { it.optString("name").ifEmpty { null } }?.take(6)?.takeIf { it.isNotEmpty() }?.joinToString(", "),
        )
    }

    /**
     * The people of a `/movie|tv/{id}?append_to_response=credits` answer, with their photos:
     * directors (the creators for a series) first, then the [maxCast] top-billed actors.
     */
    fun parseCredits(json: String, kind: TitleKind, maxCast: Int = 12): List<CastMember> {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val credits = o.optJSONObject("credits")
        val directorObjects = if (kind == TitleKind.SERIES) {
            o.optJSONArray("created_by")?.objects()
        } else {
            credits?.optJSONArray("crew")?.objects()?.filter { it.optString("job") == "Director" }
        }.orEmpty()
        fun JSONObject.photo() = optString("profile_path").ifEmpty { null }
        fun JSONObject.personId() = optLong("id", -1).takeIf { it >= 0 }
        val directors = directorObjects.mapNotNull { d ->
            d.optString("name").ifEmpty { null }?.let { CastMember(it, null, d.photo(), isDirector = true, personId = d.personId()) }
        }.distinctBy { it.name }
        val actors = credits?.optJSONArray("cast")?.objects().orEmpty().mapNotNull { a ->
            a.optString("name").ifEmpty { null }?.let { CastMember(it, a.optString("character").ifEmpty { null }, a.photo(), isDirector = false, personId = a.personId()) }
        }.take(maxCast)
        return directors + actors
    }

    /**
     * `/movie|tv/{id}/watch/providers`: the offers for [region], or null when TMDB has none for it.
     * "free" and "ads" (free with advertising) are one list here; each list is ordered by TMDB's display priority.
     */
    fun parseWatchProviders(json: String, region: String): WatchProviders? {
        val entry = runCatching { JSONObject(json).optJSONObject("results")?.optJSONObject(region) }.getOrNull() ?: return null
        fun providers(key: String): List<WatchProvider> =
            (entry.optJSONArray(key)?.objects() ?: emptyList())
                .sortedBy { it.optInt("display_priority", Int.MAX_VALUE) }
                .mapNotNull { p ->
                    val id = p.optInt("provider_id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                    val name = p.optString("provider_name").ifEmpty { null } ?: return@mapNotNull null
                    WatchProvider(id, name, TmdbImage.logo(p.optString("logo_path").ifEmpty { null }))
                }
        val result = WatchProviders(
            link = entry.optString("link").ifEmpty { null },
            subscription = providers("flatrate"),
            rent = providers("rent"),
            buy = providers("buy"),
            free = (providers("free") + providers("ads")).distinctBy { it.id },
        )
        return result.takeUnless { it.isEmpty }
    }

    /** `{"request_token":"…"}` / `{"session_id":"…"}` style single-string fields. */
    fun stringField(json: String, field: String): String? =
        runCatching { JSONObject(json).optString(field).ifEmpty { null } }.getOrNull()

    /** `/account` → id + username. */
    fun parseAccount(json: String): Pair<Long, String?>? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val id = o.optLong("id", -1).takeIf { it >= 0 } ?: return null
        return id to o.optString("username").ifEmpty { null }
    }

    /** `2010-07-16` (or `2010-7-6`) → UTC epoch seconds; a bare year, blank or garbage → null. */
    fun parseDate(text: String?): Long? {
        val parts = text?.trim()?.split('-') ?: return null
        if (parts.size != 3) return null
        val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
        return runCatching { LocalDate.of(y, m, d).atStartOfDay().toEpochSecond(ZoneOffset.UTC) }.getOrNull()
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optString(key).toDoubleOrNull() else null

    private const val SECONDS_PER_DAY = 86_400L
}
