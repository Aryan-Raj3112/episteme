package com.aryan.reader

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.aryan.reader.audiobook.AudiobookController
import com.aryan.reader.audiobook.AudiobookPlaybackRequest
import com.aryan.reader.audiobook.BookTtsAudiobookController
import com.aryan.reader.audiobook.BookTtsContentRepository
import com.aryan.reader.audiobook.BookTtsListeningProgressEntity
import com.aryan.reader.audiobook.BookTtsSessionCoordinator
import com.aryan.reader.data.AudiobookEntity
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.tts.formatReaderTtsChunkLabel

internal enum class AudiobookUiStatus { ALL, IN_PROGRESS, NOT_STARTED, COMPLETED }

private enum class ListenSource {
    ALL,
    AUDIOBOOKS,
    TTS
}

private enum class ListenSort {
    RECENTLY_LISTENED,
    RECENTLY_ADDED,
    TITLE,
    AUTHOR,
    PROGRESS
}

private val ListenSource.labelRes: Int
    get() = when (this) {
        ListenSource.ALL -> R.string.unified_library_all
        ListenSource.AUDIOBOOKS -> R.string.audiobooks_title
        ListenSource.TTS -> R.string.listen_source_tts
    }

private val ListenSort.labelRes: Int
    get() = when (this) {
        ListenSort.RECENTLY_LISTENED ->
            R.string.listen_sort_recently_listened

        ListenSort.RECENTLY_ADDED ->
            R.string.listen_sort_recently_added

        ListenSort.TITLE ->
            R.string.listen_sort_title

        ListenSort.AUTHOR ->
            R.string.listen_sort_author

        ListenSort.PROGRESS ->
            R.string.listen_sort_progress
    }

private enum class TtsPlayerPanel {
    COVER,
    CHAPTERS,
    TRANSCRIPT
}

internal data class AudiobookUiItem(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String,
    val chapter: String,
    val progress: Float,
    val remaining: String,
    val isTts: Boolean = false,
    val playbackRequest: AudiobookPlaybackRequest? = null,
    val coverPath: String? = playbackRequest?.coverPath,
    val sourceBook: RecentFileItem? = null,
    val autoStart: Boolean = false,
    val addedAt: Long = 0L,
    val lastListenedAt: Long = 0L,
)

internal fun filterAudiobooks(items: List<AudiobookUiItem>, status: AudiobookUiStatus): List<AudiobookUiItem> =
    items.filter { item ->
        when (status) {
            AudiobookUiStatus.ALL -> true
            AudiobookUiStatus.IN_PROGRESS -> item.progress > 0f && item.progress < 1f
            AudiobookUiStatus.NOT_STARTED -> item.progress <= 0f
            AudiobookUiStatus.COMPLETED -> item.progress >= 1f
        }
    }

private fun AudiobookUiItem.matchesListenQuery(
    query: String
): Boolean {
    val normalized = query.trim()

    if (normalized.isBlank()) return true

    return listOf(
        title,
        author,
        narrator,
        chapter,
        playbackRequest?.album
    ).any { value ->
        value?.contains(normalized, ignoreCase = true) == true
    }
}

private fun sortListenItems(
    items: List<AudiobookUiItem>,
    sort: ListenSort
): List<AudiobookUiItem> {
    return when (sort) {
        ListenSort.RECENTLY_LISTENED -> {
            items.sortedWith(
                compareByDescending<AudiobookUiItem> {
                    it.lastListenedAt
                }.thenByDescending {
                    it.addedAt
                }
            )
        }

        ListenSort.RECENTLY_ADDED -> {
            items.sortedByDescending {
                it.addedAt
            }
        }

        ListenSort.TITLE -> {
            items.sortedBy {
                it.title.lowercase()
            }
        }

        ListenSort.AUTHOR -> {
            items.sortedWith(
                compareBy<AudiobookUiItem> {
                    it.author.lowercase()
                }.thenBy {
                    it.title.lowercase()
                }
            )
        }

        ListenSort.PROGRESS -> {
            items.sortedByDescending {
                it.progress
            }
        }
    }
}

internal fun RecentFileItem.toTtsAudiobookUiItem(
    progress: BookTtsListeningProgressEntity? = null
) = AudiobookUiItem(
    id = "tts-$bookId",
    title = title ?: displayName.substringBeforeLast('.'),
    author = author ?: "Unknown author",
    narrator = "Text-to-speech",
    chapter = progress?.let { "Chapter ${it.chapterIndex + 1}" }
        ?: if ((progressPercentage ?: 0f) > 0f) {
            "Continue from your reading position"
        } else {
            "Start listening"
        },
    progress = ((progress?.progressPercent ?: 0f) / 100f)
        .coerceIn(0f, 1f),
    remaining = "Generated as you listen",
    isTts = true,
    coverPath = coverImagePath,
    sourceBook = this,
    addedAt = dateAddedTimestamp.takeIf { it > 0L } ?: timestamp,
    lastListenedAt = progress?.updatedAt ?: 0L
)

