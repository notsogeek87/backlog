package com.davidgcd.backlog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchStatus

/**
 * A film or series of the watch list — the persisted row, the movie-side counterpart of [GameEntity].
 * Keyed by the IMDb id (`tt…`), which is text, hence a separate table rather than more `games` rows.
 */
@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val imdbId: String,
    val title: String,
    /** [TitleKind] name. */
    val kind: String = TitleKind.MOVIE.name,
    val year: Int? = null,
    /** UTC epoch seconds, null when IMDb only knows the year. */
    val releaseDate: Long? = null,
    val posterUrl: String? = null,
    /** Genre names joined with ";" (see [genreList]). */
    val genres: String? = null,
    val plot: String? = null,
    /** IMDb user rating, 0–10. */
    val imdbRating: Double? = null,
    val runtimeMinutes: Int? = null,
    val directors: String? = null,
    val isArchived: Boolean = false,
    /** [WatchStatus] name. */
    val status: String = WatchStatus.TO_WATCH.name,
    val addedAt: Long = System.currentTimeMillis(),
    /** Personal ranking, 1 = most loved. Null = not ranked yet. */
    val userRank: Int? = null,
    /** The user's own rating out of 10 (what they gave on IMDb, or set here). */
    val userRating: Int? = null,
)

val MovieEntity.watchStatus: WatchStatus get() = WatchStatus.fromName(status)
val MovieEntity.titleKind: TitleKind get() = TitleKind.fromName(kind)
val MovieEntity.genreList: List<String> get() = genres?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
