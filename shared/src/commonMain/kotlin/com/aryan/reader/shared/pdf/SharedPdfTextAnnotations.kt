package com.aryan.reader.shared.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    const val MinPageRelativeFontSize = 0.012f
    const val MaxPageRelativeFontSize = 0.12f
    private const val DefaultDisplayFontSize = 16f

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
        val safeDisplayFontSize = fontSize.takeIf { it.isFinite() } ?: DefaultDisplayFontSize
        return sanitizePageRelativeFontSize(safeDisplayFontSize / AndroidTextBoxFontReferencePx)
    }

    internal fun pageRelativeFontSizeToDisplay(fontSize: Float): Float {
        val safeFontSize = fontSize.takeIf { it.isFinite() } ?: displayFontSizeToPageRelative(DefaultDisplayFontSize)
        return if (safeFontSize in 0f..1f) {
            (sanitizePageRelativeFontSize(safeFontSize) * AndroidTextBoxFontReferencePx).coerceIn(8f, 48f)
        } else {
            safeFontSize.coerceIn(8f, 96f)
        }
    }

    internal fun legacyFontSizeToPageRelative(fontSize: Float): Float {
        val safeFontSize = fontSize.takeIf { it.isFinite() } ?: DefaultDisplayFontSize
        return if (safeFontSize in 0f..1f) {
            sanitizePageRelativeFontSize(safeFontSize)
        } else {
            displayFontSizeToPageRelative(safeFontSize)
        }
    }

    /**
     * Text sizes are stored as a fraction of the PDF page height. Keep that
     * invariant at every persistence/render boundary so corrupt or legacy
     * absolute-pixel values cannot become full-page text overlays.
     */
    fun sanitizePageRelativeFontSize(fontSize: Float): Float {
        val safeFontSize = fontSize.takeIf { it.isFinite() }
            ?: displayFontSizeToPageRelative(DefaultDisplayFontSize)
        return safeFontSize.coerceIn(MinPageRelativeFontSize, MaxPageRelativeFontSize)
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

/** True when the normalized point (0..1 page coordinates) falls inside this bounds rect. */
fun PdfPageBounds.containsNormalizedPoint(x: Float, y: Float): Boolean {
    return x in left..right && y in top..bottom
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

/** Canonicalizes text annotation geometry and font units before state/render use. */
fun SharedPdfAnnotation.sanitizedSharedPdfTextAnnotation(): SharedPdfAnnotation {
    if (kind != PdfAnnotationKind.TEXT) return this
    val safePageRelativeFontSize = sharedPdfTextPageRelativeFontSize()
    return copy(
        bounds = bounds?.sanitizedForSharedPdf(allowEmpty = true),
        boundsList = boundsList.mapNotNull { it.sanitizedForSharedPdf(allowEmpty = true) },
        fontSize = SharedPdfTextAnnotationDefaults.pageRelativeFontSizeToDisplay(safePageRelativeFontSize),
        pageRelativeFontSize = safePageRelativeFontSize
    )
}

fun SharedPdfAnnotation.sharedPdfTextFontSizePx(canvasSize: IntSize): Float {
    val pageHeightPx = canvasSize.height.coerceAtLeast(1).toFloat()
    return (sharedPdfTextPageRelativeFontSize() * pageHeightPx).coerceAtLeast(1f)
}

internal fun PdfPageBounds.sanitizedForSharedPdf(allowEmpty: Boolean = false): PdfPageBounds? {
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
    val normalizedLeft = minOf(left, right).coerceIn(0f, 1f)
    val normalizedTop = minOf(top, bottom).coerceIn(0f, 1f)
    val normalizedRight = maxOf(left, right).coerceIn(normalizedLeft, 1f)
    val normalizedBottom = maxOf(top, bottom).coerceIn(normalizedTop, 1f)
    val hasArea = if (allowEmpty) {
        normalizedRight >= normalizedLeft && normalizedBottom >= normalizedTop
    } else {
        normalizedRight > normalizedLeft && normalizedBottom > normalizedTop
    }
    if (!hasArea) return null
    return PdfPageBounds(
        left = normalizedLeft,
        top = normalizedTop,
        right = normalizedRight,
        bottom = normalizedBottom
    )
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

/**
 * Mirrors Android's pagination text-box drag (PdfViewerScreen.onTextBoxDragEnd): while dragging
 * a text box across pages in pagination mode, the box's top-left follows the finger in container
 * (screen) coordinates, and on release its position is re-expressed relative to the target page
 * surface, clamped to a fixed padding inset.
 */
data class SharedPdfTextDragState(
    val draftId: String,
    val originDisplayPage: Int,
    val originPdfPage: Int,
    val relWidth: Float,
    val relHeight: Float,
    val dragOffset: Offset,
    val originCanvasSize: IntSize,
    val dragWidthPx: Float,
    val dragHeightPx: Float,
    val dragCameraScale: Float
)

/**
 * Converts a drop position in container coordinates into page-relative bounds on the target
 * page surface, clamped with [paddingPx] insets (Android's 14dp box padding).
 */
fun sharedPdfTextDropBounds(
    dropTopLeft: Offset,
    targetRect: Rect,
    relWidth: Float,
    relHeight: Float,
    paddingPx: Float
): PdfPageBounds {
    if (targetRect.width <= 0f || targetRect.height <= 0f) {
        return PdfPageBounds(0f, 0f, relWidth.coerceIn(0f, 1f), relHeight.coerceIn(0f, 1f))
    }
    val padRelX = paddingPx / targetRect.width
    val padRelY = paddingPx / targetRect.height
    val rawRelX = (dropTopLeft.x - targetRect.left) / targetRect.width
    val rawRelY = (dropTopLeft.y - targetRect.top) / targetRect.height
    val maxRelX = (1f - relWidth - padRelX).coerceAtLeast(padRelX)
    val maxRelY = (1f - relHeight - padRelY).coerceAtLeast(padRelY)
    val finalRelX = rawRelX.coerceIn(padRelX, maxRelX)
    val finalRelY = rawRelY.coerceIn(padRelY, maxRelY)
    return PdfPageBounds(
        left = finalRelX,
        top = finalRelY,
        right = finalRelX + relWidth,
        bottom = finalRelY + relHeight
    )
}
