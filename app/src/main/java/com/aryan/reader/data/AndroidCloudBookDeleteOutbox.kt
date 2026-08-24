package com.aryan.reader.data

import android.content.SharedPreferences
import com.aryan.reader.shared.CloudBookTombstone
import org.json.JSONArray
import org.json.JSONObject

private const val CLOUD_BOOK_DELETE_OUTBOX_KEY = "cloud_book_delete_outbox_v1"

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
    fun pending(): List<CloudBookTombstone> = CloudBookDeleteOutboxCodec.decode(
        preferences.getString(CLOUD_BOOK_DELETE_OUTBOX_KEY, null),
    )

    @Synchronized
    fun enqueue(tombstones: Collection<CloudBookTombstone>) {
        val merged = (pending() + tombstones)
            .filter { it.bookId.isNotBlank() }
            .groupBy(CloudBookTombstone::bookId)
            .map { (_, values) -> values.maxBy(CloudBookTombstone::deletedAt) }
            .sortedBy(CloudBookTombstone::bookId)
        preferences.edit()
            .putString(CLOUD_BOOK_DELETE_OUTBOX_KEY, CloudBookDeleteOutboxCodec.encode(merged))
            .commit()
    }

    @Synchronized
    fun remove(bookIds: Collection<String>) {
        val ids = bookIds.toSet()
        if (ids.isEmpty()) return
        val remaining = pending().filterNot { it.bookId in ids }
        val editor = preferences.edit()
        if (remaining.isEmpty()) {
            editor.remove(CLOUD_BOOK_DELETE_OUTBOX_KEY)
        } else {
            editor.putString(CLOUD_BOOK_DELETE_OUTBOX_KEY, CloudBookDeleteOutboxCodec.encode(remaining))
        }
        editor.commit()
    }

    @Synchronized
    fun clear() {
        preferences.edit()
            .remove(CLOUD_BOOK_DELETE_OUTBOX_KEY)
            .commit()
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
