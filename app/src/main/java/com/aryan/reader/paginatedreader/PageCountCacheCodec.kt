package com.aryan.reader.paginatedreader

internal typealias PageCountCacheSnapshot =
    com.aryan.reader.shared.reader.SharedPageCountCacheSnapshot

internal object PageCountCacheCodec {
    fun encode(counts: Map<Int, Int>, finalizedChapters: Set<Int>): String =
        com.aryan.reader.shared.reader.SharedPageCountCacheCodec.encode(counts, finalizedChapters)

    fun decode(raw: String?): PageCountCacheSnapshot =
        com.aryan.reader.shared.reader.SharedPageCountCacheCodec.decode(raw)
}
