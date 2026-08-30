package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfEmbeddedAnnotationRichTextTest {
    @Test
    fun `full xhtml document with declaration and namespaces reduces to plain text`() {
        val markup = """
            <?xml version="1.0" encoding="UTF-8"?>
            <body xmlns="http://www.w3.org/1999/xhtml" xmlns:xhtml="http://www.w3.org/1999/xhtml">
            <p>First paragraph</p>
            <p>Second paragraph</p>
            </body>
        """.trimIndent()

        assertEquals("First paragraph\nSecond paragraph", sharedPdfEmbeddedAnnotationRichText(markup))
    }

    @Test
    fun `entities and line breaks decode into readable text`() {
        val markup = "<body><p>Tom &amp; Jerry &lt;3 &#x2019;quoted&#39;</p><p>Line1<br/>Line2</p></body>"

        assertEquals("Tom & Jerry <3 \u2019quoted'\nLine1\nLine2", sharedPdfEmbeddedAnnotationRichText(markup))
    }

    @Test
    fun `markup without visible text becomes empty`() {
        assertEquals(
            "",
            sharedPdfEmbeddedAnnotationRichText(
                "<?xml version=\"1.0\"?><body xmlns=\"http://www.w3.org/1999/xhtml\"><p></p></body>"
            ),
        )
        assertEquals("", sharedPdfEmbeddedAnnotationRichText("   "))
    }

    @Test
    fun `plain text contents pass through unchanged`() {
        assertEquals("Simple note", sharedPdfEmbeddedAnnotationRichText("Simple note"))
    }

    @Test
    fun `cdata payload is preserved as text`() {
        assertEquals("Kept", sharedPdfEmbeddedAnnotationRichText("<body><![CDATA[Kept]]></body>"))
    }
}
