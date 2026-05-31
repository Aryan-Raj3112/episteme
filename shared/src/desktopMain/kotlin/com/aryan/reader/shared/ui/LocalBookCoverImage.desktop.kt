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
        val loaded = withContext(Dispatchers.IO) {
            DesktopBookCoverImageCache.load(path)
        }
        if (loaded != null && loaded != bitmap) {
            bitmap = loaded
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
