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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.WindowPlacement
import com.aryan.reader.shared.AppAction
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
import com.aryan.reader.shared.ui.SharedOpdsScreen
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.shared.ui.SharedSupportProjectScreen
import com.aryan.reader.shared.ui.SharedTextInputDialog
import com.aryan.reader.shared.withTtsReplacements
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
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
    val title: String,
    val message: String,
    val confirmLabel: String = "OK",
    val action: DesktopFeatureNoticeAction? = null
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
        val initialState = initialLibrarySnapshot.toDesktopReaderScreenState()
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
    var activeReaderBookId by remember { mutableStateOf<String?>(null) }
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
    var readerSession by remember { mutableStateOf(readerEngine.createSession(desktopEmptyReaderBook())) }
    LaunchedEffect(readerSession.reader.book.id, readerSession.reader.settings.readingMode) {
        if (
            readerSession.reader.book.chapters.isNotEmpty() &&
            readerSession.reader.settings.readingMode == ReaderReadingMode.VERTICAL
        ) {
            webViewRuntimeRequested = true
        }
    }
    var readerExtrasState by remember {
        mutableStateOf(
            ReaderExtrasState(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = effectiveAiSettings().isCloudTtsAvailable
                )
            )
        )
    }
    var activePdfDocument by remember { mutableStateOf<DesktopPdfDocument?>(null) }
    var openingReader by remember { mutableStateOf<DesktopReaderOpening?>(null) }
    var nextReaderOpenRequestId by remember { mutableStateOf(0L) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showCreateSmartShelfDialog by remember { mutableStateOf(false) }
    var shelfToRename by remember { mutableStateOf<Shelf?>(null) }
    var shelfToDelete by remember { mutableStateOf<Shelf?>(null) }
    var folderToRemove by remember { mutableStateOf<Shelf?>(null) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var showTagSelectionDialog by remember { mutableStateOf(false) }
    var showAiByokSettingsDialog by remember { mutableStateOf(false) }
    var showReaderAiHub by remember { mutableStateOf(false) }
    var readerHubSummaryResult by remember { mutableStateOf<SummarizationResult?>(null) }
    var readerHubRecapResult by remember { mutableStateOf<RecapResult?>(null) }
    var isReaderHubSummaryLoading by remember { mutableStateOf(false) }
    var isReaderHubRecapLoading by remember { mutableStateOf(false) }
    var readerHubRecapProgressMessage by remember { mutableStateOf<String?>(null) }
    var showReaderCloudTtsSettings by remember { mutableStateOf(false) }
    var showDesktopAppThemeSettingsDialog by remember { mutableStateOf(false) }
    var showClearBookCacheDialog by remember { mutableStateOf(false) }
    var desktopFeatureNotice by remember { mutableStateOf<DesktopFeatureNotice?>(null) }
    var settingsQuery by remember { mutableStateOf("") }
    var settingsDestination by remember { mutableStateOf(SharedSettingsDestination.ROOT) }
    var bookInfoDialogFor by remember { mutableStateOf<BookItem?>(null) }
    var bookInfoInitiallyEditing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var dropImportState by remember { mutableStateOf(DesktopDropImportState()) }
    var opdsState by remember { mutableStateOf(opdsController.state) }
    var readerTtsJob by remember { mutableStateOf<Job?>(null) }

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
        refs: List<BookShelfRef> = shelfRefs
    ) {
        shelfRecords = records
        shelfRefs = refs
        val projected = projectState(next, records, refs)
        state = projected
        persistSnapshot(projected, records, refs)
    }

    fun updateState(next: SharedReaderScreenState) {
        val projected = projectState(next)
        state = projected
        persistSnapshot(projected)
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

    suspend fun refreshDesktopAccountProfile(showBanner: Boolean = false) {
        if (!featurePolicy.aiAndCloud || desktopBuildProfile.byokAiAvailable) return
        val session = desktopAuthRepository.restoreSavedSession()
        if (session == null) {
            updateState(state.copy(currentUser = null, isProUser = false, credits = 0, isSyncEnabled = false))
            return
        }
        val token = desktopAuthRepository.freshIdToken()
        if (token.isNullOrBlank()) {
            updateState(state.copy(currentUser = null, isProUser = false, credits = 0, isSyncEnabled = false))
            return
        }
        runCatching {
            desktopAccountProfileRepository.fetchProfile(session.user.uid, token)
        }.onSuccess { profile ->
            updateState(
                state.copy(
                    currentUser = session.user,
                    isProUser = profile.isProUser,
                    credits = profile.credits
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

    fun updateAiByokSettings(next: ReaderAiByokSettings) {
        val sanitized = next.sanitized()
        if (!desktopBuildProfile.byokAiAvailable) {
            val desktopSettings = aiByokSettings.sanitized().copy(
                hideReaderAiFeatures = sanitized.hideReaderAiFeatures,
                ttsSpeakerId = sanitized.ttsSpeakerId
            )
            aiByokSettings = desktopSettings
            readerExtrasState = readerExtrasState.copy(
                cloudTts = readerExtrasState.cloudTts.copy(
                    isAvailable = effectiveAiSettings().isCloudTtsAvailable,
                    errorMessage = null,
                    cacheSummary = desktopTtsAdapter.cacheSummary(readerSession.reader.book.title, desktopSettings.ttsSpeakerId)
                )
            )
            runCatching { aiByokStore.save(desktopSettings) }
                .onFailure { error ->
                    logDesktopTts("settings_save_failed error=\"${error.desktopTtsSummary()}\"")
                    scope.launch {
                        snackbarHostState.showSnackbar(error.message ?: "AI settings could not be saved securely.")
                    }
                }
            return
        }
        logDesktopTts(
            "settings_update keyPresent=${sanitized.geminiKey.isNotBlank()} " +
                "ttsModel=\"${sanitized.ttsModel.desktopTtsPreview()}\" speaker=\"${sanitized.ttsSpeakerId.desktopTtsPreview()}\" " +
                "cloudAvailable=${sanitized.isCloudTtsAvailable}"
        )
        aiByokSettings = sanitized
        readerExtrasState = readerExtrasState.copy(
            cloudTts = readerExtrasState.cloudTts.copy(
                isAvailable = effectiveAiSettings().isCloudTtsAvailable,
                errorMessage = null,
                cacheSummary = desktopTtsAdapter.cacheSummary(readerSession.reader.book.title, sanitized.ttsSpeakerId)
            )
        )
        runCatching { aiByokStore.save(sanitized) }
            .onFailure { error ->
                logDesktopTts("settings_save_failed error=\"${error.desktopTtsSummary()}\"")
                scope.launch {
                    snackbarHostState.showSnackbar(error.message ?: "AI settings could not be saved securely.")
                }
            }
    }

    fun updateReaderAutoScroll(autoScroll: ReaderAutoScrollState) {
        readerExtrasState = readerExtrasState.copy(autoScroll = autoScroll.sanitized())
    }

    fun currentReaderTtsCacheSummary() =
        if (activeReaderBookId == null) {
            ReaderTtsCacheSummary()
        } else {
            desktopTtsAdapter.cacheSummary(readerSession.reader.book.title, aiByokSettings.sanitized().ttsSpeakerId)
        }

    fun readerCloudTtsStoppedState(statusMessage: String? = null, errorMessage: String? = null) = ReaderCloudTtsState(
        isAvailable = effectiveAiSettings().isCloudTtsAvailable,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        cacheSummary = currentReaderTtsCacheSummary()
    )

    fun cloudTtsUnavailableMessage(): String {
        return if (desktopBuildProfile.byokAiAvailable) {
            "Add a Gemini key and select Gemini cloud TTS in AI keys and models."
        } else if (state.currentUser == null) {
            "Sign in with Google to use cloud TTS."
        } else if (state.credits <= 0) {
            "Out of credits. Pro and credits can only be purchased from the Android app."
        } else {
            "Cloud TTS is not configured for this desktop build."
        }
    }

    fun desktopFeatureNoticeForReaderAi(feature: ReaderAiFeature, text: String): DesktopFeatureNotice? {
        if (desktopBuildProfile.byokAiAvailable) return null
        if (!featurePolicy.networkAccess || !desktopCloudConfig.isAiWorkerConfigured) {
            return desktopFeatureUnavailableNotice("Desktop AI is not configured for this build.")
        }
        if (effectiveAiSettings().hideReaderAiFeatures) {
            return desktopFeatureUnavailableNotice("Reader AI features are hidden.")
        }
        if (feature == ReaderAiFeature.DEFINE && desktopReaderWordCount(text) > 1 && state.currentUser == null) {
            return desktopSignInRequiredNotice("multi-word smart dictionary")
        }
        if (feature == ReaderAiFeature.DEFINE && desktopReaderWordCount(text) > 1 && !state.isProUser) {
            return desktopProRequiredNotice("Multi-word smart dictionary")
        }
        if (feature == ReaderAiFeature.SUMMARIZE && state.currentUser == null) {
            return desktopSignInRequiredNotice("summaries")
        }
        if (feature == ReaderAiFeature.SUMMARIZE && !state.isProUser && state.credits <= 0) {
            return desktopOutOfCreditsNotice("summaries")
        }
        if (feature == ReaderAiFeature.RECAP && state.currentUser == null) {
            return desktopSignInRequiredNotice("recaps")
        }
        if (feature == ReaderAiFeature.RECAP && state.credits <= 0) {
            return desktopOutOfCreditsNotice("recaps")
        }
        return null
    }

    fun desktopFeatureNoticeForCloudTts(): DesktopFeatureNotice? {
        if (desktopBuildProfile.byokAiAvailable) return null
        if (!featurePolicy.networkAccess || !desktopCloudConfig.isTtsWorkerConfigured) {
            return desktopFeatureUnavailableNotice("Cloud TTS is not configured for this desktop build.")
        }
        if (state.currentUser == null) return desktopSignInRequiredNotice("cloud TTS")
        if (state.credits <= 0) return desktopOutOfCreditsNotice("cloud TTS")
        return null
    }

    fun openReaderExternalLookup(action: ReaderExternalLookupAction, text: String) {
        if (!featurePolicy.externalLookup) return
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        openExternalUrl(externalLookupUrl(action, normalizedText.take(1800)))
    }

    fun readerHubBookKey(): String {
        return activeReaderBookId
            ?: readerSession.reader.book.id.ifBlank { readerSession.reader.book.title.ifBlank { "Untitled" } }
    }

    fun readerHubChapterIndex(): Int {
        return readerSession.reader.currentPage?.chapterIndex
            ?: readerSession.reader.currentPageIndex
    }

    fun readerHubChapterTitle(index: Int = readerHubChapterIndex()): String {
        return readerSession.reader.book.chapters.getOrNull(index)?.title?.takeIf { it.isNotBlank() }
            ?: readerSession.reader.currentPage?.chapterTitle?.takeIf { it.isNotBlank() }
            ?: "Chapter ${index + 1}"
    }

    fun readerHubChapterText(index: Int = readerHubChapterIndex()): String {
        return readerSession.reader.book.chapters.getOrNull(index)?.plainText?.trim().orEmpty()
    }

    fun readerHubCurrentChapterText(): String {
        return ReaderContextExtractor.currentChapterText(readerSession).trim()
            .ifBlank { readerHubChapterText() }
            .ifBlank { readerSession.reader.currentPage?.text?.trim().orEmpty() }
    }

    fun readerHubCurrentTextForRecap(): String {
        val chapterText = readerHubChapterText()
        val endOffset = readerSession.reader.currentPage?.endOffset ?: chapterText.length
        return if (chapterText.isNotBlank()) {
            chapterText.take(endOffset.coerceIn(0, chapterText.length)).trim()
                .ifBlank { chapterText.take(500).trim() }
        } else {
            ReaderContextExtractor.textBeforeCurrentLocation(readerSession).trim().takeLast(24_000)
        }
    }

    fun clearReaderHubSummary() {
        readerHubSummaryResult = null
        isReaderHubSummaryLoading = false
    }

    fun clearReaderHubRecap() {
        readerHubRecapResult = null
        isReaderHubRecapLoading = false
        readerHubRecapProgressMessage = null
    }

    fun generateReaderHubSummary(force: Boolean) {
        val text = readerHubCurrentChapterText()
        val chapterIndex = readerHubChapterIndex()
        val chapterTitle = readerHubChapterTitle(chapterIndex)
        val bookKey = readerHubBookKey()
        if (text.isBlank()) {
            readerHubSummaryResult = SummarizationResult(error = "There is no text to summarize.")
            return
        }
        if (!force) {
            desktopSummaryCacheStore.getSummary(bookKey, chapterIndex)?.let { cached ->
                readerHubSummaryResult = SummarizationResult(summary = cached, isCacheHit = true)
                return
            }
        }
        desktopFeatureNoticeForReaderAi(ReaderAiFeature.SUMMARIZE, text)?.let { notice ->
            desktopFeatureNotice = notice
            return
        }
        isReaderHubSummaryLoading = true
        readerHubSummaryResult = null
        scope.launch {
            val result = desktopAiAdapter.summarize(text)
            result.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                desktopSummaryCacheStore.saveSummary(bookKey, chapterIndex, chapterTitle, summary)
            }
            readerHubSummaryResult = result
            isReaderHubSummaryLoading = false
            desktopFeatureNoticeForError(result.error)?.let { desktopFeatureNotice = it }
        }
    }

    fun generateReaderHubRecap() {
        val currentText = readerHubCurrentTextForRecap()
        if (currentText.isBlank()) {
            readerHubRecapResult = RecapResult(error = "There is no reading context for a recap.")
            return
        }
        desktopFeatureNoticeForReaderAi(ReaderAiFeature.RECAP, currentText)?.let { notice ->
            desktopFeatureNotice = notice
            return
        }
        val book = readerSession.reader.book
        val bookKey = readerHubBookKey()
        val currentChapterIndex = readerHubChapterIndex().coerceIn(0, book.chapters.size.coerceAtLeast(1) - 1)
        isReaderHubRecapLoading = true
        readerHubRecapResult = null
        readerHubRecapProgressMessage = "Checking past chapters..."
        scope.launch {
            val pastSummaries = mutableListOf<String>()
            for (chapterIndex in 0 until currentChapterIndex) {
                readerHubRecapProgressMessage = "Analyzing Chapter ${chapterIndex + 1}..."
                val cached = desktopSummaryCacheStore.getSummary(bookKey, chapterIndex)
                if (!cached.isNullOrBlank()) {
                    pastSummaries += cached
                    continue
                }
                val chapterText = readerHubChapterText(chapterIndex)
                if (chapterText.length <= 100) continue
                val summary = desktopAiAdapter.summarize(chapterText)
                summary.summary?.takeIf { it.isNotBlank() }?.let { generated ->
                    val title = readerHubChapterTitle(chapterIndex)
                    desktopSummaryCacheStore.saveSummary(bookKey, chapterIndex, title, generated)
                    pastSummaries += generated
                }
                if (summary.error != null) {
                    desktopFeatureNoticeForError(summary.error)?.let { desktopFeatureNotice = it }
                }
                delay(500)
            }

            readerHubRecapProgressMessage = "Generating recap..."
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
            readerHubRecapResult = recap
            isReaderHubRecapLoading = false
            readerHubRecapProgressMessage = null
            desktopFeatureNoticeForError(recap.error)?.let { desktopFeatureNotice = it }
        }
    }

    fun runReaderAiAction(feature: ReaderAiFeature, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        if (!effectiveAiSettings().areReaderAiFeaturesAvailable) return
        desktopFeatureNoticeForReaderAi(feature, normalizedText)?.let { notice ->
            desktopFeatureNotice = notice
            return
        }
        readerExtrasState = readerExtrasState.copy(
            aiResult = ReaderAiResultState(
                title = feature.displayName,
                isLoading = true
            )
        )
        scope.launch {
            val result = when (feature) {
                ReaderAiFeature.DEFINE -> desktopAiAdapter.define(
                    text = normalizedText.take(2400),
                    context = ReaderContextExtractor.currentPageText(readerSession)
                ).let { it.definition to it.error }
                ReaderAiFeature.SUMMARIZE -> {
                    val summary = desktopAiAdapter.summarize(normalizedText)
                    summary.summary?.takeIf { it.isNotBlank() }?.let { generated ->
                        desktopSummaryCacheStore.saveSummary(
                            readerHubBookKey(),
                            readerHubChapterIndex(),
                            readerHubChapterTitle(),
                            generated
                        )
                    }
                    readerHubSummaryResult = summary
                    summary.summary to summary.error
                }
                ReaderAiFeature.RECAP -> {
                    val recap = desktopAiAdapter.recap(normalizedText)
                    readerHubRecapResult = recap
                    recap.recap to recap.error
                }
            }
            readerExtrasState = readerExtrasState.copy(
                aiResult = ReaderAiResultState(
                    title = feature.displayName,
                    text = result.first.orEmpty(),
                    errorMessage = result.second,
                    isLoading = false
                )
            )
            desktopFeatureNoticeForError(result.second)?.let { desktopFeatureNotice = it }
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

    fun updateActiveBookReadingState(
        pageIndex: Int,
        progress: Float,
        session: ReaderSessionState? = null,
        pdfViewport: SharedPdfReaderViewport? = null
    ) {
        activeReaderBookId?.let { bookId ->
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
        }
    }

    fun updateActiveBookReaderSettings(settings: ReaderSettings) {
        activeReaderBookId?.let { bookId ->
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
        }
    }

    fun importDesktopReaderTexture(settings: ReaderSettings): ReaderSettings? {
        val source = chooseReaderTextureFile() ?: return null
        val textureId = DesktopReaderTextures.importTexture(source) ?: return null
        readerCustomTextureIds = DesktopReaderTextures.importedTextureIds()
        return settings.copy(textureId = textureId)
    }

    fun stopReaderCloudTts() {
        logDesktopTts("reader_stop_requested")
        readerTtsJob?.cancel()
        readerTtsJob = null
        scope.launch {
            desktopTtsAdapter.stop()
            readerExtrasState = readerExtrasState.copy(
                cloudTts = readerCloudTtsStoppedState(statusMessage = "Stopped")
            )
        }
    }

    fun signOutDesktopAccount() {
        desktopAuthRepository.signOut()
        stopReaderCloudTts()
        updateState(state.copy(currentUser = null, isProUser = false, credits = 0, isSyncEnabled = false))
        accountStatusMessage = "Signed out."
    }

    fun pauseResumeReaderCloudTts() {
        val current = readerExtrasState.cloudTts
        if (current.isPaused) {
            scope.launch {
                desktopTtsAdapter.resume()
                readerExtrasState = readerExtrasState.copy(
                    cloudTts = readerExtrasState.cloudTts.copy(
                        isPaused = false,
                        isPlaying = true,
                        statusMessage = readerExtrasState.cloudTts.progress.currentPositionLabel ?: "Reading"
                    )
                )
            }
        } else if (current.isPlaying) {
            scope.launch {
                desktopTtsAdapter.pause()
                readerExtrasState = readerExtrasState.copy(
                    cloudTts = readerExtrasState.cloudTts.copy(
                        isPlaying = false,
                        isPaused = true,
                        statusMessage = "Paused"
                    )
                )
            }
        }
    }

    fun clearReaderCloudTtsCache() {
        desktopTtsAdapter.clearBookCacheForSpeaker(readerSession.reader.book.title, aiByokSettings.sanitized().ttsSpeakerId)
        readerExtrasState = readerExtrasState.copy(
            cloudTts = readerExtrasState.cloudTts.copy(
                statusMessage = "Voice cache cleared",
                cacheSummary = currentReaderTtsCacheSummary()
            )
        )
    }

    fun startReaderCloudTts(readScope: ReaderTtsReadScope, chunks: List<ReaderTtsChunk>) {
        val replacementBookId = activeReaderBookId ?: readerSession.reader.book.title
        val ttsChunks = chunks
            .filter { it.text.isNotBlank() }
            .withTtsReplacements(state.readerTtsReplacementPreferences, replacementBookId)
        val settings = aiByokSettings.sanitized()
        logDesktopTts(
            "reader_sequence_toggle scope=${readScope.name} chunks=${ttsChunks.size} " +
                "isPlaying=${readerExtrasState.cloudTts.isPlaying} isLoading=${readerExtrasState.cloudTts.isLoading} " +
                "keyPresent=${settings.geminiKey.isNotBlank()} ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" " +
                "available=${desktopTtsAdapter.isAvailable}"
        )
        if (readerExtrasState.cloudTts.isPlaying || readerExtrasState.cloudTts.isLoading || readerExtrasState.cloudTts.isPaused) {
            stopReaderCloudTts()
            return
        }
        if (ttsChunks.isEmpty()) {
            logDesktopTts("reader_sequence_ignored reason=blank_text scope=${readScope.name}")
            readerExtrasState = readerExtrasState.copy(
                cloudTts = readerExtrasState.cloudTts.copy(
                    errorMessage = "There is no text here to read.",
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        if (!desktopTtsAdapter.isAvailable) {
            logDesktopTts("reader_sequence_blocked reason=adapter_unavailable")
            desktopFeatureNoticeForCloudTts()?.let { desktopFeatureNotice = it }
            readerExtrasState = readerExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = cloudTtsUnavailableMessage(),
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        val ttsSessionId = System.currentTimeMillis()
        val initialProgress = ReaderTtsProgress(
            sessionId = ttsSessionId,
            scope = readScope,
            chunks = ttsChunks,
            currentChunkIndex = -1
        )
        readerExtrasState = readerExtrasState.copy(
            cloudTts = ReaderCloudTtsState(
                isAvailable = true,
                isLoading = true,
                statusMessage = "Preparing ${readScope.label.lowercase()}",
                progress = initialProgress,
                cacheSummary = currentReaderTtsCacheSummary()
            )
        )
        readerTtsJob = scope.launch {
            runCatching {
                logDesktopTts("reader_sequence_start scope=${readScope.name} chunks=${ttsChunks.size}")
                desktopTtsAdapter.speakChunks(readerSession.reader.book.title, readScope, ttsChunks) { index ->
                    if (!isActive) throw kotlinx.coroutines.CancellationException("Reader cloud TTS stopped")
                    val chunk = ttsChunks[index]
                    val progress = initialProgress.copy(currentChunkIndex = index)
                    if (readerSession.reader.currentPageIndex != chunk.pageIndex) {
                        val updatedSession = readerEngine.goToPage(readerSession, chunk.pageIndex)
                        readerSession = updatedSession
                        updateActiveBookReadingState(
                            pageIndex = updatedSession.reader.currentPageIndex,
                            progress = updatedSession.reader.progress,
                            session = updatedSession
                        )
                    }
                    readerExtrasState = readerExtrasState.copy(
                        cloudTts = ReaderCloudTtsState(
                            isAvailable = true,
                            isPlaying = true,
                            statusMessage = progress.currentPositionLabel ?: "Reading",
                            progress = progress,
                            cacheSummary = currentReaderTtsCacheSummary()
                        )
                    )
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
                readerExtrasState = if (error is kotlinx.coroutines.CancellationException) {
                    readerExtrasState.copy(
                        cloudTts = readerCloudTtsStoppedState(statusMessage = "Stopped")
                    )
                } else {
                    desktopFeatureNoticeForError(error.message)?.let { desktopFeatureNotice = it }
                    readerExtrasState.copy(
                        cloudTts = readerCloudTtsStoppedState(errorMessage = error.message ?: "Cloud TTS failed.")
                    )
                }
            }.onSuccess {
                logDesktopTts("reader_sequence_success chunks=${ttsChunks.size}")
                readerExtrasState = readerExtrasState.copy(
                    cloudTts = readerCloudTtsStoppedState(statusMessage = "Finished")
                )
            }
        }
    }

    fun toggleReaderCloudTts(text: String) {
        val normalizedText = text.trim()
        val settings = aiByokSettings.sanitized()
        logDesktopTts(
            "reader_toggle textChars=${normalizedText.length} isPlaying=${readerExtrasState.cloudTts.isPlaying} " +
                "isLoading=${readerExtrasState.cloudTts.isLoading} keyPresent=${settings.geminiKey.isNotBlank()} " +
                "ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" available=${desktopTtsAdapter.isAvailable}"
        )
        if (readerExtrasState.cloudTts.isPlaying || readerExtrasState.cloudTts.isLoading || readerExtrasState.cloudTts.isPaused) {
            stopReaderCloudTts()
            return
        }
        if (normalizedText.isBlank()) {
            logDesktopTts("reader_toggle_ignored reason=blank_text")
            readerExtrasState = readerExtrasState.copy(
                cloudTts = readerExtrasState.cloudTts.copy(
                    errorMessage = "There is no text on this page to read.",
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        if (!desktopTtsAdapter.isAvailable) {
            logDesktopTts("reader_toggle_blocked reason=adapter_unavailable")
            desktopFeatureNoticeForCloudTts()?.let { desktopFeatureNotice = it }
            readerExtrasState = readerExtrasState.copy(
                cloudTts = ReaderCloudTtsState(
                    isAvailable = false,
                    errorMessage = cloudTtsUnavailableMessage(),
                    cacheSummary = currentReaderTtsCacheSummary()
                )
            )
            return
        }
        val page = readerSession.reader.currentPage
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
                pageIndex = readerSession.reader.currentPageIndex,
                chapterIndex = 0,
                chapterTitle = "Selection"
            )
        }
        startReaderCloudTts(ReaderTtsReadScope.PAGE, selectionChunks)
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
            updateState(state.withBanner("Could not import $failedCount file(s).", isError = true))
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
                        it.withBanner("Imported ${counts.addedCount} file(s). Skipped $skippedCount file(s).")
                    }
                    counts.addedCount > 0 -> it.withBanner("Imported ${counts.addedCount} file(s).")
                    counts.duplicateCount > 0 -> it.withBanner("Those files are already in the library.")
                    counts.failedCount > 0 -> it.withBanner("Could not import ${counts.failedCount} file(s).", isError = true)
                    else -> it
                }
            }
        updateState(next)
        onImported(importPlan.importedBooks)
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
            }
        }
    }

    fun importFiles(files: List<ImportedBookFile>, onImported: (List<BookItem>) -> Unit = {}) {
        if (files.isEmpty()) return
        updateState(state.withBanner("Importing ${files.size} file(s)..."))
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
                    "Folder sync failed for $failedCount folder(s)."
                failedCount > 0 ->
                    "Folder sync finished with $failedCount folder(s) skipped."
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
            activeReaderBookId = activeReaderBookId?.let { result.idMigrations[it] ?: it }
            replaceLibrary(
                completedState,
                refs = result.shelfRefs
            )
            if (activeReaderBookId != null && completedState.rawLibraryBooks.none { it.id == activeReaderBookId }) {
                openingReader = null
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                readerSession = readerEngine.createSession(desktopEmptyReaderBook())
                selectedTab = SharedAppTab.HOME
            }
        }
    }

    fun syncFolderMetadata(showBanner: Boolean = true) {
        syncLocalFolders(showBanner = showBanner, metadataOnly = true)
    }

    fun scanSyncedFolders(showBanner: Boolean = true) {
        syncLocalFolders(showBanner = showBanner, metadataOnly = false)
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
        val clearedSettings = state.rawLibraryBooks.map { book ->
            val settings = book.readerSettings
            if (settings?.customFontPath == font.path) {
                book.copy(readerSettings = settings.copy(fontFamily = "Default", customFontPath = null))
            } else {
                book
            }
        }
        if (readerSession.reader.settings.customFontPath == font.path) {
            readerSession = readerEngine.updateSettings(
                readerSession,
                readerSession.reader.settings.copy(fontFamily = "Default", customFontPath = null)
            )
        }
        updateState(state.copy(rawLibraryBooks = clearedSettings).withBanner("Deleted ${font.displayName}."))
    }

    fun removeSelectedBooks() {
        SharedLibraryEditor.removeSelectedBooks(state, shelfRecords, shelfRefs)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun createShelf(name: String) {
        SharedLibraryEditor.createShelf(state, shelfRecords, shelfRefs, name, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun createSmartShelf(name: String, definition: SmartCollectionDefinition) {
        SharedLibraryEditor.createSmartShelf(state, shelfRecords, shelfRefs, name, definition, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun renameShelf(shelf: Shelf, name: String) {
        SharedLibraryEditor.renameShelf(state, shelfRecords, shelfRefs, shelf, name)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
        }
    }

    fun deleteShelf(shelf: Shelf) {
        val result = SharedLibraryEditor.deleteShelf(state, shelfRecords, shelfRefs, shelf)
        replaceLibrary(result.state, records = result.shelfRecords, refs = result.shelfRefs)
    }

    fun addSelectedBooksToShelf(shelfId: String) {
        SharedLibraryEditor.addSelectedBooksToShelf(state, shelfRecords, shelfRefs, shelfId, System.currentTimeMillis())?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
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
        result.state.rawLibraryBooks.firstOrNull { it.id == updated.id }?.let(::syncBookSidecars)
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
        openedState.rawLibraryBooks.firstOrNull { it.id == bookId }?.let(::syncBookSidecars)
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
        }
    }

    fun schedulePdfEmbeddedAnnotationsLoad(document: DesktopPdfDocument) {
        scope.launch {
            delay(650L)
            if (activePdfDocument?.handleId != document.handleId) return@launch
            val annotations = withContext(Dispatchers.IO) {
                DesktopPdfium.loadEmbeddedAnnotations(document)
            }
            if (activePdfDocument?.handleId == document.handleId) {
                document.replaceEmbeddedAnnotations(annotations)
            }
        }
    }

    fun exitReaderTo(tab: SharedAppTab) {
        val wasPdfReaderVisible = selectedTab == SharedAppTab.READER &&
            openingReader == null &&
            activePdfDocument != null
        val detachedPdfDocument = activePdfDocument
        openingReader = null
        activePdfDocument = null
        selectedTab = tab
        if (!wasPdfReaderVisible) {
            detachedPdfDocument?.close()
        }
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
        if (nextTab != SharedAppTab.READER && (selectedTab == SharedAppTab.READER || activePdfDocument != null)) {
            exitReaderTo(nextTab)
        } else {
            selectedTab = nextTab
        }
    }

    fun applyReaderOpenResult(result: DesktopReaderOpenResult) {
        if (openingReader?.requestId != result.opening.requestId) {
            if (result is DesktopReaderOpenResult.Pdf && activePdfDocument?.handleId != result.document.handleId) {
                result.document.close()
            }
            return
        }

        openingReader = null
        when (result) {
            is DesktopReaderOpenResult.Failure -> {
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                selectedTab = result.opening.returnTab
                updateState(state.withBanner(result.message, isError = true))
            }

            is DesktopReaderOpenResult.Pdf -> {
                activePdfDocument?.takeIf { it.handleId != result.document.handleId }?.close()
                activePdfDocument = result.document
                activeReaderBookId = result.book.id
                recordBookOpened(result.book.id)
                selectedTab = SharedAppTab.READER
                if (result.book.type == FileType.PDF) {
                    schedulePdfEmbeddedAnnotationsLoad(result.document)
                }
            }

            is DesktopReaderOpenResult.Text -> {
                activePdfDocument?.close()
                activePdfDocument = null
                readerSession = result.session
                activeReaderBookId = result.book.id
                recordBookOpened(result.book.id)
                selectedTab = SharedAppTab.READER
            }
        }
    }

    fun openReader(book: BookItem) {
        val desktopReaderSurface = SharedFileCapabilities.surfaceFor(book.type, ReaderPlatform.DESKTOP)
        if (openingReader?.bookId == book.id) return
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
            val readerPath = streamReference?.let { path } ?: File(path).absolutePath
            if (activePdfDocument?.path == readerPath) {
                openingReader = null
                activeReaderBookId = book.id
                recordBookOpened(book.id)
                selectedTab = SharedAppTab.READER
                return
            }
        } else if (
            desktopReaderSurface == ReaderFeatureSurface.EPUB_READER ||
            desktopReaderSurface == ReaderFeatureSurface.TEXT_READER
        ) {
            if (activePdfDocument == null && activeReaderBookId == book.id) {
                openingReader = null
                recordBookOpened(book.id)
                selectedTab = SharedAppTab.READER
                return
            }
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
            returnTab = selectedTab.takeUnless { it == SharedAppTab.READER } ?: SharedAppTab.LIBRARY
        )
        val readerDefaultSettings = state.readerDefaultSettings
        openingReader = opening
        selectedTab = SharedAppTab.READER

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
                                    FileType.PDF -> DesktopPdfium.load(readerFile, loadEmbeddedAnnotations = false)
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
                    DesktopReaderOpenResult.Failure(
                        opening = opening,
                        book = book,
                        message = "Could not open ${SharedFileCapabilities.displayNameFor(book.type)}: " +
                            (error.message ?: "unknown error")
                    )
                }
            }
            applyReaderOpenResult(result)
        }
    }

    fun removeFolder(shelf: Shelf) {
        val removedBookIds = shelf.books.mapTo(mutableSetOf()) { it.id }
        val wasReadingRemovedBook = activeReaderBookId in removedBookIds
        val nextTabBook = state.openTabIds
            .filterNot { it in removedBookIds }
            .lastOrNull()
            ?.let { nextId -> state.rawLibraryBooks.firstOrNull { it.id == nextId } }
        SharedLibraryEditor.removeFolder(state, shelfRecords, shelfRefs, shelf)?.let {
            replaceLibrary(it.state, records = it.shelfRecords, refs = it.shelfRefs)
            if (wasReadingRemovedBook) {
                openingReader = null
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                if (nextTabBook != null) {
                    openReader(nextTabBook)
                } else {
                    readerSession = readerEngine.createSession(desktopEmptyReaderBook())
                    selectedTab = SharedAppTab.HOME
                }
            }
        }
    }

    fun closeReaderTab(book: BookItem) {
        val wasActive = activeReaderBookId == book.id
        if (openingReader?.bookId == book.id) {
            openingReader = null
        }
        val remainingIds = state.openTabIds.filterNot { it == book.id }
        updateState(state.reduce(AppAction.BookTabClosed(book.id)))
        if (!wasActive) return

        openingReader = null
        activePdfDocument?.close()
        activePdfDocument = null
        activeReaderBookId = null
        val nextBook = remainingIds.lastOrNull()?.let { nextId ->
            state.rawLibraryBooks.firstOrNull { it.id == nextId }
        }
        if (nextBook != null) {
            openReader(nextBook)
        } else {
            readerSession = readerEngine.createSession(desktopEmptyReaderBook())
            selectedTab = SharedAppTab.HOME
        }
    }

    fun closeAllReaderTabs() {
        openingReader = null
        activePdfDocument?.close()
        activePdfDocument = null
        activeReaderBookId = null
        readerSession = readerEngine.createSession(desktopEmptyReaderBook())
        selectedTab = SharedAppTab.HOME
        updateState(state.reduce(AppAction.AllTabsClosed))
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
            if (activeReaderBookId in streamBookIds) {
                activePdfDocument?.close()
                activePdfDocument = null
                activeReaderBookId = null
                readerSession = readerEngine.createSession(desktopEmptyReaderBook())
                selectedTab = SharedAppTab.HOME
            }
            updateState(
                state.copy(
                    rawLibraryBooks = state.rawLibraryBooks.filterNot { it.id in streamBookIds },
                    openTabIds = state.openTabIds.filterNot { it in streamBookIds },
                    activeTabBookId = state.activeTabBookId?.takeUnless { it in streamBookIds }
                ).withBanner("Removed ${streamBookIds.size} streamed OPDS book(s) from that catalog.")
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

    DisposableEffect(Unit) {
        onDispose {
            activePdfDocument?.close()
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

    LaunchedEffect(Unit) {
        if (state.syncedFolders.isNotEmpty()) {
            scanSyncedFolders(showBanner = false)
        }
    }

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let { banner ->
            snackbarHostState.showSnackbar(banner.message)
            updateState(state.reduce(AppAction.BannerDismissed))
        }
    }

    LaunchedEffect(activeReaderBookId, readerSession.reader.currentPage?.chapterIndex) {
        clearReaderHubSummary()
        clearReaderHubRecap()
    }

    LaunchedEffect(activePdfDocument, selectedTab) {
        if (activePdfDocument != null || selectedTab != SharedAppTab.READER) {
            showReaderAiHub = false
        }
    }

    LaunchedEffect(aiByokSettings, state.currentUser, state.credits, activeReaderBookId, readerSession.reader.book.title) {
        readerExtrasState = readerExtrasState.copy(
            cloudTts = readerExtrasState.cloudTts.copy(
                isAvailable = effectiveAiSettings().isCloudTtsAvailable,
                errorMessage = null,
                cacheSummary = currentReaderTtsCacheSummary()
            )
        )
    }

    SharedAppTheme(
        appThemeMode = state.appThemeMode,
        appContrastOption = state.appContrastOption,
        appTextDimFactorLight = state.appTextDimFactorLight,
        appTextDimFactorDark = state.appTextDimFactorDark,
        appSeedColor = state.appSeedColor
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
                onSyncRequested = {
                    scanSyncedFolders()
                },
                onFolderMetadataSyncRequested = { syncFolderMetadata() },
                onAppThemeModeChange = { mode -> updateState(state.reduce(AppAction.AppThemeChanged(mode))) },
                onAppContrastOptionChange = { option -> updateState(state.reduce(AppAction.AppContrastChanged(option))) },
                onAppTextDimFactorLightChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorLightChanged(factor))) },
                onAppTextDimFactorDarkChange = { factor -> updateState(state.reduce(AppAction.AppTextDimFactorDarkChanged(factor))) },
                onAppSeedColorChange = { color -> updateState(state.reduce(AppAction.AppSeedColorChanged(color))) },
                onCustomAppThemeAdded = { theme -> updateState(state.reduce(AppAction.CustomAppThemeAdded(theme))) },
                onCustomAppThemeDeleted = { themeId -> updateState(state.reduce(AppAction.CustomAppThemeDeleted(themeId))) },
                onTabsEnabledChange = { enabled ->
                    if (!enabled && (selectedTab == SharedAppTab.READER || activePdfDocument != null || openingReader != null)) {
                        exitReaderTo(SharedAppTab.HOME)
                        activeReaderBookId = null
                    }
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
                                    syncAvailable = false,
                                    folderSyncAvailable = true,
                                    aiSettingsAvailable = desktopBuildProfile.byokAiAvailable,
                                    includeLanguage = false,
                                    includeScreenCaptureProtection = false,
                                    includeExternalFileBehavior = false,
                                    includeStrictFileFilter = false,
                                    includeReaderTabs = false,
                                    includeHideReaderAi = featurePolicy.aiAndCloud,
                                    isTabsEnabled = state.isTabsEnabled,
                                    isFolderSyncEnabled = state.isFolderSyncEnabled,
                                    hideReaderAi = effectiveAiSettings().hideReaderAiFeatures
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
                                        if (
                                            state.isTabsEnabled &&
                                            (selectedTab == SharedAppTab.READER || activePdfDocument != null || openingReader != null)
                                        ) {
                                            exitReaderTo(SharedAppTab.HOME)
                                            activeReaderBookId = null
                                        }
                                        updateState(state.reduce(AppAction.TabsEnabledChanged(!state.isTabsEnabled)))
                                    }
                                    SharedSettingsAction.FOLDER_SYNC -> updateState(state.reduce(AppAction.FolderSyncEnabledChanged(!state.isFolderSyncEnabled)))
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
                                    SharedSettingsAction.CLOUD_SYNC,
                                    SharedSettingsAction.LANGUAGE,
                                    SharedSettingsAction.RECENT_LIMIT,
                                    SharedSettingsAction.STRICT_FILE_FILTER,
                                    SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
                                    SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                                    SharedSettingsAction.TTS_SETTINGS,
                                    SharedSettingsAction.PDF_READER_DEFAULTS,
                                    SharedSettingsAction.TEXT_READER_DEFAULTS,
                                    SharedSettingsAction.READER_TOOLBAR,
                                    SharedSettingsAction.TTS_REPLACEMENTS,
                                    SharedSettingsAction.LOCAL_OVERRIDE_NOTE -> Unit
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

                        SharedAppTab.READER -> {
                            val opening = openingReader
                            val pdfDocument = activePdfDocument
                            if (opening != null) {
                                DesktopReaderOpeningScreen(
                                    opening = opening,
                                    onReturnToLibrary = {
                                        exitReaderTo(opening.returnTab)
                                    }
                                )
                            } else if (pdfDocument != null) {
                                PdfReaderScreen(
                                    document = pdfDocument,
                                    initialPageIndex = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId }?.lastPageIndex }
                                        ?: 0,
                                    initialViewport = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId }?.pdfReaderViewport },
                                    initialReaderSettings = activeReaderBookId
                                        ?.let { bookId -> state.rawLibraryBooks.find { it.id == bookId } }
                                        ?.let { book -> resolvedDesktopReaderSettings(book, state.pdfReaderDefaultSettings) }
                                        ?: state.pdfReaderDefaultSettings,
                                    onReturnToLibrary = {
                                        onReaderFullscreenChange(false)
                                        exitReaderTo(SharedAppTab.LIBRARY)
                                    },
                                    onFullscreenChange = onReaderFullscreenChange,
                                    onPageStateChange = { page, progress, viewport ->
                                        updateActiveBookReadingState(page, progress, pdfViewport = viewport)
                                    },
                                    onReaderSettingsChange = ::updateActiveBookReaderSettings,
                                    pdfHighlighterPalette = state.pdfHighlighterPalette,
                                    onPdfHighlighterPaletteChange = { palette ->
                                        updateState(state.reduce(AppAction.PdfHighlighterPaletteChanged(palette)))
                                    },
                                    customTextureIds = readerCustomTextureIds,
                                    onImportTexture = ::importDesktopReaderTexture,
                                    onLocalSidecarsChanged = {
                                        activeReaderBookId
                                            ?.let { bookId -> state.rawLibraryBooks.firstOrNull { it.id == bookId } }
                                            ?.let(::syncBookSidecars)
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
                                    }
                                )
                            } else {
                                DesktopReaderScreen(
                                    session = readerSession,
                                    readerEngine = readerEngine,
                                    onSessionChange = { updated ->
                                        readerSession = updated
                                        updateActiveBookReadingState(
                                            pageIndex = updated.reader.currentPageIndex,
                                            progress = updated.reader.progress,
                                            session = updated
                                        )
                                    },
                                    onReturnToLibrary = {
                                        onReaderFullscreenChange(false)
                                        exitReaderTo(SharedAppTab.LIBRARY)
                                    },
                                    onFullscreenChange = onReaderFullscreenChange,
                                    toolbarPreferences = state.readerToolbarPreferences,
                                    onToolbarPreferencesChange = { preferences ->
                                        updateState(state.reduce(AppAction.ReaderToolbarPreferencesChanged(preferences)))
                                    },
                                    highlightPalette = state.readerHighlightPalette,
                                    onHighlightPaletteChange = { palette ->
                                        updateState(state.reduce(AppAction.ReaderHighlightPaletteChanged(palette)))
                                    },
                                    ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                                    ttsReplacementBookId = activeReaderBookId ?: readerSession.reader.book.title,
                                    onTtsReplacementPreferencesChange = { preferences ->
                                        updateState(state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences)))
                                    },
                                    onPickCustomFont = {
                                        importCustomFont(chooseFontFile())?.path
                                    },
                                    customFonts = customFonts,
                                    readerExtrasState = readerExtrasState,
                                    aiByokSettings = effectiveAiSettings(),
                                    externalLookupAvailable = featurePolicy.externalLookup,
                                    cloudTtsControlsAvailable = featurePolicy.aiAndCloud,
                                    onExternalLookup = ::openReaderExternalLookup,
                                    onAiAction = ::runReaderAiAction,
                                    onAiResultDismiss = {
                                        readerExtrasState = readerExtrasState.copy(aiResult = ReaderAiResultState())
                                    },
                                    onCloudTtsToggle = ::toggleReaderCloudTts,
                                    onCloudTtsStart = ::startReaderCloudTts,
                                    onCloudTtsPauseResume = ::pauseResumeReaderCloudTts,
                                    onCloudTtsStop = ::stopReaderCloudTts,
                                    onCloudTtsClearCache = ::clearReaderCloudTtsCache,
                                    onOpenAiHub = { showReaderAiHub = true },
                                    onAutoScrollChange = ::updateReaderAutoScroll,
                                    onDownloadReaderImage = ::downloadReaderImage,
                                    readerTextureDataUri = DesktopReaderTextures::dataUriFor,
                                    readerCustomTextureIds = readerCustomTextureIds,
                                    onImportReaderTexture = ::importDesktopReaderTexture,
                                    bottomChromeExtraContent = {
                                        if (featurePolicy.aiAndCloud) {
                                            val settings = effectiveAiSettings()
                                            val ttsActive = readerExtrasState.cloudTts.isLoading ||
                                                readerExtrasState.cloudTts.isPlaying ||
                                                readerExtrasState.cloudTts.isPaused
                                            if (showReaderCloudTtsSettings) {
                                                DesktopCloudTtsSettingsOverlay(
                                                    settings = settings,
                                                    isTtsActive = ttsActive,
                                                    showCredits = !desktopBuildProfile.byokAiAvailable,
                                                    credits = state.credits,
                                                    cacheSummary = readerExtrasState.cloudTts.cacheSummary,
                                                    onClearCache = ::clearReaderCloudTtsCache,
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
                                                cloudTts = readerExtrasState.cloudTts,
                                                credits = state.credits,
                                                showCredits = !desktopBuildProfile.byokAiAvailable,
                                                onRead = {
                                                    startReaderCloudTts(
                                                        ReaderTtsReadScope.BOOK,
                                                        ReaderTtsPlanner.chunksFromCurrentLocation(readerSession)
                                                    )
                                                },
                                                onPauseResume = ::pauseResumeReaderCloudTts,
                                                onStop = ::stopReaderCloudTts,
                                                onOpenSettings = {
                                                    showReaderCloudTtsSettings = !showReaderCloudTtsSettings
                                                }
                                            )
                                        }
                                    },
                                    webViewRuntimeState = webViewRuntimeState,
                                    webViewNetworkAccessEnabled = featurePolicy.networkAccess,
                                    epubPaginationCache = desktopEpubPaginationCache,
                                    epubPaginationCacheGeneration = epubPaginationCacheGeneration
                                )
                            }
                        }
                }
            }
            DesktopDropImportOverlay(dropImportState)
        }

        if (showReaderAiHub && activePdfDocument == null && selectedTab == SharedAppTab.READER) {
            DesktopAiHubSheet(
                bookKey = readerHubBookKey(),
                bookTitle = readerSession.reader.book.title.ifBlank { "Untitled" },
                itemIndex = readerHubChapterIndex(),
                itemTitle = readerHubChapterTitle(),
                summaryCacheStore = desktopSummaryCacheStore,
                summaryResult = readerHubSummaryResult,
                isSummaryLoading = isReaderHubSummaryLoading,
                recapResult = readerHubRecapResult,
                isRecapLoading = isReaderHubRecapLoading,
                recapProgressMessage = readerHubRecapProgressMessage,
                onGenerateSummary = ::generateReaderHubSummary,
                onClearSummary = ::clearReaderHubSummary,
                onGenerateRecap = ::generateReaderHubRecap,
                onClearRecap = ::clearReaderHubRecap,
                onDismiss = { showReaderAiHub = false },
                credits = state.credits,
                showCredits = !desktopBuildProfile.byokAiAvailable
            )
        }

        desktopFeatureNotice?.let { notice ->
            AlertDialog(
                onDismissRequest = { desktopFeatureNotice = null },
                title = { Text(notice.title) },
                text = { Text(notice.message) },
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
                        Text(notice.confirmLabel)
                    }
                },
                dismissButton = if (notice.action != null) {
                    {
                        TextButton(onClick = { desktopFeatureNotice = null }) {
                            Text("Not now")
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

        if (showClearBookCacheDialog) {
            SharedConfirmDialog(
                title = "Clear book cache",
                body = "Delete generated desktop book and EPUB pagination cache files? They will be recreated the next time books are opened.",
                confirmLabel = "Clear",
                onDismiss = { showClearBookCacheDialog = false },
                onConfirm = {
                    clearDesktopBookCache()
                    showClearBookCacheDialog = false
                }
            )
        }

        if (showCreateShelfDialog) {
            SharedTextInputDialog(
                title = "Create shelf",
                label = "Shelf name",
                initialValue = "",
                confirmLabel = "Create",
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
                title = "Rename shelf",
                label = "Shelf name",
                initialValue = shelf.name,
                confirmLabel = "Rename",
                onDismiss = { shelfToRename = null },
                onConfirm = { name ->
                    renameShelf(shelf, name)
                    shelfToRename = null
                }
            )
        }

        shelfToDelete?.let { shelf ->
            SharedConfirmDialog(
                title = "Delete shelf",
                body = "Delete \"${shelf.name}\"? Books stay in your library.",
                confirmLabel = "Delete",
                onDismiss = { shelfToDelete = null },
                onConfirm = {
                    deleteShelf(shelf)
                    shelfToDelete = null
                }
            )
        }

        folderToRemove?.let { folder ->
            SharedConfirmDialog(
                title = "Remove folder",
                body = "Remove \"${folder.name}\" and its ${folder.bookCount} book(s) from the app? Files on disk will not be deleted.",
                confirmLabel = "Remove",
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
                title = "Tag selected books",
                label = "Tag name",
                initialValue = state.allTags.firstOrNull()?.name.orEmpty(),
                confirmLabel = "Apply",
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

private fun desktopSignInRequiredNotice(featureLabel: String): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        title = "Sign in required",
        message = "Sign in with Google to use $featureLabel on desktop.",
        confirmLabel = "Sign in",
        action = DesktopFeatureNoticeAction.SIGN_IN
    )
}

private fun desktopOutOfCreditsNotice(featureLabel: String): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        title = "Out of credits",
        message = "Using $featureLabel needs credits on desktop. Pro and credits can only be purchased from the Android app.",
        confirmLabel = "View Pro and credits",
        action = DesktopFeatureNoticeAction.OPEN_PRO
    )
}

private fun desktopProRequiredNotice(featureLabel: String): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        title = "Pro required",
        message = "$featureLabel requires Pro. Pro can only be purchased from the Android app, then desktop will use the upgraded account after sign-in.",
        confirmLabel = "View Pro and credits",
        action = DesktopFeatureNoticeAction.OPEN_PRO
    )
}

private fun desktopFeatureUnavailableNotice(message: String): DesktopFeatureNotice {
    return DesktopFeatureNotice(
        title = "Feature unavailable",
        message = message
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
            desktopOutOfCreditsNotice("This feature")

        message.contains("Sign in", ignoreCase = true) ||
            message.contains("HTTP 401", ignoreCase = true) ||
            message.contains("status code 401", ignoreCase = true) ||
            message.contains("Authentication required", ignoreCase = true) ->
            desktopSignInRequiredNotice("this feature")

        message.contains("requires Pro", ignoreCase = true) ||
            message.contains("REQUIRES_PRO", ignoreCase = true) ->
            desktopProRequiredNotice("This feature")

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
