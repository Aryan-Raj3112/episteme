@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Create
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Destroy
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_FillRect
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetBuffer
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetStride
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_RenderPageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithLength
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Round-trip check that an exported rich-text raster stays upright relative to
 * the pre-export overlay, for unrotated and /Rotate 180 pages (the mirror bug).
 */
class IosPdfRichTextOrientationExportTest {

    @Test
    fun exportedRichTextStaysUprightOnUnrotatedPage() = runTest {
        assertRichTextMatchesSourceOrientation(pageRotation = null)
    }

    @Test
    fun exportedRichTextStaysUprightOnRotate180Page() = runTest {
        assertRichTextMatchesSourceOrientation(pageRotation = 180)
    }

    private suspend fun assertRichTextMatchesSourceOrientation(pageRotation: Int?) {
        val source = temporaryPath(suffix = "source.pdf")
        val destination = temporaryPath(suffix = "exported.pdf")
        assertTrue(
            minimalPdfBytes(pageRotation).toNSData().writeToFile(source, atomically = true),
            "Failed to stage source PDF",
        )

        try {
            val layout = SharedPdfRichPageLayout(
                pageIndex = 0,
                visibleText = AnnotatedString("Because"),
                globalStartIndex = 0,
                globalEndIndex = 7,
                pageHeightPx = 1200f,
            )
            val fontRegistry = buildIosPdfTextFontRegistry(
                annotations = emptyList(),
                richTextLayouts = listOf(layout),
            )

            val (sourceOverlay, displayWidth, displayHeight) = loadSourceOverlay(
                sourcePath = source,
                layout = layout,
                fontRegistry = fontRegistry,
            )
            assertTrue(
                sourceOverlay.bgraPixels.any { it != 0.toByte() },
                "Rich text overlay has no opaque pixels",
            )

            val snapshot = SharedPdfExportSnapshot(
                state = SharedPdfReaderState(pageCount = 1),
                richTextPageLayouts = listOf(layout),
            )
            val exported = exportIosPdfAnnotations(
                sourcePath = source,
                destinationPath = destination,
                password = null,
                snapshot = snapshot,
            )
            assertTrue(exported, "exportIosPdfAnnotations returned false")

            val scores = scoreOrientationVariants(
                destinationPath = destination,
                overlay = sourceOverlay,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
            )
            val best = scores.minByOrNull { it.value }!!.key
            val identity = scores.getValue("identity")
            val secondBest = scores.filterKeys { it != "identity" }.values.min()
            // Identity must win clearly. A slightly looser ratio than Desktop absorbs
            // Skia→PDFium resampling without accepting a mirrored winner.
            assertTrue(
                best == "identity" && identity < secondBest * 0.85,
                "Expected upright overlay after export; scores=$scores best=$best",
            )
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(source, error = null)
            NSFileManager.defaultManager.removeItemAtPath(destination, error = null)
        }
    }

    private suspend fun loadSourceOverlay(
        sourcePath: String,
        layout: SharedPdfRichPageLayout,
        fontRegistry: IosPdfTextFontRegistry,
    ): Triple<IosPdfRasterOverlay, Float, Float> = IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(sourcePath, null) ?: error("Failed to load source PDF")
        try {
            val page = FPDF_LoadPage(document, 0) ?: error("Failed to load source page")
            try {
                val width = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                val height = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                val result = buildIosPdfTextRasterOverlays(
                    pageIndex = 0,
                    pageWidth = width,
                    pageHeight = height,
                    annotations = emptyList(),
                    richTextLayouts = listOf(layout),
                    fontRegistry = fontRegistry,
                )
                assertTrue(result.overlays.size == 1, "Expected one rich text overlay, got ${result.overlays.size}")
                Triple(result.overlays.single(), width, height)
            } finally {
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }

    private fun scoreOrientationVariants(
        destinationPath: String,
        overlay: IosPdfRasterOverlay,
        displayWidth: Float,
        displayHeight: Float,
    ): Map<String, Double> {
        val document = FPDF_LoadDocument(destinationPath, null) ?: error("Failed to reload exported PDF")
        try {
            val page = FPDF_LoadPage(document, 0) ?: error("Failed to load exported page")
            try {
                val renderWidth = (displayWidth * 2f).toInt().coerceAtLeast(1)
                val renderHeight = (displayHeight * 2f).toInt().coerceAtLeast(1)
                val bitmap = FPDFBitmap_Create(renderWidth, renderHeight, 1)
                    ?: error("Failed to create render bitmap")
                try {
                    FPDFBitmap_FillRect(bitmap, 0, 0, renderWidth, renderHeight, 0xFFFFFFFFu)
                    FPDF_RenderPageBitmap(bitmap, page, 0, 0, renderWidth, renderHeight, 0, 0)
                    val buffer = FPDFBitmap_GetBuffer(bitmap) ?: error("Render buffer missing")
                    val stride = FPDFBitmap_GetStride(bitmap).coerceAtLeast(renderWidth * 4)
                    val rendered = ByteArray(stride * renderHeight)
                    rendered.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), buffer, rendered.size.convert())
                    }

                    val region = cropNormalizedRegionBgra(
                        pixels = rendered,
                        stride = stride,
                        width = renderWidth,
                        height = renderHeight,
                        left = overlay.bounds.left,
                        top = overlay.bounds.top,
                        right = overlay.bounds.right,
                        bottom = overlay.bounds.bottom,
                    )
                    return mapOf(
                        "identity" to meanAbsDiffBgra(
                            region.pixels, region.width, region.height,
                            overlay.bgraPixels, overlay.width, overlay.height,
                            flipX = false, flipY = false,
                        ),
                        "flipY" to meanAbsDiffBgra(
                            region.pixels, region.width, region.height,
                            overlay.bgraPixels, overlay.width, overlay.height,
                            flipX = false, flipY = true,
                        ),
                        "flipX" to meanAbsDiffBgra(
                            region.pixels, region.width, region.height,
                            overlay.bgraPixels, overlay.width, overlay.height,
                            flipX = true, flipY = false,
                        ),
                        "rotate180" to meanAbsDiffBgra(
                            region.pixels, region.width, region.height,
                            overlay.bgraPixels, overlay.width, overlay.height,
                            flipX = true, flipY = true,
                        ),
                    )
                } finally {
                    FPDFBitmap_Destroy(bitmap)
                }
            } finally {
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }

