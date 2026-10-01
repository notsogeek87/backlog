package com.davidgcd.backlog.data.csv

import android.content.Context
import android.net.Uri
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import java.io.OutputStreamWriter

class CsvExportService(private val context: Context) {

    /** One file for the whole library: a `type` column (GAME / BOOK) tells the rows apart, see [BookCsv]. */
    fun export(uri: Uri, games: List<GameEntity>, books: List<BookEntity> = emptyList()) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            OutputStreamWriter(output).use { writer ->
                writer.appendLine(CsvFormat.writeRow(BookCsv.HEADER))
                games.forEach { game -> writer.appendLine(CsvFormat.writeRow(gameRow(game))) }
                books.forEach { book -> writer.appendLine(CsvFormat.writeRow(BookCsv.row(book))) }
            }
        }
    }

    /** The games' columns exactly as before, behind the `type` column and followed by the books' (empty) ones. */
    private fun gameRow(game: GameEntity): List<String> =
        listOf(BookCsv.GAME_TYPE) + row(game) + BookCsv.EXTRA_COLUMNS.map { "" }

    private fun row(game: GameEntity): List<String> = CsvColumn.EXPORT_ORDER.map { column ->
        when (column) {
            CsvColumn.NAME -> game.name
            CsvColumn.IGDB_ID -> game.igdbId.toString()
            // A machine format, never a localized date — game.firstReleaseDate is already UTC epoch seconds.
            CsvColumn.RELEASE_DATE -> game.firstReleaseDate?.let {
                java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            } ?: ""
            CsvColumn.GENRES -> GameJsonCache.genreNames(game).joinToString(";")
            CsvColumn.PLATFORMS -> GameJsonCache.platformNames(game).joinToString(";")
            CsvColumn.ARCHIVED -> game.isArchived.toString()
            CsvColumn.STEAM_APP_ID -> game.steamAppId?.toString() ?: ""
            CsvColumn.STATUS -> game.status
            CsvColumn.RANK -> game.userRank?.toString() ?: ""
        }
    }
}
