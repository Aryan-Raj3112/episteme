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
package com.aryan.reader.epub

import android.content.Context
import android.graphics.BitmapFactory
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.InputStream
import java.util.UUID

class MobiParser(private val context: Context) {

    private data class ParsedMobiTocEntry(val title: String, val filePosition: Int) : Comparable<ParsedMobiTocEntry> {
        override fun compareTo(other: ParsedMobiTocEntry): Int = this.filePosition.compareTo(other.filePosition)
    }

    // Updated data class to receive the full raw HTML
    private data class ParsedMobiData(
        val title: String?,
        val author: String?,
        val publisher: String?,
        val rawHtmlContent: String?, // This is the full HTML of the book
        val resources: Array<ParsedMobiResource>,
        val toc: Array<ParsedMobiTocEntry>?,
        val coverImageResourceUid: Int // Use -1 if not found
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ParsedMobiData

            if (title != other.title) return false
            if (author != other.author) return false
            if (publisher != other.publisher) return false
            if (rawHtmlContent != other.rawHtmlContent) return false
            if (!resources.contentEquals(other.resources)) return false
            if (toc != null) {
                if (other.toc == null) return false
                if (!toc.contentEquals(other.toc)) return false
            } else if (other.toc != null) return false
            if (coverImageResourceUid != other.coverImageResourceUid) return false

            return true
        }

