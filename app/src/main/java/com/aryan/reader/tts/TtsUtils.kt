/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.tts

import android.content.Context
import android.media.MediaPlayer
import timber.log.Timber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val googleCloudWorkerTtsUrl = ""

const val TTS_SAMPLE_TEXT = "The greater danger for most of us lies not in setting our aim too high and falling short; but in setting our aim too low, and achieving our mark."

const val TTS_CHUNK_MAX_LENGTH = 250

const val DEFAULT_SPEAKER_ID = "en-US-Standard-F"

@Suppress("unused")
val GOOGLE_TTS_SPEAKERS = listOf(
    "US Female: F" to "en-US-Standard-F",
    "US Female: H" to "en-US-Standard-H",
    "US Male: I" to "en-US-Standard-I",
    "US Male: J" to "en-US-Standard-J"
)

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
    private val scope: CoroutineScope
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
            try {
                val url = URL(googleCloudWorkerTtsUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
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
                    val audioBase64 = JSONObject(responseBody).getString("audio_base64")

                    val dataUri = "data:audio/mpeg;base64,$audioBase64"

                    withContext(Dispatchers.Main) {
                        if (loadingSpeakerId != speakerId) {
                            return@withContext
                        }
                        sampleMediaPlayer.setDataSource(context, dataUri.toUri())
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
                    Timber.e("Failed to fetch sample for $speakerId. Code: ${connection.responseCode}")
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