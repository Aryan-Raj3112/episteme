package com.aryan.reader.shared

import kotlin.coroutines.cancellation.CancellationException

/**
 * The destructive cloud-maintenance operations are deliberately modelled as
 * a remote-first state machine.  A platform can use any transport it needs,
 * but it must not start local cleanup until both remote operations complete.
 */
data class CloudMaintenanceIntent(
    val userId: String,
)

enum class CloudMaintenancePhase {
    REMOTE_DRIVE,
    REMOTE_FIRESTORE,
    LOCAL_CLEANUP,
    SUCCEEDED,
    FAILED,
}

sealed interface CloudMaintenanceResult {
    data object Succeeded : CloudMaintenanceResult

    data class Failed(
        val phase: CloudMaintenancePhase,
        val error: Exception,
    ) : CloudMaintenanceResult
}

/**
 * Runs a clear-all operation in its safety-critical order.
 *
 * The operation lambdas are injected so this contract stays pure and can be
 * exercised without a Drive/Firestore SDK. A `false` Drive result is treated
 * as a failed remote operation rather than a successful no-op.
 */
class CloudMaintenanceCoordinator(
    private val deleteDrive: suspend (CloudMaintenanceIntent) -> Boolean,
    private val deleteFirestore: suspend (CloudMaintenanceIntent) -> Unit,
    private val clearLocal: suspend () -> Unit,
) {
    suspend fun clearAll(
        intent: CloudMaintenanceIntent,
        onPhaseChanged: (CloudMaintenancePhase) -> Unit = {},
    ): CloudMaintenanceResult {
        suspend fun runRemote(
            phase: CloudMaintenancePhase,
            operation: suspend () -> Unit,
        ): CloudMaintenanceResult? {
            onPhaseChanged(phase)
            return try {
                operation()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onPhaseChanged(CloudMaintenancePhase.FAILED)
                CloudMaintenanceResult.Failed(phase, error)
            }
        }

        runRemote(CloudMaintenancePhase.REMOTE_DRIVE) {
            check(deleteDrive(intent)) { "Drive deletion did not complete" }
        }?.let { return it }

        runRemote(CloudMaintenancePhase.REMOTE_FIRESTORE) {
            deleteFirestore(intent)
        }?.let { return it }

        runRemote(CloudMaintenancePhase.LOCAL_CLEANUP) {
            clearLocal()
        }?.let { return it }

        onPhaseChanged(CloudMaintenancePhase.SUCCEEDED)
        return CloudMaintenanceResult.Succeeded
    }
}
