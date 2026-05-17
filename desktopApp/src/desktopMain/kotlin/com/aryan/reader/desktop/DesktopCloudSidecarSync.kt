package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

internal object DesktopCloudSidecarSync {
    fun hasLocalAnnotationData(book: BookItem): Boolean {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return false
        if (book.type != FileType.PDF) return false
        return desktopPdfAnnotationFile(path).isFile ||
            desktopPdfBookmarkFile(path).isFile ||
            desktopPdfRichTextFile(path).isFile
    }

    fun localAnnotationTimestamp(book: BookItem): Long {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return 0L
        if (book.type != FileType.PDF) return 0L
        return maxOf(
            desktopPdfAnnotationFile(path).lastModifiedIfFile(),
            desktopPdfBookmarkFile(path).lastModifiedIfFile(),
            desktopPdfRichTextFile(path).lastModifiedIfFile()
        )
    }

    fun exportAnnotationBundle(book: BookItem): File? {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return null
        if (book.type != FileType.PDF) return null
        val annotationFile = desktopPdfAnnotationFile(path)
        val bookmarkFile = desktopPdfBookmarkFile(path)
        val richTextFile = desktopPdfRichTextFile(path)
        val data = buildMap {
            if (annotationFile.isFile) {
                val annotations = SharedPdfAnnotationSerializer.decode(annotationFile.readText())
                put(
                    SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS,
                    SharedPdfAnnotationSidecarCodec.encodeAnnotationsElement(annotations)
                )
            }
            if (bookmarkFile.isFile) {
                cloudSidecarJson.parseElementOrNull(bookmarkFile.readText())?.let { put("bookmarks", it) }
            }
            if (richTextFile.isFile) {
                cloudSidecarJson.parseElementOrNull(richTextFile.readText())?.let { element ->
                    put("text", SharedPdfRichTextSerializer.encodeElement(SharedPdfRichTextSerializer.decodeElement(element)))
                }
            }
        }
        if (data.isEmpty()) return null
        val payload = JsonObject(mapOf("version" to JsonPrimitive(2)) + data)
        val canonical = SharedPdfAnnotationSidecarCodec.canonicalizeDataJson(
            cloudSidecarJson.encodeToString(JsonElement.serializer(), payload)
        )
        val tempFile = File(
            desktopUserCacheRoot(),
            "sync_bundle_${book.id.toDesktopSafeFileName()}_${System.nanoTime()}.json"
        )
        tempFile.parentFile?.mkdirs()
        tempFile.writeText(canonical)
        return tempFile
    }

    fun importAnnotationBundle(book: BookItem, rawJson: String, timestamp: Long): Boolean {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return false
        if (book.type != FileType.PDF) return false
        val root = cloudSidecarJson.parseElementOrNull(rawJson)?.jsonObjectOrNull() ?: return false
        val data = root["data"]?.jsonObjectOrNull() ?: root
        val canonicalData = SharedPdfAnnotationSidecarCodec.withCanonicalAnnotations(data)
        val annotationFile = desktopPdfAnnotationFile(path)
        val bookmarkFile = desktopPdfBookmarkFile(path)
        val richTextFile = desktopPdfRichTextFile(path)

        if (canonicalData.hasPdfAnnotationPayload()) {
            val annotations = SharedPdfAnnotationSidecarCodec.annotationsFromData(canonicalData)
            annotationFile.parentFile?.mkdirs()
            annotationFile.writeText(SharedPdfAnnotationSerializer.encode(annotations))
            annotationFile.setLastModified(timestamp)
        } else if (annotationFile.isFile) {
            annotationFile.delete()
        }

        canonicalData["bookmarks"]?.let { bookmarks ->
            bookmarkFile.parentFile?.mkdirs()
            bookmarkFile.writeText(cloudSidecarJson.encodeToString(JsonElement.serializer(), bookmarks))
            bookmarkFile.setLastModified(timestamp)
        } ?: run {
            if (bookmarkFile.isFile) bookmarkFile.delete()
        }

        canonicalData["text"]?.let { richText ->
            val richDocument = SharedPdfRichTextSerializer.decodeElement(richText)
            richTextFile.parentFile?.mkdirs()
            richTextFile.writeText(SharedPdfRichTextSerializer.encode(richDocument))
            richTextFile.setLastModified(timestamp)
        } ?: run {
            if (richTextFile.isFile) richTextFile.delete()
        }
        return true
    }
}

private val cloudSidecarJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

private fun Json.parseElementOrNull(raw: String): JsonElement? {
    return runCatching { parseToJsonElement(raw) }.getOrNull()
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? {
    if (this is JsonNull) return null
    return runCatching { jsonObject }.getOrNull()
}

private fun JsonObject.hasPdfAnnotationPayload(): Boolean {
    return containsKey(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS) ||
        containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_INK) ||
        containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_TEXT_BOXES) ||
        containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_HIGHLIGHTS)
}

private fun File.lastModifiedIfFile(): Long {
    return if (isFile) lastModified() else 0L
}
