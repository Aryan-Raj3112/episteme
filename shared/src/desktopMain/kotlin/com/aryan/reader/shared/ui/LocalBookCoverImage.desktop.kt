package com.aryan.reader.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun LocalBookCoverImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier
) {
    var bitmap by remember(path) {
        mutableStateOf(DesktopBookCoverImageCache.peek(path))
    }

    LaunchedEffect(path) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                DesktopBookCoverImageCache.load(path)
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
