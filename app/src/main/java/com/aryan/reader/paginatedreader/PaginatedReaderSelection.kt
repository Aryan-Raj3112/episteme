// PaginatedReader.kt
@file:Suppress("VariableNeverRead")

package com.aryan.reader.paginatedreader

import android.os.Build
import androidx.compose.ui.unit.isSpecified
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.plainTextCharacterCount
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.reader.paintOnlyColorOverlayText
import com.aryan.reader.shared.reader.withoutForegroundColorSpans
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.net.URI
import java.net.URLDecoder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PaginatedSelection(
    val startBlockIndex: Int,
    val endBlockIndex: Int,
    val startBaseCfi: String,
    val endBaseCfi: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val rect: Rect,
    val startPageIndex: Int,
    val endPageIndex: Int,
    val startBlockCharOffset: Int = 0,
    val endBlockCharOffset: Int = 0,
    val textPerBlock: Map<String, String> = emptyMap()
)

internal fun PaginatedSelection.toSharedHighlightLocator(
    chapterIndex: Int?,
    cfi: String
): SharedReaderLocator {
    val startAbsoluteOffset = startBlockCharOffset + startOffset
    val endAbsoluteOffset = endBlockCharOffset + endOffset
    val rangeStart = minOf(startAbsoluteOffset, endAbsoluteOffset)
    val rangeEnd = maxOf(startAbsoluteOffset, endAbsoluteOffset)
    return SharedReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = startPageIndex,
        startOffset = rangeStart,
        endOffset = rangeEnd,
        blockIndex = startBlockIndex.takeIf { it >= 0 },
        charOffset = rangeStart,
        textQuote = text,
        cfi = cfi
    )
}
data class NativeVerticalLocation(
    val locator: Locator?,
    val chapterIndex: Int?,
    val progressPercent: Float,
    val compatPageIndex: Int,
    val compatTotalPages: Int,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val firstVisibleItemSize: Int,
    val isAtStart: Boolean,
    val isAtEnd: Boolean,
    val visibleTextRanges: List<NativeVerticalVisibleTextRange> = emptyList(),
    val chapterPageInfo: NativeVerticalChapterPageInfo? = null
)

data class NativeVerticalVisibleTextRange(
    val chapterIndex: Int,
    val blockIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int
)

fun NativeVerticalLocation.locatorForPersistence(): Locator? {
    val visibleRange = visibleTextRanges.firstOrNull()
    return if (visibleRange != null) {
        Locator(
            chapterIndex = visibleRange.chapterIndex,
            blockIndex = visibleRange.blockIndex,
            charOffset = visibleRange.startCharOffset
        )
    } else {
        locator
    }
}

internal data class SelectionBlockKey(
    val pageIndex: Int,
    val blockIndex: Int,
    val blockCharOffset: Int
)

internal data class NativeVerticalViewportSample(
    val firstVisiblePageIndex: Int,
    val firstVisiblePageScrollOffset: Int,
    val firstVisibleItemSize: Int,
    val isAtStart: Boolean,
    val isAtEnd: Boolean,
    val totalPageCount: Int,
    val layoutTick: Int,
    val initialScrollComplete: Boolean
)

internal data class AndroidEpubPageContentBounds(
    val topPx: Int,
    val bottomPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val horizontalPaddingPx: Int,
    val verticalPaddingPx: Int
)

internal val AndroidEpubPageContentBounds.pageClipBottomPx: Int
    get() = bottomPx + verticalPaddingPx

internal data class AndroidEpubRenderedBlockBounds(
    val blockIndex: Int,
    val kind: String,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val expectedHeightPx: Int,
    val sourceRange: String,
    val textChars: Int,
    val marginTopPx: Int,
    val marginBottomPx: Int,
    val paddingTopPx: Int,
    val paddingBottomPx: Int
) {
    val bottomPx: Int = topPx + heightPx
}

internal fun buildSelectionBlockKey(
    pageIndex: Int,
    blockIndex: Int,
    blockCharOffset: Int
): String = "${pageIndex}_${blockIndex}_${blockCharOffset}"

internal fun parseSelectionBlockKey(key: String): SelectionBlockKey? {
    val parts = key.split("_")
    if (parts.size != 3) return null
    return SelectionBlockKey(
        pageIndex = parts[0].toIntOrNull() ?: return null,
        blockIndex = parts[1].toIntOrNull() ?: return null,
        blockCharOffset = parts[2].toIntOrNull() ?: return null
    )
}

internal fun compareSelectionBlockKeys(
    firstKey: String,
    secondKey: String
): Int {
    val first = parseSelectionBlockKey(firstKey)
    val second = parseSelectionBlockKey(secondKey)

    if (first == null && second == null) return firstKey.compareTo(secondKey)
    if (first == null) return 1
    if (second == null) return -1

    return compareValuesBy(
        first,
        second,
        SelectionBlockKey::pageIndex,
        SelectionBlockKey::blockIndex,
        SelectionBlockKey::blockCharOffset
    )
}

internal fun getTextBlockCharOffset(block: TextContentBlock): Int = when (block) {
    is ParagraphBlock -> block.startCharOffsetInSource
    is HeaderBlock -> block.startCharOffsetInSource
    is QuoteBlock -> block.startCharOffsetInSource
    is ListItemBlock -> block.startCharOffsetInSource
}

internal fun textBlockLayoutKey(
    cfi: String,
    pageIndex: Int,
    block: TextContentBlock
): String = "${cfi}_${block.blockIndex}_${getTextBlockCharOffset(block)}_${block.content.text.length}_$pageIndex"

internal fun legacyTextBlockLayoutKey(cfi: String, pageIndex: Int): String = "${cfi}_$pageIndex"

internal fun headerFontScale(level: Int): Float = when (level) {
    1 -> 1.5f
    2 -> 1.4f
    3 -> 1.3f
    4 -> 1.2f
    5 -> 1.1f
    else -> 1.0f
}

internal const val WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER = 1.2f
internal const val ReaderUiCutoffLogTag = "EpistemeEpubCutoff"
internal const val ReaderUiPageGapDiagLogTag = "EpistemePageGapDiag"
internal const val AndroidEpubEdgeDiagLogTag = "ReaderEdgeDiag"
internal const val AndroidEpubCutoffTolerancePx = 1
internal const val AndroidEpubCutoffEdgeProbePx = 2
internal const val AndroidEpubLargeBottomGapMinPx = 72
internal const val AndroidEpubLargeBottomGapPageFraction = 0.18f
internal const val AndroidEpubWrapNarrowWidthFraction = 0.45f
internal const val AndroidEpubWrapShortLineFraction = 0.28f
internal const val READER_UI_STABLE_PAGE_NAV_TAG = "StablePageNav"
internal const val TAG_PAGINATED_HIGHLIGHT_DIAG = "PaginatedHighlightDiag"
internal const val TAG_ANDROID_HIGHLIGHT_RENDER_DIAG = "AndroidHighlightRenderDiag"
internal const val TAG_READER_INTERACTION_DIAG = "ReaderInteractionDiag"
internal object ReaderSelectionHandleOverlayAlignment : Alignment {
    override fun align(
        size: IntSize,
        space: IntSize,
        layoutDirection: LayoutDirection
    ): IntOffset = IntOffset.Zero
}
internal const val EXPLICIT_NAVIGATION_SHIFT_ANCHOR_WINDOW_MS = 10_000L
internal const val DEBUG_PAGE_TURN_DIAG = false
internal const val NATIVE_VERTICAL_UI_LOAD_LOG_TAG = "NativeVerticalLoad"

internal fun highlightDiagSnippet(text: String, maxLength: Int = 80): String {
    return text
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .take(maxLength)
}

internal fun UserHighlight.androidHighlightRenderLabel(): String {
    val highlightLocator = this.locator
    return "highlightId=$id highlightChapter=$chapterIndex " +
        "highlightCfi=${highlightDiagSnippet(cfi, 120)} textLen=${text.length} " +
        "text='${highlightDiagSnippet(text)}' " +
        "locatorChapter=${highlightLocator.chapterIndex} locatorPage=${highlightLocator.pageIndex} " +
        "locatorOffsets=${highlightLocator.startOffset}..${highlightLocator.endOffset} " +
        "locatorBlock=${highlightLocator.blockIndex} locatorChar=${highlightLocator.charOffset} " +
        "locatorCfi=${highlightDiagSnippet(highlightLocator.cfi.orEmpty(), 120)}"
}

internal fun paginationLineHeightMultiplierForWebViewSetting(multiplier: Float): Float {
    return if (abs(multiplier - 1.0f) < 0.001f) WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER else multiplier
}

internal fun createHeaderTextStyle(
    baseStyle: TextStyle,
    level: Int,
    textAlign: TextAlign?
): TextStyle {
    val scale = headerFontScale(level)
    val scaledFontSize = baseStyle.fontSize * scale
    val scaledLineHeight = if (baseStyle.lineHeight != TextUnit.Unspecified) {
        baseStyle.lineHeight * scale
    } else {
        scaledFontSize * 1.2f
    }

    return baseStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = scaledFontSize,
        lineHeight = scaledLineHeight,
        textAlign = textAlign ?: baseStyle.textAlign
    )
}

