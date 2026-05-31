/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.tts

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.pow

const val googleCloudWorkerTtsUrl = BuildConfig.TTS_WORKER_URL

const val TTS_CHUNK_MAX_LENGTH = 250
const val DEFAULT_SPEAKER_ID = "Aoede"
internal const val TTS_SETTINGS_PREFS_NAME = "epub_reader_settings"
internal const val TTS_SPEAKER_KEY = "tts_speaker"

data class GeminiVoice(val id: String, val name: String, val description: String)

val GEMINI_TTS_SPEAKERS = listOf(
    GeminiVoice("Zephyr", "Zephyr", "Bright, Higher pitch"),
    GeminiVoice("Puck", "Puck", "Upbeat, Middle pitch"),
    GeminiVoice("Charon", "Charon", "Informative, Lower pitch"),
    GeminiVoice("Kore", "Kore", "Firm, Middle pitch"),
    GeminiVoice("Fenrir", "Fenrir", "Excitable, Lower middle pitch"),
    GeminiVoice("Leda", "Leda", "Youthful, Higher pitch"),
    GeminiVoice("Orus", "Orus", "Firm, Lower middle pitch"),
    GeminiVoice("Aoede", "Aoede", "Breezy, Middle pitch"),
    GeminiVoice("Callirrhoe", "Callirrhoe", "Easy-going, Middle pitch"),
    GeminiVoice("Autonoe", "Autonoe", "Bright, Middle pitch"),
    GeminiVoice("Enceladus", "Enceladus", "Breathy, Lower pitch"),
    GeminiVoice("Iapetus", "Iapetus", "Clear, Lower middle pitch"),
    GeminiVoice("Umbriel", "Umbriel", "Easy-going, Lower middle pitch"),
    GeminiVoice("Algieba", "Algieba", "Smooth, Lower pitch"),
    GeminiVoice("Despina", "Despina", "Smooth, Middle pitch"),
    GeminiVoice("Erinome", "Erinome", "Clear, Middle pitch"),
    GeminiVoice("Algenib", "Algenib", "Gravelly, Lower pitch"),
    GeminiVoice("Rasalgethi", "Rasalgethi", "Informative, Middle pitch"),
    GeminiVoice("Laomedeia", "Laomedeia", "Upbeat, Higher pitch"),
    GeminiVoice("Achernar", "Achernar", "Soft, Higher pitch"),
    GeminiVoice("Alnilam", "Alnilam", "Firm, Lower middle pitch"),
    GeminiVoice("Schedar", "Schedar", "Even, Lower middle pitch"),
    GeminiVoice("Gacrux", "Gacrux", "Mature, Middle pitch"),
    GeminiVoice("Pulcherrima", "Pulcherrima", "Forward, Middle pitch"),
    GeminiVoice("Achird", "Achird", "Friendly, Lower middle pitch"),
    GeminiVoice("Zubenelgenubi", "Zubenelgenubi", "Casual, Lower middle pitch"),
    GeminiVoice("Vindemiatrix", "Vindemiatrix", "Gentle, Middle pitch"),
    GeminiVoice("Sadachbia", "Sadachbia", "Lively, Lower pitch"),
    GeminiVoice("Sadaltager", "Sadaltager", "Lively, Lower pitch"),
    GeminiVoice("Sulafat", "Sulafat", "Warn, Middle pitch"),
)

internal fun normalizeTtsSpeakerId(speakerId: String?): String {
    val cleanSpeakerId = speakerId?.takeIf { it.isNotBlank() } ?: return DEFAULT_SPEAKER_ID
    return cleanSpeakerId.takeIf { id -> GEMINI_TTS_SPEAKERS.any { it.id == id } } ?: DEFAULT_SPEAKER_ID
}

internal fun saveTtsSpeaker(context: Context, speakerId: String) {
    val prefs = context.getSharedPreferences(TTS_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_SPEAKER_KEY, normalizeTtsSpeakerId(speakerId)) }
}

internal fun loadTtsSpeaker(context: Context): String {
    val prefs = context.getSharedPreferences(TTS_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return normalizeTtsSpeakerId(prefs.getString(TTS_SPEAKER_KEY, DEFAULT_SPEAKER_ID))
}

data class TtsChapterCacheInfo(
    val chapterTitle: String,
    val chunkCount: Int,
    val totalChunks: Int?,
    val sizeBytes: Long,
    val directory: File,
    val matchingFiles: List<File> = emptyList()
)

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %cB", bytes / 1024.0.pow(exp.toDouble()), pre)
}

class TtsCacheManager(private val context: Context) {
    private val baseDir: File
        get() = File(context.filesDir, "TTS_Cache")

    private fun safeCacheSegment(name: String, fallback: String): String {
        val normalized = name.trim().takeIf { it.isNotBlank() } ?: fallback
        val slug = normalized
            .replace(Regex("[^a-zA-Z0-9_-]+"), "_")
            .trim('_', '-')
            .ifBlank { fallback }
            .take(48)
        return "${slug}_${hash(normalized).take(16)}"
    }

