package com.aryan.reader.shared

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(units.indices)
    return formatDecimal(bytes / 1024.0.pow(digitGroups.toDouble()), 2) + " " + units[digitGroups]
}

fun progressPercentValue(progressPercentage: Float?): Int {
    return (progressPercentage ?: 0f).coerceIn(0f, 100f).roundToInt()
}

fun progressFraction(progressPercentage: Float?): Float {
    return progressPercentValue(progressPercentage) / 100f
}

fun BookItem.cardTitle(): String {
    return title?.takeIf { it.isNotBlank() } ?: displayName
}

fun BookItem.cardAuthor(fallback: String = "No author listed"): String {
    return author
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        ?: fallback
}

fun BookItem.isOpdsStream(): Boolean {
    return path?.startsWith("opds-pse://") == true
}

private fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    val text = rounded.toString()
    val dotIndex = text.indexOf('.')
    if (dotIndex < 0) return text + "." + "0".repeat(decimals)
    val currentDecimals = text.length - dotIndex - 1
    return if (currentDecimals >= decimals) {
        text.take(dotIndex + 1 + decimals)
    } else {
        text + "0".repeat(decimals - currentDecimals)
    }
}
