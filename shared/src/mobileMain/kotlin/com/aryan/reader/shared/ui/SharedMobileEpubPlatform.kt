package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderTtsCacheChapter
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderVoiceSampleState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.ReaderLocator

internal data class SharedMobileEpubLoadState(
    val isLoading: Boolean = true,
    val book: SharedEpubBook? = null,
    val errorMessage: String? = null
)

/**
 * Position boundary for the platform WebView reader.
 *
 * Normal scroll reports travel through the JavaScript bridge and therefore
 * cannot be assumed to have reached Compose when a jump control is tapped.
 * The platform may provide a direct JavaScript query; the last observed
 * locator remains a safe fallback while the document is loading or being
 * released.
 */
class SharedMobileEpubWebViewController {
    private var requestCurrentPosition: (((String?) -> Unit) -> Unit)? = null
    private var latestObservedLocator: ReaderLocator? = null

    internal fun attach(requester: ((String?) -> Unit) -> Unit) {
        requestCurrentPosition = requester
    }

    internal fun detach() {
        requestCurrentPosition = null
    }

    internal fun updateObservedLocator(locator: ReaderLocator) {
        latestObservedLocator = locator
    }

    fun captureCurrentLocator(onResult: (ReaderLocator?) -> Unit) {
        val requester = requestCurrentPosition
        if (requester == null) {
            onResult(latestObservedLocator)
            return
        }
        requester { rawPayload ->
            val queried = rawPayload?.sharedMobileEpubLocatorOrNull()
            if (queried != null) latestObservedLocator = queried
            onResult(queried ?: latestObservedLocator)
        }
    }
}

data class SharedMobileEpubStreamPageResponse(
    val bytes: ByteArray,
    val mimeType: String,
)

/**
 * Optional platform resource boundary for authenticated remote OPDS-PSE pages.
 * The URL passed here is a credential-free reader-opds-page resource URI; the
 * platform implementation resolves credentials from its persisted catalog store.
 */
interface SharedMobileEpubStreamPageLoader {
    suspend fun loadPage(resourceUrl: String): SharedMobileEpubStreamPageResponse?
}

@Composable
internal expect fun rememberSharedMobileEpubLoadState(book: BookItem): SharedMobileEpubLoadState

@Composable
internal expect fun SharedMobileEpubWebView(
    html: String,
    contentChunks: List<String>,
    appearanceScript: String,
    navigationScript: String?,
    navigationRequestId: Long,
    highlightsApplyScript: String,
    onBridgeMessage: (method: String, payload: String) -> Unit,
    positionController: SharedMobileEpubWebViewController? = null,
    streamPageLoader: SharedMobileEpubStreamPageLoader? = null,
    streamPageUnavailableLabel: String,
    contentBackgroundArgb: Long,
    modifier: Modifier = Modifier
)

internal expect fun openSharedMobileEpubExternalLink(url: String): Boolean

/**
 * Extra PageInfo side clearance for rounded screen corners.
 *
 * Android keeps the benchmark 16.dp side padding untouched (0.dp here);
 * iOS adds room because portrait reports no horizontal safe inset while the
 * physical corners still curve into the bar's edge-pinned clock/percentage.
 */
internal expect val sharedMobileEpubPageInfoCornerClearance: Dp

/**
 * Whether a visible bottom PageInfo bar always stays above the bottom safe
 * area, even when the reader chrome (and with it the system nav bars) hides.
 *
 * iOS needs this: with menus hidden the bar would otherwise sit flush at the
 * bottom edge, where the corner curve eats the edge-pinned clock/percentage
 * (the background still extends to the edge, so nothing turns black). Android
 * keeps the benchmark flush-when-hidden behavior.
 */
internal expect val sharedMobileEpubPageInfoAlwaysApplyBottomSafeInset: Boolean

/**
 * Whether the PageInfo bar uses the exact reader background instead of the
 * tinted/translucent info-bar color.
 *
 * iOS needs this: the bar must read as a continuation of the page (any tonal
 * step makes it look like a floating strip pasted over the book). Android
 * keeps its benchmark tinted bar.
 */