    private fun sanitizeFileToken(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('.', '_', '-')
            .ifBlank { "default" }
    }

    private fun bookDirName(bookTitle: String): String = safeCacheSegment(bookTitle, "book")

    private fun chapterDirName(chapterTitle: String?): String {
        return safeCacheSegment(chapterTitle ?: "Unknown_Chapter", "chapter")
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun saveTotalChunks(bookTitle: String, chapterTitle: String?, totalChunks: Int) {
        val bookDir = getBookCacheDir(bookTitle)
        val chapterDir = File(bookDir, chapterDirName(chapterTitle))
        if (!chapterDir.exists()) chapterDir.mkdirs()
        val metaFile = File(chapterDir, "total_chunks.txt")
        metaFile.writeText(totalChunks.toString())
    }

    @OptIn(UnstableApi::class)
    fun getCacheFile(
        bookTitle: String,
        chapterTitle: String?,
        text: String,
        speakerId: String,
        mode: TtsPlaybackManager.TtsMode
    ): File {
        val bookDir = getBookCacheDir(bookTitle)
        val chapterDir = File(bookDir, chapterDirName(chapterTitle))
        if (!chapterDir.exists()) {
            chapterDir.mkdirs()
        }

        val hashParams = hash(text + speakerId + mode.name)
        val safeSpeaker = sanitizeFileToken(speakerId)

        return File(chapterDir, "cached_chunk_${safeSpeaker}_$hashParams.wav")
    }

    fun getBookCacheDir(bookTitle: String): File {
        return File(baseDir, bookDirName(bookTitle))
    }

    fun getChapterCaches(bookTitle: String, speakerFilter: String? = null): List<TtsChapterCacheInfo> {
        val bookDir = getBookCacheDir(bookTitle)
        if (!bookDir.exists()) return emptyList()

        return bookDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { chapterDir ->
            val files = chapterDir.listFiles()?.filter { file ->
                if (!file.isFile || !file.name.endsWith(".wav")) return@filter false
                if (speakerFilter == null || speakerFilter == "All") return@filter true

                val parts = file.name.split("_")

                val speakerInName = if (parts.size >= 5 && parts[2].all { it.isDigit() }) {
                    parts[3]
                } else if (parts.size >= 4) {
                    parts[2]
                } else null

                speakerInName == speakerFilter
            } ?: emptyList()

            if (files.isEmpty()) null
            else {
                val size = files.sumOf { it.length() }
                val metaFile = File(chapterDir, "total_chunks.txt")
                val total = if (metaFile.exists()) metaFile.readText().toIntOrNull() else null

                TtsChapterCacheInfo(
                    chapterTitle = chapterDir.name,
                    chunkCount = files.size,
                    totalChunks = total,
                    sizeBytes = size,
                    directory = chapterDir,
                    matchingFiles = files
                )
            }
        }?.sortedBy { it.chapterTitle } ?: emptyList()
    }

    fun deleteChapterCache(chapterDir: File) {
        if (chapterDir.isInsideBaseDir()) {
            chapterDir.deleteRecursively()
        }
    }

    fun deleteSpecificFiles(files: List<File>, chapterDir: File) {
        if (!chapterDir.isInsideBaseDir()) return
        files.forEach { file ->
            if (file.isInside(chapterDir)) {
                file.delete()
            }
        }
        if (chapterDir.listFiles()?.isEmpty() == true && chapterDir.isInsideBaseDir()) {
            chapterDir.deleteRecursively()
        }
    }

    fun clearBookCache(bookTitle: String) {
        getBookCacheDir(bookTitle).takeIf { it.isInsideBaseDir() }?.deleteRecursively()
    }

    private fun File.isInsideBaseDir(): Boolean {
        return isInside(baseDir)
    }

    private fun File.isInside(root: File): Boolean {
        val rootPath = runCatching { root.canonicalFile.path }.getOrNull() ?: return false
        val targetPath = runCatching { canonicalFile.path }.getOrNull() ?: return false
        return targetPath != rootPath && targetPath.startsWith(rootPath + File.separator)
    }
}

fun patchWavHeader(file: File, pcmDataLength: Int) {
    try {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.writeInt(Integer.reverseBytes(36 + pcmDataLength))
            raf.seek(40)
            raf.writeInt(Integer.reverseBytes(pcmDataLength))
        }
    } catch (e: Exception) {
        Timber.tag("TTS_CLOUD_DIAG").e(e, "Failed to patch WAV header for cached file")
    }
}

