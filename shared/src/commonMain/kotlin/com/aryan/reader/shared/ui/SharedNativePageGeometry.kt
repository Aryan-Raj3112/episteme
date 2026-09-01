package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import kotlin.math.roundToInt

internal data class SharedNativeContentFit(
    val rootTopPx: Int,
    val heightPx: Int
)

internal data class SharedNativePageRenderGeometry(
    val readerWidthPx: Int,
    val readerHeightPx: Int,
    val pageOuterWidthPx: Int,
    val pageContentWidthPx: Int,
    val pageContentHeightPx: Int,
    val pageGapPx: Int,
    val horizontalMarginPx: Int,
    val verticalMarginPx: Int,
    val configuredPageWidthPx: Int,
    val visiblePageCount: Int,
    val spreadMode: String
)

/**
 * Android benchmark parity: a page slot that fills the reader width renders full-bleed
 * with no border, shadow, or corner shape. Page chrome is reserved for slots narrower
 * than the viewport (two-page spreads, capped page widths) where page edges are visible.
 * The 1px tolerance absorbs float rounding between the dp and px width computations.
 */
internal val SharedNativePageRenderGeometry.showsPageChrome: Boolean
    get() = pageOuterWidthPx < readerWidthPx - 1

internal data class SharedNativeTextFitLabel(
    val page: ReaderPage,
    val blockIndex: Int,
    val kind: String,
    val sourceRange: String,
    val textChars: Int
)

internal data class SharedNativeTextFit(
    val pageIndex: Int,
    val chapterIndex: Int,
    val blockIndex: Int,
    val kind: String,
    val sourceRange: String,
    val textChars: Int,
    val rootTopPx: Int,
    val boxWidthPx: Int,
    val boxHeightPx: Int,
    val layoutWidthPx: Int,
    val layoutHeightPx: Int,
    val lineCount: Int,
    val lastLineIndex: Int,
    val lastLineTopPx: Int,
    val lastLineBottomPx: Int,
    val lastLineStartOffset: Int,
    val lastLineEndOffset: Int
) {
    val key: String
        get() = "$pageIndex:$blockIndex:$sourceRange:$textChars"

    val layoutRootBottomPx: Int
        get() = rootTopPx + layoutHeightPx

    val lastLineRootBottomPx: Int
        get() = rootTopPx + lastLineBottomPx

    val overflowRootBottomPx: Int
        get() = maxOf(layoutRootBottomPx, lastLineRootBottomPx)

    fun lastLineOverflowPx(contentBottomRootPx: Int): Int {
        return overflowRootBottomPx - contentBottomRootPx
    }

    fun format(contentTopPx: Int, contentBottomRootPx: Int): String {
        val textBoxTopPx = rootTopPx - contentTopPx
        val textBoxBottomPx = textBoxTopPx + boxHeightPx
        val lineTopPx = rootTopPx + lastLineTopPx - contentTopPx
        val lineBottomPx = lastLineRootBottomPx - contentTopPx
        val layoutBottomPx = layoutRootBottomPx - contentTopPx
        val overflowBottomPx = overflowRootBottomPx - contentTopPx
        return "block=$blockIndex kind=$kind textBox=${boxWidthPx}x$boxHeightPx@top=$textBoxTopPx " +
            "textBoxBottom=$textBoxBottomPx layout=${layoutWidthPx}x$layoutHeightPx " +
            "layoutBottom=$layoutBottomPx lines=$lineCount lastLine=$lastLineIndex " +
            "lineTop=$lineTopPx lineBottom=$lineBottomPx overflowBottom=$overflowBottomPx " +
            "lineOverflowPx=${lastLineOverflowPx(contentBottomRootPx)} " +
            "lineOffsets=$lastLineStartOffset..$lastLineEndOffset range=$sourceRange textChars=$textChars"
    }
}

internal data class SharedNativeBlockFit(
    val index: Int,
    val kind: String,
    val blockIndex: Int,
    val sourceRange: String,
    val rootTopPx: Int,
    val heightPx: Int
) {
    fun relativeTopPx(contentTopPx: Int): Int = rootTopPx - contentTopPx

    fun relativeBottomPx(contentTopPx: Int): Int = relativeTopPx(contentTopPx) + heightPx

    fun format(contentTopPx: Int): String {
        val topPx = relativeTopPx(contentTopPx)
        val bottomPx = topPx + heightPx
        return "#$index:$kind(block=$blockIndex,top=$topPx,height=$heightPx,bottom=$bottomPx,range=$sourceRange)"
    }
}

