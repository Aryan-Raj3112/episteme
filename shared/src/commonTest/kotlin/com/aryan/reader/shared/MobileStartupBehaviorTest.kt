package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileStartupBehaviorTest {
    @Test
    fun missingOrUnknownPersistedValuesKeepTheHistoricalBehaviour() {
        assertEquals(MobileStartupBehavior.OPEN_LAST_READING, MobileStartupBehavior.fromPersisted(null))
        assertEquals(MobileStartupBehavior.OPEN_LAST_READING, MobileStartupBehavior.fromPersisted(""))
        assertEquals(MobileStartupBehavior.OPEN_LAST_READING, MobileStartupBehavior.fromPersisted("last book"))
        assertEquals(MobileStartupBehavior.OPEN_LAST_READING, MobileStartupBehavior.DEFAULT)
    }

    @Test
    fun everyChoiceSurvivesAPersistRoundTrip() {
        MobileStartupBehavior.entries.forEach { behavior ->
            assertEquals(behavior, MobileStartupBehavior.fromPersisted(behavior.persistedValue))
        }
    }

    @Test
    fun onlyTheDefaultReopensTheReaderSession() {
        assertTrue(mobileStartupBehaviorAllowsReaderRestore(MobileStartupBehavior.OPEN_LAST_READING))
        assertFalse(mobileStartupBehaviorAllowsReaderRestore(MobileStartupBehavior.HOME))
    }

    @Test
    fun theRowPreviewNamesTheChosenBehaviour() {
        assertEquals(
            "Reopen the last book",
            sharedStartupBehaviorValue(
                MobileStartupBehavior.OPEN_LAST_READING,
                openLastBookLabel = "Reopen the last book",
                homeLabel = "Start at your library",
            ),
        )
        assertEquals(
            "Start at your library",
            sharedStartupBehaviorValue(
                MobileStartupBehavior.HOME,
                openLastBookLabel = "Reopen the last book",
                homeLabel = "Start at your library",
            ),
        )
    }
}