internal fun compareBlockPositionsOnPage(
    firstBlockIndex: Int,
    firstBlockCharOffset: Int,
    secondBlockIndex: Int,
    secondBlockCharOffset: Int
): Int = when {
    firstBlockIndex != secondBlockIndex -> firstBlockIndex.compareTo(secondBlockIndex)
    else -> firstBlockCharOffset.compareTo(secondBlockCharOffset)
}

internal fun isBlockSelectedOnPage(
    block: TextContentBlock,
    pageIndex: Int,
    selection: PaginatedSelection
): Boolean {
    if (pageIndex < selection.startPageIndex || pageIndex > selection.endPageIndex) return false
    if (pageIndex > selection.startPageIndex && pageIndex < selection.endPageIndex) return true

    val blockCharOffset = getTextBlockCharOffset(block)
    val afterStart = if (pageIndex == selection.startPageIndex) {
        compareBlockPositionsOnPage(
            block.blockIndex,
            blockCharOffset,
            selection.startBlockIndex,
            selection.startBlockCharOffset
        ) >= 0
    } else {
        true
    }
    val beforeEnd = if (pageIndex == selection.endPageIndex) {
        compareBlockPositionsOnPage(
            block.blockIndex,
            blockCharOffset,
            selection.endBlockIndex,
            selection.endBlockCharOffset
        ) <= 0
    } else {
        true
    }

    return afterStart && beforeEnd
}

internal fun isSelectionBlockKeyInsideSelection(
    key: SelectionBlockKey,
    selection: PaginatedSelection
): Boolean {
    if (key.pageIndex < selection.startPageIndex || key.pageIndex > selection.endPageIndex) return false
    if (key.pageIndex > selection.startPageIndex && key.pageIndex < selection.endPageIndex) return true

    val afterStart = if (key.pageIndex == selection.startPageIndex) {
        compareBlockPositionsOnPage(
            key.blockIndex,
            key.blockCharOffset,
            selection.startBlockIndex,
            selection.startBlockCharOffset
        ) >= 0
    } else {
        true
    }
    val beforeEnd = if (key.pageIndex == selection.endPageIndex) {
        compareBlockPositionsOnPage(
            key.blockIndex,
            key.blockCharOffset,
            selection.endBlockIndex,
            selection.endBlockCharOffset
        ) <= 0
    } else {
        true
    }

    return afterStart && beforeEnd
}

internal data class AttachedSelectionBlock(
    val pageIndex: Int,
    val layout: TextLayoutResult,
    val coords: LayoutCoordinates,
    val block: TextContentBlock
)

internal fun attachedSelectionBlocks(
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    pageFilter: (Int) -> Boolean = { true }
): List<AttachedSelectionBlock> {
    return blockLayoutMap.entries
        .asSequence()
        .mapNotNull { (key, layoutInfo) ->
            val pageIndex = key.substringAfterLast("_").toIntOrNull()
                ?: return@mapNotNull null
            if (!pageFilter(pageIndex)) return@mapNotNull null
            val (layout, coords, block) = layoutInfo
            if (!coords.isAttached || block.cfi == null) return@mapNotNull null
            AttachedSelectionBlock(
                pageIndex = pageIndex,
                layout = layout,
                coords = coords,
                block = block
            )
        }
        .sortedWith(
            compareBy<AttachedSelectionBlock> { it.pageIndex }
                .thenBy { it.block.blockIndex }
                .thenBy { getTextBlockCharOffset(it.block) }
        )
        .toList()
}

internal fun visibleSelectedBlocks(
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    selection: PaginatedSelection
): List<AttachedSelectionBlock> {
    return attachedSelectionBlocks(blockLayoutMap) { pageIndex ->
        pageIndex in selection.startPageIndex..selection.endPageIndex
    }.filter { blockInfo ->
        isBlockSelectedOnPage(blockInfo.block, blockInfo.pageIndex, selection)
    }
}

internal fun selectionWindowBounds(
    selection: PaginatedSelection,
    selectedBlocks: List<AttachedSelectionBlock>,
    extraBottomPaddingPx: Float = 0f
): Rect {
    var minLeft = Float.POSITIVE_INFINITY
    var minTop = Float.POSITIVE_INFINITY
    var maxRight = Float.NEGATIVE_INFINITY
    var maxBottom = Float.NEGATIVE_INFINITY

    selectedBlocks.forEach { blockInfo ->
        val textLayout = blockInfo.layout
        val coords = blockInfo.coords
        val block = blockInfo.block
        val currentBlockAbs = getTextBlockCharOffset(block)
        val isStartBlockPart =
            blockInfo.pageIndex == selection.startPageIndex &&
                block.blockIndex == selection.startBlockIndex &&
                currentBlockAbs == selection.startBlockCharOffset
        val isEndBlockPart =
            blockInfo.pageIndex == selection.endPageIndex &&
                block.blockIndex == selection.endBlockIndex &&
                currentBlockAbs == selection.endBlockCharOffset

        val blockStartOffset = if (isStartBlockPart) selection.startOffset else 0
        val blockEndOffset = if (isEndBlockPart) selection.endOffset else textLayout.layoutInput.text.length

        val textLen = textLayout.layoutInput.text.length
        val safeStart = blockStartOffset.coerceIn(0, textLen)
        val safeEnd = blockEndOffset.coerceIn(safeStart, textLen)
        if (safeStart >= safeEnd) return@forEach

        try {
            val localBounds = textLayout.getPathForRange(safeStart, safeEnd).getBounds()
            val topLeftWin = coords.localToWindow(localBounds.topLeft)
            val bottomRightWin = coords.localToWindow(localBounds.bottomRight)
            minLeft = minOf(minLeft, topLeftWin.x, bottomRightWin.x)
            minTop = minOf(minTop, topLeftWin.y, bottomRightWin.y)
            maxRight = maxOf(maxRight, topLeftWin.x, bottomRightWin.x)
            maxBottom = maxOf(maxBottom, topLeftWin.y, bottomRightWin.y)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating exact selection bounds")
        }
    }

    return if (minTop != Float.POSITIVE_INFINITY && maxBottom != Float.NEGATIVE_INFINITY) {
        Rect(minLeft, minTop, maxRight, maxBottom + extraBottomPaddingPx)
    } else {
        Rect(
            selection.rect.left,
            selection.rect.top,
            selection.rect.right,
            selection.rect.bottom + extraBottomPaddingPx
        )
    }
}

internal fun findSelectionLayout(
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    cfi: String,
    pageIndex: Int,
    blockCharOffset: Int
): Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? {
    blockLayoutMap[legacyTextBlockLayoutKey(cfi, pageIndex)]?.takeIf {
        getTextBlockCharOffset(it.third) == blockCharOffset
    }?.let { return it }

    return blockLayoutMap.entries.firstOrNull { (key, layoutInfo) ->
        key.substringAfterLast("_").toIntOrNull() == pageIndex &&
            layoutInfo.third.cfi == cfi &&
            getTextBlockCharOffset(layoutInfo.third) == blockCharOffset
    }?.value
}

internal fun selectionHandleRootPosition(
    selection: PaginatedSelection,
    isStart: Boolean,
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    rootCoords: LayoutCoordinates?
): Offset {
    val handlePageIndex = if (isStart) selection.startPageIndex else selection.endPageIndex
    val selCfi = if (isStart) selection.startBaseCfi else selection.endBaseCfi
    val selOffset = if (isStart) selection.startOffset else selection.endOffset
    val targetBlockAbs = if (isStart) selection.startBlockCharOffset else selection.endBlockCharOffset
    val layoutInfo = findSelectionLayout(
        blockLayoutMap = blockLayoutMap,
        cfi = selCfi,
        pageIndex = handlePageIndex,
        blockCharOffset = targetBlockAbs
    )
    val root = rootCoords

    if (layoutInfo == null || !layoutInfo.second.isAttached || root == null || !root.isAttached) {
        return Offset.Unspecified
    }

    return try {
        val textLayout = layoutInfo.first
        val coords = layoutInfo.second
        val maxIdx = maxOf(0, textLayout.layoutInput.text.length - 1)
        val safeOffset = selOffset.coerceIn(0, textLayout.layoutInput.text.length)
        val safeOffsetForLine = safeOffset.coerceIn(0, maxIdx)
        val line = textLayout.getLineForOffset(safeOffsetForLine)
        val x = textLayout.getHorizontalPosition(safeOffset, usePrimaryDirection = true)
        val y = textLayout.getLineBottom(line)
        val windowPos = coords.localToWindow(Offset(x, y))
        root.windowToLocal(windowPos)
    } catch (_: Exception) {
        Offset.Unspecified
    }
}

