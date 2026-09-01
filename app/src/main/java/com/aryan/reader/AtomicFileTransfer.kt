package com.aryan.reader

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Commits an app-owned staged file without first deleting the current final.
 *
 * Both paths must live on the same app-owned filesystem. The atomic move is
 * deliberately not used for SAF/document-provider URIs: those are separate,
 * non-transactional sinks and are handled by their caller.
 */
internal fun replaceAppOwnedFileAtomically(
    staged: File,
    destination: File,
) {
    require(staged != destination) { "Staged and destination files must differ" }
    require(staged.parentFile?.canonicalFile == destination.parentFile?.canonicalFile) {
        "Atomic replacement requires a shared parent directory"
    }
    if (!staged.isFile) {
        throw IOException("Staged transfer is missing: ${staged.absolutePath}")
    }

    try {
        Files.move(
            staged.toPath(),
            destination.toPath(),
            ATOMIC_MOVE,
            REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        // App-private Android files are on one local filesystem. renameTo()
        // replaces an existing sibling in one filesystem operation and keeps
        // the old final intact if the rename is rejected.
        if (!staged.renameTo(destination)) {
            throw IOException("Could not atomically replace ${destination.absolutePath}")
        }
    }
}

/**
 * Writes a staged app-owned file and commits it without exposing a partial
 * final. The caller supplies a unique sibling staging path; all failures
 * remove only that staging path and leave the previous destination untouched.
 */
internal fun writeAndReplaceAppOwnedFileAtomically(
    staged: File,
    destination: File,
    write: (OutputStream) -> Unit,
) {
    require(staged.parentFile?.canonicalFile == destination.parentFile?.canonicalFile) {
        "Atomic replacement requires a shared parent directory"
    }
    try {
        FileOutputStream(staged).use { output ->
            write(output)
            output.flush()
            output.fd.sync()
        }
        replaceAppOwnedFileAtomically(staged, destination)
    } finally {
        if (staged.exists()) staged.delete()
    }
}
