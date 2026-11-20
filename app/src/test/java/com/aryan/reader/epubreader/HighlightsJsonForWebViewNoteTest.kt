package com.aryan.reader.epubreader

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WebView draws the "this highlight has a note" mark itself, so the payload has to carry the
 * two facts it needs: which highlights have a note, and the colour to paint the mark with.
 *
 * The note body is deliberately absent — the mark needs presence, not content, and shipping the text
 * into the document would be a second copy of something the app already owns.
 */
class HighlightsJsonForWebViewNoteTest {

    private fun highlight(
        id: String,
        note: String?,
        colorArgb: Int? = 0xFF3366CC.toInt(),
        color: HighlightColor = HighlightColor.BLUE,
    ) = UserHighlight(
        id = id,
        cfi = "0:2:1:5:3",
        text = "alpha beta",
        color = color,
        colorArgb = colorArgb,
        chapterIndex = 0,
        note = note,
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = 0,
            endOffset = 10,
            textQuote = "alpha beta",
            cfi = "0:2:1:5:3"
        )
    )

    @Test
    fun `only highlights with a note are marked`() {
        val json = highlightsJsonForWebView(
            listOf(
                highlight("with-note", "a thought"),
                highlight("without-note", null),
                highlight("blank-note", "   "),
            )
        )

        assertEquals(1, Regex("\"hasNote\":true").findAll(json).count())
        assertTrue(json.contains("\"id\":\"with-note\""))
    }

    @Test
    fun `the mark carries no colour of its own`() {
        val json = highlightsJsonForWebView(
            listOf(
                highlight("blue", "a thought"),
                highlight("yellow", "a thought", colorArgb = null, color = HighlightColor.YELLOW),
            )
        )

        // The mark is a fixed gold in the document: taking the highlight's colour made it invisible
        // on a yellow highlight, which is the one highlight it most needed to stand out from.
        assertFalse(json.contains("badgeCss"))
        assertEquals(2, Regex("\"hasNote\":true").findAll(json).count())
    }

    @Test
    fun `the note body never reaches the document`() {
        val json = highlightsJsonForWebView(listOf(highlight("with-note", "a private thought")))

        assertFalse(json.contains("a private thought"))
    }
}
