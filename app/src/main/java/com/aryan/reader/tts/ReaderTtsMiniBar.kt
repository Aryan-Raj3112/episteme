package com.aryan.reader.tts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.R
import com.aryan.reader.tts.TtsPlaybackManager.TtsState

private const val TTS_MINI_BAR_EDGE_PADDING_DP = 16
private const val TTS_MINI_BAR_MAIN_BOTTOM_PADDING_DP = 96

internal fun shouldShowReaderTtsMiniBar(
    ttsState: TtsState,
    isOnReaderRoute: Boolean
): Boolean {
    if (isOnReaderRoute) return false
    if (ttsState.playbackSource != "READER") return false
    if (ttsState.sessionEndedByStop || ttsState.sessionFinished) return false
    return ttsState.isLoading || !ttsState.currentText.isNullOrBlank()
}

internal fun readerTtsMiniBarBottomPaddingDp(isOnMainRoute: Boolean): Int {
    return if (isOnMainRoute) {
        TTS_MINI_BAR_MAIN_BOTTOM_PADDING_DP
    } else {
        TTS_MINI_BAR_EDGE_PADDING_DP
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ReaderTtsMiniBar(
    ttsController: TtsController,
    ttsState: TtsState,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canOpenReader = !ttsState.bookId.isNullOrBlank()
    val canSkipPreviousChunk = !ttsState.isLoading &&
        ttsState.currentChunkIndex > 0 &&
        ttsState.totalChunks > 0
    val canSkipNextChunk = !ttsState.isLoading &&
        ttsState.currentChunkIndex >= 0 &&
        ttsState.currentChunkIndex < ttsState.totalChunks - 1
    val chunkLabel = remember(ttsState.currentChunkIndex, ttsState.totalChunks) {
        formatReaderTtsChunkLabel(ttsState.currentChunkIndex, ttsState.totalChunks)
    }
    val title = ttsState.bookTitle
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.action_read_aloud)
    val subtitle = remember(title, ttsState.chapterTitle, chunkLabel) {
        listOfNotNull(
            chunkLabel,
            ttsState.chapterTitle
                ?.takeIf { it.isNotBlank() && it != title }
        ).joinToString(" - ")
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
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
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                enabled = canSkipPreviousChunk,
                onClick = ttsController::skipToPreviousChunk,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.content_desc_tts_previous_chunk),
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = {
                        if (ttsState.isPlaying) {
                            ttsController.pause()
                        } else {
                            ttsController.resume()
                        }
                    },
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(
                            if (ttsState.isPlaying) {
                                R.string.content_desc_pause_tts
                            } else {
                                R.string.content_desc_resume_tts
                            }
                        ),
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (ttsState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            IconButton(
                enabled = canSkipNextChunk,
                onClick = ttsController::skipToNextChunk,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.content_desc_tts_next_chunk),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
