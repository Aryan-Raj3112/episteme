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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.aryan.reader.shared.reader.MobileEpubNcxNavigationNode
import com.aryan.reader.shared.reader.flattenMobileEpubNcxNavigation
import com.aryan.reader.shared.reader.MobileEpubNcxChapterMetadata as NcxMetadata
import com.aryan.reader.shared.reader.mobileEpubNcxChapterMetadata
import com.aryan.reader.shared.reader.MobileEpubMetaElement
import com.aryan.reader.shared.reader.resolveMobileEpubMetadata
import com.aryan.reader.shared.reader.MobileEpubManifestItem as EpubManifestItem
import com.aryan.reader.shared.reader.mobileEpubSpineItemIds
import com.aryan.reader.shared.reader.resolveMobileEpubNcxManifestId
import com.aryan.reader.shared.reader.resolveMobileEpubOpfPath
import com.aryan.reader.shared.reader.MobileEpubSpineResourceKind
import com.aryan.reader.shared.reader.mobileEpubSpineResourceKind
import com.aryan.reader.shared.reader.resolveMobileEpubChapterNavigation
import com.aryan.reader.shared.reader.MobileEpubNcxPageNode
import com.aryan.reader.shared.reader.mobileEpubPageTargets
import com.aryan.reader.shared.reader.decodeMobileEpubUrl
import com.aryan.reader.shared.reader.mobileEpubImages
import com.aryan.reader.shared.reader.mobileEpubCoverCandidates
import com.aryan.reader.shared.reader.mobileEpubCoverBitmapSampleSize
import com.aryan.reader.shared.reader.mobileEpubCssPaths
import com.aryan.reader.shared.reader.resolveMobileEpubReference
import com.aryan.reader.shared.reader.resolveMobileEpubSpineChapterTitle
import com.aryan.reader.shared.reader.mobileEpubLogicalSectionRanges
import com.aryan.reader.shared.reader.MobileEpubExtractionAction
import com.aryan.reader.shared.reader.mobileEpubExtractionAction
import com.aryan.reader.shared.reader.MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES
import com.aryan.reader.shared.reader.MOBILE_EPUB_MAX_CACHED_BOOK_METADATA_BYTES
import com.aryan.reader.shared.reader.MobileEpubExtractionCacheManifest
import com.aryan.reader.shared.reader.MOBILE_EPUB_EXTRACTION_CACHE_VERSION
import com.aryan.reader.shared.reader.matchesMobileEpubExtractionCache
import com.aryan.reader.shared.reader.toMobileEpubExtractionCacheChapter
import com.aryan.reader.shared.reader.MobileEpubExtractionDirectoryMode
import com.aryan.reader.shared.reader.mobileEpubExtractionLifecycle

class EpubParser(private val context: Context) {
    private val jsonSerializer = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    data class EpubDocument(
        val metadata: Node, val manifest: Node, val spine: Node, val opfFilePath: String
    )

    data class TempEpubChapter(
        val url: String,
        val title: String?,
        val htmlFilePath: String,
        val chapterIndex: Int,
        val plainTextContent: String,
        val htmlContent: String,
        val depth: Int,
        val isInToc: Boolean
    )

