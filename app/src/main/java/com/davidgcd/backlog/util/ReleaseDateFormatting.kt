package com.davidgcd.backlog.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * IGDB's first_release_date is a Unix timestamp (seconds, UTC). This is the
 * single place that turns it into a display string — mirrors the iOS app's
 * ReleaseDateFormatter rule of never re-implementing this conversion at a
 * call site.
 */
object ReleaseDateFormatting {
    fun format(epochSeconds: Long?, locale: Locale = Locale.getDefault()): String? {
        if (epochSeconds == null) return null
        val date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate()
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }

    /** True when the UTC calendar day for [epochSeconds] is today's UTC calendar day. */
    fun isToday(epochSeconds: Long?, now: Instant = Instant.now()): Boolean {
        if (epochSeconds == null) return false
        val releaseDay = Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate()
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        return releaseDay == today
    }
}
