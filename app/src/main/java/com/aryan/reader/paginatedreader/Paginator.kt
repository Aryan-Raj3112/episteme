/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader

import android.os.Build
import android.util.Log
import com.aryan.reader.BuildConfig
import timber.log.Timber
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.reader.withoutForegroundColorSpans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

private const val DEBUG_PAGINATION_LOGS = false
private const val AndroidEpubCutoffLogTag = "EpistemeEpubCutoff"
private const val AndroidEpubPageGapDiagLogTag = "EpistemePageGapDiag"
private const val JustifiedSplitGapProbeMinFraction = 0.18f

/**
 * Minimum remaining space (px) below which vertical fragmentation is refused, mirroring the
 * paginator's heightForSplitting threshold.
 */
private const val MinVerticalFragmentSpacePx = 50

private fun TextLayoutResult.paginationMeasuredHeightPx(): Int {
    val lastLineBottomPx = if (lineCount > 0) getLineBottom(lineCount - 1) else 0f
    return measuredTextHeightForPagination(size.height, lastLineBottomPx)
}

private fun logAndroidEpubCutoff(message: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(AndroidEpubCutoffLogTag, message)
}

private fun logAndroidEpubPageGapDiag(message: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(AndroidEpubPageGapDiagLogTag, message)
}


private fun CharSequence.firstWordOrEmpty(): String {
    var start = 0
    while (start < length && this[start].isWhitespace()) start++
    if (start >= length) return ""
    var end = start
    while (end < length && !this[end].isWhitespace()) end++
    return subSequence(start, end).toString()
}

private fun CharSequence.skipWhitespaceFrom(index: Int): Int {
    var current = index.coerceIn(0, length)
    while (current < length && this[current].isWhitespace()) current++
    return current
}

private fun CharSequence.trimTrailingWhitespaceBefore(index: Int): Int {
    var current = index.coerceIn(0, length)
    while (current > 0 && this[current - 1].isWhitespace()) current--
    return current
}

private fun CharSequence.nextWordEndAfter(index: Int): Int {
    var current = skipWhitespaceFrom(index)
    while (current < length && !this[current].isWhitespace()) current++
    return current
}

