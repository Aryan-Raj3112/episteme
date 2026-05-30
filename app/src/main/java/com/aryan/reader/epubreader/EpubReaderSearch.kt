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
import com.aryan.reader.epub.contentFilePath
import com.aryan.reader.paginatedreader.IPaginator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val EPUB_SEARCH_WINDOW_CHARS = 32_768
private const val EPUB_SEARCH_SNIPPET_RADIUS = 35
private const val EPUB_SEARCH_MAX_OVERLAP_CHARS = 4_096

private val epubSearchSkippedTags = setOf("script", "style", "noscript")
private val epubSearchBlockBoundaryTags = setOf(
    "address",
    "article",
    "aside",
    "blockquote",
    "br",
    "caption",
    "dd",
    "div",
    "dl",
    "dt",
    "figcaption",
    "figure",
    "footer",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hr",
    "li",
    "main",
    "nav",
    "ol",
    "p",
    "pre",
    "section",
    "table",
    "td",
    "th",
    "tr",
    "ul"
)

/**
 * Creates the search implementation for EPUB chapters.
 */
fun createEpubSearcher(epubBook: EpubBook): suspend (String) -> List<SearchResult> = { query ->
    withContext(Dispatchers.Default) {
        val searchQuery = query.trim()
        if (searchQuery.isBlank()) {
            return@withContext emptyList()
        }

        val results = mutableListOf<SearchResult>()
        epubBook.chapters.forEachIndexed { chapterIndex, chapter ->
            try {
                val htmlFile = File(epubBook.extractionBasePath, chapter.contentFilePath())
                if (!htmlFile.exists()) return@forEachIndexed

                val doc = Jsoup.parse(htmlFile, "UTF-8")
                doc.select("script, style, noscript").remove()
                val bodyNodes = doc.body().childNodes().toList()
                val chunks = bodyNodes.chunked(20)
                var occurrenceIndexInChapter = 0

                chunks.forEachIndexed { chunkIndex, chunkNodes ->
                    occurrenceIndexInChapter = appendSearchResultsFromNodes(
                        nodes = chunkNodes,
                        query = searchQuery,
                        chapterIndex = chapterIndex,
                        chapterTitle = chapter.title,
                        chunkIndex = chunkIndex,
                        occurrenceIndexInChapter = occurrenceIndexInChapter,
                        results = results
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to search in chapter $chapterIndex")
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "Skipping search in chapter $chapterIndex after running out of memory")
            }
        }
        results
    }
}

private fun appendSearchResultsFromNodes(
    nodes: List<Node>,
    query: String,
    chapterIndex: Int,
    chapterTitle: String,
    chunkIndex: Int,
    occurrenceIndexInChapter: Int,
    results: MutableList<SearchResult>
): Int {
    val searchWindow = EpubSearchWindow(
        query = query,
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        chunkIndex = chunkIndex,
        initialOccurrenceIndex = occurrenceIndexInChapter,
        results = results
    )
    nodes.forEach { node ->
        searchWindow.visit(node)
    }
    searchWindow.finish()
    return searchWindow.occurrenceIndex
}

private class EpubSearchWindow(
    private val query: String,
    private val chapterIndex: Int,
    private val chapterTitle: String,
    private val chunkIndex: Int,
    initialOccurrenceIndex: Int,
    private val results: MutableList<SearchResult>
) {
    private val buffer = StringBuilder()
    private val overlapChars = (query.length + EPUB_SEARCH_SNIPPET_RADIUS)
        .coerceIn(EPUB_SEARCH_SNIPPET_RADIUS * 2, EPUB_SEARCH_MAX_OVERLAP_CHARS)
    private var lastAppendedWasWhitespace = true
    private var previousCharBeforeBuffer: Char? = null

    var occurrenceIndex: Int = initialOccurrenceIndex
        private set

    fun visit(node: Node) {
        when (node) {
            is TextNode -> appendNormalizedText(node.wholeText)
            is Element -> {
                val tagName = node.tagName().lowercase()
                if (tagName in epubSearchSkippedTags) return

                if (tagName == "br") {
                    appendNormalizedWhitespace()
                    return
                }

                node.childNodes().forEach(::visit)
                if (tagName in epubSearchBlockBoundaryTags) {
                    appendNormalizedWhitespace()
                }
            }
            else -> node.childNodes().forEach(::visit)
        }
    }

    fun finish() {
        scanBuffer(buffer.length)
        buffer.clear()
        previousCharBeforeBuffer = null
    }

    private fun appendNormalizedText(text: String) {
        text.forEach { char ->
            if (char.isWhitespace()) {
                appendNormalizedWhitespace()
            } else {
                buffer.append(char)
                lastAppendedWasWhitespace = false
                trimScannedPrefixIfNeeded()
            }
        }
    }

    private fun appendNormalizedWhitespace() {
        if (buffer.isEmpty() || lastAppendedWasWhitespace) {
            lastAppendedWasWhitespace = true
            return
        }
        buffer.append(' ')
        lastAppendedWasWhitespace = true
        trimScannedPrefixIfNeeded()
    }

    private fun trimScannedPrefixIfNeeded() {
        if (buffer.length < EPUB_SEARCH_WINDOW_CHARS) return

        val scanEndExclusive = (buffer.length - overlapChars).coerceAtLeast(0)
        if (scanEndExclusive <= 0) return

        scanBuffer(scanEndExclusive)
        previousCharBeforeBuffer = buffer[scanEndExclusive - 1]
        buffer.delete(0, scanEndExclusive)
    }

    private fun scanBuffer(scanEndExclusive: Int) {
        var searchFrom = 0
        while (searchFrom < scanEndExclusive) {
            val matchStart = buffer.indexOfIgnoreCase(query, searchFrom, scanEndExclusive)
            if (matchStart == -1) break

            if (isWordStart(matchStart)) {
                addSearchResult(matchStart)
            }
            searchFrom = matchStart + 1
        }
    }

    private fun isWordStart(matchStart: Int): Boolean {
        val previousChar = if (matchStart > 0) {
            buffer[matchStart - 1]
        } else {
            previousCharBeforeBuffer
        }
        return previousChar == null || !previousChar.isLetterOrDigit()
    }

    private fun addSearchResult(matchStart: Int) {
        val snippetStart = max(0, matchStart - EPUB_SEARCH_SNIPPET_RADIUS)
        val snippetEnd = min(buffer.length, matchStart + query.length + EPUB_SEARCH_SNIPPET_RADIUS)
        val rawSnippet = buffer.substring(snippetStart, snippetEnd)
        val highlightStart = matchStart - snippetStart
        val highlightEnd = highlightStart + query.length
        val annotatedSnippet = buildAnnotatedString {
            append(rawSnippet)
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.Bold),
                start = highlightStart,
                end = highlightEnd
            )
        }

        results.add(
            SearchResult(
                locationInSource = chapterIndex,
                locationTitle = chapterTitle,
                snippet = annotatedSnippet,
                query = query,
                occurrenceIndexInLocation = occurrenceIndex,
                chunkIndex = chunkIndex
            )
        )
        occurrenceIndex++
    }
}

private fun CharSequence.indexOfIgnoreCase(
    query: String,
    startIndex: Int,
    matchStartLimitExclusive: Int
): Int {
    if (query.isEmpty()) return -1
    val lastStart = min(length - query.length, matchStartLimitExclusive - 1)
    if (lastStart < startIndex) return -1

    var index = startIndex.coerceAtLeast(0)
    while (index <= lastStart) {
        var queryIndex = 0
        while (
            queryIndex < query.length &&
            this[index + queryIndex].equals(query[queryIndex], ignoreCase = true)
        ) {
            queryIndex++
        }
        if (queryIndex == query.length) return index
        index++
    }
    return -1
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