internal fun updatedSelectionForHandleDrag(
    selection: PaginatedSelection,
    windowPos: Offset,
    currentDragHandle: SelectionHandle,
    attachedBlocks: List<AttachedSelectionBlock>,
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>
): Pair<PaginatedSelection, SelectionHandle>? {
    var activeDragHandle = currentDragHandle
    if (attachedBlocks.isEmpty()) return null

    val targetBlockInfo = attachedBlocks.minByOrNull { blockInfo ->
        val coords = blockInfo.coords
        val rect = Rect(coords.positionInWindow(), coords.size.toSize())
        val dx = maxOf(rect.left - windowPos.x, 0f, windowPos.x - rect.right)
        val dy = maxOf(rect.top - windowPos.y, 0f, windowPos.y - rect.bottom)
        dx * dx + dy * dy
    } ?: return null

    val textLayout = targetBlockInfo.layout
    val coords = targetBlockInfo.coords
    val block = targetBlockInfo.block
    val localPos = coords.windowToLocal(windowPos)
    val offset = textLayout.getOffsetForPosition(localPos)
        .coerceIn(0, textLayout.layoutInput.text.length)

    val isStartHandle = activeDragHandle == SelectionHandle.START
    var newStartIdx = if (isStartHandle) block.blockIndex else selection.startBlockIndex
    var newEndIdx = if (isStartHandle) selection.endBlockIndex else block.blockIndex
    var newStartOffset = if (isStartHandle) offset else selection.startOffset
    var newEndOffset = if (isStartHandle) selection.endOffset else offset
    var newStartCfi = if (isStartHandle) block.cfi!! else selection.startBaseCfi
    var newEndCfi = if (isStartHandle) selection.endBaseCfi else block.cfi!!
    var newStartPageIdx = if (isStartHandle) targetBlockInfo.pageIndex else selection.startPageIndex
    var newEndPageIdx = if (isStartHandle) selection.endPageIndex else targetBlockInfo.pageIndex

    val currentBlockAbs = getTextBlockCharOffset(block)
    var newStartBlockAbs = if (isStartHandle) currentBlockAbs else selection.startBlockCharOffset
    var newEndBlockAbs = if (!isStartHandle) currentBlockAbs else selection.endBlockCharOffset

    val isReversed = when {
        newStartPageIdx != newEndPageIdx -> newStartPageIdx > newEndPageIdx
        else -> {
            val blockCompare = compareBlockPositionsOnPage(
                newStartIdx,
                newStartBlockAbs,
                newEndIdx,
                newEndBlockAbs
            )
            if (blockCompare != 0) blockCompare > 0 else newStartOffset > newEndOffset
        }
    }

    if (isReversed) {
        newStartPageIdx = newEndPageIdx.also { newEndPageIdx = newStartPageIdx }
        newStartIdx = newEndIdx.also { newEndIdx = newStartIdx }
        newStartOffset = newEndOffset.also { newEndOffset = newStartOffset }
        newStartCfi = newEndCfi.also { newEndCfi = newStartCfi }
        newStartBlockAbs = newEndBlockAbs.also { newEndBlockAbs = newStartBlockAbs }
        activeDragHandle = if (activeDragHandle == SelectionHandle.START) SelectionHandle.END else SelectionHandle.START
    }

    if (
        newStartPageIdx == selection.startPageIndex &&
        newEndPageIdx == selection.endPageIndex &&
        newStartIdx == selection.startBlockIndex &&
        newEndIdx == selection.endBlockIndex &&
        newStartOffset == selection.startOffset &&
        newEndOffset == selection.endOffset
    ) {
        return null
    }

    val tentativeSelection = selection.copy(
        startBlockIndex = newStartIdx,
        endBlockIndex = newEndIdx,
        startBaseCfi = newStartCfi,
        endBaseCfi = newEndCfi,
        startOffset = newStartOffset,
        endOffset = newEndOffset,
        startPageIndex = newStartPageIdx,
        endPageIndex = newEndPageIdx,
        startBlockCharOffset = newStartBlockAbs,
        endBlockCharOffset = newEndBlockAbs
    )

    val relevantBlocks = attachedBlocks
        .filter { isBlockSelectedOnPage(it.block, it.pageIndex, tentativeSelection) }
        .sortedWith(
            compareBy<AttachedSelectionBlock> { it.pageIndex }
                .thenBy { it.block.blockIndex }
                .thenBy { getTextBlockCharOffset(it.block) }
        )

    val attachedKeys = attachedBlocks.map { blockInfo ->
        buildSelectionBlockKey(
            pageIndex = blockInfo.pageIndex,
            blockIndex = blockInfo.block.blockIndex,
            blockCharOffset = getTextBlockCharOffset(blockInfo.block)
        )
    }.toSet()
    val newTextPerBlock = selection.textPerBlock.toMutableMap()
    newTextPerBlock.keys.removeAll { keyStr ->
        val key = parseSelectionBlockKey(keyStr)
        keyStr in attachedKeys ||
            (key != null && !isSelectionBlockKeyInsideSelection(key, tentativeSelection))
    }

    for (blockInfo in relevantBlocks) {
        val txt = blockInfo.block.content.text
        val blockAbs = getTextBlockCharOffset(blockInfo.block)
        val isStartBlockPart =
            blockInfo.pageIndex == newStartPageIdx &&
                blockInfo.block.blockIndex == newStartIdx &&
                blockAbs == newStartBlockAbs
        val isEndBlockPart =
            blockInfo.pageIndex == newEndPageIdx &&
                blockInfo.block.blockIndex == newEndIdx &&
                blockAbs == newEndBlockAbs

        val start = if (isStartBlockPart) newStartOffset else 0
        val end = if (isEndBlockPart) newEndOffset else txt.length
        val safeStart = start.coerceIn(0, txt.length)
        val safeEnd = end.coerceIn(safeStart, txt.length)
        val key = buildSelectionBlockKey(
            pageIndex = blockInfo.pageIndex,
            blockIndex = blockInfo.block.blockIndex,
            blockCharOffset = blockAbs
        )

        if (safeStart < safeEnd) {
            newTextPerBlock[key] = txt.substring(safeStart, safeEnd)
        } else {
            newTextPerBlock.remove(key)
        }
    }

    val newText = newTextPerBlock.entries
        .sortedWith { first, second -> compareSelectionBlockKeys(first.key, second.key) }
        .joinToString(" ") { it.value }
        .ifEmpty { selection.text }

    val selectionWithText = tentativeSelection.copy(
        text = newText,
        textPerBlock = newTextPerBlock
    )

    val sLayout = findSelectionLayout(blockLayoutMap, newStartCfi, newStartPageIdx, newStartBlockAbs)
    val eLayout = findSelectionLayout(blockLayoutMap, newEndCfi, newEndPageIdx, newEndBlockAbs)
    val newRect = if (sLayout != null && eLayout != null && sLayout.second.isAttached && eLayout.second.isAttached) {
        val sMaxIdx = maxOf(0, sLayout.first.layoutInput.text.length - 1)
        val eMaxIdx = maxOf(0, eLayout.first.layoutInput.text.length - 1)
        try {
            val sRectLocal = sLayout.first.getBoundingBox(newStartOffset.coerceIn(0, sMaxIdx))
            val sRectWin = Rect(
                sLayout.second.localToWindow(sRectLocal.topLeft),
                sLayout.second.localToWindow(sRectLocal.bottomRight)
            )
            val eRectLocal = eLayout.first.getBoundingBox((newEndOffset - 1).coerceIn(0, eMaxIdx))
            val eRectWin = Rect(
                eLayout.second.localToWindow(eRectLocal.topLeft),
                eLayout.second.localToWindow(eRectLocal.bottomRight)
            )
            Rect(
                minOf(sRectWin.left, eRectWin.left),
                sRectWin.top,
                maxOf(sRectWin.right, eRectWin.right),
                eRectWin.bottom
            )
        } catch (_: Exception) {
            selectionWindowBounds(selectionWithText, relevantBlocks)
        }
    } else {
        selectionWindowBounds(selectionWithText, relevantBlocks)
    }

    return selectionWithText.copy(rect = newRect) to activeDragHandle
}

internal fun highlightsForPaginatedPage(
    pageChapterIndex: Int?,
    userHighlights: List<UserHighlight>
): List<UserHighlight> {
    if (pageChapterIndex == null) {
        if (userHighlights.isNotEmpty()) {
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "page_scope_skip reason=null_page_chapter inputHighlightCount=${userHighlights.size}"
            )
        }
        return emptyList()
    }
    val scoped = userHighlights.filter { it.chapterIndex == pageChapterIndex }
    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
        "page_scope pageChapter=$pageChapterIndex inputHighlightCount=${userHighlights.size} " +
            "scopedHighlightCount=${scoped.size} scopedIds=${scoped.map { it.id }}"
    )
    return scoped
}

