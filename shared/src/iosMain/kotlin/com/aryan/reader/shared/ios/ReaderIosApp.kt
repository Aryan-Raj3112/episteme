@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.aryan.reader.shared.AnnotationExportFormat
import com.aryan.reader.shared.AnnotationExportFormatter
import com.aryan.reader.shared.AccountAuthProvider
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ImportedBookFile
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.LocalFolderSyncEngine
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.SharedImportPlanner
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedFolderScannedFile
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.UserData
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.canEnableGoogleDriveSync
import com.aryan.reader.shared.canUseCloudSync
import com.aryan.reader.shared.mergeCloudLibrarySnapshotWithDownloadedBooks
import com.aryan.reader.shared.parseTagList
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.withMobileBookOpened
import com.aryan.reader.shared.withMobileTemporaryBookClosed
import com.aryan.reader.shared.withMobileTemporaryBookOpened
import com.aryan.reader.shared.withMobileImportedBooks
import com.aryan.reader.shared.withMigratedMobileBookIdentity
import com.aryan.reader.shared.withLoadedMetadata
import com.aryan.reader.shared.withUserEditedMetadata
import com.aryan.reader.shared.withNewerReaderSession
import com.aryan.reader.shared.withPdfReadingProgress
import com.aryan.reader.shared.withReaderSessionState
import com.aryan.reader.shared.toSharedMobileLibrarySnapshot
import com.aryan.reader.shared.toSharedMobileReaderState
import com.aryan.reader.shared.sharedSettingsHubModel
import com.aryan.reader.shared.sharedAppLanguageLabel
import com.aryan.reader.shared.sharedAppLanguages
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.aryan.reader.shared.opds.SharedOpdsController
import com.aryan.reader.shared.opds.SharedOpdsDownloadState
import com.aryan.reader.shared.opds.SharedOpdsStreamUri
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfReaderStateSerializer
import com.aryan.reader.shared.ui.SharedAppTheme
import com.aryan.reader.shared.ui.SharedAppThemeSettingsDialog
import com.aryan.reader.shared.ui.SharedAboutScreen
import com.aryan.reader.shared.ui.SharedCustomFontsScreen
import com.aryan.reader.shared.ui.SharedHelpFeedbackScreen
import com.aryan.reader.shared.ui.SharedMobileAppDrawerContent
import com.aryan.reader.shared.ui.SharedMobileEpubReaderScreen
import com.aryan.reader.shared.ui.SharedMobilePdfReaderScreen
import com.aryan.reader.shared.ui.SharedMobileHomeScreen
import com.aryan.reader.shared.ui.SharedMobileHomeActions
import com.aryan.reader.shared.ui.SharedMobileLibraryScreen
import com.aryan.reader.shared.ui.LocalUsePdfFileNameAsDisplayName
import com.aryan.reader.shared.ui.SharedMobileLibraryTab
import com.aryan.reader.shared.ui.SharedMobileMainDestination
import com.aryan.reader.shared.ui.SharedMobileMainScaffold
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedStringResolver
import com.aryan.reader.shared.ui.SharedSupportProjectScreen
import com.aryan.reader.shared.ui.openSharedMobileExternalUrl
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
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
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIScreen
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

class ReaderIosBridge {
    private var systemUiHandler: ((statusHidden: Boolean, navigationHidden: Boolean, lightContent: Boolean, backgroundArgb: Long, edgeToEdge: Boolean) -> Unit)? = null
    private var latestSystemUiState: IosSystemUiState? = null
    private var originalReaderBrightness: Double? = null
    private var orientationHandler: ((mode: Int) -> Unit)? = null
    internal var importedFiles by mutableStateOf<List<IosImportedFile>>(loadPersistedImportedFiles())
        private set
    internal var importedFonts by mutableStateOf<List<CustomFontItem>>(loadIosLibrarySnapshot().customFonts)
        private set
    internal var pendingExternalOpen by mutableStateOf<IosExternalOpen?>(null)
        private set
    internal var pendingFolderScan by mutableStateOf<IosPendingFolderScan?>(null)
        private set
    internal var importedCoverPath by mutableStateOf<String?>(null)
        private set
    internal var localStoreKitState by mutableStateOf(IosLocalStoreKitState())
        private set
    internal var accountState by mutableStateOf(IosAccountState())
        private set
    internal var appLifecycleState by mutableStateOf(IosAppLifecycleState())
        private set
    private var purchaseHandler: ((String) -> Unit)? = null
    private var restorePurchasesHandler: (() -> Unit)? = null
    private var authHandler: ((String) -> Unit)? = null
    private var signOutHandler: (() -> Unit)? = null
    private var cloudSyncHandler: ((String) -> Unit)? = null
    private var cloudUploadHandler: ((String) -> Unit)? = null
    private var folderFileDeletionHandler: ((String, List<String>) -> Unit)? = null
    private var folderFileReplacementHandler: ((String, String) -> String?)? = null
    internal var pendingCloudSync by mutableStateOf<IosPendingCloudSync?>(null)
        private set
    internal var cloudSyncStatus by mutableStateOf<String?>(null)
        private set

