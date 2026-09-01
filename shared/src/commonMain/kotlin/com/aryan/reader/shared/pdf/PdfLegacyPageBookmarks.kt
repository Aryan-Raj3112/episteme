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

private const val MAX_LEGACY_PDF_BOOKMARK_PAGE_COUNT = 1_000_000

object LegacyPdfPageBookmarkCodec {
    fun decode(raw: String?): Set<LegacyPdfPageBookmark> {
        if (raw.isNullOrBlank()) return emptySet()
        val array = runCatching { Json.parseToJsonElement(raw) as JsonArray }.getOrNull()
            ?: return emptySet()
        return array.mapNotNull { element ->
            runCatching {
                val value = element.jsonObject
                val pageIndex = value.getValue("pageIndex").jsonPrimitive.int
                val totalPages = value.getValue("totalPages").jsonPrimitive.int
                require(totalPages in 1..MAX_LEGACY_PDF_BOOKMARK_PAGE_COUNT)
                require(pageIndex in 0 until totalPages)
                LegacyPdfPageBookmark(
                    pageIndex = pageIndex,
                    title = value.getValue("title").jsonPrimitive.content,
                    totalPages = totalPages
                )
            }.getOrNull()
        }.toSet()
    }
}
