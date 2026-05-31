package com.aryan.reader.desktop

import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal object DesktopComicArchive {
    private val comicTypes = SharedFileCapabilities.comicArchiveTypes
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun canLoad(type: FileType): Boolean = type in comicTypes

    fun load(file: File, type: FileType): DesktopComicDocument {
        require(file.isFile) { "Missing comic archive: ${file.absolutePath}" }
        require(canLoad(type)) { "${type.name} is not a comic archive type." }
        return when (type) {
            FileType.CBZ -> loadZip(file)
            FileType.CBR -> loadRar(file)
            FileType.CB7 -> loadSevenZ(file)
            FileType.CBT -> loadTar(file)
            else -> error("${type.name} is not a comic archive type.")
        }
    }

    fun loadOpdsStream(
        path: String,
        title: String,
        reference: OpdsStreamReference,
        catalog: OpdsCatalog?
    ): DesktopComicDocument {
        val cacheDir = File(
            DesktopLibraryDatabase.defaultDatabaseFile().parentFile,
            "opds_stream_cache/${reference.id.hashCode()}"
        ).apply { mkdirs() }
        val pages = (0 until reference.count).map { pageIndex ->
            DesktopComicPage(
                name = "opds_${pageIndex + 1}.jpg",
                width = 800,
                height = 1200,
                source = OpdsStreamComicPageSource(
                    pageIndex = pageIndex,
                    urlTemplate = reference.urlTemplate,
                    catalog = catalog,
                    cacheDir = cacheDir
                )
            )
        }
        return DesktopComicDocument(
            path = path,
            title = title,
            pages = pages,
            closeAction = {}
        )
    }

    private fun loadZip(file: File): DesktopComicDocument {
        val zip = ZipFile(file)
        return try {
            val pages = zip.entries()
                .asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.isComicImageName() }
                .sortedBy { entry -> entry.name.comicSortKey() }
                .mapNotNull { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val size = decodeImageSize(bytes) ?: return@mapNotNull null
                    DesktopComicPage(
                        name = entry.name,
                        width = size.first,
                        height = size.second,
                        source = ZipComicPageSource(zip, entry.name)
                    )
                }
                .toList()
            require(pages.isNotEmpty()) { "No readable image pages were found in ${file.name}." }
            DesktopComicDocument(
                path = file.absolutePath,
                title = file.nameWithoutExtension,
                pages = pages,
                closeAction = { zip.close() }
            )
        } catch (throwable: Throwable) {
            runCatching { zip.close() }
            throw throwable
        }
    }

    private fun loadRar(file: File): DesktopComicDocument {
        val nativeResult = runCatching { loadRarWithNativeLibarchive(file) }
        if (nativeResult.isSuccess) return nativeResult.getOrThrow()

        return runCatching { loadWithArchiveCommand(file) }
            .getOrElse { commandError ->
                error(
                    "Could not open CBR with libarchive. " +
                        "Bundle libarchive for this desktop platform, or keep tar/bsdtar available on PATH. " +
                        "Native: ${nativeResult.exceptionOrNull()?.shortMessage().orEmpty()} " +
                        "Command: ${commandError.shortMessage()}"
                )
            }
    }

    private fun loadRarWithNativeLibarchive(file: File): DesktopComicDocument {
        val tempDir = Files.createTempDirectory("reader-comic-").toFile()
        return try {
            val extracted = DesktopLibarchive.extractImagePages(file, tempDir, imageExtensions)
            documentFromExtracted(file, extracted, tempDir)
        } catch (throwable: Throwable) {
            runCatching { tempDir.deleteRecursively() }
            throw throwable
        }
    }

    private fun loadSevenZ(file: File): DesktopComicDocument {
        val tempDir = Files.createTempDirectory("reader-comic-").toFile()
        val commonsResult = runCatching {
            val extracted = mutableListOf<ExtractedComicPage>()
            @Suppress("DEPRECATION")
            SevenZFile(file).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    val name = entry.name.orEmpty()
                    if (!entry.isDirectory && name.isComicImageName()) {
                        val target = File(tempDir, "page_${extracted.size}.${name.imageExtension()}")
                        target.outputStream().use { output ->
                            archive.copyCurrentEntryTo(output)
                        }
                        extracted += ExtractedComicPage(name = name, file = target)
                    }
                    entry = archive.nextEntry
                }
            }
            documentFromExtracted(file, extracted, tempDir)
        }
        if (commonsResult.isSuccess) return commonsResult.getOrThrow()

        runCatching { tempDir.deleteRecursively() }
        return runCatching { loadWithArchiveCommand(file) }
            .getOrElse { commandError ->
                error(
                    "Could not open CB7 with Commons Compress or system tar/bsdtar. " +
                        "Commons: ${commonsResult.exceptionOrNull()?.shortMessage().orEmpty()} " +
                        "Command: ${commandError.shortMessage()}"
                )
            }
    }

    private fun loadTar(file: File): DesktopComicDocument {
        val tempDir = Files.createTempDirectory("reader-comic-").toFile()
        return try {
            val extracted = mutableListOf<ExtractedComicPage>()
            TarArchiveInputStream(file.inputStream().buffered()).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    val name = entry.name.orEmpty()
                    if (!entry.isDirectory && name.isComicImageName()) {
                        val target = File(tempDir, "page_${extracted.size}.${name.imageExtension()}")
                        target.outputStream().use { output ->
                            archive.copyTo(output)
                        }
                        extracted += ExtractedComicPage(name = name, file = target)
                    }
                    entry = archive.nextEntry
                }
            }
            documentFromExtracted(file, extracted, tempDir)
        } catch (throwable: Throwable) {
            runCatching { tempDir.deleteRecursively() }
            throw throwable
        }
    }

    private fun loadWithArchiveCommand(file: File): DesktopComicDocument {
        val tempDir = Files.createTempDirectory("reader-comic-").toFile()
        return try {
            val extracted = DesktopArchiveCommand.extractImagePages(file, tempDir, imageExtensions)
            documentFromExtracted(file, extracted, tempDir)
        } catch (throwable: Throwable) {
            runCatching { tempDir.deleteRecursively() }
            throw throwable
        }
    }

    private fun documentFromExtracted(
        file: File,
        extracted: List<ExtractedComicPage>,
        tempDir: File
    ): DesktopComicDocument {
        val pages = extracted
            .sortedBy { page -> page.name.comicSortKey() }
            .mapNotNull { page ->
                val bytes = page.file.readBytes()
                val size = decodeImageSize(bytes) ?: return@mapNotNull null
                DesktopComicPage(
                    name = page.name,
                    width = size.first,
                    height = size.second,
                    source = FileComicPageSource(page.file)
                )
            }
        require(pages.isNotEmpty()) { "No readable image pages were found in ${file.name}." }
        return DesktopComicDocument(
            path = file.absolutePath,
            title = file.nameWithoutExtension,
            pages = pages,
            closeAction = { tempDir.deleteRecursively() }
        )
    }

    private fun SevenZFile.copyCurrentEntryTo(output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer, 0, buffer.size)
            if (read < 0) break
            if (read > 0) output.write(buffer, 0, read)
        }
    }

    private fun decodeImageSize(bytes: ByteArray): Pair<Int, Int>? {
        val image = ByteArrayInputStream(bytes).use { input ->
            ImageIO.read(input)
        } ?: return null
        return image.width.coerceAtLeast(1) to image.height.coerceAtLeast(1)
    }

    internal fun String.isComicImageName(): Boolean {
        return imageExtension() in imageExtensions
    }

    internal fun String.imageExtension(): String {
        return substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', missingDelimiterValue = "img")
            .lowercase()
            .takeIf { it in imageExtensions }
            ?: "img"
    }

    internal fun String.comicSortKey(): String {
        return replace('\\', '/').lowercase()
    }

    internal fun Throwable.shortMessage(): String {
        return message
            ?.replace(Regex("\\s+"), " ")
            ?.take(240)
            ?.ifBlank { null }
            ?: javaClass.simpleName
    }

    internal data class ExtractedComicPage(
        val name: String,
        val file: File
    )
}

