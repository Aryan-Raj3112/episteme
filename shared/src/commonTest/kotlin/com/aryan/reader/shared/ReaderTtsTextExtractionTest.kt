package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderTtsTextExtractionTest {

    @Test
    fun `sharedHtmlToPlainText strips scripts styles and converts blocks to lines`() {
        val html = """
            <html><head><style>.lead {}</style></head><body>
              <h1>Heading</h1>
              <p>First line.<br>Second line.</p>
              <script>window.bad = true</script>
              <p>Third &amp; final.</p>
            </body></html>
        """.trimIndent()

        val text = sharedHtmlToPlainText(html)

        assertEquals(
            "Heading\n\nFirst line.\nSecond line.\n\nThird & final.",
            text,
        )
        assertEquals(false, text.contains("bad", ignoreCase = true))
        assertEquals(false, text.contains(".lead", ignoreCase = true))
    }

    @Test
    fun `sharedDecodeHtmlEntities decodes numeric and named entities`() {
        assertEquals("A \u263A\u263A", sharedDecodeHtmlEntities("A&nbsp;&#x263A;&#9786;"))
        assertEquals("<<&\"'", sharedDecodeHtmlEntities("&lt;&#60;&amp;&quot;&apos;"))
        assertEquals("\u2014", sharedDecodeHtmlEntities("&mdash;"))
        assertEquals("\uD83D\uDE00", sharedDecodeHtmlEntities("&#x1F600;"))
        assertEquals("&unknown;", sharedDecodeHtmlEntities("&unknown;"))
    }
}
