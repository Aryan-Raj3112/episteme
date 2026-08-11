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
    fun nonCloudNonPaidSettingsGapsRemainExplicit() {
        val gaps = SharedSettingsAction.entries
            .filter { it.iosDisposition() == IosSettingsActionDisposition.PARITY_GAP }
            .toSet()

        assertEquals(
            setOf(
                SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                SharedSettingsAction.HIDE_READER_AI,
                SharedSettingsAction.CLEAR_BOOK_CACHE,
            ),
            gaps,
        )
    }
}
