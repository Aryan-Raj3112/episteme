/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader

import android.os.Build
import timber.log.Timber
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BlockMeasurementProvider {
    suspend fun measure(block: ContentBlock): Int
    suspend fun split(block: ParagraphBlock, availableHeight: Int): Pair<ParagraphBlock, ParagraphBlock>?
    suspend fun split(block: WrappingContentBlock, availableHeight: Int): Pair<WrappingContentBlock, List<ContentBlock>>?
    suspend fun split(block: TableBlock, availableHeight: Int): Pair<TableBlock, TableBlock>?
    suspend fun split(block: FlexContainerBlock, availableHeight: Int): Pair<FlexContainerBlock, FlexContainerBlock>?
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class SuspendingAndroidBlockMeasurementProvider(
    private val textMeasurer: TextMeasurer,
    private val constraints: Constraints,
    private val textStyle: TextStyle,
    private val density: Density
) : BlockMeasurementProvider {

    override suspend fun measure(block: ContentBlock): Int {
        return measureBlockHeight(
            block = block,
            textMeasurer = textMeasurer,
            constraints = constraints,
            defaultStyle = textStyle,
            headerStyle = textStyle.copy(fontWeight = FontWeight.Bold),
            density = density
        )
    }

    override suspend fun split(block: ParagraphBlock, availableHeight: Int): Pair<ParagraphBlock, ParagraphBlock>? {
        return splitParagraphBlock(
            block = block,
            textMeasurer = textMeasurer,
            constraints = constraints,
            textStyle = textStyle,
            availableHeight = availableHeight,
            density = density
        )
    }

    override suspend fun split(block: WrappingContentBlock, availableHeight: Int): Pair<WrappingContentBlock, List<ContentBlock>>? {

        val imageBlock = block.floatedImage
        val (imageWidthPx, imageHeightPx) = run {
            val imageStyle = imageBlock.style
            val intrinsicWidth = imageBlock.intrinsicWidth
            val intrinsicHeight = imageBlock.intrinsicHeight

            if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f) {
                0f to 0f
            } else {
                val aspectRatio = intrinsicHeight / intrinsicWidth
                val renderWidth = with(density) {
                    var w = intrinsicWidth

                    if (imageStyle.width != Dp.Unspecified) {
                        w = imageStyle.width.toPx()
                    }

                    if (imageStyle.maxWidth != Dp.Unspecified) {
                        w = w.coerceAtMost(imageStyle.maxWidth.toPx())
                    }

                    w.coerceAtMost(constraints.maxWidth.toFloat())
                }
                renderWidth to (renderWidth * aspectRatio)
            }
        }

        if (imageWidthPx <= 0 || imageHeightPx <= 0) {
            return null
        }

        val paragraphOffsets = mutableListOf<IntRange>()
        val fullText = buildAnnotatedString {
            block.paragraphsToWrap.forEachIndexed { index, paragraphBlock ->
                val textStartOffset = length
                append(paragraphBlock.content)
                val textEndOffset = length
                paragraphOffsets.add(textStartOffset until textEndOffset)

                if (index < block.paragraphsToWrap.lastIndex) {
                    append("\n\n")
                }
            }
        }

        if (fullText.isEmpty()) {
            return null
        }

        val paragraphEndOffsetMap = mutableMapOf<Int, Int>()
        var currentParaOffset = 0
        block.paragraphsToWrap.forEachIndexed { index, p ->
            currentParaOffset += p.content.length
            paragraphEndOffsetMap[currentParaOffset - 1] = index
            if (index < block.paragraphsToWrap.lastIndex) {
                currentParaOffset += 2
            }
        }

        var currentY = 0f
        var textOffset = 0
        var lastFittingTextOffset = 0
        val wrappingContentWidth = (constraints.maxWidth - imageWidthPx).toInt().coerceAtLeast(0)

        while (textOffset < fullText.length) {
            val isBesideImage = currentY < imageHeightPx
            val currentMaxWidth = if (isBesideImage) wrappingContentWidth else constraints.maxWidth

            if (currentMaxWidth <= 0) {
                break
            }

            val lineConstraints = constraints.copy(minWidth = 0, maxWidth = currentMaxWidth)
            val remainingText = fullText.subSequence(textOffset, fullText.length)

            val styleForMeasure = remainingText.spanStyles
                .firstOrNull { it.item.fontFamily != null }?.item?.fontFamily
                ?.let { textStyle.copy(fontFamily = it) }
                ?: textStyle

            val layoutResult = withContext(Dispatchers.Main) {
                textMeasurer.measure(remainingText, style = styleForMeasure, constraints = lineConstraints)
            }

            val firstLineEndOffset = layoutResult.getLineEnd(0, visibleEnd = true)
            val lineHeight = layoutResult.getLineBottom(0)

            val endOfLineVisibleCharIndex = textOffset + firstLineEndOffset - 1
            val paraIndex = paragraphEndOffsetMap[endOfLineVisibleCharIndex]

            var gapHeight = 0f
            if (paraIndex != null && paraIndex < block.paragraphsToWrap.lastIndex) {
                val currentPara = block.paragraphsToWrap[paraIndex]
                val nextPara = block.paragraphsToWrap[paraIndex + 1]
                with(density) {
                    val marginBottom = currentPara.style.margin.bottom.toPx()
                    val marginTop = nextPara.style.margin.top.toPx()
                    gapHeight = maxOf(marginBottom, marginTop)
                }
            }

            if (currentY + lineHeight + gapHeight > availableHeight) {
                if (currentY + lineHeight <= availableHeight) {
                    lastFittingTextOffset = textOffset + firstLineEndOffset
                }
                break
            }

            currentY += lineHeight + gapHeight

            textOffset += firstLineEndOffset
            lastFittingTextOffset = textOffset

            while (textOffset < fullText.length && fullText[textOffset].isWhitespace()) {
                textOffset++
            }

            if (textOffset < fullText.length && firstLineEndOffset == 0) {
                textOffset++; continue
            }
            if (firstLineEndOffset == 0) break
        }

        if (lastFittingTextOffset == 0) {
            return null
        }

        val paragraphsForPart1 = mutableListOf<ParagraphBlock>()
        val remainingBlocksForPart2 = mutableListOf<ContentBlock>()
        var splitOccurred = false

        for ((index, paraRange) in paragraphOffsets.withIndex()) {
            val originalPara = block.paragraphsToWrap[index]

            if (splitOccurred) {
                remainingBlocksForPart2.add(originalPara)
                continue
            }

            val separatorLength = 2
            val isLastPara = index == paragraphOffsets.lastIndex

            if (!isLastPara && lastFittingTextOffset >= paraRange.last + separatorLength || isLastPara && lastFittingTextOffset >= paraRange.last) {
                paragraphsForPart1.add(originalPara)
            } else {
                val splitPointInPara = lastFittingTextOffset - paraRange.first
                if (splitPointInPara <= 0) {
                    remainingBlocksForPart2.add(originalPara)
                    splitOccurred = true
                    continue
                }

                val originalContent = originalPara.content
                val part1Text = originalContent.subSequence(0, splitPointInPara)

                var trimStartIndex = splitPointInPara
                while (trimStartIndex < originalContent.length && originalContent[trimStartIndex].isWhitespace()) {
                    trimStartIndex++
                }
                val part2Text = originalContent.subSequence(trimStartIndex, originalContent.length)

                if (part1Text.isNotEmpty()) {
                    paragraphsForPart1.add(originalPara.copy(content = part1Text))
                }
                if (part2Text.isNotEmpty()) {
                    val part2TextWithoutIndent = buildAnnotatedString {
                        append(part2Text)
                        part2Text.paragraphStyles.firstOrNull { it.start == 0 && it.item.textIndent != null }?.let { styleRange ->
                            val originalIndent = styleRange.item.textIndent
                            if (originalIndent != null) {
                                addStyle(
                                    style = styleRange.item.copy(
                                        textIndent = TextIndent(
                                            firstLine = 0.sp,
                                            restLine = originalIndent.restLine
                                        )
                                    ),
                                    start = 0,
                                    end = styleRange.end.coerceAtMost(this.length)
                                )
                            }
                        }
                    }
                    remainingBlocksForPart2.add(originalPara.copy(content = part2TextWithoutIndent))
                }
                splitOccurred = true
            }
        }

        if (paragraphsForPart1.isEmpty()) {
            return null
        }

        val part1 = block.copy(paragraphsToWrap = paragraphsForPart1)

        if (remainingBlocksForPart2.isNotEmpty()) {
            val firstBlock = remainingBlocksForPart2[0]
            val newStyle = firstBlock.style.copy(
                margin = firstBlock.style.margin.copy(top = 0.dp)
            )
            remainingBlocksForPart2[0] = copyBlockWithNewStyle(firstBlock, newStyle)
        }

        return part1 to remainingBlocksForPart2
    }

    override suspend fun split(block: TableBlock, availableHeight: Int): Pair<TableBlock, TableBlock>? {
        var currentHeight = 0
        var splitRowIndex = -1

        // Account for top and bottom decorations (padding + border)
        val decorationTop = with(density) {
            block.style.padding.top.toPx() + (block.style.border?.width?.toPx() ?: 0f)
        }.toInt()

        val decorationBottom = with(density) {
            block.style.padding.bottom.toPx() + (block.style.border?.width?.toPx() ?: 0f)
        }.toInt()

        Timber.tag("PAGINATION_DEBUG").d("SplitTable: avail=$availableHeight, topDec=$decorationTop, botDec=$decorationBottom")
        currentHeight += decorationTop

        for (i in block.rows.indices) {
            val row = block.rows[i]
            var maxRowHeight = 0
            val totalColspan = row.sumOf { it.colspan }.toFloat().coerceAtLeast(1f)

            row.forEach { cell ->
                val cellMaxWidth = ((constraints.maxWidth) * (cell.colspan.toFloat() / totalColspan)).toInt()
                constraints.copy(maxWidth = cellMaxWidth.coerceAtLeast(0))

                var cellHeight = 0
                cell.content.forEach { b ->
                    cellHeight += measure(b)
                }
                val cellDecoration = with(density) {
                    cell.style.blockStyle.padding.top.toPx() + cell.style.blockStyle.padding.bottom.toPx() +
                            (cell.style.blockStyle.border?.width?.toPx() ?: 0f) * 2
                }.toInt()
                maxRowHeight = maxOf(maxRowHeight, cellHeight + cellDecoration)
            }

            // CHECK: Must account for decorationBottom here
            if (currentHeight + maxRowHeight + decorationBottom > availableHeight) {
                Timber.tag("PAGINATION_DEBUG").d("SplitTable: Breaking at row $i. currentH=$currentHeight, rowH=$maxRowHeight")
                splitRowIndex = i
                break
            }
            currentHeight += maxRowHeight
        }

        if (splitRowIndex <= 0) return null // Can't even fit the first row

        val part1Rows = block.rows.subList(0, splitRowIndex)
        val part2Rows = block.rows.subList(splitRowIndex, block.rows.size)

        val part1 = block.copy(rows = part1Rows, style = block.style.copy(margin = block.style.margin.copy(bottom = 0.dp)))
        val part2 = block.copy(rows = part2Rows, style = block.style.copy(margin = block.style.margin.copy(top = 0.dp)))

        return part1 to part2
    }

    override suspend fun split(block: FlexContainerBlock, availableHeight: Int): Pair<FlexContainerBlock, FlexContainerBlock>? {
        if (block.style.flexDirection == "row") return null

        var currentHeight = 0
        var splitChildIndex = -1

        val decorationTop = with(density) {
            block.style.padding.top.toPx() + (block.style.border?.width?.toPx() ?: 0f)
        }.toInt()

        val decorationBottom = with(density) {
            block.style.padding.bottom.toPx() + (block.style.border?.width?.toPx() ?: 0f)
        }.toInt()

        currentHeight += decorationTop

        for (i in block.children.indices) {
            val child = block.children[i]
            val childHeight = measure(child)
            val margin = with(density) {
                if (i > 0) {
                    val prevMargin = block.children[i-1].style.margin.bottom.toPx()
                    val currMargin = child.style.margin.top.toPx()
                    maxOf(prevMargin, currMargin)
                } else child.style.margin.top.toPx()
            }.toInt()

            // CHECK: Must account for decorationBottom here
            if (currentHeight + childHeight + margin + decorationBottom > availableHeight) {
                splitChildIndex = i
                break
            }
            currentHeight += childHeight + margin
        }

        if (splitChildIndex <= 0) return null

        val part1Children = block.children.subList(0, splitChildIndex)
        val part2Children = block.children.subList(splitChildIndex, block.children.size)

        val part1 = block.copy(children = part1Children, style = block.style.copy(margin = block.style.margin.copy(bottom = 0.dp)))
        val part2 = block.copy(children = part2Children, style = block.style.copy(margin = block.style.margin.copy(top = 0.dp)))

        return part1 to part2
    }
}

