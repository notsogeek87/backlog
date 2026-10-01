package com.davidgcd.backlog.model

/**
 * Where the reader is at with a book. Persisted by [name] (see BookEntity.status), never by its
 * French label. "Favori" is not a status: it is an independent flag on the book.
 */
enum class ReadStatus {
    TO_READ,
    READING,
    READ,
    ABANDONED,
    ;

    companion object {
        fun fromName(name: String?): ReadStatus {
            val key = name?.trim()
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: TO_READ
        }
    }
}
