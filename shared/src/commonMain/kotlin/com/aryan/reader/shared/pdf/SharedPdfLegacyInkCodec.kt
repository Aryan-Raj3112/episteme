package com.aryan.reader.shared.pdf

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.round

data class SharedPdfLegacyInkAnnotation(
    val id: String,
    val pageIndex: Int,
    val annotationTypeName: String = "INK",
    val inkTypeName: String = "PEN",
    val colorArgb: Int,
    val strokeWidth: Float,
    val points: List<PdfPagePoint>,
    val note: String? = null,
)

data class SharedPdfLegacyInkDecodeResult(
    val annotations: List<SharedPdfLegacyInkAnnotation>,
    val annotationsWereCapped: Boolean = false,
    val pointsWereCapped: Boolean = false,
)

/** Owns the byte-compatible JSON policy used by Android's original ink sidecar. */
object SharedPdfLegacyInkCodec {
    const val MAX_ANNOTATIONS_PER_LOAD = 20_000
    const val MAX_POINTS_PER_ANNOTATION = 5_000

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(annotations: List<SharedPdfLegacyInkAnnotation>): String {
        val array = JsonArray(annotations.map { annotation ->
            JsonObject(buildMap {
                put("id", JsonPrimitive(annotation.id))
                put("pageIndex", JsonPrimitive(annotation.pageIndex))
                put("annotationType", JsonPrimitive(annotation.annotationTypeName))
                put("inkType", JsonPrimitive(annotation.inkTypeName))
                put("color", JsonPrimitive(annotation.colorArgb))
                put("strokeWidth", JsonPrimitive(annotation.strokeWidth.toDouble()))
                annotation.note?.takeIf { it.isNotBlank() }?.let { put("note", JsonPrimitive(it)) }
                put("points", JsonArray(annotation.points.map { point ->
                    JsonObject(linkedMapOf(
                        "x" to JsonPrimitive(point.x.roundedLegacyCoordinate()),
                        "y" to JsonPrimitive(point.y.roundedLegacyCoordinate()),
                        "t" to JsonPrimitive(point.timestamp),
                    ))
                }))
            })
        })
        return json.encodeToString(JsonElement.serializer(), array)
    }

    fun decode(rawJson: String, newId: () -> String): SharedPdfLegacyInkDecodeResult {
        if (rawJson.isBlank()) return SharedPdfLegacyInkDecodeResult(emptyList())
        return runCatching {
            val root = json.parseToJsonElement(rawJson).jsonArray
            var pointsWereCapped = false
            val annotations = root.take(MAX_ANNOTATIONS_PER_LOAD).mapNotNull { element ->
                val obj = element.objectOrNull() ?: return@mapNotNull null
                val pageIndex = obj.number("pageIndex")?.toInt() ?: return@mapNotNull null
                val colorArgb = obj.number("color")?.toInt() ?: return@mapNotNull null
                val strokeWidth = obj.number("strokeWidth")?.toFloat() ?: return@mapNotNull null
                val rawPoints = obj["points"]?.arrayOrNull().orEmpty()
                if (rawPoints.size > MAX_POINTS_PER_ANNOTATION) pointsWereCapped = true
                val points = rawPoints.take(MAX_POINTS_PER_ANNOTATION).mapNotNull { pointElement ->
                    val point = pointElement.objectOrNull() ?: return@mapNotNull null
                    PdfPagePoint(
                        x = point.number("x")?.toFloat() ?: return@mapNotNull null,
                        y = point.number("y")?.toFloat() ?: return@mapNotNull null,
                        timestamp = point.number("t")?.toLong() ?: 0L,
                    )
                }
                SharedPdfLegacyInkAnnotation(
                    id = obj.string("id") ?: newId(),
                    pageIndex = pageIndex,
                    annotationTypeName = obj.string("annotationType") ?: "INK",
                    inkTypeName = obj.string("inkType") ?: obj.string("type") ?: "PEN",
                    colorArgb = colorArgb,
                    strokeWidth = strokeWidth,
                    points = points,
                    note = obj.string("note"),
                )
            }
            SharedPdfLegacyInkDecodeResult(
                annotations = annotations,
                annotationsWereCapped = root.size > MAX_ANNOTATIONS_PER_LOAD,
                pointsWereCapped = pointsWereCapped,
            )
        }.getOrElse { SharedPdfLegacyInkDecodeResult(emptyList()) }
    }

    private fun Float.roundedLegacyCoordinate(): Double = round(toDouble() * 100_000.0) / 100_000.0

    private fun JsonElement.objectOrNull(): JsonObject? =
        takeUnless { it is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }

    private fun JsonElement.arrayOrNull(): JsonArray? =
        takeUnless { it is JsonNull }?.let { runCatching { it.jsonArray }.getOrNull() }

    private fun JsonObject.string(name: String): String? =
        runCatching { this[name]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(name: String): Number? {
        val primitive = runCatching { this[name]?.jsonPrimitive }.getOrNull() ?: return null
        return primitive.longOrNull ?: primitive.doubleOrNull
    }
}
