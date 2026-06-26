package com.aryan.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsUtilsTest {
    @Test
    fun `split text enforces hard cap inside long sentence`() {
        val text = "This is a long sentence with many clauses, " +
            "and it should be divided without waiting for a sentence boundary ".repeat(8) +
            "before it finally ends."

        val chunks = splitTextIntoChunks(text, maxLengthPerChunk = 250)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 250 })
    }

    @Test
    fun `long sentence split prefers punctuation before hard cut`() {
        val text = "Alpha beta gamma, " + "word ".repeat(20) + "tail"

        val parts = splitLongTtsSentence(text, maxLength = 30)

        assertEquals("Alpha beta gamma,", parts.first())
        assertTrue(parts.all { it.length <= 30 })
    }
}
