package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.data.local.GameEntity
import retrofit2.HttpException
import java.io.IOException

/**
 * A store the user owns games on (Steam today; Epic, GOG, Xbox… later). Implementations only know how
 * to *list* a library — matching against IGDB, dedup and persistence are shared in [LibrarySyncService].
 */
interface GameLibraryProvider {
    /** Stable key persisted in `game_sources.provider` and the account store. Never rename. */
    val id: String

    /** IGDB `external_game_source` id used to map this store's ids to IGDB games, or null if IGDB has none. */
    val igdbExternalSourceId: Int?

    /** @throws LibraryException with a user-mappable [LibraryError]. */
    suspend fun fetchLibrary(accountId: String): List<LibraryGame>

    /** The external id already stored on a backlog entity before `game_sources` existed (legacy `steamAppId`). */
    fun existingLinkId(game: GameEntity): String? = null
}

object LibraryProviders {
    const val STEAM = "steam"
}

data class LibraryGame(
    val externalId: String,
    val name: String,
    /** Null = the store gave no figure. Zero is a valid "never played", not an error. */
    val playtimeMinutes: Int? = null,
    val recentPlaytimeMinutes: Int? = null,
    val imageUrl: String? = null,
)

enum class LibraryError {
    NOT_CONNECTED,
    CANCELLED,
    PRIVATE_LIBRARY,
    ACCOUNT_NOT_FOUND,
    UNAVAILABLE,
    RATE_LIMITED,
    UNKNOWN,
}

/** Never carries text meant for the user — the UI maps [error] to a localized message. [message] is for logs. */
class LibraryException(
    val error: LibraryError,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: error.name, cause)

/**
 * Classifies any failure of a store / IGDB call. HTTP 401/403 (a bad or revoked API key) is a build
 * problem rather than the user's, so it is reported as "unavailable" and the detail stays in the log.
 */
fun Throwable.toLibraryException(): LibraryException = when {
    this is LibraryException -> this
    this is HttpException && code() == 429 -> LibraryException(LibraryError.RATE_LIMITED, "HTTP 429", this)
    this is HttpException -> LibraryException(LibraryError.UNAVAILABLE, "HTTP ${code()}", this)
    this is IOException -> LibraryException(LibraryError.UNAVAILABLE, "I/O: $message", this) // includes timeouts
    else -> LibraryException(LibraryError.UNKNOWN, message, this) // e.g. unexpected JSON
}
