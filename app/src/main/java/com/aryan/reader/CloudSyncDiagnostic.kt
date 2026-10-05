package com.aryan.reader

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import timber.log.Timber

/**
 * One tag for every cloud-sync surface, so a single `logcat -s EpistemeCloudSync`
 * shows both sides of sync across both subsystems.
 *
 * Previously the library path used `EpistemeCloudSync` and the folder path used
 * `EpistemeCloudFolderSync`, which meant correlating "device 1 pushed a position"
 * with "device 2 pulled a folder" required watching two interleaved streams and
 * mentally joining them. This file emits every sync event under the single
 * [CloudSyncTraceTag] with explicit `plane` and `dir` fields.
 *
 * Design constraints, matching the existing folder logger:
 *
 *  - **No user data.** Every identifier goes through [cloudSyncDeviceLabel] or
 *    [cloudFolderSafeId], so account IDs, book IDs, URIs, paths, titles and
 *    Drive object IDs never reach logcat. Callers pass derived values only.
 *  - **Both sides, always.** An event names what the local side believed and
 *    what the remote side reported, so "it did not sync" is answerable by
 *    comparing two fields rather than reconstructing state from many lines.
 *  - **No gate on BuildConfig.DEBUG.** The folder logger already ships
 *    unconditionally, and the events here are the same privacy-safe shape. A
 *    release build is exactly where a sync bug needs to be diagnosable from a
 *    user's bug report.
 */
internal const val CloudSyncPlaneLibrary = "library"
internal const val CloudSyncPlaneFolder = "folder"

/** Push = local to remote. Pull = remote to local. Both = a merge did both. */
internal const val CloudSyncDirPush = "push"
internal const val CloudSyncDirPull = "pull"
internal const val CloudSyncDirBoth = "both"
internal const val CloudSyncDirNone = "none"

/**
 * A short, stable, privacy-safe label for this device so two devices in one
 * logcat capture can be told apart. Derived from the FCM `installation_id`
 * (the same value the server-side fan-out excludes by), never logged raw.
 */
internal fun cloudSyncDeviceLabel(installationId: String?): String {
    val normalized = installationId?.trim().orEmpty()
    if (normalized.isBlank()) return "unknown"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    return "dev${digest.take(8)}"
}

/**
 * Identifies the cloud-folder device identity (an `ANDROID_ID`-derived value)
 * when a folder-plane event needs it. Same redaction rule as
 * [cloudSyncDeviceLabel]; separate prefix because it is a different ID space.
 */
internal fun cloudSyncFolderDeviceLabel(folderDeviceId: String?): String {
    val normalized = folderDeviceId?.trim().orEmpty()
    if (normalized.isBlank()) return "unknown"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    return "fdev${digest.take(8)}"
}

/**
 * Emit one sync event under the shared tag.
 *
 * Every field is a pre-redacted string. [local] and [remote] are optional
 * summaries produced by the caller (`cloudSyncTraceSummary`, a manifest
 * revision, a Drive object count); pass `null`/`"none"` when unknown rather than
 * interpolating an identifier that has not been hashed.
 */
internal fun cloudSyncEvent(
    plane: String,
    dir: String,
    event: String,
    result: String = "ok",
    scope: String? = null,
    device: String? = null,
    folderDevice: String? = null,
    local: String? = null,
    remote: String? = null,
    details: String = "",
) {
    val fields = StringBuilder(96)
    fields.append("plane=").append(plane)
    fields.append(" dir=").append(dir)
    fields.append(" event=").append(event)
    fields.append(" result=").append(result)
    fields.append(" self=").append(device ?: "unknown")
    if (folderDevice != null) fields.append(" fself=").append(folderDevice)
    scope?.takeIf { it.isNotBlank() }?.let { fields.append(" scope=").append(it) }
    local?.takeIf { it.isNotBlank() }?.let { fields.append(" local=").append(it) }
    remote?.takeIf { it.isNotBlank() }?.let { fields.append(" remote=").append(it) }
    details.trim().takeIf { it.isNotBlank() }?.let { fields.append(' ').append(it) }

    // An error result is a warning so it survives a filtered logcat capture;
    // everything else is debug level noise.
    val line = fields.toString()
    if (result.startsWith("error") || result.startsWith("blocked") || result.startsWith("deferred")) {
        Timber.tag(CloudSyncTraceTag).w(line)
    } else {
        Timber.tag(CloudSyncTraceTag).d(line)
    }
}

/**
 * A phase boundary for one sync pass. Use with [use] so the matching end or
 * error line is emitted even on an early return or a thrown exception, which
 * is how "the pass started but never finished" becomes visible.
 */
internal inline fun <T> cloudSyncPass(
    plane: String,
    dir: String,
    event: String,
    scope: String? = null,
    device: String? = null,
    folderDevice: String? = null,
    details: String = "",
    block: () -> T,
): T = try {
    cloudSyncEvent(
        plane = plane,
        dir = dir,
        event = event,
        result = "start",
        scope = scope,
        device = device,
        folderDevice = folderDevice,
        details = details,
    )
    val result = block()
    cloudSyncEvent(
        plane = plane,
        dir = dir,
        event = event,
        result = "ok",
        scope = scope,
        device = device,
        folderDevice = folderDevice,
        details = details,
    )
    result
} catch (error: Throwable) {
    cloudSyncEvent(
        plane = plane,
        dir = dir,
        event = event,
        result = "error:${cloudFolderErrorStatus(error)}",
        scope = scope,
        device = device,
        folderDevice = folderDevice,
        details = "reason=${cloudFolderSafeErrorReason(error)}",
    )
    throw error
}