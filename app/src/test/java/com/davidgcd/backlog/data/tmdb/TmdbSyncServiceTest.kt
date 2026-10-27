package com.davidgcd.backlog.data.tmdb

import com.davidgcd.backlog.data.library.FakeAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.local.MovieDao
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.repository.FakeHttp
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

class TmdbSyncServiceTest {
    private class FakeMovieDao(movies: List<MovieEntity>) : MovieDao {
        val rows = MutableStateFlow(movies)
        override fun observeAll(): Flow<List<MovieEntity>> = rows
        override suspend fun allMovies() = rows.value
        override suspend fun findById(titleKey: String) = rows.value.firstOrNull { it.titleKey == titleKey }
        override fun observeById(titleKey: String) = rows.map { list -> list.firstOrNull { it.titleKey == titleKey } }
        override suspend fun upsert(movie: MovieEntity) {
            rows.value = rows.value.filterNot { it.titleKey == movie.titleKey } + movie
        }
        override suspend fun update(movie: MovieEntity) = upsert(movie)
        override suspend fun setRank(titleKey: String, rank: Int?) = Unit
        override suspend fun delete(movie: MovieEntity) {
            rows.value = rows.value.filterNot { it.titleKey == movie.titleKey }
        }
    }

    private class FakeSessions : TmdbSessionStore {
        override suspend fun get() = TmdbSession("s3ss10n", accountId = 42, username = "lea")
        override suspend fun save(session: TmdbSession) = Unit
        override suspend fun clear() = Unit
    }

    private val empty = """{"page":1,"total_pages":1,"results":[]}"""

    /** TMDB already has Matrix rated 9; everything else in the app is unknown to the account. */
    private fun http(offline: Boolean = false) = FakeHttp { url ->
        when {
            "/genre/" in url -> """{"genres":[]}"""
            "/account/42/rated/movies" in url ->
                """{"page":1,"total_pages":1,"results":[{"id":603,"title":"Matrix","rating":9.0}]}"""
            "/account/42/" in url && "/rated/" in url || "/watchlist/" in url -> empty
            offline -> IOException("offline")
            else -> "{}"
        }
    }

    private val app = listOf(
        MovieEntity(titleKey = "movie:603", title = "Matrix", status = WatchStatus.WATCHED.name, userRating = 9),
        MovieEntity(titleKey = "movie:27205", title = "Inception", status = WatchStatus.WATCHED.name, userRating = 8),
        MovieEntity(titleKey = "tv:1396", title = "Breaking Bad", status = WatchStatus.TO_WATCH.name),
        MovieEntity(titleKey = "tv:66732", title = "Stranger Things", status = WatchStatus.WATCHING.name),
        MovieEntity(titleKey = "movie:155", title = "The Dark Knight", status = WatchStatus.TO_WATCH.name, isArchived = true),
    )

    private suspend fun linkedAccounts() = FakeAccountStore().apply { connect(LibraryProviders.TMDB, "42", "lea") }

    private fun writes(fake: FakeHttp) = fake.requests.filter { "/rating" in it || "/account/42/watchlist?" in it }

    @Test
    fun `first sync sends what the app had before the account was linked`() = runTest {
        val fake = http()
        val accounts = linkedAccounts()
        val service = TmdbSyncService(TmdbClient(fake.client, "key", FakeSessions()), FakeMovieDao(app), accounts)

        val result = service.sync(now = 1_000)

        // Inception's rating and Breaking Bad (to watch); not Matrix (already rated there), not a series
        // being watched (TMDB has no such list), not an archived title.
        assertEquals(2, result.uploaded)
        val sent = writes(fake)
        assertEquals(2, sent.size)
        assertTrue(sent.any { "/movie/27205/rating" in it })
        assertTrue(sent.any { "/account/42/watchlist?" in it })
        assertEquals(1_000L, accounts.get(LibraryProviders.TMDB)?.localUploadedAt)
    }

    @Test
    fun `later syncs never push again`() = runTest {
        val fake = http()
        val service = TmdbSyncService(TmdbClient(fake.client, "key", FakeSessions()), FakeMovieDao(app), linkedAccounts())

        service.sync(now = 1_000)
        fake.requests.clear()
        val again = service.sync(now = 2_000)

        assertEquals(0, again.uploaded)
        assertTrue(writes(fake).isEmpty())
    }

    @Test
    fun `a failed upload is retried on the next sync`() = runTest {
        val accounts = linkedAccounts()
        val dao = FakeMovieDao(app)
        val offline = TmdbSyncService(TmdbClient(http(offline = true).client, "key", FakeSessions()), dao, accounts)

        val failure = runCatching { offline.sync(now = 1_000) }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(null, accounts.get(LibraryProviders.TMDB)?.localUploadedAt)

        val online = TmdbSyncService(TmdbClient(http().client, "key", FakeSessions()), dao, accounts)
        assertEquals(2, online.sync(now = 2_000).uploaded)
    }
}
