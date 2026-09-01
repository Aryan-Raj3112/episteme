package com.aryan.reader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.withTransaction
import com.aryan.reader.shared.CloudBookTombstone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Durable state for a cloud-book delete.  The account and the delete
 * timestamp form the identity of an intent version; a newer intent can never
 * be removed by a worker finishing an older attempt.
 */
@Entity(
    tableName = "cloud_book_delete_intents",
    primaryKeys = ["accountId", "bookId"],
    indices = [
        Index(value = ["accountId", "state", "requestedAt"]),
    ],
)
data class CloudBookDeleteIntentEntity(
    val accountId: String,
    val bookId: String,
    val type: String?,
    val requestedAt: Long,
    val state: String = STATE_PENDING,
    val lastError: String? = null,
    val localClaimed: Boolean = false,
    val claimedLocalLastModifiedTimestamp: Long? = null,
    val claimedLocalTimestamp: Long? = null,
    val claimedLocalFileContentModifiedTimestamp: Long? = null,
    val claimedLocalFileSize: Long? = null,
    val claimedLocalUriString: String? = null,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_LOCAL_CLAIMED = "LOCAL_CLAIMED"
        const val STATE_TOMBSTONE_PUBLISHED = "TOMBSTONE_PUBLISHED"
        const val STATE_QUARANTINED = "QUARANTINED"

        val WORKABLE_STATES = listOf(STATE_PENDING, STATE_LOCAL_CLAIMED, STATE_TOMBSTONE_PUBLISHED)
    }
}

@Dao
abstract class CloudBookDeleteDao {
    @Query(
        "SELECT * FROM cloud_book_delete_intents " +
            "WHERE accountId = :accountId AND state IN (:states) " +
            "ORDER BY requestedAt ASC, bookId ASC",
    )
    abstract suspend fun getByStates(
        accountId: String,
        states: List<String>,
    ): List<CloudBookDeleteIntentEntity>

    @Query(
        "SELECT * FROM cloud_book_delete_intents " +
            "WHERE accountId = :accountId AND bookId = :bookId LIMIT 1",
    )
    abstract suspend fun get(accountId: String, bookId: String): CloudBookDeleteIntentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replace(intent: CloudBookDeleteIntentEntity)

    /**
     * This read/compare/write is one Room transaction, so concurrent enqueue
     * calls cannot lose a newer delete or reset a published/quarantined one.
     */
    @Transaction
    open suspend fun upsertVersioned(intent: CloudBookDeleteIntentEntity) {
        val existing = get(intent.accountId, intent.bookId)
        when {
            existing == null || intent.requestedAt > existing.requestedAt -> {
                replace(intent.copy(state = CloudBookDeleteIntentEntity.STATE_PENDING))
            }

            intent.requestedAt == existing.requestedAt &&
                existing.type.isNullOrBlank() && !intent.type.isNullOrBlank() -> {
                // Preserve the progress state, but fill in a type learned by
                // a later metadata path so Drive cleanup can be completed.
                replace(existing.copy(type = intent.type.trim()))
            }
        }
    }

    @Transaction
    open suspend fun upsertVersioned(intents: Collection<CloudBookDeleteIntentEntity>) {
        intents.forEach { upsertVersioned(it) }
    }

    @Query(
        "UPDATE cloud_book_delete_intents SET state = :publishedState, lastError = NULL " +
            "WHERE accountId = :accountId AND bookId = :bookId AND requestedAt = :requestedAt " +
            "AND state IN ('PENDING', 'LOCAL_CLAIMED')",
    )
    abstract suspend fun markTombstonePublished(
        accountId: String,
        bookId: String,
        requestedAt: Long,
        publishedState: String = CloudBookDeleteIntentEntity.STATE_TOMBSTONE_PUBLISHED,
    ): Int

    @Query(
        "UPDATE cloud_book_delete_intents SET state = 'LOCAL_CLAIMED', localClaimed = 1, " +
            "claimedLocalLastModifiedTimestamp = :lastModifiedTimestamp, " +
            "claimedLocalTimestamp = :timestamp, " +
            "claimedLocalFileContentModifiedTimestamp = :fileContentModifiedTimestamp, " +
            "claimedLocalFileSize = :fileSize, claimedLocalUriString = :uriString, lastError = NULL " +
            "WHERE accountId = :accountId AND bookId = :bookId AND requestedAt = :requestedAt " +
            "AND state = 'PENDING'",
    )
    abstract suspend fun claimLocalGeneration(
        accountId: String,
        bookId: String,
        requestedAt: Long,
        lastModifiedTimestamp: Long?,
        timestamp: Long?,
        fileContentModifiedTimestamp: Long?,
        fileSize: Long?,
        uriString: String?,
    ): Int

