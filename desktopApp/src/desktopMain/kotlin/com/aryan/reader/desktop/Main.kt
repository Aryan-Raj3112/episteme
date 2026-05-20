package com.aryan.reader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.AppFontPreference
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderContextExtractor
import com.aryan.reader.shared.RecapResult
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.ReaderTtsCacheSummary
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedImportOutcomeCounts
import com.aryan.reader.shared.SharedImportPlanner
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SmartCollectionDefinition
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.aryan.reader.shared.opds.SharedOpdsController
import com.aryan.reader.shared.opds.SharedOpdsDownloadState
import com.aryan.reader.shared.opds.SharedOpdsStreamUri
import com.aryan.reader.shared.pdf.SharedPdfReaderViewport
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubMetadataEditor
import com.aryan.reader.shared.reader.SharedEpubMetadataUpdate
import com.aryan.reader.shared.reader.SharedEpubPaginationCache
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.sharedSettingsHubModel
import com.aryan.reader.shared.ui.NonReaderLibraryTab
import com.aryan.reader.shared.ui.SharedAboutScreen
import com.aryan.reader.shared.ui.SharedAddToShelfDialog
import com.aryan.reader.shared.ui.SharedAppShell
import com.aryan.reader.shared.ui.SharedAppTab
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedAppThemeSettingsDialog
import com.aryan.reader.shared.ui.SharedBookInfoDialog
import com.aryan.reader.shared.ui.SharedConfirmDialog
import com.aryan.reader.shared.ui.SharedCustomFontsScreen
import com.aryan.reader.shared.ui.SharedHelpFeedbackScreen
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedOpdsScreen
import com.aryan.reader.shared.ui.SharedReaderModalOwnerWindowProvider
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.shared.ui.SharedSupportProjectScreen
import com.aryan.reader.shared.ui.SharedTextInputDialog
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.withTtsReplacements
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.EventQueue
import java.io.File
import java.net.URI
import java.util.Base64
import java.util.UUID
import kotlin.math.max

private enum class DesktopFeatureNoticeAction {
    SIGN_IN,
    OPEN_PRO
}

private data class DesktopFeatureNotice(
    val titleKey: String,
    val titleFallback: String,
    val messageKey: String,
    val messageFallback: String,
    val confirmKey: String = "action_ok",
    val confirmFallback: String = "OK",
    val action: DesktopFeatureNoticeAction? = null
)

