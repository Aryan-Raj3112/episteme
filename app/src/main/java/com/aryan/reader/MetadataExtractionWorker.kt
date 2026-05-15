// MetadataExtractionWorker.kt
package com.aryan.reader

import android.content.Context
import android.provider.OpenableColumns
import android.util.Xml
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

class MetadataExtractionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)

    companion object {
        const val WORK_NAME = "MetadataExtractionWorker"
        const val KEY_SOURCE_FOLDER_URI = "key_source_folder_uri"
        private const val METADATA_DB_BATCH_SIZE = 100
        private const val METADATA_WORKER_BOOK_BATCH_SIZE = 300
        private const val METADATA_PROGRESS_LOG_EVERY = 250
        private val TEXT_METADATA_TYPES = setOf(
            FileType.PDF,
            FileType.EPUB,
            FileType.MOBI,
            FileType.FB2,
            FileType.ODT,
            FileType.FODT,
            FileType.DOCX
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workerStart = ReaderPerfLog.nowNanos()
        val sourceFolderUri = inputData.getString(KEY_SOURCE_FOLDER_URI)
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)

        val hasLegacy = prefs.contains("synced_folder_uri")
        val hasNew = prefs.contains("synced_folders_list_json")

        if (!hasLegacy && !hasNew) {
            ReaderPerfLog.d("MetadataWorker skipped: no linked folders")
            return@withContext Result.success()
        }

        try {
            val filesToProcess = recentFilesRepository.getFolderBooksNeedingTextMetadata(
                sourceFolderUri = sourceFolderUri,
                limit = METADATA_WORKER_BOOK_BATCH_SIZE
            )

            if (filesToProcess.isEmpty()) {
                ReaderPerfLog.d("MetadataWorker skipped: no metadata pending folder=${sourceFolderUri ?: "ALL"}")
                return@withContext Result.success()
            }

            ReaderPerfLog.i(
                "MetadataWorker start mode=metadata books=${filesToProcess.size} " +
                    "batchLimit=$METADATA_WORKER_BOOK_BATCH_SIZE folder=${sourceFolderUri ?: "ALL"}"
            )

            val pendingUpdates = mutableListOf<RecentFileItem>()
            var processed = 0
            var updated = 0
            var coversUpdated = 0
            var failed = 0

            suspend fun flushUpdates() {
                if (pendingUpdates.isEmpty()) return
                val flushStart = ReaderPerfLog.nowNanos()
                recentFilesRepository.updateExtractedMetadata(pendingUpdates)
                ReaderPerfLog.d(
                    "MetadataWorker DB flush rows=${pendingUpdates.size} elapsed=${ReaderPerfLog.elapsedMs(flushStart)}ms"
                )
                pendingUpdates.clear()
            }

            filesToProcess.forEach { item ->
                if (isStopped) return@forEach

                if (item.sourceFolderUri == null) return@forEach

                var needsTextMetadata = item.type in TEXT_METADATA_TYPES && !item.folderTextMetadataParsed
                var needsEmbeddedCover = false

                try {
                    val uri = item.uriString?.toUri() ?: return@forEach
                    val fileSize = item.fileSize.takeIf { it > 0L } ?: queryFileSize(uri)
                    val existingCoverIsAvailable = item.coverImagePath?.let { File(it).isFile } == true
                    needsEmbeddedCover = EmbeddedEbookMetadataExtractor.canExtractEmbeddedCover(item.type) &&
                        !item.folderCoverMetadataParsed &&
                        !existingCoverIsAvailable

                    val metadata = when (item.type) {
                        FileType.EPUB,
                        FileType.MOBI,
                        FileType.FB2 -> {
                            if (needsTextMetadata || needsEmbeddedCover) {
                                EmbeddedEbookMetadataExtractor.extract(
                                    type = item.type,
                                    displayName = item.displayName,
                                    openStream = { appContext.contentResolver.openInputStream(uri) },
                                    extractCover = needsEmbeddedCover
                                ).toTextMetadata()
                            } else {
                                TextMetadata()
                            }
                        }
                        FileType.PDF -> parsePdfTextMetadata(uri)
                        FileType.ODT -> parseZipTextMetadata(uri, "meta.xml")
                        FileType.FODT -> parseFlatXmlTextMetadata(uri)
                        FileType.DOCX -> parseZipTextMetadata(uri, "docProps/core.xml")
                        FileType.PPTX -> parseZipTextMetadata(uri, "docProps/core.xml")
                        else -> TextMetadata()
                    }

                    val title = sanitizeTitle(metadata.title)
                    val author = sanitizeAuthor(metadata.author)
                    val description = metadata.description?.trim()?.takeIf { it.isNotBlank() }
                    val seriesName = metadata.seriesName?.trim()?.takeIf { it.isNotBlank() }
                    val seriesIndex = metadata.seriesIndex?.takeIf { it > 0.0 }
                    val sizeChanged = fileSize > 0L && fileSize != item.fileSize
                    val titleChanged = title != null && title != item.title
                    val authorChanged = author != null && author != item.author
                    val descriptionChanged = description != null && description != item.description
                    val seriesChanged = seriesName != null && seriesName != item.seriesName
                    val seriesIndexChanged = seriesIndex != null && seriesIndex != item.seriesIndex
                    val coverPath = if (needsEmbeddedCover) {
                        metadata.cover?.let { cover ->
                            recentFilesRepository.saveEmbeddedCoverToCache(cover.bytes, uri, cover.extension)
                        }
                    } else {
                        null
                    }
                    val coverChanged = coverPath != null && coverPath != item.coverImagePath
                    val coverMetadataParsed = item.folderCoverMetadataParsed || needsEmbeddedCover
                    val textMetadataParsed = item.folderTextMetadataParsed || needsTextMetadata

                    if (needsTextMetadata || needsEmbeddedCover || sizeChanged || titleChanged || authorChanged || descriptionChanged || seriesChanged || seriesIndexChanged || coverChanged) {
                        pendingUpdates.add(
                            item.copy(
                                coverImagePath = coverPath ?: item.coverImagePath,
                                title = title ?: item.title ?: item.displayName,
                                author = author ?: item.author,
                                description = description ?: item.description,
                                seriesName = seriesName ?: item.seriesName,
                                seriesIndex = seriesIndex ?: item.seriesIndex,
                                fileSize = if (fileSize > 0L) fileSize else item.fileSize,
                                folderTextMetadataParsed = textMetadataParsed,
                                folderCoverMetadataParsed = coverMetadataParsed
                            )
                        )
                        if (sizeChanged || titleChanged || authorChanged || descriptionChanged || seriesChanged || seriesIndexChanged || coverChanged) {
                            updated++
                        }
                        if (coverChanged) coversUpdated++
                        if (pendingUpdates.size >= METADATA_DB_BATCH_SIZE) {
                            flushUpdates()
                        }
                    }

                    processed++
                    if (processed % METADATA_PROGRESS_LOG_EVERY == 0) {
                        ReaderPerfLog.d(
                            "MetadataWorker progress mode=metadata processed=$processed updated=$updated covers=$coversUpdated failed=$failed"
                        )
                    }
                } catch (e: Exception) {
                    failed++
                    Timber.tag("MetadataWorker").e(e, "Failed metadata extraction for ${item.displayName}")
                    if (needsTextMetadata || needsEmbeddedCover) {
                        pendingUpdates.add(
                            item.copy(
                                folderTextMetadataParsed = item.folderTextMetadataParsed || needsTextMetadata,
                                folderCoverMetadataParsed = item.folderCoverMetadataParsed || needsEmbeddedCover
                            )
                        )
                        if (pendingUpdates.size >= METADATA_DB_BATCH_SIZE) {
                            flushUpdates()
                        }
                    }
                }
            }

            flushUpdates()

            val nextBatchEnqueued = !isStopped &&
                filesToProcess.size >= METADATA_WORKER_BOOK_BATCH_SIZE &&
                recentFilesRepository.hasFolderBooksNeedingTextMetadata(sourceFolderUri)
            if (nextBatchEnqueued) {
                enqueueNextBatch(sourceFolderUri)
            }

            ReaderPerfLog.i(
                "MetadataWorker finished mode=metadata processed=$processed updated=$updated covers=$coversUpdated failed=$failed " +
                    "nextBatch=$nextBatchEnqueued elapsed=${ReaderPerfLog.elapsedMs(workerStart)}ms folder=${sourceFolderUri ?: "ALL"}"
            )

            return@withContext Result.success()
        } catch (e: Exception) {
            Timber.tag("MetadataWorker").e(e, "Metadata extraction failed")
            return@withContext Result.failure()
        }
    }

    private fun enqueueNextBatch(sourceFolderUri: String?) {
        val data = androidx.work.Data.Builder().apply {
            if (!sourceFolderUri.isNullOrBlank()) {
                putString(KEY_SOURCE_FOLDER_URI, sourceFolderUri)
            }
        }.build()
        val request = OneTimeWorkRequestBuilder<MetadataExtractionWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        ReaderPerfLog.d("MetadataWorker enqueued next metadata batch folder=${sourceFolderUri ?: "ALL"}")
    }

    private fun queryFileSize(uri: android.net.Uri): Long {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).length() } ?: 0L
            } else {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    } else {
                        0L
                    }
                } ?: 0L
            }
        } catch (e: Exception) {
            Timber.tag("MetadataWorker").e(e, "Failed to query file size for $uri")
            0L
        }
    }

    private fun parseZipTextMetadata(uri: android.net.Uri, targetEntryName: String): TextMetadata {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == targetEntryName) {
                        val xml = zip.readTextEntry()
                        return parseXmlTextMetadata(xml)
                    }
                    zip.closeEntry()
                }
            }
        }
        return TextMetadata()
    }

    private fun parseFlatXmlTextMetadata(uri: android.net.Uri): TextMetadata {
        val xml = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: return TextMetadata()
        return parseXmlTextMetadata(xml)
    }

    private fun parsePdfTextMetadata(uri: android.net.Uri): TextMetadata {
        return try {
            val pdfiumCore = PdfiumCore(appContext)
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val pdfDocument = pdfiumCore.newDocument(pfd)
                try {
                    val meta = pdfiumCore.getDocumentMeta(pdfDocument)
                    TextMetadata(title = meta.title, author = meta.author)
                } finally {
                    pdfiumCore.closeDocument(pdfDocument)
                }
            } ?: TextMetadata()
        } catch (e: Exception) {
            Timber.tag("MetadataWorker").e(e, "Failed to extract PDF text metadata")
            TextMetadata()
        }
    }

    private fun parseXmlTextMetadata(xml: String): TextMetadata {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(xml.reader())

        var title: String? = null
        var author: String? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.substringAfter(':').lowercase()
                when {
                    title == null && name == "title" -> title = parser.nextTextOrNull()
                    author == null && (name == "creator" || name == "initial-creator") -> {
                        author = parser.nextTextOrNull()
                    }
                }
            }
            event = parser.next()
        }

        return TextMetadata(title = title, author = author)
    }

    private fun XmlPullParser.nextTextOrNull(): String? {
        return try {
            nextText()?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun ZipInputStream.readTextEntry(): String {
        return String(readBytes(), Charsets.UTF_8)
    }

    private fun sanitizeTitle(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("content", ignoreCase = true) }
    }

    private fun sanitizeAuthor(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    }

    private fun EmbeddedEbookMetadata.toTextMetadata(): TextMetadata {
        return TextMetadata(
            title = title,
            author = author,
            description = description,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            cover = cover
        )
    }

    private data class TextMetadata(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val seriesName: String? = null,
        val seriesIndex: Double? = null,
        val cover: EmbeddedEbookCover? = null
    )
}
