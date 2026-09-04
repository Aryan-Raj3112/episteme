package com.aryan.reader.shared

/**
 * Folder-head wake policy, shared first per AGENTS.md.
 *
 * Android wakes folder pulls from a foreground-only Firestore listener on
 * `users/{uid}/cloudFolderHeads` (`CloudFolderHeadListenerCoordinator`: attach
 * on foreground, detach on background, 500ms per-root debounce, pull only
 * when the remote revision is still ahead after the debounce). The iOS
 * executor consumes this predicate; Android keeps its own copy untouched.
 */
const val CLOUD_FOLDER_HEAD_DEBOUNCE_MILLIS = 500L

const val CLOUD_FOLDER_HEAD_COMMITTED_STATE = "COMMITTED"

const val CLOUD_FOLDER_HEAD_COMMITTING_STATE = "COMMITTING"

/**
 * Whether a head update with [remoteRevision] should schedule a pull given
 * the durable [knownRevision] (max of manifest revision and binding ack),
 * selection [isIncluded], and whether this device holds a [hasBinding].
 */
fun shouldScheduleCloudFolderHeadPull(
    remoteRevision: Long,
    knownRevision: Long,
    hasBinding: Boolean,
    isIncluded: Boolean,
): Boolean =
    remoteRevision >= 0L &&
        remoteRevision > knownRevision &&
        (!hasBinding || isIncluded)
