package com.davidgcd.backlog.data.csv

/** How often the automatic CSV export runs. [days] is the WorkManager repeat interval. */
enum class AutoExportFrequency(val days: Long) {
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30);

    companion object {
        val DEFAULT = WEEKLY

        fun fromStoredName(name: String?): AutoExportFrequency =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
