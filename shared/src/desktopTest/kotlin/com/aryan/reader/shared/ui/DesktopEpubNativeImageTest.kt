package com.aryan.reader.shared.ui

import androidx.compose.ui.Alignment
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopEpubNativeImageTest {
    @Test
    fun `desktop native epub image maps css object position to compose alignment`() {
        assertEquals(Alignment.TopStart, desktopEpubImageContentAlignment("left top"))
        assertEquals(Alignment.TopStart, desktopEpubImageContentAlignment("top left"))
        assertEquals(Alignment.BottomStart, desktopEpubImageContentAlignment("0% 100%"))
        assertEquals(Alignment.CenterEnd, desktopEpubImageContentAlignment("right center"))
        assertEquals(Alignment.Center, desktopEpubImageContentAlignment(null))
    }
}
