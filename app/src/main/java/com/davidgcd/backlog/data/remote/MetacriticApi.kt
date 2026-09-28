package com.davidgcd.backlog.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Placeholder contract for a RapidAPI Metacritic provider — there is no
 * single standard one, so this shape (a "search by title" endpoint
 * returning a list of {title, score}) is a template: adjust the endpoint
 * path, query param name and [MetacriticSearchResult] fields to whatever
 * provider Secrets.METACRITIC_RAPIDAPI_HOST/KEY point at. See
 * MetacriticService's doc comment and config/Secrets.kt.example.
 */
interface MetacriticApi {
    @GET("search")
    suspend fun search(@Query("title") title: String): List<MetacriticSearchResult>
}

@JsonClass(generateAdapter = true)
data class MetacriticSearchResult(
    val title: String,
    @Json(name = "metascore") val metascore: Int?,
    @Json(name = "url") val url: String? = null,
)
