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
import org.jsoup.nodes.Document
import java.io.File
import java.io.InputStream
import java.util.UUID

class MobiParser(private val context: Context) {

    /**
     * One NCX (or guide) TOC entry. `internal` rather than private so the split it feeds can be
     * unit tested without the JNI parser; the JNI constructor lookup is unaffected.
     */
    internal data class ParsedMobiTocEntry(val title: String, val filePosition: Int) : Comparable<ParsedMobiTocEntry> {
        override fun compareTo(other: ParsedMobiTocEntry): Int = this.filePosition.compareTo(other.filePosition)
    }

    // Updated data class to receive the full raw HTML
    private data class ParsedMobiData(
        val title: String?,
        val author: String?,
        val publisher: String?,
        // Mutable so the importer can drop the whole-book HTML as soon as it has encoded it:
        // the String is a full copy of the book and stayed reachable through the entire chapter
        // loop, where a chapter's serialization already lost a small allocation on a 256 MB heap
        // (docs/crashlytics-triage.md#162).
        var rawHtmlContent: String?, // This is the full HTML of the book
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
        private val nativeLoadError: Throwable? = try {
            System.loadLibrary("mobi")
            System.loadLibrary("native-lib")
            null
        } catch (t: Throwable) {
            Timber.e(t, "MOBI native parser is unavailable on this device.")
            t
        }

        val isNativeParserAvailable: Boolean
            get() = nativeLoadError == null

        fun nativeParserUnavailableMessage(): String =
            nativeLoadError?.message ?: "MOBI native parser is unavailable on this device."
    }

