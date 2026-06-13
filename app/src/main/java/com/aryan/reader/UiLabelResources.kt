package com.aryan.reader

import androidx.annotation.StringRes
import java.text.Normalizer
import java.util.Locale

data class AppLanguageOption(
    val tag: String?,
    @StringRes val labelRes: Int,
    val searchAliases: List<String> = emptyList()
)

val systemAppLanguageOption = AppLanguageOption(
    tag = null,
    labelRes = R.string.language_system_default,
    searchAliases = listOf("system", "default", "device", "automatic")
)

val supportedAppLanguageOptions = listOf(
    AppLanguageOption("en", R.string.language_english, listOf("english")),
    AppLanguageOption("ar", R.string.language_arabic, listOf("arabic", "arabi")),
    AppLanguageOption("de", R.string.language_german, listOf("german", "deutsch")),
    AppLanguageOption("nl", R.string.language_dutch, listOf("dutch", "nederlands", "holland", "netherlands")),
    AppLanguageOption("tr", R.string.language_turkish, listOf("turkish", "turkce", "turkçe")),
    AppLanguageOption("fr", R.string.language_french, listOf("french", "francais", "français")),
    AppLanguageOption("ru", R.string.language_russian, listOf("russian", "russkiy", "русский")),
    AppLanguageOption("uk", R.string.language_ukrainian, listOf("ukrainian", "ukrayinska", "українська", "ukraine")),
    AppLanguageOption("be", R.string.language_belarusian, listOf("belarusian", "belarus", "belaruskaya")),
    AppLanguageOption("es", R.string.language_spanish, listOf("spanish", "espanol", "español")),
    AppLanguageOption(
        "pt-BR",
        R.string.language_portuguese_brazilian,
        listOf(
            "portuguese",
            "brazilian portuguese",
            "portugues",
            "português",
            "portugues brasileiro",
            "português brasileiro",
            "brasil",
            "brazil",
            "pt-br"
        )
    ),
    AppLanguageOption("it", R.string.language_italian, listOf("italian", "italiano", "italia", "italy")),
    AppLanguageOption("pl", R.string.language_polish, listOf("polish", "polski", "polska")),
    AppLanguageOption("id", R.string.language_indonesian, listOf("indonesian", "bahasa indonesia", "bahasa", "indonesia")),
    AppLanguageOption(
        "vi",
        R.string.language_vietnamese,
        listOf("vietnamese", "vietnam", "tieng viet", "tiếng việt")
    ),
    AppLanguageOption("ja", R.string.language_japanese, listOf("japanese", "nihongo", "日本語")),
    AppLanguageOption("ko", R.string.language_korean, listOf("korean", "hangul", "hangugeo", "한국어", "한글")),
    AppLanguageOption("hi", R.string.language_hindi, listOf("hindi", "devanagari", "हिंदी", "हिन्दी")),
    AppLanguageOption(
        tag = "zh-CN",
        labelRes = R.string.language_chinese_simplified,
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
        )
    ),
    AppLanguageOption("et", R.string.language_estonian, listOf("estonian", "eesti"))
)

val appLanguageSelectionOptions = listOf(systemAppLanguageOption) + supportedAppLanguageOptions

fun AppLanguageOption.matchesLanguageSearch(label: String, query: String): Boolean {
    val searchTokens = query.normalizedLanguageSearchTokens()
    if (searchTokens.isEmpty()) return true

    val searchableText = buildString {
        append(label)
        append(' ')
        append(tag.orEmpty())
        append(' ')
        append(searchAliases.joinToString(" "))
    }.normalizedLanguageSearchText()

    return searchTokens.all { token -> token in searchableText }
}

private fun String.normalizedLanguageSearchTokens(): List<String> =
    normalizedLanguageSearchText()
        .split(' ')
        .filter { it.isNotBlank() }

private fun String.normalizedLanguageSearchText(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
        .trim()

val AddBooksSource.labelRes: Int
    @StringRes get() = when (this) {
        AddBooksSource.UNSHELVED -> R.string.add_books_source_unshelved
        AddBooksSource.ALL_BOOKS -> R.string.add_books_source_all_books
    }

val AppThemeMode.labelRes: Int
    @StringRes get() = when (this) {
        AppThemeMode.SYSTEM -> R.string.app_theme_mode_system
        AppThemeMode.LIGHT -> R.string.app_theme_mode_light
        AppThemeMode.DARK -> R.string.app_theme_mode_dark
    }

val AppContrastOption.labelRes: Int
    @StringRes get() = when (this) {
        AppContrastOption.STANDARD -> R.string.app_contrast_standard
        AppContrastOption.MEDIUM -> R.string.app_contrast_medium
        AppContrastOption.HIGH -> R.string.app_contrast_high
    }

val SortOrder.labelRes: Int
    @StringRes get() = when (this) {
        SortOrder.RECENT -> R.string.sort_recent
        SortOrder.TITLE_ASC -> R.string.sort_title_az
        SortOrder.AUTHOR_ASC -> R.string.sort_author_az
        SortOrder.PERCENT_ASC -> R.string.sort_percent_asc
        SortOrder.PERCENT_DESC -> R.string.sort_percent_desc
        SortOrder.SIZE_ASC -> R.string.sort_size_smallest
        SortOrder.SIZE_DESC -> R.string.sort_size_biggest
    }

val ReadStatusFilter.labelRes: Int
    @StringRes get() = when (this) {
        ReadStatusFilter.ALL -> R.string.read_status_all
        ReadStatusFilter.UNREAD -> R.string.read_status_unread
        ReadStatusFilter.IN_PROGRESS -> R.string.read_status_in_progress
        ReadStatusFilter.COMPLETED -> R.string.read_status_completed
    }
