package com.davidgcd.backlog.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer of recent network/debug events, viewable and copyable from within the
 * app (Settings screen) — lets a non-technical tester grab diagnostics without adb/logcat.
 */
object DebugLog {
    private const val MAX_ENTRIES = 200
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    @Synchronized
    fun log(message: String) {
        val line = "${timeFormat.format(Date())}  $message"
        _entries.value = (_entries.value + line).takeLast(MAX_ENTRIES)
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }

    fun asText(): String = _entries.value.joinToString("\n")
}
