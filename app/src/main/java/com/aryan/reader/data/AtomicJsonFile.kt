package com.aryan.reader.data

import java.io.File
import java.io.IOException

/** Writes UTF-8 JSON with Android's backup/restore atomic-file protocol. */
fun File.writeJsonAtomically(json: String) {
    parentFile?.mkdirs()
    val backupName = File(parentFile, "$name.bak")
    val newName = File(parentFile, "$name.new")

    if (exists()) {
        if (!backupName.exists()) {
            if (!renameTo(backupName)) {
                copyTo(backupName, overwrite = true)
                delete()
            }
        } else {
            delete()
        }
    }

    try {
        newName.outputStream().use { output -> output.write(json.toByteArray(Charsets.UTF_8)) }
        if (!newName.renameTo(this)) {
            if (exists() && !delete() || !newName.renameTo(this)) {
                throw IOException("Failed to persist ${absolutePath}")
            }
        }
        backupName.delete()
    } catch (error: Throwable) {
        delete()
        if (backupName.exists() && !backupName.renameTo(this)) {
            backupName.copyTo(this, overwrite = true)
            backupName.delete()
        }
        newName.delete()
        throw error
    }
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
