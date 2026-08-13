package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfCloudAnnotationSyncPlannerTest {
    private fun inventory(hasInk: Boolean, timestamp: Long) = PdfAnnotationArtifactInventory(
        hasInk = hasInk,
        inkTimestamp = timestamp,
        hasRichText = false,
        richTextTimestamp = 0L,
        hasLayout = false,
        layoutTimestamp = 0L,
        hasTextBoxes = false,
        textBoxesTimestamp = 0L,
        hasHighlights = false,
        highlightsTimestamp = 0L
    )

    @Test
    fun uploadsWhenLocalPayloadIsNewer() {
        assertEquals(
            PdfAnnotationSyncOperation.UPLOAD_LOCAL,
            PdfCloudAnnotationSyncPlanner.plan(
                PdfAnnotationSyncInput(inventory(true, 20L), true, 10L)
            )
        )
    }

    @Test
    fun downloadsWhenRemotePayloadIsNewerOrLocalIsMissing() {
        assertEquals(
            PdfAnnotationSyncOperation.DOWNLOAD_REMOTE,
            PdfCloudAnnotationSyncPlanner.plan(
                PdfAnnotationSyncInput(inventory(true, 10L), true, 20L)
            )
        )
        assertEquals(
            PdfAnnotationSyncOperation.DOWNLOAD_REMOTE,
            PdfCloudAnnotationSyncPlanner.plan(
                PdfAnnotationSyncInput(inventory(false, 0L), true, 20L)
            )
        )
    }

    @Test
    fun doesNothingWhenPayloadsAreEqualOrAbsent() {
        assertEquals(
            PdfAnnotationSyncOperation.NONE,
            PdfCloudAnnotationSyncPlanner.plan(
                PdfAnnotationSyncInput(inventory(true, 20L), true, 20L)
            )
        )
        assertEquals(
            PdfAnnotationSyncOperation.NONE,
            PdfCloudAnnotationSyncPlanner.plan(
                PdfAnnotationSyncInput(inventory(false, 0L), false, 0L)
            )
        )
    }
}
