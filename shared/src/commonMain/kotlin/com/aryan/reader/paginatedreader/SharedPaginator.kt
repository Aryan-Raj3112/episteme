package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

suspend fun paginateReaderBlocks(
    blocks: List<ContentBlock>,
    pageHeight: Int,
    measurementProvider: BlockMeasurementProvider,
    density: Density,
    onCutoffDiagnostic: (String) -> Unit = {},
    onPageGapDiagnostic: (String) -> Unit = {}
): List<Page> {
    if (blocks.isEmpty()) return emptyList()
    val pages = mutableListOf<Page>()
    var currentPageContent = mutableListOf<ContentBlock>()
    var remainingHeight = pageHeight
    val remainingBlocks = blocks.toMutableList()
    var pageIndex = 0

    fun blockList(): String = currentPageContent.joinToString(",") {
        "${it.blockIndex}:${it::class.simpleName ?: "Block"}:${it.expectedHeight}"
    }

    fun normalizePreviousBottomMargin() {
        if (currentPageContent.isEmpty()) return
        val previous = currentPageContent.last()
        val style = previous.style.copy(margin = previous.style.margin.copy(bottom = 0.dp))
        currentPageContent[currentPageContent.lastIndex] =
            previous.withReaderBlockStyle(style).withReaderExpectedHeight(previous.expectedHeight)
    }

    suspend fun preparedSplitPart(block: ContentBlock, spaceBetweenBlocks: Int): ContentBlock {
        normalizePreviousBottomMargin()
        val collapsedMargin = with(density) { spaceBetweenBlocks.toDp() }
        val style = block.style.copy(margin = block.style.margin.copy(top = collapsedMargin))
        val styled = block.withReaderBlockStyle(style)
        return styled.withReaderExpectedHeight(measurementProvider.measure(styled) + spaceBetweenBlocks)
    }

    fun commitPage(reason: String) {
        onPageGapDiagnostic(
            "decision=commit_page reason=$reason page=${pageIndex + 1} remainingPx=$remainingHeight " +
                "blockCount=${currentPageContent.size} blocks=${blockList()}"
        )
        zeroReaderLastBottomMargin(currentPageContent)
        pages += Page(currentPageContent.toList())
        pageIndex++
        currentPageContent = mutableListOf()
        remainingHeight = pageHeight
    }

    while (remainingBlocks.isNotEmpty()) {
        coroutineContext.ensureActive()
        val block = remainingBlocks.removeAt(0)
        if (currentPageContent.isNotEmpty() && block.style.forcesReaderPageBreakBefore()) {
            remainingBlocks.add(0, block)
            commitPage("forced_break_before")
            continue
        }

        val blockHeight = measurementProvider.measure(block)
        val previousBottomMargin = currentPageContent.lastOrNull()?.let {
            with(density) { it.style.margin.bottom.toPx() }
        }
        val rawTopMargin = with(density) { block.style.margin.top.toPx() }
        val effectiveTopMargin = effectiveTopMarginPxForPagination(currentPageContent.isEmpty(), rawTopMargin)
        val spaceBetweenBlocks = collapsedVerticalMarginPxForPagination(previousBottomMargin, effectiveTopMargin)
        if ((previousBottomMargin != null && previousBottomMargin < 0f) || effectiveTopMargin < 0f) {
            onCutoffDiagnostic(
                "cutoff_probe layer=android_paginator_margin_clamp page=${pageIndex + 1} " +
                    "block=${block.blockIndex} kind=${block::class.simpleName ?: "Block"} " +
                    "prevBottomMarginPx=${previousBottomMargin ?: "none"} currentTopMarginPx=$effectiveTopMargin " +
                    "collapsedMarginPx=$spaceBetweenBlocks remainingBeforePx=$remainingHeight blockHeightPx=$blockHeight"
            )
        }
        val spaceRequired = blockHeight + spaceBetweenBlocks
        onPageGapDiagnostic(
            "decision=consider page=${pageIndex + 1} block=${block.blockIndex} " +
                "remainingBeforePx=$remainingHeight blockHeightPx=$blockHeight collapsedMarginPx=$spaceBetweenBlocks " +
                "spaceRequiredPx=$spaceRequired fits=${spaceRequired <= remainingHeight}"
        )

        if (spaceRequired <= remainingHeight) {
            normalizePreviousBottomMargin()
            val collapsedMargin = with(density) { spaceBetweenBlocks.toDp() }
            val style = block.style.copy(margin = block.style.margin.copy(top = collapsedMargin))
            val blockToAdd = block.withReaderBlockStyle(style).withReaderExpectedHeight(spaceRequired)
            currentPageContent += blockToAdd
            remainingHeight -= spaceRequired
            onPageGapDiagnostic(
                "decision=place_fit page=${pageIndex + 1} block=${block.blockIndex} " +
                    "spaceRequiredPx=$spaceRequired remainingAfterPx=$remainingHeight"
            )
            if (block.style.forcesReaderPageBreakAfter() && remainingBlocks.isNotEmpty()) {
                commitPage("forced_break_after")
            }
            continue
        }

        var wasSplit = false
        val heightForSplitting = remainingHeight - spaceBetweenBlocks
        if (heightForSplitting > 50) {
            when (block) {
                is ParagraphBlock -> if (!block.style.avoidsReaderPageBreakInside()) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        if (part1.content.isNotEmpty()) {
                            val prepared = preparedSplitPart(part1, spaceBetweenBlocks) as ParagraphBlock
                            currentPageContent += prepared
                            if (part2.content.isNotEmpty()) remainingBlocks.add(0, part2)
                            wasSplit = true
                        }
                    }
                }
                is WrappingContentBlock -> if (!block.style.avoidsReaderPageBreakInside()) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        if (part1.paragraphsToWrap.any { it.content.isNotBlank() }) {
                            currentPageContent += preparedSplitPart(part1, spaceBetweenBlocks)
                            if (part2.isNotEmpty()) remainingBlocks.addAll(0, part2)
                            wasSplit = true
                        }
                    }
                }
                is TableBlock -> if (!block.style.avoidsReaderPageBreakInside()) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        currentPageContent += preparedSplitPart(part1, spaceBetweenBlocks)
                        remainingBlocks.add(0, part2)
                        wasSplit = true
                    }
                }
                is FlexContainerBlock -> if (!block.style.avoidsReaderPageBreakInside()) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        currentPageContent += preparedSplitPart(part1, spaceBetweenBlocks)
                        remainingBlocks.add(0, part2)
                        wasSplit = true
                    }
                }
                is ChantScoreBlock -> measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                    val measured = part1.withReaderExpectedHeight(
                        measurementProvider.measure(part1) + spaceBetweenBlocks
                    )
                    currentPageContent += measured
                    remainingBlocks.add(0, part2)
                    wasSplit = true
                }
                else -> Unit
            }
        }

        if (!wasSplit) {
            if (currentPageContent.isEmpty()) {
                val forcedHeight = blockHeight + spaceBetweenBlocks
                onCutoffDiagnostic(
                    "cutoff_probe layer=android_paginator_forced_oversize_block page=${pageIndex + 1} " +
                        "block=${block.blockIndex} kind=${block::class.simpleName ?: "Block"} pageHeightPx=$pageHeight " +
                        "remainingHeightPx=$remainingHeight spaceRequiredPx=$spaceRequired forcedHeightPx=$forcedHeight " +
                        "blockHeightPx=$blockHeight marginPx=$spaceBetweenBlocks"
                )
                currentPageContent += block.withReaderExpectedHeight(forcedHeight)
            } else {
                onCutoffDiagnostic(
                    "cutoff_probe layer=android_paginator_page_gap page=${pageIndex + 1} " +
                        "nextPageBlock=${block.blockIndex} remainingHeightPx=$remainingHeight " +
                        "heightForSplittingPx=$heightForSplitting spaceRequiredPx=$spaceRequired"
                )
                remainingBlocks.add(0, block)
            }
        }
        commitPage("overflow_or_split")
    }

    if (currentPageContent.isNotEmpty()) commitPage("final")
    return pages
}