private fun copyBlockWithNewStyle(block: ContentBlock, newStyle: BlockStyle): ContentBlock {
    return when (block) {
        is ParagraphBlock -> block.copy(style = newStyle)
        is HeaderBlock -> block.copy(style = newStyle)
        is ImageBlock -> block.copy(style = newStyle)
        is SpacerBlock -> block.copy(style = newStyle)
        is QuoteBlock -> block.copy(style = newStyle)
        is ListItemBlock -> block.copy(style = newStyle)
        is WrappingContentBlock -> block.copy(style = newStyle)
        is TableBlock -> block.copy(style = newStyle)
        is FlexContainerBlock -> block.copy(style = newStyle)
        is MathBlock -> block.copy(style = newStyle)
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
suspend fun paginate(
    blocks: List<ContentBlock>,
    pageHeight: Int,
    measurementProvider: BlockMeasurementProvider,
    density: Density
): List<Page> {
    if (blocks.isEmpty()) {
        return emptyList()
    }
    Timber.d("Starting pagination for ${blocks.size} blocks with page height $pageHeight.")

    val pages = mutableListOf<Page>()
    var currentPageContent = mutableListOf<ContentBlock>()
    var remainingHeight = pageHeight
    val remainingBlocks = blocks.toMutableList()
    var pageIndex = 0
    val safetyMarginPerBlock = 2

    while (remainingBlocks.isNotEmpty()) {
        val block = remainingBlocks.removeAt(0)
        val blockHeight = measurementProvider.measure(block)
        val blockHeightWithSafetyMargin = blockHeight + safetyMarginPerBlock

        val spaceBetweenBlocks = with(density) {
            if (currentPageContent.isNotEmpty()) {
                val prevMargin = currentPageContent.last().style.margin.bottom.toPx()
                val currentMargin = block.style.margin.top.toPx()
                maxOf(prevMargin, currentMargin)
            } else {
                block.style.margin.top.toPx()
            }
        }

        val spaceRequired = (blockHeightWithSafetyMargin + spaceBetweenBlocks).toInt()

        Timber.tag("PAGINATION_DEBUG").d("Processing ${block::class.simpleName}: req=$spaceRequired, remaining=$remainingHeight, margin=$spaceBetweenBlocks, heightOnly=$blockHeight")

        if (spaceRequired <= remainingHeight) {
            var blockToAdd = block
            val collapsedMarginDp = with(density) { spaceBetweenBlocks.toDp() }

            if (currentPageContent.isNotEmpty()) {
                val prevBlock = currentPageContent.last()
                val newPrevStyle = prevBlock.style.copy(margin = prevBlock.style.margin.copy(bottom = 0.dp))
                val newPrevBlock = copyBlockWithNewStyle(prevBlock, newPrevStyle)
                currentPageContent[currentPageContent.size - 1] = newPrevBlock
            }
            val newCurrentStyle = block.style.copy(margin = block.style.margin.copy(top = collapsedMarginDp))
            blockToAdd = copyBlockWithNewStyle(block, newCurrentStyle)

            currentPageContent.add(blockToAdd)
            remainingHeight -= spaceRequired
        } else {
            var wasSplit = false
            val heightForSplitting = remainingHeight - spaceBetweenBlocks.toInt()

            if (heightForSplitting > 50) {
                when (block) {
                    is ParagraphBlock -> {
                        if (!block.style.pageBreakInsideAvoid) {
                            measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                                if (part1.content.isNotEmpty()) {
                                    val collapsedMarginDp = with(density) { spaceBetweenBlocks.toDp() }
                                    if (currentPageContent.isNotEmpty()) {
                                        val prevBlock = currentPageContent.last()
                                        val newPrevStyle = prevBlock.style.copy(margin = prevBlock.style.margin.copy(bottom = 0.dp))
                                        currentPageContent[currentPageContent.size - 1] = copyBlockWithNewStyle(prevBlock, newPrevStyle)
                                    }
                                    val newPart1Style = part1.style.copy(margin = part1.style.margin.copy(top = collapsedMarginDp))
                                    val finalPart1 = part1.copy(style = newPart1Style)
                                    currentPageContent.add(finalPart1)
                                    if (part2.content.isNotEmpty()) remainingBlocks.add(0, part2)
                                    wasSplit = true
                                }
                            }
                        }
                    }
                    is WrappingContentBlock -> {
                        measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                            if (part1.paragraphsToWrap.any { it.content.isNotBlank() }) {
                                val collapsedMarginDp = with(density) { spaceBetweenBlocks.toDp() }
                                if (currentPageContent.isNotEmpty()) {
                                    val prevBlock = currentPageContent.last()
                                    val newPrevStyle = prevBlock.style.copy(margin = prevBlock.style.margin.copy(bottom = 0.dp))
                                    currentPageContent[currentPageContent.size - 1] = copyBlockWithNewStyle(prevBlock, newPrevStyle)
                                }
                                val newPart1Style = part1.style.copy(margin = part1.style.margin.copy(top = collapsedMarginDp))
                                val finalPart1 = part1.copy(style = newPart1Style)
                                currentPageContent.add(finalPart1)
                                if (part2.isNotEmpty()) {
                                    remainingBlocks.addAll(0, part2)
                                }
                                wasSplit = true
                            }
                        }
                    }
                    is TableBlock -> {
                        measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                            val collapsedMarginDp = with(density) { spaceBetweenBlocks.toDp() }
                            currentPageContent.add(copyBlockWithNewStyle(part1, part1.style.copy(margin = part1.style.margin.copy(top = collapsedMarginDp))))
                            remainingBlocks.add(0, part2)
                            wasSplit = true
                        }
                    }
                    is FlexContainerBlock -> {
                        measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                            val collapsedMarginDp = with(density) { spaceBetweenBlocks.toDp() }
                            currentPageContent.add(copyBlockWithNewStyle(part1, part1.style.copy(margin = part1.style.margin.copy(top = collapsedMarginDp))))
                            remainingBlocks.add(0, part2)
                            wasSplit = true
                        }
                    }
                    else -> { Timber.d("Page ${pageIndex + 1}: Block type is not splittable.") }
                }
            } else {
                Timber.d("Page ${pageIndex + 1}: Not enough height for splitting ($heightForSplitting <= 50).")
            }

            if (!wasSplit) {
                if (currentPageContent.isEmpty()) {
                    Timber.tag("PAGINATION_DEBUG").w("FORCING block ${block::class.simpleName} onto page because it is the first block, even though req($spaceRequired) > remaining($remainingHeight)")
                    currentPageContent.add(block)
                } else {
                    Timber.tag("PAGINATION_DEBUG").d("Block ${block::class.simpleName} did not fit and was not split. Moving to next page.")
                    remainingBlocks.add(0, block)
                }
            }

            pages.add(Page(content = currentPageContent.toList()))
            pageIndex++
            currentPageContent = mutableListOf()
            remainingHeight = pageHeight
        }
    }
    if (currentPageContent.isNotEmpty()) {
        pages.add(Page(content = currentPageContent.toList()))
    }

    Timber.i("Pagination complete. Produced ${pages.size} pages from ${blocks.size} initial blocks.")
    return pages
}

