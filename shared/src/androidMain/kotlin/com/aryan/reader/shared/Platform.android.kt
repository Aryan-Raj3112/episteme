package com.aryan.reader.shared

import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerEvent

actual fun currentTimestamp(): Long = System.currentTimeMillis()

actual fun sharedPdfStylusBarrelPressed(event: PointerEvent): Boolean {
    val buttonState = event.motionEvent?.buttonState ?: return false
    return buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 ||
        buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
}
