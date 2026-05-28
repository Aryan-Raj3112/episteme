package com.aryan.reader.paginatedreader

import com.aryan.reader.SearchResult

internal fun flattenTextContentBlocksForNavigation(blocks: List<ContentBlock>): List<TextContentBlock> {
    return blocks.flatMap { block ->
        when (block) {
            is WrappingContentBlock -> flattenTextContentBlocksForNavigation(
                listOf<ContentBlock>(block.floatedImage) + block.paragraphsToWrap
            )
            is FlexContainerBlock -> flattenTextContentBlocksForNavigation(block.children)
            is TableBlock -> block.rows.flatten().flatMap { flattenTextContentBlocksForNavigation(it.content) }
            is TextContentBlock -> listOf(block)
            else -> emptyList()
        }
    }
}

internal fun findLocatorForSearchResultInBlocks(
    result: SearchResult,
    blocks: List<ContentBlock>
): Locator? {
    val query = result.query.takeIf { it.isNotBlank() } ?: return null
    var occurrenceCount = 0

    flattenTextContentBlocksForNavigation(blocks).forEach { block ->
        val text = block.content.text
        var lastIndex = -1
        while (true) {
            lastIndex = text.indexOf(query, startIndex = lastIndex + 1, ignoreCase = true)
            if (lastIndex == -1) break

            val isWordStart = lastIndex == 0 || !text[lastIndex - 1].isLetterOrDigit()
            if (isWordStart) {
                if (occurrenceCount == result.occurrenceIndexInLocation) {
                    return Locator(
                        chapterIndex = result.locationInSource,
                        blockIndex = block.blockIndex,
                        charOffset = block.startCharOffsetInSource + lastIndex
                    )
                }
                occurrenceCount++
            }
        }
    }

    return null
}

internal fun findLocatorForAnchorInBlocks(
    chapterIndex: Int,
    anchor: String?,
    blocks: List<ContentBlock>
): Locator? {
    if (anchor.isNullOrBlank()) return Locator(chapterIndex, 0, 0)
    return blocks.asSequence()
        .mapNotNull { findLocatorForAnchorInBlock(chapterIndex, anchor, it) }
        .firstOrNull()
}

private fun findLocatorForAnchorInBlock(
    chapterIndex: Int,
    anchor: String,
    block: ContentBlock
): Locator? {
    if (block.elementId == anchor) return locatorForBlockStart(chapterIndex, block)

    if (block is TextContentBlock) {
        block.content.getStringAnnotations("ID", 0, block.content.length)
            .firstOrNull { it.item == anchor }
            ?.let { annotation ->
                return Locator(
                    chapterIndex = chapterIndex,
                    blockIndex = block.blockIndex,
                    charOffset = block.startCharOffsetInSource + annotation.start
                )
            }
    }

    return when (block) {
        is FlexContainerBlock -> block.children.asSequence()
            .mapNotNull { findLocatorForAnchorInBlock(chapterIndex, anchor, it) }
            .firstOrNull()
        is TableBlock -> block.rows.asSequence()
            .flatMap { row -> row.asSequence() }
            .flatMap { cell -> cell.content.asSequence() }
            .mapNotNull { findLocatorForAnchorInBlock(chapterIndex, anchor, it) }
            .firstOrNull()
        is WrappingContentBlock -> sequenceOf<ContentBlock>(block.floatedImage)
            .plus(block.paragraphsToWrap.asSequence().map { it as ContentBlock })
            .mapNotNull { findLocatorForAnchorInBlock(chapterIndex, anchor, it) }
            .firstOrNull()
        else -> null
    }
}

private fun locatorForBlockStart(chapterIndex: Int, block: ContentBlock): Locator {
    val firstText = flattenTextContentBlocksForNavigation(listOf(block)).firstOrNull()
    return if (firstText != null) {
        Locator(
            chapterIndex = chapterIndex,
            blockIndex = firstText.blockIndex,
            charOffset = firstText.startCharOffsetInSource
        )
    } else {
        Locator(
            chapterIndex = chapterIndex,
            blockIndex = block.blockIndex,
            charOffset = 0
        )
    }
}
