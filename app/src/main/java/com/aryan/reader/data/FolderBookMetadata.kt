// FolderBookMetadata.kt
package com.aryan.reader.data

import com.aryan.reader.FileType
import org.json.JSONObject

data class FolderBookMetadata(
    val bookId: String,
    val title: String?,
    val author: String?,
    val displayName: String,
    val type: String,
    val lastChapterIndex: Int?,
    val lastPage: Int?,
    val lastPositionCfi: String?,
    val progressPercentage: Float,
    val isRecent: Boolean,
    val lastModifiedTimestamp: Long,
    val bookmarksJson: String?,
    val locatorBlockIndex: Int?,
    val locatorCharOffset: Int?
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("bookId", bookId)
        json.put("title", title)
        json.put("author", author)
        json.put("displayName", displayName)
        json.put("type", type)
        json.put("lastChapterIndex", lastChapterIndex ?: -1)
        json.put("lastPage", lastPage ?: -1)
        json.put("lastPositionCfi", lastPositionCfi)
        json.put("progressPercentage", progressPercentage.toDouble())
        json.put("isRecent", isRecent)
        json.put("lastModifiedTimestamp", lastModifiedTimestamp)
        json.put("bookmarksJson", bookmarksJson)
        json.put("locatorBlockIndex", locatorBlockIndex ?: -1)
        json.put("locatorCharOffset", locatorCharOffset ?: -1)
        return json.toString()
    }

    companion object {
        fun fromJsonString(jsonString: String): FolderBookMetadata {
            val json = JSONObject(jsonString)

            fun JSONObject.optStringNull(key: String): String? {
                return if (has(key) && !isNull(key)) getString(key) else null
            }

            fun JSONObject.optIntNull(key: String): Int? {
                val value = optInt(key, -1)
                return if (value == -1) null else value
            }

            return FolderBookMetadata(
                bookId = json.getString("bookId"),
                title = json.optStringNull("title"),
                author = json.optStringNull("author"),
                displayName = json.optString("displayName", "Unknown"),
                type = json.optString("type", "PDF"),
                lastChapterIndex = json.optIntNull("lastChapterIndex"),
                lastPage = json.optIntNull("lastPage"),
                lastPositionCfi = json.optStringNull("lastPositionCfi"),
                progressPercentage = json.optDouble("progressPercentage", 0.0).toFloat(),
                isRecent = json.optBoolean("isRecent", true),
                lastModifiedTimestamp = json.optLong("lastModifiedTimestamp", 0L),
                bookmarksJson = json.optStringNull("bookmarksJson"),
                locatorBlockIndex = json.optIntNull("locatorBlockIndex"),
                locatorCharOffset = json.optIntNull("locatorCharOffset")
            )
        }
    }
}

fun FolderBookMetadata.toRecentFileItem(uriString: String?, coverPath: String?, sourceFolderUri: String?): RecentFileItem {
    return RecentFileItem(
        bookId = this.bookId,
        uriString = uriString,
        type = try { FileType.valueOf(this.type) } catch (_: Exception) { FileType.EPUB },
        displayName = this.displayName,
        timestamp = System.currentTimeMillis(),
        coverImagePath = coverPath,
        title = this.title,
        author = this.author,
        lastChapterIndex = this.lastChapterIndex,
        lastPage = this.lastPage,
        lastPositionCfi = this.lastPositionCfi,
        locatorBlockIndex = this.locatorBlockIndex,
        locatorCharOffset = this.locatorCharOffset,
        progressPercentage = this.progressPercentage,
        isRecent = this.isRecent,
        isAvailable = true,
        lastModifiedTimestamp = this.lastModifiedTimestamp,
        isDeleted = false,
        bookmarksJson = this.bookmarksJson,
        sourceFolderUri = sourceFolderUri
    )
}