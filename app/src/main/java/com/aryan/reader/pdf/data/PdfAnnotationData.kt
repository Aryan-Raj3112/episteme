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
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.Reader
import java.io.StringReader
import java.util.Locale
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
    private const val MAX_ANNOTATIONS_PER_LOAD = 20_000
    private const val MAX_POINTS_PER_ANNOTATION = 5_000

    fun toJson(annotations: Map<Int, List<PdfAnnotation>>): String {
        val rootArray = JSONArray()
        annotations.forEach { (_, list) ->
            list.forEach { annotation ->
                val obj = JSONObject()
                obj.put("id", annotation.id)
                obj.put("pageIndex", annotation.pageIndex)
                obj.put("annotationType", annotation.type.name)
                obj.put("inkType", annotation.inkType.name)
                obj.put("color", annotation.color.toArgb())
                obj.put("strokeWidth", annotation.strokeWidth.toDouble())
                if (!annotation.note.isNullOrBlank()) {
                    obj.put("note", annotation.note)
                }

                val pointsArray = JSONArray()
                annotation.points.forEach { p ->
                    val pObj = JSONObject()
                    pObj.put("x", String.format(Locale.US, "%.5f", p.x).toDouble())
                    pObj.put("y", String.format(Locale.US, "%.5f", p.y).toDouble())
                    pObj.put("t", p.timestamp)
                    pointsArray.put(pObj)
                }
                obj.put("points", pointsArray)
                rootArray.put(obj)
            }
        }
        return rootArray.toString()
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
        try {
            val rootArray = JSONArray(json)
            val count = minOf(rootArray.length(), MAX_ANNOTATIONS_PER_LOAD)
            for (index in 0 until count) {
                readAnnotation(rootArray.optJSONObject(index))?.let { annotation ->
                    resultMap.getOrPut(annotation.pageIndex) { mutableListOf() }.add(annotation)
                }
            }
            if (rootArray.length() > MAX_ANNOTATIONS_PER_LOAD) {
                Timber.w("PDF ink annotation load capped at $MAX_ANNOTATIONS_PER_LOAD annotations")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse PDF ink annotations")
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Ran out of memory while parsing PDF ink annotations")
        }
        return resultMap
    }

    private fun readAnnotation(obj: JSONObject?): PdfAnnotation? {
        if (obj == null) return null
        val pageIndex = obj.optNumber("pageIndex")?.toInt() ?: return null
        val colorInt = obj.optNumber("color")?.toInt() ?: return null
        val strokeWidth = obj.optNumber("strokeWidth")?.toFloat() ?: return null
        val annType = runCatching { AnnotationType.valueOf(obj.optString("annotationType", AnnotationType.INK.name)) }
            .getOrDefault(AnnotationType.INK)
        val resolvedInkTypeStr = obj.optString("inkType").ifBlank { obj.optString("type") }.ifBlank { InkType.PEN.name }
        val inkType = runCatching { InkType.valueOf(resolvedInkTypeStr) }.getOrDefault(InkType.PEN)
        return PdfAnnotation(
            type = annType,
            inkType = inkType,
            pageIndex = pageIndex,
            points = readPoints(obj.optJSONArray("points")),
            color = Color(colorInt),
            strokeWidth = strokeWidth,
            id = obj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            note = obj.optString("note").takeIf { it.isNotBlank() }
        )
    }

    private fun readPoints(array: JSONArray?): List<PdfPoint> {
        if (array == null) return emptyList()
        val count = minOf(array.length(), MAX_POINTS_PER_ANNOTATION)
        val points = ArrayList<PdfPoint>(count)
        for (index in 0 until count) {
            readPoint(array.optJSONObject(index))?.let(points::add)
        }
        if (array.length() > MAX_POINTS_PER_ANNOTATION) {
            Timber.w("PDF ink annotation point list capped at $MAX_POINTS_PER_ANNOTATION points")
        }
        return points
    }

    private fun readPoint(obj: JSONObject?): PdfPoint? {
        if (obj == null) return null
        val x = obj.optNumber("x")?.toFloat() ?: return null
        val y = obj.optNumber("y")?.toFloat() ?: return null
        val timestamp = obj.optNumber("t")?.toLong() ?: 0L
        return PdfPoint(x, y, timestamp)
    }

    private fun JSONObject.optNumber(name: String): Number? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

}

