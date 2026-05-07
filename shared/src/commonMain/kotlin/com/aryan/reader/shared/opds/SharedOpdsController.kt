package com.aryan.reader.shared.opds

class SharedOpdsController(
    private val repository: SharedOpdsRepository,
    private val idFactory: () -> String
) {
    private val urlStack = mutableListOf<String>()

    var state: SharedOpdsScreenState = SharedOpdsScreenState(catalogs = repository.loadCatalogs())
        private set

    fun reloadCatalogs(): SharedOpdsScreenState {
        state = state.copy(catalogs = repository.loadCatalogs())
        return state
    }

    fun addCatalog(title: String, url: String, username: String?, password: String?): SharedOpdsScreenState {
        val nextCatalogs = SharedOpdsCatalogs.addCatalog(
            catalogs = repository.loadCatalogs(),
            title = title,
            url = url,
            username = username,
            password = password,
            idFactory = idFactory
        )
        repository.saveCatalogs(nextCatalogs)
        state = state.copy(catalogs = nextCatalogs)
        return state
    }

    fun updateCatalog(id: String, title: String, url: String, username: String?, password: String?): SharedOpdsScreenState {
        val nextCatalogs = SharedOpdsCatalogs.updateCatalog(
            catalogs = repository.loadCatalogs(),
            id = id,
            title = title,
            url = url,
            username = username,
            password = password
        )
        repository.saveCatalogs(nextCatalogs)
        state = state.copy(
            catalogs = nextCatalogs,
            currentCatalog = state.currentCatalog?.let { current ->
                nextCatalogs.firstOrNull { it.id == current.id } ?: current
            }
        )
        return state
    }

    fun removeCatalog(id: String): SharedOpdsScreenState {
        val nextCatalogs = SharedOpdsCatalogs.removeCatalog(repository.loadCatalogs(), id)
        repository.saveCatalogs(nextCatalogs)
        state = state.copy(catalogs = nextCatalogs)
        return state
    }

    suspend fun openCatalog(catalog: OpdsCatalog, emit: (SharedOpdsScreenState) -> Unit) {
        urlStack.clear()
        state = state.copy(searchUrlTemplate = null, currentCatalog = catalog)
        fetchUrl(catalog.url, isPagination = false, emit = emit)
    }

    suspend fun openFeedUrl(url: String, emit: (SharedOpdsScreenState) -> Unit) {
        fetchUrl(url, isPagination = false, emit = emit)
    }

    suspend fun loadNextPage(emit: (SharedOpdsScreenState) -> Unit) {
        val nextUrl = state.currentFeed?.nextUrl ?: return
        if (state.isLoading) return
        fetchUrl(nextUrl, isPagination = true, emit = emit)
    }

    suspend fun navigateBack(emit: (SharedOpdsScreenState) -> Unit): Boolean {
        return if (urlStack.size > 1) {
            urlStack.removeAt(urlStack.lastIndex)
            val previousUrl = urlStack.removeAt(urlStack.lastIndex)
            fetchUrl(previousUrl, isPagination = false, emit = emit)
            true
        } else {
            urlStack.clear()
            state = state.copy(
                isViewingCatalog = false,
                currentFeed = null,
                searchUrlTemplate = null,
                currentCatalog = null
            )
            emit(state)
            false
        }
    }

    suspend fun search(query: String, emit: (SharedOpdsScreenState) -> Unit) {
        val searchLink = state.searchUrlTemplate ?: return
        if (query.isBlank()) return
        val catalog = state.currentCatalog
        state = state.copy(isLoading = true, errorMessage = null)
        emit(state)
        val finalUrl = runCatching {
            SharedOpdsSearch.buildSearchUrl(searchLink, query) { openSearchUrl ->
                repository.getSearchTemplate(openSearchUrl, catalog?.username, catalog?.password)
            }
        }.getOrElse { error ->
            state = state.copy(isLoading = false, errorMessage = "Failed to search catalog: ${error.message}")
            emit(state)
            return
        }
        fetchUrl(finalUrl, isPagination = false, emit = emit)
    }

    fun clearError(): SharedOpdsScreenState {
        state = state.copy(errorMessage = null)
        return state
    }

    fun updateDownloadState(entryId: String, downloadState: SharedOpdsDownloadState?): SharedOpdsScreenState {
        val nextMap = if (downloadState == null) {
            state.downloadingState - entryId
        } else {
            state.downloadingState + (entryId to downloadState)
        }
        state = state.copy(downloadingState = nextMap)
        return state
    }

    private suspend fun fetchUrl(
        url: String,
        isPagination: Boolean,
        emit: (SharedOpdsScreenState) -> Unit
    ) {
        val catalog = state.currentCatalog
        state = state.copy(isLoading = true, errorMessage = null, isViewingCatalog = true)
        emit(state)

        val result = repository.fetchFeed(url, catalog?.username, catalog?.password)
        result.onSuccess { newFeed ->
            val template = newFeed.searchUrl ?: state.searchUrlTemplate
            state = if (isPagination) {
                val currentEntries = state.currentFeed?.entries.orEmpty()
                state.copy(
                    isLoading = false,
                    currentFeed = newFeed.copy(entries = currentEntries + newFeed.entries),
                    searchUrlTemplate = template
                )
            } else {
                if (urlStack.isEmpty() || urlStack.last() != url) {
                    urlStack.add(url)
                }
                state.copy(
                    isLoading = false,
                    currentFeed = newFeed,
                    searchUrlTemplate = template
                )
            }
        }.onFailure { error ->
            state = state.copy(
                isLoading = false,
                errorMessage = "Failed to load feed: ${error.message ?: "unknown error"}"
            )
        }
        emit(state)
    }
}
