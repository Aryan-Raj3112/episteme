@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderExternalLookupService
import com.aryan.reader.shared.LocalTtsInterruptionAction
import com.aryan.reader.shared.LocalTtsInterruptionEvent
import com.aryan.reader.shared.LocalTtsInterruptionState
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.ios.loadIosEpubBook
import com.aryan.reader.shared.ios.IosTtsAudioInterruption
import com.aryan.reader.shared.ios.IosTtsAudioInterruptionMonitor
import com.aryan.reader.shared.reduce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSRange
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIReferenceLibraryViewController
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueCount
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueIndex
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.NSObject
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

@Composable
internal actual fun rememberSharedMobileEpubLoadState(book: BookItem): SharedMobileEpubLoadState {
    var state by remember(book.id, book.path) { mutableStateOf(SharedMobileEpubLoadState()) }
    LaunchedEffect(book.id, book.path) {
        state = SharedMobileEpubLoadState(isLoading = true)
        state = runCatching {
            withContext(Dispatchers.Default) { loadIosEpubBook(book) }
        }.fold(
            onSuccess = { SharedMobileEpubLoadState(isLoading = false, book = it) },
            onFailure = { error ->
                SharedMobileEpubLoadState(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not open this EPUB"
                )
            }
        )
    }
    return state
}

@Composable
internal actual fun SharedMobileEpubWebView(
    html: String,
    contentChunks: List<String>,
    appearanceScript: String,
    navigationScript: String?,
    navigationRequestId: Long,
    onBridgeMessage: (method: String, payload: String) -> Unit,
    modifier: Modifier
) {
    val latestBridgeMessage by rememberUpdatedState(onBridgeMessage)
    val coordinator = remember {
        IosEpubWebViewCoordinator { method, payload -> latestBridgeMessage(method, payload) }
    }
    coordinator.onBridgeMessage = { method, payload -> latestBridgeMessage(method, payload) }
    UIKitView(
        factory = coordinator::createWebView,
        modifier = modifier,
        update = { webView ->
            coordinator.update(
                webView = webView,
                html = html,
                contentChunks = contentChunks,
                appearanceScript = appearanceScript,
                navigationScript = navigationScript,
                navigationRequestId = navigationRequestId
            )
        },
        onRelease = coordinator::release,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true
        )
    )
}

internal actual fun openSharedMobileEpubExternalLink(url: String): Boolean {
    val normalized = if (url.trim().startsWith("//")) "https:${url.trim()}" else url.trim()
    val target = NSURL.URLWithString(normalized) ?: return false
    return UIApplication.sharedApplication.openURL(target)
}

internal object IosReaderLookupServices {
    var dictionary: ReaderExternalLookupService = ReaderExternalLookupService.SYSTEM
    var translate: ReaderExternalLookupService = ReaderExternalLookupService.GOOGLE_TRANSLATE
    var search: ReaderExternalLookupService = ReaderExternalLookupService.GOOGLE
}

internal actual fun openSharedMobileEpubLookup(
    action: ReaderExternalLookupAction,
    text: String
): Boolean {
    val query = text.trim()
    if (query.isEmpty()) return false
    val service = when (action) {
        ReaderExternalLookupAction.DICTIONARY -> IosReaderLookupServices.dictionary
        ReaderExternalLookupAction.TRANSLATE -> IosReaderLookupServices.translate
        ReaderExternalLookupAction.SEARCH -> IosReaderLookupServices.search
    }
    if (action == ReaderExternalLookupAction.DICTIONARY && service == ReaderExternalLookupService.SYSTEM) {
        val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
        presenter.presentViewController(
            UIReferenceLibraryViewController(term = query),
            animated = true,
            completion = null
        )
        return true
    }
    return openSharedMobileEpubExternalLink(externalLookupUrl(action, query, service))
}

