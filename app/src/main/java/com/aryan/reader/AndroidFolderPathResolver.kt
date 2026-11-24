package com.aryan.reader

import android.net.Uri
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
            val documentId = documentIdOf(documentUriString) ?: return emptyList()

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
            val resolved = segmentAfter(rootFolderUriString, TREE_SEGMENT).orEmpty()
            rootDocumentIds[rootFolderUriString] = resolved
            resolved.ifEmpty { null }
        }

    private fun documentIdOf(documentUriString: String): String? =
        segmentAfter(documentUriString, DOCUMENT_SEGMENT)
            ?: segmentAfter(documentUriString, TREE_SEGMENT)

    /**
     * The decoded path segment that follows [marker] in [uriString], or `null` when there is none.
     *
     * Equivalent to `Uri.parse(uriString).pathSegments` bookkeeping, but without the `Uri`: a book's
     * document URI is unique per book, so unlike [rootDocumentIds] it cannot be memoized, and
     * `Uri.parse` allocates a `StringUri` while `getPathSegments` decodes *every* segment of the
     * path - once per book on every projection pass. That `Uri.parse` allocation is the frame an
     * OOM report died on, so the path is walked as an encoded string and only the segment we return
     * is decoded.
     *
     * A marker only matches as a whole path segment, and the walk stops at the query or fragment so
     * `?next=document/x` cannot match. SAF document ids percent-encode `:` and `/` but not letters,
     * so the marker itself appears literally in the encoded path.
     */
    private fun segmentAfter(uriString: String, marker: String): String? {
        val authorityStart = uriString.indexOf("//")
        if (authorityStart < 0) return null
        val pathStart = uriString.indexOf('/', authorityStart + 2)
        if (pathStart < 0) return null
        val pathEnd = uriString.indexOfAny(PATH_TERMINATORS, pathStart).let {
            if (it < 0) uriString.length else it
        }

        var match = uriString.indexOf(marker, pathStart + 1)
        while (match >= 0 && match + marker.length <= pathEnd) {
            val segmentEnd = match + marker.length
            val isWholeSegment = uriString[match - 1] == '/' &&
                (segmentEnd == pathEnd || uriString[segmentEnd] == '/')
            if (isWholeSegment) {
                if (segmentEnd == pathEnd) return null
                val valueEnd = uriString.indexOf('/', segmentEnd + 1).let {
                    if (it < 0 || it > pathEnd) pathEnd else it
                }
                return Uri.decode(uriString.substring(segmentEnd + 1, valueEnd))
            }
            match = uriString.indexOf(marker, match + 1)
        }
        return null
    }

    private companion object {
        const val MAX_CACHED_FOLDERS = 64
        const val TREE_SEGMENT = "tree"
        const val DOCUMENT_SEGMENT = "document"
        val PATH_TERMINATORS = charArrayOf('?', '#')
    }
}
