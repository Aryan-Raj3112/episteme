package com.aryan.reader.shared.reader

import com.aryan.reader.shared.pdf.HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION
import com.aryan.reader.shared.ui.SHARED_STICKY_NOTE_2_PATH_DATA
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The note mark is drawn by the document, so the script is the whole implementation on the shared
 * WebView surfaces. These assertions pin the parts a refactor can silently drop: the mark layer, the
 * glyph it draws, and the bridge call that opens the same sheet a tap on the highlight opens.
 */
class ReaderHtmlNoteBadgeScriptTest {

    private val script = readerHtmlAnnotationScript()

    @Test
    fun `script schedules the note mark pass`() {
        assertTrue(script.contains("window.readerScheduleNoteBadgeUpdate"))
    }

    @Test
    fun `reapplying highlights rebuilds the marks`() {
        // The payload is the only thing that knows which highlights carry a note, so a note added or
        // cleared arrives as a new payload and the marks have to follow it.
        val applyStart = script.indexOf("window.readerApplyHighlights = function")
        val applyEnd = script.indexOf("function scheduleReaderHighlightReconcile")
        assertTrue(applyStart in 0 until applyEnd)
        assertTrue(
            script.substring(applyStart, applyEnd).contains("readerScheduleNoteBadgeUpdate()"),
            "reconcile must refresh the note marks"
        )
    }

    @Test
    fun `mark draws the sticky note glyph and follows the highlight colour`() {
        assertTrue(script.contains(SHARED_STICKY_NOTE_2_PATH_DATA))
        assertTrue(script.contains("highlight.badgeCss"))
    }

    @Test
    fun `mark taps reuse the highlight click bridge`() {
        assertTrue(script.contains("sendReaderHighlightClick(id)"))
    }

    @Test
    fun `mark size and protrusion come from the shared geometry`() {
        // A copy of the numbers here would drift from the Compose surfaces the moment either moved.
        assertTrue(script.contains(HIGHLIGHT_NOTE_BADGE_HEIGHT_FRACTION.toString()))
        assertTrue(script.contains("leadingAbove"))
    }
}
