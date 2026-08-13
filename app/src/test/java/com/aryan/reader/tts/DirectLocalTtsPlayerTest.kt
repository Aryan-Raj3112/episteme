package com.aryan.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectLocalTtsPlayerTest {

    @Test
    fun `utterance id round trips generation chunk and resume offset`() {
        val id = localTtsUtteranceId(generation = 12, chunkIndex = 34, spokenStartOffset = 56)

        assertEquals(LocalTtsUtterance(12, 34, 56), parseLocalTtsUtteranceId(id))
    }

    @Test
    fun `malformed or foreign utterance ids are ignored`() {
        assertNull(parseLocalTtsUtteranceId(null))
        assertNull(parseLocalTtsUtteranceId("other:1:2:3"))
        assertNull(parseLocalTtsUtteranceId("episteme-local:x:2:3"))
    }

    @Test
    fun `range callback maps resumed spoken range to source offset`() {
        assertEquals(
            142,
            resolveLocalTtsSourceOffset(
                sourceStartOffset = 100,
                spokenStartOffset = 30,
                rangeStart = 12,
                visibleTextMatchesSpokenText = true
            )
        )
    }

    @Test
    fun `replacement text keeps highlight anchored to visible chunk`() {
        assertEquals(
            100,
            resolveLocalTtsSourceOffset(
                sourceStartOffset = 100,
                spokenStartOffset = 30,
                rangeStart = 12,
                visibleTextMatchesSpokenText = false
            )
        )
    }
}
