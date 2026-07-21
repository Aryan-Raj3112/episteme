@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFAction_GetDest
import com.aryan.reader.shared.pdfium.c.FPDFAction_GetFilePath
import com.aryan.reader.shared.pdfium.c.FPDFAction_GetType
import com.aryan.reader.shared.pdfium.c.FPDFAction_GetURIPath
import com.aryan.reader.shared.pdfium.c.FPDFDest_GetDestPageIndex
import com.aryan.reader.shared.pdfium.c.FPDFLink_GetAction
import com.aryan.reader.shared.pdfium.c.FPDFLink_GetDest
import com.aryan.reader.shared.pdfium.c.FPDFLink_GetLinkAtPoint
import com.aryan.reader.shared.pdfium.c.FPDFLink_Enumerate
import com.aryan.reader.shared.pdfium.c.FPDFLink_GetAnnotRect
import com.aryan.reader.shared.pdfium.c.FPDFLink_CloseWebLinks
import com.aryan.reader.shared.pdfium.c.FPDFLink_CountRects
import com.aryan.reader.shared.pdfium.c.FPDFLink_CountWebLinks
import com.aryan.reader.shared.pdfium.c.FPDFLink_GetRect
import com.aryan.reader.shared.pdfium.c.FPDFLink_GetURL
import com.aryan.reader.shared.pdfium.c.FPDFLink_LoadWebLinks
import com.aryan.reader.shared.pdfium.c.PDFACTION_GOTO
import com.aryan.reader.shared.pdfium.c.PDFACTION_LAUNCH
import com.aryan.reader.shared.pdfium.c.PDFACTION_REMOTEGOTO
import com.aryan.reader.shared.pdfium.c.PDFACTION_URI
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_PAGE
import com.aryan.reader.shared.pdfium.c.FPDFText_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDFText_CountChars
import com.aryan.reader.shared.pdfium.c.FPDFText_CountRects
import com.aryan.reader.shared.pdfium.c.FPDFText_GetCharBox
import com.aryan.reader.shared.pdfium.c.FPDFText_GetCharIndexAtPos
import com.aryan.reader.shared.pdfium.c.FPDFText_GetRect
import com.aryan.reader.shared.pdfium.c.FPDFText_GetText
import com.aryan.reader.shared.pdfium.c.FPDFText_GetUnicode
import com.aryan.reader.shared.pdfium.c.FPDFText_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_TEXTPAGE
import com.aryan.reader.shared.pdfium.c.FPDF_LINKVar
import com.aryan.reader.shared.pdfium.c.FS_RECTF
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.IntVar
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * Lifecycle-managed handle to a PDF document together with the loaded text
 * page for one specific page. The owner is responsible for calling [close]
 * exactly once.
 *
 * All PDF coordinates returned by this class are in PDF user space (origin
 * bottom-left), exactly as PDFium emits them. The Composable layer is
 * responsible for mapping them to the rendered bitmap's device-space
 * (origin top-left).
 */
