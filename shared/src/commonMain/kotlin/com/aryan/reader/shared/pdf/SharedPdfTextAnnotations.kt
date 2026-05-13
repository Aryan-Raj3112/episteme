package com.aryan.reader.shared.pdf

import androidx.compose.ui.unit.IntSize
import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable
data class SharedPdfTextStyleConfig(
    val colorArgb: Int = 0xFF000000.toInt(),
    val backgroundColorArgb: Int = 0x00000000,
    val fontSize: Float = 16f,
    val pageRelativeFontSize: Float? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val fontPath: String? = null,
    val fontName: String? = null
)

@Serializable
data class SharedPdfTextFontPreset(
    val name: String,
    val fontPath: String? = null
)

enum class SharedPdfTextResizeHandle {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    RIGHT_CENTER,
    BOTTOM_RIGHT,
    BOTTOM_CENTER,
    BOTTOM_LEFT,
    LEFT_CENTER
}

@Serializable
data class SharedPdfTextDraft(
    val id: String,
    val pageIndex: Int,
    val bounds: PdfPageBounds,
    val text: String = "",
    val style: SharedPdfTextStyleConfig = SharedPdfTextStyleConfig(),
    val createdAt: Long = 0L,
    val isManuallySized: Boolean = false
)

object SharedPdfTextAnnotationDefaults {
    private const val AndroidTextBoxFontReferencePx = 500f
    private const val MinPageRelativeFontSize = 0.012f
    private const val MaxPageRelativeFontSize = 0.12f

    val fontSizes: List<Float> = listOf(12f, 14f, 16f, 18f, 20f, 24f, 30f)

    val fontPresets: List<SharedPdfTextFontPreset> = listOf(
        SharedPdfTextFontPreset("Default"),
        SharedPdfTextFontPreset("Merriweather", "asset:fonts/merriweather.ttf"),
        SharedPdfTextFontPreset("Lato", "asset:fonts/lato.ttf"),
        SharedPdfTextFontPreset("Lora", "asset:fonts/lora.ttf"),
        SharedPdfTextFontPreset("Roboto Mono", "asset:fonts/roboto_mono.ttf"),
        SharedPdfTextFontPreset("Lexend", "asset:fonts/lexend.ttf")
    )

    val textColorPalette: List<Int>
        get() = SharedPdfAnnotationDefaults.penPalette

    val backgroundColorPalette: List<Int> = listOf(
        0x00000000,
        0x8CFF9800.toInt(),
        0x8CFFEB3B.toInt(),
        0x8C81C784.toInt(),
        0x8C64B5F6.toInt(),
        0x8CE1BEE7.toInt()
    )

    fun normalizeTextDraft(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
    }

    fun createAnnotation(
        id: String,
        pageIndex: Int,
        anchor: PdfPagePoint,
        canvasSize: IntSize,
        text: String,
        style: SharedPdfTextStyleConfig,
        createdAt: Long
    ): SharedPdfAnnotation {
        val cleanText = normalizeTextDraft(text)
        return SharedPdfAnnotation(
            id = id,
            pageIndex = pageIndex,
            kind = PdfAnnotationKind.TEXT,
            tool = PdfInkTool.TEXT,
            bounds = boundsForPlacedText(anchor, canvasSize, cleanText, style),
            text = cleanText,
            colorArgb = style.colorArgb,
            backgroundArgb = style.backgroundColorArgb,
            strokeWidth = SharedPdfAnnotationDefaults.configFor(PdfInkTool.TEXT).strokeWidth,
            fontSize = style.fontSize,
            pageRelativeFontSize = style.sharedPdfTextPageRelativeFontSize(),
            isBold = style.isBold,
            isItalic = style.isItalic,
            isUnderline = style.isUnderline,
            isStrikeThrough = style.isStrikeThrough,
            fontPath = style.fontPath,
            fontName = style.fontName,
            createdAt = createdAt
        )
    }

    fun createDraft(
        id: String,
        pageIndex: Int,
        anchor: PdfPagePoint,
        canvasSize: IntSize,
        style: SharedPdfTextStyleConfig,
        createdAt: Long
    ): SharedPdfTextDraft {
        return SharedPdfTextDraft(
            id = id,
            pageIndex = pageIndex,
            bounds = boundsForPlacedText(anchor, canvasSize, " ", style),
            text = "",
            style = style,
            createdAt = createdAt
        )
    }

