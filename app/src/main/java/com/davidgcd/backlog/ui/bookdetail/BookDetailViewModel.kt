package com.davidgcd.backlog.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.data.share.ShareLinkService
import com.davidgcd.backlog.data.share.toShareItem
import com.davidgcd.backlog.util.AppLogger
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.ReadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BookDetailState {
    data object Loading : BookDetailState
    data class Saved(val book: BookEntity) : BookDetailState
    /** Seen in a search but not saved; [duplicateKey] is the saved row it duplicates, if any. */
    data class Remote(val book: Book, val duplicateKey: String?) : BookDetailState
    data object NotFound : BookDetailState
}

/** A book on the detail screen may or may not be in the list — same split as the game and film details. */
class BookDetailViewModel(
    private val bookKey: String,
    private val repository: BookRepository,
    private val shareLinkService: ShareLinkService? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<BookDetailState>(BookDetailState.Loading)
    val state: StateFlow<BookDetailState> = _state

    /** Other editions of the same work, fetched once; empty when unknown or unreachable (the card is then hidden). */
    private val _editions = MutableStateFlow<List<Book>>(emptyList())
    val editions: StateFlow<List<Book>> = _editions

    /** What the list holds, to mark the editions already added. */
    val savedBooks: StateFlow<List<Book>> = repository.observeAll()
        .map { list -> list.map { it.toBook() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var enriched = false
    private var remoteLoadStarted = false
    private var editionsRequested = false

    init {
        repository.observe(bookKey)
            .onEach { entity ->
                if (entity != null) {
                    _state.value = BookDetailState.Saved(entity)
                    enrichOnce()
                    loadEditionsOnce(entity.toBook())
                } else if (_state.value !is BookDetailState.Saved) {
                    loadRemoteIfNeeded()
                } else {
                    // Removed while open: nothing left to show.
                    _state.value = BookDetailState.NotFound
                }
            }
            .launchIn(viewModelScope)
    }

    /** Fills in a saved book's missing facts (description, ISBN, publisher…) once, quietly. */
    private fun enrichOnce() {
        if (enriched) return
        enriched = true
        viewModelScope.launch {
            try {
                repository.enrich(bookKey)
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
            val book = try {
                repository.fetchRemote(bookKey)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }
            if (_state.value is BookDetailState.Saved) return@launch
            if (book == null) {
                _state.value = BookDetailState.NotFound
                return@launch
            }
            _state.value = BookDetailState.Remote(book, repository.findDuplicate(book)?.bookKey)
            loadEditionsOnce(book)
        }
    }

    private fun loadEditionsOnce(book: Book) {
        if (editionsRequested || book.workId == null) return
        editionsRequested = true
        viewModelScope.launch { _editions.value = repository.editions(book) }
    }

    /** Adds [book] (this one, or another edition of it) as "à lire", then completes it in the background. */
    fun add(book: Book, onResult: (BookAddResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.add(book)
            onResult(result)
            if (result is BookAddResult.Added) repository.enrich(result.key)
        }
    }

    /**
     * This one book on a public page of its own (a fresh link each time), or null when the server can't be
     * reached — the caller then shares plain text. A saved book carries the reader's status, favorite and note.
     */
    suspend fun publishBookLink(book: Book, saved: BookEntity?): String? {
        val service = shareLinkService ?: return null
        return try {
            service.publishBook(book.toShareItem(saved))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppLogger.network.error("Share link: publishing the book page failed, sharing plain text instead", t)
            null
        }
    }

    fun setStatus(book: BookEntity, status: ReadStatus) {
        viewModelScope.launch { repository.setStatus(book, status) }
    }

    fun setFavorite(book: BookEntity, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(book, favorite) }
    }

    /** Tapping the current rating again clears it. */
    fun setUserRating(book: BookEntity, rating: Int) {
        viewModelScope.launch { repository.setUserRating(book, rating.takeIf { it != book.userRating }) }
    }

    /** [onDone] runs once the row is deleted, so leaving the screen can't cancel the delete mid-flight. */
    fun remove(book: BookEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.remove(book)
            onDone()
        }
    }
}

class BookDetailViewModelFactory(
    private val bookKey: String,
    private val repository: BookRepository,
    private val shareLinkService: ShareLinkService? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == BookDetailViewModel::class.java)
        return BookDetailViewModel(bookKey, repository, shareLinkService) as T
    }
}