internal actual fun shareSharedMobileEpubImage(bytes: ByteArray, fileName: String): Boolean {
    if (bytes.isEmpty()) return false
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "image.png" }
    val path = NSTemporaryDirectory() + safeName
    val file = fopen(path, "wb") ?: return false
    val written = try {
        bytes.usePinned { pinned -> fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file) }
    } finally {
        fclose(file)
    }
    if (written != bytes.size.toULong()) return false
    val url = NSURL.fileURLWithPath(path)
    val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
    val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    controller.modalPresentationStyle = UIModalPresentationFullScreen
    presenter.presentViewController(controller, animated = true, completion = null)
    return true
}

@Composable
internal actual fun rememberSharedMobileEpubLocalTts(): SharedMobileEpubLocalTts {
    val controller = remember { IosSharedMobileEpubLocalTts() }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}

private const val IosReaderTtsRateKey = "reader.tts.speechRate"
private const val IosReaderTtsPitchKey = "reader.tts.pitch"
private const val IosReaderTtsVoiceKey = "reader.tts.voiceIdentifier"

private fun NSUserDefaults.readerTtsFloat(key: String, fallback: Float): Float {
    return if (objectForKey(key) == null) fallback else doubleForKey(key).toFloat()
}

private class IosSharedMobileEpubLocalTts : SharedMobileEpubLocalTts {
    private val preferences = NSUserDefaults.standardUserDefaults
    private val synthesizer = AVSpeechSynthesizer()
    private val previewSynthesizer = AVSpeechSynthesizer()
    private val delegate = IosSharedMobileEpubSpeechDelegate(
        onStarted = ::utteranceStarted,
        onPaused = ::utterancePaused,
        onContinued = ::utteranceContinued,
        onFinished = ::utteranceFinished,
        onCancelled = ::utteranceCancelled,
        onWillSpeakRange = ::utteranceWillSpeakRange
    )
    override var state by mutableStateOf(SharedMobileEpubLocalTtsState.IDLE)
    override var isSessionActive by mutableStateOf(false)
        private set
    override var progress by mutableStateOf(ReaderTtsProgress())
        private set
    override var completionCount by mutableStateOf(0L)
        private set
    override var errorMessage by mutableStateOf<String?>(null)
        private set
    override var speechRate by mutableStateOf(
        preferences.readerTtsFloat(IosReaderTtsRateKey, 1f).coerceIn(0.5f, 3f)
    )
        private set
    override var speechPitch by mutableStateOf(
        preferences.readerTtsFloat(IosReaderTtsPitchKey, 1f).coerceIn(0.5f, 2f)
    )
        private set
    override val availableVoices: List<SharedMobileEpubVoice> =
        AVSpeechSynthesisVoice.speechVoices()
            .mapNotNull { it as? AVSpeechSynthesisVoice }
            .map { voice ->
                SharedMobileEpubVoice(
                    identifier = voice.identifier,
                    name = voice.name,
                    language = voice.language
                )
            }
            .sortedWith(compareBy(SharedMobileEpubVoice::language, SharedMobileEpubVoice::name))
    override var selectedVoiceIdentifier by mutableStateOf(
        preferences.stringForKey(IosReaderTtsVoiceKey)
            ?.takeIf { saved -> availableVoices.any { it.identifier == saved } }
    )
        private set
    private var bookTitle: String = ""
    private var chunks: List<ReaderTtsChunk> = emptyList()
    private var currentChunkIndex = -1
    private var sessionId = 0L
    private var activeUtterance: AVSpeechUtterance? = null
    private var activeSpokenOffset = 0
    private var activeUtteranceBaseOffset = 0
    private var wantsPlayback = true
    private var audioSessionActive = false
    private var interruptionState = LocalTtsInterruptionState()
    private val interruptionMonitor = IosTtsAudioInterruptionMonitor(::handleAudioInterruption)

    init {
        synthesizer.delegate = delegate
        installRemoteCommands()
    }

    override fun prepare() {
        if (!audioSessionActive) {
            configureAudioSession(active = true)
            audioSessionActive = true
        }
        isSessionActive = true
    }