internal fun AudiobookEntity.toUiItem(
    libraryItem: RecentFileItem? = null
): AudiobookUiItem {
    val progress = if (durationMs > 0L) {
        positionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }

    val minutes = (durationMs - positionMs)
        .coerceAtLeast(0L) / 60_000L

    val remaining = when {
        durationMs <= 0L -> "Duration unavailable"
        minutes >= 60L -> "${minutes / 60} hr ${minutes % 60} min"
        else -> "$minutes min"
    }

    val lastListenedAt = if (positionMs > 0L) {
        maxOf(
            libraryItem?.readingPositionModifiedTimestamp ?: 0L,
            libraryItem?.timestamp ?: 0L
        )
    } else {
        0L
    }

    return AudiobookUiItem(
        id = bookId,
        title = title,
        author = author ?: "Unknown author",
        narrator = narrator ?: author ?: "Unknown narrator",
        chapter = format,
        progress = progress.coerceIn(0f, 1f),
        remaining = remaining,
        playbackRequest = AudiobookPlaybackRequest(
            bookId = bookId,
            filePath = filePath,
            title = title,
            author = author,
            narrator = narrator,
            album = album,
            coverPath = coverPath,
            positionMs = positionMs,
            durationMs = durationMs,
            speed = playbackSpeed
        ),
        coverPath = coverPath,
        addedAt = addedAt,
        lastListenedAt = lastListenedAt
    )
}

