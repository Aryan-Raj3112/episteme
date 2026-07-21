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
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfZoomTileRequest
import com.aryan.reader.shared.pdf.planPdfZoomTiles
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
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
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_RenderPageBitmap
import cnames.structs.fpdf_page_t__
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.get
import kotlinx.cinterop.plus
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.memcpy
import kotlin.math.roundToInt

@Composable
internal actual fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int,
    zoomScale: Float
): SharedMobilePdfPageRender {
    var render by remember(book.path, pageIndex) { mutableStateOf(SharedMobilePdfPageRender()) }

    LaunchedEffect(book.path, pageIndex) {
        render = IosPdfiumRenderer.render(book.path, pageIndex, 1f)
    }

    return render
}

@Composable
internal actual fun rememberSharedMobilePdfTileRenders(
    book: BookItem,
    pageIndex: Int,
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: PdfPageBounds?
): List<SharedMobilePdfTileRender> {
    val requests = remember(pageAspectRatio, zoomScale, visibleBounds) {
        visibleBounds?.let { planPdfZoomTiles(pageAspectRatio, zoomScale, it) }.orEmpty()
    }
    var tiles by remember(book.path, pageIndex) { mutableStateOf<List<SharedMobilePdfTileRender>>(emptyList()) }
    LaunchedEffect(book.path, pageIndex, requests) {
        if (requests.isEmpty()) {
            tiles = emptyList()
            return@LaunchedEffect
        }
        val requestedIds = requests.mapTo(mutableSetOf()) { it.id }
        val renderScale = requests.first().renderScale
        val retained = tiles.filter { it.request.id in requestedIds && it.request.renderScale == renderScale }
        val retainedIds = retained.mapTo(mutableSetOf()) { it.request.id }
        val missing = requests.filterNot { it.id in retainedIds }
        if (missing.isEmpty()) {
            tiles = retained
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(60)
        tiles = retained + IosPdfiumRenderer.renderTiles(book.path, pageIndex, missing)
    }
    val activeScale = requests.firstOrNull()?.renderScale
    val activeIds = requests.mapTo(mutableSetOf()) { it.id }
    return tiles.filter { it.request.renderScale == activeScale && it.request.id in activeIds }
}

private object IosPdfiumRenderer {
    suspend fun renderTiles(
        path: String?,
        pageIndex: Int,
        requests: List<PdfZoomTileRequest>
    ): List<SharedMobilePdfTileRender> = IosPdfiumRuntime.mutex.withLock {
        val resolvedPath = path.resolvedIosPdfPath() ?: return@withLock emptyList()
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) return@withLock emptyList()
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(resolvedPath, null) ?: return@withLock emptyList()
        try {
            val count = FPDF_GetPageCount(document).coerceAtLeast(1)
            val page = FPDF_LoadPage(document, pageIndex.coerceIn(0, count - 1)) ?: return@withLock emptyList()
            try {
                requests.mapNotNull { request -> renderTile(page, request) }
            } finally {
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }

    private fun renderTile(
        page: kotlinx.cinterop.CPointer<fpdf_page_t__>,
        request: PdfZoomTileRequest
    ): SharedMobilePdfTileRender? {
        val bitmap = FPDFBitmap_Create(request.widthPx, request.heightPx, 1) ?: return null
        return try {
            FPDFBitmap_FillRect(bitmap, 0, 0, request.widthPx, request.heightPx, 0xFFFFFFFFu)
            FPDF_RenderPageBitmap(
                bitmap,
                page,
                -request.leftPx,
                -request.topPx,
                request.fullWidthPx,
                request.fullHeightPx,
                0,
                0
            )
            val buffer = FPDFBitmap_GetBuffer(bitmap) ?: return null
            val stride = FPDFBitmap_GetStride(bitmap).coerceAtLeast(request.widthPx * 4)
            val bytes = ByteArray(stride * request.heightPx)
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), buffer, bytes.size.convert()) }
            val image = Image.makeRaster(
                ImageInfo(request.widthPx, request.heightPx, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                bytes,
                stride
            ).toComposeImageBitmap()
            SharedMobilePdfTileRender(request, image)
        } finally {
            FPDFBitmap_Destroy(bitmap)
        }
    }

    suspend fun render(path: String?, pageIndex: Int, zoomScale: Float): SharedMobilePdfPageRender =
        IosPdfiumRuntime.mutex.withLock {
        val resolvedPath = path.resolvedIosPdfPath()
        if (resolvedPath.isNullOrBlank()) {
            return SharedMobilePdfPageRender(errorMessage = "PDF path is unavailable")
        }
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) {
            return SharedMobilePdfPageRender(errorMessage = "PDF file is missing: $resolvedPath")
        }

        IosPdfiumRuntime.ensureInitialized()

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
                    val targetSide = MaxRenderedPageSidePx
                    val scale = targetSide / maxOf(pageWidth, pageHeight)
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
}

private fun String?.resolvedIosPdfPath(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!value.startsWith("file://")) return value
    return NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
}

private const val MaxRenderedPageSidePx = 1600f
private const val SearchPreviewRadius = 42
private const val MaxSearchResults = 500
