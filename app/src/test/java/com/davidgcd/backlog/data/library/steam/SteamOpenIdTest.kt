package com.davidgcd.backlog.data.library.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamOpenIdTest {
    private val steamId = "76561198012345678"
    private val claimed = "https://steamcommunity.com/openid/id/$steamId"

    private fun assertion(vararg overrides: Pair<String, String>) = mapOf(
        "openid.ns" to "http://specs.openid.net/auth/2.0",
        "openid.mode" to "id_res",
        "openid.return_to" to SteamOpenId.RETURN_URL,
        "openid.claimed_id" to claimed,
        "openid.identity" to claimed,
        "openid.sig" to "abc",
    ) + overrides

    @Test fun `login url targets steamcommunity with our return address`() {
        val url = SteamOpenId.loginUrl()
        assertTrue(url.startsWith("https://steamcommunity.com/openid/login?"))
        assertTrue(url.contains("openid.mode=checkid_setup"))
        assertTrue(url.contains("openid.return_to=https%3A%2F%2Fbacklog.invalid%2Fauth%2Fsteam%2Fcallback"))
    }

    @Test fun `callback urls are recognised and their openid parameters parsed`() {
        val url = SteamOpenId.RETURN_URL + "?openid.mode=id_res&openid.claimed_id=" +
            java.net.URLEncoder.encode(claimed, "UTF-8") + "&other=1"
        assertTrue(SteamOpenId.isCallback(url))
        assertFalse(SteamOpenId.isCallback("https://steamcommunity.com/openid/login"))
        val params = SteamOpenId.parseCallback(url)
        assertEquals(claimed, params["openid.claimed_id"])
        assertNull("non-openid params are dropped", params["other"])
    }

    @Test fun `a well-formed assertion yields the SteamID64`() = assertEquals(steamId, SteamOpenId.claimedSteamId(assertion()))

    @Test fun `assertions for another return address are rejected`() =
        assertNull(SteamOpenId.claimedSteamId(assertion("openid.return_to" to "https://evil.example/cb")))

    @Test fun `claimed ids not on steamcommunity are rejected`() {
        val fake = "https://evil.example/openid/id/$steamId"
        assertNull(SteamOpenId.claimedSteamId(assertion("openid.claimed_id" to fake, "openid.identity" to fake)))
    }

    @Test fun `identity and claimed id must agree`() =
        assertNull(SteamOpenId.claimedSteamId(assertion("openid.identity" to "https://steamcommunity.com/openid/id/76561198000000001")))

    @Test fun `a cancelled sign-in is detected and is not an assertion`() {
        val cancelled = mapOf("openid.mode" to "cancel")
        assertTrue(SteamOpenId.isCancelled(cancelled))
        assertNull(SteamOpenId.claimedSteamId(cancelled))
    }

    @Test fun `verification echoes the assertion with check_authentication`() {
        val params = SteamOpenId.verificationParams(assertion())
        assertEquals("check_authentication", params["openid.mode"])
        assertEquals("abc", params["openid.sig"])
    }

    @Test fun `only is_valid true is accepted`() {
        assertTrue(SteamOpenId.isValidResponse("ns:http://specs.openid.net/auth/2.0\nis_valid:true\n"))
        assertFalse(SteamOpenId.isValidResponse("ns:http://specs.openid.net/auth/2.0\nis_valid:false\n"))
        assertFalse(SteamOpenId.isValidResponse(""))
    }
}
