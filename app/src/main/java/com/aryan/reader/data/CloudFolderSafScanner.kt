package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.aryan.reader.cloudFolderErrorStatus
import com.aryan.reader.cloudFolderLogD
import com.aryan.reader.cloudFolderLogError
import com.aryan.reader.cloudFolderSafeId
import com.aryan.reader.cloudFolderSafeUri
import com.aryan.reader.shared.CloudFolderNode
import com.aryan.reader.shared.CloudFolderNodeKind
import com.aryan.reader.shared.cloudFolderNodeId
import com.aryan.reader.shared.normalizeCloudFolderRelativePath
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A scanned node plus its source URI; the URI never leaves this device. */
data class CloudFolderSafEntry(
    val uri: Uri,
    val node: CloudFolderNode,
)

data class CloudFolderSafScanResult(
    val entries: List<CloudFolderSafEntry>,
    val complete: Boolean,
    val scannedAt: Long,
    val errorMessage: String? = null,
) {
    val nodes: List<CloudFolderNode> get() = entries.map(CloudFolderSafEntry::node)
    val files: List<CloudFolderSafEntry> get() = entries.filter { it.node.isFile }
    val directories: List<CloudFolderSafEntry> get() = entries.filter { it.node.isDirectory }
}

/**
 * Fast device-local inventory used by the folder UI before cloud selection.
 * Unlike [scan], this never opens file streams or hashes bytes; it only
 * enumerates SAF children and reads provider metadata.
 */
data class CloudFolderSafInventoryResult(
    val fileCount: Int,
    val directoryCount: Int,
    val totalBytes: Long,
    val sizeComplete: Boolean,
    val complete: Boolean,
    val scannedAt: Long,
    val errorMessage: String? = null,
)

/**
 * SAF scanner for cloud folders. It reads bytes only to hash them; no file is
 * imported into the Reader library or copied to app storage. A single provider
 * error makes the result incomplete, so a caller must not publish it as a
 * replacement manifest.
 */
