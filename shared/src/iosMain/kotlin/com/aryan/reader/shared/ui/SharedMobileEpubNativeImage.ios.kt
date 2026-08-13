package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthContext
import kotlin.math.roundToInt

internal actual suspend fun decodeSharedMobileEpubImage(
    bytes: ByteArray,
    isSvg: Boolean
): ImageBitmap? {
    if (isSvg) decodeIosSvg(bytes)?.let { return it }
    return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

private fun decodeIosSvg(bytes: ByteArray): ImageBitmap? {
    var data: Data? = null
    var dom: SVGDOM? = null
    var surface: Surface? = null
    return runCatching {
        data = Data.makeFromBytes(bytes)
        dom = SVGDOM(data!!)
        val root = dom?.root
        val viewBox = root?.viewBox
        val intrinsic = root?.getIntrinsicSize(SVGLengthContext(DefaultSvgViewportPx, DefaultSvgViewportPx))
        val width = (intrinsic?.x?.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.width?.takeIf { it.isFinite() && it > 0f }
            ?: DefaultSvgViewportPx).roundToInt().coerceIn(1, MaxSvgRasterDimensionPx)
        val height = (intrinsic?.y?.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.height?.takeIf { it.isFinite() && it > 0f }
            ?: DefaultSvgViewportPx).roundToInt().coerceIn(1, MaxSvgRasterDimensionPx)
        dom?.setContainerSize(width.toFloat(), height.toFloat())
        surface = Surface.makeRasterN32Premul(width, height)
        surface!!.canvas.clear(Color.TRANSPARENT)
        dom?.render(surface!!.canvas)
        surface!!.makeImageSnapshot().toComposeImageBitmap()
    }.getOrNull().also {
        surface?.close()
        dom?.close()
        data?.close()
    }
}

private const val DefaultSvgViewportPx = 512f
private const val MaxSvgRasterDimensionPx = 4096
