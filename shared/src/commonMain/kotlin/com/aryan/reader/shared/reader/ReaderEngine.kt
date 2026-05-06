package com.aryan.reader.shared.reader

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.UserHighlight

data class ReaderBookmark(
    val id: String,
    val pageIndex: Int,
    val chapterTitle: String,
    val preview: String,
    val locator: ReaderLocator = ReaderLocator(pageIndex = pageIndex, textQuote = preview)
)

data class ReaderSearchResult(
    val pageIndex: Int,
    val chapterTitle: String,
    val preview: String,
    val matchIndex: Int = 0,
    val chapterIndex: Int = 0,
    val locator: ReaderLocator = ReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        startOffset = matchIndex,
        textQuote = preview
    )
)

data class ReaderSearchOptions(
    val matchCase: Boolean = false,
    val wholeWords: Boolean = false
)

data class ReaderSessionState(
    val reader: PaginatedReaderState,
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val highlights: List<UserHighlight> = emptyList(),
    val isSearchActive: Boolean = false,
    val showSearchResultsPanel: Boolean = true,
    val searchQuery: String = "",
    val searchOptions: ReaderSearchOptions = ReaderSearchOptions(),
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val activeSearchResultIndex: Int = -1
) {
    val currentBookmark: ReaderBookmark?
        get() = bookmarks.firstOrNull { it.pageIndex == reader.currentPageIndex }

    val activeSearchResult: ReaderSearchResult?
        get() = searchResults.getOrNull(activeSearchResultIndex)

    val canGoToPreviousSearchResult: Boolean
        get() = when {
            activeSearchResultIndex > 0 -> true
            activeSearchResultIndex >= 0 -> false
            else -> searchResults.any { it.pageIndex <= reader.currentPageIndex }
        }

    val canGoToNextSearchResult: Boolean
        get() = when {
            activeSearchResultIndex in 0 until searchResults.lastIndex -> true
            activeSearchResultIndex >= 0 -> false
            else -> searchResults.any { it.pageIndex >= reader.currentPageIndex }
        }
}

