package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.playbackBandHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The media overlay's paintable band.
 *
 * This is the seam that lets a new playback engine reuse the read-aloud paint path instead of
 * touching six of them. So the tests are mostly about the *contract* rather than the arithmetic: the
 * band must be indistinguishable from read-aloud's to every painter and hit-tester, and must not be
 * mistaken for a highlight the reader made.
 */
class SharedMediaOverlayPlaybackBandTest {

    private fun projection(
        chapterIndex: Int? = 2,
        clipIndex: Int = 7,
        fragment: SharedPlaybackFragment? = SharedPlaybackFragment("/2/4/3", 120, 168)
    ) = SharedMediaOverlayProjection(
        spineItemIndex = 4,
        clipIndex = clipIndex,
        chapterIndex = chapterIndex,
        fragment = fragment
    )

    // --- shape ------------------------------------------------------------------------------

    @Test
    fun `the band is a transient playback band and not a reader highlight`() {
        val highlight = sharedMediaOverlayBandHighlight(projection(), sessionId = 9L)
        assertNotNull(highlight)
        assertTrue(highlight.isTransientPlaybackBand)
        // That one property is what stops the selection sheet and hit-testing from claiming it.
        assertFalse(highlight.note != null)
    }

    /**
     * The reason the whole approach works: a media overlay band and a read-aloud band have to be the
     * same shape to every consumer. A painter that special-cased one would now paint them
     * differently, which is exactly the drift this was built to avoid.
     */
    @Test
    fun `the band is shaped exactly like read aloud's`() {
        val chunk = ReaderTtsChunkFixture.chunk()
        val tts = chunk.toHighlight(sessionId = 4L)
        val overlay = assertNotNull(
            sharedMediaOverlayBandHighlight(
                projection = projection(chapterIndex = chunk.chapterIndex, clipIndex = chunk.index),
                sessionId = 4L
            )
        )
        assertEquals(tts.color, overlay.color)
        assertEquals(tts.style, overlay.style)
        assertEquals(tts.chapterIndex, overlay.chapterIndex)
        // Same id layout, so nothing that recognises one needs to learn about the other.
        assertEquals(tts.id.substringBeforeLast('_'), overlay.id.substringBeforeLast('_'))
    }

    /**
     * The read-aloud band must survive being routed through the shared builder unchanged.
     *
     * Refactoring [ReaderTtsChunk.toHighlight] onto the shared path is only safe if it is the same
     * band it always was. It has a `desktop:` cfi fallback that exists only because some engines
     * produce no source cfi, and losing it would leave a spoken chunk with no position at all on
     * every surface that resolves by cfi.
     */
    @Test
    fun `read aloud's band is unchanged by sharing the builder`() {
        val chunk = ReaderTtsChunkFixture.chunk()
        val band = chunk.toHighlight(sessionId = 3L)

        assertEquals("/2/4/3", band.cfi)
        assertEquals(chunk.text, band.text)
        assertEquals(2, band.chapterIndex)
        assertEquals(0, band.locator.pageIndex)
        assertEquals(120, band.locator.startOffset)
        assertEquals(168, band.locator.endOffset)
        assertEquals(chunk.text, band.locator.textQuote)
        assertTrue(band.isTransientPlaybackBand)
    }

    /**
     * The same fallback, for a chunk with no source cfi — which some TTS engines genuinely produce.
     *
     * This is the case that would have broken silently: the band would still look like a band, and
     * would only fail to resolve on surfaces that read the cfi.
     */
    @Test
    fun `read aloud's band keeps its desktop cfi fallback when there is no source cfi`() {
        val band = ReaderTtsChunkFixture.chunk().copy(sourceCfi = null).toHighlight(sessionId = 3L)
        assertEquals("desktop:2:120:168", band.cfi)
        assertEquals("desktop:2:120:168", band.locator.cfi)
    }

    /** A backwards extent must not survive into the locator as a range that resolves to nothing. */
    @Test
    fun `read aloud's backwards extent is coerced forward`() {
        val band = ReaderTtsChunkFixture.chunk().copy(startOffset = 200, endOffset = 100).toHighlight(sessionId = 3L)
        assertEquals(200, band.locator.startOffset)
        assertEquals(200, band.locator.endOffset)
    }

