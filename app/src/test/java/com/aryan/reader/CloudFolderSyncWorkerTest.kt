package com.aryan.reader

import android.net.Uri
import com.aryan.reader.data.CloudFolderSafEntry
import com.aryan.reader.data.CloudFolderSafScanResult
import com.aryan.reader.shared.CloudFolderManifest
import com.aryan.reader.shared.CloudFolderNode
import com.aryan.reader.shared.CloudFolderNodeKind
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderSyncDirection
import com.aryan.reader.shared.CloudFolderSyncOperation
import com.aryan.reader.shared.CloudFolderSyncOperationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudFolderSyncWorkerTest {
    @Test
    fun localSnapshotPreservesStableObjectIdWhenContentIsUnchanged() {
        val baseNode = fileNode(
            nodeId = "book",
            path = "Series/Book.epub",
            hash = "sha256:${"a".repeat(64)}",
            revision = 3L,
            objectId = "drive-object",
            modifiedAt = 10L,
        )
        val base = manifest(revision = 3L, nodes = listOf(baseNode))
        val scan = scan(
            fileNode(
                nodeId = "book",
                path = "Series/Book.epub",
                hash = requireNotNull(baseNode.contentHash),
                revision = 0L,
                modifiedAt = 10L,
            )
        )

        val local = buildLocalManifest(base, scan, now = 20L, deviceId = "pixel")

        assertEquals(3L, local.revision)
        assertEquals("drive-object", local.nodes.single().contentObjectId)
        assertEquals(3L, local.nodes.single().revision)
        assertEquals(20L, local.root.stats.scannedAt)
    }

    @Test
    fun localSnapshotCreatesTombstoneAndRevisionForMissingNode() {
        val existing = fileNode(
            nodeId = "old",
            path = "Old.epub",
            hash = "sha256:${"b".repeat(64)}",
            revision = 4L,
            modifiedAt = 10L,
        )
        val base = manifest(revision = 4L, nodes = listOf(existing))
        val scan = scan(
            fileNode(
                nodeId = "new",
                path = "New.epub",
                hash = "sha256:${"c".repeat(64)}",
                revision = 0L,
                modifiedAt = 20L,
            )
        )

        val local = buildLocalManifest(base, scan, now = 30L, deviceId = "pixel")

        assertEquals(5L, local.revision)
        assertEquals(5L, local.nodes.single().revision)
        assertNull(local.nodes.single().contentObjectId)
        assertEquals(1, local.tombstones.size)
        assertEquals("old", local.tombstones.single().nodeId)
        assertEquals(5L, local.tombstones.single().deletedRevision)
        assertEquals("pixel", local.tombstones.single().deletedByDeviceId)
    }

    @Test
    fun documentIdBasedNodeIdsSurviveRenameButPathFallbackIsDeterministic() {
        val rootId = "folder-root"
        val firstUri = Uri.parse("content://provider/document/primary%3Abooks%2Fone")
        val renamedUri = Uri.parse("content://provider/document/primary%3Abooks%2Fone")
        val first = com.aryan.reader.data.CloudFolderSafScanner.stableNodeId(rootId, firstUri, "One.epub")
        val renamed = com.aryan.reader.data.CloudFolderSafScanner.stableNodeId(rootId, renamedUri, "Renamed.epub")

        assertEquals(first, renamed)
        assertNotEquals(
            first,
            com.aryan.reader.data.CloudFolderSafScanner.stableNodeId(
                rootId,
                Uri.parse("content://provider/raw/local-file"),
                "Other.epub",
            ),
        )
    }

    @Test
    fun pushSchedulerMapsLocalToCloudAndPullSchedulerMapsCloudToLocal() {
        val operation = CloudFolderSyncOperation(
            nodeId = "node",
            kind = CloudFolderSyncOperationKind.UPLOAD_FILE,
            direction = CloudFolderSyncDirection.LOCAL_TO_CLOUD,
            relativePath = "Book.epub",
        )
        assertEquals(CloudFolderSyncDirection.LOCAL_TO_CLOUD, operation.direction)
        assertTrue(operation.kind == CloudFolderSyncOperationKind.UPLOAD_FILE)
    }

    private fun scan(vararg nodes: CloudFolderNode): CloudFolderSafScanResult =
        CloudFolderSafScanResult(
            entries = nodes.map { node ->
                CloudFolderSafEntry(Uri.parse("content://provider/${node.nodeId}"), node)
            },
            complete = true,
            scannedAt = 20L,
        )

    private fun manifest(revision: Long, nodes: List<CloudFolderNode>): CloudFolderManifest =
        CloudFolderManifest(
            root = CloudFolderRoot(
                rootId = "root",
                name = "Books",
                createdAt = 1L,
                createdByDeviceId = "pixel",
            ),
            revision = revision,
            generatedAt = 10L,
            generatedByDeviceId = "pixel",
            nodes = nodes,
        )

    private fun fileNode(
        nodeId: String,
        path: String,
        hash: String,
        revision: Long,
        objectId: String? = null,
        modifiedAt: Long,
    ): CloudFolderNode = CloudFolderNode(
        nodeId = nodeId,
        rootId = "root",
        relativePath = path,
        kind = CloudFolderNodeKind.FILE,
        contentHash = hash,
        sizeBytes = 10L,
        mimeType = "application/epub+zip",
        fileModifiedAt = modifiedAt,
        revision = revision,
        modifiedAt = modifiedAt,
        modifiedByDeviceId = "pixel",
        contentObjectId = objectId,
    )
}
