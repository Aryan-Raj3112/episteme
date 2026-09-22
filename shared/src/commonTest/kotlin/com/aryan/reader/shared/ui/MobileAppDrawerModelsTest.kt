package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MobileAppDrawerModelsTest {
    @Test
    fun `global drawer keeps appearance and help actions`() {
        assertEquals(
            listOf(
                MobileAppDrawerItem.SETTINGS,
                MobileAppDrawerItem.APP_THEME,
                MobileAppDrawerItem.FONTS,
                MobileAppDrawerItem.AI_SETTINGS,
                MobileAppDrawerItem.HELP_FEEDBACK,
            ),
            mobileAppDrawerModel(MobileAppDrawerCapabilities.GLOBAL).items,
        )
    }

    @Test
    fun `unified library account drawer removes duplicated local actions`() {
        assertEquals(
            listOf(
                MobileAppDrawerItem.SETTINGS,
                MobileAppDrawerItem.ABOUT,
                MobileAppDrawerItem.SUPPORT_PROJECT,
                MobileAppDrawerItem.HELP_FEEDBACK,
            ),
            mobileAppDrawerModel(MobileAppDrawerCapabilities.UNIFIED_LIBRARY_ACCOUNT).items,
        )
    }

    @Test
    fun `ios homescreen hides ai keys while feature stays hidden`() {
        // Intentional temporary iOS scope: homescreen uses
        // GLOBAL.copy(showAiSettings = false). The shared model must keep
        // filtering the row so the platform only maps semantics to UI.
        assertEquals(
            listOf(
                MobileAppDrawerItem.SETTINGS,
                MobileAppDrawerItem.APP_THEME,
                MobileAppDrawerItem.FONTS,
                MobileAppDrawerItem.HELP_FEEDBACK,
            ),
            mobileAppDrawerModel(
                MobileAppDrawerCapabilities.GLOBAL.copy(showAiSettings = false)
            ).items,
        )
    }
}
