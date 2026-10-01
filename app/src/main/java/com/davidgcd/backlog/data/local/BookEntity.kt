package com.davidgcd.backlog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.model.ReadStatus

/**
 * A book of the reading list — the persisted row, the book-side counterpart of [GameEntity] / [MovieEntity].
 * Everything the screens show is stored here (description and cover URL included), so a saved book is
 * displayed without any network call. Keyed by the app-wide book key (see model/BookKey): one row per
 * *edition* the user added, with the Open Library work id kept to relate editions of the same work.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val bookKey: String,
    val title: String,
    val subtitle: String? = null,
    /** Author names joined with ";" (see [authorList]). */
    val authors: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val pageCount: Int? = null,
    /** Language codes joined with ";" (see [languageList]). */
    val languages: String? = null,
    /** Subjects / genres joined with ";" (see [subjectList]). */
    val subjects: String? = null,
    val workId: String? = null,
    val editionId: String? = null,
    /** [BookSource] name. */
    val source: String = BookSource.OPEN_LIBRARY.name,
    /** [ReadStatus] name. */
    val status: String = ReadStatus.TO_READ.name,
    val isFavorite: Boolean = false,
    /** The reader's own rating, 1–5 stars. */
    val userRating: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

val BookEntity.readStatus: ReadStatus get() = ReadStatus.fromName(status)
val BookEntity.authorList: List<String> get() = authors.splitList()
val BookEntity.languageList: List<String> get() = languages.splitList()
val BookEntity.subjectList: List<String> get() = subjects.splitList()

private fun String?.splitList(): List<String> = this?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

/** Lists are stored as one ";"-joined column; a ";" inside a name would split it, so it becomes ",". */
fun List<String>.joinForStorage(): String? =
    map { it.replace(';', ',').trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.joinToString(";")