    private data class CroppedRegion(
        val pixels: ByteArray,
        val width: Int,
        val height: Int,
    )

    private fun cropNormalizedRegionBgra(
        pixels: ByteArray,
        stride: Int,
        width: Int,
        height: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): CroppedRegion {
        val x0 = (left.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width - 1)
        val x1 = (right.coerceIn(0f, 1f) * width).toInt().coerceIn(x0 + 1, width)
        val y0 = (top.coerceIn(0f, 1f) * height).toInt().coerceIn(0, height - 1)
        val y1 = (bottom.coerceIn(0f, 1f) * height).toInt().coerceIn(y0 + 1, height)
        val cropWidth = x1 - x0
        val cropHeight = y1 - y0
        val out = ByteArray(cropWidth * cropHeight * 4)
        for (y in 0 until cropHeight) {
            val srcOffset = (y0 + y) * stride + x0 * 4
            val dstOffset = y * cropWidth * 4
            pixels.copyInto(out, dstOffset, srcOffset, srcOffset + cropWidth * 4)
        }
        return CroppedRegion(out, cropWidth, cropHeight)
    }

    private fun meanAbsDiffBgra(
        region: ByteArray,
        regionWidth: Int,
        regionHeight: Int,
        source: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        flipX: Boolean,
        flipY: Boolean,
    ): Double {
        if (regionWidth <= 0 || regionHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return Double.MAX_VALUE
        }
        if (region.size < regionWidth * regionHeight * 4 || source.size < sourceWidth * sourceHeight * 4) {
            return Double.MAX_VALUE
        }

        var total = 0L
        var count = 0L
        for (y in 0 until regionHeight) {
            var sy = ((y + 0.5f) * sourceHeight / regionHeight).toInt().coerceIn(0, sourceHeight - 1)
            if (flipY) sy = sourceHeight - 1 - sy
            for (x in 0 until regionWidth) {
                var sx = ((x + 0.5f) * sourceWidth / regionWidth).toInt().coerceIn(0, sourceWidth - 1)
                if (flipX) sx = sourceWidth - 1 - sx
                val srcOffset = (sy * sourceWidth + sx) * 4
                val srcA = source[srcOffset + 3].toInt() and 0xFF
                if (srcA == 0) continue
                val inv = 255 - srcA
                val srcB = (source[srcOffset].toInt() and 0xFF) * srcA / 255 + inv
                val srcG = (source[srcOffset + 1].toInt() and 0xFF) * srcA / 255 + inv
                val srcR = (source[srcOffset + 2].toInt() and 0xFF) * srcA / 255 + inv
                val dstOffset = (y * regionWidth + x) * 4
                val dstB = region[dstOffset].toInt() and 0xFF
                val dstG = region[dstOffset + 1].toInt() and 0xFF
                val dstR = region[dstOffset + 2].toInt() and 0xFF
                total += abs(srcR - dstR)
                total += abs(srcG - dstG)
                total += abs(srcB - dstB)
                count += 3
            }
        }
        return if (count == 0L) Double.MAX_VALUE else total.toDouble() / count
    }

    private fun temporaryPath(suffix: String): String =
        "${NSTemporaryDirectory().trimEnd('/')}/reader-rich-orient-${kotlin.random.Random.nextLong()}.$suffix"

    private fun minimalPdfBytes(pageRotation: Int?): ByteArray {
        val rotateEntry = pageRotation?.let { " /Rotate $it" } ?: ""
        // Asymmetric marker in the top-left so a page-level /Rotate is observable
        // independent of the rich-text overlay comparison.
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]$rotateEntry /Resources << >> /Contents 4 0 R >>",
            "<< /Length 64 >>\nstream\n0 0 0 RG 0 0 612 792 re S 0 0 0 rg 0 732 60 60 re f\nendstream",
        )
        val builder = StringBuilder("%PDF-1.4\n")
        val offsets = IntArray(objects.size + 1)
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
}
