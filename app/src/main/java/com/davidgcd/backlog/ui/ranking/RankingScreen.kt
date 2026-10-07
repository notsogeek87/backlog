package com.davidgcd.backlog.ui.ranking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.davidgcd.backlog.R
import com.davidgcd.backlog.ui.components.BookCover
import com.davidgcd.backlog.ui.components.GameCover
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.LibraryEmptyState
import com.davidgcd.backlog.ui.components.MediaType
import com.davidgcd.backlog.ui.components.GlassPill
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.BookImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Le classement perso d'un média (jeux, films & séries, livres) : on tire une ligne par sa poignée pour la placer,
 * l'écran défile tout seul près des bords ; « tout en haut » / « tout en bas » (et « placer en position N » en
 * touchant le numéro) évitent de tirer sur de longues listes. Les mêmes déplacements sont exposés à TalkBack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    initialMedia: MediaType,
    viewModelFor: @Composable (MediaType) -> RankingViewModel,
    onBack: () -> Unit,
    onItemClick: (MediaType, String) -> Unit,
    onAddItems: (MediaType) -> Unit,
) {
    var media by rememberSaveable { mutableStateOf(initialMedia) }
    val viewModel = viewModelFor(media)
    val items by viewModel.items.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.ranking_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassPill(stringResource(R.string.media_games), selected = media == MediaType.GAMES, onClick = { media = MediaType.GAMES })
                GlassPill(stringResource(R.string.media_movies), selected = media == MediaType.MOVIES, onClick = { media = MediaType.MOVIES })
                GlassPill(stringResource(R.string.media_books), selected = media == MediaType.BOOKS, onClick = { media = MediaType.BOOKS })
            }
            if (items.isEmpty()) {
                LibraryEmptyState(
                    message = stringResource(
                        when (media) {
                            MediaType.GAMES -> R.string.ranking_empty
                            MediaType.MOVIES -> R.string.ranking_empty_movies
                            MediaType.BOOKS -> R.string.ranking_empty_books
                        },
                    ),
                    actionLabel = stringResource(
                        when (media) {
                            MediaType.GAMES -> R.string.backlog_empty_cta
                            MediaType.MOVIES -> R.string.movies_empty_cta
                            MediaType.BOOKS -> R.string.books_empty_cta
                        },
                    ),
                    onAction = { onAddItems(media) },
                )
            } else {
                RankingList(
                    items = items,
                    onMove = viewModel::moveTo,
                    onItemClick = { key -> onItemClick(media, key) },
                )
            }
        }
    }
}

