package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderMarkdownParserTest {
    @Test
    fun `parses headings lists quotes and code blocks`() {
        val document = ReaderMarkdownParser.parse(
            """
            ## Summary

            - first point
            - second point

            > quoted context

            ```
            code line
            ```
            """.trimIndent()
        )

        assertIs<ReaderMarkdownBlock.Heading>(document.blocks[0])
        assertEquals("Summary", (document.blocks[0] as ReaderMarkdownBlock.Heading).text)
        assertEquals(listOf("first point", "second point"), (document.blocks[1] as ReaderMarkdownBlock.ListItems).items)
        assertEquals("quoted context", (document.blocks[2] as ReaderMarkdownBlock.Quote).text)
        assertEquals("code line", (document.blocks[3] as ReaderMarkdownBlock.CodeBlock).text)
    }
}
