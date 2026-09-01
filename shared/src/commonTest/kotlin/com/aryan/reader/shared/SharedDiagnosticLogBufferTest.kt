package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedDiagnosticLogBufferTest {
    @Test
    fun `buffer keeps newest entries within entry and character budgets`() {
        val buffer = SharedDiagnosticLogBuffer(maxEntries = 2, maxCharacters = 7)

        buffer.append("one")
        buffer.append("two")
        buffer.append("three")

        assertEquals(listOf("three"), buffer.snapshot())
        assertEquals(1, buffer.size)
    }

    @Test
    fun `blank entries do not consume diagnostics budget`() {
        val buffer = SharedDiagnosticLogBuffer(maxEntries = 2, maxCharacters = 20)

        buffer.append("   ")
        buffer.append(" useful ")

        assertEquals(listOf(" useful"), buffer.snapshot())
        assertTrue(buffer.size == 1)
    }
}
