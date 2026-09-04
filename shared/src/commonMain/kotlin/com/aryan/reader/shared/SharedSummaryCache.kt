package com.aryan.reader.shared

/**
 * A cached chapter/page summary produced by the reader AI hub.
 *
 * File layout mirrors the Android `SummaryCacheManager` (`chapter_summaries`
 * directory, `summary_<safeTitle>_<sectionIndex>.txt` files holding
 * `sectionTitle + "\n" + summary`) so caches stay recognizable across
 * implementations.
 */
data class CachedSummary(
    val bookTitle: String,
    val sectionIndex: Int,
    val sectionTitle: String,
    val summary: String,
)

object SharedSummaryCacheFiles {
    fun safeBookTitle(bookTitle: String): String =
        bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")

    fun fileName(bookTitle: String, sectionIndex: Int): String =
        "summary_${safeBookTitle(bookTitle)}_$sectionIndex.txt"

    fun fileNamePrefix(bookTitle: String): String =
        "summary_${safeBookTitle(bookTitle)}_"

    fun encodeContent(sectionTitle: String, summary: String): String =
        "$sectionTitle\n$summary"

    fun sectionIndexOf(fileName: String, bookTitle: String): Int? {
        val prefix = fileNamePrefix(bookTitle)
        if (!fileName.startsWith(prefix) || !fileName.endsWith(".txt")) return null
        return fileName.removePrefix(prefix).removeSuffix(".txt").toIntOrNull()
    }

    fun decodeContent(
        bookTitle: String,
        sectionIndex: Int,
        raw: String,
        fallbackSectionTitle: String = "Chapter ${sectionIndex + 1}",
    ): CachedSummary? {
        val lines = raw.lines()
        val title = lines.firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() } ?: fallbackSectionTitle
        val summary = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
        if (summary.isBlank()) return null
        return CachedSummary(
            bookTitle = bookTitle,
            sectionIndex = sectionIndex,
            sectionTitle = title,
            summary = summary,
        )
    }
}

interface SharedSummaryCacheStorage {
    fun read(fileName: String): String?
    fun write(fileName: String, content: String): Boolean
    fun delete(fileName: String): Boolean
    fun listFileNames(): List<String>
}

expect fun defaultSharedSummaryCacheStorage(): SharedSummaryCacheStorage

class SharedSummaryCache(
    private val storage: SharedSummaryCacheStorage = defaultSharedSummaryCacheStorage(),
) {
    fun saveSummary(bookTitle: String, sectionIndex: Int, sectionTitle: String, summary: String): Boolean {
        if (summary.isBlank()) return false
        return storage.write(
            SharedSummaryCacheFiles.fileName(bookTitle, sectionIndex),
            SharedSummaryCacheFiles.encodeContent(sectionTitle, summary),
        )
    }

    fun getSummary(bookTitle: String, sectionIndex: Int): CachedSummary? {
        val raw = storage.read(SharedSummaryCacheFiles.fileName(bookTitle, sectionIndex)) ?: return null
        return SharedSummaryCacheFiles.decodeContent(bookTitle, sectionIndex, raw)
    }

    fun hasSummary(bookTitle: String, sectionIndex: Int): Boolean =
        getSummary(bookTitle, sectionIndex) != null

    fun getAllSummaries(bookTitle: String): List<CachedSummary> {
        val prefix = SharedSummaryCacheFiles.fileNamePrefix(bookTitle)
        return storage.listFileNames()
            .filter { it.startsWith(prefix) && it.endsWith(".txt") }
            .mapNotNull { name ->
                val index = SharedSummaryCacheFiles.sectionIndexOf(name, bookTitle) ?: return@mapNotNull null
                val raw = storage.read(name) ?: return@mapNotNull null
                SharedSummaryCacheFiles.decodeContent(bookTitle, index, raw)
            }
            .sortedBy { it.sectionIndex }
    }

    fun deleteSummary(bookTitle: String, sectionIndex: Int): Boolean =
        storage.delete(SharedSummaryCacheFiles.fileName(bookTitle, sectionIndex))

    fun clearBookCache(bookTitle: String) {
        val prefix = SharedSummaryCacheFiles.fileNamePrefix(bookTitle)
        storage.listFileNames()
            .filter { it.startsWith(prefix) && it.endsWith(".txt") }
            .forEach { storage.delete(it) }
    }
}
