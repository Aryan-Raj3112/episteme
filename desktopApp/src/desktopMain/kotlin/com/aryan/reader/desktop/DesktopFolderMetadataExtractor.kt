package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.reader.SharedJvmBookLoader
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.max

data class DesktopFolderMetadataExtractionResult(
    val books: List<BookItem>,
    val stats: DesktopFolderMetadataExtractionStats = DesktopFolderMetadataExtractionStats()
)

data class DesktopFolderMetadataExtractionStats(
    val processedBooks: Int = 0,
    val updatedBooks: Int = 0,
    val coversUpdated: Int = 0,
    val failedBooks: Int = 0
) {
    operator fun plus(other: DesktopFolderMetadataExtractionStats): DesktopFolderMetadataExtractionStats {
        return DesktopFolderMetadataExtractionStats(
            processedBooks = processedBooks + other.processedBooks,
            updatedBooks = updatedBooks + other.updatedBooks,
            coversUpdated = coversUpdated + other.coversUpdated,
            failedBooks = failedBooks + other.failedBooks
        )
    }
}

object DesktopFolderMetadataExtractor {
    private val textMetadataTypes = setOf(
        FileType.PDF,
        FileType.EPUB,
        FileType.HTML,
        FileType.MOBI,
        FileType.FB2,
        FileType.DOCX,
        FileType.ODT,
        FileType.FODT
    )
    private val generatedCoverTypes = SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP)
    private val rasterCoverExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun enrichFolderBooks(
        books: List<BookItem>,
        sourceFolder: String
    ): DesktopFolderMetadataExtractionResult {
        return enrichBooks(books) { book -> book.sourceFolder == sourceFolder }
    }

    fun enrichImportedBooks(
        books: List<BookItem>,
        importedBookIds: Set<String>
    ): DesktopFolderMetadataExtractionResult {
        if (importedBookIds.isEmpty()) {
            return DesktopFolderMetadataExtractionResult(books)
        }
        return enrichBooks(books) { book -> book.id in importedBookIds }
    }

    fun enrichOpenedBook(book: BookItem): BookItem {
        if (!book.needsFolderMetadataExtraction()) return book
        return runCatching { enrichBook(book) }.getOrDefault(book)
    }

    private fun enrichBooks(
        books: List<BookItem>,
        shouldConsider: (BookItem) -> Boolean
    ): DesktopFolderMetadataExtractionResult {
        var stats = DesktopFolderMetadataExtractionStats()
        val updatedBooks = books.map { book ->
            if (!shouldConsider(book) || !book.needsFolderMetadataExtraction()) {
                return@map book
            }

            stats = stats.copy(processedBooks = stats.processedBooks + 1)
            val updated = runCatching { enrichBook(book) }
                .onFailure { stats = stats.copy(failedBooks = stats.failedBooks + 1) }
                .getOrDefault(book)

            if (updated != book) {
                stats = stats.copy(updatedBooks = stats.updatedBooks + 1)
                if (updated.coverImagePath != book.coverImagePath) {
                    stats = stats.copy(coversUpdated = stats.coversUpdated + 1)
                }
            }
            updated
        }
        return DesktopFolderMetadataExtractionResult(updatedBooks, stats)
    }

    private fun BookItem.needsFolderMetadataExtraction(): Boolean {
        val path = path?.takeIf { it.isNotBlank() } ?: return false
        val file = File(path)
        if (!file.isFile) return false
        val needsTextMetadata = type in textMetadataTypes && !folderTextMetadataParsed
        val needsCover = type in generatedCoverTypes && coverImagePath?.let { File(it).isFile } != true
        return needsTextMetadata || needsCover
    }

    private fun enrichBook(book: BookItem): BookItem {
        val file = File(book.path.orEmpty())
        val size = file.length().takeIf { it > 0L } ?: book.fileSize
        var extractedTitle: String? = null
        var extractedAuthor: String? = null
        var extractedDescription: String? = null
        var extractedSeriesName: String? = null
        var extractedSeriesIndex: Double? = null
        var textMetadataParsed = book.folderTextMetadataParsed
        var embeddedCover: EmbeddedCover? = null

        when (book.type) {
            FileType.EPUB -> {
                val metadata = parseEpubMetadata(file)
                extractedTitle = sanitizeTitle(metadata.title)
                extractedAuthor = sanitizeAuthor(metadata.author)
                extractedDescription = sanitizeDescription(metadata.description)
                extractedSeriesName = sanitizeDescription(metadata.seriesName)
                extractedSeriesIndex = metadata.seriesIndex?.takeIf { it > 0.0 }
                embeddedCover = metadata.cover
                textMetadataParsed = true
            }
            FileType.PDF -> {
                val metadata = runCatching { DesktopPdfium.extractMetadata(file) }.getOrNull()
                extractedTitle = sanitizeTitle(metadata?.title)
                extractedAuthor = sanitizeAuthor(metadata?.author)
                extractedDescription = sanitizeDescription(metadata?.description)
                textMetadataParsed = true
            }
            FileType.HTML -> {
                extractedTitle = sanitizeTitle(parseHtmlTitle(file))
                extractedDescription = sanitizeDescription(parseHtmlDescription(file))
                textMetadataParsed = true
            }
            FileType.MOBI,
            FileType.FB2,
            FileType.DOCX,
            FileType.ODT,
            FileType.FODT -> {
                runCatching { SharedJvmBookLoader.load(file, book.type) }
                    .onSuccess { loaded ->
                        extractedTitle = sanitizeTitle(loaded.title)
                        extractedAuthor = sanitizeAuthor(loaded.author)
                        textMetadataParsed = true
                    }
            }
            else -> Unit
        }

        val coverPath = book.coverImagePath?.takeIf { File(it).isFile }
            ?: saveEmbeddedCover(book, embeddedCover)
            ?: renderReaderSurfaceCover(book, file)
            ?: saveGeneratedCover(book)

        val nextTitle = if (book.shouldApplyExtractedTitle(file)) {
            extractedTitle ?: book.title ?: file.nameWithoutExtension
        } else {
            book.title
        }
        val nextAuthor = if (book.shouldApplyExtractedText(book.author, book.originalAuthor)) {
            extractedAuthor ?: book.author
        } else {
            book.author
        }
        val nextDescription = if (book.shouldApplyExtractedText(book.description, book.originalDescription)) {
            extractedDescription ?: book.description
        } else {
            book.description
        }
        val nextSeriesName = if (book.shouldApplyExtractedText(book.seriesName, book.originalSeriesName)) {
            extractedSeriesName ?: book.seriesName
        } else {
            book.seriesName
        }
        val nextSeriesIndex = if (book.seriesIndex == null || book.seriesIndex == book.originalSeriesIndex) {
            extractedSeriesIndex ?: book.seriesIndex
        } else {
            book.seriesIndex
        }

        return book.copy(
            title = nextTitle,
            author = nextAuthor,
            description = nextDescription,
            seriesName = nextSeriesName,
            seriesIndex = nextSeriesIndex,
            originalTitle = book.originalTitle ?: extractedTitle,
            originalAuthor = book.originalAuthor ?: extractedAuthor,
            originalSeriesName = book.originalSeriesName ?: extractedSeriesName,
            originalSeriesIndex = book.originalSeriesIndex ?: extractedSeriesIndex,
            originalDescription = book.originalDescription ?: extractedDescription,
            fileSize = size,
            fileContentModifiedTimestamp = file.lastModified(),
            coverImagePath = coverPath,
            folderTextMetadataParsed = textMetadataParsed
        )
    }

    private fun parseEpubMetadata(file: File): ExtractedBookMetadata {
        ZipFile(file).use { zip ->
            val containerXml = zip.readTextOrNull("META-INF/container.xml")
            val opfPath = containerXml
                ?.let(::parseEpubRootfilePath)
                ?: zip.entries().asSequence()
                    .map { it.name }
                    .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
                ?: return ExtractedBookMetadata()
            val opf = zip.readTextOrNull(opfPath) ?: return ExtractedBookMetadata()
            val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
                .let { if (it.isBlank()) "" else "$it/" }
            val manifest = parseEpubManifest(opf)
            val cover = findEpubCover(opf, manifest)
                ?.takeIf { it.isRasterCover }
                ?.let { item ->
                    val coverPath = normalizeZipPath(basePath + item.href)
                    zip.readBytesOrNull(coverPath)?.let { bytes ->
                        EmbeddedCover(bytes = bytes, extension = item.rasterExtension ?: "png")
                    }
                }

            return ExtractedBookMetadata(
                title = opf.tagText("title"),
                author = opf.tagText("creator"),
                description = opf.tagInnerContent("description"),
                seriesName = opf.metaContent("calibre:series"),
                seriesIndex = opf.metaContent("calibre:series_index")?.toDoubleOrNull(),
                cover = cover
            )
        }
    }

    private fun parseEpubRootfilePath(containerXml: String): String? {
        return Regex("""<rootfile\b[^>]*\bfull-path=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(containerXml)
            ?.groupValues
            ?.get(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseEpubManifest(opf: String): List<EpubManifestItem> {
        return Regex("""<item\s+[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(opf)
            .mapNotNull { match ->
                val item = match.value
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isBlank() || href.isBlank()) {
                    null
                } else {
                    EpubManifestItem(
                        id = id,
                        href = href,
                        mediaType = item.attr("media-type"),
                        properties = item.attr("properties")
                    )
                }
            }
            .toList()
    }

    private fun findEpubCover(opf: String, manifest: List<EpubManifestItem>): EpubManifestItem? {
        val coverId = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(opf)
            .firstOrNull { it.value.attr("name").equals("cover", ignoreCase = true) }
            ?.value
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
        return manifest.firstOrNull { it.id == coverId }
            ?: manifest.firstOrNull { it.properties.split(Regex("\\s+")).any { property -> property == "cover-image" } }
            ?: manifest.firstOrNull { it.isRasterCover && it.href.contains("cover", ignoreCase = true) }
            ?: manifest.firstOrNull { it.isRasterCover && it.href.contains("front", ignoreCase = true) }
    }

    private fun parseHtmlTitle(file: File): String? {
        return runCatching {
            val head = file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                buildString {
                    var remaining = 64 * 1024
                    val buffer = CharArray(2048)
                    while (remaining > 0) {
                        val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                        if (read <= 0) break
                        append(buffer, 0, read)
                        remaining -= read
                        if (contains("</title>", ignoreCase = true)) break
                    }
                }
            }
            head.tagText("title")
        }.getOrNull()
    }

    private fun parseHtmlDescription(file: File): String? {
        return runCatching {
            val head = file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                buildString {
                    var remaining = 64 * 1024
                    val buffer = CharArray(2048)
                    while (remaining > 0) {
                        val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                        if (read <= 0) break
                        append(buffer, 0, read)
                        remaining -= read
                        if (contains("</head>", ignoreCase = true)) break
                    }
                }
            }
            Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
                .findAll(head)
                .firstOrNull { meta ->
                    val name = meta.value.attr("name")
                    val property = meta.value.attr("property")
                    name.equals("description", ignoreCase = true) ||
                        property.equals("og:description", ignoreCase = true)
                }
                ?.value
                ?.attr("content")
                ?.decodeEntities()
        }.getOrNull()
    }

    private fun saveEmbeddedCover(book: BookItem, cover: EmbeddedCover?): String? {
        if (cover == null || cover.bytes.isEmpty()) return null
        val extension = cover.extension.takeIf { it in rasterCoverExtensions } ?: return null
        return runCatching {
            deleteExistingCoverFiles(book)
            val target = coverCacheFile(book, extension)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeBytes(cover.bytes)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            target.absolutePath
        }.getOrNull()
    }

    private fun renderReaderSurfaceCover(book: BookItem, file: File): String? {
        if (book.type == FileType.PDF && !DesktopPdfium.isAvailable()) return null
        if (book.type != FileType.PDF && !DesktopComicArchive.canLoad(book.type)) return null
        return runCatching {
            val document = if (book.type == FileType.PDF) {
                DesktopPdfium.load(file)
            } else {
                DesktopPdfium.loadComic(file, book.type)
            }
            try {
                if (document.pageCount <= 0) {
                    null
                } else {
                    val firstPage = document.pageSizes.first()
                    val scale = 800f / firstPage.height.coerceAtLeast(1f)
                    val image = DesktopPdfium.renderPageBufferedImage(
                        document = document,
                        pageIndex = 0,
                        scale = scale,
                        renderAnnotations = false
                    )
                    saveCoverImage(book, image)
                }
            } finally {
                document.close()
            }
        }.getOrNull()
    }

    private fun saveGeneratedCover(book: BookItem): String? {
        if (book.type !in generatedCoverTypes) return null
        return saveCoverImage(book, generatedCoverImage(book))
    }

    private fun saveCoverImage(book: BookItem, image: BufferedImage): String? {
        return runCatching {
            deleteExistingCoverFiles(book)
            val target = coverCacheFile(book, "png")
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            ImageIO.write(image, "png", temp)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            target.absolutePath
        }.getOrNull()
    }

    private fun generatedCoverImage(book: BookItem): BufferedImage {
        val width = 480
        val height = 720
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val base = coverColor(book.type)
        val title = book.title?.takeIf { it.isNotBlank() }
            ?: book.displayName.substringBeforeLast('.', missingDelimiterValue = book.displayName)
        val author = book.author?.takeIf { it.isNotBlank() }

        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.paint = GradientPaint(0f, 0f, base.brighter(), 0f, height.toFloat(), base.darker())
            g.fillRect(0, 0, width, height)

            g.color = Color(255, 255, 255, 36)
            g.fillRoundRect(42, 42, width - 84, height - 84, 36, 36)
            g.color = Color(255, 255, 255, 210)
            g.font = Font("SansSerif", Font.BOLD, 34)
            g.drawString(book.type.name, 64, 104)

            g.font = Font("Serif", Font.BOLD, 48)
            val titleLines = wrapText(title, g.fontMetrics, width - 128, maxLines = 6)
            var y = 250
            titleLines.forEach { line ->
                g.drawString(line, 64, y)
                y += 58
            }

            g.font = Font("SansSerif", Font.PLAIN, 28)
            val footer = author ?: book.displayName
            val footerLines = wrapText(footer, g.fontMetrics, width - 128, maxLines = 2)
            val footerStart = max(y + 40, height - 150)
            footerLines.forEachIndexed { index, line ->
                g.drawString(line, 64, footerStart + index * 34)
            }
        } finally {
            g.dispose()
        }
        return image
    }

    private fun wrapText(text: String, metrics: java.awt.FontMetrics, maxWidth: Int, maxLines: Int): List<String> {
        val words = text.replace(Regex("\\s+"), " ").trim().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("Untitled")
        val lines = mutableListOf<String>()
        var current = ""

        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (metrics.stringWidth(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = trimToWidth(word, metrics, maxWidth)
            }
            if (lines.size == maxLines) break
        }
        if (lines.size < maxLines && current.isNotBlank()) lines += current
        return lines.take(maxLines)
    }

    private fun trimToWidth(text: String, metrics: java.awt.FontMetrics, maxWidth: Int): String {
        if (metrics.stringWidth(text) <= maxWidth) return text
        var candidate = text
        while (candidate.length > 1 && metrics.stringWidth("$candidate...") > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return "$candidate..."
    }

    private fun coverColor(type: FileType): Color {
        return when (type) {
            FileType.PDF -> Color(156, 65, 70)
            FileType.EPUB -> Color(0, 108, 76)
            FileType.CBZ, FileType.CBR, FileType.CB7 -> Color(112, 93, 73)
            FileType.MD -> Color(83, 101, 120)
            FileType.HTML -> Color(122, 87, 42)
            FileType.TXT -> Color(74, 92, 112)
            else -> Color(93, 107, 130)
        }
    }

    private fun coverCacheFile(book: BookItem, extension: String): File {
        val key = book.path?.takeIf { it.isNotBlank() } ?: book.id
        val hash = Integer.toUnsignedString(key.hashCode())
        return File(coverCacheDir(), "cover_$hash.$extension")
    }

    private fun deleteExistingCoverFiles(book: BookItem) {
        val key = book.path?.takeIf { it.isNotBlank() } ?: book.id
        val hash = Integer.toUnsignedString(key.hashCode())
        coverCacheDir().listFiles()
            ?.filter { it.isFile && it.name.startsWith("cover_$hash.") }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun coverCacheDir(): File {
        val overridePath = System.getProperty("reader.cover.cache.dir")
            ?: System.getenv("READER_COVER_CACHE_DIR")
        if (!overridePath.isNullOrBlank()) {
            return File(overridePath).apply { mkdirs() }
        }
        return File(desktopUserCacheRoot(), "cover_cache").apply { mkdirs() }
    }

    private fun ZipFile.readTextOrNull(path: String): String? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun ZipFile.readBytesOrNull(path: String): ByteArray? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).use { it.readBytes() }
    }

    private fun String.attr(name: String): String {
        return Regex("""\b$name=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    }

    private fun String.tagText(tag: String): String {
        return Regex(
            "<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.replace(Regex("<[^>]+>"), " ")
            ?.decodeEntities()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private fun String.tagInnerContent(tag: String): String {
        return Regex(
            "<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.removeSurrounding("<![CDATA[", "]]>")
            ?.decodeEntities()
            ?.trim()
            .orEmpty()
    }

    private fun String.metaContent(name: String): String? {
        return Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(this)
            .firstOrNull { it.value.attr("name").equals(name, ignoreCase = true) }
            ?.value
            ?.attr("content")
            ?.decodeEntities()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.decodeEntities(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
    }

    private fun normalizeZipPath(path: String): String {
        val parts = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
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

    private fun sanitizeDescription(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    }

    private fun BookItem.shouldApplyExtractedTitle(file: File): Boolean {
        val current = title?.trim()
        val fallback = file.nameWithoutExtension
        return current.isNullOrBlank() || current == fallback || current == originalTitle?.trim()
    }

    private fun BookItem.shouldApplyExtractedText(current: String?, original: String?): Boolean {
        val normalized = current?.trim()
        return normalized.isNullOrBlank() || normalized == original?.trim()
    }

    private val EpubManifestItem.isRasterCover: Boolean
        get() = rasterExtension != null

    private val EpubManifestItem.rasterExtension: String?
        get() {
            val extension = href.substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            if (extension in rasterCoverExtensions) return extension
            return when {
                mediaType.equals("image/jpeg", ignoreCase = true) -> "jpg"
                mediaType.equals("image/png", ignoreCase = true) -> "png"
                mediaType.equals("image/gif", ignoreCase = true) -> "gif"
                mediaType.equals("image/webp", ignoreCase = true) -> "webp"
                mediaType.equals("image/bmp", ignoreCase = true) -> "bmp"
                else -> null
            }
        }

    private data class ExtractedBookMetadata(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val seriesName: String? = null,
        val seriesIndex: Double? = null,
        val cover: EmbeddedCover? = null
    )

    private data class EmbeddedCover(
        val bytes: ByteArray,
        val extension: String
    )

    private data class EpubManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )
}
