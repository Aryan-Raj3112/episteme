package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSearchOptions
import com.aryan.reader.shared.reader.ReaderSettings

data class ReaderContentNavigationTarget(
    val locator: ReaderLocator?,
    val requestId: Long,
    val readingMode: ReaderReadingMode,
    val autoScroll: ReaderAutoScrollState = ReaderAutoScrollState(),
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
        override val highlights: List<UserHighlight>
    ) : ReaderContentRenderPlan
}
