package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MobileUnifiedLibraryDrawerModelsTest {
    @Test
    fun `drawer order matches Android benchmark and labels stay semantic`() {
        val model = mobileUnifiedLibraryDrawerModel(
            MobileUnifiedLibraryDrawerCapabilities(
                catalogsAvailable = true,
                aiSettingsAvailable = true,
            ),
        )

        assertEquals(
            listOf(
                MobileUnifiedLibraryDrawerDestination.HOME,
                MobileUnifiedLibraryDrawerDestination.AUDIOBOOKS,
                MobileUnifiedLibraryDrawerDestination.SHELVES,
                MobileUnifiedLibraryDrawerDestination.FOLDERS,
                MobileUnifiedLibraryDrawerDestination.CATALOGS,
            ),
            model.destinations,
        )
        assertEquals(
            listOf(
                MobileUnifiedLibraryDrawerLabel.HOME,
                MobileUnifiedLibraryDrawerLabel.AUDIOBOOKS,
                MobileUnifiedLibraryDrawerLabel.SHELVES,
                MobileUnifiedLibraryDrawerLabel.FOLDERS,
                MobileUnifiedLibraryDrawerLabel.CATALOGS,
            ),
            model.destinations.map { it.label },
        )
        assertEquals(
            listOf(
                MobileUnifiedLibraryDrawerAppearance.THEME,
                MobileUnifiedLibraryDrawerAppearance.SETTINGS,
                MobileUnifiedLibraryDrawerAppearance.FONTS,
                MobileUnifiedLibraryDrawerAppearance.AI,
            ),
            model.appearance,
        )
    }

    @Test
    fun `catalog and AI entries are independently capability gated`() {
        val noOptionalFeatures = mobileUnifiedLibraryDrawerModel(
            MobileUnifiedLibraryDrawerCapabilities(
                catalogsAvailable = false,
                aiSettingsAvailable = false,
            ),
        )
        assertEquals(
            listOf(
                MobileUnifiedLibraryDrawerDestination.HOME,
                MobileUnifiedLibraryDrawerDestination.AUDIOBOOKS,
                MobileUnifiedLibraryDrawerDestination.SHELVES,
                MobileUnifiedLibraryDrawerDestination.FOLDERS,
            ),
            noOptionalFeatures.destinations,
        )
        assertEquals(
            listOf(
                MobileUnifiedLibraryDrawerAppearance.THEME,
                MobileUnifiedLibraryDrawerAppearance.SETTINGS,
                MobileUnifiedLibraryDrawerAppearance.FONTS,
            ),
            noOptionalFeatures.appearance,
        )

        val catalogsOnly = mobileUnifiedLibraryDrawerModel(
            MobileUnifiedLibraryDrawerCapabilities(
                catalogsAvailable = true,
                aiSettingsAvailable = false,
            ),
        )
        assertEquals(5, catalogsOnly.destinations.size)
        assertEquals(3, catalogsOnly.appearance.size)
    }
}
