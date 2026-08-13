package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.aryan.reader.shared.pdf.SharedPdfReflowHtml
import com.aryan.reader.shared.pdf.SharedPdfReflowImageElement
import com.aryan.reader.shared.pdf.SharedPdfReflowPage
import com.aryan.reader.shared.pdf.SharedPdfReflowPageElement
import com.aryan.reader.shared.pdf.SharedPdfReflowTextElement
import com.aryan.reader.shared.pdf.buildReaderReflowTextLines
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "PdfToHtml"

object PdfToHtmlGenerator {
    suspend fun generateHtmlFile(
        context: Context,
        pdfUri: Uri,
        destFile: File,
        startPage: Int = 1,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        Timber.tag(TAG).d("generateHtmlFile START | uri=$pdfUri | startPage=$startPage")
        val pdfiumCore = PdfiumCoreProvider.core
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: run {
            Timber.tag(TAG).e("Failed to open ParcelFileDescriptor")
            return@withContext false
        }

        try {
            val document = PdfiumEngineProvider.withPdfium { pdfiumCore.newDocument(pfd) }
            val totalPages = document.getPageCount()
            val repeatingHeadersAndFooters = detectRepeatingHeaderFooter(document, totalPages)
            destFile.bufferedWriter().use { writer ->
                writer.write(SharedPdfReflowHtml.buildGlobalHtmlHeader())
                for (pageIndex in (startPage - 1) until totalPages) {
                    if (pageIndex > startPage - 1) writer.write("\n<page-break></page-break>\n")
                    writer.write(extractPageHtml(document, pageIndex, repeatingHeadersAndFooters))
                    if (pageIndex % 5 == 0 || pageIndex == totalPages - 1) {
                        onProgress((pageIndex + 1).toFloat() / totalPages.toFloat())
                    }
                }
                writer.write(SharedPdfReflowHtml.buildGlobalHtmlFooter())
            }
            PdfiumEngineProvider.withPdfium { document.close() }
            pfd.close()
            Timber.tag(TAG).d("generateHtmlFile SUCCESS | ${System.currentTimeMillis() - startedAt}ms")
            true
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to generate HTML from PDF")
            try { pfd.close() } catch (_: Exception) { }
            false
        }
    }

    private suspend fun extractPageHtml(
        document: PdfDocumentKt,
        pageIndex: Int,
        repeatingHeadersAndFooters: Set<String>
    ): String = try {
        PdfiumEngineProvider.withPdfium {
            document.openPage(pageIndex)?.use { page ->
                page.openTextPage().use { textPage ->
                    val pageNumber = pageIndex + 1
                    val charCount = textPage.textPageCountChars()
                    val pagePointer = getNativePointer(page)
                    val textPagePointer = getNativePointer(textPage)
                    val nativeAvailable = NativePdfiumBridge.ensureLoaded()
                    val images = extractImages(pagePointer, pageIndex, nativeAvailable)

                    if (charCount <= 0) {
                        return@use SharedPdfReflowHtml.buildPageHtml(
                            SharedPdfReflowPage(pageNumber, images),
                            repeatingHeadersAndFooters
                        )
                    }

                    val rawText = textPage.textPageGetText(0, charCount) ?: ""
                    val actualCount = minOf(charCount, rawText.length)
                    val sizes = if (nativeAvailable) {
                        NativePdfiumBridge.getPageFontSizes(textPagePointer, actualCount)
                    } else null
                    val weights = if (nativeAvailable) {
                        NativePdfiumBridge.getPageFontWeights(textPagePointer, actualCount)
                    } else null
                    val flags = if (nativeAvailable) {
                        NativePdfiumBridge.getPageFontFlags(textPagePointer, actualCount)
                    } else null
                    val charBoxes = if (nativeAvailable) {
                        NativePdfiumBridge.getPageCharBoxes(textPagePointer, actualCount)
                    } else null

                    if (sizes == null || weights == null || flags == null) {
                        return@use SharedPdfReflowHtml.buildFallbackPageSection(pageNumber, rawText)
                    }

                    val textElements = buildReaderReflowTextLines(
                        rawText = rawText,
                        charCount = actualCount,
                        sizeAt = sizes::get,
                        weightAt = weights::get,
                        flagsAt = flags::get,
                        boxTopYAt = { index -> charBoxes?.getOrNull(index * 4 + 1) }
                    ).map(::SharedPdfReflowTextElement)

                    SharedPdfReflowHtml.buildPageHtml(
                        page = SharedPdfReflowPage(
                            pageNumber = pageNumber,
                            elements = mergePageElements(textElements, images)
                        ),
                        headerFooterStrings = repeatingHeadersAndFooters
                    )
                }
            } ?: SharedPdfReflowHtml.buildEmptyPageSection(pageIndex + 1)
        }
    } catch (error: Throwable) {
        Timber.tag(TAG).w(error, "Error extracting page $pageIndex")
        SharedPdfReflowHtml.buildEmptyPageSection(pageIndex + 1)
    }

