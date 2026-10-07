package com.davidgcd.backlog.data.remote

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Le prix d'un jeu sur la boutique Steam (en France), avec la remise en cours s'il y en a une. */
data class SteamPrice(
    val discountPercent: Int,
    /** Prix actuel, en centimes. */
    val finalCents: Long,
    val initialCents: Long,
    /** Prix actuel tel que Steam l'écrit (« 14,99€ »), ou null. */
    val finalFormatted: String?,
)

/**
 * Lit la réponse de `store.steampowered.com/api/appdetails?filters=price_overview`. Steam y répond `"data":[]`
 * (un tableau, pas un objet) pour un jeu gratuit ou sans prix : on lit donc le JSON sans schéma figé.
 */
object SteamPriceParser {
    private val moshi = Moshi.Builder().build()

    fun parse(json: String, appId: Long): SteamPrice? {
        val root = runCatching { moshi.adapter(Any::class.java).fromJson(json) }.getOrNull() as? Map<*, *> ?: return null
        val entry = root[appId.toString()] as? Map<*, *> ?: return null
        if (entry["success"] != true) return null
        val overview = (entry["data"] as? Map<*, *>)?.get("price_overview") as? Map<*, *> ?: return null
        val final = (overview["final"] as? Number)?.toLong() ?: return null
        return SteamPrice(
            discountPercent = (overview["discount_percent"] as? Number)?.toInt() ?: 0,
            finalCents = final,
            initialCents = (overview["initial"] as? Number)?.toLong() ?: final,
            finalFormatted = overview["final_formatted"] as? String,
        )
    }
}

/** Quand prévenir d'une promotion : assez forte, et pas déjà annoncée à ce prix. */
object DealRules {
    const val MIN_DISCOUNT_PERCENT = 20

    /** [lastNotifiedFinalCents] : le prix pour lequel on a déjà prévenu (null = jamais). Une promo qui se termine remet la mémoire à zéro. */
    fun shouldNotify(price: SteamPrice, lastNotifiedFinalCents: Long?): Boolean =
        price.discountPercent >= MIN_DISCOUNT_PERCENT && price.finalCents != lastNotifiedFinalCents
}

/** Interroge Steam pour le prix d'un jeu ; aucune clé n'est nécessaire (API publique de la boutique). */
class SteamPriceService(private val http: OkHttpClient) {
    suspend fun price(appId: Long): SteamPrice? = withContext(Dispatchers.IO) {
        val url = "https://store.steampowered.com/api/appdetails".toHttpUrl().newBuilder()
            .addQueryParameter("appids", appId.toString())
            .addQueryParameter("cc", "fr")
            .addQueryParameter("l", "french")
            .addQueryParameter("filters", "price_overview")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            SteamPriceParser.parse(response.body?.string().orEmpty(), appId)
        }
    }
}
