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
    playback: com.aryan.reader.shared.SharedAudiobookPlaybackState,
    ttsPlayback: com.aryan.reader.shared.SharedBookTtsListenState,
    activeItemId: String?,
    onAudiobookClick: (AudiobookUiItem) -> Unit,
    onListenWithTtsClick: (RecentFileItem) -> Unit,
    onAddAudiobookClick: () -> Unit,
) {
    val adapterLibraryBooksById = remember(libraryBooks) { libraryBooks.associateBy(RecentFileItem::bookId) }
    val sharedAudiobooks = remember(audiobooks, adapterLibraryBooksById) {
        audiobooks.map { audiobook ->
            val libraryItem = adapterLibraryBooksById[audiobook.bookId]
            com.aryan.reader.shared.SharedAudiobook(
                bookId = audiobook.bookId,
                filePath = audiobook.filePath,
                format = audiobook.format,
                title = audiobook.title,
                author = audiobook.author,
                album = audiobook.album,
                narrator = audiobook.narrator,
                durationMs = audiobook.durationMs,
                positionMs = audiobook.positionMs,
                playbackSpeed = audiobook.playbackSpeed,
                coverPath = audiobook.coverPath,
                addedAt = audiobook.addedAt,
                lastListenedAt = if (audiobook.positionMs > 0L) {
                    maxOf(
                        libraryItem?.readingPositionModifiedTimestamp ?: 0L,
                        libraryItem?.timestamp ?: 0L,
                    )
                } else {
                    0L
                },
            )
        }
    }
    val sharedTtsItems = remember(libraryBooks, ttsProgress) {
        com.aryan.reader.shared.buildSharedTtsListenItems(
            books = libraryBooks.map(RecentFileItem::toSharedBookItem),
            progress = ttsProgress.map { progress ->
                com.aryan.reader.shared.SharedBookTtsListeningProgress(
                    bookId = progress.bookId,
                    chapterIndex = progress.chapterIndex,
                    chunkIndex = progress.chunkIndex,
                    sourceCfi = progress.sourceCfi,
                    sourceOffset = progress.sourceOffset,
                    progressPercent = progress.progressPercent,
                    speechRate = progress.speechRate,
                    pitch = progress.pitch,
                    voiceId = progress.voiceId,
                    completed = progress.completed,
                    updatedAt = progress.updatedAt,
                )
            },
        )
    }

    com.aryan.reader.shared.ui.SharedMobileAudiobooksSection(
        audiobooks = sharedAudiobooks,
        playback = playback,
        ttsItems = sharedTtsItems,
        ttsPlayback = ttsPlayback,
        onOpenPlayer = { sharedBook ->
            audiobooks.firstOrNull { it.bookId == sharedBook.bookId }
                ?.toUiItem(adapterLibraryBooksById[sharedBook.bookId])
                ?.let(onAudiobookClick)
        },
        onOpenTtsPlayer = { item, _ ->
            adapterLibraryBooksById[item.book.id]?.let(onListenWithTtsClick)
        },
        onAddAudiobook = onAddAudiobookClick,
        modifier = modifier,
    )
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
    com.aryan.reader.shared.ui.SharedMobileAudiobookAddSheet(
        onChooseFile = onChooseFile,
        onChooseMultiple = onChooseMultiple,
        onChooseFolder = onChooseFolder,
        onDismiss = onDismiss,
        onChooseTtsBook = onChooseTtsBook,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsBookPickerSheet(
    books: List<RecentFileItem>,
    onBookSelected: (RecentFileItem) -> Unit,
    onDismiss: () -> Unit
) {
    val booksById = remember(books) { books.associateBy(RecentFileItem::bookId) }
    com.aryan.reader.shared.ui.SharedMobileTtsBookPickerSheet(
        books = remember(books) { books.map(RecentFileItem::toSharedBookItem) },
        onBookSelected = { sharedBook -> booksById[sharedBook.id]?.let(onBookSelected) },
        onDismiss = onDismiss,
        coverContent = { sharedBook, modifier ->
            booksById[sharedBook.id]?.let { book ->
                ThemedBookCover(item = book, modifier = modifier, contentDescription = book.cardTitle())
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AudiobookPlayerSheet(item: AudiobookUiItem, onBeforePlay: () -> Unit, onDismiss: () -> Unit) {
    if (item.isTts) {
        BookTtsPlayerSheet(item, onDismiss)
        return
    }
    val context = LocalContext.current
    var customSleepTimers by remember(context) { mutableStateOf(loadCustomSleepTimerMinutes(context)) }
    val controller = remember { AudiobookController(context) }
    val playback by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(item.id) { item.playbackRequest?.let(controller::connect) }
    DisposableEffect(controller) { onDispose(controller::release) }
    val request = item.playbackRequest
    val sharedAudiobook = remember(item) {
        com.aryan.reader.shared.SharedAudiobook(
            bookId = item.id,
            filePath = request?.filePath.orEmpty(),
            format = item.chapter,
            title = item.title,
            author = item.author,
            album = request?.album,
            narrator = item.narrator,
            durationMs = request?.durationMs ?: 0L,
            positionMs = request?.positionMs ?: 0L,
            playbackSpeed = request?.speed ?: 1f,
            coverPath = item.coverPath,
            addedAt = item.addedAt,
            lastListenedAt = item.lastListenedAt,
        )
    }
    com.aryan.reader.shared.ui.SharedMobileAudiobookPlayerSheet(
        audiobook = sharedAudiobook,
        playback = playback,
        onTogglePlayback = { controller.togglePlay(onBeforePlay) },
        onSeek = controller::seekTo,
        onSpeedChange = controller::setSpeed,
        onSleepTimer = { minutes ->
            if (minutes == null) controller.toggleSleepTimer() else controller.toggleSleepTimer(minutes)
        },
        customSleepTimerMinutes = customSleepTimers,
        onCustomSleepTimerMinutesChange = { updated ->
            customSleepTimers = updated
            saveCustomSleepTimerMinutes(context, updated)
        },
        onStopPlayback = controller::stop,
        onDismiss = onDismiss,
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookTtsPlayerSheet(item: AudiobookUiItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var customSleepTimers by remember(context) { mutableStateOf(loadCustomSleepTimerMinutes(context)) }
    val sourceBook = item.sourceBook ?: return
    val controller = remember(sourceBook.bookId) { BookTtsAudiobookController(context) }
    val prepared by controller.uiState.collectAsStateWithLifecycle()
    val playback by controller.sharedPlaybackState.collectAsStateWithLifecycle()
    var adapterRate by remember { mutableFloatStateOf(prepared.savedProgress?.speechRate ?: loadTtsSpeechRate(context)) }
    var adapterPitch by remember { mutableFloatStateOf(prepared.savedProgress?.pitch ?: loadTtsPitch(context)) }
    val sharedItem = remember(sourceBook, prepared.savedProgress) {
        com.aryan.reader.shared.SharedTtsListenItem(
            book = sourceBook.toSharedBookItem(),
            progress = prepared.savedProgress?.let { progress ->
                com.aryan.reader.shared.SharedBookTtsListeningProgress(
                    bookId = progress.bookId,
                    chapterIndex = progress.chapterIndex,
                    chunkIndex = progress.chunkIndex,
                    sourceCfi = progress.sourceCfi,
                    sourceOffset = progress.sourceOffset,
                    progressPercent = progress.progressPercent,
                    speechRate = progress.speechRate,
                    pitch = progress.pitch,
                    voiceId = progress.voiceId,
                    completed = progress.completed,
                    updatedAt = progress.updatedAt,
                )
            },
        )
    }
    val isThisBookActive = playback.connected && playback.bookId == sourceBook.bookId
    LaunchedEffect(sourceBook.bookId) {
        controller.connect(sourceBook.bookId)
        if (item.autoStart) controller.start(sourceBook.bookId, BookTtsSessionCoordinator.START_RESUME)
    }
    LaunchedEffect(prepared.savedProgress?.updatedAt) {
        prepared.savedProgress?.let { progress ->
            adapterRate = progress.speechRate
            adapterPitch = progress.pitch
        }
    }
    val adapterChapterIndex = if (isThisBookActive) playback.chapterIndex else prepared.savedProgress?.chapterIndex ?: 0
    val adapterChapterTitle = prepared.book?.chapters?.getOrNull(adapterChapterIndex)?.title ?: item.chapter
    com.aryan.reader.shared.ui.SharedMobileTtsPlayerSheet(
        item = sharedItem,
        playback = playback.copy(
            speechRate = adapterRate,
            pitch = adapterPitch,
            chapterIndex = adapterChapterIndex,
            chapterCount = prepared.book?.chapters?.size ?: playback.chapterCount,
            chapterTitle = adapterChapterTitle,
            progressPercent = if (isThisBookActive) playback.progressPercent else item.progress,
            error = playback.error ?: prepared.error,
        ),
        chapterTitles = prepared.book?.chapters?.map { it.title },
        onTogglePlayback = {
            if (isThisBookActive) controller.togglePlay()
            else controller.start(sourceBook.bookId, BookTtsSessionCoordinator.START_RESUME)
        },
        onSeekChunk = { target ->
            if (target < playback.chunkIndex) controller.previousChunk() else controller.nextChunk()
        },
        onSeekChapter = { target ->
            if (isThisBookActive) controller.selectChapter(target)
            else controller.start(sourceBook.bookId, BookTtsSessionCoordinator.START_CHAPTER, target)
        },
        onSpeedChange = { selectedSpeed ->
            adapterRate = selectedSpeed
            controller.setParameters(rate = selectedSpeed, pitch = adapterPitch)
        },
        onSleepTimer = { minutes -> controller.startSleepTimer(minutes ?: 0) },
        customSleepTimerMinutes = customSleepTimers,
        onCustomSleepTimerMinutesChange = { updated ->
            customSleepTimers = updated
            saveCustomSleepTimerMinutes(context, updated)
        },
        onStopPlayback = controller::stop,
        onDismiss = onDismiss,
    )
    DisposableEffect(controller) { onDispose(controller::release) }
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
    onStop: () -> Unit,
) {
    com.aryan.reader.shared.ui.SharedMobileAudiobookMiniPlayerFrame(
        title = item.title,
        subtitle = item.chapter,
        progress = progress,
        isPlaying = isPlaying,
        onTogglePlayback = onTogglePlay,
        onExpand = onExpand,
        onStopPlayback = onStop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        cover = {
            MockAudioCover(
                item = item,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)),
            )
        },
    )
}

@Composable
private fun LegacyAudiobookMiniPlayer(
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
