package com.aryan.reader

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSliderChromeStateTest {

    @Test
    fun `toggle opens slider anchored to current page`() {
        val state = readerSliderToggleState(
            isCurrentlyToggledOn = false,
            currentPage = 12
        )

        assertTrue(state.isToggledOn)
        assertEquals(12, state.bookmarkPosition.startPage)
        assertEquals(12f, state.bookmarkPosition.currentPage)
    }

    @Test
    fun `toggle closes slider and resets bookmark anchor`() {
        val state = readerSliderToggleState(
            isCurrentlyToggledOn = true,
            currentPage = 4
        )

        assertFalse(state.isToggledOn)
        assertEquals(4, state.bookmarkPosition.startPage)
        assertEquals(4f, state.bookmarkPosition.currentPage)
    }

    @Test
    fun `slider only renders while toggled on and chrome is visible`() {
        assertTrue(
            shouldRenderReaderSlider(
                isToggledOn = true,
                isBottomChromeVisible = true,
                isSearchActive = false
            )
        )
        assertFalse(
            shouldRenderReaderSlider(
                isToggledOn = true,
                isBottomChromeVisible = false,
                isSearchActive = false
            )
        )
        assertFalse(
            shouldRenderReaderSlider(
                isToggledOn = true,
                isBottomChromeVisible = true,
                isSearchActive = true
            )
        )
        assertFalse(
            shouldRenderReaderSlider(
                isToggledOn = false,
                isBottomChromeVisible = true,
                isSearchActive = false
            )
        )
    }

    @Test
    fun `bookmark position clamps invalid page to start`() {
        val position = readerSliderBookmarkPosition(currentPage = -3)

        assertEquals(0, position.startPage)
        assertEquals(0f, position.currentPage)
    }

    @Test
    fun `toggle preference key is scoped to book id`() {
        assertEquals(
            "reader_slider_toggle_book-123",
            readerSliderTogglePreferenceKey("book-123")
        )
    }

    @Test
    fun `slider content color falls back on light page when theme text is low contrast`() {
        val colors = readerSliderChromeColors(
            pageBackground = Color.White,
            pageText = Color.White,
            themePrimary = Color(0xFF6750A4)
        )

        assertEquals(Color.Black, colors.contentColor)
    }

    @Test
    fun `slider accent falls back when primary is low contrast against page`() {
        val colors = readerSliderChromeColors(
            pageBackground = Color.Black,
            pageText = Color.White,
            themePrimary = Color(0xFF050505)
        )

        assertEquals(Color.White, colors.activeTrackColor)
        assertEquals(Color.White, colors.thumbColor)
        assertEquals(Color.White, colors.bookmarkColor)
    }
}
