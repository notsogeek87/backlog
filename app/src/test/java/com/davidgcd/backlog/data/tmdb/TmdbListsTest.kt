package com.davidgcd.backlog.data.tmdb

import com.davidgcd.backlog.data.repository.FakeHttp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TmdbListsTest {
    private class FakeSessions(private val session: TmdbSession? = TmdbSession("s3ss10n", accountId = 42, username = "lea")) : TmdbSessionStore {
        override suspend fun get() = session
        override suspend fun save(session: TmdbSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun client(http: FakeHttp, sessions: TmdbSessionStore = FakeSessions()) = TmdbClient(http.client, "key", sessions)

    @Test
    fun `the user's lists are parsed, nameless ones dropped`() {
        val json = """{"page":1,"total_pages":1,"results":[
          {"id":8001,"name":"À voir en famille","item_count":12},
          {"id":8002,"name":"  ","item_count":3},
          {"name":"sans id"},
          {"id":8003,"name":"Classiques"}]}"""
        assertEquals(
            listOf(TmdbUserList(8001, "À voir en famille", 12), TmdbUserList(8003, "Classiques", 0)),
            TmdbParsers.parseUserLists(json),
        )
        assertTrue(TmdbParsers.parseUserLists("nope").isEmpty())
    }

    @Test
    fun `watchlist flag`() {
        assertTrue(TmdbParsers.parseInWatchlist("""{"id":603,"watchlist":true,"rated":false}"""))
        assertFalse(TmdbParsers.parseInWatchlist("""{"id":603}"""))
        assertFalse(TmdbParsers.parseInWatchlist("garbage"))
    }

    @Test
    fun `a list's content is read as film and series keys, people skipped`() {
        val json = """{"id":8001,"page":1,"total_pages":1,"items":[
          {"id":603,"media_type":"movie"},{"id":1396,"media_type":"tv"},
          {"id":6193,"media_type":"person"},{"id":0,"media_type":"movie"},{"media_type":"tv"}]}"""
        assertEquals(setOf("movie:603", "tv:1396"), TmdbParsers.parseListItemKeys(json))
        assertTrue(TmdbParsers.parseListItemKeys("nope").isEmpty())
    }

    @Test
    fun `a list answer without media_type still tells films from series`() {
        val json = """{"items":[
          {"id":603,"title":"Matrix","release_date":"1999-03-30"},
          {"id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20"},
          {"id":6193,"name":"Leonardo DiCaprio","known_for_department":"Acting"}]}"""
        assertEquals(setOf("movie:603", "tv:1396"), TmdbParsers.parseListItemKeys(json))
    }

    @Test
    fun `lists are read page by page for the signed-in account`() = runTest {
        val http = FakeHttp { url ->
            when {
                "page=2" in url -> """{"page":2,"total_pages":2,"results":[{"id":8002,"name":"B","item_count":1}]}"""
                else -> """{"page":1,"total_pages":2,"results":[{"id":8001,"name":"A","item_count":2}]}"""
            }
        }
        val lists = client(http).userLists()
        assertEquals(listOf("A", "B"), lists.map { it.name })
        assertTrue(http.requests.all { "/account/42/lists" in it && "session_id=s3ss10n" in it })
    }

    @Test
    fun `a series in a list is found, which item_status could never say`() = runTest {
        val http = FakeHttp { url ->
            when {
                "/account_states" in url -> """{"watchlist":true}"""
                "page=2" in url -> """{"page":2,"total_pages":2,"items":[{"id":1396,"media_type":"tv"}]}"""
                else -> """{"page":1,"total_pages":2,"items":[{"id":603,"media_type":"movie"}]}"""
            }
        }
        val c = client(http)
        assertTrue(c.isInWatchlist("tv:1396"))
        assertTrue(http.requests[0].contains("/tv/1396/account_states"))
        assertEquals(setOf("movie:603", "tv:1396"), c.listItemKeys(8001))
        assertTrue(http.requests.drop(1).all { "/list/8001?" in it && "session_id=s3ss10n" in it })
    }

    @Test
    fun `adding and removing hit add_item and remove_item`() = runTest {
        val http = FakeHttp { "{}" }
        val c = client(http)
        c.setInList(8001, "movie:603", true)
        c.setInList(8001, "movie:603", false)
        assertTrue(http.requests[0].contains("/list/8001/add_item"))
        assertTrue(http.requests[1].contains("/list/8001/remove_item"))
    }

    @Test
    fun `adding something already in the list counts as done`() = runTest {
        val http = FakeHttp { url -> if ("/add_item" in url) 403 else """{"page":1,"total_pages":1,"items":[{"id":603,"media_type":"movie"}]}""" }
        client(http).setInList(8001, "movie:603", true)
        assertTrue(http.requests.any { "/list/8001?" in it })
    }

    @Test
    fun `a refusal on a list the title is not in is still an error`() = runTest {
        val http = FakeHttp { url -> if ("/add_item" in url) 403 else """{"page":1,"total_pages":1,"items":[]}""" }
        try {
            client(http).setInList(8001, "movie:603", true)
            fail("expected a TmdbException")
        } catch (e: TmdbException) {
            assertEquals(TmdbError.UNAVAILABLE, e.error)
        }
    }

    private val v4Session = TmdbSession("s3ss10n", accountId = 42, username = "lea", accessToken = "us3r-v4")

    @Test
    fun `with a v4 token a series goes to the list as a series, through v4`() = runTest {
        val http = FakeHttp { """{"success":true,"results":[{"media_type":"tv","media_id":74415,"success":true}]}""" }
        val c = client(http, FakeSessions(v4Session))
        c.setInList(8001, "tv:74415", true)
        c.setInList(8001, "tv:74415", false)
        assertEquals(2, http.requests.size)
        assertTrue(http.requests.all { it.startsWith("https://api.themoviedb.org/4/list/8001/items") })
        assertTrue(http.requests.none { "add_item" in it || "remove_item" in it || "api_key=" in it })
    }

    @Test
    fun `without a v4 token a series is refused before anything is written`() = runTest {
        val http = FakeHttp { "{}" }
        try {
            client(http).setInList(8001, "tv:74415", true)
            fail("expected a TmdbException")
        } catch (e: TmdbException) {
            assertEquals(TmdbError.NOT_SIGNED_IN, e.error)
        }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `a v4 item already in the list counts as done`() = runTest {
        val http = FakeHttp { url ->
            if ("/4/list/" in url) """{"success":true,"results":[{"media_type":"tv","media_id":1396,"success":false}]}"""
            else """{"page":1,"total_pages":1,"items":[{"id":1396,"media_type":"tv"}]}"""
        }
        client(http, FakeSessions(v4Session)).setInList(8001, "tv:1396", true)
        assertTrue(http.requests.any { "/3/list/8001?" in it })
    }

    @Test
    fun `v4 items answer`() {
        assertTrue(TmdbParsers.parseV4ItemsSuccess("""{"success":true,"results":[{"success":true}]}"""))
        assertTrue(TmdbParsers.parseV4ItemsSuccess("""{"success":true}"""))
        assertFalse(TmdbParsers.parseV4ItemsSuccess("""{"success":true,"results":[{"success":false}]}"""))
        assertFalse(TmdbParsers.parseV4ItemsSuccess("""{"success":false}"""))
        assertFalse(TmdbParsers.parseV4ItemsSuccess("nope"))
    }

    @Test
    fun `the v4 sign-in turns the access token into the v3 session and keeps both`() = runTest {
        var saved: TmdbSession? = null
        val store = object : TmdbSessionStore {
            override suspend fun get() = saved
            override suspend fun save(session: TmdbSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        val http = FakeHttp { url ->
            when {
                "/4/auth/request_token" in url -> """{"success":true,"request_token":"rq"}"""
                "/4/auth/access_token" in url -> """{"success":true,"access_token":"us3r-v4","account_id":"abc"}"""
                "/session/convert/4" in url -> """{"success":true,"session_id":"s3ss10n"}"""
                "/3/account?" in url -> """{"id":42,"username":"lea"}"""
                else -> 500
            }
        }
        val c = TmdbClient(http.client, "key", store, readAccessToken = "r3ad")
        assertTrue(c.usesV4)
        assertEquals("rq", c.newRequestToken("https://backlog.invalid/tmdb-approved"))
        assertEquals("https://www.themoviedb.org/auth/access?request_token=rq", c.approvalUrl("rq", "https://backlog.invalid/tmdb-approved"))
        val session = c.createSession("rq")
        assertEquals(TmdbSession("s3ss10n", 42, "lea", "us3r-v4"), session)
        assertEquals(session, saved)
        assertTrue(http.requests.none { "/authentication/session/new" in it })
    }

    @Test
    fun `without the read token the sign-in stays v3`() {
        assertFalse(TmdbClient(FakeHttp { "{}" }.client, "key", FakeSessions(), readAccessToken = "YOUR_TMDB_READ_ACCESS_TOKEN").usesV4)
        assertFalse(client(FakeHttp { "{}" }).usesV4)
    }

    @Test
    fun `without a session every list call says to sign in`() = runTest {
        val c = client(FakeHttp { "{}" }, FakeSessions(session = null))
        try {
            c.userLists()
            fail("expected a TmdbException")
        } catch (e: TmdbException) {
            assertEquals(TmdbError.NOT_SIGNED_IN, e.error)
        }
    }
}
