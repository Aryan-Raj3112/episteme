package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.reader.ReaderSettings
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
    val matchLength: Int = 0,
    val boundsList: List<PdfPageBounds> = emptyList()
)

@Serializable
data class SharedPdfBookmark(
    val pageIndex: Int,
    val label: String = "",
    val createdAt: Long = 0L
)

/** Formats a spread-aware page label, mirroring Android's pdfPageRangeLabel. */
fun sharedPdfPageRangeLabel(pageLabel: String, pageCount: Int): String {
    val range = pageLabel.ifBlank { "1" }
    return if ('-' in range) {
        "Pages $range of ${pageCount.coerceAtLeast(1)}"
    } else {
        "Page $range of ${pageCount.coerceAtLeast(1)}"
    }
}

@Serializable
data class SharedPdfBookmarkStore(
    val version: Int = 1,
    val bookmarks: List<SharedPdfBookmark> = emptyList()
)

@Serializable
data class SharedPdfBlankPageInsertion(
    val afterPdfIndex: Int,
    val widthPx: Float = 595f,
    val heightPx: Float = 842f,
    val id: String = "",
    val wasManuallyAdded: Boolean = true,
)

sealed interface SharedPdfVirtualPage {
    data class PdfPage(val pdfIndex: Int) : SharedPdfVirtualPage
    data class BlankPage(val insertion: SharedPdfBlankPageInsertion) : SharedPdfVirtualPage
}

/**
 * Builds the display layout for a PDF: pdf pages in order with blank pages inserted
 * after their target pdf page. Blank pages inserted at the same slot keep insertion order.
 * Insertions pointing past the last page (or before the first) are clamped.
 */
fun buildSharedPdfVirtualPageLayout(
    pageCount: Int,
    insertions: List<SharedPdfBlankPageInsertion>,
): List<SharedPdfVirtualPage> {
    val safeCount = pageCount.coerceAtLeast(0)
    val bySlot = HashMap<Int, MutableList<SharedPdfBlankPageInsertion>>()
    insertions.forEach { insertion ->
        val slot = insertion.afterPdfIndex.coerceIn(0, (safeCount - 1).coerceAtLeast(0))
        bySlot.getOrPut(slot) { mutableListOf() }.add(insertion)
    }
    val layout = mutableListOf<SharedPdfVirtualPage>()
    for (pdfIndex in 0 until safeCount) {
        layout.add(SharedPdfVirtualPage.PdfPage(pdfIndex))
        bySlot[pdfIndex]?.forEach { layout.add(SharedPdfVirtualPage.BlankPage(it)) }
    }
    return layout
}

/** The pdf page index displayed at [displayIndex], or null when that position is a blank page. */
fun sharedPdfPdfPageIndexAt(
    layout: List<SharedPdfVirtualPage>,
    displayIndex: Int,
): Int? = (layout.getOrNull(displayIndex) as? SharedPdfVirtualPage.PdfPage)?.pdfIndex

/** The display position at which the pdf page [pdfIndex] appears (first match; blanks shift it). */
fun sharedPdfDisplayIndexFor(
    layout: List<SharedPdfVirtualPage>,
    pdfIndex: Int,
): Int {
    val match = layout.indexOfFirst { it is SharedPdfVirtualPage.PdfPage && it.pdfIndex == pdfIndex }
    return if (match >= 0) match else layout.size
}

/**
 * The pdf page index to associate with [displayIndex]: the page itself for pdf pages,
 * or the preceding pdf page for blank pages (blanks always follow a pdf page).
 */
fun sharedPdfNearestPdfPageIndex(
    layout: List<SharedPdfVirtualPage>,
    displayIndex: Int,
): Int? {
    for (index in displayIndex.coerceAtLeast(0) downTo 0) {
        val pdfIndex = sharedPdfPdfPageIndexAt(layout, index) ?: continue
        return pdfIndex
    }
    return null
}

