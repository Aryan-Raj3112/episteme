// PdfToMarkdownGenerator.kt
package com.aryan.reader.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

object PdfToMarkdownGenerator {

    // Unique delimiter to split pages reliably
    const val PAGE_DELIMITER = "\n\n[[PAGE_BREAK]]\n\n"

    suspend fun generateMarkdownFile(
        context: Context,
        pdfUri: Uri,
        destFile: File,
        startPage: Int = 1,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                // Setup mixed memory usage to handle larger files without OOM
                PDDocument.load(inputStream, MemoryUsageSetting.setupMixed(50 * 1024 * 1024)).use { doc ->
                    val totalPages = doc.numberOfPages

                    // Configure stripper for linear processing
                    val stripper = MarkdownStripper(totalPages, onProgress)
                    stripper.startPage = startPage
                    stripper.endPage = totalPages

                    // Write directly to file stream (O(N) complexity)
                    destFile.bufferedWriter().use { writer ->
                        stripper.writeText(doc, writer)
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate Markdown from PDF")
            return@withContext false
        }
    }

    private class MarkdownStripper(
        private val totalPages: Int,
        private val onProgress: (Float) -> Unit
    ) : PDFTextStripper() {
        private var currentPageBaseFontSize = 0f

        init {
            sortByPosition = true
            suppressDuplicateOverlappingText = true
            paragraphStart = ""
            paragraphEnd = "\n\n"
        }

        // Override endPage to update progress and insert delimiter
        override fun endPage(page: PDPage?) {
            super.endPage(page)

            try {
                // Insert our custom delimiter so importer can split chapters
                output.write(PAGE_DELIMITER)

                // Update progress
                val current = currentPageNo // inherited from PDFTextStripper
                if (totalPages > 0) {
                    onProgress(current.toFloat() / totalPages.toFloat())
                }
            } catch (e: Exception) {
                Timber.e(e, "Error writing page delimiter")
            }
        }

        override fun startPage(page: PDPage?) {
            currentPageBaseFontSize = 0f
            super.startPage(page)
        }

        private fun calculateBaseFontSize(textPositions: List<TextPosition>) {
            val sizeCounts = mutableMapOf<Float, Int>()
            textPositions.forEach { pos ->
                val size = pos.fontSizeInPt.roundToInt().toFloat()
                sizeCounts[size] = (sizeCounts[size] ?: 0) + 1
            }
            currentPageBaseFontSize = sizeCounts.maxByOrNull { it.value }?.key ?: 12f
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return

            if (currentPageBaseFontSize == 0f) {
                calculateBaseFontSize(textPositions)
            }

            val firstPos = textPositions[0]
            val fontSize = firstPos.fontSizeInPt
            val fontDescriptor = firstPos.font?.fontDescriptor

            val isBold = fontDescriptor?.isForceBold == true ||
                    (firstPos.font?.name?.contains("Bold", ignoreCase = true) == true)
            val isItalic = fontDescriptor?.isItalic == true ||
                    (firstPos.font?.name?.contains("Italic", ignoreCase = true) == true)

            // Header detection logic
            val isHeader = fontSize > currentPageBaseFontSize * 1.2
            val isBigHeader = fontSize > currentPageBaseFontSize * 1.5

            val sb = StringBuilder()

            if (isBigHeader) sb.append("## ")
            else if (isHeader) sb.append("### ")

            if (isBold && !isHeader) sb.append("**")
            if (isItalic) sb.append("*")

            text.forEach { char -> sb.append(char) }

            if (isItalic) sb.append("*")
            if (isBold && !isHeader) sb.append("**")

            writeString(sb.toString())
        }
    }
}