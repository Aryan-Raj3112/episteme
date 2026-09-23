@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfBlankPageInsertion
import com.aryan.reader.shared.pdf.SharedPdfExportSnapshot
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosPdfSaveCopyTest {
    @Test
    fun annotatedCopyUsesTheNativeExporterAndNeverOverwritesTheSource() = runTest {
        val source = temporaryPdfPath()
        NSFileManager.defaultManager.createFileAtPath(source, contents = null, attributes = null)
        var exportArguments: List<String?> = emptyList()
        val annotation = SharedPdfAnnotation(
            id = "ink",
            pageIndex = 0,
            kind = PdfAnnotationKind.INK,
            points = listOf(PdfPagePoint(0.1f, 0.1f), PdfPagePoint(0.2f, 0.2f)),
            colorArgb = 0xFF000000.toInt(),
        )

        val result = prepareIosPdfSaveCopy(
            book = book(source),
            password = "secret",
            snapshot = SharedPdfExportSnapshot(SharedPdfReaderState(annotations = listOf(annotation))),
            exporter = { sourcePath, destinationPath, password, snapshot ->
                exportArguments = listOf(sourcePath, destinationPath, password, snapshot.state.annotations.single().id)
                true
            },
        )

        val ready = assertIs<IosPdfSaveCopyPreparation.Ready>(result)
        assertEquals(source, exportArguments[0])
        assertTrue(exportArguments[1] != source)
        assertEquals(listOf("secret", "ink"), exportArguments.drop(2))
        assertEquals(exportArguments[1], ready.book.path)
        // Android benchmark suggested filename, staged flat in tmp (nested share-*/ dirs break
        // UIActivityViewController item loading: "error fetching item for URL").
        assertTrue(!ready.book.path!!.contains("/shared_files/"))
        assertTrue(ready.book.path!!.endsWith(".pdf"))
        assertTrue(ready.book.path!!.substringAfterLast('/').contains("_annotated_"))
        assertTrue(ready.book.path!!.substringAfterLast('/').startsWith("Book_annotated_"))
        NSFileManager.defaultManager.removeItemAtPath(source, error = null)
        ready.book.path?.let {
            NSFileManager.defaultManager.removeItemAtPath(it, error = null)
        }
    }

    @Test
    fun originalCopyIsStagedWithASuggestedFilename() = runTest {
        val source = temporaryPdfPath()
        NSFileManager.defaultManager.createFileAtPath(source, contents = null, attributes = null)

        val result = prepareIosPdfSaveCopy(
            book = book(source),
            password = null,
            snapshot = SharedPdfExportSnapshot(SharedPdfReaderState()),
            exporter = { _, _, _, _ -> error("Exporter must not run for ORIGINAL") },
        )

        val ready = assertIs<IosPdfSaveCopyPreparation.Ready>(result)
        assertTrue(ready.book.path != source)
        assertTrue(!ready.book.path!!.contains("/shared_files/"))
        assertTrue(ready.book.path!!.substringAfterLast('/').startsWith("Book_"))
        assertTrue(!ready.book.path!!.substringAfterLast('/').contains("_annotated_"))
        ready.book.path?.let {
            NSFileManager.defaultManager.removeItemAtPath(it, error = null)
        }
        NSFileManager.defaultManager.removeItemAtPath(source, error = null)
    }

    @Test
    fun preparationNeverThrowsAndSurfacesUnavailable() = runTest {
        val result = prepareIosPdfSaveCopy(
            book(source = "/nonexistent/missing.pdf"),
            password = null,
            snapshot = SharedPdfExportSnapshot(SharedPdfReaderState()),
            exporter = { _, _, _, _ -> error("Exporter must not run without a source") },
        )

        assertIs<IosPdfSaveCopyPreparation.Unavailable>(result)
    }

    @Test
    fun virtualPagesUseTheNativeExporter() = runTest {
        val source = temporaryPdfPath()
        NSFileManager.defaultManager.createFileAtPath(source, contents = null, attributes = null)
        var called = false

        val result = prepareIosPdfSaveCopy(
            book(source),
            password = null,
            snapshot = SharedPdfExportSnapshot(SharedPdfReaderState(blankPageInsertions = listOf(SharedPdfBlankPageInsertion(0)))),
            exporter = { _, _, _, _ -> called = true; true },
        )

        val ready = assertIs<IosPdfSaveCopyPreparation.Ready>(result)
        assertEquals(true, called)
        assertTrue(!ready.book.path!!.contains("/shared_files/"))
        ready.book.path?.let {
            NSFileManager.defaultManager.removeItemAtPath(it, error = null)
        }
        NSFileManager.defaultManager.removeItemAtPath(source, error = null)
    }

    @Test
    fun forceOriginalSkipsAnnotatedExportEvenWhenTheSnapshotHasContent() = runTest {
        val source = temporaryPdfPath()
        NSFileManager.defaultManager.createFileAtPath(source, contents = null, attributes = null)
        val annotation = SharedPdfAnnotation(
            id = "ink",
            pageIndex = 0,
            kind = PdfAnnotationKind.INK,
            points = listOf(PdfPagePoint(0.1f, 0.1f), PdfPagePoint(0.2f, 0.2f)),
            colorArgb = 0xFF000000.toInt(),
        )

        val result = prepareIosPdfSaveCopy(
            book = book(source),
            password = null,
            snapshot = SharedPdfExportSnapshot(SharedPdfReaderState(annotations = listOf(annotation))),
            forceOriginal = true,
            exporter = { _, _, _, _ -> error("Exporter must not run when forceOriginal is set") },
        )

        val ready = assertIs<IosPdfSaveCopyPreparation.Ready>(result)
        assertTrue(ready.book.path != source)
        assertTrue(ready.book.path!!.substringAfterLast('/').startsWith("Book_"))
        assertTrue(!ready.book.path!!.substringAfterLast('/').contains("_annotated_"))
        ready.book.path?.let {
            NSFileManager.defaultManager.removeItemAtPath(it, error = null)
        }
        NSFileManager.defaultManager.removeItemAtPath(source, error = null)
    }

    private fun book(path: String) = BookItem(
        id = "pdf",
        path = path,
        type = FileType.PDF,
        displayName = "Book.pdf",
        timestamp = 1L,
    )

    private fun temporaryPdfPath(): String =
        "${NSTemporaryDirectory().trimEnd('/')}/reader-export-test-${kotlin.random.Random.nextLong()}.pdf"
}
