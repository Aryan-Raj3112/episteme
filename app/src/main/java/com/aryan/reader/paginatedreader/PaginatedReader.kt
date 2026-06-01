// PaginatedReader.kt
@file:Suppress("VariableNeverRead")

package com.aryan.reader.paginatedreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.aryan.reader.BuildConfig
import androidx.compose.ui.unit.isSpecified
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest.Builder
import com.aryan.reader.R
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.countWords
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.plainTextCharacterCount
import com.aryan.reader.epubreader.HighlightColor
import com.aryan.reader.epubreader.PaginatedTextSelectionMenu
import com.aryan.reader.epubreader.PaletteManagerDialog
import com.aryan.reader.epubreader.ReaderTextAlign
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
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

private fun PaginatedSelection.toSharedHighlightLocator(
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
    val visibleTextRanges: List<NativeVerticalVisibleTextRange> = emptyList()
)

data class NativeVerticalVisibleTextRange(
    val chapterIndex: Int,
    val blockIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int
)

private data class SelectionBlockKey(
    val pageIndex: Int,
    val blockIndex: Int,
    val blockCharOffset: Int
)

private data class NativeVerticalViewportSample(
    val firstVisiblePageIndex: Int,
    val firstVisiblePageScrollOffset: Int,
    val firstVisibleItemSize: Int,
    val isAtStart: Boolean,
    val isAtEnd: Boolean,
    val totalPageCount: Int,
    val layoutTick: Int,
    val initialScrollComplete: Boolean
)

private data class AndroidEpubPageContentBounds(
    val topPx: Int,
    val bottomPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val horizontalPaddingPx: Int,
    val verticalPaddingPx: Int
)

private val AndroidEpubPageContentBounds.pageClipBottomPx: Int
    get() = bottomPx + verticalPaddingPx

private data class NativeVerticalFlowChapter(
    val chapterIndex: Int,
    val title: String?,
    val blocks: List<ContentBlock>,
    val isLoaded: Boolean = true,
    val estimatedLocationWeight: Int = 0
)

private enum class NativeVerticalFlowItemKind {
    BLOCK,
    CHAPTER_GAP,
    EMPTY_CHAPTER,
    UNLOADED_CHAPTER
}

private data class NativeVerticalFlowItem(
    val key: String,
    val chapterIndex: Int,
    val blockOrdinal: Int,
    val block: ContentBlock?,
    val kind: NativeVerticalFlowItemKind,
    val locationWeight: Int
)

private fun buildSelectionBlockKey(
    pageIndex: Int,
    blockIndex: Int,
    blockCharOffset: Int
): String = "${pageIndex}_${blockIndex}_${blockCharOffset}"

internal fun nativeVerticalInitialChapterPrefetchOrder(
    chapterCount: Int,
    initialChapter: Int,
    forwardCount: Int = 2,
    backwardCount: Int = 1
): List<Int> {
    if (chapterCount <= 0) return emptyList()
    val start = initialChapter.coerceIn(0, chapterCount - 1)
    return buildList {
        for (offset in 1..forwardCount.coerceAtLeast(0)) {
            val chapterIndex = start + offset
            if (chapterIndex < chapterCount) add(chapterIndex)
        }
        for (offset in 1..backwardCount.coerceAtLeast(0)) {
            val chapterIndex = start - offset
            if (chapterIndex >= 0) add(chapterIndex)
        }
    }
}

private fun parseSelectionBlockKey(key: String): SelectionBlockKey? {
    val parts = key.split("_")
    if (parts.size != 3) return null
    return SelectionBlockKey(
        pageIndex = parts[0].toIntOrNull() ?: return null,
        blockIndex = parts[1].toIntOrNull() ?: return null,
        blockCharOffset = parts[2].toIntOrNull() ?: return null
    )
}

