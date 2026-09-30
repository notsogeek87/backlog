package com.davidgcd.backlog.ui.moviedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface MovieDetailState {
    data object Loading : MovieDetailState
    data class Saved(val movie: MovieEntity) : MovieDetailState
    /** Seen in a search / chart / link but not saved yet. */
    data class Remote(val title: MediaTitle) : MovieDetailState
    data object NotFound : MovieDetailState
}

/** A title on the detail screen may or may not be in the list — same split as the game detail. */
class MovieDetailViewModel(
    private val titleKey: String,
    private val repository: MovieRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MovieDetailState>(MovieDetailState.Loading)
    val state: StateFlow<MovieDetailState> = _state

    private var refreshed = false
    private var remoteLoadStarted = false

    init {
        repository.observe(titleKey)
            .onEach { movie ->
                if (movie != null) {
                    _state.value = MovieDetailState.Saved(movie)
                    refreshOnce(movie)
                } else if (_state.value !is MovieDetailState.Saved) {
                    loadRemoteIfNeeded()
                } else {
                    // Removed while open: nothing left to show.
                    _state.value = MovieDetailState.NotFound
                }
            }
            .launchIn(viewModelScope)
    }

    /** Titles imported from TMDB's CSV have no plot: pull the page once, quietly. */
    private fun refreshOnce(movie: MovieEntity) {
        if (refreshed || (movie.plot != null && movie.tmdbRating != null)) return
        refreshed = true
        viewModelScope.launch {
            try {
                repository.refresh(movie)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Best effort: the saved data is still shown.
            }
        }
    }

    private fun loadRemoteIfNeeded() {
        if (remoteLoadStarted) return
        remoteLoadStarted = true
        viewModelScope.launch {
            val title = try {
                repository.fetchRemote(titleKey)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }
            if (_state.value is MovieDetailState.Saved) return@launch
            _state.value = if (title != null) MovieDetailState.Remote(title) else MovieDetailState.NotFound
        }
    }

    fun add(title: MediaTitle) {
        viewModelScope.launch { repository.add(title) }
    }

    fun setStatus(movie: MovieEntity, status: WatchStatus) {
        viewModelScope.launch { repository.setStatus(movie, status) }
    }

    /** Tapping the current rating again clears it. */
    fun setUserRating(movie: MovieEntity, rating: Int) {
        viewModelScope.launch { repository.setUserRating(movie, rating.takeIf { it != movie.userRating }) }
    }

    fun setArchived(movie: MovieEntity, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(movie, archived) }
    }

    /** [onDone] runs once the row is deleted, so leaving the screen can't cancel the delete mid-flight. */
    fun remove(movie: MovieEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.remove(movie)
            onDone()
        }
    }
}

class MovieDetailViewModelFactory(
    private val titleKey: String,
    private val repository: MovieRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MovieDetailViewModel::class.java)
        return MovieDetailViewModel(titleKey, repository) as T
    }
}
