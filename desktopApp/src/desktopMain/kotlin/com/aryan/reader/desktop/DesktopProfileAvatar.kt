package com.aryan.reader.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.shape.CircleShape
import com.aryan.reader.shared.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun DesktopProfileAvatar(
    user: UserData,
    modifier: Modifier = Modifier
) {
    val photoUrl = user.photoUrl?.takeIf { it.isNotBlank() }
    var bitmap by remember(photoUrl) { mutableStateOf(photoUrl?.let(DesktopProfileAvatarCache::peek)) }

    LaunchedEffect(photoUrl) {
        bitmap = if (photoUrl == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                DesktopProfileAvatarCache.load(photoUrl)
            }
        }
    }

    val imageBitmap = bitmap
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        DesktopProfileAvatarFallback(user = user, modifier = modifier)
    }
}

@Composable
private fun DesktopProfileAvatarFallback(
    user: UserData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            val initial = (user.displayName ?: user.email)
                ?.trim()
                ?.firstOrNull()
                ?.uppercase()
            if (initial != null) {
                Text(initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.AccountCircle, contentDescription = null)
            }
        }
    }
}

private object DesktopProfileAvatarCache {
    private const val MaxEntries = 24

    private val cache = object : LinkedHashMap<String, ImageBitmap>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MaxEntries
        }
    }

    fun peek(url: String): ImageBitmap? {
        return synchronized(cache) { cache[url] }
    }

    fun load(url: String): ImageBitmap? {
        peek(url)?.let { return it }
        val bitmap = runCatching {
            DesktopOpdsHttp.fetchBytes(url, catalog = null).toImageBitmap()
        }.getOrNull() ?: return null

        synchronized(cache) {
            cache[url] = bitmap
        }
        return bitmap
    }

    private fun ByteArray.toImageBitmap(): ImageBitmap? {
        return runCatching { SkiaImage.makeFromEncoded(this).toComposeImageBitmap() }.getOrNull()
    }
}
