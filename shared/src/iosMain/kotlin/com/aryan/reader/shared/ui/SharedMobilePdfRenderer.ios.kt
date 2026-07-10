@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Create
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Destroy
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_FillRect
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetBuffer
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetStride
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_InitLibrary
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_RenderPageBitmap
import com.aryan.reader.shared.pdfium.c.FPDFText_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDFText_CountChars
import com.aryan.reader.shared.pdfium.c.FPDFText_CountRects
import com.aryan.reader.shared.pdfium.c.FPDFText_GetText
import com.aryan.reader.shared.pdfium.c.FPDFText_GetRect
import com.aryan.reader.shared.pdfium.c.FPDFText_LoadPage
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.PdfPageBounds
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.get
import kotlinx.cinterop.plus
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.memcpy
import kotlin.math.roundToInt

internal actual suspend fun searchSharedMobilePdf(
    book: BookItem,
    query: String
): List<SharedPdfSearchResult> = IosPdfiumRenderer.search(book.path, query)

@Composable
internal actual fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int
): SharedMobilePdfPageRender {
    var render by remember(book.path, pageIndex) { mutableStateOf(SharedMobilePdfPageRender()) }

    LaunchedEffect(book.path, pageIndex) {
        render = IosPdfiumRenderer.render(book.path, pageIndex)
    }

    return render
}

private object IosPdfiumRenderer {
    private var initialized = false

    fun render(path: String?, pageIndex: Int): SharedMobilePdfPageRender {
        val resolvedPath = path.resolvedIosPdfPath()
        if (resolvedPath.isNullOrBlank()) {
            return SharedMobilePdfPageRender(errorMessage = "PDF path is unavailable")
        }
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) {
            return SharedMobilePdfPageRender(errorMessage = "PDF file is missing: $resolvedPath")
        }

        ensureInitialized()

