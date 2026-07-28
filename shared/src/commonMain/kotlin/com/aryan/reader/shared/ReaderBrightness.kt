package com.aryan.reader.shared

import kotlin.math.roundToInt

const val DefaultReaderCustomBrightness = 0.75f
const val MinimumReaderCustomBrightness = 0.01f

fun normalizeReaderBrightness(brightness: Float): Float {
    return (brightness * 100f).roundToInt().coerceIn(1, 100) / 100f
}

fun stepReaderBrightness(brightness: Float, percentDelta: Int): Float {
    val currentPercent = (normalizeReaderBrightness(brightness) * 100f).roundToInt()
    return (currentPercent + percentDelta).coerceIn(1, 100) / 100f
}
