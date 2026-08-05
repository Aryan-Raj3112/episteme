package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.SharedAudiobook
import com.aryan.reader.shared.SharedAudiobookPlaybackState
import com.aryan.reader.shared.SharedAudiobookSort
import com.aryan.reader.shared.SharedAudiobookStatus
import com.aryan.reader.shared.filterSharedAudiobooks
import com.aryan.reader.shared.formatSharedPlaybackTime
import com.aryan.reader.shared.formatSharedSleepTimerLabel
import com.aryan.reader.shared.matchesSharedAudiobookQuery
import com.aryan.reader.shared.progressFraction
import com.aryan.reader.shared.sharedAudiobookRemainingLabel
import com.aryan.reader.shared.sortSharedAudiobooks
import com.aryan.reader.shared.toSharedAudiobookLibraryItem
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SharedMobileAudiobooksSection(
    audiobooks: List<SharedAudiobook>,
    playback: SharedAudiobookPlaybackState,
    onOpenPlayer: (SharedAudiobook) -> Unit,
    onAddAudiobook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf(SharedAudiobookStatus.ALL) }
    var sort by remember { mutableStateOf(SharedAudiobookSort.RECENTLY_LISTENED) }
    var query by remember { mutableStateOf("") }
    var sortExpanded by remember { mutableStateOf(false) }

    val visibleItems = remember(audiobooks, status, sort, query) {
        val filteredIds = filterSharedAudiobooks(
            audiobooks.map { it.toSharedAudiobookLibraryItem() },
            status,
        ).mapTo(mutableSetOf()) { it.id }
        sortSharedAudiobooks(
            audiobooks.filter { it.bookId in filteredIds && it.matchesSharedAudiobookQuery(query) },
            sort,
        )
    }

    val continueItem = remember(visibleItems, playback.bookId) {
        visibleItems.firstOrNull { it.bookId == playback.bookId }
            ?: visibleItems.filter { it.progressFraction in 0.001f..<1f }
                .maxByOrNull { it.lastListenedAt }
    }

    Column(modifier = modifier.fillMaxSize().testTag("AudiobooksLibrary")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    text = readerQuantityString("listen_item_count", visibleItems.size, "%1\$d item", "%1\$d items", visibleItems.size),
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
            continueItem?.let { book ->
                item(key = "continue-${book.bookId}") {
                    SharedMobileAudiobookContinueCard(
                        audiobook = book,
                        isActive = book.bookId == playback.bookId,
                        onOpen = { onOpenPlayer(book) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (visibleItems.isEmpty()) {
                item(key = "listen-empty") {
                    ListenLibraryEmptyState(query = query, onAdd = onAddAudiobook)
                }
            } else {
                items(visibleItems, key = { it.bookId }) { book ->
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
                .padding(horizontal = 24.dp),
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
                TextButton(onClick = onCancelSleep, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(readerString("audiobooks_turn_off_sleep_timer", "Turn off"))
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
            "M360,120L360,40L600,40L600,120L360,120ZM440,560L520,560L520,320L440,320L440,560ZM340.5,851.5Q275,823 226,774Q177,725 148.5,659.5Q120,594 120,520Q120,446 148.5,380.5Q177,315 226,266Q275,217 340.5,188.5Q406,160 480,160Q542,160 599,180Q656,200 706,238L762,182L818,238L762,294Q800,344 820,401Q840,458 840,520Q840,594 811.5,659.5Q783,725 734,774Q685,823 619.5,851.5Q554,880 480,880Q406,880 340.5,851.5ZM678,718Q760,636 760,520Q760,404 678,322Q596,240 480,240Q364,240 282,322Q200,404 200,520Q200,636 282,718Q364,800 480,800Q596,800 678,718ZM480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Q480,520 480,520Z"
        )
    }.build()
}
