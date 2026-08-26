package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferContractsTest {
    @Test
    fun appOwnedPlanPromisesAtomicCommitAndPreservesExistingFinal() {
        val plan = SharedTransferPlan.appOwnedAtomic("transfer-1", "book.epub")

        assertTrue(plan.supportsAtomicCommit)
        assertTrue(plan.preservesExistingFinalOnFailure)
        assertFalse(plan.cleanupIsBestEffort)
    }

    @Test
    fun providerPlanDoesNotClaimAtomicityAndOnlyAllowsBestEffortCleanup() {
        val plan = SharedTransferPlan.providerCreateDocument("transfer-2", "book.epub")

        assertFalse(plan.supportsAtomicCommit)
        assertFalse(plan.preservesExistingFinalOnFailure)
        assertTrue(plan.cleanupIsBestEffort)
    }

    @Test
    fun stateMachineRequiresStagingBeforeCommitAndRetainsFailure() {
        val plan = SharedTransferPlan.appOwnedAtomic("transfer-3", "book.pdf")
        val completed = plan.initialState()
            .start()
            .markStaged(42L)
            .beginCommit()
            .complete()
        assertEquals(SharedTransferPhase.COMPLETED, completed.phase)
        assertEquals(42L, completed.bytesTransferred)

        val failed = plan.initialState()
            .start()
            .fail("download failed")
        assertEquals(SharedTransferPhase.FAILED, failed.phase)
        assertEquals("download failed", failed.errorMessage)
    }

    @Test
    fun providerStateCompletesWithoutPretendingToCommitAtomically() {
        val completed = SharedTransferPlan.providerCreateDocument("transfer-4", "book.epub")
            .initialState()
            .start()
            .markStaged(7L)
            .completeProviderWrite()

        assertEquals(SharedTransferPhase.COMPLETED, completed.phase)
        assertEquals(7L, completed.bytesTransferred)
    }
}
