package com.aryan.reader.shared.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SharedMeasuredEpubPaginator(
    private val textMeasurer: TextMeasurer,
    private val density: Density,
    private val fontFamily: FontFamily = FontFamily.Default,
    private val pageCache: SharedEpubPaginationCache? = null,
    private val cacheWriteScope: CoroutineScope? = null
) {
    suspend fun paginate(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec
    ): List<ReaderPage> {
        currentCoroutineContext().ensureActive()
        pageCache?.load(
            book = book,
            settings = settings,
            viewport = viewport,
            density = density.density,
            fontScale = density.fontScale
        )?.let { cached ->
            logEpubPagination {
                "cache_hit book=\"${book.title.logPreview()}\" pages=${cached.size} " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} spread=${settings.pageSpreadMode}"
            }
            return cached
        }

        currentCoroutineContext().ensureActive()
        val geometry = measuredPageGeometryFor(settings, viewport, density.density)
        logEpubPagination {
            "paginate_start book=\"${book.title.logPreview()}\" chapters=${book.chapters.size} " +
                "viewport=${viewport.widthPx}x${viewport.heightPx} page=${geometry.pageWidthPx}x${geometry.pageHeightPx} " +
                "spread=${settings.pageSpreadMode} font=${settings.fontSize} lineSpacing=${settings.lineSpacing} " +
                "margins=${settings.resolvedHorizontalMargin}x${settings.resolvedVerticalMargin} " +
                "pageWidthSetting=${settings.pageWidth} density=${density.density} fontScale=${density.fontScale}"
        }
        val baseStyle = TextStyle(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineSpacing).sp,
            fontFamily = fontFamily,
            textAlign = settings.textAlign.toComposeTextAlign()
        )
        val pages = mutableListOf<ReaderPage>()
        book.chapters.forEachIndexed { chapterIndex, chapter ->
            currentCoroutineContext().ensureActive()
            pages += paginateChapter(
                chapter = chapter,
                chapterIndex = chapterIndex,
                firstPageIndex = pages.size,
                settings = settings,
                geometry = geometry,
                baseStyle = baseStyle
            )
        }
        currentCoroutineContext().ensureActive()
        val measuredPages = pages.mapIndexed { index, page -> page.copy(pageIndex = index) }
        pageCache?.let { cache ->
            val savePages = suspend {
                cache.save(
                    book = book,
                    settings = settings,
                    viewport = viewport,
                    pages = measuredPages,
                    density = density.density,
                    fontScale = density.fontScale
                )
            }
            if (cacheWriteScope != null) {
                cacheWriteScope.launch { savePages() }
            } else {
                savePages()
            }
        }
        logEpubPagination {
            "paginate_complete book=\"${book.title.logPreview()}\" pages=${measuredPages.size} " +
                "viewport=${viewport.widthPx}x${viewport.heightPx} page=${geometry.pageWidthPx}x${geometry.pageHeightPx}"
        }
        return measuredPages
    }

    private suspend fun paginateChapter(
        chapter: SharedEpubChapter,
        chapterIndex: Int,
        firstPageIndex: Int,
        settings: ReaderSettings,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle
    ): List<ReaderPage> {
        currentCoroutineContext().ensureActive()
        val sourceBlocks = chapter.semanticBlocks.ifEmpty { chapter.plainText.toPlainSemanticBlocks() }
        if (sourceBlocks.isEmpty()) {
            return listOf(
                ReaderPage(
                    pageIndex = firstPageIndex,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title,
                    text = "",
                    startOffset = 0,
                    endOffset = 0
                )
            )
        }

        val pages = mutableListOf<ReaderPage>()
        val queue = ArrayDeque<SemanticBlock>().apply { addAll(sourceBlocks) }
        var pageBlocks = mutableListOf<SemanticBlock>()
        var usedHeight = 0

        logEpubPagination {
            "chapter_start chapter=$chapterIndex title=\"${chapter.title.logPreview()}\" " +
                "sourceBlocks=${sourceBlocks.size} plainChars=${chapter.plainText.length} pageHeightPx=${geometry.pageHeightPx}"
        }

        fun emitPage(reason: String) {
            if (pageBlocks.isEmpty()) return
            val page = pageBlocks.toReaderPage(
                pageIndex = firstPageIndex + pages.size,
                chapterIndex = chapterIndex,
                chapterTitle = chapter.title
            )
            logEpubPagination {
                "emit_page reason=$reason page=${page.pageIndex + 1} chapter=$chapterIndex " +
                    "usedPx=$usedHeight pageHeightPx=${geometry.pageHeightPx} remainingPx=${geometry.pageHeightPx - usedHeight} " +
                    "blocks=${pageBlocks.size} range=${page.startOffset}..${page.endOffset} textChars=${page.text.length}"
            }
            logReaderGapPagination {
                val firstTopMargin = pageBlocks.firstOrNull()?.effectiveTopMarginPx() ?: 0
                val trailingBottomMargin = pageBlocks.lastOrNull()?.effectiveBottomMarginPx(settings) ?: 0
                "paginator_page layer=measured_page reason=$reason page=${page.pageIndex + 1} " +
                    "chapter=$chapterIndex usedPx=$usedHeight pageHeightPx=${geometry.pageHeightPx} " +
                    "remainingPx=${geometry.pageHeightPx - usedHeight} blocks=${pageBlocks.size} " +
                    "firstTopMarginPx=$firstTopMargin trailingBottomMarginPx=$trailingBottomMargin " +
                    "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length}"
            }
            pages += page
            pageBlocks = mutableListOf()
            usedHeight = 0
        }

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val block = queue.removeFirst()
            val blockHeight = measureBlock(block, geometry, baseStyle, settings)
            val spaceBeforeBlock = block.collapsedMarginBefore(pageBlocks.lastOrNull(), settings)
            val requiredHeight = blockHeight + spaceBeforeBlock
            val fitsCurrent = requiredHeight <= geometry.pageHeightPx - usedHeight
            if (fitsCurrent) {
                pageBlocks += block
                usedHeight += requiredHeight
                continue
            }

            val remainingHeight = (geometry.pageHeightPx - usedHeight).coerceAtLeast(0)
            val splitAvailableHeight = (remainingHeight - spaceBeforeBlock).coerceAtLeast(0)
            val split = splitBlock(block, splitAvailableHeight, geometry, baseStyle, settings)
            if (split != null && split.first.hasReadableContent()) {
                val splitHeight = measureBlock(split.first, geometry, baseStyle, settings)
                logEpubPagination {
                    "split_current block=${block.kindName()} blockPx=$blockHeight splitPx=$splitHeight " +
                        "spaceBeforePx=$spaceBeforeBlock remainingPx=$remainingHeight " +
                        "splitAvailablePx=$splitAvailableHeight usedPx=$usedHeight " +
                        "pageHeightPx=${geometry.pageHeightPx} chapter=$chapterIndex"
                }
                pageBlocks += split.first
                usedHeight += spaceBeforeBlock + splitHeight
                if (split.second.hasReadableContent()) queue.addFirst(split.second)
                emitPage("split_current")
                continue
            }

            emitPage("before_block")
            val newPageSpaceBefore = block.collapsedMarginBefore(previous = null, settings)
            if (blockHeight + newPageSpaceBefore <= geometry.pageHeightPx) {
                pageBlocks += block
                usedHeight = blockHeight + newPageSpaceBefore
                continue
            }

            val oversizedAvailableHeight = (geometry.pageHeightPx - newPageSpaceBefore).coerceAtLeast(0)
            val oversizedSplit = splitBlock(block, oversizedAvailableHeight, geometry, baseStyle, settings)
            if (oversizedSplit != null && oversizedSplit.first.hasReadableContent()) {
                val splitHeight = measureBlock(oversizedSplit.first, geometry, baseStyle, settings)
                val page = listOf(oversizedSplit.first).toReaderPage(
                    pageIndex = firstPageIndex + pages.size,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title
                )
                logEpubPagination {
                    "emit_page reason=split_oversized page=${page.pageIndex + 1} chapter=$chapterIndex " +
                        "block=${block.kindName()} blockPx=$blockHeight splitPx=$splitHeight " +
                        "spaceBeforePx=$newPageSpaceBefore pageHeightPx=${geometry.pageHeightPx} " +
                        "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length}"
                }
                pages += page
                if (oversizedSplit.second.hasReadableContent()) queue.addFirst(oversizedSplit.second)
            } else {
                val page = listOf(block).toReaderPage(
                    pageIndex = firstPageIndex + pages.size,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title
                )
                logEpubPagination {
                    "emit_page reason=unsplittable_oversized page=${page.pageIndex + 1} chapter=$chapterIndex " +
                        "block=${block.kindName()} blockPx=$blockHeight pageHeightPx=${geometry.pageHeightPx} " +
                        "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length}"
                }
                pages += page
            }
        }
        emitPage("chapter_end")
        val chapterPages = pages.ifEmpty {
            listOf(
                ReaderPage(
                    pageIndex = firstPageIndex,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title,
                    text = chapter.plainText.trim(),
                    startOffset = 0,
                    endOffset = chapter.plainText.length
                )
            )
        }
        logEpubPagination {
            "chapter_complete chapter=$chapterIndex pages=${chapterPages.size} title=\"${chapter.title.logPreview()}\""
        }
        return chapterPages
    }

    private suspend fun measureBlock(
        block: SemanticBlock,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Int {
        currentCoroutineContext().ensureActive()
        val padding = block.style.blockStyle.padding.verticalPx()
        val borders = block.style.blockStyle.verticalBorderPx()
        val contentWidth = (geometry.pageWidthPx - block.style.blockStyle.horizontalOuterPx()).coerceAtLeast(64)
        val contentHeight = when (block) {
            is SemanticTextBlock -> measureTextBlock(block, contentWidth, baseStyle, settings)
            is SemanticList -> measureBlockStack(
                blocks = block.items,
                geometry = geometry,
                baseStyle = baseStyle,
                settings = settings,
                includeTrailingBottomMargin = true
            )
            is SemanticTable -> measureTable(block, geometry, baseStyle, settings)
            is SemanticFlexContainer -> measureBlockStack(
                blocks = block.children,
                geometry = geometry,
                baseStyle = baseStyle,
                settings = settings,
                includeTrailingBottomMargin = true
            )
            is SemanticWrappingBlock -> measureBlockStack(
                blocks = listOf(block.floatedImage) + block.paragraphsToWrap,
                geometry = geometry,
                baseStyle = baseStyle,
                settings = settings,
                includeTrailingBottomMargin = true
            )
            is SemanticImage -> measureImage(block, geometry, settings)
            is SemanticMath -> measureMath(block, geometry, baseStyle, settings)
            is SemanticSpacer -> if (block.isExplicitLineBreak) 8 else 16
        }
        return (contentHeight + padding + borders).coerceAtLeast(1)
    }

    private suspend fun measureBlockStack(
        blocks: List<SemanticBlock>,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings,
        includeTrailingBottomMargin: Boolean
    ): Int {
        if (blocks.isEmpty()) return 0
        val items = mutableListOf<PaginationStackItem>()
        for (block in blocks) {
            items += PaginationStackItem(
                contentHeightPx = measureBlock(block, geometry, baseStyle, settings),
                marginTopPx = block.effectiveTopMarginPx(),
                marginBottomPx = block.effectiveBottomMarginPx(settings)
            )
        }
        return collapsedPaginationStackHeight(
            items = items,
            includeTrailingBottomMargin = includeTrailingBottomMargin
        )
    }

    private suspend fun measureTextBlock(
        block: SemanticTextBlock,
        widthPx: Int,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Int {
        currentCoroutineContext().ensureActive()
        val style = block.textStyle(baseStyle, settings)
        val annotated = block.toAnnotatedString(style.fontSize.value)
        val minimumLineHeight = style.lineHeight.takeIfSpecified()
            ?.let { lineHeight -> with(density) { lineHeight.toPx().roundToInt() } }
            ?: with(density) { (settings.fontSize * settings.lineSpacing).sp.toPx().roundToInt() }
        if (annotated.text.isBlank()) return minimumLineHeight.coerceAtLeast(1)
        return measureTextLayout(annotated, style, widthPx)
            .size
            .height
            .coerceAtLeast(minimumLineHeight.coerceAtLeast(1))
    }

    private suspend fun measureTextLayout(
        text: AnnotatedString,
        style: TextStyle,
        widthPx: Int
    ): TextLayoutResult {
        return withContext(Dispatchers.Main) {
            textMeasurer.measure(
                text = text,
                style = style,
                constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1))
            )
        }
    }

    private suspend fun measureTable(
        block: SemanticTable,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Int {
        if (block.rows.isEmpty()) return 1
        return block.rows.sumOf { row ->
            row.maxOfOrNull { cell ->
                measureBlockStack(
                    blocks = cell.content,
                    geometry = geometry,
                    baseStyle = baseStyle,
                    settings = settings,
                    includeTrailingBottomMargin = true
                )
            } ?: 1
        }
    }

    private suspend fun measureMath(
        block: SemanticMath,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Int {
        val explicit = block.svgHeight?.toCssPxOrNull(geometry.pageHeightPx)
        if (explicit != null) return explicit.coerceIn(16, geometry.pageHeightPx)
        return measureTextBlock(
            SemanticParagraph(
                text = block.altText ?: "Equation",
                spans = emptyList(),
                style = block.style,
                elementId = block.elementId,
                cfi = block.cfi,
                blockIndex = block.blockIndex
            ),
            geometry.pageWidthPx,
            baseStyle,
            settings
        )
    }

    private fun measureImage(block: SemanticImage, geometry: MeasuredPageGeometry, settings: ReaderSettings): Int {
        val width = block.intrinsicWidth?.takeIf { it > 0f }
        val height = block.intrinsicHeight?.takeIf { it > 0f }
        val maxWidth = geometry.pageWidthPx * settings.imageScale.coerceIn(0.5f, 2.0f)
        val measured = when {
            width != null && height != null -> {
                val scale = (maxWidth / width).coerceAtMost(1.0f)
                (height * scale).roundToInt()
            }
            block.style.blockStyle.height.isSpecified -> block.style.blockStyle.height.toPxInt()
            else -> with(density) { (settings.fontSize * 8f).sp.toPx().roundToInt() }
        }
        return measured.coerceIn(24, (geometry.pageHeightPx * 0.86f).roundToInt().coerceAtLeast(24))
    }

    private suspend fun splitBlock(
        block: SemanticBlock,
        availableHeight: Int,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Pair<SemanticBlock, SemanticBlock>? {
        currentCoroutineContext().ensureActive()
        val minimumSplitHeight = with(density) { (settings.fontSize * settings.lineSpacing * 2f).sp.toPx().roundToInt() }
        if (availableHeight < minimumSplitHeight) return null
        return when (block) {
            is SemanticTextBlock -> splitTextBlock(block, availableHeight, geometry, baseStyle, settings)
            is SemanticList -> splitList(block, availableHeight, geometry, baseStyle, settings)
            is SemanticFlexContainer -> splitFlex(block, availableHeight, geometry, baseStyle, settings)
            is SemanticWrappingBlock -> splitWrapping(block, availableHeight, geometry, baseStyle, settings)
            else -> null
        }
    }

    private suspend fun splitTextBlock(
        block: SemanticTextBlock,
        availableHeight: Int,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Pair<SemanticBlock, SemanticBlock>? {
        val text = block.text
        if (text.isBlank()) return null
        if (block.style.blockStyle.pageBreakInsideAvoid) return null

        val style = block.textStyle(baseStyle, settings)
        val contentWidth = (geometry.pageWidthPx - block.style.blockStyle.horizontalOuterPx()).coerceAtLeast(64)
        val availableTextHeight = availableHeight - block.splitDecorationPx()
        if (availableTextHeight <= 0) return null

        val layoutResult = measureTextLayout(
            text = block.toAnnotatedString(style.fontSize.value),
            style = style,
            widthPx = contentWidth
        )
        if (layoutResult.size.height <= availableTextHeight) return null
        if (layoutResult.lineCount <= 1 || layoutResult.getLineBottom(0) > availableTextHeight) return null

        var lastVisibleLine = layoutResult
            .getLineForVerticalPosition(availableTextHeight.toFloat())
            .coerceIn(0, layoutResult.lineCount - 1)
        while (lastVisibleLine >= 0 && layoutResult.getLineBottom(lastVisibleLine) > availableTextHeight) {
            lastVisibleLine--
        }
        if (lastVisibleLine <= 0) return null

        var splitOffset = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)
        val remaining = splitSemanticTextBlockAtOffsetForPagination(block, splitOffset)?.second
        if (remaining != null && remaining.text.isNotBlank()) {
            val remainingLayout = measureTextLayout(
                text = remaining.toAnnotatedString(style.fontSize.value),
                style = style,
                widthPx = contentWidth
            )
            if (remainingLayout.lineCount == 1) {
                lastVisibleLine--
                if (lastVisibleLine <= 0) return null
                splitOffset = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)
            }
        }

        return splitSemanticTextBlockAtOffsetForPagination(block, splitOffset)
    }

    private suspend fun splitList(
        block: SemanticList,
        availableHeight: Int,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Pair<SemanticBlock, SemanticBlock>? {
        val firstItems = mutableListOf<SemanticListItem>()
        var used = 0
        var previous: SemanticBlock? = null
        for (item in block.items) {
            val itemHeight = measureBlock(item, geometry, baseStyle, settings)
            val itemRequired = itemHeight + item.collapsedMarginBefore(previous, settings)
            if (firstItems.isNotEmpty() && used + itemRequired > availableHeight) break
            if (firstItems.isEmpty() && itemRequired > availableHeight) return null
            firstItems += item
            used += itemRequired
            previous = item
        }
        if (firstItems.isEmpty() || firstItems.size >= block.items.size) return null
        return block.copy(items = firstItems) to block.copy(items = block.items.drop(firstItems.size))
    }

    private suspend fun splitFlex(
        block: SemanticFlexContainer,
        availableHeight: Int,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Pair<SemanticBlock, SemanticBlock>? {
        val firstChildren = mutableListOf<SemanticBlock>()
        var used = 0
        var previous: SemanticBlock? = null
        for (child in block.children) {
            val childHeight = measureBlock(child, geometry, baseStyle, settings)
            val childRequired = childHeight + child.collapsedMarginBefore(previous, settings)
            if (firstChildren.isNotEmpty() && used + childRequired > availableHeight) break
            if (firstChildren.isEmpty() && childRequired > availableHeight) return null
            firstChildren += child
            used += childRequired
            previous = child
        }
        if (firstChildren.isEmpty() || firstChildren.size >= block.children.size) return null
        return block.copy(children = firstChildren) to block.copy(children = block.children.drop(firstChildren.size))
    }

    private suspend fun splitWrapping(
        block: SemanticWrappingBlock,
        availableHeight: Int,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Pair<SemanticBlock, SemanticBlock>? {
        val imageHeight = measureBlock(block.floatedImage, geometry, baseStyle, settings)
        val imageRequired = imageHeight + block.floatedImage.collapsedMarginBefore(previous = null, settings)
        if (imageRequired >= availableHeight) return null
        val firstParagraphs = mutableListOf<SemanticParagraph>()
        var used = imageRequired
        var previous: SemanticBlock? = block.floatedImage
        for (paragraph in block.paragraphsToWrap) {
            val height = measureBlock(paragraph, geometry, baseStyle, settings)
            val required = height + paragraph.collapsedMarginBefore(previous, settings)
            if (firstParagraphs.isNotEmpty() && used + required > availableHeight) break
            if (firstParagraphs.isEmpty() && used + required > availableHeight) return null
            firstParagraphs += paragraph
            used += required
            previous = paragraph
        }
        if (firstParagraphs.isEmpty() || firstParagraphs.size >= block.paragraphsToWrap.size) return null
        return block.copy(paragraphsToWrap = firstParagraphs) to
            block.copy(paragraphsToWrap = block.paragraphsToWrap.drop(firstParagraphs.size))
    }

    private fun Dp.toPxInt(): Int = with(density) { toPx().roundToInt() }

    private fun com.aryan.reader.paginatedreader.BoxBorders.verticalPx(): Int {
        return top.toPxIfSpecified() + bottom.toPxIfSpecified()
    }

    private fun com.aryan.reader.paginatedreader.BoxBorders.horizontalPx(): Int {
        return left.toPxIfSpecified() + right.toPxIfSpecified()
    }

    private fun com.aryan.reader.paginatedreader.BlockStyle.verticalBorderPx(): Int {
        return (borderTop?.width?.toPxIfSpecified() ?: 0) + (borderBottom?.width?.toPxIfSpecified() ?: 0)
    }

    private fun com.aryan.reader.paginatedreader.BlockStyle.horizontalBorderPx(): Int {
        return (borderLeft?.width?.toPxIfSpecified() ?: 0) + (borderRight?.width?.toPxIfSpecified() ?: 0)
    }

    private fun com.aryan.reader.paginatedreader.BlockStyle.horizontalOuterPx(): Int {
        return margin.horizontalPx() + padding.horizontalPx() + horizontalBorderPx()
    }

    private fun SemanticTextBlock.splitDecorationPx(): Int {
        val blockStyle = style.blockStyle
        return blockStyle.padding.verticalPx() + blockStyle.verticalBorderPx()
    }

    private fun Dp.toPxIfSpecified(): Int = if (isSpecified) toPxInt() else 0

    private fun SemanticBlock.effectiveTopMarginPx(): Int {
        return style.blockStyle.margin.top.toPxIfSpecified()
    }

    private fun SemanticBlock.effectiveBottomMarginPx(settings: ReaderSettings): Int {
        val explicitBottom = style.blockStyle.margin.bottom.toPxIfSpecified()
        return explicitBottom.takeIf { it != 0 } ?: renderedDefaultBottomSpacingPx(settings)
    }

    private fun SemanticBlock.collapsedMarginBefore(
        previous: SemanticBlock?,
        settings: ReaderSettings
    ): Int {
        val top = effectiveTopMarginPx()
        return previous?.let { maxOf(it.effectiveBottomMarginPx(settings), top) } ?: top
    }

    private fun SemanticBlock.renderedDefaultBottomSpacingPx(settings: ReaderSettings): Int {
        if (style.blockStyle.margin.bottom.toPxIfSpecified() != 0) return 0
        return when (this) {
            is SemanticParagraph,
            is SemanticHeader,
            is SemanticList,
            is SemanticTable,
            is SemanticImage -> settings.renderedDefaultBlockSpacingPx()
            is SemanticMath -> if (svgContent == null) settings.renderedDefaultBlockSpacingPx() else 0
            else -> 0
        }
    }

    private fun ReaderSettings.renderedDefaultBlockSpacingPx(): Int {
        return with(density) { (fontSize * paragraphSpacing).sp.toPx().roundToInt() }.coerceAtLeast(0)
    }

    private fun SharedReaderTextAlign.toComposeTextAlign(): TextAlign {
        return when (this) {
            SharedReaderTextAlign.START -> TextAlign.Start
            SharedReaderTextAlign.JUSTIFY -> TextAlign.Justify
            SharedReaderTextAlign.CENTER -> TextAlign.Center
        }
    }
}

internal data class MeasuredPageGeometry(
    val pageWidthPx: Int,
    val pageHeightPx: Int
) {
    companion object {
        fun from(
            settings: ReaderSettings,
            viewport: ReaderViewportSpec,
            densityScale: Float = 1f
        ): MeasuredPageGeometry {
            val safeWidth = viewport.widthPx.takeIf { it > 0 } ?: 980
            val safeHeight = viewport.heightPx.takeIf { it > 0 } ?: 720
            val scale = densityScale.takeIf { it.isFinite() && it > 0f } ?: 1f
            val gutter = if (settings.isTwoPageSpreadEnabled()) MeasuredSpreadGutterPx.scaleCssPx(scale) else 0
            val horizontalMargin = settings.resolvedHorizontalMargin.scaleCssPx(scale) * 2
            val verticalMargin = settings.resolvedVerticalMargin.scaleCssPx(scale) * 2
            val contentWidth = (safeWidth - horizontalMargin).coerceAtLeast(1)
            val configuredPageWidth = settings.pageWidth.scaleCssPx(scale).coerceAtLeast(1)
            val pageWidth = if (settings.isTwoPageSpreadEnabled()) {
                val spreadWidth = contentWidth.coerceAtMost((configuredPageWidth * 2) + gutter)
                ((spreadWidth - gutter).coerceAtLeast(1) / 2).coerceAtLeast(1)
            } else {
                contentWidth.coerceAtMost(configuredPageWidth).coerceAtLeast(1)
            }
            val pageHeight = (safeHeight - verticalMargin).coerceAtLeast(1)
            return MeasuredPageGeometry(pageWidthPx = pageWidth, pageHeightPx = pageHeight)
        }
    }
}

internal fun measuredPageGeometryFor(
    settings: ReaderSettings,
    viewport: ReaderViewportSpec,
    densityScale: Float = 1f
): MeasuredPageGeometry {
    return MeasuredPageGeometry.from(settings, viewport, densityScale)
}

private const val MeasuredSpreadGutterPx = 28

private fun Int.scaleCssPx(scale: Float): Int {
    return (this * scale).roundToInt()
}

internal data class PaginationStackItem(
    val contentHeightPx: Int,
    val marginTopPx: Int,
    val marginBottomPx: Int
)

internal fun collapsedPaginationStackHeight(
    items: List<PaginationStackItem>,
    includeTrailingBottomMargin: Boolean
): Int {
    if (items.isEmpty()) return 0
    var total = 0
    var previousBottom: Int? = null
    for (item in items) {
        total += item.contentHeightPx.coerceAtLeast(0)
        total += previousBottom?.let { maxOf(it, item.marginTopPx.coerceAtLeast(0)) }
            ?: item.marginTopPx.coerceAtLeast(0)
        previousBottom = item.marginBottomPx.coerceAtLeast(0)
    }
    if (includeTrailingBottomMargin) {
        total += previousBottom ?: 0
    }
    return total
}

private fun List<SemanticBlock>.toReaderPage(
    pageIndex: Int,
    chapterIndex: Int,
    chapterTitle: String
): ReaderPage {
    val textBlocks = flatMap { it.textBlocks() }.sortedBy { it.startCharOffsetInSource }
    val text = textBlocks.joinToString("\n\n") { it.text }.trim()
    val startOffset = textBlocks.minOfOrNull { it.startCharOffsetInSource } ?: 0
    val endOffset = textBlocks.maxOfOrNull { it.startCharOffsetInSource + it.text.length } ?: startOffset
    return ReaderPage(
        pageIndex = pageIndex,
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        text = text,
        startOffset = startOffset,
        endOffset = endOffset,
        semanticBlocks = this
    )
}

private fun SemanticBlock.hasReadableContent(): Boolean {
    return when (this) {
        is SemanticTextBlock -> text.isNotBlank()
        is SemanticList -> items.any { it.hasReadableContent() }
        is SemanticTable -> rows.any { row -> row.any { cell -> cell.content.any { it.hasReadableContent() } } }
        is SemanticFlexContainer -> children.any { it.hasReadableContent() }
        is SemanticWrappingBlock -> floatedImage.hasReadableContent() || paragraphsToWrap.any { it.hasReadableContent() }
        is SemanticImage -> true
        is SemanticMath -> true
        is SemanticSpacer -> true
    }
}

private fun SemanticBlock.textBlocks(): List<SemanticTextBlock> {
    return when (this) {
        is SemanticTextBlock -> listOf(this)
        is SemanticList -> items
        is SemanticTable -> rows.flatMap { row -> row.flatMap { cell -> cell.content.flatMap { it.textBlocks() } } }
        is SemanticFlexContainer -> children.flatMap { it.textBlocks() }
        is SemanticWrappingBlock -> paragraphsToWrap
        else -> emptyList()
    }
}

private fun SemanticTextBlock.toAnnotatedString(blockFontSizeSp: Float): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start < end) {
                addStyle(span.style.toMeasurementSpanStyle(blockFontSizeSp), start, end)
            }
        }
    }
}

