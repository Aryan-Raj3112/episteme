@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.io.encoding.ExperimentalEncodingApi::class,
)

package com.aryan.reader.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.CloudTtsPlaybackMonitorPolicy
import com.aryan.reader.shared.CloudTtsBackend
import com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.GEMINI_TTS_MODEL_LITE
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderTtsCacheChapter
import com.aryan.reader.shared.ReaderTtsCacheSummary
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderVoiceSampleState
import com.aryan.reader.shared.readerTtsCacheDisplayLabel
import com.aryan.reader.shared.readerTtsCacheSpeakerId
import com.aryan.reader.shared.LocalTtsInterruptionAction
import com.aryan.reader.shared.LocalTtsInterruptionEvent
import com.aryan.reader.shared.LocalTtsInterruptionState
import com.aryan.reader.shared.FISH_TTS_MODEL as FISH_TTS_MODEL_DEFAULT
import com.aryan.reader.shared.FISH_TTS_MODEL_ID
import com.aryan.reader.shared.formatMicrosUsd
import com.aryan.reader.shared.formatSpendGuardCountdown
import com.aryan.reader.shared.hasSpendableBalance
import com.aryan.reader.shared.parseSpendGuardError
import com.aryan.reader.shared.parseSpendGuardSentinel
import com.aryan.reader.shared.spendGuardSentinel
import com.aryan.reader.shared.ios.IOS_TTS_WORKER_URL
import com.aryan.reader.shared.ios.IosReaderAiHttpClient
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.resolveCloudTtsBackend
import com.aryan.reader.shared.sha256
import com.aryan.reader.shared.ios.IosTtsAudioInterruption
import com.aryan.reader.shared.ios.IosTtsAudioInterruptionMonitor
import com.aryan.reader.shared.ios.IosTtsAudioSessionTeardown
import com.aryan.reader.shared.ios.iosCloudTtsTraceLog
import com.aryan.reader.shared.ios.iosTtsStartLog
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSRange
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionWebSocketCloseCodeNormalClosure
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.dataWithLength
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.darwin.NSObject

/**
 * iOS implementation of the shared cloud reader boundary.
 *
 * The worker and direct Gemini paths use the same Gemini Live protocol as
 * Android. Audio is accumulated as PCM, written atomically as a WAV cache
 * entry, and then handed to AVAudioPlayer. Network and file work stays off
 * the UI thread; cancellation closes the active generation without leaking a
 * WebSocket or player.
 */
