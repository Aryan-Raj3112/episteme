package com.aryan.reader.shared.docparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedMarkdownConverterTest {

    @Test
    fun headingsStartSectionsLikeAndroidMarkdownImport() {
        val markdown = """
            # First

            intro text

            ## Nested Topic

            more text
        """.trimIndent()

        val sections = SharedMarkdownConverter.convert(markdown)
        assertEquals(listOf("First", "Nested Topic"), sections.map { it.title })
        assertEquals(listOf(0, 1), sections.map { it.depth })
    }

    @Test
    fun documentsWithoutHeadingsBecomeSinglePage() {
        val sections = SharedMarkdownConverter.convert("Just a paragraph.\n\nAnother one.")
        assertEquals(1, sections.size)
        assertEquals(null, sections.single().title)
    }

    @Test
    fun rendersInlineFormatting() {
        val html = SharedMarkdownConverter.renderInlines(
            "This is **bold**, *italic*, ~~gone~~, `code`, and a [link](https://example.com).",
        )
        assertTrue("<strong>bold</strong>" in html)
        assertTrue("<em>italic</em>" in html)
        assertTrue("<del>gone</del>" in html)
        assertTrue("<code>code</code>" in html)
        assertTrue("""<a href="https://example.com">link</a>""" in html)
    }

    @Test
    fun escapesHtmlAndBlocksScriptUrls() {
        val html = SharedMarkdownConverter.renderInlines("<script>alert(1)</script> and ![x](javascript:alert(2))")
        assertTrue("<script>" !in html)
        assertTrue("&lt;script&gt;" in html)
        assertTrue("""src=""" !in html)
    }

    @Test
    fun rendersTablesStrikethroughTasksAndCodeFences() {
        val markdown = """
            | A | B |
            |---|:--:|
            | 1 | 2 |

            - [ ] todo
            - [x] done

            ```kotlin
            val x = 1 < 2 && 3 > 2
            ```
        """.trimIndent()

        val html = SharedMarkdownConverter.convert(markdown).joinToString("\n") { it.html }
        assertTrue("<table>" in html)
        assertTrue("""<th style="text-align:center">""" in html)
        assertTrue("""<input type="checkbox" disabled="" /> """ in html)
        assertTrue("checked=" in html)
        assertTrue("""<pre><code class="language-kotlin">""" in html)
        assertTrue("1 &lt; 2 &amp;&amp; 3 &gt; 2" in html)
    }

    @Test
    fun autolinksBareUrls() {
        val html = SharedMarkdownConverter.renderInlines("See https://example.com/x now.")
        assertTrue("""<a href="https://example.com/x">https://example.com/x</a>""" in html)
    }

    @Test
    fun blockquotesNestParagraphs() {
        val html = SharedMarkdownConverter.convert("> quoted line").single().html
        assertTrue("<blockquote>" in html)
        assertTrue("<p>quoted line</p>" in html)
    }
}