class ReactiveBlockMap(
    private val delegate: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>> = mutableStateMapOf()
) : MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>> by delegate {
    var tick by mutableIntStateOf(0)

    override fun put(key: String, value: Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>): Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? {
        tick++
        return delegate.put(key, value)
    }

    override fun remove(key: String): Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? {
        tick++
        return delegate.remove(key)
    }

    override fun clear() {
        tick++
        delegate.clear()
    }

    fun pruneDetached() {
        val detachedKeys = delegate
            .filterValues { (_, coords, _) -> !coords.isAttached }
            .keys
            .toList()
        if (detachedKeys.isEmpty()) return
        detachedKeys.forEach { delegate.remove(it) }
        tick++
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal fun estimateNativeVerticalCompatPage(
    book: EpubBook,
    paginator: BookPaginator,
    locator: Locator?,
    fallbackPage: Int
): Int {
    if (locator == null) return fallbackPage
    return nativeVerticalCompatPageForLocator(
        chapterCharOffset = locator.charOffset,
        chapterStartPageIndex = paginator.chapterStartPageIndices[locator.chapterIndex],
        chapterPageCount = paginator.chapterPageCounts[locator.chapterIndex],
        chapterLengthChars = book.chaptersForPagination
            .getOrNull(locator.chapterIndex)
            ?.plainTextCharacterCount(),
        fallbackPageIndex = fallbackPage,
    )
}

internal fun estimateNativeVerticalProgressPercent(
    book: EpubBook,
    locator: Locator?
): Float? {
    if (locator == null) return null
    return nativeVerticalProgressPercentForLocator(
        chapterCharacterCounts = book.chaptersForPagination.map { it.plainTextCharacterCount() },
        chapterIndex = locator.chapterIndex,
        chapterCharOffset = locator.charOffset,
    )
}

internal fun locatorForNativeVerticalFlowBlock(chapterIndex: Int, block: ContentBlock): Locator {
    return nativeVerticalNavigationTargetForBlock(chapterIndex, block)
}

internal fun findNativeVerticalFlowTextBlockForLocator(
    chapters: List<NativeVerticalFlowChapter>,
    locator: Locator
): TextContentBlock? {
    return findNativeVerticalFlowTextBlockForTarget(chapters, locator)
}

internal fun nativeVerticalFlowBlockMatchesLocator(block: ContentBlock, locator: Locator): Boolean {
    return nativeVerticalFlowBlockMatchesTarget(block, locator)
}

internal fun findNativeVerticalFlowItemIndexForProgress(
    items: List<NativeVerticalFlowItem>,
    progressPercent: Float
): Int? {
    return nativeVerticalProgressToItemIndex(
        itemWeights = items.map { it.locationWeight },
        progressPercent = progressPercent
    )
}

internal fun estimateNativeVerticalScrollProgressPercent(
    items: List<NativeVerticalFlowItem>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int
): Float? {
    return estimateNativeVerticalWeightedScrollProgressPercent(
        itemWeights = items.map { it.locationWeight },
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        firstVisibleItemSize = firstVisibleItemSize
    )
}

internal fun findNativeVerticalFlowItemIndexForLocator(
    items: List<NativeVerticalFlowItem>,
    chapters: List<NativeVerticalFlowChapter>,
    locator: Locator
): Int? {
    return findNativeVerticalFlowItemIndexForTarget(items, chapters, locator)
}

internal fun locatorForNativeVerticalFlowItem(item: NativeVerticalFlowItem): Locator? {
    return nativeVerticalNavigationTargetForItem(item)
}

internal fun resolveNativeVerticalScrollDeltaForLocator(
    rootWindowBounds: Rect,
    chapterLayoutMap: Map<Int, LayoutCoordinates>,
    flowItems: List<NativeVerticalFlowItem>,
    flowItemLayoutMap: Map<String, LayoutCoordinates>,
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    chapters: List<NativeVerticalFlowChapter>,
    locator: Locator,
    allowChapterFallback: Boolean = true
): Float? {
    if (rootWindowBounds == Rect.Zero) return null

    val targetTextBlock = findNativeVerticalFlowTextBlockForLocator(chapters, locator)
    if (targetTextBlock?.cfi != null && targetTextBlock.blockIndex == locator.blockIndex) {
        val layoutInfo = findSelectionLayout(
            blockLayoutMap = blockLayoutMap,
            cfi = targetTextBlock.cfi!!,
            pageIndex = locator.chapterIndex,
            blockCharOffset = getTextBlockCharOffset(targetTextBlock)
        )
        if (layoutInfo != null) {
            val (layout, coords, block) = layoutInfo
            if (coords.isAttached && layout.lineCount > 0) {
                val relativeOffset = (locator.charOffset - getTextBlockCharOffset(block))
                    .coerceIn(0, block.content.text.length)
                val lineIndex = runCatching { layout.getLineForOffset(relativeOffset) }
                    .getOrDefault(0)
                    .coerceIn(0, layout.lineCount - 1)
                val localY = runCatching { layout.getLineTop(lineIndex) }
                    .getOrDefault(0f)
                val targetWindowY = coords.localToWindow(Offset(0f, localY)).y
                return targetWindowY - rootWindowBounds.top
            }
        }
    }

    flowItems.firstOrNull { item ->
        item.chapterIndex == locator.chapterIndex &&
            item.block?.let { nativeVerticalFlowBlockMatchesLocator(it, locator) } == true
    }?.let { item ->
        val coords = flowItemLayoutMap[item.key]
        if (coords?.isAttached == true) {
            return coords.positionInWindow().y - rootWindowBounds.top
        }
    }

    if (!allowChapterFallback) return null

    val chapterCoords = chapterLayoutMap[locator.chapterIndex]
    if (chapterCoords?.isAttached == true) {
        return chapterCoords.positionInWindow().y - rootWindowBounds.top
    }

    return null
}

internal fun resolveNativeVerticalFlowVisibleLocator(
    rootWindowBounds: Rect,
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>
): Locator? {
    if (rootWindowBounds == Rect.Zero) return null
    val viewportTop = rootWindowBounds.top + 8f
    val viewportBottom = rootWindowBounds.bottom - 8f

    val visible = blockLayoutMap.entries
        .asSequence()
        .mapNotNull { (key, layoutInfo) ->
            val chapterIndex = key.substringAfterLast("_").toIntOrNull()
                ?: return@mapNotNull null
            val (layout, coords, block) = layoutInfo
            if (!coords.isAttached) return@mapNotNull null
            val bounds = Rect(coords.positionInWindow(), coords.size.toSize())
            if (bounds.bottom <= viewportTop || bounds.top >= viewportBottom) {
                null
            } else {
                Triple(chapterIndex, bounds, layoutInfo)
            }
        }
        .sortedBy { it.second.top }
        .firstOrNull { it.second.bottom > viewportTop }
        ?: return null

    val chapterIndex = visible.first
    val bounds = visible.second
    val (layout, _, block) = visible.third
    val blockStartOffset = getTextBlockCharOffset(block)
    if (layout.lineCount <= 0) {
        return Locator(chapterIndex, block.blockIndex, blockStartOffset)
    }

    val maxLayoutY = (layout.size.height - 1).coerceAtLeast(0).toFloat()
    val localY = (viewportTop - bounds.top).coerceIn(0f, maxLayoutY)
    val lineIndex = runCatching { layout.getLineForVerticalPosition(localY) }
        .getOrDefault(0)
        .coerceIn(0, layout.lineCount - 1)
    val relativeOffset = runCatching { layout.getLineStart(lineIndex) }
        .getOrDefault(0)
        .coerceIn(0, block.content.text.length)

    return Locator(
        chapterIndex = chapterIndex,
        blockIndex = block.blockIndex,
        charOffset = blockStartOffset + relativeOffset
    )
}

internal fun resolveNativeVerticalVisibleTextRanges(
    rootWindowBounds: Rect,
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>
): List<NativeVerticalVisibleTextRange> {
    if (rootWindowBounds == Rect.Zero) return emptyList()
    val viewportTop = rootWindowBounds.top + 8f
    val viewportBottom = rootWindowBounds.bottom - 8f

    return blockLayoutMap.entries
        .asSequence()
        .mapNotNull { (key, layoutInfo) ->
            val chapterIndex = key.substringAfterLast("_").toIntOrNull()
                ?: return@mapNotNull null
            val (layout, coords, block) = layoutInfo
            if (!coords.isAttached) return@mapNotNull null
            val bounds = Rect(coords.positionInWindow(), coords.size.toSize())
            if (bounds.bottom <= viewportTop || bounds.top >= viewportBottom) {
                null
            } else {
                val blockStart = getTextBlockCharOffset(block)
                val visibleTopInText = (viewportTop - bounds.top).coerceAtLeast(0f)
                val visibleBottomInText = (viewportBottom - bounds.top).coerceAtMost(bounds.height)
                var firstVisibleOffset: Int? = null
                var lastVisibleOffset: Int? = null

                for (lineIndex in 0 until layout.lineCount) {
                    val lineTop = runCatching { layout.getLineTop(lineIndex) }.getOrDefault(0f)
                    val lineBottom = runCatching { layout.getLineBottom(lineIndex) }.getOrDefault(lineTop)
                    if (lineBottom < visibleTopInText || lineTop > visibleBottomInText) continue

                    val lineStart = runCatching { layout.getLineStart(lineIndex) }.getOrDefault(0)
                        .coerceIn(0, block.content.length)
                    val lineEnd = runCatching { layout.getLineEnd(lineIndex, visibleEnd = true) }.getOrDefault(lineStart)
                        .coerceIn(lineStart, block.content.length)
                    firstVisibleOffset = minOf(firstVisibleOffset ?: lineStart, lineStart)
                    lastVisibleOffset = maxOf(lastVisibleOffset ?: lineEnd, lineEnd)
                }

                val start = blockStart + (firstVisibleOffset ?: 0)
                val end = blockStart + (lastVisibleOffset ?: block.content.text.length)
                bounds.top to NativeVerticalVisibleTextRange(
                    chapterIndex = chapterIndex,
                    blockIndex = block.blockIndex,
                    startCharOffset = start,
                    endCharOffset = end
                )
            }
        }
        .sortedBy { it.first }
        .map { it.second }
        .toList()
}

internal fun resolveReaderFootnoteHtml(
    book: EpubBook,
    currentChapterPath: String,
    href: String
): String? {
    val decodedHref = try {
        URLDecoder.decode(href, "UTF-8")
    } catch (_: Exception) {
        href
    }
    val parts = decodedHref.split('#', limit = 2)
    val pathPart = parts[0]
    val anchor = if (parts.size > 1) parts[1] else null

    if (anchor != null) {
        val targetPath = if (pathPart.isBlank()) currentChapterPath else {
            try {
                URI(currentChapterPath).resolve(pathPart).normalize().path
            } catch (_: Exception) {
                null
            }
        }

        if (targetPath != null) {
            val targetChapter = book.chaptersForPagination.find {
                try {
                    URI(it.absPath).normalize().path == targetPath
                } catch (_: Exception) {
                    false
                }
            }

            if (targetChapter != null) {
                val sourceChapter = book.chaptersForPagination.find {
                    runCatching { URI(it.absPath).normalize().path == URI(currentChapterPath).normalize().path }
                        .getOrDefault(false)
                }
                val sourceHtml = sourceChapter?.htmlContent?.ifEmpty {
                    try {
                        File(book.extractionBasePath, sourceChapter.htmlFilePath).readText()
                    } catch (_: Exception) {
                        ""
                    }
                }.orEmpty()
                val targetHtml = targetChapter.htmlContent.ifEmpty {
                    try {
                        File(book.extractionBasePath, targetChapter.htmlFilePath).readText()
                    } catch (_: Exception) {
                        ""
                    }
                }
                if (targetHtml.isNotEmpty()) {
                    return resolveEpubNoteHtml(
                        sourceHtml = sourceHtml,
                        targetHtml = targetHtml,
                        href = href,
                        anchor = anchor,
                        targetBaseUri = targetChapter.absPath
                    )
                }
            }
        }
    }

    return null
}

internal fun resolveEpubNoteHtml(
    sourceHtml: String,
    targetHtml: String,
    href: String,
    anchor: String,
    sourceIsNoteref: Boolean = false,
    targetBaseUri: String? = null
): String? {
    val sourceNoteref = sourceIsNoteref || Jsoup.parse(sourceHtml)
        .select("a[href]")
        .firstOrNull { link -> link.attr("href") == href }
        ?.let { link ->
            link.attr("epub:type").hasSemanticToken("noteref") ||
                link.attr("role").hasSemanticToken("doc-noteref")
        } == true
    val target = Jsoup.parse(targetHtml).getElementById(anchor) ?: return null
    val semanticContainer = generateSequence(target) { element -> element.parent() }
        .firstOrNull { element -> element.isSemanticNoteContainer() }
    if (!sourceNoteref && semanticContainer == null) return null

    val contentContainer = semanticContainer ?: generateSequence(target.parent()) { element -> element.parent() }
        .firstOrNull { element -> element.tagName() in EPUB_NOTE_FALLBACK_CONTAINER_TAGS }
        ?: target
    if (!targetBaseUri.isNullOrBlank()) {
        contentContainer.select("a[href]").forEach { link ->
            val rawHref = link.attr("href")
            val resolvedHref = runCatching { URI(targetBaseUri).resolve(rawHref).toString() }.getOrNull()
            if (!resolvedHref.isNullOrBlank()) link.attr("href", resolvedHref)
        }
    }
    return contentContainer.html().takeIf { it.isNotBlank() }
}

private val EPUB_NOTE_FALLBACK_CONTAINER_TAGS = setOf("aside", "li", "p", "div", "section")

private fun org.jsoup.nodes.Element.isSemanticNoteContainer(): Boolean {
    return attr("epub:type").hasSemanticToken("footnote", "endnote") ||
        attr("role").hasSemanticToken("doc-footnote", "doc-endnote") ||
        classNames().any { it.equals("footnote", true) || it.equals("endnote", true) }
}

private fun String.hasSemanticToken(vararg expected: String): Boolean {
    val tokens = split(Regex("\\s+")).filter(String::isNotBlank)
    return tokens.any { token -> expected.any { it.equals(token, ignoreCase = true) } }
}

data class PendingCrossPageSelection(val fromPageIndex: Int)

enum class SelectionHandle { START, END }

internal class SmartPopupPositionProvider(
    private val contentRect: Rect, private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val padding = with(density) { 8.dp.roundToPx() }
        val popupWidth = popupContentSize.width
        val popupHeight = popupContentSize.height

        var x = (contentRect.center.x - popupWidth / 2).toInt()
        x = x.coerceIn(0, windowSize.width - popupWidth)

        val topY = (contentRect.top - popupHeight - padding).toInt()
        val bottomY = (contentRect.bottom + padding).toInt()

        var y = topY
        if (y < 0) {
            y = if (bottomY + popupHeight <= windowSize.height) {
                bottomY
            } else {
                val spaceTop = contentRect.top
                val spaceBottom = windowSize.height - contentRect.bottom
                if (spaceBottom > spaceTop) {
                    bottomY.coerceAtMost(windowSize.height - popupHeight)
                } else {
                    topY.coerceAtLeast(0)
                }
            }
        }

        return IntOffset(x, y)
    }
}

internal object CfiUtils {
    fun compare(cfi1: String, cfi2: String): Int {
        val path1 = cfi1.split(':').first()
        val path2 = cfi2.split(':').first()

        val parts1 = path1.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val parts2 = path2.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

        val length = minOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val cmp = parts1[i].compareTo(parts2[i])
            if (cmp != 0) return cmp
        }

        if (parts1.size != parts2.size) {
            return parts1.size.compareTo(parts2.size)
        }

        val offset1 = cfi1.substringAfter(':', "0").toIntOrNull() ?: 0
        val offset2 = cfi2.substringAfter(':', "0").toIntOrNull() ?: 0
        return offset1.compareTo(offset2)
    }

    fun getPath(cfi: String): String = cfi.split(':').first()
    fun getOffset(cfi: String): Int = cfi.substringAfter(':', "0").toIntOrNull() ?: 0
    fun getOffsetOrNull(cfi: String): Int? = cfi.substringAfter(':', "").toIntOrNull()

    fun isPathStrictlyBetween(candidate: String, start: String, end: String): Boolean {
        val candidateParts = pathParts(candidate) ?: return false
        val startParts = pathParts(start) ?: return false
        val endParts = pathParts(end) ?: return false
        return comparePathParts(candidateParts, startParts) > 0 &&
            comparePathParts(candidateParts, endParts) < 0
    }

    private fun pathParts(cfi: String): List<Int>? {
        val segments = getPath(cfi).split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        return segments.map { it.toIntOrNull() ?: return null }
    }

    private fun comparePathParts(first: List<Int>, second: List<Int>): Int {
        val length = minOf(first.size, second.size)
        for (index in 0 until length) {
            val cmp = first[index].compareTo(second[index])
            if (cmp != 0) return cmp
        }
        return first.size.compareTo(second.size)
    }
}

