@file:JvmName("AndroidReaderNavigationTargets")

package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.SearchResult

internal fun flattenTextContentBlocksForNavigation(blocks: List<ContentBlock>): List<TextContentBlock> {
    return flattenTextContentBlocksForSharedNavigation(blocks)
}

internal fun findLocatorForSearchResultInBlocks(
    result: SearchResult,
    blocks: List<ContentBlock>
): Locator? {
    return findSharedNavigationTargetForSearchResult(result, blocks)
}

internal fun findLocatorForAnchorInBlocks(
    chapterIndex: Int,
    anchor: String?,
    blocks: List<ContentBlock>
): Locator? {
    return findSharedNavigationTargetForAnchor(chapterIndex, anchor, blocks)
}
