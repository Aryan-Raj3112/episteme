package com.aryan.reader.paginatedreader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Vertical (`tategaki`) pagination for native content blocks. Android is the
 * benchmark: this mirrors the shared semantic implementation
 * ([com.aryan.reader.shared.reader.SharedMeasuredEpubPaginator] vertical
 * path) block for block, and both share [layoutVerticalParagraph], so
 * measurement agrees across pipelines by construction.
 */
internal data class VerticalContentMeasureContext(
    val textMeasurer: TextMeasurer,
    val density: Density,
    val baseStyle: TextStyle,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val imageSizeMultiplier: Float,
    val hideImages: Boolean,
    val defaultLineSpacing: Float,
    val glyphCaches: MutableMap<String, MutableMap<VerticalGlyphCacheKey, Size>> = mutableMapOf()
)

internal data class VerticalContentTextParams(
    val fontSizePx: Float,
    val lineHeightEm: Float,
    val columnHeightPx: Float,
    val textAlign: TextAlign,
    val tcyRanges: List<IntRange>
)

private fun AnnotatedString.baseFontSizeAtStart(fallback: TextUnit): TextUnit {
    var size = fallback
    spanStyles.filter { it.start == 0 && it.item.fontSize.isSpecified }
        .forEach { size = it.item.fontSize }
    return size
}

internal fun TextContentBlock.verticalContentParams(
    baseStyle: TextStyle,
    pageHeightPx: Int,
    density: Density,
    defaultLineSpacing: Float
): VerticalContentTextParams {
    val fontSize = content.baseFontSizeAtStart(baseStyle.fontSize)
        .takeIf { it.isSpecified } ?: baseStyle.fontSize
    val fontSizePx = with(density) { fontSize.toPx() }.coerceAtLeast(1f)
    val paragraphLineHeight = content.paragraphStyles.firstOrNull()?.item?.lineHeight
    val lineHeightEm = when {
        paragraphLineHeight != null && paragraphLineHeight.isSpecified && paragraphLineHeight.isEm ->
            paragraphLineHeight.value
        paragraphLineHeight != null && paragraphLineHeight.isSpecified && paragraphLineHeight.isSp ->
            paragraphLineHeight.value / fontSize.value.coerceAtLeast(0.01f)
        baseStyle.lineHeight.isSpecified && baseStyle.fontSize.isSpecified && baseStyle.fontSize.value > 0f ->
            baseStyle.lineHeight.value / baseStyle.fontSize.value
        else -> defaultLineSpacing
    }
    val verticalDecorations = with(density) {
        style.padding.top.coerceAtLeast(0.dp).toPx() + style.padding.bottom.coerceAtLeast(0.dp).toPx() +
            (style.borderTop?.width?.toPx() ?: 0f) + (style.borderBottom?.width?.toPx() ?: 0f) +
            style.margin.top.toPx() + style.margin.bottom.toPx()
    }
    val columnHeightPx = (pageHeightPx - verticalDecorations).coerceAtLeast(48f)
    val blockAlign: TextAlign? = when (this) {
        is ParagraphBlock -> textAlign
        is HeaderBlock -> textAlign
        is QuoteBlock -> textAlign
        else -> null
    }
    val tcyRanges = content.getStringAnnotations("TateChuYoko", 0, content.length)
        .mapNotNull { annotation ->
            if (annotation.end > annotation.start) annotation.start..(annotation.end - 1) else null
        }
    return VerticalContentTextParams(
        fontSizePx = fontSizePx,
        lineHeightEm = lineHeightEm,
        columnHeightPx = columnHeightPx,
        textAlign = blockAlign ?: baseStyle.textAlign,
        tcyRanges = tcyRanges
    )
}