internal class IosPdfTextPage internal constructor(
    private val document: FPDF_DOCUMENT,
    private val page: FPDF_PAGE,
    private val textPage: FPDF_TEXTPAGE,
    private val pageWidth: Float,
    private val pageHeight: Float
) : PdfTextPageSession, AutoCloseable {

    override val pageCharCount: Int by lazy {
        val page = textPage ?: return@lazy 0
        FPDFText_CountChars(page)
    }

    override fun charAt(index: Int): Char {
        val page = textPage ?: return 0.toChar()
        if (index < 0) return 0.toChar()
        return FPDFText_GetUnicode(page, index).toInt().toChar()
    }

    override fun charIndexAtNormalized(
        normX: Float,
        normY: Float,
        xTolerance: Double,
        yTolerance: Double
    ): Int {
        val page = textPage ?: return -1
        val pdfX = (normX * pageWidth).toDouble()
        // Normalised Y has top-left origin, pdfium expects bottom-left origin.
        val pdfY = (pageHeight - normY * pageHeight).toDouble()
        return FPDFText_GetCharIndexAtPos(page, pdfX, pdfY, xTolerance, yTolerance)
    }

    override fun linkAtNormalized(normX: Float, normY: Float): PdfLinkTarget? {
        val doc = document ?: return null
        val pagePtr = page ?: return null
        val pdfX = (normX * pageWidth).toDouble()
        val pdfY = (pageHeight - normY * pageHeight).toDouble()
        val link = FPDFLink_GetLinkAtPoint(pagePtr, pdfX, pdfY)
        if (link == null) {
            val webTarget = webLinkAtPdfPoint(pdfX, pdfY)
            pdfLinkLog { "native-hit annotation=false pdf=$pdfX,$pdfY webTarget=$webTarget" }
            return webTarget
        }
        // Try the action first (URI / GoTo / RemoteGoTo / Launch).
        val action = FPDFLink_GetAction(link)
        if (action != null) {
            val type = FPDFAction_GetType(action)
            pdfLinkLog { "native-hit annotation=true pdf=$pdfX,$pdfY actionType=${type.toInt()}" }
            when (type.toInt()) {
                PDFACTION_URI -> {
                    val uri = readUriPath(doc, action)
                    if (uri != null) return PdfLinkTarget.ExternalUrl(uri)
                }
                PDFACTION_GOTO -> {
                    val dest = FPDFAction_GetDest(doc, action) ?: return null
                    val pageIndex = FPDFDest_GetDestPageIndex(doc, dest)
                    if (pageIndex >= 0) return PdfLinkTarget.InternalPage(pageIndex)
                }
                PDFACTION_REMOTEGOTO, PDFACTION_LAUNCH -> {
                    val path = readFilePath(action)
                    if (path != null) return PdfLinkTarget.ExternalUrl(path)
                }
            }
        }
        // Fallback: link has a direct dest (no action).
        val dest = FPDFLink_GetDest(doc, link) ?: return null
        val pageIndex = FPDFDest_GetDestPageIndex(doc, dest)
        if (pageIndex >= 0) return PdfLinkTarget.InternalPage(pageIndex)
        return webLinkAtPdfPoint(pdfX, pdfY)
    }

    override fun linkBoundsNormalized(): List<PdfPageBounds> {
        val pagePtr = page ?: return emptyList()
        val result = mutableListOf<PdfPageBounds>()
        memScoped {
            val startPosition = alloc<IntVar>().also { it.value = 0 }
            val linkVar = alloc<FPDF_LINKVar>()
            val rect = alloc<FS_RECTF>()
            while (FPDFLink_Enumerate(pagePtr, startPosition.ptr, linkVar.ptr) != 0) {
                val link = linkVar.value ?: continue
                if (FPDFLink_GetAnnotRect(link, rect.ptr) != 0) {
                    result += pdfRectToNormalized(
                        left = rect.left.toDouble(),
                        top = rect.top.toDouble(),
                        right = rect.right.toDouble(),
                        bottom = rect.bottom.toDouble()
                    )
                }
            }
        }
        val text = textPage
        if (text != null) {
            val webLinks = FPDFLink_LoadWebLinks(text)
            if (webLinks != null) try {
                memScoped {
                    val left = alloc<DoubleVar>()
                    val top = alloc<DoubleVar>()
                    val right = alloc<DoubleVar>()
                    val bottom = alloc<DoubleVar>()
                    for (linkIndex in 0 until FPDFLink_CountWebLinks(webLinks)) {
                        for (rectIndex in 0 until FPDFLink_CountRects(webLinks, linkIndex)) {
                            if (FPDFLink_GetRect(webLinks, linkIndex, rectIndex, left.ptr, top.ptr, right.ptr, bottom.ptr) != 0) {
                                result += pdfRectToNormalized(left.value, top.value, right.value, bottom.value)
                            }
                        }
                    }
                }
            } finally {
                FPDFLink_CloseWebLinks(webLinks)
            }
        }
        pdfLinkLog { "bounds annotation-and-web count=${result.size}" }
        return result.distinct()
    }

    private fun pdfRectToNormalized(left: Double, top: Double, right: Double, bottom: Double): PdfPageBounds {
        val normalizedLeft = (minOf(left, right) / pageWidth).toFloat().coerceIn(0f, 1f)
        val normalizedRight = (maxOf(left, right) / pageWidth).toFloat().coerceIn(0f, 1f)
        val normalizedTop = (1.0 - maxOf(top, bottom) / pageHeight).toFloat().coerceIn(0f, 1f)
        val normalizedBottom = (1.0 - minOf(top, bottom) / pageHeight).toFloat().coerceIn(0f, 1f)
        return PdfPageBounds(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom)
    }

    private fun webLinkAtPdfPoint(pdfX: Double, pdfY: Double): PdfLinkTarget? {
        val text = textPage ?: return null
        val webLinks = FPDFLink_LoadWebLinks(text) ?: return null
        return try {
            val linkCount = FPDFLink_CountWebLinks(webLinks)
            memScoped {
                val left = alloc<DoubleVar>()
                val top = alloc<DoubleVar>()
                val right = alloc<DoubleVar>()
                val bottom = alloc<DoubleVar>()
                for (linkIndex in 0 until linkCount) {
                    val rectCount = FPDFLink_CountRects(webLinks, linkIndex)
                    var containsPoint = false
                    for (rectIndex in 0 until rectCount) {
                        val ok = FPDFLink_GetRect(
                            webLinks,
                            linkIndex,
                            rectIndex,
                            left.ptr,
                            top.ptr,
                            right.ptr,
                            bottom.ptr
                        )
                        if (ok != 0 &&
                            pdfX in minOf(left.value, right.value)..maxOf(left.value, right.value) &&
                            pdfY in minOf(bottom.value, top.value)..maxOf(bottom.value, top.value)
                        ) {
                            containsPoint = true
                            break
                        }
                    }
                    if (!containsPoint) continue
                    val length = FPDFLink_GetURL(webLinks, linkIndex, null, 0)
                    if (length <= 1) continue
                    val buffer = allocArray<UShortVar>(length)
                    val written = FPDFLink_GetURL(webLinks, linkIndex, buffer, length)
                    if (written > 1) {
                        val url = CharArray(written - 1) { index -> buffer[index].toInt().toChar() }
                            .concatToString()
                            .trim()
                        if (url.isNotBlank()) return@memScoped PdfLinkTarget.ExternalUrl(url)
                    }
                }
                null
            }
        } finally {
            FPDFLink_CloseWebLinks(webLinks)
        }
    }

    private fun readUriPath(doc: FPDF_DOCUMENT, action: com.aryan.reader.shared.pdfium.c.FPDF_ACTION): String? {
        // First call to get the byte length (incl. NUL), second call fills.
        val len = FPDFAction_GetURIPath(doc, action, null, 0u)
        if (len <= 0u) return null
        return memScoped {
            val buffer = allocArray<ByteVar>(len.toInt())
            FPDFAction_GetURIPath(doc, action, buffer, len)
            buffer.toKString()
        }
    }

    private fun readFilePath(action: com.aryan.reader.shared.pdfium.c.FPDF_ACTION): String? {
        val len = FPDFAction_GetFilePath(action, null, 0u)
        if (len <= 0u) return null
        return memScoped {
            val buffer = allocArray<ByteVar>(len.toInt())
            FPDFAction_GetFilePath(action, buffer, len)
            buffer.toKString()
        }
    }

    override fun charBoxNormalized(index: Int): PdfPageBounds? {
        val page = textPage ?: return null
        return memScoped {
            val left = alloc<DoubleVar>()
            val right = alloc<DoubleVar>()
            val bottom = alloc<DoubleVar>()
            val top = alloc<DoubleVar>()
            val ok = FPDFText_GetCharBox(page, index, left.ptr, right.ptr, bottom.ptr, top.ptr)
            if (ok == 0) return@memScoped null
            // PDFium returns coords in PDF user-space (origin bottom-left).
            // Convert to normalised page coords with top-left origin.
            val normLeft = (left.value / pageWidth).toFloat().coerceIn(0f, 1f)
            val normRight = (right.value / pageWidth).toFloat().coerceIn(0f, 1f)
            val normTop = (1f - (top.value / pageHeight).toFloat()).coerceIn(0f, 1f)
            val normBottom = (1f - (bottom.value / pageHeight).toFloat()).coerceIn(0f, 1f)
            PdfPageBounds(
                left = minOf(normLeft, normRight),
                top = minOf(normTop, normBottom),
                right = maxOf(normLeft, normRight),
                bottom = maxOf(normTop, normBottom)
            )
        }
    }

    /**
     * Rectangles occupied by `[startIndex, startIndex + length)`. PDFium's
     * `FPDFText_CountRects` / `FPDFText_GetRect` automatically merges adjacent
     * glyphs sharing a font on the same line, matching Android's
     * `textPageGetRectsForRanges` output. Returns normalised page coords with
     * top-left origin.
     */
    override fun rectsForRangeNormalized(startIndex: Int, length: Int): List<PdfPageBounds> {
        val page = textPage ?: return emptyList()
        if (length <= 0) return emptyList()
        val rectCount = FPDFText_CountRects(page, startIndex, length)
        if (rectCount <= 0) return emptyList()
        val result = ArrayList<PdfPageBounds>(rectCount)
        memScoped {
            val left = alloc<DoubleVar>()
            val top = alloc<DoubleVar>()
            val right = alloc<DoubleVar>()
            val bottom = alloc<DoubleVar>()
            for (rectIndex in 0 until rectCount) {
                val ok = FPDFText_GetRect(page, rectIndex, left.ptr, top.ptr, right.ptr, bottom.ptr)
                if (ok == 0) continue
                val normLeft = (left.value / pageWidth).toFloat().coerceIn(0f, 1f)
                val normRight = (right.value / pageWidth).toFloat().coerceIn(0f, 1f)
                val normTop = (1f - (top.value / pageHeight).toFloat()).coerceIn(0f, 1f)
                val normBottom = (1f - (bottom.value / pageHeight).toFloat()).coerceIn(0f, 1f)
                result += PdfPageBounds(
                    left = minOf(normLeft, normRight),
                    top = minOf(normTop, normBottom),
                    right = maxOf(normLeft, normRight),
                    bottom = maxOf(normTop, normBottom)
                )
            }
        }
        return result
    }

    /** UTF-16 text for `[startIndex, startIndex + length)`, or `null` on failure. */
    override fun textForRange(startIndex: Int, length: Int): String? {
        val page = textPage ?: return null
        if (length <= 0) return null
        return memScoped {
            val buffer = allocArray<UShortVar>(length + 1)
            val written = FPDFText_GetText(page, startIndex, length, buffer)
            if (written <= 0) return@memScoped null
            val chars = CharArray(written) { index -> buffer[index].toInt().toChar() }
            chars.concatToString().trimEnd('\u0000')
        }
    }

    override fun close() {
        textPage?.let { FPDFText_ClosePage(it) }
        page?.let { FPDF_ClosePage(it) }
        document?.let { FPDF_CloseDocument(it) }
    }

    companion object {
        private fun ensureLibraryInitialized() {
            // A page must be rendered before it can receive selection gestures, so the shared
            // renderer has already initialized PDFium through this process-wide runtime.
            IosPdfiumRuntime.ensureInitialized()
        }

        fun open(path: String?, pageIndex: Int): IosPdfTextPage? {
            val resolvedPath = path.resolvedIosPdfPath() ?: return null
            if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) return null
            ensureLibraryInitialized()
            val document = FPDF_LoadDocument(resolvedPath, null) ?: return null
            return try {
                val pageCount = FPDF_GetPageCount(document).coerceAtLeast(1)
                val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
                val page = FPDF_LoadPage(document, safePageIndex) ?: run {
                    FPDF_CloseDocument(document)
                    return null
                }
                val textPage = FPDFText_LoadPage(page) ?: run {
                    FPDF_ClosePage(page)
                    FPDF_CloseDocument(document)
                    return null
                }
                val pageWidth = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                val pageHeight = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                IosPdfTextPage(
                    document = document,
                    page = page,
                    textPage = textPage,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight
                )
            } catch (cause: Throwable) {
                FPDF_CloseDocument(document)
                null
            }
        }

        private fun String?.resolvedIosPdfPath(): String? {
            val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (!value.startsWith("file://")) return value
            return NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
        }
    }
}
