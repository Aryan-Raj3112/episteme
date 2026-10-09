package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A highlight made in a WebView has to be placeable against parsed chapter text.
 *
 * The WebView stores the characters the source had, including the non-breaking spaces EPUB typography
 * uses so a line cannot break there. The parsed blocks the native surfaces place against have usually
 * had those turned into ordinary spaces. With only `Char.isWhitespace` deciding what a space is — and
 * on the JVM that reports false for U+00A0 — the two halves glued into one unmatchable "word", and a
 * perfectly ordinary highlight resolved to nothing in every native surface while painting fine in the
 * one it was made in.
 */
class EpubChapterTextIndexReaderSpaceTest {

    private fun block(blockIndex: Int, text: String) = SemanticParagraph(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = "/4/${blockIndex * 2}",
        startCharOffsetInSource = 0,
        blockIndex = blockIndex
    )

    private fun indexOf(vararg paragraphs: String): EpubChapterTextIndex =
        assertNotNull(EpubChapterTextIndex.of(0, paragraphs.mapIndexed { i, t -> block(i, t) }))

    /** The WebView's rendering of the text: the source's own non-breaking space preserved. */
    private val webViewText = "She was\u00A0walking by the\u00A0river when the\u2009letter arrived"

    /** The parsed blocks: an ordinary space and a thin space where the non-breaking ones were. */
    private val parsedParagraphs = arrayOf(
        "She was walking by the river when the letter arrived",
        "A second paragraph, so the highlight spans a block join the way a real selection does."
    )

    @Test
    fun aQuoteWithNonBreakingSpacesResolvesAgainstParsedText() {
        val index = indexOf(*parsedParagraphs)

        val anchor = index.resolve(webViewText)

        assertNotNull(anchor, "a WebView-made highlight must still be placeable against parsed blocks")
        assertEquals(webViewText.length, anchor.absoluteEnd - anchor.absoluteStart)
    }

    @Test
    fun aQuoteSpanningTwoParagraphsResolvesToOneSegmentPerBlock() {
        val index = indexOf(*parsedParagraphs)
        val spanning = parsedParagraphs[0].takeLast(20) + " " + parsedParagraphs[1].take(20)

        val anchor = index.resolve(spanning)

        assertNotNull(anchor)
        assertEquals(
            2, anchor.segments.size,
            "a selection crossing a paragraph boundary needs a segment in each block it touches"
        )
        assertEquals(listOf(0, 1), anchor.segments.map { it.blockIndex })
    }

    @Test
    fun aQuoteThatIsNotInTheChapterStillResolvesToNothing() {
        val index = indexOf(*parsedParagraphs)

        assertNull(index.resolve("a sentence this chapter does not contain"))
    }

    @Test
    fun theSpaceRuleFoldsNonBreakingSpacesWithoutFoldingRealCharacters() {
        assertTrue(isReaderSpace('\u00A0'), "U+00A0 is a space separator")
        assertTrue(isReaderSpace('\u202F'), "U+202F narrow no-break space is a space separator")
        assertTrue(isReaderSpace('\u2009'), "U+2009 thin space is a space separator")
        assertTrue(isReaderSpace(' '))
        assertTrue(!isReaderSpace('a'))
        assertEquals(listOf("a", "b"), splitOnReaderSpaces("a\u00A0\u2009b"))
    }

    @Test
    fun repairingAWebViewMadeHighlightFillsInItsOffsets() {
        val index = indexOf(*parsedParagraphs)
        val highlight = UserHighlight(
            id = "made_in_webview",
            cfi = "/4/2/6/32/2/12:114",
            text = webViewText,
            color = HighlightColor.GREEN,
            chapterIndex = 0,
            locator = ReaderLocator(chapterIndex = 0, textQuote = webViewText, cfi = "/4/2/6/32/2/12:114")
        )

        val repaired = index.repairHighlights(listOf(highlight))

        assertEquals(1, repaired.repaired)
        val located = repaired.highlights.single().locator
        assertNotNull(located.startOffset)
        assertNotNull(located.blockIndex)
    }
}