package com.davidgcd.backlog.data.csv

/** Minimal RFC 4180-ish CSV read/write — no external dependency for a handful of columns. */
object CsvFormat {
    fun writeRow(fields: List<String>): String =
        fields.joinToString(",") { field ->
            if (field.contains(',') || field.contains('"') || field.contains('\n')) {
                "\"" + field.replace("\"", "\"\"") + "\""
            } else {
                field
            }
        }

    fun parseRow(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