private suspend fun measureBlockHeight(
    block: ContentBlock,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density
): Int {
    var verticalPaddingPx = 0f
    var horizontalPaddingPx = 0f
    var verticalBorderPx = 0f
    var horizontalBorderPx = 0f

    with(density) {
        verticalPaddingPx = block.style.padding.top.toPx() + block.style.padding.bottom.toPx()
        horizontalPaddingPx = block.style.padding.left.toPx() + block.style.padding.right.toPx()
        block.style.border?.let {
            verticalBorderPx = it.width.toPx() * 2
            horizontalBorderPx = it.width.toPx() * 2
        }
    }

    val isBorderBox = block.style.boxSizing == "border-box"
    val specifiedWidthDp = block.style.width
    val specifiedMaxWidthDp = block.style.maxWidth

    // 1. Determine the block's final outer width.
    val blockOuterWidthPx = with(density) {
        var effectiveWidthPx = constraints.maxWidth.toFloat()
        if (specifiedWidthDp != Dp.Unspecified) {
            effectiveWidthPx = specifiedWidthDp.toPx()
        }
        if (specifiedMaxWidthDp != Dp.Unspecified) {
            val maxWidthPx = specifiedMaxWidthDp.toPx()
            if (effectiveWidthPx > maxWidthPx) {
                effectiveWidthPx = maxWidthPx
            }
        }
        effectiveWidthPx.coerceAtMost(constraints.maxWidth.toFloat())
    }

    // 2. Determine the width available for the content itself.
    val contentMaxWidth = if (isBorderBox) {
        (blockOuterWidthPx - horizontalPaddingPx - horizontalBorderPx)
    } else {
        blockOuterWidthPx
    }

    val adjustedConstraints = constraints.copy(
        maxWidth = contentMaxWidth.toInt().coerceAtLeast(0)
    )

    // 3. Measure the height of the actual content using the calculated content width.
    val contentHeight = when (block) {
        is ParagraphBlock -> {
            val height = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content,
                    style = defaultStyle.copy(textAlign = block.textAlign ?: defaultStyle.textAlign),
                    constraints = adjustedConstraints
                ).size.height
            }
            height
        }
        is HeaderBlock -> {
            val style = headerStyle.copy(
                textAlign = block.textAlign ?: headerStyle.textAlign
            )
            val height = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content,
                    style = style,
                    constraints = adjustedConstraints
                ).size.height
            }
            height
        }
        is ImageBlock -> {
            val imageIntrinsicWidth = block.intrinsicWidth
            val imageIntrinsicHeight = block.intrinsicHeight

            if (imageIntrinsicWidth != null && imageIntrinsicHeight != null && imageIntrinsicWidth > 0) {
                val aspectRatio = imageIntrinsicHeight / imageIntrinsicWidth
                val styledWidthDp = block.style.width

                val imageRenderWidthPx = if (styledWidthDp != Dp.Unspecified) {
                    with(density) { styledWidthDp.toPx() }
                } else {
                    contentMaxWidth
                }

                val height = (imageRenderWidthPx * aspectRatio).toInt()
                height
            } else {
                Timber.w("Image at '${block.path}' has no valid intrinsic dimensions, falling back to fixed height.")
                if (block.style.height != Dp.Unspecified) {
                    with(density) { block.style.height.toPx() }.toInt()
                } else {
                    with(density) { 250.dp.toPx() }.toInt()
                }
            }
        }
        is SpacerBlock -> {
            // A spacer's height property defines its content height.
            val height = with(density) { block.height.toPx() }.toInt()
            height
        }
        is QuoteBlock -> {
            val height = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content,
                    style = defaultStyle.copy(textAlign = block.textAlign ?: defaultStyle.textAlign),
                    constraints = adjustedConstraints
                ).size.height
            }
            height
        }
        is ListItemBlock -> {
            val markerWidthPx = with(density) { 32.dp.toPx() }.toInt()
            val textConstraints = adjustedConstraints.copy(
                maxWidth = (adjustedConstraints.maxWidth - markerWidthPx).coerceAtLeast(0)
            )
            val textContentHeight = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content,
                    style = defaultStyle,
                    constraints = textConstraints
                ).size.height
            }
            val markerImageHeight = if (block.itemMarkerImage != null) {
                with(density) { (defaultStyle.fontSize.value * 0.8f).sp.toPx() }.toInt()
            } else {
                0
            }
            val height = maxOf(textContentHeight, markerImageHeight)
            height
        }
        is TableBlock -> {
            var totalHeight = 0
            block.rows.forEach { row ->
                var maxRowHeight = 0
                val totalColspan = row.sumOf { it.colspan }.toFloat().coerceAtLeast(1f)

                row.forEach { cell ->
                    val cellBlockStyle = cell.style.blockStyle
                    val cellMaxWidth = when {
                        cellBlockStyle.width.isSpecified -> with(density) { cellBlockStyle.width.toPx() }.toInt()
                        else -> (adjustedConstraints.maxWidth * (cell.colspan.toFloat() / totalColspan)).toInt()
                    }

                    val cellConstraints = adjustedConstraints.copy(maxWidth = cellMaxWidth.coerceAtLeast(0))

                    val cellContentHeight = calculateContentHeightWithMargins(cell.content, textMeasurer, cellConstraints, defaultStyle, headerStyle, density)

                    var cellDecorationHeight = 0f
                    with(density) {
                        cellDecorationHeight = cellBlockStyle.padding.top.toPx() + cellBlockStyle.padding.bottom.toPx()
                        cellBlockStyle.border?.let { cellDecorationHeight += it.width.toPx() * 2 }
                    }
                    maxRowHeight = maxOf(maxRowHeight, (cellContentHeight + cellDecorationHeight).toInt())
                }
                totalHeight += maxRowHeight
            }
            totalHeight
        }
        is WrappingContentBlock -> {
            val imageBlock = block.floatedImage

            val (imageWidthPx, imageHeightPx) = run {
                val imageStyle = imageBlock.style
                val intrinsicWidth = imageBlock.intrinsicWidth
                val intrinsicHeight = imageBlock.intrinsicHeight

                if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f) {
                    0f to 0f
                } else {
                    val aspectRatio = intrinsicHeight / intrinsicWidth
                    val renderWidth = with(density) {
                        var w = intrinsicWidth

                        if (imageStyle.width != Dp.Unspecified) {
                            w = imageStyle.width.toPx()
                        }

                        if (imageStyle.maxWidth != Dp.Unspecified) {
                            w = w.coerceAtMost(imageStyle.maxWidth.toPx())
                        }

                        w.coerceAtMost(adjustedConstraints.maxWidth.toFloat())
                    }
                    renderWidth to (renderWidth * aspectRatio)
                }
            }

            // If image has no size, it can't float. Just measure the paragraphs.
            if (imageWidthPx <= 0 || imageHeightPx <= 0) {
                val height = block.paragraphsToWrap.sumOf { p ->
                    measureBlockHeight(p, textMeasurer, adjustedConstraints, defaultStyle, headerStyle, density)
                }
                return height
            }

            // Combine all text into one string, preserving paragraph breaks with newlines.
            val fullText = buildAnnotatedString {
                block.paragraphsToWrap.forEachIndexed { index, paragraphBlock ->
                    append(paragraphBlock.content)
                    if (index < block.paragraphsToWrap.lastIndex) {
                        append("\n\n")
                    }
                }
            }

            val paragraphEndOffsetMap = mutableMapOf<Int, Int>()
            var currentOffset = 0
            block.paragraphsToWrap.forEachIndexed { index, p ->
                currentOffset += p.content.length
                paragraphEndOffsetMap[currentOffset - 1] = index
                if (index < block.paragraphsToWrap.lastIndex) {
                    currentOffset += 2
                }
            }

            var currentY = 0f
            var textOffset = 0
            val wrappingContentWidth = (adjustedConstraints.maxWidth - imageWidthPx).toInt().coerceAtLeast(0)

            // Loop until all text is measured.
            while (textOffset < fullText.length) {
                val isBesideImage = currentY < imageHeightPx
                val currentMaxWidth = if (isBesideImage) {
                    wrappingContentWidth
                } else {
                    adjustedConstraints.maxWidth
                }

                if (currentMaxWidth <= 0) {
                    break
                }

                val lineConstraints = adjustedConstraints.copy(maxWidth = currentMaxWidth)
                val remainingText = fullText.subSequence(textOffset, fullText.length)

                val styleForMeasure = remainingText.spanStyles
                    .firstOrNull { it.item.fontFamily != null }?.item?.fontFamily
                    ?.let { defaultStyle.copy(fontFamily = it) }
                    ?: defaultStyle

                val layoutResult = withContext(Dispatchers.Main) {
                    textMeasurer.measure(remainingText, style = styleForMeasure, constraints = lineConstraints)
                }

                val firstLineEndOffset = layoutResult.getLineEnd(0, visibleEnd = true)

                if (textOffset < fullText.length && firstLineEndOffset == 0) {
                    textOffset++
                    continue
                }
                if (firstLineEndOffset == 0) break

                val lineHeight = layoutResult.getLineBottom(0)
                currentY += lineHeight

                val endOfLineVisibleCharIndex = textOffset + firstLineEndOffset - 1
                val paraIndex = paragraphEndOffsetMap[endOfLineVisibleCharIndex]

                if (paraIndex != null && paraIndex < block.paragraphsToWrap.lastIndex) {
                    val currentPara = block.paragraphsToWrap[paraIndex]
                    val nextPara = block.paragraphsToWrap[paraIndex + 1]
                    with(density) {
                        val marginBottom = currentPara.style.margin.bottom.toPx()
                        val marginTop = nextPara.style.margin.top.toPx()
                        currentY += maxOf(marginBottom, marginTop)
                    }
                }

                textOffset += firstLineEndOffset

                // Skip leading whitespace for the next iteration.
                while (textOffset < fullText.length && fullText[textOffset].isWhitespace()) {
                    textOffset++
                }
            }

            val height = maxOf(currentY, imageHeightPx).toInt()
            height
        }
        is FlexContainerBlock -> {
            val isRow = block.style.flexDirection == "row"
            val height = if (isRow) {
                block.children.maxOfOrNull { child ->
                    measureBlockHeight(child, textMeasurer, adjustedConstraints, defaultStyle, headerStyle, density)
                } ?: 0
            } else {
                calculateContentHeightWithMargins(block.children, textMeasurer, adjustedConstraints, defaultStyle, headerStyle, density)
            }
            height
        }
        is MathBlock -> {
            val fontSizePx = with(density) { defaultStyle.fontSize.toPx() }
            val containerWidthPx = adjustedConstraints.maxWidth

            val widthPx = parseSvgDimension(block.svgWidth, fontSizePx, containerWidthPx, density)
            val heightPx = parseSvgDimension(block.svgHeight, fontSizePx, containerWidthPx, density)

            val finalHeight = if (heightPx != null) {
                Timber.d("Paginator measuring MathBlock '${block.elementId}': Using explicit height '${block.svgHeight}' -> ${heightPx.toInt()}px")
                heightPx.toInt()
            } else if (widthPx != null && block.svgViewBox != null) {
                val viewBoxParts = block.svgViewBox.split(' ', ',').mapNotNull { it.toFloatOrNull() }
                if (viewBoxParts.size == 4 && viewBoxParts[2] > 0) {
                    val viewBoxWidth = viewBoxParts[2]
                    val viewBoxHeight = viewBoxParts[3]
                    val aspectRatio = viewBoxHeight / viewBoxWidth
                    val calculatedHeight = (widthPx * aspectRatio).toInt()
                    Timber.d("Paginator measuring MathBlock '${block.elementId}': Using width '${block.svgWidth}' -> ${widthPx}px and viewBox ratio $aspectRatio -> calculated height ${calculatedHeight}px")
                    calculatedHeight
                } else {
                    val fallbackHeight = with(density) { (defaultStyle.fontSize.value * 3).sp.toPx() }.toInt()
                    Timber.w("Paginator measuring MathBlock '${block.elementId}': Invalid viewBox '${block.svgViewBox}'. Using fallback height ${fallbackHeight}px")
                    fallbackHeight
                }
            } else {
                val fallbackHeight = with(density) { (defaultStyle.fontSize.value * 3).sp.toPx() }.toInt()
                Timber.w("Paginator measuring MathBlock '${block.elementId}': No usable dimensions (width='${block.svgWidth}', height='${block.svgHeight}'). Using fallback height ${fallbackHeight}px")
                fallbackHeight
            }
            finalHeight
        }
    }
    // 4. Calculate the final total height based on box-sizing.
    val specifiedHeightDp = block.style.height
    val finalHeight = if (isBorderBox && specifiedHeightDp != Dp.Unspecified) {
        with(density) { specifiedHeightDp.toPx() }.toInt()
    } else {
        (contentHeight + verticalPaddingPx + verticalBorderPx).toInt()
    }

    Timber.tag("PAGINATION_DEBUG").v("Measure result for ${block::class.simpleName}: content=$contentHeight, paddingV=$verticalPaddingPx, borderV=$verticalBorderPx, total=$finalHeight")
    return finalHeight
}

