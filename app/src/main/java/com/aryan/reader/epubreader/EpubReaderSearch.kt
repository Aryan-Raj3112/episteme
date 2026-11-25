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
import com.aryan.reader.applyBookReplacementsToHtmlDocument
import com.aryan.reader.shared.READER_SEARCH_BLOCK_BOUNDARY_TAGS
import com.aryan.reader.shared.READER_SEARCH_SKIPPED_TAGS
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.SearchResult
import com.aryan.reader.SearchResultsPanel
import com.aryan.reader.shared.ReaderSearchState as SearchState
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.contentFilePath
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.paginatedreader.sharedReaderBodyChunks
import com.aryan.reader.shared.readerSearchMatchQuote
import com.aryan.reader.shared.readerWordStartMatchOffsets
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

/**
 * Creates the search implementation for EPUB chapters.
 *
 * Chunking and matching come from `shared/` so the hits this produces are in
 * the reader's own text and chunk spaces: the same chunker that renders the
 * chapter, the same word-start rule the paginated locator uses, and the same
 * book replacements the reader applies. Footnote/endnote markup is ordinary
 * chapter text — what used to break was that each consumer counted occurrences
 * in a slightly different text, so one extra superscript marker upstream moved
 * every later hit to a neighbouring page.
 */
fun createEpubSearcher(
    epubBook: EpubBook,
    replacementPreferences: () -> ReaderBookReplacementPreferences = { ReaderBookReplacementPreferences() },
    replacementFileId: String? = null,
): suspend (String) -> List<SearchResult> = { query ->
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
                // Remove exactly what the reader removes before it chunks the
                // body, so hit chunk indexes stay in the rendered chunk space.
                // The skipped tags themselves contribute no text (see
                // EpubSearchWindow.visit), so they stay for chunk parity.
                doc.select("script").remove()
                // The reader renders replacements, so search must see the same
                // text; otherwise every hit after a replaced phrase is offset.
                applyBookReplacementsToHtmlDocument(
                    document = doc,
                    preferences = replacementPreferences(),
                    fileId = replacementFileId,
                )
                val chunks = sharedReaderBodyChunks(doc.body().childNodes().toList())
                var occurrenceIndexInChapter = 0

                chunks.forEachIndexed { chunkIndex, chunk ->
                    occurrenceIndexInChapter = appendSearchResultsFromNodes(
                        nodes = chunk.nodes,
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
                if (tagName in READER_SEARCH_SKIPPED_TAGS) return

                if (tagName == "br") {
                    appendNormalizedWhitespace()
                    return
                }

                node.childNodes().forEach(::visit)
                if (tagName in READER_SEARCH_BLOCK_BOUNDARY_TAGS) {
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

    /**
     * Counts the matches in `[0, scanEndExclusive)` with the shared rule, so the
     * ordinals recorded here mean the same thing to the paginated locator and
     * to the WebView painter.
     */
    private fun scanBuffer(scanEndExclusive: Int) {
        readerWordStartMatchOffsets(
            text = buffer,
            query = query,
            fromIndex = 0,
            endIndex = scanEndExclusive,
            charBeforeText = previousCharBeforeBuffer,
        ).forEach(::addSearchResult)
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
                chunkIndex = chunkIndex,
                matchQuote = readerSearchMatchQuote(buffer, matchStart, query.length),
            )
        )
        occurrenceIndex++
    }
}

/**
 * Paints the query and scrolls to [result]'s own occurrence in the WebView.
 *
 * The scroll target is the hit's quote, not only its per-chunk ordinal: the
 * ordinal is trustworthy only while the searched text and the painted text are
 * identical, and the quote is what keeps a divergent chapter (footnote/endnote
 * markup, a replacement, a placeholder) on the intended occurrence.
 */
internal fun epubSearchHighlightAndScrollScript(
    results: List<SearchResult>,
    result: SearchResult,
): String {
    val relativeIndex = results
        .filter { it.locationInSource == result.locationInSource && it.chunkIndex == result.chunkIndex }
        .indexOf(result)
        .coerceAtLeast(0)

    return buildString {
        append("javascript:window.CURRENT_SEARCH_QUERY = '")
        append(escapeJsString(result.query))
        append("'; window.highlightAllOccurrences(window.CURRENT_SEARCH_QUERY); ")
        append("window.scrollToChunkOccurrence(")
        append(result.chunkIndex)
        append(", ")
        append(relativeIndex)
        if (result.matchQuote.isNotBlank()) {
            append(", '")
            append(escapeJsString(result.matchQuote))
            append("'")
        }
        append(");")
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
                    webView?.evaluateJavascript(
                        epubSearchHighlightAndScrollScript(searchState.searchResults, result),
                        null
                    )
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
    focusRequester: FocusRequester,
    autoFocus: Boolean = true,
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

    // 2. Focus Management (deep-links suppress the initial focus so the
    // keyboard doesn't pop unprompted; manual opens re-enable it).
    LaunchedEffect(searchState.isSearchActive, autoFocus) {
        if (searchState.isSearchActive) {
            if (autoFocus) {
                delay(100)
                focusRequester.requestFocus()
            }
        } else {
            webViewRef?.evaluateJavascript("javascript:window.clearSearchHighlights();", null)
        }
    }
}

@Composable
fun EpubReaderSearchOverlay(
    searchState: SearchState,
    onNavigateResult: (Int) -> Unit,
    bottomPadding: Dp,
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
                // The drop-down hangs off the tool row's top edge (the host layer
                // already sits at the reader's chrome origin, same as the top bar),
                // so the fixed offset needs no inset of its own.
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
