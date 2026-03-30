package com.aryan.reader.opds

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

data class OpdsScreenState(
    val catalogs: List<OpdsCatalog> = emptyList(),
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

    private val httpClient = OkHttpClient()

    private val _downloadingEntries = MutableStateFlow<Set<String>>(emptySet())
    val downloadingEntries: StateFlow<Set<String>> = _downloadingEntries.asStateFlow()

    private fun fetchUrl(url: String, isPagination: Boolean = false) {
        viewModelScope.launch {
            Timber.tag("OpdsDebug").i("ViewModel requesting URL: $url (isPagination=$isPagination)")
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isViewingCatalog = true) }

            val result = repository.fetchFeed(url)
            result.onSuccess { newFeed ->
                Timber.tag("OpdsDebug").i("ViewModel received feed: ${newFeed.title} with ${newFeed.entries.size} entries")
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
                Timber.tag("OpdsDebug").e("ViewModel received error: ${e.message}")
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

    fun downloadBook(entry: OpdsEntry, context: Context, onDownloaded: (Uri) -> Unit) {
        val downloadUrl = entry.downloadUrl ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _downloadingEntries.update { it + entry.id }
            try {
                val request = Request.Builder().url(downloadUrl).build()
                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body ?: throw Exception("Empty body")

                    val ext = when {
                        entry.downloadMimeType?.contains("epub") == true -> ".epub"
                        entry.downloadMimeType?.contains("pdf") == true -> ".pdf"
                        entry.downloadMimeType?.contains("mobi") == true -> ".mobi"
                        entry.downloadMimeType?.contains("fictionbook") == true -> ".fb2"
                        entry.downloadMimeType?.contains("cbz") == true -> ".cbz"
                        downloadUrl.endsWith(".epub", ignoreCase = true) -> ".epub"
                        downloadUrl.endsWith(".pdf", ignoreCase = true) -> ".pdf"
                        downloadUrl.endsWith(".mobi", ignoreCase = true) -> ".mobi"
                        downloadUrl.endsWith(".fb2", ignoreCase = true) -> ".fb2"
                        downloadUrl.endsWith(".cbz", ignoreCase = true) -> ".cbz"
                        else -> ".epub"
                    }

                    val safeTitle = entry.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
                    val tempFile = File(context.cacheDir, "opds_dl_${safeTitle}$ext")

                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
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
                _downloadingEntries.update { it - entry.id }
            }
        }
    }

    init {
        loadCatalogs()
    }

    private fun loadCatalogs() {
        _uiState.update { it.copy(catalogs = repository.getCatalogs()) }
    }

    fun addCatalog(title: String, url: String) {
        repository.addCatalog(title, url)
        loadCatalogs()
    }

    fun removeCatalog(id: String) {
        repository.removeCatalog(id)
        loadCatalogs()
    }

    fun openCatalog(catalog: OpdsCatalog) {
        urlStack.clear()
        _uiState.update { it.copy(searchUrlTemplate = null) }
        fetchUrl(catalog.url)
    }

    fun openFeedUrl(url: String) {
        fetchUrl(url)
    }

    private fun fetchUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isViewingCatalog = true) }

            val result = repository.fetchFeed(url)
            result.onSuccess { feed ->
                if (urlStack.isEmpty() || urlStack.last() != url) {
                    urlStack.add(url)
                }
                _uiState.update { it.copy(isLoading = false, currentFeed = feed) }
            }.onFailure { e ->
                Timber.e(e, "Failed to load OPDS feed")
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load feed: ${e.message}") }
            }
        }
    }

    /**
     * Handles back navigation within the catalog.
     * Returns true if it navigated back internally, false if it reached the root (catalog list).
     */
    fun navigateBack(): Boolean {
        if (urlStack.size > 1) {
            urlStack.removeAt(urlStack.lastIndex)
            val previousUrl = urlStack.last()

            urlStack.removeAt(urlStack.lastIndex)
            fetchUrl(previousUrl)
            return true
        } else {
            urlStack.clear()
            _uiState.update { it.copy(isViewingCatalog = false, currentFeed = null, searchUrlTemplate = null) }
            return false
        }
    }

    fun search(query: String) {
        val template = _uiState.value.searchUrlTemplate ?: return
        val searchUrl = template.replace("{searchTerms}", Uri.encode(query))
        fetchUrl(searchUrl)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}