internal fun highlightQueryInText(
    text: AnnotatedString, query: String, highlightColor: Color
): AnnotatedString {
    if (query.length < 3) return text

    return buildAnnotatedString {
        append(text)
        val textString = text.text
        var startIndex = 0
        while (startIndex < textString.length) {
            val index = textString.indexOf(query, startIndex, ignoreCase = true)
            if (index == -1) break
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = index,
                end = index + query.length
            )
            startIndex = index + query.length
        }
    }
}

internal fun AnnotatedString.readerUrlAnnotationAtOffset(offset: Int): String? {
    if (length == 0) return null

    val safeOffset = offset.coerceIn(0, length)
    getStringAnnotations("URL", safeOffset, safeOffset).firstOrNull()?.let { return it.item }

    if (safeOffset < length) {
        getStringAnnotations("URL", safeOffset, safeOffset + 1).firstOrNull()?.let { return it.item }
    }

    if (safeOffset > 0) {
        getStringAnnotations("URL", safeOffset - 1, safeOffset).firstOrNull()?.let { return it.item }
    }

    return null
}

internal fun String.isReaderExternalHref(): Boolean {
    val href = trim()
    if (href.startsWith("//")) return true

    val schemeEnd = href.indexOf(':')
    if (schemeEnd <= 0) return false

    val scheme = href.substring(0, schemeEnd)
    if (!scheme.first().isLetter()) return false
    if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return false

    return scheme.lowercase() in setOf("http", "https", "mailto", "tel", "sms", "geo")
}

internal fun String.readerExternalHrefForDisplay(): String {
    val href = trim()
    return if (href.startsWith("//")) "https:$href" else href
}

internal const val READER_LINK_HIT_SLOP_PX = 2f

