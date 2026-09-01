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
import org.junit.Assert.assertNull
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
    fun legacyObjectNamesRemainRecognizableWithoutExposingIds() {
        val rootId = "root"
        val nodeId = "node/with/a/path"
        val legacyManifest = cloudFolderLegacyManifestDriveName(rootId)
        val legacyContent = cloudFolderLegacyContentDriveName(rootId, nodeId)

        assertEquals("${cloudFolderManifestDrivePrefix(rootId)}.json", legacyManifest)
        assertEquals(
            "cloud-folder-v1-content-${cloudFolderDriveSegment(rootId)}-${cloudFolderDriveSegment(nodeId)}",
            legacyContent,
        )
        assertTrue("$rootId" !in legacyManifest)
        assertTrue(nodeId !in legacyContent)
        assertNotEquals(legacyManifest, cloudFolderManifestDriveName(rootId, 0L, "sha256:" + "a".repeat(64)))
        assertNotEquals(legacyContent, cloudFolderContentDriveName(rootId, nodeId, "sha256:" + "a".repeat(64), 0L))
    }

    @Test
    fun legacyMetadataRequiresExactIdentityAndOptionalPayloadMetadataMatches() {
        val rootId = "root"
        val nodeId = "node"
        val hash = "sha256:" + "a".repeat(64)
        val properties = cloudFolderDriveMetadata(
            rootId = rootId,
            nodeId = nodeId,
            revision = 4L,
            contentHash = hash,
            contentSizeBytes = 12L,
        )

        assertTrue(
            cloudFolderLegacyContentMetadataMatches(
                name = cloudFolderLegacyContentDriveName(rootId, nodeId),
                properties = properties,
                rootId = rootId,
                nodeId = nodeId,
                revision = 4L,
            )
        )
        assertTrue(
            cloudFolderLegacyOptionalContentMetadataMatches(
                properties = properties,
                expectedContentHash = hash,
                expectedSizeBytes = 12L,
            )
        )
        assertTrue(
            !cloudFolderLegacyContentMetadataMatches(
                name = cloudFolderLegacyContentDriveName(rootId, "other-node"),
                properties = properties,
                rootId = rootId,
                nodeId = nodeId,
                revision = 4L,
            )
        )
        assertTrue(
            !cloudFolderLegacyOptionalContentMetadataMatches(
                properties = properties,
                expectedContentHash = "sha256:" + "b".repeat(64),
                expectedSizeBytes = 12L,
            )
        )

        val legacyManifestPayloadHash = "sha256:" + "c".repeat(64)
        val legacyManifestProperties = cloudFolderDriveMetadata(
            rootId = rootId,
            nodeId = CLOUD_FOLDER_MANIFEST_NODE_ID,
            revision = 4L,
            contentHash = legacyManifestPayloadHash,
            contentSizeBytes = 24L,
        )
        assertTrue(
            cloudFolderLegacyManifestMetadataMatches(
                name = cloudFolderLegacyManifestDriveName(rootId),
                properties = legacyManifestProperties,
                rootId = rootId,
                revision = 4L,
            )
        )
        assertTrue(
            cloudFolderLegacyOptionalContentMetadataMatches(
                properties = legacyManifestProperties,
                expectedContentHash = legacyManifestPayloadHash,
                expectedSizeBytes = 24L,
            )
        )
        assertTrue(
            !cloudFolderLegacyOptionalContentMetadataMatches(
                properties = legacyManifestProperties,
                expectedContentHash = legacyManifestPayloadHash,
                expectedSizeBytes = 25L,
            )
        )
    }

    @Test
    fun manifestPayloadDigestIsDerivedFromDownloadedBytesAndInvalidPayloadsFail() {
        val manifest = CloudFolderManifest(
            root = CloudFolderRoot(rootId = "root", name = "Books"),
            revision = 2L,
        )
        val payload = CloudFolderManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8)
        val decoded = decodeCloudFolderManifestPayload(payload)

        assertEquals(manifest.normalized(), decoded?.manifest)
        assertEquals(payload.size.toLong(), decoded?.contentSizeBytes)
        assertEquals(sha256CloudFolderBytes(payload), decoded?.contentHash)
        assertEquals(null, decodeCloudFolderManifestPayload("not-json".toByteArray()))
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
        assertEquals("7", metadata["cloudFolderRevision"])
        assertEquals("sha256:abc", metadata["cloudFolderContentHash"])
        // Relative paths are unbounded user data and must stay out of Drive
        // appProperties (124-byte cap); they live in the manifest instead.
        assertNull(metadata["cloudFolderRelativePath"])
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
