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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aryan.reader.tts.TtsPlaybackManager.TtsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class WordTimingInfo(val word: String, val startTime: Double)
data class TtsAudioData(
    val audioFile: File?,
    val serverText: String?,
    val wordTimings: List<WordTimingInfo>?,
    val error: String? = null
)

data class PageCharacterRange(
    val pageInChapter: Int,
    val cfi: String,
    val startOffset: Int,
    val endOffset: Int
)

@UnstableApi
class TtsService : MediaSessionService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var playbackManager: TtsPlaybackManager
    private lateinit var baseTtsSynthesizer: BaseTtsSynthesizer

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

            if (startInForegroundRequired) {
                stopSelf()
            }

            return
        }

        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private suspend fun downloadFromTtsServer(
        chunkToSpeak: String,
        speakerId: String,
        serverUrl: String,
        audioFileExtension: String,
        authToken: String?
    ): TtsAudioData {
        if (chunkToSpeak.isBlank()) {
            return TtsAudioData(null, null, null)
        }

        Timber.tag("TTS_CLOUD_DIAG").d("downloadFromTtsServer Start: speaker=$speakerId, textLen=${chunkToSpeak.length}, tokenPresent=${!authToken.isNullOrBlank()}")

        val useCache = loadTtsCacheEnabled(applicationContext)
        val cacheManager = TtsCacheManager(applicationContext)

        if (useCache) {
            val cachedFile = cacheManager.getCachedFile(chunkToSpeak, speakerId)
            if (cachedFile != null) {
                Timber.tag("TTS_CLOUD_DIAG").d("Cache HIT for speaker: $speakerId")
                return TtsAudioData(cachedFile, chunkToSpeak, emptyList())
            }
        }

        Timber.tag("TTS_CLOUD_DIAG").d("Cache MISS for speaker: $speakerId. Fetching from network.")

        return withContext(Dispatchers.IO) {
            var tempAudioFile: File? = null
            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                // ... (Keep existing headers/setup)
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                if (authToken != null) connection.setRequestProperty("Authorization", "Bearer $authToken")
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.doInput = true

                val jsonPayload = JSONObject().apply {
                    put("text", chunkToSpeak)
                    put("speaker", speakerId)
                }
                connection.outputStream.use { os ->
                    val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                Timber.tag("TTS_CLOUD_DIAG").d("Network response code: $responseCode")

                if (responseCode == 402) {
                    Timber.tag("TTS_CLOUD_DIAG").w("TTS Server: Insufficient Credits")
                    return@withContext TtsAudioData(null, null, null, "INSUFFICIENT_CREDITS")
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { "Could not read error stream" }
                    Timber.tag("TTS_CLOUD_DIAG").e("HTTP Error $responseCode: $errorBody")
                    return@withContext TtsAudioData(null, null, null)
                }

                val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Timber.tag("TTS_CLOUD_DIAG").d("Received successful response, body length: ${responseBody.length}")

                val jsonResponse = JSONObject(responseBody)

                if (jsonResponse.has("audio_base64") && jsonResponse.has("text_chunk")) {
                    val audioBase64 = jsonResponse.getString("audio_base64")
                    val serverTextChunk = jsonResponse.getString("text_chunk")
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

                    if (useCache) {
                        tempAudioFile = cacheManager.saveToCache(chunkToSpeak, speakerId, audioBytes)
                    } else {
                        tempAudioFile = File.createTempFile("tts_audio_chunk_", ".wav", applicationContext.cacheDir)
                        FileOutputStream(tempAudioFile).use { it.write(audioBytes) }
                    }

                    TtsAudioData(tempAudioFile, serverTextChunk, emptyList())
                } else {
                    Timber.tag("TTS_CLOUD_DIAG").e("Response JSON missing expected keys.")
                    TtsAudioData(null, null, null)
                }
            } catch (e: Exception) {
                Timber.tag("TTS_CLOUD_DIAG").e(e, "DownloadAudioChunk Exception: ${e.message}")
                tempAudioFile?.delete()
                TtsAudioData(null, null, null)
            }
        }
    }

    private val downloadAudioChunk: suspend (String, String, String?) -> TtsAudioData =
        { chunkToSpeak, speakerId, authToken ->
            downloadFromTtsServer(chunkToSpeak, speakerId, googleCloudWorkerTtsUrl, ".mp3", authToken)
        }

    private val synthesizeBaseTtsChunk: suspend (String) -> TtsAudioData =
        { chunkToSpeak ->
            val (file, text) = baseTtsSynthesizer.synthesizeToFile(chunkToSpeak)
            TtsAudioData(file, text, null)
        }

    private val audioGenerator: suspend (text: String, speaker: String, mode: TtsMode, authToken: String?) -> TtsAudioData =
        { text, speaker, mode, authToken ->
            when (mode) {
                TtsMode.CLOUD -> downloadAudioChunk(text, speaker, authToken)
                TtsMode.BASE -> synthesizeBaseTtsChunk(text)
            }
        }

    override fun onCreate() {
        super.onCreate()
        Timber.d("TtsService created.")

        baseTtsSynthesizer = BaseTtsSynthesizer(this)
        scope.launch {
            try {
                baseTtsSynthesizer.initialize()
            } catch (e: Exception) {
                Timber.e(e, "Base TTS synthesizer failed to initialize")
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        playbackManager = TtsPlaybackManager(
            player = player,
            generateAudioChunk = audioGenerator
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(playbackManager)
            .build()

        mediaSession?.let { playbackManager.setMediaSession(it) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) {
            stopSelf()
        }
        Timber.d("onTaskRemoved called, stopping service.")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        Timber.d("TtsService is being destroyed.")
        baseTtsSynthesizer.shutdown()
        playbackManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}