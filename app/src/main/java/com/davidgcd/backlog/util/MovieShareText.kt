package com.davidgcd.backlog.util

import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.local.watchStatus
import com.davidgcd.backlog.model.ImdbTitle
import com.davidgcd.backlog.model.MovieRanking
import com.davidgcd.backlog.model.WatchStatus

/**
 * Plain-text version of the films & séries list for the share sheet — the movie twin of
 * [BacklogShareText]: ranked titles first as a numbered "Mon classement", the rest grouped by status,
 * each with its imdb.com link.
 */
object MovieShareText {
    class Labels(
        val header: () -> String,
        val status: (WatchStatus) -> String,
        val ranking: String,
    )

    fun build(movies: List<MovieEntity>, labels: Labels): String {
        val active = movies.filter { !it.isArchived }
        val ranked = MovieRanking.order(active).filter { it.userRank != null }
        val rankedIds = ranked.mapTo(HashSet()) { it.imdbId }
        val lines = mutableListOf(labels.header())
        if (ranked.isNotEmpty()) {
            lines += ""
            lines += labels.ranking
            ranked.forEachIndexed { i, m -> lines += "${i + 1}. ${line(m)}" }
        }
        WatchStatus.entries.forEach { status ->
            val group = active.filter { it.watchStatus == status && it.imdbId !in rankedIds }.sortedBy { it.title.lowercase() }
            if (group.isNotEmpty()) {
                lines += ""
                lines += labels.status(status)
                group.forEach { lines += "- ${line(it)}" }
            }
        }
        return lines.joinToString("\n")
    }

    private fun line(m: MovieEntity): String {
        val year = m.year?.let { " ($it)" }.orEmpty()
        return "${m.title}$year — ${ImdbTitle.imdbTitleUrl(m.imdbId)}"
    }
}
