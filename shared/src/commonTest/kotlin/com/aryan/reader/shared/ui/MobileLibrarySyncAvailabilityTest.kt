package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileLibrarySyncAvailabilityTest {
    @Test
    fun cloudSyncAloneOffersPullToSync() {
        assertTrue(canPullToSyncLibrary(cloudSyncEnabled = true, foldersWithLocalSyncEnabled = emptyList()))
    }

    @Test
    fun anyEnabledFolderOffersPullToSync() {
        assertTrue(
            canPullToSyncLibrary(
                cloudSyncEnabled = false,
                foldersWithLocalSyncEnabled = listOf(false, true, false),
            )
        )
    }

    @Test
    fun noSyncMechanismHidesPullToSync() {
        // Disabled cloud sync with every folder's local sync off means refresh would be a
        // no-op, so the gesture must not be offered.
        assertFalse(canPullToSyncLibrary(cloudSyncEnabled = false, foldersWithLocalSyncEnabled = emptyList()))
        assertFalse(
            canPullToSyncLibrary(
                cloudSyncEnabled = false,
                foldersWithLocalSyncEnabled = listOf(false, false),
            )
        )
    }
}