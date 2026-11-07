package com.aryan.reader.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.aryan.reader.shared.LIBRARY_COVER_LOG_TAG
import com.aryan.reader.shared.reader.writeSharedReaderDiagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws a book cover from a local file path.
 *
 * Shared across platforms: decoding is cached, downsampled, keyed on the file's
 * current contents rather than its path, and kept off the composition thread.
 */
@Composable
internal fun LocalBookCoverImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier
) {
    // Read so an in-place cover rewrite re-runs the effect below.
    val revision = BookCoverImageCache.revision
    var bitmap by remember(path, revision) {
        mutableStateOf(BookCoverImageCache.peek(path))
    }

    LaunchedEffect(path, revision) {
        val loaded = withContext(Dispatchers.Default) { BookCoverImageCache.load(path) }
        if (loaded != null) {
            bitmap = loaded
        }
        // A composed cell that ends with no bitmap is the exact symptom being
        // chased, so it gets its own line: every stage above can report healthy
        // while this one still fails to draw.
        writeSharedReaderDiagnostic(
            LIBRARY_COVER_LOG_TAG,
            "render.result path=${path.coverLogPath()} drawn=${bitmap != null} revision=$revision",
        )
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}