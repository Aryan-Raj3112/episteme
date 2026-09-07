package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the truncate-first contract of [txtFormatTracePreview].
 *
 * The preview feeds log lines but callers pass chapter-sized HTML. Escaping
 * the full string first copies multi-MB inputs several times for a 220-char
 * preview and OOMs low-memory devices (see docs/crashlytics-triage.md#15),
 * so the helper must bound its output regardless of input size while keeping
 * short-input output byte-identical to the old escape-then-truncate order.
 */
class ReaderHtmlTracePreviewTest {

    @Test
    fun `short input is fully escaped with no suffix`() {
        assertEquals("abc", "abc".txtFormatTracePreview())
        assertEquals("a\\\"b\\nc", "a\"b\nc".txtFormatTracePreview())
        assertEquals("", "".txtFormatTracePreview())
    }

    @Test
    fun `suffix appears only when input exceeds max length`() {
        val exact = "x".repeat(220)
        assertEquals(exact, exact.txtFormatTracePreview())
        assertEquals("x".repeat(220) + "...", ("x".repeat(221)).txtFormatTracePreview())
    }

    @Test
    fun `chapter sized input stays bounded`() {
        val chapterSized = "y\n".repeat(500_000)
        val preview = chapterSized.txtFormatTracePreview()
        assertTrue(
            preview.length < 500,
            "preview of a ${chapterSized.length}-char input must stay short, was ${preview.length}"
        )
        assertTrue(preview.endsWith("..."), "over-long input must carry the truncation suffix")
    }
}
