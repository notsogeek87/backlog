package com.davidgcd.backlog.ui.backlog

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.IgdbService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BacklogViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg games: GameEntity): BacklogViewModel {
        val dao = FakeGameDao(games.toList())
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val repository = BacklogRepository(dao, IgdbService(FakeIgdbApi()), moshi)
        return BacklogViewModel(repository)
    }

    @Test
    fun `sort by name orders alphabetically`() = runTest {
        val viewModel = viewModel(
            GameEntity(igdbId = 1, name = "Zelda", addedAt = 1),
            GameEntity(igdbId = 2, name = "Apex", addedAt = 2),
        )
        val job = launch { viewModel.visibleBacklog.collect {} }

        viewModel.setSort(BacklogSort.NAME)

        assertEquals(listOf("Apex", "Zelda"), viewModel.visibleBacklog.value.map { it.name })
        job.cancel()
    }

    @Test
    fun `sort by recently added is most recent first`() = runTest {
        val viewModel = viewModel(
            GameEntity(igdbId = 1, name = "Old", addedAt = 1),
            GameEntity(igdbId = 2, name = "New", addedAt = 2),
        )
        val job = launch { viewModel.visibleBacklog.collect {} }

        viewModel.setSort(BacklogSort.RECENTLY_ADDED)

        assertEquals(listOf("New", "Old"), viewModel.visibleBacklog.value.map { it.name })
        job.cancel()
    }

    @Test
    fun `sort by release date puts games with no date last`() = runTest {
        val viewModel = viewModel(
            GameEntity(igdbId = 1, name = "Undated", addedAt = 1, firstReleaseDate = null),
            GameEntity(igdbId = 2, name = "Dated", addedAt = 2, firstReleaseDate = 100L),
        )
        val job = launch { viewModel.visibleBacklog.collect {} }

        viewModel.setSort(BacklogSort.RELEASE_DATE)

        assertEquals(listOf("Dated", "Undated"), viewModel.visibleBacklog.value.map { it.name })
        job.cancel()
    }

    @Test
    fun `archived games are hidden until the filter shows them`() = runTest {
        val viewModel = viewModel(
            GameEntity(igdbId = 1, name = "Active", addedAt = 1, isArchived = false),
            GameEntity(igdbId = 2, name = "Archived", addedAt = 2, isArchived = true),
        )
        val job = launch { viewModel.visibleBacklog.collect {} }

        assertEquals(listOf("Active"), viewModel.visibleBacklog.value.map { it.name })

        viewModel.setFilter(viewModel.filter.value.copy(showArchived = true))

        assertEquals(setOf("Active", "Archived"), viewModel.visibleBacklog.value.map { it.name }.toSet())
        job.cancel()
    }

    @Test
    fun `genre filter only keeps matching games`() = runTest {
        val viewModel = viewModel(
            GameEntity(igdbId = 1, name = "RPG", addedAt = 1, genresJson = """[{"id":1,"name":"RPG"}]"""),
            GameEntity(igdbId = 2, name = "Shooter", addedAt = 2, genresJson = """[{"id":2,"name":"Shooter"}]"""),
        )
        val job = launch { viewModel.visibleBacklog.collect {} }

        viewModel.setFilter(viewModel.filter.value.copy(genre = "RPG"))

        assertEquals(listOf("RPG"), viewModel.visibleBacklog.value.map { it.name })
        job.cancel()
    }

    @Test
    fun `isBacklogEmpty reflects the unfiltered backlog, not the current filter`() = runTest {
        val viewModel = viewModel(
            GameEntity(igdbId = 1, name = "Archived", addedAt = 1, isArchived = true),
        )
        val job = launch { viewModel.isBacklogEmpty.collect {} }

        // The only game is archived and hidden by default, but the backlog itself isn't empty.
        assertEquals(false, viewModel.isBacklogEmpty.value)
        job.cancel()
    }
}
