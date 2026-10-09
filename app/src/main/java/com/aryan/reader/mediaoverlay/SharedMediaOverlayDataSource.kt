package com.aryan.reader.mediaoverlay

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.aryan.reader.shared.reader.SharedJvmEpubArchiveReader
import com.aryan.reader.shared.reader.SharedMediaOverlayAudioUri
import java.io.InputStream

/**
 * Plays an EPUB's own audio straight out of the archive.
 *
 * **Why not extract the audio to the cache first.** That is the obvious design and it is wrong here.
 * The reference book is 124 MB of which the audio is most; a reader who has opened a book but not
 * pressed play would have paid for the whole narration. Worse, the extraction cache is keyed to book
 * loading, so the files would have to be invalidated by a parser-version bump every time this
 * feature changed — coupling a reader feature into every book's load path. Streaming costs nothing
 * until playback starts, and nothing at all if it never does.
 *
 * The path encoding itself lives in [SharedMediaOverlayAudioUri] rather than here, so both platforms
 * address entries identically and it can be tested without a device.
 */
internal object MediaOverlayUri {
    const val scheme = SharedMediaOverlayAudioUri.scheme

    /** The archive path inside an entry Uri, or null for a Uri that is not one of ours. */
    fun entryPathOf(uri: Uri): String? =
        SharedMediaOverlayAudioUri.entryPathOf(uri.toString())

    fun uriFor(entryPath: String): Uri = Uri.parse(SharedMediaOverlayAudioUri.uriFor(entryPath))
}

/**
 * A seekable [DataSource] over one zip entry.
 *
 * Random access is supported because the media framework needs it: without it ExoPlayer cannot seek
 * within a clip, and a clip's `clipBegin` is not the start of the file. Seeking forward skips
 * bytes on the stream; seeking backward reopens it, which is why a `par` boundary crossing never
 * replays from zero.
 */
class SharedEpubZipEntryDataSource(
    private val archive: SharedJvmEpubArchiveReader,
    private val entryPath: String
) : BaseDataSource(/* isNetwork = */ false) {

    private var stream: InputStream? = null
    private var entryLength: Long = UnknownLength
    private var bytesRemaining: Long = UnknownLength
    private var bytesRead: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val requestedStart = if (dataSpec.position == C.POSITION_UNSET.toLong()) 0L else dataSpec.position
        val fullLength = archive.entryLengthOrNull(entryPath)
        val opened = archive.openStreamOrNull(entryPath)
        if (opened == null) {
            throw java.io.IOException("Missing EPUB audio entry: $entryPath")
        }
        // Skip to the requested position. Without this the stream starts at the entry's first byte
        // and every clip but the first would play the file's opening under a highlight that says
        // otherwise — the media framework seeks forward per clip, and it reads position 0 for none
        // of them.
        if (requestedStart > 0L) opened.skipFully(requestedStart)
        stream = opened
        entryLength = fullLength ?: UnknownLength
        bytesRead = requestedStart
        // A DataSpec may name a window inside the entry; everything outside it is not ours to serve.
        bytesRemaining = if (dataSpec.length == UnknownLength || fullLength == null) {
            UnknownLength
        } else {
            (fullLength - requestedStart).coerceAtLeast(0L)
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val source = stream ?: throw java.io.IOException("Stream is not open")
        val toRead = if (bytesRemaining == UnknownLength) length else minOf(length.toLong(), bytesRemaining).toInt()
        val read = source.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            // The zip entry really ended earlier than its header claimed, which some real files do.
            bytesRemaining = 0L
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read
        if (bytesRemaining != UnknownLength) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri = MediaOverlayUri.uriFor(entryPath)

    override fun close() {
        runCatching { stream?.close() }
        stream = null
        transferEnded()
    }

    companion object {
        /**
         * `C.LENGTH_UNSET` widened to [Long].
         *
         * The constant is an `Int` sentinel that media3 compares against widened values, so keeping
         * the field typed [Long] means one sentinel rather than a widening at every comparison — and
         * a comparison that silently widened differently would read as "stream ended".
         */
        const val UnknownLength = C.LENGTH_UNSET.toLong()
    }
}

/**
 * Advances [count] bytes, or as many as the stream has.
 *
 * [java.io.InputStream.skip] is allowed to skip fewer bytes than asked, and a zip entry stream
 * routinely does exactly that, so a single call would leave the position short and a clip would
 * start mid-syllable. A zero-byte skip is also legal, which is why this falls back to reading and
 * discarding rather than looping on skip forever.
 */
private fun InputStream.skipFully(count: Long): Long {
    var remaining = count
    if (remaining <= 0L) return 0L
    val scratch = ByteArray(DEFAULT_BUFFER_SIZE)
    while (remaining > 0L) {
        val skipped = runCatching { skip(remaining) }.getOrDefault(0L)
        if (skipped > 0L) {
            remaining -= skipped
            continue
        }
        val read = read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
        if (read <= 0) break
        remaining -= read
    }
    return count - remaining
}

/**
 * Sends `reader-epub-audio:` entries to [SharedEpubZipEntryDataSource] and everything else to a
 * delegate.
 *
 * A router rather than a substitution because a book may legitimately point a `par` at a `file:` or
 * `http:` URL, and the delegate handles those without this needing to know how.
 */
internal class RoutingDataSource(
    private val archive: SharedJvmEpubArchiveReader,
    private val fallback: DataSource.Factory
) : DataSource {

    private var inner: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        // Deferred, because for a zip entry the delegate is never used and constructing one anyway
        // would open a resolver lookup per clip.
        pendingListeners += transferListener
        inner?.addTransferListener(transferListener)
    }

    private val pendingListeners = mutableListOf<TransferListener>()

    override fun open(dataSpec: DataSpec): Long {
        val entryPath = MediaOverlayUri.entryPathOf(dataSpec.uri)
        val source = if (entryPath != null) {
            SharedEpubZipEntryDataSource(archive, entryPath)
        } else {
            fallback.createDataSource().also { delegate ->
                pendingListeners.forEach(delegate::addTransferListener)
            }
        }
        pendingListeners.forEach(source::addTransferListener)
        inner = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        inner?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    /** Null until opened: a data source that has never been opened has no resource to name. */
    override fun getUri(): Uri? = inner?.uri

    override fun close() {
        inner?.close()
        inner = null
    }
}