private fun CharSequence.previousWordEndBefore(index: Int): Int {
    var current = trimTrailingWhitespaceBefore(index)
    while (current > 0 && !this[current - 1].isWhitespace()) current--
    return trimTrailingWhitespaceBefore(current)
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class SuspendingAndroidBlockMeasurementProvider(
    private val textMeasurer: TextMeasurer,
    private val constraints: Constraints,
    private val textStyle: TextStyle,
    private val density: Density,
    private val imageSizeMultiplier: Float,
    private val hideImages: Boolean = false
) : BlockMeasurementProvider {
    private val measurementCache = ConcurrentHashMap<Int, Int>()
    private val chantUnitMeasurementCache = ConcurrentHashMap<Int, Pair<Int, Int>>()

    override suspend fun measure(block: ContentBlock): Int {
        coroutineContext.ensureActive()
        val cacheKey = blockMeasurementCacheKey(block)
        measurementCache[cacheKey]?.let { return it }

        val measured = if (block is ChantScoreBlock) {
            measureChantRows(
                block.units,
                textMeasurer,
                constraints.maxWidth,
                textStyle,
                density,
                chantUnitMeasurementCache
            ).sumOf { it.heightPx }
        } else measureBlockHeight(
            block = block,
            textMeasurer = textMeasurer,
            constraints = constraints,
            defaultStyle = textStyle,
            headerStyle = textStyle.copy(fontWeight = FontWeight.Bold),
            density = density,
            imageSizeMultiplier = imageSizeMultiplier,
            hideImages = hideImages
        )
        measurementCache[cacheKey] = measured
        return measured
    }

    private fun blockMeasurementCacheKey(block: ContentBlock): Int {
        var result = block.hashCode()
        result = 31 * result + constraints.maxWidth
        result = 31 * result + constraints.maxHeight
        result = 31 * result + textStyle.hashCode()
        result = 31 * result + imageSizeMultiplier.hashCode()
        result = 31 * result + hideImages.hashCode()
        return result
    }

    /**
     * Fragments a vertically stacked list of blocks into (head, tail) under a height budget.
     *
     * Whole children are placed while they fit; the first overflowing child is fragmented in
     * place when possible (text mid-split, or recursion into nested flex/table/chant blocks).
     * Returns null when nothing fits and the first child cannot be fragmented, or when the
     * list fits entirely (no fragmentation needed).
     *
     * [contentConstraints] must be the measuring constraints of the container's CONTENT area
     * (container width minus its own horizontal padding/borders/margins) so children are
     * measured and line-split at exactly the width they render.
     */
    private suspend fun splitVerticalContentList(
        children: List<ContentBlock>,
        availableContentHeight: Int,
        contentConstraints: Constraints
    ): Pair<List<ContentBlock>, List<ContentBlock>>? {
        coroutineContext.ensureActive()
        if (children.isEmpty() || availableContentHeight <= 0) return null

        val contentHeights = ArrayList<Int>(children.size)
        val collapsedGaps = ArrayList<Int>(children.size)
        val trailingBottomMargins = ArrayList<Int>(children.size)
        var previousBottomMarginPx: Float? = null
        for (child in children) {
            contentHeights += measureChildForSplit(child, contentConstraints)
            val gap = with(density) {
                collapsedVerticalMarginPxForPagination(previousBottomMarginPx, child.style.margin.top.toPx())
            }
            collapsedGaps += gap
            trailingBottomMargins += with(density) { child.style.margin.bottom.toPx().coerceAtLeast(0f).roundToInt() }
            previousBottomMarginPx = with(density) { child.style.margin.bottom.toPx() }
        }

        val fragmentResults = arrayOfNulls<Pair<ContentBlock, List<ContentBlock>>>(children.size)
        val plan = planPaginationStackFragmentation(
            contentHeightsPx = contentHeights,
            collapsedGapsPx = collapsedGaps,
            trailingBottomMarginsPx = trailingBottomMargins,
            availableHeightPx = availableContentHeight,
            childCanFragment = { index -> isVerticallyFragmentable(children[index]) },
            childFragmentHeadFits = { index, leftoverPx ->
                val fragments = midSplitVerticalChild(children[index], leftoverPx, contentConstraints)
                if (fragments != null) {
                    fragmentResults[index] = fragments
                    true
                } else {
                    false
                }
            }
        )

        return when (plan) {
            PaginationStackFragmentationPlan.FitsEntirely,
            PaginationStackFragmentationPlan.NothingFits -> null
            is PaginationStackFragmentationPlan.Fragmented -> {
                val headCount = plan.headCount
                val splitChildIndex = plan.splitChildIndex
                val head = ArrayList<ContentBlock>(children.take(headCount))
                val tail = mutableListOf<ContentBlock>()
                if (splitChildIndex != null) {
                    val fragments = fragmentResults[splitChildIndex] ?: return null
                    head += fragments.first
                    tail += fragments.second
                    tail += children.drop(splitChildIndex + 1)
                } else {
                    tail += children.drop(headCount)
                }
                if (head.isEmpty() || tail.isEmpty()) null else head to tail
            }
        }
    }

    /**
     * Measures a child at its container's content constraints. Unlike [measure], this cannot
     * use the provider-wide cache because the width differs per container.
     */
    private suspend fun measureChildForSplit(child: ContentBlock, contentConstraints: Constraints): Int {
        return measureBlockHeight(
            block = child,
            textMeasurer = textMeasurer,
            constraints = contentConstraints,
            defaultStyle = textStyle,
            headerStyle = textStyle.copy(fontWeight = FontWeight.Bold),
            density = density,
            imageSizeMultiplier = imageSizeMultiplier,
            hideImages = hideImages
        )
    }

    private fun isVerticallyFragmentable(child: ContentBlock): Boolean = when (child) {
        is TextContentBlock -> true
        is FlexContainerBlock -> true
        is TableBlock -> true
        is ChantScoreBlock -> true
        else -> false
    }

    /**
     * Attempts an in-place fragmentation of a single child against leftover space.
     *
     * break-inside:avoid hints are honored unless the child alone is taller than a full page,
     * in which case fragmenting is the only readable placement.
     */
    private suspend fun midSplitVerticalChild(
        child: ContentBlock,
        availableHeightPx: Int,
        contentConstraints: Constraints
    ): Pair<ContentBlock, List<ContentBlock>>? {
        coroutineContext.ensureActive()
        if (availableHeightPx <= MinVerticalFragmentSpacePx) return null
        if (child.style.avoidsReaderPageBreakInside() &&
            measureChildForSplit(child, contentConstraints) <= this.constraints.maxHeight
        ) {
            return null
        }
        return when (child) {
            is TextContentBlock -> {
                // Mirror the measurement width exactly: list items are measured with their
                // marker area reserved, so their line breaks must be chosen with the same
                // narrowed width or fragments re-wrap taller than budgeted.
                val textConstraints = if (child is ListItemBlock) {
                    val markerWidthPx = with(density) { 32.dp.toPx() }.toInt()
                    contentConstraints.copy(
                        maxWidth = (contentConstraints.maxWidth - markerWidthPx).coerceAtLeast(0)
                    )
                } else {
                    contentConstraints
                }
                splitTextContentBlock(
                    block = child,
                    textMeasurer = textMeasurer,
                    constraints = textConstraints,
                    textStyle = if (child is HeaderBlock) textStyle.copy(fontWeight = FontWeight.Bold) else textStyle,
                    availableHeight = availableHeightPx,
                    density = density
                )?.let { it.first to listOf(it.second) }
            }
            is FlexContainerBlock -> splitFlexContainerAt(contentConstraints, child, availableHeightPx)
                ?.let { it.first to listOf(it.second) }
            is TableBlock -> splitTableAt(contentConstraints, child, availableHeightPx)
                ?.let { it.first to listOf(it.second) }
            is ChantScoreBlock -> split(child, availableHeightPx)?.let { it.first to listOf(it.second) }
            else -> null
        }
    }

    override suspend fun split(block: ParagraphBlock, availableHeight: Int): Pair<ParagraphBlock, ParagraphBlock>? {
        coroutineContext.ensureActive()
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
        coroutineContext.ensureActive()

        val imageBlock = block.floatedImage
        val (imageWidthPx, imageHeightPx) = if (hideImages) {
            0f to 0f
        } else run {
            measureScaledImageSizePx(
                block = imageBlock,
                density = density,
                maxWidthPx = constraints.maxWidth.toFloat(),
                imageSizeMultiplier = imageSizeMultiplier
            )
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
            coroutineContext.ensureActive()
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
                textMeasurer.measure(
                    remainingText.withoutForegroundColorSpans(),
                    style = styleForMeasure,
                    constraints = lineConstraints
                )
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
            coroutineContext.ensureActive()
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
        return splitTableAt(contentConstraints = constraints, block = block, availableHeight = availableHeight)
    }

    /**
     * Row-walking table fragmentation against [contentConstraints], the width budget the table
     * lives in. Nested tables (a table inside a callout box) must derive cell widths from the
     * containing box's content area, not from the full page, or fragments render taller than
     * they were measured.
     */
    private suspend fun splitTableAt(
        contentConstraints: Constraints,
        block: TableBlock,
        availableHeight: Int
    ): Pair<TableBlock, TableBlock>? {
        coroutineContext.ensureActive()
        var currentHeight = 0
        var splitRowIndex = -1

        val decorationTop = with(density) {
            block.style.padding.top.toPx() + (block.style.borderTop?.width?.toPx() ?: 0f)
        }.roundToInt()

        val decorationBottom = with(density) {
            block.style.padding.bottom.toPx() + (block.style.borderBottom?.width?.toPx() ?: 0f)
        }.roundToInt()

        if (DEBUG_PAGINATION_LOGS) {
            Timber.tag("PAGINATION_DEBUG").d("SplitTable: avail=$availableHeight, topDec=$decorationTop, botDec=$decorationBottom")
        }
        currentHeight += decorationTop

        val stackRows = block.shouldStackRowsForNarrowPagination()
        val rowsForSplit = if (stackRows) block.rowsForNarrowPaginationLayout() else block.rows
        for (i in rowsForSplit.indices) {
            coroutineContext.ensureActive()
            val rowHeight = measureTableRowHeight(
                row = rowsForSplit[i],
                textMeasurer = textMeasurer,
                constraints = contentConstraints,
                defaultStyle = textStyle,
                headerStyle = textStyle.copy(fontWeight = FontWeight.Bold),
                density = density,
                imageSizeMultiplier = imageSizeMultiplier,
                stackCellsVertically = stackRows
            )

            if (currentHeight + rowHeight + decorationBottom > availableHeight) {
                if (DEBUG_PAGINATION_LOGS) {
                    Timber.tag("PAGINATION_DEBUG").d("SplitTable: Breaking at row $i. currentH=$currentHeight, rowH=$rowHeight stacked=$stackRows")
                }
                val availableForRow = (availableHeight - currentHeight - decorationBottom).coerceAtLeast(0)
                if (stackRows) {
                    if (availableForRow > 0) {
                        splitStackedTableRow(
                            row = rowsForSplit[i],
                            availableHeight = availableForRow,
                            textMeasurer = textMeasurer,
                            constraints = contentConstraints,
                            defaultStyle = textStyle,
                            headerStyle = textStyle.copy(fontWeight = FontWeight.Bold),
                            density = density,
                            imageSizeMultiplier = imageSizeMultiplier,
                            hideImages = hideImages
                        )?.let { (part1Row, part2Row) ->
                            logAndroidEpubCutoff(
                                "cutoff_probe layer=android_stacked_table_split_success block=${block.blockIndex} " +
                                    "rowIndex=$i rowsBefore=${rowsForSplit.take(i).size} rowsAfter=${rowsForSplit.size - i - 1} " +
                                    "availableHeightPx=$availableForRow " +
                                    "rowHeightPx=$rowHeight textChars=${rowsForSplit[i].sumOf { cell -> cell.content.sumOf { it.paginationTextCharCount() } }}"
                            )
                            return tableSplitAtRow(block, rowsForSplit, i, part1Row, part2Row)
                        }
                    }
                    logAndroidEpubCutoff(
                        "cutoff_probe layer=android_stacked_table_split_miss block=${block.blockIndex} " +
                            "rowIndex=$i rowsBefore=${rowsForSplit.take(i).size} availableHeightPx=$availableForRow " +
                            "rowHeightPx=$rowHeight rowCells=${rowsForSplit[i].size} " +
                            "rowTextChars=${rowsForSplit[i].sumOf { cell -> cell.content.sumOf { it.paginationTextCharCount() } }}"
                    )
                } else if (availableForRow > 0) {
                    // Fragment the overflowing row itself so layout tables (publisher callout
                    // boxes with a single tall cell) span pages instead of overflowing them.
                    splitTableRowCells(row = rowsForSplit[i], availableHeight = availableForRow, contentConstraints = contentConstraints)?.let { (part1Row, part2Row) ->
                        logAndroidEpubCutoff(
                            "cutoff_probe layer=android_table_row_fragmentation_success block=${block.blockIndex} " +
                                "rowIndex=$i availableForRowPx=$availableForRow rowCells=${part1Row.size}"
                        )
                        return tableSplitAtRow(block, rowsForSplit, i, part1Row, part2Row)
                    }
                    logAndroidEpubCutoff(
                        "cutoff_probe layer=android_table_row_fragmentation_miss block=${block.blockIndex} " +
                            "rowIndex=$i availableForRowPx=$availableForRow rowHeightPx=$rowHeight rowCells=${rowsForSplit[i].size}"
                    )
                }
                splitRowIndex = i
                break
            }
            currentHeight += rowHeight
        }

        if (splitRowIndex <= 0) return null

        val part1Rows = rowsForSplit.subList(0, splitRowIndex)
        val part2Rows = rowsForSplit.subList(splitRowIndex, rowsForSplit.size)

        val part1 = block.copy(rows = part1Rows, style = block.style.copy(margin = block.style.margin.copy(bottom = 0.dp)))
        val part2 = block.copy(rows = part2Rows, style = block.style.copy(margin = block.style.margin.copy(top = 0.dp)))

        return part1 to part2
    }

    private fun tableSplitAtRow(
        block: TableBlock,
        rowsForSplit: List<List<TableCell>>,
        rowIndex: Int,
        headRowFragments: List<TableCell>,
        tailRowFragments: List<TableCell>
    ): Pair<TableBlock, TableBlock> {
        val part1 = block.copy(
            rows = rowsForSplit.take(rowIndex) + listOf(headRowFragments),
            style = block.style.copy(margin = block.style.margin.copy(bottom = 0.dp))
        )
        val part2 = block.copy(
            rows = listOf(tailRowFragments) + rowsForSplit.drop(rowIndex + 1),
            style = block.style.copy(margin = block.style.margin.copy(top = 0.dp))
        )
        return part1 to part2
    }

    /**
     * Fragments a single overflowing table row by splitting every cell's content stack at the
     * same vertical budget. Cells fragment independently but share the budget, so the row's
     * column alignment is preserved across the page break.
     */
    private suspend fun splitTableRowCells(
        row: List<TableCell>,
        availableHeight: Int,
        contentConstraints: Constraints
    ): Pair<List<TableCell>, List<TableCell>>? {
        coroutineContext.ensureActive()
        if (row.isEmpty() || availableHeight <= 0) return null
        val totalColspan = row.sumOf { it.colspan }.toFloat().coerceAtLeast(1f)
        var anyHeadContent = false
        var anyTailContent = false
        val heads = mutableListOf<TableCell>()
        val tails = mutableListOf<TableCell>()
        for (cell in row) {
            coroutineContext.ensureActive()
            val cellBlockStyle = cell.style.blockStyle
            val cellMaxWidth = when {
                cellBlockStyle.width.isSpecified -> with(density) { cellBlockStyle.width.toPx().roundToInt() }
                else -> (contentConstraints.maxWidth * (cell.colspan.toFloat() / totalColspan)).roundToInt()
            }
            val cellConstraints = contentConstraints.copy(maxWidth = cellMaxWidth.coerceAtLeast(0))
            val cellDecorationHeight = tableCellVerticalDecorationHeightPx(
                cell = cell,
                density = density,
                stackCellsVertically = false
            )
            val outcome = splitVerticalContentList(
                children = cell.content,
                availableContentHeight = (availableHeight - cellDecorationHeight).coerceAtLeast(0),
                contentConstraints = cellConstraints
            ) ?: return null
            anyHeadContent = anyHeadContent || outcome.first.isNotEmpty()
            anyTailContent = anyTailContent || outcome.second.isNotEmpty()
            heads += cell.copy(content = outcome.first)
            tails += cell.copy(content = outcome.second)
        }
        if (!anyHeadContent || !anyTailContent) return null
        return heads to tails
    }

    override suspend fun split(block: FlexContainerBlock, availableHeight: Int): Pair<FlexContainerBlock, FlexContainerBlock>? {
        return splitFlexContainerAt(contentConstraints = constraints, block = block, availableHeight = availableHeight)
    }

    /**
     * Column-flow container fragmentation against [contentConstraints], the width budget the
     * container lives in. Nested containers (a list flex inside a callout box flex) must derive
     * their content width from the containing box's content area, not from the full page, or
     * fragments measure shorter than they render.
     */
    private suspend fun splitFlexContainerAt(
        contentConstraints: Constraints,
        block: FlexContainerBlock,
        availableHeight: Int
    ): Pair<FlexContainerBlock, FlexContainerBlock>? {
        coroutineContext.ensureActive()
        if (block.style.display == "reader-chant-flow") {
            val maxWidth = contentConstraints.maxWidth.coerceAtLeast(1)
            var rowWidth = 0
            var rowHeight = 0
            var usedHeight = 0
            var splitIndex = block.children.size
            for ((index, unit) in block.children.withIndex()) {
                val unitWidth = chantUnitEstimatedWidthPx(unit, textStyle, density).coerceAtMost(maxWidth)
                val unitHeight = measure(unit)
                if (rowWidth > 0 && rowWidth + unitWidth > maxWidth) {
                    if (usedHeight + rowHeight + unitHeight > availableHeight) {
                        splitIndex = index
                        break
                    }
                    usedHeight += rowHeight
                    rowWidth = 0
                    rowHeight = 0
                }
                rowWidth += unitWidth
                rowHeight = maxOf(rowHeight, unitHeight)
            }
            if (splitIndex <= 0 || splitIndex >= block.children.size) return null
            return block.copy(children = block.children.take(splitIndex)) to
                block.copy(children = block.children.drop(splitIndex))
        }
        if (block.style.flexDirection == "row") return null

        val boxMetrics = computeBlockBoxMetrics(block, contentConstraints, density)
        val decorationTop = with(density) {
            block.style.padding.top.toPx() + (block.style.borderTop?.width?.toPx() ?: 0f)
        }.roundToInt()

        val decorationBottom = with(density) {
            block.style.padding.bottom.toPx() + (block.style.borderBottom?.width?.toPx() ?: 0f)
        }.roundToInt()

        val availableContentHeight = (availableHeight - decorationTop - decorationBottom).coerceAtLeast(0)
        if (availableContentHeight <= 0) return null

        // Children are measured margin-free for flex pagination, so the walker sees zero gaps.
        // The walker fragments an overflowing child in place when possible, which also allows
        // a split at child index 0 (a box whose first paragraph alone exceeds the page).
        // Children must be measured and split at the container's CONTENT width so fragment
        // heights match how they render inside the box.
        val (headChildren, tailChildren) = splitVerticalContentList(
            children = block.childrenForFlexPaginationMeasurement(),
            availableContentHeight = availableContentHeight,
            contentConstraints = boxMetrics.contentConstraints
        ) ?: return null

        val part1 = block.copy(
            children = headChildren,
            style = block.style.copy(margin = block.style.margin.copy(bottom = 0.dp))
        )
        val part2 = block.copy(
            children = tailChildren,
            style = block.style.copy(margin = block.style.margin.copy(top = 0.dp))
        )
        return part1 to part2
    }

    override suspend fun split(block: ChantScoreBlock, availableHeight: Int): Pair<ChantScoreBlock, ChantScoreBlock>? {
        val rows = measureChantRows(block.units, textMeasurer, constraints.maxWidth, textStyle, density, chantUnitMeasurementCache)
        var used = 0
        var splitIndex = 0
        for (row in rows) {
            if (used + row.heightPx > availableHeight) break
            used += row.heightPx
            splitIndex = row.endExclusive
        }
        if (splitIndex <= 0 || splitIndex >= block.units.size) return null
        return block.copy(units = block.units.take(splitIndex)) to
            block.copy(units = block.units.drop(splitIndex))
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
        is ChantScoreBlock -> block.copy(style = newStyle)
        is MathBlock -> block.copy(style = newStyle)
    }
}

@Suppress("UNCHECKED_CAST")
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
suspend fun paginate(
    blocks: List<ContentBlock>,
    pageHeight: Int,
    measurementProvider: BlockMeasurementProvider,
    density: Density
): List<Page> = paginateReaderBlocks(
    blocks = blocks,
    pageHeight = pageHeight,
    measurementProvider = measurementProvider,
    density = density,
    onCutoffDiagnostic = ::logAndroidEpubCutoff,
    onPageGapDiagnostic = ::logAndroidEpubPageGapDiag
)
private suspend fun measureBlockHeight(
    block: ContentBlock,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density,
    imageSizeMultiplier: Float = 1.0f,
    hideImages: Boolean = false,
    includeCenteredTextSafetyPadding: Boolean = true
): Int {
    coroutineContext.ensureActive()
    val boxMetrics = computeBlockBoxMetrics(block, constraints, density)
    val verticalPaddingPx = boxMetrics.verticalPaddingPx
    val verticalBorderPx = boxMetrics.verticalBorderPx
    val adjustedConstraints = boxMetrics.contentConstraints

    val contentHeight = when (block) {
        is ParagraphBlock -> {
            val paragraphStyle = defaultStyle.copy(textAlign = block.textAlign ?: defaultStyle.textAlign)
            val height = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content.withoutForegroundColorSpans(),
                    style = paragraphStyle,
                    constraints = adjustedConstraints
                ).paginationMeasuredHeightPx()
            }
            height + centeredTextSafetyPaddingPx(paragraphStyle, density, includeCenteredTextSafetyPadding)
        }
        is HeaderBlock -> {
            val style = headerStyle.copy(
                textAlign = block.textAlign ?: headerStyle.textAlign
            )
            val height = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content.withoutForegroundColorSpans(),
                    style = style,
                    constraints = adjustedConstraints
                ).paginationMeasuredHeightPx()
            }
            height + centeredTextSafetyPaddingPx(style, density, includeCenteredTextSafetyPadding)
        }
        is ImageBlock -> {
            if (hideImages) {
                0
            } else {
                val measuredHeight = measureScaledImageHeightPx(
                    block = block,
                    density = density,
                    contentMaxWidth = adjustedConstraints.maxWidth.toFloat(),
                    imageSizeMultiplier = imageSizeMultiplier
                ) ?: with(density) { 250.dp.toPx() }

                val finalHeight = measuredHeight.coerceAtMost(constraints.maxHeight.toFloat()).roundToInt()
                if (DEBUG_PAGINATION_LOGS) {
                    Timber.tag("IMAGE_DIAG").d("Measured Image [#${block.blockIndex}]: $finalHeight px (Capped at ${constraints.maxHeight})")
                }
                finalHeight
            }
        }
        is SpacerBlock -> {
            val height = with(density) { block.height.toPx().roundToInt() }
            height
        }
        is QuoteBlock -> {
            val quoteStyle = defaultStyle.copy(textAlign = block.textAlign ?: defaultStyle.textAlign)
            val height = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content.withoutForegroundColorSpans(),
                    style = quoteStyle,
                    constraints = adjustedConstraints
                ).paginationMeasuredHeightPx()
            }
            height + centeredTextSafetyPaddingPx(quoteStyle, density, includeCenteredTextSafetyPadding)
        }
        is ListItemBlock -> {
            val markerWidthPx = with(density) { 32.dp.toPx() }.toInt()
            val textConstraints = adjustedConstraints.copy(
                maxWidth = (adjustedConstraints.maxWidth - markerWidthPx).coerceAtLeast(0)
            )
            val textContentHeight = withContext(Dispatchers.Main) {
                textMeasurer.measure(
                    text = block.content.withoutForegroundColorSpans(),
                    style = defaultStyle,
                    constraints = textConstraints
                ).paginationMeasuredHeightPx()
            }
            val markerImageHeight = if (block.itemMarkerImage != null) {
                with(density) { (defaultStyle.fontSize.value * 0.8f).sp.toPx().roundToInt() }
            } else {
                0
            }
            val height = maxOf(textContentHeight, markerImageHeight)
            height
        }
        is TableBlock -> {
            val stackRows = block.shouldStackRowsForNarrowPagination()
            val rowsForMeasure = if (stackRows) block.rowsForNarrowPaginationLayout() else block.rows
            rowsForMeasure.sumOf { row ->
                measureTableRowHeight(
                    row = row,
                    textMeasurer = textMeasurer,
                    constraints = adjustedConstraints,
                    defaultStyle = defaultStyle,
                    headerStyle = headerStyle,
                    density = density,
                    imageSizeMultiplier = imageSizeMultiplier,
                    hideImages = hideImages,
                    stackCellsVertically = stackRows
                )
            }
        }
        is WrappingContentBlock -> {
            val imageBlock = block.floatedImage

            val (imageWidthPx, imageHeightPx) = if (hideImages) {
                0f to 0f
            } else run {
                measureScaledImageSizePx(
                    block = imageBlock,
                    density = density,
                    maxWidthPx = adjustedConstraints.maxWidth.toFloat(),
                    imageSizeMultiplier = imageSizeMultiplier
                )
            }

            // If image has no size, it can't float. Just measure the paragraphs.
            if (imageWidthPx <= 0 || imageHeightPx <= 0) {
                val height = block.paragraphsToWrap.sumOf { p ->
                    measureBlockHeight(p, textMeasurer, adjustedConstraints, defaultStyle, headerStyle, density, imageSizeMultiplier, hideImages)
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
                coroutineContext.ensureActive()
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
                    textMeasurer.measure(
                        remainingText.withoutForegroundColorSpans(),
                        style = styleForMeasure,
                        constraints = lineConstraints
                    )
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

                while (textOffset < fullText.length && fullText[textOffset].isWhitespace()) {
                    textOffset++
                }
            }

            val height = maxOf(currentY, imageHeightPx).roundToInt()
            height
        }
        is FlexContainerBlock -> {
            val isRow = block.style.flexDirection == "row"
            val childrenForMeasure = block.childrenForFlexPaginationMeasurement()
            val height = if (block.style.display == "reader-chant-flow") {
                var totalHeight = 0
                var rowWidth = 0
                var rowHeight = 0
                childrenForMeasure.forEach { child ->
                    val childWidth = chantUnitEstimatedWidthPx(child, defaultStyle, density)
                        .coerceAtMost(adjustedConstraints.maxWidth)
                    val childHeight = measureBlockHeight(child, textMeasurer, adjustedConstraints, defaultStyle, headerStyle, density, imageSizeMultiplier, hideImages)
                    if (rowWidth > 0 && rowWidth + childWidth > adjustedConstraints.maxWidth) {
                        totalHeight += rowHeight
                        rowWidth = 0
                        rowHeight = 0
                    }
                    rowWidth += childWidth
                    rowHeight = maxOf(rowHeight, childHeight)
                }
                totalHeight + rowHeight
            } else if (isRow) {
                childrenForMeasure.maxOfOrNull { child ->
                    measureBlockHeight(child, textMeasurer, adjustedConstraints, defaultStyle, headerStyle, density, imageSizeMultiplier, hideImages)
                } ?: 0
            } else {
                childrenForMeasure.sumOf { child ->
                    measureBlockHeight(child, textMeasurer, adjustedConstraints, defaultStyle, headerStyle, density, imageSizeMultiplier, hideImages)
                }
            }
            height
        }
        is ChantScoreBlock -> measureChantRows(
            block.units,
            textMeasurer,
            adjustedConstraints.maxWidth,
            defaultStyle,
            density
        ).sumOf { it.heightPx }
        is MathBlock -> {
            val fontSizePx = with(density) { defaultStyle.fontSize.toPx() }
            val containerWidthPx = adjustedConstraints.maxWidth

            val widthPx = parseSvgDimension(block.svgWidth, fontSizePx, containerWidthPx, density)
            val heightPx = parseSvgDimension(block.svgHeight, fontSizePx, containerWidthPx, density)

            val finalHeight = if (heightPx != null) {
                heightPx.roundToInt()
            } else {
                val viewBoxParts = block.svgViewBox?.split(' ', ',')?.mapNotNull { it.toFloatOrNull() }
                if (viewBoxParts != null && viewBoxParts.size == 4 && viewBoxParts[2] > 0) {
                    val aspectRatio = viewBoxParts[3] / viewBoxParts[2]

                    val effectiveWidth = widthPx ?: containerWidthPx.toFloat()
                    (effectiveWidth * aspectRatio).roundToInt()
                } else {
                    with(density) { (defaultStyle.fontSize.value * 3).sp.toPx().roundToInt() }
                }
            }
            finalHeight
        }
    }
    val specifiedHeightDp = block.style.height
    var finalHeight = if (block.style.boxSizing == "border-box" && specifiedHeightDp != Dp.Unspecified) {
        with(density) { specifiedHeightDp.toPx().roundToInt() }
    } else {
        (contentHeight + verticalPaddingPx + verticalBorderPx).roundToInt()
    }
    with(density) {
        if (block.style.minHeight.isSpecified) {
            finalHeight = finalHeight.coerceAtLeast(block.style.minHeight.toPx().roundToInt())
        }
        if (block.style.maxHeight.isSpecified && block.style.overflow in setOf("hidden", "clip", "scroll", "auto")) {
            finalHeight = finalHeight.coerceAtMost(block.style.maxHeight.toPx().roundToInt())
        }
    }

    if (DEBUG_PAGINATION_LOGS) {
        Timber.tag("PAGINATION_DEBUG").v("Measure result for ${block::class.simpleName}: content=$contentHeight, paddingV=$verticalPaddingPx, borderV=$verticalBorderPx, total=$finalHeight")
    }
    return finalHeight
}