internal class DesktopComicDocument(
    val path: String,
    val title: String,
    pages: List<DesktopComicPage>,
    private val closeAction: () -> Unit
) {
    private val pages = pages.toList()

    val pageCount: Int = pages.size
    val pageSizes: List<DesktopPdfPageSize> = pages.map { page ->
        DesktopPdfPageSize(page.width.toFloat(), page.height.toFloat())
    }

    fun renderPageBufferedImage(pageIndex: Int, scale: Float): BufferedImage {
        val page = pages.getOrNull(pageIndex) ?: error("Invalid comic page index $pageIndex.")
        val sourceImage = ByteArrayInputStream(page.source.readBytes()).use { input ->
            ImageIO.read(input)
        } ?: error("Could not decode comic page ${pageIndex + 1}.")
        val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val sourceWidth = sourceImage.width.coerceAtLeast(1)
        val sourceHeight = sourceImage.height.coerceAtLeast(1)
        val width = (sourceWidth * safeScale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * safeScale).roundToInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(sourceImage, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
            sourceImage.flush()
        }
        return image
    }

    fun close() {
        closeAction()
    }
}

internal data class DesktopComicPage(
    val name: String,
    val width: Int,
    val height: Int,
    val source: ComicPageSource
)

internal interface ComicPageSource {
    fun readBytes(): ByteArray
}

