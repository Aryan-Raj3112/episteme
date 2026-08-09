package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.reader.decodeMobileEpubUrl
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

@OptIn(ExperimentalEncodingApi::class)
fun decodeNativeVerticalSvgDataUri(source: String): String? {
    if (!source.startsWith("data:image/svg+xml", ignoreCase = true)) return null
    val commaIndex = source.indexOf(',')
    if (commaIndex < 0) return null
    val metadata = source.substring(0, commaIndex)
    val payload = source.substring(commaIndex + 1)
    return runCatching {
        if (metadata.contains(";base64", ignoreCase = true)) {
            Base64.decode(payload).decodeToString()
        } else {
            decodeMobileEpubUrl(payload.replace("+", "%2B"))
        }
    }.getOrNull()
}

data class NativeVerticalChapterPageInfo(
    val currentPage: Int,
    val totalPages: Int,
)

data class NativeVerticalFlowChapter(
    val chapterIndex: Int,
    val title: String?,
    val blocks: List<ContentBlock>,
    val isLoaded: Boolean = true,
    val estimatedLocationWeight: Int = 0,
)

enum class NativeVerticalFlowItemKind {
    BLOCK,
    CHAPTER_GAP,
    EMPTY_CHAPTER,
    UNLOADED_CHAPTER,
}

data class NativeVerticalFlowItem(
    val key: String,
    val chapterIndex: Int,
    val blockOrdinal: Int,
    val block: ContentBlock?,
    val kind: NativeVerticalFlowItemKind,
    val locationWeight: Int,
)

fun nativeVerticalTextBlockCharOffset(block: TextContentBlock): Int = when (block) {
    is ParagraphBlock -> block.startCharOffsetInSource
    is HeaderBlock -> block.startCharOffsetInSource
    is QuoteBlock -> block.startCharOffsetInSource
    is ListItemBlock -> block.startCharOffsetInSource
}

fun List<ContentBlock>.nativeVerticalTextBlocks(): List<TextContentBlock> = buildList {
    for (block in this@nativeVerticalTextBlocks) {
        when (block) {
            is WrappingContentBlock -> addAll(block.paragraphsToWrap)
            is FlexContainerBlock -> addAll(block.children.nativeVerticalTextBlocks())
            is TableBlock -> block.rows.forEach { row ->
                row.forEach { cell -> addAll(cell.content.nativeVerticalTextBlocks()) }
            }
            is TextContentBlock -> add(block)
            else -> Unit
        }
    }
}

fun nativeVerticalFlowItemWeight(block: ContentBlock?): Int {
    if (block == null) return 0
    val textLength = listOf(block).nativeVerticalTextBlocks().sumOf { it.content.text.length }
    return textLength.coerceAtLeast(
        when (block) {
            is ImageBlock -> 250
            is MathBlock -> 80
            is SpacerBlock -> 1
            else -> 24
        }
    )
}

fun buildNativeVerticalFlowItems(
    chapters: List<NativeVerticalFlowChapter>,
): List<NativeVerticalFlowItem> = chapters.flatMapIndexed { chapterOrdinal, chapter ->
    val boundary = if (chapterOrdinal > 0) {
        listOf(NativeVerticalFlowItem("chapter-${chapter.chapterIndex}-gap", chapter.chapterIndex, -2, null, NativeVerticalFlowItemKind.CHAPTER_GAP, 0))
    } else emptyList()
    when {
        !chapter.isLoaded -> boundary + NativeVerticalFlowItem(
            "chapter-${chapter.chapterIndex}-unloaded", chapter.chapterIndex, -1, null,
            NativeVerticalFlowItemKind.UNLOADED_CHAPTER, chapter.estimatedLocationWeight.coerceAtLeast(24),
        )
        chapter.blocks.isEmpty() -> boundary + NativeVerticalFlowItem(
            "chapter-${chapter.chapterIndex}-empty", chapter.chapterIndex, -1, null,
            NativeVerticalFlowItemKind.EMPTY_CHAPTER, 0,
        )
        else -> boundary + chapter.blocks.mapIndexed { ordinal, block ->
            NativeVerticalFlowItem(
                "chapter-${chapter.chapterIndex}-block-$ordinal-${block.blockIndex}",
                chapter.chapterIndex, ordinal, block, NativeVerticalFlowItemKind.BLOCK,
                nativeVerticalFlowItemWeight(block),
            )
        }
    }
}

