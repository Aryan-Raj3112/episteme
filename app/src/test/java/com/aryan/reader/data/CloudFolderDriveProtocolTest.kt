package com.aryan.reader.data

import com.aryan.reader.shared.CloudFolderManifest
import com.aryan.reader.shared.CloudFolderNode
import com.aryan.reader.shared.CloudFolderNodeKind
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderSyncDirection
import com.aryan.reader.shared.CloudFolderSyncOperation
import com.aryan.reader.shared.CloudFolderSyncOperationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFolderDriveProtocolTest {
    @Test
    fun driveObjectNamesAreStableAndDoNotExposePathsOrIds() {
        val manifestName = cloudFolderManifestDriveName("folder-root")
        val contentName = cloudFolderContentDriveName("folder-root", "node/with/a/path")

        assertEquals(manifestName, cloudFolderManifestDriveName("folder-root"))
        assertEquals(contentName, cloudFolderContentDriveName("folder-root", "node/with/a/path"))
        assertTrue(manifestName.startsWith("cloud-folder-v1-manifest-"))
        assertTrue(contentName.startsWith("cloud-folder-v1-content-"))
        assertTrue("folder-root" !in manifestName)
        assertTrue("node/with/a/path" !in contentName)
        assertNotEquals(
            contentName,
            cloudFolderContentDriveName("another-root", "node/with/a/path"),
        )
    }

    @Test
    fun immutableObjectNamesIncludeRevisionAndContentAddress() {
        val hashA = "sha256:" + "a".repeat(64)
        val hashB = "sha256:" + "b".repeat(64)
        val contentA = cloudFolderContentDriveName("root", "node", hashA, revision = 3L)
        val contentB = cloudFolderContentDriveName("root", "node", hashB, revision = 4L)
        val manifestA = cloudFolderManifestDriveName("root", revision = 3L, manifestHash = hashA)
        val manifestB = cloudFolderManifestDriveName("root", revision = 4L, manifestHash = hashB)

        assertNotEquals(contentA, contentB)
        assertNotEquals(manifestA, manifestB)
        assertTrue(contentA.contains("-r3-"))
        assertTrue(manifestA.contains("-r3-"))
    }

    @Test
    fun driveMetadataRetainsPortableIdentityAndRevision() {
        val metadata = cloudFolderDriveMetadata(
            rootId = "root",
            nodeId = "node",
            relativePath = "Series/Book.epub",
            revision = 7L,
            contentHash = "sha256:abc",
        )

        assertEquals("1", metadata["cloudFolderSchema"])
        assertEquals("root", metadata["cloudFolderRootId"])
        assertEquals("node", metadata["cloudFolderNodeId"])
        assertEquals("Series/Book.epub", metadata["cloudFolderRelativePath"])
        assertEquals("7", metadata["cloudFolderRevision"])
        assertEquals("sha256:abc", metadata["cloudFolderContentHash"])
    }

    @Test
    fun driveMetadataAuthenticationRejectsWrongIdentityOrSize() {
        val hash = "sha256:" + "a".repeat(64)
        val metadata = cloudFolderDriveMetadata(
            rootId = "root",
            nodeId = "node",
            revision = 7L,
            contentHash = hash,
            contentSizeBytes = 12L,
        )

        assertTrue(
            cloudFolderDriveMetadataMatches(
                properties = metadata,
                rootId = "root",
                nodeId = "node",
                revision = 7L,
                contentHash = hash,
                contentSizeBytes = 12L,
            )
        )
        assertTrue(
            !cloudFolderDriveMetadataMatches(
                properties = metadata,
                rootId = "other-root",
                nodeId = "node",
                revision = 7L,
                contentHash = hash,
                contentSizeBytes = 12L,
            )
        )
        assertTrue(
            !cloudFolderDriveMetadataMatches(
                properties = metadata,
                rootId = "root",
                nodeId = "node",
                revision = 7L,
                contentHash = hash,
                contentSizeBytes = 13L,
            )
        )
    }

    @Test
    fun manifestCodecRoundTripsAndRejectsInvalidRoot() {
        val root = CloudFolderRoot(rootId = "root", name = "Books")
        val manifest = CloudFolderManifest(
            root = root,
            revision = 2L,
            nodes = listOf(
                CloudFolderNode(
                    nodeId = "node",
                    rootId = "root",
                    relativePath = "Book.epub",
                    kind = CloudFolderNodeKind.FILE,
                    sizeBytes = 42L,
                ),
            ),
        )

        val decoded = CloudFolderManifestCodec.decode(CloudFolderManifestCodec.encode(manifest))
        assertEquals(manifest.normalized(), decoded)

        val invalid = manifest.copy(nodes = listOf(manifest.nodes.single().copy(rootId = "other")))
        val encodedInvalid = CloudFolderManifestCodec.json.encodeToString(
            CloudFolderManifest.serializer(),
            invalid,
        )
        assertTrue(runCatching { CloudFolderManifestCodec.decode(encodedInvalid) }.isFailure)
    }

    @Test
    fun outboxOperationIdIsDeterministicPerRootAndOperation() {
        val operation = CloudFolderSyncOperation(
            nodeId = "node",
            kind = CloudFolderSyncOperationKind.UPLOAD_FILE,
            direction = CloudFolderSyncDirection.LOCAL_TO_CLOUD,
            relativePath = "Book.epub",
            revision = 4L,
        )

        assertEquals(
            cloudFolderOutboxOperationId(operation, "account-a", "root"),
            cloudFolderOutboxOperationId(operation, "account-a", "root"),
        )
        assertNotEquals(
            cloudFolderOutboxOperationId(operation, "account-a", "root"),
            cloudFolderOutboxOperationId(operation, "account-a", "other-root"),
        )
        assertNotEquals(
            cloudFolderOutboxOperationId(operation, "account-a", "root"),
            cloudFolderOutboxOperationId(operation, "account-b", "root"),
        )
        assertNotEquals(
            cloudFolderOutboxOperationId(operation, "account-a", "root"),
            cloudFolderOutboxOperationId(operation.copy(revision = 5L), "account-a", "root"),
        )
    }
}
