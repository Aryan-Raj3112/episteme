package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.readerMarkdownPlainText

/**
 * Shared mobile port of Android's `AiDefinitionPopup`
 * (app/src/main/java/com/aryan/reader/AndroidSearchUi.kt).
 *
 * Presented as a bottom sheet (the shared-idiomatic equivalent of Android's
 * bottom-centered popup card): selected-word headline, End-aligned TTS /
 * Copy / external-dictionary actions, divider, then Thinking / error /
 * scrollable markdown with live TTS highlight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileAiDefinitionSheet(
    word: String?,
    result: ReaderAiResultState,
    isMainTtsActive: Boolean,
    onOpenExternalDictionary: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val popupTts = rememberSharedMobileEpubLocalTts()
    DisposableEffect(popupTts) {
        onDispose {
            if (popupTts.isSessionActive) popupTts.stop()
        }
    }
    val definitionTitle = readerString("ai_definition_title", "AI Definition")
    val plainText = remember(result.text) {
        readerMarkdownPlainText(result.text).ifBlank { result.text.trim() }
    }
    val ttsChunks = remember(plainText, word) {
        sharedMobileAiTtsChunks(plainText, word.orEmpty())
    }
    val currentSpoken = popupTts.progress.currentChunk?.spokenText
        .takeIf { popupTts.progress.isActive }
    val isTtsSessionActive = popupTts.progress.isActive ||
        popupTts.state == SharedMobileEpubLocalTtsState.SPEAKING
    val copyLabel = readerString("action_copy", "Copy")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(modifier = Modifier.padding(all = 20.dp)) {
            if (result.isLoading && plainText.isBlank() && result.errorMessage.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(
                        readerString("ai_thinking", "Thinking…"),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                word?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (plainText.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isTtsSessionActive) {
                                    popupTts.stop()
                                } else if (ttsChunks.isNotEmpty()) {
                                    popupTts.start(
                                        chunks = ttsChunks,
                                        bookTitle = definitionTitle,
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
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { writeSharedClipboard(copyLabel, plainText) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = readerString("action_copy", "Copy")
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { word?.let(onOpenExternalDictionary) }) {
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val errorText = result.errorMessage
                if (errorText != null && plainText.isBlank()) {
                    Text(
                        errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (plainText.isNotBlank()) {
                    // Android parity: keep streaming chunks visible while the
                    // rest generates (no nested loading row once text exists).
                    if (result.isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 12.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                readerString("ai_thinking", "Thinking…"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SharedMarkdownText(
                        markdown = result.text,
                        style = MaterialTheme.typography.bodyLarge,
                        highlightText = currentSpoken,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    Text(
                        readerString("ai_no_definition", "No definition found."),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            // Bottom-sheet equivalent of Android's outside-tap/back dismiss is
            // the sheet drag + scrim; keep content bottom-padded for the handle.
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
    }
}
