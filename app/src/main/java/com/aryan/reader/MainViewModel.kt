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
@file:Suppress("DEPRECATION", "ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")

package com.aryan.reader

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.paginatedreader.LocatorConverter
import kotlinx.serialization.protobuf.ProtoBuf
import com.aryan.reader.paginatedreader.semanticBlockModule
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.BookMetadataEdit
import com.aryan.reader.data.CloudflareRepository
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.data.FeedbackRepository
import com.aryan.reader.data.FirestoreRepository
import com.aryan.reader.data.FontMetadata
import com.aryan.reader.data.FontsRepository
import com.aryan.reader.data.GoogleDriveRepository
import com.aryan.reader.data.LocalSyncUtils
import com.aryan.reader.data.PurchaseEntity
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.data.RemoteConfigRepository
import com.aryan.reader.data.ShelfMetadata
import com.aryan.reader.data.TagEntity
import com.aryan.reader.data.effectiveAnnotationModifiedTimestamp
import com.aryan.reader.data.effectiveReadingPositionModifiedTimestamp
import com.aryan.reader.data.getUri
import com.aryan.reader.data.toBookMetadata
import com.aryan.reader.data.toRecentFileItem
import com.aryan.reader.epub.CalibreBundleExtractor
import com.aryan.reader.epub.CalibreBundleResult
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubParser
import com.aryan.reader.epub.ImportedFileCache
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.epub.SingleFileImporter
import com.aryan.reader.epub.hasReadableExtractedContent
import com.aryan.reader.ml.ISpeechBubbleDetector
import com.aryan.reader.ml.SpeechBubble
import com.aryan.reader.ml.SpeechBubbleDetector
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.paginatedreader.data.BookProcessingWorker
import com.aryan.reader.pdf.PdfCoverGenerator
import com.aryan.reader.pdf.PDF_BLANK_PAGE_PERSISTENCE_TAG
import com.aryan.reader.pdf.PdfUserHighlight
import com.aryan.reader.pdf.PdfiumCoreProvider
import com.aryan.reader.pdf.PdfiumEngineProvider
import com.aryan.reader.pdf.PdfiumAnnotationExporter
import com.aryan.reader.pdf.ReflowWorker
import com.aryan.reader.pdf.pdfLayoutDebugSummary
import com.aryan.reader.pdf.remapPdfAnnotationsForLayoutChange
import com.aryan.reader.pdf.remapPdfBookmarksJsonForLayoutChange
import com.aryan.reader.pdf.data.PageLayoutRepository
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfHighlightRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.pptx.PptxCoverGenerator
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedImportOutcomeCounts
import com.aryan.reader.shared.SharedImportPlanner
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.shouldApplyRemoteCloudBookMetadataUpdate
import com.aryan.reader.shared.shouldDownloadRemoteCloudBookContent
import com.aryan.reader.shared.shouldUploadLocalCloudBookContent
import com.aryan.reader.shared.shouldUploadLocalCloudBookMetadataUpdate
import com.aryan.reader.shared.sharedCloudBookContentFileName
import com.aryan.reader.shared.AppAction as SharedAppAction
import com.aryan.reader.shared.LibraryAction as SharedLibraryAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit

private data class SpeechBubbleCacheKey(
    val documentId: String,
    val pageIndex: Int
)

private data class CachedSpeechBubble(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
    val maskBitmap: Bitmap?
)

private data class PendingExternalFileRemoval(
    val bookId: String,
    val uriString: String?
)

private const val BANNER_AUTO_DISMISS_MILLIS = 3_000L
private const val CLOUD_CONTENT_RETRY_DELAY_MILLIS = 10_000L
private const val CLOUD_METADATA_UPLOAD_DEBOUNCE_MILLIS = 1_500L

