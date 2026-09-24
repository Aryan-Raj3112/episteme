@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetAP
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetSubtype
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_APPEARANCEMODE_NORMAL
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_INK
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDFPage_GetAnnot
import com.aryan.reader.shared.pdfium.c.FPDFPage_GetAnnotCount
import com.aryan.reader.shared.pdfium.c.FPDFPage_CloseAnnot
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithLength
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertTrue

class IosPdfInkAppearanceExportTest {

    @Test
    fun inkExportWritesAppearanceStreamAndInkList() = runTest {
        exportAndAssertInkAppearance(
            points = listOf(
                PdfPagePoint(0.2f, 0.3f),
                PdfPagePoint(0.5f, 0.6f),
                PdfPagePoint(0.8f, 0.4f),
            ),
        )
    }

    @Test
    fun inkExportWritesSinglePointDotAppearance() = runTest {
        exportAndAssertInkAppearance(points = listOf(PdfPagePoint(0.4f, 0.5f)))
    }

    private fun exportAndAssertInkAppearance(points: List<PdfPagePoint>) = runTest {
        val source = temporaryPath(suffix = "source.pdf")
        val destination = temporaryPath(suffix = "exported.pdf")
        val staged = minimalPdfBytes().toNSData().writeToFile(source, atomically = true)
        assertTrue(staged, "Failed to stage source PDF")

        val snapshot = SharedPdfExportSnapshot(
            state = SharedPdfReaderState(
                annotations = listOf(
                    SharedPdfAnnotation(
                        id = "ink-1",
                        pageIndex = 0,
                        kind = PdfAnnotationKind.INK,
                        tool = PdfInkTool.PEN,
                        points = points,
                        colorArgb = 0xFFFF0000.toInt(),
                        strokeWidth = 2f,
                    ),
                ),
            ),
        )

        val exported = exportIosPdfAnnotations(
            sourcePath = source,
            destinationPath = destination,
            password = null,
            snapshot = snapshot,
        )
        assertTrue(exported, "exportIosPdfAnnotations returned false")

        val exportedData = NSData.dataWithContentsOfFile(destination) ?: error("Exported PDF missing")
        val text = NSString.create(data = exportedData, encoding = 4u /* NSISOLatin1StringEncoding */)
            ?.toString()
            ?: exportedData.toByteArray().decodeToString()
        assertTrue(text.contains("/InkList"), "Exported PDF missing /InkList")
        // Preview draws the Border rectangle unless /AP /N is present. PDFium Flate-compresses
        // the stream body, so assert the dictionary reference here and the operators via GetAP.
        assertTrue(text.contains("/AP"), "Exported PDF missing /AP appearance dictionary")
        assertTrue(Regex("/AP\\s*<<\\s*/N").containsMatchIn(text), "Exported PDF missing /AP /N stream")

        val appearance = readInkAppearance(destination)
        assertTrue(appearance.contains(" m"), "Exported AP missing moveto operator: $appearance")
        assertTrue(appearance.contains(" l"), "Exported AP missing lineto operator: $appearance")
        assertTrue(appearance.contains("S"), "Exported AP missing stroke operator: $appearance")
        assertTrue(appearance.contains("RG"), "Exported AP missing stroke color: $appearance")
        assertTrue(appearance.contains("1 J"), "Exported AP missing round line cap: $appearance")

        NSFileManager.defaultManager.removeItemAtPath(source, error = null)
        NSFileManager.defaultManager.removeItemAtPath(destination, error = null)
    }

    private fun readInkAppearance(path: String): String {
        val document = FPDF_LoadDocument(path, null) ?: error("Failed to reload exported PDF")
        try {
            val pageCount = FPDF_GetPageCount(document)
            assertTrue(pageCount > 0, "Exported PDF has no pages")
            val page = FPDF_LoadPage(document, 0) ?: error("Failed to load exported page")
            try {
                val annotCount = FPDFPage_GetAnnotCount(page)
                assertTrue(annotCount > 0, "Exported PDF has no annotations")
                for (index in 0 until annotCount) {
                    val annot = FPDFPage_GetAnnot(page, index) ?: continue
                    try {
                        if (FPDFAnnot_GetSubtype(annot) != FPDF_ANNOT_INK) continue
                        return memScoped {
                            val length = FPDFAnnot_GetAP(annot, FPDF_ANNOT_APPEARANCEMODE_NORMAL, null, 0u)
                                .toInt()
                            assertTrue(length > 2, "Ink annotation has empty /AP")
                            val utf16 = UShortArray((length + 1) / 2)
                            val written = utf16.usePinned { pinned ->
                                FPDFAnnot_GetAP(
                                    annot,
                                    FPDF_ANNOT_APPEARANCEMODE_NORMAL,
                                    pinned.addressOf(0),
                                    length.toULong(),
                                ).toInt()
                            }
                            assertTrue(written > 2, "FPDFAnnot_GetAP wrote no appearance")
                            utf16.takeWhile { it != 0.toUShort() }
                                .map { it.toInt().toChar() }
                                .joinToString("")
                        }
                    } finally {
                        FPDFPage_CloseAnnot(annot)
                    }
                }
            } finally {
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
        error("Exported PDF has no ink annotation appearance")
    }

    private fun temporaryPath(suffix: String): String =
        "${NSTemporaryDirectory().trimEnd('/')}/reader-ink-ap-${kotlin.random.Random.nextLong()}.$suffix"

    private fun minimalPdfBytes(): ByteArray {
        // Minimal single-page PDF that PDFium can load for annotation export.
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> /Contents 4 0 R >>",
            "<< /Length 44 >>\nstream\n0 0 0 RG 0 0 612 792 re S\nendstream",
        )
        val builder = StringBuilder("%PDF-1.4\n")
        val offsets = IntArray(objects.size + 1)
        offsets[0] = 0
        objects.forEachIndexed { index, body ->
            offsets[index + 1] = builder.length
            builder.append("${index + 1} 0 obj\n").append(body).append("\nendobj\n")
        }
        val xrefOffset = builder.length
        builder.append("xref\n0 ${objects.size + 1}\n")
        builder.append("0000000000 65535 f \n")
        for (i in 1..objects.size) {
            builder.append(offsets[i].toString().padStart(10, '0')).append(" 00000 n \n")
        }
        builder.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        builder.append("startxref\n").append(xrefOffset).append("\n%%EOF\n")
        val source = builder.toString()
        return ByteArray(source.length) { index -> source[index].code.toByte() }
    }

    private fun ByteArray.toNSData(): NSData {
        val data = NSMutableData.dataWithLength(size.toULong()) ?: NSMutableData()
        if (isNotEmpty()) {
            usePinned { pinned ->
                memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
            }
        }
        return data
    }

    private fun NSData.toByteArray(): ByteArray {
        val output = ByteArray(length.toInt())
        if (output.isNotEmpty()) {
            output.usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }
        return output
    }
}