        override fun hashCode(): Int {
            var result = title?.hashCode() ?: 0
            result = 31 * result + (author?.hashCode() ?: 0)
            result = 31 * result + (publisher?.hashCode() ?: 0)
            result = 31 * result + (rawHtmlContent?.hashCode() ?: 0)
            result = 31 * result + resources.contentHashCode()
            result = 31 * result + (toc?.contentHashCode() ?: 0)
            result = 31 * result + coverImageResourceUid
            return result
        }
    }

    private data class ParsedMobiResource(
        val uid: Int,
        val path: String,
        val data: ByteArray,
        val mediaType: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ParsedMobiResource

            if (uid != other.uid) return false
            if (path != other.path) return false
            if (!data.contentEquals(other.data)) return false
            if (mediaType != other.mediaType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = uid
            result = 31 * result + path.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + mediaType.hashCode()
            return result
        }
    }

    private external fun parseMobiFile(filePath: String): ParsedMobiData?

    companion object {
        const val EXTRACTED_EPUB_DIR_NAME = "extracted_epubs"

        init {
            System.loadLibrary("mobi")
            System.loadLibrary("native-lib")
        }
    }

    private fun getBookExtractionDir(bookIdentifier: String): File {
        val parentDir = File(context.cacheDir, EXTRACTED_EPUB_DIR_NAME)
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        return File(parentDir, bookIdentifier)
    }

    suspend fun createMobiBook(inputStream: InputStream, originalBookNameHint: String): EpubBook? = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("temp_mobi_", ".mobi", context.cacheDir)
        try {
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            Timber.d("MOBI stream saved to temporary file: ${tempFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write InputStream to temporary file.")
            tempFile.delete()
            return@withContext null
        }

        val parsedData = try {
            parseMobiFile(tempFile.absolutePath)
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "JNI call failed. Is the native library loaded correctly?")
            null
        } finally {
            tempFile.delete()
        }

        if (parsedData?.rawHtmlContent == null) {
            Timber.e("The native parser returned null or empty HTML content. Check JNI logs.")
            return@withContext null
        }

        Timber.d("Received ${parsedData.resources.size} resources from JNI.")

        val bookTitle = parsedData.title ?: originalBookNameHint
        val bookAuthor = parsedData.author ?: "Unknown Author"

        val bookIdentifier = bookTitle.asFileName() + "_" + UUID.randomUUID().toString().substring(0, 8)
        val extractionDir = getBookExtractionDir(bookIdentifier)
        extractionDir.mkdirs()

        // This map is the key. It maps the 1-based sequential index of an image to its new path.
        val sequentialImageMap = parsedData.resources
            .filter { it.mediaType.startsWith("image/") }
            .sortedBy { it.uid } // Sort by UID to ensure order is correct
            .mapIndexed { index, resource -> (index + 1) to resource.path }
            .toMap()

        parsedData.resources.forEach { resource ->
            try {
                val file = File(extractionDir, resource.path)
                file.parentFile?.mkdirs()
                file.writeBytes(resource.data)
                Timber.d("Wrote resource to disk -> Path: ${file.absolutePath}, Type: ${resource.mediaType}, Size: ${resource.data.size}")
            } catch (e: Exception) {
                Timber.e(e, "Parser: FAILED to write resource to disk: ${resource.path}")
            }
        }

        val cssFlowMap = parsedData.resources
            .filter { it.mediaType == "text/css" && it.path.startsWith("flow_") }
            .associate {
                val index = it.path.removePrefix("flow_").removeSuffix(".css").toIntOrNull() ?: -1
                "kindle:flow:${String.format("%04d", index)}?mime=text/css" to it.path
            }

        val processChapterHtml: (String) -> String = { html ->
            val doc = Jsoup.parse(html)

            doc.select("link[href]").forEach { link ->
                val originalHref = link.attr("href")
                cssFlowMap[originalHref]?.let { newPath ->
                    link.attr("href", newPath)
                    Timber.d("Rewrote CSS link from '$originalHref' to '$newPath'")
                } ?: Timber.w("Could not find mapping for CSS link: $originalHref")
            }

            doc.select("img").forEach { img ->
                val src = img.attr("src")
                if (src.startsWith("kindle:embed:")) {
                    val embedIndexString = src.substringAfter("embed:").substringBefore("?")
                    val embedIndex = embedIndexString.toIntOrNull()
                    if (embedIndex != null) {
                        // **THE FIX**: Use the sequential map, not a UID map
                        sequentialImageMap[embedIndex]?.let { newPath ->
                            img.attr("src", newPath)
                            Timber.d("Rewrote image src from '$src' to '$newPath' using sequential map")
                        } ?: Timber.w("No resource found for sequential image index: $embedIndex")
                    }
                } else if (img.hasAttr("recindex")) {
                    val recIndex = img.attr("recindex").toIntOrNull()
                    if (recIndex != null) {
                        sequentialImageMap[recIndex]?.let { newPath ->
                            img.attr("src", newPath)
                            img.removeAttr("recindex")
                        } ?: Timber.w("Kotlin: No matching image found for recindex: $recIndex")
                    }
                }
            }
            doc.outerHtml()
        }

        // --- CHAPTER SPLITTING LOGIC ---
        val rawHtmlBytes = parsedData.rawHtmlContent.toByteArray(Charsets.UTF_8)
        val chapterHtmlParts = mutableListOf<Pair<String, String>>()

        val sortedToc = parsedData.toc?.sorted()
        if (!sortedToc.isNullOrEmpty()) {
            Timber.d("Splitting content using TOC (${sortedToc.size} entries).")
            for (i in sortedToc.indices) {
                val tocEntry = sortedToc[i]
                val startByte = tocEntry.filePosition
                val endByte = if (i + 1 < sortedToc.size) sortedToc[i + 1].filePosition else rawHtmlBytes.size
                if (startByte >= endByte) continue
                val chapterBytes = rawHtmlBytes.sliceArray(startByte until endByte)
                val chapterHtml = String(chapterBytes, Charsets.UTF_8)
                chapterHtmlParts.add(Pair(chapterHtml, tocEntry.title))
            }
        } else {
            Timber.d("No TOC found. Falling back to splitting by <mbp:pagebreak/>.")
            val parts = parsedData.rawHtmlContent.split("(?i)<mbp:pagebreak\\s*/>".toRegex())
            parts.forEachIndexed { index, html ->
                if (html.isNotBlank()) {
                    chapterHtmlParts.add(Pair(html, "Chapter ${index + 1}"))
                }
            }
        }

        Timber.d("Successfully split content into ${chapterHtmlParts.size} chapters.")

        val epubChapters = chapterHtmlParts.mapIndexedNotNull { index, (chapterHtml, title) ->
            try {
                val rewrittenHtml = processChapterHtml(chapterHtml)
                val doc = Jsoup.parse(rewrittenHtml)
                val chapterFileName = "chapter_$index.html"
                val chapterFile = File(extractionDir, chapterFileName)
                chapterFile.writeText(rewrittenHtml)
                EpubChapter(
                    chapterId = "mobi_chapter_$index",
                    title = title,
                    absPath = chapterFileName,
                    htmlFilePath = chapterFileName,
                    htmlContent = rewrittenHtml,
                    plainTextContent = doc.text()
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to process split chapter $index")
                null
            }
        }

        val images = parsedData.resources
            .filter { it.mediaType.startsWith("image/") }
            .map { EpubImage(absPath = it.path) }

        val cssContent = parsedData.resources
            .filter { it.mediaType == "text/css" }
            .associate { it.path to String(it.data, Charsets.UTF_8) }

        Timber.d("Extracted ${cssContent.size} CSS files.")

        val coverImageBytes = if (parsedData.coverImageResourceUid != -1) {
            parsedData.resources.find { it.uid == parsedData.coverImageResourceUid }?.data
        } else {
            null
        }
        val coverImage = coverImageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (coverImage == null) {
            Timber.d("Kotlin: Cover image data not found for UID: ${parsedData.coverImageResourceUid}")
        }

        val finalBook = EpubBook(
            fileName = bookTitle.asFileName(),
            title = bookTitle,
            author = bookAuthor,
            language = "en",
            coverImage = coverImage,
            chapters = epubChapters,
            chaptersForPagination = epubChapters,
            images = images,
            pageList = emptyList(),
            extractionBasePath = extractionDir.absolutePath,
            css = cssContent
        )
        Timber.d("Final EpubBook created. CSS map size: ${finalBook.css.size}, Image count: ${finalBook.images.size}")
        return@withContext finalBook
    }
}