    fun boundsForPlacedText(
        anchor: PdfPagePoint,
        canvasSize: IntSize,
        text: String,
        style: SharedPdfTextStyleConfig
    ): PdfPageBounds {
        val widthPx = canvasSize.width.coerceAtLeast(1).toFloat()
        val heightPx = canvasSize.height.coerceAtLeast(1).toFloat()
        val fontSizePx = style.sharedPdfTextFontSizePx(canvasSize)
        val widthNorm = estimateWidthNorm(text, fontSizePx, widthPx).coerceIn(0.18f, 0.62f)
        val lineCount = estimateLineCount(text, fontSizePx, widthPx * widthNorm)
        val heightNorm = (((fontSizePx * 1.35f * lineCount) + 14f) / heightPx).coerceIn(0.04f, 0.36f)
        val left = anchor.x.coerceIn(0f, 1f - widthNorm)
        val top = anchor.y.coerceIn(0f, 1f - heightNorm)
        return PdfPageBounds(
            left = left,
            top = top,
            right = left + widthNorm,
            bottom = top + heightNorm
        )
    }

    fun estimateLineCount(text: String, fontSize: Float, widthPx: Float): Int {
        if (text.isBlank()) return 1
        val averageCharWidth = (fontSize * 0.55f).coerceAtLeast(1f)
        val charsPerLine = (widthPx / averageCharWidth).toInt().coerceAtLeast(8)
        return text.lineSequence().sumOf { rawLine ->
            val length = rawLine.length.coerceAtLeast(1)
            ceil(length / charsPerLine.toFloat()).toInt().coerceAtLeast(1)
        }.coerceAtLeast(1)
    }

    private fun estimateWidthNorm(
        text: String,
        fontSizePx: Float,
        pageWidthPx: Float
    ): Float {
        val longestLine = text.lineSequence().maxOfOrNull { it.length } ?: 0
        val estimatedTextWidth = (longestLine.coerceAtLeast(12) * fontSizePx * 0.55f) + 18f
        return (estimatedTextWidth / pageWidthPx).coerceAtLeast(0.28f)
    }

    internal fun displayFontSizeToPageRelative(fontSize: Float): Float {
        return (fontSize / AndroidTextBoxFontReferencePx)
            .coerceIn(MinPageRelativeFontSize, MaxPageRelativeFontSize)
    }

    internal fun pageRelativeFontSizeToDisplay(fontSize: Float): Float {
        return if (fontSize in 0f..1f) {
            (fontSize * AndroidTextBoxFontReferencePx).coerceIn(8f, 48f)
        } else {
            fontSize.coerceIn(8f, 96f)
        }
    }

    internal fun legacyFontSizeToPageRelative(fontSize: Float): Float {
        return if (fontSize in 0f..1f) {
            fontSize.coerceIn(MinPageRelativeFontSize, MaxPageRelativeFontSize)
        } else {
            displayFontSizeToPageRelative(fontSize)
        }
    }
}

fun SharedPdfTextDraft.withText(
    text: String,
    canvasSize: IntSize
): SharedPdfTextDraft {
    val normalizedText = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    if (isManuallySized) {
        return copy(text = normalizedText)
    }
    val anchor = PdfPagePoint(bounds.left, bounds.top, createdAt)
    return copy(
        text = normalizedText,
        bounds = SharedPdfTextAnnotationDefaults.boundsForPlacedText(
            anchor = anchor,
            canvasSize = canvasSize,
            text = normalizedText.ifBlank { " " },
            style = style
        )
    )
}

fun SharedPdfTextDraft.withStyle(
    style: SharedPdfTextStyleConfig,
    canvasSize: IntSize
): SharedPdfTextDraft {
    if (isManuallySized) {
        return copy(style = style)
    }
    val anchor = PdfPagePoint(bounds.left, bounds.top, createdAt)
    return copy(
        style = style,
        bounds = SharedPdfTextAnnotationDefaults.boundsForPlacedText(
            anchor = anchor,
            canvasSize = canvasSize,
            text = text.ifBlank { " " },
            style = style
        )
    )
}

fun SharedPdfTextDraft.withBounds(bounds: PdfPageBounds): SharedPdfTextDraft {
    return copy(bounds = bounds.coercedToPage(), isManuallySized = true)
}

