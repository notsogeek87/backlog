package com.davidgcd.backlog.data.imdb

import com.davidgcd.backlog.model.ImdbTitle
import com.davidgcd.backlog.model.TitleKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Everything that turns IMDb's own payloads (suggestion JSON, page JSON-LD, chart data, CSV exports)
 * into [ImdbTitle]s. Pure functions, no network: IMDb has no public API, so this is the one place to
 * fix when it changes a page — and the one covered by unit tests.
 */
object ImdbParsers {
    private val ID_REGEX = Regex("""tt\d{6,}""")
    private val LD_JSON_REGEX = Regex(
        """<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val NEXT_DATA_REGEX = Regex(
        """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    fun idFromUrl(url: String?): String? = url?.let { ID_REGEX.find(it)?.value }

    // --- suggestion API (search) -------------------------------------------------------------

    /**
     * `https://v3.sg.media-imdb.com/suggestion/x/<query>.json`. Keeps titles only: people (`nm…`),
     * companies and the like are dropped, and so are video games and podcasts (the games side of
     * the app is IGDB's).
     */
    fun parseSuggestions(json: String): List<ImdbTitle> {
        val items = runCatching { JSONObject(json).optJSONArray("d") }.getOrNull() ?: return emptyList()
        return items.objects().mapNotNull { item ->
            val id = item.optString("id")
            if (!ID_REGEX.matches(id)) return@mapNotNull null
            val type = item.optString("qid").ifEmpty { item.optString("q") }
            val kind = kindFromSuggestionType(type) ?: return@mapNotNull null
            val title = item.optString("l").trim()
            if (title.isEmpty()) return@mapNotNull null
            ImdbTitle(
                id = id,
                title = title,
                kind = kind,
                year = item.optIntOrNull("y"),
                posterUrl = item.optJSONObject("i")?.optString("imageUrl")?.ifEmpty { null },
                cast = item.optString("s").ifEmpty { null },
            )
        }
    }

    private fun kindFromSuggestionType(type: String): TitleKind? = when (type.lowercase()) {
        "movie", "tvmovie", "short", "tvshort", "video", "tvspecial", "feature", "tv movie", "tv special" -> TitleKind.MOVIE
        "tvseries", "tvminiseries", "tv series", "tv mini-series", "tv mini series" -> TitleKind.SERIES
        "tvepisode", "videogame", "podcastseries", "podcastepisode", "musicvideo" -> null
        // An unknown type is more likely a new kind of film than a new kind of person.
        else -> if (type.isBlank()) TitleKind.MOVIE else null
    }

    // --- title page (JSON-LD) ----------------------------------------------------------------

    /** The `application/ld+json` block of a `/title/tt…/` page, or null if the page has none we understand. */
    fun parseTitlePage(html: String, fallbackId: String? = null): ImdbTitle? {
        for (block in ldJsonBlocks(html)) {
            val obj = runCatching { JSONObject(block) }.getOrNull() ?: continue
            titleFromLd(obj, fallbackId)?.let { return it }
        }
        return null
    }

    private fun titleFromLd(obj: JSONObject, fallbackId: String?): ImdbTitle? {
        val type = obj.optString("@type")
        val kind = when (type) {
            "Movie", "TVMovie", "ShortFilm", "CreativeWork" -> TitleKind.MOVIE
            "TVSeries", "TVMiniSeries" -> TitleKind.SERIES
            else -> return null
        }
        val id = idFromUrl(obj.optString("url")) ?: fallbackId ?: return null
        val name = unescapeHtml(obj.optString("name")).trim()
        if (name.isEmpty()) return null
        val published = obj.optString("datePublished").ifEmpty { null }
        val release = parseDate(published)
        return ImdbTitle(
            id = id,
            title = name,
            kind = kind,
            year = release?.let { LocalDate.ofEpochDay(it / SECONDS_PER_DAY).year } ?: published?.take(4)?.toIntOrNull(),
            releaseDate = release,
            posterUrl = imageOf(obj.opt("image")),
            genres = strings(obj.opt("genre")).map(::unescapeHtml),
            plot = obj.optString("description").ifEmpty { null }?.let(::unescapeHtml),
            rating = obj.optJSONObject("aggregateRating")?.optDoubleOrNull("ratingValue"),
            runtimeMinutes = parseIsoDurationMinutes(obj.optString("duration")),
            directors = names(obj.opt("director")).takeIf { it.isNotEmpty() }?.joinToString(", "),
            cast = names(obj.opt("actor")).takeIf { it.isNotEmpty() }?.take(6)?.joinToString(", "),
        )
    }

    // --- charts ------------------------------------------------------------------------------

    /** A `/chart/…` page: the JSON-LD ItemList when present, else the page's `__NEXT_DATA__`. */
    fun parseChart(html: String): List<ImdbTitle> {
        val fromLd = ldJsonBlocks(html).asSequence()
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .map(::chartFromLd)
            .firstOrNull { it.isNotEmpty() }
        if (fromLd != null) return fromLd
        val next = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1) ?: return emptyList()
        return runCatching { chartFromNextData(JSONObject(next)) }.getOrDefault(emptyList())
    }

