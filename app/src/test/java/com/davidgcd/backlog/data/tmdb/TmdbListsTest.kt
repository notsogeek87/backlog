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
