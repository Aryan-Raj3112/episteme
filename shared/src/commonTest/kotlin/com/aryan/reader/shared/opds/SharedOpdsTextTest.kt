package com.aryan.reader.shared.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class SharedOpdsTextTest {
    @Test
    fun `stripXmlTags removes tags with single spaces`() {
        assertEquals("Hello  world !", SharedOpdsText.stripXmlTags("Hello <b>world</b>!"))
        assertEquals("  ", SharedOpdsText.stripXmlTags("<a><b>"))
        assertEquals("", SharedOpdsText.stripXmlTags(""))
        assertEquals("no tags", SharedOpdsText.stripXmlTags("no tags"))
    }

    @Test
    fun `stripXmlTags keeps angle brackets that never close`() {
        // Matches the old <[^>]+> semantics: without a later '>', the '<'
        // is literal text, not a tag.
        assertEquals("a < b", SharedOpdsText.stripXmlTags("a < b"))
        assertEquals("x <", SharedOpdsText.stripXmlTags("x <"))
        assertEquals("<a", SharedOpdsText.stripXmlTags("<a"))
        assertEquals("<>", SharedOpdsText.stripXmlTags("<>"))
        assertEquals("a<>b", SharedOpdsText.stripXmlTags("a<>b"))
    }

    @Test
    fun `stripXmlTags skips to the first closing bracket`() {
        assertEquals("a   d", SharedOpdsText.stripXmlTags("a < b <c> d"))
    }

    @Test
    fun `cleanSummary collapses markup and whitespace`() {
        assertEquals("Hello World", SharedOpdsText.cleanSummary("<p>Hello<br>World</p>"))
        assertEquals("", SharedOpdsText.cleanSummary(null))
        assertEquals("", SharedOpdsText.cleanSummary("   "))
        assertEquals("a < b", SharedOpdsText.cleanSummary("a < b"))
    }

    @Test
    fun `long plain text full of comparisons strips in linear time`() {
        // Regression test: the old <[^>]+> regex backtracked quadratically
        // here (~31s for 250KB), freezing the loading screen with no error
        // and no timeout. A '<' with no later '>' is literal text.
        val hostile = "a < b ".repeat(50_000)
        val (stripped, elapsed) = timedMs { SharedOpdsText.stripXmlTags(hostile) }
        assertEquals(hostile, stripped)
        assertTrue(elapsed < 5_000, "stripping took ${elapsed}ms")
    }

    @Test
    fun `long text with one distant tag close stays fast`() {
        val body = "a < b ".repeat(20_000)
        val tail = " d".repeat(1_000)
        val input = body + "<c>" + tail
        val (stripped, elapsed) = timedMs { SharedOpdsText.stripXmlTags(input) }
        assertEquals("a  " + tail, stripped)
        assertTrue(elapsed < 5_000, "stripping took ${elapsed}ms")
    }

    private inline fun <T> timedMs(block: () -> T): Pair<T, Long> {
        val mark = TimeSource.Monotonic.markNow()
        val result = block()
        return result to mark.elapsedNow().inWholeMilliseconds
    }
}
