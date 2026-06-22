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
import com.aryan.reader.FileType
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import org.zwobble.mammoth.DocumentConverter
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile

class SingleFileImporter(private val context: Context) {

    private val jsonSerializer = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val MAX_DOCX_ARCHIVE_BYTES = 64L * 1024L * 1024L
        private const val MAX_DOCX_XML_BYTES = 48L * 1024L * 1024L
        private const val MAX_HTML_CHAPTER_CHARS = 1_000_000
        private const val MAX_HTML_BUFFERED_LINE_CHARS = 128_000
        private const val MAX_HTML_HEAD_SCAN_CHARS = 256_000
        private const val MAX_HTML_INLINE_CSS_CHARS = 256_000
        private const val MAX_SINGLE_FILE_PLAIN_TEXT_CHARS = 256_000
        private const val MAX_SINGLE_FILE_METADATA_BYTES = 2L * 1024L * 1024L
        private const val BOOK_METADATA_FILE = "book_metadata.json"
        private const val TXT_PREFORMATTED_METADATA_FILE = "book_metadata_txt_preformatted_v3.json"
        private const val PAGE_BREAK_MARKER = "<page-break></page-break>"
        private const val HTML_IMPORT_DEBUG_TAG = "HtmlImportDebug"
        private const val TXT_FORMAT_TRACE_TAG = "TxtFormatTrace"
        private const val TXT_PREFORMATTED_CLASS = "reader-txt-preformatted"
        private const val TXT_PREFORMATTED_INLINE_STYLE =
            "white-space: pre-wrap !important; text-indent: 0 !important;"