    @Query(
        "UPDATE cloud_book_delete_intents SET state = :quarantinedState, lastError = :reason " +
            "WHERE accountId = :accountId AND bookId = :bookId AND requestedAt = :requestedAt " +
            "AND state IN ('PENDING', 'LOCAL_CLAIMED', 'TOMBSTONE_PUBLISHED')",
    )
    abstract suspend fun quarantine(
        accountId: String,
        bookId: String,
        requestedAt: Long,
        reason: String,
        quarantinedState: String = CloudBookDeleteIntentEntity.STATE_QUARANTINED,
    ): Int

    @Query(
        "DELETE FROM cloud_book_delete_intents " +
            "WHERE accountId = :accountId AND bookId = :bookId AND requestedAt = :requestedAt",
    )
    abstract suspend fun removeIfVersion(accountId: String, bookId: String, requestedAt: Long): Int

    @Query("DELETE FROM cloud_book_delete_intents WHERE accountId = :accountId")
    abstract suspend fun clearAccount(accountId: String): Int
}

internal data class CloudBookLocalGeneration(
    val lastModifiedTimestamp: Long,
    val timestamp: Long,
    val fileContentModifiedTimestamp: Long,
    val fileSize: Long,
    val uriString: String?,
)

internal enum class CloudBookLocalClaimResult {
    CLAIMED,
    QUARANTINED,
    SKIPPED,
}

internal fun RecentFileItem.cloudBookLocalGeneration(): CloudBookLocalGeneration =
    CloudBookLocalGeneration(
        lastModifiedTimestamp = lastModifiedTimestamp,
        timestamp = timestamp,
        fileContentModifiedTimestamp = fileContentModifiedTimestamp,
        fileSize = fileSize,
        uriString = uriString,
    )

internal fun CloudBookDeleteIntentEntity.claimedLocalGeneration(): CloudBookLocalGeneration? {
    if (!localClaimed || claimedLocalLastModifiedTimestamp == null ||
        claimedLocalTimestamp == null || claimedLocalFileContentModifiedTimestamp == null ||
        claimedLocalFileSize == null
    ) return null
    return CloudBookLocalGeneration(
        lastModifiedTimestamp = claimedLocalLastModifiedTimestamp,
        timestamp = claimedLocalTimestamp,
        fileContentModifiedTimestamp = claimedLocalFileContentModifiedTimestamp,
        fileSize = claimedLocalFileSize,
        uriString = claimedLocalUriString,
    )
}

internal fun RecentFileItem.matchesCloudBookLocalGeneration(generation: CloudBookLocalGeneration): Boolean =
    lastModifiedTimestamp == generation.lastModifiedTimestamp &&
        timestamp == generation.timestamp &&
        fileContentModifiedTimestamp == generation.fileContentModifiedTimestamp &&
        fileSize == generation.fileSize &&
        uriString == generation.uriString

/**
 * Room-backed delete queue with one-time migration from the pre-Room
 * SharedPreferences outbox.  SharedPreferences remains read-only legacy
 * recovery storage; all new writes use the transactional DAO.
 */
