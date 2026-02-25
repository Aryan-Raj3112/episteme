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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import timber.log.Timber
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aryan.reader.tts.TtsPlaybackManager.TtsMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

data class WordTimingInfo(val word: String, val startTime: Double)
data class TtsAudioData(
    val audioFile: File?,
    val serverText: String?,
    val wordTimings: List<WordTimingInfo>?
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

    /**
     * Generic function to download TTS audio from a server endpoint.
     * This is used for both the self-hosted server and the Google Cloud worker.
     *
     * @param chunkToSpeak The text to synthesize.
     * @param speakerId The identifier for the voice.
     * @param serverUrl The base URL of the TTS server.
     * @param audioFileExtension The file extension for the temporary audio file (e.g., ".flac", ".mp3").
     * @return A pair containing the temporary audio file and the text chunk returned by the server, or null if it fails.
     */
    private suspend fun downloadFromTtsServer(
        chunkToSpeak: String,
        speakerId: String,
        serverUrl: String,
        audioFileExtension: String
    ): TtsAudioData {
        if (chunkToSpeak.isBlank()) {
            return TtsAudioData(null, null, null)
        }
        return withContext(Dispatchers.IO) {
            var tempAudioFile: File? = null
            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.doInput = true

                val jsonPayload = JSONObject()
                jsonPayload.put("text", chunkToSpeak)
                jsonPayload.put("speaker", speakerId)
                val jsonInputString = jsonPayload.toString()
                connection.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { "" }
                    Timber.e("TTS Server request failed with code: $responseCode for URL: $serverUrl. Body: $errorBody")
                    return@withContext TtsAudioData(null, null, null)
                }

                val responseBody =
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val jsonResponse = JSONObject(responseBody)
                if (jsonResponse.has("audio_base64") && jsonResponse.has("text_chunk")) {
                    val audioBase64 = jsonResponse.getString("audio_base64")
                    val serverTextChunk = jsonResponse.getString("text_chunk")
                    val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)

                    val wordTimings = mutableListOf<WordTimingInfo>()
                    if (jsonResponse.has("word_timings")) {
                        val timingsArray: JSONArray = jsonResponse.getJSONArray("word_timings")
                        for (i in 0 until timingsArray.length()) {
                            val timingObject = timingsArray.getJSONObject(i)
                            wordTimings.add(
                                WordTimingInfo(
                                    word = timingObject.getString("word"),
                                    startTime = timingObject.getDouble("startTime")
                                )
                            )
                        }
                    }

                    tempAudioFile = File.createTempFile(
                        "tts_audio_chunk_",
                        audioFileExtension,
                        applicationContext.cacheDir
                    )
                    FileOutputStream(tempAudioFile).use { output -> output.write(audioBytes) }
                    TtsAudioData(tempAudioFile, serverTextChunk, wordTimings)
                } else {
                    Timber.e("DownloadAudioChunk: 'audio_base64' or 'text_chunk' field missing."
                    )
                    TtsAudioData(null, null, null)
                }
            } catch (e: Exception) {
                Timber.e(e, "DownloadAudioChunk: TTS Request Exception: ${e.message}")
                tempAudioFile?.delete()
                TtsAudioData(null, null, null)
            }
        }
    }

    private val downloadAudioChunk: suspend (String, String) -> TtsAudioData =
        { chunkToSpeak, speakerId ->
            downloadFromTtsServer(
                chunkToSpeak,
                speakerId,
                googleCloudWorkerTtsUrl,
                ".mp3"
            )
        }

    private val synthesizeBaseTtsChunk: suspend (String) -> TtsAudioData =
        { chunkToSpeak ->
            val (file, text) = baseTtsSynthesizer.synthesizeToFile(chunkToSpeak)
            TtsAudioData(file, text, null)
        }

    private val audioGenerator: suspend (text: String, speaker: String, mode: TtsMode) -> TtsAudioData =
        { text, speaker, mode ->
            when (mode) {
                TtsMode.CLOUD -> downloadAudioChunk(text, speaker)
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