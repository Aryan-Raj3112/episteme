package com.aryan.reader.shared.reader

/**
 * Turning a media overlay clip into something the reader can paint and follow.
 *
 * Two pieces live here, and the split matters:
 *
 * - [SharedMediaOverlayDocumentCache] is the **lazy** half. It reads and parses SMIL bodies on
 *   demand and keeps a bounded number of them. A well-produced book has one clip per *line* — the
 *   reference book has 2315 across 155 documents — so parsing every body on open is a regression on
 *   a 124 MB archive, and keeping them all is a memory leak on a longer one.
 * - [resolveSharedMediaOverlayFragment] / [resolveSharedMediaOverlayFragments] is the **exact**
 *   half. It maps an element-id anchor onto absolute chapter offsets and returns a
 *   [SharedPlaybackFragment] — the same type TTS produces — so one paint path serves both.
 *
 * Deliberately *not* used: the quote-repair machinery (`EpubChapterTextIndex`,
 * `sharedNativeHighlightRangeForBlock`). That exists to repair *stored* highlights whose offsets were
 * computed in a different coordinate space, which is the source of the bugs documented in
 * `docs/epub-highlight-audit-and-plan.md`. An overlay anchor is derived from the same parse that
 * produced the blocks, so it is exact by construction and needs no repair. Reaching for it here
 * would reintroduce exactly that bug class.
 */

/**
 * Reads and caches parsed overlay documents, a bounded number at a time.
 *
 * Not synchronized by design: it belongs to the reader's single-threaded UI state, and a lock here
 * would imply a concurrency contract nobody needs. A miss simply re-reads the archive, so a misuse
 * degrades in speed rather than corrupting anything.
 *
 * @param index the package-level index, which supplies the smil path and declared duration.
 * @param readSmil archive entry path -> SMIL text, or null when the entry is unreadable.
 * @param maxEntries how many parsed documents to keep. A chapter is a few hundred clips at most, so
 *   a small number is ample — the bound is the point, not the size.
 */
class SharedMediaOverlayDocumentCache(
    private val index: SharedMediaOverlayIndex,
    private val readSmil: (String) -> String?,
    maxEntries: Int = 8
) {
    private val cache = SharedLruMemoryCache<Int, SharedMediaOverlayDocument>(maxEntries)

    /** How many bodies have actually been parsed. Diagnostics and tests; not a hit count. */
    var parseCount: Int = 0
        private set

    /**
     * The overlay document for a spine item, or null when it has no overlay or its body is
     * unreadable or unparseable.
     *
     * "No overlay" and "broken overlay" are deliberately indistinguishable. Both mean the reader
     * shows nothing, and distinguishing them would only invite a message about a file the reader
     * never promised to support.
     */
    fun document(spineItemIndex: Int): SharedMediaOverlayDocument? {
        cache[spineItemIndex]?.let { return it }
        val smilPath = index.smilPathBySpineItem[spineItemIndex] ?: return null
        val raw = readSmil(smilPath) ?: return null
        parseCount++
        val document = parseSharedMediaOverlayXml(
            raw = raw,
            smilEntryPath = smilPath,
            spineItemIndex = spineItemIndex,
            declaredDurationMs = index.declaredDurationMsBySpineItem[spineItemIndex]
        ) ?: return null
        cache[spineItemIndex] = document
        return document
    }

    /** The clip at a playback position, or null when either lookup misses. */
    fun clipAt(spineItemIndex: Int, clipIndex: Int): SharedMediaOverlayClip? =
        document(spineItemIndex)?.clips?.getOrNull(clipIndex)

    /** The resolved anchor for a playback position, or null when the clip has no text anchor. */
    fun fragmentAt(
        spineItemIndex: Int,
        clipIndex: Int,
        chapters: List<SharedEpubChapter>,
        spineItemIndexToChapterIndex: Map<Int, Int> = emptyMap()
    ): SharedPlaybackFragment? {
        val document = document(spineItemIndex) ?: return null
        val clip = document.clips.getOrNull(clipIndex) ?: return null
        return resolveSharedMediaOverlayFragment(
            clip = clip,
            spineItemIndex = document.spineItemIndex,
            chapters = chapters,
            spineItemIndexToChapterIndex = spineItemIndexToChapterIndex
        )
    }

    /** Drops every cached document, for when the book changes under us. */
    fun clear() = cache.clear()
}

