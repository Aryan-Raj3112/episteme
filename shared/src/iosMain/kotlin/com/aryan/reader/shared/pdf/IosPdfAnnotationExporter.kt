@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFANNOT_COLORTYPE_Color
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_BGRA
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_CreateEx
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Destroy
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_AddInkStroke
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_AppendAttachmentPoints
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetBorder
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetColor
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetRect
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetFlags
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_SetStringValue
import com.aryan.reader.shared.pdfium.c.FPDFPage_CloseAnnot
import com.aryan.reader.shared.pdfium.c.FPDFPage_CreateAnnot
import com.aryan.reader.shared.pdfium.c.FPDFPage_GenerateContent
import com.aryan.reader.shared.pdfium.c.FPDFPage_InsertObject
import com.aryan.reader.shared.pdfium.c.FPDFPageObj_NewImageObj
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
import platform.posix.fwrite

private const val IOS_PDF_ANNOTATION_FLAG_PRINT = 4

internal suspend fun exportIosPdfAnnotations(
    sourcePath: String,
    destinationPath: String,
    password: String?,
    snapshot: SharedPdfExportSnapshot,
): Boolean = withContext(Dispatchers.Default) {
    IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val sourceDocument = FPDF_LoadDocument(sourcePath, password) ?: return@withLock false
        var destinationDocument: com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT? = null
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
                    )
                    rasterization.overlays.forEach { overlay ->
                        annotationsWritten = addIosPdfRasterOverlay(document, page, overlay, width, height) && annotationsWritten
                    }
                    if (rasterization.overlays.isNotEmpty()) {
                        annotationsWritten = FPDFPage_GenerateContent(page) != 0 && annotationsWritten
                    }
                    annotationsWritten = rasterization.complete && annotationsWritten
                } finally {
                    FPDF_ClosePage(page)
                }
            }
            annotationsWritten && saveIosPdfDocument(document, destinationPath)
        } finally {
            destinationDocument?.let { FPDF_CloseDocument(it) } ?: FPDF_CloseDocument(sourceDocument)
            if (destinationDocument != null) FPDF_CloseDocument(sourceDocument)
        }
    }
}

private fun addIosPdfRasterOverlay(
    document: com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT,
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    overlay: IosPdfRasterOverlay,
    pageWidth: Float,
    pageHeight: Float,
): Boolean = overlay.bgraPixels.usePinned { pixels ->
    val bitmap = FPDFBitmap_CreateEx(
        overlay.width,
        overlay.height,
        FPDFBitmap_BGRA,
        pixels.addressOf(0),
        overlay.width * 4,
    ) ?: return@usePinned false
    try {
        val image = FPDFPageObj_NewImageObj(document) ?: return@usePinned false
        if (FPDFImageObj_SetBitmap(null, 0, image, bitmap) == 0) return@usePinned false
        val left = overlay.bounds.left * pageWidth
        val bottom = (1f - overlay.bounds.bottom) * pageHeight
        val width = (overlay.bounds.right - overlay.bounds.left) * pageWidth
        val height = (overlay.bounds.bottom - overlay.bounds.top) * pageHeight
        if (FPDFImageObj_SetMatrix(image, width.toDouble(), 0.0, 0.0, height.toDouble(), left.toDouble(), bottom.toDouble()) == 0) {
            return@usePinned false
        }
        FPDFPage_InsertObject(page, image)
        true
    } finally {
        FPDFBitmap_Destroy(bitmap)
    }
}

