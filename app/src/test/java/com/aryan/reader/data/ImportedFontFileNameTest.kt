package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedFontFileNameTest {
    @Test
    fun importedFontFileNamePreservesVariableFontVariantTokens() {
        val fileName = importedFontFileName(
            displayName = "Pliant-Italic-VariableFont_wdth,wght",
            extension = "TTF"
        )

        assertEquals("Pliant-Italic-VariableFont_wdth,wght.ttf", fileName)
    }

    @Test
    fun importedFontFileNameRemovesPathUnsafeCharacters() {
        val fileName = importedFontFileName(
            displayName = """Pliant/Italic:VariableFont*wdth?wght""",
            extension = "t/tf"
        )

        assertEquals("Pliant_Italic_VariableFont_wdth_wght.ttf", fileName)
    }

    @Test
    fun importedFontFileNameFallsBackForBlankNames() {
        assertTrue(importedFontFileName("...", "ttf").startsWith("font."))
    }
}
