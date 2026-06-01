package com.aryan.reader

import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.effectiveAnnotationModifiedTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudPdfAnnotationSidecarDecisionsTest {
    @Test
    fun `layout-only sidecar does not block newer remote annotations`() {
        val local = AndroidPdfCloudSidecarState(
            hasInk = false,
            inkTimestamp = 0L,
            hasRichText = false,
            richTextTimestamp = 0L,
            hasLayout = true,
            layoutTimestamp = 2_000L,
            hasTextBoxes = false,
            textBoxesTimestamp = 0L,
            hasHighlights = false,
            highlightsTimestamp = 0L
        )

        val localShouldUpload = shouldUploadLocalPdfCloudAnnotations(
            localSidecars = local,
            remoteHasAnnotations = true,
            remoteAnnotationModifiedTimestamp = 1_500L
        )

        assertFalse(localShouldUpload)
        assertTrue(
            shouldDownloadRemotePdfCloudAnnotations(
                localSidecars = local,
                localAnnotationsShouldUpload = localShouldUpload,
                remoteHasAnnotations = true,
                remoteAnnotationModifiedTimestamp = 1_500L
            )
        )
    }

    @Test
    fun `newer local ink payload uploads instead of downloading remote annotations`() {
        val local = AndroidPdfCloudSidecarState(
            hasInk = true,
            inkTimestamp = 2_000L,
            hasRichText = false,
            richTextTimestamp = 0L,
            hasLayout = true,
            layoutTimestamp = 2_500L,
            hasTextBoxes = false,
            textBoxesTimestamp = 0L,
            hasHighlights = false,
            highlightsTimestamp = 0L
        )

        val localShouldUpload = shouldUploadLocalPdfCloudAnnotations(
            localSidecars = local,
            remoteHasAnnotations = true,
            remoteAnnotationModifiedTimestamp = 1_500L
        )

        assertTrue(localShouldUpload)
        assertFalse(
            shouldDownloadRemotePdfCloudAnnotations(
                localSidecars = local,
                localAnnotationsShouldUpload = localShouldUpload,
                remoteHasAnnotations = true,
                remoteAnnotationModifiedTimestamp = 1_500L
            )
        )
    }

    @Test
    fun `newer local deletion tombstone uploads instead of downloading remote annotations`() {
        val local = AndroidPdfCloudSidecarState(
            hasInk = false,
            inkTimestamp = 0L,
            hasDeletedInk = true,
            deletedInkTimestamp = 2_000L,
            hasRichText = false,
            richTextTimestamp = 0L,
            hasLayout = false,
            layoutTimestamp = 0L,
            hasTextBoxes = false,
            textBoxesTimestamp = 0L,
            hasHighlights = false,
            highlightsTimestamp = 0L
        )

        val localShouldUpload = shouldUploadLocalPdfCloudAnnotations(
            localSidecars = local,
            remoteHasAnnotations = true,
            remoteAnnotationModifiedTimestamp = 1_500L
        )

        assertTrue(localShouldUpload)
        assertFalse(
            shouldDownloadRemotePdfCloudAnnotations(
                localSidecars = local,
                localAnnotationsShouldUpload = localShouldUpload,
                remoteHasAnnotations = true,
                remoteAnnotationModifiedTimestamp = 1_500L
            )
        )
    }

    @Test
    fun `newer remote metadata alone does not make equal annotation payload download`() {
        val local = AndroidPdfCloudSidecarState(
            hasInk = true,
            inkTimestamp = 2_000L,
            hasRichText = false,
            richTextTimestamp = 0L,
            hasLayout = false,
            layoutTimestamp = 0L,
            hasTextBoxes = false,
            textBoxesTimestamp = 0L,
            hasHighlights = false,
            highlightsTimestamp = 0L
        )

        val localShouldUpload = shouldUploadLocalPdfCloudAnnotations(
            localSidecars = local,
            remoteHasAnnotations = true,
            remoteAnnotationModifiedTimestamp = 2_000L
        )

        assertFalse(localShouldUpload)
        assertFalse(
            shouldDownloadRemotePdfCloudAnnotations(
                localSidecars = local,
                localAnnotationsShouldUpload = localShouldUpload,
                remoteHasAnnotations = true,
                remoteAnnotationModifiedTimestamp = 2_000L
            )
        )
    }

    @Test
    fun `annotation freshness does not fall back to book metadata timestamp`() {
        val remote = BookMetadata(
            bookId = "book-1",
            lastModifiedTimestamp = 5_000L,
            hasAnnotations = true
        )

        assertEquals(0L, remote.effectiveAnnotationModifiedTimestamp())
        assertEquals(3_000L, remote.effectiveAnnotationModifiedTimestamp(sidecarModifiedTimestamp = 3_000L))
    }

    @Test
    fun `empty sidecar placeholder is not syncable annotation payload`() {
        assertFalse(tempSidecar("[]").hasSyncableCloudAnnotationPayload())
        assertFalse(tempSidecar("{}").hasSyncableCloudAnnotationPayload())
        assertTrue(tempSidecar("[{\"pageIndex\":0}]").hasSyncableCloudAnnotationPayload())
    }

    private fun tempSidecar(content: String): File {
        return File.createTempFile("cloud-sidecar", ".json").apply {
            writeText(content)
            deleteOnExit()
        }
    }
}
