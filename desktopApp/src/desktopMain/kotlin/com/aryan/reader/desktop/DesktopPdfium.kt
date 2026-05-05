package com.aryan.reader.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class DesktopPdfDocument(
    val path: String,
    val title: String,
    val pageCount: Int,
    val pageSizes: List<DesktopPdfPageSize>,
    val textPages: List<String>,
    val textCharsByPage: List<List<DesktopPdfTextChar>> = emptyList()
) {
    fun close() {
        DesktopPdfium.closeDocument(path)
    }
}

data class DesktopPdfPageSize(
    val width: Float,
    val height: Float
)

data class DesktopPdfPageRender(
    val image: ImageBitmap,
    val width: Int,
    val height: Int
)

data class DesktopPdfTextChar(
    val index: Int,
    val char: Char,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val hasBounds: Boolean
        get() = right > left && bottom > top
}

data class DesktopPdfTextRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object DesktopPdfium {
    private const val FPDF_ANNOT = 0x01
    private const val FPDF_LCD_TEXT = 0x02
    private const val FPDF_RENDER_NO_SMOOTHTEXT = 0x1000
    private const val FPDF_BITMAP_BGRA = 4

    private val pdfiumDll: File by lazy(::resolvePdfiumDll)
    private val zoomSpec = PdfZoomSpec()
    private val api: PdfiumLibrary by lazy {
        require(pdfiumDll.exists()) {
            "Missing Pdfium DLL. Expected pdfium-v8-win-x64 under third_party/pdfium/win-x64-v8/bin/pdfium.dll."
        }
        Native.load(pdfiumDll.absolutePath, PdfiumLibrary::class.java)
    }

    private var initialized = false
    private val openDocuments = LinkedHashMap<String, Pointer>()

    fun isAvailable(): Boolean = pdfiumDll.exists()

    fun load(file: File, password: String? = null): DesktopPdfDocument {
        initLibrary()
        val document = api.FPDF_LoadDocument(file.absolutePath, password)
            ?: error("Pdfium could not open ${file.name}. It may be encrypted or unsupported.")
        val pageCount = api.FPDF_GetPageCount(document)
        openDocuments[file.absolutePath] = document

        val pageSizes = (0 until pageCount).map { pageIndex ->
            loadPage(document, pageIndex).usePointer { page ->
                DesktopPdfPageSize(
                    width = api.FPDF_GetPageWidthF(page),
                    height = api.FPDF_GetPageHeightF(page)
                )
            }
        }

        val textPageData = (0 until pageCount).map { pageIndex ->
            extractPageTextData(document, pageIndex, pageSizes[pageIndex])
        }

        return DesktopPdfDocument(
            path = file.absolutePath,
            title = file.nameWithoutExtension,
            pageCount = pageCount,
            pageSizes = pageSizes,
            textPages = textPageData.map { it.text },
            textCharsByPage = textPageData.map { it.chars }
        )
    }

    fun closeDocument(path: String) {
        openDocuments.remove(path)?.let(api::FPDF_CloseDocument)
    }

    fun renderPage(
        document: DesktopPdfDocument,
        pageIndex: Int,
        scale: Float,
        renderAnnotations: Boolean = true
    ): DesktopPdfPageRender {
        val nativeDocument = openDocuments[document.path] ?: error("PDF document is not open.")
        val pageSize = document.pageSizes.getOrNull(pageIndex) ?: error("Invalid PDF page index $pageIndex.")
        val safeScale = zoomSpec.safeRenderScale(pageSize.width, pageSize.height, scale)
        val width = (pageSize.width * safeScale).roundToInt().coerceAtLeast(1)
        val height = (pageSize.height * safeScale).roundToInt().coerceAtLeast(1)
        val stride = width * 4
        val memory = Memory((stride * height).toLong())
        memory.clear(memory.size())

        val bitmap = api.FPDFBitmap_CreateEx(width, height, FPDF_BITMAP_BGRA, memory, stride)
            ?: error("Pdfium could not allocate render bitmap.")

        try {
            api.FPDFBitmap_FillRect(bitmap, 0, 0, width, height, -1)
            loadPage(nativeDocument, pageIndex).usePointer { page ->
                val flags = FPDF_LCD_TEXT or
                    (if (renderAnnotations) FPDF_ANNOT else FPDF_RENDER_NO_SMOOTHTEXT)
                api.FPDF_RenderPageBitmap(bitmap, page, 0, 0, width, height, 0, flags)
            }
            return DesktopPdfPageRender(
                image = memory.toBufferedImage(width, height, stride).toComposeImageBitmap(),
                width = width,
                height = height
            )
        } finally {
            api.FPDFBitmap_Destroy(bitmap)
        }
    }

