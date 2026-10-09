package com.aryan.reader.shared.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.io.File

/**
 * Android's SMIL reader: a real [File] over the book archive.
 *
 * Library-imported books live as files under the app's `filesDir/books/`, and external `file://`
 * books are files by construction, so the common case is a plain path. A `content://` URI cannot be
 * handed here — and callers should not try: the reader screen resolves the archive location and
 * passes [bookPath] as a plain filesystem path, because `java.util.zip.ZipFile` needs a seekable
 * file, which only a real path provides.
 */
@Composable
actual fun rememberSharedMediaOverlaySmilReader(bookPath: String): ((String) -> String?)? {
    val reader = remember(bookPath) {
        val file = File(bookPath.trim())
        if (!file.isFile) {
            null
        } else {
            val candidate = SharedJvmEpubArchiveReader(file)
            // An archive that cannot be opened at all degrades to "no narration" rather than
            // throwing, so an unreadable book stays exactly as usable as before this feature
            // existed. The failed reader was never handed out, so it is ours to close here.
            if (candidate.isReadable()) candidate else { candidate.close(); null }
        }
    } ?: return null

    DisposableEffect(reader) {
        onDispose { reader.close() }
    }

    return { path -> reader.readTextOrNull(path) }
}