private suspend fun splitParagraphBlock(
    block: ParagraphBlock,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    textStyle: TextStyle,
    availableHeight: Int,
    density: Density
): Pair<ParagraphBlock, ParagraphBlock>? {
    val text = block.content
    if (text.isEmpty()) return null

    val decorationTop = with(density) {
        block.style.padding.top.toPx() + (block.style.border?.width?.toPx() ?: 0f)
    }

    val decorationBottom = with(density) {
        block.style.padding.bottom.toPx() + (block.style.border?.width?.toPx() ?: 0f)
    }

    val availableTextHeight = availableHeight - decorationTop - decorationBottom

    Timber.tag("PAGINATION_DEBUG").d("SplitPara: totalAvail=$availableHeight, topDec=$decorationTop, botDec=$decorationBottom, textAvail=$availableTextHeight")

    if (availableTextHeight <= 0) {
        Timber.tag("PAGINATION_DEBUG").w("SplitPara aborted: availableTextHeight <= 0")
        return null
    }

    val layoutResult = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = text,
            style = textStyle,
            constraints = constraints
        )
    }

    if (layoutResult.size.height <= availableTextHeight) {
        return null
    }

    if (layoutResult.getLineBottom(0) > availableTextHeight) {
        return null
    }

    var lastVisibleLine = layoutResult.getLineForVerticalPosition(availableTextHeight)

    if (layoutResult.getLineBottom(lastVisibleLine) > availableHeight.toFloat()) {
        lastVisibleLine--
    }

    if (lastVisibleLine < 0) { // Safety check after decrementing
        return null
    }

    if (lastVisibleLine == 0) {
        Timber.d("Orphan control: Preventing split that would leave one line at the bottom of the page.")
        return null
    }

    var splitOffset = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)

    val part2CheckText = text.subSequence(splitOffset, text.length)
    if (part2CheckText.isNotBlank()) {
        val part2Layout = withContext(Dispatchers.Main) {
            textMeasurer.measure(
                text = part2CheckText,
                constraints = constraints
            )
        }
        if (part2Layout.lineCount == 1) {
            Timber.d("Widow control: Adjusting split to prevent a single line at the top of the next page.")
            lastVisibleLine--
            splitOffset = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)
        }
    }

    if (splitOffset <= 0 || splitOffset >= text.length) {
        return null
    }

    var part1End = splitOffset
    while (part1End > 0 && text[part1End - 1].isWhitespace()) {
        part1End--
    }
    val part1Text = text.subSequence(0, part1End)

    val initialPart2 = text.subSequence(splitOffset, text.length)
    var trimStartIndex = 0
    while (trimStartIndex < initialPart2.length && initialPart2[trimStartIndex].isWhitespace()) {
        trimStartIndex++
    }
    val part2Text = initialPart2.subSequence(trimStartIndex, initialPart2.length)

    if (part1Text.isEmpty() || part2Text.isEmpty()) {
        return null
    }

    val part2TextWithoutIndent = buildAnnotatedString {
        append(part2Text)
        part2Text.paragraphStyles.firstOrNull { it.start == 0 && it.item.textIndent != null }?.let { styleRange ->
            val originalIndent = styleRange.item.textIndent
            if (originalIndent != null) {
                addStyle(
                    style = styleRange.item.copy(
                        textIndent = TextIndent(
                            firstLine = 0.sp,
                            restLine = originalIndent.restLine
                        )
                    ),
                    start = 0,
                    end = styleRange.end.coerceAtMost(this.length)
                )
            }
        }
    }

    val originalStartOffset = block.startCharOffsetInSource
    val part1EndOffset = originalStartOffset + splitOffset

    val part1 = block.copy(
        content = part1Text,
        endCharOffsetInSource = part1EndOffset
    )
    val part2Style = block.style.copy(margin = block.style.margin.copy(top = 0.dp))
    val part2 = block.copy(
        content = part2TextWithoutIndent,
        style = part2Style,
        startCharOffsetInSource = part1EndOffset,
        endCharOffsetInSource = block.endCharOffsetInSource
    )

    Timber.d("Split block at offset $splitOffset. Part 1 len: ${part1.content.length}, Part 2 len: ${part2.content.length}")

    return part1 to part2
}

