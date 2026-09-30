package com.davidgcd.backlog.model

/** A streaming / rental / purchase platform, as TMDB (via JustWatch) lists it. */
data class WatchProvider(
    val id: Int,
    val name: String,
    /** Full logo URL, or null when TMDB has none. */
    val logoUrl: String?,
)

/**
 * Where a title can be watched in one country. The four lists are what the fiche shows separately:
 * included in a subscription, to rent, to buy, and free (with or without ads).
 */
data class WatchProviders(
    /** TMDB's page for the country (it leads to the offers on JustWatch). */
    val link: String?,
    val subscription: List<WatchProvider>,
    val rent: List<WatchProvider>,
    val buy: List<WatchProvider>,
    val free: List<WatchProvider>,
) {
    val isEmpty: Boolean get() = subscription.isEmpty() && rent.isEmpty() && buy.isEmpty() && free.isEmpty()

    companion object {
        /** The only region the app serves. */
        const val REGION = "FR"
    }
}
