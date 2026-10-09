package com.aryan.reader.mediaoverlay

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.aryan.reader.shared.reader.SharedJvmEpubArchiveReader
import java.io.File

/**
 * The archive the media player streams narration out of.
 *
 * Exists as a holder rather than as a `DataSource.Factory` built per book because `ExoPlayer.Builder`
 * takes a factory **once**, at construction, and has no setter afterwards. Rebuilding the player per
 * book would mean dropping the media session — losing the notification, the lock-screen entry and the
 * audio focus — every time the reader opened a different book.
 *
 * So the factory is built once over this holder and reads the *current* archive each time a load
 * starts. Attaching a new book swaps one field. That also removes a real hazard: a factory that
 * captured an earlier `ZipFile` would make the next clip open the wrong book, silently, and it would
 * sound like narration from the wrong place entirely.
 *
 * Volatile rather than locked. The only mutation is a whole-reference swap on the main thread, and
 * the reader it happens on is the media framework's own load thread.
 */
class SharedMediaOverlayArchiveHolder {

    @Volatile
    private var reader: SharedJvmEpubArchiveReader? = null

    /** The archive currently attached, or null before the first book. Diagnostics and tests. */
    val attachedBook: File?
        get() = attachedFile

    @Volatile
    private var attachedFile: File? = null

    /**
     * Opens [file] and makes it the current archive.
     *
     * @return null on success, or a message describing why the archive is unusable. A failure leaves
     *   the previous archive in place, so a reader that tries a second book and fails does not lose
     *   narration that was already playing.
     */
    fun attach(file: File): String? {
        if (!file.isFile) return "Book file is missing"
        val next = SharedJvmEpubArchiveReader(file)
        if (!next.isReadable()) {
            next.close()
            return "Book archive could not be opened"
        }
        val previous = reader
        reader = next
        attachedFile = file
        if (previous !== next) previous?.close()
        return null
    }

    /** The current archive, or null when no book has been attached. */
    fun currentOrNull(): SharedJvmEpubArchiveReader? = reader

    /**
     * The data source factory the player is built with.
     *
     * Routes [MediaOverlayUri.scheme] at the attached archive and everything else — a `file:` or
     * `http:` URL an unusual book may declare — at the ordinary sources, so an overlay pointing
     * somewhere unusual is handled rather than rejected.
     */
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val fallback = DataSource.Factory {
            DefaultDataSource.Factory(context.applicationContext).createDataSource()
        }
        return DataSource.Factory {
            val archive = reader
            if (archive == null) fallback.createDataSource() else RoutingDataSource(archive, fallback)
        }
    }

    fun close() {
        reader?.close()
        reader = null
        attachedFile = null
    }
}