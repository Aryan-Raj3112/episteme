package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalDocumentIntakeTest {
    @Test
    fun `normalization preserves order and reports blank unsupported and duplicate candidates`() {
        val result = SharedExternalDocumentIntake.normalize(
            action = ExternalDocumentAction.SEND_MULTIPLE,
            candidates = listOf(
                ExternalDocumentCandidate(
                    uri = " content://books/first.pdf ",
                    mimeType = "application/pdf",
                    source = ExternalDocumentSource.EXTRA_STREAM,
                ),
                ExternalDocumentCandidate(
                    uri = "content://books/first.pdf",
                    mimeType = "application/pdf",
                    source = ExternalDocumentSource.CLIP_DATA,
                ),
                ExternalDocumentCandidate(
                    uri = "content://books/unsupported.zip",
                    mimeType = "application/zip",
                    source = ExternalDocumentSource.EXTRA_STREAM,
                ),
                ExternalDocumentCandidate(
                    uri = "  ",
                    source = ExternalDocumentSource.CLIP_DATA,
                ),
                ExternalDocumentCandidate(
                    uri = "content://books/second.md",
                    mimeType = "text/markdown",
                    source = ExternalDocumentSource.CLIP_DATA,
                ),
            ),
            grantCapabilities = ExternalDocumentGrantCapabilities(read = true, persistable = true),
        )

        assertEquals(
            listOf("content://books/first.pdf", "content://books/second.md"),
            result.uris,
        )
        assertEquals(ExternalDocumentOpenMode.IMPORT_BATCH, result.request?.openMode)
        assertEquals(ExternalDocumentSource.MIXED, result.request?.source)
        assertEquals(true, result.request?.grantCapabilities?.read)
        assertEquals(true, result.request?.grantCapabilities?.persistable)
        assertEquals(
            listOf(
                ExternalDocumentRejectionReason.DUPLICATE,
                ExternalDocumentRejectionReason.UNSUPPORTED,
                ExternalDocumentRejectionReason.BLANK_URI,
            ),
            result.rejections.map { it.reason },
        )
    }

    @Test
    fun `a single supported send opens while multiple accepted files batch import`() {
        val result = SharedExternalDocumentIntake.normalize(
            action = ExternalDocumentAction.SEND,
            candidates = listOf(
                ExternalDocumentCandidate(
                    uri = "content://books/chapter.xhtml",
                    mimeType = "application/xhtml+xml",
                    source = ExternalDocumentSource.DATA,
                ),
            ),
        )

        assertEquals(ExternalDocumentOpenMode.OPEN_SINGLE, result.request?.openMode)
        assertEquals(FileType.HTML, result.documents.single().fileType)

        val batch = SharedExternalDocumentIntake.normalize(
            action = ExternalDocumentAction.SEND_MULTIPLE,
            candidates = result.documents.map {
                ExternalDocumentCandidate(
                    uri = it.uri,
                    displayName = it.displayName,
                    mimeType = it.mimeType,
                    source = it.source,
                )
            } + ExternalDocumentCandidate(
                uri = "content://books/other.pdf",
                mimeType = "application/pdf",
                source = ExternalDocumentSource.EXTRA_STREAM,
            ),
        )

        assertEquals(ExternalDocumentOpenMode.IMPORT_BATCH, batch.request?.openMode)
        assertEquals(
            listOf("content://books/chapter.xhtml", "content://books/other.pdf"),
            batch.uris,
        )
    }

    @Test
    fun `supported extension can identify a document when provider reports a generic mime`() {
        val result = SharedExternalDocumentIntake.normalize(
            action = ExternalDocumentAction.VIEW,
            candidates = listOf(
                ExternalDocumentCandidate(
                    uri = "content://books/comic.cbz",
                    mimeType = "application/zip",
                    source = ExternalDocumentSource.DATA,
                ),
            ),
        )

        assertEquals(FileType.CBZ, result.documents.single().fileType)
        assertTrue(result.rejections.isEmpty())
    }

    @Test
    fun `unsupported candidates do not create a request`() {
        val result = SharedExternalDocumentIntake.normalize(
            action = ExternalDocumentAction.SEND,
            candidates = listOf(
                ExternalDocumentCandidate(
                    uri = "content://books/notes.zip",
                    mimeType = "application/zip",
                    source = ExternalDocumentSource.EXTRA_STREAM,
                ),
            ),
        )

        assertNull(result.request)
        assertEquals(listOf(ExternalDocumentRejectionReason.UNSUPPORTED), result.rejections.map { it.reason })
    }
}
