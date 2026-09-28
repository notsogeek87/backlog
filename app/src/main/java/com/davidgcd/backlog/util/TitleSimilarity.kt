package com.davidgcd.backlog.util

/**
 * Normalized Levenshtein similarity (0..1) used to match a search result's
 * title against the game being looked up — mirrors the iOS app's
 * TitleSimilarity, the single matcher shared by every "disambiguate a
 * third-party search result" case (there: Metacritic, HowLongToBeat).
 */
object TitleSimilarity {
    const val MATCH_THRESHOLD = 0.8

    fun similarity(a: String, b: String): Double {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isEmpty() && right.isEmpty()) return 1.0
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) return 1.0
        return 1.0 - levenshtein(left, right).toDouble() / maxLength
    }

    fun matches(a: String, b: String, threshold: Double = MATCH_THRESHOLD): Boolean =
        similarity(a, b) >= threshold

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }
}
