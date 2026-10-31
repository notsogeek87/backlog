package com.davidgcd.backlog.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgdbTokenProviderTest {
    /** Hands out tok1, tok2… and records the `stale` fingerprint of each call. */
    private class FakeApi(private val expiresIn: Long = 3600) : IgdbTokenApi {
        val staleSeen = mutableListOf<String?>()
        override suspend fun token(stale: String?): IgdbTokenResponse {
            staleSeen += stale
            return IgdbTokenResponse(client_id = "cid", access_token = "tok${staleSeen.size}", expires_in = expiresIn)
        }
    }

    @Test
    fun `credentials come from the server and are cached until close to expiry`() = runTest {
        var now = 0L
        val api = FakeApi(expiresIn = 3600)
        val provider = IgdbTokenProvider(api) { now }
        assertEquals(IgdbCredentials("cid", "tok1"), provider.credentials())
        now = 3_000_000 // 50 min: still fresh
        assertEquals("tok1", provider.credentials().token)
        now = 3_550_000 // inside the 1-min refresh margin
        assertEquals("tok2", provider.credentials().token)
        assertEquals(listOf<String?>(null, null), api.staleSeen)
    }

    @Test
    fun `an invalidated token is reported to the server on the next fetch`() = runTest {
        val api = FakeApi()
        val provider = IgdbTokenProvider(api) { 0L }
        provider.credentials()
        provider.invalidate("tok1")
        assertEquals("tok2", provider.credentials().token)
        assertEquals(IgdbTokenProvider.fingerprint("tok1"), api.staleSeen[1])
        provider.credentials()
        assertEquals("the report is sent once", 2, api.staleSeen.size)
    }

    @Test
    fun `invalidating an older token does not drop the current one`() = runTest {
        val api = FakeApi()
        val provider = IgdbTokenProvider(api) { 0L }
        provider.credentials()
        provider.invalidate("not-the-current-one")
        assertEquals("tok1", provider.credentials().token)
        assertEquals(1, api.staleSeen.size)
        assertNull(api.staleSeen[0])
    }

    @Test
    fun `the fingerprint matches the server's (first 16 hex chars of sha256)`() {
        // sha256("abc") = ba7816bf8f01cfea414140de5dae2223…
        assertEquals("ba7816bf8f01cfea", IgdbTokenProvider.fingerprint("abc"))
    }
}
