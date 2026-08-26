package com.aryan.reader.data

import android.content.SharedPreferences
import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.mergeCloudBookTombstones
import org.json.JSONArray
import org.json.JSONObject

private const val CLOUD_BOOK_DELETE_OUTBOX_KEY_PREFIX = "cloud_book_delete_outbox_v2_"

/** JSON codec kept separate so the durable outbox can be tested without Android storage. */
internal object CloudBookDeleteOutboxCodec {
    internal sealed class DecodeResult {
        data class Valid(val tombstones: List<CloudBookTombstone>) : DecodeResult()
        data class Malformed(val reason: String) : DecodeResult()
    }

    fun encode(tombstones: Collection<CloudBookTombstone>): String {
        val array = JSONArray()
        tombstones
            .filter { it.bookId.isNotBlank() }
            .forEach { tombstone ->
                array.put(
                    JSONObject().apply {
                        put("bookId", tombstone.bookId)
                        put("type", tombstone.type)
                        put("deletedAt", tombstone.deletedAt)
                    },
                )
            }
        return array.toString()
    }

    fun decode(raw: String?): List<CloudBookTombstone> {
        return when (val result = decodeResult(raw)) {
            is DecodeResult.Valid -> result.tombstones
            is DecodeResult.Malformed -> emptyList()
        }
    }

    /** Strict parser used during migration so malformed data is never erased. */
    fun decodeResult(raw: String?): DecodeResult {
        if (raw.isNullOrBlank()) return DecodeResult.Malformed("empty value")
        return try {
            val array = JSONArray(raw)
            val tombstones = buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                        ?: return DecodeResult.Malformed("entry $index is not an object")
                    val bookId = item.optString("bookId").trim()
                    if (bookId.isBlank()) {
                        return DecodeResult.Malformed("entry $index has no bookId")
                    }
                    if (!item.has("deletedAt")) {
                        return DecodeResult.Malformed("entry $index has no deletedAt")
                    }
                    add(
                        CloudBookTombstone(
                            bookId = bookId,
                            type = item.takeUnless { it.isNull("type") }?.optString("type"),
                            deletedAt = item.optLong("deletedAt", 0L),
                        ),
                    )
                }
            }
            DecodeResult.Valid(tombstones)
        } catch (error: Exception) {
            DecodeResult.Malformed(error.message ?: "invalid JSON")
        }
    }
}

/**
 * Durable Android retry state for cloud book deletion. It intentionally uses
 * tombstones, matching the shared/iOS merge model, and is removed only after
 * remote deletion and local finalization both succeed.
 */
internal class AndroidCloudBookDeleteOutbox(
    private val preferences: SharedPreferences,
) {
    @Synchronized
    fun pending(accountId: String): List<CloudBookTombstone> = CloudBookDeleteOutboxCodec.decode(
        preferences.getString(scopedKey(accountId), null),
    )

    /** Raw legacy value used for a conditional, lossless Room migration. */
    @Synchronized
    internal fun readEncoded(accountId: String): String? = preferences.getString(scopedKey(accountId), null)

    /**
     * Remove the legacy value only if it is byte-for-byte unchanged since it
     * was read. This protects a migration from erasing an old-version enqueue
     * that happened concurrently.
     */
    @Synchronized
    internal fun clearIfEncoded(accountId: String, expected: String?): Boolean {
        val key = scopedKey(accountId)
        if (preferences.getString(key, null) != expected) return false
        return preferences.edit().remove(key).commit()
    }

    @Synchronized
    fun enqueue(accountId: String, tombstones: Collection<CloudBookTombstone>): Boolean {
        if (tombstones.isEmpty()) return true
        val merged = mergeCloudBookTombstones(pending(accountId) + tombstones)
        return preferences.edit()
            .putString(scopedKey(accountId), CloudBookDeleteOutboxCodec.encode(merged))
            .commit()
    }

    @Synchronized
    fun remove(accountId: String, bookIds: Collection<String>): Boolean {
        val ids = bookIds.toSet()
        if (ids.isEmpty()) return true
        val remaining = pending(accountId).filterNot { it.bookId in ids }
        val editor = preferences.edit()
        if (remaining.isEmpty()) {
            editor.remove(scopedKey(accountId))
        } else {
            editor.putString(scopedKey(accountId), CloudBookDeleteOutboxCodec.encode(remaining))
        }
        return editor.commit()
    }

    @Synchronized
    fun clear(accountId: String): Boolean {
        // The v1 key deliberately remains untouched. It has no account
        // identity, so processing it after an account switch could delete a
        // different user's Drive file. New intents are always v2-scoped.
        return preferences.edit()
            .remove(scopedKey(accountId))
            .commit()
    }

    private fun scopedKey(accountId: String): String {
        val normalizedAccountId = accountId.trim()
        require(normalizedAccountId.isNotEmpty()) { "Cloud delete outbox requires an account id" }
        return CLOUD_BOOK_DELETE_OUTBOX_KEY_PREFIX + normalizedAccountId
    }

}

/**
 * Generic ordering contract for a cloud-book delete. Remote work must finish
 * before any local visibility/state mutation is allowed.
 */
internal suspend fun <T> executeRemoteFirstLocalDelete(
    local: T?,
    deleteRemote: suspend () -> Unit,
    markDeleted: suspend (T) -> Unit,
    finalizeLocal: suspend (T) -> Unit,
) {
    deleteRemote()
    local?.let {
        markDeleted(it)
        finalizeLocal(it)
    }
}
