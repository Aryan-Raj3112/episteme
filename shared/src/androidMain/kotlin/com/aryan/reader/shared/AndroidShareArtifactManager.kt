package com.aryan.reader.shared

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Owns the short-lived files that Android sends to another application.
 *
 * The provider is deliberately scoped to [SHARE_ROOT_DIRECTORY]. Each share
 * gets its own UUID directory so a second chooser cannot overwrite or delete
 * the URI held by the first chooser. The chooser has no completion callback,
 * therefore files are retained until the bounded TTL rather than being
 * deleted immediately after launching it.
 */
object AndroidShareArtifactManager {
    const val SHARE_ROOT_DIRECTORY: String = "shared_files"
    const val REQUEST_DIRECTORY_PREFIX: String = "share-"
    const val DEFAULT_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

    private val invalidFileNameChars = Regex("[^A-Za-z0-9._-]+")
    private val requestIdPattern = Regex("[A-Za-z0-9-]{1,64}")

    data class Artifact(
        val requestId: String,
        val fileName: String,
        val uri: Uri,
    )

    /**
     * Creates one isolated share artifact. A failed write removes only this
     * request.
     */
    fun create(
        context: Context,
        requestedFileName: String,
        write: (OutputStream) -> Unit,
        nowMillis: Long = System.currentTimeMillis(),
    ): Artifact {
        val prepared = prepare(context, requestedFileName, nowMillis)
        try {
            FileOutputStream(prepared.file).use(write)
            return artifactFor(context, prepared)
        } catch (error: Throwable) {
            prepared.requestDirectory.deleteRecursively()
            throw error
        }
    }

    /** Variant for exporters whose writer needs to call suspend functions. */
    suspend fun createSuspending(
        context: Context,
        requestedFileName: String,
        write: suspend (OutputStream) -> Unit,
        nowMillis: Long = System.currentTimeMillis(),
    ): Artifact {
        val prepared = prepare(context, requestedFileName, nowMillis)
        val output = try {
            FileOutputStream(prepared.file)
        } catch (error: Throwable) {
            prepared.requestDirectory.deleteRecursively()
            throw error
        }
        return try {
            write(output)
            artifactFor(context, prepared)
        } catch (error: Throwable) {
            prepared.requestDirectory.deleteRecursively()
            throw error
        } finally {
            output.close()
        }
    }

    fun buildShareIntent(
        artifact: Artifact,
        mimeType: String,
        title: String = artifact.fileName,
        subject: String? = null,
    ): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, artifact.uri)
        putExtra(Intent.EXTRA_TITLE, title)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        clipData = ClipData.newRawUri(artifact.fileName, artifact.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Removes only expired direct children of the dedicated provider root.
     * Request ids in [activeRequestIds] are always retained. Canonical path
     * checks prevent a malformed or symlinked child from escaping the root.
     */
    fun sweep(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        activeRequestIds: Set<String> = emptySet(),
    ): Int {
        val root = File(context.cacheDir, SHARE_ROOT_DIRECTORY)
        if (!root.isDirectory) return 0
        val canonicalRoot = root.canonicalFile
        val cutoff = nowMillis - max(0L, ttlMillis)
        var deletedCount = 0
        root.listFiles().orEmpty().forEach { child ->
            val requestId = child.name.removePrefix(REQUEST_DIRECTORY_PREFIX)
                .takeIf { child.name.startsWith(REQUEST_DIRECTORY_PREFIX) }
            if (requestId != null && requestId in activeRequestIds) return@forEach
            if (child.lastModified() > cutoff) return@forEach
            if (child.canonicalFile.parentFile != canonicalRoot) return@forEach
            val deleted = if (child.isDirectory) child.deleteRecursively() else child.delete()
            if (deleted) deletedCount++
        }
        return deletedCount
    }

    fun requestDirectoryName(requestId: String): String {
        val normalized = requestId.trim()
        require(normalized.matches(requestIdPattern)) {
            "Invalid share request id"
        }
        return "$REQUEST_DIRECTORY_PREFIX$normalized"
    }

    fun sanitizeFileName(requestedFileName: String): String {
        val normalized = requestedFileName
            .trim()
            .replace(invalidFileNameChars, "_")
            .trim('_')
            .take(120)
        return normalized.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: "shared-file"
    }

    private data class PreparedArtifact(
        val requestId: String,
        val fileName: String,
        val requestDirectory: File,
        val file: File,
    )

    private fun prepare(
        context: Context,
        requestedFileName: String,
        nowMillis: Long,
    ): PreparedArtifact {
        val root = shareRoot(context)
        sweep(context, nowMillis = nowMillis)
        val requestId = UUID.randomUUID().toString()
        val requestDirectory = createRequestDirectory(root, requestId)
        val fileName = sanitizeFileName(requestedFileName)
        return PreparedArtifact(
            requestId = requestId,
            fileName = fileName,
            requestDirectory = requestDirectory,
            file = childWithin(requestDirectory, fileName),
        )
    }

    private fun artifactFor(context: Context, prepared: PreparedArtifact): Artifact {
        val uri = FileProvider.getUriForFile(
            context.applicationContext,
            "${context.packageName}.provider",
            prepared.file,
        )
        return Artifact(
            requestId = prepared.requestId,
            fileName = prepared.fileName,
            uri = uri,
        )
    }

    private fun shareRoot(context: Context): File =
        File(context.cacheDir, SHARE_ROOT_DIRECTORY).apply {
            check(isDirectory || mkdirs()) { "Unable to create share cache directory" }
        }

    private fun createRequestDirectory(root: File, requestId: String): File {
        val requestDirectory = File(root, requestDirectoryName(requestId))
        check(requestDirectory.parentFile?.canonicalFile == root.canonicalFile) {
            "Share request escaped its root"
        }
        check(requestDirectory.mkdir()) { "Unable to create share request directory" }
        check(requestDirectory.canonicalFile.parentFile == root.canonicalFile) {
            "Share request escaped its root"
        }
        return requestDirectory
    }

    private fun childWithin(parent: File, fileName: String): File {
        val child = File(parent, fileName)
        check(child.canonicalFile.parentFile == parent.canonicalFile) {
            "Share file escaped its request directory"
        }
        return child
    }
}
