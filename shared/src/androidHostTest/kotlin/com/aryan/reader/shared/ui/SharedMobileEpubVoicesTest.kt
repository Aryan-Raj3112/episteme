package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedMobileEpubVoicesTest {

    private fun voice(
        name: String,
        language: String,
        quality: SharedMobileEpubVoiceQuality = SharedMobileEpubVoiceQuality.STANDARD,
        languageTag: String = "",
    ) = SharedMobileEpubVoice(
        identifier = "id-$name-$language-$quality",
        name = name,
        language = language,
        languageTag = languageTag,
        quality = quality,
    )

    @Test
    fun `android quality ints collapse to three shared tiers`() {
        assertEquals(SharedMobileEpubVoiceQuality.STANDARD, sharedMobileEpubVoiceQualityForAndroidQuality(100))
        assertEquals(SharedMobileEpubVoiceQuality.STANDARD, sharedMobileEpubVoiceQualityForAndroidQuality(300))
        assertEquals(SharedMobileEpubVoiceQuality.ENHANCED, sharedMobileEpubVoiceQualityForAndroidQuality(400))
        assertEquals(SharedMobileEpubVoiceQuality.PREMIUM, sharedMobileEpubVoiceQualityForAndroidQuality(500))
    }

    @Test
    fun `voices sort by language then name then quality`() {
        val sorted = listOf(
            voice("Zoe", "German", SharedMobileEpubVoiceQuality.PREMIUM),
            voice("Anna", "German", SharedMobileEpubVoiceQuality.ENHANCED),
            voice("Anna", "German", SharedMobileEpubVoiceQuality.STANDARD),
            voice("Mia", "English (United States)"),
        ).sortedForTtsDisplay()

        assertEquals("English (United States)", sorted[0].language)
        assertEquals(
            listOf(SharedMobileEpubVoiceQuality.STANDARD, SharedMobileEpubVoiceQuality.ENHANCED, SharedMobileEpubVoiceQuality.PREMIUM),
            sorted.drop(1).map { it.quality },
        )
        assertTrue(sorted.drop(1).all { it.name == "Anna" || it.name == "Zoe" })
        assertEquals("Anna", sorted[1].name)
        assertEquals("Anna", sorted[2].name)
        assertEquals("Zoe", sorted[3].name)
    }

    @Test
    fun `language options are distinct sorted with all first`() {
        val options = sharedMobileEpubVoiceLanguageOptions(
            listOf(voice("B", "German"), voice("A", "English (United States)"), voice("C", "German")),
            "All",
        )
        assertEquals(listOf("All", "English (United States)", "German"), options)
    }

    @Test
    fun `quality options only list present tiers`() {
        val options = sharedMobileEpubVoiceQualityOptions(
            listOf(voice("A", "English", SharedMobileEpubVoiceQuality.PREMIUM), voice("B", "English")),
        )
        assertEquals(
            listOf(SharedMobileEpubVoiceQuality.STANDARD, SharedMobileEpubVoiceQuality.PREMIUM),
            options,
        )
    }

    @Test
    fun `filter applies language and quality together`() {
        val voices = listOf(
            voice("A", "English", SharedMobileEpubVoiceQuality.STANDARD),
            voice("B", "English", SharedMobileEpubVoiceQuality.ENHANCED),
            voice("C", "German", SharedMobileEpubVoiceQuality.STANDARD),
        )
        assertEquals(3, voices.filteredForTtsDisplay("All", "All", null).size)
        assertEquals(
            listOf("B"),
            voices.filteredForTtsDisplay("English", "All", SharedMobileEpubVoiceQuality.ENHANCED).map { it.name },
        )
        assertEquals(
            listOf("A", "C"),
            voices.filteredForTtsDisplay("All", "All", SharedMobileEpubVoiceQuality.STANDARD).map { it.name },
        )
    }

    @Test
    fun `sample text falls back to default when blank`() {
        assertEquals(SHARED_MOBILE_TTS_SAMPLE_DEFAULT, effectiveSharedMobileTtsSampleText(null))
        assertEquals(SHARED_MOBILE_TTS_SAMPLE_DEFAULT, effectiveSharedMobileTtsSampleText("   "))
        assertEquals(
            SHARED_MOBILE_TTS_SAMPLE_DEFAULT,
            effectiveSharedMobileTtsSampleText(sanitizeSharedMobileTtsSampleText("  ")),
        )
    }

    @Test
    fun `sample text sanitizes whitespace and caps length`() {
        assertEquals("", sanitizeSharedMobileTtsSampleText("   "))
        assertEquals("hello world next", sanitizeSharedMobileTtsSampleText("  hello   world\n\tnext "))
        assertEquals("hello world next", effectiveSharedMobileTtsSampleText("  hello   world\n\tnext "))
        val overlong = "a".repeat(SHARED_MOBILE_TTS_SAMPLE_MAX_LENGTH + 50)
        assertEquals(SHARED_MOBILE_TTS_SAMPLE_MAX_LENGTH, sanitizeSharedMobileTtsSampleText(overlong).length)
        assertEquals(SHARED_MOBILE_TTS_SAMPLE_MAX_LENGTH, effectiveSharedMobileTtsSampleText(overlong).length)
    }

    @Test
    fun `sample text preserves any language`() {
        assertEquals("नमस्ते दुनिया", effectiveSharedMobileTtsSampleText("नमस्ते दुनिया"))
        assertEquals("这是语音示例。", effectiveSharedMobileTtsSampleText("这是语音示例。"))
    }

    @Test
    fun `subtitle only carries quality suffix for non-standard tiers`() {
        assertEquals(
            "English (United States)",
            sharedMobileEpubVoiceSubtitle(voice("A", "English (United States)"), "Standard"),
        )
        assertEquals(
            "English (United States) • Enhanced",
            sharedMobileEpubVoiceSubtitle(
                voice("A", "English (United States)", SharedMobileEpubVoiceQuality.ENHANCED),
                "Enhanced",
            ),
        )
    }
}
