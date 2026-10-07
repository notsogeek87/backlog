package com.davidgcd.backlog.ui.movies

import com.davidgcd.backlog.util.StaleCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.MediaTitle
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
    data class Loaded(val titles: List<MediaTitle>) : MovieDiscoverState
    data object Error : MovieDiscoverState
}

/** TMDB charts (popular / top rated, films / séries), one fetch per selected chart — the twin of the games' DiscoverViewModel. */
class MovieDiscoverViewModel(private val repository: MovieRepository) : ViewModel() {
    private val _state = MutableStateFlow<MovieDiscoverState>(MovieDiscoverState.Loading)
    val state: StateFlow<MovieDiscoverState> = _state

    private val _chart = MutableStateFlow(MovieChart.POPULAR_MOVIES)
    val chart: StateFlow<MovieChart> = _chart

    private var loadJob: Job? = null

    val savedIds: StateFlow<Set<String>> = repository.observeAll()
        .map { list -> list.map { it.titleKey }.toSet() }
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
        // Stale-while-revalidate: a chart already seen shows at once, and is only re-fetched once it is old.
        val cached = cache.get(chart)
        if (cached != null) {
            _state.value = MovieDiscoverState.Loaded(cached.value)
            if (cached.fresh) return
        } else {
            _state.value = MovieDiscoverState.Loading
        }
        loadJob = viewModelScope.launch {
            try {
                // Charts list hundreds of titles; the first page is what anyone browses.
                val fresh = repository.chart(chart).take(MAX_TITLES)
                if (fresh.isNotEmpty()) {
                    cache.put(chart, fresh)
                    _state.value = MovieDiscoverState.Loaded(fresh)
                } else if (cached == null) {
                    _state.value = MovieDiscoverState.Error
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (cached == null) _state.value = MovieDiscoverState.Error
            }
        }
    }

    fun add(title: MediaTitle) {
        viewModelScope.launch { repository.add(title) }
    }

    private companion object {
        const val MAX_TITLES = 50
        val cache = StaleCache<MovieChart, List<MediaTitle>>()
    }
}

class MovieDiscoverViewModelFactory(private val repository: MovieRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MovieDiscoverViewModel::class.java)
        return MovieDiscoverViewModel(repository) as T
    }
}
