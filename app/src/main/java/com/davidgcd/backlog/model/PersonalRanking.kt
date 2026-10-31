package com.davidgcd.backlog.model

/**
 * The rules every personal ranking shares (games [Ranking], films & séries [MovieRanking], books [BookTop]):
 * first = most loved; ranked items first by rank, then the not-yet-ranked ones in the order they were added,
 * so a title added later simply joins the end. Written once here, tested once ([PersonalRankingTest]).
 */
object PersonalRanking {
    fun <T> order(items: List<T>, rank: (T) -> Int?, addedAt: (T) -> Long): List<T> {
        val ranked = items.filter { rank(it) != null }.sortedWith(compareBy<T>({ rank(it) }, { addedAt(it) }))
        val unranked = items.filter { rank(it) == null }.sortedBy(addedAt)
        return ranked + unranked
    }

    /** Moves the item at [index] by [delta] places (-1 = up), or returns the list unchanged at either end. */
    fun <T> move(ordered: List<T>, index: Int, delta: Int): List<T> = moveTo(ordered, index, index + delta)

    /** Puts the item at [index] at position [target] (0 = first), or returns the list unchanged if nothing moves. */
    fun <T> moveTo(ordered: List<T>, index: Int, target: Int): List<T> {
        if (index !in ordered.indices || target !in ordered.indices || index == target) return ordered
        return ordered.toMutableList().also { list -> list.add(target, list.removeAt(index)) }
    }

    /** Ranks 1..n for the new order, only for the items whose rank actually changes, keyed by [key]. */
    fun <T, K> changes(newOrder: List<T>, rank: (T) -> Int?, key: (T) -> K): Map<K, Int> =
        newOrder.mapIndexedNotNull { i, item -> (i + 1).takeIf { it != rank(item) }?.let { key(item) to it } }.toMap()
}
