package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderTtsReplacementEngineTest {
    @Test
    fun `literal replacement changes spoken text only`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(rule(from = "Dr.", to = "Doctor", wholeWord = false))
        )

        val result = ReaderTtsReplacementEngine.apply("Dr. Smith arrived.", preferences)

        assertEquals("Doctor Smith arrived.", result.text)
        assertEquals(listOf("rule"), result.appliedRuleIds)
    }

    @Test
    fun `phrase replacement handles multi word phrases`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(rule(from = "et al.", to = "and others", wholeWord = false))
        )

        val result = ReaderTtsReplacementEngine.apply("Smith et al. wrote it.", preferences)

        assertEquals("Smith and others wrote it.", result.text)
    }

    @Test
    fun `whole word replacement does not replace inside larger words`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(rule(from = "he", to = "they", wholeWord = true))
        )

        val result = ReaderTtsReplacementEngine.apply("he heard the theme", preferences)

        assertEquals("they heard the theme", result.text)
    }

    @Test
    fun `case sensitivity can be required per rule`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(rule(from = "NASA", to = "N A S A", matchCase = true))
        )

        val result = ReaderTtsReplacementEngine.apply("NASA and nasa", preferences)

        assertEquals("N A S A and nasa", result.text)
    }

    @Test
    fun `regex rule supports capture replacements`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(
                rule(
                    from = """\b([A-Z])\.\s*([A-Z])\.""",
                    to = "\$1 \$2",
                    isRegex = true,
                    wholeWord = false
                )
            )
        )

        val result = ReaderTtsReplacementEngine.apply("J. R. wrote it.", preferences)

        assertEquals("J R wrote it.", result.text)
    }

    @Test
    fun `invalid regex is skipped and reported`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(rule(from = "(", to = "open", isRegex = true))
        )

        val result = ReaderTtsReplacementEngine.apply("Keep this text.", preferences)

        assertEquals("Keep this text.", result.text)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `global rules run before book rules`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(rule(id = "global", from = "Dr.", to = "Doctor", wholeWord = false)),
            bookRules = mapOf(
                "book" to listOf(rule(id = "book", from = "Doctor", to = "Professor"))
            )
        )

        val result = ReaderTtsReplacementEngine.apply("Dr. Smith", preferences, bookId = "book")

        assertEquals("Professor Smith", result.text)
        assertEquals(listOf("global", "book"), result.appliedRuleIds)
    }

    @Test
    fun `book settings can disable inherited global rules`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(rule(id = "global", from = "Dr.", to = "Doctor", wholeWord = false)),
            bookSettings = mapOf(
                "book" to ReaderTtsReplacementBookSettings(disabledGlobalRuleIds = setOf("global"))
            )
        )

        val result = ReaderTtsReplacementEngine.apply("Dr. Smith", preferences, bookId = "book")

        assertEquals("Dr. Smith", result.text)
        assertTrue(result.appliedRuleIds.isEmpty())
    }

    @Test
    fun `preferences serialize and deserialize without losing rules`() {
        val preferences = ReaderTtsReplacementPreferences(
            isEnabled = false,
            globalRules = listOf(rule(id = "global", from = "Mr.", to = "Mister", wholeWord = false)),
            bookRules = mapOf(
                "book" to listOf(rule(id = "book", from = "St.", to = "Saint", wholeWord = false))
            ),
            bookSettings = mapOf(
                "book" to ReaderTtsReplacementBookSettings(
                    localRulesEnabled = false,
                    globalRulesEnabled = true,
                    disabledGlobalRuleIds = setOf("global")
                )
            )
        )

        val decoded = ReaderTtsReplacementPreferencesJson.decodeOrEmpty(
            ReaderTtsReplacementPreferencesJson.encode(preferences)
        )

        assertEquals(preferences, decoded)
    }

    private fun rule(
        id: String = "rule",
        from: String,
        to: String,
        enabled: Boolean = true,
        isRegex: Boolean = false,
        matchCase: Boolean = false,
        wholeWord: Boolean = true
    ): ReaderTtsReplacementRule {
        return ReaderTtsReplacementRule(
            id = id,
            from = from,
            to = to,
            enabled = enabled,
            isRegex = isRegex,
            matchCase = matchCase,
            wholeWord = wholeWord
        )
    }
}
