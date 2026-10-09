package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.SharedListeningSurface
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The media overlay projection: playback position in, paintable fragment out.
 *
 * The interesting cases are not the happy path. A projection has to answer honestly when a book is
 * wrong — narration recorded against a different reflow, a `par` with no anchor, a spine that points
 * at the wrong document — and it has to not do O(clips x blocks) work on a path that runs every two
 * seconds. Both are what these cover.
 */
class SharedMediaOverlayProjectionTest {

    // --- fixtures ---------------------------------------------------------------------------

    private fun clip(
        index: Int,
        elementId: String? = "p$index",
        audioPath: String? = "OEBPS/Audio/a.mp3"
    ) = SharedMediaOverlayClip(
        parId = "par$index",
        clipIndex = index,
        textHref = "OEBPS/Text/c.xhtml",
        elementId = elementId,
        audioPath = audioPath,
        clipBeginMs = index * 1_000L,
        clipEndMs = (index + 1) * 1_000L,
        epubTypes = emptySet(),
        seqDepth = 1
    )

    private fun document(vararg clips: SharedMediaOverlayClip) = SharedMediaOverlayDocument(
        spineItemIndex = 4,
        textHref = "OEBPS/Text/c.xhtml",
        clips = clips.toList(),
        declaredDurationMs = null
    )

    /**
     * Chapter `chapterIndex`'s block `blockIndex`: element id `p<blockIndex>`, cfi `/2/c<chapter>/b<block>`,
     * absolute offset `chapterIndex * 100 + blockIndex * 10`.
     *
     * Every chapter carries the *same* element ids, which is what a real book looks like — each
     * document's overlay names ids local to that document. That makes the chapter-scoped offset the
     * only thing that can tell two chapters' fragments apart, so it is the part the assertions lean on.
     */
    private fun block(chapterIndex: Int, blockIndex: Int) = SemanticParagraph(
        text = "line$blockIndex",
        spans = emptyList(),
        style = CssStyle(),
        elementId = "p$blockIndex",
        cfi = "/2/c$chapterIndex/b$blockIndex",
        startCharOffsetInSource = chapterIndex * 100 + blockIndex * 10
    )

    private fun chapters(count: Int) = (0 until count).map { chapterIndex ->
        SharedEpubChapter(
            id = "c$chapterIndex",
            title = "Chapter $chapterIndex",
            plainText = "",
            semanticBlocks = (0 until 5).map { block(chapterIndex, it) }
        )
    }

    private val index = SharedMediaOverlayIndex(
        smilPathBySpineItem = mapOf(4 to "OEBPS/smil/c.smil"),
        smilIdBySpineItem = mapOf(4 to "c"),
        totalDurationMs = null,
        narrator = null,
        activeClass = null,
        playbackActiveClass = null,
        declaredDurationMsBySpineItem = emptyMap()
    )

    private fun smil(clips: List<SharedMediaOverlayClip>) = buildString {
        append("<smil xmlns=\"http://www.w3.org/ns/SMIL\"><body><seq>")
        for (c in clips) {
            append("<par>")
            append("<text src=\"c.xhtml#${c.elementId}\"/>")
            c.audioPath?.let { append("<audio src=\"$it\" clipBegin=\"${c.clipBeginMs}\" clipEnd=\"${c.clipEndMs}\"/>") }
            append("</par>")
        }
        append("</seq></body></smil>")
    }

    private fun projector(
        vararg clips: SharedMediaOverlayClip,
        bookChapters: List<SharedEpubChapter> = chapters(3),
        spineMap: Map<Int, Int> = mapOf(4 to 1)
    ) = SharedMediaOverlayProjector(
        cache = SharedMediaOverlayDocumentCache(index, readSmil = { smil(clips.toList()) }),
        chapters = bookChapters,
        spineItemIndexToChapterIndex = spineMap
    )

    // --- projection -------------------------------------------------------------------------

