package com.davidgcd.backlog.util

import android.util.Log

/**
 * The one logging entry point — mirrors the iOS app's AppLogger wrapper
 * (os.Logger there). Never call android.util.Log directly elsewhere.
 */
object AppLogger {
    val network = Category("Backlog/Network")

    class Category(private val tag: String) {
        fun warn(message: String) {
            Log.w(tag, message)
            DebugLog.log("W $message")
        }

        fun error(message: String, throwable: Throwable? = null) {
            Log.e(tag, message, throwable)
            DebugLog.log("E $message" + (throwable?.let { " (${it::class.simpleName}: ${it.message})" } ?: ""))
        }
    }
}