internal fun AnnotatedString.readerUrlAnnotationAtPosition(
    layout: TextLayoutResult,
    position: Offset,
    textStartOffset: Int = 0
): String? {
    if (length == 0 || layout.lineCount == 0) return null

    val localTextLength = layout.layoutInput.text.length
    if (localTextLength == 0) return null

    val lineIndex = layout.getLineForVerticalPosition(position.y)
    if (lineIndex !in 0 until layout.lineCount) return null

    val lineTop = layout.getLineTop(lineIndex)
    val lineBottom = layout.getLineBottom(lineIndex)
    if (
        position.y < lineTop - READER_LINK_HIT_SLOP_PX ||
        position.y > lineBottom + READER_LINK_HIT_SLOP_PX
    ) {
        return null
    }

    val localLineStart = layout.getLineStart(lineIndex)
    val localLineEnd = layout.getLineEnd(lineIndex, visibleEnd = true)
    if (localLineStart >= localLineEnd) return null

    val globalLineStart = (textStartOffset + localLineStart).coerceIn(0, length)
    val globalLineEnd = (textStartOffset + localLineEnd).coerceIn(globalLineStart, length)
    if (globalLineStart >= globalLineEnd) return null

    return getStringAnnotations("URL", globalLineStart, globalLineEnd)
        .firstOrNull { annotation ->
            if (annotation.item.isBlank()) return@firstOrNull false

            val localStart = (annotation.start - textStartOffset).coerceIn(0, localTextLength)
            val localEnd = (annotation.end - textStartOffset).coerceIn(0, localTextLength)
            val segmentStart = maxOf(localStart, localLineStart)
            val segmentEnd = minOf(localEnd, localLineEnd)
            layout.readerTextRangeContainsPosition(segmentStart, segmentEnd, position)
        }
        ?.item
}

internal fun TextLayoutResult.readerTextRangeContainsPosition(
    start: Int,
    endExclusive: Int,
    position: Offset
): Boolean {
    val textLength = layoutInput.text.length
    val safeStart = start.coerceIn(0, textLength)
    val safeEnd = endExclusive.coerceIn(safeStart, textLength)
    if (safeStart >= safeEnd) return false

    val lineIndex = getLineForVerticalPosition(position.y)
    val startLine = getLineForOffset(safeStart)
    val endLine = getLineForOffset((safeEnd - 1).coerceAtLeast(safeStart))
    if (lineIndex !in startLine..endLine) return false

    val lineStart = getLineStart(lineIndex)
    val lineEnd = getLineEnd(lineIndex, visibleEnd = true)
    val segmentStart = maxOf(safeStart, lineStart)
    val segmentEnd = minOf(safeEnd, lineEnd)
    if (segmentStart >= segmentEnd) return false

    var left = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    for (offset in segmentStart until segmentEnd) {
        val box = getBoundingBox(offset)
        left = minOf(left, box.left, box.right)
        right = maxOf(right, box.left, box.right)
    }
    if (left == Float.POSITIVE_INFINITY || right == Float.NEGATIVE_INFINITY) return false

    return position.x >= left - READER_LINK_HIT_SLOP_PX &&
        position.x <= right + READER_LINK_HIT_SLOP_PX
}

internal data class ReaderPageLinkHit(
    val href: String,
    val blockIndex: Int,
    val cfi: String?
)

internal fun ReactiveBlockMap.readerLinkAtPagePosition(
    pageCoordinates: LayoutCoordinates,
    pageIndex: Int,
    position: Offset
): ReaderPageLinkHit? {
    val windowPosition = pageCoordinates.localToWindow(position)
    return entries.firstNotNullOfOrNull { (key, value) ->
        if (!key.endsWith("_$pageIndex")) return@firstNotNullOfOrNull null

        val (layout, coordinates, block) = value
        if (!coordinates.isAttached) return@firstNotNullOfOrNull null

        val localPosition = coordinates.windowToLocal(windowPosition)
        if (
            localPosition.x < 0f ||
            localPosition.y < 0f ||
            localPosition.x > layout.size.width.toFloat() ||
            localPosition.y > layout.size.height.toFloat()
        ) {
            return@firstNotNullOfOrNull null
        }

        layout.layoutInput.text
            .readerUrlAnnotationAtPosition(layout, localPosition)
            ?.let { href ->
                ReaderPageLinkHit(
                    href = href,
                    blockIndex = block.blockIndex,
                    cfi = block.cfi
                )
            }
    }
}

internal suspend fun AwaitPointerEventScope.awaitReaderLinkTap(
    source: String,
    urlAtPosition: (Offset) -> String?,
    touchSlop: Float,
    onLinkClick: (String) -> Unit
) {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (down.isConsumed) {
        Timber.tag(TAG_PAGINATED_LINK_DIAG).v(
            "tap_down_skip_consumed source=$source x=${down.position.x.roundToInt()} y=${down.position.y.roundToInt()}"
        )
        return
    }
    val url = urlAtPosition(down.position)
    if (url == null) {
        Timber.tag(TAG_PAGINATED_LINK_DIAG).v(
            "tap_down_miss source=$source x=${down.position.x.roundToInt()} y=${down.position.y.roundToInt()}"
        )
        return
    }
    Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
        "tap_down_hit source=$source x=${down.position.x.roundToInt()} y=${down.position.y.roundToInt()} " +
            "href=${url.readerLinkDiagPreview()}"
    )
    down.consume()

    var movedOutsideTapSlop = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
        val dx = change.position.x - down.position.x
        val dy = change.position.y - down.position.y
        if (sqrt(dx * dx + dy * dy) > touchSlop) {
            movedOutsideTapSlop = true
        }

        if (!change.pressed) {
            if (!movedOutsideTapSlop) {
                change.consume()
                Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                    "tap_up_open source=$source href=${url.readerLinkDiagPreview()}"
                )
                onLinkClick(url)
            } else {
                Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                    "tap_cancel_slop source=$source href=${url.readerLinkDiagPreview()} " +
                        "dx=${dx.roundToInt()} dy=${dy.roundToInt()} slop=${touchSlop.roundToInt()}"
                )
            }
            break
        }

        if (!movedOutsideTapSlop) {
            change.consume()
        }
    }
}

internal fun AnnotatedString.withReaderLinkDisplayStyle(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): AnnotatedString {
    val urls = getStringAnnotations("URL", 0, length)
    if (urls.isEmpty()) return this

    val linkStyle = readerLinkSpanStyle(
        isDarkTheme = isDarkTheme,
        themeBackgroundColor = themeBackgroundColor,
        themeTextColor = themeTextColor
    )

    return buildAnnotatedString {
        append(this@withReaderLinkDisplayStyle)
        urls.forEach { range ->
            addStyle(linkStyle, range.start, range.end)
        }
    }
}

internal fun AnnotatedString.withParagraphTextAlignStart(): AnnotatedString {
    return withStackedPaginationTextStartAlignment()
}
@Composable
internal fun LinkAwareText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit,
    wrapDiagnosticsContext: String? = null
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lastWrapDiagnosticsSignature by remember { mutableStateOf<String?>(null) }
    val viewConfiguration = LocalViewConfiguration.current
    val latestLayoutResult = rememberUpdatedState(layoutResult)
    val latestOnLinkClick = rememberUpdatedState(onLinkClick)
    val latestOnGeneralTap = rememberUpdatedState(onGeneralTap)
    val displayText = remember(text, isDarkTheme, themeBackgroundColor, themeTextColor, style.color) {
        text.withReaderLinkDisplayStyle(
            isDarkTheme = isDarkTheme,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = style.color.takeIf { it.isSpecified } ?: themeTextColor
        )
    }
    LaunchedEffect(displayText) {
        if (READER_LINK_DIAGNOSTICS_ENABLED && displayText.getStringAnnotations("URL", 0, displayText.length).isNotEmpty()) {
            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                "compose_text source=LinkAwareText " + displayText.readerAnnotatedLinkDiagSummary()
            )
        }
    }

    // Foreground colors are paint-only. Keep one shaping input so contextual
    // OpenType features can cross author color spans. A second input restores
    // colors through native character-level paint spans, which can color an
    // attached mark without recoloring its base.
    val shapingDisplayText = remember(displayText) {
        displayText.withoutForegroundColorSpans()
    }
    val paintOnlyColorOverlayText = remember(displayText, style.color) {
        displayText.paintOnlyColorOverlayText(
            baseColor = style.color.takeIf { it.isSpecified } ?: Color.Unspecified
        )
    }

    Box(modifier = modifier) {
        Text(
            text = shapingDisplayText,
            style = style,
            modifier = Modifier.fillMaxWidth()
            .pointerInput(displayText, viewConfiguration.touchSlop) {
                awaitEachGesture {
                    awaitReaderLinkTap(
                        source = "LinkAwareText",
                        urlAtPosition = { offset ->
                            latestLayoutResult.value?.let { layout ->
                                displayText.readerUrlAnnotationAtPosition(layout, offset)
                            }
                        },
                        touchSlop = viewConfiguration.touchSlop,
                        onLinkClick = { latestOnLinkClick.value(it) }
                    )
                }
            }
            .pointerInput(displayText) {
                detectTapGestures(
                    onTap = { offset ->
                        val url = latestLayoutResult.value?.let { layout ->
                            displayText.readerUrlAnnotationAtPosition(layout, offset)
                        }
                        if (url != null) {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "detect_tap_link source=LinkAwareText href=${url.readerLinkDiagPreview()}"
                            )
                            latestOnLinkClick.value(url)
                        } else {
                            latestOnGeneralTap.value(offset)
                        }
                    }
                )
        },
        onTextLayout = {
            layoutResult = it
            wrapDiagnosticsContext?.let { context ->
                var maxLineWidthPx = 0
                val samples = mutableListOf<String>()
                val sampleLimit = minOf(it.lineCount, 6)
                for (line in 0 until it.lineCount) {
                    val lineStart = it.getLineStart(line)
                    val lineEnd = it.getLineEnd(line, visibleEnd = true).coerceIn(lineStart, displayText.length)
                    val lineWidth = abs(it.getLineRight(line) - it.getLineLeft(line)).roundToInt()
                    maxLineWidthPx = maxOf(maxLineWidthPx, lineWidth)
                    if (line < sampleLimit) {
                        samples += "${line}:${lineStart}..$lineEnd:${lineWidth}px:'${displayText.text.substring(lineStart, lineEnd).replace('\n', ' ').take(24)}'"
                    }
                }
                val lineOverflowPx = maxLineWidthPx - it.size.width
                val signature = "$context:${it.size.width}x${it.size.height}:${it.lineCount}:$maxLineWidthPx"
                if (signature != lastWrapDiagnosticsSignature && (it.lineCount > 1 || lineOverflowPx > AndroidEpubCutoffTolerancePx)) {
                    lastWrapDiagnosticsSignature = signature
                    logReaderUiAndroidEpubCutoff(
                        "cutoff_probe layer=android_table_text_wrap $context " +
                            "layoutPx=${it.size.width}x${it.size.height} lineCount=${it.lineCount} " +
                            "maxLineWidthPx=$maxLineWidthPx lineOverflowPx=$lineOverflowPx " +
                            "textAlign=${it.layoutInput.style.textAlign} fontSize=${it.layoutInput.style.fontSize} " +
                            "lineHeight=${it.layoutInput.style.lineHeight} textChars=${displayText.length} " +
                            "lines=${samples.joinToString("|")}"
                    )
                }
            }
            if (READER_LINK_DIAGNOSTICS_ENABLED && displayText.getStringAnnotations("URL", 0, displayText.length).isNotEmpty()) {
                Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                    "layout_text source=LinkAwareText size=${it.size.width}x${it.size.height} " +
                        "lines=${it.lineCount} " + displayText.readerAnnotatedLinkDiagSummary()
                )
            }
        }
        )
        if (paintOnlyColorOverlayText.isNotEmpty()) {
            Text(
                text = paintOnlyColorOverlayText,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {},
                style = style.copy(color = Color.Transparent)
            )
        }
    }
}

