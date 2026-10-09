package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity item B1 — the playback anchor type.
 *
 * Every case here pins Android's existing `TtsHighlightInfo` behaviour, because this type replaced
 * that class and Android's six paint paths were rewritten onto it. The point of the rewrite was to
 * delete the duplicated intersection, not to change what gets painted, so a behaviour change would
 * be a regression in a read-aloud feature that has nothing to do with parity.
 */
class SharedPlaybackFragmentTest {

    private fun fragment(cfi: String? = "/6/4", start: Int = 100, length: Int = 50) =
        SharedPlaybackFragment.ofLength(cfi, start, length)

    // --- shape -------------------------------------------------------------------------------

    @Test
    fun `ofLength builds an absolute range and reports its length`() {
        val f = fragment(start = 100, length = 50)
        assertEquals(100, f.startAbs)
        assertEquals(150, f.endAbs)
        assertEquals(50, f.length)
        assertFalse(f.isEmpty)
    }

    @Test
    fun `a negative length yields an empty fragment rather than a backwards range`() {
        val f = fragment(start = 100, length = -5)
        assertEquals(0, f.length)
        assertTrue(f.isEmpty)
        assertEquals(100, f.startAbs)
        assertEquals(100, f.endAbs)
    }

    @Test
    fun `a zero length fragment is empty and paints nothing`() {
        assertTrue(fragment(length = 0).isEmpty)
        assertNull(sharedPlaybackFragmentRangeInBlock(fragment(length = 0), "/6/4", 0, 500))
    }

    // --- the intersection ---------------------------------------------------------------------

    @Test
    fun `a fragment inside one block resolves to block-local offsets`() {
        // Block starts at 100 and is 500 long; fragment covers 120..170.
        assertEquals(
            20 until 70,
            sharedPlaybackFragmentRangeInBlock(fragment(start = 120, length = 50), "/6/4", 100, 500)
        )
    }

    @Test
    fun `a fragment starting before the block is clipped to the block start`() {
        // Fragment 80..180 against block 100..600 paints 100..180, i.e. block-local 0..80.
        assertEquals(
            0 until 80,
            sharedPlaybackFragmentRangeInBlock(fragment(start = 80, length = 100), "/6/4", 100, 500)
        )
    }

    @Test
    fun `a fragment ending past the block is clipped to the block end`() {
        assertEquals(
            350 until 500,
            sharedPlaybackFragmentRangeInBlock(fragment(start = 450, length = 200), "/6/4", 100, 500)
        )
    }

    /**
     * Only the block the fragment names is painted, and that block's own bounds clamp it.
     *
     * This is Android's behaviour, not a limitation introduced here: the paint sites all guarded on
     * `block.cfi == ttsHighlightInfo.cfi`, so a fragment paints in exactly one block even when its
     * absolute range overlaps its neighbours. Worth pinning because it looks like a bug at a glance
     * and the fix would be a visible read-aloud change — TTS chunk boundaries would start painting
     * a second block.
     */
    @Test
    fun `a fragment paints in the block it names and no other`() {
        // 100..150, overlapping three blocks' spans, but anchored to /6/4 only.
        val f = fragment(start = 100, length = 50)
        assertEquals(0 until 50, sharedPlaybackFragmentRangeInBlock(f, "/6/4", 100, 500))
        // Blocks that merely overlap the same absolute range are not painted.
        assertNull(sharedPlaybackFragmentRangeInBlock(f, "/6/6", 90, 500))
        assertNull(sharedPlaybackFragmentRangeInBlock(f, "/6/8", 120, 500))
    }

    @Test
    fun `touching is not overlapping so a chunk boundary paints no sliver`() {
        // Fragment ends exactly at the next block's first character.
        val f = fragment(start = 60, length = 40) // 60..100
        assertNull(sharedPlaybackFragmentRangeInBlock(f, "/6/6", 100, 400))
    }

    @Test
    fun `a block entirely before the fragment does not paint`() {
        assertNull(sharedPlaybackFragmentRangeInBlock(fragment(start = 500), "/6/4", 100, 200))
    }

    // --- block identity ----------------------------------------------------------------------

    @Test
    fun `only the block whose cfi matches is highlighted`() {
        val f = fragment(cfi = "/6/4", start = 100, length = 50)
        assertEquals(0 until 50, sharedPlaybackFragmentRangeInBlock(f, "/6/4", 100, 500))
        assertNull(sharedPlaybackFragmentRangeInBlock(f, "/6/6", 100, 500))
    }

    /**
     * A block with no cfi must never be painted, even when the fragment also has none. Android
     * compared `block.cfi == ttsHighlightInfo.cfi` where the fragment's cfi was a non-null `String`,
     * so this case was unreachable there — but it is the case that would paint every cfi-less block
     * in the chapter with the live chunk if nulls were treated as equal.
     */
    @Test
    fun `a null cfi on either side never matches`() {
        assertNull(sharedPlaybackFragmentRangeInBlock(fragment(cfi = null), null, 100, 500))
        assertNull(sharedPlaybackFragmentRangeInBlock(fragment(cfi = null), "/6/4", 100, 500))
        assertNull(sharedPlaybackFragmentRangeInBlock(fragment(cfi = "/6/4"), null, 100, 500))
        assertFalse(fragment(cfi = null).matchesBlock(null))
        assertFalse(fragment(cfi = null).matchesBlock("/6/4"))
        assertFalse(fragment(cfi = "/6/4").matchesBlock(null))
    }

