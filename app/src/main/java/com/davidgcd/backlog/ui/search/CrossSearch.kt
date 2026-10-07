package com.davidgcd.backlog.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.suggestedFor
import com.davidgcd.backlog.ui.components.AddButton
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookDuplicates
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.ui.books.BookHit
import com.davidgcd.backlog.ui.books.BookHitItem
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.movies.MediaTitleListItem
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.FrenchLabels
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Hits from the categories other than the tab's own; a failing catalogue just leaves its list empty. */
data class CrossSearchResults(
    val games: List<Game> = emptyList(),
    val movies: List<MediaTitle> = emptyList(),
    val books: List<Book> = emptyList(),
) {
    val isEmpty: Boolean get() = games.isEmpty() && movies.isEmpty() && books.isEmpty()
}

/**
 * Searches the categories a tab does *not* own, so a query typed on any tab also finds games, films & séries
 * and books. The tab's own results stay first; these are shown below them, [PER_CATEGORY] at most each.
 */
class CrossSearchViewModel(
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModel() {

    private val _results = MutableStateFlow(CrossSearchResults())
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var job: Job? = null

    private val savedGameIds: StateFlow<Set<Long>> = games.observeBacklog()
        .map { list -> list.map { it.igdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val savedMovieIds: StateFlow<Set<String>> = movies.observeAll()
        .map { list -> list.map { it.titleKey }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val savedBooks: StateFlow<List<Book>> = books.observeAll()
        .map { list -> list.map { it.toBook() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Results plus what's already saved, so each row can show "déjà ajouté" instead of an Add button. */
    val state: StateFlow<CrossSearchState> = combine(
        _results, savedGameIds, savedMovieIds, savedBooks,
    ) { results, gameIds, movieIds, saved ->
        CrossSearchState(
            games = results.games.map { it to (it.id in gameIds) },
            movies = results.movies.map { it to (it.id in movieIds) },
            books = results.books.map { BookHit(it, BookDuplicates.find(it, saved)?.key) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrossSearchState())

    /** [primary] is the tab's own category: it is skipped here because the tab searches it itself. */
    fun search(query: String, primary: MediaType) {
        job?.cancel()
        if (query.isBlank()) {
            _results.value = CrossSearchResults()
            _isSearching.value = false
            return
        }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _isSearching.value = true
            try {
                _results.value = coroutineScope {
                    val g = async { if (primary == MediaType.GAMES) emptyList() else attempt { this@CrossSearchViewModel.games.searchGames(query).take(PER_CATEGORY) } }
                    val m = async { if (primary == MediaType.MOVIES) emptyList() else attempt { this@CrossSearchViewModel.movies.search(query).take(PER_CATEGORY) } }
                    val b = async { if (primary == MediaType.BOOKS) emptyList() else attempt { this@CrossSearchViewModel.books.search(query).books.take(PER_CATEGORY) } }
                    CrossSearchResults(g.await(), m.await(), b.await())
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    private inline fun <T> attempt(block: () -> List<T>): List<T> = try {
        block()
    } catch (t: CancellationException) {
        throw t
    } catch (t: Throwable) {
        emptyList()
    }

    fun addGame(game: Game) {
        viewModelScope.launch { games.addToBacklog(game, com.davidgcd.backlog.model.GameStatus.suggestedFor(game.firstReleaseDate)) }
    }

    fun addMovie(title: MediaTitle) {
        viewModelScope.launch { movies.add(title) }
    }

    fun addBook(book: Book, onResult: (com.davidgcd.backlog.data.repository.BookAddResult) -> Unit = {}) {
        viewModelScope.launch { onResult(books.add(book)) }
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        const val PER_CATEGORY = 5
    }
}

data class CrossSearchState(
    val games: List<Pair<Game, Boolean>> = emptyList(),
    val movies: List<Pair<MediaTitle, Boolean>> = emptyList(),
    val books: List<BookHit> = emptyList(),
) {
    val isEmpty: Boolean get() = games.isEmpty() && movies.isEmpty() && books.isEmpty()
}

class CrossSearchViewModelFactory(
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == CrossSearchViewModel::class.java)
        return CrossSearchViewModel(games, movies, books) as T
    }
}

/** What a tab needs to show the other categories: the shared search plus where each kind of row leads. */
class CrossSearch(
    val viewModel: CrossSearchViewModel,
    val onGameClick: (Long) -> Unit,
    val onMovieClick: (String) -> Unit,
    val onBookClick: (String) -> Unit,
)

/** Appends one section per non-tab category (games, films & séries, books) that has hits. */
fun LazyListScope.crossSearchItems(
    state: CrossSearchState,
    primary: MediaType,
    crossSearch: CrossSearch,
    onAdded: (String) -> Unit,
) {
    val vm = crossSearch.viewModel
    if (primary != MediaType.GAMES && state.games.isNotEmpty()) {
        item(key = "cross-games-header") { SectionTitle(R.string.tab_games) }
        items(state.games.size, key = { "cross-game-${state.games[it].first.id}" }) { i ->
            val (game, saved) = state.games[i]
            GameListItem(
                name = game.name,
                coverImageId = game.cover?.imageId,
                platforms = game.platforms?.map { FrenchLabels.platform(it.name) } ?: emptyList(),
                meta = ReleaseDateFormatting.format(game.firstReleaseDate),
                onClick = { crossSearch.onGameClick(game.id) },
                trailing = {
                    if (saved) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.discover_already_in_backlog),
                            tint = Glass.Green,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        AddButton(
                            contentDescription = stringResource(R.string.action_add_to_backlog),
                            onAdd = { vm.addGame(game); onAdded(game.name) },
                        )
                    }
                },
            )
        }
    }
    if (primary != MediaType.MOVIES && state.movies.isNotEmpty()) {
        item(key = "cross-movies-header") { SectionTitle(R.string.tab_movies) }
        items(state.movies.size, key = { "cross-movie-${state.movies[it].first.id}" }) { i ->
            val (title, saved) = state.movies[i]
            MediaTitleListItem(
                title = title,
                saved = saved,
                onAdd = { vm.addMovie(title); onAdded(title.title) },
                onClick = { crossSearch.onMovieClick(title.id) },
            )
        }
    }
    if (primary != MediaType.BOOKS && state.books.isNotEmpty()) {
        item(key = "cross-books-header") { SectionTitle(R.string.tab_books) }
        items(state.books.size, key = { "cross-book-${state.books[it].book.key}" }) { i ->
            val hit = state.books[i]
            BookHitItem(
                hit = hit,
                onAdd = { vm.addBook(hit.book); onAdded(hit.book.title) },
                onClick = { crossSearch.onBookClick(hit.savedKey ?: hit.book.key) },
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun SectionTitle(labelRes: Int) {
    Text(
        stringResource(R.string.search_also_in, stringResource(labelRes)),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = Glass.TextMuted,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
