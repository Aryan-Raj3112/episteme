package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.BlockStyle
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
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun buildSharedNativeVerticalFlowItems(
    book: SharedEpubBook,
    pages: List<ReaderPage>
): List<SharedNativeVerticalFlowItem> {
    val pagesByChapter = pages.groupBy { it.chapterIndex }
    return book.chapters.flatMapIndexed { chapterIndex, chapter ->
        val chapterPages = pagesByChapter[chapterIndex].orEmpty()
        val fallbackPage = chapterPages.firstOrNull()
            ?: chapter.toSharedNativeVerticalSyntheticPage(chapterIndex, pages.size + chapterIndex)
        val boundary = if (chapterIndex > 0) {
            listOf(
                SharedNativeVerticalFlowItem(
                    key = "chapter-$chapterIndex-gap",
                    kind = SharedNativeVerticalFlowItemKind.CHAPTER_GAP,
                    page = fallbackPage
                )
            )
        } else {
            emptyList()
        }
        when {
            chapter.semanticBlocks.isNotEmpty() -> {
                boundary + chapter.semanticBlocks.mapIndexed { ordinal, block ->
                    val pageIndex = chapterPages.findSharedNativeVerticalPageIndexForBlock(block)
                        ?: fallbackPage.pageIndex
                    val range = block.sharedNativeVerticalTextRange()
                        ?: (0 to chapter.plainText.length)
                    SharedNativeVerticalFlowItem(
                        key = "chapter-$chapterIndex-block-$ordinal-${block.blockIndex}",
                        kind = SharedNativeVerticalFlowItemKind.BLOCK,
                        page = ReaderPage(
                            pageIndex = pageIndex,
                            chapterIndex = chapterIndex,
                            chapterTitle = chapter.title,
                            text = block.sharedNativeVerticalText().ifBlank { chapter.plainText },
                            startOffset = range.first,
                            endOffset = range.second.coerceAtLeast(range.first),
                            semanticBlocks = listOf(block)
                        ),
                        block = block
                    )
                }
            }

            chapterPages.isNotEmpty() -> {
                boundary + chapterPages.map { page ->
                    SharedNativeVerticalFlowItem(
                        key = "chapter-$chapterIndex-page-${page.pageIndex}",
                        kind = SharedNativeVerticalFlowItemKind.TEXT_PAGE,
                        page = page
                    )
                }
            }

            chapter.plainText.isNotBlank() -> {
                boundary + SharedNativeVerticalFlowItem(
                    key = "chapter-$chapterIndex-text",
                    kind = SharedNativeVerticalFlowItemKind.TEXT_PAGE,
                    page = chapter.toSharedNativeVerticalSyntheticPage(chapterIndex, fallbackPage.pageIndex)
                )
            }

            else -> {
                boundary + SharedNativeVerticalFlowItem(
                    key = "chapter-$chapterIndex-empty",
                    kind = SharedNativeVerticalFlowItemKind.EMPTY_CHAPTER,
                    page = fallbackPage
                )
            }
        }
    }
}

internal fun SharedEpubChapter.toSharedNativeVerticalSyntheticPage(
    chapterIndex: Int,
    pageIndex: Int
): ReaderPage {
    return ReaderPage(
        pageIndex = pageIndex.coerceAtLeast(0),
        chapterIndex = chapterIndex,
        chapterTitle = title,
        text = plainText,
        startOffset = 0,
        endOffset = plainText.length,
        semanticBlocks = emptyList()
    )
}

internal fun List<ReaderPage>.findSharedNativeVerticalPageIndexForBlock(block: SemanticBlock): Int? {
    val range = block.sharedNativeVerticalTextRange()
    val blockIndex = block.blockIndex
    if (range != null) {
        val (start, end) = range
        firstOrNull { page ->
            if (start == end) {
                page.containsNativeCollapsedOffset(start)
            } else {
                start < page.endOffset && end > page.startOffset
            }
        }?.let { return it.pageIndex }
    }
    firstOrNull { page ->
        page.semanticBlocks.flattenNativeSemanticBlocks().any { it.blockIndex == blockIndex }
    }?.let { return it.pageIndex }
    return firstOrNull()?.pageIndex
}

internal fun List<SharedNativeVerticalFlowItem>.sharedNativeVerticalItemIndexForLocator(
    locator: ReaderLocator
): Int? {
    val chapterIndex = locator.chapterIndex
    if (chapterIndex != null) {
        locator.blockIndex?.let { blockIndex ->
            val sameBlock = indexOfFirst { item ->
                item.page.chapterIndex == chapterIndex &&
                    item.page.semanticBlocks.flattenNativeSemanticBlocks().any { block -> block.blockIndex == blockIndex }
            }
            if (sameBlock >= 0) return sameBlock
        }
        val targetOffset = locator.charOffset ?: locator.startOffset
        if (targetOffset != null) {
            val sameOffset = indexOfFirst { item ->
                item.page.chapterIndex == chapterIndex &&
                    item.page.containsNativeCollapsedOffset(targetOffset)
            }
            if (sameOffset >= 0) return sameOffset
        }
    }
    locator.pageIndex?.let { pageIndex ->
        val samePage = indexOfFirst { item -> item.page.pageIndex == pageIndex }
        if (samePage >= 0) return samePage
    }
    if (chapterIndex != null) {
        val sameChapter = indexOfFirst { item -> item.page.chapterIndex == chapterIndex }
        if (sameChapter >= 0) return sameChapter
    }
    return null
}

