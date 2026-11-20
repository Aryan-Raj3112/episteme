package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.SearchResult

data class ReaderNavigationTarget(
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int
)

fun flattenTextContentBlocksForSharedNavigation(blocks: List<ContentBlock>): List<TextContentBlock> =
    blocks.flatMap { block ->
        when (block) {
            is WrappingContentBlock -> flattenTextContentBlocksForSharedNavigation(
                listOf<ContentBlock>(block.floatedImage) + block.paragraphsToWrap
            )
            is FlexContainerBlock -> flattenTextContentBlocksForSharedNavigation(block.children)
            is TableBlock -> block.rows.flatten().flatMap {
                flattenTextContentBlocksForSharedNavigation(it.content)
            }
            is TextContentBlock -> listOf(block)
            else -> emptyList()
        }
    }

/**
 * Resolves a search hit to a paginated block/character target.
 *
 * One implementation for every platform: flatten the blocks in reading order
 * (all text-bearing block types, tables/flex/wrapping included), then resolve by
 * the hit's word-start ordinal and, when the searched text and this text
 * disagree, by the hit's own quote. See [readerSearchTarget].
 */
fun findSharedNavigationTargetForSearchResult(
    result: SearchResult,
    blocks: List<ContentBlock>
): ReaderNavigationTarget? {
    val target = readerSearchTarget(
        textBlocks = readerSearchTextBlocks(blocks),
        query = result.query,
        occurrenceIndexInLocation = result.occurrenceIndexInLocation,
        matchQuote = result.matchQuote,
    ) ?: return null

    return ReaderNavigationTarget(
        chapterIndex = result.locationInSource,
        blockIndex = target.blockIndex,
        charOffset = target.charOffsetInSource,
    )
}

fun findSharedNavigationTargetForAnchor(
    chapterIndex: Int,
    anchor: String?,
    blocks: List<ContentBlock>
): ReaderNavigationTarget? {
    if (anchor.isNullOrBlank()) return ReaderNavigationTarget(chapterIndex, 0, 0)
    return blocks.asSequence()
        .mapNotNull { findSharedNavigationTargetForAnchorInBlock(chapterIndex, anchor, it) }
        .firstOrNull()
}

private fun findSharedNavigationTargetForAnchorInBlock(
    chapterIndex: Int,
    anchor: String,
    block: ContentBlock
): ReaderNavigationTarget? {
    if (block.elementId == anchor) return sharedNavigationTargetForBlockStart(chapterIndex, block)

    if (block is TextContentBlock) {
        block.content.getStringAnnotations("ID", 0, block.content.length)
            .firstOrNull { it.item == anchor }
            ?.let { annotation ->
                return ReaderNavigationTarget(
                    chapterIndex = chapterIndex,
                    blockIndex = block.blockIndex,
                    charOffset = block.startCharOffsetInSource + annotation.start
                )
            }
    }

    return when (block) {
        is FlexContainerBlock -> block.children.asSequence()
            .mapNotNull { findSharedNavigationTargetForAnchorInBlock(chapterIndex, anchor, it) }
            .firstOrNull()
        is TableBlock -> block.rows.asSequence()
            .flatMap { it.asSequence() }
            .flatMap { it.content.asSequence() }
            .mapNotNull { findSharedNavigationTargetForAnchorInBlock(chapterIndex, anchor, it) }
            .firstOrNull()
        is WrappingContentBlock -> sequenceOf<ContentBlock>(block.floatedImage)
            .plus(block.paragraphsToWrap.asSequence().map { it as ContentBlock })
            .mapNotNull { findSharedNavigationTargetForAnchorInBlock(chapterIndex, anchor, it) }
            .firstOrNull()
        else -> null
    }
}

private fun sharedNavigationTargetForBlockStart(
    chapterIndex: Int,
    block: ContentBlock
): ReaderNavigationTarget {
    val firstText = flattenTextContentBlocksForSharedNavigation(listOf(block)).firstOrNull()
    return if (firstText != null) {
        ReaderNavigationTarget(
            chapterIndex = chapterIndex,
            blockIndex = firstText.blockIndex,
            charOffset = firstText.startCharOffsetInSource
        )
    } else {
        ReaderNavigationTarget(
            chapterIndex = chapterIndex,
            blockIndex = block.blockIndex,
            charOffset = 0
        )
    }
}
