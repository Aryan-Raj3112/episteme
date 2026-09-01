package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ios.IosPdfPerformanceMetrics

internal actual fun recordSharedPdfInteraction() = IosPdfPerformanceMetrics.recordInteraction()
internal actual fun recordSharedPdfCameraUpdate() = IosPdfPerformanceMetrics.recordCameraUpdate()
internal actual fun recordSharedPdfFling() = IosPdfPerformanceMetrics.recordFling()
internal actual fun recordSharedPdfFrame(durationMillis: Long) = IosPdfPerformanceMetrics.recordFrame(durationMillis)
