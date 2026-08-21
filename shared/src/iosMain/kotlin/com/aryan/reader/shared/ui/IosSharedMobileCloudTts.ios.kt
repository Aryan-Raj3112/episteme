@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.io.encoding.ExperimentalEncodingApi::class,
)

package com.aryan.reader.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.DEFAULT_CLOUD_TTS_SPEAKER_ID
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderTtsCacheSummary
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.sha256
import com.aryan.reader.shared.ios.IosTtsAudioInterruption
import com.aryan.reader.shared.ios.IosTtsAudioInterruptionMonitor
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
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSCachesDirectory
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
import platform.Foundation.dataWithLength
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
        onFinished = { success -> onAudioFinished(success) },
        onDecodeError = { onAudioDecodeError() },
    )
    private val interruptionMonitor = IosTtsAudioInterruptionMonitor(::handleAudioInterruption)

    override var state by mutableStateOf(ReaderCloudTtsState())
        private set

    private var settings = ReaderAiByokSettings()
    private var isSignedIn = false
    private var isProUser = false
    private var credits = 0
    private var authToken: String? = null
    private var workerUrl = ""
    private var chunks: List<ReaderTtsChunk> = emptyList()
    private var bookTitle = ""
    private var bookId: String? = null
    private var currentChunkIndex = -1
    private var sessionId = 0L
    private var wantsPlayback = true
    private var playbackContinuation: CompletableDeferred<Boolean>? = null
    private var player: AVAudioPlayer? = null
    private var playJob: Job? = null
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
    }

    override fun configure(
        settings: ReaderAiByokSettings,
        isSignedIn: Boolean,
        isProUser: Boolean,
        credits: Int,
        authToken: String?,
        workerUrl: String,
    ) {
        val sanitized = settings.sanitized()
        val speakerChanged = this.settings.ttsSpeakerId != sanitized.ttsSpeakerId
        val modeChanged = this.settings.ttsModel != sanitized.ttsModel
        val accessChanged = this.isSignedIn != isSignedIn || this.authToken != authToken || this.workerUrl != workerUrl
        this.settings = sanitized
        this.isSignedIn = isSignedIn
        this.isProUser = isProUser
        this.credits = credits.coerceAtLeast(0)
        this.authToken = authToken
        this.workerUrl = workerUrl.trim()
        if (speakerChanged || modeChanged || accessChanged) {
            // Never keep a session authenticated with an old account/token or
            // speaking with a voice that no longer matches the selected mode.
            if (state.isPlaying || state.isLoading || state.isPaused) stop()
            closeWebSocket()
        }
        state = state.copy(
            isAvailable = cloudTtsModeEnabled() && (byokAvailable() || workerAvailable()),
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
    ) {
        val readable = chunks.filter { it.spokenText.isNotBlank() }
        if (readable.isEmpty()) return
        val gateError = startGateError()
        if (gateError != null) {
            state = state.copy(
                isAvailable = cloudTtsModeEnabled() && (byokAvailable() || workerAvailable()),
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
        this.currentChunkIndex = startChunkIndex.coerceIn(0, readable.lastIndex)
        this.sessionId += 1
        this.wantsPlayback = playWhenReady
        val requestedSession = sessionId
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

    override fun pause() {
        if (!hasActiveSession()) return
        wantsPlayback = false
        player?.pause()
        state = state.copy(isPlaying = false, isPaused = true, isLoading = state.isLoading)
    }

    override fun resume() {
        if (!hasActiveSession()) return
        wantsPlayback = true
        player?.play()
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
        if (hasActiveSession()) stop()
        refreshCacheSummary()
    }

    override fun clearCache() {
        scope.launch(Dispatchers.Default) {
            if (fileManager.fileExistsAtPath(cacheRoot)) {
                fileManager.removeItemAtPath(cacheRoot, error = null)
            }
            fileManager.createDirectoryAtPath(
                cacheRoot,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            refreshCacheSummary()
        }
    }

    override fun stop() {
        stop(clearError = true)
    }

    private fun stop(clearError: Boolean) {
        sessionId += 1
        playJob?.cancel()
        playJob = null
        playbackContinuation?.cancel()
        playbackContinuation = null
        player?.stop()
        player = null
        chunks = emptyList()
        currentChunkIndex = -1
        wantsPlayback = false
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
        interruptionMonitor.close()
        scope.cancel()
        websocketSession?.invalidateAndCancel()
        websocketSession = null
    }

    private suspend fun playChunks(requestedSession: Long) {
        try {
            while (scope.isActive && requestedSession == sessionId) {
                val chunk = chunks.getOrNull(currentChunkIndex) ?: break
                state = state.copy(
                    isAvailable = cloudTtsModeEnabled() && (byokAvailable() || workerAvailable()),
                    isLoading = true,
                    isPlaying = false,
                    isPaused = false,
                    progress = ReaderTtsProgress(
                        sessionId = requestedSession,
                        chunks = chunks,
                        currentChunkIndex = currentChunkIndex,
                    ),
                )
                val audio = generationMutex.withLock {
                    loadOrGenerate(chunk, requestedSession)
                }
                if (requestedSession != sessionId) return
                if (audio == null || audio.size <= WAV_HEADER_SIZE) return
                if (!playAudioAndWait(audio, requestedSession)) return
                if (requestedSession != sessionId) return
                currentChunkIndex += 1
            }
            if (requestedSession == sessionId) {
                val completed = currentChunkIndex >= chunks.size
                state = state.copy(
                    isPlaying = false,
                    isLoading = false,
                    isPaused = false,
                    progress = if (completed) ReaderTtsProgress() else state.progress,
                )
                if (completed) {
                    chunks = emptyList()
                    currentChunkIndex = -1
                    deactivateAudioSession()
                }
            }
        } catch (_: CancellationException) {
            // User stop/skip is expected and must not surface as a playback error.
        } catch (error: Throwable) {
            if (requestedSession == sessionId) fail(error.message ?: "Cloud TTS failed")
        }
    }

    private suspend fun loadOrGenerate(chunk: ReaderTtsChunk, requestedSession: Long): ByteArray? {
        val file = cacheFile(chunk)
        val cached = withContext(Dispatchers.Default) { readFile(file) }
        if (cached != null && cached.size > WAV_HEADER_SIZE) {
            state = state.copy(statusMessage = "Using cached audio")
            refreshCacheSummary()
            return cached
        }
        state = state.copy(statusMessage = "Preparing audio")
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
        withContext(Dispatchers.Default) { writeFileAtomically(file, wav) }
        refreshCacheSummary()
        return wav
    }

    private suspend fun playAudioAndWait(audio: ByteArray, requestedSession: Long): Boolean {
        ensureAudioSession()
        val completed = CompletableDeferred<Boolean>()
        playbackContinuation = completed
        val createdPlayer = AVAudioPlayer(data = audio.toNSData(), error = null)
        if (!createdPlayer.prepareToPlay()) {
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
        if (wantsPlayback) createdPlayer.play()
        try {
            val success = completed.await()
            if (!success && requestedSession == sessionId) {
                fail("Could not play cloud audio")
            }
            return success
        } finally {
            if (requestedSession == sessionId) {
                player?.stop()
                player = null
                playbackContinuation = null
            }
        }
    }

    private fun onAudioFinished(success: Boolean) {
        val continuation = playbackContinuation ?: return
        scope.launch {
            if (continuation.isActive) continuation.complete(success)
        }
    }

    private fun onAudioDecodeError() {
        playbackContinuation?.let { continuation ->
            if (continuation.isActive) continuation.complete(false)
        }
        fail("Could not decode cloud audio")
    }

    private fun handleAudioInterruption(interruption: IosTtsAudioInterruption) {
        when (interruption) {
            IosTtsAudioInterruption.Began -> {
                if (state.isPlaying) pause()
            }
            is IosTtsAudioInterruption.Ended -> {
                if (interruption.systemAllowsResume && state.isPaused) resume()
            }
            IosTtsAudioInterruption.OutputBecameUnavailable -> {
                if (hasActiveSession()) stop()
            }
        }
    }

    private fun restartAt(target: Int) {
        if (chunks.isEmpty()) return
        sessionId += 1
        playJob?.cancel()
        playJob = null
        playbackContinuation?.cancel()
        player?.stop()
        player = null
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
        state = state.copy(
            isPlaying = false,
            isLoading = false,
            isPaused = false,
            errorMessage = normalized,
        )
        player?.stop()
        playbackContinuation?.let { if (it.isActive) it.complete(false) }
    }

    private fun startGateError(): String? {
        if (!cloudTtsModeEnabled()) return "Choose Cloud TTS in AI settings first."
        if (!byokAvailable() && !workerAvailable()) {
            if (isSignedIn && authToken.isNullOrBlank()) return "Sign in again to use cloud TTS."
            return if (!isSignedIn) "Sign in to use cloud TTS, or configure a Gemini key."
            else "Cloud TTS is not configured."
        }
        if (!byokAvailable() && !isProUser && credits <= 0) return "Cloud TTS needs credits."
        return null
    }

    private fun byokAvailable(): Boolean = settings.geminiKey.isNotBlank() && settings.ttsModel == GEMINI_CLOUD_TTS_MODEL_ID

    private fun workerAvailable(): Boolean = isSignedIn && !authToken.isNullOrBlank() && workerUrl.isNotBlank()

    private fun cloudTtsModeEnabled(): Boolean = settings.ttsModel == GEMINI_CLOUD_TTS_MODEL_ID

    private fun hasActiveSession(): Boolean = chunks.isNotEmpty() && (state.isLoading || state.isPlaying || state.isPaused)

    private fun ensureAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
    }

    private fun deactivateAudioSession() {
        AVAudioSession.sharedInstance().setActive(false, error = null)
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
        val lower = raw.lowercase()
        return when {
            "insufficient_credits" in lower || "402" in lower -> "Out of credits."
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
    }
}

private class IosCloudAudioDelegate(
    private val onFinished: (Boolean) -> Unit,
    private val onDecodeError: () -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    @ObjCSignatureOverride
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onFinished(successfully)
    }

    @ObjCSignatureOverride
    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: platform.Foundation.NSError?) {
        onDecodeError()
    }
}

private fun iosCloudTtsCacheRoot(): String {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
    return if (caches.isNullOrBlank()) "TTS_Cache" else "$caches/TTS_Cache"
}

private fun Number?.orZero(): Long = this?.toLong() ?: 0L
