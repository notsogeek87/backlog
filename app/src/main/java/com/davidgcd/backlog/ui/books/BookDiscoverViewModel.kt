package com.davidgcd.backlog.ui.books

import com.davidgcd.backlog.util.StaleCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookChart
import com.davidgcd.backlog.model.BookDuplicates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BookDiscoverState {
    data object Loading : BookDiscoverState
    data class Loaded(val books: List<Book>) : BookDiscoverState
    data object Error : BookDiscoverState
}

/** Open Library lists (trending, then the big subjects), one fetch per selected list — the twin of the films' Discover. */
class BookDiscoverViewModel(private val repository: BookRepository) : ViewModel() {
    private val _chart = MutableStateFlow(BookChart.TRENDING)
    val chart: StateFlow<BookChart> = _chart

    private val _loaded = MutableStateFlow<BookDiscoverState>(BookDiscoverState.Loading)

    /** The list's books, each marked with the saved row it duplicates (if any) — the add button turns into a check. */
    val hits: StateFlow<List<BookHit>> = combine(_loaded, repository.observeAll()) { state, saved ->
        val savedBooks = saved.map { it.toBook() }
        (state as? BookDiscoverState.Loaded)?.books.orEmpty().map { BookHit(it, BookDuplicates.find(it, savedBooks)?.key) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Loading / Loaded / Error for the screen; the books themselves come from [hits]. */
    val state: StateFlow<BookDiscoverState> = _loaded

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun select(chart: BookChart) {
        if (_chart.value == chart) return
        _chart.value = chart
        refresh()
    }

    fun refresh() {
        // A slower answer for a previously selected list must never overwrite the current one.
        loadJob?.cancel()
        val chart = _chart.value
        // Stale-while-revalidate: a list already seen shows at once, and is only re-fetched once it is old.
        val cached = cache.get(chart)
        if (cached != null) {
            _loaded.value = BookDiscoverState.Loaded(cached.value)
            if (cached.fresh) return
        } else {
            _loaded.value = BookDiscoverState.Loading
        }
        loadJob = viewModelScope.launch {
            try {
                val fresh = repository.chart(chart)
                if (fresh.isNotEmpty()) {
                    cache.put(chart, fresh)
                    _loaded.value = BookDiscoverState.Loaded(fresh)
                } else if (cached == null) {
                    _loaded.value = BookDiscoverState.Error
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (cached == null) _loaded.value = BookDiscoverState.Error
            }
        }
    }

    fun add(book: Book, onResult: (BookAddResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.add(book)
            onResult(result)
            if (result is BookAddResult.Added) repository.enrich(result.key)
        }
    }

    private companion object {
        val cache = StaleCache<BookChart, List<Book>>()
    }
}

class BookDiscoverViewModelFactory(private val repository: BookRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == BookDiscoverViewModel::class.java)
        return BookDiscoverViewModel(repository) as T
    }
}
