package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedSettingsAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosSettingsParityTest {
    @Test
    fun everySharedSettingsActionHasAnIosDisposition() {
        val dispositions = SharedSettingsAction.entries.associateWith { it.iosDisposition() }

        assertEquals(SharedSettingsAction.entries.size, dispositions.size)
        assertTrue(dispositions.values.none { it.name.isBlank() })
    }

    @Test
    fun noNonCloudNonPaidSettingsGapsRemain() {
        val gaps = SharedSettingsAction.entries
            .filter { it.iosDisposition() == IosSettingsActionDisposition.PARITY_GAP }
            .toSet()

        assertTrue(gaps.isEmpty())
        assertEquals(
            setOf(SharedSettingsAction.SCREEN_CAPTURE_PROTECTION, SharedSettingsAction.CLEAR_BOOK_CACHE),
            SharedSettingsAction.entries
                .filter { it.iosDisposition() == IosSettingsActionDisposition.INTENTIONAL_PLATFORM_DIFFERENCE }
                .toSet(),
        )
    }
}
