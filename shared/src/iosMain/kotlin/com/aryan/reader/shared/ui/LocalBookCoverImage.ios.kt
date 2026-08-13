@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSFileManager
import platform.posix.memcpy

@Composable
internal actual fun LocalBookCoverImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier
) {
    val bitmap = remember(path) {
        runCatching {
            val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return@runCatching null
            val bytes = ByteArray(data.length.toInt())
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), data.bytes, bytes.size.convert())
                }
            }
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
