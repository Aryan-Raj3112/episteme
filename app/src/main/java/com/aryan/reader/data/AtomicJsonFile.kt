package com.aryan.reader.data

import java.io.File
import java.io.IOException
import java.io.OutputStream
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
    stageJson { output -> output.write(json.toByteArray(Charsets.UTF_8)) }
    promoteStagedJson(move)
}

/**
 * Streaming sibling of [writeJsonAtomically]: [write] emits the payload
 * straight into the staging file instead of taking a pre-built String, so a
 * document whose encoded form does not fit in memory can still be persisted
 * (see SharedPdfLegacyInkStreamEncoder). Skips the write entirely when the
 * staged bytes already match the destination, so a no-op save neither
 * materializes nor rewrites the payload. Returns true when the file changed.
 *
 * Staging happens before the destination is rotated into the backup, so a
 * failing [write] leaves the last good payload in place untouched.
 */
fun File.writeJsonAtomicallyIfChanged(write: (OutputStream) -> Unit): Boolean {
    val staged = stageJson(write)
    if (exists() && staged.hasSameUtf8ContentAs(this)) {
        // Unchanged: drop the staging copy and leave mtime (which drives
        // cloud-sync change detection) alone.
        staged.delete()
        return false
    }
    promoteStagedJson(::moveByRename)
    return true
}

/** Writes [write]'s payload into the `$name.new` staging file and returns it. */
private fun File.stageJson(write: (OutputStream) -> Unit): File {
    parentFile?.mkdirs()
    val newName = File(parentFile, "$name.new")
    try {
        newName.outputStream().use(write)
    } catch (error: Throwable) {
        // The destination was never rotated, so it still holds the last good
        // payload: drop the partial staging file and rethrow the original
        // failure unchanged (an OOM must not be laundered into an IOException,
        // and there is no FS state to diagnose here).
        if (newName.isFile) newName.delete()
        throw error
    }
    return newName
}

/**
 * Installs the staged `$name.new` file over the destination, rotating the
 * previous payload to `$name.bak` first so a failure can restore it.
 */
private fun File.promoteStagedJson(move: (src: File, dst: File) -> Boolean) {
    val backupName = backupFile()
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
        // Never delete a still-valid destination to "clean up": if the move or
        // copy failed before replacing it, it still holds the last good
        // payload. Only restore the backup when the destination is actually
        // missing (it was rotated to .bak above).
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

private fun File.backupFile(): File = File(parentFile, "$name.bak")

/**
 * Legacy rename with delete-and-retry for firmwares where rename does not
 * overwrite an existing destination. Returns false (no throw) when the FS
 * refuses, so the caller can fall back to a copy.
 *
 * Some firmwares report failure for a rename that actually took effect. The
 * source is therefore re-checked before the destination is deleted: without
 * that guard the retry destroys the payload the "failed" rename had just
 * installed, and the caller's copy fallback then fails with
 * `NoSuchFileException` on the vanished `.new` file (crashlytics-triage #53).
 */
internal fun moveByRename(src: File, dst: File): Boolean {
    if (src.renameTo(dst)) return true
    // The rename may have moved the file despite reporting failure. If the
    // source is gone the payload already sits at the destination — treat that
    // as success so the retry below cannot delete the only good copy.
    if (!src.exists()) return dst.exists()
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

/**
 * True when this file's UTF-8 bytes exactly match [other]'s. The file-to-file
 * counterpart of [hasSameUtf8Content]: lets an incrementally written sidecar be
 * compared against the stored one without either side ever being fully
 * materialized. Reads in bounded chunks, so peak memory is the buffer, not the
 * payload.
 */
fun File.hasSameUtf8ContentAs(other: File): Boolean {
    if (!isFile || !other.isFile) return false
    if (length() != other.length()) return false

    val buffer = ByteArray(READ_BUFFER_BYTES)
    val otherBuffer = ByteArray(READ_BUFFER_BYTES)
    inputStream().use { mine ->
        other.inputStream().use { theirs ->
            while (true) {
                val read = mine.read(buffer)
                if (read <= 0) return true
                val readOther = theirs.read(otherBuffer, 0, read)
                if (read != readOther) return false
                for (index in 0 until read) {
                    if (buffer[index] != otherBuffer[index]) return false
                }
            }
        }
    }
}

private const val READ_BUFFER_BYTES = 32 * 1024
