package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.SearchHighlightMode

data class SharedPdfSearchResult(
    val pageIndex: Int,
    val preview: String,
    val matchIndex: Int
)

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
            zoom = zoomSpec.clamp(zoom)
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
    data class AnnotationsLoaded(val annotations: List<SharedPdfAnnotation>) : SharedPdfReaderAction
    data class AnnotationAdded(val annotation: SharedPdfAnnotation) : SharedPdfReaderAction
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
        is SharedPdfReaderAction.StrokeWidthChanged -> copy(strokeWidth = action.strokeWidth.coerceAtLeast(0.1f))
        is SharedPdfReaderAction.TextSelectionModeChanged -> copy(isTextSelectionMode = action.enabled)
        is SharedPdfReaderAction.AnnotationsLoaded -> copy(annotations = action.annotations.toList())
        is SharedPdfReaderAction.AnnotationAdded -> copy(annotations = annotations + action.annotation)
        is SharedPdfReaderAction.AnnotationsChanged -> copy(annotations = action.annotations.toList())
        is SharedPdfReaderAction.UndoLastAnnotationOnPage -> {
            val index = annotations.indexOfLast { it.pageIndex == action.pageIndex }
            if (index < 0) this else copy(annotations = annotations.toMutableList().also { it.removeAt(index) })
        }
        is SharedPdfReaderAction.ClearPageAnnotations -> {
            copy(annotations = annotations.filterNot { it.pageIndex == action.pageIndex })
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
                    matchIndex = matchIndex
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

private fun Int.wrapIndex(size: Int): Int {
    if (size <= 0) return -1
    return when {
        this < 0 -> size - 1
        this >= size -> 0
        else -> this
    }
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