object CloudFolderSafScanner {
    suspend fun scanInventory(
        context: Context,
        rootUri: Uri,
        rootId: String,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderSafInventoryResult {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        val safeUri = cloudFolderSafeUri(rootUri)
        cloudFolderLogD("event=saf_inventory_start root=$safeRoot uri=$safeUri")
        val root = DocumentFile.fromTreeUri(context, rootUri)
        if (root == null || !root.isDirectory) {
            val message = if (root == null) "SAF root is unavailable" else "SAF root is not a directory"
            cloudFolderLogD(
                "event=saf_inventory_end root=$safeRoot uri=$safeUri complete=false " +
                    "files=0 directories=0 sizeComplete=false errorStatus=unavailable " +
                    "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
            return CloudFolderSafInventoryResult(0, 0, 0L, false, false, now, message)
        }

        var fileCount = 0
        var directoryCount = 0
        var totalBytes = 0L
        var sizeComplete = true
        var complete = true
        var firstError: String? = null
        val visited = mutableSetOf<String>()

        suspend fun walk(directory: DocumentFile) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(directory.uri.toString())) {
                complete = false
                firstError = firstError ?: "SAF provider returned a directory cycle"
                return
            }
            val children = try {
                directory.listFiles()
            } catch (_: SecurityException) {
                complete = false
                firstError = firstError ?: "SAF permission was revoked while listing"
                return
            } catch (error: RuntimeException) {
                complete = false
                firstError = firstError ?: "SAF listing failed (${cloudFolderErrorStatus(error)})"
                return
            }
            for (child in children) {
                currentCoroutineContext().ensureActive()
                when {
                    child.isDirectory -> {
                        directoryCount++
                        walk(child)
                    }
                    child.isFile -> {
                        fileCount++
                        val length = runCatching { child.length() }.getOrDefault(-1L)
                        if (length < 0L) {
                            sizeComplete = false
                        } else {
                            totalBytes = (totalBytes + length).coerceAtLeast(totalBytes)
                        }
                    }
                    else -> {
                        complete = false
                        firstError = firstError ?: "SAF returned an unknown entry type"
                    }
                }
            }
        }

        try {
            walk(root)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            complete = false
            firstError = firstError ?: "SAF inventory failed (${cloudFolderErrorStatus(error)})"
            cloudFolderLogError("saf_inventory_exception", error, "root=$safeRoot")
        }
        val result = CloudFolderSafInventoryResult(
            fileCount = fileCount,
            directoryCount = directoryCount,
            totalBytes = totalBytes,
            sizeComplete = sizeComplete,
            complete = complete,
            scannedAt = now,
            errorMessage = firstError,
        )
        cloudFolderLogD(
            "event=saf_inventory_end root=$safeRoot uri=$safeUri complete=${result.complete} " +
                "files=${result.fileCount} directories=${result.directoryCount} " +
                "sizeComplete=${result.sizeComplete} errorStatus=${cloudFolderErrorStatus(result.errorMessage)} " +
                "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        return result
    }

    suspend fun scan(
        context: Context,
        rootUri: Uri,
        rootId: String,
        deviceId: String,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderSafScanResult {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        val safeUri = cloudFolderSafeUri(rootUri)
        cloudFolderLogD(
            "event=saf_scan_start root=$safeRoot uri=$safeUri " +
                "device=${cloudFolderSafeId(deviceId)}",
        )
        val root = DocumentFile.fromTreeUri(context, rootUri)
        if (root == null) {
            cloudFolderLogD(
                "event=saf_scan_end root=$safeRoot uri=$safeUri complete=false " +
                    "files=0 directories=0 errorStatus=unavailable " +
                    "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
            return CloudFolderSafScanResult(emptyList(), false, now, "SAF root is unavailable")
        }
        if (!root.isDirectory) {
            cloudFolderLogD(
                "event=saf_scan_end root=$safeRoot uri=$safeUri complete=false " +
                    "files=0 directories=0 errorStatus=not_directory " +
                    "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
            return CloudFolderSafScanResult(emptyList(), false, now, "SAF root is not a directory")
        }

        val entries = mutableListOf<CloudFolderSafEntry>()
        val visited = mutableSetOf<String>()
        var complete = true
        var firstError: String? = null

        suspend fun walk(directory: DocumentFile, parentPath: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (depth > MAX_DEPTH) {
                complete = false
                firstError = firstError ?: "SAF folder depth exceeds $MAX_DEPTH"
                return
            }
            val directoryKey = directory.uri.toString()
            if (!visited.add(directoryKey)) {
                complete = false
                firstError = firstError ?: "SAF provider returned a directory cycle"
                return
            }
            val children = try {
                directory.listFiles()
            } catch (error: SecurityException) {
                complete = false
                firstError = firstError ?: "SAF permission was revoked while listing"
                return
            } catch (error: RuntimeException) {
                complete = false
                firstError = firstError ?: "SAF listing failed (${cloudFolderErrorStatus(error)})"
                return
            }
            for (child in children) {
                currentCoroutineContext().ensureActive()
                val name = child.name?.trim().orEmpty()
                if (name.isBlank()) {
                    complete = false
                    firstError = firstError ?: "SAF returned an unnamed entry"
                    continue
                }
                val path = if (parentPath.isBlank()) name else "$parentPath/$name"
                val normalizedPath = normalizeCloudFolderRelativePath(path)
                if (normalizedPath == null) {
                    complete = false
                    firstError = firstError ?: "SAF returned an unsafe path"
                    continue
                }
                val nodeId = stableNodeId(rootId, child.uri, normalizedPath)
                if (child.isDirectory) {
                    entries += CloudFolderSafEntry(
                        uri = child.uri,
                        node = CloudFolderNode(
                            nodeId = nodeId,
                            rootId = rootId,
                            relativePath = normalizedPath,
                            kind = CloudFolderNodeKind.DIRECTORY,
                            fileModifiedAt = child.lastModified().coerceAtLeast(0L),
                            revision = 0L,
                            modifiedAt = now,
                            modifiedByDeviceId = deviceId,
                        ),
                    )
                    walk(child, normalizedPath, depth + 1)
                } else if (child.isFile) {
                    val hashAndSize = try {
                        hashFile(context, child.uri)
                    } catch (error: SecurityException) {
                        complete = false
                        firstError = firstError ?: "SAF permission was revoked while reading"
                        continue
                    } catch (error: IOException) {
                        complete = false
                        firstError = firstError ?: "SAF file read failed (${cloudFolderErrorStatus(error)})"
                        continue
                    }
                    entries += CloudFolderSafEntry(
                        uri = child.uri,
                        node = CloudFolderNode(
                            nodeId = nodeId,
                            rootId = rootId,
                            relativePath = normalizedPath,
                            kind = CloudFolderNodeKind.FILE,
                            contentHash = hashAndSize.hash,
                            sizeBytes = hashAndSize.sizeBytes,
                            mimeType = child.type,
                            fileModifiedAt = child.lastModified().coerceAtLeast(0L),
                            revision = 0L,
                            modifiedAt = now,
                            modifiedByDeviceId = deviceId,
                        ),
                    )
                } else {
                    // Providers can return virtual, loading, or otherwise
                    // unknown document types. Treating one as absent would
                    // make a complete scan look smaller and could publish a
                    // manifest that later deletes a real remote entry.
                    complete = false
                    firstError = firstError ?: "SAF returned an unknown entry type"
                }
            }
        }

        try {
            walk(root, "", 0)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            complete = false
            firstError = firstError ?: "SAF scan failed (${cloudFolderErrorStatus(error)})"
            cloudFolderLogError(
                event = "saf_scan_exception",
                error = error,
                details = "root=$safeRoot",
            )
        }
        val result = CloudFolderSafScanResult(
            entries = entries.sortedWith(compareBy<CloudFolderSafEntry> { it.node.pathKey }.thenBy { it.node.nodeId }),
            complete = complete,
            scannedAt = now,
            errorMessage = firstError,
        )
        cloudFolderLogD(
            "event=saf_scan_end root=$safeRoot uri=$safeUri complete=${result.complete} " +
                "files=${result.files.size} directories=${result.directories.size} " +
                "errorStatus=${cloudFolderErrorStatus(result.errorMessage)} " +
                "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        return result
    }

    internal fun stableNodeId(rootId: String, uri: Uri, normalizedPath: String): String {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        // Document IDs normally survive a rename. The path fallback keeps the
        // scanner deterministic for providers that omit document IDs.
        return cloudFolderNodeId(rootId, documentId?.let { "document:$it" } ?: normalizedPath)
    }

    private data class HashAndSize(val hash: String, val sizeBytes: Long)

    private suspend fun hashFile(context: Context, uri: Uri): HashAndSize {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open SAF input stream")
        return input.use { source -> hashStream(source) }
    }

    private suspend fun hashStream(input: InputStream): HashAndSize {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            digest.update(buffer, 0, count)
            total += count
        }
        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        return HashAndSize("sha256:$hash", total)
    }

    private const val BUFFER_SIZE = 128 * 1024
    private const val MAX_DEPTH = 128
}
