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
import com.aryan.reader.shared.BannerMessage
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryAction
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.parseTagList
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.withMobileBookOpened
import com.aryan.reader.shared.withMobileImportedBooks
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
import com.aryan.reader.shared.ui.SharedMobileLibraryTab
import com.aryan.reader.shared.ui.SharedMobileMainDestination
import com.aryan.reader.shared.ui.SharedMobileMainScaffold
import com.aryan.reader.shared.ui.SharedSettingsHub
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedStringResolver
import com.aryan.reader.shared.ui.SharedSupportProjectScreen
import com.aryan.reader.shared.ui.openSharedMobileExternalUrl
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import kotlinx.coroutines.launch
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIScreen
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

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
    internal var importedCoverPath by mutableStateOf<String?>(null)
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
                path = filePaths.getOrNull(index) ?: fileName,
            )
        }
        importedFiles = (imported + importedFiles).distinctBy { it.path }
        persistImportedFiles(importedFiles)
        latestNativeEvent = "Selected ${fileNames.size} file(s) from iOS"
    }

    fun recordImportedFolder(folderName: String, fileNames: List<String>, filePaths: List<String>) {
        val imported = fileNames.mapIndexed { index, fileName ->
            IosImportedFile(
                name = fileName,
                path = filePaths.getOrNull(index) ?: fileName,
                sourceFolder = folderName,
            )
        }
        importedFiles = (imported + importedFiles.filterNot { it.sourceFolder == folderName }).distinctBy { it.path }
        persistImportedFiles(importedFiles)
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

    fun openExternalFile(fileName: String, filePath: String, addToLibrary: Boolean) {
        if (fileName.isBlank() || filePath.isBlank()) return
        if (addToLibrary) {
            recordImportedFiles(listOf(fileName), listOf(filePath))
        }
        pendingExternalOpen = IosExternalOpen(
            file = IosImportedFile(name = fileName, path = filePath),
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

    fun removeImportedFiles(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        importedFiles = importedFiles.filterNot { it.path in filePaths }
        persistImportedFiles(importedFiles)
        latestNativeEvent = "Removed ${filePaths.size} file(s) from iOS library"
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
}

private data class IosSystemUiState(
    val statusHidden: Boolean,
    val navigationHidden: Boolean,
    val lightContent: Boolean,
    val backgroundArgb: Long,
    val edgeToEdge: Boolean
)

private enum class IosUtilityScreen {
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
)

internal data class IosExternalOpen(
    val file: IosImportedFile,
    val addToLibrary: Boolean,
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
            book.copy(
                path = book.path?.resolvedIosImportedFilePath(),
                coverImagePath = book.coverImagePath?.resolvedIosCoverPath(),
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

private fun loadPersistedImportedFiles(): List<IosImportedFile> {
    val encoded = NSUserDefaults.standardUserDefaults.stringForKey(IosImportedFilesDefaultsKey) ?: return emptyList()
    return encoded
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.splitEscapedTab()
            if (parts.size !in 2..3) return@mapNotNull null
            val name = parts[0].unescapePersistedValue()
            val resolvedPath = parts[1].unescapePersistedValue().resolvedIosImportedFilePath()
            val sourceFolder = parts.getOrNull(2)
                ?.unescapePersistedValue()
                ?.takeUnless { it == "iOS import" }
                .orEmpty()
            IosImportedFile(name = name, path = resolvedPath, sourceFolder = sourceFolder)
        }
        .distinctBy { it.path }
        .toList()
}

private fun persistImportedFiles(files: List<IosImportedFile>) {
    val encoded = files.joinToString("\n") { file ->
        "${file.name.escapePersistedValue()}\t${file.path.stableIosImportedFilePath().escapePersistedValue()}\t${file.sourceFolder.escapePersistedValue()}"
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
    var selectedPage by remember { mutableStateOf(SharedMobileMainDestination.HOME) }
    var selectedLibraryTab by remember { mutableStateOf(SharedMobileLibraryTab.BOOKS) }
    var utilityScreen by remember { mutableStateOf<IosUtilityScreen?>(null) }
    var settingsDestination by remember { mutableStateOf(SharedSettingsDestination.ROOT) }
    var settingsQuery by remember { mutableStateOf("") }
    var showAppThemePanel by remember { mutableStateOf(false) }
    var activeReaderBook by remember { mutableStateOf<BookItem?>(null) }
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

    fun openLibraryBook(book: BookItem) {
        state = state.withMobileBookOpened(book)
        val openedBook = state.rawLibraryBooks.firstOrNull { it.id == book.id } ?: book
        if (book.type in IOS_NATIVE_READER_FILE_TYPES) {
            if (book.type == FileType.MOBI) {
                iosMobiLog {
                    "Opening reader screen id=${book.id} file=${book.displayName} pathPresent=${!book.path.isNullOrBlank()}"
                }
            }
            activeReaderBook = openedBook
            return
        }
        state = state.copy(
            bannerMessage = BannerMessage("${book.type.name} is not supported by the iOS reader yet")
        )
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

    LaunchedEffect(bridge.pendingExternalOpen) {
        val request = bridge.pendingExternalOpen ?: return@LaunchedEffect
        val existing = state.rawLibraryBooks.firstOrNull { it.path == request.file.path }
        val externalBook = existing
            ?: listOf(request.file).toImportedBooks(existingBooks = state.rawLibraryBooks).firstOrNull()
        if (externalBook != null) {
            if (request.addToLibrary && existing == null) {
                addBooksToLibrary(listOf(externalBook), "Added ${externalBook.displayName}")
            }
            openLibraryBook(externalBook)
        } else {
            showMessage("This file type is not supported")
        }
        bridge.consumeExternalOpen()
    }

    LaunchedEffect(bridge.importedFiles) {
        val importedPaths = bridge.importedFiles.mapTo(mutableSetOf()) { it.path }
        val managedFolderNames = state.syncedFolders.mapTo(mutableSetOf()) { it.name }
        val staleBookIds = state.rawLibraryBooks
            .filter { it.sourceFolder in managedFolderNames && it.path !in importedPaths }
            .mapTo(mutableSetOf()) { it.id }
        if (staleBookIds.isNotEmpty()) {
            state = state.removeIosBooks(staleBookIds)
        }
        val importedBooks = bridge.importedFiles.toImportedBooks(existingBooks = state.rawLibraryBooks)
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
        CompositionLocalProvider(LocalSharedStringResolver provides stringResolver) {
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
                                bridge.restoreReaderBrightness()
                                activeReaderBook = null
                            },
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
                            onBack = { activeReaderBook = null },
                            onReaderStateChange = { snapshot ->
                                val updatedBook = book.copy(
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
                    IosUtilityScreen.SETTINGS -> {
                        val settingsModel = sharedSettingsHubModel(
                            SharedSettingsHubInput(
                                platform = SharedSettingsPlatform.IOS,
                                accountAvailable = false,
                                includeAccountAuthActions = false,
                                syncAvailable = false,
                                folderSyncAvailable = true,
                                aiSettingsAvailable = false,
                                ttsSettingsAvailable = true,
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
                        isProUser = false,
                        isStandardEdition = true,
                        credits = state.credits,
                        isSyncEnabled = state.isSyncEnabled,
                        isFolderSyncEnabled = state.isFolderSyncEnabled,
                        onSignInClick = { runDrawerAction { showMessage("Sign-in bridge is next for iOS") } },
                        onSignOutClick = { runDrawerAction { showMessage("Sign-out bridge is next for iOS") } },
                        onSyncToggle = { enabled -> state = state.reduce(AppAction.SyncEnabledChanged(enabled)) },
                        onFolderSyncToggle = { enabled ->
                            state = state.reduce(AppAction.FolderSyncEnabledChanged(enabled))
                            if (enabled && state.syncedFolders.isEmpty()) onImportFolder()
                        },
                        onProClick = { runDrawerAction { showMessage("Standard iOS version is active") } },
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
                                        showMessage("Refreshing local folders")
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
                                    override fun updateBook(book: BookItem) {
                                        state = state.withUpdatedIosBook(book)
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
                                                state = state.withUpdatedIosBook(book.copy(tags = parsed))
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
                                    state = state.withUpdatedIosBook(book)
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
                                            state = state.withUpdatedIosBook(book.copy(tags = parsed))
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
                                    bridge.removeImportedFiles(
                                        state.rawLibraryBooks.filter { it.id in bookIds }.mapNotNull { it.path }
                                    )
                                    state = state.removeIosBooks(bookIds)
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
    val existingIds = existingBooks.mapTo(mutableSetOf()) { it.id }
    val existingPaths = existingBooks.mapNotNullTo(mutableSetOf()) { it.path }
    val now = currentTimestamp()
    return distinctBy { it.path }
        .filterNot { it.path in existingPaths }
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
                sourceFolder = file.sourceFolder.takeIf { it.isNotBlank() },
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
