// src\oss
package com.aryan.reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aryan.reader.FileType
import com.aryan.reader.shared.CloudFolderManifest
import java.io.InputStream
import java.io.OutputStream
import java.io.File

data class DriveFileList(
    val files: List<DriveFile> = emptyList()
)

data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTimeMillis: Long = 0L
)

data class ShelfMetadata(
    val shelfId: String = "",
    val name: String = "",
    val bookIds: List<String> = emptyList(),
    val lastModifiedTimestamp: Long = 0L,
    var isDeleted: Boolean = false,
    val legacyDocumentId: String = ""
)

class GoogleDriveRepository {

    fun hasDrivePermissions(context: Context): Boolean {
        return false
    }

    suspend fun getAccessToken(context: Context): String? {
        return null
    }

    suspend fun getFiles(accessToken: String): DriveFileList? {
        return DriveFileList(emptyList())
    }

    suspend fun getFilesOrThrow(accessToken: String): DriveFileList {
        return getFiles(accessToken) ?: error("Drive is unavailable in the OSS build")
    }

    suspend fun uploadAnnotationFile(accessToken: String, bookId: String, file: File): DriveFile? {
        return null
    }

    suspend fun downloadAnnotationFile(accessToken: String, bookId: String, destination: File): Boolean {
        return false
    }

    suspend fun uploadFont(accessToken: String, fileName: String, file: File, extension: String): DriveFile? {
        return null
    }

    suspend fun uploadFile(accessToken: String, bookId: String, file: File, type: FileType): DriveFile? {
        return null
    }

    suspend fun uploadCloudFolderFile(
        accessToken: String,
        rootId: String,
        nodeId: String,
        relativePath: String,
        mimeType: String?,
        input: InputStream,
        sizeBytes: Long = -1L,
        revision: Long = 0L,
        contentHash: String? = null,
    ): DriveFile? {
        input.close()
        return null
    }

    suspend fun uploadCloudFolderFileFromContentResolver(
        context: Context,
        accessToken: String,
        rootId: String,
        nodeId: String,
        relativePath: String,
        mimeType: String?,
        sourceUri: Uri,
        revision: Long = 0L,
        contentHash: String? = null,
    ): DriveFile? = null

    suspend fun uploadCloudFolderManifest(
        accessToken: String,
        manifest: CloudFolderManifest,
    ): DriveFile? = null

    suspend fun listCloudFolderManifestRefs(accessToken: String): List<CloudFolderManifestRef> = emptyList()

    suspend fun listCloudFolderObjectsForGarbageCollection(
        accessToken: String,
    ): List<CloudFolderDriveObjectRef> = emptyList()

    suspend fun deleteCloudFolderObject(
        accessToken: String,
        objectRef: CloudFolderDriveObjectRef,
    ): Boolean = false

    suspend fun downloadCloudFolderManifest(
        accessToken: String,
        rootId: String,
    ): CloudFolderManifestReadResult = CloudFolderManifestReadResult.NotFound

    suspend fun downloadCloudFolderFileTo(
        accessToken: String,
        fileId: String,
        output: OutputStream,
        expectedRootId: String,
        expectedNodeId: String,
        expectedRevision: Long,
        expectedContentHash: String,
        expectedSizeBytes: Long,
    ) {
        throw UnsupportedOperationException("Drive is unavailable in the OSS build")
    }

    suspend fun downloadFile(accessToken: String, fileId: String, destination: File): Boolean {
        return false
    }

    suspend fun deleteAllFiles(accessToken: String): Boolean {
        return false
    }

    suspend fun deleteDriveFile(accessToken: String, fileId: String): Boolean {
        return false
    }

    suspend fun deleteDriveFileOrThrow(accessToken: String, fileId: String) {
        check(deleteDriveFile(accessToken, fileId)) {
            "Drive is unavailable in the OSS build"
        }
    }

    fun getSignInIntent(context: Context): Intent {
        return Intent()
    }

    fun isUserSignedInToDrive(context: Context): Boolean {
        return false
    }

    fun handleSignInResult(data: Intent?): Boolean {
        return false
    }
}
