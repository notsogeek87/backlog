package com.davidgcd.backlog.data.csv

import android.content.Context
import android.net.Uri
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.util.AppLogger
import com.davidgcd.backlog.util.TitleSimilarity
import java.io.BufferedReader
import java.io.InputStreamReader

/** [cancelled] = the user stopped the import midway; the counts cover the rows processed until then. */
data class CsvImportResult(
    val added: Int,
    val skipped: Int,
    val failedNames: List<String>,
    val cancelled: Boolean = false,
)

/** [done] rows processed out of [total] data rows. */
data class CsvImportProgress(val done: Int, val total: Int)

/**
 * Rows with an `igdbId` resolve directly against IGDB; a name-only row (the
 * historical CSVColumn.acceptedHeaders case on iOS) goes through a search
 * ranked by [TitleSimilarity], same disambiguation the iOS import uses for
 * name-only rows. A row that can't be resolved is skipped, never aborts the
 * whole import.
 */
class CsvImportService(
    private val context: Context,
    private val repository: BacklogRepository,
) {
    suspend fun import(uri: Uri, onProgress: (CsvImportProgress) -> Unit = {}): CsvImportResult {
        val lines = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).readLines()
        } ?: return CsvImportResult(added = 0, skipped = 0, failedNames = emptyList())

        if (lines.isEmpty()) return CsvImportResult(added = 0, skipped = 0, failedNames = emptyList())

        val header = CsvFormat.parseRow(lines.first()).map { it.trim() }
        val nameIndex = header.indexOf(CsvColumn.NAME.header)
        val idIndex = header.indexOf(CsvColumn.IGDB_ID.header)
        val archivedIndex = header.indexOf(CsvColumn.ARCHIVED.header)
        val statusIndex = header.indexOf(CsvColumn.STATUS.header)

        var added = 0
        var skipped = 0
        val failedNames = mutableListOf<String>()

        val rows = lines.drop(1).filter { it.isNotBlank() }
        var cancelled = false
        onProgress(CsvImportProgress(done = 0, total = rows.size))

        try {
            rows.forEachIndexed { index, line ->
                val fields = CsvFormat.parseRow(line)
                val name = nameIndex.takeIf { it >= 0 }?.let { fields.getOrNull(it) }?.trim().orEmpty()
                val igdbId = idIndex.takeIf { it >= 0 }?.let { fields.getOrNull(it) }?.trim()?.toLongOrNull()
                val archived = archivedIndex.takeIf { it >= 0 }
                    ?.let { fields.getOrNull(it) }?.trim()?.toBooleanStrictOrNull() ?: false
                val status = statusIndex.takeIf { it >= 0 }
                    ?.let { fields.getOrNull(it) }?.let(GameStatus::fromName) ?: GameStatus.BACKLOG

                try {
                    val game = when {
                        igdbId != null -> repository.fetchRemoteGame(igdbId)
                        name.isNotBlank() -> resolveByName(name)
                        else -> null
                    }

                    if (game == null) {
                        skipped++
                        if (name.isNotBlank()) failedNames.add(name)
                        return@forEachIndexed
                    }

                    repository.addToBacklog(game)
                    if (archived) {
                        repository.findEntity(game.id)?.let { repository.setArchived(it, archived = true) }
                    }
                    if (status != GameStatus.BACKLOG) {
                        repository.findEntity(game.id)?.let { repository.setStatus(it, status) }
                    }
                    added++
                } catch (t: kotlinx.coroutines.CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    AppLogger.network.warn("CSV import row failed for \"$name\": ${t.message}")
                    skipped++
                    if (name.isNotBlank()) failedNames.add(name)
                } finally {
                    onProgress(CsvImportProgress(done = index + 1, total = rows.size))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Games already added stay; report them instead of dropping the whole result.
            cancelled = true
        }

        return CsvImportResult(added, skipped, failedNames, cancelled)
    }

    private suspend fun resolveByName(name: String) =
        repository.searchGames(name)
            .maxByOrNull { TitleSimilarity.similarity(it.name, name) }
            ?.takeIf { TitleSimilarity.matches(it.name, name) }
}