private fun chantUnitEstimatedWidthPx(block: ContentBlock, textStyle: TextStyle, density: Density): Int {
    val container = block as? FlexContainerBlock
    if (container?.style?.display == "reader-chant-nonbreaking") {
        return container.children.sumOf { chantUnitEstimatedWidthPx(it, textStyle, density) }
    }
    val textRows = container?.children.orEmpty().filterIsInstance<TextContentBlock>()
    val longest = textRows.maxOfOrNull { row ->
        val fontScale = if (row.content.spanStyles.any { it.item.fontSize.isSpecified }) 1.75f else 1f
        row.content.text.codePointCount(0, row.content.length) * fontScale
    } ?: 1f
    val fontPx = with(density) { textStyle.fontSize.toPx() }
    return (longest * fontPx * 0.68f + with(density) { 6.dp.toPx() }).roundToInt().coerceAtLeast(1)
}

private data class MeasuredChantRow(val endExclusive: Int, val heightPx: Int)

private suspend fun measureChantRows(
    units: List<ChantUnitBlock>,
    textMeasurer: TextMeasurer,
    maxWidth: Int,
    textStyle: TextStyle,
    density: Density,
    unitCache: MutableMap<Int, Pair<Int, Int>>? = null
): List<MeasuredChantRow> = withContext(Dispatchers.Main) {
    if (units.isEmpty()) return@withContext emptyList()
    val gap = with(density) { 4.dp.toPx() }.roundToInt()
    val maxTextWidth = maxWidth.coerceAtLeast(1)
    val measured = units.map { unit ->
        val key = 31 * unit.hashCode() + maxTextWidth
        unitCache?.get(key) ?: run {
            val neume = if (unit.isDropCap) null else textMeasurer.measure(unit.neume.withoutForegroundColorSpans(), style = textStyle, constraints = Constraints(maxWidth = maxTextWidth))
            val lyric = textMeasurer.measure(unit.lyric.withoutForegroundColorSpans(), style = textStyle, constraints = Constraints(maxWidth = maxTextWidth))
            val size = maxOf(neume?.size?.width ?: 0, lyric.size.width).coerceAtLeast(1) + gap to
                ((neume?.size?.height ?: 0) + lyric.size.height + gap)
            unitCache?.set(key, size)
            size
        }
    }
    val rows = mutableListOf<MeasuredChantRow>()
    var rowWidth = 0
    var rowHeight = 0
    var index = 0
    while (index < units.size) {
        var groupEnd = index + 1
        while (groupEnd < units.size && units[groupEnd - 1].keepWithNext) groupEnd++
        val groupWidth = (index until groupEnd).sumOf { measured[it].first }
        val groupHeight = (index until groupEnd).maxOf { measured[it].second }
        if (rowWidth > 0 && rowWidth + groupWidth > maxTextWidth) {
            rows += MeasuredChantRow(index, rowHeight)
            rowWidth = 0
            rowHeight = 0
        }
        rowWidth += groupWidth
        rowHeight = maxOf(rowHeight, groupHeight)
        index = groupEnd
    }
    rows += MeasuredChantRow(units.size, rowHeight)
    rows
}

