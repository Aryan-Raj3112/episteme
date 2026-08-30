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
import androidx.compose.ui.graphics.Color
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
import com.aryan.reader.data.CloudBookDeletePersistence
import com.aryan.reader.data.CloudflareRepository
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.CloudFolderSyncRepository
import com.aryan.reader.data.CloudFolderSafScanner
import com.aryan.reader.data.CloudFolderLocalInventory
import com.aryan.reader.data.CloudFolderLocalInventoryState
import com.aryan.reader.data.DriveFile
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.data.FeedbackRepository
import com.aryan.reader.data.FirestoreRepository
import com.aryan.reader.data.FontMetadata
import com.aryan.reader.data.FontsRepository
import com.aryan.reader.data.GoogleDriveRepository
import com.aryan.reader.data.LocalSyncUtils
import com.aryan.reader.data.PurchaseEntity
import com.aryan.reader.data.PERSISTED_URI_GRANT_FLAGS
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
import com.aryan.reader.pdf.PdfHighlightColor
import com.aryan.reader.pdf.PdfUserHighlight
import com.aryan.reader.pdf.toSharedPdfHighlightAnnotation
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
import com.aryan.reader.shared.AnnotationExportDocument
import com.aryan.reader.shared.AnnotationExportFormat
import com.aryan.reader.shared.AnnotationExportFormatter
import com.aryan.reader.shared.AndroidShareArtifactManager
import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.CloudFolderDeviceBinding
import com.aryan.reader.shared.CloudFolderConflictResolution
import com.aryan.reader.shared.CloudFolderConflictUiItem
import com.aryan.reader.shared.CloudFolderIncomingChoice
import com.aryan.reader.shared.CloudFolderIncomingFolderPrompt
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderPermissionState
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderRootStats
import com.aryan.reader.shared.CloudFolderSyncDirection
import com.aryan.reader.shared.CloudFolderSyncProgress
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.CloudMaintenanceCoordinator
import com.aryan.reader.shared.CloudMaintenanceIntent
import com.aryan.reader.shared.CloudMaintenanceResult
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedLibraryEditor
import com.aryan.reader.shared.SharedImportOutcomeCounts
import com.aryan.reader.shared.SharedImportPlanner
import com.aryan.reader.shared.MobileImportOutcome
import com.aryan.reader.shared.record
import com.aryan.reader.shared.MobileExternalFileCloseAction
import com.aryan.reader.shared.mobileExternalFileCloseAction
import com.aryan.reader.shared.MobileReaderSessionRestoreAction
import com.aryan.reader.shared.MAX_SYNCED_FOLDER_COUNT
import com.aryan.reader.shared.SyncedFolderAddDecision
import com.aryan.reader.shared.AppShelfAction
import com.aryan.reader.shared.AppReaderSessionAction
import com.aryan.reader.shared.PdfSplitOrientation
import com.aryan.reader.shared.PdfSplitPane
import com.aryan.reader.shared.PdfSplitPaneState
import com.aryan.reader.shared.PdfSplitWorkspaceAction
import com.aryan.reader.shared.PdfSplitWorkspaceJson
import com.aryan.reader.shared.PdfSplitWorkspaceState
import com.aryan.reader.shared.recoverMissingPanes
import com.aryan.reader.shared.samePdfDocument
import com.aryan.reader.shared.cloudFolderRootId
import com.aryan.reader.shared.syncedFolderAddDecision
import com.aryan.reader.shared.withSyncedFolder
import com.aryan.reader.shared.withoutSyncedFolder
import com.aryan.reader.shared.withSyncedFolderFileTypes
import com.aryan.reader.shared.withSyncedFolderLocalSync
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.shouldApplyRemoteCloudBookMetadataUpdate
import com.aryan.reader.shared.shouldDownloadRemoteCloudBookContent
import com.aryan.reader.shared.shouldUploadLocalCloudBookContent
import com.aryan.reader.shared.shouldUploadLocalCloudBookMetadataUpdate
import com.aryan.reader.shared.sharedCloudBookContentFileName
import com.aryan.reader.shared.AppAction as SharedAppAction
import com.aryan.reader.shared.LibraryAction as SharedLibraryAction
import com.aryan.reader.shared.NavigationEvent
import com.aryan.reader.shared.reduce
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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

/**
 * Result of checking a folder-backed book before opening it. A malformed or
 * unregistered app-private URI is deliberately not treated as a missing file:
 * deleting the library row would hide an account/path-integrity problem.
 */
private data class FolderBookLocation(
    val uri: Uri?,
    val canConfirmMissing: Boolean,
    val accountId: String? = null,
)

private const val BANNER_AUTO_DISMISS_MILLIS = 3_000L
private const val CLOUD_CONTENT_RETRY_DELAY_MILLIS = 10_000L
private const val CLOUD_METADATA_UPLOAD_DEBOUNCE_MILLIS = 1_500L
private const val LOCAL_FOLDER_INVENTORY_REFRESH_MILLIS = 5L * 60L * 1_000L
private const val LOCAL_FOLDER_INVENTORY_RETRY_MILLIS = 30L * 1_000L
private const val LOCAL_FOLDER_INVENTORY_STALE_MILLIS = 60L * 1_000L

