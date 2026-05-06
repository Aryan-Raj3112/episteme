package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.SearchHighlightMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SharedPdfSearchResult(
    val pageIndex: Int,
    val preview: String,
    val matchIndex: Int,
    val matchLength: Int = 0
)

@Serializable
data class SharedPdfBookmark(
    val pageIndex: Int,
    val label: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class SharedPdfBookmarkStore(
    val version: Int = 1,
    val bookmarks: List<SharedPdfBookmark> = emptyList()
)

object SharedPdfBookmarkSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(bookmarks: List<SharedPdfBookmark>): String {
        return json.encodeToString(SharedPdfBookmarkStore(bookmarks = bookmarks))
    }

    fun decode(raw: String): List<SharedPdfBookmark> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<SharedPdfBookmarkStore>(raw).bookmarks
        }.getOrElse {
            runCatching { json.decodeFromString<List<SharedPdfBookmark>>(raw) }.getOrDefault(emptyList())
        }
    }
}

data class SharedPdfJumpHistory(
    val pages: List<Int> = emptyList(),
    val cursor: Int = -1,
    val maxEntries: Int = 21
) {
    val backPage: Int? get() = pages.getOrNull(cursor - 1)
    val forwardPage: Int? get() = pages.getOrNull(cursor + 1)
    val hasJumpTargets: Boolean get() = backPage != null || forwardPage != null

    fun record(
        currentPageIndex: Int,
        targetPageIndex: Int,
        pageCount: Int
    ): SharedPdfJumpHistory {
        if (
            pageCount <= 0 ||
            currentPageIndex !in 0 until pageCount ||
            targetPageIndex !in 0 until pageCount ||
            currentPageIndex == targetPageIndex
        ) {
            return this
        }

        val pruned = pruned(pageCount)
        val nextPages = pruned.pages.toMutableList()
        var nextCursor = pruned.cursor

        while (nextPages.lastIndex > nextCursor) {
            nextPages.removeAt(nextPages.lastIndex)
        }

        if (nextCursor > 0 && nextPages.getOrNull(nextCursor - 1) == currentPageIndex) {
            nextPages[nextCursor] = targetPageIndex
            return copy(
                pages = nextPages,
                cursor = nextCursor
            ).bounded()
        }

        if (nextCursor == -1 || nextPages.getOrNull(nextCursor) != currentPageIndex) {
            nextPages += currentPageIndex
            nextCursor = nextPages.lastIndex
        }

        if (nextPages.lastOrNull() != targetPageIndex) {
            nextPages += targetPageIndex
            nextCursor = nextPages.lastIndex
        }

        return copy(
            pages = nextPages,
            cursor = nextCursor
        ).bounded()
    }

    fun pruned(pageCount: Int): SharedPdfJumpHistory {
        if (pageCount <= 0) return clear()
        val nextPages = pages.toMutableList()
        var nextCursor = cursor
        var index = nextPages.lastIndex
        while (index >= 0) {
            if (nextPages[index] !in 0 until pageCount) {
                nextPages.removeAt(index)
                if (nextCursor >= index) nextCursor--
            }
            index--
        }
        return copy(
            pages = nextPages,
            cursor = nextCursor.coerceIn(-1, nextPages.lastIndex)
        ).bounded()
    }

    fun stepBack(): SharedPdfJumpHistory {
        return if (backPage == null) this else copy(cursor = (cursor - 1).coerceAtLeast(0))
    }

    fun stepForward(): SharedPdfJumpHistory {
        return if (forwardPage == null) this else copy(cursor = (cursor + 1).coerceAtMost(pages.lastIndex))
    }

    fun clear(): SharedPdfJumpHistory = copy(pages = emptyList(), cursor = -1)

    private fun bounded(): SharedPdfJumpHistory {
        val safeMaxEntries = maxEntries.coerceAtLeast(2)
        if (pages.size <= safeMaxEntries) {
            return copy(cursor = cursor.coerceIn(-1, pages.lastIndex))
        }
        val overflow = pages.size - safeMaxEntries
        return copy(
            pages = pages.drop(overflow),
            cursor = (cursor - overflow).coerceIn(-1, pages.size - overflow - 1)
        )
    }
}

