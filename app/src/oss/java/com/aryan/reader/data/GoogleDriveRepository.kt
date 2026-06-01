// src\oss
package com.aryan.reader.data

import android.content.Context
import android.content.Intent
import com.aryan.reader.FileType
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
    val name: String = "",
    val bookIds: List<String> = emptyList(),
    val lastModifiedTimestamp: Long = 0L,
    var isDeleted: Boolean = false
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

    suspend fun downloadFile(accessToken: String, fileId: String, destination: File): Boolean {
        return false
    }

    suspend fun deleteAllFiles(accessToken: String): Boolean {
        return false
    }

    suspend fun deleteDriveFile(accessToken: String, fileId: String): Boolean {
        return false
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
