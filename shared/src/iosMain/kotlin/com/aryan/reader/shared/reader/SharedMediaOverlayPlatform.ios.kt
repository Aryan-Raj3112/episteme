package com.aryan.reader.shared.reader

import androidx.compose.runtime.Composable

/**
 * iOS has no archive reader for media overlays yet.
 *
 * The iOS engine does not exist either — [IosSharedMediaOverlayPlaybackHolder] holds the seam for
 * it — so a SMIL reader alone would be a reference nobody can act on. Both land together, when the
 * `AVPlayer` engine reads its SMIL bodies through `IosZipEpubArchive`, the same way Android's
 * engine reads them through `SharedJvmEpubArchiveReader`.
 *
 * Returning null is the in-spec reader behaviour (`RS §9`): a reader that does not support overlays
 * ignores them and opens the book normally. Android is the benchmark; iOS follows it.
 */
@Composable
actual fun rememberSharedMediaOverlaySmilReader(bookPath: String): ((String) -> String?)? = null
