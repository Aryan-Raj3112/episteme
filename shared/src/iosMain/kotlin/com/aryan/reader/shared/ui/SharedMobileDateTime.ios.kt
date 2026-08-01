package com.aryan.reader.shared.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle

internal actual fun formatSharedMobileDateTime(epochMillis: Long): String {
    val unixSecondsAtAppleReferenceDate = 978_307_200.0
    val formatter = NSDateFormatter().apply {
        dateFormat = "MMM d, h:mm a"
    }
    return formatter.stringFromDate(
        NSDate(
            timeIntervalSinceReferenceDate =
                epochMillis.coerceAtLeast(0L) / 1_000.0 - unixSecondsAtAppleReferenceDate
        )
    )
}

internal actual fun formatSharedMobileClockTime(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateStyle = platform.Foundation.NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.stringFromDate(epochMillis.toNSDate())
}

internal actual fun formatSharedMobileBookInfoDateTime(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "MMM dd, yyyy HH:mm"
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