internal class CloudBookDeletePersistence(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.getDatabase(appContext) }
    private val dao by lazy { database.cloudBookDeleteDao() }
    private val legacy by lazy {
        AndroidCloudBookDeleteOutbox(
            appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE),
        )
    }

    suspend fun pending(accountId: String): List<CloudBookDeleteIntentEntity> = withContext(Dispatchers.IO) {
        val normalized = normalizeAccountId(accountId)
        migrateLegacy(normalized)
        dao.getByStates(
            normalized,
            listOf(
                CloudBookDeleteIntentEntity.STATE_PENDING,
                CloudBookDeleteIntentEntity.STATE_LOCAL_CLAIMED,
                CloudBookDeleteIntentEntity.STATE_TOMBSTONE_PUBLISHED,
                CloudBookDeleteIntentEntity.STATE_QUARANTINED,
            ),
        )
    }

    suspend fun workPending(accountId: String): List<CloudBookDeleteIntentEntity> = withContext(Dispatchers.IO) {
        val normalized = normalizeAccountId(accountId)
        migrateLegacy(normalized)
        dao.getByStates(normalized, CloudBookDeleteIntentEntity.WORKABLE_STATES)
    }

    suspend fun enqueue(accountId: String, tombstones: Collection<CloudBookTombstone>): Boolean =
        withContext(Dispatchers.IO) {
            if (tombstones.isEmpty()) return@withContext true
            val normalized = normalizeAccountId(accountId)
            migrateLegacy(normalized)
            dao.upsertVersioned(
                tombstones
                    .filter { it.bookId.isNotBlank() }
                    .map { tombstone ->
                        CloudBookDeleteIntentEntity(
                            accountId = normalized,
                            bookId = tombstone.bookId,
                            type = tombstone.type?.trim()?.takeIf(String::isNotBlank),
                            // Zero was valid in the legacy JSON but is not a
                            // useful ordering version for a new row.
                            requestedAt = tombstone.deletedAt.coerceAtLeast(1L),
                        )
                    },
            )
            true
        }

    suspend fun markTombstonePublished(intent: CloudBookDeleteIntentEntity): Boolean =
        withContext(Dispatchers.IO) {
            dao.markTombstonePublished(intent.accountId, intent.bookId, intent.requestedAt) > 0
        }

    /**
     * Claim the local generation in the same Room transaction that advances
     * the delete intent. A newer local row is quarantined before any remote
     * tombstone is written.
     */
    suspend fun claimLocalGeneration(intent: CloudBookDeleteIntentEntity): CloudBookLocalClaimResult =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val current = dao.get(intent.accountId, intent.bookId)
                if (current == null || current.requestedAt != intent.requestedAt ||
                    current.state != CloudBookDeleteIntentEntity.STATE_PENDING
                ) {
                    return@withTransaction CloudBookLocalClaimResult.SKIPPED
                }
                val local = database.recentFileDao().getFileByBookId(intent.bookId)?.toRecentFileItem()
                if (local != null && local.lastModifiedTimestamp > intent.requestedAt) {
                    dao.quarantine(
                        intent.accountId,
                        intent.bookId,
                        intent.requestedAt,
                        "newer local generation modified at ${local.lastModifiedTimestamp}",
                    )
                    return@withTransaction CloudBookLocalClaimResult.QUARANTINED
                }
                val claimed = dao.claimLocalGeneration(
                    accountId = intent.accountId,
                    bookId = intent.bookId,
                    requestedAt = intent.requestedAt,
                    lastModifiedTimestamp = local?.lastModifiedTimestamp,
                    timestamp = local?.timestamp,
                    fileContentModifiedTimestamp = local?.fileContentModifiedTimestamp,
                    fileSize = local?.fileSize,
                    uriString = local?.uriString,
                )
                if (claimed == 1) CloudBookLocalClaimResult.CLAIMED
                else CloudBookLocalClaimResult.SKIPPED
            }
        }

    /** Recheck a claimed row immediately before publishing its tombstone. */
    suspend fun revalidateLocalGeneration(intent: CloudBookDeleteIntentEntity): CloudBookLocalClaimResult =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val current = dao.get(intent.accountId, intent.bookId)
                if (current == null || current.requestedAt != intent.requestedAt ||
                    current.state != CloudBookDeleteIntentEntity.STATE_LOCAL_CLAIMED
                ) {
                    return@withTransaction CloudBookLocalClaimResult.SKIPPED
                }
                val local = database.recentFileDao().getFileByBookId(intent.bookId)?.toRecentFileItem()
                val expected = current.claimedLocalGeneration()
                // A missing row is expected when the initiating device has
                // already finalized local visibility. A present row must be
                // exactly the claimed generation.
                if (local != null && (expected == null || !local.matchesCloudBookLocalGeneration(expected))) {
                    dao.quarantine(
                        intent.accountId,
                        intent.bookId,
                        intent.requestedAt,
                        "local generation changed before tombstone publication",
                    )
                    return@withTransaction CloudBookLocalClaimResult.QUARANTINED
                }
                CloudBookLocalClaimResult.CLAIMED
            }
        }

    suspend fun quarantine(intent: CloudBookDeleteIntentEntity, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.quarantine(intent.accountId, intent.bookId, intent.requestedAt, reason) > 0
        }

    suspend fun removeIfVersion(intent: CloudBookDeleteIntentEntity): Boolean =
        withContext(Dispatchers.IO) {
            dao.removeIfVersion(intent.accountId, intent.bookId, intent.requestedAt) > 0
        }

    suspend fun clear(accountId: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = normalizeAccountId(accountId)
        dao.clearAccount(normalized)
        legacy.clear(normalized)
        true
    }

    private suspend fun migrateLegacy(accountId: String) {
        val encoded = legacy.readEncoded(accountId) ?: return
        val tombstones = when (val decoded = CloudBookDeleteOutboxCodec.decodeResult(encoded)) {
            is CloudBookDeleteOutboxCodec.DecodeResult.Valid -> decoded.tombstones
            is CloudBookDeleteOutboxCodec.DecodeResult.Malformed -> {
                // Keep malformed data for manual recovery; silently clearing
                // it would lose an explicit destructive user action.
                Timber.e("Preserving malformed legacy cloud-delete outbox: ${decoded.reason}")
                return
            }
        }
        if (tombstones.isEmpty()) {
            legacy.clearIfEncoded(accountId, encoded)
            return
        }
        dao.upsertVersioned(
            tombstones.map { tombstone ->
                CloudBookDeleteIntentEntity(
                    accountId = accountId,
                    bookId = tombstone.bookId,
                    type = tombstone.type?.trim()?.takeIf(String::isNotBlank),
                    requestedAt = tombstone.deletedAt.coerceAtLeast(1L),
                )
            },
        )
        // Do not erase a legacy write that raced migration; the conditional
        // remove leaves it available for the next recovery pass.
        legacy.clearIfEncoded(accountId, encoded)
    }

    private fun normalizeAccountId(accountId: String): String = accountId.trim().also {
        require(it.isNotBlank()) { "Cloud-book deletion requires an account id" }
    }
}
