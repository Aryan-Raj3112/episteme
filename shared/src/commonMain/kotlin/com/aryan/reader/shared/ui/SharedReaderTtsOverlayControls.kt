package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.readerCloudTtsControlsModel
import com.aryan.reader.shared.readerCloudTtsVoiceById

@Composable
fun SharedReaderTtsOverlayControls(
    settings: ReaderAiByokSettings,
    cloudTts: ReaderCloudTtsState,
    credits: Int,
    showCredits: Boolean,
    isCollapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    onPauseResume: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onLocateCurrentChunk: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sanitized = settings.sanitized()
    val controls = readerCloudTtsControlsModel(cloudTts)
    if (!controls.isVisible) return

    val voice = remember(sanitized.ttsSpeakerId) { readerCloudTtsVoiceById(sanitized.ttsSpeakerId) }
    val progress = cloudTts.progress
    val chunk = progress.currentChunk
    val chunkLabel = if (progress.currentChunkIndex >= 0 && progress.chunks.isNotEmpty()) {
        readerString(
            "desktop_tts_chunk_count",
            "Part %1\$d/%2\$d",
            progress.currentChunkIndex + 1,
            progress.chunks.size
        )
    } else {
        null
    }
    val progressFraction = if (progress.currentChunkIndex >= 0 && progress.chunks.isNotEmpty()) {
        ((progress.currentChunkIndex + 1).toFloat() / progress.chunks.size).coerceIn(0f, 1f)
    } else {
        null
    }
    val title = when {
        cloudTts.isLoading -> readerString("desktop_preparing_audio", "Preparing audio")
        cloudTts.isPaused -> readerString("desktop_paused", "Paused")
        cloudTts.isPlaying -> readerString("label_reading", "Reading")
        else -> readerString("action_read_aloud", "Read aloud")
    }
    val chapterLabel = chunk?.chapterTitle
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.joinToString(" - ")
        ?.takeIf { it.isNotBlank() }
    val statusLine = cloudTts.errorMessage
        ?: progress.currentPositionLabel
        ?: cloudTts.statusMessage
        ?: chapterLabel
        ?: voice?.let { "${it.name}: ${it.description}" }
        ?: ""
    val scopeLabel = readerTtsScopeLabel(progress.scope)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = modifier.widthIn(max = 560.dp).animateContentSize()
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "SharedReaderTtsOverlay"
        ) { collapsed ->
            if (collapsed) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { onCollapseChange(false) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = readerString("content_desc_expand", "Expand"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(
                        modifier = Modifier.widthIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            chunkLabel ?: scopeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SharedReaderTtsPlayPauseButton(
                        isPlaying = cloudTts.isPlaying,
                        isLoading = cloudTts.isLoading,
                        enabled = controls.canPauseResume,
                        size = 36,
                        iconSize = 20,
                        onClick = onPauseResume
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = readerString("content_desc_stop_tts", "Stop read aloud"),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (statusLine.isNotBlank()) {
                                Text(
                                    statusLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (cloudTts.errorMessage != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                enabled = controls.canLocateCurrentChunk,
                                onClick = onLocateCurrentChunk,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = readerString("desktop_locate_current_tts", "Locate current reading"),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onCollapseChange(true) }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    contentDescription = readerString("content_desc_collapse", "Collapse"),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = readerString("content_desc_stop_tts", "Stop read aloud"),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SharedReaderTtsPill(readerString("tts_mode_cloud_ai", "Cloud AI"))
                        voice?.let { SharedReaderTtsPill(it.name) }
                        if (showCredits) {
                            SharedReaderTtsPill(readerString("credits_count", "%1\$d credits", credits))
                        }
                        SharedReaderTtsPill(chunkLabel ?: scopeLabel)
                    }

                    progressFraction?.let { fraction ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            enabled = controls.canSkipPrevious,
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = readerString("content_desc_tts_previous_chunk", "Previous chunk"),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        SharedReaderTtsPlayPauseButton(
                            isPlaying = cloudTts.isPlaying,
                            isLoading = cloudTts.isLoading,
                            enabled = controls.canPauseResume,
                            size = 56,
                            iconSize = 28,
                            onClick = onPauseResume
                        )
                        Spacer(Modifier.width(10.dp))
                        IconButton(
                            enabled = controls.canSkipNext,
                            onClick = onSkipNext,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = readerString("content_desc_tts_next_chunk", "Next chunk"),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedReaderTtsPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SharedReaderTtsPlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    size: Int,
    iconSize: Int,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        FilledIconButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.size(size.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = readerString("content_desc_play_pause", "Play or pause"),
                modifier = Modifier.size(iconSize.dp)
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                strokeWidth = if (size >= 56) 3.dp else 2.dp
            )
        }
    }
}

@Composable
private fun readerTtsScopeLabel(scope: ReaderTtsReadScope): String {
    return when (scope) {
        ReaderTtsReadScope.PAGE -> readerString("desktop_page", "Page")
        ReaderTtsReadScope.CHAPTER -> readerString("chapter", "Chapter")
        ReaderTtsReadScope.BOOK -> readerString("desktop_from_here", "From here")
    }
}
