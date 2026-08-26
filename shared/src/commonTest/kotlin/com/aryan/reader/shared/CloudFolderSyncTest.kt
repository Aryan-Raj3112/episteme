package com.aryan.reader.shared

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudFolderSyncTest {

    @Test
    fun `folder sync is excluded by default and supports individual or all roots`() {
        val default = defaultCloudFolderSyncSelection()
        assertEquals(CloudFolderSyncSelectionMode.EXCLUDED, default.mode)
        assertFalse(default.includes("root-a"))

        val selected = default.withRootIncluded(" root-a ")
        assertEquals(CloudFolderSyncSelectionMode.SELECTED, selected.mode)
        assertTrue(selected.includes("root-a"))
        assertFalse(selected.includes("root-b"))

        val all = selected.includeAllRoots()
        assertEquals(CloudFolderSyncSelectionMode.ALL, all.mode)
        assertTrue(all.includes("root-a"))
        assertTrue(all.includes("root-b"))
        assertEquals(CloudFolderSyncSelectionMode.SELECTED, all.withoutRoot("root-a").mode)
        assertTrue(all.withoutRoot("root-a").selectedRootIds.isEmpty())
        val allExceptA = all.withoutRoot("root-a", listOf("root-a", "root-b", "root-c"))
        assertEquals(setOf("root-b", "root-c"), allExceptA.selectedRootIds)
        assertTrue(all.withRootIncluded("root-c").includes("root-b"))
    }

    @Test
    fun `selection normalization drops stale ids and ignores ids in excluded mode`() {
        val selection = CloudFolderSyncSelection(
            mode = CloudFolderSyncSelectionMode.SELECTED,
            selectedRootIds = setOf("root-a", "root-b", " "),
        ).normalized(listOf("root-a", "root-c"))
        assertEquals(setOf("root-a"), selection.selectedRootIds)

        val excluded = CloudFolderSyncSelection(
            mode = CloudFolderSyncSelectionMode.EXCLUDED,
            selectedRootIds = setOf("root-a"),
        ).normalized(listOf("root-a"))
        assertTrue(excluded.selectedRootIds.isEmpty())
        assertFalse(excluded.includes("root-a"))
    }

    @Test
    fun `portable binding omits local provider uri`() {
        val binding = CloudFolderDeviceBinding(
            rootId = "root-a",
            deviceId = "pixel-9",
            localUri = "content://com.android.externalstorage.documents/tree/primary%3ABooks",
            permissionState = CloudFolderPermissionState.GRANTED,
            materializationMode = CloudFolderMaterializationMode.LOCAL_MIRROR,
            lastAcknowledgedRevision = 8L,
        )
        val portable = binding.toPortable()
        assertEquals("root-a", portable.rootId)
        assertEquals("pixel-9", portable.deviceId)
        assertEquals(CloudFolderMaterializationMode.LOCAL_MIRROR, portable.materializationMode)
        assertEquals(8L, portable.lastAcknowledgedRevision)
        assertFalse(portable.toString().contains("content://"))
    }

    @Test
    fun `relative paths are safe and canonical`() {
        assertEquals("Series/Book.epub", normalizeCloudFolderRelativePath("Series\\Book.epub"))
        assertEquals("Book.epub", normalizeCloudFolderRelativePath("Book.epub"))
        assertEquals(null, normalizeCloudFolderRelativePath("/Book.epub"))
        assertEquals(null, normalizeCloudFolderRelativePath("../Book.epub"))
        assertEquals(null, normalizeCloudFolderRelativePath("Series/./Book.epub"))
        assertEquals(null, normalizeCloudFolderRelativePath("Series//Book.epub"))
        assertEquals(cloudFolderPathKey("A/Book.epub"), cloudFolderPathKey("a\\book.epub"))
    }

    @Test
    fun `hashes are canonicalized without trusting timestamps`() {
        val raw = "A".repeat(64)
        assertEquals("a".repeat(64), canonicalCloudFolderContentHash(raw))
        assertEquals("sha256:${"a".repeat(64)}", canonicalCloudFolderContentHash(" SHA256:${raw} "))
        assertTrue(isCloudFolderSha256(raw))
        assertTrue(isCloudFolderSha256("sha256:$raw"))
        assertFalse(isCloudFolderSha256("not-a-hash"))
    }

    @Test
    fun `manifest statistics and validation expose hierarchy facts`() {
        val root = root()
        val manifest = CloudFolderManifest(
            root = root,
            nodes = listOf(
                directory("Series"),
                file("Series/One.pdf", hash = "1".repeat(64), size = 10L),
                file("Series/Two.pdf", hash = "2".repeat(64), size = 20L),
            ),
        )
        assertEquals(
            CloudFolderRootStats(
                fileCount = 2,
                directoryCount = 1,
                totalBytes = 30L,
                scannedAt = 0L,
                scanComplete = true,
            ),
            manifest.statistics(),
        )
        assertTrue(manifest.validationIssues().isEmpty())
    }

    @Test
    fun `manifest validation catches unsafe duplicates and cross-root records`() {
        val manifest = CloudFolderManifest(
            root = root(),
            nodes = listOf(
                file("Book.pdf", id = "same"),
                file("book.pdf", id = "other"),
                file("Other.pdf", id = "same", rootId = "another-root", size = -1L),
            ),
            tombstones = listOf(
                CloudFolderTombstone(
                    nodeId = "same",
                    rootId = "root-a",
                    relativePath = "Book.pdf",
                    kind = CloudFolderNodeKind.FILE,
                ),
            ),
        )
        val issues = manifest.validationIssues().map { it.type }.toSet()
        assertTrue(CloudFolderManifestIssueType.DUPLICATE_NODE_ID in issues)
        assertTrue(CloudFolderManifestIssueType.DUPLICATE_PATH in issues)
        assertTrue(CloudFolderManifestIssueType.NODE_ROOT_MISMATCH in issues)
        assertTrue(CloudFolderManifestIssueType.NEGATIVE_SIZE in issues)
        assertTrue(CloudFolderManifestIssueType.NODE_TOMBSTONE_COLLISION in issues)
    }

    @Test
    fun `manifest round trip preserves device neutral hierarchy`() {
        val manifest = CloudFolderManifest(
            root = root(name = "Books"),
            revision = 4L,
            baseRevision = 3L,
            generatedAt = 100L,
            generatedByDeviceId = "pixel-9",
            nodes = listOf(directory("Series"), file("Series/Book.epub", hash = "a".repeat(64), size = 42L)),
            tombstones = listOf(
                CloudFolderTombstone(
                    nodeId = "deleted",
                    rootId = "root-a",
                    relativePath = "Old.pdf",
                    kind = CloudFolderNodeKind.FILE,
                    deletedRevision = 4L,
                ),
            ),
        )
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<CloudFolderManifest>(json.encodeToString(manifest))
        assertEquals(manifest, decoded)
    }

    @Test
    fun `local new file emits upload without importing file bytes`() {
        val base = manifest()
        val local = base.copy(
            revision = 2L,
            nodes = listOf(file("Books/Book.pdf", hash = "a".repeat(64), size = 12L, revision = 2L)),
        )
        val plan = planCloudFolderSync(base, local, base.copy(revision = 1L), nowMillis = 20L, deviceId = "pixel")
        val operation = plan.operations.single()
        assertEquals(CloudFolderSyncOperationKind.UPLOAD_FILE, operation.kind)
        assertEquals(CloudFolderSyncDirection.LOCAL_TO_CLOUD, operation.direction)
        assertEquals("Books/Book.pdf", operation.relativePath)
        assertEquals("a".repeat(64), operation.contentHash)
        assertTrue(plan.canCommit)
        assertEquals("Books/Book.pdf", plan.mergedManifest.nodes.single().relativePath)
    }

    @Test
    fun `remote hierarchy emits directory creation before file download`() {
        val base = manifest()
        val remote = base.copy(
            revision = 3L,
            nodes = listOf(
                directory("Series", revision = 3L),
                file("Series/Book.epub", hash = "b".repeat(64), size = 30L, revision = 3L),
            ),
        )
        val plan = planCloudFolderSync(base, base.copy(revision = 2L), remote, nowMillis = 30L)
        assertEquals(
            listOf(
                CloudFolderSyncOperationKind.CREATE_LOCAL_DIRECTORY,
                CloudFolderSyncOperationKind.DOWNLOAD_FILE,
            ),
            plan.operations.map(CloudFolderSyncOperation::kind),
        )
        assertEquals(CloudFolderSyncDirection.CLOUD_TO_LOCAL, plan.operations[1].direction)
    }

    @Test
    fun `one side content change wins and same content change is idempotent`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val localChanged = original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 2L)
        val localPlan = planCloudFolderSync(
            base,
            base.copy(revision = 2L, nodes = listOf(localChanged)),
            base.copy(revision = 1L),
        )
        assertEquals(CloudFolderSyncOperationKind.UPLOAD_FILE, localPlan.operations.single().kind)
        assertTrue(localPlan.canCommit)

        val sameRemotePlan = planCloudFolderSync(
            base,
            base.copy(revision = 2L, nodes = listOf(localChanged)),
            base.copy(revision = 3L, nodes = listOf(localChanged.copy(revision = 3L))),
        )
        assertTrue(sameRemotePlan.canCommit)
        assertTrue(sameRemotePlan.operations.isEmpty())
        assertEquals("b".repeat(64), sameRemotePlan.mergedManifest.nodes.single().contentHash)
    }

    @Test
    fun `both changed content produces a conflict and safe base candidate`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val local = base.copy(revision = 2L, nodes = listOf(original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 2L)))
        val remote = base.copy(revision = 3L, nodes = listOf(original.copy(contentHash = "c".repeat(64), sizeBytes = 12L, revision = 3L)))
        val plan = planCloudFolderSync(base, local, remote)
        assertFalse(plan.canCommit)
        val conflict = plan.conflicts.single()
        assertEquals(CloudFolderConflictType.CONTENT_CHANGED_BOTH, conflict.type)
        assertEquals("b".repeat(64), conflict.localNode?.contentHash)
        assertEquals("c".repeat(64), conflict.remoteNode?.contentHash)
        assertEquals("a".repeat(64), plan.mergedManifest.nodes.single().contentHash)
        assertTrue(plan.operations.isEmpty())
    }

    @Test
    fun `delete versus unchanged update is safe and emits one directional deletion`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val localDeleted = base.copy(
            revision = 2L,
            nodes = emptyList(),
            tombstones = listOf(
                CloudFolderTombstone("book", "root-a", "Book.pdf", CloudFolderNodeKind.FILE, deletedRevision = 2L)
            ),
        )
        val plan = planCloudFolderSync(base, localDeleted, base.copy(revision = 1L))
        assertTrue(plan.canCommit)
        assertEquals(CloudFolderSyncOperationKind.DELETE_REMOTE, plan.operations.single().kind)
        assertEquals(listOf("Book.pdf"), plan.mergedManifest.tombstones.map { it.relativePath })
    }

    @Test
    fun `simultaneous delete and content update requires user action`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val localDeleted = base.copy(
            revision = 2L,
            nodes = emptyList(),
            tombstones = listOf(CloudFolderTombstone("Book.pdf", "root-a", "Book.pdf", CloudFolderNodeKind.FILE, deletedRevision = 2L)),
        )
        val remoteUpdated = base.copy(
            revision = 3L,
            nodes = listOf(original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 3L)),
        )
        val plan = planCloudFolderSync(base, localDeleted, remoteUpdated)
        assertFalse(plan.canCommit)
        assertEquals(CloudFolderConflictType.DELETE_VS_UPDATE, plan.conflicts.single().type)
        assertEquals("a".repeat(64), plan.mergedManifest.nodes.single().contentHash)
    }

    @Test
    fun `one-sided rename emits move while common rename is not a conflict`() {
        val original = file("Old/Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val renamed = original.copy(relativePath = "New/Book.pdf", revision = 2L)
        val localPlan = planCloudFolderSync(base, base.copy(revision = 2L, nodes = listOf(renamed)), base)
        assertEquals(CloudFolderSyncOperationKind.MOVE_REMOTE, localPlan.operations.single().kind)
        assertEquals("Old/Book.pdf", localPlan.operations.single().previousRelativePath)

        val commonPlan = planCloudFolderSync(
            base,
            base.copy(revision = 2L, nodes = listOf(renamed)),
            base.copy(revision = 3L, nodes = listOf(renamed.copy(revision = 3L))),
        )
        assertTrue(commonPlan.canCommit)
        assertTrue(commonPlan.operations.isEmpty())
    }

    @Test
    fun `different simultaneous renames produce move conflict`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val local = base.copy(revision = 2L, nodes = listOf(original.copy(relativePath = "A/Book.pdf", revision = 2L)))
        val remote = base.copy(revision = 3L, nodes = listOf(original.copy(relativePath = "B/Book.pdf", revision = 3L)))
        val plan = planCloudFolderSync(base, local, remote)
        assertEquals(CloudFolderConflictType.MOVE_CHANGED_BOTH, plan.conflicts.single().type)
        assertFalse(plan.canCommit)
    }

    @Test
    fun `new nodes with the same path produce path collision rather than overwrite`() {
        val base = manifest()
        val local = base.copy(revision = 2L, nodes = listOf(file("Book.pdf", id = "local-book", hash = "a".repeat(64))))
        val remote = base.copy(revision = 3L, nodes = listOf(file("book.pdf", id = "remote-book", hash = "b".repeat(64))))
        val plan = planCloudFolderSync(base, local, remote)
        val collision = plan.conflicts.firstOrNull { it.type == CloudFolderConflictType.PATH_COLLISION }
        assertNotNull(collision)
        assertEquals(setOf("local-book", "remote-book"), collision.relatedNodeIds.toSet())
        assertTrue(plan.operations.isEmpty())
        assertFalse(plan.canCommit)
    }

    @Test
    fun `root mismatch never generates file operations`() {
        val base = manifest()
        val remote = CloudFolderManifest(root = root(rootId = "other-root"), revision = 4L)
        val plan = planCloudFolderSync(base, base.copy(revision = 2L), remote)
        assertEquals(CloudFolderConflictType.ROOT_MISMATCH, plan.conflicts.single().type)
        assertTrue(plan.operations.isEmpty())
        assertFalse(plan.canCommit)
        assertEquals(base.rootId, plan.mergedManifest.rootId)
    }

    @Test
    fun `revision advances monotonically and merged root gets scan statistics`() {
        val base = manifest(revision = 7L)
        val local = base.copy(revision = 9L, generatedAt = 100L, nodes = listOf(file("Book.pdf", hash = "a".repeat(64), size = 25L)))
        val remote = base.copy(revision = 8L)
        val plan = planCloudFolderSync(base, local, remote, nowMillis = 500L, deviceId = "pixel")
        assertEquals(10L, plan.nextRevision)
        assertEquals(10L, plan.mergedManifest.revision)
        assertEquals("pixel", plan.mergedManifest.generatedByDeviceId)
        assertEquals(1, plan.mergedManifest.root.stats.fileCount)
        assertEquals(25L, plan.mergedManifest.root.stats.totalBytes)
    }

    private fun root(
        rootId: String = "root-a",
        name: String = "Books",
    ): CloudFolderRoot = CloudFolderRoot(
        rootId = rootId,
        name = name,
        createdAt = 1L,
        createdByDeviceId = "pixel-9",
    )

    private fun manifest(
        revision: Long = 0L,
        nodes: List<CloudFolderNode> = emptyList(),
    ): CloudFolderManifest = CloudFolderManifest(
        root = root(),
        revision = revision,
        generatedAt = revision,
        generatedByDeviceId = "pixel-9",
        nodes = nodes,
    )

    private fun directory(
        path: String,
        id: String = path,
        rootId: String = "root-a",
        revision: Long = 1L,
    ): CloudFolderNode = CloudFolderNode(
        nodeId = id,
        rootId = rootId,
        relativePath = path,
        kind = CloudFolderNodeKind.DIRECTORY,
        revision = revision,
    )

    private fun file(
        path: String,
        id: String = path,
        rootId: String = "root-a",
        hash: String? = null,
        size: Long = 0L,
        revision: Long = 1L,
    ): CloudFolderNode = CloudFolderNode(
        nodeId = id,
        rootId = rootId,
        relativePath = path,
        kind = CloudFolderNodeKind.FILE,
        contentHash = hash,
        sizeBytes = size,
        revision = revision,
    )
}
