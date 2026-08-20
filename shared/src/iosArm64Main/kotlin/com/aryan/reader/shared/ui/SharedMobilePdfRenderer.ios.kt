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
import com.aryan.reader.shared.pdf.PdfReverseColorMode
import com.aryan.reader.shared.pdf.PdfZoomTileRequest
import com.aryan.reader.shared.pdf.PDF_ZOOM_RENDER_SETTLE_MILLIS
import com.aryan.reader.shared.pdf.PDF_ZOOM_TILE_CACHE_MAX_BYTES
import com.aryan.reader.shared.pdf.PdfTileLruCache
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
import com.aryan.reader.shared.pdfium.c.FPDF_GetLastError
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_RenderPageBitmap
import cnames.structs.fpdf_page_t__
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.get
import kotlinx.cinterop.plus
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRequest
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.posix.memcpy
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

@Composable
internal actual fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int,
    zoomScale: Float,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): SharedMobilePdfPageRender {
    var render by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) { mutableStateOf(SharedMobilePdfPageRender()) }

    LaunchedEffect(book.path, pageIndex, password, reverseColorMode, preserveImageColors) {
        render = IosPdfiumRenderer.render(book.path, pageIndex, 1f, password, reverseColorMode, preserveImageColors)
    }

    return render
}

@Composable
internal actual fun rememberSharedMobilePdfPageThumbnail(
    book: BookItem,
    pageIndex: Int,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): SharedMobilePdfPageThumbnail {
    var thumbnail by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) {
        mutableStateOf<SharedMobilePdfPageThumbnail?>(IosPdfThumbnailCache.get(book.path, pageIndex, password, reverseColorMode, preserveImageColors))
    }

    LaunchedEffect(book.path, pageIndex, password, reverseColorMode, preserveImageColors) {
        if (thumbnail == null) {
            thumbnail = IosPdfThumbnailCache.get(book.path, pageIndex, password, reverseColorMode, preserveImageColors)
                ?: IosPdfiumRenderer.renderThumbnail(book.path, pageIndex, password, reverseColorMode, preserveImageColors)?.also { rendered ->
                    IosPdfThumbnailCache.put(book.path, pageIndex, password, reverseColorMode, preserveImageColors, rendered)
                }
                ?: SharedMobilePdfPageThumbnail()
        }
    }

    return thumbnail ?: SharedMobilePdfPageThumbnail()
}

@Composable
internal actual fun rememberSharedMobilePdfTileRenders(
    book: BookItem,
    pageIndex: Int,
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: PdfPageBounds?,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): List<SharedMobilePdfTileRender> {
    var settledZoomScale by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) { mutableStateOf(zoomScale) }
    var zoomIsSettling by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) { mutableStateOf(false) }
    LaunchedEffect(zoomScale) {
        zoomIsSettling = true
        delay(PDF_ZOOM_RENDER_SETTLE_MILLIS)
        settledZoomScale = zoomScale
        zoomIsSettling = false
    }
    val requests = remember(pageAspectRatio, settledZoomScale, visibleBounds) {
        visibleBounds?.let { planPdfZoomTiles(pageAspectRatio, settledZoomScale, it) }.orEmpty()
    }
    var tiles by remember(book.path, pageIndex, password, reverseColorMode, preserveImageColors) { mutableStateOf<List<SharedMobilePdfTileRender>>(emptyList()) }
    LaunchedEffect(book.path, pageIndex, requests, password, zoomIsSettling, reverseColorMode, preserveImageColors) {
        if (zoomIsSettling) return@LaunchedEffect
        if (requests.isEmpty()) {
            tiles = emptyList()
            return@LaunchedEffect
        }
        val cached = IosPdfTileCache.get(book, pageIndex, password, reverseColorMode, preserveImageColors, requests)
        val cachedIds = cached.mapTo(mutableSetOf()) { it.request.id }
        val missing = requests.filterNot { it.id in cachedIds }
        if (missing.isEmpty()) {
            tiles = cached
            return@LaunchedEffect
        }
        val rendered = IosPdfiumRenderer.renderTiles(book.path, pageIndex, missing, password, reverseColorMode, preserveImageColors)
        coroutineContext.ensureActive()
        IosPdfTileCache.put(book, pageIndex, password, reverseColorMode, preserveImageColors, rendered)
        tiles = cached + rendered
    }
    return tiles
}

