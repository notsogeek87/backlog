package com.davidgcd.backlog.ui.platforms

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.library.LibraryError

/** The only text a user ever sees for a failure — technical detail stays in the server-side style log. */
@StringRes
fun LibraryError.messageRes(): Int = when (this) {
    LibraryError.NOT_CONNECTED -> R.string.library_error_not_connected
    LibraryError.PRIVATE_LIBRARY -> R.string.library_error_private
    LibraryError.ACCOUNT_NOT_FOUND -> R.string.library_error_account_not_found
    LibraryError.UNAVAILABLE -> R.string.library_error_unavailable
    LibraryError.RATE_LIMITED -> R.string.library_error_rate_limited
    LibraryError.CANCELLED, LibraryError.UNKNOWN -> R.string.library_error_unknown
}

/** "124 h", "35 min", or null when there's no figure / it was never launched. */
fun formatPlaytime(minutes: Int?): String? = when {
    minutes == null || minutes <= 0 -> null
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h"
}

/** Quantity string through plain resources, so it doesn't depend on Compose's plural API stability. */
@Composable
fun pluralText(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(id, count, *args)