data class SharedPdfReaderState(
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val displayMode: PdfDisplayMode = PdfDisplayMode.PAGINATION,
    val zoom: Float = PdfZoomSpec().default,
    val searchQuery: String = "",
    val activeSearchResultIndex: Int = -1,
    val searchHighlightMode: SearchHighlightMode = SearchHighlightMode.ALL,
    val selectedTool: PdfInkTool = PdfInkTool.PEN,
    val selectedColorArgb: Int = SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).colorArgb,
    val strokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).strokeWidth,
    val isTextSelectionMode: Boolean = false,
    val bookmarks: List<SharedPdfBookmark> = emptyList(),
    val selectedAnnotationId: String? = null,
    val annotations: List<SharedPdfAnnotation> = emptyList()
) {
    val safePageCount: Int get() = pageCount.coerceAtLeast(0)
    val lastPageIndex: Int get() = (safePageCount - 1).coerceAtLeast(0)
    val canGoPrevious: Boolean get() = pageIndex > 0
    val canGoNext: Boolean get() = pageIndex < lastPageIndex
    val progressPercent: Float get() = ((pageIndex + 1).toFloat() / safePageCount.coerceAtLeast(1)) * 100f

    fun coerced(zoomSpec: PdfZoomSpec = PdfZoomSpec()): SharedPdfReaderState {
        val safePage = pageIndex.coerceIn(0, lastPageIndex)
        return copy(
            pageIndex = safePage,
            pageCount = safePageCount,
            activeSearchResultIndex = activeSearchResultIndex.coerceAtLeast(-1),
            zoom = zoomSpec.clamp(zoom),
            bookmarks = bookmarks.normalizedBookmarks(lastPageIndex),
            selectedAnnotationId = selectedAnnotationId?.takeIf { selectedId ->
                annotations.any { it.id == selectedId }
            }
        )
    }

    companion object {
        fun initial(
            pageCount: Int,
            initialPageIndex: Int = 0,
            zoomSpec: PdfZoomSpec = PdfZoomSpec()
        ): SharedPdfReaderState {
            val safePageCount = pageCount.coerceAtLeast(0)
            val lastPageIndex = (safePageCount - 1).coerceAtLeast(0)
            return SharedPdfReaderState(
                pageIndex = initialPageIndex.coerceIn(0, lastPageIndex),
                pageCount = safePageCount,
                zoom = zoomSpec.clamp(zoomSpec.default)
            )
        }
    }
}

sealed interface SharedPdfReaderAction {
    data class GoToPage(val pageIndex: Int) : SharedPdfReaderAction
    data object PreviousPage : SharedPdfReaderAction
    data object NextPage : SharedPdfReaderAction
    data object FirstPage : SharedPdfReaderAction
    data object LastPage : SharedPdfReaderAction
    data class DisplayModeChanged(val mode: PdfDisplayMode) : SharedPdfReaderAction
    data object DisplayModeToggled : SharedPdfReaderAction
    data class ZoomChanged(val zoom: Float) : SharedPdfReaderAction
    data class ZoomBy(val delta: Float) : SharedPdfReaderAction
    data class SearchChanged(val query: String) : SharedPdfReaderAction
    data class SearchHighlightModeChanged(val mode: SearchHighlightMode) : SharedPdfReaderAction
    data object SearchHighlightModeToggled : SharedPdfReaderAction
    data class GoToSearchResult(
        val resultIndex: Int,
        val results: List<SharedPdfSearchResult>
    ) : SharedPdfReaderAction
    data class ToolSelected(val tool: PdfInkTool) : SharedPdfReaderAction
    data class ColorSelected(val colorArgb: Int) : SharedPdfReaderAction
    data class StrokeWidthChanged(val strokeWidth: Float) : SharedPdfReaderAction
    data class TextSelectionModeChanged(val enabled: Boolean) : SharedPdfReaderAction
    data class BookmarksLoaded(val bookmarks: List<SharedPdfBookmark>) : SharedPdfReaderAction
    data class BookmarkToggled(
        val pageIndex: Int,
        val label: String = "",
        val createdAt: Long = 0L
    ) : SharedPdfReaderAction
    data class AnnotationsLoaded(val annotations: List<SharedPdfAnnotation>) : SharedPdfReaderAction
    data class AnnotationAdded(val annotation: SharedPdfAnnotation) : SharedPdfReaderAction
    data class AnnotationSelected(val annotationId: String?) : SharedPdfReaderAction
    data class AnnotationUpdated(val annotation: SharedPdfAnnotation) : SharedPdfReaderAction
    data class AnnotationDeleted(val annotationId: String) : SharedPdfReaderAction
    data class AnnotationsChanged(val annotations: List<SharedPdfAnnotation>) : SharedPdfReaderAction
    data class UndoLastAnnotationOnPage(val pageIndex: Int) : SharedPdfReaderAction
    data class ClearPageAnnotations(val pageIndex: Int) : SharedPdfReaderAction
}

