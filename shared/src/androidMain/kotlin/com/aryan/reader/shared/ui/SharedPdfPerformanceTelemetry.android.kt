package com.aryan.reader.shared.ui

/** Android keeps its established PDF diagnostics; this port adds no new runtime work there. */
internal actual fun recordSharedPdfInteraction() = Unit
internal actual fun recordSharedPdfCameraUpdate() = Unit
internal actual fun recordSharedPdfFling() = Unit
internal actual fun recordSharedPdfFrame(durationMillis: Long) = Unit
