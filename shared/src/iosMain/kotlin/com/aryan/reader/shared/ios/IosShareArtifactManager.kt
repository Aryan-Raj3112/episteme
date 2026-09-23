@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.pdf.sanitizeSharedPdfExportFilename
import com.aryan.reader.shared.pdf.suggestSharedPdfExportFilename
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.timeIntervalSince1970

/**
 * Android benchmark (AndroidShareArtifactManager.filename + PdfViewerScreen suggested names):
 * iOS share/save copies are staged with sanitized suggested filenames
 * (`<base>[_annotated]_NNNN.pdf`) so the share sheet / document picker never exposes internal
 * library paths.
 *
 * Unlike Android (which needs per-request `shared_files/share-<UUID>/` dirs for its FileProvider),
 * iOS stages flat directly in `NSTemporaryDirectory()`: `UIActivityViewController` item loading
 * fails for files nested in `tmp/shared_files/share-<UUID>/` ("error fetching item for URL"),
 * while flat tmp files present reliably. Copies are retained with a 24h TTL (Android
 * `DEFAULT_TTL_MILLIS`; the share sheet and document picker have no completion callback).
 */
internal object IosShareArtifactManager {
    const val SHARE_ROOT_DIRECTORY: String = "shared_files"
    const val REQUEST_DIRECTORY_PREFIX: String = "share-"
    const val LEGACY_FLAT_PREFIX: String = "reader-export-"
    const val DEFAULT_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

    private val requestIdPattern = Regex("[A-Za-z0-9-]{1,64}")
    private val suggestedPdfNamePattern = Regex(".*_[0-9]{4}\\.pdf")

    data class Artifact(
        val fileName: String,
        val path: String,
    )

    fun sanitizeFileName(requestedFileName: String): String =
        sanitizeSharedPdfExportFilename(requestedFileName)

    fun suggestedFilename(displayName: String?, isAnnotated: Boolean): String =
        sanitizeFileName(suggestSharedPdfExportFilename(displayName, isAnnotated))

    fun prepare(
        displayName: String?,
        isAnnotated: Boolean,
        nowMillis: Long = currentTimeMillis(),
    ): Artifact {
        sweep(nowMillis = nowMillis)
        val tempRoot = NSTemporaryDirectory().trimEnd('/')
        // The 4-digit suffix makes collisions unlikely; retry with a fresh suffix if the flat
        // name is somehow taken instead of overwriting another in-flight share.
        repeat(10) {
            val fileName = suggestedFilename(displayName, isAnnotated)
            check(!fileName.contains("/")) {
                "Share file escaped its staging directory"
            }
            val path = "$tempRoot/$fileName"
            if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
                return Artifact(fileName, path)
            }
        }
        // Extremely unlikely (10 consecutive collisions): fall back to a UUID-suffixed name so
        // staging never overwrites another share and never throws out of the export flow.
        val fallback = sanitizeFileName(
            "${suggestSharedPdfExportFilename(displayName, isAnnotated).substringBeforeLast(".pdf")}_${currentTimeMillis()}.pdf",
        )
        return Artifact(fallback, "$tempRoot/$fallback")
    }

    fun discard(artifact: Artifact) {
        NSFileManager.defaultManager.removeItemAtPath(artifact.path, error = null)
    }

    fun sweep(
        nowMillis: Long = currentTimeMillis(),
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
    ): Int {
        val fileManager = NSFileManager.defaultManager
        val tempRoot = NSTemporaryDirectory().trimEnd('/')
        val cutoff = nowMillis - maxOf(0L, ttlMillis)
        var deleted = 0
        // Leftover isolated dirs from the nested staging layout (share-<UUID>/).
        val shareRoot = "$tempRoot/$SHARE_ROOT_DIRECTORY"
        if (fileManager.fileExistsAtPath(shareRoot, isDirectory = null)) {
            val children = fileManager.contentsOfDirectoryAtPath(shareRoot, error = null)
            @Suppress("UNCHECKED_CAST")
            (children as? List<String>).orEmpty().forEach { child ->
                if (!child.startsWith(REQUEST_DIRECTORY_PREFIX)) return@forEach
                val requestId = child.removePrefix(REQUEST_DIRECTORY_PREFIX)
                if (!requestId.matches(requestIdPattern)) return@forEach
                if (requestId.contains("/") || child.contains("/")) return@forEach
                val childPath = "$shareRoot/$child"
                if (isStale(childPath, cutoff)) {
                    if (fileManager.removeItemAtPath(childPath, error = null)) deleted++
                }
            }
        }
        // Flat staging files: legacy reader-export-* plus suggested <base>[_annotated]_NNNN.pdf.
        val entries = fileManager.contentsOfDirectoryAtPath(tempRoot, error = null)
        @Suppress("UNCHECKED_CAST")
        (entries as? List<String>).orEmpty().forEach { child ->
            if (child.contains("/")) return@forEach
            val matches = child.startsWith(LEGACY_FLAT_PREFIX) ||
                (child.endsWith(".pdf") && child.matches(suggestedPdfNamePattern))
            if (!matches) return@forEach
            if (isStale("$tempRoot/$child", cutoff)) {
                if (fileManager.removeItemAtPath("$tempRoot/$child", error = null)) deleted++
            }
        }
        return deleted
    }

    private fun isStale(path: String, cutoff: Long): Boolean {
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        val modified = (attributes?.get(NSFileModificationDate) as? NSDate)
            ?.timeIntervalSince1970?.times(1000L)?.toLong()
        // Missing mtime is treated as stale (crash leftover), matching Android's
        // lastModified()==0 files which fall before any cutoff.
        return modified == null || modified <= cutoff
    }

    private fun currentTimeMillis(): Long = currentTimestamp()
}
