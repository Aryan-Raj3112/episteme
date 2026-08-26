package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderSettings
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
 */
object AndroidEpubFormatBenchmark {
    const val baseFontSizeSp: Float = 18f
    const val baseLineSpacing: Float = 1.45f
    const val baseMarginPx: Float = 48f
}

enum class AndroidEpubFormatSlider {
    FONT_SIZE,
    LINE_HEIGHT,
    PARAGRAPH_GAP,
    IMAGE_SIZE,
    HORIZONTAL_MARGIN,
    VERTICAL_MARGIN,
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

    fun forSlider(slider: AndroidEpubFormatSlider): AndroidEpubFormatSliderSpec = when (slider) {
        AndroidEpubFormatSlider.FONT_SIZE -> fontSize
        AndroidEpubFormatSlider.LINE_HEIGHT -> lineHeight
        AndroidEpubFormatSlider.PARAGRAPH_GAP -> paragraphGap
        AndroidEpubFormatSlider.IMAGE_SIZE -> imageSize
        AndroidEpubFormatSlider.HORIZONTAL_MARGIN -> horizontalMargin
        AndroidEpubFormatSlider.VERTICAL_MARGIN -> verticalMargin
    }
}

/** The six Android EPUB format positions in canonical multiplier space. */
data class AndroidEpubFormatSliderValues(
    val fontSize: Float = 1f,
    val lineHeight: Float = 1f,
    val paragraphGap: Float = 1f,
    val imageSize: Float = 1f,
    val horizontalMargin: Float = 1f,
    val verticalMargin: Float = 1f,
) {
    /** Sanitizes persisted values while preserving fractional values between steps. */
    fun clamped(): AndroidEpubFormatSliderValues = copy(
        fontSize = AndroidEpubFormatSliders.fontSize.clamp(fontSize),
        lineHeight = AndroidEpubFormatSliders.lineHeight.clamp(lineHeight),
        paragraphGap = AndroidEpubFormatSliders.paragraphGap.clamp(paragraphGap),
        imageSize = AndroidEpubFormatSliders.imageSize.clamp(imageSize),
        horizontalMargin = AndroidEpubFormatSliders.horizontalMargin.clamp(horizontalMargin),
        verticalMargin = AndroidEpubFormatSliders.verticalMargin.clamp(verticalMargin),
    )

    /** Values produced by the Android/shared slider controls. */
    fun snapped(): AndroidEpubFormatSliderValues = copy(
        fontSize = AndroidEpubFormatSliders.fontSize.snap(fontSize),
        lineHeight = AndroidEpubFormatSliders.lineHeight.snap(lineHeight),
        paragraphGap = AndroidEpubFormatSliders.paragraphGap.snap(paragraphGap),
        imageSize = AndroidEpubFormatSliders.imageSize.snap(imageSize),
        horizontalMargin = AndroidEpubFormatSliders.horizontalMargin.snap(horizontalMargin),
        verticalMargin = AndroidEpubFormatSliders.verticalMargin.snap(verticalMargin),
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
