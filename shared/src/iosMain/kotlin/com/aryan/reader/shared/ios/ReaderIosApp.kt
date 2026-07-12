@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.withMobileBookOpened
import com.aryan.reader.shared.withMobileImportedBooks
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.aryan.reader.shared.opds.SharedOpdsController
import com.aryan.reader.shared.opds.SharedOpdsDownloadState
import com.aryan.reader.shared.opds.SharedOpdsStreamUri
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfReaderStateSerializer
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedMobileAppDrawerContent
import com.aryan.reader.shared.ui.SharedMobileEpubReaderScreen
import com.aryan.reader.shared.ui.SharedMobilePdfReaderScreen
import com.aryan.reader.shared.ui.SharedMobileHomeScreen
import com.aryan.reader.shared.ui.SharedMobileHomeActions
import com.aryan.reader.shared.ui.SharedMobileLibraryScreen
import com.aryan.reader.shared.ui.SharedMobileLibraryTab
import com.aryan.reader.shared.ui.SharedMobileMainDestination
import com.aryan.reader.shared.ui.SharedMobileMainScaffold
import kotlinx.coroutines.launch
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import platform.UIKit.UIApplication

class ReaderIosBridge {
    private var systemUiHandler: ((hidden: Boolean, lightContent: Boolean, backgroundArgb: Long) -> Unit)? = null
    private var latestSystemUiState: Triple<Boolean, Boolean, Long>? = null
    internal var importedFiles by mutableStateOf<List<IosImportedFile>>(loadPersistedImportedFiles())
        private set

    internal var latestNativeEvent by mutableStateOf<String?>(null)
        private set

    fun recordImportedFiles(fileNames: List<String>, filePaths: List<String> = fileNames) {
        if (fileNames.isEmpty()) {
            latestNativeEvent = "Import cancelled"
            return
        }

        val imported = fileNames.mapIndexed { index, fileName ->
            IosImportedFile(
                name = fileName,
                path = filePaths.getOrNull(index) ?: fileName
            )
        }
        importedFiles = (imported + importedFiles).distinctBy { it.path }
        persistImportedFiles(importedFiles)
        latestNativeEvent = "Selected ${fileNames.size} file(s) from iOS"
    }

    fun recordNativeEvent(message: String) {
        latestNativeEvent = message
    }

    fun removeImportedFiles(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        importedFiles = importedFiles.filterNot { it.path in filePaths }
        persistImportedFiles(importedFiles)
        latestNativeEvent = "Removed ${filePaths.size} file(s) from iOS library"
    }

    fun setKeepScreenOn(enabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }

    fun setSystemUiHandler(handler: (hidden: Boolean, lightContent: Boolean, backgroundArgb: Long) -> Unit) {
        systemUiHandler = handler
        latestSystemUiState?.let { (hidden, lightContent, backgroundArgb) ->
            handler(hidden, lightContent, backgroundArgb)
        }
    }

    fun updateSystemUi(hidden: Boolean, lightContent: Boolean, backgroundArgb: Long) {
        latestSystemUiState = Triple(hidden, lightContent, backgroundArgb)
        systemUiHandler?.invoke(hidden, lightContent, backgroundArgb)
    }
}

data class IosImportedFile(
    val name: String,
    val path: String
)

private const val IosImportedFilesDefaultsKey = "reader_ios_imported_files_v1"
private const val IosImportsRelativePrefix = "Imports/"
private const val IosDocumentsRelativePrefix = "Documents/"
private const val IosPdfReaderStateDefaultsPrefix = "reader_ios_pdf_state_v1_"
private const val IosEpubReaderStateDefaultsPrefix = "reader_ios_epub_state_v1_"

