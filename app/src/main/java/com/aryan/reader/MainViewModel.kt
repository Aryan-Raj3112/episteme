/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
// MainViewModel.kt
@file:Suppress("DEPRECATION")

package com.aryan.reader

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aryan.reader.data.CloudflareRepository
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.data.FeedbackRepository
import com.aryan.reader.data.FirestoreRepository
import com.aryan.reader.data.FontMetadata
import com.aryan.reader.data.FontsRepository
import com.aryan.reader.data.GoogleDriveRepository
import com.aryan.reader.data.PurchaseEntity
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.data.RemoteConfigRepository
import com.aryan.reader.data.ShelfMetadata
import com.aryan.reader.data.toBookMetadata
import com.aryan.reader.data.toRecentFileItem
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubParser
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.epub.SingleFileImporter
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.paginatedreader.data.BookProcessingWorker
import com.aryan.reader.pdf.PdfCoverGenerator
import com.aryan.reader.pdf.PdfExporter
import com.aryan.reader.pdf.data.PageLayoutRepository
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.pdf.data.VirtualPage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

private const val KEY_RENDER_MODE = "render_mode"
private const val KEY_FOLDER_SYNC_ENABLED = "folder_sync_enabled"
private const val KEY_FOLDER_MIGRATION_COMPLETED = "folder_migration_completed_v2"

data class BannerMessage(val message: String, val isError: Boolean = false)

data class UserData(
    val uid: String, val displayName: String?, val photoUrl: String?, val email: String?
)

enum class AddBooksSource(val displayName: String) {
    UNSHELVED("Unshelved"), ALL_BOOKS("All Books")
}

enum class FileType {
    PDF, EPUB, MOBI, MD, TXT, HTML
}

enum class RenderMode {
    VERTICAL_SCROLL, PAGINATED
}

data class DeviceItem(val deviceId: String, val deviceName: String, val lastSeen: Date?)

data class DeviceLimitReachedState(
    val isLimitReached: Boolean = false, val registeredDevices: List<DeviceItem> = emptyList()
)

data class Shelf(val name: String, val books: List<RecentFileItem>) {
    val bookCount: Int
        get() = books.size
    val topBook: RecentFileItem?
        get() = books.maxByOrNull { it.timestamp }
}

enum class SortOrder(val displayName: String) {
    RECENT("Recent"), TITLE_ASC("Title A-Z"), AUTHOR_ASC("Author A-Z"), PERCENT_ASC("Percent complete 0-100"), PERCENT_DESC(
        "Percent complete 100-0"
    )
}

data class ReaderScreenState(
    val selectedPdfUri: Uri? = null,
    val selectedBookId: String? = null,
    val selectedEpubBook: EpubBook? = null,
    val selectedEpubUri: Uri? = null,
    val selectedFileType: FileType? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val recentFiles: List<RecentFileItem> = emptyList(),
    val contextualActionItems: Set<RecentFileItem> = emptySet(),
    val renderMode: RenderMode = RenderMode.VERTICAL_SCROLL,
    val sortOrder: SortOrder = SortOrder.RECENT,
    val initialLocator: Locator? = null,
    val initialCfi: String? = null,
    val initialBookmarksJson: String? = null,
    val initialPageInBook: Int? = null,
    val shelves: List<Shelf> = emptyList(),
    val viewingShelfName: String? = null,
    val isAddingBooksToShelf: Boolean = false,
    val showCreateShelfDialog: Boolean = false,
    val mainScreenStartPage: Int = 0,
    val libraryScreenStartPage: Int = 0,
    val showRenameShelfDialogFor: String? = null,
    val showDeleteShelfDialogFor: String? = null,
    val addBooksSource: AddBooksSource = AddBooksSource.UNSHELVED,
    val booksSelectedForAdding: Set<String> = emptySet(),
    val booksAvailableForAdding: List<RecentFileItem> = emptyList(),
    val contextualActionShelfNames: Set<String> = emptySet(),
    val currentUser: UserData? = null,
    val isAuthMenuExpanded: Boolean = false,
    val isProUser: Boolean = false,
    val isSyncEnabled: Boolean = false,
    val isFolderSyncEnabled: Boolean = false,
    val bannerMessage: BannerMessage? = null,
    val deviceLimitState: DeviceLimitReachedState = DeviceLimitReachedState(),
    val isReplacingDevice: Boolean = false,
    val isRequestingDrivePermission: Boolean = false,
    val downloadingBookIds: Set<String> = emptySet(),
    val uploadingBookIds: Set<String> = emptySet(),
    val syncedFolderUri: String? = null,
    val lastFolderScanTime: Long? = null,
    val hasUnreadFeedback: Boolean = false,
    val searchQuery: String = "",
    val showFolderMigrationDialog: Boolean = false,
    val isRefreshing: Boolean = false,
)

