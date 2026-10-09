package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Guards the folder annotation sidecar export rule.
 *
 * The bug this prevents was observed in a live two-device session: a device
 * that had not yet imported its peer's annotations published a 31-byte empty
 * sidecar stamped with wall clock, moments before publishing its real payload.
 * Because the sidecar merges last-writer-wins, that empty state outranked the
 * peer's real annotations and they never arrived.
 *
 * These tests encode the three cases that must hold forever:
 *  - a real local edit is stamped with wall clock
 *  - a clear only ever outranks the snapshot it is clearing
 *  - nothing is published when there is neither a payload nor a prior sidecar
 */
class SharedAnnotationSidecarExportPolicyTest {

    private companion object {
        const val PEER_SIDECAR_TS = 1_700_000_000_000L
        const val LOCAL_ARTIFACT_TS = 1_700_000_100_000L
        const val NOW = 1_700_000_200_000L
    }

    @Test
    fun localEditIsStampedWithWallClockSoItOutranksThePeer() {
        val decision = decideSharedAnnotationSidecarExport(
            hasLocalPayload = true,
            newestLocalArtifactTimestamp = LOCAL_ARTIFACT_TS,
            priorSidecarTimestamp = PEER_SIDECAR_TS,
            now = NOW,
        )

        val plan = assertIs<SharedAnnotationSidecarExportDecision.Write>(decision).plan
        assertEquals(NOW, plan.timestamp)
        assertTrue(!plan.isClear, "a real edit must not be recorded as a clear")
    }

    @Test
    fun localEditNeverStampsBelowItsOwnArtifactTime() {
        // An import writes artifact files with the sidecar's own timestamp. If
        // the wall clock were somehow behind, regressing would let the peer win
        // and silently drop the just-imported edit on the next merge.
        val decision = decideSharedAnnotationSidecarExport(
            hasLocalPayload = true,
            newestLocalArtifactTimestamp = NOW,
            priorSidecarTimestamp = PEER_SIDECAR_TS,
            now = PEER_SIDECAR_TS,
        )

        val plan = assertIs<SharedAnnotationSidecarExportDecision.Write>(decision).plan
        assertEquals(NOW, plan.timestamp)
    }

    @Test
    fun clearOutranksTheSnapshotItClearsButNotTheWallClock() {
        val decision = decideSharedAnnotationSidecarExport(
            hasLocalPayload = false,
            newestLocalArtifactTimestamp = 0L,
            priorSidecarTimestamp = PEER_SIDECAR_TS,
            now = NOW,
        )

        val plan = assertIs<SharedAnnotationSidecarExportDecision.Write>(decision).plan
        assertTrue(plan.isClear, "empty local state against a prior sidecar is a clear")
        assertEquals(PEER_SIDECAR_TS + 1L, plan.timestamp)
        assertTrue(
            plan.timestamp > PEER_SIDECAR_TS,
            "a clear that does not outrank its target would be ignored by the merge",
        )
        assertTrue(
            plan.timestamp < NOW,
            "a clear must not claim to be newer than the peer's real edits; " +
                "that is what erased annotations across devices",
        )
    }

    @Test
    fun deviceThatHasNotImportedYetPublishesNothing() {
        // The core regression. No local payload and no sidecar means this
        // device has no basis for claiming the book has no annotations.
        val decision = decideSharedAnnotationSidecarExport(
            hasLocalPayload = false,
            newestLocalArtifactTimestamp = 0L,
            priorSidecarTimestamp = 0L,
            now = NOW,
        )

        assertIs<SharedAnnotationSidecarExportDecision.Skip>(decision)
    }

    @Test
    fun saturatedPriorTimestampStaysSaturated() {
        // Long.MAX_VALUE is the existing "permanently cleared" sentinel and
        // must not overflow to a negative value.
        val decision = decideSharedAnnotationSidecarExport(
            hasLocalPayload = false,
            newestLocalArtifactTimestamp = 0L,
            priorSidecarTimestamp = Long.MAX_VALUE,
            now = NOW,
        )

        val plan = assertIs<SharedAnnotationSidecarExportDecision.Write>(decision).plan
        assertEquals(Long.MAX_VALUE, plan.timestamp)
    }

    @Test
    fun clearNeverOutranksARealPeerEditMadeAfterIt() {
        // End-to-end shape of the bug: device B clears (or has simply not
        // imported) while device A makes a real edit afterwards. A must win.
        val peerRealEditTs = 1_700_000_500_000L
        val deviceAClear = assertIs<SharedAnnotationSidecarExportDecision.Write>(
            decideSharedAnnotationSidecarExport(
                hasLocalPayload = false,
                newestLocalArtifactTimestamp = 0L,
                priorSidecarTimestamp = PEER_SIDECAR_TS,
                now = PEER_SIDECAR_TS,
            ),
        ).plan.timestamp

        assertTrue(
            peerRealEditTs > deviceAClear,
            "the peer's later real edit must win over the clear",
        )
    }
}
