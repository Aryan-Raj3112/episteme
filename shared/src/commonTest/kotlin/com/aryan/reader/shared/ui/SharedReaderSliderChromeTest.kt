package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedReaderSliderChromeTest {
    @Test
    fun lightPageKeepsBlackContentAndVisiblePrimary() {
        val colors = sharedReaderSliderChromeColors(
            pageBackground = Color.White,
            pageText = Color.Black,
            themePrimary = Color(0xFF0000FF),
        )
        assertEquals(Color.Black, colors.contentColor)
        assertEquals(Color(0xFF0000FF), colors.activeTrackColor)
        assertEquals(Color(0xFF0000FF), colors.thumbColor)
    }

    @Test
    fun darkPageKeepsWhiteContent() {
        val colors = sharedReaderSliderChromeColors(
            pageBackground = Color.Black,
            pageText = Color.White,
            themePrimary = Color.White,
        )
        assertEquals(Color.White, colors.contentColor)
    }

    @Test
    fun lowContrastPageTextFallsBackToHighContrast() {
        val colors = sharedReaderSliderChromeColors(
            pageBackground = Color.White,
            pageText = Color(0xFFDDDDDD),
            themePrimary = Color(0xFF0000FF),
        )
        assertEquals(Color.Black, colors.contentColor)
    }

    @Test
    fun lowContrastPrimaryFallsBackToContent() {
        val colors = sharedReaderSliderChromeColors(
            pageBackground = Color.White,
            pageText = Color.Black,
            themePrimary = Color.Yellow,
        )
        assertEquals(Color.Black, colors.contentColor)
        assertEquals(Color.Black, colors.activeTrackColor)
        assertEquals(Color.Black, colors.thumbColor)
    }

    @Test
    fun pdfLightAssumptionYieldsBlackArrows() {
        val colors = sharedReaderSliderChromeColors(
            pageBackground = Color.White,
            pageText = Color.Black,
            themePrimary = Color.Unspecified,
        )
        assertEquals(Color.Black, colors.contentColor)
    }
}
