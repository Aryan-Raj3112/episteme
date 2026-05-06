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
    val activeSearchResultIndex: Int = -1,
    val navigationLocator: ReaderLocator? = null,
    val navigationRequestId: Long = 0L
) {
    val currentBookmark: ReaderBookmark?
        get() = navigationLocator
            ?.let { locator -> bookmarks.firstOrNull { it.locator.sameLocation(locator) } }
            ?: bookmarks.firstOrNull { it.pageIndex == reader.currentPageIndex && !it.locator.hasTextRange }

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
        val initialIndex = initialPageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        val reader = PaginatedReaderState(
            book = book,
            pages = pages,
            currentPageIndex = initialIndex,
            settings = settings
        )
        return ReaderSessionState(
            reader = reader,
            bookmarks = bookmarks
                .mapNotNull { it.normalizedForBook(book, pages) }
                .distinctBy { it.locationKey() }
                .sortedWith(compareBy<ReaderBookmark> { it.pageIndex }.thenBy { it.locator.startOffset ?: -1 }),
            highlights = highlights
                .map { it.withNormalizedLocator() }
                .filter { (it.locator.chapterIndex ?: it.chapterIndex) in book.chapters.indices }
                .distinctBy { it.id },
            navigationLocator = reader.currentPage?.toLocator(book)
        )
    }

    fun next(state: ReaderSessionState): ReaderSessionState {
        if (!state.reader.canGoNext) return state
        return goToPage(state, state.reader.currentPageIndex + 1)
    }

    fun previous(state: ReaderSessionState): ReaderSessionState {
        if (!state.reader.canGoPrevious) return state
        return goToPage(state, state.reader.currentPageIndex - 1)
    }

    fun goToPage(state: ReaderSessionState, pageIndex: Int): ReaderSessionState {
        val target = pageIndex.coerceIn(0, state.reader.pages.lastIndex.coerceAtLeast(0))
        val page = state.reader.pages.getOrNull(target)
        return state.copy(
            reader = state.reader.copy(currentPageIndex = target),
            activeSearchResultIndex = state.searchResults.indexOfFirst { it.pageIndex == target },
            navigationLocator = page?.toLocator(state.reader.book),
            navigationRequestId = state.navigationRequestId + 1
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
        val page = state.reader.pages.getOrNull(pageIndex) ?: return state
        val chapter = state.reader.book.chapters.getOrNull(page.chapterIndex)
        val normalizedLocator = locator.copy(pageIndex = pageIndex).withFallbacks(
            chapterIndex = page.chapterIndex,
            chapterId = chapter?.id,
            href = chapter?.baseHref,
            pageIndex = pageIndex,
            startOffset = page.startOffset,
            endOffset = page.endOffset,
            textQuote = locator.textQuote ?: page.text.preview(),
            cfi = locator.cfi ?: page.toDesktopCfi()
        )
        return state.copy(
            reader = state.reader.copy(currentPageIndex = pageIndex),
            activeSearchResultIndex = state.searchResults.indexOfFirst { it.pageIndex == pageIndex },
            navigationLocator = normalizedLocator,
            navigationRequestId = state.navigationRequestId + 1
        )
    }

    fun syncVisiblePage(state: ReaderSessionState, pageIndex: Int, locator: ReaderLocator? = null): ReaderSessionState {
        val target = pageIndex.coerceIn(0, state.reader.pages.lastIndex.coerceAtLeast(0))
        val normalizedLocator = locator?.normalizedForPage(state, target)
        if (target == state.reader.currentPageIndex && normalizedLocator == null) return state
        return state.copy(
            reader = state.reader.copy(currentPageIndex = target),
            activeSearchResultIndex = state.searchResults.indexOfFirst { it.pageIndex == target },
            navigationLocator = normalizedLocator ?: state.navigationLocator
        )
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
        val locator = state.navigationLocator
            ?.takeIf { it.belongsTo(page) }
            ?.normalizedForPage(state, page.pageIndex)
            ?: ReaderLocator(
                chapterIndex = page.chapterIndex,
                chapterId = chapter?.id,
                pageIndex = page.pageIndex,
                startOffset = page.startOffset,
                endOffset = page.endOffset,
                textQuote = page.text.preview()
            )
        val preview = locator.textQuote?.takeIf { it.isNotBlank() } ?: page.text.preview()
        return toggleBookmarkAtLocator(
            state = state,
            locator = locator,
            chapterTitle = page.chapterTitle,
            preview = preview
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
        val normalizedLocator = locator.copy(pageIndex = targetPageIndex).withFallbacks(
            chapterIndex = page.chapterIndex,
            chapterId = chapter?.id,
            href = chapter?.baseHref,
            pageIndex = targetPageIndex,
            startOffset = page.startOffset,
            endOffset = page.endOffset,
            textQuote = preview ?: page.text.preview(),
            cfi = locator.cfi ?: "desktop:${page.chapterIndex}:${locator.startOffset ?: page.startOffset}:${locator.endOffset ?: locator.startOffset ?: page.startOffset}"
        )
        val existing = state.bookmarks.firstOrNull {
            it.locator.sameLocation(normalizedLocator) ||
                (!normalizedLocator.hasTextRange && it.pageIndex == targetPageIndex)
        }
        val updated = if (existing != null) {
            state.bookmarks - existing
        } else {
            state.bookmarks + ReaderBookmark(
                id = bookmarkId(state.reader.book.id, targetPageIndex, normalizedLocator),
                pageIndex = targetPageIndex,
                chapterTitle = chapterTitle ?: page.chapterTitle,
                preview = preview ?: page.text.preview(),
                locator = normalizedLocator
            )
        }
        return state.copy(
            bookmarks = updated.sortedWith(
                compareBy<ReaderBookmark> { it.pageIndex }.thenBy { it.locator.startOffset ?: -1 }
            )
        )
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
        val page = state.reader.pages.getOrNull(targetPage)
        val chapter = page?.let { state.reader.book.chapters.getOrNull(it.chapterIndex) }
        return state.copy(
            reader = state.reader.copy(currentPageIndex = targetPage),
            activeSearchResultIndex = targetIndex,
            navigationLocator = result.locator.copy(pageIndex = targetPage).withFallbacks(
                chapterIndex = page?.chapterIndex,
                chapterId = chapter?.id,
                href = chapter?.baseHref,
                pageIndex = targetPage
            ),
            navigationRequestId = state.navigationRequestId + 1
        )
    }
}

private fun ReaderPage.contains(locator: ReaderLocator): Boolean {
    val targetChapter = locator.chapterIndex
    if (targetChapter != null && targetChapter != chapterIndex) return false
    if (locator.hasTextRange) {
        val start = locator.startOffset ?: return false
        val end = locator.endOffset ?: start
        return if (start == end) {
            start in startOffset..endOffset
        } else {
            start < endOffset && end > startOffset
        }
    }
    val targetPage = locator.pageIndex
    return targetPage != null && targetPage == pageIndex
}

private fun ReaderBookmark.normalizedForBook(book: SharedEpubBook, pages: List<ReaderPage>): ReaderBookmark? {
    val targetPageIndex = pages.indexOfFirst { page -> page.contains(locator) }
        .takeIf { it >= 0 }
        ?: pageIndex.takeIf { it in pages.indices }
        ?: return null
    val page = pages.getOrNull(targetPageIndex) ?: return null
    val chapter = book.chapters.getOrNull(page.chapterIndex)
    val normalizedLocator = locator.copy(pageIndex = targetPageIndex).withFallbacks(
        chapterIndex = page.chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = targetPageIndex,
        startOffset = page.startOffset,
        endOffset = page.endOffset,
        textQuote = preview.ifBlank { page.text.preview() },
        cfi = locator.cfi ?: page.toDesktopCfi()
    )
    return copy(
        pageIndex = targetPageIndex,
        chapterTitle = chapterTitle.ifBlank { page.chapterTitle },
        preview = preview.ifBlank { normalizedLocator.textQuote ?: page.text.preview() },
        locator = normalizedLocator
    )
}

private fun ReaderBookmark.locationKey(): String {
    val locator = locator
    return listOf(
        locator.chapterIndex,
        locator.pageIndex,
        locator.startOffset,
        locator.endOffset,
        locator.cfi
    ).joinToString(":")
}

private fun bookmarkId(bookId: String, pageIndex: Int, locator: ReaderLocator): String {
    val chapter = locator.chapterIndex ?: -1
    val start = locator.startOffset ?: -1
    val end = locator.endOffset ?: start
    return "${bookId}_${pageIndex}_${chapter}_${start}_${end}"
}

private fun ReaderLocator.belongsTo(page: ReaderPage): Boolean {
    val targetChapter = chapterIndex
    if (targetChapter != null && targetChapter != page.chapterIndex) return false
    if (pageIndex == page.pageIndex) return true
    val start = startOffset
    val end = endOffset ?: start
    if (start != null && end != null) {
        return if (start == end) {
            start in page.startOffset..page.endOffset
        } else {
            start < page.endOffset && end > page.startOffset
        }
    }
    return pageIndex == page.pageIndex
}

private fun ReaderLocator.normalizedForPage(state: ReaderSessionState, pageIndex: Int): ReaderLocator? {
    val page = state.reader.pages.getOrNull(pageIndex) ?: return null
    val chapter = state.reader.book.chapters.getOrNull(page.chapterIndex)
    val start = startOffset ?: page.startOffset
    val end = (endOffset ?: start).coerceAtLeast(start)
    return copy(pageIndex = page.pageIndex).withFallbacks(
        chapterIndex = page.chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = page.pageIndex,
        startOffset = start,
        endOffset = end,
        textQuote = textQuote ?: page.text.preview(),
        cfi = cfi ?: "desktop:${page.chapterIndex}:$start:$end"
    )
}

private fun ReaderPage.toLocator(book: SharedEpubBook): ReaderLocator {
    val chapter = book.chapters.getOrNull(chapterIndex)
    return ReaderLocator(
        chapterIndex = chapterIndex,
        chapterId = chapter?.id,
        href = chapter?.baseHref,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        textQuote = text.preview(),
        cfi = toDesktopCfi()
    )
}

private fun ReaderPage.toDesktopCfi(): String {
    return "desktop:$chapterIndex:$startOffset:$endOffset"
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
