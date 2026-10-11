package com.aryan.reader.shared

import com.aryan.reader.shared.reader.DefaultReaderPageSpreadGutterDp
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The format controls are persisted as multipliers by the Android EPUB reader.
 * Keep these values in one portable model so the shared mobile reader does not
 * slowly grow a second set of ranges or step sizes.
 *
 * The Android reader still owns its native/legacy rendering and may have a
 * dynamic Compose typography base at runtime.  The absolute bases below are
 * the stable portability benchmark used by Android's settings bridge and by
 * the shared renderer; they describe the value represented by a 1.0x slider
 * position, not a replacement for Android's runtime typography.
 *
 * Margins use 16px because both Android surfaces render a 1.0x margin as 16
 * (native 16.dp in PaginatedReader, 16px in epub_reader.js), so a 1.0x slider
 * looks the same on Android and on shared/iOS readers.
 */
object AndroidEpubFormatBenchmark {
    const val baseFontSizeSp: Float = 18f
    const val baseLineSpacing: Float = 1.45f
    const val baseMarginPx: Float = 16f
}

/**
 * The multiplier a native (Compose) pagination surface applies to the font size when the reader's
 * line-height setting sits at its 1.0x position.
 *
 * Mirrors `WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER` in Android's `PaginatedReaderSelection`.
 */
const val SharedNativePaginationNormalLineHeightMultiplier: Float = 1.2f

/**
 * Base font size in sp for native (Compose) pagination surfaces.
 *
 * Android benchmark: `PaginatedReader.layoutTextStyle` and
 * `NativeVerticalReaderScreen.layoutTextStyle` both start from
 * `MaterialTheme.typography.bodyLarge` (16sp) times the font-size slider, while the
 * stored [ReaderSettings.fontSize] (and [AndroidEpubFormatBenchmark.baseFontSizeSp] = 18)
 * describes the WebView surface (18px at 1.0x). Native surfaces must therefore render
 * 16sp at the default setting, not 18sp, or every line, heading and paragraph gap
 * measures ~12.5% larger than Android and the same book paginates differently.
 *
 * HTML/WebView surfaces keep using [AndroidEpubFormatBenchmark.baseFontSizeSp] as-is;
 * only native pagination converts via [ReaderSettings.sharedNativeFontSizeSp].
 */
const val SharedNativePaginationBaseFontSizeSp: Float = 16f

/**
 * The line-height multiplier a native (Compose) pagination surface must use for a stored line
 * spacing, in shared settings units.
 *
 * [ReaderSettings.lineSpacing] is a CSS `line-height`: a 1.0x slider position is
 * [AndroidEpubFormatBenchmark.baseLineSpacing] (1.45), the value Android renders directly in its
 * WebView surfaces. Compose adds no browser leading, so Android's native pagination renders that
 * same setting at [SharedNativePaginationNormalLineHeightMultiplier] (1.2) — see
 * `paginationLineHeightMultiplierForWebViewSetting` — and every other slider position as the slider
 * value itself. Shared pagination surfaces are Compose too, so they have to apply the same rule:
 * using the CSS value directly draws 1.45x lines where Android draws 1.2x, which spends a fifth of
 * every page on leading and paginates the same book into a different number of pages.
 *
 * HTML/WebView surfaces keep using the CSS value as-is; only native pagination converts.
 */
fun sharedPaginationLineHeightMultiplier(cssLineSpacing: Float): Float {
    val webViewMultiplier = cssLineSpacing / AndroidEpubFormatBenchmark.baseLineSpacing
    return if (abs(webViewMultiplier - 1f) < 0.001f) {
        SharedNativePaginationNormalLineHeightMultiplier
    } else {
        webViewMultiplier
    }
}

/**
 * The reader's base font size in sp for native (Compose) pagination: the size a block
 * measures and draws at when the book's CSS sets no font size of its own.
 *
 * Converts the stored WebView-based [ReaderSettings.fontSize] (18 at 1.0x) to the native
 * base ([SharedNativePaginationBaseFontSizeSp] = 16 at 1.0x), matching Android's
 * `bodyLarge.fontSize * fontSizeMultiplier`. Measurement, rendering, CSS em resolution,
 * paragraph gaps and image fallbacks must all read this rather than `fontSize` directly,
 * or native pages measure larger than Android's.
 */
