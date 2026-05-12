package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal object DesktopBookCoverImageCache {
    private const val MaxEntries = 96
    private const val MaxCoverDimensionPx = 512

    private data class Entry(
        val length: Long,
        val lastModified: Long,
        val bitmap: ImageBitmap
    )

    private val entries = LinkedHashMap<String, Entry>(MaxEntries, 0.75f, true)

    fun peek(path: String): ImageBitmap? {
        val file = File(path)
        if (!file.isFile) return null
        val key = file.absolutePath
        val length = file.length()
        val lastModified = file.lastModified()
        return synchronized(entries) {
            val entry = entries[key]
            if (entry != null && entry.length == length && entry.lastModified == lastModified) {
                entry.bitmap
            } else {
                entries.remove(key)
                null
            }
        }
    }

    fun load(path: String): ImageBitmap? {
        peek(path)?.let { return it }
        val file = File(path)
        if (!file.isFile) return null
        val bitmap = decodeCover(file) ?: return null
        val entry = Entry(
            length = file.length(),
            lastModified = file.lastModified(),
            bitmap = bitmap
        )
        synchronized(entries) {
            entries[file.absolutePath] = entry
            trimToMaxEntries()
        }
        return bitmap
    }

    fun clearForTests() {
        synchronized(entries) {
            entries.clear()
        }
    }

    private fun trimToMaxEntries() {
        while (entries.size > MaxEntries) {
            val eldestKey = entries.keys.firstOrNull() ?: return
            entries.remove(eldestKey)
        }
    }

    private fun decodeCover(file: File): ImageBitmap? {
        runCatching { ImageIO.read(file) }.getOrNull()
            ?.scaledToFit(MaxCoverDimensionPx)
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
}
