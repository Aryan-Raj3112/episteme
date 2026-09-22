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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderExternalLookupService
import com.aryan.reader.shared.isReaderExternalHref
import com.aryan.reader.shared.normalizeReaderHref
import com.aryan.reader.shared.LocalTtsInterruptionAction
import com.aryan.reader.shared.LocalTtsInterruptionEvent
import com.aryan.reader.shared.LocalTtsInterruptionState
import com.aryan.reader.shared.externalLookupUrl
import com.aryan.reader.shared.ios.loadIosEpubBook
import com.aryan.reader.shared.ios.IosEpubResourceStore
import com.aryan.reader.shared.ios.IosTtsAudioInterruption
import com.aryan.reader.shared.ios.IosTtsAudioInterruptionMonitor
import com.aryan.reader.shared.opds.SharedOpdsStreamRequest
import com.aryan.reader.shared.reader.SharedEpubResourceScheme
import com.aryan.reader.shared.reader.parseSharedEpubResourceUrl
import com.aryan.reader.shared.reader.sharedEpubResourceMimeType
import com.aryan.reader.shared.reader.sharedEpubOpenTrace
import com.aryan.reader.shared.reader.sharedEpubOpenTraceElapsedMs
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMark
import com.aryan.reader.shared.reader.sharedEpubOpenTraceMs
import com.aryan.reader.shared.reduce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSRange
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithLength
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIReferenceLibraryViewController
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityEnhanced
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityPremium
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
import platform.posix.memcpy

