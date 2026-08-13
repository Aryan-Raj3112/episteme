package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontFamilyMapperTest {
    @Test
    fun loadedFontsWinInCssOrderWithQuoteAndCaseNormalization() {
        val loaded = FontFamily.Serif
        assertEquals(
            loaded,
            resolveReaderFontFamily(
                listOf("Missing", " 'Book Face' ", "sans-serif"),
                mapOf("book face" to loaded)
            )
        )
    }

    @Test
    fun genericFallbackAndEmptyInputMatchAndroid() {
        assertEquals(
            FontFamily.Monospace,
            resolveReaderFontFamily(listOf("Missing", "monospace"), emptyMap())
        )
        assertNull(resolveReaderFontFamily(emptyList(), mapOf("book" to FontFamily.Serif)))
        assertNull(resolveReaderFontFamily(listOf("Missing"), emptyMap()))
    }
}