internal class IosSharedMobileCloudTts : SharedMobileEpubCloudTts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val generationMutex = Mutex()
    private val fileManager = NSFileManager.defaultManager
    private val cacheRoot: String = iosCloudTtsCacheRoot()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val audioDelegate = IosCloudAudioDelegate(
        onFinished = { callbackPlayer, success -> onAudioFinished(callbackPlayer, success) },
        onDecodeError = { callbackPlayer -> onAudioDecodeError(callbackPlayer) },
    )
    private val interruptionMonitor = IosTtsAudioInterruptionMonitor(::handleAudioInterruption)

    override var state by mutableStateOf(ReaderCloudTtsState())
        private set

    private var settings = ReaderAiByokSettings()
    private var isSignedIn = false
    private var isProUser = false
    private var credits = 0
    private var walletMicros = 0L
    private var walletMigrated = false
    private var sessionSpendMicros = 0L
    private var rateLimitRetriesLeft = 0
    private var authToken: String? = null
    private var workerUrl = ""
    private var chunks: List<ReaderTtsChunk> = emptyList()
    private var bookTitle = ""
    // Observable because audiobook Listen projects its whole UI state out of these, and reads
    // them through a `SharedTtsPlaybackSnapshot`. `currentChunkIndex` is deliberately a plain
    // var: it changes on the order of once per sentence, and progress (which is observable)
    // already drives recomposition.
    private var activePlaybackSource by mutableStateOf<String?>(null)
    private var bookId by mutableStateOf<String?>(null)
    private var activeTotalChapters by mutableStateOf(0)
    private var currentChunkIndex = -1
    private var sessionId = 0L
    private var wantsPlayback = true
    private var interruptionState = LocalTtsInterruptionState()
    private var playbackContinuation: CompletableDeferred<Boolean>? = null
    private var player: AVAudioPlayer? = null
    private var playJob: Job? = null
    // Android parity (TtsPlaybackManager.prefetchNextChunkAudio): the chunk
    // after the one playing is fetched while it plays, so the hand-off has
    // audio ready instead of a network round-trip of silence.
    private var prefetchJob: Job? = null
    private val prefetchedAudio = mutableMapOf<Int, ByteArray>()
    // Start-session stopwatch for ReaderTtsStart diagnostics: set at start(),
    // consumed at the first player.play(), cleared at stop().
    private var ttsStartMark: TimeMark? = null
    private var websocketSession: NSURLSession? = null
    private var websocket: NSURLSessionWebSocketTask? = null
    private var setupReady = CompletableDeferred<Boolean>().apply { complete(false) }
    private var events = Channel<IosGeminiWsEvent>(Channel.UNLIMITED)
    private var receiving = false

    init {
        fileManager.createDirectoryAtPath(
            cacheRoot,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        refreshCacheSummary()
        seedCachedVoiceSamples()
    }

    override fun configure(
        settings: ReaderAiByokSettings,
        isSignedIn: Boolean,
        isProUser: Boolean,
        credits: Int,
        authToken: String?,
        workerUrl: String,
        walletMicros: Long,
        walletMigrated: Boolean,
    ) {
        val sanitized = settings.sanitized()
        val speakerChanged = this.settings.ttsSpeakerId != sanitized.ttsSpeakerId
        val modeChanged = this.settings.ttsModel != sanitized.ttsModel
        val accessChanged = this.isSignedIn != isSignedIn || this.authToken != authToken || this.workerUrl != workerUrl
        this.settings = sanitized
        this.isSignedIn = isSignedIn
        this.isProUser = isProUser
        this.credits = credits.coerceAtLeast(0)
        this.walletMicros = walletMicros.coerceAtLeast(0L)
        this.walletMigrated = walletMigrated
        this.authToken = authToken
        this.workerUrl = workerUrl.trim()
        if (speakerChanged || modeChanged || accessChanged) {
            // Never keep a session authenticated with an old account/token or
            // speaking with a voice that no longer matches the selected mode.
            val hadActiveSession = state.isPlaying || state.isLoading || state.isPaused
            iosCloudTtsTraceLog(
                "cloud.configure",
                "speakerChanged=$speakerChanged modeChanged=$modeChanged accessChanged=$accessChanged hadActiveSession=$hadActiveSession"
            )
            if (hadActiveSession) stop()
            closeWebSocket()
        }
        state = state.copy(
            isAvailable = cloudTtsModeEnabled() && (byokAvailable() || fishByokAvailable() || workerAvailable()),
            errorMessage = null,
            cacheSummary = state.cacheSummary,
        )
        refreshCacheSummary()
    }

    override fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String?,
        startChunkIndex: Int,
        playWhenReady: Boolean,
        continueSession: Boolean,
        playbackSource: String?,
        totalChapters: Int,
    ) {
        val readable = chunks.filter { it.spokenText.isNotBlank() }
        if (readable.isEmpty()) {
            iosTtsStartLog("cloud.start empty", "chunks=${chunks.size}")
            return
        }
        val gateError = startGateError()
        if (gateError != null) {
            iosTtsStartLog("cloud.start gated", "error=$gateError")
            state = state.copy(
                isAvailable = cloudTtsModeEnabled() && (byokAvailable() || fishByokAvailable() || workerAvailable()),
                isPlaying = false,
                isLoading = false,
                isPaused = false,
                errorMessage = gateError,
            )
            return
        }
        stop(clearError = false)
        this.chunks = readable
        this.bookTitle = bookTitle
        this.bookId = bookId
        this.activePlaybackSource = playbackSource
        this.activeTotalChapters = totalChapters
        this.currentChunkIndex = startChunkIndex.coerceIn(0, readable.lastIndex)
        this.sessionId += 1
        this.wantsPlayback = playWhenReady
        // Fresh listen keeps its own spend line (Android benchmark parity);
        // chained chapters keep accruing into the same session spend.
        if (!continueSession) {
            this.sessionSpendMicros = 0L
        }
        this.rateLimitRetriesLeft = 1
        val requestedSession = sessionId
        ttsStartMark = TimeSource.Monotonic.markNow()
        iosTtsStartLog(
            "cloud.start",
            "chunks=${readable.size} startIndex=$currentChunkIndex playWhenReady=$playWhenReady " +
                "fishRest=${useFishRest()} worker=${workerAvailable()} byok=${byokAvailable() || fishByokAvailable()}"
        )
        iosCloudTtsTraceLog(
            "cloud.start",
            "session=$requestedSession chunks=${readable.size} startIndex=$currentChunkIndex " +
                "playWhenReady=$playWhenReady continued=$continueSession backend=${cloudBackendName()}"
        )
        state = state.copy(
            isAvailable = true,
            isPlaying = false,
            isLoading = true,
            isPaused = false,
            errorMessage = null,
            progress = ReaderTtsProgress(
                sessionId = requestedSession,
                chunks = readable,
                currentChunkIndex = currentChunkIndex,
            ),
        )
        playJob = scope.launch { playChunks(requestedSession) }
    }

    override val playbackSource: String? get() = activePlaybackSource
    override val sessionBookId: String? get() = bookId
    override val sessionTotalChapters: Int get() = activeTotalChapters

    override fun pause() {
        if (!hasActiveSession()) return
        interruptionState = LocalTtsInterruptionState()
        pauseInternal()
    }

    private fun pauseInternal() {
        if (!hasActiveSession()) return
        wantsPlayback = false
        player?.pause()
        iosCloudTtsTraceLog("cloud.pause", "index=$currentChunkIndex playerPresent=${player != null}")
        state = state.copy(isPlaying = false, isPaused = true, isLoading = state.isLoading)
    }

    override fun resume() {
        if (!hasActiveSession()) return
        interruptionState = LocalTtsInterruptionState()
        resumeInternal()
    }

    private fun resumeInternal() {
        if (!hasActiveSession()) return
        ensureAudioSession()
        wantsPlayback = true
        player?.play()
        iosCloudTtsTraceLog(
            "cloud.resume",
            "index=$currentChunkIndex playerPresent=${player != null} pos=${player?.currentTime ?: -1.0} dur=${player?.duration ?: -1.0}"
        )
        state = state.copy(isPlaying = player != null, isPaused = player == null && !state.isLoading)
    }

    override fun skipPrevious() {
        if (chunks.isEmpty()) return
        restartAt((currentChunkIndex - 1).coerceAtLeast(0))
    }

    override fun skipNext() {
        if (chunks.isEmpty()) return
        restartAt((currentChunkIndex + 1).coerceAtMost(chunks.lastIndex))
    }

    override fun setVoice(identifier: String) {
        if (identifier.isBlank()) return
        settings = settings.copy(ttsSpeakerId = identifier).sanitized()
        refreshCacheSummary()
    }

    override fun clearCache() {
        stop()
        scope.launch(Dispatchers.Default) {
            generationMutex.withLock {
                // Android parity: chunk clearing never touches voice samples
                // (SpeakerSamplePlayer.clearSamples is a separate action).
                fileManager.contentsOfDirectoryAtPath(cacheRoot, error = null).orEmpty()
                    .mapNotNull { it as? String }
                    .filter { !(it.startsWith("voice_sample_") && it.endsWith(".wav")) }
                    .forEach { fileManager.removeItemAtPath("$cacheRoot/$it", error = null) }
            }
            refreshCacheSummary()
        }
    }

    // Android `TtsCacheTab` parity: per-chapter inventory over the same
    // book/chapter/speaker file layout.
    override fun cachedChapterVoices(): List<String> {
        return scanCacheEntries().map { it.speakerId }.distinct().sorted()
    }

    override fun cachedChapters(voiceId: String): List<ReaderTtsCacheChapter> {
        return scanCacheEntries()
            .filter { it.speakerId == safeSpeakerId(voiceId) }
            .groupBy { it.bookDir to it.chapterDir }
            .map { (dirs, files) ->
                ReaderTtsCacheChapter(
                    bookTitle = readerTtsCacheDisplayLabel(dirs.first),
                    chapterTitle = readerTtsCacheDisplayLabel(dirs.second),
                    voiceId = voiceId,
                    chunkCount = files.size,
                    sizeBytes = files.sumOf { it.sizeBytes },
                    entryKey = "${dirs.first}/${dirs.second}",
                )
            }
            .sortedBy { it.chapterTitle.lowercase() }
    }

    override fun deleteCachedChapter(chapter: ReaderTtsCacheChapter) {
        if (chapter.entryKey.isBlank()) return
        stop()
        scope.launch(Dispatchers.Default) {
            generationMutex.withLock {
                fileManager.removeItemAtPath("$cacheRoot/${chapter.entryKey}", error = null)
            }
            refreshCacheSummary()
        }
    }

    override fun deleteCachedVoice(voiceId: String) {
        stop()
        val safeSpeaker = safeSpeakerId(voiceId)
        scope.launch(Dispatchers.Default) {
            generationMutex.withLock {
                scanCacheEntries()
                    .filter { it.speakerId == safeSpeaker }
                    .forEach { entry ->
                        fileManager.removeItemAtPath("$cacheRoot/${entry.bookDir}/${entry.chapterDir}/${entry.fileName}", error = null)
                    }
            }
            refreshCacheSummary()
        }
    }

    private data class IosCacheEntry(
        val bookDir: String,
        val chapterDir: String,
        val fileName: String,
        val speakerId: String,
        val sizeBytes: Long,
    )

    private fun scanCacheEntries(): List<IosCacheEntry> {
        val found = mutableListOf<IosCacheEntry>()
        val books = fileManager.contentsOfDirectoryAtPath(cacheRoot, error = null).orEmpty()
        books.mapNotNull { it as? String }.forEach { bookDir ->
            val bookPath = "$cacheRoot/$bookDir"
            fileManager.contentsOfDirectoryAtPath(bookPath, error = null).orEmpty()
                .mapNotNull { it as? String }.forEach { chapterDir ->
                    val chapterPath = "$bookPath/$chapterDir"
                    fileManager.contentsOfDirectoryAtPath(chapterPath, error = null).orEmpty()
                        .mapNotNull { it as? String }
                        .filter { it.endsWith(".wav") && !it.startsWith("voice_sample_") }
                        .forEach { fileName ->
                            val speaker = readerTtsCacheSpeakerId(fileName) ?: return@forEach
                            val attrs = fileManager.attributesOfItemAtPath("$chapterPath/$fileName", error = null).orEmpty()
                            found += IosCacheEntry(
                                bookDir = bookDir,
                                chapterDir = chapterDir,
                                fileName = fileName,
                                speakerId = speaker,
                                sizeBytes = (attrs["NSFileSize"] as? Number)?.toLong().orZero(),
                            )
                        }
                }
        }
        return found
    }

    private fun safeSpeakerId(voiceId: String): String =
        voiceId.replace(Regex("[^A-Za-z0-9._-]+"), "_")

    // Android `SpeakerSamplePlayer` parity: static per-voice sample wavs from
    // the same Firebase bucket, downloaded once and cached beside the chunks.
    override var voiceSampleState by mutableStateOf(ReaderVoiceSampleState())
        private set

    private var samplePlayer: AVAudioPlayer? = null
    private var sampleJob: Job? = null
    private val sampleDelegate = IosCloudAudioDelegate(
        onFinished = { callbackPlayer, _ -> onSampleFinished(callbackPlayer) },
        onDecodeError = { callbackPlayer -> onSampleFinished(callbackPlayer) },
    )

    private fun sampleFile(voiceId: String): String =
        "$cacheRoot/voice_sample_${safeSpeakerId(voiceId)}.wav"

    override fun playOrStopVoiceSample(
        voiceId: String,
        fishReferenceId: String?,
        sampleAudioUrl: String?,
        sampleText: String?,
    ) {
        if (voiceSampleState.playingVoiceId == voiceId) {
            samplePlayer?.stop()
            samplePlayer = null
            voiceSampleState = voiceSampleState.copy(playingVoiceId = null)
            return
        }
        if (voiceSampleState.loadingVoiceId == voiceId) {
            sampleJob?.cancel()
            sampleJob = null
            voiceSampleState = voiceSampleState.copy(loadingVoiceId = null)
            return
        }
        sampleJob?.cancel()
        samplePlayer?.stop()
        samplePlayer = null
        voiceSampleState = voiceSampleState.copy(loadingVoiceId = voiceId, playingVoiceId = null)
        sampleJob = scope.launch(Dispatchers.Default) {
            val file = try {
                if (!fishReferenceId.isNullOrBlank()) {
                    resolveFishSampleFile(voiceId, fishReferenceId, sampleAudioUrl, sampleText)
                } else {
                    resolveGeminiSampleFile(voiceId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // A failed preview must never wedge the loading spinner or the
                // sheet; Android's SpeakerSamplePlayer clears loading on error.
                null
            }
            if (file == null) {
                withContext(Dispatchers.Main.immediate) {
                    if (voiceSampleState.loadingVoiceId == voiceId) {
                        voiceSampleState = voiceSampleState.copy(loadingVoiceId = null)
                    }
                }
                return@launch
            }
            val audio = readFile(file)
            withContext(Dispatchers.Main.immediate) {
                if (voiceSampleState.loadingVoiceId != voiceId || audio == null) {
                    if (voiceSampleState.loadingVoiceId == voiceId) {
                        voiceSampleState = voiceSampleState.copy(loadingVoiceId = null)
                    }
                    return@withContext
                }
                val created = AVAudioPlayer(data = audio.toNSData(), error = null)
                if (!created.prepareToPlay()) {
                    voiceSampleState = voiceSampleState.copy(loadingVoiceId = null)
                    return@withContext
                }
                ensureAudioSession()
                samplePlayer = created
                created.delegate = sampleDelegate
                created.play()
                voiceSampleState = voiceSampleState.copy(
                    loadingVoiceId = null,
                    playingVoiceId = voiceId,
                    cachedVoiceIds = voiceSampleState.cachedVoiceIds + voiceId,
                )
            }
        }
    }

    private suspend fun resolveGeminiSampleFile(voiceId: String): String? {
        val file = sampleFile(voiceId)
        if (fileManager.fileExistsAtPath(file)) return file
        val url = NSURL(string = "https://firebasestorage.googleapis.com/v0/b/reader-9fc469d7.firebasestorage.app/o/samples%2Fsample_${voiceId}.wav?alt=media")
        return if (downloadUrlToFile(url, file)) file else null
    }

    /**
     * Android `SpeakerSamplePlayer.playFishSample` parity. Priority: cached
     * synthesis → free catalog static MP3 (unbilled) → on-demand synthesis
     * (BYOK direct, else the credited `/v2/tts/sample` worker route).
     * Failures stay silent — a preview must never pollute overlay errors.
     */
    private suspend fun resolveFishSampleFile(
        voiceId: String,
        referenceId: String,
        sampleAudioUrl: String?,
        sampleText: String?,
    ): String? {
        val safe = safeSpeakerId(referenceId)
        val synthFile = "$cacheRoot/voice_sample_fish_${safe}.mp3"
        if (fileManager.fileExistsAtPath(synthFile)) return synthFile
        if (!sampleAudioUrl.isNullOrBlank()) {
            val staticFile = "$cacheRoot/voice_sample_fish_static_${safe}.mp3"
            if (!fileManager.fileExistsAtPath(staticFile)) {
                downloadUrlToFile(NSURL(string = sampleAudioUrl), staticFile)
            }
            if (fileManager.fileExistsAtPath(staticFile)) return staticFile
        }
        val preview = synthesizeFishPreview(sampleText, referenceId) ?: return null
        writeFileAtomically(synthFile, preview)
        return synthFile
    }

    private suspend fun synthesizeFishPreview(text: String?, referenceId: String): ByteArray? {
        val previewText = text?.trim().orEmpty().ifBlank { SHARED_MOBILE_TTS_SAMPLE_DEFAULT }
        val url: String
        val headers: Map<String, String>
        val body: String
        if (settings.fishKey.isNotBlank()) {
            url = "https://api.fish.audio/v1/tts"
            headers = mapOf(
                "Authorization" to "Bearer ${settings.fishKey}",
                // The selected model drives the Fish `model` header, so the
                // free tier is actually used when it is picked.
                "model" to fishModelHeader(),
            )
            body = buildJsonObject {
                put("text", previewText)
                put("reference_id", referenceId)
                put("format", "mp3")
                put("normalize", true)
                put("latency", "normal")
                put("chunk_length", 300)
                put("condition_on_previous_chunks", true)
            }.toString()
        } else {
            if (!workerAvailable()) return null
            url = workerUrl.removeSuffix("/") + "/v2/tts/sample"
            headers = mapOf("Authorization" to "Bearer ${authToken.orEmpty()}")
            body = buildJsonObject {
                put("text", previewText)
                put("voiceId", referenceId)
                put("format", "mp3")
                put("latency", "balanced")
            }.toString()
        }
        return try {
            val response = withTimeout(CLOUD_TTS_TIMEOUT_MILLIS) {
                IosReaderAiHttpClient.postBytes(url, body, headers)
            }
            if (response.statusCode !in 200..299 || response.bodyBytes.size < 1024) null
            else response.bodyBytes
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            null
        }
    }

    override fun clearVoiceSamples() {
        samplePlayer?.stop()
        samplePlayer = null
        sampleJob?.cancel()
        sampleJob = null
        voiceSampleState = ReaderVoiceSampleState()
        scope.launch(Dispatchers.Default) {
            fileManager.contentsOfDirectoryAtPath(cacheRoot, error = null).orEmpty()
                .mapNotNull { it as? String }
                .filter {
                    it.startsWith("voice_sample_") && (it.endsWith(".wav") || it.endsWith(".mp3"))
                }
                .forEach { fileManager.removeItemAtPath("$cacheRoot/$it", error = null) }
        }
    }

    private fun onSampleFinished(callbackPlayer: AVAudioPlayer) {
        if (samplePlayer !== callbackPlayer) return
        samplePlayer = null
        voiceSampleState = voiceSampleState.copy(playingVoiceId = null)
    }

    private suspend fun downloadUrlToFile(url: NSURL, destination: String): Boolean {
        return try {
            // Bounded NSURLSession GET (Android OkHttp parity). The previous
            // NSData.dataWithContentsOfURL call had no timeout and no
            // cancellation, so an unreachable sample URL left the row spinner
            // spinning forever.
            val response = withTimeout(CLOUD_TTS_TIMEOUT_MILLIS) {
                IosReaderAiHttpClient.getBytes(url.absoluteString ?: return@withTimeout null)
            } ?: return false
            if (response.statusCode !in 200..299 || response.bodyBytes.isEmpty()) return false
            writeFileAtomically(destination, response.bodyBytes)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun seedCachedVoiceSamples() {
        scope.launch(Dispatchers.Default) {
            val cached = fileManager.contentsOfDirectoryAtPath(cacheRoot, error = null).orEmpty()
                .mapNotNull { it as? String }
                .filter { it.startsWith("voice_sample_") && (it.endsWith(".wav") || it.endsWith(".mp3")) }
                .map { it.removePrefix("voice_sample_").removeSuffix(".wav").removeSuffix(".mp3") }
                .toSet()
            if (cached.isNotEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    voiceSampleState = voiceSampleState.copy(cachedVoiceIds = cached)
                }
            }
        }
    }

    override fun stop() {
        stop(clearError = true)
    }

    private fun stop(clearError: Boolean) {
        iosTtsStartLog("cloud.stop")
        iosCloudTtsTraceLog("cloud.stop", "sessionWas=$sessionId hadChunks=${chunks.isNotEmpty()} index=$currentChunkIndex")
        ttsStartMark = null
        sessionId += 1
        playJob?.cancel()
        playJob = null
        cancelPrefetch()
        playbackContinuation?.cancel()
        playbackContinuation = null
        interruptionState = LocalTtsInterruptionState()
        player?.stop()
        player = null
        chunks = emptyList()
        currentChunkIndex = -1
        wantsPlayback = false
        // Clearing the surface tag is what lets another surface claim this engine next, and is
        // what makes the Listen projection report disconnected. Android clears `playbackSource`
        // in the same place.
        activePlaybackSource = null
        activeTotalChapters = 0
        if (clearError) state = state.copy(errorMessage = null)
        state = state.copy(
            isPlaying = false,
            isLoading = false,
            isPaused = false,
            progress = ReaderTtsProgress(),
        )
        closeWebSocket()
        deactivateAudioSession()
    }

    override fun release() {
        stop()
        samplePlayer?.stop()
        samplePlayer = null
        sampleJob?.cancel()
        sampleJob = null
        interruptionMonitor.close()
        scope.cancel()
        websocketSession?.invalidateAndCancel()
        websocketSession = null
    }

    private suspend fun playChunks(requestedSession: Long) {
        iosCloudTtsTraceLog(
            "cloud.loopStart",
            "session=$requestedSession chunks=${chunks.size} fromIndex=$currentChunkIndex"
        )
        try {
            while (scope.isActive && requestedSession == sessionId) {
                val chunk = chunks.getOrNull(currentChunkIndex)
                if (chunk == null) {
                    iosCloudTtsTraceLog("cloud.loopBreak", "session=$requestedSession reason=missing-chunk index=$currentChunkIndex")
                    break
                }
                iosCloudTtsTraceLog("cloud.loopIter", "session=$requestedSession index=$currentChunkIndex total=${chunks.size}")
                state = state.copy(
                    isAvailable = cloudTtsModeEnabled() && (byokAvailable() || fishByokAvailable() || workerAvailable()),
                    isLoading = true,
                    isPlaying = false,
                    isPaused = false,
                    progress = ReaderTtsProgress(
                        sessionId = requestedSession,
                        chunks = chunks,
                        currentChunkIndex = currentChunkIndex,
                    ),
                )
                val audio = try {
                    ttsStartMark?.let { mark ->
                        iosTtsStartLog("cloud.firstFetch", "chunkIndex=$currentChunkIndex", mark)
                    }
                    generationMutex.withLock {
                        loadOrGenerate(chunk, requestedSession)
                    }
                } catch (throttled: FishRateLimited) {
                    // Velocity throttle: wait out the window and retry this
                    // chunk once (Android benchmark parity). Anything further
                    // fails with the friendly countdown text.
                    if (requestedSession != sessionId || rateLimitRetriesLeft <= 0) throw throttled
                    rateLimitRetriesLeft -= 1
                    state = state.copy(
                        isLoading = true,
                        errorMessage = "Slowing down — retrying in ${formatSpendGuardCountdown(throttled.retryAfterSeconds)}…",
                    )
                    delay(throttled.retryAfterSeconds.coerceIn(1, 120) * 1000L)
                    if (requestedSession != sessionId) return
                    state = state.copy(errorMessage = null)
                    generationMutex.withLock {
                        loadOrGenerate(chunk, requestedSession)
                    }
                }
                if (requestedSession != sessionId) {
                    iosCloudTtsTraceLog("cloud.loopStale", "session=$requestedSession reason=after-load")
                    return
                }
                iosCloudTtsTraceLog("cloud.loaded", "session=$requestedSession index=$currentChunkIndex bytes=${audio?.size ?: -1}")
                if (audio == null || audio.size <= WAV_HEADER_SIZE) {
                    iosCloudTtsTraceLog("cloud.loopBreak", "session=$requestedSession reason=empty-audio index=$currentChunkIndex")
                    return
                }
                val played = playAudioAndWait(audio, requestedSession)
                iosCloudTtsTraceLog("cloud.played", "session=$requestedSession index=$currentChunkIndex ok=$played")
                if (!played) return
                if (requestedSession != sessionId) {
                    iosCloudTtsTraceLog("cloud.loopStale", "session=$requestedSession reason=after-play")
                    return
                }
                currentChunkIndex += 1
                iosCloudTtsTraceLog("cloud.advanced", "session=$requestedSession nextIndex=$currentChunkIndex")
            }
            if (requestedSession == sessionId) {
                val completed = currentChunkIndex >= chunks.size
                iosCloudTtsTraceLog("cloud.loopEnd", "session=$requestedSession completed=$completed index=$currentChunkIndex total=${chunks.size}")
                state = state.copy(
                    isPlaying = false,
                    isLoading = false,
                    isPaused = false,
                    progress = if (completed) ReaderTtsProgress() else state.progress,
                    completionCount = if (completed) state.completionCount + 1 else state.completionCount,
                )
                if (completed) {
                    chunks = emptyList()
                    currentChunkIndex = -1
                    deactivateAudioSession()
                }
            }
        } catch (_: CancellationException) {
            // User stop/skip is expected and must not surface as a playback error.
            iosCloudTtsTraceLog("cloud.loopCancelled", "session=$requestedSession")
        } catch (error: Throwable) {
            iosCloudTtsTraceLog("cloud.loopError", "session=$requestedSession error=${error::class.simpleName}:${error.message?.take(160)}")
            if (requestedSession == sessionId) fail(error.message ?: "Cloud TTS failed")
        }
    }

    private suspend fun loadOrGenerate(chunk: ReaderTtsChunk, requestedSession: Long): ByteArray? {
        val file = cacheFile(chunk)
        // Prefetched audio wins over both the disk cache and a fresh request:
        // it was fetched while the previous chunk was still speaking.
        prefetchedAudio.remove(chunk.index)?.let { prefetched ->
            iosCloudTtsTraceLog("cloud.prefetchUse", "session=$requestedSession index=${chunk.index} bytes=${prefetched.size}")
            return prefetched
        }
        val cached = withContext(Dispatchers.Default) { readFile(file) }
        if (cached != null && cached.size > WAV_HEADER_SIZE) {
            state = state.copy(statusMessage = "Using cached audio")
            iosCloudTtsTraceLog("cloud.cache", "session=$requestedSession hit bytes=${cached.size}")
            refreshCacheSummary()
            return cached
        }
        state = state.copy(statusMessage = "Preparing audio")
        // Android benchmark priority: Fish BYOK / worker (Fish-backed) first,
        // then Gemini REST BYOK, then the legacy Gemini Live WebSocket.
        if (useFishRest()) {
            iosCloudTtsTraceLog("cloud.cache", "session=$requestedSession miss backend=${cloudBackendName()}")
            return fishSynthesize(chunk, requestedSession)
        }
        if (useGeminiRest()) {
            iosCloudTtsTraceLog("cloud.cache", "session=$requestedSession miss backend=gemini-byok")
            return geminiRestSynthesize(chunk, requestedSession)
        }
        iosCloudTtsTraceLog("cloud.cache", "session=$requestedSession miss backend=gemini-ws")
        ensureConnected()
        val payload = buildJsonObject {
            put("realtimeInput", buildJsonObject { put("text", chunk.spokenText) })
        }.toString()
        sendMessage(payload)
        val pcm = ByteArrayAccumulator()
        try {
            withTimeout(CLOUD_TTS_TIMEOUT_MILLIS) {
                while (requestedSession == sessionId) {
                    when (val event = events.receive()) {
                        is IosGeminiWsEvent.Audio -> pcm.append(event.bytes)
                        IosGeminiWsEvent.TurnComplete -> break
                        is IosGeminiWsEvent.Error -> throw IllegalStateException(normalizeCloudError(event.message))
                    }
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (requestedSession != sessionId) throw CancellationException()
            fail(normalizeCloudError(error.message.orEmpty()))
            return null
        }
        val bytes = pcm.toByteArray()
        if (bytes.isEmpty()) {
            fail("Cloud TTS returned no audio")
            return null
        }
        val wav = buildWav(bytes)
        withContext(Dispatchers.Default) {
            writeFileAtomically(file, wav)
            pruneCacheIfNeeded()
        }
        refreshCacheSummary()
        return wav
    }

    /**
     * Fish Audio synthesis (Android `FishRestTtsClient` parity). One request =
     * one mp3 chunk, cached under the existing chunk filename (AVAudioPlayer
     * sniffs content, so the `.wav` name is cosmetic and cache browsers keep
     * working). Throws [FishRateLimited] so the loop can wait out the window
     * and retry once; all other failures go through [fail].
     */
    private suspend fun fishSynthesize(
        chunk: ReaderTtsChunk,
        requestedSession: Long,
        reportFailures: Boolean = true,
    ): ByteArray? {
        val file = cacheFile(chunk)
        val byok = fishByokAvailable()
        iosCloudTtsTraceLog(
            "cloud.fishFetch",
            "session=$requestedSession backend=${if (byok) "byok-direct" else "worker"} textLen=${chunk.spokenText.length}"
        )
        val url = if (byok) {
            "https://api.fish.audio/v1/tts"
        } else {
            IOS_TTS_WORKER_URL.removeSuffix("/") + "/v2/tts"
        }
        val headers = if (byok) {
            mapOf(
                "Authorization" to "Bearer ${settings.fishKey}",
                // The selected model drives the Fish `model` header, so the
                // free tier is actually used when it is picked.
                "model" to fishModelHeader(),
            )
        } else {
            mapOf("Authorization" to "Bearer ${authToken.orEmpty()}")
        }
        val body = if (byok) {
            buildJsonObject {
                put("text", chunk.spokenText)
                put("reference_id", settings.ttsSpeakerId)
                put("format", "mp3")
                put("mp3_bitrate", 128)
                put("normalize", true)
                put("latency", "normal")
                put("chunk_length", 300)
                put("min_chunk_length", 50)
                put("condition_on_previous_chunks", true)
                put("max_new_tokens", 1024)
                put("temperature", 0.7)
                put("top_p", 0.7)
                put("repetition_penalty", 1.2)
                put("prosody", buildJsonObject {
                    put("speed", 1)
                    put("volume", 0)
                    put("normalize_loudness", true)
                })
            }.toString()
        } else {
            buildJsonObject {
                put("text", chunk.spokenText)
                put("voiceId", settings.ttsSpeakerId)
                put("format", "mp3")
                put("latency", "normal")
            }.toString()
        }
        val response = try {
            withTimeout(CLOUD_TTS_TIMEOUT_MILLIS) {
                IosReaderAiHttpClient.postBytes(url, body, headers)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                iosCloudTtsTraceLog("cloud.fishThrow", "session=$requestedSession error=${error::class.simpleName}")
                throw error
            }
            if (requestedSession != sessionId) throw CancellationException()
            if (reportFailures) fail("Fish TTS request failed: ${error.message.orEmpty()}")
            return null
        }
        if (requestedSession != sessionId) throw CancellationException()
        iosCloudTtsTraceLog("cloud.fishResponse", "session=$requestedSession status=${response.statusCode} bytes=${response.bodyBytes.size}")
        if (response.statusCode == 429) {
            val retry = parseSpendGuardError(response.body)?.second ?: 30
            iosCloudTtsTraceLog("cloud.fishRateLimited", "session=$requestedSession retryAfterSeconds=$retry")
            throw FishRateLimited(retry)
        }
        if (response.statusCode == 402) {
            iosCloudTtsTraceLog("cloud.fishPayment", "session=$requestedSession")
            if (!reportFailures) return null
            val guard = parseSpendGuardError(response.body)
            if (guard?.first == "DAILY_SPEND_LIMIT") {
                fail(spendGuardSentinel(guard.first, guard.second))
            } else {
                fail("INSUFFICIENT_CREDITS")
            }
            return null
        }
        if (response.statusCode !in 200..299 || response.bodyBytes.size < 1024) {
            iosCloudTtsTraceLog("cloud.fishBadResponse", "session=$requestedSession status=${response.statusCode} bytes=${response.bodyBytes.size}")
            if (reportFailures) fail("Fish TTS error ${response.statusCode}")
            return null
        }
        // Worker-reported USD cost for the session-spend line (BYOK is $0
        // here; the user's own Fish key is billed by Fish directly). Applied on
        // the main dispatcher because prefetch synthesizes off it.
        response.headers["x-tts-cost-micros"]?.toLongOrNull()?.let { cost ->
            if (cost > 0) {
                sessionSpendMicros += cost
                withContext(Dispatchers.Main.immediate) {
                    state = state.copy(cloudSessionSpendMicros = sessionSpendMicros)
                }
            }
        }
        withContext(Dispatchers.Default) {
            writeFileAtomically(file, response.bodyBytes)
            pruneCacheIfNeeded()
        }
        refreshCacheSummary()
        return response.bodyBytes
    }

    /**
     * Gemini proper (non-Live) TTS over REST (`:generateContent` with
     * `responseModalities: AUDIO`), BYOK only — the user's own Gemini key, so
     * no credits are spent. Android benchmark (`GeminiRestTtsClient`).
     *
     * The selected model id drives the endpoint, which is the point: the
     * previous iOS path sent every Gemini selection through the legacy Live
     * WebSocket and silently ignored the chosen model.
     */
    private suspend fun geminiRestSynthesize(chunk: ReaderTtsChunk, requestedSession: Long): ByteArray? {
        val file = cacheFile(chunk)
        val model = settings.ttsModel.substringAfter(':').ifBlank { GEMINI_TTS_MODEL_LITE }
        iosCloudTtsTraceLog("cloud.geminiFetch", "session=$requestedSession model=$model textLen=${chunk.spokenText.length}")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" +
            "?key=${urlEncode(settings.geminiKey)}"
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", chunk.spokenText) }) })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                put("speechConfig", buildJsonObject {
                    put("voiceConfig", buildJsonObject {
                        put("prebuiltVoiceConfig", buildJsonObject { put("voiceName", settings.ttsSpeakerId) })
                    })
                })
            })
        }.toString()
        val response = try {
            withTimeout(CLOUD_TTS_TIMEOUT_MILLIS) {
                IosReaderAiHttpClient.post(url, body, mapOf("x-goog-api-key" to settings.geminiKey))
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (requestedSession != sessionId) throw CancellationException()
            fail("Gemini TTS request failed: ${error.message.orEmpty()}")
            return null
        }
        if (requestedSession != sessionId) throw CancellationException()
        iosCloudTtsTraceLog("cloud.geminiResponse", "session=$requestedSession status=${response.statusCode}")
        if (response.statusCode == 400 || response.statusCode == 404) {
            // Almost always a bad key or a model the key cannot use. Say so
            // instead of the generic HTTP code the Fish path reports.
            val detail = response.body.take(200)
            fail("Gemini TTS rejected the request (${response.statusCode}). Check your Gemini key and model. $detail")
            return null
        }
        if (response.statusCode == 429) {
            val retry = parseSpendGuardError(response.body)?.second ?: 30
            iosCloudTtsTraceLog("cloud.geminiRateLimited", "session=$requestedSession retryAfterSeconds=$retry")
            throw FishRateLimited(retry)
        }
        if (response.statusCode !in 200..299) {
            fail("Gemini TTS error ${response.statusCode}")
            return null
        }
        val pcm = extractGeminiAudioPayload(response.body)
        if (pcm == null || pcm.isEmpty()) {
            fail("Gemini TTS returned no audio")
            return null
        }
        val wav = buildWav(pcm)
        withContext(Dispatchers.Default) {
            writeFileAtomically(file, wav)
            pruneCacheIfNeeded()
        }
        refreshCacheSummary()
        return wav
    }

    /**
     * Pulls the first base64 inline audio payload out of a `:generateContent`
     * response. Returns raw 16-bit mono PCM (Gemini's documented TTS layout),
     * which [buildWav] wraps in a header.
     */
    private fun extractGeminiAudioPayload(body: String): ByteArray? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val candidates = root["candidates"]?.jsonArray.orEmpty()
        for (candidate in candidates) {
            val parts = candidate.jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
            for (part in parts) {
                val inline = part.jsonObject["inlineData"]?.jsonObject ?: part.jsonObject["inline_data"]?.jsonObject
                val encoded = inline?.get("data")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (encoded.isNotBlank()) {
                    return runCatching { Base64.Default.decode(encoded) }.getOrNull()
                }
            }
        }
        return null
    }

    private suspend fun playAudioAndWait(audio: ByteArray, requestedSession: Long): Boolean {
        ensureAudioSession()
        iosCloudTtsTraceLog("cloud.playEntry", "session=$requestedSession bytes=${audio.size} wantsPlayback=$wantsPlayback")
        val completed = CompletableDeferred<Boolean>()
        playbackContinuation = completed
        val createdPlayer = AVAudioPlayer(data = audio.toNSData(), error = null)
        val prepared = createdPlayer.prepareToPlay()
        iosCloudTtsTraceLog("cloud.playerReady", "session=$requestedSession prepared=$prepared duration=${createdPlayer.duration}")
        if (!prepared) {
            playbackContinuation = null
            fail("Could not decode cloud audio")
            return false
        }
        player = createdPlayer
        createdPlayer.delegate = audioDelegate
        state = state.copy(
            isLoading = false,
            isPlaying = wantsPlayback,
            isPaused = !wantsPlayback,
            statusMessage = null,
        )
        if (wantsPlayback) {
            ttsStartMark?.let { mark ->
                iosTtsStartLog("cloud.firstPlay", "bytes=${audio.size}", mark)
                ttsStartMark = null
            }
            val started = createdPlayer.play()
            iosCloudTtsTraceLog("cloud.playerPlay", "session=$requestedSession started=$started")
            if (started) prefetchNextChunk(requestedSession)
        } else {
            iosCloudTtsTraceLog("cloud.playerHeld", "session=$requestedSession reason=paused-at-start")
        }
        val monitor = startPlaybackMonitor(createdPlayer, completed, requestedSession)
        try {
            iosCloudTtsTraceLog("cloud.await", "session=$requestedSession")
            val success = completed.await()
            iosCloudTtsTraceLog("cloud.awaitDone", "session=$requestedSession success=$success")
            if (!success && requestedSession == sessionId) {
                fail("Could not play cloud audio")
            }
            return success
        } catch (error: Throwable) {
            iosCloudTtsTraceLog("cloud.awaitThrow", "session=$requestedSession error=${error::class.simpleName}")
            throw error
        } finally {
            monitor.cancel()
            if (requestedSession == sessionId) {
                player?.stop()
                player = null
                playbackContinuation = null
            }
        }
    }

    /**
     * Watches the active player while [completed] is pending and completes it
     * once the stream has actually reached its end.
     *
     * Android parity (`TtsPlaybackManager.trackWordByWord`): chunk transition is
     * driven by observed playback position, not only by a completion callback —
     * ExoPlayer's own transition callback is best-effort across format/decoder
     * changes too. `AVAudioPlayer`'s `audioPlayerDidFinishPlaying` is
     * occasionally not delivered for some MP3 payloads, and a lost callback left
     * the loop suspended on `completed.await()` forever: the reader sat silent
     * after one chunk until the user pressed skip. A position-based end check
     * makes the same end-of-audio condition the transition trigger, so a lost
     * delegate costs nothing.
     *
     * A genuine mid-stream stall (position frozen, not at the end) is *not*
     * treated as completion — it is reported and left to the pause/resume and
     * interruption paths, so a dropped route is not silently skipped.
     */
    /**
     * Android parity (`TtsPlaybackManager.prefetchNextChunkAudio`): fetch the
     * chunk after the one now playing so the hand-off has audio ready.
     *
     * Without it every transition paid a full synthesis round-trip in silence —
     * the iOS trace showed ~5.7s of dead air between a 10s chunk ending and
     * the next one starting, which reads as a pause after every chunk.
     *
     * Deliberately limited to the stateless Fish REST path (BYOK or worker):
     * the Gemini Live WebSocket shares one connection and one turn channel
     * across the session, so it must not be driven ahead of the chunk that is
     * actually speaking.
     */
    /** Drops any in-flight or completed prefetch; a new session never reuses one. */
    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedAudio.clear()
    }

    private fun prefetchNextChunk(requestedSession: Long) {
        if (!useStatelessRestBackend()) return
        val nextIndex = currentChunkIndex + 1
        val nextChunk = chunks.getOrNull(nextIndex) ?: return
        if (nextIndex in prefetchedAudio) return
        prefetchJob?.cancel()
        iosCloudTtsTraceLog("cloud.prefetchStart", "session=$requestedSession index=$nextIndex")
        prefetchJob = scope.launch(Dispatchers.Default) {
            val audio = try {
                // Cache-file writes are serialized by the same generation lock
                // the playback loop uses; a prefetch that loses the race simply
                // finishes and its result is dropped below.
                generationMutex.withLock {
                    loadOrGenerateForPrefetch(nextChunk, requestedSession)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // A failed prefetch must never surface: the chunk is fetched
                // again on the main path when its turn comes.
                null
            }
            withContext(Dispatchers.Main.immediate) {
                if (requestedSession != sessionId) return@withContext
                if (audio != null && audio.size > WAV_HEADER_SIZE) {
                    prefetchedAudio[nextIndex] = audio
                    iosCloudTtsTraceLog("cloud.prefetchReady", "index=$nextIndex bytes=${audio.size}")
                } else {
                    iosCloudTtsTraceLog("cloud.prefetchSkipped", "index=$nextIndex")
                }
            }
        }
    }

    /**
     * Prefetch variant of [loadOrGenerate]: never touches UI state, never
     * reports failures to the user, and never drives the shared Gemini Live
     * socket.
     */
    private suspend fun loadOrGenerateForPrefetch(
        chunk: ReaderTtsChunk,
        requestedSession: Long,
    ): ByteArray? {
        if (!useStatelessRestBackend()) return null
        val file = cacheFile(chunk)
        val cached = withContext(Dispatchers.Default) { readFile(file) }
        if (cached != null && cached.size > WAV_HEADER_SIZE) return cached
        return if (useFishRest()) {
            fishSynthesize(chunk, requestedSession, reportFailures = false)
        } else {
            geminiRestSynthesize(chunk, requestedSession)
        }
    }

    /**
     * Stateless per-request backends only. The Gemini Live WebSocket shares one
     * connection and turn channel across the session, so it must never be
     * driven ahead of the chunk that is actually speaking.
     */
    private fun useStatelessRestBackend(): Boolean = useFishRest() || useGeminiRest()

    private fun startPlaybackMonitor(
        activePlayer: AVAudioPlayer,
        completed: CompletableDeferred<Boolean>,
        requestedSession: Long,
    ): Job {
        return scope.launch(Dispatchers.Main) {
            var lastPosition = -1.0
            var stalledSamples = 0
            while (isActive && !completed.isCompleted) {
                delay(CloudTtsPlaybackMonitorPolicy.MONITOR_MILLIS)
                if (requestedSession != sessionId) return@launch
                val duration = activePlayer.duration
                val position = activePlayer.currentTime
                val playing = activePlayer.isPlaying()
                // End detection comes first: a player that ran off the end on
                // its own already reports isPlaying == false, so gating this on
                // `playing` would miss exactly the case being recovered from.
                if (CloudTtsPlaybackMonitorPolicy.hasReachedEnd(position, duration)) {
                    if (!wantsPlayback) {
                        // Paused inside the final moments: leave the chunk
                        // alone rather than spending the next fetch while the
                        // user holds playback.
                        lastPosition = position
                        stalledSamples = 0
                        continue
                    }
                    iosCloudTtsTraceLog(
                        "cloud.finishRecovered",
                        "session=$requestedSession pos=$position dur=$duration playing=$playing delegateFired=false"
                    )
                    if (completed.isActive) completed.complete(true)
                    return@launch
                }
                if (!playing || !wantsPlayback) {
                    lastPosition = position
                    stalledSamples = 0
                    continue
                }
                stalledSamples = CloudTtsPlaybackMonitorPolicy.nextStalledSamples(
                    previous = stalledSamples,
                    position = position,
                    lastPosition = lastPosition,
                )
                lastPosition = position
                when (
                    CloudTtsPlaybackMonitorPolicy.evaluate(
                        position = position,
                        duration = duration,
                        playing = playing,
                        stalledSamples = stalledSamples,
                    )
                ) {
                    CloudTtsPlaybackMonitorPolicy.Action.FINISHED -> {
                        iosCloudTtsTraceLog(
                            "cloud.finishRecovered",
                            "session=$requestedSession pos=$position dur=$duration delegateFired=false"
                        )
                        if (completed.isActive) completed.complete(true)
                        return@launch
                    }
                    CloudTtsPlaybackMonitorPolicy.Action.STALLED -> {
                        iosCloudTtsTraceLog(
                            "cloud.stall",
                            "session=$requestedSession pos=$position dur=$duration"
                        )
                    }
                    CloudTtsPlaybackMonitorPolicy.Action.PLAYING,
                    CloudTtsPlaybackMonitorPolicy.Action.IDLE,
                    -> Unit
                }
            }
        }
    }

    private fun onAudioFinished(callbackPlayer: AVAudioPlayer, success: Boolean) {
        val identityMatch = player === callbackPlayer
        val continuation = playbackContinuation
        iosCloudTtsTraceLog(
            "cloud.delegateFinished",
            "success=$success identityMatch=$identityMatch continuationActive=${continuation?.isActive} " +
                "pos=${callbackPlayer.currentTime} dur=${callbackPlayer.duration}"
        )
        if (!identityMatch) return
        if (continuation == null) return
        scope.launch {
            if (player !== callbackPlayer) return@launch
            iosCloudTtsTraceLog("cloud.continuationComplete", "success=$success")
            if (continuation.isActive) continuation.complete(success)
        }
    }

    private fun onAudioDecodeError(callbackPlayer: AVAudioPlayer) {
        iosCloudTtsTraceLog(
            "cloud.delegateDecodeError",
            "identityMatch=${player === callbackPlayer} continuationActive=${playbackContinuation?.isActive}"
        )
        if (player !== callbackPlayer) return
        playbackContinuation?.let { continuation ->
            if (continuation.isActive) continuation.complete(false)
        }
        fail("Could not decode cloud audio")
    }

    private fun handleAudioInterruption(interruption: IosTtsAudioInterruption) {
        val event = when (interruption) {
            IosTtsAudioInterruption.Began -> LocalTtsInterruptionEvent.Began(
                playbackWasActive = state.isPlaying || state.isLoading
            )
            is IosTtsAudioInterruption.Ended -> LocalTtsInterruptionEvent.Ended(
                systemAllowsResume = interruption.systemAllowsResume
            )
            IosTtsAudioInterruption.OutputBecameUnavailable ->
                LocalTtsInterruptionEvent.OutputBecameNoisy(
                    playbackWasActive = state.isPlaying || state.isLoading
                )
        }
        val transition = interruptionState.reduce(event)
        interruptionState = transition.state
        iosCloudTtsTraceLog("cloud.interruption", "event=${interruption::class.simpleName} action=${transition.action}")
        when (transition.action) {
            LocalTtsInterruptionAction.NONE -> Unit
            LocalTtsInterruptionAction.PAUSE -> pauseInternal()
            LocalTtsInterruptionAction.RESUME -> resumeInternal()
        }
    }

    private fun restartAt(target: Int) {
        if (chunks.isEmpty()) return
        iosCloudTtsTraceLog("cloud.restartAt", "from=$currentChunkIndex to=$target sessionWas=$sessionId")
        sessionId += 1
        playJob?.cancel()
        playJob = null
        cancelPrefetch()
        playbackContinuation?.cancel()
        playbackContinuation = null
        player?.stop()
        player = null
        interruptionState = LocalTtsInterruptionState()
        currentChunkIndex = target.coerceIn(0, chunks.lastIndex)
        val requestedSession = sessionId
        wantsPlayback = true
        state = state.copy(
            isLoading = true,
            isPlaying = false,
            isPaused = false,
            errorMessage = null,
            progress = ReaderTtsProgress(
                sessionId = requestedSession,
                chunks = chunks,
                currentChunkIndex = currentChunkIndex,
            ),
        )
        playJob = scope.launch { playChunks(requestedSession) }
    }

    private suspend fun ensureConnected() {
        val directKey = settings.geminiKey.takeIf { byokAvailable() }
        val url = if (directKey != null) {
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${urlEncode(directKey)}"
        } else {
            val wsBase = workerUrl.removeSuffix("/").replace("https://", "wss://").replace("http://", "ws://")
            "$wsBase/live?speaker=${urlEncode(settings.ttsSpeakerId)}&token=${urlEncode(authToken.orEmpty())}"
        }
        if (websocket != null && setupReady.isCompleted) return
        closeWebSocket()
        val nsUrl = NSURL.URLWithString(url) ?: error("Invalid cloud TTS URL")
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration,
            delegate = null,
            delegateQueue = null,
        )
        websocketSession = session
        val task = session.webSocketTaskWithURL(nsUrl)
        websocket = task
        setupReady = CompletableDeferred()
        events = Channel(Channel.UNLIMITED)
        receiving = true
        receiveNext(task)
        task.resume()
        val systemPrompt = """
            You are a professional audiobook narrator.
            Your ONLY task is to read the exact text provided to you, word for word, neutral emotion, and with good pacing.
            Do NOT add conversational filler, acknowledgments, or extra words. Do NOT skip parts or summarize. Output ONLY the audio reading of the provided text.
        """.trimIndent()
        val setup = buildJsonObject {
            put("setup", buildJsonObject {
                put("model", "models/$GEMINI_CLOUD_TTS_MODEL")
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
                })
                put("generationConfig", buildJsonObject {
                    put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                    put("speechConfig", buildJsonObject {
                        put("voiceConfig", buildJsonObject {
                            put("prebuiltVoiceConfig", buildJsonObject { put("voiceName", settings.ttsSpeakerId) })
                        })
                    })
                })
            })
        }.toString()
        sendMessage(setup)
        try {
            withTimeout(10_000L) { if (!setupReady.await()) error("Cloud TTS setup failed") }
        } catch (error: Throwable) {
            closeWebSocket()
            throw error
        }
    }

    private suspend fun sendMessage(payload: String) {
        val task = websocket ?: error("Cloud TTS is not connected")
        suspendCancellableCoroutine<Unit> { continuation ->
            task.sendMessage(
                NSURLSessionWebSocketMessage(string = payload),
            ) { error ->
                if (!continuation.isActive) return@sendMessage
                if (error != null) continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                else continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { closeWebSocket() }
        }
    }

    private fun receiveNext(task: NSURLSessionWebSocketTask) {
        task.receiveMessageWithCompletionHandler { message, error ->
            if (task != websocket || !receiving) return@receiveMessageWithCompletionHandler
            if (error != null) {
                val mapped = if (error.localizedDescription.contains("402")) "INSUFFICIENT_CREDITS" else error.localizedDescription
                events.trySend(IosGeminiWsEvent.Error(mapped))
                if (setupReady.isActive) setupReady.complete(false)
                return@receiveMessageWithCompletionHandler
            }
            message?.let(::handleMessage)
            if (task == websocket && receiving) receiveNext(task)
        }
    }

    private fun handleMessage(message: NSURLSessionWebSocketMessage) {
        val payload = message.string ?: message.data?.toUtf8String().orEmpty()
        if (payload.isBlank()) return
        runCatching { json.parseToJsonElement(payload).jsonObject }
            .onFailure { events.trySend(IosGeminiWsEvent.Error(it.message ?: "Invalid cloud TTS response")) }
            .onSuccess { root ->
                root["error"]?.let { errorValue ->
                    val messageText = errorValue.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                        ?: errorValue.toString()
                    events.trySend(IosGeminiWsEvent.Error(messageText))
                    if (setupReady.isActive) setupReady.complete(false)
                }
                if (root["setupComplete"] != null && setupReady.isActive) setupReady.complete(true)
                val content = root["serverContent"]?.jsonObject ?: return@onSuccess
                val parts = content["modelTurn"]?.jsonObject?.get("parts")
                    ?.jsonArray
                    .orEmpty()
                parts.forEach { part ->
                    val inlineData = part.jsonObject["inlineData"]?.jsonObject
                    val encoded = inlineData?.get("data")?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (encoded.isNotBlank()) {
                        runCatching { Base64.Default.decode(encoded) }
                            .onSuccess { events.trySend(IosGeminiWsEvent.Audio(it)) }
                            .onFailure { events.trySend(IosGeminiWsEvent.Error("Invalid cloud audio")) }
                    }
                }
                if (content["turnComplete"]?.jsonPrimitive?.contentOrNull == "true") {
                    events.trySend(IosGeminiWsEvent.TurnComplete)
                }
            }
    }

    private fun closeWebSocket() {
        receiving = false
        websocket?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, reason = null)
        websocket = null
        websocketSession?.invalidateAndCancel()
        websocketSession = null
        setupReady = CompletableDeferred<Boolean>().apply { complete(false) }
        events.close()
        events = Channel(Channel.UNLIMITED)
    }

    private fun fail(message: String) {
        val normalized = normalizeCloudError(message)
        iosCloudTtsTraceLog("cloud.fail", "message=${normalized.take(160)}")
        state = state.copy(
            isPlaying = false,
            isLoading = false,
            isPaused = false,
            errorMessage = normalized,
        )
        player?.stop()
        playbackContinuation?.let { if (it.isActive) it.complete(false) }
        closeWebSocket()
    }

    /** Velocity throttle from the Fish path; the loop waits and retries once. */
    private class FishRateLimited(val retryAfterSeconds: Int) : IllegalStateException("RATE_LIMITED:$retryAfterSeconds")

    private fun startGateError(): String? {
        // Android TtsPlaybackManager gate parity: any cloud backend enables
        // start — Gemini Live BYOK, Gemini REST BYOK, Fish BYOK, or the
        // credited worker. The old legacy-only check blocked Fish/REST users.
        if (!cloudTtsModeEnabled()) return "Choose Cloud TTS in AI settings first."
        val backend = resolveBackend()
        if (backend == CloudTtsBackend.UNAVAILABLE) {
            // Name the actual reason: this gate used to report the same generic
            // message for "no key" and "key saved for a different provider",
            // which is what made a silently misconfigured setup undebuggable.
            return when {
                settings.hasAnyAiKey ->
                    "Cloud TTS is not configured. Save a ${backendProviderLabel()} key, or sign in to use the wallet."
                isSignedIn && authToken.isNullOrBlank() -> "Sign in again to use cloud TTS."
                !isSignedIn -> "Sign in to use cloud TTS, or configure a ${backendProviderLabel()} key."
                else -> "Cloud TTS is not configured."
            }
        }
        // Android benchmark parity: no Pro free pass — everyone spends the
        // wallet (migrated) or legacy credits. The worker enforces the same.
        if (backend == CloudTtsBackend.WORKER && !hasSpendableBalance(credits, walletMicros)) {
            return if (walletMigrated) "Cloud TTS needs balance. Top up your wallet to continue."
            else "Cloud TTS needs credits."
        }
        return null
    }

    private fun backendProviderLabel(): String = when (settings.ttsProvider) {
        "fish" -> "Fish Audio"
        "gemini" -> "Gemini"
        else -> "provider"
    }

    /**
     * Android benchmark priority (`TtsService.audioGenerator`): a saved BYOK key
     * always wins over spending credits, and Fish is checked before Gemini.
     */
    private fun resolveBackend(): CloudTtsBackend = resolveCloudTtsBackend(
        settings = settings,
        isSignedIn = isSignedIn,
        hasAuthToken = !authToken.isNullOrBlank(),
        hasWorkerUrl = workerUrl.isNotBlank(),
    )

    private fun byokAvailable(): Boolean = settings.isByokCloudTtsAvailable

    private fun fishByokAvailable(): Boolean = settings.isFishByokTtsAvailable

    private fun workerAvailable(): Boolean = isSignedIn && !authToken.isNullOrBlank() && workerUrl.isNotBlank()

    private fun workerFishAvailable(): Boolean =
        isSignedIn && !authToken.isNullOrBlank() && !byokAvailable() && !fishByokAvailable()

    /** Fish REST covers Fish BYOK and the credited worker (both are Fish-backed). */
    private fun useFishRest(): Boolean = cloudTtsModeEnabled() &&
        (resolveBackend() == CloudTtsBackend.FISH_BYOK || resolveBackend() == CloudTtsBackend.WORKER)

    /** Gemini REST BYOK: the user's own key against :generateContent (no credits). */
    private fun useGeminiRest(): Boolean = cloudTtsModeEnabled() && resolveBackend() == CloudTtsBackend.GEMINI_BYOK

    private fun cloudBackendName(): String = when (resolveBackend()) {
        CloudTtsBackend.FISH_BYOK -> "fish-byok"
        CloudTtsBackend.GEMINI_BYOK -> "gemini-byok"
        CloudTtsBackend.WORKER -> "fish-worker"
        CloudTtsBackend.UNAVAILABLE -> "none"
    }

    /** Fish `model` request header for the selected TTS model (free tier aware). */
    private fun fishModelHeader(): String =
        settings.ttsModel.substringAfter(':', "").ifBlank { FISH_TTS_MODEL_DEFAULT }

    private fun cloudTtsModeEnabled(): Boolean =
        com.aryan.reader.shared.isCloudTtsModelEnabled(settings.ttsModel)

    private fun hasActiveSession(): Boolean = chunks.isNotEmpty() && (state.isLoading || state.isPlaying || state.isPaused)

    private fun ensureAudioSession() {
        IosTtsAudioSessionTeardown.activate()
    }

    private fun deactivateAudioSession() {
        IosTtsAudioSessionTeardown.deactivate()
    }

    private fun cacheFile(chunk: ReaderTtsChunk): String {
        val bookPath = cacheSegment(bookTitle, "book")
        val chapterPath = cacheSegment(chunk.chapterTitle, "chapter")
        val digest = sha256((chunk.spokenText + settings.ttsSpeakerId + "CLOUD").encodeToByteArray())
            .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
            .take(16)
        val safeSpeaker = settings.ttsSpeakerId.replace(Regex("[^A-Za-z0-9._-]+"), "_")
        val directory = "$cacheRoot/$bookPath/$chapterPath"
        return "$directory/cached_chunk_${safeSpeaker}_$digest.wav"
    }

    private fun cacheSummary(): ReaderTtsCacheSummary {
        var chapters = 0
        var chunks = 0
        var voiceChunks = 0
        var totalBytes = 0L
        var voiceBytes = 0L
        fun scan(path: String, isChapter: Boolean) {
            val entries = fileManager.contentsOfDirectoryAtPath(path, error = null).orEmpty()
            val wavs = entries.mapNotNull { name ->
                val value = name as? String ?: return@mapNotNull null
                if (!value.endsWith(".wav")) return@mapNotNull null
                val full = "$path/$value"
                val attrs = fileManager.attributesOfItemAtPath(full, error = null).orEmpty()
                value to (attrs["NSFileSize"] as? Number)?.toLong().orZero()
            }
            if (isChapter && wavs.isNotEmpty()) chapters++
            wavs.forEach { (name, size) ->
                chunks++
                totalBytes += size
                if (name.contains("_${settings.ttsSpeakerId}_")) {
                    voiceChunks++
                    voiceBytes += size
                }
            }
            entries.mapNotNull { it as? String }
                .filter { !it.endsWith(".wav") }
                .forEach { child -> scan("$path/$child", isChapter = true) }
        }
        scan(cacheRoot, isChapter = false)
        return ReaderTtsCacheSummary(chapters, chunks, voiceChunks, totalBytes, voiceBytes)
    }

    private var cacheScanJob: Job? = null

    private fun refreshCacheSummary() {
        cacheScanJob?.cancel()
        cacheScanJob = scope.launch(Dispatchers.Default) {
            val summary = cacheSummary()
            withContext(Dispatchers.Main.immediate) {
                state = state.copy(cacheSummary = summary)
            }
        }
    }

    private fun cacheSegment(value: String, fallback: String): String {
        val clean = value.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_', '-')
            .ifBlank { fallback }.take(48)
        val digest = sha256(value.encodeToByteArray()).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }.take(16)
        return "${clean}_$digest"
    }

    private fun readFile(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()

    private fun writeFileAtomically(path: String, bytes: ByteArray) {
        val data = bytes.toNSData()
        val temp = "$path.tmp"
        val directory = path.substringBeforeLast('/')
        fileManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        data.writeToFile(temp, atomically = true)
        if (fileManager.fileExistsAtPath(path)) fileManager.removeItemAtPath(path, error = null)
        fileManager.moveItemAtPath(temp, toPath = path, error = null)
    }

    private fun pruneCacheIfNeeded() {
        val files = mutableListOf<CacheFile>()
        fun collect(path: String) {
            fileManager.contentsOfDirectoryAtPath(path, error = null).orEmpty()
                .mapNotNull { it as? String }
                .forEach { name ->
                    val child = "$path/$name"
                    if (name.endsWith(".wav", ignoreCase = true)) {
                        val attributes = fileManager.attributesOfItemAtPath(child, error = null).orEmpty()
                        files += CacheFile(
                            path = child,
                            bytes = (attributes[NSFileSize] as? Number)?.toLong().orZero(),
                            modifiedAt = (attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970 ?: 0.0,
                        )
                    } else if (fileManager.fileExistsAtPath(child)) {
                        collect(child)
                    }
                }
        }
        collect(cacheRoot)
        var totalBytes = files.sumOf { it.bytes }
        files.sortWith(compareBy<CacheFile> { it.modifiedAt }.thenBy { it.path })
        while (files.size > MAX_CACHE_FILES || totalBytes > MAX_CACHE_BYTES) {
            if (files.isEmpty()) break
            val evicted = files.removeAt(0)
            if (fileManager.removeItemAtPath(evicted.path, error = null)) {
                totalBytes = (totalBytes - evicted.bytes).coerceAtLeast(0L)
            }
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val output = ByteArray(length.toInt())
        if (output.isNotEmpty()) {
            output.usePinned { pinned ->
                platform.posix.memcpy(pinned.addressOf(0), bytes, length)
            }
        }
        return output
    }

    private fun ByteArray.toNSData(): NSData {
        val data = platform.Foundation.NSMutableData.dataWithLength(size.toULong())
            ?: platform.Foundation.NSMutableData()
        if (isNotEmpty()) {
            usePinned { pinned ->
                platform.posix.memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
            }
        }
        return data
    }

    private fun NSData.toUtf8String(): String = NSString.create(
        data = this,
        encoding = NSUTF8StringEncoding,
    )?.toString().orEmpty()

    private fun normalizeCloudError(raw: String): String {
        // Spend-guard sentinels pass through verbatim: the reader overlay
        // maps them to countdown notices (Android EpubReaderControls
        // parity). Prose-ifying here would destroy the retry window the
        // overlay needs. Wallet-aware copy for everything else.
        if (parseSpendGuardSentinel(raw) != null) return raw
        parseSpendGuardError(raw)?.let { (kind, retry) ->
            return if (kind == "RATE_LIMITED") {
                "Slowing down — please retry in ${formatSpendGuardCountdown(retry)}."
            } else {
                "Daily spending cap reached — resets in ${formatSpendGuardCountdown(retry)}. " +
                    "Balance: ${formatMicrosUsd(walletMicros)}."
            }
        }
        val lower = raw.lowercase()
        return when {
            "insufficient_credits" in lower || "402" in lower ->
                if (walletMigrated) "You're out of balance. Top up your wallet to continue."
                else "Out of credits."
            "unauthorized" in lower || "authentication" in lower -> "Sign in again to use cloud TTS."
            else -> raw.ifBlank { "Cloud TTS failed." }
        }
    }

    private fun urlEncode(value: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val hex = "0123456789ABCDEF"
        return buildString(value.length * 3) {
            value.encodeToByteArray().forEach { byte ->
                val valueByte = byte.toInt() and 0xFF
                val char = valueByte.toChar()
                if (char in unreserved) append(char)
                else append('%').append(hex[valueByte ushr 4]).append(hex[valueByte and 0x0F])
            }
        }
    }

    private fun buildWav(pcm: ByteArray): ByteArray {
        val output = ByteArray(WAV_HEADER_SIZE + pcm.size)
        fun writeAscii(offset: Int, value: String) {
            value.encodeToByteArray().copyInto(output, offset)
        }
        fun writeLe32(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
            output[offset + 2] = (value ushr 16).toByte()
            output[offset + 3] = (value ushr 24).toByte()
        }
        fun writeLe16(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
        }
        writeAscii(0, "RIFF")
        writeLe32(4, 36 + pcm.size)
        writeAscii(8, "WAVEfmt ")
        writeLe32(16, 16)
        writeLe16(20, 1)
        writeLe16(22, 1)
        writeLe32(24, 24_000)
        writeLe32(28, 24_000 * 2)
        writeLe16(32, 2)
        writeLe16(34, 16)
        writeAscii(36, "data")
        writeLe32(40, pcm.size)
        pcm.copyInto(output, WAV_HEADER_SIZE)
        return output
    }

    private class ByteArrayAccumulator {
        private val chunks = mutableListOf<ByteArray>()
        private var size = 0
        fun append(value: ByteArray) {
            if (value.isEmpty()) return
            if (value.size > MAX_AUDIO_PCM_BYTES - size) {
                throw IllegalStateException("Cloud TTS audio chunk is too large")
            }
            chunks += value
            size += value.size
        }
        fun toByteArray(): ByteArray {
            val output = ByteArray(size)
            var offset = 0
            chunks.forEach { value -> value.copyInto(output, offset); offset += value.size }
            return output
        }
    }

    private sealed interface IosGeminiWsEvent {
        data class Audio(val bytes: ByteArray) : IosGeminiWsEvent
        data object TurnComplete : IosGeminiWsEvent
        data class Error(val message: String) : IosGeminiWsEvent
    }

    private companion object {
        const val WAV_HEADER_SIZE = 44
        const val CLOUD_TTS_TIMEOUT_MILLIS = 30_000L
        const val MAX_AUDIO_PCM_BYTES = 32 * 1024 * 1024
        const val MAX_CACHE_FILES = 512
        const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    }

    private data class CacheFile(
        val path: String,
        val bytes: Long,
        val modifiedAt: Double,
    )
}

private class IosCloudAudioDelegate(
    private val onFinished: (AVAudioPlayer, Boolean) -> Unit,
    private val onDecodeError: (AVAudioPlayer) -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    @ObjCSignatureOverride
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onFinished(player, successfully)
    }

    @ObjCSignatureOverride
    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: platform.Foundation.NSError?) {
        onDecodeError(player)
    }
}

private fun iosCloudTtsCacheRoot(): String {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
    return if (caches.isNullOrBlank()) "TTS_Cache" else "$caches/TTS_Cache"
}

private fun Number?.orZero(): Long = this?.toLong() ?: 0L
