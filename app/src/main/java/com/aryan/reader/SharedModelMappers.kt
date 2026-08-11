package com.aryan.reader

import com.aryan.reader.data.BookShelfCrossRef
import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.ShelfEntity
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.BookItem as SharedBookItem
import com.aryan.reader.shared.BookShelfRef as SharedBookShelfRef
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.FileType as SharedFileType
import com.aryan.reader.shared.LibraryFilters as SharedLibraryFilters
import com.aryan.reader.shared.LibraryFeatureState
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.Shelf as SharedShelf
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.SyncedFolder as SharedSyncedFolder
import com.aryan.reader.shared.Tag as SharedTag
import com.aryan.reader.shared.toStablePositionCfi
import java.util.LinkedHashMap

fun FileType.toSharedFileType(): SharedFileType = this

fun SharedFileType.toAndroidFileType(): FileType = this

fun LibraryFilters.toSharedLibraryFilters(): SharedLibraryFilters = this

fun SharedLibraryFilters.toAndroidLibraryFilters(): LibraryFilters = this

fun SyncedFolder.toSharedSyncedFolder(): SharedSyncedFolder = this

fun SharedSyncedFolder.toAndroidSyncedFolder(): SyncedFolder = this

fun RecentFileItem.toSharedBookItem(): SharedBookItem {
    return toSharedBookItem(
        displayName = customName ?: displayName,
        includeReaderAnnotations = true
    )
}

internal fun RecentFileItem.toSharedBookItem(
    displayName: String,
    includeReaderAnnotations: Boolean
): SharedBookItem {
    return SharedBookItem(
        id = bookId,
        path = uriString,
        type = type,
        displayName = displayName,
        timestamp = timestamp,
        dateAddedTimestamp = dateAddedTimestamp.takeIf { it > 0L } ?: timestamp,
        coverImagePath = coverImagePath,
        title = title,
        author = author,
        description = description,
        originalTitle = originalTitle,
        originalAuthor = originalAuthor,
        originalSeriesName = originalSeriesName,
        originalSeriesIndex = originalSeriesIndex,
        originalDescription = originalDescription,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        isAvailable = isAvailable,
        fileSize = fileSize,
        fileContentModifiedTimestamp = fileContentModifiedTimestamp,
        metadataModifiedTimestamp = lastModifiedTimestamp,
        sourceFolder = sourceFolderUri,
        folderTextMetadataParsed = folderTextMetadataParsed,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        lastPageIndex = lastPage,
        readerPosition = toSharedReaderLocatorOrNull(),
        tags = tags.map { it.toSharedTag() },
        // The card can use a custom name while the embedded title remains intact.
        // Keep that user-facing name as the title-sort key too.
        titleSortKey = customName?.takeIf { it.isNotBlank() },
        readerHighlights = if (includeReaderAnnotations) {
            EpubAnnotationSerializer.parseHighlightsJson(highlightsJson)
        } else {
            emptyList()
        },
        readingPositionModifiedTimestamp = readingPositionModifiedTimestamp
    )
}

fun RecentFileItem.toSharedProjectionBookItem(): SharedBookItem {
    return toSharedBookItem(
        displayName = displayName,
        includeReaderAnnotations = false
    )
}

internal class SharedProjectionBookItemCache(
    private val maxEntries: Int = 10_000
) {
    private val entries = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return size > maxEntries
        }
    }

    fun map(item: RecentFileItem): SharedBookItem {
        entries[item.bookId]?.takeIf { it.source == item }?.let { return it.shared }
        val shared = item.toSharedProjectionBookItem()
        entries[item.bookId] = Entry(item, shared)
        return shared
    }

    fun map(items: List<RecentFileItem>): List<SharedBookItem> {
        if (items.isEmpty()) return emptyList()
        return items.map { map(it) }
    }

    private data class Entry(
        val source: RecentFileItem,
        val shared: SharedBookItem
    )
}

