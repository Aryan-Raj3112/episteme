package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UISelectionFeedbackGenerator

private class IosSelectionHaptics : SharedNativeSelectionHaptics {
    private val generator = UISelectionFeedbackGenerator()

    override fun selectionChanged() {
        // Android parity (immediate TextHandleMove tick): warm the Taptic
        // Engine on every tick so sustained handle drags stay crisp. The very
        // first tick after idle can still drop while the engine spins up —
        // there is no gesture-start hook at the call sites to prepare earlier.
        generator.prepare()
        generator.selectionChanged()
    }
}

@Composable
actual fun rememberSharedNativeSelectionHaptics(): SharedNativeSelectionHaptics {
    return remember { IosSelectionHaptics() }
}