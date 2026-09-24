package com.aryan.reader

import androidx.annotation.StringRes
import com.aryan.reader.shared.sharedAppLanguageOptions
import com.aryan.reader.shared.sharedAppLanguageSearchMatches

data class AppLanguageOption(
    val tag: String?,
    @StringRes val labelRes: Int,
    val searchAliases: List<String> = emptyList()
)

/**
 * Bridges the shared language catalog to Android string resources. The tags,
 * label keys, search aliases and matching rules are owned by
 * [sharedAppLanguageOptions] (iOS renders the same list from the same keys), so
 * the two platforms can never drift apart: this table only resolves a key to its
 * `R.string` entry, and `getValue` fails fast if a key is ever missing.
 */
private val sharedLanguageLabelResources: Map<String, Int> = mapOf(
    "language_system_default" to R.string.language_system_default,
    "language_english" to R.string.language_english,
    "language_arabic" to R.string.language_arabic,
    "language_german" to R.string.language_german,
    "language_dutch" to R.string.language_dutch,
    "language_turkish" to R.string.language_turkish,
    "language_french" to R.string.language_french,
    "language_russian" to R.string.language_russian,
    "language_ukrainian" to R.string.language_ukrainian,
    "language_belarusian" to R.string.language_belarusian,
    "language_spanish" to R.string.language_spanish,
    "language_portuguese_brazilian" to R.string.language_portuguese_brazilian,
    "language_italian" to R.string.language_italian,
    "language_polish" to R.string.language_polish,
    "language_indonesian" to R.string.language_indonesian,
    "language_vietnamese" to R.string.language_vietnamese,
    "language_japanese" to R.string.language_japanese,
    "language_korean" to R.string.language_korean,
    "language_hindi" to R.string.language_hindi,
    "language_chinese_simplified" to R.string.language_chinese_simplified,
    "language_estonian" to R.string.language_estonian,
)

val appLanguageSelectionOptions: List<AppLanguageOption> = sharedAppLanguageOptions.map { option ->
    AppLanguageOption(
        tag = option.tag,
        labelRes = sharedLanguageLabelResources.getValue(option.labelKey),
        searchAliases = option.searchAliases,
    )
}

val systemAppLanguageOption = appLanguageSelectionOptions.first()

val supportedAppLanguageOptions = appLanguageSelectionOptions.drop(1)

/** Android renders `stringResource(labelRes)` and filters with the shared rules. */
fun AppLanguageOption.matchesLanguageSearch(label: String, query: String): Boolean =
    sharedAppLanguageSearchMatches(
        label = label,
        tag = tag,
        searchAliases = searchAliases,
        query = query,
    )

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
        SortOrder.DATE_ADDED_NEWEST -> R.string.sort_date_added_newest
        SortOrder.DATE_ADDED_OLDEST -> R.string.sort_date_added_oldest
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
