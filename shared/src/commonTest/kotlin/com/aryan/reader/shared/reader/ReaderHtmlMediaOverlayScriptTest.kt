package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The media overlay half of the WebView reader.
 *
 * The script is a string, so the only automated way to hold it to anything is by asserting on what
 * it contains. That is normally weak, and it is worth it here for three specific claims that would
 * each be a real bug and would otherwise only show up as "the highlight looks wrong" on a WebView
 * device:
 *
 * - the overlay and read-aloud keep separate paint, so a handoff between them does not delete one
 *   surface's highlight with the other's;
 * - the overlay does **not** quote-match, because its anchors are exact and a fuzzy fallback would
 *   mask a wrong offset rather than reveal it;
 * - the overlay uses its own, much tighter follow margin, because a clip is a line.
 *
 * Each is asserted on the isolated block rather than the whole script, so a read-aloud change cannot
 * make one of them pass by accident.
 */
class ReaderHtmlMediaOverlayScriptTest {

    private val script = readerHtmlAnnotationScript()

    /** The media overlay block only, so assertions cannot be satisfied by read-aloud code. */
    private val mediaOverlayBlock: String = run {
        val start = script.indexOf(MEDIA_OVERLAY_MARKER)
        assertTrue(start > 0, "the media overlay block is missing from the annotation script")
        val end = script.indexOf("function highlightRange(", start)
        assertTrue(end > start, "the media overlay block has no end, so the extraction is wrong")
        script.substring(start, end)
    }

    // --- the entry point ---------------------------------------------------------------------

    @Test
    fun `the script exposes a media overlay entry point`() {
        assertTrue(script.contains("window.readerSetMediaOverlayFragment"))
    }

    @Test
    fun `the entry point takes a fragment and a follow flag`() {
        // Both arguments are load-bearing: the caller decides *whether* to ask for a scroll, which
        // is how a chapter change scrolls and a line change does not.
        assertTrue(script.contains("window.readerSetMediaOverlayFragment = function (fragment, follow)"))
    }

    // --- isolation from read-aloud -------------------------------------------------------------

    /**
     * Separate paint, separate CSS highlight name.
     *
     * Sharing one name means clearing read-aloud's highlight on a handoff also clears the overlay's,
     * and whichever surface is mid-transition loses its highlight until the next clip — a flash of
     * unhighlighted text while a narrator is speaking.
     */
    @Test
    fun `the overlay has its own highlight name and layer`() {
        assertTrue(mediaOverlayBlock.contains("'reader-media-overlay-highlight'"))
        assertTrue(mediaOverlayBlock.contains("reader-media-overlay-highlight-layer"))
        assertTrue(mediaOverlayBlock.contains("reader-media-overlay-highlight-rect"))
        // The read-aloud names must not appear in the overlay's own paint path.
        assertFalse(
            mediaOverlayBlock.contains("CSS.highlights.delete('reader-tts-highlight')"),
            "the overlay must not delete the read-aloud highlight"
        )
        assertFalse(
            mediaOverlayBlock.contains("reader-tts-highlight-layer"),
            "the overlay must not paint into the read-aloud layer"
        )
    }

    /**
     * No quote fallback.
     *
     * `normalizedRangeForText` is the repair path for anchors recorded against a different reflow.
     * An overlay anchor comes from the same parse that produced the blocks, so it is already exact;
     * quoting it in would let a genuinely wrong offset be papered over with a fuzzy match instead of
     * being visible as a highlight in the wrong place.
     */
    @Test
    fun `the overlay does not repair its anchors by quoting`() {
        assertFalse(
            mediaOverlayBlock.contains("normalizedRangeForText"),
            "the overlay must resolve by offset and cfi only, never by quote"
        )
        assertFalse(mediaOverlayBlock.contains("textQuote"))
    }

    @Test
    fun `the overlay resolves by chapter offset and cfi`() {
        assertTrue(mediaOverlayBlock.contains("rangeForOffsets("))
        assertTrue(mediaOverlayBlock.contains("fragment.cfi"))
        assertTrue(mediaOverlayBlock.contains("fragment.chapterIndex"))
    }

    // --- the follow rule ---------------------------------------------------------------------

    /**
     * A line needs to be on screen, not comfortably inside the viewport.
     *
     * Read-aloud's 12% band is tuned for sentences. A clip is a line, so it leaves that band on
     * nearly every change, and following on that would re-centre the page continuously.
     */
    @Test
    fun `the overlay follows with its own tighter margin`() {
        assertTrue(mediaOverlayBlock.contains("readerMediaOverlayFollowViewportMarginRatio"))
        assertFalse(
            mediaOverlayBlock.contains("readerTtsFollowViewportMarginRatio"),
            "the overlay must not borrow read-aloud's sentence-sized margin"
        )
        val navigation = readerHtmlNavigationScript(pageAnchorJson = "[]")
        assertTrue(navigation.contains("readerMediaOverlayFollowViewportMarginRatio"))
        // Asserted as a number, not a name: a ratio raised to read-aloud's value would reintroduce
        // the stutter while every name in this test still passed.
        val declared = Regex("""readerMediaOverlayFollowViewportMarginRatio\s*=\s*([0-9.]+)""")
            .find(navigation)?.groupValues?.get(1)?.toDoubleOrNull()
        assertTrue(declared != null && declared < 0.05, "overlay follow margin was $declared, expected well under the read-aloud 0.12")
    }

    @Test
    fun `the overlay scrolls only when asked to`() {
        assertTrue(mediaOverlayBlock.contains("if (follow && fragment && mediaOverlayFragmentNeedsFollowScroll(fragment))"))
    }

    /**
     * A repaint on resize, matching read-aloud's. Without it the overlay's rectangles stay where
     * they were painted and detach from the text the first time the reader rotates the device.
     */
    @Test
    fun `the overlay repaints on resize`() {
        assertTrue(script.contains("refreshMediaOverlayHighlight"))
        assertTrue(mediaOverlayBlock.contains("readerMediaOverlayOverlayTimer"))
    }

    private companion object {
        const val MEDIA_OVERLAY_MARKER = "// --- EPUB media overlays"
    }
}