    private fun extractImages(
        pagePointer: Long,
        pageIndex: Int,
        nativeAvailable: Boolean
    ): List<SharedPdfReflowImageElement> {
        if (!nativeAvailable) return emptyList()
        val images = mutableListOf<SharedPdfReflowImageElement>()
        val objectCount = NativePdfiumBridge.getPageObjectCount(pagePointer)
        for (objectIndex in 0 until objectCount) {
            if (NativePdfiumBridge.getPageObjectType(pagePointer, objectIndex) != 3) continue
            val bounds = FloatArray(4)
            if (!NativePdfiumBridge.getPageObjectBoundingBox(pagePointer, objectIndex, bounds)) continue
            val dimensions = IntArray(2)
            val pixels = NativePdfiumBridge.extractImagePixels(pagePointer, objectIndex, dimensions)
            if (pixels == null || dimensions[0] <= 0 || dimensions[1] <= 0) continue
            try {
                val bitmap = Bitmap.createBitmap(
                    pixels,
                    dimensions[0],
                    dimensions[1],
                    Bitmap.Config.ARGB_8888
                )
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bytes)
                images += SharedPdfReflowImageElement(
                    base64Data = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP),
                    width = dimensions[0],
                    height = dimensions[1],
                    yPos = bounds[3]
                )
                bitmap.recycle()
            } catch (_: Exception) {
                Timber.tag(TAG).w("Failed to process image $objectIndex on page $pageIndex")
            }
        }
        return images.sortedByDescending { it.yPos }
    }

    private fun mergePageElements(
        textElements: List<SharedPdfReflowTextElement>,
        images: List<SharedPdfReflowImageElement>
    ): List<SharedPdfReflowPageElement> {
        val elements = mutableListOf<SharedPdfReflowPageElement>()
        var imageIndex = 0
        for (textElement in textElements) {
            while (imageIndex < images.size && images[imageIndex].yPos >= textElement.yPos) {
                elements += images[imageIndex++]
            }
            elements += textElement
        }
        while (imageIndex < images.size) elements += images[imageIndex++]
        return elements
    }

    private suspend fun detectRepeatingHeaderFooter(
        document: PdfDocumentKt,
        totalPages: Int
    ): Set<String> = withContext(Dispatchers.Default) {
        if (totalPages < 5) return@withContext emptySet()
        val step = maxOf(1, totalPages / 8)
        val sampleLines = mutableListOf<List<String>>()
        for (pageIndex in (0 until totalPages).filter { it % step == 0 }.take(8)) {
            try {
                PdfiumEngineProvider.withPdfium {
                    document.openPage(pageIndex)?.use { page ->
                        page.openTextPage().use { textPage ->
                            val charCount = textPage.textPageCountChars()
                            if (charCount > 0) {
                                textPage.textPageGetText(0, charCount)?.let { sampleLines += it.split('\n') }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Header/footer sampling failed for page $pageIndex")
            }
        }
        SharedPdfReflowHtml.detectRepeatingHeaderFooter(sampleLines)
    }

    private fun getNativePointer(value: Any): Long {
        for (fieldName in listOf("pagePtr", "mNativePage", "page")) {
            try {
                val field = value.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                val nestedValue = field.get(value)
                if (nestedValue is Long && nestedValue != 0L) return nestedValue
                if (nestedValue != null && nestedValue !is Long) {
                    val nestedPointer = getNativePointer(nestedValue)
                    if (nestedPointer != 0L) return nestedPointer
                }
            } catch (_: Exception) { }
        }
        return 0L
    }
}