private fun compareSelectionBlockKeys(
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

private fun getTextBlockCharOffset(block: TextContentBlock): Int = when (block) {
    is ParagraphBlock -> block.startCharOffsetInSource
    is HeaderBlock -> block.startCharOffsetInSource
    is QuoteBlock -> block.startCharOffsetInSource
    is ListItemBlock -> block.startCharOffsetInSource
}

private fun textBlockLayoutKey(
    cfi: String,
    pageIndex: Int,
    block: TextContentBlock
): String = "${cfi}_${block.blockIndex}_${getTextBlockCharOffset(block)}_${block.content.text.length}_$pageIndex"

private fun legacyTextBlockLayoutKey(cfi: String, pageIndex: Int): String = "${cfi}_$pageIndex"

private fun headerFontScale(level: Int): Float = when (level) {
    1 -> 1.5f
    2 -> 1.4f
    3 -> 1.3f
    4 -> 1.2f
    5 -> 1.1f
    else -> 1.0f
}

private const val WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER = 1.2f
private const val AndroidEpubCutoffLogTag = "EpistemeEpubCutoff"
private const val AndroidEpubCutoffTolerancePx = 1
private const val AndroidEpubCutoffEdgeProbePx = 2
private const val TAG_STABLE_PAGE_NAV = "StablePageNav"
private const val TAG_PAGINATED_HIGHLIGHT_DIAG = "PaginatedHighlightDiag"
private const val TAG_ANDROID_HIGHLIGHT_RENDER_DIAG = "AndroidHighlightRenderDiag"
private const val EXPLICIT_NAVIGATION_SHIFT_ANCHOR_WINDOW_MS = 10_000L
private const val DEBUG_PAGE_TURN_DIAG = false

private fun highlightDiagSnippet(text: String, maxLength: Int = 80): String {
    return text
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .take(maxLength)
}

private fun UserHighlight.androidHighlightRenderLabel(): String {
    val highlightLocator = this.locator
    return "highlightId=$id highlightChapter=$chapterIndex " +
        "highlightCfi=${highlightDiagSnippet(cfi, 120)} textLen=${text.length} " +
        "text='${highlightDiagSnippet(text)}' " +
        "locatorChapter=${highlightLocator.chapterIndex} locatorPage=${highlightLocator.pageIndex} " +
        "locatorOffsets=${highlightLocator.startOffset}..${highlightLocator.endOffset} " +
        "locatorBlock=${highlightLocator.blockIndex} locatorChar=${highlightLocator.charOffset} " +
        "locatorCfi=${highlightDiagSnippet(highlightLocator.cfi.orEmpty(), 120)}"
}

private fun paginationLineHeightMultiplierForWebViewSetting(multiplier: Float): Float {
    return if (abs(multiplier - 1.0f) < 0.001f) WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER else multiplier
}

private fun createHeaderTextStyle(
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

private fun compareBlockPositionsOnPage(
    firstBlockIndex: Int,
    firstBlockCharOffset: Int,
    secondBlockIndex: Int,
    secondBlockCharOffset: Int
): Int = when {
    firstBlockIndex != secondBlockIndex -> firstBlockIndex.compareTo(secondBlockIndex)
    else -> firstBlockCharOffset.compareTo(secondBlockCharOffset)
}

private fun isBlockSelectedOnPage(
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

private fun isSelectionBlockKeyInsideSelection(
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

private data class AttachedSelectionBlock(
    val pageIndex: Int,
    val layout: TextLayoutResult,
    val coords: LayoutCoordinates,
    val block: TextContentBlock
)

private fun attachedSelectionBlocks(
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

private fun visibleSelectedBlocks(
    blockLayoutMap: Map<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    selection: PaginatedSelection
): List<AttachedSelectionBlock> {
    return attachedSelectionBlocks(blockLayoutMap) { pageIndex ->
        pageIndex in selection.startPageIndex..selection.endPageIndex
    }.filter { blockInfo ->
        isBlockSelectedOnPage(blockInfo.block, blockInfo.pageIndex, selection)
    }
}

private fun selectionWindowBounds(
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

private fun findSelectionLayout(
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

private fun selectionHandleRootPosition(
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

private fun updatedSelectionForHandleDrag(
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
private fun estimateNativeVerticalCompatPage(
    book: EpubBook,
    paginator: BookPaginator,
    locator: Locator?,
    fallbackPage: Int
): Int {
    if (locator == null) return fallbackPage
    val chapterStart = paginator.chapterStartPageIndices[locator.chapterIndex] ?: return fallbackPage
    val chapterPageCount = paginator.chapterPageCounts[locator.chapterIndex] ?: 1
    if (chapterPageCount <= 1) return chapterStart

    val chapterChars = book.chaptersForPagination
        .getOrNull(locator.chapterIndex)
        ?.plainTextCharacterCount()
        ?.coerceAtLeast(1)
        ?: return fallbackPage
    val ratio = locator.charOffset.toFloat().coerceAtLeast(0f) / chapterChars.toFloat()
    val pageInChapter = (ratio.coerceIn(0f, 1f) * (chapterPageCount - 1)).roundToInt()
    return chapterStart + pageInChapter
}

private fun estimateNativeVerticalProgressPercent(
    book: EpubBook,
    locator: Locator?
): Float? {
    if (locator == null) return null
    val totalChars = book.chaptersForPagination
        .sumOf { it.plainTextCharacterCount().toLong() }
        .takeIf { it > 0L }
        ?: return null
    val completedChars = book.chaptersForPagination
        .take(locator.chapterIndex)
        .sumOf { it.plainTextCharacterCount().toLong() }
    val chapterChars = book.chaptersForPagination
        .getOrNull(locator.chapterIndex)
        ?.plainTextCharacterCount()
        ?.toLong()
        ?: 0L
    val chapterOffset = locator.charOffset
        .toLong()
        .coerceIn(0L, chapterChars.coerceAtLeast(0L))
    return (((completedChars + chapterOffset).toDouble() / totalChars.toDouble()) * 100.0)
        .toFloat()
        .coerceIn(0f, 100f)
}

private fun locatorForNativeVerticalFlowBlock(chapterIndex: Int, block: ContentBlock): Locator {
    val firstTextBlock = listOf(block)
        .extractTextBlocks()
        .firstOrNull { it.content.text.isNotBlank() }
        ?: listOf(block).extractTextBlocks().firstOrNull()

    return if (firstTextBlock != null) {
        Locator(
            chapterIndex = chapterIndex,
            blockIndex = firstTextBlock.blockIndex,
            charOffset = getTextBlockCharOffset(firstTextBlock)
        )
    } else {
        Locator(
            chapterIndex = chapterIndex,
            blockIndex = block.blockIndex,
            charOffset = 0
        )
    }
}

private fun findNativeVerticalFlowTextBlockForLocator(
    chapters: List<NativeVerticalFlowChapter>,
    locator: Locator
): TextContentBlock? {
    val blocks = chapters.firstOrNull { it.chapterIndex == locator.chapterIndex }?.blocks
        ?: return null
    val textBlocks = blocks.extractTextBlocks()
    return textBlocks.firstOrNull { block ->
        val start = getTextBlockCharOffset(block)
        val end = start + block.content.text.length
        block.blockIndex == locator.blockIndex && locator.charOffset in start..end
    } ?: textBlocks.firstOrNull { it.blockIndex >= locator.blockIndex }
        ?: textBlocks.firstOrNull()
}

private fun nativeVerticalFlowBlockMatchesLocator(block: ContentBlock, locator: Locator): Boolean {
    if (block.blockIndex == locator.blockIndex) return true
    return when (block) {
        is FlexContainerBlock -> block.children.any { nativeVerticalFlowBlockMatchesLocator(it, locator) }
        is TableBlock -> block.rows.flatten().any { cell ->
            cell.content.any { nativeVerticalFlowBlockMatchesLocator(it, locator) }
        }
        is WrappingContentBlock ->
            nativeVerticalFlowBlockMatchesLocator(block.floatedImage, locator) ||
                block.paragraphsToWrap.any { nativeVerticalFlowBlockMatchesLocator(it, locator) }
        else -> false
    }
}

private fun nativeVerticalFlowItemWeight(block: ContentBlock?): Int {
    if (block == null) return 0
    val textLength = listOf(block).extractTextBlocks()
        .sumOf { it.content.text.length }
    return textLength.coerceAtLeast(
        when (block) {
            is ImageBlock -> 250
            is MathBlock -> 80
            is SpacerBlock -> 1
            else -> 24
        }
    )
}

internal fun nativeVerticalCompatPageForProgress(progressPercent: Float, totalPageCount: Int): Int {
    if (totalPageCount <= 1) return 0
    return ((progressPercent.coerceIn(0f, 100f) / 100f) * (totalPageCount - 1))
        .roundToInt()
        .coerceIn(0, totalPageCount - 1)
}

internal fun nativeVerticalProgressForCompatPage(pageIndex: Int, totalPageCount: Int): Float {
    if (totalPageCount <= 1) return 0f
    return (pageIndex.coerceIn(0, totalPageCount - 1).toFloat() / (totalPageCount - 1).toFloat() * 100f)
        .coerceIn(0f, 100f)
}

internal fun nativeVerticalProgressToItemIndex(
    itemWeights: List<Int>,
    progressPercent: Float
): Int? {
    if (itemWeights.isEmpty()) return null
    val totalWeight = itemWeights.sumOf { it.coerceAtLeast(0) }
    if (totalWeight <= 0) {
        return ((progressPercent.coerceIn(0f, 100f) / 100f) * (itemWeights.size - 1))
            .roundToInt()
            .coerceIn(0, itemWeights.lastIndex)
    }

    val targetWeight = totalWeight * (progressPercent.coerceIn(0f, 100f) / 100f)
    var accumulated = 0
    var lastWeightedIndex = 0
    itemWeights.forEachIndexed { index, rawWeight ->
        val weight = rawWeight.coerceAtLeast(0)
        if (weight <= 0) return@forEachIndexed
        lastWeightedIndex = index
        val next = accumulated + weight
        if (targetWeight <= next || index == itemWeights.lastIndex) {
            return index
        }
        accumulated = next
    }
    return lastWeightedIndex
}

private fun buildNativeVerticalFlowItems(
    chapters: List<NativeVerticalFlowChapter>
): List<NativeVerticalFlowItem> {
    return chapters.flatMapIndexed { chapterOrdinal, chapter ->
        val boundary = if (chapterOrdinal > 0) {
            listOf(
                NativeVerticalFlowItem(
                    key = "chapter-${chapter.chapterIndex}-gap",
                    chapterIndex = chapter.chapterIndex,
                    blockOrdinal = -2,
                    block = null,
                    kind = NativeVerticalFlowItemKind.CHAPTER_GAP,
                    locationWeight = 0
                )
            )
        } else {
            emptyList()
        }
        if (!chapter.isLoaded) {
            boundary + listOf(
                NativeVerticalFlowItem(
                    key = "chapter-${chapter.chapterIndex}-unloaded",
                    chapterIndex = chapter.chapterIndex,
                    blockOrdinal = -1,
                    block = null,
                    kind = NativeVerticalFlowItemKind.UNLOADED_CHAPTER,
                    locationWeight = chapter.estimatedLocationWeight.coerceAtLeast(24)
                )
            )
        } else if (chapter.blocks.isEmpty()) {
            boundary + listOf(
                NativeVerticalFlowItem(
                    key = "chapter-${chapter.chapterIndex}-empty",
                    chapterIndex = chapter.chapterIndex,
                    blockOrdinal = -1,
                    block = null,
                    kind = NativeVerticalFlowItemKind.EMPTY_CHAPTER,
                    locationWeight = 0
                )
            )
        } else {
            boundary + chapter.blocks.mapIndexed { ordinal, block ->
                NativeVerticalFlowItem(
                    key = "chapter-${chapter.chapterIndex}-block-$ordinal-${block.blockIndex}",
                    chapterIndex = chapter.chapterIndex,
                    blockOrdinal = ordinal,
                    block = block,
                    kind = NativeVerticalFlowItemKind.BLOCK,
                    locationWeight = nativeVerticalFlowItemWeight(block)
                )
            }
        }
    }
}

private fun findNativeVerticalFlowItemIndexForProgress(
    items: List<NativeVerticalFlowItem>,
    progressPercent: Float
): Int? {
    return nativeVerticalProgressToItemIndex(
        itemWeights = items.map { it.locationWeight },
        progressPercent = progressPercent
    )
}

private fun estimateNativeVerticalScrollProgressPercent(
    items: List<NativeVerticalFlowItem>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int
): Float? {
    if (items.isEmpty()) return null
    val totalWeight = items.sumOf { it.locationWeight }.takeIf { it > 0 } ?: return null
    val safeIndex = firstVisibleItemIndex.coerceIn(0, items.lastIndex)
    val completedWeight = items
        .take(safeIndex)
        .sumOf { it.locationWeight }
    val currentItem = items[safeIndex]
    val currentFraction = if (firstVisibleItemSize > 0) {
        (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize.toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val weightedPosition = completedWeight + (currentItem.locationWeight * currentFraction)
    return ((weightedPosition.toDouble() / totalWeight.toDouble()) * 100.0)
        .toFloat()
        .coerceIn(0f, 100f)
}

private fun findNativeVerticalFlowItemIndexForLocator(
    items: List<NativeVerticalFlowItem>,
    chapters: List<NativeVerticalFlowChapter>,
    locator: Locator
): Int? {
    val targetTextBlock = findNativeVerticalFlowTextBlockForLocator(chapters, locator)
    if (targetTextBlock != null && targetTextBlock.blockIndex == locator.blockIndex) {
        val exactIndex = items.indexOfFirst { item ->
            item.chapterIndex == locator.chapterIndex &&
                item.block?.let { block ->
                    listOf(block).extractTextBlocks().any { textBlock ->
                        textBlock.cfi == targetTextBlock.cfi ||
                            (
                                textBlock.blockIndex == targetTextBlock.blockIndex &&
                                    getTextBlockCharOffset(textBlock) == getTextBlockCharOffset(targetTextBlock)
                                )
                    }
                } == true
        }
        if (exactIndex >= 0) return exactIndex
    }

    val matchingContainerIndex = items.indexOfFirst { item ->
        item.chapterIndex == locator.chapterIndex &&
            item.block?.let { nativeVerticalFlowBlockMatchesLocator(it, locator) } == true
    }
    if (matchingContainerIndex >= 0) return matchingContainerIndex

    val blockIndex = items.indexOfFirst { item ->
        item.chapterIndex == locator.chapterIndex &&
            (item.block?.blockIndex ?: Int.MAX_VALUE) >= locator.blockIndex
    }
    if (blockIndex >= 0) return blockIndex

    return items.indexOfFirst { it.chapterIndex == locator.chapterIndex }
        .takeIf { it >= 0 }
}

private fun locatorForNativeVerticalFlowItem(item: NativeVerticalFlowItem): Locator? {
    return item.block?.let { locatorForNativeVerticalFlowBlock(item.chapterIndex, it) }
        ?: Locator(item.chapterIndex, 0, 0)
}

private fun resolveNativeVerticalScrollDeltaForLocator(
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

private fun resolveNativeVerticalFlowVisibleLocator(
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

private fun resolveNativeVerticalVisibleTextRanges(
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
                NativeVerticalVisibleTextRange(
                    chapterIndex = chapterIndex,
                    blockIndex = block.blockIndex,
                    startCharOffset = start,
                    endCharOffset = end
                )
            }
        }
        .toList()
}

private fun resolveReaderFootnoteHtml(
    book: EpubBook,
    currentChapterPath: String,
    href: String
): String? {
    var isFootnote = href.contains("footnote", ignoreCase = true) ||
        href.contains("fn", ignoreCase = true)
    var footnoteHtml: String? = null

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
                val targetHtml = targetChapter.htmlContent.ifEmpty {
                    try {
                        File(book.extractionBasePath, targetChapter.htmlFilePath).readText()
                    } catch (_: Exception) {
                        ""
                    }
                }
                if (targetHtml.isNotEmpty()) {
                    val doc = Jsoup.parse(targetHtml)
                    val noteEl = doc.getElementById(anchor)
                    if (noteEl != null) {
                        val targetType = noteEl.attr("epub:type")
                        val targetRole = noteEl.attr("role")
                        val targetClass = noteEl.className()
                        val targetLooksLikeFootnote =
                            targetType.contains("footnote", ignoreCase = true) ||
                                targetRole.contains("doc-footnote", ignoreCase = true) ||
                                targetClass.contains("footnote", ignoreCase = true)
                        if (isFootnote || targetLooksLikeFootnote) {
                            isFootnote = true
                            footnoteHtml = noteEl.html()
                        }
                    }
                }
            }
        }
    }

    return footnoteHtml.takeIf { isFootnote && !it.isNullOrBlank() }
}

data class PendingCrossPageSelection(val fromPageIndex: Int)

enum class SelectionHandle { START, END }

private class SmartPopupPositionProvider(
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

private fun highlightQueryInText(
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

private fun String.readerExternalHrefForDisplay(): String {
    val href = trim()
    return if (href.startsWith("//")) "https:$href" else href
}

private const val READER_LINK_HIT_SLOP_PX = 2f

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

private fun TextLayoutResult.readerTextRangeContainsPosition(
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

private data class ReaderPageLinkHit(
    val href: String,
    val blockIndex: Int,
    val cfi: String?
)

private fun ReactiveBlockMap.readerLinkAtPagePosition(
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

private suspend fun AwaitPointerEventScope.awaitReaderLinkTap(
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

private fun AnnotatedString.withReaderLinkDisplayStyle(
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

@Composable
private fun LinkAwareText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
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
        if (displayText.getStringAnnotations("URL", 0, displayText.length).isNotEmpty()) {
            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                "compose_text source=LinkAwareText " + displayText.readerAnnotatedLinkDiagSummary()
            )
        }
    }

    Text(
        text = displayText,
        style = style,
        modifier = modifier
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
            if (displayText.getStringAnnotations("URL", 0, displayText.length).isNotEmpty()) {
                Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                    "layout_text source=LinkAwareText size=${it.size.width}x${it.size.height} " +
                        "lines=${it.lineCount} " + displayText.readerAnnotatedLinkDiagSummary()
                )
            }
        }
    )
}

private fun computeImageRenderSizePx(
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
            maxWidthPx
        }
    }

    var scaledWidth = baseWidth * imageSizeMultiplier
    if (block.style.maxWidth.isSpecified && block.style.maxWidth > 0.dp) {
        scaledWidth = scaledWidth.coerceAtMost(with(density) { block.style.maxWidth.toPx() } * imageSizeMultiplier)
    }
    scaledWidth = scaledWidth.coerceAtMost(maxWidthPx)

    return scaledWidth to (scaledWidth * aspectRatio)
}

private fun computeImageRenderSizeDp(
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

private fun imageBlockContentAlignment(style: BlockStyle): Alignment {
    return when {
        style.float == "right" || style.horizontalAlign == "right" || style.horizontalAlign == "end" -> Alignment.CenterEnd
        style.float == "left" || style.horizontalAlign == "left" || style.horizontalAlign == "start" -> Alignment.CenterStart
        else -> Alignment.Center
    }
}

private fun imageContentScale(style: BlockStyle): ContentScale {
    return when (style.objectFit) {
        "cover" -> ContentScale.Crop
        "fill" -> ContentScale.FillBounds
        "contain", "scale-down" -> ContentScale.Fit
        else -> ContentScale.Fit
    }
}

private fun tableCellImageModifier(
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

@Composable
private fun WrappingContentLayout(
    block: WrappingContentBlock,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
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
        mutableStateOf<List<Triple<TextLayoutResult, Offset, Int>>>(emptyList())
    }
    var totalHeight by remember { mutableIntStateOf(0) }
    val latestTextLayouts = rememberUpdatedState(textLayouts)
    val latestOnLinkClick = rememberUpdatedState(onLinkClick)
    val latestOnGeneralTap = rememberUpdatedState(onGeneralTap)

    Layout(content = {
        AsyncImage(
            model = Builder(LocalContext.current).data(File(block.floatedImage.path)).build(),
            contentDescription = block.floatedImage.altText,
            contentScale = imageContentScale(block.floatedImage.style)
        )
    }, modifier = modifier
        .drawBehind {
            textLayouts.forEach { (layout, offset, _) ->
                drawText(layout, topLeft = offset)
            }
        }
        .pointerInput(displayFullText, viewConfiguration.touchSlop) {
            awaitEachGesture {
                awaitReaderLinkTap(
                    source = "WrappingContentLayout:block=${block.blockIndex}",
                    urlAtPosition = { offset ->
                        latestTextLayouts.value.firstNotNullOfOrNull { (layout, topLeft, textStartOffset) ->
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
                    for ((layout, topLeft, textStartOffset) in latestTextLayouts.value) {
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

        val imagePlacable = if (imageRenderWidthPx > 0 && imageRenderHeightPx > 0) {
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
        val layouts = mutableListOf<Triple<TextLayoutResult, Offset, Int>>()

        while (textOffset < displayFullText.length) {
            val isBesideImage = currentY < effectiveImageHeight
            val floatLeft = block.floatedImage.style.float == "left"

            val currentMaxWidth = if (isBesideImage) {
                (constraints.maxWidth - effectiveImageWidth).coerceAtLeast(0)
            } else {
                constraints.maxWidth
            }

            if (currentMaxWidth <= 0) break

            val lineConstraints = constraints.copy(minWidth = 0, maxWidth = currentMaxWidth)
            val remainingText = displayFullText.subSequence(textOffset, displayFullText.length)

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
            val xOffset = if (isBesideImage && floatLeft) effectiveImageWidth.toFloat() else 0f

            layouts.add(Triple(lineLayout, Offset(xOffset, currentY), textOffset))

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
            while (textOffset < displayFullText.length && displayFullText[textOffset].isWhitespace()) {
                textOffset++
            }
        }
        textLayouts = layouts
        totalHeight = maxOf(currentY, effectiveImageHeight.toFloat()).roundToInt()
        if (displayFullText.getStringAnnotations("URL", 0, displayFullText.length).isNotEmpty()) {
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

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalFoundationApi::class, ExperimentalSerializationApi::class, FlowPreview::class)
@Composable
fun PaginatedReaderScreen(
    modifier: Modifier = Modifier,
    book: EpubBook,
    bookId: String? = null,
    isDarkTheme: Boolean,
    effectiveBg: Color,
    effectiveText: Color,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    isRightToLeftPagination: Boolean = false,
    searchQuery: String,
    fontSizeMultiplier: Float,
    lineHeightMultiplier: Float,
    paragraphGapMultiplier: Float,
    imageSizeMultiplier: Float,
    horizontalMarginMultiplier: Float,
    verticalMarginMultiplier: Float,
    fontFamily: FontFamily,
    textAlign: ReaderTextAlign,
    bookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences(),
    bookReplacementFileId: String? = bookId,
    ttsHighlightInfo: TtsHighlightInfo?,
    initialChapterIndexInBook: Int?,
    fallbackLocatorForReconfiguration: Locator? = null,
    explicitNavigationAnchor: Locator? = null,
    explicitNavigationEpoch: Long = 0L,
    isExternalNavigationInProgress: Boolean = false,
    onReconfigurationAnchorCaptured: (Locator) -> Unit = {},
    onReconfigurationRestoreActiveChanged: (Boolean) -> Unit = {},
    onPaginatorReady: (IPaginator) -> Unit,
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onFootnoteRequested: (String) -> Unit,
    onInternalLinkNavigated: (Int, Locator?) -> Unit = { _, _ -> },
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String, SharedReaderLocator) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit,
    activeTextureId: String? = null,
    activeTextureAlpha: Float = 0.55f
) {
    LaunchedEffect(userHighlights) {
        Timber.d("PaginatedReaderScreen: Received ${userHighlights.size} highlights.")
        userHighlights.forEach {
            Timber.d(" -> Received Highlight: CFI=${it.cfi}, Text='${it.text.take(20)}...'")
        }
    }

    val context = LocalContext.current
    val textureBitmap = remember(activeTextureId) {
        loadReaderTextureBitmap(context, activeTextureId)
    }

    val textureModifier = if (textureBitmap != null) {
        Modifier.drawBehind {
            val brush = ShaderBrush(
                ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)
            )
            drawRect(brush = brush, blendMode = BlendMode.SrcOver, alpha = activeTextureAlpha.coerceIn(0f, 1f))
        }
    } else Modifier

    var isNavigatingByLink by remember { mutableStateOf(false) }
    var localExplicitNavigationAnchor by remember { mutableStateOf<Locator?>(null) }
    var localExplicitNavigationEpoch by remember { mutableLongStateOf(0L) }
    val latestExternalNavigationAnchor by rememberUpdatedState(explicitNavigationAnchor)
    val latestExternalNavigationEpoch by rememberUpdatedState(explicitNavigationEpoch)
    val latestIsExternalNavigationInProgress by rememberUpdatedState(isExternalNavigationInProgress)
    val bookReplacementSignature = remember(bookReplacementPreferences, bookReplacementFileId) {
        bookReplacementPreferences.signatureForFile(bookReplacementFileId)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(effectiveBg)) {
        val textMeasurer = rememberTextMeasurer()
        val baseTextStyle = MaterialTheme.typography.bodyLarge

        var debouncedFontSizeMult by remember { mutableFloatStateOf(fontSizeMultiplier) }
        var debouncedLineHeightMult by remember { mutableFloatStateOf(lineHeightMultiplier) }
        var debouncedParagraphGapMult by remember { mutableFloatStateOf(paragraphGapMultiplier) }
        var debouncedImageSizeMult by remember { mutableFloatStateOf(imageSizeMultiplier) }
        var debouncedHorizontalMarginMult by remember { mutableFloatStateOf(horizontalMarginMultiplier) }
        var debouncedVerticalMarginMult by remember { mutableFloatStateOf(verticalMarginMultiplier) }
        var debouncedFontFamily by remember { mutableStateOf(fontFamily) }
        var debouncedTextAlign by remember { mutableStateOf(textAlign) }
        var debouncedBookReplacementSignature by remember { mutableStateOf(bookReplacementSignature) }
        var debouncedBookReplacementPreferences by remember { mutableStateOf(bookReplacementPreferences) }
        var debouncedBookReplacementFileId by remember { mutableStateOf(bookReplacementFileId) }

        var anchorLocatorForReconfig by remember { mutableStateOf<Locator?>(null) }
        val currentPaginatorRef = remember { mutableStateOf<IPaginator?>(null) }
        val latestFallbackLocatorForReconfiguration by rememberUpdatedState(fallbackLocatorForReconfiguration)

        var previousConstraints by remember {
            mutableStateOf(this.constraints)
        }

        if (previousConstraints != this.constraints) {
            val activePaginator = currentPaginatorRef.value
            val currentPage = pagerState.currentPage
            val locator = resolvePaginatedReconfigurationAnchor(
                currentPageLocator = (activePaginator as? BookPaginator)?.getLocatorForPage(currentPage),
                fallbackLocator = fallbackLocatorForReconfiguration
            )
            anchorLocatorForReconfig = locator

            Timber.tag("ThemeReconfig").d("""
            RECONFIG DETECTED
            - Reason: Constraints
            - Current Page: $currentPage
            - Saved Locator: $locator
        """.trimIndent())
            previousConstraints = this.constraints
        }

        val layoutTextStyle = remember(
            baseTextStyle,
            debouncedFontSizeMult,
            debouncedLineHeightMult,
            debouncedFontFamily
        ) {
            val adjustedFontSize = baseTextStyle.fontSize * debouncedFontSizeMult
            val adjustedLineHeight = adjustedFontSize * paginationLineHeightMultiplierForWebViewSetting(debouncedLineHeightMult)

            baseTextStyle.copy(
                color = Color.Unspecified,
                fontSize = adjustedFontSize,
                lineHeight = adjustedLineHeight,
                fontFamily = debouncedFontFamily,
                lineBreak = LineBreak.Paragraph,
                letterSpacing = TextUnit.Unspecified,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.None
                )
            )
        }
        val textStyle = remember(layoutTextStyle, effectiveText) {
            layoutTextStyle.copy(color = effectiveText)
        }

        if (DEBUG_PAGE_TURN_DIAG) {
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    Timber.tag("PageTurnDiag").i("Pager Settled: Now on page $page at ${System.currentTimeMillis()}")
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
                    Timber.tag("PageTurnDiag").d("Pager Scroll State: isScrolling=$isScrolling")
                }
            }
        }

        LaunchedEffect(fontSizeMultiplier, lineHeightMultiplier, paragraphGapMultiplier, imageSizeMultiplier, horizontalMarginMultiplier, verticalMarginMultiplier, fontFamily, textAlign, bookReplacementSignature, bookReplacementFileId) {
            if (fontSizeMultiplier != debouncedFontSizeMult ||
                lineHeightMultiplier != debouncedLineHeightMult ||
                paragraphGapMultiplier != debouncedParagraphGapMult ||
                imageSizeMultiplier != debouncedImageSizeMult ||
                horizontalMarginMultiplier != debouncedHorizontalMarginMult ||
                verticalMarginMultiplier != debouncedVerticalMarginMult ||
                fontFamily != debouncedFontFamily ||
                textAlign != debouncedTextAlign ||
                bookReplacementSignature != debouncedBookReplacementSignature ||
                bookReplacementFileId != debouncedBookReplacementFileId
            ) {
                Timber.d("Formatting changed. Waiting for debounce.")
                delay(400L)

                val activePaginator = currentPaginatorRef.value
                val currentPage = pagerState.currentPage
                val locator = resolvePaginatedReconfigurationAnchor(
                    currentPageLocator = (activePaginator as? BookPaginator)?.getLocatorForPage(currentPage),
                    fallbackLocator = fallbackLocatorForReconfiguration
                )
                if (locator != null) {
                    anchorLocatorForReconfig = locator
                }

                debouncedFontSizeMult = fontSizeMultiplier
                debouncedLineHeightMult = lineHeightMultiplier
                debouncedParagraphGapMult = paragraphGapMultiplier
                debouncedImageSizeMult = imageSizeMultiplier
                debouncedHorizontalMarginMult = horizontalMarginMultiplier
                debouncedVerticalMarginMult = verticalMarginMultiplier
                debouncedFontFamily = fontFamily
                debouncedTextAlign = textAlign
                debouncedBookReplacementSignature = bookReplacementSignature
                debouncedBookReplacementPreferences = bookReplacementPreferences
                debouncedBookReplacementFileId = bookReplacementFileId
                Timber.d("Debounce complete. Applying new format settings.")
            }
        }

        val userTextAlign = remember(debouncedTextAlign) {
            when (debouncedTextAlign) {
                ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                ReaderTextAlign.LEFT -> TextAlign.Left
                ReaderTextAlign.RIGHT -> TextAlign.Right
                ReaderTextAlign.DEFAULT -> null
            }
        }

        val density = LocalDensity.current
        val requestedHorizontalPadding = 16.dp * debouncedHorizontalMarginMult
        val requestedVerticalPadding = 16.dp * debouncedVerticalMarginMult
        val effectiveReaderPadding =
            remember(this.constraints, density, requestedHorizontalPadding, requestedVerticalPadding) {
                val requestedHorizontalPaddingPx = with(density) { requestedHorizontalPadding.roundToPx() }
                val requestedVerticalPaddingPx = with(density) { requestedVerticalPadding.roundToPx() }
                val minReadableWidthPx = with(density) { 96.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxWidth)
                val minReadableHeightPx = with(density) { 160.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxHeight)
                val horizontalPaddingPx = requestedHorizontalPaddingPx.coerceAtMost(
                    ((this.constraints.maxWidth - minReadableWidthPx) / 2).coerceAtLeast(0)
                )
                val verticalPaddingPx = requestedVerticalPaddingPx.coerceAtMost(
                    ((this.constraints.maxHeight - minReadableHeightPx) / 2).coerceAtLeast(0)
                )
                with(density) {
                    horizontalPaddingPx.toDp() to verticalPaddingPx.toDp()
                }
            }
        val horizontalPadding = effectiveReaderPadding.first
        val verticalPadding = effectiveReaderPadding.second

        val textConstraints =
            remember(this.constraints, density, horizontalPadding, verticalPadding) {
                val horizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
                val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
                val finalConstraints = this.constraints.copy(
                    minWidth = 0,
                    maxWidth = (this.constraints.maxWidth - (2 * horizontalPaddingPx)).coerceAtLeast(1),
                    minHeight = 0,
                    maxHeight = (this.constraints.maxHeight - (2 * verticalPaddingPx)).coerceAtLeast(1)
                )
                finalConstraints
            }

        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val mathMLRenderer = remember { MathMLRenderer(context.applicationContext) }

        DisposableEffect(Unit) {
            onDispose {
                mathMLRenderer.destroy()
                Timber.d("PaginatedReaderScreen disposed, MathMLRenderer destroyed.")
            }
        }

        val effectiveInitialChapter =
            remember(initialChapterIndexInBook, anchorLocatorForReconfig) {
                anchorLocatorForReconfig?.chapterIndex ?: initialChapterIndexInBook ?: 0
            }

        LaunchedEffect(anchorLocatorForReconfig) {
            anchorLocatorForReconfig?.let { locator ->
                onReconfigurationAnchorCaptured(locator)
                onReconfigurationRestoreActiveChanged(true)
            }
        }

        val paginator = remember(book, bookId, textConstraints, layoutTextStyle, userTextAlign, debouncedParagraphGapMult, debouncedImageSizeMult, debouncedVerticalMarginMult, debouncedBookReplacementSignature, debouncedBookReplacementFileId) {
        val userAgentStylesheet = UserAgentStylesheet.default
            var allRules = OptimizedCssRules()
            val allFontFaces = mutableListOf<FontFaceInfo>()

            val uaResult = CssParser.parse(
                cssContent = userAgentStylesheet,
                cssPath = null,
                baseFontSizeSp = layoutTextStyle.fontSize.value,
                density = density.density,
                constraints = textConstraints,
                isDarkTheme = false,
                adaptThemeColors = false
            )
            allRules = allRules.merge(uaResult.rules)
            allFontFaces.addAll(uaResult.fontFaces)

            book.css.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = layoutTextStyle.fontSize.value,
                    density = density.density,
                    constraints = textConstraints,
                    isDarkTheme = false,
                    adaptThemeColors = false
                )
                allRules = allRules.merge(bookCssResult.rules)
                allFontFaces.addAll(bookCssResult.fontFaces)
            }
            val fontFamilyMap = loadFontFamilies(
                fontFaces = allFontFaces, extractionPath = book.extractionBasePath
            )
            val bookCacheDao =
                BookCacheDatabase.getDatabase(context.applicationContext).bookCacheDao()
            val proto = ProtoBuf { serializersModule = semanticBlockModule }

            val uniqueBookId = bookId ?: if (book.fileName.length > 20) book.fileName else book.title

            Timber.d("Recreating BookPaginator for ID: $uniqueBookId. TextAlign: $userTextAlign")
            Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: Instantiating BookPaginator. book.chaptersForPagination.size=${book.chaptersForPagination.size}, initialChapter=$effectiveInitialChapter")

            BookPaginator(
                coroutineScope = coroutineScope,
                chapters = book.chaptersForPagination,
                textMeasurer = textMeasurer,
                constraints = textConstraints,
                textStyle = layoutTextStyle,
                extractionBasePath = book.extractionBasePath,
                density = density,
                fontFamilyMap = fontFamilyMap,
                isDarkTheme = isDarkTheme,
                themeBackgroundColor = effectiveBg,
                themeTextColor = effectiveText,
                bookId = uniqueBookId,
                bookCacheDao = bookCacheDao,
                proto = proto,
                initialChapterToPaginate = effectiveInitialChapter,
                bookCss = book.css,
                userAgentStylesheet = userAgentStylesheet,
                allFontFaces = allFontFaces,
                context = context.applicationContext,
                mathMLRenderer = mathMLRenderer,
                userTextAlign = userTextAlign,
                paragraphGapMultiplier = debouncedParagraphGapMult,
                imageSizeMultiplier = debouncedImageSizeMult,
                verticalMarginMultiplier = debouncedVerticalMarginMult,
                bookReplacementPreferences = debouncedBookReplacementPreferences,
                bookReplacementFileId = debouncedBookReplacementFileId
            )
        }

        LaunchedEffect(paginator) {
            onPaginatorReady(paginator)
            currentPaginatorRef.value = paginator
        }

        DisposableEffect(paginator) {
            onDispose {
                if (currentPaginatorRef.value === paginator) {
                    currentPaginatorRef.value = null
                }
                paginator.dispose()
            }
        }

        LaunchedEffect(paginator) {
            if (anchorLocatorForReconfig != null) {
                Timber.tag("POS_DIAG").d("Restoration Triggered. Anchor Locator: $anchorLocatorForReconfig")

                try {
                    onReconfigurationRestoreActiveChanged(true)
                    snapshotFlow { paginator.isLoading }.filter { !it }.first()

                    val targetLocator = anchorLocatorForReconfig
                    if (targetLocator != null) {
                        val page = paginator.findPageForLocator(targetLocator)

                        Timber.tag("POS_DIAG").d("Restoration Result: Paginator resolved locator to page: $page")

                        if (page != null) {
                            pagerState.scrollToPage(page)
                            paginator.onUserScrolledTo(page)
                            Timber.tag("POS_DIAG").i("Restoration: Pager scrolled to $page")
                        } else {
                            val startPage = paginator.chapterStartPageIndices[targetLocator.chapterIndex]
                            if (startPage != null) {
                                Timber.tag("POS_DIAG").w("Restoration: Precise page not found, falling back to chapter start: $startPage")
                                pagerState.scrollToPage(startPage)
                                paginator.onUserScrolledTo(startPage)
                            }
                        }
                        anchorLocatorForReconfig = null
                    }
                } finally {
                    onReconfigurationRestoreActiveChanged(false)
                }
            }
        }

        var isLoading by remember { mutableStateOf(true) }
        var totalPageCount by remember { mutableIntStateOf(0) }
        var generation by remember { mutableIntStateOf(0) }

        LaunchedEffect(paginator) {
            launch { snapshotFlow { paginator.isLoading }.collect {
                Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.isLoading=$it")
                isLoading = it
            } }
            launch {
                snapshotFlow { paginator.totalPageCount }.collect { newTotalPageCount ->
                    Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.totalPageCount=$newTotalPageCount")
                    totalPageCount = newTotalPageCount
                }
            }
            launch { snapshotFlow { paginator.generation }.collect {
                Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.generation=$it")
                generation = it
            } }
        }

        LaunchedEffect(pagerState, paginator) {
            snapshotFlow { pagerState.currentPage }.debounce(500)
                .collectLatest { page ->
                    if (anchorLocatorForReconfig == null) {
                        paginator.onUserScrolledTo(page)
                    }
                }
        }

        LaunchedEffect(paginator, pagerState) {
            paginator.pageShiftRequest.collect { shiftAmount ->
                if (pagerState.pageCount <= 0) {
                    Timber.tag(TAG_STABLE_PAGE_NAV)
                        .w("shift_drop reason=emptyPager shift=$shiftAmount")
                    return@collect
                }

                val bookPaginator = paginator as? BookPaginator
                val currentPageBeforeShift = pagerState.currentPage
                val now = System.currentTimeMillis()
                val externalAgeMs = if (latestExternalNavigationEpoch > 0L) {
                    now - latestExternalNavigationEpoch
                } else {
                    -1L
                }
                val localAgeMs = if (localExplicitNavigationEpoch > 0L) {
                    now - localExplicitNavigationEpoch
                } else {
                    -1L
                }
                val recentExternalNavigation =
                    externalAgeMs in 0L..EXPLICIT_NAVIGATION_SHIFT_ANCHOR_WINDOW_MS
                val recentLocalNavigation =
                    localAgeMs in 0L..EXPLICIT_NAVIGATION_SHIFT_ANCHOR_WINDOW_MS
                val activeExplicitAnchor = when {
                    latestIsExternalNavigationInProgress -> latestExternalNavigationAnchor
                    isNavigatingByLink -> localExplicitNavigationAnchor
                    else -> null
                }
                val recentExplicitAnchor = when {
                    recentExternalNavigation -> latestExternalNavigationAnchor
                    recentLocalNavigation -> localExplicitNavigationAnchor
                    else -> null
                }
                val activeExplicitAnchorSource = when {
                    activeExplicitAnchor == null -> null
                    latestIsExternalNavigationInProgress -> "explicit_external_active"
                    else -> "explicit_link"
                }
                val recentExplicitAnchorSource = when {
                    recentExplicitAnchor == null -> null
                    recentExternalNavigation -> "explicit_external_recent"
                    else -> "explicit_link_recent"
                }
                val currentPageLocator = bookPaginator?.getLocatorForPage(currentPageBeforeShift)
                val fallbackLocator = latestFallbackLocatorForReconfiguration
                var anchorSource = "none"
                val anchor = when {
                    anchorLocatorForReconfig != null -> {
                        anchorSource = "reconfiguration"
                        anchorLocatorForReconfig
                    }
                    activeExplicitAnchor != null -> {
                        anchorSource = activeExplicitAnchorSource ?: "explicit_active"
                        activeExplicitAnchor
                    }
                    fallbackLocator != null -> {
                        anchorSource = "last_known"
                        fallbackLocator
                    }
                    recentExplicitAnchor != null -> {
                        anchorSource = recentExplicitAnchorSource ?: "explicit_recent"
                        recentExplicitAnchor
                    }
                    currentPageLocator != null -> {
                        anchorSource = "current_page"
                        currentPageLocator
                    }
                    else -> null
                }

                Timber.tag(TAG_STABLE_PAGE_NAV).d(
                    "shift_received shift=$shiftAmount currentPage=$currentPageBeforeShift anchorSource=$anchorSource anchor=$anchor currentLocator=$currentPageLocator fallback=$fallbackLocator externalInProgress=$latestIsExternalNavigationInProgress linkInProgress=$isNavigatingByLink externalAgeMs=$externalAgeMs localAgeMs=$localAgeMs"
                )

                val resolvedPage = anchor?.let { locator ->
                    bookPaginator?.findStablePageForLocator(locator)
                }

                if (resolvedPage != null) {
                    Timber.tag(TAG_STABLE_PAGE_NAV).d(
                        "shift_apply_stable shift=$shiftAmount from=$currentPageBeforeShift to=$resolvedPage anchorSource=$anchorSource anchor=$anchor"
                    )
                    pagerState.scrollToPage(resolvedPage)
                    paginator.onUserScrolledTo(resolvedPage)
                } else {
                    val maxPage = (pagerState.pageCount - 1).coerceAtLeast(0)
                    val newPage = (currentPageBeforeShift + shiftAmount).coerceIn(0, maxPage)
                    Timber.tag(TAG_STABLE_PAGE_NAV).w(
                        "shift_apply_relative shift=$shiftAmount from=$currentPageBeforeShift to=$newPage anchorSource=$anchorSource anchor=$anchor"
                    )
                    pagerState.scrollToPage(newPage)
                    paginator.onUserScrolledTo(newPage)
                }
            }
        }

        val uiState = PaginatedReaderUiState(
            isLoading = isLoading, totalPageCount = totalPageCount, generation = generation
        )

        PaginatedReaderContent(
            uiState = uiState,
            pagerState = pagerState,
            isPageTurnAnimationEnabled = isPageTurnAnimationEnabled,
            isRightToLeftPagination = isRightToLeftPagination,
            effectiveBg = effectiveBg,
            searchQuery = searchQuery,
            ttsHighlightInfo = ttsHighlightInfo,
            textStyle = textStyle,
            imageSizeMultiplier = debouncedImageSizeMult,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            onGetPage = { pageIndex ->
                val startTime = System.currentTimeMillis()
                val result = paginator.getPageContent(pageIndex)
                val duration = System.currentTimeMillis() - startTime
                if (DEBUG_PAGE_TURN_DIAG && duration > 16) {
                    Timber.tag("PageTurnDiag")
                        .w("HEAVY TASK: paginator.getPageContent($pageIndex) took ${duration}ms on Thread ${Thread.currentThread().name}")
                }
                result
            },
            onGetChapterIndex = { pageIndex -> paginator.findChapterIndexForPage(pageIndex) },
            onGetChapterPath = { pageIndex -> paginator.getChapterPathForPage(pageIndex) },
            onGetChapterInfo = { pageIndex ->
                paginator.findChapterIndexForPage(pageIndex)?.let { chapterIndex ->
                    val chapter = book.chaptersForPagination.getOrNull(chapterIndex)
                    val estimatedPages = paginator.chapterPageCounts[chapterIndex]
                    if (chapter != null) {
                        Pair(chapter.title, estimatedPages)
                    } else {
                        null
                    }
                }
            },
            onInternalLinkNavigated = onInternalLinkNavigated,
            onLinkClick = { currentChapterPath, href, onNavComplete ->
                coroutineScope.launch(Dispatchers.IO) {
                    Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                        "nav_request currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                            "href=${href.readerLinkDiagPreview()}"
                    )
                    withContext(Dispatchers.Main) { isNavigatingByLink = true }
                    try {
                        var isFootnote = false
                        var footnoteHtml: String? = null

                        val sourceChapter =
                            book.chaptersForPagination.find { it.absPath == currentChapterPath }
                        if (sourceChapter == null) {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).w(
                                "nav_source_chapter_miss currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                                    "href=${href.readerLinkDiagPreview()}"
                            )
                        }
                        if (sourceChapter != null) {
                            val sourceHtml = sourceChapter.htmlContent.ifEmpty {
                                try {
                                    File(book.extractionBasePath, sourceChapter.htmlFilePath)
                                        .readText()
                                } catch (_: Exception) {
                                    ""
                                }
                            }
                            if (sourceHtml.isNotEmpty()) {
                                val doc = Jsoup.parse(sourceHtml)
                                val safeHref = href.replace("\"", "\\\"")
                                val aTag = doc.select("a[href=\"$safeHref\"]").first()

                                val linkType = aTag?.attr("epub:type").orEmpty()
                                val linkRole = aTag?.attr("role").orEmpty()
                                if (
                                    linkType.contains("noteref", ignoreCase = true) ||
                                    linkRole.contains("doc-noteref", ignoreCase = true)
                                ) {
                                    isFootnote = true
                                }
                            }
                        }

                        run {
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
                                        URI(currentChapterPath).resolve(pathPart)
                                            .normalize().path
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
                                        val targetHtml = targetChapter.htmlContent.ifEmpty {
                                            try {
                                                File(
                                                    book.extractionBasePath,
                                                    targetChapter.htmlFilePath
                                                ).readText()
                                            } catch (_: Exception) {
                                                ""
                                            }
                                        }
                                        if (targetHtml.isNotEmpty()) {
                                            val doc = Jsoup.parse(targetHtml)
                                            val noteEl = doc.getElementById(anchor)
                                            if (noteEl != null) {
                                                val targetType = noteEl.attr("epub:type")
                                                val targetRole = noteEl.attr("role")
                                                val targetClass = noteEl.className()
                                                val targetLooksLikeFootnote =
                                                    targetType.contains("footnote", ignoreCase = true) ||
                                                        targetRole.contains("doc-footnote", ignoreCase = true) ||
                                                        targetClass.contains("footnote", ignoreCase = true)
                                                if (isFootnote || targetLooksLikeFootnote) {
                                                    footnoteHtml = noteEl.html()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!footnoteHtml.isNullOrBlank()) {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "nav_footnote_open href=${href.readerLinkDiagPreview()} htmlChars=${footnoteHtml?.length ?: 0}"
                            )
                            withContext(Dispatchers.Main) { onFootnoteRequested(footnoteHtml) }
                        } else {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "nav_resolve_start currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                                    "href=${href.readerLinkDiagPreview()}"
                            )
                            val targetPage = (paginator as? BookPaginator)?.findStablePageForHref(currentChapterPath, href)
                            withContext(Dispatchers.Main) {
                                if (targetPage != null) {
                                    val targetAnchor = (paginator as? BookPaginator)?.getLocatorForPage(targetPage)
                                    val navigationEpoch = System.currentTimeMillis()
                                    localExplicitNavigationAnchor = targetAnchor
                                    localExplicitNavigationEpoch = navigationEpoch
                                    Timber.tag(TAG_STABLE_PAGE_NAV).d(
                                        "link_resolved href=$href targetPage=$targetPage anchor=$targetAnchor epoch=$navigationEpoch"
                                    )
                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                        "nav_resolve_success href=${href.readerLinkDiagPreview()} targetPage=$targetPage " +
                                            "targetAnchor=$targetAnchor"
                                    )
                                    paginator.onUserScrolledTo(targetPage)
                                    onNavComplete(targetPage)
                                } else {
                                    Timber.tag(TAG_STABLE_PAGE_NAV).w(
                                        "link_failed href=$href currentChapterPath=$currentChapterPath"
                                    )
                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).w(
                                        "nav_resolve_failed currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                                            "href=${href.readerLinkDiagPreview()}"
                                    )
                                }
                            }
                        }
                    } finally {
                        withContext(Dispatchers.Main) { isNavigatingByLink = false }
                    }
                }
            },
            onTap = onTap,
            isProUser = isProUser,
            isOss = isOss,
            onShowDictionaryUpsellDialog = onShowDictionaryUpsellDialog,
            onWordSelectedForAiDefinition = onWordSelectedForAiDefinition,
            onTranslate = onTranslate,
            onSearch = onSearch,
            onStartTtsFromSelection = onStartTtsFromSelection,
            onNoteRequested = onNoteRequested,
            userHighlights = userHighlights,
            onHighlightCreated = onHighlightCreated,
            onHighlightDeleted = onHighlightDeleted,
            isDarkTheme = isDarkTheme,
            activeHighlightPalette = activeHighlightPalette,
            onUpdatePalette = onUpdatePalette,
            effectiveText = effectiveText,
            pageTextureModifier = if (isPageTurnAnimationEnabled) Modifier else textureModifier,
            pageTextureBitmap = textureBitmap,
            pageTextureAlpha = activeTextureAlpha.coerceIn(0f, 1f)
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = isNavigatingByLink,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Navigating...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalSerializationApi::class, FlowPreview::class)
@Composable
fun NativeVerticalReaderScreen(
    modifier: Modifier = Modifier,
    book: EpubBook,
    bookId: String? = null,
    isDarkTheme: Boolean,
    effectiveBg: Color,
    effectiveText: Color,
    searchQuery: String,
    fontSizeMultiplier: Float,
    lineHeightMultiplier: Float,
    paragraphGapMultiplier: Float,
    imageSizeMultiplier: Float,
    horizontalMarginMultiplier: Float,
    verticalMarginMultiplier: Float,
    fontFamily: FontFamily,
    textAlign: ReaderTextAlign,
    bookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences(),
    bookReplacementFileId: String? = bookId,
    ttsHighlightInfo: TtsHighlightInfo?,
    initialLocator: Locator? = null,
    initialPageIndexInBook: Int = 0,
    scrollRequestPage: Int? = null,
    scrollRequestLocator: Locator? = null,
    scrollRequestLocatorId: Long = 0L,
    scrollRequestLocatorKeepVisible: Boolean = false,
    scrollRequestProgressPercent: Float? = null,
    scrollRequestProgressId: Long = 0L,
    scrollDeltaRequest: Float? = null,
    scrollDeltaRequestId: Long = 0L,
    scrollDeltaRequestAnimated: Boolean = true,
    onScrollRequestConsumed: () -> Unit = {},
    onScrollLocatorRequestConsumed: () -> Unit = {},
    onScrollProgressRequestConsumed: () -> Unit = {},
    onScrollDeltaConsumed: () -> Unit = {},
    onPaginatorReady: (IPaginator) -> Unit,
    onVisiblePageChanged: (pageIndex: Int, chapterIndex: Int?, locator: Locator?) -> Unit = { _, _, _ -> },
    onProgressChanged: (pageIndex: Int, totalPages: Int, progressPercent: Float) -> Unit = { _, _, _ -> },
    onLocationChanged: (NativeVerticalLocation) -> Unit = {},
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int, Int?) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onFootnoteRequested: (String) -> Unit = {},
    onInternalLinkNavigated: (Int, Locator?) -> Unit = { _, _ -> },
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String, SharedReaderLocator) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit,
    activeTextureId: String? = null,
    activeTextureAlpha: Float = 0.55f
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val textureBitmap = remember(activeTextureId) {
        loadReaderTextureBitmap(context, activeTextureId)
    }
    val textureModifier = if (textureBitmap != null) {
        Modifier.drawBehind {
            val brush = ShaderBrush(
                ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)
            )
            drawRect(brush = brush, blendMode = BlendMode.SrcOver, alpha = activeTextureAlpha.coerceIn(0f, 1f))
        }
    } else {
        Modifier
    }
    val bookReplacementSignature = remember(bookReplacementPreferences, bookReplacementFileId) {
        bookReplacementPreferences.signatureForFile(bookReplacementFileId)
    }
    var rootWindowBounds by remember { mutableStateOf(Rect.Zero) }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(effectiveBg)
            .then(textureModifier)
            .onGloballyPositioned { coords ->
                rootWindowBounds = Rect(coords.positionInWindow(), coords.size.toSize())
            }
            .testTag("NativeVerticalReader")
    ) {
        val textMeasurer = rememberTextMeasurer()
        val baseTextStyle = MaterialTheme.typography.bodyLarge
        val density = LocalDensity.current
        val layoutTextStyle = remember(
            baseTextStyle,
            fontSizeMultiplier,
            lineHeightMultiplier,
            fontFamily
        ) {
            val adjustedFontSize = baseTextStyle.fontSize * fontSizeMultiplier
            val adjustedLineHeight =
                adjustedFontSize * paginationLineHeightMultiplierForWebViewSetting(lineHeightMultiplier)

            baseTextStyle.copy(
                color = Color.Unspecified,
                fontSize = adjustedFontSize,
                lineHeight = adjustedLineHeight,
                fontFamily = fontFamily,
                lineBreak = LineBreak.Paragraph,
                letterSpacing = TextUnit.Unspecified,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.None
                )
            )
        }
        val textStyle = remember(layoutTextStyle, effectiveText) {
            layoutTextStyle.copy(color = effectiveText)
        }
        val userTextAlign = remember(textAlign) {
            when (textAlign) {
                ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                ReaderTextAlign.LEFT -> TextAlign.Left
                ReaderTextAlign.RIGHT -> TextAlign.Right
                ReaderTextAlign.DEFAULT -> null
            }
        }
        val requestedHorizontalPadding = 16.dp * horizontalMarginMultiplier
        val requestedVerticalPadding = 16.dp * verticalMarginMultiplier
        val effectiveReaderPadding =
            remember(this.constraints, density, requestedHorizontalPadding, requestedVerticalPadding) {
                val requestedHorizontalPaddingPx = with(density) { requestedHorizontalPadding.roundToPx() }
                val requestedVerticalPaddingPx = with(density) { requestedVerticalPadding.roundToPx() }
                val minReadableWidthPx = with(density) { 96.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxWidth)
                val minReadableHeightPx = with(density) { 160.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxHeight)
                val horizontalPaddingPx = requestedHorizontalPaddingPx.coerceAtMost(
                    ((this.constraints.maxWidth - minReadableWidthPx) / 2).coerceAtLeast(0)
                )
                val verticalPaddingPx = requestedVerticalPaddingPx.coerceAtMost(
                    ((this.constraints.maxHeight - minReadableHeightPx) / 2).coerceAtLeast(0)
                )
                with(density) {
                    horizontalPaddingPx.toDp() to verticalPaddingPx.toDp()
                }
        }
        val horizontalPadding = effectiveReaderPadding.first
        val verticalPadding = effectiveReaderPadding.second
        val textConstraints =
            remember(this.constraints, density, horizontalPadding, verticalPadding) {
                val horizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
                val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
                this.constraints.copy(
                    minWidth = 0,
                    maxWidth = (this.constraints.maxWidth - (2 * horizontalPaddingPx)).coerceAtLeast(1),
                    minHeight = 0,
                    maxHeight = (this.constraints.maxHeight - (2 * verticalPaddingPx)).coerceAtLeast(1)
                )
            }

        val mathMLRenderer = remember { MathMLRenderer(context.applicationContext) }
        DisposableEffect(Unit) {
            onDispose {
                mathMLRenderer.destroy()
                Timber.d("NativeVerticalReaderScreen disposed, MathMLRenderer destroyed.")
            }
        }

        val paginator = remember(
            book,
            bookId,
            textConstraints,
            layoutTextStyle,
            userTextAlign,
            paragraphGapMultiplier,
            imageSizeMultiplier,
            verticalMarginMultiplier,
            bookReplacementSignature,
            bookReplacementFileId
        ) {
            val userAgentStylesheet = UserAgentStylesheet.default
            var allRules = OptimizedCssRules()
            val allFontFaces = mutableListOf<FontFaceInfo>()

            val uaResult = CssParser.parse(
                cssContent = userAgentStylesheet,
                cssPath = null,
                baseFontSizeSp = layoutTextStyle.fontSize.value,
                density = density.density,
                constraints = textConstraints,
                isDarkTheme = false,
                adaptThemeColors = false
            )
            allRules = allRules.merge(uaResult.rules)
            allFontFaces.addAll(uaResult.fontFaces)

            book.css.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = layoutTextStyle.fontSize.value,
                    density = density.density,
                    constraints = textConstraints,
                    isDarkTheme = false,
                    adaptThemeColors = false
                )
                allRules = allRules.merge(bookCssResult.rules)
                allFontFaces.addAll(bookCssResult.fontFaces)
            }

            val fontFamilyMap = loadFontFamilies(
                fontFaces = allFontFaces,
                extractionPath = book.extractionBasePath
            )
            val bookCacheDao =
                BookCacheDatabase.getDatabase(context.applicationContext).bookCacheDao()
            val proto = ProtoBuf { serializersModule = semanticBlockModule }
            val uniqueBookId = bookId ?: if (book.fileName.length > 20) book.fileName else book.title
            val initialChapter = initialLocator?.chapterIndex ?: 0

            Timber.tag("NativeVerticalReader").d(
                "Instantiating BookPaginator for native vertical. initialChapter=$initialChapter"
            )
            BookPaginator(
                coroutineScope = coroutineScope,
                chapters = book.chaptersForPagination,
                textMeasurer = textMeasurer,
                constraints = textConstraints,
                textStyle = layoutTextStyle,
                extractionBasePath = book.extractionBasePath,
                density = density,
                fontFamilyMap = fontFamilyMap,
                isDarkTheme = isDarkTheme,
                themeBackgroundColor = effectiveBg,
                themeTextColor = effectiveText,
                bookId = uniqueBookId,
                bookCacheDao = bookCacheDao,
                proto = proto,
                initialChapterToPaginate = initialChapter,
                bookCss = book.css,
                userAgentStylesheet = userAgentStylesheet,
                allFontFaces = allFontFaces,
                context = context.applicationContext,
                mathMLRenderer = mathMLRenderer,
                userTextAlign = userTextAlign,
                paragraphGapMultiplier = paragraphGapMultiplier,
                imageSizeMultiplier = imageSizeMultiplier,
                verticalMarginMultiplier = verticalMarginMultiplier,
                bookReplacementPreferences = bookReplacementPreferences,
                bookReplacementFileId = bookReplacementFileId
            )
        }

        LaunchedEffect(paginator) {
            onPaginatorReady(paginator)
        }

        DisposableEffect(paginator) {
            onDispose {
                paginator.dispose()
            }
        }

        var isLoading by remember { mutableStateOf(true) }
        var totalPageCount by remember { mutableIntStateOf(0) }
        var generation by remember { mutableIntStateOf(0) }

        LaunchedEffect(paginator) {
            launch {
                snapshotFlow { paginator.isLoading }.collect { isLoading = it }
            }
            launch {
                snapshotFlow { paginator.totalPageCount }.collect { totalPageCount = it }
            }
            launch {
                snapshotFlow { paginator.generation }.collect { generation = it }
            }
        }

        val listState = rememberLazyListState()
        val blockLayoutMap = remember(paginator) { ReactiveBlockMap() }
        val chapterLayoutMap = remember(paginator) { mutableStateMapOf<Int, LayoutCoordinates>() }
        val flowItemLayoutMap = remember(paginator) { mutableStateMapOf<String, LayoutCoordinates>() }
        var flowChapters by remember(paginator) { mutableStateOf<List<NativeVerticalFlowChapter>?>(null) }
        val flowItems = remember(flowChapters) { buildNativeVerticalFlowItems(flowChapters.orEmpty()) }
        var isFlowLoading by remember(paginator) { mutableStateOf(true) }
        val initialNativeLocator = remember(paginator) { initialLocator }
        val initialNativePageIndex = remember(paginator) { initialPageIndexInBook }
        var didInitialScroll by remember(paginator) { mutableStateOf(false) }
        val placeholderFlowChapters = remember(book) {
            book.chaptersForPagination.mapIndexed { chapterIndex, chapter ->
                NativeVerticalFlowChapter(
                    chapterIndex = chapterIndex,
                    title = chapter.title,
                    blocks = emptyList(),
                    isLoaded = false,
                    estimatedLocationWeight = chapter.plainTextCharacterCount().coerceAtLeast(24)
                )
            }
        }
        val flowChapterLoadsInFlight = remember(paginator) { mutableStateMapOf<Int, Boolean>() }

        fun ensurePlaceholderFlowChapters() {
            val current = flowChapters
            if (current == null || current.size != placeholderFlowChapters.size) {
                flowChapters = placeholderFlowChapters
            }
        }

        suspend fun loadFlowChapter(chapterIndex: Int): Boolean {
            if (chapterIndex !in placeholderFlowChapters.indices) return false
            flowChapters?.getOrNull(chapterIndex)?.takeIf { it.isLoaded }?.let { return true }
            while (flowChapterLoadsInFlight[chapterIndex] == true) {
                delay(16L)
                flowChapters?.getOrNull(chapterIndex)?.takeIf { it.isLoaded }?.let { return true }
            }

            flowChapterLoadsInFlight[chapterIndex] = true
            return try {
                val chapter = book.chaptersForPagination.getOrNull(chapterIndex) ?: return false
                val blocks = try {
                    paginator.getFlowBlocksForChapter(chapterIndex).orEmpty()
                } catch (e: Exception) {
                    Timber.e(e, "Native vertical flow failed to load chapter $chapterIndex")
                    emptyList()
                }
                val current = flowChapters ?: placeholderFlowChapters
                val updated = current.toMutableList()
                updated[chapterIndex] = NativeVerticalFlowChapter(
                    chapterIndex = chapterIndex,
                    title = chapter.title,
                    blocks = blocks,
                    isLoaded = true,
                    estimatedLocationWeight = chapter.plainTextCharacterCount().coerceAtLeast(24)
                )
                flowChapters = updated
                true
            } finally {
                flowChapterLoadsInFlight.remove(chapterIndex)
            }
        }

        @Suppress("UNUSED_PARAMETER")
        suspend fun scrollToFlowLocator(
            locator: Locator?,
            animate: Boolean,
            keepVisible: Boolean = false
        ): Boolean {
            if (locator == null) return false
            ensurePlaceholderFlowChapters()
            if (flowChapters?.getOrNull(locator.chapterIndex)?.isLoaded != true) {
                loadFlowChapter(locator.chapterIndex)
                withFrameNanos { }
            }
            val chapters = flowChapters ?: return false
            val currentFlowItems = buildNativeVerticalFlowItems(chapters)
            val exactDelta = resolveNativeVerticalScrollDeltaForLocator(
                rootWindowBounds = rootWindowBounds,
                chapterLayoutMap = chapterLayoutMap,
                flowItems = currentFlowItems,
                flowItemLayoutMap = flowItemLayoutMap,
                blockLayoutMap = blockLayoutMap,
                chapters = chapters,
                locator = locator,
                allowChapterFallback = false
            )
            if (exactDelta != null) {
                val scrollDelta = if (keepVisible) {
                    val viewportHeight = rootWindowBounds.height
                    val comfortableTop = viewportHeight * 0.24f
                    val comfortableBottom = viewportHeight * 0.76f
                    if (exactDelta in comfortableTop..comfortableBottom) {
                        0f
                    } else {
                        exactDelta - (viewportHeight * 0.38f)
                    }
                } else {
                    exactDelta
                }
                if (abs(scrollDelta) > 1f) {
                    listState.scrollBy(scrollDelta)
                }
                if (keepVisible || abs(exactDelta) > 1f) return true
            }

            val targetIndex = findNativeVerticalFlowItemIndexForLocator(
                items = currentFlowItems,
                chapters = chapters,
                locator = locator
            ) ?: return false
            listState.scrollToItem(targetIndex)
            repeat(4) {
                withFrameNanos { }
                val refinedDelta = resolveNativeVerticalScrollDeltaForLocator(
                    rootWindowBounds = rootWindowBounds,
                    chapterLayoutMap = chapterLayoutMap,
                    flowItems = currentFlowItems,
                    flowItemLayoutMap = flowItemLayoutMap,
                    blockLayoutMap = blockLayoutMap,
                    chapters = chapters,
                    locator = locator,
                    allowChapterFallback = false
                )
                if (refinedDelta != null) {
                    val scrollDelta = if (keepVisible) {
                        val viewportHeight = rootWindowBounds.height
                        refinedDelta - (viewportHeight * 0.38f)
                    } else {
                        refinedDelta
                    }
                    if (abs(scrollDelta) > 1f) {
                        listState.scrollBy(scrollDelta)
                    }
                    return true
                }
            }
            return true
        }

        suspend fun scrollToCompatPage(pageIndex: Int, animate: Boolean): Boolean {
            val targetPage = pageIndex.coerceIn(0, (totalPageCount - 1).coerceAtLeast(0))
            val locator = paginator.getLocatorForPage(targetPage)
                ?: paginator.findChapterIndexForPage(targetPage)?.let { Locator(it, 0, 0) }
                ?: return false
            val didScroll = scrollToFlowLocator(locator, animate)
            if (didScroll) paginator.onUserScrolledTo(targetPage)
            return didScroll
        }

        suspend fun scrollToProgressPercent(progressPercent: Float): Boolean {
            if (flowItems.isEmpty()) return false
            val targetIndex = findNativeVerticalFlowItemIndexForProgress(
                items = flowItems,
                progressPercent = progressPercent
            ) ?: return false
            listState.scrollToItem(targetIndex)
            paginator.onUserScrolledTo(
                nativeVerticalCompatPageForProgress(progressPercent, totalPageCount)
            )
            return true
        }

        LaunchedEffect(paginator) {
            snapshotFlow { paginator.isLoading }.filter { !it }.first()
            isFlowLoading = true
            if (placeholderFlowChapters.isEmpty()) {
                flowChapters = emptyList()
                isFlowLoading = false
                return@LaunchedEffect
            }

            flowChapters = placeholderFlowChapters
            val initialChapter = (
                initialNativeLocator?.chapterIndex
                    ?: paginator.findChapterIndexForPage(initialNativePageIndex)
                    ?: 0
                ).coerceIn(0, placeholderFlowChapters.lastIndex)
            val prefetchOrder = nativeVerticalInitialChapterPrefetchOrder(
                chapterCount = placeholderFlowChapters.size,
                initialChapter = initialChapter
            )

            loadFlowChapter(initialChapter)
            isFlowLoading = false

            prefetchOrder.forEach { chapterIndex ->
                if (!isActive) return@LaunchedEffect
                loadFlowChapter(chapterIndex)
                delay(16L)
            }
        }

        LaunchedEffect(flowChapters, totalPageCount, rootWindowBounds) {
            if (didInitialScroll || flowChapters == null || rootWindowBounds == Rect.Zero) return@LaunchedEffect
            val targetLocator = initialNativeLocator ?: paginator.getLocatorForPage(initialNativePageIndex)
            if (targetLocator == null) {
                didInitialScroll = true
                return@LaunchedEffect
            }
            val didScroll = scrollToFlowLocator(targetLocator, animate = false) ||
                scrollToCompatPage(initialNativePageIndex, animate = false)
            if (didScroll) {
                didInitialScroll = true
            }
        }

        LaunchedEffect(scrollRequestPage, totalPageCount, flowChapters, rootWindowBounds) {
            val requestedPage = scrollRequestPage ?: return@LaunchedEffect
            if (totalPageCount <= 0 || flowChapters == null || rootWindowBounds == Rect.Zero) return@LaunchedEffect
            if (scrollToCompatPage(requestedPage, animate = true)) {
                onScrollRequestConsumed()
            }
        }

        LaunchedEffect(scrollRequestLocatorId, scrollRequestLocator, scrollRequestLocatorKeepVisible, flowChapters, rootWindowBounds) {
            val requestedLocator = scrollRequestLocator ?: return@LaunchedEffect
            if (flowChapters == null || rootWindowBounds == Rect.Zero) return@LaunchedEffect
            if (scrollToFlowLocator(requestedLocator, animate = false, keepVisible = scrollRequestLocatorKeepVisible)) {
                paginator.onUserScrolledTo(
                    nativeVerticalCompatPageForProgress(
                        estimateNativeVerticalProgressPercent(book, requestedLocator) ?: 0f,
                        totalPageCount
                    )
                )
                onScrollLocatorRequestConsumed()
            }
        }

        LaunchedEffect(scrollRequestProgressId, scrollRequestProgressPercent, flowChapters) {
            val requestedProgress = scrollRequestProgressPercent ?: return@LaunchedEffect
            if (flowChapters == null) return@LaunchedEffect
            if (scrollToProgressPercent(requestedProgress)) {
                onScrollProgressRequestConsumed()
            }
        }

        LaunchedEffect(scrollDeltaRequestId, scrollDeltaRequest, scrollDeltaRequestAnimated) {
            val delta = scrollDeltaRequest ?: return@LaunchedEffect
            if (delta != 0f) {
                if (scrollDeltaRequestAnimated) {
                    listState.animateScrollBy(delta)
                } else {
                    listState.scrollBy(delta)
                }
            }
            onScrollDeltaConsumed()
        }

        var lastReportedVisiblePage by remember { mutableIntStateOf(-1) }
        var lastReportedTotalPageCount by remember { mutableIntStateOf(0) }
        var lastReportedProgressPercent by remember { mutableFloatStateOf(-1f) }
        var lastReportedLocator by remember { mutableStateOf<Locator?>(null) }
        var lastReportedVisibleTextRanges by remember { mutableStateOf<List<NativeVerticalVisibleTextRange>>(emptyList()) }

        LaunchedEffect(paginator, totalPageCount, rootWindowBounds, blockLayoutMap, flowChapters, flowItems) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val firstVisibleItemSize = visibleItems
                    .firstOrNull { it.index == listState.firstVisibleItemIndex }
                    ?.size
                    ?: 0
                val lastVisibleItem = visibleItems.lastOrNull()
                val isAtEnd = layoutInfo.totalItemsCount > 0 &&
                    lastVisibleItem?.index == layoutInfo.totalItemsCount - 1 &&
                    lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
                NativeVerticalViewportSample(
                    firstVisiblePageIndex = listState.firstVisibleItemIndex,
                    firstVisiblePageScrollOffset = listState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = firstVisibleItemSize,
                    isAtStart = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
                    isAtEnd = isAtEnd,
                    totalPageCount = totalPageCount.takeIf { it > 0 } ?: (flowChapters?.size ?: 0),
                    layoutTick = blockLayoutMap.tick,
                    initialScrollComplete = didInitialScroll
                )
            }
                .debounce(80)
                .collectLatest { sample ->
                    if (!sample.initialScrollComplete) return@collectLatest
                    val total = sample.totalPageCount
                    if (total <= 0) return@collectLatest
                    blockLayoutMap.pruneDetached()
                    val locator = resolveNativeVerticalFlowVisibleLocator(
                        rootWindowBounds = rootWindowBounds,
                        blockLayoutMap = blockLayoutMap
                    ) ?: flowItems.getOrNull(sample.firstVisiblePageIndex)
                        ?.let { locatorForNativeVerticalFlowItem(it) }
                    val visibleTextRanges = resolveNativeVerticalVisibleTextRanges(
                        rootWindowBounds = rootWindowBounds,
                        blockLayoutMap = blockLayoutMap
                    )
                    val progressPercent = when {
                        sample.isAtEnd -> 100f
                        sample.isAtStart -> 0f
                        else -> estimateNativeVerticalScrollProgressPercent(
                            items = flowItems,
                            firstVisibleItemIndex = sample.firstVisiblePageIndex,
                            firstVisibleItemScrollOffset = sample.firstVisiblePageScrollOffset,
                            firstVisibleItemSize = sample.firstVisibleItemSize
                        ) ?: estimateNativeVerticalProgressPercent(
                            book = book,
                            locator = locator
                        ) ?: 0f
                    }
                    val compatPage = nativeVerticalCompatPageForProgress(progressPercent, total)
                    paginator.onUserScrolledTo(compatPage)

                    if (
                        compatPage != lastReportedVisiblePage ||
                        total != lastReportedTotalPageCount ||
                        abs(progressPercent - lastReportedProgressPercent) >= 0.05f ||
                        locator != lastReportedLocator ||
                        visibleTextRanges != lastReportedVisibleTextRanges
                    ) {
                        lastReportedVisiblePage = compatPage
                        lastReportedTotalPageCount = total
                        lastReportedProgressPercent = progressPercent
                        lastReportedLocator = locator
                        lastReportedVisibleTextRanges = visibleTextRanges
                        onLocationChanged(
                            NativeVerticalLocation(
                                locator = locator,
                                chapterIndex = locator?.chapterIndex,
                                progressPercent = progressPercent,
                                compatPageIndex = compatPage,
                                compatTotalPages = total,
                                firstVisibleItemIndex = sample.firstVisiblePageIndex,
                                firstVisibleItemScrollOffset = sample.firstVisiblePageScrollOffset,
                                firstVisibleItemSize = sample.firstVisibleItemSize,
                                isAtStart = sample.isAtStart,
                                isAtEnd = sample.isAtEnd,
                                visibleTextRanges = visibleTextRanges
                            )
                        )
                        onProgressChanged(compatPage, total, progressPercent)
                        onVisiblePageChanged(compatPage, locator?.chapterIndex, locator)
                    }
                }
        }

        val searchHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        val ttsHighlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        var activeSelection by remember { mutableStateOf<PaginatedSelection?>(null) }
        var isDraggingHandle by remember { mutableStateOf(false) }
        var selectionEdgeScrollDelta by remember { mutableFloatStateOf(0f) }
        var selectionEdgeDragWindowPos by remember { mutableStateOf(Offset.Unspecified) }
        var selectionEdgeDragHandle by remember { mutableStateOf<SelectionHandle?>(null) }
        val activeDragHandleForDisplay = selectionEdgeDragHandle
        var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }
        val magnifierModifier = if (magnifierCenter.isSpecified) {
            Modifier.magnifier(
                sourceCenter = { magnifierCenter },
                zoom = 1.5f,
                size = DpSize(140.dp, 48.dp),
                cornerRadius = 24.dp,
                elevation = 4.dp
            )
        } else {
            Modifier
        }
        var showPaletteManager by remember { mutableStateOf(false) }
        var showExternalLinkDialog by remember { mutableStateOf<String?>(null) }
        val imageLoader = context.imageLoader

        showExternalLinkDialog?.let { urlToShow ->
            AlertDialog(
                onDismissRequest = { showExternalLinkDialog = null },
                title = { Text(stringResource(R.string.dialog_external_link_title)) },
                text = { Text(urlToShow) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, urlToShow.toUri())
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Timber.e(e, "No activity found to handle intent for URL: $urlToShow")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_no_browser),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            showExternalLinkDialog = null
                        }
                    ) { Text(stringResource(R.string.action_open)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val clipboardManager =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(context.getString(R.string.clip_label_copied_text), urlToShow)
                        )
                        showExternalLinkDialog = null
                    }) { Text(stringResource(R.string.action_copy)) }
                }
            )
        }

        val renderedFlowChapters = flowChapters

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoords = it }
                .then(magnifierModifier)
        ) {
            if (isFlowLoading || renderedFlowChapters == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (renderedFlowChapters.isNotEmpty()) {
                generation
                val chapterBoundaryGap = 44.dp * verticalMarginMultiplier.coerceIn(0.75f, 2.5f)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding)
                ) {
                    itemsIndexed(
                        items = flowItems,
                        key = { _, item -> item.key }
                    ) { _, item ->
                        val chapterIndex = item.chapterIndex
                        val block = item.block
                        val onGeneralTapCallback: (Offset) -> Unit = { offset ->
                            activeSelection = null
                            onTap(offset)
                        }
                        val onLinkClickCallback: (String) -> Unit = { href ->
                            if (href.isReaderExternalHref()) {
                                showExternalLinkDialog = href.readerExternalHrefForDisplay()
                            } else {
                                val chapterPath = book.chaptersForPagination.getOrNull(chapterIndex)?.absPath
                                coroutineScope.launch {
                                    val footnoteHtml = withContext(Dispatchers.IO) {
                                        resolveReaderFootnoteHtml(book, chapterPath.orEmpty(), href)
                                    }
                                    if (!footnoteHtml.isNullOrBlank()) {
                                        onFootnoteRequested(footnoteHtml)
                                        return@launch
                                    }
                                    val targetLocator = paginator.findStableLocatorForHref(chapterPath.orEmpty(), href)
                                    val targetPage = targetLocator?.let { paginator.findStablePageForLocator(it) }
                                        ?: paginator.findStablePageForHref(chapterPath.orEmpty(), href)
                                    if (targetPage != null) {
                                        if (targetLocator != null) {
                                            scrollToFlowLocator(targetLocator, animate = false)
                                            paginator.onUserScrolledTo(targetPage)
                                        } else {
                                            scrollToCompatPage(targetPage, animate = true)
                                        }
                                        onInternalLinkNavigated(targetPage, targetLocator)
                                    } else {
                                        Timber.tag(TAG_PAGINATED_LINK_DIAG)
                                            .w("Native vertical link failed href=$href currentChapterPath=$chapterPath")
                                    }
                                }
                            }
                        }

                        if (block == null) {
                            if (item.kind == NativeVerticalFlowItemKind.UNLOADED_CHAPTER) {
                                LaunchedEffect(chapterIndex, item.kind) {
                                    loadFlowChapter(chapterIndex)
                                }
                            }
                            val spacerHeight = when (item.kind) {
                                NativeVerticalFlowItemKind.CHAPTER_GAP -> chapterBoundaryGap
                                NativeVerticalFlowItemKind.UNLOADED_CHAPTER -> 72.dp
                                else -> 24.dp
                            }
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(spacerHeight)
                                    .onGloballyPositioned { coords ->
                                        flowItemLayoutMap[item.key] = coords
                                        chapterLayoutMap[chapterIndex] = coords
                                    }
                            )
                        } else {
                            val displayBlock = remember(block, isDarkTheme, effectiveBg, effectiveText) {
                                Page(listOf(block)).applyReaderThemeForDisplay(
                                    isDarkTheme = isDarkTheme,
                                    themeBackgroundColor = effectiveBg,
                                    themeTextColor = effectiveText
                                ).content.first()
                            }
                            val pageUserHighlights = highlightsForPaginatedPage(
                                pageChapterIndex = chapterIndex,
                                userHighlights = userHighlights
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                                    .background(effectiveBg)
                                    .onGloballyPositioned { coords ->
                                        flowItemLayoutMap[item.key] = coords
                                        if (item.blockOrdinal <= 0) {
                                            chapterLayoutMap[chapterIndex] = coords
                                        }
                                    }
                                    .pointerInput(chapterIndex, item.blockOrdinal) {
                                        detectTapGestures(onTap = { offset -> onTap(offset) })
                                    }
                            ) {
                                NativeVerticalContentBlock(
                                    block = displayBlock,
                                    pageIndex = chapterIndex,
                                    textStyle = textStyle,
                                    imageSizeMultiplier = imageSizeMultiplier,
                                    searchQuery = searchQuery,
                                    searchHighlightColor = searchHighlightColor,
                                    ttsHighlightInfo = ttsHighlightInfo,
                                    ttsHighlightColor = ttsHighlightColor,
                                    textMeasurer = textMeasurer,
                                    onLinkClickCallback = onLinkClickCallback,
                                    onGeneralTapCallback = onGeneralTapCallback,
                                    userHighlights = pageUserHighlights,
                                    activeSelection = activeSelection,
                                    onSelectionChange = { activeSelection = it },
                                    onHighlightClick = { highlight, _ ->
                                        onNoteRequested(highlight.cfi)
                                        activeSelection = null
                                    },
                                    isDarkTheme = isDarkTheme,
                                    themeBackgroundColor = effectiveBg,
                                    themeTextColor = effectiveText,
                                    blockLayoutMap = blockLayoutMap,
                                    density = density,
                                    imageLoader = imageLoader,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (activeSelection != null) {
                val sel = activeSelection!!
                @Suppress("UNUSED_VARIABLE") val selectionLayoutTick = blockLayoutMap.tick
                val selectedBlocks = visibleSelectedBlocks(blockLayoutMap, sel)

                if (!isDraggingHandle && selectedBlocks.isNotEmpty()) {
                    val handleSizePx = with(density) { 36.dp.toPx() }
                    val menuAnchorRect = selectionWindowBounds(sel, selectedBlocks, handleSizePx)
                    Popup(
                        popupPositionProvider = remember(menuAnchorRect, density) {
                            SmartPopupPositionProvider(menuAnchorRect, density)
                        },
                        onDismissRequest = { activeSelection = null },
                        properties = PopupProperties(dismissOnClickOutside = false)
                    ) {
                        PaginatedTextSelectionMenu(
                            onCopy = {
                                val clipboardManager =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboardManager.setPrimaryClip(
                                    ClipData.newPlainText(context.getString(R.string.clip_label_copied_text), sel.text)
                                )
                                activeSelection = null
                            },
                            onSelectAll = null,
                            onDictionary = {
                                if (isProUser || countWords(sel.text) <= 1) {
                                    onWordSelectedForAiDefinition(sel.text)
                                } else {
                                    onShowDictionaryUpsellDialog()
                                }
                                activeSelection = null
                            },
                            onTranslate = {
                                onTranslate(sel.text)
                                activeSelection = null
                            },
                            onSearch = {
                                onSearch(sel.text)
                                activeSelection = null
                            },
                            onHighlight = { color ->
                                val startAbsoluteOffset = sel.startBlockCharOffset + sel.startOffset
                                val endAbsoluteOffset = sel.endBlockCharOffset + sel.endOffset
                                val finalCfi =
                                    "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                val absoluteCandidateCfi =
                                    "${sel.startBaseCfi}:$startAbsoluteOffset|${sel.endBaseCfi}:$endAbsoluteOffset"
                                val locator = sel.toSharedHighlightLocator(
                                    chapterIndex = sel.startPageIndex,
                                    cfi = finalCfi
                                )
                                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                    "create_request source=native_vertical_highlight_menu color=${color.id} " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                        "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                        "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                        "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                        "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                )
                                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                    "create_request surface=native_vertical action=highlight color=${color.id} " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                        "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                        "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                        "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                        "locator=${locator} textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                )
                                onHighlightCreated(finalCfi, sel.text, color.id, locator)
                                activeSelection = null
                            },
                            onNote = {
                                onNoteRequested(null)
                                val startAbsoluteOffset = sel.startBlockCharOffset + sel.startOffset
                                val endAbsoluteOffset = sel.endBlockCharOffset + sel.endOffset
                                val finalCfi =
                                    "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                val absoluteCandidateCfi =
                                    "${sel.startBaseCfi}:$startAbsoluteOffset|${sel.endBaseCfi}:$endAbsoluteOffset"
                                val locator = sel.toSharedHighlightLocator(
                                    chapterIndex = sel.startPageIndex,
                                    cfi = finalCfi
                                )
                                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                    "create_request source=native_vertical_note_menu color=${HighlightColor.YELLOW.id} " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                        "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                        "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                        "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                        "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                )
                                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                    "create_request surface=native_vertical action=note color=${HighlightColor.YELLOW.id} " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                        "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                        "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                        "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                        "locator=${locator} textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                )
                                onHighlightCreated(finalCfi, sel.text, HighlightColor.YELLOW.id, locator)
                                activeSelection = null
                            },
                            onTts = {
                                val startAbs = sel.startOffset + sel.startBlockCharOffset
                                onStartTtsFromSelection(sel.startBaseCfi, startAbs, sel.startPageIndex)
                                activeSelection = null
                            },
                            onDelete = null,
                            isProUser = isProUser,
                            isOss = isOss,
                            activeHighlightPalette = activeHighlightPalette,
                            onOpenPaletteManager = { showPaletteManager = true }
                        )
                    }
                }

                val latestActiveSelection by rememberUpdatedState(activeSelection)
                val updateSelection: (Offset, SelectionHandle, Boolean) -> SelectionHandle =
                    updateSelection@ { windowPos, currentDragHandle, withHaptic ->
                        val currentSelection = latestActiveSelection ?: return@updateSelection currentDragHandle
                        val attachedBlocks = attachedSelectionBlocks(blockLayoutMap)
                        val updated = updatedSelectionForHandleDrag(
                            selection = currentSelection,
                            windowPos = windowPos,
                            currentDragHandle = currentDragHandle,
                            attachedBlocks = attachedBlocks,
                            blockLayoutMap = blockLayoutMap
                        )
                        if (updated != null) {
                            if (withHaptic) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            activeSelection = updated.first
                            updated.second
                        } else {
                            currentDragHandle
                        }
                    }

                val latestUpdateSelection by rememberUpdatedState(updateSelection)

                LaunchedEffect(isDraggingHandle) {
                    while (isDraggingHandle && isActive) {
                        val delta = selectionEdgeScrollDelta
                        if (abs(delta) > 0.5f) {
                            listState.scrollBy(delta)
                            withFrameNanos { }
                            val handle = selectionEdgeDragHandle
                            val targetWindowPos = selectionEdgeDragWindowPos
                            if (handle != null && targetWindowPos.isSpecified) {
                                selectionEdgeDragHandle = latestUpdateSelection(targetWindowPos, handle, false)
                            }
                        } else {
                            withFrameNanos { }
                        }
                    }
                    selectionEdgeScrollDelta = 0f
                    selectionEdgeDragWindowPos = Offset.Unspecified
                    selectionEdgeDragHandle = null
                }

                listOf(SelectionHandle.START, SelectionHandle.END).forEach { handleType ->
                    val isStart = handleType == SelectionHandle.START
                    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                    Box(
                        modifier = Modifier
                            .zIndex(8f)
                            .graphicsLayer {
                                @Suppress("UNUSED_VARIABLE") val tick = blockLayoutMap.tick
                                val pos = selectionHandleRootPosition(
                                    selection = sel,
                                    isStart = isStart,
                                    blockLayoutMap = blockLayoutMap,
                                    rootCoords = rootCoords
                                )
                                val shouldShowHandle = !isDraggingHandle ||
                                    activeDragHandleForDisplay == null ||
                                    activeDragHandleForDisplay == handleType

                                if (pos.isSpecified && shouldShowHandle) {
                                    translationX = pos.x - 18.dp.toPx()
                                    translationY = pos.y
                                    alpha = 1f
                                } else {
                                    alpha = 0f
                                }
                            }
                            .size(36.dp)
                            .onGloballyPositioned { handleCoords = it }
                            .pointerInput(handleType, listState) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    if (isDraggingHandle && selectionEdgeDragHandle != null) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            change.consume()
                                            if (!change.pressed) break
                                        }
                                        return@awaitEachGesture
                                    }
                                    isDraggingHandle = true
                                    var currentDragHandle = handleType
                                    selectionEdgeDragHandle = currentDragHandle
                                    selectionEdgeDragWindowPos = Offset.Unspecified
                                    var downPointerRoot = Offset.Unspecified
                                    var downHandleAnchorRoot = Offset.Unspecified
                                    if (
                                        handleCoords != null &&
                                        rootCoords != null &&
                                        handleCoords!!.isAttached &&
                                        rootCoords!!.isAttached
                                    ) {
                                        try {
                                            val pointerWindow = handleCoords!!.localToWindow(down.position)
                                            downPointerRoot = rootCoords!!.windowToLocal(pointerWindow)
                                            downHandleAnchorRoot = latestActiveSelection?.let { currentSelection ->
                                                selectionHandleRootPosition(
                                                    selection = currentSelection,
                                                    isStart = isStart,
                                                    blockLayoutMap = blockLayoutMap,
                                                    rootCoords = rootCoords
                                                )
                                            } ?: Offset.Unspecified
                                        } catch (_: Exception) {
                                            downPointerRoot = Offset.Unspecified
                                            downHandleAnchorRoot = Offset.Unspecified
                                        }
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        change.consume()

                                        if (
                                            handleCoords != null &&
                                            rootCoords != null &&
                                            handleCoords!!.isAttached &&
                                            rootCoords!!.isAttached
                                        ) {
                                            try {
                                                selectionEdgeDragHandle?.let { currentDragHandle = it }
                                                val pointerWindow = handleCoords!!.localToWindow(change.position)
                                                val pointerRoot = rootCoords!!.windowToLocal(pointerWindow)
                                                val edgeSize = 64.dp.toPx()
                                                val maxScrollStep = 28.dp.toPx()
                                                val rootHeight = rootCoords!!.size.height.toFloat()
                                                val edgeScrollDelta = when {
                                                    pointerRoot.y < edgeSize ->
                                                        -(((edgeSize - pointerRoot.y) / edgeSize) * maxScrollStep)
                                                            .coerceIn(2.dp.toPx(), maxScrollStep)
                                                    pointerRoot.y > rootHeight - edgeSize ->
                                                        (((pointerRoot.y - (rootHeight - edgeSize)) / edgeSize) * maxScrollStep)
                                                            .coerceIn(2.dp.toPx(), maxScrollStep)
                                                    else -> 0f
                                                }
                                                selectionEdgeScrollDelta = edgeScrollDelta

                                                val targetRootPos = if (
                                                    downPointerRoot.isSpecified &&
                                                    downHandleAnchorRoot.isSpecified
                                                ) {
                                                    downHandleAnchorRoot + (pointerRoot - downPointerRoot)
                                                } else {
                                                    pointerRoot
                                                }
                                                magnifierCenter = targetRootPos

                                                val textHitRootPos = targetRootPos.copy(
                                                    y = targetRootPos.y - 2.dp.toPx()
                                                )
                                                val targetWindowPos = rootCoords!!.localToWindow(textHitRootPos)
                                                currentDragHandle = latestUpdateSelection(targetWindowPos, currentDragHandle, true)
                                                selectionEdgeDragWindowPos = targetWindowPos
                                                selectionEdgeDragHandle = currentDragHandle
                                            } catch (_: Exception) {
                                                // Ignore detachment during fast scroll/drag handoff.
                                            }
                                        }
                                    }
                                    isDraggingHandle = false
                                    selectionEdgeScrollDelta = 0f
                                    selectionEdgeDragWindowPos = Offset.Unspecified
                                    selectionEdgeDragHandle = null
                                    magnifierCenter = Offset.Unspecified
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.teardrop),
                            contentDescription = if (isStart) "Start handle" else "End handle",
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer {
                                    rotationZ = if (isStart) 30f else -30f
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                },
                            tint = Color(0xFF1976D2)
                        )
                    }
                }
            }

            if (showPaletteManager) {
                PaletteManagerDialog(
                    currentPalette = activeHighlightPalette,
                    onDismiss = { showPaletteManager = false },
                    onSave = { newPalette ->
                        newPalette.forEachIndexed { index, color ->
                            onUpdatePalette(index, color)
                        }
                        showPaletteManager = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NativeVerticalPage(
    page: Page,
    pageIndex: Int,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    textMeasurer: TextMeasurer,
    onLinkClickCallback: (String) -> Unit,
    onGeneralTapCallback: (Offset) -> Unit,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    blockLayoutMap: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    density: Density,
    imageLoader: ImageLoader,
    horizontalPadding: Dp,
    effectiveBg: Color,
    onTap: (Offset?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(effectiveBg)
            .padding(horizontal = horizontalPadding)
            .pointerInput(pageIndex) {
                detectTapGestures(onTap = { offset -> onTap(offset) })
            }
    ) {
        page.content.forEach { block ->
            NativeVerticalContentBlock(
                block = block,
                pageIndex = pageIndex,
                textStyle = textStyle,
                imageSizeMultiplier = imageSizeMultiplier,
                searchQuery = searchQuery,
                searchHighlightColor = searchHighlightColor,
                ttsHighlightInfo = ttsHighlightInfo,
                ttsHighlightColor = ttsHighlightColor,
                textMeasurer = textMeasurer,
                onLinkClickCallback = onLinkClickCallback,
                onGeneralTapCallback = onGeneralTapCallback,
                userHighlights = userHighlights,
                activeSelection = activeSelection,
                onSelectionChange = onSelectionChange,
                onHighlightClick = onHighlightClick,
                isDarkTheme = isDarkTheme,
                themeBackgroundColor = themeBackgroundColor,
                themeTextColor = themeTextColor,
                blockLayoutMap = blockLayoutMap,
                density = density,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NativeVerticalContentBlock(
    block: ContentBlock,
    pageIndex: Int,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    textMeasurer: TextMeasurer,
    onLinkClickCallback: (String) -> Unit,
    onGeneralTapCallback: (Offset) -> Unit,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    blockLayoutMap: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    density: Density,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    val styledModifier = modifier
        .padding(
            start = block.style.margin.left.coerceAtLeast(0.dp),
            top = block.style.margin.top.coerceAtLeast(0.dp),
            end = block.style.margin.right.coerceAtLeast(0.dp),
            bottom = block.style.margin.bottom.coerceAtLeast(0.dp)
        )
        .drawCssBorders(block.style, density)
        .padding(
            start = block.style.padding.left.coerceAtLeast(0.dp),
            top = block.style.padding.top.coerceAtLeast(0.dp),
            end = block.style.padding.right.coerceAtLeast(0.dp),
            bottom = block.style.padding.bottom.coerceAtLeast(0.dp)
        )

    when (block) {
        is WrappingContentBlock -> {
            WrappingContentLayout(
                block = block,
                textStyle = textStyle,
                imageSizeMultiplier = imageSizeMultiplier,
                modifier = styledModifier,
                searchQuery = searchQuery,
                ttsHighlightInfo = ttsHighlightInfo,
                searchHighlightColor = searchHighlightColor,
                ttsHighlightColor = ttsHighlightColor,
                isDarkTheme = isDarkTheme,
                themeBackgroundColor = themeBackgroundColor,
                themeTextColor = themeTextColor,
                onLinkClick = onLinkClickCallback,
                onGeneralTap = onGeneralTapCallback
            )
        }
        is MathBlock -> {
            RenderNativeMathBlock(
                block = block,
                textStyle = textStyle,
                imageLoader = imageLoader,
                modifier = styledModifier
            )
        }
        is FlexContainerBlock -> {
            val renderChild: @Composable (ContentBlock) -> Unit = { child ->
                NativeVerticalContentBlock(
                    block = child,
                    pageIndex = pageIndex,
                    textStyle = textStyle,
                    imageSizeMultiplier = imageSizeMultiplier,
                    searchQuery = searchQuery,
                    searchHighlightColor = searchHighlightColor,
                    ttsHighlightInfo = ttsHighlightInfo,
                    ttsHighlightColor = ttsHighlightColor,
                    textMeasurer = textMeasurer,
                    onLinkClickCallback = onLinkClickCallback,
                    onGeneralTapCallback = onGeneralTapCallback,
                    userHighlights = userHighlights,
                    activeSelection = activeSelection,
                    onSelectionChange = onSelectionChange,
                    onHighlightClick = onHighlightClick,
                    isDarkTheme = isDarkTheme,
                    themeBackgroundColor = themeBackgroundColor,
                    themeTextColor = themeTextColor,
                    blockLayoutMap = blockLayoutMap,
                    density = density,
                    imageLoader = imageLoader,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (block.style.flexDirection == "row") {
                Row(modifier = styledModifier.fillMaxWidth()) {
                    block.children.forEach { child ->
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            renderChild(child)
                        }
                    }
                }
            } else {
                Column(modifier = styledModifier.fillMaxWidth()) {
                    block.children.forEach { child -> renderChild(child) }
                }
            }
        }
        else -> {
            Box(modifier = styledModifier) {
                RenderFlexChildBlock(
                    childBlock = block,
                    textStyle = textStyle,
                    imageSizeMultiplier = imageSizeMultiplier,
                    searchQuery = searchQuery,
                    searchHighlightColor = searchHighlightColor,
                    ttsHighlightInfo = ttsHighlightInfo,
                    ttsHighlightColor = ttsHighlightColor,
                    textMeasurer = textMeasurer,
                    onLinkClickCallback = onLinkClickCallback,
                    onGeneralTapCallback = onGeneralTapCallback,
                    userHighlights = userHighlights,
                    activeSelection = activeSelection,
                    onSelectionChange = onSelectionChange,
                    onHighlightClick = onHighlightClick,
                    isDarkTheme = isDarkTheme,
                    themeBackgroundColor = themeBackgroundColor,
                    themeTextColor = themeTextColor,
                    blockLayoutMap = blockLayoutMap,
                    density = density,
                    imageLoader = imageLoader,
                    pageIndex = pageIndex,
                    registerStableLayoutKey = true
                )
            }
        }
    }
}

@Composable
private fun RenderNativeMathBlock(
    block: MathBlock,
    textStyle: TextStyle,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    val svgContent = block.svgContent?.takeIf { it.isNotBlank() }
    if (svgContent != null) {
        val imageRequest = Builder(LocalContext.current)
            .data(SvgData(svgContent))
            .listener(
                onError = { _, result ->
                    Timber.e(result.throwable, "Coil failed to load SVG for native vertical MathBlock.")
                }
            )
            .build()
        AsyncImage(
            model = imageRequest,
            contentDescription = block.altText ?: "Equation",
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (block.isFromMathJax) ColorFilter.tint(textStyle.color) else null,
            imageLoader = imageLoader
        )
    } else {
        Text(
            text = block.altText ?: "[Equation not available]",
            style = textStyle,
            modifier = modifier
        )
    }
}

private fun parseEmphasisAnnotation(annotation: String, defaultColor: Color): TextEmphasis {
    Timber.d("Parsing annotation string: '$annotation'")
    val map = annotation.split(';').filter { it.isNotBlank() }.associate {
        val (key, value) = it.split(':', limit = 2)
        key to value
    }
    val emphasis = TextEmphasis(
        style = map["s"],
        fill = map["f"],
        color = map["c"]?.toULongOrNull()?.let { Color(it) } ?: defaultColor,
        position = map["p"])
    Timber.d("Parsed annotation to object: $emphasis")
    return emphasis
}

private fun findFuzzyMatch(source: String, target: String, ignoreCase: Boolean = true): IntRange? {
    if (target.isBlank()) return null
    val targetWords = target.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (targetWords.isEmpty()) return null

    var searchStart = 0
    while (searchStart < source.length) {
        val firstIdx = source.indexOf(targetWords[0], searchStart, ignoreCase = ignoreCase)
        if (firstIdx == -1) return null

        var currentIdx = firstIdx + targetWords[0].length
        var allMatch = true

        for (i in 1 until targetWords.size) {
            while (currentIdx < source.length && source[currentIdx].isWhitespace()) {
                currentIdx++
            }
            if (currentIdx >= source.length) {
                allMatch = false
                break
            }

            val word = targetWords[i]
            if (source.regionMatches(currentIdx, word, 0, word.length, ignoreCase = ignoreCase)) {
                currentIdx += word.length
            } else {
                allMatch = false
                break
            }
        }

        if (allMatch) return firstIdx until currentIdx
        searchStart = firstIdx + 1
    }
    return null
}

internal fun getHighlightOffsetsInBlock(
    block: TextContentBlock, highlight: UserHighlight
): IntRange? {
    @Suppress("REDUNDANT_ELSE_IN_WHEN") val blockStartAbs = when (block) {
        is ParagraphBlock -> block.startCharOffsetInSource
        is HeaderBlock -> block.startCharOffsetInSource
        is QuoteBlock -> block.startCharOffsetInSource
        is ListItemBlock -> block.startCharOffsetInSource
        else -> 0
    }
    val blockEndAbs = block.endCharOffsetInSource
        .takeIf { it > blockStartAbs }
        ?: (blockStartAbs + block.content.text.length)
    val blockText = block.content.text
    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
        "map_start blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
            "blockAbs=$blockStartAbs..$blockEndAbs blockLen=${blockText.length} " +
            "hasPreciseLocator=${highlight.locator.hasTextRange} " +
            highlight.androidHighlightRenderLabel()
    )

    locatorHighlightOffsetsInBlock(
        blockText = blockText,
        blockStartAbs = blockStartAbs,
        blockEndAbs = blockEndAbs,
        blockIndex = block.blockIndex,
        blockCfi = block.cfi,
        highlight = highlight
    )?.let { return it }

    if (block.cfi == null) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=missing_block_cfi blockIndex=${block.blockIndex} blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }

    val blockPath = CfiUtils.getPath(block.cfi!!)
    val sourceCfi = highlight.locator.cfi?.takeIf { it.isNotBlank() } ?: highlight.cfi
    val parts = sourceCfi.split('|')
    val startCfi = parts.firstOrNull() ?: highlight.cfi
    val endCfi = parts.lastOrNull()
    val isMultipartHighlight = endCfi != null && endCfi != startCfi

    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
        "map_check blockCfi=${block.cfi} blockPath=$blockPath " +
            "blockAbs=$blockStartAbs..$blockEndAbs blockLen=${block.content.text.length} " +
            "highlightId=${highlight.id} highlightChapter=${highlight.chapterIndex} " +
            "highlightCfi=$sourceCfi startCfi=$startCfi endCfi=$endCfi " +
            "highlightTextLen=${highlight.text.length} highlightText='${highlightDiagSnippet(highlight.text)}'"
    )

    val relevantPart = parts.find { cfiPart ->
        val highlightPath = CfiUtils.getPath(cfiPart)

        if (highlightPath.startsWith(blockPath)) return@find true

        val highlightSegments = highlightPath.split('/').filter { it.isNotEmpty() }
        val blockSegments = blockPath.split('/').filter { it.isNotEmpty() }

        if (highlightSegments.size > blockSegments.size) {
            val pathWithoutFirst = "/" + highlightSegments.drop(1).joinToString("/")
            if (pathWithoutFirst.startsWith(blockPath)) return@find true
        }

        if (highlightSegments.isNotEmpty() && blockSegments.isNotEmpty()) {
            if (highlightSegments[0] != blockSegments[0]) {
                val highlightTail = highlightSegments.drop(1)
                val blockTail = blockSegments.drop(1)
                if (blockTail.isNotEmpty() && highlightTail.size >= blockTail.size) {
                    var match = true
                    for (i in blockTail.indices) {
                        if (blockTail[i] != highlightTail[i]) {
                            match = false
                            break
                        }
                    }
                    if (match) return@find true
                }
            }
        }
        false
    }

    if (relevantPart != null) {
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_relevant_part blockCfi=${block.cfi} highlightId=${highlight.id} part=$relevantPart"
        )
    }

    val highlightText = highlight.text

    if (blockText.isEmpty() || highlightText.isEmpty()) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=empty_text blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "blockTextLen=${blockText.length} highlightTextLen=${highlightText.length} " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }

    val isIntermediateBlock = relevantPart == null &&
        isMultipartHighlight &&
        CfiUtils.isPathStrictlyBetween(block.cfi!!, startCfi, endCfi!!)

    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
        "map_decision blockCfi=${block.cfi} highlightId=${highlight.id} " +
            "relevantPart=$relevantPart isIntermediateBlock=$isIntermediateBlock"
    )

    if (relevantPart == null) {
        if (!isIntermediateBlock) {
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_skip reason=no_relevant_cfi_part blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "startCfi=$startCfi endCfi=$endCfi " +
                    highlight.androidHighlightRenderLabel()
            )
            return null
        }
        if (highlightText.contains(blockText, ignoreCase = false)) {
            val range = 0 until blockText.length
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_result reason=intermediate_exact blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "range=$range " + highlight.androidHighlightRenderLabel()
            )
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_result reason=intermediate_exact blockCfi=${block.cfi} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
            )
            return range
        }
        if (highlightText.contains(blockText, ignoreCase = true)) {
            val range = 0 until blockText.length
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_result reason=intermediate_exact_ignore_case blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "range=$range " + highlight.androidHighlightRenderLabel()
            )
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_result reason=intermediate_exact_ignore_case blockCfi=${block.cfi} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
            )
            return range
        }
        val normBlock = blockText.filter { !it.isWhitespace() }
        val normHighlight = highlightText.filter { !it.isWhitespace() }
        return if (normBlock.isNotBlank() && normHighlight.contains(normBlock, ignoreCase = true)) {
            val range = 0 until blockText.length
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_result reason=intermediate_normalized blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "range=$range " + highlight.androidHighlightRenderLabel()
            )
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_result reason=intermediate_normalized blockCfi=${block.cfi} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
            )
            range
        } else {
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_skip reason=intermediate_text_miss blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "blockText='${highlightDiagSnippet(blockText)}' " +
                    highlight.androidHighlightRenderLabel()
            )
            null
        }
    }

    if (relevantPart != null) {
        fun arePathsEquivalent(path1: String, path2: String): Boolean {
            val p1 = CfiUtils.getPath(path1).split('/').filter { it.isNotEmpty() }
            val p2 = CfiUtils.getPath(path2).split('/').filter { it.isNotEmpty() }

            if (p1 == p2) return true

            if (p1.size == p2.size && p1.isNotEmpty()) {
                return p1.drop(1) == p2.drop(1)
            }
            return false
        }

        val startMatches = arePathsEquivalent(startCfi, block.cfi!!)
        val endMatches = if (endCfi != null) arePathsEquivalent(endCfi, block.cfi!!) else false

        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_path_equivalence blockCfi=${block.cfi} highlightId=${highlight.id} " +
                "startMatches=$startMatches endMatches=$endMatches"
        )

        if (startMatches || endMatches) {
            val startAbs = CfiUtils.getOffsetOrNull(startCfi)
            val endAbs = endCfi?.let { CfiUtils.getOffsetOrNull(it) }
            val startLocal = startAbs?.let {
                cfiOffsetToBlockLocal(
                    offset = it,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )
            }
            val endLocal = endAbs?.let {
                cfiOffsetToBlockLocal(
                    offset = it,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )
            }
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_offset_inputs blockCfi=${block.cfi} highlightId=${highlight.id} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs cfiOffsets=$startAbs..$endAbs " +
                    "localOffsets=$startLocal..$endLocal"
            )
            if (startMatches && endMatches && startLocal != null && endLocal != null) {
                val rangeStartLocal = minOf(startLocal, endLocal)
                val rangeEndLocal = maxOf(startLocal, endLocal)
                if (rangeEndLocal <= 0 || rangeStartLocal >= blockText.length) {
                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                        "map_skip reason=same_path_split_outside_offsets blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                            "highlightLocal=$rangeStartLocal..$rangeEndLocal blockLen=${blockText.length} " +
                            highlight.androidHighlightRenderLabel()
                    )
                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                        "map_skip reason=same_path_split_outside_offsets blockCfi=${block.cfi} " +
                            "highlightId=${highlight.id} highlightLocal=$rangeStartLocal..$rangeEndLocal " +
                            "blockAbs=$blockStartAbs..$blockEndAbs"
                    )
                    return null
                }
            } else {
                if (startMatches && startLocal != null && startLocal >= blockText.length) {
                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                        "map_skip reason=start_offset_after_block blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                            "startLocal=$startLocal blockLen=${blockText.length} " +
                            highlight.androidHighlightRenderLabel()
                    )
                    return null
                }
                if (endMatches && endLocal != null && endLocal <= 0) {
                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                        "map_skip reason=end_offset_before_block blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                            "endLocal=$endLocal blockLen=${blockText.length} " +
                            highlight.androidHighlightRenderLabel()
                    )
                    return null
                }
            }
            var s = 0
            var e = blockText.length

            if (startMatches) {
                val rawOffset = startAbs ?: CfiUtils.getOffset(startCfi)
                val relOffset = cfiOffsetToBlockLocal(
                    offset = rawOffset,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )

                if (relOffset < 0) {
                    s = 0
                } else {
                    val safeStart = (relOffset - 50).coerceAtLeast(0)
                    val safeEnd = (relOffset + 50).coerceAtMost(blockText.length)

                    if (safeStart < safeEnd) {
                        val windowText = blockText.substring(safeStart, safeEnd)
                        val prefix = highlightText.trim().take(20).trim()

                        var snapped = false
                        if (prefix.isNotEmpty()) {
                            val matches = mutableListOf<Int>()
                            var idx = windowText.indexOf(prefix, ignoreCase = true)
                            while (idx != -1) {
                                matches.add(idx)
                                idx = windowText.indexOf(prefix, idx + 1, ignoreCase = true)
                            }

                            if (matches.isNotEmpty()) {
                                val targetRel = relOffset - safeStart
                                val bestRel = matches.minByOrNull { abs(it - targetRel) }!!
                                val newS = safeStart + bestRel
                                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                    "map_snap_start blockCfi=${block.cfi} highlightId=${highlight.id} " +
                                        "fromRel=$relOffset toRel=$newS prefix='$prefix'"
                                )
                                s = newS
                                snapped = true
                            }
                        }

                        if (!snapped) {
                            s = relOffset
                        }
                    } else {
                        s = relOffset
                    }
                }
            }

            if (endMatches) {
                val rawOffset = endAbs ?: CfiUtils.getOffset(endCfi!!)
                val relOffset = cfiOffsetToBlockLocal(
                    offset = rawOffset,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )

                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                    "map_end_match blockCfi=${block.cfi} highlightId=${highlight.id} " +
                        "rawOffset=$rawOffset relOffset=$relOffset blockLen=${blockText.length}"
                )

                e = if (relOffset > blockText.length) {
                    blockText.length
                } else {
                    relOffset
                }
            }

            s = s.coerceIn(0, blockText.length)
            e = e.coerceIn(0, blockText.length)

            if (s < e) {
                val range = s until e
                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                    "map_result reason=cfi_offsets blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                        "range=$range startMatches=$startMatches endMatches=$endMatches " +
                        "startAbs=$startAbs endAbs=$endAbs startLocal=$startLocal endLocal=$endLocal " +
                        highlight.androidHighlightRenderLabel()
                )
                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                    "map_result reason=cfi_offsets blockCfi=${block.cfi} " +
                        "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
                )
                return range
            } else {
                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                    "map_skip reason=invalid_cfi_range blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                        "range=$s..$e startMatches=$startMatches endMatches=$endMatches " +
                        highlight.androidHighlightRenderLabel()
                )
                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).w(
                    "map_skip reason=invalid_range blockCfi=${block.cfi} " +
                        "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$s..$e"
                )
                return null
            }
        }
    }

    if (highlightText.contains(blockText, ignoreCase = false)) {
        val range = 0 until blockText.length
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=block_inside_highlight_text blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$range " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=block_inside_highlight_text blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
        )
        return range
    }
    if (highlightText.contains(blockText, ignoreCase = true)) {
        val range = 0 until blockText.length
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=block_inside_highlight_text_ignore_case blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$range " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=block_inside_highlight_text_ignore_case blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
        )
        return range
    }

    var startIndex = blockText.indexOf(highlightText, ignoreCase = false)
    if (startIndex == -1) {
        startIndex = blockText.indexOf(highlightText, ignoreCase = true)
    }

    if (startIndex >= 0) {
        val range = startIndex until (startIndex + highlightText.length)
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=highlight_text_inside_block blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$range startIndex=$startIndex " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=highlight_text_inside_block blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
        )
        return range
    }

    val match = findFuzzyMatch(blockText, highlightText)
    if (match != null) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=fuzzy_text blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$match " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=fuzzy_text blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$match"
        )
        return match
    }

    if (relevantPart != null) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=cfi_match_text_miss blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "relevantPart=$relevantPart " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_skip reason=cfi_match_text_miss blockCfi=${block.cfi} " +
                "highlightId=${highlight.id} highlightCfi=${highlight.cfi}"
        )
    }

    if (highlight.locator.hasTextRange) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=precise_locator_and_cfi_miss blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "sourceCfi=$sourceCfi " + highlight.androidHighlightRenderLabel()
        )
        return null
    }

    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
        "map_skip reason=no_mapping_match blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
            highlight.androidHighlightRenderLabel()
    )
    return null
}