fun splitTextIntoChunks(text: String, maxLengthPerChunk: Int = TTS_CHUNK_MAX_LENGTH): List<String> {
    if (text.isBlank()) return emptyList()
    val sentenceBoundaryRegex = Regex("""(?<!\w\.\w.)(?<![A-Z][a-z]\.)(?<=[.?!\n])\s+""")
    val sentences = text.trim().split(sentenceBoundaryRegex).filter { it.isNotBlank() }

    if (sentences.isEmpty()) return emptyList()

    val chunks = mutableListOf<String>()
    val currentChunk = StringBuilder()

    for (sentence in sentences) {
        if (sentence.length > maxLengthPerChunk) {
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
                currentChunk.clear()
            }
            chunks.add(sentence)
            continue
        }

        if (currentChunk.isNotEmpty() && currentChunk.length + sentence.length + 1 > maxLengthPerChunk) {
            chunks.add(currentChunk.toString())
            currentChunk.clear()
            currentChunk.append(sentence)
        } else {
            if (currentChunk.isNotEmpty()) {
                currentChunk.append(" ")
            }
            currentChunk.append(sentence)
        }
    }
    if (currentChunk.isNotEmpty()) {
        chunks.add(currentChunk.toString())
    }
    return chunks
}

@UnstableApi
class SpeakerSamplePlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getAuthToken: suspend () -> String?
) {
    private val sampleMediaPlayer = MediaPlayer()
    var loadingSpeakerId by mutableStateOf<String?>(null)
    var playingSpeakerId by mutableStateOf<String?>(null)

    val cachedSpeakers = androidx.compose.runtime.mutableStateListOf<String>()

    private val httpClient = okhttp3.OkHttpClient()
    @OptIn(UnstableApi::class)
    private val liveClient = TtsService.GeminiLiveClient(httpClient)

    init {
        // Read initially existing files
        scope.launch(Dispatchers.IO) {
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("sample_") && name.endsWith(".wav") }
            val ids = files?.map { it.name.removePrefix("sample_").removeSuffix(".wav") } ?: emptyList()
            withContext(Dispatchers.Main) {
                cachedSpeakers.addAll(ids)
            }
        }

        sampleMediaPlayer.setOnErrorListener { mp, what, extra ->
            Timber.e("MediaPlayer error: what=$what, extra=$extra. Resetting.")
            playingSpeakerId = null
            loadingSpeakerId = null
            try { mp.reset() } catch (_: Exception) {}
            true
        }
    }

    fun playOrStop(speakerId: String) {
        scope.launch {
            liveClient.close()
            when {
                playingSpeakerId == speakerId -> {
                    sampleMediaPlayer.stop()
                    sampleMediaPlayer.reset()
                    playingSpeakerId = null
                }
                loadingSpeakerId == speakerId -> {
                    loadingSpeakerId = null
                }
                else -> {
                    playSample(speakerId)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun playSample(speakerId: String) {
        if (sampleMediaPlayer.isPlaying) sampleMediaPlayer.stop()
        sampleMediaPlayer.reset()
        loadingSpeakerId = speakerId
        playingSpeakerId = null

        withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "sample_$speakerId.wav")
            try {
                if (!cacheFile.exists()) {
                    val bucketName = "reader-9fc469d7.firebasestorage.app"
                    val sampleUrl = "https://firebasestorage.googleapis.com/v0/b/$bucketName/o/samples%2Fsample_${speakerId}.wav?alt=media"

                    val request = okhttp3.Request.Builder()
                        .url(sampleUrl)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        Timber.e("Failed to download sample for $speakerId. HTTP ${response.code}")
                        throw Exception("Failed to cache sample")
                    }
                }

                if (cacheFile.exists()) {
                    withContext(Dispatchers.Main) {
                        if (!cachedSpeakers.contains(speakerId)) cachedSpeakers.add(speakerId)
                        if (loadingSpeakerId != speakerId) return@withContext
                        sampleMediaPlayer.setDataSource(cacheFile.absolutePath)
                        sampleMediaPlayer.setOnPreparedListener { mp ->
                            if (loadingSpeakerId == speakerId) {
                                mp.start()
                                playingSpeakerId = speakerId
                                loadingSpeakerId = null
                            }
                        }
                        sampleMediaPlayer.setOnCompletionListener {
                            if (playingSpeakerId == speakerId) playingSpeakerId = null
                        }
                        sampleMediaPlayer.prepareAsync()
                    }
                } else {
                    throw Exception("Sample file missing after download attempt")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception playing sample for $speakerId")
                withContext(Dispatchers.Main) { if (loadingSpeakerId == speakerId) loadingSpeakerId = null }
            }
        }
    }

    fun clearSamples() {
        scope.launch(Dispatchers.IO) {
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("sample_") && name.endsWith(".wav") }
            files?.forEach { it.delete() }
            withContext(Dispatchers.Main) {
                cachedSpeakers.clear()
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun release() {
        sampleMediaPlayer.release()
        liveClient.close()
    }
}

fun createWavHeaderUnknownLength(sampleRate: Int): ByteArray {
    val numChannels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * numChannels * bitsPerSample / 8
    val blockAlign = numChannels * bitsPerSample / 8

    val header = java.nio.ByteBuffer.allocate(44)
    header.order(java.nio.ByteOrder.LITTLE_ENDIAN)

    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(0x7FFFFFFF)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1.toShort())
    header.putShort(numChannels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign.toShort())
    header.putShort(bitsPerSample.toShort())
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(0x7FFFFFFF - 36)

    return header.array()
}