internal fun computeImageRenderSizePx(
    block: ImageBlock,
    density: Density,
    maxWidthPx: Float,
    imageSizeMultiplier: Float
): Pair<Float, Float> {
    val intrinsicWidth = block.intrinsicWidth
    val intrinsicHeight = block.intrinsicHeight
    if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f || intrinsicHeight <= 0f) {
        return 0f to 0f
    }

    val aspectRatio = intrinsicHeight / intrinsicWidth
    val baseWidth = with(density) {
        if (block.style.width.isSpecified && block.style.width > 0.dp) {
            block.style.width.toPx()
        } else {
            intrinsicImageWidthPx(intrinsicWidth, density, maxWidthPx)
        }
    }

    var scaledWidth = baseWidth * imageSizeMultiplier
    if (block.style.maxWidth.isSpecified && block.style.maxWidth > 0.dp) {
        scaledWidth = scaledWidth.coerceAtMost(with(density) { block.style.maxWidth.toPx() } * imageSizeMultiplier)
    }
    scaledWidth = scaledWidth.coerceAtMost(maxWidthPx)

    return scaledWidth to (scaledWidth * aspectRatio)
}

internal fun computeImageRenderSizeDp(
    block: ImageBlock,
    density: Density,
    maxWidthDp: Dp,
    imageSizeMultiplier: Float
): Pair<Dp, Dp>? {
    val (widthPx, heightPx) = computeImageRenderSizePx(
        block = block,
        density = density,
        maxWidthPx = with(density) { maxWidthDp.toPx() },
        imageSizeMultiplier = imageSizeMultiplier
    )
    if (widthPx <= 0f || heightPx <= 0f) return null
    return with(density) { widthPx.toDp() to heightPx.toDp() }
}

internal fun imageBlockContentAlignment(style: BlockStyle): Alignment {
    return when {
        style.float == "right" || style.horizontalAlign == "right" || style.horizontalAlign == "end" -> Alignment.CenterEnd
        style.float == "left" || style.horizontalAlign == "left" || style.horizontalAlign == "start" -> Alignment.CenterStart
        else -> Alignment.Center
    }
}

internal fun imageContentScale(style: BlockStyle): ContentScale {
    return when (style.objectFit) {
        "cover" -> ContentScale.Crop
        "fill" -> ContentScale.FillBounds
        "contain", "scale-down" -> ContentScale.Fit
        else -> ContentScale.Fit
    }
}

internal fun nativeVerticalSvgContentFromDataUri(source: String): String? {
    return decodeNativeVerticalSvgDataUri(source)
}

