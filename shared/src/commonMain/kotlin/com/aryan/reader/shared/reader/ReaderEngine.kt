package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.UserHighlight

sealed interface ReaderLinkTarget {
    data class External(val url: String) : ReaderLinkTarget
    data class Internal(val locator: ReaderLocator) : ReaderLinkTarget
    data object Ignored : ReaderLinkTarget
}

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
    val navigationRequestId: Long = 0L,
    val jumpHistory: ReaderJumpHistory = ReaderJumpHistory()
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
    private data class PaginationCacheKey(
        val bookId: String,
        val chapterSignature: Int,
        val layoutSignature: ReaderLayoutSignature
    )

    private val paginationCache = object : LinkedHashMap<PaginationCacheKey, List<ReaderPage>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PaginationCacheKey, List<ReaderPage>>?): Boolean {
            return size > 8
        }
    }

    fun createSession(
        book: SharedEpubBook,
        settings: ReaderSettings = ReaderSettings(),
        initialPageIndex: Int = 0,
        initialLocator: ReaderLocator? = null,
        bookmarks: List<ReaderBookmark> = emptyList(),
        highlights: List<UserHighlight> = emptyList()
    ): ReaderSessionState {
        val pages = pagesFor(book, settings)
        val requestedInitialIndex = initialLocator
            ?.let { pages.findPageIndexForLocator(it) }
            ?.takeIf { it >= 0 }
            ?: initialPageIndex
        val initialIndex = ReaderSpreadLayout.normalizePageIndex(requestedInitialIndex, pages.size, settings)
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
            navigationLocator = initialLocator
                ?.normalizedForResolvedPage(book, pages, requestedInitialIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0)))
                ?: reader.currentPage?.toLocator(book)
        )
    }

    fun next(state: ReaderSessionState): ReaderSessionState {
        if (!state.reader.canGoNext) return state
        return goToPage(
            state,
            ReaderSpreadLayout.nextPageIndex(state.reader.currentPageIndex, state.reader.pages.size, state.reader.settings)
        )
    }

    fun previous(state: ReaderSessionState): ReaderSessionState {
        if (!state.reader.canGoPrevious) return state
        return goToPage(
            state,
            ReaderSpreadLayout.previousPageIndex(state.reader.currentPageIndex, state.reader.pages.size, state.reader.settings)
        )
    }

    fun goToPage(state: ReaderSessionState, pageIndex: Int): ReaderSessionState {
        val target = ReaderSpreadLayout.normalizePageIndex(pageIndex, state.reader.pages.size, state.reader.settings)
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
        val requestedPageIndex = state.reader.pages.indexOfFirst { page -> page.contains(locator) }
            .takeIf { it >= 0 }
            ?: locator.pageIndex
            ?.takeIf { it in state.reader.pages.indices }
            ?: return state
        val pageIndex = ReaderSpreadLayout.normalizePageIndex(requestedPageIndex, state.reader.pages.size, state.reader.settings)
        val page = state.reader.pages.getOrNull(pageIndex) ?: return state
        val requestedPage = state.reader.pages.getOrNull(requestedPageIndex) ?: page
        val requestedChapter = state.reader.book.chapters.getOrNull(requestedPage.chapterIndex)
        val normalizedLocator = locator.copy(pageIndex = requestedPageIndex).withFallbacks(
            chapterIndex = requestedPage.chapterIndex,
            chapterId = requestedChapter?.id,
            href = requestedChapter?.baseHref,
            pageIndex = requestedPageIndex,
            startOffset = requestedPage.startOffset,
            endOffset = requestedPage.endOffset,
            textQuote = locator.textQuote ?: requestedPage.text.preview(),
            cfi = locator.cfi ?: requestedPage.toDesktopCfi()
        )
        return state.copy(
            reader = state.reader.copy(currentPageIndex = pageIndex),
            activeSearchResultIndex = state.searchResults.indexOfFirst { it.pageIndex == requestedPageIndex },
            navigationLocator = normalizedLocator,
            navigationRequestId = state.navigationRequestId + 1
        )
    }

    fun jumpToPage(state: ReaderSessionState, pageIndex: Int): ReaderSessionState {
        return goToPage(state, pageIndex).withRecordedJumpFrom(state)
    }

    fun jumpToPageNumber(state: ReaderSessionState, pageNumber: Int): ReaderSessionState {
        return jumpToPage(state, pageNumber - 1)
    }

    fun jumpToChapter(state: ReaderSessionState, chapterIndex: Int): ReaderSessionState {
        return goToChapter(state, chapterIndex).withRecordedJumpFrom(state)
    }

    fun jumpToLocator(state: ReaderSessionState, locator: ReaderLocator): ReaderSessionState {
        return goToLocator(state, locator).withRecordedJumpFrom(state, requestedTarget = locator)
    }

    fun jumpToSearchResult(state: ReaderSessionState, resultIndex: Int): ReaderSessionState {
        return goToSearchResult(state, resultIndex).withRecordedJumpFrom(state)
    }

    fun jumpToNextSearchResult(state: ReaderSessionState): ReaderSessionState {
        return nextSearchResult(state).withRecordedJumpFrom(state)
    }

    fun jumpToPreviousSearchResult(state: ReaderSessionState): ReaderSessionState {
        return previousSearchResult(state).withRecordedJumpFrom(state)
    }

    fun jumpBack(state: ReaderSessionState): ReaderSessionState {
        if (state.reader.settings.readingMode == ReaderReadingMode.PAGINATED) {
            return state.copy(jumpHistory = state.jumpHistory.clear())
        }
        val history = state.jumpHistory.pruned(state.reader.book.chapters.size)
        val target = history.backLocator ?: return state.copy(jumpHistory = history)
        return goToLocator(state.copy(jumpHistory = history), target)
            .copy(jumpHistory = history.stepBack())
    }

    fun jumpForward(state: ReaderSessionState): ReaderSessionState {
        if (state.reader.settings.readingMode == ReaderReadingMode.PAGINATED) {
            return state.copy(jumpHistory = state.jumpHistory.clear())
        }
        val history = state.jumpHistory.pruned(state.reader.book.chapters.size)
        val target = history.forwardLocator ?: return state.copy(jumpHistory = history)
        return goToLocator(state.copy(jumpHistory = history), target)
            .copy(jumpHistory = history.stepForward())
    }

    fun clearJumpHistory(state: ReaderSessionState): ReaderSessionState {
        return state.copy(jumpHistory = state.jumpHistory.clear())
    }

    fun resolveLink(
        state: ReaderSessionState,
        href: String,
        sourceChapterIndex: Int? = state.reader.currentPage?.chapterIndex
    ): ReaderLinkTarget {
        val trimmed = href.trim()
        if (trimmed.isBlank()) {
            logReaderLink("resolve_ignored reason=blank")
            return ReaderLinkTarget.Ignored
        }
        val normalizedHref = when {
            trimmed.startsWith("about:blank#", ignoreCase = true) -> "#${trimmed.substringAfter('#')}"
            trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
            else -> trimmed
        }
        logReaderLink("resolve_start href=\"$trimmed\" normalized=\"$normalizedHref\" sourceChapter=$sourceChapterIndex")

        val scheme = normalizedHref.schemeOrNull()
        if (scheme != null) {
            return when (scheme.lowercase()) {
                "http", "https", "mailto", "tel" -> {
                    logReaderLink("resolve_external scheme=$scheme")
                    ReaderLinkTarget.External(normalizedHref)
                }
                else -> {
                    logReaderLink("resolve_ignored reason=unsupported_scheme scheme=$scheme")
                    ReaderLinkTarget.Ignored
                }
            }
        }

        val sourceIndex = sourceChapterIndex
            ?.takeIf { it in state.reader.book.chapters.indices }
            ?: state.reader.currentPage?.chapterIndex
            ?: 0
        val sourceChapter = state.reader.book.chapters.getOrNull(sourceIndex)
            ?: run {
                logReaderLink("resolve_ignored reason=missing_source sourceChapter=$sourceIndex")
                return ReaderLinkTarget.Ignored
            }

        val pathPart = normalizedHref.substringBefore('#').substringBefore('?')
        val fragment = normalizedHref.substringAfter('#', missingDelimiterValue = "").substringBefore('?')
            .takeIf { it.isNotBlank() }
            ?.percentDecodedOrSelf()

        val targetChapterIndex = if (pathPart.isBlank()) {
            sourceIndex
        } else {
            val targetPath = resolveEpubPath(sourceChapter.baseHref, pathPart.percentDecodedOrSelf())
            state.reader.book.chapters.indexOfFirst { chapter ->
                val chapterPath = normalizeEpubPath(chapter.baseHref.orEmpty())
                chapterPath == targetPath ||
                    chapter.id == pathPart ||
                    chapterPath.substringAfterLast('/') == targetPath.substringAfterLast('/')
            }
        }

        if (targetChapterIndex !in state.reader.book.chapters.indices) {
            logReaderLink(
                "resolve_ignored reason=missing_target path=\"$pathPart\" sourceChapter=$sourceIndex " +
                    "base=\"${sourceChapter.baseHref.orEmpty()}\""
            )
            return ReaderLinkTarget.Ignored
        }

        val targetChapter = state.reader.book.chapters[targetChapterIndex]
        val targetOffset = fragment
            ?.let { targetChapter.semanticBlocks.findElementOffset(it) }
            ?: 0
        val targetPageIndex = state.reader.pages.indexOfFirst { page ->
            page.chapterIndex == targetChapterIndex && targetOffset in page.startOffset..page.endOffset
        }.takeIf { it >= 0 }

        val locator = ReaderLocator(
            chapterIndex = targetChapterIndex,
            chapterId = targetChapter.id,
            href = targetChapter.baseHref,
            pageIndex = targetPageIndex,
            startOffset = targetOffset,
            endOffset = targetOffset,
            cfi = "desktop:$targetChapterIndex:$targetOffset:$targetOffset"
        )
        logReaderLink(
            "resolve_internal targetChapter=$targetChapterIndex targetPage=$targetPageIndex " +
                "fragment=\"${fragment.orEmpty()}\" offset=$targetOffset"
        )
        return ReaderLinkTarget.Internal(locator)
    }

    fun syncVisiblePage(state: ReaderSessionState, pageIndex: Int, locator: ReaderLocator? = null): ReaderSessionState {
        val target = ReaderSpreadLayout.normalizePageIndex(pageIndex, state.reader.pages.size, state.reader.settings)
        val normalizedLocator = locator?.normalizedForPage(state, pageIndex.coerceIn(0, state.reader.pages.lastIndex.coerceAtLeast(0)))
        if (target == state.reader.currentPageIndex && normalizedLocator == null) return state
        return state.copy(
            reader = state.reader.copy(currentPageIndex = target),
            activeSearchResultIndex = state.searchResults.indexOfFirst { it.pageIndex == pageIndex },
            navigationLocator = normalizedLocator ?: state.navigationLocator
        )
    }

    fun updateSettings(state: ReaderSessionState, settings: ReaderSettings): ReaderSessionState {
        val layoutChanged = state.reader.settings.layoutSignature() != settings.layoutSignature()
        val nextJumpHistory = if (settings.readingMode == ReaderReadingMode.PAGINATED) {
            state.jumpHistory.clear()
        } else {
            state.jumpHistory
        }
        if (!layoutChanged) {
            return state.copy(
                reader = state.reader.copy(settings = settings),
                jumpHistory = nextJumpHistory
            )
        }
        val anchor = state.navigationLocator ?: state.reader.currentPage?.toLocator(state.reader.book)
        val pages = pagesFor(state.reader.book, settings)
        val requestedIndex = anchor
            ?.let { pages.findPageIndexForLocator(it) }
            ?.takeIf { it >= 0 }
            ?: 0
        val newIndex = ReaderSpreadLayout.normalizePageIndex(requestedIndex, pages.size, settings)
        val normalizedLocator = anchor
            ?.normalizedForResolvedPage(state.reader.book, pages, requestedIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0)))
            ?: pages.getOrNull(newIndex)?.toLocator(state.reader.book)
        val updated = state.copy(
            reader = state.reader.copy(
                pages = pages,
                currentPageIndex = newIndex,
                settings = settings
            ),
            navigationLocator = normalizedLocator,
            jumpHistory = nextJumpHistory
        )
        return if (updated.searchQuery.isNotBlank()) search(updated, updated.searchQuery) else updated
    }

    fun reflowAnchorFor(state: ReaderSessionState): ReaderLocator? {
        return state.navigationLocator ?: state.reader.currentPage?.toLocator(state.reader.book)
    }

    fun replacePages(
        state: ReaderSessionState,
        pages: List<ReaderPage>,
        reflowAnchor: ReaderLocator? = null,
        navigationRequestIdAtReflowStart: Long? = null
    ): ReaderSessionState {
        if (pages.isEmpty()) return state
        val explicitNavigationAfterReflowStarted = navigationRequestIdAtReflowStart != null &&
            state.navigationRequestId != navigationRequestIdAtReflowStart
        val anchor = when {
            explicitNavigationAfterReflowStarted ->
                state.navigationLocator ?: state.activeSearchResult?.locator ?: state.reader.currentPage?.toLocator(state.reader.book)
            reflowAnchor != null -> reflowAnchor
            else -> state.navigationLocator ?: state.reader.currentPage?.toLocator(state.reader.book)
        }
        val targetIndex = anchor
            ?.let { locator -> pages.findPageIndexForLocator(locator) }
            ?.takeIf { it >= 0 }
            ?: state.reader.currentPage?.let { current ->
                pages.indexOfFirst {
                    it.chapterIndex == current.chapterIndex &&
                        it.startOffset <= current.startOffset &&
                        it.endOffset >= current.startOffset
                }.takeIf { it >= 0 }
            }
            ?: state.reader.currentPageIndex
        val requestedIndex = targetIndex.coerceIn(0, pages.lastIndex)
        val normalizedIndex = ReaderSpreadLayout.normalizePageIndex(requestedIndex, pages.size, state.reader.settings)
        val normalizedLocator = anchor
            ?.normalizedForResolvedPage(state.reader.book, pages, requestedIndex)
            ?: pages.getOrNull(normalizedIndex)?.toLocator(state.reader.book)
        val activeSearchIndex = normalizedLocator
            ?.let { locator -> state.searchResults.indexOfFirst { it.locator.sameLocation(locator) } }
            ?: -1
        val updated = state.copy(
            reader = state.reader.copy(
                pages = pages,
                currentPageIndex = normalizedIndex
            ),
            activeSearchResultIndex = activeSearchIndex,
            navigationLocator = normalizedLocator,
            jumpHistory = if (state.reader.settings.readingMode == ReaderReadingMode.PAGINATED) {
                state.jumpHistory.clear()
            } else {
                state.jumpHistory
            }
        )
        return if (updated.searchQuery.isNotBlank()) refreshSearchResults(updated) else updated
    }

    private fun pagesFor(book: SharedEpubBook, settings: ReaderSettings): List<ReaderPage> {
        val key = PaginationCacheKey(
            bookId = book.id,
            chapterSignature = book.chapters.fold(1) { acc, chapter ->
                31 * acc + chapter.id.hashCode() + chapter.plainText.length + chapter.plainText.hashCode()
            },
            layoutSignature = settings.layoutSignature()
        )
        return synchronized(paginationCache) {
            paginationCache.getOrPut(key) {
                paginator.paginate(book, settings)
            }
        }
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
        val results = searchResultsFor(state, normalized)
        return state.copy(
            isSearchActive = state.isSearchActive || normalized.isNotBlank(),
            showSearchResultsPanel = state.showSearchResultsPanel || normalized.isNotBlank(),
            searchQuery = query,
            searchResults = results,
            activeSearchResultIndex = -1
        )
    }

    private fun refreshSearchResults(state: ReaderSessionState): ReaderSessionState {
        val normalized = state.searchQuery.trim()
        val results = searchResultsFor(state, normalized)
        val previousLocator = state.activeSearchResult?.locator
        val activeIndex = previousLocator
            ?.let { locator -> results.indexOfFirst { it.locator.sameLocation(locator) } }
            ?.takeIf { it >= 0 }
            ?: results.indexOfFirst { it.pageIndex >= state.reader.currentPageIndex }.takeIf { it >= 0 }
            ?: if (results.isNotEmpty()) 0 else -1
        return state.copy(
            searchResults = results,
            activeSearchResultIndex = activeIndex
        )
    }

    private fun searchResultsFor(state: ReaderSessionState, normalized: String): List<ReaderSearchResult> {
        if (normalized.isBlank()) return emptyList()
        return state.reader.pages.flatMap { page ->
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
        val requestedPage = state.reader.pages.indexOfFirst { page -> page.contains(result.locator) }
            .takeIf { it >= 0 }
            ?: result.pageIndex.coerceIn(0, state.reader.pages.lastIndex.coerceAtLeast(0))
        val targetPage = ReaderSpreadLayout.normalizePageIndex(requestedPage, state.reader.pages.size, state.reader.settings)
        val page = state.reader.pages.getOrNull(targetPage)
        val chapter = page?.let { state.reader.book.chapters.getOrNull(it.chapterIndex) }
        return state.copy(
            reader = state.reader.copy(currentPageIndex = targetPage),
            activeSearchResultIndex = targetIndex,
            navigationLocator = result.locator.copy(pageIndex = requestedPage).withFallbacks(
                chapterIndex = page?.chapterIndex,
                chapterId = chapter?.id,
                href = chapter?.baseHref,
                pageIndex = requestedPage
            ),
            navigationRequestId = state.navigationRequestId + 1
        )
    }
}

