package com.aryan.reader

import android.os.ParcelFileDescriptor
import com.aryan.reader.pdf.ArchiveSupport
import com.aryan.reader.shared.SharedComicInfoMetadata
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.isComicInfoArchivePath
import com.aryan.reader.shared.parseSharedComicInfoXml
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

/**
 * Reads the `ComicInfo.xml` sidecar out of a comic archive.
 *
 * CBZ is a ZIP and needs nothing beyond the JDK. CBR/CB7/CBT go through
 * libarchive, which the app already ships for comic rendering, so this adds no
 * dependency — only the named-entry read `ArchiveDocumentWrapper` never needed.
 *
 * Deliberately returns metadata only. Comic covers stay owned by the page-0
 * thumbnail pipeline so a sidecar read can never mark a book "cover parsed"
 * without a cover.
 */
internal object ComicInfoArchiveReader {
    private const val MAX_SIDECAR_BYTES = 512 * 1024
    private const val ARCHIVE_BLOCK_SIZE = 64L * 1024L

    fun canRead(type: FileType): Boolean = SharedFileCapabilities.isComicArchive(type)

    /**
     * Returns null when the archive carries no sidecar.
     *
     * Throws when the archive itself cannot be read (corrupt archive, or the
     * libarchive native split is missing) so callers keep the book pending
     * instead of marking it "parsed" forever.
     */
    fun read(
        type: FileType,
        openStream: () -> InputStream?,
        openFileDescriptor: () -> ParcelFileDescriptor?,
        cacheDir: File
    ): SharedComicInfoMetadata? = when (type) {
        FileType.CBZ -> readZipSidecar(openStream)
        FileType.CBR,
        FileType.CB7,
        FileType.CBT -> readArchiveSidecar(openFileDescriptor, openStream, cacheDir)
        else -> null
    }

    private fun readZipSidecar(openStream: () -> InputStream?): SharedComicInfoMetadata? {
        openStream()?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    if (!entry.isDirectory && isComicInfoArchivePath(entry.name)) {
                        val bytes = zip.readBounded(MAX_SIDECAR_BYTES) ?: return null
                        return parseSharedComicInfoXml(bytes.toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                }
            }
        }
        return null
    }

    private fun readArchiveSidecar(
        openFileDescriptor: () -> ParcelFileDescriptor?,
        openStream: () -> InputStream?,
        cacheDir: File
    ): SharedComicInfoMetadata? {
        val descriptor = openFileDescriptor()
        if (descriptor != null && descriptor.statSize != 0L) {
            // A negative stat size means the provider handed back a pipe. RAR can
            // stream from it, but 7z keeps its header at the end, so try the
            // descriptor first and copy below only when it cannot be used.
            val fromDescriptor = descriptor.use { pfd ->
                runCatching {
                    readWithLibarchive { archive ->
                        Archive.readOpenFd(archive, pfd.fd, ARCHIVE_BLOCK_SIZE)
                    }
                }
            }
            fromDescriptor.getOrNull()?.let { return it }
            if (fromDescriptor.isSuccess) return null
            fromDescriptor.exceptionOrNull()?.let(::rethrowIfLibarchiveMissing)
        } else {
            descriptor?.close()
        }

        // Only reached for non-seekable providers or a descriptor libarchive
        // refused. One copy buys a correct read for every remaining provider.
        val temporaryArchive = File.createTempFile("comicinfo-", ".archive", cacheDir)
        return try {
            val copied = openStream()?.use { input ->
                temporaryArchive.outputStream().use { output -> input.copyTo(output) }
            } != null
            if (!copied) return null
            runCatching {
                readWithLibarchive { archive ->
                    Archive.readOpenFileName(
                        archive,
                        temporaryArchive.absolutePath.toByteArray(Charsets.UTF_8),
                        ARCHIVE_BLOCK_SIZE
                    )
                }
            }.getOrElse { throwable ->
                rethrowIfLibarchiveMissing(throwable)
                null
            }
        } finally {
            temporaryArchive.delete()
        }
    }

    private fun readWithLibarchive(openArchive: (Long) -> Unit): SharedComicInfoMetadata? {
        val archive = try {
            Archive.readNew()
        } catch (error: LinkageError) {
            ArchiveSupport.reportUnavailable(error)
            throw error
        }
        try {
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            openArchive(archive)
            while (true) {
                val entry = Archive.readNextHeader(archive)
                if (entry == 0L) return null
                val path = ArchiveEntry.pathnameUtf8(entry)
                if (path != null && isComicInfoArchivePath(path)) {
                    val size = ArchiveEntry.size(entry)
                    if (size <= 0L || size > MAX_SIDECAR_BYTES) return null
                    val bytes = readEntryBytes(archive, size.toInt()) ?: return null
                    return parseSharedComicInfoXml(bytes.toString(Charsets.UTF_8))
                }
                Archive.readDataSkip(archive)
            }
        } finally {
            runCatching { Archive.readFree(archive) }
        }
    }

    private fun readEntryBytes(archive: Long, size: Int): ByteArray? {
        val buffer = ByteBuffer.allocate(size)
        while (buffer.hasRemaining()) {
            val positionBeforeRead = buffer.position()
            Archive.readData(archive, buffer)
            if (buffer.position() == positionBeforeRead) break
        }
        if (buffer.position() == 0) return null
        return ByteArray(buffer.position()).also { bytes ->
            buffer.flip()
            buffer.get(bytes)
        }
    }

    /** A missing native split must surface, not read as "archive has no sidecar". */
    private fun rethrowIfLibarchiveMissing(throwable: Throwable) {
        if (throwable is LinkageError) {
            ArchiveSupport.reportUnavailable(throwable)
            throw IOException("libarchive is unavailable on this install", throwable)
        }
    }
}

private fun InputStream.readBounded(maxBytes: Int): ByteArray? {
    val buffer = ByteArray(16 * 1024)
    val collected = ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (collected.size() + read > maxBytes) return null
        collected.write(buffer, 0, read)
    }
    return collected.toByteArray()
}
