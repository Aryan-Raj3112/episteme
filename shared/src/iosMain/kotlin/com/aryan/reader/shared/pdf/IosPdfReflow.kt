@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFBitmap_BGRA
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Create
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Destroy
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetBuffer
import com.aryan.reader.shared.pdfium.c.FPDFPageObj_GetBounds
import com.aryan.reader.shared.pdfium.c.FPDFPageObj_GetType
import com.aryan.reader.shared.pdfium.c.FPDFPage_CountObjects
import com.aryan.reader.shared.pdfium.c.FPDFPage_GetObject
import com.aryan.reader.shared.pdfium.c.FPDF_RenderPageBitmap
import com.aryan.reader.shared.pdfium.c.FPDFText_CountChars
import com.aryan.reader.shared.pdfium.c.FPDFText_GetCharBox
import com.aryan.reader.shared.pdfium.c.FPDFText_GetFontInfo
import com.aryan.reader.shared.pdfium.c.FPDFText_GetFontSize
import com.aryan.reader.shared.pdfium.c.FPDFText_GetFontWeight
import com.aryan.reader.shared.pdfium.c.FPDFText_GetUnicode
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_PAGEOBJ_IMAGE
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.math.roundToInt

/**
 * Generates a reflowed HTML version of a PDF, mirroring Android's PdfToHtmlGenerator.
 * Text is extracted with pdfium per-char font metadata and laid out with the shared
 * SharedPdfReflowHtml builder; embedded images are rendered into JPEG data URIs.
 */
internal suspend fun generateIosPdfReflowHtml(
    pdfPath: String,
    destPath: String,
    onProgress: (Float) -> Unit,
): Boolean {
    if (!pdfPath.endsWith(".pdf", ignoreCase = true)) return false
    return withContext(Dispatchers.Default) {
        IosPdfiumRuntime.mutex.withLock {
            IosPdfiumRuntime.ensureInitialized()
            val document = FPDF_LoadDocument(pdfPath, null) ?: return@withLock false
            try {
                val totalPages = FPDF_GetPageCount(document).coerceAtLeast(0)
                if (totalPages == 0) return@withLock false

                val headerFooterStrings = detectIosReflowHeaderFooter(document, totalPages)

                val output = StringBuilder()
                output.append(SharedPdfReflowHtml.buildGlobalHtmlHeader())
                for (pageIndex in 0 until totalPages) {
                    if (pageIndex > 0) output.append("\n<page-break></page-break>\n")
                    output.append(extractIosReflowPage(document, pageIndex, pageIndex + 1, headerFooterStrings))
                    if (pageIndex % 5 == 0 || pageIndex == totalPages - 1) {
                        onProgress((pageIndex + 1).toFloat() / totalPages.toFloat())
                    }
                }
                output.append(SharedPdfReflowHtml.buildGlobalHtmlFooter())

                val bytes = output.toString().encodeToByteArray()
                val file = fopen(destPath, "wb") ?: return@withLock false
                val written = try {
                    if (bytes.isEmpty()) {
                        0uL
                    } else {
                        bytes.usePinned { pinned ->
                            fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
                        }
                    }
                } finally {
                    fclose(file)
                }
                written == bytes.size.toULong()
            } finally {
                FPDF_CloseDocument(document)
            }
        }
    }
}

private fun detectIosReflowHeaderFooter(document: com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT, totalPages: Int): Set<String> {
    if (totalPages < 5) return emptySet()
    val step = maxOf(1, totalPages / 8)
    val samplePages = (0 until totalPages).filter { it % step == 0 }.take(8)
    val samplePageLines = mutableListOf<List<String>>()
    for (pageIndex in samplePages) {
        val page = FPDF_LoadPage(document, pageIndex) ?: continue
        try {
            val textPage = com.aryan.reader.shared.pdfium.c.FPDFText_LoadPage(page) ?: continue
            try {
                val charCount = FPDFText_CountChars(textPage)
                if (charCount <= 0) continue
                val rawText = buildString {
                    for (index in 0 until charCount) append(FPDFText_GetUnicode(textPage, index).toInt().toChar())
                }
                val lines = rawText.split('\n').map { it.trim() }.filter { it.length > 2 }
                if (lines.isNotEmpty()) samplePageLines.add(lines)
            } finally {
                com.aryan.reader.shared.pdfium.c.FPDFText_ClosePage(textPage)
            }
        } finally {
            FPDF_ClosePage(page)
        }
    }
    return SharedPdfReflowHtml.detectRepeatingHeaderFooter(samplePageLines)
}

