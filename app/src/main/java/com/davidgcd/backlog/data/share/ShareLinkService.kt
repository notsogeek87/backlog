package com.davidgcd.backlog.data.share

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.shareLinkDataStore by preferencesDataStore(name = "share_link")

/** One game as the share page needs it — the same fields the server validates. */
data class ShareItem(
    val name: String,
    val status: String,
    val coverImageId: String?,
    val url: String?,
    /** 1 = most loved; null = not ranked. Position among the ranked games, never the stored rank. */
    val rank: Int? = null,
)

/**
 * Publishes a snapshot of the backlog to the share server and returns the public link.
 * The first publish creates the link and keeps its secret token on the device; later ones update
 * the same link, so what was already sent to friends stays valid. If the server no longer knows the
 * link (deleted) a new one is created.
 */
class ShareLinkService(
    private val context: Context,
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val idKey = stringPreferencesKey("share_id")
    private val tokenKey = stringPreferencesKey("share_token")
    private val urlsKey = stringPreferencesKey("igdb_urls")
    private val ownerKey = stringPreferencesKey("owner_name")

    /** Name shown on the public page ("par David", "Le top de David"); empty = anonymous. */
    val ownerName: Flow<String> = context.shareLinkDataStore.data.map { it[ownerKey].orEmpty() }

    suspend fun setOwnerName(name: String) {
        context.shareLinkDataStore.edit { it[ownerKey] = name.take(MAX_OWNER_LENGTH) }
    }

    /** igdb.com pages already looked up, so a share only asks IGDB about games it hasn't seen. */
    suspend fun cachedUrls(): Map<Long, String> {
        val raw = context.shareLinkDataStore.data.first()[urlsKey] ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().mapNotNull { key -> key.toLongOrNull()?.let { it to json.getString(key) } }.toMap()
        } catch (e: org.json.JSONException) {
            emptyMap()
        }
    }

    suspend fun rememberUrls(urls: Map<Long, String>) {
        if (urls.isEmpty()) return
        val merged = cachedUrls() + urls
        context.shareLinkDataStore.edit { prefs ->
            prefs[urlsKey] = JSONObject().apply { merged.forEach { (id, url) -> put(id.toString(), url) } }.toString()
        }
    }

    /** Throws [IOException] on any network / server failure; the caller falls back to a plain-text share. */
    suspend fun publish(title: String, items: List<ShareItem>): String = withContext(Dispatchers.IO) {
        val prefs = context.shareLinkDataStore.data.first()
        val body = payload(title, items, prefs[ownerKey].orEmpty().trim()).toRequestBody(JSON)
        val id = prefs[idKey]
        val token = prefs[tokenKey]
        if (id != null && token != null) {
            val update = Request.Builder().url("$baseUrl/api/share?id=$id").put(body).header("x-share-token", token).build()
            client.newCall(update).execute().use { response ->
                if (response.isSuccessful) return@withContext JSONObject(response.body!!.string()).getString("url")
                if (response.code != 404) throw IOException("HTTP ${response.code}")
            }
        }
        val create = Request.Builder().url("$baseUrl/api/share").post(body).build()
        client.newCall(create).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body!!.string())
            context.shareLinkDataStore.edit {
                it[idKey] = json.getString("id")
                it[tokenKey] = json.getString("token")
            }
            json.getString("url")
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://library.lielu.eu"
        const val MAX_OWNER_LENGTH = 40
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun payload(title: String, items: List<ShareItem>, owner: String = ""): String = JSONObject()
            .put("title", title)
            .put("owner", owner)
            .put(
                "items",
                JSONArray(
                    items.map {
                        JSONObject()
                            .put("name", it.name)
                            .put("status", it.status)
                            .put("coverImageId", it.coverImageId ?: JSONObject.NULL)
                            .put("url", it.url ?: JSONObject.NULL)
                            .put("rank", it.rank ?: JSONObject.NULL)
                    },
                ),
            )
            .toString()
    }
}
