package com.davidgcd.backlog.ui.platforms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.library.ImportResult
import com.davidgcd.backlog.data.library.ItemStatus
import com.davidgcd.backlog.data.library.LibraryError
import com.davidgcd.backlog.data.library.LibraryProgress
import com.davidgcd.backlog.data.library.LibrarySyncService
import com.davidgcd.backlog.data.library.SyncPreview
import com.davidgcd.backlog.data.library.toLibraryException
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportPhase { WORKING, PREVIEW, DONE, ERROR }

data class LibraryImportUiState(
    val phase: ImportPhase = ImportPhase.WORKING,
    val progress: LibraryProgress? = null,
    val preview: SyncPreview? = null,
    /** Store ids of the NEW / accepted-UNCERTAIN items that "Import" will apply. */
    val selected: Set<String> = emptySet(),
    val result: ImportResult? = null,
    /** With phase ERROR: null means the store answered but the library is empty. */
    val error: LibraryError? = null,
) {
    /** Selected games that would actually be created or linked. */
    val selectedCount: Int get() = preview?.items?.count { it.key in selected && it.isImportable } ?: 0
}

/** One screen for first import and every later "Synchronize": it always starts by syncing. */
class LibraryImportViewModel(
    private val providerId: String,
    private val sync: LibrarySyncService,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryImportUiState())
    val state: StateFlow<LibraryImportUiState> = _state
    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        if (job?.isActive == true) return
        _state.value = LibraryImportUiState()
        job = viewModelScope.launch {
            try {
                val preview = sync.sync(providerId) { p -> _state.update { it.copy(progress = p) } }
                _state.value = if (preview.items.isEmpty()) {
                    LibraryImportUiState(phase = ImportPhase.ERROR, preview = preview) // no error code = genuinely empty library
                } else {
                    LibraryImportUiState(
                        phase = ImportPhase.PREVIEW,
                        preview = preview,
                        // New games pre-selected; uncertain ones and "removed earlier" ones are opt-in.
                        selected = preview.items.filter { it.status == ItemStatus.NEW && !it.previouslyRemoved }.map { it.key }.toSet(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    fun toggle(key: String) = _state.update { s ->
        s.copy(selected = if (key in s.selected) s.selected - key else s.selected + key)
    }

    /** Selects every NEW game, or clears them all if they already were. */
    fun toggleAllNew() = _state.update { s ->
        val newKeys = s.preview?.items(ItemStatus.NEW)?.map { it.key }?.toSet().orEmpty()
        s.copy(selected = if (s.selected.containsAll(newKeys)) s.selected - newKeys else s.selected + newKeys)
    }

    fun importSelected() {
        val current = _state.value
        val preview = current.preview ?: return
        if (current.phase != ImportPhase.PREVIEW || job?.isActive == true) return
        _state.update { it.copy(phase = ImportPhase.WORKING, progress = LibraryProgress(LibraryProgress.Stage.IMPORTING)) }
        job = viewModelScope.launch {
            try {
                val result = sync.import(providerId, preview, current.selected) { p -> _state.update { it.copy(progress = p) } }
                _state.value = LibraryImportUiState(phase = ImportPhase.DONE, preview = preview, result = result)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    private fun fail(t: Throwable) {
        val error = t.toLibraryException()
        AppLogger.network.error("Library sync failed (${error.error}): ${error.message}", t)
        _state.value = LibraryImportUiState(phase = ImportPhase.ERROR, error = error.error)
    }
}

class LibraryImportViewModelFactory(
    private val providerId: String,
    private val sync: LibrarySyncService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == LibraryImportViewModel::class.java)
        return LibraryImportViewModel(providerId, sync) as T
    }
}
