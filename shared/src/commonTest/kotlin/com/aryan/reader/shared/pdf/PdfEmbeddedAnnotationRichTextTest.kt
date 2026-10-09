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

    @Test
    fun `multi-line comment containing a greater-than sign is removed whole`() {
        // Needs BOTH properties to hold. A single-line comment is matched even without spanning
        // newlines, because `.` already matches `>`, so `<!-- a > b -->` is fine either way. A
        // multi-line comment with no `>` is also fine, because the generic `<[^>]*>` stripper
        // spans newlines and eats it. Only the combination breaks: the prelude fails to match,
        // and `<[^>]*>` then stops at the `>` mid-comment, leaking the rest of it into the note
        // the user reads. PDF producers do emit comments like this.
        val markup = "<body><!-- audited: score > 4\n     reviewed 2026 --><p>Kept</p></body>"

        assertEquals("Kept", sharedPdfEmbeddedAnnotationRichText(markup).trimEnd())
    }

    @Test
    fun `a greater-than sign inside cdata stays as text`() {
        assertEquals(
            "a > b",
            sharedPdfEmbeddedAnnotationRichText("<body><![CDATA[a > b]]></body>"),
        )
    }
}
