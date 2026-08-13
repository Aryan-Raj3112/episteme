package com.aryan.reader.shared

data class SharedAppLanguage(
    val tag: String?,
    val label: String,
)

val sharedAppLanguages: List<SharedAppLanguage> = listOf(
    SharedAppLanguage(null, "System default"),
    SharedAppLanguage("en", "English"),
    SharedAppLanguage("ar", "العربية"),
    SharedAppLanguage("de", "Deutsch"),
    SharedAppLanguage("nl", "Nederlands"),
    SharedAppLanguage("tr", "Türkçe"),
    SharedAppLanguage("fr", "Français"),
    SharedAppLanguage("ru", "Русский"),
    SharedAppLanguage("uk", "Українська"),
    SharedAppLanguage("be", "Беларуская"),
    SharedAppLanguage("es", "Español"),
    SharedAppLanguage("pt-BR", "Português (Brasil)"),
    SharedAppLanguage("it", "Italiano"),
    SharedAppLanguage("pl", "Polski"),
    SharedAppLanguage("id", "Bahasa Indonesia"),
    SharedAppLanguage("vi", "Tiếng Việt"),
    SharedAppLanguage("ja", "日本語"),
    SharedAppLanguage("ko", "한국어"),
    SharedAppLanguage("hi", "हिन्दी"),
    SharedAppLanguage("zh-CN", "简体中文"),
    SharedAppLanguage("et", "Eesti"),
)

fun sharedAppLanguageLabel(tag: String?): String {
    return sharedAppLanguages.firstOrNull { it.tag == tag }?.label
        ?: sharedAppLanguages.first().label
}
