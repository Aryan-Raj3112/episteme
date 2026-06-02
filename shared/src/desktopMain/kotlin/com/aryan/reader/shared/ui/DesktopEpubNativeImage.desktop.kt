package com.aryan.reader.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.aryan.reader.paginatedreader.SemanticImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthContext
import org.jetbrains.skia.Color as SkiaColor
import org.jetbrains.skia.Image as SkiaImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.roundToInt

@Composable
fun DesktopEpubNativeImage(
    image: SemanticImage,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(image.path) {
        mutableStateOf(DesktopEpubNativeImageCache.peek(image.path))
    }

    LaunchedEffect(image.path) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                DesktopEpubNativeImageCache.load(image.path)
            }
        }
    }

    val currentBitmap = bitmap
    val isDecorative = image.altText != null && image.altText.isBlank()
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = image.altText
                ?.takeIf { it.isNotBlank() }
                ?: if (isDecorative) null else "Image from EPUB",
            modifier = modifier,
            contentScale = image.readerImageContentScale(),
            alignment = desktopEpubImageContentAlignment(image.style.blockStyle.objectPosition),
            colorFilter = image.readerImageColorFilter()
        )
    } else if (isDecorative) {
        Spacer(modifier = modifier)
    } else {
        Text(
            text = image.altText?.takeIf { it.isNotBlank() } ?: image.path.substringAfterLast('/').substringAfterLast('\\'),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private object DesktopEpubNativeImageCache {
    private const val MaxEntries = 160

    private data class Entry(
        val length: Long?,
        val lastModified: Long?,
        val bitmap: ImageBitmap
    )

    private val entries = LinkedHashMap<String, Entry>(MaxEntries, 0.75f, true)

    fun peek(path: String): ImageBitmap? {
        val source = DesktopEpubImageSource.from(path) ?: return null
        return synchronized(entries) {
            val entry = entries[source.key]
            if (entry != null && entry.length == source.length && entry.lastModified == source.lastModified) {
                entry.bitmap
            } else {
                entries.remove(source.key)
                null
            }
        }
    }

    fun load(path: String): ImageBitmap? {
        peek(path)?.let { return it }
        val source = DesktopEpubImageSource.from(path) ?: return null
        val bitmap = decode(source) ?: return null
        synchronized(entries) {
            entries[source.key] = Entry(
                length = source.length,
                lastModified = source.lastModified,
                bitmap = bitmap
            )
            trimToMaxEntries()
        }
        return bitmap
    }

    private fun trimToMaxEntries() {
        while (entries.size > MaxEntries) {
            val eldestKey = entries.keys.firstOrNull() ?: return
            entries.remove(eldestKey)
        }
    }

    private fun decode(source: DesktopEpubImageSource): ImageBitmap? {
        val bytes = source.bytes() ?: return null
        if (source.isSvg) {
            decodeSvg(bytes)?.let { return it }
        }
        runCatching {
            ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
        }.getOrNull()?.let { return it }

        return runCatching {
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }

    private fun decodeSvg(bytes: ByteArray): ImageBitmap? {
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
                ?: DefaultSvgViewportPx)
                .roundToInt()
                .coerceIn(1, MaxSvgRasterDimensionPx)
            val height = (intrinsic?.y?.takeIf { it.isFinite() && it > 0f }
                ?: viewBox?.height?.takeIf { it.isFinite() && it > 0f }
                ?: DefaultSvgViewportPx)
                .roundToInt()
                .coerceIn(1, MaxSvgRasterDimensionPx)
            dom?.setContainerSize(width.toFloat(), height.toFloat())
            surface = Surface.makeRasterN32Premul(width, height)
            val canvas = surface!!.canvas
            canvas.clear(SkiaColor.TRANSPARENT)
            dom?.render(canvas)
            surface!!.makeImageSnapshot().toComposeImageBitmap()
        }.getOrNull().also {
            surface?.close()
            dom?.close()
            data?.close()
        }
    }
}

private sealed class DesktopEpubImageSource(
    val key: String,
    val length: Long?,
    val lastModified: Long?,
    val mimeType: String?
) {
    abstract fun bytes(): ByteArray?

    val isSvg: Boolean
        get() = mimeType.equals("image/svg+xml", ignoreCase = true) ||
            key.substringBefore('?').substringBefore('#').endsWith(".svg", ignoreCase = true)

    data class FileSource(private val file: File) : DesktopEpubImageSource(
        key = file.absolutePath,
        length = file.length(),
        lastModified = file.lastModified(),
        mimeType = file.extension
            .takeIf { it.equals("svg", ignoreCase = true) }
            ?.let { "image/svg+xml" }
    ) {
        override fun bytes(): ByteArray? = runCatching { file.readBytes() }.getOrNull()
    }

    data class DataUriSource(private val path: String) : DesktopEpubImageSource(
        key = path,
        length = path.length.toLong(),
        lastModified = null,
        mimeType = path.substringAfter("data:", missingDelimiterValue = "")
            .substringBefore(';')
            .substringBefore(',')
            .takeIf { it.isNotBlank() }
    ) {
        override fun bytes(): ByteArray? {
            val marker = "base64,"
            val markerIndex = path.indexOf(marker, ignoreCase = true)
            if (markerIndex < 0) return null
            val base64 = path.substring(markerIndex + marker.length)
            if (base64.isBlank()) return null
            return runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
        }
    }

    companion object {
        fun from(path: String): DesktopEpubImageSource? {
            if (path.startsWith("data:image/", ignoreCase = true)) {
                return DataUriSource(path)
            }
            val file = File(path)
            return if (file.isFile) FileSource(file) else null
        }
    }
}

private const val DefaultSvgViewportPx = 512f
private const val MaxSvgRasterDimensionPx = 4096

private fun SemanticImage.readerImageColorFilter(): ColorFilter? {
    if (style.blockStyle.filter != "invert(100%)") return null
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

private fun SemanticImage.readerImageContentScale(): ContentScale {
    return when (style.blockStyle.objectFit) {
        "cover" -> ContentScale.Crop
        "fill" -> ContentScale.FillBounds
        "contain", "scale-down" -> ContentScale.Fit
        else -> ContentScale.Fit
    }
}

internal fun desktopEpubImageContentAlignment(objectPosition: String?): Alignment {
    val tokens = objectPosition
        ?.lowercase()
        ?.split(Regex("\\s+"))
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: return Alignment.Center

    val orderedHorizontal = tokens.getOrNull(0)?.toDesktopObjectPositionHorizontal()
    val orderedVertical = tokens.getOrNull(1)?.toDesktopObjectPositionVertical()
    val horizontal = orderedHorizontal
        ?: tokens.firstNotNullOfOrNull { it.toDesktopObjectPositionHorizontalKeyword() }
        ?: DesktopObjectPositionAxis.CENTER
    val vertical = orderedVertical
        ?: tokens.firstNotNullOfOrNull { it.toDesktopObjectPositionVerticalKeyword() }
        ?: DesktopObjectPositionAxis.CENTER

    return when (vertical) {
        DesktopObjectPositionAxis.START -> when (horizontal) {
            DesktopObjectPositionAxis.START -> Alignment.TopStart
            DesktopObjectPositionAxis.END -> Alignment.TopEnd
            else -> Alignment.TopCenter
        }
        DesktopObjectPositionAxis.END -> when (horizontal) {
            DesktopObjectPositionAxis.START -> Alignment.BottomStart
            DesktopObjectPositionAxis.END -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        }
        DesktopObjectPositionAxis.CENTER -> when (horizontal) {
            DesktopObjectPositionAxis.START -> Alignment.CenterStart
            DesktopObjectPositionAxis.END -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    }
}

private enum class DesktopObjectPositionAxis {
    START,
    CENTER,
    END
}

private fun String.toDesktopObjectPositionHorizontal(): DesktopObjectPositionAxis? {
    return when (this) {
        "left", "0%" -> DesktopObjectPositionAxis.START
        "center", "50%" -> DesktopObjectPositionAxis.CENTER
        "right", "100%" -> DesktopObjectPositionAxis.END
        else -> null
    }
}

private fun String.toDesktopObjectPositionVertical(): DesktopObjectPositionAxis? {
    return when (this) {
        "top", "0%" -> DesktopObjectPositionAxis.START
        "center", "50%" -> DesktopObjectPositionAxis.CENTER
        "bottom", "100%" -> DesktopObjectPositionAxis.END
        else -> null
    }
}

private fun String.toDesktopObjectPositionHorizontalKeyword(): DesktopObjectPositionAxis? {
    return when (this) {
        "left" -> DesktopObjectPositionAxis.START
        "center" -> DesktopObjectPositionAxis.CENTER
        "right" -> DesktopObjectPositionAxis.END
        else -> null
    }
}

private fun String.toDesktopObjectPositionVerticalKeyword(): DesktopObjectPositionAxis? {
    return when (this) {
        "top" -> DesktopObjectPositionAxis.START
        "center" -> DesktopObjectPositionAxis.CENTER
        "bottom" -> DesktopObjectPositionAxis.END
        else -> null
    }
}