    private fun chartFromLd(root: JSONObject): List<ImdbTitle> {
        val elements = root.optJSONArray("itemListElement") ?: return emptyList()
        return elements.objects().mapNotNull { element ->
            val item = element.optJSONObject("item") ?: element
            val url = item.optString("url")
            val id = idFromUrl(url) ?: return@mapNotNull null
            val name = unescapeHtml(item.optString("name")).trim()
            if (name.isEmpty()) return@mapNotNull null
            ImdbTitle(
                id = id,
                title = name,
                kind = if (item.optString("@type").startsWith("TV") && item.optString("@type") != "TVMovie") TitleKind.SERIES else TitleKind.MOVIE,
                posterUrl = imageOf(item.opt("image")),
                genres = strings(item.opt("genre")).map(::unescapeHtml),
                plot = item.optString("description").ifEmpty { null }?.let(::unescapeHtml),
                rating = item.optJSONObject("aggregateRating")?.optDoubleOrNull("ratingValue"),
                runtimeMinutes = parseIsoDurationMinutes(item.optString("duration")),
            )
        }
    }

    private fun chartFromNextData(root: JSONObject): List<ImdbTitle> {
        val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps") ?: return emptyList()
        val edges = pageProps.optJSONObject("pageData")?.optJSONObject("chartTitles")?.optJSONArray("edges")
            ?: pageProps.optJSONObject("chartTitles")?.optJSONArray("edges")
            ?: return emptyList()
        return edges.objects().mapNotNull { edge ->
            val node = edge.optJSONObject("node") ?: edge
            val id = node.optString("id").takeIf { ID_REGEX.matches(it) } ?: return@mapNotNull null
            val name = node.optJSONObject("titleText")?.optString("text")?.trim().orEmpty()
                .ifEmpty { node.optJSONObject("originalTitleText")?.optString("text")?.trim().orEmpty() }
            if (name.isEmpty()) return@mapNotNull null
            val typeId = node.optJSONObject("titleType")?.optString("id").orEmpty()
            ImdbTitle(
                id = id,
                title = name,
                kind = kindFromSuggestionType(typeId) ?: TitleKind.MOVIE,
                year = node.optJSONObject("releaseYear")?.optIntOrNull("year"),
                posterUrl = node.optJSONObject("primaryImage")?.optString("url")?.ifEmpty { null },
                rating = node.optJSONObject("ratingsSummary")?.optDoubleOrNull("aggregateRating"),
                runtimeMinutes = node.optJSONObject("runtime")?.optIntOrNull("seconds")?.let { it / 60 },
                plot = node.optJSONObject("plot")?.optJSONObject("plotText")?.optString("plainText")?.ifEmpty { null },
                genres = node.optJSONObject("titleGenres")?.optJSONArray("genres")?.objects()
                    ?.mapNotNull { it.optJSONObject("genre")?.optString("text")?.ifEmpty { null } }
                    ?: emptyList(),
            )
        }
    }

    // --- account pages -----------------------------------------------------------------------

    private val USER_ID_REGEX = Regex("""/user/(ur\d+)""")
    private val LIST_ID_PATTERNS = listOf(
        Regex("""/list/(ls\d{4,})/export"""),
        Regex("""["']predefinedList["']\s*:\s*\{[^}]*?["']id["']\s*:\s*["'](ls\d{4,})["']"""),
        Regex("""["']list_id["']\s*:\s*["'](ls\d{4,})["']"""),
        Regex("""data-list-id=["'](ls\d{4,})["']"""),
    )

    fun userIdFromUrl(url: String): String? = USER_ID_REGEX.find(url)?.groupValues?.get(1)

    /** The id (`ls…`) of the user's watchlist, from their watchlist page (or its final URL). */
    fun watchlistIdFrom(url: String, html: String): String? {
        Regex("""/list/(ls\d{4,})""").find(url)?.let { return it.groupValues[1] }
        for (pattern in LIST_ID_PATTERNS) pattern.find(html)?.let { return it.groupValues[1] }
        return null
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun ldJsonBlocks(html: String): List<String> = LD_JSON_REGEX.findAll(html).map { it.groupValues[1].trim() }.toList()

    private fun imageOf(value: Any?): String? = when (value) {
        is String -> value.ifEmpty { null }
        is JSONObject -> value.optString("url").ifEmpty { null }
        is JSONArray -> imageOf(value.opt(0))
        else -> null
    }

    private fun strings(value: Any?): List<String> = when (value) {
        is String -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).trim().ifEmpty { null } }
        else -> emptyList()
    }

    private fun names(value: Any?): List<String> = when (value) {
        is JSONObject -> listOfNotNull(value.optString("name").trim().ifEmpty { null })
        is JSONArray -> value.objects().mapNotNull { it.optString("name").trim().ifEmpty { null } }
        else -> emptyList()
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key).takeIf { it != 0 || optString(key) == "0" } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optString(key).toDoubleOrNull() else null

    /** `PT2H28M` → 148. */
    fun parseIsoDurationMinutes(duration: String?): Int? {
        if (duration.isNullOrBlank()) return null
        val match = Regex("""^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$""").find(duration.trim()) ?: return null
        val (h, m, s) = match.destructured
        if (h.isEmpty() && m.isEmpty() && s.isEmpty()) return null
        return (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0) + if ((s.toIntOrNull() ?: 0) >= 30) 1 else 0
    }

    /** `2010-07-16` (or `2010-7-6`) → UTC epoch seconds; a bare year or garbage → null. */
    fun parseDate(text: String?): Long? {
        val parts = text?.trim()?.split('-') ?: return null
        if (parts.size != 3) return null
        val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
        return runCatching { LocalDate.of(y, m, d).atStartOfDay().toEpochSecond(ZoneOffset.UTC) }.getOrNull()
    }

    /** IMDb HTML escapes quotes and apostrophes even inside its JSON-LD. */
    fun unescapeHtml(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    private const val SECONDS_PER_DAY = 86_400L
}
