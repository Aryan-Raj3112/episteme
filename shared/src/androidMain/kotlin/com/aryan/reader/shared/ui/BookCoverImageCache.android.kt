package com.aryan.reader.shared.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

internal actual fun bookCoverFileStamp(path: String): String? {
    val file = File(path)
    if (!file.isFile) return null
    return "${file.length()}:${file.lastModified()}"
}

internal actual fun decodeBookCoverBitmap(path: String, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        // Bounds are decoded at full size into a tiny int pair, so this picks a
        // power-of-two sample that lands just above the drawn size.
        inSampleSize = coverInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    return runCatching { BitmapFactory.decodeFile(path, options)?.asImageBitmap() }.getOrNull()
}

private fun coverInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    while (width / sample > maxDimension || height / sample > maxDimension) {
        sample *= 2
    }
    return sample
}