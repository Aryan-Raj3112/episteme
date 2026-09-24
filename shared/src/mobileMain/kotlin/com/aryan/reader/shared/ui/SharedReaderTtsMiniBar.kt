package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.SharedReaderTtsMiniBarState
import com.aryan.reader.shared.chunkLabel
import com.aryan.reader.shared.subtitle

/**
 * Global reader TTS mini bar. Layout mirrors Android
 * (`ReaderTtsMiniBar.kt:90-193`): rounded surface, tappable title block that
 * returns to the reader, prev/play/next chunk controls.
 */
@Composable
fun SharedReaderTtsMiniBar(
    state: SharedReaderTtsMiniBarState,
    onOpenReader: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPreviousChunk: () -> Unit,
    onNextChunk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canOpenReader = !state.bookId.isNullOrBlank()
    val canSkipPrevious = !state.isLoading && state.chunkIndex > 0 && state.totalChunks > 0
    val canSkipNext = !state.isLoading && state.chunkIndex >= 0 && state.chunkIndex < state.totalChunks - 1
    val title = state.bookTitle ?: readerString("action_read_aloud", "Read aloud")
    val subtitle = state.subtitle()
    // Resolved outside the semantics block, which is not a composable scope.
    val playbackToggleContentDescription = if (state.isPlaying) {
        readerString("tts_pause_reading", "Pause reading")
    } else {
        readerString("tts_resume_reading", "Resume reading")
    }
    Surface(
        modifier = modifier.testTag("ReaderTtsMiniBar"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = canOpenReader, onClick = onOpenReader)
                    .semantics(mergeDescendants = true) { contentDescription = "$title $subtitle" }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onPreviousChunk, enabled = canSkipPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = readerString("tts_previous_chunk", "Previous chunk"),
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(44.dp).semantics {
                        contentDescription = playbackToggleContentDescription
                    }
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }
            IconButton(onClick = onNextChunk, enabled = canSkipNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = readerString("tts_next_chunk", "Next chunk"),
                )
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/** Animated global host wrapper (Android `AppNavigation.kt:682-707` motion spec). */
@Composable
fun SharedReaderTtsMiniBarHost(
    visible: Boolean,
    state: SharedReaderTtsMiniBarState?,
    bottomPaddingDp: Int,
    onOpenReader: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPreviousChunk: () -> Unit,
    onNextChunk: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && state != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        if (state != null) {
            SharedReaderTtsMiniBar(
                state = state,
                onOpenReader = onOpenReader,
                onTogglePlayPause = onTogglePlayPause,
                onPreviousChunk = onPreviousChunk,
                onNextChunk = onNextChunk,
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomPaddingDp.dp)
            )
        }
    }
}
