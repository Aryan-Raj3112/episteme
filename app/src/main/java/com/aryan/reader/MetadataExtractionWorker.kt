// MetadataExtractionWorker.kt
package com.aryan.reader

import android.content.Context
import android.provider.OpenableColumns
import android.util.Xml
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        private const val METADATA_PROGRESS_LOG_EVERY = 250
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
            val filesToProcess = recentFilesRepository.getFolderBooksNeedingTextMetadata(sourceFolderUri)

            if (filesToProcess.isEmpty()) {
                ReaderPerfLog.d("MetadataWorker skipped: no text metadata pending folder=${sourceFolderUri ?: "ALL"}")
                return@withContext Result.success()
            }

            ReaderPerfLog.i(
                "MetadataWorker start mode=text-only books=${filesToProcess.size} folder=${sourceFolderUri ?: "ALL"}"
            )

            val pendingUpdates = mutableListOf<RecentFileItem>()
            var processed = 0
            var updated = 0
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

                try {
                    val uri = item.uriString?.toUri() ?: return@forEach
                    val fileSize = item.fileSize.takeIf { it > 0L } ?: queryFileSize(uri)
                    val metadata = when (item.type) {
                        FileType.EPUB -> parseEpubTextMetadata(uri)
                        FileType.PDF -> parsePdfTextMetadata(uri)
                        FileType.ODT -> parseZipTextMetadata(uri, "meta.xml")
                        FileType.FODT -> parseFlatXmlTextMetadata(uri)
                        FileType.DOCX -> parseZipTextMetadata(uri, "docProps/core.xml")
                        else -> TextMetadata()
                    }

                    val title = sanitizeTitle(metadata.title)
                    val author = sanitizeAuthor(metadata.author)
                    val sizeChanged = fileSize > 0L && fileSize != item.fileSize
                    val titleChanged = title != null && title != item.title
                    val authorChanged = author != null && author != item.author

                    if (!item.folderTextMetadataParsed || sizeChanged || titleChanged || authorChanged) {
                        pendingUpdates.add(
                            item.copy(
                                title = title ?: item.title ?: item.displayName,
                                author = author ?: item.author,
                                fileSize = if (fileSize > 0L) fileSize else item.fileSize,
                                folderTextMetadataParsed = true
                            )
                        )
                        if (sizeChanged || titleChanged || authorChanged) {
                            updated++
                        }
                        if (pendingUpdates.size >= METADATA_DB_BATCH_SIZE) {
                            flushUpdates()
                        }
                    }

                    processed++
                    if (processed % METADATA_PROGRESS_LOG_EVERY == 0) {
                        ReaderPerfLog.d(
                            "MetadataWorker progress mode=text-only processed=$processed updated=$updated failed=$failed"
                        )
                    }
                } catch (e: Exception) {
                    failed++
                    Timber.tag("MetadataWorker").e(e, "Failed text metadata extraction for ${item.displayName}")
                }
            }

            flushUpdates()

            ReaderPerfLog.i(
                "MetadataWorker finished mode=text-only processed=$processed updated=$updated failed=$failed " +
                    "elapsed=${ReaderPerfLog.elapsedMs(workerStart)}ms folder=${sourceFolderUri ?: "ALL"}"
            )

            return@withContext Result.success()
        } catch (e: Exception) {
            Timber.tag("MetadataWorker").e(e, "Text metadata extraction failed")
            return@withContext Result.failure()
        }
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

    private fun parseEpubTextMetadata(uri: android.net.Uri): TextMetadata {
        val opfEntries = linkedMapOf<String, String>()
        var containerXml: String? = null

        appContext.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    when {
                        name == "META-INF/container.xml" -> containerXml = zip.readTextEntry()
                        name.endsWith(".opf", ignoreCase = true) -> opfEntries[name] = zip.readTextEntry()
                    }
                    zip.closeEntry()
                }
            }
        }

        val opfPath = containerXml?.let { parseEpubRootfilePath(it) }
        val opfXml = opfPath?.let { opfEntries[it] } ?: opfEntries.values.firstOrNull()
        return opfXml?.let { parseXmlTextMetadata(it) } ?: TextMetadata()
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

    private fun parseEpubRootfilePath(containerXml: String): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(containerXml.reader())

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                return parser.getAttributeValue(null, "full-path")?.takeIf { it.isNotBlank() }
            }
            event = parser.next()
        }
        return null
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

    private data class TextMetadata(
        val title: String? = null,
        val author: String? = null
    )
}
