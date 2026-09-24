package com.aryan.reader.desktop

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.shared.pdf.SharedPdfRichPageLayout
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopPdfRasterOrientationTest {

    @Test
    fun `exported rich text overlay keeps upright orientation on unrotated page`() {
        assertRichTextMatchesSourceOrientation(pageRotation = null, requireUpright = true)
    }

    @Test
    fun `exported rich text overlay keeps upright orientation on rotate 180 page`() {
        // Rich-text bounds are display-space and the raster is drawn upright. Export
        // places the image in mediabox space with a positive matrix; when PDFium
        // re-renders this page (which still has /Rotate 180), the overlay must still
        // match the pre-export upright raster relative to the same render transform
        // used for the original page content.
        assertRichTextMatchesSourceOrientation(pageRotation = 180, requireUpright = true)
    }

    private fun assertRichTextMatchesSourceOrientation(pageRotation: Int?, requireUpright: Boolean) {
        withTempDir { dir ->
            val source = File(dir, "source.pdf")
            source.writeBytes(minimalPdfBytes(pageRotation = pageRotation))
            val destination = File(dir, "exported.pdf")

            val layout = SharedPdfRichPageLayout(
                pageIndex = 0,
                visibleText = AnnotatedString("Because"),
                globalStartIndex = 0,
                globalEndIndex = 7,
                pageHeightPx = 1200f,
            )

            val sourceOverlays: List<DesktopPdfRasterOverlay>
            val sourceDocument = DesktopPdfium.load(source)
            try {
                sourceOverlays = buildDesktopPdfRasterOverlays(
                    annotations = emptyList(),
                    richTextPageLayouts = listOf(layout),
                    pageSizes = sourceDocument.pageSizes,
                )
                assertTrue(sourceOverlays.size == 1, "Expected one rich text overlay, got ${sourceOverlays.size}")
                assertTrue(sourceOverlays.single().pixels.isNotEmpty(), "Rich text overlay has no opaque pixels")

                DesktopPdfium.exportAnnotatedPdf(
                    document = sourceDocument,
                    destination = destination,
                    annotations = emptyList(),
                    richTextPageLayouts = listOf(layout),
                )
            } finally {
                sourceDocument.close()
            }
            assertTrue(destination.isFile && destination.length() > 0, "Export did not write a PDF")

            val overlay = sourceOverlays.single()
            val exportedDocument = DesktopPdfium.load(destination)
            try {
                val rendered = DesktopPdfium.renderPageBufferedImage(
                    document = exportedDocument,
                    pageIndex = 0,
                    scale = 2f,
                    renderAnnotations = true,
                )
                val (region, regionWidth, regionHeight) = cropNormalizedRegion(
                    image = rendered,
                    left = overlay.left,
                    top = overlay.top,
                    right = overlay.right,
                    bottom = overlay.bottom,
                )

                val scores = mapOf(
                    "identity" to meanAbsDiff(
                        region, regionWidth, regionHeight, overlay.pixels,
                        overlay.width, overlay.height, flipX = false, flipY = false,
                    ),
                    "flipY" to meanAbsDiff(
                        region, regionWidth, regionHeight, overlay.pixels,
                        overlay.width, overlay.height, flipX = false, flipY = true,
                    ),
                    "flipX" to meanAbsDiff(
                        region, regionWidth, regionHeight, overlay.pixels,
                        overlay.width, overlay.height, flipX = true, flipY = false,
                    ),
                    "rotate180" to meanAbsDiff(
                        region, regionWidth, regionHeight, overlay.pixels,
                        overlay.width, overlay.height, flipX = true, flipY = true,
                    ),
                )
                val best = scores.minByOrNull { it.value }!!.key
                val identity = scores.getValue("identity")
                val worst = scores.values.max()
                // Require identity to win clearly, not by a rounding error.
                assertTrue(
                    best == "identity" && identity < worst * 0.5,
                    "Expected upright overlay after export; scores=$scores best=$best",
                )
            } finally {
                exportedDocument.close()
            }
        }
    }

    private data class CroppedRegion(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    private fun cropNormalizedRegion(
        image: BufferedImage,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): CroppedRegion {
        val x0 = (left.coerceIn(0f, 1f) * image.width).toInt().coerceIn(0, image.width - 1)
        val x1 = (right.coerceIn(0f, 1f) * image.width).toInt().coerceIn(x0 + 1, image.width)
        val y0 = (top.coerceIn(0f, 1f) * image.height).toInt().coerceIn(0, image.height - 1)
        val y1 = (bottom.coerceIn(0f, 1f) * image.height).toInt().coerceIn(y0 + 1, image.height)
        val width = x1 - x0
        val height = y1 - y0
        val out = IntArray(width * height)
        image.getRGB(x0, y0, width, height, out, 0, width)
        return CroppedRegion(out, width, height)
    }

    /**
     * Compares source overlay pixels against the re-rendered region after
     * compositing the source over white (the page background). Only opaque
     * source texels are scored so transparent crop margins do not drown the
     * orientation signal. [flipX]/[flipY] reverse the source before sampling.
     */
    private fun meanAbsDiff(
        region: IntArray,
        regionWidth: Int,
        regionHeight: Int,
        source: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        flipX: Boolean,
        flipY: Boolean,
    ): Double {
        if (regionWidth <= 0 || regionHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return Double.MAX_VALUE
        }
        if (region.size < regionWidth * regionHeight || source.size < sourceWidth * sourceHeight) {
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
                val src = source[sy * sourceWidth + sx]
                val srcA = ((src ushr 24) and 0xFF)
                if (srcA == 0) continue
                // Composite source over white so RGB matches the rendered page background.
                val inv = 255 - srcA
                val srcR = ((src ushr 16) and 0xFF) * srcA / 255 + inv
                val srcG = ((src ushr 8) and 0xFF) * srcA / 255 + inv
                val srcB = (src and 0xFF) * srcA / 255 + inv
                val dst = region[y * regionWidth + x]
                val dstR = (dst ushr 16) and 0xFF
                val dstG = (dst ushr 8) and 0xFF
                val dstB = dst and 0xFF
                total += abs(srcR - dstR)
                total += abs(srcG - dstG)
                total += abs(srcB - dstB)
                count += 3
            }
        }
        return if (count == 0L) Double.MAX_VALUE else total.toDouble() / count
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("reader-desktop-raster-orientation").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

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
}
