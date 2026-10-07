package com.davidgcd.backlog.model

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.titleKind
import com.davidgcd.backlog.data.local.genreList
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.local.subjectList
import com.davidgcd.backlog.data.local.watchStatus
import java.time.Instant
import java.time.ZoneId

/** Un coup de cœur de l'année, tous médias confondus. */
data class RecapPick(val medium: Medium, val title: String, /** Note ramenée à 0..1. */ val quality: Double)

/**
 * « Mon année » : ce qu'on a terminé, ajouté et aimé sur une année civile. Seules des données réelles y entrent :
 * la date d'un élément terminé est celle où il l'a été (`completedAt`), ou à défaut son ajout (éléments terminés
 * avant que l'app ne retienne cette date).
 */
data class YearRecap(
    val year: Int,
    val gamesFinished: Int,
    val moviesWatched: Int,
    val seriesWatched: Int,
    val booksRead: Int,
    val added: Int,
    val pagesRead: Int,
    val minutesWatched: Int,
    val topGenres: List<Pair<String, Int>>,
    val picks: List<RecapPick>,
) {
    val finished: Int get() = gamesFinished + moviesWatched + seriesWatched + booksRead
    val isEmpty: Boolean get() = finished == 0 && added == 0
}

object YearRecaps {
    private fun yearOf(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).year

    /** Les années pour lesquelles il y a quelque chose à raconter, de la plus récente à la plus ancienne. */
    fun years(games: List<GameEntity>, movies: List<MovieEntity>, books: List<BookEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
        (games.map { yearOf(it.addedAt, zone) } + movies.map { yearOf(it.addedAt, zone) } + books.map { yearOf(it.addedAt, zone) } +
            games.mapNotNull { it.completedAt }.map { yearOf(it, zone) } +
            movies.mapNotNull { it.completedAt }.map { yearOf(it, zone) } +
            books.mapNotNull { it.completedAt }.map { yearOf(it, zone) })
            .distinct().sortedDescending()

    fun compute(
        year: Int,
        games: List<GameEntity>,
        movies: List<MovieEntity>,
        books: List<BookEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): YearRecap {
        val doneGames = games.filter { it.gameStatus == GameStatus.COMPLETED && yearOf(it.completedAt ?: it.addedAt, zone) == year }
        val doneMovies = movies.filter { it.watchStatus == WatchStatus.WATCHED && yearOf(it.completedAt ?: it.addedAt, zone) == year }
        val doneBooks = books.filter { it.readStatus == ReadStatus.READ && yearOf(it.completedAt ?: it.addedAt, zone) == year }

        val added = games.count { yearOf(it.addedAt, zone) == year } +
            movies.count { yearOf(it.addedAt, zone) == year } +
            books.count { yearOf(it.addedAt, zone) == year }

        val genres = (doneGames.flatMap { GameJsonCache.genreNames(it) } +
            doneMovies.flatMap { it.genreList } +
            doneBooks.flatMap { it.subjectList.take(3) })
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5).map { it.key to it.value }

        val picks = (
            doneGames.mapNotNull { g -> g.userRating?.let { RecapPick(Medium.GAME, g.name, it / 10.0) } } +
                doneMovies.mapNotNull { m -> m.userRating?.let { RecapPick(Medium.MOVIE, m.title, it / 10.0) } } +
                doneBooks.mapNotNull { b -> b.userRating?.let { RecapPick(Medium.BOOK, b.title, it / 5.0) } }
            ).sortedWith(compareByDescending<RecapPick> { it.quality }.thenBy { it.title }).take(3)

        return YearRecap(
            year = year,
            gamesFinished = doneGames.size,
            moviesWatched = doneMovies.count { it.titleKind == TitleKind.MOVIE },
            seriesWatched = doneMovies.count { it.titleKind == TitleKind.SERIES },
            booksRead = doneBooks.size,
            added = added,
            pagesRead = doneBooks.sumOf { it.pageCount ?: 0 },
            minutesWatched = doneMovies.sumOf { it.runtimeMinutes ?: 0 },
            topGenres = genres,
            picks = picks,
        )
    }
}
