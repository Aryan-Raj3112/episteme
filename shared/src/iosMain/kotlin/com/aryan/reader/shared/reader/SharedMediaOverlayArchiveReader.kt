package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ios.IosEpubResourceStore

/**
 * iOS archive access for media overlays: SMIL bodies and, for the engine, byte ranges of the audio.
 *
 * Deliberately **borrowed, not opened.** An `IosZipEpubArchive` holds its whole zip as a
 * `ByteArray`, and the reader screen already has one open for the book it is showing — so opening a
 * second for narration would double resident memory on a large archive for as long as the reader is
 * open, on a feature most books never reach. [IosEpubResourceStore.registeredArchiveAtPath] hands
 * back the instance the loader made, and the whole cost of narration's archive access is then zero
 * until playback actually reads something.
 *
 * This is the counterpart to Android's [rememberSharedMediaOverlaySmilReader], which opens a
 * `ZipFile` over the path: there a second open is a file descriptor, here it is the file.
 *
 * The audio half exists here rather than in the engine because the engine is a Kotlin class driving
 * a Swift `AVPlayer`, and the resource loader that feeds it needs a byte-range callback across the
 * bridge. Holding the archive on this side keeps that callback a pure function of an offset and a
 * length.
 *
 * Not thread-safe by contract — same as [SharedMediaOverlayDocumentCache], it belongs to one
 * reader's UI state, and the resource loader's requests are serialized by AVFoundation.
 */
internal class SharedMediaOverlayArchiveReader private constructor(
    private val archive: com.aryan.reader.shared.ios.IosZipEpubArchive
) {

    /** An entry's text, or null when it is absent or unreadable. */
    fun readTextOrNull(path: String): String? = runCatching { archive.readText(path) }.getOrNull()

    /** An entry's uncompressed size in bytes, or null when it is absent. */
    fun entryLengthOrNull(path: String): Long? = runCatching { archive.entryLengthOrNull(path) }.getOrNull()

    /**
     * A byte range out of one entry, or null when the entry cannot be read.
     *
     * Uncompressed coordinates, because a clip's `clipBegin` is an offset into decoded audio. A range
     * that runs past the end of the entry comes back short rather than failing: a media framework
     * routinely asks for a window the file does not have, and refusing would fail a read that had
     * already been told how much data to expect.
     */
    fun readEntryRange(path: String, offset: Long, length: Long): ByteArray? =
        runCatching { archive.readEntryRange(path, offset, length) }.getOrNull()

    companion object {
        /**
         * A reader over the archive already open at [bookPath], or null when none is.
         *
         * Null rather than opening one, and that is not a placeholder: it is the answer for every
         * book whose archive is not resident, which is every book that has not been loaded through
         * the shared loader. `RS §9` requires exactly this — a reader that cannot support overlays
         * ignores them and opens the book normally.
         */
        fun at(bookPath: String): SharedMediaOverlayArchiveReader? {
            if (bookPath.isBlank()) return null
            val archive = IosEpubResourceStore.registeredArchiveAtPath(bookPath) ?: return null
            return SharedMediaOverlayArchiveReader(archive)
        }
    }
}