    @Test
    fun `the locator carries the fragment's absolute extent`() {
        val locator = assertNotNull(sharedMediaOverlayPlaybackBand(projection(), sessionId = 1L)).locator
        assertEquals(2, locator.chapterIndex)
        assertEquals(120, locator.startOffset)
        assertEquals(168, locator.endOffset)
        assertEquals("/2/4/3", locator.cfi)
        assertTrue(locator.hasTextRange)
    }

    @Test
    fun `the page index is recorded when known and omitted when not`() {
        assertEquals(5, assertNotNull(sharedMediaOverlayPlaybackBand(projection(), pageIndex = 5, sessionId = 1L)).locator.pageIndex)
        assertNull(assertNotNull(sharedMediaOverlayPlaybackBand(projection(), pageIndex = null, sessionId = 1L)).locator.pageIndex)
    }

    // --- which page the narrated line is on --------------------------------------------------

    /**
     * The page the band records, and the rule for finding it, together — because the recorded value
     * is only as good as the rule, and a reader cannot see the rule while it is wrong.
     */
    @Test
    fun `a fragment resolves to the page that reaches past its start`() {
        val page = assertNotNull(sharedMediaOverlayPageForFragment(pages(), 2, fragment(start = 320)))
        assertEquals(5, assertNotNull(sharedMediaOverlayPlaybackBand(projection(), pageIndex = page, sessionId = 1L)).locator.pageIndex)
    }

    /**
     * The boundary convention, which is the whole reason this is a rule and not a lookup.
     *
     * A page's `endOffset` is exclusive, so a fragment beginning exactly where one page ends is
     * rendered on the *next* page. An inclusive comparison would leave the reader one page behind at
     * every boundary clip — and for a well-produced book that is most of them, since a clip is
     * usually a paragraph and a page break usually falls between paragraphs.
     */
    @Test
    fun `a fragment starting where a page ends belongs to the next page`() {
        assertEquals(6, sharedMediaOverlayPageForFragment(pages(), 2, fragment(start = 400)))
    }

    /**
     * Mid-pagination a chapter has no pages yet, and a guess would be a needless page turn on every
     * clip. Nothing is the better answer, exactly as it is everywhere else in this file.
     */
    @Test
    fun `an unpaginated chapter resolves to no page`() {
        assertNull(sharedMediaOverlayPageForFragment(emptyList(), 2, fragment(start = 320)))
        assertNull(sharedMediaOverlayPageForFragment(pages(), 3, fragment(start = 320)))
        assertNull(sharedMediaOverlayPageForFragment(pages(), null, fragment(start = 320)))
        assertNull(sharedMediaOverlayPageForFragment(pages(), 2, null))
    }

    /** Chapter 2 only, so a wrong chapter index has to fail rather than match by accident. */
    private fun pages() = listOf(
        page(0, 0, 0, 200),
        page(1, 0, 200, 400),
        page(4, 2, 0, 300),
        page(5, 2, 300, 400),
        page(6, 2, 400, 520)
    )

    private fun page(pageIndex: Int, chapterIndex: Int, startOffset: Int, endOffset: Int) = ReaderPage(
        pageIndex = pageIndex,
        chapterIndex = chapterIndex,
        chapterTitle = "Chapter $chapterIndex",
        text = "",
        startOffset = startOffset,
        endOffset = endOffset
    )

    private fun fragment(start: Int, end: Int = start + 20) =
        SharedPlaybackFragment("/2/4/3", start, end)

    // --- when there is nothing to paint -------------------------------------------------------

    /**
     * Nothing playing, or a clip with no anchor. Both are ordinary — most books have no overlay at
     * all, and a `par` with no `text/@src` has audio and nothing to highlight — and both must leave
     * the reader with no band rather than a stale one.
     */
    @Test
    fun `nothing to paint yields no band`() {
        assertNull(sharedMediaOverlayPlaybackBand(null, sessionId = 1L))
        assertNull(sharedMediaOverlayPlaybackBand(projection(fragment = null), sessionId = 1L))
        assertNull(sharedMediaOverlayPlaybackBand(projection(), sessionId = 1L).takeIf { false })
        assertNull(sharedMediaOverlayBandHighlight(null, sessionId = 1L))
    }

