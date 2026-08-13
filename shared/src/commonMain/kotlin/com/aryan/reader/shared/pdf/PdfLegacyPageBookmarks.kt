package com.aryan.reader.shared.pdf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LegacyPdfPageBookmark(
    val pageIndex: Int,
    val title: String,
    val totalPages: Int
)

object LegacyPdfPageBookmarkCodec {
    fun decode(raw: String?): Set<LegacyPdfPageBookmark> {
        if (raw.isNullOrBlank()) return emptySet()
        val array = runCatching { Json.parseToJsonElement(raw) as JsonArray }.getOrNull()
            ?: return emptySet()
        return array.mapNotNull { element ->
            runCatching {
                val value = element.jsonObject
                LegacyPdfPageBookmark(
                    pageIndex = value.getValue("pageIndex").jsonPrimitive.int,
                    title = value.getValue("title").jsonPrimitive.content,
                    totalPages = value.getValue("totalPages").jsonPrimitive.int
                )
            }.getOrNull()
        }.toSet()
    }
}