fun SharedPdfTextDraft.toAnnotation(): SharedPdfAnnotation {
    val cleanText = SharedPdfTextAnnotationDefaults.normalizeTextDraft(text)
    return SharedPdfAnnotation(
        id = id,
        pageIndex = pageIndex,
        kind = PdfAnnotationKind.TEXT,
        tool = PdfInkTool.TEXT,
        bounds = bounds,
        text = cleanText,
        colorArgb = style.colorArgb,
        backgroundArgb = style.backgroundColorArgb,
        strokeWidth = SharedPdfAnnotationDefaults.configFor(PdfInkTool.TEXT).strokeWidth,
        fontSize = style.fontSize,
        pageRelativeFontSize = style.sharedPdfTextPageRelativeFontSize(),
        isBold = style.isBold,
        isItalic = style.isItalic,
        isUnderline = style.isUnderline,
        isStrikeThrough = style.isStrikeThrough,
        fontPath = style.fontPath,
        fontName = style.fontName,
        createdAt = createdAt
    )
}

fun PdfPageBounds.resizedBy(
    handle: SharedPdfTextResizeHandle,
    deltaXPx: Float,
    deltaYPx: Float,
    canvasSize: IntSize,
    minWidthPx: Float = 50f,
    minHeightPx: Float = 50f
): PdfPageBounds {
    val pageWidthPx = canvasSize.width.coerceAtLeast(1).toFloat()
    val pageHeightPx = canvasSize.height.coerceAtLeast(1).toFloat()
    val minWidth = minWidthPx.coerceIn(1f, pageWidthPx)
    val minHeight = minHeightPx.coerceIn(1f, pageHeightPx)

    var leftPx = left * pageWidthPx
    var topPx = top * pageHeightPx
    var rightPx = right * pageWidthPx
    var bottomPx = bottom * pageHeightPx

    when (handle) {
        SharedPdfTextResizeHandle.TOP_LEFT -> {
            leftPx = (leftPx + deltaXPx).coerceIn(0f, (rightPx - minWidth).coerceAtLeast(0f))
            topPx = (topPx + deltaYPx).coerceIn(0f, (bottomPx - minHeight).coerceAtLeast(0f))
        }
        SharedPdfTextResizeHandle.TOP_CENTER -> {
            topPx = (topPx + deltaYPx).coerceIn(0f, (bottomPx - minHeight).coerceAtLeast(0f))
        }
        SharedPdfTextResizeHandle.TOP_RIGHT -> {
            rightPx = (rightPx + deltaXPx).coerceIn((leftPx + minWidth).coerceAtMost(pageWidthPx), pageWidthPx)
            topPx = (topPx + deltaYPx).coerceIn(0f, (bottomPx - minHeight).coerceAtLeast(0f))
        }
        SharedPdfTextResizeHandle.RIGHT_CENTER -> {
            rightPx = (rightPx + deltaXPx).coerceIn((leftPx + minWidth).coerceAtMost(pageWidthPx), pageWidthPx)
        }
        SharedPdfTextResizeHandle.BOTTOM_RIGHT -> {
            rightPx = (rightPx + deltaXPx).coerceIn((leftPx + minWidth).coerceAtMost(pageWidthPx), pageWidthPx)
            bottomPx = (bottomPx + deltaYPx).coerceIn((topPx + minHeight).coerceAtMost(pageHeightPx), pageHeightPx)
        }
        SharedPdfTextResizeHandle.BOTTOM_CENTER -> {
            bottomPx = (bottomPx + deltaYPx).coerceIn((topPx + minHeight).coerceAtMost(pageHeightPx), pageHeightPx)
        }
        SharedPdfTextResizeHandle.BOTTOM_LEFT -> {
            leftPx = (leftPx + deltaXPx).coerceIn(0f, (rightPx - minWidth).coerceAtLeast(0f))
            bottomPx = (bottomPx + deltaYPx).coerceIn((topPx + minHeight).coerceAtMost(pageHeightPx), pageHeightPx)
        }
        SharedPdfTextResizeHandle.LEFT_CENTER -> {
            leftPx = (leftPx + deltaXPx).coerceIn(0f, (rightPx - minWidth).coerceAtLeast(0f))
        }
    }

    return PdfPageBounds(
        left = leftPx / pageWidthPx,
        top = topPx / pageHeightPx,
        right = rightPx / pageWidthPx,
        bottom = bottomPx / pageHeightPx
    ).coercedToPage()
}

