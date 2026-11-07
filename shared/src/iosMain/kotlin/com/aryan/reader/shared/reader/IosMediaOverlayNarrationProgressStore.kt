package com.aryan.reader.shared.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

/**
 * `NSUserDefaults`-backed narration positions, one entry per book.
 *
 * Same shape as iOS's TTS listening-progress store, and chosen for the same reason: this is a few
 * integers per narrated book, read once when narration starts and written on a clip boundary, which
 * does not justify a database on a platform whose reader has none.
 *
 * [shared] is what makes the reader and Listen agree: both narrate the same clips for the same book,
 * so a position recorded in one has to be the position the other resumes at.
 */
class IosMediaOverlayNarrationProgressStore : SharedMediaOverlayNarrationProgressStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun load(bookId: String): SharedMediaOverlayNarrationProgress? =
        readAll()[bookId]

    override fun record(progress: SharedMediaOverlayNarrationProgress) {
        val entries = readAll().toMutableMap()
        entries[progress.bookId] = progress
        writeAll(entries)
    }

    private fun readAll(): Map<String, SharedMediaOverlayNarrationProgress> {
        val raw = defaults.stringForKey(storeKey) ?: return emptyMap()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapNotNull { (bookId, element) ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val clipIndex = obj.intValue("clipIndex") ?: return@mapNotNull null
                SharedMediaOverlayNarrationProgress(
                    bookId = bookId,
                    spineItemIndex = obj.intValue("spineItemIndex") ?: 0,
                    chapterIndex = obj.intValue("chapterIndex"),
                    clipIndex = clipIndex,
                    updatedAt = (obj["updatedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }.associateBy { it.bookId }
        }.getOrDefault(emptyMap())
    }

    private fun writeAll(entries: Map<String, SharedMediaOverlayNarrationProgress>) {
        val root = buildJsonObject {
            entries.forEach { (bookId, entry) ->
                put(
                    bookId,
                    buildJsonObject {
                        put("spineItemIndex", entry.spineItemIndex)
                        entry.chapterIndex?.let { put("chapterIndex", it) }
                        put("clipIndex", entry.clipIndex)
                        put("updatedAt", entry.updatedAt)
                    },
                )
            }
        }
        defaults.setObject(Json.encodeToString(JsonElement.serializer(), root), forKey = storeKey)
    }

    private fun JsonObject.intValue(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { if (it.contentOrNull == null) null else it.intOrNull }

    companion object {
        private const val storeKey = "reader.mediaOverlayNarrationProgress.v1"

        /** The one store both iOS narration surfaces record into and resume from. */
        val shared = IosMediaOverlayNarrationProgressStore()
    }
}
