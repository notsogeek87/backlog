package com.davidgcd.backlog.model

/** A YouTube trailer of a title; [isFrench] is false when only a version in another language exists. */
data class Trailer(
    val youtubeKey: String,
    val isFrench: Boolean,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$youtubeKey"
}

/** IGDB `game_videos` row: a YouTube video of [game], titled [name] (often "Trailer", "Launch Trailer"…). */
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class GameVideo(
    @com.squareup.moshi.Json(name = "video_id") val videoId: String = "",
    val name: String? = null,
) {
    companion object {
        private val FRENCH = Regex("""\b(fr|vf|vostfr|fran[cç]ais(e)?|french)\b""", RegexOption.IGNORE_CASE)
        private val TRAILER = Regex("""trailer|bande[- ]annonce""", RegexOption.IGNORE_CASE)

        /**
         * IGDB has no language field: a French video is recognised by its title, then a "trailer" by title,
         * then the first video. Null when the game has none.
         */
        fun pickTrailer(videos: List<GameVideo>): Trailer? {
            val usable = videos.filter { it.videoId.isNotBlank() }
            val french = usable.firstOrNull { it.name != null && FRENCH.containsMatchIn(it.name) && TRAILER.containsMatchIn(it.name) }
                ?: usable.firstOrNull { it.name != null && FRENCH.containsMatchIn(it.name) }
            if (french != null) return Trailer(french.videoId, isFrench = true)
            val other = usable.firstOrNull { it.name != null && TRAILER.containsMatchIn(it.name) } ?: usable.firstOrNull()
            return other?.let { Trailer(it.videoId, isFrench = false) }
        }
    }
}