/**
 * Resolves one clip's text anchor to absolute chapter offsets.
 *
 * Returns null when there is nothing to highlight, which is the normal case for a `par` whose
 * `text/@src` carries no fragment.
 *
 * @param clip the clip being narrated.
 * @param spineItemIndex which spine item the clip belongs to. Passed explicitly because the clip
 *   itself does not repeat it — see [SharedMediaOverlayDocument].
 * @param chapters the book's chapters, in spine order.
 * @param spineItemIndexToChapterIndex spine item index -> chapter index. A lookup rather than an
 *   assumption, because one chapter can be split across several spine items and several spine items
 *   can fold into one chapter.
 */
fun resolveSharedMediaOverlayFragment(
    clip: SharedMediaOverlayClip,
    spineItemIndex: Int,
    chapters: List<SharedEpubChapter>,
    spineItemIndexToChapterIndex: Map<Int, Int> = emptyMap()
): SharedPlaybackFragment? {
    val elementId = clip.elementId?.takeIf(String::isNotBlank) ?: return null
    val chapterIndex = sharedMediaOverlayChapterIndex(
        elementId = elementId,
        textHref = clip.textHref,
        preferredSpineItemIndex = spineItemIndex,
        chapters = chapters,
        spineItemIndexToChapterIndex = spineItemIndexToChapterIndex
    ) ?: return null

    val range = chapters[chapterIndex].semanticBlocks.findElementTextRange(elementId) ?: return null
    return SharedPlaybackFragment(
        blockCfi = range.blockCfi,
        startAbs = range.startAbs,
        endAbs = range.endAbs
    )
}

/**
 * Resolves every anchor in a document in one pass.
 *
 * Resolving per clip is O(clips x blocks). For the reference book's 15-clip chapters that is nothing,
 * but a 500-clip chapter would be 500 walks of the whole chapter, and a seek needs the whole
 * chapter's map at once. One walk collecting every requested id is O(blocks + clips).
 *
 * Unresolvable clips are absent from the map rather than mapped to null, so a caller walking playback
 * order skips them with one lookup and no branching.
 *
 * @return clip index -> fragment, for the clips that resolved.
 */
fun resolveSharedMediaOverlayFragments(
    document: SharedMediaOverlayDocument,
    chapters: List<SharedEpubChapter>,
    spineItemIndexToChapterIndex: Map<Int, Int> = emptyMap()
): Map<Int, SharedPlaybackFragment> {
    val requested = document.clips.mapNotNull { clip ->
        clip.elementId?.takeIf(String::isNotBlank)?.let { clip.clipIndex to it }
    }
    if (requested.isEmpty()) return emptyMap()

    val chapterIndex = sharedMediaOverlayChapterIndex(
        elementId = requested.first().second,
        textHref = document.textHref,
        preferredSpineItemIndex = document.spineItemIndex,
        chapters = chapters,
        spineItemIndexToChapterIndex = spineItemIndexToChapterIndex
    ) ?: return emptyMap()

    val elementIds = requested.map { it.second }.toSet()
    val byElementId = HashMap<String, SharedElementTextRange>(elementIds.size)
    for (block in chapters[chapterIndex].semanticBlocks) {
        for (id in elementIds) {
            if (byElementId.containsKey(id)) continue
            block.findElementTextRange(id)?.let { byElementId[id] = it }
        }
    }

    val resolved = LinkedHashMap<Int, SharedPlaybackFragment>(byElementId.size)
    for ((clipIndex, elementId) in requested) {
        val range = byElementId[elementId] ?: continue
        resolved[clipIndex] = SharedPlaybackFragment(
            blockCfi = range.blockCfi,
            startAbs = range.startAbs,
            endAbs = range.endAbs
        )
    }
    return resolved
}

/**
 * Which chapter holds an overlay fragment.
 *
 * The spine-index map is consulted first because it is authoritative when present: the overlay was
 * linked to that spine item, so that is where the fragment belongs. Only if it is absent or points
 * nowhere does this fall back to searching chapter content, which is what rescues a book whose
 * overlay and spine disagree about which document a fragment lives in — common when narration was
 * recorded against one reflow and the book was republished from another.
 */
private fun sharedMediaOverlayChapterIndex(
    elementId: String,
    textHref: String?,
    preferredSpineItemIndex: Int,
    chapters: List<SharedEpubChapter>,
    spineItemIndexToChapterIndex: Map<Int, Int>
): Int? {
    spineItemIndexToChapterIndex[preferredSpineItemIndex]
        ?.takeIf { it in chapters.indices }
        ?.let { return it }
    return findChapterContainingElement(
        chapters = chapters,
        preferredIndex = null,
        elementId = elementId,
        preferredPath = textHref
    )
}