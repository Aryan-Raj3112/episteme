@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfBlankPageInsertion
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
            state = SharedPdfReaderState(annotations = listOf(annotation)),
            exporter = { sourcePath, destinationPath, password, annotations ->
                exportArguments = listOf(sourcePath, destinationPath, password, annotations.single().id)
                true
            },
        )

        val ready = assertIs<IosPdfSaveCopyPreparation.Ready>(result)
        assertEquals(source, exportArguments[0])
        assertTrue(exportArguments[1] != source)
        assertEquals(listOf("secret", "ink"), exportArguments.drop(2))
        assertEquals(exportArguments[1], ready.book.path)
        NSFileManager.defaultManager.removeItemAtPath(source, error = null)
    }

    @Test
    fun unsupportedVirtualPagesDoNotInvokeTheExporter() = runTest {
        val source = temporaryPdfPath()
        NSFileManager.defaultManager.createFileAtPath(source, contents = null, attributes = null)
        var called = false

        val result = prepareIosPdfSaveCopy(
            book(source),
            password = null,
            state = SharedPdfReaderState(blankPageInsertions = listOf(SharedPdfBlankPageInsertion(0))),
            exporter = { _, _, _, _ -> called = true; true },
        )

        assertIs<IosPdfSaveCopyPreparation.Unavailable>(result)
        assertEquals(false, called)
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
