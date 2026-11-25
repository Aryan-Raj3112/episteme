package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The markup flattener has to produce the same text a DOM walk produces, or the
 * shells disagree about which occurrence a hit is.
 */
class ReaderSearchHtmlTextTest {

    @Test
    fun `inline markup never splits a word`() {
        // A footnote reference is inline: "text" + "1" stays one run, exactly as
        // a DOM walk reads it, so ordinals agree with the rendered page.
        assertEquals(
            "Alpha beta1 and more",
            readerSearchHtmlToText("""<p>Alpha <em>beta</em><a href="#n1"><sup>1</sup></a> and more</p>"""),
        )
    }

    @Test
    fun `block boundaries separate words`() {
        assertEquals(
            "one two three",
            readerSearchHtmlToText("<div><p>one</p><p>two</p><br/>three</div>"),
        )
    }

    @Test
    fun `non rendered tags contribute no text`() {
        assertEquals(
            "Visible text",
            readerSearchHtmlToText(
                "<p>Visible<script>var a = 'hidden';</script>" +
                    "<style>.x{color:red}</style><noscript>Enable scripts</noscript>" +
                    "<template><p>template</p></template> text</p>"
            ),
        )
    }

    @Test
    fun `entities decode and whitespace collapses`() {
        assertEquals(
            "a & b <c> de",
            readerSearchHtmlToText("<p>a&nbsp;&amp;\n\n  b &lt;c&gt; &#100;&#x65;</p>"),
        )
    }

    @Test
    fun `attributes and comments are not text`() {
        assertEquals(
            "keep drop",
            readerSearchHtmlToText("""<p class="x" title="not text">keep<!-- drop --> drop</p>"""),
        )
    }

    @Test
    fun `empty and text only input`() {
        assertEquals("", readerSearchHtmlToText(""))
        assertEquals("plain prose", readerSearchHtmlToText("  plain   prose  "))
    }
}