        val document = FPDF_LoadDocument(resolvedPath, null)
            ?: return SharedMobilePdfPageRender(errorMessage = "Pdfium could not open this PDF: $resolvedPath")
        return try {
                val pageCount = FPDF_GetPageCount(document).coerceAtLeast(1)
                val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
                val page = FPDF_LoadPage(document, safePageIndex)
                    ?: return SharedMobilePdfPageRender(
                        pageCount = pageCount,
                        errorMessage = "Pdfium could not load page ${safePageIndex + 1}"
                    )
                try {
                    val pageWidth = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                    val pageHeight = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                    val aspectRatio = (pageWidth / pageHeight).coerceIn(0.1f, 10f)
                    val scale = MaxRenderedPageSidePx / maxOf(pageWidth, pageHeight)
                    val bitmapWidth = (pageWidth * scale).roundToInt().coerceAtLeast(1)
                    val bitmapHeight = (pageHeight * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = FPDFBitmap_Create(bitmapWidth, bitmapHeight, 1)
                        ?: return SharedMobilePdfPageRender(
                            pageCount = pageCount,
                            errorMessage = "Pdfium could not allocate a page bitmap"
                        )
                    try {
                        FPDFBitmap_FillRect(bitmap, 0, 0, bitmapWidth, bitmapHeight, 0xFFFFFFFFu)
                        FPDF_RenderPageBitmap(
                            bitmap,
                            page,
                            0,
                            0,
                            bitmapWidth,
                            bitmapHeight,
                            0,
                            0
                        )

                        val buffer = FPDFBitmap_GetBuffer(bitmap)
                            ?: return SharedMobilePdfPageRender(
                                pageCount = pageCount,
                                errorMessage = "Pdfium returned an empty page buffer"
                            )
                        val stride = FPDFBitmap_GetStride(bitmap).coerceAtLeast(bitmapWidth * 4)
                        val byteCount = stride * bitmapHeight
                        val bytes = ByteArray(byteCount)
                        bytes.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), buffer, byteCount.convert())
                        }
                        val image = Image.makeRaster(
                            ImageInfo(
                                width = bitmapWidth,
                                height = bitmapHeight,
                                colorType = ColorType.BGRA_8888,
                                alphaType = ColorAlphaType.OPAQUE
                            ),
                            bytes,
                            stride
                        ).toComposeImageBitmap()
                        SharedMobilePdfPageRender(
                            pageCount = pageCount,
                            aspectRatio = aspectRatio,
                            bitmap = image
                        )
                    } finally {
                        FPDFBitmap_Destroy(bitmap)
                    }
                } finally {
                    FPDF_ClosePage(page)
                }
            } finally {
                FPDF_CloseDocument(document)
            }
    }

    fun search(path: String?, rawQuery: String): List<SharedPdfSearchResult> {
        val query = rawQuery.trim()
        if (query.length < 2) return emptyList()
        val resolvedPath = path.resolvedIosPdfPath() ?: return emptyList()
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) return emptyList()
        ensureInitialized()
        val document = FPDF_LoadDocument(resolvedPath, null) ?: return emptyList()
        return try {
            val results = mutableListOf<SharedPdfSearchResult>()
            val pageCount = FPDF_GetPageCount(document).coerceAtLeast(0)
            pageLoop@ for (pageIndex in 0 until pageCount) {
                val page = FPDF_LoadPage(document, pageIndex) ?: continue
                try {
                    val textPage = FPDFText_LoadPage(page) ?: continue
                    try {
                        val charCount = FPDFText_CountChars(textPage).coerceAtLeast(0)
                        val textBuffer = UShortArray(charCount + 1)
                        val copied = if (charCount == 0) 0 else textBuffer.usePinned { pinned ->
                            FPDFText_GetText(textPage, 0, charCount, pinned.addressOf(0))
                        }.coerceIn(0, charCount)
                        val text = buildString(copied) {
                            for (index in 0 until copied) {
                                val value = textBuffer[index]
                                if (value != 0.toUShort()) append(value.toInt().toChar())
                            }
                        }
                        var fromIndex = 0
                        while (fromIndex < text.length) {
                            val matchIndex = text.indexOf(query, startIndex = fromIndex, ignoreCase = true)
                            if (matchIndex < 0) break
                            val previewStart = (matchIndex - SearchPreviewRadius).coerceAtLeast(0)
                            val previewEnd = (matchIndex + query.length + SearchPreviewRadius).coerceAtMost(text.length)
                            val pageWidth = FPDF_GetPageWidthF(page).coerceAtLeast(1f).toDouble()
                            val pageHeight = FPDF_GetPageHeightF(page).coerceAtLeast(1f).toDouble()
                            val matchBounds = memScoped {
                                val rect = allocArray<DoubleVar>(4)
                                buildList {
                                    val rectCount = FPDFText_CountRects(textPage, matchIndex, query.length).coerceAtLeast(0)
                                    for (rectIndex in 0 until rectCount) {
                                        val succeeded = FPDFText_GetRect(
                                            textPage,
                                            rectIndex,
                                            rect,
                                            rect + 1,
                                            rect + 2,
                                            rect + 3
                                        )
                                        if (succeeded != 0) {
                                            add(
                                                PdfPageBounds(
                                                    left = (rect[0] / pageWidth).toFloat().coerceIn(0f, 1f),
                                                    top = (1.0 - rect[1] / pageHeight).toFloat().coerceIn(0f, 1f),
                                                    right = (rect[2] / pageWidth).toFloat().coerceIn(0f, 1f),
                                                    bottom = (1.0 - rect[3] / pageHeight).toFloat().coerceIn(0f, 1f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            results += SharedPdfSearchResult(
                                pageIndex = pageIndex,
                                preview = text.substring(previewStart, previewEnd)
                                    .replace(Regex("\\s+"), " ")
                                    .trim(),
                                matchIndex = matchIndex,
                                matchLength = query.length,
                                boundsList = matchBounds
                            )
                            if (results.size >= MaxSearchResults) break@pageLoop
                            fromIndex = matchIndex + query.length.coerceAtLeast(1)
                        }
                    } finally {
                        FPDFText_ClosePage(textPage)
                    }
                } finally {
                    FPDF_ClosePage(page)
                }
            }
            results
        } finally {
            FPDF_CloseDocument(document)
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            FPDF_InitLibrary()
            initialized = true
        }
    }
}

private fun String?.resolvedIosPdfPath(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!value.startsWith("file://")) return value
    return NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
}

private const val MaxRenderedPageSidePx = 1600f
private const val SearchPreviewRadius = 42
private const val MaxSearchResults = 500
