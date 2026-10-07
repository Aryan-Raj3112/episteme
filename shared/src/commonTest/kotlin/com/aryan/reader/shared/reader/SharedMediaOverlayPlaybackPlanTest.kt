package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The platform-free half of media overlay playback: what plays, how long each clip really runs, and
 * what "next" means.
 *
 * These are the decisions two engines must agree on. Android hands clip boundaries to ExoPlayer as
 * `ClippingConfiguration`; iOS has to enforce them itself against an `AVPlayer` clock. If the two
 * disagreed about where a clip ends, the highlight would drift from the voice — the one bug this
 * feature exists not to have. So the rules live here, tested once, and both engines consume them.
 */
class SharedMediaOverlayPlaybackPlanTest {

    private fun clip(
        index: Int,
        beginMs: Long,
        endMs: Long?,
        audioPath: String? = "OEBPS/Audio/a.mp3",
        text: String? = "line"
    ) = SharedMediaOverlayClip(
        parId = "p$index",
        clipIndex = index,
        textHref = "OEBPS/Text/c.xhtml",
        elementId = text,
        audioPath = audioPath,
        clipBeginMs = beginMs,
        clipEndMs = endMs,
        epubTypes = emptySet(),
        seqDepth = 1
    )

    private fun document(vararg clips: SharedMediaOverlayClip) = SharedMediaOverlayDocument(
        spineItemIndex = 3,
        textHref = "OEBPS/Text/c.xhtml",
        clips = clips.toList(),
        declaredDurationMs = null
    )

    // --- the clamp ---------------------------------------------------------------------------

    /**
     * `RS §9.2.2` twice: a missing `clipEnd` means "to the end of the media", and a `clipEnd` past
     * the end of the media *is* the end of the media. Both are load-bearing, and each is a silent
     * desync when wrong.
     */
    @Test
    fun `clipEnd past the end of the media is clamped to it`() {
        val c = clip(0, beginMs = 0, endMs = 60_000)
        assertEquals(30_000L, sharedMediaOverlayClipEndMs(c, mediaDurationMs = 30_000))
    }

    @Test
    fun `a missing clipEnd becomes the media duration when it is known`() {
        val c = clip(0, beginMs = 1_000, endMs = null)
        assertEquals(30_000L, sharedMediaOverlayClipEndMs(c, mediaDurationMs = 30_000))
    }

    /**
     * With no known duration the declared value is kept. Clamping against an assumed zero would
     * silence the book, and this code does not decode audio so it has nothing better to offer.
     */
    @Test
    fun `an unknown media duration leaves the declared bounds alone`() {
        val bounded = clip(0, beginMs = 0, endMs = 60_000)
        assertEquals(60_000L, sharedMediaOverlayClipEndMs(bounded, mediaDurationMs = null))
        val unbounded = clip(0, beginMs = 0, endMs = null)
        assertNull(sharedMediaOverlayClipEndMs(unbounded, mediaDurationMs = null))
    }

    /** A zero duration is "unknown", not "instantly over" — collapsing it would drop every clip. */
    @Test
    fun `a zero or negative media duration is treated as unknown`() {
        val c = clip(0, beginMs = 0, endMs = 5_000)
        assertEquals(5_000L, sharedMediaOverlayClipEndMs(c, mediaDurationMs = 0))
        assertEquals(5_000L, sharedMediaOverlayClipEndMs(c, mediaDurationMs = -1))
    }

    @Test
    fun `a clipEnd inside the media is untouched`() {
        val c = clip(0, beginMs = 1_000, endMs = 2_000)
        assertEquals(2_000L, sharedMediaOverlayClipEndMs(c, mediaDurationMs = 30_000))
    }

    // --- what plays --------------------------------------------------------------------------

