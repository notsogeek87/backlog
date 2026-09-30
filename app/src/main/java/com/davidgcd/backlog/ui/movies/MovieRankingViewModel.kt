package com.davidgcd.backlog.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.MovieEntity
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.MovieRanking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The personal ranking of films & séries, most loved first. Moving a title writes the new order straight to Room. */
class MovieRankingViewModel(private val repository: MovieRepository) : ViewModel() {
    val ordered: StateFlow<List<MovieEntity>> = repository.observeAll()
        .map(MovieRanking::order)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveToTop(index: Int) = moveTo(index, 0)

    fun moveToBottom(index: Int) = moveTo(index, ordered.value.lastIndex)

    private fun moveTo(index: Int, target: Int) {
        val newOrder = MovieRanking.moveTo(ordered.value, index, target)
        if (newOrder === ordered.value) return
        viewModelScope.launch { repository.applyRanking(newOrder) }
    }

    /** [delta] = -1 moves the title one place up (better), +1 one place down. */
    fun move(index: Int, delta: Int) {
        val newOrder = MovieRanking.move(ordered.value, index, delta)
        if (newOrder === ordered.value) return
        viewModelScope.launch { repository.applyRanking(newOrder) }
    }
}

class MovieRankingViewModelFactory(private val repository: MovieRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == MovieRankingViewModel::class.java)
        return MovieRankingViewModel(repository) as T
    }
}
