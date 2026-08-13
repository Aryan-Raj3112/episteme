/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf.data

import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.pdf.AnnotationType
import com.aryan.reader.pdf.InkType
import com.aryan.reader.pdf.PdfHighlightColor
import com.aryan.reader.pdf.PdfPoint
import com.aryan.reader.pdf.PdfUserHighlight
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.pdf.SharedPdfAnnotationComment
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfLegacyHighlight
import com.aryan.reader.shared.pdf.SharedPdfLegacyHighlightCodec
import com.aryan.reader.shared.pdf.SharedPdfLegacyInkAnnotation
import com.aryan.reader.shared.pdf.SharedPdfLegacyInkCodec
import com.aryan.reader.shared.pdf.SharedPdfLegacyTextBox
import com.aryan.reader.shared.pdf.SharedPdfLegacyTextBoxCodec
import timber.log.Timber
import java.io.Reader
import java.io.StringReader
import java.util.UUID

data class PdfTextBox(
    val id: String,
    val pageIndex: Int,
    val relativeBounds: Rect,
    val text: String,
    val color: Color,
    val backgroundColor: Color,
    val fontSize: Float,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val fontPath: String? = null,
    val fontName: String? = null
)

data class PdfAnnotation(
    val type: AnnotationType,
    val inkType: InkType = InkType.PEN,
    val pageIndex: Int,
    val points: List<PdfPoint>,
    val color: Color,
    val strokeWidth: Float,
    val id: String = UUID.randomUUID().toString(),
    val note: String? = null
)

object AnnotationSerializer {
    fun toJson(annotations: Map<Int, List<PdfAnnotation>>): String {
        return SharedPdfLegacyInkCodec.encode(annotations.values.flatten().map { annotation ->
            SharedPdfLegacyInkAnnotation(
                id = annotation.id,
                pageIndex = annotation.pageIndex,
                annotationTypeName = annotation.type.name,
                inkTypeName = annotation.inkType.name,
                colorArgb = annotation.color.toArgb(),
                strokeWidth = annotation.strokeWidth,
                points = annotation.points.map { PdfPagePoint(it.x, it.y, it.timestamp) },
                note = annotation.note,
            )
        })
    }

    fun fromJson(json: String): Map<Int, List<PdfAnnotation>> {
        if (json.isBlank()) return emptyMap()
        return fromJson(StringReader(json))
    }

    fun fromJson(reader: Reader): Map<Int, List<PdfAnnotation>> {
        return parseAnnotationArray(reader.readText())
    }

    private fun parseAnnotationArray(json: String): Map<Int, List<PdfAnnotation>> {
        val resultMap = mutableMapOf<Int, MutableList<PdfAnnotation>>()
        if (json.isBlank()) return resultMap
        val decoded = SharedPdfLegacyInkCodec.decode(json) { UUID.randomUUID().toString() }
        decoded.annotations.forEach { annotation ->
            val androidAnnotation = PdfAnnotation(
                type = runCatching { AnnotationType.valueOf(annotation.annotationTypeName) }
                    .getOrDefault(AnnotationType.INK),
                inkType = runCatching { InkType.valueOf(annotation.inkTypeName) }.getOrDefault(InkType.PEN),
                pageIndex = annotation.pageIndex,
                points = annotation.points.map { PdfPoint(it.x, it.y, it.timestamp) },
                color = Color(annotation.colorArgb),
                strokeWidth = annotation.strokeWidth,
                id = annotation.id,
                note = annotation.note,
            )
            resultMap.getOrPut(androidAnnotation.pageIndex) { mutableListOf() }.add(androidAnnotation)
        }
        if (decoded.annotationsWereCapped) Timber.w("PDF ink annotation load capped at ${SharedPdfLegacyInkCodec.MAX_ANNOTATIONS_PER_LOAD} annotations")
        if (decoded.pointsWereCapped) Timber.w("PDF ink annotation point list capped at ${SharedPdfLegacyInkCodec.MAX_POINTS_PER_ANNOTATION} points")
        return resultMap
    }
}

object TextBoxSerializer {
    fun toJson(textBoxes: List<PdfTextBox>): String {
        return SharedPdfLegacyTextBoxCodec.encode(textBoxes.map { box ->
            SharedPdfLegacyTextBox(
                id = box.id,
                pageIndex = box.pageIndex,
                bounds = PdfPageBounds(
                    box.relativeBounds.left,
                    box.relativeBounds.top,
                    box.relativeBounds.right,
                    box.relativeBounds.bottom,
                ),
                text = box.text,
                colorArgb = box.color.toArgb(),
                backgroundArgb = box.backgroundColor.toArgb(),
                fontSize = box.fontSize,
                isBold = box.isBold,
                isItalic = box.isItalic,
                isUnderline = box.isUnderline,
                isStrikeThrough = box.isStrikeThrough,
                fontPath = box.fontPath,
                fontName = box.fontName,
            )
        })
    }

    fun fromJson(json: String): List<PdfTextBox> {
        return SharedPdfLegacyTextBoxCodec.decode(json).map { box ->
            PdfTextBox(
                id = box.id,
                pageIndex = box.pageIndex,
                relativeBounds = Rect(box.bounds.left, box.bounds.top, box.bounds.right, box.bounds.bottom),
                text = box.text,
                color = Color(box.colorArgb),
                backgroundColor = Color(box.backgroundArgb),
                fontSize = box.fontSize,
                isBold = box.isBold,
                isItalic = box.isItalic,
                isUnderline = box.isUnderline,
                isStrikeThrough = box.isStrikeThrough,
                fontPath = box.fontPath,
                fontName = box.fontName,
            )
        }
    }
}

object HighlightSerializer {
    fun toJson(highlights: List<PdfUserHighlight>): String {
        return SharedPdfLegacyHighlightCodec.encode(highlights.map { highlight ->
            SharedPdfLegacyHighlight(
                id = highlight.id,
                pageIndex = highlight.pageIndex,
                bounds = highlight.bounds.map { PdfPageBounds(it.left, it.top, it.right, it.bottom) },
                colorName = highlight.color.name,
                colorArgb = highlight.colorArgb,
                text = highlight.text,
                rangeStart = highlight.range.first,
                rangeEnd = highlight.range.second,
                style = highlight.style,
                note = highlight.note,
                comments = highlight.comments,
            )
        })
    }

    fun fromJson(json: String): List<PdfUserHighlight> {
        return SharedPdfLegacyHighlightCodec.decode(json) { UUID.randomUUID().toString() }.map { highlight ->
            PdfUserHighlight(
                id = highlight.id ?: UUID.randomUUID().toString(),
                pageIndex = highlight.pageIndex,
                bounds = highlight.bounds.map { RectF(it.left, it.top, it.right, it.bottom) },
                color = runCatching { PdfHighlightColor.valueOf(highlight.colorName) }
                    .getOrDefault(PdfHighlightColor.YELLOW),
                colorArgb = highlight.colorArgb,
                text = highlight.text,
                range = highlight.rangeStart to highlight.rangeEnd,
                style = highlight.style,
                note = highlight.note,
                comments = highlight.comments,
            )
        }
    }
}