internal fun SharedNativeTextFitLabel.toSharedNativeTextFit(
    coordinates: LayoutCoordinates,
    layout: TextLayoutResult
): SharedNativeTextFit {
    val lastLine = layout.lineCount - 1
    return SharedNativeTextFit(
        pageIndex = page.pageIndex,
        chapterIndex = page.chapterIndex,
        blockIndex = blockIndex,
        kind = kind,
        sourceRange = sourceRange,
        textChars = textChars,
        rootTopPx = coordinates.positionInRoot().y.roundToInt(),
        boxWidthPx = coordinates.size.width,
        boxHeightPx = coordinates.size.height,
        layoutWidthPx = layout.size.width,
        layoutHeightPx = layout.size.height,
        lineCount = layout.lineCount,
        lastLineIndex = lastLine,
        lastLineTopPx = if (lastLine >= 0) layout.getLineTop(lastLine).roundToInt() else 0,
        lastLineBottomPx = if (lastLine >= 0) layout.getLineBottom(lastLine).roundToInt() else layout.size.height,
        lastLineStartOffset = if (lastLine >= 0) layout.getLineStart(lastLine) else 0,
        lastLineEndOffset = if (lastLine >= 0) layout.getLineEnd(lastLine, visibleEnd = true) else 0
    )
}

internal fun SemanticBlock.toSharedNativeBlockFit(
    index: Int,
    coordinates: LayoutCoordinates
): SharedNativeBlockFit {
    return SharedNativeBlockFit(
        index = index,
        kind = sharedNativeKindName(),
        blockIndex = blockIndex,
        sourceRange = sharedNativeSourceRangeLabel(),
        rootTopPx = coordinates.positionInRoot().y.roundToInt(),
        heightPx = coordinates.size.height
    )
}

internal fun List<SharedNativeBlockFit>.renderedPageFitTail(contentTopPx: Int): String {
    return takeLast(EpubPageFitTailBlockCount).joinToString("|") { it.format(contentTopPx) }
}

internal fun SemanticBlock.sharedNativeTextFitCount(): Int {
    return when (this) {
        is SemanticTextBlock -> 1
        is SemanticList -> items.sumOf { it.sharedNativeTextFitCount() }
        is SemanticTable -> rows.sumOf { row -> row.sumOf { cell -> cell.content.sumOf { it.sharedNativeTextFitCount() } } }
        is SemanticFlexContainer -> children.sumOf { it.sharedNativeTextFitCount() }
        is SemanticWrappingBlock -> paragraphsToWrap.sumOf { it.sharedNativeTextFitCount() }
        is SemanticImage,
        is SemanticMath,
        is SemanticSpacer -> 0
    }
}

internal fun SemanticBlock.sharedNativeKindName(): String {
    return when (this) {
        is SemanticTextBlock -> when (this) {
            is SemanticHeader -> "header"
            is SemanticParagraph -> "paragraph"
            is SemanticListItem -> "list_item"
            else -> "text"
        }
        is SemanticList -> "list"
        is SemanticTable -> "table"
        is SemanticFlexContainer -> "flex"
        is SemanticWrappingBlock -> "wrapping"
        is SemanticImage -> "image"
        is SemanticMath -> "math"
        is SemanticSpacer -> "spacer"
    }
}

internal fun SemanticBlock.sharedNativeSourceRangeLabel(): String {
    return when (this) {
        is SemanticTextBlock -> {
            val start = startCharOffsetInSource
            "$start..${start + text.length}"
        }
        else -> cfi?.takeIf { it.isNotBlank() }
            ?: elementId?.takeIf { it.isNotBlank() }
            ?: "-"
    }.sharedNativeLogPreview(maxLength = 80)
}

internal fun String.sharedNativeLogPreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}

internal fun UserHighlight.nativeHighlightLogKey(): String {
    val normalizedLocator = this.locator.withFallbacks(
        chapterIndex = chapterIndex,
        cfi = cfi,
        textQuote = text
    )
    val page = normalizedLocator.pageIndex?.let { it + 1 }?.toString() ?: "null"
    return "id=\"${id.sharedNativeLogPreview(48)}\"" +
        ":chapter=${normalizedLocator.chapterIndex ?: "null"}" +
        ":page=$page" +
        ":offsets=${normalizedLocator.startOffset ?: "null"}..${normalizedLocator.endOffset ?: "null"}" +
        ":block=${normalizedLocator.blockIndex ?: "null"}" +
        ":char=${normalizedLocator.charOffset ?: "null"}" +
        ":text=\"${(normalizedLocator.textQuote ?: text).sharedNativeLogPreview(64)}\""
}