private fun androidHighlightSourceCfi(highlight: UserHighlight): String {
    return highlight.locator.cfi?.takeIf { it.isNotBlank() } ?: highlight.cfi
}

private fun androidCfiPathsEquivalent(first: String, second: String): Boolean {
    val firstPath = CfiUtils.getPath(first)
    val secondPath = CfiUtils.getPath(second)
    if (firstPath == secondPath || firstPath.startsWith("$secondPath/") || secondPath.startsWith("$firstPath/")) {
        return true
    }
    val firstParts = firstPath.split('/').filter { it.isNotEmpty() }
    val secondParts = secondPath.split('/').filter { it.isNotEmpty() }
    if (firstParts == secondParts) return true
    return firstParts.size == secondParts.size &&
        firstParts.isNotEmpty() &&
        firstParts.drop(1) == secondParts.drop(1)
}

private fun androidHighlightHasMultipartCfiRange(highlight: UserHighlight): Boolean {
    val parts = androidHighlightSourceCfi(highlight)
        .split('|')
        .filter { it.startsWith("/") }
    if (parts.size < 2) return false
    val first = parts.first()
    return parts.drop(1).any { !androidCfiPathsEquivalent(first, it) }
}

private fun androidHighlightCfiTouchesBlock(highlight: UserHighlight, blockCfi: String?): Boolean {
    val blockPath = blockCfi?.takeIf { it.startsWith("/") } ?: return false
    return androidHighlightSourceCfi(highlight)
        .split('|')
        .filter { it.startsWith("/") }
        .any { androidCfiPathsEquivalent(it, blockPath) }
}

