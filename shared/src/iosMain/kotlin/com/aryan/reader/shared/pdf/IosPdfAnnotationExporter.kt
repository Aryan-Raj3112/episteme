@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFANNOT_COLORTYPE_Color
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_BGRA
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_CreateEx
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Destroy
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_AddInkStroke
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_AppendAttachmentPoints
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetAP
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetBorder
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetColor
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetRect
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetFlags
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetStringValue
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_APPEARANCEMODE_NORMAL
import com.aryan.reader.shared.pdfium.c.FPDFPage_CloseAnnot
import com.aryan.reader.shared.pdfium.c.FPDFPage_CreateAnnot
import com.aryan.reader.shared.pdfium.c.FPDFPage_GenerateContent
import com.aryan.reader.shared.pdfium.c.FPDFPage_InsertObject
import com.aryan.reader.shared.pdfium.c.FPDFPageObj_NewImageObj
import com.aryan.reader.shared.pdfium.c.FPDFPage_GetRotation
import com.aryan.reader.shared.pdfium.c.FPDFImageObj_SetBitmap
import com.aryan.reader.shared.pdfium.c.FPDFImageObj_SetMatrix
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_HIGHLIGHT
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_INK
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_SQUIGGLY
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_STRIKEOUT
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_TEXT
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_UNDERLINE
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_CreateNewDocument
import com.aryan.reader.shared.pdfium.c.FPDF_FILEWRITE
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDFPage_New
import com.aryan.reader.shared.pdfium.c.FPDF_ImportPagesByIndex
import com.aryan.reader.shared.pdfium.c.FPDF_NO_INCREMENTAL
import com.aryan.reader.shared.pdfium.c.FPDF_SaveAsCopy
import com.aryan.reader.shared.pdfium.c.FS_POINTF
import com.aryan.reader.shared.pdfium.c.FS_QUADPOINTSF
import com.aryan.reader.shared.pdfium.c.FS_RECTF
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.free
import platform.posix.fwrite
import platform.posix.malloc
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min
import com.aryan.reader.shared.pdf.sharedPdfDisplayPointToMediabox
import com.aryan.reader.shared.pdf.sharedPdfDisplayRectToMediabox
import com.aryan.reader.shared.pdf.sharedPdfRasterImageMatrix
import com.aryan.reader.shared.pdf.sharedPdfRotationDegreesFromPdfiumCode

private const val IOS_PDF_ANNOTATION_FLAG_PRINT = 4
private const val IOS_PDF_HIGHLIGHT_ALPHA = 102u
private const val IOS_PDF_MARKUP_ALPHA = 235u

/**
 * Raster overlay pixel buffers must outlive `FPDF_SaveAsCopy`. PDFium's
 * external `FPDFBitmap_CreateEx` buffer is not copied on `FPDFImageObj_SetBitmap`,
 * so releasing the pin/bitmap before save embeds freed memory (gibberish glyphs).
 * Mirrors Android `rasterBitmapsToDestroy` and Desktop `DesktopPdfRasterResource`.
 */
private class IosPdfRasterResource(
    val bitmap: com.aryan.reader.shared.pdfium.c.FPDF_BITMAP,
    val nativePixels: CPointer<*>,
) {
    fun release() {
        FPDFBitmap_Destroy(bitmap)
        free(nativePixels)
    }
}

