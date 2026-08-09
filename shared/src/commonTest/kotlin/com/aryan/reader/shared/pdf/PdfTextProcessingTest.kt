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
