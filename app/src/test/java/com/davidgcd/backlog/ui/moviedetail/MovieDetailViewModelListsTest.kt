package com.davidgcd.backlog.ui.moviedetail

import com.davidgcd.backlog.data.local.MovieDao
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.repository.FakeHttp
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.data.tmdb.TmdbClient
import com.davidgcd.backlog.data.tmdb.TmdbSession
import com.davidgcd.backlog.data.tmdb.TmdbSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelListsTest {
    private class FakeDao : MovieDao {
        val rows = MutableStateFlow<List<MovieEntity>>(emptyList())
        override fun observeAll(): Flow<List<MovieEntity>> = rows
        override suspend fun allMovies() = rows.value
        override suspend fun findById(titleKey: String) = rows.value.firstOrNull { it.titleKey == titleKey }
        override fun observeById(titleKey: String) = rows.map { list -> list.firstOrNull { it.titleKey == titleKey } }
        override suspend fun upsert(movie: MovieEntity) { rows.value = rows.value.filterNot { it.titleKey == movie.titleKey } + movie }
        override suspend fun update(movie: MovieEntity) = upsert(movie)
        override suspend fun setRank(titleKey: String, rank: Int?) = Unit
        override suspend fun delete(movie: MovieEntity) { rows.value = rows.value.filterNot { it.titleKey == movie.titleKey } }
    }

    private class Sessions(private val signedIn: Boolean) : TmdbSessionStore {
        override suspend fun get() = if (signedIn) TmdbSession("s", 42, "lea") else null
        override suspend fun save(session: TmdbSession) = Unit
        override suspend fun clear() = Unit
    }

    private val dispatcher = StandardTestDispatcher()

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    /** Lists A (8001, already holds the film) and B (8002, empty); the watchlist is empty. [failWrites] makes TMDB refuse writes. */
    private fun viewModel(signedIn: Boolean = true, failWrites: Boolean = false): Pair<MovieDetailViewModel, FakeHttp> {
        val http = FakeHttp { url ->
            when {
                "/genre/" in url -> """{"genres":[]}"""
                "/account/42/lists" in url -> """{"page":1,"total_pages":1,"results":[{"id":8001,"name":"A","item_count":1},{"id":8002,"name":"B","item_count":0}]}"""
                "/account_states" in url -> """{"watchlist":false}"""
                "/list/8001/item_status" in url -> """{"item_present":true}"""
                "/list/8002/item_status" in url -> """{"item_present":false}"""
                failWrites && ("/add_item" in url || "/remove_item" in url || "/watchlist" in url) -> 500
                else -> "{}"
            }
        }
        val repository = MovieRepository(FakeDao(), TmdbClient(http.client, "key", Sessions(signedIn)))
        return MovieDetailViewModel("movie:603", repository) to http
    }

    /** Drains the test scheduler until [done] holds; the fake network answers on real IO threads, so this polls briefly. */
    private fun TestScope.settle(done: () -> Boolean) {
        repeat(1000) {
            advanceUntilIdle()
            if (done()) return
            Thread.sleep(5)
        }
        error("did not settle")
    }

    private fun MovieDetailViewModel.isLoaded() = tmdbLists.value is TmdbListsUi.Loaded
    private fun MovieDetailViewModel.idle() = (tmdbLists.value as? TmdbListsUi.Loaded)?.busy?.isEmpty() == true

    private fun MovieDetailViewModel.loaded() = tmdbLists.value as TmdbListsUi.Loaded

    @Test
    fun `lists load with the ticks of the title`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        settle { vm.isLoaded() }
        val state = vm.loaded().state
        assertFalse(state.watchlist)
        assertEquals(listOf("A" to true, "B" to false), state.lists.map { it.list.name to it.contains })
    }

    @Test
    fun `the card is hidden without a linked account`() = runTest(dispatcher) {
        val (vm, http) = viewModel(signedIn = false)
        settle { vm.tmdbLists.value == TmdbListsUi.Hidden }
        assertEquals(TmdbListsUi.Hidden, vm.tmdbLists.value)
        assertTrue(http.requests.none { "/account/" in it })
    }

    @Test
    fun `ticking a list writes it and keeps the tick`() = runTest(dispatcher) {
        val (vm, http) = viewModel()
        settle { vm.isLoaded() }
        var errors = 0
        vm.toggleTmdbList(8002) { errors++ }
        assertTrue("optimistic", vm.loaded().state.lists[1].contains)
        assertTrue(TmdbListsUi.listRow(8002) in vm.loaded().busy)
        settle { vm.idle() }
        assertTrue(vm.loaded().state.lists[1].contains)
        assertTrue(vm.loaded().busy.isEmpty())
        assertEquals(0, errors)
        assertTrue(http.requests.any { "/list/8002/add_item" in it })
    }

    @Test
    fun `unticking removes the title from the list`() = runTest(dispatcher) {
        val (vm, http) = viewModel()
        settle { vm.isLoaded() }
        vm.toggleTmdbList(8001) {}
        settle { vm.idle() }
        assertFalse(vm.loaded().state.lists[0].contains)
        assertTrue(http.requests.any { "/list/8001/remove_item" in it })
    }

    @Test
    fun `the watchlist is toggled like a list`() = runTest(dispatcher) {
        val (vm, http) = viewModel()
        settle { vm.isLoaded() }
        vm.toggleTmdbWatchlist {}
        settle { vm.idle() }
        assertTrue(vm.loaded().state.watchlist)
        assertTrue(http.requests.any { "/account/42/watchlist" in it })
    }

    @Test
    fun `a refused write puts the tick back and reports the error`() = runTest(dispatcher) {
        val (vm, _) = viewModel(failWrites = true)
        settle { vm.isLoaded() }
        var errors = 0
        vm.toggleTmdbList(8002) { errors++ }
        vm.toggleTmdbWatchlist { errors++ }
        settle { vm.idle() }
        assertFalse(vm.loaded().state.lists[1].contains)
        assertFalse(vm.loaded().state.watchlist)
        assertTrue(vm.loaded().busy.isEmpty())
        assertEquals(2, errors)
    }

    @Test
    fun `a second tap while the first is being written is ignored`() = runTest(dispatcher) {
        val (vm, http) = viewModel()
        settle { vm.isLoaded() }
        vm.toggleTmdbList(8002) {}
        vm.toggleTmdbList(8002) {}
        settle { vm.idle() }
        assertEquals(1, http.requests.count { "/list/8002/add_item" in it })
        assertTrue(vm.loaded().state.lists[1].contains)
    }
}
