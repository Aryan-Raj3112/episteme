package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The language list, its aliases and its search rules are shared so Android
 * (`UiLabelResources`) and iOS (`SharedMobileLanguageSelectionList`) filter the
 * same catalog identically.
 */
class SharedAppLanguagesTest {

    @Test
    fun `language order matches the Android locale configuration`() {
        assertEquals(
            listOf(
                "en", "ar", "de", "nl", "tr", "fr", "ru", "uk", "be", "es", "pt-BR", "it", "pl",
                "id", "vi", "ja", "ko", "hi", "zh-CN", "et"
            ),
            sharedAppLanguages.mapNotNull { it.tag }
        )
        assertEquals(null, sharedAppLanguageOptions.first().tag)
        assertEquals("language_system_default", sharedAppLanguageOptions.first().labelKey)
        assertEquals("language_estonian", sharedAppLanguageOptions.last().labelKey)
    }

    @Test
    fun `label keys are unique so the platform catalogs cannot collide`() {
        val keys = sharedAppLanguageOptions.map { it.labelKey }

        assertEquals(keys.distinct(), keys)
    }

    @Test
    fun `search matches labels tags and aliases`() {
        val chinese = sharedAppLanguageOption("zh-CN")

        assertTrue(match(chinese, label = "简体中文（简体中文）", query = "zh cn"))
        assertTrue(match(chinese, label = "简体中文（简体中文）", query = "mandarin"))
        assertTrue(match(chinese, label = "简体中文（简体中文）", query = "简体"))
        assertFalse(match(chinese, label = "简体中文（简体中文）", query = "korean"))
        assertTrue(match(sharedAppLanguageOption(null), label = "System default", query = "device"))
    }

    @Test
    fun `search folds accents and decomposed input`() {
        // iOS may hand the query over already decomposed (NFD).
        assertTrue(match(sharedAppLanguageOption("tr"), label = "Türkçe (Turkish)", query = "Tu\u0308rk\u00e7e"))
        assertTrue(match(sharedAppLanguageOption("es"), label = "Español", query = "espanol"))
        assertTrue(match(sharedAppLanguageOption("pt-BR"), label = "Português (Brasil)", query = "portugues brasileiro"))
        assertTrue(match(sharedAppLanguageOption("vi"), label = "Tiếng Việt", query = "tieng viet"))
        assertTrue(match(sharedAppLanguageOption("ja"), label = "日本語", query = "nihongo"))
        assertTrue(match(sharedAppLanguageOption("ko"), label = "한국어", query = "hangul"))
    }

    @Test
    fun `blank queries match every language`() {
        sharedAppLanguageOptions.forEach { option ->
            assertTrue(match(option, label = option.label, query = "   "))
        }
    }

    private fun match(option: SharedAppLanguageOption, label: String, query: String): Boolean =
        sharedAppLanguageSearchMatches(
            label = label,
            tag = option.tag,
            searchAliases = option.searchAliases,
            query = query,
        )
}