private data class DesktopCloudSyncCredentials(
    val userId: String,
    val idToken: String,
    val driveAccessToken: String,
    val deviceId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpistemeDesktopApp(
    window: Component? = null,
    appWindowPlacement: WindowPlacement,
    readerFullscreen: Boolean,
    onReaderFullscreenChange: (Boolean) -> Unit
) {
    val desktopBuildProfile = remember { currentDesktopBuildProfile() }
    val desktopLanguageSettingsStore = remember { DesktopLanguageSettingsStore() }
    var desktopLanguageTag by remember { mutableStateOf(desktopLanguageSettingsStore.load().languageTag) }
    val desktopStringLocale = remember(desktopLanguageTag) { desktopLocaleForLanguageTag(desktopLanguageTag) }
    val desktopStringResolver = remember(desktopStringLocale) { loadDesktopStringResolver(locale = desktopStringLocale) }
    fun desktopString(name: String, fallback: String, vararg args: Any?): String {
        return desktopStringResolver.string(name, fallback, *args)
    }
    fun desktopQuantityString(
        name: String,
        quantity: Int,
        fallbackOne: String,
        fallbackOther: String,
        vararg args: Any?
    ): String {
        return desktopStringResolver.quantityString(name, quantity, fallbackOne, fallbackOther, *args)
    }
    val featurePolicy = desktopBuildProfile.featurePolicy
    val libraryProjector = remember { SharedLibraryStateProjector(DesktopFolderPathResolver) }
    val readerEngine = remember { ReaderEngine() }
    val libraryDatabase = remember { DesktopLibraryDatabase() }
    val desktopBookImporter = remember { DesktopBookImporter() }
    val customFontStore = remember {
        DesktopCustomFontStore(
            googleFontsDownloadAvailable = { featurePolicy.googleFontsDownload }
        )
    }
    val opdsRepository = remember { DesktopOpdsRepository() }
    val opdsController = remember {
        SharedOpdsController(
            repository = opdsRepository,
            idFactory = { UUID.randomUUID().toString() }
        )
    }
    val desktopCloudConfig = remember { loadDesktopCloudConfig() }
    val desktopAuthRepository = remember { DesktopFirebaseAuthRepository(desktopCloudConfig) }
    val desktopAccountProfileRepository = remember { DesktopAccountProfileRepository(desktopCloudConfig) }
    val desktopCloudSyncSettingsStore = remember { DesktopCloudSyncSettingsStore() }
    val initialDesktopCloudSyncSettings = remember { desktopCloudSyncSettingsStore.load() }
    val desktopInstallationIdStore = remember { DesktopInstallationIdStore() }
    val desktopFirestoreRepository = remember { DesktopFirestoreRepository(desktopCloudConfig) }
    val desktopGoogleDriveRepository = remember { DesktopGoogleDriveRepository() }
    val desktopCloudSync = remember {
        DesktopCloudSync(
            firestoreRepository = desktopFirestoreRepository,
            driveRepository = desktopGoogleDriveRepository,
            bookImporter = desktopBookImporter,
            customFontStore = customFontStore
        )
    }
    val aiByokStore = remember { DesktopAiByokStore() }
    var aiByokSettings by remember {
        mutableStateOf(aiByokStore.load())
    }
    val initialLibrarySnapshot = remember { libraryDatabase.load().withDesktopDefaults() }
    val scope = rememberCoroutineScope()
    var webViewRuntimeState by remember { mutableStateOf(DesktopWebViewRuntimeState()) }
    var webViewRuntimeRequested by remember { mutableStateOf(false) }
    var readerCustomTextureIds by remember { mutableStateOf(DesktopReaderTextures.importedTextureIds()) }
    val appWindowFullscreen = appWindowPlacement == WindowPlacement.Fullscreen

    EpistemeDesktopWindowDecorationEffect(
        window = window,
        hideDecoration = readerFullscreen && !appWindowFullscreen
    )
    DesktopReaderFullscreenEffect(
        window = window,
        enabled = readerFullscreen && !appWindowFullscreen
    )

    DisposableEffect(Unit) {
        onDispose {
            KCEF.disposeBlocking()
        }
    }

    var shelfRecords by remember { mutableStateOf(initialLibrarySnapshot.shelfRecords) }
    var shelfRefs by remember { mutableStateOf(initialLibrarySnapshot.shelfRefs) }
    var state by remember {
        val initialState = initialLibrarySnapshot.toDesktopReaderScreenState().copy(
            isSyncEnabled = initialDesktopCloudSyncSettings.isSyncEnabled,
            isFolderSyncEnabled = initialDesktopCloudSyncSettings.isFolderSyncEnabled
        )
        mutableStateOf(
            libraryProjector.projectDesktopLibraryState(
                state = initialState,
                shelfRecords = shelfRecords,
                shelfRefs = shelfRefs
            )
        )
    }
    var accountStatusMessage by remember { mutableStateOf<String?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountRefreshRequestCount by remember { mutableStateOf(0) }
    fun effectiveAiSettings(): ReaderAiByokSettings {
        val hidden = aiByokSettings.hideReaderAiFeatures
        return if (desktopBuildProfile.byokAiAvailable) {
            aiByokSettings.withDesktopFeaturePolicy(featurePolicy)
        } else {
            ReaderAiByokSettings(
                hideReaderAiFeatures = hidden,
                ttsSpeakerId = aiByokSettings.sanitized().ttsSpeakerId,
                serverBackedReaderAiFeatures = featurePolicy.aiAndCloud && featurePolicy.networkAccess,
                serverBackedCloudTts = featurePolicy.aiAndCloud &&
                    featurePolicy.networkAccess &&
                    state.currentUser != null &&
                    state.credits > 0 &&
                    desktopCloudConfig.isTtsWorkerConfigured
            )
        }
    }
    val desktopAiAdapter = remember(desktopBuildProfile) {
        if (desktopBuildProfile.byokAiAvailable) {
            DesktopByokAiAdapter(
                settingsProvider = { effectiveAiSettings() },
                networkAccess = { featurePolicy.networkAccess }
            )
        } else {
            DesktopPaidAiAdapter(
                config = desktopCloudConfig,
                networkAccess = { featurePolicy.networkAccess },
                hideReaderAiFeatures = { effectiveAiSettings().hideReaderAiFeatures },
                currentAuthToken = { desktopAuthRepository.freshIdToken() },
                currentSignedIn = { state.currentUser != null },
                currentIsProUser = { state.isProUser },
                currentCredits = { state.credits },
                onUsageCompleted = {
                    scope.launch { accountRefreshRequestCount++ }
                    Unit
                }
            )
        }
    }
    val desktopTtsAdapter = remember(desktopBuildProfile) {
        DesktopGeminiCloudTtsAdapter(
            settingsProvider = { effectiveAiSettings() },
            networkAccess = { featurePolicy.networkAccess },
            workerUrlProvider = { desktopCloudConfig.ttsWorkerUrl },
            authTokenProvider = { desktopAuthRepository.freshIdToken() },
            useWorkerProvider = { !desktopBuildProfile.byokAiAvailable },
            onWorkerUsageCompleted = {
                scope.launch { accountRefreshRequestCount++ }
                Unit
            }
        )
    }
    val desktopSummaryCacheStore = remember { DesktopSummaryCacheStore() }
    var selectedTab by remember { mutableStateOf(SharedAppTab.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(NonReaderLibraryTab.BOOKS) }
    var customFonts by remember {
        mutableStateOf(initialLibrarySnapshot.customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() })
    }
    var readerWindows by remember { mutableStateOf<List<DesktopReaderWindowState>>(emptyList()) }
    var reflowingPdfBookIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val desktopEpubPaginationCache = remember { SharedEpubPaginationCache() }
    var epubPaginationCacheGeneration by remember { mutableStateOf(0) }
    LaunchedEffect(webViewRuntimeRequested) {
        if (!shouldStartDesktopWebViewRuntime(webViewRuntimeRequested, webViewRuntimeState)) {
            return@LaunchedEffect
        }

        val webViewBundleDir = withContext(Dispatchers.IO) { bundledDesktopWebViewDir() }
        val webViewBundlePresent = withContext(Dispatchers.IO) {
            isBundledDesktopWebViewPresent(webViewBundleDir)
        }
        if (!webViewBundlePresent) {
            webViewRuntimeState = webViewRuntimeState.copy(
                errorMessage = "Bundled embedded webview is missing from ${webViewBundleDir.absolutePath}."
            )
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.IO) {
                KCEF.init(
                    builder = {
                        installDir(webViewBundleDir)
                        progress {
                            onDownloading {
                                webViewRuntimeState = webViewRuntimeState.copy(downloadProgress = max(it, 0f))
                            }
                            onInitialized {
                                webViewRuntimeState = webViewRuntimeState.copy(initialized = true, errorMessage = null)
                            }
                        }
                        settings {
                            cachePath = File(desktopUserCacheRoot(), "kcef").absolutePath
                        }
                    },
                    onError = { error ->
                        webViewRuntimeState = webViewRuntimeState.copy(errorMessage = error?.message ?: error.toString())
                    },
                    onRestartRequired = {
                        webViewRuntimeState = webViewRuntimeState.copy(restartRequired = true)
                    }
                )
            }
        }.onFailure { error ->
            webViewRuntimeState = webViewRuntimeState.copy(errorMessage = error.message ?: error.toString())
        }
    }
    LaunchedEffect(readerWindows) {
        if (readerWindows.any { window ->
                val content = window.content
                content is DesktopReaderWindowContent.Text &&
                    content.session.reader.book.chapters.isNotEmpty() &&
                    content.session.reader.settings.readingMode == ReaderReadingMode.VERTICAL
            }
        ) {
            webViewRuntimeRequested = true
        }
    }
    var nextReaderOpenRequestId by remember { mutableStateOf(0L) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showCreateSmartShelfDialog by remember { mutableStateOf(false) }
    var shelfToRename by remember { mutableStateOf<Shelf?>(null) }
    var shelfToDelete by remember { mutableStateOf<Shelf?>(null) }
    var folderToRemove by remember { mutableStateOf<Shelf?>(null) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }
    var showAiByokSettingsDialog by remember { mutableStateOf(false) }
    var showDesktopAppThemeSettingsDialog by remember { mutableStateOf(false) }
    var showDesktopLanguageDialog by remember { mutableStateOf(false) }
    var showClearBookCacheDialog by remember { mutableStateOf(false) }
    var desktopFeatureNotice by remember { mutableStateOf<DesktopFeatureNotice?>(null) }
    var settingsQuery by remember { mutableStateOf("") }
    var settingsDestination by remember { mutableStateOf(SharedSettingsDestination.ROOT) }
    var bookInfoDialogFor by remember { mutableStateOf<BookItem?>(null) }
    var bookInfoInitiallyEditing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var dropImportState by remember { mutableStateOf(DesktopDropImportState()) }
    var opdsState by remember { mutableStateOf(opdsController.state) }
    var desktopCloudSyncJob by remember { mutableStateOf<Job?>(null) }
    var pendingDesktopCloudSyncAfterActive by remember { mutableStateOf(false) }
    val desktopBookCloudSyncJobs = remember { mutableMapOf<String, Job>() }
    var initialDesktopCloudSyncDone by remember { mutableStateOf(false) }
    val readerWindowDefaults = remember(desktopBuildProfile) { epistemeDesktopWindowDefaults(desktopBuildProfile) }

    fun projectState(
        next: SharedReaderScreenState,
        records: List<ShelfRecord> = shelfRecords,
        refs: List<BookShelfRef> = shelfRefs
    ): SharedReaderScreenState {
        return libraryProjector.projectDesktopLibraryState(
            state = next,
            shelfRecords = records,
            shelfRefs = refs
        )
    }

    fun persistSnapshot(
        projected: SharedReaderScreenState,
        records: List<ShelfRecord> = shelfRecords,
        refs: List<BookShelfRef> = shelfRefs,
        fonts: List<CustomFontItem> = customFonts
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                libraryDatabase.save(
                    projected.toDesktopLibrarySnapshot(
                        shelfRecords = records,
                        shelfRefs = refs,
                        customFonts = fonts
                    )
                )
            }
        }
    }

    fun replaceLibrary(
        next: SharedReaderScreenState,
        records: List<ShelfRecord> = shelfRecords,
        refs: List<BookShelfRef> = shelfRefs,
        fonts: List<CustomFontItem> = customFonts
    ) {
        shelfRecords = records
        shelfRefs = refs
        val projected = projectState(next, records, refs)
        state = projected
        persistSnapshot(projected, records, refs, fonts)
    }

    fun updateState(next: SharedReaderScreenState) {
        val projected = projectState(next)
        state = projected
        persistSnapshot(projected)
    }

    fun DesktopReaderWindowState.closeReaderResources() {
        when (val content = content) {
            DesktopReaderWindowContent.Opening,
            is DesktopReaderWindowContent.PasswordRequired -> Unit
            is DesktopReaderWindowContent.Pdf -> content.document.close()
            is DesktopReaderWindowContent.Text -> content.ttsJob?.cancel()
        }
    }

    fun updateReaderWindow(
        windowId: String,
        transform: (DesktopReaderWindowState) -> DesktopReaderWindowState
    ) {
        readerWindows = readerWindows.map { window ->
            if (window.id == windowId) transform(window) else window
        }
    }

    fun updateTextReaderWindow(
        windowId: String,
        transform: (DesktopReaderWindowContent.Text) -> DesktopReaderWindowContent.Text
    ) {
        readerWindows = readerWindows.replaceDesktopTextReaderContent(windowId, transform)
    }

    fun textReaderWindowContent(windowId: String): DesktopReaderWindowContent.Text? {
        return readerWindows.firstOrNull { it.id == windowId }?.content as? DesktopReaderWindowContent.Text
    }

    fun closeReaderWindow(windowId: String) {
        val closing = readerWindows.firstOrNull { it.id == windowId } ?: return
        val shouldStopTts = (closing.content as? DesktopReaderWindowContent.Text)?.extrasState?.cloudTts?.let {
            it.isLoading || it.isPlaying || it.isPaused
        } == true
        closing.closeReaderResources()
        if (shouldStopTts) {
            scope.launch { desktopTtsAdapter.stop() }
        }
        readerWindows = readerWindows.withoutDesktopReaderWindow(windowId)
        updateState(state.reduce(AppAction.BookTabClosed(closing.bookId)))
    }

    fun closeReaderWindowsForBookIds(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        val closing = readerWindows.filter { it.bookId in bookIds }
        val shouldStopTts = closing.any { window ->
            (window.content as? DesktopReaderWindowContent.Text)?.extrasState?.cloudTts?.let {
                it.isLoading || it.isPlaying || it.isPaused
            } == true
        }
        closing.forEach { it.closeReaderResources() }
        if (shouldStopTts) {
            scope.launch { desktopTtsAdapter.stop() }
        }
        readerWindows = readerWindows.withoutDesktopReaderBookIds(bookIds)
    }

    fun closeAllReaderWindows() {
        val shouldStopTts = readerWindows.any { window ->
            (window.content as? DesktopReaderWindowContent.Text)?.extrasState?.cloudTts?.let {
                it.isLoading || it.isPlaying || it.isPaused
            } == true
        }
        readerWindows.forEach { it.closeReaderResources() }
        if (shouldStopTts) {
            scope.launch { desktopTtsAdapter.stop() }
        }
        readerWindows = emptyList()
        updateState(state.reduce(AppAction.AllTabsClosed))
    }

    fun downloadReaderImage(image: ReaderImageReference) {
        val target = chooseSaveImageFile(image.suggestedDownloadFileName()) ?: return
        runCatching {
            target.parentFile?.mkdirs()
            target.writeBytes(image.desktopImageBytes())
        }.onSuccess {
            updateState(state.withBanner("Saved ${target.name}."))
        }.onFailure { error ->
            updateState(state.withBanner(error.message ?: "Could not save image.", isError = true))
        }
    }

    fun clearDesktopBookCache() {
        scope.launch {
            withContext(Dispatchers.IO) {
                desktopEpubPaginationCache.clearAll()
                SharedJvmBookLoader.clearCache()
            }
            epubPaginationCacheGeneration++
            updateState(state.withBanner("Book cache cleared. EPUB pagination will be recreated on demand."))
        }
    }

    fun desktopCloudSyncAvailable(): Boolean {
        return featurePolicy.aiAndCloud &&
            featurePolicy.networkAccess &&
            !desktopBuildProfile.byokAiAvailable &&
            desktopCloudConfig.isAuthConfigured
    }

    fun saveDesktopCloudSyncSettings(
        syncEnabled: Boolean = state.isSyncEnabled,
        folderSyncEnabled: Boolean = state.isFolderSyncEnabled
    ) {
        desktopCloudSyncSettingsStore.save(
            DesktopCloudSyncSettings(
                isSyncEnabled = syncEnabled,
                isFolderSyncEnabled = folderSyncEnabled
            )
        )
    }

    suspend fun refreshDesktopAccountProfile(showBanner: Boolean = false) {
        if (!featurePolicy.aiAndCloud || desktopBuildProfile.byokAiAvailable) return
        val session = desktopAuthRepository.restoreSavedSession()
        if (session == null) {
            if (state.isSyncEnabled) saveDesktopCloudSyncSettings(syncEnabled = false)
            updateState(state.copy(currentUser = null, isProUser = false, credits = 0, isSyncEnabled = false))
            return
        }
        val token = desktopAuthRepository.freshIdToken()
        if (token.isNullOrBlank()) {
            if (state.isSyncEnabled) saveDesktopCloudSyncSettings(syncEnabled = false)
            updateState(state.copy(currentUser = null, isProUser = false, credits = 0, isSyncEnabled = false))
            return
        }
        runCatching {
            desktopAccountProfileRepository.fetchProfile(session.user.uid, token)
        }.onSuccess { profile ->
            val nextSyncEnabled = state.isSyncEnabled && profile.isProUser
            if (!nextSyncEnabled && state.isSyncEnabled) {
                saveDesktopCloudSyncSettings(syncEnabled = false)
            }
            updateState(
                state.copy(
                    currentUser = session.user,
                    isProUser = profile.isProUser,
                    credits = profile.credits,
                    isSyncEnabled = nextSyncEnabled
                )
            )
            accountStatusMessage = if (profile.isProUser) {
                "Account checked. Pro is unlocked."
            } else {
                "Account checked. Pro is not unlocked."
            }
            if (showBanner) updateState(state.withBanner("Account status refreshed."))
        }.onFailure { error ->
            accountStatusMessage = error.message ?: "Could not check account status."
            if (showBanner) updateState(state.withBanner(accountStatusMessage.orEmpty(), isError = true))
        }
    }

    fun signInDesktopAccount() {
        if (!desktopCloudConfig.isAuthConfigured) {
            updateState(state.withBanner("Desktop Google sign-in is not configured for this build.", isError = true))
            return
        }
        scope.launch {
            accountBusy = true
            accountStatusMessage = "Waiting for Google sign-in..."
            runCatching {
                desktopAuthRepository.signIn(::openExternalUrl)
            }.onSuccess { session ->
                updateState(state.copy(currentUser = session.user, isProUser = false, credits = 0))
                accountStatusMessage = "Signed in. Checking Pro and credits..."
                refreshDesktopAccountProfile()
            }.onFailure { error ->
                accountStatusMessage = error.message ?: "Google sign-in failed."
                updateState(state.withBanner(accountStatusMessage.orEmpty(), isError = true))
            }
            accountBusy = false
        }
    }

    suspend fun desktopCloudSyncCredentials(showBanner: Boolean): DesktopCloudSyncCredentials? {
        if (!desktopCloudSyncAvailable()) {
            if (showBanner) {
                updateState(state.withBanner("Desktop cloud sync is not configured for this build.", isError = true))
            }
            return null
        }

        val session = desktopAuthRepository.restoreSavedSession()
        val user = state.currentUser ?: session?.user
        if (user == null) {
            if (showBanner) updateState(state.withBanner("Sign in with Google to use cloud sync.", isError = true))
            return null
        }

        if (!state.isProUser) {
            if (showBanner) updateState(state.withBanner("A Pro account is required for cloud sync.", isError = true))
            return null
        }

        val idToken = desktopAuthRepository.freshIdToken()
        if (idToken.isNullOrBlank()) {
            if (showBanner) updateState(state.withBanner("Sign in again to use cloud sync.", isError = true))
            return null
        }

        val driveAccessToken = desktopAuthRepository.freshGoogleAccessToken()
        if (driveAccessToken.isNullOrBlank()) {
            if (showBanner) {
                updateState(state.withBanner("Sign in again to grant Google Drive sync access.", isError = true))
            }
            return null
        }

        return DesktopCloudSyncCredentials(
            userId = user.uid,
            idToken = idToken,
            driveAccessToken = driveAccessToken,
            deviceId = desktopInstallationIdStore.getOrCreateId()
        )
    }

    fun desktopCloudSyncCompleteMessage(result: DesktopCloudSyncResult): String {
        val details = buildList {
            if (result.uploadedBooks > 0) add("Uploaded ${result.uploadedBooks}.")
            if (result.downloadedBooks > 0) add("Downloaded ${result.downloadedBooks}.")
        }
        return if (details.isEmpty()) {
            "Cloud sync complete."
        } else {
            "Cloud sync complete. ${details.joinToString(" ")}"
        }
    }

    fun syncDesktopCloud(showBanner: Boolean = false): Job {
        desktopCloudSyncJob?.takeIf { it.isActive }?.let { return it }
        val job = scope.launch {
            if (!state.isSyncEnabled) return@launch
            val credentials = desktopCloudSyncCredentials(showBanner) ?: return@launch
            val snapshotState = state
            val snapshotShelfRecords = shelfRecords
            val snapshotShelfRefs = shelfRefs
            val snapshotFonts = customFonts

            if (showBanner) {
                updateState(state.copy(isRefreshing = true).withBanner("Cloud sync: checking library..."))
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    desktopCloudSync.sync(
                        DesktopCloudSyncInput(
                            userId = credentials.userId,
                            idToken = credentials.idToken,
                            driveAccessToken = credentials.driveAccessToken,
                            deviceId = credentials.deviceId,
                            state = snapshotState,
                            shelfRecords = snapshotShelfRecords,
                            shelfRefs = snapshotShelfRefs,
                            customFonts = snapshotFonts,
                            includeFolderBooks = snapshotState.isFolderSyncEnabled
                        )
                    )
                }
            }.onSuccess { result ->
                customFonts = result.customFonts
                val syncedState = result.state.copy(
                    isSyncEnabled = state.isSyncEnabled,
                    isFolderSyncEnabled = state.isFolderSyncEnabled,
                    isRefreshing = false
                )
                replaceLibrary(
                    next = if (showBanner) syncedState.withBanner(desktopCloudSyncCompleteMessage(result)) else syncedState,
                    records = result.shelfRecords,
                    refs = result.shelfRefs,
                    fonts = result.customFonts
                )
            }.onFailure { error ->
                val failed = state.copy(isRefreshing = false)
                if (showBanner) {
                    updateState(failed.withBanner(error.message ?: "Cloud sync failed.", isError = true))
                } else {
                    updateState(failed)
                }
            }
        }
        desktopCloudSyncJob = job
        job.invokeOnCompletion {
            if (desktopCloudSyncJob == job) desktopCloudSyncJob = null
        }
        return job
    }

    fun setDesktopCloudSyncEnabled(enabled: Boolean) {
        if (enabled && !desktopCloudSyncAvailable()) {
            updateState(state.withBanner("Desktop cloud sync is not configured for this build.", isError = true))
            return
        }
        if (enabled && state.currentUser == null) {
            updateState(state.withBanner("Sign in with Google to use cloud sync.", isError = true))
            return
        }
        if (enabled && !state.isProUser) {
            updateState(state.withBanner("A Pro account is required for cloud sync.", isError = true))
            return
        }

        saveDesktopCloudSyncSettings(syncEnabled = enabled)
        val next = state.reduce(AppAction.SyncEnabledChanged(enabled))
        updateState(next)
        if (enabled) {
            syncDesktopCloud(showBanner = true)
        }
    }

    fun setDesktopFolderSyncEnabled(enabled: Boolean) {
        saveDesktopCloudSyncSettings(folderSyncEnabled = enabled)
        val next = state.reduce(AppAction.FolderSyncEnabledChanged(enabled))
        updateState(next)
        if (enabled && next.isSyncEnabled) {
            syncDesktopCloud(showBanner = false)
        }
    }

    fun queueCloudBookMetadataSync(book: BookItem, uploadContent: Boolean = false) {
        if (!state.isSyncEnabled) return
        if (isDesktopPdfReflowBookId(book.id)) return
        if (book.sourceFolder != null) return
        if (book.path?.startsWith("opds-pse") == true) return
        if (SharedFileCapabilities.isManualOnlyReaderFileName(book.displayName)) return

        desktopBookCloudSyncJobs.remove(book.id)?.cancel()
        val job = scope.launch {
            if (!uploadContent) delay(1_200L)
            val credentials = desktopCloudSyncCredentials(showBanner = false) ?: return@launch
            val latestBook = state.rawLibraryBooks.firstOrNull { it.id == book.id } ?: return@launch
            if (isDesktopPdfReflowBookId(latestBook.id)) return@launch
            if (latestBook.sourceFolder != null) return@launch

            if (uploadContent) {
                updateState(state.copy(uploadingBookIds = state.uploadingBookIds + latestBook.id))
            }

            try {
                val syncedBook = withContext(Dispatchers.IO) {
                    desktopCloudSync.uploadBookAndMetadata(
                        input = DesktopCloudSyncInput(
                            userId = credentials.userId,
                            idToken = credentials.idToken,
                            driveAccessToken = credentials.driveAccessToken,
                            deviceId = credentials.deviceId,
                            state = state,
                            shelfRecords = shelfRecords,
                            shelfRefs = shelfRefs,
                            customFonts = customFonts,
                            includeFolderBooks = state.isFolderSyncEnabled
                        ),
                        book = latestBook,
                        uploadContent = uploadContent
                    )
                } ?: return@launch

                updateState(
                    state.copy(
                        rawLibraryBooks = state.rawLibraryBooks.map { current ->
                            if (current.id == syncedBook.id && current.timestamp == latestBook.timestamp) {
                                current.copy(timestamp = syncedBook.timestamp)
                            } else {
                                current
                            }
                        }
                    )
                )
            } finally {
                if (uploadContent) {
                    updateState(state.copy(uploadingBookIds = state.uploadingBookIds - latestBook.id))
                }
            }
        }
        desktopBookCloudSyncJobs[book.id] = job
        job.invokeOnCompletion {
            if (desktopBookCloudSyncJobs[book.id] == job) {
                desktopBookCloudSyncJobs.remove(book.id)
            }
        }
    }

    fun syncCloudShelfChange(record: ShelfRecord, refs: List<BookShelfRef>, isDeleted: Boolean = false) {
        if (!state.isSyncEnabled || record.isSmart) return
        scope.launch {
            val credentials = desktopCloudSyncCredentials(showBanner = false) ?: return@launch
            withContext(Dispatchers.IO) {
                desktopCloudSync.syncShelfChange(
                    userId = credentials.userId,
                    idToken = credentials.idToken,
                    deviceId = credentials.deviceId,
                    record = record,
                    refs = refs,
                    isDeleted = isDeleted
                )
            }
        }
    }

    fun deleteBooksFromDesktopCloud(books: List<BookItem>) {
        if (!state.isSyncEnabled || books.isEmpty()) return
        scope.launch {
            val credentials = desktopCloudSyncCredentials(showBanner = false) ?: return@launch
            withContext(Dispatchers.IO) {
                desktopCloudSync.deleteBooksFromCloud(
                    userId = credentials.userId,
                    idToken = credentials.idToken,
                    accessToken = credentials.driveAccessToken,
                    deviceId = credentials.deviceId,
                    books = books
                )
            }
        }
    }

    fun deleteCustomFontFromDesktopCloud(font: CustomFontItem) {
        if (!state.isSyncEnabled) return
        scope.launch {
            val credentials = desktopCloudSyncCredentials(showBanner = false) ?: return@launch
            withContext(Dispatchers.IO) {
                desktopCloudSync.deleteFontFromCloud(
                    userId = credentials.userId,
                    idToken = credentials.idToken,
                    accessToken = credentials.driveAccessToken,
                    font = font
                )
            }
        }
    }

    fun queueFullCloudSyncAfterLocalChange() {
        if (!state.isSyncEnabled) return
        val active = desktopCloudSyncJob
        if (active?.isActive == true) {
            if (pendingDesktopCloudSyncAfterActive) return
            pendingDesktopCloudSyncAfterActive = true
            scope.launch {
                active.join()
                pendingDesktopCloudSyncAfterActive = false
                if (state.isSyncEnabled) {
                    syncDesktopCloud(showBanner = false)
                }
            }
        } else {
            syncDesktopCloud(showBanner = false)
        }
    }

    fun updateAiByokSettings(next: ReaderAiByokSettings) {
        val sanitized = next.sanitized()
        val settingsToSave = if (!desktopBuildProfile.byokAiAvailable) {
            aiByokSettings.sanitized().copy(
                hideReaderAiFeatures = sanitized.hideReaderAiFeatures,
                ttsSpeakerId = sanitized.ttsSpeakerId
            )
        } else {
            logDesktopTts(
                "settings_update keyPresent=${sanitized.geminiKey.isNotBlank()} " +
                    "ttsModel=\"${sanitized.ttsModel.desktopTtsPreview()}\" speaker=\"${sanitized.ttsSpeakerId.desktopTtsPreview()}\" " +
                    "cloudAvailable=${sanitized.isCloudTtsAvailable}"
            )
            sanitized
        }

        aiByokSettings = settingsToSave
        readerWindows = readerWindows.replaceAllDesktopTextReaderContent { content ->
            content.copy(
                extrasState = content.extrasState.copy(
                    cloudTts = content.extrasState.cloudTts.copy(
                        isAvailable = effectiveAiSettings().isCloudTtsAvailable,
                        errorMessage = null,
                        cacheSummary = desktopTtsAdapter.cacheSummary(
                            content.session.reader.book.title,
                            settingsToSave.ttsSpeakerId
                        )
                    )
                )
            )
        }
        runCatching { aiByokStore.save(settingsToSave) }
            .onFailure { error ->
                logDesktopTts("settings_save_failed error=\"${error.desktopTtsSummary()}\"")
                scope.launch {
                    snackbarHostState.showSnackbar(error.message ?: "AI settings could not be saved securely.")
                }
            }
    }

    fun updateReaderAutoScroll(windowId: String, autoScroll: ReaderAutoScrollState) {
        updateTextReaderWindow(windowId) { content ->
            content.copy(extrasState = content.extrasState.copy(autoScroll = autoScroll.sanitized()))
        }
    }

    fun textReaderTtsCacheSummary(content: DesktopReaderWindowContent.Text): ReaderTtsCacheSummary {
        return desktopTtsAdapter.cacheSummary(
            content.session.reader.book.title,
            aiByokSettings.sanitized().ttsSpeakerId
        )
    }

    fun readerCloudTtsStoppedState(
        content: DesktopReaderWindowContent.Text,
        statusMessage: String? = null,
        errorMessage: String? = null
    ) = ReaderCloudTtsState(
        isAvailable = effectiveAiSettings().isCloudTtsAvailable,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        cacheSummary = textReaderTtsCacheSummary(content)
    )

    fun cloudTtsUnavailableMessage(): String {
        return if (desktopBuildProfile.byokAiAvailable) {
            desktopString(
                "desktop_cloud_tts_needs_gemini_key_desc",
                "Add a Gemini key and select Gemini cloud TTS in AI keys and models."
            )
        } else if (state.currentUser == null) {
            desktopString("desktop_cloud_tts_sign_in_required_desc", "Sign in with Google to use cloud TTS.")
        } else if (state.credits <= 0) {
            desktopString(
                "desktop_out_of_credits_android_purchase_desc",
                "Out of credits. Pro and credits can only be purchased from the Android app."
            )
        } else {
            desktopString(
                "desktop_cloud_tts_not_configured_desc",
                "Cloud TTS is not configured for this desktop build."
            )
        }
    }

    fun desktopReadScopeLabel(readScope: ReaderTtsReadScope): String {
        return when (readScope) {
            ReaderTtsReadScope.PAGE -> desktopString("desktop_page", "Page")
            ReaderTtsReadScope.CHAPTER -> desktopString("chapter", "Chapter")
            ReaderTtsReadScope.BOOK -> desktopString("desktop_from_here", "From here")
        }
    }

    fun desktopFeatureNoticeForReaderAi(feature: ReaderAiFeature, text: String): DesktopFeatureNotice? {
        if (desktopBuildProfile.byokAiAvailable) return null
        if (!featurePolicy.networkAccess || !desktopCloudConfig.isAiWorkerConfigured) {
            return desktopFeatureUnavailableNotice(
                messageKey = "desktop_ai_not_configured_desc",
                messageFallback = "Desktop AI is not configured for this build."
            )
        }
        if (effectiveAiSettings().hideReaderAiFeatures) {
            return desktopFeatureUnavailableNotice(
                messageKey = "desktop_reader_ai_hidden_desc",
                messageFallback = "Reader AI features are hidden."
            )
        }
        if (feature == ReaderAiFeature.DEFINE && desktopReaderWordCount(text) > 1 && state.currentUser == null) {
            return desktopSignInRequiredNotice(
                messageKey = "desktop_sign_in_required_multi_word_dictionary_desc",
                messageFallback = "Sign in with Google to use multi-word smart dictionary on desktop."
            )
        }
        if (feature == ReaderAiFeature.DEFINE && desktopReaderWordCount(text) > 1 && !state.isProUser) {
            return desktopProRequiredNotice(
                messageKey = "desktop_pro_required_multi_word_dictionary_desc",
                messageFallback = "Multi-word smart dictionary requires Pro. Pro can only be purchased from the Android app, then desktop will use the upgraded account after sign-in."
            )
        }
        if (feature == ReaderAiFeature.SUMMARIZE && state.currentUser == null) {
            return desktopSignInRequiredNotice(
                messageKey = "desktop_sign_in_required_summaries_desc",
                messageFallback = "Sign in with Google to use summaries on desktop."
            )
        }
        if (feature == ReaderAiFeature.SUMMARIZE && !state.isProUser && state.credits <= 0) {
            return desktopOutOfCreditsNotice(
                messageKey = "desktop_out_of_credits_summaries_desc",
                messageFallback = "Using summaries needs credits on desktop. Pro and credits can only be purchased from the Android app."
            )
        }
        if (feature == ReaderAiFeature.RECAP && state.currentUser == null) {
            return desktopSignInRequiredNotice(
                messageKey = "desktop_sign_in_required_recaps_desc",
                messageFallback = "Sign in with Google to use recaps on desktop."
            )
        }
        if (feature == ReaderAiFeature.RECAP && state.credits <= 0) {
            return desktopOutOfCreditsNotice(
                messageKey = "desktop_out_of_credits_recaps_desc",
                messageFallback = "Using recaps needs credits on desktop. Pro and credits can only be purchased from the Android app."
            )
        }
        return null
    }

    fun desktopFeatureNoticeForCloudTts(): DesktopFeatureNotice? {
        if (desktopBuildProfile.byokAiAvailable) return null
        if (!featurePolicy.networkAccess || !desktopCloudConfig.isTtsWorkerConfigured) {
            return desktopFeatureUnavailableNotice(
                messageKey = "desktop_cloud_tts_not_configured_desc",
                messageFallback = "Cloud TTS is not configured for this desktop build."
            )
        }
        if (state.currentUser == null) {
            return desktopSignInRequiredNotice(
                messageKey = "desktop_cloud_tts_sign_in_required_desc",
                messageFallback = "Sign in with Google to use cloud TTS."
            )
        }
        if (state.credits <= 0) {
            return desktopOutOfCreditsNotice(
                messageKey = "desktop_out_of_credits_cloud_tts_desc",
                messageFallback = "Using cloud TTS needs credits on desktop. Pro and credits can only be purchased from the Android app."
            )
        }
        return null
    }

    fun openReaderExternalLookup(action: ReaderExternalLookupAction, text: String) {
        if (!featurePolicy.externalLookup) return
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        openExternalUrl(externalLookupUrl(action, normalizedText.take(1800)))
    }

    fun readerHubBookKey(content: DesktopReaderWindowContent.Text): String {
        return content.book.id.ifBlank {
            content.session.reader.book.id.ifBlank { content.session.reader.book.title.ifBlank { "Untitled" } }
        }
    }

    fun readerHubChapterIndex(content: DesktopReaderWindowContent.Text): Int {
        return content.session.reader.currentPage?.chapterIndex
            ?: content.session.reader.currentPageIndex
    }

    fun readerHubChapterTitle(
        content: DesktopReaderWindowContent.Text,
        index: Int = readerHubChapterIndex(content)
    ): String {
        return content.session.reader.book.chapters.getOrNull(index)?.title?.takeIf { it.isNotBlank() }
            ?: content.session.reader.currentPage?.chapterTitle?.takeIf { it.isNotBlank() }
            ?: "Chapter ${index + 1}"
    }

    fun readerHubChapterText(
        content: DesktopReaderWindowContent.Text,
        index: Int = readerHubChapterIndex(content)
    ): String {
        return content.session.reader.book.chapters.getOrNull(index)?.plainText?.trim().orEmpty()
    }

    fun readerHubCurrentChapterText(content: DesktopReaderWindowContent.Text): String {
        return ReaderContextExtractor.currentChapterText(content.session).trim()
            .ifBlank { readerHubChapterText(content) }
            .ifBlank { content.session.reader.currentPage?.text?.trim().orEmpty() }
    }

    fun readerHubCurrentTextForRecap(content: DesktopReaderWindowContent.Text): String {
        val chapterText = readerHubChapterText(content)
        val endOffset = content.session.reader.currentPage?.endOffset ?: chapterText.length
        return if (chapterText.isNotBlank()) {
            chapterText.take(endOffset.coerceIn(0, chapterText.length)).trim()
                .ifBlank { chapterText.take(500).trim() }
        } else {
            ReaderContextExtractor.textBeforeCurrentLocation(content.session).trim().takeLast(24_000)
        }
    }

    fun clearReaderHubSummary(windowId: String) {
        updateTextReaderWindow(windowId) { content ->
            content.copy(summaryResult = null, isSummaryLoading = false)
        }
    }

    fun clearReaderHubRecap(windowId: String) {
        updateTextReaderWindow(windowId) { content ->
            content.copy(recapResult = null, isRecapLoading = false, recapProgressMessage = null)
        }
    }

    fun generateReaderHubSummary(windowId: String, force: Boolean) {
        val content = textReaderWindowContent(windowId) ?: return
        val text = readerHubCurrentChapterText(content)
        val chapterIndex = readerHubChapterIndex(content)
        val chapterTitle = readerHubChapterTitle(content, chapterIndex)
        val bookKey = readerHubBookKey(content)
        if (text.isBlank()) {
            updateTextReaderWindow(windowId) {
                it.copy(
                    summaryResult = SummarizationResult(
                        error = desktopString("desktop_no_text_to_summarize", "There is no text to summarize.")
                    )
                )
            }
            return
        }
        if (!force) {
            desktopSummaryCacheStore.getSummary(bookKey, chapterIndex)?.let { cached ->
                updateTextReaderWindow(windowId) {
                    it.copy(summaryResult = SummarizationResult(summary = cached, isCacheHit = true))
                }
                return
            }
        }
        desktopFeatureNoticeForReaderAi(ReaderAiFeature.SUMMARIZE, text)?.let { notice ->
            desktopFeatureNotice = notice
            return
        }
        updateTextReaderWindow(windowId) { it.copy(isSummaryLoading = true, summaryResult = null) }
        scope.launch {
            var streamedSummary = ""
            var streamedCost: Double? = null
            var streamedFreeRemaining: Int? = null
            fun updateStreamingSummary(error: String? = null) {
                updateTextReaderWindow(windowId) { current ->
                    current.copy(
                        summaryResult = SummarizationResult(
                            summary = streamedSummary.takeIf { it.isNotBlank() },
                            error = error,
                            cost = streamedCost,
                            freeRemaining = streamedFreeRemaining
                        )
                    )
                }
            }
            val result = desktopAiAdapter.summarizeStreaming(
                text = text,
                onUsageReceived = { cost, freeRemaining ->
                    cost?.let { streamedCost = it }
                    freeRemaining?.let { streamedFreeRemaining = it }
                    updateStreamingSummary()
                },
                onUpdate = { chunk ->
                    streamedSummary += chunk
                    updateStreamingSummary()
                }
            )
            val finalSummary = result.summary?.takeIf { it.isNotBlank() } ?: streamedSummary.takeIf { it.isNotBlank() }
            finalSummary?.let { summary ->
                desktopSummaryCacheStore.saveSummary(bookKey, chapterIndex, chapterTitle, summary)
            }
            updateTextReaderWindow(windowId) { current ->
                current.copy(
                    summaryResult = result.copy(summary = finalSummary),
                    isSummaryLoading = false
                )
            }
            desktopFeatureNoticeForError(result.error)?.let { desktopFeatureNotice = it }
        }
    }

    fun generateReaderHubRecap(windowId: String) {
        val content = textReaderWindowContent(windowId) ?: return
        val currentText = readerHubCurrentTextForRecap(content)
        if (currentText.isBlank()) {
            updateTextReaderWindow(windowId) { it.copy(recapResult = RecapResult(error = "There is no reading context for a recap.")) }
            return
        }
        desktopFeatureNoticeForReaderAi(ReaderAiFeature.RECAP, currentText)?.let { notice ->
            desktopFeatureNotice = notice
            return
        }
        val book = content.session.reader.book
        val bookKey = readerHubBookKey(content)
        val currentChapterIndex = readerHubChapterIndex(content).coerceIn(0, book.chapters.size.coerceAtLeast(1) - 1)
        updateTextReaderWindow(windowId) {
            it.copy(
                isRecapLoading = true,
                recapResult = null,
                recapProgressMessage = "Checking past chapters..."
            )
        }
        scope.launch {
            val pastSummaries = mutableListOf<String>()
            for (chapterIndex in 0 until currentChapterIndex) {
                updateTextReaderWindow(windowId) { it.copy(recapProgressMessage = "Analyzing Chapter ${chapterIndex + 1}...") }
                val cached = desktopSummaryCacheStore.getSummary(bookKey, chapterIndex)
                if (!cached.isNullOrBlank()) {
                    pastSummaries += cached
                    continue
                }
                val latest = textReaderWindowContent(windowId) ?: return@launch
                val chapterText = readerHubChapterText(latest, chapterIndex)
                if (chapterText.length <= 100) continue
                val summary = desktopAiAdapter.summarize(chapterText)
                summary.summary?.takeIf { it.isNotBlank() }?.let { generated ->
                    val title = readerHubChapterTitle(latest, chapterIndex)
                    desktopSummaryCacheStore.saveSummary(bookKey, chapterIndex, title, generated)
                    pastSummaries += generated
                }
                if (summary.error != null) {
                    desktopFeatureNoticeForError(summary.error)?.let { desktopFeatureNotice = it }
                }
                delay(500)
            }

            updateTextReaderWindow(windowId) { it.copy(recapProgressMessage = "Generating recap...") }
            val recap = (desktopAiAdapter as? DesktopPaidAiAdapter)
                ?.recapWithContext(pastSummaries, currentText)
                ?: desktopAiAdapter.recap(
                    buildString {
                        pastSummaries.forEachIndexed { index, summary ->
                            append("Past chapter ${index + 1} summary:\n")
                            append(summary)
                            append("\n\n")
                        }
                        append(currentText)
                    }
                )
            updateTextReaderWindow(windowId) {
                it.copy(
                    recapResult = recap,
                    isRecapLoading = false,
                    recapProgressMessage = null
                )
            }
            desktopFeatureNoticeForError(recap.error)?.let { desktopFeatureNotice = it }
        }
    }

    fun isReaderAiResultVisible(content: DesktopReaderWindowContent.Text, requestId: Long): Boolean =
        content.readerAiResultRequestId == requestId && content.dismissedReaderAiResultRequestId != requestId

    fun updateReaderAiResult(windowId: String, requestId: Long, aiResult: ReaderAiResultState) {
        updateTextReaderWindow(windowId) { content ->
            if (isReaderAiResultVisible(content, requestId)) {
                content.copy(extrasState = content.extrasState.copy(aiResult = aiResult))
            } else {
                content
            }
        }
    }

    fun runReaderAiAction(windowId: String, feature: ReaderAiFeature, text: String) {
        val content = textReaderWindowContent(windowId) ?: return
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        if (!effectiveAiSettings().areReaderAiFeaturesAvailable) return
        desktopFeatureNoticeForReaderAi(feature, normalizedText)?.let { notice ->
            desktopFeatureNotice = notice
            return
        }
        val aiResultRequestId = content.readerAiResultRequestId + 1
        updateTextReaderWindow(windowId) {
            it.copy(
                readerAiResultRequestId = aiResultRequestId,
                dismissedReaderAiResultRequestId = null
            )
        }
        updateReaderAiResult(
            windowId,
            aiResultRequestId,
            ReaderAiResultState(
                title = feature.displayName,
                isLoading = true
            )
        )
        scope.launch {
            val result = when (feature) {
                ReaderAiFeature.DEFINE -> {
                    var streamedDefinition = ""
                    val latest = textReaderWindowContent(windowId) ?: return@launch
                    val definition = desktopAiAdapter.defineStreaming(
                        text = normalizedText.take(2400),
                        context = ReaderContextExtractor.currentPageText(latest.session),
                        onUpdate = { chunk ->
                            streamedDefinition += chunk
                            updateReaderAiResult(
                                windowId,
                                aiResultRequestId,
                                ReaderAiResultState(
                                    title = feature.displayName,
                                    text = streamedDefinition,
                                    isLoading = true
                                )
                            )
                        }
                    )
                    (definition.definition?.takeIf { it.isNotBlank() } ?: streamedDefinition) to definition.error
                }
                ReaderAiFeature.SUMMARIZE -> {
                    var streamedSummary = ""
                    var streamedCost: Double? = null
                    var streamedFreeRemaining: Int? = null
                    fun updateStreamingSummary() {
                        val partial = SummarizationResult(
                            summary = streamedSummary.takeIf { it.isNotBlank() },
                            cost = streamedCost,
                            freeRemaining = streamedFreeRemaining
                        )
                        updateTextReaderWindow(windowId) { current ->
                            current.copy(summaryResult = partial)
                        }
                        updateReaderAiResult(
                            windowId,
                            aiResultRequestId,
                            ReaderAiResultState(
                                title = feature.displayName,
                                text = streamedSummary,
                                isLoading = true
                            )
                        )
                    }
                    val summary = desktopAiAdapter.summarizeStreaming(
                        text = normalizedText,
                        onUsageReceived = { cost, freeRemaining ->
                            cost?.let { streamedCost = it }
                            freeRemaining?.let { streamedFreeRemaining = it }
                            updateStreamingSummary()
                        },
                        onUpdate = { chunk ->
                            streamedSummary += chunk
                            updateStreamingSummary()
                        }
                    )
                    val finalSummary = summary.summary?.takeIf { it.isNotBlank() } ?: streamedSummary.takeIf { it.isNotBlank() }
                    finalSummary?.let { generated ->
                        val latest = textReaderWindowContent(windowId) ?: return@let
                        desktopSummaryCacheStore.saveSummary(
                            readerHubBookKey(latest),
                            readerHubChapterIndex(latest),
                            readerHubChapterTitle(latest),
                            generated
                        )
                    }
                    updateTextReaderWindow(windowId) { current ->
                        current.copy(summaryResult = summary.copy(summary = finalSummary))
                    }
                    finalSummary to summary.error
                }
                ReaderAiFeature.RECAP -> {
                    val recap = desktopAiAdapter.recap(normalizedText)
                    updateTextReaderWindow(windowId) { current ->
                        current.copy(recapResult = recap)
                    }
                    recap.recap to recap.error
                }
            }
            updateReaderAiResult(
                windowId,
                aiResultRequestId,
                ReaderAiResultState(
                    title = feature.displayName,
                    text = result.first.orEmpty(),
                    errorMessage = result.second,
                    isLoading = false
                )
            )
            val latest = textReaderWindowContent(windowId)
            if (latest != null && isReaderAiResultVisible(latest, aiResultRequestId)) {
                desktopFeatureNoticeForError(result.second)?.let { desktopFeatureNotice = it }
            }
        }
    }

    fun syncBookSidecars(book: BookItem) {
        if (book.sourceFolder.isNullOrBlank()) {
            logDesktopFolderSync("bookSidecars.skipNoFolder book=${book.id}")
            return
        }
        logDesktopFolderSync(
            "bookSidecars.request book=${book.id} sourceFolder=\"${book.sourceFolder.orEmpty().folderSyncPreview()}\""
        )
        scope.launch(Dispatchers.IO) {
            DesktopLocalFolderSync.saveBookSidecars(book)
        }
    }

    fun updateBookReadingState(
        bookId: String,
        pageIndex: Int,
        progress: Float,
        session: ReaderSessionState? = null,
        pdfViewport: SharedPdfReaderViewport? = null
    ) {
        var updatedBook: BookItem? = null
        var shouldSyncSidecars = false
        val next = state.copy(
            rawLibraryBooks = state.rawLibraryBooks.map { book ->
                if (book.id == bookId) {
                    val readerPosition = session?.navigationLocator ?: book.readerPosition
                    shouldSyncSidecars = session != null ||
                        book.lastPageIndex != pageIndex ||
                        book.progressPercentage != progress ||
                        book.readerPosition != readerPosition
                    book.copy(
                        progressPercentage = progress,
                        timestamp = System.currentTimeMillis(),
                        isRecent = true,
                        lastPageIndex = pageIndex,
                        readerPosition = readerPosition,
                        readerSettings = session?.reader?.settings ?: book.readerSettings,
                        readerBookmarks = session?.bookmarks ?: book.readerBookmarks,
                        readerHighlights = session?.highlights ?: book.readerHighlights,
                        pdfReaderViewport = pdfViewport ?: book.pdfReaderViewport
                    ).also { updatedBook = it }
                } else {
                    book
                }
            }
        )
        updateState(next)
        if (shouldSyncSidecars) {
            updatedBook?.let(::syncBookSidecars)
        }
        updatedBook?.let { queueCloudBookMetadataSync(it) }
    }

    fun updateBookReaderSettings(bookId: String, settings: ReaderSettings) {
        var updatedBook: BookItem? = null
        val next = state.copy(
            rawLibraryBooks = state.rawLibraryBooks.map { book ->
                if (book.id == bookId) {
                    book.copy(
                        timestamp = System.currentTimeMillis(),
                        isRecent = true,
                        readerSettings = settings
                    ).also { updatedBook = it }
                } else {
                    book
                }
            }
        )
        updateState(next)
        updatedBook?.let(::syncBookSidecars)
        updatedBook?.let { queueCloudBookMetadataSync(it) }
    }

    fun importDesktopReaderTexture(settings: ReaderSettings): ReaderSettings? {
        val source = chooseReaderTextureFile() ?: return null
        val textureId = DesktopReaderTextures.importTexture(source) ?: return null
        readerCustomTextureIds = DesktopReaderTextures.importedTextureIds()
        return settings.copy(textureId = textureId)
    }

    fun stopReaderCloudTts(windowId: String? = null) {
        logDesktopTts("reader_stop_requested")
        val targetWindowIds = readerWindows
            .filter { window -> windowId == null || window.id == windowId }
            .map { it.id }
            .toSet()
        readerWindows
            .filter { window -> window.id in targetWindowIds }
            .mapNotNull { window -> window.content as? DesktopReaderWindowContent.Text }
            .forEach { it.ttsJob?.cancel() }
        scope.launch {
            desktopTtsAdapter.stop()
            readerWindows = readerWindows.map { window ->
                val content = window.content
                if (window.id in targetWindowIds && content is DesktopReaderWindowContent.Text) {
                    window.copy(
                        content = content.copy(
                            ttsJob = null,
                            extrasState = content.extrasState.copy(
                                cloudTts = readerCloudTtsStoppedState(
                                    content,
                                    statusMessage = desktopString("desktop_stopped", "Stopped")
                                )
                            )
                        )
                    )
                } else {
                    window
                }
            }
        }
    }

    fun signOutDesktopAccount() {
        desktopAuthRepository.signOut()
        stopReaderCloudTts()
        saveDesktopCloudSyncSettings(syncEnabled = false)
        updateState(state.copy(currentUser = null, isProUser = false, credits = 0, isSyncEnabled = false))
        accountStatusMessage = "Signed out."
    }

    fun pauseResumeReaderCloudTts(windowId: String) {
        val content = textReaderWindowContent(windowId) ?: return
        val current = content.extrasState.cloudTts
        if (current.isPaused) {
            scope.launch {
                desktopTtsAdapter.resume()
                updateTextReaderWindow(windowId) { latest ->
                    latest.copy(
                        extrasState = latest.extrasState.copy(
                            cloudTts = latest.extrasState.cloudTts.copy(
                                isPaused = false,
                                isPlaying = true,
                                statusMessage = latest.extrasState.cloudTts.progress.currentPositionLabel
                                    ?: desktopString("label_reading", "Reading")
                            )
                        )
                    )
                }
            }
        } else if (current.isPlaying) {
            scope.launch {
                desktopTtsAdapter.pause()
                updateTextReaderWindow(windowId) { latest ->
                    latest.copy(
                        extrasState = latest.extrasState.copy(
                            cloudTts = latest.extrasState.cloudTts.copy(
                                isPlaying = false,
                                isPaused = true,
                                statusMessage = desktopString("desktop_paused", "Paused")
                            )
                        )
                    )
                }
            }
        }
    }

    fun clearReaderCloudTtsCache(windowId: String) {
        val content = textReaderWindowContent(windowId) ?: return
        desktopTtsAdapter.clearBookCacheForSpeaker(content.session.reader.book.title, aiByokSettings.sanitized().ttsSpeakerId)
        updateTextReaderWindow(windowId) { latest ->
            latest.copy(
                extrasState = latest.extrasState.copy(
                    cloudTts = latest.extrasState.cloudTts.copy(
                        statusMessage = desktopString("desktop_voice_cache_cleared", "Voice cache cleared"),
                        cacheSummary = textReaderTtsCacheSummary(latest)
                    )
                )
            )
        }
    }

    fun startReaderCloudTts(windowId: String, readScope: ReaderTtsReadScope, chunks: List<ReaderTtsChunk>) {
        val content = textReaderWindowContent(windowId) ?: return
        val replacementBookId = content.book.id.ifBlank { content.session.reader.book.title }
        val ttsChunks = chunks
            .filter { it.text.isNotBlank() }
            .withTtsReplacements(state.readerTtsReplacementPreferences, replacementBookId)
        val settings = aiByokSettings.sanitized()
        val currentCloudTts = content.extrasState.cloudTts
        logDesktopTts(
            "reader_sequence_toggle scope=${readScope.name} chunks=${ttsChunks.size} " +
                "isPlaying=${currentCloudTts.isPlaying} isLoading=${currentCloudTts.isLoading} " +
                "keyPresent=${settings.geminiKey.isNotBlank()} ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" " +
                "available=${desktopTtsAdapter.isAvailable}"
        )
        if (currentCloudTts.isPlaying || currentCloudTts.isLoading || currentCloudTts.isPaused) {
            stopReaderCloudTts(windowId)
            return
        }
        if (ttsChunks.isEmpty()) {
            logDesktopTts("reader_sequence_ignored reason=blank_text scope=${readScope.name}")
            updateTextReaderWindow(windowId) { latest ->
                latest.copy(
                    extrasState = latest.extrasState.copy(
                        cloudTts = latest.extrasState.cloudTts.copy(
                            errorMessage = desktopString("desktop_no_text_here_to_read", "There is no text here to read."),
                            cacheSummary = textReaderTtsCacheSummary(latest)
                        )
                    )
                )
            }
            return
        }
        if (!desktopTtsAdapter.isAvailable) {
            logDesktopTts("reader_sequence_blocked reason=adapter_unavailable")
            desktopFeatureNoticeForCloudTts()?.let { desktopFeatureNotice = it }
            updateTextReaderWindow(windowId) { latest ->
                latest.copy(
                    extrasState = latest.extrasState.copy(
                        cloudTts = ReaderCloudTtsState(
                            isAvailable = false,
                            errorMessage = cloudTtsUnavailableMessage(),
                            cacheSummary = textReaderTtsCacheSummary(latest)
                        )
                    )
                )
            }
            return
        }
        readerWindows = readerWindows.map { window ->
            val textContent = window.content as? DesktopReaderWindowContent.Text
            if (window.id != windowId && textContent != null) {
                textContent.ttsJob?.cancel()
                window.copy(
                    content = textContent.copy(
                        ttsJob = null,
                        extrasState = textContent.extrasState.copy(
                            cloudTts = readerCloudTtsStoppedState(
                                textContent,
                                statusMessage = desktopString("desktop_stopped", "Stopped")
                            )
                        )
                    )
                )
            } else {
                window
            }
        }
        val ttsSessionId = System.currentTimeMillis()
        val initialProgress = ReaderTtsProgress(
            sessionId = ttsSessionId,
            scope = readScope,
            chunks = ttsChunks,
            currentChunkIndex = -1
        )
        updateTextReaderWindow(windowId) { latest ->
            latest.copy(
                extrasState = latest.extrasState.copy(
                    cloudTts = ReaderCloudTtsState(
                        isAvailable = true,
                        isLoading = true,
                        statusMessage = desktopString(
                            "desktop_preparing_scope_format",
                            "Preparing %1\$s",
                            desktopReadScopeLabel(readScope)
                        ),
                        progress = initialProgress,
                        cacheSummary = textReaderTtsCacheSummary(latest)
                    )
                )
            )
        }
        val ttsJob = scope.launch {
            runCatching {
                logDesktopTts("reader_sequence_start scope=${readScope.name} chunks=${ttsChunks.size}")
                desktopTtsAdapter.speakChunks(content.session.reader.book.title, readScope, ttsChunks) { index ->
                    if (!isActive) throw kotlinx.coroutines.CancellationException("Reader cloud TTS stopped")
                    val chunk = ttsChunks[index]
                    val progress = initialProgress.copy(currentChunkIndex = index)
                    val latest = textReaderWindowContent(windowId)
                        ?: throw kotlinx.coroutines.CancellationException("Reader window closed")
                    if (latest.session.reader.currentPageIndex != chunk.pageIndex) {
                        val updatedSession = readerEngine.goToPage(latest.session, chunk.pageIndex)
                        updateTextReaderWindow(windowId) { current -> current.copy(session = updatedSession) }
                        updateBookReadingState(
                            bookId = latest.book.id,
                            pageIndex = updatedSession.reader.currentPageIndex,
                            progress = updatedSession.reader.progress,
                            session = updatedSession
                        )
                    }
                    updateTextReaderWindow(windowId) { current ->
                        current.copy(
                            extrasState = current.extrasState.copy(
                                cloudTts = ReaderCloudTtsState(
                                    isAvailable = true,
                                    isPlaying = true,
                                    statusMessage = progress.currentPositionLabel
                                        ?: desktopString("label_reading", "Reading"),
                                    progress = progress,
                                    cacheSummary = textReaderTtsCacheSummary(current)
                                )
                            )
                        )
                    }
                    logDesktopTts(
                        "reader_chunk_start scope=${readScope.name} index=${index + 1}/${ttsChunks.size} " +
                        "page=${chunk.pageIndex + 1} chapter=${chunk.chapterIndex} offsets=${chunk.startOffset}..${chunk.endOffset} " +
                            "sourceCfi=\"${chunk.sourceCfi.orEmpty().logPreview()}\" chars=${chunk.text.length} " +
                            "text=\"${chunk.text.logPreview()}\""
                    )
                }
            }.onFailure { error ->
                logDesktopTts("reader_sequence_failed error=\"${error.desktopTtsSummary()}\"")
                if (error !is kotlinx.coroutines.CancellationException) error.printStackTrace()
                updateTextReaderWindow(windowId) { latest ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        latest.copy(
                            ttsJob = null,
                            extrasState = latest.extrasState.copy(
                                cloudTts = readerCloudTtsStoppedState(
                                    latest,
                                    statusMessage = desktopString("desktop_stopped", "Stopped")
                                )
                            )
                        )
                    } else {
                        desktopFeatureNoticeForError(error.message)?.let { desktopFeatureNotice = it }
                        latest.copy(
                            ttsJob = null,
                            extrasState = latest.extrasState.copy(
                                cloudTts = readerCloudTtsStoppedState(
                                    latest,
                                    errorMessage = error.message
                                        ?: desktopString("desktop_cloud_tts_failed", "Cloud TTS failed.")
                                )
                            )
                        )
                    }
                }
            }.onSuccess {
                logDesktopTts("reader_sequence_success chunks=${ttsChunks.size}")
                updateTextReaderWindow(windowId) { latest ->
                    latest.copy(
                        ttsJob = null,
                        extrasState = latest.extrasState.copy(
                            cloudTts = readerCloudTtsStoppedState(
                                latest,
                                statusMessage = desktopString("desktop_finished", "Finished")
                            )
                        )
                    )
                }
            }
        }
        updateTextReaderWindow(windowId) { latest -> latest.copy(ttsJob = ttsJob) }
    }

    fun toggleReaderCloudTts(windowId: String, text: String) {
        val content = textReaderWindowContent(windowId) ?: return
        val normalizedText = text.trim()
        val settings = aiByokSettings.sanitized()
        val currentCloudTts = content.extrasState.cloudTts
        logDesktopTts(
            "reader_toggle textChars=${normalizedText.length} isPlaying=${currentCloudTts.isPlaying} " +
                "isLoading=${currentCloudTts.isLoading} keyPresent=${settings.geminiKey.isNotBlank()} " +
                "ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" available=${desktopTtsAdapter.isAvailable}"
        )
        if (currentCloudTts.isPlaying || currentCloudTts.isLoading || currentCloudTts.isPaused) {
            stopReaderCloudTts(windowId)
            return
        }
        if (normalizedText.isBlank()) {
            logDesktopTts("reader_toggle_ignored reason=blank_text")
            updateTextReaderWindow(windowId) { latest ->
                latest.copy(
                    extrasState = latest.extrasState.copy(
                        cloudTts = latest.extrasState.cloudTts.copy(
                            errorMessage = desktopString(
                                "desktop_no_text_on_page_to_read",
                                "There is no text on this page to read."
                            ),
                            cacheSummary = textReaderTtsCacheSummary(latest)
                        )
                    )
                )
            }
            return
        }
        if (!desktopTtsAdapter.isAvailable) {
            logDesktopTts("reader_toggle_blocked reason=adapter_unavailable")
            desktopFeatureNoticeForCloudTts()?.let { desktopFeatureNotice = it }
            updateTextReaderWindow(windowId) { latest ->
                latest.copy(
                    extrasState = latest.extrasState.copy(
                        cloudTts = ReaderCloudTtsState(
                            isAvailable = false,
                            errorMessage = cloudTtsUnavailableMessage(),
                            cacheSummary = textReaderTtsCacheSummary(latest)
                        )
                    )
                )
            }
            return
        }
        val page = content.session.reader.currentPage
        val selectionChunks = if (page != null) {
            ReaderTtsPlanner.chunksForText(
                text = normalizedText,
                pageIndex = page.pageIndex,
                chapterIndex = page.chapterIndex,
                chapterTitle = page.chapterTitle,
                sourceStartOffset = page.startOffset
            )
        } else {
            ReaderTtsPlanner.chunksForText(
                text = normalizedText,
                pageIndex = content.session.reader.currentPageIndex,
                chapterIndex = 0,
                chapterTitle = desktopString("desktop_selection", "Selection")
            )
        }
        startReaderCloudTts(windowId, ReaderTtsReadScope.PAGE, selectionChunks)
    }

    fun finishImportFiles(
        files: List<ImportedBookFile>,
        failedCount: Int,
        onImported: (List<BookItem>) -> Unit = {}
    ) {
        val importStart = System.currentTimeMillis()
        val existingIds = state.rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
        val importPlan = SharedImportPlanner.plan(
            files = files,
            existingBookIds = existingIds,
            platform = ReaderPlatform.DESKTOP,
            nowMillis = importStart
        )
        val counts = SharedImportOutcomeCounts(
            addedCount = importPlan.importedCount,
            duplicateCount = importPlan.duplicateCount,
            unsupportedCount = importPlan.unsupportedCount,
            failedCount = failedCount
        )
        if (files.isEmpty() && failedCount > 0) {
            updateState(
                state.withBanner(
                    desktopQuantityString(
                        "desktop_import_failed_file_count",
                        failedCount,
                        "Could not import %1\$d file.",
                        "Could not import %1\$d files.",
                        failedCount
                    ),
                    isError = true
                )
            )
            return
        }
        if (importPlan.supportedFiles.isEmpty() && files.isNotEmpty()) {
            updateState(
                state.withBanner(
                    "No supported desktop reader files were selected. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are supported.",
                    isError = true
                )
            )
            return
        }
        val next = state.copy(rawLibraryBooks = importPlan.importedBooks + state.rawLibraryBooks)
            .let {
                when {
                    counts.addedCount > 0 && (counts.unsupportedCount > 0 || counts.failedCount > 0) -> {
                        val skippedCount = counts.unsupportedCount + counts.failedCount
                        val importedMessage = desktopQuantityString(
                            "desktop_imported_file_count",
                            counts.addedCount,
                            "Imported %1\$d file.",
                            "Imported %1\$d files.",
                            counts.addedCount
                        )
                        val skippedMessage = desktopQuantityString(
                            "desktop_skipped_file_count",
                            skippedCount,
                            "Skipped %1\$d file.",
                            "Skipped %1\$d files.",
                            skippedCount
                        )
                        it.withBanner(
                            desktopString(
                                "desktop_import_result_pair",
                                "%1\$s %2\$s",
                                importedMessage,
                                skippedMessage
                            )
                        )
                    }
                    counts.addedCount > 0 -> it.withBanner(
                        desktopQuantityString(
                            "desktop_imported_file_count",
                            counts.addedCount,
                            "Imported %1\$d file.",
                            "Imported %1\$d files.",
                            counts.addedCount
                        )
                    )
                    counts.duplicateCount > 0 -> it.withBanner("Those files are already in the library.")
                    counts.failedCount > 0 -> it.withBanner(
                        desktopQuantityString(
                            "desktop_import_failed_file_count",
                            counts.failedCount,
                            "Could not import %1\$d file.",
                            "Could not import %1\$d files.",
                            counts.failedCount
                        ),
                        isError = true
                    )
                    else -> it
                }
            }
        updateState(next)
        onImported(importPlan.importedBooks)
        importPlan.importedBooks.forEach { imported ->
            queueCloudBookMetadataSync(imported, uploadContent = true)
        }
        val targetBookIds = importPlan.importedBooks.mapTo(mutableSetOf()) { it.id }
        if (targetBookIds.isEmpty()) return
        val originalTargetBooksById = next.rawLibraryBooks
            .filter { it.id in targetBookIds }
            .associateBy { it.id }

        scope.launch {
            val metadataResult = withContext(Dispatchers.IO) {
                DesktopFolderMetadataExtractor.enrichImportedBooks(
                    books = next.rawLibraryBooks,
                    importedBookIds = targetBookIds
                )
            }
            if (metadataResult.stats.updatedBooks > 0) {
                val enrichedBooksById = metadataResult.books
                    .filter { it.id in targetBookIds }
                    .associateBy { it.id }
                updateState(
                    state.copy(
                        rawLibraryBooks = state.rawLibraryBooks.map { book ->
                            val enriched = enrichedBooksById[book.id] ?: return@map book
                            book.withDesktopImportMetadata(
                                enriched = enriched,
                                original = originalTargetBooksById[book.id]
                            )
                        }
                    )
                )
                enrichedBooksById.values.forEach { enriched ->
                    queueCloudBookMetadataSync(enriched, uploadContent = false)
                }
            }
        }
    }

    fun importFiles(files: List<ImportedBookFile>, onImported: (List<BookItem>) -> Unit = {}) {
        if (files.isEmpty()) return
        updateState(
            state.withBanner(
                desktopQuantityString(
                    "desktop_importing_file_count",
                    files.size,
                    "Importing %1\$d file...",
                    "Importing %1\$d files...",
                    files.size
                )
            )
        )
        scope.launch {
            val preparedImport = withContext(Dispatchers.IO) {
                desktopBookImporter.prepareImports(files)
            }
            finishImportFiles(
                files = preparedImport.files,
                failedCount = preparedImport.failedCount,
                onImported = onImported
            )
        }
    }

    fun syncLocalFolders(
        targetFolder: File? = null,
        showBanner: Boolean = true,
        metadataOnly: Boolean = false
    ) {
        val mode = if (metadataOnly) "metadata" else "full"
        logDesktopFolderSync(
            "ui.sync.request mode=$mode target=\"${targetFolder?.absolutePath?.folderSyncPreview() ?: "ALL"}\" " +
                "showBanner=$showBanner linkedFolders=${state.syncedFolders.size} books=${state.rawLibraryBooks.size}"
        )
        if (targetFolder == null && state.syncedFolders.isEmpty()) {
            logDesktopFolderSync("ui.sync.skipNoFolders mode=$mode")
            updateState(state.withBanner("No local folders are linked yet.", isError = true))
            return
        }

        val snapshotState = state
        val snapshotShelfRefs = shelfRefs
        if (showBanner) {
            val message = if (metadataOnly) {
                "Folder sync: updating metadata..."
            } else {
                "Folder sync: scanning local folders..."
            }
            updateState(state.withBanner(message))
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DesktopLocalFolderSync.sync(
                    state = snapshotState,
                    shelfRefs = snapshotShelfRefs,
                    targetFolder = targetFolder,
                    metadataOnly = metadataOnly
                )
            }
            val failedCount = result.failedFolders.size
            val stats = result.stats
            val metadataStats = result.metadataStats
            val message = when {
                failedCount > 0 && stats.supportedFiles == 0 ->
                    desktopQuantityString(
                        "desktop_folder_sync_failed_folder_count",
                        failedCount,
                        "Folder sync failed for %1\$d folder.",
                        "Folder sync failed for %1\$d folders.",
                        failedCount
                    )
                failedCount > 0 ->
                    desktopQuantityString(
                        "desktop_folder_sync_skipped_folder_count",
                        failedCount,
                        "Folder sync finished with %1\$d folder skipped.",
                        "Folder sync finished with %1\$d folders skipped.",
                        failedCount
                    )
                metadataOnly ->
                    "Folder metadata sync complete."
                else ->
                    "Folder sync complete: ${stats.newBooks} new, ${stats.updatedBooks + stats.remoteMetadataUpdates + metadataStats.updatedBooks} updated, ${stats.removedBooks} removed."
            }
            logDesktopFolderSync(
                "ui.sync.result mode=$mode failed=$failedCount message=\"${message.folderSyncPreview()}\" " +
                    "new=${stats.newBooks} updated=${stats.updatedBooks} remoteUpdates=${stats.remoteMetadataUpdates} " +
                    "removed=${stats.removedBooks} metadataExtracted=${metadataStats.updatedBooks}"
            )
            val completedState = if (showBanner || failedCount > 0) {
                result.state.withBanner(message, isError = failedCount > 0)
            } else {
                result.state
            }
            replaceLibrary(
                completedState,
                refs = result.shelfRefs
            )
            val existingBookIds = completedState.rawLibraryBooks.mapTo(mutableSetOf()) { it.id }
            readerWindows = readerWindows.mapNotNull { window ->
                val migratedBookId = result.idMigrations[window.bookId] ?: window.bookId
                if (migratedBookId !in existingBookIds) {
                    window.closeReaderResources()
                    null
                } else if (migratedBookId != window.bookId) {
                    val migratedContent = when (val content = window.content) {
                        DesktopReaderWindowContent.Opening -> content
                        is DesktopReaderWindowContent.PasswordRequired -> content.copy(
                            book = content.book.copy(id = migratedBookId)
                        )
                        is DesktopReaderWindowContent.Pdf -> content.copy(
                            book = content.book.copy(id = migratedBookId)
                        )
                        is DesktopReaderWindowContent.Text -> content.copy(
                            book = content.book.copy(id = migratedBookId)
                        )
                    }
                    window.copy(
                        id = migratedBookId,
                        opening = window.opening.copy(bookId = migratedBookId),
                        content = migratedContent
                    )
                } else {
                    window
                }
            }
            queueFullCloudSyncAfterLocalChange()
        }
    }

    fun syncFolderMetadata(showBanner: Boolean = true) {
        syncLocalFolders(showBanner = showBanner, metadataOnly = true)
    }

    fun scanSyncedFolders(showBanner: Boolean = true) {
        syncLocalFolders(showBanner = showBanner, metadataOnly = false)
    }

    fun syncDesktopLibrary(showBanner: Boolean = true) {
        val hasCloud = state.isSyncEnabled
        val hasFolders = state.syncedFolders.isNotEmpty()
        if (!hasCloud && !hasFolders) {
            updateState(state.withBanner("No sync methods are active.", isError = true))
            return
        }
        if (hasFolders) {
            scanSyncedFolders(showBanner = showBanner)
        } else if (hasCloud) {
            syncDesktopCloud(showBanner = showBanner)
        }
    }

    fun importFolder(folder: File) {
        logDesktopFolderSync("ui.importFolder.request folder=\"${folder.absolutePath.folderSyncPreview()}\"")
        if (!DesktopLocalFolderSync.hasSupportedFiles(folder)) {
            logDesktopFolderSync("ui.importFolder.skipNoSupportedFiles folder=\"${folder.absolutePath.folderSyncPreview()}\"")
            updateState(state.withBanner("That folder does not contain any supported desktop reader files.", isError = true))
            return
        }
        syncLocalFolders(targetFolder = folder)
    }

    fun importCustomFont(file: File?): CustomFontItem? {
        val source = file ?: return null
        return customFontStore.importFont(source)
            .onSuccess { font ->
                customFonts = (customFonts.filterNot { it.id == font.id } + font)
                    .filterNot { it.isDeleted }
                    .sortedBy { it.displayName.lowercase() }
                updateState(state.withBanner("Imported ${font.displayName}."))
                queueFullCloudSyncAfterLocalChange()
            }
            .onFailure { error ->
                updateState(state.withBanner(error.message ?: "Could not import font.", isError = true))
            }
            .getOrNull()
    }

    fun downloadGoogleFont(fontName: String, onComplete: () -> Unit) {
        if (!featurePolicy.googleFontsDownload) {
            updateState(state.withBanner("Google Fonts download is unavailable in this desktop build.", isError = true))
            onComplete()
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                customFontStore.downloadGoogleFont(fontName)
            }
            result
                .onSuccess { font ->
                    customFonts = (customFonts.filterNot { it.id == font.id } + font)
                        .filterNot { it.isDeleted }
                        .sortedBy { it.displayName.lowercase() }
                    updateState(state.withBanner("${font.displayName} downloaded successfully."))
                    queueFullCloudSyncAfterLocalChange()
                }
                .onFailure { error ->
                    updateState(state.withBanner(error.message ?: "Could not download $fontName.", isError = true))
                }
            onComplete()
        }
    }

    fun deleteCustomFont(font: CustomFontItem) {
        customFontStore.deleteFont(font)
        customFonts = customFonts.filterNot { it.id == font.id }
        val resetAppFont = state.appFontPreference.referencesCustomFont(font.id)
        val clearedSettings = state.rawLibraryBooks.map { book ->
            val settings = book.readerSettings
            if (settings?.customFontPath == font.path) {
                book.copy(readerSettings = settings.copy(fontFamily = "Default", customFontPath = null))
            } else {
                book
            }
        }
        readerWindows = readerWindows.replaceAllDesktopTextReaderContent { content ->
            if (content.session.reader.settings.customFontPath == font.path) {
                content.copy(
                    session = readerEngine.updateSettings(
                        content.session,
                        content.session.reader.settings.copy(fontFamily = "Default", customFontPath = null)
                    )
                )
            } else {
                content
            }
        }
        val nextState = state.copy(
            rawLibraryBooks = clearedSettings,
            appFontPreference = if (resetAppFont) AppFontPreference.System else state.appFontPreference
        )
        updateState(nextState.withBanner("Deleted ${font.displayName}."))
        deleteCustomFontFromDesktopCloud(font)
    }

    fun removeSelectedBooks() {
        val booksToRemove = state.rawLibraryBooks.filter { it.id in state.selectedBookIds }
        closeReaderWindowsForBookIds(booksToRemove.mapTo(mutableSetOf()) { it.id })
        SharedLibraryEditor.removeSelectedBooks(state, shelfRecords, shelfRefs)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
            deleteBooksFromDesktopCloud(booksToRemove)
        }
    }

    fun createShelf(name: String) {
        SharedLibraryEditor.createShelf(state, shelfRecords, shelfRefs, name, System.currentTimeMillis())?.let { result ->
            replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
            result.shelfRecords.lastOrNull { record -> record.name == name.trim() }
                ?.let { record -> syncCloudShelfChange(record, result.shelfRefs) }
        }
    }

    fun createSmartShelf(name: String, definition: SmartCollectionDefinition) {
        SharedLibraryEditor.createSmartShelf(state, shelfRecords, shelfRefs, name, definition, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun renameShelf(shelf: Shelf, name: String) {
        val previousRecord = shelfRecords.firstOrNull { it.id == shelf.id }
        SharedLibraryEditor.renameShelf(state, shelfRecords, shelfRefs, shelf, name)?.let { result ->
            replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
            result.shelfRecords.firstOrNull { record -> record.id == shelf.id }
                ?.let { record ->
                    if (previousRecord != null && previousRecord.name != record.name) {
                        syncCloudShelfChange(previousRecord, shelfRefs, isDeleted = true)
                    }
                    syncCloudShelfChange(record, result.shelfRefs)
                }
        }
    }

    fun deleteShelf(shelf: Shelf) {
        val record = shelfRecords.firstOrNull { it.id == shelf.id }
        val result = SharedLibraryEditor.deleteShelf(state, shelfRecords, shelfRefs, shelf)
        replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
        record?.let { syncCloudShelfChange(it, shelfRefs, isDeleted = true) }
    }

    fun addSelectedBooksToShelf(shelfId: String) {
        SharedLibraryEditor.addSelectedBooksToShelf(state, shelfRecords, shelfRefs, shelfId, System.currentTimeMillis())?.let { result ->
            replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
            result.shelfRecords.firstOrNull { record -> record.id == shelfId }
                ?.let { record -> syncCloudShelfChange(record, result.shelfRefs) }
        }
    }

    fun tagSelectedBooks(tagName: String) {
        SharedLibraryEditor.tagSelectedBooks(state, shelfRecords, shelfRefs, tagName, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun applyBookMetadataUpdate(updated: BookItem) {
        val result = SharedLibraryEditor.updateBookMetadata(state, shelfRecords, shelfRefs, updated, System.currentTimeMillis())
        replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
        result.state.rawLibraryBooks.firstOrNull { it.id == updated.id }?.let { book ->
            syncBookSidecars(book)
            queueCloudBookMetadataSync(book, uploadContent = book.fileContentModifiedTimestamp > 0L)
        }
    }

    fun writeDesktopEpubMetadata(original: BookItem, updated: BookItem): BookItem {
        val file = File(original.path ?: error("Book path is missing."))
        require(file.isFile && file.canWrite()) { "EPUB file is not writable." }
        val backup = File(
            File(desktopUserDataRoot(), "metadata_backups").apply { mkdirs() },
            "${original.id.toDesktopSafeFileName()}.epub"
        )
        val snapshot = SharedEpubMetadataEditor.rewriteInPlace(
            source = file,
            backup = backup,
            update = SharedEpubMetadataUpdate(
                title = updated.title,
                author = updated.author,
                description = updated.description,
                seriesName = updated.seriesName,
                seriesIndex = updated.seriesIndex
            )
        )
        return updated.copy(
            title = snapshot.title ?: updated.title,
            author = snapshot.author,
            description = snapshot.description,
            seriesName = snapshot.seriesName,
            seriesIndex = snapshot.seriesIndex,
            originalTitle = original.originalTitle ?: original.title,
            originalAuthor = original.originalAuthor ?: original.author,
            originalSeriesName = original.originalSeriesName ?: original.seriesName,
            originalSeriesIndex = original.originalSeriesIndex ?: original.seriesIndex,
            originalDescription = original.originalDescription ?: original.description,
            fileSize = file.length(),
            fileContentModifiedTimestamp = file.lastModified()
        )
    }

    fun updateBookMetadata(updated: BookItem) {
        val original = state.rawLibraryBooks.firstOrNull { it.id == updated.id }
        if (original != null && original.type == FileType.EPUB && original.hasEmbeddedMetadataChange(updated)) {
            scope.launch {
                val rewritten = runCatching {
                    withContext(Dispatchers.IO) {
                        writeDesktopEpubMetadata(original, updated)
                    }
                }
                rewritten.onSuccess(::applyBookMetadataUpdate)
                    .onFailure { error ->
                        println("Failed to update EPUB metadata for ${updated.displayName}: ${error.message}")
                        updateState(state.copy(bannerMessage = BannerMessage("Could not update EPUB metadata.")))
                    }
            }
            return
        }

        applyBookMetadataUpdate(updated)
    }

    fun recordBookOpened(bookId: String) {
        val now = System.currentTimeMillis()
        val next = SharedLibraryEditor.markBookOpened(state, bookId, now)
        val openedState = next.reduce(AppAction.BookTabOpened(bookId))
        updateState(openedState)
        openedState.rawLibraryBooks.firstOrNull { it.id == bookId }?.let { book ->
            syncBookSidecars(book)
            queueCloudBookMetadataSync(book)
        }
    }

    fun scheduleOpenedBookMetadataExtraction(book: BookItem) {
        scope.launch {
            val enriched = withContext(Dispatchers.IO) {
                DesktopFolderMetadataExtractor.enrichOpenedBook(book)
            }
            if (enriched == book) return@launch
            updateState(
                state.copy(
                    rawLibraryBooks = state.rawLibraryBooks.map { current ->
                        if (current.id == book.id) {
                            current.withDesktopImportMetadata(enriched = enriched, original = book)
                        } else {
                            current
                        }
                    }
                )
            )
            state.rawLibraryBooks.firstOrNull { it.id == book.id }?.let { queueCloudBookMetadataSync(it) }
        }
    }

    fun schedulePdfEmbeddedAnnotationsLoad(windowId: String, document: DesktopPdfDocument) {
        scope.launch {
            delay(650L)
            val stillOpen = readerWindows.any { window ->
                window.id == windowId &&
                    (window.content as? DesktopReaderWindowContent.Pdf)?.document?.handleId == document.handleId
            }
            if (!stillOpen) return@launch
            val annotations = withContext(Dispatchers.IO) {
                DesktopPdfium.loadEmbeddedAnnotations(document)
            }
            val stillCurrent = readerWindows.any { window ->
                window.id == windowId &&
                    (window.content as? DesktopReaderWindowContent.Pdf)?.document?.handleId == document.handleId
            }
            if (stillCurrent) {
                document.replaceEmbeddedAnnotations(annotations)
            }
        }
    }

    fun exitReaderTo(tab: SharedAppTab) {
        selectedTab = tab.takeUnless { it == SharedAppTab.READER } ?: SharedAppTab.LIBRARY
    }

    fun selectAppTab(tab: SharedAppTab) {
        val nextTab = if (tab == SharedAppTab.CATALOGS && !featurePolicy.opdsCatalogs) {
            SharedAppTab.HOME
        } else {
            tab
        }
        if (nextTab == SharedAppTab.SETTINGS) {
            settingsQuery = ""
            settingsDestination = SharedSettingsDestination.ROOT
        }
        if (nextTab == SharedAppTab.READER) {
            state.activeTabBookId?.let { bookId ->
                readerWindows = readerWindows.focusDesktopReaderWindow(bookId)
            }
            selectedTab = SharedAppTab.LIBRARY
        } else {
            selectedTab = nextTab
        }
    }

    fun applyReaderOpenResult(result: DesktopReaderOpenResult) {
        val window = readerWindows.firstOrNull { it.opening.requestId == result.opening.requestId }
        if (window == null) {
            if (result is DesktopReaderOpenResult.Pdf) {
                result.document.close()
            }
            return
        }

        when (result) {
            is DesktopReaderOpenResult.Failure -> {
                readerWindows = readerWindows.withoutDesktopReaderWindow(window.id)
                updateState(state.withBanner(result.message, isError = true))
            }

            is DesktopReaderOpenResult.PasswordRequired -> {
                readerWindows = readerWindows.withDesktopReaderWindowContent(
                    requestId = result.opening.requestId,
                    content = DesktopReaderWindowContent.PasswordRequired(
                        book = result.book,
                        attemptedPassword = result.attemptedPassword
                    )
                )
            }

            is DesktopReaderOpenResult.Pdf -> {
                (window.content as? DesktopReaderWindowContent.Pdf)
                    ?.document
                    ?.takeIf { it.handleId != result.document.handleId }
                    ?.close()
                readerWindows = readerWindows.withDesktopReaderWindowContent(
                    requestId = result.opening.requestId,
                    content = DesktopReaderWindowContent.Pdf(
                        book = result.book,
                        document = result.document
                    )
                )
                recordBookOpened(result.book.id)
                if (result.book.type == FileType.PDF) {
                    schedulePdfEmbeddedAnnotationsLoad(window.id, result.document)
                }
            }

            is DesktopReaderOpenResult.Text -> {
                val cloudTts = ReaderCloudTtsState(
                    isAvailable = effectiveAiSettings().isCloudTtsAvailable,
                    cacheSummary = desktopTtsAdapter.cacheSummary(
                        result.session.reader.book.title,
                        aiByokSettings.sanitized().ttsSpeakerId
                    )
                )
                readerWindows = readerWindows.withDesktopReaderWindowContent(
                    requestId = result.opening.requestId,
                    content = DesktopReaderWindowContent.Text(
                        book = result.book,
                        session = result.session,
                        extrasState = ReaderExtrasState(cloudTts = cloudTts)
                    )
                )
                recordBookOpened(result.book.id)
            }
        }
    }

    fun openReader(
        book: BookItem,
        password: String? = null,
        force: Boolean = false,
        returnTabOverride: SharedAppTab? = null
    ) {
        val desktopReaderSurface = SharedFileCapabilities.surfaceFor(book.type, ReaderPlatform.DESKTOP)
        if (shouldRequestDesktopWebViewRuntime(desktopReaderSurface)) {
            webViewRuntimeRequested = true
        }

        if (desktopReaderSurface == ReaderFeatureSurface.PDF_VIEWER) {
            val path = book.path
            if (path.isNullOrBlank()) {
                updateState(
                    state.withBanner(
                        "This ${SharedFileCapabilities.displayNameFor(book.type)} does not have a local path.",
                        isError = true
                    )
                )
                return
            }
            val streamReference = SharedOpdsStreamUri.parse(path)
            if (streamReference != null && !featurePolicy.opdsCatalogs) {
                updateState(state.withBanner("OPDS streams are unavailable in this desktop build.", isError = true))
                return
            }
        } else if (
            desktopReaderSurface == ReaderFeatureSurface.EPUB_READER ||
            desktopReaderSurface == ReaderFeatureSurface.TEXT_READER
        ) {
        } else {
            updateState(
                state.withBanner(
                    "${SharedFileCapabilities.displayNameFor(book.type)} reader support comes later. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are available on desktop."
                )
            )
            return
        }

        scheduleOpenedBookMetadataExtraction(book)

        val opening = DesktopReaderOpening(
            requestId = ++nextReaderOpenRequestId,
            bookId = book.id,
            title = book.cardTitleForMessage(),
            formatLabel = SharedFileCapabilities.displayNameFor(book.type),
            returnTab = returnTabOverride
                ?: selectedTab.takeUnless { it == SharedAppTab.READER }
                ?: SharedAppTab.LIBRARY,
            password = password
        )
        val readerDefaultSettings = state.readerDefaultSettings
        if (force) {
            readerWindows.firstOrNull { it.bookId == book.id }?.closeReaderResources()
        }
        val openDecision = readerWindows.openOrFocusDesktopReaderWindow(opening, force)
        readerWindows = openDecision.windows
        if (!openDecision.shouldStartOpen) {
            recordBookOpened(book.id)
            return
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (desktopReaderSurface) {
                        ReaderFeatureSurface.PDF_VIEWER -> {
                            val path = book.path.orEmpty()
                            val streamReference = SharedOpdsStreamUri.parse(path)
                            val document = if (streamReference != null) {
                                DesktopPdfium.loadOpdsStream(
                                    path = path,
                                    title = book.title?.takeIf { it.isNotBlank() } ?: book.displayName,
                                    reference = streamReference,
                                    catalog = opdsRepository.catalogById(streamReference.catalogId)
                                )
                            } else {
                                val readerFile = File(path)
                                when (book.type) {
                                    FileType.PDF -> DesktopPdfium.load(
                                        readerFile,
                                        password = opening.password,
                                        loadEmbeddedAnnotations = false
                                    )
                                    FileType.PPTX -> DesktopPdfium.loadPptx(readerFile)
                                    else -> DesktopPdfium.loadComic(readerFile, book.type)
                                }
                            }
                            DesktopReaderOpenResult.Pdf(opening, book, document)
                        }

                        ReaderFeatureSurface.EPUB_READER,
                        ReaderFeatureSurface.TEXT_READER -> {
                            val path = book.path?.takeIf { it.isNotBlank() } ?: error("Book path is missing.")
                            val loadedBook = SharedJvmBookLoader.load(
                                file = File(path),
                                type = book.type,
                                titleOverride = book.title?.takeIf { it.isNotBlank() },
                                authorOverride = book.author?.takeIf { it.isNotBlank() }
                            )
                            val restoredSettings = resolvedDesktopReaderSettings(book, readerDefaultSettings)
                            val restoredSession = readerEngine.createSession(
                                book = loadedBook,
                                settings = restoredSettings,
                                initialPageIndex = book.lastPageIndex ?: 0,
                                initialLocator = book.readerPosition,
                                bookmarks = book.readerBookmarks,
                                highlights = book.readerHighlights
                            )
                            val restoredProgress = book.progressPercentage
                            val session = if (book.readerPosition == null && book.lastPageIndex == null && restoredProgress != null) {
                                readerEngine.goToProgress(restoredSession, restoredProgress.coerceIn(0f, 100f) / 100f)
                            } else {
                                restoredSession
                            }
                            DesktopReaderOpenResult.Text(opening, book, session)
                        }

                        else -> error("${SharedFileCapabilities.displayNameFor(book.type)} reader support comes later.")
                    }
                }.getOrElse { error ->
                    if (desktopReaderSurface == ReaderFeatureSurface.PDF_VIEWER &&
                        book.type == FileType.PDF &&
                        error.isDesktopPdfPasswordException()
                    ) {
                        DesktopReaderOpenResult.PasswordRequired(
                            opening = opening,
                            book = book,
                            attemptedPassword = !opening.password.isNullOrEmpty()
                        )
                    } else {
                        DesktopReaderOpenResult.Failure(
                            opening = opening,
                            book = book,
                            message = "Could not open ${SharedFileCapabilities.displayNameFor(book.type)}: " +
                                (error.message ?: "unknown error")
                        )
                    }
                }
            }
            applyReaderOpenResult(result)
        }
    }

    fun requestPdfReflow(
        sourceBook: BookItem,
        document: DesktopPdfDocument,
        pageIndex: Int
    ) {
        if (document.formatLabel != "PDF") return
        val reflowBookId = desktopPdfReflowBookId(sourceBook.id)
        val existingReflowBook = state.rawLibraryBooks.firstOrNull { book ->
            book.id == reflowBookId &&
                book.path?.takeIf { it.isNotBlank() }?.let { File(it).isFile } == true
        }
        if (existingReflowBook != null) {
            openReader(
                existingReflowBook.copy(lastPageIndex = pageIndex),
                force = true
            )
            return
        }
        if (sourceBook.id in reflowingPdfBookIds) return

        reflowingPdfBookIds = reflowingPdfBookIds + sourceBook.id
        updateState(state.withBanner("Generating Text View..."))
        scope.launch {
            try {
                val originalTitle = sourceBook.title?.takeIf { it.isNotBlank() }
                    ?: sourceBook.displayName.substringBeforeLast('.', sourceBook.displayName)
                        .takeIf { it.isNotBlank() }
                    ?: document.title
                val destination = desktopBookImporter.createBookFile(
                    desktopPdfReflowFileName(sourceBook.id, originalTitle)
                )
                val generated = withContext(Dispatchers.IO) {
                    DesktopPdfReflowGenerator.generateHtmlFile(
                        document = document,
                        destFile = destination,
                        startPage = 1,
                        onProgress = {}
                    )
                }
                if (!generated || !destination.isFile || destination.length() <= 0L) {
                    runCatching { destination.delete() }
                    updateState(state.withBanner("Text view generation failed.", isError = true))
                    return@launch
                }

                val reflowBook = desktopPdfReflowBookItem(
                    sourceBook = sourceBook,
                    generatedFile = destination,
                    nowMillis = System.currentTimeMillis(),
                    initialPageIndex = pageIndex
                )
                updateState(
                    state.copy(
                        rawLibraryBooks = listOf(reflowBook) + state.rawLibraryBooks.filterNot { it.id == reflowBook.id }
                    ).withBanner("Generated Text View.")
                )
                openReader(
                    reflowBook,
                    force = true
                )
            } catch (error: Throwable) {
                updateState(
                    state.withBanner(
                        error.message ?: "Text view generation failed.",
                        isError = true
                    )
                )
            } finally {
                reflowingPdfBookIds = reflowingPdfBookIds - sourceBook.id
            }
        }
    }

    fun removeFolder(shelf: Shelf) {
        val removedBookIds = shelf.books.mapTo(mutableSetOf()) { it.id }
        closeReaderWindowsForBookIds(removedBookIds)
        SharedLibraryEditor.removeFolder(state, shelfRecords, shelfRefs, shelf)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
            queueFullCloudSyncAfterLocalChange()
        }
    }

    fun closeReaderTab(book: BookItem) {
        readerWindows.firstOrNull { it.bookId == book.id }?.let { window ->
            closeReaderWindow(window.id)
        } ?: updateState(state.reduce(AppAction.BookTabClosed(book.id)))
    }

    fun closeAllReaderTabs() {
        closeAllReaderWindows()
    }

    fun importAndOpenBook() {
        val file = chooseBookFile() ?: return
        val importedFile = file.toDesktopImportedBookFile()
        val type = importedFile.desktopFileType()
        if (type !in DesktopBookFileTypes) {
            updateState(
                state.withBanner(
                    "No supported desktop reader file was selected. " +
                        "${SharedFileCapabilities.supportedFormatsLabel(ReaderPlatform.DESKTOP)} are supported.",
                    isError = true
                )
            )
            return
        }
        importFiles(listOf(importedFile)) { importedBooks ->
            importedBooks.firstOrNull()?.let(::openReader)
        }
    }

    fun importAndOpenPdf() {
        val file = choosePdfFile() ?: return
        importFiles(listOf(file.toDesktopImportedBookFile())) { importedBooks ->
            importedBooks.firstOrNull()?.let(::openReader)
        }
    }

    fun emitOpds(next: com.aryan.reader.shared.opds.SharedOpdsScreenState) {
        opdsState = next
    }

    fun openOpdsCatalog(catalog: OpdsCatalog) {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.openCatalog(catalog, ::emitOpds)
        }
    }

    fun openOpdsFeedUrl(url: String) {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.openFeedUrl(url, ::emitOpds)
        }
    }

    fun navigateOpdsBack() {
        scope.launch {
            opdsController.navigateBack(::emitOpds)
        }
    }

    fun searchOpds(query: String) {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.search(query, ::emitOpds)
        }
    }

    fun loadNextOpdsPage() {
        if (!featurePolicy.opdsCatalogs) return
        scope.launch {
            opdsController.loadNextPage(::emitOpds)
        }
    }

    fun removeOpdsCatalog(catalog: OpdsCatalog) {
        emitOpds(opdsController.removeCatalog(catalog.id))
        val streamBookIds = state.rawLibraryBooks
            .filter { book -> SharedOpdsStreamUri.parse(book.path)?.catalogId == catalog.id }
            .mapTo(mutableSetOf()) { it.id }
        if (streamBookIds.isNotEmpty()) {
            closeReaderWindowsForBookIds(streamBookIds)
            updateState(
                state.copy(
                    rawLibraryBooks = state.rawLibraryBooks.filterNot { it.id in streamBookIds },
                    openTabIds = state.openTabIds.filterNot { it in streamBookIds },
                    activeTabBookId = state.activeTabBookId?.takeUnless { it in streamBookIds }
                ).withBanner(
                    desktopQuantityString(
                        "desktop_opds_removed_stream_book_count",
                        streamBookIds.size,
                        "Removed %1\$d streamed OPDS book from that catalog.",
                        "Removed %1\$d streamed OPDS books from that catalog.",
                        streamBookIds.size
                    )
                )
            )
        }
    }

    fun downloadOpdsBook(entry: OpdsEntry, acquisition: OpdsAcquisition) {
        if (!featurePolicy.opdsCatalogs) {
            updateState(state.withBanner("OPDS downloads are unavailable in this desktop build.", isError = true))
            return
        }
        val catalog = opdsState.currentCatalog
        scope.launch {
            emitOpds(opdsController.updateDownloadState(entry.id, SharedOpdsDownloadState(true, 0f)))
            val result = runCatching {
                opdsRepository.downloadBook(entry, acquisition, catalog) { progress ->
                    scope.launch {
                        if (opdsController.state.downloadingState[entry.id]?.isDownloading == true) {
                            emitOpds(opdsController.updateDownloadState(entry.id, SharedOpdsDownloadState(true, progress)))
                        }
                    }
                }
            }
            emitOpds(opdsController.updateDownloadState(entry.id, null))
            result.onSuccess { file ->
                importFiles(listOf(file.toDesktopImportedBookFile()))
                updateState(state.withBanner("Downloaded ${file.name} from OPDS."))
            }.onFailure { error ->
                updateState(
                    state.withBanner(
                        "Could not download ${entry.title}: ${error.message ?: "unknown error"}",
                        isError = true
                    )
                )
            }
        }
    }

    fun streamOpdsBook(entry: OpdsEntry, catalog: OpdsCatalog?) {
        if (!featurePolicy.opdsCatalogs) {
            updateState(state.withBanner("OPDS streams are unavailable in this desktop build.", isError = true))
            return
        }
        val pageCount = entry.pseCount
        val urlTemplate = entry.pseUrlTemplate
        if (pageCount == null || pageCount <= 0 || urlTemplate.isNullOrBlank()) {
            updateState(state.withBanner("This OPDS entry does not expose a readable stream.", isError = true))
            return
        }
        val reference = OpdsStreamReference(
            id = entry.id.ifBlank { "${entry.title}:$urlTemplate" },
            count = pageCount,
            urlTemplate = urlTemplate,
            catalogId = catalog?.id
        )
        val uriString = SharedOpdsStreamUri.build(reference)
        val now = System.currentTimeMillis()
        val streamBook = BookItem(
            id = uriString,
            path = uriString,
            type = FileType.CBZ,
            displayName = entry.title,
            timestamp = now,
            title = entry.title,
            author = entry.author,
            fileSize = 0L
        )
        if (state.rawLibraryBooks.none { it.id == streamBook.id }) {
            updateState(state.copy(rawLibraryBooks = state.rawLibraryBooks + streamBook))
        }
        openReader(streamBook)
    }

    val latestReaderWindows by rememberUpdatedState(readerWindows)
    DisposableEffect(Unit) {
        onDispose {
            latestReaderWindows.forEach { it.closeReaderResources() }
        }
    }

    DesktopFileDropTarget(
        window = window,
        onFilesDropped = ::importFiles,
        onDragStateChange = { dropImportState = it }
    )

    LaunchedEffect(Unit) {
        if (featurePolicy.aiAndCloud && !desktopBuildProfile.byokAiAvailable) {
            refreshDesktopAccountProfile(showBanner = false)
        }
    }

    LaunchedEffect(accountRefreshRequestCount) {
        if (accountRefreshRequestCount > 0 && featurePolicy.aiAndCloud && !desktopBuildProfile.byokAiAvailable) {
            refreshDesktopAccountProfile(showBanner = false)
        }
    }

    LaunchedEffect(state.isSyncEnabled, state.currentUser?.uid, state.isProUser) {
        if (
            !initialDesktopCloudSyncDone &&
            state.isSyncEnabled &&
            state.currentUser != null &&
            state.isProUser
        ) {
            initialDesktopCloudSyncDone = true
            syncDesktopCloud(showBanner = false).join()
        }
    }

    LaunchedEffect(Unit) {
        if (state.syncedFolders.isNotEmpty()) {
            scanSyncedFolders(showBanner = false)
        }
    }

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let { banner ->
            snackbarHostState.showSnackbar(banner.text?.let(desktopStringResolver::sharedText) ?: banner.message)
            updateState(state.reduce(AppAction.BannerDismissed))
        }
    }

    LaunchedEffect(aiByokSettings, state.currentUser, state.credits) {
        readerWindows = readerWindows.replaceAllDesktopTextReaderContent { content ->
            content.copy(
                extrasState = content.extrasState.copy(
                    cloudTts = content.extrasState.cloudTts.copy(
                        isAvailable = effectiveAiSettings().isCloudTtsAvailable,
                        errorMessage = null,
                        cacheSummary = textReaderTtsCacheSummary(content)
                    )
                )
            )
        }
    }

    val desktopAppFontFamily = remember(state.appFontPreference, customFonts) {
        state.appFontPreference.toDesktopAppFontFamily(customFonts)
    }

    CompositionLocalProvider(LocalSharedStringResolver provides desktopStringResolver) {
        SharedAppTheme(
            appThemeMode = state.appThemeMode,
            appContrastOption = state.appContrastOption,
            appTextDimFactorLight = state.appTextDimFactorLight,
            appTextDimFactorDark = state.appTextDimFactorDark,
            appSeedColor = state.appSeedColor,
            appFontFamily = desktopAppFontFamily
        ) {
        EpistemeDesktopWindowChromeEffect(
            window = window,
            captionColor = MaterialTheme.colorScheme.surface,
            textColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.background
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SharedAppShell(
                selectedTab = selectedTab,
                snackbarHostState = snackbarHostState,
                appThemeMode = state.appThemeMode,
                appContrastOption = state.appContrastOption,
                appTextDimFactorLight = state.appTextDimFactorLight,
                appTextDimFactorDark = state.appTextDimFactorDark,
                appSeedColor = state.appSeedColor,
                customAppThemes = state.customAppThemes,
                isTabsEnabled = state.isTabsEnabled,
                featurePolicy = featurePolicy,
                onTabSelected = { tab ->
                    selectAppTab(tab)
                },
                onImportFiles = { importFiles(chooseFiles()) },
                onImportFolder = { chooseFolder()?.let(::importFolder) },
                onSyncRequested = { syncDesktopLibrary() },
                onFolderMetadataSyncRequested = { syncFolderMetadata() },
                onAppThemeModeChange = { mode -> updateState(state.reduce(AppAction.AppThemeChanged(mode))) },
                onAppContrastOptionChange = { option -> updateState(state.reduce(AppAction.AppContrastChanged(option))) },
                onAppTextDimFactorLightChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorLightChanged(factor))) },
                onAppTextDimFactorDarkChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorDarkChanged(factor))) },
                onAppSeedColorChange = { color -> updateState(state.reduce(AppAction.AppSeedColorChanged(color))) },
                onCustomAppThemeAdded = { theme -> updateState(state.reduce(AppAction.CustomAppThemeAdded(theme))) },
                onCustomAppThemeDeleted = { themeId -> updateState(state.reduce(AppAction.CustomAppThemeDeleted(themeId))) },
                onTabsEnabledChange = { enabled ->
                    if (!enabled) closeAllReaderWindows()
                    updateState(state.reduce(AppAction.TabsEnabledChanged(enabled)))
                },
                onAiSettingsRequested = if (desktopBuildProfile.byokAiAvailable) {
                    { showAiByokSettingsDialog = true }
                } else {
                    null
                }
            ) { tab ->
                when (tab) {
                        SharedAppTab.HOME -> HomeScreen(
                            state = state,
                            selectedLibraryTab = selectedLibraryTab,
                            onLibraryTabChange = { selectedLibraryTab = it },
                            onStateChange = ::updateState,
                            onImportBooks = {
                                importFiles(chooseFiles())
                            },
                            onImportFolder = { chooseFolder()?.let(::importFolder) },
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            onClearSelection = { updateState(state.reduce(LibraryAction.SelectionCleared)) },
                            onRemoveSelected = ::removeSelectedBooks,
                            onShowBookInfo = {
                                bookInfoInitiallyEditing = false
                                bookInfoDialogFor = it
                            },
                            onEditBook = {
                                bookInfoInitiallyEditing = true
                                bookInfoDialogFor = it
                            },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onCreateSmartShelf = { showCreateSmartShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onRemoveFolder = { folderToRemove = it },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true },
                            onSyncFolderMetadata = { syncFolderMetadata() },
                            onScanFolders = { scanSyncedFolders() },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.LibraryPinToggled(book.id))) }
                        )

                        SharedAppTab.SETTINGS -> SharedSettingsHub(
                            model = sharedSettingsHubModel(
                                SharedSettingsHubInput(
                                    platform = SharedSettingsPlatform.DESKTOP,
                                    featurePolicy = featurePolicy,
                                    isDebugBuild = false,
                                    isSignedIn = state.currentUser != null,
                                    isProUser = state.isProUser,
                                    accountAvailable = featurePolicy.aiAndCloud && !desktopBuildProfile.byokAiAvailable,
                                    syncAvailable = desktopCloudSyncAvailable(),
                                    folderSyncAvailable = true,
                                    aiSettingsAvailable = desktopBuildProfile.byokAiAvailable,
                                    includeLanguage = true,
                                    includeScreenCaptureProtection = false,
                                    includeExternalFileBehavior = false,
                                    includeStrictFileFilter = false,
                                    includeReaderTabs = false,
                                    includeHideReaderAi = featurePolicy.aiAndCloud,
                                    isTabsEnabled = state.isTabsEnabled,
                                    isSyncEnabled = state.isSyncEnabled,
                                    isFolderSyncEnabled = state.isFolderSyncEnabled,
                                    hideReaderAi = effectiveAiSettings().hideReaderAiFeatures,
                                    languageTitle = desktopString("options_language", "Language"),
                                    languageSummary = selectedDesktopLanguageOption(desktopLanguageTag).let { option ->
                                        desktopString(option.labelKey, option.fallbackLabel)
                                    }
                                )
                            ),
                            query = settingsQuery,
                            onQueryChange = { settingsQuery = it },
                            destination = settingsDestination,
                            onDestinationChange = { settingsDestination = it },
                            readerDefaultSettings = state.readerDefaultSettings,
                            onReaderDefaultSettingsChange = { settings ->
                                updateState(state.reduce(AppAction.ReaderDefaultSettingsChanged(settings)))
                            },
                            pdfReaderDefaultSettings = state.pdfReaderDefaultSettings,
                            onPdfReaderDefaultSettingsChange = { settings ->
                                updateState(state.reduce(AppAction.PdfReaderDefaultSettingsChanged(settings)))
                            },
                            readerToolbarPreferences = state.readerToolbarPreferences,
                            onReaderToolbarPreferencesChange = { preferences ->
                                updateState(state.reduce(AppAction.ReaderToolbarPreferencesChanged(preferences)))
                            },
                            ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                            onTtsReplacementPreferencesChange = { preferences ->
                                updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                            },
                            customFonts = customFonts,
                            onPickCustomFont = { importCustomFont(chooseFontFile())?.path },
                            readerCustomTextureIds = readerCustomTextureIds,
                            onImportReaderTexture = ::importDesktopReaderTexture,
                            onAction = { action ->
                                when (action) {
                                    SharedSettingsAction.APP_THEME -> showDesktopAppThemeSettingsDialog = true
                                    SharedSettingsAction.TABS_TOGGLE -> {
                                        if (state.isTabsEnabled) closeAllReaderWindows()
                                        updateState(state.reduce(AppAction.TabsEnabledChanged(!state.isTabsEnabled)))
                                    }
                                    SharedSettingsAction.FOLDER_SYNC -> setDesktopFolderSyncEnabled(!state.isFolderSyncEnabled)
                                    SharedSettingsAction.AI_SETTINGS -> if (desktopBuildProfile.byokAiAvailable) showAiByokSettingsDialog = true
                                    SharedSettingsAction.SIGN_IN -> signInDesktopAccount()
                                    SharedSettingsAction.SIGN_OUT -> signOutDesktopAccount()
                                    SharedSettingsAction.HIDE_READER_AI -> {
                                        val next = aiByokSettings.copy(hideReaderAiFeatures = !effectiveAiSettings().hideReaderAiFeatures)
                                        aiByokSettings = next
                                        runCatching { aiByokStore.save(next.sanitized()) }
                                    }
                                    SharedSettingsAction.CUSTOM_FONTS -> selectAppTab(SharedAppTab.CUSTOM_FONTS)
                                    SharedSettingsAction.HELP_FEEDBACK -> selectAppTab(SharedAppTab.FEEDBACK)
                                    SharedSettingsAction.SUPPORT -> selectAppTab(SharedAppTab.SUPPORT)
                                    SharedSettingsAction.ABOUT -> selectAppTab(SharedAppTab.ABOUT)
                                    SharedSettingsAction.CLEAR_BOOK_CACHE -> showClearBookCacheDialog = true
                                    SharedSettingsAction.CLEAR_REFLOW_CACHE,
                                    SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA,
                                    SharedSettingsAction.TEST_PANEL_DETECTION,
                                    SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
                                    SharedSettingsAction.EXPORT_LOGS,
                                    SharedSettingsAction.DEBUG_ACTIONS,
                                    SharedSettingsAction.DEVICE_MANAGEMENT,
                                    SharedSettingsAction.RECENT_LIMIT,
                                    SharedSettingsAction.STRICT_FILE_FILTER,
                                    SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME,
                                    SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
                                    SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                                    SharedSettingsAction.TTS_SETTINGS,
                                    SharedSettingsAction.PDF_READER_DEFAULTS,
                                    SharedSettingsAction.TEXT_READER_DEFAULTS,
                                    SharedSettingsAction.READER_TOOLBAR,
                                    SharedSettingsAction.TTS_REPLACEMENTS,
                                    SharedSettingsAction.LOCAL_OVERRIDE_NOTE -> Unit
                                    SharedSettingsAction.LANGUAGE -> showDesktopLanguageDialog = true
                                    SharedSettingsAction.CLOUD_SYNC -> setDesktopCloudSyncEnabled(!state.isSyncEnabled)
                                }
                            }
                        )

                        SharedAppTab.PRO -> DesktopProScreen(
                            user = state.currentUser,
                            isProUser = state.isProUser,
                            credits = state.credits,
                            authConfigured = desktopCloudConfig.isAuthConfigured,
                            isBusy = accountBusy,
                            statusMessage = accountStatusMessage,
                            onSignIn = ::signInDesktopAccount,
                            onSignOut = ::signOutDesktopAccount,
                            onRefresh = {
                                scope.launch {
                                    accountBusy = true
                                    refreshDesktopAccountProfile(showBanner = true)
                                    accountBusy = false
                                }
                            }
                        )

                        SharedAppTab.LIBRARY -> LibraryScreen(
                            state = state,
                            selectedLibraryTab = selectedLibraryTab,
                            onLibraryTabChange = { selectedLibraryTab = it },
                            onStateChange = ::updateState,
                            onImportBooks = {
                                importFiles(chooseFiles())
                            },
                            onImportFolder = { chooseFolder()?.let(::importFolder) },
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            onClearSelection = { updateState(state.reduce(LibraryAction.SelectionCleared)) },
                            onRemoveSelected = ::removeSelectedBooks,
                            onShowBookInfo = {
                                bookInfoInitiallyEditing = false
                                bookInfoDialogFor = it
                            },
                            onEditBook = {
                                bookInfoInitiallyEditing = true
                                bookInfoDialogFor = it
                            },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onCreateSmartShelf = { showCreateSmartShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onRemoveFolder = { folderToRemove = it },
                            onTagSelectedBooks = { showTagSelectionDialog = true },
                            onAddSelectedBooksToShelf = { showAddToShelfDialog = true },
                            onSyncFolderMetadata = { syncFolderMetadata() },
                            onScanFolders = { scanSyncedFolders() },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.LibraryPinToggled(book.id))) }
                        )

                        SharedAppTab.SHELVES -> ShelvesScreen(
                            shelves = state.shelves,
                            onRead = ::openReader,
                            onSelect = { id -> updateState(state.reduce(LibraryAction.BookSelectionToggled(id))) },
                            selectedBookIds = state.selectedBookIds,
                            pinnedBookIds = state.pinnedLibraryBookIds,
                            onShowBookInfo = {
                                bookInfoInitiallyEditing = false
                                bookInfoDialogFor = it
                            },
                            onEditBook = {
                                bookInfoInitiallyEditing = true
                                bookInfoDialogFor = it
                            },
                            onTogglePinned = { book -> updateState(state.reduce(AppAction.LibraryPinToggled(book.id))) },
                            onCreateShelf = { showCreateShelfDialog = true },
                            onCreateSmartShelf = { showCreateSmartShelfDialog = true },
                            onRenameShelf = { shelfToRename = it },
                            onDeleteShelf = { shelfToDelete = it },
                            onRemoveFolder = { folderToRemove = it }
                        )

                        SharedAppTab.CATALOGS -> {
                            if (featurePolicy.opdsCatalogs) {
                                SharedOpdsScreen(
                                    state = opdsState,
                                    localLibraryBooks = state.rawLibraryBooks,
                                    onOpenCatalog = ::openOpdsCatalog,
                                    onOpenFeedUrl = ::openOpdsFeedUrl,
                                    onNavigateBack = ::navigateOpdsBack,
                                    onSearch = ::searchOpds,
                                    onLoadNextPage = ::loadNextOpdsPage,
                                    onAddCatalog = { title, url, username, password ->
                                        emitOpds(opdsController.addCatalog(title, url, username, password))
                                    },
                                    onUpdateCatalog = { id, title, url, username, password ->
                                        emitOpds(opdsController.updateCatalog(id, title, url, username, password))
                                    },
                                    onRemoveCatalog = ::removeOpdsCatalog,
                                    onDownloadBook = ::downloadOpdsBook,
                                    onReadBook = ::openReader,
                                    onStreamBook = ::streamOpdsBook,
                                    onClearError = { emitOpds(opdsController.clearError()) },
                                    coverContent = { entry, modifier ->
                                        DesktopOpdsCoverImage(
                                            entry = entry,
                                            catalog = opdsState.currentCatalog,
                                            modifier = modifier
                                        )
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize())
                            }
                        }

                        SharedAppTab.CUSTOM_FONTS -> SharedCustomFontsScreen(
                            fonts = customFonts,
                            appFontPreference = state.appFontPreference,
                            onAppFontPreferenceChange = { preference ->
                                updateState(state.reduce(AppAction.AppFontPreferenceChanged(preference)))
                            },
                            onImportFont = { importCustomFont(chooseFontFile()) },
                            onDeleteFont = ::deleteCustomFont,
                            googleFontsAvailable = featurePolicy.googleFontsDownload,
                            getGoogleFonts = { customFontStore.loadGoogleFontsList() },
                            onDownloadGoogleFont = ::downloadGoogleFont,
                            fontFamilyForPreview = { font -> font.toDesktopPreviewFontFamily() }
                        )

                        SharedAppTab.FEEDBACK -> SharedHelpFeedbackScreen(
                            onOpenGitHubIssues = { openExternalUrl(EpistemeIssuesUrl) },
                            onEmailSupport = {
                                val subject = desktopFeedbackSubject(desktopBuildProfile).urlEncode()
                                openExternalUrl("mailto:$EpistemeSupportEmail?subject=$subject")
                            }
                        )

                        SharedAppTab.SUPPORT -> SharedSupportProjectScreen(
                            onOpenGitHubSponsors = { openExternalUrl(EpistemeGitHubSponsorsUrl) },
                            onOpenPatreon = { openExternalUrl(EpistemePatreonUrl) }
                        )

                        SharedAppTab.ABOUT -> SharedAboutScreen(
                            versionName = desktopAppVersionName(),
                            buildLabel = desktopBuildProfile.buildLabel,
                            onOpenSource = if (featurePolicy.projectLinks) {
                                { openExternalUrl(EpistemeSourceUrl) }
                            } else {
                                null
                            },
                            onOpenIssues = if (featurePolicy.projectLinks) {
                                { openExternalUrl(EpistemeIssuesUrl) }
                            } else {
                                null
                            }
                        )

                        SharedAppTab.READER -> Box(Modifier.fillMaxSize())
                }
            }
            DesktopDropImportOverlay(dropImportState)
        }

        readerWindows.forEach { readerWindow ->
            key(readerWindow.id) {
                val windowState = rememberWindowState(
                    position = WindowPosition(Alignment.Center),
                    size = DpSize(1120.dp, 760.dp)
                )
                Window(
                    onCloseRequest = { closeReaderWindow(readerWindow.id) },
                    title = desktopString("desktop_label_pair_format", "%1\$s - %2\$s", readerWindow.title, readerWindowDefaults.title),
                    state = windowState,
                    icon = painterResource(readerWindowDefaults.iconResourcePath)
                ) {
                    val readerAwtWindow = this.window
                    DisposableEffect(readerAwtWindow, readerWindowDefaults.minimumSize) {
                        readerAwtWindow.minimumSize = readerWindowDefaults.minimumSize
                        onDispose {}
                    }
                    LaunchedEffect(readerWindow.focusRequestId) {
                        EventQueue.invokeLater {
                            val awtWindow = readerAwtWindow as? java.awt.Window ?: return@invokeLater
                            if (awtWindow is java.awt.Frame && awtWindow.extendedState and java.awt.Frame.ICONIFIED != 0) {
                                awtWindow.extendedState = awtWindow.extendedState and java.awt.Frame.ICONIFIED.inv()
                            }
                            awtWindow.toFront()
                            awtWindow.requestFocus()
                            awtWindow.requestFocusInWindow()
                        }
                    }
                    EpistemeDesktopWindowDecorationEffect(
                        window = readerAwtWindow,
                        hideDecoration = readerWindow.fullscreen && windowState.placement != WindowPlacement.Fullscreen
                    )
                    DesktopReaderFullscreenEffect(
                        window = readerAwtWindow,
                        enabled = readerWindow.fullscreen && windowState.placement != WindowPlacement.Fullscreen
                    )
                    SharedAppTheme(
                        appThemeMode = state.appThemeMode,
                        appContrastOption = state.appContrastOption,
                        appTextDimFactorLight = state.appTextDimFactorLight,
                        appTextDimFactorDark = state.appTextDimFactorDark,
                        appSeedColor = state.appSeedColor,
                        appFontFamily = desktopAppFontFamily
                    ) {
                        EpistemeDesktopWindowChromeEffect(
                            window = readerAwtWindow,
                            captionColor = MaterialTheme.colorScheme.surfaceVariant,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = MaterialTheme.colorScheme.background
                        )
                        SharedReaderModalOwnerWindowProvider(ownerWindow = readerAwtWindow) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                when (val content = readerWindow.content) {
                                DesktopReaderWindowContent.Opening -> {
                                    DesktopReaderOpeningScreen(opening = readerWindow.opening)
                                }

                                is DesktopReaderWindowContent.PasswordRequired -> {
                                    DesktopReaderOpeningScreen(opening = readerWindow.opening)
                                    DesktopPdfPasswordDialog(
                                        title = content.book.displayName,
                                        isError = content.attemptedPassword,
                                        onDismiss = { closeReaderWindow(readerWindow.id) },
                                        onConfirm = { enteredPassword ->
                                            openReader(
                                                book = content.book,
                                                password = enteredPassword,
                                                force = true,
                                                returnTabOverride = readerWindow.opening.returnTab
                                            )
                                        }
                                    )
                                }

                                is DesktopReaderWindowContent.Pdf -> {
                                    val activePdfBook = state.rawLibraryBooks.firstOrNull { it.id == content.book.id }
                                        ?: content.book
                                    val activePdfReflowBookId = desktopPdfReflowBookId(activePdfBook.id)
                                    val activePdfHasReflowFile = state.rawLibraryBooks.any { book ->
                                        book.id == activePdfReflowBookId &&
                                            book.path?.takeIf { it.isNotBlank() }?.let { File(it).isFile } == true
                                    }
                                    PdfReaderScreen(
                                        document = content.document,
                                        initialPageIndex = activePdfBook.lastPageIndex ?: 0,
                                        initialViewport = activePdfBook.pdfReaderViewport,
                                        initialReaderSettings = resolvedDesktopReaderSettings(
                                            activePdfBook,
                                            state.pdfReaderDefaultSettings
                                        ),
                                        onReturnToLibrary = null,
                                        onFullscreenChange = { enabled ->
                                            updateReaderWindow(readerWindow.id) { it.copy(fullscreen = enabled) }
                                        },
                                        onPageStateChange = { page, progress, viewport ->
                                            updateBookReadingState(
                                                bookId = content.book.id,
                                                pageIndex = page,
                                                progress = progress,
                                                pdfViewport = viewport
                                            )
                                        },
                                        onReaderSettingsChange = { settings ->
                                            updateBookReaderSettings(content.book.id, settings)
                                        },
                                        pdfHighlighterPalette = state.pdfHighlighterPalette,
                                        onPdfHighlighterPaletteChange = { palette ->
                                            updateState(state.reduce(AppAction.PdfHighlighterPaletteChanged(palette)))
                                        },
                                        customTextureIds = readerCustomTextureIds,
                                        onImportTexture = ::importDesktopReaderTexture,
                                        onLocalSidecarsChanged = {
                                            state.rawLibraryBooks.firstOrNull { it.id == content.book.id }?.let { book ->
                                                syncBookSidecars(book)
                                                queueCloudBookMetadataSync(book)
                                            }
                                        },
                                        aiByokSettings = effectiveAiSettings(),
                                        aiAdapter = desktopAiAdapter,
                                        ttsAdapter = desktopTtsAdapter,
                                        ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                                        onTtsReplacementPreferencesChange = { preferences ->
                                            updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                                        },
                                        summaryCacheStore = desktopSummaryCacheStore,
                                        credits = state.credits,
                                        showPaidCredits = !desktopBuildProfile.byokAiAvailable,
                                        onAiByokSettingsChange = ::updateAiByokSettings,
                                        featurePolicy = featurePolicy,
                                        onReaderAiEntitlementRequired = { feature, text ->
                                            desktopFeatureNoticeForReaderAi(feature, text)?.let { notice ->
                                                desktopFeatureNotice = notice
                                                true
                                            } ?: false
                                        },
                                        onCloudTtsEntitlementRequired = {
                                            desktopFeatureNoticeForCloudTts()?.let { notice ->
                                                desktopFeatureNotice = notice
                                                true
                                            } ?: false
                                        },
                                        onPaidFeatureError = { errorMessage ->
                                            desktopFeatureNoticeForError(errorMessage)?.let { desktopFeatureNotice = it }
                                        },
                                        hasReflowFile = activePdfHasReflowFile,
                                        isReflowingThisBook = activePdfBook.id in reflowingPdfBookIds,
                                        onReflowAction = if (content.document.formatLabel == "PDF") {
                                            { pageIndex -> requestPdfReflow(activePdfBook, content.document, pageIndex) }
                                        } else {
                                            null
                                        }
                                    )
                                }

                                is DesktopReaderWindowContent.Text -> {
                                    LaunchedEffect(
                                        readerWindow.id,
                                        content.session.reader.book.id,
                                        content.session.reader.currentPage?.chapterIndex
                                    ) {
                                        clearReaderHubSummary(readerWindow.id)
                                        clearReaderHubRecap(readerWindow.id)
                                    }
                                    DesktopReaderScreen(
                                        session = content.session,
                                        readerEngine = readerEngine,
                                        onSessionChange = { updated ->
                                            updateTextReaderWindow(readerWindow.id) { current ->
                                                current.copy(session = updated)
                                            }
                                            updateBookReadingState(
                                                bookId = content.book.id,
                                                pageIndex = updated.reader.currentPageIndex,
                                                progress = updated.reader.progress,
                                                session = updated
                                            )
                                        },
                                        onReturnToLibrary = null,
                                        onFullscreenChange = { enabled ->
                                            updateReaderWindow(readerWindow.id) { it.copy(fullscreen = enabled) }
                                        },
                                        toolbarPreferences = state.readerToolbarPreferences,
                                        onToolbarPreferencesChange = { preferences ->
                                            updateState(state.reduce(AppAction.ReaderToolbarPreferencesChanged(preferences)))
                                        },
                                        highlightPalette = state.readerHighlightPalette,
                                        onHighlightPaletteChange = { palette ->
                                            updateState(state.reduce(AppAction.ReaderHighlightPaletteChanged(palette)))
                                        },
                                        ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                                        ttsReplacementBookId = content.book.id.ifBlank { content.session.reader.book.title },
                                        onTtsReplacementPreferencesChange = { preferences ->
                                            updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                                        },
                                        onPickCustomFont = {
                                            importCustomFont(chooseFontFile())?.path
                                        },
                                        customFonts = customFonts,
                                        readerExtrasState = content.extrasState,
                                        aiByokSettings = effectiveAiSettings(),
                                        externalLookupAvailable = featurePolicy.externalLookup,
                                        cloudTtsControlsAvailable = featurePolicy.aiAndCloud,
                                        onExternalLookup = ::openReaderExternalLookup,
                                        onAiAction = { feature, text ->
                                            runReaderAiAction(readerWindow.id, feature, text)
                                        },
                                        onAiResultDismiss = {
                                            updateTextReaderWindow(readerWindow.id) { current ->
                                                current.copy(
                                                    dismissedReaderAiResultRequestId = current.readerAiResultRequestId,
                                                    extrasState = current.extrasState.copy(aiResult = ReaderAiResultState())
                                                )
                                            }
                                        },
                                        onCloudTtsToggle = { text -> toggleReaderCloudTts(readerWindow.id, text) },
                                        onCloudTtsStart = { readScope, chunks ->
                                            startReaderCloudTts(readerWindow.id, readScope, chunks)
                                        },
                                        onCloudTtsPauseResume = { pauseResumeReaderCloudTts(readerWindow.id) },
                                        onCloudTtsStop = { stopReaderCloudTts(readerWindow.id) },
                                        onCloudTtsClearCache = { clearReaderCloudTtsCache(readerWindow.id) },
                                        onOpenAiHub = {
                                            updateTextReaderWindow(readerWindow.id) { current ->
                                                current.copy(showAiHub = true)
                                            }
                                        },
                                        onAutoScrollChange = { autoScroll ->
                                            updateReaderAutoScroll(readerWindow.id, autoScroll)
                                        },
                                        onDownloadReaderImage = ::downloadReaderImage,
                                        readerTextureDataUri = DesktopReaderTextures::dataUriFor,
                                        readerCustomTextureIds = readerCustomTextureIds,
                                        onImportReaderTexture = ::importDesktopReaderTexture,
                                        bottomChromeExtraContent = {
                                            if (featurePolicy.aiAndCloud) {
                                                val settings = effectiveAiSettings()
                                                val ttsActive = content.extrasState.cloudTts.isLoading ||
                                                    content.extrasState.cloudTts.isPlaying ||
                                                    content.extrasState.cloudTts.isPaused
                                                if (content.showCloudTtsSettings) {
                                                    DesktopCloudTtsSettingsOverlay(
                                                        settings = settings,
                                                        isTtsActive = ttsActive,
                                                        showCredits = !desktopBuildProfile.byokAiAvailable,
                                                        credits = state.credits,
                                                        cacheSummary = content.extrasState.cloudTts.cacheSummary,
                                                        onClearCache = { clearReaderCloudTtsCache(readerWindow.id) },
                                                        onSettingsChange = { next ->
                                                            updateAiByokSettings(
                                                                aiByokSettings.sanitized().copy(
                                                                    ttsSpeakerId = next.sanitized().ttsSpeakerId
                                                                )
                                                            )
                                                        }
                                                    )
                                                }
                                                DesktopCloudTtsChromeControls(
                                                    settings = settings,
                                                    cloudTts = content.extrasState.cloudTts,
                                                    credits = state.credits,
                                                    showCredits = !desktopBuildProfile.byokAiAvailable,
                                                    onRead = {
                                                        startReaderCloudTts(
                                                            readerWindow.id,
                                                            ReaderTtsReadScope.BOOK,
                                                            ReaderTtsPlanner.chunksFromCurrentLocation(content.session)
                                                        )
                                                    },
                                                    onPauseResume = { pauseResumeReaderCloudTts(readerWindow.id) },
                                                    onStop = { stopReaderCloudTts(readerWindow.id) },
                                                    onOpenSettings = {
                                                        updateTextReaderWindow(readerWindow.id) { current ->
                                                            current.copy(showCloudTtsSettings = !current.showCloudTtsSettings)
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        webViewRuntimeState = webViewRuntimeState,
                                        webViewNetworkAccessEnabled = featurePolicy.networkAccess,
                                        epubPaginationCache = desktopEpubPaginationCache,
                                        epubPaginationCacheGeneration = epubPaginationCacheGeneration,
                                        useDetachedChromeLayer = true,
                                        useDetachedPanelLayer = true
                                    )

                                    if (content.showAiHub) {
                                        DesktopAiHubSheet(
                                            bookKey = readerHubBookKey(content),
                                            bookTitle = content.session.reader.book.title.ifBlank {
                                                desktopString("desktop_untitled", "Untitled")
                                            },
                                            itemIndex = readerHubChapterIndex(content),
                                            itemTitle = readerHubChapterTitle(content),
                                            summaryCacheStore = desktopSummaryCacheStore,
                                            summaryResult = content.summaryResult,
                                            isSummaryLoading = content.isSummaryLoading,
                                            recapResult = content.recapResult,
                                            isRecapLoading = content.isRecapLoading,
                                            recapProgressMessage = content.recapProgressMessage,
                                            onGenerateSummary = { force ->
                                                generateReaderHubSummary(readerWindow.id, force)
                                            },
                                            onClearSummary = { clearReaderHubSummary(readerWindow.id) },
                                            onGenerateRecap = { generateReaderHubRecap(readerWindow.id) },
                                            onClearRecap = { clearReaderHubRecap(readerWindow.id) },
                                            onDismiss = {
                                                updateTextReaderWindow(readerWindow.id) { current ->
                                                    current.copy(showAiHub = false)
                                                }
                                            },
                                            credits = state.credits,
                                            showCredits = !desktopBuildProfile.byokAiAvailable
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }

        desktopFeatureNotice?.let { notice ->
            AlertDialog(
                onDismissRequest = { desktopFeatureNotice = null },
                title = { Text(readerString(notice.titleKey, notice.titleFallback)) },
                text = { Text(readerString(notice.messageKey, notice.messageFallback)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            desktopFeatureNotice = null
                            when (notice.action) {
                                DesktopFeatureNoticeAction.SIGN_IN -> signInDesktopAccount()
                                DesktopFeatureNoticeAction.OPEN_PRO -> selectAppTab(SharedAppTab.PRO)
                                null -> Unit
                            }
                        }
                    ) {
                        Text(readerString(notice.confirmKey, notice.confirmFallback))
                    }
                },
                dismissButton = if (notice.action != null) {
                    {
                        TextButton(onClick = { desktopFeatureNotice = null }) {
                            Text(readerString("action_not_now", "Not now"))
                        }
                    }
                } else {
                    null
                }
            )
        }

        if (showAiByokSettingsDialog && desktopBuildProfile.byokAiAvailable) {
            DesktopAiByokSettingsDialog(
                settings = aiByokSettings,
                secureStorageAvailable = aiByokStore.isSecureStorageAvailable,
                onSettingsChange = ::updateAiByokSettings,
                onDismiss = { showAiByokSettingsDialog = false }
            )
        }

        if (showDesktopAppThemeSettingsDialog) {
            SharedAppThemeSettingsDialog(
                appThemeMode = state.appThemeMode,
                appContrastOption = state.appContrastOption,
                appTextDimFactorLight = state.appTextDimFactorLight,
                appTextDimFactorDark = state.appTextDimFactorDark,
                appSeedColor = state.appSeedColor,
                customAppThemes = state.customAppThemes,
                onThemeModeChanged = { mode -> updateState(state.reduce(AppAction.AppThemeChanged(mode))) },
                onContrastOptionChanged = { option -> updateState(state.reduce(AppAction.AppContrastChanged(option))) },
                onTextDimFactorLightChanged = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorLightChanged(factor))) },
                onTextDimFactorDarkChanged = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorDarkChanged(factor))) },
                onSeedColorChanged = { color -> updateState(state.reduce(AppAction.AppSeedColorChanged(color))) },
                onCustomThemeAdded = { theme -> updateState(state.reduce(AppAction.CustomAppThemeAdded(theme))) },
                onCustomThemeDeleted = { themeId -> updateState(state.reduce(AppAction.CustomAppThemeDeleted(themeId))) },
                onDismiss = { showDesktopAppThemeSettingsDialog = false }
            )
        }

        if (showDesktopLanguageDialog) {
            DesktopLanguageDialog(
                selectedLanguageTag = desktopLanguageTag,
                onLanguageSelected = { languageTag ->
                    desktopLanguageTag = languageTag
                    desktopLanguageSettingsStore.save(DesktopLanguageSettings(languageTag))
                },
                onDismiss = { showDesktopLanguageDialog = false }
            )
        }

        if (showClearBookCacheDialog) {
            SharedConfirmDialog(
                title = readerString("options_clear_book_cache", "Clear book cache"),
                body = readerString(
                    "desktop_clear_book_cache_desc",
                    "Delete generated desktop book and EPUB pagination cache files? They will be recreated the next time books are opened."
                ),
                confirmLabel = readerString("action_clear", "Clear"),
                onDismiss = { showClearBookCacheDialog = false },
                onConfirm = {
                    clearDesktopBookCache()
                    showClearBookCacheDialog = false
                }
            )
        }

        if (showCreateShelfDialog) {
            SharedTextInputDialog(
                title = readerString("create_new_shelf", "Create shelf"),
                label = readerString("shelf_name_hint", "Shelf name"),
                initialValue = "",
                confirmLabel = readerString("action_create", "Create"),
                onDismiss = { showCreateShelfDialog = false },
                onConfirm = { name ->
                    createShelf(name)
                    showCreateShelfDialog = false
                }
            )
        }

        if (showCreateSmartShelfDialog) {
            SmartShelfDialog(
                onDismiss = { showCreateSmartShelfDialog = false },
                onConfirm = { name, definition ->
                    createSmartShelf(name, definition)
                    showCreateSmartShelfDialog = false
                }
            )
        }

        shelfToRename?.let { shelf ->
            SharedTextInputDialog(
                title = readerString("dialog_rename_shelf", "Rename shelf"),
                label = readerString("shelf_name_hint", "Shelf name"),
                initialValue = shelf.name,
                confirmLabel = readerString("action_rename", "Rename"),
                onDismiss = { shelfToRename = null },
                onConfirm = { name ->
                    renameShelf(shelf, name)
                    shelfToRename = null
                }
            )
        }

        shelfToDelete?.let { shelf ->
            SharedConfirmDialog(
                title = readerString("menu_delete_shelf", "Delete shelf"),
                body = readerString("desktop_delete_shelf_desc", "Delete \"%1\$s\"? Books stay in your library.", shelf.name),
                confirmLabel = readerString("action_delete", "Delete"),
                onDismiss = { shelfToDelete = null },
                onConfirm = {
                    deleteShelf(shelf)
                    shelfToDelete = null
                }
            )
        }

        folderToRemove?.let { folder ->
            SharedConfirmDialog(
                title = readerString("menu_remove_folder", "Remove folder"),
                body = desktopQuantityString(
                    "desktop_remove_folder_desc_with_book_count",
                    folder.bookCount,
                    "Remove \"%1\$s\" and its %2\$d book from the app? Files on disk will not be deleted.",
                    "Remove \"%1\$s\" and its %2\$d books from the app? Files on disk will not be deleted.",
                    folder.name,
                    folder.bookCount
                ),
                confirmLabel = readerString("action_remove", "Remove"),
                onDismiss = { folderToRemove = null },
                onConfirm = {
                    removeFolder(folder)
                    folderToRemove = null
                }
            )
        }

        if (showAddToShelfDialog) {
            SharedAddToShelfDialog(
                shelves = state.shelves.filter { it.type == ShelfType.MANUAL && it.id != "unshelved" },
                onDismiss = { showAddToShelfDialog = false },
                onCreateShelf = {
                    showAddToShelfDialog = false
                    showCreateShelfDialog = true
                },
                onShelfSelected = { shelf ->
                    addSelectedBooksToShelf(shelf.id)
                    showAddToShelfDialog = false
                }
            )
        }

        if (showTagSelectionDialog) {
            SharedTextInputDialog(
                title = readerString("desktop_tag_selected_books", "Tag selected books"),
                label = readerString("desktop_tag_name", "Tag name"),
                initialValue = state.allTags.firstOrNull()?.name.orEmpty(),
                confirmLabel = readerString("action_apply", "Apply"),
                onDismiss = { showTagSelectionDialog = false },
                onConfirm = { name ->
                    tagSelectedBooks(name)
                    showTagSelectionDialog = false
                }
            )
        }

        bookInfoDialogFor?.let { book ->
            val canEditEmbeddedMetadata = book.type == FileType.EPUB &&
                book.path?.let { File(it).isFile && File(it).canWrite() } == true
            val canRenameDisplayName = book.type != FileType.EPUB
            SharedBookInfoDialog(
                book = book,
                knownTags = state.allTags,
                initiallyEditing = bookInfoInitiallyEditing && (canEditEmbeddedMetadata || canRenameDisplayName),
                canEditEmbeddedMetadata = canEditEmbeddedMetadata,
                canRenameDisplayName = canRenameDisplayName,
                canRestoreEmbeddedMetadata = canEditEmbeddedMetadata,
                onDismiss = {
                    bookInfoInitiallyEditing = false
                    bookInfoDialogFor = null
                },
                onSave = { updated ->
                    updateBookMetadata(updated)
                    bookInfoInitiallyEditing = false
                    bookInfoDialogFor = null
                },
                onRestore = { restored ->
                    updateBookMetadata(restored)
                    bookInfoInitiallyEditing = false
                    bookInfoDialogFor = null
                }
            )
        }
        }
    }
}

private fun desktopSignInRequiredNotice(
    messageKey: String,
    messageFallback: String
): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        titleKey = "sign_in_required",
        titleFallback = "Sign in required",
        messageKey = messageKey,
        messageFallback = messageFallback,
        confirmKey = "drawer_sign_in",
        confirmFallback = "Sign in",
        action = DesktopFeatureNoticeAction.SIGN_IN
    )
}

