package com.davidgcd.backlog.ui.backlog

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import kotlinx.coroutines.launch
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.GameEntity
import com.davidgcd.backlog.data.local.GameJsonCache
import com.davidgcd.backlog.data.local.gameStatus
import com.davidgcd.backlog.model.Game
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.model.next
import com.davidgcd.backlog.model.suggestedFor
import com.davidgcd.backlog.ui.components.AddButton
import com.davidgcd.backlog.ui.components.AddChoice
import com.davidgcd.backlog.ui.components.FilterSortSheet
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GameListItem
import com.davidgcd.backlog.ui.components.LibraryEmptyState
import com.davidgcd.backlog.ui.components.LibrarySearchField
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.components.NoResultsMessage
import com.davidgcd.backlog.ui.components.OverflowAction
import com.davidgcd.backlog.ui.components.OverflowMenuButton
import com.davidgcd.backlog.ui.components.QuickAction
import com.davidgcd.backlog.ui.components.QuickActionsSheet
import com.davidgcd.backlog.ui.components.RemovableChip
import com.davidgcd.backlog.ui.components.SearchFeedback
import com.davidgcd.backlog.ui.components.SearchCarry
import com.davidgcd.backlog.ui.components.SearchRequest
import com.davidgcd.backlog.ui.components.SearchScope
import com.davidgcd.backlog.ui.components.SearchScopePills
import com.davidgcd.backlog.ui.components.SectionHeader
import com.davidgcd.backlog.ui.components.SheetChoice
import com.davidgcd.backlog.ui.components.SheetSection
import com.davidgcd.backlog.ui.components.SortLabel
import com.davidgcd.backlog.ui.components.StatCard
import com.davidgcd.backlog.ui.components.StatusChoice
import com.davidgcd.backlog.ui.components.SwipeAction
import com.davidgcd.backlog.ui.components.SwipeActionRow
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.labelRes
import com.davidgcd.backlog.ui.components.rememberShareFlow
import com.davidgcd.backlog.ui.components.shareDetailLink
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.ui.search.CrossSearch
import com.davidgcd.backlog.ui.search.CrossSearchState
import com.davidgcd.backlog.ui.search.crossSearchItems
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.BacklogShareText
import com.davidgcd.backlog.util.DetailLink
import com.davidgcd.backlog.util.DetailPreview
import com.davidgcd.backlog.util.FrenchLabels
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Grille, puces de filtre et résultats de recherche de l'écran Jeux (BacklogScreen.kt garde l'écran lui-même).

/**
 * Home + list in one adaptive grid: 1 column on phones, several on tablets/desktop.
 * Header blocks (stats, recent carousel, active filters) span the full width.
 */
