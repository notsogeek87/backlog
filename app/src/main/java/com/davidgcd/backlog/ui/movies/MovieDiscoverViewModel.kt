package com.davidgcd.backlog.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.ImdbTitle
import com.davidgcd.backlog.model.MovieChart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MovieDiscoverState {
    data object Loading : MovieDiscoverState
    data class Loaded(val titles: List<ImdbTitle>) : MovieDiscoverState
    data object Error : MovieDiscoverState
}

/** IMDb charts (popular / top rated, films / séries), one fetch per selected chart — the twin of the games' DiscoverViewModel. */
class MovieDiscoverViewModel(private val repository: MovieRepository) : ViewModel() {
    private val _state = MutableStateFlow<MovieDiscoverState>(MovieDiscoverState.Loading)
    val state: StateFlow<MovieDiscoverState> = _state

    private val _chart = MutableStateFlow(MovieChart.POPULAR_MOVIES)
    val chart: StateFlow<MovieChart> = _chart

    private var loadJob: Job? = null

    val savedIds: StateFlow<Set<String>> = repository.observeAll()
        .map { list -> list.map { it.imdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        refresh()
    }

    fun select(chart: MovieChart) {
        if (_chart.value == chart) return
        _chart.value = chart
        refresh()
    }

    fun refresh() {
        // A slower response for a previously selected chart must never overwrite the current one.
        loadJob?.cancel()
        val chart = _chart.value
        loadJob = viewModelScope.launch {
            _state.value = MovieDiscoverState.Loading
            _state.value = try {
                // Charts list hundreds of titles; the first page is what anyone browses.
                repository.chart(chart).take(MAX_TITLES).let {
                    if (it.isEmpty()) MovieDiscoverState.Error else MovieDiscoverState.Loaded(it)
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                MovieDiscoverState.Error
            }
        }
    }

    fun add(title: ImdbTitle) {
        viewModelScope.launch { repository.add(title) }
    }

    private companion object {
        const val MAX_TITLES = 50
    }
}

class MovieDiscoverViewModelFactory(private val repository: MovieRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MovieDiscoverViewModel::class.java)
        return MovieDiscoverViewModel(repository) as T
    }
}
