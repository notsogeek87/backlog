package com.davidgcd.backlog.data.csv

/**
 * The CSV header row, both ways. Machine-readable only (English headers,
 * `yyyy-MM-dd` dates) — mirrors the iOS app's CSVColumn: never a localized
 * header or a device-locale date, or the file stops reading back on another
 * device/app.
 */
enum class CsvColumn(val header: String) {
    NAME("name"),
    IGDB_ID("igdbId"),
    RELEASE_DATE("releaseDate"),
    GENRES("genres"),
    PLATFORMS("platforms"),
    ARCHIVED("archived"),
    STEAM_APP_ID("steamAppId"),
    STATUS("status"),
    ;

    companion object {
        val EXPORT_ORDER = listOf(NAME, IGDB_ID, RELEASE_DATE, GENRES, PLATFORMS, ARCHIVED, STEAM_APP_ID, STATUS)
    }
}
