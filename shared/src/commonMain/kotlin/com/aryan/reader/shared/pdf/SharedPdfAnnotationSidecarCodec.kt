package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.localFolderSyncSha256ShortHex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object SharedPdfAnnotationSidecarCodec {
    const val KEY_PDF_ANNOTATIONS = "pdfAnnotations"
    const val KEY_LEGACY_INK = "ink"
    const val KEY_LEGACY_TEXT_BOXES = "textBoxes"
    const val KEY_LEGACY_HIGHLIGHTS = "highlights"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encodeAnnotationsElement(annotations: List<SharedPdfAnnotation>): JsonElement {
        return json.parseToJsonElement(SharedPdfAnnotationSerializer.encode(annotations))
    }

    fun decodeAnnotationsElement(element: JsonElement): List<SharedPdfAnnotation> {
        return SharedPdfAnnotationSerializer.decode(json.encodeToString(JsonElement.serializer(), element))
    }

    fun annotationsFromData(data: JsonObject): List<SharedPdfAnnotation> {
        data[KEY_PDF_ANNOTATIONS]?.let { return decodeAnnotationsElement(it) }

        data[KEY_LEGACY_INK]?.let { ink ->
            val decoded = decodeAnnotationsElement(ink)
            if (decoded.isNotEmpty() || ink.looksLikeSharedAnnotationStore()) {
                return decoded
            }
        }

        return legacyAndroidAnnotationsFromData(data)
    }

    fun withCanonicalAnnotations(data: JsonObject): JsonObject {
        if (data[KEY_PDF_ANNOTATIONS] != null) return data
        val annotations = annotationsFromData(data)
        if (annotations.isEmpty() && !data.hasLegacyAndroidAnnotationPayload()) return data
        return JsonObject(data + (KEY_PDF_ANNOTATIONS to encodeAnnotationsElement(annotations)))
    }

    fun canonicalizeDataJson(rawDataJson: String): String {
        val data = parseObjectOrNull(rawDataJson) ?: return rawDataJson
        return json.encodeToString(JsonElement.serializer(), withCanonicalAnnotations(data))
    }

    fun legacyAndroidDataFromAnnotations(
        annotations: List<SharedPdfAnnotation>,
        existingData: JsonObject = JsonObject(emptyMap())
    ): JsonObject {
        val next = existingData.toMutableMap()
        val replaceLegacyFromCanonical = existingData[KEY_PDF_ANNOTATIONS] != null
        if (annotations.isEmpty() && !replaceLegacyFromCanonical) return existingData

        if (replaceLegacyFromCanonical || !existingData[KEY_LEGACY_INK].isLegacyAndroidInkArray()) {
            next[KEY_LEGACY_INK] = annotations.toLegacyAndroidInkArray()
        }
        if (replaceLegacyFromCanonical || !existingData[KEY_LEGACY_TEXT_BOXES].isJsonArray()) {
            next[KEY_LEGACY_TEXT_BOXES] = annotations.toLegacyAndroidTextBoxArray()
        }
        if (replaceLegacyFromCanonical || !existingData[KEY_LEGACY_HIGHLIGHTS].isJsonArray()) {
            next[KEY_LEGACY_HIGHLIGHTS] = annotations.toLegacyAndroidHighlightArray()
        }
        return JsonObject(next)
    }

    fun legacyAndroidDataJsonFromCanonical(rawDataJson: String): String {
        val data = parseObjectOrNull(rawDataJson) ?: return rawDataJson
        val annotations = annotationsFromData(data)
        if (annotations.isEmpty() && data[KEY_PDF_ANNOTATIONS] == null) return rawDataJson
        return json.encodeToString(
            JsonElement.serializer(),
            legacyAndroidDataFromAnnotations(annotations, data)
        )
    }

    private fun legacyAndroidAnnotationsFromData(data: JsonObject): List<SharedPdfAnnotation> {
        return buildList {
            addAll(data[KEY_LEGACY_INK].parseLegacyAndroidInk())
            addAll(data[KEY_LEGACY_TEXT_BOXES].parseLegacyAndroidTextBoxes())
            addAll(data[KEY_LEGACY_HIGHLIGHTS].parseLegacyAndroidHighlights())
        }
    }

    private fun JsonElement?.parseLegacyAndroidInk(): List<SharedPdfAnnotation> {
        val array = this?.jsonArrayOrNull() ?: return emptyList()
        if (!this.isLegacyAndroidInkArray()) return emptyList()
        return array.mapNotNull { element ->
            val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
            val points = obj.array("points")
                ?.mapNotNull { pointElement ->
                    val point = pointElement.jsonObjectOrNull() ?: return@mapNotNull null
                    PdfPagePoint(
                        x = point.float("x") ?: return@mapNotNull null,
                        y = point.float("y") ?: return@mapNotNull null,
                        timestamp = point.long("t") ?: point.long("timestamp") ?: 0L
                    )
                }
                .orEmpty()
            if (points.isEmpty()) return@mapNotNull null

            val tool = obj.string("inkType")
                ?: obj.string("type")
                ?: PdfInkTool.PEN.name
            SharedPdfAnnotation(
                id = obj.string("id") ?: stableAnnotationId("ink", element),
                pageIndex = obj.int("pageIndex") ?: return@mapNotNull null,
                kind = PdfAnnotationKind.INK,
                tool = tool.toPdfInkTool(),
                points = points,
                note = obj.string("note"),
                colorArgb = obj.int("color") ?: SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).colorArgb,
                strokeWidth = obj.float("strokeWidth") ?: SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).strokeWidth,
                createdAt = points.firstOrNull()?.timestamp ?: 0L
            )
        }
    }

    private fun JsonElement?.parseLegacyAndroidTextBoxes(): List<SharedPdfAnnotation> {
        val array = this?.jsonArrayOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
            val bounds = obj.objectValue("bounds")?.toPdfPageBoundsOrNull() ?: return@mapNotNull null
            val rawFontSize = obj.float("fontSize") ?: 16f
            SharedPdfAnnotation(
                id = obj.string("id") ?: stableAnnotationId("text", element),
                pageIndex = obj.int("pageIndex") ?: return@mapNotNull null,
                kind = PdfAnnotationKind.TEXT,
                tool = PdfInkTool.TEXT,
                bounds = bounds,
                text = obj.string("text").orEmpty(),
                colorArgb = obj.int("color") ?: 0xFF000000.toInt(),
                backgroundArgb = obj.int("backgroundColor") ?: 0x00000000,
                strokeWidth = SharedPdfAnnotationDefaults.configFor(PdfInkTool.TEXT).strokeWidth,
                fontSize = SharedPdfTextAnnotationDefaults.pageRelativeFontSizeToDisplay(rawFontSize),
                pageRelativeFontSize = SharedPdfTextAnnotationDefaults.legacyFontSizeToPageRelative(rawFontSize),
                isBold = obj.boolean("isBold") ?: false,
                isItalic = obj.boolean("isItalic") ?: false,
                isUnderline = obj.boolean("isUnderline") ?: false,
                isStrikeThrough = obj.boolean("isStrikeThrough") ?: false,
                fontPath = obj.string("fontPath"),
                fontName = obj.string("fontName")
            )
        }
    }

    private fun JsonElement?.parseLegacyAndroidHighlights(): List<SharedPdfAnnotation> {
        val array = this?.jsonArrayOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
            val boundsList = obj.array("bounds")
                ?.mapNotNull { it.jsonObjectOrNull()?.toPdfPageBoundsOrNull() }
                ?.filter { it.isNormalizedPageBounds() }
                .orEmpty()
            val rangeStart = obj.int("rangeStart")
            val rangeEnd = obj.int("rangeEnd")
            if (boundsList.isEmpty() && (rangeStart == null || rangeEnd == null)) return@mapNotNull null
            val inclusiveRangeEnd = if (rangeStart != null && rangeEnd != null) {
                (rangeEnd - 1).coerceAtLeast(rangeStart)
            } else {
                rangeEnd
            }

            val colorName = obj.string("color") ?: "YELLOW"
            SharedPdfAnnotation(
                id = obj.string("id") ?: stableAnnotationId("highlight", element),
                pageIndex = obj.int("pageIndex") ?: return@mapNotNull null,
                kind = PdfAnnotationKind.HIGHLIGHT,
                tool = PdfInkTool.HIGHLIGHTER,
                bounds = boundsList.firstOrNull(),
                boundsList = boundsList,
                text = obj.string("text").orEmpty(),
                note = obj.string("note"),
                colorArgb = SharedPdfAndroidHighlightColors.argbForName(colorName),
                rangeStartIndex = rangeStart,
                rangeEndIndex = inclusiveRangeEnd
            )
        }
    }

    private fun List<SharedPdfAnnotation>.toLegacyAndroidInkArray(): JsonArray {
        return JsonArray(
            filter { it.kind == PdfAnnotationKind.INK && it.points.isNotEmpty() }
                .map { annotation ->
                    JsonObject(
                        buildMap {
                            put("id", JsonPrimitive(annotation.id))
                            put("pageIndex", JsonPrimitive(annotation.pageIndex))
                            put("annotationType", JsonPrimitive("INK"))
                            put("inkType", JsonPrimitive(annotation.tool.name))
                            put("color", JsonPrimitive(annotation.colorArgb))
                            put("strokeWidth", JsonPrimitive(annotation.strokeWidth.toDouble()))
                            annotation.note?.takeIf { it.isNotBlank() }?.let { put("note", JsonPrimitive(it)) }
                            put(
                                "points",
                                JsonArray(
                                    annotation.points.map { point ->
                                        JsonObject(
                                            mapOf(
                                                "x" to JsonPrimitive(point.x.toDouble()),
                                                "y" to JsonPrimitive(point.y.toDouble()),
                                                "t" to JsonPrimitive(point.timestamp)
                                            )
                                        )
                                    }
                                )
                            )
                        }
                    )
                }
        )
    }

    private fun List<SharedPdfAnnotation>.toLegacyAndroidTextBoxArray(): JsonArray {
        return JsonArray(
            filter { it.kind == PdfAnnotationKind.TEXT && it.bounds != null }
                .map { annotation ->
                    val bounds = requireNotNull(annotation.bounds)
                    JsonObject(
                        buildMap {
                            put("id", JsonPrimitive(annotation.id))
                            put("pageIndex", JsonPrimitive(annotation.pageIndex))
                            put("text", JsonPrimitive(annotation.text))
                            put("color", JsonPrimitive(annotation.colorArgb))
                            put("backgroundColor", JsonPrimitive(annotation.backgroundArgb))
                            put("fontSize", JsonPrimitive(annotation.sharedPdfTextPageRelativeFontSize().toDouble()))
                            put("isBold", JsonPrimitive(annotation.isBold))
                            put("isItalic", JsonPrimitive(annotation.isItalic))
                            put("isUnderline", JsonPrimitive(annotation.isUnderline))
                            put("isStrikeThrough", JsonPrimitive(annotation.isStrikeThrough))
                            annotation.fontPath?.let { put("fontPath", JsonPrimitive(it)) }
                            annotation.fontName?.let { put("fontName", JsonPrimitive(it)) }
                            put("bounds", bounds.toJsonObject())
                        }
                    )
                }
        )
    }

    private fun List<SharedPdfAnnotation>.toLegacyAndroidHighlightArray(): JsonArray {
        return JsonArray(
            filter { it.kind == PdfAnnotationKind.HIGHLIGHT }
                .map { annotation ->
                    JsonObject(
                        buildMap {
                            put("id", JsonPrimitive(annotation.id))
                            put("pageIndex", JsonPrimitive(annotation.pageIndex))
                            put("color", JsonPrimitive(SharedPdfAndroidHighlightColors.nearestName(annotation.colorArgb)))
                            put("text", JsonPrimitive(annotation.text))
                            val rangeStart = annotation.rangeStartIndex ?: 0
                            val rangeEnd = annotation.rangeEndIndex?.plus(1)?.coerceAtLeast(rangeStart) ?: rangeStart
                            put("rangeStart", JsonPrimitive(rangeStart))
                            put("rangeEnd", JsonPrimitive(rangeEnd))
                            annotation.note?.takeIf { it.isNotBlank() }?.let { put("note", JsonPrimitive(it)) }
                            put("bounds", JsonArray(emptyList()))
                        }
                    )
                }
        )
    }

    private fun parseObjectOrNull(raw: String): JsonObject? {
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    private fun stableAnnotationId(prefix: String, element: JsonElement): String {
        return "${prefix}_${localFolderSyncSha256ShortHex(json.encodeToString(JsonElement.serializer(), element))}"
    }

    private fun JsonElement.looksLikeSharedAnnotationStore(): Boolean {
        val obj = jsonObjectOrNull()
        if (obj?.array("annotations") != null) return true
        val array = jsonArrayOrNull() ?: return false
        val first = array.firstOrNull()?.jsonObjectOrNull() ?: return false
        return first["kind"] != null && first["colorArgb"] != null
    }

    private fun JsonElement?.isLegacyAndroidInkArray(): Boolean {
        val array = this?.jsonArrayOrNull() ?: return false
        if (array.isEmpty()) return true
        return array.all { element ->
            val obj = element.jsonObjectOrNull() ?: return@all false
            obj["kind"] == null &&
                obj["points"] != null &&
                (obj["annotationType"] != null || obj["inkType"] != null || obj["type"] != null)
        }
    }

    private fun JsonElement?.isJsonArray(): Boolean = this?.jsonArrayOrNull() != null

    private fun JsonObject.hasLegacyAndroidAnnotationPayload(): Boolean {
        return this[KEY_LEGACY_INK] != null ||
            this[KEY_LEGACY_TEXT_BOXES] != null ||
            this[KEY_LEGACY_HIGHLIGHTS] != null
    }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? {
        if (this is JsonNull) return null
        return runCatching { jsonArray }.getOrNull()
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? {
        if (this is JsonNull) return null
        return runCatching { jsonObject }.getOrNull()
    }

    private fun JsonObject.array(name: String): JsonArray? = this[name]?.jsonArrayOrNull()

    private fun JsonObject.objectValue(name: String): JsonObject? = this[name]?.jsonObjectOrNull()

    private fun JsonObject.string(name: String): String? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.int(name: String): Int? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull }.getOrNull()
    }

    private fun JsonObject.long(name: String): Long? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull }.getOrNull()
    }

    private fun JsonObject.float(name: String): Float? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull?.toFloat() }.getOrNull()
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull }.getOrNull()
    }

    private fun JsonObject.toPdfPageBoundsOrNull(): PdfPageBounds? {
        val left = float("left") ?: return null
        val top = float("top") ?: return null
        val right = float("right") ?: return null
        val bottom = float("bottom") ?: return null
        return PdfPageBounds(
            left = minOf(left, right),
            top = minOf(top, bottom),
            right = maxOf(left, right),
            bottom = maxOf(top, bottom)
        )
    }

    private fun PdfPageBounds.toJsonObject(): JsonObject {
        return JsonObject(
            mapOf(
                "left" to JsonPrimitive(left.toDouble()),
                "top" to JsonPrimitive(top.toDouble()),
                "right" to JsonPrimitive(right.toDouble()),
                "bottom" to JsonPrimitive(bottom.toDouble())
            )
        )
    }

    private fun PdfPageBounds.isNormalizedPageBounds(): Boolean {
        return left in 0f..1f &&
            top in 0f..1f &&
            right in 0f..1f &&
            bottom in 0f..1f &&
            right >= left &&
            bottom >= top
    }

    private fun String.toPdfInkTool(): PdfInkTool {
        return runCatching { PdfInkTool.valueOf(this) }.getOrDefault(PdfInkTool.PEN)
    }

}
