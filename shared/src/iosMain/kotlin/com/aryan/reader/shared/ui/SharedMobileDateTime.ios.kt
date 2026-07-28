package com.aryan.reader.shared.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

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