private fun extractIosReflowPage(
    document: com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT,
    pageIndex: Int,
    pageNumber: Int,
    headerFooterStrings: Set<String>,
): String {
    val page = FPDF_LoadPage(document, pageIndex)
        ?: return SharedPdfReflowHtml.buildEmptyPageSection(pageNumber)
    try {
        val pageHeight = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
        val imageElements = extractIosReflowImages(page, pageHeight)

        val textPage = com.aryan.reader.shared.pdfium.c.FPDFText_LoadPage(page)
            ?: return SharedPdfReflowHtml.buildPageHtml(
                SharedPdfReflowPage(pageNumber, imageElements),
                headerFooterStrings,
            )
        try {
            val charCount = FPDFText_CountChars(textPage)
            if (charCount <= 0) {
                return SharedPdfReflowHtml.buildPageHtml(
                    SharedPdfReflowPage(pageNumber, imageElements),
                    headerFooterStrings,
                )
            }

            val rawText = buildString {
                for (index in 0 until charCount) append(FPDFText_GetUnicode(textPage, index).toInt().toChar())
            }
            val actualCount = minOf(charCount, rawText.length)
            if (actualCount == 0) {
                return SharedPdfReflowHtml.buildPageHtml(
                    SharedPdfReflowPage(pageNumber, imageElements),
                    headerFooterStrings,
                )
            }

            val sizes = FloatArray(actualCount)
            val weights = IntArray(actualCount)
            val italicFlags = IntArray(actualCount)
            val boxTops = FloatArray(actualCount)
            var boxesAvailable = true
            for (index in 0 until actualCount) {
                sizes[index] = FPDFText_GetFontSize(textPage, index).toFloat()
                weights[index] = FPDFText_GetFontWeight(textPage, index)
                italicFlags[index] = reflowItalicFlag(textPage, index)
                val top = reflowCharBoxTop(textPage, index)
                if (top == null) {
                    boxesAvailable = false
                } else {
                    boxTops[index] = pageHeight - top
                }
            }

            val textLines = buildReaderReflowTextLines(
                rawText = rawText,
                charCount = actualCount,
                sizeAt = { index -> sizes[index] },
                weightAt = { index -> weights[index] },
                flagsAt = { index -> italicFlags[index] },
                boxTopYAt = { index -> if (boxesAvailable) boxTops[index] else null },
            )

            val elements = mutableListOf<SharedPdfReflowPageElement>()
            elements.addAll(imageElements)
            elements.addAll(textLines.map { line -> SharedPdfReflowTextElement(line) })
            elements.sortByDescending { it.yPos }

            return SharedPdfReflowHtml.buildPageHtml(
                SharedPdfReflowPage(pageNumber, elements),
                headerFooterStrings,
            )
        } finally {
            com.aryan.reader.shared.pdfium.c.FPDFText_ClosePage(textPage)
        }
    } catch (_: Throwable) {
        return SharedPdfReflowHtml.buildEmptyPageSection(pageNumber)
    } finally {
        FPDF_ClosePage(page)
    }
}

private fun reflowItalicFlag(textPage: com.aryan.reader.shared.pdfium.c.FPDF_TEXTPAGE, index: Int): Int {
    return memScoped {
        val flags = alloc<IntVar>()
        val nameBuffer = allocArray<ByteVar>(256)
        FPDFText_GetFontInfo(textPage, index, nameBuffer, 256u, flags.ptr)
        flags.value
    }
}

private fun reflowCharBoxTop(textPage: com.aryan.reader.shared.pdfium.c.FPDF_TEXTPAGE, index: Int): Float? {
    return memScoped {
        val left = alloc<DoubleVar>()
        val right = alloc<DoubleVar>()
        val bottom = alloc<DoubleVar>()
        val top = alloc<DoubleVar>()
        val ok = FPDFText_GetCharBox(textPage, index, left.ptr, right.ptr, bottom.ptr, top.ptr)
        if (ok != 0) top.value.toFloat() else null
    }
}