private fun VerticalContentMeasureContext.verticalMeasurer(): VerticalGlyphMeasurer =
    object : VerticalGlyphMeasurer {
        private val family = baseStyle.fontFamily
        private val weight = baseStyle.fontWeight
        private val features = listOfNotNull(
            baseStyle.fontFeatureSettings?.takeIf { it.isNotBlank() },
            "\"vert\""
        ).joinToString(", ").takeIf { it.isNotBlank() }

        private fun styleFor(sizePx: Float, upright: Boolean): TextStyle = TextStyle(
            fontSize = with(density) { sizePx.toSp() },
            fontFamily = family,
            fontWeight = weight,
            fontFeatureSettings = if (upright) features else baseStyle.fontFeatureSettings
        )

        // Called inside withContext(Dispatchers.Main) by pagination and
        // directly on Main by rendering; measurement itself is synchronous.
        override fun measureUpright(text: String, fontSizePx: Float): Size =
            textMeasurer.measure(text, styleFor(fontSizePx, upright = true)).size.let { size ->
                Size(size.width.toFloat(), size.height.toFloat())
            }

        override fun measureHorizontal(text: String, fontSizePx: Float): Size =
            textMeasurer.measure(text, styleFor(fontSizePx, upright = false)).size.let { size ->
                Size(size.width.toFloat(), size.height.toFloat())
            }
    }

private fun VerticalContentMeasureContext.cacheFor(block: TextContentBlock): MutableMap<VerticalGlyphCacheKey, Size> =
    glyphCaches.getOrPut(baseStyle.fontFamily.toString()) { verticalGlyphCache() }

private suspend fun measureVerticalContentText(
    block: TextContentBlock,
    ctx: VerticalContentMeasureContext
): Int {
    if (block.content.isEmpty()) return 0
    val params = block.verticalContentParams(ctx.baseStyle, ctx.pageHeightPx, ctx.density, ctx.defaultLineSpacing)
    // TextMeasurer is Main-bound; glyph sizes are cached across the chapter.
    val layout = withContext(Dispatchers.Main) {
        layoutVerticalParagraph(
            text = block.content.text,
            rubies = block.rubies,
            tcyRanges = params.tcyRanges,
            fontSizePx = params.fontSizePx,
            lineHeightEm = params.lineHeightEm,
            columnHeightPx = params.columnHeightPx,
            textAlign = params.textAlign,
            measurer = ctx.verticalMeasurer(),
            cache = ctx.cacheFor(block)
        )
    }
    val horizontalDecorations = with(ctx.density) {
        block.style.padding.left.coerceAtLeast(0.dp).toPx() +
            block.style.padding.right.coerceAtLeast(0.dp).toPx() +
            (block.style.borderLeft?.width?.toPx() ?: 0f) +
            (block.style.borderRight?.width?.toPx() ?: 0f)
    }
    return (layout.widthPx + horizontalDecorations).roundToInt().coerceAtLeast(0)
}

private fun AnnotatedString.withoutVerticalFirstLineIndent(): AnnotatedString {
    var result: AnnotatedString = this
    paragraphStyles.firstOrNull { it.start == 0 && it.item.textIndent != null }?.let { styleRange ->
        val originalIndent = styleRange.item.textIndent ?: return@let
        result = androidx.compose.ui.text.buildAnnotatedString {
            append(this@withoutVerticalFirstLineIndent)
            addStyle(
                style = styleRange.item.copy(
                    textIndent = androidx.compose.ui.text.style.TextIndent(
                        firstLine = 0.sp,
                        restLine = originalIndent.restLine
                    )
                ),
                start = 0,
                end = styleRange.end.coerceAtMost(this.length)
            )
        }
    }
    return result
}

private suspend fun splitVerticalContentText(
    block: TextContentBlock,
    availableWidthPx: Int,
    ctx: VerticalContentMeasureContext
): Pair<TextContentBlock, TextContentBlock>? {
    if (block.content.isBlank() || availableWidthPx <= 0) return null
    val params = block.verticalContentParams(ctx.baseStyle, ctx.pageHeightPx, ctx.density, ctx.defaultLineSpacing)
    val pitch = verticalPitchPx(params.fontSizePx, params.lineHeightEm, block.rubies.isNotEmpty())
    var headColumns = (availableWidthPx / pitch).toInt().coerceAtLeast(1)
    val full = withContext(Dispatchers.Main) {
        layoutVerticalParagraph(
            text = block.content.text,
            rubies = block.rubies,
            tcyRanges = params.tcyRanges,
            fontSizePx = params.fontSizePx,
            lineHeightEm = params.lineHeightEm,
            columnHeightPx = params.columnHeightPx,
            textAlign = params.textAlign,
            measurer = ctx.verticalMeasurer(),
            cache = ctx.cacheFor(block)
        )
    }
    if (full.columns.size <= headColumns) return null
    // Column-level orphan/widow control mirrors the horizontal line policy.
    val orphans = block.style.orphans.coerceAtLeast(1)
    val widows = block.style.widows.coerceAtLeast(1)
    if (headColumns < orphans) return null
    val tailColumns = full.columns.size - headColumns
    if (tailColumns < widows) {
        headColumns -= (widows - tailColumns)
        if (headColumns < orphans) return null
    }
    val endOffset = full.endOffsetForColumnPrefix(headColumns)
    if (endOffset <= 0 || endOffset >= block.content.length) return null
    return sliceVerticalContentText(block, endOffset)
}

