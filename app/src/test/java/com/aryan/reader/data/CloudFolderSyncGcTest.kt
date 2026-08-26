package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudFolderSyncGcTest {
    @Test
    fun planKeepsReferencedRecentAndUnknownTimestampObjects() {
        val candidates = planCloudFolderGarbageCollection(
            objects = listOf(
                objectRef("old-b", rootId = "root-b", nodeId = "node", revision = 2L, modifiedAt = 100L),
                objectRef("old-a", rootId = "root-a", nodeId = "node", revision = 1L, modifiedAt = 100L),
                objectRef("referenced", rootId = "root-a", nodeId = "node", revision = 0L, modifiedAt = 100L),
                objectRef("recent", rootId = "root-a", nodeId = "node", revision = 3L, modifiedAt = 950L),
                objectRef("unknown-time", rootId = "root-a", nodeId = "node", revision = 4L, modifiedAt = 0L),
            ),
            referencedDriveFileIds = setOf("referenced"),
            nowMillis = 1_000L,
            retentionMillis = 100L,
        )

        // Ordering is stable even when Drive returns pages in a different order.
        assertEquals(listOf("old-a", "old-b"), candidates.map { it.objectRef.driveFileId })
    }

    @Test
    fun planRejectsInvalidClockInputs() {
        val objectRef = objectRef("old", modifiedAt = 1L)
        assertThrows(IllegalArgumentException::class.java) {
            planCloudFolderGarbageCollection(emptyList(), emptySet(), -1L, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            planCloudFolderGarbageCollection(listOf(objectRef), emptySet(), 1L, -1L)
        }
    }

    private fun objectRef(
        driveFileId: String,
        rootId: String = "root",
        nodeId: String = "node",
        revision: Long = 1L,
        modifiedAt: Long,
    ) = CloudFolderDriveObjectRef(
        driveFileId = driveFileId,
        name = "cloud-folder-object",
        rootId = rootId,
        nodeId = nodeId,
        revision = revision,
        modifiedTimeMillis = modifiedAt,
        properties = emptyMap(),
    )
}