fun ReaderSettings.sharedNativeFontSizeSp(): Float =
    SharedNativePaginationBaseFontSizeSp * (fontSize / AndroidEpubFormatBenchmark.baseFontSizeSp)

/**
 * The reader's base leading in sp for native (Compose) pagination: the size a block measures and
 * draws at when the book's CSS sets no line height of its own.
 *
 * Measurement and rendering must both read this rather than
 * `fontSize * lineSpacing` directly, or a page measured with one leading draws with another.
 * The base is the native font size ([ReaderSettings.sharedNativeFontSizeSp]), not the stored
 * WebView size, matching Android's `adjustedFontSize * paginationLineHeightMultiplier`.
 */
fun ReaderSettings.sharedPaginationBaseLineHeightSp(): Float =
    sharedNativeFontSizeSp() * sharedPaginationLineHeightMultiplier(lineSpacing)

enum class AndroidEpubFormatSlider {
    FONT_SIZE,
    LINE_HEIGHT,
    PARAGRAPH_GAP,
    IMAGE_SIZE,
    HORIZONTAL_MARGIN,
    VERTICAL_MARGIN,
    /**
     * Gap between the two pages of a two-page spread, in dp (absolute, not a
     * multiplier — there is no typographic base for a gutter). Only applies
     * when the spread mode is two-page.
     */
    SPREAD_GAP,
}

data class AndroidEpubFormatSliderSpec(
    val minimum: Float,
    val maximum: Float,
    val step: Float = 0.1f,
    val default: Float = 1f,
) {
    init {
        require(minimum <= maximum) { "minimum must not exceed maximum" }
        require(step > 0f) { "step must be positive" }
    }

    /** Clamps a persisted value without changing an existing fractional position. */
    fun clamp(value: Float): Float {
        if (!value.isFinite()) return default.coerceIn(minimum, maximum)
        return value.coerceIn(minimum, maximum)
    }

    /** Snaps a user interaction to the same one-decimal step used by Android. */
    fun snap(value: Float): Float {
        val clamped = clamp(value)
        val stepCount = ((clamped - minimum) / step).roundToInt()
        return (minimum + stepCount * step).coerceIn(minimum, maximum).rounded(1)
    }

    fun step(value: Float, deltaSteps: Int): Float = snap(clamp(value) + (deltaSteps * step))
}

object AndroidEpubFormatSliders {
    val fontSize = AndroidEpubFormatSliderSpec(minimum = 0.5f, maximum = 3f)
    val lineHeight = AndroidEpubFormatSliderSpec(minimum = 1f, maximum = 3f)
    val paragraphGap = AndroidEpubFormatSliderSpec(minimum = 0f, maximum = 3f)
    val imageSize = AndroidEpubFormatSliderSpec(minimum = 0.5f, maximum = 2f)
    val horizontalMargin = AndroidEpubFormatSliderSpec(minimum = 0f, maximum = 3f)
    val verticalMargin = AndroidEpubFormatSliderSpec(minimum = 0f, maximum = 3f)
    val spreadGap = AndroidEpubFormatSliderSpec(
        minimum = 0f,
        maximum = 48f,
        step = 2f,
        default = DefaultReaderPageSpreadGutterDp
    )

    fun forSlider(slider: AndroidEpubFormatSlider): AndroidEpubFormatSliderSpec = when (slider) {
        AndroidEpubFormatSlider.FONT_SIZE -> fontSize
        AndroidEpubFormatSlider.LINE_HEIGHT -> lineHeight
        AndroidEpubFormatSlider.PARAGRAPH_GAP -> paragraphGap
        AndroidEpubFormatSlider.IMAGE_SIZE -> imageSize
        AndroidEpubFormatSlider.HORIZONTAL_MARGIN -> horizontalMargin
        AndroidEpubFormatSlider.VERTICAL_MARGIN -> verticalMargin
        AndroidEpubFormatSlider.SPREAD_GAP -> spreadGap
    }
}

