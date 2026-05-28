package com.aryan.reader

import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderWordReplacementRule
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookReplacementHtmlTest {
    @Test
    fun `html replacement rewrites visible text for matching book`() {
        val document = Jsoup.parse(
            """
            <html>
              <body>
                <p id="first">Alice &amp; Alice</p>
                <a href="chapter.xhtml">Alice link</a>
              </body>
            </html>
            """.trimIndent(),
        )
        val preferences = ReaderBookReplacementPreferences(
            fileRules = mapOf(
                "book" to listOf(rule(from = "Alice", to = "Alicia")),
            ),
        )

        val changed = applyBookReplacementsToHtmlDocument(document, preferences, "book")

        assertTrue(changed)
        assertEquals("Alicia & Alicia", document.selectFirst("p")?.text())
        assertEquals("Alicia link", document.selectFirst("a")?.text())
        assertEquals("chapter.xhtml", document.selectFirst("a")?.attr("href"))
    }

    @Test
    fun `html replacement skips blocked script text`() {
        val document = Jsoup.parse(
            """
            <html>
              <body>
                <p>Alice</p>
                <script>var name = "Alice";</script>
              </body>
            </html>
            """.trimIndent(),
        )
        val preferences = ReaderBookReplacementPreferences(
            fileRules = mapOf(
                "book" to listOf(rule(from = "Alice", to = "Alicia")),
            ),
        )

        val changed = applyBookReplacementsToHtmlDocument(document, preferences, "book")

        assertTrue(changed)
        assertEquals("Alicia", document.selectFirst("p")?.text())
        assertTrue(document.selectFirst("script")?.html()?.contains("Alice") == true)
    }

    private fun rule(
        id: String = "rule",
        from: String,
        to: String,
    ): ReaderWordReplacementRule {
        return ReaderWordReplacementRule(
            id = id,
            from = from,
            to = to,
        )
    }
}