internal suspend fun exportIosPdfAnnotations(
    sourcePath: String,
    destinationPath: String,
    password: String?,
    snapshot: SharedPdfExportSnapshot,
): Boolean = withContext(Dispatchers.Default) {
    // The caller surfaces false as an "Unable to export" message; never throw, or the reader
    // format dialog dismisses with no feedback at all.
    try {
        IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val sourceDocument = FPDF_LoadDocument(sourcePath, password) ?: return@withLock false
        var destinationDocument: com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT? = null
        val retainedRasters = mutableListOf<IosPdfRasterResource>()
        try {
            val pageCount = FPDF_GetPageCount(sourceDocument).coerceAtLeast(0)
            val virtualLayout = buildSharedPdfVirtualPageLayout(
                pageCount = pageCount,
                insertions = snapshot.state.blankPageInsertions,
            )
            val document = if (snapshot.state.blankPageInsertions.isEmpty()) {
                sourceDocument
            } else {
                val importedDocument = FPDF_CreateNewDocument() ?: return@withLock false
                destinationDocument = importedDocument
                var imported = true
                virtualLayout.forEachIndexed { outputIndex, virtualPage ->
                    if (!imported) return@forEachIndexed
                    when (virtualPage) {
                        is SharedPdfVirtualPage.PdfPage -> {
                            val sourceIndex = intArrayOf(virtualPage.pdfIndex)
                            imported = FPDF_ImportPagesByIndex(
                                importedDocument,
                                sourceDocument,
                                sourceIndex.usePinned { it.addressOf(0) },
                                1u,
                                outputIndex,
                            ) != 0
                        }
                        is SharedPdfVirtualPage.BlankPage -> {
                            val page = FPDFPage_New(
                                importedDocument,
                                outputIndex,
                                virtualPage.insertion.widthPx.toDouble().coerceAtLeast(1.0),
                                virtualPage.insertion.heightPx.toDouble().coerceAtLeast(1.0),
                            )
                            page?.let { FPDF_ClosePage(it) }
                            imported = page != null
                        }
                    }
                }
                if (!imported) return@withLock false
                importedDocument
            }
            val payload = SharedPdfAnnotationExportMapper.build(snapshot.state.annotations)
            val textFontRegistry = buildIosPdfTextFontRegistry(
                annotations = snapshot.state.annotations,
                richTextLayouts = snapshot.richTextPageLayouts,
            )
            val inkByPage = payload.inkAnnotations.groupBy { it.pageIndex }
            val highlightsByPage = payload.highlightAnnotations.groupBy { it.pageIndex }
            val textPageIndices = snapshot.state.annotations
                .filter { it.kind == PdfAnnotationKind.TEXT && it.text.isNotBlank() }
                .mapTo(mutableSetOf(), SharedPdfAnnotation::pageIndex)
            val richTextPageIndices = snapshot.richTextPageLayouts
                .filter { it.visibleText.any { char -> !char.isWhitespace() } }
                .mapTo(mutableSetOf(), SharedPdfRichPageLayout::pageIndex)
            var annotationsWritten = true
            val outputPageCount = FPDF_GetPageCount(document).coerceAtLeast(0)
            (inkByPage.keys + highlightsByPage.keys + textPageIndices + richTextPageIndices).forEach { pageIndex ->
                if (pageIndex !in 0 until outputPageCount) {
                    annotationsWritten = false
                    return@forEach
                }
                val page = FPDF_LoadPage(document, pageIndex)
                if (page == null) {
                    annotationsWritten = false
                    return@forEach
                }
                try {
                    val width = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                    val height = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                    inkByPage[pageIndex].orEmpty().forEach {
                        annotationsWritten = addIosPdfInkAnnotation(page, it, width, height) && annotationsWritten
                    }
                    highlightsByPage[pageIndex].orEmpty().forEach {
                        annotationsWritten = addIosPdfHighlightAnnotation(page, it, width, height) && annotationsWritten
                    }
                    val rasterization = buildIosPdfTextRasterOverlays(
                        pageIndex,
                        width,
                        height,
                        snapshot.state.annotations,
                        snapshot.richTextPageLayouts,
                        fontRegistry = textFontRegistry,
                        richTextExportScale = snapshot.richTextExportScale,
                    )
                    rasterization.overlays.forEach { overlay ->
                        addIosPdfRasterOverlay(
                            document, page, overlay, width, height, retainedRasters,
                        ).let { ok -> annotationsWritten = ok && annotationsWritten }
                    }
                    if (rasterization.overlays.isNotEmpty()) {
                        annotationsWritten = FPDFPage_GenerateContent(page) != 0 && annotationsWritten
                    }
                    annotationsWritten = rasterization.complete && annotationsWritten
                } finally {
                    FPDF_ClosePage(page)
                }
            }
            // Android benchmark (pdfium_bridge.cpp): save whenever the document is writable;
            // partial annotation failures are logged there but do not block the save.
            // Returning false only for a failed write keeps "Unable to export" for real I/O errors.
            val saved = saveIosPdfDocument(document, destinationPath)
            if (!annotationsWritten) {
                SharedPdfRichTextLog.d("exportIosPdfAnnotations partial failures; saved=$saved")
            }
            saved
        } finally {
            destinationDocument?.let { FPDF_CloseDocument(it) } ?: FPDF_CloseDocument(sourceDocument)
            if (destinationDocument != null) FPDF_CloseDocument(sourceDocument)
            retainedRasters.forEach { it.release() }
        }
        }
    } catch (_: Throwable) {
        false
    }
}

