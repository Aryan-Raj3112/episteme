package com.aryan.reader.pdf

import org.junit.Assert.assertFalse
import org.junit.Test

class PdfTextBoxInputOwnershipTest {

    @Test
    fun selectedLegacyTextBoxDisablesPageRichTextInput() {
        assertFalse(
            isPdfRichTextInputEnabled(
                isEditMode = true,
                selectedTool = InkType.TEXT,
                selectedTextBoxId = "box",
            )
        )
    }

    @Test
    fun pageRichTextInputStaysDisabledWhenNoLegacyTextBoxIsSelected() {
        // Currently retired: page rich text is hidden on Android, text boxes
        // only (docs/android-page-rich-text-retirement.md). The page editor
        // never owns the IME, even with no box selected.
        assertFalse(
            isPdfRichTextInputEnabled(
                isEditMode = true,
                selectedTool = InkType.TEXT,
                selectedTextBoxId = null,
            )
        )
    }
}