@Composable
internal fun BacklogGrid(
    games: List<GameEntity>,
    filter: BacklogFilter,
    sort: BacklogSort,
    searchText: String,
    statusCounts: Map<GameStatus, Int>,
    recentlyAdded: List<GameEntity>,
    onFilterChange: (BacklogFilter) -> Unit,
    onOpenSheet: () -> Unit,
    onSearchCatalog: () -> Unit,
    onArchiveToggle: (GameEntity) -> Unit,
    onNextStatus: (GameEntity) -> Unit,
    onStatusChange: (GameEntity, GameStatus) -> Unit,
    onLongClick: (GameEntity) -> Unit,
    onClick: (GameEntity) -> Unit,
    listState: LazyGridState,
) {
    val searching = searchText.isNotBlank()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        if (!searching) {
            // The status tiles are the status filter: tap one to narrow the list, tap it again to clear.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameStatus.entries.forEach { status ->
                        StatCard(
                            value = (statusCounts[status] ?: 0).toString(),
                            label = status.label(),
                            modifier = Modifier.weight(1f),
                            accent = status.tint(),
                            selected = filter.status == status,
                            onClick = { onFilterChange(filter.copy(status = if (filter.status == status) null else status)) },
                        )
                    }
                }
            }
            if (!filter.isActive && filter.scope == BacklogScope.ALL && recentlyAdded.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.section_recent))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            items(recentlyAdded, key = { it.igdbId }) { game ->
                                GameCover(
                                    imageId = game.coverImageId,
                                    width = 112.dp,
                                    contentDescription = game.name,
                                    modifier = Modifier.clickable { onClick(game) },
                                )
                            }
                        }
                    }
                }
            }
            if (filter.isActive || filter.scope != BacklogScope.ALL) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ActiveFilterChips(filter = filter, onChange = onFilterChange)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                stringResource(R.string.section_my_games),
                trailing = { SortLabel(stringResource(R.string.sort_active_label, sort.label()), onOpenSheet) },
            )
        }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (searching) {
                    LibraryEmptyState(
                        message = stringResource(R.string.search_library_none, searchText),
                        actionLabel = stringResource(R.string.search_in_catalog),
                        onAction = onSearchCatalog,
                        fillScreen = false,
                    )
                } else {
                    LibraryEmptyState(
                        message = stringResource(R.string.backlog_filtered_empty),
                        actionLabel = stringResource(R.string.filter_reset),
                        onAction = { onFilterChange(BacklogFilter()) },
                        fillScreen = false,
                    )
                }
            }
        }
        items(games, key = { it.igdbId }) { game ->
            val supporting = listOfNotNull(
                ReleaseDateFormatting.format(game.firstReleaseDate),
                if (game.isArchived) stringResource(R.string.label_archived) else null,
            ).joinToString(" · ")
            val next = game.gameStatus.next()
            SwipeActionRow(
                startAction = next?.let {
                    SwipeAction(stringResource(R.string.swipe_next_status, it.label()), Icons.Filled.SkipNext, it.tint()) { onNextStatus(game) }
                },
                endAction = SwipeAction(
                    stringResource(if (game.isArchived) R.string.action_unarchive else R.string.swipe_archive),
                    if (game.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    Glass.Amber,
                ) { onArchiveToggle(game) },
            ) {
                GameListItem(
                    name = game.name,
                    coverImageId = game.coverImageId,
                    platforms = GameJsonCache.platformNames(game).map(FrenchLabels::platform),
                    meta = supporting,
                    rating = game.totalRating,
                    dimmed = game.isArchived,
                    statusLabel = game.gameStatus.label(),
                    statusTint = game.gameStatus.tint(),
                    statusChoices = GameStatus.entries.map { status ->
                        StatusChoice(status.label(), status.tint(), selected = status == game.gameStatus, onSelect = { onStatusChange(game, status) })
                    },
                    userRatingLabel = game.userRating?.let { "★ $it/10" },
                    onLongClick = { onLongClick(game) },
                    onLongClickLabel = stringResource(R.string.quick_actions_hint),
                    onClick = { onClick(game) },
                    trailing = {
                        val label = stringResource(if (game.isArchived) R.string.action_unarchive else R.string.action_archive)
                        IconButton(onClick = { onArchiveToggle(game) }) {
                            Icon(
                                if (game.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = label,
                                tint = Glass.TextMuted,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Active filters stay visible (and one tap removable) after the sheet closes. */
@Composable
internal fun ActiveFilterChips(filter: BacklogFilter, onChange: (BacklogFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (filter.scope) {
            BacklogScope.WISHLIST -> RemovableChip(stringResource(R.string.scope_wishlist)) { onChange(filter.copy(scope = BacklogScope.ALL)) }
            BacklogScope.OWNED -> RemovableChip(stringResource(R.string.scope_owned)) { onChange(filter.copy(scope = BacklogScope.ALL)) }
            BacklogScope.ALL -> Unit
        }
        filter.status?.let { RemovableChip(it.label()) { onChange(filter.copy(status = null)) } }
        filter.genre?.let { RemovableChip(FrenchLabels.genre(it)) { onChange(filter.copy(genre = null)) } }
        filter.platform?.let { RemovableChip(FrenchLabels.platform(it)) { onChange(filter.copy(platform = null)) } }
        if (filter.showArchived) {
            RemovableChip(stringResource(R.string.filter_show_archived)) { onChange(filter.copy(showArchived = false)) }
        }
    }
}

@Composable
internal fun BacklogSort.label(): String = stringResource(
    when (this) {
        BacklogSort.RECENTLY_ADDED -> R.string.sort_recently_added
        BacklogSort.NAME -> R.string.sort_name
        BacklogSort.RELEASE_DATE -> R.string.sort_release_date
        BacklogSort.RATING -> R.string.sort_rating
        BacklogSort.MY_RANKING -> R.string.sort_my_ranking
    },
)

@Composable
internal fun SearchResultsList(
    results: List<Game>,
    backlogIds: Set<Long>,
    onAdd: (Game, GameStatus?) -> Unit,
    onClick: (Game) -> Unit,
    extra: LazyListScope.() -> Unit = {},
) {
    // A fresh list state per first hit: new rows can never leave the list anchored below the tab's own results.
    val listState = remember(results.firstOrNull()?.id) { androidx.compose.foundation.lazy.LazyListState() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(results, key = { it.id }) { game ->
            GameListItem(
                name = game.name,
                coverImageId = game.cover?.imageId,
                platforms = game.platforms?.map { FrenchLabels.platform(it.name) } ?: emptyList(),
                meta = ReleaseDateFormatting.format(game.firstReleaseDate),
                onClick = { onClick(game) },
                trailing = {
                    if (backlogIds.contains(game.id)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.discover_already_in_backlog),
                            tint = Glass.Green,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        AddButton(
                            contentDescription = stringResource(R.string.action_add_to_backlog),
                            onAdd = { onAdd(game, null) },
                            choices = GameStatus.entries.map { status -> AddChoice(status.label()) { onAdd(game, status) } },
                        )
                    }
                },
            )
        }
        extra()
    }
}