/** The six Android EPUB format positions in canonical multiplier space, plus the absolute spread gap. */
data class AndroidEpubFormatSliderValues(
    val fontSize: Float = 1f,
    val lineHeight: Float = 1f,
    val paragraphGap: Float = 1f,
    val imageSize: Float = 1f,
    val horizontalMargin: Float = 1f,
    val verticalMargin: Float = 1f,
    val spreadGapDp: Float = DefaultReaderPageSpreadGutterDp,
) {
    /** Sanitizes persisted values while preserving fractional values between steps. */
    fun clamped(): AndroidEpubFormatSliderValues = copy(
        fontSize = AndroidEpubFormatSliders.fontSize.clamp(fontSize),
        lineHeight = AndroidEpubFormatSliders.lineHeight.clamp(lineHeight),
        paragraphGap = AndroidEpubFormatSliders.paragraphGap.clamp(paragraphGap),
        imageSize = AndroidEpubFormatSliders.imageSize.clamp(imageSize),
        horizontalMargin = AndroidEpubFormatSliders.horizontalMargin.clamp(horizontalMargin),
        verticalMargin = AndroidEpubFormatSliders.verticalMargin.clamp(verticalMargin),
        spreadGapDp = AndroidEpubFormatSliders.spreadGap.clamp(spreadGapDp),
    )

    /** Values produced by the Android/shared slider controls. */
    fun snapped(): AndroidEpubFormatSliderValues = copy(
        fontSize = AndroidEpubFormatSliders.fontSize.snap(fontSize),
        lineHeight = AndroidEpubFormatSliders.lineHeight.snap(lineHeight),
        paragraphGap = AndroidEpubFormatSliders.paragraphGap.snap(paragraphGap),
        imageSize = AndroidEpubFormatSliders.imageSize.snap(imageSize),
        horizontalMargin = AndroidEpubFormatSliders.horizontalMargin.snap(horizontalMargin),
        verticalMargin = AndroidEpubFormatSliders.verticalMargin.snap(verticalMargin),
        spreadGapDp = AndroidEpubFormatSliders.spreadGap.snap(spreadGapDp),
    )
}

/**
 * Converts shared absolute settings into Android's canonical multiplier model.
 * This intentionally clamps but does not snap, so importing an older persisted
 * value does not unexpectedly move it by a whole slider step.  New slider
 * gestures use [AndroidEpubFormatSliderSpec.snap].
 */
fun ReaderSettings.toAndroidEpubFormatSliderValues(): AndroidEpubFormatSliderValues {
    val horizontalMargin = resolvedHorizontalMargin / AndroidEpubFormatBenchmark.baseMarginPx
    val verticalMargin = resolvedVerticalMargin / AndroidEpubFormatBenchmark.baseMarginPx
    return AndroidEpubFormatSliderValues(
        fontSize = fontSize / AndroidEpubFormatBenchmark.baseFontSizeSp,
        lineHeight = lineSpacing / AndroidEpubFormatBenchmark.baseLineSpacing,
        paragraphGap = paragraphSpacing,
        imageSize = imageScale,
        horizontalMargin = horizontalMargin,
        verticalMargin = verticalMargin,
        spreadGapDp = pageSpreadGutterDp,
    ).clamped()
}

/**
 * Converts canonical multipliers into the absolute [ReaderSettings] model
 * consumed by the shared HTML/native readers.
 */
fun AndroidEpubFormatSliderValues.toReaderSettings(base: ReaderSettings = ReaderSettings()): ReaderSettings {
    val values = clamped()
    val horizontalMargin =
        (AndroidEpubFormatBenchmark.baseMarginPx * values.horizontalMargin).roundToInt().coerceAtLeast(0)
    val verticalMargin =
        (AndroidEpubFormatBenchmark.baseMarginPx * values.verticalMargin).roundToInt().coerceAtLeast(0)
    return base.copy(
        fontSize = (AndroidEpubFormatBenchmark.baseFontSizeSp * values.fontSize).roundToInt(),
        fontWeight = base.fontWeight.coerceIn(0, 1000),
        letterSpacing = base.letterSpacing.coerceIn(-0.10f, 0.50f),
        lineSpacing = AndroidEpubFormatBenchmark.baseLineSpacing * values.lineHeight,
        margin = maxOf(horizontalMargin, verticalMargin),
        horizontalMargin = horizontalMargin,
        verticalMargin = verticalMargin,
        paragraphSpacing = values.paragraphGap,
        imageScale = values.imageSize,
        pageSpreadGutterDp = values.spreadGapDp,
    )
}

