package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.googlebooks.GoogleBooksService
import com.davidgcd.backlog.data.local.BookDao
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.joinForStorage
import com.davidgcd.backlog.data.local.languageList
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.local.subjectList
import com.davidgcd.backlog.data.openlibrary.BookSourceError
import com.davidgcd.backlog.data.openlibrary.BookSourceException
import com.davidgcd.backlog.data.openlibrary.OpenLibraryService
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookChart
import com.davidgcd.backlog.model.BookDuplicates
import com.davidgcd.backlog.model.BookKey
import com.davidgcd.backlog.model.BookRanking
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.model.BookText
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.util.Isbn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** One page of search results; [fromFallback] is true when Open Library had nothing and Google Books answered. */
data class BookSearchResult(val books: List<Book>, val hasMore: Boolean, val fromFallback: Boolean = false)

sealed interface BookAddResult {
    data class Added(val key: String) : BookAddResult
    /** The book (or this very edition) is already in the list, under [existingKey]. */
    data class Duplicate(val existingKey: String) : BookAddResult
}

/**
 * Single source of truth for books: Room locally, Open Library (then Google Books) for search and
 * details — the book-side twin of [MovieRepository]. The flow is catalogue → local row → UI: once a
 * book is added, every screen reads the row, so the list and the fiche work offline.
 */
