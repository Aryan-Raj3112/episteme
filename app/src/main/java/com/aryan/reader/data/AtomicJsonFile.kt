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
