package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudTtsPlaybackMonitorPolicyTest {
    @Test
    fun positionAtTheStreamEndCountsAsFinished() {
        assertEquals(
            CloudTtsPlaybackMonitorPolicy.Action.FINISHED,
            CloudTtsPlaybackMonitorPolicy.evaluate(
                position = 12.0,
                duration = 12.0,
                playing = true,
                stalledSamples = 0,
            ),
        )
    }

    @Test
    fun mp3TailRoundingShortOfDurationStillCountsAsFinished() {
        // Encoder delay/padding keeps the reported position a fraction short of
        // the nominal duration; advancing only on exact equality would hang here.
        assertEquals(
            CloudTtsPlaybackMonitorPolicy.Action.FINISHED,
            CloudTtsPlaybackMonitorPolicy.evaluate(
                position = 11.9,
                duration = 12.0,
                playing = true,
                stalledSamples = 0,
            ),
        )
    }

    @Test
    fun playbackBeforeTheTailIsNotFinished() {
        assertEquals(
            CloudTtsPlaybackMonitorPolicy.Action.PLAYING,
            CloudTtsPlaybackMonitorPolicy.evaluate(
                position = 6.0,
                duration = 12.0,
                playing = true,
                stalledSamples = 0,
            ),
        )
    }

    @Test
    fun midStreamFreezeIsReportedButNeverSkipsTheChunk() {
        // The reported iOS stall: audio reached the end but the finish delegate
        // never arrived. A freeze part-way through must not be mistaken for the
        // end, or the reader would silently drop unheard text.
        assertEquals(
            CloudTtsPlaybackMonitorPolicy.Action.STALLED,
            CloudTtsPlaybackMonitorPolicy.evaluate(
                position = 3.0,
                duration = 12.0,
                playing = true,
                stalledSamples = CloudTtsPlaybackMonitorPolicy.STALL_SAMPLES,
            ),
        )
    }

    @Test
    fun pausedOrStoppedPlaybackIsIdleAndNeverAdvances() {
        assertEquals(
            CloudTtsPlaybackMonitorPolicy.Action.IDLE,
            CloudTtsPlaybackMonitorPolicy.evaluate(
                position = 3.0,
                duration = 12.0,
                playing = false,
                stalledSamples = 0,
            ),
        )
    }

    @Test
    fun unknownDurationCannotReportFinished() {
        // duration <= 0 means the player has not reported a usable length; the
        // policy must fall back to the completion callback alone.
        assertEquals(
            CloudTtsPlaybackMonitorPolicy.Action.PLAYING,
            CloudTtsPlaybackMonitorPolicy.evaluate(
                position = 5.0,
                duration = 0.0,
                playing = true,
                stalledSamples = 0,
            ),
        )
    }

    @Test
    fun aPlayerThatStoppedOnItsOwnStillCountsAsFinished() {
        // A player that played to the end reports isPlaying == false by the
        // time the monitor samples it. End detection must not depend on that
        // flag or the chunk would never transition.
        assertEquals(
            CloudTtsPlaybackMonitorPolicy.Action.FINISHED,
            CloudTtsPlaybackMonitorPolicy.evaluate(
                position = 12.0,
                duration = 12.0,
                playing = false,
                stalledSamples = 0,
            ),
        )
    }

    @Test
    fun reachedEndNeedsAUsableDuration() {
        assertEquals(
            false,
            CloudTtsPlaybackMonitorPolicy.hasReachedEnd(position = 5.0, duration = 0.0),
        )
        assertEquals(
            true,
            CloudTtsPlaybackMonitorPolicy.hasReachedEnd(position = 9.9, duration = 10.0),
        )
    }

    @Test
    fun frozenSamplesAccumulateAndResetOncePositionMoves() {
        assertEquals(
            1,
            CloudTtsPlaybackMonitorPolicy.nextStalledSamples(
                previous = 0,
                position = 3.0,
                lastPosition = 3.0,
            ),
        )
        assertEquals(
            2,
            CloudTtsPlaybackMonitorPolicy.nextStalledSamples(
                previous = 1,
                position = 3.0,
                lastPosition = 3.0,
            ),
        )
        assertEquals(
            0,
            CloudTtsPlaybackMonitorPolicy.nextStalledSamples(
                previous = 2,
                position = 3.2,
                lastPosition = 3.0,
            ),
        )
    }

    @Test
    fun idleSamplesAccumulateOnlyWhileAPlayerThatShouldPlaySitsAtZero() {
        assertEquals(
            1,
            CloudTtsPlaybackMonitorPolicy.nextIdleSamples(previous = 0, position = 0.0, playing = false),
        )
        // A pause is not a failed start: the counter resets, so resuming later is not reported.
        assertEquals(
            0,
            CloudTtsPlaybackMonitorPolicy.nextIdleSamples(previous = 3, position = 1.5, playing = false),
        )
        // Once it plays, it started, whatever happened before.
        assertEquals(
            0,
            CloudTtsPlaybackMonitorPolicy.nextIdleSamples(previous = 3, position = 0.0, playing = true),
        )
    }

    @Test
    fun aStartThatNeverProducedAudioIsDetectedAfterTheBudget() {
        var idleSamples = 0
        repeat(CloudTtsPlaybackMonitorPolicy.NEVER_STARTED_SAMPLES - 1) {
            idleSamples = CloudTtsPlaybackMonitorPolicy.nextIdleSamples(idleSamples, 0.0, playing = false)
            assertFalse(CloudTtsPlaybackMonitorPolicy.hasNeverStarted(idleSamples))
        }
        idleSamples = CloudTtsPlaybackMonitorPolicy.nextIdleSamples(idleSamples, 0.0, playing = false)
        assertTrue(CloudTtsPlaybackMonitorPolicy.hasNeverStarted(idleSamples))
    }
}
