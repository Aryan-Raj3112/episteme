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
import timber.log.Timber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aryan.reader.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.edit

const val googleCloudWorkerTtsUrl = BuildConfig.TTS_WORKER_URL

const val TTS_SAMPLE_TEXT = "The greater danger for most of us lies not in setting our aim too high and falling short; but in setting our aim too low, and achieving our mark."
const val TTS_CHUNK_MAX_LENGTH = 250
const val DEFAULT_SPEAKER_ID = "Aoede"

val GEMINI_TTS_SPEAKERS = listOf(
    "Aoede" to "Aoede",
    "Charon" to "Charon",
    "Fenrir" to "Fenrir",
    "Kore" to "Kore",
    "Puck" to "Puck"
)

class TtsCacheManager(private val context: Context) {
    val cacheDir = java.io.File(context.cacheDir, "tts_audio_cache").apply { if (!exists()) mkdirs() }

    private fun getParamsHash(params: Map<String, String>): String {
        val paramsStr = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return hashString(paramsStr)
    }

    fun getCachedFile(bookTitle: String, text: String, params: Map<String, String>): java.io.File? {
        val paramsHash = getParamsHash(params)
        val textHash = hashString(text)
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val file = java.io.File(cacheDir, "${safeTitle}__${paramsHash}__${textHash}.wav")

        val oldFile = java.io.File(cacheDir, "${hashString(text + params["speaker"])}.wav")

        if (file.exists() && file.length() > 0) return file
        if (oldFile.exists() && oldFile.length() > 0) return oldFile

        return null
    }

    fun saveToCache(bookTitle: String, text: String, params: Map<String, String>, audioBytes: ByteArray): java.io.File {
        val paramsHash = getParamsHash(params)
        val textHash = hashString(text)
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val file = java.io.File(cacheDir, "${safeTitle}__${paramsHash}__${textHash}.wav")
        file.writeBytes(audioBytes)
        Timber.tag("TTS_CLOUD_DIAG").d("Saved to cache: $safeTitle, size=${audioBytes.size} bytes")
        return file
    }

    data class CacheGroup(val bookTitle: String, val sizeBytes: Long, val fileCount: Int)

    fun getCacheGroups(): List<CacheGroup> {
        val groups = mutableMapOf<String, MutableList<java.io.File>>()
        cacheDir.listFiles()?.filter { it.isFile && it.extension == "wav" }?.forEach { file ->
            val parts = file.nameWithoutExtension.split("__")
            val bookTitle = if (parts.size == 3) parts[0].replace("_", " ") else "Old Files / Samples"
            groups.getOrPut(bookTitle) { mutableListOf() }.add(file)
        }
        return groups.map { (title, files) ->
            CacheGroup(
                bookTitle = title,
                sizeBytes = files.sumOf { it.length() },
                fileCount = files.size
            )
        }.sortedByDescending { it.sizeBytes }
    }