object TextBoxSerializer {
    fun toJson(textBoxes: List<PdfTextBox>): String {
        val rootArray = JSONArray()
        textBoxes.forEach { box ->
            val obj = JSONObject()
            obj.put("id", box.id)
            obj.put("pageIndex", box.pageIndex)
            obj.put("text", box.text)
            obj.put("color", box.color.toArgb())
            obj.put("backgroundColor", box.backgroundColor.toArgb())
            obj.put("fontSize", box.fontSize.toDouble())
            obj.put("isBold", box.isBold)
            obj.put("isItalic", box.isItalic)
            obj.put("isUnderline", box.isUnderline)
            obj.put("isStrikeThrough", box.isStrikeThrough)
            if (box.fontPath != null) {
                obj.put("fontPath", box.fontPath)
            }
            if (box.fontName != null) {
                obj.put("fontName", box.fontName)
            }

            val rectObj = JSONObject()
            rectObj.put("left", box.relativeBounds.left.toDouble())
            rectObj.put("top", box.relativeBounds.top.toDouble())
            rectObj.put("right", box.relativeBounds.right.toDouble())
            rectObj.put("bottom", box.relativeBounds.bottom.toDouble())
            obj.put("bounds", rectObj)

            rootArray.put(obj)
        }
        return rootArray.toString()
    }

