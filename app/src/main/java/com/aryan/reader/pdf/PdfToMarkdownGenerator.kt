// PdfToMarkdownGenerator.kt
package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object PdfToMarkdownGenerator {

    /**
     * Generates a "Skeleton" EpubBook immediately and launches a background job
     * to fill in the content (text/images) page by page.
     */
    suspend fun generateReflowBook(
        context: Context,
        bookId: String,
        pdfUri: Uri,
        scope: CoroutineScope, // Passed from ViewModel to keep processing alive
        startPage: Int = 0, // The page the user is currently looking at
        onProgress: (Int, Int) -> Unit
    ): EpubBook = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "reflow_cache/$bookId")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val imagesDir = File(context.cacheDir, "reflow_images/$bookId")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        var title = "Reflow Book"
        var author = ""
        var totalPages = 0
        val chapters = mutableListOf<EpubChapter>()

        try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream, MemoryUsageSetting.setupMixed(1024 * 1024)).use { doc ->
                    val info = doc.documentInformation
                    title = info.title ?: "Reflow View"
                    author = info.author ?: ""
                    totalPages = doc.numberOfPages
                    Timber.tag("ReflowPaginationDiag").d("generateReflowBook: Initial scan complete. totalPages=$totalPages, title='$title'")
                }
            }
        } catch (e: Exception) {
            Timber.tag("ReflowPaginationDiag").e(e, "generateReflowBook: Failed to read PDF metadata")
            return@withContext EpubBook(
                fileName = "Error", title = "Error", author = "", language = "en",
                coverImage = null, chapters = emptyList(), css = emptyMap()
            )
        }

        @Suppress("EmptyRange") for (i in 0 until totalPages) {
            val pageFileName = "page_$i.html"
            val pageFile = File(cacheDir, pageFileName)

            if (!pageFile.exists()) {
                val placeholderHtml = createSkeletonHtml(i + 1)
                pageFile.writeText(placeholderHtml)
            }

            chapters.add(EpubChapter(
                chapterId = "${bookId}_page_$i",
                absPath = pageFileName,
                title = "Page ${i + 1}",
                htmlFilePath = pageFileName,
                plainTextContent = "",
                htmlContent = "",
                depth = 0,
                isInToc = true
            ))
            Timber.tag("ReflowPaginationDiag").d("generateReflowBook: Created skeleton chapter $i: $pageFileName")
        }

        scope.launch(Dispatchers.IO) {
            processPdfContent(context, pdfUri, cacheDir, imagesDir, totalPages, startPage, onProgress)
        }

        Timber.tag("ReflowPaginationDiag").d("generateReflowBook: Returning EpubBook with ${chapters.size} chapters.")

        return@withContext EpubBook(
            fileName = bookId,
            title = title,
            author = author,
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

    private fun createSkeletonHtml(pageNum: Int): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Page $pageNum</title>
                <style>
                    body { font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; color: #666; }
                    .loader { text-align: center; }
                </style>
            </head>
            <body>
                <div class="loader">
                    <h3>Processing Page $pageNum...</h3>
                    <p>Please wait.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private suspend fun processPdfContent(
        context: Context,
        pdfUri: Uri,
        cacheDir: File,
        imagesDir: File,
        totalPages: Int,
        startPage: Int, // Priority page
        onProgress: (Int, Int) -> Unit
    ) {
        try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                // OPTIMIZATION: Use Mixed memory (50MB RAM buffer) before swapping to disk.
                // This prevents the "Unusable" slowness caused by temp-file-only swapping.
                val document = PDDocument.load(inputStream, MemoryUsageSetting.setupMixed(50 * 1024 * 1024))

                document.use { doc ->
                    val stripper = SmartReflowStripper(imagesDir)

                    // Priority Processing: Process the page the user is looking at FIRST, then neighbors, then the rest.
                    val pageOrder = mutableListOf<Int>()
                    pageOrder.add(startPage)
                    // Add surrounding pages (e.g., +1, +2, -1)
                    for (i in 1..2) {
                        if (startPage + i < totalPages) pageOrder.add(startPage + i)
                        if (startPage - i >= 0) pageOrder.add(startPage - i)
                    }
                    // Add the rest linearly
                    for (i in 0 until totalPages) {
                        if (i !in pageOrder) pageOrder.add(i)
                    }

                    var processedCount = 0

                    for (i in pageOrder) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) break

                        if (i !in 0..<totalPages) continue

                        stripper.startPage = i + 1
                        stripper.endPage = i + 1

                        val rawHtml = stripper.getText(doc)
                        val cleanedHtml = processSmartFormatting(rawHtml)

                        val pageFileName = "page_$i.html"
                        val pageFile = File(cacheDir, pageFileName)

                        val fullHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <title>Page ${i + 1}</title>
                                <style>
                                    body { font-family: sans-serif; line-height: 1.6; padding: 1em; color: #1a1a1a; }
                                    p { margin-bottom: 1em; text-align: justify; }
                                    h1, h2, h3 { color: #333; margin-top: 1.5em; font-weight: bold; }
                                    img { max-width: 100%; height: auto; display: block; margin: 1em auto; border-radius: 4px; }
                                    .page-num { font-size: 0.75em; color: #888; text-align: center; margin-top: 2em; border-top: 1px solid #eee; padding-top: 1em;}
                                </style>
                            </head>
                            <body>
                                $cleanedHtml
                                <div class="page-num">${i + 1}</div>
                            </body>
                            </html>
                        """.trimIndent()

                        // Overwrite the skeleton file
                        pageFile.writeText(fullHtml)

                        processedCount++
                        // Update progress less frequently to save UI churn
                        if (processedCount % 3 == 0 || processedCount == totalPages) {
                            // We calculate progress based on count, not index
                            onProgress(processedCount, totalPages)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Background Reflow Generation Failed")
        }
    }

    private fun processSmartFormatting(html: String): String {
        var processed = html
        // Remove hyphens at end of lines
        processed = processed.replace(Regex("-\\s*\\n\\s*(?=[a-z])"), "")
        // Join broken lines
        processed = processed.replace(Regex("([^.?!:\">])\\n(?=[^<])"), "$1 ")
        return processed
    }

    private class SmartReflowStripper(
        private val imagesDir: File
    ) : PDFTextStripper() {

        private var currentPageBaseFontSize = 0f

        init {
            sortByPosition = true
            // OPTIMIZATION: Don't process overlapping text (speed boost)
            suppressDuplicateOverlappingText = true
            paragraphStart = "<p>"
            paragraphEnd = "</p>\n"
        }

        override fun processPage(page: PDPage) {
            extractImages(page)
            super.processPage(page)
        }

        private fun extractImages(page: PDPage) {
            try {
                val resources = page.resources ?: return
                var imageCount = 0

                resources.xObjectNames.forEach { xObjectName ->
                    val xObject = resources.getXObject(xObjectName)
                    if (xObject is PDImageXObject) {
                        val imageBitmap = xObject.image

                        // OPTIMIZATION: Filter out tiny icons or huge background junk
                        if (imageBitmap != null && imageBitmap.width > 80 && imageBitmap.height > 80) {
                            val fileName = "img_${currentPageNo}_${imageCount}.jpg" // Changed to JPG
                            val imageFile = File(imagesDir, fileName)

                            // OPTIMIZATION: Use JPEG instead of PNG.
                            // PNG compression is CPU intensive. JPEG is hardware accelerated and fast.
                            // Quality 80 is sufficient for reading.
                            FileOutputStream(imageFile).use { out ->
                                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                            }
                            imageCount++
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w("Error extracting images: ${e.message}")
            }
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

            val isHeader = fontSize > currentPageBaseFontSize * 1.2
            val isBigHeader = fontSize > currentPageBaseFontSize * 1.5

            val sb = StringBuilder()

            if (isBigHeader) sb.append("<h2>")
            else if (isHeader) sb.append("<h3>")

            if (isBold && !isHeader) sb.append("<b>")
            if (isItalic) sb.append("<i>")

            text.forEach { char ->
                when(char) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    else -> if (char.code >= 32) sb.append(char)
                }
            }

            if (isItalic) sb.append("</i>")
            if (isBold && !isHeader) sb.append("</b>")

            if (isBigHeader) sb.append("</h2>\n")
            else if (isHeader) sb.append("</h3>\n")

            writeString(sb.toString())
        }

        override fun startPage(page: PDPage?) {
            currentPageBaseFontSize = 0f
            super.startPage(page)
        }
    }
}