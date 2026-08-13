package com.aryan.reader.shared

import androidx.compose.ui.input.pointer.PointerEvent

actual fun currentTimestamp(): Long = System.currentTimeMillis()

actual fun sharedPdfStylusBarrelPressed(event: PointerEvent): Boolean = false
