@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetAP
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetRect
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
import com.aryan.reader.shared.pdfium.c.FS_RECTF
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithLength
import platform.Foundation.writeToFile
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun translucentHighlighterExportCarriesTheOpacityGraphicsState() = runTest {
        val destination = exportInk(
            tool = PdfInkTool.HIGHLIGHTER,
            // Highlighter palette colour (SharedPdfHighlighterPalette.DefaultAlpha) as stored.
            colorArgb = 0x8CFFEB3B.toInt(),
            points = listOf(PdfPagePoint(0.2f, 0.3f), PdfPagePoint(0.8f, 0.3f)),
        )

        // FPDFAnnot_SetColor writes /CA from the colour alpha; FPDFAnnot_SetAP only
        // attaches /Resources/ExtGState/GS while that /CA is below 1. If this pdfium build
        // stops doing that, the stroke silently renders opaque again.
        val text = readDocumentText(destination)
        assertTrue(
            "/ExtGState" in text,
            "Exported PDF has no /ExtGState resource for the translucent stroke",
        )
        assertTrue(
            Regex("/ExtGState\\s*<<\\s*/GS\\b").containsMatchIn(text),
            "Exported PDF has no /GS graphics state: $text",
        )
        // 140/255 = 0.549..., the palette alpha the reader draws the highlighter with.
        assertTrue(
            Regex("/CA\\s*0?\\.5").containsMatchIn(text),
            "Exported /GS does not carry the highlighter alpha: $text",
        )
        val appearance = readInkAppearance(destination)
        assertTrue(appearance.startsWith("q\n"), appearance)
        assertTrue(appearance.contains("/GS gs"), appearance)

        cleanup(destination)
    }

    @Test
    fun opaquePenExportAttachesAppearanceResourcesForAppleRenderers() = runTest {
        val destination = exportInk(
            tool = PdfInkTool.PEN,
            colorArgb = 0xFFFF0000.toInt(),
            points = listOf(PdfPagePoint(0.2f, 0.3f), PdfPagePoint(0.8f, 0.3f)),
        )

        // FPDFAnnot_SetAP only attaches /Resources while the annotation /CA is below 1, and
        // Apple's PDFKit paints an appearance stream without /Resources black instead of
        // honouring its colour operators (sharedPdfInkAnnotationDictColorArgb clamps the
        // opaque dict alpha to 254/255 for exactly this reason). The opaque stroke itself
        // must still stay /GS-free: it has no opacity to apply.
        val text = readDocumentText(destination)
        assertTrue(
            Regex("/ExtGState\\s*<<\\s*/GS\\b").containsMatchIn(text),
            "Exported PDF has no appearance /Resources entry: $text",
        )
        assertTrue(
            Regex("/CA\\s*0?\\.99").containsMatchIn(text),
            "Exported /CA is not clamped below 1: $text",
        )
        val appearance = readInkAppearance(destination)
        assertTrue(appearance.startsWith("q\n"), appearance)
        assertTrue(appearance.contains("1 0 0 RG"), appearance)
        assertFalse(appearance.contains("gs"), appearance)

        cleanup(destination)
    }

    /**
     * End-to-end guard for the same bug: PDFKit is what Preview, Quick Look and Books use,
     * and it must rasterize the exported red pen as red, not as a black `/InkList` fallback.
     */
    @Test
    fun opaquePenExportRasterizesRedThroughPdfKit() = runTest {
        val destination = exportInk(
            tool = PdfInkTool.PEN,
            colorArgb = 0xFFFF0000.toInt(),
            points = listOf(PdfPagePoint(0.2f, 0.3f), PdfPagePoint(0.8f, 0.3f)),
        )

        assertTrue(
            countPdfKitRedPixels(destination) > 100,
            "PDFKit did not paint the exported red pen stroke in red",
        )

        cleanup(destination)
    }

    /**
     * A rotated page is where the Android exporter used to drift: its appearance stream was
     * built with a rotation-blind `x*W,(1-y)*H` while /InkList went through the rotation-aware
     * display→page transform. The appearance must now land in the same page space as the rect
     * derived from the very same points.
     */
    @Test
    fun rotatedPageKeepsAppearanceInsideTheInkRect() = runTest {
        val destination = exportInk(
            tool = PdfInkTool.PEN,
            colorArgb = 0xFFFF0000.toInt(),
            points = listOf(PdfPagePoint(0.25f, 0.75f), PdfPagePoint(0.75f, 0.25f)),
            pageRotation = 90,
        )

        val appearance = readInkAppearance(destination)
        // 612x792 media box with /Rotate 90 -> 792x612 display; (0.25, 0.75) maps to
        // page space (459, 198) and (0.75, 0.25) to (153, 594).
        assertTrue(appearance.contains("459 198 m"), appearance)
        assertTrue(appearance.contains("153 594 l"), appearance)

        val rect = readInkRect(destination)
        val left = rect[0]
        val bottom = rect[1]
        val right = rect[2]
        val top = rect[3]
        assertTrue(459f in left..right && 198f in bottom..top, "AP point outside rect: $left,$bottom,$right,$top")
        assertTrue(153f in left..right && 594f in bottom..top, "AP point outside rect: $left,$bottom,$right,$top")

        cleanup(destination)
    }

    private fun exportAndAssertInkAppearance(points: List<PdfPagePoint>) = runTest {
        val destination = exportInk(
            tool = PdfInkTool.PEN,
            colorArgb = 0xFFFF0000.toInt(),
            points = points,
        )

        val text = readDocumentText(destination)
        assertTrue(text.contains("/InkList"), "Exported PDF missing /InkList")
        // Preview draws the Border rectangle unless /AP /N is present. PDFium Flate-compresses
        // the stream body, so assert the dictionary reference here and the operators via GetAP.
        assertTrue(text.contains("/AP"), "Exported PDF missing /AP appearance dictionary")
        assertTrue(Regex("/AP\\s*<<\\s*/N").containsMatchIn(text), "Exported PDF missing /AP /N stream")

        val appearance = readInkAppearance(destination)
        assertTrue(appearance.contains(" m"), "Exported AP missing moveto operator: $appearance")
        assertTrue(appearance.contains(" l"), "Exported AP missing lineto operator: $appearance")
        assertTrue(appearance.contains("S"), "Exported AP missing stroke operator: $appearance")
        assertTrue(appearance.contains("1 0 0 RG"), "Exported AP missing the red stroke colour: $appearance")
        assertTrue(appearance.contains("1 J"), "Exported AP missing round line cap: $appearance")

        cleanup(destination)
    }

    private suspend fun exportInk(
        tool: PdfInkTool,
        colorArgb: Int,
        points: List<PdfPagePoint>,
        pageRotation: Int = 0,
    ): String {
        val source = temporaryPath(suffix = "source.pdf")
        val destination = temporaryPath(suffix = "exported.pdf")
        val staged = minimalPdfBytes(pageRotation).toNSData().writeToFile(source, atomically = true)
        assertTrue(staged, "Failed to stage source PDF")

        val snapshot = SharedPdfExportSnapshot(
            state = SharedPdfReaderState(
                annotations = listOf(
                    SharedPdfAnnotation(
                        id = "ink-1",
                        pageIndex = 0,
                        kind = PdfAnnotationKind.INK,
                        tool = tool,
                        points = points,
                        colorArgb = colorArgb,
                        strokeWidth = 0.01f,
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
        NSFileManager.defaultManager.removeItemAtPath(source, error = null)
        return destination
    }

    private fun cleanup(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private fun readDocumentText(path: String): String {
        val data = NSData.dataWithContentsOfFile(path) ?: error("Exported PDF missing")
        return NSString.create(data = data, encoding = 4u /* NSISOLatin1StringEncoding */)
            ?.toString()
            ?: data.toByteArray().decodeToString()
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

    /** `[left, bottom, right, top]` of the first ink annotation, in page space. */
    private fun readInkRect(path: String): FloatArray {
        val document = FPDF_LoadDocument(path, null) ?: error("Failed to reload exported PDF")
        try {
            val page = FPDF_LoadPage(document, 0) ?: error("Failed to load exported page")
            try {
                val annotCount = FPDFPage_GetAnnotCount(page)
                for (index in 0 until annotCount) {
                    val annot = FPDFPage_GetAnnot(page, index) ?: continue
                    try {
                        if (FPDFAnnot_GetSubtype(annot) != FPDF_ANNOT_INK) continue
                        return memScoped {
                            val rect = alloc<FS_RECTF>()
                            assertTrue(FPDFAnnot_GetRect(annot, rect.ptr) != 0, "Ink annotation has no rect")
                            floatArrayOf(rect.left, rect.bottom, rect.right, rect.top)
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
        error("Exported PDF has no ink annotation rect")
    }

    /**
     * Red pixels PDFKit rasterizes for the decorated page; `0` means the stroke came out
     * black (or was not drawn at all).
     */
    private fun countPdfKitRedPixels(path: String): Int {
        val document = PDFDocument(NSURL.fileURLWithPath(path))
        val page = document.pageAtIndex(0uL) ?: error("Exported PDF has no PDFKit page")
        val width = 612
        val height = 792
        val pixels = ByteArray(width * height * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        pixels.usePinned { pinned ->
            val context = CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                8.toULong(),
                (width * 4).toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: error("Failed to create the PDFKit bitmap context")
            CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            page.drawWithBox(kPDFDisplayBoxMediaBox, context)
        }
        var red = 0
        for (index in pixels.indices step 4) {
            val r = pixels[index].toInt() and 0xFF
            val g = pixels[index + 1].toInt() and 0xFF
            val b = pixels[index + 2].toInt() and 0xFF
            if (r > 180 && g < 80 && b < 80) red++
        }
        return red
    }

    private fun temporaryPath(suffix: String): String =
        "${NSTemporaryDirectory().trimEnd('/')}/reader-ink-ap-${kotlin.random.Random.nextLong()}.$suffix"

    private fun minimalPdfBytes(pageRotation: Int): ByteArray {
        // Minimal single-page PDF that PDFium can load for annotation export.
        val rotate = if (pageRotation == 0) "" else " /Rotate $pageRotation"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]$rotate /Resources << >> /Contents 4 0 R >>",
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
