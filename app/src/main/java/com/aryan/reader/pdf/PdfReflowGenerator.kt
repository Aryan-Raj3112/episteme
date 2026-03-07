package com.aryan.reader.pdf

import android.content.Context
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.pdf.data.PdfTextRepository
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PdfReflowGenerator {

    suspend fun generateReflowBook(
        context: Context,
        bookId: String,
        document: PdfDocumentKt,
        repository: PdfTextRepository,
        totalPages: Int
    ): EpubBook = withContext(Dispatchers.Default) {
        val cacheDir = File(context.cacheDir, "reflow_cache/$bookId")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        cacheDir.mkdirs()

        val chapters = mutableListOf<EpubChapter>()
        val css = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; }
            p { margin-bottom: 1em; }
            h1, h2 { color: #333; margin-top: 1.5em; }
            .page-marker { color: #888; font-size: 0.8em; margin-bottom: 2em; border-bottom: 1px solid #eee; }
        """.trimIndent()

        // We generate a chapter for every page to keep sync simple
        for (i in 0 until totalPages) {
            val rawText = repository.getOrExtractText(bookId, document, i)
            val cleanedHtml = processTextToHtml(rawText, i + 1)

            val fileName = "page_$i.html"
            val file = File(cacheDir, fileName)

            val fullHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Page ${i + 1}</title>
                    <style>$css</style>
                </head>
                <body>
                    $cleanedHtml
                </body>
                </html>
            """.trimIndent()

            file.writeText(fullHtml)

            chapters.add(
                EpubChapter(
                    chapterId = "${bookId}_page_$i",
                    absPath = fileName,
                    title = "Page ${i + 1}",
                    htmlFilePath = fileName,
                    plainTextContent = rawText, // Raw text for search/TTS
                    htmlContent = fullHtml,
                    depth = 0,
                    isInToc = true
                )
            )
        }

        EpubBook(
            fileName = "Reflow_Session",
            title = document.getDocumentMeta().title ?: "Reflow View",
            author = document.getDocumentMeta().author ?: "",
            language = "en",
            coverImage = null,
            chapters = chapters,
            chaptersForPagination = chapters,
            images = emptyList(),
            pageList = emptyList(),
            extractionBasePath = cacheDir.absolutePath,
            css = emptyMap()
        )
    }

    private fun processTextToHtml(rawText: String, pageNumber: Int): String {
        if (rawText.isBlank()) return "<p><i>(No text on this page)</i></p>"

        val lines = rawText.split('\n')
        val sb = StringBuilder()

        sb.append("<div class='page-marker'>Page $pageNumber</div>")

        var currentParagraph = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentParagraph.isNotEmpty()) {
                    sb.append("<p>${currentParagraph.toString()}</p>")
                    currentParagraph.clear()
                }
                continue
            }

            // Heuristic: Header detection (All caps, short line, no punctuation at end)
            val isHeader = trimmed.length < 50 && trimmed.all { it.isUpperCase() || !it.isLetter() } && !trimmed.endsWith(".")

            if (isHeader) {
                if (currentParagraph.isNotEmpty()) {
                    sb.append("<p>${currentParagraph.toString()}</p>")
                    currentParagraph.clear()
                }
                sb.append("<h2>$trimmed</h2>")
                continue
            }

            if (currentParagraph.isNotEmpty()) {
                currentParagraph.append(" ")
            }
            currentParagraph.append(trimmed)

            if (trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!") || trimmed.endsWith(":")) {
            }
        }

        if (currentParagraph.isNotEmpty()) {
            sb.append("<p>${currentParagraph.toString()}</p>")
        }

        return sb.toString()
    }
}