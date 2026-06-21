package com.aryan.reader.paginatedreader

internal data class PageCountCacheSnapshot(
    val counts: Map<Int, Int>,
    val finalizedChapters: Set<Int>,
    val isVersioned: Boolean
)

internal object PageCountCacheCodec {
    private const val VERSION_PREFIX = "v2"
    private const val FINAL_PREFIX = "final="
    private const val COUNTS_PREFIX = "counts="

    fun encode(counts: Map<Int, Int>, finalizedChapters: Set<Int>): String {
        val finalized = finalizedChapters.sorted().joinToString(",")
        val measuredCounts = finalizedChapters
            .sorted()
            .mapNotNull { chapter -> counts[chapter]?.let { "$chapter:$it" } }
            .joinToString(",")
        return "$VERSION_PREFIX;$FINAL_PREFIX$finalized;$COUNTS_PREFIX$measuredCounts"
    }

    fun decode(raw: String?): PageCountCacheSnapshot {
        if (raw.isNullOrBlank()) {
            return PageCountCacheSnapshot(emptyMap(), emptySet(), isVersioned = false)
        }

        if (!raw.startsWith("$VERSION_PREFIX;")) {
            val counts = decodeCounts(raw)
            return PageCountCacheSnapshot(counts, emptySet(), isVersioned = false)
        }

        val sections = raw.split(';')
        val finalized = sections
            .firstOrNull { it.startsWith(FINAL_PREFIX) }
            ?.removePrefix(FINAL_PREFIX)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            .orEmpty()
        val counts = sections
            .firstOrNull { it.startsWith(COUNTS_PREFIX) }
            ?.removePrefix(COUNTS_PREFIX)
            ?.let(::decodeCounts)
            .orEmpty()
        return PageCountCacheSnapshot(counts, finalized, isVersioned = true)
    }

    private fun decodeCounts(raw: String): Map<Int, Int> {
        return raw
            .split(',')
            .asSequence()
            .filter { it.contains(':') }
            .mapNotNull { entry ->
                val parts = entry.split(':', limit = 2)
                val chapter = parts.getOrNull(0)?.toIntOrNull()
                val count = parts.getOrNull(1)?.toIntOrNull()
                if (chapter != null && count != null && count > 0) chapter to count else null
            }
            .toMap()
    }
}
