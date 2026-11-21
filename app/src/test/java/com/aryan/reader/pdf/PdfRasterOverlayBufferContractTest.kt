package com.aryan.reader.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Android export bridge must hand `FPDFBitmap_CreateEx` the overlay buffer exactly as
 * `Bitmap.getPixels()` produced it: row 0 is the image top.
 *
 * `sharedPdfRasterImageMatrix` documents that contract and, for an unrotated page, returns
 * `d = +height`, which puts the image's `y = 1` edge at the top of the placed rect. iOS and
 * Desktop feed the buffer unchanged, so any row reversal in the JNI copy mirrors the baked
 * overlay vertically - that is the flipped text box text reported in #589, where a copy of
 * `Bitmap.toRasterOverlay`'s pixels came out upside down inside the same rect.
 *
 * A JVM test cannot drive the native export (and the export path crashes in pdfium's own
 * path-based loader on the emulator images available here), so this pins the Android-only
 * delta while `DesktopPdfRasterOrientationTest` keeps the real orientation round trip for the
 * shared matrix and buffer convention.
 */
class PdfRasterOverlayBufferContractTest {

    @Test
    fun `raster embed keeps Bitmap row order`() {
        val bridge = readFile("src/main/cpp/pdfium_bridge.cpp")

        assertTrue(
            "raster bitmap creation not found in pdfium_bridge.cpp",
            bridge.contains("bitmap_create_ex_func("),
        )
        // A row reversal shows up as indexing the buffer from the far end
        // (`imageHeight - 1 - y`) inside the embed loop.
        assertFalse(
            "raster embed reverses pixel rows, which mirrors every overlay vertically (#589)",
            Regex("""imageHeight\s*-\s*1\s*-\s*y""").containsMatchIn(bridge),
        )
        assertFalse(
            "raster embed claims a row-order compensation that PDFium does not need",
            bridge.contains("embed.flipY"),
        )
    }

    private fun readFile(relativePath: String): String {
        return sequenceOf(
            File(relativePath),
            File("app/$relativePath"),
        ).firstOrNull(File::isFile)?.readText()
            ?: error("Unable to locate $relativePath")
    }
}
