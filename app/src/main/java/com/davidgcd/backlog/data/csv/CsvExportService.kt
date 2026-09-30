package com.davidgcd.backlog.data.csv

import android.content.Context
import android.net.Uri
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import java.io.OutputStreamWriter

class CsvExportService(private val context: Context) {

    fun export(uri: Uri, games: List<GameEntity>) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output).use { writer ->
                writer.appendLine(CsvFormat.writeRow(CsvColumn.EXPORT_ORDER.map { it.header }))
                games.forEach { game -> writer.appendLine(CsvFormat.writeRow(row(game))) }
            }
        }
    }

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
        }
    }
}
