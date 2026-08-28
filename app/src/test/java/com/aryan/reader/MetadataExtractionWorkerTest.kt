package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataExtractionWorkerTest {

    @Test
    fun `metadata extraction includes enabled app managed roots and excludes disabled folders`() {
        val enabledSafFolder = SyncedFolder(
            uriString = "content://provider/tree/books",
            name = "Books",
            lastScanTime = 0L,
            localSyncEnabled = true,
        )
        val enabledAppFolder = SyncedFolder(
            uriString = "file:///data/user/0/com.aryan.reader/files/cloud-folder-sync/root-1",
            name = "Synced books",
            lastScanTime = 0L,
            localSyncEnabled = true,
            cloudRootId = "root-1",
            isAppManaged = true,
        )
        val disabledFolder = enabledSafFolder.copy(
            uriString = "content://provider/tree/disabled",
            localSyncEnabled = false,
        )

        assertEquals(
            setOf(enabledSafFolder.uriString, enabledAppFolder.uriString),
            metadataExtractionEnabledFolderUris(
                listOf(enabledSafFolder, enabledAppFolder, disabledFolder)
            )
        )
    }

    @Test
    fun `metadata failures retry with a bounded attempt count`() {
        assertTrue(shouldRetryMetadataExtraction(failedCount = 1, runAttemptCount = 0))
        assertTrue(shouldRetryMetadataExtraction(failedCount = 2, runAttemptCount = 2))
        assertFalse(shouldRetryMetadataExtraction(failedCount = 1, runAttemptCount = 3))
        assertFalse(shouldRetryMetadataExtraction(failedCount = 0, runAttemptCount = 0))
    }
}
