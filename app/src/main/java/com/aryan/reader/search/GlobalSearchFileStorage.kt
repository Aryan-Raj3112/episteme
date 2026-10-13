package com.aryan.reader.search

import com.aryan.reader.data.AndroidBookArtifactPaths
import com.aryan.reader.shared.search.SharedGlobalSearchPersistedBookIndex
import com.aryan.reader.shared.search.SharedGlobalSearchStorage
import com.aryan.reader.search.GlobalSearchLog
import com.aryan.reader.search.brief
import kotlinx.serialization.json.Json
import java.io.File
/**
 * File-backed [SharedGlobalSearchStorage]: one JSON sidecar per book under
 * `filesDir/search_index/`, named with the same safe-name rules as
 * annotation sidecars ([AndroidBookArtifactPaths.sidecarName], 200-byte cap
 * with hash fallback). Writes are atomic (tmp + rename) so a killed process
 * never leaves a half-written index; corrupt files read as missing and are
 * deleted so the next background pass rebuilds them.
 */
class GlobalSearchFileStorage(
    private val indexDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SharedGlobalSearchStorage {

    override fun saveBook(index: SharedGlobalSearchPersistedBookIndex) {
        indexDir.mkdirs()
        val target = fileFor(index.bookId)
        val tmp = File(target.parent, "${target.name}.tmp")
        val json = json.encodeToString(SharedGlobalSearchPersistedBookIndex.serializer(), index)
        tmp.writeText(json)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                error("GlobalSearchFileStorage: atomic rename failed for ${target.name}")
            }
        }
        GlobalSearchLog.d {
            "saved bookId=${index.bookId} chunks=${index.chunkCount} " +
                "terms=${index.totalTerms} bytes=${json.toByteArray().size} file=${target.name}"
        }
    }

    override fun loadBook(bookId: String): SharedGlobalSearchPersistedBookIndex? {
        val file = fileFor(bookId)
        if (!file.isFile) {
            GlobalSearchLog.d { "load miss bookId=$bookId file=${file.name}" }
            return null
        }
        return try {
            json.decodeFromString(
                SharedGlobalSearchPersistedBookIndex.serializer(),
                file.readText(),
            ).also {
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
                json.decodeFromString(
                    SharedGlobalSearchPersistedBookIndex.serializer(),
                    file.readText(),
                ).bookId
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
