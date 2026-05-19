package com.aryan.reader.desktop

import com.aryan.reader.shared.ui.SharedStringResolver
import org.w3c.dom.Element
import java.io.InputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal fun loadDesktopStringResolver(
    locale: Locale = currentDesktopStringsLocale(),
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader
        ?: DesktopAndroidStringResources::class.java.classLoader
): SharedStringResolver {
    val resources = DesktopAndroidStringResources.load(locale = locale, classLoader = classLoader)
    return SharedStringResolver(resources::stringOrNull)
}

internal data class DesktopAndroidStringResources(
    private val strings: Map<String, String>
) {
    fun stringOrNull(name: String): String? = strings[name]

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
            return DesktopAndroidStringResources(fallback + localized)
        }

        private fun loadResourceMap(classLoader: ClassLoader, path: String): Map<String, String> {
            val stream = classLoader.getResourceAsStream(path) ?: return emptyMap()
            return stream.use(::parseAndroidStringXml)
        }
    }
}

internal fun desktopAndroidStringResourcePaths(locale: Locale): List<String> {
    val language = locale.language.takeIf { it.isNotBlank() } ?: return emptyList()
    val country = locale.country.takeIf { it.isNotBlank() }
    val exact = country?.let { androidValuesFolderFor(language, it) }
    val languageOnly = androidValuesFolderFor(language, null)
    return listOfNotNull(exact, languageOnly)
        .filterNot { it == "values" }
        .distinct()
        .map { "$DesktopAndroidStringsRoot/$it/strings.xml" }
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