private fun ReaderSessionState.withRecordedJumpFrom(
    previous: ReaderSessionState,
    requestedTarget: ReaderLocator? = null
): ReaderSessionState {
    if (
        previous.reader.settings.readingMode == ReaderReadingMode.PAGINATED ||
        reader.settings.readingMode == ReaderReadingMode.PAGINATED
    ) {
        return copy(jumpHistory = previous.jumpHistory.clear())
    }
    val current = previous.currentJumpLocator()
    val target = navigationLocator ?: requestedTarget ?: currentJumpLocator()
    return copy(
        jumpHistory = previous.jumpHistory.record(
            currentLocator = current,
            targetLocator = target,
            chapterCount = reader.book.chapters.size
        )
    )
}

private fun ReaderSessionState.currentJumpLocator(): ReaderLocator? {
    return navigationLocator ?: reader.currentPage?.toLocator(reader.book)
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

private fun List<ReaderPage>.findPageIndexForLocator(locator: ReaderLocator): Int {
    return indexOfFirst { page -> page.contains(locator) }
        .takeIf { it >= 0 }
        ?: locator.pageIndex?.takeIf { it in indices }
        ?: -1
}

private fun ReaderLocator.normalizedForResolvedPage(
    book: SharedEpubBook,
    pages: List<ReaderPage>,
    pageIndex: Int
): ReaderLocator? {
    val page = pages.getOrNull(pageIndex) ?: return null
    val chapter = book.chapters.getOrNull(page.chapterIndex)
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

private fun String.schemeOrNull(): String? {
    val colonIndex = indexOf(':')
    if (colonIndex <= 0) return null
    val firstPathIndex = listOf(indexOf('/'), indexOf('?'), indexOf('#'))
        .filter { it >= 0 }
        .minOrNull()
    if (firstPathIndex != null && firstPathIndex < colonIndex) return null
    val candidate = substring(0, colonIndex)
    return candidate.takeIf { it.all { char -> char.isLetterOrDigit() || char == '+' || char == '-' || char == '.' } }
}

private fun resolveEpubPath(baseHref: String?, hrefPath: String): String {
    val path = hrefPath.trimStart('/')
    if (path.isBlank()) return normalizeEpubPath(baseHref.orEmpty())
    val base = baseHref.orEmpty()
    val baseDirectory = if (base.substringAfterLast('/', base).contains('.')) {
        base.substringBeforeLast('/', missingDelimiterValue = "")
    } else {
        base
    }
    return normalizeEpubPath(if (baseDirectory.isBlank()) path else "$baseDirectory/$path")
}

private fun normalizeEpubPath(path: String): String {
    val parts = mutableListOf<String>()
    path.replace('\\', '/')
        .split('/')
        .forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
    return parts.joinToString("/")
}

private fun String.percentDecodedOrSelf(): String {
    return runCatching {
        val output = StringBuilder()
        val bytes = mutableListOf<Byte>()
        fun flushBytes() {
            if (bytes.isNotEmpty()) {
                output.append(bytes.toByteArray().decodeToString())
                bytes.clear()
            }
        }
        var index = 0
        while (index < length) {
            val char = this[index]
            if (char == '%' && index + 2 < length) {
                val value = substring(index + 1, index + 3).toIntOrNull(16)
                if (value != null) {
                    bytes += value.toByte()
                    index += 3
                    continue
                }
            }
            flushBytes()
            output.append(char)
            index++
        }
        flushBytes()
        output.toString()
    }.getOrDefault(this)
}

private fun Iterable<SemanticBlock>.findElementOffset(elementId: String): Int? {
    for (block in this) {
        block.findElementOffset(elementId)?.let { return it }
    }
    return null
}

private fun SemanticBlock.findElementOffset(elementId: String): Int? {
    if (this is SemanticTextBlock) {
        if (this.elementId == elementId) return startCharOffsetInSource
        spans.firstOrNull { it.elementId == elementId }?.let { span ->
            return startCharOffsetInSource + span.start.coerceAtLeast(0)
        }
    }
    return when (this) {
        is SemanticList -> items.findElementOffset(elementId)
        is SemanticTable -> rows.asSequence()
            .flatMap { it.asSequence() }
            .mapNotNull { it.content.findElementOffset(elementId) }
            .firstOrNull()
        is SemanticFlexContainer -> children.findElementOffset(elementId)
        is SemanticWrappingBlock -> paragraphsToWrap.findElementOffset(elementId)
        else -> null
    }
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

private fun logReaderLink(message: String) {
    logSharedReaderDiagnostic("ReaderLinkResolve") { message }
}