    /**
     * An empty fragment is not a zero-width band.
     *
     * A zero-width range would resolve, match nothing, and cost a full chapter walk on the fallback
     * path every clip — the "repair" machinery running pointlessly on the one feature that is
     * supposed not to need it.
     */
    @Test
    fun `an empty fragment yields no band`() {
        assertNull(sharedMediaOverlayPlaybackBand(projection(fragment = SharedPlaybackFragment("/2/4/3", 120, 120)), sessionId = 1L))
        assertNull(sharedMediaOverlayPlaybackBand(projection(fragment = SharedPlaybackFragment("/2/4/3", 168, 120)), sessionId = 1L))
    }

    /**
     * The chapter could not be placed, so the band could not be painted on the right page.
     *
     * Better no band than one on the wrong chapter: a highlight that jumps chapters every clip is
     * more alarming than one that briefly does not appear.
     */
    @Test
    fun `an unplaceable chapter yields no band`() {
        assertNull(sharedMediaOverlayPlaybackBand(projection(chapterIndex = null), sessionId = 1L))
    }

    // --- session identity ----------------------------------------------------------------------

    /**
     * Two sessions must not collide.
     *
     * The id is what hit-testing and the selection sheet would key on, so a collision would let a
     * band from a finished session be edited as though it were a highlight the reader made.
     */
    @Test
    fun `sessions produce distinct bands for the same clip`() {
        val first = assertNotNull(sharedMediaOverlayBandHighlight(projection(), sessionId = 1L))
        val second = assertNotNull(sharedMediaOverlayBandHighlight(projection(), sessionId = 2L))
        assertFalse(first.id == second.id)
        // The clip index has to stay in the id too, or two clips in one session would collide.
        val other = assertNotNull(sharedMediaOverlayBandHighlight(projection(clipIndex = 8), sessionId = 1L))
        assertFalse(first.id == other.id)
    }

    // --- the quote fallback ------------------------------------------------------------------

    private fun chapter(vararg blocks: SemanticParagraph) = SharedEpubChapter(
        id = "c",
        title = "C",
        plainText = "",
        semanticBlocks = blocks.toList()
    )

    private fun paragraph(cfi: String, offset: Int, text: String) = SemanticParagraph(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = cfi,
        startCharOffsetInSource = offset
    )

    @Test
    fun `the quote is read from the block the fragment lands in`() {
        val quote = sharedMediaOverlayTextQuote(
            chapters = listOf(chapter(paragraph("/2/4/0", 0, "zero"), paragraph("/2/4/3", 100, "Hello world."))),
            chapterIndex = 0,
            fragment = SharedPlaybackFragment("/2/4/3", 106, 111)
        )
        assertEquals("world", quote)
    }

    @Test
    fun `a quote spanning a block boundary is not fabricated`() {
        // The fragment runs past the end of its block. Slicing the block's own text would produce a
        // truncated quote that no longer matches, which is worse than none.
        assertNull(
            sharedMediaOverlayTextQuote(
                chapters = listOf(chapter(paragraph("/2/4/0", 0, "short"))),
                chapterIndex = 0,
                fragment = SharedPlaybackFragment("/2/4/0", 2, 99)
            )
        )
    }

    @Test
    fun `a missing block yields no quote`() {
        assertNull(
            sharedMediaOverlayTextQuote(
                chapters = listOf(chapter(paragraph("/2/4/0", 0, "text"))),
                chapterIndex = 0,
                fragment = SharedPlaybackFragment("/2/4/9", 0, 4)
            )
        )
        assertNull(sharedMediaOverlayTextQuote(emptyList(), 0, SharedPlaybackFragment("/2/4/0", 0, 4)))
        assertNull(sharedMediaOverlayTextQuote(listOf(chapter(paragraph("/2/4/0", 0, "t"))), 9, SharedPlaybackFragment("/2/4/0", 0, 1)))
        assertNull(sharedMediaOverlayTextQuote(listOf(chapter(paragraph("/2/4/0", 0, "t"))), 0, null))
    }

