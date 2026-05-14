package com.aryan.reader.desktop

import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class DesktopPreparedImport(
    val files: List<ImportedBookFile>,
    val failedCount: Int
)

internal class DesktopBookImporter(
    private val booksDirectory: File = File(desktopUserDataRoot(), "books")
) {
    fun prepareImports(files: List<ImportedBookFile>): DesktopPreparedImport {
        val preparedFiles = mutableListOf<ImportedBookFile>()
        var failedCount = 0
        booksDirectory.mkdirs()

        files.forEach { file ->
            val type = SharedFileCapabilities.fileTypeForName(file.name)
            if (!SharedFileCapabilities.canOpen(type, ReaderPlatform.DESKTOP)) {
                preparedFiles += file.copy(sourceFolder = null)
                return@forEach
            }

            val source = file.localPath
                ?.let(::File)
                ?.takeIf { it.isFile }

            if (source == null) {
                failedCount += 1
                return@forEach
            }

            val hashResult = runCatching { source.sha256() }
            if (hashResult.isFailure) {
                failedCount += 1
                return@forEach
            }
            val hash = hashResult.getOrThrow()
            val destination = File(booksDirectory, "$hash${file.storageSuffix(source)}")

            val copyResult = runCatching {
                copyIfNeeded(source, destination)
                destination
            }
            if (copyResult.isFailure) {
                failedCount += 1
                return@forEach
            }
            val copied = copyResult.getOrThrow()

            preparedFiles += ImportedBookFile(
                name = file.name,
                uriString = null,
                localPath = copied.absolutePath,
                size = copied.length(),
                sourceFolder = null,
                id = hash
            )
        }

        return DesktopPreparedImport(
            files = preparedFiles,
            failedCount = failedCount
        )
    }

    private fun copyIfNeeded(source: File, destination: File) {
        val sourceFile = source.canonicalFile
        val destinationFile = destination.canonicalFile
        if (sourceFile == destinationFile) return
        destination.parentFile?.mkdirs()
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun ImportedBookFile.storageSuffix(source: File): String {
        return SharedFileCapabilities.fileExtensionSuffixForName(name)
            ?: source.extension.takeIf { it.isNotBlank() }?.let { ".$it" }
            ?: ".book"
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
