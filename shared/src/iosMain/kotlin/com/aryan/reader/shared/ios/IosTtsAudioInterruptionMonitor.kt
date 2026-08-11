@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue

/** Translates AVAudioSession interruption notifications into portable events. */
internal sealed interface IosTtsAudioInterruption {
    data object Began : IosTtsAudioInterruption
    data class Ended(val systemAllowsResume: Boolean) : IosTtsAudioInterruption
    data object OutputBecameUnavailable : IosTtsAudioInterruption
}

internal class IosTtsAudioInterruptionMonitor(
    onEvent: (IosTtsAudioInterruption) -> Unit,
) {
    private val center = NSNotificationCenter.defaultCenter
    private val interruptionObserver = center.addObserverForName(
        name = AVAudioSessionInterruptionNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        val userInfo = notification?.userInfo ?: return@addObserverForName
        val type = (userInfo[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.intValue
        when (type) {
            INTERRUPTION_BEGAN -> onEvent(IosTtsAudioInterruption.Began)
            INTERRUPTION_ENDED -> {
                val options = (userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.intValue ?: 0
                onEvent(
                    IosTtsAudioInterruption.Ended(
                        systemAllowsResume = options and INTERRUPTION_OPTION_SHOULD_RESUME != 0
                    )
                )
            }
        }
    }
    private val routeObserver = center.addObserverForName(
        name = AVAudioSessionRouteChangeNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.intValue
        if (reason == ROUTE_OLD_DEVICE_UNAVAILABLE) {
            onEvent(IosTtsAudioInterruption.OutputBecameUnavailable)
        }
    }

    fun close() {
        center.removeObserver(interruptionObserver)
        center.removeObserver(routeObserver)
    }

    private companion object {
        // AVAudioSessionInterruptionType raw values and ShouldResume option bit.
        const val INTERRUPTION_ENDED = 0
        const val INTERRUPTION_BEGAN = 1
        const val INTERRUPTION_OPTION_SHOULD_RESUME = 1
        const val ROUTE_OLD_DEVICE_UNAVAILABLE = 2
    }
}
