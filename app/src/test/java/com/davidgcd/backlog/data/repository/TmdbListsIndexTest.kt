package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.local.MovieDao
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.tmdb.FileTmdbListsIndexStore
import com.davidgcd.backlog.data.tmdb.TmdbClient
import com.davidgcd.backlog.data.tmdb.TmdbListContent
import com.davidgcd.backlog.data.tmdb.TmdbListsIndexStore
import com.davidgcd.backlog.data.tmdb.TmdbSession
import com.davidgcd.backlog.data.tmdb.TmdbSessionStore
import com.davidgcd.backlog.model.WatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TmdbListsIndexTest {
    private class FakeDao(movies: List<MovieEntity> = emptyList()) : MovieDao {
        val rows = MutableStateFlow(movies)
        override fun observeAll(): Flow<List<MovieEntity>> = rows
        override suspend fun allMovies() = rows.value
        override suspend fun findById(titleKey: String) = rows.value.firstOrNull { it.titleKey == titleKey }
        override fun observeById(titleKey: String) = rows.map { list -> list.firstOrNull { it.titleKey == titleKey } }
        override suspend fun upsert(movie: MovieEntity) { rows.value = rows.value.filterNot { it.titleKey == movie.titleKey } + movie }
        override suspend fun update(movie: MovieEntity) = upsert(movie)
        override suspend fun setRank(titleKey: String, rank: Int?) = Unit
        override suspend fun delete(movie: MovieEntity) { rows.value = rows.value.filterNot { it.titleKey == movie.titleKey } }
    }

    private class Sessions(var signedIn: Boolean = true) : TmdbSessionStore {
        override suspend fun get() = if (signedIn) TmdbSession("s", 42, "lea") else null
        override suspend fun save(session: TmdbSession) = Unit
        override suspend fun clear() = Unit
    }

    private class MemoryStore(var saved: List<TmdbListContent> = emptyList()) : TmdbListsIndexStore {
        override suspend fun load() = saved
        override suspend fun save(lists: List<TmdbListContent>) { saved = lists }
    }

    /** Watchlist: Matrix. « Charlie »: Pokémon (a series, no media_type) and Matrix. */
    private fun http(offline: () -> Boolean = { false }) = FakeHttp { url ->
        when {
            offline() -> IOException("offline")
            "/genre/" in url -> """{"genres":[]}"""
            "/account/42/watchlist/movies" in url -> """{"page":1,"total_pages":1,"results":[{"id":603,"title":"Matrix","release_date":"1999-03-30"}]}"""
            "/account/42/watchlist/tv" in url -> """{"page":1,"total_pages":1,"results":[]}"""
            "/account/42/lists" in url -> """{"page":1,"total_pages":1,"results":[{"id":8001,"name":"Charlie","item_count":2}]}"""
            "/list/8001?" in url -> """{"page":1,"total_pages":1,"items":[
                {"id":116135,"name":"Pokémon : Les horizons","first_air_date":"2023-04-14"},
                {"id":603,"media_type":"movie","title":"Matrix","release_date":"1999-03-30"}]}"""
            else -> "{}"
        }
    }

    @Test
    fun `the lists are indexed and their titles land in the library as to watch`() = runTest {
        val dao = FakeDao(listOf(MovieEntity(titleKey = "movie:603", title = "Matrix", status = WatchStatus.WATCHED.name, userRating = 9)))
        val store = MemoryStore()
        val repo = MovieRepository(dao, TmdbClient(http().client, "key", Sessions()), store)
        repo.refreshTmdbListsIndex()

        val index = repo.tmdbListsIndex.value
        assertEquals(listOf(TmdbListContent.WATCHLIST_ID, 8001L), index.map { it.id })
        assertEquals(setOf("movie:603"), index[0].keys)
        assertEquals("Charlie", index[1].name)
        assertEquals(setOf("tv:116135", "movie:603"), index[1].keys)
        assertEquals(index, store.saved)

        val pokemon = dao.findById("tv:116135")
        assertNotNull(pokemon)
        assertEquals(WatchStatus.TO_WATCH.name, pokemon!!.status)
        // A title already in the library keeps what the user set.
        assertEquals(WatchStatus.WATCHED.name, dao.findById("movie:603")!!.status)
        assertEquals(9, dao.findById("movie:603")!!.userRating)
    }

    @Test
    fun `refreshes are spaced out unless forced`() = runTest {
        var now = 1_000_000L
        val fake = http()
        val repo = MovieRepository(FakeDao(), TmdbClient(fake.client, "key", Sessions()), MemoryStore()) { now }
        repo.refreshTmdbListsIndex()
        val calls = fake.requests.size
        now += 30_000
        repo.refreshTmdbListsIndex()
        assertEquals(calls, fake.requests.size)
        repo.refreshTmdbListsIndex(force = true)
        assertTrue(fake.requests.size > calls)
    }

    @Test
    fun `offline, the last known index from the device stays`() = runTest {
        val known = listOf(TmdbListContent(8001, "Charlie", setOf("tv:116135")))
        val repo = MovieRepository(FakeDao(), TmdbClient(http(offline = { true }).client, "key", Sessions()), MemoryStore(known))
        repo.refreshTmdbListsIndex()
        assertEquals(known, repo.tmdbListsIndex.value)
    }

    @Test
    fun `signed out, the index is emptied on screen and on the device`() = runTest {
        val store = MemoryStore(listOf(TmdbListContent(8001, "Charlie", setOf("tv:116135"))))
        val repo = MovieRepository(FakeDao(), TmdbClient(http().client, "key", Sessions(signedIn = false)), store)
        repo.refreshTmdbListsIndex()
        assertTrue(repo.tmdbListsIndex.value.isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `a tick written from the app updates the index at once`() = runTest {
        val repo = MovieRepository(FakeDao(), TmdbClient(http().client, "key", Sessions()), MemoryStore())
        repo.refreshTmdbListsIndex()
        repo.setInTmdbList(8001, "movie:27205", on = true)
        assertTrue("movie:27205" in repo.tmdbListsIndex.value.first { it.id == 8001L }.keys)
        repo.setInTmdbWatchlist("movie:603", on = false)
        assertTrue(repo.tmdbListsIndex.value.first { it.isWatchlist }.keys.isEmpty())
    }

    @Test
    fun `the device copy round-trips`() {
        val lists = listOf(TmdbListContent(0, "", setOf("movie:603")), TmdbListContent(8001, "Charlie", setOf("tv:116135", "movie:603")))
        assertEquals(lists, FileTmdbListsIndexStore.decode(FileTmdbListsIndexStore.encode(lists)))
    }
}
