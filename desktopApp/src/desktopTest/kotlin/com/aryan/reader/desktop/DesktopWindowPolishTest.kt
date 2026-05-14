package com.aryan.reader.desktop

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowPolishTest {
    @Test
    fun `desktop window defaults use app branding and a useful first launch size`() {
        val defaults = epistemeDesktopWindowDefaults(desktopBuildProfileForFlavor("standard"))

        assertEquals(EpistemeDesktopWindowTitle, defaults.title)
        assertEquals(EpistemeDesktopWindowIconResource, defaults.iconResourcePath)
        assertTrue(defaults.defaultSize.width.value > defaults.minimumSize.width.toFloat())
        assertTrue(defaults.defaultSize.height.value > defaults.minimumSize.height.toFloat())
        assertEquals(EpistemeDesktopWindowMinimumWidthPx, defaults.minimumSize.width)
        assertEquals(EpistemeDesktopWindowMinimumHeightPx, defaults.minimumSize.height)
    }

    @Test
    fun `oss desktop window defaults use oss branding`() {
        val defaults = epistemeDesktopWindowDefaults(desktopBuildProfileForFlavor("oss-offline"))

        assertEquals(EpistemeDesktopOssAppName, defaults.title)
    }

    @Test
    fun `desktop chrome colors choose dark mode from dark theme surfaces`() {
        val darkChrome = desktopWindowChromeColors(
            captionColor = Color(0xFF12140E),
            textColor = Color(0xFFE2E3D8),
            borderColor = Color(0xFF0C0F09)
        )

        val lightChrome = desktopWindowChromeColors(
            captionColor = Color(0xFFF9FAEF),
            textColor = Color(0xFF1A1C16),
            borderColor = Color(0xFFFFFFFF)
        )

        assertTrue(darkChrome.useDarkMode)
        assertFalse(lightChrome.useDarkMode)
    }
}
