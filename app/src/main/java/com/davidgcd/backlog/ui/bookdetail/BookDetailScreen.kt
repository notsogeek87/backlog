package com.davidgcd.backlog.ui.bookdetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.local.readStatus
import com.davidgcd.backlog.data.repository.BookAddResult
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.model.BookDuplicates
import com.davidgcd.backlog.model.BookSource
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.ui.components.BookCover
import com.davidgcd.backlog.ui.components.GlassBadgeButton
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.GlassCard
import com.davidgcd.backlog.ui.components.GradientButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.components.label
import com.davidgcd.backlog.ui.components.tint
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.BookImage
import com.davidgcd.backlog.util.BookLanguages
import com.davidgcd.backlog.util.BookShareText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    onBack: () -> Unit,
    /** Opens another saved book (the one a duplicate points to). */
    onOpenBook: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val editions by viewModel.editions.collectAsState()
    val savedBooks by viewModel.savedBooks.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val openLabel = stringResource(R.string.action_open)

    val cover = when (val s = state) {
        is BookDetailState.Saved -> s.book.coverUrl
        is BookDetailState.Remote -> s.book.coverUrl
        else -> null
    }?.let { BookImage.sized(it, 'L') }

    /** Adds a book (or one of its editions) and says what happened; "Ouvrir" leads to the existing one. */
    val addBook: (Book) -> Unit = { book ->
        viewModel.add(book) { result ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                when (result) {
                    is BookAddResult.Added -> snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_book_added, book.title),
                        duration = SnackbarDuration.Short,
                    )
                    is BookAddResult.Duplicate -> {
                        val outcome = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.book_already_in_backlog),
                            actionLabel = openLabel,
                            duration = SnackbarDuration.Short,
                        )
                        if (outcome == SnackbarResult.ActionPerformed) onOpenBook(result.existingKey)
                    }
                }
            }
        }
    }

    var sharing by remember { mutableStateOf(false) }
    /** One book, one link: a public page of its own (status, favorite and note included when saved), else plain text. */
    val shareBook: () -> Unit = share@{
        val (book, saved) = when (val s = state) {
            is BookDetailState.Saved -> s.book.toBook() to s.book
            is BookDetailState.Remote -> s.book to null
            else -> return@share
        }
        scope.launch {
            sharing = true
            try {
                val link = viewModel.publishBookLink(book, saved)
                val text = when {
                    link != null -> "${book.title}\n$link"
                    saved != null -> BookShareText.line(saved)
                    else -> listOfNotNull(book.title, book.authorLine.ifEmpty { null }, book.catalogUrl).joinToString(" — ")
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_book_chooser)))
            } finally {
                sharing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The cover tints the screen: blurred, dimmed, faded into the night canvas (blur needs API 31+).
        if (cover != null) {
            Box(modifier = Modifier.fillMaxWidth().height(460.dp).clipToBounds()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.2f).blur(28.dp).alpha(0.5f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x26050A14), Glass.Bg))),
                )
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Glass.Text,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = glassTopAppBarColors(),
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (state is BookDetailState.Saved || state is BookDetailState.Remote) {
                            IconButton(onClick = shareBook, enabled = !sharing) {
                                if (sharing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Glass.Text)
                                } else {
                                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_book))
                                }
                            }
                        }
                        (state as? BookDetailState.Saved)?.book?.let { saved ->
                            IconButton(onClick = { viewModel.setFavorite(saved, !saved.isFavorite) }) {
                                Icon(
                                    if (saved.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(if (saved.isFavorite) R.string.book_favorite_remove else R.string.book_favorite_add),
                                    tint = if (saved.isFavorite) Glass.Pink else Glass.Text,
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (val current = state) {
                    is BookDetailState.Loading -> Centered { CircularProgressIndicator(color = Glass.Cyan) }
                    is BookDetailState.NotFound -> Centered { Text(stringResource(R.string.book_not_found)) }
                    is BookDetailState.Saved -> BookContent(
                        book = current.book.toBook(),
                        saved = current.book,
                        duplicateKey = null,
                        editions = editions,
                        savedBooks = savedBooks,
                        onStatusChange = { viewModel.setStatus(current.book, it) },
                        onRate = { viewModel.setUserRating(current.book, it) },
                        onRemove = { viewModel.remove(current.book, onDone = onBack) },
                        onAdd = addBook,
                        onOpenBook = onOpenBook,
                    )
                    is BookDetailState.Remote -> BookContent(
                        book = current.book,
                        saved = null,
                        duplicateKey = current.duplicateKey,
                        editions = editions,
                        savedBooks = savedBooks,
                        onStatusChange = {},
                        onRate = {},
                        onRemove = {},
                        onAdd = addBook,
                        onOpenBook = onOpenBook,
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookContent(
    book: Book,
    saved: BookEntity?,
    duplicateKey: String?,
    editions: List<Book>,
    savedBooks: List<Book>,
    onStatusChange: (ReadStatus) -> Unit,
    onRate: (Int) -> Unit,
    onRemove: () -> Unit,
    onAdd: (Book) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                BookCover(url = BookImage.sized(book.coverUrl, 'L'), title = book.title, modifier = Modifier.width(132.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Glass.Text,
                    )
                    book.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Glass.TextMuted) }
                    if (book.authors.isNotEmpty()) {
                        Text(book.authorLine, style = MaterialTheme.typography.titleSmall, color = Glass.Text.copy(alpha = 0.9f))
                    }
                    saved?.let { StarRating(rating = it.userRating ?: 0, onRate = onRate) }
                }
            }

            if (saved != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.status_title), style = MaterialTheme.typography.labelLarge, color = Glass.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadStatus.entries.forEach { option ->
                            GlassBadgeButton(option.label(), option.tint(), selected = option == saved.readStatus) { onStatusChange(option) }
                        }
                    }
                }
                // The next step of the reading, one tap: à lire → en cours → lu.
                when (saved.readStatus) {
                    ReadStatus.TO_READ -> GradientButton(
                        text = stringResource(R.string.book_start_reading),
                        onClick = { onStatusChange(ReadStatus.READING) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ReadStatus.READING -> GradientButton(
                        text = stringResource(R.string.book_mark_read),
                        onClick = { onStatusChange(ReadStatus.READ) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ReadStatus.ABANDONED -> GlassButton(
                        text = stringResource(R.string.book_resume_reading),
                        onClick = { onStatusChange(ReadStatus.READING) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ReadStatus.READ -> Unit
                }
            } else {
                if (duplicateKey != null) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.book_already_in_backlog),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onOpenBook(duplicateKey) }) { Text(stringResource(R.string.action_open)) }
                        }
                    }
                } else {
                    GradientButton(
                        text = stringResource(R.string.action_add_to_books),
                        onClick = { onAdd(book) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            book.description?.let { description ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.book_description_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Glass.Text.copy(alpha = 0.86f),
                        )
                    }
                }
            }

            InfoCard(book)

            EditionsCard(editions = editions, current = book, savedBooks = savedBooks, onAdd = onAdd)

            Column(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                book.catalogUrl?.let { url ->
                    GlassButton(
                        text = stringResource(if (book.source == BookSource.GOOGLE_BOOKS) R.string.action_open_google_books else R.string.action_open_open_library),
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (saved != null) {
                    GlassButton(
                        text = stringResource(R.string.action_remove_from_books),
                        onClick = { confirmRemove = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.remove_confirm_title)) },
            text = { Text(stringResource(R.string.remove_book_confirm_message, book.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Five stars; tapping the current rating again clears it. */
@Composable
private fun StarRating(rating: Int, onRate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { n ->
            IconButton(onClick = { onRate(n) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (n <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.book_rate_stars, n),
                    tint = if (n <= rating) Glass.Amber else Glass.TextMuted,
                )
            }
        }
    }
}

/** The facts about the book — a line only when the catalogue gave the value, so there is never an empty field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(book: Book) {
    val rows = buildList {
        if (book.authors.isNotEmpty()) {
            add(stringResource(if (book.authors.size > 1) R.string.book_info_authors else R.string.book_info_author) to book.authorLine)
        }
        book.publishedYear?.let { add(stringResource(R.string.book_info_published) to it.toString()) }
        book.publisher?.let { add(stringResource(R.string.book_info_publisher) to it) }
        book.pageCount?.let { add(stringResource(R.string.book_info_pages) to it.toString()) }
        val isbns = listOfNotNull(book.canonicalIsbn13, book.canonicalIsbn10)
        if (isbns.isNotEmpty()) add(stringResource(R.string.book_info_isbn) to isbns.joinToString("\n"))
        val languages = BookLanguages.labels(book.languages)
        if (languages.isNotEmpty()) add(stringResource(R.string.book_info_languages) to languages.joinToString(", "))
        if (book.subjects.isNotEmpty()) add(stringResource(R.string.book_info_genres) to book.subjects.take(8).joinToString(", "))
    }
    if (rows.isEmpty()) return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.book_info_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = Glass.Border)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = Glass.Text)
                }
            }
        }
    }
}

/**
 * The other physical editions of the same work (French, paperback, anniversary…). Each is added on its
 * own, as its own entry — an edition is never silently merged into, or mistaken for, another book.
 */
@Composable
private fun EditionsCard(editions: List<Book>, current: Book, savedBooks: List<Book>, onAdd: (Book) -> Unit) {
    // The edition on screen is not "another" one; with a single edition there is nothing to choose.
    val others = editions.filterNot { edition ->
        edition.key == current.key ||
            (current.editionId != null && edition.editionId == current.editionId) ||
            (current.canonicalIsbn13 != null && edition.canonicalIsbn13 == current.canonicalIsbn13)
    }
    if (others.isEmpty()) return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.book_editions_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.book_editions_hint), style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted)
            others.take(MAX_EDITIONS).forEach { edition ->
                val isSaved = BookDuplicates.find(edition, savedBooks) != null
                val line = listOfNotNull(
                    edition.publisher,
                    edition.publishedYear?.toString(),
                    BookLanguages.labels(edition.languages).firstOrNull(),
                    edition.pageCount?.let { stringResource(R.string.book_edition_pages, it) },
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(edition.title, style = MaterialTheme.typography.bodyMedium, color = Glass.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelMedium, color = Glass.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        edition.canonicalIsbn13?.let { Text("ISBN $it", style = MaterialTheme.typography.labelSmall, color = Glass.TextMuted) }
                    }
                    if (isSaved) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.books_already_saved), tint = Glass.Green, modifier = Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = { onAdd(edition) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_books), tint = Glass.Cyan)
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_EDITIONS = 10
