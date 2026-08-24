@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.ComposeUIViewController
import com.aryan.reader.shared.AppAction
import com.aryan.reader.shared.AddBooksSource
import com.aryan.reader.shared.AnnotationExportFormat
import com.aryan.reader.shared.AnnotationExportFormatter
import com.aryan.reader.shared.sanitizeCustomSleepTimerMinutes
import com.aryan.reader.shared.AccountAuthProvider
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.LocalFolderSyncEngine
import com.aryan.reader.shared.MAX_OPEN_PDF_TABS
import com.aryan.reader.shared.MobileExternalFileCloseAction
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.SharedLegalProfile
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedImportPlanner
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedFolderScannedFile
import com.aryan.reader.shared.SharedMobileFolderScanResult
import com.aryan.reader.shared.availableMobileFolderName
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SharedAudiobook
import com.aryan.reader.shared.SharedAudiobookFormats
import com.aryan.reader.shared.splitFilesByAudiobookDecodability
import com.aryan.reader.shared.SharedAudiobookPlaybackRequest
import com.aryan.reader.shared.SharedAudiobookPlaybackState
import com.aryan.reader.shared.sharedAudiobookResumePosition
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.UserData
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.ReaderAutoScrollProfile
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.shared.RecapResult
import com.aryan.reader.shared.ReaderExternalLookupService
import com.aryan.reader.shared.ui.IosReaderLookupServices
import com.aryan.reader.shared.migrateLegacyIosReaderAutoScrollSpeed
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.canEnableGoogleDriveSync
import com.aryan.reader.shared.canOpenMobilePdfTab
import com.aryan.reader.shared.canUseCloudSync
import com.aryan.reader.shared.mergeCloudLibrarySnapshotWithDownloadedBooks
import com.aryan.reader.shared.enqueueMobileFolderScan
import com.aryan.reader.shared.mobileExternalFileCloseAction
import com.aryan.reader.shared.MobileExternalOpenAction
import com.aryan.reader.shared.mobileExternalOpenAction
import com.aryan.reader.shared.MobileSettingsMutation
import com.aryan.reader.shared.MobileSettingsMutationState
import com.aryan.reader.shared.MobileStrictFileFilterEffect
import com.aryan.reader.shared.planMobileSettingsMutation
import com.aryan.reader.shared.normalizedExternalFileBehavior
import com.aryan.reader.shared.planMobileImportBatch
import com.aryan.reader.shared.singleSelectionOpenBook
import com.aryan.reader.shared.mobileBookOpenPreflightAction
import com.aryan.reader.shared.MobileBookOpenPreflightAction
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.withMobileBookOpened
import com.aryan.reader.shared.withMobileBookClosed
import com.aryan.reader.shared.withMobileLibrarySearchActive
import com.aryan.reader.shared.withMobileLibrarySearchQuery
import com.aryan.reader.shared.withMobileFolderFileTypes
import com.aryan.reader.shared.withMobileTemporaryBookClosed
import com.aryan.reader.shared.withMobileTemporaryBookOpened
import com.aryan.reader.shared.withRestoredMobileReaderSession
import com.aryan.reader.shared.withRestoredMobileLibraryNavigation
import com.aryan.reader.shared.withoutMobileReaderSession
import com.aryan.reader.shared.withMobileImportedBooks
import com.aryan.reader.shared.withMigratedMobileBookIdentity
import com.aryan.reader.shared.withAudiobookImported
import com.aryan.reader.shared.withAudiobookPosition
import com.aryan.reader.shared.withLoadedMetadata
import com.aryan.reader.shared.withUserEditedMetadata
import com.aryan.reader.shared.DefaultReaderCustomBrightness
import com.aryan.reader.shared.normalizeReaderBrightness
import com.aryan.reader.shared.PdfReaderTool
import com.aryan.reader.shared.PdfToolbarPreferences
import com.aryan.reader.shared.withNewerReaderSession
import com.aryan.reader.shared.withPdfReadingProgress
import com.aryan.reader.shared.withReaderSessionState
import com.aryan.reader.shared.toSharedMobileLibrarySnapshot
import com.aryan.reader.shared.toSharedMobileReaderState
import com.aryan.reader.shared.sharedSettingsHubModel
import com.aryan.reader.shared.sharedLegalLinksForProfile
import com.aryan.reader.shared.sharedAppLanguageLabel
import com.aryan.reader.shared.sharedAppLanguages
import com.aryan.reader.shared.shouldApplyMobileFolderScan
import com.aryan.reader.shared.shouldRequestCloudSyncAfterFolderSyncChange
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.aryan.reader.shared.opds.SharedOpdsController
import com.aryan.reader.shared.opds.SharedOpdsDownloadLocation
import com.aryan.reader.shared.opds.SharedOpdsDownloadState
import com.aryan.reader.shared.opds.SharedOpdsStreamUri
import com.aryan.reader.shared.opds.opdsStreamBooksForCatalog
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfExportSnapshot
import com.aryan.reader.shared.pdf.SharedPdfReaderStateSerializer
import com.aryan.reader.shared.pdf.SharedPdfCloudSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfCloudSidecarSnapshot
import com.aryan.reader.shared.pdf.LegacyPdfPageBookmarkCodec
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.IosPdfCloudSidecarStore
import com.aryan.reader.shared.pdf.loadIosPdfOcrLanguage
import com.aryan.reader.shared.pdf.persistIosPdfOcrLanguage
import com.aryan.reader.shared.pdf.SharedPdfReaderHostConfig
import com.aryan.reader.shared.pdf.SharedPdfReaderGlobalResource
import com.aryan.reader.shared.pdf.SharedPdfReaderSessionKey
import com.aryan.reader.shared.pdf.PdfAutoScrollProfile
import com.aryan.reader.shared.pdf.generateIosPdfReflowHtml
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedAppThemeSettingsDialog
import com.aryan.reader.shared.ui.SharedAboutScreen
import com.aryan.reader.shared.ui.SharedAnnotationExportFormatDialog
import com.aryan.reader.shared.ui.SharedCustomFontsScreen
import com.aryan.reader.shared.ui.SharedHelpFeedbackScreen
import com.aryan.reader.shared.ui.SharedMobileAppDrawerContent
import com.aryan.reader.shared.ui.SharedMobileEpubReaderScreen
import com.aryan.reader.shared.ui.SharedMobileReaderTtsSettingsSheet
import com.aryan.reader.shared.ui.SharedMobilePdfReaderHost
import com.aryan.reader.shared.ui.SharedMobilePdfReflowUiState
import com.aryan.reader.shared.ui.SharedMobileDictionarySettingsSheet
import com.aryan.reader.shared.ui.SharedAiSettingsScreen
import com.aryan.reader.shared.ui.SharedAiSettingsStrings
import com.aryan.reader.shared.ui.IosSharedMobileCloudTts
import com.aryan.reader.shared.ui.sharedAnnotationExportFormatOptions
import com.aryan.reader.shared.ui.SharedMobileHomeScreen
import com.aryan.reader.shared.ui.SharedMobileHomeActions
import com.aryan.reader.shared.ui.SharedMobileLibraryScreen
import com.aryan.reader.shared.ui.SharedMobileUnifiedLibraryScreen
import com.aryan.reader.shared.ui.SharedOpdsScreen
import com.aryan.reader.shared.ui.LocalUsePdfFileNameAsDisplayName
import com.aryan.reader.shared.ui.SharedMobileLibraryTab
import com.aryan.reader.shared.ui.SharedMobileMainDestination
import com.aryan.reader.shared.ui.SharedMobileMainScaffold
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedStringResolver
import com.aryan.reader.shared.ui.formatSharedMobileDateTime
import com.aryan.reader.shared.ui.SharedSupportProjectScreen
import com.aryan.reader.shared.ui.mobileRecentBooks
import com.aryan.reader.shared.ui.readerBannerMessage
import com.aryan.reader.shared.ui.readerLiteral
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.openSharedMobileExternalUrl
import com.aryan.reader.shared.ui.rememberSharedMobileEpubLocalTts
import com.aryan.reader.shared.ui.withoutIosFolderFilter
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.ui.SharedMobilePdfNativeAction
import com.aryan.reader.shared.PdfSplitPane
import com.aryan.reader.shared.PdfSplitWorkspaceAction
import com.aryan.reader.shared.PdfSplitWorkspaceState
import com.aryan.reader.shared.samePdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.UIKit.UIViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIPrintInteractionController
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.rename

private val IOS_NATIVE_READER_FILE_TYPES = setOf(
    FileType.PDF,
    FileType.EPUB,
    FileType.MOBI,
    FileType.TXT,
    FileType.MD,
    FileType.HTML,
    FileType.FB2,
    FileType.FODT,
    FileType.CBZ,
    FileType.CBR,
    FileType.CB7,
    FileType.CBT,
    FileType.DOCX,
    FileType.ODT,
    FileType.PPTX,
)

private val IosLegalLinks = sharedLegalLinksForProfile(SharedLegalProfile.STANDARD)

class ReaderIosBridge internal constructor(
    private val readerSystemEffects: IosReaderSystemEffects,
    private val pdfNativeActionPresenter: IosPdfNativeActionPresenter,
) {
    constructor() : this(
        readerSystemEffects = UIKitReaderSystemEffects,
        pdfNativeActionPresenter = IosPdfNativeActionPresenter(::performIosPdfNativeAction),
    )

    private var systemUiHandler: ((statusHidden: Boolean, navigationHidden: Boolean, lightContent: Boolean, backgroundArgb: Long, edgeToEdge: Boolean) -> Unit)? = null
    private var latestSystemUiState: IosSystemUiState? = null
    private var originalReaderBrightness: Double? = null
    private var orientationHandler: ((mode: Int) -> Unit)? = null
    internal var importedFiles by mutableStateOf<List<IosImportedFile>>(loadPersistedImportedFiles())
        private set
    internal var pendingImportBatches by mutableStateOf<List<IosPendingImportBatch>>(emptyList())
        private set
    internal var importedFonts by mutableStateOf<List<CustomFontItem>>(loadIosLibrarySnapshot().customFonts)
        private set
    internal var pendingExternalOpen by mutableStateOf<IosExternalOpen?>(null)
        private set
    internal var pendingFolderScans by mutableStateOf<List<SharedMobileFolderScanResult>>(emptyList())
        private set
    internal var importedCoverPath by mutableStateOf<String?>(null)
        private set

    /** Android-parity unified library grid/list toggle, persisted per device. */
    internal var iosUnifiedLibraryListView by mutableStateOf(loadIosUnifiedLibraryListView())

    internal var localStoreKitState by mutableStateOf(IosLocalStoreKitState())
        private set
    internal var accountState by mutableStateOf(IosAccountState())
        private set
    internal var registeredDevices by mutableStateOf<List<com.aryan.reader.shared.DeviceItem>>(emptyList())
        private set
    internal var isDeviceManagementLoading by mutableStateOf(false)
        private set
    internal var deviceManagementStatus by mutableStateOf<String?>(null)
        private set
    internal var isCloudLocalDataClearLoading by mutableStateOf(false)
        private set
    internal var cloudLocalDataClearStatus by mutableStateOf<String?>(null)
        private set
    internal var localCloudDataClearEvent by mutableStateOf(0)
        private set
    internal var isDebugBuild by mutableStateOf(false)
        private set
    internal var appLifecycleState by mutableStateOf(IosAppLifecycleState())
        private set
    private var purchaseHandler: ((String) -> Unit)? = null
    private var restorePurchasesHandler: (() -> Unit)? = null
    private var authHandler: ((String) -> Unit)? = null
    private var signOutHandler: (() -> Unit)? = null
    private var cloudSyncHandler: ((String) -> Unit)? = null
    private var cloudUploadHandler: ((String) -> Unit)? = null
    private var deviceManagementRefreshHandler: (() -> Unit)? = null
    private var deviceRevokeHandler: ((String) -> Unit)? = null
    private var cloudLocalDataClearHandler: (() -> Unit)? = null
    private var folderFileDeletionHandler: ((String, List<String>) -> Unit)? = null
    private var folderFileReplacementHandler: ((String, String) -> String?)? = null
    private var folderFileAdditionHandler: ((String, String, String) -> String?)? = null
    internal var audiobookPlayHandler: ((String, Double, Double) -> Unit)? = null
    internal var audiobookPauseHandler: (() -> Unit)? = null
    internal var audiobookSpeedAndResumeHandler: ((Float) -> Unit)? = null
    internal var audiobookSeekHandler: ((Double) -> Unit)? = null
    internal var audiobookSpeedHandler: ((Double) -> Unit)? = null
    internal var audiobookSleepTimerHandler: ((Int) -> Unit)? = null
    internal var audiobookCancelSleepHandler: (() -> Unit)? = null
    internal var audiobookStopHandler: (() -> Unit)? = null
    internal var audiobookMetadataHandler: ((String, String, (String, String?, String?, Long) -> Unit) -> Unit)? = null

    /**
     * Native capture of the process unified log for diagnostics export. Swift owns
     * OSLogStore (not exposed to Kotlin/Native platform libraries); Android parity
     * target is `logcat -d -t 5000`.
     */
    internal var unifiedDiagnosticsProvider: (() -> String?)? = null
    internal var audiobookPlaybackSnapshot by mutableStateOf(SharedAudiobookPlaybackState())
        private set
    internal var pendingCloudSync by mutableStateOf<IosPendingCloudSync?>(null)
        private set
    internal var cloudSyncStatus by mutableStateOf<String?>(null)
        private set

    internal var latestNativeEvent by mutableStateOf<String?>(null)
        private set

    fun updateAudiobookPlaybackState(
        isPlaying: Boolean,
        isLoading: Boolean,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
        sleepTimerRemainingMs: Long,
        error: String?,
    ) {
        audiobookPlaybackSnapshot = audiobookPlaybackSnapshot.copy(
            connected = true,
            isPlaying = isPlaying,
            isLoading = isLoading,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            speed = speed.takeIf { it > 0f } ?: 1f,
            sleepTimerRemainingMs = sleepTimerRemainingMs.coerceAtLeast(0L),
            error = error,
        )
    }

    internal fun markAudiobookConnected(bookId: String) {
        audiobookPlaybackSnapshot = audiobookPlaybackSnapshot.copy(
            connected = true,
            bookId = bookId,
            error = null,
        )
    }

    internal fun markAudiobookStopped() {
        audiobookPlaybackSnapshot = audiobookPlaybackSnapshot.copy(
            connected = false,
            bookId = null,
            isPlaying = false,
            isLoading = false,
            sleepTimerRemainingMs = 0L,
            error = null,
        )
    }

    fun recordImportedFiles(
        fileNames: List<String>,
        filePaths: List<String> = fileNames,
        contentIds: List<String> = emptyList(),
        failedCount: Int = 0,
        wasCancelled: Boolean = false,
        autoOpen: Boolean = true,
    ) {
        if (wasCancelled) {
            latestNativeEvent = "Import cancelled"
            return
        }

        val imported = fileNames.mapIndexed { index, fileName ->
            IosImportedFile(
                name = fileName,
                path = filePaths.getOrNull(index) ?: fileName,
                contentId = contentIds.getOrNull(index).orEmpty(),
            )
        }
        importedFiles = (imported + importedFiles).distinctBy { it.path }
        persistImportedFiles(importedFiles)
        if (imported.isNotEmpty() || failedCount > 0) {
            pendingImportBatches = pendingImportBatches + IosPendingImportBatch(
                files = imported,
                failedCount = failedCount.coerceAtLeast(0),
                autoOpen = autoOpen,
            )
        }
        latestNativeEvent = when {
            imported.isEmpty() && failedCount > 0 -> "Could not import the selected file(s)"
            failedCount > 0 -> "Copied ${imported.size} file(s); $failedCount failed"
            imported.isEmpty() -> "No files selected"
            else -> "Selected ${fileNames.size} file(s) from iOS"
        }
    }

    internal fun consumeImportBatch() {
        pendingImportBatches = pendingImportBatches.drop(1)
    }

    fun recordImportedFolder(
        folderName: String,
        fileNames: List<String>,
        filePaths: List<String>,
        contentIds: List<String> = emptyList(),
        relativePaths: List<String> = emptyList(),
        fileSizes: List<String> = emptyList(),
        lastModifiedTimestamps: List<String> = emptyList(),
        scanSucceeded: Boolean = true,
    ) {
        val imported = fileNames.mapIndexed { index, fileName ->
            IosImportedFile(
                name = fileName,
                path = filePaths.getOrNull(index) ?: fileName,
                sourceFolder = folderName,
                contentId = contentIds.getOrNull(index).orEmpty(),
                relativePath = relativePaths.getOrNull(index).orEmpty().ifBlank { fileName },
                fileSize = fileSizes.getOrNull(index)?.toLongOrNull() ?: 0L,
                lastModifiedTimestamp = lastModifiedTimestamps.getOrNull(index)?.toLongOrNull() ?: 0L,
            )
        }
        if (scanSucceeded) {
            importedFiles = (imported + importedFiles.filterNot { it.sourceFolder == folderName }).distinctBy { it.path }
            persistImportedFiles(importedFiles)
        }
        val scannedFiles = imported.map { file ->
            SharedFolderScannedFile(
                name = file.name,
                path = file.path,
                sourceFolder = folderName,
                relativePath = file.relativePath.ifBlank { file.name },
                // Android resolves types through the shared capabilities table (MOBI aliases,
                // xhtml, fb2.zip, ...). iOS must not keep a diverging extension map.
                type = SharedFileCapabilities.fileTypeForName(file.name),
                size = file.fileSize,
                lastModified = file.lastModifiedTimestamp,
            )
        }
        pendingFolderScans = enqueueMobileFolderScan(
            pendingFolderScans,
            SharedMobileFolderScanResult(
                folderName = folderName,
                files = scannedFiles,
                succeeded = scanSucceeded,
            ),
        )
        latestNativeEvent = if (!scanSucceeded) {
            "Could not refresh $folderName; keeping the previous scan"
        } else if (imported.isEmpty()) {
            "No supported files found in $folderName"
        } else {
            "Imported ${imported.size} file(s) from $folderName"
        }
    }

    fun availableImportedFolderName(
        preferredName: String,
        existingNames: List<String>,
    ): String = availableMobileFolderName(preferredName, existingNames)

    fun recordImportedFonts(fileNames: List<String>, filePaths: List<String> = fileNames) {
        if (fileNames.isEmpty()) {
            latestNativeEvent = "Font import cancelled"
            return
        }
        val imported = fileNames.mapIndexedNotNull { index, fileName ->
            val path = filePaths.getOrNull(index) ?: return@mapIndexedNotNull null
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension !in setOf("ttf", "otf", "woff", "woff2")) return@mapIndexedNotNull null
            CustomFontItem(
                id = "ios-font-${path.hashCode()}",
                displayName = fileName.substringBeforeLast('.').ifBlank { fileName },
                fileName = fileName,
                fileExtension = extension,
                path = path,
                timestamp = currentTimestamp()
            )
        }
        importedFonts = (imported + importedFonts).distinctBy { it.path }
        latestNativeEvent = if (imported.isEmpty()) "No supported font files selected" else "Imported ${imported.size} font(s)"
    }

    fun deleteImportedFont(path: String) {
        runCatching { NSFileManager.defaultManager.removeItemAtPath(path, error = null) }
        importedFonts = importedFonts.filterNot { it.path == path }
        latestNativeEvent = "Removed imported font"
    }

    fun recordNativeEvent(message: String) {
        latestNativeEvent = message
        IosDiagnosticLogStore.record("ReaderIosNative", message)
    }

    fun externalFileBehavior(): String = loadIosLibrarySnapshot().externalFileBehavior

    fun shouldAddExternalFileToLibrary(): Boolean = mobileExternalOpenAction(externalFileBehavior()) == MobileExternalOpenAction.OPEN_LIBRARY_COPY

    fun usesStrictFileFilter(): Boolean = loadIosLibrarySnapshot().useStrictFileFilter

    fun readableFileExtensions(): List<String> {
        return SharedFileCapabilities.all
            .filter { it.surfaceFor(ReaderPlatform.IOS) != null }
            .flatMap { it.extensions }
            .distinct()
            .sorted()
    }

    fun importedFilePathsMissingContentId(): List<String> {
        return importedFiles.filter { it.contentId.isBlank() }.map { it.path }
    }

    fun backfillImportedContentIds(filePaths: List<String>, contentIds: List<String>) {
        val identities = filePaths.mapIndexedNotNull { index, path ->
            contentIds.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { path to it }
        }.toMap()
        if (identities.isEmpty()) return
        importedFiles = importedFiles.map { file ->
            identities[file.path]?.let { file.copy(contentId = it) } ?: file
        }
        persistImportedFiles(importedFiles)
    }

    fun openExternalFile(fileName: String, filePath: String, contentId: String, addToLibrary: Boolean) {
        if (fileName.isBlank() || filePath.isBlank()) return
        val behavior = normalizedExternalFileBehavior(externalFileBehavior())
        if (addToLibrary) {
            recordImportedFiles(listOf(fileName), listOf(filePath), listOf(contentId))
        }
        pendingExternalOpen = IosExternalOpen(
            file = IosImportedFile(name = fileName, path = filePath, contentId = contentId),
            addToLibrary = addToLibrary,
            behavior = behavior,
        )
    }

    fun recordImportedCover(filePath: String?) {
        importedCoverPath = filePath?.takeIf { it.isNotBlank() }
        latestNativeEvent = if (importedCoverPath == null) "Cover selection cancelled" else "Selected cover image"
    }

    internal fun consumeImportedCover() {
        importedCoverPath = null
    }

    internal fun consumeExternalOpen() {
        pendingExternalOpen = null
    }

    internal fun consumeFolderScan() {
        pendingFolderScans = pendingFolderScans.drop(1)
    }

    fun removeImportedFiles(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        val importsRoot = iosImportsDirectoryPath()?.canonicalIosFilePath()
        filePaths.forEach { path ->
            val canonicalPath = path.canonicalIosFilePath()
            if (importsRoot != null && canonicalPath.startsWith("$importsRoot/")) {
                runCatching {
                    NSFileManager.defaultManager.removeItemAtPath(canonicalPath, error = null)
                }
            }
        }
        importedFiles = importedFiles.filterNot { it.path in filePaths }
        persistImportedFiles(importedFiles)
        latestNativeEvent = "Removed ${filePaths.size} file(s) from iOS library"
    }

    fun setFolderFileDeletionHandler(handler: (String, List<String>) -> Unit) {
        folderFileDeletionHandler = handler
    }

    fun setFolderFileReplacementHandler(handler: (String, String) -> String?) {
        folderFileReplacementHandler = handler
    }

    fun setFolderFileAdditionHandler(handler: (folderName: String, sourcePath: String, fileName: String) -> String?) {
        folderFileAdditionHandler = handler
    }

    internal fun addFolderManagedFile(folderName: String, sourcePath: String, fileName: String): String? {
        return folderFileAdditionHandler?.invoke(folderName, sourcePath, fileName)
    }

    fun setUnifiedDiagnosticsProvider(handler: (() -> String?)?) {
        unifiedDiagnosticsProvider = handler
    }

    fun setAudiobookPlayHandler(handler: (filePath: String, positionMs: Double, speed: Double) -> Unit) {
        audiobookPlayHandler = handler
    }

    fun setAudiobookPauseHandler(handler: () -> Unit) {
        audiobookPauseHandler = handler
    }

    fun setAudiobookSpeedAndResumeHandler(handler: (speed: Float) -> Unit) {
        audiobookSpeedAndResumeHandler = handler
    }

    fun setAudiobookSeekHandler(handler: (positionMs: Double) -> Unit) {
        audiobookSeekHandler = handler
    }

    fun setAudiobookSpeedHandler(handler: (speed: Double) -> Unit) {
        audiobookSpeedHandler = handler
    }

    fun setAudiobookSleepTimerHandler(handler: (minutes: Int) -> Unit) {
        audiobookSleepTimerHandler = handler
    }

    fun setAudiobookCancelSleepHandler(handler: () -> Unit) {
        audiobookCancelSleepHandler = handler
    }

    fun setAudiobookStopHandler(handler: () -> Unit) {
        audiobookStopHandler = handler
    }

    fun setAudiobookMetadataHandler(
        handler: (filePath: String, fallbackTitle: String, completion: (title: String, author: String?, album: String?, durationMs: Long) -> Unit) -> Unit,
    ) {
        audiobookMetadataHandler = handler
    }

    internal fun replaceFolderManagedFile(folderName: String, managedPath: String): IosFolderReplacement? {
        val fields = folderFileReplacementHandler?.invoke(folderName, managedPath)
            ?.split('\t') ?: return null
        val fileSize = fields.getOrNull(0)?.toLongOrNull() ?: return null
        val modifiedAt = fields.getOrNull(1)?.toLongOrNull() ?: return null
        importedFiles = importedFiles.map { file ->
            if (file.path == managedPath) {
                file.copy(fileSize = fileSize, lastModifiedTimestamp = modifiedAt)
            } else {
                file
            }
        }
        persistImportedFiles(importedFiles)
        return IosFolderReplacement(fileSize = fileSize, lastModifiedTimestamp = modifiedAt)
    }

    fun removeFolderManagedFiles(folderName: String, filePaths: List<String>) {
        if (folderName.isBlank() || filePaths.isEmpty()) return
        folderFileDeletionHandler?.invoke(folderName, filePaths)
        importedFiles = importedFiles.filterNot { it.path in filePaths }
        persistImportedFiles(importedFiles)
        latestNativeEvent = "Deleted ${filePaths.size} file(s) from $folderName"
    }

    fun shareFile(path: String): Boolean {
        if (path.isBlank() || !NSFileManager.defaultManager.fileExistsAtPath(path)) return false
        return presentIosShareSheet(NSURL.fileURLWithPath(path))
    }

    fun exportAnnotations(book: BookItem, format: AnnotationExportFormat): Boolean {
        val document = when (book.type) {
            FileType.PDF -> AnnotationExportFormatter.fromPdfAnnotations(
                bookTitle = book.cardTitle(),
                annotations = loadPersistedIosPdfReaderState(book)?.annotations.orEmpty(),
            )
            else -> AnnotationExportFormatter.fromEpubBook(book)
        }
        if (!document.hasAnnotations) return false
        val fileName = AnnotationExportFormatter.suggestedFileName(document.bookTitle, format)
        val path = NSTemporaryDirectory() + fileName
        if (!writeIosUtf8File(path, AnnotationExportFormatter.render(document, format))) return false
        return presentIosShareSheet(NSURL.fileURLWithPath(path))
    }

    /**
     * Shares the bounded, app-owned iOS diagnostic stream. Android can dump
     * logcat; iOS cannot safely read the system log, so this export contains
     * recent reader/native events collected in-process.
     */
    fun exportDiagnosticLogs(): Boolean {
        val entries = IosDiagnosticLogStore.snapshot()
        val unifiedEntries = IosDiagnosticLogStore.unifiedLogSnapshot(unifiedDiagnosticsProvider)
        val content = buildString {
            appendLine("Episteme iOS diagnostics")
            appendLine("Generated at: ${currentTimestamp()}")
            appendLine("Entries: ${entries.size}")
            appendLine()
            if (entries.isEmpty()) {
                appendLine("No in-process diagnostic events have been collected yet.")
            } else {
                entries.forEach(::appendLine)
            }
            appendLine()
            appendLine("=== Unified log (last 24h, process scope) ===")
            if (unifiedEntries.isEmpty()) {
                appendLine("Unified log capture unavailable or empty.")
            } else {
                unifiedEntries.forEach(::appendLine)
            }
        }
        val path = NSTemporaryDirectory() + "episteme-diagnostics-${currentTimestamp()}.txt"
        if (!writeIosUtf8File(path, content)) {
            recordNativeEvent("Diagnostic log export failed: could not write temporary file")
            return false
        }
        recordNativeEvent(
            "Diagnostic log export prepared entries=${entries.size} unifiedEntries=${unifiedEntries.size}",
        )
        return presentIosShareSheet(NSURL.fileURLWithPath(path))
    }

    fun setKeepScreenOn(enabled: Boolean) {
        readerSystemEffects.setKeepScreenOn(enabled)
    }

    fun updateAppActive(active: Boolean) {
        appLifecycleState = IosAppLifecycleState(
            isActive = active,
            eventId = appLifecycleState.eventId + 1,
        )
    }

    fun setReaderBrightness(brightness: Float?) {
        if (originalReaderBrightness == null) {
            originalReaderBrightness = readerSystemEffects.brightness
        }
        readerSystemEffects.setBrightness(
            brightness?.toDouble() ?: originalReaderBrightness ?: readerSystemEffects.brightness
        )
    }

    fun restoreReaderBrightness() {
        originalReaderBrightness?.let(readerSystemEffects::setBrightness)
        originalReaderBrightness = null
    }

    internal fun performPdfNativeAction(
        book: BookItem,
        action: SharedMobilePdfNativeAction,
    ): Boolean = pdfNativeActionPresenter.perform(book, action)

    fun setOrientationHandler(handler: (mode: Int) -> Unit) {
        orientationHandler = handler
    }

    fun applyReaderOrientation(mode: ReaderScreenOrientationMode) {
        orientationHandler?.invoke(mode.ordinal)
    }

    fun setSystemUiHandler(handler: (statusHidden: Boolean, navigationHidden: Boolean, lightContent: Boolean, backgroundArgb: Long, edgeToEdge: Boolean) -> Unit) {
        systemUiHandler = handler
        latestSystemUiState?.let { state ->
            handler(state.statusHidden, state.navigationHidden, state.lightContent, state.backgroundArgb, state.edgeToEdge)
        }
    }

    fun updateSystemUi(hidden: Boolean, lightContent: Boolean, backgroundArgb: Long, edgeToEdge: Boolean) {
        updateReaderSystemUi(hidden, hidden, lightContent, backgroundArgb, edgeToEdge)
    }

    fun updateReaderSystemUi(
        statusHidden: Boolean,
        navigationHidden: Boolean,
        lightContent: Boolean,
        backgroundArgb: Long,
        edgeToEdge: Boolean
    ) {
        latestSystemUiState = IosSystemUiState(statusHidden, navigationHidden, lightContent, backgroundArgb, edgeToEdge)
        systemUiHandler?.invoke(statusHidden, navigationHidden, lightContent, backgroundArgb, edgeToEdge)
    }

    fun setLocalStoreKitHandlers(
        purchase: (String) -> Unit,
        restore: () -> Unit,
    ) {
        purchaseHandler = purchase
        restorePurchasesHandler = restore
    }

    fun requestLocalStoreKitPurchase(productId: String) {
        purchaseHandler?.invoke(productId)
    }

    fun requestLocalStoreKitRestore() {
        restorePurchasesHandler?.invoke()
    }

    fun updateLocalStoreKitState(
        available: Boolean,
        proUnlocked: Boolean,
        credits: Int,
        proPrice: String?,
        credits100Price: String?,
        credits300Price: String?,
        credits750Price: String?,
        status: String?,
    ) {
        localStoreKitState = IosLocalStoreKitState(
            available = available,
            proUnlocked = proUnlocked,
            credits = credits.coerceAtLeast(0),
            proPrice = proPrice,
            creditPrices = mapOf(
                IosStoreKitProductIds.CREDITS_100 to credits100Price,
                IosStoreKitProductIds.CREDITS_300 to credits300Price,
                IosStoreKitProductIds.CREDITS_750 to credits750Price,
            ).filterValues { it != null }.mapValues { it.value!! },
            status = status,
        )
        status?.let { IosDiagnosticLogStore.record("StoreKit", it) }
    }

    fun setAuthHandlers(
        authenticate: (String) -> Unit,
        signOut: () -> Unit,
    ) {
        authHandler = authenticate
        signOutHandler = signOut
    }

    fun requestAuthentication(provider: String) {
        authHandler?.invoke(provider)
    }

    fun requestSignOut() {
        signOutHandler?.invoke()
    }

    /**
     * Native account-owned device management keeps Firebase out of the shared
     * settings UI. The handler refreshes the active device list, while this
     * bridge owns observable loading/status state for Compose.
     */
    fun setDeviceManagementHandlers(
        refresh: () -> Unit,
        revoke: (String) -> Unit,
    ) {
        deviceManagementRefreshHandler = refresh
        deviceRevokeHandler = revoke
    }

    fun requestDeviceManagement() {
        isDeviceManagementLoading = true
        deviceManagementStatus = null
        deviceManagementRefreshHandler?.invoke()
            ?: run {
                isDeviceManagementLoading = false
                deviceManagementStatus = "device_unavailable"
            }
    }

    fun requestDeviceRevoke(deviceId: String) {
        if (deviceId.isBlank()) return
        isDeviceManagementLoading = true
        deviceManagementStatus = null
        deviceRevokeHandler?.invoke(deviceId)
            ?: run {
                isDeviceManagementLoading = false
                deviceManagementStatus = "device_unavailable"
            }
    }

    fun updateRegisteredDevices(
        deviceIds: List<String>,
        deviceNames: List<String>,
        lastSeenEpochMillis: List<String>,
        status: String?,
    ) {
        registeredDevices = deviceIds.mapIndexed { index, deviceId ->
            com.aryan.reader.shared.DeviceItem(
                deviceId = deviceId,
                deviceName = deviceNames.getOrNull(index)?.ifBlank { deviceId } ?: deviceId,
                lastSeenEpochMillis = lastSeenEpochMillis.getOrNull(index)?.toLongOrNull()?.takeIf { it > 0L },
            )
        }.sortedByDescending { it.lastSeenEpochMillis ?: 0L }
        isDeviceManagementLoading = false
        deviceManagementStatus = status
    }

    fun setCloudLocalDataClearHandler(handler: () -> Unit) {
        cloudLocalDataClearHandler = handler
    }

    fun requestCloudLocalDataClear() {
        isCloudLocalDataClearLoading = true
        cloudLocalDataClearStatus = null
        cloudLocalDataClearHandler?.invoke()
            ?: completeCloudLocalDataClear(
                success = false,
                message = "clear_unavailable",
            )
    }

    fun completeCloudLocalDataClear(success: Boolean, message: String) {
        isCloudLocalDataClearLoading = false
        cloudLocalDataClearStatus = message
    }

    fun setDebugBuild(enabled: Boolean) {
        isDebugBuild = enabled
    }

    /** Invoked by the native clear-data completion hook after cloud deletion. */
    fun clearLocalCloudData() {
        val fileManager = NSFileManager.defaultManager
        importedFiles.map { it.path.resolvedIosImportedFilePath() }
            .filter(String::isNotBlank)
            .forEach { path -> fileManager.removeItemAtPath(path, error = null) }
        val appSupportPath = (
            fileManager.URLsForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomains = NSUserDomainMask,
            ).firstOrNull() as? NSURL
            )?.path
        listOf("Imports", "Documents", "Covers", "Fonts", "LocalFolders", "PdfSidecars", "MetadataBackups")
            .mapNotNull { directoryName -> appSupportPath?.let { "$it/$directoryName" } }
            .forEach { path -> fileManager.removeItemAtPath(path, error = null) }

        importedFiles = emptyList()
        importedFonts = emptyList()
        pendingImportBatches = emptyList()
        pendingFolderScans = emptyList()
        pendingExternalOpen = null
        pendingCloudSync = null
        persistImportedFiles(emptyList())
        clearPendingIosExternalFileRemoval()
        val defaults = NSUserDefaults.standardUserDefaults
        listOf(
            "reader_ios_library_snapshot_v1",
            "reader_ios_reader_preferences_v1",
            IosSyncEnabledDefaultsKey,
        ).forEach(defaults::removeObjectForKey)
        val perBookPrefixes = listOf(
            IosPdfReaderStateDefaultsPrefix,
            IosPdfReaderSidecarTimestampDefaultsPrefix,
            IosPdfPageSliderVisibleDefaultsPrefix,
            IosEpubPageSliderVisibleDefaultsPrefix,
            IosEpubReaderStateDefaultsPrefix,
        )
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { key -> perBookPrefixes.any(key::startsWith) }
            .forEach(defaults::removeObjectForKey)
        localCloudDataClearEvent += 1
        latestNativeEvent = "Cloud and local library data cleared"
    }

    fun setCloudSyncHandlers(
        sync: (String) -> Unit,
        upload: (String) -> Unit,
    ) {
        cloudSyncHandler = sync
        cloudUploadHandler = upload
    }

    fun requestCloudSync(snapshotJson: String) {
        cloudSyncStatus = "Checking Google Drive…"
        cloudSyncHandler?.invoke(snapshotJson)
    }

    fun uploadCloudSnapshot(snapshotJson: String) {
        cloudUploadHandler?.invoke(snapshotJson)
    }

    fun completeCloudSync(
        remoteSnapshotJson: String?,
        downloadedBookIds: List<String>,
        downloadedBookPaths: List<String>,
        status: String,
    ) {
        pendingCloudSync = remoteSnapshotJson
            ?.takeIf(String::isNotBlank)
            ?.let { json ->
                IosPendingCloudSync(
                    remoteSnapshotJson = json,
                    downloadedBookPaths = downloadedBookIds.mapIndexedNotNull { index, id ->
                        downloadedBookPaths.getOrNull(index)?.let { id to it }
                    }.toMap(),
                )
            }
        cloudSyncStatus = status
        latestNativeEvent = status
        IosDiagnosticLogStore.record("CloudSync", status)
    }

    internal fun consumeCloudSnapshot() {
        pendingCloudSync = null
    }

    fun updateAccountState(
        uid: String?,
        displayName: String?,
        email: String?,
        appleLinked: Boolean,
        googleLinked: Boolean,
        googleDriveAuthorized: Boolean,
        status: String?,
        authToken: String? = null,
    ) {
        accountState = IosAccountState(
            uid = uid,
            displayName = displayName,
            email = email,
            providers = buildSet {
                if (appleLinked) add(AccountAuthProvider.APPLE)
                if (googleLinked) add(AccountAuthProvider.GOOGLE)
            },
            googleDriveAuthorized = googleDriveAuthorized,
            status = status,
            authToken = authToken,
            hasLoaded = true,
        )
        status?.let { IosDiagnosticLogStore.record("Account", it) }
    }

    /** Refreshable Firebase ID tokens stay on the native auth boundary. */
    fun updateAccountAuthToken(authToken: String?, expectedUid: String? = null) {
        // Firebase can finish a token request after a sign-out or account switch.
        // Keep that stale callback from re-enabling server-backed features for the
        // account that is no longer active.
        if (expectedUid != null && accountState.uid != expectedUid) return
        accountState = accountState.copy(authToken = authToken)
    }
}

