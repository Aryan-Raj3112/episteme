package com.aryan.reader.shared.pdf

import kotlin.random.Random

private val exportFilenameUnsafeChars = Regex("[^a-zA-Z0-9._-]")
private val shareArtifactUnsafeChars = Regex("[^A-Za-z0-9._-]+")

/**
 * Android benchmark (PdfDocumentUtils.getSuggestedFilename): shared/exported PDF copies use a
 * sanitized base (50 chars) plus an annotated marker and a short random id, so share sheets and
 * save dialogs never expose internal library paths.
 */
fun suggestSharedPdfExportFilename(
    originalName: String?,
    isAnnotated: Boolean,
    randomSuffix: Int = Random.nextInt(1000, 9999),
): String {
    val base = originalName?.substringBeforeLast('.') ?: "Document"
    val safeBase = base.replace(exportFilenameUnsafeChars, "_").take(50)
    val suffix = if (isAnnotated) "_annotated" else ""
    return "${safeBase}${suffix}_${randomSuffix}.pdf"
}

/**
 * Android benchmark (AndroidShareArtifactManager.sanitizeFileName): per-request share files are
 * isolated to sanitized names so a second chooser cannot escape its directory.
 */
fun sanitizeSharedPdfExportFilename(requestedFileName: String): String {
    val normalized = requestedFileName
        .trim()
        .replace(shareArtifactUnsafeChars, "_")
        .trim('_')
        .take(120)
    return normalized.takeIf { it.isNotBlank() && it != "." && it != ".." }
        ?: "shared-file"
}

/**
 * Android benchmark (PdfiumAnnotationExporter.toPdfDateString): PDF CreationDate/M dates are UTC
 * `D:yyyyMMddHHmmssZ`. Pure Kotlin so Android, iOS and desktop embed identical dates.
 */
fun sharedPdfDateString(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val totalSeconds = epochMillis / 1000L
    val daysSinceEpoch = totalSeconds / 86400L
    val secondOfDay = (totalSeconds % 86400L).toInt()
    val hour = secondOfDay / 3600
    val minute = (secondOfDay % 3600) / 60
    val second = secondOfDay % 60

    // Howard Hinnant's civil_from_days, days since 1970-01-01 -> Gregorian date.
    val z = daysSinceEpoch + 719468L
    val era = if (z >= 0) z / 146097L else (z - 146096L) / 146097L
    val dayOfEra = z - era * 146097L
    val yearOfEra = (dayOfEra - dayOfEra / 1460L + dayOfEra / 36524L - dayOfEra / 146096L) / 365L
    var year = yearOfEra + era * 400L
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
    val monthPart = (5L * dayOfYear + 2L) / 153L
    val day = (dayOfYear - (153L * monthPart + 2L) / 5L + 1L).toInt()
    var month = (monthPart + if (monthPart < 10) 3 else -9).toInt()
    if (month <= 2) year += 1

    fun two(value: Int): String = if (value < 10) "0$value" else value.toString()
    val yearText = year.toString().padStart(4, '0')
    return "D:${yearText}${two(month)}${two(day)}${two(hour)}${two(minute)}${two(second)}Z"
}
