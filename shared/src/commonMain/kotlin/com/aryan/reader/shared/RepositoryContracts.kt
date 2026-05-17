package com.aryan.reader.shared

import kotlinx.coroutines.flow.Flow

data class ImportedBookFile(
    val name: String,
    val uriString: String?,
    val localPath: String?,
    val size: Long,
    val sourceFolder: String? = null,
    val id: String? = null
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
    suspend fun defineStreaming(
        text: String,
        context: String? = null,
        onUpdate: (String) -> Unit
    ): AiDefinitionResult {
        val result = define(text, context)
        result.definition?.takeIf { it.isNotBlank() }?.let(onUpdate)
        return result
    }

    suspend fun summarize(text: String): SummarizationResult
    suspend fun summarizeStreaming(
        text: String,
        onUsageReceived: (cost: Double?, freeRemaining: Int?) -> Unit = { _, _ -> },
        onUpdate: (String) -> Unit
    ): SummarizationResult {
        val result = summarize(text)
        if (result.cost != null || result.freeRemaining != null) {
            onUsageReceived(result.cost, result.freeRemaining)
        }
        result.summary?.takeIf { it.isNotBlank() }?.let(onUpdate)
        return result
    }

    suspend fun recap(textBeforeCurrentLocation: String): RecapResult
}

interface TtsAdapter {
    val isAvailable: Boolean
    suspend fun speak(text: String)
    suspend fun pause() = Unit
    suspend fun resume() = Unit
    suspend fun stop()
}