internal fun List<SharedNativeVerticalFlowItem>.sharedNativeVerticalItemIndexForPage(pageIndex: Int): Int? {
    val samePage = indexOfFirst { item -> item.page.pageIndex == pageIndex }
    if (samePage >= 0) return samePage
    val nearestPage = filter { item -> item.kind != SharedNativeVerticalFlowItemKind.CHAPTER_GAP }
        .minByOrNull { item -> kotlin.math.abs(item.page.pageIndex - pageIndex) }
    return nearestPage?.let { indexOf(it) }
}

internal fun SharedNativeVerticalFlowItem.toNativeVerticalLocator(): ReaderLocator {
    return page.toNativeReaderLocator().withFallbacks(
        blockIndex = block?.blockIndex,
        cfi = block?.cfi
    )
}

internal fun SemanticBlock.sharedNativeVerticalTextRange(): Pair<Int, Int>? {
    val textBlocks = flattenNativeSemanticBlock().filterIsInstance<SemanticTextBlock>()
    if (textBlocks.isEmpty()) return null
    val start = textBlocks.minOf { it.startCharOffsetInSource }
    val end = textBlocks.maxOf { it.startCharOffsetInSource + it.text.length }
    return start to end.coerceAtLeast(start)
}

internal fun SemanticBlock.sharedNativeVerticalText(): String {
    return flattenNativeSemanticBlock()
        .filterIsInstance<SemanticTextBlock>()
        .joinToString(separator = "\n") { it.text }
        .trim()
}

internal fun ReaderPage.toNativeReaderLocator(): ReaderLocator {
    val blockPosition = firstNativeLocatorBlockPosition()
    return ReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        blockIndex = blockPosition?.blockIndex,
        charOffset = blockPosition?.charOffset,
        textQuote = text.replace(Regex("\\s+"), " ").trim().take(160),
        cfi = blockPosition?.androidStyleCfi() ?: "desktop:$chapterIndex:$startOffset:$endOffset"
    )
}

internal data class SharedNativeLocatorBlockPosition(
    val blockIndex: Int,
    val charOffset: Int,
    val cfi: String? = null,
    val localCharOffset: Int = 0
) {
    fun androidStyleCfi(): String? {
        val base = cfi
            ?.takeIf { it.startsWith("/") }
            ?.substringBefore(':')
            ?: return null
        return "$base:${localCharOffset.coerceAtLeast(0)}"
    }
}

internal fun ReaderPage.firstNativeLocatorBlockPosition(): SharedNativeLocatorBlockPosition? {
    val blocks = semanticBlocks.flattenNativeSemanticBlocks()
    val textBlock = blocks
        .filterIsInstance<SemanticTextBlock>()
        .firstOrNull { it.text.isNotBlank() }
        ?: blocks.filterIsInstance<SemanticTextBlock>().firstOrNull()
    if (textBlock != null) {
        return SharedNativeLocatorBlockPosition(
            blockIndex = textBlock.blockIndex,
            charOffset = textBlock.startCharOffsetInSource,
            cfi = textBlock.cfi,
            localCharOffset = 0
        )
    }
    val firstBlock = blocks.firstOrNull() ?: return null
    return SharedNativeLocatorBlockPosition(firstBlock.blockIndex, 0, firstBlock.cfi, 0)
}

internal fun List<SemanticBlock>.flattenNativeSemanticBlocks(): List<SemanticBlock> {
    return flatMap { it.flattenNativeSemanticBlock() }
}

internal fun SemanticBlock.flattenNativeSemanticBlock(): List<SemanticBlock> {
    return when (this) {
        is SemanticList -> listOf(this) + items
        is SemanticTable -> listOf(this) + rows.flatMap { row -> row.flatMap { cell -> cell.content.flattenNativeSemanticBlocks() } }
        is SemanticFlexContainer -> listOf(this) + children.flattenNativeSemanticBlocks()
        is SemanticWrappingBlock -> listOf(this, floatedImage) + paragraphsToWrap
        is SemanticImage,
        is SemanticMath,
        is SemanticSpacer,
        is SemanticTextBlock -> listOf(this)
    }
}

internal fun String.toReaderAnnotatedString(
    searchQuery: String,
    searchHighlight: Color,
    chapterIndex: Int,
    pageIndex: Int,
    absoluteStartOffset: Int,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color
): AnnotatedString {
    val normalized = searchQuery.trim()
    return buildAnnotatedString {
        append(this@toReaderAnnotatedString)
        highlights.forEach { highlight ->
            applyHighlightToTextRange(
                highlight = highlight,
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                textStartOffset = absoluteStartOffset,
                textLength = this@toReaderAnnotatedString.length
            )
        }
        applySelectionToTextRange(
            selection = activeSelection,
            textStartOffset = absoluteStartOffset,
            textLength = this@toReaderAnnotatedString.length,
            color = selectionHighlight
        )
        if (normalized.length >= 2) {
            var startIndex = 0
            while (startIndex < this@toReaderAnnotatedString.length) {
                val index = this@toReaderAnnotatedString.indexOf(normalized, startIndex, ignoreCase = true)
                if (index < 0) break
                addStyle(
                    style = SpanStyle(background = searchHighlight),
                    start = index,
                    end = index + normalized.length
                )
                startIndex = index + normalized.length
            }
        }
    }
}