    fun deleteGroup(bookTitle: String) {
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        cacheDir.listFiles()?.filter { it.isFile && it.extension == "wav" }?.forEach { file ->
            val parts = file.nameWithoutExtension.split("__")
            val fileBookTitle = if (parts.size == 3) parts[0] else "Old Files / Samples"
            if (fileBookTitle == safeTitle || fileBookTitle == bookTitle) {
                file.delete()
            }
        }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun hashString(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

fun loadTtsCacheEnabled(context: Context): Boolean {
    return context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).getBoolean("use_tts_cache", true)
}

fun saveTtsCacheEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit {
        putBoolean(
            "use_tts_cache",
            enabled
        )
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

class SpeakerSamplePlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getAuthToken: suspend () -> String?
) {
    private val sampleMediaPlayer = MediaPlayer()
    var loadingSpeakerId by mutableStateOf<String?>(null)
    var playingSpeakerId by mutableStateOf<String?>(null)

    init {
        sampleMediaPlayer.setOnErrorListener { mp, what, extra ->
            Timber.e("MediaPlayer error: what=$what, extra=$extra. Resetting.")
            playingSpeakerId = null
            loadingSpeakerId = null
            try {
                mp.reset()
            } catch (e: IllegalStateException) {
                Timber.e("Error resetting MediaPlayer: ${e.message}")
            }
            true
        }
    }

    @Suppress("unused")
    fun playOrStop(speakerId: String) {
        scope.launch {
            when {
                playingSpeakerId == speakerId -> {
                    sampleMediaPlayer.stop()
                    sampleMediaPlayer.reset()
                    playingSpeakerId = null
                }
                loadingSpeakerId == speakerId -> {
                    loadingSpeakerId = null
                }
                else -> playSample(speakerId)
            }
        }
    }

    private suspend fun playSample(speakerId: String) {
        if (sampleMediaPlayer.isPlaying) {
            sampleMediaPlayer.stop()
        }
        sampleMediaPlayer.reset()
        loadingSpeakerId = speakerId
        playingSpeakerId = null

        withContext(Dispatchers.IO) {
            val authToken = getAuthToken()
            Timber.tag("TTS_CLOUD_DIAG").d("SpeakerSamplePlayer: playSample for speaker=$speakerId. AuthToken present: ${!authToken.isNullOrBlank()}")
            try {
                val url = URL(googleCloudWorkerTtsUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")

                if (authToken != null) {
                    connection.setRequestProperty("Authorization", "Bearer $authToken")
                }

                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true
                connection.doInput = true

                val jsonPayload = JSONObject().apply {
                    put("text", TTS_SAMPLE_TEXT)
                    put("speaker", speakerId)
                }
                connection.outputStream.use { os ->
                    os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(responseBody)
                    val audioBase64 = jsonResponse.getString("audio_base64")
                    val mimeType = jsonResponse.optString("mime_type", "audio/pcm;rate=24000")
                    var audioBytes = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)

                    val isRawPcm = mimeType.contains("audio/pcm", ignoreCase = true) ||
                            mimeType.contains("audio/l16", ignoreCase = true) ||
                            mimeType.contains("audio/raw", ignoreCase = true)

                    if (isRawPcm) {
                        var sampleRate = 24000
                        val rateRegex = Regex("rate=(\\d+)")
                        val match = rateRegex.find(mimeType)
                        if (match != null) {
                            sampleRate = match.groupValues[1].toInt()
                        }
                        audioBytes = addWavHeader(audioBytes, sampleRate)
                    }

                    val tempFile = java.io.File.createTempFile("tts_sample_", ".wav", context.cacheDir).apply {
                        writeBytes(audioBytes)
                    }

                    withContext(Dispatchers.Main) {
                        if (loadingSpeakerId != speakerId) return@withContext
                        sampleMediaPlayer.setDataSource(tempFile.absolutePath)
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
                    val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { "Could not read error stream" }
                    Timber.tag("TTS_CLOUD_DIAG").e("Failed to fetch sample for $speakerId. Code: ${connection.responseCode}, Body: $errorBody")
                    withContext(Dispatchers.Main) { if (loadingSpeakerId == speakerId) loadingSpeakerId = null }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception playing sample for $speakerId: ${e.message}")
                withContext(Dispatchers.Main) { if (loadingSpeakerId == speakerId) loadingSpeakerId = null }
            }
        }
    }

    fun release() {
        sampleMediaPlayer.release()
    }
}

fun addWavHeader(pcmData: ByteArray, sampleRate: Int): ByteArray {
    val numChannels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * numChannels * bitsPerSample / 8
    val blockAlign = numChannels * bitsPerSample / 8
    val dataLength = pcmData.size

    val header = java.nio.ByteBuffer.allocate(44)
    header.order(java.nio.ByteOrder.LITTLE_ENDIAN)

    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + dataLength)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // Subchunk1Size
    header.putShort(1.toShort()) // AudioFormat (PCM)
    header.putShort(numChannels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign.toShort())
    header.putShort(bitsPerSample.toShort())
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataLength)

    val combined = ByteArray(44 + dataLength)
    System.arraycopy(header.array(), 0, combined, 0, 44)
    System.arraycopy(pcmData, 0, combined, 44, dataLength)

    return combined
}