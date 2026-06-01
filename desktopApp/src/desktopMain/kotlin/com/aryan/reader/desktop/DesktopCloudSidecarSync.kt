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
import kotlinx.serialization.json.jsonObject
import java.io.File

internal object DesktopCloudSidecarSync {
    fun localAnnotationDebugSummary(book: BookItem): String {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return "path=null type=${book.type}"
        if (book.type != FileType.PDF) return "path=${path.logPreview(140)} type=${book.type}"
        val annotationFile = desktopPdfAnnotationFile(path)
        val deletedAnnotationFile = desktopPdfAnnotationDeletionFile(path)
        val bookmarkFile = desktopPdfBookmarkFile(path)
        val richTextFile = desktopPdfRichTextFile(path)
        return "path=${path.logPreview(140)} " +
            "annotations{exists=${annotationFile.isFile} syncable=${annotationFile.hasSyncablePdfAnnotations()} " +
            "bytes=${annotationFile.length()} ts=${annotationFile.lastModifiedIfFile()}} " +
            "deletedAnnotations{exists=${deletedAnnotationFile.isFile} count=${deletedAnnotationFile.annotationDeletionCount()} " +
            "bytes=${deletedAnnotationFile.length()} ts=${deletedAnnotationFile.lastModifiedIfFile()}} " +
            "bookmarks{exists=${bookmarkFile.isFile} bytes=${bookmarkFile.length()} ts=${bookmarkFile.lastModifiedIfFile()}} " +
            "text{exists=${richTextFile.isFile} syncable=${richTextFile.hasSyncablePdfRichText()} " +
            "bytes=${richTextFile.length()} ts=${richTextFile.lastModifiedIfFile()}} " +
            "payloadTs=${localAnnotationPayloadTimestamp(book)} totalTs=${localAnnotationTimestamp(book)}"
    }

    fun hasLocalAnnotationData(book: BookItem): Boolean {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return false
        if (book.type != FileType.PDF) return false
        return desktopPdfAnnotationFile(path).hasSyncablePdfAnnotations() ||
            desktopPdfAnnotationDeletionFile(path).hasSyncablePdfAnnotationDeletions() ||
            desktopPdfBookmarkFile(path).isFile ||
            desktopPdfRichTextFile(path).hasSyncablePdfRichText()
    }

    fun localAnnotationTimestamp(book: BookItem): Long {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return 0L
        if (book.type != FileType.PDF) return 0L
        return maxOf(
            localAnnotationPayloadTimestamp(path),
            desktopPdfBookmarkFile(path).lastModifiedIfFile()
        )
    }

    fun localAnnotationPayloadTimestamp(book: BookItem): Long {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return 0L
        if (book.type != FileType.PDF) return 0L
        return localAnnotationPayloadTimestamp(path)
    }

    fun markAnnotationPayloadSynced(book: BookItem, timestamp: Long) {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return
        if (book.type != FileType.PDF || timestamp <= 0L) return
        listOf(desktopPdfAnnotationFile(path), desktopPdfAnnotationDeletionFile(path), desktopPdfRichTextFile(path))
            .filter { it.isFile }
            .forEach { it.setLastModified(timestamp) }
    }

    fun recordAnnotationDeletions(book: BookItem, annotationIds: Collection<String>) {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return
        if (book.type != FileType.PDF) return
        recordAnnotationDeletions(path, book.id, annotationIds)
    }

    fun recordAnnotationDeletions(documentPath: String, logBookId: String, annotationIds: Collection<String>) {
        val ids = annotationIds.mapNotNull { it.takeIf(String::isNotBlank) }.toSet()
        if (ids.isEmpty()) return
        val file = desktopPdfAnnotationDeletionFile(documentPath)
        val existing = if (file.isFile) {
            SharedPdfAnnotationSidecarCodec.annotationDeletionsFromJson(file.readText())
        } else {
            emptyMap()
        }
        val now = System.currentTimeMillis()
        val next = existing.toMutableMap()
        ids.forEach { id -> next[id] = maxOf(next[id] ?: 0L, now) }
        val nextJson = SharedPdfAnnotationSidecarCodec.annotationDeletionsJson(next)
        if (file.isFile && file.readText() == nextJson) return
        file.parentFile?.mkdirs()
        file.writeText(nextJson)
        logDesktopCloudAnnotations {
            "desktop.local.mark_deleted_annotations book=$logBookId ids=${ids.sorted()} " +
                "bytes=${file.length()} ts=${file.lastModified()}"
        }
    }

