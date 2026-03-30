package com.aryan.reader.opds

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.File

data class OpdsScreenState(
    val catalogs: List<OpdsCatalog> = emptyList(),
    val currentCatalog: OpdsCatalog? = null,
    val currentFeed: OpdsFeed? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isViewingCatalog: Boolean = false,
    val searchUrlTemplate: String? = null
)

class OpdsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpdsRepository(application)

    private val _uiState = MutableStateFlow(OpdsScreenState())
    val uiState: StateFlow<OpdsScreenState> = _uiState.asStateFlow()

    private val urlStack = mutableListOf<String>()

    private val _downloadingEntries = MutableStateFlow<Set<String>>(emptySet())
    val downloadingEntries: StateFlow<Set<String>> = _downloadingEntries.asStateFlow()

    private fun fetchUrl(url: String, isPagination: Boolean = false) {
        viewModelScope.launch {
            val catalog = _uiState.value.currentCatalog
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isViewingCatalog = true) }

            val result = repository.fetchFeed(url, catalog?.username, catalog?.password)
            result.onSuccess { newFeed ->
                val template = newFeed.searchUrl ?: _uiState.value.searchUrlTemplate
                if (!isPagination) {
                    if (urlStack.isEmpty() || urlStack.last() != url) {
                        urlStack.add(url)
                    }
                    _uiState.update { it.copy(isLoading = false, currentFeed = newFeed, searchUrlTemplate = template) }
                } else {
                    _uiState.update { state ->
                        val currentEntries = state.currentFeed?.entries ?: emptyList()
                        state.copy(
                            isLoading = false,
                            currentFeed = newFeed.copy(entries = currentEntries + newFeed.entries),
                            searchUrlTemplate = template
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load feed: ${e.message}") }
            }
        }
    }

    fun loadNextPage() {
        val nextUrl = _uiState.value.currentFeed?.nextUrl
        if (nextUrl != null && !_uiState.value.isLoading) {
            fetchUrl(nextUrl, isPagination = true)
        }
    }

    data class DownloadState(val isDownloading: Boolean, val progress: Float? = null)

    private val _downloadingState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadingState: StateFlow<Map<String, DownloadState>> = _downloadingState.asStateFlow()

    fun downloadBook(entry: OpdsEntry, acquisition: OpdsAcquisition, context: Context, onDownloaded: (Uri) -> Unit) {
        val downloadUrl = acquisition.url
        val catalog = _uiState.value.currentCatalog
        viewModelScope.launch(Dispatchers.IO) {
            _downloadingState.update { it + (entry.id to DownloadState(true, 0f)) }
            try {
                val client = repository.getAuthenticatedClient(catalog?.username, catalog?.password)
                val request = Request.Builder().url(downloadUrl).build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body ?: throw Exception("Empty body")
                    val contentLength = body.contentLength()

                    val ext = when (acquisition.formatName) {
                        "EPUB" -> ".epub"
                        "PDF" -> ".pdf"
                        "MOBI" -> ".mobi"
                        "FB2" -> ".fb2"
                        "CBZ" -> ".cbz"
                        "CBR" -> ".cbr"
                        "TXT" -> ".txt"
                        else -> ".epub"
                    }

                    val safeTitle = entry.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
                    val tempFile = File(context.cacheDir, "opds_dl_${safeTitle}$ext")

                    val input = body.byteStream()
                    val output = tempFile.outputStream()
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastProgressUpdate = System.currentTimeMillis()

                    input.use { inp ->
                        output.use { out ->
                            while (inp.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (contentLength > 0) {
                                    val now = System.currentTimeMillis()
                                    // Throttle UI updates to 4-5 fps
                                    if (now - lastProgressUpdate > 200) {
                                        val progress = totalRead.toFloat() / contentLength.toFloat()
                                        _downloadingState.update { it + (entry.id to DownloadState(true, progress)) }
                                        lastProgressUpdate = now
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onDownloaded(Uri.fromFile(tempFile))
                    }
                } else {
                    Timber.e("Download failed: ${response.code}")
                    _uiState.update { it.copy(errorMessage = "Download failed: ${response.message}") }
                }
            } catch (e: Exception) {
                Timber.e(e, "Download error")
                _uiState.update { it.copy(errorMessage = "Download error: ${e.message}") }
            } finally {
                _downloadingState.update { it - entry.id }
            }
        }
    }

    init {
        loadCatalogs()
    }

    private fun loadCatalogs() {
        _uiState.update { it.copy(catalogs = repository.getCatalogs()) }
    }

    fun addCatalog(title: String, url: String, username: String?, password: String?) {
        repository.addCatalog(title, url, username, password)
        loadCatalogs()
    }

    fun removeCatalog(id: String) {
        repository.removeCatalog(id)
        loadCatalogs()
    }

    fun openCatalog(catalog: OpdsCatalog) {
        urlStack.clear()
        _uiState.update { it.copy(searchUrlTemplate = null, currentCatalog = catalog) }
        fetchUrl(catalog.url)
    }

    fun openFeedUrl(url: String) {
        fetchUrl(url)
    }

    fun navigateBack(): Boolean {
        if (urlStack.size > 1) {
            urlStack.removeAt(urlStack.lastIndex)
            val previousUrl = urlStack.last()
            urlStack.removeAt(urlStack.lastIndex)
            fetchUrl(previousUrl)
            return true
        } else {
            urlStack.clear()
            _uiState.update { it.copy(isViewingCatalog = false, currentFeed = null, searchUrlTemplate = null, currentCatalog = null) }
            return false
        }
    }

    fun updateCatalog(id: String, title: String, url: String, username: String?, password: String?) {
        repository.updateCatalog(id, title, url, username, password)
        loadCatalogs()
    }

    fun search(query: String) {
        val searchLink = _uiState.value.searchUrlTemplate ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val template = if (!searchLink.contains("{searchTerms}")) {
                repository.getSearchTemplate(searchLink) ?: searchLink
            } else {
                searchLink
            }

            val finalUrl = if (template.contains("{searchTerms}")) {
                template.replace("{searchTerms}", Uri.encode(query))
            } else {
                val separator = if (template.contains("?")) "&" else "?"
                "$template${separator}query=${Uri.encode(query)}"
            }

            openFeedUrl(finalUrl)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}