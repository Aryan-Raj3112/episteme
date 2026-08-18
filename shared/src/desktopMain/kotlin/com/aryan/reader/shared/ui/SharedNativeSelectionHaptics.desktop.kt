package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
actual fun rememberSharedNativeSelectionHaptics(): SharedNativeSelectionHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    return remember { object : SharedNativeSelectionHaptics {
        override fun selectionChanged() {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    } }
}