@Composable
internal fun SharedSemanticBlockStack(
    blocks: List<SemanticBlock>,
    page: ReaderPage,
    background: Color,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: ReaderSettings,
    includeTrailingBottomMargin: Boolean,
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    onTextLaidOut: ((SharedNativeTextFit) -> Unit)? = null,
    onBlockLaidOut: ((SharedNativeBlockFit) -> Unit)? = null
) {
    var previous: SemanticBlock? = null
    blocks.forEachIndexed { index, block ->
        SharedSemanticBlockView(
            block = block,
            page = page,
            background = background,
            foreground = foreground,
            searchQuery = searchQuery,
            searchHighlight = searchHighlight,
            highlights = highlights,
            activeSelection = activeSelection,
            selectionHighlight = selectionHighlight,
            fallbackTextAlign = fallbackTextAlign,
            fallbackFontFamily = fallbackFontFamily,
            settings = settings,
            marginTop = block.collapsedTopMarginDp(previous, settings),
            marginBottom = if (includeTrailingBottomMargin && index == blocks.lastIndex) {
                block.effectiveBottomMarginDp(settings)
            } else {
                0.dp
            },
            onReaderTap = onReaderTap,
            onSelectionChange = onSelectionChange,
            onSelectionGestureActiveChange = onSelectionGestureActiveChange,
            onHighlightSelected = onHighlightSelected,
            onLinkClicked = onLinkClicked,
            selectionLayouts = selectionLayouts,
            imageContent = imageContent,
            layoutIndex = index,
            onTextLaidOut = onTextLaidOut,
            onBlockLaidOut = onBlockLaidOut
        )
        previous = block
    }
}

