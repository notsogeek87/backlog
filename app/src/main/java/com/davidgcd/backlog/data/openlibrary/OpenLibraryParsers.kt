package com.davidgcd.backlog.data.openlibrary

import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookKey
import com.davidgcd.backlog.model.BookPage
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.util.BookImage
import com.davidgcd.backlog.util.BookSubjects
import com.davidgcd.backlog.util.Isbn
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns Open Library's JSON into [Book]s. Pure functions, no network — the part covered by unit tests.
 * Every field is optional in Open Library's data: nothing is invented, a missing field stays null / empty.
 */
object OpenLibraryParsers {
    const val PAGE_SIZE = 20
    private const val MAX_SUBJECTS = 12

    /**
     * `search.json` → one [Book] per *work*. A work-level hit does not know which ISBN is "the" one (the
     * `isbn` array lists every edition), so none is taken from it — except the one the user typed
     * ([queryIsbn]), which is exactly the edition they are after. The ISBN of the cover edition is read
     * later (see [parseEdition]).
     */
    fun parseSearch(json: String, page: Int = 1, queryIsbn: String? = null): BookPage {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return BookPage(emptyList(), hasMore = false)
        // `search.json` answers `docs`; the trending and subject lists answer `works` (same hit, a few fields named differently).
        val docs = root.optJSONArray("docs") ?: root.optJSONArray("works") ?: return BookPage(emptyList(), hasMore = false)
        val books = docs.objects().mapNotNull { parseSearchDoc(it, queryIsbn) }
        val total = root.optInt("numFound", 0)
        return BookPage(books, hasMore = page * PAGE_SIZE < total)
    }

    private fun parseSearchDoc(doc: JSONObject, queryIsbn: String?): Book? {
        val title = doc.optString("title").trim().ifEmpty { return null }
        val workId = doc.optString("key").substringAfterLast('/').takeIf { WORK_ID.matches(it) }
        val editionId = doc.optString("cover_edition_key").trim().takeIf { EDITION_ID.matches(it) }
        val coverId = (doc.optLong("cover_i", -1).takeIf { it > 0 } ?: doc.optLong("cover_id", -1)).takeIf { it > 0 }
        val languages = doc.optJSONArray("language").strings()
        val book = Book(
            key = "",
            title = title,
            subtitle = doc.optString("subtitle").trim().ifEmpty { null },
            authors = doc.optJSONArray("author_name").strings()
                .ifEmpty { doc.optJSONArray("authors")?.objects()?.mapNotNull { it.optString("name").trim().ifEmpty { null } } ?: emptyList() },
            coverUrl = coverId?.let(BookImage::byCoverId) ?: editionId?.let(BookImage::byEditionId),
            publishedYear = doc.optInt("first_publish_year", 0).takeIf { it > 0 },
            isbn10 = queryIsbn?.let(Isbn::toIsbn10),
            isbn13 = queryIsbn?.let(Isbn::toIsbn13),
            pageCount = doc.optInt("number_of_pages_median", 0).takeIf { it > 0 },
            // The list is every language the work exists in: only a single one says something about *this* edition.
            languages = languages.singleOrNull()?.let(::listOf) ?: emptyList(),
            subjects = BookSubjects.clean(doc.optJSONArray("subject").strings(), MAX_SUBJECTS),
            workId = workId,
            editionId = editionId,
            source = BookSource.OPEN_LIBRARY,
        )
        return BookKey.withKey(book)
    }

    /** `/works/OL45804W.json`: what a work knows that a search hit doesn't — the description, mostly. */
    fun parseWork(json: String): Book? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val title = o.optString("title").trim().ifEmpty { return null }
        return Book(
            key = "",
            title = title,
            subtitle = o.optString("subtitle").trim().ifEmpty { null },
            description = description(o.opt("description")),
            coverUrl = o.optJSONArray("covers")?.positiveLongs()?.firstOrNull()?.let(BookImage::byCoverId),
            publishedYear = year(o.optString("first_publish_date")),
            subjects = BookSubjects.clean(o.optJSONArray("subjects").strings(), MAX_SUBJECTS),
            workId = o.optString("key").substringAfterLast('/').takeIf { WORK_ID.matches(it) },
        )
    }

    /** `/books/OL7353617M.json` or `/isbn/<isbn>.json`: one physical edition. */
    fun parseEdition(json: String): Book? = runCatching { JSONObject(json) }.getOrNull()?.let(::parseEdition)

    private fun parseEdition(o: JSONObject): Book? {
        val title = o.optString("title").trim().ifEmpty { return null }
        val isbn13 = o.optJSONArray("isbn_13").strings().firstNotNullOfOrNull(Isbn::normalize)
        val isbn10 = o.optJSONArray("isbn_10").strings().firstNotNullOfOrNull(Isbn::normalize)
        return Book(
            key = "",
            title = title,
            subtitle = o.optString("subtitle").trim().ifEmpty { null },
            description = description(o.opt("description")),
            coverUrl = o.optJSONArray("covers")?.positiveLongs()?.firstOrNull()?.let(BookImage::byCoverId),
            publishedYear = year(o.optString("publish_date")),
            publisher = o.optJSONArray("publishers").strings().firstOrNull(),
            isbn10 = isbn10,
            isbn13 = isbn13,
            pageCount = o.optInt("number_of_pages", 0).takeIf { it > 0 },
            languages = o.optJSONArray("languages")?.objects()
                ?.mapNotNull { it.optString("key").substringAfterLast('/').ifEmpty { null } }
                ?: emptyList(),
            subjects = BookSubjects.clean(o.optJSONArray("subjects").strings(), MAX_SUBJECTS),
            workId = o.optJSONArray("works")?.objects()?.firstOrNull()?.optString("key")
                ?.substringAfterLast('/')?.takeIf { WORK_ID.matches(it) },
            editionId = o.optString("key").substringAfterLast('/').takeIf { EDITION_ID.matches(it) },
        )
    }

    /** `/works/OL45804W/editions.json` → its editions; editions without any ISBN or title are skipped. */
    fun parseEditions(json: String): List<Book> {
        val entries = runCatching { JSONObject(json).optJSONArray("entries") }.getOrNull() ?: return emptyList()
        return entries.objects().mapNotNull { entry ->
            parseEdition(entry)?.takeIf { it.hasIsbn || it.editionId != null }
        }
    }

    /** Open Library writes a description either as a bare string or as `{"type": …, "value": "…"}`. */
    private fun description(value: Any?): String? = when (value) {
        is String -> value
        is JSONObject -> value.optString("value")
        else -> null
    }?.trim()?.ifEmpty { null }

    /** `1965`, `March 1990`, `1990-03-01`, `[1982?]` → the year. */
    internal fun year(text: String?): Int? = text?.let { YEAR.find(it)?.value?.toIntOrNull() }

    private val WORK_ID = Regex("""^OL\d+W$""")
    private val EDITION_ID = Regex("""^OL\d+M$""")
    private val YEAR = Regex("""\b(1[0-9]{3}|20[0-9]{2})\b""")

    private fun JSONArray?.strings(): List<String> =
        this?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().ifEmpty { null } } } ?: emptyList()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray.positiveLongs(): List<Long> = (0 until length()).map { optLong(it, -1) }.filter { it > 0 }
}
