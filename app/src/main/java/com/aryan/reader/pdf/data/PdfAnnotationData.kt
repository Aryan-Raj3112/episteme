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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
    val strokeWidth: Float
)

object AnnotationSerializer {
    fun toJson(annotations: Map<Int, List<PdfAnnotation>>): String {
        val rootArray = JSONArray()
        annotations.forEach { (_, list) ->
            list.forEach { annotation ->
                val obj = JSONObject()
                obj.put("pageIndex", annotation.pageIndex)
                obj.put("annotationType", annotation.type.name)
                obj.put("inkType", annotation.inkType.name)
                obj.put("color", annotation.color.toArgb())
                obj.put("strokeWidth", annotation.strokeWidth.toDouble())

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
        val resultMap = mutableMapOf<Int, MutableList<PdfAnnotation>>()
        if (json.isBlank()) return emptyMap()

        try {
            val rootArray = JSONArray(json)
            for (i in 0 until rootArray.length()) {
                val obj = rootArray.getJSONObject(i)

                val pageIndex = obj.getInt("pageIndex")
                val annTypeStr = obj.optString("annotationType", AnnotationType.INK.name)
                val annType = try { AnnotationType.valueOf(annTypeStr) } catch(_: Exception) { AnnotationType.INK }

                val inkTypeStr = obj.optString("inkType", "PEN")
                val finalInkTypeStr = if (obj.has("inkType")) inkTypeStr else obj.optString("type", "PEN")
                val inkType = try { InkType.valueOf(finalInkTypeStr) } catch(_: Exception) { InkType.PEN }

                val colorInt = obj.getInt("color")
                val strokeWidth = obj.getDouble("strokeWidth").toFloat()

                val pointsArray = obj.getJSONArray("points")
                val points = ArrayList<PdfPoint>()
                for (j in 0 until pointsArray.length()) {
                    val pObj = pointsArray.getJSONObject(j)
                    points.add(
                        PdfPoint(
                            x = pObj.getDouble("x").toFloat(),
                            y = pObj.getDouble("y").toFloat(),
                            timestamp = pObj.optLong("t", 0L)
                        )
                    )
                }

                val annotation = PdfAnnotation(
                    type = annType,
                    inkType = inkType,
                    pageIndex = pageIndex,
                    points = points,
                    color = Color(colorInt),
                    strokeWidth = strokeWidth
                )

                if (!resultMap.containsKey(pageIndex)) {
                    resultMap[pageIndex] = mutableListOf()
                }
                resultMap[pageIndex]?.add(annotation)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultMap
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
                        fontPath = obj.optString("fontPath", null).takeIf { !it.isNullOrBlank() },
                        fontName = obj.optString("fontName", null).takeIf { !it.isNullOrBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            obj.put("text", h.text)
            obj.put("rangeStart", h.range.first)
            obj.put("rangeEnd", h.range.second)

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
                        text = obj.optString("text", ""),
                        range = Pair(obj.optInt("rangeStart", 0), obj.optInt("rangeEnd", 0))
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}