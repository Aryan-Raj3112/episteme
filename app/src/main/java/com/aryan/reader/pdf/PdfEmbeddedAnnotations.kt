package com.aryan.reader.pdf

import android.graphics.RectF
import timber.log.Timber

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

    val annotMap = annotations
        .filter { !it.name.isNullOrBlank() }
        .associateBy { it.name }
    val orphans = mutableListOf<EmbeddedAnnotation>()

    annotations.forEach { annot ->
        if (!annot.inReplyTo.isNullOrBlank() && annotMap.containsKey(annot.inReplyTo)) {
            Timber.tag("PdfCommentDebug").i("Linking: ${annot.name} is a reply to ${annot.inReplyTo}")
            annotMap[annot.inReplyTo]?.replies?.add(annot)
        } else {
            orphans.add(annot)
        }
    }

    Timber.tag("PdfCommentDebug").d("After ID linking: Orphans count = ${orphans.size}")

    val groupedRoots = mutableListOf<MutableList<EmbeddedAnnotation>>()
    orphans.forEach { annot ->
        val match = groupedRoots.find { group ->
            val root = group.first()
            val inflatedRoot = RectF(root.rect).apply { inset(-10f, -10f) }
            RectF.intersects(inflatedRoot, annot.rect)
        }
        if (match != null) {
            Timber.tag("PdfCommentDebug").w(
                "Geometric grouping triggered for ${annot.name} with ${match.first().name}. This might flatten nested replies!"
            )
            match.add(annot)
        } else {
            groupedRoots.add(mutableListOf(annot))
        }
    }

    return groupedRoots.map { group ->
        val root = group.first()
        if (group.size > 1) {
            root.replies.addAll(group.drop(1))
        }
        root
    }.filter {
        !it.contents.isNullOrBlank() || it.replies.any { reply -> !reply.contents.isNullOrBlank() }
    }
}