fun SharedPdfReaderState.reduce(
    action: SharedPdfReaderAction,
    zoomSpec: PdfZoomSpec = PdfZoomSpec()
): SharedPdfReaderState {
    fun goToPage(target: Int): SharedPdfReaderState {
        return copy(pageIndex = target.coerceIn(0, lastPageIndex)).coerced(zoomSpec)
    }

    return when (action) {
        is SharedPdfReaderAction.GoToPage -> goToPage(action.pageIndex)
        SharedPdfReaderAction.PreviousPage -> goToPage(pageIndex - 1)
        SharedPdfReaderAction.NextPage -> goToPage(pageIndex + 1)
        SharedPdfReaderAction.FirstPage -> goToPage(0)
        SharedPdfReaderAction.LastPage -> goToPage(lastPageIndex)
        is SharedPdfReaderAction.DisplayModeChanged -> copy(displayMode = action.mode)
        SharedPdfReaderAction.DisplayModeToggled -> copy(
            displayMode = when (displayMode) {
                PdfDisplayMode.PAGINATION -> PdfDisplayMode.VERTICAL_SCROLL
                PdfDisplayMode.VERTICAL_SCROLL -> PdfDisplayMode.PAGINATION
            }
        )
        is SharedPdfReaderAction.ZoomChanged -> copy(zoom = zoomSpec.clamp(action.zoom))
        is SharedPdfReaderAction.ZoomBy -> copy(zoom = zoomSpec.clamp(zoom + action.delta))
        is SharedPdfReaderAction.SearchChanged -> copy(
            searchQuery = action.query,
            activeSearchResultIndex = -1
        )
        is SharedPdfReaderAction.SearchHighlightModeChanged -> copy(searchHighlightMode = action.mode)
        SharedPdfReaderAction.SearchHighlightModeToggled -> copy(
            searchHighlightMode = when (searchHighlightMode) {
                SearchHighlightMode.ALL -> SearchHighlightMode.FOCUSED
                SearchHighlightMode.FOCUSED -> SearchHighlightMode.ALL
            }
        )
        is SharedPdfReaderAction.GoToSearchResult -> {
            if (action.results.isEmpty()) {
                this
            } else {
                val normalizedIndex = action.resultIndex.wrapIndex(action.results.size)
                copy(
                    activeSearchResultIndex = normalizedIndex,
                    pageIndex = action.results[normalizedIndex].pageIndex.coerceIn(0, lastPageIndex)
                )
            }
        }
        is SharedPdfReaderAction.ToolSelected -> {
            val config = SharedPdfAnnotationDefaults.configFor(action.tool)
            copy(
                selectedTool = action.tool,
                selectedColorArgb = config.colorArgb,
                strokeWidth = config.strokeWidth
            )
        }
        is SharedPdfReaderAction.ColorSelected -> copy(selectedColorArgb = action.colorArgb)
        is SharedPdfReaderAction.StrokeWidthChanged -> copy(strokeWidth = action.strokeWidth.coerceAtLeast(0.0001f))
        is SharedPdfReaderAction.TextSelectionModeChanged -> copy(isTextSelectionMode = action.enabled)
        is SharedPdfReaderAction.BookmarksLoaded -> copy(bookmarks = action.bookmarks.normalizedBookmarks(lastPageIndex))
        is SharedPdfReaderAction.BookmarkToggled -> {
            val page = action.pageIndex.coerceIn(0, lastPageIndex)
            val withoutPage = bookmarks.filterNot { it.pageIndex == page }
            val nextBookmarks = if (withoutPage.size == bookmarks.size) {
                withoutPage + SharedPdfBookmark(
                    pageIndex = page,
                    label = action.label.ifBlank { "Page ${page + 1}" },
                    createdAt = action.createdAt
                )
            } else {
                withoutPage
            }
            copy(bookmarks = nextBookmarks.normalizedBookmarks(lastPageIndex))
        }
        is SharedPdfReaderAction.AnnotationsLoaded -> copy(annotations = action.annotations.toList())
        is SharedPdfReaderAction.AnnotationAdded -> copy(
            annotations = annotations + action.annotation,
            selectedAnnotationId = action.annotation.id
        )
        is SharedPdfReaderAction.AnnotationSelected -> copy(
            selectedAnnotationId = action.annotationId?.takeIf { id -> annotations.any { it.id == id } }
        )
        is SharedPdfReaderAction.AnnotationUpdated -> {
            val index = annotations.indexOfFirst { it.id == action.annotation.id }
            if (index < 0) {
                this
            } else {
                copy(annotations = annotations.toMutableList().also { it[index] = action.annotation })
            }
        }
        is SharedPdfReaderAction.AnnotationDeleted -> copy(
            annotations = annotations.filterNot { it.id == action.annotationId },
            selectedAnnotationId = selectedAnnotationId?.takeIf { it != action.annotationId }
        )
        is SharedPdfReaderAction.AnnotationsChanged -> copy(annotations = action.annotations.toList())
        is SharedPdfReaderAction.UndoLastAnnotationOnPage -> {
            val index = annotations.indexOfLast { it.pageIndex == action.pageIndex }
            if (index < 0) {
                this
            } else {
                val removedId = annotations[index].id
                copy(
                    annotations = annotations.toMutableList().also { it.removeAt(index) },
                    selectedAnnotationId = selectedAnnotationId?.takeIf { it != removedId }
                )
            }
        }
        is SharedPdfReaderAction.ClearPageAnnotations -> {
            val removedIds = annotations.filter { it.pageIndex == action.pageIndex }.map { it.id }.toSet()
            copy(
                annotations = annotations.filterNot { it.pageIndex == action.pageIndex },
                selectedAnnotationId = selectedAnnotationId?.takeIf { it !in removedIds }
            )
        }
    }.coerced(zoomSpec)
}

