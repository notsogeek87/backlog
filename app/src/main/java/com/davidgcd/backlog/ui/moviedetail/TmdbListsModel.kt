package com.davidgcd.backlog.ui.moviedetail

import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.data.repository.TmdbListsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The « Mes listes TMDB » card: hidden (no account linked / TMDB unreachable), loading, or the lists with the ticks. */
sealed interface TmdbListsUi {
    data object Hidden : TmdbListsUi
    data object Loading : TmdbListsUi
    /** [busy] = the rows being written right now ([WATCHLIST_ROW] or [listRow]); they ignore further taps. */
    data class Loaded(val state: TmdbListsState, val busy: Set<String> = emptySet()) : TmdbListsUi

    companion object {
        const val WATCHLIST_ROW = "watchlist"
        fun listRow(id: Long) = "list:$id"
    }
}

/**
 * Where one title stands in the user's TMDB account (watchlist + their own lists) and the ticks to flip it.
 * Shared by the detail page and by the « ajouter aussi à une liste » sheet shown after an add from a list;
 * the caller gives the [scope] the writes run in.
 */
class TmdbListsModel(
    private val scope: CoroutineScope,
    private val repository: MovieRepository,
    private val titleKey: String,
) {
    private val _state = MutableStateFlow<TmdbListsUi>(TmdbListsUi.Loading)
    val state: StateFlow<TmdbListsUi> = _state

    init {
        scope.launch {
            val loaded = try {
                repository.tmdbLists(titleKey)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }
            _state.value = if (loaded == null) TmdbListsUi.Hidden else TmdbListsUi.Loaded(loaded)
        }
    }

    fun toggleWatchlist(onError: () -> Unit) = toggle(
        row = TmdbListsUi.WATCHLIST_ROW,
        current = { it.watchlist },
        apply = { state, on -> state.copy(watchlist = on) },
        write = { on -> repository.setInTmdbWatchlist(titleKey, on) },
        onError = onError,
    )

    fun toggleList(listId: Long, onError: () -> Unit) = toggle(
        row = TmdbListsUi.listRow(listId),
        current = { state -> state.lists.firstOrNull { it.list.id == listId }?.contains },
        apply = { state, on -> state.copy(lists = state.lists.map { if (it.list.id == listId) it.copy(contains = on) else it }) },
        write = { on -> repository.setInTmdbList(listId, titleKey, on) },
        onError = onError,
    )

    /** Optimistic: the tick flips at once, and flips back if TMDB can't be written. */
    private fun toggle(
        row: String,
        current: (TmdbListsState) -> Boolean?,
        apply: (TmdbListsState, Boolean) -> TmdbListsState,
        write: suspend (Boolean) -> Unit,
        onError: () -> Unit,
    ) {
        val loaded = _state.value as? TmdbListsUi.Loaded ?: return
        if (row in loaded.busy) return
        val before = current(loaded.state) ?: return
        val wanted = !before
        _state.value = loaded.copy(state = apply(loaded.state, wanted), busy = loaded.busy + row)

        fun settle(on: Boolean) {
            (_state.value as? TmdbListsUi.Loaded)?.let { now ->
                _state.value = now.copy(state = apply(now.state, on), busy = now.busy - row)
            }
        }
        scope.launch {
            try {
                write(wanted)
                settle(wanted)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                settle(before)
                onError()
            }
        }
    }
}