    @Test
    fun `every well formed clip plays`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(
                clip(0, 0, 2_000),
                clip(1, 2_000, 5_000),
                clip(2, 5_000, null)
            ),
            mediaDurationMsByPath = mapOf("OEBPS/Audio/a.mp3" to 8_000)
        )
        assertFalse(plan.isEmpty)
        assertEquals(listOf(0, 1, 2), plan.sourceClipIndices)
        // 2000 + 3000 + 3000 = 8000, the whole file.
        assertEquals(8_000L, plan.totalDurationMs)
    }

    /**
     * A `par` with `text` but no `audio` is in-spec and would be spoken by a TTS engine. This is not
     * that engine, so letting one through would park the player on a silent item.
     */
    @Test
    fun `a clip with no audio is dropped`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(
                clip(0, 0, 2_000),
                clip(1, 2_000, 4_000, audioPath = null),
                clip(2, 4_000, 6_000)
            )
        )
        assertEquals(listOf(0, 2), plan.sourceClipIndices)
    }

    @Test
    fun `a clip whose audio path did not resolve is dropped`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(clip(0, 0, 2_000), clip(1, 2_000, 4_000, audioPath = "  "))
        )
        assertEquals(listOf(0), plan.sourceClipIndices)
    }

    /** A zero-length clip would leave the player parked, so it never enters the sequence. */
    @Test
    fun `a zero or negative length clip is dropped`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(
                clip(0, 0, 0),
                clip(1, 1_000, 1_000),
                clip(2, 2_000, 1_000),
                clip(3, 3_000, 4_000)
            )
        )
        assertEquals(listOf(3), plan.sourceClipIndices)
    }

    @Test
    fun `a clip starting past the end of its media is dropped`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(
                clip(0, 0, 2_000),
                clip(1, 40_000, 45_000)
            ),
            mediaDurationMsByPath = mapOf("OEBPS/Audio/a.mp3" to 30_000)
        )
        assertEquals(listOf(0), plan.sourceClipIndices)
    }

    /**
     * A dropped clip leaves a hole in the source indices, which is why the plan carries both lists
     * rather than pretending the surviving clips renumbered themselves.
     */
    @Test
    fun `a plan maps source clip indices back and forth`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(clip(0, 0, 2_000), clip(1, 2_000, 4_000, audioPath = null), clip(2, 4_000, 6_000))
        )
        assertEquals(0, plan.playbackIndexOf(0))
        assertEquals(1, plan.playbackIndexOf(2))
        assertEquals(-1, plan.playbackIndexOf(1))
        assertEquals(clip(0, 0, 2_000).clipIndex, plan.clipAt(0)?.clipIndex)
        assertNull(plan.clipAt(9))
    }

    /**
     * An unknown media duration means the total is unknown, not zero — a zero would look finished.
     * A *known* duration with a missing `clipEnd` is the opposite case and does resolve: the clip
     * runs to the end of the file, so the total is knowable.
     */
    @Test
    fun `an unknown total is reported as null rather than summed from guesses`() {
        assertNull(sharedMediaOverlayPlaybackPlan(document(clip(0, 0, 2_000))).totalDurationMs)
        val unbounded = sharedMediaOverlayPlaybackPlan(document(clip(0, 0, null)), mapOf("OEBPS/Audio/a.mp3" to 9_000))
        assertEquals(9_000L, unbounded.totalDurationMs)
        // Unknown duration *and* no clipEnd: nothing to sum.
        assertNull(sharedMediaOverlayPlaybackPlan(document(clip(0, 0, null))).totalDurationMs)
    }

    @Test
    fun `an empty document produces an empty plan`() {
        val plan = sharedMediaOverlayPlaybackPlan(document())
        assertTrue(plan.isEmpty)
        assertNull(plan.totalDurationMs)
        assertEquals(0, sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex = 4))
    }

    // --- stepping ----------------------------------------------------------------------------

    @Test
    fun `next and previous stop at the ends rather than wrapping`() {
        val plan = sharedMediaOverlayPlaybackPlan(document(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000)))
        assertEquals(1, plan.nextAfter(0))
        assertEquals(2, plan.nextAfter(1))
        assertNull(plan.nextAfter(2))
        assertEquals(1, plan.previousBefore(2))
        assertEquals(0, plan.previousBefore(1))
        assertNull(plan.previousBefore(0))
    }

    /**
     * Unlike the audiobook player, "previous" does not restart the current clip. An overlay clip is
     * one sentence or one line, so a restart is a near no-op the user cannot feel; stepping back a
     * clip is what they meant.
     */
    @Test
    fun `previous steps to the previous clip rather than restarting the current one`() {
        val plan = sharedMediaOverlayPlaybackPlan(document(clip(0, 0, 1_000), clip(1, 1_000, 2_000)))
        assertEquals(0, plan.previousBefore(1))
        assertNull(plan.previousBefore(0))
    }

    // --- where playback starts ----------------------------------------------------------------

    @Test
    fun `playback starts at the reader's position`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000), clip(3, 3_000, 4_000))
        )
        assertEquals(2, sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex = 2))
        // Before the first clip starts the first one.
        assertEquals(0, sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex = -5))
        assertEquals(0, sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex = null))
    }

    /** Reading past the last narrated fragment still starts something, rather than nothing. */
    @Test
    fun `a reader position past the end falls back to the last clip`() {
        val plan = sharedMediaOverlayPlaybackPlan(document(clip(0, 0, 1_000), clip(1, 1_000, 2_000)))
        assertEquals(1, sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex = 999))
    }

    /**
     * The start index is a *playback* index, so a reader position in the dropped clip range has to
     * land on the next surviving clip rather than one position out.
     */
    @Test
    fun `a dropped clip does not shift the start position`() {
        val plan = sharedMediaOverlayPlaybackPlan(
            document(clip(0, 0, 1_000), clip(1, 1_000, 2_000, audioPath = null), clip(2, 2_000, 3_000))
        )
        // Reader is on source clip 2, which is playback index 1.
        assertEquals(1, sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex = 2))
    }

    // --- what plays after this chapter ---------------------------------------------------------

    /**
     * A narrated book has un-narrated spine items inside it — a cover, a colophon, a playlist — and
     * stopping at the first one would make continuation almost never fire. They are skipped.
     */
    @Test
    fun `continuation skips spine items that do not narrate`() {
        val index = index(narrated = listOf(0, 2, 3))

        assertEquals(
            listOf(2, 3),
            sharedMediaOverlaySpineItemsAfter(index, listOf(0, 1, 2, 3), finishedSpineItemIndex = 0)
        )
    }

    /** The whole tail comes back, so a caller can skip one whose SMIL turns out to be unusable. */
    @Test
    fun `continuation returns every narrated item after this one in reading order`() {
        val index = index(narrated = listOf(0, 1, 2))

        assertEquals(
            listOf(2),
            sharedMediaOverlaySpineItemsAfter(index, listOf(0, 1, 2), finishedSpineItemIndex = 1)
        )
    }

    @Test
    fun `continuation ends at the last narrated chapter`() {
        val index = index(narrated = listOf(0, 1))

        assertTrue(sharedMediaOverlaySpineItemsAfter(index, listOf(0, 1, 2), finishedSpineItemIndex = 1).isEmpty())
        assertTrue(sharedMediaOverlaySpineItemsAfter(index, listOf(0, 1, 2), finishedSpineItemIndex = 2).isEmpty())
    }

    /**
     * A reading order that does not contain the finished item is a book replaced under a live
     * session. Continuing from a guess would narrate the wrong chapter, so there is nothing to do.
     */
    @Test
    fun `an unknown finished item continues nowhere`() {
        val index = index(narrated = listOf(0, 1))

        assertTrue(sharedMediaOverlaySpineItemsAfter(index, listOf(0, 1), finishedSpineItemIndex = 7).isEmpty())
        assertTrue(sharedMediaOverlaySpineItemsAfter(index, emptyList(), finishedSpineItemIndex = 0).isEmpty())
    }

    /** A book with no overlays at all has nowhere to continue to, however long its spine is. */
    @Test
    fun `a book that does not narrate has no continuation`() {
        val index = index(narrated = emptyList())

        assertTrue(sharedMediaOverlaySpineItemsAfter(index, listOf(0, 1), finishedSpineItemIndex = 0).isEmpty())
    }

    /**
     * The reading order is the reader's, so a document split into several chapters contributes one
     * spine item — and the item *after* it is still found.
     */
    @Test
    fun `continuation reads the order it is given, not the spine array`() {
        val index = index(narrated = listOf(4, 9))

        // Chapter order 4, 4, 9 — the caller deduplicates before asking.
        assertEquals(listOf(9), sharedMediaOverlaySpineItemsAfter(index, listOf(4, 9), finishedSpineItemIndex = 4))
        // A non-ascending spine (a right-to-left book's reader order) still works positionally.
        assertEquals(listOf(4), sharedMediaOverlaySpineItemsAfter(index, listOf(9, 4), finishedSpineItemIndex = 9))
    }

    private fun index(narrated: List<Int>) = SharedMediaOverlayIndex(
        smilPathBySpineItem = narrated.associateWith { "OEBPS/Text/p$it.xhtml.smil" },
        contentPathBySpineItem = emptyMap(),
        smilIdBySpineItem = emptyMap(),
        totalDurationMs = null,
        narrator = null,
        activeClass = null,
        playbackActiveClass = null,
        declaredDurationMsBySpineItem = emptyMap()
    )

    // --- speed -------------------------------------------------------------------------------

    @Test
    fun `speed is clamped into the supported range`() {
        assertEquals(0.5f, sharedMediaOverlaySpeed(0.1f))
        assertEquals(3.0f, sharedMediaOverlaySpeed(9f))
        assertEquals(1.5f, sharedMediaOverlaySpeed(1.5f))
    }

    /** A NaN speed from a bad restore would otherwise poison every comparison downstream. */
    @Test
    fun `a nonsensical speed resets to the default`() {
        assertEquals(SharedMediaOverlayDefaultSpeed, sharedMediaOverlaySpeed(Float.NaN))
        assertEquals(SharedMediaOverlayDefaultSpeed, sharedMediaOverlaySpeed(Float.POSITIVE_INFINITY))
    }

    /**
     * The offered ladder has to be usable by the engine as-is: every entry inside the supported
     * range, ascending, and containing the default. A ladder entry the engine would clamp is a button
     * that lies about what is playing.
     */
    @Test
    fun `the offered speeds are all playable and ascending`() {
        assertTrue(SharedMediaOverlaySpeeds.isNotEmpty())
        assertTrue(SharedMediaOverlaySpeeds.all { it in SharedMediaOverlaySpeedRange })
        assertTrue(SharedMediaOverlaySpeeds.contains(SharedMediaOverlayDefaultSpeed))
        assertEquals(SharedMediaOverlaySpeeds.sorted(), SharedMediaOverlaySpeeds)
        // Every offered speed must clamp to itself, or the button would promise a pace the engine
        // silently changes.
        assertEquals(SharedMediaOverlaySpeeds, SharedMediaOverlaySpeeds.map(::sharedMediaOverlaySpeed))
    }

    /**
     * The label is what the reader sees on the button, so it must not be formatted by the platform:
     * one locale writes `1,5` where another writes `1.5`, and the trailing zeros are noise.
     */
    @Test
    fun `a speed label is trimmed and clamped`() {
        assertEquals("1×", sharedMediaOverlaySpeedLabel(1.0f))
        assertEquals("1.5×", sharedMediaOverlaySpeedLabel(1.5f))
        assertEquals("1.25×", sharedMediaOverlaySpeedLabel(1.25f))
        assertEquals("0.75×", sharedMediaOverlaySpeedLabel(0.75f))
        assertEquals("2×", sharedMediaOverlaySpeedLabel(2.0f))
        // Out of range and nonsense values label what will really play, not what was asked for.
        assertEquals("3×", sharedMediaOverlaySpeedLabel(9f))
        assertEquals("0.5×", sharedMediaOverlaySpeedLabel(0.1f))
        assertEquals("1×", sharedMediaOverlaySpeedLabel(Float.NaN))
    }

    // --- state -------------------------------------------------------------------------------

    private fun state(
        positionMs: Long = 0,
        startMs: Long = 0,
        endMs: Long? = 1_000
    ) = SharedMediaOverlayPlaybackState(
        isPlaying = true,
        positionMs = positionMs,
        clipStartMs = startMs,
        clipEndMs = endMs
    )

    @Test
    fun `clip progress is measured from the clip start not the file start`() {
        // A clip spanning 900..2900 (2000ms), playing 500ms in.
        assertEquals(0.25f, state(positionMs = 1_400, startMs = 900, endMs = 2_900).clipProgress, 1e-4f)
    }

    @Test
    fun `clip progress is clamped and survives a degenerate clip`() {
        assertEquals(0f, state(positionMs = -100, startMs = 0, endMs = 1_000).clipProgress)
        assertEquals(1f, state(positionMs = 99_999, startMs = 0, endMs = 1_000).clipProgress)
        // No known end: nothing to be a fraction of.
        assertEquals(0f, state(endMs = null).clipProgress)
        assertEquals(0f, state(startMs = 500, endMs = 500).clipProgress)
    }
}