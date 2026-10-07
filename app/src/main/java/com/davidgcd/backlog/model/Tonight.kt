package com.davidgcd.backlog.model

/** Les trois mondes de l'app, côté modèle (l'interface a son propre `MediaType` pour les onglets). */
enum class Medium { GAME, MOVIE, BOOK }

/** Temps dont on dispose ce soir. Les jeux n'ont pas de durée connue : ils passent tous les filtres de temps. */
enum class TimeBudget(val maxMinutes: Int?) {
    /** Une petite heure et demie. */
    SHORT(90),

    /** Une soirée. */
    EVENING(210),

    /** Aucune limite (week-end) : les contenus longs sont même favorisés. */
    LONG(null),
}

/** Un élément de la bibliothèque qu'on pourrait choisir ce soir, quel que soit son média. */
data class TonightCandidate(
    val medium: Medium,
    /** Clé de navigation : id IGDB, titleKey TMDB ou bookKey. */
    val key: String,
    val title: String,
    val coverImageId: String? = null,
    val coverUrl: String? = null,
    val inProgress: Boolean = false,
    val addedAt: Long = 0L,
    /** Qualité 0..1 (note perso si elle existe, sinon note publique) ; null = inconnue. */
    val quality: Double? = null,
    /** Durée estimée (film : durée ; livre : pages ÷ 40 par heure) ; null pour un jeu. */
    val durationMinutes: Int? = null,
    val subtitle: String? = null,
)

/**
 * « Ce soir, je fais quoi ? » : propose quelques éléments de la bibliothèque selon le média voulu et le temps
 * disponible. Ordre : ce qui est commencé d'abord, puis ce qui traîne depuis longtemps, bien noté, et qui tient
 * dans le temps. Un grain ([seed]) change la sélection d'un tirage à l'autre sans jamais choisir au hasard pur.
 */
object Tonight {
    private const val DAY_MILLIS = 86_400_000L
    private const val PAGES_PER_HOUR = 40

    /** Durée de lecture estimée d'un livre de [pages] pages (null si inconnue). */
    fun readingMinutes(pages: Int?): Int? = pages?.takeIf { it > 0 }?.let { it * 60 / PAGES_PER_HOUR }

    fun suggest(
        candidates: List<TonightCandidate>,
        medium: Medium?,
        budget: TimeBudget,
        now: Long,
        seed: Long = 0L,
        count: Int = 3,
    ): List<TonightCandidate> =
        candidates
            .filter { medium == null || it.medium == medium }
            .filter { fits(it, budget) }
            .sortedByDescending { score(it, budget, now, seed) }
            .take(count)

    internal fun fits(candidate: TonightCandidate, budget: TimeBudget): Boolean {
        val max = budget.maxMinutes ?: return true
        val duration = candidate.durationMinutes ?: return true
        return duration <= max
    }

    internal fun score(candidate: TonightCandidate, budget: TimeBudget, now: Long, seed: Long): Double {
        var score = 0.0
        if (candidate.inProgress) score += 3.0
        val ageDays = ((now - candidate.addedAt).coerceAtLeast(0L) / DAY_MILLIS).toDouble()
        score += minOf(ageDays / 30.0, 3.0) * 0.5
        score += (candidate.quality ?: 0.5) * 2.0
        val duration = candidate.durationMinutes
        if (duration != null) {
            val max = budget.maxMinutes
            // Un contenu qui remplit bien le temps disponible vaut mieux qu'un format minuscule ; en « long », on aime le long.
            score += if (max == null) minOf(duration / 240.0, 1.0) else (duration.toDouble() / max).coerceIn(0.0, 1.0)
        }
        return score + jitter(candidate.key, seed) * 0.4
    }

    private fun jitter(key: String, seed: Long): Double = ((key.hashCode().toLong() xor seed * 31L) and 0xFFFFL) / 65_535.0
}
