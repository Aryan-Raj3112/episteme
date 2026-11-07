package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal actual fun bookCoverFileStamp(path: String): String? {
    val file = File(path)
    if (!file.isFile) return null
    return "${file.length()}:${file.lastModified()}"
}

internal actual fun decodeBookCoverBitmap(path: String, maxDimension: Int): ImageBitmap? {
    val file = File(path)
    runCatching { ImageIO.read(file) }.getOrNull()
        ?.scaledToFit(maxDimension)
        ?.toComposeImageBitmap()
        ?.let { return it }
    return runCatching {
        SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }.getOrNull()
}

private fun BufferedImage.scaledToFit(maxDimension: Int): BufferedImage {
    val largestDimension = maxOf(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toDouble() / largestDimension.toDouble()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = target.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        graphics.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        )
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return target
}