private suspend fun measureTableRowHeight(
    row: List<TableCell>,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false,
    stackCellsVertically: Boolean
): Int {
    coroutineContext.ensureActive()
    var rowHeight = 0
    val totalColspan = row.sumOf { it.colspan }.toFloat().coerceAtLeast(1f)

    for (cell in row) {
        coroutineContext.ensureActive()
        val cellBlockStyle = cell.style.blockStyle
        val cellMaxWidth = when {
            stackCellsVertically -> constraints.maxWidth
            cellBlockStyle.width.isSpecified -> with(density) { cellBlockStyle.width.toPx().roundToInt() }
            else -> (constraints.maxWidth * (cell.colspan.toFloat() / totalColspan)).roundToInt()
        }
        val cellConstraints = constraints.copy(maxWidth = cellMaxWidth.coerceAtLeast(0))
        val cellContent = if (stackCellsVertically) {
            cell.contentForStackedPaginationMeasurement()
        } else {
            cell.content
        }
        val cellDefaultStyle = if (stackCellsVertically) defaultStyle.copy(textAlign = TextAlign.Left) else defaultStyle
        val cellHeaderStyle = if (stackCellsVertically) headerStyle.copy(textAlign = TextAlign.Left) else headerStyle
        val cellContentHeight = if (stackCellsVertically) {
            measureStackedTableCellContentHeight(
                children = cellContent,
                textMeasurer = textMeasurer,
                constraints = cellConstraints,
                defaultStyle = cellDefaultStyle,
                headerStyle = cellHeaderStyle,
                density = density,
                imageSizeMultiplier = imageSizeMultiplier,
                hideImages = hideImages
            )
        } else {
            calculateContentHeightWithMargins(
                cellContent,
                textMeasurer,
                cellConstraints,
                cellDefaultStyle,
                cellHeaderStyle,
                density,
                imageSizeMultiplier,
                hideImages
            )
        }

        val cellDecorationHeight = tableCellVerticalDecorationHeightPx(
            cell = cell,
            density = density,
            stackCellsVertically = stackCellsVertically
        )
        val cellHeight = cellContentHeight + cellDecorationHeight
        rowHeight = if (stackCellsVertically) rowHeight + cellHeight else maxOf(rowHeight, cellHeight)
    }

    return rowHeight
}
private suspend fun measureStackedTableCellContentHeight(
    children: List<ContentBlock>,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false
): Int {
    var totalHeight = 0
    for ((index, child) in children.withIndex()) {
        coroutineContext.ensureActive()
        val childHeight = measureStackedTableCellChildHeight(
            child = child,
            textMeasurer = textMeasurer,
            constraints = constraints,
            defaultStyle = defaultStyle,
            headerStyle = headerStyle,
            density = density,
            imageSizeMultiplier = imageSizeMultiplier,
            hideImages = hideImages
        )
        val margin = with(density) {
            collapsedVerticalMarginPxForPagination(
                previousBottomMarginPx = children.getOrNull(index - 1)?.style?.margin?.bottom?.toPx(),
                currentTopMarginPx = child.style.margin.top.toPx()
            )
        }
        totalHeight += childHeight + margin
    }
    if (children.isNotEmpty()) {
        totalHeight += with(density) { children.last().style.margin.bottom.toPx().coerceAtLeast(0f).roundToInt() }
    }
    return totalHeight
}

