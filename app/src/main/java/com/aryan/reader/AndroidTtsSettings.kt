package com.aryan.reader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.shared.CloudTtsBackend
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderFishVoice
import com.aryan.reader.shared.resolveCloudTtsBackend
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedMobileReaderTtsSettingsSheet
import com.aryan.reader.shared.ui.toggleSharedMobileTtsVoiceFavorite
import com.aryan.reader.tts.AndroidSharedMobileEpubCloudTts
import com.aryan.reader.tts.SharedMobileEpubTtsAdapter
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.googleCloudWorkerTtsUrl
import com.aryan.reader.tts.loadCloudVoiceLanguage
import com.aryan.reader.tts.loadTtsFavoriteVoices
import com.aryan.reader.tts.saveCloudVoiceLanguage
import com.aryan.reader.tts.saveTtsFavoriteVoices
import com.aryan.reader.tts.saveTtsSpeakerName
import timber.log.Timber

/**
 * Android's TTS voice/engine settings, hosted on the shared sheet.
 *
 * The sheet itself is `SharedMobileReaderTtsSettingsSheet` — the same one iOS and desktop render — so
 * the layout, the engine pill, the tab strip, the Fish catalog rules and the voice/lock gating live
 * in exactly one place. What stays here is what is genuinely platform-owned:
 *
 * - the engines: [SharedMobileEpubTtsAdapter] (Media3 TTS service, device voices) and
 *   [AndroidSharedMobileEpubCloudTts] (cloud cache browser and voice samples),
 * - the Fish catalog loader ([fetchFishVoices] / [fetchCloudFishVoices], BYOK-aware),
 * - Android's own strings, through [androidSharedStringResolver] — shared copy is keyed by Android
 *   resource name, so the sheet renders the app's translations instead of its English fallbacks,
 * - the per-surface voice prefs ([loadDeviceVoiceName] / [saveDeviceVoiceName] /
 *   [saveCloudVoiceName]): the reader uses the defaults, audiobook Listen binds its own keys.
 */
@UnstableApi
@Composable
fun TtsSettingsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    currentMode: TtsPlaybackManager.TtsMode,
    onModeChange: (TtsPlaybackManager.TtsMode) -> Unit,
    currentSpeakerId: String,
    onSpeakerChange: (String) -> Unit,
    isTtsActive: Boolean,
    // Whether the engine/voice controls are frozen. Defaults to [isTtsActive] (the reader's
    // rule: any live session freezes settings). Audiobook Listen overrides it because its player
    // sheet closes the moment playback stops, so demanding a full stop makes the voice
    // unreachable; there, only actively speaking counts as locked.
    isVoiceChangeLocked: Boolean = isTtsActive,
    getAuthToken: suspend () -> String?,
    bookTitle: String,
    // Listen binds these to its independent prefs; Reader uses the defaults.
    loadDeviceVoiceName: (Context) -> String? = ::loadNativeVoice,
    saveDeviceVoiceName: (Context, String?) -> Unit = ::saveNativeVoice,
    saveCloudVoiceName: (Context, String) -> Unit = ::saveTtsSpeakerName
) {
    if (!isVisible) return
    val context = LocalContext.current
    // Android benchmark (TtsSettingsSheet): an OSS build without a usable BYOK key has no cloud
    // engine at all, so the sheet drops the engine switcher and the tabs entirely.
    val cloudAvailable = !androidTtsSettingsDeviceOnly(
        isOssFlavor = BuildConfig.FLAVOR == "oss",
        byokCloudTtsAvailable = isByokCloudTtsAvailable(context),
    )
    val localTts = remember(context) { SharedMobileEpubTtsAdapter(context) }
    DisposableEffect(localTts) {
        onDispose { localTts.release() }
    }
    // The device voices are enumerated when the sheet opens — never when the engine is created, so a
    // reader or an AI dialog holding one does not warm TextToSpeech for a sheet nobody opened.
    var deviceVoice by remember(context, loadDeviceVoiceName) {
        mutableStateOf(loadDeviceVoiceName(context))
    }
    LaunchedEffect(localTts) {
        localTts.loadAvailableVoices { clearedVoice ->
            // OFFLINE dropped a saved network-backed voice; persist the surviving value through the
            // prefs this surface owns (Reader and Listen store the device voice separately).
            if (deviceVoice != clearedVoice) {
                deviceVoice = clearedVoice
                saveDeviceVoiceName(context, clearedVoice)
            }
        }
    }
    val byok = remember(context) { loadAiByokSettings(context).sanitized() }
    val backend = remember(byok) { resolveCloudTtsBackend(byok) }
    // BYOK Gemini lists the prebuilt Gemini voices; every other backend lists the Fish catalog.
    val expectFishVoices = !androidTtsCloudCatalogIsStaticGemini(backend)
    var fishVoicesLoading by remember(backend) { mutableStateOf(expectFishVoices) }
    var fishVoices by remember(backend) { mutableStateOf<List<ReaderFishVoice>>(emptyList()) }
    LaunchedEffect(backend, cloudAvailable) {
        // The device-only branch never shows the catalog, so it must not fetch one: Android's
        // benchmark sheet only ever loaded it from the cloud tab.
        if (!cloudAvailable || !expectFishVoices) {
            fishVoices = emptyList()
            fishVoicesLoading = false
            return@LaunchedEffect
        }
        fishVoicesLoading = true
        fishVoices = if (backend == CloudTtsBackend.FISH_BYOK) {
            fetchFishVoices(byok.fishKey)
        } else {
            fetchCloudFishVoices(googleCloudWorkerTtsUrl, getAuthToken())
        }
        fishVoicesLoading = false
    }
    var favoriteVoiceIds by remember(context) { mutableStateOf(loadTtsFavoriteVoices(context)) }
    var cloudVoiceLanguage by remember(context) { mutableStateOf(loadCloudVoiceLanguage(context)) }
    val currentVoiceId = rememberUpdatedState(currentSpeakerId)
    val cloudTts = remember(context, cloudAvailable, bookTitle) {
        if (!cloudAvailable) {
            null
        } else {
            AndroidSharedMobileEpubCloudTts(
                context = context,
                getAuthToken = getAuthToken,
                // The shared panel selects a voice through this *and* `onCloudTtsVoiceChange`; the
                // surface's own callback is the command, so this only records the display name that
                // overlays show instead of a raw Fish reference id.
                onVoiceSelected = { voiceId ->
                    androidTtsCloudVoiceName(voiceId, backend, fishVoices)
                        ?.let { name -> saveCloudVoiceName(context, name) }
                },
                cacheBookTitle = { bookTitle },
                currentVoiceId = { currentVoiceId.value },
            )
        }
    }
    DisposableEffect(cloudTts) {
        onDispose { cloudTts?.release() }
    }
    CompositionLocalProvider(
        LocalSharedStringResolver provides androidSharedStringResolver(context)
    ) {
        SharedMobileReaderTtsSettingsSheet(
            tts = localTts,
            onDismiss = onDismiss,
            cloudTts = cloudTts,
            cloudTtsModeEnabled = currentMode == TtsPlaybackManager.TtsMode.CLOUD,
            onCloudTtsModeChange = { isCloud ->
                onModeChange(
                    if (isCloud) TtsPlaybackManager.TtsMode.CLOUD
                    else TtsPlaybackManager.TtsMode.BASE
                )
            },
            cloudTtsVoiceId = currentSpeakerId,
            onCloudTtsVoiceChange = onSpeakerChange,
            onClearCloudTtsCache = { cloudTts?.clearCache() },
            fishVoices = fishVoices,
            expectFishVoices = expectFishVoices,
            fishVoicesLoading = fishVoicesLoading,
            favoriteCloudVoiceIds = favoriteVoiceIds,
            onToggleFavoriteCloudVoice = { voiceId ->
                favoriteVoiceIds = toggleSharedMobileTtsVoiceFavorite(favoriteVoiceIds, voiceId)
                saveTtsFavoriteVoices(context, favoriteVoiceIds)
            },
            cloudVoiceLanguage = cloudVoiceLanguage,
            onCloudVoiceLanguageChange = { language ->
                cloudVoiceLanguage = language
                saveCloudVoiceLanguage(context, language)
            },
            onClearCloudVoiceSamples = { cloudTts?.clearVoiceSamples() },
            deviceVoiceIdentifier = deviceVoice,
            onDeviceVoiceChange = { voice ->
                deviceVoice = voice
                saveDeviceVoiceName(context, voice)
            },
            voiceChangeLocked = isVoiceChangeLocked,
            trace = { line -> Timber.tag(TTS_SETTINGS_TRACE_TAG).d(line) },
        )
    }
}