class ReaderEngine(
    private val paginator: SimplePaginator = SimplePaginator()
) {
    fun createSession(
        book: SharedEpubBook,
        settings: ReaderSettings = ReaderSettings(),
        initialPageIndex: Int = 0,
        bookmarks: List<ReaderBookmark> = emptyList(),
        highlights: List<UserHighlight> = emptyList()
    ): ReaderSessionState {
        val pages = paginator.paginate(book, settings)
        return ReaderSessionState(
            reader = PaginatedReaderState(
                book = book,
                pages = pages,
                currentPageIndex = initialPageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0)),
                settings = settings
            ),
            bookmarks = bookmarks
                .filter { it.pageIndex in pages.indices }
                .distinctBy { it.pageIndex }
                .sortedBy { it.pageIndex },
            highlights = highlights
                .map { it.withNormalizedLocator() }
                .filter { (it.locator.chapterIndex ?: it.chapterIndex) in book.chapters.indices }
                .distinctBy { it.id }
        )
    }

    fun next(state: ReaderSessionState): ReaderSessionState {
        if (!state.reader.canGoNext) return state
        return state.copy(reader = state.reader.copy(currentPageIndex = state.reader.currentPageIndex + 1))
    }

    fun previous(state: ReaderSessionState): ReaderSessionState {
        if (!state.reader.canGoPrevious) return state
        return state.copy(reader = state.reader.copy(currentPageIndex = state.reader.currentPageIndex - 1))
    }

    fun goToPage(state: ReaderSessionState, pageIndex: Int): ReaderSessionState {
        val target = pageIndex.coerceIn(0, state.reader.pages.lastIndex.coerceAtLeast(0))
        return state.copy(
            reader = state.reader.copy(currentPageIndex = target),
            activeSearchResultIndex = state.searchResults.indexOfFirst { it.pageIndex == target }
        )
    }

    fun goToPageNumber(state: ReaderSessionState, pageNumber: Int): ReaderSessionState {
        return goToPage(state, pageNumber - 1)
    }

    fun goToProgress(state: ReaderSessionState, progress: Float): ReaderSessionState {
        if (state.reader.pages.isEmpty()) return state
        val target = ((state.reader.pages.lastIndex) * progress.coerceIn(0f, 1f)).toInt()
        return goToPage(state, target)
    }

    fun goToChapter(state: ReaderSessionState, chapterIndex: Int): ReaderSessionState {
        val pageIndex = state.reader.pages.indexOfFirst { it.chapterIndex == chapterIndex }
        return if (pageIndex >= 0) goToPage(state, pageIndex) else state
    }

    fun goToLocator(state: ReaderSessionState, locator: ReaderLocator): ReaderSessionState {
        val pageIndex = state.reader.pages.indexOfFirst { page -> page.contains(locator) }
            .takeIf { it >= 0 }
            ?: locator.pageIndex
            ?.takeIf { it in state.reader.pages.indices }
            ?: return state
        return goToPage(state, pageIndex)
    }

    fun updateSettings(state: ReaderSessionState, settings: ReaderSettings): ReaderSessionState {
        val updated = state.copy(reader = paginator.repaginate(state.reader, settings))
        return if (updated.searchQuery.isNotBlank()) search(updated, updated.searchQuery) else updated
    }

    fun openSearch(state: ReaderSessionState): ReaderSessionState {
        return state.copy(isSearchActive = true, showSearchResultsPanel = true)
    }

    fun closeSearch(state: ReaderSessionState): ReaderSessionState {
        return state.copy(
            isSearchActive = false,
            showSearchResultsPanel = true,
            searchQuery = "",
            searchResults = emptyList(),
            activeSearchResultIndex = -1
        )
    }

    fun toggleSearchResultsPanel(state: ReaderSessionState): ReaderSessionState {
        return state.copy(showSearchResultsPanel = !state.showSearchResultsPanel)
    }

    fun updateSearchOptions(state: ReaderSessionState, options: ReaderSearchOptions): ReaderSessionState {
        val updated = state.copy(searchOptions = options)
        return if (updated.searchQuery.isBlank()) updated else search(updated, updated.searchQuery)
    }

    fun toggleBookmark(state: ReaderSessionState): ReaderSessionState {
        val page = state.reader.currentPage ?: return state
        val chapter = state.reader.book.chapters.getOrNull(page.chapterIndex)
        val locator = ReaderLocator(
            chapterIndex = page.chapterIndex,
            chapterId = chapter?.id,
            pageIndex = page.pageIndex,
            startOffset = page.startOffset,
            endOffset = page.endOffset,
            textQuote = page.text.preview()
        )
        return toggleBookmarkAtLocator(
            state = state,
            locator = locator,
            chapterTitle = page.chapterTitle,
            preview = page.text.preview()
        )
    }

    fun toggleBookmarkAtLocator(
        state: ReaderSessionState,
        locator: ReaderLocator,
        chapterTitle: String? = null,
        preview: String? = null
    ): ReaderSessionState {
        val targetPageIndex = state.reader.pages.indexOfFirst { page -> page.contains(locator) }
            .takeIf { it >= 0 }
            ?: locator.pageIndex
            ?.takeIf { it in state.reader.pages.indices }
            ?: state.reader.currentPageIndex
        val page = state.reader.pages.getOrNull(targetPageIndex) ?: return state
        val chapter = state.reader.book.chapters.getOrNull(page.chapterIndex)
        val normalizedLocator = locator.withFallbacks(
            chapterIndex = page.chapterIndex,
            chapterId = chapter?.id,
            pageIndex = targetPageIndex,
            startOffset = page.startOffset,
            endOffset = page.endOffset,
            textQuote = preview ?: page.text.preview()
        )
        val existing = state.bookmarks.firstOrNull {
            it.pageIndex == targetPageIndex || it.locator.sameLocation(normalizedLocator)
        }
        val updated = if (existing != null) {
            state.bookmarks - existing
        } else {
            state.bookmarks + ReaderBookmark(
                id = "${state.reader.book.id}_$targetPageIndex",
                pageIndex = targetPageIndex,
                chapterTitle = chapterTitle ?: page.chapterTitle,
                preview = preview ?: page.text.preview(),
                locator = normalizedLocator
            )
        }
        return state.copy(bookmarks = updated.sortedBy { it.pageIndex })
    }

    fun upsertHighlight(state: ReaderSessionState, highlight: UserHighlight): ReaderSessionState {
        if (highlight.text.isBlank()) return state
        val normalized = highlight.withNormalizedLocator()
        val existingIndex = state.highlights.indexOfFirst {
            it.id == normalized.id ||
                (it.chapterIndex == normalized.chapterIndex && it.locator.sameLocation(normalized.locator))
        }
        val updated = state.highlights.toMutableList()
        if (existingIndex >= 0) {
            updated[existingIndex] = updated[existingIndex].copy(
                cfi = normalized.cfi,
                text = normalized.text,
                color = normalized.color,
                chapterIndex = normalized.chapterIndex,
                locator = normalized.locator
            )
        } else {
            updated += normalized
        }
        return state.copy(
            highlights = updated
                .filter { (it.locator.chapterIndex ?: it.chapterIndex) in state.reader.book.chapters.indices }
                .distinctBy { it.id }
        )
    }

    fun updateHighlight(
        state: ReaderSessionState,
        highlightId: String,
        color: HighlightColor? = null,
        note: String? = null
    ): ReaderSessionState {
        return state.copy(
            highlights = state.highlights.map { highlight ->
                if (highlight.id == highlightId) {
                    highlight.copy(
                        color = color ?: highlight.color,
                        note = if (note != null) note.takeIf { it.isNotBlank() } else highlight.note
                    )
                } else {
                    highlight
                }
            }
        )
    }

    fun deleteHighlight(state: ReaderSessionState, highlightId: String): ReaderSessionState {
        return state.copy(highlights = state.highlights.filterNot { it.id == highlightId })
    }

    fun search(state: ReaderSessionState, query: String): ReaderSessionState {
        val normalized = query.trim()
        val results = if (normalized.isBlank()) {
            emptyList()
        } else {
            state.reader.pages.flatMap { page ->
                val matches = mutableListOf<ReaderSearchResult>()
                var startIndex = 0
                while (startIndex < page.text.length) {
                    val index = page.text.indexOfSearch(normalized, startIndex, state.searchOptions)
                    if (index < 0) break
                    val endIndex = (index + normalized.length).coerceAtMost(page.text.length)
                    matches +=
                        ReaderSearchResult(
                            pageIndex = page.pageIndex,
                            chapterTitle = page.chapterTitle,
                            preview = page.text.previewAround(index, normalized.length),
                            matchIndex = index,
                            chapterIndex = page.chapterIndex,
                            locator = ReaderLocator(
                                chapterIndex = page.chapterIndex,
                                pageIndex = page.pageIndex,
                                startOffset = page.startOffset + index,
                                endOffset = page.startOffset + endIndex,
                                textQuote = page.text.substring(index, endIndex)
                            )
                        )
                    startIndex = index + normalized.length.coerceAtLeast(1)
                }
                matches
            }
        }
        val activeIndex = results.indexOfFirst { it.pageIndex >= state.reader.currentPageIndex }
            .takeIf { it >= 0 }
            ?: if (results.isNotEmpty()) 0 else -1
        val updated = state.copy(
            isSearchActive = state.isSearchActive || normalized.isNotBlank(),
            showSearchResultsPanel = state.showSearchResultsPanel || normalized.isNotBlank(),
            searchQuery = query,
            searchResults = results,
            activeSearchResultIndex = activeIndex
        )
        return updated.activeSearchResult?.let { goToSearchResult(updated, activeIndex) } ?: updated
    }

    fun nextSearchResult(state: ReaderSessionState): ReaderSessionState {
        val targetIndex = if (state.activeSearchResultIndex >= 0) {
            state.activeSearchResultIndex + 1
        } else {
            state.searchResults.indexOfFirst { it.pageIndex >= state.reader.currentPageIndex }
        }
        if (targetIndex !in state.searchResults.indices) return state
        return goToSearchResult(state, targetIndex)
    }

    fun previousSearchResult(state: ReaderSessionState): ReaderSessionState {
        val targetIndex = if (state.activeSearchResultIndex >= 0) {
            state.activeSearchResultIndex - 1
        } else {
            state.searchResults.indexOfLast { it.pageIndex <= state.reader.currentPageIndex }
        }
        if (targetIndex !in state.searchResults.indices) return state
        return goToSearchResult(state, targetIndex)
    }

    fun goToSearchResult(state: ReaderSessionState, resultIndex: Int): ReaderSessionState {
        if (state.searchResults.isEmpty()) return state
        val targetIndex = resultIndex.coerceIn(0, state.searchResults.lastIndex)
        val result = state.searchResults[targetIndex]
        val targetPage = state.reader.pages.indexOfFirst { page -> page.contains(result.locator) }
            .takeIf { it >= 0 }
            ?: result.pageIndex.coerceIn(0, state.reader.pages.lastIndex.coerceAtLeast(0))
        return state.copy(
            reader = state.reader.copy(currentPageIndex = targetPage),
            activeSearchResultIndex = targetIndex
        )
    }
}

