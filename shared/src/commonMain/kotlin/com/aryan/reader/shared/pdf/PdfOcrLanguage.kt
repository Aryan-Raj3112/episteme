package com.aryan.reader.shared.pdf

/**
 * Script families exposed by the mobile PDF OCR controls.
 *
 * Android's ML Kit adapter and iOS Vision use different model identifiers, so
 * the shared value intentionally describes the user-facing choice while each
 * platform maps it to its native language list.
 */
enum class SharedPdfOcrLanguage(
    val id: String,
    val displayName: String,
    val visionLanguageCodes: List<String>,
) {
    LATIN(
        id = "latin",
        displayName = "Latin",
        visionLanguageCodes = listOf("en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR"),
    ),
    DEVANAGARI(
        id = "devanagari",
        displayName = "Devanagari",
        visionLanguageCodes = listOf("hi-IN"),
    ),
    CHINESE(
        id = "chinese",
        displayName = "Chinese",
        visionLanguageCodes = listOf("zh-Hans", "zh-Hant"),
    ),
    JAPANESE(
        id = "japanese",
        displayName = "Japanese",
        visionLanguageCodes = listOf("ja-JP"),
    ),
    KOREAN(
        id = "korean",
        displayName = "Korean",
        visionLanguageCodes = listOf("ko-KR"),
    );

    companion object {
        fun fromId(value: String?): SharedPdfOcrLanguage = entries.firstOrNull {
            it.id == value || it.name.equals(value, ignoreCase = true)
        } ?: LATIN
    }
}