private fun cfiOffsetToBlockLocal(
    offset: Int,
    blockStartAbs: Int,
    blockEndAbs: Int,
    textLength: Int
): Int {
    return when {
        offset in 0..textLength -> offset
        offset in blockStartAbs..blockEndAbs -> offset - blockStartAbs
        else -> offset
    }
}

private fun locatorHighlightOffsetsInBlock(
    blockText: String,
    blockStartAbs: Int,
    blockEndAbs: Int,
    blockIndex: Int,
    blockCfi: String?,
    highlight: UserHighlight
): IntRange? {
    if (blockText.isEmpty()) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=empty_block_text blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    if (androidHighlightHasMultipartCfiRange(highlight)) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=multipart_cfi_uses_cfi_mapper blockIndex=$blockIndex blockCfi=$blockCfi " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val locatorBlockIndex = highlight.locator.blockIndex
    val blockMatchesLocator = locatorBlockIndex != null && locatorBlockIndex == blockIndex
    val cfiMatchesBlock = androidHighlightCfiTouchesBlock(highlight, blockCfi)
    val hasStructuralScope = locatorBlockIndex != null || androidHighlightSourceCfi(highlight).startsWith("/")
    if (hasStructuralScope && !blockMatchesLocator && !cfiMatchesBlock) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_miss reason=structural_scope_miss blockIndex=$blockIndex blockCfi=$blockCfi " +
                "blockMatchesLocator=$blockMatchesLocator cfiMatchesBlock=$cfiMatchesBlock " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val start = highlight.locator.startOffset ?: run {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=missing_start blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val end = highlight.locator.endOffset ?: run {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=missing_end blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val rangeStartAbs = minOf(start, end)
    val rangeEndAbs = maxOf(start, end)
    if (rangeEndAbs <= blockStartAbs || rangeStartAbs >= blockEndAbs) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_miss reason=no_intersection blockAbs=$blockStartAbs..$blockEndAbs " +
                "highlightAbs=$rangeStartAbs..$rangeEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val localStart = (rangeStartAbs - blockStartAbs).coerceIn(0, blockText.length)
    val localEnd = (rangeEndAbs - blockStartAbs).coerceIn(localStart, blockText.length)
    return if (localStart < localEnd) {
        val range = localStart until localEnd
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=locator_offsets blockAbs=$blockStartAbs..$blockEndAbs " +
                "range=$range highlightAbs=$rangeStartAbs..$rangeEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        range
    } else {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_miss reason=invalid_local_range blockAbs=$blockStartAbs..$blockEndAbs " +
                "local=$localStart..$localEnd highlightAbs=$rangeStartAbs..$rangeEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        null
    }
}

private fun List<ContentBlock>.extractTextBlocks(): List<TextContentBlock> {
    val result = mutableListOf<TextContentBlock>()
    for (block in this) {
        when (block) {
            is WrappingContentBlock -> result.addAll(block.paragraphsToWrap)
            is FlexContainerBlock -> result.addAll(block.children.extractTextBlocks())
            is TableBlock -> {
                block.rows.forEach { row ->
                    row.forEach { cell ->
                        result.addAll(cell.content.extractTextBlocks())
                    }
                }
            }
            is TextContentBlock -> result.add(block)
            else -> {}
        }
    }
    return result
}

private fun LayoutCoordinates.androidEpubPageContentBounds(
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int
): AndroidEpubPageContentBounds {
    val pageTopPx = positionInWindow().y.roundToInt()
    val contentTopPx = pageTopPx + verticalPaddingPx
    val contentBottomPx = pageTopPx + size.height - verticalPaddingPx
    return AndroidEpubPageContentBounds(
        topPx = contentTopPx,
        bottomPx = contentBottomPx,
        widthPx = (size.width - (horizontalPaddingPx * 2)).coerceAtLeast(0),
        heightPx = (contentBottomPx - contentTopPx).coerceAtLeast(0),
        pageWidthPx = size.width,
        pageHeightPx = size.height,
        horizontalPaddingPx = horizontalPaddingPx,
        verticalPaddingPx = verticalPaddingPx
    )
}

private fun logAndroidEpubCutoff(message: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(AndroidEpubCutoffLogTag, message)
}

private fun Modifier.androidEpubNaturalHeight(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        )
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
)

private fun TextContentBlock.androidEpubSourceRangeLabel(): String {
    val start = startCharOffsetInSource
    val end = endCharOffsetInSource.takeIf { it > start } ?: (start + content.text.length)
    return "$start..$end"
}

private fun TextContentBlock.androidEpubKindName(): String {
    return when (this) {
        is HeaderBlock -> "header"
        is ParagraphBlock -> "paragraph"
        is QuoteBlock -> "quote"
        is ListItemBlock -> "list_item"
        else -> "text"
    }
}

private fun ContentBlock.androidEpubKindName(): String {
    return when (this) {
        is HeaderBlock -> "header"
        is ParagraphBlock -> "paragraph"
        is QuoteBlock -> "quote"
        is ListItemBlock -> "list_item"
        is TextContentBlock -> "text"
        is ImageBlock -> "image"
        is MathBlock -> "math"
        is TableBlock -> "table"
        is FlexContainerBlock -> "flex"
        is WrappingContentBlock -> "wrapping"
        is SpacerBlock -> "spacer"
    }
}

private fun logAndroidEpubBlockOverflowIfNeeded(
    pageIndex: Int,
    block: ContentBlock,
    coordinates: LayoutCoordinates,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    signatureAlreadyLogged: (String) -> Boolean,
    markSignatureLogged: (String) -> Unit
) {
    val bounds = pageContentBounds ?: return
    val blockTopPx = coordinates.positionInWindow().y.roundToInt()
    val blockBottomPx = blockTopPx + coordinates.size.height
    val contentOverflowPx = blockBottomPx - bounds.bottomPx
    val pageClipOverflowPx = blockBottomPx - bounds.pageClipBottomPx
    if (pageClipOverflowPx <= AndroidEpubCutoffTolerancePx) return
    val relativeTopPx = blockTopPx - bounds.topPx
    val signature = "block:$pageIndex:${block.blockIndex}:$relativeTopPx:${coordinates.size.height}:$pageClipOverflowPx"
    if (signatureAlreadyLogged(signature)) return
    markSignatureLogged(signature)
    logAndroidEpubCutoff(
        "cutoff_probe layer=android_rendered_block_overflow page=${pageIndex + 1} " +
            "block=${block.blockIndex} kind=${block.androidEpubKindName()} " +
            "blockTopPx=$relativeTopPx blockHeightPx=${coordinates.size.height} " +
            "blockBottomPx=${blockBottomPx - bounds.topPx} contentPx=${bounds.widthPx}x${bounds.heightPx} " +
            "pagePx=${bounds.pageWidthPx}x${bounds.pageHeightPx} contentOverflowPx=$contentOverflowPx " +
            "pageClipOverflowPx=$pageClipOverflowPx " +
            "expectedHeightPx=${block.expectedHeight} actualHeightPx=${coordinates.size.height} " +
            "paddingPx=${bounds.horizontalPaddingPx}x${bounds.verticalPaddingPx} $diagnosticsContext"
    )
}

private fun logAndroidEpubTextCutoffIfNeeded(
    pageIndex: Int,
    block: TextContentBlock,
    layout: TextLayoutResult,
    coordinates: LayoutCoordinates,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    previousSignature: String?
): String? {
    val boxTopPx = coordinates.positionInWindow().y.roundToInt()
    val boxHeightPx = coordinates.size.height
    val lastLine = layout.lineCount - 1
    val lastLineTopPx = if (lastLine >= 0) layout.getLineTop(lastLine).roundToInt() else 0
    val lastLineBottomPx = if (lastLine >= 0) layout.getLineBottom(lastLine).roundToInt() else layout.size.height
    val lastLineStart = if (lastLine >= 0) layout.getLineStart(lastLine) else 0
    val lastLineEnd = if (lastLine >= 0) layout.getLineEnd(lastLine, visibleEnd = true) else 0
    val overflowBottomInBoxPx = maxOf(layout.size.height, lastLineBottomPx)
    val boxClipPx = overflowBottomInBoxPx - boxHeightPx
    val bounds = pageContentBounds
    val lineBottomInPagePx = if (bounds != null) {
        boxTopPx + overflowBottomInBoxPx - bounds.topPx
    } else {
        overflowBottomInBoxPx
    }
    val contentOverflowPx = bounds?.let { boxTopPx + overflowBottomInBoxPx - it.bottomPx } ?: 0
    val pageClipOverflowPx = bounds?.let { boxTopPx + overflowBottomInBoxPx - it.pageClipBottomPx } ?: 0
    val contentBottomInsetPx = bounds?.let { it.bottomPx - (boxTopPx + overflowBottomInBoxPx) }
    val pageClipBottomInsetPx = bounds?.let { it.pageClipBottomPx - (boxTopPx + overflowBottomInBoxPx) }
    val bottomEdgeRisk = pageClipBottomInsetPx != null && pageClipBottomInsetPx in 0..AndroidEpubCutoffEdgeProbePx
    if (
        boxClipPx <= AndroidEpubCutoffTolerancePx &&
        pageClipOverflowPx <= AndroidEpubCutoffTolerancePx &&
        !bottomEdgeRisk
    ) {
        return previousSignature
    }

    val signature = buildString {
        append(pageIndex)
        append(':')
        append(block.blockIndex)
        append(':')
        append(coordinates.size.width)
        append('x')
        append(boxHeightPx)
        append(':')
        append(layout.size.width)
        append('x')
        append(layout.size.height)
        append(':')
        append(lastLineBottomPx)
        append(':')
        append(bounds?.pageClipBottomPx ?: -1)
    }
    if (signature == previousSignature) return previousSignature

    val layer = if (boxClipPx > AndroidEpubCutoffTolerancePx) {
        "android_text_clip"
    } else if (pageClipOverflowPx > AndroidEpubCutoffTolerancePx) {
        "android_text_page_overflow"
    } else if (bottomEdgeRisk) {
        "android_text_bottom_edge"
    } else {
        "android_text_page_overflow"
    }
    logAndroidEpubCutoff(
        "cutoff_probe layer=$layer page=${pageIndex + 1} block=${block.blockIndex} " +
            "kind=${block.androidEpubKindName()} boxPx=${coordinates.size.width}x$boxHeightPx " +
            "layoutPx=${layout.size.width}x${layout.size.height} lines=${layout.lineCount} " +
            "lastLine=$lastLine lastLineTopPx=$lastLineTopPx lastLineBottomPx=$lastLineBottomPx " +
            "lastLineBottomInPagePx=$lineBottomInPagePx boxClipPx=$boxClipPx " +
            "contentOverflowPx=$contentOverflowPx pageClipOverflowPx=$pageClipOverflowPx " +
            "contentBottomInsetPx=${contentBottomInsetPx ?: "unknown"} " +
            "pageClipBottomInsetPx=${pageClipBottomInsetPx ?: "unknown"} " +
            "contentPx=${bounds?.let { "${it.widthPx}x${it.heightPx}" } ?: "unknown"} " +
            "pagePx=${bounds?.let { "${it.pageWidthPx}x${it.pageHeightPx}" } ?: "unknown"} " +
            "lineOffsets=$lastLineStart..$lastLineEnd sourceRange=${block.androidEpubSourceRangeLabel()} " +
            "textChars=${block.content.text.length} expectedHeightPx=${block.expectedHeight} $diagnosticsContext"
    )
    return signature
}

@Composable
private fun TextWithEmphasis(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    pageIndex: Int,
    @Suppress("unused") textMeasurer: TextMeasurer,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit,
    block: TextContentBlock,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    @Suppress("unused") onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    pageContentBoundsProvider: (() -> AndroidEpubPageContentBounds?)? = null,
    cutoffDiagnosticsEnabled: Boolean = true,
    cutoffDiagnosticsContext: String = "",
    onRegisterLayout: ((TextLayoutResult, LayoutCoordinates) -> Unit)? = null
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lastCutoffLogSignature by remember { mutableStateOf<String?>(null) }
    val viewConfiguration = LocalViewConfiguration.current
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    var pressedHighlightCfi by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val latestTextLayoutResult = rememberUpdatedState(textLayoutResult)
    val latestOnLinkClick = rememberUpdatedState(onLinkClick)
    val latestOnGeneralTap = rememberUpdatedState(onGeneralTap)
    val displayText = remember(text, isDarkTheme, themeBackgroundColor, themeTextColor, style.color) {
        text.withReaderLinkDisplayStyle(
            isDarkTheme = isDarkTheme,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = style.color.takeIf { it.isSpecified } ?: themeTextColor
        )
    }

    data class EmphasisMarkInfo(val center: Offset, val radius: Float, val color: Color)
    data class UnderlineDrawInfo(val path: Path?, val effect: PathEffect?, val minX: Float, val maxX: Float, val y: Float, val decoStyle: String, val decoColor: Color)

    // --- CACHING DECORATIONS FOR PERFORMANCE ---
    val cachedHighlights = remember(block, userHighlights, textLayoutResult, pressedHighlightCfi) {
        val startTime = System.currentTimeMillis()
        val paths = mutableListOf<Pair<Path, Color>>()
        val layout = textLayoutResult
        if (layout != null && userHighlights.isNotEmpty()) {
            userHighlights.forEach { highlight ->
                val range = getHighlightOffsetsInBlock(block, highlight)
                if (range != null) {
                    try {
                        val blockStartAbs = getTextBlockCharOffset(block)
                        val blockEndAbs = block.endCharOffsetInSource
                            .takeIf { it > blockStartAbs }
                            ?: (blockStartAbs + block.content.text.length)
                        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                            "draw_highlight page=$pageIndex blockCfi=${block.cfi} " +
                                "blockIndex=${block.blockIndex} blockAbs=$blockStartAbs..$blockEndAbs " +
                                "highlightId=${highlight.id} highlightChapter=${highlight.chapterIndex} " +
                                "highlightCfi=${highlight.cfi} range=$range " +
                                "blockText='${highlightDiagSnippet(block.content.text)}'"
                        )
                        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                            "draw_highlight surface=native_or_paginated page=$pageIndex blockIndex=${block.blockIndex} " +
                                "blockCfi=${block.cfi} blockAbs=$blockStartAbs..$blockEndAbs range=$range " +
                                "blockText='${highlightDiagSnippet(block.content.text)}' " +
                                highlight.androidHighlightRenderLabel()
                        )
                        val path = layout.getPathForRange(range.first, range.last + 1)
                        paths.add(path to highlight.color.color.copy(alpha = 0.4f))
                        if (highlight.cfi == pressedHighlightCfi) {
                            paths.add(path to Color.Black.copy(alpha = 0.1f))
                        }
                    } catch (e: Exception) {
                        Timber.tag("DecorationsDiag").e(e, "Highlight path out of bounds")
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated highlight paths for block ${block.blockIndex} in ${duration}ms")
        }
        paths
    }

    val cachedEmphasisMarks = remember(textLayoutResult, text, style.color, density) {
        val startTime = System.currentTimeMillis()
        val marks = mutableListOf<EmphasisMarkInfo>()
        val layout = textLayoutResult
        if (layout != null) {
            val emphasisAnnotations = text.getStringAnnotations("TextEmphasis", 0, text.length)
            if (emphasisAnnotations.isNotEmpty()) {
                with(density) { // Provides the scope for .toPx()
                    emphasisAnnotations.forEach { annotation ->
                        val emphasis = parseEmphasisAnnotation(annotation.item, style.color)
                        val markColor = if (emphasis.color.isSpecified) emphasis.color else style.color
                        val markSize = layout.layoutInput.style.fontSize.toPx() * 0.3f
                        for (offset in annotation.start until annotation.end) {
                            if (offset >= text.text.length || text.text[offset].isWhitespace()) continue
                            try {
                                val boundingBox = layout.getBoundingBox(offset)
                                val center = Offset(
                                    boundingBox.center.x,
                                    if (emphasis.position == "under") boundingBox.bottom + markSize * 0.1f
                                    else boundingBox.top - markSize * 0.1f
                                )
                                marks.add(EmphasisMarkInfo(center, markSize / 2, markColor))
                            } catch (e: Exception) {
                                Timber.tag("DecorationsDiag").e(e, "Emphasis mark out of bounds")
                            }
                        }
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated emphasis marks for block ${block.blockIndex} in ${duration}ms")
        }
        marks
    }

    val cachedUnderlines = remember(textLayoutResult, text, style.color, density) {
        val startTime = System.currentTimeMillis()
        val lines = mutableListOf<UnderlineDrawInfo>()
        val layout = textLayoutResult
        if (layout != null) {
            val customUnderlines = text.getStringAnnotations("CustomUnderline", 0, text.length)
            if (customUnderlines.isNotEmpty()) {
                val maxIdx = maxOf(0, text.length - 1)
                val groupedUnderlines = customUnderlines.groupBy { it.item }
                val mergedUnderlines = mutableListOf<AnnotatedString.Range<String>>()

                groupedUnderlines.forEach { (item, annotations) ->
                    val sorted = annotations.sortedBy { it.start }
                    var currentStart = -1
                    var currentEnd = -1

                    for (ann in sorted) {
                        if (currentStart == -1) {
                            currentStart = ann.start
                            currentEnd = ann.end
                        } else if (ann.start <= currentEnd) {
                            currentEnd = maxOf(currentEnd, ann.end)
                        } else {
                            mergedUnderlines.add(AnnotatedString.Range(item, currentStart, currentEnd))
                            currentStart = ann.start
                            currentEnd = ann.end
                        }
                    }
                    if (currentStart != -1) {
                        mergedUnderlines.add(AnnotatedString.Range(item, currentStart, currentEnd))
                    }
                }

                with(density) {
                    mergedUnderlines.forEach { annotation ->
                        val parts = annotation.item.split('|')
                        val decoStyle = parts.getOrNull(0) ?: "solid"
                        val colorStr = parts.getOrNull(1) ?: "Unspecified"
                        val decoColor = if (colorStr != "Unspecified") Color(colorStr.toULong()) else style.color

                        val safeStart = annotation.start.coerceIn(0, text.length)
                        val safeEnd = annotation.end.coerceIn(0, text.length)
                        if (safeStart < safeEnd) {
                            val startLine = layout.getLineForOffset(safeStart.coerceIn(0, maxIdx))
                            val endLine = layout.getLineForOffset((safeEnd - 1).coerceIn(0, maxIdx))

                            for (line in startLine..endLine) {
                                val lineStart = layout.getLineStart(line)
                                val lineEnd = layout.getLineEnd(line, visibleEnd = true)

                                val intersectionStart = maxOf(safeStart, lineStart)
                                val intersectionEnd = minOf(safeEnd, lineEnd)

                                var actualStart = intersectionStart
                                while (actualStart < intersectionEnd && text[actualStart].isWhitespace()) {
                                    actualStart++
                                }

                                var actualEnd = intersectionEnd
                                while (actualEnd > actualStart && text[actualEnd - 1].isWhitespace()) {
                                    actualEnd--
                                }

                                if (actualStart < actualEnd) {
                                    var minX = Float.POSITIVE_INFINITY
                                    var maxX = Float.NEGATIVE_INFINITY
                                    for (i in actualStart until actualEnd) {
                                        try {
                                            val box = layout.getBoundingBox(i)
                                            minX = minOf(minX, box.left, box.right)
                                            maxX = maxOf(maxX, box.left, box.right)
                                        } catch (e: Exception) {
                                            Timber.tag("DecorationsDiag").e(e, "Underline box out of bounds")
                                        }
                                    }

                                    if (minX < maxX && !minX.isInfinite() && !maxX.isInfinite()) {
                                        val baseline = layout.getLineBaseline(line)
                                        val defaultOffset = layout.layoutInput.style.fontSize.toPx() * 0.1f
                                        val requestedOffset = parts.getOrNull(2)?.toFloatOrNull()?.dp?.toPx()
                                        val y = baseline + (requestedOffset ?: defaultOffset)

                                        var underlinePath: Path? = null
                                        var effect: PathEffect? = null

                                        when (decoStyle) {
                                            "wavy" -> {
                                                underlinePath = Path()
                                                underlinePath.moveTo(minX, y)
                                                val waveLength = 4.dp.toPx()
                                                val amplitude = 1.dp.toPx()
                                                var currentX = minX
                                                var isUp = true

                                                while (currentX < maxX) {
                                                    val nextX = minOf(currentX + waveLength / 2f, maxX)
                                                    val midX = currentX + (nextX - currentX) / 2f
                                                    val cpY = if (isUp) y - amplitude else y + amplitude
                                                    underlinePath.quadraticTo(midX, cpY, nextX, y)
                                                    currentX = nextX
                                                    isUp = !isUp
                                                }
                                            }
                                            "dashed" -> {
                                                effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                                            }
                                            "dotted" -> {
                                                effect = PathEffect.dashPathEffect(floatArrayOf(1f, 4.dp.toPx()))
                                            }
                                        }

                                        lines.add(UnderlineDrawInfo(underlinePath, effect, minX, maxX, y, decoStyle, decoColor))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated custom underlines for block ${block.blockIndex} in ${duration}ms")
        }
        lines
    }

    val customDrawer = Modifier.drawBehind {
        val drawStartTime = System.currentTimeMillis()

        textLayoutResult?.let { layoutResult ->
            if (activeSelection != null) {
                val currentBlockAbs = getTextBlockCharOffset(block)
                val isSelectedOnPage = isBlockSelectedOnPage(block, pageIndex, activeSelection)
                val isStart =
                    pageIndex == activeSelection.startPageIndex &&
                        block.blockIndex == activeSelection.startBlockIndex &&
                        currentBlockAbs == activeSelection.startBlockCharOffset
                val isEnd =
                    pageIndex == activeSelection.endPageIndex &&
                        block.blockIndex == activeSelection.endBlockIndex &&
                        currentBlockAbs == activeSelection.endBlockCharOffset

                if (isSelectedOnPage) {
                    val sOffset = if (isStart) activeSelection.startOffset else 0
                    val eOffset = if (isEnd) activeSelection.endOffset else layoutResult.layoutInput.text.length

                    if (sOffset < eOffset) {
                        try {
                            val path = layoutResult.getPathForRange(sOffset, eOffset)
                            drawPath(path, Color(0xFF1976D2).copy(alpha = 0.3f))
                        } catch (e: Exception) {
                            Timber.tag("DecorationsDiag").e(e, "Highlight path out of bounds")
                        }
                    }
                }
            }

            cachedHighlights.forEach { (path, color) ->
                drawPath(path, color, blendMode = BlendMode.SrcOver)
            }

            cachedEmphasisMarks.forEach { mark ->
                drawCircle(mark.color, mark.radius, mark.center, style = Stroke(1f))
            }

            cachedUnderlines.forEach { line ->
                when (line.decoStyle) {
                    "wavy" -> {
                        line.path?.let { p ->
                            drawPath(p, color = line.decoColor, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    "dashed", "dotted" -> {
                        drawLine(
                            color = line.decoColor,
                            start = Offset(line.minX, line.y),
                            end = Offset(line.maxX, line.y),
                            strokeWidth = if (line.decoStyle == "dotted") 2.dp.toPx() else 1.dp.toPx(),
                            cap = if (line.decoStyle == "dotted") StrokeCap.Round else StrokeCap.Butt,
                            pathEffect = line.effect
                        )
                    }
                    else -> { // Solid or Double
                        drawLine(
                            color = line.decoColor,
                            start = Offset(line.minX, line.y),
                            end = Offset(line.maxX, line.y),
                            strokeWidth = 1.dp.toPx()
                        )
                        if (line.decoStyle == "double") {
                            drawLine(
                                color = line.decoColor,
                                start = Offset(line.minX, line.y + 2.dp.toPx()),
                                end = Offset(line.maxX, line.y + 2.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
        val drawDuration = System.currentTimeMillis() - drawStartTime
        if (drawDuration > 5) {
            Timber.tag("DecorationsDiag").w("Modifier.drawBehind took ${drawDuration}ms for block ${block.blockIndex}")
        }
    }

    fun getHighlightAt(offset: Offset, layout: TextLayoutResult): Pair<UserHighlight, Rect>? {
        if (block.cfi == null) return null

        // Optimization: Quick bounds check
        val charOffset = layout.getOffsetForPosition(offset)
        val lineIndex = layout.getLineForOffset(charOffset)
        val lineLeft = layout.getLineLeft(lineIndex)
        val lineRight = layout.getLineRight(lineIndex)
        if (offset.x < minOf(lineLeft, lineRight) - 50 || offset.x > maxOf(
                lineLeft, lineRight
            ) + 50
        ) {
            return null
        }

        // Iterate highlights reversed (topmost first)
        for (highlight in userHighlights.reversed()) {
            val range = getHighlightOffsetsInBlock(block, highlight) ?: continue

            if (charOffset in range) {
                val blockStartAbs = getTextBlockCharOffset(block)
                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                    "tap_highlight page=$pageIndex blockCfi=${block.cfi} " +
                        "blockIndex=${block.blockIndex} blockAbsStart=$blockStartAbs " +
                        "charOffset=$charOffset absoluteCharOffset=${blockStartAbs + charOffset} " +
                        "highlightId=${highlight.id} highlightCfi=${highlight.cfi} range=$range"
                )
                val path = layout.getPathForRange(range.first, range.last)
                val bounds = path.getBounds()
                return highlight to bounds
            }
        }
        return null
    }

    fun logCutoffIfNeeded(
        layout: TextLayoutResult?,
        coordinates: LayoutCoordinates?,
        pageContentBounds: AndroidEpubPageContentBounds? = pageContentBoundsProvider?.invoke()
    ) {
        if (!cutoffDiagnosticsEnabled) return
        if (layout == null || coordinates == null || !coordinates.isAttached) return
        lastCutoffLogSignature = logAndroidEpubTextCutoffIfNeeded(
            pageIndex = pageIndex,
            block = block,
            layout = layout,
            coordinates = coordinates,
            pageContentBounds = pageContentBounds,
            diagnosticsContext = cutoffDiagnosticsContext,
            previousSignature = lastCutoffLogSignature
        )
    }

    val currentPageContentBounds = pageContentBoundsProvider?.invoke()
    LaunchedEffect(textLayoutResult, layoutCoordinates, currentPageContentBounds) {
        logCutoffIfNeeded(textLayoutResult, layoutCoordinates, currentPageContentBounds)
    }

    Text(text = displayText, style = style, modifier = modifier
        .onGloballyPositioned {
            layoutCoordinates = it
            logCutoffIfNeeded(textLayoutResult, it)
            if (textLayoutResult != null && block.cfi != null) {
                onRegisterLayout?.invoke(textLayoutResult!!, it)
            }
        }
        .then(customDrawer)
        .pointerInput(displayText, viewConfiguration.touchSlop) {
            awaitEachGesture {
                awaitReaderLinkTap(
                    source = "TextWithEmphasis:block=${block.blockIndex}",
                    urlAtPosition = { offset ->
                        latestTextLayoutResult.value?.let { layout ->
                            displayText.readerUrlAnnotationAtPosition(layout, offset)
                        }
                    },
                    touchSlop = viewConfiguration.touchSlop,
                    onLinkClick = { latestOnLinkClick.value(it) }
                )
            }
        }
        .pointerInput(userHighlights, displayText) {
            detectTapGestures(
                onLongPress = { offset ->
                    latestTextLayoutResult.value?.let { layout ->
                        val charOffset = layout.getOffsetForPosition(offset)
                        val wordBoundary = layout.getWordBoundary(charOffset)

                        var start = wordBoundary.start
                        var end = wordBoundary.end

                        val textStr = text.text
                        while (start < end && start < textStr.length && !textStr[start].isLetterOrDigit()) start++
                        while (end > start && end <= textStr.length && !textStr[end - 1].isLetterOrDigit()) end--

                        if (start < end && block.cfi != null) {
                            layoutCoordinates?.let { coords ->
                                if (coords.isAttached) {
                                    val maxIdx = maxOf(0, textStr.length - 1)
                                    val startBox = layout.getBoundingBox(start.coerceIn(0, maxIdx))
                                    val endBox = layout.getBoundingBox((end - 1).coerceIn(0, maxIdx))

                                    val topLeftWin = coords.localToWindow(startBox.topLeft)
                                    val bottomRightWin = coords.localToWindow(endBox.bottomRight)

                                    val selText = textStr.substring(start, end)

                                    val startBlockAbs = when (block) {
                                        is ParagraphBlock -> block.startCharOffsetInSource
                                        is HeaderBlock -> block.startCharOffsetInSource
                                        is QuoteBlock -> block.startCharOffsetInSource
                                        is ListItemBlock -> block.startCharOffsetInSource
                                    }

                                    onSelectionChange(
                                        PaginatedSelection(
                                            startBlockIndex = block.blockIndex,
                                            endBlockIndex = block.blockIndex,
                                            startBaseCfi = block.cfi!!,
                                            endBaseCfi = block.cfi!!,
                                            startOffset = start,
                                            endOffset = end,
                                            text = selText,
                                            rect = Rect(topLeftWin, bottomRightWin),
                                            startPageIndex = pageIndex,
                                            endPageIndex = pageIndex,
                                            startBlockCharOffset = startBlockAbs,
                                            endBlockCharOffset = startBlockAbs,
                                            textPerBlock = mapOf(
                                                buildSelectionBlockKey(
                                                    pageIndex = pageIndex,
                                                    blockIndex = block.blockIndex,
                                                    blockCharOffset = startBlockAbs
                                                ) to selText
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                onTap = { offset ->
                    latestTextLayoutResult.value?.let { layout ->
                        val hit = getHighlightAt(offset, layout)
                        if (hit != null) {
                            val (highlight, localRect) = hit
                            val globalRect = layoutCoordinates?.let { coords ->
                                if (coords.isAttached) {
                                    val topLeft = coords.localToWindow(localRect.topLeft)
                                    val bottomRight = coords.localToWindow(localRect.bottomRight)
                                    Rect(topLeft, bottomRight)
                                } else null
                            } ?: localRect
                            onHighlightClick(highlight, globalRect)
                            return@detectTapGestures
                        }

                        val charOffset = layout.getOffsetForPosition(offset)
                        val url = displayText.readerUrlAnnotationAtPosition(layout, offset)
                        if (url != null) {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "detect_tap_link source=TextWithEmphasis:block=${block.blockIndex} " +
                                    "page=$pageIndex charOffset=$charOffset href=${url.readerLinkDiagPreview()}"
                            )
                            latestOnLinkClick.value(url)
                        } else {
                            latestOnGeneralTap.value(offset)
                        }
                    }
                }
            )
        }, onTextLayout = {
        textLayoutResult = it
        if (displayText.getStringAnnotations("URL", 0, displayText.length).isNotEmpty()) {
            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                "layout_text source=TextWithEmphasis page=$pageIndex block=${block.blockIndex} " +
                    "size=${it.size.width}x${it.size.height} lines=${it.lineCount} " +
                    displayText.readerAnnotatedLinkDiagSummary()
            )
        }
        if (layoutCoordinates != null && block.cfi != null) {
            onRegisterLayout?.invoke(it, layoutCoordinates!!)
        }
        logCutoffIfNeeded(it, layoutCoordinates)
    })
}

@SuppressLint("BinaryOperationInTimber")
private fun checkLayoutMismatch(
    blockIndex: Int,
    blockType: String,
    expectedHeight: Int,
    actualHeight: Int,
    textSnippet: String,
    diagnostics: String = "",
    @Suppress("SameParameterValue") tolerance: Int = 2
) {
    if (expectedHeight == 0) {
        Timber.tag("PAGINATION_MISMATCH").w(
            "Block #$blockIndex ($blockType) has expectedHeight=0. Skipping check. Text: '$textSnippet'" +
                    if (diagnostics.isNotBlank()) "\n -> Diagnostics: $diagnostics" else ""
        )
        return
    }

    if (actualHeight > expectedHeight + tolerance) {
        val diff = actualHeight - expectedHeight
        Timber.tag("PAGINATION_MISMATCH").e(
            "OVERFLOW DETECTED! Block #$blockIndex ($blockType)\n" +
                    " -> Expected: ${expectedHeight}px\n" +
                    " -> Actual:   ${actualHeight}px\n" +
                    " -> Diff:     +${diff}px\n" +
                    " -> Content:  '$textSnippet'" +
                    if (diagnostics.isNotBlank()) "\n -> Diagnostics: $diagnostics" else ""
        )
    }
}

@Suppress("unused")
@SuppressLint("UnusedBoxWithConstraintsScope", "BinaryOperationInTimber")
@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun PaginatedReaderContent(
    uiState: PaginatedReaderUiState,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    isRightToLeftPagination: Boolean = false,
    effectiveBg: Color,
    effectiveText: Color,
    searchQuery: String,
    ttsHighlightInfo: TtsHighlightInfo?,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    onGetPage: (Int) -> Page?,
    onGetChapterIndex: (Int) -> Int?,
    onGetChapterPath: (Int) -> String?,
    onLinkClick: (currentChapterPath: String, href: String, onNavComplete: (Int) -> Unit) -> Unit,
    onInternalLinkNavigated: (Int, Locator?) -> Unit,
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onGetChapterInfo: (Int) -> Pair<String, Int?>?,
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String, SharedReaderLocator) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit,
    isDarkTheme: Boolean,
    pageTextureModifier: Modifier = Modifier,
    pageTextureBitmap: ImageBitmap? = null,
    pageTextureAlpha: Float = 0f
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pageViewConfiguration = LocalViewConfiguration.current
    var showExternalLinkDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val textMeasurer = rememberTextMeasurer()
    var activeSelection by remember { mutableStateOf<PaginatedSelection?>(null) }

    if (showExternalLinkDialog != null) {
        val urlToShow = showExternalLinkDialog!!
        AlertDialog(
            onDismissRequest = { showExternalLinkDialog = null },
            title = { Text(stringResource(R.string.dialog_external_link_title)) },
            text = {
                Text(
                    stringResource(R.string.dialog_external_link_desc, urlToShow)
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(context.getString(R.string.clip_label_copied_link), urlToShow)
                            clipboard.setPrimaryClip(clip)
                            showExternalLinkDialog = null
                        }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, urlToShow.toUri())
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Timber.e(
                                    e, "No activity found to handle intent for URL: $urlToShow"
                                )
                                Toast.makeText(
                                    context, context.getString(R.string.error_no_browser), Toast.LENGTH_LONG
                                ).show()
                            }
                            showExternalLinkDialog = null
                        }) { Text(stringResource(R.string.action_open)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalLinkDialog = null }) { Text(stringResource(R.string.action_cancel)) }
            })
    }

    var pageTurnTouchY by remember { mutableStateOf<Float?>(null) }
    var lastKnownSelectionRect by remember { mutableStateOf<Pair<Rect, Int>?>(null) }

    LaunchedEffect(activeSelection) {
        if (activeSelection != null && activeSelection!!.rect != Rect.Zero) {
            lastKnownSelectionRect = activeSelection!!.rect to pagerState.currentPage
        }
    }

    val blockLayoutMap = remember {
        ReactiveBlockMap()
    }
    var showColorPickerDialog by remember { mutableStateOf<Int?>(null) }

    var showPaletteManager by remember { mutableStateOf(false) }
    var pagerWindowBounds by remember { mutableStateOf(Rect.Zero) }
    val hapticFeedback = LocalHapticFeedback.current
    var isDraggingHandle by remember { mutableStateOf(false) }
    var pendingCrossPageSelection by remember { mutableStateOf<PendingCrossPageSelection?>(null) }
    var crossPageTriggerInfo by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { newPage ->
            if (newPage == previousPage + 1) {
                if (crossPageTriggerInfo != null && crossPageTriggerInfo!!.first == previousPage) {
                    pendingCrossPageSelection = PendingCrossPageSelection(fromPageIndex = previousPage)
                    Timber.d("CrossPageSelection: Strict bottom trigger activated, queued for page $newPage")
                }
            } else if (newPage != previousPage) {
                pendingCrossPageSelection = null
            }
            crossPageTriggerInfo = null
            previousPage = newPage
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Timber.tag("ReflowPaginationDiag").d("PaginatedReaderContent: isLoading=false, totalPageCount=${uiState.totalPageCount}")
        if (uiState.totalPageCount > 0) {
            uiState.generation

            var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }

            val magnifierModifier = if (magnifierCenter.isSpecified) {
                Modifier.magnifier(
                    sourceCenter = { magnifierCenter },
                    zoom = 1.5f,
                    size = DpSize(140.dp, 48.dp),
                    cornerRadius = 24.dp,
                    elevation = 4.dp
                )
            } else Modifier

            Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }.then(magnifierModifier)) {
                run {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().onGloballyPositioned { coords ->
                            pagerWindowBounds =
                                Rect(coords.positionInWindow(), coords.size.toSize())
                        }.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = event.changes.firstOrNull { it.pressed }
                                    if (down != null) {
                                        pageTurnTouchY = down.position.y
                                    }
                                }
                            }
                        },
                        beyondViewportPageCount = 1,
                        reverseLayout = isRightToLeftPagination
                    ) { pageIndex ->
                        val pageOffset =
                            (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                        val zIndex = -pageOffset

                        val pageModifier = if (isPageTurnAnimationEnabled) {
                            Modifier.zIndex(zIndex).realisticBookPage(
                                pagerState,
                                pageIndex,
                                effectiveBg,
                                isDarkTheme,
                                pageTurnTouchY,
                                pageTextureBitmap,
                                pageTextureAlpha
                            )
                        } else Modifier

                        var pageContent by remember { mutableStateOf<Page?>(null) }
                        var currentChapterPath by remember { mutableStateOf<String?>(null) }
                        var pageLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        val pageChapterIndex = onGetChapterIndex(pageIndex)
                        val pageUserHighlights = highlightsForPaginatedPage(
                            pageChapterIndex = pageChapterIndex,
                            userHighlights = userHighlights
                        )
                        val themedPageContent = remember(pageContent, isDarkTheme, effectiveBg, effectiveText) {
                            pageContent?.applyReaderThemeForDisplay(
                                isDarkTheme = isDarkTheme,
                                themeBackgroundColor = effectiveBg,
                                themeTextColor = effectiveText
                            )
                        }

                        if (pageUserHighlights.size != userHighlights.size) {
                            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                "page_scope page=$pageIndex pageChapter=$pageChapterIndex " +
                                    "inputHighlightCount=${userHighlights.size} " +
                                    "pageHighlightCount=${pageUserHighlights.size} " +
                                    "inputHighlightChapters=${userHighlights.map { it.chapterIndex }.distinct()}"
                            )
                        }

                        LaunchedEffect(pageIndex, uiState.generation) {
                            if (DEBUG_PAGE_TURN_DIAG) {
                                Timber.tag("PageTurnDiag").d("Page $pageIndex: Starting content fetch")
                            }
                            val fetchStartTime = if (DEBUG_PAGE_TURN_DIAG) System.currentTimeMillis() else 0L

                            pageContent = onGetPage(pageIndex)

                            if (DEBUG_PAGE_TURN_DIAG) {
                                val fetchDuration = System.currentTimeMillis() - fetchStartTime
                                Timber.tag("PageTurnDiag").d("Page $pageIndex: Content fetched in ${fetchDuration}ms")
                            }

                            onGetChapterPath(pageIndex)?.let { currentChapterPath = it }
                        }

                        LaunchedEffect(pageIndex, pageChapterIndex, currentChapterPath, themedPageContent) {
                            val page = themedPageContent ?: return@LaunchedEffect
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "page_render page=$pageIndex chapter=$pageChapterIndex " +
                                    "chapterPath=${currentChapterPath.orEmpty().readerLinkDiagPreview()} " +
                                    page.readerPageLinkDiagSummary()
                            )
                        }

                        val textBlocksOnPage =
                            themedPageContent?.content?.extractTextBlocks()
                                ?.filter { it.cfi != null } ?: emptyList()
                        val lastTextBlock = textBlocksOnPage.lastOrNull()
                        val lastBlockAbs = lastTextBlock?.let {
                            when (it) {
                                is ParagraphBlock -> it.startCharOffsetInSource
                                is HeaderBlock -> it.startCharOffsetInSource
                                is QuoteBlock -> it.startCharOffsetInSource
                                is ListItemBlock -> it.startCharOffsetInSource
                            }
                        }

                        LaunchedEffect(activeSelection, lastTextBlock, isDraggingHandle) {
                            if (isDraggingHandle && activeSelection != null && lastTextBlock != null &&
                                activeSelection!!.endPageIndex == pageIndex &&
                                activeSelection!!.endBlockIndex == lastTextBlock.blockIndex &&
                                activeSelection!!.endBlockCharOffset == lastBlockAbs) {
                                if (activeSelection!!.endOffset >= lastTextBlock.content.text.length - 3) {
                                    if (crossPageTriggerInfo?.first != pageIndex) {
                                        Timber.tag("TextSelectionDiag")
                                            .d("Cross-page trigger ACTIVATED. Selection at bottom-right of page $pageIndex.")
                                        crossPageTriggerInfo = pageIndex to lastTextBlock.cfi!!
                                    }
                                    return@LaunchedEffect
                                }
                            }
                            if (!isDraggingHandle && activeSelection == null && crossPageTriggerInfo?.first == pageIndex) {
                                Timber.tag("TextSelectionDiag")
                                    .d("Cross-page trigger CLEARED on page $pageIndex (Custom).")
                                crossPageTriggerInfo = null
                            }
                        }

                        // Smart Cross-page selection logic
                        LaunchedEffect(pendingCrossPageSelection, pageContent) {
                            val pending = pendingCrossPageSelection ?: return@LaunchedEffect
                            if (pageIndex != pending.fromPageIndex + 1) return@LaunchedEffect
                            val content = pageContent ?: return@LaunchedEffect

                            val firstTextBlock =
                                content.content.extractTextBlocks()
                                    .firstOrNull { it.cfi != null } ?: run {
                                    pendingCrossPageSelection = null
                                    return@LaunchedEffect
                                }

                            var layoutInfo: Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? =
                                null
                            for (i in 0 until 20) {
                                layoutInfo = blockLayoutMap["${firstTextBlock.cfi}_$pageIndex"]
                                if (layoutInfo != null && layoutInfo.second.isAttached) break
                                delay(50)
                            }

                            if (layoutInfo == null || !layoutInfo.second.isAttached) {
                                pendingCrossPageSelection = null
                                return@LaunchedEffect
                            }

                            val text = firstTextBlock.content.text
                            if (text.isEmpty()) {
                                pendingCrossPageSelection = null
                                return@LaunchedEffect
                            }

                            // Smart boundary logic (>10 chars & ends at a word)
                            var endIndex = minOf(text.length, 10)
                            if (text.length > 10) {
                                for (i in 10 until text.length) {
                                    if (text[i].isWhitespace() || !text[i].isLetterOrDigit()) {
                                        endIndex = i
                                        break
                                    }
                                }
                            }

                            try {
                                val path = layoutInfo.first.getPathForRange(0, endIndex)
                                val localRect = path.getBounds()
                                val windowTopLeft =
                                    layoutInfo.second.localToWindow(localRect.topLeft)
                                val windowBottomRight =
                                    layoutInfo.second.localToWindow(localRect.bottomRight)

                                val previousSel = activeSelection

                                val firstTextBlockAbs = when (firstTextBlock) {
                                    is ParagraphBlock -> firstTextBlock.startCharOffsetInSource
                                    is HeaderBlock -> firstTextBlock.startCharOffsetInSource
                                    is QuoteBlock -> firstTextBlock.startCharOffsetInSource
                                    is ListItemBlock -> firstTextBlock.startCharOffsetInSource
                                }

                                val newTextPerBlock = (previousSel?.textPerBlock ?: emptyMap()).toMutableMap()
                                newTextPerBlock[
                                    buildSelectionBlockKey(
                                        pageIndex = pageIndex,
                                        blockIndex = firstTextBlock.blockIndex,
                                        blockCharOffset = firstTextBlockAbs
                                    )
                                ] = text.substring(0, endIndex)

                                val newText = newTextPerBlock.entries
                                    .sortedWith { first, second ->
                                        compareSelectionBlockKeys(first.key, second.key)
                                    }
                                    .joinToString(" ") { it.value }

                                activeSelection = PaginatedSelection(
                                    startBlockIndex = previousSel?.startBlockIndex ?: firstTextBlock.blockIndex,
                                    endBlockIndex = firstTextBlock.blockIndex,
                                    startBaseCfi = previousSel?.startBaseCfi ?: firstTextBlock.cfi!!,
                                    endBaseCfi = firstTextBlock.cfi!!,
                                    startOffset = previousSel?.startOffset ?: 0,
                                    endOffset = endIndex,
                                    text = newText,
                                    rect = Rect(windowTopLeft, windowBottomRight),
                                    startPageIndex = previousSel?.startPageIndex ?: pending.fromPageIndex,
                                    endPageIndex = pageIndex,
                                    startBlockCharOffset = previousSel?.startBlockCharOffset ?: firstTextBlockAbs,
                                    endBlockCharOffset = firstTextBlockAbs,
                                    textPerBlock = newTextPerBlock
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "CrossPageSelection: Failed to create selection")
                            }

                            pendingCrossPageSelection = null
                        }

                        val onGeneralTapCallback: (Offset) -> Unit = { offset ->
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "page_general_tap source=content page=$pageIndex x=${offset.x.roundToInt()} y=${offset.y.roundToInt()}"
                            )
                            activeSelection = null
                            onTap(offset)
                        }
                        val onLinkClickCallback: (String) -> Unit = { href ->
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "link_click_callback page=$pageIndex currentPagerPage=${pagerState.currentPage} " +
                                    "chapterPath=${currentChapterPath.orEmpty().readerLinkDiagPreview()} " +
                                    "href=${href.readerLinkDiagPreview()}"
                            )
                            if (href.isReaderExternalHref()) {
                                Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                    "external_link_dialog href=${href.readerLinkDiagPreview()}"
                                )
                                showExternalLinkDialog = href.readerExternalHrefForDisplay()
                            } else {
                                val path = currentChapterPath
                                if (path == null) {
                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).w(
                                        "internal_link_dropped reason=missing_current_chapter_path href=${href.readerLinkDiagPreview()}"
                                    )
                                } else {
                                    onLinkClick(path, href) { targetPageIndex ->
                                        onInternalLinkNavigated(targetPageIndex, null)
                                        coroutineScope.launch {
                                            Timber.tag(TAG_STABLE_PAGE_NAV).d(
                                                "link_scroll targetPage=$targetPageIndex currentPage=${pagerState.currentPage}"
                                            )
                                            pagerState.scrollToPage(targetPageIndex)
                                        }
                                    }
                                }
                            }
                        }
                        val latestPageLayoutCoordinates = rememberUpdatedState(pageLayoutCoordinates)
                        val latestOnLinkClickCallback = rememberUpdatedState(onLinkClickCallback)
                        val pageHorizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
                        val pageVerticalPaddingPx = with(density) { verticalPadding.roundToPx() }
                        val pageContentBoundsProvider = {
                            pageLayoutCoordinates
                                ?.takeIf { it.isAttached }
                                ?.androidEpubPageContentBounds(
                                    horizontalPaddingPx = pageHorizontalPaddingPx,
                                    verticalPaddingPx = pageVerticalPaddingPx
                                )
                        }
                        val cutoffLogSignatures = remember(pageIndex, uiState.generation) {
                            mutableStateMapOf<String, Boolean>()
                        }
                        val cutoffDiagnosticsEnabled = !uiState.isLoading
                        val cutoffDiagnosticsContext =
                            "generation=${uiState.generation} loading=${uiState.isLoading} pageCount=${uiState.totalPageCount}"

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(effectiveBg)
                                .then(pageTextureModifier)
                                .then(pageModifier)
                                .onGloballyPositioned { pageLayoutCoordinates = it }
                                .pointerInput(pageIndex, pageViewConfiguration.touchSlop) {
                                    awaitEachGesture {
                                        awaitReaderLinkTap(
                                            source = "PageLinkInterceptor:page=$pageIndex",
                                            urlAtPosition = { offset ->
                                                val hit = latestPageLayoutCoordinates.value
                                                    ?.takeIf { it.isAttached }
                                                    ?.let { coordinates ->
                                                        blockLayoutMap.readerLinkAtPagePosition(
                                                            pageCoordinates = coordinates,
                                                            pageIndex = pageIndex,
                                                            position = offset
                                                        )
                                                    }
                                                if (hit != null) {
                                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                                        "page_link_interceptor_hit page=$pageIndex block=${hit.blockIndex} " +
                                                            "cfi=${hit.cfi.orEmpty().readerLinkDiagPreview()} " +
                                                            "href=${hit.href.readerLinkDiagPreview()}"
                                                    )
                                                }
                                                hit?.href
                                            },
                                            touchSlop = pageViewConfiguration.touchSlop,
                                            onLinkClick = { latestOnLinkClickCallback.value(it) }
                                        )
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                                "page_general_tap source=background page=$pageIndex " +
                                                    "x=${offset.x.roundToInt()} y=${offset.y.roundToInt()}"
                                            )
                                            activeSelection = null
                                            onTap(offset)
                                        })
                                })
                                Box(modifier = Modifier.fillMaxSize().padding(
                                    horizontal = horizontalPadding,
                                    vertical = verticalPadding
                                ), contentAlignment = Alignment.TopStart) {
                                    if (themedPageContent != null) {
                                        val displayPage = themedPageContent

                                        // Measure page blocks at their natural height; pagination, not Column, owns page breaks.
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(unbounded = true)
                                        ) {
                                            val searchHighlightColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            val ttsHighlightColor =
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)

                                            displayPage.content.forEach { block ->
                                                val marginModifier = Modifier.padding(
                                                    top = block.style.margin.top.coerceAtLeast(0.dp),
                                                    bottom = block.style.margin.bottom.coerceAtLeast(
                                                        0.dp
                                                    )
                                                )

                                                val alignModifier =
                                                    if (block.style.horizontalAlign == "center") {
                                                        Modifier.align(Alignment.CenterHorizontally)
                                                    } else {
                                                        Modifier.padding(
                                                            start = block.style.margin.left.coerceAtLeast(
                                                                0.dp
                                                            ),
                                                            end = block.style.margin.right.coerceAtLeast(
                                                                0.dp
                                                            )
                                                        )
                                                    }

                                                val widthModifier =
                                                    if (block.style.width != Dp.Unspecified) {
                                                        Modifier.width(block.style.width)
                                                    } else {
                                                        Modifier.fillMaxWidth()
                                                    }.then(
                                                        Modifier.widthIn(
                                                            min = block.style.minWidth.takeIf { it.isSpecified && it > 0.dp } ?: Dp.Unspecified,
                                                            max = block.style.maxWidth.takeIf { it.isSpecified && it > 0.dp } ?: Dp.Unspecified
                                                        )
                                                    )

                                                val styleModifier =
                                                    alignModifier.then(if (block.style.horizontalAlign == "center") widthModifier else Modifier)
                                                        .drawCssBorders(
                                                            blockStyle = block.style,
                                                            density = density
                                                        )
                                                        .then(if (block.style.visibility == "hidden") Modifier.graphicsLayer(alpha = 0f) else Modifier)

                                                val diagnosticModifier =
                                                    Modifier.onGloballyPositioned { coordinates ->
                                                        val actualHeight =
                                                            coordinates.size.height
                                                        if (cutoffDiagnosticsEnabled) {
                                                            logAndroidEpubBlockOverflowIfNeeded(
                                                                pageIndex = pageIndex,
                                                                block = block,
                                                                coordinates = coordinates,
                                                                pageContentBounds = pageContentBoundsProvider(),
                                                                diagnosticsContext = cutoffDiagnosticsContext,
                                                                signatureAlreadyLogged = { signature ->
                                                                    cutoffLogSignatures[signature] == true
                                                                },
                                                                markSignatureLogged = { signature ->
                                                                    cutoffLogSignatures[signature] = true
                                                                }
                                                            )
                                                        }
                                                        if (block.expectedHeight > 0) {
                                                            val snippet = when (block) {
                                                                is ParagraphBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is HeaderBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is QuoteBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is ListItemBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is TextContentBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                else -> "Non-text content"
                                                            }

                                                            checkLayoutMismatch(
                                                                blockIndex = block.blockIndex,
                                                                blockType = block::class.simpleName
                                                                    ?: "Block",
                                                                expectedHeight = block.expectedHeight,
                                                                actualHeight = actualHeight,
                                                                textSnippet = snippet,
                                                                diagnostics = buildString {
                                                                    append("page=")
                                                                    append(pageIndex)
                                                                    append(", width=")
                                                                    append(coordinates.size.width)
                                                                    append("px, styleWidth=")
                                                                    append(block.style.width)
                                                                    append(", maxWidth=")
                                                                    append(block.style.maxWidth)
                                                                    append(", margin=")
                                                                    append(block.style.margin)
                                                                    append(", padding=")
                                                                    append(block.style.padding)
                                                                    append(", borders=(")
                                                                    append(block.style.borderLeft?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderTop?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderRight?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderBottom?.width ?: 0.dp)
                                                                    append(")")
                                                                    when (block) {
                                                                        is ParagraphBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is HeaderBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is QuoteBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is ListItemBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                        }

                                                                        is TextContentBlock -> {
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                        }

                                                                        else -> Unit
                                                                    }
                                                                },
                                                                tolerance = 2
                                                            )
                                                        }
                                                    }.then(marginModifier).then(styleModifier)

                                                Box(modifier = diagnosticModifier.androidEpubNaturalHeight()) {
                                                    val paddingModifier = Modifier.padding(
                                                        start = block.style.padding.left.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderLeft?.width ?: 0.dp),
                                                        top = block.style.padding.top.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderTop?.width ?: 0.dp),
                                                        end = block.style.padding.right.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderRight?.width
                                                            ?: 0.dp),
                                                        bottom = block.style.padding.bottom.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderBottom?.width
                                                            ?: 0.dp)
                                                    ).then(
                                                        if (block.style.horizontalAlign != "center") widthModifier else Modifier.fillMaxWidth()
                                                    )

                                                    block.style.backgroundImage
                                                        ?.trim()
                                                        ?.takeIf { it.isNotBlank() && !it.contains("gradient(", ignoreCase = true) }
                                                        ?.let { backgroundImagePath ->
                                                            val backgroundFile = remember(backgroundImagePath) { File(backgroundImagePath) }
                                                            AsyncImage(
                                                                model = if (backgroundFile.exists()) backgroundFile else backgroundImagePath,
                                                                contentDescription = null,
                                                                modifier = Modifier.matchParentSize(),
                                                                contentScale = imageContentScale(block.style)
                                                            )
                                                        }

                                                    @Suppress("DEPRECATION") when (block) {
                                                        is ParagraphBlock -> {
                                                            val paragraphStyle = textStyle.copy(
                                                                textAlign = block.textAlign
                                                                    ?: textStyle.textAlign
                                                            )
                                                            val searchHighlighted =
                                                                highlightQueryInText(
                                                                    block.content,
                                                                    searchQuery,
                                                                    searchHighlightColor
                                                                )
                                                            val finalContent =
                                                                if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                    buildAnnotatedString {
                                                                        append(searchHighlighted)

                                                                        // Define absolute ranges
                                                                        val blockStartAbs =
                                                                            block.startCharOffsetInSource
                                                                        val blockEndAbs =
                                                                            block.startCharOffsetInSource + searchHighlighted.length
                                                                        val highlightStartAbs =
                                                                            ttsHighlightInfo.offset
                                                                        val highlightEndAbs =
                                                                            ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                        // Calculate intersection
                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
                                                                            )

                                                                        // Check for overlap and apply
                                                                        // style
                                                                        if (intersectionStartAbs < intersectionEndAbs) {
                                                                            val highlightStartRelative =
                                                                                intersectionStartAbs - blockStartAbs
                                                                            val highlightEndRelative =
                                                                                intersectionEndAbs - blockStartAbs
                                                                            addStyle(
                                                                                style = SpanStyle(
                                                                                    background = ttsHighlightColor
                                                                                ),
                                                                                start = highlightStartRelative,
                                                                                end = highlightEndRelative
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    searchHighlighted
                                                                }

                                                            @Suppress(
                                                                "UnusedVariable",
                                                                "Unused"
                                                            ) val diagnosticModifier =
                                                                if (block.textAlign == TextAlign.Justify) {
                                                                    Modifier.onGloballyPositioned { coordinates ->
                                                                        val width =
                                                                            coordinates.size.width
                                                                        Timber.d(
                                                                            """
                                                                [UI Render]
                                                                Block Index: ${block.blockIndex}
                                                                Text Start: ${
                                                                                block.content.text.take(
                                                                                    20
                                                                                )
                                                                            }...
                                                                Actual Render Width Px: $width
                                                                ------------------------------------------------
                                                            """.trimIndent()
                                                                        )
                                                                    }
                                                                } else {
                                                                    Modifier
                                                                }

                                                            TextWithEmphasis(
                                                                text = finalContent,
                                                                style = paragraphStyle,
                                                                modifier = paddingModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = pageUserHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(highlight.cfi)
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                pageContentBoundsProvider = pageContentBoundsProvider,
                                                                cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is HeaderBlock -> {
                                                            val style = createHeaderTextStyle(
                                                                baseStyle = textStyle,
                                                                level = block.level,
                                                                textAlign = block.textAlign
                                                            )
                                                            val searchHighlighted =
                                                                highlightQueryInText(
                                                                    block.content,
                                                                    searchQuery,
                                                                    searchHighlightColor
                                                                )
                                                            val finalContent =
                                                                if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                    buildAnnotatedString {
                                                                        append(searchHighlighted)

                                                                        val blockStartAbs =
                                                                            block.startCharOffsetInSource
                                                                        val blockEndAbs =
                                                                            block.startCharOffsetInSource + searchHighlighted.length
                                                                        val highlightStartAbs =
                                                                            ttsHighlightInfo.offset
                                                                        val highlightEndAbs =
                                                                            ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
                                                                            )

                                                                        if (intersectionStartAbs < intersectionEndAbs) {
                                                                            val highlightStartRelative =
                                                                                intersectionStartAbs - blockStartAbs
                                                                            val highlightEndRelative =
                                                                                intersectionEndAbs - blockStartAbs
                                                                            addStyle(
                                                                                style = SpanStyle(
                                                                                    background = ttsHighlightColor
                                                                                ),
                                                                                start = highlightStartRelative,
                                                                                end = highlightEndRelative
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    searchHighlighted
                                                                }
                                                            TextWithEmphasis(
                                                                text = finalContent,
                                                                style = style,
                                                                modifier = paddingModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = pageUserHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(
                                                                        highlight.cfi
                                                                    )
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                pageContentBoundsProvider = pageContentBoundsProvider,
                                                                cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is QuoteBlock -> {
                                                            val quoteStyle = textStyle.copy(
                                                                textAlign = block.textAlign
                                                                    ?: textStyle.textAlign
                                                            )
                                                            val quoteModifier =
                                                                paddingModifier.padding(start = 16.dp)
                                                            val searchHighlighted =
                                                                highlightQueryInText(
                                                                    block.content,
                                                                    searchQuery,
                                                                    searchHighlightColor
                                                                )
                                                            val finalContent =
                                                                if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                    buildAnnotatedString {
                                                                        append(searchHighlighted)

                                                                        val blockStartAbs =
                                                                            block.startCharOffsetInSource
                                                                        val blockEndAbs =
                                                                            block.startCharOffsetInSource + searchHighlighted.length
                                                                        val highlightStartAbs =
                                                                            ttsHighlightInfo.offset
                                                                        val highlightEndAbs =
                                                                            ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
                                                                            )

                                                                        if (intersectionStartAbs < intersectionEndAbs) {
                                                                            val highlightStartRelative =
                                                                                intersectionStartAbs - blockStartAbs
                                                                            val highlightEndRelative =
                                                                                intersectionEndAbs - blockStartAbs
                                                                            addStyle(
                                                                                style = SpanStyle(
                                                                                    background = ttsHighlightColor
                                                                                ),
                                                                                start = highlightStartRelative,
                                                                                end = highlightEndRelative
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    searchHighlighted
                                                                }
                                                            TextWithEmphasis(
                                                                text = finalContent,
                                                                style = quoteStyle,
                                                                modifier = quoteModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = pageUserHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(highlight.cfi)
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                pageContentBoundsProvider = pageContentBoundsProvider,
                                                                cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is ListItemBlock -> {
                                                            Row(
                                                                modifier = paddingModifier,
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                val markerAreaModifier =
                                                                    Modifier.width(32.dp)
                                                                        .padding(end = 8.dp)
                                                                val itemMarkerImage = block.itemMarkerImage
                                                                val itemMarker = block.itemMarker

                                                                if (itemMarkerImage != null) {
                                                                    val imageRequest =
                                                                        Builder(LocalContext.current).data(
                                                                            File(
                                                                                itemMarkerImage
                                                                            )
                                                                        ).crossfade(true).build()
                                                                    val imageSize = with(density) {
                                                                        (textStyle.fontSize.value * 0.8f).sp.toDp()
                                                                    }

                                                                    AsyncImage(
                                                                        model = imageRequest,
                                                                        contentDescription = stringResource(R.string.content_desc_list_item_marker),
                                                                        modifier = markerAreaModifier.height(
                                                                            imageSize
                                                                        ),
                                                                        alignment = Alignment.CenterEnd,
                                                                        contentScale = ContentScale.FillHeight
                                                                    )
                                                                } else if (itemMarker != null) {
                                                                    Text(
                                                                        text = itemMarker,
                                                                        style = textStyle.copy(
                                                                            textAlign = TextAlign.End
                                                                        ),
                                                                        modifier = markerAreaModifier
                                                                    )
                                                                }
                                                                val searchHighlighted =
                                                                    highlightQueryInText(
                                                                        block.content,
                                                                        searchQuery,
                                                                        searchHighlightColor
                                                                    )
                                                                val finalContent =
                                                                    if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                        buildAnnotatedString {
                                                                            append(searchHighlighted)

                                                                            val blockStartAbs =
                                                                                block.startCharOffsetInSource
                                                                            val blockEndAbs =
                                                                                block.startCharOffsetInSource + searchHighlighted.length
                                                                            val highlightStartAbs =
                                                                                ttsHighlightInfo.offset
                                                                            val highlightEndAbs =
                                                                                ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                            val intersectionStartAbs =
                                                                                maxOf(
                                                                                    blockStartAbs,
                                                                                    highlightStartAbs
                                                                                )
                                                                            val intersectionEndAbs =
                                                                                minOf(
                                                                                    blockEndAbs,
                                                                                    highlightEndAbs
                                                                                )

                                                                            if (intersectionStartAbs < intersectionEndAbs) {
                                                                                val highlightStartRelative =
                                                                                    intersectionStartAbs - blockStartAbs
                                                                                val highlightEndRelative =
                                                                                    intersectionEndAbs - blockStartAbs
                                                                                addStyle(
                                                                                    style = SpanStyle(
                                                                                        background = ttsHighlightColor
                                                                                    ),
                                                                                    start = highlightStartRelative,
                                                                                    end = highlightEndRelative
                                                                                )
                                                                            }
                                                                        }
                                                                    } else {
                                                                        searchHighlighted
                                                                    }
                                                                TextWithEmphasis(
                                                                    text = finalContent,
                                                                    style = textStyle,
                                                                    modifier = Modifier.weight(1f),
                                                                    pageIndex = pageIndex,
                                                                    textMeasurer = textMeasurer,
                                                                    onLinkClick = onLinkClickCallback,
                                                                    onGeneralTap = onGeneralTapCallback,
                                                                    block = block,
                                                                    userHighlights = pageUserHighlights,
                                                                    activeSelection = activeSelection,
                                                                    onSelectionChange = { sel ->
                                                                        activeSelection = sel
                                                                    },
                                                                    onHighlightClick = { highlight, _ ->
                                                                        onNoteRequested(highlight.cfi)
                                                                        activeSelection = null
                                                                    },
                                                                    isDarkTheme = isDarkTheme,
                                                                    themeBackgroundColor = effectiveBg,
                                                                    themeTextColor = effectiveText,
                                                                    pageContentBoundsProvider = pageContentBoundsProvider,
                                                                    cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                    cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                    onRegisterLayout = { layout, coords ->
                                                                        if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                            Triple(
                                                                                layout,
                                                                                coords,
                                                                                block
                                                                            )
                                                                    })
                                                            }
                                                        }

                                                        is WrappingContentBlock -> {
                                                            WrappingContentLayout(
                                                                block = block,
                                                                textStyle = textStyle,
                                                                imageSizeMultiplier = imageSizeMultiplier,
                                                                modifier = paddingModifier,
                                                                searchQuery = searchQuery,
                                                                ttsHighlightInfo = ttsHighlightInfo,
                                                                searchHighlightColor = searchHighlightColor,
                                                                ttsHighlightColor = ttsHighlightColor,
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback
                                                            )
                                                        }

                                                        is FlexContainerBlock -> {

                                                            if (block.style.flexDirection == "row") {
                                                                val horizontalArrangement =
                                                                    when (block.style.justifyContent) {
                                                                        "center" -> Arrangement.Center
                                                                        "flex-end" -> Arrangement.End
                                                                        "space-between" -> Arrangement.SpaceBetween
                                                                        "space-around" -> Arrangement.SpaceAround
                                                                        else -> Arrangement.Start
                                                                    }
                                                                val verticalAlignment =
                                                                    when (block.style.alignItems) {
                                                                        "center" -> Alignment.CenterVertically
                                                                        "flex-end" -> Alignment.Bottom
                                                                        else -> Alignment.Top
                                                                    }
                                                                Row(
                                                                    modifier = paddingModifier.fillMaxWidth(),
                                                                    horizontalArrangement = horizontalArrangement,
                                                                    verticalAlignment = verticalAlignment
                                                                ) {
                                                                    block.children.forEach { childBlock ->
                                                                        RenderFlexChildBlock(
                                                                            childBlock = childBlock,
                                                                            textStyle = textStyle,
                                                                            imageSizeMultiplier = imageSizeMultiplier,
                                                                            searchQuery = searchQuery,
                                                                            searchHighlightColor = searchHighlightColor,
                                                                            ttsHighlightInfo = ttsHighlightInfo,
                                                                            ttsHighlightColor = ttsHighlightColor,
                                                                            textMeasurer = textMeasurer,
                                                                            onLinkClickCallback = onLinkClickCallback,
                                                                            onGeneralTapCallback = onGeneralTapCallback,
                                                                            userHighlights = pageUserHighlights,
                                                                            activeSelection = activeSelection,
                                                                            onSelectionChange = { sel ->
                                                                                activeSelection =
                                                                                    sel
                                                                            },
                                                                            onHighlightClick = { highlight, _ ->
                                                                                onNoteRequested(
                                                                                    highlight.cfi
                                                                                )
                                                                                activeSelection =
                                                                                    null
                                                                            },
                                                                            isDarkTheme = isDarkTheme,
                                                                            themeBackgroundColor = effectiveBg,
                                                                            themeTextColor = effectiveText,
                                                                            blockLayoutMap = blockLayoutMap,
                                                                            density = density,
                                                                            imageLoader = imageLoader,
                                                                            pageIndex = pageIndex
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                val verticalArrangement =
                                                                    when (block.style.justifyContent) {
                                                                        "center" -> Arrangement.Center
                                                                        "flex-end" -> Arrangement.Bottom
                                                                        "space-between" -> Arrangement.SpaceBetween
                                                                        "space-around" -> Arrangement.SpaceAround
                                                                        else -> Arrangement.Top
                                                                    }
                                                                val horizontalAlignment =
                                                                    when (block.style.alignItems) {
                                                                        "center" -> Alignment.CenterHorizontally
                                                                        "flex-end" -> Alignment.End
                                                                        else -> Alignment.Start
                                                                    }
                                                                Column(
                                                                    modifier = paddingModifier.fillMaxWidth(),
                                                                    verticalArrangement = verticalArrangement,
                                                                    horizontalAlignment = horizontalAlignment
                                                                ) {
                                                                    block.children.forEach { childBlock ->
                                                                        RenderFlexChildBlock(
                                                                            childBlock = childBlock,
                                                                            textStyle = textStyle,
                                                                            imageSizeMultiplier = imageSizeMultiplier,
                                                                            searchQuery = searchQuery,
                                                                            searchHighlightColor = searchHighlightColor,
                                                                            ttsHighlightInfo = ttsHighlightInfo,
                                                                            ttsHighlightColor = ttsHighlightColor,
                                                                            textMeasurer = textMeasurer,
                                                                            onLinkClickCallback = onLinkClickCallback,
                                                                            onGeneralTapCallback = onGeneralTapCallback,
                                                                            userHighlights = pageUserHighlights,
                                                                            activeSelection = activeSelection,
                                                                            onSelectionChange = { sel ->
                                                                                activeSelection =
                                                                                    sel
                                                                            },
                                                                            onHighlightClick = { highlight, _ ->
                                                                                onNoteRequested(
                                                                                    highlight.cfi
                                                                                )
                                                                                activeSelection =
                                                                                    null
                                                                            },
                                                                            isDarkTheme = isDarkTheme,
                                                                            themeBackgroundColor = effectiveBg,
                                                                            themeTextColor = effectiveText,
                                                                            blockLayoutMap = blockLayoutMap,
                                                                            density = density,
                                                                            imageLoader = imageLoader,
                                                                            pageIndex = pageIndex
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        is MathBlock -> {
                                                            val svgContent = block.svgContent?.takeIf { it.isNotBlank() }
                                                            Timber.d(
                                                                "PaginatedReader: Rendering MathBlock. Alt: '${block.altText}', Has SVG: ${svgContent != null}"
                                                            )
                                                            if (svgContent != null) {
                                                                val nonBlankSvgContent = svgContent
                                                                BoxWithConstraints(
                                                                    modifier = paddingModifier
                                                                ) {
                                                                    val localDensity =
                                                                        LocalDensity.current
                                                                    val fontSizePx =
                                                                        with(localDensity) {
                                                                            textStyle.fontSize.toPx()
                                                                        }
                                                                    val containerWidthPx =
                                                                        with(localDensity) {
                                                                            maxWidth.roundToPx()
                                                                        }
                                                                    val widthPx = parseSvgDimension(
                                                                        block.svgWidth,
                                                                        fontSizePx,
                                                                        containerWidthPx,
                                                                        localDensity
                                                                    )
                                                                    val heightPx =
                                                                        parseSvgDimension(
                                                                            block.svgHeight,
                                                                            fontSizePx,
                                                                            containerWidthPx,
                                                                            localDensity
                                                                        )

                                                                    var imageModifier: Modifier =
                                                                        Modifier
                                                                    if (widthPx != null) {
                                                                        val finalWidthDp =
                                                                            with(localDensity) { widthPx.toDp() }
                                                                        Timber.d("Applying calculated width to MathBlock image: $finalWidthDp")
                                                                        imageModifier =
                                                                            imageModifier.width(
                                                                                finalWidthDp
                                                                            )
                                                                    } else {
                                                                        Timber.w("Could not calculate a specific width for MathBlock. It will fill available space.")
                                                                        imageModifier =
                                                                            imageModifier.fillMaxWidth()
                                                                    }

                                                                    if (heightPx != null) {
                                                                        val finalHeightDp =
                                                                            with(localDensity) { heightPx.toDp() }
                                                                        Timber.d("Applying calculated height to MathBlock image: $finalHeightDp")
                                                                        imageModifier =
                                                                            imageModifier.height(
                                                                                finalHeightDp
                                                                            )
                                                                    } else {
                                                                        val viewBoxParts =
                                                                            block.svgViewBox?.split(
                                                                                ' ',
                                                                                ','
                                                                            )
                                                                                ?.mapNotNull { it.toFloatOrNull() }
                                                                        if (viewBoxParts != null && viewBoxParts.size == 4 && viewBoxParts[2] > 0) {
                                                                            val aspectRatio =
                                                                                viewBoxParts[3] / viewBoxParts[2]
                                                                            val effectiveWidth =
                                                                                widthPx
                                                                                    ?: containerWidthPx.toFloat()
                                                                            val finalHeightDp =
                                                                                with(localDensity) { (effectiveWidth * aspectRatio).toDp() }
                                                                            imageModifier =
                                                                                imageModifier.height(
                                                                                    finalHeightDp
                                                                                )
                                                                        } else {
                                                                            val fallbackHeightDp =
                                                                                with(localDensity) { (textStyle.fontSize.value * 3).sp.toDp() }
                                                                            imageModifier =
                                                                                imageModifier.height(
                                                                                    fallbackHeightDp
                                                                                )
                                                                        }
                                                                    }

                                                                    val imageRequest =
                                                                        Builder(LocalContext.current).data(
                                                                            SvgData(
                                                                                nonBlankSvgContent
                                                                            )
                                                                        ).listener(
                                                                            onError = { _, result ->
                                                                                Timber.e(
                                                                                    result.throwable,
                                                                                    "Coil failed to load SVG for MathBlock."
                                                                                )
                                                                            }).build()

                                                                    val colorFilter =
                                                                        if (block.isFromMathJax) ColorFilter.tint(
                                                                            textStyle.color
                                                                        )
                                                                        else null

                                                                    AsyncImage(
                                                                        model = imageRequest,
                                                                        contentDescription = block.altText
                                                                            ?: "Equation",
                                                                        modifier = imageModifier,
                                                                        contentScale = ContentScale.Fit,
                                                                        colorFilter = colorFilter,
                                                                        imageLoader = imageLoader
                                                                    )
                                                                }
                                                            } else {
                                                                Timber.w(
                                                                    "PaginatedReader: MathBlock has no SVG content, rendering alt text."
                                                                )
                                                                Text(
                                                                    text = block.altText
                                                                        ?: "[Equation not available]",
                                                                    style = textStyle,
                                                                    modifier = paddingModifier
                                                                )
                                                            }
                                                        }

                                                        is ImageBlock -> {
                                                            val style = block.style
                                                            val colorFilter =
                                                                if (block.style.filter == "invert(100%)") {
                                                                    val matrix = floatArrayOf(
                                                                        -1f,
                                                                        0f,
                                                                        0f,
                                                                        0f,
                                                                        255f,
                                                                        0f,
                                                                        -1f,
                                                                        0f,
                                                                        0f,
                                                                        255f,
                                                                        0f,
                                                                        0f,
                                                                        -1f,
                                                                        0f,
                                                                        255f,
                                                                        0f,
                                                                        0f,
                                                                        0f,
                                                                        1f,
                                                                        0f
                                                                    )
                                                                    ColorFilter.colorMatrix(
                                                                        ColorMatrix(matrix)
                                                                    )
                                                                } else {
                                                                    null
                                                                }
                                                            val context = LocalContext.current
                                                            val imageRequest =
                                                                Builder(context).data(File(block.path))
                                                                    .listener(onSuccess = { _, _ ->
                                                                        Timber.d(
                                                                            "Coil successfully loaded image: ${block.path}"
                                                                        )
                                                                    }, onError = { _, result ->
                                                                        Timber.e(
                                                                            result.throwable,
                                                                            "Coil FAILED to load image: ${block.path}"
                                                                        )
                                                                    }).crossfade(true).build()

                                                            BoxWithConstraints(
                                                                modifier = paddingModifier,
                                                                contentAlignment = imageBlockContentAlignment(style)
                                                            ) {
                                                                val scaledSize = computeImageRenderSizeDp(
                                                                    block = block,
                                                                    density = density,
                                                                    maxWidthDp = maxWidth,
                                                                    imageSizeMultiplier = imageSizeMultiplier
                                                                )
                                                                val finalImageModifier = Modifier
                                                                    .then(
                                                                        if (scaledSize != null) {
                                                                            Modifier.width(scaledSize.first).height(scaledSize.second)
                                                                        } else if (style.width.isSpecified && style.width > 0.dp) {
                                                                            Modifier.width(style.width)
                                                                        } else {
                                                                            Modifier.fillMaxWidth()
                                                                        }
                                                                    )
                                                                    .then(
                                                                        if (scaledSize == null && style.maxWidth.isSpecified && style.maxWidth > 0.dp) {
                                                                            Modifier.widthIn(max = style.maxWidth)
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                                    .then(
                                                                        if (scaledSize == null) {
                                                                            if (block.expectedHeight > 0) {
                                                                                Modifier.height(with(density) { (block.expectedHeight * imageSizeMultiplier).toDp() })
                                                                            } else {
                                                                                Modifier.height(250.dp)
                                                                            }
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )

                                                                AsyncImage(
                                                                    model = imageRequest,
                                                                    contentDescription = block.altText
                                                                        ?: "Image from EPUB",
                                                                    modifier = finalImageModifier,
                                                                    contentScale = imageContentScale(style),
                                                                    colorFilter = colorFilter
                                                                )
                                                            }
                                                        }

                                                        is SpacerBlock -> {
                                                            Box(
                                                                modifier = Modifier.fillMaxWidth()
                                                                    .height(block.height)
                                                                    .drawCssBorders(
                                                                        block.style,
                                                                        density
                                                                    )
                                                            )
                                                        }

                                                        is TableBlock -> {
                                                            Column(modifier = paddingModifier) {
                                                                block.rows.forEach { tableRow ->
                                                                    Row(
                                                                        Modifier.fillMaxWidth()
                                                                            .height(
                                                                                IntrinsicSize.Min
                                                                            )
                                                                    ) {
                                                                        val hasFixedWidths =
                                                                            tableRow.any {
                                                                                it.style.blockStyle.width != Dp.Unspecified
                                                                            }

                                                                        tableRow.forEach { cell ->
                                                                            val cellStyle =
                                                                                cell.style.blockStyle

                                                                            val cellContainerModifier =
                                                                                if (hasFixedWidths) {
                                                                                    if (cellStyle.width != Dp.Unspecified) Modifier.width(
                                                                                        cellStyle.width
                                                                                    )
                                                                                    else Modifier.weight(
                                                                                        cell.colspan.toFloat(),
                                                                                        fill = true
                                                                                    )
                                                                                } else {
                                                                                    Modifier.weight(
                                                                                        cell.colspan.toFloat(),
                                                                                        fill = true
                                                                                    )
                                                                                }

                                                                            val alignment =
                                                                                when (cell.style.paragraphStyle.textAlign) {
                                                                                    TextAlign.Center -> Alignment.CenterHorizontally
                                                                                    TextAlign.End -> Alignment.End
                                                                                    else -> Alignment.Start
                                                                                }

                                                                            val cellModifier =
                                                                                cellContainerModifier.fillMaxHeight()
                                                                                    .then(
                                                                                        if (cellStyle.backgroundColor.isSpecified) {
                                                                                            Modifier.background(
                                                                                                cellStyle.backgroundColor
                                                                                            )
                                                                                        } else {
                                                                                            Modifier
                                                                                        }
                                                                                    )
                                                                                    .drawCssBorders(
                                                                                        cellStyle,
                                                                                        density
                                                                                    ).padding(
                                                                                        start = cellStyle.padding.left.coerceAtLeast(
                                                                                            0.dp
                                                                                        ),
                                                                                        top = cellStyle.padding.top.coerceAtLeast(
                                                                                            0.dp
                                                                                        ),
                                                                                        end = cellStyle.padding.right.coerceAtLeast(
                                                                                            0.dp
                                                                                        ),
                                                                                        bottom = cellStyle.padding.bottom.coerceAtLeast(
                                                                                            0.dp
                                                                                        )
                                                                                    )

                                                                            Column(
                                                                                modifier = cellModifier,
                                                                                horizontalAlignment = alignment
                                                                            ) {
                                                                                val cellTextStyle =
                                                                                    if (cell.isHeader) {
                                                                                        textStyle.copy(
                                                                                            fontWeight = FontWeight.Bold
                                                                                        )
                                                                                    } else {
                                                                                        textStyle
                                                                                    }

                                                                                cell.content.forEach { blockInCell ->
                                                                                    when (blockInCell) {
                                                                                        is ParagraphBlock -> {
                                                                                            LinkAwareText(
                                                                                                text = blockInCell.content,
                                                                                                style = cellTextStyle,
                                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                                isDarkTheme = isDarkTheme,
                                                                                                themeBackgroundColor = effectiveBg,
                                                                                                themeTextColor = effectiveText,
                                                                                                onLinkClick = onLinkClickCallback,
                                                                                                onGeneralTap = onGeneralTapCallback
                                                                                            )
                                                                                        }

                                                                                        is HeaderBlock -> {
                                                                                            LinkAwareText(
                                                                                                text = blockInCell.content,
                                                                                                style = cellTextStyle.copy(
                                                                                                    fontWeight = FontWeight.Bold
                                                                                                ),
                                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                                isDarkTheme = isDarkTheme,
                                                                                                themeBackgroundColor = effectiveBg,
                                                                                                themeTextColor = effectiveText,
                                                                                                onLinkClick = onLinkClickCallback,
                                                                                                onGeneralTap = onGeneralTapCallback
                                                                                            )
                                                                                        }

                                                                                        is ListItemBlock -> {
                                                                                            Row(
                                                                                                verticalAlignment = Alignment.Top
                                                                                            ) {
                                                                                                val itemMarker = blockInCell.itemMarker
                                                                                                if (itemMarker != null) {
                                                                                                    Text(
                                                                                                        text = itemMarker,
                                                                                                        style = cellTextStyle,
                                                                                                        modifier = Modifier.padding(
                                                                                                            end = 4.dp
                                                                                                        )
                                                                                                    )
                                                                                                }
                                                                                                LinkAwareText(
                                                                                                    text = blockInCell.content,
                                                                                                    style = cellTextStyle,
                                                                                                    modifier = Modifier.weight(
                                                                                                        1f
                                                                                                    ),
                                                                                                    isDarkTheme = isDarkTheme,
                                                                                                    themeBackgroundColor = effectiveBg,
                                                                                                    themeTextColor = effectiveText,
                                                                                                    onLinkClick = onLinkClickCallback,
                                                                                                    onGeneralTap = onGeneralTapCallback
                                                                                                )
                                                                                            }
                                                                                        }

                                                                                        is SpacerBlock -> {
                                                                                            Spacer(
                                                                                                modifier = Modifier.fillMaxWidth()
                                                                                                    .height(
                                                                                                        blockInCell.height
                                                                                                    )
                                                                                                    .drawCssBorders(
                                                                                                        blockInCell.style,
                                                                                                        density
                                                                                                    )
                                                                                            )
                                                                                        }

                                                                                        is ImageBlock -> {
                                                                                            AsyncImage(
                                                                                                model = Builder(
                                                                                                    LocalContext.current
                                                                                                ).data(
                                                                                                    File(
                                                                                                        blockInCell.path
                                                                                                    )
                                                                                                )
                                                                                                    .build(),
                                                                                                contentDescription = blockInCell.altText,
                                                                                                contentScale = imageContentScale(blockInCell.style),
                                                                                                modifier = tableCellImageModifier(
                                                                                                    block = blockInCell,
                                                                                                    density = density,
                                                                                                    imageSizeMultiplier = imageSizeMultiplier
                                                                                                )
                                                                                            )
                                                                                        }

                                                                                        is TextContentBlock -> {
                                                                                            LinkAwareText(
                                                                                                text = blockInCell.content,
                                                                                                style = cellTextStyle,
                                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                                isDarkTheme = isDarkTheme,
                                                                                                themeBackgroundColor = effectiveBg,
                                                                                                themeTextColor = effectiveText,
                                                                                                onLinkClick = onLinkClickCallback,
                                                                                                onGeneralTap = onGeneralTapCallback
                                                                                            )
                                                                                        }

                                                                                        else -> {}
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        var chapterInfo by remember {
                                            mutableStateOf<Pair<String, Int?>?>(null)
                                        }
                                        LaunchedEffect(pageIndex) {
                                            chapterInfo = onGetChapterInfo(pageIndex)
                                        }

                                        ChapterLoadingPlaceholder(title = chapterInfo?.first)
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeSelection != null) {
                    val sel = activeSelection!!
                    val currentPageSuffix = "_${pagerState.currentPage}"

                    val currentPageBlocks =
                        blockLayoutMap.filterKeys { it.endsWith(currentPageSuffix) }.values.filter { it.second.isAttached }
                    val visibleSelectedBlocks =
                        currentPageBlocks.filter { isBlockSelectedOnPage(it.third, pagerState.currentPage, sel) }

                    if (!isDraggingHandle && visibleSelectedBlocks.isNotEmpty()) {
                        val menuAnchorRect = run {
                            var minLeft = Float.MAX_VALUE
                            var minTop = Float.MAX_VALUE
                            var maxRight = Float.MIN_VALUE
                            var maxBottom = Float.MIN_VALUE

                            visibleSelectedBlocks.forEach { triple ->
                                val (textLayout, coords, block) = triple

                                val currentBlockAbs = getTextBlockCharOffset(block)
                                val isStartBlockPart =
                                    pagerState.currentPage == sel.startPageIndex &&
                                        block.blockIndex == sel.startBlockIndex &&
                                        currentBlockAbs == sel.startBlockCharOffset
                                val isEndBlockPart =
                                    pagerState.currentPage == sel.endPageIndex &&
                                        block.blockIndex == sel.endBlockIndex &&
                                        currentBlockAbs == sel.endBlockCharOffset

                                val blockStartOffset = if (isStartBlockPart) sel.startOffset else 0
                                val blockEndOffset = if (isEndBlockPart) sel.endOffset else textLayout.layoutInput.text.length

                                val textLen = textLayout.layoutInput.text.length
                                val maxIdx = maxOf(0, textLen - 1)

                                val safeStart = blockStartOffset.coerceIn(0, textLen)
                                val safeEnd = blockEndOffset.coerceIn(safeStart, textLen)

                                if (safeStart < safeEnd) {
                                    try {
                                        val startBox = textLayout.getBoundingBox(safeStart.coerceIn(0, maxIdx))
                                        val endBox = textLayout.getBoundingBox((safeEnd - 1).coerceIn(0, maxIdx))

                                        val topWin =
                                            coords.localToWindow(Offset(0f, startBox.top)).y
                                        val bottomWin =
                                            coords.localToWindow(Offset(0f, endBox.bottom)).y
                                        val leftWin1 =
                                            coords.localToWindow(Offset(startBox.left, 0f)).x
                                        val rightWin1 =
                                            coords.localToWindow(Offset(startBox.right, 0f)).x
                                        val leftWin2 =
                                            coords.localToWindow(Offset(endBox.left, 0f)).x
                                        val rightWin2 =
                                            coords.localToWindow(Offset(endBox.right, 0f)).x

                                        minTop = minOf(minTop, topWin)
                                        maxBottom = maxOf(maxBottom, bottomWin)
                                        minLeft = minOf(minLeft, leftWin1, leftWin2)
                                        maxRight = maxOf(maxRight, rightWin1, rightWin2)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error calculating exact selection bounds")
                                    }
                                }
                            }

                            val handleSizePx = with(density) { 36.dp.toPx() }
                            if (minTop != Float.MAX_VALUE && maxBottom != Float.MIN_VALUE) {
                                Rect(minLeft, minTop, maxRight, maxBottom + handleSizePx)
                            } else {
                                Rect(
                                    sel.rect.left,
                                    sel.rect.top,
                                    sel.rect.right,
                                    sel.rect.bottom + handleSizePx
                                )
                            }
                        }

                        Popup(
                            popupPositionProvider = remember(
                                menuAnchorRect,
                                density
                            ) { SmartPopupPositionProvider(menuAnchorRect, density) },
                            onDismissRequest = { activeSelection = null },
                            properties = PopupProperties(
                                dismissOnClickOutside = false
                            )
                        ) {
                            PaginatedTextSelectionMenu(
                                onCopy = {
                                    val clipboardManager =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(context.getString(R.string.clip_label_copied_text), sel.text)
                                    clipboardManager.setPrimaryClip(clip)
                                    activeSelection = null
                                },
                                onSelectAll = null,
                                onDictionary = {
                                    if (isProUser || countWords(sel.text) <= 1) {
                                        onWordSelectedForAiDefinition(sel.text)
                                    } else {
                                        onShowDictionaryUpsellDialog()
                                    }
                                    activeSelection = null
                                },
                                onTranslate = {
                                    onTranslate(sel.text)
                                    activeSelection = null
                                },
                                onSearch = {
                                    onSearch(sel.text)
                                    activeSelection = null
                                },
                                onHighlight = { color ->
                                    val startAbsoluteOffset = sel.startBlockCharOffset + sel.startOffset
                                    val endAbsoluteOffset = sel.endBlockCharOffset + sel.endOffset
                                    val finalCfi =
                                        "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                    val absoluteCandidateCfi =
                                        "${sel.startBaseCfi}:$startAbsoluteOffset|${sel.endBaseCfi}:$endAbsoluteOffset"
                                    val locator = sel.toSharedHighlightLocator(
                                        chapterIndex = onGetChapterIndex(sel.startPageIndex),
                                        cfi = finalCfi
                                    )
                                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                        "create_request source=highlight_menu color=${color.id} " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                        "create_request surface=paginated action=highlight color=${color.id} " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "locator=${locator} textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    onHighlightCreated(finalCfi, sel.text, color.id, locator)
                                    activeSelection = null
                                },
                                onNote = {
                                    onNoteRequested(null)
                                    val startAbsoluteOffset = sel.startBlockCharOffset + sel.startOffset
                                    val endAbsoluteOffset = sel.endBlockCharOffset + sel.endOffset
                                    val finalCfi =
                                        "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                    val absoluteCandidateCfi =
                                        "${sel.startBaseCfi}:$startAbsoluteOffset|${sel.endBaseCfi}:$endAbsoluteOffset"
                                    val locator = sel.toSharedHighlightLocator(
                                        chapterIndex = onGetChapterIndex(sel.startPageIndex),
                                        cfi = finalCfi
                                    )
                                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                        "create_request source=note_menu color=${HighlightColor.YELLOW.id} " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                        "create_request surface=paginated action=note color=${HighlightColor.YELLOW.id} " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "locator=${locator} textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    onHighlightCreated(finalCfi, sel.text, HighlightColor.YELLOW.id, locator)
                                    activeSelection = null
                                },
                                onTts = {
                                    val startAbs = sel.startOffset + sel.startBlockCharOffset
                                    onStartTtsFromSelection(sel.startBaseCfi, startAbs)
                                    activeSelection = null
                                },
                                onDelete = null,
                                isProUser = isProUser,
                                isOss = isOss,
                                activeHighlightPalette = activeHighlightPalette,
                                onOpenPaletteManager = { showPaletteManager = true })
                        }
                    }

                    val updateSelection: (Offset, SelectionHandle) -> SelectionHandle =
                        { windowPos, currentDragHandle ->
                            var activeDragHandle = currentDragHandle

                            val attachedBlocks =
                                blockLayoutMap.filterKeys { it.endsWith(currentPageSuffix) }.values.filter { it.second.isAttached }
                                    .sortedBy { it.second.positionInWindow().y }

                            if (attachedBlocks.isNotEmpty()) {
                                val targetTriple = attachedBlocks.minByOrNull {
                                    val coords = it.second
                                    val rect = Rect(coords.positionInWindow(), coords.size.toSize())
                                    val dx =
                                        maxOf(rect.left - windowPos.x, 0f, windowPos.x - rect.right)
                                    val dy =
                                        maxOf(rect.top - windowPos.y, 0f, windowPos.y - rect.bottom)
                                    dx * dx + dy * dy
                                } ?: attachedBlocks.last()

                                val (textLayout, coords, block) = targetTriple
                                val localPos = coords.windowToLocal(windowPos)
                                val offset = textLayout.getOffsetForPosition(localPos)
                                    .coerceIn(0, textLayout.layoutInput.text.length)

                                val isStartHandle = activeDragHandle == SelectionHandle.START
                                var newStartIdx =
                                    if (isStartHandle) block.blockIndex else sel.startBlockIndex
                                var newEndIdx =
                                    if (isStartHandle) sel.endBlockIndex else block.blockIndex
                                var newStartOffset = if (isStartHandle) offset else sel.startOffset
                                var newEndOffset = if (isStartHandle) sel.endOffset else offset
                                var newStartCfi = if (isStartHandle) block.cfi!! else sel.startBaseCfi
                                var newEndCfi = if (isStartHandle) sel.endBaseCfi else block.cfi!!
                                var newStartPageIdx = if (isStartHandle) pagerState.currentPage else sel.startPageIndex
                                var newEndPageIdx = if (isStartHandle) sel.endPageIndex else pagerState.currentPage

                                val currentBlockAbs = getTextBlockCharOffset(block)
                                var newStartBlockAbs = if (isStartHandle) currentBlockAbs else sel.startBlockCharOffset
                                var newEndBlockAbs = if (!isStartHandle) currentBlockAbs else sel.endBlockCharOffset

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
                                    newStartPageIdx != sel.startPageIndex ||
                                    newEndPageIdx != sel.endPageIndex ||
                                    newStartIdx != sel.startBlockIndex ||
                                    newEndIdx != sel.endBlockIndex ||
                                    newStartOffset != sel.startOffset ||
                                    newEndOffset != sel.endOffset
                                ) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    val relevantBlocks = attachedBlocks
                                        .filter {
                                            isBlockSelectedOnPage(
                                                block = it.third,
                                                pageIndex = pagerState.currentPage,
                                                selection = PaginatedSelection(
                                                    startBlockIndex = newStartIdx,
                                                    endBlockIndex = newEndIdx,
                                                    startBaseCfi = newStartCfi,
                                                    endBaseCfi = newEndCfi,
                                                    startOffset = newStartOffset,
                                                    endOffset = newEndOffset,
                                                    text = sel.text,
                                                    rect = sel.rect,
                                                    startPageIndex = newStartPageIdx,
                                                    endPageIndex = newEndPageIdx,
                                                    startBlockCharOffset = newStartBlockAbs,
                                                    endBlockCharOffset = newEndBlockAbs,
                                                    textPerBlock = sel.textPerBlock
                                                )
                                            )
                                        }
                                        .sortedWith(compareBy({ it.third.blockIndex }, { getTextBlockCharOffset(it.third) }))

                                    val newTextPerBlock = sel.textPerBlock.toMutableMap()
                                    newTextPerBlock.keys.removeAll { keyStr ->
                                        parseSelectionBlockKey(keyStr)?.pageIndex == pagerState.currentPage
                                    }

                                    for (b in relevantBlocks) {
                                        val txt = b.third.content.text
                                        val bAbs = getTextBlockCharOffset(b.third)
                                        val isStartBlockPart = b.third.blockIndex == newStartIdx && bAbs == newStartBlockAbs
                                        val isEndBlockPart = b.third.blockIndex == newEndIdx && bAbs == newEndBlockAbs

                                        val s = if (isStartBlockPart) newStartOffset else 0
                                        val e = if (isEndBlockPart) newEndOffset else txt.length

                                        val safeS = s.coerceIn(0, txt.length)
                                        val safeE = e.coerceIn(safeS, txt.length)

                                        if (safeS < safeE) {
                                            newTextPerBlock[
                                                buildSelectionBlockKey(
                                                    pageIndex = pagerState.currentPage,
                                                    blockIndex = b.third.blockIndex,
                                                    blockCharOffset = bAbs
                                                )
                                            ] = txt.substring(safeS, safeE)
                                        } else {
                                            newTextPerBlock.remove(
                                                buildSelectionBlockKey(
                                                    pageIndex = pagerState.currentPage,
                                                    blockIndex = b.third.blockIndex,
                                                    blockCharOffset = bAbs
                                                )
                                            )
                                        }
                                    }

                                    val newText = newTextPerBlock.entries
                                        .sortedWith { first, second ->
                                            compareSelectionBlockKeys(first.key, second.key)
                                        }
                                        .joinToString(" ") { it.value }

                                    val sLayout = blockLayoutMap["${newStartCfi}_$newStartPageIdx"]?.takeIf {
                                        val abs = getTextBlockCharOffset(it.third)
                                        abs == newStartBlockAbs
                                    }

                                    val eLayout = blockLayoutMap["${newEndCfi}_$newEndPageIdx"]
                                    var newRect = sel.rect

                                    if (sLayout != null && eLayout != null && sLayout.second.isAttached && eLayout.second.isAttached) {
                                        val sMaxIdx = maxOf(0, sLayout.first.layoutInput.text.length - 1)
                                        val eMaxIdx = maxOf(0, eLayout.first.layoutInput.text.length - 1)

                                        val sRectLocal = sLayout.first.getBoundingBox(
                                            newStartOffset.coerceIn(0, sMaxIdx)
                                        )
                                        val sRectWin = Rect(
                                            sLayout.second.localToWindow(sRectLocal.topLeft),
                                            sLayout.second.localToWindow(sRectLocal.bottomRight)
                                        )
                                        val eRectLocal = eLayout.first.getBoundingBox(
                                            (newEndOffset - 1).coerceIn(0, eMaxIdx)
                                        )
                                        val eRectWin = Rect(
                                            eLayout.second.localToWindow(eRectLocal.topLeft),
                                            eLayout.second.localToWindow(eRectLocal.bottomRight)
                                        )
                                        newRect = Rect(
                                            minOf(sRectWin.left, eRectWin.left),
                                            sRectWin.top,
                                            maxOf(sRectWin.right, eRectWin.right),
                                            eRectWin.bottom
                                        )
                                    } else {
                                        var minLeft = Float.MAX_VALUE
                                        var minTop = Float.MAX_VALUE
                                        var maxRight = Float.MIN_VALUE
                                        var maxBottom = Float.MIN_VALUE
                                        relevantBlocks.forEach { b ->
                                            if (b.second.isAttached) {
                                                val r = Rect(
                                                    b.second.positionInWindow(),
                                                    b.second.size.toSize()
                                                )
                                                minLeft = minOf(minLeft, r.left)
                                                minTop = minOf(minTop, r.top)
                                                maxRight = maxOf(maxRight, r.right)
                                                maxBottom = maxOf(maxBottom, r.bottom)
                                            }
                                        }
                                        if (minLeft != Float.MAX_VALUE) {
                                            newRect = Rect(minLeft, minTop, maxRight, maxBottom)
                                        }
                                    }

                                    activeSelection = PaginatedSelection(
                                        startBlockIndex = newStartIdx,
                                        endBlockIndex = newEndIdx,
                                        startBaseCfi = newStartCfi,
                                        endBaseCfi = newEndCfi,
                                        startOffset = newStartOffset,
                                        endOffset = newEndOffset,
                                        text = newText,
                                        rect = newRect,
                                        startPageIndex = newStartPageIdx,
                                        endPageIndex = newEndPageIdx,
                                        startBlockCharOffset = newStartBlockAbs,
                                        endBlockCharOffset = newEndBlockAbs,
                                        textPerBlock = newTextPerBlock
                                    )
                                }
                            }
                            activeDragHandle
                        }

                    val latestUpdateSelection by rememberUpdatedState(updateSelection)

                    listOf(SelectionHandle.START, SelectionHandle.END).forEach { handleType ->
                        val isStart = handleType == SelectionHandle.START
                        var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    @Suppress("UNUSED_VARIABLE") val animOffset = pagerState.currentPageOffsetFraction
                                    @Suppress("UNUSED_VARIABLE") val currPage = pagerState.currentPage
                                    @Suppress("UNUSED_VARIABLE") val isScrolling = pagerState.isScrollInProgress
                                    @Suppress("UNUSED_VARIABLE") val tick = blockLayoutMap.tick

                                    val handlePageIndex = if (isStart) sel.startPageIndex else sel.endPageIndex
                                    val pos = if (handlePageIndex == pagerState.currentPage) {
                                        val selCfi = if (isStart) sel.startBaseCfi else sel.endBaseCfi
                                        val selOffset = if (isStart) sel.startOffset else sel.endOffset
                                        val targetBlockAbs = if (isStart) sel.startBlockCharOffset else sel.endBlockCharOffset
                                        val layoutInfo = blockLayoutMap["${selCfi}_$handlePageIndex"]?.takeIf {
                                            val blockAbs = getTextBlockCharOffset(it.third)
                                            blockAbs == targetBlockAbs
                                        }

                                        if (layoutInfo != null && layoutInfo.second.isAttached && rootCoords != null && rootCoords!!.isAttached) {
                                            val textLayout = layoutInfo.first
                                            val coords = layoutInfo.second
                                            val maxIdx = maxOf(0, textLayout.layoutInput.text.length - 1)
                                            val safeOffset = selOffset.coerceIn(0, textLayout.layoutInput.text.length)
                                            val safeOffsetForLine = safeOffset.coerceIn(0, maxIdx)

                                            val line = textLayout.getLineForOffset(safeOffsetForLine)
                                            val x = textLayout.getHorizontalPosition(safeOffset, usePrimaryDirection = true)
                                            val y = textLayout.getLineBottom(line)

                                            try {
                                                val windowPos = coords.localToWindow(Offset(x, y))
                                                rootCoords!!.windowToLocal(windowPos)
                                            } catch (e: Exception) {
                                                Offset.Unspecified
                                            }
                                        } else {
                                            Offset.Unspecified
                                        }
                                    } else {
                                        Offset.Unspecified
                                    }

                                    if (pos.isSpecified) {
                                        translationX = pos.x - 18.dp.toPx()
                                        translationY = pos.y
                                        alpha = 1f
                                    } else {
                                        alpha = 0f
                                    }
                                }
                                .size(36.dp)
                                .onGloballyPositioned { handleCoords = it }
                                .pointerInput(handleType) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        down.consume()
                                        isDraggingHandle = true
                                        var currentDragHandle = handleType

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()

                                            if (handleCoords != null && rootCoords != null && handleCoords!!.isAttached && rootCoords!!.isAttached) {
                                                try {
                                                    val pointerWindow = handleCoords!!.localToWindow(change.position)
                                                    val pointerRoot = rootCoords!!.windowToLocal(pointerWindow)
                                                    val targetRootY = pointerRoot.y - 36.dp.toPx()
                                                    val targetRootPos = Offset(pointerRoot.x, targetRootY)

                                                    magnifierCenter = targetRootPos

                                                    val targetWindowPos = rootCoords!!.localToWindow(targetRootPos)
                                                    currentDragHandle = latestUpdateSelection(targetWindowPos, currentDragHandle)
                                                } catch (e: Exception) {
                                                    // Ignore detachment crashes during fast scrolls
                                                }
                                            }
                                        }
                                        isDraggingHandle = false
                                        magnifierCenter = Offset.Unspecified
                                    }
                                },
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.teardrop),
                                contentDescription = if (isStart) "Start handle" else "End handle",
                                modifier = Modifier.size(36.dp).graphicsLayer {
                                    rotationZ = if (isStart) 30f else -30f
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                },
                                tint = Color(0xFF1976D2)
                            )
                        }
                    }
                }

                if (showColorPickerDialog != null) {
                    AlertDialog(
                        onDismissRequest = { showColorPickerDialog = null },
                        title = { Text(stringResource(R.string.dialog_select_color)) },
                        text = {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(HighlightColor.entries) { colorOption ->
                                    Box(
                                        modifier = Modifier.size(48.dp)
                                            .background(colorOption.color, CircleShape).border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            ).clickable {
                                                onUpdatePalette(
                                                    showColorPickerDialog!!,
                                                    colorOption
                                                )
                                                showColorPickerDialog = null
                                            })
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showColorPickerDialog = null
                            }) { Text(stringResource(R.string.action_close)) }
                        })
                }

                if (showPaletteManager) {
                    PaletteManagerDialog(
                        currentPalette = activeHighlightPalette,
                        onDismiss = { showPaletteManager = false },
                        onSave = { newPalette ->
                            newPalette.forEachIndexed { index, color ->
                                onUpdatePalette(index, color)
                            }
                            showPaletteManager = false
                        })
                }
            }
        } else {
            Timber.w("Book has no pages to display.")
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.msg_book_no_content))
            }
        }
    }
}

@Composable
private fun ChapterLoadingPlaceholder(title: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(16.dp))
            }
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Preparing chapter.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun RenderFlexChildBlock(
    childBlock: ContentBlock,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    textMeasurer: TextMeasurer,
    onLinkClickCallback: (String) -> Unit,
    onGeneralTapCallback: (Offset) -> Unit,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    blockLayoutMap: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    density: Density,
    imageLoader: ImageLoader,
    pageIndex: Int,
    registerStableLayoutKey: Boolean = false
) {
    @Composable
    fun renderTextBlock(block: TextContentBlock) {
        val searchHighlighted =
            highlightQueryInText(block.content, searchQuery, searchHighlightColor)
        val finalContent = if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
            buildAnnotatedString {
                append(searchHighlighted)
                val blockStartAbs = block.startCharOffsetInSource
                val blockEndAbs = block.startCharOffsetInSource + searchHighlighted.length
                val highlightStartAbs = ttsHighlightInfo.offset
                val highlightEndAbs = ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                val intersectionStartAbs = maxOf(blockStartAbs, highlightStartAbs)
                val intersectionEndAbs = minOf(blockEndAbs, highlightEndAbs)

                if (intersectionStartAbs < intersectionEndAbs) {
                    val highlightStartRelative = intersectionStartAbs - blockStartAbs
                    val highlightEndRelative = intersectionEndAbs - blockStartAbs
                    addStyle(
                        style = SpanStyle(background = ttsHighlightColor),
                        start = highlightStartRelative,
                        end = highlightEndRelative
                    )
                }
            }
        } else {
            searchHighlighted
        }

        // Apply block specific styles (like header font weight)
        val finalStyle = if (block is HeaderBlock) {
            createHeaderTextStyle(
                baseStyle = textStyle,
                level = block.level,
                textAlign = block.textAlign
            )
        } else {
            textStyle
        }

        TextWithEmphasis(
            text = finalContent,
            style = finalStyle,
            modifier = Modifier,
            pageIndex = pageIndex,
            textMeasurer = textMeasurer,
            onLinkClick = onLinkClickCallback,
            onGeneralTap = onGeneralTapCallback,
            block = block,
            userHighlights = userHighlights,
            activeSelection = activeSelection,
            onSelectionChange = onSelectionChange,
            onHighlightClick = onHighlightClick,
            isDarkTheme = isDarkTheme,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor,
            onRegisterLayout = { layout, coords ->
                block.cfi?.let { cfi ->
                    val key = if (registerStableLayoutKey) {
                        textBlockLayoutKey(cfi, pageIndex, block)
                    } else {
                        legacyTextBlockLayoutKey(cfi, pageIndex)
                    }
                    blockLayoutMap[key] = Triple(layout, coords, block)
                }
            })
    }

    when (childBlock) {
        is ListItemBlock -> {
            Row(modifier = Modifier, verticalAlignment = Alignment.Top) {
                val markerAreaModifier = Modifier
                    .width(32.dp)
                    .padding(end = 8.dp)
                val itemMarkerImage = childBlock.itemMarkerImage
                val itemMarker = childBlock.itemMarker

                if (itemMarkerImage != null) {
                    val imageRequest =
                        Builder(LocalContext.current).data(File(itemMarkerImage))
                            .crossfade(true).build()
                    val imageSize = with(density) { (textStyle.fontSize.value * 0.8f).sp.toDp() }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(R.string.content_desc_list_item_marker),
                        modifier = markerAreaModifier.height(imageSize),
                        alignment = Alignment.CenterEnd,
                        contentScale = ContentScale.FillHeight
                    )
                } else if (itemMarker != null) {
                    Text(
                        text = itemMarker,
                        style = textStyle.copy(textAlign = TextAlign.End),
                        modifier = markerAreaModifier
                    )
                }

                // Reuse text rendering logic
                renderTextBlock(childBlock)
            }
        }

        is ParagraphBlock -> renderTextBlock(childBlock)
        is HeaderBlock -> renderTextBlock(childBlock)
        is QuoteBlock -> renderTextBlock(childBlock)
        is TextContentBlock -> renderTextBlock(childBlock)
        is ImageBlock -> {
            val style = childBlock.style
            val colorFilter = if (childBlock.style.filter == "invert(100%)") {
                val matrix = floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    -1f,
                    0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    -1f,
                    0f,
                    255f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
                )
                ColorFilter.colorMatrix(ColorMatrix(matrix))
            } else null

            BoxWithConstraints(contentAlignment = imageBlockContentAlignment(style)) {
                val scaledSize = computeImageRenderSizeDp(
                    block = childBlock,
                    density = density,
                    maxWidthDp = maxWidth,
                    imageSizeMultiplier = imageSizeMultiplier
                )
                val imageModifier = Modifier
                    .then(
                        if (scaledSize != null) {
                            Modifier.width(scaledSize.first).height(scaledSize.second)
                        } else if (style.width != Dp.Unspecified && style.width > 0.dp) {
                            Modifier.width(style.width)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (scaledSize == null && style.maxWidth != Dp.Unspecified && style.maxWidth > 0.dp) {
                            Modifier.widthIn(max = style.maxWidth)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (scaledSize == null) {
                            if (childBlock.expectedHeight > 0) {
                                Modifier.height(with(density) { (childBlock.expectedHeight * imageSizeMultiplier).toDp() })
                            } else {
                                Modifier.height(250.dp)
                            }
                        } else {
                            Modifier
                        }
                    )

                AsyncImage(
                    model = Builder(LocalContext.current).data(File(childBlock.path)).crossfade(true)
                        .build(),
                    contentDescription = childBlock.altText,
                    modifier = imageModifier,
                    contentScale = imageContentScale(childBlock.style),
                    colorFilter = colorFilter,
                    imageLoader = imageLoader
                )
            }
        }

        is SpacerBlock -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(childBlock.height)
                    .drawCssBorders(childBlock.style, density)
            )
        }

        is TableBlock -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                childBlock.rows.forEach { tableRow ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        val hasFixedWidths =
                            tableRow.any { it.style.blockStyle.width != Dp.Unspecified }

                        tableRow.forEach { cell ->
                            val cellStyle = cell.style.blockStyle
                            val cellModifier = Modifier
                                .fillMaxHeight()
                                .then(
                                    if (hasFixedWidths && cellStyle.width != Dp.Unspecified) Modifier.width(
                                        cellStyle.width
                                    )
                                    else Modifier.weight(
                                        cell.colspan.toFloat(), fill = true
                                    )
                                )
                                .then(
                                    if (cellStyle.backgroundColor.isSpecified) Modifier.background(
                                        cellStyle.backgroundColor
                                    )
                                    else Modifier
                                )
                                .drawCssBorders(cellStyle, density)
                                .padding(
                                    start = cellStyle.padding.left.coerceAtLeast(
                                        0.dp
                                    ),
                                    top = cellStyle.padding.top.coerceAtLeast(0.dp),
                                    end = cellStyle.padding.right.coerceAtLeast(
                                        0.dp
                                    ),
                                    bottom = cellStyle.padding.bottom.coerceAtLeast(
                                        0.dp
                                    )
                                )

                            val alignment = when (cell.style.paragraphStyle.textAlign) {
                                TextAlign.Center -> Alignment.CenterHorizontally
                                TextAlign.End -> Alignment.End
                                else -> Alignment.Start
                            }

                            Column(modifier = cellModifier, horizontalAlignment = alignment) {
                                val cellTextStyle =
                                    if (cell.isHeader) textStyle.copy(fontWeight = FontWeight.Bold)
                                    else textStyle
                                cell.content.forEach { blockInCell ->
                                    if (blockInCell is TextContentBlock) {
                                        LinkAwareText(
                                            text = blockInCell.content,
                                            style = cellTextStyle,
                                            modifier = Modifier.fillMaxWidth(),
                                            isDarkTheme = isDarkTheme,
                                            themeBackgroundColor = themeBackgroundColor,
                                            themeTextColor = themeTextColor,
                                            onLinkClick = onLinkClickCallback,
                                            onGeneralTap = onGeneralTapCallback
                                        )
                                    } else if (blockInCell is ImageBlock) {
                                        AsyncImage(
                                            model = Builder(LocalContext.current).data(
                                                File(
                                                    blockInCell.path
                                                )
                                            ).build(),
                                            contentDescription = blockInCell.altText,
                                            contentScale = imageContentScale(blockInCell.style),
                                            modifier = tableCellImageModifier(
                                                block = blockInCell,
                                                density = density,
                                                imageSizeMultiplier = imageSizeMultiplier
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        else -> {
            Timber.w(
                "FlexContainerBlock child type still not supported: ${childBlock::class.simpleName}"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.realisticBookPage(
    pagerState: PagerState,
    pageIndex: Int,
    paperColor: Color,
    isDarkTheme: Boolean,
    touchY: Float?,
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f
): Modifier = composed {

    val frontPath = remember { Path() }
    val backPath = remember { Path() }
    val reflectedScreenPath = remember { Path() }

    this
        .graphicsLayer {
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction

            if (abs(pageOffset) > 0.001f && abs(pageOffset) < 0.999f) {
                Timber.tag("PageTurnFixDiag").d("graphicsLayer: Page $pageIndex, Offset: $pageOffset")
            }

            if (pageOffset <= 1f && pageOffset > -1f) {
                translationX = -pageOffset * size.width
            }

            if (pageOffset != 0f) {
                shadowElevation = 10f
                shape = RectangleShape
                clip = false
            }
        }
        .drawWithContent {
            val drawStart = System.nanoTime()
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
            fun drawPaperBackground() {
                drawRect(color = paperColor)
                if (textureBitmap != null && textureAlpha > 0f) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                        blendMode = BlendMode.SrcOver,
                        alpha = textureAlpha
                    )
                }
            }

            if (abs(pageOffset) < 0.001f) {
                drawPaperBackground()
                drawContent()
            }
            else if (pageOffset < 0f && pageOffset > -1f) {
                val progress = -pageOffset
                val w = size.width
                val h = size.height

                val startY = touchY ?: h
                val rawCenterDist = ((startY - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)

                val flattenFactor = if (progress > 0.75f) {
                    ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val centerDist = rawCenterDist * (1f - flattenFactor)

                val cornerY = if (centerDist >= 0) h else 0f

                val dragX = w - w * 2.2f * progress
                val dragY = cornerY - h * 0.5f * progress * centerDist

                val midX = (w + dragX) / 2f
                val midY = (cornerY + dragY) / 2f

                val dx = w - dragX
                val dy = cornerY - dragY
                val nLen = sqrt(dx * dx + dy * dy)

                // CRITICAL GEOMETRY LOG
                if (progress > 0.8f) { // Focus logs on the "end" of the turn where the stall happens
                    Timber.tag("PageTurnFixDiag").i(
                        "Geometry Page $pageIndex: progress=$progress, nLen=$nLen, cornerY=$cornerY, dragX=$dragX, midX=$midX"
                    )
                }

                if (nLen > 0f) {
                    val nx = dx / nLen
                    val ny = dy / nLen

                    if (nx.isNaN() || ny.isNaN()) {
                        Timber.tag("PageTurnFixDiag").e("NAN DETECTED in Normal Vectors: nx=$nx, ny=$ny")
                    }

                    val huge = w * 3f
                    val vx = -ny

                    val p1X = midX + vx * huge
                    val p1Y = midY + nx * huge
                    val p2X = midX - vx * huge
                    val p2Y = midY - nx * huge

                    frontPath.rewind()
                    frontPath.moveTo(p1X, p1Y)
                    frontPath.lineTo(p2X, p2Y)
                    frontPath.lineTo(p2X - nx * huge, p2Y - ny * huge)
                    frontPath.lineTo(p1X - nx * huge, p1Y - ny * huge)
                    frontPath.close()

                    clipPath(frontPath) {
                        drawPaperBackground()
                        this@drawWithContent.drawContent()
                    }

                    val shadowWidth = (40.dp.toPx() * (1f - progress)).coerceAtLeast(10.dp.toPx())
                    backPath.rewind()
                    backPath.moveTo(p1X, p1Y)
                    backPath.lineTo(p2X, p2Y)
                    backPath.lineTo(p2X + nx * huge, p2Y + ny * huge)
                    backPath.lineTo(p1X + nx * huge, p1Y + ny * huge)
                    backPath.close()

                    val dropShadowBrush = Brush.linearGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent),
                        start = Offset(midX, midY),
                        end = Offset(midX + nx * shadowWidth, midY + ny * shadowWidth)
                    )
                    clipRect(0f, 0f, w, h) {
                        drawPath(backPath, dropShadowBrush)
                    }

                    fun reflect(px: Float, py: Float): Offset {
                        val vX = px - midX
                        val vY = py - midY
                        val dist = vX * nx + vY * ny
                        return Offset(px - 2 * dist * nx, py - 2 * dist * ny)
                    }

                    val rTL = reflect(0f, 0f)
                    val rTR = reflect(w, 0f)
                    val rBR = reflect(w, h)
                    val rBL = reflect(0f, h)

                    reflectedScreenPath.rewind()
                    reflectedScreenPath.moveTo(rTL.x, rTL.y)
                    reflectedScreenPath.lineTo(rTR.x, rTR.y)
                    reflectedScreenPath.lineTo(rBR.x, rBR.y)
                    reflectedScreenPath.lineTo(rBL.x, rBL.y)
                    reflectedScreenPath.close()

                    clipRect(0f, 0f, w, h) {
                        clipPath(frontPath) {
                            drawPath(reflectedScreenPath, color = paperColor)
                            if (textureBitmap != null && textureAlpha > 0f) {
                                clipPath(reflectedScreenPath) {
                                    drawRect(
                                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                                        blendMode = BlendMode.SrcOver,
                                        alpha = textureAlpha
                                    )
                                }
                            }
                            val flapTint = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                            drawPath(reflectedScreenPath, color = flapTint)

                            val innerShadowWidth = shadowWidth * 0.7f
                            val innerShadowBrush = Brush.linearGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.05f), Color.Transparent),
                                start = Offset(midX, midY),
                                end = Offset(midX - nx * innerShadowWidth, midY - ny * innerShadowWidth)
                            )
                            drawPath(reflectedScreenPath, innerShadowBrush)

                            drawPath(
                                path = reflectedScreenPath,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        drawLine(
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                            start = Offset(p1X, p1Y),
                            end = Offset(p2X, p2Y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                } else {
                    drawPaperBackground()
                    drawContent()
                }
            }
            else {
                drawPaperBackground()
                drawContent()
            }

            val drawDuration = (System.nanoTime() - drawStart) / 1_000_000.0
            if (drawDuration > 12.0) { // Log slow frames (anything near the 16ms frame budget)
                Timber.tag("PageTurnFixDiag").w("Slow Draw on Page $pageIndex: ${drawDuration}ms")
            }
        }
}

@Suppress("KotlinConstantConditions")
@Composable
fun Modifier.drawCssBorders(
    blockStyle: BlockStyle,
    @Suppress("unused") density: Density
): Modifier = this.drawBehind {
    val borderTop = blockStyle.borderTop
    val borderRight = blockStyle.borderRight
    val borderBottom = blockStyle.borderBottom
    val borderLeft = blockStyle.borderLeft
    val topWidth = borderTop?.width?.toPx() ?: 0f
    val rightWidth = borderRight?.width?.toPx() ?: 0f
    val bottomWidth = borderBottom?.width?.toPx() ?: 0f
    val leftWidth = borderLeft?.width?.toPx() ?: 0f

    val tlRadius = blockStyle.borderTopLeftRadius.toPx()
    val trRadius = blockStyle.borderTopRightRadius.toPx()
    val brRadius = blockStyle.borderBottomRightRadius.toPx()
    val blRadius = blockStyle.borderBottomLeftRadius.toPx()

    if (blockStyle.backgroundColor.isSpecified && blockStyle.backgroundColor != Color.Transparent) {
        val bgPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(tlRadius, tlRadius),
                    topRight = CornerRadius(trRadius, trRadius),
                    bottomRight = CornerRadius(brRadius, brRadius),
                    bottomLeft = CornerRadius(blRadius, blRadius)
                )
            )
        }
        drawPath(bgPath, color = blockStyle.backgroundColor, style = Fill)
    }

    // 2. Helper for PathEffects
    fun getPathEffect(style: String?, width: Float): PathEffect? {
        return when (style) {
            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * 2f), 0f)
            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(width, width), 0f)
            else -> null
        }
    }

    // TOP
    if (topWidth > 0f && borderTop != null) {
        val color = borderTop.color
        val effect = getPathEffect(borderTop.style, topWidth)
        val offset = topWidth / 2f

        val startX = if (tlRadius > 0) tlRadius else 0f
        val endX = if (trRadius > 0) size.width - trRadius else size.width

        drawLine(
            color = color,
            start = Offset(startX, offset),
            end = Offset(endX, offset),
            strokeWidth = topWidth,
            pathEffect = effect
        )
    }

    // BOTTOM
    if (bottomWidth > 0f && borderBottom != null) {
        val color = borderBottom.color
        val effect = getPathEffect(borderBottom.style, bottomWidth)
        val offset = size.height - (bottomWidth / 2f)

        val startX = if (blRadius > 0) blRadius else 0f
        val endX = if (brRadius > 0) size.width - brRadius else size.width

        drawLine(
            color = color,
            start = Offset(startX, offset),
            end = Offset(endX, offset),
            strokeWidth = bottomWidth,
            pathEffect = effect
        )
    }

    // LEFT
    if (leftWidth > 0f && borderLeft != null) {
        val color = borderLeft.color
        val effect = getPathEffect(borderLeft.style, leftWidth)
        val offset = leftWidth / 2f

        val startY = if (tlRadius > 0) tlRadius else 0f
        val endY = if (blRadius > 0) size.height - blRadius else size.height

        drawLine(
            color = color,
            start = Offset(offset, startY),
            end = Offset(offset, endY),
            strokeWidth = leftWidth,
            pathEffect = effect
        )
    }

    // RIGHT
    if (rightWidth > 0f && borderRight != null) {
        val color = borderRight.color
        val effect = getPathEffect(borderRight.style, rightWidth)
        val offset = size.width - (rightWidth / 2f)

        val startY = if (trRadius > 0) trRadius else 0f
        val endY = if (brRadius > 0) size.height - brRadius else size.height

        drawLine(
            color = color,
            start = Offset(offset, startY),
            end = Offset(offset, endY),
            strokeWidth = rightWidth,
            pathEffect = effect
        )
    }

    if (tlRadius > 0f && topWidth > 0f && leftWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 180f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth/2f, topWidth/2f),
            size = Size(tlRadius * 2 - leftWidth, tlRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (trRadius > 0f && topWidth > 0f && rightWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 270f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width - (trRadius * 2) + (rightWidth/2f), topWidth/2f),
            size = Size(trRadius * 2 - rightWidth, trRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (brRadius > 0f && bottomWidth > 0f && rightWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 0f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width - (brRadius * 2) + (rightWidth/2f), size.height - (brRadius * 2) + (bottomWidth/2f)),
            size = Size(brRadius * 2 - rightWidth, brRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }

    if (blRadius > 0f && bottomWidth > 0f && leftWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 90f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth/2f, size.height - (blRadius * 2) + (bottomWidth/2f)),
            size = Size(blRadius * 2 - leftWidth, blRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }
}
