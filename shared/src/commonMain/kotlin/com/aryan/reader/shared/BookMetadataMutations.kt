package com.aryan.reader.shared

/**
 * Applies user-visible library metadata while keeping Android's independent
 * metadata clock and original-value provenance.
 */
fun BookItem.withUserEditedMetadata(
    edited: BookItem,
    modifiedAt: Long = currentTimestamp(),
): BookItem {
    require(id == edited.id) { "A metadata edit cannot change book identity." }
    val changed = displayName != edited.displayName ||
        title != edited.title ||
        author != edited.author ||
        description != edited.description ||
        seriesName != edited.seriesName ||
        seriesIndex != edited.seriesIndex ||
        coverImagePath != edited.coverImagePath ||
        tags != edited.tags
    if (!changed) return this

    val embeddedMetadataChanged = title != edited.title ||
        author != edited.author ||
        description != edited.description ||
        seriesName != edited.seriesName ||
        seriesIndex != edited.seriesIndex
    return edited.copy(
        path = path,
        timestamp = timestamp,
        originalTitle = if (embeddedMetadataChanged) originalTitle ?: title else edited.originalTitle,
        originalAuthor = if (embeddedMetadataChanged) originalAuthor ?: author else edited.originalAuthor,
        originalSeriesName = if (embeddedMetadataChanged) originalSeriesName ?: seriesName else edited.originalSeriesName,
        originalSeriesIndex = if (embeddedMetadataChanged) originalSeriesIndex ?: seriesIndex else edited.originalSeriesIndex,
        originalDescription = if (embeddedMetadataChanged) originalDescription ?: description else edited.originalDescription,
        metadataModifiedTimestamp = modifiedAt,
        titleSortKey = if (displayName != edited.displayName) edited.displayName else edited.titleSortKey,
    )
}

/**
 * Backfills metadata discovered by a reader loader. Existing values—including
 * user edits—win, and all reading/session fields remain on the receiver.
 */
fun BookItem.withLoadedMetadata(title: String?, author: String?): BookItem {
    val loadedTitle = title?.takeIf { it.isNotBlank() }
    val loadedAuthor = author?.takeIf { it.isNotBlank() }
    val nextTitle = this.title?.takeIf { it.isNotBlank() } ?: loadedTitle
    val nextAuthor = this.author?.takeIf { it.isNotBlank() } ?: loadedAuthor
    if (nextTitle == this.title && nextAuthor == this.author) return this
    return copy(title = nextTitle, author = nextAuthor)
}