    suspend fun createMobiBook(
        inputStream: InputStream,
        bookId: String,
        originalBookNameHint: String,
        parseContent: Boolean = true,
        extractionDirOverride: File? = null
    ): EpubBook? = withContext(Dispatchers.IO) {
        if (!isNativeParserAvailable) {
            Timber.e("Skipping MOBI parsing: ${nativeParserUnavailableMessage()}")
            return@withContext null
        }

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

        val extractionDir = extractionDirOverride?.let(ImportedFileCache::prepareDirectory)
            ?: if (parseContent) {
                ImportedFileCache.prepareActiveBookDir(context, bookId)
            } else {
                ImportedFileCache.createTemporaryBookDir(context, bookId, "metadata")
            }

        val sequentialImageMap = parsedData.resources
            .filter { it.mediaType.startsWith("image/") }
            .sortedBy { it.uid }
            .mapIndexed { index, resource -> (index + 1) to resource.path }
            .toMap()

        if (parseContent) {
            parsedData.resources.forEachIndexed { index, resource ->
                try {
                    val file = File(extractionDir, resource.path)
                    file.parentFile?.mkdirs()
                    file.writeBytes(resource.data)
                    // The payload is on disk now, so release this copy before the chapter work below.
                    // Every embedded image and font would otherwise stay resident byte-for-byte,
                    // leaving a low-heap device with nothing to allocate the chapter split from
                    // (docs/crashlytics-triage.md#91). Only on success: a failed write still needs
                    // its bytes. CSS and the cover are read again after this loop, so they keep
                    // theirs.
                    if (resource.mediaType != "text/css" &&
                        resource.uid != parsedData.coverImageResourceUid
                    ) {
                        parsedData.resources[index] = resource.copy(data = ByteArray(0))
                    }
                    Timber.d("Wrote resource to disk -> Path: ${file.absolutePath}")
                } catch (e: Exception) {
                    Timber.e(e, "Parser: FAILED to write resource to disk: ${resource.path}")
                }
            }
        }

        val cssFlowMap = parsedData.resources
            .filter { it.mediaType == "text/css" && it.path.startsWith("flow_") }
            .associate {
                val index = it.path.removePrefix("flow_").removeSuffix(".css").toIntOrNull() ?: -1
                "kindle:flow:${String.format("%04d", index)}?mime=text/css" to it.path
            }

        // --- CHAPTER SPLITTING LOGIC ---
        // Only the chapter list below consumes the whole-book HTML, so a metadata-only import
        // skips all of it. While the chapters are being built, though, the book must not stay
        // resident as a String: that is a second full copy of the text, and every chapter used to
        // be decoded up front as a third one. Serializing a chapter with all of that still alive
        // is what lost a 40-byte allocation on a 256 MB heap (docs/crashlytics-triage.md#162).
        // Both branches therefore drop the book as soon as they have the bytes (or parts) they
        // need, and decode one chapter at a time.
        val epubChapters = if (parseContent) {
            val sortedToc = parsedData.toc?.sorted()
            if (!sortedToc.isNullOrEmpty()) {
                Timber.d("Splitting content using TOC (${sortedToc.size} entries).")
                val rawHtmlBytes = parsedData.takeRawHtmlBytes()
                splitMobiChapterSlices(sortedToc, rawHtmlBytes.size).mapIndexedNotNull { index, slice ->
                    // Decoded here rather than inside buildMobiChapter's try: a TOC position past
                    // the end of the flow must fail the import, as the old split did, instead of
                    // silently dropping chapters.
                    buildMobiChapter(
                        chapterIndex = index,
                        chapterHtml = slice.decode(rawHtmlBytes),
                        title = slice.title,
                        extractionDir = extractionDir,
                        cssFlowMap = cssFlowMap,
                        sequentialImageMap = sequentialImageMap
                    )
                }
            } else {
                Timber.d("No TOC found. Falling back to splitting by <mbp:pagebreak/>.")
                buildMobiChaptersWithoutToc(
                    rawHtml = parsedData.takeRawHtml(),
                    extractionDir = extractionDir,
                    cssFlowMap = cssFlowMap,
                    sequentialImageMap = sequentialImageMap
                )
            }
        } else {
            emptyList()
        }

        Timber.d("Successfully built ${epubChapters.size} chapters.")

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

    /**
     * Returns the concatenated flow and clears it from [ParsedMobiData] first, so the parsed-data
     * object stops retaining the book for the rest of the import. Callers therefore keep only the
     * representation they still need: the encoded bytes for the TOC split, or this String for the
     * pagebreak fallback (docs/crashlytics-triage.md#162).
     */
    private fun ParsedMobiData.takeRawHtml(): String {
        val html = rawHtmlContent ?: error("MOBI parser returned no HTML content")
        rawHtmlContent = null
        return html
    }

    /** UTF-8 bytes of the concatenated flow, with the source String released by [takeRawHtml]. */
    private fun ParsedMobiData.takeRawHtmlBytes(): ByteArray = takeRawHtml().toByteArray(Charsets.UTF_8)
}

/**
 * Parses one chapter and rewrites its asset references to the paths used after extraction.
 *
 * Returns the DOM, not its HTML, so the caller can take the chapter's text from the same tree it
 * serializes for the chapter file. The previous shape returned only [Document.outerHtml] and the
 * caller immediately re-parsed that string to get the text, which meant two full jsoup documents
 * per chapter were alive at once - a multi-MB peak per chapter on a big MOBI for no benefit
 * (docs/crashlytics-triage.md#116).
 */
internal fun rewriteMobiChapterDocument(
    html: String,
    cssFlowMap: Map<String, String>,
    sequentialImageMap: Map<Int, String>,
): Document {
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

    return doc
}

/**
 * Parses one chapter, writes its rewritten HTML next to the other extracted files, and returns
 * the in-memory chapter the reader uses.
 *
 * One DOM per chapter: it is serialized once for the chapter file, and the plain text comes from
 * the same tree (docs/crashlytics-triage.md#116).
 */
private fun buildMobiChapter(
    chapterIndex: Int,
    chapterHtml: String,
    title: String,
    extractionDir: File,
    cssFlowMap: Map<String, String>,
    sequentialImageMap: Map<Int, String>
): EpubChapter? {
    return try {
        val doc = rewriteMobiChapterDocument(chapterHtml, cssFlowMap, sequentialImageMap)
        val rewrittenHtml = doc.outerHtml()
        val chapterFileName = "chapter_$chapterIndex.html"
        File(extractionDir, chapterFileName).writeText(rewrittenHtml)
        EpubChapter(
            chapterId = "mobi_chapter_$chapterIndex",
            title = title,
            absPath = chapterFileName,
            htmlFilePath = chapterFileName,
            htmlContent = rewrittenHtml,
            plainTextContent = doc.text()
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to process split chapter $chapterIndex")
        null
    }
}

private val mobiPagebreakRegex = Regex("(?i)<mbp:pagebreak\\s*/>")

/**
 * Splits a TOC-less flow on `<mbp:pagebreak/>` and builds the chapters one at a time.
 *
 * The old `rawHtml.split(regex).mapIndexedNotNull` materialized every chapter before the first
 * DOM was built; only one decoded chapter is alive here (docs/crashlytics-triage.md#162). Titles
 * still use the position in the split (blank parts included) while file names use the kept-chapter
 * position — the numbering the old two-pass shape produced.
 */
internal fun buildMobiChaptersWithoutToc(
    rawHtml: String,
    extractionDir: File,
    cssFlowMap: Map<String, String>,
    sequentialImageMap: Map<Int, String>
): List<EpubChapter> {
    val chapters = mutableListOf<EpubChapter>()
    var partIndex = 0
    var chapterIndex = 0

    fun addPart(chapterHtml: String) {
        if (chapterHtml.isNotBlank()) {
            buildMobiChapter(
                chapterIndex = chapterIndex,
                chapterHtml = chapterHtml,
                title = "Chapter ${partIndex + 1}",
                extractionDir = extractionDir,
                cssFlowMap = cssFlowMap,
                sequentialImageMap = sequentialImageMap
            )?.let(chapters::add)
            chapterIndex++
        }
        partIndex++
    }

    // `split` always yields the text before the first marker and after the last one, even when
    // the flow has no marker at all; the trailing part is added after the loop.
    var partStart = 0
    for (match in mobiPagebreakRegex.findAll(rawHtml)) {
        addPart(rawHtml.substring(partStart, match.range.first))
        partStart = match.range.last + 1
    }
    addPart(rawHtml.substring(partStart))
    return chapters
}

/**
 * One chapter of the concatenated flow, addressed by its byte range.
 *
 * Byte offsets are what the NCX carries, so the flow has to be encoded once; keeping the slices
 * separate from the encoded array lets the caller release the whole-book String and decode one
 * chapter at a time (docs/crashlytics-triage.md#91, #162).
 */
internal data class MobiChapterSlice(val title: String, val startByte: Int, val length: Int)

/** Maps the TOC's byte positions to the chapter slices they delimit. */
internal fun splitMobiChapterSlices(
    sortedToc: List<MobiParser.ParsedMobiTocEntry>,
    totalByteCount: Int
): List<MobiChapterSlice> = sortedToc.mapIndexedNotNull { index, entry ->
    val startByte = entry.filePosition
    val endByte = if (index + 1 < sortedToc.size) sortedToc[index + 1].filePosition else totalByteCount
    if (startByte >= endByte) return@mapIndexedNotNull null
    MobiChapterSlice(entry.title, startByte, endByte - startByte)
}

/** Decodes just this chapter out of the flow's UTF-8 bytes. */
internal fun MobiChapterSlice.decode(bytes: ByteArray): String =
    String(bytes, startByte, length, Charsets.UTF_8)