fun nativeVerticalNavigationTargetForBlock(
    chapterIndex: Int,
    block: ContentBlock,
): ReaderNavigationTarget {
    val textBlocks = listOf(block).nativeVerticalTextBlocks()
    val firstTextBlock = textBlocks.firstOrNull { it.content.text.isNotBlank() } ?: textBlocks.firstOrNull()
    return if (firstTextBlock != null) {
        ReaderNavigationTarget(chapterIndex, firstTextBlock.blockIndex, nativeVerticalTextBlockCharOffset(firstTextBlock))
    } else ReaderNavigationTarget(chapterIndex, block.blockIndex, 0)
}

fun findNativeVerticalFlowTextBlockForTarget(
    chapters: List<NativeVerticalFlowChapter>,
    target: ReaderNavigationTarget,
): TextContentBlock? {
    val blocks = chapters.firstOrNull { it.chapterIndex == target.chapterIndex }?.blocks ?: return null
    val textBlocks = blocks.nativeVerticalTextBlocks()
    return textBlocks.firstOrNull { block ->
        val start = nativeVerticalTextBlockCharOffset(block)
        block.blockIndex == target.blockIndex && target.charOffset in start..(start + block.content.text.length)
    } ?: textBlocks.firstOrNull { it.blockIndex >= target.blockIndex } ?: textBlocks.firstOrNull()
}

fun nativeVerticalFlowBlockMatchesTarget(block: ContentBlock, target: ReaderNavigationTarget): Boolean {
    if (block.blockIndex == target.blockIndex) return true
    return when (block) {
        is FlexContainerBlock -> block.children.any { nativeVerticalFlowBlockMatchesTarget(it, target) }
        is TableBlock -> block.rows.flatten().any { cell -> cell.content.any { nativeVerticalFlowBlockMatchesTarget(it, target) } }
        is WrappingContentBlock -> nativeVerticalFlowBlockMatchesTarget(block.floatedImage, target) ||
            block.paragraphsToWrap.any { nativeVerticalFlowBlockMatchesTarget(it, target) }
        else -> false
    }
}

fun findNativeVerticalFlowItemIndexForTarget(
    items: List<NativeVerticalFlowItem>,
    chapters: List<NativeVerticalFlowChapter>,
    target: ReaderNavigationTarget,
): Int? {
    val targetTextBlock = findNativeVerticalFlowTextBlockForTarget(chapters, target)
    if (targetTextBlock != null && targetTextBlock.blockIndex == target.blockIndex) {
        val exactIndex = items.indexOfFirst { item ->
            item.chapterIndex == target.chapterIndex && item.block?.let { block ->
                listOf(block).nativeVerticalTextBlocks().any { textBlock ->
                    textBlock.cfi == targetTextBlock.cfi ||
                        (textBlock.blockIndex == targetTextBlock.blockIndex &&
                            nativeVerticalTextBlockCharOffset(textBlock) == nativeVerticalTextBlockCharOffset(targetTextBlock))
                }
            } == true
        }
        if (exactIndex >= 0) return exactIndex
    }
    val containerIndex = items.indexOfFirst { it.chapterIndex == target.chapterIndex && it.block?.let { block -> nativeVerticalFlowBlockMatchesTarget(block, target) } == true }
    if (containerIndex >= 0) return containerIndex
    val blockIndex = items.indexOfFirst { it.chapterIndex == target.chapterIndex && (it.block?.blockIndex ?: Int.MAX_VALUE) >= target.blockIndex }
    if (blockIndex >= 0) return blockIndex
    return items.indexOfFirst { it.chapterIndex == target.chapterIndex }.takeIf { it >= 0 }
}

