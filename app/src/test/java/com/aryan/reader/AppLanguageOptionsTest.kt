package com.aryan.reader

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageOptionsTest {

    @Test
    fun `supported app languages include Spanish`() {
        assertEquals(
            listOf("en", "ar", "de", "tr", "fr", "ru", "es"),
            supportedAppLanguageOptions.mapNotNull { it.tag }
        )
        assertEquals(R.string.language_spanish, supportedAppLanguageOptions.last().labelRes)
    }

    @Test
    fun `app language selection defaults to system before explicit overrides`() {
        assertEquals(null, appLanguageSelectionOptions.first().tag)
        assertEquals(R.string.language_system_default, appLanguageSelectionOptions.first().labelRes)
        assertEquals(supportedAppLanguageOptions, appLanguageSelectionOptions.drop(1))
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
