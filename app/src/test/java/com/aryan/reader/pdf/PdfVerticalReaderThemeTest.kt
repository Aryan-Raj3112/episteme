package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import com.aryan.reader.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PdfVerticalReaderThemeTest {

    @Test
    fun `vertical background falls back when theme color is unspecified`() {
        val theme = ReaderTheme(
            id = "custom-without-background",
            name = "Custom",
            backgroundColor = Color.Unspecified,
            textColor = Color.Black,
            isDark = false
        )

        val background = resolvePdfVerticalPageBackgroundColor(theme)

        assertEquals(Color.White, background)
        assertNotEquals(Color.Unspecified, background)
    }

    @Test
    fun `vertical background keeps explicit reverse theme black`() {
        val theme = ReaderTheme(
            id = "reverse",
            name = "Reverse",
            backgroundColor = Color.White,
            textColor = Color.Black,
            isDark = true
        )

        assertEquals(Color.Black, resolvePdfVerticalPageBackgroundColor(theme))
    }
}
