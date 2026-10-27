package com.davidgcd.backlog.util

/** Formats a duration in seconds the way the game detail shows it: « 45 min », « 12 h », « 12 h 30 ». */
object PlayTimeFormatting {
    fun format(seconds: Long): String {
        val minutes = Math.round(seconds / 60.0)
        if (minutes < 60) return "$minutes min"
        // Under 10 h the half-hour matters; above, whole hours are readable and honest enough.
        if (minutes < 600) {
            val h = minutes / 60
            val m = (minutes % 60).let { (Math.round(it / 30.0) * 30).toInt() }
            return when (m) {
                0 -> "$h h"
                60 -> "${h + 1} h"
                else -> "$h h $m"
            }
        }
        return "${Math.round(minutes / 60.0)} h"
    }
}
