package com.aryan.reader.shared

/**
 * Pure-CloudKit library sync contract (iOS Pro).
 *
 * Android is the absolute benchmark and is NOT changed. Drive+Firestore stays
 * dormant for later cross-platform work; this file only holds platform-agnostic
 * math so Swift (`CloudKitLibraryTransport` / `CloudKitLibraryMapper`) and
 * tests share exact behavior:
 * - stable record/zone naming,
 * - hot-vs-cold dirty classification (metadata-only saves never touch assets),
 * - CloudKit-aware retry delays (honor server `retryAfter`, else shared backoff).
 *
 * Transport (CKRecord/CKAsset I/O, subscriptions, account status) stays Swift-only.
 */

const val CLOUDKIT_LIBRARY_ZONE = "LibraryZone"

const val CLOUDKIT_RECORD_BOOK_STATE = "BookState"
const val CLOUDKIT_RECORD_BOOK_CONTENT = "BookContent"
const val CLOUDKIT_RECORD_PDF_SIDECAR = "PdfSidecar"
const val CLOUDKIT_RECORD_SHELF = "Shelf"
const val CLOUDKIT_RECORD_FONT_META = "FontMeta"
const val CLOUDKIT_RECORD_FONT_CONTENT = "FontContent"
const val CLOUDKIT_RECORD_BOOK_TOMBSTONE = "BookTombstone"

/** Stable record names: book IDs are already unique, only namespaced per type. */
fun cloudKitLibraryRecordName(recordType: String, id: String): String =
    "$recordType:${id.trim()}"

/**
 * Inverse of [cloudKitLibraryRecordName], used to route a delta back to the
 * entity it describes.
 *
 * Splits on the *first* colon: record-type names never contain one, while
 * entity ids may, so `BookState:isbn:1234` must keep `isbn:1234` intact. The id
 * is returned untrimmed because [cloudKitLibraryRecordName] trims on the way
 * out, so a stored name has already been normalized.
 */
data class CloudKitLibraryRecordRef(val recordType: String, val id: String)

fun cloudKitSplitLibraryRecordName(recordName: String): CloudKitLibraryRecordRef? {
    val separator = recordName.indexOf(':')
    if (separator <= 0 || separator == recordName.lastIndex) return null
    return CloudKitLibraryRecordRef(
        recordType = recordName.substring(0, separator),
        id = recordName.substring(separator + 1),
    )
}

/**
 * Whether [id] can be used as part of a CloudKit record name.
 *
 * Record names may not contain '/' and must be stable on every device, so a
 * device-local absolute path is doubly invalid: rejected by CloudKit, and
 * different on every install. Android derives the book id from a content hash
 * (`FileHasher.calculateSha256`) and gates on a non-null cloud filename; iOS
 * reaches the same name via `stableImportId`, which falls back to `localPath`
 * when a caller omits the id. This predicate lets the data plane refuse such a
 * book up front and say so, instead of letting the server reject a malformed
 * name on every single pass.
 */
fun isValidCloudKitLibraryId(id: String): Boolean {
    val trimmed = id.trim()
    return trimmed.isNotEmpty() &&
        trimmed.length <= 255 &&
        trimmed.none { it == '/' || it == ':' || it.isISOControl() }
}

fun cloudKitBookStateRecordName(bookId: String): String =
    cloudKitLibraryRecordName(CLOUDKIT_RECORD_BOOK_STATE, bookId)

fun cloudKitBookContentRecordName(bookId: String): String =
    cloudKitLibraryRecordName(CLOUDKIT_RECORD_BOOK_CONTENT, bookId)

/** Clocks needed to classify one book without touching payload bytes. */
data class CloudKitBookClocks(
    val bookId: String,
    val lastModifiedTimestamp: Long = 0L,
    val readingPositionModifiedTimestamp: Long = 0L,
    val annotationModifiedTimestamp: Long = 0L,
    val fileContentModifiedTimestamp: Long = 0L,
    val sidecarModifiedTimestamp: Long = 0L,
    val localFileAvailable: Boolean = false,
    val remoteModifiedTimestamp: Long = 0L,
    val remoteContentModifiedTimestamp: Long? = null,
    val remoteDeleted: Boolean = false,
)

/** Minimal font clocks for asset-vs-meta split. */
data class CloudKitFontClocks(
    val fontId: String,
    val localTimestamp: Long = 0L,
    val remoteTimestamp: Long = 0L,
    val localFileAvailable: Boolean = false,
)

/** Dynamic write sets: only dirty IDs enter a `CKModifyRecordsOperation`. */
data class CloudKitLibraryDirtySets(
    /** Small `BookState` records (position, progress, bookmarks, metadata). */
    val metadataBookIds: Set<String> = emptySet(),
    /** Inline sidecar records (small PDF annotation JSON, never assets). */
    val sidecarBookIds: Set<String> = emptySet(),
    /** `BookContent` assets (large, rare). */
    val contentBookIds: Set<String> = emptySet(),
    /** `FontContent` assets (large, rare). */
    val fontContentIds: Set<String> = emptySet(),
    /** Tombstone records to publish. */
    val tombstoneBookIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = metadataBookIds.isEmpty() && sidecarBookIds.isEmpty() &&
            contentBookIds.isEmpty() && fontContentIds.isEmpty() &&
            tombstoneBookIds.isEmpty()

    val recordCount: Int
        get() = metadataBookIds.size + sidecarBookIds.size + contentBookIds.size +
            fontContentIds.size + tombstoneBookIds.size
}

