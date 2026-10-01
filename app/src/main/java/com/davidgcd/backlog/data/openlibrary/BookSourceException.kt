package com.davidgcd.backlog.data.openlibrary

enum class BookSourceError {
    /** The catalogue doesn't know that book. */
    NOT_FOUND,
    /** The catalogue didn't answer (offline, timeout, 5xx), or answered something unexpected. */
    UNAVAILABLE,
}

/** Never carries text for the user — the UI maps the failure to a localized message. */
class BookSourceException(val error: BookSourceError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.name, cause) {
    /** An I/O problem (offline, timeout) as opposed to an odd answer: the UI words them differently. */
    val isNetwork: Boolean get() = cause is java.io.IOException
}
