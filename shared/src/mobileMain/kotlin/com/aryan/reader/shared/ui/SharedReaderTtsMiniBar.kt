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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButtonDefaults
import com.aryan.reader.shared.SharedReaderTtsMiniBarState
import com.aryan.reader.shared.chunkLabel
import com.aryan.reader.shared.subtitle

/**
 * Global reader TTS mini bar. Layout mirrors Android
 * (`ReaderTtsMiniBar.kt:90-193`): rounded surface with an outline border, a
 * tappable title block that returns to the reader, and 40dp prev/play/next chunk
 * controls.
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
    val title = state.bookTitle?.takeIf { it.isNotBlank() }
        ?: readerString("action_read_aloud", "Read aloud")
    val subtitle = state.subtitle()
    // Resolved outside the semantics block, which is not a composable scope.
    val playbackToggleContentDescription = if (state.isPlaying) {
        readerString("content_desc_pause_tts", "Pause reading")
    } else {
        readerString("content_desc_resume_tts", "Resume reading")
    }
    val previousContentDescription = readerString("content_desc_tts_previous_chunk", "Previous chunk")
    val nextContentDescription = readerString("content_desc_tts_next_chunk", "Next chunk")
    Surface(
        modifier = modifier.testTag("ReaderTtsMiniBar"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = canOpenReader, onClick = onOpenReader)
                    .semantics(mergeDescendants = true) { contentDescription = "$title $subtitle" }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
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
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onPreviousChunk,
                enabled = canSkipPrevious,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = previousContentDescription,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(44.dp).semantics {
                        contentDescription = playbackToggleContentDescription
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }
            IconButton(
                onClick = onNextChunk,
                enabled = canSkipNext,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = nextContentDescription,
                    modifier = Modifier.size(24.dp)
                )
            }
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