    @Test
    fun `a playback position resolves to a fragment`() {
        runTest {
            val projection = projector(clip(0)).project(spineItemIndex = 4, clipIndex = 0)
            assertEquals(4, projection?.spineItemIndex)
            assertEquals(0, projection?.clipIndex)
            assertEquals(1, projection?.chapterIndex)
            assertTrue(projection?.hasFragment == true)
            assertEquals("/2/c1/b0", projection?.fragment?.blockCfi)
            assertEquals(100, projection?.fragment?.startAbs)
        }
    }

    @Test
    fun `a clip outside the document projects to null rather than a wrong fragment`() {
        runTest {
            assertNull(projector(clip(0)).project(spineItemIndex = 4, clipIndex = 7))
        }
    }

    @Test
    fun `a spine item with no overlay projects to null`() {
        runTest {
            assertNull(projector(clip(0)).project(spineItemIndex = 99, clipIndex = 0))
        }
    }

    /**
     * The common real-book failure: a `par` whose `text/@src` has no fragment. The audio plays and
     * nothing is highlighted, which is correct — there is nothing to highlight.
     */
    @Test
    fun `a clip with no anchor plays without a fragment`() {
        runTest {
            val projection = projector(clip(0, elementId = null)).project(spineItemIndex = 4, clipIndex = 0)
            assertEquals(0, projection?.clipIndex)
            assertNull(projection?.fragment)
            assertFalse(projection?.hasFragment == true)
        }
    }

    /** An anchor that names an element the chapter does not contain. Also normal, not an error. */
    @Test
    fun `an unresolvable anchor yields no fragment but keeps the chapter`() {
        runTest {
            val projection = projector(clip(0, elementId = "does-not-exist")).project(4, 0)
            assertNull(projection?.fragment)
            // The chapter still comes from the spine mapping, so navigation works even though painting
            // cannot. The two failing independently is the whole reason they are separate fields.
            assertEquals(1, projection?.chapterIndex)
        }
    }

    // --- the cost rule ----------------------------------------------------------------------

    /**
     * The reason [SharedMediaOverlayProjector] caches at all.
     *
     * Clips advance every couple of seconds, so this runs constantly. Re-resolving per clip would walk
     * a chapter's blocks once per `par`; the reference book does that 2315 times over one chapter.
     */
    @Test
    fun `anchors are resolved once per chapter and reused across clip changes`() {
        runTest {
            val allClips = (0 until 5).map { clip(it) }
            val cache = SharedMediaOverlayDocumentCache(index, readSmil = { smil(allClips) })
            val projector = SharedMediaOverlayProjector(cache, chapters(3), mapOf(4 to 1))

            assertEquals("/2/c1/b0", projector.project(4, 0)?.fragment?.blockCfi)
            assertEquals(1, projector.anchorResolutionCount)
            val parsesAfterFirst = cache.parseCount

            // Every subsequent clip in the same chapter must re-resolve nothing.
            for (i in 1..4) {
                assertEquals("/2/c1/b$i", projector.project(4, i)?.fragment?.blockCfi)
            }
            assertEquals(1, projector.anchorResolutionCount)
            assertEquals(parsesAfterFirst, cache.parseCount)
        }
    }