private class ZipComicPageSource(
    private val zip: ZipFile,
    private val entryName: String
) : ComicPageSource {
    override fun readBytes(): ByteArray {
        val entry = zip.getEntry(entryName) ?: error("Missing comic page entry: $entryName")
        return zip.getInputStream(entry).use { it.readBytes() }
    }
}

private class FileComicPageSource(
    private val file: File
) : ComicPageSource {
    override fun readBytes(): ByteArray = file.readBytes()
}

private class OpdsStreamComicPageSource(
    private val pageIndex: Int,
    private val urlTemplate: String,
    private val catalog: OpdsCatalog?,
    private val cacheDir: File
) : ComicPageSource {
    override fun readBytes(): ByteArray {
        val cachedFile = File(cacheDir, "page_$pageIndex.jpg")
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            return cachedFile.readBytes()
        }

        return runCatching {
            val bytes = DesktopOpdsHttp.fetchBytes(streamPageUrl(), catalog)
            if (bytes.isNotEmpty()) {
                cachedFile.writeBytes(bytes)
                bytes
            } else {
                error("Empty OPDS stream page response.")
            }
        }.getOrElse {
            errorPageBytes()
        }
    }

    private fun streamPageUrl(): String {
        return effectiveTemplate()
            .replace("{pageNumber}", pageIndex.toString())
            .replace("{page}", pageIndex.toString())
            .replace("{maxWidth}", "1600")
            .replace("{maxHeight}", "2400")
    }

    private fun effectiveTemplate(): String {
        val catalogUrl = catalog?.url ?: return urlTemplate
        if (!urlTemplate.startsWith("http", ignoreCase = true)) return urlTemplate
        return runCatching {
            val oldUrl = URL(urlTemplate)
            val newUrl = URL(catalogUrl)
            val oldBase = "${oldUrl.protocol}://${oldUrl.authority}"
            val newBase = "${newUrl.protocol}://${newUrl.authority}"
            urlTemplate.replace(oldBase, newBase)
        }.getOrDefault(urlTemplate)
    }

    private fun errorPageBytes(): ByteArray {
        val image = BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.DARK_GRAY
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color.WHITE
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 36)
            val text = "Page unavailable"
            val metrics = graphics.fontMetrics
            graphics.drawString(text, (image.width - metrics.stringWidth(text)) / 2, image.height / 2)
        } finally {
            graphics.dispose()
        }
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "jpg", output)
            output.toByteArray()
        }
    }
}

private object DesktopArchiveCommand {
    private const val EXTRACT_TIMEOUT_SECONDS = 60L

    fun extractImagePages(
        file: File,
        tempDir: File,
        imageExtensions: Set<String>
    ): List<DesktopComicArchive.ExtractedComicPage> {
        val command = resolveCommand()
            ?: error("No tar/bsdtar command was found on PATH.")
        val names = listArchiveEntries(command, file)
            .filter { name ->
                val extension = name.substringBefore('?')
                    .substringBefore('#')
                    .substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase()
                extension in imageExtensions
            }
            .sortedBy { name -> with(DesktopComicArchive) { name.comicSortKey() } }
        require(names.isNotEmpty()) { "No readable image pages were found in ${file.name}." }
        return names.mapIndexed { index, name ->
            val extension = name.substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('.', missingDelimiterValue = "img")
                .lowercase()
                .ifBlank { "img" }
            val target = File(tempDir, "page_$index.$extension")
            extractEntry(command, file, name, target)
            DesktopComicArchive.ExtractedComicPage(name = name, file = target)
        }
    }

    private fun resolveCommand(): String? {
        val override = System.getProperty("reader.archive.command")
            ?: System.getenv("READER_ARCHIVE_COMMAND")
        return listOfNotNull(override?.takeIf { it.isNotBlank() }, "bsdtar", "tar")
            .firstOrNull(::isCommandAvailable)
    }

