package com.aryan.reader.data

import android.util.AtomicFile
import java.io.File

/** Writes UTF-8 JSON with Android's backup/restore atomic-file protocol. */
fun File.writeJsonAtomically(json: String) {
    parentFile?.mkdirs()
    val atomicFile = AtomicFile(this)
    val output = atomicFile.startWrite()
    try {
        output.write(json.toByteArray(Charsets.UTF_8))
        atomicFile.finishWrite(output)
    } catch (error: Throwable) {
        atomicFile.failWrite(output)
        throw error
    }
}
