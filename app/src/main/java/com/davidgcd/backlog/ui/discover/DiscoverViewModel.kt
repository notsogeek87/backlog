package com.davidgcd.backlog.ui.discover

import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.model.suggestedFor
import com.davidgcd.backlog.util.StaleCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.data.repository.BacklogRepository
import com.davidgcd.backlog.model.DiscoverCategory
import com.davidgcd.backlog.model.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DiscoverState {
    data object Loading : DiscoverState
    data class Loaded(val games: List<Game>) : DiscoverState
    data object Error : DiscoverState
}

/**
 * Games discovery (popular, top rated, trending, new, upcoming — see [DiscoverCategory]),
 * a simplified stand-in for the iOS app's PopularGamesLoader: one fetch per selected list, refreshed on demand
 * (no disk cache / stale-while-revalidate yet, see README). Backlog games
 * are filtered out client-side so "add" only ever offers something new.
 */
class DiscoverViewModel(private val repository: BacklogRepository) : ViewModel() {

    private val _state = MutableStateFlow<DiscoverState>(DiscoverState.Loading)
    val state: StateFlow<DiscoverState> = _state

    private val _category = MutableStateFlow(DiscoverCategory.PERSONALIZED)
    val category: StateFlow<DiscoverCategory> = _category

    private var loadJob: Job? = null

    val backlogIds: StateFlow<Set<Long>> = repository.observeBacklog()
        .combine(_state) { list, _ -> list.map { it.igdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Pour la section "Pour vous" : jeux du backlog pour extraire genres/platformes préférés
    val backlogGames: StateFlow<List<GameEntity>> = repository.observeBacklog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun select(category: DiscoverCategory) {
        if (_category.value == category) return
        _category.value = category
        refresh()
    }

    fun refresh() {
        // A slower response for a previously selected list must never overwrite the current one.
        loadJob?.cancel()
        val category = _category.value
        // Stale-while-revalidate: a list already seen shows at once, and is only re-fetched once it is old.
        val cached = cache.get(category)
        if (cached != null) {
            _state.value = DiscoverState.Loaded(cached.value)
            if (cached.fresh) return
        } else {
            _state.value = DiscoverState.Loading
        }
        loadJob = viewModelScope.launch {
            try {
                val fresh = if (category == DiscoverCategory.PERSONALIZED) {
                    getPersonalizedRecommendations()
                } else {
                    repository.getDiscoverGames(category)
                }
                cache.put(category, fresh)
                _state.value = DiscoverState.Loaded(fresh)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // A failed refresh keeps the list that is already on screen.
                if (cached == null) _state.value = DiscoverState.Error
            }
        }
    }

    /**
     * Génère des recommandations personnalisées basées sur les genres et plateformes
     * les plus fréquents dans le backlog de l'utilisateur.
     */
    private suspend fun getPersonalizedRecommendations(): List<Game> {
        val backlog = backlogGames.value
        if (backlog.isEmpty()) {
            // Si le backlog est vide, retourne les jeux populaires par défaut
            return repository.getDiscoverGames(DiscoverCategory.POPULAR)
        }

        // Extraire les genres et plateformes les plus fréquents
        val allGenres = backlog.flatMap { GameJsonCache.genreNames(it) }
        val allPlatforms = backlog.flatMap { GameJsonCache.platformNames(it) }

        val favoriteGenres = allGenres.groupBy { it }.maxByOrNull { it.value.size }?.key
        val favoritePlatforms = allPlatforms.groupBy { it }.maxByOrNull { it.value.size }?.key

        // Récupérer les jeux populaires et mieux notés, puis les filtrer
        val popularGames = repository.getDiscoverGames(DiscoverCategory.POPULAR, limit = 50)
        val topRatedGames = repository.getDiscoverGames(DiscoverCategory.TOP_RATED, limit = 50)
        val allGames = (popularGames + topRatedGames).distinctBy { it.id }

        // Filtrer par genres et plateformes préférés (si disponibles)
        return allGames
            .filter { game ->
                (favoriteGenres == null || game.genres?.any { it.name == favoriteGenres } == true) &&
                (favoritePlatforms == null || game.platforms?.any { it.name == favoritePlatforms } == true)
            }
            .take(20)
            .ifEmpty {
                // Si aucun jeu ne correspond, retourner les populaires
                popularGames.take(20)
            }
    }

    /** Sans [status] : « Souhaité » pour un jeu pas encore sorti, « Backlog » sinon. */
    fun addToBacklog(game: Game, status: GameStatus? = null) {
        viewModelScope.launch { repository.addToBacklog(game, status ?: GameStatus.suggestedFor(game.firstReleaseDate)) }
    }

    private companion object {
        val cache = StaleCache<DiscoverCategory, List<Game>>()
    }
}

class DiscoverViewModelFactory(private val repository: BacklogRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == DiscoverViewModel::class.java)
        return DiscoverViewModel(repository) as T
    }
}
