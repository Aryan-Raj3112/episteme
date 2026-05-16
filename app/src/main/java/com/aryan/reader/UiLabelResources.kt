package com.aryan.reader

import androidx.annotation.StringRes

data class AppLanguageOption(
    val tag: String?,
    @StringRes val labelRes: Int
)

val systemAppLanguageOption = AppLanguageOption(null, R.string.language_system_default)

val supportedAppLanguageOptions = listOf(
    AppLanguageOption("en", R.string.language_english),
    AppLanguageOption("ar", R.string.language_arabic),
    AppLanguageOption("de", R.string.language_german),
    AppLanguageOption("tr", R.string.language_turkish),
    AppLanguageOption("fr", R.string.language_french),
    AppLanguageOption("ru", R.string.language_russian),
    AppLanguageOption("es", R.string.language_spanish)
)

val appLanguageSelectionOptions = listOf(systemAppLanguageOption) + supportedAppLanguageOptions

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
