@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.aryan.reader.shared.UserData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

/**
 * iOS account-image slot used by both the global drawer and Unified top bar.
 * Firebase exposes photoURL as a remote URL, so decode it off the Compose
 * thread and retain the account-circle fallback for missing/invalid images.
 */
@Composable
internal fun IosAccountAvatar(
    user: UserData?,
    modifier: Modifier = Modifier,
) {
    val url = user?.photoUrl?.takeIf { it.startsWith("https://") }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.Default) {
            url?.let(::loadIosAccountImage)
        }
    }

    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = user?.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = user?.displayName,
            modifier = Modifier.fillMaxSize(),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun loadIosAccountImage(url: String): ImageBitmap? = runCatching {
    val data = NSData.dataWithContentsOfURL(NSURL.URLWithString(url) ?: return@runCatching null)
        ?: return@runCatching null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, bytes.size.convert())
        }
    }
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
