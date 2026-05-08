package com.aryan.reader

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.credentials.CredentialManager
import androidx.work.WorkManager
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.aryan.reader.data.*
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.data.BookCacheDao
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.TtsPlaybackManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MainViewModel
    private lateinit var mockApplication: Application
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private val billingStateFlow = MutableStateFlow(ProUpgradeState())
    private val customFontsFlow = MutableStateFlow<List<CustomFontEntity>>(emptyList())
    private val ttsStateFlow = MutableStateFlow(TtsPlaybackManager.TtsState())
    private val recentFilesFlow = MutableStateFlow<List<RecentFileItem>>(emptyList())
    private val shelvesFlow = MutableStateFlow<List<ShelfEntity>>(emptyList())
    private val shelfRefsFlow = MutableStateFlow<List<BookShelfCrossRef>>(emptyList())
    private val tagsFlow = MutableStateFlow<List<TagEntity>>(emptyList())
    private val tagRefsFlow = MutableStateFlow<List<BookTagCrossRef>>(emptyList())

    @Before
    fun setup() {
        recentFilesFlow.value = emptyList()
        shelvesFlow.value = emptyList()
        shelfRefsFlow.value = emptyList()
        tagsFlow.value = emptyList()
        tagRefsFlow.value = emptyList()
        billingStateFlow.value = ProUpgradeState()
        customFontsFlow.value = emptyList()
        ttsStateFlow.value = TtsPlaybackManager.TtsState()

        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns false
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        mockApplication = mockk()
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        val mockResources = mockk<Resources>(relaxed = true)
        val testRoot = File("build/test-tmp/MainViewModelTest/${System.nanoTime()}")
        val filesDir = File(testRoot, "files").apply { mkdirs() }
        val cacheDir = File(testRoot, "cache").apply { mkdirs() }
        val externalFilesDir = File(testRoot, "external-files").apply { mkdirs() }

        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockApplication.resources } returns mockResources
        every { mockApplication.packageName } returns "com.aryan.reader"
        every { mockApplication.filesDir } returns filesDir
        every { mockApplication.cacheDir } returns cacheDir
        every { mockApplication.getExternalFilesDir(any()) } returns externalFilesDir
        every { mockPrefs.edit() } returns mockEditor

        every { mockPrefs.getString(any(), any()) } answers { secondArg() as String? }
        every { mockPrefs.getBoolean(any(), any()) } answers { secondArg() as Boolean }
        every { mockPrefs.getInt(any(), any()) } answers { secondArg() as Int }
        every { mockPrefs.getFloat(any(), any()) } answers { secondArg() as Float }

        mockkObject(AppDatabase.Companion)
        val mockDb = mockk<AppDatabase>(relaxed = true)
        every { AppDatabase.getDatabase(any()) } returns mockDb
        mockkObject(BookCacheDatabase.Companion)
        val mockBookCacheDb = mockk<BookCacheDatabase>(relaxed = true)
        every { mockBookCacheDb.bookCacheDao() } returns mockk<BookCacheDao>(relaxed = true)
        every { BookCacheDatabase.getDatabase(any()) } returns mockBookCacheDb

        mockkObject(WorkManager.Companion)
        val mockWorkManager = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any()) } returns mockWorkManager
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockk(relaxed = true)
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockk(relaxed = true)
        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns mockk(relaxed = true)
        mockkStatic(BillingClient::class)
        val mockBillingClient = mockk<BillingClient>(relaxed = true)
        val mockBillingBuilder = mockk<BillingClient.Builder>(relaxed = true)
        every { BillingClient.newBuilder(any()) } returns mockBillingBuilder
        every { mockBillingBuilder.setListener(any()) } returns mockBillingBuilder
        every { mockBillingBuilder.enablePendingPurchases(any()) } returns mockBillingBuilder
        every { mockBillingBuilder.build() } returns mockBillingClient
        every { mockBillingClient.isReady } returns false
        every { mockBillingClient.startConnection(any()) } answers {
            firstArg<com.android.billingclient.api.BillingClientStateListener>()
                .onBillingSetupFinished(
                    BillingResult.newBuilder()
                        .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                        .build()
                )
        }
        mockkConstructor(AuthRepository::class)
        mockkConstructor(RecentFilesRepository::class)
        mockkConstructor(BillingClientWrapper::class)
        mockkConstructor(RemoteConfigRepository::class)
        mockkConstructor(FirestoreRepository::class)
        mockkConstructor(FeedbackRepository::class)
        mockkConstructor(FontsRepository::class)
        mockkConstructor(TtsController::class)

        every { anyConstructed<BillingClientWrapper>().proUpgradeState } returns billingStateFlow
        every { anyConstructed<AuthRepository>().getSignedInUser() } returns null
        every { anyConstructed<AuthRepository>().observeAuthState() } returns flowOf(null)
        every { anyConstructed<RemoteConfigRepository>().init() } just Runs
        every { anyConstructed<TtsController>().ttsState } returns ttsStateFlow
        every { anyConstructed<TtsController>().connect() } just Runs
        every { anyConstructed<TtsController>().release() } just Runs
        every { anyConstructed<RecentFilesRepository>().getRecentFilesFlow() } returns recentFilesFlow
        every { anyConstructed<RecentFilesRepository>().activeShelvesFlow } returns shelvesFlow
        every { anyConstructed<RecentFilesRepository>().shelfCrossRefsFlow } returns shelfRefsFlow
        every { anyConstructed<RecentFilesRepository>().tagsFlow } returns tagsFlow
        every { anyConstructed<RecentFilesRepository>().tagCrossRefsFlow } returns tagRefsFlow

        coEvery { anyConstructed<RecentFilesRepository>().migrateLegacyShelvesToRoom() } just Runs
        coEvery { anyConstructed<RecentFilesRepository>().seedTagsIfEmpty(any()) } just Runs
        coEvery { anyConstructed<RecentFilesRepository>().assignTagToBook(any(), any()) } just Runs
        coEvery { anyConstructed<RecentFilesRepository>().removeTagFromBook(any(), any()) } just Runs
        coEvery { anyConstructed<RecentFilesRepository>().removeBooksFromShelf(any(), any()) } just Runs
        coEvery { anyConstructed<RecentFilesRepository>().addBooksToShelf(any(), any()) } just Runs
        coEvery { anyConstructed<RecentFilesRepository>().deleteShelf(any()) } just Runs

        every { anyConstructed<FontsRepository>().getAllFonts() } returns customFontsFlow

        viewModel = MainViewModel(mockApplication)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `search query updates uiState when search is active`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setSearchActive(true)
        viewModel.onSearchQueryChange("Moby Dick")

        val state = viewModel.uiState.first {
            it.searchQuery == "Moby Dick" && it.isSearchActive
        }
        assertEquals("Moby Dick", state.searchQuery)
        assertTrue(state.isSearchActive)
    }

    @Test
    fun `setSearchActive false clears the search query`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setSearchActive(true)
        viewModel.onSearchQueryChange("Android")
        viewModel.setSearchActive(false)

        assertEquals("", viewModel.uiState.value.searchQuery)
        assertFalse(viewModel.uiState.value.isSearchActive)
    }

    @Test
    fun `search query change is ignored while search is inactive`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onSearchQueryChange("Invisible")

        assertEquals("", viewModel.uiState.value.searchQuery)
        assertFalse(viewModel.uiState.value.isSearchActive)
    }

    @Test
    fun `switching theme updates internal state and preferences`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setAppThemeMode(AppThemeMode.DARK)

        val state = viewModel.uiState.first { it.appThemeMode == AppThemeMode.DARK }
        assertEquals(AppThemeMode.DARK, state.appThemeMode)
        verify { mockEditor.putString("app_theme_mode", AppThemeMode.DARK.name) }
    }

    @Test
    fun `setTabsEnabled persists to shared preferences`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setTabsEnabled(true)

        val state = viewModel.uiState.first { it.isTabsEnabled }
        assertTrue(state.isTabsEnabled)
        verify { mockEditor.putBoolean("tabs_enabled", true) }
    }

    @Test
    fun `setRenderMode persists mode without touching saved epub position`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setRenderMode(RenderMode.PAGINATED)

        val state = viewModel.uiState.first { it.renderMode == RenderMode.PAGINATED }
        assertEquals(RenderMode.PAGINATED, state.renderMode)
        verify { mockEditor.putString(KEY_RENDER_MODE, RenderMode.PAGINATED.name) }
        coVerify(exactly = 0) {
            anyConstructed<RecentFilesRepository>().updateEpubReadingPosition(any(), any(), any(), any())
        }
    }

    @Test
    fun `saveEpubReadingPosition forwards cfi locator and progress to repository`() = runTest {
        val uriString = "content://books/one"
        val uri = mockUri(uriString)
        val locator = Locator(chapterIndex = 5, blockIndex = 77, charOffset = 14)
        coEvery { anyConstructed<RecentFilesRepository>().getFileByUri(uriString) } returns RecentFileItem(
            bookId = "book-1",
            uriString = uriString,
            type = FileType.EPUB,
            displayName = "One.epub",
            timestamp = 1L
        )
        coEvery {
            anyConstructed<RecentFilesRepository>().updateEpubReadingPosition(any(), any(), any(), any())
        } just Runs

        viewModel.saveEpubReadingPosition(uri, locator, "/4/2/6:14", 37.25f)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            anyConstructed<RecentFilesRepository>().updateEpubReadingPosition(
                uriString = uriString,
                locator = locator,
                cfiForWebView = "/4/2/6:14",
                progress = 37.25f
            )
        }
    }

    @Test
    fun `setRecentFilesLimit persists and limits visible home recents`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val first = recentFile("first", isRecent = true)
        val second = recentFile("second", isRecent = true)
        recentFilesFlow.value = listOf(first, second)
        viewModel.uiState.first { it.rawLibraryFiles.size == 2 }

        viewModel.setRecentFilesLimit(1)
        val state = viewModel.uiState.first { it.recentFiles.bookIds() == setOf("first") }

        assertEquals(listOf("first"), state.recentFiles.map { it.bookId })
        verify { mockEditor.putInt("recent_files_limit", 1) }
    }

    @Test
    fun `strict file filter and external file behavior persist preferences`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setStrictFileFilter(true)
        viewModel.setExternalFileBehavior("KEEP")

        val state = viewModel.uiState.first {
            it.useStrictFileFilter && it.externalFileBehavior == "KEEP"
        }
        assertTrue(state.useStrictFileFilter)
        assertEquals("KEEP", state.externalFileBehavior)
        verify { mockEditor.putBoolean("use_strict_file_filter", true) }
        verify { mockEditor.putString("external_file_behavior", "KEEP") }
    }

    @Test
    fun `setSortOrder persists preference and reorders visible home and library lists`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val beta = recentFile("beta", title = "Beta", timestamp = 3L)
        val alpha = recentFile("alpha", title = "Alpha", timestamp = 1L)
        val gamma = recentFile("gamma", title = "Gamma", timestamp = 2L, isRecent = false)
        recentFilesFlow.value = listOf(beta, alpha, gamma)
        viewModel.uiState.first { it.rawLibraryFiles.size == 3 }

        viewModel.setSortOrder(SortOrder.TITLE_ASC)
        val state = viewModel.uiState.first {
            it.sortOrder == SortOrder.TITLE_ASC &&
                it.allRecentFiles.map { item -> item.bookId } == listOf("alpha", "beta", "gamma")
        }

        assertEquals(listOf("alpha", "beta"), state.recentFiles.map { it.bookId })
        assertEquals(listOf("alpha", "beta", "gamma"), state.allRecentFiles.map { it.bookId })
        verify { mockEditor.putString("sort_order", SortOrder.TITLE_ASC.name) }
    }

    @Test
    fun `setMainScreenPage clamps to bottom navigation bounds and persists`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setMainScreenPage(99)

        val state = viewModel.uiState.first { it.mainScreenStartPage == 1 }
        assertEquals(1, state.mainScreenStartPage)
        verify { mockEditor.putInt(KEY_MAIN_SCREEN_START_PAGE, 1) }
    }

    @Test
    fun `setLibraryScreenPage clamps to available library tabs and persists`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setLibraryScreenPage(99)

        val expectedMaxPage = if (BuildConfig.IS_OFFLINE) 2 else 3
        val state = viewModel.uiState.first { it.libraryScreenStartPage == expectedMaxPage }
        assertEquals(expectedMaxPage, state.libraryScreenStartPage)
        verify { mockEditor.putInt(KEY_LIBRARY_SCREEN_START_PAGE, expectedMaxPage) }
    }

    @Test
    fun `create shelf dialog state opens and dismisses`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.showCreateShelfDialog()
        val openedState = viewModel.uiState.first { it.showCreateShelfDialog }
        assertTrue(openedState.showCreateShelfDialog)

        viewModel.dismissCreateShelfDialog()
        val dismissedState = viewModel.uiState.first { !it.showCreateShelfDialog }
        assertFalse(dismissedState.showCreateShelfDialog)
    }

    @Test
    fun `selectAllRecentFiles toggles only visible recent home items`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val recent = recentFile("recent", isRecent = true)
        val notRecent = recentFile("not_recent", isRecent = false)
        recentFilesFlow.value = listOf(recent, notRecent)
        viewModel.uiState.first { it.rawLibraryFiles.size == 2 }

        viewModel.selectAllRecentFiles()
        val selectedState = viewModel.uiState.first {
            it.contextualActionItems.bookIds() == setOf("recent")
        }

        assertEquals(setOf("recent"), selectedState.contextualActionItems.bookIds())

        viewModel.selectAllRecentFiles()
        val clearedState = viewModel.uiState.first { it.contextualActionItems.isEmpty() }

        assertTrue(clearedState.contextualActionItems.isEmpty())
    }

    @Test
    fun `selectAllLibraryFiles toggles all filtered library items`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val pdf = recentFile("pdf", type = FileType.PDF)
        val epub = recentFile("epub", type = FileType.EPUB)
        recentFilesFlow.value = listOf(pdf, epub)
        viewModel.uiState.first { it.rawLibraryFiles.size == 2 }

        viewModel.updateLibraryFilters(LibraryFilters(fileTypes = setOf(FileType.PDF)))
        viewModel.uiState.first { it.allRecentFiles.bookIds() == setOf("pdf") }
        viewModel.selectAllLibraryFiles()
        val selectedState = viewModel.uiState.first {
            it.contextualActionItems.bookIds() == setOf("pdf")
        }

        assertEquals(setOf("pdf"), selectedState.contextualActionItems.bookIds())
    }

    @Test
    fun `selectAllLibraryFiles clears selection when all visible library items are already selected`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val first = recentFile("first")
        val second = recentFile("second")
        recentFilesFlow.value = listOf(first, second)
        viewModel.uiState.first { it.rawLibraryFiles.size == 2 }

        viewModel.selectAllLibraryFiles()
        viewModel.uiState.first { it.contextualActionItems.bookIds() == setOf("first", "second") }
        viewModel.selectAllLibraryFiles()
        val clearedState = viewModel.uiState.first { it.contextualActionItems.isEmpty() }

        assertTrue(clearedState.contextualActionItems.isEmpty())
    }

    @Test
    fun `togglePinForContextualItems pins selected home items and clears selection`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val book = recentFile("book")
        recentFilesFlow.value = listOf(book)
        viewModel.uiState.first { it.rawLibraryFiles.size == 1 }

        viewModel.onRecentItemLongPress(book)
        viewModel.togglePinForContextualItems(isHome = true)
        val pinnedState = viewModel.uiState.first {
            it.pinnedHomeBookIds == setOf("book") && it.contextualActionItems.isEmpty()
        }

        assertEquals(setOf("book"), pinnedState.pinnedHomeBookIds)
        assertTrue(pinnedState.contextualActionItems.isEmpty())
        verify { mockEditor.putStringSet("pinned_home_books", setOf("book")) }
    }

    @Test
    fun `togglePinForContextualItems unpins when every selected item is already pinned`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val book = recentFile("book")
        recentFilesFlow.value = listOf(book)
        viewModel.uiState.first { it.rawLibraryFiles.size == 1 }

        viewModel.onRecentItemLongPress(book)
        viewModel.togglePinForContextualItems(isHome = true)
        viewModel.uiState.first { it.pinnedHomeBookIds == setOf("book") }
        viewModel.onRecentItemLongPress(book)
        viewModel.togglePinForContextualItems(isHome = true)
        val state = viewModel.uiState.first {
            it.pinnedHomeBookIds.isEmpty() && it.contextualActionItems.isEmpty()
        }

        assertTrue(state.pinnedHomeBookIds.isEmpty())
        verify { mockEditor.putStringSet("pinned_home_books", emptySet<String>()) }
    }

    @Test
    fun `clearContextualAction clears selected books without disturbing pinned state`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val book = recentFile("book")
        recentFilesFlow.value = listOf(book)
        viewModel.uiState.first { it.rawLibraryFiles.size == 1 }

        viewModel.onRecentItemLongPress(book)
        viewModel.uiState.first { it.contextualActionItems.bookIds() == setOf("book") }
        viewModel.clearContextualAction()
        val state = viewModel.uiState.first { it.contextualActionItems.isEmpty() }

        assertTrue(state.contextualActionItems.isEmpty())
        assertTrue(state.pinnedHomeBookIds.isEmpty())
        assertTrue(state.pinnedLibraryBookIds.isEmpty())
    }

    @Test
    fun `togglePinForContextualItems pins selected library items separately from home pins`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val book = recentFile("library_book")
        recentFilesFlow.value = listOf(book)
        viewModel.uiState.first { it.rawLibraryFiles.size == 1 }

        viewModel.onRecentItemLongPress(book)
        viewModel.togglePinForContextualItems(isHome = false)
        val state = viewModel.uiState.first {
            it.pinnedLibraryBookIds == setOf("library_book") && it.contextualActionItems.isEmpty()
        }

        assertEquals(setOf("library_book"), state.pinnedLibraryBookIds)
        assertTrue(state.pinnedHomeBookIds.isEmpty())
        verify { mockEditor.putStringSet("pinned_library_books", setOf("library_book")) }
    }

    @Test
    fun `updateLibraryFilters updates state and persists every filter dimension`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val filters = LibraryFilters(
            fileTypes = setOf(FileType.PDF, FileType.EPUB),
            sourceFolders = setOf("IN_APP_STORAGE", "content://sync"),
            readStatus = ReadStatusFilter.COMPLETED,
            tagIds = setOf("favorite")
        )

        viewModel.updateLibraryFilters(filters)

        val state = viewModel.uiState.first { it.libraryFilters == filters }
        assertEquals(filters, state.libraryFilters)
        verify { mockEditor.putStringSet(KEY_FILTER_FILE_TYPES, setOf("PDF", "EPUB")) }
        verify { mockEditor.putStringSet(KEY_FILTER_FOLDERS, filters.sourceFolders) }
        verify { mockEditor.putString(KEY_FILTER_READ_STATUS, ReadStatusFilter.COMPLETED.name) }
        verify { mockEditor.putStringSet(KEY_FILTER_TAG_IDS, filters.tagIds) }
    }

    @Test
    fun `updateLibraryFilters clears active filters and persists empty dimensions`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.updateLibraryFilters(
            LibraryFilters(
                fileTypes = setOf(FileType.PDF),
                sourceFolders = setOf("content://sync"),
                readStatus = ReadStatusFilter.IN_PROGRESS,
                tagIds = setOf("favorite")
            )
        )
        viewModel.uiState.first { it.libraryFilters.isActive }

        viewModel.updateLibraryFilters(LibraryFilters())
        val state = viewModel.uiState.first { !it.libraryFilters.isActive }

        assertEquals(LibraryFilters(), state.libraryFilters)
        verify { mockEditor.putStringSet(KEY_FILTER_FILE_TYPES, emptySet<String>()) }
        verify { mockEditor.putStringSet(KEY_FILTER_FOLDERS, emptySet<String>()) }
        verify { mockEditor.putString(KEY_FILTER_READ_STATUS, ReadStatusFilter.ALL.name) }
        verify { mockEditor.putStringSet(KEY_FILTER_TAG_IDS, emptySet<String>()) }
    }

    @Test
    fun `tag selection ignores empty targets and closes after opening`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.openTagSelection(emptySet())
        assertTrue(viewModel.uiState.value.showTagSelectionDialogFor.isEmpty())

        viewModel.openTagSelection(setOf("book"))
        val openedState = viewModel.uiState.first { it.showTagSelectionDialogFor == setOf("book") }
        assertEquals(setOf("book"), openedState.showTagSelectionDialogFor)

        viewModel.closeTagSelection()
        val closedState = viewModel.uiState.first { it.showTagSelectionDialogFor.isEmpty() }
        assertTrue(closedState.showTagSelectionDialogFor.isEmpty())
    }

    @Test
    fun `toggleTagForBooks assigns and removes tags for sanitized book ids`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.toggleTagForBooks("favorite", setOf(" book ", "", "other"), assign = true)
        advanceUntilIdle()
        coVerify { anyConstructed<RecentFilesRepository>().assignTagToBook("book", "favorite") }
        coVerify { anyConstructed<RecentFilesRepository>().assignTagToBook("other", "favorite") }

        viewModel.toggleTagForBooks("favorite", setOf("book"), assign = false)
        advanceUntilIdle()
        coVerify { anyConstructed<RecentFilesRepository>().removeTagFromBook("book", "favorite") }

        viewModel.toggleTagForBooks(" ", setOf("book"), assign = true)
        advanceUntilIdle()
        coVerify(exactly = 0) { anyConstructed<RecentFilesRepository>().assignTagToBook("book", " ") }
    }

    @Test
    fun `rename and delete shelf dialogs store their target and dismiss cleanly`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.showRenameShelfDialog("manual")
        val renameState = viewModel.uiState.first { it.showRenameShelfDialogFor == "manual" }
        assertEquals("manual", renameState.showRenameShelfDialogFor)

        viewModel.dismissRenameShelfDialog()
        viewModel.uiState.first { it.showRenameShelfDialogFor == null }

        viewModel.showDeleteShelfDialog("manual")
        val deleteState = viewModel.uiState.first { it.showDeleteShelfDialogFor == "manual" }
        assertEquals("manual", deleteState.showDeleteShelfDialogFor)

        viewModel.dismissDeleteShelfDialog()
        val dismissedState = viewModel.uiState.first { it.showDeleteShelfDialogFor == null }
        assertEquals(null, dismissedState.showDeleteShelfDialogFor)
    }

    @Test
    fun `shelf selection only allows manual mutable shelves and toggles by click`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        shelvesFlow.value = listOf(shelfEntity("manual", "Manual"))
        val manualShelf = viewModel.uiState.first { it.shelves.any { shelf -> shelf.id == "manual" } }
            .shelves.first { it.id == "manual" }
        val tagShelf = Shelf("tag_favorite", "Favorite", ShelfType.TAG, books = emptyList())

        viewModel.onShelfLongPress(tagShelf)
        assertTrue(viewModel.uiState.value.contextualActionShelfIds.isEmpty())

        viewModel.onShelfLongPress(manualShelf)
        val selectedState = viewModel.uiState.first { it.contextualActionShelfIds == setOf("manual") }
        assertEquals(setOf("manual"), selectedState.contextualActionShelfIds)

        viewModel.onShelfClick(manualShelf)
        val clearedState = viewModel.uiState.first { it.contextualActionShelfIds.isEmpty() }
        assertTrue(clearedState.contextualActionShelfIds.isEmpty())
    }

    @Test
    fun `onShelfClick navigates when shelf contextual mode is inactive`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        shelvesFlow.value = listOf(shelfEntity("manual", "Manual"))
        val manualShelf = viewModel.uiState.first { it.shelves.any { shelf -> shelf.id == "manual" } }
            .shelves.first { it.id == "manual" }

        viewModel.onShelfClick(manualShelf)
        val state = viewModel.uiState.first {
            it.viewingShelfId == "manual" && it.mainScreenStartPage == 1 && it.libraryScreenStartPage == 1
        }

        assertEquals("manual", state.viewingShelfId)
    }

    @Test
    fun `shelf navigation sets library landing state and can be cleared`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        shelvesFlow.value = listOf(shelfEntity("manual", "Manual"))
        viewModel.uiState.first { it.shelves.any { shelf -> shelf.id == "manual" } }

        viewModel.navigateToShelf("manual")
        val shelfState = viewModel.uiState.first {
            it.viewingShelfId == "manual" && it.mainScreenStartPage == 1 && it.libraryScreenStartPage == 1
        }
        assertEquals("manual", shelfState.viewingShelfId)

        viewModel.unselectShelf()
        val clearedState = viewModel.uiState.first { it.viewingShelfId == null }
        assertEquals(null, clearedState.viewingShelfId)
    }

    @Test
    fun `clearShelfContextualAction clears selected shelves`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        shelvesFlow.value = listOf(shelfEntity("manual", "Manual"))
        val manualShelf = viewModel.uiState.first { it.shelves.any { shelf -> shelf.id == "manual" } }
            .shelves.first { it.id == "manual" }

        viewModel.onShelfLongPress(manualShelf)
        viewModel.uiState.first { it.contextualActionShelfIds == setOf("manual") }
        viewModel.clearShelfContextualAction()
        val state = viewModel.uiState.first { it.contextualActionShelfIds.isEmpty() }

        assertTrue(state.contextualActionShelfIds.isEmpty())
    }

    @Test
    fun `deleteSelectedShelves deletes only mutable selected shelves and clears selection`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        shelvesFlow.value = listOf(
            shelfEntity("manual", "Manual"),
            shelfEntity("other", "Other")
        )
        val shelves = viewModel.uiState.first { state ->
            state.shelves.any { it.id == "manual" } && state.shelves.any { it.id == "unshelved" }
        }.shelves
        val manual = shelves.first { it.id == "manual" }
        val unshelved = shelves.first { it.id == "unshelved" }

        viewModel.onShelfLongPress(manual)
        viewModel.onShelfLongPress(unshelved)
        viewModel.uiState.first { it.contextualActionShelfIds == setOf("manual") }
        viewModel.deleteSelectedShelves()
        advanceUntilIdle()

        coVerify { anyConstructed<RecentFilesRepository>().deleteShelf("manual") }
        coVerify(exactly = 0) { anyConstructed<RecentFilesRepository>().deleteShelf("unshelved") }
        val clearedState = viewModel.uiState.first { it.contextualActionShelfIds.isEmpty() }
        assertTrue(clearedState.contextualActionShelfIds.isEmpty())
    }

    @Test
    fun `add books mode resets selection and tracks source changes`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val shelved = recentFile("shelved")
        val loose = recentFile("loose")
        recentFilesFlow.value = listOf(shelved, loose)
        shelvesFlow.value = listOf(shelfEntity("manual", "Manual"))
        shelfRefsFlow.value = listOf(BookShelfCrossRef(bookId = "shelved", shelfId = "manual", addedAt = 1L))
        viewModel.uiState.first { it.shelves.any { shelf -> shelf.id == "manual" } }

        viewModel.navigateToShelf("manual")
        viewModel.showAddBooksToShelf()
        val addModeState = viewModel.uiState.first {
            it.isAddingBooksToShelf && it.booksAvailableForAdding.bookIds() == setOf("loose")
        }
        assertEquals(AddBooksSource.UNSHELVED, addModeState.addBooksSource)

        viewModel.setAddBooksSource(AddBooksSource.ALL_BOOKS)
        viewModel.toggleBookSelectionForAdding("loose")
        val selectedState = viewModel.uiState.first {
            it.addBooksSource == AddBooksSource.ALL_BOOKS && it.booksSelectedForAdding == setOf("loose")
        }
        assertEquals(setOf("loose"), selectedState.booksSelectedForAdding)
        verify { mockEditor.putString("add_books_source", AddBooksSource.ALL_BOOKS.name) }

        viewModel.dismissAddBooksToShelf()
        val dismissedState = viewModel.uiState.first {
            !it.isAddingBooksToShelf && it.booksSelectedForAdding.isEmpty()
        }
        assertFalse(dismissedState.isAddingBooksToShelf)
    }

    @Test
    fun `toggleBookSelectionForAdding toggles individual books`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.toggleBookSelectionForAdding("loose")
        val selectedState = viewModel.uiState.first { it.booksSelectedForAdding == setOf("loose") }
        assertEquals(setOf("loose"), selectedState.booksSelectedForAdding)

        viewModel.toggleBookSelectionForAdding("loose")
        val clearedState = viewModel.uiState.first { it.booksSelectedForAdding.isEmpty() }
        assertTrue(clearedState.booksSelectedForAdding.isEmpty())
    }

    @Test
    fun `addBooksToShelf saves selected books for mutable shelves and exits add mode`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val loose = recentFile("loose")
        recentFilesFlow.value = listOf(loose)
        shelvesFlow.value = listOf(shelfEntity("manual", "Manual"))
        viewModel.uiState.first { it.shelves.any { shelf -> shelf.id == "manual" } }

        viewModel.navigateToShelf("manual")
        viewModel.showAddBooksToShelf()
        viewModel.toggleBookSelectionForAdding("loose")
        viewModel.addBooksToShelf("manual")
        advanceUntilIdle()

        coVerify { anyConstructed<RecentFilesRepository>().addBooksToShelf("manual", listOf("loose")) }
        val state = viewModel.uiState.first {
            !it.isAddingBooksToShelf && it.booksSelectedForAdding.isEmpty()
        }
        assertFalse(state.isAddingBooksToShelf)
        assertTrue(state.booksSelectedForAdding.isEmpty())
    }

    @Test
    fun `addBooksToShelf dismisses add mode when target shelf is not mutable`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.toggleBookSelectionForAdding("loose")
        viewModel.addBooksToShelf("unshelved")
        val state = viewModel.uiState.first {
            !it.isAddingBooksToShelf && it.booksSelectedForAdding.isEmpty()
        }

        assertFalse(state.isAddingBooksToShelf)
        assertTrue(state.booksSelectedForAdding.isEmpty())
        coVerify(exactly = 0) { anyConstructed<RecentFilesRepository>().addBooksToShelf("unshelved", any()) }
    }

    @Test
    fun `removeContextualItemsFromShelf removes selected books from the current mutable shelf`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val book = recentFile("book")
        recentFilesFlow.value = listOf(book)
        shelvesFlow.value = listOf(shelfEntity("manual", "Manual"))
        shelfRefsFlow.value = listOf(BookShelfCrossRef(bookId = "book", shelfId = "manual", addedAt = 1L))
        viewModel.uiState.first { it.shelves.any { shelf -> shelf.id == "manual" } }

        viewModel.navigateToShelf("manual")
        viewModel.onRecentItemLongPress(book)
        viewModel.uiState.first { it.contextualActionItems.bookIds() == setOf("book") }
        viewModel.removeContextualItemsFromShelf()
        advanceUntilIdle()

        coVerify { anyConstructed<RecentFilesRepository>().removeBooksFromShelf("manual", listOf("book")) }
        val clearedState = viewModel.uiState.first { it.contextualActionItems.isEmpty() }
        assertTrue(clearedState.contextualActionItems.isEmpty())
    }

    @Test
    fun `app appearance settings persist contrast brightness seed and custom themes`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val color = Color(0xFF006C4C)
        val theme = CustomAppTheme(id = "forest", name = "Forest", seedColor = color)

        viewModel.setAppContrastOption(AppContrastOption.HIGH)
        viewModel.setAppTextDimFactorLight(0.75f)
        viewModel.setAppTextDimFactorDark(0.65f)
        viewModel.addCustomAppTheme(theme)
        val themedState = viewModel.uiState.first {
            it.appContrastOption == AppContrastOption.HIGH &&
                it.appTextDimFactorLight == 0.75f &&
                it.appTextDimFactorDark == 0.65f &&
                it.customAppThemes == listOf(theme) &&
                it.appSeedColor == color
        }

        assertEquals(AppContrastOption.HIGH, themedState.appContrastOption)
        assertEquals(listOf(theme), themedState.customAppThemes)
        verify { mockEditor.putString("app_contrast_option", AppContrastOption.HIGH.name) }
        verify { mockEditor.putFloat("app_text_dim_factor_light", 0.75f) }
        verify { mockEditor.putFloat("app_text_dim_factor_dark", 0.65f) }
        verify { mockEditor.putInt("app_seed_color", color.toArgb()) }

        viewModel.deleteCustomAppTheme(theme.id)
        val deletedState = viewModel.uiState.first {
            it.customAppThemes.isEmpty() && it.appSeedColor == null
        }
        assertTrue(deletedState.customAppThemes.isEmpty())
        assertEquals(null, deletedState.appSeedColor)
        verify { mockEditor.remove("app_seed_color") }
    }

    @Test
    fun `setAppSeedColor can clear a selected seed color`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val color = Color(0xFF123456)

        viewModel.setAppSeedColor(color)
        viewModel.uiState.first { it.appSeedColor == color }
        viewModel.setAppSeedColor(null)
        val clearedState = viewModel.uiState.first { it.appSeedColor == null }

        assertEquals(null, clearedState.appSeedColor)
        verify { mockEditor.putInt("app_seed_color", color.toArgb()) }
        verify { mockEditor.remove("app_seed_color") }
    }

    @Test
    fun `addCustomAppTheme replaces existing theme with the same id`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val first = CustomAppTheme(id = "theme", name = "First", seedColor = Color(0xFF123456))
        val second = CustomAppTheme(id = "theme", name = "Second", seedColor = Color(0xFF654321))

        viewModel.addCustomAppTheme(first)
        viewModel.uiState.first { it.customAppThemes == listOf(first) }
        viewModel.addCustomAppTheme(second)
        val state = viewModel.uiState.first { it.customAppThemes == listOf(second) }

        assertEquals(listOf(second), state.customAppThemes)
        assertEquals(second.seedColor, state.appSeedColor)
    }

    @Test
    fun `banner message logic works correctly`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.showBanner("Test Message", isError = true)

        val currentBanner = viewModel.uiState.first {
            it.bannerMessage?.message == "Test Message"
        }.bannerMessage
        assertEquals("Test Message", currentBanner?.message)
        assertTrue(currentBanner?.isError == true)

        viewModel.bannerMessageShown()
        val clearedState = viewModel.uiState.first { it.bannerMessage == null }
        assertEquals(null, clearedState.bannerMessage)
    }

    private fun recentFile(
        id: String,
        type: FileType = FileType.EPUB,
        isRecent: Boolean = true,
        title: String? = null,
        timestamp: Long = 1L
    ) = RecentFileItem(
        bookId = id,
        uriString = "content://$id",
        type = type,
        displayName = "$id.${type.name.lowercase()}",
        timestamp = timestamp,
        isRecent = isRecent,
        title = title
    )

    private fun mockUri(uriString: String): Uri {
        return mockk<Uri>().also { uri ->
            every { uri.toString() } returns uriString
            every { uri.scheme } returns uriString.substringBefore(":", "")
        }
    }

    private fun shelfEntity(id: String, name: String) = ShelfEntity(
        id = id,
        name = name,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun Iterable<RecentFileItem>.bookIds(): Set<String> = mapTo(mutableSetOf()) { it.bookId }
}
