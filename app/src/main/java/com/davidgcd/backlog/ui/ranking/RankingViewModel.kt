package com.davidgcd.backlog.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.Ranking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The personal ranking, most loved first. Moving a game writes the new order straight to Room. */
class RankingViewModel(private val repository: BacklogRepository) : ViewModel() {
    val ordered: StateFlow<List<GameEntity>> = repository.observeBacklog()
        .map(Ranking::order)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Straight to the top (first = most loved) or the bottom, for a big jump in one tap. */
    fun moveToTop(index: Int) = moveTo(index, 0)

    fun moveToBottom(index: Int) = moveTo(index, ordered.value.lastIndex)

    private fun moveTo(index: Int, target: Int) {
        val newOrder = Ranking.moveTo(ordered.value, index, target)
        if (newOrder === ordered.value) return
        viewModelScope.launch { repository.applyRanking(newOrder) }
    }

    /** [delta] = -1 moves the game one place up (better), +1 one place down. */
    fun move(index: Int, delta: Int) {
        val newOrder = Ranking.move(ordered.value, index, delta)
        if (newOrder === ordered.value) return
        viewModelScope.launch { repository.applyRanking(newOrder) }
    }
}

class RankingViewModelFactory(private val repository: BacklogRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == RankingViewModel::class.java)
        return RankingViewModel(repository) as T
    }
}