internal object IosStoreKitProductIds {
    const val PRO_LIFETIME = "episteme_pro_lifetime"
    const val CREDITS_100 = "credits_100"
    const val CREDITS_300 = "credits_300"
    const val CREDITS_750 = "credits_750"
}

internal data class IosLocalStoreKitState(
    val available: Boolean = false,
    val proUnlocked: Boolean = false,
    val credits: Int = 0,
    val proPrice: String? = null,
    val creditPrices: Map<String, String> = emptyMap(),
    val status: String? = null,
)

internal data class IosAccountState(
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val providers: Set<AccountAuthProvider> = emptySet(),
    val googleDriveAuthorized: Boolean = false,
    val status: String? = null,
    val authToken: String? = null,
    val hasLoaded: Boolean = false,
) {
    val canSync: Boolean
        get() = canEnableGoogleDriveSync(providers, googleDriveAuthorized)
}

private data class IosSystemUiState(
    val statusHidden: Boolean,
    val navigationHidden: Boolean,
    val lightContent: Boolean,
    val backgroundArgb: Long,
    val edgeToEdge: Boolean
)

private enum class IosUtilityScreen {
    ACCOUNT,
    PRO,
    SETTINGS,
    DEVICES,
    AI_SETTINGS,
    LANGUAGE,
    FONTS,
    FEEDBACK,
    SUPPORT,
    ABOUT,
}

data class IosImportedFile(
    val name: String,
    val path: String,
    val sourceFolder: String = "",
    val contentId: String = "",
    val relativePath: String = "",
    val fileSize: Long = 0L,
    val lastModifiedTimestamp: Long = 0L,
)

data class IosPendingImportBatch(
    val files: List<IosImportedFile>,
    val failedCount: Int,
    val autoOpen: Boolean = true,
)

internal data class IosExternalOpen(
    val file: IosImportedFile,
    val addToLibrary: Boolean,
    val behavior: String,
)

private data class IosPendingExternalFileRemoval(
    val bookId: String,
    val path: String?,
)

private data class IosMobileLibraryNavigation(
    val shelfId: String?,
    val isAddingBooks: Boolean,
    val addBooksSource: AddBooksSource,
)

internal data class IosPendingCloudSync(
    val remoteSnapshotJson: String,
    val downloadedBookPaths: Map<String, String>,
)

internal data class IosFolderReplacement(
    val fileSize: Long,
    val lastModifiedTimestamp: Long,
)

private const val IosImportedFilesDefaultsKey = "reader_ios_imported_files_v1"
private const val IosImportsRelativePrefix = "Imports/"
private const val IosDocumentsRelativePrefix = "Documents/"
private const val IosCoversRelativePrefix = "Covers/"
private const val IosPdfReaderStateDefaultsPrefix = "reader_ios_pdf_state_v2_"
private const val IosPdfLegacyReaderStateDefaultsPrefix = "reader_ios_pdf_state_v1_"
private const val IosPdfReaderSidecarTimestampDefaultsPrefix = "reader_ios_pdf_sidecar_timestamp_v1_"
private val IosReaderJson = Json { ignoreUnknownKeys = true }
private const val IosPdfPageSliderVisibleDefaultsPrefix = "reader_ios_pdf_slider_visible_v1_"
private const val IosEpubPageSliderVisibleDefaultsPrefix = "reader_ios_epub_slider_visible_v1_"
private const val IosEpubReaderStateDefaultsPrefix = "reader_ios_epub_state_v1_"
private const val IosReaderBrightnessDefaultsKey = "reader_ios_reader_brightness_v1"
private const val IosReaderCustomBrightnessDefaultsKey = "reader_ios_reader_custom_brightness_v1"
private const val IosPdfToolbarHiddenDefaultsKey = "reader_ios_pdf_toolbar_hidden_v1"
private const val IosPdfToolbarOrderDefaultsKey = "reader_ios_pdf_toolbar_order_v1"
private const val IosPdfToolbarBottomDefaultsKey = "reader_ios_pdf_toolbar_bottom_v1"
private const val IosPdfTopTabStripVisibleDefaultsKey = "reader_ios_pdf_top_tab_strip_visible_v1"
private const val IosReaderAutoScrollSpeedDefaultsKey = "reader_ios_auto_scroll_speed_v1"
private const val IosReaderAutoScrollMinDefaultsKey = "reader_ios_auto_scroll_min_v1"
private const val IosReaderAutoScrollMaxDefaultsKey = "reader_ios_auto_scroll_max_v1"
private const val IosReaderAutoScrollSliderDefaultsKey = "reader_ios_auto_scroll_slider_v1"
private const val IosReaderAutoScrollMusicianDefaultsKey = "reader_ios_auto_scroll_musician_v1"
private const val IosPdfAutoScrollSpeedDefaultsKey = "reader_ios_pdf_auto_scroll_speed_v1"
private const val IosPdfAutoScrollMinDefaultsKey = "reader_ios_pdf_auto_scroll_min_v1"
private const val IosPdfAutoScrollMaxDefaultsKey = "reader_ios_pdf_auto_scroll_max_v1"
private const val IosPdfAutoScrollMusicianDefaultsKey = "reader_ios_pdf_auto_scroll_musician_v1"
private const val IosPdfAutoScrollSliderDefaultsKey = "reader_ios_pdf_auto_scroll_slider_v1"
private const val IosReaderOrientationDefaultsKey = "reader_ios_reader_orientation_v1"
private const val IosKeepScreenOnDefaultsKey = "reader_ios_keep_screen_on_v1"
private const val IosNativeVerticalRendererDefaultsKey = "reader_native_vertical_renderer"
private const val IosStylusOnlyModeDefaultsKey = "reader_ios_stylus_only_mode_v1"
private const val IosPendingExternalBookIdDefaultsKey = "reader_ios_pending_external_book_id_v1"
private const val IosPendingExternalPathDefaultsKey = "reader_ios_pending_external_path_v1"
private const val IosViewingShelfIdDefaultsKey = "reader_ios_viewing_shelf_id_v1"
private const val IosAddingBooksToShelfDefaultsKey = "reader_ios_adding_books_to_shelf_v1"
private const val IosAddBooksSourceDefaultsKey = "reader_ios_add_books_source_v1"
private const val IosUnifiedLibrarySectionDefaultsKey = "reader_ios_unified_library_section_v1"
private const val IosSyncEnabledDefaultsKey = "reader_ios_sync_enabled_v1"

private fun loadPendingIosExternalFileRemoval(): IosPendingExternalFileRemoval? {
    val defaults = NSUserDefaults.standardUserDefaults
    val bookId = defaults.stringForKey(IosPendingExternalBookIdDefaultsKey)
        ?.takeIf(String::isNotBlank)
        ?: return null
    val path = defaults.stringForKey(IosPendingExternalPathDefaultsKey)
        ?.resolvedIosImportedFilePath()
        ?.takeIf(String::isNotBlank)
    return IosPendingExternalFileRemoval(bookId, path)
}

private fun persistPendingIosExternalFileRemoval(book: BookItem) {
    val path = book.path?.stableIosImportedFilePath()?.takeIf(String::isNotBlank) ?: return
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(book.id, forKey = IosPendingExternalBookIdDefaultsKey)
    defaults.setObject(path, forKey = IosPendingExternalPathDefaultsKey)
}

private fun clearPendingIosExternalFileRemoval() {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.removeObjectForKey(IosPendingExternalBookIdDefaultsKey)
    defaults.removeObjectForKey(IosPendingExternalPathDefaultsKey)
}

private fun loadIosMobileLibraryNavigation(): IosMobileLibraryNavigation {
    val defaults = NSUserDefaults.standardUserDefaults
    val source = defaults.stringForKey(IosAddBooksSourceDefaultsKey)
        ?.let { runCatching { AddBooksSource.valueOf(it) }.getOrNull() }
        ?: AddBooksSource.UNSHELVED
    return IosMobileLibraryNavigation(
        shelfId = defaults.stringForKey(IosViewingShelfIdDefaultsKey),
        isAddingBooks = defaults.boolForKey(IosAddingBooksToShelfDefaultsKey),
        addBooksSource = source,
    )
}

private fun persistIosMobileLibraryNavigation(state: SharedReaderScreenState) {
    val defaults = NSUserDefaults.standardUserDefaults
    state.viewingShelfId?.let {
        defaults.setObject(it, forKey = IosViewingShelfIdDefaultsKey)
    } ?: defaults.removeObjectForKey(IosViewingShelfIdDefaultsKey)
    defaults.setBool(state.isAddingBooksToShelf, forKey = IosAddingBooksToShelfDefaultsKey)
    defaults.setObject(state.addBooksSource.name, forKey = IosAddBooksSourceDefaultsKey)
}

private fun loadIosUnifiedLibrarySection(): Int =
    NSUserDefaults.standardUserDefaults.integerForKey(IosUnifiedLibrarySectionDefaultsKey).toInt()

private fun persistIosUnifiedLibrarySection(section: Int) {
    NSUserDefaults.standardUserDefaults.setInteger(section.toLong(), forKey = IosUnifiedLibrarySectionDefaultsKey)
}

private const val IosUnifiedLibraryListViewDefaultsKey = "reader_ios_unified_library_list_view_v1"

private fun loadIosUnifiedLibraryListView(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(IosUnifiedLibraryListViewDefaultsKey)

private fun persistIosUnifiedLibraryListView(useListView: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(useListView, forKey = IosUnifiedLibraryListViewDefaultsKey)
}

private fun loadIosSyncEnabled(): Boolean {
    return NSUserDefaults.standardUserDefaults.boolForKey(IosSyncEnabledDefaultsKey)
}

private fun persistIosSyncEnabled(enabled: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosSyncEnabledDefaultsKey)
}

internal fun SharedLibrarySnapshot.withResolvedIosBookPaths(): SharedLibrarySnapshot {
    val resolvedBooks = books
        .map { book ->
            val resolvedPath = book.path?.resolvedIosImportedFilePath()
            book.copy(
                path = resolvedPath,
                coverImagePath = book.coverImagePath?.resolvedIosCoverPath(),
                isAvailable = resolvedPath?.startsWith("opds-pse://") == true ||
                    (!resolvedPath.isNullOrBlank() &&
                        NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)),
            )
        }
        .distinctBy { book -> book.path?.takeIf(String::isNotBlank)?.let { "path:$it" } ?: "id:${book.id}" }
    return copy(books = resolvedBooks)
}

internal fun SharedLibrarySnapshot.withStableIosBookPaths(): SharedLibrarySnapshot {
    val stableBooks = books
        .map { book ->
            book.copy(
                path = book.path?.stableIosImportedFilePath(),
                coverImagePath = book.coverImagePath?.stableIosCoverPath(),
            )
        }
        .distinctBy { book -> book.path?.takeIf(String::isNotBlank)?.let { "path:$it" } ?: "id:${book.id}" }
    return copy(books = stableBooks)
}

internal fun SharedLibrarySnapshot.withResolvedIosAudiobookPaths(): SharedLibrarySnapshot {
    val resolvedAudiobooks = audiobooks
        .map { audiobook -> audiobook.copy(filePath = audiobook.filePath.resolvedIosImportedFilePath()) }
        .distinctBy { it.bookId }
    return copy(audiobooks = resolvedAudiobooks)
}

internal fun SharedLibrarySnapshot.withStableIosAudiobookPaths(): SharedLibrarySnapshot {
    val stableAudiobooks = audiobooks
        .map { audiobook -> audiobook.copy(filePath = audiobook.filePath.stableIosImportedFilePath()) }
        .distinctBy { it.bookId }
    return copy(audiobooks = stableAudiobooks)
}

private fun loadIosReaderOrientation(): ReaderScreenOrientationMode {
    val name = NSUserDefaults.standardUserDefaults.stringForKey(IosReaderOrientationDefaultsKey)
    return ReaderScreenOrientationMode.entries.firstOrNull { it.name == name }
        ?: ReaderScreenOrientationMode.FOLLOW_SYSTEM
}

private fun persistIosReaderOrientation(mode: ReaderScreenOrientationMode) {
    NSUserDefaults.standardUserDefaults.setObject(mode.name, forKey = IosReaderOrientationDefaultsKey)
}

private const val IosCustomSleepTimerMinutesDefaultsKey = "custom_sleep_timers"

private fun loadIosCustomSleepTimerMinutes(): List<Int> {
    val stored = NSUserDefaults.standardUserDefaults.stringForKey(IosCustomSleepTimerMinutesDefaultsKey)
        .orEmpty()
        .split(',')
        .mapNotNull(String::toIntOrNull)
    return sanitizeCustomSleepTimerMinutes(stored)
}

private fun persistIosCustomSleepTimerMinutes(values: List<Int>) {
    NSUserDefaults.standardUserDefaults.setObject(
        sanitizeCustomSleepTimerMinutes(values).joinToString(","),
        forKey = IosCustomSleepTimerMinutesDefaultsKey,
    )
}

private fun loadIosReaderBrightness(): Float? {
    val stored = NSUserDefaults.standardUserDefaults.stringForKey(IosReaderBrightnessDefaultsKey) ?: return null
    return stored.toFloatOrNull()?.coerceIn(0.01f, 1f)
}

private fun loadIosReaderCustomBrightness(): Float {
    val defaults = NSUserDefaults.standardUserDefaults
    return defaults.stringForKey(IosReaderCustomBrightnessDefaultsKey)
        ?.toFloatOrNull()
        ?.let(::normalizeReaderBrightness)
        ?: defaults.stringForKey(IosReaderBrightnessDefaultsKey)
            ?.toFloatOrNull()
            ?.let(::normalizeReaderBrightness)
        ?: DefaultReaderCustomBrightness
}

private fun persistIosReaderBrightness(brightness: Float?, customBrightness: Float) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(brightness?.let(::normalizeReaderBrightness)?.toString() ?: "system", forKey = IosReaderBrightnessDefaultsKey)
    defaults.setObject(normalizeReaderBrightness(customBrightness).toString(), forKey = IosReaderCustomBrightnessDefaultsKey)
}

private const val IosLookupDictionaryServiceKey = "ios_reader_lookup_dictionary_service"
private const val IosLookupTranslateServiceKey = "ios_reader_lookup_translate_service"
private const val IosLookupSearchServiceKey = "ios_reader_lookup_search_service"

private fun loadIosReaderLookupServices():
    Triple<ReaderExternalLookupService, ReaderExternalLookupService, ReaderExternalLookupService> {
    return Triple(
        loadIosLookupService(IosLookupDictionaryServiceKey, ReaderExternalLookupService.SYSTEM),
        loadIosLookupService(IosLookupTranslateServiceKey, ReaderExternalLookupService.GOOGLE_TRANSLATE),
        loadIosLookupService(IosLookupSearchServiceKey, ReaderExternalLookupService.GOOGLE),
    )
}

private fun loadIosLookupService(key: String, default: ReaderExternalLookupService): ReaderExternalLookupService {
    val stored = NSUserDefaults.standardUserDefaults.stringForKey(key) ?: return default
    return ReaderExternalLookupService.fromId(stored)
}

private fun persistIosLookupService(key: String, service: ReaderExternalLookupService) {
    NSUserDefaults.standardUserDefaults.setObject(service.id, forKey = key)
}

private fun loadIosPdfToolbarPreferences(): PdfToolbarPreferences {
    val defaults = NSUserDefaults.standardUserDefaults
    val baseline = PdfToolbarPreferences()
    fun ids(key: String): List<String>? = defaults.stringForKey(key)?.split(',')?.filter(String::isNotBlank)
    return PdfToolbarPreferences(
        hiddenToolIds = ids(IosPdfToolbarHiddenDefaultsKey)?.toSet() ?: baseline.hiddenToolIds,
        toolOrder = ids(IosPdfToolbarOrderDefaultsKey)?.mapNotNull(PdfReaderTool::fromId)
            ?.takeIf { it.isNotEmpty() }
            ?: baseline.toolOrder,
        bottomToolIds = ids(IosPdfToolbarBottomDefaultsKey)?.toSet() ?: baseline.bottomToolIds,
    ).sanitized()
}

private fun persistIosPdfToolbarPreferences(preferences: PdfToolbarPreferences) {
    val sanitized = preferences.sanitized()
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(sanitized.hiddenToolIds.sorted().joinToString(","), forKey = IosPdfToolbarHiddenDefaultsKey)
    defaults.setObject(sanitized.toolOrder.joinToString(",") { it.id }, forKey = IosPdfToolbarOrderDefaultsKey)
    defaults.setObject(sanitized.bottomToolIds.sorted().joinToString(","), forKey = IosPdfToolbarBottomDefaultsKey)
}

