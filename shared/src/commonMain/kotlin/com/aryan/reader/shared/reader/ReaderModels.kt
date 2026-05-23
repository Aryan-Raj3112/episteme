package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.SystemUiMode

data class SharedEpubBook(
    val id: String,
    val fileName: String,
    val title: String,
    val author: String? = null,
    val chapters: List<SharedEpubChapter>,
    val css: Map<String, String> = emptyMap(),
    val tableOfContents: List<SharedEpubTocEntry> = emptyList()
)

data class SharedEpubTocEntry(
    val label: String,
    val href: String,
    val fragmentId: String? = null,
    val depth: Int = 0
)

data class SharedEpubChapter(
    val id: String,
    val title: String,
    val plainText: String,
    val semanticBlocks: List<SemanticBlock> = emptyList(),
    val htmlContent: String = "",
    val baseHref: String? = null
)

typealias ReaderLocator = com.aryan.reader.shared.ReaderLocator

enum class ReaderReadingMode {
    PAGINATED,
    VERTICAL
}

enum class ReaderPageSpreadMode {
    SINGLE,
    TWO_PAGE
}

enum class SharedReaderTextAlign {
    START,
    RIGHT,
    JUSTIFY,
    CENTER
}

data class ReaderSettings(
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.45f,
    val margin: Int = 48,
    val darkMode: Boolean = false,
    val readingMode: ReaderReadingMode = ReaderReadingMode.VERTICAL,
    val textAlign: SharedReaderTextAlign = SharedReaderTextAlign.START,
    val pageWidth: Int = 760,
    val fontFamily: String = "Default",
    val paragraphSpacing: Float = 1.0f,
    val imageScale: Float = 1.0f,
    val horizontalMargin: Int? = null,
    val verticalMargin: Int? = null,
    val themeId: String? = null,
    val textureId: String? = null,
    val textureAlpha: Float = 0.55f,
    val customFontPath: String? = null,
    val backgroundColorArgb: Long? = null,
    val textColorArgb: Long? = null,
    val systemUiMode: SystemUiMode = SystemUiMode.DEFAULT,
    val pageInfoMode: PageInfoMode = PageInfoMode.DEFAULT,
    val pageInfoPosition: PageInfoPosition = PageInfoPosition.BOTTOM,
    val pageSpreadMode: ReaderPageSpreadMode = ReaderPageSpreadMode.SINGLE,
    val rightToLeftPagination: Boolean = false,
    val pdfVerticalPageGapVisible: Boolean = true,
    val pdfPageNumberOverlayVisible: Boolean = true,
    val pdfFirstPageStandaloneInSpread: Boolean = false,
    val seamlessChapterNavigation: Boolean = true,
    val chapterTurnDragMultiplier: Float = 1.0f
) {
    val resolvedHorizontalMargin: Int get() = horizontalMargin ?: margin
    val resolvedVerticalMargin: Int get() = verticalMargin ?: margin
}

data class ReaderLayoutSignature(
    val fontSize: Int,
    val lineSpacing: Float,
    val horizontalMargin: Int,
    val verticalMargin: Int,
    val readingMode: ReaderReadingMode,
    val textAlign: SharedReaderTextAlign,
    val pageWidth: Int,
    val fontFamily: String,
    val paragraphSpacing: Float,
    val imageScale: Float,
    val pageSpreadMode: ReaderPageSpreadMode,
    val customFontPath: String?
)

data class ReaderAppearanceSignature(
    val darkMode: Boolean,
    val themeId: String?,
    val textureId: String?,
    val textureAlpha: Float,
    val backgroundColorArgb: Long?,
    val textColorArgb: Long?
)

fun ReaderSettings.layoutSignature(): ReaderLayoutSignature {
    return ReaderLayoutSignature(
        fontSize = fontSize,
        lineSpacing = lineSpacing,
        horizontalMargin = resolvedHorizontalMargin,
        verticalMargin = resolvedVerticalMargin,
        readingMode = readingMode,
        textAlign = textAlign,
        pageWidth = pageWidth,
        fontFamily = fontFamily,
        paragraphSpacing = paragraphSpacing,
        imageScale = imageScale,
        pageSpreadMode = pageSpreadMode,
        customFontPath = customFontPath
    )
}

fun ReaderSettings.appearanceSignature(): ReaderAppearanceSignature {
    return ReaderAppearanceSignature(
        darkMode = darkMode,
        themeId = themeId,
        textureId = textureId,
        textureAlpha = textureAlpha,
        backgroundColorArgb = backgroundColorArgb,
        textColorArgb = textColorArgb
    )
}

data class ReaderViewportSpec(
    val widthPx: Int,
    val heightPx: Int
) {
    val isSpecified: Boolean get() = widthPx > 0 && heightPx > 0
}

data class ReaderPage(
    val pageIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val semanticBlocks: List<SemanticBlock> = emptyList()
)