private fun SemanticTextBlock.textStyle(baseStyle: TextStyle, settings: ReaderSettings): TextStyle {
    val fontSize = (style.fontSize.takeIfSpecified()
        ?: style.spanStyle.fontSize.takeIfSpecified())
        ?.resolveFontSizeSp(settings.fontSize.toFloat())
        ?: when (this) {
            is SemanticHeader -> (settings.fontSize * headerScale(level)).sp
            else -> baseStyle.fontSize
        }
    val lineHeight = style.paragraphStyle.lineHeight.takeIfSpecified()
        ?.resolveLineHeightSp(fontSize.value)
        ?: if (fontSize.isSpecified) {
            (fontSize.value * settings.lineSpacing).sp
        } else {
            baseStyle.lineHeight
        }
    return baseStyle.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = if (this is SemanticHeader) FontWeight.Bold else baseStyle.fontWeight,
        textAlign = style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: baseStyle.textAlign
    )
}

private fun TextUnit.takeIfSpecified(): TextUnit? = if (isSpecified) this else null

private fun TextUnit.resolveFontSizeSp(baseFontSizeSp: Float): TextUnit {
    return when {
        isEm -> (baseFontSizeSp * value).sp
        else -> value.sp
    }
}

private fun TextUnit.resolveLineHeightSp(fontSizeSp: Float): TextUnit {
    return when {
        isEm -> (fontSizeSp * value).sp
        else -> value.sp
    }
}

