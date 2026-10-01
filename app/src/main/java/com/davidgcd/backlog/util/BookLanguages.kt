package com.davidgcd.backlog.util

import java.util.Locale

/** Open Library gives ISO 639-2 codes (`fre`, `eng`), Google Books ISO 639-1 (`fr`): both read as a French name. */
object BookLanguages {
    // ISO 639-2 (B and T spellings) → 639-1, for the languages books are written in: Java only
    // knows the two-letter codes by name.
    private val twoLetter = mapOf(
        "fre" to "fr", "fra" to "fr", "eng" to "en", "spa" to "es", "ger" to "de", "deu" to "de", "ita" to "it",
        "por" to "pt", "dut" to "nl", "nld" to "nl", "lat" to "la", "jpn" to "ja", "rus" to "ru", "chi" to "zh",
        "zho" to "zh", "ara" to "ar", "pol" to "pl", "swe" to "sv", "dan" to "da", "nor" to "no", "fin" to "fi",
        "cat" to "ca", "gre" to "el", "ell" to "el", "tur" to "tr", "heb" to "he", "hun" to "hu", "cze" to "cs",
        "ces" to "cs", "kor" to "ko", "hin" to "hi", "ukr" to "uk", "rum" to "ro", "ron" to "ro", "bul" to "bg",
        "glg" to "gl", "baq" to "eu", "eus" to "eu", "per" to "fa", "fas" to "fa", "ice" to "is", "isl" to "is",
    )

    fun label(code: String): String {
        val raw = code.trim().lowercase()
        if (raw.isEmpty()) return ""
        val tag = twoLetter[raw] ?: raw
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.FRENCH)
        // An unknown code comes back as itself: show it upper-cased rather than as a fake word.
        return if (name.isBlank() || name.equals(tag, ignoreCase = true)) raw.uppercase()
        else name.replaceFirstChar { it.uppercase() }
    }

    fun labels(codes: List<String>): List<String> = codes.map(::label).filter { it.isNotEmpty() }.distinct()

    /** French in any of the spellings the two catalogues use. */
    fun isFrench(code: String): Boolean = code.trim().lowercase() in setOf("fr", "fre", "fra")
}
