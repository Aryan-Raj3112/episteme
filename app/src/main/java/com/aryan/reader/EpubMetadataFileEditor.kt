package com.aryan.reader

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.aryan.reader.data.BookMetadataEdit
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.reader.SharedEpubCoverSnapshot
import com.aryan.reader.shared.reader.SharedEpubCoverUpdate
import com.aryan.reader.shared.reader.SharedEpubMetadataEditor
import com.aryan.reader.shared.reader.SharedEpubMetadataSnapshot
import com.aryan.reader.shared.reader.SharedEpubMetadataUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

private const val MAX_METADATA_COVER_BYTES = 12L * 1024L * 1024L

data class AndroidEpubMetadataEditResult(
    val metadata: SharedEpubMetadataSnapshot,
    val fileSize: Long,
    val fileContentModifiedTimestamp: Long,
    val cover: SharedEpubCoverSnapshot?
)

class EpubMetadataFileEditor(private val context: Context) {
    suspend fun writeMetadata(
        item: RecentFileItem,
        metadata: BookMetadataEdit
    ): Result<AndroidEpubMetadataEditResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(item.type == FileType.EPUB) { "Only EPUB metadata editing is supported." }
            val sourceUri = item.uriString?.toUri() ?: error("Book file is not available.")
            val token = UUID.randomUUID().toString()
            val sourceCopy = File(context.cacheDir, "epub_metadata_source_$token.epub")
            val editedCopy = File(context.cacheDir, "epub_metadata_edited_$token.epub")

            try {
                copyUriToFile(sourceUri, sourceCopy)
                val backupFile = backupOriginalIfNeeded(item, sourceCopy)
                val coverUpdate = metadata.coverImageUri?.toUri()?.let(::readCoverUpdate)
                    ?: if (metadata.restoreOriginalCover) {
                        SharedEpubMetadataEditor.readCover(backupFile)?.let { cover ->
                            SharedEpubCoverUpdate(bytes = cover.bytes, extension = cover.extension)
                        }
                    } else {
                        null
                    }
                val rewritten = SharedEpubMetadataEditor.rewrite(
                    source = sourceCopy,
                    destination = editedCopy,
                    update = SharedEpubMetadataUpdate(
                        title = metadata.title,
                        author = metadata.author,
                        description = metadata.description,
                        seriesName = metadata.seriesName,
                        seriesIndex = metadata.seriesIndex,
                        cover = coverUpdate
                    )
                )
                replaceUriBytes(sourceUri, editedCopy)

                AndroidEpubMetadataEditResult(
                    metadata = rewritten,
                    fileSize = queryFileSize(sourceUri).takeIf { it > 0L } ?: editedCopy.length(),
                    fileContentModifiedTimestamp = queryLastModified(sourceUri).takeIf { it > 0L }
                        ?: System.currentTimeMillis(),
                    cover = SharedEpubMetadataEditor.readCover(editedCopy)
                )
            } finally {
                sourceCopy.delete()
                editedCopy.delete()
            }
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read EPUB source.")
    }

    private fun readCoverUpdate(uri: Uri): SharedEpubCoverUpdate {
        val extension = coverExtension(uri) ?: error("Choose a JPG, PNG, GIF, WebP, or BMP cover image.")
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytesLimited(MAX_METADATA_COVER_BYTES)
        } ?: error("Unable to read cover image.")
        return SharedEpubCoverUpdate(bytes = bytes, extension = extension)
    }

    private fun coverExtension(uri: Uri): String? {
        val mimeExtension = when (context.contentResolver.getType(uri)?.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> null
        }
        return mimeExtension ?: DocumentFile.fromSingleUri(context, uri)?.name
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") }
    }

    private fun replaceUriBytes(uri: Uri, editedFile: File) {
        if (uri.scheme == "file") {
            val target = File(uri.path ?: error("Invalid file URI."))
            editedFile.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            editedFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to write EPUB source.")
    }

    private fun backupOriginalIfNeeded(item: RecentFileItem, sourceCopy: File): File {
        val backupFile = File(
            File(context.filesDir, "metadata_backups").apply { mkdirs() },
            "${item.bookId.toSafeBackupName()}.epub"
        )
        if (!backupFile.exists()) {
            sourceCopy.inputStream().use { input ->
                backupFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return backupFile
    }

    private fun queryFileSize(uri: Uri): Long {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).length() } ?: 0L
        } else {
            DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
        }
    }

    private fun queryLastModified(uri: Uri): Long {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).lastModified() } ?: 0L
        } else {
            DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0L
        }
    }
}

private fun java.io.InputStream.readBytesLimited(maxBytes: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        require(total <= maxBytes) { "Cover image is too large." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun String.toSafeBackupName(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "book" }
}