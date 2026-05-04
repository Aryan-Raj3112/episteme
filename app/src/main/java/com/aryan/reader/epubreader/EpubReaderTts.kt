/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
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
    webViewRef: WebView?,
    loadedChunkCount: Int,
    totalChunksInChapter: Int,
    paginator: IPaginator?,
    pagerState: PagerState,
    ttsChapterIndex: Int?,
    onTtsChapterIndexChange: (Int?) -> Unit,
    onNavigateToChapter: (Int) -> Unit,
    onToggleTtsStartOnLoad: (Boolean) -> Unit,
    userStoppedTts: Boolean,
    scope: CoroutineScope,
    currentTtsMode: TtsMode,
    getAuthToken: suspend () -> String?,
    locatorConverter: com.aryan.reader.paginatedreader.LocatorConverter, // NEW
    epubBook: com.aryan.reader.epub.EpubBook // NEW
) {
    val currentRenderModeState = rememberUpdatedState(currentRenderMode)
    val loadedChunkCountState = rememberUpdatedState(loadedChunkCount)
    val totalChunksInChapterState = rememberUpdatedState(totalChunksInChapter)
    val ttsChapterIndexState = rememberUpdatedState(ttsChapterIndex)
    val userStoppedTtsState = rememberUpdatedState(userStoppedTts)
    val chaptersState = rememberUpdatedState(chapters)
    val webViewRefState = rememberUpdatedState(webViewRef)
    val paginatorState = rememberUpdatedState(paginator)
    val pagerStateState = rememberUpdatedState(pagerState)
    val onToggleTtsStartOnLoadState = rememberUpdatedState(onToggleTtsStartOnLoad)
    val onNavigateToChapterState = rememberUpdatedState(onNavigateToChapter)
    val onTtsChapterIndexChangeState = rememberUpdatedState(onTtsChapterIndexChange)
    val locatorConverterState = rememberUpdatedState(locatorConverter) // NEW
    val epubBookState = rememberUpdatedState(epubBook) // NEW

    DisposableEffect(ttsController) {
        val job = scope.launch {
            var wasPlaying = false
            var wasSessionFinished = false

            ttsController.ttsState.collect { currentState ->
                val isPlaying = currentState.isPlaying
                val sessionFinished = currentState.sessionFinished
                val sessionEndedByStop = currentState.sessionEndedByStop
                val isReaderSource = currentState.playbackSource == "READER"

                if (isReaderSource) {
                    if (sessionFinished && !wasSessionFinished) {
                        Timber.tag("TTS_CHAPTER_CHANGE_DIAG").i("TTS finished naturally. Checking for next content.")

                        if (currentRenderModeState.value == RenderMode.VERTICAL_SCROLL) {
                            handleVerticalAutoAdvance(
                                webViewRef = webViewRefState.value,
                                loadedChunkCount = loadedChunkCountState.value,
                                totalChunksInChapter = totalChunksInChapterState.value,
                                currentTtsChapterIndex = ttsChapterIndexState.value,
                                totalChapters = chaptersState.value.size,
                                onNavigateToNextChapter = { nextIndex ->
                                    onToggleTtsStartOnLoadState.value(false)
                                    onNavigateToChapterState.value(nextIndex)
                                },
                                onUpdateTtsChapter = onTtsChapterIndexChangeState.value,
                                onStopTts = { onTtsChapterIndexChangeState.value(null) },
                                chapters = chaptersState.value,
                                currentTtsMode = currentTtsMode,
                                epubBookTitle = epubBookTitle,
                                coverImagePath = coverImagePath,
                                getAuthToken = getAuthToken,
                                ttsController = ttsController,
                                scope = this,
                                locatorConverter = locatorConverterState.value,
                                epubBook = epubBookState.value
                            )
                        } else if (currentRenderModeState.value == RenderMode.PAGINATED) {
                            handlePaginatedAutoAdvance(
                                ttsController = ttsController,
                                paginator = paginatorState.value,
                                pagerState = pagerStateState.value,
                                chapters = chaptersState.value,
                                currentTtsChapterIndex = ttsChapterIndexState.value,
                                epubBookTitle = epubBookTitle,
                                coverImagePath = coverImagePath,
                                onUpdateTtsChapter = onTtsChapterIndexChangeState.value,
                                scope = this,
                                ttsMode = currentTtsMode,
                                getAuthToken = getAuthToken
                            )
                        }
                    } else if (wasPlaying && !isPlaying && !sessionFinished) {
                        if (userStoppedTtsState.value || sessionEndedByStop) {
                            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("TTS stopped by user/stop command.")
                            onTtsChapterIndexChangeState.value(null)
                        }
                    }
                }

                wasPlaying = isPlaying
                wasSessionFinished = sessionFinished
            }
        }

        onDispose {
            job.cancel()
        }
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
    currentChapterIndex: Int,
    webViewRef: WebView?,
    paginator: IPaginator?,
    pagerState: PagerState,
    ttsChapterIndex: Int?,
    scope: CoroutineScope
) {
    LaunchedEffect(ttsState.currentText, ttsState.sourceCfi, ttsState.startOffsetInSource, webViewRef) {
        val text = ttsState.currentText
        val cfi = ttsState.sourceCfi
        val offset = ttsState.startOffsetInSource
        val activeTtsChapterIndex = ttsState.chapterIndex ?: ttsChapterIndex

        if (
            currentRenderMode == RenderMode.VERTICAL_SCROLL &&
            activeTtsChapterIndex != null &&
            activeTtsChapterIndex != currentChapterIndex
        ) {
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d(
                "Vertical highlight skipped because visible chapter differs from active TTS chapter. " +
                    "visibleChapter=$currentChapterIndex activeTtsChapter=$activeTtsChapterIndex " +
                    "cfi=${cfi?.take(48)} offset=$offset"
            )
            webViewRef?.evaluateJavascript("javascript:window.removeHighlight();", null)
            return@LaunchedEffect
        }

        if (!text.isNullOrBlank() && !cfi.isNullOrBlank() && offset != -1) {
            val escapedText = escapeJsString(text)
            val escapedCfi = escapeJsString(cfi)
            val jsCommand = "javascript:window.highlightFromCfi('$escapedCfi', '$escapedText', $offset);"
            if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d(
                    "Applying vertical TTS highlight. visibleChapter=$currentChapterIndex " +
                        "activeTtsChapter=$activeTtsChapterIndex cfi=${cfi.take(48)} " +
                        "offset=$offset textLen=${text.length}"
                )
            }
            webViewRef?.evaluateJavascript(jsCommand, null)
        } else {
            if (!ttsState.isPlaying && !ttsState.isLoading) {
                if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                    Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d(
                        "Removing vertical TTS highlight because playback is idle. " +
                            "visibleChapter=$currentChapterIndex activeTtsChapter=$activeTtsChapterIndex"
                    )
                }
                webViewRef?.evaluateJavascript("javascript:window.removeHighlight();", null)
            }
        }
    }

    LaunchedEffect(ttsState.sourceCfi, ttsState.startOffsetInSource, paginator, ttsChapterIndex) {
        if (currentRenderMode != RenderMode.PAGINATED) return@LaunchedEffect

        val cfi = ttsState.sourceCfi ?: return@LaunchedEffect
        val offset = ttsState.startOffsetInSource.takeIf { it != -1 } ?: return@LaunchedEffect
        val chapterIdx = ttsChapterIndex ?: return@LaunchedEffect
        val pag = paginator ?: return@LaunchedEffect

        val targetPage = pag.findPageForCfiAndOffset(chapterIdx, cfi, offset)

        if (targetPage != null && targetPage != pagerState.currentPage) {
            scope.launch {
                pagerState.scrollToPage(targetPage)
            }
        }
    }
}