private fun loadPersistedImportedFiles(): List<IosImportedFile> {
    val encoded = NSUserDefaults.standardUserDefaults.stringForKey(IosImportedFilesDefaultsKey) ?: return emptyList()
    return encoded
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.splitEscapedTab()
            if (parts.size != 2) return@mapNotNull null
            val name = parts[0].unescapePersistedValue()
            val resolvedPath = parts[1].unescapePersistedValue().resolvedIosImportedFilePath()
            IosImportedFile(name = name, path = resolvedPath)
        }
        .distinctBy { it.path }
        .toList()
}

private fun persistImportedFiles(files: List<IosImportedFile>) {
    val encoded = files.joinToString("\n") { file ->
        "${file.name.escapePersistedValue()}\t${file.path.stableIosImportedFilePath().escapePersistedValue()}"
    }
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = IosImportedFilesDefaultsKey)
}

private fun String.resolvedIosImportedFilePath(): String {
    if (startsWith(IosImportsRelativePrefix)) {
        return iosImportsDirectoryPath()?.let { importsPath ->
            "$importsPath/${substringAfter(IosImportsRelativePrefix)}"
        } ?: this
    }
    if (startsWith(IosDocumentsRelativePrefix)) {
        return iosDocumentsDirectoryPath()?.let { documentsPath ->
            "$documentsPath/${substringAfter(IosDocumentsRelativePrefix)}"
        } ?: this
    }
    if (NSFileManager.defaultManager.fileExistsAtPath(this)) return this
    val importedFileName = substringAfterLast('/').takeIf { it.isNotBlank() } ?: return this
    return listOfNotNull(
        iosImportsDirectoryPath()?.let { "$it/$importedFileName" },
        iosDocumentsDirectoryPath()?.let { "$it/$importedFileName" }
    ).firstOrNull(NSFileManager.defaultManager::fileExistsAtPath) ?: this
}

private fun String.stableIosImportedFilePath(): String {
    val importedFileName = substringAfterLast('/').takeIf { it.isNotBlank() } ?: return this
    val importsPath = iosImportsDirectoryPath() ?: return this
    return if (startsWith("$importsPath/")) {
        IosImportsRelativePrefix + importedFileName
    } else {
        val documentsPath = iosDocumentsDirectoryPath()
        if (documentsPath != null && startsWith("$documentsPath/")) {
            IosDocumentsRelativePrefix + importedFileName
        } else {
            this
        }
    }
}

private fun iosDocumentsDirectoryPath(): String? {
    return (NSFileManager.defaultManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL)?.path
}

private fun iosImportsDirectoryPath(): String? {
    val appSupport = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL
    val importsDirectory = appSupport?.URLByAppendingPathComponent("Imports", isDirectory = true)
    importsDirectory?.path?.let { path ->
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }
    return importsDirectory?.path
}

private fun loadPersistedIosPdfReaderState(book: BookItem): SharedPdfReaderState? {
    val encoded = NSUserDefaults.standardUserDefaults.stringForKey(book.iosPdfReaderStateKey()) ?: return null
    return SharedPdfReaderStateSerializer.decode(
        raw = encoded,
        fallbackPageCount = 1,
        fallbackPageIndex = book.lastPageIndex ?: 0
    )
}

private fun persistIosPdfReaderState(book: BookItem, state: SharedPdfReaderState) {
    val encoded = SharedPdfReaderStateSerializer.encode(state)
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = book.iosPdfReaderStateKey())
}

private fun BookItem.iosPdfReaderStateKey(): String {
    return IosPdfReaderStateDefaultsPrefix + (path ?: id).normalizedId()
}

private fun loadPersistedIosEpubBookState(book: BookItem): BookItem {
    val encoded = NSUserDefaults.standardUserDefaults.stringForKey(book.iosEpubReaderStateKey()) ?: return book
    val stored = SharedLibrarySnapshotJson.decodeOrEmpty(encoded).books.firstOrNull() ?: return book
    return book.copy(
        title = stored.title ?: book.title,
        author = stored.author ?: book.author,
        progressPercentage = stored.progressPercentage ?: book.progressPercentage,
        lastPageIndex = stored.lastPageIndex ?: book.lastPageIndex,
        readerPosition = stored.readerPosition ?: book.readerPosition,
        readerSettings = stored.readerSettings ?: book.readerSettings,
        readerBookmarks = stored.readerBookmarks,
        readerHighlights = stored.readerHighlights,
        readingPositionModifiedTimestamp = stored.readingPositionModifiedTimestamp
    )
}