private const val TTS_SETTINGS_TRACE_TAG = "TTS_DIAGNOSE"

/**
 * Android benchmark (`TtsSettingsSheet`): an OSS build with no usable BYOK TTS key has no cloud
 * engine at all, so the sheet drops the engine switcher and the tabs and shows the device voices
 * alone. Pro builds always have the credited worker; OSS gains the cloud branch once a BYOK key with
 * a TTS model is saved.
 */
internal fun androidTtsSettingsDeviceOnly(
    isOssFlavor: Boolean,
    byokCloudTtsAvailable: Boolean,
): Boolean = isOssFlavor && !byokCloudTtsAvailable

/**
 * Whether the cloud-voices tab lists the prebuilt Gemini voices instead of the Fish catalog.
 *
 * A BYOK Gemini key means there is no Fish catalog to fetch, so the shared panel renders its static
 * Gemini list; every other backend lists Fish, credited worker included.
 */
internal fun androidTtsCloudCatalogIsStaticGemini(backend: CloudTtsBackend): Boolean =
    backend == CloudTtsBackend.GEMINI_BYOK

/**
 * Display name saved alongside a cloud voice id, so the TTS overlay shows "Ava" rather than a raw
 * Fish reference id. Null when the id is not in the catalog the sheet is currently listing.
 */
internal fun androidTtsCloudVoiceName(
    voiceId: String,
    backend: CloudTtsBackend,
    fishVoices: List<ReaderFishVoice>,
): String? = if (androidTtsCloudCatalogIsStaticGemini(backend)) {
    ReaderCloudTtsVoices.firstOrNull { it.id == voiceId }?.name?.takeIf { it.isNotBlank() }
} else {
    fishVoices.firstOrNull { voice -> voice.referenceId == voiceId || voice.id == voiceId }
        ?.title
        ?.takeIf { it.isNotBlank() }
}

fun loadNativeVoice(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_NATIVE_TTS_VOICE, null)
}

internal fun saveNativeVoice(context: Context, @Suppress("SameParameterValue") voiceName: String?) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    if (voiceName == null) {
        prefs.edit { remove(PREF_NATIVE_TTS_VOICE) }
    } else {
        prefs.edit { putString(PREF_NATIVE_TTS_VOICE, voiceName) }
    }
}
