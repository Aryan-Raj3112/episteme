package com.aryan.reader.shared.ui

import com.aryan.reader.paginatedreader.EpubChapterTextIndex
import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSearchOptions
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook

data class ReaderContentNavigationTarget(
    val locator: ReaderLocator?,
    val requestId: Long,
    val readingMode: ReaderReadingMode,
    val ttsLocator: ReaderLocator? = null,
    val ttsRequestId: Long = 0L
)

sealed interface ReaderContentRenderPlan {
    val background: Color
    val foreground: Color
    val navigationTarget: ReaderContentNavigationTarget
    val highlights: List<UserHighlight>

    data class WebDocument(
        val html: String,
        val appearanceScript: String,
        val highlightPaletteScript: String,
        override val background: Color,
        override val foreground: Color,
        override val navigationTarget: ReaderContentNavigationTarget,
        override val highlights: List<UserHighlight>
    ) : ReaderContentRenderPlan

    data class NativePaginatedPages(
        val visiblePages: List<ReaderPage>,
        val settings: ReaderSettings,
        val searchQuery: String,
        val searchOptions: ReaderSearchOptions,
        val highlightPalette: ReaderHighlightPalette,
        override val background: Color,
        override val foreground: Color,
        override val navigationTarget: ReaderContentNavigationTarget,
        override val highlights: List<UserHighlight>,
        /**
         * Each chapter's text laid out in one coordinate space, keyed by chapter.
         *
         * Highlight placement needs this: block offsets as parsed are element-relative (§4), so
         * placing a highlight by comparing a stored range against a block's reported offsets compares
         * two unrelated spaces. Null or missing for a chapter falls back to the legacy per-block
         * chain, which is what desktop still uses.
         */
        val chapterTextIndexes: Map<Int, EpubChapterTextIndex> = emptyMap()
    ) : ReaderContentRenderPlan

    data class NativeVerticalPages(
        val book: SharedEpubBook,
        val pages: List<ReaderPage>,
        val currentPageIndex: Int,
        val settings: ReaderSettings,
        val searchQuery: String,
        val searchOptions: ReaderSearchOptions,
        val highlightPalette: ReaderHighlightPalette,
        override val background: Color,
        override val foreground: Color,
        override val navigationTarget: ReaderContentNavigationTarget,
        override val highlights: List<UserHighlight>,
        /** See [NativePaginatedPages.chapterTextIndexes]. */
        val chapterTextIndexes: Map<Int, EpubChapterTextIndex> = emptyMap()
    ) : ReaderContentRenderPlan
}