fun PdfPageBounds.movedBy(
    deltaXPx: Float,
    deltaYPx: Float,
    canvasSize: IntSize
): PdfPageBounds {
    val pageWidthPx = canvasSize.width.coerceAtLeast(1).toFloat()
    val pageHeightPx = canvasSize.height.coerceAtLeast(1).toFloat()
    val widthPx = ((right - left) * pageWidthPx).coerceIn(1f, pageWidthPx)
    val heightPx = ((bottom - top) * pageHeightPx).coerceIn(1f, pageHeightPx)
    val nextLeftPx = ((left * pageWidthPx) + deltaXPx).coerceIn(0f, (pageWidthPx - widthPx).coerceAtLeast(0f))
    val nextTopPx = ((top * pageHeightPx) + deltaYPx).coerceIn(0f, (pageHeightPx - heightPx).coerceAtLeast(0f))
    return PdfPageBounds(
        left = nextLeftPx / pageWidthPx,
        top = nextTopPx / pageHeightPx,
        right = (nextLeftPx + widthPx) / pageWidthPx,
        bottom = (nextTopPx + heightPx) / pageHeightPx
    ).coercedToPage()
}

fun SharedPdfAnnotation.sharedPdfTextStyle(): SharedPdfTextStyleConfig {
    return SharedPdfTextStyleConfig(
        colorArgb = colorArgb,
        backgroundColorArgb = backgroundArgb,
        fontSize = fontSize,
        pageRelativeFontSize = pageRelativeFontSize,
        isBold = isBold,
        isItalic = isItalic,
        isUnderline = isUnderline,
        isStrikeThrough = isStrikeThrough,
        fontPath = fontPath,
        fontName = fontName
    )
}

fun SharedPdfAnnotation.withSharedPdfTextStyle(style: SharedPdfTextStyleConfig): SharedPdfAnnotation {
    return copy(
        colorArgb = style.colorArgb,
        backgroundArgb = style.backgroundColorArgb,
        fontSize = style.fontSize,
        pageRelativeFontSize = style.sharedPdfTextPageRelativeFontSize(),
        isBold = style.isBold,
        isItalic = style.isItalic,
        isUnderline = style.isUnderline,
        isStrikeThrough = style.isStrikeThrough,
        fontPath = style.fontPath,
        fontName = style.fontName
    )
}

fun SharedPdfTextStyleConfig.withSharedPdfTextFontSize(fontSize: Float): SharedPdfTextStyleConfig {
    return copy(
        fontSize = fontSize,
        pageRelativeFontSize = SharedPdfTextAnnotationDefaults.displayFontSizeToPageRelative(fontSize)
    )
}

fun SharedPdfTextStyleConfig.sharedPdfTextPageRelativeFontSize(): Float {
    return pageRelativeFontSize
        ?.let { SharedPdfTextAnnotationDefaults.legacyFontSizeToPageRelative(it) }
        ?: SharedPdfTextAnnotationDefaults.displayFontSizeToPageRelative(fontSize)
}

fun SharedPdfTextStyleConfig.sharedPdfTextFontSizePx(canvasSize: IntSize): Float {
    val pageHeightPx = canvasSize.height.coerceAtLeast(1).toFloat()
    return (sharedPdfTextPageRelativeFontSize() * pageHeightPx).coerceAtLeast(1f)
}

fun SharedPdfAnnotation.sharedPdfTextPageRelativeFontSize(): Float {
    return pageRelativeFontSize
        ?.let { SharedPdfTextAnnotationDefaults.legacyFontSizeToPageRelative(it) }
        ?: SharedPdfTextAnnotationDefaults.displayFontSizeToPageRelative(fontSize)
}

fun SharedPdfAnnotation.sharedPdfTextFontSizePx(canvasSize: IntSize): Float {
    val pageHeightPx = canvasSize.height.coerceAtLeast(1).toFloat()
    return (sharedPdfTextPageRelativeFontSize() * pageHeightPx).coerceAtLeast(1f)
}

private fun PdfPageBounds.coercedToPage(): PdfPageBounds {
    val coercedLeft = left.coerceIn(0f, 1f)
    val coercedTop = top.coerceIn(0f, 1f)
    val coercedRight = right.coerceIn(coercedLeft, 1f)
    val coercedBottom = bottom.coerceIn(coercedTop, 1f)
    return PdfPageBounds(
        left = coercedLeft,
        top = coercedTop,
        right = coercedRight,
        bottom = coercedBottom
    )
}
