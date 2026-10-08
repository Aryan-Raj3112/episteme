package com.aryan.reader.shared.reader

import com.aryan.reader.shared.SharedListeningSurface

/**
 * Turning a media overlay playback position into something the reader can paint and follow.
 *
 * The reader already has all of this machinery for read-aloud — a paint path, a keep-visible follow
 * rule, a chapter-scoped coordinate space — because a fragment is a [SharedPlaybackFragment] either
 * way. What is missing is the projection: playback state in, fragment out.
 *
 * The one thing this file has to get right beyond the obvious is **cost**. A media overlay clip is a
 * line, not a sentence: the reference book advances every two seconds or so, so a projection that
 * re-resolves anchors on every clip change walks a chapter's blocks 2315 times over a chapter and
 * turns a 2-second advance into visible jank. [SharedMediaOverlayProjector] therefore resolves a
 * chapter's anchors once and reuses them while playback stays in that chapter.
 */

/**
 * One narration position, resolved into reader coordinates.
 *
 * [fragment] is null whenever there is nothing to paint — no clip loaded, a clip with no
 * `text/@src` anchor, or an anchor that did not resolve. Every consumer must treat that as "play the
 * audio, highlight nothing" rather than as a failure: an unresolvable anchor is normal on a book
 * whose narration was recorded against a different reflow.
 */
data class SharedMediaOverlayProjection(
    val spineItemIndex: Int,
    val clipIndex: Int,
    /**
     * Which chapter holds the narrated element, or null when it could not be placed.
     *
     * Separate from [fragment] because the two fail independently: a chapter can be known from the
     * spine mapping while an individual element id fails to resolve, and navigation needs the former
     * even when painting is impossible.
     */
    val chapterIndex: Int?,
    val fragment: SharedPlaybackFragment?,
    /**
     * The SMIL element id of the active clip, or null.
     *
     * The WebView surface's own anchor: it highlights by id in the DOM rather than by offset, and
     * Android's reader WebView is the one that wants it. Native surfaces resolve through [fragment]
     * and never read this.
     */
    val elementId: String? = null,
) {
    val hasFragment: Boolean get() = fragment != null
}

/**
 * What a narration session needs from whichever block representation a platform's reader has.
 *
 * The three questions are the whole contract, and they are asked at very different frequencies on
 * purpose: [document] once per chapter change, [project] once per clip — which for a well-produced
 * book is every couple of seconds, and is why implementations cache anchors — and
 * [clipIndexForReaderPosition] only when the reader presses play mid-chapter.
 *
 * Deliberately suspend on two of the three. Android resolves a chapter's anchors from the paginator's
 * `ContentBlock`s, which it builds on demand and so can suspend on; iOS resolves from the semantic
 * blocks the loader already parsed and does not. An interface that forced the iOS side to pretend to
 * suspend, or the Android side to pretend it does not, would put a lie in the one place a divergence
 * between the platforms would otherwise be invisible.
 */
interface SharedMediaOverlayProjectionSource {
    /** The parsed overlay document for a spine item, or null when it has none or it is unusable. */
    fun document(spineItemIndex: Int): SharedMediaOverlayDocument?

    /** Resolves a playback position, or null when the position names no clip at all. */
    suspend fun project(spineItemIndex: Int, clipIndex: Int): SharedMediaOverlayProjection?

    /** The document clip narrating [readerOffset] in the chapter, or null. */
    suspend fun clipIndexForReaderPosition(spineItemIndex: Int, readerOffset: Int): Int?
}

/**
 * Resolves playback positions to fragments, remembering the last chapter's anchors.
 *
 * Holds the book rather than being handed it per call, because the caching is the point: a caller
 * that passes `chapters` on every call is paying the copy it was trying to avoid. Not synchronized —
 * it belongs to the reader's single-threaded UI state, same as [SharedMediaOverlayDocumentCache].
 *
 * @param cache the lazy SMIL document cache; also the source of the clips themselves.
 * @param chapters the book's chapters in spine order.
 * @param spineItemIndexToChapterIndex authoritative spine -> chapter mapping. Optional because
 *   [findChapterContainingElement] rescues a book whose overlay and spine disagree, which is common
 *   in republished books; when the map is present it wins.
 */
