package com.aryan.reader

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AppLanguageOptionsTest {

    @Test
    fun `supported app languages expose configured locale order`() {
        assertEquals(
            listOf(
                "en", "ar", "de", "nl", "tr", "fr", "ru", "uk", "be", "es", "pt-BR", "it", "pl",
                "id", "vi", "ja", "ko", "hi", "zh-CN"
            ),
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
        val dutch = supportedAppLanguageOptions.first { it.tag == "nl" }
        val ukrainian = supportedAppLanguageOptions.first { it.tag == "uk" }
        val belarusian = supportedAppLanguageOptions.first { it.tag == "be" }
        val portugueseBrazilian = supportedAppLanguageOptions.first { it.tag == "pt-BR" }
        val indonesian = supportedAppLanguageOptions.first { it.tag == "id" }
        val vietnamese = supportedAppLanguageOptions.first { it.tag == "vi" }
        val japanese = supportedAppLanguageOptions.first { it.tag == "ja" }
        val korean = supportedAppLanguageOptions.first { it.tag == "ko" }

        assertTrue(turkish.matchesLanguageSearch(label = "Türkçe (Turkish)", query = "turkce"))
        assertTrue(dutch.matchesLanguageSearch(label = "Nederlands", query = "dutch"))
        assertTrue(ukrainian.matchesLanguageSearch(label = "Українська", query = "ukrayinska"))
        assertTrue(belarusian.matchesLanguageSearch(label = "Беларуская", query = "belarusian"))
        assertTrue(
            portugueseBrazilian.matchesLanguageSearch(
                label = "Português (Brasil)",
                query = "portugues brasileiro"
            )
        )
        assertTrue(indonesian.matchesLanguageSearch(label = "Bahasa Indonesia", query = "bahasa"))
        assertTrue(vietnamese.matchesLanguageSearch(label = "Tiếng Việt", query = "tieng viet"))
        assertTrue(japanese.matchesLanguageSearch(label = "日本語", query = "nihongo"))
        assertTrue(korean.matchesLanguageSearch(label = "한국어", query = "hangul"))
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

    @Test
    fun `android manifest enables AppCompat language persistence`() {
        val manifest = readAndroidManifest()
        val service = manifest.getElementsByTagName("service").asElements()
            .singleOrNull {
                it.androidAttribute("name") == "androidx.appcompat.app.AppLocalesMetadataHolderService"
            }

        assertTrue(service != null)
        assertEquals("false", service!!.androidAttribute("enabled"))
        assertEquals("false", service.androidAttribute("exported"))

        val autoStoreLocales = service.getElementsByTagName("meta-data").asElements()
            .singleOrNull { it.androidAttribute("name") == "autoStoreLocales" }

        assertTrue(autoStoreLocales != null)
        assertEquals("true", autoStoreLocales!!.androidAttribute("value"))
    }

    @Test
    fun `android manifest exposes cbt comic archive mime types`() {
        val mimeTypes = readAndroidManifest()
            .getElementsByTagName("data")
            .asElements()
            .mapNotNull { it.androidAttribute("mimeType") }

        assertTrue("application/x-cbt" in mimeTypes)
        assertTrue("application/vnd.comicbook+tar" in mimeTypes)
        assertTrue("application/x-tar" in mimeTypes)
        assertTrue("application/tar" in mimeTypes)
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

    private fun readAndroidManifest(): org.w3c.dom.Document {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.isFile }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        buildList {
            for (index in 0 until length) {
                val element = item(index) as? Element
                if (element != null) add(element)
            }
        }

    private fun Element.androidAttribute(name: String): String? =
        attributes
            ?.getNamedItemNS("http://schemas.android.com/apk/res/android", name)
            ?.nodeValue
}
