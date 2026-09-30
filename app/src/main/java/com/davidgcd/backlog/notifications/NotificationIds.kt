package com.davidgcd.backlog.notifications

/** Mirrors the iOS app's NotificationIdentifier factory: never hand-build an id at a call site. */
object NotificationIds {
    const val RELEASE_CHANNEL_ID = "release_reminders"
    const val DRIFT_CHANNEL_ID = "drift_alerts"

    /** Intent extra carrying the igdbId of the game a notification tap should open. */
    const val EXTRA_GAME_ID = "com.davidgcd.backlog.extra.GAME_ID"

    /** Stable per-game notification id so a re-post updates rather than duplicates. */
    fun releaseNotificationId(igdbId: Long): Int = ("release_$igdbId").hashCode()

    fun dateChangeNotificationId(igdbId: Long): Int = ("date_change_$igdbId").hashCode()

    fun newPlatformNotificationId(igdbId: Long): Int = ("new_platform_$igdbId").hashCode()
}
