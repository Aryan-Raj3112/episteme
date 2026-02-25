/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.epubreader

import android.content.Context
import android.net.Uri
import android.os.Build
import timber.log.Timber
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.RenderMode
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.BookPaginator
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.TtsPlaybackManager.TtsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

const val TAG_TTS_DIAGNOSIS = "TTS_DIAGNOSIS"
private const val TTS_MODE_KEY = "tts_mode"

data class TtsHighlightInfo(
    val text: String,
    val cfi: String,
    val offset: Int
)

@Suppress("unused")
@OptIn(UnstableApi::class)
fun saveTtsMode(context: Context, mode: TtsMode) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_MODE_KEY, mode.name) }
}

@Suppress("unused")
@OptIn(UnstableApi::class)
fun loadTtsMode(): TtsMode {
    // For this release, Cloud TTS is disabled. Force BASE mode.
    return TtsMode.BASE
}

/**
 * Helper to update the WebView auto-scroll state.
 */
fun updateAutoScrollJs(webView: WebView?, playing: Boolean, speed: Float) {
    if (playing) {
        val jsCommand = "javascript:window.autoScroll.start($speed);"
        webView?.evaluateJavascript(jsCommand, null)
    } else {
        webView?.evaluateJavascript("javascript:window.autoScroll.stop();", null)
    }
}

/**
 * Logic for triggering the actual TTS start based on the current mode.
 */
