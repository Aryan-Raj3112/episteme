package com.aryan.reader

import com.aryan.reader.shared.CloudTtsBackend
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderFishVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules Android's TTS settings sheet used to encode inline, now shared-hosted.
 *
 * They are worth pinning because they are user-visible branches: which panel an OSS build without a
 * BYOK key gets, whether the cloud tab lists the Fish catalog or the prebuilt Gemini voices, and the
 * display name saved next to a selected voice.
 */
class AndroidTtsSettingsTest {

    private val fishVoice = ReaderFishVoice(
        id = "fish-alias",
        referenceId = "2f5c4d1e-0000-4000-8000-000000000000",
        title = "Ava",
    )

    @Test
    fun `an OSS build without a BYOK key has no cloud engine`() {
        assertTrue(androidTtsSettingsDeviceOnly(isOssFlavor = true, byokCloudTtsAvailable = false))
    }

    @Test
    fun `an OSS build with a BYOK key keeps the cloud tabs`() {
        assertFalse(androidTtsSettingsDeviceOnly(isOssFlavor = true, byokCloudTtsAvailable = true))
    }

    @Test
    fun `pro builds always keep the cloud tabs`() {
        assertFalse(androidTtsSettingsDeviceOnly(isOssFlavor = false, byokCloudTtsAvailable = false))
        assertFalse(androidTtsSettingsDeviceOnly(isOssFlavor = false, byokCloudTtsAvailable = true))
    }

    @Test
    fun `only BYOK Gemini falls back to the prebuilt voice list`() {
        assertTrue(androidTtsCloudCatalogIsStaticGemini(CloudTtsBackend.GEMINI_BYOK))
        assertFalse(androidTtsCloudCatalogIsStaticGemini(CloudTtsBackend.FISH_BYOK))
        assertFalse(androidTtsCloudCatalogIsStaticGemini(CloudTtsBackend.WORKER))
        assertFalse(androidTtsCloudCatalogIsStaticGemini(CloudTtsBackend.UNAVAILABLE))
    }

    @Test
    fun `a Gemini voice resolves to the name the sheet lists`() {
        val prebuilt = ReaderCloudTtsVoices.first()

        assertEquals(
            prebuilt.name,
            androidTtsCloudVoiceName(prebuilt.id, CloudTtsBackend.GEMINI_BYOK, emptyList()),
        )
    }

    @Test
    fun `a fish voice resolves by reference id and by alias`() {
        val backend = CloudTtsBackend.FISH_BYOK

        assertEquals("Ava", androidTtsCloudVoiceName(fishVoice.referenceId, backend, listOf(fishVoice)))
        assertEquals("Ava", androidTtsCloudVoiceName(fishVoice.id, backend, listOf(fishVoice)))
    }

    @Test
    fun `an unknown voice has no display name`() {
        assertNull(androidTtsCloudVoiceName("nope", CloudTtsBackend.FISH_BYOK, listOf(fishVoice)))
        assertNull(androidTtsCloudVoiceName("nope", CloudTtsBackend.GEMINI_BYOK, emptyList()))
    }
}
