package com.aryan.reader

import android.net.Uri
import androidx.core.net.toUri
import com.aryan.reader.data.RecentFileItem
import timber.log.Timber

class AndroidFolderPathResolver : FolderPathResolver {
    /**
     * A synced folder's tree document id comes from its `sourceFolderUri`, which every book in
     * that folder shares, so the same SAF path was being parsed and URI-decoded once per book on
     * every projection miss (search typing, sort changes, shelf edits). That showed up as the
     * allocating frame of an OOM report (`Uri.getPathSegments` -> `UriCodec.decode`), so the
     * folder-level part is memoized. `MainViewModel` keeps one resolver for its lifetime, so the
     * memo also covers later passes; it is bounded by the number of synced folders, not books.
     */
    private val rootDocumentIds = object : LinkedHashMap<String, String>(
        MAX_CACHED_FOLDERS,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, String>?
        ): Boolean = size > MAX_CACHED_FOLDERS
    }

    /** Count of root folder URIs actually parsed (test hook, as in `SharedProjectionBookItemCache`). */
    internal var rootDocumentIdParseCount: Int = 0
        private set

    override fun relativeFolderSegments(item: RecentFileItem): List<String> {
        val documentUriString = item.uriString ?: return emptyList()
        val rootFolderUriString = item.sourceFolderUri ?: return emptyList()

        return try {
            val rootDocId = cachedRootDocumentId(rootFolderUriString) ?: return emptyList()
            val documentId = documentUriString.toUri().documentIdOrNull() ?: return emptyList()

            val rootPath = rootDocId.substringAfter(':', "")
            val documentPath = documentId.substringAfter(':', "")
            val relativeDocumentPath = when {
                rootPath.isBlank() -> documentPath
                documentPath == rootPath -> ""
                documentPath.startsWith("$rootPath/") -> documentPath.removePrefix("$rootPath/")
                else -> documentPath
            }

            relativeDocumentPath
                .substringBeforeLast('/', "")
                .split('/')
                .map { Uri.decode(it).trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.tag("FolderShelves").w(e, "Failed to derive relative folder path for ${item.displayName}")
            emptyList()
        }
    }

    private fun cachedRootDocumentId(rootFolderUriString: String): String? =
        synchronized(rootDocumentIds) {
            rootDocumentIds[rootFolderUriString]?.let { return if (it.isEmpty()) null else it }
            rootDocumentIdParseCount++
            val resolved = rootFolderUriString.toUri().treeDocumentIdOrNull().orEmpty()
            rootDocumentIds[rootFolderUriString] = resolved
            resolved.ifEmpty { null }
        }

    private fun Uri.treeDocumentIdOrNull(): String? {
        val segments = pathSegments
        val treeIndex = segments.indexOf("tree")
        return segments.getOrNull(treeIndex + 1)
    }

    private fun Uri.documentIdOrNull(): String? {
        val segments = pathSegments
        val documentIndex = segments.indexOf("document")
        if (documentIndex >= 0) {
            return segments.getOrNull(documentIndex + 1)
        }
        return treeDocumentIdOrNull()
    }

    private companion object {
        const val MAX_CACHED_FOLDERS = 64
    }
}
