package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerCapabilities
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerDestination
import com.aryan.reader.shared.ui.MobileAppDrawerCapabilities
import com.aryan.reader.shared.ui.MobileAppDrawerItem
import com.aryan.reader.shared.ui.mobileAppDrawerModel
import com.aryan.reader.shared.ui.mobileUnifiedLibraryDrawerModel
import com.aryan.reader.shared.ui.mobileUnifiedLibraryModel
import com.aryan.reader.shared.ui.MobileUnifiedLibraryFilter
import com.aryan.reader.shared.ui.MobileUnifiedLibraryViewState
import com.aryan.reader.shared.ui.SharedMobileUnifiedLibrarySelectionAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosUnifiedLibraryParityTest {
    @Test
    fun `ios unified drawer keeps Android section order without Android-only AI row`() {
        val model = mobileUnifiedLibraryDrawerModel(
            MobileUnifiedLibraryDrawerCapabilities(
                catalogsAvailable = true,
                aiSettingsAvailable = false,
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
                MobileUnifiedLibraryDrawerAppearance.THEME,
                MobileUnifiedLibraryDrawerAppearance.SETTINGS,
                MobileUnifiedLibraryDrawerAppearance.FONTS,
            ),
            model.appearance,
        )
    }

    @Test
    fun `ios unified account drawer omits actions owned by local drawer`() {
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
    fun iosUnifiedLibraryExposesTheFullContextualSelectionActionSet() {
        assertEquals(
            SharedMobileUnifiedLibrarySelectionAction.entries.toSet(),
            iosUnifiedLibrarySelectionCapabilities().enabledActions,
        )
    }

    @Test
    fun iosUnifiedLibraryUsesTheSharedHomeSearchAndContinueContract() {
        val model = mobileUnifiedLibraryModel(
            viewState = MobileUnifiedLibraryViewState(
                filter = MobileUnifiedLibraryFilter.ALL,
                query = "",
                searchActive = false,
            ),
            visibleBooks = listOf("book"),
            continueReading = "book",
        )
        assertTrue(model.showContinueReading)
        assertFalse(model.showSearchResults)

        val searchModel = mobileUnifiedLibraryModel(
            viewState = MobileUnifiedLibraryViewState(
                query = "reader",
                searchActive = true,
            ),
            visibleBooks = emptyList(),
            continueReading = "book",
        )
        assertFalse(searchModel.showContinueReading)
        assertTrue(searchModel.showSearchResults)
    }
}