private fun CssStyle.toMeasurementSpanStyle(parentFontSizeSp: Float): SpanStyle {
    val resolvedFontSize = (spanStyle.fontSize.takeIfSpecified() ?: fontSize.takeIfSpecified())
        ?.resolveFontSizeSp(parentFontSizeSp)
    return if (resolvedFontSize == null) {
        spanStyle
    } else {
        spanStyle.copy(fontSize = resolvedFontSize)
    }
}

private fun headerScale(level: Int): Float {
    return when (level) {
        1 -> 1.5f
        2 -> 1.35f
        3 -> 1.2f
        4 -> 1.1f
        else -> 1f
    }
}

private fun SemanticTextBlock.sliceText(start: Int, end: Int): SemanticTextBlock {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    val slicedText = text.substring(safeStart, safeEnd)
    val slicedSpans = spans.mapNotNull { span ->
        val spanStart = span.start.coerceAtLeast(safeStart)
        val spanEnd = span.end.coerceAtMost(safeEnd)
        if (spanEnd <= spanStart) {
            null
        } else {
            span.copy(start = spanStart - safeStart, end = spanEnd - safeStart)
        }
    }
    val nextOffset = startCharOffsetInSource + safeStart
    return when (this) {
        is SemanticParagraph -> copy(
            text = slicedText,
            spans = slicedSpans,
            startCharOffsetInSource = nextOffset
        )
        is SemanticHeader -> copy(
            text = slicedText,
            spans = slicedSpans,
            startCharOffsetInSource = nextOffset
        )
        is SemanticListItem -> copy(
            text = slicedText,
            spans = slicedSpans,
            startCharOffsetInSource = nextOffset
        )
        else -> SemanticParagraph(
            text = slicedText,
            spans = slicedSpans,
            style = style,
            elementId = elementId,
            cfi = cfi,
            startCharOffsetInSource = nextOffset,
            blockIndex = blockIndex
        )
    }
}

