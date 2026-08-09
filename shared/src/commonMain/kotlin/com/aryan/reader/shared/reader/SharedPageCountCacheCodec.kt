package com.aryan.reader.shared.reader

data class SharedPageCountCacheSnapshot(
    val counts: Map<Int, Int>,
    val finalizedChapters: Set<Int>,
    val isVersioned: Boolean,
)

/** Byte-compatible owner of Android's persisted paginated page-count cache format. */
object SharedPageCountCacheCodec {
    private const val VersionPrefix = "v2"
    private const val FinalPrefix = "final="
    private const val CountsPrefix = "counts="

    fun encode(counts: Map<Int, Int>, finalizedChapters: Set<Int>): String {
        val finalized = finalizedChapters.sorted().joinToString(",")
        val measuredCounts = finalizedChapters
            .sorted()
            .mapNotNull { chapter -> counts[chapter]?.let { "$chapter:$it" } }
            .joinToString(",")
        return "$VersionPrefix;$FinalPrefix$finalized;$CountsPrefix$measuredCounts"
    }

    fun decode(raw: String?): SharedPageCountCacheSnapshot {
        if (raw.isNullOrBlank()) {
            return SharedPageCountCacheSnapshot(emptyMap(), emptySet(), isVersioned = false)
        }
        if (!raw.startsWith("$VersionPrefix;")) {
            return SharedPageCountCacheSnapshot(decodeCounts(raw), emptySet(), isVersioned = false)
        }

        val sections = raw.split(';')
        val finalized = sections
            .firstOrNull { it.startsWith(FinalPrefix) }
            ?.removePrefix(FinalPrefix)
            ?.split(',')
            ?.mapNotNull(String::toIntOrNull)
            ?.toSet()
            .orEmpty()
        val counts = sections
            .firstOrNull { it.startsWith(CountsPrefix) }
            ?.removePrefix(CountsPrefix)
            ?.let(::decodeCounts)
            .orEmpty()
        return SharedPageCountCacheSnapshot(counts, finalized, isVersioned = true)
    }

    private fun decodeCounts(raw: String): Map<Int, Int> = raw
        .split(',')
        .asSequence()
        .filter { ':' in it }
        .mapNotNull { entry ->
            val parts = entry.split(':', limit = 2)
            val chapter = parts.getOrNull(0)?.toIntOrNull()
            val count = parts.getOrNull(1)?.toIntOrNull()
            if (chapter != null && count != null && count > 0) chapter to count else null
        }
        .toMap()
}
