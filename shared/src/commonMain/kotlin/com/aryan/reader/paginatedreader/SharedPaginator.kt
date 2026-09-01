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

    /**
     * Prepares a split head and accepts it only if its remeasured height (including collapsed
     * spacing) still fits the current page. Splitters can disagree with the final remeasure by
     * more than rounding on decorated containers; committing such a fragment unchecked is what
     * produced bottom-of-page overflows, so a rejected fragment falls back to the unsplit path
     * (push to next page, or force-place when the page is empty).
     */
    suspend fun verifiedSplitPart(
        block: ContentBlock,
        spaceBetweenBlocks: Int,
        heightForSplittingPx: Int
    ): ContentBlock? {
        val prepared = preparedSplitPart(block, spaceBetweenBlocks)
        if (prepared.expectedHeight <= remainingHeight) return prepared
        onCutoffDiagnostic(
            "cutoff_probe layer=android_paginator_split_fragment_rejected page=${pageIndex + 1} " +
                "block=${block.blockIndex} kind=${block::class.simpleName ?: "Block"} " +
                "fragmentExpectedPx=${prepared.expectedHeight} remainingPx=$remainingHeight " +
                "heightForSplittingPx=$heightForSplittingPx textChars=${block.paginationTextCharCount()}"
        )
        return null
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

        val previousBottomMargin = currentPageContent.lastOrNull()?.let {
            with(density) { it.style.margin.bottom.toPx() }
        }
        val rawTopMargin = with(density) { block.style.margin.top.toPx() }
        val effectiveTopMargin = effectiveTopMarginPxForPagination(currentPageContent.isEmpty(), rawTopMargin)
        val spaceBetweenBlocks = collapsedVerticalMarginPxForPagination(previousBottomMargin, effectiveTopMargin)
        val heightForSplitting = remainingHeight - spaceBetweenBlocks

        // Measuring a large vertical container before asking it to split measures every child on
        // every successive page (N + (N-k) + ...). Probe its incremental splitter first so each
        // child is visited only for the fragment where it can appear. The final fragment still
        // follows the normal full-measure path, preserving the existing fit decisions.
        if (
            block is FlexContainerBlock &&
            block.style.flexDirection != "row" &&
            !block.style.avoidsReaderPageBreakInside() &&
            heightForSplitting > 50
        ) {
            val probeSplit = measurementProvider.split(block, heightForSplitting)
            if (probeSplit != null) {
                val prepared = verifiedSplitPart(probeSplit.first, spaceBetweenBlocks, heightForSplitting)
                if (prepared != null) {
                    currentPageContent += prepared
                    remainingBlocks.add(0, probeSplit.second)
                    commitPage("incremental_vertical_container_split")
                    continue
                }
                // Rejected fragment: fall through to the full-measure path, which will either
                // split again with verified placement or push the whole block to the next page.
            }
        }

        val blockHeight = measurementProvider.measure(block)
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
        if (heightForSplitting > 50) {
            // CSS break-inside:avoid is best-effort. A block taller than a full page can never
            // be placed readably, so the avoid hint is dropped and it is fragmented like any
            // other oversized block.
            val avoidsPageBreak = block.style.avoidsReaderPageBreakInside()
            val maySplitInPlace = !avoidsPageBreak || blockHeight > pageHeight
            if (avoidsPageBreak && maySplitInPlace) {
                onCutoffDiagnostic(
                    "cutoff_probe layer=android_paginator_break_avoid_relaxed page=${pageIndex + 1} " +
                        "block=${block.blockIndex} kind=${block::class.simpleName ?: "Block"} " +
                        "blockHeightPx=$blockHeight pageHeightPx=$pageHeight"
                )
            }
            when (block) {
                is ParagraphBlock -> if (maySplitInPlace) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        if (part1.content.isNotEmpty()) {
                            val prepared = verifiedSplitPart(part1, spaceBetweenBlocks, heightForSplitting)
                            if (prepared != null) {
                                currentPageContent += prepared
                                if (part2.content.isNotEmpty()) remainingBlocks.add(0, part2)
                                wasSplit = true
                            }
                        }
                    }
                }
                is WrappingContentBlock -> if (maySplitInPlace) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        if (part1.paragraphsToWrap.any { it.content.isNotBlank() }) {
                            val prepared = verifiedSplitPart(part1, spaceBetweenBlocks, heightForSplitting)
                            if (prepared != null) {
                                currentPageContent += prepared
                                if (part2.isNotEmpty()) remainingBlocks.addAll(0, part2)
                                wasSplit = true
                            }
                        }
                    }
                }
                is TableBlock -> if (maySplitInPlace) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        val prepared = verifiedSplitPart(part1, spaceBetweenBlocks, heightForSplitting)
                        if (prepared != null) {
                            currentPageContent += prepared
                            remainingBlocks.add(0, part2)
                            wasSplit = true
                        }
                    }
                }
                is FlexContainerBlock -> if (maySplitInPlace) {
                    measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                        val prepared = verifiedSplitPart(part1, spaceBetweenBlocks, heightForSplitting)
                        if (prepared != null) {
                            currentPageContent += prepared
                            remainingBlocks.add(0, part2)
                            wasSplit = true
                        }
                    }
                }
                is ChantScoreBlock -> measurementProvider.split(block, heightForSplitting)?.let { (part1, part2) ->
                    val measured = part1.withReaderExpectedHeight(
                        measurementProvider.measure(part1) + spaceBetweenBlocks
                    )
                    if (measured.expectedHeight <= remainingHeight) {
                        currentPageContent += measured
                        remainingBlocks.add(0, part2)
                        wasSplit = true
                    } else {
                        onCutoffDiagnostic(
                            "cutoff_probe layer=android_paginator_split_fragment_rejected page=${pageIndex + 1} " +
                                "block=${block.blockIndex} kind=ChantScoreBlock " +
                                "fragmentExpectedPx=${measured.expectedHeight} remainingPx=$remainingHeight " +
                                "heightForSplittingPx=$heightForSplitting"
                        )
                    }
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
