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
 * The lookup runs on every SMIL read rather than once at composition, because composition runs
 * before the loader registers the archive: the reader screen composes immediately on open while
 * `loadIosEpubBook` registers on a background dispatcher. Resolving eagerly here cached a null
 * for the whole first open, so a narrated book showed no toolbar icon until it was closed and
 * reopened (when the store still held the first open's registration). A non-blank path therefore
 * always yields a reader; each read borrows whatever the store holds then, which is the cached
 * instance once loaded.
 *
 * Null only when [bookPath] is blank, which is the honest answer rather than a placeholder: it
 * means this platform cannot reach the book, and `RS §9` requires exactly that degrade — a reader
 * that does not support overlays ignores them and opens the book normally. Android is the
 * benchmark; iOS follows it.
 */
@Composable
actual fun rememberSharedMediaOverlaySmilReader(bookPath: String): ((String) -> String?)? {
    if (bookPath.isBlank()) return null
    return remember(bookPath) { iosMediaOverlaySmilReaderFor(bookPath) }
}