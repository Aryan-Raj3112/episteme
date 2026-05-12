package com.aryan.reader.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun DesktopOpdsCoverImage(
    entry: OpdsEntry,
    catalog: OpdsCatalog?,
    modifier: Modifier = Modifier
) {
    val coverUrl = entry.coverUrl?.takeIf { it.isNotBlank() }
    val cacheKey = remember(coverUrl, catalog?.id, catalog?.username) {
        coverUrl?.let { DesktopOpdsCoverImageCache.cacheKey(it, catalog) }
    }
    var bitmap by remember(cacheKey) { mutableStateOf(cacheKey?.let { DesktopOpdsCoverImageCache.peek(it) }) }

    LaunchedEffect(cacheKey) {
        bitmap = if (coverUrl == null || cacheKey == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                DesktopOpdsCoverImageCache.load(cacheKey, coverUrl, catalog)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val imageBitmap = bitmap
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                text = entry.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private object DesktopOpdsCoverImageCache {
    private const val MaxEntries = 160

    private val cache = object : LinkedHashMap<String, ImageBitmap>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MaxEntries
        }
    }

    fun cacheKey(url: String, catalog: OpdsCatalog?): String {
        return "${catalog?.id.orEmpty()}|${catalog?.username.orEmpty()}|$url"
    }

    fun peek(cacheKey: String): ImageBitmap? {
        return synchronized(cache) { cache[cacheKey] }
    }

    fun load(cacheKey: String, url: String, catalog: OpdsCatalog?): ImageBitmap? {
        peek(cacheKey)?.let { return it }
        val bitmap = runCatching {
            DesktopOpdsHttp.fetchBytes(url, catalog).toImageBitmap()
        }.getOrNull() ?: return null

        synchronized(cache) {
            cache[cacheKey] = bitmap
        }
        return bitmap
    }

    private fun ByteArray.toImageBitmap(): ImageBitmap? {
        return runCatching { SkiaImage.makeFromEncoded(this).toComposeImageBitmap() }.getOrNull()
    }
}
