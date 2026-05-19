package com.aryan.reader.desktop

import com.aryan.reader.shared.ui.SharedStringResolver
import org.w3c.dom.Element
import java.io.InputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

internal fun loadDesktopStringResolver(
    locale: Locale = currentDesktopStringsLocale(),
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader
        ?: DesktopAndroidStringResources::class.java.classLoader
): SharedStringResolver {
    val resources = DesktopAndroidStringResources.load(locale = locale, classLoader = classLoader)
    return SharedStringResolver(
        resolve = resources::stringOrNull,
        resolveQuantity = resources::quantityStringOrNull
    )
}

internal data class DesktopAndroidStringResources(
    private val strings: Map<String, String>,
    private val plurals: Map<String, Map<String, String>>,
    private val locale: Locale
) {
    fun stringOrNull(name: String): String? = strings[name]

    fun quantityStringOrNull(name: String, quantity: Int): String? {
        val items = plurals[name].orEmpty()
        if (items.isEmpty()) return null
        val quantityName = desktopAndroidPluralQuantity(locale, quantity, items.keys)
        return items[quantityName] ?: items["other"] ?: items.values.firstOrNull()
    }

    companion object {
        fun load(
            locale: Locale,
            classLoader: ClassLoader
        ): DesktopAndroidStringResources {
            val fallback = loadResourceMap(classLoader, "$DesktopAndroidStringsRoot/values/strings.xml")
            val localized = desktopAndroidStringResourcePaths(locale)
                .asReversed()
                .fold(emptyMap<String, String>()) { merged, path ->
                    merged + loadResourceMap(classLoader, path)
                }
            val fallbackPlurals = loadPluralMap(classLoader, "$DesktopAndroidStringsRoot/values/plurals.xml")
            val localizedPlurals = desktopAndroidPluralResourcePaths(locale)
                .asReversed()
                .fold(emptyMap<String, Map<String, String>>()) { merged, path ->
                    merged + loadPluralMap(classLoader, path)
                }
            return DesktopAndroidStringResources(
                strings = fallback + localized,
                plurals = fallbackPlurals + localizedPlurals,
                locale = locale
            )
        }

        private fun loadResourceMap(classLoader: ClassLoader, path: String): Map<String, String> {
            val stream = classLoader.getResourceAsStream(path) ?: return emptyMap()
            return stream.use(::parseAndroidStringXml)
        }

        private fun loadPluralMap(classLoader: ClassLoader, path: String): Map<String, Map<String, String>> {
            val stream = classLoader.getResourceAsStream(path) ?: return emptyMap()
            return stream.use(::parseAndroidPluralXml)
        }
    }
}

internal fun desktopAndroidStringResourcePaths(locale: Locale): List<String> {
    return desktopAndroidResourcePaths(locale, "strings.xml")
}

internal fun desktopAndroidPluralResourcePaths(locale: Locale): List<String> {
    return desktopAndroidResourcePaths(locale, "plurals.xml")
}

private fun desktopAndroidResourcePaths(locale: Locale, fileName: String): List<String> {
    val language = locale.language.takeIf { it.isNotBlank() } ?: return emptyList()
    val country = locale.country.takeIf { it.isNotBlank() }
    val exact = country?.let { androidValuesFolderFor(language, it) }
    val languageOnly = androidValuesFolderFor(language, null)
    return listOfNotNull(exact, languageOnly)
        .filterNot { it == "values" }
        .distinct()
        .map { "$DesktopAndroidStringsRoot/$it/$fileName" }
}

internal fun currentDesktopStringsLocale(): Locale {
    val overrideTag = System.getProperty(DesktopLocaleProperty)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return overrideTag?.let(Locale::forLanguageTag)?.takeUnless { it.language.isBlank() }
        ?: Locale.getDefault()
}

internal fun desktopLocaleForLanguageTag(languageTag: String?): Locale {
    return normalizeDesktopLanguageTag(languageTag)
        ?.let(Locale::forLanguageTag)
        ?.takeUnless { it.language.isBlank() }
        ?: currentDesktopStringsLocale()
}

internal fun parseAndroidStringXml(stream: InputStream): Map<String, String> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isIgnoringComments = true
        isNamespaceAware = false
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }
    val document = factory.newDocumentBuilder().parse(stream)
    val nodes = document.getElementsByTagName("string")
    val strings = linkedMapOf<String, String>()
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: continue
        strings[name] = element.textContent.orEmpty().decodeAndroidStringEscapes()
    }
    return strings
}

