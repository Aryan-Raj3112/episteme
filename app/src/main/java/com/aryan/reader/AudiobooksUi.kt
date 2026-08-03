package com.aryan.reader

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
    activeItemId: String?,
    onAudiobookClick: (AudiobookUiItem) -> Unit,
    onListenWithTtsClick: (RecentFileItem) -> Unit,
    onAddAudiobookClick: () -> Unit,
) {
    var status by remember { mutableStateOf(AudiobookUiStatus.ALL) }

    val ttsItems = ebooks
        .filter { BookTtsContentRepository.supports(it.type) }
        .map { book ->
            book.toTtsAudiobookUiItem(
                ttsProgress.firstOrNull { it.bookId == book.bookId }
            )
        }

    val allAudiobooks = audiobooks.map(AudiobookEntity::toUiItem)
    val visible = filterAudiobooks(allAudiobooks, status)

    val resumableItems = (allAudiobooks + ttsItems)
        .filter { it.progress in 0.001f..<1f }

    val continueItem =
        resumableItems.firstOrNull { it.id == activeItemId }
            ?: resumableItems.maxByOrNull { candidate ->
                if (candidate.isTts) {
                    ttsProgress
                        .firstOrNull { "tts-${it.bookId}" == candidate.id }
                        ?.updatedAt
                        ?: 0L
                } else {
                    0L
                }
            }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("AudiobooksLibrary"),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        continueItem?.let { item ->
            item {
                AudiobookContinueCard(
                    item = item,
                    isActive = item.id == activeItemId,
                    onClick = {
                        onAudiobookClick(
                            if (item.isTts) {
                                item.copy(autoStart = true)
                            } else {
                                item
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        item {
            Column {
                SectionHeading(
                    title = stringResource(R.string.audiobooks_your_audiobooks),
                    subtitle = stringResource(R.string.audiobooks_imported_files_desc)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AudiobookUiStatus.entries) { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = {
                                Text(stringResource(option.labelRes))
                            }
                        )
                    }
                }

                if (visible.isEmpty()) {
                    AudiobookEmptyCard(
                        onAdd = onAddAudiobookClick,
                        modifier = Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 8.dp
                        )
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(
                            horizontal = 20.dp,
                            vertical = 8.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = visible,
                            key = { it.id }
                        ) { item ->
                            AudiobookCoverCard(
                                item = item,
                                isActive = item.id == activeItemId,
                                onClick = { onAudiobookClick(item) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionHeading(
                    title = stringResource(R.string.audiobooks_listen_to_ebooks),
                    subtitle = stringResource(R.string.audiobooks_tts_desc)
                )

                if (ttsItems.isEmpty()) {
                    TtsDiscoveryCard(
                        onAdd = onAddAudiobookClick,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = ttsItems,
                            key = { it.id }
                        ) { item ->
                            TtsBookCard(
                                item = item,
                                isActive = item.id == activeItemId,
                                onClick = {
                                    item.sourceBook?.let(onListenWithTtsClick)
                                }
                            )
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

@Composable
private fun AudiobookCoverCard(
    item: AudiobookUiItem,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val cardWidth = if (item.isTts) 142.dp else 164.dp
    val artworkAspectRatio = if (item.isTts) 0.72f else 1f

    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
            .testTag("AudiobookCard-${item.id}")
    ) {
        Box {
            MockAudioCover(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(artworkAspectRatio)
            )

            if (isActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )

                        Text(
                            text = stringResource(R.string.audiobooks_now_playing),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Text(
            text = item.title,
            modifier = Modifier.padding(top = 9.dp),
            fontWeight = FontWeight.SemiBold,
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

        if (item.progress > 0f) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(4.dp)
                    .clip(CircleShape)
            )
        }

        Text(
            text = when {
                item.progress >= 1f ->
                    stringResource(R.string.audiobooks_completed)

                item.progress > 0f ->
                    item.remaining

                else ->
                    stringResource(R.string.audiobooks_not_started)
            },
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

@Composable
private fun TtsBookCard(
    item: AudiobookUiItem,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val book = item.sourceBook ?: return

    Card(
        modifier = Modifier
            .width(270.dp)
            .heightIn(min = 132.dp)
            .clickable(onClick = onClick)
            .testTag("ListenWithTts-${book.bookId}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemedBookCover(
                item = book,
                modifier = Modifier.size(
                    width = 68.dp,
                    height = 102.dp
                ),
                contentDescription = book.displayName
            )

            Column(modifier = Modifier.weight(1f)) {
                if (isActive) {
                    Text(
                        text = stringResource(R.string.audiobooks_now_playing)
                            .uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = item.title,
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
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                    )

                    Text(
                        text = stringResource(
                            R.string.progress_complete,
                            (item.progress * 100).toInt()
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = if (isActive) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = stringResource(
                        R.string.audiobooks_listen_with_tts
                    ),
                    modifier = Modifier.padding(10.dp)
                )
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
    var showPlayerMenu by remember(item.id) {
        mutableStateOf(false)
    }
    val duration = playback.durationMs.takeIf { it > 0 } ?: item.playbackRequest?.durationMs ?: 0L
    val displayedPosition = draggedPosition?.toLong() ?: playback.positionMs
    var showSpeedDialog by remember(item.id) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AssistChip(
                    onClick = {
                        showSpeedDialog = true
                    },
                    label = {
                        Text(audiobookSpeedLabel(playback.speed))
                    }
                )
                AssistChip(onClick = controller::toggleSleepTimer, label = { Text(sleepTimerLabel) }, leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) })
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
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookTtsPlayerSheet(item: AudiobookUiItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sourceBook = item.sourceBook ?: return
    val controller = remember(sourceBook.bookId) { BookTtsAudiobookController(context) }
    val prepared by controller.uiState.collectAsStateWithLifecycle()
    val playback by controller.playbackState.collectAsStateWithLifecycle()
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
    LaunchedEffect(
        playback.currentChunkIndex,
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
                        playback.currentChunkIndex -
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
                                    currentIndex = playback.currentChunkIndex,
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
                            if (isThisBookActive && playback.currentChunkIndex >= 0) {
                                formatReaderTtsChunkLabel(playback.currentChunkIndex, playback.totalChunks).orEmpty()
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
                        enabled = isThisBookActive && playback.currentChunkIndex > 0
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
                                playback.currentChunkIndex < playback.totalChunks - 1
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

                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = {
                                showSpeedDialog = true
                            },
                            label = {
                                Text(audiobookSpeedLabel(rate))
                            }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                activePanel =
                                    if (
                                        activePanel ==
                                        TtsPlayerPanel.CHAPTERS
                                    ) {
                                        TtsPlayerPanel.COVER
                                    } else {
                                        TtsPlayerPanel.CHAPTERS
                                    }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (
                                            activePanel ==
                                            TtsPlayerPanel.CHAPTERS
                                        ) {
                                            R.string.audiobooks_hide_chapters
                                        } else {
                                            R.string.audiobooks_chapters
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                activePanel =
                                    if (
                                        activePanel ==
                                        TtsPlayerPanel.TRANSCRIPT
                                    ) {
                                        TtsPlayerPanel.COVER
                                    } else {
                                        TtsPlayerPanel.TRANSCRIPT
                                    }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (
                                            activePanel ==
                                            TtsPlayerPanel.TRANSCRIPT
                                        ) {
                                            R.string.audiobooks_hide_transcript
                                        } else {
                                            R.string.audiobooks_transcript
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                showSleepTimerDialog = true
                            },
                            label = {
                                Text(sleepTimerLabel)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
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