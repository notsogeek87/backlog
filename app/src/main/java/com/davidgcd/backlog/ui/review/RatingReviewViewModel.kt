package com.davidgcd.backlog.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.model.RatingReview
import com.davidgcd.backlog.model.ReviewItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RatingReviewState(
    val isLoading: Boolean = true,
    /** La file figée au départ : noter un élément ne la réordonne pas sous les doigts. */
    val queue: List<ReviewItem> = emptyList(),
    val position: Int = 0,
    /** Notes enregistrées pendant cette session (changées ou confirmées). */
    val rated: Int = 0,
) {
    val current: ReviewItem? get() = queue.getOrNull(position)
    val isFinished: Boolean get() = !isLoading && position >= queue.size
}

/** Fait défiler la bibliothèque entière, de la note la plus ancienne à la plus récente, pour la re-noter. */
class RatingReviewViewModel(
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RatingReviewState())
    val state: StateFlow<RatingReviewState> = _state

    init {
        start()
    }

    /** Reconstruit la file depuis la bibliothèque (premier lancement, ou « Recommencer »). */
    fun start() {
        viewModelScope.launch {
            _state.value = RatingReviewState(isLoading = true)
            val queue = RatingReview.queue(games.allGames(), movies.allMovies(), books.allBooks())
            _state.value = RatingReviewState(isLoading = false, queue = queue)
        }
    }

    /** Enregistre [rating] (ou re-confirme la note actuelle, qui prend alors la date du jour) puis passe au suivant. */
    fun rate(rating: Int) {
        val item = _state.value.current ?: return
        viewModelScope.launch {
            when (item.medium) {
                Medium.GAME -> item.key.toLongOrNull()?.let { id -> games.findEntity(id)?.let { games.setUserRating(it, rating) } }
                Medium.MOVIE -> movies.find(item.key)?.let { movies.setUserRating(it, rating) }
                Medium.BOOK -> books.find(item.key)?.let { books.setUserRating(it, rating) }
            }
            _state.update { it.copy(position = it.position + 1, rated = it.rated + 1) }
        }
    }

    fun skip() {
        _state.update { it.copy(position = it.position + 1) }
    }
}

class RatingReviewViewModelFactory(
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == RatingReviewViewModel::class.java)
        return RatingReviewViewModel(games, movies, books) as T
    }
}
