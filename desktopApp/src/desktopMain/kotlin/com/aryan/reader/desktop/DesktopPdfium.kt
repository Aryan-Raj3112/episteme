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
    val textPages: List<String>
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

        val textPages = (0 until pageCount).map { pageIndex ->
            extractPageText(document, pageIndex)
        }

        return DesktopPdfDocument(
            path = file.absolutePath,
            title = file.nameWithoutExtension,
            pageCount = pageCount,
            pageSizes = pageSizes,
            textPages = textPages
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

    private fun extractPageText(document: Pointer, pageIndex: Int): String {
        return runCatching {
            loadPage(document, pageIndex).usePointer { page ->
                val textPage = api.FPDFText_LoadPage(page) ?: return@usePointer ""
                try {
                    val charCount = api.FPDFText_CountChars(textPage)
                    if (charCount <= 0) return@usePointer ""
                    val buffer = Memory(((charCount + 1) * 2L))
                    val written = api.FPDFText_GetText(textPage, 0, charCount, buffer)
                    if (written <= 0) {
                        ""
                    } else {
                        buffer.getCharArray(0, written).concatToString().trimEnd('\u0000')
                    }
                } finally {
                    api.FPDFText_ClosePage(textPage)
                }
            }
        }.getOrDefault("")
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
    }
}
