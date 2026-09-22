package com.aryan.reader.data

import java.io.File
import java.io.IOException
import timber.log.Timber

/** Writes UTF-8 JSON with Android's backup/restore atomic-file protocol. */
fun File.writeJsonAtomically(json: String) {
    writeJsonAtomically(json, ::moveByRename)
}

/**
 * Test-visible overload: [move] replaces the rename step so tests can force
 * the copy fallback without filesystem tricks. Production always uses
 * [moveByRename].
 */
internal fun File.writeJsonAtomically(json: String, move: (src: File, dst: File) -> Boolean) {
    parentFile?.mkdirs()
    val backupName = File(parentFile, "$name.bak")
    val newName = File(parentFile, "$name.new")

    if (exists()) {
        if (!backupName.exists()) {
            if (!renameTo(backupName) && exists()) {
                // renameTo fails silently, and the source can vanish between
                // the exists() check and here when two saves to the same file
                // race (or it is deleted concurrently). There is then nothing
                // to back up: persist best-effort and continue with the fresh
                // write instead of crashing the save.
                runCatching {
                    copyTo(backupName, overwrite = true)
                    delete()
                }.onFailure { error ->
                    Timber.w(error, "Skipping backup of $absolutePath.")
                }
            }
        } else {
            delete()
        }
    }

    try {
        newName.outputStream().use { output -> output.write(json.toByteArray(Charsets.UTF_8)) }
        if (!move(newName, this)) {
            // File.renameTo is unreliable on some devices/firmwares: it
            // returns false without a reason (no overwrite semantics, stale
            // FDs, transient FS errors). A copy achieves the same durable
            // result, only slower, so use it as a fallback instead of
            // crashing the save and losing the in-memory annotations.
            try {
                Timber.w("rename of $newName failed; falling back to copy for $absolutePath.")
                newName.copyTo(this, overwrite = true)
                newName.delete()
            } catch (copyError: Throwable) {
                throw IOException("Failed to persist ${describeForPersist(newName, backupName)}", copyError)
            }
        }
        backupName.delete()
    } catch (error: Throwable) {
        // Never delete a still-valid destination to "clean up": if the .new
        // write or the move/copy failed before replacing it, it still holds
        // the last good payload. Only restore the backup when the
        // destination is actually missing (it was rotated to .bak above).
        if (!exists() && backupName.exists()) {
            // Best-effort restore: never let it mask the original failure.
            runCatching {
                if (!backupName.renameTo(this)) {
                    backupName.copyTo(this, overwrite = true)
                }
                if (exists()) backupName.delete()
            }.onFailure { restoreError ->
                Timber.w(restoreError, "Failed to restore backup of $absolutePath.")
            }
        }
        // Clean the staging file only once the destination is safe; if the
        // destination is still missing, the .new file may hold the only copy.
        if (exists()) newName.delete()
        if (error is IOException && error.message?.startsWith("Failed to persist") == true) throw error
        // Destination safe (old payload intact) or a non-Exception Error
        // (e.g. OOM, which must never be converted to IOException): rethrow.
        if (exists() || error !is Exception) throw error
        // Destination missing and no backup restored: surface FS diagnostics
        // so the next Crashlytics report names the FS state, not just rename.
        throw IOException("Failed to persist ${describeForPersist(newName, backupName)}", error)
    }
}

/**
 * Legacy rename with delete-and-retry for firmwares where rename does not
 * overwrite an existing destination. Returns false (no throw) when the FS
 * refuses, so the caller can fall back to a copy.
 */
private fun moveByRename(src: File, dst: File): Boolean {
    if (src.renameTo(dst)) return true
    if (dst.exists() && !dst.delete()) return false
    return src.renameTo(dst)
}

private fun File.describeForPersist(newName: File, backupName: File): String {
    val parent = parentFile
    return "$absolutePath " +
        "(destExists=${exists()}, newExists=${newName.exists()}, " +
        "newLen=${runCatching { newName.length() }.getOrDefault(-1)}, " +
        "backupExists=${backupName.exists()}, parentExists=${parent?.exists() ?: false}, " +
        "parentWritable=${parent?.canWrite() ?: false}, " +
        "usableBytes=${runCatching { parent?.usableSpace ?: -1 }.getOrDefault(-1)})"
}

/**
 * True when the file's UTF-8 bytes exactly match [content]. Compares in bounded
 * chunks so large sidecars never need the whole file materialized in memory on
 * top of the already-encoded [content] (the previous `file.readText() == json`
 * pattern allocated extra full-size copies and buffer-doubling transients).
 */
fun File.hasSameUtf8Content(content: String): Boolean {
    if (!exists()) return false
    val expected = content.toByteArray(Charsets.UTF_8)
    if (length() != expected.size.toLong()) return false

    val buffer = ByteArray(READ_BUFFER_BYTES)
    var offset = 0
    inputStream().use { input ->
        while (offset < expected.size) {
            val read = input.read(buffer, 0, minOf(buffer.size, expected.size - offset))
            if (read <= 0) return false
            for (index in 0 until read) {
                if (buffer[index] != expected[offset + index]) return false
            }
            offset += read
        }
        return true
    }
}

private const val READ_BUFFER_BYTES = 32 * 1024
