@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Surface
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.posix.memcpy

internal actual fun bookCoverFileStamp(path: String): String? {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        ?: return null
    val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: return null
    val modified = (attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970
        ?: return null
    return "$size:$modified"
}

internal actual fun decodeBookCoverBitmap(path: String, maxDimension: Int): ImageBitmap? {
    val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isEmpty()) return null
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, bytes.size.convert())
    }
    val image = SkiaImage.makeFromEncoded(bytes) ?: return null
    return image.downscaledToFit(maxDimension).toComposeImageBitmap()
}

private fun SkiaImage.downscaledToFit(maxDimension: Int): SkiaImage {
    val longestEdge = maxOf(width, height)
    if (longestEdge <= maxDimension) return this
    val scale = maxDimension.toFloat() / longestEdge.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
    surface.canvas.apply {
        scale(scale, scale)
        drawImage(this@downscaledToFit, 0f, 0f)
    }
    return surface.makeImageSnapshot()
}