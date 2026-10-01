package com.davidgcd.backlog.data.googlebooks

import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookKey
import com.davidgcd.backlog.model.BookPage
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.util.Isbn
import org.json.JSONArray
import org.json.JSONObject

/** Google Books `volumes` JSON → [Book]s (pure, covered by unit tests). Same rule as everywhere: no field is invented. */
object GoogleBooksParsers {
    const val PAGE_SIZE = 20

    fun parseSearch(json: String): BookPage {
        val items = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull() ?: return BookPage(emptyList(), hasMore = false)
        val books = (0 until items.length()).mapNotNull { items.optJSONObject(it)?.let(::parseVolume) }
        // Google's totalItems is a rough estimate: a full page is the only reliable "there may be more".
        return BookPage(books, hasMore = items.length() >= PAGE_SIZE)
    }

    fun parseVolume(volume: JSONObject): Book? {
        val id = volume.optString("id").ifEmpty { null } ?: return null
        val info = volume.optJSONObject("volumeInfo") ?: return null
        val title = info.optString("title").trim().ifEmpty { return null }
        val identifiers = info.optJSONArray("industryIdentifiers")
        val isbn13 = identifiers.identifier("ISBN_13")
        val isbn10 = identifiers.identifier("ISBN_10")
        val book = Book(
            key = "",
            title = title,
            subtitle = info.optString("subtitle").trim().ifEmpty { null },
            authors = info.optJSONArray("authors").strings(),
            description = info.optString("description").trim().ifEmpty { null },
            coverUrl = info.optJSONObject("imageLinks")?.let { links ->
                (links.optString("thumbnail").ifEmpty { links.optString("smallThumbnail") }).ifEmpty { null }
            }?.let(::secureCover),
            publishedYear = Regex("""^\d{4}""").find(info.optString("publishedDate"))?.value?.toIntOrNull(),
            publisher = info.optString("publisher").trim().ifEmpty { null },
            isbn10 = isbn10,
            isbn13 = isbn13,
            pageCount = info.optInt("pageCount", 0).takeIf { it > 0 },
            languages = info.optString("language").trim().ifEmpty { null }?.let(::listOf) ?: emptyList(),
            subjects = info.optJSONArray("categories").strings(),
            source = BookSource.GOOGLE_BOOKS,
        )
        // No ISBN: the Google volume id is the identity (an ISBN-ed volume is keyed by its ISBN like any other book).
        return book.copy(key = if (book.hasIsbn) BookKey.of(book) else "${BookKey.GOOGLE_PREFIX}$id")
    }

    /** Google serves thumbnails over http and with a page-curl effect: ask for the plain image over https. */
    internal fun secureCover(url: String): String =
        url.replaceFirst("http://", "https://").replace("&edge=curl", "")

    private fun JSONArray?.identifier(type: String): String? {
        if (this == null) return null
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            if (o.optString("type") == type) Isbn.normalize(o.optString("identifier"))?.let { return it }
        }
        return null
    }

    private fun JSONArray?.strings(): List<String> =
        this?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().ifEmpty { null } } } ?: emptyList()
}
