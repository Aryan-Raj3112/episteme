package com.aryan.reader.tts

import android.speech.tts.Voice
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class TtsModePolicyTest {
    @Test
    fun `offline builds force device tts even when cloud config is present`() {
        val mode = resolveTtsModeForBuild(
            requestedModeName = TtsPlaybackManager.TtsMode.CLOUD.name,
            isOfflineBuild = true,
            isProBuild = true,
            workerUrl = "https://example.com/tts",
            byokCloudAvailable = true
        )

        assertEquals(TtsPlaybackManager.TtsMode.BASE, mode)
    }

    @Test
    fun `oss builds ignore server backed cloud tts without byok`() {
        val mode = resolveTtsModeForBuild(
            requestedModeName = TtsPlaybackManager.TtsMode.CLOUD.name,
            isOfflineBuild = false,
            isProBuild = false,
            workerUrl = "https://example.com/tts",
            byokCloudAvailable = false
        )

        assertEquals(TtsPlaybackManager.TtsMode.BASE, mode)
    }

    @Test
    fun `pro builds can use configured cloud tts`() {
        val mode = resolveTtsModeForBuild(
            requestedModeName = TtsPlaybackManager.TtsMode.CLOUD.name,
            isOfflineBuild = false,
            isProBuild = true,
            workerUrl = "https://example.com/tts",
            byokCloudAvailable = false
        )

        assertEquals(TtsPlaybackManager.TtsMode.CLOUD, mode)
    }

    @Test
    fun `oss builds can use byok cloud tts when online`() {
        val mode = resolveTtsModeForBuild(
            requestedModeName = TtsPlaybackManager.TtsMode.CLOUD.name,
            isOfflineBuild = false,
            isProBuild = false,
            workerUrl = "",
            byokCloudAvailable = true
        )

        assertEquals(TtsPlaybackManager.TtsMode.CLOUD, mode)
    }

    @Test
    fun `invalid tts mode falls back to device tts`() {
        val mode = resolveTtsModeForBuild(
            requestedModeName = "REMOTE",
            isOfflineBuild = false,
            isProBuild = true,
            workerUrl = "https://example.com/tts",
            byokCloudAvailable = false
        )

        assertEquals(TtsPlaybackManager.TtsMode.BASE, mode)
    }

    @Test
    fun `native tts voice list is resolved only when required`() {
        assertEquals(false, shouldResolveNativeTtsVoice(preferredVoiceName = null, isOfflineBuild = false))
        assertEquals(false, shouldResolveNativeTtsVoice(preferredVoiceName = "   ", isOfflineBuild = false))
        assertEquals(true, shouldResolveNativeTtsVoice(preferredVoiceName = "voice-id", isOfflineBuild = false))
        assertEquals(true, shouldResolveNativeTtsVoice(preferredVoiceName = null, isOfflineBuild = true))
    }

    @Test
    fun `offline native tts ignores saved network voice`() {
        val localVoice = voice("local", requiresNetwork = false)
        val networkVoice = voice("network", requiresNetwork = true)

        val resolved = resolveNativeTtsVoiceForBuild(
            preferredVoiceName = networkVoice.name,
            defaultVoice = networkVoice,
            availableVoices = listOf(networkVoice, localVoice),
            defaultLocale = Locale.US,
            isOfflineBuild = true
        )

        assertEquals(localVoice.name, resolved?.name)
    }

    @Test
    fun `online native tts keeps saved network voice`() {
        val localVoice = voice("local", requiresNetwork = false)
        val networkVoice = voice("network", requiresNetwork = true)

        val resolved = resolveNativeTtsVoiceForBuild(
            preferredVoiceName = networkVoice.name,
            defaultVoice = localVoice,
            availableVoices = listOf(localVoice, networkVoice),
            defaultLocale = Locale.US,
            isOfflineBuild = false
        )

        assertEquals(networkVoice.name, resolved?.name)
    }

    private fun voice(
        name: String,
        locale: Locale = Locale.US,
        requiresNetwork: Boolean
    ): Voice {
        return Voice(
            name,
            locale,
            Voice.QUALITY_NORMAL,
            Voice.LATENCY_NORMAL,
            requiresNetwork,
            emptySet<String>()
        )
    }
}
