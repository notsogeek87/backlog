package com.davidgcd.backlog.notifications

/**
 * How many days before a release the reminder fires. Persisted by `name`
 * (a stable enum name, never a translated label) — the iOS app's rule for
 * anything stored: SortOption, ReleaseStage, etc.
 */
enum class ReleaseReminderSchedule(val leadDays: Int) {
    RELEASE_DAY(0),
    DAY_BEFORE(1),
    WEEK_BEFORE(7),
    ;

    companion object {
        val DEFAULT = RELEASE_DAY

        fun fromStoredName(name: String?): ReleaseReminderSchedule =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
