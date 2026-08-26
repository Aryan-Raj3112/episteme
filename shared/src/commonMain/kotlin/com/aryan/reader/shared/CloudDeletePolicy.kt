package com.aryan.reader.shared

/**
 * A delete is idempotent when the server confirms success or says that the
 * target is already gone.  Keeping this policy in shared code prevents the
 * Android and desktop transports from diverging on retries.
 */
fun isIdempotentCloudDeleteStatus(statusCode: Int): Boolean =
    statusCode in 200..299 || statusCode == 404

/**
 * Merge deletion intents by book while retaining the content type needed to
 * remove a Drive payload. A later tombstone may come from a metadata-only
 * path and omit [CloudBookTombstone.type]; that omission must not erase a
 * type already known by an earlier intent.
 */
fun mergeCloudBookTombstones(
    tombstones: Collection<CloudBookTombstone>,
): List<CloudBookTombstone> {
    return tombstones
        .filter { it.bookId.isNotBlank() }
        .groupBy(CloudBookTombstone::bookId)
        .map { (_, values) ->
            val newest = values.maxWithOrNull(
                compareBy<CloudBookTombstone> { it.deletedAt }
                    .thenBy { if (it.type.normalizedCloudDeleteType() == null) 0 else 1 }
                    .thenBy { it.type.normalizedCloudDeleteType().orEmpty() },
            ) ?: error("A non-empty tombstone group was expected")
            val preservedType = newest.type.normalizedCloudDeleteType()
                ?: values
                    .asSequence()
                    .filter { it.type.normalizedCloudDeleteType() != null && it.deletedAt <= newest.deletedAt }
                    .maxWithOrNull(
                        compareBy<CloudBookTombstone> { it.deletedAt }
                            .thenBy { it.type.normalizedCloudDeleteType().orEmpty() },
                    )
                    ?.type
                    ?.normalizedCloudDeleteType()
            newest.copy(type = preservedType)
        }
        .sortedBy(CloudBookTombstone::bookId)
}

private fun String?.normalizedCloudDeleteType(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
