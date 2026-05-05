package com.aryan.reader.shared.reader

data class ReaderBookmark(
    val id: String,
    val pageIndex: Int,
    val chapterTitle: String,
    val preview: String
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
        bookmarks: List<ReaderBookmark> = emptyList()
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
                .sortedBy { it.pageIndex }
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

    fun updateSettings(state: ReaderSessionState, settings: ReaderSettings): ReaderSessionState {
        return state.copy(reader = paginator.repaginate(state.reader, settings))
    }

    fun toggleBookmark(state: ReaderSessionState): ReaderSessionState {
        val page = state.reader.currentPage ?: return state
        val existing = state.bookmarks.firstOrNull { it.pageIndex == state.reader.currentPageIndex }
        val updated = if (existing != null) {
            state.bookmarks - existing
        } else {
            state.bookmarks + ReaderBookmark(
                id = "${state.reader.book.id}_${state.reader.currentPageIndex}",
                pageIndex = state.reader.currentPageIndex,
                chapterTitle = page.chapterTitle,
                preview = page.text.preview()
            )
        }
        return state.copy(bookmarks = updated.sortedBy { it.pageIndex })
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