private fun sliceVerticalContentText(block: TextContentBlock, endOffset: Int): Pair<TextContentBlock, TextContentBlock>? {
    val safeEnd = endOffset.coerceIn(1, block.content.length - 1)
    val headContent = block.content.subSequence(0, safeEnd)
    val tailContent = block.content.subSequence(safeEnd, block.content.length)
        .withoutVerticalFirstLineIndent()
    if (headContent.isBlank() || tailContent.isBlank()) return null
    val headRubies = sliceRubyAnnotations(block.rubies, 0, safeEnd)
    val tailRubies = sliceRubyAnnotations(block.rubies, safeEnd, block.content.length)
    val splitOffset = block.startCharOffsetInSource + safeEnd
    fun withSlice(
        content: AnnotatedString,
        rubies: List<RubyAnnotation>,
        startOffset: Int,
        endOffset: Int,
        topMargin: androidx.compose.ui.unit.Dp
    ): TextContentBlock {
        val style = block.style.copy(margin = block.style.margin.copy(top = topMargin))
        return when (block) {
            is ParagraphBlock -> block.copy(
                content = content, rubies = rubies, style = style,
                startCharOffsetInSource = startOffset, endCharOffsetInSource = endOffset
            )
            is HeaderBlock -> block.copy(
                content = content, rubies = rubies, style = style,
                startCharOffsetInSource = startOffset, endCharOffsetInSource = endOffset
            )
            is QuoteBlock -> block.copy(
                content = content, rubies = rubies, style = style,
                startCharOffsetInSource = startOffset, endCharOffsetInSource = endOffset
            )
            is ListItemBlock -> block.copy(
                content = content, rubies = rubies, style = style,
                startCharOffsetInSource = startOffset, endCharOffsetInSource = endOffset
            )
            else -> block
        }
    }
    val head = withSlice(headContent, headRubies, block.startCharOffsetInSource, splitOffset, block.style.margin.top)
    val tail = withSlice(
        tailContent, tailRubies, splitOffset, block.endCharOffsetInSource, 0.dp
    )
    return head to tail
}

internal fun measureVerticalContentImage(
    block: ImageBlock,
    contentWidthPx: Int,
    columnHeightPx: Float,
    imageSizeMultiplier: Float,
    hideImages: Boolean
): Int {
    if (hideImages) return 0
    val intrinsicWidth = block.intrinsicWidth?.takeIf { it > 0f }
    val intrinsicHeight = block.intrinsicHeight?.takeIf { it > 0f }
    if (intrinsicWidth == null || intrinsicHeight == null) return contentWidthPx
    val aspect = intrinsicHeight / intrinsicWidth
    var width = minOf(intrinsicWidth * imageSizeMultiplier, contentWidthPx.toFloat())
    var height = width * aspect
    if (height > columnHeightPx) {
        height = columnHeightPx
        width = (height / aspect).coerceAtLeast(1f)
    }
    return width.roundToInt().coerceAtLeast(1)
}

private fun expandVerticalContentFlow(block: ContentBlock): List<ContentBlock> =
    if (block is FlexContainerBlock && block.isVerticalContentBlock() &&
        block.style.flexDirection != "row" && block.style.display != "reader-chant-flow"
    ) {
        block.children.flatMap { expandVerticalContentFlow(it) }
    } else {
        listOf(block)
    }

/**
 * Paginates vertical-rl content blocks width-first (columns right-to-left).
 * Horizontal fallback blocks (tables, lists, …) each own their page segment;
 * callers pre-split oversize ones through the horizontal splitter.
 */
/**
 * True when a paginated page must render with the native tategaki engine.
 * Pagination segments mixed writing modes ([segmentBlocksByWritingMode]), so
 * probing the first non-spacer block decides the whole page.
 */
