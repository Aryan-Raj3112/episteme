package com.aryan.reader

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.aryan.reader.shared.ExternalDocumentOpenMode
import com.aryan.reader.shared.ExternalDocumentRejectionReason
import com.aryan.reader.shared.ExternalDocumentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalDocumentIntentMapperTest {
    @Test
    fun `send multiple merges stream and clip uri carriers in stable order and deduplicates`() {
        val first = Uri.parse("content://books/first.pdf")
        val second = Uri.parse("content://books/second.epub")
        val third = Uri.parse("content://books/third.md")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("application/octet-stream")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(first, second),
            )
            .putExtra(Intent.EXTRA_TEXT, "this is not a document")
            .apply {
                clipData = ClipData.newRawUri("documents", second).apply {
                    addItem(ClipData.Item(third))
                }
            }

        val result = ExternalDocumentIntentMapper.map(intent)

        assertEquals(listOf(first.toString(), second.toString(), third.toString()), result?.uris)
        assertEquals(ExternalDocumentOpenMode.IMPORT_BATCH, result?.request?.openMode)
        assertEquals(ExternalDocumentSource.EXTRA_STREAM, result?.documents?.first()?.source)
        assertTrue(result?.request?.grantCapabilities?.read == true)
        assertTrue(result?.request?.grantCapabilities?.persistable == true)
        assertEquals(
            listOf(ExternalDocumentRejectionReason.DUPLICATE),
            result?.rejections?.map { it.reason },
        )
    }

    @Test
    fun `send text only does not become a document`() {
        val result = ExternalDocumentIntentMapper.map(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "plain text shared by another app"),
        )

        assertTrue(result?.documents.isNullOrEmpty())
        assertNull(result?.request)
        assertTrue(result?.rejections.orEmpty().isEmpty())
    }

    @Test
    fun `view data remains a single open request`() {
        val uri = Uri.parse("content://books/chapter.xhtml")
        val result = ExternalDocumentIntentMapper.map(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/xhtml+xml"),
        )

        assertEquals(listOf(uri.toString()), result?.uris)
        assertEquals(ExternalDocumentOpenMode.OPEN_SINGLE, result?.request?.openMode)
        assertEquals(ExternalDocumentSource.DATA, result?.documents?.single()?.source)
    }

    @Test
    fun `unsupported stream is rejected through shared capabilities`() {
        val result = ExternalDocumentIntentMapper.map(
            Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://books/archive.zip")),
        )

        assertNull(result?.request)
        assertEquals(
            listOf(ExternalDocumentRejectionReason.UNSUPPORTED),
            result?.rejections?.map { it.reason },
        )
    }

    @Test
    fun `route mode normalizes view and falls back to direct uri when metadata is unusable`() {
        val supportedView = Intent(Intent.ACTION_VIEW).setDataAndType(
            Uri.parse("content://books/book.pdf"),
            "application/pdf",
        )
        assertEquals(
            ExternalDocumentOpenMode.OPEN_SINGLE,
            ExternalFileOpenRouteDecider.openModeForIntent(supportedView),
        )

        val metadataFreeView = Intent(Intent.ACTION_VIEW).setDataAndType(
            Uri.parse("content://books/provider-id"),
            "application/octet-stream",
        )
        assertEquals(
            ExternalDocumentOpenMode.OPEN_SINGLE,
            ExternalFileOpenRouteDecider.openModeForIntent(metadataFreeView),
        )
    }

    @Test
    fun `route mode accepts data-less send stream and batches send multiple`() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://books/book.pdf"))
        assertEquals(
            ExternalDocumentOpenMode.OPEN_SINGLE,
            ExternalFileOpenRouteDecider.openModeForIntent(send),
        )

        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("application/pdf")
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(
                    Uri.parse("content://books/one.pdf"),
                    Uri.parse("content://books/two.pdf"),
                ),
            )
        assertEquals(
            ExternalDocumentOpenMode.IMPORT_BATCH,
            ExternalFileOpenRouteDecider.openModeForIntent(multiple),
        )
        assertNull(
            ExternalFileOpenRouteDecider.openModeForIntent(
                Intent(Intent.ACTION_SEND)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://books/archive.zip")),
            )
        )
    }
}
