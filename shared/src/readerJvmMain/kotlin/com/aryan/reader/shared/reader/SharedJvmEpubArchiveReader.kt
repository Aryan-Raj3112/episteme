package com.aryan.reader.shared.reader

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Reads single entries out of an EPUB archive.
 *
 * Media overlays need two things from the archive that ordinary book loading does not provide: the
 * SMIL bodies, one per narrated spine item, and the audio itself. Neither belongs in the loading
 * path — the bodies are only wanted for one chapter at a time, and pulling 124 MB of audio to disk
 * for a reader that may never press play would be a large cost for a feature most books never use.
 *
 * So this is a narrow, on-demand reader. It holds the [ZipFile] open for its lifetime, which is
 * what makes it cheap enough to sit behind a per-chapter cache: a clip advances every couple of
 * seconds and re-opening the archive each time would dominate the cost of parsing it.
 *
 * Not thread-safe by contract — it belongs to one reader's single-threaded UI state, same as
 * [SharedMediaOverlayDocumentCache]. [readBytes] is safe to call from a background thread only if the
 * caller owns the whole instance; the loader does.
 */
class SharedJvmEpubArchiveReader(private val file: File) : AutoCloseable {

    private var zip: ZipFile? = null

    /**
     * Whether this archive can be opened at all.
     *
     * Checked before handing the reader to the overlay code so an unreadable book degrades to "no
     * narration" instead of throwing on the first chapter the reader opens.
     */
    fun isReadable(): Boolean = runCatching { open() }.isSuccess

    private fun open(): ZipFile = zip ?: ZipFile(file).also { zip = it }

    /**
     * An entry's bytes, or null when it is absent or unreadable.
     *
     * [path] is resolved through [safeEpubPathOrNull] first, so a SMIL document naming `../../..`
     * cannot read outside the archive. That guard is shared with package loading rather than
     * repeated, because a second copy of a zip-slip guard is a second copy to get wrong.
     */
    fun readBytes(path: String): ByteArray? {
        val safePath = safeEpubPathOrNull(path) ?: return null
        return runCatching {
            val entry = open().getEntry(safePath) ?: return null
            open().getInputStream(entry).use(InputStream::readBytes)
        }.getOrNull()
    }

    /** An entry's text, or null. Decoded as UTF-8, which is what `EPUB RS §9` specifies. */
    fun readTextOrNull(path: String): String? = readBytes(path)?.toString(Charsets.UTF_8)

    /**
     * A fresh stream over an entry, or null when it is absent.
     *
     * Streaming rather than [readBytes] because the audio is read by the media framework over the
     * course of a whole chapter, and buffering 40 MB per clip to play a sentence of it would be
     * absurd. The caller closes the stream; the archive stays open.
     */
    fun openStreamOrNull(path: String): InputStream? {
        val safePath = safeEpubPathOrNull(path) ?: return null
        return runCatching {
            val entry = open().getEntry(safePath) ?: return null
            open().getInputStream(entry)
        }.getOrNull()
    }

    /**
     * An entry's uncompressed size in bytes, or null when it is absent.
     *
     * Media frameworks want a length up front so they can seek and can report a duration before
     * decoding. Taking it from the zip entry rather than by reading the stream is what makes that
     * possible.
     */
    fun entryLengthOrNull(path: String): Long? {
        val safePath = safeEpubPathOrNull(path) ?: return null
        return runCatching { open().getEntry(safePath)?.size }.getOrNull()?.takeIf { it > 0L }
    }

    override fun close() {
        runCatching { zip?.close() }
        zip = null
    }
}