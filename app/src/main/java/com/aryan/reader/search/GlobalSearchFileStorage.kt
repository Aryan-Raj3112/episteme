package com.aryan.reader.search

import com.aryan.reader.data.AndroidBookArtifactPaths
import com.aryan.reader.shared.search.SharedGlobalSearchPersistedBookIndex
import com.aryan.reader.shared.search.SharedGlobalSearchPersistedMetadata
import com.aryan.reader.shared.search.SharedGlobalSearchStorage
import com.aryan.reader.shared.search.toMetadata
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
        writeMetadata(index.toMetadata())
    }

    override fun loadBook(bookId: String): SharedGlobalSearchPersistedBookIndex? {
        val file = fileFor(bookId)
        if (!file.isFile) {
            GlobalSearchLog.d { "load miss bookId=$bookId file=${file.name}" }
            return null
        }
        return decodePayload(file)
    }

    /**
     * Header read from the tiny sidecar, so freshness checks and library
     * reconciliation never build a book's index graph
     * (docs/crashlytics-triage.md#179). Payloads written before the sidecar
     * existed are decoded once and then get one.
     */
    override fun loadMetadata(bookId: String): SharedGlobalSearchPersistedMetadata? {
        val meta = metaFileFor(bookId)
        // A corrupt sidecar is deleted by the reader; fall through and rebuild it
        // from the payload rather than reporting the book as unindexed.
        if (meta.isFile) readMetadataFile(meta)?.let { return it }
        val payload = fileFor(bookId)
        if (!payload.isFile) return null
        val metadata = decodePayload(payload)?.toMetadata() ?: return null
        writeMetadata(metadata)
        return metadata
    }

    private fun decodePayload(file: File): SharedGlobalSearchPersistedBookIndex? = try {
        // Decode from the stream rather than `readText()`: the text path builds a
        // `StringWriter` buffer and then a whole-file `String`/`char[]` (12 MB for a ~6 MB
        // index), and that single contiguous allocation is what OOMed the loader on a
        // nearly-full heap (docs/crashlytics-triage.md#134).
        val persisted = file.inputStream().use { stream ->
            json.decodeFromStream(SharedGlobalSearchPersistedBookIndex.serializer(), stream)
        }
        persisted.also {
            GlobalSearchLog.d {
                "load hit bookId=${it.bookId} chunks=${it.chunkCount} " +
                    "terms=${it.totalTerms} hash=${it.contentHash} status=${it.status}"
            }
        }
    } catch (e: Exception) {
        GlobalSearchLog.w { "load corrupt file=${file.name} cause=${e.brief()} -> deleting" }
        file.delete()
        File(file.parentFile, file.name + META_SUFFIX).delete()
        null
    }

    private fun writeMetadata(metadata: SharedGlobalSearchPersistedMetadata) {
        val target = metaFileFor(metadata.bookId)
        val tmp = File(target.parent, "${target.name}.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(SharedGlobalSearchPersistedMetadata.serializer(), metadata))
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) error("atomic rename failed for ${target.name}")
            }
        }.onFailure { e ->
            GlobalSearchLog.w { "metadata write failed bookId=${metadata.bookId} cause=${e.brief()}" }
            tmp.delete()
        }
    }

    private fun readMetadataFile(file: File): SharedGlobalSearchPersistedMetadata? = try {
        json.decodeFromString(SharedGlobalSearchPersistedMetadata.serializer(), file.readText())
    } catch (e: Exception) {
        GlobalSearchLog.w { "metadata corrupt file=${file.name} cause=${e.brief()} -> deleting" }
        file.delete()
        null
    }

    override fun deleteBook(bookId: String) {
        val existed = fileFor(bookId).delete()
        metaFileFor(bookId).delete()
        GlobalSearchLog.d { "deleted bookId=$bookId existed=$existed" }
    }

    override fun storedBookIds(): Set<String> {
        val ids = mutableSetOf<String>()
        indexDir.listFiles { file -> file.isFile && file.name.endsWith(META_SUFFIX) }
            .orEmpty()
            .mapNotNullTo(ids) { file -> readMetadataFile(file)?.bookId }
        // Payloads written before the sidecar existed still count, and gain one.
        indexDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .forEach { payload ->
                if (File(payload.parentFile, payload.name + META_SUFFIX).isFile) return@forEach
                val metadata = decodePayload(payload)?.toMetadata() ?: return@forEach
                writeMetadata(metadata)
                ids += metadata.bookId
            }
        return ids
    }

    internal fun fileFor(bookId: String): File = File(
        indexDir,
        AndroidBookArtifactPaths.sidecarName(
            prefix = "gsi_",
            sanitizedId = AndroidBookArtifactPaths.safeBookId(bookId),
            stableKey = bookId,
        ),
    )

    private fun metaFileFor(bookId: String): File = File(indexDir, fileFor(bookId).name + META_SUFFIX)

    companion object {
        const val INDEX_DIR_NAME = "search_index"

        /** Suffix of the [SharedGlobalSearchPersistedMetadata] sidecar; see #179. */
        const val META_SUFFIX = ".meta"

        fun fromFilesDir(filesDir: File): GlobalSearchFileStorage =
            GlobalSearchFileStorage(File(filesDir, INDEX_DIR_NAME))
    }
}
