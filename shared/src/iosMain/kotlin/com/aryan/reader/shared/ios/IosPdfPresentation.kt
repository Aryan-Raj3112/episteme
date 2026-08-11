@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Create
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Destroy
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_FillRect
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetBuffer
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetStride
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetMetaText
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_RenderPageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.posix.memcpy
import kotlin.math.roundToInt

internal fun extractIosPdfPresentation(book: BookItem): IosBookPresentation {
    val path = book.path.resolveIosEpubSourcePath() ?: return IosBookPresentation()
    IosPdfiumRuntime.ensureInitialized()
    val document = FPDF_LoadDocument(path, null) ?: return IosBookPresentation()
    try {
        fun readMetadata(tag: String): String? {
            val byteCount = FPDF_GetMetaText(document, tag, null, 0u).toInt()
            if (byteCount <= 2) return null
            val utf16 = UShortArray((byteCount + 1) / 2)
            val written = utf16.usePinned { pinned ->
                FPDF_GetMetaText(document, tag, pinned.addressOf(0), byteCount.toULong()).toInt()
            }
            return utf16.takeIf { written > 2 }
                ?.takeWhile { it != 0.toUShort() }
                ?.map { it.toInt().toChar() }
                ?.joinToString("")
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }
        val page = FPDF_LoadPage(document, 0) ?: return IosBookPresentation()
        try {
            val pageWidth = FPDF_GetPageWidthF(page).toDouble().coerceAtLeast(1.0)
            val pageHeight = FPDF_GetPageHeightF(page).toDouble().coerceAtLeast(1.0)
            val width = 480
            val height = (width * pageHeight / pageWidth).roundToInt().coerceIn(1, 960)
            val bitmap = FPDFBitmap_Create(width, height, 1) ?: return IosBookPresentation()
            try {
                FPDFBitmap_FillRect(bitmap, 0, 0, width, height, 0xFFFFFFFFu)
                FPDF_RenderPageBitmap(bitmap, page, 0, 0, width, height, 0, 0)
                val buffer = FPDFBitmap_GetBuffer(bitmap) ?: return IosBookPresentation()
                val stride = FPDFBitmap_GetStride(bitmap).coerceAtLeast(width * 4)
                val pixels = ByteArray(stride * height)
                pixels.usePinned { pinned -> memcpy(pinned.addressOf(0), buffer, pixels.size.convert()) }
                val encoded = Image.makeRaster(
                    ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), pixels, stride,
                ).encodeToData()
                return IosBookPresentation(readMetadata("Title"), readMetadata("Author"), encoded?.bytes)
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
