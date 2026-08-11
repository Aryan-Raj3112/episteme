package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfRichTextTest {

    @Test
    fun `mapper clips global rich spans into requested local range`() {
        val document = SharedPdfRichDocument(
            text = "0123456789",
            spans = listOf(
                SharedPdfRichSpan(
                    start = 2,
                    end = 6,
                    color = Color.Red.toArgb(),
                    backgroundColor = Color.Yellow.toArgb(),
                    fontSizeNorm = 0.02f,
                    isBold = true,
                    isItalic = true,
                    isUnderline = true,
                    isStrikethrough = true,
                    fontPath = "asset:fonts/lora.ttf"
                )
            )
        )

        val annotated = SharedPdfRichTextMapper.toAnnotatedString(
            document = document,
            pageHeightPx = 1_000f,
            rangeStart = 4,
            rangeEnd = 8
        )

        assertEquals("4567", annotated.text)
        val range = annotated.spanStyles.single()
        assertEquals(0, range.start)
        assertEquals(2, range.end)
        assertEquals(Color.Red, range.item.color)
        assertEquals(Color.Yellow, range.item.background)
        assertEquals(20.sp, range.item.fontSize)
        assertEquals(FontWeight.Bold, range.item.fontWeight)
        assertEquals(FontStyle.Italic, range.item.fontStyle)
        assertTrue(range.item.textDecoration!!.contains(TextDecoration.Underline))
        assertTrue(range.item.textDecoration!!.contains(TextDecoration.LineThrough))

        val roundTrip = SharedPdfRichTextMapper.fromAnnotatedString(annotated, pageHeightPx = 1_000f)
        assertEquals("4567", roundTrip.text)
        assertEquals(2, roundTrip.spans.size)
        assertEquals("asset:fonts/lora.ttf", roundTrip.spans.first().fontPath)
        assertEquals(0 to 2, roundTrip.spans.first().let { it.start to it.end })
        assertEquals(2 to 4, roundTrip.spans.last().let { it.start to it.end })
    }

    @Test
    fun `mapper fromAnnotatedString splits overlapping styles and preserves page breaks`() {
        val text = "Hello${SHARED_PDF_PAGE_BREAK_CHAR}World"
        val annotated = buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(
                    color = Color.Black,
                    background = Color.Transparent,
                    fontSize = 20.sp
                ),
                start = 0,
                end = text.length
            )
            addStyle(
                SpanStyle(
                    color = Color.Magenta,
                    background = Color.Cyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    textDecoration = TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )
                ),
                start = 0,
                end = 5
            )
        }

        val document = SharedPdfRichTextMapper.fromAnnotatedString(annotated, pageHeightPx = 1_000f)

        assertEquals(text, document.text)
        assertEquals(2, document.spans.size)
        val first = document.spans[0]
        assertEquals(0, first.start)
        assertEquals(5, first.end)
        assertEquals(Color.Magenta.toArgb(), first.color)
        assertEquals(Color.Cyan.toArgb(), first.backgroundColor)
        assertEquals(0.024f, first.fontSizeNorm, 0.0001f)
        assertTrue(first.isBold)
        assertTrue(first.isItalic)
        assertTrue(first.isUnderline)
        assertTrue(first.isStrikethrough)
        val second = document.spans[1]
        assertEquals(5, second.start)
        assertEquals(text.length, second.end)
        assertEquals(Color.Black.toArgb(), second.color)
        assertFalse(second.isBold)
    }

    @Test
    fun `serializer uses android rich text sidecar schema`() {
        val document = SharedPdfRichDocument(
            text = "Saved rich text",
            spans = listOf(
                SharedPdfRichSpan(
                    start = 0,
                    end = 5,
                    color = Color.Red.toArgb(),
                    backgroundColor = Color.Transparent.toArgb(),
                    fontSizeNorm = 0.018f,
                    isBold = true,
                    isItalic = false,
                    isUnderline = true,
                    isStrikethrough = false,
                    fontPath = "asset:fonts/lora.ttf"
                )
            )
        )

        val encoded = SharedPdfRichTextSerializer.encode(document)
        val decoded = SharedPdfRichTextSerializer.decode(encoded)

        assertTrue(encoded.contains("\"s\""))
        assertTrue(encoded.contains("\"fp\""))
        assertEquals(document, decoded)
    }

    @Test
    fun `serializer preserves Android compact ordering ranges and absent font default`() {
        val document = SharedPdfRichDocument(
            text = "x",
            spans = listOf(
                SharedPdfRichSpan(
                    start = 4,
                    end = 2,
                    color = 7,
                    backgroundColor = 0,
                    fontSizeNorm = 0.5f,
                    isBold = false,
                    isItalic = false,
                    isUnderline = false,
                    isStrikethrough = false,
                    fontPath = null,
                ),
            ),
        )

        val encoded = SharedPdfRichTextSerializer.encode(document)
        val decoded = SharedPdfRichTextSerializer.decode(encoded)

        assertEquals(
            "{\"text\":\"x\",\"spans\":[{\"s\":4,\"e\":2,\"c\":7,\"bg\":0,\"sz\":0.5," +
                "\"b\":false,\"i\":false,\"u\":false,\"st\":false}]}",
            encoded,
        )
        assertEquals(4, decoded.spans.single().start)
        assertEquals(2, decoded.spans.single().end)
        assertEquals("", decoded.spans.single().fontPath)
    }

    @Test
    fun `serializer rejects a partially malformed Android sidecar as one document`() {
        assertEquals(
            SharedPdfRichDocument(),
            SharedPdfRichTextSerializer.decode(
                "{\"text\":\"x\",\"spans\":[{\"s\":0,\"e\":1,\"c\":1,\"sz\":0.1},{}]}",
            ),
        )
    }

    @Test
    fun `loaded rich text rescales font spans when real page height arrives`() {
        val document = SharedPdfRichDocument(
            text = "Stable size",
            spans = listOf(
                SharedPdfRichSpan(
                    start = 0,
                    end = 6,
                    color = Color.Black.toArgb(),
                    backgroundColor = Color.Transparent.toArgb(),
                    fontSizeNorm = 0.02f,
                    isBold = false,
                    isItalic = false,
                    isUnderline = false,
                    isStrikethrough = false
                )
            )
        )
        val referenceHeight = 1_414f
        val actualHeight = 1_000f
        val loadedBeforeLayout = SharedPdfRichTextMapper.toAnnotatedString(document, referenceHeight)

        val loadedAtActualHeight = loadedBeforeLayout.withScaledSharedPdfRichFontSizes(actualHeight / referenceHeight)
        val savedAgain = SharedPdfRichTextMapper.fromAnnotatedString(loadedAtActualHeight, actualHeight)

        assertEquals(20.sp, loadedAtActualHeight.spanStyles.single().item.fontSize)
        assertEquals(0.02f, savedAgain.spans.first().fontSizeNorm, 0.0001f)
    }

    @Test
    fun `serializer returns empty document for blank and corrupt payloads`() {
        assertEquals(SharedPdfRichDocument(), SharedPdfRichTextSerializer.decode(""))
        assertEquals(SharedPdfRichDocument(), SharedPdfRichTextSerializer.decode("{not json"))
        assertEquals(
            SharedPdfRichDocument("", emptyList()),
            SharedPdfRichTextMapper.fromAnnotatedString(AnnotatedString(""), pageHeightPx = 1_000f)
        )
    }

    @Test
    fun `selection bounds normalize reversed and clamped rich text selections`() {
        assertEquals(44 to 45, sharedPdfRichTextSelectionBounds(45, 44, textLength = 45))
        assertEquals(0 to 5, sharedPdfRichTextSelectionBounds(-3, 99, textLength = 5))
        assertEquals(null, sharedPdfRichTextSelectionBounds(3, 3, textLength = 5))
    }

    @Test
    fun `trailing page break creates editable blank page layout`() {
        val globalText = AnnotatedString("$SHARED_PDF_PAGE_BREAK_CHAR")
        val layouts = listOf(
            SharedPdfRichPageLayout(
                pageIndex = 0,
                visibleText = globalText,
                globalStartIndex = 0,
                globalEndIndex = 1,
                pageHeightPx = 1_000f
            )
        )

        val withBlankPage = layouts.withTrailingBlankRichTextPageIfNeeded(
            globalText = globalText,
            pageHeightPx = 1_000f
        )

        assertEquals(2, withBlankPage.size)
        assertEquals(1, withBlankPage.last().pageIndex)
        assertEquals("", withBlankPage.last().visibleText.text)
        assertEquals(1, withBlankPage.last().globalStartIndex)
        assertEquals(1, withBlankPage.last().globalEndIndex)
    }

    @Test
    fun `trailing blank page helper is idempotent`() {
        val globalText = AnnotatedString("A$SHARED_PDF_PAGE_BREAK_CHAR")
        val layouts = listOf(
            SharedPdfRichPageLayout(
                pageIndex = 0,
                visibleText = AnnotatedString("A$SHARED_PDF_PAGE_BREAK_CHAR"),
                globalStartIndex = 0,
                globalEndIndex = 2,
                pageHeightPx = 1_000f
            ),
            SharedPdfRichPageLayout(
                pageIndex = 1,
                visibleText = AnnotatedString(""),
                globalStartIndex = 2,
                globalEndIndex = 2,
                pageHeightPx = 1_000f
            )
        )

        val withBlankPage = layouts.withTrailingBlankRichTextPageIfNeeded(
            globalText = globalText,
            pageHeightPx = 1_000f
        )

        assertEquals(layouts, withBlankPage)
    }

    @Test
    fun `consecutive explicit page breaks keep editable blank pages`() {
        val globalText = AnnotatedString("A$SHARED_PDF_PAGE_BREAK_CHAR$SHARED_PDF_PAGE_BREAK_CHAR")
        val layouts = listOf(
            SharedPdfRichPageLayout(
                pageIndex = 0,
                visibleText = AnnotatedString("A$SHARED_PDF_PAGE_BREAK_CHAR"),
                globalStartIndex = 0,
                globalEndIndex = 2,
                pageHeightPx = 1_000f
            ),
            SharedPdfRichPageLayout(
                pageIndex = 1,
                visibleText = AnnotatedString("$SHARED_PDF_PAGE_BREAK_CHAR"),
                globalStartIndex = 2,
                globalEndIndex = 3,
                pageHeightPx = 1_000f
            )
        )

        val withBlankPage = layouts.withTrailingBlankRichTextPageIfNeeded(
            globalText = globalText,
            pageHeightPx = 1_000f
        )

        assertEquals(3, withBlankPage.size)
        assertEquals("A$SHARED_PDF_PAGE_BREAK_CHAR", withBlankPage[0].visibleText.text)
        assertEquals("$SHARED_PDF_PAGE_BREAK_CHAR", withBlankPage[1].visibleText.text)
        assertEquals("", withBlankPage[2].visibleText.text)
        assertEquals(3, withBlankPage[2].globalStartIndex)
        assertEquals(3, withBlankPage[2].globalEndIndex)
    }

    @Test
    fun `editable rich text hides trailing structural page break`() {
        val text = AnnotatedString("Body$SHARED_PDF_PAGE_BREAK_CHAR")

        val editable = text.withoutTrailingSharedPdfPageBreak()

        assertEquals("Body", editable.text)
    }

    @Test
    fun `blank page insertion uses one page break at explicit rich text boundaries`() {
        val text = "Page 1${SHARED_PDF_PAGE_BREAK_CHAR}Page 2"
        val insertionIndex = "Page 1${SHARED_PDF_PAGE_BREAK_CHAR}".length

        assertEquals(1, sharedPdfRichTextBlankInsertBreakCount(text, insertionIndex))
        assertEquals(1, sharedPdfRichTextBlankInsertBreakCount("Page 1", "Page 1".length))
        assertEquals(1, sharedPdfRichTextBlankInsertBreakCount("Page 1", 0))
    }

    @Test
    fun `blank page insertion uses two page breaks only for measured text boundaries with following content`() {
        val text = "Page 1Page 2"
        val insertionIndex = "Page 1".length

        assertEquals(2, sharedPdfRichTextBlankInsertBreakCount(text, insertionIndex))
        assertEquals(
            insertionIndex,
            sharedPdfRichTextInsertionIndexForPage(
                insertPageIndex = 1,
                pageLayouts = listOf(
                    SharedPdfRichPageLayout(
                        pageIndex = 0,
                        visibleText = AnnotatedString("Page 1"),
                        globalStartIndex = 0,
                        globalEndIndex = insertionIndex,
                        pageHeightPx = 1_000f
                    ),
                    SharedPdfRichPageLayout(
                        pageIndex = 1,
                        visibleText = AnnotatedString("Page 2"),
                        globalStartIndex = insertionIndex,
                        globalEndIndex = text.length,
                        pageHeightPx = 1_000f
                    )
                ),
                textLength = text.length
            )
        )
    }

    @Test
    fun `layout config follows Android geometry and density change policy`() {
        val density = Density(density = 2f, fontScale = 1.1f)

        assertFalse(
            shouldUpdateSharedPdfRichTextLayoutConfig(
                previousWidth = 1_000f,
                previousHeight = 1_414f,
                previousDensity = density,
                width = 1_000f,
                height = 1_414f,
                density = density,
            )
        )
        assertTrue(
            shouldUpdateSharedPdfRichTextLayoutConfig(
                previousWidth = 1_000f,
                previousHeight = 1_414f,
                previousDensity = density,
                width = 1_000f,
                height = 1_200f,
                density = density,
            )
        )
        assertTrue(
            shouldUpdateSharedPdfRichTextLayoutConfig(
                previousWidth = 1_000f,
                previousHeight = 1_414f,
                previousDensity = density,
                width = 1_000f,
                height = 1_414f,
                density = Density(density = 2f, fontScale = 1.2f),
            )
        )
    }

    @Test
    fun `controller platform adapters preserve Android load-if-empty and focus behavior`() = runTest {
        var focusRequests = 0
        var resolvedFontPath: String? = null
        val controller = SharedPdfRichTextController(
            scope = this,
            documentToAnnotatedString = { document, _ -> AnnotatedString(document.text) },
            annotatedStringToDocument = { text, _ -> SharedPdfRichDocument(text.text) },
            styleForFontPath = { style, path ->
                resolvedFontPath = path
                style.copy(fontWeight = FontWeight.Bold)
            },
            onEditingFocusRequested = { focusRequests++ },
        )

        controller.loadDocumentIfEmpty(SharedPdfRichDocument(text = "first"))
        controller.loadDocumentIfEmpty(SharedPdfRichDocument(text = "second"))
        controller.requestEditingFocus()
        controller.updateCurrentStyle(SpanStyle(), fontPath = "asset:fonts/lora.ttf")

        assertEquals("first", controller.globalTextFieldValue.text)
        assertTrue(controller.hasRenderableText)
        assertEquals("asset:fonts/lora.ttf", resolvedFontPath)
        assertEquals(FontWeight.Bold, controller.currentStyle.fontWeight)
        assertEquals(2, focusRequests)
    }

    @Test
    fun `immediate save captures the latest edit before debounce completes`() = runTest {
        var saved = SharedPdfRichDocument()
        val controller = SharedPdfRichTextController(
            scope = this,
            onDocumentChange = { saved = it },
            documentToAnnotatedString = { document, _ -> AnnotatedString(document.text) },
            annotatedStringToDocument = { text, _ -> SharedPdfRichDocument(text.text) },
        )

        controller.onValueChanged(TextFieldValue("latest edit"))
        controller.saveImmediate()

        assertEquals("latest edit", saved.text)
    }
}
