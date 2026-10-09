package com.aryan.reader.shared.reader

import com.aryan.reader.shared.BookItem

/**
 * A media overlay engine a reader screen can drive, plus the one thing it cannot work out itself.
 *
 * The transport half is [SharedMediaOverlayPlayback] and is implemented per platform. What is added
 * here is *which archive*, and it is not a detail the screen can decide: Android resolves a
 * `content://` URI to a real file before opening a `ZipFile`, while iOS borrows the archive the
 * loader already has open. Asking the engine means a screen written once cannot pick the wrong one.
 *
 * The archive's lifetime is the reader's, not the session's — a reader that opens a narrated book and
 * never presses play must cost nothing, and one that closes must not leave a resource loader serving
 * a book it no longer shows — so attaching is separate from [play] rather than inferred by it.
 */
interface SharedMediaOverlayEngine : SharedMediaOverlayPlayback {
    /** Points the engine at the open book's archive, or detaches when [book] is null. */
    fun attachArchive(book: BookItem?)
}