private fun addIosPdfRasterOverlay(
    document: com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT,
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    overlay: IosPdfRasterOverlay,
    pageWidth: Float,
    pageHeight: Float,
    retainedRasters: MutableList<IosPdfRasterResource>,
): Boolean {
    val rotationDegrees = sharedPdfRotationDegreesFromPdfiumCode(FPDFPage_GetRotation(page))
    val matrix = sharedPdfRasterImageMatrix(
        left = overlay.bounds.left,
        top = overlay.bounds.top,
        right = overlay.bounds.right,
        bottom = overlay.bounds.bottom,
        displayWidth = pageWidth,
        displayHeight = pageHeight,
        rotationDegrees = rotationDegrees,
    ) ?: return false

    val pixelBytes = overlay.bgraPixels
    val byteCount = pixelBytes.size
    if (overlay.width <= 0 || overlay.height <= 0 || byteCount < overlay.width * overlay.height * 4) {
        return false
    }
    // Native copy so pdfium keeps a stable buffer until release() after save; Kotlin
    // usePinned only guarantees the pin for the duration of the block.
    val nativePixels = malloc(byteCount.toULong()) ?: return false
    pixelBytes.usePinned { pinned ->
        memcpy(nativePixels, pinned.addressOf(0), byteCount.toULong())
    }
    val bitmap = FPDFBitmap_CreateEx(
        overlay.width,
        overlay.height,
        FPDFBitmap_BGRA,
        nativePixels,
        overlay.width * 4,
    )
    if (bitmap == null) {
        free(nativePixels)
        return false
    }
    val image = FPDFPageObj_NewImageObj(document)
    if (image == null ||
        FPDFImageObj_SetBitmap(null, 0, image, bitmap) == 0 ||
        FPDFImageObj_SetMatrix(
            image,
            matrix.a,
            matrix.b,
            matrix.c,
            matrix.d,
            matrix.e,
            matrix.f,
        ) == 0
    ) {
        FPDFBitmap_Destroy(bitmap)
        free(nativePixels)
        return false
    }
    FPDFPage_InsertObject(page, image)
    retainedRasters += IosPdfRasterResource(bitmap, nativePixels)
    return true
}