private suspend fun measureStackedTableCellChildHeight(
    child: ContentBlock,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false
): Int {
    return when (child) {
        is HeaderBlock -> measureStackedTableCellTextHeight(child.content, textMeasurer, constraints, headerStyle)
        is TextContentBlock -> measureStackedTableCellTextHeight(child.content, textMeasurer, constraints, defaultStyle)
        else -> measureBlockHeight(
            block = child,
            textMeasurer = textMeasurer,
            constraints = constraints,
            defaultStyle = defaultStyle,
            headerStyle = headerStyle,
            density = density,
            imageSizeMultiplier = imageSizeMultiplier,
            hideImages = hideImages,
            includeCenteredTextSafetyPadding = false
        )
    }
}

private suspend fun measureStackedTableCellTextHeight(
    text: AnnotatedString,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    style: TextStyle
): Int {
    return withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = text.withStackedPaginationTextStartAlignment().withoutForegroundColorSpans(),
            style = style.copy(textAlign = TextAlign.Left),
            constraints = constraints
        ).paginationMeasuredHeightPx()
    }
}
private fun tableCellVerticalDecorationHeightPx(
    cell: TableCell,
    density: Density,
    stackCellsVertically: Boolean
): Int {
    val cellBlockStyle = cell.style.blockStyle
    return with(density) {
        val paddingHeight = if (stackCellsVertically) {
            cellBlockStyle.padding.top.toPx().coerceAtLeast(0f)
        } else {
            cellBlockStyle.padding.top.toPx() + cellBlockStyle.padding.bottom.toPx()
        }
        paddingHeight +
            (cellBlockStyle.borderTop?.width?.toPx() ?: 0f) +
            (cellBlockStyle.borderBottom?.width?.toPx() ?: 0f)
    }.roundToInt()
}
private suspend fun splitStackedTableRow(
    row: List<TableCell>,
    availableHeight: Int,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false
): Pair<List<TableCell>, List<TableCell>>? {
    coroutineContext.ensureActive()
    if (row.size != 1 || availableHeight <= 0) return null
    val cell = row.single()
    val cellDecorationHeight = tableCellVerticalDecorationHeightPx(
        cell = cell,
        density = density,
        stackCellsVertically = true
    )
    val contentAvailableHeight = (availableHeight - cellDecorationHeight).coerceAtLeast(0)
    if (contentAvailableHeight <= 0) return null

    val part1Content = mutableListOf<ContentBlock>()
    val part2Content = mutableListOf<ContentBlock>()
    var consumedHeight = 0
    var splitOccurred = false
    val cellConstraints = constraints.copy(maxWidth = constraints.maxWidth.coerceAtLeast(0))

    val measurableContent = cell.contentForStackedPaginationMeasurement()
    val cellDefaultStyle = defaultStyle.copy(textAlign = TextAlign.Left)
    val cellHeaderStyle = headerStyle.copy(textAlign = TextAlign.Left)
    for ((index, child) in measurableContent.withIndex()) {
        coroutineContext.ensureActive()
        if (splitOccurred) {
            part2Content.add(child)
            continue
        }

        val childHeight = measureBlockHeight(
            block = child,
            textMeasurer = textMeasurer,
            constraints = cellConstraints,
            defaultStyle = cellDefaultStyle,
            headerStyle = cellHeaderStyle,
            density = density,
            imageSizeMultiplier = imageSizeMultiplier,
            hideImages = hideImages,
            includeCenteredTextSafetyPadding = false
        )

        if (consumedHeight + childHeight <= contentAvailableHeight) {
            part1Content.add(child)
            consumedHeight += childHeight
            continue
        }

        val remainingForChild = (contentAvailableHeight - consumedHeight).coerceAtLeast(0)
        if (remainingForChild > 0 && child is TextContentBlock) {
            splitTextContentBlock(
                block = child,
                textMeasurer = textMeasurer,
                constraints = cellConstraints,
                textStyle = if (child is HeaderBlock) cellHeaderStyle else cellDefaultStyle,
                availableHeight = remainingForChild,
                density = density
            )?.let { (part1, part2) ->
                part1Content.add(part1)
                part2Content.add(part2)
                part2Content.addAll(measurableContent.drop(index + 1))
                return listOf(cell.copy(content = part1Content)) to listOf(cell.withoutStackedDramaCellTopGap().copy(content = part2Content))
            }
        }

        if (part1Content.isEmpty()) return null
        part2Content.add(child)
        part2Content.addAll(measurableContent.drop(index + 1))
        splitOccurred = true
    }

    if (!splitOccurred || part1Content.isEmpty() || part2Content.isEmpty()) return null
    return listOf(cell.copy(content = part1Content)) to listOf(cell.withoutStackedDramaCellTopGap().copy(content = part2Content))
}
private suspend fun splitTextContentBlock(
    block: TextContentBlock,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    textStyle: TextStyle,
    availableHeight: Int,
    density: Density
): Pair<ContentBlock, ContentBlock>? {
    val paragraph = ParagraphBlock(
        content = block.content,
        textAlign = block.textAlignForPaginationSplit(),
        style = block.style,
        elementId = block.elementId,
        cfi = block.cfi,
        startCharOffsetInSource = block.startCharOffsetInSource,
        endCharOffsetInSource = block.endCharOffsetInSource,
        blockIndex = block.blockIndex,
        expectedHeight = block.expectedHeight
    )
    val split = splitParagraphBlock(
        block = paragraph,
        textMeasurer = textMeasurer,
        constraints = constraints,
        textStyle = textStyle.copy(textAlign = paragraph.textAlign ?: textStyle.textAlign),
        availableHeight = availableHeight,
        density = density
    ) ?: return null

    return block.copyFromPaginationSplit(split.first) to block.copyFromPaginationSplit(split.second)
}

private fun TextContentBlock.textAlignForPaginationSplit(): TextAlign? {
    return when (this) {
        is ParagraphBlock -> textAlign
        is HeaderBlock -> textAlign
        is QuoteBlock -> textAlign
        else -> null
    }
}