    fun fromJson(json: String): List<PdfTextBox> {
        val result = mutableListOf<PdfTextBox>()
        if (json.isBlank()) return result
        try {
            val rootArray = JSONArray(json)
            for (i in 0 until rootArray.length()) {
                val obj = rootArray.getJSONObject(i)
                val rectObj = obj.getJSONObject("bounds")
                val rect = Rect(
                    rectObj.getDouble("left").toFloat(),
                    rectObj.getDouble("top").toFloat(),
                    rectObj.getDouble("right").toFloat(),
                    rectObj.getDouble("bottom").toFloat()
                )

                result.add(
                    PdfTextBox(
                        id = obj.getString("id"),
                        pageIndex = obj.getInt("pageIndex"),
                        relativeBounds = rect,
                        text = obj.optString("text", ""),
                        color = Color(obj.getInt("color")),
                        backgroundColor = Color(obj.getInt("backgroundColor")),
                        fontSize = obj.getDouble("fontSize").toFloat(),
                        isBold = obj.optBoolean("isBold", false),
                        isItalic = obj.optBoolean("isItalic", false),
                        isUnderline = obj.optBoolean("isUnderline", false),
                        isStrikeThrough = obj.optBoolean("isStrikeThrough", false),
                        fontPath = obj.optString("fontPath").takeIf { !it.isNullOrBlank() },
                        fontName = obj.optString("fontName").takeIf { !it.isNullOrBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse PDF text boxes")
        }
        return result
    }
}

object HighlightSerializer {
    fun toJson(highlights: List<PdfUserHighlight>): String {
        val rootArray = JSONArray()
        highlights.forEach { h ->
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("pageIndex", h.pageIndex)
            obj.put("color", h.color.name)
            h.colorArgb?.let { obj.put("colorArgb", it) }
            obj.put("text", h.text)
            obj.put("rangeStart", h.range.first)
            obj.put("rangeEnd", h.range.second)
            obj.put("style", h.style.id)

            if (!h.note.isNullOrBlank()) {
                obj.put("note", h.note)
            }
            val commentsArray = h.comments.toJsonArray()
            if (commentsArray.length() > 0) {
                obj.put("comments", commentsArray)
            }

            val boundsArray = JSONArray()
            h.bounds.forEach { r ->
                val rObj = JSONObject()
                rObj.put("left", r.left.toDouble())
                rObj.put("top", r.top.toDouble())
                rObj.put("right", r.right.toDouble())
                rObj.put("bottom", r.bottom.toDouble())
                boundsArray.put(rObj)
            }
            obj.put("bounds", boundsArray)
            rootArray.put(obj)
        }
        return rootArray.toString()
    }

    fun fromJson(json: String): List<PdfUserHighlight> {
        val result = mutableListOf<PdfUserHighlight>()
        if (json.isBlank()) return result
        try {
            val rootArray = JSONArray(json)
            for (i in 0 until rootArray.length()) {
                val obj = rootArray.getJSONObject(i)
                val boundsArray = obj.getJSONArray("bounds")
                val bounds = mutableListOf<RectF>()
                for (j in 0 until boundsArray.length()) {
                    val rObj = boundsArray.getJSONObject(j)
                    bounds.add(RectF(
                        rObj.getDouble("left").toFloat(),
                        rObj.getDouble("top").toFloat(),
                        rObj.getDouble("right").toFloat(),
                        rObj.getDouble("bottom").toFloat()
                    ))
                }
                result.add(
                    PdfUserHighlight(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        pageIndex = obj.getInt("pageIndex"),
                        bounds = bounds,
                        color = try { PdfHighlightColor.valueOf(obj.getString("color")) } catch(_: Exception) { PdfHighlightColor.YELLOW },
                        colorArgb = obj.optNullableInt("colorArgb"),
                        text = obj.optString("text", ""),
                        range = Pair(obj.optInt("rangeStart", 0), obj.optInt("rangeEnd", 0)),
                        style = HighlightStyle.fromId(if (obj.has("style")) obj.optString("style") else null),
                        note = obj.optString("note").takeIf { !it.isNullOrBlank() },
                        comments = obj.optJSONArray("comments").toSharedPdfAnnotationComments()
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse PDF highlights")
        }
        return result
    }

    private fun List<SharedPdfAnnotationComment>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { comment ->
            val contents = comment.contents.trim()
            if (contents.isBlank()) return@forEach
            val obj = JSONObject()
            obj.put("id", comment.id)
            comment.parentId?.takeIf { it.isNotBlank() }?.let { obj.put("parentId", it) }
            comment.author.takeIf { it.isNotBlank() }?.let { obj.put("author", it) }
            obj.put("contents", contents)
            if (comment.createdAt > 0L) obj.put("createdAt", comment.createdAt)
            val modifiedAt = comment.modifiedAt.takeIf { it > 0L } ?: comment.createdAt
            if (modifiedAt > 0L) obj.put("modifiedAt", modifiedAt)
            array.put(obj)
        }
        return array
    }

    private fun JSONArray?.toSharedPdfAnnotationComments(): List<SharedPdfAnnotationComment> {
        if (this == null) return emptyList()
        val comments = mutableListOf<SharedPdfAnnotationComment>()
        for (index in 0 until length()) {
            val obj = optJSONObject(index) ?: continue
            val contents = obj.optString("contents")
                .ifBlank { obj.optString("text") }
                .ifBlank { obj.optString("comment") }
                .trim()
            if (contents.isBlank()) continue
            val createdAt = obj.optLong("createdAt", obj.optLong("created", 0L))
            comments += SharedPdfAnnotationComment(
                id = obj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                parentId = obj.optString("parentId")
                    .ifBlank { obj.optString("inReplyTo") }
                    .takeIf { it.isNotBlank() },
                author = obj.optString("author").trim(),
                contents = contents,
                createdAt = createdAt,
                modifiedAt = obj.optLong(
                    "modifiedAt",
                    obj.optLong("modified", createdAt)
                )
            )
        }
        return comments
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }
}