private fun addIosPdfInkAnnotation(
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    ink: SharedPdfInkAnnotationExport,
    pageWidth: Float,
    pageHeight: Float,
): Boolean {
    val points = ink.pdfInkAppearancePoints(pageWidth, pageHeight)
    if (points.isEmpty()) return false
    val rotationDegrees = sharedPdfRotationDegreesFromPdfiumCode(FPDFPage_GetRotation(page))
    val pagePoints = points.mapNotNull { point ->
        sharedPdfDisplayPointToMediabox(
            xNorm = point.x,
            yNorm = point.y,
            displayWidth = pageWidth,
            displayHeight = pageHeight,
            rotationDegrees = rotationDegrees,
        )?.let { (x, y) -> PdfPagePoint(x = x, y = y) }
    }
    if (pagePoints.size != points.size || pagePoints.isEmpty()) return false
    val annotation = FPDFPage_CreateAnnot(page, FPDF_ANNOT_INK) ?: return false
    return try {
        memScoped {
            val nativePoints = allocArray<FS_POINTF>(pagePoints.size)
            pagePoints.forEachIndexed { index, point ->
                nativePoints[index].x = point.x
                nativePoints[index].y = point.y
            }
            if (FPDFAnnot_AddInkStroke(annotation, nativePoints, pagePoints.size.toULong()) < 0) return@memScoped false
            // Android benchmark (pdfium_bridge.cpp ink path + DesktopPdfium): stroke width has a
            // 0.25pt floor, the annot rect pads by 1.5x stroke, and opaque highlighters burn at
            // alpha 102 to match the on-screen chisel rendering.
            val strokeWidth = max(0.25f, ink.strokeWidth * pageWidth)
            val padding = strokeWidth * 1.5f
            val minX = pagePoints.minOf { it.x }
            val maxX = pagePoints.maxOf { it.x }
            val minY = pagePoints.minOf { it.y }
            val maxY = pagePoints.maxOf { it.y }
            val bounds = alloc<FS_RECTF> {
                left = minX - padding
                right = maxX + padding
                top = maxY + padding
                bottom = minY - padding
            }
            FPDFAnnot_SetRect(annotation, bounds.ptr)
            FPDFAnnot_SetBorder(annotation, 0f, 0f, strokeWidth)
            setIosPdfAnnotationColor(annotation, iosPdfInkColorArgb(ink))
            FPDFAnnot_SetFlags(annotation, IOS_PDF_ANNOTATION_FLAG_PRINT)
            setIosPdfAnnotationMetadata(annotation, ink.id, ink.contents)
            // Preview.app draws the annotation border rectangle when /AP is missing
            // and does not rebuild the path from /InkList. SetBorder clears any
            // existing appearance, so install the stroke stream after the border.
            val appearance = sharedPdfInkAppearanceContent(
                pagePoints = pagePoints,
                strokeWidthPdfUnits = strokeWidth,
                colorArgb = iosPdfInkColorArgb(ink),
            )
            if (appearance.isNotEmpty()) {
                setIosPdfAnnotationAppearance(annotation, appearance)
            }
            // Android benchmark (pdfium_bridge.cpp): GenerateContent is best-effort after ink; a
            // zero return does not fail the export, only raster requires it.
            FPDFPage_GenerateContent(page)
            true
        }
    } finally {
        FPDFPage_CloseAnnot(annotation)
    }
}

private fun setIosPdfAnnotationAppearance(
    annotation: com.aryan.reader.shared.pdfium.c.FPDF_ANNOTATION,
    content: String,
) {
    // FPDFAnnot_SetAP expects UTF-16LE (NUL-terminated wide string).
    val utf16 = UShortArray(content.length + 1) { index ->
        content.getOrNull(index)?.code?.toUShort() ?: 0u
    }
    utf16.usePinned { pinned ->
        FPDFAnnot_SetAP(annotation, FPDF_ANNOT_APPEARANCEMODE_NORMAL, pinned.addressOf(0))
    }
}

private fun iosPdfInkColorArgb(ink: SharedPdfInkAnnotationExport): Int {
    val alpha = (ink.colorArgb ushr 24) and 0xFF
    if (alpha != 255) return ink.colorArgb
    return when (ink.tool) {
        PdfInkTool.HIGHLIGHTER, PdfInkTool.HIGHLIGHTER_ROUND ->
            (ink.colorArgb and 0x00FFFFFF) or (102 shl 24)
        else -> ink.colorArgb
    }
}

