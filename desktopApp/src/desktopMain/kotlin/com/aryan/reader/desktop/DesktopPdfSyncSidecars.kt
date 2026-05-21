package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.SharedPdfBookmarkSerializer
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val desktopPdfSyncJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

internal fun desktopPdfAnnotationElementForSync(rawJson: String): JsonElement? {
    val annotations = SharedPdfAnnotationSerializer.decode(rawJson)
    if (annotations.isEmpty()) return null
    return SharedPdfAnnotationSidecarCodec.encodeAnnotationsElement(annotations)
}

internal fun desktopPdfRichTextElementForSync(rawJson: String): JsonElement? {
    val element = runCatching { desktopPdfSyncJson.parseToJsonElement(rawJson) }.getOrNull()
        ?: return null
    val document = SharedPdfRichTextSerializer.decodeElement(element)
    if (document.text.isEmpty() && document.spans.isEmpty()) return null
    return SharedPdfRichTextSerializer.encodeElement(document)
}

internal fun desktopPdfBookmarksMetadataJson(book: BookItem): String? {
    if (book.type != FileType.PDF) return null
    val path = book.path?.takeIf { it.isNotBlank() } ?: return null
    val bookmarkFile = desktopPdfBookmarkFile(path).takeIf { it.isFile } ?: return null
    return desktopPdfBookmarksMetadataJson(
        bookmarks = SharedPdfBookmarkSerializer.decode(bookmarkFile.readText()),
        lastPageIndex = book.lastPageIndex
    )
}

internal fun desktopPdfBookmarksMetadataJson(
    bookmarks: List<SharedPdfBookmark>,
    lastPageIndex: Int?
): String {
    val totalPages = maxOf(
        (lastPageIndex ?: 0) + 1,
        (bookmarks.maxOfOrNull { it.pageIndex } ?: 0) + 1
    ).coerceAtLeast(1)
    return desktopPdfSyncJson.encodeToString(
        JsonElement.serializer(),
        JsonArray(
            bookmarks.map { bookmark ->
                JsonObject(
                    mapOf(
                        "pageIndex" to JsonPrimitive(bookmark.pageIndex.coerceAtLeast(0)),
                        "title" to JsonPrimitive(bookmark.label.ifBlank { "Page ${bookmark.pageIndex + 1}" }),
                        "totalPages" to JsonPrimitive(totalPages)
                    )
                )
            }
        )
    )
}

internal fun desktopPdfBookmarkMetadataTimestamp(book: BookItem): Long {
    if (book.type != FileType.PDF) return 0L
    val path = book.path?.takeIf { it.isNotBlank() } ?: return 0L
    return desktopPdfBookmarkFile(path).lastModifiedIfFile()
}

internal fun importDesktopPdfBookmarksMetadata(
    book: BookItem,
    bookmarksJson: String?,
    timestamp: Long
): Boolean {
    if (book.type != FileType.PDF) return false
    val path = book.path?.takeIf { it.isNotBlank() } ?: return false
    val rawJson = bookmarksJson?.takeIf { it.isNotBlank() } ?: return false
    val bookmarks = desktopPdfBookmarksFromMetadataJson(rawJson)
    val bookmarkFile = desktopPdfBookmarkFile(path)
    val localTimestamp = bookmarkFile.lastModifiedIfFile()
    if (timestamp <= localTimestamp + 1000L) return false

    if (bookmarks.isEmpty()) {
        if (bookmarkFile.isFile) bookmarkFile.delete()
        return true
    }

    bookmarkFile.parentFile?.mkdirs()
    bookmarkFile.writeText(SharedPdfBookmarkSerializer.encode(bookmarks))
    bookmarkFile.setLastModified(timestamp)
    return true
}

internal fun desktopPdfBookmarksFromMetadataJson(rawJson: String): List<SharedPdfBookmark> {
    val root = runCatching { desktopPdfSyncJson.parseToJsonElement(rawJson) }.getOrNull()
        ?: return emptyList()

    root.jsonArrayOrNull()?.let { androidBookmarks ->
        return androidBookmarks.mapNotNull { element ->
            val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
            val pageIndex = obj.int("pageIndex") ?: return@mapNotNull null
            SharedPdfBookmark(
                pageIndex = pageIndex.coerceAtLeast(0),
                label = obj.string("title") ?: obj.string("label") ?: "Page ${pageIndex + 1}",
                createdAt = obj.longString("createdAt") ?: 0L
            )
        }
    }

    return SharedPdfBookmarkSerializer.decode(rawJson)
}

private fun File.lastModifiedIfFile(): Long {
    return if (isFile()) lastModified() else 0L
}

private fun JsonElement.jsonArrayOrNull(): JsonArray? {
    if (this is JsonNull) return null
    return runCatching { jsonArray }.getOrNull()
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? {
    if (this is JsonNull) return null
    return runCatching { jsonObject }.getOrNull()
}

private fun JsonObject.string(name: String): String? {
    return this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}

private fun JsonObject.int(name: String): Int? {
    return this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
}

private fun JsonObject.longString(name: String): Long? {
    return string(name)?.toLongOrNull()
        ?: this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}
