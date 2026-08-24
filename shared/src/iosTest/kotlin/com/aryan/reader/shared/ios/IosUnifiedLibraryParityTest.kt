package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerAppearance
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerCapabilities
import com.aryan.reader.shared.ui.MobileUnifiedLibraryDrawerDestination
import com.aryan.reader.shared.ui.mobileUnifiedLibraryDrawerModel
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
