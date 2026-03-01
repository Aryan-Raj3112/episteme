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
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.UUID
import java.util.zip.ZipFile

class EpubParser(private val context: Context) {
    data class EpubDocument(
        val metadata: Node, val manifest: Node, val spine: Node, val opfFilePath: String
    )

    data class EpubManifestItem(
        val id: String, val absPath: String, val mediaType: String, val properties: String
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

    // Helper class for NCX parsing results
    data class NcxMetadata(
        val title: String,
        val depth: Int
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
    @Serializable
    data class EpubPageTarget(
        val id: String?,
        val value: String?,
        val label: String?,
        val contentSrc: String
    )

    companion object {
        const val TAG = "EpubParser"
        internal const val EXTRACTED_EPUB_DIR_NAME = "extracted_epubs"
    }

    internal val String.decodedURL: String
        get() = try {
            URLDecoder.decode(this, "UTF-8")
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode URL: $this")
            this
        }

    private fun getBookExtractionDir(bookIdentifier: String): File {
        val parentDir = File(context.cacheDir, EXTRACTED_EPUB_DIR_NAME)
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        return File(parentDir, bookIdentifier)
    }


    private fun parsePageList(pageListElement: Element?, ncxFileParentDir: File): List<EpubPageTarget> {
        if (pageListElement == null) {
            Timber.d("No <pageList> element found in NCX.")
            return emptyList()
        }
        val pageTargets = mutableListOf<EpubPageTarget>()
        pageListElement.selectChildTag("pageTarget").forEach { ptElement ->
            val contentSrcRaw = ptElement.selectFirstChildTag("content")?.getAttributeValue("src")?.decodedURL
            if (contentSrcRaw != null) {
                val contentPathRelativeToEpubRoot = Paths.get(ncxFileParentDir.path, contentSrcRaw)
                    .normalize().toString().replace(File.separatorChar, '/')

                pageTargets.add(
                    EpubPageTarget(
                        id = ptElement.getAttributeValue("id"),
                        value = ptElement.getAttributeValue("value"),
                        label = ptElement.selectFirstChildTag("navLabel")?.selectFirstChildTag("text")?.textContent,
                        contentSrc = contentPathRelativeToEpubRoot
                    )
                )
            } else {
                Timber.w("PageTarget found with no content src: ${ptElement.getAttributeValue("id")}")
            }
        }
        Timber.d("Parsed ${pageTargets.size} page targets from NCX.")
        return pageTargets
    }

    private fun parseEpubCss(
        manifestItems: Map<String, EpubManifestItem>,
        filesContentMap: Map<String, EpubFile>,
        extractionRoot: File
    ): Map<String, String> {
        val listedCss = manifestItems.values
            .filter { it.mediaType == "text/css" }
            .mapNotNull { manifestItem ->
                val bytes = filesContentMap[manifestItem.absPath]?.data?.takeIf { it.isNotEmpty() }
                    ?: File(extractionRoot, manifestItem.absPath).takeIf { it.exists() }?.readBytes()

                bytes?.let { manifestItem.absPath to String(it, Charsets.UTF_8) }
            }

        val listedCssPaths = listedCss.map { it.first }.toSet()
        val unlistedCss = filesContentMap.asSequence()
            .filter { (path, _) -> path.endsWith(".css", ignoreCase = true) }
            .filterNot { (path, _) -> listedCssPaths.contains(path) }
            .mapNotNull { (path, file) ->
                val bytes = file.data.takeIf { it.isNotEmpty() }
                    ?: File(extractionRoot, path).takeIf { it.exists() }?.readBytes()

                bytes?.let { path to String(it, Charsets.UTF_8) }
            }
            .toList()

        val allCss = (listedCss + unlistedCss).toMap()

        Timber.d("Parsed ${allCss.size} CSS files (listed: ${listedCss.size}, unlisted: ${unlistedCss.size}): ${allCss.keys.joinToString()}")
        return allCss
    }

    suspend fun createEpubBook(
        inputStream: InputStream,
        shouldUseToc: Boolean = true,
        originalBookNameHint: String = "streamed_book",
        parseContent: Boolean = true
    ): EpubBook {
        return withContext(Dispatchers.IO) {
            Timber.d("Parsing EPUB input stream")

            val bookIdentifier = originalBookNameHint.asFileName() + "_" + UUID.randomUUID().toString().substring(0, 8)
            val extractionDir = getBookExtractionDir(bookIdentifier)

            if (extractionDir.exists()) {
                extractionDir.deleteRecursively()
            }
            extractionDir.mkdirs()

            val tempFile = File.createTempFile("epub_stream", ".epub", context.cacheDir)
            val filesMap: Map<String, EpubFile>
            try {
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                filesMap = extractEpubContents(ZipFile(tempFile), extractionDir, parseContent)
            } finally {
                tempFile.delete()
            }

            val document = createEpubDocument(filesMap)
            val book = parseAndCreateEbook(filesMap, document, shouldUseToc, extractionDir.absolutePath,
                bookIdentifier, parseContent)
            return@withContext book
        }
    }

    private fun extractEpubContents(zipFile: ZipFile, extractionDir: File, parseContent: Boolean): Map<String, EpubFile> {
        val filesMap = mutableMapOf<String, EpubFile>()
        zipFile.use { zf ->
            zf.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val outputFile = File(extractionDir, entry.name)
                outputFile.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val data = if (isEssentialFile(entry.name, parseContent)) {
                    outputFile.readBytes()
                } else {
                    ByteArray(0)
                }

                filesMap[entry.name] = EpubFile(absPath = entry.name, data = data)
            }
        }
        return filesMap
    }

    private suspend fun parseAndCreateEbook(
        filesContentMap: Map<String, EpubFile>,
        document: EpubDocument,
        shouldUseToc: Boolean,
        extractionBasePath: String,
        originalFilePathOrKey: String,
        parseContent: Boolean = true
    ): EpubBook = withContext(Dispatchers.IO) {
        val metadataTitle =
            document.metadata.selectFirstChildTag("dc:title")?.textContent ?: File(originalFilePathOrKey).nameWithoutExtension
        val metadataAuthor =
            document.metadata.selectFirstChildTag("dc:creator")?.textContent ?: "Unknown Author"
        val metadataLanguage =
            document.metadata.selectFirstChildTag("dc:language")?.textContent ?: "en"
        val metadataCoverId = getMetadataCoverId(document.metadata)
        val opfRelativePath = document.opfFilePath
        val opfParentDir = File(opfRelativePath).parentFile ?: File("")
        val manifestItems = getManifestItems(document.manifest, opfParentDir)
        var pageTargets: List<EpubPageTarget> = emptyList()
        val ncxMetadataMap = mutableMapOf<String, NcxMetadata>()
        val extractionRoot = File(extractionBasePath)

        if (shouldUseToc) {
            Timber.d("shouldUseToc is true. Attempting to parse NCX.")
            val tocFileItem = manifestItems.values.firstOrNull {
                it.absPath.endsWith(".ncx", ignoreCase = true)
            }
            if (tocFileItem != null) {
                val ncxParentDir = File(tocFileItem.absPath).parentFile ?: File("")
                val ncxData = filesContentMap[tocFileItem.absPath]?.data?.takeIf { it.isNotEmpty() }
                    ?: File(extractionRoot, tocFileItem.absPath).takeIf { it.exists() }?.readBytes()

                val tocDocumentNode = ncxData?.let { parseXMLFile(it) }

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
            val tocFileItem = manifestItems.values.firstOrNull {
                it.absPath.endsWith(".ncx", ignoreCase = true)
            }
            if (tocFileItem != null) {
                val ncxParentDir = File(tocFileItem.absPath).parentFile ?: File("")
                val ncxData = filesContentMap[tocFileItem.absPath]?.data?.takeIf { it.isNotEmpty() }
                    ?: File(extractionRoot, tocFileItem.absPath).takeIf { it.exists() }?.readBytes()
                val tocDocumentNode = ncxData?.let { parseXMLFile(it) }
                val navMapElement = tocDocumentNode?.selectFirstTag("navMap") as Element?

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
            parseUsingSpine(document.spine, manifestItems, filesContentMap, ncxMetadataMap)
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

        Timber.d("EpubBook created with ${chaptersFromSpine.size} spine chapters.")
        return@withContext EpubBook(
            fileName = metadataTitle.asFileName(),
            title = metadataTitle,
            author = metadataAuthor,
            language = metadataLanguage,
            coverImage = coverImage,
            chapters = chaptersFromSpine, chaptersForPagination = chaptersFromSpine,
            images = images,
            pageList = pageTargets,
            tableOfContents = tableOfContents,
            extractionBasePath = extractionBasePath,
            css = cssContent
        )
    }

    private fun parseTableOfContents(
        navMapElement: Element,
        ncxParentDir: File
    ): List<EpubTocEntry> {
        val result = mutableListOf<EpubTocEntry>()

        fun recurse(element: Element, currentDepth: Int) {
            val navPoints = element.childElements.filter { it.tagName == "navPoint" }
            for (navPoint in navPoints) {
                val label = navPoint.selectFirstChildTag("navLabel")
                    ?.selectFirstChildTag("text")?.textContent?.trim() ?: "Untitled"

                val contentSrc = navPoint.selectFirstChildTag("content")
                    ?.getAttributeValue("src")?.decodedURL

                if (contentSrc != null) {
                    val fullPathRaw = Paths.get(ncxParentDir.path, contentSrc)
                        .normalize().toString().replace(File.separatorChar, '/')

                    val parts = fullPathRaw.split("#", limit = 2)
                    val absolutePath = parts[0]
                    val fragmentId = if (parts.size > 1) parts[1] else null

                    result.add(EpubTocEntry(label, absolutePath, fragmentId, currentDepth))

                    recurse(navPoint, currentDepth + 1)
                }
            }
        }

        recurse(navMapElement, 0)
        return result
    }


    @Throws(EpubParserException::class)
    private fun createEpubDocument(files: Map<String, EpubFile>): EpubDocument {
        val containerFile = files["META-INF/container.xml"]
            ?: throw EpubParserException("META-INF/container.xml file missing")

        val rawOpfPath = parseXMLFile(containerFile.data)?.selectFirstTag("rootfile")
            ?.getAttributeValue("full-path")?.decodedURL
            ?: throw EpubParserException("Invalid container.xml: Could not find rootfile full-path.")

        val opfFilePath = rawOpfPath.trimStart('/')

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
                val pathRelativeToEpubRoot = Paths.get(opfParentDir.path, href)
                    .normalize().toString().replace(File.separatorChar, '/')

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
        ncxFileParentDir: File,
        depth: Int = 0
    ): Map<String, NcxMetadata> {
        val result = mutableMapOf<String, NcxMetadata>()

        val navPoints = element.childElements.filter { it.tagName == "navPoint" }

        for (navPoint in navPoints) {
            val navLabelText = navPoint.selectFirstChildTag("navLabel")
                ?.selectFirstChildTag("text")?.textContent?.trim()
            val contentSrcRaw = navPoint.selectFirstChildTag("content")
                ?.getAttributeValue("src")?.decodedURL

            if (navLabelText != null && contentSrcRaw != null && navLabelText.isNotEmpty()) {
                val contentPathRelativeToEpubRoot = Paths.get(ncxFileParentDir.path, contentSrcRaw)
                    .normalize().toString().replace(File.separatorChar, '/')
                    .substringBefore('#')

                if (!result.containsKey(contentPathRelativeToEpubRoot)) {
                    result[contentPathRelativeToEpubRoot] = NcxMetadata(navLabelText, depth)
                    Timber.d("NCX Map: '$contentPathRelativeToEpubRoot' -> '$navLabelText' (Depth $depth)")
                }
            }
            result.putAll(parseNavMapRecursive(navPoint, ncxFileParentDir, depth + 1))
        }

        return result
    }

    private fun generateId(): String {
        return UUID.randomUUID().toString()
    }

    private fun parseUsingSpine(
        spine: Node,
        manifestItems: Map<String, EpubManifestItem>,
        filesContentMap: Map<String, EpubFile>,
        ncxMetadataMap: Map<String, NcxMetadata>
    ): List<EpubChapter> {
        var chapterCounter = 0
        val tempChapters = mutableListOf<TempEpubChapter>()

        spine.selectChildTag("itemref")
            .ifEmpty { spine.selectChildTag("opf:itemref") }
            .mapNotNull { manifestItems[it.getAttribute("idref")] }
            .forEach { item ->
                val fileBytes = filesContentMap[item.absPath]?.data
                if (fileBytes != null) {
                    if (item.mediaType.startsWith("application/xhtml+xml") ||
                        item.mediaType.startsWith("text/html") ||
                        item.absPath.endsWith(".html", ignoreCase = true) ||
                        item.absPath.endsWith(".xhtml", ignoreCase = true) ||
                        item.absPath.endsWith(".xml", ignoreCase = true)
                    ) {
                        val rawHtml = String(fileBytes, Charsets.UTF_8)
                        val plainText = Jsoup.parse(rawHtml).text()

                        val parser = EpubXMLFileParser(
                            fileRelativePath = item.absPath,
                            data = fileBytes,
                            fragmentId = null
                        )
                        val res = parser.parseForTitleAndPath()
                        val chapterTitleFromHtml = res.title
                        val ncxKey = item.absPath.substringBefore('#')
                        val ncxData = ncxMetadataMap[ncxKey]
                        val isEffectiveInToc = if (ncxMetadataMap.isNotEmpty()) {
                            ncxData != null
                        } else {
                            true
                        }
                        val finalChapterTitle = if (ncxData != null && ncxData.title.isNotBlank()) {
                            ncxData.title
                        } else {
                            Timber.d("No NCX title for ${item.absPath}, using HTML title: '$chapterTitleFromHtml'")
                            chapterTitleFromHtml
                        }
                        val finalDepth = ncxData?.depth ?: 0

                        chapterCounter++

                        tempChapters.add(
                            TempEpubChapter(
                                url = item.absPath,
                                title = finalChapterTitle,
                                htmlFilePath = res.effectiveHtmlPath,
                                chapterIndex = chapterCounter,
                                plainTextContent = plainText,
                                htmlContent = rawHtml,
                                depth = finalDepth,
                                isInToc = isEffectiveInToc
                            )
                        )
                    } else if (item.mediaType.startsWith("image/")) {
                        val htmlContent = """
                            <!DOCTYPE html><html style="margin:0;padding:0;height:100%;"><head><title>Image</title></head><body style="margin:0;padding:0;height:100%;text-align:center;"><img src="${item.absPath}" alt="Image from spine" style="object-fit:contain;width:100%;height:100%;"/></body></html>
                        """.trimIndent()

                        val ncxKey = item.absPath.substringBefore('#')
                        val ncxData = ncxMetadataMap[ncxKey]
                        val isEffectiveInToc = if (ncxMetadataMap.isNotEmpty()) ncxData != null else true

                        chapterCounter++

                        tempChapters.add(
                            TempEpubChapter(
                                url = item.absPath,
                                title = ncxData?.title ?: "Image",
                                htmlFilePath = item.absPath,
                                chapterIndex = chapterCounter,
                                plainTextContent = "[Image]",
                                htmlContent = htmlContent,
                                depth = ncxData?.depth ?: 0,
                                isInToc = isEffectiveInToc
                            )
                        )
                    }
                }
            }

        return tempChapters.map { tempChapter ->
            EpubChapter(
                chapterId = generateId(),
                absPath = tempChapter.url,
                title = tempChapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${tempChapter.chapterIndex}",
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
        val imageExtensions = setOf(".png", ".gif", ".jpg", ".jpeg", ".webp", ".svg")

        val listedImages = manifestItems.values
            .filter { it.mediaType.startsWith("image/") }
            .map { manifestItem ->
                EpubImage(absPath = manifestItem.absPath)
            }

        val listedPaths = listedImages.map { it.absPath }.toSet()

        val unlistedImages = filesContentMap.keys
            .filter { path ->
                val lowerPath = path.lowercase()
                imageExtensions.any { lowerPath.endsWith(it) } && !listedPaths.contains(path)
            }
            .map { path ->
                EpubImage(absPath = path)
            }

        Timber.d("Identified ${listedImages.size + unlistedImages.size} images (content not loaded into memory).")
        return (listedImages + unlistedImages).distinctBy { it.absPath }
    }

    private fun parseCoverImage(
        metadataCoverId: String?,
        manifestItems: Map<String, EpubManifestItem>,
        filesContentMap: Map<String, EpubFile>,
        extractionRoot: File
    ): Bitmap? {
        val coverManifestItem = manifestItems[metadataCoverId]
        if (coverManifestItem != null) {
            val coverImageBytes = filesContentMap[coverManifestItem.absPath]?.data?.takeIf { it.isNotEmpty() }
                ?: File(extractionRoot, coverManifestItem.absPath).takeIf { it.exists() }?.readBytes()

            if (coverImageBytes != null) {
                return BitmapFactory.decodeByteArray(coverImageBytes, 0, coverImageBytes.size)
            } else {
                Timber.e("Cover image file content not found for path: ${coverManifestItem.absPath}")
            }
        } else {
            if (metadataCoverId != null) {
                Timber.w("Cover image ID '$metadataCoverId' not found in manifest.")
            } else {
                Timber.d("No cover image ID specified in metadata.")
            }
        }

        val commonCoverNames = listOf("cover.jpg", "cover.jpeg", "cover.png")
        for (name in commonCoverNames) {
            val possiblePaths = listOf(
                name, "images/$name", "Images/$name", "image/$name", "Image/$name",
                "OEBPS/images/$name", "OEBPS/Images/$name", "OEBPS/image/$name", "OEBPS/Image/$name",
                "OPS/images/$name", "OPS/Images/$name", "OPS/image/$name", "OPS/Image/$name"
            )
            for (path in possiblePaths) {
                if (filesContentMap.containsKey(path)) {
                    val bytes = filesContentMap[path]?.data?.takeIf { it.isNotEmpty() }
                        ?: File(extractionRoot, path).takeIf { it.exists() }?.readBytes()

                    bytes?.let {
                        Timber.d("Found fallback cover image at $path")
                        return BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }
                manifestItems.values.find { item -> item.absPath.equals(path, ignoreCase = true) && item.mediaType.startsWith("image/") }?.let { manifestItem ->
                    val bytes = filesContentMap[manifestItem.absPath]?.data?.takeIf { it.isNotEmpty() }
                        ?: File(extractionRoot, manifestItem.absPath).takeIf { it.exists() }?.readBytes()

                    bytes?.let {
                        Timber.d("Found fallback cover image via manifest item (case-insensitive) at ${manifestItem.absPath}")
                        return BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }
            }
        }
        Timber.d("Cover image could not be loaded from metadata or common fallbacks.")
        return null
    }

    private fun isEssentialFile(fileName: String, parseContent: Boolean): Boolean {
        val lowerName = fileName.lowercase()

        if (lowerName.endsWith("container.xml") || lowerName.endsWith(".opf")) {
            return true
        }

        if (!parseContent) {
            return false
        }

        return lowerName.endsWith(".xml") ||
                lowerName.endsWith(".ncx") ||
                lowerName.endsWith(".html") ||
                lowerName.endsWith(".xhtml") ||
                lowerName.endsWith(".htm") ||
                lowerName.endsWith(".css")
    }
}