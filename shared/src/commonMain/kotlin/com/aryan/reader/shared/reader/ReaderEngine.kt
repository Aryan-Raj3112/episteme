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
    val matchIndex: Int = 0
)

data class ReaderSessionState(
    val reader: PaginatedReaderState,
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val highlights: List<UserHighlight> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val activeSearchResultIndex: Int = -1
) {
    val currentBookmark: ReaderBookmark?
        get() = bookmarks.firstOrNull { it.pageIndex == reader.currentPageIndex }

    val activeSearchResult: ReaderSearchResult?
        get() = searchResults.getOrNull(activeSearchResultIndex)
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
        return state.copy(reader = paginator.repaginate(state.reader, settings))
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
                    val index = page.text.indexOf(normalized, startIndex, ignoreCase = true)
                    if (index < 0) break
                    matches +=
                        ReaderSearchResult(
                            pageIndex = page.pageIndex,
                            chapterTitle = page.chapterTitle,
                            preview = page.text.previewAround(index, normalized.length),
                            matchIndex = index
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
            searchQuery = query,
            searchResults = results,
            activeSearchResultIndex = activeIndex
        )
        return updated.activeSearchResult?.let { goToPage(updated, it.pageIndex) } ?: updated
    }

    fun nextSearchResult(state: ReaderSessionState): ReaderSessionState {
        if (state.searchResults.isEmpty()) return state
        val nextIndex = if (state.activeSearchResultIndex < state.searchResults.lastIndex) {
            state.activeSearchResultIndex + 1
        } else {
            0
        }
        return goToSearchResult(state, nextIndex)
    }

    fun previousSearchResult(state: ReaderSessionState): ReaderSessionState {
        if (state.searchResults.isEmpty()) return state
        val nextIndex = if (state.activeSearchResultIndex > 0) {
            state.activeSearchResultIndex - 1
        } else {
            state.searchResults.lastIndex
        }
        return goToSearchResult(state, nextIndex)
    }

    fun goToSearchResult(state: ReaderSessionState, resultIndex: Int): ReaderSessionState {
        if (state.searchResults.isEmpty()) return state
        val targetIndex = resultIndex.coerceIn(0, state.searchResults.lastIndex)
        return state.copy(
            reader = state.reader.copy(currentPageIndex = state.searchResults[targetIndex].pageIndex),
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
