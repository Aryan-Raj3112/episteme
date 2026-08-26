package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudFolderSyncUiTest {

    @Test
    fun `settings state defaults to local only and no cloud roots`() {
        val state = CloudFolderSyncSettingsUiState(
            folders = listOf(
                CloudFolderSyncFolderOption("root-a", "Books", fileCount = 4, totalBytes = 100L),
            ),
        )

        assertFalse(state.localFolderIndexingEnabled)
        assertEquals(CloudFolderSyncSelectionMode.EXCLUDED, state.selection.mode)
        assertEquals(0, state.selectedFolderCount)
        assertEquals(0, state.selectedFileCount)
    }

    @Test
    fun `individual selection derives counts and size without mutating source options`() {
        val state = CloudFolderSyncSettingsUiState(
            folders = listOf(
                CloudFolderSyncFolderOption(" root-b ", "Zeta", fileCount = 2, totalBytes = 50L),
                CloudFolderSyncFolderOption("root-a", "Alpha", fileCount = 3, totalBytes = 70L),
            ),
        ).includeRoot("root-b")

        assertEquals(listOf("Alpha", "Zeta"), state.normalizedFolders.map { it.normalizedDisplayName })
        assertEquals(1, state.selectedFolderCount)
        assertEquals(2, state.selectedFileCount)
        assertEquals(50L, state.selectedTotalBytes)
        assertEquals(listOf("root-a", "root-b"), state.folders.map { it.rootId })
    }

    @Test
    fun `select all and clear all are explicit policy transitions`() {
        val state = CloudFolderSyncSettingsUiState(
            folders = listOf(
                CloudFolderSyncFolderOption("root-a", "A", fileCount = 1, totalBytes = 10L),
                CloudFolderSyncFolderOption("root-b", "B", fileCount = 2, totalBytes = 20L),
            ),
        )

        val all = state.selectAllRoots()
        assertEquals(CloudFolderSyncSelectionMode.ALL, all.selection.mode)
        assertEquals(2, all.selectedFolderCount)
        assertEquals(3, all.selectedFileCount)
        assertEquals(30L, all.selectedTotalBytes)

        val none = all.excludeAllRoots()
        assertEquals(CloudFolderSyncSelectionMode.EXCLUDED, none.selection.mode)
        assertEquals(0, none.selectedFolderCount)
    }

    @Test
    fun `excluding one root from all preserves every other known root`() {
        val state = CloudFolderSyncSettingsUiState(
            folders = listOf(
                CloudFolderSyncFolderOption("root-a", "A"),
                CloudFolderSyncFolderOption("root-b", "B"),
                CloudFolderSyncFolderOption("root-c", "C"),
            ),
        ).selectAllRoots().excludeRoot("root-b")

        assertEquals(CloudFolderSyncSelectionMode.SELECTED, state.selection.mode)
        assertTrue(state.selection.includes("root-a"))
        assertFalse(state.selection.includes("root-b"))
        assertTrue(state.selection.includes("root-c"))
    }

    @Test
    fun `incoming prompt exposes portable root statistics and choices`() {
        val prompt = CloudFolderIncomingFolderPrompt(
            root = CloudFolderRoot(
                rootId = "root-1",
                name = "Shared books",
                stats = CloudFolderRootStats(fileCount = 7, directoryCount = 2, totalBytes = 512L),
            ),
            sourceDeviceName = "Phone",
        )

        assertEquals("root-1", prompt.rootId)
        assertEquals("Shared books", prompt.displayName)
        assertEquals(7, prompt.fileCount)
        assertEquals(2, prompt.directoryCount)
        assertEquals(512L, prompt.totalBytes)
        assertEquals(
            setOf(
                CloudFolderIncomingChoice.CLOUD_ONLY,
                CloudFolderIncomingChoice.DOWNLOAD_ALL,
                CloudFolderIncomingChoice.BIND_LOCAL_FOLDER,
            ),
            CloudFolderIncomingChoice.entries.toSet(),
        )
    }
}
