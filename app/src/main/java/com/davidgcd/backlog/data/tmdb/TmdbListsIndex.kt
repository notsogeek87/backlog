package com.davidgcd.backlog.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One of the user's TMDB lists and the titles in it (keys `movie:603` / `tv:1396`), for the library's « Liste TMDB »
 * filter. [id] is [WATCHLIST_ID] for the watchlist, the TMDB list id otherwise.
 */
data class TmdbListContent(val id: Long, val name: String, val keys: Set<String>) {
    val isWatchlist: Boolean get() = id == WATCHLIST_ID

    companion object {
        /** TMDB list ids are positive: 0 can only be the watchlist. */
        const val WATCHLIST_ID = 0L
    }
}

/** Keeps the last known lists on the device, so the filter works at launch and offline. */
interface TmdbListsIndexStore {
    suspend fun load(): List<TmdbListContent>
    suspend fun save(lists: List<TmdbListContent>)
}

/** A small JSON file in app storage (not in backups: rebuilt from TMDB on the next opening). */
class FileTmdbListsIndexStore(dir: File) : TmdbListsIndexStore {
    private val file = File(dir, "tmdb_lists_index.json")

    override suspend fun load(): List<TmdbListContent> = withContext(Dispatchers.IO) {
        runCatching { decode(file.readText()) }.getOrDefault(emptyList())
    }

    override suspend fun save(lists: List<TmdbListContent>) = withContext(Dispatchers.IO) {
        if (lists.isEmpty()) file.delete() else file.writeText(encode(lists))
        Unit
    }

    companion object {
        fun encode(lists: List<TmdbListContent>): String = JSONArray(
            lists.map { l -> JSONObject().put("id", l.id).put("name", l.name).put("keys", JSONArray(l.keys.sorted())) },
        ).toString()

        fun decode(json: String): List<TmdbListContent> {
            val array = JSONArray(json)
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val keys = o.optJSONArray("keys") ?: JSONArray()
                TmdbListContent(o.optLong("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null, o.optString("name"), (0 until keys.length()).map { keys.optString(it) }.filter { it.isNotEmpty() }.toSet())
            }
        }
    }
}
