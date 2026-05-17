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

        assertTrue("<p class=\"page-marker\">-- Page 3 --</p>" in html)
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
                "<p class=\"page-marker\">-- Page 1 --</p>\n" +
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
}