@Composable
internal fun RankingList(items: List<RankItem>, onMove: (from: Int, to: Int) -> Unit, onItemClick: (String) -> Unit) {
    val listState = rememberLazyListState()
    val drag = remember(listState) { DragReorderState(listState) }
    val scope = rememberCoroutineScope()
    // The list on screen follows the finger live; Room is only written once, when the row is dropped.
    var local by remember { mutableStateOf(items) }
    var startIndex by remember { mutableStateOf(-1) }
    var placing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(items) { if (drag.draggingKey == null) local = items }

    // Near the top / bottom edge the list scrolls by itself, keeping the dragged row under the finger.
    LaunchedEffect(drag.draggingKey) {
        while (drag.draggingKey != null) {
            val direction = drag.edgeDirection()
            if (direction != 0) drag.scrolledBy(listState.scrollBy(direction * 22f))
            delay(16)
        }
    }

    placing?.let { index ->
        PlaceAtDialog(
            count = local.size,
            current = index + 1,
            onConfirm = { position ->
                placing = null
                onMove(index, (position - 1).coerceIn(0, local.lastIndex))
            },
            onDismiss = { placing = null },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = 0) {
            Text(
                stringResource(R.string.ranking_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.TextMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        itemsIndexed(local, key = { _, item -> item.key }) { index, item ->
            val dragging = drag.draggingKey == item.key
            RankRow(
                index = index,
                count = local.size,
                item = item,
                modifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = drag.offsetY }
                } else {
                    Modifier.animateItem()
                },
                dragging = dragging,
                onClick = { onItemClick(item.key) },
                onPlace = { placing = index },
                onMoveTop = { onMove(index, 0) },
                onMoveBottom = { onMove(index, local.lastIndex) },
                onMoveBy = { delta -> onMove(index, (index + delta).coerceIn(0, local.lastIndex)) },
                dragHandle = Modifier.pointerInput(item.key) {
                    detectDragGestures(
                        onDragStart = {
                            startIndex = local.indexOfFirst { it.key == item.key }
                            drag.start(item.key)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            drag.drag(amount.y) { draggedKey, targetKey ->
                                val from = local.indexOfFirst { it.key == draggedKey }
                                val to = local.indexOfFirst { it.key == targetKey }
                                if (from >= 0 && to >= 0) {
                                    local = local.toMutableList().also { list -> list.add(to, list.removeAt(from)) }
                                }
                            }
                        },
                        onDragEnd = {
                            val key = drag.draggingKey
                            val to = local.indexOfFirst { it.key == key }
                            val from = startIndex
                            drag.end()
                            if (from >= 0 && to >= 0 && from != to) scope.launch { onMove(from, to) }
                        },
                        onDragCancel = {
                            drag.end()
                            local = items
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun RankRow(
    index: Int,
    count: Int,
    item: RankItem,
    modifier: Modifier,
    dragging: Boolean,
    onClick: () -> Unit,
    onPlace: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onMoveBy: (Int) -> Unit,
    dragHandle: Modifier,
) {
    val up = stringResource(R.string.ranking_move_up)
    val down = stringResource(R.string.ranking_move_down)
    val top = stringResource(R.string.ranking_move_top)
    val bottom = stringResource(R.string.ranking_move_bottom)
    val a11yActions = buildList {
        if (index > 0) {
            add(CustomAccessibilityAction(top) { onMoveTop(); true })
            add(CustomAccessibilityAction(up) { onMoveBy(-1); true })
        }
        if (index < count - 1) {
            add(CustomAccessibilityAction(down) { onMoveBy(1); true })
            add(CustomAccessibilityAction(bottom) { onMoveBottom(); true })
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (dragging) Modifier.shadow(12.dp, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The number is a button: it opens « place at position… », the quick way across a long list.
        Text(
            "${index + 1}",
            modifier = Modifier
                .width(40.dp)
                .height(48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .clickable(onClickLabel = stringResource(R.string.ranking_place_at), onClick = onPlace)
                .padding(top = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (item.ranked) Glass.Cyan else Glass.TextMuted,
        )
        // The moves are custom actions of the card itself: it is the one node TalkBack focuses (its parts are merged into it).
        GlassCard(modifier = Modifier.weight(1f).semantics { customActions = a11yActions }, onClick = onClick) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (item.isBook) {
                    BookCover(url = BookImage.sized(item.coverUrl, 'M'), title = item.title, modifier = Modifier.width(44.dp))
                } else {
                    GameCover(item.coverImageId, width = 48.dp, imageUrl = item.coverUrl)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Glass.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                // « Tout en haut » / « tout en bas » stay one tap away: with hundreds of entries dragging is not enough.
                Column {
                    IconButton(modifier = Modifier.size(44.dp), enabled = index > 0, onClick = onMoveTop) {
                        Icon(Icons.Filled.KeyboardDoubleArrowUp, contentDescription = top)
                    }
                    IconButton(modifier = Modifier.size(44.dp), enabled = index < count - 1, onClick = onMoveBottom) {
                        Icon(Icons.Filled.KeyboardDoubleArrowDown, contentDescription = bottom)
                    }
                }
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.ranking_drag_handle),
                    tint = Glass.TextMuted,
                    modifier = Modifier.size(44.dp).padding(10.dp).then(dragHandle),
                )
            }
        }
    }
}

@Composable
private fun PlaceAtDialog(count: Int, current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val position = text.toIntOrNull()
    val valid = position != null && position in 1..count
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.ranking_place_at)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.ranking_place_at_hint, count)) },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { position?.let(onConfirm) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
