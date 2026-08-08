package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.HighlightStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class SharedPdfLegacyHighlight(
    val id: String?,
    val pageIndex: Int,
    val bounds: List<PdfPageBounds>,
    val colorName: String,
    val colorArgb: Int?,
    val text: String,
    val rangeStart: Int,
    val rangeEnd: Int,
    val style: HighlightStyle,
    val note: String?,
    val comments: List<SharedPdfAnnotationComment>,
)

/** Byte-shape-compatible policy for Android's pre-shared PDF highlight sidecar. */
object SharedPdfLegacyHighlightCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(highlights: List<SharedPdfLegacyHighlight>): String = buildJsonArray {
        highlights.forEach { highlight ->
            add(buildJsonObject {
                put("id", JsonPrimitive(highlight.id.orEmpty()))
                put("pageIndex", JsonPrimitive(highlight.pageIndex))
                put("color", JsonPrimitive(highlight.colorName))
                highlight.colorArgb?.let { put("colorArgb", JsonPrimitive(it)) }
                put("text", JsonPrimitive(highlight.text))
                put("rangeStart", JsonPrimitive(highlight.rangeStart))
                put("rangeEnd", JsonPrimitive(highlight.rangeEnd))
                put("style", JsonPrimitive(highlight.style.id))
                highlight.note?.takeIf { it.isNotBlank() }?.let { put("note", JsonPrimitive(it)) }
                encodedComments(highlight.comments).takeIf { it.isNotEmpty() }?.let { put("comments", it) }
                put("bounds", buildJsonArray {
                    highlight.bounds.forEach { bounds ->
                        add(buildJsonObject {
                            put("left", JsonPrimitive(bounds.left.toDouble()))
                            put("top", JsonPrimitive(bounds.top.toDouble()))
                            put("right", JsonPrimitive(bounds.right.toDouble()))
                            put("bottom", JsonPrimitive(bounds.bottom.toDouble()))
                        })
                    }
                })
            })
        }
    }.toString()

    fun decode(value: String, newId: () -> String): List<SharedPdfLegacyHighlight> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(value).jsonArray.map { element ->
                val obj = element.jsonObject
                SharedPdfLegacyHighlight(
                    id = obj.string("id")?.takeIf { it.isNotBlank() } ?: newId(),
                    pageIndex = obj.int("pageIndex") ?: error("Missing pageIndex"),
                    bounds = obj["bounds"]?.jsonArray.orEmpty().map { boundsElement ->
                        val bounds = boundsElement.jsonObject
                        PdfPageBounds(
                            left = bounds.float("left") ?: error("Missing left"),
                            top = bounds.float("top") ?: error("Missing top"),
                            right = bounds.float("right") ?: error("Missing right"),
                            bottom = bounds.float("bottom") ?: error("Missing bottom"),
                        )
                    },
                    colorName = obj.string("color").orEmpty(),
                    colorArgb = obj.int("colorArgb"),
                    text = obj.string("text").orEmpty(),
                    rangeStart = obj.int("rangeStart") ?: 0,
                    rangeEnd = obj.int("rangeEnd") ?: 0,
                    style = HighlightStyle.fromId(obj.string("style")),
                    note = obj.string("note")?.takeIf { it.isNotBlank() },
                    comments = decodeComments(obj["comments"], newId),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun encodedComments(comments: List<SharedPdfAnnotationComment>): JsonArray = buildJsonArray {
        comments.forEach { comment ->
            val contents = comment.contents.trim()
            if (contents.isBlank()) return@forEach
            add(buildJsonObject {
                put("id", JsonPrimitive(comment.id))
                comment.parentId?.takeIf { it.isNotBlank() }?.let { put("parentId", JsonPrimitive(it)) }
                comment.author.takeIf { it.isNotBlank() }?.let { put("author", JsonPrimitive(it)) }
                put("contents", JsonPrimitive(contents))
                if (comment.createdAt > 0L) put("createdAt", JsonPrimitive(comment.createdAt))
                val modifiedAt = comment.modifiedAt.takeIf { it > 0L } ?: comment.createdAt
                if (modifiedAt > 0L) put("modifiedAt", JsonPrimitive(modifiedAt))
            })
        }
    }

    private fun decodeComments(element: JsonElement?, newId: () -> String): List<SharedPdfAnnotationComment> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val contents = sequenceOf("contents", "text", "comment")
                .mapNotNull { key -> obj.string(key) }
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
            if (contents.isBlank()) return@mapNotNull null
            val createdAt = obj.long("createdAt") ?: obj.long("created") ?: 0L
            SharedPdfAnnotationComment(
                id = obj.string("id")?.takeIf { it.isNotBlank() } ?: newId(),
                parentId = (obj.string("parentId") ?: obj.string("inReplyTo"))?.takeIf { it.isNotBlank() },
                author = obj.string("author").orEmpty().trim(),
                contents = contents,
                createdAt = createdAt,
                modifiedAt = obj.long("modifiedAt") ?: obj.long("modified") ?: createdAt,
            )
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String): Long? = get(key)?.jsonPrimitive?.longOrNull
    private fun JsonObject.float(key: String): Float? = string(key)?.toFloatOrNull()
}
