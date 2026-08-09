package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfReflowTest {
    @Test
    fun `detectRepeatingHeaderFooter returns edge text repeated across sampled pages`() {
        val samples = listOf(
            listOf("Book Title", "Chapter", "Body one", "12"),
            listOf("Book Title", "Chapter", "Body two", "13"),
            listOf("Book Title", "Chapter", "Body three", "14"),
            listOf("Book Title", "Chapter", "Body four", "15"),
            listOf("Other Title", "Chapter", "Body five", "16")
        )

        val result = SharedPdfReflowHtml.detectRepeatingHeaderFooter(samples)

        assertTrue("Book Title" in result)
        assertTrue("Chapter" in result)
        assertFalse("Body one" in result)
    }

    @Test
    fun `header detection preserves android threshold when some sampled pages have no text`() {
        assertEquals(
            setOf("Repeated Header"),
            SharedPdfReflowHtml.detectRepeatingHeaderFooter(
                listOf(
                    listOf("Repeated Header"),
                    listOf("Repeated Header"),
                    listOf("Repeated Header")
                )
            )
        )
    }

    @Test
    fun `buildPageHtml maps Android-style spans headings and lists`() {
        val page = SharedPdfReflowPage(
            pageNumber = 3,
            elements = listOf(
                textLine("Repeated Header", size = 10f),
                textLine("Chapter Heading", size = 22f, bold = true),
                textLine("This is a paragraph that should keep bold and italic words.", size = 12f, bold = true, italic = true),
                textLine("- first item", size = 12f),
                textLine("2. second item", size = 12f)
            )
        )

        val html = SharedPdfReflowHtml.buildPageHtml(
            page = page,
            headerFooterStrings = setOf("Repeated Header")
        )

        assertTrue("<p class=\"page-marker\">— Page 3 —</p>" in html)
        assertFalse("Repeated Header" in html)
        assertTrue("<h1>Chapter Heading</h1>" in html)
        assertTrue("<strong><em>This is a paragraph" in html)
        assertTrue("<ul>" in html)
        assertTrue("<li>first item</li>" in html)
        assertTrue("<ol>" in html)
        assertTrue("<li>second item</li>" in html)
    }

    @Test
    fun `empty page renders fallback section`() {
        val html = SharedPdfReflowHtml.buildPageHtml(
            SharedPdfReflowPage(pageNumber = 1, elements = emptyList())
        )

        assertEquals(
            "<section class=\"page-section\">\n" +
                "<p class=\"page-marker\">— Page 1 —</p>\n" +
                "<p><em>(No text on this page)</em></p>\n</section>\n",
            html
        )
    }

    private fun textLine(
        text: String,
        size: Float,
        bold: Boolean = false,
        italic: Boolean = false
    ): SharedPdfReflowTextElement {
        return SharedPdfReflowTextElement(
            SharedPdfReflowTextLine(
                spans = listOf(
                    SharedPdfReflowTextSpan(
                        text = text,
                        size = size,
                        isBold = bold,
                        isItalic = italic
                    )
                ),
                yPos = 0f,
                charCount = text.length
            )
        )
    }

    @Test
    fun `text lines split spans on size bold and italic changes`() {
        val rawText = "ABcdef"
        val lines = buildReaderReflowTextLines(
            rawText = rawText,
            charCount = rawText.length,
            sizeAt = { index -> if (index < 2) 20f else 10f },
            weightAt = { index -> if (index < 1) 800 else 400 },
            flagsAt = { index -> if (index == 1) 64 else 0 },
            boxTopYAt = { 12f }
        )

        assertEquals(1, lines.size)
        val spans = lines.first().spans
        assertEquals(3, spans.size)
        assertEquals("A", spans[0].text)
        assertTrue(spans[0].isBold)
        assertFalse(spans[0].isItalic)
        assertEquals(20f, spans[0].size)
        assertEquals("B", spans[1].text)
        assertTrue(spans[1].isItalic)
        assertEquals("cdef", spans[2].text)
        assertEquals(10f, spans[2].size)
        assertFalse(spans[2].isBold)
    }

    @Test
    fun `text lines break on carriage returns and skip blank lines`() {
        val rawText = "first line\r\n\r\nsecond\nthird"
        val lines = buildReaderReflowTextLines(
            rawText = rawText,
            charCount = rawText.length,
            sizeAt = { 12f },
            weightAt = { 400 },
            flagsAt = { 0 },
            boxTopYAt = { null }
        )

        assertEquals(3, lines.size)
        assertEquals("first line", lines[0].spans.joinToString("") { it.text })
        assertEquals("second", lines[1].spans.joinToString("") { it.text })
        assertEquals("third", lines[2].spans.joinToString("") { it.text })
    }

    @Test
    fun `text lines filter junk characters`() {
        val rawText = "a\uFFFDb\uFFFEc\uE000d\uD800e"
        val lines = buildReaderReflowTextLines(
            rawText = rawText,
            charCount = rawText.length,
            sizeAt = { 12f },
            weightAt = { 400 },
            flagsAt = { 0 },
            boxTopYAt = { null }
        )

        assertEquals("abcde", lines.single().spans.joinToString("") { it.text })
    }

    @Test
    fun `text lines normalize nbsp soft hyphen and tab`() {
        val rawText = "a\u00A0b\u00ADc\t d"
        val lines = buildReaderReflowTextLines(
            rawText = rawText,
            charCount = rawText.length,
            sizeAt = { 12f },
            weightAt = { 400 },
            flagsAt = { 0 },
            boxTopYAt = { null }
        )

        assertEquals("a b-c  d", lines.single().spans.joinToString("") { it.text })
    }

    @Test
    fun `text lines record baseline from char box`() {
        val rawText = "line"
        val lines = buildReaderReflowTextLines(
            rawText = rawText,
            charCount = rawText.length,
            sizeAt = { 12f },
            weightAt = { 400 },
            flagsAt = { 0 },
            boxTopYAt = { index -> if (index == 0) 42f else 0f }
        )

        assertEquals(42f, lines.single().yPos)
    }
}
