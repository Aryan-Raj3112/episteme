package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.SerialName

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
enum class HighlightStyle(val id: String) {
    @SerialName("background")
    BACKGROUND("background"),
    @SerialName("underline")
    UNDERLINE("underline"),
    @SerialName("wavy_underline")
    WAVY_UNDERLINE("wavy_underline"),
    @SerialName("strikethrough")
    STRIKETHROUGH("strikethrough");

    companion object {
        fun fromId(id: String?): HighlightStyle {
            return entries.firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) } ?: BACKGROUND
        }
    }
}

val DefaultEpubHighlightPaletteArgb: List<Int>
    get() = listOf(
        HighlightColor.YELLOW.color.toArgb(),
        HighlightColor.GREEN.color.toArgb(),
        HighlightColor.BLUE.color.toArgb(),
        HighlightColor.RED.color.toArgb()
    )

fun sanitizeEpubHighlightPalette(palette: List<Int>): List<Int> =
    palette.takeIf { it.size == 4 } ?: DefaultEpubHighlightPaletteArgb

fun legacyEpubHighlightColorForArgb(argb: Int): HighlightColor =
    HighlightColor.entries.firstOrNull { it.color.toArgb() == argb } ?: HighlightColor.YELLOW

fun legacyEpubHighlightColorOrNull(argb: Int): HighlightColor? =
    HighlightColor.entries.firstOrNull { it.color.toArgb() == argb }

fun epubHighlightColorTag(argb: Int): String =
    legacyEpubHighlightColorOrNull(argb)?.id ?: "custom_${argb.toUInt().toString(16)}"