@kotlin.OptIn(ExperimentalSerializationApi::class)
@UnstableApi
open class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Context = application.applicationContext
    private val authRepository = AuthRepository(appContext)
    private val recentFilesRepository = RecentFilesRepository(appContext)

    private val pdfTextRepository by lazy { PdfTextRepository(appContext) }
    private val bookCacheDao by lazy { BookCacheDatabase.getDatabase(application).bookCacheDao() }
    private val epubParser by lazy { EpubParser(appContext) }
    private val mobiParser by lazy { MobiParser(appContext) }
    private val fb2Parser by lazy { com.aryan.reader.epub.Fb2Parser(appContext) }
    private val odtParser by lazy { com.aryan.reader.epub.OdtParser(appContext) }
    private val singleFileImporter by lazy { SingleFileImporter(appContext) }
    private val bookImporter by lazy { BookImporter(appContext) }
    private val epubMetadataFileEditor by lazy { EpubMetadataFileEditor(appContext) }
    private val pageLayoutRepository by lazy { PageLayoutRepository(appContext) }
    private val pdfRichTextRepository by lazy { com.aryan.reader.pdf.PdfRichTextRepository(appContext) }
    private val pdfTextBoxRepository by lazy { PdfTextBoxRepository(appContext) }
    private val pdfHighlightRepository by lazy { PdfHighlightRepository(appContext) }
    private val pdfAnnotationRepository by lazy { PdfAnnotationRepository(appContext) }

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
    private val _prefsUpdateFlow = MutableStateFlow(0L)
    private val prefsListener: SharedPreferences.OnSharedPreferenceChangeListener
    private val feedbackRepository = FeedbackRepository(appContext)
    private val libraryStateProjector = LibraryStateProjector(AndroidFolderPathResolver())
    private var feedbackListener: Any? = null
    private val importMutex = Mutex()
    private val epubRecoveryMutex = Mutex()
    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    @Suppress("unused")
    val navigationEvent = _navigationEvent.receiveAsFlow()
    private var bannerDismissJob: Job? = null
    private var bannerDismissGeneration = 0L
    private var pendingSwitchDeferred: CompletableDeferred<Boolean>? = null
    private var externalOpenedBookId: String? = null
    private var cloudContentRetryJob: Job? = null
    private val cloudMetadataUploadJobs = ConcurrentHashMap<String, Job>()

    private var panelDetector: com.aryan.reader.ml.IPanelDetector? = null
    private var speechBubbleDetector: ISpeechBubbleDetector? = null

    private val mlDispatcher = newSingleThreadExecutor().asCoroutineDispatcher()
    private val speechBubbleCacheMutex = Mutex()
    private val speechBubbleCache = ConcurrentHashMap<SpeechBubbleCacheKey, List<CachedSpeechBubble>>()
    private val speechBubbleDetectionJobs = ConcurrentHashMap<SpeechBubbleCacheKey, Deferred<List<CachedSpeechBubble>>>()

    private fun getOrInitDetector(context: Context): com.aryan.reader.ml.IPanelDetector? {
        if (panelDetector == null && BuildConfig.DEBUG) {
            val modelFile = File(context.getExternalFilesDir(null), "best_float16.tflite")
            if (modelFile.exists()) {
                try {
                    val clazz = Class.forName(
                        "com.aryan.reader.ml.ComicPanelDetector",
                        false,
                        context.classLoader
                    )
                    panelDetector = clazz.getConstructor(File::class.java).newInstance(modelFile) as com.aryan.reader.ml.IPanelDetector
                } catch (e: Exception) {
                    Timber.e(e, "Failed to instantiate ComicPanelDetector via reflection")
                }
            } else {
                Timber.e("Model file best_float16.tflite not found in external files dir")
            }
        }
        return panelDetector
    }

    private fun getOrInitSpeechBubbleDetector(context: Context): ISpeechBubbleDetector? {
        if (speechBubbleDetector == null && BuildConfig.FLAVOR != "oss") {
            val modelFile = File(context.getExternalFilesDir(null), "manga_speech_bubble_v3.ort")
            if (modelFile.exists()) {
                try {
                    speechBubbleDetector = SpeechBubbleDetector(modelFile)
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to instantiate SpeechBubbleDetector. Deleting corrupted model.")
                    modelFile.delete()
                }
            } else {
                Timber.e("Model file manga_speech_bubble_v3.ort not found in external files dir")
            }
        }
        return speechBubbleDetector
    }

    private fun normalizeSpeechBubbles(
        bubbles: List<SpeechBubble>,
        width: Int,
        height: Int
    ): List<CachedSpeechBubble> {
        if (width <= 0 || height <= 0) return emptyList()
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        return bubbles.mapNotNull { bubble ->
            val bounds = bubble.bounds
            if (bounds.width() <= 0f || bounds.height() <= 0f) {
                null
            } else {
                CachedSpeechBubble(
                    leftFraction = (bounds.left / widthF).coerceIn(0f, 1f),
                    topFraction = (bounds.top / heightF).coerceIn(0f, 1f),
                    rightFraction = (bounds.right / widthF).coerceIn(0f, 1f),
                    bottomFraction = (bounds.bottom / heightF).coerceIn(0f, 1f),
                    maskBitmap = bubble.maskBitmap
                )
            }
        }
    }

    private fun scaleCachedSpeechBubbles(
        bubbles: List<CachedSpeechBubble>,
        width: Int,
        height: Int
    ): List<SpeechBubble> {
        if (width <= 0 || height <= 0) return emptyList()
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        return bubbles.mapNotNull { bubble ->
            val bounds = android.graphics.RectF(
                bubble.leftFraction * widthF,
                bubble.topFraction * heightF,
                bubble.rightFraction * widthF,
                bubble.bottomFraction * heightF
            )
            if (bounds.width() <= 0f || bounds.height() <= 0f) {
                null
            } else {
                SpeechBubble(bounds = bounds, maskBitmap = bubble.maskBitmap)
            }
        }
    }

    fun hasCachedSpeechBubbles(documentId: String, pageIndex: Int): Boolean {
        return speechBubbleCache.containsKey(SpeechBubbleCacheKey(documentId, pageIndex))
    }

    fun testSpeechBubbleDetection(context: Context) {
        viewModelScope.launch(mlDispatcher) {
            try { // <--- We now wrap the WHOLE thing in a Throwable catch
                val modelFile = File(context.getExternalFilesDir(null), "manga_speech_bubble_v3.ort")
                if (!modelFile.exists()) {
                    withContext(Dispatchers.Main) { showBanner("ONNX Model not found", isError = true) }
                    return@launch
                }

                val cbzItem = uiState.value.contextualActionItems.firstOrNull { it.type == FileType.CBZ }
                    ?: uiState.value.allRecentFiles.firstOrNull { it.type == FileType.CBZ }

                if (cbzItem == null) {
                    withContext(Dispatchers.Main) { showBanner("No CBZ found in Library.", isError = true) }
                    return@launch
                }

                val uri = cbzItem.getUri() ?: return@launch
                Timber.d("BUBBLE TEST START: ${cbzItem.displayName}")

                var cacheFile: File? = null
                try {
                    Timber.d("Initializing Speech Bubble Detector...")
                    val detector = getOrInitSpeechBubbleDetector(context) ?: run {
                        withContext(Dispatchers.Main) { showBanner("ONNX Model could not be loaded", isError = true) }
                        return@launch
                    }
                    Timber.d("Detector successfully initialized. Copying CBZ to cache...")

                    cacheFile = File(context.cacheDir, "temp_test_bubble.cbz")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    Timber.d("CBZ copied to cache successfully. Opening archive...")

                    val archiveDoc = com.aryan.reader.pdf.ArchiveDocumentWrapper(cacheFile)
                    val totalPages = archiveDoc.getPageCount()
                    Timber.d("Archive opened. Total pages: $totalPages")

                    val targetIndex = 2
                    if (targetIndex < totalPages) {
                        Timber.d("Reading page $targetIndex...")
                        val page = archiveDoc.openPage(targetIndex)
                        if (page != null) {
                            val w = page.getPageWidthPoint()
                            val h = page.getPageHeightPoint()
                            if (w > 0 && h > 0) {
                                Timber.d("Rendering bitmap: $w x $h...")
                                val bitmap = androidx.core.graphics.createBitmap(w, h)
                                page.renderPageBitmap(bitmap, 0, 0, w, h, false)

                                Timber.d("Running ONNX Inference...")
                                val pageStartTime = System.currentTimeMillis()
                                val bubbles = detector.detectBubbles(bitmap, confidenceThreshold = 0.4f)
                                val pageDuration = System.currentTimeMillis() - pageStartTime

                                val logLine = "Page $targetIndex: ${pageDuration}ms (Found ${bubbles.size} bubbles)"
                                Timber.d(">>> [BUBBLE] $logLine")

                                withContext(Dispatchers.Main) {
                                    showBanner("Bubble Test Complete! $logLine")
                                }
                                bitmap.recycle()
                            }
                            page.close()
                        }
                    } else {
                        Timber.e("Page $targetIndex out of bounds")
                        withContext(Dispatchers.Main) {
                            showBanner("CBZ does not have a 3rd page.", isError = true)
                        }
                    }
                    archiveDoc.close()
                    Timber.d("Archive closed cleanly.")
                } finally {
                    cacheFile?.delete()
                }
            } catch (t: Throwable) {
                Timber.e(t, "Fatal error during bubble test")
            }
        }
    }

    val speechBubbleModelDownloadProgress = MutableStateFlow<Float?>(null)

    fun isSpeechBubbleModelAvailable(context: Context): Boolean {
        return File(context.getExternalFilesDir(null), "manga_speech_bubble_v3.ort").exists()
    }

    fun downloadSpeechBubbleModel(context: Context) {
        if (speechBubbleModelDownloadProgress.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            speechBubbleModelDownloadProgress.value = 0f
            var success = false
            val modelFile = File(context.getExternalFilesDir(null), "manga_speech_bubble_v3.ort")
            val tempFile = File(context.getExternalFilesDir(null), "manga_speech_bubble_v3.ort.tmp")
            val urlString = "https://huggingface.co/1m4ryan/speech-bubble-detector/resolve/main/manga_speech_bubble_v3.ort"

            var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
            val maxRetries = 3
            var retryCount = 0

            while (retryCount < maxRetries && !success) {
                try {
                    val url = java.net.URL(urlString)
                    val connection = url.openConnection() as java.net.HttpURLConnection

                    if (downloadedBytes > 0) {
                        connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                    }

                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.connect()

                    val responseCode = connection.responseCode
                    val isPartial = responseCode == java.net.HttpURLConnection.HTTP_PARTIAL

                    if (responseCode == java.net.HttpURLConnection.HTTP_OK && downloadedBytes > 0) {
                        downloadedBytes = 0L
                    } else if (responseCode != java.net.HttpURLConnection.HTTP_OK && !isPartial) {
                        throw Exception("HTTP error code: $responseCode")
                    }

                    val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                    val totalFileLength = if (contentLength != -1L) downloadedBytes + contentLength else -1L

                    val input = connection.inputStream
                    val output = FileOutputStream(tempFile, isPartial)
                    val data = ByteArray(16 * 1024)
                    var count: Int

                    while (input.read(data).also { count = it } != -1) {
                        output.write(data, 0, count)
                        downloadedBytes += count
                        if (totalFileLength > 0) {
                            speechBubbleModelDownloadProgress.value = (downloadedBytes.toFloat() / totalFileLength).coerceIn(0f, 1f)
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()

                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (modelFile.exists()) modelFile.delete()
                        if (tempFile.renameTo(modelFile)) {
                            success = true
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to download Bubble Zoom model, attempt ${retryCount + 1}")
                    retryCount++
                    if (retryCount >= maxRetries) break
                    delay(2000)
                    downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
                }
            }

            speechBubbleModelDownloadProgress.value = null
            withContext(Dispatchers.Main) {
                if (success) {
                    showBanner("Bubble Zoom model downloaded successfully!")
                } else {
                    showBanner("Download failed. Please keep the app open during download.", isError = true)
                }
            }
        }
    }

    data class PageModificationResult(
        val layout: List<VirtualPage>,
        val annotations: Map<Int, List<PdfAnnotation>>,
        val bookmarksJson: String
    )

    val proUpgradeState = billingClientWrapper.proUpgradeState

    val ttsController by lazy { TtsController(appContext).apply { connect() } }

    private var backgroundTtsBook: EpubBook? = null
    private var backgroundTtsBookId: String? = null
    private var backgroundTtsCoverPath: String? = null

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
            mainScreenStartPage = prefs.getInt(KEY_MAIN_SCREEN_START_PAGE, 0).coerceIn(0, 1),
            libraryScreenStartPage = prefs.getInt(
                KEY_LIBRARY_SCREEN_START_PAGE,
                0
            ).coerceIn(0, if (BuildConfig.IS_OFFLINE) 2 else 3),
            viewingShelfId = prefs.getString(KEY_LAST_VIEWING_SHELF_ID, null),
            isAddingBooksToShelf = prefs.getBoolean(KEY_LAST_ADDING_BOOKS_TO_SHELF, false),
            currentUser = authRepository.getSignedInUser(),
            isSyncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, false),
            isFolderSyncEnabled = prefs.getBoolean(KEY_FOLDER_SYNC_ENABLED, false),
            libraryFilters = LibraryFilters(
                fileTypes = prefs.getStringSet(KEY_FILTER_FILE_TYPES, emptySet())?.mapNotNull {
                    runCatching { FileType.valueOf(it) }.getOrNull()
                }?.filterTo(mutableSetOf()) { it in ANDROID_READABLE_FILE_TYPES } ?: emptySet(),
                sourceFolders = prefs.getStringSet(KEY_FILTER_FOLDERS, emptySet()) ?: emptySet(),
                readStatus = runCatching {
                    ReadStatusFilter.valueOf(prefs.getString(KEY_FILTER_READ_STATUS, ReadStatusFilter.ALL.name) ?: ReadStatusFilter.ALL.name)
                }.getOrDefault(ReadStatusFilter.ALL),
                tagIds = prefs.getStringSet(KEY_FILTER_TAG_IDS, emptySet()) ?: emptySet()
            ),
            syncedFolders = loadSyncedFoldersFromPrefs(),
            lastFolderScanTime = if (prefs.contains(KEY_LAST_FOLDER_SCAN_TIME)) prefs.getLong(
                KEY_LAST_FOLDER_SCAN_TIME, 0L
            )
            else null,
            pinnedHomeBookIds = prefs.getStringSet(KEY_PINNED_HOME, emptySet()) ?: emptySet(),
            pinnedLibraryBookIds = prefs.getStringSet(KEY_PINNED_LIBRARY, emptySet()) ?: emptySet(),
            recentFilesLimit = prefs.getInt(KEY_RECENT_FILES_LIMIT, 0),
            isTabsEnabled = prefs.getBoolean(KEY_TABS_ENABLED, true),
            openTabIds = prefs.getString(KEY_OPEN_TAB_IDS, null)?.let {
                try {
                    val arr = JSONArray(it)
                    List(arr.length()) { i -> arr.getString(i) }
                } catch(_: Exception) { emptyList() }
            } ?: emptyList(),
            activeTabBookId = prefs.getString(KEY_ACTIVE_TAB, null),
            externalFileBehavior = prefs.getString(KEY_EXTERNAL_FILE_BEHAVIOR, "ASK") ?: "ASK",
            useStrictFileFilter = prefs.getBoolean(KEY_USE_STRICT_FILE_FILTER, false),
            usePdfFileNameAsDisplayName = prefs.getBoolean(KEY_USE_PDF_FILE_NAME_AS_DISPLAY_NAME, false),
            isScreenCaptureProtectionEnabled = prefs.getBoolean(KEY_SCREEN_CAPTURE_PROTECTION, false),
            appThemeMode = try {
                AppThemeMode.valueOf(prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
            } catch (_: Exception) { AppThemeMode.SYSTEM },
            appContrastOption = try {
                AppContrastOption.valueOf(prefs.getString(KEY_APP_CONTRAST_OPTION, AppContrastOption.STANDARD.name) ?: AppContrastOption.STANDARD.name)
            } catch (_: Exception) { AppContrastOption.STANDARD },
            appTextDimFactorLight = prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR_LIGHT, prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR, 1.0f)),
            appTextDimFactorDark = prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR_DARK, prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR, 1.0f)),
            appSeedColor = if (prefs.contains(KEY_APP_SEED_COLOR)) androidx.compose.ui.graphics.Color(prefs.getInt(KEY_APP_SEED_COLOR, 0)) else null,
            appFontPreference = loadAppFontPreference(prefs),
            customAppThemes = loadCustomAppThemes(prefs)
        )
    )

    private suspend fun prepareBookForImport(externalUri: Uri): ImportResult? {
        val displayName = getFileNameFromUri(externalUri, appContext)
        var type = getFileTypeFromUri(externalUri, appContext)

        val hash = FileHasher.calculateSha256 {
            appContext.contentResolver.openInputStream(externalUri)
        }
        if (hash == null) {
            Timber.e("Failed to process file hash for $externalUri")
            return null
        }

        val existingItem = recentFilesRepository.getFileByBookId(hash)
        if (existingItem != null) {
            val pendingRemoval = pendingExternalFileRemovals()
                .firstOrNull { it.bookId == hash }
            if (pendingRemoval != null) {
                deletePendingExternalFileRemoval(
                    pendingRemoval.copy(uriString = pendingRemoval.uriString ?: existingItem.uriString)
                )
            } else {
                Timber.i("Book with ID: $hash already exists. Skipping import.")
                return null
            }
        }

        val fileName = displayName ?: ""
        if (fileName.endsWith(".zip", ignoreCase = true) || type == FileType.CBZ) {
            val bundleResult = CalibreBundleExtractor.processZip(appContext, externalUri, hash, bookImporter, recentFilesRepository)

            Timber.d("MainViewModel: Calibre processZip returned: $bundleResult")

            if (bundleResult != null) {
                return ImportResult(
                    internalUri = bundleResult.internalBookUri,
                    bookId = hash,
                    type = bundleResult.type,
                    bundleResult = bundleResult
                )
            }
            if (type == null) type = FileType.CBZ
        }

        if (type == null) return null

        Timber.i("Importing new book with ID: $hash")
        val internalFile = bookImporter.importBook(externalUri) ?: return null
        return ImportResult(internalFile.toUri(), hash, type, null)
    }

    val libraryFlow = combine(
        recentFilesRepository.getRecentFilesFlow(),
        recentFilesRepository.activeShelvesFlow,
        recentFilesRepository.shelfCrossRefsFlow,
        ::Triple
    )

    val tagFlow = combine(
        recentFilesRepository.tagsFlow,
        recentFilesRepository.tagCrossRefsFlow,
        ::Pair
    )

    open val uiState: StateFlow<ReaderScreenState> = combine(
        _internalState, libraryFlow, tagFlow
    ) { internalState, (recentFilesFromDb, dbShelves, shelfRefs), (dbTags, tagRefs) ->
        withContext(Dispatchers.Default) {
            libraryStateProjector.project(
                LibraryProjectionInput(
                    state = internalState,
                    recentFilesFromDb = recentFilesFromDb,
                    dbShelves = dbShelves,
                    shelfRefs = shelfRefs,
                    dbTags = dbTags,
                    tagRefs = tagRefs
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _internalState.value
    )

    private fun ReaderScreenState.withSharedLibraryAction(action: SharedLibraryAction): ReaderScreenState {
        return AndroidSharedStateBridge.reduceLibraryAction(
            current = this,
            projectedState = uiState.value,
            action = action
        )
    }

    private fun ReaderScreenState.withSharedAppAction(action: SharedAppAction): ReaderScreenState {
        return AndroidSharedStateBridge.reduceAppAction(
            current = this,
            projectedState = uiState.value,
            action = action
        )
    }

    fun setTabsEnabled(enabled: Boolean) {
        val projectedState = uiState.value
        prefs.edit { putBoolean(KEY_TABS_ENABLED, enabled) }
        _internalState.update {
            AndroidSharedStateBridge.setTabsEnabled(
                current = it,
                projectedState = projectedState,
                enabled = enabled
            )
        }
        if (!enabled) {
            persistTabState(_internalState.value.openTabIds, _internalState.value.activeTabBookId)
        }
    }

    fun switchTab(bookId: String) {
        Timber.tag("PdfTabSync").i("ViewModel: switchTab called for bookId: $bookId")
        val item = uiState.value.rawLibraryFiles.find { it.bookId == bookId } ?: run {
            Timber.tag("PdfTabSync").e("ViewModel: switchTab FAILED - BookId $bookId not found in library")
            return
        }

        val currentState = _internalState.value
        if (bookId !in currentState.openTabIds) {
            if (currentState.openTabIds.size >= 20) {
                viewModelScope.launch(Dispatchers.Main) {
                    showBanner("Maximum of 20 tabs allowed. Please close a tab first.", isError = true)
                }
                return
            }
        }
        val tabState = AndroidSharedStateBridge.openBookTab(
            current = currentState,
            projectedState = uiState.value,
            bookId = bookId
        )

        persistTabState(tabState.openTabIds, tabState.activeTabBookId)

        val uri = item.getUri()
        Timber.tag("PdfTabSync").d("ViewModel: ActiveTab updated to $bookId. URI found: ${uri != null}")

        uri?.let {
            persistReaderSession(bookId, item.type)
            Timber.tag("PdfTabSync").d("ViewModel: Setting new URI directly: $it")
            _internalState.update { state ->
                state.copy(
                    isTabsEnabled = tabState.isTabsEnabled,
                    openTabIds = tabState.openTabIds,
                    activeTabBookId = tabState.activeTabBookId,
                    selectedPdfUri = it,
                    selectedBookId = bookId,
                    selectedFileType = item.type,
                    initialPageInBook = item.lastPage,
                    initialPageInBookIsExplicit = false,
                    isOpeningFromTtsNotification = false,
                    initialBookmarksJson = item.bookmarksJson,
                    isLoading = false,
                    errorMessage = null
                )
            }

            viewModelScope.launch {
                addFileToRecent(
                    it,
                    item.type,
                    bookId,
                    customDisplayName = item.displayName,
                    isRecent = true,
                    sourceFolderUri = item.sourceFolderUri
                )
            }
        } ?: run {
            _internalState.update {
                it.copy(
                    openTabIds = tabState.openTabIds,
                    activeTabBookId = tabState.activeTabBookId,
                    isTabsEnabled = tabState.isTabsEnabled
                )
            }
        }
    }

    private fun persistTabState(openTabIds: List<String>, activeTabBookId: String?) {
        prefs.edit {
            if (openTabIds.isEmpty()) {
                remove(KEY_OPEN_TAB_IDS)
            } else {
                putString(KEY_OPEN_TAB_IDS, JSONArray(openTabIds).toString())
            }
            if (activeTabBookId == null) {
                remove(KEY_ACTIVE_TAB)
            } else {
                putString(KEY_ACTIVE_TAB, activeTabBookId)
            }
        }
    }

    fun openTagSelection(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        _internalState.update { it.copy(showTagSelectionDialogFor = bookIds) }
    }

    fun closeTagSelection() {
        _internalState.update { it.copy(showTagSelectionDialogFor = emptySet()) }
    }

    fun createAndAssignTag(name: String, bookIds: Set<String>) {
        val sanitizedBookIds = SharedLibraryEditor.cleanBookIds(bookIds)
        if (sanitizedBookIds.isEmpty()) return

        viewModelScope.launch {
            val tagId = UUID.randomUUID().toString()
            val colors = listOf(0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB, 0xFF64B5F6, 0xFF4FC3F7, 0xFF4DD0E1, 0xFF4DB6AC, 0xFF81C784, 0xFFAED581, 0xFFFF8A65, 0xFFA1887F, 0xFF90A4AE)
            val color = colors.random().toInt()
            val now = System.currentTimeMillis()
            val tag = SharedLibraryEditor.createTag(name, tagId, color)?.toTagEntity(now) ?: return@launch
            recentFilesRepository.createTag(tag)

            sanitizedBookIds.forEach { bookId ->
                recentFilesRepository.assignTagToBook(bookId, tagId)
            }
        }
    }

    fun toggleTagForBooks(tagId: String, bookIds: Set<String>, assign: Boolean) {
        val sanitizedBookIds = SharedLibraryEditor.cleanBookIds(bookIds)
        if (tagId.isBlank() || sanitizedBookIds.isEmpty()) return
        viewModelScope.launch {
            sanitizedBookIds.forEach { bookId ->
                if (assign) {
                    recentFilesRepository.assignTagToBook(bookId, tagId)
                } else {
                    recentFilesRepository.removeTagFromBook(bookId, tagId)
                }
            }
        }
    }

    private fun buildDefaultTags(): List<TagEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            TagEntity(id = "default_to_read", name = "To Read", color = 0xFF64B5F6.toInt(), createdAt = now),
            TagEntity(id = "default_reading", name = "Reading", color = 0xFF81C784.toInt(), createdAt = now + 1),
            TagEntity(id = "default_finished", name = "Finished", color = 0xFFFFB74D.toInt(), createdAt = now + 2),
            TagEntity(id = "default_favorites", name = "Favorites", color = 0xFFF06292.toInt(), createdAt = now + 3),
            TagEntity(id = "default_reference", name = "Reference", color = 0xFF9575CD.toInt(), createdAt = now + 4)
        )
    }

    private var googleFontsCache: List<String> = emptyList()

    fun loadGoogleFontsList(context: Context): List<String> {
        if (googleFontsCache.isEmpty()) {
            try {
                val jsonString = context.assets.open("google_fonts.json").bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                googleFontsCache = list
                Timber.d("Loaded ${list.size} Google Fonts from assets.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load google_fonts.json from assets")
            }
        }
        return googleFontsCache
    }

    fun downloadGoogleFont(fontName: String, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encodedName = java.net.URLEncoder.encode(fontName, "UTF-8")
                val url = java.net.URL("https://fonts.googleapis.com/css?family=$encodedName")
                val connection = url.openConnection() as java.net.HttpURLConnection

                // CRITICAL: We spoof an old Safari User-Agent. This forces Google to return the raw .ttf file instead of .woff2
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; en-us) AppleWebKit/533.21.1 (KHTML, like Gecko) Version/5.0.5 Safari/533.21.1")

                if (connection.responseCode != 200) {
                    withContext(Dispatchers.Main) { showBanner("Font '$fontName' not found on server.", isError = true) }
                    return@launch
                }

                val css = connection.inputStream.bufferedReader().readText()

                val regex = """url\((https://[^)]+)\)""".toRegex()
                val match = regex.find(css)

                if (match != null) {
                    val fontUrl = match.groupValues[1]
                    val ext = fontUrl.substringAfterLast(".", "ttf").lowercase()

                    // Strict format validation
                    if (ext != "ttf" && ext != "otf") {
                        withContext(Dispatchers.Main) { showBanner("Unsupported format ($ext) returned for $fontName", isError = true) }
                        return@launch
                    }

                    val fontConnection = java.net.URL(fontUrl).openConnection() as java.net.HttpURLConnection
                    val tempFile = File(appContext.cacheDir, "$fontName.$ext")

                    fontConnection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val result = fontsRepository.importFont(Uri.fromFile(tempFile))
                    result.onSuccess { font ->
                        if (uiState.value.isSyncEnabled) {
                            uploadNewFont(font)
                        }
                        withContext(Dispatchers.Main) { showBanner("$fontName downloaded successfully!") }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            showBanner(appContext.getString(R.string.error_import_font, it.message), isError = true)
                        }
                    }
                    tempFile.delete()
                } else {
                    withContext(Dispatchers.Main) { showBanner("Could not parse download link for $fontName", isError = true) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download Google Font: $fontName")
                withContext(Dispatchers.Main) { showBanner("Failed to download $fontName: ${e.localizedMessage}", isError = true) }
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun closeTab(bookId: String) {
        Timber.tag("PdfTabSync").i("ViewModel: closeTab called for $bookId")
        val currentState = _internalState.value
        val tabState = AndroidSharedStateBridge.closeBookTab(
            current = currentState,
            projectedState = uiState.value,
            bookId = bookId
        )

        if (tabState.openTabIds.isEmpty()) {
            persistTabState(tabState.openTabIds, tabState.activeTabBookId)
            _internalState.update {
                it.copy(
                    isTabsEnabled = tabState.isTabsEnabled,
                    openTabIds = tabState.openTabIds,
                    activeTabBookId = tabState.activeTabBookId
                )
            }
            clearSelectedFile()
        } else {
            val activeTab = currentState.activeTabBookId
            if (activeTab == bookId) {
                val nextTabId = tabState.activeTabBookId ?: tabState.openTabIds.last()
                persistTabState(tabState.openTabIds, nextTabId)
                _internalState.update {
                    it.copy(
                        isTabsEnabled = tabState.isTabsEnabled,
                        openTabIds = tabState.openTabIds,
                        activeTabBookId = nextTabId
                    )
                }
                switchTab(nextTabId)
            } else {
                persistTabState(tabState.openTabIds, tabState.activeTabBookId)
                _internalState.update {
                    it.copy(
                        isTabsEnabled = tabState.isTabsEnabled,
                        openTabIds = tabState.openTabIds,
                        activeTabBookId = tabState.activeTabBookId
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _internalState.update {
            if (it.isSearchActive) {
                it.withSharedLibraryAction(SharedLibraryAction.SearchChanged(newQuery))
            } else {
                it
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _internalState.update {
            if (active) {
                it.copy(isSearchActive = true)
            } else {
                it.copy(isSearchActive = false, searchQuery = "")
            }
        }
    }

    fun streamOpdsBook(
        bookId: String,
        title: String,
        urlTemplate: String,
        pageCount: Int,
        catalogId: String?
    ) {
        val encodedUrl = Uri.encode(urlTemplate)
        val safeId = Uri.encode(bookId)
        val catId = catalogId?.let { "&catalogId=${Uri.encode(it)}" } ?: ""

        val uriString = "opds-pse://stream?id=$safeId&count=$pageCount&url=$encodedUrl$catId"
        openBook(uriString.toUri(), bookId, FileType.CBZ, title)
    }

    fun deleteStreamedBooksForCatalog(catalogId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val filesToDelete = recentFilesRepository.getAllFilesForSync().filter {
                it.uriString?.contains("catalogId=$catalogId") == true
            }
            if (filesToDelete.isNotEmpty()) {
                val ids = filesToDelete.map { it.bookId }
                ids.forEach { bookId ->
                    cleanupBookDataLocally(bookId)
                    try {
                        val cacheDir = File(appContext.cacheDir, "opds_stream_${bookId.hashCode()}")
                        if (cacheDir.exists()) cacheDir.deleteRecursively()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to clean stream cache for $bookId")
                    }
                }
                recentFilesRepository.deleteFilePermanently(ids)
                withContext(Dispatchers.Main) {
                    showBanner(appContext.getString(R.string.banner_removed_streaming_books, filesToDelete.size))
                }
            }
        }
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
        blankPageId: String? = null,
        wasManuallyAdded: Boolean = false
    ): PageModificationResult = withContext(Dispatchers.Default + NonCancellable) {
        Timber.d("Adding page at index $insertIndex for book $bookId (manual=$wasManuallyAdded)")
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "vm.addPage.start bookId=$bookId insertIndex=$insertIndex blankPageId=$blankPageId " +
                "manual=$wasManuallyAdded ref=${referenceWidth}x$referenceHeight " +
                "current=${currentLayout.pdfLayoutDebugSummary()} annotationPages=${currentAnnotations.keys.sorted()} " +
                "bookmarksBytes=${currentBookmarksJson.length}"
        )

        val newLayout = currentLayout.toMutableList()
        val safeIndex = insertIndex.coerceIn(0, newLayout.size)

        val newPage = VirtualPage.BlankPage(
            id = blankPageId ?: UUID.randomUUID().toString(),
            width = referenceWidth,
            height = referenceHeight,
            wasManuallyAdded = wasManuallyAdded
        )
        newLayout.add(safeIndex, newPage)

        val newAnnotations = remapPdfAnnotationsForLayoutChange(
            currentLayout = currentLayout,
            updatedLayout = newLayout,
            annotations = currentAnnotations
        )

        val newBookmarksJson = try {
            remapPdfBookmarksJsonForLayoutChange(
                currentLayout = currentLayout,
                updatedLayout = newLayout,
                currentBookmarksJson = currentBookmarksJson
            )
        } catch (e: Exception) {
            Timber.e(e, "Error shifting bookmarks")
            currentBookmarksJson
        }

        pageLayoutRepository.saveLayout(bookId, newLayout)
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "vm.addPage.done bookId=$bookId safeIndex=$safeIndex new=${newLayout.pdfLayoutDebugSummary()} " +
                "newAnnotationPages=${newAnnotations.keys.sorted()} bookmarksBytes=${newBookmarksJson.length}"
        )

        PageModificationResult(newLayout, newAnnotations, newBookmarksJson)
    }

    suspend fun removePage(
        bookId: String,
        currentLayout: List<VirtualPage>,
        removeIndex: Int,
        currentAnnotations: Map<Int, List<PdfAnnotation>>,
        currentBookmarksJson: String
    ): PageModificationResult = withContext(Dispatchers.Default + NonCancellable) {
        Timber.d("Removing page at index $removeIndex for book $bookId")
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "vm.removePage.start bookId=$bookId removeIndex=$removeIndex " +
                "current=${currentLayout.pdfLayoutDebugSummary()} annotationPages=${currentAnnotations.keys.sorted()} " +
                "bookmarksBytes=${currentBookmarksJson.length}"
        )

        val newLayout = currentLayout.toMutableList()
        if (removeIndex in newLayout.indices) {
            newLayout.removeAt(removeIndex)
        } else {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                "vm.removePage.ignored bookId=$bookId removeIndex=$removeIndex current=${currentLayout.pdfLayoutDebugSummary()}"
            )
            return@withContext PageModificationResult(
                currentLayout, currentAnnotations, currentBookmarksJson
            )
        }

        val newAnnotations = remapPdfAnnotationsForLayoutChange(
            currentLayout = currentLayout,
            updatedLayout = newLayout,
            annotations = currentAnnotations
        )

        val newBookmarksJson = try {
            remapPdfBookmarksJsonForLayoutChange(
                currentLayout = currentLayout,
                updatedLayout = newLayout,
                currentBookmarksJson = currentBookmarksJson
            )
        } catch (e: Exception) {
            Timber.e(e, "Error shifting bookmarks")
            currentBookmarksJson
        }

        pageLayoutRepository.saveLayout(bookId, newLayout)
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "vm.removePage.done bookId=$bookId removeIndex=$removeIndex new=${newLayout.pdfLayoutDebugSummary()} " +
                "newAnnotationPages=${newAnnotations.keys.sorted()} bookmarksBytes=${newBookmarksJson.length}"
        )

        PageModificationResult(newLayout, newAnnotations, newBookmarksJson)
    }

    init {
        Timber.d("ViewModel instance created.")
        WorkManager.getInstance(application).apply {
            cancelUniqueWork(FolderSyncWorker.WORK_NAME)
            pruneWork()
        }

        val locatorConverter = LocatorConverter(
            bookCacheDao,
            ProtoBuf { serializersModule = semanticBlockModule },
            appContext
        )

        viewModelScope.launch {
            var wasSessionFinished = false
            ttsController.ttsState.collect { state ->
                val isPlaying = state.isPlaying
                val sessionFinished = state.sessionFinished
                val isReaderSource = state.playbackSource == "READER"

                if (isReaderSource) {
                    if (sessionFinished && !wasSessionFinished) {
                        if (_internalState.value.selectedEpubBook == null) {
                            Timber.tag("TTS_BG_ADVANCE").i("Reader is closed. Handling auto-advance in background.")
                            advanceTtsChapterInBackground(state, locatorConverter)
                        }
                    }
                    if (state.sessionEndedByStop) {
                        backgroundTtsBook = null
                        backgroundTtsBookId = null
                        backgroundTtsCoverPath = null
                    }
                }
                wasSessionFinished = sessionFinished
            }
        }
        viewModelScope.launch {
            recentFilesRepository.migrateLegacyShelvesToRoom()
            if (!prefs.getBoolean(KEY_DEFAULT_TAGS_SEEDED, false)) {
                recentFilesRepository.seedTagsIfEmpty(buildDefaultTags())
                prefs.edit { putBoolean(KEY_DEFAULT_TAGS_SEEDED, true) }
            }
        }
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

        viewModelScope.launch {
            _internalState
                .map { it.bannerMessage }
                .distinctUntilChanged()
                .collect { banner ->
                    scheduleBannerAutoDismiss(banner)
                }
        }

        if (_internalState.value.syncedFolders.any { it.localSyncEnabled }) {
            triggerFolderSyncWorker(metadataOnly = false, showFeedback = false)
        }

        sweepOrphanedCache()
        cleanupPendingExternalFileRemovals()
        restoreReaderSessionIfNeeded()

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

                    userProfileListener = firestoreRepository.listenToUserProfile(newUserData.uid) { isProFromBackend, creditsFromBackend ->
                        _internalState.update { it.copy(isProUser = isProFromBackend, credits = creditsFromBackend) }

                            if (isProFromBackend) {
                                verifyDeviceForProUser()

                                if (_internalState.value.isSyncEnabled) {
                                    viewModelScope.launch {
                                        logCloudSyncTrace {
                                            "android.startup.sync_check user=${newUserData.uid} isSyncEnabled=${_internalState.value.isSyncEnabled}"
                                        }
                                        Timber.tag("AnnotationSync").d(
                                            "Startup: Pro user & Sync enabled. Initiating cloud sync."
                                        )

                                        if (googleDriveRepository.hasDrivePermissions(appContext)) {
                                            syncWithCloud(showBanner = false)
                                        } else {
                                            logCloudSyncTrace { "android.startup.sync_skip reason=missing_drive_permissions user=${newUserData.uid}" }
                                            Timber.tag("AnnotationSync").d(
                                                "Startup: Sync skipped. Missing Drive permissions."
                                            )
                                        }
                                    }
                                }
                            }
                        }
                } else {
                    _internalState.update { it.copy(isProUser = false, credits = 0, isSyncEnabled = false, hasUnreadFeedback = false) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                billingClientWrapper.proUpgradeState.map { it.activePurchases },
                _internalState.map { it.currentUser?.uid }
            ) { purchases, uid ->
                Pair(purchases, uid)
            }
                .distinctUntilChanged()
                .collect { (purchases, uid) ->
                    if (uid != null && purchases.isNotEmpty()) {
                        Timber.d("Active purchases or User changed, triggering migration check")
                        triggerLegacyPurchaseMigration()
                    }
                }
        }
    }

    private fun persistReaderSession(bookId: String, type: FileType) {
        prefs.edit {
            putString(KEY_LAST_OPEN_BOOK_ID, bookId)
            putString(KEY_LAST_OPEN_FILE_TYPE, type.name)
        }
    }

    private fun clearPersistedReaderSession() {
        prefs.edit {
            remove(KEY_LAST_OPEN_BOOK_ID)
            remove(KEY_LAST_OPEN_FILE_TYPE)
        }
    }

    private fun pendingExternalFileRemovals(): List<PendingExternalFileRemoval> {
        return prefs.getStringSet(KEY_PENDING_EXTERNAL_FILE_REMOVALS, emptySet())
            .orEmpty()
            .mapNotNull(::decodePendingExternalFileRemoval)
            .distinctBy { it.bookId }
    }

    private fun markPendingExternalFileRemoval(bookId: String, uriString: String?) {
        if (bookId.isBlank()) return
        val removalsByBookId = pendingExternalFileRemovals()
            .associateBy { it.bookId }
            .toMutableMap()
        removalsByBookId[bookId] = PendingExternalFileRemoval(bookId, uriString)
        writePendingExternalFileRemovals(removalsByBookId.values)
    }

    private fun clearPendingExternalFileRemovals(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        val remaining = pendingExternalFileRemovals().filterNot { it.bookId in bookIds }
        writePendingExternalFileRemovals(remaining)
    }

    private fun writePendingExternalFileRemovals(removals: Collection<PendingExternalFileRemoval>) {
        val encoded = removals
            .filter { it.bookId.isNotBlank() }
            .mapTo(mutableSetOf(), ::encodePendingExternalFileRemoval)
        prefs.edit(commit = true) {
            if (encoded.isEmpty()) {
                remove(KEY_PENDING_EXTERNAL_FILE_REMOVALS)
            } else {
                putStringSet(KEY_PENDING_EXTERNAL_FILE_REMOVALS, encoded)
            }
        }
    }

    private fun cleanupPendingExternalFileRemovals() {
        val removals = pendingExternalFileRemovals()
        if (removals.isEmpty()) return

        val pendingBookIds = removals.mapTo(mutableSetOf()) { it.bookId }
        if (prefs.getString(KEY_LAST_OPEN_BOOK_ID, null) in pendingBookIds) {
            clearPersistedReaderSession()
        }

        viewModelScope.launch {
            removals.forEach { removal ->
                deletePendingExternalFileRemoval(removal)
            }
        }
    }

    private fun deletePendingExternalFileRemoval(bookId: String, uriString: String?) {
        markPendingExternalFileRemoval(bookId, uriString)
        viewModelScope.launch {
            deletePendingExternalFileRemoval(PendingExternalFileRemoval(bookId, uriString))
        }
    }

    private suspend fun deletePendingExternalFileRemoval(removal: PendingExternalFileRemoval) {
        var shouldRetry = false
        runCatching {
            cleanupBookDataLocally(removal.bookId)
        }.onFailure { error ->
            Timber.w(error, "Failed to clear local caches for pending external file ${removal.bookId}")
        }

        runCatching {
            recentFilesRepository.deleteFilePermanently(listOf(removal.bookId))
        }.onFailure { error ->
            shouldRetry = true
            Timber.w(error, "Failed to remove pending external file ${removal.bookId} from library")
        }

        removal.uriString?.let { uriString ->
            runCatching {
                bookImporter.deleteBookByUriString(uriString)
            }.onFailure { error ->
                shouldRetry = true
                Timber.w(error, "Failed to delete pending external file copy for ${removal.bookId}")
            }
        }

        if (!shouldRetry) {
            clearPendingExternalFileRemovals(setOf(removal.bookId))
        }
    }

    private fun encodePendingExternalFileRemoval(removal: PendingExternalFileRemoval): String {
        return JSONObject()
            .put("bookId", removal.bookId)
            .apply {
                if (!removal.uriString.isNullOrBlank()) {
                    put("uriString", removal.uriString)
                }
            }
            .toString()
    }

    private fun decodePendingExternalFileRemoval(value: String): PendingExternalFileRemoval? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("{")) {
            runCatching {
                val json = JSONObject(trimmed)
                val bookId = json.optString("bookId").takeIf { it.isNotBlank() }
                val uriString = json.optString("uriString").takeIf { it.isNotBlank() }
                bookId?.let { PendingExternalFileRemoval(it, uriString) }
            }.getOrNull()
        } else {
            PendingExternalFileRemoval(trimmed, null)
        }
    }

    private fun restoreReaderSessionIfNeeded() {
        val currentState = _internalState.value
        if (currentState.selectedBookId != null || currentState.selectedPdfUri != null || currentState.selectedEpubUri != null) {
            return
        }

        val persistedType = prefs.getString(KEY_LAST_OPEN_FILE_TYPE, null)?.let { typeName ->
            runCatching { FileType.valueOf(typeName) }.getOrNull()
        }
        val restoreBookId = prefs.getString(KEY_LAST_OPEN_BOOK_ID, null) ?: return
        if (restoreBookId in pendingExternalFileRemovals().map { it.bookId }) {
            clearPersistedReaderSession()
            return
        }
        if (persistedType == null) {
            clearPersistedReaderSession()
            return
        }

        viewModelScope.launch {
            val item = recentFilesRepository.getFileByBookId(restoreBookId)
            val restoreUri = item?.getUri()
            if (item == null || restoreUri == null || item.type != persistedType) {
                Timber.tag("ReaderRestore")
                    .w("Skipping restore for bookId=$restoreBookId. Item missing, URI missing, or type mismatch.")
                clearPersistedReaderSession()
                return@launch
            }

            when {
                item.type in PDF_VIEWER_FILE_TYPES -> {
                    _internalState.update { state ->
                        if (state.selectedBookId != null || state.selectedPdfUri != null || state.selectedEpubUri != null) {
                            state
                        } else {
                            state.copy(
                                selectedPdfUri = restoreUri,
                                selectedBookId = item.bookId,
                                selectedEpubBook = null,
                                selectedEpubUri = null,
                                selectedFileType = item.type,
                                isLoading = false,
                                errorMessage = null,
                                initialLocator = null,
                                initialCfi = null,
                                initialBookmarksJson = item.bookmarksJson,
                                initialHighlightsJson = null,
                                initialPageInBook = item.lastPage,
                                initialPageInBookIsExplicit = false,
                                isOpeningFromTtsNotification = false
                            )
                        }
                    }
                    persistReaderSession(item.bookId, item.type)
                    Timber.tag("ReaderRestore").i("Restored reader session for ${item.bookId} (${item.type}).")
                }
                item.type in EPUB_READER_FILE_TYPES -> {
                    val locator =
                        if (item.lastChapterIndex != null && item.locatorBlockIndex != null && item.locatorCharOffset != null) {
                            Locator(
                                chapterIndex = item.lastChapterIndex,
                                blockIndex = item.locatorBlockIndex,
                                charOffset = item.locatorCharOffset
                            )
                        } else {
                            null
                        }

                    _internalState.update { state ->
                        if (state.selectedBookId != null || state.selectedPdfUri != null || state.selectedEpubUri != null) {
                            state
                        } else {
                            state.copy(
                                selectedPdfUri = null,
                                selectedBookId = item.bookId,
                                selectedEpubBook = null,
                                selectedEpubUri = restoreUri,
                                selectedFileType = item.type,
                                isLoading = true,
                                errorMessage = null,
                                initialLocator = locator,
                                initialCfi = item.lastPositionCfi,
                                initialBookmarksJson = item.bookmarksJson,
                                initialHighlightsJson = item.highlightsJson,
                                initialPageInBook = null,
                                initialPageInBookIsExplicit = false,
                                isOpeningFromTtsNotification = false
                            )
                        }
                    }

                    runCatching {
                        restoreEpubReaderBook(item, restoreUri)
                    }.onSuccess { restoredBook ->
                        _internalState.update { state ->
                            if (state.selectedBookId != item.bookId) {
                                state
                            } else {
                                state.copy(selectedEpubBook = restoredBook, isLoading = false, errorMessage = null)
                            }
                        }
                        persistReaderSession(item.bookId, item.type)
                        Timber.tag("ReaderRestore").i("Restored reader session for ${item.bookId} (${item.type}).")
                    }.onFailure { error ->
                        Timber.tag("ReaderRestore").e(error, "Failed to restore EPUB-like session for ${item.bookId}")
                        clearPersistedReaderSession()
                        _internalState.update { state ->
                            if (state.selectedBookId != item.bookId) {
                                state
                            } else {
                                state.copy(
                                    selectedBookId = null,
                                    selectedEpubUri = null,
                                    selectedEpubBook = null,
                                    selectedFileType = null,
                                    isLoading = false,
                                    errorMessage = appContext.getString(R.string.error_load_file, error.message)
                                )
                            }
                        }
                    }
                }
                else -> {
                    clearPersistedReaderSession()
                }
            }
        }
    }

    private suspend fun restoreEpubReaderBook(item: RecentFileItem, uri: Uri): EpubBook {
        return restoreEpubReaderBook(item.type, item.bookId, item.displayName, uri)
    }

    private suspend fun restoreEpubReaderBook(
        type: FileType,
        bookId: String,
        displayName: String,
        uri: Uri
    ): EpubBook = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) {
                throw Exception("Could not open input stream for restore")
            }

            when (type) {
                FileType.EPUB -> epubParser.createEpubBook(
                    inputStream = inputStream,
                    bookId = bookId,
                    originalBookNameHint = displayName,
                    sourceFingerprint = epubSourceFingerprint(uri)
                )

                FileType.MOBI -> mobiParser.createMobiBook(
                    inputStream = inputStream,
                    bookId = bookId,
                    originalBookNameHint = displayName
                ) ?: throw Exception(
                    if (MobiParser.isNativeParserAvailable) {
                        "MobiParser returned null. The file might be DRM-protected or invalid."
                    } else {
                        MobiParser.nativeParserUnavailableMessage()
                    }
                )

                FileType.FB2 -> fb2Parser.createFb2Book(
                    inputStream = inputStream,
                    bookId = bookId,
                    originalBookNameHint = displayName
                )

                FileType.ODT, FileType.FODT -> odtParser.createOdtBook(
                    inputStream = inputStream,
                    bookId = bookId,
                    originalBookNameHint = displayName,
                    isFlat = type == FileType.FODT
                )

                FileType.MD, FileType.TXT, FileType.HTML, FileType.DOCX -> singleFileImporter.importSingleFile(
                    inputStream = inputStream,
                    type = type,
                    originalBookNameHint = displayName,
                    bookId = bookId
                )

                else -> throw IllegalArgumentException("Unsupported reader restore type: $type")
            }
        }
    }

    fun recoverSelectedEpubContent() {
        val state = _internalState.value
        val bookId = state.selectedBookId ?: return
        val uri = state.selectedEpubUri ?: return
        val type = state.selectedFileType ?: return

        if (type !in EPUB_READER_FILE_TYPES) return

        val displayName = state.selectedEpubBook?.fileName
            ?: state.selectedEpubBook?.title
            ?: getFileNameFromUri(uri, appContext)
            ?: "unknown_book"

        viewModelScope.launch {
            epubRecoveryMutex.withLock {
                val latestState = _internalState.value
                if (latestState.selectedBookId != bookId || latestState.selectedEpubUri != uri) {
                    return@withLock
                }
                if (latestState.selectedEpubBook?.hasReadableExtractedContent() == true) {
                    return@withLock
                }

                _internalState.update {
                    if (it.selectedBookId == bookId) it.copy(isLoading = true, errorMessage = null) else it
                }

                runCatching {
                    val item = recentFilesRepository.getFileByBookId(bookId)
                    restoreEpubReaderBook(
                        type = item?.type ?: type,
                        bookId = bookId,
                        displayName = item?.displayName ?: displayName,
                        uri = uri
                    )
                }.onSuccess { restoredBook ->
                    _internalState.update {
                        if (it.selectedBookId == bookId && it.selectedEpubUri == uri) {
                            it.copy(selectedEpubBook = restoredBook, isLoading = false, errorMessage = null)
                        } else {
                            it
                        }
                    }
                    Timber.tag("EpubRecovery").i("Recovered missing extracted content for $bookId")
                }.onFailure { error ->
                    Timber.tag("EpubRecovery").e(error, "Failed to recover missing extracted content for $bookId")
                    _internalState.update {
                        if (it.selectedBookId == bookId && it.selectedEpubUri == uri) {
                            it.copy(
                                isLoading = false,
                                errorMessage = appContext.getString(R.string.error_load_file, error.message)
                            )
                        } else {
                            it
                        }
                    }
                }
            }
        }
    }

    private fun getDisplayPathFromUri(context: Context, uriString: String): String {
        val uri = uriString.toUri()
        val fallbackName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Unknown Folder"
        if (DocumentsContract.isTreeUri(uri) && DocumentsContract.getTreeDocumentId(uri)
                .isNotEmpty()
        ) {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val split = documentId.split(":")
            if (split.size > 1) {
                return split[1]
            }
        }
        return fallbackName
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
        importFonts(listOf(uri))
    }

    fun importFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _internalState.update { it.copy(isLoading = true) }
            try {
                uris.forEach { uri ->
                    val result = fontsRepository.importFont(uri)
                    result.onSuccess { font ->
                        if (uiState.value.isSyncEnabled) {
                            uploadNewFont(font)
                        }
                    }.onFailure {
                        showBanner(appContext.getString(R.string.error_import_font, it.message), isError = true)
                    }
                }
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
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
        deleteFonts(listOf(fontId))
    }

    fun deleteFonts(fontIds: Collection<String>) {
        val uniqueFontIds = fontIds.filter { it.isNotBlank() }.toSet()
        if (uniqueFontIds.isEmpty()) return
        viewModelScope.launch {
            uniqueFontIds.forEach { fontId ->
                fontsRepository.deleteFont(fontId)
            }
            if (uniqueFontIds.any { _internalState.value.appFontPreference.referencesCustomFont(it) }) {
                setAppFontPreference(AppFontPreference.System)
            }
        }
    }

    fun deleteBookPermanently(bookId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            @Suppress("UnusedVariable", "Unused") val item = recentFilesRepository.getFileByBookId(bookId) ?: return@launch

            Timber.d("Deleting book permanently from reader: $bookId")

            cleanupBookDataLocally(bookId)
            recentFilesRepository.deleteFilePermanently(listOf(bookId))

            withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
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
        showBanner(appContext.getString(R.string.error_sync_drive_permission), isError = true)
    }

    private fun verifyPurchaseWithBackend(
        purchase: PurchaseEntity, isSilentMigrationCheck: Boolean = false
    ) {
        viewModelScope.launch {
            val productId = purchase.products.firstOrNull()

            if (productId == null || (!productId.startsWith("credits_") && productId != BillingClientWrapper.PRO_LIFETIME_PRODUCT_ID)) {
                Timber.e("Purchase verification failed: Incorrect product ID.")
                if (!isSilentMigrationCheck) {
                    _internalState.update { it.copy(bannerMessage = BannerMessage(appContext.getString(R.string.error_purchase_general), isError = true)) }
                }
                billingClientWrapper.clearVerificationState()
                return@launch
            }

            val tokenHash = PurchaseAccountObfuscator.purchaseTokenHash(purchase.purchaseToken)
            Timber.i(
                "Verifying purchase. productId=$productId tokenHash=$tokenHash orderId=${purchase.orderId} " +
                        "obfuscatedAccountId=${purchase.obfuscatedAccountId} uid=${_internalState.value.currentUser?.uid} " +
                        "silent=$isSilentMigrationCheck"
            )

            val result = cloudflareRepository.verifyPurchase(purchase.purchaseToken, productId)

            if (result.isSuccess) {
                Timber.i("Backend verification successful. Firestore will update the app.")
                billingClientWrapper.clearAccountConflict()

                if (productId.startsWith("credits_")) {
                    billingClientWrapper.consumePurchase(purchase.purchaseToken)
                    if (!isSilentMigrationCheck) {
                        _internalState.update { it.copy(bannerMessage = BannerMessage("Credits successfully added!")) }
                    }
                } else {
                    if (!isSilentMigrationCheck) {
                        _internalState.update { it.copy(bannerMessage = BannerMessage(appContext.getString(R.string.banner_upgrade_success))) }
                    }
                    verifyDeviceForProUser()
                }
            } else {
                val exception = result.exceptionOrNull()
                if (exception?.message?.contains("already claimed") == true) {
                    Timber.i("Migration/Refresh check: Purchase token is already claimed. Silently ignoring.")
                    if (productId.startsWith("credits_")) {
                        billingClientWrapper.consumePurchase(purchase.purchaseToken)
                    } else {
                        billingClientWrapper.markAccountConflict()
                    }
                } else {
                    val errorMessage = appContext.getString(R.string.error_purchase_verification)
                    Timber.e(exception, "Backend verification failed")
                    if (!isSilentMigrationCheck) {
                        _internalState.update { it.copy(bannerMessage = BannerMessage(errorMessage, isError = true)) }
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
                    showBanner(appContext.getString(R.string.banner_device_removed))
                }

                is com.aryan.reader.data.DeviceStatus.NotFound -> {
                    Timber.d("Device not found during verification. Triggering full registration.")
                    registerOrUpdateDeviceOnSignIn(currentUser.uid)
                }

                is com.aryan.reader.data.DeviceStatus.Error -> {
                    Timber.e(deviceStatus.exception, "Error checking device status.")
                    _internalState.update {
                        it.copy(
                            errorMessage = appContext.getString(R.string.error_verify_device)
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
                        errorMessage = appContext.getString(R.string.error_update_devices),
                        isReplacingDevice = false
                    )
                }
            }
        }
    }

    private fun getFastFileId(context: Context, uri: Uri): String {
        var result = uri.toString()
        try {
            if (uri.scheme == "file") {
                uri.path?.let {
                    val file = File(it)
                    result = "${file.name}_${file.length()}"
                }
            } else {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                        val name = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown"
                        result = "${name}_${size}"
                    }
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
        highlights: List<PdfUserHighlight>? = null,
        bookId: String
    ) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(isLoading = true, bannerMessage = BannerMessage(appContext.getString(R.string.banner_saving_pdf)))
            }
            try {
                val virtualPages = pageLayoutRepository.getLayoutOrNull(bookId)
                val outputStream = appContext.contentResolver.openOutputStream(destUri)
                if (outputStream != null) {
                    PdfiumAnnotationExporter.exportAnnotatedPdf(
                        context = appContext,
                        sourceUri = sourceUri,
                        destStream = outputStream,
                        virtualPages = virtualPages,
                        inkAnnotations = annotations,
                        richTextPageLayouts = richTextPageLayouts,
                        textBoxes = textBoxes,
                        highlights = highlights
                    )
                    showBanner(appContext.getString(R.string.banner_pdf_saved))
                } else {
                    showBanner(appContext.getString(R.string.error_open_file_saving), isError = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save annotated PDF")
                showBanner(appContext.getString(R.string.error_saving_pdf, e.localizedMessage), isError = true)
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveOriginalPdf(sourceUri: Uri, destUri: Uri) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(isLoading = true, bannerMessage = BannerMessage(appContext.getString(R.string.banner_saving_original_pdf)))
            }
            try {
                val contentResolver = appContext.contentResolver
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                showBanner(appContext.getString(R.string.banner_original_pdf_saved))
            } catch (e: Exception) {
                Timber.e(e, "Failed to save original PDF")
                showBanner(appContext.getString(R.string.error_saving_pdf, e.localizedMessage), isError = true)
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun togglePinForContextualItems(isHome: Boolean) {
        if (_internalState.value.contextualActionItems.isEmpty()) return

        var pinsToPersist: Set<String> = emptySet()
        val projectedState = uiState.value
        _internalState.update { state ->
            val updated = AndroidSharedStateBridge.togglePinsForSelectedBooks(
                current = state,
                projectedState = projectedState,
                isHome = isHome
            )
            pinsToPersist = if (isHome) updated.pinnedHomeBookIds else updated.pinnedLibraryBookIds
            updated
        }
        prefs.edit { putStringSet(if (isHome) KEY_PINNED_HOME else KEY_PINNED_LIBRARY, pinsToPersist) }
    }

    fun updateLibraryFilters(filters: LibraryFilters) {
        val sanitizedFilters = filters.copy(
            fileTypes = filters.fileTypes.filterTo(mutableSetOf()) { it in ANDROID_READABLE_FILE_TYPES }
        )
        _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.FiltersChanged(sanitizedFilters)) }

        prefs.edit {
            putStringSet(KEY_FILTER_FILE_TYPES, sanitizedFilters.fileTypes.map { it.name }.toSet())
            putStringSet(KEY_FILTER_FOLDERS, sanitizedFilters.sourceFolders)
            putString(KEY_FILTER_READ_STATUS, sanitizedFilters.readStatus.name)
            putStringSet(KEY_FILTER_TAG_IDS, sanitizedFilters.tagIds)
        }

        Timber.d("Library filters updated and persisted: $sanitizedFilters")
    }

    suspend fun sharePdf(
        activityContext: Context,
        sourceUri: Uri,
        annotations: Map<Int, List<PdfAnnotation>>,
        richTextPageLayouts: List<com.aryan.reader.pdf.PageTextLayout>? = null,
        textBoxes: List<PdfTextBox>? = null,
        highlights: List<PdfUserHighlight>? = null,
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
                FileOutputStream(destFile).use { outputStream ->
                    if (includeAnnotations) {
                        val virtualPages = pageLayoutRepository.getLayoutOrNull(resolvedBookId)

                        PdfiumAnnotationExporter.exportAnnotatedPdf(
                            context = appContext,
                            sourceUri = sourceUri,
                            destStream = outputStream,
                            virtualPages = virtualPages,
                            inkAnnotations = annotations,
                            richTextPageLayouts = richTextPageLayouts,
                            textBoxes = textBoxes,
                            highlights = highlights
                        )
                    } else {
                        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                            input.copyTo(outputStream)
                        }
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
                    putExtra(Intent.EXTRA_SUBJECT, appContext.getString(R.string.share_subject, filename))

                    clipData = ClipData.newRawUri(filename, contentUri)

                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, appContext.getString(R.string.share_chooser_title))

                if (activityContext !is android.app.Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) { activityContext.startActivity(chooser) }
            } catch (e: Exception) {
                Timber.e(e, "Share failed")
                showBanner(appContext.getString(R.string.error_share_failed, e.localizedMessage), isError = true)
            }
        }
    }

    private fun queueCloudMetadataUpload(bookId: String, reason: String, debounce: Boolean = true) {
        if (!uiState.value.isSyncEnabled) return
        cloudMetadataUploadJobs.remove(bookId)?.cancel()
        val job = viewModelScope.launch {
            if (debounce) delay(CLOUD_METADATA_UPLOAD_DEBOUNCE_MILLIS)
            val latest = recentFilesRepository.getFileByBookId(bookId) ?: run {
                logCloudSyncTrace { "android.upload.queue_skip reason=missing_local book=$bookId trigger=$reason" }
                return@launch
            }
            logCloudSyncTrace {
                "android.upload.queue_fire trigger=$reason debounce=$debounce ${latest.cloudSyncTraceSummary()}"
            }
            uploadSingleBookMetadata(latest)
        }
        cloudMetadataUploadJobs[bookId] = job
        job.invokeOnCompletion {
            if (cloudMetadataUploadJobs[bookId] == job) {
                cloudMetadataUploadJobs.remove(bookId)
            }
        }
    }

    fun queuePdfSidecarCloudUpload(bookId: String) {
        queueCloudMetadataUpload(bookId, reason = "pdf_sidecar")
    }

    private fun uploadSingleBookMetadata(book: RecentFileItem) {
        if (!uiState.value.isSyncEnabled) {
            logCloudSyncTrace { "android.upload.skip reason=sync_disabled ${book.cloudSyncTraceSummary()}" }
            return
        }

        if (book.uriString?.startsWith("opds-pse") == true) {
            logCloudSyncTrace { "android.upload.skip reason=opds_stream ${book.cloudSyncTraceSummary()}" }
            Timber.d("Skipping metadata sync for OPDS stream book: ${book.displayName}")
            return
        }

        if (book.sourceFolderUri != null) {
            logCloudSyncTrace { "android.upload.skip reason=folder_book ${book.cloudSyncTraceSummary()}" }
            Timber.d("Skipping metadata sync for local folder book: ${book.displayName}")
            return
        }

        if (book.isManualOnlyReaderFile()) {
            logCloudSyncTrace { "android.upload.skip reason=manual_only ${book.cloudSyncTraceSummary()}" }
            Timber.d("Skipping metadata sync for manual-only reader file: ${book.displayName}")
            return
        }
        val currentUser = uiState.value.currentUser ?: run {
            logCloudSyncTrace { "android.upload.skip reason=no_user ${book.cloudSyncTraceSummary()}" }
            return
        }

        viewModelScope.launch {
            try {
                val deviceId = getInstallationId()
                var bookForMetadata = book
                var uploadedAnnotationPayload = false
                var uploadedAnnotationModifiedTimestamp = 0L
                var remoteBookLoaded = false
                var remoteBookForUpload: BookMetadata? = null
                var remoteAnnotationDriveTimestampLoaded = false
                var remoteAnnotationDriveTimestamp = 0L
                suspend fun loadRemoteBookForUpload(): BookMetadata? {
                    if (!remoteBookLoaded) {
                        remoteBookForUpload = firestoreRepository.getBookMetadata(currentUser.uid, book.bookId)
                        remoteBookLoaded = true
                    }
                    return remoteBookForUpload
                }
                suspend fun loadRemoteAnnotationDriveTimestamp(): Long {
                    if (!remoteAnnotationDriveTimestampLoaded) {
                        val remote = loadRemoteBookForUpload()
                        remoteAnnotationDriveTimestamp = if (remote?.hasAnnotations == true) {
                            googleDriveRepository.getAccessToken(appContext)?.let { accessToken ->
                                googleDriveRepository.getFiles(accessToken)
                                    ?.files
                                    .orEmpty()
                                    .firstOrNull { it.name == cloudPdfAnnotationDriveFileName(book.bookId) }
                                    ?.modifiedTimeMillis
                            } ?: 0L
                        } else {
                            0L
                        }
                        remoteAnnotationDriveTimestampLoaded = true
                    }
                    return remoteAnnotationDriveTimestamp
                }
                if (book.needsRemoteEpubAnnotationMetadataGuard()) {
                    val remote = loadRemoteBookForUpload()
                    val merged = bookForMetadata.mergeRemoteEpubAnnotationMetadata(remote)
                    if (merged != bookForMetadata) {
                        logCloudSyncTrace {
                            "android.upload.epub_annotation_preserve book=${book.bookId} " +
                                "local=${bookForMetadata.cloudSyncTraceSummary()} " +
                                "remote=${remote?.cloudSyncTraceSummary() ?: "null"} " +
                                "merged=${merged.cloudSyncTraceSummary()}"
                        }
                        recentFilesRepository.addRecentFile(merged)
                        bookForMetadata = merged
                    }
                }
                val remoteForContent = loadRemoteBookForUpload()?.toRecentFileItem()
                if (shouldUploadLocalBookContent(bookForMetadata, remoteForContent)) {
                    val accessToken = googleDriveRepository.getAccessToken(appContext) ?: run {
                        logCloudSyncTrace { "android.upload.content_guard_skip reason=no_access_token ${bookForMetadata.cloudSyncTraceSummary()}" }
                        return@launch
                    }
                    val source = bookForMetadata.getUri()?.path?.let(::File)
                    if (source?.exists() != true) {
                        logCloudSyncTrace {
                            "android.upload.content_guard_skip reason=file_missing book=${bookForMetadata.bookId} " +
                                "path=${(source?.absolutePath).cloudSyncPreview()}"
                        }
                        return@launch
                    }
                    logCloudSyncTrace {
                        "android.upload.content_guard_start book=${bookForMetadata.bookId} " +
                            "localContentTs=${bookForMetadata.fileContentModifiedTimestamp} " +
                            "remoteContentTs=${remoteForContent?.fileContentModifiedTimestamp ?: 0L}"
                    }
                    val uploadedFile = googleDriveRepository.uploadFile(
                        accessToken,
                        bookForMetadata.bookId,
                        source,
                        bookForMetadata.type
                    )
                    if (uploadedFile == null) {
                        logCloudSyncTrace { "android.upload.content_guard_failed book=${bookForMetadata.bookId}" }
                        return@launch
                    }
                    val contentTimestamp = bookForMetadata.fileContentModifiedTimestamp.takeIf { it > 0L }
                        ?: source.lastModified()
                    bookForMetadata = bookForMetadata.copy(
                        fileSize = source.length(),
                        fileContentModifiedTimestamp = contentTimestamp
                    )
                    logCloudSyncTrace {
                        "android.upload.content_guard_success book=${bookForMetadata.bookId} " +
                            "driveId=${uploadedFile.id} contentTs=$contentTimestamp"
                    }
                }
                logCloudSyncTrace { "android.upload.start device=$deviceId ${bookForMetadata.cloudSyncTraceSummary()}" }
                Timber.tag("AnnotationSync").d("Preparing to sync book: ${book.bookId}")

                val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(book.bookId)
                val deletedInkFile = pdfAnnotationRepository.getDeletedAnnotationsFileForSync(book.bookId)
                val richTextFile = pdfRichTextRepository.getFileForSync(book.bookId)
                val layoutFile = pageLayoutRepository.getLayoutFile(book.bookId)
                val textBoxFile = pdfTextBoxRepository.getFileForSync(book.bookId)
                val highlightFile = pdfHighlightRepository.getFileForSync(book.bookId)

                val hasInk = inkFile.hasSyncableCloudAnnotationPayload()
                val hasDeletedInk = deletedInkFile.hasSyncableCloudAnnotationPayload()
                val hasRichText = richTextFile.hasSyncableCloudAnnotationPayload()
                val hasLayout = layoutFile.exists()
                val hasTextBoxes = textBoxFile.hasSyncableCloudAnnotationPayload()
                val hasHighlights = highlightFile.hasSyncableCloudAnnotationPayload()
                val sidecars = AndroidPdfCloudSidecarState(
                    hasInk = hasInk,
                    inkTimestamp = inkFile?.lastModified() ?: 0L,
                    hasDeletedInk = hasDeletedInk,
                    deletedInkTimestamp = deletedInkFile?.lastModified() ?: 0L,
                    hasRichText = hasRichText,
                    richTextTimestamp = richTextFile.lastModified(),
                    hasLayout = hasLayout,
                    layoutTimestamp = layoutFile.lastModified(),
                    hasTextBoxes = hasTextBoxes,
                    textBoxesTimestamp = textBoxFile.lastModified(),
                    hasHighlights = hasHighlights,
                    highlightsTimestamp = highlightFile.lastModified()
                )
                logCloudSyncTrace {
                    "android.upload.sidecars book=${book.bookId} hasInk=$hasInk hasDeletedInk=$hasDeletedInk hasText=$hasRichText " +
                        "hasLayout=$hasLayout hasTextBoxes=$hasTextBoxes hasHighlights=$hasHighlights " +
                        "payloadTs=${sidecars.annotationPayloadTimestamp} bundleTs=${sidecars.bundleTimestamp}"
                }
                logCloudAnnotationSyncTrace {
                    "android.upload.inspect book=${book.bookId} remoteHas=${remoteBookForUpload?.hasAnnotations} " +
                        "remoteTs=${remoteBookForUpload?.lastModifiedTimestamp ?: 0L} " +
                        "ink{exists=$hasInk bytes=${inkFile?.length() ?: 0L} ts=${sidecars.inkTimestamp}} " +
                        "deletedInk{exists=$hasDeletedInk bytes=${deletedInkFile?.length() ?: 0L} ts=${sidecars.deletedInkTimestamp}} " +
                        "text{exists=$hasRichText bytes=${richTextFile.length()} ts=${sidecars.richTextTimestamp}} " +
                        "layout{exists=$hasLayout bytes=${layoutFile.length()} ts=${sidecars.layoutTimestamp}} " +
                        "textBoxes{exists=$hasTextBoxes bytes=${textBoxFile.length()} ts=${sidecars.textBoxesTimestamp}} " +
                        "highlights{exists=$hasHighlights bytes=${highlightFile.length()} ts=${sidecars.highlightsTimestamp}} " +
                        "payloadTs=${sidecars.annotationPayloadTimestamp} bundleTs=${sidecars.bundleTimestamp} " +
                        "hasPayload=${sidecars.hasAnnotationPayload}"
                }
                val remoteAnnotationDriveTimestampForUpload = loadRemoteAnnotationDriveTimestamp()
                val remoteAnnotationTimestampForUpload =
                    remoteBookForUpload?.effectiveAnnotationModifiedTimestamp(remoteAnnotationDriveTimestampForUpload) ?: 0L
                val localAnnotationsShouldUpload = shouldUploadLocalPdfCloudAnnotations(
                    localSidecars = sidecars,
                    remoteHasAnnotations = remoteBookForUpload?.hasAnnotations == true,
                    remoteAnnotationModifiedTimestamp = remoteAnnotationTimestampForUpload
                )
                logCloudAnnotationSyncTrace {
                    "android.upload.annotation_decision book=${book.bookId} " +
                        "localShouldUpload=$localAnnotationsShouldUpload remoteHas=${remoteBookForUpload?.hasAnnotations} " +
                        "remoteAnnTs=$remoteAnnotationTimestampForUpload " +
                        "remoteDriveAnnTs=$remoteAnnotationDriveTimestampForUpload payloadTs=${sidecars.annotationPayloadTimestamp}"
                }
                Timber.d(
                    "android.cloud.export candidates book=${book.bookId} hasRichText=$hasRichText " +
                        "richBytes=${if (hasRichText) richTextFile.length() else 0L} " +
                        "hasAnnotationPayload=${sidecars.hasAnnotationPayload}"
                )

                if (localAnnotationsShouldUpload) {
                    if (googleDriveRepository.hasDrivePermissions(appContext)) {
                        val accessToken = googleDriveRepository.getAccessToken(appContext)

                        if (accessToken != null) {
                            val bundleJson = JSONObject()
                            bundleJson.put("version", 2)

                            fun putJsonSafe(key: String, file: File?) {
                                if (file == null || !file.exists()) return
                                try {
                                    val content = file.readText().trim()
                                    if (key == "text") {
                                        Timber.d(
                                            "android.cloud.export.readRichText book=${book.bookId} rawLen=${content.length} " +
                                                "file=${file.absolutePath}"
                                        )
                                    }
                                    if (content.startsWith("[")) {
                                        bundleJson.put(key, JSONArray(content))
                                    } else if (content.startsWith("{")) {
                                        bundleJson.put(key, JSONObject(content))
                                    }
                                } catch (e: Exception) {
                                    if (key == "text") {
                                        Timber.e(e, "android.cloud.export.richTextParseFailed book=${book.bookId}")
                                    }
                                    Timber.e(e, "Failed to parse local $key file")
                                }
                            }

                            if (hasInk) putJsonSafe("ink", inkFile)
                            if (hasDeletedInk) putJsonSafe(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATION_DELETIONS, deletedInkFile)
                            if (hasRichText) putJsonSafe("text", richTextFile)
                            if (hasLayout) putJsonSafe("layout", layoutFile)
                            if (hasTextBoxes) putJsonSafe("textBoxes", textBoxFile)
                            if (hasHighlights) putJsonSafe("highlights", highlightFile)

                            val bundleFile =
                                File(appContext.cacheDir, "sync_bundle_${book.bookId}.json")
                            val canonicalBundle = SharedPdfAnnotationSidecarCodec.canonicalizeDataJson(bundleJson.toString())
                            var uploadBundle = canonicalBundle
                            var mergedRemoteIntoUpload = false
                            if (remoteBookForUpload?.hasAnnotations == true) {
                                val remoteBundleFile = File(appContext.cacheDir, "remote_sync_bundle_${book.bookId}.json")
                                try {
                                    val didDownloadRemote = googleDriveRepository.downloadAnnotationFile(
                                        accessToken,
                                        book.bookId,
                                        remoteBundleFile
                                    )
                                    if (didDownloadRemote && remoteBundleFile.isFile) {
                                        val remoteBundle = remoteBundleFile.readText()
                                        val mergedBundle = SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
                                            localDataJson = canonicalBundle,
                                            remoteDataJson = remoteBundle,
                                            preferRemoteOnConflict = false
                                        )
                                        val localCount = SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(canonicalBundle)
                                        val remoteCount = SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(remoteBundle)
                                        val mergedCount = SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(mergedBundle)
                                        uploadBundle = mergedBundle
                                        mergedRemoteIntoUpload = mergedBundle != canonicalBundle
                                        logCloudAnnotationSyncTrace {
                                            "android.upload.merge_remote book=${book.bookId} didDownload=true " +
                                                "localCount=$localCount remoteCount=$remoteCount mergedCount=$mergedCount " +
                                                "changed=$mergedRemoteIntoUpload"
                                        }
                                    } else {
                                        logCloudAnnotationSyncTrace {
                                            "android.upload.merge_remote_missing book=${book.bookId} " +
                                                "didDownload=$didDownloadRemote tempExists=${remoteBundleFile.exists()}"
                                        }
                                    }
                                } catch (e: Exception) {
                                    logCloudAnnotationSyncError(e) {
                                        "android.upload.merge_remote_failed book=${book.bookId}"
                                    }
                                } finally {
                                    remoteBundleFile.delete()
                                }
                            }
                            bundleFile.writeText(uploadBundle)
                            logCloudAnnotationSyncTrace {
                                "android.upload.bundle_ready book=${book.bookId} " +
                                    "rawKeys=${bundleJson.keys().asSequence().toList()} " +
                                    "canonicalBytes=${canonicalBundle.length} uploadBytes=${uploadBundle.length} " +
                                    "fileBytes=${bundleFile.length()}"
                            }
                            if (hasRichText) {
                                Timber.d(
                                    "android.cloud.export.bundleReady book=${book.bookId} canonicalLen=${canonicalBundle.length} " +
                                        "bundleFile=${bundleFile.absolutePath}"
                                )
                            }

                            val uploaded = googleDriveRepository.uploadAnnotationFile(
                                accessToken, book.bookId, bundleFile
                            )
                            bundleFile.delete()

                            if (uploaded != null) {
                                uploadedAnnotationPayload = true
                                uploadedAnnotationModifiedTimestamp = uploaded.modifiedTimeMillis
                                if (mergedRemoteIntoUpload) {
                                    recentFilesRepository.importAnnotationBundle(
                                        book.bookId,
                                        uploadBundle,
                                        uploadedAnnotationModifiedTimestamp
                                    )
                                    logCloudAnnotationSyncTrace {
                                        "android.upload.local_apply_merged book=${book.bookId} " +
                                            "driveTs=$uploadedAnnotationModifiedTimestamp bytes=${uploadBundle.length}"
                                    }
                                }
                                markPdfCloudAnnotationSidecarsSynced(
                                    uploadedAnnotationModifiedTimestamp,
                                    inkFile,
                                    richTextFile,
                                    layoutFile,
                                    textBoxFile,
                                    highlightFile,
                                    deletedInkFile
                                )
                                logCloudAnnotationSyncTrace {
                                    "android.upload.sidecar_success book=${book.bookId} driveId=${uploaded.id} " +
                                        "driveTs=$uploadedAnnotationModifiedTimestamp bytes=${uploadBundle.length}"
                                }
                                logCloudSyncTrace {
                                    "android.upload.sidecar_success book=${book.bookId} driveId=${uploaded.id} " +
                                        "driveTs=$uploadedAnnotationModifiedTimestamp bytes=${uploadBundle.length}"
                                }
                                if (hasRichText) {
                                    Timber
                                        .d("android.cloud.export.uploadSuccess book=${book.bookId} driveId=${uploaded.id}")
                                }
                                Timber.tag("AnnotationSync")
                                    .d("Bundle upload SUCCESS. ID: ${uploaded.id}")
                            } else {
                                logCloudAnnotationSyncTrace {
                                    "android.upload.sidecar_failed book=${book.bookId} bytes=${uploadBundle.length}"
                                }
                                logCloudSyncTrace { "android.upload.sidecar_failed book=${book.bookId}; aborting_metadata_upload" }
                                if (hasRichText) {
                                    Timber
                                        .e("android.cloud.export.uploadFailed book=${book.bookId}")
                                }
                                Timber.tag("AnnotationSync")
                                    .e("Bundle upload FAILED. Skipping Firestore sync to prevent data loss.")
                                return@launch
                            }
                        } else {
                            logCloudAnnotationSyncTrace { "android.upload.skip_sidecar reason=no_access_token book=${book.bookId}" }
                            logCloudSyncTrace { "android.upload.sidecar_failed book=${book.bookId}; reason=no_access_token; aborting_metadata_upload" }
                            return@launch
                        }
                    } else {
                        logCloudAnnotationSyncTrace { "android.upload.skip_sidecar reason=missing_drive_permission book=${book.bookId}" }
                        logCloudSyncTrace { "android.upload.sidecar_failed book=${book.bookId}; reason=missing_drive_permission; aborting_metadata_upload" }
                        return@launch
                    }
                } else {
                    logCloudAnnotationSyncTrace {
                        "android.upload.skip_sidecar reason=${if (sidecars.hasAnnotationPayload) "remote_annotation_not_older" else "no_annotation_payload"} " +
                            "book=${book.bookId} layoutOnly=$hasLayout layoutTs=${sidecars.layoutTimestamp} " +
                            "remoteAnnTs=$remoteAnnotationTimestampForUpload payloadTs=${sidecars.annotationPayloadTimestamp}"
                    }
                    logCloudSyncTrace {
                        "android.upload.sidecars_skipped book=${book.bookId} " +
                            "reason=${if (sidecars.hasAnnotationPayload) "remote_annotation_not_older" else "no_annotation_payload"}"
                    }
                    Timber.tag("AnnotationSync").d(
                        if (sidecars.hasAnnotationPayload) {
                            "Local annotation payload is not newer than remote for ${book.bookId}"
                        } else {
                            "No local annotation payload (ink/text/text boxes/highlights) to upload for ${book.bookId}"
                        }
                    )
                }

                val latestLocalForMetadata = recentFilesRepository.getFileByBookId(bookForMetadata.bookId)
                val refreshedBookForMetadata = bookForMetadata.withFreshLocalReadingPositionForCloudUpload(
                    latestLocalForMetadata
                )
                if (refreshedBookForMetadata != bookForMetadata) {
                    logCloudSyncTrace {
                        "android.upload.refresh_latest book=${bookForMetadata.bookId} " +
                            "before=${bookForMetadata.cloudSyncTraceSummary()} " +
                            "latest=${latestLocalForMetadata?.cloudSyncTraceSummary() ?: "null"} " +
                            "after=${refreshedBookForMetadata.cloudSyncTraceSummary()}"
                    }
                    bookForMetadata = refreshedBookForMetadata
                }
                val remoteMetadata = loadRemoteBookForUpload()
                val localReadingTimestamp = bookForMetadata.effectiveReadingPositionModifiedTimestamp()
                val remoteReadingTimestamp = remoteMetadata?.effectiveReadingPositionModifiedTimestamp() ?: 0L
                val remoteAnnotationTimestamp = remoteMetadata?.effectiveAnnotationModifiedTimestamp(
                    remoteAnnotationDriveTimestampForUpload
                ) ?: 0L
                val remoteReadingPositionWins = remoteMetadata != null && remoteReadingTimestamp > localReadingTimestamp
                val remoteMetadataWins = remoteMetadata != null &&
                    remoteMetadata.lastModifiedTimestamp > bookForMetadata.lastModifiedTimestamp
                val metadataBase = if (remoteMetadataWins && remoteMetadata != null) {
                    remoteMetadata.toRecentFileItem().withLocalStorageForCloudMetadata(bookForMetadata)
                } else {
                    bookForMetadata
                }
                val metadataBook = when {
                    remoteReadingPositionWins && remoteMetadata != null -> metadataBase.withCloudReadingPosition(remoteMetadata)
                    metadataBase != bookForMetadata -> metadataBase.withLocalReadingPosition(bookForMetadata)
                    else -> bookForMetadata
                }
                val readingPositionTimestamp = if (remoteReadingPositionWins) {
                    remoteReadingTimestamp
                } else {
                    localReadingTimestamp
                }
                if (remoteReadingPositionWins) {
                    logCloudSyncTrace {
                        "android.upload.preserve_remote_position book=${bookForMetadata.bookId} " +
                            "remoteReadTs=$remoteReadingTimestamp localReadTs=$localReadingTimestamp " +
                            "remote=${remoteMetadata?.cloudSyncTraceSummary() ?: "null"}"
                    }
                }
                if (remoteMetadataWins) {
                    logCloudSyncTrace {
                        "android.upload.preserve_remote_metadata book=${bookForMetadata.bookId} " +
                            "remoteTs=${remoteMetadata?.lastModifiedTimestamp ?: 0L} localTs=${bookForMetadata.lastModifiedTimestamp} " +
                            "remoteReadTs=$remoteReadingTimestamp localReadTs=$localReadingTimestamp " +
                            metadataBook.cloudSyncTraceSummary("metadata")
                    }
                }

                val newTimestamp = System.currentTimeMillis()
                val syncedAnnotationTimestamp = if (uploadedAnnotationPayload) {
                    uploadedAnnotationModifiedTimestamp.takeIf { it > 0L }
                        ?: maxOf(sidecars.annotationPayloadTimestamp, newTimestamp)
                } else if (remoteMetadata?.hasAnnotations == true) {
                    remoteAnnotationTimestamp
                } else {
                    0L
                }
                val syncedHasAnnotations = uploadedAnnotationPayload || remoteMetadata?.hasAnnotations == true
                val metadataToSync = metadataBook.toBookMetadata().copy(
                    lastModifiedTimestamp = newTimestamp,
                    readingPositionModifiedTimestamp = readingPositionTimestamp,
                    annotationModifiedTimestamp = syncedAnnotationTimestamp,
                    hasAnnotations = syncedHasAnnotations
                )

                firestoreRepository.syncBookMetadata(currentUser.uid, metadataToSync, deviceId)
                recentFilesRepository.addRecentFile(
                    metadataBook.copy(
                        lastModifiedTimestamp = newTimestamp,
                        readingPositionModifiedTimestamp = readingPositionTimestamp
                    )
                )
                logCloudAnnotationSyncTrace {
                    "android.upload.metadata_success book=${bookForMetadata.bookId} newTs=$newTimestamp " +
                        "readTs=$readingPositionTimestamp hasAnnotations=$syncedHasAnnotations " +
                        "annTs=$syncedAnnotationTimestamp payloadTs=${sidecars.annotationPayloadTimestamp}"
                }
                logCloudSyncTrace {
                    "android.upload.metadata_success user=${currentUser.uid} oldTs=${bookForMetadata.lastModifiedTimestamp} " +
                        "newTs=$newTimestamp readTs=$readingPositionTimestamp annTs=$syncedAnnotationTimestamp " +
                        "remoteReadTs=$remoteReadingTimestamp localReadTs=$localReadingTimestamp " +
                        "hasAnnotations=$syncedHasAnnotations ${metadataBook.cloudSyncTraceSummary()}"
                }
                Timber.tag("AnnotationSync")
                    .d("Firestore metadata updated for ${book.bookId} (hasAnnotationPayload=${sidecars.hasAnnotationPayload}, syncedHasAnnotations=$syncedHasAnnotations)")
            } catch (e: Exception) {
                logCloudSyncError(e) { "android.upload.failed ${book.cloudSyncTraceSummary()}" }
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

                if (uiState.value.isSyncEnabled && googleDriveRepository.hasDrivePermissions(
                        appContext
                    )
                ) {
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

        val closingBookId = _internalState.value.selectedBookId
        val uriString = _internalState.value.selectedPdfUri?.toString()
            ?: _internalState.value.selectedEpubUri?.toString()
        logCloudSyncTrace {
            "android.reader.close_request book=$closingBookId uri=${uriString.cloudSyncPreview()} sync=${uiState.value.isSyncEnabled}"
        }

        val ttsState = ttsController.ttsState.value
        val isTtsActive = ttsState.playbackSource == "READER" &&
                (ttsState.isPlaying || ttsState.isLoading || ttsState.sessionFinished || ttsState.currentText != null)

        if (isTtsActive && _internalState.value.selectedEpubBook != null) {
            backgroundTtsBook = _internalState.value.selectedEpubBook
            backgroundTtsBookId = _internalState.value.selectedBookId
            backgroundTtsCoverPath = uiState.value.recentFiles.find { it.bookId == backgroundTtsBookId }?.coverImagePath
        } else if (!isTtsActive) {
            backgroundTtsBook = null
            backgroundTtsBookId = null
            backgroundTtsCoverPath = null
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
                initialPageInBook = null,
                initialPageInBookIsExplicit = false,
                isOpeningFromTtsNotification = false
            )
        }
        clearPersistedReaderSession()

        var removesExternalFileOnClose = false
        if (closingBookId != null && closingBookId == externalOpenedBookId) {
            val behavior = prefs.getString(KEY_EXTERNAL_FILE_BEHAVIOR, "ASK") ?: "ASK"
            if (behavior == "ASK") {
                _internalState.update { it.copy(showExternalFileSavePromptFor = closingBookId) }
            } else if (behavior == "DELETE") {
                removesExternalFileOnClose = true
                deletePendingExternalFileRemoval(closingBookId, uriString)
            } else {
                clearPendingExternalFileRemovals(setOf(closingBookId))
            }
            externalOpenedBookId = null
        }

        if (uriString != null && !removesExternalFileOnClose) {
            viewModelScope.launch {
                val freshBook = recentFilesRepository.getFileByUri(uriString)
                freshBook?.let {
                    if (uiState.value.uploadingBookIds.contains(it.bookId)) {
                        logCloudSyncTrace { "android.reader.close_upload_skip reason=already_uploading ${it.cloudSyncTraceSummary()}" }
                        return@launch
                    }
                    if (uiState.value.isSyncEnabled) {
                        logCloudSyncTrace { "android.reader.close_upload_start ${it.cloudSyncTraceSummary()}" }
                        Timber.d("Book closed, triggering metadata sync for ${it.bookId}")
                        uploadSingleBookMetadata(it)
                    } else {
                        logCloudSyncTrace { "android.reader.close_upload_skip reason=sync_disabled ${it.cloudSyncTraceSummary()}" }
                    }

                    if (it.sourceFolderUri != null) {
                        Timber.tag("FolderAnnotationSync")
                            .d("Book closed (Folder Linked), syncing metadata and annotations to folder: ${it.bookId}")
                        recentFilesRepository.syncLocalMetadataToFolder(it.bookId)
                        recentFilesRepository.syncLocalAnnotationsToFolder(it.bookId)
                    }
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

    private fun advanceTtsChapterInBackground(state: TtsPlaybackManager.TtsState, locatorConverter: LocatorConverter) {
        val currentChapterIndex = state.chapterIndex ?: return
        val book = backgroundTtsBook ?: return
        val bookId = backgroundTtsBookId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            var nextIdx = currentChapterIndex + 1
            val totalChapters = book.chapters.size
            var foundContent = false

            while (nextIdx < totalChapters) {
                Timber.tag("TTS_BG_ADVANCE").d("Trying chapter $nextIdx natively.")
                val nativeChunks = locatorConverter.getTtsChunksForChapter(book, nextIdx, bookId)

                if (!nativeChunks.isNullOrEmpty()) {
                    val token = getAuthToken()
                    val mode = try {
                        TtsPlaybackManager.TtsMode.valueOf(state.ttsMode)
                    } catch(e: Exception) {
                        TtsPlaybackManager.TtsMode.CLOUD
                    }

                    withContext(Dispatchers.Main) {
                        ttsController.start(
                            chunks = nativeChunks,
                            bookTitle = book.title,
                            chapterTitle = book.chapters.getOrNull(nextIdx)?.title,
                            coverImageUri = backgroundTtsCoverPath?.let { Uri.fromFile(File(it)).toString() },
                            bookId = bookId,
                            chapterIndex = nextIdx,
                            totalChapters = totalChapters,
                            ttsMode = mode,
                            playbackSource = "READER",
                            authToken = token
                        )
                    }
                    foundContent = true

                    // Save reading position locally
                    val cfi = nativeChunks.firstOrNull()?.sourceCfi
                    if (cfi != null) {
                        val locator = locatorConverter.getLocatorFromCfi(book, nextIdx, cfi, bookId)
                        if (locator != null) {
                            recentFilesRepository.getFileByBookId(bookId)?.uriString?.let { uriString ->
                                recentFilesRepository.updateEpubReadingPosition(uriString, locator, cfi, 0f)
                            }
                        }
                    }
                    break
                } else {
                    Timber.tag("TTS_BG_ADVANCE").d("Chapter $nextIdx is empty natively. Skipping to next.")
                    nextIdx++
                }
            }

            if (!foundContent) {
                Timber.tag("TTS_BG_ADVANCE").d("Reached end of book or no content found.")
                withContext(Dispatchers.Main) {
                    ttsController.stop()
                }
            }
        }
    }

    private fun loadSyncedFoldersFromPrefs(): List<SyncedFolder> {
        val jsonString = prefs.getString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, null)
        val oldUri = prefs.getString(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI, null)
        val folders = SyncedFolderPrefs.decodeSyncedFolders(
            jsonString = jsonString,
            legacyUri = oldUri,
            legacyLastScanTime = prefs.getLong(SyncedFolderPrefs.KEY_LEGACY_LAST_FOLDER_SCAN_TIME, 0L),
            legacyNameResolver = { uri -> getDisplayPathFromUri(appContext, uri) }
        )

        if (jsonString == null && prefs.contains(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI) && oldUri != null) {
            saveSyncedFoldersToPrefs(folders)
            prefs.edit {
                remove(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI)
                remove(SyncedFolderPrefs.KEY_LEGACY_LAST_FOLDER_SCAN_TIME)
            }
        }
        return folders
    }

    private fun saveSyncedFoldersToPrefs(folders: List<SyncedFolder>) {
        prefs.edit {
            putString(
                SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON,
                SyncedFolderPrefs.encodeSyncedFolders(folders)
            )
        }
    }

    fun addSyncedFolder(folderUri: Uri) {
        val currentFolders = _internalState.value.syncedFolders

        if (currentFolders.size >= MAX_FOLDER_LIMIT) {
            showBanner(appContext.getString(R.string.error_folder_limit_reached, MAX_FOLDER_LIMIT), isError = true)
            return
        }

        if (currentFolders.any { it.uriString == folderUri.toString() }) {
            showBanner(appContext.getString(R.string.error_folder_already_synced), isError = true)
            return
        }

        viewModelScope.launch {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                val name = getDisplayPathFromUri(appContext, folderUri.toString())
                val newFolder = SyncedFolder(
                    uriString = folderUri.toString(),
                    name = name,
                    lastScanTime = 0L,
                    allowedFileTypes = ANDROID_SYNCABLE_FILE_TYPES,
                    localSyncEnabled = true
                )
                val newStats = currentFolders + newFolder

                saveSyncedFoldersToPrefs(newStats)

                _internalState.update {
                    it.copy(
                        syncedFolders = newStats
                    )
                }

                triggerFolderSyncWorker(
                    metadataOnly = false,
                    showFeedback = true,
                    targetFolderUriString = newFolder.uriString
                )

                showBanner(appContext.getString(R.string.banner_folder_added, name))

            } catch (e: SecurityException) {
                Timber.e(e, "Failed to take permissions for $folderUri")
                showBanner(appContext.getString(R.string.error_access_folder_permissions), isError = true)
            }
        }
    }

    fun removeSyncedFolder(folder: SyncedFolder) {
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(appContext)
            ReaderPerfLog.d("FolderRemove request folder=${folder.uriString}")
            workManager.cancelUniqueWork(FolderSyncWorker.WORK_NAME_ONETIME)
            workManager.cancelUniqueWork(MetadataExtractionWorker.WORK_NAME)

            val currentFolders = _internalState.value.syncedFolders.toMutableList()
            currentFolders.removeAll { it.uriString == folder.uriString }

            saveSyncedFoldersToPrefs(currentFolders)
            _internalState.update { it.copy(syncedFolders = currentFolders) }

            val filesToRemove = recentFilesRepository.getFilesBySourceFolder(folder.uriString)
            recentFilesRepository.deleteFilesBySourceFolder(folder.uriString)
            filesToRemove.forEach { cleanupBookDataLocally(it.bookId) }
            try {
                appContext.contentResolver.releasePersistableUriPermission(
                    folder.uriString.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Timber.w("Failed to release permissions: ${e.message}")
            }

            if (currentFolders.isEmpty()) {
                workManager.cancelUniqueWork(FolderSyncWorker.WORK_NAME)
            }

            showBanner(appContext.getString(R.string.banner_folder_removed))
        }
    }

    fun setFolderLocalSyncEnabled(
        folder: SyncedFolder,
        enabled: Boolean,
        removeSyncDataFolder: Boolean = false
    ) {
        viewModelScope.launch {
            val currentFolders = _internalState.value.syncedFolders.toMutableList()
            val index = currentFolders.indexOfFirst { it.uriString == folder.uriString }
            if (index == -1) return@launch

            val updatedFolder = currentFolders[index].copy(localSyncEnabled = enabled)
            currentFolders[index] = updatedFolder
            saveSyncedFoldersToPrefs(currentFolders)
            _internalState.update { it.copy(syncedFolders = currentFolders) }

            if (enabled) {
                showBanner(appContext.getString(R.string.banner_folder_local_sync_enabled))
                triggerFolderSyncWorker(
                    metadataOnly = false,
                    showFeedback = true,
                    targetFolderUriString = updatedFolder.uriString
                )
            } else {
                val workManager = WorkManager.getInstance(appContext)
                workManager.cancelUniqueWork(FolderSyncWorker.WORK_NAME_ONETIME)
                workManager.cancelUniqueWork(MetadataExtractionWorker.WORK_NAME)

                if (removeSyncDataFolder) {
                    val removed = withContext(Dispatchers.IO) {
                        LocalSyncUtils.deleteSyncDataFolder(appContext, updatedFolder.uriString.toUri())
                    }
                    val message = if (removed) {
                        appContext.getString(R.string.banner_folder_local_sync_disabled_removed_data)
                    } else {
                        appContext.getString(R.string.banner_folder_sync_data_remove_failed)
                    }
                    showBanner(message, isError = !removed)
                } else {
                    showBanner(appContext.getString(R.string.banner_folder_local_sync_disabled))
                }
            }
        }
    }

    fun syncFolderMetadata(showFeedback: Boolean = false) {
        triggerFolderSyncWorker(metadataOnly = true, showFeedback = showFeedback)
    }

    fun scanSyncedFolder() {
        triggerFolderSyncWorker(metadataOnly = false, showFeedback = true)
    }

    private fun triggerFolderSyncWorker(
        metadataOnly: Boolean,
        showFeedback: Boolean,
        targetFolderUriString: String? = null
    ) {
        val allFolders = _internalState.value.syncedFolders
        val folders = if (targetFolderUriString.isNullOrBlank()) {
            allFolders.filter { it.localSyncEnabled }
        } else {
            allFolders.filter { it.uriString == targetFolderUriString && it.localSyncEnabled }
        }
        if (folders.isEmpty()) {
            if (showFeedback) {
                showBanner(appContext.getString(R.string.error_no_enabled_folder_sync), isError = true)
            }
            return
        }

        val targetFolderName = targetFolderUriString
            ?.let { target -> allFolders.firstOrNull { it.uriString == target }?.name ?: target }
        ReaderPerfLog.d(
            "FolderSync request folders=${folders.size} target=${targetFolderName ?: "ALL"} " +
                "metadataOnly=$metadataOnly feedback=$showFeedback"
        )

        val workManager = WorkManager.getInstance(appContext)
        if (!metadataOnly) {
            workManager.cancelUniqueWork(MetadataExtractionWorker.WORK_NAME)
        }
        val data = androidx.work.Data.Builder()
            .putBoolean(FolderSyncWorker.KEY_METADATA_ONLY, metadataOnly)
            .apply {
                if (!targetFolderUriString.isNullOrBlank()) {
                    putString(FolderSyncWorker.KEY_TARGET_FOLDER_URI, targetFolderUriString)
                }
            }
            .build()

        val request = OneTimeWorkRequestBuilder<FolderSyncWorker>().setInputData(data).build()

        workManager.enqueueUniqueWork(
            FolderSyncWorker.WORK_NAME_ONETIME, ExistingWorkPolicy.REPLACE, request
        )

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).filterNotNull().first { workInfo ->
                when (workInfo.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                        if (showFeedback) {
                            val msg = if (metadataOnly) appContext.getString(R.string.banner_folder_sync_updating) else appContext.getString(R.string.banner_folder_sync_scanning)
                            _internalState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = true,
                                    bannerMessage = BannerMessage(msg, isPersistent = true)
                                )
                            }
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        _internalState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                bannerMessage = if (showFeedback) BannerMessage(appContext.getString(R.string.banner_folder_sync_complete)) else it.bannerMessage,
                                lastFolderScanTime = System.currentTimeMillis(),
                                syncedFolders = loadSyncedFoldersFromPrefs()
                            )
                        }
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _internalState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = if (showFeedback) appContext.getString(R.string.error_sync_failed) else it.errorMessage,
                                bannerMessage = null
                            )
                        }
                    }

                    else -> Unit
                }

                if (workInfo.state.isFinished) {
                    workManager.pruneWork()
                }
                workInfo.state.isFinished
            }
        }
    }

    fun updateFolderFilters(folder: SyncedFolder, newFilters: Set<FileType>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentFolders = _internalState.value.syncedFolders.toMutableList()
            val index = currentFolders.indexOfFirst { it.uriString == folder.uriString }
            if (index != -1) {
                val sanitizedFilters = newFilters.filterTo(mutableSetOf()) { it in ANDROID_SYNCABLE_FILE_TYPES }
                val updatedFolder = folder.copy(allowedFileTypes = sanitizedFilters)
                currentFolders[index] = updatedFolder
                saveSyncedFoldersToPrefs(currentFolders)
                _internalState.update { it.copy(syncedFolders = currentFolders) }

                val filesToRemove = recentFilesRepository.getFilesBySourceFolder(folder.uriString)
                    .filter { it.type !in sanitizedFilters }

                if (filesToRemove.isNotEmpty()) {
                    Timber.d("Removing ${filesToRemove.size} files that no longer match the filter for folder ${folder.name}")
                    val idsToRemove = filesToRemove.map { it.bookId }

                    idsToRemove.forEach { bookId ->
                        cleanupBookDataLocally(bookId)
                    }
                    recentFilesRepository.deleteFilePermanently(idsToRemove)
                }

                withContext(Dispatchers.Main) {
                    triggerFolderSyncWorker(
                        metadataOnly = false,
                        showFeedback = true,
                        targetFolderUriString = folder.uriString
                    )
                }
            }
        }
    }

    fun disconnectAllSyncedFolders() {
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(appContext)
            ReaderPerfLog.d("FolderRemove disconnect all folders=${_internalState.value.syncedFolders.size}")
            workManager.cancelUniqueWork(FolderSyncWorker.WORK_NAME_ONETIME)
            workManager.cancelUniqueWork(FolderSyncWorker.WORK_NAME)
            workManager.cancelUniqueWork(MetadataExtractionWorker.WORK_NAME)

            val folders = _internalState.value.syncedFolders
            folders.forEach { folder ->
                val filesToRemove = recentFilesRepository.getFilesBySourceFolder(folder.uriString)
                recentFilesRepository.deleteFilesBySourceFolder(folder.uriString)
                filesToRemove.forEach { cleanupBookDataLocally(it.bookId) }
                try {
                    appContext.contentResolver.releasePersistableUriPermission(
                        folder.uriString.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }

            prefs.edit {
                remove(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON)
                remove(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI)
            }
            _internalState.update { it.copy(syncedFolders = emptyList()) }
        }
    }

    private fun downloadBook(item: RecentFileItem, openWhenComplete: Boolean = false): Job {
        if (!uiState.value.isSyncEnabled) {
            _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_enable_sync_download)) }
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

                val fileName = sharedCloudBookContentFileName(item.bookId, item.type)
                    ?: throw Exception("Unsupported cloud file type: ${item.type}")
                val driveFileId = remoteFiles[fileName]?.id

                if (driveFileId != null) {
                    val destinationFile = bookImporter.createBookFile(fileName)
                    Timber.d("Downloading book: ${item.displayName}")
                    if (googleDriveRepository.downloadFile(
                            accessToken, driveFileId, destinationFile
                        )
                    ) {
                        if (item.fileContentModifiedTimestamp > 0L) {
                            destinationFile.setLastModified(item.fileContentModifiedTimestamp)
                        }
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
                    it.copy(errorMessage = appContext.getString(R.string.error_download_failed, item.displayName))
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
            _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_enable_sync_clear_cloud)) }
            return
        }

        if (!googleDriveRepository.isUserSignedInToDrive(appContext)) {
            _internalState.update {
                it.copy(errorMessage = appContext.getString(R.string.error_not_signed_in_clear_cloud))
            }
            return
        }

        _internalState.update {
            it.copy(
                isLoading = true,
                bannerMessage = BannerMessage(appContext.getString(R.string.banner_clearing_cloud_local_data))
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
                            isLoading = false, bannerMessage = BannerMessage(appContext.getString(R.string.banner_cloud_local_data_cleared))
                        )
                    }
                } else {
                    throw Exception("Failed to clear cloud data.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete all cloud and local user data.")
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_clear_all_data))
                }
            }
        }
    }

    private fun triggerLegacyPurchaseMigration() {
        val user = _internalState.value.currentUser
        val localPurchases = billingClientWrapper.proUpgradeState.value.activePurchases

        if (user != null && localPurchases.isNotEmpty()) {
            Timber.i("Checking for unconsumed purchases or legacy pro statuses...")

            localPurchases.forEach { purchase ->
                verifyPurchaseWithBackend(purchase, isSilentMigrationCheck = true)
            }
        }
    }

    fun deleteAllUserData() {
        val currentUser = _internalState.value.currentUser ?: return
        Timber.w("DESTRUCTIVE: Starting deletion of all user data for ${currentUser.uid}")
        _internalState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                recentFilesRepository.clearAllLocalData()
                clearBookCache()
                pdfTextRepository.clearAllText()
                pdfTextBoxRepository.clearAll()
                prefs.edit { remove(KEY_LAST_SYNC_TIMESTAMP) }

                _internalState.update {
                    it.copy(
                        isLoading = false, bannerMessage = BannerMessage(appContext.getString(R.string.banner_local_data_cleared))
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete all user data.")
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_clear_all_data))
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
                            bannerMessage = BannerMessage(appContext.getString(R.string.error_sign_in_failed), isError = true), isLoading = false
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
                    appContext.getString(R.string.error_no_google_account)
                } else {
                    appContext.getString(R.string.error_sign_in_internet)
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
                    withTimeoutOrNull(3000) {
                        firestoreRepository.deleteDevice(currentUser.uid, deviceId)
                    }
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
                            })
                    )
                }
            } ?: run {
                showBanner(appContext.getString(R.string.error_sign_in_device_management), isError = true)
            }
        }
    }

    fun launchPurchaseFlow(activity: android.app.Activity, productId: String = BillingClientWrapper.PRO_LIFETIME_PRODUCT_ID) {
        Timber.d("Attempting to launch purchase flow for $productId. Pro state is: ${proUpgradeState.value}")
        val currentUser = uiState.value.currentUser
        if (currentUser == null) {
            _internalState.update {
                it.copy(bannerMessage = BannerMessage(appContext.getString(R.string.sign_in_to_purchase), isError = true))
            }
            return
        }

        billingClientWrapper.clearAccountConflict()
        billingClientWrapper.launchPurchaseFlow(
            activity = activity,
            productId = productId,
            obfuscatedAccountId = PurchaseAccountObfuscator.obfuscatedAccountId(currentUser.uid)
        )
    }

    fun clearBillingError() {
        billingClientWrapper.clearError()
    }

    fun setSyncEnabled(enabled: Boolean) {
        if (!uiState.value.isProUser) {
            Timber.d("Sync toggle blocked for free user.")
            _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_sync_pro_feature)) }
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

    private fun shouldDownloadRemoteBookContent(local: RecentFileItem, remote: RecentFileItem): Boolean {
        val localFile = local.getUri()?.path?.let(::File)
        val localContentTimestamp = local.fileContentModifiedTimestamp.takeIf { it > 0L }
            ?: localFile?.takeIf { it.isFile }?.lastModified()
            ?: 0L
        return local.sourceFolderUri == null &&
            !local.isDeleted &&
            local.type == remote.type &&
            sharedCloudBookContentFileName(local.bookId, local.type) != null &&
            shouldDownloadRemoteCloudBookContent(
                localFileAvailable = local.isAvailable && localFile?.isFile != false,
                localContentModifiedTimestamp = localContentTimestamp,
                remoteContentModifiedTimestamp = remote.fileContentModifiedTimestamp,
                remoteDeleted = remote.isDeleted
            )
    }

    private fun shouldUploadLocalBookContent(local: RecentFileItem, remote: RecentFileItem?): Boolean {
        val localFile = local.getUri()?.path?.let(::File)
        val localContentTimestamp = local.fileContentModifiedTimestamp.takeIf { it > 0L }
            ?: localFile?.takeIf { it.isFile }?.lastModified()
            ?: 0L
        return local.sourceFolderUri == null &&
            sharedCloudBookContentFileName(local.bookId, local.type) != null &&
            shouldUploadLocalCloudBookContent(
                localFileAvailable = local.isAvailable && localFile?.isFile == true,
                localContentModifiedTimestamp = localContentTimestamp,
                remoteContentModifiedTimestamp = remote?.fileContentModifiedTimestamp
            )
    }

    private suspend fun downloadCloudBookFile(accessToken: String, remote: RecentFileItem): Boolean {
        val fileName = sharedCloudBookContentFileName(remote.bookId, remote.type) ?: return false
        val driveFileId = googleDriveRepository.getFiles(accessToken)
            ?.files
            .orEmpty()
            .firstOrNull { it.name == fileName }
            ?.id
            ?: return false

        val destinationFile = bookImporter.createBookFile(fileName)
        if (!googleDriveRepository.downloadFile(accessToken, driveFileId, destinationFile)) {
            destinationFile.delete()
            return false
        }

        if (remote.fileContentModifiedTimestamp > 0L) {
            destinationFile.setLastModified(remote.fileContentModifiedTimestamp)
        }
        cleanupBookDataLocally(remote.bookId)
        addFileToRecent(
            destinationFile.toUri(),
            remote.type,
            remote.bookId,
            customDisplayName = remote.displayName,
            isRecent = remote.isRecent,
            sourceFolderUri = null
        )
        return true
    }

    private fun scheduleCloudContentRetry(bookIds: Set<String>) {
        if (bookIds.isEmpty() || cloudContentRetryJob?.isActive == true) return
        cloudContentRetryJob = viewModelScope.launch {
            delay(CLOUD_CONTENT_RETRY_DELAY_MILLIS)
            if (uiState.value.isSyncEnabled) {
                logCloudSyncTrace { "android.full_sync.content_retry books=${bookIds.joinToString()}" }
                syncWithCloud(showBanner = false).join()
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
            logCloudSyncTrace {
                "android.full_sync.skip reason=${if (!hasPermissions) "missing_drive_permissions" else "no_user"} " +
                    "showBanner=$showBanner"
            }
            if (showBanner) _internalState.update {
                it.copy(errorMessage = appContext.getString(R.string.error_not_signed_in_sync))
            }
            return@launch
        }

        logCloudSyncTrace {
            "android.full_sync.start user=${currentUser.uid} showBanner=$showBanner " +
                "folderSync=${_internalState.value.isFolderSyncEnabled}"
        }
        if (showBanner) {
            _internalState.update {
                it.copy(bannerMessage = BannerMessage(appContext.getString(R.string.banner_cloud_sync_checking)))
            }
        }

        try {
            val accessToken = googleDriveRepository.getAccessToken(appContext) ?: run {
                logCloudSyncTrace { "android.full_sync.skip reason=no_access_token user=${currentUser.uid}" }
                return@launch
            }

            val deviceId = getInstallationId()
            val remoteBooksDeferred = async(Dispatchers.IO) {
                firestoreRepository.getAllBooks(currentUser.uid)
            }
            val remoteShelvesDeferred = async(Dispatchers.IO) {
                firestoreRepository.getAllShelves(currentUser.uid)
            }
            val localBooks = withContext(Dispatchers.IO) {
                val allFiles = recentFilesRepository.getAllFilesForSync()
                val filtered = if (_internalState.value.isFolderSyncEnabled) {
                    allFiles
                } else {
                    allFiles.filter { it.sourceFolderUri == null }
                }
                filtered
                    .filterNot { it.uriString?.startsWith("opds-pse") == true }
                    .filterNot { it.isManualOnlyReaderFile() }
            }

            val localShelfNames = prefs.getStringSet(KEY_SHELVES, emptySet()).orEmpty()
            val remoteBooks = remoteBooksDeferred.await()
                .filterNot { it.isManualOnlyReaderFile() }
            val remoteShelves = remoteShelvesDeferred.await()
            val initialDriveFiles = withContext(Dispatchers.IO) {
                googleDriveRepository.getFiles(accessToken)?.files.orEmpty().associateBy { it.name }
            }
            logCloudSyncTrace {
                "android.full_sync.loaded user=${currentUser.uid} device=$deviceId " +
                    "localBooks=${localBooks.size} remoteBooks=${remoteBooks.size} " +
                    "remoteShelves=${remoteShelves.size} driveFiles=${initialDriveFiles.size}"
            }
            val syncableBookIds = (localBooks.map { it.bookId } + remoteBooks.map { it.bookId }).toSet()
            val allKnownShelfNames =
                (localShelfNames + remoteShelves.map { it.name }).toSet()
            val localShelves = allKnownShelfNames.mapNotNull { name ->
                val timestamp = prefs.getLong("$KEY_SHELF_TIMESTAMP_PREFIX$name", 0L)
                if (timestamp == 0L && name !in localShelfNames) return@mapNotNull null
                val bookIds = prefs.getStringSet(
                    "$KEY_SHELF_CONTENT_PREFIX$name", emptySet()
                ).orEmpty().filter { it in syncableBookIds }
                val isDeleted = prefs.getBoolean("$KEY_SHELF_DELETED_PREFIX$name", false)
                ShelfMetadata(name, bookIds, timestamp, isDeleted)
            }

            // 3. Merge Books
            val localBooksMap = localBooks.associateBy { it.bookId }
            val remoteBooksMap = remoteBooks.associateBy { it.bookId }
            val allBookIds = (localBooksMap.keys + remoteBooksMap.keys).distinct()
            val pendingContentDownloads = mutableSetOf<String>()

            allBookIds.forEach { bookId ->
                val local = localBooksMap[bookId]
                val remote = remoteBooksMap[bookId]

                if (local?.sourceFolderUri != null) {
                    logCloudSyncTrace { "android.full_sync.book_skip reason=folder_book ${local.cloudSyncTraceSummary()}" }
                    Timber.d("Skipping cloud book metadata merge for local folder book: ${local.displayName}")
                    return@forEach
                }

                if (local != null && remote != null) {
                    logCloudSyncTrace {
                        "android.full_sync.compare book=$bookId ${local.cloudSyncTraceSummary()} " +
                            "${remote.cloudSyncTraceSummary()} "
                    }
                    Timber.tag("AnnotationSync").d(
                        "Checking $bookId. LocalTS: ${local.lastModifiedTimestamp}, RemoteTS: ${remote.lastModifiedTimestamp}, RemoteHasAnn: ${remote.hasAnnotations}"
                    )
                }

                when {
                    local != null && remote == null -> {
                        if (local.isDeleted) {
                            logCloudSyncTrace { "android.full_sync.decision action=upload_deleted_metadata ${local.cloudSyncTraceSummary()}" }
                            uploadSingleBookMetadata(local)
                        } else {
                            logCloudSyncTrace { "android.full_sync.decision action=upload_new_book ${local.cloudSyncTraceSummary()}" }
                            uploadNewBookAndMetadata(local)
                        }
                    }

                    local == null && remote != null -> {
                        if (remote.isDeleted) {
                            logCloudSyncTrace { "android.full_sync.decision action=skip_deleted_remote_only ${remote.cloudSyncTraceSummary()}" }
                            return@forEach
                        }
                        logCloudSyncTrace { "android.full_sync.decision action=apply_remote_new ${remote.cloudSyncTraceSummary()}" }
                        recentFilesRepository.addRecentFile(remote.toRecentFileItem())
                        if (remote.hasAnnotations) {
                            val remoteAnnotationDriveTimestamp =
                                initialDriveFiles[cloudPdfAnnotationDriveFileName(bookId)]?.modifiedTimeMillis ?: 0L
                            val remoteAnnotationTimestamp = remote.effectiveAnnotationModifiedTimestamp(remoteAnnotationDriveTimestamp)
                            logCloudAnnotationSyncTrace {
                                "android.full_sync.remote_only_download book=$bookId remoteTs=${remote.lastModifiedTimestamp} " +
                                    "remoteAnnTs=$remoteAnnotationTimestamp remoteDriveAnnTs=$remoteAnnotationDriveTimestamp " +
                                    "remoteHasAnnotations=${remote.hasAnnotations}"
                            }
                            downloadAnnotationsForBook(accessToken, bookId, remoteAnnotationTimestamp)
                        }
                    }

                    local != null && remote != null -> {
                        val remoteItem = remote.toRecentFileItem()
                        val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId)
                        val deletedInkFile = pdfAnnotationRepository.getDeletedAnnotationsFileForSync(bookId)
                        val richTextFile = pdfRichTextRepository.getFileForSync(bookId)
                        val layoutFile = pageLayoutRepository.getLayoutFile(bookId)
                        val textBoxFile = pdfTextBoxRepository.getFileForSync(bookId)
                        val highlightFile = pdfHighlightRepository.getFileForSync(bookId)
                        val localSidecars = AndroidPdfCloudSidecarState(
                            hasInk = inkFile.hasSyncableCloudAnnotationPayload(),
                            inkTimestamp = inkFile?.lastModified() ?: 0L,
                            hasDeletedInk = deletedInkFile.hasSyncableCloudAnnotationPayload(),
                            deletedInkTimestamp = deletedInkFile?.lastModified() ?: 0L,
                            hasRichText = richTextFile.hasSyncableCloudAnnotationPayload(),
                            richTextTimestamp = richTextFile.lastModified(),
                            hasLayout = layoutFile.exists(),
                            layoutTimestamp = layoutFile.lastModified(),
                            hasTextBoxes = textBoxFile.hasSyncableCloudAnnotationPayload(),
                            textBoxesTimestamp = textBoxFile.lastModified(),
                            hasHighlights = highlightFile.hasSyncableCloudAnnotationPayload(),
                            highlightsTimestamp = highlightFile.lastModified()
                        )
                        val fileLastModified = localSidecars.annotationPayloadTimestamp
                        val remoteAnnotationDriveTimestamp =
                            initialDriveFiles[cloudPdfAnnotationDriveFileName(bookId)]?.modifiedTimeMillis ?: 0L
                        val remoteAnnotationTimestamp = remote.effectiveAnnotationModifiedTimestamp(remoteAnnotationDriveTimestamp)
                        val localAnnotationsShouldUpload = shouldUploadLocalPdfCloudAnnotations(
                            localSidecars = localSidecars,
                            remoteHasAnnotations = remote.hasAnnotations,
                            remoteAnnotationModifiedTimestamp = remoteAnnotationTimestamp
                        )
                        logCloudAnnotationSyncTrace {
                            "android.full_sync.inspect book=$bookId remoteHas=${remote.hasAnnotations} " +
                                "remoteTs=${remote.lastModifiedTimestamp} remoteAnnTs=$remoteAnnotationTimestamp " +
                                "remoteDriveAnnTs=$remoteAnnotationDriveTimestamp " +
                                "localTs=${local.lastModifiedTimestamp} " +
                                "remoteReadTs=${remote.effectiveReadingPositionModifiedTimestamp()} " +
                                "localReadTs=${local.effectiveReadingPositionModifiedTimestamp()} " +
                                "localPayload=${localSidecars.hasAnnotationPayload} " +
                                "localPayloadTs=${localSidecars.annotationPayloadTimestamp} " +
                                "layoutExists=${localSidecars.hasLayout} layoutTs=${localSidecars.layoutTimestamp} " +
                                "shouldUploadLocal=$localAnnotationsShouldUpload"
                        }
                        if (remote.isDeleted) {
                            val localWinsDeletedRemote = shouldUploadLocalCloudBookMetadataUpdate(
                                localModifiedTimestamp = local.lastModifiedTimestamp,
                                remoteModifiedTimestamp = remote.lastModifiedTimestamp
                            )
                            val remoteDeleteWins = shouldApplyRemoteCloudBookMetadataUpdate(
                                localModifiedTimestamp = local.lastModifiedTimestamp,
                                remoteModifiedTimestamp = remote.lastModifiedTimestamp
                            )
                            when {
                                localWinsDeletedRemote -> {
                                    logCloudSyncTrace {
                                        "android.full_sync.decision action=resurrect_upload_local book=$bookId " +
                                            "localTs=${local.lastModifiedTimestamp} remoteTs=${remote.lastModifiedTimestamp} payloadSidecarTs=$fileLastModified"
                                    }
                                    if (shouldUploadLocalBookContent(local, null)) {
                                        uploadNewBookAndMetadata(local)
                                    } else {
                                        uploadSingleBookMetadata(local)
                                    }
                                }

                                remoteDeleteWins -> {
                                    logCloudSyncTrace {
                                        "android.full_sync.decision action=apply_remote_delete book=$bookId " +
                                            "localTs=${local.lastModifiedTimestamp} remoteTs=${remote.lastModifiedTimestamp} payloadSidecarTs=$fileLastModified"
                                    }
                                    recentFilesRepository.deleteFilePermanently(listOf(bookId))
                                }

                                else -> {
                                    logCloudSyncTrace {
                                        "android.full_sync.decision action=skip_equal_delete book=$bookId " +
                                            "localTs=${local.lastModifiedTimestamp} remoteTs=${remote.lastModifiedTimestamp} payloadSidecarTs=$fileLastModified"
                                    }
                                }
                            }
                            return@forEach
                        }
                        val localWithRemoteEpubAnnotations = local.mergeRemoteEpubAnnotationMetadata(remote)
                        val effectiveLocal = if (localWithRemoteEpubAnnotations != local) {
                            logCloudSyncTrace {
                                "android.full_sync.decision action=merge_remote_epub_annotations book=$bookId " +
                                    "local=${local.cloudSyncTraceSummary()} ${remote.cloudSyncTraceSummary()} " +
                                    "merged=${localWithRemoteEpubAnnotations.cloudSyncTraceSummary()}"
                            }
                            recentFilesRepository.addRecentFile(localWithRemoteEpubAnnotations)
                            localWithRemoteEpubAnnotations
                        } else {
                            local
                        }
                        val localReadingTimestamp = effectiveLocal.effectiveReadingPositionModifiedTimestamp()
                        val remoteReadingTimestamp = remote.effectiveReadingPositionModifiedTimestamp()
                        val localReadingPositionShouldUpload = localReadingTimestamp > remoteReadingTimestamp
                        val shouldDownloadContent = shouldDownloadRemoteBookContent(effectiveLocal, remoteItem)
                        val downloadedRemoteContent = if (shouldDownloadContent) {
                            logCloudSyncTrace {
                                "android.full_sync.content_download_start book=$bookId " +
                                    "localContentTs=${effectiveLocal.fileContentModifiedTimestamp} remoteContentTs=${remote.fileContentModifiedTimestamp}"
                            }
                            downloadCloudBookFile(accessToken, remoteItem.copy(displayName = remoteItem.displayName.ifBlank { effectiveLocal.displayName }))
                        } else {
                            false
                        }
                        logCloudSyncTrace {
                            "android.full_sync.content_decision book=$bookId shouldDownload=$shouldDownloadContent " +
                                "downloaded=$downloadedRemoteContent localPayloadSidecarTs=$fileLastModified " +
                                "localLayoutTs=${localSidecars.layoutTimestamp.takeIf { localSidecars.hasLayout } ?: 0L}"
                        }
                        if (shouldDownloadContent && !downloadedRemoteContent) {
                            pendingContentDownloads += bookId
                        }

                        if (shouldUploadLocalCloudBookMetadataUpdate(
                                localModifiedTimestamp = effectiveLocal.lastModifiedTimestamp,
                                remoteModifiedTimestamp = remote.lastModifiedTimestamp
                            )
                        ) {
                            logCloudSyncTrace {
                                "android.full_sync.decision action=upload_local book=$bookId " +
                                    "localTs=${effectiveLocal.lastModifiedTimestamp} remoteTs=${remote.lastModifiedTimestamp} " +
                                    "localReadTs=$localReadingTimestamp remoteReadTs=$remoteReadingTimestamp payloadSidecarTs=$fileLastModified " +
                                    "uploadContent=${shouldUploadLocalBookContent(effectiveLocal, remoteItem)}"
                            }
                            if (shouldUploadLocalBookContent(effectiveLocal, remoteItem)) {
                                uploadNewBookAndMetadata(effectiveLocal)
                            } else {
                                uploadSingleBookMetadata(effectiveLocal)
                            }
                        } else {
                            val isMetadataNewer =
                                shouldApplyRemoteCloudBookMetadataUpdate(
                                    localModifiedTimestamp = effectiveLocal.lastModifiedTimestamp,
                                    remoteModifiedTimestamp = remote.lastModifiedTimestamp
                                )

                            if (isMetadataNewer) {
                                logCloudSyncTrace {
                                    "android.full_sync.decision action=apply_remote_metadata book=$bookId " +
                                        "localTs=${effectiveLocal.lastModifiedTimestamp} remoteTs=${remote.lastModifiedTimestamp} payloadSidecarTs=$fileLastModified " +
                                        "downloadedContent=$downloadedRemoteContent"
                                }
                                val remoteForLocalDb = if (shouldDownloadContent && !downloadedRemoteContent) {
                                    remote.toRecentFileItem().copy(
                                        fileContentModifiedTimestamp = effectiveLocal.fileContentModifiedTimestamp
                                    )
                                } else {
                                    remote.toRecentFileItem()
                                }
                                recentFilesRepository.addRecentFile(
                                    remoteForLocalDb
                                )
                                if (localAnnotationsShouldUpload || localReadingPositionShouldUpload) {
                                    recentFilesRepository.getFileByBookId(bookId)?.let { merged ->
                                        logCloudAnnotationSyncTrace {
                                            "android.full_sync.upload_local_annotations book=$bookId reason=remote_metadata_newer " +
                                                "remoteTs=${remote.lastModifiedTimestamp} localPayloadTs=$fileLastModified " +
                                                "localReadTs=$localReadingTimestamp remoteReadTs=$remoteReadingTimestamp " +
                                                "uploadReadingPosition=$localReadingPositionShouldUpload"
                                        }
                                        logCloudSyncTrace {
                                            "android.full_sync.decision action=upload_local_supplement book=$bookId " +
                                                "remoteMetadataTs=${remote.lastModifiedTimestamp} payloadSidecarTs=$fileLastModified " +
                                                "uploadAnnotations=$localAnnotationsShouldUpload uploadReadingPosition=$localReadingPositionShouldUpload"
                                        }
                                        uploadSingleBookMetadata(merged)
                                    }
                                }
                            } else {
                                logCloudSyncTrace {
                                    "android.full_sync.decision action=metadata_noop book=$bookId " +
                                        "localTs=${effectiveLocal.lastModifiedTimestamp} remoteTs=${remote.lastModifiedTimestamp} " +
                                        "localReadTs=$localReadingTimestamp remoteReadTs=$remoteReadingTimestamp payloadSidecarTs=$fileLastModified"
                                }
                                if (localAnnotationsShouldUpload || localReadingPositionShouldUpload) {
                                    logCloudAnnotationSyncTrace {
                                        "android.full_sync.upload_local_annotations book=$bookId reason=metadata_noop " +
                                            "remoteTs=${remote.lastModifiedTimestamp} localPayloadTs=$fileLastModified " +
                                            "localReadTs=$localReadingTimestamp remoteReadTs=$remoteReadingTimestamp"
                                    }
                                    logCloudSyncTrace {
                                        "android.full_sync.decision action=upload_local_supplement book=$bookId " +
                                            "metadataEqual=${effectiveLocal.lastModifiedTimestamp == remote.lastModifiedTimestamp} " +
                                            "uploadAnnotations=$localAnnotationsShouldUpload uploadReadingPosition=$localReadingPositionShouldUpload " +
                                            "payloadSidecarTs=$fileLastModified"
                                    }
                                    uploadSingleBookMetadata(effectiveLocal)
                                }
                            }

                            val shouldDownloadRemoteAnnotations = shouldDownloadRemotePdfCloudAnnotations(
                                localSidecars = localSidecars,
                                localAnnotationsShouldUpload = localAnnotationsShouldUpload,
                                remoteHasAnnotations = remote.hasAnnotations,
                                remoteAnnotationModifiedTimestamp = remoteAnnotationTimestamp
                            )

                            if (shouldDownloadRemoteAnnotations) {
                                logCloudAnnotationSyncTrace {
                                    "android.full_sync.download_remote_annotations book=$bookId " +
                                        "metadataNewer=$isMetadataNewer localPayloadMissing=${!localSidecars.hasAnnotationPayload} " +
                                        "remoteTs=${remote.lastModifiedTimestamp} remoteAnnTs=$remoteAnnotationTimestamp " +
                                        "localPayloadTs=$fileLastModified " +
                                        "layoutTs=${localSidecars.layoutTimestamp.takeIf { localSidecars.hasLayout } ?: 0L}"
                                }
                                logCloudSyncTrace {
                                    "android.full_sync.sidecar_download_start book=$bookId reason=" +
                                        "metadataNewer=$isMetadataNewer localPayloadMissing=${!localSidecars.hasAnnotationPayload} " +
                                        "remoteTs=${remote.lastModifiedTimestamp} remoteAnnTs=$remoteAnnotationTimestamp " +
                                        "localPayloadSidecarTs=$fileLastModified " +
                                        "localLayoutTs=${localSidecars.layoutTimestamp.takeIf { localSidecars.hasLayout } ?: 0L}"
                                }
                                Timber.tag("AnnotationSync").d("Triggering download for $bookId.")
                                downloadAnnotationsForBook(accessToken, bookId, remoteAnnotationTimestamp)
                            } else {
                                logCloudAnnotationSyncTrace {
                                    "android.full_sync.skip_remote_annotations book=$bookId " +
                                        "remoteHas=${remote.hasAnnotations} localShouldUpload=$localAnnotationsShouldUpload " +
                                        "metadataNewer=$isMetadataNewer localPayload=${localSidecars.hasAnnotationPayload} " +
                                        "remoteTs=${remote.lastModifiedTimestamp} remoteAnnTs=$remoteAnnotationTimestamp " +
                                        "localPayloadTs=$fileLastModified"
                                }
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
                        currentUser.uid, local, deviceId
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
                                    remote.bookIds.filter { it in syncableBookIds }.toSet()
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
                                        remote.bookIds.filter { it in syncableBookIds }.toSet()
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
            }.filterNot { it.isManualOnlyReaderFile() }
            val remoteFiles = withContext(Dispatchers.IO) {
                googleDriveRepository.getFiles(accessToken)?.files.orEmpty().associateBy { it.name }
            }

            finalMergedBooks.forEach { book ->
                if (book.sourceFolderUri != null) return@forEach
                val fileName = sharedCloudBookContentFileName(book.bookId, book.type) ?: return@forEach
                if (book.isDeleted) {
                    remoteFiles[fileName]?.id?.let { fileId ->
                        Timber.d("Deleting from Drive: $fileName")
                        googleDriveRepository.deleteDriveFile(accessToken, fileId)
                    }
                    remoteFiles[cloudPdfAnnotationDriveFileName(book.bookId)]?.id?.let { fileId ->
                        Timber.d("Deleting annotation bundle from Drive: ${book.bookId}")
                        googleDriveRepository.deleteDriveFile(accessToken, fileId)
                    }
                    recentFilesRepository.deleteFilePermanently(listOf(book.bookId))
                } else if (
                    book.sourceFolderUri == null &&
                    book.isAvailable &&
                    !remoteFiles.containsKey(fileName)
                ) {
                    val remoteItem = remoteBooksMap[book.bookId]?.toRecentFileItem()
                    if (remoteItem == null || shouldUploadLocalBookContent(book, remoteItem)) {
                        book.getUri()?.path?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                Timber.d("Uploading book: ${book.displayName}")
                                val uploadedFile = googleDriveRepository.uploadFile(
                                    accessToken, book.bookId, file, book.type
                                )
                                if (uploadedFile != null) {
                                    val contentTimestamp = book.fileContentModifiedTimestamp.takeIf { it > 0L }
                                        ?: file.lastModified()
                                    uploadSingleBookMetadata(
                                        book.copy(
                                            fileSize = file.length(),
                                            fileContentModifiedTimestamp = contentTimestamp
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        pendingContentDownloads += book.bookId
                        logCloudSyncTrace {
                            "android.full_sync.content_wait_missing_remote book=${book.bookId} " +
                                "file=$fileName localContentTs=${book.fileContentModifiedTimestamp} " +
                                "remoteContentTs=${remoteItem.fileContentModifiedTimestamp}"
                        }
                    }
                } else if (!book.isAvailable && remoteFiles.containsKey(fileName)) {
                    Timber.d("Sync: Triggering auto-download for ${book.displayName}")
                    val remoteItem = remoteBooksMap[book.bookId]
                        ?.toRecentFileItem()
                        ?.copy(displayName = book.displayName)
                        ?: book
                    val downloaded = downloadCloudBookFile(accessToken, remoteItem)
                    if (!downloaded) {
                        pendingContentDownloads += book.bookId
                    }
                } else if (!book.isAvailable) {
                    pendingContentDownloads += book.bookId
                }
            }

            if (pendingContentDownloads.isNotEmpty()) {
                logCloudSyncTrace {
                    "android.full_sync.content_pending books=${pendingContentDownloads.joinToString()}"
                }
                scheduleCloudContentRetry(pendingContentDownloads)
            } else {
                cloudContentRetryJob?.cancel()
                cloudContentRetryJob = null
            }
            syncFonts(currentUser.uid)

            logCloudSyncTrace { "android.full_sync.complete user=${currentUser.uid}" }
            if (showBanner) {
                _internalState.update {
                    it.copy(
                        isLoading = false, bannerMessage = BannerMessage(appContext.getString(R.string.banner_cloud_sync_complete))
                    )
                }
            }
        } catch (e: Exception) {
            logCloudSyncError(e) { "android.full_sync.failed user=${currentUser.uid}" }
            Timber.tag("AnnotationSync").e(e, "Error during cloud sync")
            if (showBanner) {
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_sync_library_failed))
                }
            }
        }
    }

    private suspend fun downloadAnnotationsForBook(
        accessToken: String,
        bookId: String,
        annotationModifiedTimestamp: Long
    ) {
        // We download to a temp location first to inspect the content
        val tempDownloadFile = File(appContext.cacheDir, "temp_download_${bookId}.json")

        logCloudSyncTrace {
            "android.sidecar_download.start book=$bookId remoteAnnTs=$annotationModifiedTimestamp temp=${tempDownloadFile.name}"
        }
        logCloudAnnotationSyncTrace {
            "android.download.start book=$bookId remoteAnnTs=$annotationModifiedTimestamp temp=${tempDownloadFile.name}"
        }
        Timber.tag("AnnotationSync").d("Attempting download of bundle for $bookId.")

        val didDownload =
            googleDriveRepository.downloadAnnotationFile(accessToken, bookId, tempDownloadFile)

        if (didDownload && tempDownloadFile.exists()) {
            logCloudAnnotationSyncTrace {
                "android.download.success book=$bookId remoteAnnTs=$annotationModifiedTimestamp bytes=${tempDownloadFile.length()}"
            }
            logCloudSyncTrace {
                "android.sidecar_download.success book=$bookId remoteAnnTs=$annotationModifiedTimestamp bytes=${tempDownloadFile.length()}"
            }
            Timber.tag("AnnotationSync")
                .d("Download SUCCESS. Size: ${tempDownloadFile.length()}. Unpacking...")

            try {
                val jsonString = tempDownloadFile.readText()
                val appliedAnnotationTimestamp =
                    annotationModifiedTimestamp.takeIf { it > 0L }
                        ?: tempDownloadFile.lastModified().takeIf { it > 0L }
                        ?: 0L
                Timber.d(
                    "android.cloud.import.downloaded book=$bookId rawLen=${jsonString.length}"
                )

                // Determine format
                val isBundle = try {
                    val obj = JSONObject(jsonString)
                    obj.has("version") ||
                        obj.has(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS) ||
                        obj.has("ink") ||
                        obj.has("text") ||
                        obj.has("layout") ||
                        obj.has("textBoxes") ||
                        obj.has("highlights")
                } catch (_: Exception) {
                    false
                }
                logCloudAnnotationSyncTrace {
                    "android.download.inspect book=$bookId isBundle=$isBundle rawBytes=${jsonString.length} " +
                        "appliedAnnTs=$appliedAnnotationTimestamp rawPreview=${jsonString.take(80).replace('\n', ' ')}"
                }

                val inkFile = pdfAnnotationRepository.getAnnotationFileForSync(bookId) ?: File(
                    appContext.filesDir, "annotations/annotation_$bookId.json"
                )
                val deletedInkFile = File(appContext.filesDir, "annotations/deleted_annotation_$bookId.json")
                val richTextFile = pdfRichTextRepository.getFileForSync(bookId)
                val layoutFile = pageLayoutRepository.getLayoutFile(bookId)
                val textBoxFile = pdfTextBoxRepository.getFileForSync(bookId)
                val highlightFile = pdfHighlightRepository.getFileForSync(bookId)

                inkFile.parentFile?.mkdirs()
                deletedInkFile.parentFile?.mkdirs()
                richTextFile.parentFile?.mkdirs()
                layoutFile.parentFile?.mkdirs()
                textBoxFile.parentFile?.mkdirs()
                highlightFile.parentFile?.mkdirs()

                if (isBundle) {
                    val bundle = JSONObject(
                        SharedPdfAnnotationSidecarCodec.legacyAndroidDataJsonFromCanonical(jsonString)
                    )
                    Timber.d(
                        "android.cloud.import.bundle book=$bookId hasRichText=${bundle.has("text")} keys=${bundle.keys().asSequence().toList()}"
                    )
                    logCloudAnnotationSyncTrace {
                        "android.download.bundle_keys book=$bookId keys=${bundle.keys().asSequence().toList()} " +
                            "hasInk=${bundle.has("ink")} hasText=${bundle.has("text")} " +
                            "hasLayout=${bundle.has("layout")} hasTextBoxes=${bundle.has("textBoxes")} " +
                            "hasHighlights=${bundle.has("highlights")} " +
                            "hasDeletedInk=${bundle.has(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATION_DELETIONS)}"
                    }

                    fun writeSafe(key: String, file: File) {
                        if (bundle.has(key)) {
                            file.parentFile?.mkdirs()
                            val content = bundle.get(key).toString()
                            file.writeText(content)
                            appliedAnnotationTimestamp.takeIf { it > 0L }?.let(file::setLastModified)
                            logCloudAnnotationSyncTrace {
                                "android.download.write key=$key book=$bookId bytes=${content.length} " +
                                    "path=${file.absolutePath.cloudSyncPreview(140)} ts=${file.lastModified()}"
                            }
                            if (key == "text") {
                                Timber.d(
                                    "android.cloud.import.writeRichText book=$bookId rawLen=${content.length} file=${file.absolutePath}"
                                )
                            }
                        } else {
                            if (key == "layout") {
                                logCloudAnnotationSyncTrace {
                                    "android.download.preserve_missing key=layout book=$bookId " +
                                        "path=${file.absolutePath.cloudSyncPreview(140)} exists=${file.exists()}"
                                }
                                Timber.d(
                                    "android.cloud.import.preserveMissingLayout book=$bookId file=${file.absolutePath}"
                                )
                                return
                            }
                            if (key == "text" && file.exists()) {
                                Timber.d(
                                    "android.cloud.import.deleteMissingRichText book=$bookId file=${file.absolutePath}"
                                )
                            }
                            if (file.exists()) {
                                val deleted = file.delete()
                                logCloudAnnotationSyncTrace {
                                    "android.download.delete_missing key=$key book=$bookId deleted=$deleted " +
                                        "path=${file.absolutePath.cloudSyncPreview(140)}"
                                }
                            } else {
                                logCloudAnnotationSyncTrace {
                                    "android.download.missing_key key=$key book=$bookId path=${file.absolutePath.cloudSyncPreview(140)}"
                                }
                            }
                        }
                    }

                    writeSafe("ink", inkFile)
                    writeSafe(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATION_DELETIONS, deletedInkFile)
                    writeSafe("text", richTextFile)
                    writeSafe("layout", layoutFile)
                    writeSafe("textBoxes", textBoxFile)
                    writeSafe("highlights", highlightFile)

                    logCloudSyncTrace {
                        "android.sidecar_download.applied_bundle book=$bookId remoteAnnTs=$annotationModifiedTimestamp " +
                            "keys=${bundle.keys().asSequence().toList()}"
                    }
                    Timber.tag("AnnotationSync").d("Unpacked unified bundle.")
                } else {
                    Timber.tag("AnnotationSync").d("Detected legacy format (Ink only).")
                    inkFile.writeText(jsonString)
                    appliedAnnotationTimestamp.takeIf { it > 0L }?.let(inkFile::setLastModified)
                    logCloudAnnotationSyncTrace {
                        "android.download.write_legacy_ink book=$bookId bytes=${jsonString.length} " +
                            "path=${inkFile.absolutePath.cloudSyncPreview(140)} ts=${inkFile.lastModified()}"
                    }
                    logCloudSyncTrace { "android.sidecar_download.applied_legacy book=$bookId remoteAnnTs=$annotationModifiedTimestamp" }
                }
            } catch (e: Exception) {
                logCloudAnnotationSyncError(e) { "android.download.apply_failed book=$bookId remoteAnnTs=$annotationModifiedTimestamp" }
                logCloudSyncError(e) { "android.sidecar_download.apply_failed book=$bookId remoteAnnTs=$annotationModifiedTimestamp" }
                Timber.e(e, "Error unpacking synced annotation data")
            } finally {
                tempDownloadFile.delete()
            }
        } else {
            logCloudAnnotationSyncTrace {
                "android.download.missing book=$bookId remoteAnnTs=$annotationModifiedTimestamp didDownload=$didDownload " +
                    "tempExists=${tempDownloadFile.exists()} tempBytes=${tempDownloadFile.length()}"
            }
            logCloudSyncTrace { "android.sidecar_download.missing book=$bookId remoteAnnTs=$annotationModifiedTimestamp" }
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
        sourceFolderUri: String? = null,
        bundleResult: CalibreBundleResult? = null
    ) = withContext(Dispatchers.IO) {
        val addStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf")
            .d("[$bookId] addFileToRecent START | type=$type | hasEpubBook=${epubBook != null}")
        val isNewBook = withContext(Dispatchers.IO) {
            recentFilesRepository.getFileByBookId(bookId) == null
        }

        val fileSize = withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).length() } ?: 0L
                } else {
                    appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                        } else 0L
                    } ?: 0L
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get file size for $uri")
                0L
            }
        }
        val fileContentModifiedTimestamp = withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).lastModified() } ?: 0L
                } else {
                    DocumentFile.fromSingleUri(appContext, uri)?.lastModified() ?: 0L
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get file modified time for $uri")
                0L
            }
        }

        val existingItem = recentFilesRepository.getFileByBookId(bookId)
        val displayName = customDisplayName ?: existingItem?.displayName ?: getFileNameFromUri(
            uri, appContext
        ) ?: "Unknown File"

        var coverPath: String? = bundleResult?.coverCachePath
        var title: String? = bundleResult?.title
        var author: String? = bundleResult?.author
        var seriesName: String? = bundleResult?.seriesName
        var seriesIndex: Double? = bundleResult?.seriesIndex
        var description: String? = bundleResult?.description
        var bookForMetadata = epubBook

        if (bookForMetadata == null && bundleResult == null && type in EPUB_READER_FILE_TYPES) {
            Timber.d("Parsing downloaded book for cover/metadata: $displayName")
            Timber.tag("FileOpenPerf")
                .d("[$bookId] addFileToRecent: Starting metadata parsing (no book provided)")
            val parseStart = System.currentTimeMillis()
            try {
                importMutex.withLock {
                    bookForMetadata = withContext(Dispatchers.IO) {
                        appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                            when (type) {
                                FileType.EPUB -> {
                                    epubParser.createEpubBook(
                                        inputStream = inputStream,
                                        bookId = bookId,
                                        originalBookNameHint = displayName,
                                        parseContent = false
                                    )
                                }
                                FileType.MOBI -> {
                                    mobiParser.createMobiBook(
                                        inputStream = inputStream,
                                        bookId = bookId,
                                        originalBookNameHint = displayName,
                                        parseContent = false
                                    )
                                }
                                FileType.FB2 -> {
                                    fb2Parser.createFb2Book(
                                        inputStream = inputStream,
                                        bookId = bookId,
                                        originalBookNameHint = displayName,
                                        parseContent = false
                                    )
                                }
                                FileType.ODT, FileType.FODT -> {
                                    odtParser.createOdtBook(
                                        inputStream = inputStream,
                                        bookId = bookId,
                                        originalBookNameHint = displayName,
                                        isFlat = type == FileType.FODT,
                                        parseContent = false
                                    )
                                }
                                else -> {
                                    singleFileImporter.importSingleFile(
                                        inputStream,
                                        type,
                                        originalBookNameHint = displayName,
                                        bookId = bookId,
                                        parseContent = false
                                    )
                                }
                            }
                        }
                    }
                }
                Timber.tag("FileOpenPerf")
                    .d("[$bookId] addFileToRecent: Metadata parsing completed | elapsed=${System.currentTimeMillis() - parseStart}ms")
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Failed to parse metadata for book: $displayName. Proceeding with basic info."
                )
                bookForMetadata = null
            }
            Timber.tag("FileOpenPerf")
                .d("[$bookId] addFileToRecent COMPLETE | totalElapsed=${System.currentTimeMillis() - addStart}ms")
        }

        val finalBookMetadata = bookForMetadata

        if (type in EPUB_READER_FILE_TYPES && finalBookMetadata != null) {
            title = title ?: finalBookMetadata.title.takeIf { it.isNotBlank() && it != "content" } ?: displayName

            author = author ?: finalBookMetadata.author.takeIf {
                it.isNotBlank() && !it.equals("Unknown", ignoreCase = true)
            }

            if (coverPath == null) {
                finalBookMetadata.coverImage?.let { cover ->
                    coverPath = recentFilesRepository.saveCoverToCache(cover, uri)
                }
            }

            seriesName = seriesName ?: finalBookMetadata.seriesName
            seriesIndex = seriesIndex ?: finalBookMetadata.seriesIndex
            description = description ?: finalBookMetadata.description
        } else if (type in PDF_VIEWER_FILE_TYPES) {
            title = title ?: displayName

            if (type == FileType.PDF) {
                try {
                    appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfiumEngineProvider.withPdfium {
                            PdfiumCoreProvider.core.newDocument(pfd).use { pdfDocument ->
                                val meta = pdfDocument.getDocumentMeta()

                                val extractedTitle = meta.title
                                if (!extractedTitle.isNullOrBlank() && title == displayName) {
                                    title = extractedTitle
                                }

                                val extractedAuthor = meta.author
                                if (!extractedAuthor.isNullOrBlank() && author == null) {
                                    author = extractedAuthor
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to extract PDF title using PdfiumCore")
                }

                if (coverPath == null) {
                    val pdfCoverGenerator = PdfCoverGenerator(appContext)
                    val coverBitmap = pdfCoverGenerator.generateCover(uri)
                    if (coverBitmap != null) {
                        coverPath = recentFilesRepository.saveCoverToCache(coverBitmap, uri)
                    }
                }
            } else if (type == FileType.PPTX) {
                if (coverPath == null) {
                    val pptxCoverGenerator = PptxCoverGenerator(appContext)
                    val coverBitmap = pptxCoverGenerator.generateCover(uri)
                    if (coverBitmap != null) {
                        coverPath = recentFilesRepository.saveCoverToCache(coverBitmap, uri)
                    }
                }
            } else if (uri.scheme != "opds-pse" && type in COMIC_ARCHIVE_FILE_TYPES) {
                if (coverPath == null) {
                    var cacheFile: File? = null
                    try {
                        cacheFile = File(appContext.cacheDir, "temp_archive_cover_${System.currentTimeMillis()}.${type.name.lowercase()}")
                        withContext(Dispatchers.IO) {
                            appContext.contentResolver.openInputStream(uri)?.use { input ->
                                cacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        val archiveDoc = com.aryan.reader.pdf.ArchiveDocumentWrapper(cacheFile)
                        if (archiveDoc.getPageCount() > 0) {
                            val page = archiveDoc.openPage(0)
                            if (page != null) {
                                val w = page.getPageWidthPoint()
                                val h = page.getPageHeightPoint()
                                if (w > 0 && h > 0) {
                                    val targetHeight = 800
                                    val targetWidth = (targetHeight * (w.toFloat() / h.toFloat())).toInt()
                                    if (targetWidth > 0) {
                                        val bitmap = createBitmap(targetWidth, targetHeight)
                                        page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, false)
                                        coverPath = recentFilesRepository.saveCoverToCache(bitmap, uri)
                                    }
                                }
                                page.close()
                            }
                        }
                        archiveDoc.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Error generating comic archive cover")
                    } finally {
                        try {
                            if (cacheFile?.exists() == true) {
                                val deleted = cacheFile.delete()
                                if (deleted) Timber.d("Successfully deleted temp archive file: ${cacheFile.name}")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to delete temp archive file")
                        }
                    }
                }
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
            sourceFolderUri = sourceFolderUri,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            description = description
        )
        recentFilesRepository.addRecentFile(newItem)
        Timber.i("Added/Updated $displayName ($type) to recent files via repository.")

        if (isNewBook) {
            uploadNewBookAndMetadata(newItem)
        }
    }

    fun setRecentFilesLimit(limit: Int) {
        _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.RecentLimitChanged(limit)) }
        prefs.edit { putInt(KEY_RECENT_FILES_LIMIT, limit) }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.SortChanged(sortOrder)) }
        prefs.edit { putString(KEY_SORT_ORDER, sortOrder.name) }
    }

    fun bannerMessageShown() {
        _internalState.update { it.copy(bannerMessage = null) }
        scheduleBannerAutoDismiss(null)
    }

    fun showBanner(message: String, isError: Boolean = false, isPersistent: Boolean = false) {
        val banner = BannerMessage(message, isError, isPersistent)
        _internalState.update { it.copy(bannerMessage = banner) }
        scheduleBannerAutoDismiss(banner)
    }

    private fun scheduleBannerAutoDismiss(banner: BannerMessage?) {
        if (_internalState.value.bannerMessage != banner) return
        bannerDismissJob?.cancel()
        bannerDismissJob = null
        val generation = ++bannerDismissGeneration
        if (banner == null || banner.isPersistent) return

        bannerDismissJob = viewModelScope.launch {
            delay(BANNER_AUTO_DISMISS_MILLIS)
            _internalState.update { state ->
                if (generation == bannerDismissGeneration && state.bannerMessage == banner) {
                    state.copy(bannerMessage = null)
                } else {
                    state
                }
            }
        }
    }

    fun errorMessageShown() {
        _internalState.update { it.copy(errorMessage = null) }
    }

    private fun sweepOrphanedCache() {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("Running Cache Sweeper to clean up orphaned temporary files...")
            try {
                val cacheDir = appContext.cacheDir
                if (!cacheDir.exists()) return@launch

                val oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
                val allDbIds = recentFilesRepository.getAllFilesForSync().map { it.bookId }.toSet()
                val validStreamHashes = allDbIds.map { it.hashCode().toString() }.toSet()
                val validActiveBookCacheDirs = allDbIds.mapTo(mutableSetOf()) {
                    ImportedFileCache.activeBookDirName(it)
                }
                ImportedFileCache.deleteStaleTemporaryBookDirs(appContext, TimeUnit.HOURS.toMillis(1))

                cacheDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.startsWith("temp_") || name.startsWith("sync_bundle_")) {
                        if (file.lastModified() < oneHourAgo) {
                            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                            if (deleted) Timber.d("Sweeper cleaned old temp file: $name")
                        }
                    } else if (ImportedFileCache.isActiveBookDir(name)) {
                        val legacyBookId = name.removePrefix("imported_file_")
                        if (name !in validActiveBookCacheDirs && legacyBookId !in allDbIds && file.lastModified() < oneHourAgo) {
                            val deleted = file.deleteRecursively()
                            if (deleted) Timber.d("Sweeper cleaned orphaned extracted cache: $name")
                        }
                    } else if (name.startsWith("opds_stream_")) {
                        val bookIdHash = name.removePrefix("opds_stream_")
                        if (bookIdHash !in validStreamHashes) {
                            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                            if (deleted) Timber.d("Sweeper cleaned orphaned OPDS stream cache for hash: $bookIdHash")
                        }
                    }
                }
                val legacyExtractedDir = File(cacheDir, "extracted_epubs")
                if (legacyExtractedDir.exists()) {
                    val deleted = legacyExtractedDir.deleteRecursively()
                    if (deleted) Timber.d("Sweeper reclaimed storage by deleting legacy extracted_epubs directory")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during cache sweep: ${e.message}")
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri, context: Context): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            try {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = it.getString(nameIndex)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Timber.w(e, "Permission denied while resolving display name for URI: $uri")
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Provider rejected display-name query for URI: $uri")
            } catch (e: RuntimeException) {
                Timber.w(e, "Unexpected failure while resolving display name for URI: $uri")
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

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return

        if (uris.size == 1) {
            onFileSelected(uris.first(), isFromRecent = false)
            return
        }

        viewModelScope.launch {
            _internalState.update {
                it.copy(
                    bannerMessage = BannerMessage(
                        message = appContext.resources.getQuantityString(
                            R.plurals.banner_importing_books_count,
                            uris.size,
                            uris.size
                        ),
                        isPersistent = true
                    ),
                    contextualActionItems = emptySet()
                )
            }

            var importedCount = 0
            var duplicateCount = 0
            var unsupportedCount = 0
            var failedCount = 0

            withContext(Dispatchers.IO) {
                for (externalUri in uris) {
                    val importResult = prepareBookForImport(externalUri)
                    if (importResult != null) {
                        val (internalUri, bookId, type) = importResult
                        val displayName = getFileNameFromUri(externalUri, appContext) ?: "Unknown File"

                        addFileToRecent(
                            uri = internalUri,
                            type = type,
                            bookId = bookId,
                            customDisplayName = displayName,
                            isRecent = false,
                            sourceFolderUri = null,
                            bundleResult = importResult.bundleResult
                        )
                        importedCount++
                    } else {
                        val hash = FileHasher.calculateSha256 {
                            appContext.contentResolver.openInputStream(externalUri)
                        }
                        if (hash != null && recentFilesRepository.getFileByBookId(hash) != null) {
                            duplicateCount++
                        } else if (getFileTypeFromUri(externalUri, appContext) == null) {
                            unsupportedCount++
                        } else {
                            failedCount++
                        }
                    }
                }
            }

            _internalState.update {
                val feedback = SharedImportPlanner.feedbackForCounts(
                    counts = SharedImportOutcomeCounts(
                        addedCount = importedCount,
                        duplicateCount = duplicateCount,
                        unsupportedCount = unsupportedCount,
                        failedCount = failedCount
                    ),
                    importedMessage = appContext.resources.getQuantityString(
                        R.plurals.banner_books_imported_library_tab,
                        importedCount,
                        importedCount
                    ),
                    duplicateMessage = appContext.getString(R.string.banner_duplicate_files_already_in_library),
                    unsupportedMessage = appContext.getString(R.string.error_unsupported_file_type),
                    failedMessage = appContext.getString(R.string.error_import_file_failed)
                )
                it.copy(
                    bannerMessage = BannerMessage(
                        message = feedback.message,
                        isError = feedback.isError,
                        isPersistent = false
                    )
                )
            }

            Timber.tag("BulkImport").i("Bulk import complete. $importedCount new files, $duplicateCount duplicates, $unsupportedCount unsupported, $failedCount failed.")
        }
    }

    fun onFileSelected(uri: Uri, isFromRecent: Boolean = false, isExternalIntent: Boolean = false) {
        if (isFromRecent) {
            Timber.i("Opening recent file: $uri")
            viewModelScope.launch {
                val item = recentFilesRepository.getFileByUri(uri.toString())
                if (item != null) {
                    openBook(uri, item.bookId, item.type, item.displayName)
                } else {
                    _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_recent_item_not_found)) }
                }
            }
        } else {
            Timber.i("Importing new file: $uri")
            importExternalFile(uri, isExternalIntent)
        }
    }

    fun openTtsNotificationTarget(
        bookId: String,
        sourceCfi: String?,
        startOffset: Int?,
        chapterIndex: Int?,
        pageIndex: Int?
    ) {
        viewModelScope.launch {
            val item = recentFilesRepository.getFileByBookId(bookId)
            if (item == null) {
                _internalState.update {
                    it.copy(errorMessage = appContext.getString(R.string.error_recent_item_not_found))
                }
                return@launch
            }

            val uri = item.getUri()
            if (uri == null) {
                _internalState.update {
                    it.copy(errorMessage = appContext.getString(R.string.error_file_location_not_found))
                }
                return@launch
            }

            val initialLocator = chapterIndex?.let {
                Locator(
                    chapterIndex = it,
                    blockIndex = 0,
                    charOffset = startOffset ?: 0
                )
            }

            openBook(
                uri = uri,
                bookId = item.bookId,
                type = item.type,
                originalDisplayName = item.displayName,
                initialPageOverride = pageIndex,
                isInitialPageExplicit = pageIndex != null,
                initialLocatorOverride = initialLocator,
                initialCfiOverride = sourceCfi?.takeIf { it.isNotBlank() },
                preserveTtsOnOpen = true
            )
        }
    }

    private fun importExternalFile(externalUri: Uri, isExternalIntent: Boolean = false) {
        _internalState.update {
            it.copy(isLoading = true, errorMessage = null, contextualActionItems = emptySet())
        }

        viewModelScope.launch {
            try {
                val importResult = prepareBookForImport(externalUri)

                if (importResult != null) {
                    val (internalUri, bookId, type) = importResult
                    if (isExternalIntent) {
                        externalOpenedBookId = bookId
                        if (prefs.getString(KEY_EXTERNAL_FILE_BEHAVIOR, "ASK") == "DELETE") {
                            markPendingExternalFileRemoval(bookId, internalUri.toString())
                        }
                    }
                    val displayName = getFileNameFromUri(externalUri, appContext) ?: "Unknown File"
                    openBook(
                        internalUri, bookId = bookId, type = type,
                        originalDisplayName = displayName, bundleResult = importResult.bundleResult
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
                    val messageRes = if (getFileTypeFromUri(externalUri, appContext) == null) {
                        R.string.error_unsupported_file_type
                    } else {
                        R.string.error_import_file_failed
                    }
                    _internalState.update {
                        it.copy(isLoading = false, errorMessage = appContext.getString(messageRes))
                    }
                }
            } catch (e: SecurityException) {
                Timber.e(e, "Permission denied while importing URI: $externalUri")
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_import_file_failed))
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Provider rejected URI import for: $externalUri")
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_import_file_failed))
                }
            } catch (e: RuntimeException) {
                Timber.e(e, "Unexpected import failure for URI: $externalUri")
                _internalState.update {
                    it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_import_file_failed))
                }
            }
        }
    }

    fun saveHighlights(bookId: String, highlightsJson: String) {
        viewModelScope.launch {
            val currentBookUri = _internalState.value.selectedPdfUri ?: _internalState.value.selectedEpubUri
            if (currentBookUri != null) {
                recentFilesRepository.getFileByUri(currentBookUri.toString())?.let { item ->
                    if (annotationJsonEquivalentForNoop(item.highlightsJson, highlightsJson)) {
                        logCloudSyncTrace {
                            "android.reader.highlights_save_noop book=${item.bookId} highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                        }
                        return@launch
                    }
                    logCloudSyncTrace {
                        "android.reader.highlights_save book=${item.bookId} highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                    }
                    recentFilesRepository.updateHighlights(item.bookId, highlightsJson)
                }
            } else if (bookId.isNotBlank()) {
                val existing = recentFilesRepository.getFileByBookId(bookId)
                if (annotationJsonEquivalentForNoop(existing?.highlightsJson, highlightsJson)) {
                    logCloudSyncTrace {
                        "android.reader.highlights_save_noop book=$bookId highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                    }
                    return@launch
                }
                logCloudSyncTrace {
                    "android.reader.highlights_save book=$bookId highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                }
                recentFilesRepository.updateHighlights(bookId, highlightsJson)
            }
        }
    }

    private val _reflowWorkInfo = MutableStateFlow<WorkInfo?>(null)
    val reflowWorkInfo: StateFlow<WorkInfo?> = _reflowWorkInfo.asStateFlow()

    fun switchToFileSeamlessly(item: RecentFileItem, syncPosition: Int) {
        viewModelScope.launch {
            Timber.tag("FileSwitch")
                .d("Starting seamless switch to ${item.bookId}, position: $syncPosition")

            val stateUpdateDeferred = CompletableDeferred<Boolean>()
            pendingSwitchDeferred = stateUpdateDeferred

            _internalState.update { it.copy(isLoading = true, errorMessage = null) }

            val uri = item.getUri() ?: run {
                _internalState.update {
                    it.copy(
                        isLoading = false, errorMessage = appContext.getString(R.string.error_file_location_not_found)
                    )
                }
                stateUpdateDeferred.complete(false)
                pendingSwitchDeferred = null
                return@launch
            }

            val type = item.type
            val bookId = item.bookId

            if (type in PDF_VIEWER_FILE_TYPES) {
                persistReaderSession(bookId, type)
                _internalState.update {
                    it.copy(
                        selectedEpubUri = null,
                        selectedEpubBook = null,
                        selectedFileType = type,
                        selectedBookId = bookId,
                        selectedPdfUri = uri,
                        initialPageInBook = syncPosition,
                        initialPageInBookIsExplicit = true,
                        isOpeningFromTtsNotification = false,
                        initialBookmarksJson = item.bookmarksJson,
                        isLoading = false
                    )
                }

                delay(50)

                addFileToRecent(
                    uri,
                    type,
                    bookId,
                    customDisplayName = item.displayName,
                    isRecent = true,
                    sourceFolderUri = null
                )

                Timber.tag("FileSwitch").d("PDF state updated, emitting navigation event")
                _navigationEvent.send(NavigationEvent("pdf_viewer", bookId, uri))
                stateUpdateDeferred.complete(true)

            } else if (type in EPUB_READER_FILE_TYPES) {
                persistReaderSession(bookId, type)
                try {
                    val epubBook = restoreEpubReaderBook(type, bookId, item.displayName, uri)
                    _internalState.update {
                        it.copy(
                            selectedPdfUri = null,
                            selectedFileType = type,
                            selectedBookId = bookId,
                            selectedEpubUri = uri,
                            selectedEpubBook = epubBook,
                            initialLocator = Locator(
                                chapterIndex = syncPosition, blockIndex = 0, charOffset = 0
                            ),
                            initialCfi = null,
                            initialBookmarksJson = item.bookmarksJson,
                            initialHighlightsJson = item.highlightsJson,
                            isLoading = false
                        )
                    }

                    delay(50)

                    addFileToRecent(
                        uri,
                        type,
                        bookId,
                        epubBook,
                        item.displayName,
                        isRecent = true,
                        sourceFolderUri = null
                    )

                    Timber.tag("FileSwitch").d("EPUB state updated, emitting navigation event")
                    _navigationEvent.send(NavigationEvent("epub_reader", bookId, uri))
                    stateUpdateDeferred.complete(true)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to switch seamlessly to $type book: $bookId")
                    _internalState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = appContext.getString(R.string.error_load_file, e.message),
                            selectedFileType = null
                        )
                    }
                    stateUpdateDeferred.complete(false)
                }
            } else {
                _internalState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = appContext.getString(R.string.error_unsupported_file_type),
                        selectedFileType = null
                    )
                }
                stateUpdateDeferred.complete(false)
            }
        }
    }

    fun generateAndImportReflowFile(
        pdfBookId: String,
        pdfUri: Uri,
        originalTitle: String,
        autoOpenPage: Int? = null
    ) {
        Timber.tag("PdfToMdPerf")
            .d("generateAndImportReflowFile START | pdfBookId=$pdfBookId | pdfUri=$pdfUri")
        val reflowBookId = "${pdfBookId}_reflow"

        viewModelScope.launch {
            val existing = recentFilesRepository.getFileByBookId(reflowBookId)
            if (existing != null) {
                if (autoOpenPage != null) {
                    switchToFileSeamlessly(existing, autoOpenPage)
                } else {
                    onRecentFileClicked(existing)
                }
                return@launch
            }

            val workManager = WorkManager.getInstance(appContext)

            val inputData =
                androidx.work.Data.Builder().putString(ReflowWorker.KEY_BOOK_ID, pdfBookId)
                    .putString(ReflowWorker.KEY_PDF_URI, pdfUri.toString())
                    .putString(ReflowWorker.KEY_ORIGINAL_TITLE, originalTitle).build()

            val request = OneTimeWorkRequestBuilder<ReflowWorker>().setInputData(inputData)
                .addTag(ReflowWorker.WORK_NAME).addTag("book_$pdfBookId").build()

            workManager.enqueueUniqueWork(
                "reflow_$pdfBookId", ExistingWorkPolicy.KEEP, request
            )

            val finalWorkInfo = CompletableDeferred<WorkInfo>()

            launch {
                workManager.getWorkInfoByIdFlow(request.id).filterNotNull().first { workInfo ->
                    _reflowWorkInfo.value = workInfo
                    if (workInfo.state.isFinished) {
                        finalWorkInfo.complete(workInfo)
                        workManager.pruneWork()
                    }
                    workInfo.state.isFinished
                }
            }

            if (autoOpenPage != null) {
                launch {
                    importMutex.withLock {
                        val finalInfo = finalWorkInfo.await()

                        if (finalInfo.state == WorkInfo.State.SUCCEEDED) {
                            var retries = 0
                            var newItem = recentFilesRepository.getFileByBookId(reflowBookId)
                            while (newItem == null && retries < 10) {
                                delay(200)
                                newItem = recentFilesRepository.getFileByBookId(reflowBookId)
                                retries++
                            }
                            if (newItem != null) {
                                switchToFileSeamlessly(newItem, autoOpenPage)
                            } else {
                                showBanner(appContext.getString(R.string.error_load_generated_text_view), true)
                            }
                        } else {
                            showBanner(appContext.getString(R.string.error_text_view_generation_failed), true)
                        }
                    }
                }
            }
        }
    }

    private suspend fun cleanupBookDataLocally(bookId: String) {
        pdfTextRepository.clearBookText(bookId)
        clearImportedFileCache(bookId)
        bookCacheDao.deleteEntireBookCache(bookId)
    }

    private fun clearImportedFileCache(bookId: String) {
        try {
            ImportedFileCache.clearBookCache(appContext, bookId)
            Timber.tag("FileCleanup").d("Deleted imported cache for $bookId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear imported file cache for $bookId")
        }
    }

    private fun epubSourceFingerprint(uri: Uri): String? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.isFile) return null
                "${file.length()}:${file.lastModified()}"
            } else {
                val document = DocumentFile.fromSingleUri(appContext, uri) ?: return null
                val length = document.length()
                val modified = document.lastModified()
                if (length <= 0L && modified <= 0L) null else "$length:$modified"
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to compute EPUB source fingerprint for $uri")
            null
        }
    }

    private fun openBook(
        uri: Uri,
        bookId: String,
        type: FileType,
        originalDisplayName: String? = null,
        suppressNavigation: Boolean = false,
        bundleResult: CalibreBundleResult? = null,
        initialPageOverride: Int? = null,
        isInitialPageExplicit: Boolean = false,
        initialLocatorOverride: Locator? = null,
        initialCfiOverride: String? = null,
        preserveTtsOnOpen: Boolean = false
    ) {
        val openBookStartTime = System.currentTimeMillis()
        ReaderPerfLog.d("FileOpen start bookId=$bookId type=$type")
        Timber.tag("FileOpenPerf")
            .d("[$bookId] openBook START | type=$type | displayName=$originalDisplayName")

        val currentTabState = _internalState.value
        if (currentTabState.isTabsEnabled && type == FileType.PDF) {
            if (bookId !in currentTabState.openTabIds) {
                if (currentTabState.openTabIds.size >= 20) {
                    viewModelScope.launch(Dispatchers.Main) {
                        showBanner("Maximum of 20 tabs allowed. Please close a tab first.", isError = true)
                    }
                    return
                }
            }
            val tabState = AndroidSharedStateBridge.openBookTab(
                current = currentTabState,
                projectedState = uiState.value,
                bookId = bookId
            )
            persistTabState(tabState.openTabIds, tabState.activeTabBookId)
            _internalState.update {
                it.copy(
                    isTabsEnabled = tabState.isTabsEnabled,
                    openTabIds = tabState.openTabIds,
                    activeTabBookId = tabState.activeTabBookId
                )
            }
        }

        if (uri.scheme != "opds-pse") {
            try {
                if (uri.scheme == "file") {
                    uri.path?.let {
                        val file = File(it)
                        val size = file.length()
                        val name = file.name
                        Timber.tag("FileOpenPerf").d("[$bookId] File details | name=$name | size=${size} bytes | sizeMB=${size / (1024.0 * 1024)}")
                    }
                } else {
                    val cursor = appContext.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val size = if (sizeIndex != -1) it.getLong(sizeIndex) else -1L
                            val name = if (nameIndex != -1) it.getString(nameIndex) else "unknown"
                            Timber.tag("FileOpenPerf")
                                .d("[$bookId] File details | name=$name | size=${size} bytes | sizeMB=${size / (1024.0 * 1024)}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("FileOpenPerf").e(e, "[$bookId] Failed to get file details")
            }
        }

        viewModelScope.launch {
            _internalState.update {
                it.copy(
                    selectedPdfUri = null,
                    selectedEpubUri = null,
                    selectedBookId = bookId,
                    selectedEpubBook = null,
                    selectedFileType = type,
                    isLoading = true,
                    errorMessage = null,
                    initialLocator = initialLocatorOverride,
                    initialCfi = initialCfiOverride,
                    initialPageInBook = initialPageOverride,
                    initialPageInBookIsExplicit = isInitialPageExplicit,
                    isOpeningFromTtsNotification = preserveTtsOnOpen
                )
            }

            if (type in PDF_VIEWER_FILE_TYPES) {
                viewModelScope.launch {
                    val recentItem = recentFilesRepository.getFileByBookId(bookId)

                    Timber.tag("FileOpenPerf")
                        .d("[$bookId] Branch: PDF | elapsed=${System.currentTimeMillis() - openBookStartTime}ms")
                    _internalState.update {
                        it.copy(
                            selectedPdfUri = uri,
                            initialPageInBook = initialPageOverride ?: recentItem?.lastPage,
                            initialPageInBookIsExplicit = isInitialPageExplicit,
                            isOpeningFromTtsNotification = preserveTtsOnOpen,
                            initialBookmarksJson = recentItem?.bookmarksJson,
                            isLoading = false
                        )
                    }
                    ReaderPerfLog.d("FileOpen ready bookId=$bookId type=$type elapsed=${System.currentTimeMillis() - openBookStartTime}ms")
                    persistReaderSession(bookId, type)
                    addFileToRecent(
                        uri,
                        type,
                        bookId,
                        customDisplayName = originalDisplayName,
                        isRecent = true,
                        sourceFolderUri = null,
                        bundleResult = bundleResult
                    )

                    if (!suppressNavigation) {
                        Timber.tag("FileSwitch").d("PDF state updated, emitting navigation event")
                        _navigationEvent.send(NavigationEvent("pdf_viewer", bookId, uri))
                    } else {
                        Timber.tag("FileSwitch").d("PDF state updated, suppressing navigation event for smooth transition")
                    }
                }
            } else if (type in EPUB_READER_FILE_TYPES) {
                viewModelScope.launch {
                    val recentItem = recentFilesRepository.getFileByBookId(bookId)
                    Timber.tag("FileOpenPerf")
                        .d("[$bookId] Branch: ${type.name} | elapsed=${System.currentTimeMillis() - openBookStartTime}ms")
                    val locator = initialLocatorOverride
                        ?: if (recentItem?.lastChapterIndex != null && recentItem.locatorBlockIndex != null && recentItem.locatorCharOffset != null) {
                            Locator(
                                chapterIndex = recentItem.lastChapterIndex,
                                blockIndex = recentItem.locatorBlockIndex,
                                charOffset = recentItem.locatorCharOffset
                            )
                        } else {
                            null
                        }
                    logCloudSyncTrace {
                        "android.reader.open_epub_position book=$bookId " +
                            "overrideLocator=$initialLocatorOverride overrideCfi=${initialCfiOverride.cloudSyncPreview()} " +
                            (recentItem?.cloudSyncTraceSummary("recent") ?: "recent=null") +
                            " chosenLocator=$locator chosenCfi=${(initialCfiOverride ?: recentItem?.lastPositionCfi).cloudSyncPreview()}"
                    }

                    _internalState.update {
                        it.copy(
                            selectedEpubUri = uri,
                            initialLocator = locator,
                            initialCfi = initialCfiOverride ?: recentItem?.lastPositionCfi,
                            initialBookmarksJson = recentItem?.bookmarksJson,
                            initialHighlightsJson = recentItem?.highlightsJson,
                        )
                    }
                    ReaderPerfLog.d("FileOpen ready bookId=$bookId type=$type elapsed=${System.currentTimeMillis() - openBookStartTime}ms")
                    persistReaderSession(bookId, type)

                    if (!suppressNavigation) {
                        Timber.tag("FileSwitch").d("EPUB state updated, emitting navigation event")
                        _navigationEvent.send(NavigationEvent("epub_reader", bookId, uri))
                    }

                    when (type) {
                        FileType.EPUB -> {
                            loadEpub(uri, bookId, customDisplayName = originalDisplayName, bundleResult = bundleResult)
                        }

                        FileType.MOBI -> {
                            loadMobi(uri, bookId, customDisplayName = originalDisplayName, bundleResult = bundleResult)
                        }

                        FileType.FB2 -> {
                            loadFb2(uri, bookId, customDisplayName = originalDisplayName, bundleResult = bundleResult)
                        }
                        FileType.ODT, FileType.FODT -> {
                            loadOdt(uri, bookId, type == FileType.FODT, customDisplayName = originalDisplayName, bundleResult = bundleResult)
                        }
                        else -> {
                            loadSingleFile(
                                uri, bookId, type, customDisplayName = originalDisplayName, bundleResult = bundleResult
                            )
                        }
                    }
                }
            } else {
                _internalState.update {
                    it.copy(
                        selectedPdfUri = null,
                        selectedEpubUri = null,
                        selectedEpubBook = null,
                        selectedFileType = null,
                        selectedBookId = null,
                        isLoading = false,
                        errorMessage = appContext.getString(R.string.error_unsupported_file_type),
                        initialPageInBookIsExplicit = false,
                        isOpeningFromTtsNotification = false
                    )
                }
            }
        }
    }

    private fun loadFb2(uri: Uri, bookId: String, customDisplayName: String? = null, bundleResult: CalibreBundleResult? = null) {
        val loadStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[$bookId] loadFb2 START")
        viewModelScope.launch {
            if (!_internalState.value.isLoading) {
                _internalState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            Timber.d("Starting FB2 parsing for URI: $uri")
            try {
                val fb2Book = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) throw Exception("Could not open input stream")
                        fb2Parser.createFb2Book(
                            inputStream = inputStream,
                            bookId = bookId,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(uri, appContext) ?: "unknown.fb2"
                        )
                    }
                }
                Timber.i("FB2 parsing successful. Title: ${fb2Book.title}")
                Timber.tag("FileOpenPerf").d("[$bookId] loadFb2 completed | chapters=${fb2Book.chapters.size} | elapsed=${System.currentTimeMillis() - loadStart}ms")

                addFileToRecent(
                    uri, FileType.FB2, bookId, fb2Book, customDisplayName, isRecent = true, sourceFolderUri = null, bundleResult = bundleResult
                )

                _internalState.update { it.copy(selectedEpubBook = fb2Book, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing FB2 for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = appContext.getString(R.string.error_load_fb2, e.message), isLoading = false)
                }
            }
        }
    }

    private fun loadOdt(uri: Uri, bookId: String, isFlat: Boolean, customDisplayName: String? = null, bundleResult: CalibreBundleResult? = null) {
        val loadStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[$bookId] loadOdt START | isFlat=$isFlat")
        viewModelScope.launch {
            if (!_internalState.value.isLoading) {
                _internalState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            Timber.d("Starting ODT parsing for URI: $uri")
            try {
                val odtBook = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) throw Exception("Could not open input stream")
                        odtParser.createOdtBook(
                            inputStream = inputStream,
                            bookId = bookId,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(uri, appContext) ?: if (isFlat) "unknown.fodt" else "unknown.odt",
                            isFlat = isFlat
                        )
                    }
                }
                Timber.i("ODT parsing successful. Title: ${odtBook.title}")
                Timber.tag("FileOpenPerf").d("[$bookId] loadOdt completed | chapters=${odtBook.chapters.size} | elapsed=${System.currentTimeMillis() - loadStart}ms")

                addFileToRecent(
                    uri, if (isFlat) FileType.FODT else FileType.ODT, bookId, odtBook, customDisplayName, isRecent = true, sourceFolderUri = null, bundleResult = bundleResult
                )

                _internalState.update { it.copy(selectedEpubBook = odtBook, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing ODT for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = appContext.getString(R.string.error_load_file, e.message), isLoading = false)
                }
            }
        }
    }

    private fun loadSingleFile(
        uri: Uri,
        bookId: String,
        type: FileType,
        customDisplayName: String? = null,
        bundleResult: CalibreBundleResult? = null
    ) {
        val loadStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[$bookId] loadSingleFile START | type=$type")
        viewModelScope.launch {
            if (type !in EPUB_READER_FILE_TYPES) {
                _internalState.update {
                    it.copy(
                        errorMessage = appContext.getString(R.string.error_unsupported_file_type),
                        isLoading = false
                    )
                }
                return@launch
            }
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
                        singleFileImporter.importSingleFile(
                            inputStream,
                            type,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(
                                uri, appContext
                            ) ?: "unknown_doc",
                            bookId = bookId
                        )
                    }
                }

                Timber.tag("FileOpenPerf")
                    .d("[$bookId] loadSingleFile: importSingleFile completed | chapters=${epubBook.chapters.size} | elapsed=${System.currentTimeMillis() - loadStart}ms")
                Timber.i("Import successful ($type). Title: ${epubBook.title}")
                addFileToRecent(
                    uri,
                    type,
                    bookId,
                    epubBook,
                    customDisplayName,
                    isRecent = true,
                    sourceFolderUri = null,
                    bundleResult = bundleResult
                )

                _internalState.update { it.copy(selectedEpubBook = epubBook, isLoading = false) }
                Timber.tag("FileOpenPerf")
                    .d("[$bookId] loadSingleFile COMPLETE | totalElapsed=${System.currentTimeMillis() - loadStart}ms")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing file ($type) for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = appContext.getString(R.string.error_load_file, e.message), isLoading = false)
                }
            }
        }
    }

    fun setRenderMode(newMode: RenderMode) {
        _internalState.update { it.copy(renderMode = newMode) }
        prefs.edit { putString(KEY_RENDER_MODE, newMode.name) }
    }

    private fun getFileTypeFromUri(uri: Uri, context: Context): FileType? {
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (e: SecurityException) {
            Timber.w(e, "Permission denied while resolving MIME type for URI: $uri")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Provider rejected MIME type lookup for URI: $uri")
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Unexpected failure while resolving MIME type for URI: $uri")
            null
        }
        val fileName = getFileNameFromUri(uri, context)

        Timber.d("Determining type for: $uri | Mime: $mimeType | Name: $fileName")

        return resolveFileTypeFromMetadata(fileName, mimeType)
    }

    private fun loadMobi(uri: Uri, bookId: String, customDisplayName: String? = null, bundleResult: CalibreBundleResult? = null) {
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
                            inputStream = inputStream,
                            bookId = bookId,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(uri, appContext) ?: "unknown.mobi"
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
                        sourceFolderUri = null,
                        bundleResult = bundleResult
                    )
                    _internalState.update {
                        it.copy(selectedEpubBook = mobiAsEpubBook, isLoading = false)
                    }
                } else {
                    throw Exception(
                        if (MobiParser.isNativeParserAvailable) {
                            "MobiParser returned null. The file might be DRM-protected or invalid."
                        } else {
                            MobiParser.nativeParserUnavailableMessage()
                        }
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing MOBI for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = appContext.getString(R.string.error_load_mobi, e.message), isLoading = false)
                }
            }
        }
    }

    private fun loadEpub(uri: Uri, bookId: String, customDisplayName: String? = null, bundleResult: CalibreBundleResult? = null) {
        val loadStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[$bookId] loadEpub START")
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
                            inputStream = inputStream,
                            bookId = bookId,
                            originalBookNameHint = customDisplayName ?: getFileNameFromUri(uri, appContext) ?: "unknown.epub",
                            sourceFingerprint = epubSourceFingerprint(uri)
                        )
                    }
                }
                Timber.i("EPUB parsing successful. Title: ${epubBook.title}")
                Timber.tag("FileOpenPerf")
                    .d("[$bookId] loadEpub: createEpubBook completed | chapters=${epubBook.chapters.size} | elapsed=${System.currentTimeMillis() - loadStart}ms")

                addFileToRecent(
                    uri,
                    FileType.EPUB,
                    bookId,
                    epubBook,
                    customDisplayName,
                    isRecent = true,
                    sourceFolderUri = null,
                    bundleResult = bundleResult
                )

                _internalState.update { it.copy(selectedEpubBook = epubBook, isLoading = false) }
                Timber.tag("FileOpenPerf")
                    .d("[$bookId] loadEpub COMPLETE | totalElapsed=${System.currentTimeMillis() - loadStart}ms")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing EPUB for URI: $uri")
                _internalState.update {
                    it.copy(errorMessage = appContext.getString(R.string.error_load_epub, e.message), isLoading = false)
                }
            }
        }
    }

    fun saveEpubReadingPosition(
        uri: Uri, locator: Locator, cfiForWebView: String?, progress: Float
    ) {
        Timber.d("Saving EPUB position locally: URI=$uri, Locator=$locator")
        viewModelScope.launch {
            recentFilesRepository.getFileByUri(uri.toString())?.let { existing ->
                logCloudSyncTrace {
                    "android.reader.position_save_start book=${existing.bookId} beforeTs=${existing.lastModifiedTimestamp} " +
                        "locator={chapter=${locator.chapterIndex} block=${locator.blockIndex} char=${locator.charOffset}} " +
                        "progress=$progress cfi=${cfiForWebView.cloudSyncPreview()}"
                }
                recentFilesRepository.updateEpubReadingPosition(
                    uriString = uri.toString(),
                    locator = locator,
                    cfiForWebView = cfiForWebView,
                    progress = progress
                )
                val updated = recentFilesRepository.getFileByBookId(existing.bookId)
                logCloudSyncTrace {
                    "android.reader.position_save_done beforeTs=${existing.lastModifiedTimestamp} " +
                        (updated?.cloudSyncTraceSummary("after") ?: "after=null")
                }
                queueCloudMetadataUpload(existing.bookId, reason = "epub_position")
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
            Timber.tag("PdfPositionDebug").i("ViewModel: Save request triggered | Page: $page | Total: $totalPages | Progress: $progress | URI: ${currentPdfUri.lastPathSegment}")
            viewModelScope.launch {
                recentFilesRepository.getFileByUri(currentPdfUri.toString())?.let { existing ->
                    logCloudSyncTrace {
                        "android.reader.pdf_position_save_start book=${existing.bookId} beforeTs=${existing.lastModifiedTimestamp} " +
                            "beforeReadTs=${existing.effectiveReadingPositionModifiedTimestamp()} page=$page progress=$progress"
                    }
                    recentFilesRepository.updatePdfReadingPosition(
                        uriString = currentPdfUri.toString(), page = page, progress = progress
                    )
                    val updated = recentFilesRepository.getFileByBookId(existing.bookId)
                    logCloudSyncTrace {
                        "android.reader.pdf_position_save_done beforeTs=${existing.lastModifiedTimestamp} " +
                            (updated?.cloudSyncTraceSummary("after") ?: "after=null")
                    }
                    queueCloudMetadataUpload(existing.bookId, reason = "pdf_position")
                } ?: run {
                    Timber.tag("PdfPositionDebug").e("ViewModel: Save aborted. Could not resolve file item from URI in DB.")
                }
            }
        } else {
            Timber.tag("PdfPositionDebug").w("ViewModel: Save aborted. No selectedPdfUri found in state.")
        }
    }

    fun exportLogsToFile(activityContext: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("Generating logcat dump for debugging...")
                val logFile = File(appContext.cacheDir, "debug_logs_${System.currentTimeMillis()}.txt")

                val process = Runtime.getRuntime().exec("logcat -d -v threadtime -t 5000")
                process.inputStream.bufferedReader().use { reader ->
                    logFile.writeText(reader.readText())
                }

                val authority = "${appContext.packageName}.provider"
                val uri = androidx.core.content.FileProvider.getUriForFile(appContext, authority, logFile)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, "App Debug Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Export Debug Logs")
                if (activityContext !is android.app.Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    activityContext.startActivity(chooser)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export logs")
                withContext(Dispatchers.Main) {
                    showBanner("Failed to export logs", isError = true)
                }
            }
        }
    }

    fun refreshLibrary() {
        val syncEnabled = _internalState.value.isSyncEnabled
        val hasFolder = _internalState.value.syncedFolders.any { it.localSyncEnabled }

        if (!syncEnabled && !hasFolder) {
            Timber.d("Refresh skipped: No sync methods active.")
            _internalState.update { it.copy(isRefreshing = false) }
            return
        }

        viewModelScope.launch {
            _internalState.update { it.copy(isRefreshing = true) }

            try {
                if (syncEnabled) {
                    syncWithCloud(showBanner = false).join()
                }

                if (hasFolder) {
                    syncFolderMetadata(showFeedback = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Refresh failed")
                _internalState.update { it.copy(isRefreshing = false) }
            } finally {
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
        ReaderPerfLog.d("FileOpen click bookId=${item.bookId} name=${item.displayName}")
        val currentSelection = _internalState.value.contextualActionItems
        if (currentSelection.isNotEmpty()) {
            Timber.d("Toggling selection for: ${item.displayName}")
            _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.BookSelectionToggled(item.bookId)) }
            Timber.d("New selection size: ${_internalState.value.contextualActionItems.size}")
        } else {
            if (item.sourceFolderUri != null && item.uriString != null) {
                viewModelScope.launch {
                    val exists = try {
                        val uri = item.uriString.toUri()
                        DocumentFile.fromSingleUri(appContext, uri)?.exists() == true
                    } catch (_: Exception) {
                        false
                    }

                    if (!exists) {
                        Timber.tag("FolderSync")
                            .i("LazyCleanup: File ${item.displayName} missing. Removing.")
                        recentFilesRepository.deleteFilePermanently(listOf(item.bookId))
                        showBanner(appContext.getString(R.string.banner_file_deleted_from_folder))
                        return@launch
                    }

                    Timber.d("Recent file clicked (opening): ${item.displayName}")
                    if (item.isAvailable) {
                        item.getUri()?.let { uri ->
                            openBook(uri, item.bookId, item.type, item.displayName)
                        } ?: run {
                            _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_file_location_not_found)) }
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
                    _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_file_location_not_found)) }
                    return
                }
            } else {
                downloadBook(item, openWhenComplete = true)
            }
        }
    }

    private fun uploadNewBookAndMetadata(book: RecentFileItem) {
        if (!uiState.value.isSyncEnabled) {
            logCloudSyncTrace { "android.upload_content.skip reason=sync_disabled ${book.cloudSyncTraceSummary()}" }
            return
        }

        if (book.uriString?.startsWith("opds-pse") == true) {
            logCloudSyncTrace { "android.upload_content.skip reason=opds_stream ${book.cloudSyncTraceSummary()}" }
            Timber.d("Skipping book content sync for OPDS stream book: ${book.displayName}")
            return
        }

        if (book.sourceFolderUri != null) {
            logCloudSyncTrace { "android.upload_content.skip reason=folder_book ${book.cloudSyncTraceSummary()}" }
            Timber.d("Skipping book content sync for local folder book: ${book.displayName}")
            return
        }

        if (book.isManualOnlyReaderFile()) {
            logCloudSyncTrace { "android.upload_content.skip reason=manual_only ${book.cloudSyncTraceSummary()}" }
            Timber.d("Skipping book content sync for manual-only reader file: ${book.displayName}")
            return
        }

        viewModelScope.launch {
            _internalState.update { it.copy(uploadingBookIds = it.uploadingBookIds + book.bookId) }
            try {
                logCloudSyncTrace { "android.upload_content.start ${book.cloudSyncTraceSummary()}" }
                val accessToken = googleDriveRepository.getAccessToken(appContext) ?: run {
                    logCloudSyncTrace { "android.upload_content.skip reason=no_access_token ${book.cloudSyncTraceSummary()}" }
                    return@launch
                }

                book.getUri()?.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        logCloudSyncTrace { "android.upload_content.file book=${book.bookId} path=${path.cloudSyncPreview()} bytes=${file.length()}" }
                        Timber.d("Uploading newly added book content: ${book.displayName}")
                        val uploadedFile = googleDriveRepository.uploadFile(
                            accessToken, book.bookId, file, book.type
                        )
                        if (uploadedFile != null) {
                            logCloudSyncTrace { "android.upload_content.success book=${book.bookId} driveId=${uploadedFile.id}" }
                            Timber.d("Upload successful, now syncing metadata for ${book.bookId}")
                            val latestBookState = recentFilesRepository.getFileByBookId(book.bookId)
                            if (latestBookState != null) {
                                uploadSingleBookMetadata(latestBookState)
                            } else {
                                uploadSingleBookMetadata(book)
                            }
                        } else {
                            logCloudSyncTrace { "android.upload_content.failed_null book=${book.bookId}" }
                            Timber.e("Google Drive upload returned null for ${book.bookId}")
                        }
                    } else {
                        logCloudSyncTrace { "android.upload_content.skip reason=file_missing book=${book.bookId} path=${path.cloudSyncPreview()}" }
                        Timber.w("File for new book upload does not exist at path: $path")
                    }
                }
            } catch (e: Exception) {
                logCloudSyncError(e) { "android.upload_content.failed ${book.cloudSyncTraceSummary()}" }
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
            _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.BookSelectionToggled(item.bookId)) }
        }
        Timber.d("New selection size: ${_internalState.value.contextualActionItems.size}")
    }

    fun selectAllRecentFiles() {
        val projectedState = uiState.value
        val currentVisible = projectedState.recentFiles.filter { it.isRecent }
        _internalState.update { state ->
            AndroidSharedStateBridge.replaceBookSelectionWithVisibleBooks(
                current = state,
                projectedState = projectedState,
                visibleBooks = currentVisible
            )
        }
    }

    fun selectAllLibraryFiles() {
        val projectedState = uiState.value
        val currentVisible = projectedState.allRecentFiles
        _internalState.update { state ->
            AndroidSharedStateBridge.replaceBookSelectionWithVisibleBooks(
                current = state,
                projectedState = projectedState,
                visibleBooks = currentVisible
            )
        }
    }

    fun clearContextualAction() {
        Timber.d("Clearing contextual action mode.")
        if (_internalState.value.contextualActionItems.isNotEmpty()) {
            _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.SelectionCleared) }
        }
    }

    fun showCreateShelfDialog() {
        _internalState.update { it.copy(showCreateShelfDialog = true) }
    }

    fun handleExternalFilePrompt(bookId: String, keep: Boolean, dontAskAgain: Boolean) {
        if (dontAskAgain) {
            val newBehavior = if (keep) "KEEP" else "DELETE"
            setExternalFileBehavior(newBehavior)
        }
        if (keep) {
            clearPendingExternalFileRemovals(setOf(bookId))
        } else {
            deletePendingExternalFileRemoval(bookId, null)
        }
        _internalState.update { it.copy(showExternalFileSavePromptFor = null) }
    }
    fun setExternalFileBehavior(behavior: String) {
        prefs.edit { putString(KEY_EXTERNAL_FILE_BEHAVIOR, behavior) }
        _internalState.update { it.copy(externalFileBehavior = behavior) }
    }

    fun dismissCreateShelfDialog() {
        _internalState.update { it.copy(showCreateShelfDialog = false) }
    }

    fun createShelf(name: String) {
        val shelfId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val shelf = SharedLibraryEditor.createShelfRecord(name, shelfId)?.toShelfEntity(now) ?: return
        viewModelScope.launch {
            recentFilesRepository.addShelf(shelf)
            dismissCreateShelfDialog()
            syncShelfChangeToFirestore(shelfId)
        }
    }

    fun setMainScreenPage(page: Int) {
        val sanitizedPage = page.coerceIn(0, 1)
        if (_internalState.value.mainScreenStartPage == sanitizedPage) return
        _internalState.update { it.copy(mainScreenStartPage = sanitizedPage) }
        persistLibraryLandingState()
    }

    fun setLibraryScreenPage(page: Int) {
        val maxLibraryPage = if (BuildConfig.IS_OFFLINE) 2 else 3
        val sanitizedPage = page.coerceIn(0, maxLibraryPage)
        if (_internalState.value.libraryScreenStartPage == sanitizedPage) return
        _internalState.update {
            it.copy(libraryScreenStartPage = sanitizedPage)
        }
        persistLibraryLandingState()
    }

    fun navigateToShelf(id: String) {
        _internalState.update {
            it.copy(viewingShelfId = id, mainScreenStartPage = 1, libraryScreenStartPage = 1)
        }
        persistLibraryLandingState()
    }

    fun showRenameShelfDialog(shelfId: String) {
        _internalState.update { it.copy(showRenameShelfDialogFor = shelfId) }
    }

    fun dismissRenameShelfDialog() {
        _internalState.update { it.copy(showRenameShelfDialogFor = null) }
    }

    fun showDeleteShelfDialog(shelfId: String) {
        _internalState.update { it.copy(showDeleteShelfDialogFor = shelfId) }
    }

    fun dismissDeleteShelfDialog() {
        _internalState.update { it.copy(showDeleteShelfDialogFor = null) }
    }

    fun renameShelf(shelfId: String, newName: String) {
        val cleanName = SharedLibraryEditor.cleanShelfName(newName)
        if (!SharedLibraryEditor.canMutateShelf(shelfId) || cleanName == null) {
            dismissRenameShelfDialog()
            return
        }
        viewModelScope.launch {
            recentFilesRepository.renameShelf(shelfId, cleanName)
            syncShelfChangeToFirestore(shelfId)
            _internalState.update { it.copy(viewingShelfId = shelfId) }
            persistLibraryLandingState()
            dismissRenameShelfDialog()
        }
    }

    fun deleteShelf(shelfId: String) {
        if (!SharedLibraryEditor.canMutateShelf(shelfId)) {
            dismissDeleteShelfDialog()
            return
        }
        viewModelScope.launch {
            _internalState.update {
                it.copy(viewingShelfId = null, isAddingBooksToShelf = false, showDeleteShelfDialogFor = null)
            }
            persistLibraryLandingState()
            recentFilesRepository.deleteShelf(shelfId)
            syncShelfChangeToFirestore(shelfId)
        }
    }

    fun unselectShelf() {
        _internalState.update { it.copy(viewingShelfId = null, isAddingBooksToShelf = false) }
        persistLibraryLandingState()
    }

    fun navigateBackFromShelf() {
        val currentShelf = uiState.value.shelves.find { it.id == _internalState.value.viewingShelfId }
        val parentShelfId = currentShelf?.takeIf { it.type == ShelfType.FOLDER }?.parentShelfId
        if (parentShelfId != null) {
            _internalState.update { it.copy(viewingShelfId = parentShelfId, isAddingBooksToShelf = false) }
            persistLibraryLandingState()
        } else {
            unselectShelf()
        }
    }

    fun removeContextualItemsFromShelf() {
        val shelfId = _internalState.value.viewingShelfId
        if (!SharedLibraryEditor.canMutateShelf(shelfId)) {
            clearContextualAction()
            return
        }
        val targetShelfId = shelfId ?: return

        val bookIdsToRemove = SharedLibraryEditor.cleanBookIds(_internalState.value.contextualActionItems.map { it.bookId })
        if (bookIdsToRemove.isEmpty()) {
            clearContextualAction()
            return
        }

        viewModelScope.launch {
            recentFilesRepository.removeBooksFromShelf(targetShelfId, bookIdsToRemove.toList())
            clearContextualAction()
            syncShelfChangeToFirestore(targetShelfId)
        }
    }

    fun onShelfClick(shelf: Shelf) {
        if (_internalState.value.contextualActionShelfIds.isNotEmpty()) {
            toggleShelfSelection(shelf)
        } else {
            navigateToShelf(shelf.id)
        }
    }

    private fun toggleShelfSelection(shelf: Shelf) {
        if (shelf.type != ShelfType.MANUAL) return

        _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.ShelfSelectionToggled(shelf.id)) }
    }

    fun onShelfLongPress(shelf: Shelf) {
        if (shelf.type != ShelfType.MANUAL || shelf.id == "unshelved") return
        val currentSelection = _internalState.value.contextualActionShelfIds
        if (shelf.id !in currentSelection) {
            _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.ShelfSelectionToggled(shelf.id)) }
        }
    }

    fun clearShelfContextualAction() {
        if (_internalState.value.contextualActionShelfIds.isNotEmpty()) {
            _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.ShelfSelectionCleared) }
        }
    }

    fun deleteSelectedShelves() {
        val shelvesToDelete = _internalState.value.contextualActionShelfIds
            .filterTo(mutableSetOf()) { SharedLibraryEditor.canMutateShelf(it) }
        if (shelvesToDelete.isEmpty()) {
            clearShelfContextualAction()
            return
        }

        viewModelScope.launch {
            shelvesToDelete.forEach { shelfId ->
                recentFilesRepository.deleteShelf(shelfId)
                syncShelfChangeToFirestore(shelfId)
            }
            clearShelfContextualAction()
        }
    }

    fun showAddBooksToShelf() {
        _internalState.update {
            it.copy(
                isAddingBooksToShelf = true,
                addBooksSource = AddBooksSource.UNSHELVED,
                booksSelectedForAdding = emptySet()
            )
        }
        persistLibraryLandingState()
    }

    private fun syncShelfChangeToFirestore(shelfId: String) {
        if (!uiState.value.isSyncEnabled) return
        val currentUser = uiState.value.currentUser ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val db = com.aryan.reader.data.AppDatabase.getDatabase(appContext)
            val shelf = db.shelfDao().getShelfById(shelfId) ?: return@launch
            val crossRefs = db.shelfDao().getCrossRefsForShelf(shelfId)
            val manualOnlyBookIds = recentFilesRepository.getAllFilesForSync()
                .filter { it.isManualOnlyReaderFile() }
                .mapTo(mutableSetOf()) { it.bookId }
            val bookIds = crossRefs.map { it.bookId }
                .filterNot { it in manualOnlyBookIds }

            val shelfMetadata = ShelfMetadata(
                name = shelf.name,
                bookIds = bookIds,
                isDeleted = shelf.isDeleted,
                lastModifiedTimestamp = shelf.updatedAt
            )

            val deviceId = getInstallationId()
            firestoreRepository.syncShelf(currentUser.uid, shelfMetadata, deviceId)
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
        persistLibraryLandingState()
    }

    fun addBooksToShelf(shelfId: String) {
        val bookIdsToAdd = SharedLibraryEditor.cleanBookIds(_internalState.value.booksSelectedForAdding)
        if (!SharedLibraryEditor.canMutateShelf(shelfId) || bookIdsToAdd.isEmpty()) {
            dismissAddBooksToShelf()
            return
        }
        viewModelScope.launch {
            recentFilesRepository.addBooksToShelf(shelfId, bookIdsToAdd.toList())
            syncShelfChangeToFirestore(shelfId)
            _internalState.update {
                it.copy(isAddingBooksToShelf = false, booksSelectedForAdding = emptySet())
            }
            persistLibraryLandingState()
        }
    }

    fun setAddBooksSource(source: AddBooksSource) {
        _internalState.update { it.copy(addBooksSource = source) }
        prefs.edit { putString(KEY_ADD_BOOKS_SOURCE, source.name) }
    }

    private fun persistLibraryLandingState() {
        val state = _internalState.value
        val resolvedUiState = uiState.value
        prefs.edit {
            putInt(KEY_MAIN_SCREEN_START_PAGE, state.mainScreenStartPage)
            putInt(KEY_LIBRARY_SCREEN_START_PAGE, state.libraryScreenStartPage)
            putString(KEY_LAST_VIEWING_SHELF_ID, resolvedUiState.viewingShelfId)
            putBoolean(KEY_LAST_ADDING_BOOKS_TO_SHELF, resolvedUiState.isAddingBooksToShelf)
        }
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
                val canSync =
                    uiState.value.isSyncEnabled && googleDriveRepository.hasDrivePermissions(
                        appContext
                    )

                val (folderBooks, managedBooks) = itemsToRemove.partition { it.sourceFolderUri != null }

                withContext(Dispatchers.IO) {
                    if (folderBooks.isNotEmpty()) {
                        Timber.d("Processing ${folderBooks.size} folder books for deletion.")

                        val idsToDeleteLocally = mutableListOf<String>()

                        folderBooks.forEach { item ->
                            idsToDeleteLocally.add(item.bookId)
                            cleanupBookDataLocally(item.bookId)

                            clearImportedFileCache(item.bookId)

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

                            if (item.sourceFolderUri != null) {
                                try {
                                    val rootUri = item.sourceFolderUri.toUri()
                                    val rootDoc = DocumentFile.fromTreeUri(appContext, rootUri)

                                    if (rootDoc != null) {
                                        val hiddenMeta = rootDoc.findFile(".${item.bookId}.json")
                                        val legacyVisibleMeta = rootDoc.findFile("${item.bookId}.json")

                                        hiddenMeta?.delete()
                                        legacyVisibleMeta?.delete()

                                        Timber.tag("FolderSync")
                                            .d("Deleted metadata for ${item.bookId} from root.")
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
                                    bannerMessage = BannerMessage(appContext.getString(R.string.banner_deleting_all_devices))
                                )
                            }
                            try {
                                val accessToken =
                                    googleDriveRepository.getAccessToken(appContext) ?: throw Exception(
                                        "No token"
                                    )
                                val deviceId = getInstallationId()

                                val remoteFiles = googleDriveRepository.getFiles(accessToken)?.files.orEmpty()
                                    .associateBy { it.name }

                                for (item in managedBooks) {
                                    if (item.isManualOnlyReaderFile()) {
                                        cleanupBookDataLocally(item.bookId)
                                        recentFilesRepository.deleteFilePermanently(listOf(item.bookId))
                                        continue
                                    }

                                    recentFilesRepository.markAsDeleted(listOf(item.bookId))
                                    cleanupBookDataLocally(item.bookId)

                                    firestoreRepository.syncBookMetadata(
                                        currentUser.uid,
                                        item.toBookMetadata().copy(isDeleted = true),
                                        deviceId
                                    )

                                    sharedCloudBookContentFileName(item.bookId, item.type)
                                        ?.let { fileName ->
                                            remoteFiles[fileName]?.id?.let { fileId ->
                                                Timber.d("Deleting from Drive: $fileName")
                                                googleDriveRepository.deleteDriveFile(accessToken, fileId)
                                            }
                                        }
                                    remoteFiles[cloudPdfAnnotationDriveFileName(item.bookId)]?.id?.let { fileId ->
                                        Timber.d("Deleting annotation bundle from Drive: ${item.bookId}")
                                        googleDriveRepository.deleteDriveFile(accessToken, fileId)
                                    }

                                    recentFilesRepository.deleteFilePermanently(listOf(item.bookId))
                                }

                                _internalState.update {
                                    it.copy(
                                        isLoading = false,
                                        bannerMessage = BannerMessage(appContext.getString(R.string.banner_deletion_complete))
                                    )
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error during permanent deletion")
                                recentFilesRepository.deleteFilePermanently(managedBooks.map { it.bookId })
                                managedBooks.forEach { item ->
                                    cleanupBookDataLocally(item.bookId)
                                }
                                _internalState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = appContext.getString(R.string.error_cloud_sync_failed_deleted_locally)
                                    )
                                }
                            }
                        } else {
                            recentFilesRepository.deleteFilePermanently(managedBooks.map { it.bookId })
                            managedBooks.forEach { item ->
                                cleanupBookDataLocally(item.bookId)
                            }
                        }
                    }
                }

                val totalRemoved = folderBooks.size + managedBooks.size
                _internalState.update {
                    it.copy(
                        isLoading = false,
                        bannerMessage = BannerMessage(appContext.resources.getQuantityString(R.plurals.banner_books_removed_library, totalRemoved, totalRemoved))
                    )
                }
            }
        } else {
            Timber.w("Attempted to remove contextual items, but none were selected.")
        }
    }

    fun navigateToFolderSync() {
        setMainScreenPage(1)
        setLibraryScreenPage(2)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        firestoreRepository.removeListener(feedbackListener)
        panelDetector?.close()
        panelDetector = null

        speechBubbleDetector?.close()
        speechBubbleDetector = null
        speechBubbleCache.clear()
        speechBubbleDetectionJobs.values.forEach { it.cancel() }
        speechBubbleDetectionJobs.clear()
        mlDispatcher.close()

        ttsController.release()

        Timber.d("ViewModel instance cleared (onCleared).")
    }

    suspend fun checkAndMigrateLegacyBookId(legacyId: String, newId: String) =
        withContext(Dispatchers.IO) {
            if (legacyId == newId) return@withContext
            Timber.tag("FolderAnnotationSync")
                .d("Checking migration from legacyId=$legacyId to newId=$newId")
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "vm.migrate.check legacyId=$legacyId newId=$newId"
            )

            try {
                fun safeMigrate(legacyFile: File?, newFile: File?, tag: String) {
                    if (legacyFile != null && legacyFile.exists()) {
                        if (newFile != null) {
                            if (newFile.exists()) {
                                val legacyTs = legacyFile.lastModified()
                                val newTs = newFile.lastModified()

                                if (newTs > legacyTs) {
                                    Timber.tag("FolderAnnotationSync")
                                        .i("Skipping migration for $tag: Destination ($newId) is newer than Legacy ($legacyId). Deleting legacy.")
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
                            Timber.tag("FolderAnnotationSync")
                                .w("Destination file for $tag is null. Skipping.")
                        }
                    }
                }

                fun layoutBlankScore(file: File?): Pair<Int, Int> {
                    if (file == null || !file.exists()) return 0 to 0
                    return try {
                        val array = JSONArray(file.readText())
                        var blankCount = 0
                        var manualBlankCount = 0
                        for (i in 0 until array.length()) {
                            val page = array.optJSONObject(i) ?: continue
                            if (page.optString("type") == "blank") {
                                blankCount++
                                if (page.optBoolean("manual", false)) manualBlankCount++
                            }
                        }
                        manualBlankCount to blankCount
                    } catch (e: Exception) {
                        Timber.tag("FolderAnnotationSync").w(e, "Unable to score layout for migration: ${file.name}")
                        0 to 0
                    }
                }

                fun safeMigrateLayout(legacyFile: File?, newFile: File?) {
                    Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                        "vm.migrate.layout.start legacyId=$legacyId newId=$newId " +
                            "legacyPath=${legacyFile?.absolutePath} legacyExists=${legacyFile?.exists()} " +
                            "legacyBytes=${legacyFile?.takeIf { it.exists() }?.length() ?: 0L} " +
                            "legacyMtime=${legacyFile?.takeIf { it.exists() }?.lastModified() ?: 0L} " +
                            "newPath=${newFile?.absolutePath} newExists=${newFile?.exists()} " +
                            "newBytes=${newFile?.takeIf { it.exists() }?.length() ?: 0L} " +
                            "newMtime=${newFile?.takeIf { it.exists() }?.lastModified() ?: 0L}"
                    )
                    if (legacyFile == null || !legacyFile.exists()) {
                        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                            "vm.migrate.layout.noLegacy legacyId=$legacyId newId=$newId"
                        )
                        return
                    }
                    if (newFile == null) {
                        Timber.tag("FolderAnnotationSync")
                            .w("Destination file for layout is null. Skipping.")
                        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                            "vm.migrate.layout.noDestination legacyId=$legacyId newId=$newId"
                        )
                        return
                    }

                    if (newFile.exists()) {
                        val legacyTs = legacyFile.lastModified()
                        val newTs = newFile.lastModified()
                        val legacyScore = layoutBlankScore(legacyFile)
                        val newScore = layoutBlankScore(newFile)
                        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                            "vm.migrate.layout.compare legacyId=$legacyId newId=$newId " +
                                "legacyScore=$legacyScore legacyTs=$legacyTs newScore=$newScore newTs=$newTs"
                        )
                        val shouldKeepExisting =
                            newScore.first > legacyScore.first ||
                                (newScore.first == legacyScore.first && newScore.second > legacyScore.second) ||
                                (newScore == legacyScore && newTs >= legacyTs)

                        if (shouldKeepExisting) {
                            Timber.tag("FolderAnnotationSync")
                                .i("Skipping layout migration: destination preserves newer or richer blank-page layout.")
                            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                                "vm.migrate.layout.keepExisting legacyId=$legacyId newId=$newId"
                            )
                            legacyFile.delete()
                            return
                        }

                        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                            "vm.migrate.layout.replaceExisting legacyId=$legacyId newId=$newId"
                        )
                        newFile.delete()
                    }

                    if (legacyFile.renameTo(newFile)) {
                        Timber.tag("FolderAnnotationSync").i("Migrated layout successfully.")
                        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                            "vm.migrate.layout.done legacyId=$legacyId newId=$newId " +
                                "newExists=${newFile.exists()} newBytes=${newFile.length()} newMtime=${newFile.lastModified()}"
                        )
                    } else {
                        Timber.tag("FolderAnnotationSync").w("Failed to rename layout file.")
                        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                            "vm.migrate.layout.renameFailed legacyId=$legacyId newId=$newId"
                        )
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
                safeMigrateLayout(
                    pageLayoutRepository.getLayoutFile(legacyId),
                    pageLayoutRepository.getLayoutFile(newId)
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

    fun clearReflowCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val reflowDir = File(appContext.cacheDir, "reflow_cache")
            if (reflowDir.exists()) {
                reflowDir.deleteRecursively()
            }
            val imagesDir = File(appContext.cacheDir, "reflow_images")
            if (imagesDir.exists()) {
                imagesDir.deleteRecursively()
            }

            val allFiles = recentFilesRepository.getAllFilesForSync()
            val reflowBooks = allFiles.filter { it.bookId.endsWith("_reflow") }

            if (reflowBooks.isNotEmpty()) {
                val reflowBookIds = reflowBooks.map { it.bookId }

                reflowBookIds.forEach { bookId ->
                    cleanupBookDataLocally(bookId)
                }

                recentFilesRepository.deleteFilePermanently(reflowBookIds)
            }

            withContext(Dispatchers.Main) {
                showBanner(appContext.getString(R.string.banner_reflow_cache_cleared))
            }
        }
    }

    fun updateCustomName(bookId: String, newName: String?) {
        viewModelScope.launch {
            val item = recentFilesRepository.getFileByBookId(bookId)
            if (item != null) {
                val updatedItem = item.copy(customName = newName, lastModifiedTimestamp = System.currentTimeMillis())
                recentFilesRepository.addRecentFile(updatedItem)

                if (uiState.value.isSyncEnabled) {
                    uploadSingleBookMetadata(updatedItem)
                }

                if (updatedItem.sourceFolderUri != null) {
                    launch(Dispatchers.IO) {
                        recentFilesRepository.syncLocalMetadataToFolder(bookId, force = true)
                    }
                }
            }
        }
    }

    fun updateBookMetadata(bookId: String, metadata: BookMetadataEdit) {
        viewModelScope.launch {
            val currentItem = recentFilesRepository.getFileByBookId(bookId) ?: return@launch
            if (currentItem.type != FileType.EPUB) {
                showBanner("Only EPUB files support embedded metadata editing right now.", isError = true)
                return@launch
            }

            val editResult = epubMetadataFileEditor.writeMetadata(currentItem, metadata)
            editResult.onFailure { error ->
                Timber.e(error, "Failed to update EPUB metadata for $bookId")
                showBanner("Could not update EPUB metadata.", isError = true)
            }.onSuccess { result ->
                cleanupBookDataLocally(bookId)
                val savedMetadata = BookMetadataEdit(
                    title = result.metadata.title ?: metadata.title,
                    author = result.metadata.author,
                    seriesName = result.metadata.seriesName,
                    seriesIndex = result.metadata.seriesIndex,
                    description = result.metadata.description
                )
                recentFilesRepository.updateUserEditableMetadata(
                    bookId = bookId,
                    metadata = savedMetadata,
                    fileSize = result.fileSize,
                    fileContentModifiedTimestamp = result.fileContentModifiedTimestamp
                )
                val updatedItem = recentFilesRepository.getFileByBookId(bookId)
                if (updatedItem != null && uiState.value.isSyncEnabled && updatedItem.sourceFolderUri == null) {
                    uploadNewBookAndMetadata(updatedItem)
                }
                showBanner("EPUB metadata updated.")
            }
        }
    }

    fun restoreOriginalBookMetadata(bookId: String) {
        viewModelScope.launch {
            val currentItem = recentFilesRepository.getFileByBookId(bookId) ?: return@launch
            if (currentItem.type != FileType.EPUB) {
                showBanner("Only EPUB files support embedded metadata restore right now.", isError = true)
                return@launch
            }
            val originalTitle = currentItem.originalTitle ?: currentItem.title
            if (originalTitle.isNullOrBlank() &&
                currentItem.originalAuthor.isNullOrBlank() &&
                currentItem.originalSeriesName.isNullOrBlank() &&
                currentItem.originalDescription.isNullOrBlank() &&
                currentItem.originalSeriesIndex == null
            ) {
                showBanner("No original EPUB metadata is available.", isError = true)
                return@launch
            }

            val metadata = BookMetadataEdit(
                title = originalTitle ?: currentItem.displayName.substringBeforeLast('.', currentItem.displayName),
                author = currentItem.originalAuthor,
                seriesName = currentItem.originalSeriesName,
                seriesIndex = currentItem.originalSeriesIndex,
                description = currentItem.originalDescription
            )
            val editResult = epubMetadataFileEditor.writeMetadata(currentItem, metadata)
            editResult.onFailure { error ->
                Timber.e(error, "Failed to restore EPUB metadata for $bookId")
                showBanner("Could not restore EPUB metadata.", isError = true)
            }.onSuccess { result ->
                cleanupBookDataLocally(bookId)
                recentFilesRepository.restoreOriginalMetadata(
                    bookId = bookId,
                    fileSize = result.fileSize,
                    fileContentModifiedTimestamp = result.fileContentModifiedTimestamp
                )
                val restoredItem = recentFilesRepository.getFileByBookId(bookId)
                if (restoredItem != null && uiState.value.isSyncEnabled && restoredItem.sourceFolderUri == null) {
                    uploadNewBookAndMetadata(restoredItem)
                }
                showBanner("Original EPUB metadata restored.")
            }
        }
    }

    fun closeAllTabs() {
        Timber.tag("PdfTabSync").i("ViewModel: closeAllTabs called")
        val tabState = AndroidSharedStateBridge.closeAllTabs(
            current = _internalState.value,
            projectedState = uiState.value
        )
        persistTabState(tabState.openTabIds, tabState.activeTabBookId)
        _internalState.update {
            it.copy(
                isTabsEnabled = tabState.isTabsEnabled,
                openTabIds = tabState.openTabIds,
                activeTabBookId = tabState.activeTabBookId
            )
        }
        clearSelectedFile()
    }

    fun setStrictFileFilter(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_STRICT_FILE_FILTER, enabled) }
        _internalState.update { it.copy(useStrictFileFilter = enabled) }
    }

    fun setUsePdfFileNameAsDisplayName(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_PDF_FILE_NAME_AS_DISPLAY_NAME, enabled) }
        _internalState.update { it.copy(usePdfFileNameAsDisplayName = enabled) }
    }

    fun setScreenCaptureProtectionEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SCREEN_CAPTURE_PROTECTION, enabled) }
        _internalState.update { it.copy(isScreenCaptureProtectionEnabled = enabled) }
    }

    private fun loadCustomAppThemes(prefs: SharedPreferences): List<CustomAppTheme> {
        val jsonString = prefs.getString(KEY_CUSTOM_APP_THEMES, "[]") ?: "[]"
        val themes = mutableListOf<CustomAppTheme>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                themes.add(
                    CustomAppTheme(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        seedColor = androidx.compose.ui.graphics.Color(obj.getInt("seedColor"))
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse custom app themes")
        }
        return themes
    }

    private fun loadAppFontPreference(prefs: SharedPreferences): AppFontPreference {
        val kind = try {
            AppFontPreferenceKind.valueOf(
                prefs.getString(KEY_APP_FONT_KIND, AppFontPreferenceKind.SYSTEM.name)
                    ?: AppFontPreferenceKind.SYSTEM.name
            )
        } catch (_: Exception) {
            AppFontPreferenceKind.SYSTEM
        }
        return AppFontPreference(
            kind = kind,
            customFontId = prefs.getString(KEY_APP_FONT_CUSTOM_ID, null)
        ).sanitized()
    }

    fun setAppFontPreference(preference: AppFontPreference) {
        val sanitized = preference.sanitized()
        _internalState.update { it.withSharedAppAction(SharedAppAction.AppFontPreferenceChanged(sanitized)) }
        prefs.edit {
            putString(KEY_APP_FONT_KIND, sanitized.kind.name)
            if (sanitized.customFontId == null) {
                remove(KEY_APP_FONT_CUSTOM_ID)
            } else {
                putString(KEY_APP_FONT_CUSTOM_ID, sanitized.customFontId)
            }
        }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        _internalState.update { it.withSharedAppAction(SharedAppAction.AppThemeChanged(mode)) }
        prefs.edit { putString(KEY_APP_THEME_MODE, mode.name) }
    }

    fun setAppContrastOption(option: AppContrastOption) {
        _internalState.update { it.withSharedAppAction(SharedAppAction.AppContrastChanged(option)) }
        prefs.edit { putString(KEY_APP_CONTRAST_OPTION, option.name) }
    }

    fun setAppTextDimFactorLight(factor: Float) {
        _internalState.update { it.withSharedAppAction(SharedAppAction.AppTextDimFactorLightChanged(factor)) }
        prefs.edit { putFloat(KEY_APP_TEXT_DIM_FACTOR_LIGHT, _internalState.value.appTextDimFactorLight) }
    }

    fun setAppTextDimFactorDark(factor: Float) {
        _internalState.update { it.withSharedAppAction(SharedAppAction.AppTextDimFactorDarkChanged(factor)) }
        prefs.edit { putFloat(KEY_APP_TEXT_DIM_FACTOR_DARK, _internalState.value.appTextDimFactorDark) }
    }

    fun setAppSeedColor(color: androidx.compose.ui.graphics.Color?) {
        _internalState.update { it.withSharedAppAction(SharedAppAction.AppSeedColorChanged(color)) }
        prefs.edit {
            if (color == null) {
                remove(KEY_APP_SEED_COLOR)
            } else {
                putInt(KEY_APP_SEED_COLOR, (color).toArgb())
            }
        }
    }

    fun addCustomAppTheme(theme: CustomAppTheme) {
        _internalState.update { it.withSharedAppAction(SharedAppAction.CustomAppThemeAdded(theme)) }
        val current = _internalState.value.customAppThemes
        saveCustomAppThemes(current)
        prefs.edit { putInt(KEY_APP_SEED_COLOR, theme.seedColor.toArgb()) }
    }

    fun deleteCustomAppTheme(themeId: String) {
        _internalState.update { it.withSharedAppAction(SharedAppAction.CustomAppThemeDeleted(themeId)) }
        val current = _internalState.value.customAppThemes
        saveCustomAppThemes(current)
        prefs.edit {
            val seed = _internalState.value.appSeedColor
            if (seed == null) {
                remove(KEY_APP_SEED_COLOR)
            } else {
                putInt(KEY_APP_SEED_COLOR, seed.toArgb())
            }
        }
    }

    private fun saveCustomAppThemes(themes: List<CustomAppTheme>) {
        val jsonArray = JSONArray()
        themes.forEach { theme ->
            val obj = JSONObject().apply {
                put("id", theme.id)
                put("name", theme.name)
                put("seedColor", (theme.seedColor).toArgb())
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString(KEY_CUSTOM_APP_THEMES, jsonArray.toString()) }
    }

    suspend fun getAuthToken(): String? {
        return authRepository.getIdToken()
    }

    fun testPanelDetection(context: Context) {
        viewModelScope.launch(mlDispatcher) {
            try {
                val modelFile = File(context.getExternalFilesDir(null), "best_float16.tflite")
                if (!modelFile.exists()) {
                    withContext(Dispatchers.Main) { showBanner("Model not found", isError = true) }
                    return@launch
                }

                val cbzItem = uiState.value.contextualActionItems.firstOrNull { it.type == FileType.CBZ }
                    ?: uiState.value.allRecentFiles.firstOrNull { it.type == FileType.CBZ }

                if (cbzItem == null) {
                    withContext(Dispatchers.Main) { showBanner("No CBZ found in Library.", isError = true) }
                    return@launch
                }

                val uri = cbzItem.getUri() ?: return@launch
                Timber.d("BATCH TEST START: ${cbzItem.displayName}")

                var cacheFile: File? = null
                try {
                    val detector = getOrInitDetector(context) ?: run {
                        withContext(Dispatchers.Main) { showBanner("Model could not be loaded", isError = true) }
                        return@launch
                    }
                    cacheFile = File(context.cacheDir, "temp_test_batch.cbz")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    // 1. Initialize Model Once
                    val initStartTime = System.currentTimeMillis()
                    val initDuration = System.currentTimeMillis() - initStartTime
                    Timber.d(">>> [BATCH] Model Initialization: ${initDuration}ms")

                    val archiveDoc = com.aryan.reader.pdf.ArchiveDocumentWrapper(cacheFile)
                    val totalPages = archiveDoc.getPageCount()

                    val startIndex = 3
                    val numPagesToTest = 10
                    val endIndex = minOf(startIndex + numPagesToTest - 1, totalPages - 1)

                    val resultsLog = StringBuilder()
                    resultsLog.append("Batch Results:\n")

                    // 2. Loop through pages
                    for (i in startIndex..endIndex) {
                        val page = archiveDoc.openPage(i)
                        if (page != null) {
                            val w = page.getPageWidthPoint()
                            val h = page.getPageHeightPoint()
                            if (w > 0 && h > 0) {
                                val bitmap = androidx.core.graphics.createBitmap(w, h)
                                page.renderPageBitmap(bitmap, 0, 0, w, h, false)

                                // Measure precise inference time
                                val pageStartTime = System.currentTimeMillis()
                                val panels = detector.detectPanels(bitmap)
                                val pageDuration = System.currentTimeMillis() - pageStartTime

                                val logLine = "Page $i: ${pageDuration}ms (Found ${panels.size} panels)"
                                Timber.d(">>>[BATCH] $logLine")
                                resultsLog.append("$logLine\n")
                                bitmap.recycle()
                                delay(20)
                            }
                            page.close()
                        }
                    }
                    archiveDoc.close()

                    withContext(Dispatchers.Main) {
                        Timber.i(resultsLog.toString())
                        showBanner("Batch test complete! Check logs for page-by-page timings.")
                    }

                } finally {
                    cacheFile?.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Batch test failed")
            }
        }
    }

    suspend fun detectComicPanels(bitmap: Bitmap, context: Context): List<android.graphics.RectF> {
        return withContext(mlDispatcher) {
            try {
                val detector = getOrInitDetector(context)
                detector?.detectPanels(bitmap) ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Error during panel detection")
                emptyList()
            }
        }
    }

    private suspend fun runSpeechBubbleDetection(
        bitmap: Bitmap,
        context: Context,
        confidenceThreshold: Float = 0.1f
    ): List<SpeechBubble> {
        return withContext(mlDispatcher) {
            try {
                val detector = getOrInitSpeechBubbleDetector(context)
                if (detector == null) {
                    Timber.tag("BubbleZoom").w("ViewModel: Detector is null!")
                    return@withContext emptyList()
                }
                detector.detectBubbles(bitmap, confidenceThreshold)
            } catch (e: Exception) {
                Timber.tag("BubbleZoom").e(e, "ViewModel: Error during speech bubble detection")
                emptyList()
            }
        }
    }

    suspend fun detectSpeechBubbles(bitmap: Bitmap, context: Context): List<SpeechBubble> {
        Timber.tag("BubbleZoom").d("ViewModel: detectSpeechBubbles called")
        val bubbles = runSpeechBubbleDetection(bitmap, context)
        Timber.tag("BubbleZoom").d("ViewModel: detectSpeechBubbles returning ${bubbles.size} bubbles")
        return bubbles
    }

    suspend fun detectSpeechBubblesCached(
        documentId: String,
        pageIndex: Int,
        bitmap: Bitmap,
        context: Context
    ): List<SpeechBubble> {
        val key = SpeechBubbleCacheKey(documentId = documentId, pageIndex = pageIndex)

        val cachedBeforeLock = speechBubbleCache[key]
        if (cachedBeforeLock != null) {
            return scaleCachedSpeechBubbles(cachedBeforeLock, bitmap.width, bitmap.height)
        }

        val detectionJob: Deferred<List<CachedSpeechBubble>>
        speechBubbleCacheMutex.withLock {
            val cachedInsideLock = speechBubbleCache[key]
            if (cachedInsideLock != null) {
                detectionJob = CompletableDeferred(cachedInsideLock)
            } else {
                detectionJob = speechBubbleDetectionJobs[key] ?: viewModelScope.async(mlDispatcher) {
                    val detected = runSpeechBubbleDetection(bitmap, context)
                    val normalized = normalizeSpeechBubbles(detected, bitmap.width, bitmap.height)
                    speechBubbleCache[key] = normalized
                    normalized
                }.also { job ->
                    speechBubbleDetectionJobs[key] = job
                }
            }
        }

        val cached = try {
            detectionJob.await()
        } finally {
            speechBubbleCacheMutex.withLock {
                val activeJob = speechBubbleDetectionJobs[key]
                if (activeJob === detectionJob && detectionJob.isCompleted) {
                    speechBubbleDetectionJobs.remove(key)
                }
            }
        }

        return scaleCachedSpeechBubbles(cached, bitmap.width, bitmap.height)
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
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_APP_OPEN_COUNT = "app_open_count"
        internal const val KEY_SYNCED_FOLDER_URI = "synced_folder_uri"
        internal const val KEY_LAST_FOLDER_SCAN_TIME = "last_folder_scan_time"
        private const val MAX_FOLDER_LIMIT = 10
        internal const val KEY_PINNED_HOME = "pinned_home_books"
        internal const val KEY_PINNED_LIBRARY = "pinned_library_books"
        private const val KEY_RECENT_FILES_LIMIT = "recent_files_limit"
        private const val KEY_TABS_ENABLED = "tabs_enabled"
        private const val KEY_OPEN_TAB_IDS = "open_tab_ids"
        private const val KEY_ACTIVE_TAB = "active_tab_book_id"
        private const val KEY_LAST_OPEN_BOOK_ID = "last_open_book_id"
        private const val KEY_LAST_OPEN_FILE_TYPE = "last_open_file_type"
        private const val KEY_EXTERNAL_FILE_BEHAVIOR = "external_file_behavior"
        private const val KEY_PENDING_EXTERNAL_FILE_REMOVALS = "pending_external_file_removals"
        private const val KEY_USE_STRICT_FILE_FILTER = "use_strict_file_filter"
        private const val KEY_USE_PDF_FILE_NAME_AS_DISPLAY_NAME = "use_pdf_file_name_as_display_name"
        private const val KEY_SCREEN_CAPTURE_PROTECTION = "screen_capture_protection_enabled"
        private const val KEY_APP_THEME_MODE = "app_theme_mode"
        private const val KEY_APP_CONTRAST_OPTION = "app_contrast_option"
        private const val KEY_APP_SEED_COLOR = "app_seed_color"
        private const val KEY_APP_TEXT_DIM_FACTOR = "app_text_dim_factor"
        private const val KEY_APP_TEXT_DIM_FACTOR_LIGHT = "app_text_dim_factor_light"
        private const val KEY_APP_TEXT_DIM_FACTOR_DARK = "app_text_dim_factor_dark"
        private const val KEY_APP_FONT_KIND = "app_font_kind"
        private const val KEY_APP_FONT_CUSTOM_ID = "app_font_custom_id"
        private const val KEY_CUSTOM_APP_THEMES = "custom_app_themes"

        val SUPPORTED_MIME_TYPES = SharedFileCapabilities.androidFilePickerMimeTypes.toTypedArray()
    }
}

private fun RecentFileItem.isManualOnlyReaderFile(): Boolean {
    return isManualOnlyReaderFileName(displayName)
}

private fun BookMetadata.isManualOnlyReaderFile(): Boolean {
    return isManualOnlyReaderFileName(displayName)
}

private fun RecentFileItem.withFreshLocalReadingPositionForCloudUpload(
    latestLocal: RecentFileItem?
): RecentFileItem {
    if (latestLocal == null || latestLocal.bookId != bookId) return this
    val latestReadingTimestamp = latestLocal.effectiveReadingPositionModifiedTimestamp()
    val currentReadingTimestamp = effectiveReadingPositionModifiedTimestamp()
    val shouldRefresh = latestLocal.lastModifiedTimestamp > lastModifiedTimestamp ||
        latestReadingTimestamp > currentReadingTimestamp
    if (!shouldRefresh) return this

    return latestLocal.copy(
        fileSize = fileSize.takeIf { it > 0L } ?: latestLocal.fileSize,
        fileContentModifiedTimestamp = maxOf(fileContentModifiedTimestamp, latestLocal.fileContentModifiedTimestamp),
        isAvailable = isAvailable || latestLocal.isAvailable,
        uriString = latestLocal.uriString ?: uriString,
        bookmarksJson = latestLocal.bookmarksJson ?: bookmarksJson,
        highlightsJson = latestLocal.highlightsJson ?: highlightsJson
    )
}

private fun RecentFileItem.withCloudReadingPosition(remote: BookMetadata): RecentFileItem {
    return copy(
        lastChapterIndex = remote.lastChapterIndex,
        lastPage = remote.lastPage,
        lastPositionCfi = remote.lastPositionCfi,
        locatorBlockIndex = remote.locatorBlockIndex,
        locatorCharOffset = remote.locatorCharOffset,
        progressPercentage = remote.progressPercentage,
        readingPositionModifiedTimestamp = remote.effectiveReadingPositionModifiedTimestamp()
    )
}

private fun RecentFileItem.withLocalReadingPosition(local: RecentFileItem): RecentFileItem {
    return copy(
        lastChapterIndex = local.lastChapterIndex,
        lastPage = local.lastPage,
        lastPositionCfi = local.lastPositionCfi,
        locatorBlockIndex = local.locatorBlockIndex,
        locatorCharOffset = local.locatorCharOffset,
        progressPercentage = local.progressPercentage,
        readingPositionModifiedTimestamp = local.effectiveReadingPositionModifiedTimestamp()
    )
}

private fun RecentFileItem.withLocalStorageForCloudMetadata(local: RecentFileItem): RecentFileItem {
    return copy(
        uriString = local.uriString ?: uriString,
        isAvailable = local.isAvailable || isAvailable,
        coverImagePath = local.coverImagePath ?: coverImagePath,
        sourceFolderUri = local.sourceFolderUri ?: sourceFolderUri,
        fileSize = local.fileSize.takeIf { it > 0L } ?: fileSize,
        fileContentModifiedTimestamp = maxOf(local.fileContentModifiedTimestamp, fileContentModifiedTimestamp),
        folderTextMetadataParsed = local.folderTextMetadataParsed || folderTextMetadataParsed,
        folderCoverMetadataParsed = local.folderCoverMetadataParsed || folderCoverMetadataParsed,
        tags = local.tags
    )
}
