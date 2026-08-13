package com.aryan.reader.shared.ios

import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.sharedAppLanguages
import com.aryan.reader.shared.ui.SharedStringResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSUserDefaults

internal fun currentIosLanguageTag(): String {
    val preferred = NSUserDefaults.standardUserDefaults
        .stringArrayForKey("AppleLanguages")
        ?.firstOrNull()
        ?.toString()
        .orEmpty()
    return normalizeIosLanguageTag(preferred) ?: "en"
}

internal suspend fun loadIosStringResolver(languageTag: String?): SharedStringResolver {
    val effectiveTag = normalizeIosLanguageTag(languageTag ?: currentIosLanguageTag()) ?: "en"
    val fallback = loadIosCatalog("en")
    val localized = if (effectiveTag == "en") fallback else loadIosCatalog(effectiveTag)
    val strings = fallback.strings + localized.strings
    val plurals = fallback.plurals + localized.plurals
    val keysByEnglishText = fallback.strings.entries
        .groupBy(keySelector = { it.value }, valueTransform = { it.key })
    return SharedStringResolver(
        resolve = strings::get,
        resolveQuantity = { name, quantity ->
            val options = plurals[name].orEmpty()
            options[iosPluralQuantity(effectiveTag, quantity)]
                ?: options["other"]
                ?: options.values.firstOrNull()
        },
        resolveLiteral = { text ->
            keysByEnglishText[text]
                ?.firstNotNullOfOrNull { key -> localized.strings[key] }
        },
    )
}

private data class IosStringCatalog(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
)

private suspend fun loadIosCatalog(tag: String): IosStringCatalog {
    val raw = runCatching {
        Res.readBytes("files/localization/$tag.json").decodeToString()
    }.getOrNull() ?: return IosStringCatalog(emptyMap(), emptyMap())
    val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        ?: return IosStringCatalog(emptyMap(), emptyMap())
    val strings = root["strings"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
    val plurals = root["plurals"]?.jsonObject.orEmpty().mapValues { (_, value) ->
        value.jsonObject.mapValues { it.value.jsonPrimitive.content }
    }
    return IosStringCatalog(strings, plurals)
}

private fun normalizeIosLanguageTag(tag: String): String? {
    val normalized = tag.replace('_', '-')
    val supported = sharedAppLanguages.mapNotNullTo(mutableSetOf()) { it.tag }
    return when {
        normalized.equals("pt-BR", ignoreCase = true) -> "pt-BR"
        normalized.startsWith("zh", ignoreCase = true) -> "zh-CN"
        else -> normalized.substringBefore('-').let { language ->
            supported.firstOrNull { it.equals(language, ignoreCase = true) }
        }
    }
}

private fun iosPluralQuantity(tag: String, quantity: Int): String {
    val value = kotlin.math.abs(quantity)
    val mod10 = value % 10
    val mod100 = value % 100
    return when (tag.substringBefore('-')) {
        "ar" -> when {
            value == 0 -> "zero"
            value == 1 -> "one"
            value == 2 -> "two"
            mod100 in 3..10 -> "few"
            mod100 in 11..99 -> "many"
            else -> "other"
        }
        "ru", "uk", "be" -> when {
            mod10 == 1 && mod100 != 11 -> "one"
            mod10 in 2..4 && mod100 !in 12..14 -> "few"
            else -> "many"
        }
        "pl" -> when {
            value == 1 -> "one"
            mod10 in 2..4 && mod100 !in 12..14 -> "few"
            else -> "many"
        }
        "fr" -> if (value == 0 || value == 1) "one" else "other"
        else -> if (value == 1) "one" else "other"
    }
}