internal fun nativeVerticalImageModelData(source: String): Any {
    val trimmed = source.trim()
    return when {
        trimmed.startsWith("<svg", ignoreCase = true) -> SvgData(trimmed)
        trimmed.startsWith("data:image/svg+xml", ignoreCase = true) ->
            nativeVerticalSvgContentFromDataUri(trimmed)?.let { SvgData(it) } ?: trimmed
        trimmed.startsWith("file:", ignoreCase = true) ||
            trimmed.startsWith("content:", ignoreCase = true) ||
            trimmed.startsWith("android.resource:", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.toUri()
        trimmed.startsWith("data:", ignoreCase = true) -> trimmed
        else -> File(trimmed)
    }
}

internal fun tableCellImageModifier(
    block: ImageBlock,
    density: Density,
    imageSizeMultiplier: Float
): Modifier {
    val baseModifier = if (block.style.width.isSpecified && block.style.width > 0.dp) {
        Modifier.width(block.style.width * imageSizeMultiplier)
    } else {
        Modifier.fillMaxWidth(imageSizeMultiplier.coerceIn(0f, 1f))
    }

    val intrinsicWidth = block.intrinsicWidth
    val intrinsicHeight = block.intrinsicHeight
    val sizedModifier = if (
        intrinsicWidth != null &&
        intrinsicHeight != null &&
        intrinsicWidth > 0f &&
        intrinsicHeight > 0f
    ) {
        baseModifier.aspectRatio(intrinsicWidth / intrinsicHeight)
    } else {
        baseModifier.height(
            if (block.expectedHeight > 0) {
                with(density) { (block.expectedHeight * imageSizeMultiplier).toDp() }
            } else {
                250.dp
            }
        )
    }

    return if (block.style.maxWidth.isSpecified && block.style.maxWidth > 0.dp) {
        sizedModifier.widthIn(max = block.style.maxWidth * imageSizeMultiplier)
    } else {
        sizedModifier
    }
}

private data class WrappingTextLayout(
    val layout: TextLayoutResult,
    val overlayLayout: TextLayoutResult?,
    val offset: Offset,
    val textStartOffset: Int
)

@Composable
internal fun WrappingContentLayout(
    block: WrappingContentBlock,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false,
    modifier: Modifier = Modifier,
    searchQuery: String,
    ttsHighlightInfo: TtsHighlightInfo?,
    searchHighlightColor: Color,
    ttsHighlightColor: Color,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val fullText = remember(block.paragraphsToWrap, searchQuery, ttsHighlightInfo) {
        buildAnnotatedString {
            block.paragraphsToWrap.forEachIndexed { index, p ->
                val searchHighlighted =
                    highlightQueryInText(p.content, searchQuery, searchHighlightColor)
                val finalContent = if (ttsHighlightInfo != null && p.cfi == ttsHighlightInfo.cfi) {
                    buildAnnotatedString {
                        append(searchHighlighted)
                        val blockStartAbs = p.startCharOffsetInSource
                        val blockEndAbs = p.startCharOffsetInSource + searchHighlighted.length
                        val highlightStartAbs = ttsHighlightInfo.offset
                        val highlightEndAbs = ttsHighlightInfo.offset + ttsHighlightInfo.text.length
                        val intersectionStartAbs = maxOf(blockStartAbs, highlightStartAbs)
                        val intersectionEndAbs = minOf(blockEndAbs, highlightEndAbs)

                        if (intersectionStartAbs < intersectionEndAbs) {
                            val highlightStartRelative = intersectionStartAbs - blockStartAbs
                            val highlightEndRelative = intersectionEndAbs - blockStartAbs
                            addStyle(
                                style = SpanStyle(
                                    background = ttsHighlightColor
                                ), start = highlightStartRelative, end = highlightEndRelative
                            )
                        }
                    }
                } else {
                    searchHighlighted
                }
                append(finalContent)
                if (index < block.paragraphsToWrap.lastIndex) append("\n\n")
            }
        }
    }
    val displayFullText = remember(fullText, isDarkTheme, themeBackgroundColor, themeTextColor, textStyle.color) {
        fullText.withReaderLinkDisplayStyle(
            isDarkTheme = isDarkTheme,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = textStyle.color.takeIf { it.isSpecified } ?: themeTextColor
        )
    }
    val shapingDisplayFullText = remember(displayFullText) {
        displayFullText.withoutForegroundColorSpans()
    }
    val paintOnlyColorOverlayText = remember(displayFullText, textStyle.color) {
        displayFullText.paintOnlyColorOverlayText(
            baseColor = textStyle.color.takeIf { it.isSpecified } ?: Color.Unspecified
        )
    }
    val (paragraphStartOffsets, paragraphEndOffsetMap) = remember(block.paragraphsToWrap) {
        val starts = mutableSetOf<Int>()
        val endMap = mutableMapOf<Int, Int>()
        var currentOffset = 0
        block.paragraphsToWrap.forEachIndexed { index, p ->
            starts.add(currentOffset)
            currentOffset += p.content.length
            endMap[currentOffset - 1] = index
            if (index < block.paragraphsToWrap.lastIndex) {
                currentOffset += 2
            }
        }
        starts to endMap
    }
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    var textLayouts by remember {
        mutableStateOf<List<WrappingTextLayout>>(emptyList())
    }
    var totalHeight by remember { mutableIntStateOf(0) }
    val latestTextLayouts = rememberUpdatedState(textLayouts)
    val latestOnLinkClick = rememberUpdatedState(onLinkClick)
    val latestOnGeneralTap = rememberUpdatedState(onGeneralTap)

    Layout(content = {
        AsyncImage(
            model = Builder(LocalContext.current)
                .data(nativeVerticalImageModelData(block.floatedImage.path))
                .build(),
            contentDescription = block.floatedImage.altText,
            contentScale = imageContentScale(block.floatedImage.style)
        )
    }, modifier = modifier
        .drawWithContent {
            drawContent()
            textLayouts.forEach { line ->
                drawText(line.layout, topLeft = line.offset)
                line.overlayLayout?.let { overlayLayout ->
                    drawText(overlayLayout, topLeft = line.offset)
                }
            }
        }
        .pointerInput(displayFullText, viewConfiguration.touchSlop) {
            awaitEachGesture {
                awaitReaderLinkTap(
                    source = "WrappingContentLayout:block=${block.blockIndex}",
                    urlAtPosition = { offset ->
                        latestTextLayouts.value.firstNotNullOfOrNull { line ->
                            val layout = line.layout
                            val topLeft = line.offset
                            val textStartOffset = line.textStartOffset
                            val localOffset = Offset(offset.x - topLeft.x, offset.y - topLeft.y)
                            if (
                                localOffset.x >= 0f &&
                                localOffset.y >= 0f &&
                                localOffset.x <= layout.size.width.toFloat() &&
                                localOffset.y <= layout.size.height.toFloat()
                            ) {
                                displayFullText.readerUrlAnnotationAtPosition(
                                    layout = layout,
                                    position = localOffset,
                                    textStartOffset = textStartOffset
                                )
                            } else {
                                null
                            }
                        }
                    },
                    touchSlop = viewConfiguration.touchSlop,
                    onLinkClick = { latestOnLinkClick.value(it) }
                )
            }
        }
        .pointerInput(displayFullText) {
            detectTapGestures(
                onTap = { offset ->
                    for (line in latestTextLayouts.value) {
                        val layout = line.layout
                        val topLeft = line.offset
                        val textStartOffset = line.textStartOffset
                        val localOffset = Offset(offset.x - topLeft.x, offset.y - topLeft.y)
                        if (
                            localOffset.x >= 0f &&
                            localOffset.y >= 0f &&
                            localOffset.x <= layout.size.width.toFloat() &&
                            localOffset.y <= layout.size.height.toFloat()
                        ) {
                            val url = displayFullText.readerUrlAnnotationAtPosition(
                                layout = layout,
                                position = localOffset,
                                textStartOffset = textStartOffset
                            )
                            if (url != null) {
                                Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                    "detect_tap_link source=WrappingContentLayout:block=${block.blockIndex} " +
                                        "href=${url.readerLinkDiagPreview()}"
                                )
                                latestOnLinkClick.value(url)
                                return@detectTapGestures
                            }
                        }
                    }
                    latestOnGeneralTap.value(offset)
                }
            )
        }) { measurables, constraints ->
        val (imageRenderWidthPx, imageRenderHeightPx) = run {
            computeImageRenderSizePx(
                block = block.floatedImage,
                density = density,
                maxWidthPx = constraints.maxWidth.toFloat(),
                imageSizeMultiplier = imageSizeMultiplier
            )
        }

        val imagePlacable = if (!hideImages && imageRenderWidthPx > 0 && imageRenderHeightPx > 0) {
            measurables.first().measure(
                Constraints.fixed(
                    imageRenderWidthPx.roundToInt(), imageRenderHeightPx.roundToInt()
                )
            )
        } else {
            null
        }

        val effectiveImageWidth = imagePlacable?.width ?: 0
        val effectiveImageHeight = imagePlacable?.height ?: 0

        var currentY = 0f
        var textOffset = 0
        val layouts = mutableListOf<WrappingTextLayout>()

        while (textOffset < shapingDisplayFullText.length) {
            val isBesideImage = currentY < effectiveImageHeight
            val floatLeft = block.floatedImage.style.float == "left"

            val currentMaxWidth = if (isBesideImage) {
                (constraints.maxWidth - effectiveImageWidth).coerceAtLeast(0)
            } else {
                constraints.maxWidth
            }

            if (currentMaxWidth <= 0) break

            val lineConstraints = constraints.copy(minWidth = 0, maxWidth = currentMaxWidth)
            val remainingText = shapingDisplayFullText.subSequence(textOffset, shapingDisplayFullText.length)

            val styleForMeasure =
                remainingText.spanStyles.firstOrNull { it.item.fontFamily != null }?.item?.fontFamily?.let {
                    textStyle.copy(fontFamily = it)
                } ?: textStyle

            val layoutResult = textMeasurer.measure(
                remainingText, style = styleForMeasure, constraints = lineConstraints
            )

            val firstLineEndOffset = layoutResult.getLineEnd(0, visibleEnd = true)
            if (firstLineEndOffset == 0 && remainingText.isNotEmpty()) {
                textOffset++
                continue
            }
            if (firstLineEndOffset == 0) break
            val lineText = remainingText.subSequence(0, firstLineEndOffset)
            val isStartOfParagraph = paragraphStartOffsets.contains(textOffset)
            val finalLineText = if (isStartOfParagraph) {
                lineText
            } else {
                val stylesWithIndent =
                    lineText.paragraphStyles.filter { it.item.textIndent != null }
                if (stylesWithIndent.isNotEmpty()) {
                    buildAnnotatedString {
                        append(lineText)
                        stylesWithIndent.forEach {
                            addStyle(
                                it.item.copy(textIndent = TextIndent(0.sp, 0.sp)), it.start, it.end
                            )
                        }
                    }
                } else {
                    lineText
                }
            }

            val lineLayout = textMeasurer.measure(
                finalLineText, style = styleForMeasure, constraints = lineConstraints
            )
            val lineOverlayLayout = if (paintOnlyColorOverlayText.isNotEmpty()) {
                val overlayLineText = paintOnlyColorOverlayText.subSequence(
                    textOffset,
                    textOffset + firstLineEndOffset
                )
                val finalOverlayLineText = if (isStartOfParagraph) {
                    overlayLineText
                } else {
                    val stylesWithIndent =
                        overlayLineText.paragraphStyles.filter { it.item.textIndent != null }
                    if (stylesWithIndent.isNotEmpty()) {
                        buildAnnotatedString {
                            append(overlayLineText)
                            stylesWithIndent.forEach {
                                addStyle(
                                    it.item.copy(textIndent = TextIndent(0.sp, 0.sp)),
                                    it.start,
                                    it.end
                                )
                            }
                        }
                    } else {
                        overlayLineText
                    }
                }
                textMeasurer.measure(
                    finalOverlayLineText,
                    style = styleForMeasure,
                    constraints = lineConstraints
                )
            } else {
                null
            }
            val xOffset = if (isBesideImage && floatLeft) effectiveImageWidth.toFloat() else 0f

            layouts.add(
                WrappingTextLayout(
                    layout = lineLayout,
                    overlayLayout = lineOverlayLayout,
                    offset = Offset(xOffset, currentY),
                    textStartOffset = textOffset
                )
            )

            currentY += lineLayout.size.height
            val endOfLineVisibleCharIndex = textOffset + firstLineEndOffset - 1
            val paraIndex = paragraphEndOffsetMap[endOfLineVisibleCharIndex]

            if (paraIndex != null && paraIndex < block.paragraphsToWrap.lastIndex) {
                val currentPara = block.paragraphsToWrap[paraIndex]
                val nextPara = block.paragraphsToWrap[paraIndex + 1]

                val gap = with(density) {
                    val marginBottom = currentPara.style.margin.bottom.toPx()
                    val marginTop = nextPara.style.margin.top.toPx()
                    maxOf(marginBottom, marginTop)
                }
                currentY += gap
            }
            textOffset += firstLineEndOffset
            while (textOffset < shapingDisplayFullText.length && shapingDisplayFullText[textOffset].isWhitespace()) {
                textOffset++
            }
        }
        textLayouts = layouts
        totalHeight = maxOf(currentY, effectiveImageHeight.toFloat()).roundToInt()
        if (READER_LINK_DIAGNOSTICS_ENABLED && displayFullText.getStringAnnotations("URL", 0, displayFullText.length).isNotEmpty()) {
            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                "layout_wrapping block=${block.blockIndex} layouts=${layouts.size} totalHeight=$totalHeight " +
                    displayFullText.readerAnnotatedLinkDiagSummary()
            )
        }
        layout(constraints.maxWidth, totalHeight) {
            if (imagePlacable != null) {
                val imageX = if (block.floatedImage.style.float == "left") 0
                else constraints.maxWidth - effectiveImageWidth
                imagePlacable.placeRelative(x = imageX, y = 0)
            }
        }
    }
}
