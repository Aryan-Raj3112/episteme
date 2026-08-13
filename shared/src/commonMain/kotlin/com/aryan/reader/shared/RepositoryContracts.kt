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

interface BookTtsListeningProgressRepository {
    fun observeAll(): Flow<List<SharedBookTtsListeningProgress>>
    suspend fun get(bookId: String): SharedBookTtsListeningProgress?
    suspend fun upsert(progress: SharedBookTtsListeningProgress)
    suspend fun delete(bookId: String)
}

interface AudiobookPlaybackAdapter {
    fun observeState(): Flow<SharedAudiobookPlaybackState>
    suspend fun play(request: SharedAudiobookPlaybackRequest)
    suspend fun pause()
    suspend fun resume()
    suspend fun seekTo(positionMs: Long)
    suspend fun setSpeed(speed: Float)
    suspend fun stop()
    suspend fun startSleepTimer(minutes: Int)
    suspend fun cancelSleepTimer()
}
