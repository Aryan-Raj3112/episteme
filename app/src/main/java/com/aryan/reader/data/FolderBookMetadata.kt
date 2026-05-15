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
    val locatorCharOffset: Int?,
    val customName: String?,
    val highlightsJson: String?,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
    val originalTitle: String? = null,
    val originalAuthor: String? = null,
    val originalSeriesName: String? = null,
    val originalSeriesIndex: Double? = null,
    val originalDescription: String? = null
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
        json.put("customName", customName)
        json.put("highlightsJson", highlightsJson)
        json.put("seriesName", seriesName)
        json.put("seriesIndex", seriesIndex)
        json.put("description", description)
        json.put("originalTitle", originalTitle)
        json.put("originalAuthor", originalAuthor)
        json.put("originalSeriesName", originalSeriesName)
        json.put("originalSeriesIndex", originalSeriesIndex)
        json.put("originalDescription", originalDescription)
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
                locatorCharOffset = json.optIntNull("locatorCharOffset"),
                customName = json.optStringNull("customName"),
                highlightsJson = json.optStringNull("highlightsJson"),
                seriesName = json.optStringNull("seriesName"),
                seriesIndex = if (json.has("seriesIndex") && !json.isNull("seriesIndex")) json.optDouble("seriesIndex") else null,
                description = json.optStringNull("description"),
                originalTitle = json.optStringNull("originalTitle"),
                originalAuthor = json.optStringNull("originalAuthor"),
                originalSeriesName = json.optStringNull("originalSeriesName"),
                originalSeriesIndex = if (json.has("originalSeriesIndex") && !json.isNull("originalSeriesIndex")) json.optDouble("originalSeriesIndex") else null,
                originalDescription = json.optStringNull("originalDescription")
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
        sourceFolderUri = sourceFolderUri,
        customName = this.customName,
        highlightsJson = this.highlightsJson,
        seriesName = this.seriesName,
        seriesIndex = this.seriesIndex,
        description = this.description,
        originalTitle = this.originalTitle,
        originalAuthor = this.originalAuthor,
        originalSeriesName = this.originalSeriesName,
        originalSeriesIndex = this.originalSeriesIndex,
        originalDescription = this.originalDescription
    )
}