class SharedMediaOverlayProjector(
    private val cache: SharedMediaOverlayDocumentCache,
    private val chapters: List<SharedEpubChapter>,
    private val spineItemIndexToChapterIndex: Map<Int, Int> = emptyMap(),
) : SharedMediaOverlayProjectionSource {
    private var anchorsForSpineItemIndex: Int? = null
    private var anchors: Map<Int, SharedPlaybackFragment> = emptyMap()

    /**
     * How many times a chapter's anchors have actually been resolved. Diagnostics and tests; not a
     * hit count.
     *
     * Exists because the optimization this class exists for is invisible from the outside: the SMIL
     * document cache hides the expensive parse, so a projector that re-resolved on every clip would
     * look identical while walking the chapter's blocks 2315 times over.
     */
    var anchorResolutionCount: Int = 0
        private set

    /**
     * The chapter's resolved anchors, recomputed only when the chapter changes.
     *
     * Keyed on spine item rather than chapter index on purpose: a chapter can be narrated by more
     * than one spine item and vice versa, so the spine item is what actually identifies the
     * document whose clips were resolved.
     */
    private fun anchorsFor(spineItemIndex: Int): Map<Int, SharedPlaybackFragment> {
        if (anchorsForSpineItemIndex == spineItemIndex) return anchors
        val document = cache.document(spineItemIndex)
        anchors = if (document == null) {
            emptyMap()
        } else {
            anchorResolutionCount++
            resolveSharedMediaOverlayFragments(
                document = document,
                chapters = chapters,
                spineItemIndexToChapterIndex = spineItemIndexToChapterIndex
            )
        }
        anchorsForSpineItemIndex = spineItemIndex
        return anchors
    }

    /** The parsed overlay document for a spine item, or null. See the session's contract. */
    override fun document(spineItemIndex: Int): SharedMediaOverlayDocument? = cache.document(spineItemIndex)

    /**
     * The document clip narrating [readerOffset] in the chapter, or null.
     *
     * Used to start narration from where the reader is rather than from the top of the chapter. The
     * first clip whose resolved fragment reaches or passes the offset wins, so tapping play
     * mid-chapter narrates from there. A clip with no resolvable fragment cannot be placed and is
     * skipped, which matches how playback will skip it audibly.
     */
    override suspend fun clipIndexForReaderPosition(
        spineItemIndex: Int,
        readerOffset: Int
    ): Int? {
        val document = cache.document(spineItemIndex) ?: return null
        if (readerOffset < 0) return null
        val chapterAnchors = anchorsFor(spineItemIndex)
        if (chapterAnchors.isEmpty()) return null
        return document.clips.firstOrNull { clip ->
            val fragment = chapterAnchors[clip.clipIndex]
            fragment != null && fragment.endAbs > readerOffset
        }?.clipIndex
    }

    /**
     * Resolves a playback position.
     *
     * Cheap on the hot path: a chapter change re-resolves, a clip change within a chapter is a map
     * lookup. That is the difference between advancing a narration smoothly and stuttering it.
     */
    override suspend fun project(spineItemIndex: Int, clipIndex: Int): SharedMediaOverlayProjection? {
        val document = cache.document(spineItemIndex) ?: return null
        if (document.clips.getOrNull(clipIndex) == null) return null
        return SharedMediaOverlayProjection(
            spineItemIndex = spineItemIndex,
            clipIndex = clipIndex,
            chapterIndex = chapterIndexFor(spineItemIndex),
            fragment = anchorsFor(spineItemIndex)[clipIndex]
        )
    }

    /**
     * Which chapter a spine item narrates.
     *
     * Resolved from the first clip's anchor rather than from the document's `epub:textref`: the
     * spine mapping is authoritative when present, and the content search is the fallback for a book
     * whose overlay points at the wrong document. Deriving it from the clips means it is answered by
     * the same evidence the fragments are, so the two cannot disagree about which chapter is being
     * narrated.
     */
    fun chapterIndexFor(spineItemIndex: Int): Int? {
        spineItemIndexToChapterIndex[spineItemIndex]
            ?.takeIf { it in chapters.indices }
            ?.let { return it }
        val document = cache.document(spineItemIndex) ?: return null
        val firstAnchor = document.clips.firstNotNullOfOrNull { it.elementId?.takeIf(String::isNotBlank) }
            ?: return null
        return findChapterContainingElement(
            chapters = chapters,
            preferredIndex = null,
            elementId = firstAnchor,
            preferredPath = document.textHref
        )
    }

    /** Forgets the cached anchors, for when the book or its chapters change underneath us. */
    fun invalidate() {
        anchorsForSpineItemIndex = null
        anchors = emptyMap()
    }
}

