package com.aryan.reader.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign

data class SharedLibrarySnapshot(
    val books: List<BookItem> = emptyList(),
    val shelfRecords: List<ShelfRecord> = emptyList(),
    val shelfRefs: List<BookShelfRef> = emptyList(),
    val tags: List<Tag> = emptyList()
)

object SharedLibrarySnapshotJson {
    private const val SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun decodeOrEmpty(rawJson: String): SharedLibrarySnapshot {
        val root = runCatching {
            json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull() ?: return SharedLibrarySnapshot()

        return SharedLibrarySnapshot(
            books = root.array("books").mapNotNull { it.asBookItemOrNull() },
            shelfRecords = root.array("shelves").mapNotNull { it.asShelfRecordOrNull() },
            shelfRefs = root.array("bookShelfRefs").mapNotNull { it.asBookShelfRefOrNull() },
            tags = root.array("tags").mapNotNull { it.asTagOrNull() }
        )
    }

    fun encode(snapshot: SharedLibrarySnapshot): String {
        val root = JsonObject(
            mapOf(
                "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
                "books" to JsonArray(snapshot.books.map { it.toJsonObject() }),
                "shelves" to JsonArray(snapshot.shelfRecords.map { it.toJsonObject() }),
                "bookShelfRefs" to JsonArray(snapshot.shelfRefs.map { it.toJsonObject() }),
                "tags" to JsonArray(snapshot.tags.map { it.toJsonObject() })
            )
        )
        return json.encodeToString(JsonElement.serializer(), root)
    }
}

private fun JsonObject.array(name: String): List<JsonElement> {
    return runCatching { this[name]?.jsonArray?.toList().orEmpty() }.getOrDefault(emptyList())
}

private fun JsonObject.string(name: String): String? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content }.getOrNull()
}

private fun JsonObject.long(name: String, fallback: Long = 0L): Long {
    return runCatching { this[name]?.jsonPrimitive?.longOrNull }.getOrNull() ?: fallback
}

private fun JsonObject.int(name: String): Int? {
    return runCatching { this[name]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()
}

private fun JsonObject.float(name: String): Float? {
    return runCatching { this[name]?.jsonPrimitive?.floatOrNull }.getOrNull()
}

private fun JsonObject.double(name: String): Double? {
    return runCatching { this[name]?.jsonPrimitive?.doubleOrNull }.getOrNull()
}

private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean {
    return runCatching { this[name]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: fallback
}

private fun JsonElement.asBookItemOrNull(): BookItem? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val id = obj.string("id") ?: return null
    val displayName = obj.string("displayName") ?: return null
    val type = obj.string("type")?.let { runCatching { FileType.valueOf(it) }.getOrNull() } ?: FileType.UNKNOWN
    return BookItem(
        id = id,
        path = obj.string("path"),
        type = type,
        displayName = displayName,
        timestamp = obj.long("timestamp"),
        title = obj.string("title"),
        author = obj.string("author"),
        progressPercentage = obj.float("progressPercentage"),
        isRecent = obj.boolean("isRecent", true),
        fileSize = obj.long("fileSize"),
        sourceFolder = obj.string("sourceFolder"),
        seriesName = obj.string("seriesName"),
        seriesIndex = obj.double("seriesIndex"),
        tags = obj.array("tags").mapNotNull { it.asTagOrNull() },
        lastPageIndex = obj.int("lastPageIndex"),
        readerSettings = obj["readerSettings"]?.takeUnless { it is JsonNull }?.asReaderSettingsOrNull(),
        readerBookmarks = obj.array("readerBookmarks").mapNotNull { it.asReaderBookmarkOrNull() }
    )
}

private fun JsonElement.asShelfRecordOrNull(): ShelfRecord? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return ShelfRecord(
        id = obj.string("id") ?: return null,
        name = obj.string("name") ?: return null,
        isSmart = obj.boolean("isSmart", false),
        smartRulesJson = obj.string("smartRulesJson")
    )
}

private fun JsonElement.asBookShelfRefOrNull(): BookShelfRef? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return BookShelfRef(
        bookId = obj.string("bookId") ?: return null,
        shelfId = obj.string("shelfId") ?: return null,
        addedAt = obj.long("addedAt")
    )
}

