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

import timber.log.Timber
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.RenderMode
import com.aryan.reader.SearchNavigationControls
import com.aryan.reader.SearchResult
import com.aryan.reader.SearchResultsPanel
import com.aryan.reader.SearchState
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.paginatedreader.IPaginator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Creates the search implementation for EPUB chapters.
 */
fun createEpubSearcher(epubBook: EpubBook): suspend (String) -> List<SearchResult> = { query ->
    withContext(Dispatchers.Default) {
        val results = mutableListOf<SearchResult>()
        epubBook.chapters.forEachIndexed { chapterIndex, chapter ->
            try {
                val fullPath = "${epubBook.extractionBasePath}/${chapter.htmlFilePath}"
                val htmlFile = File(fullPath)
                if (!htmlFile.exists()) return@forEachIndexed

                val doc = Jsoup.parse(htmlFile, "UTF-8")
                val bodyChildren = doc.body().children().toList()
                val chunks = bodyChildren.chunked(20)

                chunks.forEachIndexed { chunkIndex, chunkOfElements ->
                    val chunkHtml = chunkOfElements.joinToString(separator = "\n") { it.outerHtml() }
                    val content = Jsoup.parse(chunkHtml).text()
                    var lastIndex = -1

                    while (true) {
                        lastIndex = content.indexOf(query, startIndex = lastIndex + 1, ignoreCase = true)
                        if (lastIndex == -1) break

                        val isWordStart = lastIndex == 0 || !content[lastIndex - 1].isLetterOrDigit()
                        if (isWordStart) {
                            val snippetStart = max(0, lastIndex - 35)
                            val snippetEnd = min(content.length, lastIndex + query.length + 35)
                            val rawSnippet = content.substring(snippetStart, snippetEnd)
                            val annotatedSnippet = buildAnnotatedString {
                                append(rawSnippet)
                                val highlightStart = content.indexOf(query, lastIndex, ignoreCase = true) - snippetStart
                                val highlightEnd = highlightStart + query.length
                                addStyle(
                                    style = SpanStyle(fontWeight = FontWeight.Bold),
                                    start = highlightStart,
                                    end = highlightEnd
                                )
                            }
                            results.add(
                                SearchResult(
                                    locationInSource = chapterIndex,
                                    locationTitle = chapter.title,
                                    snippet = annotatedSnippet,
                                    query = query,
                                    occurrenceIndexInLocation = results.count { it.locationInSource == chapterIndex },
                                    chunkIndex = chunkIndex
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e("Failed to search in chapter $chapterIndex", e)
            }
        }
        results
    }
}

/**
 * Handles the navigation to a specific search result.
 */
fun performSearchResultNavigation(
    index: Int,
    searchState: SearchState,
    renderMode: RenderMode,
    currentChapterIndex: Int,
    loadedChunkCount: Int,
    webView: WebView?,
    paginator: IPaginator?,
    coroutineScope: CoroutineScope,
    onVerticalChapterChange: (chapterIndex: Int, chunkIndex: Int, result: SearchResult) -> Unit,
    onVerticalScrollToResult: (result: SearchResult) -> Unit,
    onPaginatedScrollToPage: suspend (pageIndex: Int) -> Unit
) {
    if (index !in searchState.searchResults.indices) return

    val result = searchState.searchResults[index]
    searchState.currentSearchResultIndex = index

    when (renderMode) {
        RenderMode.VERTICAL_SCROLL -> {
            if (currentChapterIndex != result.locationInSource) {
                onVerticalChapterChange(result.locationInSource, result.chunkIndex, result)
            } else {
                if (result.chunkIndex >= loadedChunkCount) {
                    onVerticalChapterChange(result.locationInSource, result.chunkIndex, result)
                } else {
                    webView?.let {
                        val js = "javascript:window.scrollToOccurrence(${result.occurrenceIndexInLocation});"
                        it.evaluateJavascript(js, null)
                    }
                    onVerticalScrollToResult(result)
                }
            }
        }

        RenderMode.PAGINATED -> {
            paginator?.findPageForSearchResult(result) { pageIndex ->
                coroutineScope.launch {
                    onPaginatedScrollToPage(pageIndex)
                }
            }
        }
    }
}

@Composable
fun EpubReaderSearchEffects(
    searchState: SearchState,
    webViewRef: WebView?,
    currentChapterIndex: Int,
    focusRequester: FocusRequester
) {
    // 1. Auto-Highlight in WebView
    LaunchedEffect(searchState.searchResults, currentChapterIndex) {
        val query = searchState.searchQuery
        if (query.isBlank()) {
            webViewRef?.evaluateJavascript("javascript:window.clearSearchHighlights();", null)
            return@LaunchedEffect
        }

        val resultsInCurrentChapter = searchState.searchResults.any { it.locationInSource == currentChapterIndex }
        if (resultsInCurrentChapter) {
            webViewRef?.let { webView ->
                val escapedQuery = escapeJsString(query)
                val js = "javascript:window.highlightAllOccurrences('${escapedQuery}');"
                Timber.d("Highligting: $js")
                webView.evaluateJavascript(js, null)
            }
        } else {
            webViewRef?.evaluateJavascript("javascript:window.clearSearchHighlights();", null)
        }
    }

    // 2. Focus Management
    LaunchedEffect(searchState.isSearchActive) {
        if (searchState.isSearchActive) {
            delay(100)
            focusRequester.requestFocus()
        } else {
            webViewRef?.evaluateJavascript("javascript:window.clearSearchHighlights();", null)
        }
    }
}

@Composable
fun EpubReaderSearchOverlay(
    searchState: SearchState,
    onNavigateResult: (Int) -> Unit,
    bottomPadding: Dp
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {

        // Search Results Panel
        AnimatedVisibility(
            visible = searchState.isSearchActive && searchState.showSearchResultsPanel,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            SearchResultsPanel(
                results = searchState.searchResults,
                isSearching = searchState.isSearchInProgress,
                onResultClick = { result ->
                    val resultIndex = searchState.searchResults.indexOf(result)
                    if (resultIndex != -1) {
                        onNavigateResult(resultIndex)
                    }
                    searchState.showSearchResultsPanel = false
                    keyboardController?.hide()
                },
                modifier = Modifier.padding(top = 50.dp)
            )
        }

        AnimatedVisibility(
            visible = searchState.isSearchActive && !searchState.showSearchResultsPanel && searchState.hasResults,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding + 45.dp + 16.dp, end = 16.dp)
        ) {
            SearchNavigationControls(
                searchState = searchState,
                onNavigate = { index -> onNavigateResult(index) }
            )
        }
    }
}