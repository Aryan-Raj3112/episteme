package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color

data class EpubBookmark(
    val cfi: String,
    val chapterTitle: String,
    val label: String? = null,
    val snippet: String,
    val pageInChapter: Int?,
    val totalPagesInChapter: Int?,
    val chapterIndex: Int
)

enum class HighlightColor(val id: String, val color: Color, val cssClass: String) {
    YELLOW("yellow", Color(0xFFFBC02D), "user-highlight-yellow"),
    GREEN("green", Color(0xFF388E3C), "user-highlight-green"),
    BLUE("blue", Color(0xFF1976D2), "user-highlight-blue"),
    RED("red", Color(0xFFD32F2F), "user-highlight-red"),
    PURPLE("purple", Color(0xFF7B1FA2), "user-highlight-purple"),
    ORANGE("orange", Color(0xFFF57C00), "user-highlight-orange"),
    CYAN("cyan", Color(0xFF0097A7), "user-highlight-cyan"),
    MAGENTA("magenta", Color(0xFFC2185B), "user-highlight-magenta"),
    LIME("lime", Color(0xFFAFB42B), "user-highlight-lime"),
    PINK("pink", Color(0xFFE91E63), "user-highlight-pink"),
    TEAL("teal", Color(0xFF00796B), "user-highlight-teal"),
    INDIGO("indigo", Color(0xFF303F9F), "user-highlight-indigo"),
    BLACK("black", Color(0xFF424242), "user-highlight-black"),
    WHITE("white", Color(0xFFF5F5F5), "user-highlight-white")
}

data class UserHighlight(
    val id: String,
    val cfi: String,
    val text: String,
    val color: HighlightColor,
    val chapterIndex: Int,
    val note: String? = null
)

fun escapeJsString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
}