private fun persistIosEpubBookState(book: BookItem) {
    val encoded = SharedLibrarySnapshotJson.encode(SharedLibrarySnapshot(books = listOf(book)))
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = book.iosEpubReaderStateKey())
}

private fun BookItem.iosEpubReaderStateKey(): String {
    return IosEpubReaderStateDefaultsPrefix + id.normalizedId()
}

private fun String.escapePersistedValue(): String {
    return buildString {
        this@escapePersistedValue.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                else -> append(char)
            }
        }
    }
}

private fun String.unescapePersistedValue(): String {
    return buildString {
        var index = 0
        while (index < this@unescapePersistedValue.length) {
            val char = this@unescapePersistedValue[index]
            if (char == '\\' && index + 1 < this@unescapePersistedValue.length) {
                when (val next = this@unescapePersistedValue[index + 1]) {
                    '\\' -> append('\\')
                    't' -> append('\t')
                    'n' -> append('\n')
                    else -> append(next)
                }
                index += 2
            } else {
                append(char)
                index += 1
            }
        }
    }
}

private fun String.splitEscapedTab(): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '\\' && index + 1 < length) {
            current.append(char)
            current.append(this[index + 1])
            index += 2
        } else if (char == '\t') {
            parts += current.toString()
            current.clear()
            index += 1
        } else {
            current.append(char)
            index += 1
        }
    }
    parts += current.toString()
    return parts
}

fun readerComposeViewController(
    bridge: ReaderIosBridge,
    onImportBooks: () -> Unit
): UIViewController = ComposeUIViewController {
    ReaderIosApp(
        bridge = bridge,
        onImportBooks = onImportBooks
    )
}

