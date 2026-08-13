package com.aryan.reader.shared.pdf

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

data class SharedPdfLegacyTextBox(
    val id: String,
    val pageIndex: Int,
    val bounds: PdfPageBounds,
    val text: String,
    val colorArgb: Int,
    val backgroundArgb: Int,
    val fontSize: Float,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val fontPath: String? = null,
    val fontName: String? = null,
)

/** Byte-compatible policy for Android's original PDF text-box sidecar. */
object SharedPdfLegacyTextBoxCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(textBoxes: List<SharedPdfLegacyTextBox>): String {
        val array = JsonArray(textBoxes.map { box ->
            JsonObject(buildMap {
                put("id", JsonPrimitive(box.id))
                put("pageIndex", JsonPrimitive(box.pageIndex))
                put("text", JsonPrimitive(box.text))
                put("color", JsonPrimitive(box.colorArgb))
                put("backgroundColor", JsonPrimitive(box.backgroundArgb))
                put("fontSize", JsonPrimitive(box.fontSize.toDouble()))
                put("isBold", JsonPrimitive(box.isBold))
                put("isItalic", JsonPrimitive(box.isItalic))
                put("isUnderline", JsonPrimitive(box.isUnderline))
                put("isStrikeThrough", JsonPrimitive(box.isStrikeThrough))
                box.fontPath?.let { put("fontPath", JsonPrimitive(it)) }
                box.fontName?.let { put("fontName", JsonPrimitive(it)) }
                put("bounds", JsonObject(linkedMapOf(
                    "left" to JsonPrimitive(box.bounds.left.toDouble()),
                    "top" to JsonPrimitive(box.bounds.top.toDouble()),
                    "right" to JsonPrimitive(box.bounds.right.toDouble()),
                    "bottom" to JsonPrimitive(box.bounds.bottom.toDouble()),
                )))
            })
        })
        return json.encodeToString(JsonElement.serializer(), array)
    }

    fun decode(rawJson: String): List<SharedPdfLegacyTextBox> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(rawJson).jsonArray.map { element ->
                val obj = element.jsonObject
                val bounds = obj.requiredObject("bounds")
                SharedPdfLegacyTextBox(
                    id = obj.requiredString("id"),
                    pageIndex = obj.requiredInt("pageIndex"),
                    bounds = PdfPageBounds(
                        left = bounds.requiredFloat("left"),
                        top = bounds.requiredFloat("top"),
                        right = bounds.requiredFloat("right"),
                        bottom = bounds.requiredFloat("bottom"),
                    ),
                    text = obj.string("text").orEmpty(),
                    colorArgb = obj.requiredInt("color"),
                    backgroundArgb = obj.requiredInt("backgroundColor"),
                    fontSize = obj.requiredFloat("fontSize"),
                    isBold = obj.boolean("isBold") ?: false,
                    isItalic = obj.boolean("isItalic") ?: false,
                    isUnderline = obj.boolean("isUnderline") ?: false,
                    isStrikeThrough = obj.boolean("isStrikeThrough") ?: false,
                    fontPath = obj.string("fontPath"),
                    fontName = obj.string("fontName"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.requiredObject(name: String): JsonObject = this[name]?.jsonObject
        ?: error("Missing $name")
    private fun JsonObject.requiredString(name: String): String =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull ?: error("Missing $name")
    private fun JsonObject.requiredInt(name: String): Int = this[name]?.jsonPrimitive?.intOrNull
        ?: error("Missing $name")
    private fun JsonObject.requiredFloat(name: String): Float = this[name]?.jsonPrimitive?.doubleOrNull?.toFloat()
        ?: error("Missing $name")
    private fun JsonObject.string(name: String): String? =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull
}
