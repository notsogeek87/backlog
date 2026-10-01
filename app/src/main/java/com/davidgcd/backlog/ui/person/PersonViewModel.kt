package com.davidgcd.backlog.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.MediaTitle
import com.davidgcd.backlog.model.PersonFilmography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PersonState {
    data object Loading : PersonState
    data class Loaded(val person: PersonFilmography) : PersonState
    data object Error : PersonState
}

/** The films & séries an actor played in, or a director directed — fetched live from TMDB. */
class PersonViewModel(
    private val personId: Long,
    val asDirector: Boolean,
    private val repository: MovieRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PersonState>(PersonState.Loading)
    val state: StateFlow<PersonState> = _state

    val savedIds: StateFlow<Set<String>> = repository.observeAll()
        .map { list -> list.map { it.titleKey }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = PersonState.Loading
            _state.value = try {
                repository.personFilmography(personId, asDirector)?.let { PersonState.Loaded(it) } ?: PersonState.Error
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                PersonState.Error
            }
        }
    }

    fun add(title: MediaTitle) {
        viewModelScope.launch { repository.add(title) }
    }
}

class PersonViewModelFactory(
    private val personId: Long,
    private val asDirector: Boolean,
    private val repository: MovieRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PersonViewModel::class.java)
        return PersonViewModel(personId, asDirector, repository) as T
    }
}
