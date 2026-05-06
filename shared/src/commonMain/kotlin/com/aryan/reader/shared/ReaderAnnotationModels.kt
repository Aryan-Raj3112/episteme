package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color

data class EpubBookmark(
    val cfi: String,
    val chapterTitle: String,
    val label: String? = null,
    val snippet: String,
    val pageInChapter: Int?,
    val totalPagesInChapter: Int?,
    val chapterIndex: Int,
    val locator: ReaderLocator = ReaderLocator.fromLegacy(
        chapterIndex = chapterIndex,
        cfi = cfi,
        pageIndex = pageInChapter?.minus(1),
        textQuote = snippet
    )
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

data class ReaderLocator(
    val chapterIndex: Int? = null,
    val chapterId: String? = null,
    val href: String? = null,
    val pageIndex: Int? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val textQuote: String? = null,
    val cfi: String? = null
) {
    val hasTextRange: Boolean
        get() = startOffset != null && endOffset != null && endOffset >= startOffset

    fun withFallbacks(
        chapterIndex: Int? = null,
        chapterId: String? = null,
        href: String? = null,
        pageIndex: Int? = null,
        startOffset: Int? = null,
        endOffset: Int? = null,
        textQuote: String? = null,
        cfi: String? = null
    ): ReaderLocator {
        return copy(
            chapterIndex = this.chapterIndex ?: chapterIndex,
            chapterId = this.chapterId ?: chapterId,
            href = this.href ?: href,
            pageIndex = this.pageIndex ?: pageIndex,
            startOffset = this.startOffset ?: startOffset,
            endOffset = this.endOffset ?: endOffset,
            textQuote = this.textQuote ?: textQuote,
            cfi = this.cfi ?: cfi
        )
    }

    fun sameLocation(other: ReaderLocator): Boolean {
        val sameChapter = chapterIndex == null || other.chapterIndex == null || chapterIndex == other.chapterIndex
        if (!sameChapter) return false

        if (hasTextRange && other.hasTextRange) {
            return startOffset == other.startOffset && endOffset == other.endOffset
        }

        if (pageIndex != null && other.pageIndex != null) {
            return pageIndex == other.pageIndex
        }

        return cfi != null && cfi == other.cfi
    }

    companion object {
        fun fromLegacy(
            chapterIndex: Int? = null,
            cfi: String? = null,
            pageIndex: Int? = null,
            textQuote: String? = null
        ): ReaderLocator {
            val desktopParts = cfi
                ?.takeIf { it.startsWith("desktop:") }
                ?.split(':')
                .orEmpty()
            val parsedChapterIndex = desktopParts.getOrNull(1)?.toIntOrNull()
            val possibleStartOffset = desktopParts.getOrNull(2)?.toIntOrNull()
            val possibleEndOffset = desktopParts.getOrNull(3)?.toIntOrNull()
            val hasOffsetRange = desktopParts.size == 4 &&
                possibleStartOffset != null &&
                possibleEndOffset != null &&
                possibleStartOffset >= 0 &&
                possibleEndOffset >= possibleStartOffset &&
                possibleEndOffset - possibleStartOffset <= 100_000
            val parsedStartOffset = if (hasOffsetRange) possibleStartOffset else null
            val parsedEndOffset = if (hasOffsetRange) possibleEndOffset else null
            val parsedPageIndex = when {
                pageIndex != null -> pageIndex
                desktopParts.size == 3 || desktopParts.size >= 5 || (desktopParts.size == 4 && !hasOffsetRange) ->
                    desktopParts.getOrNull(2)?.toIntOrNull()
                else -> null
            }
            return ReaderLocator(
                chapterIndex = chapterIndex ?: parsedChapterIndex,
                pageIndex = parsedPageIndex,
                startOffset = parsedStartOffset,
                endOffset = parsedEndOffset,
                textQuote = textQuote,
                cfi = cfi
            )
        }
    }
}

data class ReaderHighlightPalette(
    val colors: List<HighlightColor> = defaultColors
) {
    fun sanitized(): ReaderHighlightPalette {
        val distinct = colors.distinct().filter { it in HighlightColor.entries }
        return copy(colors = distinct.ifEmpty { defaultColors })
    }

    fun contains(color: HighlightColor): Boolean {
        return color in sanitized().colors
    }

    fun withColor(color: HighlightColor, enabled: Boolean): ReaderHighlightPalette {
        val next = if (enabled) {
            colors + color
        } else {
            colors - color
        }
        return copy(colors = next).sanitized()
    }

    companion object {
        val defaultColors: List<HighlightColor>
            get() = listOf(
                HighlightColor.YELLOW,
                HighlightColor.GREEN,
                HighlightColor.BLUE,
                HighlightColor.RED,
                HighlightColor.PURPLE,
                HighlightColor.ORANGE
            )
    }
}

data class UserHighlight(
    val id: String,
    val cfi: String,
    val text: String,
    val color: HighlightColor,
    val chapterIndex: Int,
    val note: String? = null,
    val locator: ReaderLocator = ReaderLocator.fromLegacy(
        chapterIndex = chapterIndex,
        cfi = cfi,
        textQuote = text
    )
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
