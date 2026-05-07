package com.aryan.reader.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

@Composable
internal actual fun LocalBookCoverImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier
) {
    val bitmap = remember(path) {
        runCatching {
            val file = File(path)
            if (!file.isFile) {
                null
            } else {
                SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
