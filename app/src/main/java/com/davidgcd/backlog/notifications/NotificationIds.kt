package com.davidgcd.backlog.notifications

/** Mirrors the iOS app's NotificationIdentifier factory: never hand-build an id at a call site. */
object NotificationIds {
    const val RELEASE_CHANNEL_ID = "release_reminders"

    /** Stable per-game notification id so a re-post updates rather than duplicates. */
    fun releaseNotificationId(igdbId: Long): Int = ("release_$igdbId").hashCode()
}
