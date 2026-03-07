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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object PdfToMarkdownGenerator {

    suspend fun generateMarkdownFile(
        context: Context,
        pdfUri: Uri,
        destFile: File,
        startPage: Int = 1, // Default to 1
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream, MemoryUsageSetting.setupMixed(50 * 1024 * 1024)).use { doc ->
                    val stripper = MarkdownStripper()
                    val totalPages = doc.numberOfPages

                    val isAppending = startPage > 1

                    if (isAppending && destFile.exists()) {
                        destFile.appendText("\n\n---\n\n")
                    }

                    FileOutputStream(destFile, isAppending).bufferedWriter().use { writer ->
                        for (i in startPage..totalPages) {
                            if (!isActive) return@use

                            stripper.startPage = i
                            stripper.endPage = i

                            val pageText = stripper.getText(doc)
                            writer.write(pageText)

                            writer.write("\n\n---\n\n")

                            if (i % 5 == 0) writer.flush()

                            onProgress(i.toFloat() / totalPages.toFloat())
                        }
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate Markdown from PDF")
            return@withContext false
        }
    }

    private class MarkdownStripper : PDFTextStripper() {
        private var currentPageBaseFontSize = 0f

        init {
            sortByPosition = true
            suppressDuplicateOverlappingText = true
            // Basic Markdown line breaks
            paragraphStart = ""
            paragraphEnd = "\n\n"
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

            // Header detection
            val isHeader = fontSize > currentPageBaseFontSize * 1.2
            val isBigHeader = fontSize > currentPageBaseFontSize * 1.5

            val sb = StringBuilder()

            if (isBigHeader) sb.append("## ")
            else if (isHeader) sb.append("### ")

            if (isBold && !isHeader) sb.append("**")
            if (isItalic) sb.append("*")

            // Clean text
            text.forEach { char ->
                // Escape Markdown characters if needed, or just write them.
                // For simple reading, raw usually works, but let's be safe with basic chars.
                sb.append(char)
            }

            if (isItalic) sb.append("*")
            if (isBold && !isHeader) sb.append("**")

            writeString(sb.toString())
        }

        override fun startPage(page: PDPage?) {
            currentPageBaseFontSize = 0f
            super.startPage(page)
        }
    }
}