    /**
     * Moving to another spine item must re-resolve, and the count is what proves the reuse is
     * conditional rather than a one-shot cache that silently serves the wrong chapter forever.
     */
    @Test
    fun `moving to another chapter re-resolves against its own blocks`() {
        runTest {
            val twoSpine = SharedMediaOverlayIndex(
                smilPathBySpineItem = mapOf(4 to "OEBPS/smil/c.smil", 5 to "OEBPS/smil/c2.smil"),
                smilIdBySpineItem = mapOf(4 to "c", 5 to "c2"),
                totalDurationMs = null,
                narrator = null,
                activeClass = null,
                playbackActiveClass = null,
                declaredDurationMsBySpineItem = emptyMap()
            )
            val twoClips = (0 until 2).map { clip(it) }
            val cache = SharedMediaOverlayDocumentCache(twoSpine, readSmil = { smil(twoClips) })
            val projector = SharedMediaOverlayProjector(
                cache,
                chapters(3),
                mapOf(4 to 1, 5 to 2)
            )

            // Same element ids in both chapters, so only the chapter-scoped cfi and offset can tell them
            // apart — which is exactly what a stale cache would get wrong.
            assertEquals("/2/c1/b0", projector.project(4, 0)?.fragment?.blockCfi)
            assertEquals(100, projector.project(4, 0)?.fragment?.startAbs)
            assertEquals("/2/c2/b0", projector.project(5, 0)?.fragment?.blockCfi)
            assertEquals(200, projector.project(5, 0)?.fragment?.startAbs)
            // One resolution per chapter, not one for the whole projector: caching without the
            // chapter-change check would serve chapter 1's anchors for chapter 2 forever.
            assertEquals(2, projector.anchorResolutionCount)
        }
    }

    @Test
    fun `invalidate drops the cached anchors`() {
        runTest {
            val projector = projector(clip(0))
            assertEquals("/2/c1/b0", projector.project(4, 0)?.fragment?.blockCfi)
            assertEquals(1, projector.anchorResolutionCount)
            projector.invalidate()
            assertEquals("/2/c1/b0", projector.project(4, 0)?.fragment?.blockCfi)
            // Without this the reader would keep painting a chapter's fragments after the book changed
            // under it — stale highlights pointing at text that no longer means anything.
            assertEquals(2, projector.anchorResolutionCount)
        }
    }

    // --- chapter resolution -----------------------------------------------------------------

    @Test
    fun `the spine mapping wins over a content search`() {
        runTest {
            // Every chapter carries `p0`, so a content search would return chapter 0. The spine says 2,
            // and the spine is authoritative.
            val projector = projector(clip(0), spineMap = mapOf(4 to 2))
            assertEquals(2, projector.project(4, 0)?.chapterIndex)
        }
    }

    /**
     * A spine index mapping to a chapter that does not exist must not index off the end. This is a
     * lookup because real books have more chapters than spine items, not because of any promise.
     */
    @Test
    fun `a spine entry pointing outside the book falls back to a content search`() {
        runTest {
            val projector = projector(clip(0), spineMap = mapOf(4 to 99))
            assertEquals(0, projector.project(4, 0)?.chapterIndex)
        }
    }

    /**
     * No spine mapping at all is the normal case for a book loaded without spine indices.
     *
     * Every chapter carries the same element ids, so the answer is the first match. That is the right
     * fallback rather than a coincidence: when the overlay and the spine disagree about where a
     * fragment lives, the fragment's own document is better evidence than a spine index that has
     * already been shown to be wrong.
     */
    @Test
    fun `the chapter is found by content when there is no spine mapping`() {
        runTest {
            val projector = projector(clip(0), spineMap = emptyMap())
            assertEquals(0, projector.project(4, 0)?.chapterIndex)
        }
    }

    // --- follow -----------------------------------------------------------------------------

    private fun projection(chapterIndex: Int?) = SharedMediaOverlayProjection(
        spineItemIndex = 4,
        clipIndex = 0,
        chapterIndex = chapterIndex,
        fragment = SharedPlaybackFragment("/2/4/0", 0, 10)
    )

    /**
     * The one case a surface's keep-visible rule cannot catch: the narrated fragment is not on screen
     * at all, because it is in a different chapter.
     */
    @Test
    fun `narration leaving the reader's chapter follows`() {
        runTest {
            assertTrue(shouldFollowSharedMediaOverlay(next = projection(2), previous = projection(1), readerChapterIndex = 1))
        }
    }

    /** Playback just started and the reader may be anywhere. */
    @Test
    fun `starting playback follows`() {
        runTest {
            assertTrue(shouldFollowSharedMediaOverlay(next = projection(1), previous = null, readerChapterIndex = 1))
        }
    }

