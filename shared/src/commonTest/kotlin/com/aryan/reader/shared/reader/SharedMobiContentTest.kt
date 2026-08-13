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