/** Applies one canonical slider position without disturbing unrelated settings. */
fun ReaderSettings.withAndroidEpubFormatSliderValue(
    slider: AndroidEpubFormatSlider,
    value: Float,
): ReaderSettings {
    val normalized = AndroidEpubFormatSliders.forSlider(slider).clamp(value)
    return when (slider) {
        AndroidEpubFormatSlider.FONT_SIZE -> copy(
            fontSize = (AndroidEpubFormatBenchmark.baseFontSizeSp * normalized).roundToInt(),
        )
        AndroidEpubFormatSlider.LINE_HEIGHT -> copy(
            lineSpacing = AndroidEpubFormatBenchmark.baseLineSpacing * normalized,
        )
        AndroidEpubFormatSlider.PARAGRAPH_GAP -> copy(paragraphSpacing = normalized)
        AndroidEpubFormatSlider.IMAGE_SIZE -> copy(imageScale = normalized)
        AndroidEpubFormatSlider.HORIZONTAL_MARGIN -> {
            val nextHorizontal =
                (AndroidEpubFormatBenchmark.baseMarginPx * normalized).roundToInt().coerceAtLeast(0)
            val currentVertical = resolvedVerticalMargin.coerceAtLeast(0)
            copy(
                margin = maxOf(nextHorizontal, currentVertical),
                horizontalMargin = nextHorizontal,
                verticalMargin = verticalMargin ?: currentVertical,
            )
        }
        AndroidEpubFormatSlider.VERTICAL_MARGIN -> {
            val nextVertical =
                (AndroidEpubFormatBenchmark.baseMarginPx * normalized).roundToInt().coerceAtLeast(0)
            val currentHorizontal = resolvedHorizontalMargin.coerceAtLeast(0)
            copy(
                margin = maxOf(currentHorizontal, nextVertical),
                horizontalMargin = horizontalMargin ?: currentHorizontal,
                verticalMargin = nextVertical,
            )
        }
        AndroidEpubFormatSlider.SPREAD_GAP -> copy(pageSpreadGutterDp = normalized)
    }
}

/**
 * One-time migration/sanitization for settings persisted by the shared mobile
 * reader.  Navigation, theme, alignment, and the Android pagination justify
 * policy remain untouched; only format values represented by the sliders are
 * normalized.
 */
fun ReaderSettings.migrateAndroidEpubFormatSettings(): ReaderSettings {
    return toAndroidEpubFormatSliderValues().toReaderSettings(this)
}

/** Applies the EPUB format migration to portable iOS/mobile library state. */
fun BookItem.migrateAndroidEpubFormatSettings(): BookItem {
    if (type == FileType.PDF) return this
    return copy(
        readerSettings = readerSettings?.migrateAndroidEpubFormatSettings(),
        readerLocalFormatSettings = readerLocalFormatSettings?.migrateAndroidEpubFormatSettings(),
    )
}

fun SharedLibrarySnapshot.migrateAndroidEpubFormatSettings(): SharedLibrarySnapshot = copy(
    readerDefaultSettings = readerDefaultSettings.migrateAndroidEpubFormatSettings(),
    books = books.map(BookItem::migrateAndroidEpubFormatSettings),
)

private fun Float.rounded(decimalPlaces: Int): Float {
    val factor = when (decimalPlaces) {
        1 -> 10f
        2 -> 100f
        3 -> 1_000f
        else -> 10f.powInt(decimalPlaces)
    }
    return (this * factor).roundToInt() / factor
}

private fun Float.powInt(exponent: Int): Float {
    var result = 1f
    repeat(exponent.coerceAtLeast(0)) { result *= 10f }
    return result
}
