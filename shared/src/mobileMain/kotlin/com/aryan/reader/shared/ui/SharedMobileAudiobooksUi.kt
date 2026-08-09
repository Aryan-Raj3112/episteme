package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.SharedAudiobook
import com.aryan.reader.shared.SharedAudiobookPlaybackState
import com.aryan.reader.shared.SharedAudiobookSort
import com.aryan.reader.shared.SharedAudiobookStatus
import com.aryan.reader.shared.SharedBookTtsListenState
import com.aryan.reader.shared.SharedTtsListenItem
import com.aryan.reader.shared.SharedTtsListenCapabilities
import com.aryan.reader.shared.filterSharedAudiobooks
import com.aryan.reader.shared.formatSharedPlaybackTime
import com.aryan.reader.shared.formatSharedSleepTimerLabel
import com.aryan.reader.shared.matchesSharedAudiobookQuery
import com.aryan.reader.shared.progressFraction
import com.aryan.reader.shared.sharedAudiobookRemainingLabel
import com.aryan.reader.shared.sharedShouldAutoStartTtsListen
import com.aryan.reader.shared.sortSharedAudiobooks
import com.aryan.reader.shared.toSharedAudiobookLibraryItem
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileAudiobookAddSheet(
    onChooseFile: () -> Unit,
    onChooseMultiple: () -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
    onChooseTtsBook: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(readerString("listen_add", "Add"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                readerString("audiobooks_add_preview_desc", "Add an audiobook or listen to a library book with text-to-speech"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            onChooseTtsBook?.let { chooseTtsBook ->
                SharedMobileAudiobookAddChoice(
                    icon = Icons.Default.Book,
                    title = readerString("listen_choose_tts_book", "Choose a book for TTS"),
                    description = readerString("listen_choose_tts_book_desc", "Pick a supported book from your library and start listening"),
                    onClick = chooseTtsBook,
                )
            }
            SharedMobileAudiobookAddChoice(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = readerString("audiobooks_add_file", "Choose audiobook file"),
                description = readerString("audiobooks_add_file_desc", "Import one supported audiobook file"),
                onClick = onChooseFile,
            )
            SharedMobileAudiobookAddChoice(
                icon = Icons.Default.Folder,
                title = readerString("audiobooks_add_multiple", "Choose multiple files"),
                description = "Import several audiobook files at once",
                onClick = onChooseMultiple,
            )
            SharedMobileAudiobookAddChoice(
                icon = Icons.Default.Folder,
                title = readerString("audiobooks_add_folder", "Choose a folder"),
                description = "Import supported audio files from a folder",
                onClick = onChooseFolder,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SharedMobileAudiobookAddChoice(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(11.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileTtsBookPickerSheet(
    books: List<BookItem>,
    onBookSelected: (BookItem) -> Unit,
    onDismiss: () -> Unit,
    coverContent: @Composable (BookItem, Modifier) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visibleBooks = remember(books, query) {
        books.asSequence()
            .filter { it.type != com.aryan.reader.shared.FileType.AUDIOBOOK && SharedTtsListenCapabilities.supports(it.type) }
            .filter { book ->
                query.isBlank() || listOf(book.sharedListenTitle(), book.author, book.displayName).any { value ->
                    value?.contains(query, ignoreCase = true) == true
                }
            }
            .sortedBy { it.sharedListenTitle().lowercase() }
            .toList()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(readerString("listen_choose_tts_book", "Choose a book for TTS"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                readerString("listen_choose_tts_book_desc", "Pick a supported book from your library and start listening"),
                modifier = Modifier.padding(top = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                placeholder = { Text(readerString("listen_search_library_books", "Search library books")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = readerString("action_clear", "Clear"))
                        }
                    }
                },
                singleLine = true,
            )
            if (visibleBooks.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Text(readerString("listen_no_tts_books", "No supported books found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleBooks, key = BookItem::id) { book ->
                        Surface(
                            onClick = { onBookSelected(book) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                coverContent(book, Modifier.size(width = 52.dp, height = 78.dp).clip(RoundedCornerShape(10.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(book.sharedListenTitle(), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        book.author ?: readerString("unknown_author", "Unknown author"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(book.type.name, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = readerString("audiobooks_listen_with_tts", "Listen with TTS"), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun BookItem.sharedListenTitle(): String = title?.takeIf(String::isNotBlank)
    ?: displayName.substringBeforeLast('.').ifBlank { displayName }

@Composable
fun SharedMobileAudiobooksSection(
    audiobooks: List<SharedAudiobook>,
    playback: SharedAudiobookPlaybackState,
    ttsItems: List<SharedTtsListenItem>,
    ttsPlayback: SharedBookTtsListenState,
    onOpenPlayer: (SharedAudiobook) -> Unit,
    onOpenTtsPlayer: (SharedTtsListenItem, Boolean) -> Unit,
    onAddAudiobook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var source by remember { mutableStateOf(ListenSource.ALL) }
    var status by remember { mutableStateOf(SharedAudiobookStatus.ALL) }
    var sort by remember { mutableStateOf(SharedAudiobookSort.RECENTLY_LISTENED) }
    var query by remember { mutableStateOf("") }
    var sortExpanded by remember { mutableStateOf(false) }

    val visibleEntries = remember(audiobooks, ttsItems, source, status, sort, query) {
        val audioIds = filterSharedAudiobooks(
            audiobooks.map { it.toSharedAudiobookLibraryItem() },
            status,
        ).mapTo(mutableSetOf()) { it.id }
        val ttsIds = filterSharedAudiobooks(
            ttsItems.map { it.toSharedAudiobookLibraryItem() },
            status,
        ).mapTo(mutableSetOf()) { it.id }
        val audioEntries = audiobooks
            .filter { it.bookId in audioIds && it.matchesSharedAudiobookQuery(query) }
            .map { book ->
                ListenEntry(
                    id = book.bookId,
                    isTts = false,
                    audiobook = book,
                    tts = null,
                    progress = book.progressFraction,
                    addedAt = book.addedAt,
                    lastListenedAt = book.lastListenedAt,
                    title = book.title,
                    author = book.author,
                )
            }
        val ttsEntries = ttsItems
            .filter { item ->
                item.book.id in ttsIds &&
                    (item.title.contains(query, ignoreCase = true) || item.author.contains(query, ignoreCase = true))
            }
            .map { item ->
                ListenEntry(
                    id = item.book.id,
                    isTts = true,
                    audiobook = null,
                    tts = item,
                    progress = ((item.progress?.progressPercent ?: 0f) / 100f).coerceIn(0f, 1f),
                    addedAt = item.book.dateAddedTimestamp.takeIf { it > 0L } ?: item.book.timestamp,
                    lastListenedAt = item.progress?.updatedAt ?: 0L,
                    title = item.title,
                    author = item.author,
                )
            }
        val comparator = compareListenEntries(sort)
        when (source) {
            ListenSource.ALL -> (audioEntries + ttsEntries).sortedWith(comparator)
            ListenSource.AUDIOBOOKS -> audioEntries.sortedWith(comparator)
            ListenSource.TTS -> ttsEntries.sortedWith(comparator)
        }
    }

    val continueEntry = remember(visibleEntries, playback.bookId, ttsPlayback.bookId, query) {
        if (query.isNotBlank()) {
            null
        } else {
            visibleEntries.firstOrNull { !it.isTts && it.id == playback.bookId }
                ?: visibleEntries.firstOrNull { it.isTts && it.id == ttsPlayback.bookId && ttsPlayback.connected }
                ?: visibleEntries
                    .filter { it.progress in 0.001f..<1f }
                    .maxByOrNull { it.lastListenedAt }
        }
    }
    val regularEntries = remember(visibleEntries, continueEntry) {
        if (continueEntry == null) visibleEntries else visibleEntries.filterNot { it.id == continueEntry.id }
    }

    Column(modifier = modifier.fillMaxSize().testTag("AudiobooksLibrary")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListenSourceSwitcher(
                selected = source,
                onSelected = { source = it },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag("ListenSearch"),
                placeholder = { Text(readerString("listen_search", "Search audiobooks and TTS books")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = readerString("action_clear", "Clear"))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = readerQuantityString("listen_item_count", visibleEntries.size, "%1\$d item", "%1\$d items", visibleEntries.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ListenSortMenu(selected = sort, onSelected = { sort = it })
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SharedAudiobookStatus.entries, key = { it.name }) { option ->
                FilterChip(
                    selected = status == option,
                    onClick = { status = option },
                    label = { Text(readerString(option.stringKey, option.fallbackLabel)) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            continueEntry?.let { entry ->
                item(key = "continue-${entry.id}") {
                    if (entry.isTts) {
                        entry.tts?.let { item ->
                            SharedMobileTtsContinueCard(
                                item = item,
                                isActive = ttsPlayback.connected && item.book.id == ttsPlayback.bookId,
                                onOpen = { onOpenTtsPlayer(item, true) },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    } else {
                        entry.audiobook?.let { book ->
                            SharedMobileAudiobookContinueCard(
                                audiobook = book,
                                isActive = book.bookId == playback.bookId,
                                onOpen = { onOpenPlayer(book) },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
            if (regularEntries.isEmpty() && continueEntry == null) {
                item(key = "listen-empty") {
                    if (source == ListenSource.TTS) {
                        TtsLibraryEmptyState(query = query)
                    } else {
                        ListenLibraryEmptyState(query = query, onAdd = onAddAudiobook)
                    }
                }
            } else {
                items(regularEntries, key = { it.id }) { entry ->
                    if (entry.isTts) {
                        entry.tts?.let { item ->
                            SharedMobileTtsRow(
                                item = item,
                                isActive = ttsPlayback.connected && item.book.id == ttsPlayback.bookId,
                                isPlaying = ttsPlayback.connected && item.book.id == ttsPlayback.bookId && ttsPlayback.isPlaying,
                                onOpen = { onOpenTtsPlayer(item, sharedShouldAutoStartTtsListen(item.book.id, ttsPlayback)) },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    } else {
                        entry.audiobook?.let { book ->
                            SharedMobileAudiobookRow(
                                audiobook = book,
                                isActive = book.bookId == playback.bookId,
                                isPlaying = book.bookId == playback.bookId && playback.isPlaying,
                                onOpen = { onOpenPlayer(book) },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ListenSource { ALL, AUDIOBOOKS, TTS }

private data class ListenEntry(
    val id: String,
    val isTts: Boolean,
    val audiobook: SharedAudiobook?,
    val tts: SharedTtsListenItem?,
    val progress: Float,
    val addedAt: Long,
    val lastListenedAt: Long,
    val title: String,
    val author: String?,
)

private fun compareListenEntries(sort: SharedAudiobookSort): Comparator<ListenEntry> = when (sort) {
    SharedAudiobookSort.RECENTLY_LISTENED ->
        compareByDescending<ListenEntry> { it.lastListenedAt }.thenByDescending { it.addedAt }
    SharedAudiobookSort.RECENTLY_ADDED -> compareByDescending<ListenEntry> { it.addedAt }
    SharedAudiobookSort.TITLE -> compareBy<ListenEntry> { it.title.lowercase() }
    SharedAudiobookSort.AUTHOR ->
        compareBy<ListenEntry> { it.author?.lowercase().orEmpty() }.thenBy { it.title.lowercase() }
    SharedAudiobookSort.PROGRESS -> compareByDescending<ListenEntry> { it.progress }
}

@Composable
private fun ListenSourceSwitcher(
    selected: ListenSource,
    onSelected: (ListenSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ListenSource.entries.forEach { option ->
                val isSelected = option == selected
                val label = when (option) {
                    ListenSource.ALL -> readerString("unified_library_all", "All")
                    ListenSource.AUDIOBOOKS -> readerString("audiobooks_title", "Audiobooks")
                    ListenSource.TTS -> readerString("listen_source_tts", "TTS")
                }
                Surface(
                    onClick = { onSelected(option) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsLibraryEmptyState(query: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = if (query.isBlank()) {
                    readerString("listen_no_tts_books", "No supported books found")
                } else {
                    readerString("listen_no_matches", "No listening items match \"%1\$s\"", query)
                },
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (query.isBlank()) {
                Text(
                    text = readerString(
                        "listen_choose_tts_book_desc",
                        "Pick a supported book from your library and start listening",
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ttsChapterLabel(item: SharedTtsListenItem): String =
    item.progress?.let { readerString("listen_chapter_number", "Chapter %1\$d", it.chapterIndex + 1) }
        ?: if ((item.book.progressPercentage ?: 0f) > 0f) {
            "Continue from your reading position"
        } else {
            "Start listening"
        }

private const val TtsRemainingLabel = "Generated as you listen"

@Composable
private fun SharedMobileTtsBookCover(book: BookItem, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFF6D4C41), Color(0xFFD7A86E))
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (!book.coverImagePath.isNullOrBlank()) {
            LocalBookCoverImage(
                path = book.coverImagePath,
                contentDescription = book.displayName,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun TtsSourceBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = readerString("listen_badge_tts", "TTS"),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SharedMobileTtsRow(
    item: SharedTtsListenItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag("ListenLibraryRow-${item.book.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SharedMobileTtsBookCover(item.book, Modifier.size(width = 66.dp, height = 98.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TtsSourceBadge()
                    if (isActive) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(item.title, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    ttsChapterLabel(item),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progressFraction = ((item.progress?.progressPercent ?: 0f) / 100f).coerceIn(0f, 1f)
                if (progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(4.dp).clip(CircleShape),
                    )
                }
                Text(
                    text = when {
                        item.progress?.completed == true || progressFraction >= 1f ->
                            readerString("audiobooks_completed", "Completed")
                        progressFraction > 0f -> TtsRemainingLabel
                        else -> readerString("audiobooks_not_started", "Not started")
                    },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = if (isActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = readerString("content_desc_start_tts", "Play"),
                    modifier = Modifier.padding(11.dp),
                )
            }
        }
    }
}

@Composable
private fun SharedMobileTtsContinueCard(
    item: SharedTtsListenItem,
    isActive: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("TtsContinue"),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainer,
                        )
                    )
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SharedMobileTtsBookCover(item.book, Modifier.size(width = 92.dp, height = 132.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = readerString(
                        if (isActive) "audiobooks_now_playing" else "audiobooks_continue_listening",
                        if (isActive) "Now playing" else "Continue listening",
                    ).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    ttsChapterLabel(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { ((item.progress?.progressPercent ?: 0f) / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(CircleShape),
                )
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(((item.progress?.progressPercent ?: 0f) / 100f) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Text(
                        TtsRemainingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    imageVector = if (isActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun SharedMobileAudiobookContinueCard(
    audiobook: SharedAudiobook,
    isActive: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("AudiobookContinue"),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainer,
                        )
                    )
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SharedMobileAudiobookCover(audiobook, Modifier.size(112.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = readerString(
                        if (isActive) "audiobooks_now_playing" else "audiobooks_continue_listening",
                        if (isActive) "Now playing" else "Continue listening",
                    ).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(audiobook.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    audiobook.author?.takeIf { it.isNotBlank() } ?: readerString("audiobooks_unknown_author", "Unknown author"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    audiobook.format,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { audiobook.progressFraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(CircleShape),
                )
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(audiobook.progressFraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Text(
                        sharedAudiobookRemainingLabel(audiobook.durationMs, audiobook.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    imageVector = if (isActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
fun SharedMobileAudiobookRow(
    audiobook: SharedAudiobook,
    isActive: Boolean,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag("ListenLibraryRow-${audiobook.bookId}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SharedMobileAudiobookCover(audiobook, Modifier.size(width = 72.dp, height = 72.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListenSourceBadge()
                    if (isActive) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(audiobook.title, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    audiobook.author?.takeIf { it.isNotBlank() } ?: readerString("audiobooks_unknown_author", "Unknown author"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(audiobook.format, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (audiobook.progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { audiobook.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(4.dp).clip(CircleShape),
                    )
                }
                Text(
                    text = when {
                        audiobook.progressFraction >= 1f -> readerString("audiobooks_completed", "Completed")
                        audiobook.progressFraction > 0f -> sharedAudiobookRemainingLabel(audiobook.durationMs, audiobook.positionMs)
                        else -> readerString("audiobooks_not_started", "Not started")
                    },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = if (isActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                    contentDescription = readerString("content_desc_start_tts", "Play"),
                    modifier = Modifier.padding(11.dp),
                )
            }
        }
    }
}

@Composable
private fun ListenSourceBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = readerString("listen_badge_audiobook", "AUDIOBOOK"),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ListenLibraryEmptyState(query: String, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = if (query.isBlank()) {
                    readerString("listen_empty", "Nothing to listen to yet")
                } else {
                    readerString("listen_no_matches", "No listening items match \"%1\$s\"", query)
                },
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (query.isBlank()) {
                Text(
                    text = readerString("listen_empty_desc", "Import an audiobook or choose a library book to listen with text-to-speech."),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onAdd, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("listen_add", "Add"))
                }
            }
        }
    }
}

@Composable
private fun ListenSortMenu(selected: SharedAudiobookSort, onSelected: (SharedAudiobookSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(readerString(selected.stringKey, selected.fallbackLabel)) },
            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SharedAudiobookSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            readerString(option.stringKey, option.fallbackLabel),
                            fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SharedMobileAudiobookCover(audiobook: SharedAudiobook, modifier: Modifier = Modifier) {
    audiobook.coverPath?.takeIf(String::isNotBlank)?.let { path ->
        LocalBookCoverImage(
            path = path,
            contentDescription = audiobook.title,
            modifier = modifier.clip(RoundedCornerShape(18.dp)),
        )
        return
    }
    val colors = listOf(Color(0xFF6D4C41), Color(0xFFD7A86E))
    Box(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(colors)).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(34.dp), tint = Color.White.copy(alpha = 0.9f))
            Text(
                audiobook.title,
                modifier = Modifier.padding(top = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileAudiobookPlayerSheet(
    audiobook: SharedAudiobook,
    playback: SharedAudiobookPlaybackState,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimer: (Int?) -> Unit,
    onStopPlayback: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var draggedPosition by remember { mutableStateOf<Float?>(null) }

    val duration = playback.durationMs.takeIf { it > 0L } ?: audiobook.durationMs
    val displayedPosition = draggedPosition?.toLong() ?: playback.positionMs.takeIf { it >= 0L } ?: audiobook.positionMs
    val sleepTimerActive = playback.sleepTimerRemainingMs > 0L

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = readerString("audiobooks_collapse_player", "Collapse player"))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(readerString("audiobooks_now_playing", "Now playing").uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(audiobook.format, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options", "More options"))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(readerString("audiobooks_stop_playback", "Stop playback")) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onStopPlayback()
                                onDismiss()
                            },
                        )
                    }
                }
            }
            SharedMobileAudiobookCover(
                audiobook,
                Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .padding(top = 18.dp)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(20.dp)),
            )
            Text(audiobook.title, Modifier.padding(top = 22.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                audiobook.author?.takeIf { it.isNotBlank() } ?: readerString("audiobooks_unknown_author", "Unknown author"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = displayedPosition.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { draggedPosition = it },
                onValueChangeFinished = { draggedPosition?.toLong()?.let(onSeek); draggedPosition = null },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = duration > 0L,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatSharedPlaybackTime(displayedPosition), style = MaterialTheme.typography.labelSmall)
                Text("−${formatSharedPlaybackTime((duration - displayedPosition).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall)
            }
            playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSeek((displayedPosition - 30_000L).coerceAtLeast(0L)) }, enabled = playback.connected) {
                    Icon(FastRewindIcon, contentDescription = readerString("audiobooks_rewind_30_seconds", "Rewind 30 seconds"), modifier = Modifier.size(32.dp))
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
                    IconButton(onClick = onTogglePlayback, enabled = playback.connected, modifier = Modifier.size(76.dp)) {
                        AnimatedPlayPauseIcon(
                            isPlaying = playback.isPlaying,
                            contentDescription = readerString(
                                if (playback.isPlaying) "content_desc_pause_tts" else "content_desc_start_tts",
                                if (playback.isPlaying) "Pause" else "Play",
                            ),
                            modifier = Modifier.size(42.dp).testTag("SharedMobileAudiobookPlayPause"),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                IconButton(onClick = { onSeek(displayedPosition + 30_000L) }, enabled = playback.connected) {
                    Icon(FastForwardIcon, contentDescription = readerString("audiobooks_forward_30_seconds", "Forward 30 seconds"), modifier = Modifier.size(32.dp))
                }
            }
            val sleepTimerDockLabel = if (sleepTimerActive) {
                formatSharedSleepTimerLabel(playback.sleepTimerRemainingMs)
            } else {
                readerString("listen_timer", "Timer")
            }
            PlayerControlDock(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                PlayerDockAction(
                    icon = SpeedIcon,
                    label = audiobookSpeedLabel(playback.speed.takeIf { it > 0f } ?: 1f),
                    selected = false,
                    onClick = { showSpeedDialog = true },
                )
                PlayerDockAction(
                    icon = TimerIcon,
                    label = sleepTimerDockLabel,
                    selected = sleepTimerActive,
                    onClick = {
                        if (sleepTimerActive) {
                            onSleepTimer(null)
                        } else {
                            showSleepDialog = true
                        }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showSpeedDialog) {
        SharedMobileAudiobookSpeedDialog(
            currentSpeed = playback.speed.takeIf { it > 0f } ?: 1f,
            onSpeedSelected = onSpeedChange,
            onDismiss = { showSpeedDialog = false },
        )
    }
    if (showSleepDialog) {
        SharedMobileAudiobookSleepTimerDialog(
            onDurationSelected = { minutes ->
                onSleepTimer(minutes)
                showSleepDialog = false
            },
            onCancelSleep = {
                onSleepTimer(null)
                showSleepDialog = false
            },
            onDismiss = { showSleepDialog = false },
        )
    }
}

@Composable
fun SharedMobileAudiobookSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("audiobooks_playback_speed", "Playback speed")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                speeds.forEach { speed ->
                    val selected = abs(speed - currentSpeed) < 0.01f
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable {
                                onSpeedSelected(speed)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            audiobookSpeedLabel(speed),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Text(
                                readerString("audiobooks_speed_selected", "Selected"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        },
    )
}

@Composable
fun SharedMobileAudiobookSleepTimerDialog(
    onDurationSelected: (Int) -> Unit,
    onCancelSleep: () -> Unit,
    onDismiss: () -> Unit,
) {
    val durations = listOf(1, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("audiobooks_sleep_timer", "Sleep timer")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                durations.forEach { minutes ->
                    val label = when (minutes) {
                        60 -> readerString("audiobooks_one_hour", "1 hour")
                        1 -> readerString("audiobooks_one_minute", "1 minute")
                        else -> readerString("audiobooks_minutes", "%1\$d minutes", minutes)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onDurationSelected(minutes)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        },
    )
}

@Composable
fun SharedMobileAudiobookMiniPlayer(
    audiobook: SharedAudiobook,
    playback: SharedAudiobookPlaybackState,
    onTogglePlayback: () -> Unit,
    onExpand: () -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = if (playback.bookId == audiobook.bookId && playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else {
        audiobook.progressFraction
    }
    Surface(
        modifier = modifier
            .pointerInput(onExpand) {
                detectVerticalDragGestures(
                    onDragStart = { verticalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        verticalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (verticalDrag < -48f) onExpand()
                        verticalDrag = 0f
                    },
                )
            }
            .testTag("AudiobookMiniPlayer"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        onClick = onExpand,
    ) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SharedMobileAudiobookCover(audiobook, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(audiobook.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        audiobook.format,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = readerString(
                            if (playback.isPlaying) "content_desc_pause_tts" else "content_desc_start_tts",
                            if (playback.isPlaying) "Pause" else "Play",
                        ),
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options", "More options"))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(readerString("audiobooks_stop_playback", "Stop playback")) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onStopPlayback()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Platform-neutral mini-player frame. The cover remains a slot so Android can keep its exact
 * existing image loading/fallback behavior while the interaction and layout live in shared code.
 */
@Composable
fun SharedMobileAudiobookMiniPlayerFrame(
    title: String,
    subtitle: String,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onExpand: () -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
) {
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .pointerInput(onExpand) {
                detectVerticalDragGestures(
                    onDragStart = { verticalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        verticalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (verticalDrag < -48f) onExpand()
                        verticalDrag = 0f
                    },
                )
            }
            .testTag("AudiobookMiniPlayer"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        onClick = onExpand,
    ) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                cover()
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = readerString(
                            if (isPlaying) "content_desc_pause_tts" else "content_desc_start_tts",
                            if (isPlaying) "Pause" else "Play",
                        ),
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options", "More options"))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(readerString("audiobooks_stop_playback", "Stop playback")) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onStopPlayback()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color,
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(180), initialScale = 0.65f))
                .togetherWith(fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(140), targetScale = 0.65f))
        },
        label = "PlayPauseIcon",
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
    }
}

@Composable
private fun PlayerControlDock(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun RowScope.PlayerDockAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(19.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(21.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SharedAudiobookStatus.stringKey: String
    get() = when (this) {
        SharedAudiobookStatus.ALL -> "unified_library_all"
        SharedAudiobookStatus.IN_PROGRESS -> "audiobooks_in_progress"
        SharedAudiobookStatus.NOT_STARTED -> "audiobooks_not_started"
        SharedAudiobookStatus.COMPLETED -> "audiobooks_completed"
    }

private val SharedAudiobookStatus.fallbackLabel: String
    get() = when (this) {
        SharedAudiobookStatus.ALL -> "All"
        SharedAudiobookStatus.IN_PROGRESS -> "In progress"
        SharedAudiobookStatus.NOT_STARTED -> "Not started"
        SharedAudiobookStatus.COMPLETED -> "Completed"
    }

private val SharedAudiobookSort.stringKey: String
    get() = when (this) {
        SharedAudiobookSort.RECENTLY_LISTENED -> "listen_sort_recently_listened"
        SharedAudiobookSort.RECENTLY_ADDED -> "listen_sort_recently_added"
        SharedAudiobookSort.TITLE -> "listen_sort_title"
        SharedAudiobookSort.AUTHOR -> "listen_sort_author"
        SharedAudiobookSort.PROGRESS -> "listen_sort_progress"
    }

private val SharedAudiobookSort.fallbackLabel: String
    get() = when (this) {
        SharedAudiobookSort.RECENTLY_LISTENED -> "Recently listened"
        SharedAudiobookSort.RECENTLY_ADDED -> "Recently added"
        SharedAudiobookSort.TITLE -> "Title"
        SharedAudiobookSort.AUTHOR -> "Author"
        SharedAudiobookSort.PROGRESS -> "Progress"
    }

private fun audiobookSpeedLabel(speed: Float): String {
    val number = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "$number×"
}

private fun ImageVector.Builder.addIconPath(pathData: String) {
    val nodes = PathParser().parsePathString(pathData).toNodes()
    addPath(pathData = nodes, fill = SolidColor(Color.Black))
}

private val FastRewindIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FastRewind",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        addIconPath("M860,720L500,480L860,240L860,720ZM460,720L100,480L460,240L460,720ZM380,480L380,480L380,480ZM780,480L780,480L780,480ZM380,570L380,390L244,480L380,570ZM780,570L780,390L644,480L780,570Z")
    }.build()
}

private val FastForwardIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FastForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        addIconPath("M100,720L100,240L460,480L100,720ZM500,720L500,240L860,480L500,720ZM180,480L180,480L180,480ZM580,480L580,480L580,480ZM180,570L316,480L180,390L180,570ZM580,570L716,480L580,390L580,570Z")
    }.build()
}

private val SpeedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Speed",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        addIconPath(
            "M480,643.5Q518,643 536,616L760,280L424,504Q397,522 395.5,559Q394,596 418,620Q442,644 480,643.5ZM480,160Q539,160 593.5,176.5Q648,193 696,226L620,274Q587,257 551.5,248.5Q516,240 480,240Q347,240 253.5,333.5Q160,427 160,560Q160,602 171.5,643Q183,684 204,720L756,720Q779,682 789.5,641Q800,600 800,556Q800,520 791.5,486Q783,452 766,420L814,344Q844,391 861.5,444Q879,497 880,554Q881,611 867,663Q853,715 826,762Q815,780 796,790Q777,800 756,800L204,800Q183,800 164,790Q145,780 134,762Q108,717 94,666.5Q80,616 80,560Q80,477 111.5,404.5Q143,332 197.5,277.5Q252,223 325,191.5Q398,160 480,160ZM487,473L487,473Q487,473 487,473Q487,473 487,473Q487,473 487,473Q487,473 487,473Q487,473 487,473Q487,473 487,473L487,473L487,473L487,473Q487,473 487,473Q487,473 487,473Q487,473 487,473Q487,473 487,473Z"
        )
    }.build()
}

private val TimerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Timer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        addIconPath(
            "M360,120L360,40L600,40L600,120L360,120ZM440,560L520,560L520,320L440,320L440,560ZM340.5,851.5Q275,823 226,774Q177,725 148.5,659.5Q120,594 120,520Q120,446 148.5,380.5Q177,315 226,266Q275,217 340.5,188.5Q406,160 480,160Q542,160 599,180Q656,200 706,238L762,182L818,238L762,294Q800,344 820,401Q840,458 840,520Q840,594 811.5,659.5Q783,725 734,774Q685,823 619.5,851.5Q554,880 480,880Q406,880 340.5,851.5ZM678,718Q760,636 760,520Q760,404 678,322Q596,240 480,240Q364,240 282,322Q200,404 200,520Q200,636 282,718Q364,800 480,800Q596,800 678,718ZM480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Z"
        )
    }.build()
}

private enum class TtsPlayerPanel { COVER, CHAPTERS, TRANSCRIPT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileTtsPlayerSheet(
    item: SharedTtsListenItem,
    playback: SharedBookTtsListenState,
    chapterTitles: List<String>?,
    onTogglePlayback: () -> Unit,
    onSeekChunk: (Int) -> Unit,
    onSeekChapter: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimer: (Int?) -> Unit,
    onStopPlayback: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(TtsPlayerPanel.COVER) }
    val isActive = playback.connected && playback.bookId == item.book.id
    val sleepTimerActive = playback.sleepTimerRemainingMs > 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                ),
        ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = readerString("audiobooks_collapse_player", "Collapse player"))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = readerString("audiobooks_listening_with_tts", "Listening with TTS").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AnimatedContent(
                        targetState = if (isActive && !playback.chapterTitle.isNullOrBlank()) playback.chapterTitle else item.title,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                                (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 2 })
                        },
                        label = "TtsChapterTitle",
                    ) { title ->
                        Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options", "More options"))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(readerString("audiobooks_stop_playback", "Stop playback")) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            enabled = isActive,
                            onClick = {
                                showMenu = false
                                onStopPlayback()
                                onDismiss()
                            },
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = panel,
                    transitionSpec = {
                        val direction = targetState.ordinal - initialState.ordinal
                        val movingForward = direction > 0
                        (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { if (movingForward) it / 7 else -it / 7 }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { if (movingForward) -it / 7 else it / 7 })
                    },
                    label = "TtsPlayerPanel",
                ) { target ->
                    when (target) {
                        TtsPlayerPanel.COVER -> TtsCoverPanel(item)
                        TtsPlayerPanel.CHAPTERS -> TtsChaptersPanel(
                            playback = playback,
                            chapterTitles = chapterTitles,
                            onSeekChapter = { index ->
                                if (!isActive || index != playback.chapterIndex) onSeekChapter(index)
                                panel = TtsPlayerPanel.COVER
                            },
                        )
                        TtsPlayerPanel.TRANSCRIPT -> TtsTranscriptPanel(playback = playback)
                    }
                }
            }

            Text(item.title, modifier = Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val chunkLabel = if (isActive && playback.chunkIndex >= 0) {
                "Chunk ${playback.chunkIndex + 1}/${
                    playback.chunkCount.coerceAtLeast(playback.chunkIndex + 1)
                }"
            } else {
                readerString("listen_chapter_number", "Chapter %1\$d", playback.chapterIndex + 1)
            }
            LinearProgressIndicator(
                progress = { playback.progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(4.dp).clip(CircleShape),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(playback.progressPercent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Text(chunkLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSeekChapter(playback.chapterIndex - 1) },
                    enabled = isActive && playback.chapterIndex > 0,
                ) {
                    Icon(FastRewindIcon, contentDescription = readerString("audiobooks_previous_chapter", "Previous chapter"), modifier = Modifier.size(30.dp))
                }
                IconButton(
                    onClick = { onSeekChunk(playback.chunkIndex - 1) },
                    enabled = isActive && playback.chunkIndex > 0,
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = readerString("audiobooks_previous_passage", "Previous spoken passage"), modifier = Modifier.size(27.dp))
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
                    IconButton(onClick = onTogglePlayback, modifier = Modifier.size(76.dp)) {
                        AnimatedPlayPauseIcon(
                            isPlaying = isActive && playback.isPlaying,
                            contentDescription = readerString(
                                if (isActive && playback.isPlaying) "content_desc_pause_tts" else "content_desc_start_tts",
                                if (isActive && playback.isPlaying) "Pause" else "Play",
                            ),
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                IconButton(
                    onClick = { onSeekChunk(playback.chunkIndex + 1) },
                    enabled = isActive && playback.chunkCount > 0 && playback.chunkIndex in 0 until playback.chunkCount - 1,
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = readerString("audiobooks_next_passage", "Next spoken passage"), modifier = Modifier.size(27.dp))
                }
                IconButton(
                    onClick = { onSeekChapter(playback.chapterIndex + 1) },
                    enabled = isActive && playback.chapterCount > 0 && playback.chapterIndex < playback.chapterCount - 1,
                ) {
                    Icon(FastForwardIcon, contentDescription = readerString("audiobooks_next_chapter", "Next chapter"), modifier = Modifier.size(30.dp))
                }
            }

            PlayerControlDock(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp)) {
                PlayerDockAction(
                    icon = SpeedIcon,
                    label = audiobookSpeedLabel(playback.speechRate.takeIf { it > 0f } ?: 1f),
                    selected = false,
                    onClick = { showSpeedDialog = true },
                )
                PlayerDockAction(
                    icon = Icons.Default.Menu,
                    label = if (isActive) {
                        readerString("listen_chapter_number", "Chapter %1\$d", playback.chapterIndex + 1)
                    } else {
                        readerString("audiobooks_chapters", "Chapters")
                    },
                    selected = panel == TtsPlayerPanel.CHAPTERS,
                    onClick = { panel = if (panel == TtsPlayerPanel.CHAPTERS) TtsPlayerPanel.COVER else TtsPlayerPanel.CHAPTERS },
                )
                PlayerDockAction(
                    icon = Icons.Default.Book,
                    label = readerString("audiobooks_transcript", "Transcript"),
                    selected = panel == TtsPlayerPanel.TRANSCRIPT,
                    onClick = { panel = if (panel == TtsPlayerPanel.TRANSCRIPT) TtsPlayerPanel.COVER else TtsPlayerPanel.TRANSCRIPT },
                )
                PlayerDockAction(
                    icon = TimerIcon,
                    label = if (sleepTimerActive) {
                        formatSharedSleepTimerLabel(playback.sleepTimerRemainingMs)
                    } else {
                        readerString("listen_timer", "Timer")
                    },
                    selected = sleepTimerActive,
                    onClick = {
                        if (sleepTimerActive) {
                            onSleepTimer(null)
                        } else {
                            showSleepDialog = true
                        }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        }
    }

    if (showSpeedDialog) {
        SharedMobileAudiobookSpeedDialog(
            currentSpeed = playback.speechRate.takeIf { it > 0f } ?: 1f,
            onSpeedSelected = onSpeedChange,
            onDismiss = { showSpeedDialog = false },
        )
    }
    if (showSleepDialog) {
        SharedMobileAudiobookSleepTimerDialog(
            onDurationSelected = { minutes ->
                onSleepTimer(minutes)
                showSleepDialog = false
            },
            onCancelSleep = {
                onSleepTimer(null)
                showSleepDialog = false
            },
            onDismiss = { showSleepDialog = false },
        )
    }
}

@Composable
private fun TtsCoverPanel(item: SharedTtsListenItem, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .aspectRatio(0.72f)
                .shadow(elevation = 18.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF34495E), Color(0xFF6C5CE7))))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.book.coverImagePath.isNullOrBlank()) {
                LocalBookCoverImage(
                    path = item.book.coverImagePath,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(34.dp), tint = Color.White.copy(alpha = 0.9f))
                    Text(item.title, modifier = Modifier.padding(top = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TtsChaptersPanel(
    playback: SharedBookTtsListenState,
    chapterTitles: List<String>?,
    onSeekChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        chapterTitles == null && !playback.error.isNullOrBlank() -> {
            Box(modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    playback.error,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
        chapterTitles == null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    readerString("listen_chapters_loading", "Loading chapters..."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        chapterTitles.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    readerString("listen_no_readable_text", "This book contains no readable text"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize().testTag("AudiobookChapterList"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(chapterTitles, key = { index, _ -> index }) { index, title ->
                    val isCurrent = playback.connected && playback.chapterIndex == index
                    val containerColor by animateColorAsState(
                        if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        animationSpec = tween(220),
                        label = "ChapterContainerColor",
                    )
                    val scale by animateFloatAsState(
                        if (isCurrent) 1f else 0.985f,
                        animationSpec = tween(220),
                        label = "ChapterScale",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(16.dp))
                            .background(containerColor)
                            .clickable { onSeekChapter(index) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = title,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                        )
                        AnimatedContent(
                            targetState = isCurrent,
                            transitionSpec = {
                                (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f)) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f))
                            },
                            label = "SelectedChapterIcon",
                        ) { selected ->
                            if (selected) Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            else Spacer(Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsTranscriptPanel(playback: SharedBookTtsListenState, modifier: Modifier = Modifier) {
    val chunks = playback.transcriptChunks
    if (chunks.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Spoken text will appear when playback starts",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val currentLocalIndex = playback.chunkIndex - playback.transcriptStartIndex
    val listState = rememberLazyListState()
    LaunchedEffect(playback.chunkIndex, playback.transcriptStartIndex, chunks.size) {
        if (currentLocalIndex >= 0) {
            listState.animateSharedAudiobookScrollToCenter(currentLocalIndex.coerceAtMost(chunks.lastIndex))
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("AudiobookTranscript"),
        contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(chunks, key = { index, _ -> playback.transcriptStartIndex + index }) { index, chunkText ->
            val isCurrent = index == currentLocalIndex
            val containerColor by animateColorAsState(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(260),
                label = "TranscriptContainerColor",
            )
            val textColor by animateColorAsState(
                if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(260),
                label = "TranscriptTextColor",
            )
            val scale by animateFloatAsState(
                if (isCurrent) 1f else 0.985f,
                animationSpec = tween(260),
                label = "TranscriptItemScale",
            )
            Surface(
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                tonalElevation = if (isCurrent) 2.dp else 0.dp,
            ) {
                Text(
                    text = chunkText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                )
            }
        }
    }
}

private suspend fun androidx.compose.foundation.lazy.LazyListState.animateSharedAudiobookScrollToCenter(index: Int) {
    if (index < 0) return
    val visibleItems = layoutInfo.visibleItemsInfo
    val estimatedItemHeight = visibleItems.firstOrNull { it.index == index }?.size
        ?: visibleItems.takeIf { it.isNotEmpty() }?.map { it.size }?.average()?.toInt()
        ?: 0
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    animateScrollToItem(index, -((viewportHeight - estimatedItemHeight) / 2))
}

@Composable
fun SharedMobileTtsMiniPlayer(
    item: SharedTtsListenItem,
    playback: SharedBookTtsListenState,
    onTogglePlayback: () -> Unit,
    onExpand: () -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .pointerInput(onExpand) {
                detectVerticalDragGestures(
                    onDragStart = { verticalDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        verticalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (verticalDrag < -48f) onExpand()
                        verticalDrag = 0f
                    },
                )
            }
            .testTag("TtsMiniPlayer"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        onClick = onExpand,
    ) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { playback.progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SharedMobileTtsBookCover(item.book, Modifier.size(width = 36.dp, height = 48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = playback.chapterTitle?.takeIf { it.isNotBlank() }
                            ?: readerString("audiobooks_listening_with_tts", "Listening with TTS"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = readerString(
                            if (playback.isPlaying) "content_desc_pause_tts" else "content_desc_start_tts",
                            if (playback.isPlaying) "Pause" else "Play",
                        ),
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options", "More options"))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(readerString("audiobooks_stop_playback", "Stop playback")) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onStopPlayback()
                            },
                        )
                    }
                }
            }
        }
    }
}
