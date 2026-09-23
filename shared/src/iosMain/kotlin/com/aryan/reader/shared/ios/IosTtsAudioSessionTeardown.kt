@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.setActive

/**
 * Audio-session teardown shared by the iOS TTS engines.
 *
 * `AVAudioSession.setActive(false)` can block while the system finishes
 * detaching the audio route, and the engines hit it from synthesizer delegate
 * callbacks or straight from a button press, which froze the UI. The teardown
 * therefore runs off the main thread, on a scope that outlives the engine: a
 * released reader must still hand audio back to the rest of the system.
 */
internal object IosTtsAudioSessionTeardown {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