    internal var latestNativeEvent by mutableStateOf<String?>(null)
        private set

    fun recordImportedFiles(
        fileNames: List<String>,
        filePaths: List<String> = fileNames,
        contentIds: List<String> = emptyList(),
    ) {
        if (fileNames.isEmpty()) {
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
        latestNativeEvent = "Selected ${fileNames.size} file(s) from iOS"
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
        if (!scanSucceeded) {
            latestNativeEvent = "Could not refresh $folderName; keeping the previous scan"
            return
        }
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
        importedFiles = (imported + importedFiles.filterNot { it.sourceFolder == folderName }).distinctBy { it.path }
        persistImportedFiles(importedFiles)
        pendingFolderScan = IosPendingFolderScan(folderName = folderName, files = imported)
        latestNativeEvent = if (imported.isEmpty()) {
            "No supported files found in $folderName"
        } else {
            "Imported ${imported.size} file(s) from $folderName"
        }
    }

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
    }

    fun externalFileBehavior(): String = loadIosLibrarySnapshot().externalFileBehavior

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
        if (addToLibrary) {
            recordImportedFiles(listOf(fileName), listOf(filePath), listOf(contentId))
        }
        pendingExternalOpen = IosExternalOpen(
            file = IosImportedFile(name = fileName, path = filePath, contentId = contentId),
            addToLibrary = addToLibrary,
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
        pendingFolderScan = null
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

    fun exportAnnotations(book: BookItem): Boolean {
        val document = when (book.type) {
            FileType.PDF -> AnnotationExportFormatter.fromPdfAnnotations(
                bookTitle = book.cardTitle(),
                annotations = loadPersistedIosPdfReaderState(book)?.annotations.orEmpty(),
            )
            else -> AnnotationExportFormatter.fromEpubBook(book)
        }
        if (!document.hasAnnotations) return false
        val format = AnnotationExportFormat.MARKDOWN
        val fileName = AnnotationExportFormatter.suggestedFileName(document.bookTitle, format)
        val path = NSTemporaryDirectory() + fileName
        if (!writeIosUtf8File(path, AnnotationExportFormatter.render(document, format))) return false
        return presentIosShareSheet(NSURL.fileURLWithPath(path))
    }

    fun setKeepScreenOn(enabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }

    fun updateAppActive(active: Boolean) {
        appLifecycleState = IosAppLifecycleState(
            isActive = active,
            eventId = appLifecycleState.eventId + 1,
        )
    }

    fun setReaderBrightness(brightness: Float?) {
        val screen = UIScreen.mainScreen
        if (originalReaderBrightness == null) {
            originalReaderBrightness = screen.brightness
        }
        screen.brightness = brightness?.toDouble() ?: originalReaderBrightness ?: screen.brightness
    }

    fun restoreReaderBrightness() {
        originalReaderBrightness?.let { UIScreen.mainScreen.brightness = it }
        originalReaderBrightness = null
    }

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
        )
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

internal data class IosAppLifecycleState(
    val isActive: Boolean = true,
    val eventId: Long = 0L,
)

private enum class IosUtilityScreen {
    ACCOUNT,
    PRO,
    SETTINGS,
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

internal data class IosExternalOpen(
    val file: IosImportedFile,
    val addToLibrary: Boolean,
)

internal data class IosPendingCloudSync(
    val remoteSnapshotJson: String,
    val downloadedBookPaths: Map<String, String>,
)

internal data class IosPendingFolderScan(
    val folderName: String,
    val files: List<IosImportedFile>,
)

internal data class IosFolderReplacement(
    val fileSize: Long,
    val lastModifiedTimestamp: Long,
)

private const val IosImportedFilesDefaultsKey = "reader_ios_imported_files_v1"
private const val IosImportsRelativePrefix = "Imports/"
private const val IosDocumentsRelativePrefix = "Documents/"
private const val IosCoversRelativePrefix = "Covers/"
private const val IosPdfReaderStateDefaultsPrefix = "reader_ios_pdf_state_v1_"
private const val IosEpubReaderStateDefaultsPrefix = "reader_ios_epub_state_v1_"
private const val IosReaderBrightnessDefaultsKey = "reader_ios_reader_brightness_v1"
private const val IosReaderAutoScrollSpeedDefaultsKey = "reader_ios_auto_scroll_speed_v1"
private const val IosReaderOrientationDefaultsKey = "reader_ios_reader_orientation_v1"
private const val IosKeepScreenOnDefaultsKey = "reader_ios_keep_screen_on_v1"
private const val IosReaderPreferencesDefaultsKey = "reader_ios_reader_preferences_v1"
private const val IosLibrarySnapshotDefaultsKey = "reader_ios_library_snapshot_v1"

private fun loadIosLibrarySnapshot(): SharedLibrarySnapshot {
    val defaults = NSUserDefaults.standardUserDefaults
    val encoded = defaults.stringForKey(IosLibrarySnapshotDefaultsKey)
        ?: defaults.stringForKey(IosReaderPreferencesDefaultsKey)
        ?: return SharedLibrarySnapshot()
    return SharedLibrarySnapshotJson.decodeOrEmpty(encoded).withResolvedIosBookPaths()
}

private fun persistIosLibrarySnapshot(state: SharedReaderScreenState) {
    val encoded = SharedLibrarySnapshotJson.encode(
        state.toSharedMobileLibrarySnapshot().withStableIosBookPaths()
    )
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = IosLibrarySnapshotDefaultsKey)
}

private fun SharedLibrarySnapshot.withResolvedIosBookPaths(): SharedLibrarySnapshot {
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

private fun SharedLibrarySnapshot.withStableIosBookPaths(): SharedLibrarySnapshot {
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

private fun loadIosReaderOrientation(): ReaderScreenOrientationMode {
    val name = NSUserDefaults.standardUserDefaults.stringForKey(IosReaderOrientationDefaultsKey)
    return ReaderScreenOrientationMode.entries.firstOrNull { it.name == name }
        ?: ReaderScreenOrientationMode.FOLLOW_SYSTEM
}

private fun persistIosReaderOrientation(mode: ReaderScreenOrientationMode) {
    NSUserDefaults.standardUserDefaults.setObject(mode.name, forKey = IosReaderOrientationDefaultsKey)
}

private fun loadIosReaderBrightness(): Float? {
    val stored = NSUserDefaults.standardUserDefaults.stringForKey(IosReaderBrightnessDefaultsKey) ?: return null
    return stored.toFloatOrNull()?.coerceIn(0.01f, 1f)
}

private fun persistIosReaderBrightness(brightness: Float?) {
    NSUserDefaults.standardUserDefaults.setObject(brightness?.coerceIn(0.01f, 1f)?.toString() ?: "system", forKey = IosReaderBrightnessDefaultsKey)
}

private fun loadIosReaderAutoScrollSpeed(): Float {
    return NSUserDefaults.standardUserDefaults.stringForKey(IosReaderAutoScrollSpeedDefaultsKey)
        ?.toFloatOrNull()
        ?.coerceIn(12f, 160f)
        ?: 36f
}

private fun persistIosReaderAutoScrollSpeed(speed: Float) {
    NSUserDefaults.standardUserDefaults.setObject(speed.coerceIn(12f, 160f).toString(), forKey = IosReaderAutoScrollSpeedDefaultsKey)
}

private fun loadIosKeepScreenOn(): Boolean {
    return NSUserDefaults.standardUserDefaults.boolForKey(IosKeepScreenOnDefaultsKey)
}

private fun persistIosKeepScreenOn(enabled: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = IosKeepScreenOnDefaultsKey)
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
    var state by remember {
        mutableStateOf(persistedLibrary.toSharedMobileReaderState().withoutLegacyIosImportsFolder())
    }
    LaunchedEffect(state) {
        persistIosLibrarySnapshot(state)
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
            isSyncEnabled = state.isSyncEnabled && account.canSync,
        )
    }
    var selectedPage by remember { mutableStateOf(SharedMobileMainDestination.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(SharedMobileLibraryTab.BOOKS) }
    var utilityScreen by remember { mutableStateOf<IosUtilityScreen?>(null) }
    var settingsDestination by remember { mutableStateOf(SharedSettingsDestination.ROOT) }
    var settingsQuery by remember { mutableStateOf("") }
    var showAppThemePanel by remember { mutableStateOf(false) }
    var activeReaderBook by remember { mutableStateOf<BookItem?>(null) }
    var activeTemporaryBookId by remember { mutableStateOf<String?>(null) }
    var activeTemporaryBookPath by remember { mutableStateOf<String?>(null) }
    var pendingUnavailableBookId by remember { mutableStateOf<String?>(null) }
    var readerBrightness by remember { mutableStateOf(loadIosReaderBrightness()) }
    var readerAutoScrollSpeed by remember { mutableStateOf(loadIosReaderAutoScrollSpeed()) }
    var readerOrientation by remember { mutableStateOf(loadIosReaderOrientation()) }
    var stringResolver by remember { mutableStateOf(SharedStringResolver()) }
    LaunchedEffect(state.appLanguageTag) {
        stringResolver = loadIosStringResolver(state.appLanguageTag)
    }
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

    fun cloudSyncEligible(): Boolean = state.isSyncEnabled && canUseCloudSync(
        providers = bridge.accountState.providers,
        hasGoogleDrivePermission = bridge.accountState.googleDriveAuthorized,
        isProUser = state.isProUser,
    )

    fun openLibraryBook(book: BookItem, temporary: Boolean = false) {
        if (!book.isAvailable) {
            if (cloudSyncEligible()) {
                pendingUnavailableBookId = book.id
                bridge.requestCloudSync(
                    SharedLibrarySnapshotJson.encode(
                        state.toSharedMobileLibrarySnapshot().withStableIosBookPaths()
                    )
                )
                showMessage("Downloading ${book.displayName}")
            } else {
                showMessage("This book is not available on this device")
            }
            return
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
        if (book.type == FileType.MOBI) {
            iosMobiLog {
                "Opening reader screen id=${book.id} file=${book.displayName} pathPresent=${!book.path.isNullOrBlank()}"
            }
        }
        activeReaderBook = openedBook
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
        selectedPage = SharedMobileMainDestination.LIBRARY
        selectedLibraryTab = SharedMobileLibraryTab.BOOKS
    }

    fun requestCloudSyncIfEligible() {
        if (!cloudSyncEligible()) return
        bridge.requestCloudSync(
            SharedLibrarySnapshotJson.encode(
                state.toSharedMobileLibrarySnapshot().withStableIosBookPaths()
            )
        )
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
            requestCloudSyncIfEligible()
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
                        onRefreshFolders()
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
        val localSnapshot = state.toSharedMobileLibrarySnapshot()
        val remoteSnapshot = SharedLibrarySnapshotJson.decodeOrEmpty(pending.remoteSnapshotJson)
        val mergedSnapshot = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = localSnapshot,
            remote = remoteSnapshot,
            downloadedBookPaths = pending.downloadedBookPaths,
        )
        state = mergedSnapshot
            .withResolvedIosBookPaths()
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
            SharedLibrarySnapshotJson.encode(mergedSnapshot.withStableIosBookPaths())
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
            openLibraryBook(externalBook, temporary = !request.addToLibrary)
        } else {
            showMessage("This file type is not supported")
        }
        bridge.consumeExternalOpen()
    }

    LaunchedEffect(bridge.importedFiles, bridge.pendingFolderScan) {
        bridge.pendingFolderScan?.let { scan ->
            val now = currentTimestamp()
            val configuredFolder = state.syncedFolders.firstOrNull { it.name == scan.folderName }
                ?: SyncedFolder(
                    uriString = "ios-local-folder://${scan.folderName.normalizedId()}",
                    name = scan.folderName,
                    lastScanTime = 0L,
                )
            val scannedFiles = scan.files.map { file ->
                SharedFolderScannedFile(
                    name = file.name,
                    path = file.path,
                    sourceFolder = scan.folderName,
                    relativePath = file.relativePath.ifBlank { file.name },
                    type = file.name.fileTypeFromExtension(),
                    size = file.fileSize,
                    lastModified = file.lastModifiedTimestamp,
                )
            }
            val syncResult = LocalFolderSyncEngine.syncFolder(
                state = state,
                folder = configuredFolder.copy(uriString = scan.folderName),
                files = scannedFiles,
                remoteMetadata = emptyMap(),
                nowMillis = now,
            )
            val syncedFolder = configuredFolder.copy(lastScanTime = now)
            state = syncResult.state.copy(
                syncedFolders = (
                    state.syncedFolders.filterNot { it.name == scan.folderName } + syncedFolder
                ).sortedBy { it.name.lowercase() },
            )
            if (syncResult.stats.newBooks > 0) {
                selectedPage = SharedMobileMainDestination.LIBRARY
                selectedLibraryTab = SharedMobileLibraryTab.BOOKS
                showMessage("Added ${syncResult.stats.newBooks} book(s) from ${scan.folderName}")
            } else if (
                syncResult.stats.updatedBooks > 0 ||
                syncResult.stats.removedBooks > 0 ||
                syncResult.stats.migratedBooks > 0
            ) {
                showMessage("Refreshed ${scan.folderName}")
            }
            bridge.consumeFolderScan()
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
                book.type in setOf(FileType.PDF, FileType.EPUB, FileType.MOBI, FileType.CBZ) &&
                (
                    book.coverImagePath.isNullOrBlank() ||
                        book.title.isNullOrBlank() ||
                        book.title == book.displayName.substringBeforeLast('.', book.displayName)
                )
        }
        presentationCandidates.forEach { book ->
            val presentation = extractIosBookPresentation(book)
            val coverPath = presentation.coverBytes
                ?.takeIf { it.isNotEmpty() }
                ?.let { bytes -> persistIosGeneratedCover(book, bytes) }
            if (presentation.title != null || presentation.author != null || coverPath != null) {
                state = state.withUpdatedIosBook(
                    book.copy(
                        title = presentation.title ?: book.title,
                        author = presentation.author ?: book.author,
                        coverImagePath = coverPath ?: book.coverImagePath,
                    ),
                )
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
        CompositionLocalProvider(
            LocalSharedStringResolver provides stringResolver,
            LocalUsePdfFileNameAsDisplayName provides state.usePdfFileNameAsDisplayName,
        ) {
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
            activeReaderBook?.let { book ->
                when (book.type) {
                    FileType.PDF -> {
                        val initialPdfReaderState = remember(book.id) { loadPersistedIosPdfReaderState(book) }
                        SharedMobilePdfReaderScreen(
                            book = book,
                            onBack = {
                                closeActiveReader(book)
                            },
                            onNativePdfBridgeNeeded = { pdfBook ->
                                showMessage("${pdfBook.displayName}: page ${book.lastPageIndex?.plus(1) ?: 1}")
                            },
                            initialReaderState = initialPdfReaderState,
                            readerDefaultSettings = state.pdfReaderDefaultSettings,
                            onReaderDefaultSettingsChange = { defaults ->
                                state = state.reduce(AppAction.PdfReaderDefaultSettingsChanged(defaults))
                            },
                            initialKeepScreenOn = loadIosKeepScreenOn(),
                            onKeepScreenOnPreferenceChange = ::persistIosKeepScreenOn,
                            onReaderStateChange = { pdfState ->
                                persistIosPdfReaderState(book, pdfState)
                                val updatedBook = book.withIosPdfReaderProgress(pdfState)
                                if (updatedBook != book) {
                                    activeReaderBook = updatedBook
                                    state = state.withUpdatedIosBook(updatedBook)
                                }
                            },
                            onKeepScreenOnChange = bridge::setKeepScreenOn,
                            onSystemUiAppearanceChange = bridge::updateSystemUi,
                            modifier = Modifier.fillMaxSize()
                        )
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
                            readerBrightness = readerBrightness,
                            readerBrightnessSupported = true,
                            onReaderBrightnessChange = { brightness ->
                                readerBrightness = brightness
                                persistIosReaderBrightness(brightness)
                                bridge.setReaderBrightness(brightness)
                            },
                            readerAutoScrollSpeed = readerAutoScrollSpeed,
                            onReaderAutoScrollSpeedChange = { speed ->
                                readerAutoScrollSpeed = speed.coerceIn(12f, 160f)
                                persistIosReaderAutoScrollSpeed(readerAutoScrollSpeed)
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
                return@Surface
            }

            utilityScreen?.let { screen ->
                when (screen) {
                    IosUtilityScreen.ACCOUNT -> IosAccountScreen(
                        account = bridge.accountState,
                        onBack = { utilityScreen = null },
                        onAuthenticate = bridge::requestAuthentication,
                        onSignOut = bridge::requestSignOut,
                    )
                    IosUtilityScreen.PRO -> IosLocalStoreKitScreen(
                        store = bridge.localStoreKitState,
                        account = bridge.accountState,
                        onBack = { utilityScreen = null },
                        onPurchase = bridge::requestLocalStoreKitPurchase,
                        onRestore = bridge::requestLocalStoreKitRestore,
                    )
                    IosUtilityScreen.SETTINGS -> {
                        val settingsModel = sharedSettingsHubModel(
                            SharedSettingsHubInput(
                                platform = SharedSettingsPlatform.IOS,
                                isSignedIn = bridge.accountState.uid != null,
                                isProUser = state.isProUser,
                                accountAvailable = true,
                                includeAccountAuthActions = true,
                                syncAvailable = true,
                                cloudSyncEligible = bridge.accountState.canSync,
                                folderSyncAvailable = true,
                                aiSettingsAvailable = false,
                                ttsSettingsAvailable = false,
                                bookCacheMaintenanceAvailable = false,
                                reflowCacheMaintenanceAvailable = false,
                                includeLanguage = true,
                                includeScreenCaptureProtection = false,
                                includeCloudLocalDataClear = false,
                                supportProjectAvailable = true,
                                isTabsEnabled = state.isTabsEnabled,
                                isFolderSyncEnabled = state.isFolderSyncEnabled,
                                useStrictFileFilter = state.useStrictFileFilter,
                                includePdfFileNameDisplayName = true,
                                usePdfFileNameAsDisplayName = state.usePdfFileNameAsDisplayName,
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
                                when (action) {
                                    SharedSettingsAction.APP_THEME -> showAppThemePanel = true
                                    SharedSettingsAction.LANGUAGE -> utilityScreen = IosUtilityScreen.LANGUAGE
                                    SharedSettingsAction.TABS_TOGGLE -> {
                                        state = state.reduce(AppAction.TabsEnabledChanged(!state.isTabsEnabled))
                                    }
                                    SharedSettingsAction.STRICT_FILE_FILTER -> {
                                        state = state.copy(useStrictFileFilter = !state.useStrictFileFilter)
                                    }
                                    SharedSettingsAction.CUSTOM_FONTS -> utilityScreen = IosUtilityScreen.FONTS
                                    SharedSettingsAction.SIGN_IN -> utilityScreen = IosUtilityScreen.ACCOUNT
                                    SharedSettingsAction.SIGN_OUT -> bridge.requestSignOut()
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
                                    SharedSettingsAction.FOLDER_SYNC -> {
                                        val enabled = !state.isFolderSyncEnabled
                                        state = state.reduce(AppAction.FolderSyncEnabledChanged(enabled))
                                        if (enabled && state.syncedFolders.isEmpty()) onImportFolder()
                                    }
                                    SharedSettingsAction.HELP_FEEDBACK -> utilityScreen = IosUtilityScreen.FEEDBACK
                                    SharedSettingsAction.SUPPORT -> utilityScreen = IosUtilityScreen.SUPPORT
                                    SharedSettingsAction.ABOUT -> utilityScreen = IosUtilityScreen.ABOUT
                                    SharedSettingsAction.RECENT_LIMIT -> {
                                        val next = when (state.recentFilesLimit) {
                                            in 0..6 -> 12
                                            in 7..12 -> 24
                                            else -> 6
                                        }
                                        state = state.copy(recentFilesLimit = next)
                                        showMessage("Recent books limit: $next")
                                    }
                                    SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR -> {
                                        state = state.copy(
                                            externalFileBehavior = when (state.externalFileBehavior) {
                                                "ASK" -> "COPY"
                                                "COPY" -> "TEMPORARY"
                                                else -> "ASK"
                                            }
                                        )
                                        showMessage("External files: ${state.externalFileBehavior.lowercase()}")
                                    }
                                    SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME -> {
                                        state = state.copy(
                                            usePdfFileNameAsDisplayName = !state.usePdfFileNameAsDisplayName
                                        )
                                    }
                                    SharedSettingsAction.TEXT_READER_DEFAULTS,
                                    SharedSettingsAction.PDF_READER_DEFAULTS,
                                    SharedSettingsAction.READER_TOOLBAR,
                                    SharedSettingsAction.TTS_REPLACEMENTS,
                                    SharedSettingsAction.LOCAL_OVERRIDE_NOTE -> Unit
                                    else -> showMessage("${action.name.lowercase().replace('_', ' ')} is not available on iOS")
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    IosUtilityScreen.LANGUAGE -> IosUtilityPage(onBack = { utilityScreen = IosUtilityScreen.SETTINGS }) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(sharedAppLanguages, key = { it.tag ?: "system" }) { language ->
                                TextButton(
                                    onClick = {
                                        state = state.copy(appLanguageTag = language.tag)
                                        utilityScreen = IosUtilityScreen.SETTINGS
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
                            versionName = "iOS",
                            buildLabel = "Standard edition",
                            onOpenSource = {
                                openSharedMobileExternalUrl("https://github.com/Aryan-Raj3112/episteme")
                            },
                            onOpenIssues = {
                                openSharedMobileExternalUrl("https://github.com/Aryan-Raj3112/episteme/issues")
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
                        aiSettingsAvailable = false,
                        credits = state.credits,
                        isSyncEnabled = state.isSyncEnabled,
                        isFolderSyncEnabled = state.isFolderSyncEnabled,
                        onSignInClick = { runDrawerAction { utilityScreen = IosUtilityScreen.ACCOUNT } },
                        onSignOutClick = { runDrawerAction { bridge.requestSignOut() } },
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
                            state = state.reduce(AppAction.FolderSyncEnabledChanged(enabled))
                            if (enabled && state.syncedFolders.isEmpty()) onImportFolder()
                        },
                        onProClick = { runDrawerAction { utilityScreen = IosUtilityScreen.PRO } },
                        onFontsClick = { runDrawerAction { utilityScreen = IosUtilityScreen.FONTS } },
                        onAiSettingsClick = { runDrawerAction { showMessage("AI settings bridge is next for iOS") } },
                        onSettingsClick = { runDrawerAction { utilityScreen = IosUtilityScreen.SETTINGS } },
                        onAppThemeClick = { runDrawerAction { showAppThemePanel = true } },
                        onFeedbackClick = { runDrawerAction { utilityScreen = IosUtilityScreen.FEEDBACK } }
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
                                        onImportFolder()
                                    }
                                    override fun refresh() {
                                        onRefreshFolders()
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
                                        state = state.copy(selectedBookIds = state.recentBooks.mapTo(mutableSetOf()) { it.id })
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
                                    override fun deleteSelectedBooks() {
                                        val removed = state.selectedBookIds
                                        val removedBooks = state.rawLibraryBooks.filter { it.id in removed }
                                        removedBooks.filter { it.sourceFolder == null }
                                            .mapNotNull { it.path }
                                            .let(bridge::removeImportedFiles)
                                        removedBooks.filter { it.sourceFolder != null }
                                            .groupBy { it.sourceFolder.orEmpty() }
                                            .forEach { (folder, books) ->
                                                bridge.removeFolderManagedFiles(folder, books.mapNotNull { it.path })
                                            }
                                        state = state.removeIosBooks(removed, recordCloudDeletion = true)
                                    }
                                    override fun createShelfFromSelectedBooks(name: String) {
                                        state = state.createIosShelf(name, state.selectedBookIds)
                                        selectedPage = SharedMobileMainDestination.LIBRARY
                                        selectedLibraryTab = SharedMobileLibraryTab.SHELVES
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
                                        if (!bridge.exportAnnotations(book)) {
                                            showMessage("This book has no annotations to export")
                                        }
                                    }
                                    override fun importCover() = onImportCover()
                                    override fun tagSelectedBooks(tags: String) {
                                        val parsed = parseTagList(tags, state.allTags)
                                        state.selectedBookIds.forEach { id ->
                                            state.rawLibraryBooks.firstOrNull { it.id == id }?.let { book ->
                                                state = state.withUpdatedIosMetadata(book.copy(tags = parsed))
                                            }
                                        }
                                        state = state.copy(selectedBookIds = emptySet())
                                    }
                                    override fun openSettings() {
                                        utilityScreen = IosUtilityScreen.SETTINGS
                                    }
                                    override fun openMoreActions() {
                                        utilityScreen = IosUtilityScreen.SETTINGS
                                    }
                                },
                                importedCoverPath = bridge.importedCoverPath,
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
                                onSettingsClick = { utilityScreen = IosUtilityScreen.SETTINGS },
                                onNewShelfClick = {},
                                onOpenShelf = { shelf -> state = state.copy(viewingShelfId = shelf.id) },
                                onLongPressShelf = { shelf -> state = state.reduce(LibraryAction.ShelfSelectionToggled(shelf.id)) },
                                onTogglePinned = { book -> state = state.toggleLibraryPinned(book.id) },
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
                                    if (!bridge.exportAnnotations(book)) {
                                        showMessage("This book has no annotations to export")
                                    }
                                },
                                onImportCover = onImportCover,
                                importedCoverPath = bridge.importedCoverPath,
                                onTagBooks = { bookIds, tags ->
                                    val parsed = parseTagList(tags, state.allTags)
                                    bookIds.forEach { id ->
                                        state.rawLibraryBooks.firstOrNull { it.id == id }?.let { book ->
                                            state = state.withUpdatedIosMetadata(book.copy(tags = parsed))
                                        }
                                    }
                                    state = state.copy(selectedBookIds = emptySet())
                                },
                                onCreateShelf = { name, bookIds ->
                                    state = state.createIosShelf(name, bookIds)
                                },
                                onAddFolder = onImportFolder,
                                onScanFolders = onRefreshFolders,
                                onSyncFolderMetadata = onRefreshFolders,
                                onFolderLocalSyncChange = { folder, enabled ->
                                    state = state.copy(
                                        syncedFolders = state.syncedFolders.map {
                                            if (it.uriString == folder.uriString) it.copy(localSyncEnabled = enabled) else it
                                        },
                                    )
                                },
                                onFolderFileTypesChange = { folder, types ->
                                    state = state.copy(
                                        syncedFolders = state.syncedFolders.map {
                                            if (it.uriString == folder.uriString) it.copy(allowedFileTypes = types) else it
                                        },
                                    )
                                    onRefreshFolders()
                                },
                                onRemoveFolder = { folder ->
                                    onRemoveFolder(folder.name)
                                    val folderBookIds = state.rawLibraryBooks
                                        .filter { it.sourceFolder == folder.name || it.sourceFolder == folder.uriString }
                                        .mapTo(mutableSetOf()) { it.id }
                                    state = state.copy(
                                        syncedFolders = state.syncedFolders.filterNot { it.uriString == folder.uriString },
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
                                    val removedShelves = state.shelves.filter { it.id in shelfIds }
                                    val removedFolderNames = removedShelves
                                        .filter { it.type == ShelfType.FOLDER }
                                        .mapTo(mutableSetOf()) { it.name }
                                    val removedFolderBookIds = state.rawLibraryBooks
                                        .filter { it.sourceFolder in removedFolderNames }
                                        .mapTo(mutableSetOf()) { it.id }
                                    removedFolderNames.forEach(onRemoveFolder)
                                    bridge.removeImportedFiles(
                                        state.rawLibraryBooks
                                            .filter { it.id in removedFolderBookIds }
                                            .mapNotNull { it.path }
                                    )
                                    state = state.copy(
                                        shelves = state.shelves.filterNot { it.id in shelfIds },
                                        syncedFolders = state.syncedFolders.filterNot { it.name in removedFolderNames },
                                        selectedShelfIds = emptySet(),
                                        viewingShelfId = state.viewingShelfId?.takeUnless { it in shelfIds }
                                    ).removeIosBooks(removedFolderBookIds)
                                },
                                onRenameShelf = { shelf, name ->
                                    val trimmedName = name.trim()
                                    if (trimmedName.isNotBlank()) {
                                        state = state.copy(
                                            shelves = state.shelves.map {
                                                if (it.id == shelf.id) it.copy(name = trimmedName) else it
                                            },
                                            selectedShelfIds = emptySet(),
                                        )
                                    }
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
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
    }
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
            Text("Episteme Account")
            Text(
                account.displayName ?: account.email ?: if (account.uid == null) "Not signed in" else "Signed in",
                modifier = Modifier.padding(vertical = 12.dp),
            )
            TextButton(onClick = { onAuthenticate("APPLE") }) {
                Text(if (AccountAuthProvider.APPLE in account.providers) "Apple linked" else "Continue with Apple")
            }
            TextButton(onClick = { onAuthenticate("GOOGLE") }) {
                Text(if (AccountAuthProvider.GOOGLE in account.providers) "Google linked" else "Continue with Google")
            }
            Text(
                when {
                    account.canSync -> "Google Drive sync is available."
                    AccountAuthProvider.GOOGLE in account.providers ->
                        "Authorize Google Drive to enable full library sync."
                    else ->
                        "Sync requires Google. Apple-only accounts can use Pro and credits but cannot sync."
                },
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (account.uid != null) {
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
            account.status?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
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
            Text("Pro and Credits")
            Text(
                if (store.available) {
                    "Local StoreKit testing is active. These DEBUG purchases do not grant production entitlements."
                } else {
                    "StoreKit local testing is unavailable in this build."
                },
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(if (store.proUnlocked) "Pro unlocked" else "Pro not unlocked")
            Text("${store.credits} credits", modifier = Modifier.padding(bottom = 12.dp))
            if (account.uid == null) {
                Text(
                    "Sign in with Apple or Google before purchasing or restoring.",
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            TextButton(
                enabled = store.available && account.uid != null && !store.proUnlocked,
                onClick = { onPurchase(IosStoreKitProductIds.PRO_LIFETIME) },
            ) {
                Text("Buy Pro lifetime${store.proPrice?.let { " — $it" }.orEmpty()}")
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
                    Text("Add $amount credits${store.creditPrices[productId]?.let { " — $it" }.orEmpty()}")
                }
            }
            TextButton(enabled = store.available && account.uid != null, onClick = onRestore) {
                Text("Restore purchases")
            }
            store.status?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
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
            Text("Back")
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
    val nextOpenTabIds = openTabIds.filterNot { it == bookId }
    return copy(
        openTabIds = nextOpenTabIds,
        activeTabBookId = if (activeTabBookId == bookId) nextOpenTabIds.lastOrNull() else activeTabBookId
    )
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
