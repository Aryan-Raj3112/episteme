@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetLinkedAnnot
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetRect
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetStringValue
import com.aryan.reader.shared.pdfium.c.FPDFAnnot_GetSubtype
import com.aryan.reader.shared.pdfium.c.FPDFPage_CloseAnnot
import com.aryan.reader.shared.pdfium.c.FPDFPage_GetAnnot
import com.aryan.reader.shared.pdfium.c.FPDFPage_GetAnnotCount
import com.aryan.reader.shared.pdfium.c.FPDF_ANNOT_LINK
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FS_RECTF
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager

internal suspend fun loadIosPdfEmbeddedAnnotations(
    path: String?,
    pageIndex: Int,
    password: String?,
): List<SharedPdfEmbeddedAnnotation> = withContext(Dispatchers.Default) {
    val source = path?.takeIf(NSFileManager.defaultManager::fileExistsAtPath) ?: return@withContext emptyList()
    IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(source, password) ?: return@withLock emptyList()
        try {
            val page = FPDF_LoadPage(document, pageIndex) ?: return@withLock emptyList()
            try {
                val width = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                val height = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                val count = FPDFPage_GetAnnotCount(page).coerceAtLeast(0)
                val annotations = buildList {
                    repeat(count) { index ->
                        val annotation = FPDFPage_GetAnnot(page, index) ?: return@repeat
                        try {
                            val subtype = FPDFAnnot_GetSubtype(annotation)
                            if (subtype == FPDF_ANNOT_LINK) return@repeat
                            val bounds = memScoped {
                                val rect = alloc<FS_RECTF>()
                                if (FPDFAnnot_GetRect(annotation, rect.ptr) == 0) null
                                else rect.toSharedPdfBounds(width, height)
                            } ?: return@repeat
                            val name = annotation.iosPdfString("NM")
                            add(
                                SharedPdfEmbeddedAnnotation(
                                    id = name.ifBlank { "embedded_${pageIndex}_$index" },
                                    pageIndex = pageIndex,
                                    index = index,
                                    subtype = subtype,
                                    bounds = bounds,
                                    contents = annotation.iosPdfString("Contents").ifBlank {
                                        // /RC is XHTML markup; never surface it raw.
                                        sharedPdfEmbeddedAnnotationRichText(annotation.iosPdfString("RC"))
                                    },
                                    author = annotation.iosPdfString("T"),
                                    name = name,
                                    inReplyTo = annotation.iosPdfLinkedName("IRT"),
                                ),
                            )
                        } finally {
                            FPDFPage_CloseAnnot(annotation)
                        }
                    }
                }
                SharedPdfEmbeddedAnnotationThreads.group(annotations)
            } finally {
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }
}

private fun FS_RECTF.toSharedPdfBounds(pageWidth: Float, pageHeight: Float): PdfPageBounds? {
    return normalizedPdfPageBounds(left, bottom, right, top, pageWidth, pageHeight)
}

private fun com.aryan.reader.shared.pdfium.c.FPDF_ANNOTATION.iosPdfString(key: String): String {
    val byteCount = FPDFAnnot_GetStringValue(this, key, null, 0u).toInt()
    if (byteCount <= 2) return ""
    val utf16 = UShortArray((byteCount + 1) / 2)
    val written = utf16.usePinned { pinned ->
        FPDFAnnot_GetStringValue(this, key, pinned.addressOf(0), byteCount.toULong()).toInt()
    }
    if (written <= 2) return ""
    return utf16.takeWhile { it != 0.toUShort() }.map { it.toInt().toChar() }.joinToString("")
}

private fun com.aryan.reader.shared.pdfium.c.FPDF_ANNOTATION.iosPdfLinkedName(key: String): String {
    val linked = FPDFAnnot_GetLinkedAnnot(this, key) ?: return ""
    return try {
        linked.iosPdfString("NM")
    } finally {
        FPDFPage_CloseAnnot(linked)
    }
}
