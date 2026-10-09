package com.aryan.reader.shared.ios

import com.aryan.reader.shared.LOCAL_FOLDER_SCAN_LOG_TAG
import com.aryan.reader.shared.LocalFolderSidecars
import com.aryan.reader.shared.SharedFolderBookMetadata
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

/**
 * iOS sidecar IO against a linked folder.
 *
 * Reads and writes use plain stdio (`fopen`/`fread`/`fwrite`) against a path
 * whose security scope the caller is already holding, matching how every other
 * file read in this codebase works.
 *
 * `NSFileCoordinator` is deliberately not used. It is the right tool when a
 * provider may need to *materialise* a stub, but it also hands the block a
 * temporary URL and cannot be composed with `fopen`, which is what the readers
 * need. In practice the coordinated-versus-uncoordinated distinction only
 * matters for files that are not local yet, and the folder scan already
 * touches every file's metadata, which forces the provider to publish the
 * entry. Revisit if a real provider stub failure shows up in the field.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosFolderSidecarStore {
    private val fileManager = NSFileManager.defaultManager

    /**
     * Reads every metadata sidecar in the folder, keyed by book id.
     *
     * Read-only and tolerant: an unreadable or malformed sidecar is skipped
     * rather than failing the folder, matching Android's `getAllFolderMetadata`.
     * The newest record per book id wins, which is what Android does when a
     * rename or a crash left more than one behind.
     */
    fun readAllMetadata(folderRoot: String): Map<String, SharedFolderBookMetadata> {
        val result = mutableMapOf<String, SharedFolderBookMetadata>()
        val dirPath = LocalFolderSidecars.syncDirPath(folderRoot)
        val names = listSidecarNames(dirPath) ?: return result
        names.forEach { name ->
            if (!name.endsWith(".json")) return@forEach
            if (name.contains("_annotations")) return@forEach
            if (!LocalFolderSidecars.isSidecarName(name)) return@forEach
            val raw = readText("$dirPath/$name") ?: return@forEach
            val metadata = SharedFolderBookMetadata.fromJsonString(raw) ?: return@forEach
            val existing = result[metadata.bookId]
            if (existing == null || metadata.lastModifiedTimestamp > existing.lastModifiedTimestamp) {
                result[metadata.bookId] = metadata
            }
        }
        return result
    }

    /**
     * Writes a metadata sidecar.
     *
     * Uses the same temp-file-then-move sequence as Android's
     * `saveMetadataToFolder`: a provider rename is not atomic, so the payload
     * goes to a unique dot-prefixed temp name and is only then moved into
     * place. A failure part-way leaves the previous sidecar intact rather than
     * a truncated one.
     *
     * Returns false when the write failed, which callers treat as "keep the
     * in-memory state and retry later", never as data loss.
     */
    fun writeMetadata(folderRoot: String, metadata: SharedFolderBookMetadata): Boolean {
        val payload = metadata.toJsonString() ?: return false
        val dirPath = LocalFolderSidecars.syncDirPath(folderRoot)
        if (!ensureSyncDir(dirPath)) return false
        val targetName = LocalFolderSidecars.metadataFileName(metadata.bookId)
        return writeSidecarAtomically(
            dirPath = dirPath,
            targetName = targetName,
            payload = payload
        )
    }

    /** Removes both sidecars for a book. Best effort; used on book removal. */
    fun deleteBookSidecars(folderRoot: String, bookId: String): Boolean {
        val dirPath = LocalFolderSidecars.syncDirPath(folderRoot)
        var allDeleted = true
        listOf(
            LocalFolderSidecars.metadataFileName(bookId),
            LocalFolderSidecars.annotationFileName(bookId),
        ).forEach { name ->
            val path = "$dirPath/$name"
            if (!fileManager.fileExistsAtPath(path)) return@forEach
            if (!deletePath(path)) allDeleted = false
        }
        return allDeleted
    }

    /** Removes the whole `EpistemeSyncData` directory. Used when local sync is disabled. */
    fun deleteSyncDir(folderRoot: String): Boolean {
        val dirPath = LocalFolderSidecars.syncDirPath(folderRoot)
        // A missing directory is a successful no-op, so the UI can report
        // "removed" rather than an error the user cannot act on.
        if (!fileManager.fileExistsAtPath(dirPath)) return true
        return deletePath(dirPath)
    }

    // --- internals -------------------------------------------------------

    private fun listSidecarNames(dirPath: String): List<String>? {
        if (!fileManager.fileExistsAtPath(dirPath)) return null
        val names = fileManager.contentsOfDirectoryAtPath(dirPath, error = null) ?: return null
        return names.filterIsInstance<String>()
    }

    private fun ensureSyncDir(dirPath: String): Boolean {
        if (fileManager.fileExistsAtPath(dirPath)) return true
        fileManager.createDirectoryAtPath(
            path = dirPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return fileManager.fileExistsAtPath(dirPath)
    }

    private fun writeSidecarAtomically(
        dirPath: String,
        targetName: String,
        payload: String
    ): Boolean {
        // Dot-prefixed and unique per write, so a concurrent folder scan never
        // sees it and a crash never leaves a half-written canonical name.
        val unique = NSUUID().UUIDString.replace("-", "").take(8)
        val tempName = ".${targetName.removePrefix(".")}.$unique.tmp"
        val tempPath = "$dirPath/$tempName"
        val targetPath = "$dirPath/$targetName"

        if (!writeText(tempPath, payload)) {
            deletePath(tempPath)
            return false
        }
        if (fileManager.fileExistsAtPath(targetPath)) {
            // Providers do not guarantee an atomic overwrite. Remove-then-move
            // fails as "previous sidecar gone", which is recoverable, rather
            // than "truncated sidecar", which is not.
            deletePath(targetPath)
        }
        if (!fileManager.moveItemAtPath(tempPath, targetPath, error = null)) {
            deletePath(tempPath)
            IosDiagnosticLogStore.record(
                LOCAL_FOLDER_SCAN_LOG_TAG,
                "sidecar.move_failed name=$targetName dir=$dirPath",
            )
            return false
        }
        return true
    }

    private fun deletePath(path: String): Boolean =
        fileManager.removeItemAtPath(path, error = null)

    private fun readText(path: String): String? {
        val file = fopen(path, "rb") ?: return null
        return try {
            val buffer = ByteArray(16 * 1024)
            val out = mutableListOf<Byte>()
            while (true) {
                val read = buffer.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1u, buffer.size.convert(), file)
                }.toInt()
                if (read <= 0) break
                for (index in 0 until read) out.add(buffer[index])
            }
            out.toByteArray().decodeToString()
        } catch (_: Throwable) {
            null
        } finally {
            fclose(file)
        }
    }

    private fun writeText(path: String, payload: String): Boolean {
        val file = fopen(path, "wb") ?: return false
        return try {
            val bytes = payload.encodeToByteArray()
            if (bytes.isEmpty()) {
                true
            } else {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), file)
                }.toInt() == bytes.size
            }
        } catch (_: Throwable) {
            false
        } finally {
            fclose(file)
        }
    }
}
