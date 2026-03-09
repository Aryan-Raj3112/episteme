// PdfToMarkdownGenerator.kt
package com.aryan.reader.pdf

import android.content.Context
import android.net.Uri
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

object PdfToMarkdownGenerator {
    const val PAGE_DELIMITER = "\n\n[[PAGE_BREAK]]\n\n"

    suspend fun generateMarkdownFile(
        context: Context,
        pdfUri: Uri,
        destFile: File,
        startPage: Int = 1,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val methodStartTime = System.currentTimeMillis()
        Timber.tag("PdfToMdPerf").d("generateMarkdownFile NATIVE START | uri=$pdfUri | startPage=$startPage")

        val pdfiumCore = PdfiumCoreKt(Dispatchers.Default)
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
        if (pfd == null) {
            Timber.tag("PdfToMdPerf").e("Failed to open ParcelFileDescriptor")
            return@withContext false
        }

        try {
            val doc = pdfiumCore.newDocument(pfd)
            val totalPages = doc.getPageCount()
            Timber.tag("PdfToMdPerf").d("Document loaded natively. Total Pages: $totalPages")

            destFile.bufferedWriter().use { writer ->
                for (pageIdx in (startPage - 1) until totalPages) {
                    val pageMd = extractPageMarkdown(doc, pageIdx)
                    writer.write(pageMd)
                    writer.write(PAGE_DELIMITER)

                    if (pageIdx % 5 == 0 || pageIdx == totalPages - 1) {
                        onProgress((pageIdx + 1).toFloat() / totalPages.toFloat())
                    }
                }
            }

            doc.close()
            pfd.close()

            Timber.tag("PdfToMdPerf").d("generateMarkdownFile NATIVE SUCCESS | totalTime=${System.currentTimeMillis() - methodStartTime}ms")
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate Markdown from PDF natively")
            pfd.close()
            return@withContext false
        }
    }