internal fun splitSemanticTextBlockAtOffsetForPagination(
    block: SemanticTextBlock,
    splitOffset: Int
): Pair<SemanticTextBlock, SemanticTextBlock>? {
    val safeSplit = splitOffset.coerceIn(0, block.text.length)
    var firstEnd = safeSplit
    while (firstEnd > 0 && block.text[firstEnd - 1].isWhitespace()) {
        firstEnd--
    }
    var secondStart = safeSplit
    while (secondStart < block.text.length && block.text[secondStart].isWhitespace()) {
        secondStart++
    }
    if (firstEnd <= 0 || secondStart >= block.text.length) return null

    val first = block.sliceText(0, firstEnd)
    val second = block
        .sliceText(secondStart, block.text.length)
        .asPaginationContinuation()
    return first to second
}

private fun SemanticTextBlock.asPaginationContinuation(): SemanticTextBlock {
    val paragraphStyle = style.paragraphStyle
    val textIndent = paragraphStyle.textIndent
    val continuationParagraphStyle = if (textIndent != null) {
        paragraphStyle.copy(
            textIndent = TextIndent(
                firstLine = 0.sp,
                restLine = textIndent.restLine
            )
        )
    } else {
        paragraphStyle
    }
    val continuationStyle = style.copy(
        paragraphStyle = continuationParagraphStyle,
        blockStyle = style.blockStyle.copy(
            margin = style.blockStyle.margin.copy(top = 0.dp)
        )
    )
    return copyWithStyle(continuationStyle)
}

