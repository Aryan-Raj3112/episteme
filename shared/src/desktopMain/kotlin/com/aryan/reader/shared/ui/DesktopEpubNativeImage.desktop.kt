package com.aryan.reader.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.aryan.reader.paginatedreader.SemanticImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

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
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = image.altText ?: "Image from EPUB",
            modifier = modifier,
            contentScale = ContentScale.Fit,
            colorFilter = image.readerImageColorFilter()
        )
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
        runCatching {
            ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
        }.getOrNull()?.let { return it }

        return runCatching {
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }
}

private sealed class DesktopEpubImageSource(
    val key: String,
    val length: Long?,
    val lastModified: Long?
) {
    abstract fun bytes(): ByteArray?

    data class FileSource(private val file: File) : DesktopEpubImageSource(
        key = file.absolutePath,
        length = file.length(),
        lastModified = file.lastModified()
    ) {
        override fun bytes(): ByteArray? = runCatching { file.readBytes() }.getOrNull()
    }

    data class DataUriSource(private val path: String) : DesktopEpubImageSource(
        key = path,
        length = path.length.toLong(),
        lastModified = null
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
