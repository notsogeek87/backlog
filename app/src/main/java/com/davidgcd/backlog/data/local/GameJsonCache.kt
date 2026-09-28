package com.davidgcd.backlog.data.local

import com.davidgcd.backlog.model.Genre
import com.davidgcd.backlog.model.Platform
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Decodes the JSON strings cached on [GameEntity] (genres, platforms) back
 * into the collections Room can't store natively — the read side of the
 * "JSON caching" pattern the iOS app uses on its own GameEntity. A private
 * Moshi instance is enough here: this is the only place that reads these
 * two fields back, so there is no need to route it through the app's
 * networking Moshi instance.
 */
object GameJsonCache {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val genresListType = Types.newParameterizedType(List::class.java, Genre::class.java)
    private val platformsListType = Types.newParameterizedType(List::class.java, Platform::class.java)

    fun genreNames(entity: GameEntity): List<String> =
        entity.genresJson?.let { json ->
            moshi.adapter<List<Genre>>(genresListType).fromJson(json)
        }?.map { it.name } ?: emptyList()

    fun platformNames(entity: GameEntity): List<String> =
        entity.platformsJson?.let { json ->
            moshi.adapter<List<Platform>>(platformsListType).fromJson(json)
        }?.map { it.name } ?: emptyList()
}