@Serializable
data class SharedPdfReaderStore(
    val version: Int = 1,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val displayMode: PdfDisplayMode = PdfDisplayMode.PAGINATION,
    val themeId: String = "no_theme",
    val zoom: Float = PdfZoomSpec().default,
    val isScrollLocked: Boolean = false,
    val lockedZoomScale: Float = 1f,
    val lockedZoomOffsetX: Float = 0f,
    val lockedZoomOffsetY: Float = 0f,
    val selectedTool: PdfInkTool = PdfInkTool.NONE,
    val selectedColorArgb: Int = SharedPdfAnnotationDefaults.configFor(PdfInkTool.NONE).colorArgb,
    val strokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.NONE).strokeWidth,
    val isTextSelectionMode: Boolean = false,
    val bookmarks: List<SharedPdfBookmark> = emptyList(),
    val annotations: List<SharedPdfAnnotation> = emptyList(),
    val blankPageInsertions: List<SharedPdfBlankPageInsertion> = emptyList(),
    val penPalette: List<Int> = SharedPdfAnnotationDefaults.penPalette,
    val lastActivePenTool: PdfInkTool = PdfInkTool.PEN,
    val lastActiveHighlighterTool: PdfInkTool = PdfInkTool.HIGHLIGHTER,
    val richTextDocumentJson: String = ""
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

object SharedPdfReaderStateSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(state: SharedPdfReaderState): String {
        return json.encodeToString(
            SharedPdfReaderStore(
                pageIndex = state.pageIndex,
                pageCount = state.pageCount,
                displayMode = state.displayMode,
                themeId = state.themeId,
                zoom = state.zoom,
                isScrollLocked = state.isScrollLocked,
                lockedZoomScale = state.lockedZoomScale,
                lockedZoomOffsetX = state.lockedZoomOffsetX,
                lockedZoomOffsetY = state.lockedZoomOffsetY,
                selectedTool = state.selectedTool,
                selectedColorArgb = state.selectedColorArgb,
                strokeWidth = state.strokeWidth,
                isTextSelectionMode = state.isTextSelectionMode,
                bookmarks = state.bookmarks,
                annotations = state.annotations,
                blankPageInsertions = state.blankPageInsertions,
                penPalette = state.penPalette,
                lastActivePenTool = state.lastActivePenTool,
                lastActiveHighlighterTool = state.lastActiveHighlighterTool,
                richTextDocumentJson = state.richTextDocumentJson
            )
        )
    }

    fun decode(raw: String?, fallbackPageCount: Int = 1, fallbackPageIndex: Int = 0): SharedPdfReaderState? {
        if (raw.isNullOrBlank()) return null
        val store = runCatching { json.decodeFromString<SharedPdfReaderStore>(raw) }.getOrNull()
            ?: return null
        val restoredPageIndex = if (store.pageCount > 0) {
            store.pageIndex
        } else {
            store.pageIndex.takeIf { it > 0 } ?: fallbackPageIndex
        }.coerceAtLeast(0)
        val restoredPageCount = store.pageCount.takeIf { it > 0 }
            ?: maxOf(fallbackPageCount, restoredPageIndex + 1, 1)
        return SharedPdfReaderState(
            pageIndex = restoredPageIndex,
            pageCount = restoredPageCount,
            displayMode = store.displayMode,
            themeId = store.themeId,
            zoom = store.zoom,
            isScrollLocked = store.isScrollLocked,
            lockedZoomScale = store.lockedZoomScale,
            lockedZoomOffsetX = store.lockedZoomOffsetX,
            lockedZoomOffsetY = store.lockedZoomOffsetY,
            selectedTool = store.selectedTool,
            selectedColorArgb = store.selectedColorArgb,
            strokeWidth = store.strokeWidth,
            isTextSelectionMode = store.isTextSelectionMode,
            bookmarks = store.bookmarks,
            annotations = store.annotations,
            blankPageInsertions = store.blankPageInsertions,
            penPalette = store.penPalette,
            lastActivePenTool = store.lastActivePenTool,
            lastActiveHighlighterTool = store.lastActiveHighlighterTool,
            richTextDocumentJson = store.richTextDocumentJson
        ).coerced()
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

data class SharedPdfReaderViewport(
    val pageIndex: Int = 0,
    val displayMode: PdfDisplayMode = PdfDisplayMode.PAGINATION,
    val zoom: Float = PdfZoomSpec().default,
    val horizontalScrollOffset: Int = 0,
    val paginatedVerticalScrollOffset: Int = 0,
    val verticalFirstPageIndex: Int = pageIndex,
    val verticalFirstPageScrollOffset: Int = 0
) {
    fun sanitized(
        pageCount: Int,
        zoomSpec: PdfZoomSpec = PdfZoomSpec()
    ): SharedPdfReaderViewport {
        val lastPageIndex = (pageCount.coerceAtLeast(0) - 1).coerceAtLeast(0)
        val safeZoom = if (zoom.isFinite() && zoom > 0f) zoom else zoomSpec.default
        return copy(
            pageIndex = pageIndex.coerceIn(0, lastPageIndex),
            zoom = zoomSpec.clamp(safeZoom),
            horizontalScrollOffset = horizontalScrollOffset.coerceAtLeast(0),
            paginatedVerticalScrollOffset = paginatedVerticalScrollOffset.coerceAtLeast(0),
            verticalFirstPageIndex = verticalFirstPageIndex.coerceIn(0, lastPageIndex),
            verticalFirstPageScrollOffset = verticalFirstPageScrollOffset.coerceAtLeast(0)
        )
    }
}

data class SharedPdfReaderState(
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val displayMode: PdfDisplayMode = PdfDisplayMode.PAGINATION,
    val themeId: String = "no_theme",
    val zoom: Float = PdfZoomSpec().default,
    val isScrollLocked: Boolean = false,
    val lockedZoomScale: Float = 1f,
    val lockedZoomOffsetX: Float = 0f,
    val lockedZoomOffsetY: Float = 0f,
    val isSearchActive: Boolean = false,
    val showSearchResultsPanel: Boolean = true,
    val searchQuery: String = "",
    val activeSearchResultIndex: Int = -1,
    val searchHighlightMode: SearchHighlightMode = SearchHighlightMode.ALL,
    val selectedTool: PdfInkTool = PdfInkTool.NONE,
    val selectedColorArgb: Int = SharedPdfAnnotationDefaults.configFor(PdfInkTool.NONE).colorArgb,
    val strokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.NONE).strokeWidth,
    val isTextSelectionMode: Boolean = false,
    val bookmarks: List<SharedPdfBookmark> = emptyList(),
    val selectedAnnotationId: String? = null,
    val annotations: List<SharedPdfAnnotation> = emptyList(),
    val blankPageInsertions: List<SharedPdfBlankPageInsertion> = emptyList(),
    val toolConfigs: Map<PdfInkTool, PdfToolConfig> = emptyMap(),
    val penPalette: List<Int> = SharedPdfAnnotationDefaults.penPalette,
    val lastActivePenTool: PdfInkTool = PdfInkTool.PEN,
    val lastActiveHighlighterTool: PdfInkTool = PdfInkTool.HIGHLIGHTER,
    val annotationUndoStack: List<SharedPdfAnnotationHistoryAction> = emptyList(),
    val annotationRedoStack: List<SharedPdfAnnotationHistoryAction> = emptyList(),
    val richTextDocumentJson: String = ""
) {
    val safePageCount: Int get() = pageCount.coerceAtLeast(0)
    val displayPageCount: Int get() = safePageCount + blankPageInsertions.size.coerceAtLeast(0)
    val lastPageIndex: Int get() = (displayPageCount - 1).coerceAtLeast(0)
    val lastPdfPageIndex: Int get() = (safePageCount - 1).coerceAtLeast(0)
    val canGoPrevious: Boolean get() = pageIndex > 0
    val canGoNext: Boolean get() = pageIndex < lastPageIndex
    val canUndoAnnotationEdit: Boolean get() = annotationUndoStack.isNotEmpty()
    val canRedoAnnotationEdit: Boolean get() = annotationRedoStack.isNotEmpty()
    val progressPercent: Float get() = ((pageIndex + 1).toFloat() / displayPageCount.coerceAtLeast(1)) * 100f
    val virtualPageLayout: List<SharedPdfVirtualPage>
        get() = buildSharedPdfVirtualPageLayout(pageCount, blankPageInsertions)
    val currentPdfPageIndex: Int? get() = sharedPdfPdfPageIndexAt(virtualPageLayout, pageIndex)
    val currentNearestPdfPageIndex: Int? get() = sharedPdfNearestPdfPageIndex(virtualPageLayout, pageIndex)

    fun coerced(zoomSpec: PdfZoomSpec = PdfZoomSpec()): SharedPdfReaderState {
        val safePage = pageIndex.coerceIn(0, lastPageIndex)
        return copy(
            pageIndex = safePage,
            pageCount = safePageCount,
            activeSearchResultIndex = activeSearchResultIndex.coerceAtLeast(-1),
            zoom = zoomSpec.clamp(zoom),
            lockedZoomScale = lockedZoomScale.takeIf { it.isFinite() }?.coerceIn(1f, 5f) ?: 1f,
            lockedZoomOffsetX = lockedZoomOffsetX.takeIf { it.isFinite() } ?: 0f,
            lockedZoomOffsetY = lockedZoomOffsetY.takeIf { it.isFinite() } ?: 0f,
            bookmarks = bookmarks.normalizedBookmarks(lastPdfPageIndex),
            penPalette = penPalette.sanitizedSharedPdfPenPalette(),
            lastActivePenTool = lastActivePenTool.takeIf { it.isSharedPdfPenTool } ?: PdfInkTool.PEN,
            lastActiveHighlighterTool = lastActiveHighlighterTool.takeIf { it.isSharedPdfHighlighterTool }
                ?: PdfInkTool.HIGHLIGHTER,
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

fun pdfPaginationEdgeTarget(
    currentPage: Int,
    lastPage: Int,
    tappedLeftEdge: Boolean,
    rightToLeft: Boolean,
): Int? {
    val delta = when {
        tappedLeftEdge && rightToLeft -> 1
        tappedLeftEdge -> -1
        rightToLeft -> -1
        else -> 1
    }
    return (currentPage + delta).takeIf { it in 0..lastPage }
}

fun initialSharedPdfReaderState(
    persistedState: SharedPdfReaderState?,
    defaults: ReaderSettings,
    initialPageIndex: Int,
): SharedPdfReaderState {
    return persistedState
        ?.copy(themeId = defaults.themeId ?: persistedState.themeId)
        ?.coerced()
        // Opening is asynchronous. Preserve the requested restore page until the renderer reports
        // the real page count instead of clamping every non-zero restore to page zero.
        ?: SharedPdfReaderState.initial(
            pageCount = (initialPageIndex.coerceAtLeast(0) + 1).coerceAtLeast(1),
            initialPageIndex = initialPageIndex,
        )
            .copy(
                displayMode = PdfDisplayMode.VERTICAL_SCROLL,
                themeId = defaults.themeId ?: "no_theme",
            )
}

sealed interface SharedPdfAnnotationHistoryAction {
    data class Add(val pageIndex: Int, val annotation: SharedPdfAnnotation) : SharedPdfAnnotationHistoryAction
    data class Remove(val itemsByPage: Map<Int, List<SharedPdfAnnotation>>) : SharedPdfAnnotationHistoryAction
}

sealed interface SharedPdfReaderAction {
    data class GoToPage(val pageIndex: Int) : SharedPdfReaderAction
    data object PreviousPage : SharedPdfReaderAction
    data object NextPage : SharedPdfReaderAction
    data object FirstPage : SharedPdfReaderAction
    data object LastPage : SharedPdfReaderAction
    data class DisplayModeChanged(val mode: PdfDisplayMode) : SharedPdfReaderAction
    data object DisplayModeToggled : SharedPdfReaderAction
    data class ThemeChanged(val themeId: String) : SharedPdfReaderAction
    data class ZoomChanged(val zoom: Float) : SharedPdfReaderAction
    data class ZoomBy(val delta: Float) : SharedPdfReaderAction
    data class ScrollLockChanged(
        val locked: Boolean,
        val zoomScale: Float,
        val offsetX: Float,
        val offsetY: Float
    ) : SharedPdfReaderAction
    data class SearchChanged(val query: String) : SharedPdfReaderAction
    data object SearchOpened : SharedPdfReaderAction
    data object SearchClosed : SharedPdfReaderAction
    data object SearchResultsPanelToggled : SharedPdfReaderAction
    data class SearchHighlightModeChanged(val mode: SearchHighlightMode) : SharedPdfReaderAction
    data object SearchHighlightModeToggled : SharedPdfReaderAction
    data class GoToSearchResult(
        val resultIndex: Int,
        val results: List<SharedPdfSearchResult>
    ) : SharedPdfReaderAction
    data class ToolSelected(val tool: PdfInkTool) : SharedPdfReaderAction
    data class ColorSelected(val colorArgb: Int) : SharedPdfReaderAction
    data class StrokeWidthChanged(val strokeWidth: Float) : SharedPdfReaderAction
    data class PenPaletteChanged(val colors: List<Int>) : SharedPdfReaderAction
    data class TextSelectionModeChanged(val enabled: Boolean) : SharedPdfReaderAction
    data class BookmarksLoaded(val bookmarks: List<SharedPdfBookmark>) : SharedPdfReaderAction
    data class BookmarkToggled(
        val pageIndex: Int,
        val label: String = "",
        val createdAt: Long = 0L
    ) : SharedPdfReaderAction
    data class BookmarkRenamed(val pageIndex: Int, val label: String) : SharedPdfReaderAction
    data class BookmarkDeleted(val pageIndex: Int) : SharedPdfReaderAction
    data class InsertBlankPageAt(
        val displayIndex: Int,
        val widthPx: Float,
        val heightPx: Float,
        val id: String = "",
        val wasManuallyAdded: Boolean = true
    ) : SharedPdfReaderAction
    data class DeleteBlankPageAt(val displayIndex: Int) : SharedPdfReaderAction
    data class AnnotationsLoaded(val annotations: List<SharedPdfAnnotation>) : SharedPdfReaderAction
    data class AnnotationAdded(val annotation: SharedPdfAnnotation) : SharedPdfReaderAction
    data class AnnotationSelected(val annotationId: String?) : SharedPdfReaderAction
    data class AnnotationUpdated(val annotation: SharedPdfAnnotation) : SharedPdfReaderAction
    data class AnnotationDeleted(val annotationId: String) : SharedPdfReaderAction
    data class AnnotationsChanged(val annotations: List<SharedPdfAnnotation>) : SharedPdfReaderAction
    data class UndoLastAnnotationOnPage(val pageIndex: Int) : SharedPdfReaderAction
    data object UndoAnnotationEdit : SharedPdfReaderAction
    data object RedoAnnotationEdit : SharedPdfReaderAction
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
        is SharedPdfReaderAction.ThemeChanged -> copy(themeId = action.themeId.ifBlank { "no_theme" })
        is SharedPdfReaderAction.ZoomChanged -> copy(zoom = zoomSpec.clamp(action.zoom))
        is SharedPdfReaderAction.ZoomBy -> copy(zoom = zoomSpec.clamp(zoom + action.delta))
        is SharedPdfReaderAction.ScrollLockChanged -> copy(
            isScrollLocked = action.locked,
            lockedZoomScale = action.zoomScale.takeIf { it.isFinite() }?.coerceIn(1f, 5f) ?: 1f,
            lockedZoomOffsetX = action.offsetX.takeIf { it.isFinite() } ?: 0f,
            lockedZoomOffsetY = action.offsetY.takeIf { it.isFinite() } ?: 0f
        )
        is SharedPdfReaderAction.SearchChanged -> {
            val normalized = action.query.trim()
            copy(
                isSearchActive = isSearchActive || normalized.isNotBlank(),
                showSearchResultsPanel = showSearchResultsPanel || normalized.isNotBlank(),
                searchQuery = action.query,
                activeSearchResultIndex = -1
            )
        }
        SharedPdfReaderAction.SearchOpened -> copy(
            isSearchActive = true,
            showSearchResultsPanel = true
        )
        SharedPdfReaderAction.SearchClosed -> copy(
            isSearchActive = false,
            showSearchResultsPanel = true,
            searchQuery = "",
            activeSearchResultIndex = -1
        )
        SharedPdfReaderAction.SearchResultsPanelToggled -> copy(showSearchResultsPanel = !showSearchResultsPanel)
        is SharedPdfReaderAction.SearchHighlightModeChanged -> copy(searchHighlightMode = action.mode)
        SharedPdfReaderAction.SearchHighlightModeToggled -> copy(
            searchHighlightMode = when (searchHighlightMode) {
                SearchHighlightMode.ALL -> SearchHighlightMode.FOCUSED
                SearchHighlightMode.FOCUSED -> SearchHighlightMode.ALL
            }
        )
        is SharedPdfReaderAction.GoToSearchResult -> {
            val result = action.results.getOrNull(action.resultIndex)
            if (result == null) {
                this
            } else {
                copy(
                    activeSearchResultIndex = action.resultIndex,
                    pageIndex = sharedPdfDisplayIndexFor(virtualPageLayout, result.pageIndex.coerceIn(0, lastPdfPageIndex))
                )
            }
        }
        is SharedPdfReaderAction.ToolSelected -> {
            val config = toolConfigFor(action.tool)
            copy(
                selectedTool = action.tool,
                selectedColorArgb = config.colorArgb,
                strokeWidth = config.strokeWidth,
                isTextSelectionMode = false,
                lastActivePenTool = if (action.tool.isSharedPdfPenTool) action.tool else lastActivePenTool,
                lastActiveHighlighterTool = if (action.tool.isSharedPdfHighlighterTool) {
                    action.tool
                } else {
                    lastActiveHighlighterTool
                }
            )
        }
        is SharedPdfReaderAction.ColorSelected -> withActiveToolColor(action.colorArgb)
        is SharedPdfReaderAction.StrokeWidthChanged -> withActiveToolStrokeWidth(action.strokeWidth.coerceAtLeast(0.0001f))
        is SharedPdfReaderAction.PenPaletteChanged -> copy(penPalette = action.colors.sanitizedSharedPdfPenPalette())
        is SharedPdfReaderAction.TextSelectionModeChanged -> {
            if (action.enabled) {
                val config = SharedPdfAnnotationDefaults.configFor(PdfInkTool.NONE)
                copy(
                    isTextSelectionMode = true,
                    selectedTool = PdfInkTool.NONE,
                    selectedColorArgb = config.colorArgb,
                    strokeWidth = config.strokeWidth
                )
            } else {
                copy(isTextSelectionMode = false)
            }
        }
        is SharedPdfReaderAction.BookmarksLoaded -> copy(bookmarks = action.bookmarks.normalizedBookmarks(lastPdfPageIndex))
        is SharedPdfReaderAction.BookmarkToggled -> {
            val page = action.pageIndex.coerceIn(0, lastPdfPageIndex)
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
            copy(bookmarks = nextBookmarks.normalizedBookmarks(lastPdfPageIndex))
        }
        is SharedPdfReaderAction.BookmarkRenamed -> {
            val page = action.pageIndex.coerceIn(0, lastPdfPageIndex)
            copy(
                bookmarks = bookmarks.map { bookmark ->
                    if (bookmark.pageIndex == page) bookmark.copy(label = action.label.trim().ifBlank { "Page ${page + 1}" }) else bookmark
                }.normalizedBookmarks(lastPdfPageIndex)
            )
        }
        is SharedPdfReaderAction.BookmarkDeleted -> {
            val page = action.pageIndex.coerceIn(0, lastPdfPageIndex)
            copy(bookmarks = bookmarks.filterNot { it.pageIndex == page }.normalizedBookmarks(lastPdfPageIndex))
        }
        is SharedPdfReaderAction.InsertBlankPageAt -> {
            val layout = virtualPageLayout
            val targetPdfIndex = sharedPdfNearestPdfPageIndex(layout, action.displayIndex)
                ?.coerceIn(0, (safePageCount - 1).coerceAtLeast(0)) ?: return this
            val insertion = SharedPdfBlankPageInsertion(
                afterPdfIndex = targetPdfIndex,
                widthPx = action.widthPx.coerceAtLeast(1f),
                heightPx = action.heightPx.coerceAtLeast(1f),
                id = action.id.ifBlank { "blank_${targetPdfIndex}_${kotlin.random.Random.nextLong()}" },
                wasManuallyAdded = action.wasManuallyAdded
            )
            val nextInsertions = blankPageInsertions + insertion
            val nextLayout = buildSharedPdfVirtualPageLayout(pageCount, nextInsertions)
            val insertedAt = nextLayout.indexOfFirst {
                it is SharedPdfVirtualPage.BlankPage && it.insertion.id == insertion.id
            }
            if (insertedAt < 0) return this
            copy(blankPageInsertions = nextInsertions, pageIndex = insertedAt).coerced(zoomSpec)
        }
        is SharedPdfReaderAction.DeleteBlankPageAt -> {
            val layout = virtualPageLayout
            val page = layout.getOrNull(action.displayIndex) as? SharedPdfVirtualPage.BlankPage ?: return this
            copy(
                blankPageInsertions = blankPageInsertions.filterNot { it.id == page.insertion.id },
                pageIndex = action.displayIndex.coerceAtMost((displayPageCount - 2).coerceAtLeast(0))
            ).coerced(zoomSpec)
        }
        is SharedPdfReaderAction.AnnotationsLoaded -> copy(
            annotations = action.annotations.toList(),
            annotationUndoStack = emptyList(),
            annotationRedoStack = emptyList()
        )
        is SharedPdfReaderAction.AnnotationAdded -> copy(
            annotations = annotations + action.annotation,
            selectedAnnotationId = action.annotation.id,
            annotationUndoStack = annotationUndoStack + SharedPdfAnnotationHistoryAction.Add(
                pageIndex = action.annotation.pageIndex,
                annotation = action.annotation
            ),
            annotationRedoStack = emptyList()
        )
        is SharedPdfReaderAction.AnnotationSelected -> copy(
            selectedAnnotationId = action.annotationId?.takeIf { id -> annotations.any { it.id == id } }
        )
        is SharedPdfReaderAction.AnnotationUpdated -> {
            val index = annotations.indexOfFirst { it.id == action.annotation.id }
            if (index < 0) {
                this
            } else {
                copy(
                    annotations = annotations.toMutableList().also { it[index] = action.annotation },
                    annotationRedoStack = emptyList()
                )
            }
        }
        is SharedPdfReaderAction.AnnotationDeleted -> {
            val removed = annotations.firstOrNull { it.id == action.annotationId }
            if (removed == null) {
                this
            } else {
                copy(
                    annotations = annotations.filterNot { it.id == action.annotationId },
                    selectedAnnotationId = selectedAnnotationId?.takeIf { it != action.annotationId },
                    annotationUndoStack = annotationUndoStack + SharedPdfAnnotationHistoryAction.Remove(
                        itemsByPage = mapOf(removed.pageIndex to listOf(removed))
                    ),
                    annotationRedoStack = emptyList()
                )
            }
        }
        is SharedPdfReaderAction.AnnotationsChanged -> copy(
            annotations = action.annotations.toList(),
            annotationUndoStack = emptyList(),
            annotationRedoStack = emptyList()
        )
        is SharedPdfReaderAction.UndoLastAnnotationOnPage -> {
            val index = annotations.indexOfLast { it.pageIndex == action.pageIndex }
            if (index < 0) {
                this
            } else {
                val removed = annotations[index]
                val removedId = annotations[index].id
                copy(
                    annotations = annotations.toMutableList().also { it.removeAt(index) },
                    selectedAnnotationId = selectedAnnotationId?.takeIf { it != removedId },
                    annotationUndoStack = annotationUndoStack + SharedPdfAnnotationHistoryAction.Remove(
                        itemsByPage = mapOf(removed.pageIndex to listOf(removed))
                    ),
                    annotationRedoStack = emptyList()
                )
            }
        }
        SharedPdfReaderAction.UndoAnnotationEdit -> undoSharedPdfAnnotationEdit()
        SharedPdfReaderAction.RedoAnnotationEdit -> redoSharedPdfAnnotationEdit()
        is SharedPdfReaderAction.ClearPageAnnotations -> {
            val removed = annotations.filter { it.pageIndex == action.pageIndex }
            if (removed.isEmpty()) {
                this
            } else {
                val removedIds = removed.mapTo(mutableSetOf()) { it.id }
                copy(
                    annotations = annotations.filterNot { it.pageIndex == action.pageIndex },
                    selectedAnnotationId = selectedAnnotationId?.takeIf { it !in removedIds },
                    annotationUndoStack = annotationUndoStack + SharedPdfAnnotationHistoryAction.Remove(
                        itemsByPage = mapOf(action.pageIndex to removed)
                    ),
                    annotationRedoStack = emptyList()
                )
            }
        }
    }.coerced(zoomSpec)
}

private fun SharedPdfReaderState.toolConfigFor(tool: PdfInkTool): PdfToolConfig {
    return toolConfigs[tool] ?: SharedPdfAnnotationDefaults.configFor(tool)
}

private fun SharedPdfReaderState.withActiveToolColor(colorArgb: Int): SharedPdfReaderState {
    if (!selectedTool.isSharedPdfConfigurableTool) {
        return copy(selectedColorArgb = colorArgb)
    }
    val currentConfig = toolConfigFor(selectedTool)
    return copy(
        selectedColorArgb = colorArgb,
        toolConfigs = toolConfigs + (selectedTool to currentConfig.copy(colorArgb = colorArgb))
    )
}

private fun SharedPdfReaderState.withActiveToolStrokeWidth(strokeWidth: Float): SharedPdfReaderState {
    if (!selectedTool.isSharedPdfConfigurableTool) {
        return copy(strokeWidth = strokeWidth)
    }
    val currentConfig = toolConfigFor(selectedTool)
    return copy(
        strokeWidth = strokeWidth,
        toolConfigs = toolConfigs + (selectedTool to currentConfig.copy(strokeWidth = strokeWidth))
    )
}

private fun List<Int>.sanitizedSharedPdfPenPalette(): List<Int> {
    val defaults = SharedPdfAnnotationDefaults.penPalette
    val normalized = filter { it != 0 }.take(defaults.size)
    val filled = if (normalized.isEmpty()) {
        defaults
    } else {
        normalized + defaults.drop(normalized.size)
    }
    return filled.take(defaults.size)
}

private val PdfInkTool.isSharedPdfPenTool: Boolean
    get() = this == PdfInkTool.FOUNTAIN_PEN || this == PdfInkTool.PEN || this == PdfInkTool.PENCIL

private val PdfInkTool.isSharedPdfHighlighterTool: Boolean
    get() = this == PdfInkTool.HIGHLIGHTER || this == PdfInkTool.HIGHLIGHTER_ROUND

private val PdfInkTool.isSharedPdfConfigurableTool: Boolean
    get() = this != PdfInkTool.NONE

private fun SharedPdfReaderState.undoSharedPdfAnnotationEdit(): SharedPdfReaderState {
    val action = annotationUndoStack.lastOrNull() ?: return this
    val nextUndoStack = annotationUndoStack.dropLast(1)
    return when (action) {
        is SharedPdfAnnotationHistoryAction.Add -> copy(
            annotations = annotations.filterNot { it.id == action.annotation.id },
            selectedAnnotationId = selectedAnnotationId?.takeIf { it != action.annotation.id },
            annotationUndoStack = nextUndoStack,
            annotationRedoStack = annotationRedoStack + action
        )

        is SharedPdfAnnotationHistoryAction.Remove -> copy(
            annotations = annotations + action.itemsByPage.values.flatten(),
            annotationUndoStack = nextUndoStack,
            annotationRedoStack = annotationRedoStack + action
        )
    }
}

private fun SharedPdfReaderState.redoSharedPdfAnnotationEdit(): SharedPdfReaderState {
    val action = annotationRedoStack.lastOrNull() ?: return this
    val nextRedoStack = annotationRedoStack.dropLast(1)
    return when (action) {
        is SharedPdfAnnotationHistoryAction.Add -> copy(
            annotations = annotations + action.annotation,
            selectedAnnotationId = action.annotation.id,
            annotationUndoStack = annotationUndoStack + action,
            annotationRedoStack = nextRedoStack
        )

        is SharedPdfAnnotationHistoryAction.Remove -> {
            val removedIds = action.itemsByPage.values.flatten().mapTo(mutableSetOf()) { it.id }
            copy(
                annotations = annotations.filterNot { it.id in removedIds },
                selectedAnnotationId = selectedAnnotationId?.takeIf { it !in removedIds },
                annotationUndoStack = annotationUndoStack + action,
                annotationRedoStack = nextRedoStack
            )
        }
    }
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
