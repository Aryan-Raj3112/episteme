@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.posix.memcpy

@Composable
internal fun IosOpdsCoverImage(
    url: String?,
    contentDescription: String?,
    repository: IosOpdsRepository,
    username: String?,
    password: String?,
    modifier: Modifier,
) {
    var bitmap by remember(url, username, password) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, username, password) {
        bitmap = url?.let {
            runCatching {
                val data = repository.fetchCoverData(it, username, password) ?: return@runCatching null
                val bytes = ByteArray(data.length.toInt())
                if (bytes.isNotEmpty()) {
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), data.bytes, bytes.size.convert())
                    }
                }
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