    /**
     * Same chapter, next line.
     *
     * False on purpose. A clip is a line, so a per-clip scroll decision made without a rectangle
     * re-centres the page every two seconds; and the surfaces can measure the real rectangle, which
     * is a strictly better answer than this function could give.
     */
    @Test
    fun `advancing within a chapter leaves the scroll to the surface`() {
        runTest {
            assertFalse(shouldFollowSharedMediaOverlay(next = projection(1), previous = projection(1), readerChapterIndex = 1))
        }
    }

    /**
     * No evidence either way.
     *
     * Scrolling wrongly is recoverable — the reader scrolls back. Leaving them stranded on a chapter
     * the narration has left is not, so the uncertain case scrolls.
     */
    @Test
    fun `an unplaceable chapter follows rather than stranding the reader`() {
        runTest {
            assertTrue(shouldFollowSharedMediaOverlay(next = projection(null), previous = projection(null), readerChapterIndex = 1))
            assertTrue(shouldFollowSharedMediaOverlay(next = projection(1), previous = projection(1), readerChapterIndex = null))
        }
    }

    @Test
    fun `nothing playing never follows`() {
        runTest {
            assertFalse(shouldFollowSharedMediaOverlay(next = null, previous = projection(1), readerChapterIndex = 2))
            assertFalse(shouldFollowSharedMediaOverlay(next = null, previous = null, readerChapterIndex = null))
        }
    }

    // --- tap to play ------------------------------------------------------------------------

    @Test
    fun `tapping an element finds the clip that narrates it`() {
        runTest {
            val doc = document(clip(0), clip(1), clip(2))
            assertEquals(1, sharedMediaOverlayClipIndexForElementId(doc, "p1"))
            assertNull(sharedMediaOverlayClipIndexForElementId(doc, "p9"))
            assertNull(sharedMediaOverlayClipIndexForElementId(doc, null))
            assertNull(sharedMediaOverlayClipIndexForElementId(doc, "  "))
        }
    }

    /**
     * The first matching clip wins, and that is the right answer rather than an accident of ordering:
     * publishers do repeat an element id across a sequence, and playing it again from the start is
     * better than playing a later, arbitrary occurrence of the same line.
     */
    @Test
    fun `a repeated element id resolves to its first clip`() {
        runTest {
            val doc = document(clip(0), clip(1, elementId = "p0"), clip(2))
            assertEquals(0, sharedMediaOverlayClipIndexForElementId(doc, "p0"))
        }
    }

    // --- which surface owns the reader's play button -----------------------------------------

    /**
     * Arbitration guarantees only one engine plays, so "what should the button do" is a lookup, not a
     * negotiation. A loaded-but-stopped overlay must yield to read-aloud: it has no state to speak
     * of, and stealing the button from a live session would be a regression on the feature that
     * already works.
     */
    @Test
    fun `a loaded but stopped overlay yields to a live tts session`() {
        runTest {
            assertEquals(
                SharedListeningSurface.READER_TTS,
                sharedMediaOverlayActiveSurface(
                    isPlaying = false,
                    mediaOverlayLoaded = true,
                    ttsSessionActive = true
                )
            )
        }
    }

    @Test
    fun `playing audio wins over everything`() {
        runTest {
            assertEquals(
                SharedListeningSurface.MEDIA_OVERLAY,
                sharedMediaOverlayActiveSurface(
                    isPlaying = true,
                    mediaOverlayLoaded = true,
                    ttsSessionActive = true
                )
            )
        }
    }

    @Test
    fun `a loaded but paused overlay still answers when nothing else is playing`() {
        runTest {
            assertEquals(
                SharedListeningSurface.MEDIA_OVERLAY,
                sharedMediaOverlayActiveSurface(
                    isPlaying = false,
                    mediaOverlayLoaded = true,
                    ttsSessionActive = false
                )
            )
            assertNull(
                sharedMediaOverlayActiveSurface(
                    isPlaying = false,
                    mediaOverlayLoaded = false,
                    ttsSessionActive = false
                )
            )
        }
    }

}