package com.aryan.reader.shared

import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfCloudSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfCloudSidecarSnapshot
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfCloudSnapshotMergeTest {
    @Test
    fun `library snapshot carries and merges pdf sidecars`() {
        fun annotation(id: String) = SharedPdfAnnotation(
            id = id,
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            bounds = PdfPageBounds(0.1f, 0.1f, 0.5f, 0.2f),
            text = id,
            colorArgb = 0x8CFFEB3B.toInt(),
        )

        val localState = SharedPdfReaderState.initial(1).copy(
            themeId = "light",
            annotations = listOf(annotation("local")),
        )
        val remoteState = SharedPdfReaderState.initial(1).copy(
            themeId = "dark",
            annotations = listOf(annotation("remote")),
        )
        val localSidecar = SharedPdfCloudSidecarSnapshot(
            bookId = "pdf-1",
            timestamp = 100L,
            data = SharedPdfCloudSidecarCodec.encode("pdf-1", localState, modifiedTimestamp = 100L),
        )
        val remoteSidecar = SharedPdfCloudSidecarSnapshot(
            bookId = "pdf-1",
            timestamp = 200L,
            data = SharedPdfCloudSidecarCodec.encode("pdf-1", remoteState, modifiedTimestamp = 200L),
        )

        val localSnapshot = SharedLibrarySnapshotJson.decodeOrEmpty(
            SharedLibrarySnapshotJson.encode(SharedLibrarySnapshot(pdfSidecars = listOf(localSidecar)))
        )
        val remoteSnapshot = SharedLibrarySnapshotJson.decodeOrEmpty(
            SharedLibrarySnapshotJson.encode(SharedLibrarySnapshot(pdfSidecars = listOf(remoteSidecar)))
        )
        val merged = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = localSnapshot,
            remote = remoteSnapshot,
            downloadedBookPaths = emptyMap(),
        )

        assertEquals(1, merged.pdfSidecars.size)
        assertEquals(200L, merged.pdfSidecars.single().timestamp)
        val payload = SharedPdfCloudSidecarCodec.decode(merged.pdfSidecars.single().data)
        assertEquals("dark", payload?.readerState?.themeId)
        assertEquals(listOf("local", "remote"), payload?.annotations?.map { it.id })
    }
}