@Composable
internal fun AudiobooksLibrarySection(
    modifier: Modifier,
    audiobooks: List<AudiobookEntity>,
    libraryBooks: List<RecentFileItem>,
    ttsProgress: List<BookTtsListeningProgressEntity>,
    activeItemId: String?,
    onAudiobookClick: (AudiobookUiItem) -> Unit,
    onListenWithTtsClick: (RecentFileItem) -> Unit,
    onAddAudiobookClick: () -> Unit,
) {
    var source by remember {
        mutableStateOf(ListenSource.ALL)
    }
    var status by remember {
        mutableStateOf(AudiobookUiStatus.ALL)
    }
    var sort by remember {
        mutableStateOf(ListenSort.RECENTLY_LISTENED)
    }
    var query by rememberSaveable {
        mutableStateOf("")
    }

    val libraryBooksById = remember(libraryBooks) {
        libraryBooks.associateBy {
            it.bookId
        }
    }

    val ttsProgressById = remember(ttsProgress) {
        ttsProgress.associateBy {
            it.bookId
        }
    }

    val importedItems = remember(
        audiobooks,
        libraryBooksById
    ) {
        audiobooks.map { audiobook ->
            audiobook.toUiItem(
                libraryItem = libraryBooksById[audiobook.bookId]
            )
        }
    }

    val ttsItems = remember(
        libraryBooks,
        ttsProgressById
    ) {
        libraryBooks
            .asSequence()
            .filter {
                it.type != FileType.AUDIOBOOK &&
                        BookTtsContentRepository.supports(it.type)
            }
            .map { book ->
                book.toTtsAudiobookUiItem(
                    ttsProgressById[book.bookId]
                )
            }
            .toList()
    }

    val allItems = remember(
        importedItems,
        ttsItems
    ) {
        importedItems + ttsItems
    }

    val sourceItems = remember(
        allItems,
        importedItems,
        ttsItems,
        source
    ) {
        when (source) {
            ListenSource.ALL -> allItems
            ListenSource.AUDIOBOOKS -> importedItems
            ListenSource.TTS -> ttsItems
        }
    }

    val visibleItems = remember(
        sourceItems,
        status,
        sort,
        query
    ) {
        sortListenItems(
            items = filterAudiobooks(
                items = sourceItems,
                status = status
            ).filter {
                it.matchesListenQuery(query)
            },
            sort = sort
        )
    }

    val continueItem = remember(
        visibleItems,
        activeItemId,
        query
    ) {
        if (query.isNotBlank()) {
            null
        } else {
            visibleItems.firstOrNull {
                it.id == activeItemId
            } ?: visibleItems
                .filter {
                    it.progress in 0.001f..<1f
                }
                .maxByOrNull {
                    it.lastListenedAt
                }
        }
    }

    val regularListItems = remember(
        visibleItems,
        continueItem
    ) {
        if (continueItem == null) {
            visibleItems
        } else {
            visibleItems.filterNot {
                it.id == continueItem.id
            }
        }
    }

    fun openItem(item: AudiobookUiItem) {
        if (item.isTts) {
            item.sourceBook?.let {
                onListenWithTtsClick(it)
            }
        } else {
            onAudiobookClick(item)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("AudiobooksLibrary")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ListenSourceSwitcher(
                selected = source,
                onSelected = {
                    source = it
                }
            )

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ListenSearch"),
                placeholder = {
                    Text(
                        stringResource(
                            R.string.listen_search
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(
                            onClick = {
                                query = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.action_clear
                                )
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.listen_item_count,
                        visibleItems.size,
                        visibleItems.size
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                ListenSortMenu(
                    selected = sort,
                    onSelected = {
                        sort = it
                    }
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 8.dp,
                end = 20.dp,
                bottom = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = AudiobookUiStatus.entries,
                key = {
                    it.name
                }
            ) { option ->
                FilterChip(
                    selected = status == option,
                    onClick = {
                        status = option
                    },
                    label = {
                        Text(
                            stringResource(
                                option.labelRes
                            )
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            continueItem?.let { item ->
                item(
                    key = "continue-${item.id}"
                ) {
                    AudiobookContinueCard(
                        item = item,
                        isActive = item.id == activeItemId,
                        onClick = {
                            openItem(
                                if (item.isTts) {
                                    item.copy(autoStart = true)
                                } else {
                                    item
                                }
                            )
                        },
                        modifier = Modifier.padding(
                            horizontal = 20.dp
                        )
                    )
                }
            }

            if (
                regularListItems.isEmpty() &&
                continueItem == null
            ) {
                item(
                    key = "listen-empty"
                ) {
                    ListenLibraryEmptyState(
                        query = query,
                        onAdd = onAddAudiobookClick
                    )
                }
            } else {
                items(
                    items = regularListItems,
                    key = {
                        it.id
                    }
                ) { item ->
                    ListenLibraryRow(
                        item = item,
                        isActive = item.id == activeItemId,
                        onClick = {
                            openItem(item)
                        },
                        modifier = Modifier.padding(
                            horizontal = 20.dp
                        )
                    )
                }
            }
        }
    }
}

private val AudiobookUiStatus.labelRes: Int get() = when (this) {
    AudiobookUiStatus.ALL -> R.string.unified_library_all
    AudiobookUiStatus.IN_PROGRESS -> R.string.audiobooks_in_progress
    AudiobookUiStatus.NOT_STARTED -> R.string.audiobooks_not_started
    AudiobookUiStatus.COMPLETED -> R.string.audiobooks_completed
}

@Composable
private fun AudiobookContinueCard(
    item: AudiobookUiItem,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("AudiobookContinue"),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (item.isTts) {
                MockAudioCover(
                    item = item,
                    modifier = Modifier.size(
                        width = 92.dp,
                        height = 132.dp
                    )
                )
            } else {
                MockAudioCover(
                    item = item,
                    modifier = Modifier.size(112.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isActive) {
                            R.string.audiobooks_now_playing
                        } else {
                            R.string.audiobooks_continue_listening
                        }
                    ).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = item.chapter,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(item.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )

                    Text(
                        text = item.remaining,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isActive) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable private fun MockAudioCover(item: AudiobookUiItem, modifier: Modifier) {
    val coverPath = item.coverPath
    if (coverPath != null) {
        AsyncImage(
            model = coverPath,
            contentDescription = item.title,
            modifier = modifier.clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop
        )
        return
    }
    val colors = if (item.isTts) listOf(Color(0xFF34495E), Color(0xFF6C5CE7)) else listOf(Color(0xFF6D4C41), Color(0xFFD7A86E))
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(colors)).padding(12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(34.dp), tint = Color.White.copy(alpha = .9f))
            Text(item.title, Modifier.padding(top = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudiobookAddSheet(
    onChooseFile: () -> Unit,
    onChooseMultiple: () -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
    onChooseTtsBook: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.listen_add),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                stringResource(
                    R.string.audiobooks_add_preview_desc
                ),
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            onChooseTtsBook?.let { chooseTtsBook ->
                AddChoice(
                    icon = Icons.Default.Book,
                    title = stringResource(
                        R.string.listen_choose_tts_book
                    ),
                    description = stringResource(
                        R.string.listen_choose_tts_book_desc
                    ),
                    onClick = chooseTtsBook
                )
            }

            AddChoice(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = stringResource(
                    R.string.audiobooks_add_file
                ),
                description = stringResource(
                    R.string.audiobooks_add_file_desc
                ),
                onClick = onChooseFile
            )

            AddChoice(
                icon = Icons.Default.Folder,
                title = stringResource(
                    R.string.audiobooks_add_multiple
                ),
                description =
                    "Import several audiobook files at once",
                onClick = onChooseMultiple
            )

            AddChoice(
                icon = Icons.Default.Folder,
                title = stringResource(
                    R.string.audiobooks_add_folder
                ),
                description =
                    "Import supported audio files from a folder",
                onClick = onChooseFolder
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsBookPickerSheet(
    books: List<RecentFileItem>,
    onBookSelected: (RecentFileItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var query by rememberSaveable {
        mutableStateOf("")
    }

    val visibleBooks = remember(
        books,
        query
    ) {
        books
            .asSequence()
            .filter {
                it.type != FileType.AUDIOBOOK &&
                        BookTtsContentRepository.supports(it.type)
            }
            .filter { book ->
                query.isBlank() ||
                        listOf(
                            book.cardTitle(),
                            book.author,
                            book.displayName
                        ).any { value ->
                            value?.contains(
                                query,
                                ignoreCase = true
                            ) == true
                        }
            }
            .sortedBy {
                it.cardTitle().lowercase()
            }
            .toList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(
                    R.string.listen_choose_tts_book
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(
                    R.string.listen_choose_tts_book_desc
                ),
                modifier = Modifier.padding(top = 3.dp),
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                placeholder = {
                    Text(
                        stringResource(
                            R.string.listen_search_library_books
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(
                            onClick = {
                                query = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription =
                                    stringResource(
                                        R.string.action_clear
                                    )
                            )
                        }
                    }
                },
                singleLine = true
            )

            if (visibleBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            R.string.listen_no_tts_books
                        ),
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        top = 14.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = visibleBooks,
                        key = {
                            it.bookId
                        }
                    ) { book ->
                        Surface(
                            onClick = {
                                onBookSelected(book)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color =
                                MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.spacedBy(14.dp)
                            ) {
                                ThemedBookCover(
                                    item = book,
                                    modifier = Modifier
                                        .size(
                                            width = 52.dp,
                                            height = 78.dp
                                        )
                                        .clip(
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentDescription =
                                        book.cardTitle()
                                )

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = book.cardTitle(),
                                        fontWeight =
                                            FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow =
                                            TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = book.cardAuthor(),
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow =
                                            TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = book.type.name,
                                        modifier =
                                            Modifier.padding(top = 4.dp),
                                        style =
                                            MaterialTheme.typography.labelSmall,
                                        color =
                                            MaterialTheme.colorScheme.primary
                                    )
                                }

                                Icon(
                                    imageVector =
                                        Icons.Default.PlayArrow,
                                    contentDescription =
                                        stringResource(
                                            R.string.audiobooks_listen_with_tts
                                        ),
                                    tint =
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AddChoice(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(11.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AudiobookPlayerSheet(item: AudiobookUiItem, onBeforePlay: () -> Unit, onDismiss: () -> Unit) {
    if (item.isTts) {
        BookTtsPlayerSheet(item, onDismiss)
        return
    }
    val context = LocalContext.current
    val controller = remember { AudiobookController(context) }
    val playback by controller.state.collectAsStateWithLifecycle()
    val sleepTimerLabel by controller.sleepTimerLabel.collectAsStateWithLifecycle()
    LaunchedEffect(item.id) { item.playbackRequest?.let(controller::connect) }
    DisposableEffect(controller) { onDispose(controller::release) }
    var draggedPosition by remember(item.id) { mutableStateOf<Float?>(null) }
    var showPlayerMenu by remember(item.id) {
        mutableStateOf(false)
    }
    val duration = playback.durationMs.takeIf { it > 0 } ?: item.playbackRequest?.durationMs ?: 0L
    val displayedPosition = draggedPosition?.toLong() ?: playback.positionMs
    var showSpeedDialog by remember(item.id) {
        mutableStateOf(false)
    }
    var showSleepTimerDialog by remember(item.id) {
        mutableStateOf(false)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f), MaterialTheme.colorScheme.surface))
            ).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(
                            R.string.audiobooks_collapse_player
                        )
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.audiobooks_now_playing)
                            .uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = item.chapter,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(onClick = { showPlayerMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                R.string.content_desc_more_options
                            )
                        )
                    }

                    DropdownMenu(
                        expanded = showPlayerMenu,
                        onDismissRequest = { showPlayerMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.audiobooks_stop_playback))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showPlayerMenu = false
                                controller.stop()
                                onDismiss()
                            }
                        )
                    }
                }
            }
            MockAudioCover(
                item = item,
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .padding(top = 18.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(20.dp)
                    )
            )
            Text(item.title, Modifier.padding(top = 22.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = displayedPosition.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { draggedPosition = it },
                onValueChangeFinished = { draggedPosition?.toLong()?.let(controller::seekTo); draggedPosition = null },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = duration > 0,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatPlayerTime(displayedPosition), style = MaterialTheme.typography.labelSmall); Text("−${formatPlayerTime((duration - displayedPosition).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall) }
            playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { controller.seekBy(-30_000) },
                    enabled = playback.connected
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fast_rewind),
                        contentDescription = stringResource(
                            R.string.audiobooks_rewind_30_seconds
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) { IconButton(onClick = { controller.togglePlay(onBeforePlay) }, enabled = playback.connected, modifier = Modifier.size(76.dp)) { Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playback.isPlaying) R.string.content_desc_pause_tts else R.string.content_desc_start_tts), Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimary) } }
                IconButton(
                    onClick = { controller.seekBy(30_000) },
                    enabled = playback.connected
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fast_forward),
                        contentDescription = stringResource(
                            R.string.audiobooks_forward_30_seconds
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            val sleepTimerActive = sleepTimerLabel != "Sleep"

            val sleepTimerDockLabel = if (sleepTimerActive) {
                sleepTimerLabel
            } else {
                stringResource(R.string.listen_timer)
            }

            PlayerControlDock(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                PlayerDockAction(
                    iconRes = R.drawable.speed,
                    label = audiobookSpeedLabel(playback.speed),
                    selected = false,
                    onClick = {
                        showSpeedDialog = true
                    }
                )

                PlayerDockAction(
                    iconRes = R.drawable.timer,
                    label = sleepTimerDockLabel,
                    selected = sleepTimerActive,
                    onClick = {
                        if (sleepTimerActive) {
                            controller.toggleSleepTimer()
                        } else {
                            showSleepTimerDialog = true
                        }
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (showSpeedDialog) {
        AudiobookSpeedDialog(
            currentSpeed = playback.speed,
            onSpeedSelected = controller::setSpeed,
            onDismiss = {
                showSpeedDialog = false
            }
        )
    }
    if (showSleepTimerDialog) {
        AudiobookSleepTimerDialog(
            onDurationSelected = { minutes ->
                controller.toggleSleepTimer(minutes)
            },
            onDismiss = {
                showSleepTimerDialog = false
            }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookTtsPlayerSheet(item: AudiobookUiItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sourceBook = item.sourceBook ?: return
    val controller = remember(sourceBook.bookId) { BookTtsAudiobookController(context) }
    val prepared by controller.uiState.collectAsStateWithLifecycle()
    val playback by controller.sharedPlaybackState.collectAsStateWithLifecycle()
    var showPlayerMenu by remember(sourceBook.bookId) {
        mutableStateOf(false)
    }
    var activePanel by rememberSaveable(sourceBook.bookId) {
        mutableStateOf(TtsPlayerPanel.COVER)
    }
    val sleepTimerLabel by controller.sleepTimerLabel
        .collectAsStateWithLifecycle()
    var showSpeedDialog by remember(sourceBook.bookId) {
        mutableStateOf(false)
    }
    var showSleepTimerDialog by remember(sourceBook.bookId) {
        mutableStateOf(false)
    }
    val transcriptListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rate by remember { mutableFloatStateOf(prepared.savedProgress?.speechRate ?: loadTtsSpeechRate(context)) }
    var pitch by remember { mutableFloatStateOf(prepared.savedProgress?.pitch ?: loadTtsPitch(context)) }
    val isThisBookActive = playback.connected && playback.bookId == sourceBook.bookId
    val book = prepared.book
    val currentChapter = if (isThisBookActive) playback.chapterIndex else prepared.savedProgress?.chapterIndex
    val progress = if (isThisBookActive) {
        calculateTtsAudiobookProgress(
            chapterIndex = currentChapter ?: 0,
            chapterCount = book?.chapters?.size ?: 0,
            chunkIndex = playback.chunkIndex,
            chunkCount = playback.chunkCount
        )
    } else item.progress
    val currentChapterTitle = currentChapter?.let { book?.chapters?.getOrNull(it)?.title }
        ?: item.chapter

    LaunchedEffect(sourceBook.bookId) {
        controller.connect(sourceBook.bookId)
        if (item.autoStart) controller.start(sourceBook.bookId, BookTtsSessionCoordinator.START_RESUME)
    }
    LaunchedEffect(prepared.savedProgress?.updatedAt) {
        prepared.savedProgress?.let {
            rate = it.speechRate
            pitch = it.pitch
        }
    }
    LaunchedEffect(
        playback.chunkIndex,
        playback.transcriptStartIndex,
        playback.transcriptChunks.size,
        activePanel
    ) {
        if (
            activePanel == TtsPlayerPanel.TRANSCRIPT &&
            playback.transcriptChunks.isNotEmpty()
        ) {
            val localIndex =
                (
                        playback.chunkIndex -
                                playback.transcriptStartIndex
                        )
                    .coerceIn(
                        playback.transcriptChunks.indices
                    )

            transcriptListState.animateScrollToCenter(localIndex)
        }
    }
    DisposableEffect(controller) { onDispose(controller::release) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(
                                R.string.audiobooks_collapse_player
                            )
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(
                                R.string.audiobooks_listening_with_tts
                            ).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        AnimatedContent(
                            targetState = currentChapterTitle,
                            transitionSpec = {
                                (
                                        fadeIn(tween(220)) +
                                                slideInVertically(
                                                    animationSpec = tween(260),
                                                    initialOffsetY = { it / 2 }
                                                )
                                        ) togetherWith (
                                        fadeOut(tween(150)) +
                                                slideOutVertically(
                                                    animationSpec = tween(200),
                                                    targetOffsetY = { -it / 2 }
                                                )
                                        )
                            },
                            label = "TtsChapterTitle"
                        ) { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showPlayerMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(
                                    R.string.content_desc_more_options
                                )
                            )
                        }

                        DropdownMenu(
                            expanded = showPlayerMenu,
                            onDismissRequest = { showPlayerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.audiobooks_stop_playback))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null
                                    )
                                },
                                enabled = isThisBookActive,
                                onClick = {
                                    showPlayerMenu = false
                                    controller.stop()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = activePanel,
                        transitionSpec = {
                            val movingForward =
                                targetState.ordinal > initialState.ordinal

                            val enterOffset: (Int) -> Int = { width ->
                                if (movingForward) width / 7 else -width / 7
                            }

                            val exitOffset: (Int) -> Int = { width ->
                                if (movingForward) -width / 7 else width / 7
                            }

                            (
                                    fadeIn(
                                        animationSpec = tween(durationMillis = 220)
                                    ) + slideInHorizontally(
                                        animationSpec = tween(durationMillis = 300),
                                        initialOffsetX = enterOffset
                                    )
                                    ) togetherWith (
                                    fadeOut(
                                        animationSpec = tween(durationMillis = 160)
                                    ) + slideOutHorizontally(
                                        animationSpec = tween(durationMillis = 240),
                                        targetOffsetX = exitOffset
                                    )
                                    )
                        },
                        label = "TtsPlayerPanel"
                    ) { panel ->
                        when (panel) {
                            TtsPlayerPanel.CHAPTERS -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("AudiobookChapterList"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(
                                        top = 8.dp,
                                        bottom = 24.dp
                                    )
                                ) {
                                    items(
                                        items = book?.chapters.orEmpty(),
                                        key = { chapter -> chapter.id }
                                    ) { chapter ->
                                        val selected =
                                            chapter.index == currentChapter

                                        ChapterSelectionRow(
                                            chapterNumber = chapter.index + 1,
                                            title = chapter.title,
                                            selected = selected,
                                            onClick = {
                                                if (selected && isThisBookActive) {
                                                    activePanel = TtsPlayerPanel.COVER
                                                } else {
                                                    if (isThisBookActive) {
                                                        controller.selectChapter(
                                                            chapter.index
                                                        )
                                                    } else {
                                                        controller.start(
                                                            sourceBook.bookId,
                                                            BookTtsSessionCoordinator.START_CHAPTER,
                                                            chapter.index
                                                        )
                                                    }

                                                    activePanel = TtsPlayerPanel.COVER
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            TtsPlayerPanel.TRANSCRIPT -> {
                                AudiobookTranscript(
                                    chunks = playback.transcriptChunks,
                                    startIndex = playback.transcriptStartIndex,
                                    currentIndex = playback.chunkIndex,
                                    listState = transcriptListState,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            TtsPlayerPanel.COVER -> {
                                MockAudioCover(
                                    item = item,
                                    modifier = Modifier
                                        .fillMaxHeight(0.94f)
                                        .aspectRatio(0.72f)
                                        .shadow(
                                            elevation = 18.dp,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                )
                            }
                        }
                    }
                }
                Text(
                    item.title,
                    Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(item.author, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (isThisBookActive && playback.chunkIndex >= 0) {
                                formatReaderTtsChunkLabel(playback.chunkIndex, playback.chunkCount).orEmpty()
                            } else "Chapter ${(currentChapter ?: 0) + 1}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Outer left: previous chapter.
                    IconButton(
                        onClick = controller::previousChapter,
                        enabled = isThisBookActive && (currentChapter ?: 0) > 0
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.fast_rewind),
                            contentDescription = stringResource(
                                R.string.audiobooks_previous_chapter
                            ),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Inner left: previous generated passage/chunk.
                    IconButton(
                        onClick = controller::previousChunk,
                        enabled = isThisBookActive && playback.chunkIndex > 0
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = stringResource(
                                R.string.audiobooks_previous_passage
                            ),
                            modifier = Modifier.size(27.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp
                    ) {
                        IconButton(
                            onClick = {
                                if (isThisBookActive) {
                                    controller.togglePlay()
                                } else {
                                    controller.start(
                                        sourceBook.bookId,
                                        BookTtsSessionCoordinator.START_RESUME
                                    )
                                }
                            },
                            modifier = Modifier.size(76.dp)
                        ) {
                            AnimatedPlayPauseIcon(
                                isPlaying = playback.isPlaying,
                                contentDescription = stringResource(
                                    if (playback.isPlaying) {
                                        R.string.content_desc_pause_tts
                                    } else {
                                        R.string.content_desc_start_tts
                                    }
                                ),
                                modifier = Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Inner right: next generated passage/chunk.
                    IconButton(
                        onClick = controller::nextChunk,
                        enabled = isThisBookActive &&
                                playback.chunkIndex < playback.chunkCount - 1
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = stringResource(
                                R.string.audiobooks_next_passage
                            ),
                            modifier = Modifier.size(27.dp)
                        )
                    }

                    // Outer right: next chapter.
                    IconButton(
                        onClick = controller::nextChapter,
                        enabled = isThisBookActive &&
                                (currentChapter ?: 0) < (book?.chapters?.lastIndex ?: 0)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.fast_forward),
                            contentDescription = stringResource(
                                R.string.audiobooks_next_chapter
                            ),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                val sleepTimerActive = sleepTimerLabel != "Sleep"

                val timerDockLabel = if (sleepTimerActive) {
                    sleepTimerLabel
                } else {
                    stringResource(R.string.listen_timer)
                }

                val chapterDockLabel = if (currentChapter != null) {
                    stringResource(
                        R.string.listen_chapter_number,
                        currentChapter + 1
                    )
                } else {
                    stringResource(R.string.audiobooks_chapters)
                }

                PlayerControlDock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 14.dp,
                            bottom = 10.dp
                        )
                ) {
                    PlayerDockAction(
                        iconRes = R.drawable.speed,
                        label = audiobookSpeedLabel(rate),
                        selected = false,
                        onClick = {
                            showSpeedDialog = true
                        }
                    )

                    PlayerDockAction(
                        iconRes = R.drawable.menu,
                        label = chapterDockLabel,
                        selected = activePanel == TtsPlayerPanel.CHAPTERS,
                        onClick = {
                            activePanel = if (
                                activePanel == TtsPlayerPanel.CHAPTERS
                            ) {
                                TtsPlayerPanel.COVER
                            } else {
                                TtsPlayerPanel.CHAPTERS
                            }
                        }
                    )

                    PlayerDockAction(
                        iconRes = R.drawable.book,
                        label = stringResource(
                            R.string.audiobooks_transcript
                        ),
                        selected = activePanel == TtsPlayerPanel.TRANSCRIPT,
                        onClick = {
                            activePanel = if (
                                activePanel == TtsPlayerPanel.TRANSCRIPT
                            ) {
                                TtsPlayerPanel.COVER
                            } else {
                                TtsPlayerPanel.TRANSCRIPT
                            }
                        }
                    )

                    PlayerDockAction(
                        iconRes = R.drawable.timer,
                        label = timerDockLabel,
                        selected = sleepTimerActive,
                        onClick = {
                            if (sleepTimerActive) {
                                controller.startSleepTimer(0)
                            } else {
                                showSleepTimerDialog = true
                            }
                        }
                    )
                }
            }
        }
    }
    if (showSpeedDialog) {
        AudiobookSpeedDialog(
            currentSpeed = rate,
            onSpeedSelected = { selectedSpeed ->
                rate = selectedSpeed
                controller.setParameters(
                    rate = selectedSpeed,
                    pitch = pitch
                )
            },
            onDismiss = {
                showSpeedDialog = false
            }
        )
    }
    if (showSleepTimerDialog) {
        AudiobookSleepTimerDialog(
            onDurationSelected = controller::startSleepTimer,
            onDismiss = {
                showSleepTimerDialog = false
            }
        )
    }
}

internal fun calculateTtsAudiobookProgress(
    chapterIndex: Int,
    chapterCount: Int,
    chunkIndex: Int,
    chunkCount: Int
): Float {
    if (chapterCount <= 0) return 0f
    val chapterFraction = if (chunkCount > 0) (chunkIndex.coerceAtLeast(0) + 1f) / chunkCount else 0f
    return ((chapterIndex.coerceIn(0, chapterCount - 1) + chapterFraction) / chapterCount).coerceIn(0f, 1f)
}

@Composable
private fun AudiobookTranscript(
    chunks: List<String>,
    startIndex: Int,
    currentIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    if (chunks.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Spoken text will appear when playback starts",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.testTag("AudiobookTranscript"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            top = 18.dp,
            bottom = 18.dp
        )
    ) {
        itemsIndexed(
            items = chunks,
            key = { localIndex, _ ->
                startIndex + localIndex
            }
        ) { localIndex, text ->
            val absoluteIndex = startIndex + localIndex
            val selected = absoluteIndex == currentIndex

            val containerColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = tween(durationMillis = 260),
                label = "TranscriptContainerColor"
            )

            val textColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(durationMillis = 260),
                label = "TranscriptTextColor"
            )

            val scale by animateFloatAsState(
                targetValue = if (selected) {
                    1f
                } else {
                    0.985f
                },
                animationSpec = tween(durationMillis = 260),
                label = "TranscriptItemScale"
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                tonalElevation = if (selected) 2.dp else 0.dp
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun ChapterSelectionRow(
    chapterNumber: Int,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 220),
        label = "ChapterContainerColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = tween(durationMillis = 220),
        label = "ChapterScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapterNumber.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }
        )

        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (
                        fadeIn(tween(180)) +
                                scaleIn(
                                    animationSpec = tween(180),
                                    initialScale = 0.6f
                                )
                        ) togetherWith (
                        fadeOut(tween(120)) +
                                scaleOut(
                                    animationSpec = tween(120),
                                    targetScale = 0.6f
                                )
                        )
            },
            label = "SelectedChapterIcon"
        ) { isSelected ->
            if (isSelected) {
                Icon(
                    imageVector =
                        Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        }
    }
}

private suspend fun androidx.compose.foundation.lazy.LazyListState
        .animateScrollToCenter(index: Int) {
    if (index < 0) return

    val layout = layoutInfo
    val visibleItems = layout.visibleItemsInfo

    val targetItem = visibleItems.firstOrNull {
        it.index == index
    }

    val estimatedItemHeight = targetItem?.size
        ?: visibleItems
            .takeIf { it.isNotEmpty() }
            ?.map { it.size }
            ?.average()
            ?.toInt()
        ?: 0

    val viewportHeight =
        layout.viewportEndOffset - layout.viewportStartOffset

    val centeredOffset = -(
            (viewportHeight - estimatedItemHeight) / 2
            )

    animateScrollToItem(
        index = index,
        scrollOffset = centeredOffset
    )
}

@Composable
internal fun AudiobookMiniPlayer(
    item: AudiobookUiItem,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onStop: () -> Unit
) {
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var showMenu by remember {
        mutableStateOf(false)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
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
                    }
                )
            }
            .testTag("AudiobookMiniPlayer"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        onClick = onExpand
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = {
                    progress.coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MockAudioCover(
                    item = item,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(11.dp))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.chapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (isPlaying) {
                                R.string.content_desc_pause_tts
                            } else {
                                R.string.content_desc_start_tts
                            }
                        )
                    )
                }

                Box {
                    IconButton(onClick = {
                        showMenu = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                R.string.content_desc_more_options
                            )
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = {
                            showMenu = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        R.string.audiobooks_stop_playback
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onStop()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
private fun AudiobookSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(
        0.75f,
        1f,
        1.25f,
        1.5f,
        1.75f,
        2f
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.audiobooks_playback_speed))
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = speeds,
                    key = { it }
                ) { speed ->
                    val selected = kotlin.math.abs(
                        speed - currentSpeed
                    ) < 0.01f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable {
                                onSpeedSelected(speed)
                                onDismiss()
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 13.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = audiobookSpeedLabel(speed),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        if (selected) {
                            Text(
                                text = stringResource(
                                    R.string.audiobooks_speed_selected
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun audiobookSpeedLabel(speed: Float): String {
    val number = if (speed % 1f == 0f) {
        speed.toInt().toString()
    } else {
        speed.toString()
    }

    return "${number}×"
}

@Composable
private fun AudiobookSleepTimerDialog(
    onDurationSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val durations = listOf(
        1,
        15,
        30,
        45,
        60
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.audiobooks_sleep_timer))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                durations.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onDurationSelected(minutes)
                                onDismiss()
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 14.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (minutes) {
                                1 -> stringResource(R.string.audiobooks_one_minute)
                                60 -> stringResource(R.string.audiobooks_one_hour)
                                else -> stringResource(
                                    R.string.audiobooks_minutes,
                                    minutes
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (
                    fadeIn(tween(150)) +
                            scaleIn(
                                animationSpec = tween(180),
                                initialScale = 0.65f
                            )
                    ) togetherWith (
                    fadeOut(tween(100)) +
                            scaleOut(
                                animationSpec = tween(140),
                                targetScale = 0.65f
                            )
                    )
        },
        label = "PlayPauseIcon"
    ) { playing ->
        Icon(
            imageVector = if (playing) {
                Icons.Default.Pause
            } else {
                Icons.Default.PlayArrow
            },
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

@Composable
private fun ListenSourceSwitcher(
    selected: ListenSource,
    onSelected: (ListenSource) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ListenSource.entries.forEach { option ->
                val isSelected = selected == option

                Surface(
                    onClick = {
                        onSelected(option)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 11.dp
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenSortMenu(
    selected: ListenSort,
    onSelected: (ListenSort) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box {
        AssistChip(
            onClick = {
                expanded = true
            },
            label = {
                Text(
                    stringResource(selected.labelRes)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            ListenSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(option.labelRes),
                            fontWeight = if (option == selected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ListenLibraryRow(
    item: AudiobookUiItem,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("ListenLibraryRow-${item.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val coverModifier = Modifier
                .size(
                    width = if (item.isTts) 66.dp else 72.dp,
                    height = if (item.isTts) 98.dp else 72.dp
                )
                .clip(RoundedCornerShape(14.dp))

            if (
                item.isTts &&
                item.sourceBook != null
            ) {
                ThemedBookCover(
                    item = item.sourceBook,
                    modifier = coverModifier,
                    contentDescription = item.title
                )
            } else {
                MockAudioCover(
                    item = item,
                    modifier = coverModifier
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ListenSourceBadge(
                        isTts = item.isTts
                    )

                    if (isActive) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = item.title,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = item.chapter,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = {
                            item.progress.coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                }

                Text(
                    text = when {
                        item.progress >= 1f -> {
                            stringResource(
                                R.string.audiobooks_completed
                            )
                        }

                        item.progress > 0f -> {
                            item.remaining
                        }

                        else -> {
                            stringResource(
                                R.string.audiobooks_not_started
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (isActive) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            ) {
                Icon(
                    imageVector = if (isActive) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = stringResource(
                        R.string.content_desc_start_tts
                    ),
                    modifier = Modifier.padding(11.dp)
                )
            }
        }
    }
}

@Composable
private fun ListenSourceBadge(
    isTts: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isTts) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isTts) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    ) {
        Text(
            text = stringResource(
                if (isTts) {
                    R.string.listen_badge_tts
                } else {
                    R.string.listen_badge_audiobook
                }
            ),
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 3.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ListenLibraryEmptyState(
    query: String,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = if (query.isBlank()) {
                    stringResource(
                        R.string.listen_empty
                    )
                } else {
                    stringResource(
                        R.string.listen_no_matches,
                        query
                    )
                },
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign =
                    androidx.compose.ui.text.style.TextAlign.Center
            )

            if (query.isBlank()) {
                Text(
                    text = stringResource(
                        R.string.listen_empty_desc
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign =
                        androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = onAdd,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )

                    Text(
                        text = stringResource(
                            R.string.listen_add
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlDock(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color =
            MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement =
                Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun RowScope.PlayerDockAction(
    @DrawableRes iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(19.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 4.dp,
                vertical = 9.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(21.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
