package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable

/**
 * Platform haptic feedback for native text-selection handle drags.
 *
 * Android benchmark: `HapticFeedbackType.TextHandleMove` ticks once per effective selection
 * change while dragging a handle. Compose Multiplatform maps `TextHandleMove` to a no-op on
 * iOS, so each platform provides its own implementation (iOS: `UISelectionFeedbackGenerator`).
 */
interface SharedNativeSelectionHaptics {
    fun selectionChanged()
}

@Composable
expect fun rememberSharedNativeSelectionHaptics(): SharedNativeSelectionHaptics