    // EpubFile can still represent in-memory file data during initial parsing before extraction
    data class EpubFile(val absPath: String, val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EpubFile
            if (absPath != other.absPath) return false
            return data.contentEquals(other.data)
        }
        override fun hashCode(): Int {
            var result = absPath.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
    companion object {
        const val TAG = "EpubParser"
        private const val BOOK_METADATA_FILE = "book_metadata.json"
        private const val CACHE_MANIFEST_FILE = "epub_cache_manifest.json"
        // v3 also materializes fragment-based navigation sections as reader
        // chapters, rather than treating a large shared spine file as one page.
    }

    internal val String.decodedURL: String
        get() = decodeMobileEpubUrl(this)

    private fun parsePageList(pageListElement: Element?, ncxFileParentDir: File): List<EpubPageTarget> {
        if (pageListElement == null) {
            Timber.d("No <pageList> element found in NCX.")
            return emptyList()
        }
        val nodes = pageListElement.selectChildTag("pageTarget").map { ptElement ->
            val contentSrcRaw = ptElement.selectFirstChildTag("content")?.getAttributeValue("src")?.decodedURL
            val resolvedContentSrc = contentSrcRaw?.let {
                resolveMobileEpubReference("${ncxFileParentDir.path}/_", contentSrcRaw)
            }
            if (resolvedContentSrc == null) {
                Timber.w("PageTarget found with no content src: ${ptElement.getAttributeValue("id")}")
            }
            MobileEpubNcxPageNode(
                id = ptElement.getAttributeValue("id"),
                value = ptElement.getAttributeValue("value"),
                label = ptElement.selectFirstChildTag("navLabel")?.selectFirstChildTag("text")?.textContent,
                resolvedContentSrc = resolvedContentSrc
            )
        }.toList()
        val pageTargets = mobileEpubPageTargets(nodes)
        Timber.d("Parsed ${pageTargets.size} page targets from NCX.")
        return pageTargets
    }

    private fun parseEpubCss(
        manifestItems: Map<String, EpubManifestItem>,
        filesContentMap: Map<String, EpubFile>,
        extractionRoot: File
    ): Map<String, String> {
        val cssPaths = mobileEpubCssPaths(manifestItems.values.toList(), filesContentMap.keys.toList())
        val allCss = cssPaths.mapNotNull { path ->
            val bytes = filesContentMap[path]?.data?.takeIf { it.isNotEmpty() }
                ?: File(extractionRoot, path).takeIf { it.exists() }?.readBytes()
            bytes?.let { path to String(it, Charsets.UTF_8) }
        }.toMap()

        Timber.d("Parsed ${allCss.size} CSS files: ${allCss.keys.joinToString()}")
        return allCss
    }

    suspend fun createEpubBook(
        inputStream: InputStream,
        bookId: String,
        shouldUseToc: Boolean = true,
        originalBookNameHint: String = "streamed_book",
        parseContent: Boolean = true,
        extractionDirOverride: File? = null,
        sourceFingerprint: String? = null
    ): EpubBook {
        return withContext(Dispatchers.IO) {
            Timber.d("Parsing EPUB input stream for bookId: $bookId")

            val extractionLifecycle = mobileEpubExtractionLifecycle(
                parseContent = parseContent,
                hasDirectoryOverride = extractionDirOverride != null
            )
            val extractionDir = when (extractionLifecycle.directoryMode) {
                MobileEpubExtractionDirectoryMode.OVERRIDE ->
                    ImportedFileCache.prepareDirectory(requireNotNull(extractionDirOverride))
                MobileEpubExtractionDirectoryMode.TEMPORARY_METADATA ->
                    ImportedFileCache.createTemporaryBookDir(context, bookId, "metadata")
                MobileEpubExtractionDirectoryMode.ACTIVE_CACHE -> {
                    val activeDir = ImportedFileCache.ensureActiveBookDir(context, bookId)
                    if (extractionLifecycle.mayReadCache) {
                        readCachedEpubBook(
                            extractionDir = activeDir,
                            bookId = bookId,
                            originalBookNameHint = originalBookNameHint,
                            shouldUseToc = shouldUseToc,
                            sourceFingerprint = sourceFingerprint
                        )?.let { cachedBook ->
                            Timber.tag("FileOpenPerf").d("[EPUB] Loaded extracted book from cache | bookId=$bookId")
                            return@withContext cachedBook
                        }
                    }
                    ImportedFileCache.resetActiveBookDir(context, bookId)
                }
            }

            val tempFile = File.createTempFile("epub_stream", ".epub", context.cacheDir)
            val filesMap: Map<String, EpubFile>
            try {
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                filesMap = extractEpubContents(
                    zipFile = ZipFile(tempFile),
                    extractionDir = extractionDir,
                    parseContent = parseContent,
                    extractImagesForMetadata = extractionLifecycle.deleteDirectoryAfterLoad
                )
            } finally {
                tempFile.delete()
            }

            val document = createEpubDocument(filesMap)
            val book = parseAndCreateEbook(filesMap, document, shouldUseToc, extractionDir.absolutePath,
                originalBookNameHint, parseContent)
            if (extractionLifecycle.mayWriteCache) {
                writeCachedEpubBook(
                    extractionDir = extractionDir,
                    bookId = bookId,
                    originalBookNameHint = originalBookNameHint,
                    shouldUseToc = shouldUseToc,
                    sourceFingerprint = sourceFingerprint,
                    book = book
                )
            }
            if (extractionLifecycle.deleteDirectoryAfterLoad) {
                extractionDir.deleteRecursively()
            }
            return@withContext book
        }
    }

    private fun readCachedEpubBook(
        extractionDir: File,
        bookId: String,
        originalBookNameHint: String,
        shouldUseToc: Boolean,
        sourceFingerprint: String?
    ): EpubBook? {
        val metadataFile = File(extractionDir, BOOK_METADATA_FILE)
        val manifestFile = File(extractionDir, CACHE_MANIFEST_FILE)
        if (!metadataFile.isFile || !manifestFile.isFile) return null

        return try {
            val manifestText = manifestFile.readTextIfWithinLimit(MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES.toLong()) ?: return null
            val manifest = jsonSerializer.decodeFromString<MobileEpubExtractionCacheManifest>(manifestText)
            val isCompatible = manifest.matchesMobileEpubExtractionCache(
                bookId = bookId,
                originalBookNameHint = originalBookNameHint,
                shouldUseToc = shouldUseToc,
                sourceFingerprint = sourceFingerprint
            )

            if (!isCompatible) {
                Timber.d("EPUB extraction cache manifest is stale for bookId=$bookId")
                return null
            }

            val metadataText = metadataFile.readTextIfWithinLimit(MOBILE_EPUB_MAX_CACHED_BOOK_METADATA_BYTES) ?: return null
            val cachedBook = jsonSerializer.decodeFromString<EpubBook>(metadataText)
                .copy(
                    extractionBasePath = extractionDir.absolutePath,
                    css = readExtractedCss(extractionDir)
                )

            cachedBook.takeIf { it.hasReadableExtractedContent() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read EPUB extraction cache for bookId=$bookId")
            null
        }
    }

    private fun File.readTextIfWithinLimit(maxBytes: Long): String? {
        val size = length()
        if (size > maxBytes) {
            Timber.w("Ignoring oversized EPUB cache file: path=$absolutePath size=$size limit=$maxBytes")
            return null
        }
        return readText(Charsets.UTF_8)
    }

    private fun writeCachedEpubBook(
        extractionDir: File,
        bookId: String,
        originalBookNameHint: String,
        shouldUseToc: Boolean,
        sourceFingerprint: String?,
        book: EpubBook
    ) {
        try {
            val metadataFile = File(extractionDir, BOOK_METADATA_FILE)
            val manifestFile = File(extractionDir, CACHE_MANIFEST_FILE)
            val metadataWritten = writeCachedBookMetadata(metadataFile, book.toExtractionMetadataCache())
            if (!metadataWritten) {
                manifestFile.delete()
                return
            }
            manifestFile.writeText(
                jsonSerializer.encodeToString(
                    MobileEpubExtractionCacheManifest(
                        bookId = bookId,
                        originalBookNameHint = originalBookNameHint,
                        parserVersion = MOBILE_EPUB_EXTRACTION_CACHE_VERSION,
                        parseContent = true,
                        shouldUseToc = shouldUseToc,
                        sourceFingerprint = sourceFingerprint
                    )
                )
            )
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Failed to write EPUB extraction cache without exhausting memory for bookId=$bookId")
            File(extractionDir, BOOK_METADATA_FILE).delete()
            File(extractionDir, CACHE_MANIFEST_FILE).delete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to write EPUB extraction cache for bookId=$bookId")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeCachedBookMetadata(metadataFile: File, cachedBook: EpubBook): Boolean {
        val tempFile = File(metadataFile.parentFile, "${metadataFile.name}.tmp")
        runCatching { tempFile.delete() }
        return try {
            tempFile.outputStream().buffered().use { output ->
                jsonSerializer.encodeToStream(cachedBook, output)
            }
            if (tempFile.length() > MOBILE_EPUB_MAX_CACHED_BOOK_METADATA_BYTES) {
                Timber.w(
                    "Skipping oversized EPUB extraction metadata cache: " +
                        "path=${metadataFile.absolutePath} size=${tempFile.length()} limit=$MOBILE_EPUB_MAX_CACHED_BOOK_METADATA_BYTES"
                )
                tempFile.delete()
                metadataFile.delete()
                return false
            }
            if (metadataFile.exists() && !metadataFile.delete()) {
                Timber.w("Unable to replace existing EPUB metadata cache at ${metadataFile.absolutePath}")
                tempFile.delete()
                return false
            }
            if (!tempFile.renameTo(metadataFile)) {
                Timber.w("Unable to promote EPUB metadata cache temp file at ${tempFile.absolutePath}")
                tempFile.delete()
                return false
            }
            true
        } catch (e: OutOfMemoryError) {
            tempFile.delete()
            metadataFile.delete()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to stream EPUB metadata cache to ${metadataFile.absolutePath}")
            tempFile.delete()
            metadataFile.delete()
            false
        }
    }

    private fun EpubBook.toExtractionMetadataCache(): EpubBook {
        return copy(
            coverImage = null,
            chapters = chapters.map(EpubChapter::toMobileEpubExtractionCacheChapter),
            chaptersForPagination = chaptersForPagination.map(EpubChapter::toMobileEpubExtractionCacheChapter),
            css = emptyMap()
        )
    }

    private fun readExtractedCss(extractionDir: File): Map<String, String> {
        if (!extractionDir.isDirectory) return emptyMap()

        return extractionDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("css", ignoreCase = true) }
            .mapNotNull { file ->
                if (file.length() > MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES) {
                    Timber.w("Skipping oversized extracted CSS file in EPUB cache: ${file.absolutePath}")
                    return@mapNotNull null
                }

                val relativePath = file.relativeTo(extractionDir).path.replace(File.separatorChar, '/')
                relativePath to file.readText(Charsets.UTF_8)
            }
            .toMap()
    }

    internal fun extractEpubContents(
        zipFile: ZipFile,
        extractionDir: File,
        parseContent: Boolean,
        extractImagesForMetadata: Boolean
    ): Map<String, EpubFile> {
        val filesMap = mutableMapOf<String, EpubFile>()
        zipFile.use { zf ->
            zf.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val action = mobileEpubExtractionAction(entry.name, parseContent, extractImagesForMetadata)
                when (action) {
                    MobileEpubExtractionAction.SKIP -> Unit
                    MobileEpubExtractionAction.READ_IN_MEMORY -> {
                        val data = zf.readSmallEntryBytes(entry) ?: return@forEach
                        filesMap[entry.name] = EpubFile(absPath = entry.name, data = data)
                    }
                    MobileEpubExtractionAction.EXTRACT_WITHOUT_MEMORY,
                    MobileEpubExtractionAction.EXTRACT_AND_READ -> {
                        val outputFile = safeExtractionFile(extractionDir, entry.name)
                            ?: return@forEach
                        outputFile.parentFile?.mkdirs()
                        zf.getInputStream(entry).use { input ->
                            FileOutputStream(outputFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val data = if (action == MobileEpubExtractionAction.EXTRACT_AND_READ) {
                            outputFile.readBytes()
                        } else {
                            ByteArray(0)
                        }
                        filesMap[entry.name] = EpubFile(absPath = entry.name, data = data)
                    }
                }
            }
        }
        return filesMap
    }

    private fun safeExtractionFile(extractionDir: File, entryName: String): File? {
        val outputFile = File(extractionDir, entryName)
        val root = extractionDir.canonicalFile
        val target = outputFile.canonicalFile
        val rootPath = root.path
        val targetPath = target.path
        val isInsideRoot = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)

        if (!isInsideRoot) {
            Timber.w("Skipping unsafe EPUB entry outside extraction root: $entryName")
            return null
        }

        return outputFile
    }

    private fun ZipFile.readSmallEntryBytes(entry: ZipEntry): ByteArray? {
        if (entry.size > MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES.toLong()) {
            Timber.w("Skipping oversized EPUB metadata entry: ${entry.name} (${entry.size} bytes)")
            return null
        }

        val initialSize = entry.size
            .takeIf { it in 0..MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES.toLong() }
            ?.toInt()
            ?: DEFAULT_BUFFER_SIZE
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        return getInputStream(entry).use { input ->
            ByteArrayOutputStream(initialSize).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    totalBytes += read
                    if (totalBytes > MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES) {
                        Timber.w("Skipping oversized EPUB metadata entry while reading: ${entry.name}")
                        return null
                    }

                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
    }

    private suspend fun parseAndCreateEbook(
        filesContentMap: Map<String, EpubFile>,
        document: EpubDocument,
        shouldUseToc: Boolean,
        extractionBasePath: String,
        originalFilePathOrKey: String,
        parseContent: Boolean = true
    ): EpubBook = withContext(Dispatchers.IO) {
        val metadataNodes = document.metadata.selectChildTag("meta")
            .ifEmpty { document.metadata.selectChildTag("opf:meta") }
        val metadata = resolveMobileEpubMetadata(
            sourceFileName = originalFilePathOrKey,
            title = document.metadata.selectFirstChildTag("dc:title")?.textContent,
            author = document.metadata.selectFirstChildTag("dc:creator")?.textContent,
            language = document.metadata.selectFirstChildTag("dc:language")?.textContent,
            description = document.metadata.selectFirstChildTag("dc:description")?.textContent,
            metaElements = metadataNodes.map { meta ->
                MobileEpubMetaElement(
                    id = meta.getAttributeValue("id"),
                    name = meta.getAttributeValue("name"),
                    property = meta.getAttributeValue("property"),
                    content = meta.getAttributeValue("content"),
                    text = meta.textContent.trim().takeIf { it.isNotEmpty() },
                    refines = meta.getAttributeValue("refines")
                )
            }.toList()
        )
        val metadataCoverId = getMetadataCoverId(document.metadata)

        Timber.d("EpubParser: Extracted OPF metadata: title='${metadata.title}', author='${metadata.author}'")

        val opfRelativePath = document.opfFilePath
        val opfParentDir = File(opfRelativePath).parentFile ?: File("")
        val manifestItems = getManifestItems(document.manifest, opfParentDir)
        var pageTargets: List<EpubPageTarget> = emptyList()
        val ncxMetadataMap = mutableMapOf<String, NcxMetadata>()
        val extractionRoot = File(extractionBasePath)
        val tocFileItem = if (shouldUseToc) resolveTocFileItem(document.spine, manifestItems) else null
        val tocDocumentNode = tocFileItem?.let { item ->
            val ncxData = filesContentMap[item.absPath]?.data?.takeIf { it.isNotEmpty() }
                ?: File(extractionRoot, item.absPath).takeIf { it.exists() }?.readBytes()
            ncxData?.let { parseXMLFile(it) }
        }
        val ncxParentDir = tocFileItem?.let { File(it.absPath).parentFile ?: File("") }

        if (shouldUseToc) {
            Timber.d("shouldUseToc is true. Attempting to parse NCX.")
            if (tocFileItem != null && ncxParentDir != null) {
                if (tocDocumentNode != null) {
                    Timber.d("Successfully parsed NCX file: ${tocFileItem.absPath}")
                    val pageListElement = tocDocumentNode.selectFirstTag("pageList") as Element?
                    pageTargets = parsePageList(pageListElement, ncxParentDir)

                    val navMapElement = tocDocumentNode.selectFirstTag("navMap") as Element?
                    if (navMapElement != null) {
                        // Recursively parse navMap
                        ncxMetadataMap.putAll(parseNavMapRecursive(navMapElement, ncxParentDir))
                    } else {
                        Timber.d("No <navMap> element found in NCX.")
                    }
                } else {
                    Timber.w("NCX file item '${tocFileItem.absPath}' found in manifest but could not be parsed.")
                }
            } else {
                Timber.d("No NCX file found in manifest. Skipping NCX-based PageList/NavMap.")
            }
        } else {
            Timber.d("shouldUseToc is false. Skipping NCX parsing for PageList/NavMap.")
        }

        Timber.d("Parsing chapters based on OPF spine for rendering order. NCX titles/depth will be used if available.")

        val tableOfContents = if (shouldUseToc) {
            if (tocDocumentNode != null && ncxParentDir != null) {
                val navMapElement = tocDocumentNode.selectFirstTag("navMap") as Element?

                if (navMapElement != null) {
                    parseTableOfContents(navMapElement, ncxParentDir)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val chaptersFromSpine = if (parseContent) {
            parseUsingSpine(document.spine, manifestItems, filesContentMap, ncxMetadataMap, extractionRoot)
        } else {
            emptyList()
        }

        Timber.d("Parsing images (for cover and general access)")
        val images = if (parseContent) {
            parseEpubImages(manifestItems, filesContentMap, extractionRoot)
        } else {
            emptyList()
        }

        Timber.d("Parsing cover image")
        val coverImage = parseCoverImage(metadataCoverId, manifestItems, filesContentMap, extractionRoot)

        val cssContent = if (parseContent) {
            parseEpubCss(manifestItems, filesContentMap, extractionRoot)
        } else {
            emptyMap()
        }

        val logicalBookContent = materializeTocSections(
            spineChapters = chaptersFromSpine,
            tableOfContents = tableOfContents,
            extractionRoot = extractionRoot
        )

        Timber.d(
            "EpubBook created with ${logicalBookContent.chapters.size} reader chapters " +
                "from ${chaptersFromSpine.size} spine documents."
        )
        return@withContext EpubBook(
            fileName = metadata.fileName,
            title = metadata.title,
            author = metadata.author,
            language = metadata.language,
            coverImage = coverImage,
            chapters = logicalBookContent.chapters, chaptersForPagination = logicalBookContent.chapters,
            images = images,
            pageList = pageTargets,
            tableOfContents = logicalBookContent.tableOfContents,
            extractionBasePath = extractionBasePath,
            css = cssContent,
            seriesName = metadata.seriesName,
            seriesIndex = metadata.seriesIndex,
            description = metadata.description
        )
    }

    private data class LogicalBookContent(
        val chapters: List<EpubChapter>,
        val tableOfContents: List<EpubTocEntry>
    )

    /** Materialize fragment TOC entries so reader chapters follow the EPUB's navigation structure. */
    private fun materializeTocSections(
        spineChapters: List<EpubChapter>,
        tableOfContents: List<EpubTocEntry>,
        extractionRoot: File
    ): LogicalBookContent {
        if (spineChapters.isEmpty() || tableOfContents.isEmpty()) {
            return LogicalBookContent(spineChapters, tableOfContents)
        }

        val tocByPath = tableOfContents
            .filter { !it.fragmentId.isNullOrBlank() }
            .groupBy { it.absolutePath }
        val remappedEntries = mutableMapOf<EpubTocEntry, EpubTocEntry>()
        val logicalChapters = mutableListOf<EpubChapter>()

        spineChapters.forEach { spineChapter ->
            val entries = tocByPath[spineChapter.absPath].orEmpty().distinctBy { it.fragmentId }
            val sourceFile = File(extractionRoot, spineChapter.contentFilePath())
            if (entries.size < 2 || !sourceFile.isFile) {
                logicalChapters += spineChapter
                return@forEach
            }

            val sourceDocument = runCatching { Jsoup.parse(sourceFile, "UTF-8") }.getOrNull()
            val body = sourceDocument?.body()
            if (sourceDocument == null || body == null) {
                logicalChapters += spineChapter
                return@forEach
            }

            val bodyChildren = body.children().toList()
            val idChildIndices = mutableMapOf<String, Int>()
            val nameChildIndices = mutableMapOf<String, Int>()
            entries.forEach { entry ->
                val fragmentId = entry.fragmentId ?: return@forEach
                fun directChildIndex(target: org.jsoup.nodes.Element?): Int? = target?.let { targetElement ->
                    generateSequence(targetElement) { it.parent() }
                        .firstOrNull { it.parent() == body }
                }?.let(bodyChildren::indexOf)?.takeIf { it >= 0 }
                directChildIndex(body.getElementById(fragmentId))?.let { idChildIndices.putIfAbsent(fragmentId, it) }
                directChildIndex(body.selectFirst("[name='$fragmentId']"))?.let { nameChildIndices.putIfAbsent(fragmentId, it) }
            }
            val sectionRanges = mobileEpubLogicalSectionRanges(
                entries = entries,
                bodyChildCount = bodyChildren.size,
                fragmentId = EpubTocEntry::fragmentId,
                idChildIndex = idChildIndices::get,
                nameChildIndex = nameChildIndices::get
            )

            if (sectionRanges.isEmpty()) {
                logicalChapters += spineChapter
                return@forEach
            }

            sectionRanges.forEach { range ->
                val entry = range.entry
                val sectionDocument = sourceDocument.clone()
                val sectionBody = sectionDocument.body()
                sectionBody.empty()
                bodyChildren.subList(range.startChildIndex, range.endChildIndexExclusive)
                    .forEach { sectionBody.appendChild(it.clone()) }

                val sourcePath = spineChapter.contentFilePath()
                val extension = sourcePath.substringAfterLast('.', "xhtml")
                val baseName = sourcePath.substringBeforeLast('.', sourcePath)
                val sectionPath = "${baseName}.episteme-section-${range.materializationIndex + 1}.$extension"
                val sectionFile = File(extractionRoot, sectionPath)
                sectionFile.parentFile?.mkdirs()
                sectionFile.writeText(sectionDocument.outerHtml())

                logicalChapters += spineChapter.copy(
                    chapterId = generateId(),
                    absPath = sectionPath,
                    htmlFilePath = sectionPath,
                    title = entry.label,
                    plainTextContent = sectionBody.text(),
                    plainTextLength = sectionBody.text().length,
                    depth = entry.depth,
                    isInToc = true
                )
                remappedEntries[entry] = entry.copy(absolutePath = sectionPath)
            }
        }

        return LogicalBookContent(
            chapters = logicalChapters.ifEmpty { spineChapters },
            tableOfContents = tableOfContents.map { remappedEntries[it] ?: it }
        )
    }

    private fun parseNavigationNodes(
        element: Element,
        ncxParentDir: File
    ): List<MobileEpubNcxNavigationNode> =
            element.childElements.filter { it.tagName == "navPoint" }.map { navPoint ->
                val label = navPoint.selectFirstChildTag("navLabel")
                    ?.selectFirstChildTag("text")?.textContent?.trim()
                val contentSrc = navPoint.selectFirstChildTag("content")
                    ?.getAttributeValue("src")?.decodedURL
                val pathAndFragment = contentSrc?.let {
                    val fullPathRaw = resolveMobileEpubReference("${ncxParentDir.path}/_", contentSrc)
                    val parts = fullPathRaw.split("#", limit = 2)
                    parts[0] to parts.getOrNull(1)
                }
                MobileEpubNcxNavigationNode(
                    label = label,
                    absolutePath = pathAndFragment?.first,
                    fragmentId = pathAndFragment?.second,
                    children = parseNavigationNodes(navPoint, ncxParentDir)
                )
            }.toList()

    private fun parseTableOfContents(navMapElement: Element, ncxParentDir: File): List<EpubTocEntry> =
        flattenMobileEpubNcxNavigation(parseNavigationNodes(navMapElement, ncxParentDir))

    private fun resolveTocFileItem(
        spine: Node,
        manifestItems: Map<String, EpubManifestItem>
    ): EpubManifestItem? {
        val id = resolveMobileEpubNcxManifestId(
            spineTocId = spine.getAttributeValue("toc"),
            manifest = manifestItems.values.toList()
        ) ?: return null
        return manifestItems[id]
    }

    @Throws(EpubParserException::class)
    private fun createEpubDocument(files: Map<String, EpubFile>): EpubDocument {
        val containerFile = files["META-INF/container.xml"]
            ?: throw EpubParserException("META-INF/container.xml file missing")

        val rawOpfPath = listOf(
            parseXMLFile(containerFile.data)?.selectFirstTag("rootfile")
                ?.getAttributeValue("full-path")?.decodedURL
        )
        val opfFilePath = resolveMobileEpubOpfPath(rawOpfPath)
            ?: throw EpubParserException("Invalid container.xml: Could not find rootfile full-path.")

        val opfFile = files[opfFilePath]
            ?: throw EpubParserException(".opf file missing at normalized path '$opfFilePath'.")

        val document = parseXMLFile(opfFile.data)
            ?: throw EpubParserException(".opf file failed to parse data from '$opfFilePath'")
        val metadata = document.selectFirstTag("metadata")
            ?: document.selectFirstTag("opf:metadata")
            ?: throw EpubParserException(".opf file metadata section missing in '$opfFilePath'")
        val manifest = document.selectFirstTag("manifest")
            ?: document.selectFirstTag("opf:manifest")
            ?: throw EpubParserException(".opf file manifest section missing in '$opfFilePath'")
        val spine = document.selectFirstTag("spine")
            ?: document.selectFirstTag("opf:spine")
            ?: throw EpubParserException(".opf file spine section missing in '$opfFilePath'")

        return EpubDocument(metadata, manifest, spine, opfFilePath)
    }


    private fun getMetadataCoverId(metadata: Node): String? {
        return metadata.selectChildTag("meta")
            .ifEmpty { metadata.selectChildTag("opf:meta") }
            .find { it.getAttributeValue("name") == "cover" }?.getAttributeValue("content")
    }

    private fun getManifestItems(
        manifest: Node,
        opfParentDir: File
    ): Map<String, EpubManifestItem> {
        return manifest.selectChildTag("item")
            .ifEmpty { manifest.selectChildTag("opf:item") }
            .mapNotNull { itemElement ->
                val href = itemElement.getAttribute("href")?.decodedURL ?: return@mapNotNull null
                val pathRelativeToEpubRoot = resolveMobileEpubReference("${opfParentDir.path}/_", href)

                EpubManifestItem(
                    id = itemElement.getAttribute("id"),
                    absPath = pathRelativeToEpubRoot,
                    mediaType = itemElement.getAttribute("media-type"),
                    properties = itemElement.getAttribute("properties")
                )
            }.associateBy { it.id }
    }

    private fun parseNavMapRecursive(
        element: Element,
        ncxFileParentDir: File
    ): Map<String, NcxMetadata> {
        val result = mobileEpubNcxChapterMetadata(parseNavigationNodes(element, ncxFileParentDir))
        result.forEach { (path, metadata) ->
            Timber.d("NCX Map: '$path' -> '${metadata.title}' (Depth ${metadata.depth})")
        }
        return result
    }

    private fun generateId(): String {
        return UUID.randomUUID().toString()
    }

    private suspend fun parseUsingSpine(
        spine: Node,
        manifestItems: Map<String, EpubManifestItem>,
        filesContentMap: Map<String, EpubFile>,
        ncxMetadataMap: Map<String, NcxMetadata>,
        extractionRoot: File
    ): List<EpubChapter> = withContext(Dispatchers.Default) {
        val parsingSemaphore = Semaphore(6)

        val spineItems = spine.selectChildTag("itemref")
            .ifEmpty { spine.selectChildTag("opf:itemref") }
        val spineIds = mobileEpubSpineItemIds(spineItems.map { it.getAttributeValue("idref") }.toList())

        val deferredChapters = spineIds.mapIndexed { index, idRef ->
            async {
                parsingSemaphore.withPermit {
                    val item = manifestItems[idRef] ?: return@withPermit null

                    val fileBytes = filesContentMap[item.absPath]?.data?.takeIf { it.isNotEmpty() }
                        ?: File(extractionRoot, item.absPath).takeIf { it.exists() }?.readBytes()
                        ?: return@withPermit null

                    val mediaType = item.mediaType
                    val absPath = item.absPath

                    when (mobileEpubSpineResourceKind(mediaType, absPath)) {
                        MobileEpubSpineResourceKind.HTML -> {
                            val rawHtml = String(fileBytes, Charsets.UTF_8)
                            val document = Jsoup.parse(rawHtml)
                            val plainText = document.text()

                            val parser = EpubXMLFileParser(
                                fileRelativePath = absPath,
                                data = fileBytes,
                                fragmentId = null
                            )
                            val res = parser.parseForTitleAndPath(document)

                            val navigation = resolveMobileEpubChapterNavigation(
                                absolutePath = absPath,
                                fallbackTitle = res.title,
                                navigationMetadata = ncxMetadataMap
                            )

                            TempEpubChapter(
                                url = absPath,
                                title = navigation.title,
                                htmlFilePath = res.effectiveHtmlPath,
                                chapterIndex = index + 1,
                                plainTextContent = plainText,
                                htmlContent = "", // OPTIMIZATION: Don't store HTML in memory, it's on disk
                                depth = navigation.depth,
                                isInToc = navigation.isInToc
                            )
                        }
                        MobileEpubSpineResourceKind.IMAGE -> {
                            // Image handling remains similar, but usually small enough
                            val htmlContent = """
                                <!DOCTYPE html><html style="margin:0;padding:0;height:100%;"><head><title>Image</title></head><body style="margin:0;padding:0;height:100%;text-align:center;"><img src="$absPath" alt="Image from spine" style="object-fit:contain;width:100%;height:100%;"/></body></html>
                            """.trimIndent()

                            val navigation = resolveMobileEpubChapterNavigation(
                                absolutePath = absPath,
                                fallbackTitle = "Image",
                                navigationMetadata = ncxMetadataMap
                            )

                            TempEpubChapter(
                                url = absPath,
                                title = navigation.title,
                                htmlFilePath = absPath,
                                chapterIndex = index + 1,
                                plainTextContent = "[Image]",
                                htmlContent = htmlContent,
                                depth = navigation.depth,
                                isInToc = navigation.isInToc
                            )
                        }
                        MobileEpubSpineResourceKind.UNSUPPORTED -> null
                    }
                }
            }
        }

        val tempChapters = deferredChapters.toList().awaitAll().filterNotNull()

        return@withContext tempChapters.map { tempChapter ->
            EpubChapter(
                chapterId = generateId(),
                absPath = tempChapter.url,
                title = resolveMobileEpubSpineChapterTitle(tempChapter.title, tempChapter.chapterIndex - 1),
                htmlFilePath = tempChapter.htmlFilePath,
                plainTextContent = tempChapter.plainTextContent,
                htmlContent = tempChapter.htmlContent,
                depth = tempChapter.depth,
                isInToc = tempChapter.isInToc
            )
        }.filter { it.htmlFilePath.isNotBlank() }
    }

    private fun parseEpubImages(
        manifestItems: Map<String, EpubManifestItem>,
        filesContentMap: Map<String, EpubFile>,
        @Suppress("UNUSED_PARAMETER") extractionRoot: File
    ): List<EpubImage> {
        return mobileEpubImages(manifestItems.values.toList(), filesContentMap.keys.toList()).also {
            Timber.d("Identified ${it.size} images (content not loaded into memory).")
        }
    }

    private fun parseCoverImage(
        metadataCoverId: String?,
        manifestItems: Map<String, EpubManifestItem>,
        filesContentMap: Map<String, EpubFile>,
        extractionRoot: File
    ): Bitmap? {
        mobileEpubCoverCandidates(metadataCoverId, manifestItems.values.toList(), filesContentMap.keys).forEach { path ->
            decodeEpubImage(path, filesContentMap, extractionRoot)?.let {
                Timber.d("Found cover image at $path")
                return it
            }
        }
        Timber.d("Cover image could not be loaded from metadata or common fallbacks.")
        return null
    }

    private fun decodeEpubImage(
        path: String,
        filesContentMap: Map<String, EpubFile>,
        extractionRoot: File
    ): Bitmap? {
        filesContentMap[path]?.data?.takeIf { it.isNotEmpty() }?.let { bytes ->
            return decodeSampledByteArray(bytes)
        }

        val imageFile = File(extractionRoot, path).takeIf { it.exists() && it.isFile } ?: return null
        return decodeSampledFile(imageFile)
    }

    private fun decodeSampledByteArray(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = mobileEpubCoverBitmapSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeSampledFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = mobileEpubCoverBitmapSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

}
