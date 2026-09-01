package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class CloudMaintenanceCoordinatorTest {
    private val intent = CloudMaintenanceIntent(userId = "user-1")

    @Test
    fun `clear all runs remote operations before local cleanup`() = runTest {
        val events = mutableListOf<String>()
        val phases = mutableListOf<CloudMaintenancePhase>()
        val result = CloudMaintenanceCoordinator(
            deleteDrive = {
                events += "drive"
                true
            },
            deleteFirestore = {
                events += "firestore"
            },
            clearLocal = {
                events += "local"
            },
        ).clearAll(intent) { phases += it }

        assertIs<CloudMaintenanceResult.Succeeded>(result)
        assertEquals(listOf("drive", "firestore", "local"), events)
        assertEquals(
            listOf(
                CloudMaintenancePhase.REMOTE_DRIVE,
                CloudMaintenancePhase.REMOTE_FIRESTORE,
                CloudMaintenancePhase.LOCAL_CLEANUP,
                CloudMaintenancePhase.SUCCEEDED,
            ),
            phases,
        )
    }

    @Test
    fun `drive failure preserves local and does not start firestore`() = runTest {
        val events = mutableListOf<String>()
        val result = CloudMaintenanceCoordinator(
            deleteDrive = {
                events += "drive"
                false
            },
            deleteFirestore = {
                events += "firestore"
            },
            clearLocal = {
                events += "local"
            },
        ).clearAll(intent)

        val failure = assertIs<CloudMaintenanceResult.Failed>(result)
        assertEquals(CloudMaintenancePhase.REMOTE_DRIVE, failure.phase)
        assertEquals(listOf("drive"), events)
    }

    @Test
    fun `firestore failure preserves local`() = runTest {
        val events = mutableListOf<String>()
        val result = CloudMaintenanceCoordinator(
            deleteDrive = {
                events += "drive"
                true
            },
            deleteFirestore = {
                events += "firestore"
                error("firestore unavailable")
            },
            clearLocal = {
                events += "local"
            },
        ).clearAll(intent)

        val failure = assertIs<CloudMaintenanceResult.Failed>(result)
        assertEquals(CloudMaintenancePhase.REMOTE_FIRESTORE, failure.phase)
        assertEquals(listOf("drive", "firestore"), events)
    }
}
