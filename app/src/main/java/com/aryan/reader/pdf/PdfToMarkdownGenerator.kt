package com.aryan.reader.pdf

import android.content.Context
import com.aryan.reader.pdf.data.PdfTextRepository
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object PdfToMarkdownGenerator {

    suspend fun generateReflowFile(
        context: Context,
        bookId: String,
        document: PdfDocumentKt,
        repository: PdfTextRepository,
        totalPages: Int
    ): File = withContext(Dispatchers.Default) {
        val cacheDir = File(context.cacheDir, "reflow_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val outputFile = File(cacheDir, "${bookId}.md")

        if (outputFile.exists() && outputFile.length() > 0) {
            Timber.d("Reflow: Returning cached Markdown file for $bookId")
            return@withContext outputFile
        }

        Timber.d("Reflow: Generating new Markdown file for $bookId ($totalPages pages)")
        val sb = StringBuilder()

        val metaTitle = document.getDocumentMeta().title ?: "Reflowed Document"
        sb.append("# $metaTitle\n\n")

        for (i in 0 until totalPages) {
            val rawPageText = repository.getOrExtractText(bookId, document, i)

            if (rawPageText.isNotBlank()) {
                val processedPage = processPageText(rawPageText, i + 1)
                sb.append(processedPage).append("\n\n")
            }

            if (i % 10 == 0) Timber.d("Reflow: Processed page $i/$totalPages")
        }

        outputFile.writeText(sb.toString())
        return@withContext outputFile
    }

    private fun processPageText(rawText: String, pageNumber: Int): String {
        val lines = rawText.split('\n')
        val sb = StringBuilder()

        sb.append("<!-- Page $pageNumber -->\n")

        val paragraphBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph(sb, paragraphBuffer)
                continue
            }

            if (trimmed.length < 5 && trimmed.all { it.isDigit() }) {
                continue
            }

            val isHeader = trimmed.length < 60 &&
                    trimmed.any { it.isLetter() } &&
                    trimmed.all { !it.isLetter() || it.isUpperCase() } &&
                    !trimmed.endsWith(".")

            if (isHeader) {
                flushParagraph(sb, paragraphBuffer)
                sb.append("## $trimmed\n\n")
                continue
            }

            if (paragraphBuffer.isNotEmpty()) {
                paragraphBuffer.append(" ")
            }
            paragraphBuffer.append(trimmed)
        }

        flushParagraph(sb, paragraphBuffer)
        return sb.toString()
    }

    private fun flushParagraph(sb: StringBuilder, buffer: StringBuilder) {
        if (buffer.isNotEmpty()) {
            sb.append(buffer.toString()).append("\n\n")
            buffer.clear()
        }
    }
}