class BookRepository(
    private val dao: BookDao,
    private val openLibrary: OpenLibraryService,
    private val googleBooks: GoogleBooksService,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Books seen in a search this session: opening one shows what the list already knew even if the catalogue is down. */
    private val seen = ConcurrentHashMap<String, Book>()

    private val searchCache = object : LinkedHashMap<String, Pair<Long, BookSearchResult>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, BookSearchResult>>?) = size > SEARCH_CACHE_SIZE
    }
    private val editionsCache = ConcurrentHashMap<String, List<Book>>()

    fun observeAll(): Flow<List<BookEntity>> = dao.observeAll()

    suspend fun allBooks(): List<BookEntity> = dao.allBooks()

    fun observe(bookKey: String): Flow<BookEntity?> = dao.observeById(bookKey)

    suspend fun find(bookKey: String): BookEntity? = dao.findById(bookKey)

    // --- search ------------------------------------------------------------------------------

    /**
     * Open Library first; Google Books only if Open Library is down or has nothing. Throws
     * [BookSourceException] only when no catalogue could be asked at all (offline).
     */
    suspend fun search(query: String, page: Int = 1): BookSearchResult {
        val q = query.trim()
        if (q.isEmpty()) return BookSearchResult(emptyList(), hasMore = false)
        val cacheKey = "${q.lowercase()}|$page"
        synchronized(searchCache) { searchCache[cacheKey] }?.let { (at, result) ->
            if (now() - at < SEARCH_CACHE_TTL_MS) return result
        }

        var failure: BookSourceException? = null
        var answered = false
        val primary = try {
            openLibrary.searchBooks(q, page).also { answered = true }
        } catch (e: BookSourceException) {
            failure = e
            null
        }
        if (primary != null && primary.books.isNotEmpty()) {
            return finish(cacheKey, q, BookSearchResult(primary.books, primary.hasMore))
        }
        val secondary = try {
            googleBooks.searchBooks(q, page).also { answered = true }
        } catch (e: BookSourceException) {
            failure = failure ?: e
            null
        }
        if (secondary != null && secondary.books.isNotEmpty()) {
            return finish(cacheKey, q, BookSearchResult(secondary.books, secondary.hasMore, fromFallback = true))
        }
        // Nobody had anything: that's "no result" if at least one catalogue answered, an error if none did.
        if (!answered) throw failure ?: BookSourceException(BookSourceError.UNAVAILABLE)
        return BookSearchResult(emptyList(), hasMore = false)
    }

    /** A Discover list (see [BookChart]), cached like a search so switching tabs doesn't ask again. Throws when Open Library can't be reached. */
    suspend fun chart(chart: BookChart): List<Book> {
        val cacheKey = "chart:${chart.name}"
        synchronized(searchCache) { searchCache[cacheKey] }?.let { (at, result) ->
            if (now() - at < SEARCH_CACHE_TTL_MS) return result.books
        }
        val books = openLibrary.chart(chart)
        books.forEach { seen[it.key] = it }
        synchronized(searchCache) { searchCache[cacheKey] = now() to BookSearchResult(books, hasMore = false) }
        return books
    }

    private fun finish(cacheKey: String, query: String, raw: BookSearchResult): BookSearchResult {
        val result = raw.copy(books = BookRanking.rank(raw.books, query))
        result.books.forEach { seen[it.key] = it }
        synchronized(searchCache) { searchCache[cacheKey] = now() to result }
        return result
    }

    /**
     * The book behind [key] with whatever Open Library adds to what the search showed (edition facts,
     * description); the search hit alone if the catalogue can't be read. Null only if [key] was never seen.
     */
    suspend fun fetchRemote(key: String): Book? {
        val known = seen[key] ?: return null
        val merged = localize(readFresh(known)?.let { known.enrichedWith(it) } ?: known)
        seen[key] = merged
        return merged
    }

    /** The other physical editions of the work behind [book]; empty when unknown or unreachable (the card is then hidden). */
    suspend fun editions(book: Book): List<Book> {
        val workId = book.workId ?: return emptyList()
        editionsCache[workId]?.let { return it }
        val editions = try {
            openLibrary.getEditions(workId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return emptyList()
        }
        // An edition record has no author: it borrows the work's, and keeps its own title (often a translation).
        val result = editions.map { BookKey.withKey(it.asEditionOf(book)) }.distinctBy { it.key }
        editionsCache[workId] = result
        result.forEach { seen[it.key] = it }
        return result
    }

    private suspend fun readFresh(book: Book): Book? {
        if (book.source != BookSource.OPEN_LIBRARY) return null
        if (book.workId == null && book.editionId == null && !book.hasIsbn) return null
        return try {
            openLibrary.getBook(book.workId, book.editionId, book.canonicalIsbn13)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    // --- the list ----------------------------------------------------------------------------

    /** The saved book that [book] duplicates (same ISBN, same Open Library edition, same title + author), or null. */
    suspend fun findDuplicate(book: Book): BookEntity? {
        val saved = dao.allBooks()
        val match = BookDuplicates.find(book, saved.map { it.toBook() }) ?: return null
        return saved.firstOrNull { it.bookKey == match.key }
    }

    suspend fun add(book: Book, status: ReadStatus = ReadStatus.TO_READ): BookAddResult =
        addSaved(book.toEntity(status, now()))

    /** Adds a row as is (status, favorite, rating included) unless it duplicates one already in the list — the CSV import's entry point. */
    suspend fun addSaved(entity: BookEntity): BookAddResult {
        dao.findById(entity.bookKey)?.let { return BookAddResult.Duplicate(it.bookKey) }
        findDuplicate(entity.toBook())?.let { return BookAddResult.Duplicate(it.bookKey) }
        dao.upsert(entity)
        return BookAddResult.Added(entity.bookKey)
    }

    suspend fun setStatus(entity: BookEntity, status: ReadStatus) =
        dao.update(entity.copy(status = status.name, updatedAt = now()))

    suspend fun setFavorite(entity: BookEntity, favorite: Boolean) =
        dao.update(entity.copy(isFavorite = favorite, updatedAt = now()))

    /** 1–5, or null to clear. */
    suspend fun setUserRating(entity: BookEntity, rating: Int?) =
        dao.update(entity.copy(userRating = rating?.coerceIn(1, 5), updatedAt = now()))

    suspend fun remove(entity: BookEntity) = dao.delete(entity)

    /**
     * Fills in what a saved book is missing (description, ISBN, publisher, pages…) from Open Library,
     * keeping everything the user set. Best effort: offline or unknown just leaves the row as it is.
     */
    suspend fun enrich(bookKey: String) {
        val entity = dao.findById(bookKey) ?: return
        val complete = entity.publisher != null && entity.pageCount != null && entity.isbn13 != null && entity.languages != null
        if (complete && entity.description?.let(BookText::looksFrench) == true) return
        val book = entity.toBook()
        val enriched = readFresh(book)?.let { book.enrichedWith(it) } ?: book
        val updated = localize(enriched).toEntity(entity.readStatus, entity.addedAt)
            .copy(isFavorite = entity.isFavorite, userRating = entity.userRating, updatedAt = now())
        if (updated.copy(updatedAt = entity.updatedAt) != entity) dao.update(updated)
    }

    /**
     * A row read from a CSV, completed from the catalogue: a file with just a title, an author or an ISBN
     * gets its cover, description, publisher, pages and ids from Open Library (Google Books as fallback),
     * found by ISBN, else by title + author. The row keeps its key, status, favorite, rating and date.
     * Best effort: offline, no match, or a match that isn't the same title returns the row untouched,
     * so an import never fails (or waits) because of the catalogue.
     */
    suspend fun completeFromCatalog(entity: BookEntity): BookEntity {
        if (entity.coverUrl != null && entity.description != null) return entity
        val book = entity.toBook()
        val hit = try {
            val query = book.canonicalIsbn13 ?: "${book.title} ${book.authors.firstOrNull().orEmpty()}".trim()
            search(query).books.firstOrNull { it.matches(book) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        } ?: return entity
        val merged = localize(book.enrichedWith(hit).let { first -> readFresh(first)?.let(first::enrichedWith) ?: first })
        return merged.toEntity(entity.readStatus, entity.addedAt).copy(
            bookKey = entity.bookKey,
            isFavorite = entity.isFavorite,
            userRating = entity.userRating,
            updatedAt = entity.updatedAt,
        )
    }

    private val frenchDescriptions = ConcurrentHashMap<String, Optional<String>>()

    /**
     * The description in French: kept if it already is, else looked up on Google Books restricted to French
     * (by ISBN, then by title + author). Open Library's descriptions are mostly English; when no French one
     * exists the original is kept — better an English description than none.
     */
    private suspend fun localize(book: Book): Book {
        val current = book.description
        if (current != null && BookText.looksFrench(current)) return book
        val answer = frenchDescriptions[book.key] ?: findFrenchDescription(book)?.also { frenchDescriptions[book.key] = it }
        val french = answer?.orElse(null)
        return if (french != null) book.copy(description = french) else book
    }

    /** Empty = Google has no French description for it (remembered); null = couldn't ask (offline: asked again next time). */
    private suspend fun findFrenchDescription(book: Book): Optional<String>? {
        val queries = listOfNotNull(
            book.canonicalIsbn13?.let { "isbn:$it" },
            "intitle:${book.title}" + book.authors.firstOrNull()?.let { " inauthor:$it" }.orEmpty(),
        )
        for (query in queries) {
            val hits = try {
                googleBooks.searchBooks(query, limit = FRENCH_LOOKUP_SIZE, langRestrict = "fr").books
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                return null
            }
            val found = hits.firstOrNull { hit ->
                hit.description?.let(BookText::looksFrench) == true && BookText.normalize(hit.title).let { t ->
                    val mine = BookText.normalize(book.title)
                    t == mine || t.startsWith(mine) || mine.startsWith(t)
                }
            }
            if (found != null) return Optional.ofNullable(found.description)
        }
        return Optional.empty()
    }

    /** The same book: same ISBN when both have one, else the same title (accents and case aside). */
    private fun Book.matches(other: Book): Boolean {
        val mine = canonicalIsbn13
        val theirs = other.canonicalIsbn13
        if (mine != null && theirs != null) return mine == theirs
        return BookText.normalize(title) == BookText.normalize(other.title)
    }

    private companion object {
        const val SEARCH_CACHE_SIZE = 30
        const val FRENCH_LOOKUP_SIZE = 5
        const val SEARCH_CACHE_TTL_MS = 10 * 60 * 1000L
    }
}

fun Book.toEntity(status: ReadStatus = ReadStatus.TO_READ, addedAt: Long = System.currentTimeMillis()) = BookEntity(
    bookKey = key,
    title = title,
    subtitle = subtitle,
    authors = authors.joinForStorage(),
    description = description,
    coverUrl = coverUrl,
    publishedYear = publishedYear,
    publisher = publisher,
    isbn10 = isbn10,
    isbn13 = isbn13,
    pageCount = pageCount,
    languages = languages.joinForStorage(),
    subjects = subjects.joinForStorage(),
    workId = workId,
    editionId = editionId,
    source = source.name,
    status = status.name,
    addedAt = addedAt,
    updatedAt = addedAt,
)

fun BookEntity.toBook() = Book(
    key = bookKey,
    title = title,
    subtitle = subtitle,
    authors = authorList,
    description = description,
    coverUrl = coverUrl,
    publishedYear = publishedYear,
    publisher = publisher,
    isbn10 = isbn10,
    isbn13 = isbn13,
    pageCount = pageCount,
    languages = languageList,
    subjects = subjectList,
    workId = workId,
    editionId = editionId,
    source = BookSource.fromName(source),
)
