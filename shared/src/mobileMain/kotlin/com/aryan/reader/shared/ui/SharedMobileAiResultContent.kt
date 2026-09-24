package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.readerMarkdownPlainText
import com.aryan.reader.shared.splitSharedTtsListenChunks

/**
 * Shared mobile port of Android's `AiResultContentView`
 * (app/src/main/java/com/aryan/reader/AndroidAiHub.kt) and the action row of
 * `AiDefinitionPopup` (AndroidSearchUi.kt).
 *
 * Title + usage badge, Thinking loading state, End-aligned actions
 * (Delete / Regenerate / TTS / Copy / external dictionary), divider, then
 * scrollable markdown with live TTS highlight. TTS uses a popup-scoped local
 * engine (Android parity: separate POPUP controller stopped on dispose) so
 * the reader's main session is untouched.
 */
@Composable
fun SharedMobileAiResultContent(
    title: String,
    markdownText: String,
    errorText: String?,
    isLoading: Boolean,
    cost: Double?,
    freeRemaining: Int?,
    isCacheHit: Boolean,
    onRegenerate: (() -> Unit)?,
    onClear: (() -> Unit)?,
    onOpenExternalDictionary: (() -> Unit)?,
    isMainTtsActive: Boolean,
    ttsBookTitle: String,
    ttsChapterTitle: String,
    emptyText: String? = null,
    /**
     * Usage badge (cost / free-remaining / cache-hit) mirrors Android's
     * credits UI. iOS hides it until credits launch — callers pass
     * `aiCredits != null`.
     */
    showUsageBadge: Boolean = true,
    modifier: Modifier = Modifier
) {
    val popupTts = rememberSharedMobileEpubLocalTts()
    DisposableEffect(popupTts) {
        onDispose {
            if (popupTts.isSessionActive) popupTts.stop()
        }
    }
    val plainText = remember(markdownText) {
        readerMarkdownPlainText(markdownText).ifBlank { markdownText.trim() }
    }
    val ttsChunks = remember(plainText, ttsChapterTitle) {
        sharedMobileAiTtsChunks(plainText, ttsChapterTitle)
    }
    val currentSpoken = popupTts.progress.currentChunk?.spokenText
        .takeIf { popupTts.progress.isActive }
    val isTtsSessionActive = popupTts.progress.isActive ||
        popupTts.state == SharedMobileEpubLocalTtsState.SPEAKING
    val copyLabel = readerString("action_copy", "Copy")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            if (showUsageBadge && sharedAiUsageBadgeVisible(isCacheHit, cost, isLoading, plainText.isNotBlank())) {
                SharedAiUsageBadge(
                    cost = cost,
                    freeRemaining = freeRemaining,
                    isCacheHit = isCacheHit,
                    isLoading = isLoading
                )
            }
        }

        if (isLoading && plainText.isBlank() && errorText.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(
                        readerString("ai_thinking", "Thinking…"),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            if (plainText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onClear != null && !isLoading) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = readerString("action_clear", "Clear"),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (onRegenerate != null) {
                        TextButton(onClick = onRegenerate) {
                            Text(readerString("ai_regenerate", "Regenerate"))
                        }
                    }
                    IconButton(
                        onClick = {
                            if (isTtsSessionActive) {
                                popupTts.stop()
                            } else if (ttsChunks.isNotEmpty()) {
                                popupTts.start(
                                    chunks = ttsChunks,
                                    bookTitle = ttsBookTitle,
                                    startChunkIndex = 0,
                                    playWhenReady = true
                                )
                            }
                        },
                        enabled = !isMainTtsActive || popupTts.isSessionActive
                    ) {
                        Icon(
                            imageVector = if (isTtsSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = readerString(
                                if (isTtsSessionActive) "action_stop" else "action_read_aloud",
                                if (isTtsSessionActive) "Stop" else "Read aloud"
                            )
                        )
                    }
                    IconButton(onClick = { writeSharedClipboard(copyLabel, plainText) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = readerString("action_copy", "Copy")
                        )
                    }
                    if (onOpenExternalDictionary != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onOpenExternalDictionary) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = readerString(
                                    "content_desc_open_dictionary",
                                    "Open in dictionary"
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (errorText != null && plainText.isBlank()) {
                Text(
                    errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (plainText.isNotBlank()) {
                SharedMarkdownText(
                    markdown = markdownText,
                    style = MaterialTheme.typography.bodyLarge,
                    highlightText = currentSpoken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            } else if (emptyText != null) {
                Text(emptyText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun SharedAiUsageBadge(
    cost: Double?,
    freeRemaining: Int?,
    isCacheHit: Boolean,
    isLoading: Boolean
) {
    val isFreeGenerated = cost == 0.0 && freeRemaining != null
    val highlighted = isCacheHit || isFreeGenerated
    Surface(
        color = if (highlighted) Color(0xFF4CAF50).copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = when {
                isCacheHit -> readerString("ai_cache_hit_free", "Cache hit · Free")
                cost != null && isFreeGenerated -> readerString(
                    "ai_generated_free_remaining",
                    "Generated · %1\$d free left",
                    freeRemaining
                )
                cost != null -> readerString("ai_generated_cost", "Cost: %1\$s", cost.toString())
                else -> readerString("ai_generating_cost_calculating", "Calculating cost…")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) Color(0xFF388E3C)
            else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

internal fun sharedAiUsageBadgeVisible(
    isCacheHit: Boolean,
    cost: Double?,
    isLoading: Boolean,
    hasText: Boolean
): Boolean {
    if (!hasText && !isLoading) return false
    return isCacheHit || cost != null || isLoading
}

internal fun sharedMobileAiTtsChunks(plainText: String, chapterTitle: String): List<ReaderTtsChunk> {
    val parts = splitSharedTtsListenChunks(plainText)
    var offset = 0
    return parts.mapIndexed { index, part ->
        val start = plainText.indexOf(part, offset).takeIf { it >= 0 } ?: offset
        val end = (start + part.length).coerceAtMost(plainText.length)
        offset = end
        ReaderTtsChunk(
            index = index,
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = chapterTitle,
            text = part,
            startOffset = start,
            endOffset = end
        )
    }
}

/**
 * Generic fallback sheet for summary/recap results produced outside the hub
 * (e.g. hub dismissed mid-generation): title + full result content with
 * TTS/Copy, no regenerate (original input is not retained).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileAiTextResultSheet(
    result: ReaderAiResultState,
    isMainTtsActive: Boolean,
    ttsBookTitle: String,
    onDismiss: () -> Unit,
    showUsageBadge: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SharedMobileAiResultContent(
                title = result.title ?: readerString("desktop_ai", "AI"),
                markdownText = result.text,
                errorText = result.errorMessage,
                isLoading = result.isLoading,
                cost = result.cost,
                freeRemaining = result.freeRemaining,
                isCacheHit = result.isCacheHit,
                onRegenerate = null,
                onClear = null,
                onOpenExternalDictionary = null,
                isMainTtsActive = isMainTtsActive,
                ttsBookTitle = ttsBookTitle,
                ttsChapterTitle = result.title.orEmpty(),
                showUsageBadge = showUsageBadge,
            )
        }
    }
}
