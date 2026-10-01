package com.davidgcd.backlog.data.csv

import android.content.Context
import android.net.Uri
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.data.repository.BookRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/** [duplicates] = rows whose book (or edition) was already in the list; [skipped] = rows that were not a book. */
data class BookImportResult(val added: Int, val duplicates: Int, val skipped: Int)

/** Reads and writes the books CSV (see [BookCsv]); everything is local — an import never calls a catalogue. */
class BookCsvService(
    private val context: Context,
    private val repository: BookRepository,
) {
    fun export(uri: Uri, books: List<BookEntity>) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            OutputStreamWriter(output).use { writer -> BookCsv.write(books).forEach(writer::appendLine) }
        }
    }

    suspend fun import(uri: Uri): BookImportResult {
        val lines = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).readLines()
        } ?: return BookImportResult(0, 0, 0)
        val rows = BookCsv.parse(lines)
        val dataRows = lines.drop(1).count { it.isNotBlank() }
        var added = 0
        var duplicates = 0
        rows.forEach { entity ->
            when (repository.addSaved(entity)) {
                is BookAddResult.Added -> added++
                is BookAddResult.Duplicate -> duplicates++
            }
        }
        return BookImportResult(added, duplicates, skipped = (dataRows - rows.size).coerceAtLeast(0))
    }
}
