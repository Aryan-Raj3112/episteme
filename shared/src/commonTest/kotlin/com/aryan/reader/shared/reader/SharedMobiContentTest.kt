package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobiContentTest {
    @Test
    fun splitsContentUsingAndroidStyleTocBytePositions() {
        val html = "<h1>One</h1><p>A</p><h1>Two</h1><p>B</p>"
        val second = html.substring(0, html.indexOf("<h1>Two")).encodeToByteArray().size

        val sections = splitMobiHtml(
            html,
            listOf(SharedMobiTocPoint("One", 0), SharedMobiTocPoint("Two", second)),
            "Book",
        )

        assertEquals(listOf("One", "Two"), sections.map { it.title })
        assertTrue(sections[0].html.contains("<p>A</p>"))
        assertTrue(sections[1].html.contains("<p>B</p>"))
    }

    @Test
    fun tocSplitInsideTagDoesNotLeakAttributeTail() {
        val html = "<div><p>Intro</p><div id=\"ch9\" aid=\"2RHM2\">Chapter 9 text</div></div>"
        val bytes = html.encodeToByteArray()
        // Split inside `<div id="ch9" ...>` at the `9`, reproducing chapters that
        // started with visible `9" aid="2RHM2">`.
        val splitInsideTag = html.indexOf("ch9") + "ch".length
        val splitBytes = html.substring(0, splitInsideTag).encodeToByteArray().size
        require(splitBytes in 1 until bytes.size)

        val sections = splitMobiHtml(
            html,
            listOf(SharedMobiTocPoint("One", 0), SharedMobiTocPoint("Two", splitBytes)),
            "Book",
        )

        assertEquals(2, sections.size)
        assertFalse(sections[1].html.contains("aid="), "chapter must not start with tag tail: ${sections[1].html}")
        assertFalse(sections[1].html.startsWith("9\""), "chapter must not start with tag tail: ${sections[1].html}")
        assertTrue(sections[1].html.contains("Chapter 9 text"))
    }

    @Test
    fun tocSplitInsideQuotedAttributeSkipsPastTagEnd() {
        val html = "<p>First</p><p title=\"a > b\">Second</p>"
        val bytes = html.encodeToByteArray()
        val tagStart = html.indexOf("<p title")
        // Split inside the quoted `a > b` value; the `>` inside quotes must not
        // end the tag early.
        val splitInsideQuotes = html.indexOf("a > b") + 1
        val splitBytes = html.substring(0, splitInsideQuotes).encodeToByteArray().size
        require(splitBytes in 1 until bytes.size)
        require(tagStart >= 0)

        val sections = splitMobiHtml(
            html,
            listOf(SharedMobiTocPoint("One", 0), SharedMobiTocPoint("Two", splitBytes)),
            "Book",
        )

        assertEquals(2, sections.size)
        assertTrue(sections[1].html.contains("Second"))
        // The second chapter must start at clean text or a whole tag, never mid-attribute.
        assertFalse(sections[1].html.contains("title="))
    }

    @Test
    fun rewritesKindleImagesCssAndRecindex() {
        val result = rewriteMobiResourceReferences(
            """<link href="kindle:flow:0001?mime=text/css"><img src="kindle:embed:0001"><img recindex="2">""",
            listOf("data:image/jpeg;base64,one", "data:image/png;base64,two"),
            mapOf(1 to "data:text/css;base64,css"),
        )

        assertTrue(result.contains("data:text/css;base64,css"))
        assertTrue(result.contains("data:image/jpeg;base64,one"))
        assertTrue(result.contains("data:image/png;base64,two"))
        assertFalse(result.contains("kindle:"))
        assertFalse(result.contains("recindex"))
    }
}