private fun extractIosReflowImages(
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    pageHeight: Float,
): List<SharedPdfReflowImageElement> {
    val objectCount = FPDFPage_CountObjects(page)
    if (objectCount <= 0) return emptyList()

    val images = mutableListOf<SharedPdfReflowImageElement>()
    for (objectIndex in 0 until objectCount) {
        val pageObject = FPDFPage_GetObject(page, objectIndex) ?: continue
        if (FPDFPageObj_GetType(pageObject) != FPDF_PAGEOBJ_IMAGE) continue
        val bounds = reflowObjectBounds(pageObject, pageHeight) ?: continue
        if (bounds.width <= 0 || bounds.height <= 0 || bounds.width > 4096 || bounds.height > 4096) continue

        val bitmap = FPDFBitmap_Create(bounds.width, bounds.height, FPDFBitmap_BGRA) ?: continue
        try {
            FPDF_RenderPageBitmap(bitmap, page, bounds.left, bounds.top, bounds.width, bounds.height, 0, 0)
            val buffer = FPDFBitmap_GetBuffer(bitmap) ?: continue
            val bytes = ByteArray(bounds.width * bounds.height * 4)
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), buffer, bytes.size.toULong())
                }
            }
            val jpegBase64 = reflowJpegBase64(bytes, bounds.width, bounds.height) ?: continue
            images.add(
                SharedPdfReflowImageElement(
                    base64Data = jpegBase64,
                    width = bounds.width,
                    height = bounds.height,
                    yPos = bounds.top.toFloat(),
                )
            )
        } finally {
            FPDFBitmap_Destroy(bitmap)
        }
    }
    return images
}

private data class IosReflowImageBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private fun reflowObjectBounds(
    pageObject: com.aryan.reader.shared.pdfium.c.FPDF_PAGEOBJECT,
    pageHeight: Float,
): IosReflowImageBounds? {
    // Returns bounds in render coordinates (top-left origin).
    return memScoped {
        val left = alloc<FloatVar>()
        val bottom = alloc<FloatVar>()
        val right = alloc<FloatVar>()
        val top = alloc<FloatVar>()
        val ok = FPDFPageObj_GetBounds(pageObject, left.ptr, bottom.ptr, right.ptr, top.ptr)
        if (ok == 0) return@memScoped null
        val width = (right.value - left.value).roundToInt()
        val height = (top.value - bottom.value).roundToInt()
        if (width <= 0 || height <= 0) return@memScoped null
        IosReflowImageBounds(
            left = left.value.roundToInt(),
            top = (pageHeight - top.value).roundToInt(),
            width = width,
            height = height,
        )
    }
}

private fun reflowJpegBase64(bgraBytes: ByteArray, width: Int, height: Int): String? {
    if (bgraBytes.isEmpty()) return null
    val uBytes = bgraBytes.toUByteArray()
    val data = uBytes.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0), uBytes.size.toLong())
    } ?: return null
    val provider = CGDataProviderCreateWithCFData(data) ?: return null
    val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value or kCGBitmapByteOrder32Little
    val image = CGImageCreate(
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bitsPerPixel = 32u,
        bytesPerRow = (width * 4).toULong(),
        space = colorSpace,
        bitmapInfo = bitmapInfo,
        provider = provider,
        decode = null,
        shouldInterpolate = true,
        intent = CGColorRenderingIntent.kCGRenderingIntentDefault,
    ) ?: return null
    val uiImage = UIImage.imageWithCGImage(image) ?: return null
    val jpegData = UIImageJPEGRepresentation(uiImage, 0.8) ?: return null
    val length = jpegData.length.toInt()
    if (length <= 0) return null
    val bytes = ByteArray(length)
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), jpegData.bytes, length.toULong())
        }
    }
    return base64Encode(bytes)
}

private fun base64Encode(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = StringBuilder(((bytes.size + 2) / 3) * 4)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index++].toInt() and 0xFF
        val second = if (index < bytes.size) bytes[index++].toInt() and 0xFF else -1
        val third = if (index < bytes.size) bytes[index++].toInt() and 0xFF else -1
        output.append(alphabet[first shr 2])
        output.append(alphabet[((first and 0x03) shl 4) or ((second.coerceAtLeast(0) and 0xF0) shr 4)])
        output.append(if (second >= 0) alphabet[((second and 0x0F) shl 2) or ((third.coerceAtLeast(0) and 0xC0) shr 6)] else '=')
        output.append(if (third >= 0) alphabet[third and 0x3F] else '=')
    }
    return output.toString()
}
