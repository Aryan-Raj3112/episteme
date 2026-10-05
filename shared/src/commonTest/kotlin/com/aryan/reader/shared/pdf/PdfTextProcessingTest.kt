package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfTextProcessingTest {
    @Test
    fun `tts preprocessing preserves android newline and source-index behavior`() {
        val processed = PdfTextProcessing.preprocessForTts("Hello\nworld\r\nEnd.\nNext")

        assertEquals("Hello world End.Next", processed.cleanText)
        assertEquals(processed.cleanText.length, processed.indexMap.size)
        assertEquals(5, processed.indexMap[5])
        assertEquals(12, processed.indexMap[11])
    }

    @Test
    fun `tts preprocessing joins hyphenated line breaks into one word`() {
        val processed = PdfTextProcessing.preprocessForTts("understand-\ning well")

        assertEquals("understanding well", processed.cleanText)
        assertEquals(processed.cleanText.length, processed.indexMap.size)
        assertEquals(12, processed.indexMap[10])
    }

    @Test
    fun `hyphenated line break joining merges soft wrapped words`() {
        assertEquals(
            "understanding the",
            PdfTextProcessing.joinHyphenatedLineBreaks("understand-\ning the")
        )
        assertEquals(
            "cofounder",
            PdfTextProcessing.joinHyphenatedLineBreaks("co\u2010\nfounder")
        )
        assertEquals(
            "example",
            PdfTextProcessing.joinHyphenatedLineBreaks("exam\u00AD\nple")
        )
        assertEquals(
            "understanding",
            PdfTextProcessing.joinHyphenatedLineBreaks("understand-\r\ning")
        )
        // Documented tradeoff: compounds already carrying a hyphen cannot be told
        // apart from soft wraps without a dictionary.
        assertEquals(
            "mother-inlaw",
            PdfTextProcessing.joinHyphenatedLineBreaks("mother-in-\nlaw")
        )
    }

    @Test
    fun `hyphenated line break joining keeps regular breaks and non word hyphens`() {
        assertEquals("Hello\nworld", PdfTextProcessing.joinHyphenatedLineBreaks("Hello\nworld"))
        assertEquals("pages 10-\n20 stay", PdfTextProcessing.joinHyphenatedLineBreaks("pages 10-\n20 stay"))
        assertEquals("-\nItem list", PdfTextProcessing.joinHyphenatedLineBreaks("-\nItem list"))
        assertEquals("well-\n\nknown gap", PdfTextProcessing.joinHyphenatedLineBreaks("well-\n\nknown gap"))
        assertEquals("stop—\ngoing", PdfTextProcessing.joinHyphenatedLineBreaks("stop—\ngoing"))
    }

    @Test
    fun `joined hyphen breaks remap clean offsets back to source offsets`() {
        val mapped = PdfTextProcessing.joinHyphenatedLineBreaksMapped("understand-\ning")

        assertEquals("understanding", mapped.cleanText)
        assertEquals(mapped.cleanText.length, mapped.indexMap.size)
        assertEquals(12 to 15, mapped.rawRange(10, 13))
        assertEquals(0 to 15, mapped.rawRange(0, 13))
    }

    @Test
    fun `screen and pdf line merging preserve their distinct coordinate contracts`() {
        assertEquals(
            listOf(PdfPageBounds(10f, 10f, 35f, 31f)),
            PdfTextProcessing.mergeScreenBoundsIntoLines(
                listOf(PdfPageBounds(10f, 10f, 20f, 30f), PdfPageBounds(22f, 15f, 35f, 31f))
            )
        )
        assertEquals(
            listOf(PdfPageBounds(0f, 100f, 40f, 90f)),
            PdfTextProcessing.mergePdfBoundsIntoLines(
                listOf(PdfPageBounds(0f, 100f, 20f, 90f), PdfPageBounds(22f, 99f, 40f, 91f))
            )
        )
    }

    @Test
    fun `tightly led text lines are not merged into one rect`() {
        // A font box is roughly ascender + descender (~13.8pt for 12pt text), so 12pt leading
        // already leaves ~1.8pt of shared vertical band between adjacent lines. A loose overlap
        // test merged them into one rect, and because a decoration stroke is positioned relative
        // to the rect (underline at 86% of height) the single stroke landed under the second line
        // only, leaving the first line with no stroke at all.
        val twoLines = listOf(
            PdfPageBounds(10f, 700f, 200f, 713.8f),
            PdfPageBounds(10f, 676f, 190f, 690f),
        )
        assertEquals(2, PdfTextProcessing.mergePdfBoundsIntoLines(twoLines).size)
        assertEquals(2, PdfTextProcessing.mergeScreenBoundsIntoLines(twoLines).size)

        // Runs on a single line still merge, which is the whole point of these helpers.
        val oneLineRuns = listOf(
            PdfPageBounds(10f, 100f, 80f, 113.8f),
            PdfPageBounds(80f, 100f, 150f, 113.8f),
            PdfPageBounds(150f, 100.5f, 220f, 114f),
        )
        assertEquals(1, PdfTextProcessing.mergePdfBoundsIntoLines(oneLineRuns).size)
        assertEquals(1, PdfTextProcessing.mergeScreenBoundsIntoLines(oneLineRuns).size)
    }

    @Test
    fun `ocr matching ignores case and android trailing punctuation`() {
        val bounds = PdfPageBounds(12f, 0f, 30f, 10f)
        val punctuatedBounds = PdfPageBounds(32f, 0f, 55f, 10f)

        assertEquals(
            listOf(bounds, punctuatedBounds),
            PdfTextProcessing.findOcrWordSequence(
                words = listOf(
                    PdfOcrWord("The", null),
                    PdfOcrWord("Quick", bounds),
                    PdfOcrWord("Brown.", punctuatedBounds)
                ),
                textChunk = "quick brown"
            )
        )
    }
}
