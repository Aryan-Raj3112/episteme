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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.aryan.reader.paginatedreader.SemanticImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun SharedMobileEpubNativeImage(
    image: SemanticImage,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(image.path) {
        mutableStateOf(SharedMobileEpubNativeImageCache.peek(image.path))
    }

    LaunchedEffect(image.path) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.Default) {
                SharedMobileEpubNativeImageCache.load(image.path)
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
            contentScale = image.sharedNativeImageContentScale(),
            colorFilter = image.sharedNativeImageColorFilter()
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

@OptIn(ExperimentalEncodingApi::class)
private object SharedMobileEpubNativeImageCache {
    private const val MaxEntries = 160

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, ImageBitmap>()

    fun peek(path: String): ImageBitmap? {
        val source = SharedMobileEpubNativeImageSource.from(path) ?: return null
        return runBlocking {
            mutex.withLock {
                val bitmap = entries.remove(source.key)
                if (bitmap != null) {
                    entries[source.key] = bitmap
                }
                bitmap
            }
        }
    }

    suspend fun load(path: String): ImageBitmap? {
        peek(path)?.let { return it }
        val source = SharedMobileEpubNativeImageSource.from(path) ?: return null
        val bytes = source.bytes() ?: return null
        val bitmap = decodeSharedMobileEpubImage(bytes, source.isSvg) ?: return null
        runBlocking {
            mutex.withLock {
                entries[source.key] = bitmap
                while (entries.size > MaxEntries) {
                    val eldestKey = entries.keys.firstOrNull() ?: break
                    entries.remove(eldestKey)
                }
            }
        }
        return bitmap
    }

}

internal expect suspend fun decodeSharedMobileEpubImage(
    bytes: ByteArray,
    isSvg: Boolean
): ImageBitmap?

private class SharedMobileEpubNativeImageSource(
    val key: String,
    val mimeType: String?,
    private val payload: ByteArray?
) {
    fun bytes(): ByteArray? = payload

    val isSvg: Boolean
        get() = mimeType.equals("image/svg+xml", ignoreCase = true) ||
            key.substringBefore('?').substringBefore('#').endsWith(".svg", ignoreCase = true)

    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun from(path: String): SharedMobileEpubNativeImageSource? {
            if (!path.startsWith("data:image/", ignoreCase = true)) return null
            val comma = path.indexOf(',')
            if (comma <= 5) return null
            val metadata = path.substring(5, comma)
            val payload = path.substring(comma + 1)
            if (!metadata.contains(";base64", ignoreCase = true)) return null
            val bytes = runCatching { Base64.Default.decode(payload) }.getOrNull() ?: return null
            return SharedMobileEpubNativeImageSource(
                key = path,
                mimeType = metadata.substringBefore(';'),
                payload = bytes
            )
        }
    }
}