private fun TextContentBlock.copyFromPaginationSplit(part: ParagraphBlock): ContentBlock {
    return when (this) {
        is ParagraphBlock -> copy(
            content = part.content,
            style = part.style,
            startCharOffsetInSource = part.startCharOffsetInSource,
            endCharOffsetInSource = part.endCharOffsetInSource,
            expectedHeight = part.expectedHeight
        )
        is HeaderBlock -> copy(
            content = part.content,
            style = part.style,
            startCharOffsetInSource = part.startCharOffsetInSource,
            endCharOffsetInSource = part.endCharOffsetInSource,
            expectedHeight = part.expectedHeight
        )
        is QuoteBlock -> copy(
            content = part.content,
            style = part.style,
            startCharOffsetInSource = part.startCharOffsetInSource,
            endCharOffsetInSource = part.endCharOffsetInSource,
            expectedHeight = part.expectedHeight
        )
        is ListItemBlock -> copy(
            content = part.content,
            style = part.style,
            startCharOffsetInSource = part.startCharOffsetInSource,
            endCharOffsetInSource = part.endCharOffsetInSource,
            expectedHeight = part.expectedHeight
        )
        else -> part
    }
}
private suspend fun logJustifiedSplitGapIfSuspicious(
    block: ParagraphBlock,
    text: AnnotatedString,
    textMeasurer: TextMeasurer,
    paragraphStyle: TextStyle,
    paragraphConstraints: Constraints,
    layoutResult: TextLayoutResult,
    lastVisibleLine: Int,
    splitOffset: Int,
    availableTextHeight: Int
) {
    coroutineContext.ensureActive()
    val isJustified = block.textAlign == TextAlign.Justify ||
        paragraphStyle.textAlign == TextAlign.Justify ||
        text.paragraphStyles.any { it.item.textAlign == TextAlign.Justify }
    if (!isJustified || lastVisibleLine !in 0 until layoutResult.lineCount) return

    val lineStart = layoutResult.getLineStart(lastVisibleLine)
    val lineEnd = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)
    if (lineStart >= lineEnd || lineEnd > text.length) return

    val visibleRightPx = (lineStart until lineEnd)
        .asSequence()
        .filter { !text[it].isWhitespace() }
        .mapNotNull { index ->
            runCatching { layoutResult.getBoundingBox(index).right }.getOrNull()
        }
        .maxOrNull() ?: return

    val contentWidthPx = paragraphConstraints.maxWidth.takeIf { it > 0 } ?: return
    val visualGapPx = contentWidthPx - visibleRightPx
    if (visualGapPx < contentWidthPx * JustifiedSplitGapProbeMinFraction) return

    val nextWord = text.text.subSequence(splitOffset.coerceIn(0, text.length), text.length)
        .firstWordOrEmpty()
        .take(48)
    if (nextWord.isBlank()) return

    val lineText = text.text.substring(lineStart, lineEnd).trimEnd()
    val candidateLineCount = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = "$lineText $nextWord",
            style = paragraphStyle,
            constraints = paragraphConstraints
        ).lineCount
    }

    coroutineContext.ensureActive()
    logAndroidEpubCutoff(
        "cutoff_probe layer=android_justified_split_gap block=${block.blockIndex} " +
            "line=$lastVisibleLine lineOffsets=$lineStart..$lineEnd splitOffset=$splitOffset " +
            "sourceRange=${block.startCharOffsetInSource}..${block.endCharOffsetInSource} " +
            "contentWidthPx=$contentWidthPx visibleRightPx=${visibleRightPx.roundToInt()} " +
            "visualGapPx=${visualGapPx.roundToInt()} availableTextHeightPx=$availableTextHeight " +
            "nextWordChars=${nextWord.length} candidateLineCount=$candidateLineCount " +
            "note=justify_expands_spaces_so_visual_gap_may_not_be_fit_capacity"
    )
}

private suspend fun logRenderedJustifiedSplitGapIfSuspicious(
    block: ParagraphBlock,
    part1Text: AnnotatedString,
    part2Text: AnnotatedString,
    textMeasurer: TextMeasurer,
    paragraphStyle: TextStyle,
    paragraphConstraints: Constraints,
    originalLayoutResult: TextLayoutResult,
    originalLastVisibleLine: Int,
    splitOffset: Int,
    availableTextHeight: Int
) {
    coroutineContext.ensureActive()
    val isJustified = block.textAlign == TextAlign.Justify ||
        paragraphStyle.textAlign == TextAlign.Justify ||
        part1Text.paragraphStyles.any { it.item.textAlign == TextAlign.Justify }
    if (!isJustified || part1Text.isEmpty()) return

    val renderedPart1Layout = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = part1Text.withoutForegroundColorSpans(),
            style = paragraphStyle,
            constraints = paragraphConstraints
        )
    }
    coroutineContext.ensureActive()

    val renderedLastLine = renderedPart1Layout.lineCount - 1
    if (renderedLastLine < 0) return

    val renderedLineStart = renderedPart1Layout.getLineStart(renderedLastLine)
    val renderedLineEnd = renderedPart1Layout.getLineEnd(renderedLastLine, visibleEnd = true)
    if (renderedLineStart >= renderedLineEnd || renderedLineEnd > part1Text.length) return

    val visibleRightPx = (renderedLineStart until renderedLineEnd)
        .asSequence()
        .filter { !part1Text[it].isWhitespace() }
        .mapNotNull { index ->
            runCatching { renderedPart1Layout.getBoundingBox(index).right }.getOrNull()
        }
        .maxOrNull() ?: return

    val contentWidthPx = paragraphConstraints.maxWidth.takeIf { it > 0 } ?: return
    val visualGapPx = contentWidthPx - visibleRightPx
    val renderedLineText = part1Text.text.substring(renderedLineStart, renderedLineEnd).trim()
    val renderedLineWordCount = renderedLineText.split(Regex("\\s+")).count { it.isNotBlank() }
    val sparseByGap = visualGapPx >= contentWidthPx * 0.10f
    val sparseByWords = renderedLineWordCount <= 4 && visualGapPx >= contentWidthPx * 0.06f
    if (!sparseByGap && !sparseByWords) return

    val nextWord = part2Text.text.firstWordOrEmpty().take(48)
    if (nextWord.isBlank()) return

    val visualCandidateLineCount = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = "$renderedLineText $nextWord",
            style = paragraphStyle,
            constraints = paragraphConstraints
        ).lineCount
    }

    val part2LineCount = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = part2Text.withoutForegroundColorSpans(),
            style = paragraphStyle,
            constraints = paragraphConstraints
        ).lineCount
    }
    val nextWordEnd = part2Text.text.nextWordEndAfter(0)
    val remainingAfterNextWordStart = part2Text.text.skipWhitespaceFrom(nextWordEnd)
    val remainingAfterNextWordLineCount = if (remainingAfterNextWordStart < part2Text.length) {
        withContext(Dispatchers.Main) {
            textMeasurer.measure(
                text = part2Text.subSequence(remainingAfterNextWordStart, part2Text.length)
                    .withoutForegroundColorSpans(),
                style = paragraphStyle,
                constraints = paragraphConstraints
            ).lineCount
        }
    } else {
        0
    }

    val originalLineStart = if (originalLastVisibleLine in 0 until originalLayoutResult.lineCount) {
        originalLayoutResult.getLineStart(originalLastVisibleLine)
    } else {
        -1
    }
    val originalLineEnd = if (originalLastVisibleLine in 0 until originalLayoutResult.lineCount) {
        originalLayoutResult.getLineEnd(originalLastVisibleLine, visibleEnd = true)
    } else {
        -1
    }

    coroutineContext.ensureActive()
    logAndroidEpubCutoff(
        "cutoff_probe layer=android_justified_split_gap block=${block.blockIndex} " +
            "sourceRange=${block.startCharOffsetInSource}..${block.endCharOffsetInSource} " +
            "splitOffset=$splitOffset availableTextHeightPx=$availableTextHeight " +
            "renderedLines=${renderedPart1Layout.lineCount} renderedLastLine=$renderedLastLine " +
            "renderedLineOffsets=$renderedLineStart..$renderedLineEnd " +
            "renderedLineChars=${renderedLineText.length} renderedLineWords=$renderedLineWordCount " +
            "contentWidthPx=$contentWidthPx renderedVisibleRightPx=${visibleRightPx.roundToInt()} " +
            "renderedVisualGapPx=${visualGapPx.roundToInt()} nextWordChars=${nextWord.length} " +
            "visualCandidateLineCount=$visualCandidateLineCount part2Lines=$part2LineCount " +
            "remainingAfterNextWordLines=$remainingAfterNextWordLineCount " +
            "originalLastLine=$originalLastVisibleLine originalLineOffsets=$originalLineStart..$originalLineEnd " +
            "note=rendered_split_final_line_is_unjustified_so_gap_can_appear_after_pagination"
    )
}

private data class RenderedSplitCandidate(
    val splitOffset: Int,
    val prefixHeightPx: Int,
    val prefixLineCount: Int,
    val remainingLineCount: Int,
    val lastLineChars: Int,
    val lastLineWords: Int,
    val lastLineVisualGapPx: Int,
    val contentWidthPx: Int
) {
    val sparseLastLine: Boolean
        get() = lastLineVisualGapPx >= contentWidthPx * 0.20f ||
            (lastLineWords <= 4 && lastLineVisualGapPx >= contentWidthPx * 0.08f)
}

private fun isBetterRenderedJustifySplitCandidate(
    candidate: RenderedSplitCandidate,
    current: RenderedSplitCandidate
): Boolean {
    if (candidate.sparseLastLine != current.sparseLastLine) {
        return !candidate.sparseLastLine
    }
    if (candidate.sparseLastLine) {
        if (candidate.lastLineVisualGapPx != current.lastLineVisualGapPx) {
            return candidate.lastLineVisualGapPx < current.lastLineVisualGapPx
        }
        return candidate.splitOffset > current.splitOffset
    }
    if (candidate.prefixLineCount != current.prefixLineCount) {
        return candidate.prefixLineCount > current.prefixLineCount
    }
    if (candidate.lastLineVisualGapPx != current.lastLineVisualGapPx) {
        return candidate.lastLineVisualGapPx < current.lastLineVisualGapPx
    }
    return candidate.splitOffset > current.splitOffset
}

