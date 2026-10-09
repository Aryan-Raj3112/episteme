package com.aryan.reader.shared.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS's SMIL reader: a reader over the archive the book was loaded through.
 *
 * Borrowed rather than opened — see [SharedMediaOverlayArchiveReader] for why a second
 * [com.aryan.reader.shared.ios.IosZipEpubArchive] over a 124 MB book is not a cost worth paying for
 * a feature the book may never use.
 *
 * Null when no archive is registered at [bookPath], which is the honest answer rather than a
 * placeholder: it means this platform cannot reach the book, and `RS §9` requires exactly that
 * degrade — a reader that does not support overlays ignores them and opens the book normally.
 * Android is the benchmark; iOS follows it.
 */
@Composable
actual fun rememberSharedMediaOverlaySmilReader(bookPath: String): ((String) -> String?)? =
    remember(bookPath) {
        SharedMediaOverlayArchiveReader.at(bookPath)?.let { reader ->
            { path -> reader.readTextOrNull(path) }
        }
    }