    fun charIndexAt(
        document: DesktopPdfDocument,
        pageIndex: Int,
        normalizedX: Float,
        normalizedY: Float,
        viewportWidth: Int? = null,
        viewportHeight: Int? = null,
        tolerance: Float = 0.006f
    ): Int? {
        val nativeDocument = openDocuments[document.path] ?: return null
        val pageSize = document.pageSizes.getOrNull(pageIndex) ?: return null
        val viewport = pageSize.normalizedViewport(viewportWidth, viewportHeight)
        return runCatching {
            loadPage(nativeDocument, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer null
                try {
                    val pagePoint = deviceToPagePoint(
                        page = page,
                        viewport = viewport,
                        normalizedX = normalizedX,
                        normalizedY = normalizedY
                    )
                    api.FPDFText_GetCharIndexAtPos(
                        textPage,
                        pagePoint.first,
                        pagePoint.second,
                        (pageSize.width * tolerance).toDouble(),
                        (pageSize.height * tolerance).toDouble()
                    ).takeIf { it >= 0 }
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrNull()
    }

    fun textRectsForRange(
        document: DesktopPdfDocument,
        pageIndex: Int,
        startIndex: Int,
        endIndex: Int,
        viewportWidth: Int? = null,
        viewportHeight: Int? = null
    ): List<DesktopPdfTextRect> {
        val nativeDocument = openDocuments[document.path] ?: return emptyList()
        val pageSize = document.pageSizes.getOrNull(pageIndex) ?: return emptyList()
        val viewport = pageSize.normalizedViewport(viewportWidth, viewportHeight)
        val first = minOf(startIndex, endIndex).coerceAtLeast(0)
        val count = (maxOf(startIndex, endIndex) - first + 1).coerceAtLeast(1)
        return runCatching {
            loadPage(nativeDocument, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer emptyList()
                try {
                    val rectCount = api.FPDFText_CountRects(textPage, first, count)
                    (0 until rectCount).mapNotNull { rectIndex ->
                        val left = DoubleArray(1)
                        val top = DoubleArray(1)
                        val right = DoubleArray(1)
                        val bottom = DoubleArray(1)
                        val hasRect = api.FPDFText_GetRect(textPage, rectIndex, left, top, right, bottom) != 0
                        if (!hasRect || right[0] <= left[0] || top[0] <= bottom[0]) {
                            null
                        } else {
                            val bounds = pageToNormalizedBounds(
                                page = page,
                                pageSize = pageSize,
                                viewport = viewport,
                                left = left[0],
                                top = top[0],
                                right = right[0],
                                bottom = bottom[0]
                            )
                            DesktopPdfTextRect(
                                left = bounds.left,
                                top = bounds.top,
                                right = bounds.right,
                                bottom = bounds.bottom
                            )
                        }
                    }
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun extractPageTextData(document: Pointer, pageIndex: Int, pageSize: DesktopPdfPageSize): DesktopPdfTextPageData {
        return runCatching {
            loadPage(document, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer DesktopPdfTextPageData()
                try {
                    val charCount = api.FPDFText_CountChars(textPage)
                    if (charCount <= 0) return@usePointer DesktopPdfTextPageData()
                    val buffer = Memory(((charCount + 1) * 2L))
                    val written = api.FPDFText_GetText(textPage, 0, charCount, buffer)
                    val text = if (written <= 0) {
                        ""
                    } else {
                        buffer.getCharArray(0, written).concatToString().trimEnd('\u0000')
                    }
                    val chars = (0 until charCount).mapNotNull { index ->
                        val unicode = api.FPDFText_GetUnicode(textPage, index)
                        if (unicode <= 0) return@mapNotNull null
                        val left = DoubleArray(1)
                        val right = DoubleArray(1)
                        val bottom = DoubleArray(1)
                        val top = DoubleArray(1)
                        val hasBox = api.FPDFText_GetCharBox(textPage, index, left, right, bottom, top) != 0
                        if (!hasBox) {
                            DesktopPdfTextChar(index, unicode.toChar(), 0f, 0f, 0f, 0f)
                        } else {
                            val bounds = pageToNormalizedBounds(
                                page = page,
                                pageSize = pageSize,
                                viewport = pageSize.normalizedViewport(),
                                left = left[0],
                                top = top[0],
                                right = right[0],
                                bottom = bottom[0]
                            )
                            DesktopPdfTextChar(
                                index = index,
                                char = unicode.toChar(),
                                left = bounds.left,
                                top = bounds.top,
                                right = bounds.right,
                                bottom = bounds.bottom
                            )
                        }
                    }
                    DesktopPdfTextPageData(text = text, chars = chars)
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrDefault(DesktopPdfTextPageData())
    }

    private fun loadPage(document: Pointer, pageIndex: Int): PointerResource {
        val page = api.FPDF_LoadPage(document, pageIndex)
            ?: error("Pdfium could not open page ${pageIndex + 1}.")
        return PointerResource(page, api::FPDF_ClosePage)
    }

    private fun initLibrary() {
        if (!initialized) {
            api.FPDF_InitLibrary()
            initialized = true
        }
    }

    private fun resolvePdfiumDll(): File {
        val overridePath = System.getProperty("reader.pdfium.dll")
            ?: System.getenv("READER_PDFIUM_DLL")
        if (!overridePath.isNullOrBlank()) {
            return File(overridePath).absoluteFile
        }

        val relativePath = listOf("third_party", "pdfium", "win-x64-v8", "bin", "pdfium.dll")
            .joinToString(File.separator)
        val roots = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .take(6)
            .toList()

        return roots
            .map { File(it, relativePath).absoluteFile }
            .firstOrNull { it.exists() }
            ?: File(File(System.getProperty("user.dir")).absoluteFile, relativePath).absoluteFile
    }

    private fun Memory.toBufferedImage(width: Int, height: Int, stride: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val buffer = getByteBuffer(0, size()).order(ByteOrder.LITTLE_ENDIAN)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            buffer.position(y * stride)
            for (x in 0 until width) {
                val b = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val r = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width)
        return image
    }

    private class PointerResource(
        private val pointer: Pointer,
        private val closer: (Pointer) -> Unit
    ) {
        fun <T> usePointer(block: (Pointer) -> T): T {
            try {
                return block(pointer)
            } finally {
                closer(pointer)
            }
        }
    }

    private data class DesktopPdfTextPageData(
        val text: String = "",
        val chars: List<DesktopPdfTextChar> = emptyList()
    )

    private data class NormalizedViewport(
        val width: Int,
        val height: Int
    )

    private data class NormalizedBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private fun DesktopPdfPageSize.normalizedViewport(widthOverride: Int? = null, heightOverride: Int? = null): NormalizedViewport {
        return NormalizedViewport(
            width = widthOverride?.coerceAtLeast(1) ?: width.roundToInt().coerceAtLeast(1),
            height = heightOverride?.coerceAtLeast(1) ?: height.roundToInt().coerceAtLeast(1)
        )
    }

    private fun pageToNormalizedBounds(
        page: Pointer,
        pageSize: DesktopPdfPageSize,
        viewport: NormalizedViewport = pageSize.normalizedViewport(),
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): NormalizedBounds {
        val topLeft = pageToDevicePoint(page, viewport, left, top)
        val bottomRight = pageToDevicePoint(page, viewport, right, bottom)
        val deviceLeft = minOf(topLeft.first, bottomRight.first).toFloat()
        val deviceRight = maxOf(topLeft.first, bottomRight.first).toFloat()
        val deviceTop = minOf(topLeft.second, bottomRight.second).toFloat()
        val deviceBottom = maxOf(topLeft.second, bottomRight.second).toFloat()
        return NormalizedBounds(
            left = (deviceLeft / viewport.width).coerceIn(0f, 1f),
            top = (deviceTop / viewport.height).coerceIn(0f, 1f),
            right = (deviceRight / viewport.width).coerceIn(0f, 1f),
            bottom = (deviceBottom / viewport.height).coerceIn(0f, 1f)
        )
    }

    private fun pageToDevicePoint(
        page: Pointer,
        viewport: NormalizedViewport,
        pageX: Double,
        pageY: Double
    ): Pair<Int, Int> {
        val deviceX = IntArray(1)
        val deviceY = IntArray(1)
        api.FPDF_PageToDevice(
            page,
            0,
            0,
            viewport.width,
            viewport.height,
            0,
            pageX,
            pageY,
            deviceX,
            deviceY
        )
        return deviceX[0] to deviceY[0]
    }

    private fun deviceToPagePoint(
        page: Pointer,
        viewport: NormalizedViewport,
        normalizedX: Float,
        normalizedY: Float
    ): Pair<Double, Double> {
        val pageX = DoubleArray(1)
        val pageY = DoubleArray(1)
        api.FPDF_DeviceToPage(
            page,
            0,
            0,
            viewport.width,
            viewport.height,
            0,
            (normalizedX.coerceIn(0f, 1f) * viewport.width).roundToInt(),
            (normalizedY.coerceIn(0f, 1f) * viewport.height).roundToInt(),
            pageX,
            pageY
        )
        return pageX[0] to pageY[0]
    }

    @Suppress("FunctionName")
    private interface PdfiumLibrary : Library {
        fun FPDF_InitLibrary()
        fun FPDF_LoadDocument(filePath: String, password: String?): Pointer?
        fun FPDF_CloseDocument(document: Pointer)
        fun FPDF_GetPageCount(document: Pointer): Int
        fun FPDF_LoadPage(document: Pointer, pageIndex: Int): Pointer?
        fun FPDF_ClosePage(page: Pointer)
        fun FPDF_GetPageWidthF(page: Pointer): Float
        fun FPDF_GetPageHeightF(page: Pointer): Float
        fun FPDFBitmap_CreateEx(width: Int, height: Int, format: Int, firstScan: Pointer, stride: Int): Pointer?
        fun FPDFBitmap_FillRect(bitmap: Pointer, left: Int, top: Int, width: Int, height: Int, color: Int)
        fun FPDFBitmap_Destroy(bitmap: Pointer)
        fun FPDF_RenderPageBitmap(
            bitmap: Pointer,
            page: Pointer,
            startX: Int,
            startY: Int,
            sizeX: Int,
            sizeY: Int,
            rotate: Int,
            flags: Int
        )

        fun FPDFText_LoadPage(page: Pointer): Pointer?
        fun FPDFText_ClosePage(textPage: Pointer)
        fun FPDFText_CountChars(textPage: Pointer): Int
        fun FPDFText_GetText(textPage: Pointer, startIndex: Int, count: Int, result: Pointer): Int
        fun FPDFText_GetUnicode(textPage: Pointer, index: Int): Int
        fun FPDFText_GetCharBox(
            textPage: Pointer,
            index: Int,
            left: DoubleArray,
            right: DoubleArray,
            bottom: DoubleArray,
            top: DoubleArray
        ): Int
        fun FPDFText_GetCharIndexAtPos(
            textPage: Pointer,
            x: Double,
            y: Double,
            xTolerance: Double,
            yTolerance: Double
        ): Int
        fun FPDFText_CountRects(textPage: Pointer, startIndex: Int, count: Int): Int
        fun FPDFText_GetRect(
            textPage: Pointer,
            rectIndex: Int,
            left: DoubleArray,
            top: DoubleArray,
            right: DoubleArray,
            bottom: DoubleArray
        ): Int
        fun FPDF_PageToDevice(
            page: Pointer,
            startX: Int,
            startY: Int,
            sizeX: Int,
            sizeY: Int,
            rotate: Int,
            pageX: Double,
            pageY: Double,
            deviceX: IntArray,
            deviceY: IntArray
        )
        fun FPDF_DeviceToPage(
            page: Pointer,
            startX: Int,
            startY: Int,
            sizeX: Int,
            sizeY: Int,
            rotate: Int,
            deviceX: Int,
            deviceY: Int,
            pageX: DoubleArray,
            pageY: DoubleArray
        )
    }
}
