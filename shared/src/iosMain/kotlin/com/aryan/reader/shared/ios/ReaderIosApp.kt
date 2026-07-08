package com.aryan.reader.shared.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.opds.SharedOpdsCatalogs
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedMobileAppDrawerContent
import com.aryan.reader.shared.ui.SharedMobilePdfReaderScreen
import com.aryan.reader.shared.ui.SharedMobileHomeScreen
import com.aryan.reader.shared.ui.SharedMobileLibraryScreen
import com.aryan.reader.shared.ui.SharedMobileLibraryTab
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

class ReaderIosBridge {
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
}

data class IosImportedFile(
    val name: String,
    val path: String
)

private const val IosImportedFilesDefaultsKey = "reader_ios_imported_files_v1"

private fun loadPersistedImportedFiles(): List<IosImportedFile> {
    val encoded = NSUserDefaults.standardUserDefaults.stringForKey(IosImportedFilesDefaultsKey) ?: return emptyList()
    return encoded
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.splitEscapedTab()
            if (parts.size != 2) return@mapNotNull null
            IosImportedFile(name = parts[0].unescapePersistedValue(), path = parts[1].unescapePersistedValue())
        }
        .distinctBy { it.path }
        .toList()
}

private fun persistImportedFiles(files: List<IosImportedFile>) {
    val encoded = files.joinToString("\n") { file ->
        "${file.name.escapePersistedValue()}\t${file.path.escapePersistedValue()}"
    }
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = IosImportedFilesDefaultsKey)
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

private enum class ReaderIosMainPage(
    val label: String
) {
    HOME("Home"),
    LIBRARY("Library")
}