data class PaginatedReaderState(
    val book: SharedEpubBook,
    val pages: List<ReaderPage>,
    val currentPageIndex: Int = 0,
    val settings: ReaderSettings = ReaderSettings()
) {
    val currentPage: ReaderPage? get() = pages.getOrNull(currentPageIndex)
    val progress: Float
        get() {
            if (pages.isEmpty()) return 0f
            val visibleEnd = ReaderSpreadLayout.visiblePageIndices(currentPageIndex, pages.size, settings)
                .lastOrNull()
                ?: currentPageIndex
            return ((visibleEnd + 1).toFloat() / pages.size) * 100f
        }
    val canGoPrevious: Boolean get() = currentPageIndex > 0
    val canGoNext: Boolean get() = ReaderSpreadLayout.canGoNext(currentPageIndex, pages.size, settings)
    val currentSpreadStartIndex: Int get() = ReaderSpreadLayout.normalizePageIndex(currentPageIndex, pages.size, settings)
    val visiblePages: List<ReaderPage>
        get() = ReaderSpreadLayout.visiblePageIndicesForDisplay(currentPageIndex, pages.size, settings)
            .mapNotNull { pages.getOrNull(it) }
}

object ReaderSpreadLayout {
    fun pageStep(settings: ReaderSettings): Int {
        return if (settings.isTwoPageSpreadEnabled()) 2 else 1
    }

    fun normalizePageIndex(pageIndex: Int, pageCount: Int, settings: ReaderSettings): Int {
        if (pageCount <= 0) return 0
        val clamped = pageIndex.coerceIn(0, pageCount - 1)
        return if (settings.isTwoPageSpreadEnabled()) {
            (clamped - (clamped % 2)).coerceIn(0, pageCount - 1)
        } else {
            clamped
        }
    }

    fun canGoNext(pageIndex: Int, pageCount: Int, settings: ReaderSettings): Boolean {
        if (pageCount <= 1) return false
        val current = normalizePageIndex(pageIndex, pageCount, settings)
        return current + pageStep(settings) < pageCount
    }

    fun nextPageIndex(pageIndex: Int, pageCount: Int, settings: ReaderSettings): Int {
        return normalizePageIndex(pageIndex + pageStep(settings), pageCount, settings)
    }

    fun previousPageIndex(pageIndex: Int, pageCount: Int, settings: ReaderSettings): Int {
        return normalizePageIndex(pageIndex - pageStep(settings), pageCount, settings)
    }

    fun visiblePageIndices(pageIndex: Int, pageCount: Int, settings: ReaderSettings): List<Int> {
        if (pageCount <= 0) return emptyList()
        val start = normalizePageIndex(pageIndex, pageCount, settings)
        if (!settings.isTwoPageSpreadEnabled()) return listOf(start)
        return listOf(start, start + 1).filter { it in 0 until pageCount }
    }

    fun visiblePageIndicesForDisplay(pageIndex: Int, pageCount: Int, settings: ReaderSettings): List<Int> {
        val indices = visiblePageIndices(pageIndex, pageCount, settings)
        return if (settings.isRightToLeftPaginationEnabled()) indices.asReversed() else indices
    }

    fun pageRangeLabel(pageIndex: Int, pageCount: Int, settings: ReaderSettings): String {
        val total = pageCount.coerceAtLeast(1)
        val pages = visiblePageIndices(pageIndex, total, settings).ifEmpty { listOf(0) }
        val first = pages.first() + 1
        val last = pages.last() + 1
        return if (first == last) "$first" else "$first-$last"
    }

    fun sliderStepCount(pageCount: Int, settings: ReaderSettings): Int {
        val total = pageCount.coerceAtLeast(1)
        return if (settings.isTwoPageSpreadEnabled()) {
            (total + 1) / 2
        } else {
            total
        }
    }

    fun sliderPositionForPage(pageIndex: Int, pageCount: Int, settings: ReaderSettings): Int {
        val normalized = normalizePageIndex(pageIndex, pageCount, settings)
        val position = if (settings.isTwoPageSpreadEnabled()) {
            (normalized / 2) + 1
        } else {
            normalized + 1
        }
        return position.coerceIn(1, sliderStepCount(pageCount, settings))
    }

    fun pageNumberForSliderPosition(position: Int, pageCount: Int, settings: ReaderSettings): Int {
        val clamped = position.coerceIn(1, sliderStepCount(pageCount, settings))
        val pageIndex = if (settings.isTwoPageSpreadEnabled()) {
            (clamped - 1) * 2
        } else {
            clamped - 1
        }
        return normalizePageIndex(pageIndex, pageCount, settings) + 1
    }
}

fun ReaderSettings.isTwoPageSpreadEnabled(): Boolean {
    return readingMode == ReaderReadingMode.PAGINATED && pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE
}

fun ReaderSettings.isRightToLeftPaginationEnabled(): Boolean {
    return readingMode == ReaderReadingMode.PAGINATED && rightToLeftPagination
}
