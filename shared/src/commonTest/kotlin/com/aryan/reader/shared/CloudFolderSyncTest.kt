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
    fun `legacy policies can be represented as an explicit selection`() {
        val roots = listOf("root-a", "root-b")

        val excluded = CloudFolderSyncSelection(
            mode = CloudFolderSyncSelectionMode.EXCLUDED,
        ).toExplicitSelection(roots)
        assertEquals(CloudFolderSyncSelectionMode.SELECTED, excluded.mode)
        assertTrue(excluded.selectedRootIds.isEmpty())

        val all = CloudFolderSyncSelection(
            mode = CloudFolderSyncSelectionMode.ALL,
        ).toExplicitSelection(roots)
        assertEquals(CloudFolderSyncSelectionMode.SELECTED, all.mode)
        assertEquals(roots.toSet(), all.selectedRootIds)

        // Do not turn a still-loading ALL policy into an empty selection.
        assertEquals(
            CloudFolderSyncSelectionMode.ALL,
            CloudFolderSyncSelection(mode = CloudFolderSyncSelectionMode.ALL)
                .toExplicitSelection(emptyList())
                .mode,
        )
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
    fun `manifest root ids reject filesystem traversal`() {
        val unsafeRootIds = listOf("../outside", "nested/root", "absolute\\root", ".", "..")
        unsafeRootIds.forEach { rootId ->
            val manifest = manifest().copy(root = root().copy(rootId = rootId))
            assertTrue(
                CloudFolderManifestIssueType.INVALID_ROOT_ID in
                    manifest.validationIssues().map { it.type },
            )
        }
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
    fun `raw validation rejects unsupported schema before normalization`() {
        val invalid = manifest().copy(
            schemaVersion = CLOUD_FOLDER_MANIFEST_SCHEMA_VERSION + 1,
            nodes = listOf(file("Book.pdf", size = -1L)),
        )

        assertTrue(CloudFolderManifestIssueType.UNSUPPORTED_SCHEMA_VERSION in invalid.validationIssues().map { it.type })
        assertTrue(CloudFolderManifestIssueType.NEGATIVE_SIZE in invalid.validationIssues().map { it.type })
        assertEquals(invalid.schemaVersion, invalid.normalized().schemaVersion)

        val plan = planCloudFolderSync(invalid, invalid, invalid)
        assertFalse(plan.canCommit)
        assertTrue(plan.operations.isEmpty())
        assertTrue(plan.conflicts.all { it.type == CloudFolderConflictType.INVALID_MANIFEST })
    }

    @Test
    fun `manifest validation requires every parent to be an explicit directory`() {
        val missingParent = manifest(
            nodes = listOf(file("Series/Book.epub")),
        )
        assertTrue(
            CloudFolderManifestIssueType.MISSING_PARENT_DIRECTORY in
                missingParent.validationIssues().map { it.type },
        )

        val fileParent = manifest(
            nodes = listOf(
                file("Series"),
                file("Series/Book.epub"),
            ),
        )
        assertTrue(
            CloudFolderManifestIssueType.PARENT_NOT_DIRECTORY in
                fileParent.validationIssues().map { it.type },
        )

        val plan = planCloudFolderSync(missingParent, missingParent, missingParent)
        assertFalse(plan.canCommit)
        assertTrue(plan.operations.isEmpty())
    }

    @Test
    fun `manifest validation imposes a bounded node list`() {
        val oversized = manifest(
            nodes = List(MAX_CLOUD_FOLDER_MANIFEST_NODES + 1) { index ->
                file("Book-$index.pdf", id = "book-$index")
            },
        )
        assertTrue(
            CloudFolderManifestIssueType.TOO_MANY_NODES in
                oversized.validationIssues().map { it.type },
        )
        assertFalse(planCloudFolderSync(oversized, oversized, oversized).canCommit)
    }

    @Test
    fun `logical root ids have UUID strength and do not expose the seed`() {
        val id = cloudFolderRootId("device-generated-uuid-1234")
        val suffix = id.removePrefix("folder_root_")
        assertEquals(32, suffix.length)
        assertTrue(suffix.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(id.contains("device-generated-uuid-1234"))
        assertTrue(id != cloudFolderRootId("other-device-generated-uuid"))
    }

    @Test
    fun `incoming download all explicitly requests offline materialization`() {
        assertEquals(
            CloudFolderMaterializationMode.KEEP_OFFLINE,
            CloudFolderIncomingChoice.DOWNLOAD_ALL.materializationMode,
        )
        assertTrue(CloudFolderIncomingChoice.DOWNLOAD_ALL.shouldIncludeInLocalSyncSelection)
        assertEquals(
            CloudFolderMaterializationMode.CLOUD_ONLY,
            CloudFolderIncomingChoice.CLOUD_ONLY.materializationMode,
        )
        assertFalse(CloudFolderIncomingChoice.CLOUD_ONLY.shouldIncludeInLocalSyncSelection)
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
        val base = manifest(
            nodes = listOf(directory("Books", revision = 1L)),
        )
        val local = base.copy(
            revision = 2L,
            nodes = listOf(
                directory("Books", revision = 1L),
                file("Books/Book.pdf", hash = "a".repeat(64), size = 12L, revision = 2L),
            ),
        )
        val plan = planCloudFolderSync(base, local, base.copy(revision = 1L), nowMillis = 20L, deviceId = "pixel")
        val operation = plan.operations.single()
        assertEquals(CloudFolderSyncOperationKind.UPLOAD_FILE, operation.kind)
        assertEquals(CloudFolderSyncDirection.LOCAL_TO_CLOUD, operation.direction)
        assertEquals("Books/Book.pdf", operation.relativePath)
        assertEquals("a".repeat(64), operation.contentHash)
        assertTrue(plan.canCommit)
        assertEquals(
            "Books/Book.pdf",
            plan.mergedManifest.nodes.first { it.isFile }.relativePath,
        )
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
    fun `hashed file metadata changes emit metadata operations without reuploading bytes`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val unchangedRemote = base.copy(revision = 1L)

        listOf(
            original.copy(mimeType = "application/pdf", revision = 2L),
            original.copy(fileModifiedAt = 42L, revision = 2L),
        ).forEach { changedNode ->
            val local = base.copy(revision = 2L, nodes = listOf(changedNode))
            val plan = planCloudFolderSync(base, local, unchangedRemote)

            assertTrue(plan.canCommit)
            assertEquals(CloudFolderSyncOperationKind.UPDATE_REMOTE_METADATA, plan.operations.single().kind)
            assertEquals(CloudFolderSyncDirection.LOCAL_TO_CLOUD, plan.operations.single().direction)
            assertEquals(changedNode.contentHash, plan.operations.single().contentHash)
            assertEquals(changedNode.mimeType, plan.mergedManifest.nodes.single().mimeType)
            assertEquals(changedNode.fileModifiedAt, plan.mergedManifest.nodes.single().fileModifiedAt)
        }
    }

    @Test
    fun `independent metadata changes on both sides produce a metadata conflict`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val local = base.copy(
            revision = 2L,
            nodes = listOf(original.copy(mimeType = "application/pdf", revision = 2L)),
        )
        val remote = base.copy(
            revision = 3L,
            nodes = listOf(original.copy(fileModifiedAt = 42L, revision = 3L)),
        )

        val plan = planCloudFolderSync(base, local, remote)

        assertFalse(plan.canCommit)
        assertTrue(plan.operations.isEmpty())
        assertEquals(CloudFolderConflictType.METADATA_CHANGED_BOTH, plan.conflicts.single().type)
        assertEquals(original.contentHash, plan.conflicts.single().localNode?.contentHash)
        assertEquals(original.contentHash, plan.conflicts.single().remoteNode?.contentHash)
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
    fun `sidecar conflicts never create a generic keep both copy`() {
        val original = file("EpistemeSyncData/.book_abc.json", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val local = base.copy(
            revision = 2L,
            nodes = listOf(original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 2L)),
        )
        val remote = base.copy(
            revision = 3L,
            nodes = listOf(original.copy(contentHash = "c".repeat(64), sizeBytes = 12L, revision = 3L)),
        )

        val plan = planCloudFolderSync(base, local, remote)

        assertEquals(CloudFolderConflictType.SIDECAR_CHANGED_BOTH, plan.conflicts.single().type)
        assertFalse(plan.conflicts.single().type.supportsKeepBoth())
        assertEquals(
            CloudFolderConflictResolution.KEEP_LOCAL,
            plan.conflicts.single().type.effectiveResolution(CloudFolderConflictResolution.KEEP_BOTH),
        )
        val resolved = resolveCloudFolderSync(
            base = base,
            local = local,
            remote = remote,
            plan = plan,
            resolutions = mapOf(plan.conflicts.single().conflictId to CloudFolderConflictResolution.KEEP_BOTH),
        )
        assertTrue(resolved.canCommit)
        assertEquals(1, resolved.mergedManifest.activeFiles().count { it.pathKey == original.pathKey })
        assertEquals("b".repeat(64), resolved.mergedManifest.activeFiles().single().contentHash)
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
    fun `keep both on delete versus update keeps the only surviving cloud file`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val localDeleted = base.copy(
            revision = 2L,
            nodes = emptyList(),
            tombstones = listOf(
                CloudFolderTombstone(
                    nodeId = original.nodeId,
                    rootId = original.rootId,
                    relativePath = original.relativePath,
                    kind = CloudFolderNodeKind.FILE,
                    deletedRevision = 2L,
                ),
            ),
        )
        val remoteUpdated = base.copy(
            revision = 3L,
            nodes = listOf(original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 3L)),
        )
        val pending = planCloudFolderSync(base, localDeleted, remoteUpdated)
        val conflict = pending.conflicts.single()
        val resolved = resolveCloudFolderSync(
            base = base,
            local = localDeleted,
            remote = remoteUpdated,
            plan = pending,
            resolutions = mapOf(conflict.conflictId to CloudFolderConflictResolution.KEEP_BOTH),
        )

        assertTrue(resolved.canCommit)
        assertEquals("b".repeat(64), resolved.mergedManifest.activeFiles().single().contentHash)
        assertTrue(resolved.mergedManifest.tombstones.isEmpty())
        assertEquals(CloudFolderSyncOperationKind.DOWNLOAD_FILE, resolved.operations.single().kind)
        assertEquals(CloudFolderSyncDirection.CLOUD_TO_LOCAL, resolved.operations.single().direction)
    }

    @Test
    fun `keep both on update versus delete keeps the only surviving local file`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val localUpdated = base.copy(
            revision = 2L,
            nodes = listOf(original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 2L)),
        )
        val remoteDeleted = base.copy(
            revision = 3L,
            nodes = emptyList(),
            tombstones = listOf(
                CloudFolderTombstone(
                    nodeId = original.nodeId,
                    rootId = original.rootId,
                    relativePath = original.relativePath,
                    kind = CloudFolderNodeKind.FILE,
                    deletedRevision = 3L,
                ),
            ),
        )
        val pending = planCloudFolderSync(base, localUpdated, remoteDeleted)
        val conflict = pending.conflicts.single()
        val resolved = resolveCloudFolderSync(
            base = base,
            local = localUpdated,
            remote = remoteDeleted,
            plan = pending,
            resolutions = mapOf(conflict.conflictId to CloudFolderConflictResolution.KEEP_BOTH),
        )

        assertTrue(resolved.canCommit)
        assertEquals("b".repeat(64), resolved.mergedManifest.activeFiles().single().contentHash)
        assertTrue(resolved.mergedManifest.tombstones.isEmpty())
        assertEquals(CloudFolderSyncOperationKind.UPLOAD_FILE, resolved.operations.single().kind)
        assertEquals(CloudFolderSyncDirection.LOCAL_TO_CLOUD, resolved.operations.single().direction)
    }

    @Test
    fun `persisted keep local decision resolves only the conflicted node`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val local = base.copy(
            revision = 2L,
            nodes = listOf(original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 2L)),
        )
        val remote = base.copy(
            revision = 3L,
            nodes = listOf(original.copy(contentHash = "c".repeat(64), sizeBytes = 12L, revision = 3L)),
        )
        val pending = planCloudFolderSync(base, local, remote)
        val conflict = pending.conflicts.single()
        val resolved = resolveCloudFolderSync(
            base = base,
            local = local,
            remote = remote,
            plan = pending,
            resolutions = mapOf(conflict.conflictId to CloudFolderConflictResolution.KEEP_LOCAL),
            nowMillis = 10L,
            deviceId = "pixel",
        )

        assertTrue(resolved.canCommit)
        assertEquals("b".repeat(64), resolved.mergedManifest.activeFiles().single().contentHash)
        assertEquals(CloudFolderSyncOperationKind.UPLOAD_FILE, resolved.operations.single().kind)
        assertEquals(CloudFolderSyncDirection.LOCAL_TO_CLOUD, resolved.operations.single().direction)
    }

    @Test
    fun `persisted keep cloud decision materializes the remote version`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val local = base.copy(
            revision = 2L,
            nodes = listOf(original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 2L)),
        )
        val remote = base.copy(
            revision = 3L,
            nodes = listOf(original.copy(contentHash = "c".repeat(64), sizeBytes = 12L, revision = 3L)),
        )
        val pending = planCloudFolderSync(base, local, remote)
        val conflict = pending.conflicts.single()
        val resolved = resolveCloudFolderSync(
            base,
            local,
            remote,
            pending,
            mapOf(conflict.conflictId to CloudFolderConflictResolution.KEEP_REMOTE),
        )

        assertTrue(resolved.canCommit)
        assertEquals("c".repeat(64), resolved.mergedManifest.activeFiles().single().contentHash)
        assertEquals(CloudFolderSyncOperationKind.DOWNLOAD_FILE, resolved.operations.single().kind)
        assertEquals(CloudFolderSyncDirection.CLOUD_TO_LOCAL, resolved.operations.single().direction)
    }

    @Test
    fun `keep both creates a deterministic local copy and retains cloud bytes`() {
        val original = file(
            "Book.pdf",
            hash = "a".repeat(64),
            size = 10L,
            revision = 1L,
        )
        val base = manifest(revision = 1L, nodes = listOf(original))
        val localNode = original.copy(contentHash = "b".repeat(64), sizeBytes = 11L, revision = 2L)
        val remoteNode = original.copy(
            contentHash = "c".repeat(64),
            sizeBytes = 12L,
            revision = 3L,
            contentObjectId = "drive-cloud-book",
        )
        val local = base.copy(revision = 2L, nodes = listOf(localNode))
        val remote = base.copy(revision = 3L, nodes = listOf(remoteNode))
        val pending = planCloudFolderSync(base, local, remote)
        val conflict = pending.conflicts.single()
        val resolved = resolveCloudFolderSync(
            base = base,
            local = local,
            remote = remote,
            plan = pending,
            resolutions = mapOf(conflict.conflictId to CloudFolderConflictResolution.KEEP_BOTH),
            nowMillis = 40L,
            deviceId = "pixel",
        )

        assertTrue(resolved.canCommit)
        assertEquals(2, resolved.mergedManifest.activeFiles().size)
        assertTrue(resolved.mergedManifest.activeFiles().any { it.contentHash == "c".repeat(64) })
        val localCopy = resolved.mergedManifest.activeFiles().single { it.contentHash == "b".repeat(64) }
        assertTrue(localCopy.relativePath != "Book.pdf")
        assertTrue(localCopy.relativePath.contains("Local copy"))
        val upload = resolved.operations.single { it.kind == CloudFolderSyncOperationKind.UPLOAD_FILE }
        assertEquals(original.nodeId, upload.sourceNodeId)
        assertTrue(resolved.operations.any { it.kind == CloudFolderSyncOperationKind.DOWNLOAD_FILE })
    }

    @Test
    fun `deferred decision keeps conflict unresolved and stale plan cannot be reused`() {
        val original = file("Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val local = base.copy(revision = 2L, nodes = listOf(original.copy(contentHash = "b".repeat(64), revision = 2L)))
        val remote = base.copy(revision = 3L, nodes = listOf(original.copy(contentHash = "c".repeat(64), revision = 3L)))
        val pending = planCloudFolderSync(base, local, remote)
        val conflict = pending.conflicts.single()
        val deferred = resolveCloudFolderSync(
            base,
            local,
            remote,
            pending,
            mapOf(conflict.conflictId to CloudFolderConflictResolution.DEFER),
        )
        assertFalse(deferred.canCommit)
        assertEquals(1, deferred.conflicts.size)

        val newerLocal = local.copy(revision = 4L, nodes = listOf(original.copy(contentHash = "d".repeat(64), revision = 4L)))
        val newerPlan = planCloudFolderSync(base, newerLocal, remote)
        val stale = resolveCloudFolderSync(
            base,
            newerLocal,
            remote,
            pending,
            mapOf(conflict.conflictId to CloudFolderConflictResolution.KEEP_LOCAL),
        )
        assertEquals(pending.conflicts, stale.conflicts)
        assertTrue(newerPlan.conflicts != pending.conflicts)
        assertFalse(stale.canCommit)
    }

    @Test
    fun `one-sided rename emits move while common rename is not a conflict`() {
        val original = file("Old-Book.pdf", hash = "a".repeat(64), size = 10L, revision = 1L)
        val base = manifest(revision = 1L, nodes = listOf(original))
        val renamed = original.copy(relativePath = "New-Book.pdf", revision = 2L)
        val localPlan = planCloudFolderSync(base, base.copy(revision = 2L, nodes = listOf(renamed)), base)
        assertEquals(CloudFolderSyncOperationKind.MOVE_REMOTE, localPlan.operations.single().kind)
        assertEquals("Old-Book.pdf", localPlan.operations.single().previousRelativePath)

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
        val local = base.copy(revision = 2L, nodes = listOf(original.copy(relativePath = "A-Book.pdf", revision = 2L)))
        val remote = base.copy(revision = 3L, nodes = listOf(original.copy(relativePath = "B-Book.pdf", revision = 3L)))
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

    @Test
    fun `files missing content object ids reports only active file nodes`() {
        val withId = file("Book.epub", id = "with-id", hash = "a".repeat(64), size = 5L)
            .copy(contentObjectId = "drive-book")
        val blankId = file("Blank.epub", id = "blank-id", hash = "b".repeat(64), size = 6L)
            .copy(contentObjectId = "   ")
        val withoutId = file("Missing.epub", id = "missing-id", hash = "c".repeat(64), size = 7L)
        val folder = directory("Series", id = "series")
        val manifest = manifest(
            revision = 3L,
            nodes = listOf(withId, blankId, withoutId, folder),
        )

        val missing = manifest.filesMissingContentObjectIds()

        assertEquals(listOf("blank-id", "missing-id"), missing.map { it.nodeId })
    }

    @Test
    fun `files missing content object ids ignores foreign roots and tombstones`() {
        val foreign = file("Foreign.epub", id = "foreign", rootId = "root-b", hash = "a".repeat(64))
            .copy(contentObjectId = null)
        val manifest = manifest(revision = 2L, nodes = listOf(foreign))

        assertTrue(manifest.filesMissingContentObjectIds().isEmpty())
    }

    @Test
    fun `every decidable conflict type resolves to a non-defer default`() {
        CloudFolderConflictType.entries.forEach { type ->
            val default = type.defaultResolution()
            assertTrue(
                default == CloudFolderConflictResolution.KEEP_LOCAL ||
                    default == CloudFolderConflictResolution.KEEP_REMOTE ||
                    default == CloudFolderConflictResolution.KEEP_BOTH,
                "default resolution for $type must be decidable, was $default",
            )
        }
        assertEquals(
            CloudFolderConflictResolution.KEEP_BOTH,
            CloudFolderConflictType.CONTENT_CHANGED_BOTH.defaultResolution(),
        )
        assertEquals(
            CloudFolderConflictResolution.KEEP_LOCAL,
            CloudFolderConflictType.SIDECAR_CHANGED_BOTH.defaultResolution(),
        )
        assertEquals(
            CloudFolderConflictResolution.KEEP_REMOTE,
            CloudFolderConflictType.DELETE_VS_UPDATE.defaultResolution(),
        )
        assertEquals(
            CloudFolderConflictResolution.KEEP_LOCAL,
            CloudFolderConflictType.UPDATE_VS_DELETE.defaultResolution(),
        )
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
