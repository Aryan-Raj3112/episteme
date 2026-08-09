package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderLinkStyleTest {
    @Test
    fun `light Android theme selects benchmark blue and translucent background`() {
        val style = readerLinkSpanStyle(
            isDarkTheme = false,
            themeBackgroundColor = Color.White,
            themeTextColor = Color.Black,
        )

        assertEquals(Color(0xFF005FCC), style.color)
        assertEquals(Color(0x29005FCC), style.background)
        assertTrue(style.textDecoration?.contains(TextDecoration.Underline) == true)
    }

    @Test
    fun `dark Android theme keeps strikethrough when adding underline`() {
        val style = SpanStyle(textDecoration = TextDecoration.LineThrough).withReaderLinkStyle(
            isDarkTheme = true,
            themeBackgroundColor = Color.Black,
            themeTextColor = Color.White,
        )

        assertEquals(Color(0xFF7DD3FC), style.color)
        assertEquals(Color(0x3D7DD3FC), style.background)
        assertTrue(style.textDecoration?.contains(TextDecoration.LineThrough) == true)
        assertTrue(style.textDecoration?.contains(TextDecoration.Underline) == true)
    }

    @Test
    fun `unspecified colors retain Android light fallbacks`() {
        val style = readerLinkSpanStyle(
            isDarkTheme = false,
            themeBackgroundColor = Color.Unspecified,
            themeTextColor = Color.Unspecified,
        )

        assertEquals(Color(0xFF005FCC), style.color)
    }
}