private fun loadIosReaderAutoScrollProfile(): ReaderAutoScrollProfile {
    val defaults = NSUserDefaults.standardUserDefaults
    return ReaderAutoScrollProfile(
        speed = defaults.stringForKey(IosReaderAutoScrollSpeedDefaultsKey)
            ?.toFloatOrNull()
            ?.let(::migrateLegacyIosReaderAutoScrollSpeed)
            ?: 0.8f,
        minSpeed = defaults.stringForKey(IosReaderAutoScrollMinDefaultsKey)?.toFloatOrNull() ?: 0.1f,
        maxSpeed = defaults.stringForKey(IosReaderAutoScrollMaxDefaultsKey)?.toFloatOrNull() ?: 10f,
    ).sanitized()
}

private fun persistIosReaderAutoScrollProfile(profile: ReaderAutoScrollProfile) {
    val sanitized = profile.sanitized()
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(sanitized.speed.toString(), forKey = IosReaderAutoScrollSpeedDefaultsKey)
    defaults.setObject(sanitized.minSpeed.toString(), forKey = IosReaderAutoScrollMinDefaultsKey)
    defaults.setObject(sanitized.maxSpeed.toString(), forKey = IosReaderAutoScrollMaxDefaultsKey)
}

private fun loadIosReaderAutoScrollUseSlider(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(IosReaderAutoScrollSliderDefaultsKey)

private fun persistIosReaderAutoScrollUseSlider(useSlider: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(useSlider, forKey = IosReaderAutoScrollSliderDefaultsKey)
}

private fun loadIosReaderAutoScrollMusicianMode(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(IosReaderAutoScrollMusicianDefaultsKey)

private fun persistIosReaderAutoScrollMusicianMode(enabled: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosReaderAutoScrollMusicianDefaultsKey)
}

private fun loadIosPdfPageSliderVisible(bookId: String): Boolean {
    return NSUserDefaults.standardUserDefaults.boolForKey(IosPdfPageSliderVisibleDefaultsPrefix + bookId)
}

private fun persistIosPdfPageSliderVisible(bookId: String, visible: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(visible, forKey = IosPdfPageSliderVisibleDefaultsPrefix + bookId)
}

private fun loadIosEpubPageSliderVisible(bookId: String): Boolean {
    return NSUserDefaults.standardUserDefaults.boolForKey(IosEpubPageSliderVisibleDefaultsPrefix + bookId)
}

private fun persistIosEpubPageSliderVisible(bookId: String, visible: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(visible, forKey = IosEpubPageSliderVisibleDefaultsPrefix + bookId)
}

private fun loadIosPdfAutoScrollProfile(): PdfAutoScrollProfile {
    val defaults = NSUserDefaults.standardUserDefaults
    return PdfAutoScrollProfile(
        speed = defaults.stringForKey(IosPdfAutoScrollSpeedDefaultsKey)?.toFloatOrNull() ?: 3f,
        minSpeed = defaults.stringForKey(IosPdfAutoScrollMinDefaultsKey)?.toFloatOrNull() ?: 0.1f,
        maxSpeed = defaults.stringForKey(IosPdfAutoScrollMaxDefaultsKey)?.toFloatOrNull() ?: 10f,
    ).sanitized()
}

private fun persistIosPdfAutoScrollProfile(profile: PdfAutoScrollProfile) {
    val sanitized = profile.sanitized()
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(sanitized.speed.toString(), forKey = IosPdfAutoScrollSpeedDefaultsKey)
    defaults.setObject(sanitized.minSpeed.toString(), forKey = IosPdfAutoScrollMinDefaultsKey)
    defaults.setObject(sanitized.maxSpeed.toString(), forKey = IosPdfAutoScrollMaxDefaultsKey)
}

private fun loadIosKeepScreenOn(): Boolean {
    return NSUserDefaults.standardUserDefaults.boolForKey(IosKeepScreenOnDefaultsKey)
}

private fun persistIosKeepScreenOn(enabled: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosKeepScreenOnDefaultsKey)
}

private fun loadIosNativeVerticalRenderer(): Boolean {
    return NSUserDefaults.standardUserDefaults.boolForKey(IosNativeVerticalRendererDefaultsKey)
}

private fun persistIosNativeVerticalRenderer(enabled: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosNativeVerticalRendererDefaultsKey)
}

private fun loadIosStylusOnlyMode(): Boolean {
    return NSUserDefaults.standardUserDefaults.boolForKey(IosStylusOnlyModeDefaultsKey)
}

private fun persistIosStylusOnlyMode(enabled: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosStylusOnlyModeDefaultsKey)
}

private fun loadIosPdfTopTabStripVisible(): Boolean {
    val defaults = NSUserDefaults.standardUserDefaults
    return if (defaults.objectForKey(IosPdfTopTabStripVisibleDefaultsKey) == null) {
        true
    } else {
        defaults.boolForKey(IosPdfTopTabStripVisibleDefaultsKey)
    }
}

private fun persistIosPdfTopTabStripVisible(visible: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(visible, forKey = IosPdfTopTabStripVisibleDefaultsKey)
}

private fun loadPersistedImportedFiles(): List<IosImportedFile> {
    val encoded = NSUserDefaults.standardUserDefaults.stringForKey(IosImportedFilesDefaultsKey) ?: return emptyList()
    return encoded
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.splitEscapedTab()
            if (parts.size !in 2..7) return@mapNotNull null
            val name = parts[0].unescapePersistedValue()
            val resolvedPath = parts[1].unescapePersistedValue().resolvedIosImportedFilePath()
            val sourceFolder = parts.getOrNull(2)
                ?.unescapePersistedValue()
                ?.takeUnless { it == "iOS import" }
                .orEmpty()
            val contentId = parts.getOrNull(3)?.unescapePersistedValue().orEmpty()
            val relativePath = parts.getOrNull(4)?.unescapePersistedValue().orEmpty()
            val fileSize = parts.getOrNull(5)?.unescapePersistedValue()?.toLongOrNull() ?: 0L
            val lastModifiedTimestamp = parts.getOrNull(6)?.unescapePersistedValue()?.toLongOrNull() ?: 0L
            IosImportedFile(
                name = name,
                path = resolvedPath,
                sourceFolder = sourceFolder,
                contentId = contentId,
                relativePath = relativePath,
                fileSize = fileSize,
                lastModifiedTimestamp = lastModifiedTimestamp,
            )
        }
        .distinctBy { it.path }
        .toList()
}

private fun persistImportedFiles(files: List<IosImportedFile>) {
    val encoded = files.joinToString("\n") { file ->
        listOf(
            file.name,
            file.path.stableIosImportedFilePath(),
            file.sourceFolder,
            file.contentId,
            file.relativePath,
            file.fileSize.toString(),
            file.lastModifiedTimestamp.toString(),
        ).joinToString("\t") { it.escapePersistedValue() }
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
    val canonicalPath = canonicalIosFilePath()
    if (NSFileManager.defaultManager.fileExistsAtPath(canonicalPath)) return canonicalPath
    val importedFileName = substringAfterLast('/').takeIf { it.isNotBlank() } ?: return this
    return listOfNotNull(
        iosImportsDirectoryPath()?.let { "$it/$importedFileName" },
        iosDocumentsDirectoryPath()?.let { "$it/$importedFileName" }
    ).firstOrNull(NSFileManager.defaultManager::fileExistsAtPath) ?: this
}

private fun String.stableIosImportedFilePath(): String {
    if (substringAfterLast('/').isBlank()) return this
    val canonicalPath = canonicalIosFilePath()
    val importsPath = iosImportsDirectoryPath()?.canonicalIosFilePath() ?: return canonicalPath
    return if (canonicalPath.startsWith("$importsPath/")) {
        IosImportsRelativePrefix + canonicalPath.removePrefix("$importsPath/")
    } else {
        val documentsPath = iosDocumentsDirectoryPath()?.canonicalIosFilePath()
        if (documentsPath != null && canonicalPath.startsWith("$documentsPath/")) {
            IosDocumentsRelativePrefix + canonicalPath.removePrefix("$documentsPath/")
        } else {
            canonicalPath
        }
    }
}

private fun String.canonicalIosFilePath(): String =
    when {
        startsWith("/private/var/") -> removePrefix("/private")
        startsWith("/private/tmp/") -> removePrefix("/private")
        else -> this
    }

private fun String.resolvedIosCoverPath(): String {
    if (startsWith(IosCoversRelativePrefix)) {
        return iosCoversDirectoryPath()?.let { coversPath ->
            "$coversPath/${substringAfter(IosCoversRelativePrefix)}"
        } ?: this
    }
    val canonicalPath = canonicalIosFilePath()
    if (NSFileManager.defaultManager.fileExistsAtPath(canonicalPath)) return canonicalPath
    val fileName = substringAfterLast('/').takeIf { it.isNotBlank() } ?: return this
    return iosCoversDirectoryPath()
        ?.let { "$it/$fileName" }
        ?.takeIf(NSFileManager.defaultManager::fileExistsAtPath)
        ?: this
}

private fun String.stableIosCoverPath(): String {
    val canonicalPath = canonicalIosFilePath()
    val coversPath = iosCoversDirectoryPath()?.canonicalIosFilePath() ?: return canonicalPath
    return if (canonicalPath.startsWith("$coversPath/")) {
        IosCoversRelativePrefix + canonicalPath.removePrefix("$coversPath/")
    } else {
        canonicalPath
    }
}

private fun iosDocumentsDirectoryPath(): String? {
    return (NSFileManager.defaultManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL)?.path
}

private fun iosCoversDirectoryPath(): String? {
    val appSupport = (NSFileManager.defaultManager.URLsForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomains = NSUserDomainMask,
    ).firstOrNull() as? NSURL) ?: return null
    return appSupport.URLByAppendingPathComponent("Covers", isDirectory = true)?.path?.canonicalIosFilePath()
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
    loadPersistedIosPdfReaderStateFromDefaults(book)?.let { return it }
    val sidecarData = IosPdfCloudSidecarStore.read(book.id)
    val sidecar = SharedPdfCloudSidecarCodec.decode(
        rawDataJson = sidecarData,
        fallbackPageCount = 1,
        fallbackPageIndex = book.lastPageIndex ?: 0,
    ) ?: return null
    val legacyBookmarks = decodeLegacyIosPdfBookmarks(sidecarData)
    val restored = sidecar.readerState ?: SharedPdfReaderState.initial(
        pageCount = 1,
        initialPageIndex = book.lastPageIndex ?: 0,
    ).copy(
        bookmarks = legacyBookmarks,
        annotations = sidecar.annotations,
        richTextDocumentJson = sidecar.richTextDocumentJson.orEmpty(),
    )
    persistIosPdfReaderState(book, restored)
    sidecar.modifiedTimestamp.takeIf { it > 0L }?.let { timestamp ->
        NSUserDefaults.standardUserDefaults.setInteger(
            value = timestamp,
            forKey = book.iosPdfSidecarTimestampKey(),
        )
    }
    return restored
}

private fun loadPersistedIosPdfReaderStateFromDefaults(book: BookItem): SharedPdfReaderState? {
    val defaults = NSUserDefaults.standardUserDefaults
    val stableKey = book.iosPdfReaderStateKey()
    book.iosPdfReaderStateDefaultsKeys().forEach { key ->
        val encoded = defaults.stringForKey(key) ?: return@forEach
        val decoded = SharedPdfReaderStateSerializer.decode(
            raw = encoded,
            fallbackPageCount = 1,
            fallbackPageIndex = book.lastPageIndex ?: 0,
        ) ?: return@forEach
        if (key != stableKey) {
            // v1 used the path as the key when available.  Paths can change
            // after an import/move, so copy the first valid legacy value to
            // the id-based key before removing the old alias.
            defaults.setObject(encoded, forKey = stableKey)
            defaults.removeObjectForKey(key)
        }
        return decoded
    }
    return null
}

private fun persistIosPdfReaderState(book: BookItem, state: SharedPdfReaderState) {
    val encoded = SharedPdfReaderStateSerializer.encode(state)
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = book.iosPdfReaderStateKey())
}

private fun BookItem.iosPdfReaderStateKey(): String =
    IosPdfReaderStateDefaultsPrefix + id.normalizedId()

/** Stable id key first, followed by every v1 key that could contain this book. */
internal fun BookItem.iosPdfReaderStateDefaultsKeys(): List<String> {
    return buildList {
        add(IosPdfReaderStateDefaultsPrefix + id.normalizedId())
        add(IosPdfLegacyReaderStateDefaultsPrefix + id.normalizedId())
        path?.let { add(IosPdfLegacyReaderStateDefaultsPrefix + it.normalizedId()) }
    }.distinct()
}

private fun BookItem.iosPdfSidecarTimestampKey(): String {
    return IosPdfReaderSidecarTimestampDefaultsPrefix + id.normalizedId()
}

private fun BookItem.iosPdfSourceFingerprint(): String {
    return "${fileSize.coerceAtLeast(0L)}:${fileContentModifiedTimestamp.coerceAtLeast(0L)}"
}

/**
 * Converts the Android PDF bookmark field when an old sidecar or library
 * snapshot is opened on iOS.  PDF bookmarks historically lived outside the
 * shared BookItem model, so unknown fields must be read before the snapshot
 * decoder discards them.
 */
internal fun decodeLegacyIosPdfBookmarks(rawDataJson: String?): List<SharedPdfBookmark> {
    val root = runCatching {
        IosReaderJson.parseToJsonElement(rawDataJson.orEmpty()).jsonObject
    }.getOrNull() ?: return emptyList()
    val data = root["data"]
        ?.takeUnless { it is JsonNull }
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?: root
    val bookmarkElement = data["bookmarksJson"] ?: data["bookmarks"] ?: return emptyList()
    val rawBookmarks = runCatching { bookmarkElement.jsonPrimitive.contentOrNull }.getOrNull()
        ?: bookmarkElement.toString()
    return LegacyPdfPageBookmarkCodec.decode(rawBookmarks)
        .asSequence()
        .filter { it.pageIndex >= 0 }
        .map { bookmark ->
            SharedPdfBookmark(
                pageIndex = bookmark.pageIndex,
                label = bookmark.title.trim().ifBlank { "Page ${bookmark.pageIndex + 1}" },
                createdAt = 0L,
            )
        }
        .distinctBy { it.pageIndex }
        .sortedBy { it.pageIndex }
        .toList()
}