private fun ReaderPage.contains(locator: ReaderLocator): Boolean {
    val targetChapter = locator.chapterIndex
    if (targetChapter != null && targetChapter != chapterIndex) return false
    if (locator.hasTextRange) {
        val start = locator.startOffset ?: return false
        val end = locator.endOffset ?: start
        return start <= endOffset && end >= startOffset
    }
    val targetPage = locator.pageIndex
    return targetPage != null && targetPage == pageIndex
}

private fun UserHighlight.withNormalizedLocator(): UserHighlight {
    val normalizedLocator = locator.copy(textQuote = text).withFallbacks(
        chapterIndex = chapterIndex,
        cfi = cfi,
        textQuote = text
    )
    return copy(
        chapterIndex = normalizedLocator.chapterIndex ?: chapterIndex,
        cfi = normalizedLocator.cfi ?: cfi,
        locator = normalizedLocator
    )
}

private fun String.preview(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .take(140)
}

private fun String.previewAround(index: Int, queryLength: Int): String {
    val start = (index - 70).coerceAtLeast(0)
    val end = (index + queryLength + 100).coerceAtMost(length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < length) "..." else ""
    return prefix + substring(start, end).replace(Regex("\\s+"), " ").trim() + suffix
}

private fun String.indexOfSearch(query: String, startIndex: Int, options: ReaderSearchOptions): Int {
    var index = indexOf(query, startIndex, ignoreCase = !options.matchCase)
    if (!options.wholeWords) return index
    while (index >= 0) {
        val before = getOrNull(index - 1)
        val after = getOrNull(index + query.length)
        if (!before.isWordChar() && !after.isWordChar()) return index
        index = indexOf(query, index + query.length.coerceAtLeast(1), ignoreCase = !options.matchCase)
    }
    return -1
}

private fun Char?.isWordChar(): Boolean {
    return this != null && (isLetterOrDigit() || this == '_')
}
