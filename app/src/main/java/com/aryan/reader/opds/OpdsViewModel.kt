package com.aryan.reader.opds

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aryan.reader.FolderSyncWorker
import com.aryan.reader.R
import com.aryan.reader.shared.opds.SharedOpdsController
import com.aryan.reader.shared.opds.SharedOpdsDownloadLocation
import com.aryan.reader.shared.opds.SharedOpdsDownloadNamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.util.UUID

class OpdsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpdsRepository(application)
    private val controller = SharedOpdsController(
        repository = repository,
        feedLoadErrorMessage = { error ->
            application.getString(R.string.opds_error_load_feed, error.message.orEmpty())
        },
        idFactory = { UUID.randomUUID().toString() }
    )

    private val _uiState = MutableStateFlow(controller.state)
    val uiState: StateFlow<OpdsScreenState> = _uiState.asStateFlow()

    fun loadNextPage() {
        viewModelScope.launch {
            controller.loadNextPage(::emitState)
        }
    }

    fun downloadBook(
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
        context: Context,
        onDownloaded: (Uri) -> Unit,
        onDownloadedToFolder: (String) -> Unit
    ) {
        val downloadUrl = acquisition.url
        val catalog = _uiState.value.currentCatalog
        val destination = _uiState.value.downloadLocation
        viewModelScope.launch {
            updateDownloadState(entry.id, OpdsDownloadState(isDownloading = true, progress = 0f))
            try {
                val downloadResult = withContext(Dispatchers.IO) {
                    val client = repository.getAuthenticatedClient(catalog?.username, catalog?.password)
                    val request = Request.Builder().url(downloadUrl).build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw OpdsDownloadFailedException(
                                context.getString(R.string.opds_error_download_failed, response.message)
                            )
                        }

                        val body = response.body
                            ?: throw IllegalStateException(context.getString(R.string.opds_error_empty_response))
                        val contentLength = body.contentLength()
                        val ext = resolveOpdsDownloadExtension(acquisition, response)
                        val downloadDirectory = File(
                            context.cacheDir,
                            "opds_downloads/${entry.id.hashCode().toUInt().toString(16)}",
                        ).apply { mkdirs() }
                        val cleanFileName = SharedOpdsDownloadNamer.cleanFileName(entry.title, ext)
                        val tempFile = File(downloadDirectory, cleanFileName)

                        body.byteStream().use { input ->
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(8 * 1024)
                                var totalRead = 0L
                                var lastProgressUpdate = System.currentTimeMillis()

                                while (true) {
                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break
                                    output.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead

                                    if (contentLength > 0) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastProgressUpdate > 200) {
                                            val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                            withContext(Dispatchers.Main) {
                                                updateDownloadState(
                                                    entry.id,
                                                    OpdsDownloadState(isDownloading = true, progress = progress)
                                                )
                                            }
                                            lastProgressUpdate = now
                                        }
                                    }
                                }
                            }
                        }
                        tempFile
                    }
                }

                val savedToFolderName = destination
                    ?.takeIf { !it.isAppStorage }
                    ?.let { location -> saveToSyncedFolder(context, location, downloadResult, acquisition) }
                if (savedToFolderName == null) {
                    onDownloaded(Uri.fromFile(downloadResult))
                } else {
                    triggerFolderScan(context, destination?.folderUriString)
                    onDownloadedToFolder(savedToFolderName)
                }
            } catch (e: Exception) {
                Timber.e(e, "Download error")
                val message = if (e is OpdsDownloadFailedException) {
                    e.message.orEmpty()
                } else {
                    context.getString(R.string.opds_error_download_error, e.message.orEmpty())
                }
                emitState(controller.setErrorMessage(message))
            } finally {
                updateDownloadState(entry.id, null)
            }
        }
    }

    private fun saveToSyncedFolder(
        context: Context,
        location: SharedOpdsDownloadLocation,
        source: File,
        acquisition: OpdsAcquisition
    ): String? {
        val folderUriString = location.folderUriString ?: return null
        return runCatching {
            val folderUri = Uri.parse(folderUriString)
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.getOrThrow()
            val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return null
            if (!tree.isDirectory) return null
            val uniqueName = uniqueFolderFileName(tree, source.name)
            val mimeType = acquisition.mimeType.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            val created = tree.createFile(mimeType, uniqueName) ?: return null
            context.contentResolver.openOutputStream(created.uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return null
            source.delete()
            location.folderName?.takeIf { it.isNotBlank() } ?: tree.name.orEmpty()
        }.getOrNull()
    }

    private fun uniqueFolderFileName(tree: DocumentFile, preferredName: String): String {
        var candidate = preferredName
        var suffix = 1
        val stem = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        while (tree.findFile(candidate) != null) {
            candidate = if (extension == null) "${stem}_$suffix" else "${stem}_$suffix.$extension"
            suffix++
        }
        return candidate
    }

    private fun triggerFolderScan(context: Context, folderUriString: String?) {
        if (folderUriString.isNullOrBlank()) return
        val data = Data.Builder()
            .putBoolean(FolderSyncWorker.KEY_METADATA_ONLY, false)
            .putString(FolderSyncWorker.KEY_TARGET_FOLDER_URI, folderUriString)
            .build()
        val request = OneTimeWorkRequestBuilder<FolderSyncWorker>().setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FolderSyncWorker.WORK_NAME_ONETIME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun setDownloadLocation(location: SharedOpdsDownloadLocation) {
        emitState(controller.setDownloadLocation(location))
    }

    private fun resolveOpdsDownloadExtension(acquisition: OpdsAcquisition, response: Response): String {
        return SharedOpdsDownloadNamer.resolveExtension(
            acquisition = acquisition,
            contentDisposition = response.header("Content-Disposition"),
            urlPathSegment = Uri.parse(acquisition.url).lastPathSegment
        )
    }

    fun addCatalog(title: String, url: String, username: String?, password: String?) {
        emitState(controller.addCatalog(title, url, username, password))
    }

    fun removeCatalog(id: String) {
        emitState(controller.removeCatalog(id))
    }

    fun openCatalog(catalog: OpdsCatalog) {
        viewModelScope.launch {
            controller.openCatalog(catalog, ::emitState)
        }
    }

    fun openFeedUrl(url: String) {
        viewModelScope.launch {
            controller.openFeedUrl(url, ::emitState)
        }
    }

    fun navigateBack(): Boolean {
        val returnsToPreviousFeed = controller.hasFeedHistory()
        viewModelScope.launch {
            controller.navigateBack(::emitState)
        }
        return returnsToPreviousFeed
    }

    fun updateCatalog(id: String, title: String, url: String, username: String?, password: String?) {
        emitState(controller.updateCatalog(id, title, url, username, password))
    }

    fun search(query: String) {
        viewModelScope.launch {
            controller.search(query, ::emitState)
        }
    }

    fun clearError() {
        emitState(controller.clearError())
    }

    private fun updateDownloadState(entryId: String, downloadState: OpdsDownloadState?) {
        emitState(controller.updateDownloadState(entryId, downloadState))
    }

    private fun emitState(state: OpdsScreenState) {
        _uiState.value = state
    }

    private class OpdsDownloadFailedException(message: String) : Exception(message)
}