    fun exportAnnotationBundle(book: BookItem): File? {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return null
        if (book.type != FileType.PDF) return null
        val annotationFile = desktopPdfAnnotationFile(path)
        val deletedAnnotationFile = desktopPdfAnnotationDeletionFile(path)
        val richTextFile = desktopPdfRichTextFile(path)
        logDesktopCloudAnnotations {
            "desktop.export.inspect book=${book.id} ${localAnnotationDebugSummary(book)}"
        }
        val data = buildMap {
            if (annotationFile.isFile) {
                desktopPdfAnnotationElementForSync(annotationFile.readText())?.let { annotations ->
                    put(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS, annotations)
                }
            }
            if (deletedAnnotationFile.hasSyncablePdfAnnotationDeletions()) {
                val deletions = SharedPdfAnnotationSidecarCodec.annotationDeletionsFromJson(deletedAnnotationFile.readText())
                put(
                    SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATION_DELETIONS,
                    SharedPdfAnnotationSidecarCodec.encodeAnnotationDeletionsElement(deletions)
                )
            }
            if (richTextFile.isFile) {
                desktopPdfRichTextElementForSync(richTextFile.readText())?.let { put("text", it) }
            }
        }
        if (data.isEmpty()) {
            logDesktopCloudAnnotations { "desktop.export.skip book=${book.id} reason=no_syncable_payload" }
            return null
        }
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
        logDesktopCloudAnnotations {
            "desktop.export.bundle_ready book=${book.id} keys=${data.keys.toList()} " +
                "canonicalBytes=${canonical.length} fileBytes=${tempFile.length()} temp=${tempFile.name}"
        }
        return tempFile
    }

