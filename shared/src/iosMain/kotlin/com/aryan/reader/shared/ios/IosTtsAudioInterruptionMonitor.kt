@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue

/** Translates AVAudioSession interruption notifications into portable events. */
internal sealed interface IosTtsAudioInterruption {
    data object Began : IosTtsAudioInterruption
    data class Ended(val systemAllowsResume: Boolean) : IosTtsAudioInterruption
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

    fun close() {
        center.removeObserver(interruptionObserver)
    }

    private companion object {
        // AVAudioSessionInterruptionType raw values and ShouldResume option bit.
        const val INTERRUPTION_ENDED = 0
        const val INTERRUPTION_BEGAN = 1
        const val INTERRUPTION_OPTION_SHOULD_RESUME = 1
    }
}
