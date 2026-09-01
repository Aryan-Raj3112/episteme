package com.aryan.reader.shared

/**
 * Describes who owns the destination of a file transfer.
 *
 * An app-owned destination can be staged beside its final path and committed
 * with an atomic rename. A document-provider destination (for example an
 * Android SAF CreateDocument URI) is opened by another process; writing it is
 * inherently non-transactional, so callers may only make best-effort cleanup
 * attempts after an error.
 */
enum class SharedTransferSink {
    APP_OWNED_ATOMIC,
    PROVIDER_NON_TRANSACTIONAL,
}

enum class SharedTransferPhase {
    PLANNED,
    STAGING,
    STAGED,
    COMMITTING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class SharedTransferPlan(
    val transferId: String,
    val destinationName: String,
    val sink: SharedTransferSink,
) {
    val supportsAtomicCommit: Boolean
        get() = sink == SharedTransferSink.APP_OWNED_ATOMIC

    /** True only when a failed commit must leave an existing final untouched. */
    val preservesExistingFinalOnFailure: Boolean
        get() = supportsAtomicCommit

    /** Provider output can only be cleaned up best-effort by the caller. */
    val cleanupIsBestEffort: Boolean
        get() = sink == SharedTransferSink.PROVIDER_NON_TRANSACTIONAL

    companion object {
        fun appOwnedAtomic(
            transferId: String,
            destinationName: String,
        ): SharedTransferPlan = SharedTransferPlan(
            transferId = transferId,
            destinationName = destinationName,
            sink = SharedTransferSink.APP_OWNED_ATOMIC,
        )

        fun providerCreateDocument(
            transferId: String,
            destinationName: String,
        ): SharedTransferPlan = SharedTransferPlan(
            transferId = transferId,
            destinationName = destinationName,
            sink = SharedTransferSink.PROVIDER_NON_TRANSACTIONAL,
        )
    }
}

data class SharedTransferState(
    val plan: SharedTransferPlan,
    val phase: SharedTransferPhase = SharedTransferPhase.PLANNED,
    val bytesTransferred: Long = 0L,
    val errorMessage: String? = null,
) {
    fun start(): SharedTransferState {
        check(phase == SharedTransferPhase.PLANNED) { "Transfer is already started: $phase" }
        return copy(phase = SharedTransferPhase.STAGING, errorMessage = null)
    }

    fun markStaged(bytes: Long): SharedTransferState {
        check(phase == SharedTransferPhase.STAGING) { "Transfer is not staging: $phase" }
        require(bytes >= 0L) { "Transferred bytes cannot be negative" }
        return copy(
            phase = SharedTransferPhase.STAGED,
            bytesTransferred = bytes,
            errorMessage = null,
        )
    }

    fun beginCommit(): SharedTransferState {
        check(phase == SharedTransferPhase.STAGED) { "Transfer is not staged: $phase" }
        check(plan.supportsAtomicCommit) {
            "Provider transfers have no atomic commit: ${plan.sink}"
        }
        return copy(phase = SharedTransferPhase.COMMITTING, errorMessage = null)
    }

    fun complete(): SharedTransferState {
        check(phase == SharedTransferPhase.COMMITTING) { "Transfer is not committing: $phase" }
        check(plan.supportsAtomicCommit) {
            "Only app-owned transfers have an atomic commit: ${plan.sink}"
        }
        return copy(phase = SharedTransferPhase.COMPLETED, errorMessage = null)
    }

    /**
     * Marks a provider write complete after its stream has been closed. This
     * is deliberately separate from [beginCommit]/[complete]: a provider sink
     * has no atomic commit and only offers best-effort cleanup on failure.
     */
    fun completeProviderWrite(): SharedTransferState {
        check(phase == SharedTransferPhase.STAGED) { "Provider transfer is not staged: $phase" }
        check(plan.cleanupIsBestEffort) {
            "App-owned transfers require an atomic commit: ${plan.sink}"
        }
        return copy(phase = SharedTransferPhase.COMPLETED, errorMessage = null)
    }

    fun fail(message: String): SharedTransferState {
        check(
            phase != SharedTransferPhase.COMPLETED &&
                phase != SharedTransferPhase.FAILED &&
                phase != SharedTransferPhase.CANCELLED,
        ) { "Transfer cannot fail from $phase" }
        return copy(
            phase = SharedTransferPhase.FAILED,
            errorMessage = message.takeIf { it.isNotBlank() },
        )
    }

    fun cancel(): SharedTransferState {
        check(
            phase != SharedTransferPhase.COMPLETED &&
                phase != SharedTransferPhase.FAILED &&
                phase != SharedTransferPhase.CANCELLED,
        ) { "Transfer cannot be cancelled from $phase" }
        return copy(phase = SharedTransferPhase.CANCELLED)
    }
}

fun SharedTransferPlan.initialState(): SharedTransferState = SharedTransferState(plan = this)