fun nativeVerticalNavigationTargetForItem(item: NativeVerticalFlowItem): ReaderNavigationTarget =
    item.block?.let { nativeVerticalNavigationTargetForBlock(item.chapterIndex, it) }
        ?: ReaderNavigationTarget(item.chapterIndex, 0, 0)

fun nativeVerticalInitialChapterPrefetchOrder(
    chapterCount: Int,
    initialChapter: Int,
    forwardCount: Int = 2,
    backwardCount: Int = 0,
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

fun nativeVerticalFlowChaptersAfterLoadResult(
    currentChapters: List<NativeVerticalFlowChapter>?,
    placeholderChapters: List<NativeVerticalFlowChapter>,
    chapterIndex: Int,
    title: String?,
    blocks: List<ContentBlock>?,
    estimatedLocationWeight: Int,
): List<NativeVerticalFlowChapter>? {
    if (blocks == null || chapterIndex !in placeholderChapters.indices) return null
    val current = currentChapters ?: placeholderChapters
    if (chapterIndex !in current.indices) return null
    return current.toMutableList().also { updated ->
        updated[chapterIndex] = NativeVerticalFlowChapter(
            chapterIndex = chapterIndex,
            title = title,
            blocks = blocks,
            isLoaded = true,
            estimatedLocationWeight = estimatedLocationWeight,
        )
    }
}

fun nativeVerticalChapterWarmupOrder(
    chapterCount: Int,
    anchorChapter: Int,
    forwardCount: Int = 4,
    backwardCount: Int = 1,
): List<Int> {
    if (chapterCount <= 0) return emptyList()
    val anchor = anchorChapter.coerceIn(0, chapterCount - 1)
    val maxDistance = maxOf(forwardCount.coerceAtLeast(0), backwardCount.coerceAtLeast(0))
    return buildList {
        add(anchor)
        for (distance in 1..maxDistance) {
            if (distance <= forwardCount) {
                val next = anchor + distance
                if (next < chapterCount) add(next)
            }
            if (distance <= backwardCount) {
                val previous = anchor - distance
                if (previous >= 0) add(previous)
            }
        }
    }
}

fun shouldFallbackNativeVerticalInitialScrollToCompatPage(
    hasInitialLocator: Boolean,
    didLocatorScroll: Boolean,
): Boolean = !hasInitialLocator && !didLocatorScroll

fun nativeVerticalCenteredScrollDelta(targetOffsetInViewport: Float, viewportHeight: Float): Float =
    targetOffsetInViewport - (viewportHeight * 0.5f)

fun nativeVerticalCompatPageForProgress(progressPercent: Float, totalPageCount: Int): Int {
    if (totalPageCount <= 1) return 0
    return ((progressPercent.coerceIn(0f, 100f) / 100f) * (totalPageCount - 1))
        .roundToInt()
        .coerceIn(0, totalPageCount - 1)
}

fun nativeVerticalProgressForCompatPage(pageIndex: Int, totalPageCount: Int): Float {
    if (totalPageCount <= 1) return 0f
    return (pageIndex.coerceIn(0, totalPageCount - 1).toFloat() / (totalPageCount - 1).toFloat() * 100f)
        .coerceIn(0f, 100f)
}

fun nativeVerticalCompatPageForLocator(
    chapterCharOffset: Int,
    chapterStartPageIndex: Int?,
    chapterPageCount: Int?,
    chapterLengthChars: Int?,
    fallbackPageIndex: Int,
): Int {
    val chapterStart = chapterStartPageIndex ?: return fallbackPageIndex
    val pageCount = chapterPageCount ?: 1
    if (pageCount <= 1) return chapterStart
    val chapterChars = chapterLengthChars?.coerceAtLeast(1) ?: return fallbackPageIndex
    val ratio = chapterCharOffset.toFloat().coerceAtLeast(0f) / chapterChars.toFloat()
    val pageInChapter = (ratio.coerceIn(0f, 1f) * (pageCount - 1)).roundToInt()
    return chapterStart + pageInChapter
}

fun nativeVerticalProgressPercentForLocator(
    chapterCharacterCounts: List<Int>,
    chapterIndex: Int,
    chapterCharOffset: Int,
): Float? {
    val totalChars = chapterCharacterCounts.sumOf { it.toLong() }.takeIf { it > 0L } ?: return null
    val completedChars = chapterCharacterCounts.take(chapterIndex).sumOf { it.toLong() }
    val chapterChars = chapterCharacterCounts.getOrNull(chapterIndex)?.toLong() ?: 0L
    val chapterOffset = chapterCharOffset.toLong().coerceIn(0L, chapterChars.coerceAtLeast(0L))
    return (((completedChars + chapterOffset).toDouble() / totalChars.toDouble()) * 100.0)
        .toFloat()
        .coerceIn(0f, 100f)
}

fun nativeVerticalChapterPageInfo(
    chapterCharOffset: Int?,
    chapterLengthChars: Int,
    chapterPageCount: Int?,
    compatPageIndex: Int,
    chapterStartPageIndex: Int?,
): NativeVerticalChapterPageInfo? {
    val total = chapterPageCount?.takeIf { it > 0 } ?: return null
    val pageIndexInChapter = if (chapterCharOffset != null && chapterLengthChars > 0) {
        ((chapterCharOffset.coerceIn(0, chapterLengthChars).toFloat() / chapterLengthChars.toFloat()) * (total - 1))
            .roundToInt()
    } else if (chapterStartPageIndex != null) {
        compatPageIndex - chapterStartPageIndex
    } else {
        0
    }.coerceIn(0, total - 1)
    return NativeVerticalChapterPageInfo(pageIndexInChapter + 1, total)
}

fun nativeVerticalChapterPageInfoForScroll(
    itemChapterIndices: List<Int>,
    itemWeights: List<Int>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
    chapterPageCount: Int?,
): NativeVerticalChapterPageInfo? {
    val total = chapterPageCount?.takeIf { it > 0 } ?: return null
    if (itemChapterIndices.isEmpty() || itemWeights.isEmpty()) return NativeVerticalChapterPageInfo(1, total)
    val safeIndex = firstVisibleItemIndex.coerceIn(0, minOf(itemChapterIndices.lastIndex, itemWeights.lastIndex))
    val chapterIndex = itemChapterIndices[safeIndex]
    val chapterItems = itemChapterIndices.indices.filter { it < itemWeights.size && itemChapterIndices[it] == chapterIndex }
    val totalChapterWeight = chapterItems.sumOf { itemWeights[it].coerceAtLeast(0) }
    if (totalChapterWeight <= 0) return NativeVerticalChapterPageInfo(1, total)
    val completedWeight = chapterItems.filter { it < safeIndex }.sumOf { itemWeights[it].coerceAtLeast(0) }
    val currentWeight = itemWeights[safeIndex].coerceAtLeast(0)
    val currentFraction = if (firstVisibleItemSize > 0) {
        (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress = ((completedWeight + currentWeight * currentFraction) / totalChapterWeight.toFloat()).coerceIn(0f, 1f)
    return NativeVerticalChapterPageInfo(
        currentPage = (progress * (total - 1)).roundToInt().coerceIn(0, total - 1) + 1,
        totalPages = total,
    )
}

fun nativeVerticalProgressToItemIndex(itemWeights: List<Int>, progressPercent: Float): Int? {
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
        if (targetWeight <= next || index == itemWeights.lastIndex) return index
        accumulated = next
    }
    return lastWeightedIndex
}

fun estimateNativeVerticalWeightedScrollProgressPercent(
    itemWeights: List<Int>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
): Float? {
    if (itemWeights.isEmpty()) return null
    val totalWeight = itemWeights.sum().takeIf { it > 0 } ?: return null
    val safeIndex = firstVisibleItemIndex.coerceIn(0, itemWeights.lastIndex)
    val completedWeight = itemWeights.take(safeIndex).sum()
    val currentFraction = if (firstVisibleItemSize > 0) {
        (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val weightedPosition = completedWeight + itemWeights[safeIndex] * currentFraction
    return ((weightedPosition.toDouble() / totalWeight.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
}
