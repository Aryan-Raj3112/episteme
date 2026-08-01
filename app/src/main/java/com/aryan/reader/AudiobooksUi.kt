package com.aryan.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.AudiobookEntity
import com.aryan.reader.audiobook.AudiobookController
import com.aryan.reader.audiobook.AudiobookPlaybackRequest
import com.aryan.reader.audiobook.BookTtsListeningProgressEntity
import com.aryan.reader.audiobook.BookTtsContentRepository
import com.aryan.reader.audiobook.BookTtsAudiobookController
import com.aryan.reader.audiobook.BookTtsSessionCoordinator
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.tts.formatReaderTtsChunkLabel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal enum class AudiobookUiStatus { ALL, IN_PROGRESS, NOT_STARTED, COMPLETED }

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

internal fun RecentFileItem.toTtsAudiobookUiItem(progress: BookTtsListeningProgressEntity? = null) = AudiobookUiItem(
    id = "tts-$bookId",
    title = title ?: displayName.substringBeforeLast('.'),
    author = author ?: "Unknown author",
    narrator = "Text-to-speech",
    chapter = progress?.let { "Chapter ${it.chapterIndex + 1}" }
        ?: if ((progressPercentage ?: 0f) > 0f) "Continue from your reading position" else "Start listening",
    progress = ((progress?.progressPercent ?: 0f) / 100f).coerceIn(0f, 1f),
    remaining = "Generated as you listen",
    isTts = true,
    coverPath = coverImagePath,
    sourceBook = this,
)

internal fun AudiobookEntity.toUiItem(): AudiobookUiItem {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val minutes = (durationMs - positionMs).coerceAtLeast(0L) / 60_000
    val remaining = if (durationMs <= 0) "Duration unavailable" else if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
    return AudiobookUiItem(
        bookId, title, author ?: "Unknown author", narrator ?: author ?: "Unknown narrator", format,
        progress.coerceIn(0f, 1f), remaining,
        playbackRequest = AudiobookPlaybackRequest(bookId, filePath, title, author, narrator, album, coverPath, positionMs, durationMs, playbackSpeed)
    )
}

