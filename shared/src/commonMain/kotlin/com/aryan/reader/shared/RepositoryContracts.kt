package com.aryan.reader.shared

import kotlinx.coroutines.flow.Flow

data class ImportedBookFile(
    val name: String,
    val uriString: String?,
    val localPath: String?,
    val size: Long
)

interface BookRepository {
    fun observeBooks(): Flow<List<BookItem>>
    suspend fun getBook(bookId: String): BookItem?
    suspend fun upsertBook(book: BookItem)
    suspend fun removeBooks(bookIds: Set<String>)
}

interface LibraryRepository {
    fun observeLibraryState(): Flow<LibraryState>
    suspend fun updateSortOrder(sortOrder: SortOrder)
    suspend fun updateFilters(filters: LibraryFilters)
    suspend fun updateSearchQuery(query: String)
}

interface SettingsRepository {
    fun observeAppThemeMode(): Flow<AppThemeMode>
    fun observeReaderTheme(): Flow<ReaderTheme>
    suspend fun setAppThemeMode(mode: AppThemeMode)
    suspend fun setReaderTheme(theme: ReaderTheme)
}

interface FileImporter {
    suspend fun importFiles(files: List<ImportedBookFile>): List<BookItem>
}

interface ReaderDocumentLoader {
    suspend fun canLoad(type: FileType): Boolean
    suspend fun loadDocument(book: BookItem): SharedReaderDocument
}

interface SyncAdapter {
    val isAvailable: Boolean
    suspend fun syncNow()
}

interface AiAdapter {
    val isAvailable: Boolean
    suspend fun define(text: String, context: String? = null): AiDefinitionResult
    suspend fun summarize(text: String): SummarizationResult
    suspend fun recap(textBeforeCurrentLocation: String): RecapResult
}

interface TtsAdapter {
    val isAvailable: Boolean
    suspend fun speak(text: String)
    suspend fun stop()
}
