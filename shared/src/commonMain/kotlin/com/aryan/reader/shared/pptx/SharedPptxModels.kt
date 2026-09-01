package com.aryan.reader.shared.pptx

import kotlin.math.max
import kotlin.math.min

private const val DEFAULT_TEXT_MARGIN_PT = 91_440f / 12_700f
private const val DEFAULT_LINE_SPACING_MULTIPLE = 1.0f

/** Platform-neutral PowerPoint slide model shared by Android, desktop, and iOS. */
data class SharedPptxRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    constructor(source: SharedPptxRect) : this(source.left, source.top, source.right, source.bottom)

    fun width(): Float = (right - left).coerceAtLeast(0f)
    fun height(): Float = (bottom - top).coerceAtLeast(0f)
    fun centerX(): Float = left + width() / 2f
    fun centerY(): Float = top + height() / 2f
    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
    fun expanded(dx: Float, dy: Float): SharedPptxRect = SharedPptxRect(left - dx, top - dy, right + dx, bottom + dy)
    fun union(other: SharedPptxRect): SharedPptxRect = SharedPptxRect(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )
}

data class SharedPptxPoint(val x: Float, val y: Float)

object SharedPptxColor {
    const val TRANSPARENT: Int = 0x00000000
    const val BLACK: Int = -0x1000000
    const val WHITE: Int = -0x1
    const val RED: Int = -0x10000
    const val GREEN: Int = -0xff0100
    const val BLUE: Int = -0xffff01
    const val YELLOW: Int = -0x100
    const val CYAN: Int = -0xff0001
    const val MAGENTA: Int = -0xff01
    const val GRAY: Int = -0x7f7f80
    const val DKGRAY: Int = -0xbbbbbc
    const val LTGRAY: Int = -0x333334

    fun rgb(red: Int, green: Int, blue: Int): Int = argb(255, red, green, blue)
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xff) shl 24) or ((red and 0xff) shl 16) or ((green and 0xff) shl 8) or (blue and 0xff)
    fun alpha(color: Int): Int = color ushr 24
    fun red(color: Int): Int = color shr 16 and 0xff
    fun green(color: Int): Int = color shr 8 and 0xff
    fun blue(color: Int): Int = color and 0xff
}

data class SharedPptxDeck(
    val widthPoint: Int,
    val heightPoint: Int,
    val slides: List<SharedPptxSlide>,
)

data class SharedPptxSlide(
    val widthPoint: Int,
    val heightPoint: Int,
    val backgroundColor: Int?,
    val elements: List<SharedPptxElement>,
    val text: String,
    val charBoxes: List<SharedPptxCharBox>,
)

data class SharedPptxCharBox(val char: Char, val bounds: SharedPptxRect)

sealed interface SharedPptxElement { val bounds: SharedPptxRect }

data class SharedPptxShapeElement(
    override val bounds: SharedPptxRect,
    val preset: String,
    val fillColor: Int?,
    val gradientFill: SharedPptxGradientFill? = null,
    val lineColor: Int?,
    val lineWidthPoint: Float,
    val paragraphs: List<SharedPptxParagraph>,
    val hyperlink: String?,
    val placeholderKey: SharedPptxPlaceholderKey?,
    val textInsets: SharedPptxTextInsets = SharedPptxTextInsets(),
    val verticalAnchor: SharedPptxVerticalAnchor = SharedPptxVerticalAnchor.TOP,
    val rotationDegrees: Float = 0f,
    val renderText: Boolean = true,
    val fontScale: Float = 1f,
    val lineSpacingReduction: Float = 0f,
    val autoFitMode: SharedPptxAutoFitMode = SharedPptxAutoFitMode.NONE,
    val customGeometry: SharedPptxCustomGeometry? = null,
) : SharedPptxElement

data class SharedPptxImageElement(
    override val bounds: SharedPptxRect,
    val bytes: ByteArray,
    val contentType: String?,
    val crop: SharedPptxImageCrop = SharedPptxImageCrop(),
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
) : SharedPptxElement

data class SharedPptxImageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

data class SharedPptxTableElement(
    override val bounds: SharedPptxRect,
    val rows: List<SharedPptxTableRow>,
    val rotationDegrees: Float = 0f,
) : SharedPptxElement

data class SharedPptxTableRow(val heightPoint: Float?, val cells: List<SharedPptxTableCell>)

data class SharedPptxTableCell(
    val widthPoint: Float?,
    val fillColor: Int?,
    val lineColor: Int?,
    val paragraphs: List<SharedPptxParagraph>,
    val textInsets: SharedPptxTextInsets = SharedPptxTextInsets(left = 3.6f, top = 3.6f, right = 3.6f, bottom = 3.6f),
    val verticalAnchor: SharedPptxVerticalAnchor = SharedPptxVerticalAnchor.TOP,
)

data class SharedPptxParagraph(
    val runs: List<SharedPptxTextRun>,
    val alignment: SharedPptxTextAlign = SharedPptxTextAlign.START,
    val bullet: String? = null,
    val level: Int = 0,
    val marginLeftPt: Float? = null,
    val indentPt: Float? = null,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 0f,
    val lineSpacingMultiple: Float = DEFAULT_LINE_SPACING_MULTIPLE,
    val alignmentExplicit: Boolean = false,
    val bulletExplicit: Boolean = false,
    val spaceBeforeExplicit: Boolean = false,
    val spaceAfterExplicit: Boolean = false,
    val lineSpacingExplicit: Boolean = false,
)

data class SharedPptxTextRun(
    val text: String,
    val sizePt: Float? = null,
    val color: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val typeface: String? = null,
    val baseline: Float = 0f,
    val sizeExplicit: Boolean = false,
    val colorExplicit: Boolean = false,
    val boldExplicit: Boolean = false,
    val italicExplicit: Boolean = false,
    val typefaceExplicit: Boolean = false,
    val baselineExplicit: Boolean = false,
)

enum class SharedPptxTextAlign { START, CENTER, END }
enum class SharedPptxVerticalAnchor { TOP, MIDDLE, BOTTOM }
enum class SharedPptxAutoFitMode { NONE, NORMAL, SHAPE }

data class SharedPptxTextInsets(
    val left: Float = DEFAULT_TEXT_MARGIN_PT,
    val top: Float = DEFAULT_TEXT_MARGIN_PT,
    val right: Float = DEFAULT_TEXT_MARGIN_PT,
    val bottom: Float = DEFAULT_TEXT_MARGIN_PT,
)

data class SharedPptxGradientFill(
    val startColor: Int,
    val endColor: Int,
    val angleDegrees: Float = 0f,
)

data class SharedPptxCustomGeometry(val width: Float, val height: Float, val commands: List<SharedPptxPathCommand>)

sealed interface SharedPptxPathCommand {
    data class MoveTo(val x: Float, val y: Float) : SharedPptxPathCommand
    data class LineTo(val x: Float, val y: Float) : SharedPptxPathCommand
    data class QuadTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : SharedPptxPathCommand
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x3: Float,
        val y3: Float,
    ) : SharedPptxPathCommand
    data object Close : SharedPptxPathCommand
}

data class SharedPptxPlaceholderKey(val type: String?, val index: String?)
