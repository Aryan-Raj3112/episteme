package com.aryan.reader.shared.reader

internal fun ByteArray.readComicTarEntries(): List<Pair<String, ByteArray>> {
    val entries = mutableListOf<Pair<String, ByteArray>>()
    var offset = 0
    while (offset + TAR_BLOCK_SIZE <= size) {
        val header = copyOfRange(offset, offset + TAR_BLOCK_SIZE)
        if (header.all { it == 0.toByte() }) break
        val name = header.decodeTarString(0, 100)
        val prefix = header.decodeTarString(345, 155)
        val fullName = listOf(prefix, name).filter { it.isNotBlank() }.joinToString("/")
        val dataSize = header.decodeTarString(124, 12)
            .trim()
            .toLongOrNull(8)
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 0
        val type = header.getOrNull(156)?.toInt()?.toChar()
        val dataStart = offset + TAR_BLOCK_SIZE
        val dataEnd = dataStart + dataSize
        if (dataEnd > size) break
        if ((type == null || type == '\u0000' || type == '0') && fullName.isNotBlank()) {
            entries += fullName to copyOfRange(dataStart, dataEnd)
        }
        val paddedSize = ((dataSize + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE) * TAR_BLOCK_SIZE
        offset = dataStart + paddedSize
    }
    return entries
}

private fun ByteArray.decodeTarString(start: Int, length: Int): String {
    val end = (start until minOf(start + length, size))
        .firstOrNull { this[it] == 0.toByte() }
        ?: minOf(start + length, size)
    return copyOfRange(start, end).decodeToString().trim()
}

private const val TAR_BLOCK_SIZE = 512
