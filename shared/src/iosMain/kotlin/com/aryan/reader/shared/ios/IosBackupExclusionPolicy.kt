@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create

/**
 * iCloud backup policy for local file stores, the iOS counterpart of Android's
 * curated `backup_rules.xml` / `data_extraction_rules.xml` allowlist.
 *
 * Android keeps credentials and generated/caches out of backups while backing
 * up small user state (DB, prefs, annotations, fonts). On iOS the default is
 * the inverse — everything under Application Support and Documents is backed
 * up — so large, re-derivable media must be opted out explicitly:
 * - Imports/LocalFolders/Covers: managed copies of user-picked content; the
 *   source content lives outside the app, so backing both sides doubles
 *   backup cost (Android's cloud backup excludes book originals too).
 * - Documents/Audiobooks: re-derivable originals imported by the user.
 *
 * Small state (library snapshot JSON, PdfSidecars, MetadataBackups, Fonts)
 * stays backup-eligible, matching Android's include semantics.
 *
 * Safe to call repeatedly (idempotent); new files created later by the app in
 * these directories should re-run [apply] after bulk writes (e.g. on import).
 */
internal object IosBackupExclusionPolicy {

    private val excludedApplicationSupportDirectories = setOf(
        "Imports",
        "LocalFolders",
        "Covers"
    )

    private val excludedDocumentDirectories = setOf(
        "Audiobooks"
    )

    /** Idempotently applies the exclusion policy. Returns the number of items marked. */
    fun apply(): Int {
        val fileManager = NSFileManager.defaultManager
        var marked = 0
        applicationSupportRoot()?.let { root ->
            marked += excludeDirectories(fileManager, root, excludedApplicationSupportDirectories)
        }
        documentsRoot()?.let { root ->
            marked += excludeDirectories(fileManager, root, excludedDocumentDirectories)
        }
        return marked
    }

    private fun excludeDirectories(
        fileManager: NSFileManager,
        root: String,
        names: Set<String>
    ): Int {
        var marked = 0
        for (name in names) {
            val path = "$root/$name"
            if (!fileManager.fileExistsAtPath(path)) continue
            if (setBackupExclusion(path)) marked++
            marked += excludeDirectoryContents(fileManager, path)
        }
        return marked
    }

    private fun excludeDirectoryContents(fileManager: NSFileManager, directory: String): Int {
        var marked = 0
        val contents = fileManager.contentsOfDirectoryAtPath(directory, error = null) ?: return 0
        for (item in contents) {
            val itemPath = "$directory/$item"
            if (isDirectory(itemPath)) {
                if (setBackupExclusion(itemPath)) marked++
                marked += excludeDirectoryContents(fileManager, itemPath)
            } else {
                if (setBackupExclusion(itemPath)) marked++
            }
        }
        return marked
    }

    private fun setBackupExclusion(path: String): Boolean {
        val url = NSURL.fileURLWithPath(path) ?: return false
        return url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    private fun isDirectory(path: String): Boolean {
        val fileManager = NSFileManager.defaultManager
        return memScoped {
            val isDirectory = alloc<kotlinx.cinterop.BooleanVarOf<kotlin.Boolean>>()
            fileManager.fileExistsAtPath(path, isDirectory = isDirectory.ptr)
            isDirectory.value
        }
    }

    private fun applicationSupportRoot(): String? {
        return (NSFileManager.defaultManager.URLsForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomains = NSUserDomainMask
        ).firstOrNull() as? NSURL)?.path
    }

    private fun documentsRoot(): String? {
        return (NSFileManager.defaultManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask
        ).firstOrNull() as? NSURL)?.path
    }
}
