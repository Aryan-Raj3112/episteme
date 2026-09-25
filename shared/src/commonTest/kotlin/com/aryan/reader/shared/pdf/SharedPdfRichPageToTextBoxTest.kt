package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedPdfRichPageToTextBoxTest {

    private val canvas = IntSize(800, 1200)

    private fun layout(page: Int, start: Int, end: Int): SharedPdfRichPageLayout =
        SharedPdfRichPageLayout(
            pageIndex = page,
            visibleText = AnnotatedString(""),
            globalStartIndex = start,
            globalEndIndex = end,
            pageHeightPx = 1000f,
        )

    private fun convert(doc: SharedPdfRichDocument, layouts: List<SharedPdfRichPageLayout>) =
        sharedPdfRichPagesToTextBoxes(doc, layouts) { canvas }

    @Test
    fun `blank document or layouts convert to nothing`() {
        assertTrue(convert(SharedPdfRichDocument(), listOf(layout(0, 0, 5))).isEmpty())
        assertTrue(convert(SharedPdfRichDocument(text = "hi"), emptyList()).isEmpty())
    }

    @Test
    fun `blank and whitespace-only pages are skipped`() {
        val doc = SharedPdfRichDocument(text = "hi\n   \nbye")
        val specs = convert(doc, listOf(layout(0, 0, 3), layout(1, 3, 7), layout(2, 7, 10)))
        assertEquals(listOf(0, 2), specs.map { it.pageIndex })
        assertEquals("hi", specs[0].text)
        assertEquals("bye", specs[1].text)
    }

    @Test
    fun `plain paragraphs become stacked boxes inside the editor rect`() {
        val doc = SharedPdfRichDocument(text = "hello\nworld")
        val specs = convert(doc, listOf(layout(0, 0, 11)))
        assertEquals(2, specs.size)
        assertEquals("hello", specs[0].text)
        assertEquals("world", specs[1].text)
        assertEquals(0.1f, specs[0].bounds.left, 0.0001f)
        assertEquals(0.9f, specs[0].bounds.right, 0.0001f)
        assertEquals(0.08f, specs[0].bounds.top, 0.0001f)
        assertTrue(specs[0].bounds.bottom < specs[1].bounds.top)
        assertTrue(specs[1].bounds.bottom <= 0.92f)
        assertTrue(specs[0].paragraphs.isEmpty())
    }

    @Test
    fun `paragraph attributes follow the global offset`() {
        val doc = SharedPdfRichDocument(
            text = "a\nb\nc",
            paragraphs = listOf(
                SharedPdfRichParagraph(),
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
                SharedPdfRichParagraph(listType = SharedPdfRichListType.BULLET),
            )
        )
        // Layout covers "b\nc" (global 2..5): plain "b" alone, bulleted "c" alone.
        val specs = convert(doc, listOf(layout(3, 2, 5)))
        assertEquals(2, specs.size)
        assertEquals("b", specs[0].text)
        assertEquals(
            listOf(SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER)),
            specs[0].paragraphs
        )
        assertEquals("c", specs[1].text)
        assertEquals(SharedPdfRichListType.BULLET, specs[1].paragraphs.single().listType)
    }

    @Test
    fun `consecutive numbered paragraphs stay in one box`() {
        val doc = SharedPdfRichDocument(
            text = "1. short\n2. longer item\nplain tail",
            paragraphs = listOf(
                SharedPdfRichParagraph(listType = SharedPdfRichListType.NUMBERED),
                SharedPdfRichParagraph(listType = SharedPdfRichListType.NUMBERED),
                SharedPdfRichParagraph(),
            )
        )
        val specs = convert(doc, listOf(layout(0, 0, 34)))
        assertEquals(2, specs.size)
        assertEquals("1. short\n2. longer item", specs[0].text)
        assertTrue(specs[0].paragraphs.all { it.listType == SharedPdfRichListType.NUMBERED })
        assertEquals("plain tail", specs[1].text)
    }

    @Test
    fun `different list types split into separate boxes`() {
        val doc = SharedPdfRichDocument(
            text = "• a\n1. b",
            paragraphs = listOf(
                SharedPdfRichParagraph(listType = SharedPdfRichListType.BULLET),
                SharedPdfRichParagraph(listType = SharedPdfRichListType.NUMBERED),
            )
        )
        val specs = convert(doc, listOf(layout(0, 0, 8)))
        assertEquals(listOf("• a", "1. b"), specs.map { it.text })
    }

    @Test
    fun `blank paragraphs are dropped`() {
        val doc = SharedPdfRichDocument(text = "hi\n\nbye")
        val specs = convert(doc, listOf(layout(0, 0, 7)))
        assertEquals(listOf("hi", "bye"), specs.map { it.text })
    }

    @Test
    fun `paragraphs truncate to the trimmed slice`() {
        val doc = SharedPdfRichDocument(
            text = "hi\n",
            paragraphs = listOf(
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.RIGHT),
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
            )
        )
        val specs = convert(doc, listOf(layout(0, 0, 3)))
        assertEquals("hi", specs.single().text)
        assertEquals(
            listOf(SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.RIGHT)),
            specs.single().paragraphs
        )
    }

    @Test
    fun `dominant span wins and markers stay inline`() {
        val doc = SharedPdfRichDocument(
            text = "1. short\n2. a much longer second item",
            spans = listOf(
                SharedPdfRichSpan(
                    start = 0, end = 9, color = 0xFFFF0000.toInt(), backgroundColor = 0,
                    fontSizeNorm = 0.03f, isBold = true, isItalic = false,
                    isUnderline = false, isStrikethrough = false,
                ),
                SharedPdfRichSpan(
                    start = 9, end = 37, color = 0xFF0000FF.toInt(), backgroundColor = 0,
                    fontSizeNorm = 0.04f, isBold = false, isItalic = true,
                    isUnderline = true, isStrikethrough = false, fontPath = "asset:fonts/lora.ttf",
                ),
            ),
            paragraphs = listOf(
                SharedPdfRichParagraph(listType = SharedPdfRichListType.NUMBERED),
                SharedPdfRichParagraph(listType = SharedPdfRichListType.NUMBERED),
            )
        )
        val specs = convert(doc, listOf(layout(0, 0, 37)))
        // One grouped box: the longer second item dominates the style.
        val spec = specs.single()
        assertEquals("1. short\n2. a much longer second item", spec.text)
        assertEquals(0xFF0000FF.toInt(), spec.colorArgb)
        assertEquals(0.04f, spec.fontSizeNorm, 0.0001f)
        assertEquals(false, spec.isBold)
        assertEquals(true, spec.isItalic)
        assertEquals(true, spec.isUnderline)
        assertEquals("asset:fonts/lora.ttf", spec.fontPath)
        assertTrue(spec.paragraphs.all { it.listType == SharedPdfRichListType.NUMBERED })
    }

    @Test
    fun `no spans falls back to defaults`() {
        val specs = convert(SharedPdfRichDocument(text = "plain"), listOf(layout(0, 0, 5)))
        val spec = specs.single()
        assertEquals(0xFF000000.toInt(), spec.colorArgb)
        assertEquals(
            SharedPdfTextAnnotationDefaults.displayFontSizeToPageRelative(16f),
            spec.fontSizeNorm, 0.0001f
        )
        assertNull(spec.fontPath)
        assertTrue(spec.paragraphs.isEmpty())
    }

    @Test
    fun `overflowing stack merges the remainder into one final box`() {
        val text = List(30) { "line number $it with some words" }.joinToString("\n")
        val specs = convert(
            SharedPdfRichDocument(text = text),
            listOf(layout(0, 0, text.length))
        )
        assertTrue(specs.size in 2 until 30)
        val bounds = specs.map { it.bounds }
        for (i in 0 until bounds.size - 1) {
            assertTrue(bounds[i].bottom <= bounds[i + 1].top + 0.0001f)
        }
        assertTrue(bounds.last().bottom <= 0.92f + 0.0001f)
        assertEquals(30, specs.sumOf { it.text.split("\n").size })
    }
}