/**
 * Classify one book into hot (metadata/sidecar) vs cold (content) writes.
 * Metadata upload uses the shared LWW winner (including sidecar clock);
 * content upload uses the shared content comparator so unchanged bytes never
 * trigger an asset save.
 */
fun classifyCloudKitBookDirty(clocks: CloudKitBookClocks): CloudKitLibraryDirtySets {
    val bookId = clocks.bookId.trim()
    if (bookId.isEmpty()) return CloudKitLibraryDirtySets()
    val metadataDirty = shouldUploadLocalCloudBookUpdate(
        localModifiedTimestamp = clocks.lastModifiedTimestamp,
        remoteModifiedTimestamp = clocks.remoteModifiedTimestamp,
        localSidecarModifiedTimestamp = clocks.sidecarModifiedTimestamp,
    )
    // Sidecar-only fast path: annotation clock wins but base metadata does not
    // need a full state rewrite on its own. Kept as separate record so page
    // turns never wait on annotation payloads and vice versa.
    val sidecarDirty = clocks.sidecarModifiedTimestamp > 0L &&
        shouldUploadLocalCloudBookUpdate(
            localModifiedTimestamp = clocks.sidecarModifiedTimestamp,
            remoteModifiedTimestamp = clocks.remoteModifiedTimestamp,
        )
    val contentDirty = shouldUploadLocalCloudBookContent(
        localFileAvailable = clocks.localFileAvailable,
        localContentModifiedTimestamp = clocks.fileContentModifiedTimestamp,
        remoteContentModifiedTimestamp = clocks.remoteContentModifiedTimestamp,
    )
    return CloudKitLibraryDirtySets(
        metadataBookIds = if (metadataDirty) setOf(bookId) else emptySet(),
        sidecarBookIds = if (sidecarDirty) setOf(bookId) else emptySet(),
        contentBookIds = if (contentDirty) setOf(bookId) else emptySet(),
    )
}

/** Classify many books by merging per-book sets (deterministic, deduped). */
fun classifyCloudKitLibraryDirty(clocks: Collection<CloudKitBookClocks>): CloudKitLibraryDirtySets {
    var metadata = mutableSetOf<String>()
    var sidecars = mutableSetOf<String>()
    var contents = mutableSetOf<String>()
    for (entry in clocks) {
        val single = classifyCloudKitBookDirty(entry)
        metadata += single.metadataBookIds
        sidecars += single.sidecarBookIds
        contents += single.contentBookIds
    }
    return CloudKitLibraryDirtySets(
        metadataBookIds = metadata,
        sidecarBookIds = sidecars,
        contentBookIds = contents,
    )
}

/** Whether a remote book payload must be downloaded (accuracy gate). */
fun shouldFetchCloudKitBookContent(clocks: CloudKitBookClocks): Boolean =
    shouldDownloadRemoteCloudBookContent(
        localFileAvailable = clocks.localFileAvailable,
        localContentModifiedTimestamp = clocks.fileContentModifiedTimestamp,
        remoteContentModifiedTimestamp = clocks.remoteContentModifiedTimestamp ?: 0L,
        remoteDeleted = clocks.remoteDeleted,
    )

/**
 * Whether a deletion tombstone must be published. The previous check compared
 * against the remote *BookState* clock, but a deleted book has no BookState,
 * so every pass re-published every tombstone forever (the repeating
 * `BookTombstone` entries in the save logs). The correct comparison is the
 * remote tombstone clock itself: publish only when no remote tombstone exists
 * or the local deletion is strictly newer.
 */
fun shouldPublishCloudKitTombstone(
    localDeletedAt: Long,
    remoteTombstoneClock: Long?,
): Boolean = remoteTombstoneClock == null || localDeletedAt > remoteTombstoneClock

enum class CloudKitLibraryRetryKind {
    /** Honor server `retryAfter` (rate-limited / zone-busy / unavailable). */
    RETRY_AFTER,
    /** Transient network / service error: shared exponential backoff. */
    TRANSIENT,
    /** Deterministic: validation, unknown item, quota, auth. Do not retry. */
    DETERMINISTIC,
}

/**
 * Retry delay for a failed CloudKit pass. Server-provided `retryAfterMs` wins
 * when present; otherwise shared cloud-sync backoff (`5s * 2^attempt`, 15m max).
 */
fun cloudKitLibraryRetryDelayMs(attempt: Int, retryAfterMs: Long? = null): Long {
    if (retryAfterMs != null && retryAfterMs > 0L) return retryAfterMs
    val safeAttempt = attempt.coerceIn(0, 12)
    val backoff = SHARED_BACKGROUND_CLOUD_SYNC_RETRY_BASE_MS * (1L shl safeAttempt)
    return backoff.coerceIn(
        SHARED_BACKGROUND_CLOUD_SYNC_RETRY_BASE_MS,
        SHARED_BACKGROUND_CLOUD_SYNC_RETRY_MAX_MS,
    )
}

fun shouldRetryCloudKitLibrary(kind: CloudKitLibraryRetryKind): Boolean =
    kind != CloudKitLibraryRetryKind.DETERMINISTIC