        private val scriptTextPreservingTags = setOf("pre", "code", "kbd", "samp", "textarea")
        private val leakedAdScriptMarkers = listOf(
            "function productezoicads",
            "google_reactive_ads_global_state",
            "ezoic_pub_ad_placeholder",
            "ezdisableads",
            "ezoictestactive",
            "window.__ez",
            "ezstandalone",
            "ezosuibasgeneris",
            "data-ezscrex",
            "ezoicsearchable",
            "ezdomain",
            "var soc_app_id",
            "var did ="
        )
        private val leakedScriptTextSignals = listOf(
            "window.",
            "document.",
            "function ",
            "function(",
            "var ",
            "const ",
            "let ",
            "typeof ",
            "object.assign",
            ".push(",
            "return;"
        )
    }

    private val htmlSafelist = Safelist.relaxed()
        .addTags("article", "aside", "details", "div", "figcaption", "figure", "footer", "header", "main", "section", "summary")
        .addAttributes(":all", "class", "dir", "id", "lang", "title")
        .addAttributes("a", "name", "target")
        .addProtocols("a", "href", "http", "https", "mailto", "tel", "#")
        .addProtocols("img", "src", "http", "https", "data", "file", "content")

    private val htmlOutputSettings = Document.OutputSettings().prettyPrint(false)

    private fun metadataFile(extractionDir: File): File = File(extractionDir, BOOK_METADATA_FILE)
    private fun txtMetadataFile(extractionDir: File): File = File(extractionDir, TXT_PREFORMATTED_METADATA_FILE)

    private fun String.txtFormatTracePreview(maxLength: Int = 220): String {
        return replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
            .replace("\"", "\\\"")
    }

    private fun File.htmlDebugFileList(): String {
        return listFiles()?.joinToString { file -> "${file.name}:${file.length()}" } ?: "<unreadable>"
    }

    private fun EpubBook.lightweightSingleFileCache(): EpubBook {
        val cacheChapters = chapters.map { chapter ->
            chapter.copy(
                plainTextContent = "",
                htmlContent = ""
            )
        }
        return copy(
            coverImage = null,
            chapters = cacheChapters,
            chaptersForPagination = cacheChapters
        )
    }

    private fun readCachedSingleFileBook(metadataFile: File, extractionDir: File, tag: String): EpubBook? {
        if (!metadataFile.exists()) {
            Timber.tag(HTML_IMPORT_DEBUG_TAG).d("cache_miss_no_metadata tag=$tag dir=${extractionDir.absolutePath}")
            return null
        }
        val extractionFilesSummary = extractionDir.htmlDebugFileList()
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
            "cache_metadata_present tag=$tag dir=${extractionDir.absolutePath} " +
                "metadataBytes=${metadataFile.length()} files=$extractionFilesSummary"
        )
        if (metadataFile.length() > MAX_SINGLE_FILE_METADATA_BYTES) {
            Timber.w(
                "Ignoring oversized $tag metadata cache (${metadataFile.length()} bytes). " +
                    "The file will be reparsed with lightweight metadata."
            )
            Timber.tag(HTML_IMPORT_DEBUG_TAG).w("cache_oversized tag=$tag metadataBytes=${metadataFile.length()}")
            runCatching { metadataFile.delete() }
            return null
        }

        return try {
            val decodedBook = jsonSerializer.decodeFromString<EpubBook>(metadataFile.readText())
            val cacheChapters = decodedBook.chapters.map { it.copy(htmlContent = "") }
            decodedBook.copy(
                chapters = cacheChapters,
                chaptersForPagination = cacheChapters,
                extractionBasePath = extractionDir.absolutePath
            ).also { book ->
                Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
                    "cache_decoded tag=$tag chapters=${book.chapters.size} readable=${book.hasReadableExtractedContent()} " +
                        "extractionBasePath=${book.extractionBasePath}"
                )
            }.takeIf { it.hasReadableExtractedContent() }
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Failed to load cached $tag metadata without exhausting memory")
            Timber.tag(HTML_IMPORT_DEBUG_TAG).e(e, "cache_decode_oom tag=$tag")
            runCatching { metadataFile.delete() }
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load cached $tag, parsing again")
            Timber.tag(HTML_IMPORT_DEBUG_TAG).e(e, "cache_decode_failed tag=$tag")
            null
        }
    }

    private fun writeSingleFileMetadata(metadataFile: File, book: EpubBook, tag: String) {
        try {
            metadataFile.writeText(jsonSerializer.encodeToString(book.lightweightSingleFileCache()))
            Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
                "cache_write tag=$tag chapters=${book.chapters.size} metadataBytes=${metadataFile.length()} " +
                    "extractionBasePath=${book.extractionBasePath}"
            )
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Failed to cache lightweight $tag metadata without exhausting memory")
            Timber.tag(HTML_IMPORT_DEBUG_TAG).e(e, "cache_write_oom tag=$tag")
            runCatching { metadataFile.delete() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache $tag metadata")
            Timber.tag(HTML_IMPORT_DEBUG_TAG).e(e, "cache_write_failed tag=$tag")
        }
    }

    suspend fun importSingleFile(
        inputStream: InputStream,
        type: FileType,
        originalBookNameHint: String,
        bookId: String,
        parseContent: Boolean = true
    ): EpubBook {

        Timber.tag(HTML_IMPORT_DEBUG_TAG).d("import_start type=$type bookId=$bookId name=$originalBookNameHint parseContent=$parseContent")
        val lowerHint = originalBookNameHint.lowercase()
        val isCsv = lowerHint.endsWith(".csv") || lowerHint.endsWith(".tsv") ||
            lowerHint.endsWith(".csv.txt") || lowerHint.endsWith(".tsv.txt")
        val isCodeOrData = com.aryan.reader.isCodeOrDataFileName(originalBookNameHint)

        if (type == FileType.HTML && (isCsv || isCodeOrData)) {
            return parseDynamicContentToHtml(inputStream, originalBookNameHint, bookId, parseContent, isCsv)
        }

        return when (type) {
            FileType.MD -> parseMarkdown(inputStream, originalBookNameHint, bookId, parseContent)
            FileType.TXT -> parsePlainText(inputStream, originalBookNameHint, bookId, parseContent)
            FileType.HTML -> parseHtml(inputStream, originalBookNameHint, bookId, parseContent)
            FileType.DOCX -> parseDocx(inputStream, originalBookNameHint, bookId, parseContent)
            else -> parsePlainText(inputStream, originalBookNameHint, bookId, parseContent)
        }
    }

    private suspend fun parseDynamicContentToHtml(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String,
        parseContent: Boolean,
        isCsv: Boolean
    ): EpubBook = withContext(Dispatchers.IO) {

        val tempFile = File(context.cacheDir, "temp_conv_${UUID.randomUUID()}.html")

        try {
            FileOutputStream(tempFile).bufferedWriter().use { writer ->
                writer.write("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>${generatedHtmlTitle(originalBookNameHint)}</title>\n")

                if (isCsv) {
                    writer.write("<style>\ntable { border-collapse: collapse; width: 100%; font-family: sans-serif; }\nth, td { border: 1px solid currentColor; padding: 8px; }\n</style>\n")
                    writer.write("</head>\n<body>\n<div style='overflow-x:auto;'>\n<table>\n")
                } else {
                    writer.write("<style>\npre { padding: 10px; overflow-x: auto; font-family: monospace; white-space: pre-wrap; word-wrap: break-word; }\n</style>\n")
                    writer.write("</head>\n<body>\n<pre><code>\n")
                }

                val delimiter = if (originalBookNameHint.lowercase().let { it.endsWith(".tsv") || it.endsWith(".tsv.txt") }) '\t' else ','

                inputStream.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (isCsv) {
                            writer.write("<tr>")
                            val current = StringBuilder()
                            var inQuotes = false

                            for (char in line) {
                                when (char) {
                                    '\"' -> {
                                        inQuotes = !inQuotes
                                    }
                                    delimiter if !inQuotes -> {
                                        val escaped =
                                            current.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                                        writer.write("<td>$escaped</td>")
                                        current.clear()
                                    }
                                    else -> {
                                        current.append(char)
                                    }
                                }
                            }
                            val escapedFinal = current.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            writer.write("<td>$escapedFinal</td></tr>\n")

                        } else {
                            // Plain code/log text escaping
                            val escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            writer.write("$escaped\n")
                        }
                        line = reader.readLine()
                    }
                }

                if (isCsv) {
                    writer.write("</table>\n</div>\n</body>\n</html>")
                } else {
                    writer.write("</code></pre>\n</body>\n</html>")
                }
            }

            tempFile.inputStream().use { tempStream ->
                return@withContext parseHtml(tempStream, originalBookNameHint, bookId, parseContent)
            }

        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun parseMarkdown(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String,
        parseContent: Boolean
    ): EpubBook = withContext(Dispatchers.IO) {
        if (!parseContent) {
            return@withContext EpubBook(
                fileName = originalBookNameHint,
                title = originalBookNameHint.substringBeforeLast("."),
                author = "Unknown",
                language = "en",
                coverImage = null,
                chapters = emptyList(),
                chaptersForPagination = emptyList(),
                images = emptyList(),
                pageList = emptyList(),
                extractionBasePath = "",
                css = emptyMap()
            )
        }

        val extractionDir = ImportedFileCache.ensureActiveBookDir(context, bookId)
        val metadataFile = metadataFile(extractionDir)

        readCachedSingleFileBook(metadataFile, extractionDir, "MD")?.let { cachedBook ->
            Timber.tag("FileOpenPerf").d("[MD] Loaded from cache instantly | bookId=$bookId")
            return@withContext cachedBook
        }
        ImportedFileCache.resetActiveBookDir(context, bookId)

        val parseStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[MD] parseMarkdown START | file=$originalBookNameHint")
        Timber.d("Parsing Markdown with Page-Level Chaptering: $originalBookNameHint")
        val title = originalBookNameHint.substringBeforeLast(".")

        // Read the full Markdown content
        val markdownContent = inputStream.bufferedReader().use { it.readText() }

        Timber.tag("FileOpenPerf").d("[MD] parseMarkdown: Read ${markdownContent.length} chars | elapsed=${System.currentTimeMillis() - parseStart}ms")

        // Flexmark Setup
        val options = MutableDataSet().apply {
            set(Parser.EXTENSIONS, listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create()
            ))
            set(HtmlRenderer.GENERATE_HEADER_ID, true)
            set(HtmlRenderer.RENDER_HEADER_ID, true)
        }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()

        // Shared CSS
        val style = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
            table { border-collapse: collapse; width: 100%; margin: 1em 0; }
            th, td { border: 1px solid currentColor; padding: 0.5em; text-align: left; }
            blockquote { border-left: 4px solid currentColor; padding-left: 1em; margin-left: 0; opacity: 0.8; }
            pre { overflow-x: auto; background: rgba(127,127,127,0.1); padding: 1em; border-radius: 4px; }
            img { max-width: 100%; height: auto; }
            hr { border: 0; border-top: 1px solid #ccc; margin: 2em 0; }
        """.trimIndent()

        val rawChapters = if (markdownContent.contains("\n\n---\n\n")) {
            markdownContent.split("\n\n---\n\n")
        } else {
            listOf(markdownContent)
        }

        Timber.tag("FileOpenPerf").d("[MD] parseMarkdown: Split into ${rawChapters.size} raw chapters | elapsed=${System.currentTimeMillis() - parseStart}ms")

        val chapters = rawChapters.mapIndexed { index, rawText ->
            async(Dispatchers.Default) {
                if (rawText.isBlank()) return@async null

                val pageNum = index + 1
                val chapterTitle = "Page $pageNum"

                val document = parser.parse(rawText)
                val htmlBody = sanitizeHtmlFragment(renderer.render(document))

                val fileName = "page_$pageNum.html"
                val file = File(extractionDir, fileName)

                val fullHtml = "<!DOCTYPE html>\n<html>\n<head>\n<title>$chapterTitle</title>\n<style>$style</style>\n</head>\n<body>\n$htmlBody\n</body>\n</html>"

                file.writeText(fullHtml)

                val plainText = htmlBody.toPlainTextSummary()

                EpubChapter(
                    chapterId = "${bookId}_$pageNum",
                    absPath = fileName,
                    title = chapterTitle,
                    htmlFilePath = fileName,
                    plainTextContent = plainText.text,
                    plainTextLength = plainText.length,
                    htmlContent = "",
                    depth = 0,
                    isInToc = true
                )
            }
        }.awaitAll().filterNotNull()

        Timber.d("Markdown import complete. Created ${chapters.size} chapters (one per page).")
        Timber.tag("FileOpenPerf").d("[MD] parseMarkdown COMPLETE | chapters=${chapters.size} | totalElapsed=${System.currentTimeMillis() - parseStart}ms")

        val book = EpubBook(
            fileName = originalBookNameHint,
            title = title,
            author = "Unknown",
            language = "en",
            coverImage = null,
            chapters = chapters,
            chaptersForPagination = chapters,
            images = emptyList(),
            pageList = emptyList(),
            extractionBasePath = extractionDir.absolutePath,
            css = emptyMap()
        )

        writeSingleFileMetadata(metadataFile, book, "MD")

        return@withContext book
    }

    private suspend fun parsePlainText(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String,
        parseContent: Boolean
    ): EpubBook = withContext(Dispatchers.IO) {
        if (!parseContent) {
            return@withContext EpubBook(
                fileName = originalBookNameHint,
                title = originalBookNameHint.substringBeforeLast("."),
                author = "Unknown",
                language = "en",
                coverImage = null,
                chapters = emptyList(),
                chaptersForPagination = emptyList(),
                images = emptyList(),
                pageList = emptyList(),
                extractionBasePath = "",
                css = emptyMap()
            )
        }

        val extractionDir = ImportedFileCache.ensureActiveBookDir(context, bookId)
        val txtCacheMetadataFile = txtMetadataFile(extractionDir)
        val legacyMetadataFile = metadataFile(extractionDir)
        Timber.tag(TXT_FORMAT_TRACE_TAG).d(
            "event=android_txt_cache_lookup bookId=$bookId name=${originalBookNameHint.txtFormatTracePreview()} " +
                "metadata=${txtCacheMetadataFile.name} exists=${txtCacheMetadataFile.exists()} legacyExists=${legacyMetadataFile.exists()} " +
                "dir=${extractionDir.absolutePath.txtFormatTracePreview()} files=${extractionDir.htmlDebugFileList().txtFormatTracePreview()}"
        )

        readCachedSingleFileBook(txtCacheMetadataFile, extractionDir, "TXT_PREWRAP_INLINE")?.let { cachedBook ->
            Timber.tag("FileOpenPerf").d("[TXT] Loaded from cache instantly | bookId=$bookId")
            Timber.tag(TXT_FORMAT_TRACE_TAG).d(
                "event=android_txt_cache_hit bookId=$bookId chapters=${cachedBook.chapters.size} " +
                    "firstPath=${cachedBook.chapters.firstOrNull()?.htmlFilePath.orEmpty().txtFormatTracePreview()} " +
                    "plainChars=${cachedBook.chapters.sumOf { it.plainTextLength }}"
            )
            return@withContext cachedBook
        }
        Timber.tag(TXT_FORMAT_TRACE_TAG).d("event=android_txt_cache_miss bookId=$bookId resetDir=true")
        ImportedFileCache.resetActiveBookDir(context, bookId)

        val parseStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[TXT] parsePlainText START | file=$originalBookNameHint")
        Timber.d("Parsing Plain Text with Virtual Chaptering: $originalBookNameHint")
        val title = originalBookNameHint.substringBeforeLast(".")

        val chapters = mutableListOf<EpubChapter>()
        var chapterCounter = 1

        val cssStyle = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
            p { margin-bottom: 1em; text-indent: 0; white-space: pre-wrap; }
            .$TXT_PREFORMATTED_CLASS { white-space: pre-wrap !important; text-indent: 0 !important; }
        """.trimIndent()

        val currentChapterContent = StringBuilder()
        val currentChapterPlainText = StringBuilder()
        val chapterTargetSize = 64 * 1024
        var totalLines = 0
        var blankLines = 0
        var nonBlankLines = 0
        var linesWithRepeatedSpaces = 0
        var linesWithLeadingOrTrailingSpaces = 0
        var maxLineLength = 0
        var firstNonBlankLine: String? = null
        var firstRepeatedSpaceLine: String? = null

        fun flushChapter() {
            if (currentChapterContent.isEmpty()) return

            val fileName = "part_$chapterCounter.html"
            val file = File(extractionDir, fileName)
            val chapterTitle = "Part $chapterCounter"
            val plainText = currentChapterPlainText.toString().trim()

            val fullHtml = "<!DOCTYPE html>\n<html>\n<head>\n<title>$chapterTitle</title>\n<style>$cssStyle</style>\n</head>\n<body>\n$currentChapterContent\n</body>\n</html>"

            FileOutputStream(file).use { it.write(fullHtml.toByteArray()) }
            Timber.tag(TXT_FORMAT_TRACE_TAG).d(
                "event=android_txt_chapter_written bookId=$bookId chapter=$chapterCounter file=$fileName " +
                    "htmlChars=${fullHtml.length} plainChars=${plainText.length} htmlNewlines=${fullHtml.count { it == '\n' }} " +
                    "plainNewlines=${plainText.count { it == '\n' }} containsPreWrap=${fullHtml.contains("white-space: pre-wrap")} " +
                    "containsInlinePreWrap=${fullHtml.contains(TXT_PREFORMATTED_INLINE_STYLE)} " +
                    "htmlPreview=${fullHtml.txtFormatTracePreview()} plainPreview=${plainText.txtFormatTracePreview()}"
            )

            chapters.add(
                EpubChapter(
                    chapterId = "${bookId}_$chapterCounter",
                    absPath = fileName,
                    title = chapterTitle,
                    htmlFilePath = fileName,
                    plainTextContent = plainText,
                    htmlContent = "",
                    depth = 0,
                    isInToc = true
                )
            )

            currentChapterContent.clear()
            currentChapterPlainText.clear()
            chapterCounter++
        }

        fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }

        var inParagraph = false

        inputStream.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine()
                if (line != null) {
                    totalLines++
                    maxLineLength = maxOf(maxLineLength, line.length)
                    if (line.isBlank()) {
                        blankLines++
                    } else {
                        nonBlankLines++
                        if (firstNonBlankLine == null) firstNonBlankLine = line
                        if (line != line.trim()) linesWithLeadingOrTrailingSpaces++
                        if (Regex(" {2,}").containsMatchIn(line)) {
                            linesWithRepeatedSpaces++
                            if (firstRepeatedSpaceLine == null) firstRepeatedSpaceLine = line
                        }
                    }
                }
                if (line == null) {
                    if (inParagraph) {
                        currentChapterContent.append("</p>\n")
                    }
                    break
                }

                if (line.isBlank()) {
                    if (inParagraph) {
                        currentChapterContent.append("</p>\n")
                        currentChapterPlainText.append("\n\n")
                        inParagraph = false
                    }

                    if (currentChapterContent.length >= chapterTargetSize) {
                        flushChapter()
                    }
                } else {
                    if (!inParagraph) {
                        currentChapterContent.append(
                            "<p class=\"$TXT_PREFORMATTED_CLASS\" style=\"$TXT_PREFORMATTED_INLINE_STYLE\">"
                        )
                        inParagraph = true
                    } else {
                        currentChapterContent.append("\n")
                        currentChapterPlainText.append("\n")
                    }
                    currentChapterContent.append(escapeHtml(line))
                    currentChapterPlainText.append(line)

                    if (currentChapterContent.length >= chapterTargetSize * 2) {
                        currentChapterContent.append("</p>\n")
                        flushChapter()
                        inParagraph = false
                    }
                }
            }
        }

        Timber.tag(TXT_FORMAT_TRACE_TAG).d(
            "event=android_txt_lines_scanned bookId=$bookId lines=$totalLines blankLines=$blankLines nonBlankLines=$nonBlankLines " +
                "repeatedSpaceLines=$linesWithRepeatedSpaces edgeSpaceLines=$linesWithLeadingOrTrailingSpaces maxLineLength=$maxLineLength " +
                "firstNonBlank=${firstNonBlankLine.orEmpty().txtFormatTracePreview()} " +
                "firstRepeatedSpace=${firstRepeatedSpaceLine.orEmpty().txtFormatTracePreview()}"
        )

        flushChapter()

        if (chapters.isEmpty()) {
            currentChapterContent.append(
                "<p class=\"$TXT_PREFORMATTED_CLASS\" style=\"$TXT_PREFORMATTED_INLINE_STYLE\">(Empty File)</p>"
            )
            flushChapter()
        }

        Timber.d("Imported TXT split into ${chapters.size} chapters.")

        Timber.tag("FileOpenPerf").d("[TXT] parsePlainText COMPLETE | chapters=${chapters.size} | totalElapsed=${System.currentTimeMillis() - parseStart}ms")
        Timber.tag(TXT_FORMAT_TRACE_TAG).d(
            "event=android_txt_parse_done bookId=$bookId chapters=${chapters.size} " +
                "totalPlainChars=${chapters.sumOf { it.plainTextLength }} extraction=${extractionDir.absolutePath.txtFormatTracePreview()} " +
                "files=${extractionDir.htmlDebugFileList().txtFormatTracePreview()}"
        )

        val book = EpubBook(
            fileName = originalBookNameHint,
            title = title,
            author = "Unknown",
            language = "en",
            coverImage = null,
            chapters = chapters,
            chaptersForPagination = chapters,
            images = emptyList(),
            pageList = emptyList(),
            extractionBasePath = extractionDir.absolutePath,
            css = emptyMap()
        )

        writeSingleFileMetadata(txtCacheMetadataFile, book, "TXT_PREWRAP_INLINE")

        return@withContext book
    }

    private suspend fun parseHtml(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String,
        parseContent: Boolean
    ): EpubBook = withContext(Dispatchers.IO) {
        if (!parseContent) {
            return@withContext EpubBook(
                fileName = originalBookNameHint,
                title = originalBookNameHint.substringBeforeLast("."),
                author = "Unknown",
                language = "en",
                coverImage = null,
                chapters = emptyList(),
                chaptersForPagination = emptyList(),
                images = emptyList(),
                pageList = emptyList(),
                extractionBasePath = "",
                css = emptyMap()
            )
        }

        val extractionDir = ImportedFileCache.ensureActiveBookDir(context, bookId)
        val metadataFile = metadataFile(extractionDir)
        val extractionFilesSummary = extractionDir.htmlDebugFileList()
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
            "html_parse_cache_check bookId=$bookId name=$originalBookNameHint dir=${extractionDir.absolutePath} " +
                "metadataExists=${metadataFile.exists()} files=$extractionFilesSummary"
        )

        readCachedSingleFileBook(metadataFile, extractionDir, "HTML")?.let { cachedBook ->
            Timber.tag("FileOpenPerf").d("[HTML] Loaded from cache instantly | bookId=$bookId")
            Timber.tag(HTML_IMPORT_DEBUG_TAG).w("html_cache_hit bookId=$bookId chapters=${cachedBook.chapters.size} extractionBasePath=${cachedBook.extractionBasePath}")
            return@withContext cachedBook
        }
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d("html_cache_reset_before_parse bookId=$bookId dir=${extractionDir.absolutePath}")
        ImportedFileCache.resetActiveBookDir(context, bookId)

        val parseStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[HTML] parseHtml START | file=$originalBookNameHint")
        Timber.d("Importing HTML (Streaming): $originalBookNameHint")

        var title = originalBookNameHint.substringBeforeLast(".")
        var author = "Unknown"
        val cssBuilder = java.lang.StringBuilder()
        val chapters = mutableListOf<EpubChapter>()

        inputStream.bufferedReader().use { reader ->
            var inScript = false
            var skippedScriptLines = 0
            var scriptStartLines = 0
            var productEzoicScriptLines = 0
            var inStyle = false
            var inBody = false
            var pageNum = 1
            val headBuilder = java.lang.StringBuilder()
            val currentChapterBuilder = java.lang.StringBuilder()
            var titleFound = false
            var authorFound = false

            fun appendCss(style: String) {
                if (style.isBlank() || cssBuilder.length >= MAX_HTML_INLINE_CSS_CHARS) return
                val remaining = MAX_HTML_INLINE_CSS_CHARS - cssBuilder.length
                cssBuilder.append(style, 0, minOf(style.length, remaining)).append('\n')
            }

            fun appendHeadSample(sample: String) {
                if (headBuilder.length >= MAX_HTML_HEAD_SCAN_CHARS) return
                val remaining = MAX_HTML_HEAD_SCAN_CHARS - headBuilder.length
                headBuilder.append(sample, 0, minOf(sample.length, remaining)).append('\n')
            }

            fun flushChapter() {
                if (currentChapterBuilder.isBlank()) {
                    currentChapterBuilder.clear()
                    return
                }
                chapters.add(
                    writeHtmlChapter(
                        extractionDir,
                        bookId,
                        pageNum++,
                        title,
                        cssBuilder.toString(),
                        currentChapterBuilder.toString()
                    )
                )
                currentChapterBuilder.clear()
            }

            fun appendBodySegment(segment: String, startIndex: Int = 0, endIndex: Int = segment.length, addNewline: Boolean = true) {
                var start = startIndex
                while (start < endIndex) {
                    val remainingCapacity = (MAX_HTML_CHAPTER_CHARS - currentChapterBuilder.length).coerceAtLeast(1)
                    val requestedEnd = minOf(endIndex, start + remainingCapacity)
                    val chunkEnd = findHtmlChunkEnd(segment, start, requestedEnd, endIndex)
                    currentChapterBuilder.append(segment, start, chunkEnd)
                    start = chunkEnd
                    if (currentChapterBuilder.length >= MAX_HTML_CHAPTER_CHARS) {
                        flushChapter()
                    }
                }
                if (addNewline) {
                    currentChapterBuilder.append('\n')
                    if (currentChapterBuilder.length >= MAX_HTML_CHAPTER_CHARS) {
                        flushChapter()
                    }
                }
            }

            fun appendBodyLine(line: String) {
                var start = 0
                var markerIndex = line.indexOf(PAGE_BREAK_MARKER, start, ignoreCase = true)
                while (markerIndex >= 0) {
                    appendBodySegment(line, start, markerIndex)
                    flushChapter()
                    start = markerIndex + PAGE_BREAK_MARKER.length
                    markerIndex = line.indexOf(PAGE_BREAK_MARKER, start, ignoreCase = true)
                }
                appendBodySegment(line, start, line.length)
            }

            reader.forEachBoundedLine(MAX_HTML_BUFFERED_LINE_CHARS) { line ->
                val trimmed = line.trim()

                if (inScript) {
                    skippedScriptLines++
                    if (trimmed.contains("productEzoicAds", ignoreCase = true)) productEzoicScriptLines++
                    if (trimmed.contains("</script", ignoreCase = true)) {
                        inScript = false
                        Timber.tag(HTML_IMPORT_DEBUG_TAG).d("html_stream_script_end bookId=$bookId skippedScriptLines=$skippedScriptLines")
                    }
                    return@forEachBoundedLine
                }
                if (trimmed.startsWith("<script", ignoreCase = true)) {
                    skippedScriptLines++
                    scriptStartLines++
                    val lineHasProductEzoic = trimmed.contains("productEzoicAds", ignoreCase = true)
                    if (lineHasProductEzoic) productEzoicScriptLines++
                    Timber.tag(HTML_IMPORT_DEBUG_TAG).w("html_stream_script_start bookId=$bookId productEzoic=$lineHasProductEzoic sample=${trimmed.take(180)}")
                    if (!trimmed.contains("</script", ignoreCase = true)) {
                        inScript = true
                    }
                    return@forEachBoundedLine
                }

                if (!inBody) {
                    appendHeadSample(line)
                    if (!titleFound) {
                        (extractHtmlTitle(line) ?: extractHtmlTitle(headBuilder.toString()))?.let {
                            title = it
                            titleFound = true
                        }
                    }
                    if (!authorFound) {
                        (extractHtmlAuthor(line) ?: extractHtmlAuthor(headBuilder.toString()))?.let {
                            author = it
                            authorFound = true
                        }
                    }

                    if (trimmed.startsWith("<style", ignoreCase = true)) {
                        inStyle = true
                        val styleContent = line.substringAfter(">").substringBefore("</style>")
                        appendCss(styleContent)
                        if (trimmed.contains("</style>")) {
                            inStyle = false
                        }
                        return@forEachBoundedLine
                    }
                    if (inStyle) {
                        if (trimmed.contains("</style>")) {
                            appendCss(line.substringBefore("</style>"))
                            inStyle = false
                        } else {
                            appendCss(line)
                        }
                        return@forEachBoundedLine
                    }

                    if (trimmed.equals("<body>", ignoreCase = true)) {
                        inBody = true
                        return@forEachBoundedLine
                    }
                    if (trimmed.startsWith("<body ", ignoreCase = true)) {
                        inBody = true
                        val afterBody = line.substringAfter(">", "")
                        if (afterBody.isNotBlank()) appendBodyLine(afterBody)
                        return@forEachBoundedLine
                    }
                    val embeddedBodyIndex = line.indexOf("<body", ignoreCase = true)
                    if (embeddedBodyIndex >= 0) {
                        val bodyContentIndex = line.indexOf('>', startIndex = embeddedBodyIndex)
                        if (bodyContentIndex >= 0) {
                            inBody = true
                            val afterBody = line.substring(bodyContentIndex + 1)
                            if (afterBody.isNotBlank()) appendBodyLine(afterBody)
                            return@forEachBoundedLine
                        }
                    }

                    if (trimmed.startsWith("<p") || trimmed.startsWith("<div") ||
                        trimmed.startsWithHtmlHeadingTag() || trimmed.startsWith("<section") ||
                        trimmed.contains("<page-break>") ||
                        (trimmed.isNotBlank() && !trimmed.startsWith("<") && !trimmed.startsWith("<!"))) {
                        inBody = true
                        appendBodyLine(line)
                    }
                } else {
                    if (trimmed.equals("</body>", ignoreCase = true) || trimmed.equals("</html>", ignoreCase = true)) {
                        return@forEachBoundedLine
                    }

                    appendBodyLine(line)
                }
            }

            Timber.tag(HTML_IMPORT_DEBUG_TAG).d("html_stream_complete bookId=$bookId chaptersBeforeFinalFlush=${chapters.size} skippedScriptLines=$skippedScriptLines scriptStartLines=$scriptStartLines productEzoicScriptLines=$productEzoicScriptLines cssChars=${cssBuilder.length}")
            flushChapter()
        }

        if (chapters.isEmpty()) {
            chapters.add(writeHtmlChapter(extractionDir, bookId, 1, title, cssBuilder.toString(), "<p>(Empty File)</p>"))
        }

        Timber.tag("FileOpenPerf").d("[HTML] parseHtml COMPLETE | chapters=${chapters.size} | elapsed=${System.currentTimeMillis() - parseStart}ms")

        val book = EpubBook(
            fileName = originalBookNameHint,
            title = title,
            author = author,
            language = "en",
            coverImage = null,
            chapters = chapters,
            chaptersForPagination = chapters,
            images = emptyList(),
            pageList = emptyList(),
            extractionBasePath = extractionDir.absolutePath,
            css = emptyMap()
        )

        writeSingleFileMetadata(metadataFile, book, "HTML")

        return@withContext book
    }

    private fun String.startsWithHtmlHeadingTag(): Boolean {
        return length >= 3 &&
            this[0] == '<' &&
            this[1].lowercaseChar() == 'h' &&
            this[2] in '1'..'6'
    }

    private fun generatedHtmlTitle(originalBookNameHint: String): String {
        if (!originalBookNameHint.endsWith(".txt", ignoreCase = true)) return originalBookNameHint

        val innerName = originalBookNameHint.dropLast(4)
        return if (innerName.contains('.') && com.aryan.reader.isCodeOrDataFileName(innerName)) {
            innerName
        } else {
            originalBookNameHint
        }
    }

    private fun extractHtmlTitle(line: String): String? {
        val match = Regex(
            pattern = "<\\s*title\\b[^>]*>(.*?)<\\s*/\\s*title\\s*>",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(line) ?: return null

        return Jsoup.parse(match.groupValues[1]).text().takeIf { it.isNotBlank() }
    }

    private fun extractHtmlAuthor(line: String): String? {
        val metaTag = Regex(
            pattern = "<\\s*meta\\b[^>]*>",
            options = setOf(RegexOption.IGNORE_CASE)
        ).find(line)?.value ?: return null

        val name = Regex(
            pattern = "\\b(?:name|property)\\s*=\\s*['\"]([^'\"]+)['\"]",
            options = setOf(RegexOption.IGNORE_CASE)
        ).find(metaTag)?.groupValues?.get(1) ?: return null

        if (!name.equals("author", ignoreCase = true) && !name.equals("article:author", ignoreCase = true)) {
            return null
        }

        return Regex(
            pattern = "\\bcontent\\s*=\\s*['\"]([^'\"]+)['\"]",
            options = setOf(RegexOption.IGNORE_CASE)
        ).find(metaTag)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun sanitizeHtmlFragment(html: String, leadingTitleToDrop: String? = null): String {
        val body = Jsoup.parseBodyFragment(html).body()
        removeLeadingDuplicateTitleText(body, leadingTitleToDrop)
        val blockedNodes = body.select("script, style, noscript, template")
        val hasProductEzoicBefore = body.html().contains("productEzoicAds", ignoreCase = true) ||
            body.text().contains("productEzoicAds", ignoreCase = true)
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
            "sanitize_fragment_before inputChars=${html.length} blockedNodes=${blockedNodes.size} " +
                "productEzoic=$hasProductEzoicBefore"
        )
        blockedNodes.remove()
        val leakedScriptTextNodesRemoved = removeLeakedScriptTextNodes(body)
        val cleaned = Jsoup.clean(body.html(), "", htmlSafelist, htmlOutputSettings)
        val hasProductEzoicAfter = cleaned.contains("productEzoicAds", ignoreCase = true)
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
            "sanitize_fragment_after outputChars=${cleaned.length} blockedTextNodesRemoved=$leakedScriptTextNodesRemoved " +
                "productEzoic=$hasProductEzoicAfter"
        )
        return cleaned
    }

    private fun removeLeadingDuplicateTitleText(root: org.jsoup.nodes.Element, title: String?) {
        val normalizedTitle = title?.normalizeImportedHtmlTitleText()?.takeIf { it.isNotBlank() } ?: return
        for (node in root.childNodes().toList()) {
            if (node is org.jsoup.nodes.TextNode) {
                val normalizedText = node.text().normalizeImportedHtmlTitleText()
                if (normalizedText.isBlank()) {
                    node.remove()
                    continue
                }
                if (normalizedText == normalizedTitle) {
                    Timber.tag(HTML_IMPORT_DEBUG_TAG).d("sanitize_removed_leading_duplicate_title title=$title")
                    node.remove()
                }
            }
            return
        }
    }

    private fun String.normalizeImportedHtmlTitleText(): String {
        return trim().replace(Regex("\\s+"), " ").lowercase()
    }

    private fun removeLeakedScriptTextNodes(root: org.jsoup.nodes.Element): Int {
        var removed = 0
        val stack = ArrayDeque<org.jsoup.nodes.Element>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val element = stack.removeLast()
            val tagName = element.tagName().lowercase()
            if (tagName in scriptTextPreservingTags) continue

            element.textNodes().toList().forEach { textNode ->
                if (looksLikeLeakedScriptText(textNode.text())) {
                    textNode.remove()
                    removed++
                }
            }
            element.children().forEach { child -> stack.add(child) }
        }

        return removed
    }

    private fun looksLikeLeakedScriptText(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return false

        if (leakedAdScriptMarkers.any { marker -> marker in normalized }) {
            return true
        }

        val signalCount = leakedScriptTextSignals.count { signal -> signal in normalized }
        return normalized.length > 120 &&
            signalCount >= 3 &&
            ("window." in normalized || "document." in normalized || "function" in normalized)
    }

    private data class PlainTextSummary(val text: String, val length: Int)

    private fun String.toPlainTextSummary(maxChars: Int = MAX_SINGLE_FILE_PLAIN_TEXT_CHARS): PlainTextSummary {
        val text = StringBuilder(minOf(length, maxChars))
        var plainLength = 0
        var index = 0
        var inTag = false
        var previousWasWhitespace = true

        fun appendDecoded(char: Char) {
            val output = if (char.isWhitespace()) ' ' else char
            if (output == ' ') {
                if (previousWasWhitespace) return
                previousWasWhitespace = true
            } else {
                previousWasWhitespace = false
            }
            plainLength += 1
            if (text.length < maxChars) {
                text.append(output)
            }
        }

        while (index < length) {
            val char = this[index]
            when {
                inTag -> {
                    if (char == '>') inTag = false
                    index += 1
                }
                char == '<' -> {
                    inTag = true
                    if (startsWithAny(index, "<br", "</p", "</div", "</section", "</article", "</li", "</tr", "</h")) {
                        appendDecoded(' ')
                    }
                    index += 1
                }
                char == '&' -> {
                    val semicolon = indexOf(';', startIndex = index + 1).takeIf { it in (index + 2)..(index + 12) }
                    if (semicolon != null) {
                        appendDecoded(decodeHtmlEntity(substring(index + 1, semicolon)) ?: ' ')
                        index = semicolon + 1
                    } else {
                        appendDecoded(char)
                        index += 1
                    }
                }
                else -> {
                    appendDecoded(char)
                    index += 1
                }
            }
        }

        return PlainTextSummary(text.toString().trim(), plainLength)
    }

    private fun String.startsWithAny(index: Int, vararg prefixes: String): Boolean {
        return prefixes.any { prefix -> regionMatches(index, prefix, 0, prefix.length, ignoreCase = true) }
    }

    private fun decodeHtmlEntity(entity: String): Char? {
        return when {
            entity.equals("amp", ignoreCase = true) -> '&'
            entity.equals("lt", ignoreCase = true) -> '<'
            entity.equals("gt", ignoreCase = true) -> '>'
            entity.equals("quot", ignoreCase = true) -> '"'
            entity.equals("apos", ignoreCase = true) -> '\''
            entity.equals("nbsp", ignoreCase = true) -> ' '
            entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)?.toChar()
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toChar()
            else -> null
        }
    }

    private suspend fun parseDocx(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String,
        parseContent: Boolean
    ): EpubBook = withContext(Dispatchers.IO) {
        if (!parseContent) {
            return@withContext EpubBook(
                fileName = originalBookNameHint,
                title = originalBookNameHint.substringBeforeLast("."),
                author = "Unknown",
                language = "en",
                coverImage = null,
                chapters = emptyList(),
                chaptersForPagination = emptyList(),
                images = emptyList(),
                pageList = emptyList(),
                extractionBasePath = "",
                css = emptyMap()
            )
        }

        val extractionDir = ImportedFileCache.ensureActiveBookDir(context, bookId)
        val metadataFile = metadataFile(extractionDir)

        readCachedSingleFileBook(metadataFile, extractionDir, "DOCX")?.let { cachedBook ->
            Timber.tag("FileOpenPerf").d("[DOCX] Loaded from cache instantly | bookId=$bookId")
            return@withContext cachedBook
        }
        ImportedFileCache.resetActiveBookDir(context, bookId)

        val parseStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[DOCX] parseDocx START | file=$originalBookNameHint")

        val sourceDocxFile = File(context.cacheDir, "temp_docx_source_${UUID.randomUUID()}.docx")
        val tempFile = File(context.cacheDir, "temp_docx_${UUID.randomUUID()}.html")
        try {
            inputStream.use { stream ->
                FileOutputStream(sourceDocxFile).use { output ->
                    stream.copyTo(output)
                }
            }

            validateDocxForImport(sourceDocxFile, originalBookNameHint)

            val htmlContent = sourceDocxFile.inputStream().use { stream ->
                try {
                    val converter = DocumentConverter()
                    converter.convertToHtml(stream).value ?: ""
                } catch (oom: OutOfMemoryError) {
                    Timber.e(oom, "DOCX conversion ran out of memory for $originalBookNameHint")
                    throw IllegalStateException("This DOCX file is too large to open safely on this device.")
                }
            }

            Timber.tag("FileOpenPerf").d("[DOCX] parseDocx: mammoth conversion done | elapsed=${System.currentTimeMillis() - parseStart}ms")

            FileOutputStream(tempFile).bufferedWriter().use { writer ->
                val title = originalBookNameHint.substringBeforeLast(".")
                writer.write("<!DOCTYPE html>\n<html>\n<head>\n<title>$title</title>\n</head>\n<body>\n")
                writeHtmlBodyContentChunked(writer, htmlContent)
                writer.write("\n</body>\n</html>")
            }

            tempFile.inputStream().use { tempStream ->
                return@withContext parseHtml(tempStream, originalBookNameHint, bookId, parseContent)
            }
        } finally {
            if (sourceDocxFile.exists()) {
                sourceDocxFile.delete()
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun validateDocxForImport(sourceDocxFile: File, originalBookNameHint: String) {
        val archiveBytes = sourceDocxFile.length()
        if (archiveBytes > MAX_DOCX_ARCHIVE_BYTES) {
            throw IllegalStateException("This DOCX file is too large to open safely on this device.")
        }

        ZipFile(sourceDocxFile).use { zip ->
            var totalXmlBytes = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (entry.name.endsWith(".xml", ignoreCase = true)) {
                    val entrySize = entry.size
                    if (entrySize > 0) {
                        totalXmlBytes += entrySize
                    }
                    if (totalXmlBytes > MAX_DOCX_XML_BYTES) {
                        Timber.w("DOCX XML payload too large for import: file=$originalBookNameHint xmlBytes=$totalXmlBytes")
                        throw IllegalStateException("This DOCX file is too large to open safely on this device.")
                    }
                }
            }
        }
    }

    private fun writeHtmlChapter(
        extractionDir: File,
        bookId: String,
        pageNum: Int,
        title: String,
        cssStyle: String,
        bodyContent: String
    ): EpubChapter {
        val chapterTitle = if (pageNum > 1 || bodyContent.contains("<page-break")) "Page $pageNum" else title
        val fileName = "page_$pageNum.html"
        val file = File(extractionDir, fileName)
        val inputScriptTags = Regex("<script\\b", RegexOption.IGNORE_CASE).findAll(bodyContent).count()
        val inputHasProductEzoic = bodyContent.contains("productEzoicAds", ignoreCase = true)
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
            "html_write_chapter_before_sanitize bookId=$bookId page=$pageNum inputChars=${bodyContent.length} " +
                "scriptTags=$inputScriptTags productEzoic=$inputHasProductEzoic file=${file.absolutePath}"
        )
        val sanitizedBodyContent = sanitizeHtmlFragment(bodyContent, leadingTitleToDrop = title)
        val outputScriptTags = Regex("<script\\b", RegexOption.IGNORE_CASE).findAll(sanitizedBodyContent).count()
        val outputHasProductEzoic = sanitizedBodyContent.contains("productEzoicAds", ignoreCase = true)
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
            "html_write_chapter_after_sanitize bookId=$bookId page=$pageNum outputChars=${sanitizedBodyContent.length} " +
                "scriptTags=$outputScriptTags productEzoic=$outputHasProductEzoic"
        )
        val escapedTitle = title.replace("\"", "&quot;")

        file.bufferedWriter().use { writer ->
            writer.write("<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<title>")
            writer.write(escapedTitle)
            writer.write("</title>\n<style>")
            writer.write(cssStyle)
            writer.write("</style>\n</head>\n<body>\n")
            writer.write(sanitizedBodyContent.trim())
            writer.write("\n</body>\n</html>")
        }

        val plainText = sanitizedBodyContent.toPlainTextSummary()
        val plainTextHasProductEzoic = plainText.text.contains("productEzoicAds", ignoreCase = true)
        Timber.tag(HTML_IMPORT_DEBUG_TAG).d(
            "html_write_chapter_file_written bookId=$bookId page=$pageNum fileBytes=${file.length()} " +
                "plainTextChars=${plainText.text.length} plainTextLength=${plainText.length} " +
                "plainTextProductEzoic=$plainTextHasProductEzoic"
        )

        return EpubChapter(
            chapterId = "${bookId}_$pageNum",
            absPath = fileName,
            title = chapterTitle,
            htmlFilePath = fileName,
            plainTextContent = plainText.text,
            htmlContent = "",
            depth = 0,
            isInToc = true,
            plainTextLength = plainText.length
        )
    }

    private fun java.io.BufferedReader.forEachBoundedLine(
        maxLineChars: Int,
        onLine: (String) -> Unit
    ) {
        val buffer = CharArray(16_384)
        val currentLine = StringBuilder()
        while (true) {
            val read = read(buffer)
            if (read == -1) break

            var start = 0
            var index = 0
            while (index < read) {
                val char = buffer[index]
                val reachesLimit = currentLine.length + (index - start + 1) >= maxLineChars
                if (char == '\n' || reachesLimit) {
                    val count = index - start + if (char == '\n') 0 else 1
                    if (count > 0) {
                        currentLine.append(buffer, start, count)
                    }
                    onLine(currentLine.toString().trimEnd('\r'))
                    currentLine.clear()
                    start = index + 1
                }
                index++
            }

            if (start < read) {
                currentLine.append(buffer, start, read - start)
            }
        }

        if (currentLine.isNotEmpty()) {
            onLine(currentLine.toString().trimEnd('\r'))
        }
    }

    private fun findHtmlChunkEnd(
        source: String,
        start: Int,
        requestedEnd: Int,
        absoluteEnd: Int
    ): Int {
        if (requestedEnd >= absoluteEnd) return absoluteEnd

        val tagStart = source.lastIndexOf('<', requestedEnd - 1)
        val tagEnd = source.lastIndexOf('>', requestedEnd - 1)
        if (tagStart > start && tagStart > tagEnd && requestedEnd - tagStart <= 4096) {
            return tagStart
        }

        return requestedEnd
    }

    private fun writeHtmlBodyContentChunked(writer: java.io.Writer, htmlContent: String) {
        var charsSinceBreak = 0
        for (char in htmlContent) {
            writer.write(char.code)
            charsSinceBreak++
            if (char == '>' || charsSinceBreak >= MAX_HTML_BUFFERED_LINE_CHARS) {
                writer.write('\n'.code)
                charsSinceBreak = 0
            }
        }
    }
}