internal fun parseAndroidPluralXml(stream: InputStream): Map<String, Map<String, String>> {
    val factory = secureAndroidXmlDocumentBuilderFactory()
    val document = factory.newDocumentBuilder().parse(stream)
    val nodes = document.getElementsByTagName("plurals")
    val plurals = linkedMapOf<String, Map<String, String>>()
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: continue
        val items = linkedMapOf<String, String>()
        val itemNodes = element.getElementsByTagName("item")
        for (itemIndex in 0 until itemNodes.length) {
            val item = itemNodes.item(itemIndex) as? Element ?: continue
            val quantity = item.getAttribute("quantity").takeIf { it.isNotBlank() } ?: continue
            items[quantity] = item.textContent.orEmpty().decodeAndroidStringEscapes()
        }
        if (items.isNotEmpty()) plurals[name] = items
    }
    return plurals
}

internal fun desktopAndroidPluralQuantity(locale: Locale, quantity: Int, availableQuantities: Set<String>): String {
    val preferred = desktopAndroidPluralQuantity(locale, quantity)
    return when {
        preferred in availableQuantities -> preferred
        "other" in availableQuantities -> "other"
        else -> availableQuantities.firstOrNull().orEmpty()
    }
}

private fun desktopAndroidPluralQuantity(locale: Locale, quantity: Int): String {
    val language = locale.language.lowercase(Locale.ROOT)
    val absolute = abs(quantity)
    return when (language) {
        "ar" -> {
            val mod100 = absolute % 100
            when {
                absolute == 0 -> "zero"
                absolute == 1 -> "one"
                absolute == 2 -> "two"
                mod100 in 3..10 -> "few"
                mod100 in 11..99 -> "many"
                else -> "other"
            }
        }
        "ru", "uk", "be" -> {
            val mod10 = absolute % 10
            val mod100 = absolute % 100
            when {
                mod10 == 1 && mod100 != 11 -> "one"
                mod10 in 2..4 && mod100 !in 12..14 -> "few"
                mod10 == 0 || mod10 in 5..9 || mod100 in 11..14 -> "many"
                else -> "other"
            }
        }
        "pl" -> {
            val mod10 = absolute % 10
            val mod100 = absolute % 100
            when {
                absolute == 1 -> "one"
                mod10 in 2..4 && mod100 !in 12..14 -> "few"
                mod10 == 0 || mod10 == 1 || mod10 in 5..9 || mod100 in 12..14 -> "many"
                else -> "other"
            }
        }
        "fr" -> if (absolute == 0 || absolute == 1) "one" else "other"
        "ja", "ko", "zh", "vi", "id", "in", "tr" -> "other"
        else -> if (absolute == 1) "one" else "other"
    }
}

private fun secureAndroidXmlDocumentBuilderFactory(): DocumentBuilderFactory {
    return DocumentBuilderFactory.newInstance().apply {
        isIgnoringComments = true
        isNamespaceAware = false
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }
}

private fun androidValuesFolderFor(language: String, country: String?): String {
    val normalizedLanguage = language.lowercase(Locale.ROOT)
    val resourceLanguage = if (normalizedLanguage == "id") "in" else normalizedLanguage
    return if (country.isNullOrBlank()) {
        if (resourceLanguage == "en") "values" else "values-$resourceLanguage"
    } else {
        "values-$resourceLanguage-r${country.uppercase(Locale.ROOT)}"
    }
}

internal fun normalizeDesktopLanguageTag(languageTag: String?): String? {
    val normalizedInput = languageTag
        ?.trim()
        ?.replace('_', '-')
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val canonicalInput = when {
        normalizedInput.equals("in", ignoreCase = true) -> "id"
        normalizedInput.startsWith("in-", ignoreCase = true) -> "id-${normalizedInput.substringAfter('-')}"
        else -> normalizedInput
    }
    val locale = Locale.forLanguageTag(canonicalInput).takeUnless { it.language.isBlank() } ?: return null
    val language = when (locale.language) {
        "in" -> "id"
        else -> locale.language
    }
    val country = locale.country.takeIf { it.isNotBlank() }
    return if (country == null) {
        language
    } else {
        "$language-${country.uppercase(Locale.ROOT)}"
    }
}

private fun String.decodeAndroidStringEscapes(): String {
    return replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
}

private const val DesktopAndroidStringsRoot = "desktop-android-res"
private const val DesktopLocaleProperty = "episteme.desktop.locale"