internal expect val sharedMobileEpubPageInfoMatchesReaderBackground: Boolean
internal expect fun openSharedMobileEpubLookup(action: ReaderExternalLookupAction, text: String): Boolean
internal expect fun shareSharedMobileEpubImage(bytes: ByteArray, fileName: String): Boolean

/** Platform-backed device speech remains separate from shared cloud TTS. */
enum class SharedMobileEpubLocalTtsState { IDLE, SPEAKING, PAUSED }

/**
 * Device TTS voice tier, normalized across platforms.
 *
 * Android exposes a 5-step `Voice.getQuality` int while
 * iOS exposes default/enhanced/premium `AVSpeechSynthesisVoice.quality`.
 * Both collapse to these three buckets so the shared sheet can offer one
 * extra (quality) filter on top of the Android-benchmark language filter.
 */
enum class SharedMobileEpubVoiceQuality { STANDARD, ENHANCED, PREMIUM }

data class SharedMobileEpubVoice(
    val identifier: String,
    val name: String,
    /** Localized display name, e.g. "English (United States)" — never the raw BCP-47 tag. */
    val language: String,
    /** Raw BCP-47 tag, e.g. "en-US". Empty when the platform did not report one. */
    val languageTag: String = "",
    val quality: SharedMobileEpubVoiceQuality = SharedMobileEpubVoiceQuality.STANDARD,
)

/** Android benchmark quality buckets: very-high ≈ premium, high ≈ enhanced. */
fun sharedMobileEpubVoiceQualityForAndroidQuality(quality: Int): SharedMobileEpubVoiceQuality {
    // android.speech.tts.Voice.QUALITY_* are 100/200/300/400/500; avoid a
    // hard framework import here so this stays pure and unit-testable.
    return when {
        quality >= 500 -> SharedMobileEpubVoiceQuality.PREMIUM
        quality >= 400 -> SharedMobileEpubVoiceQuality.ENHANCED
        else -> SharedMobileEpubVoiceQuality.STANDARD
    }
}

fun List<SharedMobileEpubVoice>.sortedForTtsDisplay(): List<SharedMobileEpubVoice> =
    sortedWith(compareBy(SharedMobileEpubVoice::language, SharedMobileEpubVoice::name, SharedMobileEpubVoice::quality))

fun sharedMobileEpubVoiceLanguageOptions(
    voices: List<SharedMobileEpubVoice>,
    allLabel: String,
): List<String> =
    listOf(allLabel) +
        voices.map { it.language }.filter { it.isNotBlank() }.distinct().sorted()

fun sharedMobileEpubVoiceQualityOptions(
    voices: List<SharedMobileEpubVoice>,
): List<SharedMobileEpubVoiceQuality> =
    SharedMobileEpubVoiceQuality.entries.filter { tier -> voices.any { it.quality == tier } }

fun List<SharedMobileEpubVoice>.filteredForTtsDisplay(
    selectedLanguage: String?,
    allLanguagesLabel: String,
    selectedQuality: SharedMobileEpubVoiceQuality?,
): List<SharedMobileEpubVoice> =
    filter { voice ->
        (selectedLanguage == null || selectedLanguage == allLanguagesLabel || voice.language == selectedLanguage) &&
            (selectedQuality == null || voice.quality == selectedQuality)
    }

/**
 * List subtitle for a device voice. The quality suffix only appears for
 * non-standard tiers so the common case stays a clean "Name / Language"
 * row matching the Android benchmark.
 */
fun sharedMobileEpubVoiceSubtitle(voice: SharedMobileEpubVoice, qualityLabel: String?): String {
    if (voice.quality == SharedMobileEpubVoiceQuality.STANDARD || qualityLabel.isNullOrBlank()) {
        return voice.language
    }
    if (voice.language.isBlank()) return qualityLabel
    return "${voice.language} • $qualityLabel"
}

/** Default device-voice preview text. Matches Android's `tts_voice_sample_generic`. */
const val SHARED_MOBILE_TTS_SAMPLE_DEFAULT = "This is a voice sample."

/**
 * Max characters for the custom preview text. Previews speak the full text,
 * so the cap keeps them snappy and safely under engine input limits.
 */
const val SHARED_MOBILE_TTS_SAMPLE_MAX_LENGTH = 200

