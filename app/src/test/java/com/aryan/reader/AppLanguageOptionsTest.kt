package com.aryan.reader

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageOptionsTest {

    @Test
    fun `supported app languages expose configured locale order`() {
        assertEquals(
            listOf("en", "ar", "de", "tr", "fr", "ru", "be", "es", "pt-BR", "it", "pl", "vi", "hi", "zh-CN"),
            supportedAppLanguageOptions.mapNotNull { it.tag }
        )
        assertEquals(R.string.language_chinese_simplified, supportedAppLanguageOptions.last().labelRes)
    }

    @Test
    fun `app language selection defaults to system before explicit overrides`() {
        assertEquals(null, appLanguageSelectionOptions.first().tag)
        assertEquals(R.string.language_system_default, appLanguageSelectionOptions.first().labelRes)
        assertEquals(supportedAppLanguageOptions, appLanguageSelectionOptions.drop(1))
    }

    @Test
    fun `app language search matches labels tags and aliases`() {
        val chinese = supportedAppLanguageOptions.first { it.tag == "zh-CN" }

        assertTrue(chinese.matchesLanguageSearch(label = "简体中文（简体中文）", query = "zh cn"))
        assertTrue(chinese.matchesLanguageSearch(label = "简体中文（简体中文）", query = "mandarin"))
        assertTrue(chinese.matchesLanguageSearch(label = "简体中文（简体中文）", query = "简体"))
        assertTrue(systemAppLanguageOption.matchesLanguageSearch(label = "System default", query = "device"))
        assertFalse(chinese.matchesLanguageSearch(label = "简体中文（简体中文）", query = "korean"))
    }

    @Test
    fun `app language search normalizes accents`() {
        val turkish = supportedAppLanguageOptions.first { it.tag == "tr" }
        val belarusian = supportedAppLanguageOptions.first { it.tag == "be" }
        val portugueseBrazilian = supportedAppLanguageOptions.first { it.tag == "pt-BR" }
        val vietnamese = supportedAppLanguageOptions.first { it.tag == "vi" }

        assertTrue(turkish.matchesLanguageSearch(label = "Türkçe (Turkish)", query = "turkce"))
        assertTrue(belarusian.matchesLanguageSearch(label = "Беларуская", query = "belarusian"))
        assertTrue(
            portugueseBrazilian.matchesLanguageSearch(
                label = "Português (Brasil)",
                query = "portugues brasileiro"
            )
        )
        assertTrue(vietnamese.matchesLanguageSearch(label = "Tiếng Việt", query = "tieng viet"))
    }

    @Test
    fun `supported app language tags are unique`() {
        val tags = supportedAppLanguageOptions.mapNotNull { it.tag }

        assertEquals(tags.distinct(), tags)
    }

    @Test
    fun `supported app languages match Android locale config`() {
        assertEquals(readLocaleConfigTags(), supportedAppLanguageOptions.map { it.tag })
    }

    private fun readLocaleConfigTags(): List<String> {
        val localeConfig = listOf(
            File("src/main/res/xml/locales_config.xml"),
            File("app/src/main/res/xml/locales_config.xml")
        ).first { it.isFile }
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(localeConfig)
        val localeNodes = document.getElementsByTagName("locale")

        return buildList {
            for (index in 0 until localeNodes.length) {
                add(
                    localeNodes.item(index)
                        .attributes
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .nodeValue
                )
            }
        }
    }
}
