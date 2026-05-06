package com.aryan.reader.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object EpubAnnotationSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseBookmarksJson(rawJson: String?, chapterTitles: List<String> = emptyList()): Set<EpubBookmark> {
        if (rawJson.isNullOrBlank()) return emptySet()
        val root = runCatching { json.parseToJsonElement(rawJson).jsonArray }.getOrNull() ?: return emptySet()
        return root.mapNotNull { element ->
            when (element) {
                is JsonObject -> element.asBookmarkOrNull(chapterTitles)
                else -> element.contentOrNull()
                    ?.let { rawBookmark -> parseBookmarkObject(rawBookmark, chapterTitles) }
            }
        }.toSet()
    }

    fun parseBookmarkEntries(entries: Collection<String>, chapterTitles: List<String> = emptyList()): Set<EpubBookmark> {
        return entries.mapNotNull { parseBookmarkObject(it, chapterTitles) }.toSet()
    }

    fun bookmarksToJson(bookmarks: Collection<EpubBookmark>): String {
        val bookmarkEntries = bookmarks.map { JsonPrimitive(it.toJsonString()) }
        return json.encodeToString(JsonElement.serializer(), JsonArray(bookmarkEntries))
    }

    fun parseHighlightsJson(rawJson: String?): List<UserHighlight> {
        if (rawJson.isNullOrBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(rawJson).jsonArray }.getOrNull() ?: return emptyList()
        return root.mapNotNull { element ->
            runCatching { element.jsonObject.asHighlightOrNull() }.getOrNull()
        }
    }

    fun parseHighlightJson(rawJson: String?): UserHighlight? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching { json.parseToJsonElement(rawJson).jsonObject.asHighlightOrNull() }.getOrNull()
    }

    fun parseHighlightJsonLenient(rawJson: String?): UserHighlight? {
        if (rawJson.isNullOrBlank()) return null
        parseHighlightJson(rawJson)?.let { return it }
        val unwrapped = runCatching {
            json.parseToJsonElement(rawJson).jsonPrimitive.content
        }.getOrNull()
        return parseHighlightJson(unwrapped)
    }

    fun highlightsToJson(highlights: Collection<UserHighlight>): String {
        return json.encodeToString(
            JsonElement.serializer(),
            JsonArray(highlights.map { it.toJsonObject() })
        )
    }

    fun processAndAddHighlight(
        newCfi: String,
        newText: String,
        newColor: HighlightColor,
        chapterIndex: Int,
        currentList: MutableList<UserHighlight>,
        locator: ReaderLocator = ReaderLocator.fromLegacy(
            chapterIndex = chapterIndex,
            cfi = newCfi,
            textQuote = newText
        )
    ): String {
        val normalizedLocator = locator.withFallbacks(
            chapterIndex = chapterIndex,
            cfi = newCfi,
            textQuote = newText
        )
        val exactMatchIndex = currentList.indexOfFirst {
            it.chapterIndex == chapterIndex &&
                (it.cfi == newCfi || it.locator.sameLocation(normalizedLocator))
        }

        if (exactMatchIndex != -1) {
            val existing = currentList[exactMatchIndex]
            currentList[exactMatchIndex] = existing.copy(
                cfi = newCfi,
                color = newColor,
                text = newText,
                locator = existing.locator.copy(cfi = newCfi, textQuote = newText).withFallbacks(
                    chapterIndex = chapterIndex,
                    cfi = newCfi,
                    textQuote = newText
                )
            )
            return newCfi
        }

        currentList.add(
            UserHighlight(
                id = stableHighlightId(newCfi, chapterIndex),
                cfi = newCfi,
                text = newText,
                color = newColor,
                chapterIndex = chapterIndex,
                note = null,
                locator = normalizedLocator
            )
        )
        return newCfi
    }

    private fun parseBookmarkObject(rawJson: String, chapterTitles: List<String>): EpubBookmark? {
        return runCatching { json.parseToJsonElement(rawJson).jsonObject.asBookmarkOrNull(chapterTitles) }.getOrNull()
    }

    private fun EpubBookmark.toJsonString(): String {
        return json.encodeToString(JsonElement.serializer(), toJsonObject())
    }

    private fun EpubBookmark.toJsonObject(): JsonObject {
        return JsonObject(
            buildMap {
                put("cfi", JsonPrimitive(cfi))
                put("chapterTitle", JsonPrimitive(chapterTitle))
                put("label", label.asJson())
                put("snippet", JsonPrimitive(snippet))
                pageInChapter?.let { put("pageInChapter", JsonPrimitive(it)) }
                totalPagesInChapter?.let { put("totalPagesInChapter", JsonPrimitive(it)) }
                put("chapterIndex", JsonPrimitive(chapterIndex))
                put("locator", locator.toJsonObject())
            }
        )
    }

    private fun JsonObject.asBookmarkOrNull(chapterTitles: List<String>): EpubBookmark? {
        val cfi = string("cfi") ?: return null
        val chapterTitle = string("chapterTitle") ?: return null
        val chapterIndex = int("chapterIndex")
            ?: chapterTitles.indexOfFirst { it == chapterTitle }.coerceAtLeast(0)
        return EpubBookmark(
            cfi = cfi,
            chapterTitle = chapterTitle,
            label = string("label"),
            snippet = string("snippet") ?: "",
            pageInChapter = int("pageInChapter"),
            totalPagesInChapter = int("totalPagesInChapter"),
            chapterIndex = chapterIndex,
            locator = this["locator"]
                ?.takeUnless { it is JsonNull }
                ?.asReaderLocatorOrNull()
                ?.withFallbacks(
                    chapterIndex = chapterIndex,
                    cfi = cfi,
                    pageIndex = int("pageInChapter")?.minus(1),
                    textQuote = string("snippet") ?: ""
                )
                ?: ReaderLocator.fromLegacy(
                    chapterIndex = chapterIndex,
                    cfi = cfi,
                    pageIndex = int("pageInChapter")?.minus(1),
                    textQuote = string("snippet") ?: ""
                )
        )
    }

    private fun JsonObject.asHighlightOrNull(): UserHighlight? {
        val cfi = string("cfi") ?: return null
        val text = string("text") ?: return null
        val chapterIndex = int("chapterIndex") ?: return null
        val colorId = string("colorId")
        val color = HighlightColor.entries.firstOrNull { it.id == colorId } ?: HighlightColor.YELLOW
        val note = string("note")?.takeIf { it.isNotBlank() }
        return UserHighlight(
            id = string("id")?.takeIf { it.isNotBlank() } ?: stableHighlightId(cfi, chapterIndex),
            cfi = cfi,
            text = text,
            color = color,
            chapterIndex = chapterIndex,
            note = note,
            locator = this["locator"]
                ?.takeUnless { it is JsonNull }
                ?.asReaderLocatorOrNull()
                ?.withFallbacks(
                    chapterIndex = chapterIndex,
                    cfi = cfi,
                    textQuote = text
                )
                ?: ReaderLocator.fromLegacy(
                    chapterIndex = chapterIndex,
                    cfi = cfi,
                    textQuote = text
                )
        )
    }

    private fun UserHighlight.toJsonObject(): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "cfi" to JsonPrimitive(cfi),
                "text" to JsonPrimitive(text),
                "colorId" to JsonPrimitive(color.id),
                "chapterIndex" to JsonPrimitive(chapterIndex),
                "note" to (note ?: "").asJson(),
                "locator" to locator.toJsonObject()
            )
        )
    }

    private fun ReaderLocator.toJsonObject(): JsonObject {
        return JsonObject(
            buildMap {
                chapterIndex?.let { put("chapterIndex", JsonPrimitive(it)) }
                chapterId?.let { put("chapterId", JsonPrimitive(it)) }
                href?.let { put("href", JsonPrimitive(it)) }
                pageIndex?.let { put("pageIndex", JsonPrimitive(it)) }
                startOffset?.let { put("startOffset", JsonPrimitive(it)) }
                endOffset?.let { put("endOffset", JsonPrimitive(it)) }
                textQuote?.let { put("textQuote", JsonPrimitive(it)) }
                cfi?.let { put("cfi", JsonPrimitive(it)) }
            }
        )
    }

    private fun JsonElement.asReaderLocatorOrNull(): ReaderLocator? {
        val obj = runCatching { jsonObject }.getOrNull() ?: return null
        return ReaderLocator(
            chapterIndex = obj.int("chapterIndex"),
            chapterId = obj.string("chapterId"),
            href = obj.string("href"),
            pageIndex = obj.int("pageIndex"),
            startOffset = obj.int("startOffset"),
            endOffset = obj.int("endOffset"),
            textQuote = obj.string("textQuote"),
            cfi = obj.string("cfi")
        )
    }

    private fun stableHighlightId(cfi: String, chapterIndex: Int): String {
        val key = "$chapterIndex:$cfi"
        var hash = 1125899906842597L
        key.forEach { char -> hash = 31 * hash + char.code }
        return "highlight_${hash.toString(16)}"
    }

    private fun JsonObject.string(name: String): String? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content }.getOrNull()
    }

    private fun JsonObject.int(name: String): Int? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull }.getOrNull()
    }

    private fun JsonElement.contentOrNull(): String? {
        return runCatching { takeUnless { it is JsonNull }?.jsonPrimitive?.content }.getOrNull()
    }

    private fun String?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
}
