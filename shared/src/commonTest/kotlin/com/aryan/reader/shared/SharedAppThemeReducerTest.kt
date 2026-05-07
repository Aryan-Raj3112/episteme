package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedAppThemeReducerTest {

    @Test
    fun `app appearance actions update shared settings`() {
        val seedColor = Color(0xFF006C4C)
        val state = SharedReaderScreenState()
            .reduce(AppAction.AppThemeChanged(AppThemeMode.DARK))
            .reduce(AppAction.AppContrastChanged(AppContrastOption.HIGH))
            .reduce(AppAction.AppTextDimFactorLightChanged(0.75f))
            .reduce(AppAction.AppTextDimFactorDarkChanged(0.65f))
            .reduce(AppAction.AppSeedColorChanged(seedColor))

        assertEquals(AppThemeMode.DARK, state.appThemeMode)
        assertEquals(AppContrastOption.HIGH, state.appContrastOption)
        assertEquals(0.75f, state.appTextDimFactorLight)
        assertEquals(0.65f, state.appTextDimFactorDark)
        assertEquals(seedColor, state.appSeedColor)
    }

    @Test
    fun `custom app theme add replaces matching id and selects seed color`() {
        val first = CustomAppTheme(id = "theme", name = "First", seedColor = Color(0xFF123456))
        val second = CustomAppTheme(id = "theme", name = "Second", seedColor = Color(0xFF654321))

        val state = SharedReaderScreenState()
            .reduce(AppAction.CustomAppThemeAdded(first))
            .reduce(AppAction.CustomAppThemeAdded(second))

        assertEquals(listOf(second), state.customAppThemes)
        assertEquals(second.seedColor, state.appSeedColor)
    }

    @Test
    fun `deleting selected custom app theme clears orphaned seed color`() {
        val theme = CustomAppTheme(id = "forest", name = "Forest", seedColor = Color(0xFF006C4C))
        val state = SharedReaderScreenState()
            .reduce(AppAction.CustomAppThemeAdded(theme))
            .reduce(AppAction.CustomAppThemeDeleted(theme.id))

        assertTrue(state.customAppThemes.isEmpty())
        assertNull(state.appSeedColor)
    }

    @Test
    fun `text dim factors stay inside supported slider range`() {
        val state = SharedReaderScreenState()
            .reduce(AppAction.AppTextDimFactorLightChanged(0.1f))
            .reduce(AppAction.AppTextDimFactorDarkChanged(1.2f))

        assertEquals(0.3f, state.appTextDimFactorLight)
        assertEquals(1.0f, state.appTextDimFactorDark)
    }
}
