package com.davidgcd.backlog.model

import com.davidgcd.backlog.util.Isbn
import java.security.MessageDigest
import java.text.Normalizer

/** Where a [Book]'s data came from; Open Library is the main source, Google Books only the fallback. */
enum class BookSource {
    OPEN_LIBRARY,
    GOOGLE_BOOKS,
    ;

    companion object {
        fun fromName(name: String?): BookSource =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: OPEN_LIBRARY
    }
}

/**
 * A book as a catalogue describes it — the book-side counterpart of [Game] / [MediaTitle]: a transient
 * shape, never persisted as is (see data/local/BookEntity).
 *
 * A search hit stands for a *work* (Open Library's "work" = the text, whatever its editions); the
 * edition data (ISBN, publisher, pages, language) is filled in when it is known. [workId] and
 * [editionId] are kept apart so that two editions of one work stay two different entries.
 */
data class Book(
    /** App-wide id, assigned once when the book is first seen (see [BookKey]) and never changed after. */
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    /** Full cover URL (see util/BookImage for sizes); null when the catalogue has none. */
    val coverUrl: String? = null,
    /** First publication year of the work (falls back to the edition's). */
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val pageCount: Int? = null,
    /** Language codes as the catalogue gives them (`fre`, `eng`, `fr`…); see util/BookLanguages. */
    val languages: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    /** Open Library work id, `OL45804W`. */
    val workId: String? = null,
    /** Open Library edition id, `OL7353617M`. */
    val editionId: String? = null,
    val source: BookSource = BookSource.OPEN_LIBRARY,
) {
    val hasIsbn: Boolean get() = canonicalIsbn13 != null || canonicalIsbn10 != null

    /** The ISBN-13 of this book, converted from the ISBN-10 when that is all there is. */
    val canonicalIsbn13: String? get() = Isbn.toIsbn13(isbn13) ?: Isbn.toIsbn13(isbn10)

    val canonicalIsbn10: String? get() = Isbn.toIsbn10(isbn10) ?: Isbn.toIsbn10(isbn13)

    val authorLine: String get() = authors.joinToString(", ")

    /** The page of this book on its catalogue, for the "open" button; null when it has no catalogue id. */
    val catalogUrl: String?
        get() = when {
            source == BookSource.GOOGLE_BOOKS && key.startsWith(BookKey.GOOGLE_PREFIX) ->
                "https://books.google.com/books?id=${key.removePrefix(BookKey.GOOGLE_PREFIX)}"
            workId != null -> "https://openlibrary.org/works/$workId"
            editionId != null -> "https://openlibrary.org/books/$editionId"
            else -> null
        }

    /**
     * This edition record seen as one of the editions of [work]: an edition has no author, subjects or
     * (often) description of its own, so it borrows the work's — but keeps its own title (often a
     * translation), cover, publisher, ISBN, pages and language.
     */
    fun asEditionOf(work: Book): Book = copy(
        authors = authors.ifEmpty { work.authors },
        description = description ?: work.description,
        coverUrl = coverUrl ?: work.coverUrl,
        publishedYear = publishedYear ?: work.publishedYear,
        subjects = subjects.ifEmpty { work.subjects },
        workId = work.workId ?: workId,
        source = work.source,
    )

    /**
     * What a fresher read of the same book adds: what the book already shows stays (the user saw it),
     * what is missing is filled, and the edition-specific facts (publisher, pages, language) come from
     * the edition read when it has them, since a search hit only knows the work.
     */
    fun enrichedWith(fresh: Book): Book = copy(
        subtitle = subtitle ?: fresh.subtitle,
        authors = authors.ifEmpty { fresh.authors },
        description = description ?: fresh.description,
        coverUrl = coverUrl ?: fresh.coverUrl,
        publishedYear = publishedYear ?: fresh.publishedYear,
        publisher = fresh.publisher ?: publisher,
        isbn10 = isbn10 ?: fresh.isbn10,
        isbn13 = isbn13 ?: fresh.isbn13,
        pageCount = fresh.pageCount ?: pageCount,
        languages = fresh.languages.ifEmpty { languages },
        subjects = subjects.ifEmpty { fresh.subjects },
        workId = workId ?: fresh.workId,
        editionId = editionId ?: fresh.editionId,
    )
}

/** One page of search results; [hasMore] says whether asking for the next page is worth a call. */
data class BookPage(val books: List<Book>, val hasMore: Boolean)

/**
 * The app-wide id of a book, fixed the first time it is seen: `isbn:<ISBN-13>` when it has an ISBN
 * (an ISBN-10 is converted so both spellings give one key), else `ol:<edition id>`, else
 * `work:<work id>`, else `gb:<Google volume>` or a hash of title + author as a last resort.
 * The key only names the row; whether two books are the same is [BookDuplicates]'s job.
 */
object BookKey {
    const val GOOGLE_PREFIX = "gb:"

    fun of(book: Book): String {
        book.canonicalIsbn13?.let { return "isbn:$it" }
        book.editionId?.let { return "ol:$it" }
        book.workId?.let { return "work:$it" }
        return "ta:" + sha1("${BookText.normalize(book.title)}|${BookText.normalize(book.authors.firstOrNull().orEmpty())}").take(12)
    }

    fun withKey(book: Book): Book = book.copy(key = of(book))

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Accent- and case-insensitive text comparison for titles and authors. */
object BookText {
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(Regex("\\s+"), " ")

    private val FRENCH_WORDS = setOf("le", "la", "les", "des", "un", "une", "et", "est", "dans", "qui", "que", "pour", "du", "au", "aux", "sur", "avec", "il", "elle", "son", "sa", "ses", "ce", "se", "ne", "pas", "par", "plus", "sont", "ont")
    private val ENGLISH_WORDS = setOf("the", "and", "of", "is", "in", "to", "that", "with", "for", "his", "her", "by", "as", "on", "was", "are", "he", "she", "it", "who", "which", "from", "this", "their")

    /** A rough "is this text French?" (more French than English function words) — enough to tell a description to replace. */
    fun looksFrench(text: String): Boolean {
        val words = normalize(text).split(' ')
        val french = words.count { it in FRENCH_WORDS }
        val english = words.count { it in ENGLISH_WORDS }
        return french > english
    }
}