@Composable
internal fun AudiobooksLibrarySection(
    modifier: Modifier,
    audiobooks: List<AudiobookEntity>,
    ebooks: List<RecentFileItem>,
    ttsProgress: List<BookTtsListeningProgressEntity>,
    onAudiobookClick: (AudiobookUiItem) -> Unit,
    onListenWithTtsClick: (RecentFileItem) -> Unit,
    onAddAudiobookClick: () -> Unit,
) {
    var status by remember { mutableStateOf(AudiobookUiStatus.ALL) }
    val ttsItems = ebooks
        .filter { BookTtsContentRepository.supports(it.type) }
        .map { book -> book.toTtsAudiobookUiItem(ttsProgress.firstOrNull { it.bookId == book.bookId }) }
    val allAudiobooks = audiobooks.map(AudiobookEntity::toUiItem)
    val visible = filterAudiobooks(allAudiobooks, status)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("AudiobooksLibrary"),
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                Text(stringResource(R.string.audiobooks_listen_anywhere), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.audiobooks_listen_anywhere_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            (allAudiobooks + ttsItems)
                .filter { it.progress in 0.001f..<1f }
                .maxByOrNull { candidate ->
                    if (candidate.isTts) ttsProgress.firstOrNull { "tts-${it.bookId}" == candidate.id }?.updatedAt ?: 0L
                    else 0L
                }?.let { item ->
                AudiobookContinueCard(
                    item,
                    { onAudiobookClick(if (item.isTts) item.copy(autoStart = true) else item) },
                    Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        item {
            Column {
                SectionHeading(stringResource(R.string.audiobooks_your_audiobooks), stringResource(R.string.audiobooks_imported_files_desc))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AudiobookUiStatus.entries) { option ->
                        FilterChip(selected = status == option, onClick = { status = option }, label = { Text(stringResource(option.labelRes)) })
                    }
                }
                if (visible.isEmpty()) {
                    AudiobookEmptyCard(onAddAudiobookClick, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(visible, key = { it.id }) { AudiobookCoverCard(it, { onAudiobookClick(it) }) }
                    }
                }
            }
        }
        item {
            Column {
                SectionHeading(stringResource(R.string.audiobooks_listen_to_ebooks), stringResource(R.string.audiobooks_tts_desc))
                if (ttsItems.isEmpty()) {
                    TtsDiscoveryCard(onAddAudiobookClick, Modifier.padding(horizontal = 20.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(ttsItems, key = { it.id }) { item ->
                            TtsBookCard(requireNotNull(item.sourceBook), { onListenWithTtsClick(requireNotNull(item.sourceBook)) })
                        }
                    }
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

@Composable private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AudiobookContinueCard(item: AudiobookUiItem, onClick: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth().clickable(onClick = onClick).testTag("AudiobookContinue"), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer))).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MockAudioCover(item, Modifier.size(96.dp, 132.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.audiobooks_continue_listening).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.chapter, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator({ item.progress }, Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(CircleShape))
                Text(item.remaining, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.PlayArrow, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@Composable private fun AudiobookCoverCard(item: AudiobookUiItem, onClick: () -> Unit) {
    Column(Modifier.size(width = 142.dp, height = 248.dp).clickable(onClick = onClick).testTag("AudiobookCard-${item.id}")) {
        Box {
            MockAudioCover(item, Modifier.fillMaxWidth().aspectRatio(.72f))
            Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.PlayArrow, null, Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
        Text(item.title, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.remaining, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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

@Composable private fun TtsBookCard(book: RecentFileItem, onClick: () -> Unit) {
    Card(Modifier.size(width = 250.dp, height = 126.dp).clickable(onClick = onClick).testTag("ListenWithTts-${book.bookId}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemedBookCover(book, Modifier.size(62.dp, 94.dp), book.displayName)
            Column(Modifier.weight(1f)) {
                Text(book.title ?: book.displayName.substringBeforeLast('.'), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author ?: stringResource(R.string.audiobooks_unknown_author), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                AssistChip(onClick = onClick, label = { Text(stringResource(R.string.audiobooks_listen_with_tts)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(16.dp)) })
            }
        }
    }
}

@Composable private fun AudiobookEmptyCard(onAdd: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.audiobooks_empty), Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
            Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.audiobooks_add), Modifier.padding(start = 8.dp)) }
        }
    }
}

@Composable private fun TtsDiscoveryCard(onAdd: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth().clickable(onClick = onAdd), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Book, null, Modifier.size(38.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(stringResource(R.string.audiobooks_no_ebooks), fontWeight = FontWeight.Bold); Text(stringResource(R.string.audiobooks_no_ebooks_desc)) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AudiobookAddSheet(
    onChooseFile: () -> Unit,
    onChooseMultiple: () -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.audiobooks_add), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.audiobooks_add_preview_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
            AddChoice(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.audiobooks_add_file), stringResource(R.string.audiobooks_add_file_desc), onChooseFile)
            AddChoice(Icons.Default.Folder, stringResource(R.string.audiobooks_add_multiple), "Import several audiobook files at once", onChooseMultiple)
            AddChoice(Icons.Default.Folder, stringResource(R.string.audiobooks_add_folder), "Import supported audio files from a folder", onChooseFolder)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun AddChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
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
    val duration = playback.durationMs.takeIf { it > 0 } ?: item.playbackRequest?.durationMs ?: 0L
    val displayedPosition = draggedPosition?.toLong() ?: playback.positionMs
    val progress = if (duration > 0) displayedPosition.toFloat() / duration else item.progress
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f), MaterialTheme.colorScheme.surface))
            ).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.action_close)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.audiobooks_now_playing).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(item.chapter, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { controller.stop(); onDismiss() }) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) }
            }
            MockAudioCover(item, Modifier.fillMaxWidth(.58f).aspectRatio(.72f).padding(top = 18.dp).shadow(18.dp, RoundedCornerShape(20.dp)))
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
                IconButton(onClick = { controller.seekBy(-30_000) }, enabled = playback.connected) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.audiobooks_rewind)) }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) { IconButton(onClick = { controller.togglePlay(onBeforePlay) }, enabled = playback.connected, modifier = Modifier.size(76.dp)) { Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playback.isPlaying) R.string.content_desc_pause_tts else R.string.content_desc_start_tts), Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimary) } }
                IconButton(onClick = { controller.seekBy(30_000) }, enabled = playback.connected) { Icon(Icons.Default.SkipNext, stringResource(R.string.audiobooks_forward)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AssistChip(onClick = { controller.setSpeed(nextAudiobookSpeed(playback.speed)) }, label = { Text("${playback.speed}×") })
                AssistChip(onClick = controller::toggleSleepTimer, label = { Text(sleepTimerLabel) }, leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) })
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookTtsPlayerSheet(item: AudiobookUiItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sourceBook = item.sourceBook ?: return
    val controller = remember(sourceBook.bookId) { BookTtsAudiobookController(context) }
    val prepared by controller.uiState.collectAsStateWithLifecycle()
    val playback by controller.playbackState.collectAsStateWithLifecycle()
    var showChapters by remember { mutableStateOf(false) }
    var showTranscript by rememberSaveable(sourceBook.bookId) { mutableStateOf(false) }
    val transcriptListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rate by remember { mutableFloatStateOf(prepared.savedProgress?.speechRate ?: loadTtsSpeechRate(context)) }
    var pitch by remember { mutableFloatStateOf(prepared.savedProgress?.pitch ?: loadTtsPitch(context)) }
    val isThisBookActive = playback.playbackSource == "AUDIOBOOK_TTS" && playback.bookId == sourceBook.bookId
    val book = prepared.book
    val currentChapter = if (isThisBookActive) playback.chapterIndex else prepared.savedProgress?.chapterIndex
    val progress = if (isThisBookActive) {
        calculateTtsAudiobookProgress(
            chapterIndex = currentChapter ?: 0,
            chapterCount = book?.chapters?.size ?: 0,
            chunkIndex = playback.currentChunkIndex,
            chunkCount = playback.totalChunks
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
    LaunchedEffect(playback.currentChunkIndex, playback.transcriptStartIndex, showTranscript) {
        if (showTranscript && playback.transcriptChunks.isNotEmpty()) {
            val localIndex = (playback.currentChunkIndex - playback.transcriptStartIndex)
                .coerceIn(playback.transcriptChunks.indices)
            transcriptListState.animateScrollToItem(localIndex)
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
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.action_close))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LISTENING WITH TTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(currentChapterTitle, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { controller.stop(); onDismiss() }, enabled = isThisBookActive) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_close))
                    }
                }

                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        showChapters -> LazyColumn(
                            Modifier.fillMaxSize().testTag("AudiobookChapterList"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(book?.chapters.orEmpty(), key = { it.id }) { chapter ->
                                val selected = chapter.index == currentChapter
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                        .clickable {
                                            if (selected && isThisBookActive) {
                                                showChapters = false
                                                return@clickable
                                            }
                                            if (isThisBookActive) controller.selectChapter(chapter.index)
                                            else controller.start(sourceBook.bookId, BookTtsSessionCoordinator.START_CHAPTER, chapter.index)
                                            showChapters = false
                                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${chapter.index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(chapter.title, Modifier.weight(1f).padding(start = 16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (selected) Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp))
                                }
                            }
                        }
                        showTranscript -> AudiobookTranscript(
                            chunks = playback.transcriptChunks,
                            startIndex = playback.transcriptStartIndex,
                            currentIndex = playback.currentChunkIndex,
                            listState = transcriptListState,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> MockAudioCover(
                            item,
                            Modifier.fillMaxHeight(.94f).aspectRatio(.72f)
                                .shadow(18.dp, RoundedCornerShape(20.dp))
                        )
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
                            if (isThisBookActive && playback.currentChunkIndex >= 0) {
                                formatReaderTtsChunkLabel(playback.currentChunkIndex, playback.totalChunks).orEmpty()
                            } else "Chapter ${(currentChapter ?: 0) + 1}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = controller::previousChapter, enabled = isThisBookActive && (currentChapter ?: 0) > 0) {
                        Icon(Icons.Default.SkipPrevious, "Previous chapter")
                    }
                    IconButton(onClick = controller::previousChunk, enabled = isThisBookActive && playback.currentChunkIndex > 0) {
                        Text("−1", fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
                        IconButton(
                            onClick = {
                                if (isThisBookActive) controller.togglePlay()
                                else controller.start(sourceBook.bookId, BookTtsSessionCoordinator.START_RESUME)
                            },
                            modifier = Modifier.size(76.dp)
                        ) {
                            Icon(
                                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playback.isPlaying) "Pause" else "Play",
                                Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = controller::nextChunk, enabled = isThisBookActive && playback.currentChunkIndex < playback.totalChunks - 1) {
                        Text("+1", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = controller::nextChapter, enabled = isThisBookActive && (currentChapter ?: 0) < (book?.chapters?.lastIndex ?: 0)) {
                        Icon(Icons.Default.SkipNext, "Next chapter")
                    }
                }

                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                    AssistChip(
                        onClick = {
                            rate = nextAudiobookSpeed(rate)
                            controller.setParameters(rate, pitch)
                        },
                        label = { Text("${"%.2g".format(rate)}×") }
                    )
                    }
                    item {
                    AssistChip(
                        onClick = {
                            showChapters = !showChapters
                            if (showChapters) showTranscript = false
                        },
                        label = { Text("Chapters") },
                        leadingIcon = { Icon(Icons.Default.Menu, null, Modifier.size(16.dp)) }
                    )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                showTranscript = !showTranscript
                                if (showTranscript) showChapters = false
                            },
                            label = { Text(if (showTranscript) "Hide transcript" else "Transcript") },
                            leadingIcon = { Icon(Icons.Default.Book, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                    AssistChip(
                        onClick = { controller.startSleepTimer(30) },
                        label = { Text(controller.sleepTimerLabel.collectAsStateWithLifecycle().value) },
                        leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) }
                    )
                    }
                }
            }
        }
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
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Spoken text will appear when playback starts",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    LazyColumn(
        modifier.testTag("AudiobookTranscript"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        itemsIndexed(chunks) { localIndex, text ->
            val absoluteIndex = startIndex + localIndex
            val selected = absoluteIndex == currentIndex
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun AudiobookMiniPlayer(
    item: AudiobookUiItem,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onStop: () -> Unit
) {
    var verticalDrag by remember { mutableFloatStateOf(0f) }
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
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MockAudioCover(item, Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.chapter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = onStop) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) }
        }
    }
}

internal fun nextAudiobookSpeed(current: Float): Float {
    val speeds = listOf(.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    return speeds.firstOrNull { it > current + .01f } ?: speeds.first()
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
