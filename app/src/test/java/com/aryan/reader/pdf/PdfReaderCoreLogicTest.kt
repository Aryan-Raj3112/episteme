package com.aryan.reader.pdf

import android.graphics.RectF
import android.graphics.Rect
import com.aryan.reader.pdf.ocr.OcrBlock
import com.aryan.reader.pdf.ocr.OcrElement
import com.aryan.reader.pdf.ocr.OcrLine
import com.aryan.reader.pdf.ocr.OcrResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfReaderCoreLogicTest {

    @Test
    fun `generateShortId creates a four digit sync suffix`() {
        repeat(100) {
            val id = generateShortId()

            assertTrue(id, id.matches(Regex("\\d{4}")))
            assertTrue(id, id.toInt() in 1000..9998)
        }
    }

    @Test
    fun `resolveEraserStrokeWidth uses eraser size only for stylus override`() {
        assertEquals(
            0.08f,
            resolveEraserStrokeWidth(
                isEraserOverride = true,
                activeToolThickness = 0.005f,
                eraserToolThickness = 0.08f
            ),
            0.0001f
        )
        assertEquals(
            0.005f,
            resolveEraserStrokeWidth(
                isEraserOverride = false,
                activeToolThickness = 0.005f,
                eraserToolThickness = 0.08f
            ),
            0.0001f
        )
    }

    @Test
    fun `getSuggestedFilename sanitizes truncates and marks annotated copies`() {
        val filename = getSuggestedFilename(
            originalName = "A very long odd @name with spaces and symbols that should be truncated eventually.pdf",
            isAnnotated = true
        )

        assertTrue(filename, filename.matches(Regex("A_very_long_odd__name_with_spaces_and_symbols_that_annotated_\\d{4}\\.pdf")))
        assertTrue(filename.length <= "A_very_long_odd__name_with_spaces_and_symbols_that_annotated_0000.pdf".length)
    }

    @Test
    fun `getSuggestedFilename uses Document when original name is missing`() {
        val filename = getSuggestedFilename(originalName = null, isAnnotated = false)

        assertTrue(filename, filename.matches(Regex("Document_\\d{4}\\.pdf")))
    }

    @Test
    fun `preprocessTextForTts returns empty processed text for blank input`() {
        val processed = preprocessTextForTts(" \n\t ")

        assertEquals("", processed.cleanText)
        assertEquals(emptyList<Int>(), processed.indexMap)
    }

    @Test
    fun `preprocessTextForTts turns layout newlines inside sentences into spaces`() {
        val processed = preprocessTextForTts("Hello\nworld\r\nagain")

        assertEquals("Hello world again", processed.cleanText)
        assertEquals(processed.cleanText.length, processed.indexMap.size)
        assertEquals(5, processed.indexMap[5])
        assertEquals(12, processed.indexMap[11])
    }

    @Test
    fun `preprocessTextForTts keeps current punctuation newline behavior`() {
        val processed = preprocessTextForTts("End.\nNext?\nLast!")

        assertEquals("End.Next?Last!", processed.cleanText)
    }

    @Test
    fun `mergeRectsIntoLines combines rectangles with vertical overlap and keeps separate lines sorted`() {
        val rects = listOf(
            Rect(40, 50, 60, 70),
            Rect(10, 10, 20, 30),
            Rect(22, 15, 35, 31),
            Rect(8, 55, 30, 75)
        )

        val merged = mergeRectsIntoLines(rects)

        assertEquals(
            listOf(
                Rect(10, 10, 35, 31),
                Rect(8, 50, 60, 75)
            ),
            merged
        )
    }

    @Test
    fun `mergeRectsIntoLines treats touching vertical edges as separate lines`() {
        val merged = mergeRectsIntoLines(
            listOf(
                Rect(0, 0, 10, 10),
                Rect(0, 10, 10, 20)
            )
        )

        assertEquals(listOf(Rect(0, 0, 10, 10), Rect(0, 10, 10, 20)), merged)
    }

    @Test
    fun `mergePdfRectsIntoLines normalizes inverted pdf rects and merges slight line overlap`() {
        val merged = mergePdfRectsIntoLines(
            listOf(
                RectF(0f, 100f, 20f, 90f),
                RectF(22f, 99f, 40f, 91f),
                RectF(0f, 70f, 10f, 60f)
            )
        )

        assertEquals(2, merged.size)
        assertRectFEquals(RectF(0f, 100f, 40f, 90f), merged[0])
        assertRectFEquals(RectF(0f, 70f, 10f, 60f), merged[1])
    }

    @Test
    fun `findRectsForTextChunkInOcrVisual matches words case-insensitively across elements`() {
        val result = ocrResult(
            OcrElement("The", Rect(0, 0, 10, 10), emptyList()),
            OcrElement("Quick", Rect(12, 0, 30, 10), emptyList()),
            OcrElement("Brown.", Rect(32, 0, 55, 10), emptyList()),
            OcrElement("Fox", Rect(57, 0, 70, 10), emptyList())
        )

        val rects = findRectsForTextChunkInOcrVisual(result, "quick brown")

        assertEquals(listOf(Rect(12, 0, 30, 10), Rect(32, 0, 55, 10)), rects)
    }

    @Test
    fun `findRectsForTextChunkInOcrVisual returns empty for blank text missing words and missing OCR elements`() {
        val result = ocrResult(OcrElement("Only", Rect(0, 0, 10, 10), emptyList()))

        assertEquals(emptyList<Rect>(), findRectsForTextChunkInOcrVisual(result, ""))
        assertEquals(emptyList<Rect>(), findRectsForTextChunkInOcrVisual(result, "missing"))
        assertEquals(emptyList<Rect>(), findRectsForTextChunkInOcrVisual(OcrResult("", emptyList()), "Only"))
    }

    @Test
    fun `findWordBoundaries expands from middle of word across letters and digits`() = runTest {
        val textPage = FakeReaderTextPage("Start A1b2 end")

        val bounds = findWordBoundaries(textPage, initialCharIndex = 8, pageCharCount = textPage.source.length)

        assertEquals(6 to 10, bounds)
    }

    @Test
    fun `findWordBoundaries stops at punctuation boundaries`() = runTest {
        val textPage = FakeReaderTextPage("can't stop")

        val leftSide = findWordBoundaries(textPage, initialCharIndex = 2, pageCharCount = textPage.source.length)
        val rightSide = findWordBoundaries(textPage, initialCharIndex = 4, pageCharCount = textPage.source.length)

        assertEquals(0 to 3, leftSide)
        assertEquals(4 to 5, rightSide)
    }

    @Test
    fun `findWordBoundaries returns null for punctuation and out of bounds selection`() = runTest {
        val textPage = FakeReaderTextPage("word.")

        assertNull(findWordBoundaries(textPage, initialCharIndex = 4, pageCharCount = textPage.source.length))
        assertNull(findWordBoundaries(textPage, initialCharIndex = -1, pageCharCount = textPage.source.length))
        assertNull(findWordBoundaries(textPage, initialCharIndex = 5, pageCharCount = textPage.source.length))
    }

    private class FakeReaderTextPage(val source: String) : ReaderTextPage {
        override suspend fun textPageCountChars(): Int = source.length
        override suspend fun textPageGetText(startIndex: Int, count: Int): String? =
            source.substring(startIndex, (startIndex + count).coerceAtMost(source.length))

        override suspend fun textPageGetRectsForRanges(ranges: IntArray): List<ReaderTextRect>? = null
        override suspend fun textPageGetCharIndexAtPos(
            x: Double,
            y: Double,
            xTolerance: Double,
            yTolerance: Double
        ): Int = -1

        override suspend fun textPageGetCharBox(index: Int): RectF? = null
        override suspend fun textPageGetUnicode(index: Int): Int = source[index].code
        override suspend fun loadWebLink(): ReaderWebLinks? = null
        override fun close() = Unit
    }

    private fun ocrResult(vararg elements: OcrElement): OcrResult {
        val line = OcrLine(
            text = elements.joinToString(" ") { it.text },
            boundingBox = null,
            elements = elements.toList()
        )
        val block = OcrBlock(text = line.text, boundingBox = null, lines = listOf(line))
        return OcrResult(text = line.text, textBlocks = listOf(block))
    }

    private fun assertRectFEquals(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }
}
