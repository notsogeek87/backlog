package com.davidgcd.backlog.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.authorList
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.local.subjectList
import com.davidgcd.backlog.data.openlibrary.BookSourceException
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookDuplicates
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.ui.backlog.SearchError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BookSort {
    RECENTLY_ADDED,
    TITLE,
    AUTHOR,
    PUBLISHED_YEAR,
    MY_RATING,
}

/** `null` = any. */
data class BookFilter(
    val status: ReadStatus? = null,
    val favoritesOnly: Boolean = false,
    val subject: String? = null,
) {
    val isActive: Boolean get() = status != null || favoritesOnly || subject != null
}

/** A search hit and, when the list already holds this book (or this edition), the key of the saved row. */
data class BookHit(val book: Book, val savedKey: String?)

/** State of the Livres tab: the saved list (sorted / filtered) and the catalogue search. */
class BooksViewModel(private val repository: BookRepository) : ViewModel() {

    private val books: StateFlow<List<BookEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sort = MutableStateFlow(BookSort.RECENTLY_ADDED)
    val sort: StateFlow<BookSort> = _sort

    private val _filter = MutableStateFlow(BookFilter())
    val filter: StateFlow<BookFilter> = _filter

    /** Per-status counts for the header tiles, never narrowed by the filters. */
    val statusCounts: StateFlow<Map<ReadStatus, Int>> = books
        .map { list -> ReadStatus.entries.associateWith { status -> list.count { it.readStatus == status } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadStatus.entries.associateWith { 0 })

    val isEmpty: StateFlow<Boolean> = books
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The most common subjects of the list (Open Library lists dozens per book: the menu keeps the top few). */
    val availableSubjects: StateFlow<List<String>> = books
        .map { list ->
            list.flatMap { it.subjectList }.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(MAX_SUBJECT_FILTERS).map { it.key }.sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visible: StateFlow<List<BookEntity>> = combine(books, _sort, _filter) { list, sort, filter ->
        list
            .filter { filter.status == null || it.readStatus == filter.status }
            .filter { !filter.favoritesOnly || it.isFavorite }
            .filter { filter.subject == null || it.subjectList.contains(filter.subject) }
            .sortedWith(sort.comparator())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSort(sort: BookSort) {
        _sort.value = sort
    }

    fun setFilter(filter: BookFilter) {
        _filter.value = filter
    }

    fun setStatus(book: BookEntity, status: ReadStatus) {
        viewModelScope.launch { repository.setStatus(book, status) }
    }

    // --- search ------------------------------------------------------------------------------

    private val _results = MutableStateFlow<List<Book>>(emptyList())

    /** Search hits, each marked with the saved row it duplicates (if any) so the list can show "déjà ajouté". */
    val searchResults: StateFlow<List<BookHit>> = combine(_results, books) { results, saved ->
        val savedBooks = saved.map { it.toBook() }
        results.map { hit ->
            val match = BookDuplicates.find(hit, savedBooks)
            BookHit(hit, match?.key)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore

    /** True when the results come from Google Books because Open Library had nothing / was down. */
    private val _fromFallback = MutableStateFlow(false)
    val fromFallback: StateFlow<Boolean> = _fromFallback

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var query = ""
    private var page = 1

    /** Debounced: every keystroke restarts a 300 ms wait, so only the query the user stopped on is sent. */
    fun search(text: String) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        query = text.trim()
        page = 1
        _isLoadingMore.value = false
        if (query.isEmpty()) {
            _results.value = emptyList()
            _hasMore.value = false
            _fromFallback.value = false
            _isSearching.value = false
            _searchError.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _isSearching.value = true
            _searchError.value = null
            try {
                val result = repository.search(query, 1)
                _results.value = result.books
                _hasMore.value = result.hasMore
                _fromFallback.value = result.fromFallback
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _results.value = emptyList()
                _hasMore.value = false
                _searchError.value = t.toSearchError()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /** Next page of the current query; the list only grows when the user asks for more. */
    fun loadMore() {
        if (!_hasMore.value || _isLoadingMore.value || _isSearching.value || query.isEmpty()) return
        val asked = query
        loadMoreJob = viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = repository.search(asked, page + 1)
                if (asked == query) {
                    page++
                    _results.value = (_results.value + result.books).distinctBy { it.key }
                    _hasMore.value = result.hasMore
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // The first pages stay on screen; "Voir plus" simply stays available to retry.
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /** Adds [book] as "à lire" and completes it from Open Library in the background; [onResult] says added / already there. */
    fun add(book: Book, onResult: (BookAddResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.add(book)
            onResult(result)
            if (result is BookAddResult.Added) repository.enrich(result.key)
        }
    }

    private fun Throwable.toSearchError(): SearchError = when {
        this is BookSourceException && isNetwork -> SearchError.Network
        this is BookSourceException -> SearchError.Server
        else -> SearchError.Unknown
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MAX_SUBJECT_FILTERS = 20
    }
}

private fun BookSort.comparator(): Comparator<BookEntity> = when (this) {
    BookSort.RECENTLY_ADDED -> compareByDescending { it.addedAt }
    BookSort.TITLE -> compareBy { it.title.lowercase() }
    BookSort.AUTHOR -> compareBy<BookEntity> { it.authorList.firstOrNull()?.lowercase() ?: "￿" }.thenBy { it.title.lowercase() }
    BookSort.PUBLISHED_YEAR -> Comparator { a, b ->
        // Newest first; a book with no year goes last whatever the sort.
        val left = a.publishedYear
        val right = b.publishedYear
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> right.compareTo(left)
        }
    }
    BookSort.MY_RATING -> Comparator { a, b ->
        val left = a.userRating
        val right = b.userRating
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> right.compareTo(left)
        }
    }
}

class BooksViewModelFactory(private val repository: BookRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == BooksViewModel::class.java)
        return BooksViewModel(repository) as T
    }
}