fun SharedBookItem.toRecentFileItem(
    androidBooksById: Map<String, RecentFileItem> = emptyMap(),
    tagEntitiesById: Map<String, TagEntity> = emptyMap()
): RecentFileItem {
    val resolvedTags = tags.map { tag -> tagEntitiesById[tag.id] ?: tag.toTagEntity(createdAt = 0L) }
    val positionCfi = readerPosition?.toSharedPositionCfi()
    androidBooksById[id]?.let { existing ->
        val mappedLastChapterIndex = readerPosition?.chapterIndex ?: existing.lastChapterIndex
        val mappedLastPositionCfi = positionCfi ?: existing.lastPositionCfi
        val mappedLocatorBlockIndex = readerPosition?.blockIndex ?: existing.locatorBlockIndex
        val mappedLocatorCharOffset = readerPosition?.charOffset ?: existing.locatorCharOffset

        if (
            existing.uriString == path &&
            existing.type == type &&
            existing.timestamp == timestamp &&
            existing.dateAddedTimestamp == dateAddedTimestamp &&
            existing.coverImagePath == coverImagePath &&
            existing.title == title &&
            existing.author == author &&
            existing.description == description &&
            existing.originalTitle == originalTitle &&
            existing.originalAuthor == originalAuthor &&
            existing.originalSeriesName == originalSeriesName &&
            existing.originalSeriesIndex == originalSeriesIndex &&
            existing.originalDescription == originalDescription &&
            existing.lastPage == lastPageIndex &&
            existing.progressPercentage == progressPercentage &&
            existing.isRecent == isRecent &&
            existing.isAvailable == isAvailable &&
            existing.sourceFolderUri == sourceFolder &&
            existing.fileSize == fileSize &&
            existing.fileContentModifiedTimestamp == fileContentModifiedTimestamp &&
            existing.lastModifiedTimestamp == metadataModifiedTimestamp &&
            existing.seriesName == seriesName &&
            existing.seriesIndex == seriesIndex &&
            existing.folderTextMetadataParsed == folderTextMetadataParsed &&
            existing.lastChapterIndex == mappedLastChapterIndex &&
            existing.lastPositionCfi == mappedLastPositionCfi &&
            existing.locatorBlockIndex == mappedLocatorBlockIndex &&
            existing.locatorCharOffset == mappedLocatorCharOffset &&
            existing.readingPositionModifiedTimestamp == readingPositionModifiedTimestamp &&
            existing.tags == resolvedTags
        ) {
            return existing
        }

        return existing.copy(
            uriString = path,
            type = type,
            displayName = existing.displayName,
            timestamp = timestamp,
            dateAddedTimestamp = dateAddedTimestamp,
            coverImagePath = coverImagePath,
            title = title,
            author = author,
            description = description,
            originalTitle = originalTitle,
            originalAuthor = originalAuthor,
            originalSeriesName = originalSeriesName,
            originalSeriesIndex = originalSeriesIndex,
            originalDescription = originalDescription,
            lastPage = lastPageIndex,
            progressPercentage = progressPercentage,
            isRecent = isRecent,
            isAvailable = isAvailable,
            sourceFolderUri = sourceFolder,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            lastModifiedTimestamp = metadataModifiedTimestamp,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            folderTextMetadataParsed = folderTextMetadataParsed,
            lastChapterIndex = mappedLastChapterIndex,
            lastPositionCfi = mappedLastPositionCfi,
            locatorBlockIndex = mappedLocatorBlockIndex,
            locatorCharOffset = mappedLocatorCharOffset,
            readingPositionModifiedTimestamp = readingPositionModifiedTimestamp,
            tags = resolvedTags
        )
    }

    return RecentFileItem(
        bookId = id,
        uriString = path,
        type = type,
        displayName = displayName,
        timestamp = timestamp,
        dateAddedTimestamp = dateAddedTimestamp,
        coverImagePath = coverImagePath,
        title = title,
        author = author,
        description = description,
        originalTitle = originalTitle,
        originalAuthor = originalAuthor,
        originalSeriesName = originalSeriesName,
        originalSeriesIndex = originalSeriesIndex,
        originalDescription = originalDescription,
        lastPage = lastPageIndex,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        isAvailable = isAvailable,
        sourceFolderUri = sourceFolder,
        fileSize = fileSize,
        fileContentModifiedTimestamp = fileContentModifiedTimestamp,
        lastModifiedTimestamp = metadataModifiedTimestamp,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        folderTextMetadataParsed = folderTextMetadataParsed,
        lastChapterIndex = readerPosition?.chapterIndex,
        lastPositionCfi = positionCfi,
        locatorBlockIndex = readerPosition?.blockIndex,
        locatorCharOffset = readerPosition?.charOffset,
        readingPositionModifiedTimestamp = readingPositionModifiedTimestamp,
        tags = resolvedTags
    )
}

private fun RecentFileItem.toSharedReaderLocatorOrNull(): SharedReaderLocator? {
    if (
        lastChapterIndex == null &&
        lastPage == null &&
        lastPositionCfi.isNullOrBlank() &&
        locatorBlockIndex == null &&
        locatorCharOffset == null
    ) {
        return null
    }
    return SharedReaderLocator.fromLegacy(
        chapterIndex = lastChapterIndex,
        cfi = lastPositionCfi,
        pageIndex = lastPage
    ).withFallbacks(
        blockIndex = locatorBlockIndex,
        charOffset = locatorCharOffset
    )
}

private fun SharedReaderLocator.toSharedPositionCfi(): String? {
    return toStablePositionCfi()
}

fun TagEntity.toSharedTag(): SharedTag {
    return SharedTag(
        id = id,
        name = name,
        color = color
    )
}

fun SharedTag.toTagEntity(createdAt: Long): TagEntity {
    return TagEntity(
        id = id,
        name = name,
        color = color,
        createdAt = createdAt
    )
}

fun ShelfEntity.toSharedShelfRecord(): ShelfRecord {
    return ShelfRecord(
        id = id,
        name = name,
        isSmart = isSmart,
        smartRulesJson = smartRulesJson
    )
}

fun ShelfRecord.toShelfEntity(createdAt: Long, updatedAt: Long = createdAt): ShelfEntity {
    return ShelfEntity(
        id = id,
        name = name,
        isSmart = isSmart,
        smartRulesJson = smartRulesJson,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun BookShelfCrossRef.toSharedBookShelfRef(): SharedBookShelfRef {
    return SharedBookShelfRef(
        bookId = bookId,
        shelfId = shelfId,
        addedAt = addedAt
    )
}
