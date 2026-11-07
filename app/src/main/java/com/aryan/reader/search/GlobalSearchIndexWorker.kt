package com.aryan.reader.search

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubParser
import com.aryan.reader.epub.Fb2Parser
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.epub.OdtParser
import com.aryan.reader.epub.SingleFileImporter
import com.aryan.reader.epub.contentFilePath
import com.aryan.reader.pdf.PdfiumCoreProvider
import com.aryan.reader.pdf.PdfiumEngineProvider
import com.aryan.reader.pptx.PptxDocumentParser
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.search.SharedSearchChunkIndexer
import com.aryan.reader.shared.search.SharedGlobalSearchCoordinator
import com.aryan.reader.shared.search.SharedGlobalSearchPolicy
import com.aryan.reader.shared.search.SharedSearchChunk
import com.aryan.reader.search.GlobalSearchLog
import com.aryan.reader.search.brief
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Background per-book indexer for library-wide inside-files search.
 *
 * One unique work per book (`GlobalSearchIndexer.workName`), REPLACE so a
 * re-import or content change supersedes the stale run. Extracts native
 * text only — never OCR: image PDFs index zero pages and persist a
 * NO_NATIVE_TEXT tombstone so they are not re-extracted on every enqueue.
 *
 * Result contract: success when terminally done (indexed, tombstoned, or
 * deterministically unparseable); retry on transient IO or mid-run stop;
 * failure only for missing inputs or a vanished file (which also GCs the
 * book's persisted index). A later contentHash change re-enqueues and
 * recovers from failure.
 */
class GlobalSearchIndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID).orEmpty()
        val uriString = inputData.getString(KEY_URI).orEmpty()
        val typeName = inputData.getString(KEY_TYPE).orEmpty()
        val displayName = inputData.getString(KEY_NAME).orEmpty()
        val contentHash = inputData.getString(KEY_HASH).orEmpty()
        if (bookId.isBlank() || uriString.isBlank() || typeName.isBlank() || contentHash.isBlank()) {
            return Result.failure()
        }
        val type = runCatching { FileType.valueOf(typeName) }.getOrNull()
            ?: run {
                GlobalSearchLog.d { "input rejected: unknown type='$typeName' bookId=$bookId" }
                return Result.failure()
            }
        if (bookId.isBlank() || uriString.isBlank() || contentHash.isBlank()) {
            GlobalSearchLog.d { "input rejected: blank field bookId='$bookId' uri='$uriString' hash='$contentHash'" }
            return Result.failure()
        }

        val storage = GlobalSearchFileStorage.fromFilesDir(applicationContext.filesDir)
        val coordinator = SharedGlobalSearchCoordinator(storage)
        if (!coordinator.needsIndex(bookId, contentHash)) {
            GlobalSearchLog.d { "already fresh, no work bookId=$bookId hash=$contentHash" }
            return Result.success()
        }
        if (!SharedGlobalSearchPolicy.isContentSearchable(type)) {
            GlobalSearchLog.d { "unsupported type=$type bookId=$bookId -> removing payload" }
            coordinator.removeBook(bookId)
            return Result.success()
        }

        val uri = Uri.parse(uriString)
        GlobalSearchLog.d { "start bookId=$bookId type=$type hash=$contentHash uri=$uriString" }
        val chunks = try {
            extractNativeChunks(uri, type, bookId, displayName)
        } catch (_: FileNotFoundException) {
            GlobalSearchLog.w { "failure: file vanished bookId=$bookId uri=$uriString" }
            coordinator.removeBook(bookId)
            return Result.failure()
        } catch (e: IOException) {
            GlobalSearchLog.w { "retry: transient IO bookId=$bookId cause=${e.brief()}" }
            return Result.retry()
        } catch (e: Exception) {
            GlobalSearchLog.w { "failure: unparseable bookId=$bookId cause=${e.brief()}" }
            coordinator.upsertBookChunks(bookId, contentHash, emptyList())
            return Result.success()
        }
        if (isStopped) {
            GlobalSearchLog.w { "retry: stopped mid-run bookId=$bookId chunksSoFar=${chunks.size}" }
            return Result.retry()
        }
        val indexed = coordinator.upsertBookChunks(bookId, contentHash, chunks)
        GlobalSearchLog.d {
            "done bookId=$bookId chunks=${chunks.size} indexed=$indexed " +
                "tombstone=${!indexed} status=${coordinator.statusOf(bookId)}"
        }
        return Result.success()
    }

    private suspend fun extractNativeChunks(
        uri: Uri,
        type: FileType,
        bookId: String,
        displayName: String,
    ): List<SharedSearchChunk> = withContext(Dispatchers.IO) {
        when (type) {
            FileType.PDF -> extractPdfNativePages(uri)
            FileType.PPTX -> extractPptxSlides(uri)
            FileType.EPUB, FileType.MOBI, FileType.FB2, FileType.ODT, FileType.FODT,
            FileType.MD, FileType.TXT, FileType.HTML, FileType.DOCX, FileType.DOC ->
                extractEpubBookChapters(uri, type, bookId, displayName)
            FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT,
            FileType.AUDIOBOOK, FileType.UNKNOWN -> emptyList()
        }
    }

    /**
     * All EPUB_READER-surface types funnel through the same parsers the
     * reader uses ([MainViewModel.restoreEpubReaderBook] dispatch), then
     * read chapter HTML with the same Jsoup source in-reader search uses,
     * so the index agrees with what readers find on the page.
     */
    private suspend fun extractEpubBookChapters(
        uri: Uri,
        type: FileType,
        bookId: String,
        displayName: String,
    ): List<SharedSearchChunk> {
        val context = applicationContext
        // A private extraction dir, never the reader's active one. Without
        // this the parsers run in ACTIVE_CACHE mode and
        // resetActiveBookDir(bookId) deletes the open book's chapters —
        // that is what made an open reader show "no chapters available".
        val extractionDir = File(context.cacheDir, "gsi_extract_$bookId")
        GlobalSearchLog.d { "epub-parse start bookId=$bookId type=$type uri=$uri dir=${extractionDir.absolutePath}" }
        try {
            val epubBook: EpubBook = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (type) {
                    FileType.EPUB -> EpubParser(context).createEpubBook(
                        inputStream = stream,
                        bookId = bookId,
                        originalBookNameHint = displayName,
                        extractionDirOverride = extractionDir,
                    )
                    FileType.MOBI -> MobiParser(context).createMobiBook(
                        inputStream = stream,
                        bookId = bookId,
                        originalBookNameHint = displayName,
                        extractionDirOverride = extractionDir,
                        // Null means DRM-protected/invalid or a missing native
                        // lib on this device: deterministic, so terminal (the
                        // generic handler tombstones it) rather than IOException
                        // retry, which would back off forever on a DRM file.
                    ) ?: throw IllegalStateException("MOBI parser returned null for $bookId")
                    FileType.FB2 -> Fb2Parser(context).createFb2Book(
                        inputStream = stream,
                        bookId = bookId,
                        originalBookNameHint = displayName,
                        extractionDirOverride = extractionDir,
                    )
                    FileType.ODT, FileType.FODT -> OdtParser(context).createOdtBook(
                        inputStream = stream,
                        bookId = bookId,
                        originalBookNameHint = displayName,
                        isFlat = type == FileType.FODT,
                        extractionDirOverride = extractionDir,
                    )
                    else -> SingleFileImporter(context).importSingleFile(
                        inputStream = stream,
                        type = type,
                        originalBookNameHint = displayName,
                        bookId = bookId,
                        extractionDirOverride = extractionDir,
                    )
                }
            } ?: throw FileNotFoundException("Cannot open $uri")
            GlobalSearchLog.d {
                "epub-parse ok bookId=$bookId chapters=${epubBook.chapters.size} " +
                    "base=${epubBook.extractionBasePath}"
            }
            return readChapterChunks(epubBook, bookId)
        } finally {
            extractionDir.deleteRecursively()
        }
    }

    private fun readChapterChunks(epubBook: EpubBook, bookId: String): List<SharedSearchChunk> {
        val out = mutableListOf<SharedSearchChunk>()
        var skipped = 0
        epubBook.chapters.forEachIndexed { chapterIndex, chapter ->
            if (isStopped) return out
            try {
                val htmlFile = File(epubBook.extractionBasePath, chapter.contentFilePath())
                if (!htmlFile.isFile) {
                    skipped++
                    if (skipped <= 3) {
                        GlobalSearchLog.d {
                            "chapter file missing bookId=$bookId ch=$chapterIndex " +
                                "rel=${chapter.contentFilePath()} path=${htmlFile.absolutePath}"
                        }
                    }
                    return@forEachIndexed
                }
                val doc = Jsoup.parse(htmlFile, "UTF-8")
                doc.select("script, style, noscript").remove()
                val text = "${chapter.title}\n${doc.body().text()}".trim()
                if (text.isNotBlank()) {
                    out += SharedSearchChunkIndexer.fromPlainText(text, chapterIndex)
                }
            } catch (e: Exception) {
                GlobalSearchLog.w { "chapter error bookId=$bookId ch=$chapterIndex cause=${e.brief()}" }
            }
        }
        GlobalSearchLog.d {
            "epub-parse done bookId=$bookId chunks=${out.size} skippedChapters=$skipped"
        }
        return out
    }

    /** Native Pdfium text pages only. No bitmap render, no OCR model. */
    private suspend fun extractPdfNativePages(uri: Uri): List<SharedSearchChunk> {
        val context = applicationContext
        val pages = mutableListOf<String>()
        GlobalSearchLog.d { "pdf-parse start uri=$uri" }
        PdfiumEngineProvider.withPdfium {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Cannot open $uri")
            pfd.use {
                PdfiumCoreProvider.core.newDocument(it).use { doc ->
                    val count = doc.getPageCount()
                    GlobalSearchLog.d { "pdf-parse open pages=$count uri=$uri" }
                    var blank = 0
                    for (pageIndex in 0 until count) {
                        if (isStopped) break
                        try {
                            doc.openPage(pageIndex)?.use { page ->
                                page.openTextPage().use { textPage ->
                                    val chars = textPage.textPageCountChars()
                                    if (chars > 0) {
                                        pages += textPage.textPageGetText(0, chars).orEmpty()
                                    } else {
                                        blank++
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            GlobalSearchLog.w { "pdf page error p=$pageIndex cause=${e.brief()}" }
                        }
                    }
                    GlobalSearchLog.d { "pdf-parse done pages=$count withText=${pages.size} blank=$blank" }
                }
            }
        }
        return SharedSearchChunkIndexer.fromPdfPageTexts(pages)
    }

    private suspend fun extractPptxSlides(uri: Uri): List<SharedSearchChunk> =
        withContext(Dispatchers.IO) {
            val context = applicationContext
            val tmp = File.createTempFile("gsi_pptx_", ".pptx", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw FileNotFoundException("Cannot open $uri")
                val deck = PptxDocumentParser.parse(tmp)
                GlobalSearchLog.d { "pptx-parse slides=${deck.slides.size} uri=$uri" }
                SharedSearchChunkIndexer.fromSlideTexts(deck.slides.map { it.text })
            } finally {
                tmp.delete()
            }
        }

    companion object {
        internal const val KEY_BOOK_ID = "book_id"
        internal const val KEY_URI = "uri"
        internal const val KEY_TYPE = "type"
        internal const val KEY_NAME = "name"
        internal const val KEY_HASH = "hash"
        private const val TAG = "GlobalSearchIndex"
    }
}

/**
 * Scheduler seam for [GlobalSearchIndexWorker]. Callers pass library books;
 * hashing is `size:mtime` (sha256 would require reading every file on every
 * scan; the worker re-checks freshness via [SharedGlobalSearchCoordinator]).
 */
object GlobalSearchIndexer {
    private const val WORK_PREFIX = "global_search_"
    const val WORK_TAG = "global-search-index"

    fun workName(bookId: String): String = "$WORK_PREFIX$bookId"

    fun contentHashFor(book: BookItem): String =
        "${book.fileSize}:${book.fileContentModifiedTimestamp}"

    fun enqueue(
        context: Context,
        bookId: String,
        uriString: String?,
        type: FileType,
        displayName: String,
        contentHash: String,
    ) {
        if (bookId.isBlank()) {
            GlobalSearchLog.d { "enqueue skipped: blank bookId" }
            return
        }
        if (uriString.isNullOrBlank()) {
            GlobalSearchLog.d { "enqueue skipped: blank uri bookId=$bookId type=$type" }
            return
        }
        if (contentHash.isBlank()) {
            GlobalSearchLog.d { "enqueue skipped: blank hash bookId=$bookId" }
            return
        }
        if (!SharedGlobalSearchPolicy.isContentSearchable(type)) {
            GlobalSearchLog.d { "enqueue skipped: unsupported type=$type bookId=$bookId" }
            return
        }
        val request = OneTimeWorkRequestBuilder<GlobalSearchIndexWorker>()
            .setInputData(
                workDataOf(
                    GlobalSearchIndexWorker.KEY_BOOK_ID to bookId,
                    GlobalSearchIndexWorker.KEY_URI to uriString,
                    GlobalSearchIndexWorker.KEY_TYPE to type.name,
                    GlobalSearchIndexWorker.KEY_NAME to displayName,
                    GlobalSearchIndexWorker.KEY_HASH to contentHash,
                ),
            )
            .addTag(WORK_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(bookId), ExistingWorkPolicy.REPLACE, request)
        GlobalSearchLog.d {
            "enqueued bookId=$bookId type=$type hash=$contentHash " +
                "uri=$uriString work=${workName(bookId)}"
        }
    }

    fun enqueueBook(context: Context, book: BookItem) {
        enqueue(context, book.id, book.path, book.type, book.displayName, contentHashFor(book))
    }

    fun cancel(context: Context, bookId: String) {
        GlobalSearchLog.d { "cancel bookId=$bookId" }
        WorkManager.getInstance(context).cancelUniqueWork(workName(bookId))
        GlobalSearchFileStorage.fromFilesDir(context.filesDir).deleteBook(bookId)
    }
}
