package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.reader.SharedMediaOverlayProjection
import com.aryan.reader.shared.reader.SharedPlaybackFragment
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the WebView surface is told about the narrated fragment.
 *
 * The vertical WebView paints and scrolls through one bridge call, so the whole of the surface's
 * behaviour is decided by the two arguments this produces. Getting either wrong is visible: a
 * fragment sent without its chapter scrolls the wrong document, and a cleared fragment that is not
 * cleared leaves the last narrated line painted after the audio stopped.
 *
 * Separate from `SharedMediaOverlayPlaybackBandTest`, which covers what the *native* surfaces are
 * given. The two cannot be merged because the WebView needs no band at all — it has the document.
 */
class SharedMobileEpubMediaOverlayFollowTest {

    /**
     * The fragment travels with its chapter.
     *
     * Offsets are chapter-absolute, so a fragment without its chapter names a position in an unknown
     * document and the script would scroll the wrong chapter. `null` is the alternative, and it is
     * the safe one: the script treats it as "clear", which leaves the reader where they are rather
     * than somewhere wrong.
     */
    @Test
    fun `a fragment with no chapter clears the band rather than scrolling`() {
        val script = sharedMobileEpubMediaOverlayFragmentScript(
            projection(fragment = fragment(start = 10), chapterIndex = null)
        )
        assertTrue(script.endsWith("readerSetMediaOverlayFragment(null, false);"), script)
    }

    /** The cfi is optional — offsets alone place a range — and an absent one must be omitted. */
    @Test
    fun `a fragment without a cfi still paints by offsets`() {
        val script = sharedMobileEpubMediaOverlayFragmentScript(
            projection(fragment = fragment(start = 10, end = 40, cfi = null))
        )
        assertFalse(script.contains("cfi"), "A null cfi must be omitted rather than serialized: $script")
        assertTrue(script.contains("\"startOffset\":10"), script)
        assertTrue(script.contains("\"endOffset\":40"), script)
    }

    /**
     * The script is asked to follow, and decides for itself whether to scroll.
     *
     * It measures the fragment against the real viewport, so a chapter change scrolls and a line
     * already on screen does not. Android's WebView path asks only on a chapter change
     * (`EpubReaderScreen.kt:1997`) because it anchors by element id and has nothing to measure
     * against; here the measurement is available, and asking only on a chapter change would leave
     * the reader watching narration that had scrolled off the page.
     */
    @Test
    fun `an anchored fragment asks the script to keep it on screen`() {
        val script = sharedMobileEpubMediaOverlayFragmentScript(
            projection(fragment = fragment(start = 10), chapterIndex = 2)
        )
        assertTrue(script.endsWith("}, true);"), script)
    }

    /**
     * The clearing call matters. Without it the last narrated line stays painted after the audio
     * stopped, which reads as a highlight the reader cannot dismiss.
     */
    @Test
    fun `a session that has ended clears the band`() {
        val script = sharedMobileEpubMediaOverlayFragmentScript(projection = null)
        assertTrue(script.endsWith("readerSetMediaOverlayFragment(null, false);"), script)
    }

    /**
     * Guarded on the bridge being installed, because this script is composed into documents that may
     * predate it — a reader whose chapter HTML was rendered by an older build has no such function,
     * and calling it unconditionally would throw on every clip.
     */
    @Test
    fun `the call is guarded on the bridge being installed`() {
        assertTrue(
            sharedMobileEpubMediaOverlayFragmentScript(projection = null)
                .startsWith("if (window.readerSetMediaOverlayFragment) ")
        )
    }

    // --- which engine owns the band -----------------------------------------------------------

    /**
     * Narration wins over read-aloud, and only one of them can be live anyway.
     *
     * The arbiter stops one engine to start the other, so at most one of these is ever non-null in
     * practice. The order still has to be pinned rather than left to argument order, because the
     * native surfaces already answer this question the other way round in `playbackHighlights` —
     * and a WebView band owned by a different engine than the native band on the same book is the
     * kind of divergence that only shows up as "the highlight is on the wrong words".
     */
    @Test
    fun `narration owns the band over read-aloud`() {
        val script = sharedMobileEpubPlaybackBandScript(
            mediaOverlayProjection = projection(fragment = fragment(start = 10)),
            ttsLocator = ReaderLocator(chapterIndex = 2, startOffset = 99, textQuote = "spoken")
        )
        assertTrue(script.contains("readerSetMediaOverlayFragment"), script)
        assertFalse(script.contains("readerSetTtsLocator"), script)
    }

    /** Read-aloud still paints through its own bridge call, local or cloud engine alike. */
    @Test
    fun `read-aloud owns the band when nothing is narrated`() {
        val script = sharedMobileEpubPlaybackBandScript(
            mediaOverlayProjection = null,
            ttsLocator = ReaderLocator(chapterIndex = 2, startOffset = 99, textQuote = "spoken")
        )
        assertTrue(script.contains("readerSetTtsLocator"), script)
        assertTrue(script.contains("\"startOffset\":99"), script)
    }

    /** Nothing live is an explicit clear for whichever engine last held the band, not a no-op. */
    @Test
    fun `neither engine running clears the band`() {
        val script = sharedMobileEpubPlaybackBandScript(
            mediaOverlayProjection = null,
            ttsLocator = null
        )
        assertTrue(script.contains("readerSetTtsLocator(null, true)"), script)
    }

    private fun projection(
        fragment: SharedPlaybackFragment?,
        chapterIndex: Int? = 2
    ) = SharedMediaOverlayProjection(
        spineItemIndex = 2,
        clipIndex = 0,
        chapterIndex = chapterIndex,
        fragment = fragment
    )

    private fun fragment(
        start: Int,
        end: Int = start + 20,
        cfi: String? = "/2/c2/b3"
    ) = SharedPlaybackFragment(blockCfi = cfi, startAbs = start, endAbs = end)
}