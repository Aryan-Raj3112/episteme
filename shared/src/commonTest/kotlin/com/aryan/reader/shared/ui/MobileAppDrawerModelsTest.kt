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
}
