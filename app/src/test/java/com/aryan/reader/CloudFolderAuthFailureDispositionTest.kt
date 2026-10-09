package com.aryan.reader

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Drive 401 must never permanently disable folder sync.
 *
 * Observed live: a stale access token produced
 *
 *   folder_pass_end result=error:unauthenticated disposition=failure stage=pull_root
 *
 * `Result.failure()` tells WorkManager to stop, so one expired token silenced
 * every later pass until the process restarted. The same 401 on the publish
 * path was classified `disposition=retry`, because that path wraps the Drive
 * exception in a `CloudFolderTransferException` while the manifest-read path
 * wraps it in a plain `IOException` — and the transient check only inspected
 * `CloudFolderDriveException`. Same root cause, opposite dispositions.
 */
class CloudFolderAuthFailureDispositionTest {

    private fun driveException(
        httpStatusCode: Int,
        driveReason: String = "authError",
        statusCategory: String = "unauthenticated",
        bodyCategory: String = statusCategory,
    ) = CloudFolderDriveException(
        httpStatusCode = httpStatusCode,
        bodyCategory = bodyCategory,
        statusCategory = statusCategory,
        driveReason = driveReason,
        driveDomain = "global",
        driveErrorCode = httpStatusCode,
    )

    /** Mirrors the worker's terminal decision exactly. */
    private fun isTerminal(error: Throwable): Boolean =
        cloudFolderFailureIsDeterministic(error) && !cloudFolderAuthFailureIsTransient(error)

    @Test
    fun `stale token is transient`() {
        assertTrue(
            "a 401 is an expired token and must heal by re-fetching",
            cloudFolderAuthFailureIsTransient(driveException(httpStatusCode = 401)),
        )
    }

    @Test
    fun `stale token yields retry despite being in the deterministic status set`() {
        val error = driveException(httpStatusCode = 401)

        assertTrue(
            "precondition: the status is classified as an auth failure",
            cloudFolderErrorStatus(error) in setOf("unauthenticated", "unauthorized"),
        )
        assertFalse(
            "an expired token must not be treated as terminal",
            isTerminal(error),
        )
    }

    @Test
    fun `auth failure survives a plain IOException wrapper`() {
        // This is the manifest-read shape that actually shipped:
        //   throw IOException("Unable to read cloud-folder manifest: ...", driveException)
        // The wrapper is not a CloudFolderDriveException, so inspecting only the
        // outer type lost the 401 and the pass was marked terminal.
        val wrapped = IOException("Unable to read cloud-folder manifest", driveException(401))

        assertTrue(
            "a wrapped 401 must still be recognised as transient",
            cloudFolderAuthFailureIsTransient(wrapped),
        )
        assertFalse(
            "a wrapped 401 must not stop the worker",
            isTerminal(wrapped),
        )
    }

    @Test
    fun `auth failure survives a transfer exception wrapper`() {
        // The publish shape: CloudFolderTransferException wrapping the 401.
        val wrapped = CloudFolderTransferException(
            stage = "manifest_publish",
            category = "file_transfer_failure",
            statusCategory = "unknown",
            cause = driveException(401),
        )

        assertTrue(
            "a transfer-wrapped 401 must still be recognised as transient",
            cloudFolderAuthFailureIsTransient(wrapped),
        )
        assertFalse(
            "a transfer-wrapped 401 must not stop the worker",
            isTerminal(wrapped),
        )
    }

    @Test
    fun `both wrapper shapes now agree on disposition`() {
        // The regression in one assertion: identical root cause, one path
        // retried and the other stopped.
        val drive = driveException(httpStatusCode = 401)
        val ioWrapped = IOException("Unable to read cloud-folder manifest", drive)
        val transferWrapped = CloudFolderTransferException(
            stage = "manifest_publish",
            category = "file_transfer_failure",
            cause = drive,
        )

        assertFalse("IOException wrapper must retry", isTerminal(ioWrapped))
        assertFalse("transfer wrapper must retry", isTerminal(transferWrapped))
    }

    @Test
    fun `genuine revocation is still deterministic`() {
        // permission_denied means the user actually revoked Drive access.
        // Retrying forever would be wrong, so this must stay terminal.
        val revoked = driveException(
            httpStatusCode = 403,
            driveReason = "insufficientPermissions",
            statusCategory = "permission_denied",
        )

        assertFalse(cloudFolderAuthFailureIsTransient(revoked))
        assertTrue(
            "a real permission revocation should stop without user action",
            isTerminal(revoked),
        )
    }

    @Test
    fun `not found is retryable and not treated as auth`() {
        // A missing manifest can be transient (the object was archived, or a
        // peer had not finished uploading) so retrying is correct. It is also
        // not an auth failure, so the stale-token exemption must not apply to
        // it — that exemption is deliberately narrow.
        val missing = driveException(
            httpStatusCode = 404,
            driveReason = "notFound",
            statusCategory = "not_found",
        )

        assertFalse(cloudFolderAuthFailureIsTransient(missing))
        assertFalse(
            "not_found is not in the deterministic set, so it retries",
            isTerminal(missing),
        )
    }

    @Test
    fun `network failure stays retryable`() {
        val network = driveException(
            httpStatusCode = 503,
            driveReason = "backendError",
            statusCategory = "network",
            bodyCategory = "unknown",
        )

        assertFalse(cloudFolderAuthFailureIsTransient(network))
        assertFalse(
            "a 5xx must stay retryable",
            isTerminal(network),
        )
    }
}
