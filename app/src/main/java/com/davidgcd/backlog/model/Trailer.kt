package com.davidgcd.backlog.model

/** A YouTube trailer of a title; [isFrench] is false when only a version in another language exists. */
data class Trailer(
    val youtubeKey: String,
    val isFrench: Boolean,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$youtubeKey"
}