internal fun List<ContentBlock>.isVerticalReaderPage(): Boolean =
    firstOrNull { it !is SpacerBlock }?.isVerticalContentBlock() == true

internal fun defaultVerticalLineSpacing(baseStyle: TextStyle): Float =
    if (baseStyle.lineHeight.isSpecified && baseStyle.fontSize.isSpecified && baseStyle.fontSize.value > 0f) {
        baseStyle.lineHeight.value / baseStyle.fontSize.value
    } else {
        1.45f
    }

internal suspend fun paginateVerticalContentBlocks(
    blocks: List<ContentBlock>,
    ctx: VerticalContentMeasureContext,
    preSplitFallback: suspend (ContentBlock) -> List<ContentBlock>
): List<Page> {
    val pages = mutableListOf<Page>()
    if (blocks.isEmpty() || ctx.pageWidthPx <= 0) return pages
    val ready = ArrayDeque<ContentBlock>()
    for (source in blocks.flatMap { expandVerticalContentFlow(it) }) {
        coroutineContext.ensureActive()
        if (source is TextContentBlock || source is ImageBlock || source is SpacerBlock) {
            ready.add(source)
        } else {
            ready.addAll(preSplitFallback(source))
        }
    }
    if (ready.isEmpty()) return pages

    suspend fun verticalFlowWidth(block: ContentBlock): Int = when (block) {
        is TextContentBlock -> {
            measureVerticalContentText(block, ctx) + with(ctx.density) {
                block.style.padding.left.coerceAtLeast(0.dp).toPx() +
                    block.style.padding.right.coerceAtLeast(0.dp).toPx() +
                    (block.style.borderLeft?.width?.toPx() ?: 0f) +
                    (block.style.borderRight?.width?.toPx() ?: 0f)
            }.roundToInt()
        }
        is ImageBlock -> {
            val columnHeightPx = (ctx.pageHeightPx - with(ctx.density) {
                block.style.padding.top.coerceAtLeast(0.dp).toPx() +
                    block.style.padding.bottom.coerceAtLeast(0.dp).toPx() +
                    (block.style.borderTop?.width?.toPx() ?: 0f) +
                    (block.style.borderBottom?.width?.toPx() ?: 0f)
            }).coerceAtLeast(48f)
            measureVerticalContentImage(
                block, ctx.pageWidthPx, columnHeightPx,
                ctx.imageSizeMultiplier, ctx.hideImages
            )
        }
        is SpacerBlock -> 0
        else -> ctx.pageWidthPx
    }

    fun verticalSpaceBefore(previous: ContentBlock?, current: ContentBlock): Int =
        with(ctx.density) {
            val left = current.style.margin.left.toPx()
            if (previous == null) {
                0
            } else {
                maxOf(previous.style.margin.right.toPx(), left).roundToInt()
            }
        }

    suspend fun verticalFlowSplit(
        block: ContentBlock,
        availableWidthPx: Int
    ): Pair<ContentBlock, ContentBlock>? {
        if (block !is TextContentBlock || availableWidthPx <= 0) return null
        val padH = with(ctx.density) {
            block.style.padding.left.coerceAtLeast(0.dp).toPx() +
                block.style.padding.right.coerceAtLeast(0.dp).toPx() +
                (block.style.borderLeft?.width?.toPx() ?: 0f) +
                (block.style.borderRight?.width?.toPx() ?: 0f)
        }.roundToInt()
        val pair = splitVerticalContentText(block, (availableWidthPx - padH).coerceAtLeast(0), ctx)
            ?: return null
        return pair.first to pair.second
    }

    paginateVerticalFlow(
        blocks = ready.toList(),
        contentWidthPx = ctx.pageWidthPx,
        widthOf = { verticalFlowWidth(it) },
        spaceBefore = { previous, current -> verticalSpaceBefore(previous, current) },
        splitForWidth = { block, availableWidthPx -> verticalFlowSplit(block, availableWidthPx) },
        isFullWidth = { it !is TextContentBlock && it !is ImageBlock && it !is SpacerBlock },
        hasContent = { block ->
            when (block) {
                is TextContentBlock -> block.content.isNotBlank()
                is ImageBlock -> !ctx.hideImages
                else -> true
            }
        },
        onEmitPage = { pageBlocks -> pages += Page(pageBlocks) }
    )
    return pages
}
