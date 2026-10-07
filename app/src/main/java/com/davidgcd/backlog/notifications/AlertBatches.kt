package com.davidgcd.backlog.notifications

/** Le lot du jour : une longue liste est parcourue par tranches, la tranche changeant chaque jour, pour couvrir tout le monde en quelques jours. */
object AlertBatches {
    private const val DAY_MILLIS = 86_400_000L

    fun <T> today(items: List<T>, batchSize: Int, nowMillis: Long = System.currentTimeMillis()): List<T> =
        slice(items, batchSize, nowMillis / DAY_MILLIS)

    fun <T> slice(items: List<T>, batchSize: Int, dayIndex: Long): List<T> {
        if (items.size <= batchSize) return items
        val pages = (items.size + batchSize - 1) / batchSize
        val page = (dayIndex % pages).toInt()
        return items.drop(page * batchSize).take(batchSize)
    }
}