@Composable
private fun ReaderIosApp(
    bridge: ReaderIosBridge,
    onImportBooks: () -> Unit
) {
    var state by remember {
        mutableStateOf(SharedReaderScreenState())
    }
    var selectedPage by remember { mutableStateOf(SharedMobileMainDestination.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(SharedMobileLibraryTab.BOOKS) }
    var activeReaderBook by remember { mutableStateOf<BookItem?>(null) }
    val opdsRepository = remember { IosOpdsRepository() }
    val opdsController = remember {
        SharedOpdsController(
            repository = opdsRepository,
            idFactory = { IosOpdsCatalogIds.next() }
        )
    }
    var opdsState by remember { mutableStateOf(opdsController.state) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        state = state.withMessage(message)
        bridge.recordNativeEvent(message)
    }

    fun runDrawerAction(action: () -> Unit) {
        action()
        scope.launch { drawerState.close() }
    }

    fun openLibraryBook(book: BookItem) {
        state = state.withMobileBookOpened(book)
        if (book.type == FileType.PDF || book.type == FileType.EPUB) {
            activeReaderBook = book
            return
        }
        state = state.copy(
            bannerMessage = BannerMessage("Opening ${book.cardTitle()} comes next")
        )
    }

    fun addBooksToLibrary(books: List<BookItem>, message: String? = null) {
        val result = state.withMobileImportedBooks(books, message)
        if (result.addedBooks.isEmpty()) return
        state = result.state.withIosImportsFolder(result.addedBooks)
        selectedPage = SharedMobileMainDestination.LIBRARY
        selectedLibraryTab = SharedMobileLibraryTab.BOOKS
    }

    LaunchedEffect(bridge.importedFiles) {
        val importedBooks = bridge.importedFiles.toImportedBooks(existingBooks = state.rawLibraryBooks)
        if (importedBooks.isNotEmpty()) {
            val importedCount = importedBooks
                .distinctBy { it.id }
                .count { book -> state.rawLibraryBooks.none { it.id == book.id } }
            if (importedCount > 0) {
                addBooksToLibrary(importedBooks, "Added $importedCount import(s)")
            }
        }
    }

    SharedAppTheme(
        appThemeMode = state.appThemeMode,
        appContrastOption = state.appContrastOption,
        appTextDimFactorLight = state.appTextDimFactorLight,
        appTextDimFactorDark = state.appTextDimFactorDark,
        appSeedColor = state.appSeedColor
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            activeReaderBook?.let { book ->
                when (book.type) {
                    FileType.PDF -> {
                        val initialPdfReaderState = remember(book.id) { loadPersistedIosPdfReaderState(book) }
                        SharedMobilePdfReaderScreen(
                            book = book,
                            onBack = { activeReaderBook = null },
                            onNativePdfBridgeNeeded = { pdfBook ->
                                showMessage("${pdfBook.displayName}: page ${book.lastPageIndex?.plus(1) ?: 1}")
                            },
                            initialReaderState = initialPdfReaderState,
                            onReaderStateChange = { pdfState ->
                                persistIosPdfReaderState(book, pdfState)
                                val updatedBook = book.withIosPdfReaderProgress(pdfState)
                                if (updatedBook != book) {
                                    activeReaderBook = updatedBook
                                    state = state.withUpdatedIosBook(updatedBook)
                                }
                            },
                            onKeepScreenOnChange = bridge::setKeepScreenOn,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    FileType.EPUB -> {
                        val readerBook = remember(book.id) { loadPersistedIosEpubBookState(book) }
                        SharedMobileEpubReaderScreen(
                            book = readerBook,
                            onBack = { activeReaderBook = null },
                            onReaderStateChange = { snapshot ->
                                val updatedBook = book.copy(
                                    progressPercentage = snapshot.progressPercent,
                                    lastPageIndex = snapshot.pageIndex,
                                    readerPosition = snapshot.locator,
                                    readerSettings = snapshot.settings,
                                    readerBookmarks = snapshot.bookmarks,
                                    readerHighlights = snapshot.highlights,
                                    readingPositionModifiedTimestamp = currentTimestamp()
                                )
                                persistIosEpubBookState(updatedBook)
                                activeReaderBook = updatedBook
                                state = state.withUpdatedIosBook(updatedBook)
                            },
                            onMetadataLoaded = { title, author ->
                                val updatedBook = readerBook.copy(
                                    title = title.ifBlank { readerBook.title },
                                    author = author ?: readerBook.author
                                )
                                persistIosEpubBookState(updatedBook)
                                activeReaderBook = updatedBook
                                state = state.withUpdatedIosBook(updatedBook)
                            },
                            onKeepScreenOnChange = bridge::setKeepScreenOn,
                            onSystemUiAppearanceChange = bridge::updateSystemUi,
                            customReaderThemes = state.customReaderThemes,
                            onCustomReaderThemesChange = { themes ->
                                state = state.reduce(AppAction.CustomReaderThemesChanged(themes))
                            },
                            readerDefaultSettings = state.readerDefaultSettings,
                            onReaderDefaultSettingsChange = { defaults ->
                                state = state.reduce(AppAction.ReaderDefaultSettingsChanged(defaults))
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> Unit
                }
                return@Surface
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    SharedMobileAppDrawerContent(
                        currentUser = state.currentUser,
                        isProUser = false,
                        isStandardEdition = true,
                        credits = state.credits,
                        isSyncEnabled = state.isSyncEnabled,
                        isFolderSyncEnabled = state.isFolderSyncEnabled,
                        onSignInClick = { runDrawerAction { showMessage("Sign-in bridge is next for iOS") } },
                        onSignOutClick = { runDrawerAction { showMessage("Sign-out bridge is next for iOS") } },
                        onSyncToggle = { enabled -> state = state.reduce(AppAction.SyncEnabledChanged(enabled)) },
                        onFolderSyncToggle = { enabled -> state = state.reduce(AppAction.FolderSyncEnabledChanged(enabled)) },
                        onProClick = { runDrawerAction { showMessage("Standard iOS version is active") } },
                        onFontsClick = { runDrawerAction { showMessage("Font importer bridge is next for iOS") } },
                        onAiSettingsClick = { runDrawerAction { showMessage("AI settings bridge is next for iOS") } },
                        onSettingsClick = { runDrawerAction { showMessage("Settings bridge is next for iOS") } },
                        onAppThemeClick = { runDrawerAction { showMessage("App theme panel is next for iOS") } },
                        onFeedbackClick = { runDrawerAction { showMessage("Feedback bridge is next for iOS") } }
                    )
                }
            ) {
                SharedMobileMainScaffold(
                    selectedDestination = selectedPage,
                    onDestinationSelected = { page ->
                        if (selectedPage != page) {
                            state = state.copy(selectedBookIds = emptySet())
                        }
                        selectedPage = page
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedPage) {
                            SharedMobileMainDestination.HOME -> SharedMobileHomeScreen(
                                state = state,
                                actions = object : SharedMobileHomeActions {
                                    override fun importBooks() = onImportBooks()
                                    override fun openBook(book: BookItem) = openLibraryBook(book)
                                    override fun longPressBook(book: BookItem) {
                                        state = state.toggleBookSelection(book.id)
                                    }
                                    override fun openDrawer() {
                                        scope.launch { drawerState.open() }
                                    }
                                    override fun openSearch() {
                                        selectedPage = SharedMobileMainDestination.LIBRARY
                                        state = state.copy(isSearchActive = true)
                                    }
                                    override fun navigateToFolderSync() {
                                        selectedPage = SharedMobileMainDestination.LIBRARY
                                    }
                                    override fun refresh() = showMessage("Refresh bridge is next for iOS")
                                    override fun clearSelection() {
                                        state = state.copy(selectedBookIds = emptySet())
                                    }
                                    override fun selectAll() {
                                        state = state.copy(selectedBookIds = state.recentBooks.mapTo(mutableSetOf()) { it.id })
                                    }
                                    override fun closeTab(book: BookItem) {
                                        state = state.closeTab(book.id)
                                    }
                                    override fun closeAllTabs() {
                                        state = state.copy(openTabIds = emptyList(), activeTabBookId = null)
                                    }
                                    override fun togglePinned(book: BookItem) {
                                        state = state.toggleHomePinned(book.id)
                                    }
                                    override fun deleteSelectedBooks() {
                                        val removed = state.selectedBookIds
                                        bridge.removeImportedFiles(
                                            state.rawLibraryBooks.filter { it.id in removed }.mapNotNull { it.path }
                                        )
                                        state = state.removeIosBooks(removed)
                                    }
                                    override fun createShelfFromSelectedBooks(name: String) {
                                        state = state.createIosShelf(name, state.selectedBookIds)
                                        selectedPage = SharedMobileMainDestination.LIBRARY
                                        selectedLibraryTab = SharedMobileLibraryTab.SHELVES
                                    }
                                    override fun openSettings() = showMessage("Settings bridge is next for iOS")
                                    override fun openMoreActions() = showMessage("More actions bridge is next for iOS")
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            SharedMobileMainDestination.LIBRARY -> SharedMobileLibraryScreen(
                                state = state,
                                selectedTab = selectedLibraryTab,
                                onTabChange = { selectedLibraryTab = it },
                                opdsState = opdsState,
                                onImportBooks = onImportBooks,
                                onOpenBook = ::openLibraryBook,
                                onLongPressBook = { book -> state = state.toggleBookSelection(book.id) },
                                onSearchQueryChange = { query -> state = state.reduce(LibraryAction.SearchChanged(query)) },
                                onSearchActiveChange = { active ->
                                    state = state.copy(
                                        isSearchActive = active,
                                        searchQuery = if (active) state.searchQuery else ""
                                    )
                                },
                                onSortOrderChange = { sortOrder -> state = state.reduce(LibraryAction.SortChanged(sortOrder)) },
                                onClearSelection = {
                                    state = state.copy(selectedBookIds = emptySet(), selectedShelfIds = emptySet())
                                },
                                onSelectAll = {
                                    state = state.copy(selectedBookIds = state.libraryBooks.mapTo(mutableSetOf()) { it.id })
                                },
                                onFilterClick = {},
                                onClearFilters = { state = state.reduce(LibraryAction.FiltersChanged(LibraryFilters())) },
                                onRemoveFilters = { filters -> state = state.reduce(LibraryAction.FiltersChanged(filters)) },
                                onSettingsClick = { showMessage("Settings bridge is next for iOS") },
                                onNewShelfClick = {},
                                onOpenShelf = { shelf -> state = state.copy(viewingShelfId = shelf.id) },
                                onLongPressShelf = { shelf -> state = state.reduce(LibraryAction.ShelfSelectionToggled(shelf.id)) },
                                onTogglePinned = { book -> state = state.toggleLibraryPinned(book.id) },
                                onCreateShelf = { name, bookIds ->
                                    state = state.createIosShelf(name, bookIds)
                                },
                                onDeleteBooks = { bookIds ->
                                    bridge.removeImportedFiles(
                                        state.rawLibraryBooks.filter { it.id in bookIds }.mapNotNull { it.path }
                                    )
                                    state = state.removeIosBooks(bookIds)
                                },
                                onDeleteShelves = { shelfIds ->
                                    state = state.copy(
                                        shelves = state.shelves.filterNot { it.id in shelfIds },
                                        selectedShelfIds = emptySet(),
                                        viewingShelfId = state.viewingShelfId?.takeUnless { it in shelfIds }
                                    )
                                },
                                onNavigateShelfBack = { state = state.copy(viewingShelfId = null) },
                                onOpenCatalog = { catalog ->
                                    scope.launch {
                                        opdsController.openCatalog(catalog) { opdsState = it }
                                    }
                                },
                                onOpenFeedUrl = { url ->
                                    scope.launch {
                                        opdsController.openFeedUrl(url) { opdsState = it }
                                    }
                                },
                                onOpdsNavigateBack = {
                                    scope.launch {
                                        opdsController.navigateBack { opdsState = it }
                                    }
                                },
                                onOpdsSearch = { query ->
                                    scope.launch {
                                        opdsController.search(query) { opdsState = it }
                                    }
                                },
                                onOpdsLoadNextPage = {
                                    scope.launch {
                                        opdsController.loadNextPage { opdsState = it }
                                    }
                                },
                                onAddCatalog = { title, url, username, password ->
                                    opdsState = opdsController.addCatalog(title, url, username, password)
                                },
                                onUpdateCatalog = { id, title, url, username, password ->
                                    opdsState = opdsController.updateCatalog(id, title, url, username, password)
                                },
                                onRemoveCatalog = { catalog ->
                                    opdsState = opdsController.removeCatalog(catalog.id)
                                },
                                onDownloadOpdsBook = { entry, acquisition ->
                                    scope.launch {
                                        opdsState = opdsController.updateDownloadState(
                                            entry.id,
                                            SharedOpdsDownloadState(isDownloading = true, progress = null)
                                        )
                                        val catalog = opdsState.currentCatalog
                                        opdsRepository.downloadBook(entry, acquisition, catalog?.username, catalog?.password)
                                            .onSuccess { downloaded ->
                                                bridge.recordImportedFiles(
                                                    fileNames = listOf(downloaded.name),
                                                    filePaths = listOf(downloaded.path)
                                                )
                                                showMessage("Downloaded ${downloaded.name}")
                                            }
                                            .onFailure { error ->
                                                opdsState = opdsController.setErrorMessage(
                                                    "Download failed: ${error.message ?: "unknown error"}"
                                                )
                                            }
                                        opdsState = opdsController.updateDownloadState(entry.id, null)
                                    }
                                },
                                onStreamOpdsBook = { entry, catalog ->
                                    val count = entry.pseCount
                                    val template = entry.pseUrlTemplate
                                    if (count == null || template.isNullOrBlank()) {
                                        opdsState = opdsController.setErrorMessage("This OPDS entry cannot be streamed.")
                                    } else {
                                        val streamBook = entry.toIosStreamBook(catalog?.id)
                                        addBooksToLibrary(listOf(streamBook), "Added ${entry.title} stream")
                                        openLibraryBook(streamBook)
                                    }
                                },
                                onClearOpdsError = { opdsState = opdsController.clearError() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun SharedReaderScreenState.toggleBookSelection(bookId: String): SharedReaderScreenState {
    val nextSelection = if (bookId in selectedBookIds) {
        selectedBookIds - bookId
    } else {
        selectedBookIds + bookId
    }
    return copy(selectedBookIds = nextSelection)
}

private fun SharedReaderScreenState.toggleHomePinned(bookId: String): SharedReaderScreenState {
    val nextPinned = if (bookId in pinnedHomeBookIds) {
        pinnedHomeBookIds - bookId
    } else {
        pinnedHomeBookIds + bookId
    }
    return copy(pinnedHomeBookIds = nextPinned)
}

private fun SharedReaderScreenState.toggleLibraryPinned(bookId: String): SharedReaderScreenState {
    val nextPinned = if (bookId in pinnedLibraryBookIds) {
        pinnedLibraryBookIds - bookId
    } else {
        pinnedLibraryBookIds + bookId
    }
    return copy(pinnedLibraryBookIds = nextPinned)
}

private fun SharedReaderScreenState.closeTab(bookId: String): SharedReaderScreenState {
    val nextOpenTabIds = openTabIds.filterNot { it == bookId }
    return copy(
        openTabIds = nextOpenTabIds,
        activeTabBookId = if (activeTabBookId == bookId) nextOpenTabIds.lastOrNull() else activeTabBookId
    )
}

private fun SharedReaderScreenState.withIosImportsFolder(importedBooks: List<BookItem>): SharedReaderScreenState {
    val folderId = "ios_imports"
    val allImportBooks = rawLibraryBooks.filter { it.sourceFolder == "iOS import" }
    val folder = Shelf(
        id = folderId,
        name = "iOS Imports",
        type = ShelfType.FOLDER,
        books = allImportBooks,
        directBooks = allImportBooks
    )
    return copy(shelves = shelves.filterNot { it.id == folderId } + folder)
}

private fun SharedReaderScreenState.createIosShelf(
    name: String,
    bookIds: Set<String>
): SharedReaderScreenState {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return this
    val id = "ios_shelf_${currentTimestamp()}"
    val books = rawLibraryBooks.filter { it.id in bookIds }
    return copy(
        shelves = shelves + Shelf(
            id = id,
            name = trimmedName,
            type = ShelfType.MANUAL,
            books = books,
            directBooks = books
        ),
        selectedBookIds = emptySet(),
        bannerMessage = BannerMessage("Created shelf \"$trimmedName\"")
    )
}

private fun SharedReaderScreenState.removeIosBooks(bookIds: Set<String>): SharedReaderScreenState {
    if (bookIds.isEmpty()) return this
    fun List<BookItem>.withoutRemoved() = filterNot { it.id in bookIds }
    return copy(
        rawLibraryBooks = rawLibraryBooks.withoutRemoved(),
        libraryBooks = libraryBooks.withoutRemoved(),
        recentBooks = recentBooks.withoutRemoved(),
        openTabs = openTabs.withoutRemoved(),
        openTabIds = openTabIds.filterNot { it in bookIds },
        activeTabBookId = activeTabBookId?.takeUnless { it in bookIds },
        pinnedHomeBookIds = pinnedHomeBookIds - bookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds - bookIds,
        selectedBookIds = emptySet(),
        shelves = shelves.map { shelf ->
            shelf.copy(
                books = shelf.books.withoutRemoved(),
                directBooks = shelf.directBooks.withoutRemoved()
            )
        },
        bannerMessage = BannerMessage("Removed ${bookIds.size} book(s) from library")
    )
}

private fun SharedReaderScreenState.withMessage(message: String): SharedReaderScreenState {
    return reduce(AppAction.BannerShown(BannerMessage(message)))
}

private fun BookItem.withIosPdfReaderProgress(state: SharedPdfReaderState): BookItem {
    return copy(
        lastPageIndex = state.pageIndex,
        progressPercentage = state.progressPercent.coerceIn(0f, 100f)
    )
}

private fun SharedReaderScreenState.withUpdatedIosBook(book: BookItem): SharedReaderScreenState {
    fun List<BookItem>.updated(): List<BookItem> {
        return map { item -> if (item.id == book.id) book else item }
    }
    return copy(
        rawLibraryBooks = rawLibraryBooks.updated(),
        recentBooks = recentBooks.updated(),
        libraryBooks = libraryBooks.updated()
    )
}

private fun List<IosImportedFile>.toImportedBooks(existingBooks: List<BookItem>): List<BookItem> {
    if (isEmpty()) return emptyList()
    val existingIds = existingBooks.mapTo(mutableSetOf()) { it.id }
    val now = currentTimestamp()
    return distinctBy { it.path }
        .mapIndexed { index, file ->
            val baseId = "ios_import_${file.path.stableIosImportedFilePath().normalizedId()}"
            var id = baseId
            var suffix = 1
            while (id in existingIds) {
                id = "${baseId}_${suffix++}"
            }
            existingIds += id
            BookItem(
                id = id,
                path = file.path,
                type = file.name.fileTypeFromExtension(),
                displayName = file.name,
                timestamp = now - index,
                title = file.name.substringBeforeLast('.', file.name),
                sourceFolder = "iOS import",
                progressPercentage = 0f
            )
        }
}

private fun OpdsEntry.toIosStreamBook(catalogId: String?): BookItem {
    val streamUri = SharedOpdsStreamUri.build(
        OpdsStreamReference(
            id = id,
            count = pseCount ?: 0,
            urlTemplate = pseUrlTemplate.orEmpty(),
            catalogId = catalogId
        )
    )
    return BookItem(
        id = "ios_opds_stream_${streamUri.normalizedId()}",
        path = streamUri,
        type = FileType.CBZ,
        displayName = title,
        timestamp = currentTimestamp(),
        title = title,
        author = author,
        description = summary,
        sourceFolder = "OPDS Stream",
        progressPercentage = 0f
    )
}

private fun String.fileTypeFromExtension(): FileType {
    return when (substringAfterLast('.', "").lowercase()) {
        "pdf" -> FileType.PDF
        "epub" -> FileType.EPUB
        "mobi" -> FileType.MOBI
        "md", "markdown" -> FileType.MD
        "txt" -> FileType.TXT
        "html", "htm" -> FileType.HTML
        "fb2" -> FileType.FB2
        "cbz" -> FileType.CBZ
        "cbr" -> FileType.CBR
        "cb7" -> FileType.CB7
        "cbt" -> FileType.CBT
        "docx" -> FileType.DOCX
        "odt" -> FileType.ODT
        "fodt" -> FileType.FODT
        "pptx" -> FileType.PPTX
        else -> FileType.UNKNOWN
    }
}

private fun String.normalizedId(): String {
    return lowercase()
        .map { char -> if (char.isLetterOrDigit()) char else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "file" }
}

private fun BookItem.cardTitle(): String {
    return title ?: displayName
}
