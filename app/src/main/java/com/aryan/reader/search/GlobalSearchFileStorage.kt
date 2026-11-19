package com.aryan.reader.search

import com.aryan.reader.data.AndroidBookArtifactPaths
import com.aryan.reader.shared.search.SharedGlobalSearchPersistedBookIndex
import com.aryan.reader.shared.search.SharedGlobalSearchStorage
import com.aryan.reader.search.GlobalSearchLog
import com.aryan.reader.search.brief
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

/**
 * File-backed [SharedGlobalSearchStorage]: one JSON sidecar per book under
 * `filesDir/search_index/`, named with the same safe-name rules as
 * annotation sidecars ([AndroidBookArtifactPaths.sidecarName], 200-byte cap
 * with hash fallback). Writes are atomic (tmp + rename) so a killed process
 * never leaves a half-written index; corrupt files read as missing and are
 * deleted so the next background pass rebuilds them.
 */
@OptIn(ExperimentalSerializationApi::class)
class GlobalSearchFileStorage(
    private val indexDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SharedGlobalSearchStorage {

    override fun saveBook(index: SharedGlobalSearchPersistedBookIndex) {
        indexDir.mkdirs()
        val target = fileFor(index.bookId)
        val tmp = File(target.parent, "${target.name}.tmp")
        // Encode straight into the staging file: `encodeToString` + `writeText` held the whole
        // sidecar as a String and then as a UTF-8 byte array, so a large index needed two extra
        // full-size contiguous buffers (docs/crashlytics-triage.md#134).
        tmp.outputStream().use { out ->
            json.encodeToStream(SharedGlobalSearchPersistedBookIndex.serializer(), index, out)
        }
        val bytes = tmp.length()
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                error("GlobalSearchFileStorage: atomic rename failed for ${target.name}")
            }
        }
        GlobalSearchLog.d {
            "saved bookId=${index.bookId} chunks=${index.chunkCount} " +
                "terms=${index.totalTerms} bytes=$bytes file=${target.name}"
        }
    }

    override fun loadBook(bookId: String): SharedGlobalSearchPersistedBookIndex? {
        val file = fileFor(bookId)
        if (!file.isFile) {
            GlobalSearchLog.d { "load miss bookId=$bookId file=${file.name}" }
            return null
        }
        return try {
            // Decode from the stream rather than `readText()`: the text path builds a
            // `StringWriter` buffer and then a whole-file `String`/`char[]` (12 MB for a ~6 MB
            // index), and that single contiguous allocation is what OOMed the loader on a
            // nearly-full heap (docs/crashlytics-triage.md#134).
            val persisted = file.inputStream().use { stream ->
                json.decodeFromStream(SharedGlobalSearchPersistedBookIndex.serializer(), stream)
            }
            persisted.also {
                GlobalSearchLog.d {
                    "load hit bookId=$bookId chunks=${it.chunkCount} " +
                        "terms=${it.totalTerms} hash=${it.contentHash} status=${it.status}"
                }
            }
        } catch (e: Exception) {
            GlobalSearchLog.w { "load corrupt bookId=$bookId cause=${e.brief()} -> deleting" }
            file.delete()
            null
        }
    }

    override fun deleteBook(bookId: String) {
        val existed = fileFor(bookId).delete()
        GlobalSearchLog.d { "deleted bookId=$bookId existed=$existed" }
    }

    override fun storedBookIds(): Set<String> {
        val files = indexDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
        return files.mapNotNullTo(mutableSetOf()) { file ->
            runCatching {
                file.inputStream().use { stream ->
                    json.decodeFromStream(SharedGlobalSearchPersistedBookIndex.serializer(), stream)
                }.bookId
            }.getOrNull()
        }
    }

    internal fun fileFor(bookId: String): File = File(
        indexDir,
        AndroidBookArtifactPaths.sidecarName(
            prefix = "gsi_",
            sanitizedId = AndroidBookArtifactPaths.safeBookId(bookId),
            stableKey = bookId,
        ),
    )

    companion object {
        const val INDEX_DIR_NAME = "search_index"

        fun fromFilesDir(filesDir: File): GlobalSearchFileStorage =
            GlobalSearchFileStorage(File(filesDir, INDEX_DIR_NAME))
    }
}