private fun addIosPdfHighlightAnnotation(
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    highlight: SharedPdfHighlightAnnotationExport,
    pageWidth: Float,
    pageHeight: Float,
): Boolean {
    val subtype = when (highlight.style) {
        com.aryan.reader.shared.HighlightStyle.BACKGROUND -> FPDF_ANNOT_HIGHLIGHT
        com.aryan.reader.shared.HighlightStyle.UNDERLINE -> FPDF_ANNOT_UNDERLINE
        com.aryan.reader.shared.HighlightStyle.WAVY_UNDERLINE -> FPDF_ANNOT_SQUIGGLY
        com.aryan.reader.shared.HighlightStyle.STRIKETHROUGH -> FPDF_ANNOT_STRIKEOUT
    }
    if (highlight.boundsList.isEmpty()) return false
    val rotationDegrees = sharedPdfRotationDegreesFromPdfiumCode(FPDFPage_GetRotation(page))
    val annotation = FPDFPage_CreateAnnot(page, subtype) ?: return false
    return try {
        memScoped {
            var attachmentPointsWritten = true
            var unionLeft = Float.MAX_VALUE
            var unionRight = -Float.MAX_VALUE
            var unionTop = -Float.MAX_VALUE
            var unionBottom = Float.MAX_VALUE
            var quadCount = 0
            highlight.boundsList.forEach { bounds ->
                val rect = sharedPdfDisplayRectToMediabox(
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    displayWidth = pageWidth,
                    displayHeight = pageHeight,
                    rotationDegrees = rotationDegrees,
                ) ?: return@forEach
                val quad = alloc<FS_QUADPOINTSF> {
                    x1 = rect.left; y1 = rect.top; x2 = rect.right; y2 = rect.top
                    x3 = rect.left; y3 = rect.bottom; x4 = rect.right; y4 = rect.bottom
                }
                attachmentPointsWritten = FPDFAnnot_AppendAttachmentPoints(annotation, quad.ptr) != 0 && attachmentPointsWritten
                if (quadCount == 0) {
                    unionLeft = rect.left; unionRight = rect.right; unionTop = rect.top; unionBottom = rect.bottom
                } else {
                    unionLeft = min(unionLeft, rect.left)
                    unionRight = max(unionRight, rect.right)
                    unionTop = max(unionTop, rect.top)
                    unionBottom = min(unionBottom, rect.bottom)
                }
                quadCount++
            }
            if (quadCount == 0) return@memScoped false
            // Android pads the union rect by 1pt so thin highlights keep a valid annot rect.
            val rect = alloc<FS_RECTF> {
                left = min(unionLeft, unionRight) - 1f
                right = max(unionLeft, unionRight) + 1f
                top = max(unionTop, unionBottom) + 1f
                bottom = min(unionTop, unionBottom) - 1f
            }
            FPDFAnnot_SetRect(annotation, rect.ptr)
            setIosPdfAnnotationColor(annotation, iosPdfHighlightColorArgb(highlight))
            FPDFAnnot_SetFlags(annotation, IOS_PDF_ANNOTATION_FLAG_PRINT)
            setIosPdfAnnotationMetadata(annotation, highlight.id, highlight.contents)
            var commentsWritten = true
            highlight.comments.forEachIndexed { index, comment ->
                commentsWritten = addIosPdfHighlightComment(
                    page, comment, unionRight, unionTop, pageWidth, pageHeight,
                    highlight.colorArgb, index,
                ) && commentsWritten
            }
            // Android benchmark: GenerateContent after highlight is best-effort (only raster
            // failures mark the export hadFailure).
            FPDFPage_GenerateContent(page)
            attachmentPointsWritten && commentsWritten
        }
    } finally {
        FPDFPage_CloseAnnot(annotation)
    }
}

private fun iosPdfHighlightColorArgb(highlight: SharedPdfHighlightAnnotationExport): Int {
    val alpha = (highlight.colorArgb ushr 24) and 0xFF
    if (alpha != 255) return highlight.colorArgb
    // Android benchmark (pdfium_bridge.cpp): opaque highlights burn at 102, other markup at 235.
    val targetAlpha = when (highlight.style) {
        com.aryan.reader.shared.HighlightStyle.BACKGROUND -> IOS_PDF_HIGHLIGHT_ALPHA
        else -> IOS_PDF_MARKUP_ALPHA
    }.toInt()
    return (highlight.colorArgb and 0x00FFFFFF) or (targetAlpha shl 24)
}