/**
 * Trims, collapses whitespace runs, and caps length. Blank is preserved as
 * blank so clearing the field mid-edit doesn't snap back; use
 * [effectiveSharedMobileTtsSampleText] for the speakable value.
 */
fun sanitizeSharedMobileTtsSampleText(raw: String?): String =
    raw.orEmpty().trim().replace(Regex("\\s+"), " ").take(SHARED_MOBILE_TTS_SAMPLE_MAX_LENGTH)

/** Speakable preview text; never blank (falls back to [SHARED_MOBILE_TTS_SAMPLE_DEFAULT]). */
fun effectiveSharedMobileTtsSampleText(stored: String?): String =
    sanitizeSharedMobileTtsSampleText(stored).ifBlank { SHARED_MOBILE_TTS_SAMPLE_DEFAULT }

interface SharedMobileEpubLocalTts {
    val state: SharedMobileEpubLocalTtsState
    /** Remains true while moving between document pages, even when no utterance is active. */
    val isSessionActive: Boolean
    /** The shared planner's active chunk, used to keep the reader in sync with speech. */
    val progress: ReaderTtsProgress
    val speechRate: Float
    val speechPitch: Float
    /** Effective voice-preview text; never blank (falls back to the default). */
    val previewSampleText: String
    val availableVoices: List<SharedMobileEpubVoice>
    val selectedVoiceIdentifier: String?
    /** Non-null when the last playback attempt was interrupted or failed unexpectedly. */
    val errorMessage: String?
    /** Increments only when every chunk finishes naturally; explicit stop does not increment it. */
    val completionCount: Long
    /** Starts platform audio preparation while document text is still being extracted. */
    fun prepare()
    fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String? = null,
        startChunkIndex: Int = 0,
        playWhenReady: Boolean = true
    )
    fun pause()
    fun resume()
    fun skipPrevious()
    fun skipNext()
    fun setSpeechParameters(rate: Float, pitch: Float)
    /** Persists custom preview text; blank clears back to the default. */
    fun setPreviewSampleText(text: String)
    fun setVoice(identifier: String?)
    fun previewVoice(identifier: String?)
    fun stop()
    fun release() = Unit
}

@Composable
internal expect fun rememberSharedMobileEpubLocalTts(): SharedMobileEpubLocalTts

/**
 * Platform audio boundary for Gemini Live cloud reading on mobile.
 *
 * The reader planner, gates, settings model, and controls stay shared. The
 * platform owns the WebSocket, PCM/WAV cache, audio session, and player. A
 * nullable value keeps Android's existing MediaSession-backed adapter intact
 * while iOS supplies its native implementation.
 */
interface SharedMobileEpubCloudTts {
    val state: ReaderCloudTtsState

    fun configure(
        settings: ReaderAiByokSettings,
        isSignedIn: Boolean,
        isProUser: Boolean,
        credits: Int,
        authToken: String?,
        workerUrl: String,
    )

    fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String? = null,
        startChunkIndex: Int = 0,
        playWhenReady: Boolean = true,
    )

    fun pause()
    fun resume()
    fun skipPrevious()
    fun skipNext()
    fun setVoice(identifier: String)
    fun clearCache()
    fun stop()
    fun release()

    // Android `TtsCacheTab` parity (per-chapter browser). Default no-ops keep
    // platforms without a file inventory compiling; iOS implements them.
    /** Distinct voice ids present in the chunk cache. */
    fun cachedChapterVoices(): List<String> = emptyList()

    /** Cached chapters for one voice, newest storage order. */
    fun cachedChapters(voiceId: String): List<ReaderTtsCacheChapter> = emptyList()

    /** Deletes one browser entry (consumes its [ReaderTtsCacheChapter.entryKey]). */
    fun deleteCachedChapter(chapter: ReaderTtsCacheChapter) = Unit

    /** Deletes every cached chunk for one voice. */
    fun deleteCachedVoice(voiceId: String) = Unit

    // Android `SpeakerSamplePlayer` parity (static per-voice sample wavs).
    val voiceSampleState: ReaderVoiceSampleState get() = ReaderVoiceSampleState()

    /** Toggles sample playback for one cloud voice (downloads once, then caches). */
    fun playOrStopVoiceSample(voiceId: String) = Unit

    /** Deletes all cached voice samples. */
    fun clearVoiceSamples() = Unit
}
