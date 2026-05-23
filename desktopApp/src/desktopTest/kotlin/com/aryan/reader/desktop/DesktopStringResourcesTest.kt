package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.ByteArrayInputStream
import java.nio.file.Files
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
    fun buildsAndroidPluralResourcePathsForRegionalLocale() {
        val paths = desktopAndroidPluralResourcePaths(Locale("pt", "BR"))

        assertEquals(
            listOf(
                "desktop-android-res/values-pt-rBR/plurals.xml",
                "desktop-android-res/values-pt/plurals.xml"
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
    fun parsesAndroidPluralXmlAndDecodesEscapes() {
        val xml = """
            <resources>
                <plurals name="book_count">
                    <item quantity="one">%1${'$'}d book</item>
                    <item quantity="other">%1${'$'}d books</item>
                </plurals>
                <plurals name="quoted_count">
                    <item quantity="one">Don\'t skip %1${'$'}d file</item>
                    <item quantity="other">Don\'t skip %1${'$'}d files</item>
                </plurals>
            </resources>
        """.trimIndent()

        val parsed = parseAndroidPluralXml(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("%1${'$'}d book", parsed["book_count"]?.get("one"))
        assertEquals("%1${'$'}d books", parsed["book_count"]?.get("other"))
        assertEquals("Don't skip %1${'$'}d file", parsed["quoted_count"]?.get("one"))
    }

    @Test
    fun loadsAndroidToolbarTooltipDescriptionsForDesktop() {
        val resources = DesktopAndroidStringResources.load(
            locale = Locale.ENGLISH,
            classLoader = Thread.currentThread().contextClassLoader
                ?: DesktopStringResourcesTest::class.java.classLoader
        )

        assertEquals(
            "Exit search and go back to the reader",
            resources.stringOrNull("tooltip_close_search_desc")
        )
        assertEquals(
            "Jump to the next search match in the document",
            resources.stringOrNull("tooltip_next_result_desc")
        )
    }

    @Test
    fun choosesDesktopPluralQuantityForSupportedLanguages() {
        val slavicQuantities = setOf("one", "few", "many", "other")
        val arabicQuantities = setOf("zero", "one", "two", "few", "many", "other")

        assertEquals("one", desktopAndroidPluralQuantity(Locale("ru"), 21, slavicQuantities))
        assertEquals("few", desktopAndroidPluralQuantity(Locale("ru"), 22, slavicQuantities))
        assertEquals("many", desktopAndroidPluralQuantity(Locale("ru"), 25, slavicQuantities))
        assertEquals("few", desktopAndroidPluralQuantity(Locale("pl"), 2, slavicQuantities))
        assertEquals("one", desktopAndroidPluralQuantity(Locale("fr"), 0, setOf("one", "other")))
        assertEquals("zero", desktopAndroidPluralQuantity(Locale("ar"), 0, arabicQuantities))
        assertEquals("other", desktopAndroidPluralQuantity(Locale("ja"), 1, setOf("other")))
    }

    @Test
    fun fallsBackToOtherPluralQuantityWhenPreferredIsUnavailable() {
        val selected = desktopAndroidPluralQuantity(Locale("ru"), 2, setOf("one", "other"))

        assertEquals("other", selected)
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

    @Test
    fun desktopLanguageSettingsStorePersistsLanguageAcrossInstances() {
        val tempDirectory = Files.createTempDirectory("episteme-desktop-language-test")
        val settingsFile = tempDirectory.resolve("language.properties").toFile()

        try {
            DesktopLanguageSettingsStore(settingsFile).save(DesktopLanguageSettings("pt_br"))

            assertEquals(
                "pt-BR",
                DesktopLanguageSettingsStore(settingsFile).load().languageTag
            )

            DesktopLanguageSettingsStore(settingsFile).save(DesktopLanguageSettings(null))

            assertEquals(
                null,
                DesktopLanguageSettingsStore(settingsFile).load().languageTag
            )
        } finally {
            settingsFile.delete()
            tempDirectory.toFile().delete()
        }
    }
}
