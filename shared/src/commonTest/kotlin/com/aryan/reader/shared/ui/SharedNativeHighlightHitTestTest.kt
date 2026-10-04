package com.aryan.reader.shared.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tapping a stretch covered by two highlights has to reach both.
 *
 * The reader is allowed to highlight the same words twice, in different colours, and that is a normal
 * thing to do. Both hit-testers used to return a single match — Android walked backwards and took the
 * first hit, shared took the first annotation — so whichever of the two was not chosen was
 * unreachable by tap: it could be seen but never opened, recoloured or deleted. A tap now opens the
 * most recent of them, and every one of them is still enumerable for a disambiguation affordance.
 */
class SharedNativeHighlightHitTestTest {

    private val text = "alpha beta gamma delta epsilon"

    private fun highlight(
        id: String,
        start: Int,
        end: Int,
        color: HighlightColor = HighlightColor.YELLOW
    ) = UserHighlight(
        id = id,
        cfi = "/4/2:$start",
        text = text.substring(start, end),
        color = color,
        chapterIndex = 0,
        style = HighlightStyle.BACKGROUND,
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = start,
            endOffset = end,
            textQuote = text.substring(start, end),
            cfi = "/4/2:$start"
        )
    )

    private fun paintable(vararg entries: Pair<UserHighlight, IntRange>) =
        entries.map { (h, r) -> PaintableHighlight(h, listOf(r)) }

    @Test
    fun `both highlights in an overlap are hit`() {
        val first = highlight("first", 6, 16)
        val second = highlight("second", 11, 21, HighlightColor.GREEN)

        val hits = highlightHitsAt(13, paintable(first to 6..15, second to 11..20))

        assertEquals(listOf("first", "second"), hits.map { it.highlight.id })
    }

    @Test
    fun `a plain tap opens the most recently created highlight`() {
        val first = highlight("first", 6, 16)
        val second = highlight("second", 11, 21, HighlightColor.GREEN)

        // Whichever was added last is drawn on top, so its colour is what the reader sees there.
        assertEquals("second", primaryHighlightHitAt(13, paintable(first to 6..15, second to 11..20))?.highlight?.id)
        assertEquals("first", primaryHighlightHitAt(13, paintable(second to 11..20, first to 6..15))?.highlight?.id)
    }

    @Test
    fun `a tap outside every highlight hits nothing`() {
        val only = highlight("only", 6, 10)

        assertTrue(highlightHitsAt(20, paintable(only to 6..9)).isEmpty())
        assertNull(primaryHighlightHitAt(20, paintable(only to 6..9)))
    }

    @Test
    fun `a highlight spanning several ranges is hit at each of them`() {
        val multi = highlight("multi", 0, 5)

        assertEquals("multi", primaryHighlightHitAt(3, paintable(multi to 0..4, multi to 20..24))?.highlight?.id)
    }

    @Test
    fun `a read-aloud band is not hittable`() {
        // The band covers whatever is under it, and it is not the reader's highlight: tapping it must
        // open nothing rather than a highlight that does not exist.
        val band = UserHighlight(
            id = "tts_42_7",
            cfi = "/4/2:6",
            text = "beta",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(chapterIndex = 0, startOffset = 6, endOffset = 10, textQuote = "beta")
        )

        assertTrue(highlightHitsAt(8, paintable(band to 6..9)).isEmpty())
    }

    @Test
    fun `a band over a real highlight does not hide it`() {
        val band = UserHighlight(
            id = "tts_42_7",
            cfi = "/4/2:6",
            text = "beta",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(chapterIndex = 0, startOffset = 6, endOffset = 10, textQuote = "beta")
        )
        val real = highlight("real", 6, 10)

        assertEquals(listOf("real"), highlightHitsAt(8, paintable(band to 6..9, real to 6..9)).map { it.highlight.id })
    }

    @Test
    fun `a negative offset hits nothing`() {
        val only = highlight("only", 6, 10)

        assertTrue(highlightHitsAt(-1, paintable(only to 6..9)).isEmpty())
    }

    @Test
    fun `painted annotations enumerate every highlight and pick the last`() {
        val rendered = buildAnnotatedString {
            append(text)
            applyHighlightsToTextRanges(
                highlights = listOf(highlight("first", 6, 16), highlight("second", 11, 21, HighlightColor.GREEN)),
                chapterIndex = 0,
                pageIndex = 0,
                blockCfi = "/4/2",
                blockIndex = 1,
                blockCharOffset = 0,
                textStartOffset = 0,
                textLength = text.length,
                text = text
            )
        }

        // The overlap used to hide the first annotation behind the second, permanently.
        assertEquals(listOf("first", "second"), rendered.stringAnnotationsAt(ReaderNativeAnnotationHighlight, 13))
        assertEquals("second", rendered.stringAnnotationAt(ReaderNativeAnnotationHighlight, 13))
    }

    @Test
    fun `a painted read-aloud band contributes no annotation`() {
        val band = UserHighlight(
            id = "tts_42_7",
            cfi = "/4/2:6",
            text = "beta",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(chapterIndex = 0, startOffset = 6, endOffset = 10, textQuote = "beta")
        )
        val rendered: AnnotatedString = buildAnnotatedString {
            append(text)
            applyHighlightsToTextRanges(
                highlights = listOf(band),
                chapterIndex = 0,
                pageIndex = 0,
                blockCfi = "/4/2",
                blockIndex = 1,
                blockCharOffset = 0,
                textStartOffset = 0,
                textLength = text.length,
                text = text
            )
        }

        assertTrue(rendered.stringAnnotationsAt(ReaderNativeAnnotationHighlight, 8).isEmpty())
        // Still painted.
        assertTrue(rendered.spanStyles.any { it.start == 6 && it.end == 10 })
    }
}