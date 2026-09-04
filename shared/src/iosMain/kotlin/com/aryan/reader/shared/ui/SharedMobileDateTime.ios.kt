package com.aryan.reader.shared.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle

internal actual fun formatSharedMobileDateTime(epochMillis: Long): String {
    // Android benchmark: DateFormat.getDateTimeInstance(MEDIUM, SHORT) — locale-aware.
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.stringFromDate(epochMillis.toNSDate())
}

internal actual fun formatSharedMobileClockTime(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateStyle = platform.Foundation.NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.stringFromDate(epochMillis.toNSDate())
}

internal actual fun formatSharedMobileBookInfoDateTime(epochMillis: Long): String {
    // Android benchmark: DateFormat.getDateTimeInstance(LONG, SHORT) — locale-aware.
    val formatter = NSDateFormatter().apply {
        dateStyle = platform.Foundation.NSDateFormatterLongStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.stringFromDate(epochMillis.toNSDate())
}

private fun Long.toNSDate(): NSDate {
    val unixSecondsAtAppleReferenceDate = 978_307_200.0
    return NSDate(
        timeIntervalSinceReferenceDate =
            coerceAtLeast(0L) / 1_000.0 - unixSecondsAtAppleReferenceDate
    )
}
