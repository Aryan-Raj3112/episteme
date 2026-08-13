package com.aryan.reader

import org.json.JSONObject

internal data class PendingExternalFileRemoval(
    val bookId: String,
    val uriString: String?,
)

internal fun encodePendingExternalFileRemoval(removal: PendingExternalFileRemoval): String =
    JSONObject()
        .put("bookId", removal.bookId)
        .apply {
            if (!removal.uriString.isNullOrBlank()) {
                put("uriString", removal.uriString)
            }
        }
        .toString()

internal fun decodePendingExternalFileRemoval(value: String): PendingExternalFileRemoval? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    return if (trimmed.startsWith("{")) {
        runCatching {
            val json = JSONObject(trimmed)
            val bookId = json.optString("bookId").takeIf { it.isNotBlank() }
            val uriString = json.optString("uriString").takeIf { it.isNotBlank() }
            bookId?.let { PendingExternalFileRemoval(it, uriString) }
        }.getOrNull()
    } else {
        PendingExternalFileRemoval(trimmed, null)
    }
}
