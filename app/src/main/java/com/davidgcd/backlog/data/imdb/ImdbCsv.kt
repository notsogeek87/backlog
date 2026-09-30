package com.davidgcd.backlog.data.imdb

import com.davidgcd.backlog.data.csv.CsvFormat
import com.davidgcd.backlog.model.ImdbTitle
import com.davidgcd.backlog.model.TitleKind

/** One line of an IMDb export (watchlist / ratings): the title plus what the user did with it. */
data class ImdbCsvRow(
    val title: ImdbTitle,
    /** The user's own rating out of 10; null on a watchlist line that was never rated. */
    val userRating: Int?,
)

/**
 * Reads the CSV files IMDb lets a signed-in user export: `/list/ls…/export` (watchlist) and
 * `/user/ur…/ratings/export`. Both carry `Const` and `Title`; the rest is looked up by header name
 * so a column IMDb adds, drops or reorders doesn't break the import.
 */
object ImdbCsv {
    fun looksLikeExport(text: String): Boolean {
        val header = text.removePrefix("\uFEFF").lineSequence().firstOrNull()?.lowercase() ?: return false
        return header.contains("const") && header.contains("title")
    }

    fun parse(text: String): List<ImdbCsvRow> {
        val records = splitRecords(text.removePrefix("\uFEFF"))
        if (records.isEmpty()) return emptyList()
        val header = CsvFormat.parseRow(records.first()).map { it.trim().lowercase() }
        fun column(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val const = column("const")
        val title = column("title")
        if (const < 0 || title < 0) return emptyList()
        val rating = column("your rating")
        val type = column("title type")
        val imdbRating = column("imdb rating")
        val runtime = column("runtime (mins)")
        val year = column("year")
        val genres = column("genres")
        val released = column("release date")
        val directors = column("directors")
        val description = column("description")

        return records.drop(1).mapNotNull { record ->
            val f = CsvFormat.parseRow(record)
            fun at(i: Int) = f.getOrNull(i)?.trim()?.takeIf { i >= 0 && it.isNotEmpty() }
            val id = at(const)?.takeIf { it.matches(Regex("""tt\d+""")) } ?: return@mapNotNull null
            val name = at(title) ?: return@mapNotNull null
            val kind = kindOf(at(type)) ?: return@mapNotNull null
            val releaseDate = ImdbParsers.parseDate(at(released))
            ImdbCsvRow(
                title = ImdbTitle(
                    id = id,
                    title = name,
                    kind = kind,
                    year = at(year)?.take(4)?.toIntOrNull(),
                    releaseDate = releaseDate,
                    genres = at(genres)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                    plot = at(description),
                    rating = at(imdbRating)?.toDoubleOrNull(),
                    runtimeMinutes = at(runtime)?.toIntOrNull(),
                    directors = at(directors),
                ),
                userRating = at(rating)?.toIntOrNull()?.takeIf { it in 1..10 },
            )
        }
    }

    /** Null = not something this side of the app tracks (video games, podcasts, episodes). */
    private fun kindOf(type: String?): TitleKind? = when (type?.lowercase()?.trim()) {
        null, "", "movie", "tv movie", "tv special", "short", "tv short", "video", "documentary" -> TitleKind.MOVIE
        "tv series", "tv mini series", "tv mini-series" -> TitleKind.SERIES
        else -> null
    }

    /** Splits on line breaks that are not inside a quoted field (a description may span lines). */
    internal fun splitRecords(text: String): List<String> {
        val records = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in text) {
            when {
                c == '"' -> { inQuotes = !inQuotes; current.append(c) }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (current.isNotEmpty()) records += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) records += current.toString()
        return records
    }
}