fun initiateTtsPlayback(
    renderMode: RenderMode,
    webView: WebView?,
    onPaginatedStart: () -> Unit
) {
    when (renderMode) {
        RenderMode.VERTICAL_SCROLL -> {
            Timber.d("Vertical: requesting text extraction via JS.")
            webView?.evaluateJavascript("javascript:TtsBridgeHelper.extractAndRelayText();", null)
        }
        RenderMode.PAGINATED -> {
            onPaginatedStart()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(UnstableApi::class)
@Composable
fun TtsSessionObserver(
    ttsState: TtsPlaybackManager.TtsState,
    ttsController: TtsController,
    currentRenderMode: RenderMode,
    chapters: List<EpubChapter>,
    epubBookTitle: String,
    coverImagePath: String?,
    // Vertical Mode Dependencies
    webViewRef: WebView?,
    loadedChunkCount: Int,
    totalChunksInChapter: Int,
    // Paginated Mode Dependencies
    paginator: IPaginator?,
    pagerState: PagerState,
    ttsChapterIndex: Int?,
    onTtsChapterIndexChange: (Int?) -> Unit,
    onNavigateToChapter: (Int) -> Unit,
    onToggleTtsStartOnLoad: (Boolean) -> Unit,
    userStoppedTts: Boolean,
    scope: CoroutineScope
) {
    val prevTtsState = remember { mutableStateOf(ttsState) }

    LaunchedEffect(ttsState) {
        val wasPlaying = prevTtsState.value.isPlaying
        val isPlaying = ttsState.isPlaying
        val isChangingConfig = ttsState.isChangingConfig
        val sessionFinished = ttsState.sessionFinished
        val wasSessionFinished = prevTtsState.value.sessionFinished
        val sessionEndedByStop = ttsState.sessionEndedByStop
        val isReaderSource = ttsState.playbackSource == "READER"

        if (!isChangingConfig && isReaderSource) {
            if (sessionFinished && !wasSessionFinished) {
                Timber.d("TTS finished naturally. Checking for next content.")

                if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                    handleVerticalAutoAdvance(
                        webViewRef = webViewRef,
                        loadedChunkCount = loadedChunkCount,
                        totalChunksInChapter = totalChunksInChapter,
                        currentTtsChapterIndex = ttsChapterIndex,
                        totalChapters = chapters.size,
                        onNavigateToNextChapter = { nextIndex ->
                            onToggleTtsStartOnLoad(true)
                            onNavigateToChapter(nextIndex)
                        },
                        onStopTts = { onTtsChapterIndexChange(null) }
                    )
                } else if (currentRenderMode == RenderMode.PAGINATED) {
                    handlePaginatedAutoAdvance(
                        ttsController = ttsController,
                        paginator = paginator,
                        pagerState = pagerState,
                        chapters = chapters,
                        currentTtsChapterIndex = ttsChapterIndex,
                        epubBookTitle = epubBookTitle,
                        coverImagePath = coverImagePath,
                        onUpdateTtsChapter = onTtsChapterIndexChange,
                        scope = scope
                    )
                }
            } else if (wasPlaying && !isPlaying && !sessionFinished) {
                // Playback stopped/paused
                if (userStoppedTts || sessionEndedByStop) {
                    Timber.d("TTS stopped by user/stop command.")
                    onTtsChapterIndexChange(null)
                }
            }
        }
        prevTtsState.value = ttsState
    }
}

/**
 * Handles highlighting text in WebView (Vertical) or turning pages (Paginated)
 * based on playback progress.
 */
@OptIn(UnstableApi::class)
@Composable
fun TtsHighlightHandler(
    ttsState: TtsPlaybackManager.TtsState,
    currentRenderMode: RenderMode,
    webViewRef: WebView?,
    paginator: IPaginator?,
    pagerState: PagerState,
    ttsChapterIndex: Int?,
    scope: CoroutineScope
) {
    // 1. Vertical & General Highlighting (WebView)
    LaunchedEffect(ttsState.currentText, ttsState.sourceCfi, ttsState.startOffsetInSource, webViewRef) {
        val text = ttsState.currentText
        val cfi = ttsState.sourceCfi
        val offset = ttsState.startOffsetInSource

        if (!text.isNullOrBlank() && !cfi.isNullOrBlank() && offset != -1) {
            val escapedText = escapeJsString(text)
            val escapedCfi = escapeJsString(cfi)
            // Use window.highlightFromCfi defined in epub_reader.js
            val jsCommand = "javascript:window.highlightFromCfi('$escapedCfi', '$escapedText', $offset);"
            webViewRef?.evaluateJavascript(jsCommand, null)
        } else {
            if (!ttsState.isPlaying && !ttsState.isLoading) {
                webViewRef?.evaluateJavascript("javascript:window.removeHighlight();", null)
            }
        }
    }

    // 2. Paginated Page Turning (Sentence/Fragment level)
    LaunchedEffect(ttsState.sourceCfi, ttsState.startOffsetInSource, paginator, ttsChapterIndex) {
        if (currentRenderMode != RenderMode.PAGINATED) return@LaunchedEffect

        val cfi = ttsState.sourceCfi ?: return@LaunchedEffect
        val offset = ttsState.startOffsetInSource.takeIf { it != -1 } ?: return@LaunchedEffect
        val chapterIdx = ttsChapterIndex ?: return@LaunchedEffect
        val pag = paginator ?: return@LaunchedEffect

        val targetPage = pag.findPageForCfiAndOffset(chapterIdx, cfi, offset)

        if (targetPage != null && targetPage != pagerState.currentPage) {
            // Prevent backward jumps during reading (unless significant) to avoid jitter
            if (targetPage >= pagerState.currentPage) {
                scope.launch {
                    pagerState.animateScrollToPage(targetPage)
                }
            }
        }
    }
}

// --- Internal Helper Functions ---

private fun handleVerticalAutoAdvance(
    webViewRef: WebView?,
    loadedChunkCount: Int,
    totalChunksInChapter: Int,
    currentTtsChapterIndex: Int?,
    totalChapters: Int,
    onNavigateToNextChapter: (Int) -> Unit,
    onStopTts: () -> Unit
) {
    if (loadedChunkCount < totalChunksInChapter) {
        Timber.d("Vertical: Loading next chunk for TTS.")
        webViewRef?.evaluateJavascript("javascript:window.virtualization.loadNextChunk();", null)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            webViewRef?.evaluateJavascript("javascript:TtsBridgeHelper.extractAndRelayText();", null)
        }, 500)
    } else {
        if (currentTtsChapterIndex != null && currentTtsChapterIndex < totalChapters - 1) {
            Timber.d("Vertical: Chapter finished, moving to next.")
            onNavigateToNextChapter(currentTtsChapterIndex + 1)
        } else {
            Timber.d("Vertical: End of book.")
            onStopTts()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(UnstableApi::class)
private fun handlePaginatedAutoAdvance(
    ttsController: TtsController,
    paginator: IPaginator?,
    pagerState: PagerState,
    chapters: List<EpubChapter>,
    currentTtsChapterIndex: Int?,
    epubBookTitle: String,
    coverImagePath: String?,
    onUpdateTtsChapter: (Int?) -> Unit,
    scope: CoroutineScope
) {
    val lastPlayedChapter = currentTtsChapterIndex
    if (lastPlayedChapter != null && lastPlayedChapter < chapters.size - 1) {
        Timber.d("Paginated: Searching for next TTS content...")

        scope.launch {
            var chapterToTry = lastPlayedChapter + 1
            var foundContent = false
            val bookPaginator = paginator as? BookPaginator

            if (bookPaginator == null) {
                onUpdateTtsChapter(null)
                return@launch
            }

            while (chapterToTry < chapters.size) {
                // Visually scroll to start of chapter
                val targetPage = bookPaginator.chapterStartPageIndices[chapterToTry]
                if (targetPage != null && pagerState.currentPage != targetPage) {
                    pagerState.animateScrollToPage(targetPage)
                    delay(300)
                }

                val nextChapterChunks = bookPaginator.getTtsChunksForChapter(chapterToTry)

                if (!nextChapterChunks.isNullOrEmpty()) {
                    Timber.d("Paginated: Found content in chapter $chapterToTry. Starting.")
                    onUpdateTtsChapter(chapterToTry)

                    val chapterTitle = chapters.getOrNull(chapterToTry)?.title
                    val coverUriString = coverImagePath?.let { Uri.fromFile(File(it)).toString() }

                    ttsController.start(
                        chunks = nextChapterChunks,
                        bookTitle = epubBookTitle,
                        chapterTitle = chapterTitle,
                        coverImageUri = coverUriString,
                        ttsMode = "BASE" // Defaulting to Base for safety
                    )
                    foundContent = true
                    break
                } else {
                    Timber.d("Paginated: Chapter $chapterToTry is empty. Skipping.")
                    // Visually flip through empty pages if needed
                    val pageCount = bookPaginator.chapterPageCounts[chapterToTry] ?: 0
                    if (pageCount > 1) {
                        for (i in 1 until pageCount) {
                            pagerState.animateScrollToPage(targetPage!! + i)
                            delay(400)
                        }
                    }
                    chapterToTry++
                }
            }

            if (!foundContent) {
                Timber.d("Paginated: No more content found.")
                onUpdateTtsChapter(null)
            }
        }
    } else {
        onUpdateTtsChapter(null)
    }
}