private fun addIosPdfHighlightComment(
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    comment: SharedPdfHighlightCommentExport,
    anchorRight: Float,
    anchorTop: Float,
    pageWidth: Float,
    pageHeight: Float,
    highlightColorArgb: Int,
    commentIndex: Int,
): Boolean {
    val annotation = FPDFPage_CreateAnnot(page, FPDF_ANNOT_TEXT) ?: return false
    return try {
        memScoped {
            // Android benchmark (make_pdf_comment_rect): icon scales with page width, stacks by
            // index, and clamps inside the page so the note icon is always tappable.
            val iconSize = min(18f, max(10f, pageWidth * 0.03f))
            val left = (anchorRight + 2f).coerceIn(0f, (pageWidth - iconSize).coerceAtLeast(0f))
            var top = anchorTop - commentIndex * (iconSize + 2f)
            if (top > pageHeight) top = pageHeight
            if (top - iconSize < 0f) top = min(pageHeight, iconSize)
            val rect = alloc<FS_RECTF> {
                this.left = left
                right = left + iconSize
                this.bottom = top - iconSize
                this.top = top
            }
            FPDFAnnot_SetRect(annotation, rect.ptr)
            // Android uses the highlight RGB fully opaque for the note icon.
            val opaque = (highlightColorArgb and 0x00FFFFFF) or (255 shl 24)
            setIosPdfAnnotationColor(annotation, opaque)
            FPDFAnnot_SetFlags(annotation, IOS_PDF_ANNOTATION_FLAG_PRINT)
            setIosPdfAnnotationString(annotation, "NM", comment.id)
            if (comment.author.isNotBlank()) setIosPdfAnnotationString(annotation, "T", comment.author)
            setIosPdfAnnotationString(annotation, "Contents", comment.contents)
            sharedPdfDateString(comment.createdAt).takeIf { it.isNotBlank() }?.let {
                setIosPdfAnnotationString(annotation, "CreationDate", it)
            }
            val modified = sharedPdfDateString(comment.modifiedAt).ifBlank { sharedPdfDateString(comment.createdAt) }
            modified.takeIf { it.isNotBlank() }?.let {
                setIosPdfAnnotationString(annotation, "M", it)
            }
            // Note: bundled iOS pdfium headers expose FPDFAnnot_GetLinkedAnnot but not
            // FPDFAnnot_SetLinkedAnnot, so IRT reply linking (Android/Desktop) cannot be set here.
            // The shared mapper already collapses each highlight thread to a single visible
            // "${highlightId}_comments" note, so the full thread survives as Contents without IRT.
            true
        }
    } finally {
        FPDFPage_CloseAnnot(annotation)
    }
}

private fun setIosPdfAnnotationColor(annotation: com.aryan.reader.shared.pdfium.c.FPDF_ANNOTATION, argb: Int) {
    FPDFAnnot_SetColor(
        annotation,
        FPDFANNOT_COLORTYPE_Color,
        ((argb ushr 16) and 0xFF).toUInt(),
        ((argb ushr 8) and 0xFF).toUInt(),
        (argb and 0xFF).toUInt(),
        ((argb ushr 24) and 0xFF).toUInt(),
    )
}

private fun setIosPdfAnnotationMetadata(
    annotation: com.aryan.reader.shared.pdfium.c.FPDF_ANNOTATION,
    id: String,
    contents: String,
) {
    setIosPdfAnnotationString(annotation, "NM", id)
    if (contents.isNotBlank()) setIosPdfAnnotationString(annotation, "Contents", contents)
}

private fun setIosPdfAnnotationString(
    annotation: com.aryan.reader.shared.pdfium.c.FPDF_ANNOTATION,
    key: String,
    value: String,
) {
    val utf16 = UShortArray(value.length + 1) { index ->
        value.getOrNull(index)?.code?.toUShort() ?: 0u
    }
    utf16.usePinned { pinned -> FPDFAnnot_SetStringValue(annotation, key, pinned.addressOf(0)) }
}

private var activeIosPdfExportFile: CPointer<FILE>? = null

private fun saveIosPdfDocument(document: com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT, path: String): Boolean {
    val file = fopen(path, "wb") ?: return false
    activeIosPdfExportFile = file
    return try {
        memScoped {
            val writer = alloc<FPDF_FILEWRITE> {
                version = 1
                WriteBlock = staticCFunction(::writeIosPdfExportBlock)
            }
            FPDF_SaveAsCopy(document, writer.ptr, FPDF_NO_INCREMENTAL.toULong()) != 0
        }
    } finally {
        activeIosPdfExportFile = null
        fclose(file)
    }
}

private fun writeIosPdfExportBlock(
    self: CPointer<FPDF_FILEWRITE>?,
    data: COpaquePointer?,
    size: ULong,
): Int {
    val file = activeIosPdfExportFile ?: return 0
    if (data == null) return 0
    return if (fwrite(data, 1u, size, file) == size) 1 else 0
}