@kotlin.OptIn(ExperimentalSerializationApi::class)
@UnstableApi
open class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Context = application.applicationContext
    private val appGraph = AndroidAppGraph(appContext)
    private val authRepository = appGraph.authRepository
    private val bookStore = appGraph.bookStore
    private val folderMirrorStore = appGraph.folderMirrorStore
    private val bookArtifactStore = appGraph.bookArtifactStore
    private val legacyMigrationStore = appGraph.legacyMigrationStore
    private val libraryStore = appGraph.libraryStore
    private val pdfTextRepository get() = appGraph.pdfTextRepository
    private val bookCacheDao get() = appGraph.bookCacheDao
    private val epubParser get() = appGraph.epubParser
    private val mobiParser get() = appGraph.mobiParser
    private val fb2Parser get() = appGraph.fb2Parser
    private val odtParser get() = appGraph.odtParser
    private val singleFileImporter get() = appGraph.singleFileImporter
    private val bookImporter get() = appGraph.bookImporter
    private val epubMetadataFileEditor get() = appGraph.epubMetadataFileEditor
    private val pageLayoutRepository get() = appGraph.pageLayoutRepository
    private val pdfRichTextRepository get() = appGraph.pdfRichTextRepository
    private val pdfTextBoxRepository get() = appGraph.pdfTextBoxRepository
    private val pdfHighlightRepository get() = appGraph.pdfHighlightRepository
    private val pdfAnnotationRepository get() = appGraph.pdfAnnotationRepository

    private val prefs: SharedPreferences =
        application.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
    private val cloudBookDeletePersistence = CloudBookDeletePersistence(appContext)
    private val restoredPdfSplitWorkspace = PdfSplitWorkspaceJson.decodeOrEmpty(
        prefs.getString(KEY_PDF_SPLIT_WORKSPACE, null),
    )
    private val firestoreRepository = appGraph.firestoreRepository
    private val googleDriveRepository = appGraph.googleDriveRepository
    private val billingClientWrapper =
        BillingClientWrapper(appContext, viewModelScope) { purchase ->
            verifyPurchaseWithBackend(purchase)
        }
    private val cloudflareRepository = appGraph.cloudflareRepository
    private val remoteConfigRepository = appGraph.remoteConfigRepository
    private var userProfileListener: Any? = null
    private val _prefsUpdateFlow = MutableStateFlow(0L)
    private val prefsListener: SharedPreferences.OnSharedPreferenceChangeListener
    private val feedbackRepository = appGraph.feedbackRepository
    private val libraryStateProjector = LibraryStateProjector(AndroidFolderPathResolver())
    private val libraryMutationController by lazy {
        appGraph.libraryMutationController(::syncShelfChangeToFirestore)
    }
    private var feedbackListener: Any? = null
    private val importMutex = Mutex()
    private val epubRecoveryMutex = Mutex()
    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    @Suppress("unused")
    val navigationEvent = _navigationEvent.receiveAsFlow()
    private val _temporaryExternalOpenFinished = Channel<Unit>(Channel.BUFFERED)
    val temporaryExternalOpenFinished = _temporaryExternalOpenFinished.receiveAsFlow()
    private var bannerDismissJob: Job? = null
    private var bannerDismissGeneration = 0L
    private var pendingSwitchDeferred: CompletableDeferred<Boolean>? = null
    private var externalOpenedBookId: String? = null
    private var temporaryExternalSessionBookId: String? = null
    private var cloudContentRetryJob: Job? = null
    private val cloudMetadataUploadJobs = ConcurrentHashMap<String, Job>()
    /**
     * Reader callbacks are intentionally fire-and-forget, but closing a
     * reader must take a durable snapshot only after the final Room write has
     * committed.  Lazy jobs are registered before starting so close cannot
     * race the bookkeeping itself.
     */
    private val pendingReaderStateSaveJobs = ConcurrentHashMap<String, Job>()
    private val pendingBookmarkSaveJobs = ConcurrentHashMap<String, Job>()
    private val bookmarkSaveMutex = Mutex()
    private val bookmarkSaveRevisions = mutableMapOf<String, Long>()

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

    // Connecting creates TtsService. Keep it lazy so browsing the app never starts
    // the user's system TTS engine; TtsController.start() connects on demand.
    val ttsController by lazy { TtsController(appContext) }

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
            libraryState = LibraryState(
                sortOrder = try {
                    val savedSortOrderName = prefs.getString(
                        KEY_SORT_ORDER, SortOrder.RECENT.name
                    )
                    when (savedSortOrderName) {
                        null -> SortOrder.RECENT
                        else -> SortOrder.valueOf(savedSortOrderName)
                    }
                } catch (_: IllegalArgumentException) {
                    SortOrder.RECENT
                },
                filters = LibraryFilters(
                    fileTypes = prefs.getStringSet(KEY_FILTER_FILE_TYPES, emptySet())?.mapNotNull {
                        runCatching { FileType.valueOf(it) }.getOrNull()
                    }?.filterTo(mutableSetOf()) { it in ANDROID_READABLE_FILE_TYPES } ?: emptySet(),
                    sourceFolders = prefs.getStringSet(KEY_FILTER_FOLDERS, emptySet()) ?: emptySet(),
                    readStatus = runCatching {
                        ReadStatusFilter.valueOf(prefs.getString(KEY_FILTER_READ_STATUS, ReadStatusFilter.ALL.name) ?: ReadStatusFilter.ALL.name)
                    }.getOrDefault(ReadStatusFilter.ALL),
                    tagIds = prefs.getStringSet(KEY_FILTER_TAG_IDS, emptySet()) ?: emptySet(),
                ),
                libraryPage = prefs.getInt(
                    KEY_LIBRARY_SCREEN_START_PAGE,
                    0,
                ).coerceIn(0, if (BuildConfig.IS_OFFLINE) 2 else 3),
                recentLimit = prefs.getInt(KEY_RECENT_FILES_LIMIT, 0),
            ),
            shelfState = AppShelfState(
                viewingShelfId = prefs.getString(KEY_LAST_VIEWING_SHELF_ID, null),
                isAddingBooks = prefs.getBoolean(KEY_LAST_ADDING_BOOKS_TO_SHELF, false),
                addBooksSource = try {
                    val savedSourceName = prefs.getString(
                        KEY_ADD_BOOKS_SOURCE, AddBooksSource.UNSHELVED.name
                    )
                    AddBooksSource.valueOf(savedSourceName ?: AddBooksSource.UNSHELVED.name)
                } catch (_: IllegalArgumentException) {
                    AddBooksSource.UNSHELVED
                },
            ),
            mainScreenStartPage = prefs.getInt(KEY_MAIN_SCREEN_START_PAGE, 0).coerceIn(0, 2),
            unifiedLibrarySection = prefs.getInt(KEY_UNIFIED_LIBRARY_SECTION, 0)
                .coerceIn(0, 4),
            unifiedLibraryListView = prefs.getBoolean(KEY_UNIFIED_LIBRARY_LIST_VIEW, false),
            pdfSplitWorkspace = restoredPdfSplitWorkspace,
            currentUser = authRepository.getSignedInUser(),
            isSyncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, false),
            isFolderSyncEnabled = prefs.getBoolean(KEY_FOLDER_SYNC_ENABLED, false),
            syncedFolders = loadSyncedFoldersFromPrefs(),
            lastFolderScanTime = if (prefs.contains(KEY_LAST_FOLDER_SCAN_TIME)) prefs.getLong(
                KEY_LAST_FOLDER_SCAN_TIME, 0L
            )
            else null,
            pinState = AppPinState(
                homeBookIds = prefs.getStringSet(KEY_PINNED_HOME, emptySet()) ?: emptySet(),
                libraryBookIds = prefs.getStringSet(KEY_PINNED_LIBRARY, emptySet()) ?: emptySet(),
            ),
            tabState = AppTabState(
                isEnabled = prefs.getBoolean(KEY_TABS_ENABLED, true),
                openBookIds = prefs.getString(KEY_OPEN_TAB_IDS, null)?.let {
                    try {
                        val arr = JSONArray(it)
                        List(arr.length()) { i -> arr.getString(i) }
                    } catch(_: Exception) { emptyList() }
                } ?: emptyList(),
                activeBookId = prefs.getString(KEY_ACTIVE_TAB, null),
            ),
            externalFileBehavior = prefs.getString(KEY_EXTERNAL_FILE_BEHAVIOR, "ASK") ?: "ASK",
            useStrictFileFilter = prefs.getBoolean(KEY_USE_STRICT_FILE_FILTER, false),
            usePdfFileNameAsDisplayName = prefs.getBoolean(KEY_USE_PDF_FILE_NAME_AS_DISPLAY_NAME, false),
            isScreenCaptureProtectionEnabled = prefs.getBoolean(KEY_SCREEN_CAPTURE_PROTECTION, false),
            appAppearance = AppAppearanceState(
                themeMode = try {
                    AppThemeMode.valueOf(prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
                } catch (_: Exception) { AppThemeMode.SYSTEM },
                contrastOption = try {
                    AppContrastOption.valueOf(prefs.getString(KEY_APP_CONTRAST_OPTION, AppContrastOption.STANDARD.name) ?: AppContrastOption.STANDARD.name)
                } catch (_: Exception) { AppContrastOption.STANDARD },
                textDimFactorLight = prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR_LIGHT, prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR, 1.0f)),
                textDimFactorDark = prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR_DARK, prefs.getFloat(KEY_APP_TEXT_DIM_FACTOR, 1.0f)),
                seedColor = if (prefs.contains(KEY_APP_SEED_COLOR)) androidx.compose.ui.graphics.Color(prefs.getInt(KEY_APP_SEED_COLOR, 0)) else null,
                fontPreference = loadAppFontPreference(prefs),
                customThemes = loadCustomAppThemes(prefs),
            )
        )
    )

    /**
     * Cloud-folder inventory is intentionally separate from the library
     * projection. A folder may be cloud-synced while local indexing is off,
     * so settings must source counts and prompt state from the repository.
     */
    private val _cloudFolderRootStats = MutableStateFlow<Map<String, CloudFolderRootStats>>(emptyMap())
    val cloudFolderRootStats: StateFlow<Map<String, CloudFolderRootStats>> = _cloudFolderRootStats.asStateFlow()

    /** Device-local SAF counts; never used as the account-level manifest stats. */
    private val _cloudFolderLocalInventories = MutableStateFlow<Map<String, CloudFolderLocalInventory>>(emptyMap())
    val cloudFolderLocalInventories: StateFlow<Map<String, CloudFolderLocalInventory>> =
        _cloudFolderLocalInventories.asStateFlow()

    private val activeLocalInventoryScans = ConcurrentHashMap.newKeySet<String>()

    /**
     * Account-level roots and this device's materialization state are kept as
     * first-class UI state.  This is what lets settings show a remote
     * cloud-only or app-managed offline root even when no local SAF folder is
     * indexed.
     */
    private val _cloudFolderRoots = MutableStateFlow<List<CloudFolderRoot>>(emptyList())
    val cloudFolderRoots: StateFlow<List<CloudFolderRoot>> = _cloudFolderRoots.asStateFlow()

    private val _cloudFolderBindings = MutableStateFlow<Map<String, CloudFolderDeviceBinding>>(emptyMap())
    val cloudFolderBindings: StateFlow<Map<String, CloudFolderDeviceBinding>> =
        _cloudFolderBindings.asStateFlow()

    /** Durable per-root transfer state used by the folder-sync surface. */
    private val _cloudFolderSyncProgress = MutableStateFlow<Map<String, CloudFolderSyncProgress>>(emptyMap())
    val cloudFolderSyncProgress: StateFlow<Map<String, CloudFolderSyncProgress>> =
        _cloudFolderSyncProgress.asStateFlow()

    private val _cloudFolderConflicts = MutableStateFlow<List<CloudFolderConflictUiItem>>(emptyList())
    val cloudFolderConflicts: StateFlow<List<CloudFolderConflictUiItem>> =
        _cloudFolderConflicts.asStateFlow()

    private val _incomingCloudFolderPrompt = MutableStateFlow<CloudFolderIncomingFolderPrompt?>(null)
    val incomingCloudFolderPrompt: StateFlow<CloudFolderIncomingFolderPrompt?> =
        _incomingCloudFolderPrompt.asStateFlow()

    private suspend fun prepareBookForImport(externalUri: Uri): AndroidPreparedImport? = withContext(Dispatchers.IO) {
        val displayName = getFileNameFromUri(externalUri, appContext)
        var type = getFileTypeFromUri(externalUri, appContext)

        val hash = FileHasher.calculateSha256 {
            appContext.contentResolver.openInputStream(externalUri)
        }
        if (hash == null) {
            Timber.e("Failed to process file hash for $externalUri")
            return@withContext null
        }

        val existingItem = bookStore.getFileByBookId(hash)
        if (existingItem != null) {
            val pendingRemoval = pendingExternalFileRemovals()
                .firstOrNull { it.bookId == hash }
            if (pendingRemoval != null) {
                deletePendingExternalFileRemoval(
                    pendingRemoval.copy(uriString = pendingRemoval.uriString ?: existingItem.uriString)
                )
            } else {
                Timber.i("Book with ID: $hash already exists. Skipping import.")
                return@withContext null
            }
        }

        val fileName = displayName ?: ""
        if (fileName.endsWith(".zip", ignoreCase = true) || type == FileType.CBZ) {
            val bundleResult = CalibreBundleExtractor.processZip(appContext, externalUri, hash, bookImporter, bookArtifactStore)

            Timber.d("MainViewModel: Calibre processZip returned: $bundleResult")

            if (bundleResult != null) {
                return@withContext AndroidPreparedImport(
                    internalUri = bundleResult.internalBookUri,
                    result = com.aryan.reader.shared.ImportResult(
                        uriString = bundleResult.internalBookUri.toString(),
                        bookId = hash,
                        type = bundleResult.type,
                    ),
                    bundleResult = bundleResult
                )
            }
            if (type == null) type = FileType.CBZ
        }

        if (type == null) return@withContext null

        Timber.i("Importing new book with ID: $hash")
        val internalFile = bookImporter.importBook(externalUri) ?: return@withContext null
        val internalUri = internalFile.toUri()
        return@withContext AndroidPreparedImport(
            internalUri = internalUri,
            result = com.aryan.reader.shared.ImportResult(internalUri.toString(), hash, type),
        )
    }

    val libraryFlow = combine(
        bookStore.getRecentFilesFlow(),
        libraryStore.activeShelvesFlow,
        libraryStore.shelfCrossRefsFlow,
        ::Triple
    )

    val tagFlow = combine(
        libraryStore.tagsFlow,
        libraryStore.tagCrossRefsFlow,
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
        return reduceLibraryAction(
            current = this,
            projectedState = uiState.value,
            action = action
        )
    }

    private fun ReaderScreenState.withClearedLibraryBookSelection(): ReaderScreenState {
        return copy(
            libraryState = libraryState.reduce(SharedLibraryAction.SelectionCleared),
            contextualActionItems = emptySet(),
        )
    }

    private fun ReaderScreenState.withSharedAppAction(action: SharedAppAction): ReaderScreenState {
        return copy(appAppearance = appAppearance.reduce(action))
    }

    fun setTabsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_TABS_ENABLED, enabled) }
        _internalState.update {
            setTabsEnabled(
                current = it,
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

        val projectedState = uiState.value
        val availableBookIds = projectedState.rawLibraryFiles.mapTo(mutableSetOf()) { it.bookId }
        val currentState = reconcileTabState(_internalState.value, availableBookIds)
        if (bookId !in currentState.openTabIds) {
            if (currentState.openTabIds.size >= MAX_OPEN_PDF_TABS) {
                viewModelScope.launch(Dispatchers.Main) {
                    showBanner("Maximum of 20 tabs allowed. Please close a tab first.", isError = true)
                }
                return
            }
        }
        val tabState = openBookTab(
            current = currentState,
            availableBookIds = availableBookIds,
            bookId = bookId
        )

        persistTabState(tabState.openTabIds, tabState.activeTabBookId)

        val uri = item.getUri()
        Timber.tag("PdfTabSync").d("ViewModel: ActiveTab updated to $bookId. URI found: ${uri != null}")

        uri?.let {
            persistReaderSession(bookId, item.type)
            Timber.tag(PDF_RENAME_TRACE_TAG).i(
                "viewModel.switchTab bookId=$bookId type=${item.type} uri=$it " +
                    "displayName=${item.displayName} title=${item.title} customName=${item.customName} " +
                    "usePdfFileName=${_internalState.value.usePdfFileNameAsDisplayName}"
            )
            Timber.tag("PdfTabSync").d("ViewModel: Setting new URI directly: $it")
            _internalState.update { state ->
                markReaderSessionReady(
                    startReaderSession(state, bookId, item.type),
                    bookId,
                ).copy(
                    tabState = tabState.tabState,
                    selectedPdfUri = it,
                    initialPageInBook = item.lastPage,
                    initialPageInBookIsExplicit = false,
                    isOpeningFromTtsNotification = false,
                    initialBookmarksJson = item.bookmarksJson,
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
                    tabState = tabState.tabState,
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

    private fun reconcilePdfSplitWorkspace(libraryFiles: List<RecentFileItem>) {
        val snapshot = _internalState.value.pdfSplitWorkspace
        if (!snapshot.isOpen) return
        val cleanSnapshot = snapshot.sanitized()

        val recovery = cleanSnapshot.recoverMissingPanes(
            primaryAvailable = cleanSnapshot.primary?.let {
                resolveAvailablePdfSplitPane(it, libraryFiles) != null
            } ?: false,
            secondaryAvailable = cleanSnapshot.secondary?.let {
                resolveAvailablePdfSplitPane(it, libraryFiles) != null
            } ?: false,
        )
        if (!recovery.hasMissingPanes) return

        val repairedWorkspace = recovery.workspace.copy(
            primary = recovery.workspace.primary?.let { document ->
                resolveAvailablePdfSplitPane(document, libraryFiles)?.let { item ->
                    PdfSplitPaneState(item.bookId, item.uriString.orEmpty(), document.sessionId)
                } ?: document
            },
            secondary = recovery.workspace.secondary?.let { document ->
                resolveAvailablePdfSplitPane(document, libraryFiles)?.let { item ->
                    PdfSplitPaneState(item.bookId, item.uriString.orEmpty(), document.sessionId)
                } ?: document
            },
        ).sanitized()
        val repairedRecovery = recovery.copy(workspace = repairedWorkspace)

        var applied = false
        _internalState.update { state ->
            if (state.pdfSplitWorkspace != snapshot) {
                state
            } else {
                applied = true
                state.copy(pdfSplitWorkspace = repairedRecovery.workspace)
            }
        }
        if (!applied) return
        persistPdfSplitWorkspace(repairedRecovery.workspace)

        viewModelScope.launch(Dispatchers.Main.immediate) {
            val current = _internalState.value
            if (current.pdfSplitWorkspace != repairedRecovery.workspace) return@launch

            repairedRecovery.survivingDocument?.let { survivor ->
                if (current.selectedBookId != survivor.bookId) {
                    activatePdfPane(survivor, libraryFiles)
                }
            } ?: run {
                // Closing the workspace must not delete an external file or
                // clear caches owned by another reader. Only clear this
                // reader session when it still points at one of the missing
                // split documents.
                val selectedBookId = current.selectedBookId
                val missingBookIds = snapshot.primary
                    ?.let { primary -> setOfNotNull(primary.bookId, snapshot.secondary?.bookId) }
                    .orEmpty()
                if (selectedBookId != null && selectedBookId in missingBookIds) {
                    closeUnavailablePdfReaderSession()
                }
            }
            showBanner(
                appContext.getString(
                    if (repairedRecovery.survivingDocument != null) {
                        R.string.pdf_split_reader_missing_document
                    } else {
                        R.string.pdf_split_reader_all_documents_missing
                    },
                ),
            )
        }
    }

    private fun resolveAvailablePdfSplitPane(
        document: PdfSplitPaneState,
        libraryFiles: Collection<RecentFileItem>,
    ): RecentFileItem? {
        val item = libraryFiles.firstOrNull { candidate ->
            candidate.type == FileType.PDF &&
                !candidate.isDeleted &&
                candidate.isAvailable &&
                candidate.uriString != null &&
                PdfSplitPaneState(candidate.bookId, candidate.uriString).samePdfDocument(document)
        } ?: return null
        val uri = item.getUri() ?: return null
        val isReadable = runCatching {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
        }.getOrDefault(false)
        return item.takeIf { isReadable }
    }

    private fun closeUnavailablePdfReaderSession() {
        _internalState.update { current ->
            closeReaderSession(current).copy(
                selectedPdfUri = null,
                selectedEpubUri = null,
                selectedEpubBook = null,
                isTemporaryExternalOpen = false,
                initialLocator = null,
                initialPageInBook = null,
                initialPageInBookIsExplicit = false,
                isOpeningFromTtsNotification = false,
            )
        }
        clearPersistedReaderSession()
    }

    /** Opens a second library PDF beside the currently selected PDF. */
    fun openPdfSplit(
        secondaryBookId: String,
    ): Boolean {
        val current = _internalState.value
        val primaryUri = current.selectedPdfUri ?: return false
        if (current.selectedFileType != FileType.PDF) return false

        val secondaryItem = uiState.value.rawLibraryFiles.firstOrNull {
            it.bookId == secondaryBookId && it.type == FileType.PDF && !it.isDeleted && it.isAvailable
        } ?: return false
        val secondaryUri = secondaryItem.getUri() ?: return false
        val primaryBookId = current.selectedBookId ?: primaryUri.toString()
        val primaryDocument = PdfSplitPaneState(primaryBookId, primaryUri.toString())
        val secondaryDocument = PdfSplitPaneState(secondaryItem.bookId, secondaryUri.toString())
        if (primaryDocument.samePdfDocument(secondaryDocument)) return false

        _internalState.update { state ->
            val workspace = state.pdfSplitWorkspace
            val next = if (workspace.isOpen) {
                workspace.reduce(
                    PdfSplitWorkspaceAction.PaneOpened(
                        pane = PdfSplitPane.SECONDARY,
                        document = PdfSplitPaneState(
                            bookId = secondaryItem.bookId,
                            uriString = secondaryUri.toString(),
                        ),
                    )
                )
            } else {
                workspace.reduce(
                    PdfSplitWorkspaceAction.Open(
                        primary = PdfSplitPaneState(
                            bookId = primaryBookId,
                            uriString = primaryUri.toString(),
                        ),
                        secondary = PdfSplitPaneState(
                            bookId = secondaryItem.bookId,
                            uriString = secondaryUri.toString(),
                        ),
                    )
                )
            }
            persistPdfSplitWorkspace(next)
            state.copy(pdfSplitWorkspace = next)
        }
        return true
    }

    fun openPdfSplitPane(
        bookId: String,
        targetPane: PdfSplitPane = PdfSplitPane.SECONDARY,
        expectedRevision: Long? = null,
        expectedSessionId: Long? = null,
    ): Boolean {
        val current = _internalState.value
        val item = uiState.value.rawLibraryFiles.firstOrNull {
            it.bookId == bookId && it.type == FileType.PDF && !it.isDeleted && it.isAvailable
        } ?: return false
        val uri = item.getUri() ?: return false
        val workspace = current.pdfSplitWorkspace
        if (!workspace.isOpen) return false
        val document = PdfSplitPaneState(item.bookId, uri.toString())
        val otherPane = when (targetPane) {
            PdfSplitPane.PRIMARY -> workspace.secondary
            PdfSplitPane.SECONDARY -> workspace.primary
        }
        if (otherPane?.samePdfDocument(document) == true) return false

        var didOpen = false
        _internalState.update { state ->
            val next = state.pdfSplitWorkspace.reduce(
                PdfSplitWorkspaceAction.PaneOpened(
                    pane = targetPane,
                    document = PdfSplitPaneState(item.bookId, uri.toString()),
                    expectedRevision = expectedRevision,
                    expectedSessionId = expectedSessionId,
                ),
            )
            if (next == state.pdfSplitWorkspace) {
                state
            } else {
                didOpen = true
                persistPdfSplitWorkspace(next)
                state.copy(pdfSplitWorkspace = next)
            }
        }
        return didOpen
    }

    fun focusPdfSplitPane(
        pane: PdfSplitPane,
        expectedRevision: Long? = null,
        expectedSessionId: Long? = null,
    ) {
        _internalState.update { state ->
            val next = state.pdfSplitWorkspace.reduce(
                PdfSplitWorkspaceAction.FocusChanged(
                    pane = pane,
                    expectedRevision = expectedRevision,
                    expectedSessionId = expectedSessionId,
                ),
            )
            persistPdfSplitWorkspace(next)
            state.copy(pdfSplitWorkspace = next)
        }
    }

    fun setPdfSplitDividerFraction(
        fraction: Float,
        orientation: PdfSplitOrientation? = null,
        expectedRevision: Long? = null,
    ) {
        _internalState.update { state ->
            val next = state.pdfSplitWorkspace.reduce(
                PdfSplitWorkspaceAction.DividerChanged(
                    fraction = fraction,
                    orientation = orientation,
                    expectedRevision = expectedRevision,
                ),
            )
            persistPdfSplitWorkspace(next)
            state.copy(pdfSplitWorkspace = next)
        }
    }

    fun swapPdfSplitPanes() {
        _internalState.update { state ->
            val next = state.pdfSplitWorkspace.reduce(PdfSplitWorkspaceAction.PanesSwapped)
            persistPdfSplitWorkspace(next)
            state.copy(pdfSplitWorkspace = next)
        }
    }

    /**
     * Closes one pane. The remaining pane stays in the workspace so it can be
     * expanded naturally or receive another document. Returns whether the
     * workspace still has a reader pane.
     */
    fun closePdfSplitPane(
        pane: PdfSplitPane,
        expectedRevision: Long? = null,
        expectedSessionId: Long? = null,
    ): Boolean {
        val action = PdfSplitWorkspaceAction.PaneClosed(
            pane = pane,
            expectedRevision = expectedRevision,
            expectedSessionId = expectedSessionId,
        )
        _internalState.update { state ->
            val next = state.pdfSplitWorkspace.reduce(action)
            persistPdfSplitWorkspace(next)
            state.copy(pdfSplitWorkspace = next)
        }
        return _internalState.value.pdfSplitWorkspace.isOpen
    }

    /** Leaves split mode while keeping the focused workspace document selected. */
    fun closePdfSplitWorkspace() {
        val current = _internalState.value
        val exitDocument = current.pdfSplitWorkspace.exitTargetDocument
        val selectedBookId = current.selectedBookId
        _internalState.update { state ->
            val next = state.pdfSplitWorkspace.reduce(PdfSplitWorkspaceAction.Closed)
            persistPdfSplitWorkspace(next)
            state.copy(pdfSplitWorkspace = next)
        }
        if (exitDocument != null && exitDocument.bookId != selectedBookId) {
            activatePdfPane(exitDocument, uiState.value.rawLibraryFiles)
        }
    }

    private fun activatePdfPane(
        bookId: String,
        libraryFiles: Collection<RecentFileItem>? = null,
    ) {
        val item = (libraryFiles ?: uiState.value.rawLibraryFiles).firstOrNull {
            it.bookId == bookId && it.type == FileType.PDF && !it.isDeleted && it.isAvailable
        } ?: return
        activatePdfPane(item)
    }

    private fun activatePdfPane(
        document: PdfSplitPaneState,
        libraryFiles: Collection<RecentFileItem>,
    ) {
        val item = libraryFiles.firstOrNull {
            it.bookId == document.bookId && it.type == FileType.PDF && !it.isDeleted && it.isAvailable
        } ?: libraryFiles.firstOrNull {
            it.type == FileType.PDF && !it.isDeleted && it.isAvailable &&
                it.uriString?.let { uri -> PdfSplitPaneState(it.bookId, uri).samePdfDocument(document) } == true
        } ?: return
        activatePdfPane(item)
    }

    private fun activatePdfPane(item: RecentFileItem) {
        val uri = item.getUri() ?: return
        persistReaderSession(item.bookId, item.type)
        _internalState.update { state ->
            val prepared = state.copy(
                selectedPdfUri = uri,
                selectedEpubUri = null,
                selectedEpubBook = null,
            )
            markReaderSessionReady(
                startReaderSession(prepared, item.bookId, item.type),
                item.bookId,
            ).copy(
                selectedPdfUri = uri,
                selectedEpubUri = null,
                selectedEpubBook = null,
                initialPageInBook = item.lastPage,
                initialPageInBookIsExplicit = false,
                initialBookmarksJson = item.bookmarksJson,
                initialHighlightsJson = null,
                isOpeningFromTtsNotification = false,
            )
        }
    }

    private fun persistPdfSplitWorkspace(workspace: PdfSplitWorkspaceState) {
        prefs.edit {
            if (workspace.isOpen) {
                putString(KEY_PDF_SPLIT_WORKSPACE, PdfSplitWorkspaceJson.encode(workspace))
            } else {
                remove(KEY_PDF_SPLIT_WORKSPACE)
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

    fun openAddSelectedToShelf(bookIds: Set<String>) {
        val sanitizedBookIds = SharedLibraryEditor.cleanBookIds(bookIds)
        if (sanitizedBookIds.isEmpty()) return
        _internalState.update { it.copy(showAddSelectedToShelfDialogFor = sanitizedBookIds) }
    }

    fun closeAddSelectedToShelf() {
        _internalState.update { it.copy(showAddSelectedToShelfDialogFor = emptySet()) }
    }

    fun addSelectedBooksToShelves(shelfIds: Set<String>, bookIds: Set<String>) {
        val sanitizedBookIds = SharedLibraryEditor.cleanBookIds(bookIds)
        val mutableManualShelfIds = uiState.value.shelves
            .filter { shelf -> shelf.type == ShelfType.MANUAL && SharedLibraryEditor.canMutateShelf(shelf.id) }
            .mapTo(mutableSetOf()) { shelf -> shelf.id }
        val sanitizedShelfIds = shelfIds
            .mapNotNullTo(linkedSetOf()) { shelfId ->
                shelfId.trim().takeIf { it in mutableManualShelfIds }
            }
        if (sanitizedBookIds.isEmpty() || sanitizedShelfIds.isEmpty()) {
            closeAddSelectedToShelf()
            return
        }

        viewModelScope.launch {
            libraryMutationController.addBooksToShelves(sanitizedBookIds, sanitizedShelfIds)
            _internalState.update {
                it.copy(
                    showAddSelectedToShelfDialogFor = emptySet(),
                ).withClearedLibraryBookSelection()
            }
        }
    }

    suspend fun getFileInfoItem(bookId: String): RecentFileItem? {
        return bookStore.getFileByBookId(bookId)
    }

    fun createAndAssignTag(name: String, bookIds: Set<String>) {
        val sanitizedBookIds = SharedLibraryEditor.cleanBookIds(bookIds)
        if (sanitizedBookIds.isEmpty()) return

        viewModelScope.launch {
            libraryMutationController.createAndAssignTag(name, sanitizedBookIds)
        }
    }

    fun toggleTagForBooks(tagId: String, bookIds: Set<String>, assign: Boolean) {
        val sanitizedBookIds = SharedLibraryEditor.cleanBookIds(bookIds)
        if (tagId.isBlank() || sanitizedBookIds.isEmpty()) return
        viewModelScope.launch {
            libraryMutationController.setTagAssigned(tagId, sanitizedBookIds, assign)
        }
    }

    fun deleteTag(tagId: String) {
        val cleanTagId = tagId.trim().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            libraryMutationController.deleteTag(cleanTagId)
            val projectedFilters = uiState.value.libraryFilters
            val currentFilters = _internalState.value.libraryFilters
            val filtersToUpdate = if (cleanTagId in projectedFilters.tagIds) projectedFilters else currentFilters
            if (cleanTagId in filtersToUpdate.tagIds) {
                updateLibraryFilters(filtersToUpdate.copy(tagIds = filtersToUpdate.tagIds - cleanTagId))
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
        val projectedState = uiState.value
        val availableBookIds = projectedState.rawLibraryFiles.mapTo(mutableSetOf()) { it.bookId }
        val currentState = reconcileTabState(_internalState.value, availableBookIds)
        val tabState = closeBookTab(
            current = currentState,
            availableBookIds = availableBookIds,
            bookId = bookId
        )

        if (tabState.openTabIds.isEmpty()) {
            persistTabState(tabState.openTabIds, tabState.activeTabBookId)
            _internalState.update {
                it.copy(
                    tabState = tabState.tabState,
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
                        tabState = tabState.tabState.copy(activeBookId = nextTabId),
                    )
                }
                switchTab(nextTabId)
            } else {
                persistTabState(tabState.openTabIds, tabState.activeTabBookId)
                _internalState.update {
                    it.copy(
                        tabState = tabState.tabState,
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
                it.copy(
                    isSearchActive = false,
                    libraryState = it.libraryState.reduce(SharedLibraryAction.SearchChanged("")),
                )
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
            val filesToDelete = bookStore.getAllFilesForSync().filter {
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
                bookStore.deleteFilePermanently(ids)
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

        // Durable split identities are only preferences. Reconcile them with
        // the live library and provider as soon as the inventory is available
        // so a deleted or revoked URI cannot remain a broken reader session.
        viewModelScope.launch(Dispatchers.IO) {
            libraryFlow
                .map { it.first }
                .distinctUntilChanged()
                .collectLatest(::reconcilePdfSplitWorkspace)
        }

        WorkManager.getInstance(application).apply {
            cancelUniqueWork(FolderSyncWorker.WORK_NAME)
            pruneWork()
        }

        viewModelScope.launch(Dispatchers.IO) {
            FolderAnnotationExportWorker.scheduleAllPending(appContext)
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
            libraryStore.migrateLegacyShelves()
            if (!prefs.getBoolean(KEY_DEFAULT_TAGS_SEEDED, false)) {
                libraryStore.seedTagsIfEmpty(buildDefaultTags())
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

        // Cloud-folder workers run outside the Compose tree and may discover
        // a new root while the user is on any screen.  Refresh repository
        // state as soon as a worker commits a manifest; startup still covers
        // emissions that happened while this process was dead.
        viewModelScope.launch {
            CloudFolderSyncEvents.stateChanged.collect {
                refreshCloudFolderSyncState()
            }
        }

        // Foreground folder-head invalidation is application-scoped, while
        // entitlement and the main sync switch are owned by this ViewModel.
        // Keep the two concerns separate so a stale account or a downgrade
        // detaches the Firestore listener immediately.
        CloudFolderHeadListenerCoordinator.install(appContext)
        viewModelScope.launch {
            _internalState
                .map { state ->
                    Triple(
                        state.currentUser?.uid?.trim()?.takeIf { it.isNotBlank() },
                        state.isProUser,
                        state.isSyncEnabled,
                    )
                }
                .distinctUntilChanged()
                .collect { (accountId, isPro, syncEnabled) ->
                    CloudFolderHeadListenerCoordinator.updateEligibility(
                        context = appContext,
                        accountId = accountId,
                        isPro = isPro,
                        syncEnabled = syncEnabled,
                    )
                }
        }

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

        _internalState.value.currentUser?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { accountId ->
                viewModelScope.launch(Dispatchers.IO) {
                    registerLocalCloudFolders(accountId)
                    refreshCloudFolderSyncState()
                }
                // A delete is an explicit durable user action and must finish
                // even when the main sync switch is currently off.  Requeue
                // it on process start so cancelling a worker during sign-out
                // cannot strand the account's outbox.
                viewModelScope.launch(Dispatchers.IO) {
                    if (cloudBookDeletePersistence.pending(accountId).isNotEmpty()) {
                        runCatching {
                            CloudBookDeleteWorker.enqueue(appContext, accountId)
                        }.onFailure { error ->
                            Timber.e(error, "Unable to resume pending cloud-book deletion on startup")
                        }
                    }
                }
                if (_internalState.value.isSyncEnabled) {
                    // Discovery is metadata-only for unbound roots and is
                    // safe to schedule on every app start.
                    CloudFolderSyncWorker.enqueuePull(
                        appContext,
                        accountId = accountId,
                        replace = false,
                    )
                }
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
                    // Cloud roots and device-local bindings are account
                    // scoped. Rehydrate registrations before any sync work so
                    // a folder added while signed out can still be uploaded
                    // directly without entering the library.
                    viewModelScope.launch(Dispatchers.IO) {
                        registerLocalCloudFolders(newUserData.uid)
                        refreshCloudFolderSyncState()
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        if (cloudBookDeletePersistence.pending(newUserData.uid).isNotEmpty()) {
                            runCatching {
                                CloudBookDeleteWorker.enqueue(appContext, newUserData.uid)
                            }.onFailure { error ->
                                Timber.e(error, "Unable to resume pending cloud-book deletion after sign-in")
                            }
                        }
                    }
                    registerOrUpdateDeviceOnSignIn(newUserData.uid)

                    feedbackListener =
                        feedbackRepository.listenForUnreadFeedback(newUserData.uid) { hasUnread ->
                            _internalState.update { it.copy(hasUnreadFeedback = hasUnread) }
                        }

                    userProfileListener = firestoreRepository.listenToUserProfile(newUserData.uid) { isProFromBackend, creditsFromBackend ->
                        _internalState.update { it.copy(isProUser = isProFromBackend, credits = creditsFromBackend) }

                            if (!isProFromBackend) {
                                // A profile downgrade can arrive while cloud
                                // work is queued or while the persisted sync
                                // switch still says "on". Stop both sources
                                // of truth immediately; the worker also
                                // rechecks the switch at execution time.
                                disableCloudSyncAfterEntitlementLoss(newUserData.uid)
                            }

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
                                            CloudFolderSyncWorker.enqueuePull(
                                                appContext,
                                                accountId = newUserData.uid,
                                                replace = true,
                                            )
                                            CloudFolderSyncWorker.enqueue(
                                                appContext,
                                                accountId = newUserData.uid,
                                                replace = true,
                                            )
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
                    _cloudFolderRootStats.value = emptyMap()
                    _cloudFolderLocalInventories.value = emptyMap()
                    _cloudFolderRoots.value = emptyList()
                    _cloudFolderBindings.value = emptyMap()
                    _cloudFolderConflicts.value = emptyList()
                    _incomingCloudFolderPrompt.value = null
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
            bookStore.deleteFilePermanently(listOf(removal.bookId))
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

    private fun restoreReaderSessionIfNeeded() {
        val currentState = _internalState.value
        val readerSession = readerSessionState(currentState)
        if (!readerSession.canRestorePersistedReader || currentState.selectedPdfUri != null || currentState.selectedEpubUri != null) {
            return
        }

        val persistedTypeName = prefs.getString(KEY_LAST_OPEN_FILE_TYPE, null)
        val restoreBookId = prefs.getString(KEY_LAST_OPEN_BOOK_ID, null) ?: return

        viewModelScope.launch {
            val item = bookStore.getFileByBookId(restoreBookId)
            val restoreUri = item?.getUri()
            val restoreAction = androidReaderSessionRestoreAction(
                restoreBookId,
                persistedTypeName,
                pendingExternalFileRemovals().mapTo(mutableSetOf()) { it.bookId },
                item,
                restoreUri,
            )
            when (restoreAction) {
                MobileReaderSessionRestoreAction.NONE -> return@launch
                MobileReaderSessionRestoreAction.CLEAR_PERSISTED_SESSION -> {
                    Timber.tag("ReaderRestore")
                        .w("Skipping stale or unavailable restore for bookId=$restoreBookId.")
                    clearPersistedReaderSession()
                    return@launch
                }
                MobileReaderSessionRestoreAction.RESTORE -> Unit
            }
            checkNotNull(item)
            checkNotNull(restoreUri)

            when {
                item.type in PDF_VIEWER_FILE_TYPES -> {
                    _internalState.update { state ->
                        if (state.selectedBookId != null || state.selectedPdfUri != null || state.selectedEpubUri != null) {
                            state
                        } else {
                            markReaderSessionReady(
                                startReaderSession(state, item.bookId, item.type),
                                item.bookId,
                            ).copy(
                                selectedPdfUri = restoreUri,
                                selectedEpubBook = null,
                                selectedEpubUri = null,
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
                            startReaderSession(state, item.bookId, item.type).copy(
                                selectedPdfUri = null,
                                selectedEpubBook = null,
                                selectedEpubUri = restoreUri,
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
                                markReaderSessionReady(state, item.bookId)
                                    .copy(selectedEpubBook = restoredBook)
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
                                markReaderSessionFailed(
                                    state,
                                    item.bookId,
                                    appContext.getString(R.string.error_load_file, error.message),
                                    closeReader = true,
                                ).copy(
                                    selectedEpubUri = null,
                                    selectedEpubBook = null,
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
                    if (it.selectedBookId == bookId) {
                        startReaderSession(it, bookId, type)
                    } else {
                        it
                    }
                }

                runCatching {
                    val item = bookStore.getFileByBookId(bookId)
                    restoreEpubReaderBook(
                        type = item?.type ?: type,
                        bookId = bookId,
                        displayName = item?.displayName ?: displayName,
                        uri = uri
                    )
                }.onSuccess { restoredBook ->
                    _internalState.update {
                        if (it.selectedBookId == bookId && it.selectedEpubUri == uri) {
                            markReaderSessionReady(it, bookId)
                                .copy(selectedEpubBook = restoredBook)
                        } else {
                            it
                        }
                    }
                    Timber.tag("EpubRecovery").i("Recovered missing extracted content for $bookId")
                }.onFailure { error ->
                    Timber.tag("EpubRecovery").e(error, "Failed to recover missing extracted content for $bookId")
                    _internalState.update {
                        if (it.selectedBookId == bookId && it.selectedEpubUri == uri) {
                            markReaderSessionFailed(
                                it,
                                bookId,
                                appContext.getString(R.string.error_load_file, error.message),
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

    private val fontsRepository = appGraph.fontsRepository

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
            googleDriveRepository.getFilesOrThrow(accessToken).files.associateBy { it.name }

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
            @Suppress("UnusedVariable", "Unused") val item = bookStore.getFileByBookId(bookId) ?: return@launch

            Timber.d("Deleting book permanently from reader: $bookId")

            cleanupBookDataLocally(bookId)
            bookStore.deleteFilePermanently(listOf(bookId))

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
        customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
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
                        highlights = highlights,
                        customHighlightColors = customHighlightColors
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
                withContext(Dispatchers.IO) {
                    copyUriBytes(sourceUri, destUri)
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

    internal fun trackExternalOpenForClose(
        bookId: String,
        importedCopyUriString: String?,
        isTemporaryExternalIntent: Boolean
    ) {
        if (isTemporaryExternalIntent) {
            temporaryExternalSessionBookId = bookId
            if (importedCopyUriString != null) {
                externalOpenedBookId = bookId
                markPendingExternalFileRemoval(bookId, importedCopyUriString)
            }
            return
        }

        externalOpenedBookId = bookId
        if (
            importedCopyUriString != null &&
            prefs.getString(KEY_EXTERNAL_FILE_BEHAVIOR, EXTERNAL_FILE_BEHAVIOR_ASK) == "DELETE"
        ) {
            markPendingExternalFileRemoval(bookId, importedCopyUriString)
        }
    }

    data class PreparedAnnotationExport(
        val fileName: String,
        val contents: String
    )

    fun prepareAnnotationExport(
        item: RecentFileItem,
        format: AnnotationExportFormat,
        onReady: (PreparedAnnotationExport) -> Unit
    ) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(isLoading = true, bannerMessage = BannerMessage(appContext.getString(R.string.banner_exporting_annotations)))
            }
            try {
                val latestItem = bookStore.getFileByBookId(item.bookId) ?: item
                val document = buildAnnotationExportDocument(latestItem)
                val exportText = AnnotationExportFormatter.render(document, format)
                if (exportText.isBlank()) {
                    showBanner(appContext.getString(R.string.banner_no_annotations_to_export), isError = true)
                    return@launch
                }
                onReady(
                    PreparedAnnotationExport(
                        fileName = AnnotationExportFormatter.suggestedFileName(document.bookTitle, format),
                        contents = exportText
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare annotation export")
                showBanner(appContext.getString(R.string.error_exporting_annotations, e.localizedMessage ?: e.message.orEmpty()), isError = true)
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveAnnotationExport(contents: String, destUri: Uri) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(isLoading = true, bannerMessage = BannerMessage(appContext.getString(R.string.banner_exporting_annotations)))
            }
            try {
                if (contents.isBlank()) {
                    showBanner(appContext.getString(R.string.banner_no_annotations_to_export), isError = true)
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(destUri)?.use { output ->
                        output.write(contents.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open destination file.")
                }
                showBanner(appContext.getString(R.string.banner_annotations_exported))
            } catch (e: Exception) {
                Timber.e(e, "Failed to export annotations")
                showBanner(appContext.getString(R.string.error_exporting_annotations, e.localizedMessage ?: e.message.orEmpty()), isError = true)
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun buildAnnotationExportDocument(item: RecentFileItem): AnnotationExportDocument {
        val title = item.cardTitle(uiState.value.usePdfFileNameAsDisplayName)
        return when (item.type) {
            FileType.EPUB,
            FileType.MOBI,
            FileType.MD,
            FileType.TXT,
            FileType.HTML,
            FileType.FB2,
            FileType.DOCX,
            FileType.ODT,
            FileType.FODT,
            FileType.PPTX -> AnnotationExportFormatter.fromEpubHighlights(
                bookTitle = title,
                sourceType = item.type,
                highlights = EpubAnnotationSerializer.parseHighlightsJson(item.highlightsJson)
            )
            FileType.PDF -> AnnotationExportFormatter.fromPdfAnnotations(
                bookTitle = title,
                annotations = pdfHighlightRepository.loadHighlights(item.bookId).map { it.toSharedPdfHighlightAnnotation() }
            )
            else -> AnnotationExportDocument(title, item.type, emptyList())
        }
    }

    fun saveOriginalFile(sourceUri: Uri, destUri: Uri) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(isLoading = true, bannerMessage = BannerMessage(appContext.getString(R.string.banner_saving_original_file)))
            }
            try {
                withContext(Dispatchers.IO) {
                    copyUriBytes(sourceUri, destUri)
                }
                showBanner(appContext.getString(R.string.banner_original_file_saved))
            } catch (e: Exception) {
                Timber.e(e, "Failed to save original file")
                showBanner(appContext.getString(R.string.error_saving_file, e.localizedMessage ?: e.message.orEmpty()), isError = true)
            } finally {
                _internalState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun togglePinForContextualItems(isHome: Boolean) {
        if (_internalState.value.contextualActionItems.isEmpty()) return

        var pinsToPersist: Set<String> = emptySet()
        _internalState.update { state ->
            val updated = togglePinsForSelectedBooks(
                current = state,
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
        customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
        includeAnnotations: Boolean,
        filename: String,
        bookId: String? = null
    ) {
        withContext(Dispatchers.IO) {
            val resolvedBookId =
                bookId ?: bookStore.getFileByUri(sourceUri.toString())?.bookId
                ?: getFastFileId(appContext, sourceUri)

            try {
                val artifact = AndroidShareArtifactManager.createSuspending(appContext, filename, write = { outputStream ->
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
                            highlights = highlights,
                            customHighlightColors = customHighlightColors
                        )
                    } else {
                        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                            input.copyTo(outputStream)
                        }
                    }
                })

                val shareIntent = AndroidShareArtifactManager.buildShareIntent(
                    artifact = artifact,
                    mimeType = "application/pdf",
                    title = artifact.fileName,
                    subject = appContext.getString(R.string.share_subject, artifact.fileName),
                )

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

    suspend fun shareOriginalFile(
        activityContext: Context,
        sourceUri: Uri,
        fileType: FileType,
        filename: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val artifact = AndroidShareArtifactManager.create(appContext, filename, write = { output ->
                    appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                        input.copyTo(output)
                    } ?: error("Could not open source file.")
                })

                val mimeType = SharedFileCapabilities.mimeTypeFor(fileType) ?: "application/octet-stream"
                val shareIntent = AndroidShareArtifactManager.buildShareIntent(
                    artifact = artifact,
                    mimeType = mimeType,
                    title = artifact.fileName,
                    subject = appContext.getString(R.string.share_subject, artifact.fileName),
                )
                val chooser = Intent.createChooser(shareIntent, appContext.getString(R.string.share_file_chooser_title))
                if (activityContext !is android.app.Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                withContext(Dispatchers.Main) { activityContext.startActivity(chooser) }
            } catch (e: Exception) {
                Timber.e(e, "Share original file failed")
                showBanner(appContext.getString(R.string.error_share_failed, e.localizedMessage ?: e.message.orEmpty()), isError = true)
            }
        }
    }

    private fun copyUriBytes(sourceUri: Uri, destUri: Uri) {
        val contentResolver = appContext.contentResolver
        contentResolver.openInputStream(sourceUri)?.use { input ->
            contentResolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            } ?: error("Could not open destination file.")
        } ?: error("Could not open source file.")
    }

    private fun queueCloudMetadataUpload(bookId: String, reason: String, debounce: Boolean = true) {
        if (!uiState.value.isSyncEnabled) return
        cloudMetadataUploadJobs.remove(bookId)?.cancel()
        val job = viewModelScope.launch {
            if (debounce) delay(CLOUD_METADATA_UPLOAD_DEBOUNCE_MILLIS)
            val latest = bookStore.getFileByBookId(bookId) ?: run {
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

    suspend fun onPdfSidecarsCommitted(bookId: String, reason: String, immediate: Boolean) {
        val book = bookStore.getFileByBookId(bookId)
        if (book?.sourceFolderUri != null) {
            FolderAnnotationExportWorker.markPending(
                context = appContext,
                bookId = bookId,
                reason = reason,
                immediate = immediate,
            )
        }
        queuePdfSidecarCloudUpload(bookId)
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
                                googleDriveRepository.getFilesOrThrow(accessToken)
                                    .files
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
                        bookStore.addRecentFile(merged)
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
                val sidecars = androidPdfCloudSidecarInventory(
                    inkFile, deletedInkFile, richTextFile, layoutFile, textBoxFile, highlightFile
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
                                    folderMirrorStore.importAnnotationBundle(
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

                val latestLocalForMetadata = bookStore.getFileByBookId(bookForMetadata.bookId)
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
                bookStore.addRecentFile(
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
                bookStore.markAsNotRecent(bookIdsToHide)
                _internalState.update { it.withClearedLibraryBookSelection() }

                if (uiState.value.isSyncEnabled && googleDriveRepository.hasDrivePermissions(
                        appContext
                    )
                ) {
                    bookIdsToHide.forEach { bookId ->
                        val updatedItem = bookStore.getFileByBookId(bookId)
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
        val isTemporaryExternalSession = closingBookId != null && closingBookId == temporaryExternalSessionBookId
        logCloudSyncTrace {
            "android.reader.close_request book=$closingBookId uri=${uriString.cloudSyncPreview()} sync=${uiState.value.isSyncEnabled}"
        }
        cloudFolderLogD(
            "event=reader_close_start operation=${cloudFolderOperationId("reader-close", closingBookId.orEmpty(), uriString.orEmpty())} " +
                "correlation=${cloudFolderSyncCorrelationId("reader-close", closingBookId.orEmpty(), uriString.orEmpty())} " +
                "book=${cloudFolderSafeId(closingBookId)} source=${cloudFolderSafeUri(uriString?.toUri())} " +
                "syncEnabled=${uiState.value.isSyncEnabled}",
        )

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

        _internalState.update { current ->
            closeReaderSession(current).copy(
                selectedPdfUri = null,
                selectedEpubUri = null,
                selectedEpubBook = null,
                isTemporaryExternalOpen = false,
                initialLocator = null,
                initialPageInBook = null,
                initialPageInBookIsExplicit = false,
                isOpeningFromTtsNotification = false
            )
        }
        if (!isTemporaryExternalSession) {
            clearPersistedReaderSession()
        }

        var removesExternalFileOnClose = false
        if (closingBookId != null && (closingBookId == externalOpenedBookId || isTemporaryExternalSession)) {
            val behavior = if (isTemporaryExternalSession) {
                EXTERNAL_FILE_BEHAVIOR_TEMPORARY
            } else {
                prefs.getString(KEY_EXTERNAL_FILE_BEHAVIOR, EXTERNAL_FILE_BEHAVIOR_ASK) ?: EXTERNAL_FILE_BEHAVIOR_ASK
            }
            when (mobileExternalFileCloseAction(behavior, isTemporaryExternalSession)) {
                MobileExternalFileCloseAction.PROMPT -> {
                    _internalState.update { it.copy(showExternalFileSavePromptFor = closingBookId) }
                }
                MobileExternalFileCloseAction.DELETE -> {
                    removesExternalFileOnClose = true
                    if (behavior == EXTERNAL_FILE_BEHAVIOR_TEMPORARY) {
                        val shouldDeleteImportedCopy = closingBookId == externalOpenedBookId
                        if (shouldDeleteImportedCopy) {
                            viewModelScope.launch {
                                deletePendingExternalFileRemoval(PendingExternalFileRemoval(closingBookId, uriString))
                                _temporaryExternalOpenFinished.send(Unit)
                            }
                        } else {
                            viewModelScope.launch {
                                _temporaryExternalOpenFinished.send(Unit)
                            }
                        }
                    } else {
                        deletePendingExternalFileRemoval(closingBookId, uriString)
                    }
                }
                MobileExternalFileCloseAction.KEEP -> {
                    clearPendingExternalFileRemovals(setOf(closingBookId))
                }
            }
            externalOpenedBookId = null
            temporaryExternalSessionBookId = null
        }

        if (uriString != null && !removesExternalFileOnClose) {
            viewModelScope.launch {
                // Reader callbacks save position/bookmarks asynchronously.
                // Await the writes queued before close, then read a fresh
                // Room row for both the main sync payload and the folder
                // sidecar snapshot.
                val closeOperation = cloudFolderOperationId("reader-close", closingBookId.orEmpty(), uriString)
                val closeCorrelation = cloudFolderSyncCorrelationId("reader-close", closingBookId.orEmpty(), uriString)
                cloudFolderLogD(
                    "event=reader_state_wait_start operation=$closeOperation correlation=$closeCorrelation " +
                        "book=${cloudFolderSafeId(closingBookId)} source=${cloudFolderSafeUri(uriString.toUri())}",
                )
                awaitReaderStateWrites(uriString, closingBookId)
                cloudFolderLogD(
                    "event=reader_state_wait_end operation=$closeOperation correlation=$closeCorrelation " +
                        "book=${cloudFolderSafeId(closingBookId)} result=complete",
                )
                val freshBook = bookStore.getFileByUri(uriString)
                    ?: selectedBookRowForManagedFile(uriString.toUri())
                freshBook?.let {
                    cloudFolderLogD(
                        "event=reader_close_snapshot operation=$closeOperation correlation=$closeCorrelation " +
                            "book=${cloudFolderSafeId(it.bookId)} source=${cloudFolderSafeUri(it.sourceFolderUri?.toUri())} " +
                            "readTs=${it.effectiveReadingPositionModifiedTimestamp()} page=${it.lastPage ?: "none"} " +
                            "chapter=${it.lastChapterIndex ?: "none"} block=${it.locatorBlockIndex ?: "none"} " +
                            "char=${it.locatorCharOffset ?: "none"} progress=${it.progressPercentage ?: "none"} " +
                            "folderBook=${it.sourceFolderUri != null}",
                    )
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
                            .d("Book closed (Folder Linked), syncing metadata and scheduling annotations: ${it.bookId}")
                        val metadataSaved = folderMirrorStore.syncLocalMetadataToFolder(it.bookId, force = true)
                        cloudFolderLogD(
                            "event=reader_close_sidecar_commit operation=$closeOperation correlation=$closeCorrelation " +
                                "book=${cloudFolderSafeId(it.bookId)} result=${if (metadataSaved) "success" else "failure"}",
                        )
                        if (!metadataSaved) {
                            Timber.tag("FolderAnnotationSync").w(
                                "Book close metadata sidecar write failed; keeping local state for retry: ${it.bookId}"
                            )
                        }
                        FolderAnnotationExportWorker.markPending(
                            context = appContext,
                            bookId = it.bookId,
                            reason = "reader_close",
                            immediate = true,
                        )
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
                            bookStore.getFileByBookId(bookId)?.uriString?.let { uriString ->
                                bookStore.updateEpubReadingPosition(uriString, locator, cfi, 0f)
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
        val decodedFolders = SyncedFolderPrefs.decodeSyncedFolders(
            jsonString = jsonString,
            legacyUri = oldUri,
            legacyLastScanTime = prefs.getLong(SyncedFolderPrefs.KEY_LEGACY_LAST_FOLDER_SCAN_TIME, 0L),
            legacyNameResolver = { uri -> getDisplayPathFromUri(appContext, uri) }
        )
        val folders = decodedFolders.map { folder ->
            val existingRootId = folder.cloudRootId
            if (existingRootId?.isNotBlank() == true) {
                folder.copy(cloudRootId = existingRootId.trim())
            } else {
                // Migrate legacy URI-only bindings to a fresh logical root.
                // The random seed is never uploaded and the generated ID is
                // persisted locally alongside the provider URI.
                folder.copy(cloudRootId = cloudFolderRootId("android-root:${UUID.randomUUID()}"))
            }
        }
        if (jsonString != null && folders != decodedFolders) {
            saveSyncedFoldersToPrefs(folders)
        }

        if (jsonString == null && prefs.contains(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI) && oldUri != null) {
            saveSyncedFoldersToPrefs(folders)
            prefs.edit {
                remove(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI)
                remove(SyncedFolderPrefs.KEY_LEGACY_LAST_FOLDER_SCAN_TIME)
            }
        }
        val appManagedFolders = authRepository.getSignedInUser()?.uid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { accountId ->
                CloudFolderAppStoragePrefs.load(appContext, accountId)
                    .map { it.toSyncedFolder(appContext.filesDir) }
            }
            .orEmpty()
        return (folders + appManagedFolders).distinctBy { it.uriString }
    }

    private fun saveSyncedFoldersToPrefs(folders: List<SyncedFolder>) {
        prefs.edit {
            putString(
                SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON,
                // App-private cloud materializations have a separate
                // account-scoped registry. Keeping them out of this legacy
                // JSON means older builds continue to see only SAF folders.
                SyncedFolderPrefs.encodeSyncedFolders(
                    folders.filterNot { it.isAppManaged || it.isCloudPlaceholder }
                )
            )
        }
    }

    /**
     * Cloud-folder mutations require both the Pro build and the current
     * account entitlement. UI checks protect normal taps; these helpers are
     * the final guard for picker/work callbacks that may outlive a session.
     */
    private fun activeCloudFolderSyncAccountId(): String? {
        if (!BuildConfig.IS_PRO || !_internalState.value.isProUser) return null
        return _internalState.value.currentUser?.uid?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun isCloudFolderSyncAvailableFor(accountId: String): Boolean =
        activeCloudFolderSyncAccountId() == accountId.trim().takeIf { it.isNotBlank() }

    /** Refresh repository-backed folder counts and any durable incoming prompt. */
    fun refreshCloudFolderSyncState() {
        val accountId = _internalState.value.currentUser?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
        if (accountId == null) {
            _cloudFolderRootStats.value = emptyMap()
            _cloudFolderLocalInventories.value = emptyMap()
            _cloudFolderRoots.value = emptyList()
            _cloudFolderBindings.value = emptyMap()
            _cloudFolderSyncProgress.value = emptyMap()
            _cloudFolderConflicts.value = emptyList()
            _incomingCloudFolderPrompt.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repository = CloudFolderSyncRepository(appContext, accountId)
                val roots = repository.getRoots().filterNot { it.isDeleted }
                val bindings = repository.getBindingsForDevice()
                    .associateBy { it.rootId }
                val localInventories = repository.getLocalInventoriesForDevice()
                val syncProgress = repository.getProgressForAccount()
                val pendingRootIds = CloudFolderSyncPrefs.pendingIncomingRootIds(appContext, accountId)
                val promptRoot = roots
                    .asSequence()
                    // Pending state is authoritative until dismissal is
                    // persisted. This deliberately also covers a partial
                    // choice write (binding saved, preference write failed)
                    // so the global prompt remains retryable.
                    .filter { it.rootId in pendingRootIds }
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.rootId }))
                    .firstOrNull()
                val stats = roots.associate { it.rootId to it.stats }
                // A sign-in transition can race this refresh. Never publish
                // state belonging to the account that is no longer active.
                if (_internalState.value.currentUser?.uid?.trim() != accountId) return@launch
                // A KEEP_OFFLINE binding has no SAF URI, so its folder-tab
                // entry is held in a separate account-scoped registry. The
                // existence check also repairs entries created by an older
                // build that materialized the app-private tree before the
                // folder-tab projection existed.
                bindings.values
                    .filter { it.materializationMode == CloudFolderMaterializationMode.KEEP_OFFLINE }
                    .forEach { binding ->
                        val root = runCatching {
                            cloudFolderAppRootDirectory(appContext.filesDir, binding.rootId)
                        }.getOrNull()
                        if (root?.isDirectory == true &&
                            roots.any { it.rootId == binding.rootId } &&
                            !CloudFolderAppStoragePrefs.contains(appContext, accountId, binding.rootId)
                        ) {
                            val rootName = roots.firstOrNull { it.rootId == binding.rootId }?.name
                                ?: "Cloud folder"
                            CloudFolderAppStoragePrefs.upsert(
                                context = appContext,
                                accountId = accountId,
                                rootId = binding.rootId,
                                name = rootName,
                            )
                        }
                    }
                val appManagedFolders = CloudFolderAppStoragePrefs.load(appContext, accountId)
                    .map { it.toSyncedFolder(appContext.filesDir) }
                val appManagedRootIds = appManagedFolders
                    .mapNotNullTo(hashSetOf()) { it.cloudRootId?.trim()?.takeIf { id -> id.isNotBlank() } }
                val userFolders = _internalState.value.syncedFolders
                    .filterNot { it.isAppManaged || it.isCloudPlaceholder }
                val placeholderRootIds = (
                    CloudFolderSyncPrefs.discoveredIncomingRootIds(
                        context = appContext,
                        accountId = accountId,
                    ) + pendingRootIds
                ).distinct()
                val placeholderFolders = placeholderRootIds.asSequence()
                    .mapNotNull { rootId ->
                        val root = roots.firstOrNull { it.rootId == rootId } ?: return@mapNotNull null
                        if (rootId in appManagedRootIds || userFolders.any { it.cloudRootId == rootId }) {
                            return@mapNotNull null
                        }
                        SyncedFolder(
                            uriString = "cloud-folder-placeholder:$rootId",
                            name = root.name,
                            lastScanTime = 0L,
                            allowedFileTypes = ANDROID_SYNCABLE_FILE_TYPES,
                            localSyncEnabled = false,
                            cloudRootId = rootId,
                            isCloudPlaceholder = true,
                        )
                    }
                    .toList()
                val configuredFolders = (userFolders + appManagedFolders + placeholderFolders)
                    .distinctBy { it.uriString }
                _internalState.update { current ->
                    if (current.currentUser?.uid?.trim() == accountId) {
                        current.copy(syncedFolders = configuredFolders)
                    } else {
                        current
                    }
                }
                _cloudFolderRootStats.value = stats
                _cloudFolderLocalInventories.value = localInventories
                _cloudFolderRoots.value = roots.sortedWith(
                    compareBy<CloudFolderRoot> { it.name.lowercase() }.thenBy { it.rootId },
                )
                _cloudFolderBindings.value = bindings
                _cloudFolderSyncProgress.value = syncProgress
                _cloudFolderConflicts.value = repository.getConflictUiItems()
                _incomingCloudFolderPrompt.value = promptRoot?.let {
                    CloudFolderIncomingFolderPrompt(
                        root = it,
                        sourceDeviceName = it.createdByDeviceId.takeIf { device -> device.isNotBlank() },
                    )
                }
                scheduleLocalCloudFolderInventories(
                    accountId = accountId,
                    folders = configuredFolders,
                    roots = roots,
                    localInventories = localInventories,
                )
            } catch (error: Exception) {
                Timber.w(error, "Unable to refresh cloud-folder repository state")
            }
        }
    }

    /**
     * Refresh local folder facts independently of cloud selection and library
     * indexing. The inventory is metadata-only (no hashing or file copies),
     * runs off the UI dispatcher, and has a durable terminal state so a
     * failed provider does not leave the screen saying "Scanning" forever.
     */
    private fun scheduleLocalCloudFolderInventories(
        accountId: String,
        folders: List<SyncedFolder>,
        roots: List<CloudFolderRoot>,
        localInventories: Map<String, CloudFolderLocalInventory>,
    ) {
        val rootIds = roots.mapTo(hashSetOf()) { it.rootId }
        val now = System.currentTimeMillis()
        folders.forEach { folder ->
            // A ghost entry is an account inventory marker only. It has no
            // local URI and must never trigger SAF enumeration or a local
            // inventory row before the user materializes it.
            if (folder.isCloudPlaceholder) return@forEach
            val rootId = folder.cloudRootId?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            val localUri = folder.uriString.trim().takeIf { it.isNotBlank() } ?: return@forEach
            if (rootId !in rootIds) return@forEach
            val existing = localInventories[rootId]
            val shouldRefresh = when {
                existing == null -> true
                existing.state == CloudFolderLocalInventoryState.SCANNING ->
                    now - existing.updatedAt > LOCAL_FOLDER_INVENTORY_STALE_MILLIS
                existing.state == CloudFolderLocalInventoryState.FAILED ->
                    now - existing.updatedAt > LOCAL_FOLDER_INVENTORY_RETRY_MILLIS
                else -> now - existing.scannedAt > LOCAL_FOLDER_INVENTORY_REFRESH_MILLIS
            }
            if (!shouldRefresh) return@forEach
            val key = "$accountId\u0000$rootId"
            if (!activeLocalInventoryScans.add(key)) return@forEach
            viewModelScope.launch(Dispatchers.IO) {
                val repository = CloudFolderSyncRepository(appContext, accountId)
                try {
                    val previous = repository.getLocalInventory(rootId)
                    val scanStartedAt = System.currentTimeMillis()
                    repository.saveLocalInventory(
                        CloudFolderLocalInventory(
                            rootId = rootId,
                            deviceId = repository.deviceId,
                            state = CloudFolderLocalInventoryState.SCANNING,
                            fileCount = previous?.fileCount ?: 0,
                            directoryCount = previous?.directoryCount ?: 0,
                            totalBytes = previous?.totalBytes ?: 0L,
                            sizeComplete = previous?.sizeComplete ?: true,
                            scannedAt = previous?.scannedAt ?: 0L,
                            updatedAt = scanStartedAt,
                        )
                    )
                    CloudFolderSyncEvents.notifyStateChanged()
                    val result = if (folder.isAppManaged) {
                        val appRoot = runCatching {
                            cloudFolderAppRootDirectory(appContext.filesDir, rootId)
                        }.getOrNull()
                        if (appRoot == null) {
                            com.aryan.reader.data.CloudFolderSafInventoryResult(
                                fileCount = 0,
                                directoryCount = 0,
                                totalBytes = 0L,
                                sizeComplete = false,
                                complete = false,
                                scannedAt = System.currentTimeMillis(),
                                errorMessage = "App-private cloud folder has an invalid root ID",
                            )
                        } else {
                            CloudFolderSafScanner.scanAppStorageInventory(
                                root = appRoot,
                                rootId = rootId,
                                now = System.currentTimeMillis(),
                            )
                        }
                    } else {
                        CloudFolderSafScanner.scanInventory(
                            context = appContext,
                            rootUri = Uri.parse(localUri),
                            rootId = rootId,
                            now = System.currentTimeMillis(),
                        )
                    }
                    val completedAt = System.currentTimeMillis()
                    if (result.complete) {
                        repository.saveLocalInventory(
                            CloudFolderLocalInventory(
                                rootId = rootId,
                                deviceId = repository.deviceId,
                                state = CloudFolderLocalInventoryState.READY,
                                fileCount = result.fileCount,
                                directoryCount = result.directoryCount,
                                totalBytes = result.totalBytes,
                                sizeComplete = result.sizeComplete,
                                scannedAt = result.scannedAt,
                                updatedAt = completedAt,
                            )
                        )
                    } else {
                        // Preserve the last known totals, but expose a
                        // terminal failure state so the UI can offer a retry
                        // instead of presenting an endless spinner.
                        repository.saveLocalInventory(
                            CloudFolderLocalInventory(
                                rootId = rootId,
                                deviceId = repository.deviceId,
                                state = CloudFolderLocalInventoryState.FAILED,
                                fileCount = previous?.fileCount ?: result.fileCount,
                                directoryCount = previous?.directoryCount ?: result.directoryCount,
                                totalBytes = previous?.totalBytes ?: result.totalBytes,
                                sizeComplete = previous?.sizeComplete ?: result.sizeComplete,
                                scannedAt = previous?.scannedAt ?: 0L,
                                updatedAt = completedAt,
                                errorStatus = cloudFolderErrorStatus(result.errorMessage),
                            )
                        )
                    }
                    CloudFolderSyncEvents.notifyStateChanged()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val previous = runCatching { repository.getLocalInventory(rootId) }.getOrNull()
                    runCatching {
                        repository.saveLocalInventory(
                            CloudFolderLocalInventory(
                                rootId = rootId,
                                deviceId = repository.deviceId,
                                state = CloudFolderLocalInventoryState.FAILED,
                                fileCount = previous?.fileCount ?: 0,
                                directoryCount = previous?.directoryCount ?: 0,
                                totalBytes = previous?.totalBytes ?: 0L,
                                sizeComplete = previous?.sizeComplete ?: true,
                                scannedAt = previous?.scannedAt ?: 0L,
                                updatedAt = System.currentTimeMillis(),
                                errorStatus = cloudFolderErrorStatus(error),
                            )
                        )
                        CloudFolderSyncEvents.notifyStateChanged()
                    }.onFailure { persistError ->
                        Timber.w(persistError, "Unable to persist local folder inventory failure")
                    }
                } finally {
                    activeLocalInventoryScans.remove(key)
                }
            }
        }
    }

    /** Hide the global prompt for now; keep an unmaterialized Folders entry. */
    fun dismissIncomingCloudFolderPrompt(
        rootId: String,
        onPersisted: (Boolean) -> Unit = {},
    ) {
        val accountId = activeCloudFolderSyncAccountId() ?: run {
            reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repository = CloudFolderSyncRepository(appContext, accountId)
                val root = repository.getRoot(rootId)?.takeUnless { it.isDeleted }
                if (root == null || !isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) {
                    refreshCloudFolderSyncState()
                    reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
                    return@launch
                }
                CloudFolderSyncPrefs.snoozeIncomingPrompt(
                    context = appContext,
                    accountId = accountId,
                    rootId = root.rootId,
                    revision = root.manifestRevision,
                )
                CloudFolderSyncEvents.notifyStateChanged()
                refreshCloudFolderSyncState()
                reportIncomingCloudFolderPersistence(onPersisted, succeeded = true)
            } catch (error: Exception) {
                Timber.w(error, "Unable to persist incoming cloud-folder dismissal root=$rootId")
                // Re-read the durable prompt state so a transient failure does
                // not leave the global dialog hidden until the next app start.
                refreshCloudFolderSyncState()
                reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
            }
        }
    }

    private suspend fun registerLocalCloudFolders(accountId: String) {
        val repository = CloudFolderSyncRepository(appContext, accountId)
        _internalState.value.syncedFolders
            .filterNot { it.isAppManaged || it.isCloudPlaceholder }
            .forEach { folder ->
            val rootId = folder.cloudRootId?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            try {
                repository.registerLocalFolder(
                    localUri = folder.uriString,
                    name = folder.name,
                    rootId = rootId,
                    // A configured local source is a mirror on this device;
                    // incoming roots choose other materialization modes.
                    materializationMode = com.aryan.reader.shared.CloudFolderMaterializationMode.LOCAL_MIRROR,
                )
            } catch (error: Exception) {
                Timber.w(error, "Unable to register local cloud-folder root=$rootId")
            }
        }
    }

    /**
     * [indexInLibrary] preserves the legacy library flow by default. The
     * cloud-folder settings flow opts out so a direct cloud upload never
     * imports files into the Reader library as a side effect.
     */
    fun addSyncedFolder(folderUri: Uri, indexInLibrary: Boolean = true) {
        // The cloud-settings picker uses indexInLibrary=false. Keep legacy
        // local-folder setup available, but reject that cloud path if the
        // account/build entitlement changed while the picker was open.
        val cloudAccountId = if (!indexInLibrary) activeCloudFolderSyncAccountId() else null
        if (!indexInLibrary && cloudAccountId == null) {
            Timber.w("Ignoring cloud-folder add without an entitled Pro account")
            return
        }
        val currentFolders = _internalState.value.syncedFolders
        when (syncedFolderAddDecision(currentFolders, folderUri.toString())) {
            SyncedFolderAddDecision.LIMIT_REACHED -> {
                showBanner(
                    appContext.getString(R.string.error_folder_limit_reached, MAX_SYNCED_FOLDER_COUNT),
                    isError = true,
                )
                return
            }
            SyncedFolderAddDecision.ALREADY_SYNCED -> {
                showBanner(appContext.getString(R.string.error_folder_already_synced), isError = true)
                return
            }
            SyncedFolderAddDecision.INVALID_URI -> return
            SyncedFolderAddDecision.ALLOWED -> Unit
        }

        viewModelScope.launch {
            try {
                if (!indexInLibrary &&
                    (cloudAccountId == null || !isCloudFolderSyncAvailableFor(cloudAccountId))
                ) {
                    Timber.w("Ignoring stale cloud-folder picker result")
                    return@launch
                }
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
                    localSyncEnabled = indexInLibrary,
                    cloudRootId = cloudFolderRootId("android-root:${UUID.randomUUID()}"),
                )
                val newStats = currentFolders.withSyncedFolder(newFolder)
                val accountId = if (!indexInLibrary) {
                    cloudAccountId
                } else {
                    _internalState.value.currentUser?.uid?.trim()?.takeIf { it.isNotBlank() }
                }
                if (!indexInLibrary &&
                    (accountId == null || !isCloudFolderSyncAvailableFor(accountId))
                ) {
                    Timber.w("Ignoring cloud-folder add after account entitlement changed")
                    return@launch
                }

                saveSyncedFoldersToPrefs(newStats)

                _internalState.update {
                    it.copy(
                        syncedFolders = newStats
                    )
                }

                if (accountId != null &&
                    (indexInLibrary || isCloudFolderSyncAvailableFor(accountId))
                ) {
                    try {
                        val cloudRepository = CloudFolderSyncRepository(appContext, accountId)
                        cloudRepository.registerLocalFolder(
                            localUri = newFolder.uriString,
                            name = newFolder.name,
                            rootId = newFolder.cloudRootId,
                            materializationMode = com.aryan.reader.shared.CloudFolderMaterializationMode.LOCAL_MIRROR,
                        )
                        if (!indexInLibrary) {
                            // The cloud-folder picker is an explicit opt-in.
                            // Select the new logical root immediately, while
                            // converting legacy EXCLUDED/ALL values to the
                            // new per-folder representation so the first
                            // upload cannot be silently skipped.
                            val newRootId = requireNotNull(newFolder.cloudRootId)
                            val knownRootIds = cloudRepository.getRoots().map { it.rootId } + newRootId
                            val nextSelection = CloudFolderSyncPrefs
                                .load(appContext, accountId)
                                .toExplicitSelection(knownRootIds)
                                .withRootIncluded(newRootId)
                            CloudFolderSyncPrefs.save(appContext, accountId, nextSelection)
                            CloudFolderSyncEvents.notifyStateChanged()
                        }
                    } catch (error: Exception) {
                        // Keep the local configuration usable; sign-in/startup
                        // registration retries the account-owned binding.
                        Timber.w(error, "Unable to register newly added cloud-folder root")
                    }
                    refreshCloudFolderSyncState()
                }

                if (indexInLibrary) {
                    triggerFolderSyncWorker(
                        metadataOnly = false,
                        showFeedback = true,
                        targetFolderUriString = newFolder.uriString
                    )
                } else if (accountId != null &&
                    isCloudFolderSyncAvailableFor(accountId) &&
                    uiState.value.isSyncEnabled &&
                    newFolder.cloudRootId?.let { CloudFolderSyncPrefs.load(appContext, accountId).includes(it) } == true
                ) {
                    // Direct-cloud mode scans/uploads the SAF tree without
                    // touching RecentFilesRepository or library indexing.
                    CloudFolderSyncWorker.enqueue(
                        appContext,
                        accountId = accountId,
                        rootId = newFolder.cloudRootId,
                        replace = true,
                    )
                }

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

            val currentFolders = _internalState.value.syncedFolders.withoutSyncedFolder(folder.uriString)

            saveSyncedFoldersToPrefs(currentFolders)
            _internalState.update { it.copy(syncedFolders = currentFolders) }

            folder.cloudRootId?.trim()?.takeIf { it.isNotBlank() }?.let { rootId ->
                _internalState.value.currentUser?.uid?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { accountId ->
                        try {
                            CloudFolderSyncRepository(appContext, accountId).detachLocalFolder(rootId)
                        } catch (error: Exception) {
                            Timber.w(error, "Unable to detach removed cloud-folder root=$rootId")
                        }
                    }
            }

            val filesToRemove = bookStore.getFilesBySourceFolder(folder.uriString)
            folderMirrorStore.deleteFilesBySourceFolder(folder.uriString)
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
            val previousFolders = _internalState.value.syncedFolders
            if (previousFolders.none { it.uriString == folder.uriString }) return@launch
            val currentFolders = previousFolders.withSyncedFolderLocalSync(folder.uriString, enabled)
            val updatedFolder = currentFolders.first { it.uriString == folder.uriString }
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
        // The legacy folder worker updates local-library sidecars, while the
        // account-scoped cloud-folder worker owns Drive manifests and
        // app-managed/KEEP_OFFLINE roots. Keep this visible action useful for
        // both kinds of folders without adding another user-facing banner.
        enqueueCloudFolderManualRefresh(reason = "folder_metadata")
        triggerFolderSyncWorker(metadataOnly = true, showFeedback = showFeedback)
    }

    fun scanSyncedFolder() {
        // A full local scan can discover file additions/deletions that must
        // also reach Drive. The cloud worker performs its own authenticated
        // inventory and keeps the same three-way conflict rules.
        enqueueCloudFolderManualRefresh(reason = "folder_scan")
        triggerFolderSyncWorker(metadataOnly = false, showFeedback = true)
    }

    /**
     * Schedule the account-level cloud-folder receive and send passes for a
     * user-visible manual refresh. These workers have no UI banner of their
     * own; folder status/progress remains the single cloud-folder feedback
     * surface, while the legacy worker keeps its existing banner behavior.
     *
     * PULL and PUSH use distinct account/root unique-work names. Replacing a
     * prior manual request makes an explicit refresh recover a stale/backoff
     * request, while WorkManager still coalesces duplicate requests of the
     * same direction and account scope.
     */
    private fun enqueueCloudFolderManualRefresh(reason: String) {
        val accountId = activeCloudFolderSyncAccountId() ?: run {
            cloudFolderLogD(
                "event=manual_refresh_skip reason=cloud_folder_unavailable trigger=$reason",
            )
            return
        }
        if (!isCloudFolderSyncEnabled(appContext)) {
            cloudFolderLogD(
                "event=manual_refresh_skip account=${cloudFolderSafeId(accountId)} " +
                    "reason=sync_disabled trigger=$reason",
            )
            return
        }
        cloudFolderLogI(
            "event=manual_refresh_enqueue account=${cloudFolderSafeId(accountId)} " +
                "trigger=$reason passes=pull,push",
        )
        CloudFolderSyncWorker.enqueuePull(
            context = appContext,
            accountId = accountId,
            replace = true,
        )
        CloudFolderSyncWorker.enqueue(
            context = appContext,
            accountId = accountId,
            direction = CloudFolderSyncDirection.LOCAL_TO_CLOUD,
            replace = true,
        )
    }

    private fun triggerFolderSyncWorker(
        metadataOnly: Boolean,
        showFeedback: Boolean,
        targetFolderUriString: String? = null
    ) {
        val allFolders = _internalState.value.syncedFolders
        val folders = if (targetFolderUriString.isNullOrBlank()) {
            allFolders.filter { it.localSyncEnabled && !it.isCloudPlaceholder }
        } else {
            allFolders.filter {
                it.uriString == targetFolderUriString && it.localSyncEnabled && !it.isCloudPlaceholder
            }
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
            val previousFolders = _internalState.value.syncedFolders
            if (previousFolders.any { it.uriString == folder.uriString }) {
                val currentFolders = previousFolders.withSyncedFolderFileTypes(
                    uriString = folder.uriString,
                    requestedFileTypes = newFilters,
                    supportedFileTypes = ANDROID_SYNCABLE_FILE_TYPES,
                )
                val sanitizedFilters = currentFolders
                    .first { it.uriString == folder.uriString }
                    .allowedFileTypes
                saveSyncedFoldersToPrefs(currentFolders)
                _internalState.update { it.copy(syncedFolders = currentFolders) }

                val filesToRemove = bookStore.getFilesBySourceFolder(folder.uriString)
                    .filter { it.type !in sanitizedFilters }

                if (filesToRemove.isNotEmpty()) {
                    Timber.d("Removing ${filesToRemove.size} files that no longer match the filter for folder ${folder.name}")
                    val idsToRemove = filesToRemove.map { it.bookId }

                    idsToRemove.forEach { bookId ->
                        cleanupBookDataLocally(bookId)
                    }
                    bookStore.deleteFilePermanently(idsToRemove)
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
                val filesToRemove = bookStore.getFilesBySourceFolder(folder.uriString)
                folderMirrorStore.deleteFilesBySourceFolder(folder.uriString)
                filesToRemove.forEach { cleanupBookDataLocally(it.bookId) }
                try {
                    appContext.contentResolver.releasePersistableUriPermission(
                        folder.uriString.toUri(), PERSISTED_URI_GRANT_FLAGS
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
                    googleDriveRepository.getFilesOrThrow(accessToken).files.associateBy {
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
                val currentUser = uiState.value.currentUser
                    ?: throw IllegalStateException("Missing signed-in user")
                CloudBookSyncBarrier.withAccountLock(currentUser.uid) {
                    val accessToken = googleDriveRepository.getAccessToken(appContext)
                        ?: throw IllegalStateException("Missing Drive access token")
                    // Prevent an individual-delete worker from publishing a
                    // tombstone while the explicit clear-all transaction is
                    // removing the account's remote data. The queue is
                    // cleared only after the remote clear succeeds below.
                    CloudBookDeleteWorker.cancelForAccount(appContext, currentUser.uid)
                    val result = CloudMaintenanceCoordinator(
                        deleteDrive = {
                            googleDriveRepository.deleteAllFiles(accessToken)
                        },
                        deleteFirestore = {
                            firestoreRepository.deleteAllUserFirestoreData(currentUser.uid)
                        },
                        clearLocal = {
                            bookArtifactStore.clearAllLocalData()
                            CloudBookDeleteWorker.cancelForAccount(appContext, currentUser.uid)
                            cloudBookDeletePersistence.clear(currentUser.uid)
                            prefs.edit { remove(KEY_LAST_SYNC_TIMESTAMP) }
                        },
                    ).clearAll(CloudMaintenanceIntent(currentUser.uid))

                    if (result is CloudMaintenanceResult.Succeeded) {
                        _internalState.update {
                            it.copy(
                                isLoading = false, bannerMessage = BannerMessage(appContext.getString(R.string.banner_cloud_local_data_cleared))
                            )
                        }
                    } else if (result is CloudMaintenanceResult.Failed) {
                        throw result.error
                    }
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
                bookArtifactStore.clearAllLocalData()
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
                    // AuthStateListener normally publishes the account, but
                    // the sign-in repository can also enrich it with the
                    // Google credential's profile photo before Firebase's
                    // listener callback runs. Publish the returned snapshot
                    // so the UI does not lose that photo during the handoff.
                    _internalState.update {
                        it.copy(
                            currentUser = user,
                            isLoading = false,
                        )
                    }
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
                val accountId = currentUser.uid.trim()
                // Cancel and clear the account's folder work before changing
                // auth state.  This prevents a queued request from a prior
                // account from being reused after the next sign-in, while
                // the worker's account check remains the final safety gate.
                CloudFolderSyncWorker.cancelForAccount(appContext, accountId)
                try {
                    withContext(Dispatchers.IO) {
                        CloudFolderSyncRepository(appContext, accountId).clearAccountState()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clear cloud-folder state for account $accountId")
                }
                CloudBookDeleteWorker.cancelForAccount(appContext, accountId)
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

    /**
     * Revoke cloud sync as soon as the backend says this account is no longer
     * entitled.  This is intentionally account-scoped so a stale profile
     * callback cannot disable a different account's persisted preference.
     */
    private fun disableCloudSyncAfterEntitlementLoss(accountId: String) {
        val normalizedAccountId = accountId.trim().takeIf { it.isNotBlank() } ?: return
        if (_internalState.value.currentUser?.uid?.trim() != normalizedAccountId) return

        prefs.edit { putBoolean(KEY_SYNC_ENABLED, false) }
        _internalState.update {
            if (it.currentUser?.uid?.trim() == normalizedAccountId) {
                it.copy(
                    isProUser = false,
                    isSyncEnabled = false,
                    isRequestingDrivePermission = false,
                )
            } else {
                it
            }
        }
        CloudFolderSyncWorker.cancelForAccount(appContext, normalizedAccountId)
    }

    fun setSyncEnabled(enabled: Boolean) {
        if (!enabled) {
            prefs.edit { putBoolean(KEY_SYNC_ENABLED, false) }
            _internalState.update { it.copy(isSyncEnabled = false) }

            // WorkManager requests can already be queued or running when the
            // switch changes. Cancel only the current account's cloud-folder
            // work; the worker also re-checks the persisted switch before it
            // touches Drive or the account database.
            _internalState.value.currentUser?.uid?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { accountId ->
                    CloudFolderSyncWorker.cancelForAccount(appContext, accountId)
                }
            return
        }

        if (!BuildConfig.IS_PRO || !uiState.value.isProUser) {
            Timber.d("Sync toggle blocked for free user.")
            _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_sync_pro_feature)) }
            return
        }

        prefs.edit { putBoolean(KEY_SYNC_ENABLED, true) }
        _internalState.update { it.copy(isSyncEnabled = true) }

        viewModelScope.launch {
            if (googleDriveRepository.hasDrivePermissions(appContext)) {
                syncWithCloud(showBanner = true)
                _internalState.value.currentUser?.uid?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { accountId ->
                        CloudFolderSyncWorker.enqueue(
                            appContext,
                            accountId = accountId,
                            replace = true,
                        )
                        CloudFolderSyncWorker.enqueuePull(
                            appContext,
                            accountId = accountId,
                            replace = true,
                        )
                    }
            } else {
                Timber.d("Requesting Drive permission from user.")
                _internalState.update { it.copy(isRequestingDrivePermission = true) }
            }
        }
    }

    /**
     * Account-level folder selection is separate from the legacy local-library
     * switch.  A newly installed device therefore remains EXCLUDED until the
     * user explicitly selects roots in the settings dialog.
     */
    fun cloudFolderSyncSelection(): CloudFolderSyncSelection =
        _internalState.value.currentUser?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { accountId -> CloudFolderSyncPrefs.load(appContext, accountId) }
            ?: CloudFolderSyncSelection.Default

    fun setCloudFolderSyncSelection(selection: CloudFolderSyncSelection) {
        val accountId = activeCloudFolderSyncAccountId() ?: run {
            Timber.w("Ignoring cloud-folder selection without an entitled Pro account")
            return
        }
        val normalized = selection.normalized()
        CloudFolderSyncPrefs.save(appContext, accountId, normalized)
        CloudFolderSyncEvents.notifyStateChanged()

        // Re-register local folders as logical roots before scheduling. A
        // selection must never be a no-op: after a root row was removed (for
        // example by "Delete from Drive") the persisted folder still carries
        // its cloud root ID, and without this re-registration the worker
        // would find zero selected roots and upload nothing.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { registerLocalCloudFolders(accountId) }
                .onFailure { error ->
                    Timber.w(error, "Unable to register local folders for cloud selection")
                }

            // Queue a durable folder pass so the persisted policy is observed
            // by the worker even if the process is killed after the dialog
            // closes. The cloud-folder transfer worker consumes this same
            // policy; the existing worker also refreshes the indexed folder
            // inventory.
            if (uiState.value.isSyncEnabled) {
                CloudFolderSyncWorker.enqueue(
                    appContext,
                    accountId = accountId,
                    replace = true,
                )
            }
        }
    }

    /**
     * Persist a conflict decision against its account-scoped snapshot and
     * schedule a normal three-way sync. The worker revalidates the snapshot
     * before applying the decision, so a newer local or cloud revision cannot
     * be overwritten by an old settings action.
     */
    fun resolveCloudFolderConflict(
        conflict: CloudFolderConflictUiItem,
        resolution: CloudFolderConflictResolution,
    ) {
        val accountId = activeCloudFolderSyncAccountId() ?: return
        val rootId = conflict.normalizedRootId.takeIf { it.isNotBlank() } ?: return
        val conflictId = conflict.conflictId.trim().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) return@launch
                val repository = CloudFolderSyncRepository(appContext, accountId)
                val persisted = repository.resolveConflict(
                    rootId = rootId,
                    conflictId = conflictId,
                    resolution = resolution,
                )
                if (!persisted) {
                    // The worker may have reconciled a newer snapshot while
                    // the dialog was open. Refresh to show the new decision
                    // state rather than reporting success for stale input.
                    refreshCloudFolderSyncState()
                    return@launch
                }
                if (isCloudFolderSyncAvailableFor(accountId) &&
                    uiState.value.isSyncEnabled &&
                    authRepository.getSignedInUser()?.uid?.trim() == accountId
                ) {
                    // NONE maps to the normal SYNC planner. It can upload
                    // local operations, materialize cloud operations, and
                    // consume persisted keep-local/keep-cloud/keep-both
                    // choices in one account-scoped pass.
                    CloudFolderSyncWorker.enqueue(
                        appContext,
                        accountId = accountId,
                        rootId = rootId,
                        direction = CloudFolderSyncDirection.NONE,
                        replace = true,
                    )
                }
                refreshCloudFolderSyncState()
            } catch (error: Exception) {
                Timber.e(error, "Unable to resolve cloud-folder conflict root=$rootId conflict=$conflictId")
                refreshCloudFolderSyncState()
            }
        }
    }

    /**
     * Re-open the durable incoming-folder decision from folder settings. This
     * is intentionally a state request, not a second dialog implementation:
     * AppNavigation remains the single owner of the prompt surface.
     */
    fun showIncomingCloudFolderPrompt(rootId: String) {
        val accountId = activeCloudFolderSyncAccountId() ?: return
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repository = CloudFolderSyncRepository(appContext, accountId)
                val root = repository.getRoot(normalizedRootId)?.takeUnless { it.isDeleted }
                    ?: return@launch
                if (!isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) return@launch
                _incomingCloudFolderPrompt.value = CloudFolderIncomingFolderPrompt(
                    root = root,
                    sourceDeviceName = root.createdByDeviceId.takeIf { it.isNotBlank() },
                )
            } catch (error: Exception) {
                Timber.w(error, "Unable to reopen incoming cloud-folder prompt root=$normalizedRootId")
            }
        }
    }

    /**
     * Detach a synced folder from this device only. The cloud copy is left
     * untouched, so the folder can be re-discovered and offered again through
     * the incoming-folder prompt later.
     */
    fun removeCloudFolderFromDevice(rootId: String) {
        val accountId = activeCloudFolderSyncAccountId() ?: return
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) return@launch
                val repository = CloudFolderSyncRepository(appContext, accountId)
                val binding = repository.getBinding(normalizedRootId)
                if (binding?.materializationMode == CloudFolderMaterializationMode.KEEP_OFFLINE) {
                    CloudFolderSyncWorker.clearOfflineMaterialization(appContext, normalizedRootId)
                    CloudFolderAppStoragePrefs.remove(appContext, accountId, normalizedRootId)
                }
                repository.removeBinding(normalizedRootId)
                repository.clearTransferState(normalizedRootId)
                CloudFolderSyncPrefs.save(
                    appContext,
                    accountId,
                    CloudFolderSyncPrefs.load(appContext, accountId)
                        .withoutRoot(normalizedRootId, repository.getRoots().map { it.rootId }),
                )
                CloudFolderSyncPrefs.forgetIncomingPrompt(appContext, accountId, normalizedRootId)
                CloudFolderSyncEvents.notifyStateChanged()
                refreshCloudFolderSyncState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Unable to remove cloud-folder binding root=$normalizedRootId")
                CloudFolderSyncEvents.notifyStateChanged()
                refreshCloudFolderSyncState()
            }
        }
    }

    /**
     * Delete a synced folder from Drive account-wide. The worker publishes a
     * tombstone so other devices observe the deletion, then clears this
     * device's local copy and bookkeeping.
     */
    fun deleteCloudFolderFromDrive(rootId: String) {
        val accountId = activeCloudFolderSyncAccountId() ?: return
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        CloudFolderSyncWorker.enqueueDeleteFolder(appContext, accountId, normalizedRootId)
        CloudFolderSyncEvents.notifyStateChanged()
        refreshCloudFolderSyncState()
    }

    /**
     * Change the materialization of a remote root from the persistent folder
     * inventory.  KEEP_OFFLINE is an explicit opt-in and schedules a pull;
     * CLOUD_ONLY removes the root from local transfer selection while keeping
     * its manifest available for future decisions.
     */
    fun setCloudFolderMaterializationMode(
        rootId: String,
        mode: CloudFolderMaterializationMode,
    ) {
        if (mode == CloudFolderMaterializationMode.LOCAL_MIRROR) {
            // A SAF grant must come from the platform picker, so callers use
            // the incoming prompt's bind action for LOCAL_MIRROR.
            Timber.w("Ignoring local-mirror mode without a SAF folder root=$rootId")
            return
        }
        val accountId = activeCloudFolderSyncAccountId() ?: return
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) return@launch
                val repository = CloudFolderSyncRepository(appContext, accountId)
                val root = repository.getRoot(normalizedRootId)?.takeUnless { it.isDeleted }
                    ?: return@launch
                val existing = repository.getBinding(normalizedRootId)
                if (existing?.materializationMode == CloudFolderMaterializationMode.LOCAL_MIRROR) {
                    // Never detach an explicitly selected SAF folder from a
                    // status button; that requires a separate destructive
                    // unbind flow.
                    return@launch
                }
                if (mode == CloudFolderMaterializationMode.CLOUD_ONLY &&
                    existing?.materializationMode == CloudFolderMaterializationMode.KEEP_OFFLINE
                ) {
                    try {
                        // The worker and this transition share a process-wide
                        // mutex, so a running pull cannot race deletion of
                        // the app-private tree. Keep the old binding until
                        // cleanup succeeds; the UI then remains truthful and
                        // offers a retry if storage removal fails.
                        CloudFolderSyncWorker.clearOfflineMaterialization(
                            context = appContext,
                            rootId = normalizedRootId,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val message = "Unable to remove offline copy: " +
                            (error.message?.takeIf { it.isNotBlank() } ?: "storage error")
                        Timber.e(error, "Unable to remove offline cloud-folder materialization root=$normalizedRootId")
                        repository.markBindingError(normalizedRootId, message)
                        CloudFolderSyncEvents.notifyStateChanged()
                        refreshCloudFolderSyncState()
                        return@launch
                    }
                }
                if (!isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) return@launch
                repository.saveBinding(
                    (existing ?: CloudFolderDeviceBinding(
                        rootId = normalizedRootId,
                        deviceId = repository.deviceId,
                    )).copy(
                        localUri = null,
                        permissionState = CloudFolderPermissionState.UNKNOWN,
                        materializationMode = mode,
                        // A new KEEP_OFFLINE binding has not materialized any
                        // bytes yet. Leave its acknowledgement behind the
                        // remote revision so the next pull performs the
                        // initial download before considering it complete.
                        lastAcknowledgedRevision = if (
                            mode == CloudFolderMaterializationMode.KEEP_OFFLINE &&
                            existing?.materializationMode != CloudFolderMaterializationMode.KEEP_OFFLINE
                        ) 0L else root.manifestRevision,
                        lastError = null,
                    )
                )
                val knownRootIds = repository.getRoots().map { it.rootId }
                val currentSelection = CloudFolderSyncPrefs.load(appContext, accountId)
                val nextSelection = when (mode) {
                    CloudFolderMaterializationMode.KEEP_OFFLINE ->
                        currentSelection.withRootIncluded(normalizedRootId)
                    CloudFolderMaterializationMode.CLOUD_ONLY ->
                        currentSelection.withoutRoot(normalizedRootId, knownRootIds)
                    CloudFolderMaterializationMode.LOCAL_MIRROR -> currentSelection
                }
                CloudFolderSyncPrefs.save(appContext, accountId, nextSelection)
                CloudFolderSyncPrefs.dismissIncomingPrompt(
                    context = appContext,
                    accountId = accountId,
                    rootId = normalizedRootId,
                    revision = root.manifestRevision,
                )
                CloudFolderSyncEvents.notifyStateChanged()
                if (mode == CloudFolderMaterializationMode.KEEP_OFFLINE && uiState.value.isSyncEnabled) {
                    CloudFolderSyncWorker.enqueuePull(
                        appContext,
                        accountId = accountId,
                        rootId = normalizedRootId,
                        replace = true,
                    )
                }
                refreshCloudFolderSyncState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Unable to change incoming cloud-folder materialization root=$normalizedRootId")
                runCatching {
                    CloudFolderSyncRepository(appContext, accountId).markBindingError(
                        normalizedRootId,
                        "Unable to change folder materialization: " +
                            (error.message?.takeIf { it.isNotBlank() } ?: "unknown error"),
                    )
                }
                CloudFolderSyncEvents.notifyStateChanged()
                refreshCloudFolderSyncState()
            }
        }
    }

    /**
     * Persist device 2's materialization choice without ever uploading its
     * local SAF URI.  A bind choice is completed by the caller after the SAF
     * picker returns [localFolderUri]; cloud-only/download-all remain local
     * binding records and are safe to retry.
     */
    fun recordIncomingCloudFolderChoice(
        prompt: CloudFolderIncomingFolderPrompt,
        choice: CloudFolderIncomingChoice,
        localFolderUri: Uri? = null,
        onPersisted: (Boolean) -> Unit = {},
    ) {
        if (choice == CloudFolderIncomingChoice.BIND_LOCAL_FOLDER && localFolderUri == null) {
            Timber.w("Ignoring bind choice without a local folder URI root=${prompt.rootId}")
            reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
            return
        }
        val accountId = activeCloudFolderSyncAccountId() ?: run {
            Timber.w("Ignoring incoming cloud-folder choice without an entitled Pro account")
            reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) {
                    Timber.i("Ignoring stale incoming cloud-folder choice for account $accountId")
                    reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
                    return@launch
                }
                val localUri = localFolderUri?.toString()
                if (localFolderUri != null) {
                    appContext.contentResolver.takePersistableUriPermission(
                        localFolderUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                val materialization = choice.materializationMode
                if (!isCloudFolderSyncAvailableFor(accountId) ||
                    authRepository.getSignedInUser()?.uid?.trim() != accountId
                ) {
                    Timber.i("Ignoring account-switched incoming cloud-folder choice for $accountId")
                    reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
                    return@launch
                }
                val repository = CloudFolderSyncRepository(appContext, accountId)
                repository.saveBinding(
                    CloudFolderDeviceBinding(
                        rootId = prompt.rootId,
                        deviceId = repository.deviceId,
                        localUri = localUri,
                        permissionState = if (localUri == null) {
                            CloudFolderPermissionState.UNKNOWN
                        } else {
                            CloudFolderPermissionState.GRANTED
                        },
                        materializationMode = materialization,
                        // CLOUD_ONLY needs no local transfer. Both
                        // materializing choices must acknowledge only after
                        // their first complete pull succeeds.
                        lastAcknowledgedRevision = if (
                            materialization == CloudFolderMaterializationMode.CLOUD_ONLY
                        ) prompt.root.manifestRevision else 0L,
                    )
                )
                if (choice.shouldIncludeInLocalSyncSelection) {
                    // DOWNLOAD_ALL is intentionally an explicit opt-in: the
                    // worker will otherwise leave the incoming root cloud
                    // only because folder selection defaults to EXCLUDED.
                    CloudFolderSyncPrefs.save(
                        appContext,
                        accountId,
                        CloudFolderSyncPrefs.load(appContext, accountId).withRootIncluded(prompt.rootId),
                    )
                }
                CloudFolderSyncPrefs.dismissIncomingPrompt(
                    context = appContext,
                    accountId = accountId,
                    rootId = prompt.rootId,
                    revision = prompt.root.manifestRevision,
                )
                CloudFolderSyncEvents.notifyStateChanged()
                if (choice.shouldIncludeInLocalSyncSelection &&
                    isCloudFolderSyncAvailableFor(accountId) &&
                    authRepository.getSignedInUser()?.uid?.trim() == accountId &&
                    uiState.value.isSyncEnabled
                ) {
                    // An incoming choice is a pull. Using the default
                    // LOCAL_TO_CLOUD direction here would only scan a local
                    // mirror and would never materialize DOWNLOAD_ALL (which
                    // intentionally has no local URI).
                    CloudFolderSyncWorker.enqueuePull(
                        appContext,
                        accountId = accountId,
                        rootId = prompt.rootId,
                        replace = true,
                    )
                }
                refreshCloudFolderSyncState()
                reportIncomingCloudFolderPersistence(onPersisted, succeeded = true)
            } catch (error: SecurityException) {
                Timber.e(error, "Unable to persist incoming cloud-folder permission root=${prompt.rootId}")
                refreshCloudFolderSyncState()
                reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
            } catch (error: Exception) {
                Timber.e(error, "Unable to persist incoming cloud-folder choice root=${prompt.rootId}")
                refreshCloudFolderSyncState()
                reportIncomingCloudFolderPersistence(onPersisted, succeeded = false)
            }
        }
    }

    /** Deliver persistence results on the ViewModel's normal (main) scope. */
    private fun reportIncomingCloudFolderPersistence(
        onPersisted: (Boolean) -> Unit,
        succeeded: Boolean,
    ) {
        viewModelScope.launch {
            onPersisted(succeeded)
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
        val driveFileId = googleDriveRepository.getFilesOrThrow(accessToken)
            .files
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
        val accountId = _internalState.value.currentUser?.uid?.trim()?.takeIf { it.isNotBlank() }
            ?: return@launch
        CloudBookSyncBarrier.withAccountLock(accountId) {
            syncWithCloudLocked(accountId, showBanner)
        }
    }

    private suspend fun syncWithCloudLocked(accountId: String, showBanner: Boolean = false) {
        coroutineScope {
        val hasPermissions = googleDriveRepository.hasDrivePermissions(appContext)
        val currentUser = _internalState.value.currentUser

        if (!hasPermissions || currentUser == null || currentUser.uid.trim() != accountId ||
            authRepository.getSignedInUser()?.uid?.trim() != accountId
        ) {
            logCloudSyncTrace {
                "android.full_sync.skip reason=${when {
                    !hasPermissions -> "missing_drive_permissions"
                    currentUser == null -> "no_user"
                    else -> "account_changed"
                }} " +
                    "showBanner=$showBanner"
            }
            if (showBanner) _internalState.update {
                it.copy(errorMessage = appContext.getString(R.string.error_not_signed_in_sync))
            }
            return@coroutineScope
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
            // Deletions are durable intents completed by a background worker.
            // Keep those IDs out of this merge so an older active document
            // cannot resurrect a book, but continue syncing every unrelated
            // book instead of blocking Home on a large delete queue.
            val pendingDeleteBookIds = cloudBookDeletePersistence.pending(currentUser.uid)
                .mapTo(mutableSetOf()) { it.bookId }
            if (pendingDeleteBookIds.isNotEmpty()) {
                runCatching {
                    CloudBookDeleteWorker.enqueue(appContext, currentUser.uid)
                }.onFailure { error ->
                    Timber.e(error, "Unable to schedule pending cloud-book deletion")
                }
                logCloudSyncTrace {
                    "android.full_sync.delete_queue user=${currentUser.uid} books=${pendingDeleteBookIds.size}"
                }
            }

            val accessToken = googleDriveRepository.getAccessToken(appContext)
            if (accessToken == null) {
                logCloudSyncTrace { "android.full_sync.skip reason=no_access_token user=${currentUser.uid}" }
                return@coroutineScope
            }

            val deviceId = getInstallationId()
            val remoteBooksDeferred = async(Dispatchers.IO) {
                firestoreRepository.getAllBooks(currentUser.uid)
            }
            val remoteShelvesDeferred = async(Dispatchers.IO) {
                firestoreRepository.getAllShelves(currentUser.uid)
            }
            val localBooks = withContext(Dispatchers.IO) {
                val allFiles = bookStore.getAllFilesForSync()
                val filtered = if (_internalState.value.isFolderSyncEnabled) {
                    allFiles
                } else {
                    allFiles.filter { it.sourceFolderUri == null }
                }
                filtered
                    .filterNot { it.uriString?.startsWith("opds-pse") == true }
                    .filterNot { it.isManualOnlyReaderFile() }
            }

            val remoteBooks = remoteBooksDeferred.await()
                .filterNot { it.isManualOnlyReaderFile() }
            val rawRemoteShelves = remoteShelvesDeferred.await()
            val initialDriveFiles = withContext(Dispatchers.IO) {
                googleDriveRepository.getFilesOrThrow(accessToken).files.associateBy { it.name }
            }
            logCloudSyncTrace {
                "android.full_sync.loaded user=${currentUser.uid} device=$deviceId " +
                    "localBooks=${localBooks.size} remoteBooks=${remoteBooks.size} " +
                    "remoteShelves=${rawRemoteShelves.size} driveFiles=${initialDriveFiles.size}"
            }
            val syncableBookIds = (localBooks.map { it.bookId } + remoteBooks.map { it.bookId })
                .filterNot { it in pendingDeleteBookIds }
                .toSet()
            val shelfDao = AppDatabase.getDatabase(appContext).shelfDao()
            val localShelfEntities = shelfDao.getAllUserShelvesForSync()
            val localShelfIdByName = localShelfEntities.associate { it.name to it.id }
            val localShelves = localShelfEntities.map { shelf ->
                val bookIds = shelfDao.getCrossRefsForShelf(shelf.id)
                    .map { it.bookId }.filter { it in syncableBookIds }
                ShelfMetadata(
                    shelfId = shelf.id,
                    name = shelf.name,
                    bookIds = bookIds,
                    lastModifiedTimestamp = shelf.updatedAt,
                    isDeleted = shelf.isDeleted
                )
            }
            val remoteShelfNamesWithStableIds = rawRemoteShelves
                .filter { it.shelfId.isNotBlank() }
                .mapTo(mutableSetOf()) { it.name }
            rawRemoteShelves
                .filter { it.shelfId.isBlank() && it.name in remoteShelfNamesWithStableIds }
                .forEach { legacy ->
                    runCatching {
                        firestoreRepository.deleteShelfDocument(currentUser.uid, legacy.legacyDocumentId)
                    }.onFailure { error ->
                        Timber.w(error, "Unable to remove superseded legacy shelf ${legacy.name}")
                    }
                }
            val remoteShelves = rawRemoteShelves
                .filter { it.shelfId.isNotBlank() || it.name !in remoteShelfNamesWithStableIds }
                .map { remote ->
                    if (remote.shelfId.isNotBlank()) remote else remote.copy(
                        shelfId = localShelfIdByName[remote.name]
                            ?: "legacy_${remote.legacyDocumentId.hashCode().toUInt().toString(16)}"
                    )
                }

            // 3. Merge Books
            val localBooksMap = localBooks.associateBy { it.bookId }
            val remoteBooksMap = remoteBooks.associateBy { it.bookId }
            val allBookIds = (localBooksMap.keys + remoteBooksMap.keys)
                .filterNot { it in pendingDeleteBookIds }
                .distinct()
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
                        bookStore.addRecentFile(remote.toRecentFileItem())
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
                        val localSidecars = androidPdfCloudSidecarInventory(
                            inkFile, deletedInkFile, richTextFile, layoutFile, textBoxFile, highlightFile
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
                                    bookStore.deleteFilePermanently(listOf(bookId))
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
                            bookStore.addRecentFile(localWithRemoteEpubAnnotations)
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
                                bookStore.addRecentFile(
                                    remoteForLocalDb
                                )
                                if (localAnnotationsShouldUpload || localReadingPositionShouldUpload) {
                                    bookStore.getFileByBookId(bookId)?.let { merged ->
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

            val localShelvesMap = localShelves.associateBy { it.shelfId }
            val remoteShelvesMap = remoteShelves.associateBy { it.shelfId }
            val allShelfIds = (localShelvesMap.keys + remoteShelvesMap.keys).distinct()

            allShelfIds.forEach { shelfId ->
                val local = localShelvesMap[shelfId]
                val remote = remoteShelvesMap[shelfId]

                when {
                    local != null && remote == null -> firestoreRepository.syncShelf(
                        currentUser.uid, local, deviceId
                    )

                    local == null && remote != null -> {
                        val applied = libraryStore.applyRemoteShelf(
                            remote.shelfId, remote.name, remote.bookIds.filter { it in syncableBookIds },
                            remote.lastModifiedTimestamp, remote.isDeleted
                        )
                        if (applied && remote.legacyDocumentId != remote.shelfId) {
                            firestoreRepository.syncShelf(currentUser.uid, remote, deviceId)
                        }
                    }

                    local != null && remote != null -> {
                        if (local.lastModifiedTimestamp > remote.lastModifiedTimestamp) {
                            firestoreRepository.syncShelf(
                                currentUser.uid,
                                local.copy(legacyDocumentId = remote.legacyDocumentId),
                                deviceId
                            )
                        } else if (remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                            val applied = libraryStore.applyRemoteShelf(
                                remote.shelfId, remote.name, remote.bookIds.filter { it in syncableBookIds },
                                remote.lastModifiedTimestamp, remote.isDeleted
                            )
                            if (applied && remote.legacyDocumentId != remote.shelfId) {
                                firestoreRepository.syncShelf(currentUser.uid, remote, deviceId)
                            }
                        }
                    }
                }
            }

            val finalMergedBooks = withContext(Dispatchers.IO) {
                bookStore.getAllFilesForSync()
            }.filterNot { it.isManualOnlyReaderFile() }
                .filterNot { it.bookId in pendingDeleteBookIds }
            val remoteFiles = withContext(Dispatchers.IO) {
                googleDriveRepository.getFilesOrThrow(accessToken).files.associateBy { it.name }
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
                    bookStore.deleteFilePermanently(listOf(book.bookId))
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
            if (isCloudFolderTransferFailure(e)) {
                // Folder transfers own their durable progress/error state. Do
                // not turn a background folder problem into a misleading
                // global "Library sync failed" banner; the folder settings
                // surface and the common cloud-folder log tag are the source
                // of truth for that pipeline.
                cloudFolderLogError(
                    event = "library_sync_error_suppressed",
                    error = e,
                    details = "reason=cloud_folder_transfer",
                )
                if (showBanner) {
                    _internalState.update { it.copy(isLoading = false) }
                }
                refreshCloudFolderSyncState()
            } else {
                logCloudSyncError(e) { "android.full_sync.failed user=${currentUser.uid}" }
                Timber.tag("AnnotationSync").e(e, "Error during cloud sync")
                if (showBanner) {
                    _internalState.update {
                        it.copy(isLoading = false, errorMessage = appContext.getString(R.string.error_sync_library_failed))
                    }
                }
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
            bookStore.getFileByBookId(bookId) == null
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

        val existingItem = bookStore.getFileByBookId(bookId)
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
                    coverPath = bookArtifactStore.saveCoverToCache(cover, uri)
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
                        coverPath = bookArtifactStore.saveCoverToCache(coverBitmap, uri)
                    }
                }
            } else if (type == FileType.PPTX) {
                if (coverPath == null) {
                    val pptxCoverGenerator = PptxCoverGenerator(appContext)
                    val coverBitmap = pptxCoverGenerator.generateCover(uri)
                    if (coverBitmap != null) {
                        coverPath = bookArtifactStore.saveCoverToCache(coverBitmap, uri)
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
                                        coverPath = bookArtifactStore.saveCoverToCache(bitmap, uri)
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
        if (coverPath == null) {
            val thumbnailItem = RecentFileItem(
                bookId = bookId,
                uriString = uri.toString(),
                type = type,
                displayName = displayName,
                timestamp = System.currentTimeMillis(),
                title = title,
                author = author,
                isRecent = isRecent,
                sourceFolderUri = sourceFolderUri,
                fileSize = fileSize,
                fileContentModifiedTimestamp = fileContentModifiedTimestamp,
                seriesName = seriesName,
                seriesIndex = seriesIndex,
                description = description
            )
            try {
                ContentThumbnailGenerator(appContext).generate(thumbnailItem)?.let { thumbnail ->
                    try {
                        coverPath = bookArtifactStore.saveCoverToCache(thumbnail, uri)
                    } finally {
                        thumbnail.recycle()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to generate content thumbnail for $displayName")
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
        if (type in PDF_VIEWER_FILE_TYPES) {
            Timber.tag(PDF_RENAME_TRACE_TAG).i(
                "viewModel.addFileToRecent.pdf bookId=$bookId uri=$uri displayName=$displayName " +
                    "title=$title existingCustomName=${existingItem?.customName} " +
                    "existingTitle=${existingItem?.title} isNewBook=$isNewBook sourceFolderUri=$sourceFolderUri"
            )
        }
        bookStore.addRecentFile(newItem)
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
                val allDbIds = bookStore.getAllFilesForSync().map { it.bookId }.toSet()
                val validStreamHashes = allDbIds.map { it.hashCode().toString() }.toSet()
                val validActiveBookCacheDirs = allDbIds.mapTo(mutableSetOf()) {
                    ImportedFileCache.activeBookDirName(it)
                }
                ImportedFileCache.deleteStaleTemporaryBookDirs(appContext, TimeUnit.HOURS.toMillis(1))
                AndroidShareArtifactManager.sweep(appContext).takeIf { it > 0 }?.let { deletedCount ->
                    Timber.d("Sweeper cleaned $deletedCount expired share artifacts")
                }

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
                ).withClearedLibraryBookSelection()
            }

            var outcomeCounts = SharedImportOutcomeCounts()

            withContext(Dispatchers.IO) {
                for (externalUri in uris) {
                    val importResult = prepareBookForImport(externalUri)
                    if (importResult != null) {
                        val internalUri = importResult.internalUri
                        val bookId = importResult.bookId
                        val type = importResult.type
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
                        outcomeCounts = outcomeCounts.record(MobileImportOutcome.ADDED)
                    } else {
                        val hash = FileHasher.calculateSha256 {
                            appContext.contentResolver.openInputStream(externalUri)
                        }
                        if (hash != null && bookStore.getFileByBookId(hash) != null) {
                            outcomeCounts = outcomeCounts.record(MobileImportOutcome.DUPLICATE)
                        } else if (getFileTypeFromUri(externalUri, appContext) == null) {
                            outcomeCounts = outcomeCounts.record(MobileImportOutcome.UNSUPPORTED)
                        } else {
                            outcomeCounts = outcomeCounts.record(MobileImportOutcome.FAILED)
                        }
                    }
                }
            }

            _internalState.update {
                val feedback = SharedImportPlanner.feedbackForCounts(
                    counts = outcomeCounts,
                    importedMessage = appContext.resources.getQuantityString(
                        R.plurals.banner_books_imported_library_tab,
                        outcomeCounts.addedCount,
                        outcomeCounts.addedCount
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

            Timber.tag("BulkImport").i(
                "Bulk import complete. ${outcomeCounts.addedCount} new files, " +
                    "${outcomeCounts.duplicateCount} duplicates, " +
                    "${outcomeCounts.unsupportedCount} unsupported, " +
                    "${outcomeCounts.failedCount} failed."
            )
        }
    }

    fun onFileSelected(
        uri: Uri,
        isFromRecent: Boolean = false,
        isExternalIntent: Boolean = false,
        isTemporaryExternalIntent: Boolean = false
    ) {
        if (isFromRecent) {
            Timber.i("Opening recent file: $uri")
            viewModelScope.launch {
                val item = bookStore.getFileByUri(uri.toString())
                if (item != null) {
                    openBook(uri, item.bookId, item.type, item.displayName)
                } else {
                    _internalState.update { it.copy(errorMessage = appContext.getString(R.string.error_recent_item_not_found)) }
                }
            }
        } else {
            Timber.i("Importing new file: $uri")
            importExternalFile(uri, isExternalIntent, isTemporaryExternalIntent)
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
            val item = bookStore.getFileByBookId(bookId)
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

    private fun importExternalFile(
        externalUri: Uri,
        isExternalIntent: Boolean = false,
        isTemporaryExternalIntent: Boolean = false
    ) {
        if (isTemporaryExternalIntent) {
            openTemporaryExternalFile(externalUri)
            return
        }

        _internalState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
            ).withClearedLibraryBookSelection()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val importResult = prepareBookForImport(externalUri)

                if (importResult != null) {
                    val internalUri = importResult.internalUri
                    val bookId = importResult.bookId
                    val type = importResult.type
                    if (isExternalIntent) {
                        trackExternalOpenForClose(
                            bookId = bookId,
                            importedCopyUriString = internalUri.toString(),
                            isTemporaryExternalIntent = isTemporaryExternalIntent
                        )
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
                        val existingItem = bookStore.getFileByBookId(hash)
                        if (existingItem != null) {
                            Timber.i("Re-selected an existing book. Opening it.")
                            if (isTemporaryExternalIntent) {
                                trackExternalOpenForClose(
                                    bookId = existingItem.bookId,
                                    importedCopyUriString = null,
                                    isTemporaryExternalIntent = true
                                )
                            }
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

    private fun openTemporaryExternalFile(externalUri: Uri) {
        _internalState.update {
            it.copy(
                isLoading = true,
                isTemporaryExternalOpen = true,
                errorMessage = null,
            ).withClearedLibraryBookSelection()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val type = getFileTypeFromUri(externalUri, appContext)
            if (type == null) {
                _internalState.update {
                    it.copy(
                        isLoading = false,
                        isTemporaryExternalOpen = false,
                        errorMessage = appContext.getString(R.string.error_unsupported_file_type)
                    )
                }
                return@launch
            }

            val displayName = getFileNameFromUri(externalUri, appContext) ?: "Temporary File"
            val bookId = "temporary-${UUID.randomUUID()}"
            trackExternalOpenForClose(
                bookId = bookId,
                importedCopyUriString = null,
                isTemporaryExternalIntent = true
            )
            openBook(
                uri = externalUri,
                bookId = bookId,
                type = type,
                originalDisplayName = displayName,
                persistToLibrary = false
            )
        }
    }

    fun saveHighlights(bookId: String, highlightsJson: String) {
        viewModelScope.launch {
            val currentBookUri = _internalState.value.selectedPdfUri ?: _internalState.value.selectedEpubUri
            if (currentBookUri != null) {
                bookStore.getFileByUri(currentBookUri.toString())?.let { item ->
                    if (annotationJsonEquivalentForNoop(item.highlightsJson, highlightsJson)) {
                        logCloudSyncTrace {
                            "android.reader.highlights_save_noop book=${item.bookId} highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                        }
                        return@launch
                    }
                    logCloudSyncTrace {
                        "android.reader.highlights_save book=${item.bookId} highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                    }
                    bookStore.updateHighlights(item.bookId, highlightsJson)
                }
            } else if (bookId.isNotBlank()) {
                val existing = bookStore.getFileByBookId(bookId)
                if (annotationJsonEquivalentForNoop(existing?.highlightsJson, highlightsJson)) {
                    logCloudSyncTrace {
                        "android.reader.highlights_save_noop book=$bookId highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                    }
                    return@launch
                }
                logCloudSyncTrace {
                    "android.reader.highlights_save book=$bookId highlights=${highlightsJson.cloudSyncAnnotationSummary()}"
                }
                bookStore.updateHighlights(bookId, highlightsJson)
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
                _internalState.update { state ->
                    markReaderSessionReady(
                        startReaderSession(state, bookId, type),
                        bookId,
                    ).copy(
                        selectedEpubUri = null,
                        selectedEpubBook = null,
                        selectedPdfUri = uri,
                        initialPageInBook = syncPosition,
                        initialPageInBookIsExplicit = true,
                        isOpeningFromTtsNotification = false,
                        initialBookmarksJson = item.bookmarksJson,
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
                _navigationEvent.send(NavigationEvent("pdf_viewer", bookId, uri.toString()))
                stateUpdateDeferred.complete(true)

            } else if (type in EPUB_READER_FILE_TYPES) {
                persistReaderSession(bookId, type)
                try {
                    val epubBook = restoreEpubReaderBook(type, bookId, item.displayName, uri)
                    _internalState.update { state ->
                        markReaderSessionReady(
                            startReaderSession(state, bookId, type),
                            bookId,
                        ).copy(
                            selectedPdfUri = null,
                            selectedEpubUri = uri,
                            selectedEpubBook = epubBook,
                            initialLocator = Locator(
                                chapterIndex = syncPosition, blockIndex = 0, charOffset = 0
                            ),
                            initialCfi = null,
                            initialBookmarksJson = item.bookmarksJson,
                            initialHighlightsJson = item.highlightsJson,
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
                    _navigationEvent.send(NavigationEvent("epub_reader", bookId, uri.toString()))
                    stateUpdateDeferred.complete(true)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to switch seamlessly to $type book: $bookId")
                    _internalState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = appContext.getString(R.string.error_load_file, e.message),
                            readerSession = it.readerSession.reduce(AppReaderSessionAction.SeamlessSwitchFailed)
                        )
                    }
                    stateUpdateDeferred.complete(false)
                }
            } else {
                _internalState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = appContext.getString(R.string.error_unsupported_file_type),
                        readerSession = it.readerSession.reduce(AppReaderSessionAction.SeamlessSwitchFailed)
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
            val existing = bookStore.getFileByBookId(reflowBookId)
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
                            var newItem = bookStore.getFileByBookId(reflowBookId)
                            while (newItem == null && retries < 10) {
                                delay(200)
                                newItem = bookStore.getFileByBookId(reflowBookId)
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

    protected open suspend fun cleanupBookDataLocally(bookId: String) {
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
        preserveTtsOnOpen: Boolean = false,
        persistToLibrary: Boolean = true
    ) {
        val openBookStartTime = System.currentTimeMillis()
        ReaderPerfLog.d("FileOpen start bookId=$bookId type=$type")
        Timber.tag("FileOpenPerf")
            .d("[$bookId] openBook START | type=$type | displayName=$originalDisplayName")
        if (type in PDF_VIEWER_FILE_TYPES) {
            Timber.tag(PDF_RENAME_TRACE_TAG).i(
                "viewModel.openBook.start bookId=$bookId type=$type uri=$uri " +
                    "originalDisplayName=$originalDisplayName selectedBookId=${_internalState.value.selectedBookId} " +
                    "usePdfFileName=${_internalState.value.usePdfFileNameAsDisplayName}"
            )
        }

        val projectedState = uiState.value
        val availableBookIds = projectedState.rawLibraryFiles.mapTo(mutableSetOf()) { it.bookId }
        val currentTabState = reconcileTabState(_internalState.value, availableBookIds)
        if (currentTabState.isTabsEnabled && type == FileType.PDF) {
            if (bookId !in currentTabState.openTabIds) {
                if (currentTabState.openTabIds.size >= MAX_OPEN_PDF_TABS) {
                    viewModelScope.launch(Dispatchers.Main) {
                        showBanner("Maximum of 20 tabs allowed. Please close a tab first.", isError = true)
                    }
                    return
                }
            }
            val tabState = openBookTab(
                current = currentTabState,
                availableBookIds = availableBookIds,
                bookId = bookId
            )
            persistTabState(tabState.openTabIds, tabState.activeTabBookId)
            _internalState.update {
                it.copy(
                    tabState = tabState.tabState,
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
            _internalState.update { state ->
                startReaderSession(state, bookId, type).copy(
                    selectedPdfUri = null,
                    selectedEpubUri = null,
                    selectedEpubBook = null,
                    initialLocator = initialLocatorOverride,
                    initialCfi = initialCfiOverride,
                    initialPageInBook = initialPageOverride,
                    initialPageInBookIsExplicit = isInitialPageExplicit,
                    isOpeningFromTtsNotification = preserveTtsOnOpen
                )
            }

            if (type in PDF_VIEWER_FILE_TYPES) {
                viewModelScope.launch {
                    val recentItem = bookStore.getFileByBookId(bookId)
                    Timber.tag(PDF_RENAME_TRACE_TAG).i(
                        "viewModel.openBook.pdfBranch bookId=$bookId uri=$uri " +
                            "dbDisplayName=${recentItem?.displayName} dbTitle=${recentItem?.title} " +
                            "dbCustomName=${recentItem?.customName} dbType=${recentItem?.type} " +
                            "initialPage=${initialPageOverride ?: recentItem?.lastPage} persistToLibrary=$persistToLibrary"
                    )

                    Timber.tag("FileOpenPerf")
                        .d("[$bookId] Branch: PDF | elapsed=${System.currentTimeMillis() - openBookStartTime}ms")
                    _internalState.update { state ->
                        markReaderSessionReady(state, bookId).copy(
                            selectedPdfUri = uri,
                            initialPageInBook = initialPageOverride ?: recentItem?.lastPage,
                            initialPageInBookIsExplicit = isInitialPageExplicit,
                            isOpeningFromTtsNotification = preserveTtsOnOpen,
                            initialBookmarksJson = recentItem?.bookmarksJson,
                        )
                    }
                    ReaderPerfLog.d("FileOpen ready bookId=$bookId type=$type elapsed=${System.currentTimeMillis() - openBookStartTime}ms")
                    if (persistToLibrary) {
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
                    }

                    if (!suppressNavigation) {
                        Timber.tag("FileSwitch").d("PDF state updated, emitting navigation event")
                        _navigationEvent.send(NavigationEvent("pdf_viewer", bookId, uri.toString()))
                    } else {
                        Timber.tag("FileSwitch").d("PDF state updated, suppressing navigation event for smooth transition")
                    }
                }
            } else if (type in EPUB_READER_FILE_TYPES) {
                viewModelScope.launch {
                    val recentItem = bookStore.getFileByBookId(bookId)
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
                    if (persistToLibrary) {
                        persistReaderSession(bookId, type)
                    }

                    if (!suppressNavigation) {
                        Timber.tag("FileSwitch").d("EPUB state updated, emitting navigation event")
                        _navigationEvent.send(NavigationEvent("epub_reader", bookId, uri.toString()))
                    }

                    when (type) {
                        FileType.EPUB -> {
                            loadEpub(uri, bookId, customDisplayName = originalDisplayName, bundleResult = bundleResult, persistToLibrary = persistToLibrary)
                        }

                        FileType.MOBI -> {
                            loadMobi(uri, bookId, customDisplayName = originalDisplayName, bundleResult = bundleResult, persistToLibrary = persistToLibrary)
                        }

                        FileType.FB2 -> {
                            loadFb2(uri, bookId, customDisplayName = originalDisplayName, bundleResult = bundleResult, persistToLibrary = persistToLibrary)
                        }
                        FileType.ODT, FileType.FODT -> {
                            loadOdt(uri, bookId, type == FileType.FODT, customDisplayName = originalDisplayName, bundleResult = bundleResult, persistToLibrary = persistToLibrary)
                        }
                        else -> {
                            loadSingleFile(
                                uri, bookId, type, customDisplayName = originalDisplayName, bundleResult = bundleResult, persistToLibrary = persistToLibrary
                            )
                        }
                    }
                }
            } else {
                _internalState.update { state ->
                    markReaderSessionFailed(
                        state,
                        bookId,
                        appContext.getString(R.string.error_unsupported_file_type),
                        closeReader = true,
                    ).copy(
                        selectedPdfUri = null,
                        selectedEpubUri = null,
                        selectedEpubBook = null,
                        initialPageInBookIsExplicit = false,
                        isOpeningFromTtsNotification = false
                    )
                }
            }
        }
    }

    private fun loadFb2(
        uri: Uri,
        bookId: String,
        customDisplayName: String? = null,
        bundleResult: CalibreBundleResult? = null,
        persistToLibrary: Boolean = true
    ) {
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

                if (persistToLibrary) {
                    addFileToRecent(
                        uri, FileType.FB2, bookId, fb2Book, customDisplayName, isRecent = true, sourceFolderUri = null, bundleResult = bundleResult
                    )
                }

                _internalState.update { state ->
                    markReaderSessionReady(state, bookId).copy(selectedEpubBook = fb2Book)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing FB2 for URI: $uri")
                _internalState.update {
                    markReaderSessionFailed(
                        it, bookId, appContext.getString(R.string.error_load_fb2, e.message)
                    )
                }
            }
        }
    }

    private fun loadOdt(
        uri: Uri,
        bookId: String,
        isFlat: Boolean,
        customDisplayName: String? = null,
        bundleResult: CalibreBundleResult? = null,
        persistToLibrary: Boolean = true
    ) {
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

                if (persistToLibrary) {
                    addFileToRecent(
                        uri, if (isFlat) FileType.FODT else FileType.ODT, bookId, odtBook, customDisplayName, isRecent = true, sourceFolderUri = null, bundleResult = bundleResult
                    )
                }

                _internalState.update { state ->
                    markReaderSessionReady(state, bookId).copy(selectedEpubBook = odtBook)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing ODT for URI: $uri")
                _internalState.update {
                    markReaderSessionFailed(
                        it, bookId, appContext.getString(R.string.error_load_file, e.message)
                    )
                }
            }
        }
    }

    private fun loadSingleFile(
        uri: Uri,
        bookId: String,
        type: FileType,
        customDisplayName: String? = null,
        bundleResult: CalibreBundleResult? = null,
        persistToLibrary: Boolean = true
    ) {
        val loadStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[$bookId] loadSingleFile START | type=$type")
        viewModelScope.launch {
            if (type !in EPUB_READER_FILE_TYPES) {
                _internalState.update {
                    markReaderSessionFailed(
                        it, bookId, appContext.getString(R.string.error_unsupported_file_type)
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
                if (persistToLibrary) {
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
                }

                _internalState.update { state ->
                    markReaderSessionReady(state, bookId).copy(selectedEpubBook = epubBook)
                }
                Timber.tag("FileOpenPerf")
                    .d("[$bookId] loadSingleFile COMPLETE | totalElapsed=${System.currentTimeMillis() - loadStart}ms")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing file ($type) for URI: $uri")
                _internalState.update {
                    markReaderSessionFailed(
                        it, bookId, appContext.getString(R.string.error_load_file, e.message)
                    )
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

    private fun loadMobi(
        uri: Uri,
        bookId: String,
        customDisplayName: String? = null,
        bundleResult: CalibreBundleResult? = null,
        persistToLibrary: Boolean = true
    ) {
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
                    if (persistToLibrary) {
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
                    }
                    _internalState.update {
                        markReaderSessionReady(it, bookId)
                            .copy(selectedEpubBook = mobiAsEpubBook)
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
                    markReaderSessionFailed(
                        it, bookId, appContext.getString(R.string.error_load_mobi, e.message)
                    )
                }
            }
        }
    }

    private fun loadEpub(
        uri: Uri,
        bookId: String,
        customDisplayName: String? = null,
        bundleResult: CalibreBundleResult? = null,
        persistToLibrary: Boolean = true
    ) {
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

                if (persistToLibrary) {
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
                }

                _internalState.update { state ->
                    markReaderSessionReady(state, bookId).copy(selectedEpubBook = epubBook)
                }
                Timber.tag("FileOpenPerf")
                    .d("[$bookId] loadEpub COMPLETE | totalElapsed=${System.currentTimeMillis() - loadStart}ms")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing EPUB for URI: $uri")
                _internalState.update {
                    markReaderSessionFailed(
                        it, bookId, appContext.getString(R.string.error_load_epub, e.message)
                    )
                }
            }
        }
    }

    fun saveEpubReadingPosition(
        uri: Uri, locator: Locator, cfiForWebView: String?, progress: Float
    ) {
        Timber.d("Saving EPUB position locally: URI=$uri, Locator=$locator")
        enqueueReaderStateSave(uri.toString()) {
            val existing = bookStore.getFileByUri(uri.toString())
                ?: selectedBookRowForManagedFile(uri)
            existing?.let { book ->
                logCloudSyncTrace {
                    "android.reader.position_save_start book=${book.bookId} beforeTs=${book.lastModifiedTimestamp} " +
                        "locator={chapter=${locator.chapterIndex} block=${locator.blockIndex} char=${locator.charOffset}} " +
                        "progress=$progress cfi=${cfiForWebView.cloudSyncPreview()}"
                }
                val positionOperation = cloudFolderOperationId("reader-position", book.bookId, "epub")
                val positionCorrelation = cloudFolderSyncCorrelationId("reader-position", book.bookId, "epub")
                cloudFolderLogD(
                    "event=reader_position_save_start operation=$positionOperation correlation=$positionCorrelation " +
                        "book=${cloudFolderSafeId(book.bookId)} kind=epub " +
                        "beforeReadTs=${book.effectiveReadingPositionModifiedTimestamp()} " +
                        "chapter=${locator.chapterIndex} block=${locator.blockIndex} char=${locator.charOffset} progress=$progress",
                )
                bookStore.updateEpubReadingPosition(
                    uriString = book.uriString ?: uri.toString(),
                    locator = locator,
                    cfiForWebView = cfiForWebView,
                    progress = progress
                )
                val updated = bookStore.getFileByBookId(book.bookId)
                logCloudSyncTrace {
                    "android.reader.position_save_done beforeTs=${book.lastModifiedTimestamp} " +
                        (updated?.cloudSyncTraceSummary("after") ?: "after=null")
                }
                cloudFolderLogD(
                    "event=reader_position_save_end operation=$positionOperation correlation=$positionCorrelation " +
                        "book=${cloudFolderSafeId(book.bookId)} kind=epub result=${if (updated != null) "success" else "missing"} " +
                        "afterReadTs=${updated?.effectiveReadingPositionModifiedTimestamp() ?: 0L} " +
                        "afterChapter=${updated?.lastChapterIndex ?: "none"} afterBlock=${updated?.locatorBlockIndex ?: "none"} " +
                        "afterChar=${updated?.locatorCharOffset ?: "none"} afterProgress=${updated?.progressPercentage ?: "none"}",
                )
                queueCloudMetadataUpload(book.bookId, reason = "epub_position")
            }
        }
    }

    /**
     * Reader engines report position/bookmark changes from callbacks whose
     * jobs are not awaited by the close action. Chain writes by stable URI so
     * the final close snapshot cannot read Room before the last callback has
     * committed. The chain is deliberately in-memory; Room remains the
     * durable source of truth if the process is killed before a callback runs.
     */
    private fun enqueueReaderStateSave(
        uriString: String,
        block: suspend () -> Unit,
    ): Job {
        val stableUri = uriString.trim()
        if (stableUri.isBlank()) return viewModelScope.launch { }
        val previous = pendingReaderStateSaveJobs[stableUri]
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            previous?.join()
            block()
        }
        pendingReaderStateSaveJobs[stableUri] = job
        job.invokeOnCompletion {
            if (pendingReaderStateSaveJobs[stableUri] == job) {
                pendingReaderStateSaveJobs.remove(stableUri)
            }
        }
        job.start()
        return job
    }

    private suspend fun awaitReaderStateWrites(uriString: String?, bookId: String?) {
        uriString?.trim()?.takeIf { it.isNotBlank() }?.let { stableUri ->
            pendingReaderStateSaveJobs[stableUri]?.join()
        }
        bookId?.trim()?.takeIf { it.isNotBlank() }?.let { stableBookId ->
            pendingBookmarkSaveJobs[stableBookId]?.join()
        }
    }

    fun saveBookmarks(bookId: String, bookmarksJson: String, documentUri: Uri? = null) {
        Timber.d("saveBookmarks called. bookId=$bookId, bookmarksJson=$bookmarksJson")
        val stableBookId = bookId.trim()
        if (stableBookId.isNotEmpty()) {
            enqueueBookmarkSave(stableBookId, bookmarksJson, documentUri)
            return
        }

        val stableDocumentUri = documentUri?.toString()?.takeIf(String::isNotBlank)
        if (stableDocumentUri == null) {
            Timber.w("PdfBookmarkDebug: saveBookmarks called with no stable book identity.")
            return
        }

        // URI lookup is only a fallback for callers that genuinely do not have a
        // book id. Never consult mutable selected-reader state here: a delayed
        // save from one reader must not be applied to whichever reader is active
        // when that save happens to run.
        viewModelScope.launch {
            val item = bookStore.getFileByUri(stableDocumentUri)
            if (item != null) {
                enqueueBookmarkSave(item.bookId, bookmarksJson, documentUri)
            } else {
                Timber.w("PdfBookmarkDebug: no book found for explicit URI=$stableDocumentUri")
            }
        }
    }

    private fun enqueueBookmarkSave(bookId: String, bookmarksJson: String, documentUri: Uri?) {
        val revision = synchronized(bookmarkSaveRevisions) {
            val nextRevision = (bookmarkSaveRevisions[bookId] ?: 0L) + 1L
            bookmarkSaveRevisions[bookId] = nextRevision
            nextRevision
        }

        Timber.d(
            "Queueing bookmark save bookId=$bookId revision=$revision " +
                "documentUri=${documentUri?.toString()}"
        )
        val previous = pendingBookmarkSaveJobs[bookId]
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            // Serialize callbacks for one book. The revision check below
            // still coalesces rapid edits, while the chain makes close() able
            // to await every callback that was queued before it.
            previous?.join()
            bookmarkSaveMutex.withLock {
                val latestRevision = synchronized(bookmarkSaveRevisions) {
                    bookmarkSaveRevisions[bookId]
                }
                if (revision != latestRevision) {
                    Timber.d(
                        "Skipping stale bookmark save bookId=$bookId revision=$revision " +
                            "latest=$latestRevision"
                    )
                    return@withLock
                }
                bookStore.updateBookmarks(bookId, bookmarksJson)
            }
        }
        pendingBookmarkSaveJobs[bookId] = job
        job.invokeOnCompletion {
            if (pendingBookmarkSaveJobs[bookId] == job) {
                pendingBookmarkSaveJobs.remove(bookId)
            }
        }
        job.start()
    }

    suspend fun savePdfReadingPosition(uri: Uri, page: Int, totalPages: Int) {
        val progress = com.aryan.reader.shared.pdfReadingProgressPercentage(page, totalPages)
        Timber.tag("PdfPositionDebug").i(
            "ViewModel: Save request triggered | Page: $page | Total: $totalPages | Progress: $progress | URI: ${uri.lastPathSegment}"
        )
        val job = enqueueReaderStateSave(uri.toString()) {
            // App-managed cloud books resolve through the canonical Room URI;
            // legacy rows may carry a differently-encoded file URI for the same
            // path, so fall back to the selected book when both decode to the
            // same canonical file before giving up.
            val existing = bookStore.getFileByUri(uri.toString())
                ?: selectedBookRowForManagedFile(uri)
            existing?.let { book ->
                logCloudSyncTrace {
                    "android.reader.pdf_position_save_start book=${book.bookId} beforeTs=${book.lastModifiedTimestamp} " +
                        "beforeReadTs=${book.effectiveReadingPositionModifiedTimestamp()} page=$page progress=$progress"
                }
                val positionOperation = cloudFolderOperationId("reader-position", book.bookId, "pdf")
                val positionCorrelation = cloudFolderSyncCorrelationId("reader-position", book.bookId, "pdf")
                cloudFolderLogD(
                    "event=reader_position_save_start operation=$positionOperation correlation=$positionCorrelation " +
                        "book=${cloudFolderSafeId(book.bookId)} kind=pdf " +
                        "beforeReadTs=${book.effectiveReadingPositionModifiedTimestamp()} page=$page progress=$progress",
                )
                bookStore.updatePdfReadingPosition(
                    uriString = book.uriString ?: uri.toString(), page = page, progress = progress
                )
                val updated = bookStore.getFileByBookId(book.bookId)
                logCloudSyncTrace {
                    "android.reader.pdf_position_save_done beforeTs=${book.lastModifiedTimestamp} " +
                        (updated?.cloudSyncTraceSummary("after") ?: "after=null")
                }
                cloudFolderLogD(
                    "event=reader_position_save_end operation=$positionOperation correlation=$positionCorrelation " +
                        "book=${cloudFolderSafeId(book.bookId)} kind=pdf result=${if (updated != null) "success" else "missing"} " +
                        "afterReadTs=${updated?.effectiveReadingPositionModifiedTimestamp() ?: 0L} " +
                        "afterPage=${updated?.lastPage ?: "none"} afterProgress=${updated?.progressPercentage ?: "none"}",
                )
                queueCloudMetadataUpload(book.bookId, reason = "pdf_position")
            } ?: run {
                cloudFolderLogW(
                    "event=reader_position_save_skip kind=pdf reason=book_not_found " +
                        "book=${cloudFolderSafeId(uri.lastPathSegment.orEmpty())} page=$page",
                )
                Timber.tag("PdfPositionDebug").e("ViewModel: Save aborted. Could not resolve file item from URI in DB.")
            }
        }
        // Preserve the method's existing suspend contract: callers that await
        // a PDF save still observe the committed Room write before returning.
        job.join()
    }

    fun exportLogsToFile(activityContext: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("Generating logcat dump for debugging...")
                val process = Runtime.getRuntime().exec("logcat -d -v threadtime -t 5000")
                val logText = process.inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
                val artifact = AndroidShareArtifactManager.create(
                    context = appContext,
                    requestedFileName = "debug_logs_${System.currentTimeMillis()}.txt",
                    write = { output ->
                    output.write(logText.toByteArray())
                    },
                )
                val intent = AndroidShareArtifactManager.buildShareIntent(
                    artifact = artifact,
                    mimeType = "text/plain",
                    title = "App Debug Logs",
                )

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
        // Cloud placeholders/app-managed roots are reconciled by the
        // account-scoped worker and must not make the legacy folder worker
        // show a misleading "no enabled folder" banner.
        val hasFolder = _internalState.value.syncedFolders.any {
            it.localSyncEnabled && !it.isCloudPlaceholder
        }

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
                    if (!hasFolder) {
                        // If no legacy folder worker will be started below,
                        // still refresh selected/bound cloud-folder roots and
                        // discover deferred incoming roots from Drive.
                        enqueueCloudFolderManualRefresh(reason = "library_refresh")
                    }
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
        if (item.type in PDF_VIEWER_FILE_TYPES) {
            Timber.tag(PDF_RENAME_TRACE_TAG).i(
                "viewModel.recentClick bookId=${item.bookId} type=${item.type} uri=${item.uriString} " +
                    "displayName=${item.displayName} title=${item.title} customName=${item.customName} " +
                    "isAvailable=${item.isAvailable} sourceFolderUri=${item.sourceFolderUri} " +
                    "usePdfFileName=${_internalState.value.usePdfFileNameAsDisplayName}"
            )
        }
        val currentSelection = _internalState.value.contextualActionItems
        if (currentSelection.isNotEmpty()) {
            Timber.d("Toggling selection for: ${item.displayName}")
            _internalState.update { it.withSharedLibraryAction(SharedLibraryAction.BookSelectionToggled(item.bookId)) }
            Timber.d("New selection size: ${_internalState.value.contextualActionItems.size}")
        } else {
            if (item.sourceFolderUri != null && item.uriString != null) {
                viewModelScope.launch {
                    val location = withContext(Dispatchers.IO) {
                        resolveFolderBookLocation(item)
                    }

                    if (location.accountId != null &&
                        (authRepository.getSignedInUser()?.uid?.trim() != location.accountId ||
                            _internalState.value.currentUser?.uid?.trim() != location.accountId)
                    ) {
                        Timber.tag(CLOUD_FOLDER_SYNC_LOG_TAG)
                            .w("event=book_open_blocked reason=account_changed")
                        return@launch
                    }

                    if (location.uri == null && location.canConfirmMissing) {
                        Timber.tag("FolderSync")
                            .i("LazyCleanup: File ${item.displayName} missing. Removing.")
                        bookStore.deleteFilePermanently(listOf(item.bookId))
                        showBanner(appContext.getString(R.string.banner_file_deleted_from_folder))
                        return@launch
                    }
                    if (location.uri == null) {
                        Timber.tag(CLOUD_FOLDER_SYNC_LOG_TAG)
                            .w("event=book_open_blocked reason=unverified_folder_uri")
                        _internalState.update {
                            it.copy(errorMessage = appContext.getString(R.string.error_file_location_not_found))
                        }
                        return@launch
                    }

                    Timber.d("Recent file clicked (opening): ${item.displayName}")
                    if (item.isAvailable) {
                        openBook(location.uri, item.bookId, item.type, item.displayName)
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

    /**
     * Resolve a folder-backed book without asking DocumentFile to interpret a
     * `file://` URI. App-private cloud roots are account-scoped and must stay
     * beneath the registered cloud-folder directory; SAF roots retain the
     * provider existence check used by older builds.
     */
    private fun resolveFolderBookLocation(item: RecentFileItem): FolderBookLocation {
        val sourceFolderUriString = item.sourceFolderUri ?: return FolderBookLocation(null, false)
        val fileUriString = item.uriString ?: return FolderBookLocation(null, true)
        val sourceFolderUri = runCatching { sourceFolderUriString.toUri() }.getOrNull()
            ?: return FolderBookLocation(null, true)

        if (sourceFolderUri.scheme.equals("file", ignoreCase = true)) {
            val accountId = authRepository.getSignedInUser()?.uid?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return FolderBookLocation(null, false)
            // A file-backed source folder is an app-private cloud root. If it
            // is not registered for the active account, do not fall through to
            // an arbitrary File/DocumentFile path and do not delete the row.
            if (CloudFolderAppStoragePrefs.rootIdForUri(appContext, accountId, sourceFolderUriString) == null) {
                return FolderBookLocation(null, false)
            }
            val managedFile = CloudFolderAppStoragePrefs.resolveManagedFile(
                context = appContext,
                accountId = accountId,
                sourceFolderUri = sourceFolderUriString,
                fileUriString = fileUriString,
            ) ?: return FolderBookLocation(null, false)
            if (managedFile.exists() && !managedFile.isFile) {
                // A path-type mismatch is not proof that the indexed book was
                // deleted. Keep the row so a later repair can report/fix it.
                return FolderBookLocation(null, false)
            }
            return FolderBookLocation(
                uri = managedFile.takeIf { it.isFile }?.toUri(),
                canConfirmMissing = true,
                accountId = accountId,
            )
        }

        val fileUri = runCatching { fileUriString.toUri() }.getOrNull()
            ?: return FolderBookLocation(null, true)
        val document = runCatching { DocumentFile.fromSingleUri(appContext, fileUri) }
            .getOrNull()
        return when {
            document?.exists() == true && document.isFile -> FolderBookLocation(fileUri, true)
            document?.exists() == true -> FolderBookLocation(null, false)
            else -> FolderBookLocation(null, true)
        }
    }

    /**
     * Fallback row for URI-keyed reader writes when the raw reader URI does
     * not string-match Room's stored uriString. Older builds stored app-managed
     * cloud-folder files under a differently-encoded file URI for the same
     * path; comparing canonical paths keeps position saves attached to the
     * right book instead of silently no-opping.
     */
    private suspend fun selectedBookRowForManagedFile(uri: Uri): RecentFileItem? {
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        val selectedBookId = _internalState.value.selectedBookId ?: return null
        val candidate = bookStore.getFileByBookId(selectedBookId) ?: return null
        val candidateUri = candidate.uriString?.toUri() ?: return null
        if (!candidateUri.scheme.equals("file", ignoreCase = true)) return null
        val readerPath = runCatching { File(uri.path!!).canonicalPath }.getOrNull() ?: return null
        val storedPath = runCatching { File(candidateUri.path!!).canonicalPath }.getOrNull() ?: return null
        return candidate.takeIf { readerPath == storedPath }
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
                            val latestBookState = bookStore.getFileByBookId(book.bookId)
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
            replaceBookSelectionWithVisibleBooks(
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
            replaceBookSelectionWithVisibleBooks(
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
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.CreateDialogShown()))
        }
    }

    fun showCreateShelfDialogForSelectedBooks(bookIds: Set<String>) {
        val sanitizedBookIds = SharedLibraryEditor.cleanBookIds(bookIds)
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.CreateDialogShown(sanitizedBookIds)))
        }
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
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.CreateDialogDismissed))
        }
    }

    fun createShelf(name: String) {
        val selectedBookIds = SharedLibraryEditor.cleanBookIds(_internalState.value.createShelfSelectedBookIds)
        viewModelScope.launch {
            val shelfId = libraryMutationController.createShelf(name, selectedBookIds) ?: return@launch
            if (selectedBookIds.isNotEmpty()) {
                _internalState.update { it.withClearedLibraryBookSelection() }
            }
            dismissCreateShelfDialog()
        }
    }

    fun setMainScreenPage(page: Int) {
        val sanitizedPage = page.coerceIn(0, 2)
        if (_internalState.value.mainScreenStartPage == sanitizedPage) return
        _internalState.update {
            it.copy(
                mainScreenStartPage = sanitizedPage,
            ).withClearedLibraryBookSelection()
        }
        persistLibraryLandingState()
    }

    fun setLibraryScreenPage(page: Int) {
        val maxLibraryPage = if (BuildConfig.IS_OFFLINE) 2 else 3
        val sanitizedPage = page.coerceIn(0, maxLibraryPage)
        if (_internalState.value.libraryScreenStartPage == sanitizedPage) return
        _internalState.update {
            it.copy(
                libraryState = it.libraryState.reduce(
                    SharedLibraryAction.LibraryPageChanged(sanitizedPage),
                ),
            )
        }
        persistLibraryLandingState()
    }

    fun setUnifiedLibrarySection(section: Int) {
        val sanitizedSection = section.coerceIn(0, 4)
        if (_internalState.value.unifiedLibrarySection == sanitizedSection) return
        _internalState.update { it.copy(unifiedLibrarySection = sanitizedSection) }
        persistLibraryLandingState()
    }

    fun setUnifiedLibraryListView(useListView: Boolean) {
        if (_internalState.value.unifiedLibraryListView == useListView) return
        _internalState.update { it.copy(unifiedLibraryListView = useListView) }
        persistLibraryLandingState()
    }

    fun navigateToShelf(id: String) {
        _internalState.update {
            it.copy(
                shelfState = it.shelfState.reduce(AppShelfAction.ShelfOpened(id)),
                mainScreenStartPage = 1,
                libraryState = it.libraryState.reduce(SharedLibraryAction.LibraryPageChanged(1)),
            )
        }
        persistLibraryLandingState()
    }

    fun showRenameShelfDialog(shelfId: String) {
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.RenameDialogChanged(shelfId)))
        }
    }

    fun dismissRenameShelfDialog() {
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.RenameDialogChanged(null)))
        }
    }

    fun showDeleteShelfDialog(shelfId: String) {
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.DeleteDialogChanged(shelfId)))
        }
    }

    fun dismissDeleteShelfDialog() {
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.DeleteDialogChanged(null)))
        }
    }

    fun renameShelf(shelfId: String, newName: String) {
        val cleanName = SharedLibraryEditor.cleanShelfName(newName)
        if (!SharedLibraryEditor.canMutateShelf(shelfId) || cleanName == null) {
            dismissRenameShelfDialog()
            return
        }
        viewModelScope.launch {
            libraryMutationController.renameShelf(shelfId, cleanName)
            _internalState.update {
                it.copy(shelfState = it.shelfState.reduce(AppShelfAction.ShelfRenameCompleted(shelfId)))
            }
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
                it.copy(shelfState = it.shelfState.reduce(AppShelfAction.ShelfDeleted))
            }
            persistLibraryLandingState()
            libraryMutationController.deleteShelf(shelfId)
        }
    }

    fun unselectShelf() {
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.ShelfClosed))
        }
        persistLibraryLandingState()
    }

    fun navigateBackFromShelf() {
        val currentShelf = uiState.value.shelves.find { it.id == _internalState.value.viewingShelfId }
        val parentShelfId = currentShelf?.takeIf { it.type == ShelfType.FOLDER }?.parentShelfId
        if (parentShelfId != null) {
            _internalState.update {
                it.copy(shelfState = it.shelfState.reduce(AppShelfAction.ParentShelfOpened(parentShelfId)))
            }
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
            libraryMutationController.removeBooksFromShelf(targetShelfId, bookIdsToRemove)
            clearContextualAction()
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
            libraryMutationController.deleteShelves(shelvesToDelete)
            clearShelfContextualAction()
        }
    }

    fun showAddBooksToShelf() {
        _internalState.update {
            it.copy(
                shelfState = it.shelfState.reduce(
                    AppShelfAction.AddBooksStarted(AddBooksSource.UNSHELVED),
                ),
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
            if (shelf.isSmart) return@launch
            val crossRefs = db.shelfDao().getCrossRefsForShelf(shelfId)
            val manualOnlyBookIds = bookStore.getAllFilesForSync()
                .filter { it.isManualOnlyReaderFile() }
                .mapTo(mutableSetOf()) { it.bookId }
            val bookIds = crossRefs.map { it.bookId }
                .filterNot { it in manualOnlyBookIds }

            val shelfMetadata = ShelfMetadata(
                shelfId = shelf.id,
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
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.AddBooksDismissed))
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
            libraryMutationController.addBooksToShelves(bookIdsToAdd, setOf(shelfId))
            _internalState.update {
                it.copy(shelfState = it.shelfState.reduce(AppShelfAction.AddBooksCompleted))
            }
            persistLibraryLandingState()
        }
    }

    fun setAddBooksSource(source: AddBooksSource) {
        _internalState.update {
            it.copy(shelfState = it.shelfState.reduce(AppShelfAction.AddBooksSourceChanged(source)))
        }
        prefs.edit { putString(KEY_ADD_BOOKS_SOURCE, source.name) }
    }

    private fun persistLibraryLandingState() {
        val state = _internalState.value
        val resolvedUiState = uiState.value
        prefs.edit {
            putInt(KEY_MAIN_SCREEN_START_PAGE, state.mainScreenStartPage)
            putInt(KEY_LIBRARY_SCREEN_START_PAGE, state.libraryScreenStartPage)
            putInt(KEY_UNIFIED_LIBRARY_SECTION, state.unifiedLibrarySection)
            putBoolean(KEY_UNIFIED_LIBRARY_LIST_VIEW, state.unifiedLibraryListView)
            putString(KEY_LAST_VIEWING_SHELF_ID, resolvedUiState.viewingShelfId)
            putBoolean(KEY_LAST_ADDING_BOOKS_TO_SHELF, resolvedUiState.isAddingBooksToShelf)
        }
    }

    fun toggleBookSelectionForAdding(bookId: String) {
        _internalState.update { state ->
            state.copy(shelfState = state.shelfState.reduce(AppShelfAction.BookForAddingToggled(bookId)))
        }
    }

    fun deleteContextualItemsPermanently() {
        val requestState = _internalState.value
        val itemsToRemove = requestState.contextualActionItems
        // Capture the account at click time. Reading currentUser only after
        // launching work can associate an old selection with a newly signed-in
        // account.
        val requestedSyncEnabled = requestState.isSyncEnabled
        val requestedAccountId = requestState.currentUser?.uid?.trim()
            ?.takeIf { it.isNotBlank() }
        if (itemsToRemove.isNotEmpty()) {
            _internalState.update { it.withClearedLibraryBookSelection() }

            viewModelScope.launch {
                val (folderBooks, managedBooks) = itemsToRemove.partition { it.sourceFolderUri != null }
                var managedDeletionSucceeded = true
                var remoteDeletionQueued = false

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

                        bookStore.deleteFilePermanently(idsToDeleteLocally)
                    }

                    if (managedBooks.isNotEmpty()) {
                        val cloudRequestStillValid = requestedSyncEnabled &&
                            requestedAccountId != null &&
                            _internalState.value.isSyncEnabled &&
                            _internalState.value.currentUser?.uid?.trim() == requestedAccountId &&
                            authRepository.getSignedInUser()?.uid?.trim() == requestedAccountId

                        if (requestedSyncEnabled && !cloudRequestStillValid) {
                            managedDeletionSucceeded = false
                            _internalState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = appContext.getString(R.string.error_clear_all_data),
                                )
                            }
                        } else if (cloudRequestStillValid) {
                            _internalState.update {
                                it.copy(
                                    bannerMessage = BannerMessage(appContext.getString(R.string.banner_deleting_all_devices))
                                )
                            }
                            try {
                                // Revalidate after waiting for the account
                                // barrier; a sign-out/account switch while a
                                // full sync was in progress must not retarget
                                // this selection.
                                val accountId = requireNotNull(requestedAccountId)
                                CloudBookSyncBarrier.withAccountLock(accountId) {
                                    check(
                                        _internalState.value.isSyncEnabled &&
                                            _internalState.value.currentUser?.uid?.trim() == requestedAccountId &&
                                            authRepository.getSignedInUser()?.uid?.trim() == requestedAccountId
                                    ) { "Account changed while deleting cloud books" }

                                    val cloudItems = managedBooks.filterNot(RecentFileItem::isManualOnlyReaderFile)
                                    val localOnlyItems = managedBooks.filter(RecentFileItem::isManualOnlyReaderFile)
                                    if (localOnlyItems.isNotEmpty()) {
                                        localOnlyItems.forEach { item ->
                                            cleanupBookDataLocally(item.bookId)
                                            bookStore.deleteFilePermanently(listOf(item.bookId))
                                        }
                                    }
                                    if (cloudItems.isNotEmpty()) {
                                        // Persist before touching the local rows. A
                                        // crash, process death, or missing token
                                        // leaves these account-scoped intents for
                                        // the WorkManager retry path.
                                        check(
                                            cloudBookDeletePersistence.enqueue(
                                                accountId,
                                                cloudItems.map { item ->
                                                    CloudBookTombstone(
                                                        bookId = item.bookId,
                                                        type = item.type.name,
                                                        deletedAt = System.currentTimeMillis(),
                                                    )
                                                },
                                            )
                                        ) { "Unable to persist cloud-book deletion intent" }
                                        remoteDeletionQueued = true
                                        // Local visibility is intentionally
                                        // finalized immediately while the
                                        // remote worker runs asynchronously.
                                        bookStore.deleteFilePermanently(cloudItems.map { it.bookId })

                                        runCatching {
                                            CloudBookDeleteWorker.enqueue(appContext, accountId)
                                        }.onFailure { error ->
                                            // The queue remains durable; the
                                            // next sync/startup pass schedules it.
                                            Timber.e(error, "Unable to schedule cloud-book deletion worker")
                                        }
                                    }
                                }

                                _internalState.update {
                                    it.copy(
                                        bannerMessage = BannerMessage(appContext.getString(R.string.banner_deleting_all_devices))
                                    )
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error during permanent deletion")
                                managedDeletionSucceeded = false
                                _internalState.update {
                                    it.copy(
                                        errorMessage = appContext.getString(R.string.error_cloud_delete_pending)
                                    )
                                }
                            }
                        } else {
                            bookStore.deleteFilePermanently(managedBooks.map { it.bookId })
                            managedBooks.forEach { item ->
                                cleanupBookDataLocally(item.bookId)
                            }
                        }
                    }
                }

                if (managedDeletionSucceeded) {
                    val totalRemoved = folderBooks.size + managedBooks.size
                    _internalState.update {
                        it.copy(
                            bannerMessage = if (remoteDeletionQueued) {
                                BannerMessage(appContext.getString(R.string.banner_deleting_all_devices))
                            } else {
                                BannerMessage(appContext.resources.getQuantityString(R.plurals.banner_books_removed_library, totalRemoved, totalRemoved))
                            }
                        )
                    }
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
        CloudFolderHeadListenerCoordinator.clearEligibility(appContext)
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

            val allFiles = bookStore.getAllFilesForSync()
            val reflowBooks = allFiles.filter { it.bookId.endsWith("_reflow") }

            if (reflowBooks.isNotEmpty()) {
                val reflowBookIds = reflowBooks.map { it.bookId }

                reflowBookIds.forEach { bookId ->
                    cleanupBookDataLocally(bookId)
                }

                bookStore.deleteFilePermanently(reflowBookIds)
            }

            withContext(Dispatchers.Main) {
                showBanner(appContext.getString(R.string.banner_reflow_cache_cleared))
            }
        }
    }

    fun updateCustomName(bookId: String, newName: String?) {
        viewModelScope.launch {
            val item = bookStore.getFileByBookId(bookId)
            if (item != null) {
                Timber.tag(PDF_RENAME_TRACE_TAG).i(
                    "viewModel.rename.before bookId=$bookId type=${item.type} displayName=${item.displayName} " +
                        "title=${item.title} oldCustomName=${item.customName} newCustomName=$newName " +
                        "uri=${item.uriString} sourceFolderUri=${item.sourceFolderUri}"
                )
                bookStore.updateCustomName(bookId, newName)
                val savedItem = bookStore.getFileByBookId(bookId)
                Timber.tag(PDF_RENAME_TRACE_TAG).i(
                    "viewModel.rename.after bookId=$bookId savedDisplayName=${savedItem?.displayName} " +
                        "savedTitle=${savedItem?.title} savedCustomName=${savedItem?.customName} " +
                        "savedUri=${savedItem?.uriString}"
                )

                if (uiState.value.isSyncEnabled && savedItem != null) {
                    uploadSingleBookMetadata(savedItem)
                }

                if (savedItem?.sourceFolderUri != null) {
                    launch(Dispatchers.IO) {
                        val metadataSaved = folderMirrorStore.syncLocalMetadataToFolder(bookId, force = true)
                        if (!metadataSaved) {
                            Timber.tag("FolderAnnotationSync").w(
                                "Metadata sidecar write failed after rename; keeping local state for retry: $bookId"
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateBookMetadata(bookId: String, metadata: BookMetadataEdit) {
        viewModelScope.launch {
            val currentItem = bookStore.getFileByBookId(bookId) ?: return@launch
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
                val coverPath = result.cover?.let { cover ->
                    currentItem.uriString?.toUri()?.let { uri ->
                        bookArtifactStore.saveEmbeddedCoverToCache(cover.bytes, uri, cover.extension)
                    }
                }
                bookStore.updateUserEditableMetadata(
                    bookId = bookId,
                    metadata = savedMetadata,
                    fileSize = result.fileSize,
                    fileContentModifiedTimestamp = result.fileContentModifiedTimestamp,
                    coverImagePath = coverPath
                )
                val updatedItem = bookStore.getFileByBookId(bookId)
                if (updatedItem != null && uiState.value.isSyncEnabled && updatedItem.sourceFolderUri == null) {
                    uploadNewBookAndMetadata(updatedItem)
                }
                showBanner("EPUB metadata updated.")
            }
        }
    }

    fun restoreOriginalBookMetadata(bookId: String) {
        viewModelScope.launch {
            val currentItem = bookStore.getFileByBookId(bookId) ?: return@launch
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
                description = currentItem.originalDescription,
                restoreOriginalCover = true
            )
            val editResult = epubMetadataFileEditor.writeMetadata(currentItem, metadata)
            editResult.onFailure { error ->
                Timber.e(error, "Failed to restore EPUB metadata for $bookId")
                showBanner("Could not restore EPUB metadata.", isError = true)
            }.onSuccess { result ->
                cleanupBookDataLocally(bookId)
                val coverPath = result.cover?.let { cover ->
                    currentItem.uriString?.toUri()?.let { uri ->
                        bookArtifactStore.saveEmbeddedCoverToCache(cover.bytes, uri, cover.extension)
                    }
                }
                bookStore.restoreOriginalMetadata(
                    bookId = bookId,
                    fileSize = result.fileSize,
                    fileContentModifiedTimestamp = result.fileContentModifiedTimestamp,
                    coverImagePath = coverPath
                )
                val restoredItem = bookStore.getFileByBookId(bookId)
                if (restoredItem != null && uiState.value.isSyncEnabled && restoredItem.sourceFolderUri == null) {
                    uploadNewBookAndMetadata(restoredItem)
                }
                showBanner("Original EPUB metadata restored.")
            }
        }
    }

    fun closeAllTabs() {
        Timber.tag("PdfTabSync").i("ViewModel: closeAllTabs called")
        val tabState = closeAllTabs(
            current = _internalState.value
        )
        persistTabState(tabState.openTabIds, tabState.activeTabBookId)
        _internalState.update {
            it.copy(
                tabState = tabState.tabState,
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
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_APP_OPEN_COUNT = "app_open_count"
        internal const val KEY_SYNCED_FOLDER_URI = "synced_folder_uri"
        internal const val KEY_LAST_FOLDER_SCAN_TIME = "last_folder_scan_time"
        internal const val KEY_PINNED_HOME = "pinned_home_books"
        internal const val KEY_PINNED_LIBRARY = "pinned_library_books"
        private const val KEY_RECENT_FILES_LIMIT = "recent_files_limit"
        private const val MAX_OPEN_PDF_TABS = 20
        private const val KEY_TABS_ENABLED = "tabs_enabled"
        private const val KEY_OPEN_TAB_IDS = "open_tab_ids"
        private const val KEY_ACTIVE_TAB = "active_tab_book_id"
        private const val KEY_LAST_OPEN_BOOK_ID = "last_open_book_id"
        private const val KEY_LAST_OPEN_FILE_TYPE = "last_open_file_type"
        private const val KEY_EXTERNAL_FILE_BEHAVIOR = "external_file_behavior"
        private const val EXTERNAL_FILE_BEHAVIOR_ASK = "ASK"
        private const val EXTERNAL_FILE_BEHAVIOR_TEMPORARY = "TEMPORARY"
        private const val KEY_PENDING_EXTERNAL_FILE_REMOVALS = "pending_external_file_removals"
        private const val KEY_USE_STRICT_FILE_FILTER = "use_strict_file_filter"
        private const val KEY_USE_PDF_FILE_NAME_AS_DISPLAY_NAME = "use_pdf_file_name_as_display_name"
        private const val KEY_PDF_SPLIT_WORKSPACE = "pdf_split_workspace"
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
