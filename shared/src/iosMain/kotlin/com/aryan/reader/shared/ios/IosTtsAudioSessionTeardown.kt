@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive

/**
 * Audio-session activation/teardown shared by the iOS TTS engines.
 *
 * `AVAudioSession.setActive(true/false)` can block while the system
 * attaches/detaches the audio route, and the engines hit it straight from a
 * button press or a synthesizer delegate callback, which froze the UI
 * (`AVAudioSession_iOS.mm:978` warns against main-thread calls). Both
 * directions therefore run off the main thread, on a scope that outlives the
 * engine: a released reader must still hand audio back to the system, and a
 * starting session must not gate playback on route negotiation.
 */
internal object IosTtsAudioSessionTeardown {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Activates the shared playback session off the main thread.
     * Fire-and-forget on purpose: playback starts immediately while route
     * negotiation finishes in the background.
     */
    fun activate() {
        scope.launch {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(active = true, error = null)
        }
    }

    /**
     * Deactivates the shared audio session off the main thread.
     * Unconditional variant for engines without a generation guard.
     */
    fun deactivate() {
        scope.launch {
            AVAudioSession.sharedInstance().setActive(active = false, error = null)
        }
    }

    /**
     * Deactivates the shared audio session unless [isStillOwner] reports that a
     * newer session has taken it over in the meantime.
     */
    fun deactivateIfStillOwner(isStillOwner: suspend () -> Boolean) {
        scope.launch {
            if (withContext(Dispatchers.Main) { isStillOwner() }) {
                AVAudioSession.sharedInstance().setActive(active = false, error = null)
            }
        }
    }
}
