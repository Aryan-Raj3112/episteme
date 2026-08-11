package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalTtsInterruptionPolicyTest {
    @Test
    fun activePlaybackPausesAndResumesOnlyWhenTheSystemAllowsIt() {
        val began = LocalTtsInterruptionState().reduce(
            LocalTtsInterruptionEvent.Began(playbackWasActive = true)
        )
        assertEquals(LocalTtsInterruptionAction.PAUSE, began.action)

        val ended = began.state.reduce(
            LocalTtsInterruptionEvent.Ended(systemAllowsResume = true)
        )
        assertEquals(LocalTtsInterruptionAction.RESUME, ended.action)
        assertEquals(LocalTtsInterruptionState(), ended.state)
    }

    @Test
    fun pausedPlaybackAndNonResumableInterruptionsStayPaused() {
        val alreadyPaused = LocalTtsInterruptionState().reduce(
            LocalTtsInterruptionEvent.Began(playbackWasActive = false)
        )
        assertEquals(LocalTtsInterruptionAction.NONE, alreadyPaused.action)
        assertEquals(
            LocalTtsInterruptionAction.NONE,
            alreadyPaused.state.reduce(LocalTtsInterruptionEvent.Ended(true)).action,
        )

        val interrupted = LocalTtsInterruptionState().reduce(
            LocalTtsInterruptionEvent.Began(playbackWasActive = true)
        )
        assertEquals(
            LocalTtsInterruptionAction.NONE,
            interrupted.state.reduce(LocalTtsInterruptionEvent.Ended(false)).action,
        )
    }
}
