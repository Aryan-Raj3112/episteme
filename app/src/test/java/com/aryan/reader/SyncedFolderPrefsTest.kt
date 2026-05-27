package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedFolderPrefsTest {
    @Test
    fun `missing local sync flag defaults enabled`() {
        val folders = SyncedFolderPrefs.decodeSyncedFolders(
            jsonString = """
                [
                  {
                    "uri": "content://folder",
                    "name": "Books",
                    "lastScanTime": 12,
                    "allowedFileTypes": ["PDF"]
                  }
                ]
            """.trimIndent(),
            legacyUri = null,
            syncableTypes = setOf(FileType.PDF, FileType.EPUB)
        )

        assertTrue(folders.single().localSyncEnabled)
        assertTrue(
            SyncedFolderPrefs.isLocalSyncEnabled(
                jsonString = SyncedFolderPrefs.encodeSyncedFolders(
                    folders,
                    syncableTypes = setOf(FileType.PDF, FileType.EPUB)
                ),
                legacyUri = null,
                folderUriString = "content://folder",
                syncableTypes = setOf(FileType.PDF, FileType.EPUB)
            )
        )
    }

    @Test
    fun `disabled local sync flag persists and is checked`() {
        val encoded = SyncedFolderPrefs.encodeSyncedFolders(
            listOf(
                SyncedFolder(
                    uriString = "content://folder",
                    name = "Books",
                    lastScanTime = 12L,
                    allowedFileTypes = setOf(FileType.PDF),
                    localSyncEnabled = false
                )
            ),
            syncableTypes = setOf(FileType.PDF, FileType.EPUB)
        )
        val decoded = SyncedFolderPrefs.decodeSyncedFolders(
            jsonString = encoded,
            legacyUri = null,
            syncableTypes = setOf(FileType.PDF, FileType.EPUB)
        )

        assertFalse(decoded.single().localSyncEnabled)
        assertFalse(
            SyncedFolderPrefs.isLocalSyncEnabled(
                jsonString = encoded,
                legacyUri = null,
                folderUriString = "content://folder",
                syncableTypes = setOf(FileType.PDF, FileType.EPUB)
            )
        )
    }
}