private suspend fun measureRenderedSplitCandidate(
    text: AnnotatedString,
    textMeasurer: TextMeasurer,
    paragraphStyle: TextStyle,
    paragraphConstraints: Constraints,
    splitOffset: Int
): RenderedSplitCandidate? {
    val prefixEnd = text.text.trimTrailingWhitespaceBefore(splitOffset)
    if (prefixEnd <= 0 || prefixEnd >= text.length) return null

    val remainingStart = text.text.skipWhitespaceFrom(prefixEnd)
    if (remainingStart >= text.length) return null

    val prefixLayout = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = text.subSequence(0, prefixEnd).withoutForegroundColorSpans(),
            style = paragraphStyle,
            constraints = paragraphConstraints
        )
    }
    coroutineContext.ensureActive()

    val remainingLayout = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = text.subSequence(remainingStart, text.length).withoutForegroundColorSpans(),
            style = paragraphStyle,
            constraints = paragraphConstraints
        )
    }
    coroutineContext.ensureActive()

    val lastLine = prefixLayout.lineCount - 1
    if (lastLine < 0) return null
    val lineStart = prefixLayout.getLineStart(lastLine)
    val lineEnd = prefixLayout.getLineEnd(lastLine, visibleEnd = true)
    if (lineStart >= lineEnd || lineEnd > prefixEnd) return null
    val prefixText = text.text.substring(0, prefixEnd)
    val lastLineText = prefixText.substring(lineStart, lineEnd).trim()
    val lastLineWords = lastLineText.split(Regex("\\s+")).count { it.isNotBlank() }
    val contentWidthPx = paragraphConstraints.maxWidth.takeIf { it > 0 } ?: return null
    val visibleRightPx = (lineStart until lineEnd)
        .asSequence()
        .filter { !prefixText[it].isWhitespace() }
        .mapNotNull { index ->
            runCatching { prefixLayout.getBoundingBox(index).right }.getOrNull()
        }
        .maxOrNull() ?: return null
    val lastLineVisualGapPx = (contentWidthPx - visibleRightPx).roundToInt()

    return RenderedSplitCandidate(
        splitOffset = prefixEnd,
        prefixHeightPx = prefixLayout.paginationMeasuredHeightPx(),
        prefixLineCount = prefixLayout.lineCount,
        remainingLineCount = remainingLayout.lineCount,
        lastLineChars = lastLineText.length,
        lastLineWords = lastLineWords,
        lastLineVisualGapPx = lastLineVisualGapPx,
        contentWidthPx = contentWidthPx
    )
}

private suspend fun adjustJustifiedSplitOffsetForRenderedPrefix(
    block: ParagraphBlock,
    text: AnnotatedString,
    textMeasurer: TextMeasurer,
    paragraphStyle: TextStyle,
    paragraphConstraints: Constraints,
    initialSplitOffset: Int,
    availableTextHeight: Int,
    orphanLines: Int,
    widowLines: Int
): Int? {
    coroutineContext.ensureActive()
    val isJustified = block.textAlign == TextAlign.Justify ||
        paragraphStyle.textAlign == TextAlign.Justify ||
        text.paragraphStyles.any { it.item.textAlign == TextAlign.Justify }
    if (!isJustified) return initialSplitOffset

    val normalizedInitialOffset = text.text.trimTrailingWhitespaceBefore(initialSplitOffset)
    var candidateOffset = normalizedInitialOffset
    var bestCandidate: RenderedSplitCandidate? = null

    while (candidateOffset > 0) {
        coroutineContext.ensureActive()
        val candidate = measureRenderedSplitCandidate(
            text = text,
            textMeasurer = textMeasurer,
            paragraphStyle = paragraphStyle,
            paragraphConstraints = paragraphConstraints,
            splitOffset = candidateOffset
        )
        if (candidate != null &&
            candidate.prefixHeightPx <= availableTextHeight &&
            candidate.prefixLineCount >= orphanLines &&
            candidate.remainingLineCount >= widowLines
        ) {
            bestCandidate = candidate
            break
        }
        candidateOffset = text.text.previousWordEndBefore(candidateOffset)
    }

    if (bestCandidate == null) {
        logAndroidEpubCutoff(
            "cutoff_probe layer=android_justified_split_adjust block=${block.blockIndex} " +
                "sourceRange=${block.startCharOffsetInSource}..${block.endCharOffsetInSource} " +
                "initialSplitOffset=$initialSplitOffset adjustedSplitOffset=null " +
                "availableTextHeightPx=$availableTextHeight reason=no_rendered_prefix_fit"
        )
        return null
    }

    var acceptedCandidate: RenderedSplitCandidate = bestCandidate ?: return null
    var furthestFittingCandidate: RenderedSplitCandidate = acceptedCandidate
    while (true) {
        coroutineContext.ensureActive()
        val nextOffset = text.text.nextWordEndAfter(furthestFittingCandidate.splitOffset)
        if (nextOffset <= furthestFittingCandidate.splitOffset || nextOffset >= text.length) break

        val nextCandidate = measureRenderedSplitCandidate(
            text = text,
            textMeasurer = textMeasurer,
            paragraphStyle = paragraphStyle,
            paragraphConstraints = paragraphConstraints,
            splitOffset = nextOffset
        ) ?: break

        if (nextCandidate.prefixHeightPx > availableTextHeight ||
            nextCandidate.prefixLineCount < orphanLines ||
            nextCandidate.remainingLineCount < widowLines
        ) {
            break
        }
        furthestFittingCandidate = nextCandidate
        if (isBetterRenderedJustifySplitCandidate(nextCandidate, acceptedCandidate)) {
            acceptedCandidate = nextCandidate
        }
    }

    if (acceptedCandidate.splitOffset != normalizedInitialOffset ||
        acceptedCandidate.splitOffset != furthestFittingCandidate.splitOffset
    ) {
        val reason = if (acceptedCandidate.splitOffset != furthestFittingCandidate.splitOffset) {
            "best_rendered_last_line"
        } else {
            "rendered_prefix_fit"
        }
        logAndroidEpubCutoff(
            "cutoff_probe layer=android_justified_split_adjust block=${block.blockIndex} " +
                "sourceRange=${block.startCharOffsetInSource}..${block.endCharOffsetInSource} " +
                "initialSplitOffset=$initialSplitOffset normalizedInitialOffset=$normalizedInitialOffset " +
                "adjustedSplitOffset=${acceptedCandidate.splitOffset} availableTextHeightPx=$availableTextHeight " +
                "adjustedPrefixHeightPx=${acceptedCandidate.prefixHeightPx} " +
                "adjustedPrefixLines=${acceptedCandidate.prefixLineCount} " +
                "adjustedRemainingLines=${acceptedCandidate.remainingLineCount} " +
                "adjustedLineChars=${acceptedCandidate.lastLineChars} " +
                "adjustedLineWords=${acceptedCandidate.lastLineWords} " +
                "adjustedLineGapPx=${acceptedCandidate.lastLineVisualGapPx} " +
                "furthestFitSplitOffset=${furthestFittingCandidate.splitOffset} " +
                "furthestFitLineWords=${furthestFittingCandidate.lastLineWords} " +
                "furthestFitLineGapPx=${furthestFittingCandidate.lastLineVisualGapPx} " +
                "reason=$reason"
        )
    }

    return acceptedCandidate.splitOffset
}