private fun desktopOutOfCreditsNotice(
    messageKey: String,
    messageFallback: String
): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        titleKey = "dialog_out_of_credits_title",
        titleFallback = "Out of credits",
        messageKey = messageKey,
        messageFallback = messageFallback,
        confirmKey = "desktop_view_pro_and_credits",
        confirmFallback = "View Pro and credits",
        action = DesktopFeatureNoticeAction.OPEN_PRO
    )
}

private fun desktopProRequiredNotice(
    messageKey: String,
    messageFallback: String
): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        titleKey = "desktop_pro_required",
        titleFallback = "Pro required",
        messageKey = messageKey,
        messageFallback = messageFallback,
        confirmKey = "desktop_view_pro_and_credits",
        confirmFallback = "View Pro and credits",
        action = DesktopFeatureNoticeAction.OPEN_PRO
    )
}

private fun desktopFeatureUnavailableNotice(
    messageKey: String,
    messageFallback: String
): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        titleKey = "desktop_feature_unavailable",
        titleFallback = "Feature unavailable",
        messageKey = messageKey,
        messageFallback = messageFallback
    )
}

private fun desktopFeatureNoticeForError(errorMessage: String?): DesktopFeatureNotice? {
    val message = errorMessage?.trim().orEmpty()
    if (message.isBlank()) return null
    return when {
        message.contains("INSUFFICIENT_CREDITS", ignoreCase = true) ||
            message.contains("Out of credits", ignoreCase = true) ||
            message.contains("HTTP 402", ignoreCase = true) ||
            message.contains("status code 402", ignoreCase = true) ||
            message.contains("SUMMARY_LIMIT", ignoreCase = true) ||
            (message.contains("free summar", ignoreCase = true) && message.contains("limit", ignoreCase = true)) ||
            message.contains("needs credits", ignoreCase = true) ||
            message.contains("This action needs credits", ignoreCase = true) ->
            desktopOutOfCreditsNotice(
                messageKey = "desktop_out_of_credits_generic_feature_desc",
                messageFallback = "Using this feature needs credits on desktop. Pro and credits can only be purchased from the Android app."
            )

        message.contains("Sign in", ignoreCase = true) ||
            message.contains("HTTP 401", ignoreCase = true) ||
            message.contains("status code 401", ignoreCase = true) ||
            message.contains("Authentication required", ignoreCase = true) ->
            desktopSignInRequiredNotice(
                messageKey = "desktop_sign_in_required_generic_feature_desc",
                messageFallback = "Sign in with Google to use this feature on desktop."
            )

        message.contains("requires Pro", ignoreCase = true) ||
            message.contains("REQUIRES_PRO", ignoreCase = true) ->
            desktopProRequiredNotice(
                messageKey = "desktop_pro_required_generic_feature_desc",
                messageFallback = "This feature requires Pro. Pro can only be purchased from the Android app, then desktop will use the upgraded account after sign-in."
            )

        else -> null
    }
}

private fun desktopReaderWordCount(text: String): Int {
    return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}

private fun ReaderImageReference.desktopImageBytes(): ByteArray {
    val trimmedSource = source.trim()
    if (trimmedSource.startsWith("data:", ignoreCase = true)) {
        val commaIndex = trimmedSource.indexOf(',')
        require(commaIndex > 0 && trimmedSource.substring(0, commaIndex).contains(";base64", ignoreCase = true)) {
            "This image data could not be decoded."
        }
        return Base64.getMimeDecoder().decode(trimmedSource.substring(commaIndex + 1))
    }

    val file = runCatching {
        if (trimmedSource.startsWith("file:", ignoreCase = true)) {
            File(URI(trimmedSource))
        } else {
            File(trimmedSource)
        }
    }.getOrElse {
        File(trimmedSource)
    }
    require(file.isFile) { "Could not find the source image file." }
    return file.readBytes()
}
