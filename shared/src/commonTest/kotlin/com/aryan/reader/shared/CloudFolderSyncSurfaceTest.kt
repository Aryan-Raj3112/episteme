package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudFolderSyncSurfaceTest {

    private fun root(
        id: String,
        name: String,
        revision: Long = 1L,
        deleted: Boolean = false,
    ) = CloudFolderRoot(
        rootId = id,
        name = name,
        manifestRevision = revision,
        isDeleted = deleted,
    )

    @Test
    fun `local options hide placeholders and require cloud mapping`() {
        val options = projectCloudFolderSyncOptions(
            localFolders = listOf(
                CloudFolderLocalBindingView("uri-a", "Books", lastScanTime = 10L, cloudRootId = "root-a"),
                CloudFolderLocalBindingView("uri-b", "Pdfs", lastScanTime = 0L, cloudRootId = null),
                CloudFolderLocalBindingView(
                    "cloud-folder-placeholder:root-c",
                    "Shared",
                    cloudRootId = "root-c",
                    isCloudPlaceholder = true,
                ),
            ),
        )
        // Ghost placeholder never appears as a local option.
        assertEquals(2, options.size)
        val mapped = options.first { it.normalizedDisplayName == "Books" }
        assertTrue(mapped.isAvailable)
        assertTrue(mapped.isSelectable)
        assertFalse(mapped.isRemote)
        val legacy = options.first { it.normalizedDisplayName == "Pdfs" }
        assertFalse(legacy.isAvailable)
        assertFalse(legacy.isSelectable)
    }

    @Test
    fun `remote roots are selectable only after explicit binding`() {
        val roots = listOf(root("root-a", "Books"), root("root-b", "Comics"))
        val unbound = projectCloudFolderSyncOptions(
            localFolders = emptyList(),
            repositoryRoots = roots,
        )
        assertEquals(2, unbound.size)
        assertTrue(unbound.all { it.isRemote })
        assertTrue(unbound.all { !it.isSelectable })
        assertTrue(unbound.all { !it.isBoundLocally })

        val bound = projectCloudFolderSyncOptions(
            localFolders = emptyList(),
            repositoryRoots = roots,
            deviceBindings = mapOf(
                "root-a" to CloudFolderDeviceBinding(
                    rootId = "root-a",
                    deviceId = "d1",
                    materializationMode = CloudFolderMaterializationMode.KEEP_OFFLINE,
                ),
                "root-b" to CloudFolderDeviceBinding(
                    rootId = "root-b",
                    deviceId = "d1",
                    materializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
                ),
            ),
        )
        val offline = bound.first { it.normalizedRootId == "root-a" }
        assertTrue(offline.isSelectable)
        assertTrue(offline.isBoundLocally)
        val cloudOnly = bound.first { it.normalizedRootId == "root-b" }
        assertFalse(cloudOnly.isSelectable)
        // A cloud-only choice is still a durable binding decision.
        assertTrue(cloudOnly.isBoundLocally)
    }

    @Test
    fun `deleted roots never surface and local wins on id collision`() {
        val options = projectCloudFolderSyncOptions(
            localFolders = listOf(
                CloudFolderLocalBindingView("uri-a", "Books", lastScanTime = 5L, cloudRootId = "root-a"),
            ),
            repositoryRoots = listOf(
                root("root-a", "Books Remote"),
                root("root-gone", "Gone", deleted = true),
            ),
        )
        assertEquals(1, options.size)
        assertEquals("root-a", options.single().normalizedRootId)
        // Local binding wins the dedupe; remote inventory does not duplicate it.
        assertFalse(options.single().isRemote)
    }

    @Test
    fun `incoming prompt picks lowest name then id from pending set`() {
        val roots = listOf(
            root("root-b", "Zeta", revision = 3L),
            root("root-a", "alpha", revision = 2L),
            root("root-c", "Alpha", revision = 5L),
        )
        val prompt = selectCloudFolderIncomingPrompt(roots, setOf("root-b", "root-a", "root-c"))
        assertNotNull(prompt)
        // Case-insensitive name first ("Alpha" < "alpha" < "Zeta" would tie on
        // lowercased name; root-a vs root-c tie-breaks on rootId).
        assertEquals("root-a", prompt.rootId)

        assertNull(selectCloudFolderIncomingPrompt(roots, emptySet()))
        assertNull(
            selectCloudFolderIncomingPrompt(
                listOf(root("root-x", "Gone", deleted = true)),
                setOf("root-x"),
            ),
        )
    }

    @Test
    fun `incoming choices update selection exactly like Android`() {
        val base = CloudFolderSyncSelection.Default
        val download = nextSelectionAfterIncomingChoice(base, "root-a", CloudFolderIncomingChoice.DOWNLOAD_ALL)
        assertTrue(download.includes("root-a"))
        val bind = nextSelectionAfterIncomingChoice(base, "root-a", CloudFolderIncomingChoice.BIND_LOCAL_FOLDER)
        assertTrue(bind.includes("root-a"))
        val cloudOnly = nextSelectionAfterIncomingChoice(base, "root-a", CloudFolderIncomingChoice.CLOUD_ONLY)
        assertFalse(cloudOnly.includes("root-a"))

        assertTrue(shouldPullAfterIncomingChoice(CloudFolderIncomingChoice.DOWNLOAD_ALL, true))
        assertTrue(shouldPullAfterIncomingChoice(CloudFolderIncomingChoice.BIND_LOCAL_FOLDER, true))
        assertFalse(shouldPullAfterIncomingChoice(CloudFolderIncomingChoice.CLOUD_ONLY, true))
        assertFalse(shouldPullAfterIncomingChoice(CloudFolderIncomingChoice.DOWNLOAD_ALL, false))

        // CLOUD_ONLY acknowledges immediately; materializing choices start at
        // 0 so the next pull performs the initial download first.
        assertEquals(
            7L,
            initialAcknowledgedRevisionAfterIncomingChoice(7L, CloudFolderIncomingChoice.CLOUD_ONLY),
        )
        assertEquals(
            0L,
            initialAcknowledgedRevisionAfterIncomingChoice(7L, CloudFolderIncomingChoice.DOWNLOAD_ALL),
        )
        assertEquals(
            0L,
            initialAcknowledgedRevisionAfterIncomingChoice(7L, CloudFolderIncomingChoice.BIND_LOCAL_FOLDER),
        )
    }
}