    private suspend fun extractPageMarkdown(doc: PdfDocumentKt, pageIdx: Int): String {
        return try {
            doc.openPage(pageIdx).use { page ->
                page.openTextPage().use { textPage ->
                    val charCount = textPage.textPageCountChars()
                    if (charCount <= 0) return@use ""

                    val text = textPage.textPageGetText(0, charCount) ?: ""
                    val actualCount = minOf(charCount, text.length)

                    val rawPtr = textPage.page.pagePtr

                    val sizes: FloatArray?
                    val weights: IntArray?
                    val flags: IntArray?

                    synchronized(PdfiumCore.lock) {
                        sizes = NativePdfiumBridge.getPageFontSizes(rawPtr, actualCount)
                        weights = NativePdfiumBridge.getPageFontWeights(rawPtr, actualCount)
                        flags = NativePdfiumBridge.getPageFontFlags(rawPtr, actualCount)
                    }

                    if (sizes == null || weights == null || flags == null) {
                        return@use text
                    }

                    buildMarkdown(text, sizes, weights, flags, actualCount)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error extracting page $pageIdx")
            ""
        }
    }

    private data class TextSpan(
        val text: String,
        val size: Float,
        val isBold: Boolean,
        val isItalic: Boolean
    )

    private data class TextLine(
        val spans: List<TextSpan>
    )

    private fun fixKerning(text: String): String {
        val pattern = Regex("\\b(?:[A-Za-z0-9] ){2,}[A-Za-z0-9]\\b")
        return pattern.replace(text) { matchResult ->
            matchResult.value.replace(" ", "")
        }
    }

    private fun buildMarkdown(text: String, sizes: FloatArray, weights: IntArray, flags: IntArray, count: Int): String {
        if (count == 0) return ""

        val sizeFrequency = HashMap<Int, Int>()
        for (i in 0 until count) {
            val s = sizes[i].roundToInt()
            sizeFrequency[s] = (sizeFrequency[s] ?: 0) + 1
        }
        val baseSize = sizeFrequency.maxByOrNull { it.value }?.key ?: 12

        val lines = mutableListOf<TextLine>()
        @Suppress("CanBeVal") var currentSpans = mutableListOf<TextSpan>()
        val currentSpanText = StringBuilder()

        var currentSize = -1f
        var currentBold = false
        var currentItalic = false

        for (i in 0 until count) {
            val c = text[i]
            if (c == '\u0000') continue

            if (c == '\n' || c == '\r') {
                if (c == '\n' && i > 0 && text[i - 1] == '\r') continue

                if (currentSpanText.isNotEmpty()) {
                    currentSpans.add(TextSpan(currentSpanText.toString(), currentSize, currentBold, currentItalic))
                    currentSpanText.clear()
                }
                lines.add(TextLine(currentSpans.toList()))
                currentSpans.clear()
                continue
            }

            val isSpace = c.isWhitespace()
            val size = sizes[i]
            val bold = weights[i] > 600
            val italic = (flags[i] and 64) != 0

            if (currentSpanText.isEmpty()) {
                currentSize = size
                currentBold = bold
                currentItalic = italic
                currentSpanText.append(c)
            } else {
                if (!isSpace && (currentSize != size || currentBold != bold || currentItalic != italic)) {
                    currentSpans.add(TextSpan(currentSpanText.toString(), currentSize, currentBold, currentItalic))
                    currentSpanText.clear()
                    currentSize = size
                    currentBold = bold
                    currentItalic = italic
                }
                currentSpanText.append(c)
            }
        }

        if (currentSpanText.isNotEmpty()) {
            currentSpans.add(TextSpan(currentSpanText.toString(), currentSize, currentBold, currentItalic))
        }
        if (currentSpans.isNotEmpty()) {
            lines.add(TextLine(currentSpans))
        }

        val validLines = lines.filter { it.spans.isNotEmpty() }
        val lineLengths = validLines.map { line -> line.spans.sumOf { it.text.length } }.filter { it > 10 }.sorted()

        val typicalLineLen = if (lineLengths.isNotEmpty()) {
            lineLengths[(lineLengths.size * 0.8).toInt().coerceAtMost(lineLengths.size - 1)]
        } else {
            80
        }

        val wrapThreshold = (typicalLineLen * 0.85).toInt()

        val sb = StringBuilder()

        for (i in lines.indices) {
            val line = lines[i]
            if (line.spans.isEmpty()) {
                sb.append("\n")
                continue
            }

            val maxFontSize = line.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.size } ?: baseSize.toFloat()
            val charBigHeader = maxFontSize > baseSize * 1.5f
            val charHeader = maxFontSize > baseSize * 1.2f

            var prefix = ""
            if (charBigHeader) prefix = "## "
            else if (charHeader) prefix = "### "

            val rawLineText = line.spans.joinToString("") { it.text }
            val trimmedRaw = rawLineText.trim()
            val lineLen = trimmedRaw.length

            val isList = trimmedRaw.startsWith("•") ||
                    trimmedRaw.startsWith("- ") ||
                    trimmedRaw.startsWith("▪") ||
                    trimmedRaw.matches(Regex("^[0-9]+\\.\\s.*")) ||
                    trimmedRaw.matches(Regex("^[a-zA-Z]\\)\\s.*"))

            if (prefix.isNotEmpty() && !isList) {
                sb.append(prefix)
            }

            for (span in line.spans) {
                var spanText = span.text
                spanText = fixKerning(spanText)

                val leadingSpaces = spanText.takeWhile { it.isWhitespace() }
                val trailingSpaces = spanText.takeLastWhile { it.isWhitespace() }
                val trimmedText = spanText.trim()

                if (trimmedText.isEmpty()) {
                    sb.append(spanText)
                    continue
                }

                sb.append(leadingSpaces)

                var tag = ""
                if (span.isBold && span.isItalic) tag = "***"
                else if (span.isBold) tag = "**"
                else if (span.isItalic) tag = "*"

                sb.append(tag).append(trimmedText).append(tag)
                sb.append(trailingSpaces)
            }

            var isParagraphBreak = false

            if (prefix.isNotEmpty() || isList) {
                isParagraphBreak = true
            } else if (lineLen < wrapThreshold) {
                isParagraphBreak = true
            } else if (trimmedRaw.matches(Regex(".*[.!?\"'”’;:*]$"))) {
                isParagraphBreak = true
            } else {
                val nextLine = lines.subList(i + 1, lines.size).firstOrNull { it.spans.isNotEmpty() }
                if (nextLine != null) {
                    val nextRaw = nextLine.spans.joinToString("") { it.text }.trimStart()
                    if (nextRaw.startsWith("\"") || nextRaw.startsWith("“") || nextRaw.startsWith("-")) {
                        isParagraphBreak = true
                    }
                }
            }

            if (isParagraphBreak) {
                sb.append("\n\n")
            } else {
                sb.append("\n")
            }
        }

        return sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}