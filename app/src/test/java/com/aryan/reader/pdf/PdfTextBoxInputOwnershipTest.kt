package com.aryan.reader.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun pageRichTextInputRemainsEnabledWhenNoLegacyTextBoxIsSelected() {
        assertTrue(
            isPdfRichTextInputEnabled(
                isEditMode = true,
                selectedTool = InkType.TEXT,
                selectedTextBoxId = null,
            )
        )
    }
}