    // --- the gate ------------------------------------------------------------------------------

    /**
     * The gate has to be free. It is evaluated for every book in the library, so it reads only the
     * OPF-built index and never parses a SMIL body.
     */
    @Test
    fun `the overlay is offered only for a book that has one`() {
        val narrated = SharedMediaOverlayIndex(
            smilPathBySpineItem = mapOf(0 to "a.smil"),
            smilIdBySpineItem = mapOf(0 to "a"),
            totalDurationMs = null,
            narrator = null,
            activeClass = null,
            playbackActiveClass = null,
            declaredDurationMsBySpineItem = emptyMap()
        )
        assertTrue(sharedMediaOverlayIsOffered(narrated, playButtonVisible = true))
        assertFalse(sharedMediaOverlayIsOffered(SharedMediaOverlayIndex.EMPTY, playButtonVisible = true))
        assertFalse(sharedMediaOverlayIsOffered(null, playButtonVisible = true))
        // Nothing to play it with, so nothing to offer.
        assertFalse(sharedMediaOverlayIsOffered(narrated, playButtonVisible = false))
    }

    @Test
    fun `a missing narrator yields no label rather than an empty one`() {
        val narrated = SharedMediaOverlayIndex(
            smilPathBySpineItem = mapOf(0 to "a.smil"),
            smilIdBySpineItem = mapOf(0 to "a"),
            totalDurationMs = null,
            narrator = "Chris Hughes",
            activeClass = null,
            playbackActiveClass = null,
            declaredDurationMsBySpineItem = emptyMap()
        )
        assertEquals("Narrated by Chris Hughes", sharedMediaOverlayNarrationLabel(narrated))
        assertNull(sharedMediaOverlayNarrationLabel(SharedMediaOverlayIndex.EMPTY))
        assertNull(sharedMediaOverlayNarrationLabel(narrated.copy(narrator = "  ")))
        assertNull(sharedMediaOverlayNarrationLabel(null))
    }

    // --- the widened prefix --------------------------------------------------------------------

    /**
     * The prefix widened from `tts_` to `playback_` when media overlays arrived. A band produced
     * before the widening — i.e. by a session already running when the app updated — must still be
     * recognised, or the reader loses the highlight they are currently looking at.
     */
    @Test
    fun `bands from the previous prefix are still transient`() {
        val legacy = assertNotNull(sharedMediaOverlayBandHighlight(projection(), sessionId = 1L))
            .copy(id = "tts_1_7")
        assertTrue(legacy.isTransientPlaybackBand)
    }

    @Test
    fun `a reader's own highlight is not transient`() {
        val readerHighlight = UserHighlight(
            id = "hl_1",
            cfi = "/2/4/3",
            text = "Hello",
            color = HighlightColor.YELLOW,
            chapterIndex = 2,
            locator = ReaderLocator(chapterIndex = 2)
        )
        assertFalse(readerHighlight.isTransientPlaybackBand)
    }

    @Test
    fun `both producers agree on the prefix so consumers cannot disagree`() {
        // The read-aloud producer and the media overlay producer, read through the same constant the
        // consumers read. If these ever diverged, half the painters would draw a band the other half
        // treated as a real highlight.
        val ttsId = ReaderTtsChunkFixture.chunk().toHighlight(sessionId = 1L).id
        val overlayId = assertNotNull(sharedMediaOverlayBandHighlight(projection(), sessionId = 1L)).id
        assertTrue(ttsId.startsWith(com.aryan.reader.shared.TRANSIENT_BAND_ID_PREFIX))
        assertTrue(overlayId.startsWith(com.aryan.reader.shared.TRANSIENT_BAND_ID_PREFIX))
    }
}

/** A read-aloud chunk shaped like a real one, so the parity assertions above are meaningful. */
private object ReaderTtsChunkFixture {
    fun chunk() = com.aryan.reader.shared.ReaderTtsChunk(
        index = 7,
        pageIndex = 0,
        chapterIndex = 2,
        chapterTitle = "Sonnet VII",
        text = "world",
        startOffset = 120,
        endOffset = 168,
        sourceCfi = "/2/4/3"
    )
}