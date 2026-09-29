package com.davidgcd.backlog.data.library.steam

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Steam's official sign-in: OpenID 2.0 against steamcommunity.com. The user types their credentials
 * on Steam's own page only; Backlog receives nothing but a signed claim "this is SteamID64 N", which
 * is then verified with Steam itself (`check_authentication`) before being trusted.
 */
object SteamOpenId {
    /**
     * Never actually loaded: the login WebView intercepts the redirect to this address. `.invalid` is
     * reserved (RFC 2606), so no real site can ever receive a Steam assertion.
     */
    const val RETURN_URL = "https://backlog.invalid/auth/steam/callback"
    private const val REALM = "https://backlog.invalid/"
    private const val OPENID_NS = "http://specs.openid.net/auth/2.0"
    private const val IDENTIFIER_SELECT = "http://specs.openid.net/auth/2.0/identifier_select"
    private val claimedIdRegex = Regex("""^https?://steamcommunity\.com/openid/id/(\d{17})$""")

    fun loginUrl(): String = "https://steamcommunity.com/openid/login".toHttpUrlOrNull()!!.newBuilder()
        .addQueryParameter("openid.ns", OPENID_NS)
        .addQueryParameter("openid.mode", "checkid_setup")
        .addQueryParameter("openid.return_to", RETURN_URL)
        .addQueryParameter("openid.realm", REALM)
        .addQueryParameter("openid.identity", IDENTIFIER_SELECT)
        .addQueryParameter("openid.claimed_id", IDENTIFIER_SELECT)
        .build()
        .toString()

    fun isCallback(url: String): Boolean = url.startsWith(RETURN_URL)

    /** All `openid.*` query parameters of the callback URL (empty if it isn't one). */
    fun parseCallback(url: String): Map<String, String> {
        val parsed = url.toHttpUrlOrNull() ?: return emptyMap()
        return parsed.queryParameterNames
            .filter { it.startsWith("openid.") }
            .mapNotNull { name -> parsed.queryParameter(name)?.let { name to it } }
            .toMap()
    }

    fun isCancelled(params: Map<String, String>): Boolean = params["openid.mode"] == "cancel"

    /** The SteamID64 the assertion claims, or null if the assertion is malformed or not for this app. Not yet verified with Steam. */
    fun claimedSteamId(params: Map<String, String>): String? {
        if (params["openid.mode"] != "id_res") return null
        if (params["openid.ns"] != OPENID_NS) return null
        if (params["openid.return_to"] != RETURN_URL) return null
        val claimed = params["openid.claimed_id"] ?: return null
        if (params["openid.identity"] != claimed) return null
        return claimedIdRegex.matchEntire(claimed)?.groupValues?.get(1)
    }

    /** The exact assertion echoed back to Steam, with the mode switched to `check_authentication`. */
    fun verificationParams(params: Map<String, String>): Map<String, String> =
        params + ("openid.mode" to "check_authentication")

    fun isValidResponse(body: String): Boolean = body.lineSequence().any { it.trim() == "is_valid:true" }
}
