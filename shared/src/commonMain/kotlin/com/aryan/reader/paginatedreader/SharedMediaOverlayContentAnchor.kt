package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.reader.SharedMediaOverlayDocument
import com.aryan.reader.shared.reader.SharedPlaybackFragment

/**
 * Resolving a media overlay clip's element id against **styled content blocks**.
 *
 * This is the ContentBlock twin of the `SemanticBlock` resolver in `SharedEpubMediaOverlayPlayback`.
 * Both exist because the same book is described by two block families — the semantic IR the loaders
 * produce and the styled blocks the paginated surfaces paint — and they cannot see each other's
 * types. Android's paginator holds styled blocks, so its overlay projection resolves here.
 *
 * The two resolvers deliberately answer identically: same container recursion as
 * [findSharedNavigationTargetForAnchor], same span preference (an element id on an inline span
 * covers the span, one on a block covers the whole block), same "null when the id names no text"
 * refusal. A divergence between them would paint the band somewhere the navigation never goes.
 */
private const val ID_ANNOTATION_TAG = "ID"

/**
 * Every clip's anchor in one chapter's blocks, keyed by clip index.
 *
 * One pass collecting every requested id, for the same reason the semantic resolver does it: a
 * 500-clip chapter resolved per clip would walk the chapter 500 times, and a seek needs the whole
 * map at once. Unresolvable clips are absent rather than mapped to null, so a caller walking
 * playback order skips them with one lookup and no branching.
 */
fun resolveSharedMediaOverlayFragmentsInBlocks(
    document: SharedMediaOverlayDocument,
    blocks: List<ContentBlock>?
): Map<Int, SharedPlaybackFragment> {
    if (blocks.isNullOrEmpty()) return emptyMap()
    val byElementId = HashMap<String, SharedPlaybackFragment>()
    for (clip in document.clips) {
        val id = clip.elementId?.takeIf(String::isNotBlank) ?: continue
        if (byElementId.containsKey(id)) continue
        blocks.firstNotNullOfOrNull { block ->
            block.findMediaOverlayFragment(id)
        }?.let { byElementId[id] = it }
    }
    val resolved = LinkedHashMap<Int, SharedPlaybackFragment>()
    for (clip in document.clips) {
        val id = clip.elementId?.takeIf(String::isNotBlank) ?: continue
        byElementId[id]?.let { resolved[clip.clipIndex] = it }
    }
    return resolved
}

/**
 * One clip's anchor as a fragment, or null when [elementId] names no text in this block tree.
 *
 * A block-level id covers the whole block's text — what a publisher means by marking a paragraph.
 * An inline span id (an `"ID"` string annotation, where the styler records span element ids) covers
 * the span's own extent. Both keep the block's own cfi, because that is the identity
 * [SharedPlaybackFragment] anchors on and every paint path matches by it.
 */
private fun ContentBlock.findMediaOverlayFragment(elementId: String): SharedPlaybackFragment? {
    if (this is TextContentBlock) {
        if (elementId == this.elementId) {
            return SharedPlaybackFragment(
                blockCfi = this.cfi,
                startAbs = this.startCharOffsetInSource,
                endAbs = this.blockTextEndAbs()
            )
        }
        this.content.getStringAnnotations(ID_ANNOTATION_TAG, 0, this.content.length)
            .firstOrNull { it.item == elementId }
            ?.let { annotation ->
                // Annotation offsets are block-local; the fragment's are chapter-absolute. Both are
                // half-open, so the span's own `end` is already the exclusive chapter-relative
                // end — adding one here would make every span one character too long.
                return SharedPlaybackFragment(
                    blockCfi = this.cfi,
                    startAbs = this.startCharOffsetInSource + annotation.start,
                    endAbs = this.startCharOffsetInSource + annotation.end
                )
            }
    }
    return when (this) {
        is FlexContainerBlock -> children.firstNotNullOfOrNull { it.findMediaOverlayFragment(elementId) }
        is TableBlock -> rows.asSequence()
            .flatMap { it.asSequence() }
            .mapNotNull { cell -> cell.content.firstNotNullOfOrNull { it.findMediaOverlayFragment(elementId) } }
            .firstOrNull()
        is WrappingContentBlock -> sequenceOf<ContentBlock>(floatedImage)
            .plus(paragraphsToWrap)
            .firstNotNullOfOrNull { it.findMediaOverlayFragment(elementId) }
        else -> null
    }
}

/**
 * The block's text end, in chapter-absolute offsets.
 *
 * `endCharOffsetInSource` is chapter-absolute and exclusive — the same convention `findPageForCfi`
 * uses — and is unset (`-1`) on blocks produced by older caches, so the end falls back to the
 * block's own text length. A backwards or empty range would resolve to nothing on every surface,
 * and the band would silently never paint.
 */
private fun TextContentBlock.blockTextEndAbs(): Int {
    val start = startCharOffsetInSource
    val stored = endCharOffsetInSource
    return if (stored >= start) stored else start + content.length
}
