package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.CloudFolderDeviceBinding
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.cloudFolderRootId
import com.aryan.reader.shared.CloudFolderRootStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFolderSyncAndroidModelsTest {

    @Test
    fun `folder options use complete indexed inventory and keep roots independent`() {
        val firstUri = "content://tree/books"
        val secondUri = "content://tree/notes"
        val options = cloudFolderSyncFolderOptions(
            folders = listOf(
                SyncedFolder(firstUri, "Books", lastScanTime = 10L),
                SyncedFolder(secondUri, "Notes", lastScanTime = 20L),
            ),
            indexedFiles = listOf(
                RecentFileItem("a", null, FileType.PDF, "a.pdf", 1L, sourceFolderUri = firstUri, fileSize = 100L),
                RecentFileItem("b", null, FileType.EPUB, "b.epub", 2L, sourceFolderUri = firstUri, fileSize = 300L),
                RecentFileItem("c", null, FileType.TXT, "c.txt", 3L, sourceFolderUri = secondUri, fileSize = 50L),
                RecentFileItem("managed", null, FileType.PDF, "managed.pdf", 4L, fileSize = 999L),
            ),
        )

        assertEquals(2, options[0].fileCount)
        assertEquals(400L, options[0].totalBytes)
        assertEquals(1, options[1].fileCount)
        assertEquals(50L, options[1].totalBytes)
        assertEquals(
            cloudFolderRootId("legacy-local-binding:$firstUri"),
            options[0].rootId,
        )
    }

    @Test
    fun `repository statistics win when local indexing has no rows`() {
        val uri = "content://tree/books"
        val rootId = "folder_root_books"
        val options = cloudFolderSyncFolderOptions(
            folders = listOf(
                SyncedFolder(uri, "Books", lastScanTime = 0L, localSyncEnabled = false, cloudRootId = rootId),
            ),
            indexedFiles = emptyList(),
            repositoryStats = mapOf(
                rootId to CloudFolderRootStats(fileCount = 12, directoryCount = 3, totalBytes = 4_096L),
            ),
        )

        assertEquals(12, options.single().fileCount)
        assertEquals(4_096L, options.single().totalBytes)
    }

    @Test
    fun `remote roots remain visible with truthful materialization status`() {
        val root = CloudFolderRoot(
            rootId = "remote-books",
            name = "Shared books",
            stats = CloudFolderRootStats(fileCount = 7, totalBytes = 512L),
        )
        val options = cloudFolderSyncFolderOptions(
            folders = emptyList(),
            indexedFiles = emptyList(),
            repositoryRoots = listOf(root),
        )

        assertEquals(1, options.size)
        assertEquals("remote-books", options.single().rootId)
        assertTrue(options.single().isRemote)
        assertFalse(options.single().isBoundLocally)
        assertFalse(options.single().isSelectable)
        assertEquals(CloudFolderMaterializationMode.CLOUD_ONLY, options.single().materializationMode)
    }

    @Test
    fun `download-all remote roots are selectable and marked offline`() {
        val root = CloudFolderRoot(
            rootId = "remote-books",
            name = "Shared books",
            stats = CloudFolderRootStats(fileCount = 7, totalBytes = 512L),
        )
        val options = cloudFolderSyncFolderOptions(
            folders = emptyList(),
            indexedFiles = emptyList(),
            repositoryRoots = listOf(root),
            deviceBindings = mapOf(
                root.rootId to CloudFolderDeviceBinding(
                    rootId = root.rootId,
                    deviceId = "device-2",
                    materializationMode = CloudFolderMaterializationMode.KEEP_OFFLINE,
                ),
            ),
        )

        assertTrue(options.single().isRemote)
        assertTrue(options.single().isBoundLocally)
        assertTrue(options.single().isSelectable)
        assertEquals(CloudFolderMaterializationMode.KEEP_OFFLINE, options.single().materializationMode)
    }

    @Test
    fun `cloud-only choice remains visible as a configured remote root`() {
        val root = CloudFolderRoot(rootId = "remote-books", name = "Shared books")
        val options = cloudFolderSyncFolderOptions(
            folders = emptyList(),
            indexedFiles = emptyList(),
            repositoryRoots = listOf(root),
            deviceBindings = mapOf(
                root.rootId to CloudFolderDeviceBinding(
                    rootId = root.rootId,
                    deviceId = "device-2",
                    materializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
                ),
            ),
        )

        assertTrue(options.single().isRemote)
        assertTrue(options.single().isBoundLocally)
        assertFalse(options.single().isSelectable)
        assertEquals(CloudFolderMaterializationMode.CLOUD_ONLY, options.single().materializationMode)
    }
}
