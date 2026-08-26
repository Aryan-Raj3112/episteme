package com.aryan.reader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
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
 * SAF scanner for cloud folders. It reads bytes only to hash them; no file is
 * imported into the Reader library or copied to app storage. A single provider
 * error makes the result incomplete, so a caller must not publish it as a
 * replacement manifest.
 */
object CloudFolderSafScanner {
    suspend fun scan(
        context: Context,
        rootUri: Uri,
        rootId: String,
        deviceId: String,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderSafScanResult {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: return CloudFolderSafScanResult(emptyList(), false, now, "SAF root is unavailable")
        if (!root.isDirectory) {
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
                firstError = firstError ?: "SAF listing failed: ${error.message.orEmpty()}"
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
                        firstError = firstError ?: "SAF permission was revoked while reading $normalizedPath"
                        continue
                    } catch (error: IOException) {
                        complete = false
                        firstError = firstError ?: "SAF file read failed for $normalizedPath"
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
                    firstError = firstError ?: "SAF returned an unknown entry type for $normalizedPath"
                }
            }
        }

        try {
            walk(root, "", 0)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            complete = false
            firstError = firstError ?: "SAF scan failed: ${error.message.orEmpty()}"
        }
        return CloudFolderSafScanResult(
            entries = entries.sortedWith(compareBy<CloudFolderSafEntry> { it.node.pathKey }.thenBy { it.node.nodeId }),
            complete = complete,
            scannedAt = now,
            errorMessage = firstError,
        )
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
