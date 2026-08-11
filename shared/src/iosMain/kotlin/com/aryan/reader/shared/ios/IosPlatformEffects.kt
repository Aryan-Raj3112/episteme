package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ui.SharedMobilePdfNativeAction
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen

internal data class IosAppLifecycleState(
    val isActive: Boolean = true,
    val eventId: Long = 0L,
)

/** Native reader-window effects kept behind a replaceable boundary for lifecycle tests. */
internal interface IosReaderSystemEffects {
    val brightness: Double

    fun setKeepScreenOn(enabled: Boolean)

    fun setBrightness(value: Double)
}

internal object UIKitReaderSystemEffects : IosReaderSystemEffects {
    override val brightness: Double
        get() = UIScreen.mainScreen.brightness

    override fun setKeepScreenOn(enabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }

    override fun setBrightness(value: Double) {
        UIScreen.mainScreen.brightness = value
    }
}

internal fun interface IosPdfNativeActionPresenter {
    fun perform(book: BookItem, action: SharedMobilePdfNativeAction): Boolean
}