// --- Internal Helper Functions ---

@OptIn(UnstableApi::class)
private fun handleVerticalAutoAdvance(
    webViewRef: WebView?,
    loadedChunkCount: Int,
    totalChunksInChapter: Int,
    currentTtsChapterIndex: Int?,
    totalChapters: Int,
    onNavigateToNextChapter: (Int) -> Unit,
    onUpdateTtsChapter: (Int?) -> Unit,
    onStopTts: () -> Unit,
    chapters: List<EpubChapter>,
    currentTtsMode: TtsMode,
    epubBookTitle: String,
    coverImagePath: String?,
    getAuthToken: suspend () -> String?,
    ttsController: TtsController,
    scope: CoroutineScope,
    locatorConverter: com.aryan.reader.paginatedreader.LocatorConverter,
    epubBook: com.aryan.reader.epub.EpubBook
) {
    if (currentTtsChapterIndex == null) return

    scope.launch {
        val currentState = ttsController.ttsState.value
        val lastReadCfi = currentState.sourceCfi

        if (loadedChunkCount < totalChunksInChapter) {
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Vertical: Loading remaining text of current chapter natively.")
            val nativeChunks = locatorConverter.getTtsChunksForChapter(epubBook, currentTtsChapterIndex)

            if (!nativeChunks.isNullOrEmpty() && lastReadCfi != null) {
                val lastCfiPath = lastReadCfi.split(":")[0]
                val resumeIdx = nativeChunks.indexOfLast { it.sourceCfi.split(":")[0] == lastCfiPath }

                if (resumeIdx != -1 && resumeIdx + 1 < nativeChunks.size) {
                    val remainingChunks = nativeChunks.subList(resumeIdx + 1, nativeChunks.size)
                    val token = getAuthToken()
                    ttsController.start(
                        chunks = remainingChunks,
                        bookTitle = epubBookTitle,
                        chapterTitle = chapters.getOrNull(currentTtsChapterIndex)?.title,
                        coverImageUri = coverImagePath?.let { android.net.Uri.fromFile(File(it)).toString() },
                        chapterIndex = currentTtsChapterIndex,
                        totalChapters = chapters.size,
                        continueSession = true,
                        ttsMode = currentTtsMode,
                        playbackSource = "READER",
                        authToken = token
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        webViewRef?.evaluateJavascript("javascript:if(window.virtualization && window.virtualization.loadNextChunk) window.virtualization.loadNextChunk();", null)
                    }
                    return@launch
                }
            }
        }

        var nextIdx = currentTtsChapterIndex + 1
        var foundContent = false

        while (nextIdx < totalChapters) {
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Vertical: Trying chapter $nextIdx natively.")
            val nativeChunks = locatorConverter.getTtsChunksForChapter(epubBook, nextIdx)

            if (!nativeChunks.isNullOrEmpty()) {
                val token = getAuthToken()

                onUpdateTtsChapter(nextIdx)

                ttsController.start(
                    chunks = nativeChunks,
                    bookTitle = epubBookTitle,
                    chapterTitle = chapters.getOrNull(nextIdx)?.title,
                    coverImageUri = coverImagePath?.let { Uri.fromFile(File(it)).toString() },
                    chapterIndex = nextIdx,
                    totalChapters = chapters.size,
                    continueSession = true,
                    ttsMode = currentTtsMode,
                    playbackSource = "READER",
                    authToken = token
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onNavigateToNextChapter(nextIdx)
                }

                foundContent = true
                break
            } else {
                Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Vertical: Chapter $nextIdx is empty natively. Skipping to next.")
                nextIdx++
            }
        }

        if (!foundContent) {
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Vertical: Reached end of book or no more valid content.")
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
    scope: CoroutineScope,
    ttsMode: TtsMode,
    getAuthToken: suspend () -> String?
) {
    if (currentTtsChapterIndex != null && currentTtsChapterIndex < chapters.size - 1) {
        Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Paginated: Searching for next TTS content...")

        scope.launch {
            var chapterToTry = currentTtsChapterIndex + 1
            var foundContent = false
            val bookPaginator = paginator as? BookPaginator

            if (bookPaginator == null) {
                onUpdateTtsChapter(null)
                return@launch
            }

            while (chapterToTry < chapters.size) {
                val targetPage = bookPaginator.chapterStartPageIndices[chapterToTry]
                if (targetPage != null && pagerState.currentPage != targetPage) {
                    // CHANGED: Fire-and-forget scroll without frame blocking!
                    launch { pagerState.scrollToPage(targetPage) }
                }

                val nextChapterChunks = bookPaginator.getTtsChunksForChapter(chapterToTry)

                if (!nextChapterChunks.isNullOrEmpty()) {
                    Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Paginated: Found content in chapter $chapterToTry. Starting.")
                    onUpdateTtsChapter(chapterToTry)

                    val chapterTitle = chapters.getOrNull(chapterToTry)?.title
                    val coverUriString = coverImagePath?.let { Uri.fromFile(File(it)).toString() }
                    val token = getAuthToken()

                    ttsController.start(
                        chunks = nextChapterChunks,
                        bookTitle = epubBookTitle,
                        chapterTitle = chapterTitle,
                        coverImageUri = coverUriString,
                        chapterIndex = chapterToTry,
                        totalChapters = chapters.size,
                        continueSession = true,
                        ttsMode = ttsMode,
                        playbackSource = "READER",
                        authToken = token
                    )
                    foundContent = true
                    break
                } else {
                    Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Paginated: Chapter $chapterToTry is empty. Skipping.")
                    chapterToTry++
                }
            }

            if (!foundContent) {
                Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("Paginated: No more content found.")
                onUpdateTtsChapter(null)
            }
        }
    } else {
        onUpdateTtsChapter(null)
    }
}
