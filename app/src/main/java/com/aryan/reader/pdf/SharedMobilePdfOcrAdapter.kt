package com.aryan.reader.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import com.aryan.reader.OcrEngine
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.ui.SharedAndroidPdfOcrAdapter

internal object SharedMobilePdfOcrAdapter : SharedAndroidPdfOcrAdapter {
    override suspend fun textLineBounds(bitmap: Bitmap): List<PdfPageBounds> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val result = OcrEngine.extractTextFromBitmap(bitmap, onModelDownloading = {}) ?: return emptyList()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        return result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                line.boundingBox?.toSharedNormalizedBounds(width, height)
            }
        }
    }
}

internal fun Rect.toSharedNormalizedBounds(width: Float, height: Float): PdfPageBounds? {
    return sharedNormalizedBounds(left, top, right, bottom, width, height)
}

internal fun sharedNormalizedBounds(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    width: Float,
    height: Float,
): PdfPageBounds? {
    if (width <= 0f || height <= 0f) return null
    return PdfPageBounds(
        left = (left / width).coerceIn(0f, 1f),
        top = (top / height).coerceIn(0f, 1f),
        right = (right / width).coerceIn(0f, 1f),
        bottom = (bottom / height).coerceIn(0f, 1f),
    ).takeIf { it.right > it.left && it.bottom > it.top }
}