private fun JsonElement.asTagOrNull(): Tag? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return Tag(
        id = obj.string("id") ?: return null,
        name = obj.string("name") ?: return null,
        color = runCatching {
            obj["color"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toIntOrNull()
        }.getOrNull()
    )
}

private fun BookItem.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "path" to path.asJson(),
            "type" to JsonPrimitive(type.name),
            "displayName" to JsonPrimitive(displayName),
            "timestamp" to JsonPrimitive(timestamp),
            "title" to title.asJson(),
            "author" to author.asJson(),
            "progressPercentage" to progressPercentage.asJson(),
            "isRecent" to JsonPrimitive(isRecent),
            "fileSize" to JsonPrimitive(fileSize),
            "sourceFolder" to sourceFolder.asJson(),
            "seriesName" to seriesName.asJson(),
            "seriesIndex" to seriesIndex.asJson(),
            "tags" to JsonArray(tags.map { it.toJsonObject() }),
            "lastPageIndex" to lastPageIndex.asJson(),
            "readerSettings" to readerSettings.asJson(),
            "readerBookmarks" to JsonArray(readerBookmarks.map { it.toJsonObject() })
        )
    )
}

private fun ShelfRecord.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "isSmart" to JsonPrimitive(isSmart),
            "smartRulesJson" to smartRulesJson.asJson()
        )
    )
}

private fun BookShelfRef.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "bookId" to JsonPrimitive(bookId),
            "shelfId" to JsonPrimitive(shelfId),
            "addedAt" to JsonPrimitive(addedAt)
        )
    )
}

private fun Tag.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "color" to color.asJson()
        )
    )
}

private fun String?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Float?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Double?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Int?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

private fun JsonElement.asReaderSettingsOrNull(): ReaderSettings? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    val defaults = ReaderSettings()
    return ReaderSettings(
        fontSize = obj.int("fontSize") ?: defaults.fontSize,
        lineSpacing = obj.float("lineSpacing") ?: defaults.lineSpacing,
        margin = obj.int("margin") ?: defaults.margin,
        darkMode = obj.boolean("darkMode", defaults.darkMode),
        readingMode = obj.string("readingMode")
            ?.let { runCatching { ReaderReadingMode.valueOf(it) }.getOrNull() }
            ?: defaults.readingMode,
        textAlign = obj.string("textAlign")
            ?.let { runCatching { SharedReaderTextAlign.valueOf(it) }.getOrNull() }
            ?: defaults.textAlign,
        pageWidth = obj.int("pageWidth") ?: defaults.pageWidth,
        fontFamily = obj.string("fontFamily") ?: defaults.fontFamily
    )
}

private fun JsonElement.asReaderBookmarkOrNull(): ReaderBookmark? {
    val obj = runCatching { jsonObject }.getOrNull() ?: return null
    return ReaderBookmark(
        id = obj.string("id") ?: return null,
        pageIndex = obj.int("pageIndex") ?: return null,
        chapterTitle = obj.string("chapterTitle") ?: "",
        preview = obj.string("preview") ?: ""
    )
}

private fun ReaderSettings?.asJson(): JsonElement {
    val settings = this ?: return JsonNull
    return JsonObject(
        mapOf(
            "fontSize" to JsonPrimitive(settings.fontSize),
            "lineSpacing" to JsonPrimitive(settings.lineSpacing),
            "margin" to JsonPrimitive(settings.margin),
            "darkMode" to JsonPrimitive(settings.darkMode),
            "readingMode" to JsonPrimitive(settings.readingMode.name),
            "textAlign" to JsonPrimitive(settings.textAlign.name),
            "pageWidth" to JsonPrimitive(settings.pageWidth),
            "fontFamily" to JsonPrimitive(settings.fontFamily)
        )
    )
}

private fun ReaderBookmark.toJsonObject(): JsonObject {
    return JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "pageIndex" to JsonPrimitive(pageIndex),
            "chapterTitle" to JsonPrimitive(chapterTitle),
            "preview" to JsonPrimitive(preview)
        )
    )
}
