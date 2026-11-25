package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the keyboard contract reported for both annotation editors: only the Delete/Save row steps
 * aside while typing. The editor chrome above it and the note field's own height stay put — growing
 * the field over the chrome is exactly what hid the editor behind the input.
 */
class SharedMobileAnnotationSheetLayoutTest {

    @Test
    fun `keyboard takes the bottom action row and nothing else`() {
        val withKeyboard = sharedMobileAnnotationSheetLayout(isImeVisible = true)

        assertTrue(withKeyboard.editorChromeVisible)
        assertEquals(SharedMobileAnnotationSheetNoteMaxLines, withKeyboard.noteFieldMaxLines)
        assertFalse(withKeyboard.bottomActionsVisible)
    }

    @Test
    fun `resting editor keeps the action row and the same field height`() {
        val atRest = sharedMobileAnnotationSheetLayout(isImeVisible = false)
        val withKeyboard = sharedMobileAnnotationSheetLayout(isImeVisible = true)

        assertTrue(atRest.editorChromeVisible)
        assertTrue(atRest.bottomActionsVisible)
        assertEquals(atRest.noteFieldMaxLines, withKeyboard.noteFieldMaxLines)
        assertEquals(5, atRest.noteFieldMaxLines)
    }
}