object SharedPdfSearchEngine {
    fun search(
        pageTexts: List<String>,
        query: String,
        previewRadiusBefore: Int = 70,
        previewRadiusAfter: Int = 100
    ): List<SharedPdfSearchResult> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        return pageTexts.flatMapIndexed { pageIndex, text ->
            val matches = mutableListOf<SharedPdfSearchResult>()
            var startIndex = 0
            while (startIndex < text.length) {
                val matchIndex = text.indexOf(normalized, startIndex, ignoreCase = true)
                if (matchIndex < 0) break
                matches += SharedPdfSearchResult(
                    pageIndex = pageIndex,
                    preview = text.previewAround(
                        index = matchIndex,
                        queryLength = normalized.length,
                        before = previewRadiusBefore,
                        after = previewRadiusAfter
                    ),
                    matchIndex = matchIndex,
                    matchLength = normalized.length
                )
                startIndex = matchIndex + normalized.length.coerceAtLeast(1)
            }
            matches
        }
    }

    fun highlightsForPage(
        results: List<SharedPdfSearchResult>,
        pageIndex: Int,
        activeResultIndex: Int,
        mode: SearchHighlightMode
    ): List<SharedPdfSearchResult> {
        return when (mode) {
            SearchHighlightMode.ALL -> results.filter { it.pageIndex == pageIndex }
            SearchHighlightMode.FOCUSED -> {
                val active = results.getOrNull(activeResultIndex)
                if (active?.pageIndex == pageIndex) listOf(active) else emptyList()
            }
        }
    }
}

