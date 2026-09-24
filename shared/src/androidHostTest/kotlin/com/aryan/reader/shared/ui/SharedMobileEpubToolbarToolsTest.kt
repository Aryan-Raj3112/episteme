package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobileEpubToolbarToolsTest {

    @Test
    fun dictionaryIsPlaceableInMobileEpubToolbar() {
        assertContains(SharedMobileEpubToolbarTools, ReaderTool.DICTIONARY)
        assertContains(SharedMobileEpubCustomizableTools, ReaderTool.DICTIONARY)
    }

    @Test
    fun dictionaryIsVisibleAndTopBarByDefault() {
        val defaults = ReaderToolbarPreferences()
        assertTrue(defaults.isVisible(ReaderTool.DICTIONARY))
        assertFalse(defaults.isBottom(ReaderTool.DICTIONARY))
    }

    @Test
    fun brightnessAndOrientationArePlaceableInTopAndBottomBars() {
        // Regression: both tools were placeable in the bottom set via the
        // customize sheet but SharedMobileEpubBottomBar had no branches for
        // them, so they vanished everywhere when moved to the bottom bar.
        assertContains(SharedMobileEpubToolbarTools, ReaderTool.BRIGHTNESS)
        assertContains(SharedMobileEpubToolbarTools, ReaderTool.SCREEN_ORIENTATION)
        assertContains(SharedMobileEpubCustomizableTools, ReaderTool.BRIGHTNESS)
        assertContains(SharedMobileEpubCustomizableTools, ReaderTool.SCREEN_ORIENTATION)
    }
}