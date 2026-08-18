package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UISelectionFeedbackGenerator

private class IosSelectionHaptics : SharedNativeSelectionHaptics {
    private val generator = UISelectionFeedbackGenerator()

    override fun selectionChanged() {
        generator.selectionChanged()
    }
}

@Composable
actual fun rememberSharedNativeSelectionHaptics(): SharedNativeSelectionHaptics {
    return remember { IosSelectionHaptics() }
}