/**
 * Whether a narration move should scroll the reader.
 *
 * Only a **chapter change** scrolls. Within a chapter the answer is left to the surface, because
 * each one can do better than any rule written here: the native readers measure the fragment's real
 * rectangle and scroll only when it has left the comfortable band, and the WebView does the same
 * against the viewport. Deciding "the reader has probably not seen this line" without a rectangle
 * would either re-centre on every line — visibly worse than doing nothing — or never scroll at all.
 *
 * So this returns true exactly when the honest per-surface answer is unavailable:
 *
 * - **No previous position** — playback just started, and the reader may be anywhere.
 * - **The chapter changed** — narration has left the page, which is exactly the case keep-visible
 *   cannot catch, because the fragment is not on screen at all.
 * - **The chapter is unknown** — no evidence either way, and leaving the reader stranded is the
 *   worse failure of the two.
 *
 * @param readerChapterIndex the chapter currently on screen, or null when unknown.
 */
fun shouldFollowSharedMediaOverlay(
    next: SharedMediaOverlayProjection?,
    previous: SharedMediaOverlayProjection?,
    readerChapterIndex: Int?
): Boolean {
    if (next == null) return false
    // The reader navigated away from a chapter that is still narrating: follow back.
    if (readerChapterIndex != null && next.chapterIndex != null && readerChapterIndex != next.chapterIndex) {
        return true
    }
    // Playback just started, or we cannot place the narration. Either way the reader may be nowhere
    // near it, and a wrong scroll is recoverable while a stranded reader is not.
    if (previous == null) return true
    if (next.chapterIndex == null || readerChapterIndex == null) return true
    // Same chapter. The surface measures the rectangle.
    return next.chapterIndex != previous.chapterIndex
}

/**
 * The clip that narrates a text element, for starting playback from a tap on the page.
 *
 * Matching by element id rather than by offset is what makes this exact. The alternative — asking
 * which clip covers offset N — needs the intervals sorted and a boundary convention, and gets the
 * answer wrong at every shared boundary; two adjacent lines whose clips share an instant would make
 * the tap land on whichever came first in the list.
 *
 * Returns the *playback* index when a filtered plan is supplied, so the caller can hand it straight
 * to the engine, and the document clip index otherwise.
 */
fun sharedMediaOverlayClipIndexForElementId(
    document: SharedMediaOverlayDocument,
    elementId: String?
): Int? {
    val id = elementId?.takeIf(String::isNotBlank) ?: return null
    return document.clips.firstOrNull { it.elementId == id }?.clipIndex
}

/**
 * The playback surface a reader action should claim, given what is currently loaded.
 *
 * One decision, both platforms. The rule is that the *engine already playing* wins, because starting
 * a second one is what the arbiter would have to stop, and a media overlay that merely has a chapter
 * loaded but is paused must not steal the button from read-aloud. A loaded-but-stopped overlay has
 * no state to speak of, so it yields.
 */
fun sharedMediaOverlayActiveSurface(
    isPlaying: Boolean,
    mediaOverlayLoaded: Boolean,
    ttsSessionActive: Boolean
): SharedListeningSurface? = when {
    isPlaying -> SharedListeningSurface.MEDIA_OVERLAY
    ttsSessionActive -> SharedListeningSurface.READER_TTS
    mediaOverlayLoaded -> SharedListeningSurface.MEDIA_OVERLAY
    else -> null
}