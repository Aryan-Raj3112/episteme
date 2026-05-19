package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.ByteArrayInputStream
import java.util.Locale

class DesktopStringResourcesTest {
    @Test
    fun buildsAndroidResourcePathsForRegionalLocale() {
        val paths = desktopAndroidStringResourcePaths(Locale("pt", "BR"))

        assertEquals(
            listOf(
                "desktop-android-res/values-pt-rBR/strings.xml",
                "desktop-android-res/values-pt/strings.xml"
            ),
            paths
        )
    }

    @Test
    fun parsesAndroidStringXmlAndDecodesEscapes() {
        val xml = """
            <resources>
                <string name="line">One\nTwo</string>
                <string name="quote">Don\'t stop</string>
            </resources>
        """.trimIndent()

        val parsed = parseAndroidStringXml(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("One\nTwo", parsed["line"])
        assertEquals("Don't stop", parsed["quote"])
        assertTrue(parsed.containsKey("line"))
    }

    @Test
    fun normalizesDesktopLanguageTagsForAndroidResources() {
        assertEquals(null, normalizeDesktopLanguageTag(null))
        assertEquals("id", normalizeDesktopLanguageTag("in"))
        assertEquals("pt-BR", normalizeDesktopLanguageTag("pt_br"))
        assertEquals("zh-CN", normalizeDesktopLanguageTag("zh-cn"))
    }

    @Test
    fun resolvesSelectedDesktopLanguageOptionByNormalizedTag() {
        val option = selectedDesktopLanguageOption("pt_br")

        assertEquals("pt-BR", option.normalizedTag)
        assertEquals("language_portuguese_brazilian", option.labelKey)
    }
}
