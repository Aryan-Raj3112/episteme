package com.aryan.reader.shared.pdf

/**
 * Script families exposed by the mobile PDF OCR controls.
 *
 * Android's ML Kit adapter and iOS Vision use different model identifiers, so
 * the shared value intentionally describes the user-facing choice while each
 * platform maps it to its native language list. [visionLanguageCodes] feeds
 * the Vision request on iOS; [androidMlKitLanguageTags] mirrors the languages
 * inside Android's bundled per-script ML Kit model so both engines see the
 * same language mix for a script family.
 */
enum class SharedPdfOcrLanguage(
    val id: String,
    val displayName: String,
    val displayNameKey: String,
    val visionLanguageCodes: List<String>,
    val androidMlKitLanguageTags: List<String>,
) {
    LATIN(
        id = "latin",
        displayName = "Latin",
        displayNameKey = "ocr_language_latin",
        visionLanguageCodes = listOf("en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR"),
        androidMlKitLanguageTags = listOf("en", "fr", "de", "es", "it", "pt"),
    ),
    DEVANAGARI(
        id = "devanagari",
        displayName = "Devanagari",
        displayNameKey = "ocr_language_devanagari",
        visionLanguageCodes = listOf("hi-IN", "mr-IN"),
        androidMlKitLanguageTags = listOf("hi", "mr", "sa"),
    ),
    CHINESE(
        id = "chinese",
        displayName = "Chinese",
        displayNameKey = "ocr_language_chinese",
        visionLanguageCodes = listOf("zh-Hans", "zh-Hant"),
        androidMlKitLanguageTags = listOf("zh-Hans", "zh-Hant"),
    ),
    JAPANESE(
        id = "japanese",
        displayName = "Japanese",
        displayNameKey = "ocr_language_japanese",
        visionLanguageCodes = listOf("ja-JP"),
        androidMlKitLanguageTags = listOf("ja"),
    ),
    KOREAN(
        id = "korean",
        displayName = "Korean",
        displayNameKey = "ocr_language_korean",
        visionLanguageCodes = listOf("ko-KR"),
        androidMlKitLanguageTags = listOf("ko"),
    );

    companion object {
        fun fromId(value: String?): SharedPdfOcrLanguage = entries.firstOrNull {
            it.id == value || it.name.equals(value, ignoreCase = true)
        } ?: LATIN
    }
}

/**
 * Whether the platform OCR engine can recognize this script.
 *
 * Android (ML Kit v2) bundles all five script models. On iOS the Vision engine
 * decides at runtime: Devanagari is absent on iOS 17/18 but recognized on
 * newer releases, so the iOS actual queries the live engine and shared UI
 * hides whatever the device cannot recognize.
 */
expect val SharedPdfOcrLanguage.isSupportedOnPlatform: Boolean

/**
 * Android benchmark for OCR language selection (PdfViewerScreen.kt:
 * executeWithOcrCheck + saveOcrLanguage): OCR-dependent actions open the
 * language dialog once, remember the explicit selection, and then run the
 * pending action. Dismissing cancels the pending action.
 */
expect object SharedPdfOcrGate {
    fun loadLanguage(): SharedPdfOcrLanguage
    fun hasUserSelectedLanguage(): Boolean
    fun markUserSelectedLanguage()
    fun persistLanguage(language: SharedPdfOcrLanguage)
}

/**
 * Resolves which languages the shared OCR picker should offer and persists the
 * chosen one. Shared by Android and iOS so both platforms gate OCR actions
 * identically; platform differences live in [SharedPdfOcrGate] and the
 * capability flag.
 */
class SharedPdfOcrLanguageController(
    private val loadStored: () -> SharedPdfOcrLanguage = { SharedPdfOcrGate.loadLanguage() },
    private val hasSelection: () -> Boolean = { SharedPdfOcrGate.hasUserSelectedLanguage() },
    private val persist: (SharedPdfOcrLanguage) -> Unit = { SharedPdfOcrGate.persistLanguage(it) },
    private val markSelected: () -> Unit = { SharedPdfOcrGate.markUserSelectedLanguage() },
) {
    /** Stored language, falling back to Latin when the stored one is unsupported. */
    fun loadInitialLanguage(): SharedPdfOcrLanguage {
        val stored = loadStored()
        return if (stored.isSupportedOnPlatform) stored else SharedPdfOcrLanguage.LATIN
    }

    /** Options for the shared picker: only scripts this platform's engine supports. */
    fun availableLanguages(): List<SharedPdfOcrLanguage> =
        SharedPdfOcrLanguage.entries.filter { it.isSupportedOnPlatform }

    fun onLanguageSelected(language: SharedPdfOcrLanguage) {
        persist(language)
        markSelected()
    }
}
