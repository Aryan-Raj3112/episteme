package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins that the compile-once batch paths are output-identical to per-text
 * application: only ICU compilation is shared, never results. Guards the
 * main-thread ANR fix (regexes were recompiled per chunk × per rule during
 * chapter renders).
 */
class ReaderReplacementBatchTest {

    private val rules = listOf(
        ReaderWordReplacementRule(id = "literal", from = "Alice", to = "Mina"),
        ReaderWordReplacementRule(
            id = "regex",
            from = """A(\w+)""",
            to = "B\$1",
            isRegex = true,
            wholeWord = false,
        ),
        ReaderWordReplacementRule(id = "invalid", from = "(unclosed", to = "x", isRegex = true),
        ReaderWordReplacementRule(id = "off", from = "draft", to = "unused", enabled = false),
        ReaderWordReplacementRule(id = "blank", from = "   ", to = "unused"),
        ReaderWordReplacementRule(id = "case", from = "BOB", to = "Robert", matchCase = true),
    )

    private val texts = listOf(
        "Alice looked around.",
        "",
        "no matches here",
        "BOB and bob went to see Alice.",
        "An Aardvark Ate Apples.",
    )

    @Test
    fun `word batch apply matches per-text apply including errors`() {
        val expected = texts.map { ReaderWordReplacementEngine.apply(it, rules) }
        val actual = ReaderWordReplacementEngine.applyAll(texts, rules)

        assertEquals(expected, actual)
    }

    @Test
    fun `word batch apply with empty inputs matches per-text behavior`() {
        assertEquals(emptyList(), ReaderWordReplacementEngine.applyAll(emptyList(), rules))
        assertEquals(
            texts.map { ReaderWordReplacementApplyResult(text = it) },
            ReaderWordReplacementEngine.applyAll(texts, emptyList()),
        )
    }

    @Test
    fun `tts batch apply matches per-text apply including errors`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(
                ReaderTtsReplacementRule(id = "dr", from = "Dr.", to = "Doctor", wholeWord = false),
                ReaderTtsReplacementRule(id = "bad", from = "(unclosed", to = "x", isRegex = true),
            ),
            bookRules = mapOf("book" to listOf(
                ReaderTtsReplacementRule(id = "local", from = "Alice", to = "Mina"),
            )),
        )

        val expected = texts.map { ReaderTtsReplacementEngine.apply(it, preferences, "book") }
        val actual = ReaderTtsReplacementEngine.applyAll(texts, preferences, "book")

        assertEquals(expected, actual)
    }

    @Test
    fun `tts batch apply honors disabled preferences`() {
        val preferences = ReaderTtsReplacementPreferences(
            isEnabled = false,
            globalRules = listOf(
                ReaderTtsReplacementRule(id = "dr", from = "Dr.", to = "Doctor", wholeWord = false),
            ),
        )

        assertEquals(
            texts.map { ReaderTtsReplacementEngine.apply(it, preferences, "book") },
            ReaderTtsReplacementEngine.applyAll(texts, preferences, "book"),
        )
    }

    @Test
    fun `tts chunk list extension matches per-chunk application`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(
                ReaderTtsReplacementRule(id = "dr", from = "Dr.", to = "Doctor", wholeWord = false),
            ),
        )
        val chunks = texts.mapIndexed { index, text ->
            ReaderTtsChunk(
                index = index,
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "Chapter",
                text = text,
                startOffset = 0,
                endOffset = text.length,
            )
        }

        val expected = chunks.map { it.withTtsReplacements(preferences, "book") }
        val actual = chunks.withTtsReplacements(preferences, "book")

        assertEquals(expected, actual)
    }
}