    private fun isCommandAvailable(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder(command, "--version")
                .redirectErrorStream(true)
                .start()
            process.inputStream.use { it.readBytes() }
            process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun listArchiveEntries(command: String, file: File): List<String> {
        val process = ProcessBuilder(command, "-tf", file.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val finished = process.waitFor(EXTRACT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("$command timed out while listing ${file.name}.")
        }
        if (process.exitValue() != 0) {
            error(output.ifBlank { "$command could not list ${file.name}." })
        }
        return output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun extractEntry(command: String, file: File, entryName: String, target: File) {
        val process = ProcessBuilder(command, "-xOf", file.absolutePath, entryName)
            .start()
        val errorText = StringBuilder()
        val errorThread = Thread {
            process.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                errorText.append(reader.readText())
            }
        }.apply {
            isDaemon = true
            start()
        }
        process.inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val finished = process.waitFor(EXTRACT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("$command timed out while extracting $entryName.")
        }
        errorThread.join(1000)
        if (process.exitValue() != 0) {
            error(errorText.toString().ifBlank { "$command could not extract $entryName." })
        }
    }
}

private object DesktopLibarchive {
    private const val ARCHIVE_OK = 0
    private const val ARCHIVE_EOF = 1
    private const val ARCHIVE_ENTRY_DIRECTORY = 0x4000
    private const val BUFFER_SIZE = 128 * 1024

    fun extractImagePages(
        file: File,
        tempDir: File,
        imageExtensions: Set<String>
    ): List<DesktopComicArchive.ExtractedComicPage> {
        val archive = api.archive_read_new()
            ?: error("libarchive could not allocate a reader.")
        try {
            checkArchive(api.archive_read_support_filter_all(archive), archive, "enable archive filters")
            checkArchive(api.archive_read_support_format_all(archive), archive, "enable archive formats")
            checkArchive(
                api.archive_read_open_filename(archive, file.absolutePath, BUFFER_SIZE.toLong()),
                archive,
                "open ${file.name}"
            )

            val pages = mutableListOf<DesktopComicArchive.ExtractedComicPage>()
            val entryRef = PointerByReference()
            while (true) {
                when (val status = api.archive_read_next_header(archive, entryRef)) {
                    ARCHIVE_OK -> Unit
                    ARCHIVE_EOF -> break
                    else -> checkArchive(status, archive, "read archive header")
                }

                val entry = entryRef.value ?: continue
                val name = api.archive_entry_pathname_utf8(entry)
                    ?: api.archive_entry_pathname(entry)
                    ?: continue
                val extension = name.substringBefore('?')
                    .substringBefore('#')
                    .substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase()
                if (api.archive_entry_filetype(entry) == ARCHIVE_ENTRY_DIRECTORY || extension !in imageExtensions) {
                    api.archive_read_data_skip(archive)
                    continue
                }

                val target = File(tempDir, "page_${pages.size}.${extension.ifBlank { "img" }}")
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = api.archive_read_data(archive, buffer, buffer.size.toLong())
                        when {
                            read > 0 -> output.write(buffer, 0, read.toInt())
                            read == 0L -> break
                            else -> checkArchive(read.toInt(), archive, "extract $name")
                        }
                    }
                }
                pages += DesktopComicArchive.ExtractedComicPage(name = name, file = target)
            }
            return pages.sortedBy { page -> with(DesktopComicArchive) { page.name.comicSortKey() } }
        } finally {
            api.archive_read_free(archive)
        }
    }

    private val api: LibarchiveLibrary by lazy {
        val overridePath = System.getProperty("reader.libarchive.path")
            ?: System.getenv("READER_LIBARCHIVE_PATH")
        val candidates = if (overridePath.isNullOrBlank()) {
            listOf("archive", "libarchive", "libarchive-13", "libarchive-14")
        } else {
            listOf(File(overridePath).absolutePath, overridePath)
        }

        candidates.firstNotNullOfOrNull { candidate ->
            runCatching { Native.load(candidate, LibarchiveLibrary::class.java) }
                .getOrNull()
        } ?: error(
            "Native libarchive was not found. Set READER_LIBARCHIVE_PATH/reader.libarchive.path " +
                "or bundle libarchive for this platform."
        )
    }

    private fun checkArchive(status: Int, archive: Pointer, action: String) {
        if (status >= ARCHIVE_OK) return
        val message = api.archive_error_string(archive).orEmpty()
        error("libarchive could not $action: ${message.ifBlank { "error code $status" }}")
    }

    @Suppress("FunctionName")
    private interface LibarchiveLibrary : Library {
        fun archive_read_new(): Pointer?
        fun archive_read_support_filter_all(archive: Pointer): Int
        fun archive_read_support_format_all(archive: Pointer): Int
        fun archive_read_open_filename(archive: Pointer, fileName: String, blockSize: Long): Int
        fun archive_read_next_header(archive: Pointer, entry: PointerByReference): Int
        fun archive_read_data(archive: Pointer, buffer: ByteArray, size: Long): Long
        fun archive_read_data_skip(archive: Pointer): Int
        fun archive_read_free(archive: Pointer): Int
        fun archive_error_string(archive: Pointer): String?
        fun archive_entry_pathname_utf8(entry: Pointer): String?
        fun archive_entry_pathname(entry: Pointer): String?
        fun archive_entry_filetype(entry: Pointer): Int
    }
}