private suspend fun splitParagraphBlock(
    block: ParagraphBlock,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    textStyle: TextStyle,
    availableHeight: Int,
    density: Density
): Pair<ParagraphBlock, ParagraphBlock>? {
    coroutineContext.ensureActive()
    val text = block.content
    if (text.isEmpty()) return null
    val boxMetrics = computeBlockBoxMetrics(block, constraints, density)
    val paragraphConstraints = boxMetrics.contentConstraints
    val paragraphStyle = textStyle.copy(textAlign = block.textAlign ?: textStyle.textAlign)
    val centeredSafetyPaddingPx = centeredTextSafetyPaddingPx(paragraphStyle, density)

    val decorationTop = with(density) {
        block.style.padding.top.toPx() + (block.style.borderTop?.width?.toPx() ?: 0f)
    }.roundToInt()

    val decorationBottom = with(density) {
        block.style.padding.bottom.toPx() + (block.style.borderBottom?.width?.toPx() ?: 0f)
    }.roundToInt()

    val availableTextHeight = availableHeight - decorationTop - decorationBottom - centeredSafetyPaddingPx

    if (DEBUG_PAGINATION_LOGS) {
        Timber.tag("PAGINATION_DEBUG").d("SplitPara: totalAvail=$availableHeight, topDec=$decorationTop, botDec=$decorationBottom, textAvail=$availableTextHeight")
    }

    if (availableTextHeight <= 0) {
        if (DEBUG_PAGINATION_LOGS) {
            Timber.tag("PAGINATION_DEBUG").w("SplitPara aborted: availableTextHeight <= 0")
        }
        return null
    }

    val layoutResult = withContext(Dispatchers.Main) {
        textMeasurer.measure(
            text = text.withoutForegroundColorSpans(),
            style = paragraphStyle,
            constraints = paragraphConstraints
        )
    }

    coroutineContext.ensureActive()
    if (layoutResult.paginationMeasuredHeightPx() <= availableTextHeight) {
        return null
    }

    if (layoutResult.getLineBottom(0) > availableTextHeight) {
        return null
    }

    var lastVisibleLine = layoutResult.getLineForVerticalPosition(availableTextHeight.toFloat())

    if (layoutResult.getLineBottom(lastVisibleLine) > availableTextHeight.toFloat()) {
        lastVisibleLine--
    }

    if (lastVisibleLine < 0) {
        return null
    }

    val orphanLines = block.style.orphans.coerceAtLeast(1)
    val widowLines = block.style.widows.coerceAtLeast(1)
    val visibleLineCount = lastVisibleLine + 1
    if (visibleLineCount < orphanLines) {
        if (DEBUG_PAGINATION_LOGS) {
            Timber.d("Orphan control: Preventing split that would leave $visibleLineCount line(s) at the bottom of the page.")
        }
        return null
    }

    var splitOffset = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)

    val part2CheckText = text.subSequence(splitOffset, text.length)
    if (part2CheckText.isNotBlank()) {
        val part2Layout = withContext(Dispatchers.Main) {
            textMeasurer.measure(
                text = part2CheckText.withoutForegroundColorSpans(),
                style = paragraphStyle,
                constraints = paragraphConstraints
            )
        }
        coroutineContext.ensureActive()
        if (part2Layout.lineCount < widowLines) {
            if (DEBUG_PAGINATION_LOGS) {
                Timber.d("Widow control: Adjusting split to keep at least $widowLines line(s) at the top of the next page.")
            }
            val linesToMove = widowLines - part2Layout.lineCount
            lastVisibleLine -= linesToMove.coerceAtLeast(1)
            if (lastVisibleLine + 1 < orphanLines) return null
            splitOffset = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)
        }
    }

    splitOffset = adjustJustifiedSplitOffsetForRenderedPrefix(
        block = block,
        text = text,
        textMeasurer = textMeasurer,
        paragraphStyle = paragraphStyle,
        paragraphConstraints = paragraphConstraints,
        initialSplitOffset = splitOffset,
        availableTextHeight = availableTextHeight,
        orphanLines = orphanLines,
        widowLines = widowLines
    ) ?: return null

    logJustifiedSplitGapIfSuspicious(
        block = block,
        text = text,
        textMeasurer = textMeasurer,
        paragraphStyle = paragraphStyle,
        paragraphConstraints = paragraphConstraints,
        layoutResult = layoutResult,
        lastVisibleLine = lastVisibleLine,
        splitOffset = splitOffset,
        availableTextHeight = availableTextHeight
    )

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

    logRenderedJustifiedSplitGapIfSuspicious(
        block = block,
        part1Text = part1Text,
        part2Text = part2Text,
        textMeasurer = textMeasurer,
        paragraphStyle = paragraphStyle,
        paragraphConstraints = paragraphConstraints,
        originalLayoutResult = layoutResult,
        originalLastVisibleLine = lastVisibleLine,
        splitOffset = splitOffset,
        availableTextHeight = availableTextHeight
    )

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

    if (DEBUG_PAGINATION_LOGS) {
        Timber.d("Split block at offset $splitOffset. Part 1 len: ${part1.content.length}, Part 2 len: ${part2.content.length}")
    }

    return part1 to part2
}

private suspend fun calculateContentHeightWithMargins(
    children: List<ContentBlock>,
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    defaultStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density,
    imageSizeMultiplier: Float = 1.0f,
    hideImages: Boolean = false,
    includeCenteredTextSafetyPadding: Boolean = true
): Int {
    var totalHeight = 0
    for ((index, child) in children.withIndex()) {
        coroutineContext.ensureActive()
        val childHeight = measureBlockHeight(child, textMeasurer, constraints, defaultStyle, headerStyle, density, imageSizeMultiplier, hideImages, includeCenteredTextSafetyPadding)
        val margin = with(density) {
            collapsedVerticalMarginPxForPagination(
                previousBottomMarginPx = children.getOrNull(index - 1)?.style?.margin?.bottom?.toPx(),
                currentTopMarginPx = child.style.margin.top.toPx()
            )
        }
        totalHeight += (childHeight + margin)
        if (DEBUG_PAGINATION_LOGS) {
            Timber.tag("PAGINATION_DEBUG").v("  Internal Child ${child::class.simpleName}: h=$childHeight, margin=$margin, runningTotal=$totalHeight")
        }
    }
    if (children.isNotEmpty()) {
        totalHeight += with(density) { children.last().style.margin.bottom.toPx().coerceAtLeast(0f).roundToInt() }
    }
    return totalHeight
}

private data class BlockBoxMetrics(
    val verticalPaddingPx: Float,
    val verticalBorderPx: Float,
    val contentConstraints: Constraints
)

private fun computeBlockBoxMetrics(
    block: ContentBlock,
    constraints: Constraints,
    density: Density
): BlockBoxMetrics {
    val verticalPaddingPx: Float
    val horizontalPaddingPx: Float
    val verticalBorderPx: Float
    val horizontalBorderPx: Float
    val availableBlockWidthPx: Float

    with(density) {
        verticalPaddingPx = block.style.padding.top.coerceAtLeast(0.dp).toPx() + block.style.padding.bottom.coerceAtLeast(0.dp).toPx()
        horizontalPaddingPx = block.style.padding.left.coerceAtLeast(0.dp).toPx() + block.style.padding.right.coerceAtLeast(0.dp).toPx()
        verticalBorderPx = (block.style.borderTop?.width?.toPx() ?: 0f) + (block.style.borderBottom?.width?.toPx() ?: 0f)
        horizontalBorderPx = (block.style.borderLeft?.width?.toPx() ?: 0f) + (block.style.borderRight?.width?.toPx() ?: 0f)
        availableBlockWidthPx = availableBlockWidthPxForPagination(
            containerWidthPx = constraints.maxWidth,
            marginLeftPx = block.style.margin.left.toPx(),
            marginRightPx = block.style.margin.right.toPx(),
            isCenterAligned = block.style.horizontalAlign == "center"
        )
    }

    val isBorderBox = block.style.boxSizing == "border-box"
    val specifiedWidthDp = block.style.width
    val specifiedMaxWidthDp = block.style.maxWidth
    val specifiedMinWidthDp = block.style.minWidth

    val blockOuterWidthPx = with(density) {
        var effectiveWidthPx = availableBlockWidthPx
        if (specifiedWidthDp != Dp.Unspecified) {
            effectiveWidthPx = specifiedWidthDp.toPx()
        }
        if (specifiedMaxWidthDp != Dp.Unspecified) {
            val maxWidthPx = specifiedMaxWidthDp.toPx()
            if (effectiveWidthPx > maxWidthPx) {
                effectiveWidthPx = maxWidthPx
            }
        }
        if (specifiedMinWidthDp != Dp.Unspecified) {
            effectiveWidthPx = effectiveWidthPx.coerceAtLeast(specifiedMinWidthDp.toPx())
        }
        effectiveWidthPx.coerceAtMost(availableBlockWidthPx)
    }

    val contentMaxWidth = if (specifiedWidthDp == Dp.Unspecified || isBorderBox) {
        blockOuterWidthPx - horizontalPaddingPx - horizontalBorderPx
    } else {
        blockOuterWidthPx
    }

    return BlockBoxMetrics(
        verticalPaddingPx = verticalPaddingPx,
        verticalBorderPx = verticalBorderPx,
        contentConstraints = constraints.copy(
            maxWidth = contentMaxWidth.roundToInt().coerceAtLeast(0),
            maxHeight = Constraints.Infinity
        )
    )
}

private fun measureScaledImageHeightPx(
    block: ImageBlock,
    density: Density,
    contentMaxWidth: Float,
    imageSizeMultiplier: Float
): Float? = measureScaledImageSizePx(
    block = block,
    density = density,
    maxWidthPx = contentMaxWidth,
    imageSizeMultiplier = imageSizeMultiplier
).second.takeIf { it > 0f }

private fun measureScaledImageSizePx(
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
        if (block.style.width.isSpecified) block.style.width.toPx()
        else intrinsicImageWidthPx(intrinsicWidth, density, maxWidthPx)
    }

    var scaledWidth = baseWidth * imageSizeMultiplier
    if (block.style.maxWidth.isSpecified) {
        scaledWidth = scaledWidth.coerceAtMost(with(density) { block.style.maxWidth.toPx() } * imageSizeMultiplier)
    }
    scaledWidth = scaledWidth.coerceAtMost(maxWidthPx)

    return scaledWidth to (scaledWidth * aspectRatio)
}

/**
 * HTML's default image width is its intrinsic CSS-pixel width. Native readers
 * must use the same default; only an explicit CSS width should stretch an
 * image to the available column.
 */
