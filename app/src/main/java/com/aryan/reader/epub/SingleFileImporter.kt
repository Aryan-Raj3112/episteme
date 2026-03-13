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
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class SingleFileImporter(private val context: Context) {

    private val jsonSerializer = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun importSingleFile(
        inputStream: InputStream,
        type: FileType,
        originalBookNameHint: String,
        bookId: String
    ): EpubBook {
        return when (type) {
            FileType.MD -> parseMarkdown(inputStream, originalBookNameHint, bookId)
            FileType.TXT -> parsePlainText(inputStream, originalBookNameHint, bookId)
            FileType.HTML -> parseHtml(inputStream, originalBookNameHint, bookId)
            else -> parsePlainText(inputStream, originalBookNameHint, bookId) // Fallback
        }
    }

    private suspend fun parseMarkdown(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String
    ): EpubBook = withContext(Dispatchers.IO) {
        val extractionDir = File(context.cacheDir, "imported_file_$bookId").apply {
            if (!exists()) mkdirs()
        }
        val metadataFile = File(extractionDir, "book_metadata.json")

        if (metadataFile.exists()) {
            try {
                val cachedBook = jsonSerializer.decodeFromString<EpubBook>(metadataFile.readText())
                Timber.tag("FileOpenPerf").d("[MD] Loaded from cache instantly | bookId=$bookId")
                return@withContext cachedBook
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cached MD, parsing again")
            }
        }

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
                val htmlBody = renderer.render(document)

                val fileName = "page_$pageNum.html"
                val file = File(extractionDir, fileName)

                val fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>$chapterTitle</title>
                        <style>$style</style>
                    </head>
                    <body>
                    $htmlBody
                    </body>
                    </html>
                """.trimIndent()

                file.writeText(fullHtml)

                EpubChapter(
                    chapterId = "${bookId}_$pageNum",
                    absPath = fileName,
                    title = chapterTitle,
                    htmlFilePath = fileName,
                    plainTextContent = Jsoup.parse(htmlBody).text(),
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

        try {
            metadataFile.writeText(jsonSerializer.encodeToString(book))
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache MD metadata")
        }

        return@withContext book
    }

    private suspend fun parsePlainText(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String
    ): EpubBook = withContext(Dispatchers.IO) {
        val extractionDir = File(context.cacheDir, "imported_file_$bookId").apply {
            if (!exists()) mkdirs()
        }
        val metadataFile = File(extractionDir, "book_metadata.json")

        if (metadataFile.exists()) {
            try {
                val cachedBook = jsonSerializer.decodeFromString<EpubBook>(metadataFile.readText())
                Timber.tag("FileOpenPerf").d("[TXT] Loaded from cache instantly | bookId=$bookId")
                return@withContext cachedBook
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cached TXT, parsing again")
            }
        }

        val parseStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[TXT] parsePlainText START | file=$originalBookNameHint")
        Timber.d("Parsing Plain Text with Virtual Chaptering: $originalBookNameHint")
        val title = originalBookNameHint.substringBeforeLast(".")

        val chapters = mutableListOf<EpubChapter>()
        var chapterCounter = 1

        val cssStyle = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
            p { margin-bottom: 1em; text-indent: 1.5em; }
        """.trimIndent()

        val currentChapterContent = StringBuilder()
        val chapterTargetSize = 64 * 1024

        fun flushChapter() {
            if (currentChapterContent.isEmpty()) return

            val fileName = "part_$chapterCounter.html"
            val file = File(extractionDir, fileName)
            val chapterTitle = "Part $chapterCounter"

            val fullHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>$chapterTitle</title>
                    <style>$cssStyle</style>
                </head>
                <body>
                $currentChapterContent
                </body>
                </html>
            """.trimIndent()

            FileOutputStream(file).use { it.write(fullHtml.toByteArray()) }

            val plainText = Jsoup.parse(fullHtml).text()

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
            chapterCounter++
        }

        fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }

        val reader = inputStream.bufferedReader()
        var inParagraph = false

        while (true) {
            val line = reader.readLine()
            if (line == null) {
                if (inParagraph) {
                    currentChapterContent.append("</p>\n")
                }
                break
            }

            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (inParagraph) {
                    currentChapterContent.append("</p>\n")
                    inParagraph = false
                }

                if (currentChapterContent.length >= chapterTargetSize) {
                    flushChapter()
                }
            } else {
                if (!inParagraph) {
                    currentChapterContent.append("<p>")
                    inParagraph = true
                } else {
                    currentChapterContent.append(" ")
                }
                currentChapterContent.append(escapeHtml(trimmed))

                if (currentChapterContent.length >= chapterTargetSize * 2) {
                    currentChapterContent.append("</p>\n")
                    flushChapter()
                    inParagraph = false
                }
            }
        }

        flushChapter()

        if (chapters.isEmpty()) {
            currentChapterContent.append("<p>(Empty File)</p>")
            flushChapter()
        }

        Timber.d("Imported TXT split into ${chapters.size} chapters.")

        Timber.tag("FileOpenPerf").d("[TXT] parsePlainText COMPLETE | chapters=${chapters.size} | totalElapsed=${System.currentTimeMillis() - parseStart}ms")

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

        try {
            metadataFile.writeText(jsonSerializer.encodeToString(book))
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache TXT metadata")
        }

        return@withContext book
    }

    private suspend fun parseHtml(
        inputStream: InputStream,
        originalBookNameHint: String,
        bookId: String
    ): EpubBook = withContext(Dispatchers.IO) {
        val extractionDir = File(context.cacheDir, "imported_file_$bookId").apply {
            if (!exists()) mkdirs()
        }
        val metadataFile = File(extractionDir, "book_metadata.json")

        if (metadataFile.exists()) {
            try {
                val cachedBook = jsonSerializer.decodeFromString<EpubBook>(metadataFile.readText())
                Timber.tag("FileOpenPerf").d("[HTML] Loaded from cache instantly | bookId=$bookId")
                return@withContext cachedBook
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cached HTML, parsing again")
            }
        }

        val parseStart = System.currentTimeMillis()
        Timber.tag("FileOpenPerf").d("[HTML] parseHtml START | file=$originalBookNameHint")
        Timber.d("Importing HTML: $originalBookNameHint")

        val content = inputStream.bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(content)
        val title = doc.title().takeIf { it.isNotBlank() } ?: originalBookNameHint.substringBeforeLast(".")
        Timber.tag("FileOpenPerf").d("[HTML] parseHtml: Read ${content.length} chars | elapsed=${System.currentTimeMillis() - parseStart}ms")

        val author = doc.select("meta[name=author]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("meta[property=article:author]").attr("content").takeIf { it.isNotBlank() }

        val cssStyle = doc.select("style").html()
        val bodyHtml = doc.body().html()

        val rawChapters = if (bodyHtml.contains("<page-break></page-break>")) {
            bodyHtml.split("<page-break></page-break>")
        } else {
            listOf(bodyHtml)
        }

        val chapters = rawChapters.mapIndexed { index, rawText ->
            async(Dispatchers.Default) {
                if (rawText.isBlank()) return@async null

                val pageNum = index + 1
                val chapterTitle = if (rawChapters.size > 1) "Page $pageNum" else title
                val fileName = "page_$pageNum.html"
                val file = File(extractionDir, fileName)

                val fullHtml = """
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>${title.replace("\"", "&quot;")}</title>
                        <style>${cssStyle}</style>
                    </head>
                    <body>
                        ${rawText.trim()}
                    </body>
                    </html>
                """.trimIndent()

                file.writeText(fullHtml)

                EpubChapter(
                    chapterId = "${bookId}_$pageNum",
                    absPath = fileName,
                    title = chapterTitle,
                    htmlFilePath = fileName,
                    plainTextContent = Jsoup.parse(fullHtml).text(),
                    htmlContent = "",
                    depth = 0,
                    isInToc = true
                )
            }
        }.awaitAll().filterNotNull()

        Timber.tag("FileOpenPerf").d("[HTML] parseHtml COMPLETE | elapsed=${System.currentTimeMillis() - parseStart}ms")

        val book = EpubBook(
            fileName = originalBookNameHint,
            title = title,
            author = author ?: "Unknown",
            language = "en",
            coverImage = null,
            chapters = chapters,
            chaptersForPagination = chapters,
            images = emptyList(),
            pageList = emptyList(),
            extractionBasePath = extractionDir.absolutePath,
            css = emptyMap()
        )

        try {
            metadataFile.writeText(jsonSerializer.encodeToString(book))
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache HTML metadata")
        }

        return@withContext book
    }
}