private object IosPdfThumbnailCache {
    private const val MaxEntries = 96
    private val entries = LinkedHashMap<String, SharedMobilePdfPageThumbnail>()

    fun get(
        path: String?, pageIndex: Int, password: String?, reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
    ): SharedMobilePdfPageThumbnail? = entries[key(path, pageIndex, password, reverseColorMode, preserveImageColors)]

    fun put(
        path: String?, pageIndex: Int, password: String?, reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean, thumbnail: SharedMobilePdfPageThumbnail,
    ) {
        val key = key(path, pageIndex, password, reverseColorMode, preserveImageColors)
        entries[key] = thumbnail
        while (entries.size > MaxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    private fun key(
        path: String?, pageIndex: Int, password: String?, reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
    ): String = "${path.orEmpty()}|$pageIndex|${password.orEmpty()}|${reverseColorMode.id}|$preserveImageColors"
}

private object IosPdfTileCache {
    private val entries = PdfTileLruCache<SharedMobilePdfTileRender>(PDF_ZOOM_TILE_CACHE_MAX_BYTES)

    fun get(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
        requests: List<PdfZoomTileRequest>,
    ): List<SharedMobilePdfTileRender> = requests.mapNotNull { request ->
        val key = key(book, pageIndex, password, reverseColorMode, preserveImageColors, request)
        entries.get(key)
    }

    fun put(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
        renders: List<SharedMobilePdfTileRender>,
    ) {
        renders.forEach { render ->
            val key = key(book, pageIndex, password, reverseColorMode, preserveImageColors, render.request)
            val bytes = render.request.widthPx.toLong() * render.request.heightPx * 4L
            entries.put(key, render, bytes)
        }
    }

    private fun key(
        book: BookItem,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode,
        preserveImageColors: Boolean,
        request: PdfZoomTileRequest,
    ) =
        "${book.path.orEmpty()}|${book.fileContentModifiedTimestamp}|$pageIndex|${password.orEmpty()}|" +
            "${reverseColorMode.id}|$preserveImageColors|${request.fullWidthPx}x${request.fullHeightPx}|${request.id}"
}

private object IosPdfiumRenderer {
    suspend fun renderTiles(
        path: String?,
        pageIndex: Int,
        requests: List<PdfZoomTileRequest>,
        password: String?,
        reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
        preserveImageColors: Boolean = false,
    ): List<SharedMobilePdfTileRender> = IosPdfiumRuntime.mutex.withLock {
        val resolvedPath = path.resolvedIosPdfPath() ?: return@withLock emptyList()
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) return@withLock emptyList()
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(resolvedPath, password) ?: return@withLock emptyList()
        try {
            val count = FPDF_GetPageCount(document).coerceAtLeast(1)
            val page = FPDF_LoadPage(document, pageIndex.coerceIn(0, count - 1)) ?: return@withLock emptyList()
            try {
                val pageWidth = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                val pageHeight = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                val imageRects = if (preserveImageColors) {
                    extractIosPdfImageRects(page, pageWidth, pageHeight, requests.firstOrNull()?.fullWidthPx ?: 0, requests.firstOrNull()?.fullHeightPx ?: 0)
                } else {
                    emptyList()
                }
                requests.mapNotNull { request -> renderTile(page, request, reverseColorMode, imageRects) }
            } finally {
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }

    private fun renderTile(
        page: kotlinx.cinterop.CPointer<fpdf_page_t__>,
        request: PdfZoomTileRequest,
        reverseColorMode: PdfReverseColorMode,
        imageRects: List<IosPdfImageRect>,
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
            transformIosPdfBitmapBytes(
                bytes,
                stride,
                request.widthPx,
                request.heightPx,
                reverseColorMode,
                protectedRects = imageRects,
                targetWidth = request.fullWidthPx,
                targetHeight = request.fullHeightPx,
                targetOriginX = request.leftPx,
                targetOriginY = request.topPx,
            )
            val image = Image.makeRaster(
                ImageInfo(request.widthPx, request.heightPx, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                bytes,
                stride
            ).toComposeImageBitmap()
            SharedMobilePdfTileRender(
                request = request,
                bitmap = image,
                rasterizedReverseColorMode = reverseColorMode.takeIf {
                    it != PdfReverseColorMode.RGB || imageRects.isNotEmpty()
                },
            )
        } finally {
            FPDFBitmap_Destroy(bitmap)
        }
    }

    suspend fun render(
        path: String?,
        pageIndex: Int,
        zoomScale: Float,
        password: String?,
        reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
        preserveImageColors: Boolean = false,
    ): SharedMobilePdfPageRender =
        IosPdfiumRuntime.mutex.withLock {
        val resolvedPath = path.resolvedIosPdfPath()
        if (resolvedPath.isNullOrBlank()) {
            return SharedMobilePdfPageRender(errorMessage = "PDF path is unavailable")
        }
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) {
            return SharedMobilePdfPageRender(errorMessage = "PDF file is missing: $resolvedPath")
        }

        IosPdfiumRuntime.ensureInitialized()

        val document = FPDF_LoadDocument(resolvedPath, password)
            ?: return SharedMobilePdfPageRender(
                errorMessage = "Pdfium could not open this PDF",
                openError = sharedMobilePdfOpenErrorForPdfiumCode(FPDF_GetLastError().toLong()),
            )
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
                    val targetHeight = MaxRenderedPageHeightPx
                    val scale = targetHeight / pageHeight
                    val bitmapWidth = (pageWidth * scale).roundToInt().coerceAtLeast(1)
                    val bitmapHeight = targetHeight.roundToInt().coerceAtLeast(1)
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
                        val imageRects = if (preserveImageColors) {
                            extractIosPdfImageRects(page, pageWidth, pageHeight, bitmapWidth, bitmapHeight)
                        } else {
                            emptyList()
                        }
                        transformIosPdfBitmapBytes(
                            bytes,
                            stride,
                            bitmapWidth,
                            bitmapHeight,
                            reverseColorMode,
                            protectedRects = imageRects,
                            targetWidth = bitmapWidth,
                            targetHeight = bitmapHeight,
                        )
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
                            bitmap = image,
                            rasterizedReverseColorMode = reverseColorMode.takeIf {
                                it != PdfReverseColorMode.RGB || imageRects.isNotEmpty()
                            },
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

    suspend fun renderThumbnail(
        path: String?,
        pageIndex: Int,
        password: String?,
        reverseColorMode: PdfReverseColorMode = PdfReverseColorMode.RGB,
        preserveImageColors: Boolean = false,
    ): SharedMobilePdfPageThumbnail? =
        IosPdfiumRuntime.mutex.withLock {
            val resolvedPath = path.resolvedIosPdfPath() ?: return@withLock null
            if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) return@withLock null
            IosPdfiumRuntime.ensureInitialized()
            val document = FPDF_LoadDocument(resolvedPath, password) ?: return@withLock null
            try {
                val count = FPDF_GetPageCount(document).coerceAtLeast(1)
                val page = FPDF_LoadPage(document, pageIndex.coerceIn(0, count - 1)) ?: return@withLock null
                try {
                    val pageWidth = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                    val pageHeight = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                    val aspectRatio = (pageWidth / pageHeight).coerceIn(0.1f, 10f)
                    val scale = ThumbnailTargetWidthPx / pageWidth
                    val bitmapWidth = (pageWidth * scale).roundToInt().coerceAtLeast(1)
                    val bitmapHeight = (pageHeight * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = FPDFBitmap_Create(bitmapWidth, bitmapHeight, 1) ?: return@withLock null
                    try {
                        FPDFBitmap_FillRect(bitmap, 0, 0, bitmapWidth, bitmapHeight, 0xFFFFFFFFu)
                        FPDF_RenderPageBitmap(bitmap, page, 0, 0, bitmapWidth, bitmapHeight, 0, 0)
                        val buffer = FPDFBitmap_GetBuffer(bitmap) ?: return@withLock null
                        val stride = FPDFBitmap_GetStride(bitmap).coerceAtLeast(bitmapWidth * 4)
                        val byteCount = stride * bitmapHeight
                        val bytes = ByteArray(byteCount)
                        bytes.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), buffer, byteCount.convert())
                        }
                        val imageRects = if (preserveImageColors) {
                            extractIosPdfImageRects(page, pageWidth, pageHeight, bitmapWidth, bitmapHeight)
                        } else {
                            emptyList()
                        }
                        transformIosPdfBitmapBytes(
                            bytes,
                            stride,
                            bitmapWidth,
                            bitmapHeight,
                            reverseColorMode,
                            protectedRects = imageRects,
                            targetWidth = bitmapWidth,
                            targetHeight = bitmapHeight,
                            forceRgbTransform = true,
                        )
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
                        SharedMobilePdfPageThumbnail(bitmap = image, aspectRatio = aspectRatio)
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

internal actual suspend fun sharedMobilePdfOcrTextBounds(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): List<PdfPageBounds> {
    val render = IosPdfiumRenderer.render(book.path, pageIndex, 1f, password)
    val bitmap = render.bitmap ?: return emptyList()
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return emptyList()
    val pixels = IntArray(width * height)
    bitmap.readPixels(pixels)
    val cgImage = autoreleasepool { bgraPixelsToCgImage(pixels, width, height) } ?: return emptyList()
    return autoreleasepool { recognizeTextLineBounds(cgImage) }
}

private fun bgraPixelsToCgImage(pixels: IntArray, width: Int, height: Int): CGImageRef? {
    val bytesPerRow = width * 4
    val uBytes = ByteArray(pixels.size * 4).toUByteArray()
    pixels.usePinned { intPinned ->
        uBytes.usePinned { bytePinned ->
            memcpy(bytePinned.addressOf(0), intPinned.addressOf(0), uBytes.size.toULong())
        }
    }
    val data = uBytes.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0), uBytes.size.toLong())
    } ?: return null
    val provider = CGDataProviderCreateWithCFData(data) ?: return null
    val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value or kCGBitmapByteOrder32Little
    return CGImageCreate(
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bitsPerPixel = 32u,
        bytesPerRow = bytesPerRow.toULong(),
        space = colorSpace,
        bitmapInfo = bitmapInfo,
        provider = provider,
        decode = null,
        shouldInterpolate = true,
        intent = CGColorRenderingIntent.kCGRenderingIntentDefault,
    )
}

private fun recognizeTextLineBounds(image: CGImageRef): List<PdfPageBounds> {
    val request = VNRecognizeTextRequest(null)
    request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
    val handler = VNImageRequestHandler(image, emptyMap<Any?, Any>())
    handler.performRequests(listOf<VNRequest>(request), null)
    val results = request.results ?: return emptyList()
    return results.mapNotNull { observation ->
        val textObservation = observation as? VNRecognizedTextObservation ?: return@mapNotNull null
        textObservation.boundingBox.useContents {
            PdfPageBounds(
                left = origin.x.toFloat().coerceIn(0f, 1f),
                top = (1.0 - origin.y - size.height).toFloat().coerceIn(0f, 1f),
                right = (origin.x + size.width).toFloat().coerceIn(0f, 1f),
                bottom = (1.0 - origin.y).toFloat().coerceIn(0f, 1f),
            )
        }
    }
}

private const val MaxRenderedPageHeightPx = 2048f
private const val ThumbnailTargetWidthPx = 240
private const val SearchPreviewRadius = 42
private const val MaxSearchResults = 500
