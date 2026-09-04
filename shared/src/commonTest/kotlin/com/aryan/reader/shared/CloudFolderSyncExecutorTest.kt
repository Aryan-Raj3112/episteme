package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudFolderSyncExecutorTest {

    private fun root(id: String = "root-a", revision: Long = 3L) = CloudFolderRoot(
        rootId = id,
        name = "Books",
        manifestRevision = revision,
    )

    private fun manifest(root: CloudFolderRoot = root(), nodes: List<CloudFolderNode> = emptyList()) =
        CloudFolderManifest(root = root, revision = root.manifestRevision, nodes = nodes)

    private fun fileNode(
        path: String,
        hash: String = "sha256:" + "a".repeat(64),
        size: Long = 10L,
        nodeId: String = cloudFolderNodeId("root-a", path),
        objectId: String? = "drive-obj-1",
    ) = CloudFolderNode(
        nodeId = nodeId,
        rootId = "root-a",
        relativePath = path,
        kind = CloudFolderNodeKind.FILE,
        contentHash = hash,
        sizeBytes = size,
        mimeType = "application/pdf",
        fileModifiedAt = 100L,
        revision = 3L,
        contentObjectId = objectId,
    )

    @Test
    fun `codec round-trips and matches Android normalization`() {
        val original = manifest(nodes = listOf(fileNode("b.pdf")))
        val encoded = encodeCloudFolderManifest(original)
        val decoded = decodeCloudFolderManifestOrNull(encoded)
        assertNotNull(decoded)
        assertEquals(original.normalized(), decoded)
        assertTrue(cloudFolderManifestSha256Hex(encoded).startsWith("sha256:"))
        assertEquals(71, cloudFolderManifestSha256Hex(encoded).length)
        // Encoding is canonical: re-encoding the decoded value is stable.
        assertEquals(encoded, encodeCloudFolderManifest(decoded))
    }

    @Test
    fun `codec rejects invalid manifests`() {
        assertNull(decodeCloudFolderManifestOrNull("not json"))
        val tooMany = manifest().copy(
            nodes = List(MAX_CLOUD_FOLDER_MANIFEST_NODES + 1) { fileNode("f$it.pdf", nodeId = "n$it") },
        )
        // Encoded directly (bypassing validation) then decoded: rejected.
        val raw = JsonLenient.encode(tooMany)
        assertNull(decodeCloudFolderManifestOrNull(raw))
    }

    @Test
    fun `unchanged scan keeps revision and object ids`() {
        val node = fileNode("b.pdf")
        val base = manifest(nodes = listOf(node))
        val local = buildCloudFolderLocalManifest(base, listOf(node.copy()), 200L, "d1")
        assertEquals(3L, local.revision)
        assertEquals("drive-obj-1", local.activeNodes().single().contentObjectId)
        assertEquals(3L, local.activeNodes().single().revision)
        assertTrue(local.tombstones.isEmpty())
    }

    @Test
    fun `changed bytes bump revision and drop object id`() {
        val node = fileNode("b.pdf")
        val base = manifest(nodes = listOf(node))
        val changed = node.copy(contentHash = "sha256:" + "b".repeat(64), sizeBytes = 20L)
        val local = buildCloudFolderLocalManifest(base, listOf(changed), 200L, "d1")
        assertEquals(4L, local.revision)
        assertNull(local.activeNodes().single().contentObjectId)
        assertEquals(4L, local.activeNodes().single().revision)
    }

    @Test
    fun `same bytes with new mtime keep object id`() {
        val node = fileNode("b.pdf")
        val base = manifest(nodes = listOf(node))
        val rescanned = node.copy(fileModifiedAt = 999L, mimeType = "application/x-pdf")
        val local = buildCloudFolderLocalManifest(base, listOf(rescanned), 200L, "d1")
        assertEquals(3L, local.revision)
        assertEquals("drive-obj-1", local.activeNodes().single().contentObjectId)
    }

    @Test
    fun `deletions become tombstones and resurrections clear them`() {
        val node = fileNode("b.pdf")
        val base = manifest(nodes = listOf(node))
        val deleted = buildCloudFolderLocalManifest(base, emptyList(), 200L, "d1")
        assertEquals(4L, deleted.revision)
        assertEquals(1, deleted.tombstones.size)
        assertEquals("b.pdf", deleted.tombstones.single().relativePath)

        val resurrected = buildCloudFolderLocalManifest(deleted, listOf(node.copy()), 300L, "d1")
        assertTrue(resurrected.tombstones.isEmpty())
    }

    private fun planWithConflict(): CloudFolderSyncPlan {
        val base = manifest()
        val conflict = CloudFolderConflict(
            conflictId = "c1",
            rootId = "root-a",
            nodeId = "n1",
            type = CloudFolderConflictType.CONTENT_CHANGED_BOTH,
            relativePath = "b.pdf",
        )
        return CloudFolderSyncPlan(
            rootId = "root-a",
            baseRevision = 3L,
            localRevision = 4L,
            remoteRevision = 5L,
            nextRevision = 6L,
            conflicts = listOf(conflict),
            mergedManifest = base,
        )
    }

    @Test
    fun `reconcile keeps choices only for identical snapshots`() {
        val plan = planWithConflict()
        val stored = listOf(
            CloudFolderConflictRecord(
                conflict = plan.conflicts.single(),
                baseRevision = 3L,
                localRevision = 4L,
                remoteRevision = 5L,
                resolution = CloudFolderConflictResolution.KEEP_LOCAL,
                createdAt = 10L,
                updatedAt = 11L,
            ),
        )
        val kept = reconcileCloudFolderConflicts(plan, stored, nowMillis = 99L)
        assertEquals(CloudFolderConflictResolution.KEEP_LOCAL, kept.single().resolution)
        assertEquals(10L, kept.single().createdAt)

        // Changed remote revision resets to DEFER with fresh timestamps.
        val stale = reconcileCloudFolderConflicts(
            plan.copy(remoteRevision = 6L),
            stored,
            nowMillis = 99L,
        )
        assertEquals(CloudFolderConflictResolution.DEFER, stale.single().resolution)
        assertEquals(99L, stale.single().createdAt)

        // Empty plan clears.
        assertTrue(reconcileCloudFolderConflicts(plan.copy(conflicts = emptyList()), stored, 99L).isEmpty())
    }

    @Test
    fun `resolutions prefer stored choices then type defaults`() {
        val plan = planWithConflict()
        val stored = listOf(
            CloudFolderConflictRecord(
                conflict = plan.conflicts.single(),
                baseRevision = 3L,
                localRevision = 4L,
                remoteRevision = 5L,
                resolution = CloudFolderConflictResolution.KEEP_REMOTE,
                createdAt = 10L,
                updatedAt = 11L,
            ),
        )
        assertEquals(
            CloudFolderConflictResolution.KEEP_REMOTE,
            cloudFolderResolutionsForPlan(plan, stored)["c1"],
        )
        // DEFER falls back to the deterministic default (KEEP_BOTH for content).
        val deferred = stored.map { it.copy(resolution = CloudFolderConflictResolution.DEFER) }
        assertEquals(
            CloudFolderConflictType.CONTENT_CHANGED_BOTH.defaultResolution(),
            cloudFolderResolutionsForPlan(plan, deferred)["c1"],
        )
    }

    @Test
    fun `outbox ids are deterministic per operation`() {
        val op = CloudFolderSyncOperation(
            nodeId = "n1",
            kind = CloudFolderSyncOperationKind.UPLOAD_FILE,
            direction = CloudFolderSyncDirection.LOCAL_TO_CLOUD,
            relativePath = "b.pdf",
            revision = 4L,
        )
        val first = cloudFolderOutboxOperationId(op, "acc", "root-a")
        assertEquals(first, cloudFolderOutboxOperationId(op, "acc", "root-a"))
        assertTrue(first.startsWith("folder_op_"))
        // Different account scopes to a different id (Android parity).
        assertTrue(cloudFolderOutboxOperationId(op, "other", "root-a") != first)
    }

    @Test
    fun `roots compare ignores revisions and stats`() {
        val first = root().copy(stats = CloudFolderRootStats(fileCount = 1))
        val second = root().copy(manifestRevision = 99L, stats = CloudFolderRootStats(fileCount = 5))
        assertTrue(cloudFolderRootsEquivalentForPublish(first, second))
        assertTrue(!cloudFolderRootsEquivalentForPublish(first, second.copy(name = "Other")))
        assertTrue(!cloudFolderRootsEquivalentForPublish(first, second.copy(isDeleted = true)))
    }

    @Test
    fun `pull gates mirror the worker predicates`() {
        assertTrue(shouldPullCloudFolderRoot(isDeleted = false, isIncluded = true, hasBinding = true))
        assertTrue(!shouldPullCloudFolderRoot(isDeleted = true, isIncluded = true, hasBinding = true))
        assertTrue(!shouldPullCloudFolderRoot(isDeleted = false, isIncluded = false, hasBinding = true))
        assertTrue(
            shouldQueueCloudFolderPullAfterRemoteChange(
                hasCloudToLocalOperations = true, isSelected = true,
                hasBinding = true, isSignedIn = true, syncEnabled = true,
            ),
        )
        assertTrue(
            !shouldQueueCloudFolderPullAfterRemoteChange(
                hasCloudToLocalOperations = true, isSelected = true,
                hasBinding = true, isSignedIn = true, syncEnabled = false,
            ),
        )
    }

    @Test
    fun `gc keeps referenced recent and unknown-timestamp objects`() {
        val oldUnreferenced = CloudFolderStoredObjectRef("id-old", rootId = "r", nodeId = "n", modifiedTimeMillis = 1L)
        val recentUnreferenced = CloudFolderStoredObjectRef("id-new", rootId = "r", nodeId = "n", modifiedTimeMillis = 9_000L)
        val referenced = CloudFolderStoredObjectRef("id-ref", rootId = "r", nodeId = "n", modifiedTimeMillis = 1L)
        val unknownTime = CloudFolderStoredObjectRef("id-unknown", rootId = "r", nodeId = "n", modifiedTimeMillis = 0L)
        val plan = planSharedCloudFolderGarbageCollection(
            objects = listOf(recentUnreferenced, referenced, unknownTime, oldUnreferenced),
            referencedDriveFileIds = setOf("id-ref"),
            nowMillis = 10_000L,
            retentionMillis = 5_000L,
        )
        assertEquals(listOf("id-old"), plan.map { it.objectRef.driveFileId })
    }
}

private object JsonLenient {
    fun encode(manifest: CloudFolderManifest): String {
        // Direct serialization without validation, to craft invalid payloads.
        return kotlinx.serialization.json.Json.encodeToString(
            CloudFolderManifest.serializer(),
            manifest,
        )
    }
}
