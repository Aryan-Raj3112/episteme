@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        bitmap = withContext(Dispatchers.Default) {
            url?.let {
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
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Text(
            text = contentDescription?.trim()?.takeIf { it.isNotEmpty() }?.take(1)?.uppercase() ?: "?",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
