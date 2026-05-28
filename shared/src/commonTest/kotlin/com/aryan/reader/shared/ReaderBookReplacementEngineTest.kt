package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderBookReplacementEngineTest {
    @Test
    fun `book replacements apply only to matching file id`() {
        val preferences = ReaderBookReplacementPreferences(
            fileRules = mapOf(
                "book-a" to listOf(rule(from = "Alice", to = "Alicia")),
                "book-b" to listOf(rule(from = "Alice", to = "Alix")),
            ),
        )

        assertEquals(
            "Alicia looked around.",
            ReaderBookReplacementEngine.apply("Alice looked around.", preferences, "book-a").text,
        )
        assertEquals(
            "Alix looked around.",
            ReaderBookReplacementEngine.apply("Alice looked around.", preferences, "book-b").text,
        )
        assertEquals(
            "Alice looked around.",
            ReaderBookReplacementEngine.apply("Alice looked around.", preferences, "missing").text,
        )
    }

    @Test
    fun `book replacements have no global fallback`() {
        val preferences = ReaderBookReplacementPreferences(
            fileRules = mapOf("" to listOf(rule(from = "global", to = "local"))),
        )

        assertEquals(
            "global rule",
            ReaderBookReplacementEngine.apply("global rule", preferences, "book").text,
        )
    }

    @Test
    fun `book replacements serialize and preserve per file rules`() {
        val preferences = ReaderBookReplacementPreferences(
            fileRules = mapOf(
                "book" to listOf(
                    rule(
                        id = "regex",
                        from = """A(\w+)""",
                        to = "B\$1",
                        isRegex = true,
                        wholeWord = false,
                    ),
                ),
            ),
        )

        val decoded = ReaderBookReplacementPreferencesJson.decodeOrEmpty(
            ReaderBookReplacementPreferencesJson.encode(preferences),
        )

        assertEquals(preferences, decoded)
    }

    @Test
    fun `signature only reflects active rules for file`() {
        val preferences = ReaderBookReplacementPreferences(
            fileRules = mapOf(
                "book" to listOf(
                    rule(id = "on", from = "old", to = "new"),
                    rule(id = "off", from = "draft", to = "unused", enabled = false),
                ),
            ),
        )

        val signature = preferences.signatureForFile("book")

        assertTrue("old" in signature)
        assertTrue("draft" !in signature)
        assertEquals("", preferences.signatureForFile("missing"))
    }

    private fun rule(
        id: String = "rule",
        from: String,
        to: String,
        enabled: Boolean = true,
        isRegex: Boolean = false,
        matchCase: Boolean = false,
        wholeWord: Boolean = true,
    ): ReaderWordReplacementRule {
        return ReaderWordReplacementRule(
            id = id,
            from = from,
            to = to,
            enabled = enabled,
            isRegex = isRegex,
            matchCase = matchCase,
            wholeWord = wholeWord,
        )
    }
}
