package com.aryan.reader.shared.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
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
import kotlinx.coroutines.yield
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
        viewport: ReaderViewportSpec,
        readCache: Boolean = true
    ): List<ReaderPage> {
        currentCoroutineContext().ensureActive()
        if (readCache) {
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
                logEpubPageFit {
                    "page_fit layer=cache_hit book=\"${book.title.logPreview()}\" pages=${cached.size} " +
                        "note=clear_book_cache_to_capture_layer_measured"
                }
                return cached
            }
        }

        currentCoroutineContext().ensureActive()
        val geometryTerms = measuredPageGeometryTerms(settings, viewport, density.density)
        val geometry = geometryTerms.geometry
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
        ).withAndroidPaginationTextMetrics()
        val pages = mutableListOf<ReaderPage>()
        book.chapters.forEachIndexed { chapterIndex, chapter ->
            currentCoroutineContext().ensureActive()
            yield()
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

    suspend fun paginateChapterWindow(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        chapterIndex: Int,
        firstPageIndex: Int
    ): List<ReaderPage> {
        currentCoroutineContext().ensureActive()
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
        val geometryTerms = measuredPageGeometryTerms(settings, viewport, density.density)
        val geometry = geometryTerms.geometry
        val baseStyle = TextStyle(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineSpacing).sp,
            fontFamily = fontFamily,
            textAlign = settings.textAlign.toComposeTextAlign()
        ).withAndroidPaginationTextMetrics()
        logEpubPagination {
            "chapter_window_start book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                "firstPage=${firstPageIndex + 1} viewport=${viewport.widthPx}x${viewport.heightPx} " +
                "page=${geometry.pageWidthPx}x${geometry.pageHeightPx}"
        }
        val pages = paginateChapter(
            chapter = chapter,
            chapterIndex = chapterIndex,
            firstPageIndex = firstPageIndex,
            settings = settings,
            geometry = geometry,
            baseStyle = baseStyle
        ).mapIndexed { index, page -> page.copy(pageIndex = firstPageIndex + index) }
        logEpubPagination {
            "chapter_window_complete book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                "pages=${pages.size} firstPage=${firstPageIndex + 1}"
        }
        return pages
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
        var pageBlockFits = mutableListOf<MeasuredPageBlockFit>()
        var usedHeight = 0

        logEpubPagination {
            "chapter_start chapter=$chapterIndex title=\"${chapter.title.logPreview()}\" " +
                "sourceBlocks=${sourceBlocks.size} plainChars=${chapter.plainText.length} pageHeightPx=${geometry.pageHeightPx}"
        }

        fun addPageBlock(block: SemanticBlock, blockHeight: Int, spaceBefore: Int) {
            pageBlocks += block
            pageBlockFits += block.toMeasuredPageBlockFit(
                order = pageBlockFits.size,
                blockHeightPx = blockHeight,
                spaceBeforePx = spaceBefore,
                topMarginPx = block.effectiveTopMarginPx(),
                bottomMarginPx = block.effectiveBottomMarginPx(settings)
            )
            usedHeight += blockHeight + spaceBefore
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
            val remainingPx = geometry.pageHeightPx - usedHeight
            if (remainingPx < 0) {
                logEpubPageFit {
                    "page_fit layer=measured_overflow reason=$reason page=${page.pageIndex + 1} chapter=$chapterIndex " +
                        "usedPx=$usedHeight pageHeightPx=${geometry.pageHeightPx} remainingPx=$remainingPx " +
                        "blocks=${pageBlocks.size} range=${page.startOffset}..${page.endOffset} " +
                        "textChars=${page.text.length} tail=\"${pageBlockFits.measuredPageFitTail()}\""
                }
                logEpubCutoff {
                    "cutoff_probe layer=measured_overflow reason=$reason page=${page.pageIndex + 1} chapter=$chapterIndex " +
                        "usedPx=$usedHeight pageHeightPx=${geometry.pageHeightPx} remainingPx=$remainingPx " +
                        "overflowPx=${(-remainingPx).coerceAtLeast(0)} blocks=${pageBlocks.size} " +
                        "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length} " +
                        "tail=\"${pageBlockFits.measuredPageFitTail()}\""
                }
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
            pageBlockFits = mutableListOf()
            usedHeight = 0
        }

        var processedBlocks = 0
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            processedBlocks += 1
            if (processedBlocks % 8 == 0) yield()
            val block = queue.removeFirst()
            val blockHeight = measureBlock(block, geometry, baseStyle, settings)
            val spaceBeforeBlock = block.collapsedMarginBefore(pageBlocks.lastOrNull(), settings)
            val requiredHeight = blockHeight + spaceBeforeBlock
            val fitsCurrent = requiredHeight <= geometry.pageHeightPx - usedHeight
            if (fitsCurrent) {
                addPageBlock(block, blockHeight, spaceBeforeBlock)
                continue
            }

            val remainingHeight = (geometry.pageHeightPx - usedHeight).coerceAtLeast(0)
            val splitAvailableHeight = (remainingHeight - spaceBeforeBlock).coerceAtLeast(0)
            val split = splitBlock(block, splitAvailableHeight, geometry, baseStyle, settings)
            if (split != null && split.first.hasReadableContent()) {
                val splitHeight = measureBlock(split.first, geometry, baseStyle, settings)
                if (spaceBeforeBlock + splitHeight <= remainingHeight) {
                    logEpubPagination {
                        "split_current block=${block.kindName()} blockPx=$blockHeight splitPx=$splitHeight " +
                            "spaceBeforePx=$spaceBeforeBlock remainingPx=$remainingHeight " +
                            "splitAvailablePx=$splitAvailableHeight usedPx=$usedHeight " +
                            "pageHeightPx=${geometry.pageHeightPx} chapter=$chapterIndex"
                    }
                    addPageBlock(split.first, splitHeight, spaceBeforeBlock)
                    if (split.second.hasReadableContent()) queue.addFirst(split.second)
                    emitPage("split_current")
                    continue
                }
                logEpubPageFit {
                    "page_fit layer=split_rejected block=${block.kindName()} chapter=$chapterIndex " +
                        "spaceBeforePx=$spaceBeforeBlock splitPx=$splitHeight remainingPx=$remainingHeight " +
                        "splitAvailablePx=$splitAvailableHeight pageHeightPx=${geometry.pageHeightPx}"
                }
            }

            emitPage("before_block")
            val newPageSpaceBefore = block.collapsedMarginBefore(previous = null, settings)
            if (blockHeight + newPageSpaceBefore <= geometry.pageHeightPx) {
                addPageBlock(block, blockHeight, newPageSpaceBefore)
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
                logOversizedMeasuredPageFit(
                    reason = "split_oversized",
                    page = page,
                    chapterIndex = chapterIndex,
                    block = oversizedSplit.first,
                    blockHeightPx = splitHeight,
                    spaceBeforePx = newPageSpaceBefore,
                    pageHeightPx = geometry.pageHeightPx,
                    topMarginPx = oversizedSplit.first.effectiveTopMarginPx(),
                    bottomMarginPx = oversizedSplit.first.effectiveBottomMarginPx(settings)
                )
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
                logOversizedMeasuredPageFit(
                    reason = "unsplittable_oversized",
                    page = page,
                    chapterIndex = chapterIndex,
                    block = block,
                    blockHeightPx = blockHeight,
                    spaceBeforePx = newPageSpaceBefore,
                    pageHeightPx = geometry.pageHeightPx,
                    topMarginPx = block.effectiveTopMarginPx(),
                    bottomMarginPx = block.effectiveBottomMarginPx(settings)
                )
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
        val contentWidth = block.measuredTextContentWidthPx(geometry)
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
            .let { height ->
                height + centeredTextSafetyPaddingPx(style)
            }
            .coerceAtLeast(minimumLineHeight.coerceAtLeast(1))
    }

    private suspend fun measureTextLayout(
        text: AnnotatedString,
        style: TextStyle,
        widthPx: Int
    ): TextLayoutResult {
        currentCoroutineContext().ensureActive()
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
        val imageScale = settings.imageScale.coerceIn(0.5f, 2.0f)
        val measured = when {
            width != null && height != null -> {
                val style = block.style.blockStyle
                val contentMaxWidth = geometry.pageWidthPx.toFloat()
                val baseWidth = if (style.width.isSpecified && style.width > 0.dp) {
                    style.width.toPxInt().toFloat()
                } else {
                    contentMaxWidth
                }
                var scaledWidth = baseWidth * imageScale
                if (style.maxWidth.isSpecified && style.maxWidth > 0.dp) {
                    scaledWidth = scaledWidth.coerceAtMost(style.maxWidth.toPxInt() * imageScale)
                }
                scaledWidth = scaledWidth.coerceAtMost(contentMaxWidth)
                (scaledWidth * (height / width)).roundToInt()
            }
            block.style.blockStyle.height.isSpecified && block.style.blockStyle.height > 0.dp -> block.style.blockStyle.height.toPxInt()
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
        val contentWidth = block.measuredTextContentWidthPx(geometry)
        val availableTextHeight = availableHeight - block.splitDecorationPx() - centeredTextSafetyPaddingPx(style)
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
        val availableContentHeight = availableHeight - block.splitDecorationPx()
        if (availableContentHeight <= 0) return null
        val items = block.items.toPaginationStackItems(geometry, baseStyle, settings)
        val prefixCount = paginationStackPrefixCountThatFits(
            items = items,
            availableHeightPx = availableContentHeight,
            includeTrailingBottomMargin = true
        )
        if (prefixCount <= 0 || prefixCount >= block.items.size) return null
        return block.copy(items = block.items.take(prefixCount)) to block.copy(items = block.items.drop(prefixCount))
    }

    private suspend fun splitFlex(
        block: SemanticFlexContainer,
        availableHeight: Int,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Pair<SemanticBlock, SemanticBlock>? {
        val availableContentHeight = availableHeight - block.splitDecorationPx()
        if (availableContentHeight <= 0) return null
        val items = block.children.toPaginationStackItems(geometry, baseStyle, settings)
        val prefixCount = paginationStackPrefixCountThatFits(
            items = items,
            availableHeightPx = availableContentHeight,
            includeTrailingBottomMargin = true
        )
        if (prefixCount <= 0 || prefixCount >= block.children.size) return null
        return block.copy(children = block.children.take(prefixCount)) to block.copy(children = block.children.drop(prefixCount))
    }

    private suspend fun splitWrapping(
        block: SemanticWrappingBlock,
        availableHeight: Int,
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): Pair<SemanticBlock, SemanticBlock>? {
        val availableContentHeight = availableHeight - block.splitDecorationPx()
        if (availableContentHeight <= 0) return null
        val children = listOf<SemanticBlock>(block.floatedImage) + block.paragraphsToWrap
        val items = children.toPaginationStackItems(geometry, baseStyle, settings)
        val prefixCount = paginationStackPrefixCountThatFits(
            items = items,
            availableHeightPx = availableContentHeight,
            includeTrailingBottomMargin = true
        )
        if (prefixCount <= 1 || prefixCount >= children.size) return null
        val firstParagraphs = block.paragraphsToWrap.take(prefixCount - 1)
        return block.copy(paragraphsToWrap = firstParagraphs) to
            block.copy(paragraphsToWrap = block.paragraphsToWrap.drop(firstParagraphs.size))
    }

    private suspend fun List<SemanticBlock>.toPaginationStackItems(
        geometry: MeasuredPageGeometry,
        baseStyle: TextStyle,
        settings: ReaderSettings
    ): List<PaginationStackItem> {
        return map { block ->
            PaginationStackItem(
                contentHeightPx = measureBlock(block, geometry, baseStyle, settings),
                marginTopPx = block.effectiveTopMarginPx(),
                marginBottomPx = block.effectiveBottomMarginPx(settings)
            )
        }
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

    private fun SemanticBlock.splitDecorationPx(): Int {
        val blockStyle = style.blockStyle
        return blockStyle.padding.verticalPx() + blockStyle.verticalBorderPx()
    }

    private fun SemanticBlock.measuredTextContentWidthPx(geometry: MeasuredPageGeometry): Int {
        val markerWidth = if (this is SemanticListItem) listItemMarkerAreaWidthPx() else 0
        return (geometry.pageWidthPx - style.blockStyle.horizontalOuterPx() - markerWidth)
            .coerceAtLeast(64)
    }

    private fun listItemMarkerAreaWidthPx(): Int {
        return with(density) { MeasuredListItemMarkerAreaWidthDp.dp.toPx().roundToInt() }
    }

    private fun centeredTextSafetyPaddingPx(style: TextStyle): Int {
        if (style.textAlign != TextAlign.Center) return 0
        val fallbackLineHeight = if (style.fontSize.isSpecified) {
            style.fontSize * 1.2f
        } else {
            16.sp * 1.2f
        }
        val effectiveLineHeight = if (style.lineHeight.isSpecified) style.lineHeight else fallbackLineHeight
        return with(density) { effectiveLineHeight.toPx().roundToInt() }
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
            SharedReaderTextAlign.RIGHT -> TextAlign.Right
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
            return measuredPageGeometryTerms(settings, viewport, densityScale).geometry
        }
    }
}

private data class MeasuredPageGeometryTerms(
    val safeWidthPx: Int,
    val safeHeightPx: Int,
    val pageHorizontalMarginPx: Int,
    val pageVerticalMarginPx: Int,
    val configuredPageWidthPx: Int,
    val spreadGutterPx: Int,
    val singlePageContentWidthPx: Int,
    val twoPageAvailableOuterWidthPx: Int,
    val twoPageAvailableContentWidthPx: Int,
    val geometry: MeasuredPageGeometry
)

private fun measuredPageGeometryTerms(
    settings: ReaderSettings,
    viewport: ReaderViewportSpec,
    densityScale: Float = 1f
): MeasuredPageGeometryTerms {
    val safeWidth = viewport.widthPx.takeIf { it > 0 } ?: 980
    val safeHeight = viewport.heightPx.takeIf { it > 0 } ?: 720
    val scale = densityScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val pageHorizontalMargin = settings.resolvedHorizontalMargin.scaleCssPx(scale)
    val pageVerticalMargin = settings.resolvedVerticalMargin.scaleCssPx(scale)
    val configuredPageWidth = settings.pageWidth.scaleCssPx(scale).coerceAtLeast(1)
    val usesSpreadPageSlot = settings.usesMeasuredPaginatedSpreadPageSlot()
    val gutter = if (usesSpreadPageSlot) MeasuredSpreadGutterPx.scaleCssPx(scale) else 0
    val singlePageContentWidth = (safeWidth - (pageHorizontalMargin * 2)).coerceAtLeast(1)
    val twoPageAvailableOuterWidth = ((safeWidth - gutter).coerceAtLeast(1) / 2).coerceAtLeast(1)
    val twoPageAvailableContentWidth = (twoPageAvailableOuterWidth - (pageHorizontalMargin * 2)).coerceAtLeast(1)
    val pageWidth = if (usesSpreadPageSlot) {
        twoPageAvailableContentWidth.coerceAtMost(configuredPageWidth).coerceAtLeast(1)
    } else {
        singlePageContentWidth.coerceAtMost(configuredPageWidth).coerceAtLeast(1)
    }
    val pageHeight = (safeHeight - (pageVerticalMargin * 2)).coerceAtLeast(1)
    return MeasuredPageGeometryTerms(
        safeWidthPx = safeWidth,
        safeHeightPx = safeHeight,
        pageHorizontalMarginPx = pageHorizontalMargin,
        pageVerticalMarginPx = pageVerticalMargin,
        configuredPageWidthPx = configuredPageWidth,
        spreadGutterPx = gutter,
        singlePageContentWidthPx = singlePageContentWidth,
        twoPageAvailableOuterWidthPx = twoPageAvailableOuterWidth,
        twoPageAvailableContentWidthPx = twoPageAvailableContentWidth,
        geometry = MeasuredPageGeometry(pageWidthPx = pageWidth, pageHeightPx = pageHeight)
    )
}

internal fun measuredPageGeometryFor(
    settings: ReaderSettings,
    viewport: ReaderViewportSpec,
    densityScale: Float = 1f
): MeasuredPageGeometry {
    return MeasuredPageGeometry.from(settings, viewport, densityScale)
}

private const val MeasuredSpreadGutterPx = 28

private fun ReaderSettings.usesMeasuredPaginatedSpreadPageSlot(): Boolean {
    return readingMode == ReaderReadingMode.PAGINATED
}

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

internal fun paginationStackPrefixCountThatFits(
    items: List<PaginationStackItem>,
    availableHeightPx: Int,
    includeTrailingBottomMargin: Boolean
): Int {
    if (items.isEmpty() || availableHeightPx < 0) return 0
    var totalWithoutTrailingBottom = 0
    var previousBottom: Int? = null
    var prefixCount = 0
    for (item in items) {
        totalWithoutTrailingBottom += item.contentHeightPx.coerceAtLeast(0)
        totalWithoutTrailingBottom += previousBottom?.let { maxOf(it, item.marginTopPx.coerceAtLeast(0)) }
            ?: item.marginTopPx.coerceAtLeast(0)
        previousBottom = item.marginBottomPx.coerceAtLeast(0)

        val candidateHeight = if (includeTrailingBottomMargin) {
            totalWithoutTrailingBottom + previousBottom
        } else {
            totalWithoutTrailingBottom
        }
        if (candidateHeight > availableHeightPx) break
        prefixCount++
    }
    return prefixCount
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
    ).withAndroidPaginationTextMetrics()
}

private fun TextStyle.withAndroidPaginationTextMetrics(): TextStyle {
    return copy(
        lineBreak = LineBreak.Paragraph,
        letterSpacing = TextUnit.Unspecified,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None
        )
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

private data class MeasuredPageBlockFit(
    val order: Int,
    val kind: String,
    val blockIndex: Int,
    val blockHeightPx: Int,
    val spaceBeforePx: Int,
    val topMarginPx: Int,
    val bottomMarginPx: Int,
    val sourceRange: String
) {
    fun format(): String {
        return "#$order:$kind(block=$blockIndex,space=$spaceBeforePx,height=$blockHeightPx," +
            "top=$topMarginPx,bottom=$bottomMarginPx,range=$sourceRange)"
    }
}

private fun SemanticBlock.toMeasuredPageBlockFit(
    order: Int,
    blockHeightPx: Int,
    spaceBeforePx: Int,
    topMarginPx: Int,
    bottomMarginPx: Int
): MeasuredPageBlockFit {
    return MeasuredPageBlockFit(
        order = order,
        kind = kindName(),
        blockIndex = blockIndex,
        blockHeightPx = blockHeightPx,
        spaceBeforePx = spaceBeforePx,
        topMarginPx = topMarginPx,
        bottomMarginPx = bottomMarginPx,
        sourceRange = sourceRangeLabel()
    )
}

private fun List<MeasuredPageBlockFit>.measuredPageFitTail(): String {
    return takeLast(EpubPageFitTailBlockCount).joinToString("|") { it.format() }
}

private fun SemanticBlock.sourceRangeLabel(): String {
    return when (this) {
        is SemanticTextBlock -> {
            val start = startCharOffsetInSource
            "$start..${start + text.length}"
        }
        else -> cfi?.takeIf { it.isNotBlank() }
            ?: elementId?.takeIf { it.isNotBlank() }
            ?: "-"
    }.logPreview(maxLength = 80)
}

private fun logOversizedMeasuredPageFit(
    reason: String,
    page: ReaderPage,
    chapterIndex: Int,
    block: SemanticBlock,
    blockHeightPx: Int,
    spaceBeforePx: Int,
    pageHeightPx: Int,
    topMarginPx: Int,
    bottomMarginPx: Int
) {
    val usedPx = blockHeightPx + spaceBeforePx
    val remainingPx = pageHeightPx - usedPx
    if (remainingPx >= 0) return
    val fit = block.toMeasuredPageBlockFit(
        order = 0,
        blockHeightPx = blockHeightPx,
        spaceBeforePx = spaceBeforePx,
        topMarginPx = topMarginPx,
        bottomMarginPx = bottomMarginPx
    )
    logEpubPageFit {
        "page_fit layer=measured_overflow reason=$reason page=${page.pageIndex + 1} chapter=$chapterIndex " +
            "usedPx=$usedPx pageHeightPx=$pageHeightPx remainingPx=$remainingPx blocks=1 " +
            "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length} tail=\"${fit.format()}\""
    }
    logEpubCutoff {
        "cutoff_probe layer=measured_overflow reason=$reason page=${page.pageIndex + 1} chapter=$chapterIndex " +
            "usedPx=$usedPx pageHeightPx=$pageHeightPx remainingPx=$remainingPx " +
            "overflowPx=${(-remainingPx).coerceAtLeast(0)} blocks=1 " +
            "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length} tail=\"${fit.format()}\""
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

private inline fun logEpubPageFit(message: () -> String) {
    logSharedReaderDiagnostic("EpistemeEpubPageFit", message)
}

private inline fun logEpubCutoff(message: () -> String) {
    logSharedReaderDiagnostic(SharedEpubCutoffDiagnosticsTag, message)
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

private const val EpubPageFitTailBlockCount = 4
private const val MeasuredListItemMarkerAreaWidthDp = 32