internal fun decodeLegacyIosPdfBookmarksByBookId(rawSnapshotJson: String?): Map<String, List<SharedPdfBookmark>> {
    val root = runCatching {
        IosReaderJson.parseToJsonElement(rawSnapshotJson.orEmpty()).jsonObject
    }.getOrNull() ?: return emptyMap()
    return root["books"]
        ?.let { runCatching { it.jsonArray }.getOrNull() }
        .orEmpty()
        .mapNotNull { bookElement ->
            val book = runCatching { bookElement.jsonObject }.getOrNull() ?: return@mapNotNull null
            val bookId = book["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val bookmarks = decodeLegacyIosPdfBookmarks(bookElement.toString())
            bookmarks.takeIf { it.isNotEmpty() }?.let { bookId to it }
        }
        .toMap()
}

/**
 * Adds portable PDF sidecars to the ordinary library snapshot.  The library
 * record remains the source of truth for book metadata; the sidecar carries
 * the PDF document state that is too rich for that record (annotations,
 * inserted pages, and rich text).
 */
private fun SharedReaderScreenState.toIosCloudSnapshot(): SharedLibrarySnapshot {
    val snapshot = toSharedMobileLibrarySnapshot()
    val sidecars = rawLibraryBooks.asSequence()
        .filter { it.type == FileType.PDF }
        .mapNotNull { book ->
            val existingData = IosPdfCloudSidecarStore.read(book.id)
            val existingPayload = SharedPdfCloudSidecarCodec.decode(
                rawDataJson = existingData,
                fallbackPageCount = 1,
                fallbackPageIndex = book.lastPageIndex ?: 0,
            )
            val persistedState = loadPersistedIosPdfReaderStateFromDefaults(book)
                ?: existingPayload?.readerState
            if (persistedState == null && existingData.isNullOrBlank()) return@mapNotNull null
            val timestamp = maxOf(
                NSUserDefaults.standardUserDefaults.integerForKey(book.iosPdfSidecarTimestampKey()),
                existingPayload?.modifiedTimestamp ?: 0L,
                book.readingPositionModifiedTimestamp,
                book.timestamp,
                1L,
            )
            val data = persistedState?.let { state ->
                SharedPdfCloudSidecarCodec.encode(
                    bookId = book.id,
                    state = state,
                    sourceFingerprint = book.iosPdfSourceFingerprint(),
                    modifiedTimestamp = timestamp,
                    existingDataJson = existingData,
                )
            } ?: existingData ?: return@mapNotNull null
            SharedPdfCloudSidecarSnapshot(
                bookId = book.id,
                timestamp = timestamp,
                data = data,
            )
        }
        .toList()
    return snapshot.copy(pdfSidecars = sidecars)
}

private fun loadPersistedIosEpubBookState(book: BookItem): BookItem {
    val encoded = NSUserDefaults.standardUserDefaults.stringForKey(book.iosEpubReaderStateKey()) ?: return book
    val stored = SharedLibrarySnapshotJson.decodeOrEmpty(encoded).books.firstOrNull() ?: return book
    return book.withNewerReaderSession(stored)
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
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    onRefreshFolders: () -> Unit,
    onImportFonts: () -> Unit,
    onImportCover: () -> Unit,
    onRemoveFolder: (String) -> Unit,
): UIViewController = ComposeUIViewController {
    ReaderIosApp(
        bridge = bridge,
        onImportBooks = onImportBooks,
        onImportFolder = onImportFolder,
        onRefreshFolders = onRefreshFolders,
        onImportFonts = onImportFonts,
        onImportCover = onImportCover,
        onRemoveFolder = onRemoveFolder,
    )
}

@Composable
private fun ReaderIosApp(
    bridge: ReaderIosBridge,
    onImportBooks: () -> Unit,
    onImportFolder: () -> Unit,
    onRefreshFolders: () -> Unit,
    onImportFonts: () -> Unit,
    onImportCover: () -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    val persistedLibrary = remember { loadIosLibrarySnapshot() }
    val loadedState = remember {
        persistedLibrary.toSharedMobileReaderState()
            .withoutLegacyIosImportsFolder()
            .copy(isSyncEnabled = loadIosSyncEnabled())
    }
    val pendingExternalCleanup = remember { loadPendingIosExternalFileRemoval() }
    val persistedState = remember {
        pendingExternalCleanup?.let { pending ->
            pending.path?.let { bridge.removeImportedFiles(listOf(it)) }
            clearPendingIosExternalFileRemoval()
            clearIosReaderSession()
            loadedState.removeIosBooks(setOf(pending.bookId))
        } ?: loadedState
    }
    val restoredLibraryNavigation = remember { loadIosMobileLibraryNavigation() }
    val navigatedState = remember {
        persistedState.withRestoredMobileLibraryNavigation(
            restoredShelfId = restoredLibraryNavigation.shelfId,
            restoredIsAddingBooks = restoredLibraryNavigation.isAddingBooks,
            restoredAddBooksSource = restoredLibraryNavigation.addBooksSource,
        )
    }
    val persistedPdfSplitWorkspace = remember { loadIosPdfSplitWorkspace() }
    val restoredPdfSplitRecovery = remember {
        if (persistedPdfSplitWorkspace.isOpen && navigatedState.rawLibraryBooks.isEmpty()) {
            // The library snapshot can be hydrated after the workspace
            // preference during a cold start. Keep the durable split request
            // pending until we have a real inventory to classify as missing.
            IosPdfSplitWorkspaceRecovery(
                workspace = PdfSplitWorkspaceState(),
                missingPanes = emptySet(),
            )
        } else {
            restoreIosPdfSplitWorkspaceWithRecovery(
                persisted = persistedPdfSplitWorkspace,
                books = navigatedState.rawLibraryBooks,
            )
        }
    }
    val restoredPdfSplitWorkspace = restoredPdfSplitRecovery.workspace
    val restoredReaderBook = remember {
        loadIosReaderSessionBook(navigatedState.rawLibraryBooks)
    }
    val initialReaderBook = remember(restoredPdfSplitWorkspace, restoredReaderBook) {
        restoredPdfSplitWorkspace.exitTargetDocument
            ?.let { resolveIosPdfSplitBook(it, navigatedState.rawLibraryBooks) }
            ?: restoredReaderBook
    }
    var state by remember {
        mutableStateOf(initialReaderBook?.let(navigatedState::withRestoredMobileReaderSession) ?: navigatedState)
    }
    val readerAiSettingsStore = remember { IosReaderAiSettingsStore() }
    var readerAiSettings by remember { mutableStateOf(readerAiSettingsStore.load()) }
    val effectiveReaderAiSettings = readerAiSettings.copy(
        hideReaderAiFeatures = readerAiSettings.hideReaderAiFeatures || state.hideReaderAi,
        serverBackedReaderAiFeatures = bridge.accountState.uid != null,
        serverBackedCloudTts = bridge.accountState.uid != null,
    ).sanitized()
    val readerAiAdapter = remember(
        bridge,
        effectiveReaderAiSettings,
        bridge.accountState.uid,
        bridge.accountState.authToken,
        state.isProUser,
        state.credits,
    ) {
        IosReaderAiAdapter(
            settingsProvider = { effectiveReaderAiSettings },
            accountStateProvider = {
                IosReaderAiAccountState(
                    isSignedIn = bridge.accountState.uid != null,
                    isProUser = state.isProUser,
                    credits = state.credits,
                )
            },
            authTokenProvider = { bridge.accountState.authToken },
            onUsageReported = { usage ->
                usage.freeRemaining?.let { remaining ->
                    state = state.copy(credits = remaining.coerceAtLeast(0))
                }
            },
        )
    }
    val readerCloudTts = remember { IosSharedMobileCloudTts() }
    DisposableEffect(readerCloudTts) {
        onDispose { readerCloudTts.release() }
    }
    fun updateCloudTtsMode(enabled: Boolean) {
        val updated = readerAiSettings.copy(
            ttsModel = if (enabled) com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID else "",
        ).sanitized()
        readerAiSettings = updated
        readerAiSettingsStore.save(updated)
    }
    fun updateCloudTtsVoice(identifier: String) {
        val updated = readerAiSettings.copy(ttsSpeakerId = identifier).sanitized()
        readerAiSettings = updated
        readerAiSettingsStore.save(updated)
    }
    LaunchedEffect(
        effectiveReaderAiSettings,
        bridge.accountState.uid,
        bridge.accountState.authToken,
        state.isProUser,
        state.credits,
    ) {
        readerCloudTts.configure(
            settings = effectiveReaderAiSettings,
            isSignedIn = bridge.accountState.uid != null,
            isProUser = state.isProUser,
            credits = state.credits,
            authToken = bridge.accountState.authToken,
            workerUrl = IOS_READER_AI_WORKER_URL,
        )
    }
    var readerExtrasState by remember { mutableStateOf(com.aryan.reader.shared.ReaderExtrasState()) }
    var readerAiJob by remember { mutableStateOf<Job?>(null) }
    val readerAiAvailable = readerAiAdapter.isAvailable
    LaunchedEffect(state) {
        persistIosLibrarySnapshot(state)
    }
    var pdfSplitWorkspace by remember { mutableStateOf(restoredPdfSplitWorkspace) }
    var pendingPdfSplitWorkspaceRestore by remember {
        mutableStateOf(persistedPdfSplitWorkspace.takeIf {
            it.isOpen && !restoredPdfSplitWorkspace.isOpen && !restoredPdfSplitRecovery.hasMissingPanes
        })
    }
    LaunchedEffect(bridge.localCloudDataClearEvent) {
        if (bridge.localCloudDataClearEvent <= 0) return@LaunchedEffect
        clearIosReaderSession()
        pdfSplitWorkspace = PdfSplitWorkspaceState()
        pendingPdfSplitWorkspaceRestore = null
        state = state.copy(
            selectedBookId = null,
            selectedUriString = null,
            selectedFileType = null,
            audiobooks = emptyList(),
            shelves = emptyList(),
            syncedFolders = emptyList(),
            isSyncEnabled = false,
            isFolderSyncEnabled = false,
            selectedBookIds = emptySet(),
            selectedShelfIds = emptySet(),
            booksAvailableForAdding = emptyList(),
            downloadingBookIds = emptySet(),
            uploadingBookIds = emptySet(),
            recentBooks = emptyList(),
            libraryBooks = emptyList(),
            rawLibraryBooks = emptyList(),
            cloudBookTombstones = emptyList(),
            pinnedHomeBookIds = emptySet(),
            pinnedLibraryBookIds = emptySet(),
            openTabIds = emptyList(),
            openTabs = emptyList(),
            activeTabBookId = null,
            customFonts = emptyList(),
            allTags = emptyList(),
        )
    }
    var splitRecoveryMessagePending by remember {
        mutableStateOf(restoredPdfSplitRecovery.hasMissingPanes)
    }
    var splitRecoveryHadSurvivor by remember {
        mutableStateOf(restoredPdfSplitRecovery.survivingDocument != null)
    }
    LaunchedEffect(pdfSplitWorkspace, pendingPdfSplitWorkspaceRestore) {
        // Do not erase a split preference while waiting for the first library
        // snapshot. Once a user action or a completed restore clears the
        // pending value, closed workspaces are removed normally.
        if (pdfSplitWorkspace.isOpen || pendingPdfSplitWorkspaceRestore == null) {
            persistIosPdfSplitWorkspace(pdfSplitWorkspace)
        }
    }
    LaunchedEffect(state.viewingShelfId, state.isAddingBooksToShelf, state.addBooksSource) {
        persistIosMobileLibraryNavigation(state)
    }
    val audiobookPlayer = remember { IosAudiobookPlayback(bridge) }
    val ttsListenController = remember { IosBookTtsListeningController() }
    DisposableEffect(ttsListenController) { onDispose(ttsListenController::release) }
    val audiobookPlaybackSnapshot = bridge.audiobookPlaybackSnapshot
    var lastAudiobookPersistAt by remember { mutableStateOf(0L) }
    LaunchedEffect(audiobookPlaybackSnapshot) {
        val snapshot = audiobookPlaybackSnapshot
        val bookId = snapshot.bookId ?: return@LaunchedEffect
        val now = currentTimestamp()
        val shouldPersist = !snapshot.isPlaying || (now - lastAudiobookPersistAt) >= 5_000L
        if (snapshot.connected && shouldPersist) {
            lastAudiobookPersistAt = now
            state = state.withAudiobookPosition(
                bookId = bookId,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                speed = snapshot.speed,
                lastListenedAt = now,
            )
        }
    }
    LaunchedEffect(state.isSyncEnabled) {
        persistIosSyncEnabled(state.isSyncEnabled)
    }
    LaunchedEffect(bridge.importedFonts) {
        if (bridge.importedFonts != state.customFonts) {
            state = state.reduce(AppAction.CustomFontsChanged(bridge.importedFonts))
        }
    }
    LaunchedEffect(bridge.localStoreKitState) {
        val store = bridge.localStoreKitState
        if (store.available && (state.isProUser != store.proUnlocked || state.credits != store.credits)) {
            state = state.copy(isProUser = store.proUnlocked, credits = store.credits)
        }
    }
    LaunchedEffect(bridge.accountState) {
        val account = bridge.accountState
        state = state.copy(
            currentUser = account.uid?.let {
                UserData(
                    uid = it,
                    displayName = account.displayName,
                    photoUrl = null,
                    email = account.email,
                )
            },
            isSyncEnabled = if (account.hasLoaded) {
                state.isSyncEnabled && account.canSync
            } else {
                state.isSyncEnabled
            },
        )
    }
    var selectedPage by remember {
        mutableStateOf(
            SharedMobileMainDestination.entries.getOrElse(state.mainScreenStartPage) {
                SharedMobileMainDestination.HOME
            }
        )
    }
    var selectedLibraryTab by remember {
        mutableStateOf(
            SharedMobileLibraryTab.entries.getOrElse(state.libraryScreenStartPage) {
                SharedMobileLibraryTab.BOOKS
            }
        )
    }
    var utilityScreen by remember { mutableStateOf<IosUtilityScreen?>(null) }
    var languageReturnScreen by remember { mutableStateOf<IosUtilityScreen?>(null) }
    var settingsDestination by remember { mutableStateOf(SharedSettingsDestination.ROOT) }
    var settingsQuery by remember { mutableStateOf("") }
    var showAppThemePanel by remember { mutableStateOf(false) }
    var showRecentLimitDialog by remember { mutableStateOf(false) }
    var annotationExportBook by remember { mutableStateOf<BookItem?>(null) }
    var customSleepTimerMinutes by remember { mutableStateOf(loadIosCustomSleepTimerMinutes()) }
    var showExternalFileBehaviorDialog by remember { mutableStateOf(false) }
    var showStrictFilterConfirmation by remember { mutableStateOf(false) }
    var showClearReflowCacheConfirmation by remember { mutableStateOf(false) }
    var showClearCloudLocalDataConfirmation by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var showTtsSettings by remember { mutableStateOf(false) }
    val settingsTts = rememberSharedMobileEpubLocalTts()
    var showDictionarySettingsSheet by remember { mutableStateOf(false) }
    val initialLookupServices = remember {
        loadIosReaderLookupServices().also { (dictionary, translate, search) ->
            IosReaderLookupServices.dictionary = dictionary
            IosReaderLookupServices.translate = translate
            IosReaderLookupServices.search = search
        }
    }
    var lookupDictionaryService by remember { mutableStateOf(initialLookupServices.first) }
    var lookupTranslateService by remember { mutableStateOf(initialLookupServices.second) }
    var lookupSearchService by remember { mutableStateOf(initialLookupServices.third) }
    var pdfReflowProgress by remember { mutableStateOf<Float?>(null) }
    var activeReaderBook by remember { mutableStateOf(initialReaderBook) }
    var pdfSplitPickerTarget by remember { mutableStateOf<IosPdfSplitPickerTarget?>(null) }
    LaunchedEffect(state.rawLibraryBooks, pendingPdfSplitWorkspaceRestore) {
        val pending = pendingPdfSplitWorkspaceRestore ?: return@LaunchedEffect
        if (pdfSplitWorkspace.isOpen) return@LaunchedEffect
        if (state.rawLibraryBooks.isEmpty()) return@LaunchedEffect
        val restored = restoreIosPdfSplitWorkspaceWithRecovery(pending, state.rawLibraryBooks)
        if (restored.workspace.isOpen || restored.hasMissingPanes) {
            pdfSplitWorkspace = restored.workspace
            pendingPdfSplitWorkspaceRestore = null
            splitRecoveryMessagePending = splitRecoveryMessagePending || restored.hasMissingPanes
            splitRecoveryHadSurvivor = restored.survivingDocument != null
            restored.survivingDocument?.let { exitTarget ->
                resolveIosPdfSplitBook(exitTarget, state.rawLibraryBooks)?.let { focusedBook ->
                    activeReaderBook = focusedBook
                    persistIosReaderSession(focusedBook)
                }
            }
        }
    }
    var activeTemporaryBookId by remember { mutableStateOf<String?>(null) }
    var activeTemporaryBookPath by remember { mutableStateOf<String?>(null) }
    var activeExternalBookId by remember { mutableStateOf<String?>(null) }
    var activeExternalBehavior by remember { mutableStateOf<String?>(null) }
    var pendingExternalClosePrompt by remember { mutableStateOf<BookItem?>(null) }
    var pendingUnavailableBookId by remember { mutableStateOf<String?>(null) }
    var readerBrightness by remember { mutableStateOf(loadIosReaderBrightness()) }
    var readerCustomBrightness by remember { mutableStateOf(loadIosReaderCustomBrightness()) }
    var pdfToolbarPreferences by remember { mutableStateOf(loadIosPdfToolbarPreferences()) }
    var pdfOcrLanguage by remember { mutableStateOf(loadIosPdfOcrLanguage()) }
    var pdfTopTabStripVisible by remember { mutableStateOf(loadIosPdfTopTabStripVisible()) }
    var readerAutoScrollProfile by remember { mutableStateOf(loadIosReaderAutoScrollProfile()) }
    var readerAutoScrollUseSlider by remember { mutableStateOf(loadIosReaderAutoScrollUseSlider()) }
    var readerAutoScrollMusicianMode by remember { mutableStateOf(loadIosReaderAutoScrollMusicianMode()) }
    var pdfAutoScrollProfile by remember { mutableStateOf(loadIosPdfAutoScrollProfile()) }
    var pdfAutoScrollMusicianMode by remember {
        mutableStateOf(NSUserDefaults.standardUserDefaults.boolForKey(IosPdfAutoScrollMusicianDefaultsKey))
    }
    var pdfAutoScrollUseSlider by remember {
        mutableStateOf(NSUserDefaults.standardUserDefaults.boolForKey(IosPdfAutoScrollSliderDefaultsKey))
    }
    var readerOrientation by remember { mutableStateOf(loadIosReaderOrientation()) }
    var stringResolver by remember { mutableStateOf(SharedStringResolver()) }
    LaunchedEffect(state.appLanguageTag) {
        stringResolver = loadIosStringResolver(state.appLanguageTag)
    }
    val opdsRepository = remember { IosOpdsRepository(folderFileAdditionHandler = bridge::addFolderManagedFile) }
    val opdsController = remember {
        SharedOpdsController(
            repository = opdsRepository,
            idFactory = { IosOpdsCatalogIds.next() }
        )
    }
    var opdsState by remember { mutableStateOf(opdsController.state) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pdfSidecarWriteJobs = remember { mutableMapOf<String, Job>() }

    fun selectMainPage(page: SharedMobileMainDestination) {
        selectedPage = page
        state = state.copy(mainScreenStartPage = page.ordinal)
    }

    fun selectLibraryTab(tab: SharedMobileLibraryTab) {
        selectedLibraryTab = tab
        state = state.copy(libraryScreenStartPage = tab.ordinal)
    }

    fun showMessage(message: String) {
        state = state.withMessage(message)
        bridge.recordNativeEvent(message)
    }

    val cloudLocalDataClearStatus = bridge.cloudLocalDataClearStatus
    val localizedCloudLocalDataClearStatus = if (cloudLocalDataClearStatus == null) {
        null
    } else {
        iosOperationStatusText(cloudLocalDataClearStatus)
    }
    LaunchedEffect(bridge.cloudLocalDataClearStatus) {
        localizedCloudLocalDataClearStatus?.let(::showMessage)
    }

    fun dismissReaderAiResult() {
        readerAiJob?.cancel()
        readerAiJob = null
        readerExtrasState = readerExtrasState.copy(
            aiResult = com.aryan.reader.shared.ReaderAiResultState()
        )
    }

    fun runReaderAiAction(feature: ReaderAiFeature, text: String) {
        readerAiJob?.cancel()
        readerAiJob = null
        val input = text.trim()
        if (input.isBlank()) {
            readerExtrasState = readerExtrasState.copy(
                aiResult = com.aryan.reader.shared.ReaderAiResultState(
                    title = feature.displayName,
                    errorMessage = "There is no reading context for this action.",
                )
            )
            return
        }
        readerExtrasState = readerExtrasState.copy(
            aiResult = com.aryan.reader.shared.ReaderAiResultState(
                title = feature.displayName,
                isLoading = true,
            )
        )
        readerAiJob = scope.launch {
            val result = when (feature) {
                ReaderAiFeature.DEFINE -> readerAiAdapter.defineStreaming(input, onUpdate = { chunk ->
                            readerExtrasState = readerExtrasState.copy(
                        aiResult = readerExtrasState.aiResult.copy(text = readerExtrasState.aiResult.text + chunk)
                    )
                }).let { result ->
                    AiDefinitionResult(definition = result.definition, error = result.error)
                }
                ReaderAiFeature.SUMMARIZE -> readerAiAdapter.summarizeStreaming(
                    input,
                    onUsageReceived = { _, freeRemaining ->
                        freeRemaining?.let { state = state.copy(credits = it.coerceAtLeast(0)) }
                    },
                    onUpdate = { chunk ->
                        readerExtrasState = readerExtrasState.copy(
                            aiResult = readerExtrasState.aiResult.copy(text = readerExtrasState.aiResult.text + chunk)
                        )
                    },
                )
                ReaderAiFeature.RECAP -> readerAiAdapter.recap(input)
            }
            val textResult = when (result) {
                is AiDefinitionResult -> result.definition.orEmpty()
                is SummarizationResult -> result.summary.orEmpty()
                is RecapResult -> result.recap.orEmpty()
                else -> ""
            }
            val error = when (result) {
                is AiDefinitionResult -> result.error
                is SummarizationResult -> result.error
                is RecapResult -> result.error
                else -> "AI request failed."
            }
            readerExtrasState = readerExtrasState.copy(
                aiResult = readerExtrasState.aiResult.copy(
                    text = if (readerExtrasState.aiResult.text.isNotBlank()) readerExtrasState.aiResult.text else textResult,
                    isLoading = false,
                    errorMessage = error,
                )
            )
        }
    }

    LaunchedEffect(splitRecoveryMessagePending) {
        if (!splitRecoveryMessagePending) return@LaunchedEffect
        splitRecoveryMessagePending = false
        showMessage(
            stringResolver.string(
                if (splitRecoveryHadSurvivor) {
                    "pdf_split_reader_missing_document"
                } else {
                    "pdf_split_reader_all_documents_missing"
                },
                if (splitRecoveryHadSurvivor) {
                    "A split PDF was unavailable, so the available document was kept open."
                } else {
                    "The split PDFs are no longer available, so split mode was closed."
                },
            ),
        )
    }

    var googleFontNames by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        googleFontNames = loadIosGoogleFontNames()
    }

    fun downloadGoogleFont(fontName: String, onComplete: () -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.Default) { downloadIosGoogleFont(fontName) }
            result.onSuccess { font ->
                bridge.recordImportedFonts(listOf(font.fileName), listOf(font.path))
                showMessage("$fontName downloaded successfully!")
            }.onFailure { error ->
                showMessage("Failed to download $fontName: ${error.message}")
            }
            onComplete()
        }
    }

    fun runDrawerAction(action: () -> Unit) {
        action()
        scope.launch { drawerState.close() }
    }

    fun cloudSyncEligible(): Boolean = state.isSyncEnabled && canUseCloudSync(
        providers = bridge.accountState.providers,
        hasGoogleDrivePermission = bridge.accountState.googleDriveAuthorized,
        isProUser = state.isProUser,
    )

    fun openLibraryBook(book: BookItem, temporary: Boolean = false) {
        val canDownload = cloudSyncEligible()
        val localFileExists = book.path?.let { path ->
            path.startsWith("opds-pse://") ||
                NSFileManager.defaultManager.fileExistsAtPath(path)
        } == true
        when (
            mobileBookOpenPreflightAction(
                book = book,
                localFileExists = localFileExists,
                canDownload = canDownload,
            )
        ) {
            MobileBookOpenPreflightAction.REMOVE_MISSING_FOLDER_BOOK -> {
                state = state.removeIosBooks(setOf(book.id)).copy(
                    bannerMessage = null,
                )
                showMessage(
                    stringResolver.string(
                        "banner_file_deleted_from_folder",
                        "File deleted from folder. Removed from library.",
                    )
                )
                return
            }
            MobileBookOpenPreflightAction.SHOW_MISSING_LOCATION -> {
                showMessage(
                    stringResolver.string(
                        "error_file_location_not_found",
                        "Could not find file location.",
                    )
                )
                return
            }
            MobileBookOpenPreflightAction.DOWNLOAD -> {
                pendingUnavailableBookId = book.id
                bridge.requestCloudSync(
                    SharedLibrarySnapshotJson.encode(
                        state.toIosCloudSnapshot().withStableIosBookPaths()
                    )
                )
                showMessage("Downloading ${book.displayName}")
                return
            }
            MobileBookOpenPreflightAction.SHOW_UNAVAILABLE -> {
                showMessage("This book is not available on this device")
                return
            }
            MobileBookOpenPreflightAction.OPEN -> Unit
        }
        if (book.type !in IOS_NATIVE_READER_FILE_TYPES) {
            if (temporary) {
                book.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
            }
            state = state.copy(
                bannerMessage = BannerMessage("${book.type.name} is not supported by the iOS reader yet")
            )
            return
        }
        if (
            !temporary &&
            book.type == FileType.PDF &&
            state.isTabsEnabled &&
            !canOpenMobilePdfTab(state.openTabIds, book.id)
        ) {
            showMessage("Maximum of $MAX_OPEN_PDF_TABS tabs allowed. Please close a tab first.")
            return
        }
        state = if (temporary) {
            state.withMobileTemporaryBookOpened(book)
        } else {
            state.withMobileBookOpened(book)
        }
        val openedBook = if (temporary) {
            book
        } else {
            state.rawLibraryBooks.firstOrNull { it.id == book.id } ?: book
        }
        activeTemporaryBookId = book.id.takeIf { temporary }
        activeTemporaryBookPath = book.path.takeIf { temporary }
        if (!temporary) {
            persistIosReaderSession(openedBook)
        }
        if (book.type == FileType.MOBI) {
            iosMobiLog {
                "Opening reader screen id=${book.id} file=${book.displayName} pathPresent=${!book.path.isNullOrBlank()}"
            }
        }
        activeReaderBook = openedBook
    }

    fun focusIosPdfSplitPane(pane: PdfSplitPane, sessionId: Long) {
        val current = pdfSplitWorkspace.pane(pane) ?: return
        if (current.sessionId != sessionId) return
        val next = pdfSplitWorkspace.reduce(
            PdfSplitWorkspaceAction.FocusChanged(
                pane = pane,
                expectedRevision = pdfSplitWorkspace.revision,
                expectedSessionId = sessionId,
            )
        )
        if (next == pdfSplitWorkspace) return
        pdfSplitWorkspace = next
        resolveIosPdfSplitBook(current, state.rawLibraryBooks)?.let { focusedBook ->
            activeReaderBook = focusedBook
            persistIosReaderSession(focusedBook)
        }
    }

    fun closeIosPdfSplitPane(pane: PdfSplitPane, sessionId: Long) {
        val current = pdfSplitWorkspace.pane(pane) ?: return
        if (current.sessionId != sessionId) return
        val next = pdfSplitWorkspace.reduce(
            PdfSplitWorkspaceAction.PaneClosed(
                pane = pane,
                expectedRevision = pdfSplitWorkspace.revision,
                expectedSessionId = sessionId,
            )
        )
        if (next == pdfSplitWorkspace) return
        pdfSplitWorkspace = next
        resolveIosPdfSplitBook(next.exitTargetDocument ?: current, state.rawLibraryBooks)?.let { book ->
            activeReaderBook = book
            persistIosReaderSession(book)
        }
    }

    fun closeIosPdfSplitWorkspace() {
        pendingPdfSplitWorkspaceRestore = null
        val exitTarget = pdfSplitWorkspace.exitTargetDocument
        pdfSplitWorkspace = pdfSplitWorkspace.reduce(PdfSplitWorkspaceAction.Closed)
        resolveIosPdfSplitBook(exitTarget ?: return, state.rawLibraryBooks)?.let { book ->
            activeReaderBook = book
            persistIosReaderSession(book)
        }
    }

    fun showIosPdfSplitPicker(targetPane: PdfSplitPane) {
        pdfSplitPickerTarget = IosPdfSplitPickerTarget(
            pane = targetPane,
            expectedRevision = pdfSplitWorkspace.revision,
            expectedSessionId = pdfSplitWorkspace.pane(targetPane)?.sessionId,
        )
    }

    fun selectIosPdfSplitBook(book: BookItem) {
        val target = pdfSplitPickerTarget ?: return
        val selected = iosPdfSplitPaneState(book) ?: return
        val current = activeReaderBook ?: return
        val next = if (!pdfSplitWorkspace.isOpen) {
            val primary = iosPdfSplitPaneState(current) ?: return
            PdfSplitWorkspaceState().reduce(
                PdfSplitWorkspaceAction.Open(
                    primary = primary,
                    secondary = selected,
                    orientation = pdfSplitWorkspace.orientation,
                )
            )
        } else {
            pdfSplitWorkspace.reduce(
                PdfSplitWorkspaceAction.PaneOpened(
                    pane = target.pane,
                    document = selected,
                    expectedRevision = target.expectedRevision,
                    expectedSessionId = target.expectedSessionId,
                )
            )
        }
        if (next == pdfSplitWorkspace && pdfSplitWorkspace.isOpen) {
            pdfSplitPickerTarget = null
            return
        }
        pendingPdfSplitWorkspaceRestore = null
        pdfSplitWorkspace = next
        pdfSplitPickerTarget = null
        resolveIosPdfSplitBook(next.exitTargetDocument ?: selected, state.rawLibraryBooks)?.let { focusedBook ->
            activeReaderBook = focusedBook
            persistIosReaderSession(focusedBook)
        }
    }

    fun iosPdfSplitPickerBooks(target: IosPdfSplitPickerTarget): List<BookItem> {
        val otherPane = pdfSplitWorkspace.pane(
            when (target.pane) {
                PdfSplitPane.PRIMARY -> PdfSplitPane.SECONDARY
                PdfSplitPane.SECONDARY -> PdfSplitPane.PRIMARY
            }
        )
        val currentBook = activeReaderBook
        return state.rawLibraryBooks
            .asSequence()
            .filter { it.type == FileType.PDF && iosPdfSplitBookIsAvailable(it) }
            .filter { candidate ->
                val candidateState = iosPdfSplitPaneState(candidate) ?: return@filter false
                val isOtherPane = candidateState.samePdfDocument(otherPane)
                val isCurrentFullScreenBook = !pdfSplitWorkspace.isOpen &&
                    candidateState.samePdfDocument(currentBook?.let(::iosPdfSplitPaneState))
                !isOtherPane && !isCurrentFullScreenBook
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    fun addBooksToLibrary(books: List<BookItem>, message: String? = null) {
        val result = state.withMobileImportedBooks(books, message)
        if (result.addedBooks.isEmpty()) return
        val newLocalFolders = result.addedBooks
            .mapNotNull { it.sourceFolder }
            .filterNot { it == "iOS import" }
            .distinct()
            .filterNot { name -> result.state.syncedFolders.any { it.name == name } }
            .map { name ->
                SyncedFolder(
                    uriString = "ios-local-folder://${name.normalizedId()}",
                    name = name,
                    lastScanTime = currentTimestamp(),
                )
            }
        state = result.state
            .copy(syncedFolders = result.state.syncedFolders + newLocalFolders)
            .withIosImportsFolder(result.addedBooks)
        selectMainPage(SharedMobileMainDestination.LIBRARY)
        selectLibraryTab(SharedMobileLibraryTab.BOOKS)
    }

    fun startIosPdfReflow(pdfBook: BookItem, password: String?) {
        val reflowBookId = "${pdfBook.id}_reflow"
        val existing = state.rawLibraryBooks.firstOrNull { it.id == reflowBookId }
        if (existing != null) {
            openLibraryBook(existing)
            return
        }
        if (pdfReflowProgress != null) return
        val pdfPath = pdfBook.path?.takeIf(NSFileManager.defaultManager::fileExistsAtPath)
        if (pdfPath == null) {
            showMessage("Could not find ${pdfBook.displayName}.")
            return
        }
        val importsPath = iosImportsDirectoryPath()?.canonicalIosFilePath()
        if (importsPath == null) {
            showMessage("Unable to generate Text View.")
            return
        }
        val baseTitle = pdfBook.title?.takeIf { it.isNotBlank() }
            ?: pdfBook.displayName.substringBeforeLast('.', pdfBook.displayName)
        val destPath = "$importsPath/$reflowBookId.html"
        pdfReflowProgress = 0f
        scope.launch {
            val success = generateIosPdfReflowHtml(
                pdfPath = pdfPath,
                destPath = destPath,
                password = password,
                onProgress = { progress -> pdfReflowProgress = progress },
            )
            if (!success) {
                pdfReflowProgress = null
                showMessage("Text View generation failed.")
                return@launch
            }
            pdfReflowProgress = null
            val books = listOf(
                IosImportedFile(
                    name = "$baseTitle (Text View).html",
                    path = destPath,
                    contentId = reflowBookId,
                )
            ).toImportedBooks(existingBooks = state.rawLibraryBooks)
            val created = books.firstOrNull()
            if (created == null) {
                showMessage("Unable to add the generated Text View.")
                return@launch
            }
            val reflowBook = created.copy(
                displayName = "$baseTitle (Text View)",
                title = "$baseTitle (Reflow)",
                author = "Generated",
            )
            addBooksToLibrary(listOf(reflowBook), "Generated Text View for ${pdfBook.displayName}")
            openLibraryBook(reflowBook)
        }
    }

    fun importIosAudiobooks(files: List<IosImportedFile>) {
        val split = splitFilesByAudiobookDecodability(
            files.filter { SharedAudiobookFormats.supportsFileName(it.name) },
            IosImportedFile::name,
        )
        if (split.unsupported.isNotEmpty()) {
            showMessage(
                "Skipped ${split.unsupported.joinToString { it.name }}: " +
                    "this device cannot decode these audiobook formats"
            )
        }
        val candidates = split.decodable
            .filter { file ->
                val id = file.contentId.takeIf { it.isNotBlank() }
                    ?: "ios_audio_${file.path.stableIosImportedFilePath().normalizedId()}"
                state.audiobooks.none { it.bookId == id }
            }
            .distinctBy { it.path }
        if (candidates.isEmpty()) return
        val pending = candidates.withIndex().toMutableList()
        val total = candidates.size
        candidates.forEach { file ->
            val id = file.contentId.takeIf { it.isNotBlank() }
                ?: "ios_audio_${file.path.stableIosImportedFilePath().normalizedId()}"
            audiobookPlayer.extractAudiobookMetadata(
                filePath = file.path,
                fallbackTitle = file.name.substringBeforeLast('.').ifBlank { file.name },
            ) { title, author, album, durationMs ->
                state = state.withAudiobookImported(
                    SharedAudiobook(
                        bookId = id,
                        filePath = file.path.stableIosImportedFilePath(),
                        format = file.name.substringAfterLast('.', "").lowercase(),
                        title = title,
                        author = author,
                        album = album,
                        durationMs = durationMs,
                        positionMs = 0L,
                        playbackSpeed = 1f,
                        addedAt = currentTimestamp(),
                    )
                )
                pending.removeAll { it.value.path == file.path }
                if (pending.isEmpty()) {
                    selectMainPage(SharedMobileMainDestination.LIBRARY)
                    showMessage(if (total == 1) "Added $total audiobook" else "Added $total audiobooks")
                }
            }
        }
    }

    fun requestCloudSyncIfEligible() {
        if (!cloudSyncEligible()) return
        bridge.requestCloudSync(
            SharedLibrarySnapshotJson.encode(
                state.toIosCloudSnapshot()
                    .withStableIosBookPaths()
                    .withStableIosAudiobookPaths()
            )
        )
    }

    fun setFolderSyncEnabled(enabled: Boolean) {
        state = state.reduce(AppAction.FolderSyncEnabledChanged(enabled))
        if (shouldRequestCloudSyncAfterFolderSyncChange(enabled, state.isSyncEnabled)) {
            requestCloudSyncIfEligible()
        }
    }

    fun refreshFolders() {
        state = state.copy(isRefreshing = true)
        onRefreshFolders()
        if (bridge.pendingFolderScans.isEmpty()) {
            state = state.copy(isRefreshing = false)
        }
    }

    fun removeManagedExternalBook(book: BookItem) {
        book.path?.let { bridge.removeImportedFiles(listOf(it)) }
        state = state.removeIosBooks(setOf(book.id))
        clearPendingIosExternalFileRemoval()
    }

    fun finishManagedExternalOpen(book: BookItem): Boolean {
        if (activeExternalBookId != book.id) return false
        when (mobileExternalFileCloseAction(activeExternalBehavior)) {
            MobileExternalFileCloseAction.KEEP -> {
                clearPendingIosExternalFileRemoval()
                requestCloudSyncIfEligible()
            }
            MobileExternalFileCloseAction.PROMPT -> {
                pendingExternalClosePrompt = book
            }
            MobileExternalFileCloseAction.DELETE -> {
                removeManagedExternalBook(book)
            }
        }
        activeExternalBookId = null
        activeExternalBehavior = null
        return true
    }

    fun closeActiveReader(book: BookItem) {
        bridge.restoreReaderBrightness()
        bridge.setKeepScreenOn(false)
        activeReaderBook = null
        if (activeTemporaryBookId == book.id) {
            activeTemporaryBookPath?.let {
                NSFileManager.defaultManager.removeItemAtPath(it, error = null)
            }
            state = state.withMobileTemporaryBookClosed(book.id)
            activeTemporaryBookId = null
            activeTemporaryBookPath = null
        } else {
            clearIosReaderSession()
            state = state.withoutMobileReaderSession()
            if (!finishManagedExternalOpen(book)) {
                requestCloudSyncIfEligible()
            }
        }
    }

    LaunchedEffect(state.rawLibraryBooks, pdfSplitWorkspace) {
        if (!pdfSplitWorkspace.isOpen) return@LaunchedEffect
        val recovery = recoverIosPdfSplitWorkspace(pdfSplitWorkspace, state.rawLibraryBooks)
        if (!recovery.hasMissingPanes || recovery.workspace == pdfSplitWorkspace) return@LaunchedEffect

        val missingIds = recovery.missingPanes.mapNotNull { pane ->
            pdfSplitWorkspace.pane(pane)?.bookId
        }.toSet()
        pdfSplitWorkspace = recovery.workspace
        splitRecoveryMessagePending = true
        splitRecoveryHadSurvivor = recovery.survivingDocument != null
        recovery.survivingDocument?.let { survivor ->
            resolveIosPdfSplitBook(survivor, state.rawLibraryBooks)?.let { book ->
                activeReaderBook = book
                persistIosReaderSession(book)
            }
        } ?: run {
            if (activeReaderBook?.id in missingIds) {
                clearIosReaderSession()
                state = state.withoutMobileReaderSession()
                activeReaderBook = null
            }
        }
    }

    fun updateIosBookMetadata(book: BookItem) {
        val current = state.rawLibraryBooks.firstOrNull { it.id == book.id } ?: return
        scope.launch {
            withContext(Dispatchers.Default) {
                persistIosEpubMetadataEdit(current, book)
            }.onSuccess { persisted ->
                val folderName = current.sourceFolder
                if (!folderName.isNullOrBlank() && !persisted.path.isNullOrBlank()) {
                    val replacement = bridge.replaceFolderManagedFile(folderName, persisted.path)
                    if (replacement == null) {
                        showMessage("Could not write the edited EPUB back to $folderName")
                        refreshFolders()
                    } else {
                        state = state.withUpdatedIosBook(
                            persisted.copy(
                                fileSize = replacement.fileSize,
                                fileContentModifiedTimestamp = replacement.lastModifiedTimestamp,
                            )
                        )
                        showMessage("EPUB metadata updated")
                    }
                } else {
                    state = state.withUpdatedIosBook(persisted)
                    showMessage("EPUB metadata updated")
                }
            }.onFailure {
                showMessage(it.message ?: "Could not update EPUB metadata")
            }
        }
    }

    LaunchedEffect(bridge.pendingCloudSync) {
        val pending = bridge.pendingCloudSync ?: return@LaunchedEffect
        val localSnapshot = state.toIosCloudSnapshot()
        val remoteSnapshot = SharedLibrarySnapshotJson.decodeOrEmpty(pending.remoteSnapshotJson)
        val legacyPdfBookmarksByBookId = decodeLegacyIosPdfBookmarksByBookId(pending.remoteSnapshotJson)
        val mergedSnapshot = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = localSnapshot,
            remote = remoteSnapshot,
            downloadedBookPaths = pending.downloadedBookPaths,
        )
        val migratedLegacyPdfBookmarkBookIds = mutableSetOf<String>()
        mergedSnapshot.pdfSidecars.forEach { sidecar ->
            val book = mergedSnapshot.books.firstOrNull { it.id == sidecar.bookId } ?: return@forEach
            val payload = SharedPdfCloudSidecarCodec.decode(
                rawDataJson = sidecar.data,
                fallbackPageCount = 1,
                fallbackPageIndex = book.lastPageIndex ?: 0,
            )
            val legacySidecarBookmarks = decodeLegacyIosPdfBookmarks(sidecar.data)
            val legacyBookmarks = legacySidecarBookmarks.ifEmpty {
                legacyPdfBookmarksByBookId[book.id].orEmpty()
            }
            val restored = payload?.readerState ?: payload?.let {
                SharedPdfReaderState.initial(
                    pageCount = 1,
                    initialPageIndex = book.lastPageIndex ?: 0,
                ).copy(
                    bookmarks = legacyBookmarks,
                    annotations = it.annotations,
                    richTextDocumentJson = it.richTextDocumentJson.orEmpty(),
                )
            } ?: legacyPdfBookmarksByBookId[book.id]
                ?.takeIf { it.isNotEmpty() }
                ?.let { bookmarks ->
                    SharedPdfReaderState.initial(
                        pageCount = 1,
                        initialPageIndex = book.lastPageIndex ?: 0,
                    ).copy(bookmarks = bookmarks)
                }
            if (restored != null) {
                if (legacyBookmarks.isNotEmpty() && payload?.readerState == null) {
                    migratedLegacyPdfBookmarkBookIds += book.id
                }
                persistIosPdfReaderState(book, restored)
                NSUserDefaults.standardUserDefaults.setInteger(
                    value = maxOf(sidecar.timestamp, payload?.modifiedTimestamp ?: 0L),
                    forKey = book.iosPdfSidecarTimestampKey(),
                )
            }
        }
        // Older Android library snapshots kept PDF bookmarks in the book
        // record rather than the portable sidecar.  Preserve those records
        // even when the remote snapshot has no sidecar entry yet.
        legacyPdfBookmarksByBookId.forEach { (bookId, bookmarks) ->
            if (bookId in migratedLegacyPdfBookmarkBookIds || bookmarks.isEmpty()) return@forEach
            val book = mergedSnapshot.books.firstOrNull { it.id == bookId && it.type == FileType.PDF }
                ?: return@forEach
            if (loadPersistedIosPdfReaderStateFromDefaults(book) != null) return@forEach
            persistIosPdfReaderState(
                book,
                SharedPdfReaderState.initial(
                    pageCount = 1,
                    initialPageIndex = book.lastPageIndex ?: 0,
                ).copy(bookmarks = bookmarks),
            )
        }
        val mergedPdfSidecars = mergedSnapshot.pdfSidecars
        if (mergedPdfSidecars.isNotEmpty()) {
            scope.launch(Dispatchers.Default) {
                mergedPdfSidecars.forEach { sidecar ->
                    IosPdfCloudSidecarStore.write(sidecar.bookId, sidecar.data)
                }
            }
        }
        state = mergedSnapshot
            .withResolvedIosBookPaths()
            .withResolvedIosAudiobookPaths()
            .toSharedMobileReaderState()
        pendingUnavailableBookId?.let { bookId ->
            val downloaded = state.rawLibraryBooks.firstOrNull { it.id == bookId && it.isAvailable }
            pendingUnavailableBookId = null
            if (downloaded != null) {
                openLibraryBook(downloaded)
            } else {
                showMessage("The book could not be downloaded")
            }
        }
        bridge.uploadCloudSnapshot(
            SharedLibrarySnapshotJson.encode(
                mergedSnapshot
                    .withStableIosBookPaths()
                    .withStableIosAudiobookPaths()
            )
        )
        bridge.consumeCloudSnapshot()
    }

    LaunchedEffect(bridge.pendingExternalOpen) {
        val request = bridge.pendingExternalOpen ?: return@LaunchedEffect
        val existing = state.rawLibraryBooks.firstOrNull {
            request.addToLibrary && (
                it.path == request.file.path ||
                    (request.file.contentId.isNotBlank() && it.id == request.file.contentId)
                )
        }
        val externalBook = existing
            ?: listOf(request.file).toImportedBooks(existingBooks = state.rawLibraryBooks).firstOrNull()
                ?.let { imported ->
                    if (request.addToLibrary) imported else imported.copy(
                        id = "ios_temporary_${currentTimestamp()}_${request.file.path.hashCode()}"
                    )
                }
        if (externalBook != null) {
            if (request.addToLibrary && existing == null) {
                addBooksToLibrary(listOf(externalBook), "Added ${externalBook.displayName}")
            }
            if (request.addToLibrary && existing == null) {
                activeExternalBookId = externalBook.id
                activeExternalBehavior = request.behavior
                persistPendingIosExternalFileRemoval(externalBook)
            }
            openLibraryBook(externalBook, temporary = !request.addToLibrary)
        } else {
            showMessage("This file type is not supported")
        }
        bridge.consumeExternalOpen()
    }

    LaunchedEffect(bridge.importedFiles, bridge.pendingImportBatches, bridge.pendingFolderScans) {
        bridge.pendingImportBatches.firstOrNull()?.let { batch ->
            val audioSplit = splitFilesByAudiobookDecodability(
                batch.files.filter { SharedAudiobookFormats.supportsFileName(it.name) },
                IosImportedFile::name,
            )
            val audioFiles = audioSplit.decodable
            if (audioSplit.unsupported.isNotEmpty()) {
                showMessage(
                    "Skipped ${audioSplit.unsupported.joinToString { it.name }}: " +
                        "this device cannot decode these audiobook formats"
                )
            }
            val bookFiles = batch.files.filterNot { SharedAudiobookFormats.supportsFileName(it.name) }
            val existingBooks = state.rawLibraryBooks
            val outcome = planMobileImportBatch(
                files = bookFiles.map { file ->
                    ImportedBookFile(
                        name = file.name,
                        uriString = null,
                        localPath = file.path,
                        size = file.fileSize,
                        id = file.contentId.takeIf(String::isNotBlank),
                    )
                },
                existingBookIds = state.rawLibraryBooks.mapTo(mutableSetOf()) { it.id },
                failedCount = batch.failedCount,
            )
            val bookToOpen = outcome.singleSelectionOpenBook(existingBooks).takeIf { batch.autoOpen }
            val rejectedPaths = outcome.plan.decisions
                .filterNot { it.status == com.aryan.reader.shared.SharedImportDecisionStatus.IMPORTABLE }
                .mapNotNull { it.file.localPath }
            if (rejectedPaths.isNotEmpty()) {
                bridge.removeImportedFiles(rejectedPaths)
            }
            if (outcome.plan.importedBooks.isNotEmpty()) {
                addBooksToLibrary(
                    outcome.plan.importedBooks,
                    "Added ${outcome.counts.addedCount} book(s)",
                )
            } else if (bookFiles.isNotEmpty() || audioFiles.isEmpty()) {
                val feedback = SharedImportPlanner.feedbackForCounts(
                    counts = outcome.counts,
                    importedMessage = "Added ${outcome.counts.addedCount} book(s)",
                    duplicateMessage = if (outcome.counts.duplicateCount == 1) {
                        "This book is already in the library"
                    } else {
                        "${outcome.counts.duplicateCount} books are already in the library"
                    },
                    unsupportedMessage = "This file type is not supported",
                    failedMessage = "Could not import the selected file(s)",
                )
                showMessage(feedback.message)
            }
            if (audioFiles.isNotEmpty()) {
                importIosAudiobooks(audioFiles)
            }
            bookToOpen?.let(::openLibraryBook)
            bridge.consumeImportBatch()
        }

        bridge.pendingFolderScans.firstOrNull()?.let { scan ->
            if (!scan.succeeded) {
                showMessage("Could not refresh ${scan.folderName}; keeping the previous scan")
                bridge.consumeFolderScan()
                state = state.copy(isRefreshing = bridge.pendingFolderScans.isNotEmpty())
                return@LaunchedEffect
            }
            val now = currentTimestamp()
            val configuredFolder = state.syncedFolders.firstOrNull { it.name == scan.folderName }
            if (!shouldApplyMobileFolderScan(configuredFolder)) {
                bridge.consumeFolderScan()
                state = state.copy(isRefreshing = bridge.pendingFolderScans.isNotEmpty())
                return@LaunchedEffect
            }
            val folderForScan = configuredFolder
                ?: SyncedFolder(
                    uriString = "ios-local-folder://${scan.folderName.normalizedId()}",
                    name = scan.folderName,
                    lastScanTime = 0L,
                )
            val syncResult = LocalFolderSyncEngine.syncFolder(
                state = state,
                folder = folderForScan.copy(uriString = scan.folderName),
                files = scan.files,
                remoteMetadata = emptyMap(),
                nowMillis = now,
            )
            val syncedFolder = folderForScan.copy(lastScanTime = now)
            state = syncResult.state.copy(
                syncedFolders = (
                    state.syncedFolders.filterNot { it.name == scan.folderName } + syncedFolder
                ).sortedBy { it.name.lowercase() },
            )
            if (syncResult.stats.newBooks > 0) {
                selectMainPage(SharedMobileMainDestination.LIBRARY)
                selectLibraryTab(SharedMobileLibraryTab.BOOKS)
                showMessage("Added ${syncResult.stats.newBooks} book(s) from ${scan.folderName}")
            } else if (
                syncResult.stats.updatedBooks > 0 ||
                syncResult.stats.removedBooks > 0 ||
                syncResult.stats.migratedBooks > 0
            ) {
                showMessage("Refreshed ${scan.folderName}")
            }
            bridge.consumeFolderScan()
            state = state.copy(isRefreshing = bridge.pendingFolderScans.isNotEmpty())
        }

        bridge.importedFiles
            .filter { it.sourceFolder.isBlank() && it.contentId.isNotBlank() }
            .forEach { imported ->
                val legacyBook = state.rawLibraryBooks.firstOrNull {
                    it.path == imported.path && it.id != imported.contentId
                } ?: return@forEach
                state = if (state.rawLibraryBooks.none { it.id == imported.contentId }) {
                    state.withMigratedMobileBookIdentity(legacyBook.id, imported.contentId)
                } else {
                    state.removeIosBooks(setOf(legacyBook.id))
                }
            }
        val duplicateManagedPaths = bridge.importedFiles
            .filter { it.sourceFolder.isBlank() && it.contentId.isNotBlank() }
            .filter { imported ->
                state.rawLibraryBooks.any { book ->
                    book.id == imported.contentId && book.path != imported.path
                }
            }
            .map { it.path }
        if (duplicateManagedPaths.isNotEmpty()) {
            bridge.removeImportedFiles(duplicateManagedPaths)
            showMessage("This book is already in the library")
        }
        val importedBooks = bridge.importedFiles
            .filter { it.sourceFolder.isBlank() }
            .toImportedBooks(existingBooks = state.rawLibraryBooks)
        if (importedBooks.isNotEmpty()) {
            val importedCount = importedBooks
                .distinctBy { it.id }
                .count { book -> state.rawLibraryBooks.none { it.id == book.id } }
            if (importedCount > 0) {
                addBooksToLibrary(importedBooks, "Added $importedCount import(s)")
            }
        }
        val presentationCandidates = state.rawLibraryBooks.filter { book ->
            book.path != null &&
                book.type in IosPresentationMetadataTypes &&
                (
                    book.coverImagePath.isNullOrBlank() ||
                        book.title.isNullOrBlank() ||
                        book.title == book.displayName.substringBeforeLast('.', book.displayName) ||
                        book.seriesName.isNullOrBlank()
                )
        }
        presentationCandidates.forEach { book ->
            // PDF cover/metadata extraction rasterizes a page through PDFium; keep it off the
            // UI thread and serialized with the shared reader pipeline (Android parity).
            val presentation = withContext(Dispatchers.Default) {
                if (book.type == FileType.PDF) {
                    IosPdfiumRuntime.mutex.withLock { extractIosBookPresentation(book) }
                } else {
                    extractIosBookPresentation(book)
                }
            }
            val coverPath = presentation.coverBytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { bytes -> persistIosGeneratedCover(book, bytes) }
            if (
                presentation.title != null ||
                presentation.author != null ||
                presentation.seriesName != null ||
                coverPath != null
            ) {
                state = state.withUpdatedIosBook(
                    book.copy(
                        title = presentation.title ?: book.title,
                        author = presentation.author ?: book.author,
                        coverImagePath = coverPath ?: book.coverImagePath,
                        seriesName = presentation.seriesName?.takeIf { book.seriesName.isNullOrBlank() }
                            ?: book.seriesName,
                        seriesIndex = presentation.seriesIndex?.takeIf { book.seriesIndex == null }
                            ?: book.seriesIndex,
                    ),
                )
            }
        }
    }

    @Composable
    fun renderIosPdfHost(
        paneBook: BookItem,
        onBack: () -> Unit,
        hostConfig: SharedPdfReaderHostConfig,
        pdfTabsEnabled: Boolean,
    ) {
        val effectiveHostConfig = hostConfig.copy(isAppActive = bridge.appLifecycleState.isActive)
        val initialPdfReaderState = remember(effectiveHostConfig.sessionKey) {
            loadPersistedIosPdfReaderState(paneBook)
        }
        fun acceptsCurrentHostCallback(): Boolean {
            val key = effectiveHostConfig.sessionKey
            if (!key.isValid) return false
            return if (!pdfSplitWorkspace.isOpen) {
                activeReaderBook?.id == paneBook.id &&
                    key.canonicalBookId == paneBook.id
            } else {
                listOfNotNull(pdfSplitWorkspace.primary, pdfSplitWorkspace.secondary).any { document ->
                    document.canonicalBookId == key.canonicalBookId &&
                        document.sessionId == key.sessionId
                }
            }
        }
        LaunchedEffect(
            effectiveHostConfig.sessionKey,
            effectiveHostConfig.isFocused,
            bridge.appLifecycleState.eventId,
            readerBrightness,
        ) {
            if (effectiveHostConfig.isFocused && effectiveHostConfig.isAppActive) {
                bridge.setReaderBrightness(readerBrightness)
            }
        }
        SharedMobilePdfReaderHost(
            book = paneBook,
            onBack = onBack,
            readerAiAvailable = readerAiAvailable,
            readerExtrasState = readerExtrasState.copy(cloudTts = readerCloudTts.state),
            cloudTts = readerCloudTts,
            cloudTtsModeEnabled = effectiveReaderAiSettings.ttsModel == com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID,
            onCloudTtsModeChange = ::updateCloudTtsMode,
            cloudTtsVoiceId = effectiveReaderAiSettings.ttsSpeakerId,
            onCloudTtsVoiceChange = ::updateCloudTtsVoice,
            onClearCloudTtsCache = readerCloudTts::clearCache,
            onAiAction = ::runReaderAiAction,
            onAiResultDismiss = {
                dismissReaderAiResult()
            },
            onOpenAiHub = { utilityScreen = IosUtilityScreen.AI_SETTINGS },
            pdfReflowUiState = SharedMobilePdfReflowUiState(
                isGenerating = pdfReflowProgress != null,
                progress = pdfReflowProgress ?: 0f,
                hasReflowBook = state.rawLibraryBooks.any { it.id == "${paneBook.id}_reflow" },
            ),
            pdfTabsEnabled = pdfTabsEnabled,
            openPdfTabs = if (pdfTabsEnabled) state.openTabs else emptyList(),
            activePdfTabBookId = if (pdfTabsEnabled) state.activeTabBookId else null,
            availablePdfTabBooks = if (pdfTabsEnabled) state.rawLibraryBooks else emptyList(),
            pdfTopTabStripVisible = pdfTopTabStripVisible,
            onPdfTopTabStripVisibilityChange = { visible ->
                if (pdfTabsEnabled && acceptsCurrentHostCallback()) {
                    pdfTopTabStripVisible = visible
                    persistIosPdfTopTabStripVisible(visible)
                }
            },
            onOpenPdfTab = { tab ->
                if (pdfTabsEnabled && acceptsCurrentHostCallback() && tab.id != paneBook.id) {
                    openLibraryBook(tab)
                }
            },
            onClosePdfTab = { tab ->
                if (!pdfTabsEnabled || !acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                val closingActiveTab = tab.id == state.activeTabBookId
                state = state.withMobileBookClosed(tab.id)
                finishManagedExternalOpen(tab)
                if (closingActiveTab) {
                    val nextBook = state.activeTabBookId?.let { nextId ->
                        state.rawLibraryBooks.firstOrNull { it.id == nextId }
                    }
                    if (nextBook == null) {
                        closeActiveReader(paneBook)
                    } else {
                        activeReaderBook = nextBook
                        persistIosReaderSession(nextBook)
                    }
                }
            },
            onNativePdfAction = { pdfBook, action, password, pdfExport ->
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                when (action) {
                    SharedMobilePdfNativeAction.DICTIONARY_SETTINGS -> {
                        showDictionarySettingsSheet = true
                    }
                    SharedMobilePdfNativeAction.TEXT_VIEW -> {
                        startIosPdfReflow(pdfBook, password)
                    }
                    SharedMobilePdfNativeAction.SAVE_COPY -> scope.launch {
                        when (val export = prepareIosPdfSaveCopy(pdfBook, password, pdfExport)) {
                            is IosPdfSaveCopyPreparation.Ready -> {
                                if (!bridge.performPdfNativeAction(export.book, action)) {
                                    showMessage("Unable to export ${pdfBook.displayName}.")
                                }
                            }
                            is IosPdfSaveCopyPreparation.Unavailable -> showMessage(export.message)
                        }
                    }
                    SharedMobilePdfNativeAction.SHARE_ANNOTATED -> scope.launch {
                        when (val export = prepareIosPdfSaveCopy(pdfBook, password, pdfExport)) {
                            is IosPdfSaveCopyPreparation.Ready -> {
                                if (!bridge.performPdfNativeAction(export.book, SharedMobilePdfNativeAction.SHARE)) {
                                    showMessage("Unable to share ${pdfBook.displayName}.")
                                }
                            }
                            is IosPdfSaveCopyPreparation.Unavailable -> showMessage(export.message)
                        }
                    }
                    SharedMobilePdfNativeAction.SHARE_ORIGINAL -> {
                        if (!bridge.performPdfNativeAction(pdfBook, SharedMobilePdfNativeAction.SHARE)) {
                            showMessage("Unable to share ${pdfBook.displayName}.")
                        }
                    }
                    else -> {
                        if (!bridge.performPdfNativeAction(pdfBook, action)) {
                            showMessage(
                                when (action) {
                                    SharedMobilePdfNativeAction.SHARE -> "Unable to share ${pdfBook.displayName}."
                                    SharedMobilePdfNativeAction.SAVE_COPY -> "Unable to export ${pdfBook.displayName}."
                                    SharedMobilePdfNativeAction.PRINT -> "Printing is unavailable."
                                    else -> "Unable to perform PDF action."
                                },
                            )
                        }
                    }
                }
            },
            onBookInfoChange = { updated ->
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                val currentBook = state.rawLibraryBooks.firstOrNull { it.id == paneBook.id } ?: paneBook
                val persisted = currentBook.withUserEditedMetadata(updated)
                state = state.withUpdatedIosBook(persisted)
                if (activeReaderBook?.id == paneBook.id) activeReaderBook = persisted
            },
            knownTags = state.allTags,
            pdfToolbarPreferences = pdfToolbarPreferences,
            onPdfToolbarPreferencesChange = { preferences ->
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                pdfToolbarPreferences = preferences
                persistIosPdfToolbarPreferences(preferences)
            },
            ocrLanguage = pdfOcrLanguage,
            onOcrLanguageChange = { language ->
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                pdfOcrLanguage = language
                persistIosPdfOcrLanguage(language)
            },
            readerBrightness = readerBrightness,
            readerCustomBrightness = readerCustomBrightness,
            onReaderBrightnessChange = { brightness ->
                if (!acceptsCurrentHostCallback() || !effectiveHostConfig.owns(SharedPdfReaderGlobalResource.SYSTEM_UI)) {
                    return@SharedMobilePdfReaderHost
                }
                brightness?.let { readerCustomBrightness = normalizeReaderBrightness(it) }
                readerBrightness = brightness
                persistIosReaderBrightness(brightness, readerCustomBrightness)
                bridge.setReaderBrightness(brightness)
            },
            readerScreenOrientationMode = readerOrientation,
            onReaderScreenOrientationModeChange = { mode ->
                if (!acceptsCurrentHostCallback() || !effectiveHostConfig.owns(SharedPdfReaderGlobalResource.SYSTEM_UI)) {
                    return@SharedMobilePdfReaderHost
                }
                readerOrientation = mode
                persistIosReaderOrientation(mode)
            },
            onApplyReaderScreenOrientation = bridge::applyReaderOrientation,
            readerTtsReplacementPreferences = state.readerTtsReplacementPreferences,
            onReaderTtsReplacementPreferencesChange = {
                if (!acceptsCurrentHostCallback() || !effectiveHostConfig.owns(SharedPdfReaderGlobalResource.TTS)) {
                    return@SharedMobilePdfReaderHost
                }
                state = state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(it))
            },
            onTtsError = { message ->
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                state = state.reduce(AppAction.BannerShown(BannerMessage(message, isError = true)))
            },
            initialReaderState = initialPdfReaderState,
            readerDefaultSettings = state.pdfReaderDefaultSettings,
            onReaderDefaultSettingsChange = { defaults ->
                state = state.reduce(AppAction.PdfReaderDefaultSettingsChanged(defaults))
            },
            customReaderThemes = state.customReaderThemes,
            onCustomReaderThemesChange = { themes ->
                state = state.reduce(AppAction.CustomReaderThemesChanged(themes))
            },
            initialKeepScreenOn = loadIosKeepScreenOn(),
            onKeepScreenOnPreferenceChange = { enabled ->
                if (acceptsCurrentHostCallback()) persistIosKeepScreenOn(enabled)
            },
            initialStylusOnlyMode = loadIosStylusOnlyMode(),
            onStylusOnlyModePreferenceChange = ::persistIosStylusOnlyMode,
            initialPageSliderVisible = loadIosPdfPageSliderVisible(paneBook.id),
            onPageSliderVisibilityPreferenceChange = { visible ->
                persistIosPdfPageSliderVisible(paneBook.id, visible)
            },
            onReaderStateChange = {},
            onReaderSessionStateChange = { sessionKey, pdfState ->
                if (!effectiveHostConfig.acceptsCallback(sessionKey)) {
                    return@SharedMobilePdfReaderHost
                }
                val currentBook = state.rawLibraryBooks.firstOrNull { it.id == paneBook.id } ?: paneBook
                // Keep the id-keyed local copy even while focus/active-reader
                // ownership is changing.  The sidecar write below remains
                // guarded, but a close/background transition must not erase a
                // bookmark before the next session can flush it.
                persistIosPdfReaderState(currentBook, pdfState)
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                pdfSidecarWriteJobs[currentBook.id]?.cancel()
                pdfSidecarWriteJobs[currentBook.id] = scope.launch {
                    delay(300L)
                    val bookForWrite = currentBook
                    withContext(Dispatchers.Default) {
                        val existingData = IosPdfCloudSidecarStore.read(bookForWrite.id)
                        val existingTimestamp = SharedPdfCloudSidecarCodec.decode(
                            rawDataJson = existingData,
                            fallbackPageCount = 1,
                            fallbackPageIndex = bookForWrite.lastPageIndex ?: 0,
                        )?.modifiedTimestamp ?: 0L
                        val previousTimestamp = NSUserDefaults.standardUserDefaults.integerForKey(
                            bookForWrite.iosPdfSidecarTimestampKey()
                        )
                        val modifiedTimestamp = maxOf(
                            currentTimestamp(),
                            existingTimestamp + 1L,
                            previousTimestamp + 1L,
                        )
                        val localData = SharedPdfCloudSidecarCodec.encode(
                            bookId = bookForWrite.id,
                            state = pdfState,
                            sourceFingerprint = bookForWrite.iosPdfSourceFingerprint(),
                            modifiedTimestamp = modifiedTimestamp,
                            existingDataJson = existingData,
                        )
                        val mergedData = existingData?.let { existing ->
                            SharedPdfCloudSidecarCodec.merge(
                                localDataJson = localData,
                                remoteDataJson = existing,
                                preferRemoteOnConflict = false,
                                fallbackPageCount = 1,
                                fallbackPageIndex = bookForWrite.lastPageIndex ?: 0,
                            )
                        } ?: localData
                        IosPdfCloudSidecarStore.write(bookForWrite.id, mergedData)
                        NSUserDefaults.standardUserDefaults.setInteger(
                            value = modifiedTimestamp,
                            forKey = bookForWrite.iosPdfSidecarTimestampKey(),
                        )
                    }
                }
                val updatedBook = currentBook.withIosPdfReaderProgress(pdfState)
                if (updatedBook != currentBook) {
                    state = state.withUpdatedIosBook(updatedBook)
                    if (activeReaderBook?.id == paneBook.id) activeReaderBook = updatedBook
                }
            },
            pdfAutoScrollGlobalProfile = pdfAutoScrollProfile,
            onPdfAutoScrollGlobalProfileChange = { profile ->
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                pdfAutoScrollProfile = profile.sanitized()
                persistIosPdfAutoScrollProfile(pdfAutoScrollProfile)
            },
            initialPdfAutoScrollMusicianMode = pdfAutoScrollMusicianMode,
            onPdfAutoScrollMusicianModeChange = { enabled ->
                pdfAutoScrollMusicianMode = enabled
                NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosPdfAutoScrollMusicianDefaultsKey)
            },
            initialPdfAutoScrollUseSlider = pdfAutoScrollUseSlider,
            onPdfAutoScrollUseSliderChange = { enabled ->
                pdfAutoScrollUseSlider = enabled
                NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosPdfAutoScrollSliderDefaultsKey)
            },
            onPdfAutoScrollBookChange = { updated ->
                if (!acceptsCurrentHostCallback()) return@SharedMobilePdfReaderHost
                state = state.withUpdatedIosBook(updated)
                if (activeReaderBook?.id == paneBook.id) activeReaderBook = updated
            },
            onKeepScreenOnChange = bridge::setKeepScreenOn,
            onSystemUiAppearanceChange = bridge::updateSystemUi,
            modifier = Modifier.fillMaxSize(),
            hostConfig = effectiveHostConfig,
        )
    }

    SharedAppTheme(
        appThemeMode = state.appThemeMode,
        appContrastOption = state.appContrastOption,
        appTextDimFactorLight = state.appTextDimFactorLight,
        appTextDimFactorDark = state.appTextDimFactorDark,
        appSeedColor = state.appSeedColor
    ) {
        CompositionLocalProvider(
            LocalSharedStringResolver provides stringResolver,
            LocalUsePdfFileNameAsDisplayName provides state.usePdfFileNameAsDisplayName,
        ) {
        Box(Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showAppThemePanel) {
                SharedAppThemeSettingsDialog(
                    appThemeMode = state.appThemeMode,
                    appContrastOption = state.appContrastOption,
                    appTextDimFactorLight = state.appTextDimFactorLight,
                    appTextDimFactorDark = state.appTextDimFactorDark,
                    appSeedColor = state.appSeedColor,
                    customAppThemes = state.customAppThemes,
                    onThemeModeChanged = { state = state.reduce(AppAction.AppThemeChanged(it)) },
                    onContrastOptionChanged = { state = state.reduce(AppAction.AppContrastChanged(it)) },
                    onTextDimFactorLightChanged = {
                        state = state.reduce(AppAction.AppTextDimFactorLightChanged(it))
                    },
                    onTextDimFactorDarkChanged = {
                        state = state.reduce(AppAction.AppTextDimFactorDarkChanged(it))
                    },
                    onSeedColorChanged = { state = state.reduce(AppAction.AppSeedColorChanged(it)) },
                    onCustomThemeAdded = { state = state.reduce(AppAction.CustomAppThemeAdded(it)) },
                    onCustomThemeDeleted = { state = state.reduce(AppAction.CustomAppThemeDeleted(it)) },
                    onDismiss = { showAppThemePanel = false },
                )
            }
            if (showTtsSettings) {
                SharedMobileReaderTtsSettingsSheet(
                    tts = settingsTts,
                    onDismiss = { showTtsSettings = false },
                )
            }
            activeReaderBook?.let { book ->
                when (book.type) {
                    FileType.PDF -> {
                        if (pdfSplitWorkspace.isOpen) {
                            IosPdfSplitWorkspaceScreen(
                                workspace = pdfSplitWorkspace,
                                titleForDocument = { document ->
                                    resolveIosPdfSplitBook(document, state.rawLibraryBooks)?.displayName
                                        ?: document.uriString.substringAfterLast('/').ifBlank { "PDF" }
                                },
                                onFocusPane = { pane, sessionId ->
                                    focusIosPdfSplitPane(pane, sessionId)
                                },
                                onClosePane = { pane, sessionId ->
                                    closeIosPdfSplitPane(pane, sessionId)
                                },
                                onCloseWorkspace = ::closeIosPdfSplitWorkspace,
                                onSwapPanes = {
                                    val swapped = pdfSplitWorkspace.reduce(PdfSplitWorkspaceAction.PanesSwapped)
                                    pdfSplitWorkspace = swapped
                                    swapped.exitTargetDocument?.let { exitTarget ->
                                        resolveIosPdfSplitBook(exitTarget, state.rawLibraryBooks)
                                    }?.let { focusedBook ->
                                        activeReaderBook = focusedBook
                                        persistIosReaderSession(focusedBook)
                                    }
                                },
                                onOrientationChange = { orientation ->
                                    pdfSplitWorkspace = pdfSplitWorkspace.reduce(
                                        PdfSplitWorkspaceAction.OrientationChanged(
                                            orientation = orientation,
                                            expectedRevision = pdfSplitWorkspace.revision,
                                        )
                                    )
                                },
                                onDividerChange = { fraction, orientation, revision ->
                                    pdfSplitWorkspace = pdfSplitWorkspace.reduce(
                                        PdfSplitWorkspaceAction.DividerChanged(
                                            fraction = fraction,
                                            orientation = orientation,
                                            expectedRevision = revision,
                                        )
                                    )
                                },
                                onAddDocument = ::showIosPdfSplitPicker,
                                renderPane = { document, isFocused ->
                                    val paneBook = resolveIosPdfSplitBook(document, state.rawLibraryBooks)
                                    if (paneBook != null) {
                                        renderIosPdfHost(
                                            paneBook = paneBook,
                                            onBack = {
                                                val pane = when {
                                                    pdfSplitWorkspace.primary?.sessionId == document.sessionId -> {
                                                        PdfSplitPane.PRIMARY
                                                    }
                                                    pdfSplitWorkspace.secondary?.sessionId == document.sessionId -> {
                                                        PdfSplitPane.SECONDARY
                                                    }
                                                    else -> null
                                                }
                                                pane?.let { currentPane ->
                                                    closeIosPdfSplitPane(
                                                        pane = currentPane,
                                                        sessionId = document.sessionId,
                                                    )
                                                }
                                            },
                                            hostConfig = SharedPdfReaderHostConfig(
                                                sessionKey = SharedPdfReaderSessionKey(
                                                    bookId = document.canonicalBookId,
                                                    sessionId = document.sessionId,
                                                ),
                                                isFocused = isFocused,
                                                isAppActive = bridge.appLifecycleState.isActive,
                                            ),
                                            pdfTabsEnabled = false,
                                        )
                                    }
                                },
                            )
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                renderIosPdfHost(
                                    paneBook = book,
                                    onBack = { closeActiveReader(book) },
                                    hostConfig = SharedPdfReaderHostConfig.fullScreen(book.id),
                                    pdfTabsEnabled = state.isTabsEnabled,
                                )
                                TextButton(
                                    onClick = { showIosPdfSplitPicker(PdfSplitPane.SECONDARY) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                ) {
                                    Text(readerString("pdf_split_reader_open", "Open in split reader"))
                                }
                            }
                        }
                    }
                    FileType.EPUB,
                    FileType.TXT,
                    FileType.MD,
                    FileType.HTML,
                    FileType.FB2,
                    FileType.FODT,
                    FileType.MOBI,
                    FileType.CBZ,
                    FileType.CBR,
                    FileType.CB7,
                    FileType.CBT,
                    FileType.DOCX,
                    FileType.ODT,
                    FileType.PPTX -> {
                        val readerBook = remember(book.id) { loadPersistedIosEpubBookState(book) }
                        LaunchedEffect(readerBook.id, readerBrightness) {
                            if (book.type == FileType.MOBI) {
                                iosMobiLog {
                                    "Shared EPUB reader active id=${readerBook.id} persistedPosition=${readerBook.readerPosition != null}"
                                }
                            }
                            bridge.setReaderBrightness(readerBrightness)
                        }
                        SharedMobileEpubReaderScreen(
                            book = readerBook,
                            onBack = {
                                closeActiveReader(book)
                            },
                            onReaderStateChange = { snapshot ->
                                val currentBook = activeReaderBook?.takeIf { it.id == book.id } ?: readerBook
                                val sessionBook = currentBook.copy(
                                    progressPercentage = snapshot.progressPercent,
                                    lastPageIndex = snapshot.pageIndex,
                                    readerPosition = snapshot.locator,
                                    readerSettings = snapshot.settings,
                                    readerFormatIsLocal = snapshot.formatIsLocal,
                                    readerLocalFormatSettings = snapshot.localFormatSettings,
                                    readerAutoScrollIsLocal = snapshot.autoScrollIsLocal,
                                    readerAutoScrollLocalSpeed = snapshot.autoScrollLocalSpeed,
                                    readerAutoScrollLocalMinSpeed = snapshot.autoScrollLocalMinSpeed,
                                    readerAutoScrollLocalMaxSpeed = snapshot.autoScrollLocalMaxSpeed,
                                    readerBookmarks = snapshot.bookmarks,
                                    readerHighlights = snapshot.highlights,
                                )
                                val updatedBook = currentBook.withReaderSessionState(sessionBook)
                                if (updatedBook !== currentBook) {
                                    persistIosEpubBookState(updatedBook)
                                    activeReaderBook = updatedBook
                                    state = state.withUpdatedIosBook(updatedBook)
                                }
                            },
                            onMetadataLoaded = { title, author ->
                                val currentBook = activeReaderBook?.takeIf { it.id == book.id } ?: readerBook
                                val updatedBook = currentBook.withLoadedMetadata(title, author)
                                if (updatedBook !== currentBook) {
                                    persistIosEpubBookState(updatedBook)
                                    activeReaderBook = updatedBook
                                    state = state.withUpdatedIosBook(updatedBook)
                                }
                            },
                            onKeepScreenOnChange = bridge::setKeepScreenOn,
                            appIsActive = bridge.appLifecycleState.isActive,
                            appLifecycleEventId = bridge.appLifecycleState.eventId,
                            initialKeepScreenOn = loadIosKeepScreenOn(),
                            onKeepScreenOnPreferenceChange = ::persistIosKeepScreenOn,
                            onSystemUiAppearanceChange = { statusHidden, navigationHidden, lightContent, backgroundArgb ->
                                // Android keeps the reader edge-to-edge at all times with
                                // transparent system bars, letting the page theme paint below.
                                bridge.updateReaderSystemUi(
                                    statusHidden,
                                    navigationHidden,
                                    lightContent,
                                    backgroundArgb,
                                    edgeToEdge = true
                                )
                            },
                            customReaderThemes = state.customReaderThemes,
                            onCustomReaderThemesChange = { themes ->
                                state = state.reduce(AppAction.CustomReaderThemesChanged(themes))
                            },
                            customFonts = state.customFonts,
                            onImportFont = onImportFonts,
                            readerDefaultSettings = state.readerDefaultSettings,
                            onReaderDefaultSettingsChange = { defaults ->
                                state = state.reduce(AppAction.ReaderDefaultSettingsChanged(defaults))
                            },
                            readerHighlightPalette = state.readerHighlightPalette,
                            readerToolbarPreferences = state.readerToolbarPreferences,
                            onReaderToolbarPreferencesChange = { preferences ->
                                state = state.reduce(AppAction.ReaderToolbarPreferencesChanged(preferences))
                            },
                            readerTtsReplacementPreferences = state.readerTtsReplacementPreferences,
                            onReaderTtsReplacementPreferencesChange = { preferences ->
                                state = state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(preferences))
                            },
                            readerBookReplacementPreferences = state.readerBookReplacementPreferences,
                            onReaderBookReplacementPreferencesChange = { preferences ->
                                state = state.reduce(AppAction.ReaderBookReplacementPreferencesChanged(preferences))
                            },
                            onOpenDictionarySettings = { showDictionarySettingsSheet = true },
                            readerAiAvailable = readerAiAvailable,
                            readerExtrasState = readerExtrasState.copy(cloudTts = readerCloudTts.state),
                            cloudTts = readerCloudTts,
                            cloudTtsModeEnabled = effectiveReaderAiSettings.ttsModel == com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID,
                            onCloudTtsModeChange = ::updateCloudTtsMode,
                            cloudTtsVoiceId = effectiveReaderAiSettings.ttsSpeakerId,
                            onCloudTtsVoiceChange = ::updateCloudTtsVoice,
                            onClearCloudTtsCache = readerCloudTts::clearCache,
                            onAiAction = ::runReaderAiAction,
                            onAiResultDismiss = {
                                dismissReaderAiResult()
                            },
                            onOpenAiHub = {},
                            readerBrightness = readerBrightness,
                            readerCustomBrightness = readerCustomBrightness,
                            readerBrightnessSupported = true,
                            onReaderBrightnessChange = { brightness ->
                                brightness?.let { readerCustomBrightness = normalizeReaderBrightness(it) }
                                readerBrightness = brightness
                                persistIosReaderBrightness(brightness, readerCustomBrightness)
                                bridge.setReaderBrightness(brightness)
                            },
                            readerAutoScrollProfile = readerAutoScrollProfile,
                            onReaderAutoScrollProfileChange = { profile ->
                                readerAutoScrollProfile = profile.sanitized()
                                persistIosReaderAutoScrollProfile(readerAutoScrollProfile)
                            },
                            initialAutoScrollUseSlider = readerAutoScrollUseSlider,
                            onAutoScrollUseSliderPreferenceChange = { useSlider ->
                                readerAutoScrollUseSlider = useSlider
                                persistIosReaderAutoScrollUseSlider(useSlider)
                            },
                            initialAutoScrollMusicianMode = readerAutoScrollMusicianMode,
                            onAutoScrollMusicianModePreferenceChange = { enabled ->
                                readerAutoScrollMusicianMode = enabled
                                persistIosReaderAutoScrollMusicianMode(enabled)
                            },
                            initialPageSliderVisible = loadIosEpubPageSliderVisible(book.id),
                            onPageSliderVisibilityPreferenceChange = { visible ->
                                persistIosEpubPageSliderVisible(book.id, visible)
                            },
                            initialUseNativeVerticalRenderer = loadIosNativeVerticalRenderer(),
                            onUseNativeVerticalRendererPreferenceChange = ::persistIosNativeVerticalRenderer,
                            onTtsError = { message ->
                                state = state.reduce(AppAction.BannerShown(BannerMessage(message, isError = true)))
                            },
                            readerScreenOrientationMode = readerOrientation,
                            onReaderScreenOrientationModeChange = { mode ->
                                readerOrientation = mode
                                persistIosReaderOrientation(mode)
                            },
                            onApplyReaderScreenOrientation = bridge::applyReaderOrientation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> Unit
                }
                if (showDictionarySettingsSheet) {
                    SharedMobileDictionarySettingsSheet(
                        dictionaryService = lookupDictionaryService,
                        translateService = lookupTranslateService,
                        searchService = lookupSearchService,
                        onDictionaryServiceChange = { service ->
                            lookupDictionaryService = service
                            IosReaderLookupServices.dictionary = service
                            persistIosLookupService(IosLookupDictionaryServiceKey, service)
                        },
                        onTranslateServiceChange = { service ->
                            lookupTranslateService = service
                            IosReaderLookupServices.translate = service
                            persistIosLookupService(IosLookupTranslateServiceKey, service)
                        },
                        onSearchServiceChange = { service ->
                            lookupSearchService = service
                            IosReaderLookupServices.search = service
                            persistIosLookupService(IosLookupSearchServiceKey, service)
                        },
                        onDismiss = { showDictionarySettingsSheet = false },
                    )
                }
                pdfSplitPickerTarget?.let { target ->
                    IosPdfSplitPickerDialog(
                        books = iosPdfSplitPickerBooks(target),
                        title = if (pdfSplitWorkspace.isSplit) {
                            readerString("pdf_split_reader_replace_document", "Replace the selected split pane")
                        } else {
                            readerString("pdf_split_reader_choose_document", "Choose a PDF to open beside this document")
                        },
                        onDismiss = { pdfSplitPickerTarget = null },
                        onBookSelected = ::selectIosPdfSplitBook,
                    )
                }
                return@Surface
            }

            utilityScreen?.let { screen ->
                when (screen) {
                    IosUtilityScreen.ACCOUNT -> IosAccountScreen(
                        account = bridge.accountState,
                        onBack = { utilityScreen = null },
                        onAuthenticate = bridge::requestAuthentication,
                        onSignOut = { showSignOutConfirmation = true },
                    )
                    IosUtilityScreen.PRO -> IosLocalStoreKitScreen(
                        store = bridge.localStoreKitState,
                        account = bridge.accountState,
                        onBack = { utilityScreen = null },
                        onPurchase = bridge::requestLocalStoreKitPurchase,
                        onRestore = bridge::requestLocalStoreKitRestore,
                    )
                    IosUtilityScreen.DEVICES -> IosDeviceManagementScreen(
                        devices = bridge.registeredDevices,
                        isLoading = bridge.isDeviceManagementLoading,
                        status = bridge.deviceManagementStatus,
                        onBack = { utilityScreen = null },
                        onRefresh = bridge::requestDeviceManagement,
                        onRevoke = bridge::requestDeviceRevoke,
                    )
                    IosUtilityScreen.SETTINGS -> {
                        val settingsModel = sharedSettingsHubModel(
                            SharedSettingsHubInput(
                                platform = SharedSettingsPlatform.IOS,
                                isDebugBuild = bridge.isDebugBuild,
                                isSignedIn = bridge.accountState.uid != null,
                                isProUser = state.isProUser,
                                accountAvailable = true,
                                includeAccountAuthActions = true,
                                syncAvailable = true,
                                cloudSyncEligible = bridge.accountState.canSync,
                                folderSyncAvailable = true,
                                aiSettingsAvailable = true,
                                ttsSettingsAvailable = true,
                                bookCacheMaintenanceAvailable = false,
                                reflowCacheMaintenanceAvailable = true,
                                includeLanguage = true,
                                includeScreenCaptureProtection = false,
                                includeCloudLocalDataClear = true,
                                includeDiagnosticLogExport = true,
                                includeHideReaderAi = true,
                                supportProjectAvailable = true,
                                isTabsEnabled = state.isTabsEnabled,
                                isSyncEnabled = state.isSyncEnabled,
                                isFolderSyncEnabled = state.isFolderSyncEnabled,
                                useStrictFileFilter = state.useStrictFileFilter,
                                includePdfFileNameDisplayName = true,
                                usePdfFileNameAsDisplayName = state.usePdfFileNameAsDisplayName,
                                hideReaderAi = state.hideReaderAi,
                                languageSummary = sharedAppLanguageLabel(state.appLanguageTag),
                            )
                        )
                        SharedSettingsHub(
                            model = settingsModel,
                            query = settingsQuery,
                            onQueryChange = { settingsQuery = it },
                            readerDefaultSettings = state.readerDefaultSettings,
                            onReaderDefaultSettingsChange = {
                                state = state.reduce(AppAction.ReaderDefaultSettingsChanged(it))
                            },
                            pdfReaderDefaultSettings = state.pdfReaderDefaultSettings,
                            onPdfReaderDefaultSettingsChange = {
                                state = state.reduce(AppAction.PdfReaderDefaultSettingsChanged(it))
                            },
                            ttsReplacementPreferences = state.readerTtsReplacementPreferences,
                            onTtsReplacementPreferencesChange = {
                                state = state.reduce(AppAction.ReaderTtsReplacementPreferencesChanged(it))
                            },
                            readerToolbarPreferences = state.readerToolbarPreferences,
                            onReaderToolbarPreferencesChange = {
                                state = state.reduce(AppAction.ReaderToolbarPreferencesChanged(it))
                            },
                            customFonts = state.customFonts,
                            customReaderThemes = state.customReaderThemes,
                            onCustomReaderThemesChange = {
                                state = state.reduce(AppAction.CustomReaderThemesChanged(it))
                            },
                            destination = settingsDestination,
                            onDestinationChange = { settingsDestination = it },
                            onBack = {
                                settingsDestination = SharedSettingsDestination.ROOT
                                settingsQuery = ""
                                utilityScreen = null
                            },
                            onAction = { action ->
                                val portableMutation = planMobileSettingsMutation(
                                    action = action,
                                    state = MobileSettingsMutationState(
                                        tabsEnabled = state.isTabsEnabled,
                                        strictFileFilterEnabled = state.useStrictFileFilter,
                                        pdfFileNameAsDisplayName = state.usePdfFileNameAsDisplayName,
                                        folderSyncEnabled = state.isFolderSyncEnabled,
                                        hideReaderAi = state.hideReaderAi,
                                    ),
                                )
                                when (portableMutation) {
                                    is MobileSettingsMutation.SetTabsEnabled -> {
                                        state = state.reduce(AppAction.TabsEnabledChanged(portableMutation.enabled))
                                    }
                                    is MobileSettingsMutation.ChangeStrictFileFilter -> {
                                        when (portableMutation.effect) {
                                            MobileStrictFileFilterEffect.DISABLE -> {
                                                state = state.copy(useStrictFileFilter = false)
                                            }
                                            MobileStrictFileFilterEffect.CONFIRM_ENABLE -> {
                                                showStrictFilterConfirmation = true
                                            }
                                        }
                                    }
                                    is MobileSettingsMutation.SetPdfFileNameAsDisplayName -> {
                                        state = state.copy(usePdfFileNameAsDisplayName = portableMutation.enabled)
                                    }
                                    is MobileSettingsMutation.SetFolderSyncEnabled -> {
                                        setFolderSyncEnabled(portableMutation.enabled)
                                    }
                                    is MobileSettingsMutation.SetHideReaderAi -> state = state.copy(hideReaderAi = portableMutation.hidden)
                                    null -> Unit
                                }
                                when (action) {
                                    SharedSettingsAction.APP_THEME -> showAppThemePanel = true
                                    SharedSettingsAction.LANGUAGE -> {
                                        languageReturnScreen = IosUtilityScreen.SETTINGS
                                        utilityScreen = IosUtilityScreen.LANGUAGE
                                    }
                                    SharedSettingsAction.CUSTOM_FONTS -> utilityScreen = IosUtilityScreen.FONTS
                                    SharedSettingsAction.SIGN_IN -> utilityScreen = IosUtilityScreen.ACCOUNT
                                    SharedSettingsAction.SIGN_OUT -> showSignOutConfirmation = true
                                    SharedSettingsAction.CLOUD_SYNC -> {
                                        val enabled = !state.isSyncEnabled
                                        if (!enabled || canUseCloudSync(
                                                providers = bridge.accountState.providers,
                                                hasGoogleDrivePermission = bridge.accountState.googleDriveAuthorized,
                                                isProUser = state.isProUser,
                                            )
                                        ) {
                                            state = state.reduce(AppAction.SyncEnabledChanged(enabled))
                                            if (enabled) requestCloudSyncIfEligible()
                                        }
                                    }
                                    SharedSettingsAction.HELP_FEEDBACK -> utilityScreen = IosUtilityScreen.FEEDBACK
                                    SharedSettingsAction.SUPPORT -> utilityScreen = IosUtilityScreen.SUPPORT
                                    SharedSettingsAction.ABOUT -> utilityScreen = IosUtilityScreen.ABOUT
                                    SharedSettingsAction.AI_SETTINGS -> utilityScreen = IosUtilityScreen.AI_SETTINGS
                                    SharedSettingsAction.DEVICE_MANAGEMENT -> utilityScreen = IosUtilityScreen.DEVICES
                                    SharedSettingsAction.RECENT_LIMIT -> showRecentLimitDialog = true
                                    SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR -> {
                                        showExternalFileBehaviorDialog = true
                                    }
                                    SharedSettingsAction.TTS_SETTINGS -> {
                                        showTtsSettings = true
                                    }
                                    SharedSettingsAction.CLEAR_REFLOW_CACHE -> {
                                        showClearReflowCacheConfirmation = true
                                    }
                                    SharedSettingsAction.TEXT_READER_DEFAULTS,
                                    SharedSettingsAction.PDF_READER_DEFAULTS,
                                    SharedSettingsAction.READER_TOOLBAR,
                                    SharedSettingsAction.TTS_REPLACEMENTS,
                                    SharedSettingsAction.LOCAL_OVERRIDE_NOTE,
                                    SharedSettingsAction.TABS_TOGGLE,
                                    SharedSettingsAction.STRICT_FILE_FILTER,
                                    SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME,
                                    SharedSettingsAction.HIDE_READER_AI,
                                    SharedSettingsAction.FOLDER_SYNC -> Unit
                                    SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                                    SharedSettingsAction.CLEAR_BOOK_CACHE -> {
                                        showMessage("${action.name.lowercase().replace('_', ' ')} is not available on iOS")
                                    }
                                    SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA -> {
                                        showClearCloudLocalDataConfirmation = true
                                    }
                                    SharedSettingsAction.TEST_PANEL_DETECTION,
                                    SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
                                    SharedSettingsAction.DEBUG_ACTIONS -> {
                                        showMessage("${action.name.lowercase().replace('_', ' ')} is not available on iOS")
                                    }
                                    SharedSettingsAction.EXPORT_LOGS -> {
                                        if (!bridge.exportDiagnosticLogs()) {
                                            showMessage("Unable to export diagnostic logs")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    IosUtilityScreen.FONTS -> IosUtilityPage(onBack = { utilityScreen = null }) {
                        SharedCustomFontsScreen(
                            fonts = state.customFonts,
                            appFontPreference = state.appFontPreference,
                            onAppFontPreferenceChange = {
                                state = state.reduce(AppAction.AppFontPreferenceChanged(it))
                            },
                            onImportFont = onImportFonts,
                            onDeleteFont = { font ->
                                bridge.deleteImportedFont(font.path)
                                state = state.reduce(
                                    AppAction.CustomFontsChanged(state.customFonts.filterNot { it.id == font.id })
                                )
                            },
                            googleFontsAvailable = true,
                            getGoogleFonts = { googleFontNames },
                            onDownloadGoogleFont = ::downloadGoogleFont,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    IosUtilityScreen.AI_SETTINGS -> SharedAiSettingsScreen(
                        settings = effectiveReaderAiSettings,
                        maskedKeys = readerAiSettingsStore.maskedKeys(),
                        strings = SharedAiSettingsStrings(
                            title = readerString("ai_settings_title", "AI and cloud TTS"),
                            backDescription = readerString("action_back", "Back"),
                            savedKeys = readerString("ai_settings_saved_keys", "Saved API keys"),
                            noKeySaved = readerString("ai_settings_no_key_saved", "No key saved"),
                            addOrReplaceKey = readerString("ai_settings_add_or_replace_key", "Add or replace a key"),
                            providerLabel = readerString("label_provider", "Provider"),
                            apiKeyLabel = readerString("label_api_key", "API key"),
                            saveKey = readerString("ai_settings_save_key", "Save key"),
                            useOneModel = readerString("ai_settings_use_one_model", "Use one model for all AI features"),
                            useOneModelDescription = readerString(
                                "ai_settings_use_one_model_desc",
                                "Use the same model for definitions, summaries, and recaps.",
                            ),
                            allFeatures = readerString("ai_settings_all_features", "All AI features"),
                            allFeaturesDescription = readerString("ai_settings_all_features_desc", "Choose the default text model."),
                            smartDictionary = readerString("ai_settings_smart_dictionary", "Smart dictionary"),
                            smartDictionaryDescription = readerString(
                                "ai_settings_smart_dictionary_desc",
                                "Define selected words and passages.",
                            ),
                            summaries = readerString("ai_settings_summaries", "Summaries"),
                            summariesDescription = readerString("ai_settings_summaries_desc", "Generate concise summaries."),
                            recaps = readerString("ai_settings_recaps", "Recaps"),
                            recapsDescription = readerString("ai_settings_recaps_desc", "Catch up from earlier reading context."),
                            cloudTts = readerString("credits_cloud_tts_title", "Cloud TTS"),
                            cloudTtsDescription = readerString(
                                "ai_settings_cloud_tts_desc",
                                "Use %1\$s for natural reading aloud when signed in or using your own Gemini key.",
                                com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL,
                            ),
                            modelLabel = readerString("label_model", "Model"),
                            noModelSelected = readerString("ai_settings_no_model_selected", "No model selected"),
                            saveDialogDescription = readerString(
                                "dialog_save_key_desc",
                                "The key is stored securely in the iOS Keychain.",
                            ),
                            deleteDialogDescription = readerString(
                                "dialog_delete_key_desc",
                                "This removes the saved key from this device.",
                            ),
                            saveAction = readerString("action_save", "Save"),
                            deleteAction = readerString("action_delete", "Delete"),
                            cancelAction = readerString("action_cancel", "Cancel"),
                            providerLabels = mapOf("gemini" to "Gemini", "groq" to "Groq"),
                            saveDialogTitle = { provider ->
                                stringResolver.string("dialog_save_provider_key", "Save %1\$s key?", provider)
                            },
                            deleteDialogTitle = { provider ->
                                stringResolver.string("dialog_delete_provider_key", "Delete %1\$s key?", provider)
                            },
                            deleteKeyDescription = { provider ->
                                stringResolver.string("content_desc_delete_provider_key", "Delete saved %1\$s key", provider)
                            },
                        ),
                        onBackClick = { utilityScreen = null },
                        onSaveKey = { provider, key ->
                            readerAiSettingsStore.saveKey(provider, key)
                            readerAiSettings = readerAiSettingsStore.load()
                        },
                        onDeleteKey = { provider ->
                            readerAiSettingsStore.deleteKey(provider)
                            readerAiSettings = readerAiSettingsStore.load()
                        },
                        onSettingsChange = { updated ->
                            readerAiSettings = updated.sanitized()
                            readerAiSettingsStore.save(readerAiSettings)
                        },
                        cloudCacheSummary = readerCloudTts.state.cacheSummary,
                        onClearCloudTtsCache = readerCloudTts::clearCache,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IosUtilityScreen.LANGUAGE -> IosUtilityPage(onBack = { utilityScreen = languageReturnScreen }) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(sharedAppLanguages, key = { it.tag ?: "system" }) { language ->
                                TextButton(
                                    onClick = {
                                        state = state.copy(appLanguageTag = language.tag)
                                        utilityScreen = languageReturnScreen
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (language.tag == state.appLanguageTag) {
                                            "✓ ${language.label}"
                                        } else {
                                            language.label
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IosUtilityScreen.FEEDBACK -> IosUtilityPage(onBack = { utilityScreen = null }) {
                        SharedHelpFeedbackScreen(
                            onOpenGitHubIssues = {
                                openSharedMobileExternalUrl("https://github.com/Aryan-Raj3112/episteme/issues")
                            },
                            onEmailSupport = {
                                openSharedMobileExternalUrl("mailto:epistemereader@gmail.com?subject=Episteme%20iOS%20feedback")
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    IosUtilityScreen.SUPPORT -> IosUtilityPage(onBack = { utilityScreen = null }) {
                        SharedSupportProjectScreen(
                            onOpenGitHubSponsors = {
                                openSharedMobileExternalUrl("https://github.com/sponsors/Aryan-Raj3112")
                            },
                            onOpenPatreon = {
                                openSharedMobileExternalUrl("https://www.patreon.com/c/epistemereader")
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    IosUtilityScreen.ABOUT -> IosUtilityPage(onBack = { utilityScreen = null }) {
                        SharedAboutScreen(
                            versionName = iosAppVersionName(),
                            buildLabel = iosAppBuildLabel(),
                            subtitle = readerString("ios_reader_subtitle", "iOS reader"),
                            onOpenSource = {
                                openSharedMobileExternalUrl("https://github.com/Aryan-Raj3112/episteme")
                            },
                            onOpenIssues = {
                                openSharedMobileExternalUrl("https://github.com/Aryan-Raj3112/episteme/issues")
                            },
                            onOpenPrivacyPolicy = {
                                openSharedMobileExternalUrl(IosLegalLinks.privacyPolicyUrl)
                            },
                            onOpenTerms = {
                                openSharedMobileExternalUrl(IosLegalLinks.termsUrl)
                            },
                            onOpenLicenses = {
                                openSharedMobileExternalUrl(IosLegalLinks.licensesUrl)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                return@Surface
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    SharedMobileAppDrawerContent(
                        currentUser = state.currentUser,
                        isProUser = state.isProUser,
                        isStandardEdition = !bridge.localStoreKitState.available,
                        aiSettingsAvailable = true,
                        credits = state.credits,
                        isSyncEnabled = state.isSyncEnabled,
                        isFolderSyncEnabled = state.isFolderSyncEnabled,
                        onSignInClick = { runDrawerAction { utilityScreen = IosUtilityScreen.ACCOUNT } },
                        onSignOutClick = { runDrawerAction { showSignOutConfirmation = true } },
                        onSyncToggle = { enabled ->
                            if (!enabled || canUseCloudSync(
                                    providers = bridge.accountState.providers,
                                    hasGoogleDrivePermission = bridge.accountState.googleDriveAuthorized,
                                    isProUser = state.isProUser,
                                )
                            ) {
                                state = state.reduce(AppAction.SyncEnabledChanged(enabled))
                                if (enabled) requestCloudSyncIfEligible()
                            } else if (!state.isProUser) {
                                showMessage("Cloud sync requires Pro")
                            } else {
                                showMessage("Sync requires a linked Google account and Google Drive permission")
                            }
                        },
                        onFolderSyncToggle = { enabled ->
                            setFolderSyncEnabled(enabled)
                        },
                        onProClick = { runDrawerAction { utilityScreen = IosUtilityScreen.PRO } },
                        onFontsClick = { runDrawerAction { utilityScreen = IosUtilityScreen.FONTS } },
                        onAiSettingsClick = { runDrawerAction { utilityScreen = IosUtilityScreen.AI_SETTINGS } },
                        onSettingsClick = { runDrawerAction { utilityScreen = IosUtilityScreen.SETTINGS } },
                        onAppThemeClick = { runDrawerAction { showAppThemePanel = true } },
                        onFeedbackClick = { runDrawerAction { utilityScreen = IosUtilityScreen.FEEDBACK } },
                        onPrivacyPolicyClick = {
                            runDrawerAction { openSharedMobileExternalUrl(IosLegalLinks.privacyPolicyUrl) }
                        },
                        onTermsClick = {
                            runDrawerAction { openSharedMobileExternalUrl(IosLegalLinks.termsUrl) }
                        },
                        onLicensesClick = {
                            runDrawerAction { openSharedMobileExternalUrl(IosLegalLinks.licensesUrl) }
                        },
                    )
                }
            ) {
                SharedMobileMainScaffold(
                    selectedDestination = selectedPage,
                    onDestinationSelected = { page ->
                        if (selectedPage != page) {
                            state = state.copy(selectedBookIds = emptySet())
                        }
                        selectMainPage(page)
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
                                        selectMainPage(SharedMobileMainDestination.LIBRARY)
                                        state = state.withMobileLibrarySearchActive(true)
                                    }
                                    override fun navigateToFolderSync() {
                                        onImportFolder()
                                    }
                                    override fun refresh() {
                                        refreshFolders()
                                        if (state.isSyncEnabled) {
                                            requestCloudSyncIfEligible()
                                        } else {
                                            showMessage("Refreshing local folders")
                                        }
                                    }
                                    override fun clearSelection() {
                                        state = state.copy(selectedBookIds = emptySet())
                                    }
                                    override fun selectAll() {
                                        state = SharedLibraryEditor.toggleVisibleBookSelectionInState(
                                            state = state,
                                            visibleBookIds = state.mobileRecentBooks().map { it.id },
                                        )
                                    }
                                    override fun closeTab(book: BookItem) {
                                        state = state.closeTab(book.id)
                                    }
                                    override fun closeAllTabs() {
                                        state = state.reduce(AppAction.AllTabsClosed).copy(openTabs = emptyList())
                                    }
                                    override fun togglePinned(book: BookItem) {
                                        state = state.toggleHomePinned(book.id)
                                    }
                                    override fun toggleSelectedPins() {
                                        SharedLibraryEditor.toggleSelectedPinsInState(
                                            state = state,
                                            bookIds = state.selectedBookIds,
                                            isHome = true,
                                        )?.let { state = it }
                                    }
                                    override fun removeSelectedBooksFromRecents() {
                                        SharedLibraryEditor.removeBooksFromRecentsInState(
                                            state = state,
                                            bookIds = state.selectedBookIds,
                                        )?.let { state = it }
                                    }
                                    override fun addSelectedBooksToShelves(shelfIds: Set<String>) {
                                        SharedLibraryEditor.addBooksToShelvesInState(
                                            state = state,
                                            bookIds = state.selectedBookIds,
                                            shelfIds = shelfIds
                                        )?.let { state = it }
                                    }
                                    override fun createShelfFromSelectedBooks(name: String) {
                                        state = state.createIosShelf(name, state.selectedBookIds)
                                        selectMainPage(SharedMobileMainDestination.LIBRARY)
                                        selectLibraryTab(SharedMobileLibraryTab.SHELVES)
                                    }
                                    override fun updateBook(book: BookItem) {
                                        updateIosBookMetadata(book)
                                        bridge.consumeImportedCover()
                                    }
                                    override fun saveBook(book: BookItem) {
                                        if (!bridge.shareFile(book.path.orEmpty())) {
                                            showMessage("The original file is not available to save")
                                        }
                                    }
                                    override fun shareBook(book: BookItem) {
                                        if (!bridge.shareFile(book.path.orEmpty())) {
                                            showMessage("The original file is not available to share")
                                        }
                                    }
                                    override fun exportAnnotations(book: BookItem) {
                                        annotationExportBook = book
                                    }
                                    override fun importCover() = onImportCover()
                                    override fun createAndAssignTag(name: String) {
                                        SharedLibraryEditor.createAndAssignTagInState(
                                            state = state,
                                            name = name,
                                            bookIds = state.selectedBookIds,
                                        )?.let { state = it }
                                    }
                                    override fun toggleTagForSelectedBooks(tagId: String, assign: Boolean) {
                                        SharedLibraryEditor.toggleTagForBooksInState(
                                            state = state,
                                            tagId = tagId,
                                            bookIds = state.selectedBookIds,
                                            assign = assign,
                                        )?.let { state = it }
                                    }
                                    override fun deleteTag(tagId: String) {
                                        SharedLibraryEditor.deleteTagInState(state, tagId)?.let { state = it }
                                    }
                                    override fun openSettings() {
                                        utilityScreen = IosUtilityScreen.SETTINGS
                                    }
                                    override fun openAppTheme() {
                                        showAppThemePanel = true
                                    }
                                    override fun openRecentLimit() {
                                        showRecentLimitDialog = true
                                    }
                                    override fun openAbout() {
                                        utilityScreen = IosUtilityScreen.ABOUT
                                    }
                                    override fun openLanguage() {
                                        languageReturnScreen = null
                                        utilityScreen = IosUtilityScreen.LANGUAGE
                                    }
                                    override fun toggleTabs() {
                                        state = state.reduce(AppAction.TabsEnabledChanged(!state.isTabsEnabled))
                                    }
                                    override fun openExternalFileBehavior() {
                                        showExternalFileBehaviorDialog = true
                                    }
                                    override fun toggleStrictFileFilter() {
                                        if (state.useStrictFileFilter) {
                                            state = state.copy(useStrictFileFilter = false)
                                        } else {
                                            showStrictFilterConfirmation = true
                                        }
                                    }
                                    override fun togglePdfFileNameDisplay() {
                                        state = state.copy(
                                            usePdfFileNameAsDisplayName = !state.usePdfFileNameAsDisplayName
                                        )
                                    }
                                },
                                importedCoverPath = bridge.importedCoverPath,
                                modifier = Modifier.fillMaxSize()
                            )

                            SharedMobileMainDestination.LIBRARY -> SharedMobileLibraryScreen(
                                state = state,
                                selectedTab = selectedLibraryTab,
                                onTabChange = ::selectLibraryTab,
                                opdsState = opdsState,
                                onImportBooks = onImportBooks,
                                onOpenBook = ::openLibraryBook,
                                onLongPressBook = { book -> state = state.toggleBookSelection(book.id) },
                                onSearchQueryChange = { query ->
                                    state = state.withMobileLibrarySearchQuery(query)
                                },
                                onSearchActiveChange = { active ->
                                    state = state.withMobileLibrarySearchActive(active)
                                },
                                onSortOrderChange = { sortOrder -> state = state.reduce(LibraryAction.SortChanged(sortOrder)) },
                                onClearSelection = {
                                    state = state.copy(selectedBookIds = emptySet(), selectedShelfIds = emptySet())
                                },
                                onSelectAll = { visibleBookIds ->
                                    state = SharedLibraryEditor.toggleVisibleBookSelectionInState(
                                        state = state,
                                        visibleBookIds = visibleBookIds,
                                    )
                                },
                                onFilterClick = {},
                                onClearFilters = { state = state.reduce(LibraryAction.FiltersChanged(LibraryFilters())) },
                                onRemoveFilters = { filters -> state = state.reduce(LibraryAction.FiltersChanged(filters)) },
                                onSettingsClick = { utilityScreen = IosUtilityScreen.SETTINGS },
                                onNewShelfClick = {},
                                onOpenShelf = { shelf ->
                                    state = state.copy(
                                        viewingShelfId = shelf.id,
                                        isAddingBooksToShelf = false,
                                        addBooksSource = AddBooksSource.UNSHELVED,
                                        booksSelectedForAdding = emptySet(),
                                    )
                                },
                                onLongPressShelf = { shelf -> state = state.reduce(LibraryAction.ShelfSelectionToggled(shelf.id)) },
                                onTogglePinned = { book -> state = state.toggleLibraryPinned(book.id) },
                                onToggleSelectedPins = { bookIds ->
                                    SharedLibraryEditor.toggleSelectedPinsInState(
                                        state = state,
                                        bookIds = bookIds,
                                        isHome = false,
                                    )?.let { state = it }
                                },
                                onUpdateBook = { book ->
                                    updateIosBookMetadata(book)
                                    bridge.consumeImportedCover()
                                },
                                onSaveBook = { book ->
                                    if (!bridge.shareFile(book.path.orEmpty())) {
                                        showMessage("The original file is not available to save")
                                    }
                                },
                                onShareBook = { book ->
                                    if (!bridge.shareFile(book.path.orEmpty())) {
                                        showMessage("The original file is not available to share")
                                    }
                                },
                                onExportAnnotations = { book ->
                                    annotationExportBook = book
                                },
                                onImportCover = onImportCover,
                                importedCoverPath = bridge.importedCoverPath,
                                onCreateAndAssignTag = { bookIds, name ->
                                    SharedLibraryEditor.createAndAssignTagInState(
                                        state = state,
                                        name = name,
                                        bookIds = bookIds,
                                    )?.let { state = it }
                                },
                                onToggleTagForBooks = { bookIds, tagId, assign ->
                                    SharedLibraryEditor.toggleTagForBooksInState(
                                        state = state,
                                        tagId = tagId,
                                        bookIds = bookIds,
                                        assign = assign,
                                    )?.let { state = it }
                                },
                                onDeleteTag = { tagId ->
                                    SharedLibraryEditor.deleteTagInState(state, tagId)?.let { state = it }
                                },
                                onCreateShelf = { name, bookIds ->
                                    state = state.createIosShelf(name, bookIds)
                                },
                                onAddBooksToShelves = { bookIds, shelfIds ->
                                    SharedLibraryEditor.addBooksToShelvesInState(
                                        state = state,
                                        bookIds = bookIds,
                                        shelfIds = shelfIds,
                                    )?.let { state = it }
                                },
                                onRemoveBooksFromShelf = { shelf, bookIds ->
                                    SharedLibraryEditor.removeBooksFromShelf(state, shelf.id, bookIds)?.let {
                                        state = it
                                    }
                                },
                                onAddFolder = onImportFolder,
                                onScanFolders = ::refreshFolders,
                                onSyncFolderMetadata = ::refreshFolders,
                                onFolderLocalSyncChange = { folder, enabled ->
                                    state = state.copy(
                                        syncedFolders = state.syncedFolders.map {
                                            if (it.uriString == folder.uriString) it.copy(localSyncEnabled = enabled) else it
                                        },
                                    )
                                    if (enabled) refreshFolders()
                                },
                                onFolderFileTypesChange = { folder, types ->
                                    state = state
                                        .withMobileFolderFileTypes(folder, types)
                                        .state
                                    refreshFolders()
                                },
                                onRemoveFolder = { folder ->
                                    onRemoveFolder(folder.name)
                                    val folderBookIds = state.rawLibraryBooks
                                        .filter { it.sourceFolder == folder.name || it.sourceFolder == folder.uriString }
                                        .mapTo(mutableSetOf()) { it.id }
                                    state = state.copy(
                                        syncedFolders = state.syncedFolders.filterNot { it.uriString == folder.uriString },
                                        libraryFilters = state.libraryFilters.withoutIosFolderFilter(folder),
                                    ).removeIosBooks(folderBookIds)
                                },
                                onDeleteBooks = { bookIds ->
                                    val removedBooks = state.rawLibraryBooks.filter { it.id in bookIds }
                                    removedBooks.filter { it.sourceFolder == null }
                                        .mapNotNull { it.path }
                                        .let(bridge::removeImportedFiles)
                                    removedBooks.filter { it.sourceFolder != null }
                                        .groupBy { it.sourceFolder.orEmpty() }
                                        .forEach { (folder, books) ->
                                            bridge.removeFolderManagedFiles(folder, books.mapNotNull { it.path })
                                        }
                                    state = state.removeIosBooks(bookIds, recordCloudDeletion = true)
                                },
                                onDeleteShelves = { shelfIds ->
                                    SharedLibraryEditor.deleteShelvesInState(state, shelfIds)
                                        ?.let { state = it }
                                },
                                onRenameShelf = { shelf, name ->
                                    SharedLibraryEditor.renameShelfInState(state, shelf.id, name)
                                        ?.let { state = it }
                                },
                                onNavigateShelfBack = {
                                    state = state.copy(
                                        viewingShelfId = null,
                                        isAddingBooksToShelf = false,
                                        addBooksSource = AddBooksSource.UNSHELVED,
                                        booksSelectedForAdding = emptySet(),
                                    )
                                },
                                onShelfAddBooksStateChange = { isAdding, source ->
                                    state = state.copy(
                                        isAddingBooksToShelf = isAdding,
                                        addBooksSource = source,
                                        booksSelectedForAdding = emptySet(),
                                    )
                                },
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
                                onDeleteCatalogStreams = { catalogId ->
                                    val streamIds = state.rawLibraryBooks
                                        .opdsStreamBooksForCatalog(catalogId)
                                        .mapTo(linkedSetOf()) { it.id }
                                    state = state.removeIosBooks(streamIds)
                                },
                                onDownloadOpdsBook = { entry, acquisition ->
                                    scope.launch {
                                        opdsState = opdsController.updateDownloadState(
                                            entry.id,
                                            SharedOpdsDownloadState(isDownloading = true, progress = null)
                                        )
                                        val catalog = opdsState.currentCatalog
                                        val destinationFolder = opdsState.downloadLocation
                                            ?.takeIf { it.folderUriString != null }
                                            ?.let { location ->
                                                state.syncedFolders.firstOrNull {
                                                    it.uriString == location.folderUriString || it.name == location.folderName
                                                }
                                            }
                                        opdsRepository.downloadBook(
                                            entry,
                                            acquisition,
                                            catalog?.username,
                                            catalog?.password,
                                            destinationFolder
                                        ).onSuccess { result ->
                                            val folderName = result.folderName
                                            if (folderName == null) {
                                                bridge.recordImportedFiles(
                                                    fileNames = listOf(result.book.name),
                                                    filePaths = listOf(result.book.path),
                                                    autoOpen = false,
                                                )
                                                showMessage("Downloaded ${result.book.name}")
                                            } else {
                                                refreshFolders()
                                                showMessage("Downloaded ${result.book.name} to $folderName")
                                            }
                                        }.onFailure { error ->
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
                                opdsCoverContent = { entry, coverModifier ->
                                    IosOpdsCoverImage(
                                        url = entry.coverUrl,
                                        contentDescription = entry.title,
                                        repository = opdsRepository,
                                        username = opdsState.currentCatalog?.username,
                                        password = opdsState.currentCatalog?.password,
                                        modifier = coverModifier,
                                    )
                                },
                                onOpdsDownloadLocationChange = {
                                    opdsState = opdsController.setDownloadLocation(it)
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            SharedMobileMainDestination.UNIFIED_LIBRARY -> SharedMobileUnifiedLibraryScreen(
                                state = state,
                                onOpenBook = ::openLibraryBook,
                                onLongPressBook = { book -> state = state.toggleBookSelection(book.id) },
                                onTogglePinned = { book -> state = state.toggleLibraryPinned(book.id) },
                                onUpdateBook = { book -> updateIosBookMetadata(book) },
                                onCreateShelf = { name -> state = state.createIosShelf(name, emptySet()) },
                                onImportBooks = onImportBooks,
                                onAddFolder = onImportFolder,
                                onScanFolders = ::refreshFolders,
                                onSyncFolderMetadata = ::refreshFolders,
                                onFolderLocalSyncChange = { folder, enabled ->
                                    state = state.copy(
                                        syncedFolders = state.syncedFolders.map {
                                            if (it.uriString == folder.uriString) it.copy(localSyncEnabled = enabled) else it
                                        },
                                    )
                                    if (enabled) refreshFolders()
                                },
                                onFolderFileTypesChange = { folder, types ->
                                    state = state.withMobileFolderFileTypes(folder, types).state
                                    refreshFolders()
                                },
                                onRemoveFolder = { folder ->
                                    onRemoveFolder(folder.name)
                                    val folderBookIds = state.rawLibraryBooks
                                        .filter { it.sourceFolder == folder.name || it.sourceFolder == folder.uriString }
                                        .mapTo(mutableSetOf()) { it.id }
                                    state = state.copy(
                                        syncedFolders = state.syncedFolders.filterNot { it.uriString == folder.uriString },
                                        libraryFilters = state.libraryFilters.withoutIosFolderFilter(folder),
                                    ).removeIosBooks(folderBookIds)
                                },
                                onOpenSettings = { utilityScreen = IosUtilityScreen.SETTINGS },
                                onOpenAppTheme = { showAppThemePanel = true },
                                onOpenFonts = { utilityScreen = IosUtilityScreen.FONTS },
                                catalogContent = { catalogModifier ->
                                    SharedOpdsScreen(
                                        state = opdsState,
                                        localLibraryBooks = state.rawLibraryBooks,
                                        onOpenCatalog = { catalog ->
                                            scope.launch { opdsController.openCatalog(catalog) { opdsState = it } }
                                        },
                                        onOpenFeedUrl = { url ->
                                            scope.launch { opdsController.openFeedUrl(url) { opdsState = it } }
                                        },
                                        onNavigateBack = {
                                            scope.launch { opdsController.navigateBack { opdsState = it } }
                                        },
                                        onSearch = { query ->
                                            scope.launch { opdsController.search(query) { opdsState = it } }
                                        },
                                        onLoadNextPage = {
                                            scope.launch { opdsController.loadNextPage { opdsState = it } }
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
                                        onDeleteCatalogStreams = { catalogId ->
                                            val streamIds = state.rawLibraryBooks
                                                .opdsStreamBooksForCatalog(catalogId)
                                                .mapTo(linkedSetOf()) { it.id }
                                            state = state.removeIosBooks(streamIds)
                                        },
                                        onDownloadBook = { entry, acquisition ->
                                            scope.launch {
                                                opdsState = opdsController.updateDownloadState(
                                                    entry.id,
                                                    SharedOpdsDownloadState(isDownloading = true, progress = null),
                                                )
                                                val catalog = opdsState.currentCatalog
                                                val destinationFolder = opdsState.downloadLocation
                                                    ?.takeIf { it.folderUriString != null }
                                                    ?.let { location ->
                                                        state.syncedFolders.firstOrNull {
                                                            it.uriString == location.folderUriString || it.name == location.folderName
                                                        }
                                                    }
                                                opdsRepository.downloadBook(
                                                    entry,
                                                    acquisition,
                                                    catalog?.username,
                                                    catalog?.password,
                                                    destinationFolder,
                                                ).onSuccess { result ->
                                                    val folderName = result.folderName
                                                    if (folderName == null) {
                                                        bridge.recordImportedFiles(
                                                            fileNames = listOf(result.book.name),
                                                            filePaths = listOf(result.book.path),
                                                            autoOpen = false,
                                                        )
                                                        showMessage("Downloaded ${result.book.name}")
                                                    } else {
                                                        refreshFolders()
                                                        showMessage("Downloaded ${result.book.name} to $folderName")
                                                    }
                                                }.onFailure { error ->
                                                    opdsState = opdsController.setErrorMessage(
                                                        "Download failed: ${error.message ?: "unknown error"}",
                                                    )
                                                }
                                                opdsState = opdsController.updateDownloadState(entry.id, null)
                                            }
                                        },
                                        onDownloadLocationChange = {
                                            opdsState = opdsController.setDownloadLocation(it)
                                        },
                                        syncedFolders = state.syncedFolders,
                                        onReadBook = ::openLibraryBook,
                                        onStreamBook = { entry, catalog ->
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
                                        onClearError = { opdsState = opdsController.clearError() },
                                        coverContent = { entry, coverModifier ->
                                            IosOpdsCoverImage(
                                                url = entry.coverUrl,
                                                contentDescription = entry.title,
                                                repository = opdsRepository,
                                                username = opdsState.currentCatalog?.username,
                                                password = opdsState.currentCatalog?.password,
                                                modifier = coverModifier,
                                            )
                                        },
                                        mobileLayout = true,
                                        modifier = catalogModifier,
                                    )
                                },
                                initialSection = loadIosUnifiedLibrarySection(),
                                onSectionChange = ::persistIosUnifiedLibrarySection,
                                useListView = bridge.iosUnifiedLibraryListView,
                                onListViewChange = { useList ->
                                    bridge.iosUnifiedLibraryListView = useList
                                    persistIosUnifiedLibraryListView(useList)
                                },
                                audiobooks = state.audiobooks,
                                audiobookPlayback = audiobookPlaybackSnapshot,
                                onPlayAudiobook = { audiobook ->
                                    ttsListenController.stop()
                                    audiobookPlayer.connect(
                                        SharedAudiobookPlaybackRequest(
                                            bookId = audiobook.bookId,
                                            filePath = audiobook.filePath.resolvedIosImportedFilePath(),
                                            title = audiobook.title,
                                            author = audiobook.author,
                                            narrator = audiobook.narrator,
                                            album = audiobook.album,
                                            coverPath = audiobook.coverPath,
                                            positionMs = sharedAudiobookResumePosition(audiobook.positionMs),
                                            durationMs = audiobook.durationMs,
                                            speed = audiobook.playbackSpeed.takeIf { it > 0f } ?: 1f,
                                        )
                                    )
                                },
                                onToggleAudiobookPlayback = audiobookPlayer::togglePlayPause,
                                onSeekAudiobook = audiobookPlayer::seekTo,
                                onAudiobookSpeedChange = audiobookPlayer::setSpeed,
                                onAudiobookSleepTimer = { minutes -> if (minutes == null) audiobookPlayer.cancelSleepTimer() else audiobookPlayer.setSleepTimer(minutes) },
                                customSleepTimerMinutes = customSleepTimerMinutes,
                                onCustomSleepTimerMinutesChange = { values ->
                                    customSleepTimerMinutes = sanitizeCustomSleepTimerMinutes(values)
                                    persistIosCustomSleepTimerMinutes(customSleepTimerMinutes)
                                },
                                onStopAudiobookPlayback = {
                                    audiobookPlayer.stop()
                                },
                                ttsListenState = ttsListenController.state,
                                ttsProgress = ttsListenController.progressByBook.values.toList(),
                                ttsChapterTitles = ttsListenController.chapterTitlesByBook,
                                onStartTtsListen = { book, policy, chapterIndex ->
                                    iosTtsListenLog(
                                        "onStartTtsListen ENTRY bookId=${book.id} name=${book.displayName} " +
                                            "type=${book.type} policy=$policy chapterIndex=$chapterIndex " +
                                            "path=${book.path ?: "<null>"}"
                                    )
                                    audiobookPlayer.stop()
                                    ttsListenController.start(
                                        book,
                                        policy,
                                        chapterIndex,
                                        replacements = state.readerTtsReplacementPreferences,
                                    )
                                },
                                onToggleTtsPlayback = ttsListenController::togglePlay,
                                onSeekTtsChunk = ttsListenController::seekToChunk,
                                onSeekTtsChapter = ttsListenController::selectChapter,
                                onTtsSpeedChange = { rate ->
                                    ttsListenController.setParameters(rate, ttsListenController.state.pitch)
                                },
                                onTtsSleepTimer = { minutes ->
                                    if (minutes == null) {
                                        ttsListenController.cancelSleepTimer()
                                    } else {
                                        ttsListenController.startSleepTimer(minutes)
                                    }
                                },
                                onStopTtsPlayback = ttsListenController::stop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
    if (showRecentLimitDialog) {
        IosRecentLimitDialog(
            currentLimit = state.recentFilesLimit,
            onSelect = {
                state = state.copy(recentFilesLimit = it)
                showRecentLimitDialog = false
            },
            onDismiss = { showRecentLimitDialog = false },
        )
    }
    annotationExportBook?.let { book ->
        SharedAnnotationExportFormatDialog(
            title = readerString("dialog_export_annotations_title", "Export annotations"),
            cancelLabel = readerString("action_cancel", "Cancel"),
            options = sharedAnnotationExportFormatOptions(
                markdownLabel = readerString("export_annotations_markdown", "Markdown"),
                markdownDescription = readerString("export_annotations_markdown_description", "Formatted document with headings and quotes"),
                textLabel = readerString("export_annotations_text", "Plain text"),
                textDescription = readerString("export_annotations_text_description", "Simple readable list of highlights and notes"),
                jsonLabel = readerString("export_annotations_json", "JSON"),
                jsonDescription = readerString("export_annotations_json_description", "Structured data for apps and backups"),
                csvLabel = readerString("export_annotations_csv", "CSV"),
                csvDescription = readerString("export_annotations_csv_description", "Spreadsheet-friendly table of annotations")
            ),
            onDismiss = { annotationExportBook = null },
            onExport = { format ->
                annotationExportBook = null
                if (!bridge.exportAnnotations(book, format)) {
                    showMessage(stringResolver.string("banner_no_annotations_to_export", "This book has no annotations to export"))
                }
            }
        )
    }
    if (showExternalFileBehaviorDialog) {
        IosExternalFileBehaviorDialog(
            currentBehavior = state.externalFileBehavior,
            onSelect = {
                state = state.copy(externalFileBehavior = it)
                showExternalFileBehaviorDialog = false
            },
            onDismiss = { showExternalFileBehaviorDialog = false },
        )
    }
    pendingExternalClosePrompt?.let { book ->
        IosExternalFileSaveDialog(
            onConfirm = { keep, dontAskAgain ->
                if (dontAskAgain) {
                    state = state.copy(externalFileBehavior = if (keep) "KEEP" else "DELETE")
                }
                if (keep) {
                    clearPendingIosExternalFileRemoval()
                    requestCloudSyncIfEligible()
                } else {
                    removeManagedExternalBook(book)
                }
                pendingExternalClosePrompt = null
            },
        )
    }
    if (showStrictFilterConfirmation) {
        IosConfirmationDialog(
            title = readerString("dialog_strict_file_filter_title", "Enable Strict File Filter"),
            message = readerString(
                "dialog_strict_file_filter_desc",
                "If you enable this, some supported file types like AZW3, CB7, and FB2 might not show up depending on your file manager.\n\nAre you sure you want to enable this filter?",
            ),
            confirmLabel = readerString("action_enable", "Enable"),
            onConfirm = {
                state = state.copy(useStrictFileFilter = true)
                showStrictFilterConfirmation = false
            },
            onDismiss = { showStrictFilterConfirmation = false },
        )
    }
    if (showClearReflowCacheConfirmation) {
        IosConfirmationDialog(
            title = readerString("dialog_clear_reflow_cache", "Clear Reflow Cache"),
            message = readerString(
                "dialog_clear_reflow_cache_desc",
                "This removes every generated Text View (PDF reflow) book. Text Views are recreated the next time you open one.",
            ),
            confirmLabel = readerString("action_clear", "Clear"),
            onConfirm = {
                val reflowBooks = state.rawLibraryBooks.filter { it.id.endsWith("_reflow") }
                val reflowPaths = reflowBooks.mapNotNull { it.path }
                bridge.removeImportedFiles(reflowPaths)
                if (reflowBooks.isNotEmpty()) {
                    state = state.removeIosBooks(
                        bookIds = reflowBooks.map { it.id }.toSet(),
                        recordCloudDeletion = true,
                    )
                } else {
                    showMessage("No reflow cache found")
                }
                showClearReflowCacheConfirmation = false
            },
            onDismiss = { showClearReflowCacheConfirmation = false },
        )
    }
    if (showSignOutConfirmation) {
        IosConfirmationDialog(
            title = readerString("dialog_confirm_sign_out", "Confirm Sign Out"),
            message = readerString("dialog_confirm_sign_out_desc", "Are you sure you want to sign out?"),
            confirmLabel = readerString("drawer_sign_out", "Sign Out"),
            onConfirm = {
                showSignOutConfirmation = false
                bridge.requestSignOut()
            },
            onDismiss = { showSignOutConfirmation = false },
        )
    }
    if (showClearCloudLocalDataConfirmation) {
        IosConfirmationDialog(
            title = readerString("settings_clear_cloud_local_title", "Clear cloud and local data?"),
            message = readerString(
                "settings_clear_cloud_local_desc",
                "This permanently deletes your synced books, shelves, fonts, PDF sidecars, and matching local library files. Your account and purchases remain. This action cannot be undone.",
            ),
            confirmLabel = readerString("action_delete", "Delete"),
            onConfirm = {
                showClearCloudLocalDataConfirmation = false
                bridge.requestCloudLocalDataClear()
            },
            onDismiss = { showClearCloudLocalDataConfirmation = false },
        )
    }
    LaunchedEffect(state.bannerMessage) {
            val banner = state.bannerMessage ?: return@LaunchedEffect
            if (banner.isPersistent) return@LaunchedEffect
            delay(3_000L)
            state = state.reduce(AppAction.BannerDismissed)
        }
        state.bannerMessage?.let { banner ->
            IosAppTopBanner(
                bannerMessage = banner,
            )
        }
        }
    }
}

@Composable
private fun IosAppTopBanner(
    bannerMessage: BannerMessage,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (bannerMessage.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = readerBannerMessage(bannerMessage),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (bannerMessage.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun iosAppVersionName(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
        ?.takeIf(String::isNotBlank)
        ?: "Unknown version"

private fun iosAppBuildLabel(): String {
    val buildNumber = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
        ?.takeIf(String::isNotBlank)
    return if (buildNumber == null) "Standard edition" else "Standard edition · Build $buildNumber"
}

@Composable
private fun IosRecentLimitDialog(
    currentLimit: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("options_recent_limit", "Recent Files Limit")) },
        text = {
            Column {
                listOf(0, 10, 20, 50, 100).forEach { limit ->
                    val label = if (limit == 0) {
                        readerString("options_no_limit", "No limit")
                    } else {
                        readerString("options_files_limit", "%1\$d files", limit)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(limit) }
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = currentLimit == limit, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        },
    )
}

@Composable
private fun IosExternalFileBehaviorDialog(
    currentBehavior: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        Triple(
            "ASK",
            readerString("external_file_behavior_ask", "Ask Every Time"),
            readerString("external_file_behavior_ask_desc", "After closing an externally opened file, ask whether to keep it in the library or remove it."),
        ),
        Triple(
            "KEEP",
            readerString("external_file_behavior_keep", "Always Keep"),
            readerString("external_file_behavior_keep_desc", "Externally opened files are copied into the library and kept after closing."),
        ),
        Triple(
            "DELETE",
            readerString("external_file_behavior_delete", "Always Remove"),
            readerString("external_file_behavior_delete_desc", "Externally opened files are copied for reading, then removed after closing."),
        ),
        Triple(
            "TEMPORARY",
            readerString("external_file_behavior_temporary", "Open Temporarily"),
            readerString("external_file_behavior_temporary_desc", "Open directly from the source app in a temporary reader. Back returns to that app without adding the file to the library."),
        ),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("options_external_file_behavior", "External File Behavior")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label, description) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                    ) {
                        RadioButton(selected = currentBehavior == value, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label)
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        },
    )
}

@Composable
private fun IosDeviceManagementScreen(
    devices: List<com.aryan.reader.shared.DeviceItem>,
    isLoading: Boolean,
    status: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    var pendingRevoke by remember { mutableStateOf<com.aryan.reader.shared.DeviceItem?>(null) }
    LaunchedEffect(Unit) { onRefresh() }
    IosUtilityPage(onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                readerString("settings_device_management_title", "Device management"),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                readerString(
                    "settings_device_management_desc",
                    "Review devices signed in to this account. Revoking a device stops it from participating in account sync.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(readerString("settings_device_management_refresh", "Refresh devices"))
            }
            status?.let {
                Text(
                    iosOperationStatusText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLoading && devices.isEmpty()) {
                CircularProgressIndicator()
            } else if (devices.isEmpty()) {
                Text(readerString("settings_device_management_empty", "No active devices are registered for this account."))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(devices, key = { it.deviceId }) { device ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                                    device.lastSeenEpochMillis?.let { lastSeen ->
                                        Text(
                                            readerString(
                                                "settings_device_management_last_seen",
                                                "Last seen %1\$s",
                                                formatSharedMobileDateTime(lastSeen),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                TextButton(onClick = { pendingRevoke = device }) {
                                    Text(readerString("settings_device_management_revoke", "Revoke"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingRevoke?.let { device ->
        IosConfirmationDialog(
            title = readerString("settings_device_management_revoke_title", "Revoke device?"),
            message = readerString(
                "settings_device_management_revoke_desc",
                "%1\$s will no longer be allowed to participate in account sync.",
                device.deviceName,
            ),
            confirmLabel = readerString("settings_device_management_revoke", "Revoke"),
            onConfirm = {
                pendingRevoke = null
                onRevoke(device.deviceId)
            },
            onDismiss = { pendingRevoke = null },
        )
    }
}

@Composable
private fun iosOperationStatusText(status: String): String {
    val parts = status.split('|', limit = 2)
    return when (parts.firstOrNull()) {
        "device_empty" -> readerString(
            "settings_device_status_no_active",
            "No active devices are registered for this account.",
        )
        "device_active" -> readerString(
            "settings_device_status_cannot_revoke_active",
            "This device cannot be revoked while it is active.",
        )
        "device_revoked" -> readerString(
            "settings_device_status_revoked",
            "Device revoked.",
        )
        "device_revoke_failed" -> readerString(
            "settings_device_status_revoke_failed",
            "Unable to revoke the selected device.",
        )
        "device_unavailable" -> readerString(
            "settings_device_status_unavailable",
            "Device management is unavailable.",
        )
        "clear_confirmation_required" -> readerString(
            "settings_clear_cloud_local_confirmation_required",
            "Confirmation is required before clearing cloud and local data.",
        )
        "clear_authorization_required" -> readerString(
            "settings_clear_cloud_local_authorization_required",
            "Sign in and authorize Google Drive before clearing cloud and local data.",
        )
        "clear_in_progress" -> readerString(
            "settings_clear_cloud_local_in_progress",
            "Cloud sync is busy. Try clearing cloud and local data again shortly.",
        )
        "clear_unavailable" -> readerString(
            "settings_clear_cloud_local_unavailable",
            "Cloud and local data clearing is unavailable in this build.",
        )
        "clear_cleared" -> readerString(
            "settings_clear_cloud_local_success",
            "Cloud and local data cleared (removed %1\$d cloud file(s)).",
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
        )
        "clear_local_cleanup_unavailable" -> readerString(
            "settings_clear_cloud_local_cleanup_missing",
            "Cloud data cleared, but local cleanup was not available.",
        )
        "clear_failed" -> readerString(
            "settings_clear_cloud_local_failed",
            "Unable to clear cloud and local data: %1\$s",
            parts.getOrNull(1).orEmpty(),
        )
        else -> readerLiteral(status)
    }
}

@Composable
private fun IosExternalFileSaveDialog(
    onConfirm: (keep: Boolean, dontAskAgain: Boolean) -> Unit,
) {
    var dontAskAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(readerString("external_file_prompt_title", "Save File?")) },
        text = {
            Column {
                Text(
                    readerString(
                        "external_file_prompt_desc",
                        "Do you want to save this external file in the app's library? If not, it will be removed.",
                    )
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontAskAgain = !dontAskAgain },
                ) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = { dontAskAgain = it },
                    )
                    Text(readerString("external_file_dont_ask", "Don't ask again"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(true, dontAskAgain) }) {
                Text(readerString("external_file_keep", "Keep in Library"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onConfirm(false, dontAskAgain) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(readerString("external_file_delete", "Remove"))
            }
        },
    )
}

@Composable
private fun IosConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_cancel", "Cancel"))
            }
        },
    )
}

@Composable
private fun IosAccountScreen(
    account: IosAccountState,
    onBack: () -> Unit,
    onAuthenticate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    IosUtilityPage(onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(readerString("account_title", "Episteme Account"))
            Text(
                account.displayName ?: account.email ?: if (account.uid == null) {
                    readerString("unified_library_signed_out", "Not signed in")
                } else {
                    readerString("desktop_signed_in", "Signed in")
                },
                modifier = Modifier.padding(vertical = 12.dp),
            )
            TextButton(onClick = { onAuthenticate("APPLE") }) {
                Text(
                    when {
                        AccountAuthProvider.APPLE in account.providers ->
                            readerString("account_apple_linked", "Apple linked")
                        account.uid != null ->
                            readerString("account_link_apple", "Link Apple sign-in")
                        else -> readerString("account_continue_apple", "Continue with Apple")
                    }
                )
            }
            TextButton(onClick = { onAuthenticate("GOOGLE") }) {
                Text(
                    when {
                        AccountAuthProvider.GOOGLE in account.providers ->
                            readerString("account_google_linked", "Google linked")
                        account.uid != null ->
                            readerString("account_link_google", "Link Google sign-in")
                        else -> readerString("drawer_sign_in", "Continue with Google")
                    }
                )
            }
            if (account.uid != null && account.providers.size == 1) {
                Text(
                    readerString(
                        "account_linking_desc",
                        "Linking adds another way to sign in to this Episteme account. It does not create or purchase a separate account.",
                    ),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Text(
                when {
                    account.canSync -> readerString(
                        "account_google_drive_sync_available",
                        "Google Drive sync is available.",
                    )
                    AccountAuthProvider.GOOGLE in account.providers ->
                        readerString(
                            "account_authorize_google_drive",
                            "Authorize Google Drive to enable full library sync.",
                        )
                    else ->
                        readerString(
                            "account_google_required_for_sync",
                            "Sync requires Google. Apple-only accounts can use Pro and credits but cannot sync.",
                        )
                },
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (account.uid != null) {
                TextButton(onClick = onSignOut) {
                    Text(readerString("drawer_sign_out", "Sign out"))
                }
            }
            account.status?.let {
                Text(readerLiteral(it), modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun IosLocalStoreKitScreen(
    store: IosLocalStoreKitState,
    account: IosAccountState,
    onBack: () -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
) {
    IosUtilityPage(onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            Text(readerString("storekit_title", "Pro and Credits"))
            Text(
                if (store.available) {
                    readerString(
                        "storekit_available_desc",
                        "Purchases are securely linked to your Episteme account and can be restored after reinstalling.",
                    )
                } else {
                    readerString(
                        "storekit_unavailable",
                        "App Store products are currently unavailable.",
                    )
                },
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                if (store.proUnlocked) {
                    readerString("pro_unlocked", "Pro unlocked")
                } else {
                    readerString("storekit_pro_not_unlocked", "Pro not unlocked")
                },
            )
            Text(
                readerString("desktop_credits_available_format", "%1\$d credits", store.credits),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (account.uid == null) {
                Text(
                    readerString(
                        "storekit_sign_in_before_purchase",
                        "Sign in with Apple or Google before purchasing or restoring.",
                    ),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            TextButton(
                enabled = store.available && account.uid != null && !store.proUnlocked,
                onClick = { onPurchase(IosStoreKitProductIds.PRO_LIFETIME) },
            ) {
                Text(
                    readerString("storekit_buy_pro", "Buy Pro lifetime") +
                        store.proPrice?.let { " — $it" }.orEmpty(),
                )
            }
            listOf(
                IosStoreKitProductIds.CREDITS_100 to 100,
                IosStoreKitProductIds.CREDITS_300 to 300,
                IosStoreKitProductIds.CREDITS_750 to 750,
            ).forEach { (productId, amount) ->
                TextButton(
                    enabled = store.available && account.uid != null,
                    onClick = { onPurchase(productId) },
                ) {
                    Text(
                        readerString("storekit_add_credits", "Add %1\$d credits", amount) +
                            store.creditPrices[productId]?.let { " — $it" }.orEmpty(),
                    )
                }
            }
            TextButton(enabled = store.available && account.uid != null, onClick = onRestore) {
                Text(readerString("storekit_restore_purchases", "Restore purchases"))
            }
            store.status?.let {
                Text(readerLiteral(it), modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun IosUtilityPage(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(readerString("action_back", "Back"))
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            content()
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
    return withMobileBookClosed(bookId)
}

private fun SharedReaderScreenState.withIosImportsFolder(importedBooks: List<BookItem>): SharedReaderScreenState {
    val sourceNames = importedBooks.mapNotNullTo(mutableSetOf()) {
        it.sourceFolder?.takeUnless { source -> source == "iOS import" }
    }
    val folderIds = sourceNames.associateWith { name -> "ios_folder_${name.normalizedId()}" }
    val folders = sourceNames.map { sourceName ->
        val books = rawLibraryBooks.filter { it.sourceFolder == sourceName }
        Shelf(
            id = folderIds.getValue(sourceName),
            name = sourceName,
            type = ShelfType.FOLDER,
            books = books,
            directBooks = books,
        )
    }
    return copy(shelves = shelves.filterNot { it.id in folderIds.values } + folders)
}

private fun SharedReaderScreenState.withoutLegacyIosImportsFolder(): SharedReaderScreenState {
    fun BookItem.migrated() = if (sourceFolder == "iOS import") copy(sourceFolder = null) else this
    val migratedRaw = rawLibraryBooks.map { it.migrated() }
    val legacyIds = shelves
        .filter { it.type == ShelfType.FOLDER && (it.name == "iOS Imports" || it.name == "iOS import") }
        .mapTo(mutableSetOf()) { it.id }
    return copy(
        rawLibraryBooks = migratedRaw,
        libraryBooks = libraryBooks.map { it.migrated() },
        recentBooks = recentBooks.map { it.migrated() },
        openTabs = openTabs.map { it.migrated() },
        shelves = shelves.filterNot { it.id in legacyIds },
        syncedFolders = syncedFolders.filterNot {
            it.name == "iOS import" || it.name == "iOS Imports" || it.uriString == "iOS import"
        },
    )
}

private fun SharedReaderScreenState.createIosShelf(
    name: String,
    bookIds: Set<String>
): SharedReaderScreenState {
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) return this
    val id = "ios_shelf_${currentTimestamp()}"
    val books = rawLibraryBooks.filter { it.id in bookIds }
    val addedAt = currentTimestamp()
    return copy(
        shelves = shelves + Shelf(
            id = id,
            name = trimmedName,
            type = ShelfType.MANUAL,
            books = books,
            directBooks = books,
            directBookAddedAt = books.associate { it.id to addedAt },
        ),
        selectedBookIds = emptySet(),
        bannerMessage = BannerMessage("Created shelf \"$trimmedName\"")
    )
}

private fun SharedReaderScreenState.removeIosBooks(
    bookIds: Set<String>,
    recordCloudDeletion: Boolean = false,
): SharedReaderScreenState {
    if (bookIds.isEmpty()) return this
    val deletedAt = currentTimestamp()
    val newTombstones = if (recordCloudDeletion) {
        val deletedBooks = rawLibraryBooks.filter { book ->
            book.id in bookIds &&
                book.sourceFolder == null &&
                book.path?.startsWith("opds-pse://") != true &&
                SharedFileCapabilities.capabilityFor(book.type)?.syncEligible == true &&
                !SharedFileCapabilities.isManualOnlyReaderFileName(book.displayName)
        }
        (cloudBookTombstones + deletedBooks.map { book ->
            CloudBookTombstone(
                bookId = book.id,
                type = book.type.name,
                deletedAt = deletedAt,
            )
        }).groupBy(CloudBookTombstone::bookId)
            .map { (_, tombstones) -> tombstones.maxBy(CloudBookTombstone::deletedAt) }
    } else {
        cloudBookTombstones
    }
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
        cloudBookTombstones = newTombstones,
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
    return withPdfReadingProgress(
        pageIndex = state.pageIndex,
        progressPercentage = state.progressPercent,
    )
}

private fun SharedReaderScreenState.withUpdatedIosBook(book: BookItem): SharedReaderScreenState {
    fun List<BookItem>.updated(): List<BookItem> {
        return map { item -> if (item.id == book.id) book else item }
    }
    return copy(
        rawLibraryBooks = rawLibraryBooks.updated(),
        recentBooks = recentBooks.updated(),
        libraryBooks = libraryBooks.updated(),
        shelves = shelves.map { shelf ->
            shelf.copy(
                books = shelf.books.updated(),
                directBooks = shelf.directBooks.updated(),
            )
        },
        allTags = (allTags + book.tags).distinctBy { it.id }.sortedBy { it.name.lowercase() },
    )
}

private fun SharedReaderScreenState.withUpdatedIosMetadata(edited: BookItem): SharedReaderScreenState {
    val current = rawLibraryBooks.firstOrNull { it.id == edited.id } ?: return this
    return withUpdatedIosBook(current.withUserEditedMetadata(edited))
}

private fun persistIosEpubMetadataEdit(current: BookItem, edited: BookItem): Result<BookItem> = runCatching {
    val updated = current.withUserEditedMetadata(edited)
    if (current.type != FileType.EPUB || updated === current) return@runCatching updated
    val sourcePath = current.path?.takeIf(String::isNotBlank)
        ?: error("Book file is not available.")
    require(NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) {
        "Book file is not available."
    }
    val appSupport = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomains = NSUserDomainMask,
    ).firstOrNull() as? NSURL ?: error("Application Support is unavailable.")
    val backupDirectory = appSupport.URLByAppendingPathComponent("MetadataBackups", isDirectory = true)
        ?.path ?: error("Metadata backup directory is unavailable.")
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = backupDirectory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val backupPath = "$backupDirectory/${current.id.normalizedId()}.epub"
    if (!NSFileManager.defaultManager.fileExistsAtPath(backupPath)) {
        require(NSFileManager.defaultManager.copyItemAtPath(sourcePath, backupPath, error = null)) {
            "Could not preserve the original EPUB."
        }
    }
    val temporaryPath = "${NSTemporaryDirectory().trimEnd('/')}/${current.id.normalizedId()}-metadata.epub"
    NSFileManager.defaultManager.removeItemAtPath(temporaryPath, error = null)
    try {
        val restoringOriginalMetadata =
            updated.title == (current.originalTitle?.takeIf(String::isNotBlank)
                ?: current.displayName.substringBeforeLast('.', current.displayName)) &&
                updated.author == current.originalAuthor &&
                updated.description == current.originalDescription &&
                updated.seriesName == current.originalSeriesName &&
                updated.seriesIndex == current.originalSeriesIndex &&
                current.originalMetadataExists()
        val result = rewriteIosEpubMetadata(
            sourcePath = sourcePath,
            destinationPath = temporaryPath,
            title = updated.title,
            author = updated.author,
            description = updated.description,
            seriesName = updated.seriesName,
            seriesIndex = updated.seriesIndex,
            coverPath = updated.coverImagePath?.takeIf { it != current.coverImagePath },
            restoreCoverFromPath = backupPath.takeIf { restoringOriginalMetadata },
        )
        require(rename(temporaryPath, sourcePath) == 0) {
            "Could not replace the EPUB with the edited copy."
        }
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(sourcePath, error = null)
        val fileSize = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: updated.fileSize
        val modifiedAt = currentTimestamp()
        val generatedCoverPath = result.coverBytes
            ?.takeIf(ByteArray::isNotEmpty)
            ?.let { persistIosGeneratedCover(updated, it) }
        updated.copy(
            title = result.title,
            author = result.author,
            description = result.description,
            seriesName = result.seriesName,
            seriesIndex = result.seriesIndex,
            coverImagePath = generatedCoverPath ?: updated.coverImagePath,
            fileSize = fileSize,
            fileContentModifiedTimestamp = modifiedAt,
        )
    } finally {
        NSFileManager.defaultManager.removeItemAtPath(temporaryPath, error = null)
    }
}

private fun BookItem.originalMetadataExists(): Boolean =
    listOf(originalTitle, originalAuthor, originalSeriesName, originalDescription)
        .any { !it.isNullOrBlank() } || originalSeriesIndex != null

private fun performIosPdfNativeAction(
    book: BookItem,
    action: SharedMobilePdfNativeAction,
): Boolean {
    val path = book.path?.takeIf(NSFileManager.defaultManager::fileExistsAtPath) ?: return false
    val url = NSURL.fileURLWithPath(path)
    val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
    return when (action) {
        SharedMobilePdfNativeAction.SHARE -> presentIosShareSheet(url)
        SharedMobilePdfNativeAction.SAVE_COPY -> {
            presenter.presentViewController(
                UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true),
                animated = true,
                completion = null,
            )
            true
        }
        SharedMobilePdfNativeAction.PRINT -> {
            if (!UIPrintInteractionController.canPrintURL(url)) return false
            UIPrintInteractionController.sharedPrintController().apply {
                printingItem = url
                presentAnimated(true, completionHandler = null)
            }
            true
        }
        else -> false
    }
}

private fun presentIosShareSheet(url: NSURL): Boolean {
    val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
    val controller = UIActivityViewController(
        activityItems = listOf(url),
        applicationActivities = null,
    )
    controller.modalPresentationStyle = UIModalPresentationFullScreen
    presenter.presentViewController(controller, animated = true, completion = null)
    return true
}

private fun writeIosUtf8File(path: String, content: String): Boolean {
    val bytes = content.encodeToByteArray()
    val file = fopen(path, "wb") ?: return false
    val written = try {
        if (bytes.isEmpty()) {
            0uL
        } else {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
            }
        }
    } finally {
        fclose(file)
    }
    return written == bytes.size.toULong()
}

private fun persistIosGeneratedCover(book: BookItem, bytes: ByteArray): String? {
    val appSupport = (NSFileManager.defaultManager.URLsForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomains = NSUserDomainMask,
    ).firstOrNull() as? NSURL) ?: return null
    val directory = appSupport.URLByAppendingPathComponent("Covers", isDirectory = true) ?: return null
    val directoryPath = directory.path ?: return null
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directoryPath,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val extension = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "jpg"
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> "png"
        bytes.size >= 6 && bytes.decodeToString(0, 6) in setOf("GIF87a", "GIF89a") -> "gif"
        else -> "img"
    }
    val outputPath = "$directoryPath/${book.id.normalizedId()}.$extension"
    val file = fopen(outputPath, "wb") ?: return null
    val written = try {
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
        }
    } finally {
        fclose(file)
    }
    return outputPath.takeIf { written == bytes.size.toULong() }
}

private fun List<IosImportedFile>.toImportedBooks(existingBooks: List<BookItem>): List<BookItem> {
    if (isEmpty()) return emptyList()
    val existingPaths = existingBooks.mapNotNullTo(mutableSetOf()) { it.path }
    val candidates = distinctBy { it.contentId.takeIf(String::isNotBlank) ?: it.path }
        .filterNot { it.path in existingPaths }
    return SharedImportPlanner.plan(
        files = candidates.map { file ->
            ImportedBookFile(
                name = file.name,
                uriString = null,
                localPath = file.path,
                size = 0L,
                sourceFolder = file.sourceFolder.takeIf { it.isNotBlank() },
                id = file.contentId.takeIf { it.isNotBlank() }
                    ?: "ios_import_${file.path.stableIosImportedFilePath().normalizedId()}",
            )
        },
        existingBookIds = existingBooks.mapTo(mutableSetOf()) { it.id },
        platform = ReaderPlatform.IOS,
    ).importedBooks.mapIndexed { index, book ->
        book.copy(
            timestamp = currentTimestamp() - index,
            progressPercentage = 0f,
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
