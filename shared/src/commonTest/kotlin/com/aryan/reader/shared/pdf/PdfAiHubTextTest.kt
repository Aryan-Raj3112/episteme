package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfAiHubTextTest {
    @Test
    fun `recap skips blanks and restores chronological order`() {
        val recap = buildPdfAiHubRecapText(listOf("page three", null, "  ", "page one"))
        assertEquals("page one\n\npage three", recap)
    }

    @Test
    fun `recap returns null when no page has text`() {
        assertNull(buildPdfAiHubRecapText(listOf(null, "   ", "")))
        assertNull(buildPdfAiHubRecapText(emptyList()))
    }

    @Test
    fun `recap caps payload at max chars`() {
        val recap = buildPdfAiHubRecapText(
            pageTextsNewestFirst = listOf("b".repeat(10), "a".repeat(10)),
            maxChars = 15,
        )
        assertEquals(15, recap?.length)
        assertTrue(recap!!.startsWith("a".repeat(10)))
    }

    @Test
    fun `single page recap passes through`() {
        assertEquals("hello", buildPdfAiHubRecapText(listOf("hello")))
    }
}