fun epubHighlightColorFromToken(token: String): Pair<HighlightColor, Int?> {
    val trimmed = token.trim()
    val hex = trimmed.removePrefix("#")
    val parsedArgb = trimmed.toIntOrNull()
        ?: hex.takeIf { it.length == 6 || it.length == 8 }?.toLongOrNull(16)?.let { value ->
            if (hex.length == 6) (0xFF000000 or value).toInt() else value.toInt()
        }
    if (parsedArgb != null) return legacyEpubHighlightColorForArgb(parsedArgb) to parsedArgb
    return (HighlightColor.entries.firstOrNull { it.id == trimmed } ?: HighlightColor.YELLOW) to null
}
data class ReaderLocator(
    val chapterIndex: Int? = null,
    val chapterId: String? = null,
    val href: String? = null,
    val pageIndex: Int? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val blockIndex: Int? = null,
    val charOffset: Int? = null,
    val textQuote: String? = null,
    val cfi: String? = null
) {
    val hasTextRange: Boolean
        get() = startOffset != null && endOffset != null && endOffset >= startOffset
    val hasBlockPosition: Boolean
        get() = blockIndex != null && charOffset != null

    fun withFallbacks(
        chapterIndex: Int? = null,
        chapterId: String? = null,
        href: String? = null,
        pageIndex: Int? = null,
        startOffset: Int? = null,
        endOffset: Int? = null,
        blockIndex: Int? = null,
        charOffset: Int? = null,
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
            blockIndex = this.blockIndex ?: blockIndex,
            charOffset = this.charOffset ?: charOffset,
            textQuote = this.textQuote ?: textQuote,
            cfi = this.cfi ?: cfi
        )
    }

    /**
     * Whether [other] marks the same stretch of text, and so is the same highlight.
     *
     * Extent is part of identity, not just the start. Highlighting "beta" and then "beta gamma" in one
     * block share a block and a start; treating those as one highlight made the second silently
     * replace the first, losing its colour and any note on it. Two selections are the same highlight
     * only when they cover the same characters.
     *
     * Each side's extent is read from whichever field it has. Where one side's extent cannot be
     * determined the answer is false, so a duplicate is created rather than a highlight destroyed —
     * the cheaper error, and the visible one.
     */
    fun sameLocation(other: ReaderLocator): Boolean {
        val sameChapter = chapterIndex == null || other.chapterIndex == null || chapterIndex == other.chapterIndex
        if (!sameChapter) return false

        if (hasBlockPosition && other.hasBlockPosition) {
            if (blockIndex != other.blockIndex || charOffset != other.charOffset) return false
            val end = selectedLength ?: return false
            return other.selectedLength == end
        }

        if (hasTextRange && other.hasTextRange) {
            return startOffset == other.startOffset && endOffset == other.endOffset
        }

        if (pageIndex != null && other.pageIndex != null) {
            // Same guard as above: comparing two unknown extents would be null == null, which reads as
            // "the same stretch" and would make a page number alone enough to overwrite a highlight.
            val end = selectedLength ?: return false
            return pageIndex == other.pageIndex && other.selectedLength == end
        }

        val sameCfi = cfi != null && cfi == other.cfi
        if (!sameCfi) return false
        // A CFI that encodes only a position in the document cannot tell two ranges in the same place
        // apart, so the selected text has to agree too. Otherwise highlighting a longer span over a
        // shorter one inside one DOM node reads as the same highlight.
        return when {
            selectedLength != null && other.selectedLength != null -> selectedLength == other.selectedLength
            else -> textQuote != null && textQuote == other.textQuote
        }
    }

    /**
     * How many characters this locator covers, or null when it cannot say.
     *
     * Prefers the stored range's length, because that is measured in the chapter's own coordinates,
     * and falls back to the length of the selected text. A collapsed range is length zero.
     */
    val selectedLength: Int?
        get() = when {
            startOffset != null && endOffset != null -> (endOffset!! - startOffset!!).coerceAtLeast(0)
            charOffset != null && endOffset != null -> (endOffset!! - charOffset!!).coerceAtLeast(0)
            textQuote != null -> textQuote!!.length
            else -> null
        }

    companion object {
        fun fromLegacy(
            chapterIndex: Int? = null,
            cfi: String? = null,
            pageIndex: Int? = null,
            textQuote: String? = null
        ): ReaderLocator {
            val stableCfi = cfi?.toStableReaderPositionCfi()
            val desktopParts = stableCfi
                ?.takeIf { it.startsWith("desktop:") }
                ?.split(':')
                .orEmpty()
            val parsedChapterIndex = desktopParts.getOrNull(1)?.toIntOrNull()
            val possibleStartOffset = desktopParts.getOrNull(2)?.toIntOrNull()
            val possibleEndOffset = desktopParts.getOrNull(3)?.toIntOrNull()
            val androidLocatorParts = stableCfi
                ?.takeIf { it.startsWith("android-locator:") }
                ?.split(':')
                .orEmpty()
            val parsedAndroidChapterIndex = androidLocatorParts.getOrNull(1)?.toIntOrNull()
            val parsedBlockIndex = androidLocatorParts.getOrNull(2)?.toIntOrNull()
            val parsedCharOffset = androidLocatorParts.getOrNull(3)?.toIntOrNull()
                ?.takeIf { it >= 0 }
            val parsedAndroidEndOffset = parsedCharOffset
                ?.let { start -> textQuote?.takeIf { it.isNotBlank() }?.let { start + it.length } }
            val hasOffsetRange = desktopParts.size == 4 &&
                possibleStartOffset != null &&
                possibleEndOffset != null &&
                possibleStartOffset >= 0 &&
                possibleEndOffset >= possibleStartOffset &&
                possibleEndOffset - possibleStartOffset <= 100_000
            val parsedStartOffset = when {
                hasOffsetRange -> possibleStartOffset
                parsedBlockIndex != null && parsedAndroidEndOffset != null -> parsedCharOffset
                else -> null
            }
            val parsedEndOffset = when {
                hasOffsetRange -> possibleEndOffset
                parsedBlockIndex != null -> parsedAndroidEndOffset
                else -> null
            }
            val parsedPageIndex = when {
                pageIndex != null -> pageIndex
                desktopParts.size == 3 || desktopParts.size >= 5 || (desktopParts.size == 4 && !hasOffsetRange) ->
                    desktopParts.getOrNull(2)?.toIntOrNull()
                else -> null
            }
            return ReaderLocator(
                chapterIndex = chapterIndex ?: parsedChapterIndex ?: parsedAndroidChapterIndex,
                pageIndex = parsedPageIndex,
                startOffset = parsedStartOffset,
                endOffset = parsedEndOffset,
                blockIndex = parsedBlockIndex,
                charOffset = parsedCharOffset,
                textQuote = textQuote,
                cfi = stableCfi ?: cfi
            )
        }
    }
}

