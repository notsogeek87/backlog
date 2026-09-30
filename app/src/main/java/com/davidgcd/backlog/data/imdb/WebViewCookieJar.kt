package com.davidgcd.backlog.data.imdb

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Lets OkHttp reuse the IMDb session the user opened in the login WebView. Backlog never sees the
 * password: only the resulting session cookies, which stay in the WebView's own store (app-private),
 * are replayed on requests to imdb.com — nothing is copied elsewhere.
 */
class WebViewCookieJar : CookieJar {
    private val manager: CookieManager? get() = runCatching { CookieManager.getInstance() }.getOrNull()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = manager?.getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val m = manager ?: return
        cookies.forEach { m.setCookie(url.toString(), it.toString()) }
        m.flush()
    }

    companion object {
        /** Forgets the IMDb session (sign-out). */
        fun clearSession() {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }

        /** True when the WebView holds an IMDb sign-in cookie — a cheap check, the real one is [ImdbClient.resolveUserId]. */
        fun hasSignInCookie(): Boolean {
            val cookies = runCatching { CookieManager.getInstance().getCookie("https://www.imdb.com") }.getOrNull().orEmpty()
            return cookies.split(';').any { it.trim().startsWith("at-main=") }
        }
    }
}