    override fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String?,
        startChunkIndex: Int,
        playWhenReady: Boolean
    ) {
        val readableChunks = chunks.filter { it.spokenText.isNotBlank() }
        if (readableChunks.isEmpty()) return
        errorMessage = null
        invalidateActiveUtterance()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        this.chunks = readableChunks
        this.bookTitle = bookTitle
        currentChunkIndex = startChunkIndex.coerceIn(0, readableChunks.lastIndex) - 1
        sessionId += 1
        wantsPlayback = playWhenReady
        prepare()
        advance()
    }

    override fun pause() {
        interruptionState = LocalTtsInterruptionState()
        pauseInternal()
    }

    private fun pauseInternal() {
        wantsPlayback = false
        synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        if (activeUtterance != null) state = SharedMobileEpubLocalTtsState.PAUSED
        updateNowPlaying()
    }

    override fun resume() {
        interruptionState = LocalTtsInterruptionState()
        wantsPlayback = true
        synthesizer.continueSpeaking()
        if (activeUtterance != null) state = SharedMobileEpubLocalTtsState.SPEAKING
        updateNowPlaying()
    }

    override fun skipPrevious() = moveBy(-1)

    override fun skipNext() = moveBy(1)

    override fun setSpeechParameters(rate: Float, pitch: Float) {
        speechRate = rate.coerceIn(0.5f, 3f)
        speechPitch = pitch.coerceIn(0.5f, 2f)
        preferences.setDouble(speechRate.toDouble(), IosReaderTtsRateKey)
        preferences.setDouble(speechPitch.toDouble(), IosReaderTtsPitchKey)
        restartCurrentUtterance()
        updateNowPlaying()
    }

    override fun setVoice(identifier: String?) {
        selectedVoiceIdentifier = identifier
            ?.takeIf { candidate -> availableVoices.any { it.identifier == candidate } }
        if (selectedVoiceIdentifier == null) {
            preferences.removeObjectForKey(IosReaderTtsVoiceKey)
        } else {
            preferences.setObject(selectedVoiceIdentifier, IosReaderTtsVoiceKey)
        }
        restartCurrentUtterance()
    }

    override fun previewVoice(identifier: String?) {
        previewSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance(
            string = "This is a sample of the selected reading voice."
        ).apply {
            rate = (0.5f * speechRate).coerceIn(0.1f, 1f)
            pitchMultiplier = speechPitch
            identifier
                ?.let(AVSpeechSynthesisVoice::voiceWithIdentifier)
                ?.let { voice = it }
        }
        previewSynthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        interruptionState = LocalTtsInterruptionState()
        sessionId += 1
        errorMessage = null
        invalidateActiveUtterance()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        chunks = emptyList()
        currentChunkIndex = -1
        wantsPlayback = false
        isSessionActive = false
        progress = ReaderTtsProgress()
        state = SharedMobileEpubLocalTtsState.IDLE
        clearNowPlaying()
        if (audioSessionActive) {
            configureAudioSession(active = false)
            audioSessionActive = false
        }
    }

    private fun handleAudioInterruption(interruption: IosTtsAudioInterruption) {
        val event = when (interruption) {
            IosTtsAudioInterruption.Began -> LocalTtsInterruptionEvent.Began(
                playbackWasActive = state == SharedMobileEpubLocalTtsState.SPEAKING
            )
            is IosTtsAudioInterruption.Ended -> LocalTtsInterruptionEvent.Ended(
                systemAllowsResume = interruption.systemAllowsResume
            )
        }
        val transition = interruptionState.reduce(event)
        interruptionState = transition.state
        when (transition.action) {
            LocalTtsInterruptionAction.NONE -> Unit
            LocalTtsInterruptionAction.PAUSE -> pauseInternal()
            LocalTtsInterruptionAction.RESUME -> {
                configureAudioSession(active = true)
                resume()
            }
        }
    }

    private fun advance() {
        currentChunkIndex += 1
        speakCurrentChunk()
    }

    private fun moveBy(offset: Int) {
        if (chunks.isEmpty()) return
        val target = (currentChunkIndex + offset).coerceIn(0, chunks.lastIndex)
        if (target == currentChunkIndex) return
        invalidateActiveUtterance()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        currentChunkIndex = target
        speakCurrentChunk()
    }

    private fun speakCurrentChunk(
        spokenText: String? = null,
        sourceOffset: Int = 0
    ) {
        val chunk = chunks.getOrNull(currentChunkIndex)
        if (chunk == null) {
            errorMessage = null
            chunks = emptyList()
            currentChunkIndex = -1
            progress = ReaderTtsProgress()
            state = SharedMobileEpubLocalTtsState.IDLE
            completionCount += 1
            clearNowPlaying()
            configureAudioSession(active = false)
            return
        }
        // Keep the reader controls responsive even when a system voice starts slowly.
        // AVSpeechSynthesizer will still correct this through didStart/didPause callbacks.
        state = if (wantsPlayback) SharedMobileEpubLocalTtsState.SPEAKING else SharedMobileEpubLocalTtsState.PAUSED
        progress = ReaderTtsProgress(
            sessionId = sessionId,
            chunks = chunks,
            currentChunkIndex = currentChunkIndex
        )
        updateNowPlaying()
        activeSpokenOffset = sourceOffset
        activeUtteranceBaseOffset = sourceOffset
        val utterance = AVSpeechUtterance(string = spokenText ?: chunk.spokenText).apply {
            this.rate = (0.5f * speechRate).coerceIn(0.1f, 1f)
            pitchMultiplier = speechPitch
            selectedVoiceIdentifier
                ?.let(AVSpeechSynthesisVoice::voiceWithIdentifier)
                ?.let { voice = it }
        }
        activeUtterance = utterance
        synthesizer.speakUtterance(utterance)
    }

    private fun invalidateActiveUtterance() {
        activeUtterance = null
    }

    private fun restartCurrentUtterance() {
        val chunkText = chunks.getOrNull(currentChunkIndex)?.spokenText.orEmpty()
        val restartOffset = activeSpokenOffset.coerceIn(0, chunkText.length)
        if (activeUtterance != null && restartOffset < chunkText.length) {
            invalidateActiveUtterance()
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
            speakCurrentChunk(
                spokenText = chunkText.substring(restartOffset),
                sourceOffset = restartOffset
            )
        }
    }

    private fun isActive(utterance: AVSpeechUtterance): Boolean =
        activeUtterance?.isEqual(utterance) == true

    private fun utteranceStarted(utterance: AVSpeechUtterance) {
        if (!isActive(utterance)) return
        if (wantsPlayback) {
            state = SharedMobileEpubLocalTtsState.SPEAKING
        } else {
            synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
            state = SharedMobileEpubLocalTtsState.PAUSED
        }
        updateNowPlaying()
    }

    private fun utterancePaused(utterance: AVSpeechUtterance) {
        if (!isActive(utterance)) return
        state = SharedMobileEpubLocalTtsState.PAUSED
        updateNowPlaying()
    }

    private fun utteranceContinued(utterance: AVSpeechUtterance) {
        if (!isActive(utterance)) return
        state = SharedMobileEpubLocalTtsState.SPEAKING
        updateNowPlaying()
    }

    private fun utteranceFinished(utterance: AVSpeechUtterance) {
        if (!isActive(utterance)) return
        activeUtterance = null
        advance()
    }

    private fun utteranceCancelled(utterance: AVSpeechUtterance) {
        if (!isActive(utterance)) return
        activeUtterance = null
        if (wantsPlayback && chunks.isNotEmpty()) {
            errorMessage = "Text-to-speech was interrupted."
        }
        if (chunks.isEmpty()) state = SharedMobileEpubLocalTtsState.IDLE
        updateNowPlaying()
    }

    private fun utteranceWillSpeakRange(utterance: AVSpeechUtterance, range: CValue<NSRange>) {
        if (!isActive(utterance)) return
        activeSpokenOffset = activeUtteranceBaseOffset + range.useContents { location.toInt() }
    }

    private fun configureAudioSession(active: Boolean) {
        val audioSession = AVAudioSession.sharedInstance()
        if (active) {
            audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
        }
        audioSession.setActive(active = active, error = null)
    }

    private fun installRemoteCommands() {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.playCommand.addTargetWithHandler {
            resume()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.pauseCommand.addTargetWithHandler {
            pause()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.stopCommand.addTargetWithHandler {
            stop()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.nextTrackCommand.addTargetWithHandler {
            moveBy(1)
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.previousTrackCommand.addTargetWithHandler {
            moveBy(-1)
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    override fun release() {
        stop()
        interruptionMonitor.close()
        previewSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer.delegate = null
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.playCommand.removeTarget(null)
        commands.pauseCommand.removeTarget(null)
        commands.stopCommand.removeTarget(null)
        commands.nextTrackCommand.removeTarget(null)
        commands.previousTrackCommand.removeTarget(null)
    }

    private fun updateNowPlaying() {
        val chunk = progress.currentChunk
        if (chunk == null) return
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf(
            MPMediaItemPropertyTitle to bookTitle,
            MPMediaItemPropertyArtist to chunk.chapterTitle.ifBlank { "Reading" },
            MPMediaItemPropertyAlbumTitle to "Part ${currentChunkIndex + 1} of ${chunks.size}",
            MPNowPlayingInfoPropertyPlaybackQueueIndex to currentChunkIndex,
            MPNowPlayingInfoPropertyPlaybackQueueCount to chunks.size,
            MPNowPlayingInfoPropertyPlaybackRate to if (state == SharedMobileEpubLocalTtsState.SPEAKING) speechRate.toDouble() else 0.0
        )
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.previousTrackCommand.enabled = currentChunkIndex > 0
        commands.nextTrackCommand.enabled = currentChunkIndex in 0 until chunks.lastIndex
    }

    private fun clearNowPlaying() {
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.previousTrackCommand.enabled = false
        commands.nextTrackCommand.enabled = false
    }
}

private class IosSharedMobileEpubSpeechDelegate(
    private val onStarted: (AVSpeechUtterance) -> Unit,
    private val onPaused: (AVSpeechUtterance) -> Unit,
    private val onContinued: (AVSpeechUtterance) -> Unit,
    private val onFinished: (AVSpeechUtterance) -> Unit,
    private val onCancelled: (AVSpeechUtterance) -> Unit,
    private val onWillSpeakRange: (AVSpeechUtterance, CValue<NSRange>) -> Unit
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didStartSpeechUtterance: AVSpeechUtterance
    ) {
        onStarted(didStartSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance
    ) {
        onFinished(didFinishSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didPauseSpeechUtterance: AVSpeechUtterance
    ) {
        onPaused(didPauseSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didContinueSpeechUtterance: AVSpeechUtterance
    ) {
        onContinued(didContinueSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didCancelSpeechUtterance: AVSpeechUtterance
    ) {
        onCancelled(didCancelSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        willSpeakRangeOfSpeechString: CValue<NSRange>,
        utterance: AVSpeechUtterance
    ) {
        onWillSpeakRange(utterance, willSpeakRangeOfSpeechString)
    }
}

private class IosEpubWebViewCoordinator(
    var onBridgeMessage: (String, String) -> Unit
) {
    private val messageHandler = IosEpubScriptMessageHandler(::handleBridgeMessage)
    private val navigationDelegate = IosEpubNavigationDelegate(::documentDidFinishLoading)
    private var activeWebView: WKWebView? = null
    private var contentChunks: List<String> = emptyList()
    private var loadedHtmlHash: Int? = null
    private var loadedHtmlLength: Int = -1
    private var appliedAppearanceHash: Int? = null
    private var appliedNavigationRequestId: Long = Long.MIN_VALUE
    private var latestAppearanceScript: String = ""
    private var latestNavigationScript: String? = null
    private var latestNavigationRequestId: Long = Long.MIN_VALUE

    fun createWebView(): WKWebView {
        val contentController = WKUserContentController()
        contentController.addUserScript(
            WKUserScript(
                source = IosEpubBridgeBootstrapScript,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = false
            )
        )
        contentController.addScriptMessageHandler(messageHandler, name = IosEpubBridgeName)
        val configuration = WKWebViewConfiguration().apply {
            userContentController = contentController
            defaultWebpagePreferences.allowsContentJavaScript = true
        }
        return WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
            activeWebView = this
            navigationDelegate = this@IosEpubWebViewCoordinator.navigationDelegate
            opaque = false
            backgroundColor = UIColor.clearColor
            scrollView.backgroundColor = UIColor.clearColor
            scrollView.bounces = true
            scrollView.alwaysBounceVertical = true
            scrollView.alwaysBounceHorizontal = false
            scrollView.showsHorizontalScrollIndicator = false
        }
    }

    fun update(
        webView: WKWebView,
        html: String,
        contentChunks: List<String>,
        appearanceScript: String,
        navigationScript: String?,
        navigationRequestId: Long
    ) {
        activeWebView = webView
        this.contentChunks = contentChunks
        latestAppearanceScript = appearanceScript
        latestNavigationScript = navigationScript
        latestNavigationRequestId = navigationRequestId
        val htmlHash = html.hashCode()
        if (loadedHtmlHash != htmlHash || loadedHtmlLength != html.length) {
            loadedHtmlHash = htmlHash
            loadedHtmlLength = html.length
            appliedAppearanceHash = null
            appliedNavigationRequestId = Long.MIN_VALUE
            webView.loadHTMLString(html, baseURL = null)
            return
        }

        val appearanceHash = appearanceScript.hashCode()
        if (appliedAppearanceHash != appearanceHash) {
            appliedAppearanceHash = appearanceHash
            webView.evaluateJavaScript(appearanceScript, completionHandler = null)
        }
        if (
            navigationScript != null &&
            appliedNavigationRequestId != navigationRequestId
        ) {
            appliedNavigationRequestId = navigationRequestId
            webView.evaluateJavaScript(navigationScript, completionHandler = null)
        }
    }

    private fun documentDidFinishLoading(webView: WKWebView) {
        if (latestAppearanceScript.isNotBlank()) {
            webView.evaluateJavaScript(latestAppearanceScript, completionHandler = null)
            appliedAppearanceHash = latestAppearanceScript.hashCode()
        }
        latestNavigationScript?.let { script ->
            webView.evaluateJavaScript(script, completionHandler = null)
            appliedNavigationRequestId = latestNavigationRequestId
        }
    }

    private fun handleBridgeMessage(method: String, payload: String) {
        if (method == "readerChunkRequested") {
            val index = IosEpubChunkIndexRegex.find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return
            val chunk = contentChunks.getOrNull(index) ?: return
            activeWebView?.evaluateJavaScript(
                "window.readerVirtualization && window.readerVirtualization.provideChunk($index, ${JsonPrimitive(chunk)});",
                completionHandler = null
            )
            return
        }
        onBridgeMessage(method, payload)
    }

    fun release(webView: WKWebView) {
        webView.stopLoading()
        webView.navigationDelegate = null
        webView.configuration.userContentController.removeScriptMessageHandlerForName(IosEpubBridgeName)
        activeWebView = null
        contentChunks = emptyList()
        loadedHtmlHash = null
        loadedHtmlLength = -1
    }
}

private class IosEpubNavigationDelegate(
    private val onFinished: (WKWebView) -> Unit
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onFinished(webView)
    }
}

private class IosEpubScriptMessageHandler(
    private val callback: (String, String) -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val raw = didReceiveScriptMessage.body as? String ?: return
        val separator = raw.indexOf('\n')
        if (separator <= 0) return
        callback(raw.substring(0, separator), raw.substring(separator + 1))
    }
}

private const val IosEpubBridgeName = "reader"
private val IosEpubChunkIndexRegex = Regex("\\\"index\\\"\\s*:\\s*(\\d+)")

private val IosEpubBridgeBootstrapScript = """
    (function () {
      function post(method, payload) {
        try {
          window.webkit.messageHandlers.$IosEpubBridgeName.postMessage(String(method || '') + '\n' + String(payload || '{}'));
        } catch (_) {}
      }
      window.kmpJsBridge = {
        callNative: function (method, payload) { post(method, payload); }
      };
      window.readerDisableLinkFallback = true;
      if (!window.readerIosPointerBridgeInstalled) {
        window.readerIosPointerBridgeInstalled = true;
        var start = null;
        document.addEventListener('touchstart', function (event) {
          if (!event.touches || event.touches.length !== 1) { start = null; return; }
          var touch = event.touches[0];
          var root = document.scrollingElement || document.documentElement;
          var maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
          start = {
            x: touch.clientX,
            y: touch.clientY,
            at: Date.now(),
            atTop: window.scrollY <= 2,
            atBottom: window.scrollY >= maxScroll - 2
          };
        }, { passive: true, capture: true });
        document.addEventListener('touchmove', function (event) {
          if (!start || !event.touches || event.touches.length !== 1) return;
          if (window.readerIosPullEnabled === false) return;
          var touch = event.touches[0];
          var dx = touch.clientX - start.x;
          var dy = touch.clientY - start.y;
          if (Math.abs(dy) <= Math.abs(dx) * 1.25) return;
          var multiplier = Math.max(0.5, Math.min(2.0, Number(window.readerIosPullMultiplier || 1)));
          var threshold = 100 * multiplier;
          if (start.atTop && dy > 0) {
            post('readerChapterPull', JSON.stringify({ direction: 'previous', progress: Math.min(1.25, dy / threshold) }));
          } else if (start.atBottom && dy < 0) {
            post('readerChapterPull', JSON.stringify({ direction: 'next', progress: Math.min(1.25, -dy / threshold) }));
          }
        }, { passive: true, capture: true });
        document.addEventListener('touchend', function (event) {
          if (!start || !event.changedTouches || event.changedTouches.length !== 1) { start = null; return; }
          var touch = event.changedTouches[0];
          var dx = touch.clientX - start.x;
          var dy = touch.clientY - start.y;
          var elapsed = Date.now() - start.at;
          var startedAtTop = start.atTop;
          var startedAtBottom = start.atBottom;
          start = null;
          var selection = window.getSelection && window.getSelection();
          if (selection && selection.toString().trim()) return;
          var target = event.target;
          if (target && target.closest && target.closest('a,button,input,textarea,select,#reader-selection-menu,.reader-selection-handle')) return;
          var multiplier = Math.max(0.5, Math.min(2.0, Number(window.readerIosPullMultiplier || 1)));
          var threshold = 100 * multiplier;
          post('readerChapterPull', JSON.stringify({ direction: dy >= 0 ? 'previous' : 'next', progress: 0 }));
          if (window.readerIosSeamlessChapter === true && Math.abs(dy) >= 18 && Math.abs(dy) > Math.abs(dx) * 1.25) {
            if (startedAtTop && dy > 0) {
              post('readerChapterBoundary', JSON.stringify({ direction: 'previous' }));
              return;
            }
            if (startedAtBottom && dy < 0) {
              post('readerChapterBoundary', JSON.stringify({ direction: 'next' }));
              return;
            }
          }
          if (window.readerIosPullEnabled !== false && elapsed <= 1400 && Math.abs(dy) >= threshold && Math.abs(dy) > Math.abs(dx) * 1.25) {
            if (startedAtTop && dy > 0) {
              post('readerChapterBoundary', JSON.stringify({ direction: 'previous' }));
              return;
            }
            if (startedAtBottom && dy < 0) {
              post('readerChapterBoundary', JSON.stringify({ direction: 'next' }));
              return;
            }
          }
          if ((dx * dx + dy * dy) > 100) {
            post('readerDragActivity', '{}');
            return;
          }
          if (elapsed > 650) return;
          post('readerPointerActivity', '{}');
        }, { passive: true, capture: true });
        document.addEventListener('touchcancel', function () {
          start = null;
          post('readerChapterPull', JSON.stringify({ direction: 'next', progress: 0 }));
        }, { passive: true, capture: true });
      }
    })();
""".trimIndent()