class SharedPdfSearchIndex(
    val pageCount: Int = 0
) {
    private val pageTexts = LinkedHashMap<Int, String>()
    private val tokenPages = LinkedHashMap<String, MutableSet<Int>>()

    val indexedPageCount: Int
        get() = pageTexts.size

    fun hasPage(pageIndex: Int): Boolean = pageTexts.containsKey(pageIndex)

    fun pageText(pageIndex: Int): String? = pageTexts[pageIndex]

    fun indexedPages(): List<SharedPdfIndexedPage> {
        return pageTexts.entries
            .sortedBy { it.key }
            .map { SharedPdfIndexedPage(pageIndex = it.key, text = it.value) }
    }

    fun putPage(pageIndex: Int, text: String) {
        if (pageCount > 0 && pageIndex !in 0 until pageCount) return
        removePageTokens(pageIndex)
        pageTexts[pageIndex] = text
        text.searchTokens().forEach { token ->
            tokenPages.getOrPut(token) { linkedSetOf() } += pageIndex
        }
    }

    fun clear() {
        pageTexts.clear()
        tokenPages.clear()
    }

    fun search(
        query: String,
        previewRadiusBefore: Int = 70,
        previewRadiusAfter: Int = 100
    ): List<SharedPdfSearchResult> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        val matcher = SharedPdfPhraseMatcher(normalized)
        val candidates = candidatePages(matcher.tokens)
        return candidates.flatMap { pageIndex ->
            val text = pageTexts[pageIndex].orEmpty()
            matcher.findAll(text).map { match ->
                SharedPdfSearchResult(
                    pageIndex = pageIndex,
                    preview = text.previewAround(
                        index = match.startIndex,
                        queryLength = match.length,
                        before = previewRadiusBefore,
                        after = previewRadiusAfter
                    ),
                    matchIndex = match.startIndex,
                    matchLength = match.length
                )
            }
        }
    }

    private fun candidatePages(tokens: List<String>): List<Int> {
        if (tokens.isEmpty()) return pageTexts.keys.sorted()
        val candidateSets = tokens.map { token ->
            tokenPages.asSequence()
                .filter { (indexedToken, _) -> indexedToken.startsWith(token) }
                .flatMap { (_, pages) -> pages.asSequence() }
                .toSet()
        }
        if (candidateSets.any { it.isEmpty() }) return emptyList()
        return candidateSets
            .drop(1)
            .fold(candidateSets.first()) { acc, pages -> acc.intersect(pages) }
            .sorted()
    }

    private fun removePageTokens(pageIndex: Int) {
        if (!pageTexts.containsKey(pageIndex)) return
        val emptyTokens = mutableListOf<String>()
        tokenPages.forEach { (token, pages) ->
            pages.remove(pageIndex)
            if (pages.isEmpty()) emptyTokens += token
        }
        emptyTokens.forEach(tokenPages::remove)
    }
}

data class SharedPdfIndexedPage(
    val pageIndex: Int,
    val text: String
)

private data class SharedPdfPhraseMatch(
    val startIndex: Int,
    val length: Int
)

private class SharedPdfPhraseMatcher(query: String) {
    val tokens: List<String> = query.searchTokens()
    private val regex = query.toSearchPhraseRegex()
    private val literal = query.takeIf { regex == null }

    fun findAll(text: String): List<SharedPdfPhraseMatch> {
        return if (regex != null) {
            regex.findAll(text).map { match ->
                SharedPdfPhraseMatch(
                    startIndex = match.range.first,
                    length = match.range.last - match.range.first + 1
                )
            }.toList()
        } else {
            val needle = literal.orEmpty()
            val matches = mutableListOf<SharedPdfPhraseMatch>()
            var startIndex = 0
            while (startIndex < text.length) {
                val matchIndex = text.indexOf(needle, startIndex, ignoreCase = true)
                if (matchIndex < 0) break
                matches += SharedPdfPhraseMatch(matchIndex, needle.length)
                startIndex = matchIndex + needle.length.coerceAtLeast(1)
            }
            matches
        }
    }
}

private fun String.toSearchPhraseRegex(): Regex? {
    val tokens = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.size <= 1) return null
    val prefix = if (all { it.code < 128 }) "\\b" else ""
    return Regex(prefix + tokens.joinToString("\\s+") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
}

private fun Int.wrapIndex(size: Int): Int {
    if (size <= 0) return -1
    return when {
        this < 0 -> size - 1
        this >= size -> 0
        else -> this
    }
}

private fun List<SharedPdfBookmark>.normalizedBookmarks(lastPageIndex: Int): List<SharedPdfBookmark> {
    return asSequence()
        .filter { it.pageIndex in 0..lastPageIndex }
        .distinctBy { it.pageIndex }
        .sortedBy { it.pageIndex }
        .toList()
}

private fun String.previewAround(
    index: Int,
    queryLength: Int,
    before: Int,
    after: Int
): String {
    val start = (index - before).coerceAtLeast(0)
    val end = (index + queryLength + after).coerceAtMost(length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < length) "..." else ""
    return prefix + substring(start, end).replace(Regex("\\s+"), " ").trim() + suffix
}

private fun String.searchTokens(): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    forEach { char ->
        if (char.isLetterOrDigit() || char == '_') {
            current.append(char.lowercaseChar())
        } else if (current.isNotEmpty()) {
            tokens += current.toString()
            current.setLength(0)
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens.distinct()
}
