package com.aryan.reader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.aryan.reader.data.RecentFileItem
import timber.log.Timber

class AndroidFolderPathResolver(
    private val context: Context
) : FolderPathResolver {
    override fun relativeFolderSegments(item: RecentFileItem): List<String> {
        val documentUriString = item.uriString ?: return emptyList()
        val rootFolderUriString = item.sourceFolderUri ?: return emptyList()

        return try {
            val documentUri = documentUriString.toUri()
            val rootFolderUri = rootFolderUriString.toUri()
            val rootDocId = DocumentsContract.getTreeDocumentId(rootFolderUri)
            val documentId = when {
                DocumentsContract.isDocumentUri(context, documentUri) -> DocumentsContract.getDocumentId(documentUri)
                DocumentsContract.isTreeUri(documentUri) -> DocumentsContract.getTreeDocumentId(documentUri)
                else -> return emptyList()
            }

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
}
