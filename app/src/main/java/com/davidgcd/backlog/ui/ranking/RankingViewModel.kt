package com.davidgcd.backlog.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.BookTop
import com.davidgcd.backlog.model.MovieRanking
import com.davidgcd.backlog.model.Ranking
import com.davidgcd.backlog.ui.components.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une ligne du classement, quel que soit le média : de quoi l'afficher et l'identifier. */
data class RankItem(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val coverImageId: String? = null,
    val coverUrl: String? = null,
    val isBook: Boolean = false,
    /** Faux tant que l'élément n'a jamais été placé : il suit les éléments classés, dans l'ordre d'ajout. */
    val ranked: Boolean = true,
)

/**
 * Le classement personnel d'un média, le préféré en premier. Déplacer un élément écrit tout de suite le nouvel ordre
 * dans Room ; les index sont ceux de la liste affichée.
 */
class RankingViewModel(
    private val media: MediaType,
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModel() {

    val items: StateFlow<List<RankItem>> = itemsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun itemsFlow(): Flow<List<RankItem>> = when (media) {
        MediaType.GAMES -> games.observeBacklog().map { list ->
            Ranking.order(list).map { RankItem(it.igdbId.toString(), it.name, coverImageId = it.coverImageId, ranked = it.userRank != null) }
        }
        MediaType.MOVIES -> movies.observeAll().map { list ->
            MovieRanking.order(list).map {
                RankItem(it.titleKey, it.title, it.year?.toString(), coverUrl = com.davidgcd.backlog.util.TmdbImage.poster(it.posterUrl, width = 185), ranked = it.userRank != null)
            }
        }
        MediaType.BOOKS -> books.observeAll().map { list ->
            BookTop.order(list).map {
                RankItem(it.bookKey, it.title, it.authors?.substringBefore(';'), coverUrl = it.coverUrl, isBook = true, ranked = it.userRank != null)
            }
        }
    }

    /** Place l'élément d'index [from] à l'index [to] (0 = préféré). Sans effet si rien ne bouge. */
    fun moveTo(from: Int, to: Int) {
        if (from == to) return
        viewModelScope.launch {
            when (media) {
                MediaType.GAMES -> {
                    val order = Ranking.order(games.allGames())
                    val moved = Ranking.moveTo(order, from, to)
                    if (moved !== order) games.applyRanking(moved)
                }
                MediaType.MOVIES -> {
                    val order = MovieRanking.order(movies.allMovies())
                    val moved = MovieRanking.moveTo(order, from, to)
                    if (moved !== order) movies.applyRanking(moved)
                }
                MediaType.BOOKS -> {
                    val order = BookTop.order(books.allBooks())
                    val moved = BookTop.moveTo(order, from, to)
                    if (moved !== order) books.applyRanking(moved)
                }
            }
        }
    }

    /** Straight to the top (first = most loved) or the bottom, for a big jump in one tap. */
    fun moveToTop(index: Int) = moveTo(index, 0)

    fun moveToBottom(index: Int) = moveTo(index, items.value.lastIndex)

    /** [delta] = -1 moves the item one place up (better), +1 one place down. */
    fun move(index: Int, delta: Int) = moveTo(index, (index + delta).coerceIn(0, items.value.lastIndex))
}

class RankingViewModelFactory(
    private val media: MediaType,
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == RankingViewModel::class.java)
        return RankingViewModel(media, games, movies, books) as T
    }
}
