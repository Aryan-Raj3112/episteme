package com.aryan.reader.pdf

import android.content.Context
import android.graphics.RectF
import android.graphics.Rect
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.pdf.ocr.OcrBlock
import com.aryan.reader.pdf.ocr.OcrElement
import com.aryan.reader.pdf.ocr.OcrLine
import com.aryan.reader.pdf.ocr.OcrResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

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
    fun `locked orientation reset camera returns base fit zoom and target page pan`() {
        val camera = calculateLockedOrientationResetCamera(
            pageTopY = 1_000f,
            totalDocHeight = 3_000f,
            screenWidth = 800f,
            screenHeight = 1_200f,
            headerHeightPx = 40f,
            footerHeightPx = 60f,
            fitZoom = 1f
        )

        assertEquals(1f, camera.zoom, 0.0001f)
        assertEquals(0f, camera.panX, 0.0001f)
        assertEquals(-960f, camera.panY, 0.0001f)
    }

    @Test
    fun `locked orientation reset camera centers narrow fit zoom and clamps short documents`() {
        val camera = calculateLockedOrientationResetCamera(
            pageTopY = 120f,
            totalDocHeight = 500f,
            screenWidth = 1_000f,
            screenHeight = 900f,
            headerHeightPx = 40f,
            footerHeightPx = 60f,
            fitZoom = 0.5f
        )

        assertEquals(0.5f, camera.zoom, 0.0001f)
        assertEquals(250f, camera.panX, 0.0001f)
        assertEquals(40f, camera.panY, 0.0001f)
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
    fun `getFastFileId uses stable file name and length for file uris`() {
        val file = File("build/test-tmp/pdf-reader/fast-id-${System.nanoTime()}.pdf").apply {
            parentFile?.mkdirs()
            writeText("pdf")
        }

        val id = getFastFileId(RuntimeEnvironment.getApplication(), Uri.fromFile(file))

        assertEquals("${file.name}_${file.length()}", id)
    }

    @Test
    fun `pdf export choice is hidden when loaded sidecars have no annotations`() {
        assertFalse(
            shouldShowPdfAnnotationExportChoice(
                sidecarsReady = true,
                annotations = mapOf(0 to emptyList()),
                textBoxes = emptyList(),
                highlights = emptyList()
            )
        )
    }

    @Test
    fun `pdf export choice is shown when exportable annotations exist`() {
        val inkAnnotation = PdfAnnotation(
            type = AnnotationType.INK,
            inkType = InkType.PEN,
            pageIndex = 0,
            points = listOf(PdfPoint(0.1f, 0.2f)),
            color = Color.Black,
            strokeWidth = 0.01f
        )
        val textBox = PdfTextBox(
            id = "box",
            pageIndex = 0,
            relativeBounds = androidx.compose.ui.geometry.Rect(0.1f, 0.1f, 0.4f, 0.2f),
            text = "note",
            color = Color.Black,
            backgroundColor = Color.Transparent,
            fontSize = 16f
        )
        val highlight = PdfUserHighlight(
            pageIndex = 0,
            bounds = listOf(RectF(0.1f, 0.1f, 0.4f, 0.2f)),
            color = PdfHighlightColor.YELLOW,
            text = "selected",
            range = 0 to 8
        )

        assertTrue(shouldShowPdfAnnotationExportChoice(true, mapOf(0 to listOf(inkAnnotation)), emptyList(), emptyList()))
        assertTrue(shouldShowPdfAnnotationExportChoice(true, emptyMap(), listOf(textBox), emptyList()))
        assertTrue(shouldShowPdfAnnotationExportChoice(true, emptyMap(), emptyList(), listOf(highlight)))
    }

    @Test
    fun `pdf export choice remains available until sidecars are loaded`() {
        assertTrue(
            shouldShowPdfAnnotationExportChoice(
                sidecarsReady = false,
                annotations = emptyMap(),
                textBoxes = emptyList(),
                highlights = emptyList()
            )
        )
    }

    @Test
    fun `pdfRenderPageId separates same page across documents`() {
        val firstDocumentPage = pdfRenderPageId("book-a", 0, VirtualPage.PdfPage(0))
        val secondDocumentPage = pdfRenderPageId("book-b", 0, VirtualPage.PdfPage(0))

        assertEquals("book-a:PDF_0", firstDocumentPage)
        assertTrue(firstDocumentPage != secondDocumentPage)
    }

    @Test
    fun `pdfRenderPageId preserves virtual page source identity`() {
        assertEquals("book:PDF_12", pdfRenderPageId("book", 3, VirtualPage.PdfPage(12)))
        assertEquals(
            "book:BLANK_blank-1",
            pdfRenderPageId("book", 3, VirtualPage.BlankPage("blank-1", 595, 842))
        )
    }

    @Test
    fun `embedded annotation grouping links replies by annotation id`() {
        val root = embeddedAnnotation(index = 0, name = "root", contents = "Parent")
        val reply = embeddedAnnotation(index = 1, name = "reply", inReplyTo = "root", contents = "Child")

        val grouped = groupEmbeddedAnnotationsForDisplay(listOf(root, reply))

        assertEquals(listOf(root), grouped)
        assertEquals(listOf(reply), grouped.single().replies)
    }

    @Test
    fun `embedded annotation grouping keeps geometric replies that provide visible content`() {
        val blankRoot = embeddedAnnotation(index = 0, rect = RectF(0f, 0f, 20f, 20f), contents = "")
        val nearbyReply = embeddedAnnotation(index = 1, rect = RectF(25f, 0f, 40f, 20f), contents = "Visible")
        val emptyStandalone = embeddedAnnotation(index = 2, rect = RectF(200f, 0f, 220f, 20f), contents = "")

        val grouped = groupEmbeddedAnnotationsForDisplay(listOf(blankRoot, nearbyReply, emptyStandalone))

        assertEquals(listOf(blankRoot), grouped)
        assertEquals(listOf(nearbyReply), grouped.single().replies)
    }

    @Test
    fun `layout remap keeps annotations on their virtual pages when inserting a blank page`() {
        val existingBlank = VirtualPage.BlankPage("existing-blank", 612, 792, wasManuallyAdded = true)
        val insertedBlank = VirtualPage.BlankPage("inserted-blank", 612, 792, wasManuallyAdded = true)
        val currentLayout = listOf(
            VirtualPage.PdfPage(0),
            existingBlank,
            VirtualPage.PdfPage(1),
            VirtualPage.PdfPage(5)
        )
        val updatedLayout = listOf(
            VirtualPage.PdfPage(0),
            insertedBlank,
            existingBlank,
            VirtualPage.PdfPage(1),
            VirtualPage.PdfPage(5)
        )
        val annotations = mapOf(
            0 to listOf(testInkAnnotation(id = "pdf-0", pageIndex = 0)),
            1 to listOf(testInkAnnotation(id = "existing-blank", pageIndex = 99)),
            2 to listOf(testInkAnnotation(id = "pdf-1", pageIndex = 2)),
            3 to listOf(testInkAnnotation(id = "pdf-5", pageIndex = 3))
        )

        val remapped = remapPdfAnnotationsForLayoutChange(currentLayout, updatedLayout, annotations)

        assertNull(remapped[1])
        assertEquals(setOf(0, 2, 3, 4), remapped.keys)
        assertEquals("pdf-0", remapped.getValue(0).single().id)
        assertEquals(0, remapped.getValue(0).single().pageIndex)
        assertEquals("existing-blank", remapped.getValue(2).single().id)
        assertEquals(2, remapped.getValue(2).single().pageIndex)
        assertEquals("pdf-1", remapped.getValue(3).single().id)
        assertEquals(3, remapped.getValue(3).single().pageIndex)
        assertEquals("pdf-5", remapped.getValue(4).single().id)
        assertEquals(4, remapped.getValue(4).single().pageIndex)
    }

    @Test
    fun `layout remap drops annotations from a removed blank page and keeps later pdf annotations`() {
        val removedBlank = VirtualPage.BlankPage("removed-blank", 612, 792, wasManuallyAdded = true)
        val currentLayout = listOf(VirtualPage.PdfPage(0), removedBlank, VirtualPage.PdfPage(1))
        val updatedLayout = listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1))
        val annotations = mapOf(
            1 to listOf(testInkAnnotation(id = "blank-note", pageIndex = 1)),
            2 to listOf(testInkAnnotation(id = "pdf-1", pageIndex = 2))
        )

        val remapped = remapPdfAnnotationsForLayoutChange(currentLayout, updatedLayout, annotations)

        assertNull(remapped[0])
        assertEquals(setOf(1), remapped.keys)
        assertEquals("pdf-1", remapped.getValue(1).single().id)
        assertEquals(1, remapped.getValue(1).single().pageIndex)
    }

    @Test
    fun `text box chrome layout keeps drag pill inside hit bounds without moving text body`() {
        val bounds = androidx.compose.ui.geometry.Rect(100f, 200f, 180f, 260f)
        val bottomHandle = calculateTextBoxChromeLayout(
            textBoundsPx = bounds,
            isSelected = true,
            isHandleAtTop = false,
            handleSizePx = 10f,
            dragPillWidthPx = 72f,
            dragPillHeightPx = 48f,
            dragPillGapPx = 8f
        )
        val topHandle = calculateTextBoxChromeLayout(
            textBoundsPx = bounds,
            isSelected = true,
            isHandleAtTop = true,
            handleSizePx = 10f,
            dragPillWidthPx = 72f,
            dragPillHeightPx = 48f,
            dragPillGapPx = 8f
        )

        listOf(bottomHandle, topHandle).forEach { layout ->
            assertEquals(bounds.left, layout.outerTranslationX + layout.contentOffsetX + 5f, 0.0001f)
            assertEquals(bounds.top, layout.outerTranslationY + layout.contentOffsetY + 5f, 0.0001f)
            assertTrue(layout.dragPillLeftPx >= 0f)
            assertTrue(layout.dragPillLeftPx + 72f <= layout.containerWidthPx)
            assertTrue(layout.dragPillTopPx >= 0f)
            assertTrue(layout.dragPillTopPx + 48f <= layout.containerHeightPx)
        }
        assertEquals(0f, topHandle.dragPillTopPx, 0.0001f)
    }

    @Test
    fun `bubble prefetch only includes current page and nearby pages`() {
        assertEquals(listOf(10, 11, 9), buildPdfBubblePrefetchOrder(currentPage = 10, totalPages = 100))
    }

    @Test
    fun `bubble prefetch clamps current page and respects edges`() {
        assertEquals(listOf(0, 1), buildPdfBubblePrefetchOrder(currentPage = -4, totalPages = 5))
        assertEquals(listOf(4, 3), buildPdfBubblePrefetchOrder(currentPage = 99, totalPages = 5))
        assertEquals(emptyList<Int>(), buildPdfBubblePrefetchOrder(currentPage = 0, totalPages = 0))
    }

    @Test
    fun `bubble zoom factor fits bubble inside viewport target and clamps extremes`() {
        assertEquals(
            2f,
            computeDynamicBubbleZoomFactor(
                bubbleBounds = RectF(0f, 0f, 300f, 80f),
                viewportWidth = 1_000f,
                viewportHeight = 1_000f
            ),
            0.0001f
        )
        assertEquals(
            1.5f,
            computeDynamicBubbleZoomFactor(
                bubbleBounds = RectF(0f, 0f, 0f, 80f),
                viewportWidth = 1_000f,
                viewportHeight = 1_000f
            ),
            0.0001f
        )
        assertEquals(
            4.25f,
            computeDynamicBubbleZoomFactor(
                bubbleBounds = RectF(0f, 0f, 10f, 10f),
                viewportWidth = 1_000f,
                viewportHeight = 1_000f
            ),
            0.0001f
        )
    }

    @Test
    fun `safe pdf bitmap render scale keeps small renders and limits large renders`() {
        assertEquals(
            2f,
            safePdfBitmapRenderScale(
                contentWidth = 100f,
                contentHeight = 100f,
                requestedScale = 2f
            ),
            0.0001f
        )
        assertEquals(
            1f,
            safePdfBitmapRenderScale(
                contentWidth = 0f,
                contentHeight = 100f,
                requestedScale = 2f
            ),
            0.0001f
        )

        val limitedScale = safePdfBitmapRenderScale(
            contentWidth = 10_000f,
            contentHeight = 10_000f,
            requestedScale = 2f
        )

        assertTrue(limitedScale < 2f)
        assertTrue(limitedScale >= 0.01f)
    }

    @Test
    fun `canUsePdfSidecarsForBook only accepts loaded sidecars for active book`() {
        assertTrue(canUsePdfSidecarsForBook("book-a", "book-a", areSidecarsLoaded = true))
        assertEquals(false, canUsePdfSidecarsForBook("book-a", "book-b", areSidecarsLoaded = true))
        assertEquals(false, canUsePdfSidecarsForBook("book-a", "book-a", areSidecarsLoaded = false))
        assertEquals(false, canUsePdfSidecarsForBook(null, "book-a", areSidecarsLoaded = true))
    }

    @Test
    fun `canManagePdfVirtualPages waits for the active page layout to load`() {
        assertTrue(
            canManagePdfVirtualPages(
                isDocumentReady = true,
                currentBookId = "book-a",
                loadedPageLayoutBookId = "book-a",
                virtualPageCount = 3
            )
        )
        assertFalse(
            canManagePdfVirtualPages(
                isDocumentReady = true,
                currentBookId = "book-a",
                loadedPageLayoutBookId = null,
                virtualPageCount = 3
            )
        )
        assertFalse(
            canManagePdfVirtualPages(
                isDocumentReady = true,
                currentBookId = "book-a",
                loadedPageLayoutBookId = "book-b",
                virtualPageCount = 3
            )
        )
        assertFalse(
            canManagePdfVirtualPages(
                isDocumentReady = false,
                currentBookId = "book-a",
                loadedPageLayoutBookId = "book-a",
                virtualPageCount = 3
            )
        )
        assertFalse(
            canManagePdfVirtualPages(
                isDocumentReady = true,
                currentBookId = "book-a",
                loadedPageLayoutBookId = "book-a",
                virtualPageCount = 0
            )
        )
    }

    @Test
    fun `saveAnnotations deletes stored annotations when saving empty map`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val repository = PdfAnnotationRepository(context)
        val bookId = "empty-annotation-save-${System.nanoTime()}"
        val annotation = PdfAnnotation(
            type = AnnotationType.INK,
            inkType = InkType.PEN,
            pageIndex = 0,
            points = listOf(PdfPoint(0.1f, 0.2f)),
            color = Color.Black,
            strokeWidth = 0.01f
        )

        repository.saveAnnotations(bookId, mapOf(0 to listOf(annotation)))
        assertEquals(1, repository.loadAnnotations(bookId)[0]?.size)

        repository.saveAnnotations(bookId, emptyMap())

        assertEquals(emptyMap<Int, List<PdfAnnotation>>(), repository.loadAnnotations(bookId))
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

    private fun embeddedAnnotation(
        index: Int,
        rect: RectF = RectF(0f, 0f, 20f, 20f),
        name: String? = null,
        inReplyTo: String? = null,
        contents: String? = null
    ): EmbeddedAnnotation {
        return EmbeddedAnnotation(
            index = index,
            subtype = 0,
            rect = rect,
            contents = contents,
            author = null,
            name = name,
            inReplyTo = inReplyTo
        )
    }

    private fun testInkAnnotation(id: String, pageIndex: Int): PdfAnnotation {
        return PdfAnnotation(
            type = AnnotationType.INK,
            inkType = InkType.PEN,
            pageIndex = pageIndex,
            points = listOf(PdfPoint(0.1f, 0.2f), PdfPoint(0.2f, 0.3f)),
            color = Color.Black,
            strokeWidth = 0.01f,
            id = id
        )
    }

    private fun assertRectFEquals(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }
}
