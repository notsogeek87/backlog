package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.util.TitleSimilarity
import java.text.Normalizer

/**
 * Prudent title matching between a store's name and a backlog / IGDB name. Never merges games on a
 * near-miss silently: only [Confidence.EXACT] is applied automatically, [Confidence.LIKELY] is
 * proposed to the user.
 */
object GameMatcher {
    enum class Confidence { EXACT, LIKELY, NONE }

    private const val LIKELY_THRESHOLD = 0.85
    private val subtitleSeparators = listOf(":", " - ", " – ", " — ")
    private val combiningMarks = Regex("\\p{M}+")
    private val digits = Regex("\\d+")

    fun classify(a: String, b: String): Confidence {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isEmpty() || right.isEmpty()) return Confidence.NONE
        if (left == right) return Confidence.EXACT
        // "Fallout 3" vs "Fallout 4" is a 1-character edit but a different game.
        if (digits.findAll(left).map { it.value }.toList() != digits.findAll(right).map { it.value }.toList()) {
            return Confidence.NONE
        }
        // "The Witcher 3: Wild Hunt" ⊃ "The Witcher 3" — but "DOOM Eternal" ⊅ "DOOM" (no subtitle separator).
        val (short, long) = if (a.length <= b.length) a to b else b to a
        val head = subtitleHead(long)
        if (head != null && normalize(head) == normalize(short)) return Confidence.LIKELY
        return if (TitleSimilarity.similarity(left, right) >= LIKELY_THRESHOLD) Confidence.LIKELY else Confidence.NONE
    }

    /** Best non-NONE candidate: an EXACT one wins, otherwise the most similar LIKELY one. */
    fun <T> best(name: String, candidates: List<T>, nameOf: (T) -> String): Pair<T, Confidence>? {
        var bestPair: Pair<T, Confidence>? = null
        var bestScore = -1.0
        for (candidate in candidates) {
            val confidence = classify(name, nameOf(candidate))
            if (confidence == Confidence.NONE) continue
            val score = if (confidence == Confidence.EXACT) 2.0 else TitleSimilarity.similarity(name, nameOf(candidate))
            if (score > bestScore) {
                bestScore = score
                bestPair = candidate to confidence
            }
        }
        return bestPair
    }

    fun normalize(title: String): String {
        val folded = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD).replace(combiningMarks, "")
        return folded
            .replace("&", " and ")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .let { tokens -> if (tokens.size > 1 && tokens.first() == "the") tokens.drop(1) else tokens }
            .joinToString(" ")
    }

    private fun subtitleHead(title: String): String? =
        subtitleSeparators.map { title.indexOf(it) }.filter { it > 0 }.minOrNull()?.let { title.substring(0, it) }
}
