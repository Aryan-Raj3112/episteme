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

                    synchronized(PdfiumCore.lock) {
                        sizes = NativePdfiumBridge.getPageFontSizes(rawPtr, actualCount)
                        weights = NativePdfiumBridge.getPageFontWeights(rawPtr, actualCount)
                    }

                    if (sizes == null || weights == null) {
                        return@use text
                    }

                    buildMarkdown(text, sizes, weights, actualCount)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error extracting page $pageIdx")
            ""
        }
    }

    private fun buildMarkdown(text: String, sizes: FloatArray, weights: IntArray, count: Int): String {
        if (count == 0) return ""

        val sizeFrequency = HashMap<Int, Int>()
        for (i in 0 until count) {
            val s = sizes[i].roundToInt()
            sizeFrequency[s] = (sizeFrequency[s] ?: 0) + 1
        }
        val baseSize = sizeFrequency.maxByOrNull { it.value }?.key ?: 12

        val sb = StringBuilder()
        var isBold = false
        var lineStart = true

        for (i in 0 until count) {
            val c = text[i]
            val size = sizes[i]
            val weight = weights[i]

            if (c == '\u0000') continue

            if (c == '\n' || c == '\r') {
                if (isBold) {
                    sb.append("**")
                    isBold = false
                }
                sb.append(c)
                lineStart = true
                continue
            }

            if (lineStart && !c.isWhitespace()) {
                val charBigHeader = size > baseSize * 1.5f
                val charHeader = size > baseSize * 1.2f

                if (charBigHeader) sb.append("## ")
                else if (charHeader) sb.append("### ")

                lineStart = false
            }

            val charBold = weight > 600 && !c.isWhitespace()
            if (charBold && !isBold) {
                sb.append("**")
                isBold = true
            } else if (!charBold && isBold && !c.isWhitespace()) {
                sb.append("**")
                isBold = false
            }

            sb.append(c)
        }

        if (isBold) sb.append("**")

        return sb.toString().replace("** **", " ")
    }
}