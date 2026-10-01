package com.davidgcd.backlog.data.openlibrary

import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookChart
import com.davidgcd.backlog.model.BookPage
import com.davidgcd.backlog.util.BookImage
import com.davidgcd.backlog.util.Isbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Open Library (https://openlibrary.org/developers/api): the main catalogue for books. Free, no API
 * key. This is the only class that talks to it — screens go through BookRepository. Parsing lives in
 * [OpenLibraryParsers]; this class only fetches.
 */
class OpenLibraryService(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://openlibrary.org",
) {
    /**
     * One page of works matching [query] — a title, an author or an ISBN (`9782070368228`, with or
     * without hyphens). French editions are favoured (`lang=fr`).
     */
    suspend fun searchBooks(query: String, page: Int = 1, limit: Int = OpenLibraryParsers.PAGE_SIZE): BookPage {
        val q = query.trim()
        if (q.isEmpty()) return BookPage(emptyList(), hasMore = false)
        val isbn = Isbn.fromQuery(q)
        val body = get(
            "/search.json",
            mapOf(
                "q" to if (isbn != null) "isbn:$isbn" else q,
                "page" to page.toString(),
                "limit" to limit.toString(),
                "lang" to "fr",
                "fields" to SEARCH_FIELDS,
            ),
        )
        return OpenLibraryParsers.parseSearch(body, page, queryIsbn = isbn)
    }

    /** A Discover list: the works trending this week, or the popular ones of a subject (at most [limit]). */
    suspend fun chart(chart: BookChart, limit: Int = 40): List<Book> =
        OpenLibraryParsers.parseSearch(get(chart.path, mapOf("limit" to limit.toString()))).books

    /**
     * What Open Library knows of one book beyond the search hit: its edition (ISBN, publisher, pages,
     * language — found by edition id, else by ISBN) and its work (description, subjects). Null when it
     * knows neither; a partly answered read still returns what it got.
     */
    suspend fun getBook(workId: String?, editionId: String?, isbn: String?): Book? {
        val edition = when {
            editionId != null -> optional("/books/$editionId.json")?.let(OpenLibraryParsers::parseEdition)
            isbn != null -> optional("/isbn/$isbn.json")?.let(OpenLibraryParsers::parseEdition)
            else -> null
        }
        val work = (workId ?: edition?.workId)
            ?.let { optional("/works/$it.json") }
            ?.let(OpenLibraryParsers::parseWork)
        return when {
            edition != null && work != null -> edition.enrichedWith(work)
            else -> edition ?: work
        }
    }

    /** The editions of a work (at most [limit]), the physical books behind one search hit. */
    suspend fun getEditions(workId: String, limit: Int = 20): List<Book> =
        OpenLibraryParsers.parseEditions(get("/works/$workId/editions.json", mapOf("limit" to limit.toString())))

    /** The cover of a book at size `S`, `M` or `L` — see [BookImage]. */
    fun getBookCover(book: Book, size: Char = 'M'): String? = BookImage.sized(book.coverUrl, size)

    /** A 404 is "unknown book" (null); any other failure propagates. */
    private suspend fun optional(path: String): String? = try {
        get(path)
    } catch (e: BookSourceException) {
        if (e.error == BookSourceError.NOT_FOUND) null else throw e
    }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json").build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> throw BookSourceException(BookSourceError.NOT_FOUND, "HTTP 404 $path")
                    !response.isSuccessful -> throw BookSourceException(BookSourceError.UNAVAILABLE, "HTTP ${response.code} $path")
                    else -> response.body?.string().orEmpty()
                }
            }
        } catch (e: BookSourceException) {
            throw e
        } catch (e: IOException) {
            throw BookSourceException(BookSourceError.UNAVAILABLE, "I/O: ${e.message}", e)
        }
    }

    private companion object {
        // Open Library asks API clients to identify themselves.
        const val USER_AGENT = "Backlog-Android/1.0 (https://github.com/notsogeek87/backlog)"
        const val SEARCH_FIELDS = "key,title,subtitle,author_name,first_publish_year,cover_i,cover_edition_key,language,number_of_pages_median,subject"
    }
}