private fun addIosPdfInkAnnotation(
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    ink: SharedPdfInkAnnotationExport,
    pageWidth: Float,
    pageHeight: Float,
): Boolean {
    val points = ink.pdfInkAppearancePoints(pageWidth, pageHeight)
    if (points.size < 2) return false
    val annotation = FPDFPage_CreateAnnot(page, FPDF_ANNOT_INK) ?: return false
    return try {
        memScoped {
            val nativePoints = allocArray<FS_POINTF>(points.size)
            points.forEachIndexed { index, point ->
                nativePoints[index].x = point.x.coerceIn(0f, 1f) * pageWidth
                nativePoints[index].y = (1f - point.y.coerceIn(0f, 1f)) * pageHeight
            }
            if (FPDFAnnot_AddInkStroke(annotation, nativePoints, points.size.toULong()) < 0) return@memScoped false
            val padding = ink.strokeWidth * pageWidth
            val bounds = alloc<FS_RECTF> {
                left = (points.minOf { it.x } * pageWidth - padding).coerceAtLeast(0f)
                right = (points.maxOf { it.x } * pageWidth + padding).coerceAtMost(pageWidth)
                top = ((1f - points.minOf { it.y }) * pageHeight + padding).coerceAtMost(pageHeight)
                bottom = ((1f - points.maxOf { it.y }) * pageHeight - padding).coerceAtLeast(0f)
            }
            FPDFAnnot_SetRect(annotation, bounds.ptr)
            FPDFAnnot_SetBorder(annotation, 0f, 0f, ink.strokeWidth * pageWidth)
            setIosPdfAnnotationColor(annotation, ink.colorArgb)
            FPDFAnnot_SetFlags(annotation, IOS_PDF_ANNOTATION_FLAG_PRINT)
            setIosPdfAnnotationMetadata(annotation, ink.id, ink.contents)
            true
        }
    } finally {
        FPDFPage_CloseAnnot(annotation)
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
    val annotation = FPDFPage_CreateAnnot(page, subtype) ?: return false
    return try {
        memScoped {
            var attachmentPointsWritten = true
            highlight.boundsList.forEach { bounds ->
                val left = bounds.left * pageWidth
                val right = bounds.right * pageWidth
                val top = (1f - bounds.top) * pageHeight
                val bottom = (1f - bounds.bottom) * pageHeight
                val quad = alloc<FS_QUADPOINTSF> {
                    x1 = left; y1 = top; x2 = right; y2 = top
                    x3 = left; y3 = bottom; x4 = right; y4 = bottom
                }
                attachmentPointsWritten = FPDFAnnot_AppendAttachmentPoints(annotation, quad.ptr) != 0 && attachmentPointsWritten
            }
            val rect = alloc<FS_RECTF> {
                left = highlight.boundsList.minOf { it.left } * pageWidth
                right = highlight.boundsList.maxOf { it.right } * pageWidth
                top = (1f - highlight.boundsList.minOf { it.top }) * pageHeight
                bottom = (1f - highlight.boundsList.maxOf { it.bottom }) * pageHeight
            }
            FPDFAnnot_SetRect(annotation, rect.ptr)
            setIosPdfAnnotationColor(annotation, highlight.colorArgb)
            FPDFAnnot_SetFlags(annotation, IOS_PDF_ANNOTATION_FLAG_PRINT)
            setIosPdfAnnotationMetadata(annotation, highlight.id, highlight.contents)
            val commentsWritten = highlight.comments.all { comment ->
                addIosPdfHighlightComment(page, comment, rect, pageWidth, pageHeight, highlight.colorArgb)
            }
            attachmentPointsWritten && commentsWritten
        }
    } finally {
        FPDFPage_CloseAnnot(annotation)
    }
}

private fun addIosPdfHighlightComment(
    page: com.aryan.reader.shared.pdfium.c.FPDF_PAGE,
    comment: SharedPdfHighlightCommentExport,
    highlightRect: FS_RECTF,
    pageWidth: Float,
    pageHeight: Float,
    commentColorArgb: Int,
): Boolean {
    val annotation = FPDFPage_CreateAnnot(page, FPDF_ANNOT_TEXT) ?: return false
    return try {
        memScoped {
            val size = 24f.coerceAtMost(minOf(pageWidth, pageHeight))
            val left = highlightRect.right.coerceIn(0f, (pageWidth - size).coerceAtLeast(0f))
            val bottom = (highlightRect.top - size).coerceIn(0f, (pageHeight - size).coerceAtLeast(0f))
            val rect = alloc<FS_RECTF> {
                this.left = left
                right = left + size
                this.bottom = bottom
                top = bottom + size
            }
            FPDFAnnot_SetRect(annotation, rect.ptr)
            setIosPdfAnnotationColor(annotation, commentColorArgb)
            FPDFAnnot_SetFlags(annotation, IOS_PDF_ANNOTATION_FLAG_PRINT)
            setIosPdfAnnotationString(annotation, "NM", comment.id)
            if (comment.author.isNotBlank()) setIosPdfAnnotationString(annotation, "T", comment.author)
            setIosPdfAnnotationString(annotation, "Contents", comment.contents)
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
