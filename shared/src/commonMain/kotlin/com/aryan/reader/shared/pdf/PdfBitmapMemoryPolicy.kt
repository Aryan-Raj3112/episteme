package com.aryan.reader.shared.pdf

private const val MaxPdfBitmapPoolBytes = 16L * 1024L * 1024L

fun pdfBitmapPoolByteBudget(maxHeapBytes: Long): Long =
    (maxHeapBytes.coerceAtLeast(0L) / 32L).coerceAtMost(MaxPdfBitmapPoolBytes)

fun canPoolPdfBitmap(
    pooledBytes: Long,
    bitmapBytes: Long,
    maxHeapBytes: Long,
): Boolean {
    if (pooledBytes < 0L || bitmapBytes <= 0L) return false
    val budget = pdfBitmapPoolByteBudget(maxHeapBytes)
    return bitmapBytes <= budget && pooledBytes <= budget - bitmapBytes
}
