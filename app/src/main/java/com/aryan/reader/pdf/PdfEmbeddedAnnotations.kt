package com.aryan.reader.pdf

import android.graphics.RectF
import com.aryan.reader.shared.pdf.PdfEmbeddedAnnotationThreadItem
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfiumAnnotationSubtype
import com.aryan.reader.shared.pdf.buildPdfEmbeddedAnnotationThreadPlan

data class EmbeddedAnnotation(
    val index: Int,
    val subtype: Int,
    val rect: RectF,
    val contents: String?,
    val author: String?,
    val name: String?,
    val inReplyTo: String?,
    val replies: MutableList<EmbeddedAnnotation> = mutableListOf()
)

internal fun groupEmbeddedAnnotationsForDisplay(
    annotations: List<EmbeddedAnnotation>
): List<EmbeddedAnnotation> {
    if (annotations.isEmpty()) return emptyList()

    val plan = buildPdfEmbeddedAnnotationThreadPlan(
        annotations = annotations.map { annotation ->
            PdfEmbeddedAnnotationThreadItem(
                name = annotation.name,
                inReplyTo = annotation.inReplyTo,
                bounds = annotation.rect.toSharedBounds(),
                hasVisibleText = !annotation.contents.isNullOrBlank(),
                hasVisibleReply = annotation.replies.any { !it.contents.isNullOrBlank() },
                isPopupAttachment = annotation.subtype == PdfiumAnnotationSubtype.POPUP,
            )
        },
        geometryTolerance = 10f,
    )
    plan.replyEdges.forEach { edge ->
        annotations[edge.parentIndex].replies += annotations[edge.replyIndex]
    }
    return plan.displayGroups.map { group ->
        annotations[group.rootIndex].also { root ->
            root.replies += group.geometricReplyIndices.map(annotations::get)
        }
    }
}

private fun RectF.toSharedBounds(): PdfPageBounds = PdfPageBounds(left, top, right, bottom)