@Composable
internal actual fun rememberSharedMobileEpubLoadState(book: BookItem): SharedMobileEpubLoadState {
    var state by remember(book.id, book.path) { mutableStateOf(SharedMobileEpubLoadState()) }
    LaunchedEffect(book.id, book.path) {
        val traceMark = sharedEpubOpenTraceMark()
        sharedEpubOpenTrace { "loadState start id=${book.id} type=${book.type}" }
        state = SharedMobileEpubLoadState(isLoading = true)
        state = runCatching {
            withContext(Dispatchers.Default) { loadIosEpubBook(book) }
        }.fold(
            onSuccess = {
                sharedEpubOpenTrace { "loadState success id=${book.id} chapters=${it.chapters.size} ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(traceMark))}" }
                SharedMobileEpubLoadState(isLoading = false, book = it)
            },
            onFailure = { error ->
                sharedEpubOpenTrace { "loadState failed id=${book.id} error=${error.message} ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(traceMark))}" }
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
    highlightsApplyScript: String,
    onBridgeMessage: (method: String, payload: String) -> Unit,
    positionController: SharedMobileEpubWebViewController?,
    streamPageLoader: SharedMobileEpubStreamPageLoader?,
    streamPageUnavailableLabel: String,
    contentBackgroundArgb: Long,
    modifier: Modifier
) {
    val latestBridgeMessage by rememberUpdatedState(onBridgeMessage)
    val coordinator = remember(streamPageLoader) {
        IosEpubWebViewCoordinator(
            streamPageLoader = streamPageLoader,
            streamPageUnavailableLabel = streamPageUnavailableLabel,
            onBridgeMessage = { method, payload -> latestBridgeMessage(method, payload) },
        )
    }
    coordinator.onBridgeMessage = { method, payload -> latestBridgeMessage(method, payload) }
    coordinator.streamPageLoader = streamPageLoader
    coordinator.streamPageUnavailableLabel = streamPageUnavailableLabel
    DisposableEffect(positionController, coordinator) {
        positionController?.attach { callback -> coordinator.captureCurrentLocator(callback) }
        onDispose { positionController?.detach() }
    }
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
                navigationRequestId = navigationRequestId,
                highlightsApplyScript = highlightsApplyScript,
                contentBackgroundArgb = contentBackgroundArgb
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
    val normalized = normalizeReaderHref(url)
    val target = NSURL.URLWithString(normalized) ?: return false
    return UIApplication.sharedApplication.openURL(target)
}

// iPhone corner radii (~13-16pt) curve into the benchmark 16.dp side padding,
// so the edge-pinned clock/percentage gain room that safeDrawing cannot
// provide (it reports 0 horizontally in portrait).
internal actual val sharedMobileEpubPageInfoCornerClearance: Dp = 8.dp

// With menus hidden the bar would sit flush at the bottom edge, inside the
// corner curve. Always lifting it above the home-indicator zone keeps the
// clock/percentage on straight screen edges; the bar background still extends
// to the bottom edge underneath.
internal actual val sharedMobileEpubPageInfoAlwaysApplyBottomSafeInset: Boolean = true

// The tinted info-bar color renders as a visible step against the page, making
// the bar look like a floating strip. The exact opaque reader background
// continues the page seamlessly (texture overlay is unchanged).
internal actual val sharedMobileEpubPageInfoMatchesReaderBackground: Boolean = true

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
private const val IosReaderTtsSampleTextKey = "reader.tts.previewSampleText"
private const val IosReaderTtsFavoritesKey = "reader.tts.favoriteVoices"

private fun NSUserDefaults.readerTtsFloat(key: String, fallback: Float): Float {
    return if (objectForKey(key) == null) fallback else doubleForKey(key).toFloat()
}

/**
 * Android benchmark parity: the shared sheet groups by localized display
 * name (e.g. "English (United States)"), not the raw BCP-47 tag ("en-US").
 */
private fun iosTtsLanguageDisplayName(languageTag: String): String {
    val trimmed = languageTag.trim()
    if (trimmed.isBlank()) return ""
    val localeIdentifier = trimmed.replace('-', '_')
    val display = runCatching {
        val preferred = (NSBundle.mainBundle.preferredLocalizations.firstOrNull() as? String)
            ?.replace('-', '_')
            ?.takeIf { it.isNotBlank() }
            ?: "en"
        NSLocale(localeIdentifier = preferred)
            .displayNameForKey(NSLocaleIdentifier, localeIdentifier)
    }.getOrNull()?.takeIf { it.isNotBlank() }
    return display ?: trimmed
}

private fun iosTtsFavoriteVoices(preferences: NSUserDefaults): Set<String> =
    runCatching { preferences.stringArrayForKey(IosReaderTtsFavoritesKey) as? List<*> }
        .getOrNull()
        .orEmpty()
        .mapNotNull { it as? String }
        .filter { it.isNotBlank() }
        .toSet()

private fun iosTtsVoiceQuality(voice: AVSpeechSynthesisVoice): SharedMobileEpubVoiceQuality {
    val quality = runCatching { voice.quality }.getOrNull()
    if (quality == AVSpeechSynthesisVoiceQualityPremium) return SharedMobileEpubVoiceQuality.PREMIUM
    if (quality == AVSpeechSynthesisVoiceQualityEnhanced) return SharedMobileEpubVoiceQuality.ENHANCED
    // Identifier heuristic: same-name voices (e.g. two "Nicky" entries) only
    // differ by compact vs premium bundle once downloaded.
    val identifier = runCatching { voice.identifier }.getOrNull().orEmpty().lowercase()
    return when {
        identifier.contains("premium") -> SharedMobileEpubVoiceQuality.PREMIUM
        identifier.contains("enhanced") -> SharedMobileEpubVoiceQuality.ENHANCED
        else -> SharedMobileEpubVoiceQuality.STANDARD
    }
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
    private var previewSampleTextState by mutableStateOf(
        effectiveSharedMobileTtsSampleText(preferences.stringForKey(IosReaderTtsSampleTextKey))
    )
    override val previewSampleText: String get() = previewSampleTextState
    private var favoriteVoiceState by mutableStateOf(iosTtsFavoriteVoices(preferences))
    override val favoriteVoiceIdentifiers: Set<String> get() = favoriteVoiceState
    override val availableVoices: List<SharedMobileEpubVoice> =
        AVSpeechSynthesisVoice.speechVoices()
            .mapNotNull { it as? AVSpeechSynthesisVoice }
            .map { voice ->
                val languageTag = voice.language.orEmpty()
                SharedMobileEpubVoice(
                    identifier = voice.identifier,
                    name = voice.name,
                    language = iosTtsLanguageDisplayName(languageTag).ifBlank { languageTag },
                    languageTag = languageTag,
                    quality = iosTtsVoiceQuality(voice),
                )
            }
            .sortedForTtsDisplay()
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

    override fun setPreviewSampleText(text: String) {
        val sanitized = sanitizeSharedMobileTtsSampleText(text)
        preferences.setObject(sanitized, IosReaderTtsSampleTextKey)
        previewSampleTextState = effectiveSharedMobileTtsSampleText(sanitized)
    }

    override fun toggleFavoriteVoice(identifier: String) {
        if (identifier.isBlank()) return
        favoriteVoiceState = toggleSharedMobileTtsVoiceFavorite(favoriteVoiceState, identifier)
        preferences.setObject(favoriteVoiceState.toList(), IosReaderTtsFavoritesKey)
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
            string = previewSampleText
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
            IosTtsAudioInterruption.OutputBecameUnavailable ->
                LocalTtsInterruptionEvent.OutputBecameNoisy(
                    playbackWasActive = state == SharedMobileEpubLocalTtsState.SPEAKING
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
    var streamPageLoader: SharedMobileEpubStreamPageLoader?,
    var streamPageUnavailableLabel: String,
    var onBridgeMessage: (String, String) -> Unit
) {
    private val messageHandler = IosEpubScriptMessageHandler(::handleBridgeMessage)
    private val navigationDelegate = IosEpubNavigationDelegate(
        onFinished = ::documentDidFinishLoading,
        onFailed = ::documentDidFailLoading,
        onTerminated = ::documentProcessTerminated,
        onDecidePolicy = ::shouldCancelNavigation,
    )
    private val streamPageSchemeHandler = IosOpdsStreamPageSchemeHandler(
        loader = { streamPageLoader },
        unavailablePageLabel = { streamPageUnavailableLabel },
    )
    private val epubResourceSchemeHandler = IosEpubResourceSchemeHandler()
    private var activeWebView: WKWebView? = null
    private var contentChunks: List<String> = emptyList()
    private var loadedHtmlHash: Int? = null
    private var loadedHtmlLength: Int = -1
    private var lastHtml: String? = null
    private var appliedAppearanceHash: Int? = null
    private var appliedHighlightsHash: Int? = null
    private var appliedNavigationRequestId: Long = Long.MIN_VALUE
    private var appliedBackgroundArgb: Long? = null
    private var latestAppearanceScript: String = ""
    private var latestNavigationScript: String? = null
    private var latestNavigationRequestId: Long = Long.MIN_VALUE
    private var latestHighlightsApplyScript: String = ""
    private var htmlLoadStartMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var reportedFirstPosition: Boolean = false

    fun createWebView(): WKWebView {
        sharedEpubOpenTrace { "webview create" }
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
            setURLSchemeHandler(
                streamPageSchemeHandler,
                forURLScheme = SharedOpdsStreamRequest.ResourceScheme,
            )
            setURLSchemeHandler(
                epubResourceSchemeHandler,
                forURLScheme = SharedEpubResourceScheme,
            )
        }
        return WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
            activeWebView = this
            navigationDelegate = this@IosEpubWebViewCoordinator.navigationDelegate
            opaque = true
            backgroundColor = UIColor.whiteColor
            scrollView.backgroundColor = UIColor.whiteColor
            // Compose owns the safe area (PageInfo reserve + bar insets) and the
            // HTML owns the home-indicator bottom clearance, so WebKit must not
            // add its own automatic bottom inset. That inset lifts the chapter
            // end above the PageInfo bar and leaves a gap that only vanishes
            // after the first scroll.
            scrollView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
            scrollView.automaticallyAdjustsScrollIndicatorInsets = false
            scrollView.contentInset = UIEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
            scrollView.scrollIndicatorInsets = UIEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
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
        navigationRequestId: Long,
        highlightsApplyScript: String,
        contentBackgroundArgb: Long
    ) {
        activeWebView = webView
        this.contentChunks = contentChunks
        if (appliedBackgroundArgb != contentBackgroundArgb) {
            appliedBackgroundArgb = contentBackgroundArgb
            val nativeBackground = contentBackgroundArgb.toIosReaderColor()
            webView.opaque = true
            webView.backgroundColor = nativeBackground
            webView.scrollView.backgroundColor = nativeBackground
        }
        latestAppearanceScript = appearanceScript
        latestNavigationScript = navigationScript
        latestNavigationRequestId = navigationRequestId
        latestHighlightsApplyScript = highlightsApplyScript
        val htmlHash = html.hashCode()
        if (loadedHtmlHash != htmlHash || loadedHtmlLength != html.length) {
            // Android parity: highlight changes never reach here — the document
            // renders without highlights and they are applied in place through
            // the highlights payload, so a reload only happens for real document
            // changes. Like Android, a reload lands via the navigation script;
            // no async scroll capture (it raced the reload on big chapters).
            loadedHtmlHash = htmlHash
            loadedHtmlLength = html.length
            lastHtml = html
            appliedAppearanceHash = null
            appliedHighlightsHash = null
            appliedNavigationRequestId = Long.MIN_VALUE
            htmlLoadStartMark = sharedEpubOpenTraceMark()
            reportedFirstPosition = false
            sharedEpubOpenTrace { "webview loadHTML start chars=${html.length} chunks=${contentChunks.size}" }
            webView.loadHTMLString(html, baseURL = null)
            return
        }

        val appearanceHash = appearanceScript.hashCode()
        if (appliedAppearanceHash != appearanceHash) {
            appliedAppearanceHash = appearanceHash
            evaluateReaderScript(webView, appearanceScript, "appearance")
        }
        val highlightsHash = highlightsApplyScript.hashCode()
        if (highlightsApplyScript.isNotBlank() && appliedHighlightsHash != highlightsHash) {
            appliedHighlightsHash = highlightsHash
            evaluateReaderScript(webView, highlightsApplyScript, "highlights")
        }
        if (
            navigationScript != null &&
            appliedNavigationRequestId != navigationRequestId
        ) {
            appliedNavigationRequestId = navigationRequestId
            evaluateReaderScript(webView, navigationScript, "navigation")
        }
    }

    private fun evaluateReaderScript(webView: WKWebView, script: String, kind: String) {
        if (script.isBlank()) return
        webView.evaluateJavaScript(script) { _, error ->
            if (error != null) {
                sharedEpubOpenTrace { "webview evaluateFailed kind=$kind chars=${script.length} error=${error.localizedDescription}" }
            }
        }
    }

    private fun documentDidFinishLoading(webView: WKWebView) {
        val loadMs = htmlLoadStartMark?.let { sharedEpubOpenTraceElapsedMs(it) }
        sharedEpubOpenTrace { "webview didFinishNavigation ms=${loadMs?.let { sharedEpubOpenTraceMs(it) } ?: "?"}" }
        // Sequence appearance -> highlights -> navigation so a big chapter
        // finishes layout before the navigation scroll runs (Android parity).
        val appearance = latestAppearanceScript.takeIf { it.isNotBlank() }
        val highlights = latestHighlightsApplyScript.takeIf { it.isNotBlank() }
        val navigation = latestNavigationScript
        fun applyNavigation() {
            if (navigation == null) return
            webView.evaluateJavaScript(navigation) { _, error ->
                if (error != null) {
                    sharedEpubOpenTrace { "webview evaluateFailed kind=navigation chars=${navigation.length} error=${error.localizedDescription}" }
                } else {
                    appliedNavigationRequestId = latestNavigationRequestId
                }
            }
        }
        fun applyHighlights() {
            if (highlights == null) {
                applyNavigation()
                return
            }
            webView.evaluateJavaScript(highlights) { _, error ->
                if (error != null) {
                    sharedEpubOpenTrace { "webview evaluateFailed kind=highlights chars=${highlights.length} error=${error.localizedDescription}" }
                } else {
                    appliedHighlightsHash = highlights.hashCode()
                }
                applyNavigation()
            }
        }
        if (appearance == null) {
            applyHighlights()
            return
        }
        webView.evaluateJavaScript(appearance) { _, error ->
            if (error != null) {
                sharedEpubOpenTrace { "webview evaluateFailed kind=appearance chars=${appearance.length} error=${error.localizedDescription}" }
            } else {
                appliedAppearanceHash = appearance.hashCode()
            }
            applyHighlights()
        }
    }

    private fun documentDidFailLoading(webView: WKWebView, error: NSError) {
        val loadMs = htmlLoadStartMark?.let { sharedEpubOpenTraceElapsedMs(it) }
        sharedEpubOpenTrace {
            "webview didFailNavigation ms=${loadMs?.let { sharedEpubOpenTraceMs(it) } ?: "?"} " +
                "error=${error.localizedDescription} code=${error.code}"
        }
    }

    private fun documentProcessTerminated(webView: WKWebView) {
        sharedEpubOpenTrace { "webview contentProcessTerminated" }
        // WKWebView kills the content process under memory pressure (likely for
        // a big chapter). Reload the last document when this view is still active.
        loadedHtmlHash = null
        loadedHtmlLength = -1
        appliedAppearanceHash = null
        appliedHighlightsHash = null
        appliedNavigationRequestId = Long.MIN_VALUE
        val html = lastHtml
        if (html != null && webView == activeWebView) {
            htmlLoadStartMark = sharedEpubOpenTraceMark()
            reportedFirstPosition = false
            sharedEpubOpenTrace { "webview reloadAfterTerminate chars=${html.length}" }
            webView.loadHTMLString(html, baseURL = null)
        }
    }

    private fun shouldCancelNavigation(urlString: String?): Boolean {
        val raw = urlString?.trim().orEmpty()
        if (raw.isBlank() || raw == "about:blank") return false
        val normalized = normalizeReaderHref(raw)
        return if (isReaderExternalHref(normalized)) {
            openSharedMobileEpubExternalLink(normalized)
            true
        } else {
            false
        }
    }

    private fun handleBridgeMessage(method: String, payload: String) {
        if (!reportedFirstPosition && method == "readerPositionChanged") {
            reportedFirstPosition = true
            val loadMs = htmlLoadStartMark?.let { sharedEpubOpenTraceElapsedMs(it) }
            sharedEpubOpenTrace { "webview firstPositionReport ms=${loadMs?.let { sharedEpubOpenTraceMs(it) } ?: "?"} sinceLoadHTML" }
        }
        if (method == "readerCopyText") {
            val text = runCatching {
                Json.parseToJsonElement(payload).jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotEmpty() }
            if (text != null) {
                writeSharedClipboard(label = "Copied Text", text = text)
            }
            return
        }
        if (method == "readerChunkRequested") {
            val index = IosEpubChunkIndexRegex.find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return
            val chunk = contentChunks.getOrNull(index) ?: return
            val provideMark = sharedEpubOpenTraceMark()
            sharedEpubOpenTrace { "webview chunkProvide start index=$index chunkChars=${chunk.length}" }
            activeWebView?.evaluateJavaScript(
                "window.readerVirtualization && window.readerVirtualization.provideChunk($index, ${JsonPrimitive(chunk)});",
            ) { _, error ->
                if (error != null) {
                    sharedEpubOpenTrace { "webview chunkProvide failed index=$index error=${error.localizedDescription}" }
                } else {
                    sharedEpubOpenTrace { "webview chunkProvide dispatched index=$index dispatchMs=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(provideMark))}" }
                }
            }
            return
        }
        onBridgeMessage(method, payload)
    }

    fun captureCurrentLocator(callback: (String?) -> Unit) {
        val webView = activeWebView
        if (webView == null) {
            callback(null)
            return
        }
        webView.evaluateJavaScript(
            SharedMobileEpubCaptureCurrentPositionScript,
            completionHandler = { result, _ ->
                callback(decodeSharedMobileJavascriptResult(result?.toString()))
            }
        )
    }

    fun release(webView: WKWebView) {
        sharedEpubOpenTrace { "webview release" }
        streamPageSchemeHandler.release()
        epubResourceSchemeHandler.release()
        webView.stopLoading()
        webView.navigationDelegate = null
        webView.configuration.userContentController.removeScriptMessageHandlerForName(IosEpubBridgeName)
        activeWebView = null
        contentChunks = emptyList()
        loadedHtmlHash = null
        loadedHtmlLength = -1
        lastHtml = null
        appliedHighlightsHash = null
        appliedBackgroundArgb = null
        latestHighlightsApplyScript = ""
        htmlLoadStartMark = null
        reportedFirstPosition = false
    }
}

private fun Long.toIosReaderColor(): UIColor {
    val alpha = ((this ushr 24) and 0xFF).toDouble() / 255.0
    val red = ((this ushr 16) and 0xFF).toDouble() / 255.0
    val green = ((this ushr 8) and 0xFF).toDouble() / 255.0
    val blue = (this and 0xFF).toDouble() / 255.0
    return UIColor.colorWithRed(red, green, blue, alpha)
}

private class IosEpubResourceSchemeHandler : NSObject(), WKURLSchemeHandlerProtocol {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val taskJobs = mutableMapOf<WKURLSchemeTaskProtocol, Job>()

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
        val task = startURLSchemeTask
        val url = task.request.URL?.absoluteString.orEmpty()
        val reference = parseSharedEpubResourceUrl(url)
        if (reference == null) {
            task.didFailWithError(NSError(domain = "ReaderEpubResources", code = 1L, userInfo = null))
            return
        }
        val job = scope.launch {
            val traceMark = sharedEpubOpenTraceMark()
            val bytes = withContext(Dispatchers.Default) {
                runCatching { IosEpubResourceStore.bytesFor(reference.bookId, reference.entryPath) }.getOrNull()
            }
            ensureActive()
            sharedEpubOpenTrace {
                "webview resource entry=${reference.entryPath} bytes=${bytes?.size ?: -1} " +
                    "ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(traceMark))}"
            }
            if (bytes == null) {
                task.didFailWithError(NSError(domain = "ReaderEpubResources", code = 2L, userInfo = null))
                return@launch
            }
            val responseUrl = task.request.URL ?: return@launch
            task.didReceiveResponse(
                NSURLResponse(
                    uRL = responseUrl,
                    MIMEType = sharedEpubResourceMimeType(reference.entryPath),
                    expectedContentLength = bytes.size.toLong(),
                    textEncodingName = null,
                )
            )
            task.didReceiveData(bytes.toNSData())
            task.didFinish()
        }
        taskJobs[task] = job
        job.invokeOnCompletion { taskJobs.remove(task) }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) {
        taskJobs.remove(stopURLSchemeTask)?.cancel()
    }

    fun release() {
        taskJobs.values.toList().forEach(Job::cancel)
        taskJobs.clear()
        scope.cancel()
    }
}

private class IosOpdsStreamPageSchemeHandler(
    private val loader: () -> SharedMobileEpubStreamPageLoader?,
    private val unavailablePageLabel: () -> String,
) : NSObject(), WKURLSchemeHandlerProtocol {
    // WKURLSchemeHandler callbacks are delivered on the main run loop. Keeping
    // task bookkeeping and WebKit response callbacks on Main avoids racing the
    // mutable task map while the authenticated loader suspends for I/O.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val taskJobs = mutableMapOf<WKURLSchemeTaskProtocol, Job>()

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
        val task = startURLSchemeTask
        val resourceUrl = task.request.URL?.absoluteString.orEmpty()
        val job = scope.launch {
            val currentLoader = loader()
            val response = runCatching {
                currentLoader?.loadPage(resourceUrl)
            }.getOrNull() ?: unavailablePageResponse(unavailablePageLabel())
            ensureActive()
            val url = task.request.URL ?: return@launch
            task.didReceiveResponse(
                NSURLResponse(
                    uRL = url,
                    MIMEType = response.mimeType,
                    expectedContentLength = response.bytes.size.toLong(),
                    textEncodingName = null,
                )
            )
            task.didReceiveData(response.bytes.toNSData())
            task.didFinish()
        }
        taskJobs[task] = job
        job.invokeOnCompletion { taskJobs.remove(task) }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) {
        taskJobs.remove(stopURLSchemeTask)?.cancel()
    }

    fun release() {
        taskJobs.values.toList().forEach(Job::cancel)
        taskJobs.clear()
        scope.cancel()
    }

    private fun unavailablePageResponse(label: String): SharedMobileEpubStreamPageResponse {
        val safeLabel = label
            .ifBlank { "\u2026" }
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return SharedMobileEpubStreamPageResponse(
            bytes = IosOpdsUnavailablePageSvg.replace("%LABEL%", safeLabel).encodeToByteArray(),
            mimeType = "image/svg+xml",
        )
    }
}

private const val IosOpdsUnavailablePageSvg = """
<svg xmlns="http://www.w3.org/2000/svg" width="800" height="1200" viewBox="0 0 800 1200">
  <rect width="800" height="1200" fill="#424242"/>
  <text x="400" y="600" fill="#ffffff" font-family="sans-serif" font-size="40" text-anchor="middle">%LABEL%</text>
</svg>
"""

private fun ByteArray.toNSData(): NSData {
    val data = NSMutableData.dataWithLength(size.toULong()) ?: NSMutableData()
    if (isNotEmpty()) {
        val pinned = pin()
        try {
            memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
        } finally {
            pinned.unpin()
        }
    }
    return data
}

private class IosEpubNavigationDelegate(
    private val onFinished: (WKWebView) -> Unit,
    private val onFailed: (WKWebView, NSError) -> Unit,
    private val onTerminated: (WKWebView) -> Unit,
    private val onDecidePolicy: (String?) -> Boolean,
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onFinished(webView)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        onFailed(webView, withError)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        onFailed(webView, withError)
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        onTerminated(webView)
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit
    ) {
        // Android parity (shouldOverrideUrlLoading): external links leave the
        // reader; anything else (including about:blank for loadHTMLString and
        // reader-epub-res resources) stays in the WebView.
        val cancel = try {
            onDecidePolicy(decidePolicyForNavigationAction.request.URL?.absoluteString)
        } catch (_: Exception) {
            false
        }
        decisionHandler(
            if (cancel) WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            else WKNavigationActionPolicy.WKNavigationActionPolicyAllow
        )
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
          return true;
        } catch (_) {
          return false;
        }
      }
      window.kmpJsBridge = {
        callNative: function (method, payload) { return post(method, payload); }
      };
      window.readerDisableLinkFallback = true;
      // WKWebView draws its own native selection handles; showing the reader's
      // custom teardrop handles on top would double them. The shared selection
      // script checks this flag and leaves handle display/drag to WebKit.
      window.readerIosNativeSelectionHandles = true;
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
