package com.aryan.reader.data

import android.content.SharedPreferences
import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.mergeCloudBookTombstones
import org.json.JSONArray
import org.json.JSONObject

private const val CLOUD_BOOK_DELETE_OUTBOX_KEY_PREFIX = "cloud_book_delete_outbox_v2_"

/** JSON codec kept separate so the durable outbox can be tested without Android storage. */
internal object CloudBookDeleteOutboxCodec {
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
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val bookId = item.optString("bookId").trim()
                    if (bookId.isBlank()) continue
                    add(
                        CloudBookTombstone(
                            bookId = bookId,
                            type = item.takeUnless { it.isNull("type") }?.optString("type"),
                            deletedAt = item.optLong("deletedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
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