open class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Context = application.applicationContext
    private val authRepository = AuthRepository(appContext)
    private val recentFilesRepository = RecentFilesRepository(appContext)
    private val pdfTextRepository = PdfTextRepository(appContext)

    private val bookCacheDao = BookCacheDatabase.getDatabase(application).bookCacheDao()
    private val epubParser = EpubParser(appContext)
    private val mobiParser = MobiParser(appContext)
    private val singleFileImporter = SingleFileImporter(appContext)
    private val bookImporter = BookImporter(appContext)
    private val prefs: SharedPreferences =
        application.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
    private val firestoreRepository = FirestoreRepository()
    private val googleDriveRepository = GoogleDriveRepository()
    private val billingClientWrapper =
        BillingClientWrapper(appContext, viewModelScope) { purchase ->
            verifyPurchaseWithBackend(purchase)
        }
    private val cloudflareRepository = CloudflareRepository()
    private val remoteConfigRepository = RemoteConfigRepository()
    private var userProfileListener: Any? = null
    private val migrationAttempted = MutableStateFlow(false)
    private val _prefsUpdateFlow = MutableStateFlow(0L)
    private val prefsListener: SharedPreferences.OnSharedPreferenceChangeListener
    private val feedbackRepository = FeedbackRepository(appContext)
    private var feedbackListener: Any? = null
    private val importMutex = Mutex()
    private val pageLayoutRepository = PageLayoutRepository(appContext)
    private val pdfRichTextRepository = com.aryan.reader.pdf.PdfRichTextRepository(appContext)
    private val pdfTextBoxRepository = PdfTextBoxRepository(appContext)

    data class PageModificationResult(
        val layout: List<VirtualPage>,
        val annotations: Map<Int, List<PdfAnnotation>>,
        val bookmarksJson: String
    )

    val proUpgradeState = billingClientWrapper.proUpgradeState

    private val _internalState = MutableStateFlow(
        ReaderScreenState(
            renderMode = try {
                val savedRenderModeName = prefs.getString(
                    KEY_RENDER_MODE, RenderMode.VERTICAL_SCROLL.name
                )
                RenderMode.valueOf(
                    savedRenderModeName ?: RenderMode.VERTICAL_SCROLL.name
                )
            } catch (_: IllegalArgumentException) {
                RenderMode.VERTICAL_SCROLL
            },
            sortOrder = try {
                val savedSortOrderName = prefs.getString(
                    KEY_SORT_ORDER, SortOrder.RECENT.name
                )
                SortOrder.valueOf(
                    savedSortOrderName ?: SortOrder.RECENT.name
                )
            } catch (_: IllegalArgumentException) {
                SortOrder.RECENT
            },
            addBooksSource = try {
                val savedSourceName = prefs.getString(
                    KEY_ADD_BOOKS_SOURCE, AddBooksSource.UNSHELVED.name
                )
                AddBooksSource.valueOf(
                    savedSourceName ?: AddBooksSource.UNSHELVED.name
                )
            } catch (_: IllegalArgumentException) {
                AddBooksSource.UNSHELVED
            },
            currentUser = authRepository.getSignedInUser(),
            isSyncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, false),
            isFolderSyncEnabled = prefs.getBoolean(KEY_FOLDER_SYNC_ENABLED, false),
            syncedFolderUri = prefs.getString(KEY_SYNCED_FOLDER_URI, null),
            lastFolderScanTime = if (prefs.contains(KEY_LAST_FOLDER_SCAN_TIME)) prefs.getLong(
                KEY_LAST_FOLDER_SCAN_TIME,
                0L
            )
            else null
        )
    )

    open val uiState: StateFlow<ReaderScreenState> = combine(
        _internalState,
        recentFilesRepository.getRecentFilesFlow(),
        _prefsUpdateFlow
    ) { internalState, recentFilesFromDb, _ ->
        val validContextualItems = internalState.contextualActionItems.filter { contextItem ->
            recentFilesFromDb.any { dbItem ->
                dbItem.uriString == contextItem.uriString
            }
        }.toSet()

        if (validContextualItems.size != internalState.contextualActionItems.size) {
            Timber.d(
                "Contextual items updated due to recent files change. Before: ${internalState.contextualActionItems.size}, After: ${validContextualItems.size}"
            )
        }

        val query = internalState.searchQuery.trim()
        val filteredFiles = if (query.isBlank()) {
            recentFilesFromDb
        } else {
            recentFilesFromDb.filter { item ->
                item.displayName.contains(query, ignoreCase = true) || item.title?.contains(
                    query,
                    ignoreCase = true
                ) == true || item.author?.contains(query, ignoreCase = true) == true
            }
        }

        val sortedRecentFiles = when (internalState.sortOrder) {
            SortOrder.RECENT -> filteredFiles // Changed from recentFilesFromDb
            SortOrder.TITLE_ASC -> filteredFiles.sortedBy {
                it.title?.lowercase() ?: it.displayName.lowercase()
            }

            SortOrder.AUTHOR_ASC -> filteredFiles.sortedWith(
                compareBy(nullsLast()) {
                    it.author?.lowercase()
                })

            SortOrder.PERCENT_ASC -> filteredFiles.sortedBy { it.progressPercentage ?: 0f }
            SortOrder.PERCENT_DESC -> filteredFiles.sortedByDescending {
                it.progressPercentage ?: 0f
            }
        }

        val shelfNames = prefs.getStringSet(KEY_SHELVES, emptySet()) ?: emptySet()
        val shelvedBookIds = mutableSetOf<String>()

        val shelvesFromPrefs = shelfNames.map { shelfName ->
            val bookIds = prefs.getStringSet(
                "$KEY_SHELF_CONTENT_PREFIX$shelfName", emptySet()
            ) ?: emptySet()
            shelvedBookIds.addAll(bookIds)
            val booksForShelf = sortedRecentFiles.filter {
                it.bookId in bookIds
            }
            Shelf(shelfName, booksForShelf)
        }.sortedBy { it.name }

        val unshelvedBooks = sortedRecentFiles.filter { it.bookId !in shelvedBookIds }
        val allShelves = shelvesFromPrefs + Shelf("Unshelved", unshelvedBooks)

        val booksAvailableForAdding =
            if (internalState.isAddingBooksToShelf && internalState.viewingShelfName != null) {
                val currentShelfBooksUris = allShelves.find {
                    it.name == internalState.viewingShelfName
                }?.books?.map { it.uriString }?.toSet() ?: emptySet()

                when (internalState.addBooksSource) {
                    AddBooksSource.UNSHELVED -> unshelvedBooks
                    AddBooksSource.ALL_BOOKS -> sortedRecentFiles.filter {
                        it.uriString !in currentShelfBooksUris
                    }
                }
            } else {
                emptyList()
            }

        internalState.copy(
            recentFiles = sortedRecentFiles,
            contextualActionItems = validContextualItems,
            shelves = allShelves,
            booksAvailableForAdding = booksAvailableForAdding
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderScreenState()
    )

    fun onSearchQueryChange(newQuery: String) {
        _internalState.update { it.copy(searchQuery = newQuery) }
    }

    private val _reviewRequestEvent = Channel<Unit>(Channel.BUFFERED)
    val reviewRequestEvent = _reviewRequestEvent.receiveAsFlow()
    private var hasRequestedReviewInThisSession = false

    suspend fun loadPageLayout(bookId: String, totalPdfPages: Int): List<VirtualPage> {
        return pageLayoutRepository.loadLayout(bookId, totalPdfPages)
    }

    suspend fun addPage(
        bookId: String,
        currentLayout: List<VirtualPage>,
        insertIndex: Int,
        currentAnnotations: Map<Int, List<PdfAnnotation>>,
        currentBookmarksJson: String,
        referenceWidth: Int,
        referenceHeight: Int,
        wasManuallyAdded: Boolean = false
    ): PageModificationResult = withContext(Dispatchers.Default) {
        Timber.d("Adding page at index $insertIndex for book $bookId (manual=$wasManuallyAdded)")

        val newLayout = currentLayout.toMutableList()
        val safeIndex = insertIndex.coerceIn(0, newLayout.size)

        val newPage = VirtualPage.BlankPage(
            id = UUID.randomUUID().toString(),
            width = referenceWidth,
            height = referenceHeight,
            wasManuallyAdded = wasManuallyAdded
        )
        newLayout.add(safeIndex, newPage)

        val newAnnotations = mutableMapOf<Int, List<PdfAnnotation>>()
        currentAnnotations.forEach { (pageIdx, annots) ->
            val newIdx = if (pageIdx >= safeIndex) pageIdx + 1 else pageIdx
            val shiftedAnnots = annots.map { it.copy(pageIndex = newIdx) }
            newAnnotations[newIdx] = shiftedAnnots
        }

        val newTotalPages = newLayout.size
        val newBookmarksJson = try {
            if (currentBookmarksJson.isNotBlank()) {
                val jsonArray = JSONArray(currentBookmarksJson)
                val newArray = JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val bmPageIndex = obj.getInt("pageIndex")
                    val title = obj.getString("title")

                    val newBmPageIndex = if (bmPageIndex >= safeIndex) bmPageIndex + 1
                    else bmPageIndex

                    val newObj = JSONObject()
                    newObj.put("pageIndex", newBmPageIndex)
                    newObj.put("title", title)
                    newObj.put("totalPages", newTotalPages)
                    newArray.put(newObj)
                }
                newArray.toString()
            } else {
                "[]"
            }
        } catch (e: Exception) {
            Timber.e(e, "Error shifting bookmarks")
            currentBookmarksJson
        }

        pageLayoutRepository.saveLayout(bookId, newLayout)

        PageModificationResult(newLayout, newAnnotations, newBookmarksJson)
    }

    suspend fun removePage(
        bookId: String,
        currentLayout: List<VirtualPage>,
        removeIndex: Int,
        currentAnnotations: Map<Int, List<PdfAnnotation>>,
        currentBookmarksJson: String
    ): PageModificationResult = withContext(Dispatchers.Default) {
        Timber.d("Removing page at index $removeIndex for book $bookId")

        // 1. Update Layout
        val newLayout = currentLayout.toMutableList()
        if (removeIndex in newLayout.indices) {
            newLayout.removeAt(removeIndex)
        } else {
            return@withContext PageModificationResult(
                currentLayout, currentAnnotations, currentBookmarksJson
            )
        }

        // 2. Shift Annotations (Delete for removed page, decrement for pages > removed)
        val newAnnotations = mutableMapOf<Int, List<PdfAnnotation>>()
        currentAnnotations.forEach { (pageIdx, annots) ->
            if (pageIdx != removeIndex) {
                val newIdx = if (pageIdx > removeIndex) pageIdx - 1 else pageIdx
                val shiftedAnnots = annots.map { it.copy(pageIndex = newIdx) }
                newAnnotations[newIdx] = shiftedAnnots
            }
        }

        // 3. Shift Bookmarks
        val newTotalPages = newLayout.size
        val newBookmarksJson = try {
            if (currentBookmarksJson.isNotBlank()) {
                val jsonArray = JSONArray(currentBookmarksJson)
                val newArray = JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val bmPageIndex = obj.getInt("pageIndex")

                    if (bmPageIndex == removeIndex) continue

                    val title = obj.getString("title")
                    val newBmPageIndex = if (bmPageIndex > removeIndex) bmPageIndex - 1
                    else bmPageIndex

                    val newObj = JSONObject()
                    newObj.put("pageIndex", newBmPageIndex)
                    newObj.put("title", title)
                    newObj.put("totalPages", newTotalPages)
                    newArray.put(newObj)
                }
                newArray.toString()
            } else {
                "[]"
            }
        } catch (e: Exception) {
            Timber.e(e, "Error shifting bookmarks")
            currentBookmarksJson
        }

        // 4. Save Layout persistently
        pageLayoutRepository.saveLayout(bookId, newLayout)

        PageModificationResult(newLayout, newAnnotations, newBookmarksJson)
    }

    init {
        Timber.d("ViewModel instance created.")
        PDFBoxResourceLoader.init(getApplication())
        val currentOpenCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0)
        prefs.edit { putInt(KEY_APP_OPEN_COUNT, currentOpenCount + 1) }

        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SHELVES || key?.startsWith(KEY_SHELF_CONTENT_PREFIX) == true) {
                Timber.d("Shelf preference changed ($key), triggering UI refresh.")
                _prefsUpdateFlow.value = System.currentTimeMillis()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        remoteConfigRepository.init()

        val isMigrationCompleted = prefs.getBoolean(KEY_FOLDER_MIGRATION_COMPLETED, false)

        if (_internalState.value.syncedFolderUri != null) {
            if (isMigrationCompleted) {
                Timber.d("App Start: Triggering local folder metadata-only sync.")
                syncFolderMetadata()
            } else {
                Timber.d("App Start: Skipping sync. Waiting for migration/detachment logic.")
            }
        }

        viewModelScope.launch { billingClientWrapper.initializeConnection() }

        viewModelScope.launch {
            authRepository.observeAuthState().collect { newUserData ->
                firestoreRepository.removeListener(userProfileListener)
                firestoreRepository.removeListener(feedbackListener)

                _internalState.update { it.copy(currentUser = newUserData) }

                billingClientWrapper.refreshPurchasesAsync()

                if (newUserData != null) {
                    registerOrUpdateDeviceOnSignIn(newUserData.uid)

                    feedbackListener =
                        feedbackRepository.listenForUnreadFeedback(newUserData.uid) { hasUnread ->
                            _internalState.update { it.copy(hasUnreadFeedback = hasUnread) }
                        }

                    userProfileListener =
                        firestoreRepository.listenToUserProfile(newUserData.uid) { isProFromBackend ->
                            _internalState.update { it.copy(isProUser = isProFromBackend) }

                            if (isProFromBackend) {
                                verifyDeviceForProUser()

                                if (_internalState.value.isSyncEnabled) {
                                    viewModelScope.launch {
                                        Timber.tag("AnnotationSync").d(
                                            "Startup: Pro user & Sync enabled. Initiating cloud sync."
                                        )

                                        if (googleDriveRepository.hasDrivePermissions(appContext)) {
                                            syncWithCloud(showBanner = false)
                                        } else {
                                            Timber.tag("AnnotationSync").d(
                                                "Startup: Sync skipped. Missing Drive permissions."
                                            )
                                        }
                                    }
                                }
                            }
                            triggerLegacyPurchaseMigration()
                        }
                } else {
                    _internalState.update { it.copy(isProUser = false, hasUnreadFeedback = false) }
                }
            }
        }
        val folderUri = _internalState.value.syncedFolderUri
        val migrationCompleted = prefs.getBoolean(KEY_FOLDER_MIGRATION_COMPLETED, false)

        if (folderUri != null && !migrationCompleted) {
            Timber.tag("FolderSync").d("First time after refactor: Showing migration dialog.")
            _internalState.update { it.copy(showFolderMigrationDialog = true) }
        }
    }

    fun completeFolderMigration() {
        Timber.tag("FolderSync").d("User acknowledged update. Detaching old books and starting fresh scan.")

        viewModelScope.launch {
            recentFilesRepository.detachAllFolderBooks()

            prefs.edit { putBoolean(KEY_FOLDER_MIGRATION_COMPLETED, true) }
            _internalState.update { it.copy(showFolderMigrationDialog = false) }

            scanSyncedFolder()
        }
    }

    private val fontsRepository = FontsRepository(appContext)

    val customFonts = fontsRepository.getAllFonts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private suspend fun syncFonts(userId: String) {
        Timber.d("Starting Font Sync...")

        val accessToken = googleDriveRepository.getAccessToken(appContext) ?: return

        // 1. Fetch Metadata
        val localFonts = fontsRepository.getAllFontsForSync()
        val remoteFonts = firestoreRepository.getAllFonts(userId)
        val localFontsMap = localFonts.associateBy { it.id }
        val remoteFontsMap = remoteFonts.associateBy { it.id }
        val allFontIds = (localFontsMap.keys + remoteFontsMap.keys).distinct()
        val driveFiles =
            googleDriveRepository.getFiles(accessToken)?.files.orEmpty().associateBy { it.name }

        allFontIds.forEach { fontId ->
            val local = localFontsMap[fontId]
            val remote = remoteFontsMap[fontId]

            if (local != null && remote == null) {
                if (!local.isDeleted) {
                    val meta = FontMetadata(
                        local.id,
                        local.displayName,
                        local.fileName,
                        local.fileExtension,
                        local.timestamp,
                        false
                    )
                    firestoreRepository.syncFontMetadata(userId, meta)
                }
            } else if (local == null && remote != null) {
                fontsRepository.addFontFromSync(remote)
            } else if (local != null && remote != null) {
                if (local.isDeleted && !remote.isDeleted) {
                    firestoreRepository.syncFontMetadata(userId, remote.copy(isDeleted = true))
                } else if (!local.isDeleted && remote.isDeleted) {
                    fontsRepository.deleteFont(local.id)
                }
            }
        }

        val finalLocalFonts = fontsRepository.getAllFontsForSync()

        finalLocalFonts.forEach { font ->
            if (font.isDeleted) {
                driveFiles[font.fileName]?.id?.let { fileId ->
                    googleDriveRepository.deleteDriveFile(accessToken, fileId)
                }
                firestoreRepository.deleteFontMetadata(userId, font.id)
                fontsRepository.deletePermanently(font.id)
            } else {
                val driveFile = driveFiles[font.fileName]
                val localFile = fontsRepository.getFontFile(font.fileName)

                if (localFile.exists() && driveFile == null) {
                    Timber.d("Uploading font file: ${font.fileName}")
                    googleDriveRepository.uploadFont(
                        accessToken, font.fileName, localFile, font.fileExtension
                    )
                } else if (!localFile.exists() && driveFile != null) {
                    Timber.d("Downloading font file: ${font.fileName}")
                    googleDriveRepository.downloadFile(accessToken, driveFile.id, localFile)
                }
            }
        }
        Timber.d("Font Sync Complete.")
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            _internalState.update { it.copy(isLoading = true) }
            val result = fontsRepository.importFont(uri)
            result.onSuccess { font ->
                if (uiState.value.isSyncEnabled) {
                    uploadNewFont(font)
                }
            }.onFailure {
                showBanner("Failed to import font: ${it.message}", isError = true)
            }
            _internalState.update { it.copy(isLoading = false) }
        }
    }

    private fun uploadNewFont(font: CustomFontEntity) = viewModelScope.launch {
        try {
            val currentUser = uiState.value.currentUser ?: return@launch
            val accessToken = googleDriveRepository.getAccessToken(appContext) ?: return@launch

            val meta = FontMetadata(
                font.id, font.displayName, font.fileName, font.fileExtension, font.timestamp, false
            )
            firestoreRepository.syncFontMetadata(currentUser.uid, meta)

            val file = File(font.path)
            if (file.exists()) {
                googleDriveRepository.uploadFont(
                    accessToken, font.fileName, file, font.fileExtension
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload new font immediately")
        }
    }

    fun deleteFont(fontId: String) {
        viewModelScope.launch { fontsRepository.deleteFont(fontId) }
    }

    private fun getInstallationId(): String {
        var installationId = prefs.getString(KEY_INSTALLATION_ID, null)
        if (installationId == null) {
            installationId = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_INSTALLATION_ID, installationId) }
            Timber.d("Generated new stable installation ID: $installationId")
        }
        return installationId
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun onDrivePermissionFlowCancelled() {
        _internalState.update {
            it.copy(isRequestingDrivePermission = false, isSyncEnabled = false)
        }
        prefs.edit { putBoolean(KEY_SYNC_ENABLED, false) }
        showBanner("Sync requires Google Drive permission.", isError = true)
    }

    private fun verifyPurchaseWithBackend(
        purchase: PurchaseEntity, isSilentMigrationCheck: Boolean = false
    ) {
        viewModelScope.launch {
            if (!purchase.products.contains(BillingClientWrapper.PRO_LIFETIME_PRODUCT_ID)) {
                Timber.e("Purchase verification failed: Incorrect product ID.")
                if (!isSilentMigrationCheck) {
                    _internalState.update {
                        it.copy(
                            bannerMessage = BannerMessage(
                                "An error occurred with the purchase.", isError = true
                            )
                        )
                    }
                }
                billingClientWrapper.clearVerificationState()
                return@launch
            }

            val result = cloudflareRepository.verifyPurchase(purchase.purchaseToken)

            if (result.isSuccess) {
                Timber.i("Backend verification successful. Firestore will update the app.")
                _internalState.update {
                    it.copy(bannerMessage = BannerMessage("Upgrade successful! Welcome to Pro."))
                }
                verifyDeviceForProUser()
            } else {
                val exception = result.exceptionOrNull()
                if (exception?.message?.contains("already claimed") == true) {
                    Timber.i(
                        "Migration check: Purchase token is already claimed by another account. Silently ignoring."
                    )
                } else {
                    val errorMessage =
                        "Purchase verification failed. Please contact support if you were charged."
                    Timber.e(exception, "Backend verification failed")
                    if (!isSilentMigrationCheck) {
                        _internalState.update {
                            it.copy(bannerMessage = BannerMessage(errorMessage, isError = true))
                        }
                    }
                }
            }

            if (!isSilentMigrationCheck) {
                billingClientWrapper.clearVerificationState()
            }
        }
    }

    fun verifyDeviceForProUser() {
        if (!_internalState.value.isProUser) return
        val currentUser = _internalState.value.currentUser ?: return

        viewModelScope.launch {
            val deviceId = getInstallationId()

            when (val deviceStatus =
                firestoreRepository.getDeviceStatus(currentUser.uid, deviceId)) {
                is com.aryan.reader.data.DeviceStatus.Active -> {
                    Timber.d("Device is active. Updating last seen.")
                    firestoreRepository.updateDeviceLastSeen(currentUser.uid, deviceId)
                }

                is com.aryan.reader.data.DeviceStatus.Revoked -> {
                    Timber.w("Device has been revoked. Signing out.")
                    firestoreRepository.deleteDevice(currentUser.uid, deviceId) // Clean up
                    signOut()
                    showBanner("This device was removed from your account.")
                }

                is com.aryan.reader.data.DeviceStatus.NotFound -> {
                    Timber.d("Device not found during verification. Triggering full registration.")
                    registerOrUpdateDeviceOnSignIn(currentUser.uid)
                }

                is com.aryan.reader.data.DeviceStatus.Error -> {
                    Timber.e(deviceStatus.exception, "Error checking device status.")
                    _internalState.update {
                        it.copy(
                            errorMessage = "Could not verify this device. Please check your connection."
                        )
                    }
                }
            }
        }
    }

    fun replaceDevice(deviceToRemoveId: String) {
        val currentUser = _internalState.value.currentUser ?: return
        _internalState.update { it.copy(isReplacingDevice = true) }

        viewModelScope.launch {
            val newDeviceId = getInstallationId()
            val newDeviceName = getDeviceName()

            val success = firestoreRepository.replaceDevice(
                userId = currentUser.uid,
                deviceToRemoveId = deviceToRemoveId,
                newDeviceId = newDeviceId,
                newDeviceName = newDeviceName,
                originDeviceId = newDeviceId
            )

            if (success) {
                Timber.d("Device replaced successfully.")
                _internalState.update {
                    it.copy(
                        deviceLimitState = DeviceLimitReachedState(isLimitReached = false),
                        isReplacingDevice = false
                    )
                }
            } else {
                Timber.e("Failed to replace device.")
                _internalState.update {
                    it.copy(
                        errorMessage = "Failed to update devices. Please try again.",
                        isReplacingDevice = false
                    )
                }
            }
        }
    }

    private val pdfAnnotationRepository = PdfAnnotationRepository(appContext)

    private fun getFastFileId(context: Context, uri: Uri): String {
        var result = uri.toString()
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown"
                    result = "${name}_${size}"
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate fast file ID")
        }
        return result
    }

    fun savePdfWithAnnotations(
        sourceUri: Uri,
        destUri: Uri,
        annotations: Map<Int, List<PdfAnnotation>>,
        richTextPageLayouts: List<com.aryan.reader.pdf.PageTextLayout>? = null,
        textBoxes: List<PdfTextBox>? = null,
        bookId: String
    ) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(isLoading = true, bannerMessage = BannerMessage("Saving PDF..."))
            }
            try {
                val virtualPages = pageLayoutRepository.getLayoutOrNull(bookId)
                val outputStream = appContext.contentResolver.openOutputStream(destUri)
                if (outputStream != null) {
                    PdfExporter.exportAnnotatedPdf(
                        appContext,
                        sourceUri,
                        outputStream,
                        virtualPages,
                        annotations,
                        richTextPageLayouts,
                        textBoxes
                    )
                    showBanner("PDF saved successfully.")
                } else {
                    showBanner("Failed to open file for saving.", isError = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save annotated PDF")
                showBanner("Error saving PDF: ${e.localizedMessage}", isError = true)
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveOriginalPdf(sourceUri: Uri, destUri: Uri) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(isLoading = true, bannerMessage = BannerMessage("Saving original PDF..."))
            }
            try {
                val contentResolver = appContext.contentResolver
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                showBanner("Original PDF saved successfully.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save original PDF")
                showBanner("Error saving PDF: ${e.localizedMessage}", isError = true)
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
        }
    }

    suspend fun sharePdf(
        activityContext: Context,
        sourceUri: Uri,
        annotations: Map<Int, List<PdfAnnotation>>,
        richTextPageLayouts: List<com.aryan.reader.pdf.PageTextLayout>? = null,
        textBoxes: List<PdfTextBox>? = null,
        includeAnnotations: Boolean,
        filename: String,
        bookId: String? = null
    ) {
        withContext(Dispatchers.IO) {
            val resolvedBookId =
                bookId ?: recentFilesRepository.getFileByUri(sourceUri.toString())?.bookId
                ?: getFastFileId(appContext, sourceUri)

            try {
                val shareDir = File(appContext.cacheDir, "shared_files")

                if (shareDir.exists()) {
                    shareDir.listFiles()?.forEach { file ->
                        try {
                            file.delete()
                        } catch (_: Exception) {
                            Timber.w("Failed to delete temp share file: ${file.name}")
                        }
                    }
                } else {
                    shareDir.mkdirs()
                }

                val destFile = File(shareDir, filename)
                val outputStream = FileOutputStream(destFile)

                if (includeAnnotations) {
                    val virtualPages = pageLayoutRepository.getLayoutOrNull(resolvedBookId)

                    PdfExporter.exportAnnotatedPdf(
                        appContext,
                        sourceUri,
                        outputStream,
                        virtualPages,
                        annotations,
                        richTextPageLayouts,
                        textBoxes
                    )
                } else {
                    appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                        input.copyTo(outputStream)
                    }
                }

                val authority = "${appContext.packageName}.provider"
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    appContext, authority, destFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)

                    putExtra(Intent.EXTRA_TITLE, filename)
                    putExtra(Intent.EXTRA_SUBJECT, "Sharing: $filename")

                    clipData = ClipData.newRawUri(filename, contentUri)

                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share PDF")

                if (activityContext !is android.app.Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) { activityContext.startActivity(chooser) }
            } catch (e: Exception) {
                Timber.e(e, "Share failed")
                showBanner("Share failed: ${e.localizedMessage}", isError = true)
            }
        }
    }

    private fun uploadSingleBookMetadata(book: RecentFileItem) {
        if (!uiState.value.isSyncEnabled) return

        if (book.sourceFolderUri != null) {
            Timber.d("Skipping metadata sync for local folder book: ${book.displayName}")
            return
        }
        val currentUser = uiState.value.currentUser ?: return

        viewModelScope.launch {
            try {
                val deviceId = getInstallationId()
                Timber.tag("AnnotationSync").d("Preparing to sync book: ${book.bookId}")

                // --- CHANGED BLOCK START ---
                // Gather files from all three repositories
                val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(book.bookId)
                val richTextFile = pdfRichTextRepository.getFileForSync(book.bookId)
                val layoutFile = pageLayoutRepository.getLayoutFile(book.bookId)
                val textBoxFile = pdfTextBoxRepository.getFileForSync(book.bookId)

                val hasInk = inkFile?.exists() == true
                val hasRichText = richTextFile.exists()
                val hasLayout = layoutFile.exists()
                val hasTextBoxes = textBoxFile.exists()
                val hasAnyData = hasInk || hasRichText || hasLayout || hasTextBoxes

                if (hasAnyData) {
                    if (googleDriveRepository.hasDrivePermissions(appContext)) {
                        val accessToken = googleDriveRepository.getAccessToken(appContext)

                        if (accessToken != null) {
                            val bundleJson = JSONObject()
                            bundleJson.put("version", 2)

                            if (hasInk) {
                                try {
                                    val inkContent = inkFile.readText()
                                    val jsonArray = JSONArray(inkContent)
                                    bundleJson.put("ink", jsonArray)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to parse local ink file")
                                }
                            }

                            if (hasRichText) {
                                try {
                                    val textContent = richTextFile.readText()
                                    bundleJson.put("text", JSONArray(textContent))
                                } catch (e: Exception) {
                                    Timber.e(e)
                                }
                            }

                            if (hasLayout) {
                                try {
                                    val layoutContent = layoutFile.readText()
                                    bundleJson.put("layout", JSONArray(layoutContent))
                                } catch (e: Exception) {
                                    Timber.e(e)
                                }
                            }

                            if (hasTextBoxes) {
                                try {
                                    val tbContent = textBoxFile.readText()
                                    bundleJson.put("textBoxes", JSONArray(tbContent))
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to parse local text box file")
                                }
                            }

                            val bundleFile =
                                File(appContext.cacheDir, "sync_bundle_${book.bookId}.json")
                            bundleFile.writeText(bundleJson.toString())

                            val uploaded = googleDriveRepository.uploadAnnotationFile(
                                accessToken, book.bookId, bundleFile
                            )
                            bundleFile.delete()

                            if (uploaded != null) {
                                Timber.tag("AnnotationSync")
                                    .d("Bundle upload SUCCESS. ID: ${uploaded.id}")
                            } else {
                                Timber.tag("AnnotationSync").e("Bundle upload FAILED.")
                            }
                        }
                    }
                }  else {
                    Timber.tag("AnnotationSync")
                        .d("No local data (ink/text/layout) to upload for ${book.bookId}")
                }

                val metadataToSync = book.toBookMetadata().copy(
                    lastModifiedTimestamp = System.currentTimeMillis(),
                    hasAnnotations = hasAnyData
                )

                firestoreRepository.syncBookMetadata(currentUser.uid, metadataToSync, deviceId)
                Timber.tag("AnnotationSync")
                    .d("Firestore metadata updated for ${book.bookId} (hasData=$hasAnyData)")
            } catch (e: Exception) {
                Timber.tag("AnnotationSync").e(e, "Failed to sync book data: ${book.bookId}")
            }
        }
    }

    fun hideItemsFromRecentsView() {
        val itemsToHide = _internalState.value.contextualActionItems
        if (itemsToHide.isNotEmpty()) {
            Timber.d("DeleteDebug: Hiding ${itemsToHide.size} items from recents view.")
            viewModelScope.launch {
                val bookIdsToHide = itemsToHide.map { it.bookId }
                Timber.d("DeleteDebug: Marking book IDs as not recent: $bookIdsToHide")
                recentFilesRepository.markAsNotRecent(bookIdsToHide)
                _internalState.update { it.copy(contextualActionItems = emptySet()) }

                if (uiState.value.isSyncEnabled && googleDriveRepository.hasDrivePermissions(appContext)) {
                    bookIdsToHide.forEach { bookId ->
                        val updatedItem = recentFilesRepository.getFileByBookId(bookId)
                        if (updatedItem != null) {
                            Timber.d(
                                "DeleteDebug: Found updated item ${updatedItem.bookId} to sync, isRecent=${updatedItem.isRecent}"
                            )
                            uploadSingleBookMetadata(updatedItem)
                        } else {
                            Timber.w(
                                "DeleteDebug: Could not find item with bookId $bookId after marking as not recent."
                            )
                        }
                    }
                }
            }
        } else {
            Timber.w("DeleteDebug: Attempted to hide items, but none were selected.")
        }
    }

    fun getDriveSignInIntent(context: Context): Intent {
        return googleDriveRepository.getSignInIntent(context)
    }

    fun onDrivePermissionResult(data: Intent?) {
        viewModelScope.launch {
            _internalState.update { it.copy(isRequestingDrivePermission = false) }

            val success = googleDriveRepository.handleSignInResult(data)

            if (success) {
                Timber.d("Drive permission granted.")
                setSyncEnabled(true)
            } else {
                Timber.w("Drive permission denied or failed.")
                onDrivePermissionFlowCancelled()
            }
        }
    }

    open fun clearSelectedFile() {
        Timber.i("clearSelectedFile called.")

        val appOpenCount = prefs.getInt(KEY_APP_OPEN_COUNT, 0)
        if (!hasRequestedReviewInThisSession && appOpenCount >= 3) {
            viewModelScope.launch {
                _reviewRequestEvent.send(Unit)
                hasRequestedReviewInThisSession = true
            }
        }

        val bookToSync = uiState.value.recentFiles.find {
            it.uriString == (uiState.value.selectedPdfUri?.toString()
                ?: uiState.value.selectedEpubUri?.toString())
        }

        _internalState.update {
            it.copy(
                selectedPdfUri = null,
                selectedEpubUri = null,
                selectedBookId = null,
                selectedEpubBook = null,
                selectedFileType = null,
                isLoading = false,
                errorMessage = null,
                initialLocator = null,
                initialPageInBook = null
            )
        }

        bookToSync?.let {
            if (uiState.value.uploadingBookIds.contains(it.bookId)) {
                return
            }
            if (uiState.value.isSyncEnabled) {
                Timber.d("Book closed, triggering metadata sync for ${it.bookId}")
                uploadSingleBookMetadata(it)
            }

            if (it.sourceFolderUri != null) {
                Timber.tag("FolderAnnotationSync").d("Book closed (Folder Linked), syncing metadata and annotations to folder: ${it.bookId}")
                viewModelScope.launch {
                    recentFilesRepository.syncLocalMetadataToFolder(it.bookId)
                    recentFilesRepository.syncLocalAnnotationsToFolder(it.bookId)
                }
            }
        }
    }

    private fun registerOrUpdateDeviceOnSignIn(userId: String) {
        viewModelScope.launch {
            Timber.d("Starting device registration/update process for user: $userId")
            val installationId = getInstallationId()
            val deviceName = getDeviceName()

            firestoreRepository.getFcmToken { token ->
                if (token != null) {
                    viewModelScope.launch {
                        firestoreRepository.registerOrUpdateDevice(
                            userId = userId,
                            deviceId = installationId,
                            deviceName = deviceName,
                            fcmToken = token
                        )
                    }
                }
            }
        }
    }

    fun setSyncedFolder(folderUri: Uri) {
        viewModelScope.launch {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Timber.d("Persistable URI permission taken for folder: $folderUri")
                prefs.edit {
                    putString(KEY_SYNCED_FOLDER_URI, folderUri.toString())
                    putBoolean(KEY_FOLDER_MIGRATION_COMPLETED, true)
                }

                _internalState.update { it.copy(
                    syncedFolderUri = folderUri.toString(),
                    showFolderMigrationDialog = false
                ) }

                scanSyncedFolder()

                val workManager = WorkManager.getInstance(appContext)
                val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

                val syncRequest =
                    PeriodicWorkRequestBuilder<FolderSyncWorker>(4, TimeUnit.HOURS).setConstraints(
                        constraints
                    ).build()

                workManager.enqueueUniquePeriodicWork(
                    FolderSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, syncRequest
                )
                Timber.d("Scheduled periodic folder sync worker.")
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to take persistable URI permission for $folderUri")
                _internalState.update {
                    it.copy(errorMessage = "Could not get permission for the selected folder.")
                }
            }
        }
    }

    fun syncFolderMetadata() {
        triggerFolderSyncWorker(metadataOnly = true)
    }

    fun scanSyncedFolder() {
        triggerFolderSyncWorker(metadataOnly = false)
    }

    private fun triggerFolderSyncWorker(metadataOnly: Boolean) {
        @Suppress("UnusedVariable", "Unused") val folderUriString = _internalState.value.syncedFolderUri ?: return
        Timber.tag("FolderSync").d("Requesting folder sync (metadataOnly=$metadataOnly)")

        val workManager = WorkManager.getInstance(appContext)
        val data = androidx.work.Data.Builder()
            .putBoolean(FolderSyncWorker.KEY_METADATA_ONLY, metadataOnly)
            .build()

        val request = OneTimeWorkRequestBuilder<FolderSyncWorker>()
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            FolderSyncWorker.WORK_NAME_ONETIME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                            val msg = if (metadataOnly) "Folder Sync: Updating metadata..." else "Scanning folder for new books..."
                            _internalState.update { it.copy(
                                isLoading = false,
                                isRefreshing = true,
                                bannerMessage = BannerMessage(msg)
                            ) }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _internalState.update { it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                bannerMessage = BannerMessage("Folder Sync: Scan complete."),
                                lastFolderScanTime = System.currentTimeMillis()
                            ) }
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _internalState.update { it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = "Sync failed."
                            ) }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    fun disconnectSyncedFolder() {
        viewModelScope.launch {
            val folderUriString = _internalState.value.syncedFolderUri

            Timber.tag("FolderSync").d("Cancelling all folder sync workers...")
            WorkManager.getInstance(appContext).cancelUniqueWork(FolderSyncWorker.WORK_NAME)
            WorkManager.getInstance(appContext).cancelUniqueWork(FolderSyncWorker.WORK_NAME_ONETIME)
            WorkManager.getInstance(appContext).cancelUniqueWork(MetadataExtractionWorker.WORK_NAME)

            prefs.edit {
                remove(KEY_SYNCED_FOLDER_URI)
                remove(KEY_LAST_FOLDER_SCAN_TIME)
            }
            _internalState.update { it.copy(syncedFolderUri = null, lastFolderScanTime = null) }

            if (folderUriString != null) {
                Timber.tag("FolderSync").d("Disconnecting folder. Removing all associated books from DB.")
                recentFilesRepository.deleteFilesBySourceFolder(folderUriString)

                try {
                    val uri = folderUriString.toUri()
                    val contentResolver = appContext.contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.releasePersistableUriPermission(uri, takeFlags)
                    Timber.tag("FolderSync").d("Released permission for: $uri")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to release permission")
                }
            }
        }
    }

    private suspend fun prepareBookForImport(externalUri: Uri): Triple<Uri, String, FileType>? {
        val type = getFileTypeFromUri(externalUri, appContext)
        if (type == null) {
            Timber.e("Could not determine file type for external URI: $externalUri")
            return null
        }

        val hash = FileHasher.calculateSha256 {
            appContext.contentResolver.openInputStream(externalUri)
        }

        if (hash == null) {
            Timber.e("Failed to process file hash for $externalUri")
            return null
        }

        val existingItem = recentFilesRepository.getFileByBookId(hash)
        if (existingItem != null) {
            Timber.i("Book with ID: $hash already exists. Skipping import.")
            return null
        }

        Timber.i("Importing new book with ID: $hash")
        val internalFile = bookImporter.importBook(externalUri)
        if (internalFile == null) {
            Timber.e("Failed to copy book to internal storage for $externalUri")
            return null
        }

        return Triple(internalFile.toUri(), hash, type)
    }

    private fun downloadBook(item: RecentFileItem, openWhenComplete: Boolean = false): Job {
        if (!uiState.value.isSyncEnabled) {
            _internalState.update { it.copy(errorMessage = "Enable sync to download files.") }
            return viewModelScope.launch {}
        }
        if (uiState.value.downloadingBookIds.contains(item.bookId)) {
            Timber.d("Download for ${item.bookId} is already in progress. Ignoring request.")
            return viewModelScope.launch {}
        }
        return viewModelScope.launch {
            _internalState.update { state ->
                state.copy(downloadingBookIds = state.downloadingBookIds + item.bookId)
            }
            try {
                val accessToken = googleDriveRepository.getAccessToken(appContext)
                    ?: throw Exception("Not signed in or missing permissions")
                val remoteFiles =
                    googleDriveRepository.getFiles(accessToken)?.files.orEmpty().associateBy {
                        it.name
                    }

                val fileExtension = item.type.name.lowercase()
                val fileName = "${item.bookId}.$fileExtension"
                val driveFileId = remoteFiles[fileName]?.id

                if (driveFileId != null) {
                    val destinationFile = bookImporter.createBookFile(fileName)
                    Timber.d("Downloading book: ${item.displayName}")
                    if (googleDriveRepository.downloadFile(
                            accessToken, driveFileId, destinationFile
                        )
                    ) {
                        addFileToRecent(
                            destinationFile.toUri(),
                            item.type,
                            item.bookId,
                            customDisplayName = item.displayName,
                            isRecent = true,
                            sourceFolderUri = item.sourceFolderUri
                        )
                        if (openWhenComplete) {
                            openBook(
                                destinationFile.toUri(), item.bookId, item.type, item.displayName
                            )
                        }
                    } else {
                        throw Exception("Google Drive download failed.")
                    }
                } else {
                    throw Exception("File not found in Google Drive.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download book ${item.bookId}")
                _internalState.update {
                    it.copy(errorMessage = "Failed to download ${item.displayName}.")
                }
            } finally {
                _internalState.update { state ->
                    state.copy(downloadingBookIds = state.downloadingBookIds - item.bookId)
                }
            }
        }
    }

    fun deleteAllCloudAndLocalData() {
        if (!uiState.value.isSyncEnabled) {
            _internalState.update { it.copy(errorMessage = "Enable sync to clear cloud data.") }
            return
        }

        if (!googleDriveRepository.isUserSignedInToDrive(appContext)) {
            _internalState.update {
                it.copy(errorMessage = "Not signed in, cannot clear cloud data.")
            }
            return
        }

        _internalState.update {
            it.copy(
                isLoading = true,
                bannerMessage = BannerMessage("Clearing all cloud and local data...")
            )
        }

        viewModelScope.launch {
            try {
                // Clear local data
                recentFilesRepository.clearAllLocalData()
                clearBookCache()
                pdfTextRepository.clearAllText()
                pdfTextBoxRepository.clearAll()

                // Clear cloud data
                val accessToken = googleDriveRepository.getAccessToken(appContext)

                val success = if (accessToken != null) {
                    googleDriveRepository.deleteAllFiles(accessToken)
                } else {
                    false
                }

                val currentUser = uiState.value.currentUser
                if (currentUser != null) {
                    firestoreRepository.deleteAllUserFirestoreData(currentUser.uid)
                }

                if (success) {
                    _internalState.update {
                        it.copy(
                            isLoading = false, bannerMessage = BannerMessage(
                                "All cloud and local data cleared successfully."
                            )
                        )
                    }
                } else {
                    throw Exception("Failed to clear cloud data.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete all cloud and local user data.")
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = "Error: Failed to clear all data.")
                }
            }
        }
    }

    private fun triggerLegacyPurchaseMigration() {
        val user = _internalState.value.currentUser
        val isProOnBackend = _internalState.value.isProUser
        val localPurchases = billingClientWrapper.proUpgradeState.value.activePurchases

        val checkedUids = prefs.getStringSet(KEY_MIGRATION_CHECKED_UIDS, emptySet()) ?: emptySet()
        if (user != null && user.uid in checkedUids) {
            Timber.d(
                "Migration check for user ${user.uid} already performed on this device. Skipping."
            )
            return // Already checked, do nothing.
        }

        if (user != null && !isProOnBackend && localPurchases.isNotEmpty() && !migrationAttempted.value) {
            migrationAttempted.value = true
            Timber.i(
                "MIGRATION: Found legacy user with local purchase. Verifying with backend silently..."
            )
            val purchaseToVerify = localPurchases.first()

            verifyPurchaseWithBackend(purchaseToVerify, isSilentMigrationCheck = true)

            prefs.edit { putStringSet(KEY_MIGRATION_CHECKED_UIDS, checkedUids + user.uid) }
        }
    }

    fun deleteAllUserData() {
        val currentUser = _internalState.value.currentUser ?: return
        Timber.w("DESTRUCTIVE: Starting deletion of all user data for ${currentUser.uid}")
        _internalState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                recentFilesRepository.clearAllLocalData()
                pdfTextRepository.clearAllText()
                pdfTextBoxRepository.clearAll()
                prefs.edit { remove(KEY_LAST_SYNC_TIMESTAMP) }

                _internalState.update {
                    it.copy(
                        isLoading = false, bannerMessage = BannerMessage("All local data cleared.")
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete all user data.")
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = "Error: Failed to clear all data.")
                }
            }
        }
    }

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _internalState.update { it.copy(isLoading = true) }
            try {
                val user = authRepository.signIn(activityContext)
                if (user == null) {
                    _internalState.update {
                        it.copy(
                            bannerMessage = BannerMessage(
                                "Sign in failed. Please try again.", isError = true
                            ), isLoading = false
                        )
                    }
                } else {
                    _internalState.update { it.copy(isLoading = false) }
                }
            } catch (_: GetCredentialCancellationException) {
                Timber.d("Sign-in flow was cancelled by the user.")
                _internalState.update { it.copy(isLoading = false) }
            } catch (_: CancellationException) {
                Timber.d("Sign-in flow was cancelled by coroutine.")
                _internalState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "An unexpected error occurred during sign-in.")
                val errorMessage = if (e is NoCredentialException) {
                    "Could not find a Google account. This can happen on a fresh install, please try again in a moment."
                } else {
                    "An error occurred during sign in. Please check your internet connection."
                }
                _internalState.update {
                    it.copy(
                        bannerMessage = BannerMessage(errorMessage, isError = true),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val currentUser = _internalState.value.currentUser
            if (currentUser != null) {
                val deviceId = getInstallationId()
                try {
                    firestoreRepository.deleteDevice(currentUser.uid, deviceId)
                    Timber.i("Device $deviceId unregistered on sign out.")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to unregister device on sign out.")
                }
            }
            prefs.edit { remove(KEY_SYNC_ENABLED) }
            authRepository.signOut()
        }
    }

    fun showDeviceManagementForDebug() {
        if (!BuildConfig.DEBUG) return

        viewModelScope.launch {
            _internalState.value.currentUser?.let { user ->
                _internalState.update { it.copy(isLoading = true) }
                val registeredDevices = firestoreRepository.getRegisteredDevices(user.uid)
                val deviceItems = registeredDevices.map {
                    DeviceItem(it.deviceId, it.deviceName, it.lastSeen)
                }
                _internalState.update {
                    it.copy(
                        isLoading = false, deviceLimitState = DeviceLimitReachedState(
                            isLimitReached = true,
                            registeredDevices = deviceItems.sortedByDescending { item ->
                                item.lastSeen
                            }))
                }
            } ?: run {
                showBanner("Please sign in to test device management.", isError = true)
            }
        }
    }

    fun launchPurchaseFlow(activity: android.app.Activity) {
        Timber.d("Attempting to launch purchase flow. Pro state is: ${proUpgradeState.value}")
        billingClientWrapper.launchPurchaseFlow(activity)
    }

    fun clearBillingError() {
        billingClientWrapper.clearError()
    }

    fun setSyncEnabled(enabled: Boolean) {
        if (!uiState.value.isProUser) {
            Timber.d("Sync toggle blocked for free user.")
            _internalState.update { it.copy(errorMessage = "Sync is an Episteme Pro feature.") }
            return
        }

        prefs.edit { putBoolean(KEY_SYNC_ENABLED, enabled) }
        _internalState.update { it.copy(isSyncEnabled = enabled) }

        if (enabled) {
            viewModelScope.launch {
                if (googleDriveRepository.hasDrivePermissions(appContext)) {
                    syncWithCloud(showBanner = true)
                } else {
                    Timber.d("Requesting Drive permission from user.")
                    _internalState.update { it.copy(isRequestingDrivePermission = true) }
                }
            }
        }
    }

    fun setFolderSyncEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FOLDER_SYNC_ENABLED, enabled) }
        _internalState.update { it.copy(isFolderSyncEnabled = enabled) }

        if (enabled && uiState.value.isSyncEnabled) {
            viewModelScope.launch { syncWithCloud(showBanner = false) }
        }
    }

    private fun syncWithCloud(showBanner: Boolean = false) = viewModelScope.launch {
        val hasPermissions = googleDriveRepository.hasDrivePermissions(appContext)
        val currentUser = _internalState.value.currentUser

        if (!hasPermissions || currentUser == null) {
            if (showBanner) _internalState.update {
                it.copy(errorMessage = "Not signed in, cannot sync.")
            }
            return@launch
        }

        if (showBanner) {
            _internalState.update {
                it.copy(bannerMessage = BannerMessage("Cloud Sync: Checking for updates..."))
            }
        }

        try {
            val accessToken = googleDriveRepository.getAccessToken(appContext) ?: return@launch

            val deviceId = getInstallationId()
            val remoteBooksDeferred = async(Dispatchers.IO) {
                firestoreRepository.getAllBooks(currentUser.uid)
            }
            val remoteShelvesDeferred = async(Dispatchers.IO) {
                firestoreRepository.getAllShelves(currentUser.uid)
            }
            val localBooks = withContext(Dispatchers.IO) {
                val allFiles = recentFilesRepository.getAllFilesForSync()
                if (_internalState.value.isFolderSyncEnabled) {
                    allFiles
                } else {
                    allFiles.filter { it.sourceFolderUri == null }
                }
            }

            val localShelfNames = prefs.getStringSet(KEY_SHELVES, emptySet()).orEmpty()
            val allKnownShelfNames =
                (localShelfNames + remoteShelvesDeferred.await().map { it.name }).toSet()
            val localShelves = allKnownShelfNames.mapNotNull { name ->
                val timestamp = prefs.getLong("$KEY_SHELF_TIMESTAMP_PREFIX$name", 0L)
                if (timestamp == 0L && name !in localShelfNames) return@mapNotNull null
                val bookIds = prefs.getStringSet(
                    "$KEY_SHELF_CONTENT_PREFIX$name", emptySet()
                ).orEmpty().toList()
                val isDeleted = prefs.getBoolean("$KEY_SHELF_DELETED_PREFIX$name", false)
                ShelfMetadata(name, bookIds, timestamp, isDeleted)
            }

            val remoteBooks = remoteBooksDeferred.await()
            val remoteShelves = remoteShelvesDeferred.await()

            // 3. Merge Books
            val localBooksMap = localBooks.associateBy { it.bookId }
            val remoteBooksMap = remoteBooks.associateBy { it.bookId }
            val allBookIds = (localBooksMap.keys + remoteBooksMap.keys).distinct()

            allBookIds.forEach { bookId ->
                val local = localBooksMap[bookId]
                val remote = remoteBooksMap[bookId]

                if (local != null && remote != null) {
                    Timber.tag("AnnotationSync").d(
                        "Checking $bookId. LocalTS: ${local.lastModifiedTimestamp}, RemoteTS: ${remote.lastModifiedTimestamp}, RemoteHasAnn: ${remote.hasAnnotations}"
                    )
                }

                when {
                    local != null && remote == null -> {
                        uploadSingleBookMetadata(local)
                    }

                    local == null && remote != null -> {
                        recentFilesRepository.addRecentFile(remote.toRecentFileItem())
                        if (remote.hasAnnotations) {
                            downloadAnnotationsForBook(accessToken, bookId)
                        }
                    }

                    local != null && remote != null -> {
                        if (local.lastModifiedTimestamp > remote.lastModifiedTimestamp) {
                            uploadSingleBookMetadata(local)
                        } else {
                            val isMetadataNewer =
                                remote.lastModifiedTimestamp > local.lastModifiedTimestamp

                            if (isMetadataNewer) {
                                recentFilesRepository.addRecentFile(
                                    remote.toRecentFileItem()
                                )
                            }

                            val annotationFile =
                                pdfAnnotationRepository.getAnnotationFileForSync(bookId)
                            val localFileMissing = annotationFile == null
                            val fileLastModified = annotationFile?.lastModified() ?: 0L
                            val isFileStale =
                                remote.hasAnnotations && (remote.lastModifiedTimestamp > fileLastModified)

                            if (isMetadataNewer || localFileMissing && remote.hasAnnotations || isFileStale) {
                                Timber.tag("AnnotationSync").d("Triggering download for $bookId.")
                                downloadAnnotationsForBook(accessToken, bookId)
                            }
                        }
                    }
                }
            }

            val localShelvesMap = localShelves.associateBy { it.name }
            val remoteShelvesMap = remoteShelves.associateBy { it.name }
            val allShelfNames = (localShelvesMap.keys + remoteShelvesMap.keys).distinct()

            allShelfNames.forEach { shelfName ->
                val local = localShelvesMap[shelfName]
                val remote = remoteShelvesMap[shelfName]

                when {
                    local != null && remote == null -> firestoreRepository.syncShelf(
                        currentUser.uid,
                        local,
                        deviceId
                    )

                    local == null && remote != null -> {
                        prefs.edit {
                            val currentShelves =
                                prefs.getStringSet(KEY_SHELVES, emptySet())?.toMutableSet()
                                    ?: mutableSetOf()
                            if (remote.isDeleted) {
                                currentShelves.remove(remote.name)
                                remove("$KEY_SHELF_CONTENT_PREFIX${remote.name}")
                            } else {
                                currentShelves.add(remote.name)
                                putStringSet(
                                    "$KEY_SHELF_CONTENT_PREFIX${remote.name}",
                                    remote.bookIds.toSet()
                                )
                            }
                            putStringSet(KEY_SHELVES, currentShelves)
                            putLong(
                                "$KEY_SHELF_TIMESTAMP_PREFIX${remote.name}",
                                remote.lastModifiedTimestamp
                            )
                            putBoolean(
                                "$KEY_SHELF_DELETED_PREFIX${remote.name}", remote.isDeleted
                            )
                        }
                    }

                    local != null && remote != null -> {
                        if (local.lastModifiedTimestamp > remote.lastModifiedTimestamp) {
                            firestoreRepository.syncShelf(currentUser.uid, local, deviceId)
                        } else if (remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                            prefs.edit {
                                val currentShelves =
                                    prefs.getStringSet(KEY_SHELVES, emptySet())?.toMutableSet()
                                        ?: mutableSetOf()
                                if (remote.isDeleted) {
                                    currentShelves.remove(remote.name)
                                    remove("$KEY_SHELF_CONTENT_PREFIX${remote.name}")
                                } else {
                                    currentShelves.add(remote.name)
                                    putStringSet(
                                        "$KEY_SHELF_CONTENT_PREFIX${remote.name}",
                                        remote.bookIds.toSet()
                                    )
                                }
                                putStringSet(KEY_SHELVES, currentShelves)
                                putLong(
                                    "$KEY_SHELF_TIMESTAMP_PREFIX${remote.name}",
                                    remote.lastModifiedTimestamp
                                )
                                putBoolean(
                                    "$KEY_SHELF_DELETED_PREFIX${remote.name}", remote.isDeleted
                                )
                            }
                        }
                    }
                }
            }

            val finalMergedBooks = withContext(Dispatchers.IO) {
                recentFilesRepository.getAllFilesForSync()
            }
            val remoteFiles = withContext(Dispatchers.IO) {
                googleDriveRepository.getFiles(accessToken)?.files.orEmpty().associateBy { it.name }
            }

            val downloadJobs = mutableListOf<Job>()

            finalMergedBooks.forEach { book ->
                val fileExtension = book.type.name.lowercase()
                val fileName = "${book.bookId}.$fileExtension"
                if (book.isDeleted) {
                    remoteFiles[fileName]?.id?.let { fileId ->
                        Timber.d("Deleting from Drive: $fileName")
                        googleDriveRepository.deleteDriveFile(accessToken, fileId)
                    }
                    recentFilesRepository.deleteFilePermanently(listOf(book.bookId))
                } else if (book.isAvailable && !remoteFiles.containsKey(fileName)) {
                    book.getUri()?.path?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            Timber.d("Uploading book: ${book.displayName}")
                            googleDriveRepository.uploadFile(
                                accessToken, book.bookId, file, book.type
                            )
                        }
                    }
                } else if (!book.isAvailable && remoteFiles.containsKey(fileName)) {
                    Timber.d("Sync: Triggering auto-download for ${book.displayName}")
                    downloadJobs.add(downloadBook(book))
                }
            }

            downloadJobs.joinAll()
            syncFonts(currentUser.uid)

            if (showBanner) {
                _internalState.update {
                    it.copy(
                        isLoading = false, bannerMessage = BannerMessage("Cloud Sync: Complete.")
                    )
                }
            }
        } catch (e: Exception) {
            Timber.tag("AnnotationSync").e(e, "Error during cloud sync")
            if (showBanner) {
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to sync library.")
                }
            }
        }
    }

    private suspend fun downloadAnnotationsForBook(accessToken: String, bookId: String) {
        // We download to a temp location first to inspect the content
        val tempDownloadFile = File(appContext.cacheDir, "temp_download_${bookId}.json")

        Timber.tag("AnnotationSync").d("Attempting download of bundle for $bookId.")

        val didDownload =
            googleDriveRepository.downloadAnnotationFile(accessToken, bookId, tempDownloadFile)

        if (didDownload && tempDownloadFile.exists()) {
            Timber.tag("AnnotationSync")
                .d("Download SUCCESS. Size: ${tempDownloadFile.length()}. Unpacking...")

            try {
                val jsonString = tempDownloadFile.readText()

                // Determine format
                val isBundle = try {
                    val obj = JSONObject(jsonString)
                    obj.has("version") || obj.has("ink") || obj.has("text") || obj.has("layout")
                } catch (_: Exception) {
                    false
                }

                val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId) ?: File(
                    appContext.filesDir,
                    "annotations/annotation_$bookId.json"
                )
                val richTextFile = pdfRichTextRepository.getFileForSync(bookId)
                val layoutFile = pageLayoutRepository.getLayoutFile(bookId)
                val textBoxFile = pdfTextBoxRepository.getFileForSync(bookId)

                // Ensure directories exist
                inkFile.parentFile?.mkdirs()
                richTextFile.parentFile?.mkdirs()
                layoutFile.parentFile?.mkdirs()

                if (isBundle) {
                    val bundle = JSONObject(jsonString)

                    // 1. Ink
                    if (bundle.has("ink")) {
                        inkFile.writeText(bundle.getJSONArray("ink").toString())
                    } else {
                        // If bundle exists but no ink key, implies ink was deleted or empty
                        if (inkFile.exists()) inkFile.delete()
                    }

                    // 2. Text
                    if (bundle.has("text")) {
                        richTextFile.writeText(bundle.getJSONArray("text").toString())
                    } else {
                        if (richTextFile.exists()) richTextFile.delete()
                    }

                    // 3. Layout
                    if (bundle.has("layout")) {
                        layoutFile.writeText(bundle.getJSONArray("layout").toString())
                    } else {
                        if (layoutFile.exists()) layoutFile.delete()
                    }

                    // 4. Text Boxes
                    if (bundle.has("textBoxes")) {
                        textBoxFile.writeText(bundle.getJSONArray("textBoxes").toString())
                    } else {
                        if (textBoxFile.exists()) textBoxFile.delete()
                    }

                    Timber.tag("AnnotationSync").d("Unpacked unified bundle.")
                } else {
                    Timber.tag("AnnotationSync").d("Detected legacy format (Ink only).")
                    inkFile.writeText(jsonString)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error unpacking synced annotation data")
            } finally {
                tempDownloadFile.delete()
            }
        } else {
            Timber.tag("AnnotationSync")
                .d("FAILURE: No bundle found on Drive for $bookId (or download failed)")
        }
    }

    private suspend fun addFileToRecent(
        uri: Uri,
        type: FileType,
        bookId: String,
        epubBook: EpubBook? = null,
        customDisplayName: String? = null,
        isRecent: Boolean,
        sourceFolderUri: String? = null
    ) = withContext(Dispatchers.IO) {
        val isNewBook = withContext(Dispatchers.IO) {
            recentFilesRepository.getFileByBookId(bookId) == null
        }

        val existingItem = recentFilesRepository.getFileByBookId(bookId)
        val displayName = customDisplayName ?: existingItem?.displayName ?: getFileNameFromUri(
            uri,
            appContext
        ) ?: "Unknown File"

        var coverPath: String? = null
        var title: String? = null
        var author: String? = null
        var bookForMetadata = epubBook

        if (bookForMetadata == null && (type == FileType.EPUB || type == FileType.MOBI || type == FileType.MD || type == FileType.TXT || type == FileType.HTML)) {
            Timber.d("Parsing downloaded book for cover/metadata: $displayName")
            try {
                importMutex.withLock {
                    bookForMetadata = withContext(Dispatchers.IO) {
                        appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                            when (type) {
                                FileType.EPUB -> {
                                    epubParser.createEpubBook(
                                        inputStream = inputStream,
                                        originalBookNameHint = displayName,
                                        parseContent = false
                                    )
                                }

                                FileType.MOBI -> {
                                    mobiParser.createMobiBook(
                                        inputStream = inputStream, originalBookNameHint = displayName
                                    )
                                }

                                else -> {
                                    singleFileImporter.importSingleFile(
                                        inputStream,
                                        type,
                                        originalBookNameHint = displayName
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Failed to parse metadata for book: $displayName. Proceeding with basic info."
                )
                bookForMetadata = null
            }
        }

        val finalBookMetadata = bookForMetadata

        if ((type == FileType.EPUB || type == FileType.MOBI || type == FileType.MD || type == FileType.TXT || type == FileType.HTML) && finalBookMetadata != null) {
            title = finalBookMetadata.title.takeIf { it.isNotBlank() && it != "content" } ?: displayName

            author = finalBookMetadata.author.takeIf {
                it.isNotBlank() && !it.equals("Unknown", ignoreCase = true)
            }

            finalBookMetadata.coverImage?.let { cover ->
                coverPath = recentFilesRepository.saveCoverToCache(cover, uri)
            }
        } else if (type == FileType.PDF) {
            title = displayName
            val pdfCoverGenerator = PdfCoverGenerator(appContext)
            val coverBitmap = pdfCoverGenerator.generateCover(uri)
            if (coverBitmap != null) {
                coverPath = recentFilesRepository.saveCoverToCache(coverBitmap, uri)
            }
        }

        val newLastModifiedTimestamp =
            existingItem?.lastModifiedTimestamp ?: System.currentTimeMillis()

        val newItem = RecentFileItem(
            bookId = bookId,
            uriString = uri.toString(),
            type = type,
            displayName = displayName,
            timestamp = System.currentTimeMillis(),
            coverImagePath = coverPath,
            title = title,
            author = author,
            isAvailable = true,
            lastModifiedTimestamp = newLastModifiedTimestamp,
            isDeleted = false,
            isRecent = isRecent,
            sourceFolderUri = sourceFolderUri
        )
        recentFilesRepository.addRecentFile(newItem)
        Timber.i("Added/Updated $displayName ($type) to recent files via repository.")

        if (isNewBook) {
            uploadNewBookAndMetadata(newItem)
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _internalState.update { it.copy(sortOrder = sortOrder) }
        prefs.edit { putString(KEY_SORT_ORDER, sortOrder.name) }
    }

    fun bannerMessageShown() {
        _internalState.update { it.copy(bannerMessage = null) }
    }

    fun showBanner(message: String, isError: Boolean = false) {
        _internalState.update { it.copy(bannerMessage = BannerMessage(message, isError)) }
    }

    fun errorMessageShown() {
        _internalState.update { it.copy(errorMessage = null) }
    }

    private fun getFileNameFromUri(uri: Uri, context: Context): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path
            val cut = fileName?.lastIndexOf('/')
            if (cut != -1) {
                fileName = fileName?.substring(cut!! + 1)
            }
        }
        return fileName ?: uri.lastPathSegment
    }

    fun onFileSelected(uri: Uri, isFromRecent: Boolean = false) {
        if (isFromRecent) {
            Timber.i("Opening recent file: $uri")
            // This path is now handled by onRecentFileClicked to preserve the bookId
            // We find the book by URI to open it.
            viewModelScope.launch {
                val item = recentFilesRepository.getFileByUri(uri.toString())
                if (item != null) {
                    openBook(uri, item.bookId, item.type, item.displayName)
                } else {
                    _internalState.update { it.copy(errorMessage = "Could not find recent item.") }
                }
            }
        } else {
            Timber.i("Importing new file: $uri")
            importExternalFile(uri)
        }
    }

    private fun importExternalFile(externalUri: Uri) {
        _internalState.update {
            it.copy(isLoading = true, errorMessage = null, contextualActionItems = emptySet())
        }

        viewModelScope.launch {
            val importResult = prepareBookForImport(externalUri)

            if (importResult != null) {
                val (internalUri, bookId, type) = importResult
                val displayName = getFileNameFromUri(externalUri, appContext) ?: "Unknown File"
                openBook(
                    internalUri, bookId = bookId, type = type, originalDisplayName = displayName
                )
            } else {
                val hash = FileHasher.calculateSha256 {
                    appContext.contentResolver.openInputStream(externalUri)
                }
                if (hash != null) {
                    val existingItem = recentFilesRepository.getFileByBookId(hash)
                    if (existingItem != null) {
                        Timber.i("Re-selected an existing book. Opening it.")
                        onRecentFileClicked(existingItem)
                        _internalState.update { it.copy(isLoading = false) }
                        return@launch
                    }
                }
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to import file.")
                }
            }
        }
    }

    private fun openBook(
        uri: Uri, bookId: String, type: FileType, originalDisplayName: String? = null
    ) {
        Timber.d("Opening book with determined type: $type for bookId: $bookId")

        _internalState.update {
            it.copy(
                selectedPdfUri = null,
                selectedEpubUri = null,
                selectedBookId = bookId,
                selectedEpubBook = null,
                selectedFileType = type,
                isLoading = true,
                errorMessage = null,
                initialLocator = null,
                initialPageInBook = null
            )
        }

        if (type == FileType.PDF) {
            viewModelScope.launch {
                val recentItem = recentFilesRepository.getFileByBookId(bookId)

                if (recentItem?.sourceFolderUri != null) {
                    launch(Dispatchers.IO) {
                        recentFilesRepository.syncLocalMetadataToFolder(bookId)
                    }
                }

                Timber.d("openBook: Loading PDF. bookId=$bookId ...")
                _internalState.update {
                    it.copy(
                        selectedPdfUri = uri,
                        initialPageInBook = recentItem?.lastPage,
                        initialBookmarksJson = recentItem?.bookmarksJson,
                        isLoading = false
                    )
                }
                addFileToRecent(
                    uri,
                    type,
                    bookId,
                    customDisplayName = originalDisplayName,
                    isRecent = true,
                    sourceFolderUri = null
                )
            }
        } else if (type == FileType.EPUB || type == FileType.MOBI || type == FileType.MD || type == FileType.TXT || type == FileType.HTML) {
            viewModelScope.launch {
                val recentItem = recentFilesRepository.getFileByBookId(bookId)
                if (recentItem?.sourceFolderUri != null) {
                    launch(Dispatchers.IO) {
                        recentFilesRepository.syncLocalMetadataToFolder(bookId)
                    }
                }
                val locator =
                    if (recentItem?.lastChapterIndex != null && recentItem.locatorBlockIndex != null && recentItem.locatorCharOffset != null) {
                        Locator(
                            chapterIndex = recentItem.lastChapterIndex,
                            blockIndex = recentItem.locatorBlockIndex,
                            charOffset = recentItem.locatorCharOffset
                        )
                    } else {
                        null
                    }

                _internalState.update {
                    it.copy(
                        selectedEpubUri = uri,
                        initialLocator = locator,
                        initialCfi = recentItem?.lastPositionCfi,
                        initialBookmarksJson = recentItem?.bookmarksJson
                    )
                }

                when (type) {
                    FileType.EPUB -> {
                        loadEpub(uri, bookId, customDisplayName = originalDisplayName)
                    }
                    FileType.MOBI -> {
                        loadMobi(uri, bookId, customDisplayName = originalDisplayName)
                    }
                    else -> {
                        loadSingleFile(uri, bookId, type, customDisplayName = originalDisplayName)
                    }
                }
            }
        }
    }

    private fun loadSingleFile(uri: Uri, bookId: String, type: FileType, customDisplayName: String? = null) {
        viewModelScope.launch {
            if (!_internalState.value.isLoading) {
                _internalState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            Timber.d("Starting Single File import ($type) for URI: $uri")
            try {
                val epubBook = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) {
                            throw Exception("Could not open input stream for URI")
                        }
                        // singleFileImporter is now actually an instance of SingleFileImporter (see below)
                        // You will need to rename the class inside SingleFileImporter.kt
                        singleFileImporter.importSingleFile(
                            inputStream,
                            type,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(
                                uri,
                                appContext
                            ) ?: "unknown_doc"
                        )
                    }
                }

                Timber.i("Import successful ($type). Title: ${epubBook.title}")
                addFileToRecent(
                    uri,
                    type,
                    bookId,
                    epubBook,
                    customDisplayName,
                    isRecent = true,
                    sourceFolderUri = null
                )

                _internalState.update { it.copy(selectedEpubBook = epubBook, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing file ($type) for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = "Failed to load file: ${e.message}", isLoading = false)
                }
            }
        }
    }

    fun setRenderMode(newMode: RenderMode) {
        _internalState.update { it.copy(renderMode = newMode) }
        prefs.edit { putString(KEY_RENDER_MODE, newMode.name) }
    }

    private fun getFileTypeFromUri(uri: Uri, context: Context): FileType? {
        val mimeType = context.contentResolver.getType(uri)
        val fileName = getFileNameFromUri(uri, context)

        Timber.d("Determining type for: $uri | Mime: $mimeType | Name: $fileName")

        return when (mimeType) {
            "application/pdf" -> FileType.PDF
            "application/epub+zip" -> FileType.EPUB
            "application/x-mobipocket-ebook", "application/vnd.amazon.ebook", "application/vnd.amazon.mobi8-ebook" -> FileType.MOBI
            "text/markdown", "text/x-markdown" -> FileType.MD
            "text/html", "application/xhtml+xml" -> FileType.HTML
            "text/plain" -> {
                if (fileName?.endsWith(".md", ignoreCase = true) == true || fileName?.endsWith(".markdown", ignoreCase = true) == true) {
                    FileType.MD
                } else {
                    FileType.TXT
                }
            }
            else -> {
                when {
                    fileName?.endsWith(".pdf", ignoreCase = true) == true -> FileType.PDF
                    fileName?.endsWith(".epub", ignoreCase = true) == true -> FileType.EPUB
                    fileName?.endsWith(".mobi", ignoreCase = true) == true || fileName?.endsWith(".azw3", ignoreCase = true) == true || fileName?.endsWith(".prc", ignoreCase = true) == true -> FileType.MOBI
                    fileName?.endsWith(".md", ignoreCase = true) == true || fileName?.endsWith(".markdown", ignoreCase = true) == true -> FileType.MD
                    fileName?.endsWith(".txt", ignoreCase = true) == true -> FileType.TXT
                    fileName?.endsWith(".html", ignoreCase = true) == true || fileName?.endsWith(".xhtml", ignoreCase = true) == true || fileName?.endsWith(".htm", ignoreCase = true) == true -> FileType.HTML
                    else -> null
                }
            }
        }
    }

    private fun loadMobi(uri: Uri, bookId: String, customDisplayName: String? = null) {
        viewModelScope.launch {
            if (!_internalState.value.isLoading) {
                _internalState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            Timber.d("Starting MOBI parsing for URI: $uri")
            try {
                val mobiAsEpubBook = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) {
                            throw Exception("Could not open input stream for URI")
                        }
                        mobiParser.createMobiBook(
                            inputStream,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(
                                uri,
                                appContext
                            ) ?: "unknown.mobi"
                        )
                    }
                }

                if (mobiAsEpubBook != null) {
                    Timber.i("MOBI parsing successful. Title: ${mobiAsEpubBook.title}")
                    addFileToRecent(
                        uri,
                        FileType.MOBI,
                        bookId,
                        mobiAsEpubBook,
                        customDisplayName,
                        isRecent = true,
                        sourceFolderUri = null
                    )
                    _internalState.update {
                        it.copy(selectedEpubBook = mobiAsEpubBook, isLoading = false)
                    }
                } else {
                    throw Exception(
                        "MobiParser returned null. The file might be DRM-protected or invalid."
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing MOBI for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = "Failed to load MOBI: ${e.message}", isLoading = false)
                }
            }
        }
    }

    private fun loadEpub(uri: Uri, bookId: String, customDisplayName: String? = null) {
        viewModelScope.launch {
            if (!_internalState.value.isLoading) {
                _internalState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            Timber.d("Starting EPUB parsing for URI: $uri")
            try {
                val epubBook = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) {
                            throw Exception("Could not open input stream for URI")
                        }
                        epubParser.createEpubBook(
                            inputStream,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(
                                uri,
                                appContext
                            ) ?: "unknown.epub"
                        )
                    }
                }
                Timber.i("EPUB parsing successful. Title: ${epubBook.title}")

                addFileToRecent(
                    uri,
                    FileType.EPUB,
                    bookId,
                    epubBook,
                    customDisplayName,
                    isRecent = true,
                    sourceFolderUri = null
                )

                _internalState.update { it.copy(selectedEpubBook = epubBook, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing EPUB for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = "Failed to load EPUB: ${e.message}", isLoading = false)
                }
            }
        }
    }

    fun saveEpubReadingPosition(
        uri: Uri, locator: Locator, cfiForWebView: String?, progress: Float
    ) {
        Timber.d("Saving EPUB position locally: URI=$uri, Locator=$locator")
        viewModelScope.launch {
            recentFilesRepository.getFileByUri(uri.toString())?.let { _ ->
                recentFilesRepository.updateEpubReadingPosition(
                    uriString = uri.toString(),
                    locator = locator,
                    cfiForWebView = cfiForWebView,
                    progress = progress
                )
            }
        }
    }

    fun saveBookmarks(bookId: String, bookmarksJson: String) {
        Timber.d("saveBookmarks called. bookId=$bookId, bookmarksJson=$bookmarksJson")
        viewModelScope.launch {
            val currentBookUri =
                _internalState.value.selectedPdfUri ?: _internalState.value.selectedEpubUri
            Timber.d("saveBookmarks: currentBookUri is $currentBookUri")

            if (currentBookUri != null) {
                recentFilesRepository.getFileByUri(currentBookUri.toString())?.let { item ->
                    Timber.d(
                        "saveBookmarks: Found item by URI. Updating bookmarks for bookId=${item.bookId}"
                    )
                    recentFilesRepository.updateBookmarks(item.bookId, bookmarksJson)
                }
            } else if (bookId.isNotBlank()) {
                Timber.d(
                    "saveBookmarks: URI is null, but bookId is present. Updating bookmarks for bookId=$bookId"
                )
                recentFilesRepository.updateBookmarks(bookId, bookmarksJson)
            } else {
                Timber.w(
                    "PdfBookmarkDebug: saveBookmarks called with no active URI and empty bookId."
                )
            }
        }
    }

    fun savePdfReadingPosition(page: Int, totalPages: Int) {
        val currentPdfUri = _internalState.value.selectedPdfUri
        if (currentPdfUri != null) {
            val progress = if (totalPages > 0) {
                ((page + 1).toFloat() / totalPages.toFloat()) * 100f
            } else {
                0f
            }
            Timber.d("Saving PDF position locally: URI=$currentPdfUri, Page=$page")
            viewModelScope.launch {
                recentFilesRepository.getFileByUri(currentPdfUri.toString())?.let { _ ->
                    recentFilesRepository.updatePdfReadingPosition(
                        uriString = currentPdfUri.toString(),
                        page = page,
                        progress = progress
                    )
                }
            }
        }
    }

    fun refreshLibrary() {
        val syncEnabled = _internalState.value.isSyncEnabled
        val hasFolder = _internalState.value.syncedFolderUri != null // Check for URI instead of toggle

        if (!syncEnabled && !hasFolder) {
            Timber.d("Refresh skipped: No sync methods active.")
            _internalState.update { it.copy(isRefreshing = false) } // Ensure indicator retracts immediately
            return
        }

        viewModelScope.launch {
            _internalState.update { it.copy(isRefreshing = true) }

            try {
                if (syncEnabled) {
                    syncWithCloud(showBanner = false).join()
                }

                if (hasFolder) {
                    // This triggers the worker which we observe above to clear isRefreshing
                    syncFolderMetadata()
                }
            } catch (e: Exception) {
                Timber.e(e, "Refresh failed")
                _internalState.update { it.copy(isRefreshing = false) }
            } finally {
                // If folder sync isn't running, we must close the indicator here
                if (!hasFolder) {
                    _internalState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun clearBookCache() {
        viewModelScope.launch {
            bookCacheDao.clearAllCache()
            WorkManager.getInstance(getApplication())
                .cancelAllWorkByTag(BookProcessingWorker.WORK_TAG)
            Timber.i("Book cache has been cleared and all processing workers cancelled.")
        }
    }

    fun onRecentFileClicked(item: RecentFileItem) {
        val currentSelection = _internalState.value.contextualActionItems
        if (currentSelection.isNotEmpty()) {
            Timber.d("Toggling selection for: ${item.displayName}")
            val newSelection = if (currentSelection.any { it.bookId == item.bookId }) {
                currentSelection.filterNot { it.bookId == item.bookId }.toSet()
            } else {
                currentSelection + item
            }
            _internalState.update { it.copy(contextualActionItems = newSelection) }
            Timber.d("New selection size: ${newSelection.size}")
        } else {
            if (item.sourceFolderUri != null && item.uriString != null) {
                viewModelScope.launch {
                    val exists = try {
                        val uri = item.uriString.toUri()
                        DocumentFile.fromSingleUri(appContext, uri)?.exists() == true
                    } catch (_: Exception) { false }

                    if (!exists) {
                        Timber.tag("FolderSync").i("LazyCleanup: File ${item.displayName} missing. Removing.")
                        recentFilesRepository.deleteFilePermanently(listOf(item.bookId))
                        showBanner("File deleted from folder. Removed from library.")
                        return@launch
                    }

                    Timber.d("Recent file clicked (opening): ${item.displayName}")
                    if (item.isAvailable) {
                        item.getUri()?.let { uri ->
                            openBook(uri, item.bookId, item.type, item.displayName)
                        } ?: run {
                            _internalState.update { it.copy(errorMessage = "Could not find file location.") }
                        }
                    } else {
                        downloadBook(item, openWhenComplete = true)
                    }
                }
                return
            }

            Timber.d("Recent file clicked (opening): ${item.displayName}")
            if (item.isAvailable) {
                item.getUri()?.let { uri ->
                    openBook(uri, item.bookId, item.type, item.displayName)
                } ?: run {
                    _internalState.update { it.copy(errorMessage = "Could not find file location.") }
                    return
                }
            } else {
                downloadBook(item, openWhenComplete = true)
            }
        }
    }

    private fun uploadNewBookAndMetadata(book: RecentFileItem) {
        if (!uiState.value.isSyncEnabled) return

        viewModelScope.launch {
            _internalState.update { it.copy(uploadingBookIds = it.uploadingBookIds + book.bookId) }
            try {
                val accessToken = googleDriveRepository.getAccessToken(appContext) ?: return@launch

                book.getUri()?.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        Timber.d("Uploading newly added book content: ${book.displayName}")
                        val uploadedFile = googleDriveRepository.uploadFile(
                            accessToken, book.bookId, file, book.type
                        )
                        if (uploadedFile != null) {
                            Timber.d("Upload successful, now syncing metadata for ${book.bookId}")
                            val latestBookState = recentFilesRepository.getFileByBookId(book.bookId)
                            if (latestBookState != null) {
                                uploadSingleBookMetadata(latestBookState)
                            } else {
                                uploadSingleBookMetadata(book)
                            }
                        } else {
                            Timber.e("Google Drive upload returned null for ${book.bookId}")
                        }
                    } else {
                        Timber.w("File for new book upload does not exist at path: $path")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload new book content for bookId: ${book.bookId}")
            } finally {
                _internalState.update {
                    it.copy(uploadingBookIds = it.uploadingBookIds - book.bookId)
                }
            }
        }
    }

    fun onRecentItemLongPress(item: RecentFileItem) {
        val currentSelection = _internalState.value.contextualActionItems
        Timber.d(
            "Long press on: ${item.displayName}. Current selection size: ${currentSelection.size}"
        )
        if (currentSelection.none { it.bookId == item.bookId }) {
            _internalState.update { it.copy(contextualActionItems = currentSelection + item) }
        }
        Timber.d("New selection size: ${_internalState.value.contextualActionItems.size}")
    }

    fun selectAllRecentFiles() {
        val recentFilesForHome = uiState.value.recentFiles.filter { it.isRecent }
        _internalState.update { it.copy(contextualActionItems = recentFilesForHome.toSet()) }
    }

    fun selectAllLibraryFiles() {
        _internalState.update { it.copy(contextualActionItems = uiState.value.recentFiles.toSet()) }
    }

    fun clearContextualAction() {
        Timber.d("Clearing contextual action mode.")
        if (_internalState.value.contextualActionItems.isNotEmpty()) {
            _internalState.update { it.copy(contextualActionItems = emptySet()) }
        }
    }

    fun showCreateShelfDialog() {
        _internalState.update { it.copy(showCreateShelfDialog = true) }
    }

    fun dismissCreateShelfDialog() {
        _internalState.update { it.copy(showCreateShelfDialog = false) }
    }

    fun createShelf(name: String) {
        if (name.isNotBlank()) {
            val currentShelves = prefs.getStringSet(KEY_SHELVES, emptySet()) ?: emptySet()
            val newTimestamp = System.currentTimeMillis()
            prefs.edit {
                putStringSet(KEY_SHELVES, currentShelves + name)
                putLong("$KEY_SHELF_TIMESTAMP_PREFIX$name", newTimestamp)
                putStringSet("$KEY_SHELF_CONTENT_PREFIX$name", emptySet())
                putBoolean("$KEY_SHELF_DELETED_PREFIX$name", false)
            }
            dismissCreateShelfDialog()
            syncShelfChangeToFirestore(name)
        }
    }

    fun setMainScreenPage(page: Int) {
        _internalState.update { it.copy(mainScreenStartPage = page) }
    }

    fun setLibraryScreenPage(page: Int) {
        _internalState.update { it.copy(libraryScreenStartPage = page) }
    }

    fun navigateToShelf(name: String) {
        _internalState.update {
            it.copy(viewingShelfName = name, mainScreenStartPage = 1, libraryScreenStartPage = 1)
        }
    }

    fun showRenameShelfDialog(shelfName: String) {
        _internalState.update { it.copy(showRenameShelfDialogFor = shelfName) }
    }

    fun dismissRenameShelfDialog() {
        _internalState.update { it.copy(showRenameShelfDialogFor = null) }
    }

    fun showDeleteShelfDialog(shelfName: String) {
        _internalState.update { it.copy(showDeleteShelfDialogFor = shelfName) }
    }

    fun dismissDeleteShelfDialog() {
        _internalState.update { it.copy(showDeleteShelfDialogFor = null) }
    }

    fun renameShelf(oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank() || oldName == newName) {
            dismissRenameShelfDialog()
            return
        }

        val currentShelves =
            prefs.getStringSet(KEY_SHELVES, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (newName in currentShelves) {
            Timber.w("Cannot rename shelf. A shelf with the name '$newName' already exists.")
            _internalState.update {
                it.copy(errorMessage = "A shelf with that name already exists.")
            }
            dismissRenameShelfDialog()
            return
        }

        val oldContentKey = "$KEY_SHELF_CONTENT_PREFIX$oldName"
        val shelfContent = prefs.getStringSet(oldContentKey, emptySet()) ?: emptySet()
        val newTimestamp = System.currentTimeMillis()

        prefs.edit {
            currentShelves.remove(oldName)
            putBoolean("$KEY_SHELF_DELETED_PREFIX$oldName", true)
            putLong("$KEY_SHELF_TIMESTAMP_PREFIX$oldName", newTimestamp)

            currentShelves.add(newName)
            putStringSet(KEY_SHELVES, currentShelves)
            putStringSet("$KEY_SHELF_CONTENT_PREFIX$newName", shelfContent)
            putLong("$KEY_SHELF_TIMESTAMP_PREFIX$newName", newTimestamp)
        }

        syncShelfChangeToFirestore(oldName)
        syncShelfChangeToFirestore(newName)

        _internalState.update { it.copy(viewingShelfName = newName) }
        dismissRenameShelfDialog()
    }

    fun deleteShelf(shelfName: String) {
        if (shelfName.isBlank() || shelfName == "Unshelved") {
            dismissDeleteShelfDialog()
            return
        }

        _internalState.update {
            it.copy(
                viewingShelfName = null,
                isAddingBooksToShelf = false,
                showDeleteShelfDialogFor = null
            )
        }

        prefs.edit {
            val currentShelves =
                prefs.getStringSet(KEY_SHELVES, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentShelves.remove(shelfName)
            putStringSet(KEY_SHELVES, currentShelves)
            putBoolean("$KEY_SHELF_DELETED_PREFIX$shelfName", true)
            putLong("$KEY_SHELF_TIMESTAMP_PREFIX$shelfName", System.currentTimeMillis())
        }
        syncShelfChangeToFirestore(shelfName)
    }

    fun unselectShelf() {
        _internalState.update { it.copy(viewingShelfName = null, isAddingBooksToShelf = false) }
    }

    fun removeContextualItemsFromShelf() {
        val shelfName = _internalState.value.viewingShelfName
        if (shelfName.isNullOrBlank() || shelfName == "Unshelved") {
            Timber.w("Attempted to remove items from an invalid or unshelved shelf: $shelfName")
            clearContextualAction()
            return
        }

        val bookIdsToRemove = _internalState.value.contextualActionItems.map { it.bookId }.toSet()
        if (bookIdsToRemove.isEmpty()) {
            Timber.w("removeContextualItemsFromShelf called but no items were selected.")
            clearContextualAction()
            return
        }

        Timber.d("Removing ${bookIdsToRemove.size} book(s) from shelf '$shelfName'.")
        val key = "$KEY_SHELF_CONTENT_PREFIX$shelfName"
        val currentBookIds = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()

        currentBookIds.removeAll(bookIdsToRemove)

        prefs.edit {
            putStringSet(key, currentBookIds)
            putLong("$KEY_SHELF_TIMESTAMP_PREFIX$shelfName", System.currentTimeMillis())
        }
        Timber.d(
            "Successfully removed books. Shelf '$shelfName' now has ${currentBookIds.size} books."
        )

        clearContextualAction()
        syncShelfChangeToFirestore(shelfName)
    }

    fun onShelfClick(shelf: Shelf) {
        if (_internalState.value.contextualActionShelfNames.isNotEmpty()) {
            toggleShelfSelection(shelf.name)
        } else {
            navigateToShelf(shelf.name)
        }
    }

    private fun toggleShelfSelection(shelfName: String) {
        if (shelfName == "Unshelved") return

        _internalState.update { state ->
            val currentSelection = state.contextualActionShelfNames
            val newSelection = if (shelfName in currentSelection) {
                currentSelection - shelfName
            } else {
                currentSelection + shelfName
            }
            state.copy(contextualActionShelfNames = newSelection)
        }
    }

    fun onShelfLongPress(shelf: Shelf) {
        if (shelf.name == "Unshelved") return // Cannot select "Unshelved"
        val currentSelection = _internalState.value.contextualActionShelfNames
        if (shelf.name !in currentSelection) {
            _internalState.update {
                it.copy(contextualActionShelfNames = currentSelection + shelf.name)
            }
        }
    }

    fun clearShelfContextualAction() {
        if (_internalState.value.contextualActionShelfNames.isNotEmpty()) {
            _internalState.update { it.copy(contextualActionShelfNames = emptySet()) }
        }
    }

    fun deleteSelectedShelves() {
        val shelvesToDelete =
            _internalState.value.contextualActionShelfNames.filter { it != "Unshelved" }
        if (shelvesToDelete.isEmpty()) {
            clearShelfContextualAction()
            return
        }

        Timber.d("Deleting ${shelvesToDelete.size} shelves: ${shelvesToDelete.joinToString()}")
        val currentShelves =
            prefs.getStringSet(KEY_SHELVES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newTimestamp = System.currentTimeMillis()

        prefs.edit {
            shelvesToDelete.forEach { shelfName ->
                currentShelves.remove(shelfName)
                putBoolean("$KEY_SHELF_DELETED_PREFIX$shelfName", true)
                putLong("$KEY_SHELF_TIMESTAMP_PREFIX$shelfName", newTimestamp)
            }
            putStringSet(KEY_SHELVES, currentShelves)
        }

        shelvesToDelete.forEach { syncShelfChangeToFirestore(it) }

        Timber.d("Shelves deleted successfully.")
        clearShelfContextualAction()
    }

    fun showAddBooksToShelf() {
        _internalState.update {
            it.copy(
                isAddingBooksToShelf = true,
                addBooksSource = AddBooksSource.UNSHELVED,
                booksSelectedForAdding = emptySet()
            )
        }
    }

    private fun syncShelfChangeToFirestore(shelfName: String) {
        if (!uiState.value.isSyncEnabled) return
        val currentUser = uiState.value.currentUser ?: return

        viewModelScope.launch {
            val shelfContent =
                prefs.getStringSet("$KEY_SHELF_CONTENT_PREFIX$shelfName", null)?.toList()
                    ?: emptyList()
            val isDeleted = prefs.getBoolean("$KEY_SHELF_DELETED_PREFIX$shelfName", false)
            val timestamp = prefs.getLong("$KEY_SHELF_TIMESTAMP_PREFIX$shelfName", 0L)

            val shelfMetadata = ShelfMetadata(
                name = shelfName,
                bookIds = shelfContent,
                isDeleted = isDeleted,
                lastModifiedTimestamp = timestamp
            )

            val deviceId = getInstallationId()
            firestoreRepository.syncShelf(currentUser.uid, shelfMetadata, deviceId)
            Timber.d("Pushed shelf update to Firestore for: $shelfName")
        }
    }

    fun dismissAddBooksToShelf() {
        _internalState.update {
            it.copy(
                isAddingBooksToShelf = false,
                booksSelectedForAdding = emptySet(),
                addBooksSource = AddBooksSource.UNSHELVED
            )
        }
    }

    fun addBooksToShelf(shelfName: String) {
        val bookIdsToAdd = _internalState.value.booksSelectedForAdding
        if (bookIdsToAdd.isEmpty()) {
            Timber.w("addBooksToShelf called for '$shelfName' but no books were selected.")
            dismissAddBooksToShelf()
            return
        }

        Timber.i(
            "Attempting to add ${bookIdsToAdd.size} books to shelf '$shelfName'. Book IDs: ${bookIdsToAdd.joinToString()}"
        )
        val key = "$KEY_SHELF_CONTENT_PREFIX$shelfName"
        val currentBookIds = prefs.getStringSet(key, emptySet()) ?: emptySet()
        Timber.d("Existing book IDs in shelf '$shelfName': ${currentBookIds.joinToString()}")

        val newBookIds = currentBookIds + bookIdsToAdd
        prefs.edit {
            putStringSet(key, newBookIds)
            putLong("$KEY_SHELF_TIMESTAMP_PREFIX$shelfName", System.currentTimeMillis())
        }
        Timber.i(
            "Successfully updated shelf '$shelfName'. It now contains ${newBookIds.size} book(s)."
        )

        syncShelfChangeToFirestore(shelfName)

        _internalState.update {
            it.copy(isAddingBooksToShelf = false, booksSelectedForAdding = emptySet())
        }
    }

    fun setAddBooksSource(source: AddBooksSource) {
        _internalState.update { it.copy(addBooksSource = source) }
        prefs.edit { putString(KEY_ADD_BOOKS_SOURCE, source.name) }
    }

    fun toggleBookSelectionForAdding(bookId: String) {
        _internalState.update { state ->
            val currentSelection = state.booksSelectedForAdding
            val newSelection = if (bookId in currentSelection) {
                currentSelection - bookId
            } else {
                currentSelection + bookId
            }
            state.copy(booksSelectedForAdding = newSelection)
        }
    }

    fun deleteContextualItemsPermanently() {
        val itemsToRemove = _internalState.value.contextualActionItems
        if (itemsToRemove.isNotEmpty()) {
            _internalState.update { it.copy(contextualActionItems = emptySet()) }

            viewModelScope.launch {
                val canSync = uiState.value.isSyncEnabled && googleDriveRepository.hasDrivePermissions(appContext)

                val (folderBooks, managedBooks) = itemsToRemove.partition { it.sourceFolderUri != null }

                if (folderBooks.isNotEmpty()) {
                    Timber.d("Processing ${folderBooks.size} folder books for deletion.")

                    val idsToDeleteLocally = mutableListOf<String>()

                    folderBooks.forEach { item ->
                        idsToDeleteLocally.add(item.bookId)
                        pdfTextRepository.clearBookText(item.bookId)

                        if (item.uriString != null) {
                            try {
                                val fileUri = item.uriString.toUri()
                                val fileDoc = DocumentFile.fromSingleUri(appContext, fileUri)
                                if (fileDoc != null && fileDoc.exists()) {
                                    if (fileDoc.delete()) {
                                        Timber.i("Physically deleted folder file: ${item.displayName}")
                                    } else {
                                        Timber.e("Failed to delete folder file via SAF: ${item.displayName}")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error deleting physical file for ${item.bookId}")
                            }
                        }

                        // 2. Try to delete the metadata JSON (.bookId.json)
                        if (item.sourceFolderUri != null) {
                            try {
                                val rootUri = item.sourceFolderUri.toUri()
                                val rootDoc = DocumentFile.fromTreeUri(appContext, rootUri)

                                if (rootDoc != null) {
                                    val hiddenMeta = rootDoc.findFile(".${item.bookId}.json")
                                    val legacyVisibleMeta = rootDoc.findFile("${item.bookId}.json")

                                    hiddenMeta?.delete()
                                    legacyVisibleMeta?.delete()

                                    Timber.tag("FolderSync").d("Deleted metadata for ${item.bookId} from root.")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error deleting metadata file for ${item.bookId}")
                            }
                        }
                    }

                    recentFilesRepository.deleteFilePermanently(idsToDeleteLocally)
                }

                if (managedBooks.isNotEmpty()) {
                    val currentUser = uiState.value.currentUser

                    if (canSync && currentUser != null) {
                        _internalState.update {
                            it.copy(
                                isLoading = true,
                                bannerMessage = BannerMessage("Deleting from all devices...")
                            )
                        }
                        try {
                            val accessToken = googleDriveRepository.getAccessToken(appContext)
                                ?: throw Exception("No token")
                            val deviceId = getInstallationId()

                            val remoteFiles = withContext(Dispatchers.IO) {
                                googleDriveRepository.getFiles(accessToken)?.files.orEmpty()
                                    .associateBy { it.name }
                            }

                            for (item in managedBooks) {
                                recentFilesRepository.markAsDeleted(listOf(item.bookId))
                                pdfTextRepository.clearBookText(item.bookId)

                                firestoreRepository.syncBookMetadata(
                                    currentUser.uid, item.toBookMetadata().copy(isDeleted = true), deviceId
                                )

                                val fileExtension = item.type.name.lowercase()
                                val fileName = "${item.bookId}.$fileExtension"
                                remoteFiles[fileName]?.id?.let { fileId ->
                                    Timber.d("Deleting from Drive: $fileName")
                                    googleDriveRepository.deleteDriveFile(accessToken, fileId)
                                }

                                recentFilesRepository.deleteFilePermanently(listOf(item.bookId))
                            }

                            _internalState.update {
                                it.copy(isLoading = false, bannerMessage = BannerMessage("Deletion complete."))
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error during permanent deletion")
                            recentFilesRepository.deleteFilePermanently(managedBooks.map { it.bookId })
                            managedBooks.forEach { item ->
                                pdfTextRepository.clearBookText(item.bookId)
                            }
                            _internalState.update {
                                it.copy(isLoading = false, errorMessage = "Cloud sync failed, deleted locally.")
                            }
                        }
                    } else {
                        recentFilesRepository.deleteFilePermanently(managedBooks.map { it.bookId })
                        managedBooks.forEach { item -> pdfTextRepository.clearBookText(item.bookId) }
                    }
                }

                val totalRemoved = folderBooks.size + managedBooks.size
                _internalState.update { it.copy(isLoading = false, bannerMessage = BannerMessage("$totalRemoved books removed from library.")) }
            }
        } else {
            Timber.w("Attempted to remove contextual items, but none were selected.")
        }
    }

    fun navigateToFolderSync() {
        // 1. Switch MainScreen to Library Tab (Index 1)
        setMainScreenPage(1)
        // 2. Switch LibraryScreen to Folder Tab (Index 2)
        setLibraryScreenPage(2)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        firestoreRepository.removeListener(feedbackListener)
        Timber.d("ViewModel instance cleared (onCleared).")
    }

    suspend fun checkAndMigrateLegacyBookId(legacyId: String, newId: String) = withContext(Dispatchers.IO) {
        if (legacyId == newId) return@withContext
        Timber.tag("FolderAnnotationSync").d("Checking migration from legacyId=$legacyId to newId=$newId")

        try {
            fun safeMigrate(legacyFile: File?, newFile: File?, tag: String) {
                if (legacyFile != null && legacyFile.exists()) {
                    if (newFile != null) {
                        if (newFile.exists()) {
                            val legacyTs = legacyFile.lastModified()
                            val newTs = newFile.lastModified()

                            if (newTs > legacyTs) {
                                Timber.tag("FolderAnnotationSync").i("Skipping migration for $tag: Destination ($newId) is newer than Legacy ($legacyId). Deleting legacy.")
                                legacyFile.delete()
                                return
                            } else {
                                newFile.delete()
                            }
                        }

                        if (legacyFile.renameTo(newFile)) {
                            Timber.tag("FolderAnnotationSync").i("Migrated $tag successfully.")
                        } else {
                            Timber.tag("FolderAnnotationSync").w("Failed to rename $tag file.")
                        }
                    } else {
                        Timber.tag("FolderAnnotationSync").w("Destination file for $tag is null. Skipping.")
                    }
                }
            }

            // 1. Annotations
            safeMigrate(
                pdfAnnotationRepository.getAnnotationFileForSync(legacyId),
                pdfAnnotationRepository.getAnnotationFileForSync(newId),
                "annotations"
            )

            // 2. Rich Text
            safeMigrate(
                pdfRichTextRepository.getFileForSync(legacyId),
                pdfRichTextRepository.getFileForSync(newId),
                "rich text"
            )

            // 3. Layout
            safeMigrate(
                pageLayoutRepository.getLayoutFile(legacyId),
                pageLayoutRepository.getLayoutFile(newId),
                "layout"
            )

            // 4. Text Boxes
            safeMigrate(
                pdfTextBoxRepository.getFileForSync(legacyId),
                pdfTextBoxRepository.getFileForSync(newId),
                "text boxes"
            )

        } catch (e: Exception) {
            Timber.tag("FolderAnnotationSync").e(e, "Error migrating legacy book data")
        }
    }

    companion object {
        private const val KEY_SORT_ORDER = "sort_order"
        internal const val KEY_SHELVES = "shelf_names"
        internal const val KEY_SHELF_CONTENT_PREFIX = "shelf_content_"
        internal const val KEY_SHELF_TIMESTAMP_PREFIX = "shelf_timestamp_"
        internal const val KEY_SHELF_DELETED_PREFIX = "shelf_deleted_"
        private const val KEY_ADD_BOOKS_SOURCE = "add_books_source"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_MIGRATION_CHECKED_UIDS = "migration_checked_uids"
        private const val KEY_INSTALLATION_ID = "installation_id"
        internal const val KEY_SYNCED_FOLDER_URI = "synced_folder_uri"
        internal const val KEY_LAST_FOLDER_SCAN_TIME = "last_folder_scan_time"
        private const val KEY_APP_OPEN_COUNT = "app_open_count"
    }
}