private fun SemanticTextBlock.copyWithStyle(style: CssStyle): SemanticTextBlock {
    return when (this) {
        is SemanticParagraph -> copy(style = style)
        is SemanticHeader -> copy(style = style)
        is SemanticListItem -> copy(style = style)
        else -> SemanticParagraph(
            text = text,
            spans = spans,
            style = style,
            elementId = elementId,
            cfi = cfi,
            startCharOffsetInSource = startCharOffsetInSource,
            blockIndex = blockIndex
        )
    }
}

private fun SemanticBlock.kindName(): String {
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

private fun String.toCssPxOrNull(containerPx: Int): Int? {
    val trimmed = trim().lowercase()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.endsWith("px") -> trimmed.removeSuffix("px").toFloatOrNull()?.roundToInt()
        trimmed.endsWith("%") -> trimmed.removeSuffix("%").toFloatOrNull()?.let { (containerPx * it / 100f).roundToInt() }
        else -> trimmed.toFloatOrNull()?.roundToInt()
    }?.takeIf { it > 0 }
}

private fun String.toPlainSemanticBlocks(): List<SemanticBlock> {
    val normalized = replace("\r\n", "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    if (normalized.isBlank()) return emptyList()
    val blocks = mutableListOf<SemanticBlock>()
    var cursor = 0
    normalized.split(Regex("\\n\\s*\\n")).forEachIndexed { index, paragraph ->
        val clean = paragraph.trim()
        if (clean.isBlank()) return@forEachIndexed
        val start = normalized.indexOf(clean, cursor).takeIf { it >= 0 } ?: cursor
        blocks += SemanticParagraph(
            text = clean,
            spans = emptyList(),
            style = CssStyle(),
            elementId = null,
            cfi = null,
            startCharOffsetInSource = start,
            blockIndex = index
        )
        cursor = start + clean.length
    }
    return blocks
}

private inline fun logEpubPagination(message: () -> String) {
    logSharedReaderDiagnostic("EpistemeEpubPagination", message)
}

private inline fun logReaderGapPagination(message: () -> String) {
    logSharedReaderDiagnostic("EpistemeReaderGap", message)
}

private fun String.logPreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}
