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
    val preview: String
)

data class ReaderSessionState(
    val reader: PaginatedReaderState,
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList()
) {
    val currentBookmark: ReaderBookmark?
        get() = bookmarks.firstOrNull { it.pageIndex == reader.currentPageIndex }
}

class ReaderEngine(
    private val paginator: SimplePaginator = SimplePaginator()
) {
    fun createSession(
        book: SharedEpubBook,
        settings: ReaderSettings = ReaderSettings()
    ): ReaderSessionState {
        return ReaderSessionState(
            reader = PaginatedReaderState(
                book = book,
                pages = paginator.paginate(book, settings),
                settings = settings
            )
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
        return state.copy(reader = state.reader.copy(currentPageIndex = target))
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
            state.reader.pages.mapNotNull { page ->
                val index = page.text.indexOf(normalized, ignoreCase = true)
                if (index < 0) {
                    null
                } else {
                    ReaderSearchResult(
                        pageIndex = page.pageIndex,
                        chapterTitle = page.chapterTitle,
                        preview = page.text.previewAround(index, normalized.length)
                    )
                }
            }
        }
        return state.copy(searchQuery = query, searchResults = results)
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
