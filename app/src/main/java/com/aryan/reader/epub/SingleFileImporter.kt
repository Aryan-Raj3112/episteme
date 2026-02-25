/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
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
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class SingleFileImporter(private val context: Context) {

    suspend fun importSingleFile(
        inputStream: InputStream,
        type: FileType,
        originalBookNameHint: String
    ): EpubBook {
        return when (type) {
            FileType.MD -> parseMarkdown(inputStream, originalBookNameHint)
            FileType.TXT -> parsePlainText(inputStream, originalBookNameHint)
            FileType.HTML -> parseHtml(inputStream, originalBookNameHint)
            else -> parsePlainText(inputStream, originalBookNameHint) // Fallback
        }
    }

    private suspend fun parseMarkdown(
        inputStream: InputStream,
        originalBookNameHint: String
    ): EpubBook = withContext(Dispatchers.IO) {
        Timber.d("Parsing Markdown: $originalBookNameHint")
        val title = originalBookNameHint.substringBeforeLast(".")
        val markdownContent = inputStream.bufferedReader().use { it.readText() }

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

        val document = parser.parse(markdownContent)
        val htmlBody = renderer.render(document)

        val style = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
            table { border-collapse: collapse; width: 100%; margin: 1em 0; }
            th, td { border: 1px solid currentColor; padding: 0.5em; text-align: left; }
            blockquote { border-left: 4px solid currentColor; padding-left: 1em; margin-left: 0; opacity: 0.8; }
            pre { overflow-x: auto; background: rgba(127,127,127,0.1); padding: 1em; border-radius: 4px; }
            img { max-width: 100%; height: auto; }
        """.trimIndent()

        return@withContext createBookFromHtmlBody(title, htmlBody, style, originalBookNameHint, author = null)
    }

    private suspend fun parsePlainText(
        inputStream: InputStream,
        originalBookNameHint: String
    ): EpubBook = withContext(Dispatchers.IO) {
        Timber.d("Parsing Plain Text with Virtual Chaptering: $originalBookNameHint")
        val title = originalBookNameHint.substringBeforeLast(".")
        val bookId = UUID.randomUUID().toString()

        val extractionDir = File(context.cacheDir, "imported_txt_$bookId").apply {
            if (!exists()) mkdirs()
        }

        val chapters = mutableListOf<EpubChapter>()
        var chapterCounter = 1

        val cssStyle = """
            body { margin: 0; padding: 0; }
            pre { 
                font-family: 'Courier New', Courier, monospace; 
                font-size: 1em;
                line-height: 1.5;
                white-space: pre-wrap; 
                overflow-wrap: anywhere; 
                word-break: break-word;
                padding: 1em;
                margin: 0;
            }
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
                <body><pre>${currentChapterContent}</pre></body>
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
                    htmlContent = fullHtml,
                    depth = 0,
                    isInToc = true
                )
            )

            currentChapterContent.clear()
            chapterCounter++
        }

        val reader = inputStream.bufferedReader()
        val buffer = CharArray(8192)

        while (true) {
            val readCount = reader.read(buffer)
            if (readCount == -1) break

            for (i in 0 until readCount) {
                val c = buffer[i]

                if ((c < ' ' && c != '\t' && c != '\n' && c != '\r')) {
                    continue
                }

                when (c) {
                    '<' -> currentChapterContent.append("&lt;")
                    '>' -> currentChapterContent.append("&gt;")
                    '&' -> currentChapterContent.append("&amp;")
                    else -> currentChapterContent.append(c)
                }
            }

            if (currentChapterContent.length >= chapterTargetSize) {
                flushChapter()
            }
        }

        flushChapter()

        if (chapters.isEmpty()) {
            currentChapterContent.append("(Empty File)")
            flushChapter()
        }

        Timber.d("Imported TXT split into ${chapters.size} chapters.")

        return@withContext EpubBook(
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
    }

    private suspend fun parseHtml(
        inputStream: InputStream,
        originalBookNameHint: String
    ): EpubBook = withContext(Dispatchers.IO) {
        Timber.d("Importing HTML: $originalBookNameHint")

        val content = inputStream.bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(content)
        val title = doc.title().takeIf { it.isNotBlank() } ?: originalBookNameHint.substringBeforeLast(".")

        val author = doc.select("meta[name=author]").attr("content").takeIf { it.isNotBlank() }
            ?: doc.select("meta[property=article:author]").attr("content").takeIf { it.isNotBlank() }

        val finalHtml = doc.outerHtml()

        createBookFromHtmlBody(title, null, null, originalBookNameHint, preGeneratedFullHtml = finalHtml, author = author)
    }

    private fun createBookFromHtmlBody(
        title: String,
        bodyContent: String?,
        cssStyle: String?,
        fileName: String,
        preGeneratedFullHtml: String? = null,
        author: String? = null
    ): EpubBook {
        val fullHtml = preGeneratedFullHtml ?: """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>${title.replace("\"", "&quot;")}</title>
                <style>
                    ${cssStyle ?: ""}
                </style>
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()

        val plainText = Jsoup.parse(fullHtml).text()

        val bookId = UUID.randomUUID().toString()
        val extractionDir = File(context.cacheDir, "single_file_cache_$bookId").apply {
            if (!exists()) mkdirs()
        }

        try {
            File(extractionDir, "content.html").writeText(fullHtml)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save generated HTML to disk")
        }

        val chapter = EpubChapter(
            chapterId = bookId,
            absPath = "content.html",
            title = title,
            htmlFilePath = "content.html",
            plainTextContent = plainText,
            htmlContent = fullHtml,
            depth = 0,
            isInToc = true
        )

        return EpubBook(
            fileName = fileName,
            title = title,
            author = author ?: "",
            language = "en",
            coverImage = null,
            chapters = listOf(chapter),
            chaptersForPagination = listOf(chapter),
            images = emptyList(),
            pageList = emptyList(),
            extractionBasePath = extractionDir.absolutePath,
            css = emptyMap()
        )
    }
}