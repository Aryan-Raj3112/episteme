package com.aryan.reader.desktop

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

internal fun File.writeTextAtomically(text: String) {
    parentFile?.mkdirs()
    val temp = createSiblingTempFile()
    try {
        temp.writeText(text)
        moveReplacing(temp, this)
    } finally {
        runCatching { if (temp.exists()) temp.delete() }
    }
}

internal fun File.storePropertiesAtomically(properties: Properties, comments: String) {
    parentFile?.mkdirs()
    val temp = createSiblingTempFile()
    try {
        temp.outputStream().use { output ->
            properties.store(output, comments)
        }
        moveReplacing(temp, this)
    } finally {
        runCatching { if (temp.exists()) temp.delete() }
    }
}

private fun File.createSiblingTempFile(): File {
    val directory = parentFile ?: File(".")
    directory.mkdirs()
    val prefix = ".$name."
    return Files.createTempFile(directory.toPath(), prefix, ".tmp").toFile()
}

private fun moveReplacing(source: File, target: File) {
    target.parentFile?.mkdirs()
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}