@Composable
private fun ReaderIosApp(
    bridge: ReaderIosBridge,
    onImportBooks: () -> Unit
) {
    var state by remember {
        mutableStateOf(SharedReaderScreenState())
    }
    var selectedPage by remember { mutableStateOf(ReaderIosMainPage.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(SharedMobileLibraryTab.BOOKS) }
    var activeReaderBook by remember { mutableStateOf<BookItem?>(null) }
    var opdsState by remember {
        var index = 0
        mutableStateOf(
            SharedOpdsScreenState(
                catalogs = SharedOpdsCatalogs.defaultCatalogs {
                    "ios_default_catalog_${index++}"
                }
            )
        )
    }
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

    fun openBook(book: BookItem) {
        if (book.type == FileType.PDF) {
            activeReaderBook = book
            state = state.copy(
                selectedBookId = book.id,
                openTabIds = if (book.id in state.openTabIds) state.openTabIds else state.openTabIds + book.id,
                activeTabBookId = book.id,
                bannerMessage = null
            )
            return
        }
        state = state.copy(
            selectedBookId = book.id,
            openTabIds = if (book.id in state.openTabIds) state.openTabIds else state.openTabIds + book.id,
            activeTabBookId = book.id,
            bannerMessage = BannerMessage("Opening ${book.cardTitle()} comes next")
        )
    }

    LaunchedEffect(bridge.importedFiles) {
        val importedBooks = bridge.importedFiles.toImportedBooks(existingBooks = state.rawLibraryBooks)
        if (importedBooks.isNotEmpty()) {
            val existingIds = state.rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
            val newBooks = importedBooks.filterNot { it.id in existingIds }
            if (newBooks.isNotEmpty()) {
                val nextRawBooks = newBooks + state.rawLibraryBooks
                state = state.copy(
                    rawLibraryBooks = nextRawBooks,
                    recentBooks = newBooks + state.recentBooks,
                    libraryBooks = nextRawBooks,
                    bannerMessage = BannerMessage("Added ${newBooks.size} import(s)")
                )
                selectedPage = ReaderIosMainPage.LIBRARY
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
                SharedMobilePdfReaderScreen(
                    book = book,
                    onBack = { activeReaderBook = null },
                    onNativePdfBridgeNeeded = { pdfBook ->
                        showMessage("iOS PDF rendering bridge is next: ${pdfBook.displayName}")
                    },
                    modifier = Modifier.fillMaxSize()
                )
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
                ReaderIosMobileScaffold(
                    selectedPage = selectedPage,
                    onPageSelected = { page ->
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
                            ReaderIosMainPage.HOME -> SharedMobileHomeScreen(
                                state = state,
                                onImportBooks = onImportBooks,
                                onOpenBook = ::openBook,
                                onLongPressBook = { book -> state = state.toggleBookSelection(book.id) },
                                onDrawerClick = { scope.launch { drawerState.open() } },
                                onSearchClick = {
                                    selectedPage = ReaderIosMainPage.LIBRARY
                                    state = state.copy(isSearchActive = true)
                                },
                                onNavigateToFolderSync = { selectedPage = ReaderIosMainPage.LIBRARY },
                                onRefresh = { showMessage("Refresh bridge is next for iOS") },
                                onSettingsClick = { showMessage("Settings bridge is next for iOS") },
                                onMoreClick = { showMessage("More actions bridge is next for iOS") },
                                onClearSelection = { state = state.copy(selectedBookIds = emptySet()) },
                                onSelectAll = {
                                    state = state.copy(selectedBookIds = state.recentBooks.mapTo(mutableSetOf()) { it.id })
                                },
                                onOpenTab = ::openBook,
                                onCloseTab = { book -> state = state.closeTab(book.id) },
                                onCloseAllTabs = { state = state.copy(openTabIds = emptyList(), activeTabBookId = null) },
                                onTogglePinned = { book -> state = state.toggleHomePinned(book.id) },
                                modifier = Modifier.fillMaxSize()
                            )

                            ReaderIosMainPage.LIBRARY -> SharedMobileLibraryScreen(
                                state = state,
                                selectedTab = selectedLibraryTab,
                                onTabChange = { selectedLibraryTab = it },
                                opdsState = opdsState,
                                onImportBooks = onImportBooks,
                                onOpenBook = ::openBook,
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
                                onFilterClick = { showMessage("Library filters bridge is next for iOS") },
                                onClearFilters = { state = state.reduce(LibraryAction.FiltersChanged(LibraryFilters())) },
                                onRemoveFilters = { filters -> state = state.reduce(LibraryAction.FiltersChanged(filters)) },
                                onSettingsClick = { showMessage("Settings bridge is next for iOS") },
                                onNewShelfClick = { showMessage("New shelf bridge is next for iOS") },
                                onOpenShelf = { shelf -> showMessage("${shelf.name} shelf bridge is next") },
                                onLongPressShelf = { shelf -> state = state.reduce(LibraryAction.ShelfSelectionToggled(shelf.id)) },
                                onTogglePinned = { book -> state = state.toggleLibraryPinned(book.id) },
                                onOpenCatalog = { catalog -> showMessage("${catalog.title} network bridge is next") },
                                onOpenFeedUrl = { showMessage("OPDS feed bridge is next") },
                                onOpdsNavigateBack = { opdsState = opdsState.copy(isViewingCatalog = false, currentCatalog = null, currentFeed = null) },
                                onOpdsSearch = { query -> showMessage("OPDS search bridge is next: $query") },
                                onOpdsLoadNextPage = { showMessage("OPDS pagination bridge is next") },
                                onAddCatalog = { title, url, username, password ->
                                    opdsState = opdsState.copy(
                                        catalogs = SharedOpdsCatalogs.addCatalog(
                                            catalogs = opdsState.catalogs,
                                            title = title,
                                            url = url,
                                            username = username,
                                            password = password,
                                            idFactory = { "ios_catalog_${currentTimestamp()}" }
                                        )
                                    )
                                },
                                onUpdateCatalog = { id, title, url, username, password ->
                                    opdsState = opdsState.copy(
                                        catalogs = SharedOpdsCatalogs.updateCatalog(
                                            catalogs = opdsState.catalogs,
                                            id = id,
                                            title = title,
                                            url = url,
                                            username = username,
                                            password = password
                                        )
                                    )
                                },
                                onRemoveCatalog = { catalog ->
                                    opdsState = opdsState.copy(
                                        catalogs = SharedOpdsCatalogs.removeCatalog(opdsState.catalogs, catalog.id)
                                    )
                                },
                                onDownloadOpdsBook = { entry, _ -> showMessage("${entry.title} download bridge is next") },
                                onStreamOpdsBook = { entry, _ -> showMessage("${entry.title} stream bridge is next") },
                                onClearOpdsError = { opdsState = opdsState.copy(errorMessage = null) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderIosMobileScaffold(
    selectedPage: ReaderIosMainPage,
    onPageSelected: (ReaderIosMainPage) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                ReaderIosMainPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = selectedPage == page,
                        onClick = { onPageSelected(page) },
                        icon = {
                            Icon(
                                imageVector = when (page) {
                                    ReaderIosMainPage.HOME -> Icons.Default.Home
                                    ReaderIosMainPage.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
                                },
                                contentDescription = page.label
                            )
                        },
                        label = { Text(page.label) }
                    )
                }
            }
        },
        content = content
    )
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

private fun SharedReaderScreenState.withMessage(message: String): SharedReaderScreenState {
    return reduce(AppAction.BannerShown(BannerMessage(message)))
}

private fun List<IosImportedFile>.toImportedBooks(existingBooks: List<BookItem>): List<BookItem> {
    if (isEmpty()) return emptyList()
    val existingIds = existingBooks.mapTo(mutableSetOf()) { it.id }
    val now = currentTimestamp()
    return distinctBy { it.path }
        .mapIndexed { index, file ->
            val baseId = "ios_import_${file.path.normalizedId()}"
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
