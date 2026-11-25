package com.aryan.reader.epubreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tab switch must restore the position the reader was left at, which only happens when the live
 * CFI captured from the WebView parses cleanly. These are the shapes `evaluateJavascript` can hand
 * back for `window.getCurrentCfi()` — and the ones that mean "could not capture".
 */
class ReadingPositionCaptureTest {

    @Test
    fun `object response yields its cfi`() {
        assertEquals("/4/2:34", readingPositionCfiFromJavascriptResult("""{"cfi":"/4/2:34","log":[]}"""))
    }

    @Test
    fun `quoted string response is unwrapped before parsing`() {
        val raw = "\"{\\\"cfi\\\":\\\"/6/4!/4/2:12\\\",\\\"log\\\":[]}\""
        assertEquals("/6/4!/4/2:12", readingPositionCfiFromJavascriptResult(raw))
    }

    @Test
    fun `whitespace around the response is tolerated`() {
        assertEquals("/4/2", readingPositionCfiFromJavascriptResult("  {\"cfi\":\"/4/2\"}  "))
    }

    @Test
    fun `non cfi values are rejected so the flush fallback runs`() {
        assertNull(readingPositionCfiFromJavascriptResult("""{"cfi":"latest","log":[]}"""))
        assertNull(readingPositionCfiFromJavascriptResult("""{"cfi":"","log":[]}"""))
        assertNull(readingPositionCfiFromJavascriptResult("""{"log":[]}"""))
    }

    @Test
    fun `garbage null and timeout results yield null`() {
        assertNull(readingPositionCfiFromJavascriptResult(null))
        assertNull(readingPositionCfiFromJavascriptResult(""))
        assertNull(readingPositionCfiFromJavascriptResult("null"))
        assertNull(readingPositionCfiFromJavascriptResult("\"not json"))
        assertNull(readingPositionCfiFromJavascriptResult("42"))
    }
}
