package com.aryan.reader.shared.ui

internal expect fun formatSharedMobileDateTime(epochMillis: Long): String

internal expect fun formatSharedMobileClockTime(epochMillis: Long): String

internal expect fun formatSharedMobileBookInfoDateTime(epochMillis: Long): String