    @Test
    fun `an empty cfi string is treated as absent`() {
        // Android's fragment cfi defaulted to "" at the call site when `sourceCfi` was null, and the
        // whole fragment was then dropped upstream. Treating "" as absent keeps that guarantee here.
        assertFalse(fragment(cfi = "").matchesBlock(""))
        assertFalse(fragment(cfi = "").matchesBlock("/6/4"))
        assertNull(sharedPlaybackFragmentRangeInBlock(fragment(cfi = ""), "", 100, 500))
    }

    @Test
    fun `matchesBlock agrees with the range resolver`() {
        val f = fragment(cfi = "/6/4", start = 100, length = 50)
        for (blockCfi in listOf("/6/4", "/6/6", null, "")) {
            val matches = f.matchesBlock(blockCfi)
            val resolves = sharedPlaybackFragmentRangeInBlock(f, blockCfi, 100, 500) != null
            assertEquals(resolves, matches, "disagreement for cfi=$blockCfi")
        }
    }

    // --- degenerate inputs -------------------------------------------------------------------

    @Test
    fun `a null fragment resolves to nothing`() {
        assertNull(sharedPlaybackFragmentRangeInBlock(null, "/6/4", 100, 500))
    }

    @Test
    fun `a zero or negative block length paints nothing`() {
        val f = fragment(start = 100, length = 50)
        assertNull(sharedPlaybackFragmentRangeInBlock(f, "/6/4", 100, 0))
        assertNull(sharedPlaybackFragmentRangeInBlock(f, "/6/4", 100, -10))
    }

    @Test
    fun `a block starting before the origin still resolves`() {
        // Block -50..450 contains 100..150, so block-local 150..200.
        assertEquals(150 until 200, sharedPlaybackFragmentRangeInBlock(fragment(), "/6/4", -50, 500))
    }

    // --- painting ----------------------------------------------------------------------------

    private val band = Color(0x80FF0000)

    private fun annotated(text: String): AnnotatedString = buildAnnotatedString { append(text) }

    @Test
    fun `the painted span covers exactly the fragment's share of the block`() {
        val painted = annotated("0".repeat(500)).withPlaybackFragmentBackground(
            fragment = fragment(start = 120, length = 50),
            blockCfi = "/6/4",
            blockStartAbs = 100,
            blockLength = 500,
            color = band
        )
        val spans = painted.spanStyles.filter { it.item.background == band }
        assertEquals(1, spans.size)
        assertEquals(20, spans[0].start)
        assertEquals(70, spans[0].end)
    }

    /**
     * Paint-only by construction: the fragment adds a background and **no string annotation**, so it
     * can never be tapped or selected. This is the same guarantee the transient playback band gives
     * on the highlight path (`UserHighlight.isTransientPlaybackBand`), and it is why a fragment is
     * not modelled as a `UserHighlight`.
     */
    @Test
    fun `the fragment is paint-only and is never annotated`() {
        val painted = annotated("0".repeat(500)).withPlaybackFragmentBackground(
            fragment = fragment(start = 120, length = 50),
            blockCfi = "/6/4",
            blockStartAbs = 100,
            blockLength = 500,
            color = band
        )
        assertEquals(emptyList(), painted.getStringAnnotations(0, painted.length).toList())
    }

    @Test
    fun `an unmatched fragment returns the input unchanged`() {
        val original = annotated("0".repeat(500))
        assertTrue(
            original === original.withPlaybackFragmentBackground(
                fragment = fragment(cfi = "/6/6"),
                blockCfi = "/6/4",
                blockStartAbs = 100,
                blockLength = 500,
                color = band
            )
        )
        assertTrue(
            original === original.withPlaybackFragmentBackground(
                fragment = null,
                blockCfi = "/6/4",
                blockStartAbs = 100,
                blockLength = 500,
                color = band
            )
        )
    }

    /** The text is preserved verbatim: this adds a style, never content. */
    @Test
    fun `painting preserves the text and the spans already on it`() {
        val base = buildAnnotatedString {
            append("0123456789")
            addStyle(SpanStyle(color = Color.Red), start = 2, end = 5)
        }
        val painted = base.withPlaybackFragmentBackground(
            fragment = fragment(start = 0, length = 4),
            blockCfi = "/6/4",
            blockStartAbs = 0,
            blockLength = 10,
            color = band
        )
        assertEquals("0123456789", painted.text)
        // The pre-existing span survives untouched.
        val red = painted.spanStyles.single { it.item.color == Color.Red }
        assertEquals(2, red.start)
        assertEquals(5, red.end)
        // And the band is added after it, which is the order every call site used: the search fill
        // first, then the playback band on top. Reversing it would change which fill wins on an
        // overlap, so the ordering is part of the contract rather than an accident.
        val bandSpan = painted.spanStyles.single { it.item.background == band }
        assertEquals(0, bandSpan.start)
        assertEquals(4, bandSpan.end)
        assertEquals(1, painted.spanStyles.indexOf(bandSpan))
    }
}