@Composable
internal fun SharedSemanticBlockView(
    block: SemanticBlock,
    page: ReaderPage,
    background: Color,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: ReaderSettings,
    marginTop: Dp,
    marginBottom: Dp,
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    layoutIndex: Int? = null,
    onTextLaidOut: ((SharedNativeTextFit) -> Unit)? = null,
    onBlockLaidOut: ((SharedNativeBlockFit) -> Unit)? = null
) {
    val blockStyle = block.style.blockStyle.sharedNativeThemeBlockStyle(
        isDarkTheme = settings.darkMode,
        background = background,
        foreground = foreground
    )
    val modifier = Modifier
        .fillMaxWidth()
        .padding(
            start = blockStyle.margin.left.safeDp(),
            top = marginTop,
            end = blockStyle.margin.right.safeDp(),
            bottom = marginBottom
        )
        .sharedNativeCssBox(blockStyle)
        .padding(
            start = blockStyle.padding.left.safeDp(),
            top = blockStyle.padding.top.safeDp(),
            end = blockStyle.padding.right.safeDp(),
            bottom = blockStyle.padding.bottom.safeDp()
        )
        .sharedNativeVisibility(blockStyle)
    val measuredModifier = if (layoutIndex != null && onBlockLaidOut != null) {
        Modifier
            .onGloballyPositioned { coordinates ->
                onBlockLaidOut(block.toSharedNativeBlockFit(layoutIndex, coordinates))
            }
            .then(modifier)
    } else {
        modifier
    }

    SharedNativeCssBlockContainer(
        modifier = measuredModifier,
        blockStyle = blockStyle,
        blockIndex = block.blockIndex,
        imageContent = imageContent
    ) { contentModifier ->
        when (block) {
            is SemanticHeader -> {
                SharedSemanticTextView(
                    block = block,
                    page = page,
                    modifier = contentModifier,
                    background = background,
                    foreground = foreground,
                    searchQuery = searchQuery,
                    searchHighlight = searchHighlight,
                    highlights = highlights,
                    activeSelection = activeSelection,
                    selectionHighlight = selectionHighlight,
                    fallbackTextAlign = fallbackTextAlign,
                    fallbackFontFamily = fallbackFontFamily,
                    settings = settings,
                    fontWeight = FontWeight.Bold,
                    onReaderTap = onReaderTap,
                    onSelectionChange = onSelectionChange,
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
                    onTextLaidOut = onTextLaidOut
                )
            }

            is SemanticParagraph -> SharedSemanticTextView(block, page, contentModifier, background, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onReaderTap = onReaderTap, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts, onTextLaidOut = onTextLaidOut)
            is SemanticListItem -> SharedSemanticTextView(block, page, contentModifier, background, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onReaderTap = onReaderTap, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts, onTextLaidOut = onTextLaidOut)
            is SemanticTextBlock -> SharedSemanticTextView(block, page, contentModifier, background, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onReaderTap = onReaderTap, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts, onTextLaidOut = onTextLaidOut)

            is SemanticList -> {
                Column(modifier = contentModifier, verticalArrangement = Arrangement.Top) {
                    var previous: SemanticBlock? = null
                    block.items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.padding(
                                top = item.collapsedTopMarginDp(previous, settings),
                                bottom = if (index == block.items.lastIndex) item.effectiveBottomMarginDp(settings) else 0.dp
                            ),
                            verticalAlignment = Alignment.Top
                        ) {
                            val markerModifier = Modifier
                                .width(SharedNativeListItemMarkerAreaWidthDp.dp)
                                .padding(end = SharedNativeListItemMarkerEndPaddingDp.dp)
                            val markerImage = item.itemMarkerImage
                                ?: block.style.blockStyle.listStyleImage?.takeIf { it.isNotBlank() }
                            if (markerImage != null && imageContent != null) {
                                val markerSize = with(LocalDensity.current) { (settings.fontSize * 0.85f).sp.toDp() }
                                Box(
                                    modifier = markerModifier,
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    imageContent(
                                        SemanticImage(
                                            path = markerImage,
                                            altText = "",
                                            intrinsicWidth = null,
                                            intrinsicHeight = null,
                                            style = CssStyle(),
                                            elementId = null,
                                            cfi = null,
                                            blockIndex = item.blockIndex
                                        ),
                                        Modifier.size(markerSize)
                                    )
                                }
                            } else {
                                Text(
                                    text = sharedNativeListMarker(
                                        index = index,
                                        isOrdered = block.isOrdered,
                                        listStyleType = block.style.blockStyle.listStyleType
                                    ),
                                    color = foreground,
                                    modifier = markerModifier,
                                    textAlign = TextAlign.End,
                                    style = item.renderedTextStyle(
                                        settings = settings,
                                        fallbackFontFamily = fallbackFontFamily,
                                        fallbackTextAlign = TextAlign.End,
                                        background = background,
                                        foreground = foreground
                                    )
                                )
                            }
                            SharedSemanticTextView(
                                block = item,
                                page = page,
                                modifier = Modifier.weight(1f),
                                background = background,
                                foreground = foreground,
                                searchQuery = searchQuery,
                                searchHighlight = searchHighlight,
                                highlights = highlights,
                                activeSelection = activeSelection,
                                selectionHighlight = selectionHighlight,
                                fallbackTextAlign = fallbackTextAlign,
                                fallbackFontFamily = fallbackFontFamily,
                                settings = settings,
                                onReaderTap = onReaderTap,
                                onSelectionChange = onSelectionChange,
                                onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                                onHighlightSelected = onHighlightSelected,
                                onLinkClicked = onLinkClicked,
                                selectionLayouts = selectionLayouts,
                                onTextLaidOut = onTextLaidOut
                            )
                        }
                        previous = item
                    }
                }
            }

            is SemanticFlexContainer -> {
                if (blockStyle.flexDirection == "row") {
                    Row(
                        modifier = contentModifier.fillMaxWidth(),
                        horizontalArrangement = blockStyle.sharedNativeFlexHorizontalArrangement(),
                        verticalAlignment = blockStyle.sharedNativeFlexVerticalAlignment()
                    ) {
                        block.children.forEach { child ->
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                SharedSemanticBlockStack(
                                    blocks = listOf(child),
                                    page = page,
                                    background = background,
                                    foreground = foreground,
                                    searchQuery = searchQuery,
                                    searchHighlight = searchHighlight,
                                    highlights = highlights,
                                    activeSelection = activeSelection,
                                    selectionHighlight = selectionHighlight,
                                    fallbackTextAlign = fallbackTextAlign,
                                    fallbackFontFamily = fallbackFontFamily,
                                    settings = settings,
                                    includeTrailingBottomMargin = true,
                                    onReaderTap = onReaderTap,
                                    onSelectionChange = onSelectionChange,
                                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                                    onHighlightSelected = onHighlightSelected,
                                    onLinkClicked = onLinkClicked,
                                    selectionLayouts = selectionLayouts,
                                    imageContent = imageContent,
                                    onTextLaidOut = onTextLaidOut
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = contentModifier.fillMaxWidth(),
                        verticalArrangement = blockStyle.sharedNativeFlexVerticalArrangement(),
                        horizontalAlignment = blockStyle.sharedNativeFlexHorizontalAlignment()
                    ) {
                        SharedSemanticBlockStack(
                            blocks = block.children,
                            page = page,
                            background = background,
                            foreground = foreground,
                            searchQuery = searchQuery,
                            searchHighlight = searchHighlight,
                            highlights = highlights,
                            activeSelection = activeSelection,
                            selectionHighlight = selectionHighlight,
                            fallbackTextAlign = fallbackTextAlign,
                            fallbackFontFamily = fallbackFontFamily,
                            settings = settings,
                            includeTrailingBottomMargin = true,
                            onReaderTap = onReaderTap,
                            onSelectionChange = onSelectionChange,
                            onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                            onHighlightSelected = onHighlightSelected,
                            onLinkClicked = onLinkClicked,
                            selectionLayouts = selectionLayouts,
                            imageContent = imageContent,
                            onTextLaidOut = onTextLaidOut
                        )
                    }
                }
            }

            is SemanticWrappingBlock -> {
                SharedNativeWrappingBlock(
                    block = block,
                    page = page,
                    modifier = contentModifier,
                    background = background,
                    foreground = foreground,
                    searchQuery = searchQuery,
                    searchHighlight = searchHighlight,
                    highlights = highlights,
                    activeSelection = activeSelection,
                    selectionHighlight = selectionHighlight,
                    fallbackTextAlign = fallbackTextAlign,
                    fallbackFontFamily = fallbackFontFamily,
                    settings = settings,
                    imageContent = imageContent,
                    onReaderTap = onReaderTap,
                    onSelectionChange = onSelectionChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked
                )
            }

            is SemanticTable -> {
                Column(modifier = contentModifier, verticalArrangement = Arrangement.Top) {
                    block.rows.forEach { row ->
                        val hasFixedWidths = row.any { cell -> cell.style.blockStyle.width.isPositiveSpecified() }
                        val cellGap = if (blockStyle.borderCollapse == "collapse") {
                            0.dp
                        } else {
                            blockStyle.borderSpacing.takeIfPositiveSpecified() ?: 8.dp
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(cellGap)
                        ) {
                            row.forEach { cell ->
                                val cellBlockStyle = cell.style.blockStyle.sharedNativeThemeBlockStyle(
                                    isDarkTheme = settings.darkMode,
                                    background = background,
                                    foreground = foreground
                                )
                                val cellModifier = Modifier
                                    .then(
                                        if (hasFixedWidths && cellBlockStyle.width.isPositiveSpecified()) {
                                            Modifier.width(cellBlockStyle.width)
                                        } else {
                                            Modifier.weight(cell.colspan.toFloat().coerceAtLeast(1f), fill = true)
                                        }
                                    )
                                    .sharedNativeCssBox(cellBlockStyle)
                                    .padding(
                                        start = cellBlockStyle.padding.left.safeDp(),
                                        top = cellBlockStyle.padding.top.safeDp(),
                                        end = cellBlockStyle.padding.right.safeDp(),
                                        bottom = cellBlockStyle.padding.bottom.safeDp()
                                    )
                                    .sharedNativeVisibility(cellBlockStyle)
                                val cellAlignment = when (cell.style.paragraphStyle.textAlign) {
                                    TextAlign.Center -> Alignment.CenterHorizontally
                                    TextAlign.End,
                                    TextAlign.Right -> Alignment.End
                                    else -> Alignment.Start
                                }
                                SharedNativeCssBlockContainer(
                                    modifier = cellModifier,
                                    blockStyle = cellBlockStyle,
                                    blockIndex = cell.content.firstOrNull()?.blockIndex ?: block.blockIndex,
                                    imageContent = imageContent,
                                    contentAlignment = cell.style.sharedNativeTableCellContentAlignment(cellAlignment)
                                ) { cellContentModifier ->
                                    Column(
                                        modifier = cellContentModifier,
                                        horizontalAlignment = cellAlignment
                                    ) {
                                        SharedSemanticBlockStack(
                                            blocks = cell.content,
                                            page = page,
                                            background = background,
                                            foreground = foreground,
                                            searchQuery = searchQuery,
                                            searchHighlight = searchHighlight,
                                            highlights = highlights,
                                            activeSelection = activeSelection,
                                            selectionHighlight = selectionHighlight,
                                            fallbackTextAlign = fallbackTextAlign,
                                            fallbackFontFamily = fallbackFontFamily,
                                            settings = settings,
                                            includeTrailingBottomMargin = true,
                                            onReaderTap = onReaderTap,
                                            onSelectionChange = onSelectionChange,
                                            onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                                            onHighlightSelected = onHighlightSelected,
                                            onLinkClicked = onLinkClicked,
                                            selectionLayouts = selectionLayouts,
                                            imageContent = imageContent,
                                            onTextLaidOut = onTextLaidOut
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is SemanticImage -> {
                SharedNativeImageBlock(
                    block = block,
                    foreground = foreground,
                    settings = settings,
                    imageContent = imageContent,
                    modifier = contentModifier
                )
            }

            is SemanticMath -> {
                SharedNativeMathBlock(
                    block = block,
                    foreground = foreground,
                    settings = settings,
                    imageContent = imageContent,
                    modifier = contentModifier
                )
            }

            is SemanticSpacer -> Spacer(contentModifier.height(if (block.isExplicitLineBreak) 8.dp else 16.dp))
        }
    }
}

internal fun CssStyle.sharedNativeTableCellContentAlignment(horizontalAlignment: Alignment.Horizontal): Alignment {
    val horizontal = when (horizontalAlignment) {
        Alignment.CenterHorizontally -> "center"
        Alignment.End -> "end"
        else -> "start"
    }
    val vertical = when (verticalAlign?.lowercase()) {
        "middle", "center" -> "center"
        "bottom", "text-bottom" -> "bottom"
        else -> "top"
    }
    return when (vertical to horizontal) {
        "center" to "center" -> Alignment.Center
        "center" to "end" -> Alignment.CenterEnd
        "center" to "start" -> Alignment.CenterStart
        "bottom" to "center" -> Alignment.BottomCenter
        "bottom" to "end" -> Alignment.BottomEnd
        "bottom" to "start" -> Alignment.BottomStart
        "top" to "center" -> Alignment.TopCenter
        "top" to "end" -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
}

@Composable
internal fun SharedNativeCssBlockContainer(
    modifier: Modifier,
    blockStyle: BlockStyle,
    blockIndex: Int,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (Modifier) -> Unit
) {
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        val backgroundImage = remember(
            blockStyle.backgroundImage,
            blockStyle.objectFit,
            blockStyle.objectPosition,
            blockStyle.filter,
            blockIndex
        ) {
            blockStyle.toSharedNativeBackgroundImage(blockIndex)
        }
        if (backgroundImage != null && imageContent != null) {
            imageContent(backgroundImage, Modifier.matchParentSize())
        }
        content(Modifier.fillMaxWidth())
    }
}

internal fun BlockStyle.toSharedNativeBackgroundImage(blockIndex: Int): SemanticImage? {
    val path = sharedNativeBackgroundImagePath() ?: return null
    return SemanticImage(
        path = path,
        altText = "",
        intrinsicWidth = null,
        intrinsicHeight = null,
        style = CssStyle(
            blockStyle = BlockStyle(
                objectFit = objectFit,
                objectPosition = objectPosition,
                filter = filter
            )
        ),
        elementId = null,
        cfi = null,
        blockIndex = blockIndex
    )
}

internal fun BlockStyle.sharedNativeBackgroundImagePath(): String? {
    val raw = backgroundImage?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (raw.contains("gradient(", ignoreCase = true)) return null
    val value = SharedNativeCssUrlRegex.find(raw)?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: raw
    return value
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
}

internal data class SharedNativeWrappedTextLine(
    val text: AnnotatedString,
    val layout: TextLayoutResult,
    val topLeft: Offset
)

internal data class SharedNativeWrappedParagraph(
    val text: AnnotatedString,
    val style: TextStyle
)

@Composable
internal fun SharedNativeWrappingBlock(
    block: SemanticWrappingBlock,
    page: ReaderPage,
    modifier: Modifier,
    background: Color,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: ReaderSettings,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var wrappedLines by remember(block, page.pageIndex) {
        mutableStateOf<List<SharedNativeWrappedTextLine>>(emptyList())
    }
    val latestWrappedLines = rememberUpdatedState(wrappedLines)
    val latestActiveSelection = rememberUpdatedState(activeSelection)
    val fullText = remember(block.paragraphsToWrap) {
        block.paragraphsToWrap.joinToString(separator = "\n\n") { it.text }
    }
    val wrappedParagraphs = mutableListOf<SharedNativeWrappedParagraph>()
    for (paragraph in block.paragraphsToWrap) {
        val textStyle = paragraph.renderedTextStyle(
            settings = settings,
            fallbackFontFamily = fallbackFontFamily,
            fallbackTextAlign = fallbackTextAlign,
            background = background,
            foreground = foreground
        )
        wrappedParagraphs += SharedNativeWrappedParagraph(
            text = paragraph.toAnnotatedString(
                query = searchQuery,
                highlightColor = searchHighlight,
                highlights = highlights,
                activeSelection = activeSelection,
                selectionHighlight = selectionHighlight,
                fallbackTextAlign = fallbackTextAlign,
                blockFontSizeSp = textStyle.fontSize.value,
                chapterIndex = page.chapterIndex,
                pageIndex = page.pageIndex,
                blockCfi = paragraph.cfi,
                blockIndex = paragraph.blockIndex,
                blockCharOffset = paragraph.startCharOffsetInSource,
                background = background,
                foreground = foreground,
                isDarkTheme = settings.darkMode
            ),
            style = textStyle
        )
    }

    Layout(
        content = {
            if (imageContent != null) {
                imageContent(block.floatedImage, Modifier.fillMaxSize())
            } else {
                Text(
                    text = block.floatedImage.altText?.takeIf { it.isNotBlank() }
                        ?: block.floatedImage.path.substringAfterLast('/').substringAfterLast('\\'),
                    color = foreground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        modifier = modifier
            .drawBehind {
                wrappedLines.forEach { line ->
                    drawText(line.layout, topLeft = line.topLeft)
                }
            }
            .pointerInput(block.blockIndex, wrappedLines) {
                detectTapGestures(
                    onTap = { offset ->
                        latestWrappedLines.value.firstNotNullOfOrNull { line ->
                            line.hitAnnotation(offset)
                        }?.let { annotation ->
                            when (annotation.tag) {
                                ReaderNativeAnnotationUrl -> {
                                    onSelectionChange(null)
                                    onLinkClicked(
                                        SharedNativeReaderLinkClick(
                                            href = annotation.item,
                                            chapterIndex = page.chapterIndex,
                                            text = fullText
                                        )
                                    )
                                    return@detectTapGestures
                                }

                                ReaderNativeAnnotationHighlight -> {
                                    onSelectionChange(null)
                                    onHighlightSelected(annotation.item)
                                    return@detectTapGestures
                                }
                            }
                        }
                        if (latestActiveSelection.value == null) {
                            onReaderTap()
                        }
                        onSelectionChange(null)
                    }
                )
            }
    ) { measurables, constraints ->
        val maxWidthPx = constraints.maxWidth.coerceAtLeast(0)
        val imageScale = settings.imageScale.coerceIn(0.5f, 2f)
        val imageSize = sharedNativeImageRenderSizePxOrFallback(
            block = block.floatedImage,
            density = density,
            maxWidthPx = maxWidthPx.toFloat(),
            imageScale = imageScale,
            settings = settings
        )
        val imageWidthPx = imageSize.first.roundToInt().coerceIn(0, maxWidthPx)
        val imageHeightPx = imageSize.second.roundToInt().coerceAtLeast(0)
        val imagePlaceable = if (imageWidthPx > 0 && imageHeightPx > 0 && measurables.isNotEmpty()) {
            measurables.first().measure(Constraints.fixed(imageWidthPx, imageHeightPx))
        } else {
            null
        }
        val effectiveImageWidth = imagePlaceable?.width ?: 0
        val effectiveImageHeight = imagePlaceable?.height ?: 0
        val floatLeft = block.floatedImage.style.blockStyle.float != "right"

        var currentY = 0f
        val lines = mutableListOf<SharedNativeWrappedTextLine>()
        wrappedParagraphs.forEachIndexed { paragraphIndex, paragraph ->
            currentY = layoutSharedNativeWrappedLines(
                text = paragraph.text,
                style = paragraph.style,
                textMeasurer = textMeasurer,
                constraints = constraints,
                currentY = currentY,
                effectiveImageWidth = effectiveImageWidth,
                effectiveImageHeight = effectiveImageHeight,
                floatLeft = floatLeft,
                lines = lines
            )
            if (paragraphIndex < block.paragraphsToWrap.lastIndex) {
                currentY += block.paragraphsToWrap.sharedNativeCollapsedParagraphGapPx(
                    index = paragraphIndex,
                    settings = settings,
                    density = density
                )
            }
        }
        if (wrappedLines.sharedNativeWrappedLineSignature() != lines.sharedNativeWrappedLineSignature()) {
            wrappedLines = lines
        }
        val height = maxOf(currentY.roundToInt(), effectiveImageHeight).coerceAtLeast(0)
        layout(maxWidthPx, height) {
            imagePlaceable?.let { placeable ->
                val x = if (floatLeft) 0 else maxWidthPx - placeable.width
                placeable.placeRelative(x = x.coerceAtLeast(0), y = 0)
            }
        }
    }
}

internal fun layoutSharedNativeWrappedLines(
    text: AnnotatedString,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    currentY: Float,
    effectiveImageWidth: Int,
    effectiveImageHeight: Int,
    floatLeft: Boolean,
    lines: MutableList<SharedNativeWrappedTextLine>
): Float {
    var y = currentY
    var textOffset = 0
    while (textOffset < text.length) {
        val isBesideImage = y < effectiveImageHeight
        val currentMaxWidth = if (isBesideImage) {
            (constraints.maxWidth - effectiveImageWidth).coerceAtLeast(0)
        } else {
            constraints.maxWidth
        }
        if (currentMaxWidth <= 0) {
            if (isBesideImage) {
                y = effectiveImageHeight.toFloat()
                continue
            }
            break
        }

        val lineConstraints = constraints.copy(minWidth = 0, maxWidth = currentMaxWidth)
        val remainingText = text.subSequence(textOffset, text.length)
        val measuredRemaining = textMeasurer.measure(
            text = remainingText,
            style = style,
            constraints = lineConstraints
        )
        val firstLineEndOffset = measuredRemaining.getLineEnd(0, visibleEnd = true)
        if (firstLineEndOffset == 0 && remainingText.length > 0) {
            textOffset++
            continue
        }
        if (firstLineEndOffset == 0) break

        val lineText = remainingText.subSequence(0, firstLineEndOffset)
        val lineLayout = textMeasurer.measure(
            text = lineText,
            style = style,
            constraints = lineConstraints
        )
        val x = if (isBesideImage && floatLeft) effectiveImageWidth.toFloat() else 0f
        lines += SharedNativeWrappedTextLine(
            text = lineText,
            layout = lineLayout,
            topLeft = Offset(x, y)
        )
        y += lineLayout.size.height
        textOffset += firstLineEndOffset
        while (textOffset < text.length && text.text[textOffset].isWhitespace()) {
            textOffset++
        }
    }
    return y
}

internal data class SharedNativeWrappedAnnotation(
    val tag: String,
    val item: String
)

internal fun List<SharedNativeWrappedTextLine>.sharedNativeWrappedLineSignature(): String {
    return joinToString(separator = "|") { line ->
        "${line.text.hashCode()}:${line.layout.size.width}x${line.layout.size.height}@" +
            "${line.topLeft.x.roundToInt()},${line.topLeft.y.roundToInt()}"
    }
}

internal fun SharedNativeWrappedTextLine.hitAnnotation(offset: Offset): SharedNativeWrappedAnnotation? {
    val local = Offset(offset.x - topLeft.x, offset.y - topLeft.y)
    if (
        local.x < 0f ||
        local.y < 0f ||
        local.x > layout.size.width.toFloat() ||
        local.y > layout.size.height.toFloat()
    ) {
        return null
    }
    val textOffset = layout.getOffsetForPosition(local).coerceIn(0, text.length)
    return text.stringAnnotationAt(ReaderNativeAnnotationUrl, textOffset)?.let { href ->
        SharedNativeWrappedAnnotation(ReaderNativeAnnotationUrl, href)
    } ?: text.stringAnnotationAt(ReaderNativeAnnotationHighlight, textOffset)?.let { highlightId ->
        SharedNativeWrappedAnnotation(ReaderNativeAnnotationHighlight, highlightId)
    }
}

internal fun List<SemanticParagraph>.sharedNativeCollapsedParagraphGapPx(
    index: Int,
    settings: ReaderSettings,
    density: Density
): Float {
    val current = getOrNull(index) ?: return 0f
    val next = getOrNull(index + 1) ?: return 0f
    val explicitGap = maxOf(
        current.style.blockStyle.margin.bottom.safeDp(),
        next.style.blockStyle.margin.top.safeDp()
    )
    return with(density) {
        if (explicitGap != 0.dp) {
            explicitGap.toPx()
        } else {
            (settings.fontSize * settings.paragraphSpacing).sp.toPx()
        }
    }
}

@Composable
internal fun SharedNativeMathBlock(
    block: SemanticMath,
    foreground: Color,
    settings: ReaderSettings,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val image = remember(block.svgContent, block.svgWidth, block.svgHeight, block.svgViewBox, block.style) {
        block.toSharedNativeSvgImage()
    }
    if (image != null && imageContent != null) {
        SharedNativeImageBlock(
            block = image,
            foreground = foreground,
            settings = settings,
            imageContent = imageContent,
            modifier = modifier
        )
    } else {
        Text(
            text = block.altText ?: "Equation",
            color = foreground,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun SharedNativeImageBlock(
    block: SemanticImage,
    foreground: Color,
    settings: ReaderSettings,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = block.imageContentAlignment()
    ) {
        val imageModifier = Modifier.sharedNativeImageSize(block, settings, maxWidth)
        if (imageContent != null) {
            imageContent(block, imageModifier)
        } else {
            Text(
                text = block.altText?.takeIf { it.isNotBlank() } ?: block.path.substringAfterLast('/').substringAfterLast('\\'),
                color = foreground.copy(alpha = 0.7f),
                modifier = imageModifier,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal fun SemanticMath.toSharedNativeSvgImage(): SemanticImage? {
    val svg = svgContent?.takeIf { it.isNotBlank() } ?: return null
    val viewBoxSize = svgViewBox.sharedNativeSvgViewBoxSize()
    return SemanticImage(
        path = "data:image/svg+xml;base64,${Base64.Default.encode(svg.encodeToByteArray())}",
        altText = altText ?: "SVG Image",
        intrinsicWidth = svgWidth.sharedNativeSvgDimensionPx() ?: viewBoxSize?.first,
        intrinsicHeight = svgHeight.sharedNativeSvgDimensionPx() ?: viewBoxSize?.second,
        style = style,
        elementId = elementId,
        cfi = cfi,
        blockIndex = blockIndex
    )
}

internal fun String?.sharedNativeSvgDimensionPx(): Float? {
    val trimmed = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val numeric = trimmed
        .removeSuffix("px")
        .removeSuffix("PX")
        .toFloatOrNull()
        ?.takeIf { it > 0f }
    if (numeric != null) return numeric
    val pointValue = trimmed
        .removeSuffix("pt")
        .removeSuffix("PT")
        .toFloatOrNull()
        ?.takeIf { it > 0f }
    return pointValue?.let { it * 1.3333334f }
}

internal fun String?.sharedNativeSvgViewBoxSize(): Pair<Float, Float>? {
    val parts = this
        ?.trim()
        ?.split(Regex("[,\\s]+"))
        ?.mapNotNull { it.toFloatOrNull() }
        ?: return null
    if (parts.size < 4) return null
    val width = parts[2]
    val height = parts[3]
    return if (width > 0f && height > 0f) width to height else null
}

internal fun SemanticImage.imageContentAlignment(): Alignment {
    val style = style.blockStyle
    return when {
        style.float == "right" || style.horizontalAlign == "right" || style.horizontalAlign == "end" -> Alignment.CenterEnd
        style.float == "left" || style.horizontalAlign == "left" || style.horizontalAlign == "start" -> Alignment.CenterStart
        else -> Alignment.Center
    }
}

internal fun SemanticImage.sharedNativeImageContentScale(): ContentScale {
    return when (style.blockStyle.objectFit) {
        "cover" -> ContentScale.Crop
        "fill" -> ContentScale.FillBounds
        "contain", "scale-down" -> ContentScale.Fit
        else -> ContentScale.Fit
    }
}

internal fun SemanticImage.sharedNativeImageColorMatrix(): FloatArray? {
    if (style.blockStyle.filter != "invert(100%)") return null
    return floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
}

internal fun SemanticImage.sharedNativeImageColorFilter(): ColorFilter? {
    val matrix = sharedNativeImageColorMatrix() ?: return null
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

@Composable
internal fun Modifier.sharedNativeImageSize(
    block: SemanticImage,
    settings: ReaderSettings,
    maxWidth: Dp
): Modifier {
    val density = LocalDensity.current
    val style = block.style.blockStyle
    val imageScale = settings.imageScale.coerceIn(0.5f, 2f)
    val scaledSize = sharedNativeImageRenderSizeDp(
        block = block,
        density = density,
        maxWidth = maxWidth,
        imageScale = imageScale
    )

    return this
        .then(
            if (scaledSize != null) {
                Modifier
                    .width(scaledSize.first)
                    .height(scaledSize.second)
            } else if (style.width.isPositiveSpecified()) {
                Modifier.width(style.width)
            } else {
                Modifier.fillMaxWidth()
            }
        )
        .then(
            if (scaledSize == null && style.maxWidth.isPositiveSpecified()) {
                Modifier.widthIn(max = style.maxWidth)
            } else {
                Modifier
            }
        )
        .then(
            if (scaledSize == null) {
                val fallbackHeight = style.height.takeIfPositiveSpecified()
                    ?: with(density) { (settings.fontSize * 8f).sp.toDp() }
                Modifier.height(fallbackHeight)
            } else {
                Modifier
            }
        )
}

internal fun sharedNativeImageRenderSizeDp(
    block: SemanticImage,
    density: Density,
    maxWidth: Dp,
    imageScale: Float
): Pair<Dp, Dp>? {
    val maxWidthPx = with(density) { maxWidth.toPx() }
    return sharedNativeImageRenderSizePx(
        block = block,
        density = density,
        maxWidthPx = maxWidthPx,
        imageScale = imageScale
    )?.let { (widthPx, heightPx) ->
        with(density) {
            widthPx.toDp() to heightPx.toDp()
        }
    }
}

internal fun sharedNativeImageRenderSizePx(
    block: SemanticImage,
    density: Density,
    maxWidthPx: Float,
    imageScale: Float
): Pair<Float, Float>? {
    val intrinsicWidth = block.intrinsicWidth
    val intrinsicHeight = block.intrinsicHeight
    if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f || intrinsicHeight <= 0f) {
        return null
    }

    val style = block.style.blockStyle
    val aspectRatio = intrinsicHeight / intrinsicWidth
    val baseWidthPx = with(density) {
        if (style.width.isPositiveSpecified()) style.width.toPx() else maxWidthPx
    }

    var scaledWidthPx = baseWidthPx * imageScale
    if (style.maxWidth.isPositiveSpecified()) {
        scaledWidthPx = scaledWidthPx.coerceAtMost(with(density) { style.maxWidth.toPx() } * imageScale)
    }
    scaledWidthPx = scaledWidthPx.coerceAtMost(maxWidthPx)

    return scaledWidthPx to (scaledWidthPx * aspectRatio)
}

internal fun sharedNativeImageRenderSizePxOrFallback(
    block: SemanticImage,
    density: Density,
    maxWidthPx: Float,
    imageScale: Float,
    settings: ReaderSettings
): Pair<Float, Float> {
    sharedNativeImageRenderSizePx(
        block = block,
        density = density,
        maxWidthPx = maxWidthPx,
        imageScale = imageScale
    )?.let { return it }
    val style = block.style.blockStyle
    val widthPx = with(density) {
        when {
            style.width.isPositiveSpecified() -> style.width.toPx()
            style.maxWidth.isPositiveSpecified() -> maxWidthPx.coerceAtMost(style.maxWidth.toPx())
            else -> maxWidthPx
        }
    }.coerceAtLeast(1f)
    val heightPx = with(density) {
        (style.height.takeIfPositiveSpecified() ?: (settings.fontSize * 8f).sp.toDp()).toPx()
    }.coerceAtLeast(1f)
    return widthPx to heightPx
}
