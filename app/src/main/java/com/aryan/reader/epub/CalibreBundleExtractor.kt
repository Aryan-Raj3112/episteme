// CalibreBundleExtractor.kt
package com.aryan.reader.epub

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.aryan.reader.BookImporter
import com.aryan.reader.FileType
import com.aryan.reader.data.RecentFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class CalibreBundleResult(
    val internalBookUri: Uri,
    val type: FileType,
    val title: String?,
    val author: String?,
    val description: String?,
    val seriesName: String?,
    val seriesIndex: Double?,
    val coverCachePath: String?
)

object CalibreBundleExtractor {
    suspend fun processZip(
        context: Context,
        zipUri: Uri,
        bookId: String,
        bookImporter: BookImporter,
        recentFilesRepository: RecentFilesRepository
    ): CalibreBundleResult? = withContext(Dispatchers.IO) {
        var tempBookFile: File? = null
        var extractedType: FileType? = null
        var ext = ""
        var opfData: String? = null
        var coverBytes: ByteArray? = null

        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                Timber.d("CalibreExtractor: Started reading zip entries from $zipUri")
                while (entry != null) {
                    val name = entry.name.lowercase()
                    Timber.d("CalibreExtractor: Found zip entry: $name")
                    if (!entry.isDirectory) {
                        if (name.endsWith(".opf")) {
                            opfData = String(zis.readBytes(), Charsets.UTF_8)
                            Timber.d("CalibreExtractor: Extracted OPF data, length=${opfData?.length}")
                        } else if (name == "cover.jpg" || name == "cover.jpeg" || name.endsWith(".jpg")) {
                            // Prefer exact 'cover.jpg' but grab the first image as fallback
                            if (coverBytes == null || name.startsWith("cover")) {
                                coverBytes = zis.readBytes()
                                Timber.d("CalibreExtractor: Extracted cover image from $name")
                            }
                        } else {
                            val type = when {
                                name.endsWith(".epub") -> FileType.EPUB
                                name.endsWith(".mobi") || name.endsWith(".azw3") -> FileType.MOBI
                                name.endsWith(".pdf") -> FileType.PDF
                                name.endsWith(".fb2") -> FileType.FB2
                                else -> null
                            }
                            if (type != null && tempBookFile == null) {
                                extractedType = type
                                ext = File(name).extension
                                tempBookFile = File(context.cacheDir, "temp_bundle_${bookId}.$ext")
                                FileOutputStream(tempBookFile!!).use { fos ->
                                    zis.copyTo(fos)
                                }
                                Timber.d("CalibreExtractor: Extracted book file $name to temp file")
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            Timber.d("CalibreExtractor: Finished parsing zip. tempBookFile exists=${tempBookFile != null}, opfData exists=${opfData != null}, extractedType=$extractedType")

            if (tempBookFile != null && opfData != null && extractedType != null) {
                val finalBookFile = bookImporter.createBookFile("$bookId.$ext")
                tempBookFile!!.renameTo(finalBookFile)

                var coverPath: String? = null
                if (coverBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes!!.size)
                    if (bitmap != null) {
                        coverPath = recentFilesRepository.saveCoverToCache(bitmap, zipUri)
                    }
                }

                var title: String? = null
                var author: String? = null
                var description: String? = null
                var seriesName: String? = null
                var seriesIndex: Double? = null

                try {
                    val factory = DocumentBuilderFactory.newInstance()
                    val builder = factory.newDocumentBuilder()
                    val document = builder.parse(ByteArrayInputStream(opfData!!.toByteArray(Charsets.UTF_8)))
                    val metadataNodes = document.getElementsByTagName("metadata")
                    Timber.d("CalibreExtractor: Parsed OPF XML. metadataNodes count: ${metadataNodes.length}")

                    if (metadataNodes.length > 0) {
                        val metadata = metadataNodes.item(0) as Element

                        val titleNodes = metadata.getElementsByTagName("dc:title")
                        Timber.d("CalibreExtractor: Found ${titleNodes.length} dc:title nodes")
                        if (titleNodes.length > 0) title = titleNodes.item(0).textContent

                        val authorNodes = metadata.getElementsByTagName("dc:creator")
                        Timber.d("CalibreExtractor: Found ${authorNodes.length} dc:creator nodes")
                        if (authorNodes.length > 0) author = authorNodes.item(0).textContent

                        val descNodes = metadata.getElementsByTagName("dc:description")
                        Timber.d("CalibreExtractor: Found ${descNodes.length} dc:description nodes")
                        if (descNodes.length > 0) description = descNodes.item(0).textContent

                        val metaNodes = metadata.getElementsByTagName("meta")
                        Timber.d("CalibreExtractor: Found ${metaNodes.length} meta nodes")

                        for (i in 0 until metaNodes.length) {
                            val meta = metaNodes.item(i) as Element
                            val nameAttr = meta.getAttribute("name")
                            val contentAttr = meta.getAttribute("content")
                            if (nameAttr == "calibre:series") seriesName = contentAttr
                            if (nameAttr == "calibre:series_index") seriesIndex = contentAttr.toDoubleOrNull()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse metadata.opf")
                }

                Timber.d("CalibreExtractor: Final extracted info - title=$title, author=$author, series=$seriesName, index=$seriesIndex")

                return@withContext CalibreBundleResult(
                    internalBookUri = finalBookFile.toUri(),
                    type = extractedType!!,
                    title = title,
                    author = author,
                    description = description,
                    seriesName = seriesName,
                    seriesIndex = seriesIndex,
                    coverCachePath = coverPath
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to process zip bundle")
        } finally {
            tempBookFile?.delete() // Cleanup if parsing failed midway
        }
        return@withContext null
    }
}