internal fun parseSvgDimension(
    dimension: String?,
    fontSizePx: Float,
    containerWidthPx: Int,
    density: Density
): Float? {
    if (dimension.isNullOrBlank()) return null
    return when {
        dimension.endsWith("ex") -> dimension.removeSuffix("ex").toFloatOrNull()?.let { it * 0.5f * fontSizePx }
        dimension.endsWith("em") -> dimension.removeSuffix("em").toFloatOrNull()?.let { it * fontSizePx }
        dimension.endsWith("px") -> dimension.removeSuffix("px").toFloatOrNull()
        dimension.endsWith("pt") -> dimension.removeSuffix("pt").toFloatOrNull()?.let { it * density.density * 160f / 72f }
        dimension.endsWith("%") -> dimension.removeSuffix("%").toFloatOrNull()?.let { (it / 100f) * containerWidthPx }
        else -> dimension.toFloatOrNull()
    }
}

private suspend fun calculateContentHeightWithMargins(
    children: List<ContentBlock>,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density
): Int {
    var totalHeight = 0
    children.forEachIndexed { index, child ->
        val childHeight = measureBlockHeight(child, textMeasurer, constraints, defaultStyle, headerStyle, density)
        val margin = with(density) {
            if (index > 0) {
                val prevMargin = children[index - 1].style.margin.bottom.toPx()
                val currMargin = child.style.margin.top.toPx()
                maxOf(prevMargin, currMargin)
            } else {
                child.style.margin.top.toPx()
            }
        }
        totalHeight += (childHeight + margin).toInt()
        Timber.tag("PAGINATION_DEBUG").v("  Internal Child ${child::class.simpleName}: h=$childHeight, margin=$margin, runningTotal=$totalHeight")
    }
    if (children.isNotEmpty()) {
        totalHeight += with(density) { children.last().style.margin.bottom.toPx() }.toInt()
    }
    return totalHeight
}