fun String.toStableReaderPositionCfi(): String {
    val trimmed = trim()
    if (!trimmed.startsWith("desktop-scroll:")) return trimmed
    return trimmed
        .split(':', limit = 4)
        .getOrNull(3)
        ?.takeIf { it.isNotBlank() }
        ?: trimmed
}

fun ReaderLocator.toStablePositionCfi(): String? {
    cfi
        ?.toStableReaderPositionCfi()
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it.startsWith("desktop-scroll:") || it.startsWith("desktop-scroll-page:") }
        ?.let { return it }

    val chapter = chapterIndex
    val start = startOffset
    val end = endOffset ?: start
    return when {
        chapter != null && blockIndex != null && charOffset != null ->
            "android-locator:$chapter:$blockIndex:$charOffset"
        chapter != null && start != null && end != null ->
            "desktop:$chapter:$start:$end"
        chapter != null && pageIndex != null ->
            "desktop:$chapter:$pageIndex"
        else -> null
    }
}

data class ReaderHighlightPalette(
    val colors: List<HighlightColor> = defaultColors
) {
    fun sanitized(): ReaderHighlightPalette {
        val knownColors = colors.filter { it in HighlightColor.entries }
        return copy(colors = knownColors.takeIf { it.size == PaletteSize } ?: defaultColors)
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
        const val PaletteSize: Int = 4
        val defaultColors: List<HighlightColor>
            get() = listOf(
                HighlightColor.YELLOW,
                HighlightColor.GREEN,
                HighlightColor.BLUE,
                HighlightColor.RED
            )
    }
}

/**
 * Names highlights that show reading position rather than reader intent.
 *
 * Both the producer ([ReaderTtsChunk.toHighlight]) and the consumers read this one constant, so
 * changing the id format cannot quietly leave painters and hit-testing disagreeing about which
 * highlights are real.
 */
const val TRANSIENT_BAND_ID_PREFIX = "tts_"

data class UserHighlight(
    val id: String,
    val cfi: String,
    val text: String,
    val color: HighlightColor,
    val chapterIndex: Int,
    val note: String? = null,
    val colorArgb: Int? = null,
    val style: HighlightStyle = HighlightStyle.BACKGROUND,
    val locator: ReaderLocator = ReaderLocator.fromLegacy(
        chapterIndex = chapterIndex,
        cfi = cfi,
        textQuote = text
    )
) {
    val effectiveColor: Color
        get() = colorArgb?.let { Color(it) } ?: color.color

    /**
     * Whether this is a transient reading-position band rather than something the reader owns.
     *
     * Read-aloud paints the current chunk through the highlight pipeline, so it arrives here shaped
     * exactly like a highlight the reader made. It is not one: it is not stored, it has no id the
     * reader can look up, and it must not be reported as selected. Painters draw it; hit-testing and
     * the selection sheet ignore it.
     */
    val isTransientPlaybackBand: Boolean
        get() = id.startsWith(TRANSIENT_BAND_ID_PREFIX)

    fun renderColor(legacyAlpha: Float): Color {
        val argb = colorArgb ?: return color.color.copy(alpha = legacyAlpha)
        val storedAlpha = (argb ushr 24) and 0xFF
        return Color(argb).let { storedColor ->
            if (storedAlpha >= 0xFF) storedColor.copy(alpha = legacyAlpha) else storedColor
        }
    }
}

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
