package com.aryan.reader.shared

fun sampleLibraryState(): LibraryState {
    val reference = Tag("reference", "Reference", 0xFF9575CD.toInt())
    val reading = Tag("reading", "Reading", 0xFF81C784.toInt())
    val now = currentTimestamp()

    return LibraryState(
        books = listOf(
            BookItem(
                id = "sample_pdf",
                path = null,
                type = FileType.PDF,
                displayName = "Designing Data-Intensive Applications.pdf",
                timestamp = now - 1_000,
                title = "Designing Data-Intensive Applications",
                author = "Martin Kleppmann",
                progressPercentage = 42f,
                fileSize = 18_400_000,
                sourceFolder = "Samples",
                tags = listOf(reference, reading)
            ),
            BookItem(
                id = "sample_epub",
                path = null,
                type = FileType.EPUB,
                displayName = "The Pragmatic Programmer.epub",
                timestamp = now - 2_000,
                title = "The Pragmatic Programmer",
                author = "Andrew Hunt, David Thomas",
                progressPercentage = 12f,
                fileSize = 4_200_000,
                sourceFolder = "Samples",
                tags = listOf(reading)
            ),
            BookItem(
                id = "sample_doc",
                path = null,
                type = FileType.DOCX,
                displayName = "Research Notes.docx",
                timestamp = now - 3_000,
                title = "Research Notes",
                author = "Local",
                progressPercentage = 0f,
                fileSize = 920_000,
                sourceFolder = "Samples"
            )
        )
    )
}

fun sampleReaderScreenState(): SharedReaderScreenState {
    val library = sampleLibraryState()
    val tags = library.books.flatMap { it.tags }.distinctBy { it.id }
    return SharedReaderScreenState(
        rawLibraryBooks = library.books,
        recentBooks = library.books.filter { it.isRecent },
        libraryBooks = library.books,
        selectedBookIds = library.selectedBookIds,
        searchQuery = library.searchQuery,
        sortOrder = library.sortOrder,
        libraryFilters = library.filters,
        recentFilesLimit = library.recentLimit,
        allTags = tags
    )
}
