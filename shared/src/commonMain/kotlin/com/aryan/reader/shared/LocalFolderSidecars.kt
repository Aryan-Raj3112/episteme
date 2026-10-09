package com.aryan.reader.shared

/**
 * Reads and writes the in-folder sidecars that carry a folder book's reading
 * state, so it survives a rescan, a device change, and a reinstall.
 *
 * The layout is a single `EpistemeSyncData/` directory beside the books, with
 * dot-prefixed, hash-keyed filenames. The prefix is what keeps the sidecars out
 * of the folder scan, so this is load-bearing rather than cosmetic:
 *
 * ```
 * <linked folder>/
 *   Book.epub
 *   EpistemeSyncData/
 *     .book_<sha256[0..12]>.json              metadata
 *     .book_<sha256[0..12]>_annotations.json  annotations
 * ```
 *
 * Filenames are derived from `bookId`, never from the book filename, so they
 * stay stable when a file is renamed or moved within the folder. The exact
 * names come from the shared helpers so Android, desktop and iOS cannot drift.
 *
 * This is deliberately filesystem-only. It takes a root path rather than
 * performing IO itself, so the platform layer owns the security scope,
 * coordination and provider materialisation. On iOS the root is a resolved
 * provider URL whose scope the caller must already be holding.
 */
object LocalFolderSidecars {
    const val SYNC_DATA_DIR: String = LOCAL_FOLDER_SYNC_DATA_DIR

    fun syncDirName(): String = LOCAL_FOLDER_SYNC_DATA_DIR

    fun syncDirPath(folderRoot: String): String =
        folderRoot.trimEnd('/') + "/" + LOCAL_FOLDER_SYNC_DATA_DIR

    fun metadataFileName(bookId: String): String = localFolderSyncMetadataFileName(bookId)

    fun annotationFileName(bookId: String): String = localFolderSyncAnnotationFileName(bookId)

    /**
     * Whether a scanned entry is a sidecar rather than a user document.
     *
     * Mirrors Android's scanner exclusions so a sidecar never becomes a book
     * and never triggers a delete for the book it describes.
     *
     * [folderRelativePath] is the path relative to the linked folder, so a
     * sidecar is identified by the `EpistemeSyncData` path component or by the
     * dot-prefixed hash name.
     */
    fun isSidecarName(folderRelativePath: String): Boolean {
        if (isInsideSyncDir(folderRelativePath)) return true
        val leaf = folderRelativePath.substringAfterLast('/')
        if (leaf.isEmpty()) return false
        if (!leaf.startsWith(".")) return false
        if (!leaf.endsWith(".json") && !leaf.endsWith(".tmp")) return false
        return leaf.startsWith(".$LOCAL_FOLDER_SIDECAR_HASH_PREFIX")
    }

    /**
     * A sidecar temp file is never a book, and neither is anything under the
     * sync directory at any depth.
     */
    fun isInsideSyncDir(folderRelativePath: String): Boolean =
        folderRelativePath.split('/').any { it == LOCAL_FOLDER_SYNC_DATA_DIR }
}
