package com.aryan.reader

import android.net.Uri
import androidx.core.net.toUri
import com.aryan.reader.data.RecentFileItem
import timber.log.Timber

class AndroidFolderPathResolver : FolderPathResolver {
    override fun relativeFolderSegments(item: RecentFileItem): List<String> {
        val documentUriString = item.uriString ?: return emptyList()
        val rootFolderUriString = item.sourceFolderUri ?: return emptyList()

        return try {
            val documentUri = documentUriString.toUri()
            val rootFolderUri = rootFolderUriString.toUri()
            val rootDocId = rootFolderUri.treeDocumentIdOrNull() ?: return emptyList()
            val documentId = documentUri.documentIdOrNull() ?: return emptyList()

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
}
