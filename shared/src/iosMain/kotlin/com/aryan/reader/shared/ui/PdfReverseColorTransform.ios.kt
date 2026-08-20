@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.PdfReverseColorRect
import com.aryan.reader.shared.pdf.invertPdfArgbIfUnprotected
import com.aryan.reader.shared.pdfium.c.FPDF_PAGEOBJ_IMAGE
import com.aryan.reader.shared.pdfium.c.FPDFPageObj_GetBounds
import com.aryan.reader.shared.pdfium.c.FPDFPageObj_GetType
import com.aryan.reader.shared.pdfium.c.FPDFPage_CountObjects
import com.aryan.reader.shared.pdfium.c.FPDFPage_GetObject
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlin.math.roundToInt

internal data class IosPdfImageRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Returns image-object bounds in the same top-left pixel space used by a full-page
 * Pdfium render. Image bounds come from PDF object geometry, not a visual heuristic.
 */
internal fun extractIosPdfImageRects(
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    pageWidth: Float,
    pageHeight: Float,
    targetWidth: Int,
    targetHeight: Int,
): List<IosPdfImageRect> {
    if (pageWidth <= 0f || pageHeight <= 0f || targetWidth <= 0 || targetHeight <= 0) return emptyList()
    val objectCount = FPDFPage_CountObjects(page)
    if (objectCount <= 0) return emptyList()
    return buildList {
        for (objectIndex in 0 until objectCount) {
            val pageObject = FPDFPage_GetObject(page, objectIndex) ?: continue
            if (FPDFPageObj_GetType(pageObject) != FPDF_PAGEOBJ_IMAGE) continue
            memScoped {
                val left = alloc<FloatVar>()
                val bottom = alloc<FloatVar>()
                val right = alloc<FloatVar>()
                val top = alloc<FloatVar>()
                if (FPDFPageObj_GetBounds(pageObject, left.ptr, bottom.ptr, right.ptr, top.ptr) == 0) return@memScoped
                val rect = IosPdfImageRect(
                    left = (left.value / pageWidth * targetWidth).roundToInt().coerceIn(0, targetWidth),
                    top = ((pageHeight - top.value) / pageHeight * targetHeight).roundToInt().coerceIn(0, targetHeight),
                    right = (right.value / pageWidth * targetWidth).roundToInt().coerceIn(0, targetWidth),
                    bottom = ((pageHeight - bottom.value) / pageHeight * targetHeight).roundToInt().coerceIn(0, targetHeight),
                )
                if (rect.right > rect.left && rect.bottom > rect.top) add(rect)
            }
        }
    }
}

/** Applies a cached Okular transform to Pdfium's BGRA byte buffer. */
internal fun transformIosPdfBitmapBytes(
    bytes: ByteArray,
    stride: Int,
    width: Int,
    height: Int,
    mode: PdfReverseColorMode,
    protectedRects: List<IosPdfImageRect> = emptyList(),
    targetWidth: Int = width,
    targetHeight: Int = height,
    targetOriginX: Int = 0,
    targetOriginY: Int = 0,
    forceRgbTransform: Boolean = false,
) {
    // RGB is normally applied by the Compose color filter. When image colors
    // must be preserved, however, the renderer supplies protected rectangles
    // and the same channel-negative is baked into the unprotected pixels.
    if (mode == PdfReverseColorMode.RGB && protectedRects.isEmpty() && !forceRgbTransform) return
    val sourceRects = protectedRects.mapNotNull { rect ->
        val left = (((rect.left - targetOriginX).toFloat() / targetWidth.coerceAtLeast(1)) * width)
            .toInt().coerceIn(0, width)
        val top = (((rect.top - targetOriginY).toFloat() / targetHeight.coerceAtLeast(1)) * height)
            .toInt().coerceIn(0, height)
        val right = (((rect.right - targetOriginX).toFloat() / targetWidth.coerceAtLeast(1)) * width)
            .toInt().coerceIn(0, width)
        val bottom = (((rect.bottom - targetOriginY).toFloat() / targetHeight.coerceAtLeast(1)) * height)
            .toInt().coerceIn(0, height)
        IosPdfImageRect(left, top, right, bottom)
            .takeIf { it.right > it.left && it.bottom > it.top }
            ?.let { PdfReverseColorRect(it.left, it.top, it.right, it.bottom) }
    }
    for (y in 0 until height) {
        val row = y * stride
        for (x in 0 until width) {
            val offset = row + x * 4
            val blue = bytes[offset].toInt() and 0xFF
            val green = bytes[offset + 1].toInt() and 0xFF
            val red = bytes[offset + 2].toInt() and 0xFF
            val alpha = bytes[offset + 3].toInt() and 0xFF
            val output = invertPdfArgbIfUnprotected(
                (alpha shl 24) or (red shl 16) or (green shl 8) or blue,
                x,
                y,
                mode,
                sourceRects,
            )
            bytes[offset] = (output and 0xFF).toByte()
            bytes[offset + 1] = ((output ushr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((output ushr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((output ushr 24) and 0xFF).toByte()
        }
    }
}
