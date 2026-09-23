package com.aryan.reader.shared

/**
 * One selectable app language.
 *
 * Android owns the labels as string resources (`language_english`, ...), so the
 * shared entry carries the resource *key* instead of a translated string: iOS
 * resolves it through its generated catalog (`readerString(labelKey, label)`),
 * Android maps it to `R.string` in `UiLabelResources.kt`. The search aliases and
 * the matching rules live here so both platforms filter the list identically
 * (Android's `LanguageSelectionDialog` renders exactly this list).
 */
data class SharedAppLanguageOption(
    val tag: String?,
    val labelKey: String,
    /** Android's `values/` label — the fallback when the key cannot resolve. */
    val label: String,
    val searchAliases: List<String> = emptyList(),
)

val sharedAppLanguageOptions: List<SharedAppLanguageOption> = listOf(
    SharedAppLanguageOption(
        tag = null,
        labelKey = "language_system_default",
        label = "System default",
        searchAliases = listOf("system", "default", "device", "automatic"),
    ),
    SharedAppLanguageOption("en", "language_english", "English", listOf("english")),
    SharedAppLanguageOption("ar", "language_arabic", "Arabic", listOf("arabic", "arabi")),
    SharedAppLanguageOption("de", "language_german", "German", listOf("german", "deutsch")),
    SharedAppLanguageOption("nl", "language_dutch", "Dutch", listOf("dutch", "nederlands", "holland", "netherlands")),
    SharedAppLanguageOption("tr", "language_turkish", "Turkish", listOf("turkish", "turkce", "turkçe")),
    SharedAppLanguageOption("fr", "language_french", "French", listOf("french", "francais", "français")),
    SharedAppLanguageOption("ru", "language_russian", "Russian", listOf("russian", "russkiy", "русский")),
    SharedAppLanguageOption("uk", "language_ukrainian", "Ukrainian", listOf("ukrainian", "ukrayinska", "українська", "ukraine")),
    SharedAppLanguageOption("be", "language_belarusian", "Belarusian", listOf("belarusian", "belarus", "belaruskaya")),
    SharedAppLanguageOption("es", "language_spanish", "Spanish", listOf("spanish", "espanol", "español")),
    SharedAppLanguageOption(
        tag = "pt-BR",
        labelKey = "language_portuguese_brazilian",
        label = "Portuguese (Brazil)",
        searchAliases = listOf(
            "portuguese",
            "brazilian portuguese",
            "portugues",
            "português",
            "portugues brasileiro",
            "português brasileiro",
            "brasil",
            "brazil",
            "pt-br",
        ),
    ),
    SharedAppLanguageOption("it", "language_italian", "Italian", listOf("italian", "italiano", "italia", "italy")),
    SharedAppLanguageOption("pl", "language_polish", "Polish", listOf("polish", "polski", "polska")),
    SharedAppLanguageOption("id", "language_indonesian", "Indonesian", listOf("indonesian", "bahasa indonesia", "bahasa", "indonesia")),
    SharedAppLanguageOption("vi", "language_vietnamese", "Vietnamese", listOf("vietnamese", "vietnam", "tieng viet", "tiếng việt")),
    SharedAppLanguageOption("ja", "language_japanese", "Japanese", listOf("japanese", "nihongo", "日本語")),
    SharedAppLanguageOption("ko", "language_korean", "Korean", listOf("korean", "hangul", "hangugeo", "한국어", "한글")),
    SharedAppLanguageOption("hi", "language_hindi", "Hindi", listOf("hindi", "devanagari", "हिंदी", "हिन्दी")),
    SharedAppLanguageOption(
        tag = "zh-CN",
        labelKey = "language_chinese_simplified",
        label = "Chinese (Simplified)",
        searchAliases = listOf(
            "chinese",
            "simplified chinese",
            "mandarin",
            "zhongwen",
            "jian ti zhong wen",
            "zh-hans",
            "zh-cn",
            "中文",
            "简体中文",
        ),
    ),
    SharedAppLanguageOption("et", "language_estonian", "Estonian", listOf("estonian", "eesti")),
)

fun sharedAppLanguageOption(tag: String?): SharedAppLanguageOption =
    sharedAppLanguageOptions.firstOrNull { it.tag == tag } ?: sharedAppLanguageOptions.first()

fun sharedAppLanguageSearchAliases(tag: String?): List<String> = sharedAppLanguageOption(tag).searchAliases
data class SharedAppLanguage(
    val tag: String?,
    val label: String,
)

/**
 * Kept for the iOS string resolver, which only needs the supported tags.
 */
val sharedAppLanguages: List<SharedAppLanguage> =
    sharedAppLanguageOptions.map { SharedAppLanguage(it.tag, it.label) }

fun sharedAppLanguageLabel(tag: String?): String = sharedAppLanguageOption(tag).label

/**
 * Android's `UiLabelResources.matchesLanguageSearch`, moved here so iOS filters
 * the list by the same tokens. Android normalizes with `java.text.Normalizer`
 * (NFD + strip combining marks); common Kotlin has neither, so the same result
 * is produced by dropping combining marks directly and folding the Latin
 * diacritics these languages actually use — "turkce" still finds "Türkçe" and
 * "espanol" still finds "Español", which is what the Android aliases assert.
 */
fun sharedAppLanguageSearchMatches(
    label: String,
    tag: String?,
    searchAliases: List<String>,
    query: String,
): Boolean {
    val tokens = sharedNormalizeLanguageSearchText(query)
        .split(' ')
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true

    val searchable = sharedNormalizeLanguageSearchText(
        buildString {
            append(label)
            append(' ')
            append(tag.orEmpty())
            append(' ')
            append(searchAliases.joinToString(" "))
        }
    )
    return tokens.all { token -> token in searchable }
}

fun sharedNormalizeLanguageSearchText(text: String): String = buildString {
    text.lowercase().forEach { character ->
        // Combining marks left by a decomposed (NFD) input.
        if (character.code in 0x0300..0x036F) return@forEach
        val folded = sharedLatinFold(character)
        append(if (folded.isLetterOrDigit()) folded else ' ')
    }
}.split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ")

private fun sharedLatinFold(character: Char): Char =
    sharedLanguageDiacriticFold[character] ?: character

private val sharedLanguageDiacriticFold: Map<Char, Char> = buildMap {
    fun fold(target: Char, variants: String) {
        variants.forEach { put(it, target) }
    }
    fold('a', "áàâäãåāăą")
    fold('c', "çčćĉċ")
    fold('d', "ďđ")
    fold('e', "éèêëěēėę")
    fold('g', "ğģġ")
    fold('i', "íìîïīįı")
    fold('l', "łľĺ")
    fold('n', "ñńňņ")
    fold('o', "óòôöõøōő")
    fold('r', "řŕ")
    fold('s', "śšşș")
    fold('t', "ťţț")
    fold('u', "úùûüůūų")
    fold('y', "ýÿ")
    fold('z', "źżž")
}
