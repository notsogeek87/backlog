package com.davidgcd.backlog.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.data.repository.BookRepository
import com.davidgcd.backlog.data.repository.MovieRepository
import com.davidgcd.backlog.model.YearRecap
import com.davidgcd.backlog.model.YearRecaps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** « Mon année » : le bilan d'une année civile (par défaut l'année en cours), calculé sur la bibliothèque entière. */
class RecapViewModel(games: BacklogRepository, movies: MovieRepository, books: BookRepository) : ViewModel() {
    private val _year = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year

    private val data = combine(games.observeBacklog(), movies.observeAll(), books.observeAll()) { g, m, b -> Triple(g, m, b) }

    /** Années avec de l'activité ; l'année en cours y figure toujours, pour qu'on puisse y revenir même vide. */
    val years: StateFlow<List<Int>> = data
        .combine(_year) { (g, m, b), _ -> (YearRecaps.years(g, m, b) + LocalDate.now().year).distinct().sortedDescending() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(LocalDate.now().year))

    val recap: StateFlow<YearRecap?> = data
        .combine(_year) { (g, m, b), year -> YearRecaps.compute(year, g, m, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectYear(year: Int) {
        _year.value = year
    }
}

class RecapViewModelFactory(
    private val games: BacklogRepository,
    private val movies: MovieRepository,
    private val books: BookRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == RecapViewModel::class.java)
        return RecapViewModel(games, movies, books) as T
    }
}