    fun importAnnotationBundle(book: BookItem, rawJson: String, timestamp: Long): Boolean {
        val path = book.path?.takeIf { it.isNotBlank() } ?: run {
            logDesktopCloudAnnotations { "desktop.import.skip book=${book.id} reason=missing_path bytes=${rawJson.length}" }
            return false
        }
        if (book.type != FileType.PDF) {
            logDesktopCloudAnnotations { "desktop.import.skip book=${book.id} reason=not_pdf type=${book.type} bytes=${rawJson.length}" }
            return false
        }
        val root = cloudSidecarJson.parseElementOrNull(rawJson)?.jsonObjectOrNull() ?: run {
            logDesktopCloudAnnotations { "desktop.import.skip book=${book.id} reason=parse_failed bytes=${rawJson.length}" }
            return false
        }
        val data = root["data"]?.jsonObjectOrNull() ?: root
        val canonicalData = SharedPdfAnnotationSidecarCodec.withCanonicalAnnotations(data)
        val annotationFile = desktopPdfAnnotationFile(path)
        val deletedAnnotationFile = desktopPdfAnnotationDeletionFile(path)
        val bookmarkFile = desktopPdfBookmarkFile(path)
        val richTextFile = desktopPdfRichTextFile(path)
        logDesktopCloudAnnotations {
            "desktop.import.inspect book=${book.id} remoteTs=$timestamp rawBytes=${rawJson.length} " +
                "rawKeys=${data.keys.toList()} canonicalKeys=${canonicalData.keys.toList()} " +
                localAnnotationDebugSummary(book)
        }

        if (canonicalData.hasPdfAnnotationPayload()) {
            val annotations = SharedPdfAnnotationSidecarCodec.annotationsFromData(canonicalData)
            if (annotations.isEmpty()) {
                if (annotationFile.isFile) {
                    val deleted = annotationFile.delete()
                    logDesktopCloudAnnotations { "desktop.import.delete_annotations book=${book.id} deleted=$deleted" }
                } else {
                    logDesktopCloudAnnotations { "desktop.import.annotations_empty book=${book.id} existing=false" }
                }
            } else {
                annotationFile.parentFile?.mkdirs()
                annotationFile.writeText(SharedPdfAnnotationSerializer.encode(annotations))
                annotationFile.setLastModified(timestamp)
                logDesktopCloudAnnotations {
                    "desktop.import.write_annotations book=${book.id} count=${annotations.size} " +
                        "bytes=${annotationFile.length()} ts=${annotationFile.lastModified()}"
                }
            }
        } else if (annotationFile.isFile) {
            val deleted = annotationFile.delete()
            logDesktopCloudAnnotations { "desktop.import.delete_annotations_missing_payload book=${book.id} deleted=$deleted" }
        } else {
            logDesktopCloudAnnotations { "desktop.import.no_annotation_payload book=${book.id} existing=false" }
        }

        val deletions = SharedPdfAnnotationSidecarCodec.annotationDeletionsFromData(canonicalData)
        if (deletions.isEmpty()) {
            if (deletedAnnotationFile.isFile) {
                val deleted = deletedAnnotationFile.delete()
                logDesktopCloudAnnotations { "desktop.import.delete_annotation_tombstones book=${book.id} deleted=$deleted" }
            }
        } else {
            deletedAnnotationFile.parentFile?.mkdirs()
            deletedAnnotationFile.writeText(SharedPdfAnnotationSidecarCodec.annotationDeletionsJson(deletions))
            deletedAnnotationFile.setLastModified(timestamp)
            logDesktopCloudAnnotations {
                "desktop.import.write_annotation_tombstones book=${book.id} count=${deletions.size} " +
                    "bytes=${deletedAnnotationFile.length()} ts=${deletedAnnotationFile.lastModified()}"
            }
        }

        canonicalData["bookmarks"]?.let { bookmarks ->
            bookmarkFile.parentFile?.mkdirs()
            bookmarkFile.writeText(cloudSidecarJson.encodeToString(JsonElement.serializer(), bookmarks))
            bookmarkFile.setLastModified(timestamp)
            logDesktopCloudAnnotations {
                "desktop.import.write_bookmarks book=${book.id} bytes=${bookmarkFile.length()} ts=${bookmarkFile.lastModified()}"
            }
        }

        canonicalData["text"]?.let { richText ->
            val richDocument = SharedPdfRichTextSerializer.decodeElement(richText)
            if (richDocument.text.isEmpty() && richDocument.spans.isEmpty()) {
                if (richTextFile.isFile) {
                    val deleted = richTextFile.delete()
                    logDesktopCloudAnnotations { "desktop.import.delete_text_empty book=${book.id} deleted=$deleted" }
                } else {
                    logDesktopCloudAnnotations { "desktop.import.text_empty book=${book.id} existing=false" }
                }
            } else {
                richTextFile.parentFile?.mkdirs()
                richTextFile.writeText(SharedPdfRichTextSerializer.encode(richDocument))
                richTextFile.setLastModified(timestamp)
                logDesktopCloudAnnotations {
                    "desktop.import.write_text book=${book.id} textChars=${richDocument.text.length} " +
                        "spans=${richDocument.spans.size} bytes=${richTextFile.length()} ts=${richTextFile.lastModified()}"
                }
            }
        } ?: run {
            if (richTextFile.isFile) {
                val deleted = richTextFile.delete()
                logDesktopCloudAnnotations { "desktop.import.delete_text_missing book=${book.id} deleted=$deleted" }
            } else {
                logDesktopCloudAnnotations { "desktop.import.no_text_payload book=${book.id} existing=false" }
            }
        }
        logDesktopCloudAnnotations {
            "desktop.import.done book=${book.id} remoteTs=$timestamp ${localAnnotationDebugSummary(book)}"
        }
        return true
    }
}

private fun localAnnotationPayloadTimestamp(path: String): Long {
    return maxOf(
        desktopPdfAnnotationFile(path).lastModifiedIfSyncableAnnotations(),
        desktopPdfAnnotationDeletionFile(path).lastModifiedIfSyncableAnnotationDeletions(),
        desktopPdfRichTextFile(path).lastModifiedIfSyncableRichText()
    )
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

private fun File.hasSyncablePdfAnnotations(): Boolean {
    return isFile && desktopPdfAnnotationElementForSync(readText()) != null
}

private fun File.lastModifiedIfSyncableAnnotations(): Long {
    return if (hasSyncablePdfAnnotations()) lastModified() else 0L
}

private fun File.annotationDeletionCount(): Int {
    return if (isFile) SharedPdfAnnotationSidecarCodec.annotationDeletionsFromJson(readText()).size else 0
}

private fun File.hasSyncablePdfAnnotationDeletions(): Boolean {
    return annotationDeletionCount() > 0
}

private fun File.lastModifiedIfSyncableAnnotationDeletions(): Long {
    return if (hasSyncablePdfAnnotationDeletions()) lastModified() else 0L
}

private fun File.hasSyncablePdfRichText(): Boolean {
    return isFile && desktopPdfRichTextElementForSync(readText()) != null
}

private fun File.lastModifiedIfSyncableRichText(): Long {
    return if (hasSyncablePdfRichText()) lastModified() else 0L
}
